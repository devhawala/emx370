/*
** This file is part of the emx370 emulator.
**
** This software is provided "as is" in the hope that it will be useful,
** with no promise, commitment or even warranty (explicit or implicit)
** to be suited or usable for any particular purpose.
** Using this software is at your own risk!
**
** Written by Dr. Hans-Walter Latz, Berlin (Germany), 2015
** Released to the public domain.
*/

package dev.hawala.vm370.tape;

import java.util.Arrays;

import dev.hawala.vm370.vm.device.iDevice;
import dev.hawala.vm370.vm.device.iDeviceIO;
import dev.hawala.vm370.vm.device.iDeviceStatus;
import dev.hawala.vm370.vm.machine.iProcessorEventTracker;

/**
 * Implementation of a 3420 type tape device.
 * 
 * <p>
 * A tape device has initially no tape loaded. Tape can be loaded through tape providers, which
 * read tape files and can write new tape files from an in-memory tape.
 * </p>
 * 
 * <p>
 * See: GA33-1510-1_IBM_System_370_Model_115_Functional_Characteristics_Jul76.pdf (pp. 167.1++ (pdf: sheet 183++))
 * <p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 * 
 */
public class TapeDrive implements iDevice {
	
	// the log sink for our log outputs
	private iProcessorEventTracker eventLogger;
	
	// a /dev/null event tracker
	private static class NullTracker implements iProcessorEventTracker {
		public void logLine(String line, Object... args) {};
	}
	
	// the head an tail blocks used to delimit the tape blosk in memory
	private final TapeBlock headLimit;
	private final TapeBlock tailLimit;
	
	// the current tape mounted in this device
	private iTapeIo tapeIo = null; // null => not tape mounted
	private boolean isReadonly = true; // "write ring not mounted" resp. "enable ring not installed"
	
	// the last sequential tape block processed 
	private TapeBlock currentBlock;
	
	// has the tape been changed and should be written back
	private boolean isModified = false;
	
	// are we waiting for a tape to be mounted after a tape access failed
	private boolean hasFailedAccessAttempt = false;
	
	/**
	 * Simple constructor
	 */
	public TapeDrive() {
		this(null);
	}
	
	/**
	 * Constructor providing a logger.
	 * 
	 * @param eventTracker the logger to be used or {@code null} for /dev/null logging. 
	 */
	public TapeDrive(iProcessorEventTracker eventTracker) {
		this.headLimit = new TapeBlock();
		this.tailLimit = new TapeBlock();
		this.headLimit.append(this.tailLimit);
		this.currentBlock = this.headLimit;
		this.eventLogger = (eventTracker == null) ? new NullTracker() : eventTracker;
	}
	
	/**
	 * Mount a tape file from disk into memory, possibly creating it it does not exists. 
	 * 
	 * @param filename the filename for the tape file to load into memory
	 * @param createIfNew if the file does not exists, create the file if {@code true}.
	 * @param writable simulate tape loading with write ring ({@code true}} to allow
	 *   writes to the tape or not.
	 * @param msgSink the target to where append messages if problems occur while reading
	 *   the tape file.
	 */
	public void mountTapefile(String filename, boolean createIfNew, boolean writable, StringBuilder msgSink) {
		iTapeIo newTapeIo = null;
		
		// we currently only know about uncompressed AWS files
		if (TapeIoAws.isAwsFilename(filename)) {
			newTapeIo = TapeIoAws.get(filename, createIfNew, writable, msgSink);
		} else {
			msgSink
				.append("No tape IO handler found for file: ")
				.append(filename);
		}
		
		// unknown tape file type, does not exists and 'createIfNew' == false, ...
		if (newTapeIo == null) { return; }
		
		// we have a new tape: dismount the current tape and initialize drive for the new tape
		this.dismountTapeFile();
		this.tapeIo = newTapeIo;
		newTapeIo.readTapeFile(this.headLimit, this.tailLimit);
		this.isReadonly = !newTapeIo.isWritable(); // even if writable was requested, the file may be read/only...
		this.isModified = false;
		this.currentBlock = this.headLimit;
		this.hasFailedAccessAttempt = false;
		this.resetSense();
	}
	
