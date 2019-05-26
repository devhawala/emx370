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
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Implementation of loading and saving the content of a CKD device
 * into memory.
 * 
 * <p>
 * This class (together with {@link Track} implements the loading of
 * a drive descriptor from a base file and of the tracks either from
 * a delta file (if the track was modified in a previous session) or
 * from the base file (if the track was unchanged so far).  
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public abstract class DriveBaseExternalIO {
	
	/*
	** Content structures and construction of a CKD drive (initial loading form DDR tape, save/restore from CKDC file)
	*/
                                               // external file format (big-endian / EBCDIC)
	protected final int fileversion = 1;       // 2 bytes (unsigned)
	protected final byte[] volser;             // 6 bytes
	protected final int cylinderCount;         // 2 bytes (unsigned)
	protected final int tracksPerCylinder;     // 2 bytes (unsigned)
	protected final int maxTrackSize;          // 3 bytes (unsigned)
	protected final int maxRecordsPerTrack;    // 1 byte (unsigned)
	protected final CkdDriveType driveType;    // 4 bytes
	                                           // 12 filler bytes (for possible later use)
	                                           // => total 32 bytes header in version 1
	
	/*
	 * Tracks management
	 */
	protected final Track[] tracks;
	
	protected int getCylAndHeadIndex(int cylNo, int headNo) {
		return (cylNo * this.tracksPerCylinder) + headNo;
	}
	
	public int getCylinderCount() { return this.cylinderCount; }
	
	public int getHeadCount() { return this.tracksPerCylinder; }
	
	public Track getTrack(int cylNo, int headNo) {
		return this.tracks[this.getCylAndHeadIndex(cylNo, headNo)];
	}
	
	/*
	 * construction
	 */
	
	// constructor for loading a CKD drive from a DDR tape
	protected DriveBaseExternalIO(
			byte[] ddrVolser,
			int ddrCylinderCount,
			int ddrTracksPerCylinder,
			CkdDriveType driveType,
			int ddrMaxRecordsPerTrack,
			Track[] ddrTracks) throws Exception {
		
		// copy the volume label
		if (ddrVolser == null || ddrVolser.length != 6) {
			throw new Exception("Invalid VOLSER from DDR tape");
		}
		this.volser = new byte[6];
		System.arraycopy(ddrVolser, 0, this.volser, 0, this.volser.length);
		
		// copy the drive type
		this.driveType = driveType;
		
		// copy geometry data
		if (ddrCylinderCount < 1) {
			throw new Exception("Invalid cylinder count from DDR tape");
		}
		this.cylinderCount = ddrCylinderCount;
		if (ddrTracksPerCylinder < 1) {
			throw new Exception("Invalid tracks count per cylinder from DDR tape");
		}
		this.tracksPerCylinder = ddrTracksPerCylinder;
		this.maxRecordsPerTrack = ddrMaxRecordsPerTrack;
		
		// get the tracks into our own structure
		this.tracks = new Track[ddrCylinderCount * ddrTracksPerCylinder];
		int tracksMaxUsedSize = -1;
		for (Track t : ddrTracks) {
			int cylNo = t.getCylNo();
			int trackNo = t.getHeadNo();
			if (cylNo >= ddrCylinderCount) {
				throw new Exception("Track outside valid cylinder range from DDR tape");
			}
			if (trackNo >= ddrTracksPerCylinder) {
				throw new Exception("Track outside valid track range from DDR tape");
			}
			int idx = (cylNo * ddrTracksPerCylinder) + trackNo;
			this.tracks[idx] = t;
			int tms = t.getMaxTrackSize();
			if (tms > tracksMaxUsedSize) { tracksMaxUsedSize = tms; }
		}
		this.maxTrackSize = (tracksMaxUsedSize > driveType.getMaxTrackLen()) ? tracksMaxUsedSize : driveType.getMaxTrackLen();	
		
		// check we have all tracks at the right position
		int currCylNo = 0;
		int currTrackNo = 0;
		for (Track t : this.tracks) {
			if (t == null) {
				throw new Exception("Missing track from DDR tape");
			}
			if (t.getCylNo() != currCylNo) {
				throw new Exception("Misplaced track (cylinder) from DDR tape");
			}
			if (t.getHeadNo() != currTrackNo) {
				throw new Exception("Misplaced track (head) from DDR tape");
			}
			currTrackNo++;
			if (currTrackNo >= this.tracksPerCylinder) {
				currTrackNo = 0;
				currCylNo++;
			}
		}
		if (currCylNo != this.cylinderCount || currTrackNo != 0) {
			throw new Exception("Geometry problem at verification end (currCylNo != this.cylinderCount || currTrackNo != 0)");
		}
	}
	
	// load a CKD drive from disk in our own native format, given the filenames for delta and base file
	protected DriveBaseExternalIO(String deltaFile, String baseFile) throws Exception {
		this(
			(deltaFile == null) ? null : new FileInputStream(deltaFile),
			(baseFile == null) ? null : new FileInputStream(baseFile)
		);
	}
	
	// load a CKD drive from disk in our own native format, given the streams to delta and base file
	protected DriveBaseExternalIO(InputStream deltaStream, InputStream baseStream) throws Exception {
		int nextDeltaCylNo = 0x7FFFFFFF;
		int nextDeltaHeadNo = 0x7FFFFFFF;
		int nextDeltaDataLength = 0;
		
		if (baseStream == null) {
			throw new Exception("Base file must be specified!");
		}
		InputStreamReader baseFis = new InputStreamReader(baseStream);
		InputStreamReader deltaFis = (deltaStream != null) ? new InputStreamReader(deltaStream) : null;
		
		try {
		
			/*
			 * open and read the base file
			 */
			
			byte[] baseVolser = new byte[6];
			int baseCylCount = -1;
			int baseTracksPerCyl = -1;
			int baseMaxTrackSize = -1;
			int baseMaxRecordsPerTrack = -1;
			int baseDriveTypeCode = -1;
			CkdDriveType baseDriveType;
			
			// load header from base file
			baseFis.load(32);
			int baseVersion = baseFis.readInt2();
			if ((baseVersion & 0x8000) != 0) {
				throw new Exception("Passed a delta file as base file");
			}
			for (int i = 0; i < 6; i++) { baseVolser[i] = (byte)baseFis.readInt1(); }
			baseCylCount = baseFis.readInt2();
			baseTracksPerCyl = baseFis.readInt2();
			baseMaxTrackSize = baseFis.readInt3();
			baseMaxRecordsPerTrack = baseFis.readInt1();
			baseDriveTypeCode = baseFis.readInt4();
			baseDriveType = CkdDriveType.mapCode(baseDriveTypeCode);
			baseFis.readInt4();
			baseFis.readInt4();
			baseFis.readInt4();
			
			/*
			System.out.printf("## Drive base data: type = %s, cylCount = %d, tracksPerCyl = %d, maxTrackSize = %d, maxRecordPerTrack = %d\n",
					baseDriveType.getName(), baseCylCount, baseTracksPerCyl, baseMaxTrackSize, baseMaxRecordsPerTrack);
			*/
			
			/*
			 * if there is a delta file: open and check that it means the same drive and get the first delta track info 
			 */
			if (deltaFis != null) {
				
				// load header form delta file
				deltaFis.load(32);
				int deltaVersion = deltaFis.readInt2();
				if ((deltaVersion & 0x8000) == 0) {
					throw new Exception("Passed a base file as delta file");
				}
				for (int i = 0; i < 6; i++) { 
					if (baseVolser[i] != (byte)deltaFis.readInt1()) {
						throw new Exception("VOLSER of base and delta file differ");
					}
				}
				if (baseCylCount != deltaFis.readInt2()) {
					throw new Exception("Geometry of base and delta file differ (CylCount)");
				}
				if (baseTracksPerCyl != deltaFis.readInt2()) {
					throw new Exception("Geometry of base and delta file differ (RecordsPerCyl)");
				}
				if (baseMaxTrackSize != deltaFis.readInt3()) {
					throw new Exception("Geometry of base and delta file differ (MaxTrackSize)");
				}
				if (baseMaxRecordsPerTrack != deltaFis.readInt1()) {
					throw new Exception("Geometry of base and delta file differ (MaxrecordsPerTrack)");
				}
				if (baseDriveTypeCode != deltaFis.readInt4()) {
					throw new Exception("DriveType of base and delta file differ");
				}
				deltaFis.readInt4();
				deltaFis.readInt4();
				deltaFis.readInt4();
				
				// load first track identification from delta file 
				deltaFis.load(6);
				nextDeltaCylNo = deltaFis.readInt2();
				nextDeltaHeadNo = deltaFis.readInt1();
				nextDeltaDataLength = deltaFis.readInt3();
			}
			
			/*
			 * read the tracks, preferring delta tracks (if present) over base tracks
			 */
			Track[] tracks = new Track[baseCylCount * baseTracksPerCyl];
			int currTrack = 0;
			
			// read prefix for first base track
			baseFis.load(6);
			int baseCylNo = baseFis.readInt2();
			int baseHeadNo = baseFis.readInt1();
			int baseDataLength = baseFis.readInt3();
			
			// loop over all tracks in base file
			while(baseCylNo != 0x0000FFFF && baseHeadNo != 0x000000FF) {
				Track t = null;
				if (nextDeltaCylNo == baseCylNo && nextDeltaHeadNo == baseHeadNo) {
					// use the delta track
					t = new Track(
							baseMaxRecordsPerTrack,
							baseMaxTrackSize,
							true, // isChangedTrack
							deltaFis,
							nextDeltaDataLength
							);
					
					// read prefix for the next delta track
					deltaFis.load(6);
					nextDeltaCylNo = deltaFis.readInt2();
					nextDeltaHeadNo = deltaFis.readInt1();
					nextDeltaDataLength = deltaFis.readInt3();
					
					// skip base track
					baseFis.skip(baseDataLength);
				} else {
					// use the base track
					t = new Track(
							baseMaxRecordsPerTrack,
							baseMaxTrackSize,
							false, // isChangedTrack
							baseFis,
							baseDataLength
							);
				}
				
				// put the track in our backing array
				tracks[currTrack++] = t;
				
				// read prefix for next base track
				baseFis.load(6);
				baseCylNo = baseFis.readInt2();
				baseHeadNo = baseFis.readInt1();
				baseDataLength = baseFis.readInt3();
			}
			
			// here we have read all the base file and merged in the delta tracks
			// so set the final fields
			this.volser = baseVolser;
			this.cylinderCount = baseCylCount;
			this.tracksPerCylinder = baseTracksPerCyl;
			this.maxTrackSize = baseMaxTrackSize;
			this.maxRecordsPerTrack = baseMaxRecordsPerTrack;
			this.driveType = baseDriveType;
			this.tracks = tracks;

		} finally {
			if (baseFis != null) { baseFis.close(); }
			if (deltaFis != null) { deltaFis.close(); }
		}
		
		// check we have all tracks at the right position
		int currCylNo = 0;
		int currTrackNo = 0;
		for (Track t : this.tracks) {
			if (t == null) {
				throw new Exception("Missing track after loading drive data");
			}
			if (t.getCylNo() != currCylNo) {
				throw new Exception("Misplaced track (cylinder) after loading drive data");
			}
			if (t.getHeadNo() != currTrackNo) {
				throw new Exception("Misplaced track (head) from after loading drive data");
			}
			currTrackNo++;
			if (currTrackNo >= this.tracksPerCylinder) {
				currTrackNo = 0;
				currCylNo++;
			}
		}
		if (currCylNo != this.cylinderCount || currTrackNo != 0) {
			throw new Exception("Geometry problem at verification end after loading drive data (currCylNo != this.cylinderCount || currTrackNo != 0)");
		}
	}
	
	/*
	 * saving the CKD drive to disk
	 */
	
	private void writeInt1(OutputStream os, int val) throws Exception {
		os.write(val);
	}
	
	private void writeInt2(OutputStream os, int val) throws Exception {
		os.write(val >> 8);
		os.write(val);
	}
	
	private void writeInt3(OutputStream os, int val) throws Exception {
		os.write(val >> 16);
		os.write(val >> 8);
		os.write(val);
	}
	
	private void writeInt4(OutputStream os, int val) throws Exception {
		os.write(val >> 24);
		os.write(val >> 16);
		os.write(val >> 8);
		os.write(val);
	}
	
	/**
	 * Save this CKD drive to disk, either as delta if 'deltaFile' is specified,
	 * or as complete content if 'basefile' is given.
	 * 
	 * @param deltaFile the name of the delta file to which the tracks changed (since the
	 *   last time the drive was saved to a base file) are to be written.
	 * @param baseFile the name of a base file to which the complete drive content is
	 *   to be written.
	 * @throws Exception if some problem occured while saving the drive.
	 */
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

		FileOutputStream fos = new FileOutputStream(filename);
		this.save(fos, (filename == deltaFile));
	}
	
	/**
	 * Save this CKD drive to disk using the output stream given, either as
	 * delta or complete content, dependeing on 'isDeltaTarget'.
	 * 
	 * @param os the stream to which the CKD drive is to be saved
	 * @param isDeltaTarget save the complete drive content ({@code false})
	 *   or only the changed tracks ({@code true})..
	 * @throws Exception if some problem occured while saving the drive.
	 */
	public void save(OutputStream os, boolean isDeltaTarget) throws Exception {
		boolean doMerge = !isDeltaTarget;
		
		/*
		 * put the header
		 */
	
		// the version (most-significant bit set => this is a delta file)
		int version = (isDeltaTarget) ? 0x8000 : 0x0000;
		version += this.fileversion;
		this.writeInt2(os, version);
		
		// volume label
		os.write(volser[0]);
		os.write(volser[1]);
		os.write(volser[2]);
		os.write(volser[3]);
		os.write(volser[4]);
		os.write(volser[5]);
		
		// number of cylinder
		this.writeInt2(os, this.cylinderCount);
		
		// number of heads per cylinder
		this.writeInt2(os, this.tracksPerCylinder);
		
		// max. byte count for data on a single track
		this.writeInt3(os, this.maxTrackSize);
		
		// max. records per track
		this.writeInt1(os, this.maxRecordsPerTrack);
		
		// the drive type code
		int driveTypeCode = this.driveType.getCode();
		this.writeInt4(os, driveTypeCode);

		// 12 dummy bytes
		this.writeInt4(os, 0);
		this.writeInt4(os, 0);
		this.writeInt4(os, 0);
		
		/*
		 * the tracks
		 */
		
		for (int i = 0; i < this.tracks.length; i++) {
			Track t = this.tracks[i];
			if (doMerge || t.needsSaving()) {
				// identifiying header for merge on read
				this.writeInt2(os, t.getCylNo());
				this.writeInt1(os, t.getHeadNo());
				this.writeInt3(os, t.getExternalTrackDataLength());
				// track data
				t.dumpTo(os);
			}
		}
		
		/*
		 * mark the end (invalid track identification)
		 */
		this.writeInt2(os, 0x0000FFFF);
		this.writeInt1(os, 0x000000FF);
		this.writeInt3(os, 0);
		
		/*
		 * done
		 */
		os.close();
	}
	
	/**
	 * Dump a track System.out.
	 * @param cylNo the cylinder number of the track to dump.
	 * @param headNo the head numner of the track to dump.
	 * @throws Exception if something goes wrong.
	 */
	public void dumpCylAndHead(int cylNo, int headNo) throws Exception {
		if (cylNo >= this.cylinderCount) { return; }
		if (headNo >= this.tracksPerCylinder) { return; }
		
		int idx = this.getCylAndHeadIndex(cylNo, headNo);
		Track t = this.tracks[idx];
		if (t.getCylNo() != cylNo) {
			throw new Exception("Access mismatch for track (cylNo)");
		}
		if (t.getHeadNo() != headNo) {
			throw new Exception("Access mismatch for track (headNo)");
		}

		byte[] trackDataBuffer = new byte[this.maxTrackSize];
		t.access(trackDataBuffer);
		System.out.printf("\n--\n-- Dump of cylNo %d -- headNo %d\n", cylNo, headNo);
		t.dump();
		t.deAccess();
	}

}
