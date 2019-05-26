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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.security.InvalidParameterException;
import java.util.Arrays;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import dev.hawala.vm370.ebcdic.Ebcdic;

/**
 * Implementation of loading and saving the content of a single
 * track of a CKD device.
 * 
 * <p>
 * This class implements the Count / Key / Data structure of a
 * CKD track. Loading of the structure can occur from either from
 * track data loaded from a DDR tape or from a {@InputStream} on a
 * file saved by this class.
 * </p>
 * 
 * <p>
 * A track is held compressed in memory and must explicitly be
 * prepared for access (see {@link TrackBaseExternalIO.access})
 * and released (see {@link TrackBaseExternalIO.deAccess}).
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public abstract class TrackBaseExternalIO {
	
	/*
	** Content structures and construction of a CKD track (initial loading form DDR tape, save/restore from CKDC file)
	*/
	
	/**
	 * Representation of a single CKD count area of a track. 
	 */
	protected static class CountArea {
		
		/** record number of this CKD */
		public final int recordNo;
		
		/** offset of the first byte of this CKD's key+data block in the buffer */
		public final int startPos;
		
		/** length of the key part of the CKD */
		public final int keyLen;
		
		/** length of the data part of the CKD */
		public final int dataLen;
		
		public CountArea(int recordNo, int keyLen, int dataLen, int startPos) {
			this.recordNo = recordNo;
			this.keyLen = keyLen;
			this.dataLen = dataLen;
			this.startPos = startPos;
		}
		
		public CountArea(InputStreamReader is) throws IOException {
			is.load(8);
			this.recordNo = is.readInt2();
			this.keyLen = is.readInt1();
			this.dataLen = is.readInt2();
			this.startPos = is.readInt3();
		}
		
		public void dumpTo(OutputStream os) throws IOException {
			os.write(this.recordNo >> 8);
			os.write(this.recordNo);
			os.write(this.keyLen);
			os.write(this.dataLen >> 8);
			os.write(this.dataLen);
			os.write(this.startPos >> 16);
			os.write(this.startPos >> 8);
			os.write(this.startPos);
		}
	}
	
	protected final int maxRecordCount; // excluding recordZero
	protected final int maxTrackSize; // key- and data-bytes, excluding count-infos 
	
	protected int recordCount;    // excluding recordZero
	
	protected byte[] homeAddressRaw = new byte[5]; // ddr-length: 5
	// (not used) private byte homeAddressFlag = 0;
	protected int homeAddressCylNo = 0;
	protected int homeAddressHead = 0;
	
	protected byte[] recordZeroRaw = new byte[16]; // ddr-length: 16
	
	protected CountArea[] countAreas; // length: maxRecordCount
	
	protected int trackSize = 0; // effectively used space in unpacked track
	
	protected byte[] packedOriginal = null; // original packed data loaded from backing store
	
	protected byte[] packedChanged = null; // != null if unpacked (accessed) data was changed before de-accessing
	
	protected byte[] unpacked = null; // length: trackSize

	protected boolean packedIschanged = false;   // must the packed state be written to the backing store?
	protected boolean unpackedIsChanged = false; // must the unpacked be packed when de-accessing the track?
	
	private TrackBaseExternalIO(int maxRecordCount, int maxTrackSize) {
		// setup final fields
		this.maxRecordCount = maxRecordCount;
		this.maxTrackSize = maxTrackSize;
		
		// initialize variable size work fields
		this.countAreas = new CountArea[this.maxRecordCount];
	}
	
	// load a track content from a DDR track content
	protected TrackBaseExternalIO(
			int maxRecordCount,
			int maxTrackSize,
			int recordCount,
			byte[] ddrHead,
			int headLength,
			byte[] ddrData,
			int dataLength) throws IOException {
		// construct instance 
		this(maxRecordCount, maxTrackSize);
		
		/*
		 *  construct track from DDR input
		 */
		
		// if necessary: limit the number of records in the track
		this.recordCount = (recordCount > maxRecordCount) ? maxRecordCount : recordCount;
		
		// first 5 bytes: home address
		this.homeAddressRaw[0] = ddrHead[0];
		this.homeAddressRaw[1] = ddrHead[1];
		this.homeAddressRaw[2] = ddrHead[2];
		this.homeAddressRaw[3] = ddrHead[3];
		this.homeAddressRaw[4] = ddrHead[4];
		// (not used) this.homeAddressFlag = ddrHead[0];
		this.homeAddressCylNo = ((ddrHead[1] & 0xFF) << 8) | (ddrHead[2] & 0xFF);
		this.homeAddressHead = ((ddrHead[3] & 0xFF) << 8) | (ddrHead[4] & 0xFF);
		
		// next 16 Bytes: record zero
		System.arraycopy(ddrHead, 5, this.recordZeroRaw, 0, 16);
		
		// then come the Count headers for the {Key,Data} records
		int rawPos = 21; // offset of the current Count-info in 'ddrHead'
		int dataStartPos = 0; // offset of the {key,data}-record in 'unpacked' for the current Count-info 
		for (int i = 0; i < this.recordCount; i++) {
			int recordNo = (ddrHead[rawPos+4] & 0xFF);
			int keyLen = (ddrHead[rawPos+5] & 0xFF);
			int dataLen  = ((ddrHead[rawPos+6] & 0xFF) << 8) | (ddrHead[rawPos+7] & 0xFF);
			this.countAreas[i] = new CountArea(recordNo, keyLen, dataLen, dataStartPos);
			rawPos += 8;
			dataStartPos += keyLen + dataLen;
		}
		
		// take over the record contents as compressed data
		// ('dataStartPos' as data-offset of the 'n+1'-th record is in fact the data-length of the 'n' records)  
		this.trackSize = (maxTrackSize > dataStartPos) ? maxTrackSize : dataStartPos;
		this.unpacked = ddrData;
		this.pack();
		this.unpacked = null;
	}
	
	// load a track from a stream on a file in native format
	protected TrackBaseExternalIO(
			int maxRecordCount,
			int maxTrackSize,
			boolean isChangedTrack,
			InputStreamReader is, // Attention: 6 prefix bytes already consumed: cyl(2) / head(1) / trackContentLength(3)
			int trackContentLength) throws IOException { 
		// construct instance 
		this(maxRecordCount, maxTrackSize);
		this.packedIschanged = isChangedTrack;
		
		// first 5 bytes: home address
		if (is.read(this.homeAddressRaw) != 5) {
			throw new IOException("Failed to read raw home address");
		}
		trackContentLength -= 5;
		// (not used) this.homeAddressFlag = this.homeAddressRaw[0];
		this.homeAddressCylNo = ((this.homeAddressRaw[1] & 0xFF) << 8) | (this.homeAddressRaw[2] & 0xFF);
		this.homeAddressHead = ((this.homeAddressRaw[3] & 0xFF) << 8) | (this.homeAddressRaw[4] & 0xFF);
		
		// next 16 Bytes: record zero
		if (is.read(this.recordZeroRaw) != 16) {
			throw new IOException("Failed to read raw record zero");
		}
		trackContentLength -= 16;
		
		// prepare reading the rest of the header
		is.load(5);
		
		// get the unpacked track size
		trackContentLength -= 3;
		this.trackSize = is.readInt3();
		
		// get the record count
		trackContentLength -= 2;
		this.recordCount = is.readInt2();
		
		// read theCount headers for the {Key,Data} records
		for (int i = 0; i < this.recordCount; i++) {
			CountArea ca = new CountArea(is);
			trackContentLength -= 8;
			if (i < this.maxRecordCount) {
				this.countAreas[i] = ca;
			}
		}
		if (this.recordCount > this.maxRecordCount) {this.recordCount = this.maxRecordCount; }
		
		// load the packed record contents
		if (trackContentLength < 1) {
			throw new IOException("Failed to read packed track contents (no data left)");
		}
		this.packedOriginal = new byte[trackContentLength];
		if (is.read(this.packedOriginal) != trackContentLength) {
			throw new IOException("Failed to read packed track content");
		}
	}
	
	// create the packed copy of the unpacked track content
	private void pack() throws IOException {
		if (this.unpacked == null) { return; }
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		DeflaterOutputStream dos = new DeflaterOutputStream(baos);
		dos.write(this.unpacked, 0, this.trackSize);
		dos.finish();
		this.packedChanged = baos.toByteArray();
		dos.close();
		baos.close();
	}
	
	/**
	 * Check if the track was changed and must therefore be saved.
	 * @return
	 */
	public boolean needsSaving() {
		return this.packedChanged != null || this.packedIschanged;
	}
	
	/**
	 * Write the track to the stream in native format.
	 * 
	 * @param os the stream to write to.
	 * @throws IOException
	 */
	public void dumpTo(OutputStream os) throws IOException {
		if (this.unpacked != null) { this.pack(); } // ensure that an accessed track has a current packed content
		
		byte[] packed = (this.packedChanged != null) ? this.packedChanged : this.packedOriginal;
		if (packed == null) { return; } // what happened?
		
		// write home address raw bytes
		os.write(this.homeAddressRaw);
		
		// write record zero raw bytes
		os.write(this.recordZeroRaw);
		
		// write unpacked track size
		os.write(this.trackSize >> 16);
		os.write(this.trackSize >> 8);
		os.write(this.trackSize);
		
		// write record count
		os.write(this.recordCount >> 8);
		os.write(this.recordCount);
		
		// write count areas
		for (int i = 0; i < this.recordCount; i++) {
			this.countAreas[i].dumpTo(os);
		}
		
		// packed track content
		os.write(packed);
	}
	
	/**
	 * Prepare the track for accesses by unpacking the packed track content
	 * to the provided buffer.
	 * 
	 * @param buffer the buffer where to unpack the track content.
	 * @throws IOException
	 */
	public void access(byte[] buffer) throws IOException {
		if (this.unpacked != null) { return; } // already accessed => nothing to do
		
		// we will have a new unpacked, so it is currently unchanged
		this.unpackedIsChanged = false;
		
		// if no packed data is available: simulate a blank track
		if (this.packedOriginal == null && this.packedChanged == null) {
			Arrays.fill(buffer, (byte)0x00);
			this.unpacked = buffer;
			return;
		}
		
		// where to unpack from?
		byte[] src = (this.packedChanged != null) ? this.packedChanged : this.packedOriginal;
		
		// unpack
		ByteArrayInputStream bais = new ByteArrayInputStream(src);
		InflaterInputStream iis = new InflaterInputStream(bais);
		int unpackCount = 0;
		int cnt = iis.read(buffer);
		while (cnt >= 0 && buffer.length > unpackCount) {
			unpackCount += cnt;
			cnt = iis.read(buffer, unpackCount, buffer.length - unpackCount);
		}
		if (unpackCount < buffer.length) { Arrays.fill(buffer, unpackCount, buffer.length, (byte)0x00); }
		iis.close();
		bais.close();
		this.unpacked = buffer;
	}
	
	/**
	 * Unprepare the track for access by packing the content if required (if the
	 * track was changed) and returning the unpack-buffer which is no longer used
	 * by the track.
	 * 
	 * @return the byte-array that was used are buffer for the unpacked track content.
	 * @throws IOException
	 */
	public byte[] deAccess() throws IOException {
		// save the return value
		byte[] zeBuffer = this.unpacked;
		
		// pack and save packed bytes
		if (this.unpackedIsChanged) { this.pack(); }
		
		// clear unpacked track data
		this.unpacked = null;
		this.unpackedIsChanged = false;
		
		// done
		return zeBuffer;
	}
	
	/*
	 * accessors for properties
	 */
	
	public byte getHomeAddressRaw(int idx) {
		if (idx < 0 || idx >= this.homeAddressRaw.length) { return (byte)0; }
		return this.homeAddressRaw[idx];
	}
	
	public int getCylNo() { return this.homeAddressCylNo; }
	
	public int getHeadNo() { return this.homeAddressHead; }
	
	public int getMaxTrackSize() { return this.maxTrackSize; }
	
	public int getExternalTrackDataLength() {
		byte[] packed = (this.packedChanged != null) ? this.packedChanged : this.packedOriginal;
		if (packed == null) { return 0; } // what happened?
		int trackDataLength
			= 5  // home address raw bytes
			+ 16 // record zero raw bytes
			+ 3  // unpacked track size
			+ 2  // record count
			+ (this.recordCount * 8) // count areas
			+ packed.length; // packed track content
		return trackDataLength;
	}
	
	public int getRecordCount() { return this.recordCount; }
	
	public int getRecordKeyLen(int recNo) {
		if (recNo >= this.recordCount) { throw new InvalidParameterException("recNo out of range"); }
		return this.countAreas[recNo].keyLen;
	}
	
	public int getRecordDataLen(int recNo) {
		if (recNo >= this.recordCount) { throw new InvalidParameterException("recNo out of range"); }
		return this.countAreas[recNo].dataLen;
	}
	
	public int getRecordDataRaw(int recNo, byte[] dest) {
		if (recNo >= this.recordCount) { throw new InvalidParameterException("recNo out of range"); }
		if (this.unpacked == null) { throw new IllegalStateException("access() not invoked"); }
		int recLen = this.countAreas[recNo].keyLen + this.countAreas[recNo].dataLen;
		int len = (dest.length > recLen) ? recLen : dest.length;
		System.arraycopy(this.unpacked, this.countAreas[recNo].startPos, dest, 0, len);
		return recLen;
	}
	
	/*
	 * test utilities
	 */
	
	// dump track metadata to System.out.
	public void dump() {
		
		// dump home address
		System.out.printf("   Home Address : 0x %02X %02X %02X %02X %02X\n",
				this.homeAddressRaw[0], this.homeAddressRaw[1], 
				this.homeAddressRaw[2], this.homeAddressRaw[3], 
				this.homeAddressRaw[4]);
		
		// dump record zero
		System.out.printf("   Record Zero  : 0x %02X %02X %02X %02X %02X %02X %02X %02X - %02X %02X %02X %02X %02X %02X %02X %02X\n",
				this.recordZeroRaw[0], this.recordZeroRaw[1],
				this.recordZeroRaw[2], this.recordZeroRaw[3],
				this.recordZeroRaw[4], this.recordZeroRaw[5],
				this.recordZeroRaw[6], this.recordZeroRaw[7],
				this.recordZeroRaw[8], this.recordZeroRaw[9],
				this.recordZeroRaw[10], this.recordZeroRaw[11],
				this.recordZeroRaw[12], this.recordZeroRaw[13],
				this.recordZeroRaw[14], this.recordZeroRaw[15]
				);
		
		// dump all tracks
		for (int caIdx = 0; caIdx < this.recordCount; caIdx++) {
			CountArea ca = this.countAreas[caIdx];
			System.out.printf("---- record: cyl %d , head %d , rec %d , keyLen %d , dataLen %d\n",
					this.homeAddressCylNo, this.homeAddressHead, ca.recordNo, ca.keyLen, ca.dataLen);

			byte[] ebc = new byte[16];
			
			int count = ca.keyLen + ca.dataLen;
			int pos = ca.startPos;
			
			int blockOffset = 0;
			while (count > 0) {
				System.out.printf("0x..%04X:   ", blockOffset);
				for (int j = 0; j < 16; j++) {
					System.out.printf((j < count) ? " %02X" : "  %c", (j < count) ? (this.unpacked[pos + j] & 0xFF) : ' ');
					ebc[j] = (j < count) ? this.unpacked[pos + j] : (byte)0x40;
				}
				System.out.printf("[%s]\n", Ebcdic.toAscii(ebc, 0, 16));
				pos += 16;
				count -=16;
				blockOffset += 16;
			}
			System.out.println();
		}
	}

}