	/**
	 * Unload the current tape if one is mounted, possibly writing it back
	 * iff it was modified.
	 */
	public void dismountTapeFile() {
		if (this.tapeIo == null) { return; } // no tape mounted
		
		if (this.needsSaving()) {
			this.tapeIo.writeTapeFile(this.headLimit, this.tailLimit);
		}
		this.tapeIo = null;
		
		this.headLimit.append(this.tailLimit);
		this.tailLimit.append(null); // (just to be sure)
		
		this.isModified = false;
		this.isReadonly = true;
		this.currentBlock = this.headLimit;
		this.hasFailedAccessAttempt = false;
		
		this.resetSense();
	}
	
	/**
	 * Check if the file should be written back to disk.
	 * 
	 * @return {@code true} if the file was modified.
	 */
	public boolean needsSaving() {
		return this.isModified && !this.isReadonly;
	}
	
	/*
	 * management of sense information
	 */
	
	private byte[] senseBytes = new byte[24];
	
	// flags that we can generate here
	// (hardware problems or the like are not generated once a tape file is present)
	// bits usage in sense constants:
	// 0xFF...... : sense byte 0 (general)
	// 0x..XX.... : sense byte 1 (unit status)
	// 0x....XX.. : sense byte 4
	// 0x......XX : (unused/spare)
	
	// sense byte 0
	private static final int Sense_CommandReject        = 0x80000000;
	private static final int Sense_NotTapeWritable      = 0x80000000;
	private static final int Sense_InterventionRequired = 0x40000000;
	
	// sense byte 1 (set in updateUnitStatus based on other fields) 
	private static final int Sense_Ready                = 0x00400000;
	private static final int Sense_NotReady             = 0x00200000;
	private static final int Sense_AtLoadPoint          = 0x00080000;
	private static final int Sense_FileProtected        = 0x00020000;
	
	// sense byte 4
	private static final int Sense_EndOfTape            = 0x00002000;
	private static final int Sense_IllegalCommand       = 0x80000100; // sets also CommandReject !!
	
	// set the sense flags about the status of the loaded tape
	private void updateUnitStatus() {
		int b1 = Sense_NotReady;
		if (this.tapeIo != null) {
			b1 = Sense_Ready;
			b1 |= (this.currentBlock == this.headLimit) ? Sense_AtLoadPoint : 0;
			b1 |= (this.isReadonly) ? Sense_FileProtected : 0;
		}
		this.senseBytes[1] = (byte)((b1 >> 16) & 0xFF);
		
		int b4 = (this.currentBlock == this.tailLimit) ? Sense_EndOfTape : 0;
		this.senseBytes[4] = (byte)( ((b4 >> 8) & 0xF0) | (this.senseBytes[4] & 0x0F) );
	}
	
	// reset the sense bytes to "device is OK"
	private void resetSense() {
		Arrays.fill(this.senseBytes, (byte)0x00);
		this.updateUnitStatus();
	}
	
	// signal the successful end of a tape operation
	private int exitOk() {
		this.updateUnitStatus();
		this.eventLogger.logLine("       => DEVICE_END");
		return iDeviceStatus.OK | iDeviceStatus.DEVICE_END;
	}
	
	// signal both the successful end of a tape operation and
	// the irrelevance of a wrong data length indication
	private int exitOkIgnoreLength() {
		this.updateUnitStatus();
		this.eventLogger.logLine("       => DEVICE_END");
		return iDeviceStatus.INCORRECT_LENGTH_IS_OK | iDeviceStatus.DEVICE_END;
	}
	
