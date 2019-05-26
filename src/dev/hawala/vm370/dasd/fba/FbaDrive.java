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

package dev.hawala.vm370.dasd.fba;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import dev.hawala.vm370.dasd.iDasd;
import dev.hawala.vm370.vm.device.iDeviceIO;
import dev.hawala.vm370.vm.device.iDeviceStatus;
import dev.hawala.vm370.vm.machine.iProcessorEventTracker;

/**
 * Implementation of a FBA DASD drive.
 * 
 * <p>
 * This class implements a FBA drive with the characteristics of a
 * 3310 device, based on the document:
 * GA26-1660-1_3310_Direct_Access_Storage_Reference_Mar79.pdf
 * </p>
 * 
 * <p>
 * The implementation is a bit simplistic, as the complete content
 * of the disk is loaded as uncompressed byte-array into memory and
 * no delta information is tracked, so the complete content is saved
 * back to disk. 
 * </p>
 * 
 * <p>
 * As the complete content of the FBA-drive is loaded into memory,
 * the size of disks that can be accessed is limited to 2 MBytes.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 * 
 */
public class FbaDrive implements iDasd {
	
	private static final int BYTES_PER_BLOCK = 512;
	private static final int MAX_BLOCKS = 126016; // max. blocks of a 3310 drive
	private static final int HEADS = 11;
	private static final int BLOCKS_PRE_HEAD = 32;
	private static final int BLOCKS_PRE_CYL = BLOCKS_PRE_HEAD * HEADS;
	
	private iProcessorEventTracker eventLogger;
	
	private boolean writeProtected;
	
	private boolean isModified = false; // has the content been modified and is saving the drive necessary?
	
	private byte[] fbaBytes; // complete fba disk content...
	
	private int fbaPageCount; // number of 512-byte pages
	
	private final boolean isFilebacked;
	
	private static class NullTracker implements iProcessorEventTracker {
		public void logLine(String line, Object... args) {};
	}
	
	/*
	 * public creation and management
	 */
	
	public FbaDrive(String fbaFileName) throws Exception {
		this(fbaFileName, false, new NullTracker());
	}
	
	public FbaDrive(String fbaFileName, boolean writeProtected) throws Exception {
		this(fbaFileName, writeProtected, new NullTracker());
	}
	
	public FbaDrive(String fbaFileName, boolean writeProtected, iProcessorEventTracker eventLogger) throws Exception {
		File fbaFile = new File(fbaFileName + ".delta");
		if (!fbaFile.exists() || !fbaFile.isFile()) {
			System.out.println("** Delta for FBA-File '" + fbaFileName + "' not found, using primary FBA file");
			fbaFile = new File(fbaFileName);
		}
		if (!fbaFile.exists() || !fbaFile.isFile()) { throw new IOException("FBA-File '" + fbaFileName + "': not found"); }
		if (!fbaFile.canRead()) { throw new IOException("FBA-File '" + fbaFileName + "': cannot be read"); }
		
		long fbaSize = fbaFile.length();
		if (fbaSize < 1 || (fbaSize % 512) != 0) { throw new IOException("FBA-File '" + fbaFileName + "': invalid size"); }
//		if (fbaSize > (2 * 1024 * 1024)) { throw new IOException("FBA-File '" + fbaFileName + "': too large (> 2MByte)"); }
		this.fbaPageCount = (int)(fbaSize / 512);
		this.fbaBytes = new byte[(int)(fbaSize & 0x00FFFFFF)];
		
		InputStream is = new FileInputStream(fbaFile);
		int bytesRead = is.read(this.fbaBytes);
		is.close();
		if (bytesRead != fbaSize) { throw new IOException("FBA-File '" + fbaFileName + "': error reading complete file content"); }
		
		this.eventLogger = eventLogger;
		this.writeProtected = writeProtected;
		
		this.isFilebacked = true;
	}
	
	public FbaDrive(int blockCount, iProcessorEventTracker eventLogger) throws Exception {
		int fbaSize = blockCount * 512;
		if (fbaSize > (2 * 1024 * 1024)) { throw new IOException("FBA-File (temp): too large (> 2MByte)"); }
		
		this.fbaPageCount = blockCount;
		this.fbaBytes = new byte[fbaSize];
		
		this.eventLogger = eventLogger;
		this.writeProtected = false;
		
		this.isFilebacked = false;
	}
	
