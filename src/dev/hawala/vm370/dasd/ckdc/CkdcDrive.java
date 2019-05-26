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

package dev.hawala.vm370.dasd.ckdc;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;

import dev.hawala.vm370.dasd.iDasd;
import dev.hawala.vm370.vm.device.iDeviceIO;
import dev.hawala.vm370.vm.device.iDeviceStatus;
import dev.hawala.vm370.vm.machine.iProcessorEventTracker;

/**
 * Implementation of a CKD DASD drive for all supported drive types
 * (see {@link CkdDriveType}).
 * 
 * <p>
 * This class implements the iDevice and iDasd interface for a device
 * and uses/relies on the internal representation of the in-memory
 * representation of the cylinder and track contents of a CKD drive. 
 * </p>
 * 
 * <p>
 * Based on the information about 3340 drives in pages 136.1 .. 156
 * in: GA33-1510-1_IBM_System_370_Model_115_Functional_Characteristics_Jul76.pdf
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public class CkdcDrive extends DriveBaseExternalIO implements iDasd {
	
	protected iProcessorEventTracker eventLogger;
	
	protected boolean isFileBacked = true; // was the drive loaded from a file/stream or later saved to a file/stream?
	
	protected boolean isModified = false; // has the content been modified and is saving the drive necessary?
	
	private class NullTracker implements iProcessorEventTracker {
		public void logLine(String line, Object... args) {};
	}
	
	/*
	** Constructors: delegate to the real implementation of the superclass
	*/
	
	public CkdcDrive(
			byte[] ddrVolser,
			int ddrCylinderCount,
			int ddrTracksPerCylinder,
			CkdDriveType driveType,
			int ddrMaxRecordsPerTrack,
			Track[] ddrTracks) throws Exception {
		super(ddrVolser, ddrCylinderCount, ddrTracksPerCylinder, driveType, ddrMaxRecordsPerTrack, ddrTracks);
		this.isFileBacked = false;
		this.isModified = true;
		this.eventLogger = new NullTracker();
	}
	
	public CkdcDrive(String deltaFile, String baseFile, iProcessorEventTracker eventTracker) throws Exception {
		super(deltaFile, baseFile);
		this.isFileBacked = true;
		this.eventLogger = (eventTracker == null) ? new NullTracker() : eventTracker;
	}
	
	public CkdcDrive(InputStream deltaFis, InputStream baseFis, iProcessorEventTracker eventTracker) throws Exception {
		super(deltaFis, baseFis);
		this.isFileBacked = true;
		this.eventLogger = (eventTracker == null) ? new NullTracker() : eventTracker;
	}
	
	public void setEventTracker(iProcessorEventTracker eventTracker) {
		this.eventLogger = (eventTracker == null) ? new NullTracker() : eventTracker;
	}
	
	public boolean needsSaving() { return this.isModified; }
	
	/*
	** Overrides for superclass methods 
	*/
	
	@Override
	public void save(OutputStream os, boolean isDeltaTarget) throws Exception {
		// ensure that all tracks are storable, i.e. finalizing a possible formatting state
		if (this.bufferedtracks != null) {
			while(this.bufferedtracks.size() > 0) {
				Track t = this.bufferedtracks.get(0);
				this.freeTrackBuffer(t);
			}
		}
		// store the drive
		super.save(os, isDeltaTarget);
		this.isFileBacked = true;
		this.isModified = false;
	}
	
	
	/*
	** UnitStatus and Sense data management
	**
	** ATTENTION: this uses a simplified 3340 sense information for all drive types (3350 etc.) 
	*/
	
	// for Sense byte 0
	private static final int Sense_CommandReject        = 0x00800000;
	//private static final int Sense_InterventionRequired = 0x00400000; // unused
	private static final int Sense_EquipmentCheck       = 0x00100000;
	//private static final int Sense_DataCheck            = 0x00080000; // unused
	//private static final int Sense_Overrun              = 0x00040000; // unused
	//private static final int Sense_TrackConditionCheck  = 0x00020000; // unused
	private static final int Sense_SeekCheck            = 0x00010000;
	
	// for Sense byte 1
	//private static final int Sense_InvalidTrackFormat   = 0x00004000; // unused
	//private static final int Sense_EndOfCylinder        = 0x00002000; // unused
	private static final int Sense_RecordNotFound       = 0x00000800;
	private static final int Sense_FileProtected        = 0x00000400;
	private static final int Sense_WriteInhibited       = 0x00000200;
	//private static final int Sense_OperationIncomplete  = 0x00000100; // unused
	
	// Sense bytes
	private final byte[] senseBytes = new byte[24];
	
	private void senseDriveCylHead() {
		Arrays.fill(this.senseBytes, (byte)0); // reset all sense data
		this.senseBytes[4] = (byte)0x80; // Drive A
		this.senseBytes[5] = (byte)(this.currCyl & 0xFF);
		this.senseBytes[6] = (byte)( ((this.currCyl & 0x07) >> 3) | (this.currHead & 0x1F) );  
	}
	
	private int opOK(int flags) {
		this.senseDriveCylHead();
		return flags | iDeviceStatus.DEVICE_END;
	}
	
	private int exitOk() { return this.opOK(iDeviceStatus.OK); }
	
	private int exitOkSkipCCW() { return this.opOK(iDeviceStatus.STATUS_MODIFIER); }
	
	private int exitOkLengthIsOK() { return this.opOK(iDeviceStatus.INCORRECT_LENGTH_IS_OK); }
	
	private int exitUnitCheck(int senseFlags) {
		this.senseDriveCylHead();
		this.senseBytes[0] = (byte)((senseFlags & 0x00FF0000) >> 16);
		this.senseBytes[1] = (byte)((senseFlags & 0x0000FF00) >> 8);
		this.senseBytes[2] = (byte)(senseFlags & 0x000000FF);
		return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
	}
	
	/* .. unused??
	private int exitUnitException() {
		this.senseDriveCylHead();
		return IDeviceStatus.UNIT_EXCEPTION | IDeviceStatus.DEVICE_END;
	}
	*/
	

	
	/*
	** Implementation of interface iDevice
	** 
	** read/write operations for a S/370 CKD drive:
	** - similar to a 3340 device as defined in: GA33-1510-1 System/370 Model 115 Functional Characteristics
	** - but partly simplified (pre-condition checks, sense data in case of problems)... 
	*/
	
	// number of unpacked tracks to buffer
	private static final int TRACK_BUFFER_COUNT = 16;
	
	// buffers to unpacking the track content when accessing tracks
	private ArrayList<byte[]> trackBuffers= null;
	
	// tracks currently holding a buffer == these tracks are directly accessible without calling their access() method
	private ArrayList<Track> bufferedtracks = null;
	
	// simple counter for ordering the tracks for LRU deaccessing of "oldest" track
	private long accessCounter = 0;
	
	// current track information
	private int currCyl = -1;
	private int currHead = -1;
	private Track currTrack = null;
	
	// is the drive itself write protected?
	private boolean writeProtected = false;
	
	// flags controlling write access (control command "Set File Mask")
	private boolean allowAllWrites = true; 
	private boolean forbidWHAandWR0 = true;
	private boolean forbidFormatting = true;
	
	// flags controlling search/seek access (control command "Set File Mask")
	private boolean allowSeek = true;
	private boolean allowSeekCyl = true;
	private boolean allowSeekHead = true;
	
	// see: iDasd
	public void setWriteProtected() { this.writeProtected = true; }
	
	// see: iDasd
	public boolean isWriteProtected() { return this.writeProtected; }
	
	// set the track identified by cylinder and head to the current track
	private boolean  gotoTrack(int cyl, int head) {
		if (this.currCyl == cyl && this.currHead == head) { return true; } // already there
		if (cyl < 0) { cyl = this.currCyl; }
		if (head < 0) { head = this.currHead; }
		this.currCyl = -1;
		this.currHead = -1;
		if (cyl < 0 || head < 0 || cyl >= this.cylinderCount || head >= this.tracksPerCylinder) {
			this.currTrack = null;
			return false;
		}
		this.currTrack = this.tracks[this.getCylAndHeadIndex(cyl, head)];
		if (this.currTrack != null) {
			this.currCyl = cyl;
			this.currHead = head;
			return true;
		}
		
		// track not present...?
		return false;
	}
	
	// return the track-buffer for unpacked track content to the free pool
	private void freeTrackBuffer(byte[] buffer) {
		if (buffer == null) { return; }
		if (!this.trackBuffers.contains(buffer)) {
			this.trackBuffers.add(buffer);
		}
	}
	
	// deaccess the given track and return bound resources to the free pool
	private void freeTrackBuffer(Track track) throws IOException {
		byte[] buffer = track.deAccess();
		this.bufferedtracks.remove(track);
		this.freeTrackBuffer(buffer);
	}
	
	// get a free track-buffer, deaccessing the least used buffered track if necessary  
	private byte[] getFreeTrackBuffer() throws IOException {
		byte[] zeBuffer;
		if (this.trackBuffers.isEmpty()) {
			Track track = this.bufferedtracks.get(0);
			long lowest = track.getAccessedCount();
			for (Track cand : this.bufferedtracks) {
				long candAccCount = cand.getAccessedCount();
				if (candAccCount < lowest) {
					track = cand;
					lowest = candAccCount;
				}
			}
			zeBuffer = track.deAccess();
			this.bufferedtracks.remove(track);
			//System.out.printf("         --- bumped track buffer cyl %d head %d\n", track.getCylNo(), track.getHeadNo()); // #######
		} else {
			int lastIdx = this.trackBuffers.size()-1;
			zeBuffer = this.trackBuffers.get(lastIdx);
			this.trackBuffers.remove(lastIdx);
		}
		return zeBuffer;
	}
	
	// access the specified track and access it (i.e. unpacking the content), adding it to the buffered track set
	private void accessTrack(Track track) throws IOException {
		track.setAccessedCount(this.accessCounter++);
		if (this.bufferedtracks.contains(track)) { return; }
		track.access(this.getFreeTrackBuffer());
		this.bufferedtracks.add(track);
	}

	// prepare processing of a new CCW-chain
	// see: iDevice
	@Override
	public void resetState() {
		if (this.trackBuffers == null) {
			// initialize buffers
			this.trackBuffers = new ArrayList<byte[]>();
			this.bufferedtracks = new ArrayList<Track>();
			for (int i = 0; i < TRACK_BUFFER_COUNT; i++) {
				this.trackBuffers.add(new byte[this.maxTrackSize]);
			}
			// go to a hopefully valid track (cylinder 0 and head 0 should be there)
			this.gotoTrack(0, 0);
		}
		
		this.allowAllWrites = true; 
		this.forbidWHAandWR0 = true;
		this.forbidFormatting = true;
		
		this.allowSeek = true;
		this.allowSeekCyl = true;
		this.allowSeekHead = true;
		
		this.eventLogger.logLine(".. .. resetState()");
	}
	
	private static final String READ_OK_MSG = ".. .. %s (dataLength = %d) => OK";

	// perform a device read I/O operation
	// see: iDevice
	@Override
	public int read(int opcode, int dataLength, iDeviceIO memTarget) {
		byte[] tmpBuffer = null;
		String opName = "GenericRead/currTrack";
		try { 
			if (this.currTrack == null) {
				this.eventLogger.logLine(
						".. .. %s (dataLength = %d) :: currTrack == null => UnitCheck(CommandReject + Sense_SeekCheck)",
						opName, dataLength);
				return this.exitUnitCheck(Sense_CommandReject | Sense_SeekCheck);
			}
			opName = "GenericRead/accessTrack";
			this.accessTrack(this.currTrack);
			switch(opcode)
			{
			case 0x06: // Read Data
				opName = "Read Data";
				this.currTrack.readData(memTarget, dataLength);
				this.eventLogger.logLine(READ_OK_MSG, opName, dataLength);
				return this.exitOk();
	
			case 0x0E: // Read Key and Data
				opName = "Read Key and Data";
				this.currTrack.readKeyAndData(memTarget, dataLength);
				this.eventLogger.logLine(READ_OK_MSG, opName, dataLength);
				return this.exitOk();
	
			case 0x1E: // Read Count, Key and Data
				opName = "Read Count, Key and Data";
				tmpBuffer = this.getFreeTrackBuffer();
				this.currTrack.readCountKeyAndData(memTarget, dataLength, tmpBuffer);
				this.eventLogger.logLine(READ_OK_MSG, opName, dataLength);
				return this.exitOk();
	
			case 0x16: // Read Record Zero
				opName = "Read Record Zero";
				this.currTrack.readRecordZero(memTarget, dataLength);
				this.eventLogger.logLine(READ_OK_MSG, opName, dataLength);
				return this.exitOk();
	
			case 0x12: // Read Count
				opName = "Read Count";
				tmpBuffer = this.getFreeTrackBuffer();
				this.currTrack.readCount(memTarget, dataLength, tmpBuffer);
				this.eventLogger.logLine(READ_OK_MSG, opName, dataLength);
				return this.exitOk();
	
			case 0x1A: // Read Home Address
				opName = "Read Home Address";
				this.currTrack.readHomeAddress(memTarget, dataLength);
				this.eventLogger.logLine(READ_OK_MSG, opName, dataLength);
				return this.exitOk();
	
			case 0x02: // Read Initial Program Load
				opName = "Read Initial Program Load";
				this.gotoTrack(0, 0);
				if (this.currTrack == null) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: cyl 0 / head 0 not found => UnitCheck(CommandReject + Sense_SeekCheck)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject | Sense_SeekCheck);
				}
				this.accessTrack(this.currTrack);
				if (!this.currTrack.searchIdEqual(1)) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: record 1 not found in cyl 0 / head 0 => UnitCheck(EquipmentCheck)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_RecordNotFound);
				}
				if (!this.currTrack.searchIdEqual(1)) {
					this.eventLogger.logLine("no CKD with id = 1 found on track 0 head 0");
					this.currTrack.moveToCKDIndex(0);
				}
				this.currTrack.readData(memTarget, dataLength);
				this.eventLogger.logLine(READ_OK_MSG, opName, dataLength);
				return this.exitOk();
			
			default:
				this.eventLogger.logLine(
						".. .. unknown READ opcode 0x%02X (dataLength = %d) => UnitCheck(CommandReject)",
						opcode, dataLength);
				return exitUnitCheck(Sense_CommandReject);
			}
		} catch (Exception exc) {
			this.eventLogger.logLine(
					".. .. %s (dataLength = %d) :: caught Exception[%s] => UnitCheck(EquipmentCheck)",
					opName, dataLength, exc.getMessage());
			return this.exitUnitCheck(Sense_EquipmentCheck);
		} finally {
			if (tmpBuffer != null) { this.freeTrackBuffer(tmpBuffer); }
		}

		// should we ever get here...
		//return exitUnitCheck(Sense_CommandReject);
	}
	
	// space to buffer the key for search operations
	private final byte[] searchBuffer = new byte[256]; // 1 byte for keyLen => max. value is 255 

	// perform a device write I/O operation
	// see: iDevice
	@Override
	public int write(int opcode, int dataLength, iDeviceIO memSource) {
		byte[] tmpBuffer = null;
		String opName = "GenericWrite/currTrack";
		try {
			switch(opcode)
			{
			case 0x05: // Write Data
			case 0x0D: // Write Key and Data
				if (this.currTrack == null) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: currTrack == null => UnitCheck(CommandReject + Sense_SeekCheck)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject | Sense_SeekCheck);
				}
				opName = "GenericWrite/checkWritable";
				if (this.writeProtected) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [writeProtected == true] => UnitCheck(WriteInhibited)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_WriteInhibited);
				}
				if (!this.allowAllWrites) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [allowAllWrites == false] => UnitCheck(CommandReject)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject);
				}
				opName = "GenericWrite/accessTrack";
				this.accessTrack(this.currTrack);
				this.isModified = true;
				if (opcode == 0x05) {
					opName = "Write Data";
					this.currTrack.writeData(memSource, dataLength);
				} else {
					opName = "Write Key and Data";
					this.currTrack.writeKeyAndData(memSource, dataLength);
				}
				this.eventLogger.logLine(".. .. %s (dataLength = %d) => OK / INCORRECT_LENGTH_IS_OK", opName, dataLength);
				return this.exitOkLengthIsOK();
	
			case 0x1D: // Write Count, Key and data
				if (this.currTrack == null) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: currTrack == null => UnitCheck(CommandReject + Sense_SeekCheck)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject | Sense_SeekCheck);
				}
				opName = "GenericWrite/checkWritable";
				if (this.writeProtected) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [writeProtected == true] => UnitCheck(WriteInhibited)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_WriteInhibited);
				}
				if (!this.allowAllWrites) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [allowAllWrites == false] => UnitCheck(CommandReject)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject);
				}
				if (this.forbidFormatting) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [forbidFormatting == true] => UnitCheck(CommandReject)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject);
				}
				opName = "GenericWrite/accessTrack";
				this.accessTrack(this.currTrack);
				
				opName = "Write Count, Key and Data";
				this.isModified = true;
				this.currTrack.beginFormat();
				
				tmpBuffer = this.getFreeTrackBuffer();
				int transferLength = (dataLength > tmpBuffer.length) ? tmpBuffer.length : dataLength;
				if (transferLength < 8) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [incomplete Count-Area] => UnitCheck(CommandReject)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject);
				}
				
				memSource.transfer(tmpBuffer, 0, transferLength);
				int fmtRecordNo = tmpBuffer[4] & 0xFF;
				int fmtKeyLen = tmpBuffer[5] & 0xFF;
				int fmtDataLen = ((tmpBuffer[6] & 0xFF) << 8) | (tmpBuffer[7] & 0xFF);
				this.currTrack.appendCountKeyAndData(fmtRecordNo, fmtKeyLen, fmtDataLen, tmpBuffer, 8, transferLength - 8);

				this.eventLogger.logLine(".. .. %s (dataLength = %d) => OK / INCORRECT_LENGTH_IS_OK", opName, dataLength);
				return this.exitOkLengthIsOK();
	
			case 0x01: // Write special Count, Key and Data
				if (this.currTrack == null) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: currTrack == null => UnitCheck(CommandReject + Sense_SeekCheck)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject | Sense_SeekCheck);
				}
				opName = "GenericWrite/checkWritable";
				if (this.writeProtected) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [writeProtected == true] => UnitCheck(WriteInhibited)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_WriteInhibited);
				}
				if (!this.allowAllWrites) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [allowAllWrites == false] => UnitCheck(CommandReject)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject);
				}
				if (this.forbidFormatting) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [forbidFormatting == true] => UnitCheck(CommandReject)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject);
				}
				opName = "GenericWrite/accessTrack";
				this.accessTrack(this.currTrack);
				opName = "Write special Count, Key and Data";
				this.isModified = true;
				// ... unimplemented
				this.eventLogger.logLine(
						".. .. %s (dataLength = %d) :: [NOT SUPPORTED] => UnitCheck(CommandReject)",
						opName, dataLength);
				return this.exitUnitCheck(Sense_CommandReject);
	
			case 0x15: // Write Record Zero
				if (this.currTrack == null) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: currTrack == null => UnitCheck(CommandReject + Sense_SeekCheck)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject | Sense_SeekCheck);
				}
				opName = "GenericWrite/checkWritable";
				if (this.writeProtected) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [writeProtected == true] => UnitCheck(WriteInhibited)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_WriteInhibited);
				}
				if (!this.allowAllWrites) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [allowAllWrites == false] => UnitCheck(CommandReject)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject);
				}
				if (this.forbidWHAandWR0) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [forbidWHAandWR0 == true] => UnitCheck(CommandReject)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject);
				}
				opName = "GenericWrite/accessTrack";
				this.accessTrack(this.currTrack);
				opName = "Write Record Zero";
				this.isModified = true;
				// ... unimplemented
				this.eventLogger.logLine(
						".. .. %s (dataLength = %d) :: [NOT SUPPORTED] => UnitCheck(CommandReject)",
						opName, dataLength);
				return this.exitUnitCheck(Sense_CommandReject);
	
			case 0x19: // Write Home Address 
				if (this.currTrack == null) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: currTrack == null => UnitCheck(CommandReject + Sense_SeekCheck)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject | Sense_SeekCheck);
				}
				opName = "GenericWrite/checkWritable";
				if (this.writeProtected) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [writeProtected == true] => UnitCheck(WriteInhibited)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_WriteInhibited);
				}
				if (!this.allowAllWrites) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [allowAllWrites == false] => UnitCheck(CommandReject)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject);
				}
				if (this.forbidWHAandWR0) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [forbidWHAandWR0 == true] => UnitCheck(CommandReject)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject);
				}
				opName = "GenericWrite/accessTrack";
				this.accessTrack(this.currTrack);
				opName = "Write Home Address";
				this.isModified = true;
				// ... unimplemented
				this.eventLogger.logLine(
						".. .. %s (dataLength = %d) :: [NOT SUPPORTED] => UnitCheck(CommandReject)",
						opName, dataLength);
				return this.exitUnitCheck(Sense_CommandReject);
	
			case 0x11: // Erase
				if (this.currTrack == null) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: currTrack == null => UnitCheck(CommandReject + Sense_SeekCheck)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject | Sense_SeekCheck);
				}
				opName = "GenericWrite/checkWritable";
				if (this.writeProtected) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [writeProtected == true] => UnitCheck(WriteInhibited)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_WriteInhibited);
				}
				if (!this.allowAllWrites) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [allowAllWrites == false] => UnitCheck(CommandReject)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject);
				}
				if (this.forbidFormatting) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: [forbidFormatting == true] => UnitCheck(CommandReject)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject);
				}
				opName = "GenericWrite/accessTrack";
				this.accessTrack(this.currTrack);
				
				opName = "Erase";
				this.currTrack.beginFormat();
				this.isModified = true;
				
				// as the unused track data from the formatting start position is erased
				// when beginFormat() is called, there is nothing more left to do...
				this.eventLogger.logLine(".. .. %s (dataLength = %d) => OK / INCORRECT_LENGTH_IS_OK", opName, dataLength);
				return this.exitOkLengthIsOK();
	
			case 0x39: // Search Home Address equal
				if (this.currTrack == null) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: currTrack == null => UnitCheck(CommandReject + Sense_SeekCheck)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject | Sense_SeekCheck);
				}
				opName = "Search Home Address equal";
				int addrLen = (dataLength > 4) ? 4 : dataLength;
				memSource.transfer(this.searchBuffer, 0, addrLen);
				for (int i = 0; i < addrLen; i++) {
					if (this.searchBuffer[i] != this.currTrack.getHomeAddressRaw(i+1)) {
						// no cyl/head match => signal this to avoid endless TIC loops
						this.eventLogger.logLine(
								".. .. %s (addr:: len: %d / %02X%02X%02X%02X) => UnitCheck(RecordNotFound)",
								opName, addrLen,
								this.searchBuffer[0], this.searchBuffer[1], this.searchBuffer[2], this.searchBuffer[3]);
						return this.exitUnitCheck(Sense_RecordNotFound);
					}
				}
				this.eventLogger.logLine(".. .. %s (addr :: len: %d / %02X%02X%02X%02X) => OK / STATUS_MODIFIER",
						opName, addrLen,
						this.searchBuffer[0], this.searchBuffer[1], this.searchBuffer[2], this.searchBuffer[3]);
				return this.exitOkSkipCCW();
	
			case 0x31: // Search Identifier equal
			case 0x51: // Search Identifier high
			case 0x71: // Search Identifier equal or high
				if (this.currTrack == null) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: currTrack == null => UnitCheck(CommandReject + Sense_SeekCheck)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject | Sense_SeekCheck);
				}
				opName = (opcode == 0x31) ? "Search Identifier equal" : (opcode == 0x51) ? "Search Identifier high" : "Search Identifier equal or high"; 
				memSource.transfer(this.searchBuffer, 0, (dataLength > 4) ? 5 : dataLength);
				int cmpLen = (dataLength > 4) ? 4 : dataLength;
				for (int i = 0; i < cmpLen; i++) {
					if (this.searchBuffer[i] != this.currTrack.getHomeAddressRaw(i+1)) {
						// no cyl/head match => signal this to avoid endless TIC loops
						this.eventLogger.logLine(".. .. %s (check home address) (addr :: len: %d / %02X%02X%02X%02X) => UnitCheck(RecordNotFound)",
								opName, cmpLen,
								this.searchBuffer[0], this.searchBuffer[1], this.searchBuffer[2], this.searchBuffer[3]);
						return this.exitUnitCheck(Sense_RecordNotFound);
					}
				}
				
				// "if search conditions for this short field are satisfied, the next command is skipped"
				if (dataLength < 5) { this.exitOkSkipCCW(); }
				
				// cyl & head are OK, check if the record is found
				boolean found = false;
				if (opcode == 0x31
						&& this.currTrack.searchIdEqual(this.searchBuffer[4] & 0xFF)) {
					// record found => skip next command in CCW chain 
					found = true;
				} else if (opcode == 0x51
							&& this.currTrack.searchIdHigh(this.searchBuffer[4] & 0xFF)) {
					// record found => skip next command in CCW chain 
					found = true;
				} else if (opcode == 0x71
							&& (this.currTrack.searchIdEqual(this.searchBuffer[4] & 0xFF)
									|| this.currTrack.searchIdHigh(this.searchBuffer[4] & 0xFF))) {
					// record found => skip next command in CCW chain 
					found = true;
				}
				if (found) {
					this.eventLogger.logLine(".. .. %s (addr :: len: %d / %02X%02X%02X%02X%02X) => OK / STATUS_MODIFIER",
							opName, 5,
							this.searchBuffer[0], this.searchBuffer[1], this.searchBuffer[2], this.searchBuffer[3], this.searchBuffer[4]);
					return this.exitOkSkipCCW();
				}
				
				// not found => signal this to avoid endless TIC loops
				this.eventLogger.logLine(".. .. %s (addr :: len: %d / %02X%02X%02X%02X%02X) => UnitCheck(RecordNotFound)",
						opName, 5,
						this.searchBuffer[0], this.searchBuffer[1], this.searchBuffer[2], this.searchBuffer[3], this.searchBuffer[4]);
				return this.exitUnitCheck(Sense_RecordNotFound);
	
			case 0x29: // Search Key equal 
			case 0x49: // Search Key high
			case 0x69: // Search Key equal or high 
				if (this.currTrack == null) {
					this.eventLogger.logLine(
							".. .. %s (dataLength = %d) :: currTrack == null => UnitCheck(CommandReject + Sense_SeekCheck)",
							opName, dataLength);
					return this.exitUnitCheck(Sense_CommandReject | Sense_SeekCheck);
				}
				opName = (opcode == 0x29) ? "Search Key equal" : (opcode == 0x49) ? "Search Key high" : "Search Key equal or high"; 
				int keyLen = (dataLength > this.searchBuffer.length) ? this.searchBuffer.length : dataLength;
				memSource.transfer(this.searchBuffer, 0, keyLen);
				if (this.currTrack.searchKey(
						this.searchBuffer, keyLen,
						(opcode == 0x29 || opcode == 0x69),
						(opcode == 0x49 || opcode == 0x69)))  {
					// record found => skip next command in CCW chain 
					this.eventLogger.logLine(".. .. %s (dataLength = %d) => OK / STATUS_MODIFIER", opName, dataLength);
					this.exitOkSkipCCW();
				}
				
				// not found => signal this to avoid endless TIC loops
				this.eventLogger.logLine(".. .. %s (dataLength = %d) => UnitCheck(RecordNotFound)", opName, dataLength);
				return this.exitUnitCheck(Sense_RecordNotFound);
				
			default:
				this.eventLogger.logLine(
						".. .. unknown WRITE opcode 0x%02X (dataLength = %d) => UnitCheck(CommandReject)",
						opcode, dataLength);
				return exitUnitCheck(Sense_CommandReject);
				
			}
		} catch (Exception exc) {
			this.eventLogger.logLine(
					".. .. %s (dataLength = %d) :: caught Exception[%s] => UnitCheck(EquipmentCheck)",
					opName, dataLength, exc.getMessage());
			return this.exitUnitCheck(Sense_EquipmentCheck);
		} finally {
			if (tmpBuffer != null) { this.freeTrackBuffer(tmpBuffer); }
		}
	}

	// perform a device sense I/O operation
	// see: iDevice
	@Override
	public int sense(int opcode, int dataLength, iDeviceIO memTarget) {
		switch(opcode)
		{
		case 0x04: // Sense I/O
			memTarget.transfer(this.senseBytes, 0, this.senseBytes.length);
			this.eventLogger.logLine(".. .. Sense I/O (dataLength = %d, max. %d) => OK / DEVICE_END", dataLength, this.senseBytes.length);
			return iDeviceStatus.DEVICE_END;

		case 0xA4: // Read Buffered Log
			this.eventLogger.logLine(
					".. .. Read Buffered Log ( !! unimplemented !! ) (dataLength = %d) => UnitCheck(CommandReject)",
					dataLength);
			return exitUnitCheck(Sense_CommandReject); // currently unsupported
			
		default:
			this.eventLogger.logLine(
					".. .. unknown SENSE opcode 0x%02X (dataLength = %d) => UnitCheck(CommandReject)",
					opcode, dataLength);
			return exitUnitCheck(Sense_CommandReject);
		}
	}
	
	// temporary data for transferring seek control operation from memory
	private byte[] controlBuffer = new byte[6];
	private int seekCyl;
	private int seekHead;
	
	// get the seek data from memory
	private int getSeekAddress(int dataLength, iDeviceIO memSource) {
		if (dataLength < 6) { return Sense_CommandReject; }
		int res = memSource.transfer(this.controlBuffer, 0, 6);
		if (res < 0) { return Sense_CommandReject; }
		this.seekCyl = ((this.controlBuffer[2] & 0xFF) << 8) | (this.controlBuffer[3] & 0xFF);
		this.seekHead = ((this.controlBuffer[4] & 0xFF) << 8) | (this.controlBuffer[5] & 0xFF);
		if (this.seekCyl >= this.cylinderCount && this.seekHead >= this.tracksPerCylinder) {
			return Sense_CommandReject | Sense_SeekCheck;
		}
		return 0;
	}

	// perform a device control  I/O operation
	// see: iDevice
	@Override
	public int control(int opcode, int dataLength, iDeviceIO memSource) {
		int senseFlags;
		switch(opcode)
		{
		case 0x03: // No-Op
			this.eventLogger.logLine(".. .. No-Op (dataLength = %d) => OK", dataLength);
			return this.exitOk();

		case 0x07: // Seek
			senseFlags = this.getSeekAddress(dataLength, memSource);
			if (senseFlags != 0) {
				this.eventLogger.logLine(".. .. Seek (cyl/head spec.) (dataLength = %d) => UnitCheck(CommandReject[+SeekCheck])", dataLength);
				return this.exitUnitCheck(senseFlags);
			}
			if (!this.allowSeek) {
				this.eventLogger.logLine(".. .. Seek (disallowed) (dataLength = %d) => UnitCheck(CommandReject)", dataLength);
				this.exitUnitCheck(Sense_CommandReject);
			}
			
			if (this.gotoTrack(this.seekCyl, this.seekHead)) {
				this.eventLogger.logLine(".. .. Seek (dataLength = %d, cyl = %d, head = %d) => OK / INCORRECT_LENGTH_IS_OK",
						dataLength, this.seekCyl, this.seekHead);
				return this.exitOkLengthIsOK();
			}
			this.eventLogger.logLine(".. .. Seek (seek failed) (dataLength = %d) => UnitCheck(CommandReject + SeekCheck)", dataLength);
			return this.exitUnitCheck(Sense_CommandReject | Sense_SeekCheck);

		case 0x0B: // Seek Cylinder 
			senseFlags = this.getSeekAddress(dataLength, memSource);
			if (senseFlags != 0) {
				this.eventLogger.logLine(".. .. Seek Cylinder (cyl/head spec.) (dataLength = %d) => UnitCheck(CommandReject[+SeekCheck])", dataLength);
				return this.exitUnitCheck(senseFlags);
			}
			if (!this.allowSeekCyl) {
				this.eventLogger.logLine(".. .. Seek Cylinder (disallowed) (dataLength = %d) => UnitCheck(FileProtected)", dataLength);
				this.exitUnitCheck(Sense_FileProtected);
			}
			
			if (this.gotoTrack(this.seekCyl, -1)) {
				this.eventLogger.logLine(".. .. Seek Cylinder (dataLength = %d) => OK / INCORRECT_LENGTH_IS_OK", dataLength);
				return this.exitOkLengthIsOK();
			}
			this.eventLogger.logLine(".. .. Seek Cylinder (seek failed) (dataLength = %d) => UnitCheck(CommandReject + SeekCheck)", dataLength);
			return this.exitUnitCheck(Sense_CommandReject | Sense_SeekCheck);

		case 0x1B: // Seek Head 
			senseFlags = this.getSeekAddress(dataLength, memSource);
			if (senseFlags != 0) {
				this.eventLogger.logLine(".. .. Seek Head (cyl/head spec.) (dataLength = %d) => UnitCheck(CommandReject[+SeekCheck])", dataLength);
				return this.exitUnitCheck(senseFlags);
			}
			if (!this.allowSeekHead) {
				this.eventLogger.logLine(".. .. Seek Head (disallowed) (dataLength = %d) => UnitCheck(FileProtected)", dataLength);
				this.exitUnitCheck(Sense_FileProtected);
				}
			
			if (this.gotoTrack(-1, this.seekHead)) {
				this.eventLogger.logLine(".. .. Seek Head (dataLength = %d) => OK / INCORRECT_LENGTH_IS_OK", dataLength);
				return this.exitOkLengthIsOK();
			}
			this.eventLogger.logLine(".. .. Seek Head (invalid head) (dataLength = %d) => UnitCheck(CommandReject + SeekCheck)", dataLength);
			return this.exitUnitCheck(Sense_CommandReject | Sense_SeekCheck);

		case 0x0F: // Space Count 
			this.eventLogger.logLine(".. .. Space Count (unsupported) (dataLength = %d) => UnitCheck(CommandReject)", dataLength);
			return this.exitUnitCheck(Sense_CommandReject); // currently unsupported

		case 0x13: // Recalibrate
			if (this.allowSeek && this.gotoTrack(0, 0)) {
				this.eventLogger.logLine(".. .. Recalibrate (dataLength = %d) => OK / INCORRECT_LENGTH_IS_OK", dataLength);
				this.exitOkLengthIsOK();
			}
			this.eventLogger.logLine(
					".. .. Recalibrate (dataLength = %d) :: seek disallowed or cyl 0 / head 0 not found => UnitCheck(CommandReject)",
					dataLength);
			return this.exitUnitCheck(Sense_CommandReject);

		case 0x17: // Restore
			this.eventLogger.logLine(".. .. Restore (dataLength = %d) => OK / INCORRECT_LENGTH_IS_OK", dataLength);
			return this.exitOkLengthIsOK();

		case 0x1F: // Set File Mask 
			if (memSource.transfer(this.controlBuffer, 0, 1) < 0) {
				this.eventLogger.logLine(
						".. .. Set File Mask (cannot read control byte) (dataLength = %d) => UnitCheck(CommandReject)",
						dataLength);
				return this.exitUnitCheck(Sense_CommandReject);
			}
			if ((this.controlBuffer[0] & 0x26) != 0) {
				this.eventLogger.logLine(
						".. .. Set File Mask (invalid control byte) (dataLength = %d) => UnitCheck(CommandReject)",
						dataLength);
				return this.exitUnitCheck(Sense_CommandReject);
			}
			
			int wrFlags = this.controlBuffer[0] & 0xC0;
			if (wrFlags == 0xC0) {
				// Bit 0,1 == 1,1
				this.allowAllWrites = true; 
				this.forbidWHAandWR0 = false;
				this.forbidFormatting = false;
			} else if (wrFlags == 0x40) {
				// Bit 0,1 == 0,1
				this.allowAllWrites = false; 
				this.forbidWHAandWR0 = true;
				this.forbidFormatting = true;
			} else if (wrFlags == 0x80) {
				// Bit 0,1 == 1,0
				this.allowAllWrites = true; 
				this.forbidWHAandWR0 = false;
				this.forbidFormatting = true;
			} else { // wrFlags == 0x00
				// Bit 0,1 == 0,0
				this.allowAllWrites = true; 
				this.forbidWHAandWR0 = true;
				this.forbidFormatting = true;
			}
			
			int seekFlags = this.controlBuffer[0] & 0x18;
			if (seekFlags == 0x18) {
				this.allowSeek = false;
				this.allowSeekCyl = false;
				this.allowSeekHead = false;
			} else if (seekFlags == 0x08) {	
				this.allowSeek = false;
				this.allowSeekCyl = true;
				this.allowSeekHead = true;
			} else if (seekFlags == 0x10) {	
				this.allowSeek = false;
				this.allowSeekCyl = false;
				this.allowSeekHead = true;
			} else { // seekFlags == 0
				this.allowSeek = true;
				this.allowSeekCyl = true;
				this.allowSeekHead = true;
			}

			this.eventLogger.logLine(".. .. Set File Mask (dataLength = %d) => OK / INCORRECT_LENGTH_IS_OK", dataLength);
			return this.exitOkLengthIsOK();

		case 0x23: // Set Sector
			if (memSource.transfer(this.controlBuffer, 0, 1) < 0) {
				this.eventLogger.logLine(
						".. .. Set Sector (cannot read control byte) (dataLength = %d) => UnitCheck(CommandReject)",
						dataLength);
				return this.exitUnitCheck(Sense_CommandReject);
			}
			this.eventLogger.logLine(".. .. Set Sector (dataLength = %d) => OK / INCORRECT_LENGTH_IS_OK", dataLength);
			return this.exitOkLengthIsOK();
			
		default:
			this.eventLogger.logLine(
					".. .. unknown CONTROL opcode 0x%02X (dataLength = %d) => UnitCheck(CommandReject)",
					opcode, dataLength);
			return exitUnitCheck(Sense_CommandReject);
		}

		// should we ever get here...
		//return exitUnitCheck(Sense_CommandReject);
	}
	
	// check for attention interrupt
	// see: iDevice
	@Override
	public boolean hasPendingAsyncInterrupt() { return false; } // CKD devices do not have asynchronous events
	
	// remove one pending attention interrupt
	// see: iDevice
	@Override
	public void consumeNextAsyncInterrupt() {} // no asynchronous events to consume
	
	// enqueue an attention interrupt
	// see: iDevice
	@Override
	public void doAttentionInterrupt() {} // no attention interrupts for CKDC drives 

	// get the virtual device information for DIAG-x24
	// see: iDevice
	@Override
	public int getVDevInfo() {
		int vdevInfo = 
			// VDEVTYPC = Virtual device type class
			((this.driveType.getCpDevClass() & 0xFF) << 24)
			// VDEVTYPE = Virtual device type
		  | ((this.driveType.getCpDevType() & 0xFF) << 16)
		    // VDEVSTAT = Virtual device status
		  | 0x00000000 // (none)
		    // VDEVFLAG = Virtual device flags
		  | ((this.writeProtected) ? 0x00000080 : 0x00000000) // VDEVRDO = DASD - READONLY
		  | ((!this.isFileBacked)  ? 0x00000040 : 0x00000000) // VDEVTDSK = DASD - T-DISK SPACE ALLOCATED BY CP
		;
		return vdevInfo;
	}

	// get the real device information for DIAG-x24
	// see: iDevice
	@Override
	public int getRDevInfo() {
		int rdevInfo = 
			// RDEVTYPC = Real device type class
			((this.driveType.getCpDevClass() & 0xFF) << 24)
			// RDEVTYPE = Real device type
		  | ((this.driveType.getCpDevType() & 0xFF) << 16)
		    // RDEVMDL = Real device model number
		  | ((this.driveType.getCode() & 0xFF) << 8)
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
				"DASD %03X %04X emx370 %s %6d CYL",
				asCuu,
				(this.driveType.getCode() >> 8) & 0xFFFF,
				(this.writeProtected) ? "R/O" : "R/W",
				this.getCylinderCount()
				);
	}
}