	// signal the unsuccessful end of a tape operation with the given sense flags
	private int exitUnitCheck(int senseFlags) {
		this.senseBytes[0] = (byte)((senseFlags & 0xFF000000) >> 24);
		this.senseBytes[4] = (byte)((senseFlags & 0x0000FF00) >> 8);
		this.updateUnitStatus();
		this.eventLogger.logLine("       => UNIT_CHECK, DEVICE_END [%s%s ]",
				((senseFlags & Sense_CommandReject) != 0) ? " CommandReject" : "",
				((senseFlags & Sense_FileProtected) != 0) ? " FileProtected" : "");
		return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
	}
	
	// signal the special "tape mark encountered" unsuccessful end of tape operation 
	private int exitUnitException() {
		this.updateUnitStatus();
		this.eventLogger.logLine("       => UNIT_EXCEPTION, DEVICE_END");
		return iDeviceStatus.UNIT_EXCEPTION | iDeviceStatus.DEVICE_END;
	}	

	// prepare processing of a new CCW-chain
	// see: iDevice
	@Override
	public void resetState() {
		// what to do for a tape device??
	}

	// perform a control I/O operation
	// see: iDevice
	@Override
	public int control(int opcode, int dataLength, iDeviceIO memSource) {
		if (this.tapeIo == null) { // no tape mounted ... no operation possible (not ready)
			this.eventLogger.logLine(".. .. TapeDrive: control() without tape mounted");
			this.hasFailedAccessAttempt = true;
			return this.exitUnitCheck(Sense_InterventionRequired);
		}

		switch(opcode)
		{
			
		case 0x03: // Control no-op
			this.eventLogger.logLine(".. .. TapeDrive: control(NO-OP)");
			return this.exitOk();
			
		case 0x07: // Rewind
			this.eventLogger.logLine(".. .. TapeDrive: control(REWIND)");
			this.currentBlock = this.headLimit;
			return this.exitOk();
			
		case 0x0F: // Rewind-unload
			this.eventLogger.logLine(".. .. TapeDrive: control(REWIND-DISMOUNT)");
			this.dismountTapeFile();
			return this.exitOk();
			
		case 0x17: // Erase gap
			this.eventLogger.logLine(".. .. TapeDrive: control(ERASE-GAP)");
			if (this.isReadonly) {
				return this.exitUnitCheck(Sense_NotTapeWritable);
			}
			// erase gap effectively drops the rest of the tape content...
			this.currentBlock = this.tailLimit;
			this.isModified = true;
			return this.exitOk();
			
		case 0x1F: // Write tape mark
			this.eventLogger.logLine(".. .. TapeDrive: control(WRITE-TAPE-MARK)");
			if (this.isReadonly) {
				return this.exitUnitCheck(Sense_NotTapeWritable);
			}
			
			// at tape end: append new things before tailLimit
			if (this.currentBlock == this.tailLimit) {
				this.currentBlock = this.tailLimit.getPrev();
			}
			
			// append the tapemark
			TapeBlock tapeMark = new TapeBlock(this.currentBlock);
			tapeMark.append(this.tailLimit);
			this.currentBlock = tapeMark;
			this.isModified = true;
			
			// done
			return this.exitOk();
			
		case 0x27: // Backspace block
			this.eventLogger.logLine(".. .. TapeDrive: control(BACKSPACE-BLOCK)");
			if (this.currentBlock == this.headLimit) {
				return this.exitOk();
			}
			this.currentBlock = this.currentBlock.getPrev();
			if (this.currentBlock.isTapemark()) {
				this.exitUnitException(); 
			}
			return this.exitOk();
			
		case 0x2F: // Backspace file
			this.eventLogger.logLine(".. .. TapeDrive: control(BACKSPACE-FILE)");
			if (this.currentBlock == this.headLimit) {
				return this.exitOk();
			}
			while(this.currentBlock != this.headLimit && !this.currentBlock.isTapemark()) {
				this.currentBlock = this.currentBlock.getPrev();
			}
			if (this.currentBlock != this.headLimit) {
				this.currentBlock = this.currentBlock.getPrev();
			}
			return this.exitOk();
			
		case 0x37: // Forwardspace block
			this.eventLogger.logLine(".. .. TapeDrive: control(FORWARDSPACE-BLOCK)");
			if (this.currentBlock == this.tailLimit) {
				return this.exitOk();
			}
			this.currentBlock = this.currentBlock.getNext();
			if (this.currentBlock.isTapemark()) {
				this.exitUnitException(); 
			}
			return this.exitOk();
			
		case 0x3F: // Forwardspace file
			this.eventLogger.logLine(".. .. TapeDrive: control(FORWARDSPACE-FILE)");
			if (this.currentBlock == this.tailLimit) {
				return this.exitOk();
			}
			this.currentBlock = this.currentBlock.getNext();
			while(this.currentBlock != this.tailLimit && !this.currentBlock.isTapemark()) {
				this.currentBlock = this.currentBlock.getNext();
			}
			return this.exitOk();
		
		case 0x53: case 0x63: case 0x6B: case 0x73: case 0x7B:
		case 0x93: case 0xA3: case 0xAB: case 0xB3: case 0xBB:
		case 0xC3: case 0xCB:
			// set mode XXX => no density/parity/translator/converter in a tape emulating file => ignored!
			this.eventLogger.logLine(".. .. TapeDrive: control(SET-MODE)-0x%02X", opcode);
			return this.exitOk();
			
		case 0x97: // Data security erase
			this.eventLogger.logLine(".. .. TapeDrive: control(DATA-SECURITY-ERASE)");
			if (this.isReadonly) {
				return this.exitUnitCheck(Sense_NotTapeWritable);
			}
			if (this.currentBlock == this.tailLimit) {
				return this.exitOk();
			}
			this.currentBlock.append(this.tailLimit);
			this.isModified = true;
			return this.exitOk();
			
		case 0x1B: // request data in error
			// unsupported by this emulation!
			// (in fact: this would have to transfer from device to memory
			//           but our control data transfer is defined to transfer
			//           from memory to device !!!!!)
			this.eventLogger.logLine(".. .. TapeDrive: control(REQUEST-DATA-IN-ERROR)");
			return this.exitUnitCheck(Sense_IllegalCommand);
			
			
		case 0x4B: // Set diagnose
			// unsupported by this emulation!
			this.eventLogger.logLine(".. .. TapeDrive: control(SET-DIAGNOSE)");
			return this.exitUnitCheck(Sense_IllegalCommand);
			
		case 0x0B: // Set diagnose mode
			// unsupported by this emulation!
			this.eventLogger.logLine(".. .. TapeDrive: control(SET-DIAGNOSE-MODE)");
			return this.exitUnitCheck(Sense_IllegalCommand);
			
		case 0x8B: // Loop write-to-read
			// our write head is always OK!
			this.eventLogger.logLine(".. .. TapeDrive: control(LOOP-WRITE-TO-READ)");
			return this.exitOk();
			
		default:
			this.eventLogger.logLine(".. .. TapeDrive: control(illegal command: 0x%02X)", opcode);
			return this.exitUnitCheck(Sense_IllegalCommand);
		}
	}