	public void setEventTracker(iProcessorEventTracker eventLogger) {
		if (eventLogger == null) {
			this.eventLogger = new NullTracker();
		} else {
			this.eventLogger = eventLogger;
		}
	}
	
	/*
	 * UnitStatus and Sense data management (as far as supported / meaningful for this implementation)
	 */
	
	// for Sense byte 0
	private static final int Sense_CommandReject        = 0x00800000; // bit 0: Define Extent mask prohibits the operation | READ/WRITE not chaing form LOCATE
	
	// for Sense byte 1
	private static final int Sense_FileProtected        = 0x00040000; // bit 5: LOCATE violates logical extent limits of DEFINE EXTENT
	
	// Sense bytes
	private final byte[] senseBytes = new byte[24];
	
	private static final byte _FF = (byte)0xFF;
	
	private void senseDriveCylHead() {
		Arrays.fill(this.senseBytes, (byte)0); // reset all sense data
		if (this.extentBase < 0 || this.extentBegin < 0 || this.extentEnd < 0
			|| this.blockOffset < 0 || this.blockCount < 0
			|| this.extentBase >= MAX_BLOCKS || this.extentBegin >= MAX_BLOCKS || this.extentEnd >= MAX_BLOCKS
			|| this.blockOffset >= MAX_BLOCKS || this.blockCount >= MAX_BLOCKS
			|| (this.extentBase + this.extentBegin + this.blockOffset + this.blockCount) > MAX_BLOCKS) {
			this.senseBytes[3] = _FF;
			this.senseBytes[4] = _FF;
			this.senseBytes[5] = _FF;
			this.senseBytes[6] = _FF;
			return;
		}
		
		int lastSeek = this.extentBegin + this.blockOffset;
		int cyl = lastSeek / BLOCKS_PRE_CYL;
		int blockInCyl = lastSeek - (cyl * BLOCKS_PRE_CYL);
		int head = blockInCyl / BLOCKS_PRE_HEAD;
		int sector = blockInCyl - (head * BLOCKS_PRE_HEAD);

		this.senseBytes[3] = (byte)((cyl & 0xFF00) >> 8);
		this.senseBytes[4] = (byte)(cyl & 0xFF);
		this.senseBytes[5] = (byte)(head & 0xFF);
		this.senseBytes[6] = (byte) (sector & 0xFF);
	}
	
	private int exitOk() {
		this.senseDriveCylHead();
		this.eventLogger.logLine("       => DEVICE_END");
		return iDeviceStatus.OK | iDeviceStatus.DEVICE_END;
	}
	
	private int exitUnitCheck(int senseFlags) {
		this.senseDriveCylHead();
		this.senseBytes[0] = (byte)((senseFlags & 0x00FF0000) >> 16);
		this.senseBytes[1] = (byte)((senseFlags & 0x0000FF00) >> 8);
		this.senseBytes[2] = (byte)(senseFlags & 0x000000FF);
		this.eventLogger.logLine("       => UNIT_CHECK, DEVICE_END [%s%s ]",
				((senseFlags & Sense_CommandReject) != 0) ? " CommandReject" : "",
				((senseFlags & Sense_FileProtected) != 0) ? " FileProtected" : "");
		return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
	}
	
	
	/*
	 * current positioning data and flags
	 */
	
	private boolean allowWriteFormat = false;
	private boolean allowWriteNormal = false;
	
	private int extentBase = 0;  // offset, in blocks, from the beginning of the device to the first block of the extent.
	private int extentBegin = 0; // relative displacement, in blocks, from the beginning of the data set to the *first* block of the extent
	private int extentEnd = 0;   // relative displacement, in blocks, from the beginning of the data set to the *last* block of the extent
	
	private int blockOffset = 0;
	private int blockCount = 0;
	private boolean isBlockWrite = false;
	
