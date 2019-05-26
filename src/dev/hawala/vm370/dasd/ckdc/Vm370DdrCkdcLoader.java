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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import dev.hawala.vm370.ebcdic.Ebcdic;
import dev.hawala.vm370.ebcdic.EbcdicHandler;

import static dev.hawala.vm370.ebcdic.Ebcdic.*;

/**
 * Utility program for creating several file types for the emx370
 * emulator, these are compressed CKD files and segment files from
 * DDR files written by a (real) VM/370 system as AWS files.
 * Additionally new empty compressed CKD files can be created.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public class Vm370DdrCkdcLoader {
	
	/*
	 * AWS-tape: content of the 6 header bytes for each tape block
	 */
	private int thisBlockSize;
	private int prevBlockSize;
	private byte blockFlags0; // 0x40 => TapeMark (CMS-TAPE)
	private byte blockFlags1;
	
	/* returns true if successful, else false (i.e. EOF reached on the binary tape file) */ 
	private boolean readAwsHeader(InputStream is) throws IOException {
		int b0;
		int b1;
		
		// Attention: this is little-endian!!!
		b0 = is.read(); if (b0 < 0) { return false; }
		b1 = is.read(); if (b1 < 0) { return false; }
		this.thisBlockSize = (b1 << 8) + b0;
		
		// Attention: this is little-endian!!!
		b0 = is.read(); if (b0 < 0) { return false; }
		b1 = is.read(); if (b1 < 0) { return false; }
		this.prevBlockSize = (b1 << 8) + b0;
		
		b0 = is.read(); if (b0 < 0) { return false; }
		this.blockFlags0 = (byte)b0;
		b1 = is.read(); if (b1 < 0) { return false; }
		this.blockFlags1 = (byte)b1;
		
		if (false) {
			String desc = "";
			if ((b0 & 0x40) != 0) { desc += " EOF"; }
			if ((b0 & 0x80) != 0) { desc += " SOR"; }
			if ((b0 & 0x20) != 0) { desc += " EOR"; }
			System.out.printf("    AWSHDR( this size = %d ; prev size = %d , flags = 0x%02X 0x%02X ~%s\n",
					this.thisBlockSize, this.prevBlockSize, this.blockFlags0, this.blockFlags1, desc);
		}
		
		return true;
	}
	
	/*
	 * DDR VHR header:
	 * typedef struct {
	 *   uchar  tag[4];           // "VHR "
	 *   uchar  dasdInputUnit[6]; // BBCCHH
	 *   uchar  unused1[6];
	 *   uchar  timeOfday[8];     // binary format, ignored
	 *   ushort vhrmrec;          // max. Records per Track
	 *   ushort vhrcyla;          // last allowed cyl.no. 
	 *   ushort vhrmtck;          // max. head = track no. (incl.)
	 *   uchar  volser[6];        // volume label
	 *   uchar  unused[44];
	 * } VHRHDR, *PVHRHDR;
	 */
	private int maxRecordsPerTrack; // VHRMREC
	private int lastCylNo;          // VHRCYLA
	private int lastTrackNo;        // VHRMTCK
	private byte[] volser = new byte[6];
	private EbcdicHandler volName = new EbcdicHandler(6);
	
	private boolean isVHRHeader(byte[] buf, int buflen) {
		if (buflen != 80) { return false; }
		if (buf[0] != _V || buf[1] != _H || buf[2] != _R || buf[3] != _Blank) {
			return false;
		}
		
		this.maxRecordsPerTrack = ((buf[24] & 0xFF) << 8) + (buf[25] & 0xFF);
		this.lastCylNo = ((buf[26] & 0xFF) << 8) + (buf[27] & 0xFF);
		this.lastTrackNo = ((buf[28] & 0xFF) << 8) + (buf[29] & 0xFF);
		  
		this.volser[0] = buf[30]; 
		this.volser[1] = buf[31];
		this.volser[2] = buf[32];
		this.volser[3] = buf[33];
		this.volser[4] = buf[34];
		this.volser[5] = buf[35];
		for (int i = 0; i < volser.length; i++) {
			this.volName.appendEbcdicChar(this.volser[i]);
		}
		  
		return true;
	}
	
	/*
	 * 
	 * typedef struct {
	 *   uchar  tag[4]; // "THR "
	 *   ushort recordCount; // number of Count-Areas
	 *   ushort fullBlockCount; // number of 4096 byte blocks of this THR
	 *   ushort lastBlockLen; // length in bytes of last block of this THR
	 *   uchar  flag;
	 *   uchar  homeAddress[5]; /* Home Address Reordered = 5 Bytes Flag/CylNo/HeadNo of Track Home Address
	 *   uchar  recordZero[16]; /* 8 Bytes CountArea: CylNo/HeadNo/RecordNo/KeyLen/DataLen ; 0 Bytes KeyArea ; 8 Bytes DataArea
	 *   CountArea counts[128];
	 * } THRHDR, *PTHRHDR;
	 */
	private int recordCount;
	private int fullBlockCount;
	private int lastBlockLen;
	@SuppressWarnings("unused")
	private int thrFlag;
	
	private byte[] ddrHeader = new byte[4096]; // starts at homeAddress[0] up to the last used count-area incl.
	private int ddrHeaderLength;
	
	private byte[] ddrData = new byte[65636]; // starts after the last count-area
	private int ddrDataLength;
	
	private int remainingBlocksForTrack; // number of blocks still to read to fill ddrData

	int maxCylNoUsed = -1;
	CkdDriveType driveType = CkdDriveType.unknown;
	
	private boolean isTHRHeader(byte[] buf, int buflen) {
		if (buflen != 4096) { return false; }
		if (buf[0] != _T || buf[1] != _H || buf[2] != _R || buf[3] != _Blank) {
			return false;
		}
		
		// extract relevant header data
		this.recordCount = ((buf[4] & 0xFF) << 8) + (buf[5] & 0xFF);
		this.fullBlockCount = ((buf[6] & 0xFF) << 8) + (buf[7] & 0xFF);
		this.lastBlockLen = ((buf[8] & 0xFF) << 8) + (buf[9] & 0xFF);
		this.thrFlag = buf[10];
		
		// save ddr header data for the Track()-instantiation
		this.ddrHeaderLength
				= 5 // homeAddress
				+ 16 // record zero
				+ (this.recordCount * 8); // for each count.area: cylNo(2), headNo(2), recordNo(1), keyLen(1), dataLen(2)
		System.arraycopy(buf, 11, this.ddrHeader, 0, this.ddrHeaderLength);
		
		// copy the rest of this block as first track data bytes
		// WARNING: this assumes that recordCount is less than 508 !!
		this.ddrDataLength = buflen - 11 - this.ddrHeaderLength;
		System.arraycopy(buf, 11 + this.ddrHeaderLength, this.ddrData, 0, this.ddrDataLength);
		
		// set the number of blocks still to read
		// total = #size=4096-blocks + 1-partial-block
		// but: this one already had 4096 bytes, so it counts in #size=4096-blocks
		this.remainingBlocksForTrack = this.fullBlockCount; // -1 for this 4096-block + 1 for the last partial block
		if (this.lastBlockLen == 0) {
			// there will be no partial block at the end
			this.remainingBlocksForTrack--;
		}
		
		return true;
	}
	
	/* returns true if all data for this track have been read and next block should be a new THR or VHR header */
	private boolean appendThrBlock(byte[] buf, int buflen) {
		System.arraycopy(buf, 0, this.ddrData, this.ddrDataLength, buflen);
		this.ddrDataLength += buflen;
		this.remainingBlocksForTrack--;
		return (this.remainingBlocksForTrack < 1);
	}
	
	// load the track data in a DDR tape into the internal representation (i.e. as Track-array)
	public Track[] readDdrTape(InputStream is) throws Exception {
		byte[] buf = new byte[65536];
		int buflen;
		
		int block = 0;
		
		boolean doVhr = true;
		boolean doThr = false;
		boolean doData = false;
		
		Track[] tracks = null;
		int trackCount = 0;
		
		this.maxCylNoUsed = -1;
		this.driveType = CkdDriveType.unknown;
		
		try {
			while(this.readAwsHeader(is)) {
				// check for processing end
				if ((this.blockFlags0 & 0x40) != 0) {
					// Tape-Mark => end of track data on an DDR tape
					/*
					System.out.println("-- TAPE-MARK -- done processing DDR tape");
					*/
					break;
				}
				if (this.thisBlockSize > buf.length) {
					// this tape block cannot be processed
					System.out.printf("** Error: tape block too large (%d, max. %d), aborting!",
							this.thisBlockSize, buf.length);
					break;
				}
				
				// load the tape block and interpret
				block++;
				if (this.thisBlockSize > 0) {
					buflen = is.read(buf, 0, this.thisBlockSize);
					if (buflen != this.thisBlockSize) {
						System.out.printf("** Error: block %d read error (buflen %d instead of %d), aborting!",
								block, buflen, this.thisBlockSize);
						break;
					}
				} else {
					buflen = 0;
				}
				
				// VHR?
				if (doVhr) {
					if (this.isVHRHeader(buf, buflen)) {
						/*
						System.out.printf("## VolSer = '%s', max. records per track = %d, last cyl. no. = %d, last track no. = %d\n",
								this.volName.toString(), this.maxRecordsPerTrack, this.lastCylNo, this.lastTrackNo);
						*/
						tracks = new Track[(this.lastCylNo + 1) * (this.lastTrackNo + 1)];
						doVhr = false;
						doThr = true;
						
						// guess the drive type
						int headCount = this.lastTrackNo + 1;
						for (CkdDriveType t : CkdDriveType.values()) {
							if (headCount == t.getHeads() && this.lastCylNo < t.getMaxCyls()) {
								driveType = t;
								break;
							}
						}
					}
				} else if (doThr) {
					if (this.isTHRHeader(buf, buflen)) {
						doThr = false;
						doData = (this.remainingBlocksForTrack > 0);
					}
				} else if (doData) {
					doData = !this.appendThrBlock(buf, buflen);
				}
				
				if (!doVhr && ! doThr && !doData) {
					// a track was completed read
					int maxTrackSize = (driveType == CkdDriveType.unknown)
							? this.ddrDataLength + (this.recordCount * 16) // just a guess for unknown drives
									: driveType.getMaxTrackLen();
					Track track = new Track(
							this.maxRecordsPerTrack,
							maxTrackSize,
							this.recordCount,
							this.ddrHeader,
							this.ddrHeaderLength,
							this.ddrData,
							this.ddrDataLength
							);
					tracks[trackCount++] = track;
					
					int cylNo = track.getCylNo();
					if (cylNo > this.maxCylNoUsed) { this.maxCylNoUsed = cylNo; }
					
					/*
					System.out.printf("Cyl. %d - Head %d -> %d records, data length = %d\n",
							cylNo, track.getHeadNo(), this.recordCount, this.ddrDataLength);
					*/
					
					// next DDR tape block is a new track or the end of the dump...
					doThr = true;
				}
			}
		} catch(IOException exc) {
			System.out.printf("** Error reading AWS tape file, message: %s\n", exc.getMessage());
			exc.printStackTrace();
			return null;
		}
		
		return tracks;
	}
	
	// create a drive (in runtime representation) from a DDR tape content
	public CkdcDrive loadDdrTapeToCkdcDrive(InputStream is) throws Exception {
		
		// load the tracks
		Track[] tracks = this.readDdrTape(is);
		if (this.driveType == CkdDriveType.unknown) {
			System.out.printf("** Warning: unknown VM/370R6 compatible drive type with %d heads and %d used cylinders\n",
					this.lastTrackNo + 1, this.lastCylNo + 1);
		} else {
			System.out.printf("** Info: recognized a %s CKD drive type\n", driveType.getName());
		}
		
		// Create the drive
		CkdcDrive drive = new CkdcDrive(this.volser, this.maxCylNoUsed+1, this.lastTrackNo + 1, this.driveType, this.maxRecordsPerTrack, tracks);
		return drive;
	}
	
	// create a compressed CKD file (in our own format) from a minidisk DDR dump
	private boolean /* success? */ createDriveFile(String inFilename, String outFilename) {
		String what = "initial";
		try {
			what = "open input file";
			FileInputStream fis = new FileInputStream(inFilename);
			
			what = "read DDR tape";
			CkdcDrive d = this.loadDdrTapeToCkdcDrive(fis);
			
			what = "close input file";
			fis.close();
			
			what = "write the drive base file to disk";
			d.saveTo(null, outFilename);
		
			// success
			return true;
		} catch(Exception exc) {
			System.out.printf("** Error while doing '%s', message: %s\n", what, exc.getMessage());
			exc.printStackTrace();
		}
		
		// failure
		return false;
	}
	
	// read a saved segment with the given block count from a DDR tape and write the segment in our own format 
	public boolean /* success? */ extractSavedSegment(InputStream is, OutputStream os, int cpPageCount) throws Exception {

		// load the tracks from DDR tape
		Track[] tracks = this.readDdrTape(is);
		
		byte[] trackBuffer = new byte[65536];
		byte[] cpBlock = new byte[4096];
		
		int remainingBlocks = cpPageCount + 1; // +1 : first page with PSW, GPRs, FPRs, CRs and Storage-Keys for the IPL of the segment 
		Track deAccessTrack = null;
		
		for (Track t : tracks) {
			if (t ==  null) { continue; }
			try {
				t.access(trackBuffer);
				deAccessTrack = t;
				int recCount = t.getRecordCount();
				for (int r = 0; r < recCount && remainingBlocks > 0; r++) {
					int keyLen = t.getRecordKeyLen(r);
					int dataLen = t.getRecordDataLen(r);
					if (keyLen == 0 && dataLen == 4096) {
						t.getRecordDataRaw(r, cpBlock);
						os.write(cpBlock);
						remainingBlocks--;
					}
				}
				t.deAccess();
				deAccessTrack = null;
			} catch (Exception exc) {
				try { if (deAccessTrack != null) { deAccessTrack.deAccess(); } } catch (Exception e) { }
				System.out.printf("** Error while extracting saved segment from DDR-tape: %s\n", exc.getMessage());
				exc.printStackTrace();
				return false;
			}
		}
		return true;
	}
	
	// read a DDR tape file containing a saved segment with the given page count and create the file in out own format
	private boolean createSegmentFile(String inFilename, String outFilename, int cpPageCount) {
		String what = "initial";
		try {
			what = "open input file";
			FileInputStream fis = new FileInputStream(inFilename);
			
			what = "open output file";
			FileOutputStream fos = new FileOutputStream(outFilename);
			
			what = "write the segment file to disk";
			this.extractSavedSegment(fis, fos, cpPageCount);
			
			what = "close input file";
			fis.close();
			
			what = "close output file";
			fos.close();
		
			// success
			return true;
		} catch(Exception exc) {
			System.out.printf("** Error while doing '%s', message: %s\n", what, exc.getMessage());
			exc.printStackTrace();
		}
		
		// failure
		return false;
	}
	
	// create a new CKD drive in memory with the given cylinder count and volume label
	public CkdcDrive createNewDrive(CkdDriveType type, int cylCount, String volLabel) {
		
		// check drive type
		if (type == CkdDriveType.unknown) { return null; }
		if (cylCount > type.getMaxCyls()) { cylCount = type.getMaxCyls(); }
		
		// prepare necessary data structures
		int maxTrackSize = type.getMaxTrackLen();
		int maxRecordCount = type.getMaxRecordsPerTrack(); 
		int trackCount = type.getHeads();
		byte[] recordBuf = new byte[maxTrackSize];
		Arrays.fill(recordBuf, (byte)0x00);
		
		int ddrHeadLength = 21; // HomeAddress (5 bytes) + RecordZero (16 bytes)
		byte[] ddrHead = new byte[ddrHeadLength];
		Arrays.fill(ddrHead, (byte)0x00);
		ddrHead[12] = (byte)0x08; // data-length in Record-Zero
		
		Track[] tracks = new Track[cylCount * trackCount];
		int currTrack = 0;
		
		try {
			// create the tracks
			for (int cylNo = 0; cylNo < cylCount; cylNo++) {
				
				// encode the cylinder number into the Home-Address
				ddrHead[1] = (byte)((cylNo >> 8) & 0xFF);
				ddrHead[2] = (byte)(cylNo & 0xFF);
				
				// encode the cylinder number into the Record-Zero
				ddrHead[5] = (byte)((cylNo >> 8) & 0xFF);
				ddrHead[6] = (byte)(cylNo & 0xFF);
				
				for (int trackNo = 0; trackNo < trackCount; trackNo++) {
					// encode the head number into the Home-Address
					ddrHead[3] = (byte)((trackNo >> 8) & 0xFF);
					ddrHead[4] = (byte)(trackNo & 0xFF);
					
					// encode the head number into the Record-Zero for the track
					ddrHead[7] = (byte)((trackNo >> 8) & 0xFF);
					ddrHead[8] = (byte)(trackNo & 0xFF);
					
					// create the empty track
					Track track = new Track(
							maxRecordCount,
							maxTrackSize,
							0,
							ddrHead,
							ddrHead.length,
							recordBuf,
							maxTrackSize
							);
					tracks[currTrack++] = track;
				}
			}
			
			// create the drive
			if (volLabel == null || volLabel.length() == 0) { volLabel = "CMSDSK"; }
			byte[] label = Ebcdic.toEbcdic(volLabel.concat("      ").substring(0,6).toUpperCase());
			CkdcDrive drive = new CkdcDrive(label, cylCount, trackCount, type, type.getMaxRecordsPerTrack(), tracks);
			
			// done
			return drive;
		
		} catch (Exception exc) {
			System.out.printf("** Error while creating new %s CKDC-drive: %s\n", type.getName(), exc.getMessage());
			exc.printStackTrace();
			return null;
		}
	}
	
	// create a new compressed CKD base file with the given cylinder count and volume label
	private boolean createNewDriveFile(String outFilename, CkdDriveType type, int cylCount, String volLabel) {
		String what = "initial";
		try {
			what = "create new drive";
			CkdcDrive d = createNewDrive(type, cylCount, volLabel);
			if (d == null) { return false; }
			
			what = "write the drive base file to disk";
			d.saveTo(null, outFilename);
		
			// success
			return true;
		} catch(Exception exc) {
			System.out.printf("** Error while doing '%s', message: %s\n", what, exc.getMessage());
			exc.printStackTrace();
		}
		
		// failure
		return false;
	}
	
	// print usage info and terminate program
	static void usage() {
		String progname = Vm370DdrCkdcLoader.class.getCanonicalName(); 
		System.out.printf("Usage: %s <parameters> \n", progname);
		System.out.printf(" with parameters:\n");
		System.out.printf("    -import <infilename = DDR tape file (AWS)> <outfilename = .ckdc file>\n");
		System.out.printf("    -savedsegment <infilename = DDR tape file (AWS)> <outfilename = .segment file> [CP-pagecount]\n");
		System.out.printf("    -create <dev-type> <cyl-count> <volser> <outfilename = .ckdc file>\n");
		System.out.printf("      with <dev-type: 3330[-2], 3340[-2], 3350, 3375, 3380\n");
		
		System.exit(1);
	}
	
	private static int parseInt(String parm) {
		int val = -1;
		try {
			val = Integer.parseInt(parm);
		} catch(NumberFormatException e) {
			usage();
		}
		return val;
	}

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// check for subcommand
		if (args.length < 1) { usage(); }

		// the loader instance
		Vm370DdrCkdcLoader loader = new Vm370DdrCkdcLoader();
		
		String subcommand = args[0].toLowerCase();
		if (subcommand.equals("-import")) {
			// read DDR tape file and create a CKDC base file for it
			if (args.length < 3) { usage(); }
			String ddrFn = args[1];
			String ckdcFn = args[2];
			if (!ckdcFn.toLowerCase().endsWith(".ckdc")) { ckdcFn += ".ckdc"; }
			if (loader.createDriveFile(ddrFn, ckdcFn)) {
				System.out.printf("Created CKDC file '%s'\n", ckdcFn);
			}
		} else if (subcommand.equals("-savedsegment")) {
			// read DDR tape file with a saved segment and create the .segment file for it
			if (args.length < 3) { usage(); }
			String ddrFn = args[1];
			String segmentFn = args[2];
			int cpPageCount = 0x7FFFFFFE;
			if (!segmentFn.toLowerCase().endsWith(".segment")) { segmentFn += ".segment"; }
			if (args.length > 3) { cpPageCount = parseInt(args[3]); }
			if (loader.createSegmentFile(ddrFn,  segmentFn,  cpPageCount)) {
				System.out.printf("Created segment file '%s'\n", segmentFn);
			}
		} else if (subcommand.equals("-create")) {
			// create an empty (and unformatted) CKDC file
			if (args.length < 5) { usage(); }
			CkdDriveType type = CkdDriveType.mapName(args[1]);
			if (type == CkdDriveType.unknown) { usage(); }
			int cylCount = parseInt(args[2]);
			String volLabel = args[3].toUpperCase();
			String ckdcFn = args[4];
			if (!ckdcFn.toLowerCase().endsWith(".ckdc")) { ckdcFn += ".ckdc"; }
			if (loader.createNewDriveFile(ckdcFn, type, cylCount, volLabel)) {
				System.out.printf("Created empty (and unformatted) CKDC file '%s'\n", ckdcFn);
			}
		} else {
			usage();
		}
		
	}

}