	/* read backward not supported by DeviceHandler !!! (would transfer memory backwards!)
	@Override
	public int readBackward(int opcode, int dataLength, IDeviceIO memTarget) {
		if (this.tapeIo == null) { // no tape mounted ... no operation possible (not ready)
			return this.exitUnitCheck(Sense_InterventionRequired);
		}

		switch(opcode)
		{
			
		case 0x0C: // Read backward
			
			return this.exitUnitCheck(Sense_CommandReject);
		
		default:
			return this.exitUnitCheck(Sense_IllegalCommand);
		}
	}
	*/

	// perform a read I/O operation
	// see: iDevice
	@Override
	public int read(int opcode, int dataLength, iDeviceIO memTarget) {
		if (this.tapeIo == null) { // no tape mounted ... no operation possible (not ready)
			this.eventLogger.logLine(".. .. TapeDrive: read() without tape mounted");
			this.hasFailedAccessAttempt = true;
			return this.exitUnitCheck(Sense_InterventionRequired);
		}

		switch(opcode)
		{
			
		case 0x02: // Read forward
			this.eventLogger.logLine(".. .. TapeDrive: read(READ) [ dataLength = %d ]", dataLength);
			TapeBlock nextBlock = (this.currentBlock == this.tailLimit) ? this.tailLimit: this.currentBlock.getNext();
			if (nextBlock == null) { this.currentBlock = this.tailLimit; }
			this.currentBlock = nextBlock;
			if (this.currentBlock == this.tailLimit) {
				this.eventLogger.logLine("          -> end-of-tape");
				return this.exitUnitCheck(Sense_EndOfTape);
			}
			if (this.currentBlock.isTapemark()) {
				this.eventLogger.logLine("          -> tape-mark");
				return this.exitUnitException(); // signal the tape mark
			}
			
			byte[] blockData = this.currentBlock.getBlockData();
			memTarget.transfer(blockData, 0, blockData.length);
			this.eventLogger.logLine("          -> transferred tape block with %d bytes", blockData.length);
			
			return this.exitOkIgnoreLength();
		
		default:
			this.eventLogger.logLine(".. .. TapeDrive: read(illegal command: 0x%02X)", opcode);
			return this.exitUnitCheck(Sense_IllegalCommand);
		}
	}