	private boolean requireDefineExtent = true;
	private boolean requireLocateBlock = true;
	
	
	/*
	 * iDevice interface
	 */

	// prepare processing of a new CCW-chain
	// see: iDevice
	@Override
	public void resetState() {
		this.requireDefineExtent = true;
		this.requireLocateBlock = true;
		
		this.allowWriteFormat = false;
		this.allowWriteNormal = false;
		
		this.extentBase = 0;
		this.extentBegin = 0;
		this.extentEnd = 0;
		
		this.blockOffset = 0;
		this.blockCount = 0;
		this.isBlockWrite = false;
	}
	
	private final byte[] controlBuffer = new byte[16];  

	// perform a control I/O operation
	// see: iDevice
	@Override
	public int control(int opcode, int dataLength, iDeviceIO memSource) {
		byte[] b = this.controlBuffer; // just a shortcut

		switch(opcode)
		{
		case 0x43: // LOCATE
			// is command ordering OK?
			if (this.requireDefineExtent) {
				this.eventLogger.logLine(".. .. FbaDrive: LOCATE without DEFINE EXTENT");
				return this.exitUnitCheck(Sense_CommandReject);
			}
			
			// set command chaining check flags (in case this command fails)
			this.requireLocateBlock = true;
			
			// transfer command parameters
			int resLocate = memSource.transfer(b, 0, 8);
			this.eventLogger.logLine(".. .. FbaDrive: LOCATE (%d) 0x %02X %02X %02X %02X %02X %02X",
					resLocate, b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7]);
			if (resLocate < 0) {
				return this.exitUnitCheck(Sense_CommandReject);
			}
			
			// get block range for transfer and check plausibility
			this.blockCount = ((b[2] & 0xFF) << 8) | (b[3] & 0xFF);
			this.blockOffset = ((b[4] & 0xFF) << 24) | ((b[5] & 0xFF) << 16) | ((b[6] & 0xFF) << 8) | (b[7] & 0xFF);
			this.eventLogger.logLine("       blockOffset = %d ; blockCount = %d", this.blockOffset, this.blockCount);
			if (this.blockCount == 0) {
				return this.exitUnitCheck(Sense_CommandReject);
			}
			if (this.blockOffset < 0
				|| this.blockOffset >= MAX_BLOCKS || this.blockCount >= MAX_BLOCKS
				|| (this.extentBase + this.extentBegin + this.blockOffset + this.blockCount) > MAX_BLOCKS) {
				return this.exitUnitCheck(Sense_CommandReject);
			}
			
			// check if the block is in the extent specified before
			if (this.blockOffset < this.extentBegin || this.blockCount > (this.extentEnd + 1 - this.extentBegin)) {
				return this.exitUnitCheck(Sense_FileProtected);
			}
			
			// get transfer direction info
			if (b[0] == 0x01 || b[0] == 0x05) {
				this.isBlockWrite = true;
			} else if (b[0] == 0x06) {
				this.isBlockWrite = false;
			} else {
				return this.exitUnitCheck(Sense_CommandReject);
			}
			this.eventLogger.logLine("       i/o operation = %s", (this.isBlockWrite) ? "write" : "read");
			
			// return OK
			this.requireLocateBlock = false;
			return this.exitOk();
			
		case 0x63: // DEFINE EXTENT
			// transfer command parameters
			int resDefineExtent = memSource.transfer(b, 0, 16);
			this.eventLogger.logLine(".. .. FbaDrive: DEFINE EXTENT (%d) 0x %02X %02X %02X %02X %02X %02X  %02X %02X %02X %02X %02X %02X",
					resDefineExtent,
					b[0], b[1], b[2], b[3], b[4], b[5], b[6], b[7],
					b[8], b[9], b[10], b[11], b[12], b[13], b[14], b[15]);
			if (resDefineExtent < 0) {
				return this.exitUnitCheck(Sense_CommandReject);
			}
			
			// set command chaining check flags (in case this command fails)
			this.requireDefineExtent = true;
			this.requireLocateBlock = true;
			
			// get extent definition and check for plausibility
			this.extentBase = ((b[4] & 0xFF) << 24) | ((b[5] & 0xFF) << 16) | ((b[6] & 0xFF) << 8) | (b[7] & 0xFF);
			this.extentBegin = ((b[8] & 0xFF) << 24) | ((b[9] & 0xFF) << 16) | ((b[10] & 0xFF) << 8) | (b[11] & 0xFF);
			this.extentEnd = ((b[12] & 0xFF) << 24) | ((b[13] & 0xFF) << 16) | ((b[14] & 0xFF) << 8) | (b[15] & 0xFF);
			this.eventLogger.logLine("       extentBase = %d ; extentBegin = %d ; extentEnd = %d",
					this.extentBase, this.extentBegin, this.extentEnd);
			if (this.extentBase < 0 || this.extentBegin < 0 || this.extentEnd < 0
				|| this.extentBase >= MAX_BLOCKS || this.extentBegin >= MAX_BLOCKS || this.extentEnd >= MAX_BLOCKS
				|| this.extentBegin > this.extentEnd
				|| (this.extentBase + this.extentBegin) > MAX_BLOCKS) {
				return this.exitUnitCheck(Sense_CommandReject);
			}
			
			// check extent range for this disk
			if ((this.extentBase + this.extentEnd) > this.fbaPageCount) {
				return this.exitUnitCheck(Sense_CommandReject);
			}
			
			// get write allowed flags
			if ((b[0] & 0xC0) == 0xC0) {
				this.allowWriteFormat = true;
				this.allowWriteNormal = true;
			} else if ((b[0] & 0xC0) == 0x40) {
				this.allowWriteFormat = false;
				this.allowWriteNormal = false;
			} else if ((b[0] & 0xC0) == 0x00) {
				this.allowWriteFormat = false;
				this.allowWriteNormal = true;
			} else {
				return this.exitUnitCheck(Sense_CommandReject);
			}
			
			this.eventLogger.logLine("       allowWriteFormat = %s ; allowWriteNormal = %s",
					Boolean.toString(this.allowWriteFormat), Boolean.toString(this.allowWriteNormal));
			
			// return OK
			this.requireDefineExtent = false;
			return this.exitOk();
		
		case 0x03: // NO-OPERATION   
			return this.exitOk();
		
		default:
			return this.exitUnitCheck(Sense_CommandReject);
		}
	}

	// perform a read I/O operation
	// see: iDevice
	@Override
	public int read(int opcode, int dataLength, iDeviceIO memTarget) {

		switch(opcode)
		{
		case 0x02: // READ IPL 
			this.eventLogger.logLine(".. .. FbaDrive: READ IPL :: dataLength = %d", dataLength);
			int iplTrf = memTarget.transfer(this.fbaBytes, 0, BYTES_PER_BLOCK); // IPL data is at start of drive and max. one sector
			this.eventLogger.logLine("       transferred bytes = %d", iplTrf);
			return this.exitOk(); // channel should handle length differences
			
		case 0x42: // READ
			if (this.requireDefineExtent || this.requireLocateBlock) {
				this.eventLogger.logLine(".. .. FbaDrive: READ :: no preceeding DEFINE EXTENT resp. LOCATE");
				return this.exitUnitCheck(Sense_CommandReject);
			}
			if (this.isBlockWrite) {
				this.eventLogger.logLine(".. .. FbaDrive: READ :: not allowed (LOCATE => write)");
				return this.exitUnitCheck(Sense_CommandReject);
			}
			
			int byteOffset = (this.extentBase + this.extentBegin + this.blockOffset) * BYTES_PER_BLOCK;
			int bytesAvailable = this.blockCount * BYTES_PER_BLOCK;
			this.eventLogger.logLine(".. .. FbaDrive: READ [ block { byteOffset = %d ; bytesAvail = %d } ; dataLength = %d]",
					byteOffset, bytesAvailable, dataLength);
			int transferred = memTarget.transfer(this.fbaBytes, byteOffset, bytesAvailable);
			this.eventLogger.logLine("       transferred bytes = %d", transferred);
			
			return this.exitOk(); // channel should handle length differences 
		
		default:
			return this.exitUnitCheck(Sense_CommandReject);
		}
	}

	// perform a write I/O operation
	// see: iDevice
	@Override
	public int write(int opcode, int dataLength, iDeviceIO memSource) {

		switch(opcode)
		{
		case 0x41: // WRITE
			if (this.requireDefineExtent || this.requireLocateBlock) {
				this.eventLogger.logLine(".. .. FbaDrive: WRITE :: no preceeding DEFINE EXTENT resp. LOCATE");
				return this.exitUnitCheck(Sense_CommandReject);
			}
			if (!this.isBlockWrite) {
				this.eventLogger.logLine(".. .. FbaDrive: WRITE :: not allowed (LOCATE => read)");
				return this.exitUnitCheck(Sense_CommandReject);
			}
			if (!this.allowWriteNormal) {
				this.eventLogger.logLine(".. .. FbaDrive: WRITE :: not allowed (DEFINE EXTENT flags)");
				return this.exitUnitCheck(Sense_CommandReject);
			}
			if (this.writeProtected) {
				this.eventLogger.logLine(".. .. FbaDrive: WRITE :: write-protected");
				return this.exitUnitCheck(Sense_CommandReject);
			}
			
			int byteOffset = (this.extentBase + this.extentBegin + this.blockOffset) * BYTES_PER_BLOCK;
			int bytesAvailable = this.blockCount * BYTES_PER_BLOCK;
			this.eventLogger.logLine(".. .. FbaDrive: WRITE [ block { byteOffset = %d ; bytesAvail = %d } ; dataLength = %d ]",
					byteOffset, bytesAvailable, dataLength);
			int transferred = memSource.transfer(this.fbaBytes, byteOffset, bytesAvailable);
			this.eventLogger.logLine("       transferred bytes = %d", transferred);
			
			this.isModified = true;
			
			return this.exitOk(); // channel should handle length differences 
		
		default:
			return this.exitUnitCheck(Sense_CommandReject);
		}
	}

	// perform a sense I/O operation
	// see: iDevice
	@Override
	public int sense(int opcode, int dataLength, iDeviceIO memTarget) {

		switch(opcode)
		{
		case 0x64: // READ DEVICE CHARACTERISTICS
			byte b0 = (byte)((this.fbaPageCount >> 24) & 0xFF);
			byte b1 = (byte)((this.fbaPageCount >> 16) & 0xFF);
			byte b2= (byte)((this.fbaPageCount >> 8) & 0xFF);
			byte b3 = (byte)(this.fbaPageCount & 0xFF);
			byte[] characteristics = { // see GC20-1878-0_A_Guide_to_the_IBM_4331_Processor_Mar79.pdf page 4-11 (26)
				0x30, // operation modes
				0x08, // features
				0x21, // device class
				0x01, // unit type
				0x02, 0x00, // physical record size
				0x00, 0x00, 0x00, 0x20, // number of blocks per cyclical group
				0x00, 0x00, 0x01, 0x60, // number of blocks per access position
				b0, b1, b2, b3, // number of blocks under movable heads (here: effective page count of device)
				0x00, 0x00, 0x00, 0x00, // reserved
				0x00, 0x00, // reserved
				0x01, 0x60, // number of blocks in CE area
				0x00, 0x00, // reserved
				0x00, 0x00, // reserved
				0x00, 0x00  // reserved
			};
			this.eventLogger.logLine(".. .. FbaDrive: READ DEVICE CHARACTERISTICS [ dataLength = %d ]", dataLength);
			memTarget.transfer(characteristics, 0, characteristics.length);
			return this.exitOk();
			
		case 0x04: // SENSE
			this.eventLogger.logLine(".. .. FbaDrive: SENSE [ dataLength = %d ]", dataLength);
			memTarget.transfer(this.senseBytes, 0, this.senseBytes.length);
			Arrays.fill(this.senseBytes, (byte)0); // reset sense data
			return this.exitOk();
			
		case 0xE4: // SENSE I/O 
			byte[] senseIoresult = { _FF, 0, 0, 0, 0, 0, 0 }; // dummy data
			this.eventLogger.logLine(".. .. FbaDrive: SENSE I/O [ dataLength = %d ]", dataLength);
			memTarget.transfer(senseIoresult, 0, senseIoresult.length);
			return this.exitOk();
			
		case 0xA4: // READ AND RESET BUFFERED LOG
			// not implemented yet
			return this.exitUnitCheck(Sense_CommandReject);
		
		default:
			return this.exitUnitCheck(Sense_CommandReject);
		}
	}
	
	// check for attention interrupt
	// see: iDevice
	@Override
	public boolean hasPendingAsyncInterrupt() { return false; } // FBA devices do not have asynchronous events
	
	// remove one pending attention interrupt
	// see: iDevice
	@Override
	public void consumeNextAsyncInterrupt() {} // no asynchronous events to consume
	
	// enqueue an attention interrupt
	// see: iDevice
	@Override
	public void doAttentionInterrupt() {} // no attention interrupts for FBA drives 

	// get the virtual device information for DIAG-x24
	// see: iDevice
	@Override
	public int getVDevInfo() {
		// see: SC24-5288-0_VM_SP_System_facilities_for_Programmers_release_5_Dec86.pdf
		//      Appendix A, pp. 432 etc.
		// also: GC20-1878-0_A_Guide_to_the_IBM_4331_Processor_Mar79.pdf
		int vdevInfo = 
			// VDEVTYPC = Virtual device type class :: Fixed-Block Storage = 0x01
			((0x01) << 24)
			// VDEVTYPE = Virtual device type :: 3310 = 0x01
		  | ((0x01) << 16)
		    // VDEVSTAT = Virtual device status
		  | 0x00000000 // (none)
		    // VDEVFLAG = Virtual device flags
		  | ((this.writeProtected) ? 0x00000080 : 0x00000000) // VDEVRDO = DASD - READONLY
		  | ((!this.isFilebacked)  ? 0x00000040 : 0x00000000) // VDEVTDSK = DASD - T-DISK SPACE ALLOCATED BY CP
		;
		return vdevInfo;
	}

	// get the real device information for DIAG-x24
	// see: iDevice
	@Override
	public int getRDevInfo() {
		int rdevInfo = 
			// RDEVTYPC = Real device type class :: Fixed-Block Storage = 0x01
			(0x01 << 24)
			// RDEVTYPE = Real device type :: 3310 = 0x01
		  | (0x01 << 16)
		    // RDEVMDL = Real device model number :: ?? => 0
		  | (0x00 << 8)
		    // RDEVFTR = Real device feature code (for a device other than the keyboard/ display)
		  | 0x00000000 // (none known)
		;
		return rdevInfo;
	}

	// get a single sense byte
	// see: iDevice
	@Override
	public byte getSenseByte(int index) {
		if (index < 0 || index >= this.senseBytes.length) { return (byte)0; }
		return this.senseBytes[index];
	}
	
	// get the printable device type name
	// see: iDevice
	@Override
	public String getCpDeviceTypeName() {
		return "DASD";
	}
	
	// get the device information for QUERY VIRTUAL
	// see: iDevice
	@Override
	public String getCpQueryStatusLine(int asCuu) {
		return String.format(
				"DASD %03X 3310 emx370 %s %6d BLK",
				asCuu,
				(this.writeProtected) ? "R/O" : "R/W",
				this.fbaPageCount);
	}

	/*
	 * IDasd specific
	 */

	@Override
	public boolean needsSaving() {
		return this.isModified;
	}

	@Override
	public void saveTo(String deltaFile, String baseFile) throws Exception {
		
		String filename = null;
		
		/*
		 * figure out what to do and open the file
		 */
		if (deltaFile == null && baseFile == null) {
			throw new Exception("Both deltaFile and baseFile unspecified");
		}
		if (deltaFile != null) {
			filename = deltaFile;
		} else {
			filename = baseFile;
		}
		
		System.out.println("..saving FBA drive to file: " + filename);
		
		FileOutputStream fos = new FileOutputStream(filename);
		fos.write(this.fbaBytes);
		fos.close();
	}

	@Override
	public void setWriteProtected() {
		this.writeProtected = true;
	}
	
	@Override
	public boolean isWriteProtected() {
		return this.writeProtected;
	}
}