	// perform a write I/O operation
	// see: iDevice
	@Override
	public int write(int opcode, int dataLength, iDeviceIO memSource) {
		if (this.tapeIo == null) { // no tape mounted ... no operation possible (not ready)
			this.eventLogger.logLine(".. .. TapeDrive: write() without tape mounted");
			this.hasFailedAccessAttempt = true;
			return this.exitUnitCheck(Sense_InterventionRequired);
		}
		if (this.isReadonly) {
			this.eventLogger.logLine(".. .. TapeDrive: write() on read-only tape");
			return this.exitUnitCheck(Sense_NotTapeWritable);
		}

		switch(opcode)
		{
			
		case 0x01: // Write
			this.eventLogger.logLine(".. .. TapeDrive: write(WRITE) [ dataLength = %d ]", dataLength);
			
			// just to be sure...
			if (dataLength < 0) {
				return this.exitUnitCheck(Sense_CommandReject);
			}
			
			// at tape end: append new things before tailLimit
			if (this.currentBlock == this.tailLimit) {
				this.currentBlock = this.tailLimit.getPrev();
			}
			
			// get the block data
			byte[] blockContent = new byte[dataLength];
			memSource.transfer(blockContent, 0, blockContent.length);
			
			// create new tape block 
			TapeBlock newBlock = new TapeBlock(this.currentBlock, blockContent);
			newBlock.append(this.tailLimit);
			this.currentBlock = newBlock;
			this.isModified = true;
			
			// done
			return this.exitOk();
		
		default:
			this.eventLogger.logLine(".. .. TapeDrive: write(illegal command: 0x%02X)", opcode);
			return this.exitUnitCheck(Sense_IllegalCommand);
		}
	}
	
	// fixed data sequence returned for "Sense I/O type"
	// see: GA33-1510-1_IBM_System_370_Model_115_Functional_Characteristics_Jul76.pdf (page 167.5 (pdf: 187))
	private final static byte[] SENSE_IO_TYPE 
		= { (byte)0xFF, (byte)0x38, (byte)0x03, (byte)0x03, (byte)0x34, (byte)0x20, (byte)0x03 }; 

	// perform a sense I/O operation
	// see: iDevice
	@Override
	public int sense(int opcode, int dataLength, iDeviceIO memTarget) {

		switch(opcode)
		{
		case 0x04: // Sense
			this.eventLogger.logLine(".. .. TapeDrive: sense(SENSE) [ dataLength = %d ]", dataLength);
			memTarget.transfer(this.senseBytes, 0, this.senseBytes.length);
			this.resetSense(); // reset at it has been read (where else to reset it?)
			return this.exitOk();
			
		case 0xE4: // Sense I/O type
			this.eventLogger.logLine(".. .. TapeDrive: sense(SENSE-I/O-TYPE) [ dataLength = %d ]", dataLength);
			memTarget.transfer(SENSE_IO_TYPE, 0, SENSE_IO_TYPE.length);
			return this.exitOk();
		
		default:
			this.eventLogger.logLine(".. .. TapeDrive: sense(illegal command: 0x%02X)", opcode);
			return this.exitUnitCheck(Sense_IllegalCommand);
		}
	}

	// get a single sense byte
	// see: iDevice
	@Override
	public byte getSenseByte(int index) {
		if (index < 0 || index >= this.senseBytes.length) { return (byte)0; }
		return this.senseBytes[index];
	}

	// get the virtual device information for DIAG-x24
	// see: iDevice
	@Override
	public int getVDevInfo() {
		return 0x08100302; // Diag-x24 Ry on VM/370R6 Sixpack 1.2 for a 3420 as 181 with or without a mounted tape
	}

	// get the real device information for DIAG-x24
	// see: iDevice
	@Override
	public int getRDevInfo() {
		return 0x08100802; // Diag-x24 Ry+1 on VM/370R6 Sixpack 1.2 for a 3420 as 181 with or without a mounted tape
	}
	
	// get the printable device type name
	// see: iDevice
	@Override
	public String getCpDeviceTypeName() {
		return "TAPE";
	}

	// get the device information for QUERY VIRTUAL
	// see: iDevice
	@Override
	public String getCpQueryStatusLine(int asCuu) {
		return String.format("TAPE %03X ON TAPE %03X", asCuu, asCuu);
	}

	// check for attention interrupt
	// see: iDevice
	@Override
	public boolean hasPendingAsyncInterrupt() {
		return this.hasFailedAccessAttempt && this.tapeIo != null;
	}

	// remove one pending attention interrupt
	// see: iDevice
	@Override
	public void consumeNextAsyncInterrupt() {
		this.hasFailedAccessAttempt = false;
	}

	// enqueue an attention interrupt
	// see: iDevice
	@Override
	public void doAttentionInterrupt() {
		// irrelevant for tape
	}
	
	/*
	 * information methods 
	 */
	
	/**
	 * Check if a tape os mounted on the tape dwvice.
	 * 
	 * @return {@code true} if a tape is mounted.
	 */
	public boolean hasMountedTape() {
		return (this.tapeIo != null);
	}

	/**
	 * Check if the mounted tape is read/only (i.e. was mounted
	 * as read/only or the tape file is not writable).
	 * 
	 * @return {@code true} if the tape is read/only.
	 */
	public boolean isReadOnly() {
		return this.isReadonly;
	}
	
	/**
	 * Check if the mounted tape was modified.
	 * 
	 * @return {@code true} if the tape is modified.
	 */
	public boolean isModified() {
		return this.isModified;
	}
	
	/**
	 * Get the name of the mounted tape file.
	 *  
	 * @return the filename of the tape file or [@code null} if no
	 *   tape is mounted on the device.
	 */
	public String getTapeFilename() {
		if (this.tapeIo == null) { return null; }
		return this.tapeIo.getTapeFilename();
	}
	
	/**
	 * Get the numner of blocks currently in the mounted tape or
	 * zero if no tape is mounted.
	 * 
	 * @return the number of tape blocks in the current tape.
	 */
	public int getCurrBlockCount() {
		int count = 0;
		TapeBlock curr = this.headLimit.getNext();
		while(curr != this.tailLimit) {
			count++;
			curr = curr.getNext();
		}
		return count;
	}
	
	/**
	 * Get the number of data bytes in the current tape or zero if
	 * not tape is mounted.
	 * 
	 * @return the number of data bytes in the tape.
	 */
	public int getCurrBytes() {
		int bytes = 0;
		TapeBlock curr = this.headLimit.getNext();
		while(curr != this.tailLimit) {
			bytes += (curr.isTapemark()) ? 0 : curr.getBlockData().length;
			curr = curr.getNext();
		}
		return bytes;
	}
}
