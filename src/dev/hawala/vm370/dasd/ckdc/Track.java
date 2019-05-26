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

import dev.hawala.vm370.vm.device.iDeviceIO;

/**
 * Representation of a track for a CKD device.
 * 
 * <p>
 * This class implements the I/O functionality on a track
 * used by the {@link iDevice} implementation of the CDK
 * operations.  
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public class Track extends TrackBaseExternalIO {
	
	/*
	** Constructors: delegate to the real implementation of the superclass
	*/
	
	public Track(
				int maxRecordCount,
				int maxTrackSize,
				int recordCount,
				byte[] ddrHead,
				int headLength,
				byte[] ddrData,
				int dataLength) throws IOException {
		super(maxRecordCount, maxTrackSize, recordCount, ddrHead, headLength, ddrData, dataLength);
	}
	
	public Track(
				int maxRecordCount,
				int maxTrackSize,
				boolean isChangedTrack,
				InputStreamReader is, // Attention: 6 prefix bytes already consumed: cyl(2) / head(1) / trackContentLength(3)
				int trackContentLength) throws IOException {
		super(maxRecordCount, maxTrackSize, isChangedTrack, is, trackContentLength);
	}
	
	/*
	** Overrides for superclass methods 
	*/
	
	@Override
	public byte[] deAccess() throws IOException {
		this.formatting = false;
		return super.deAccess();
	}
	
	/*
	** read/write operations for the track as part of a S/370 CKD drive 
	*/
	
	private final static int COUNT_INDEX_ZERO = -42;
	private int currCountIndex = 0; // Index of the CountArea to be used for all record operations
	
	private boolean formatting = false;
	
	private long accessedCount = -1;
	
	public long getAccessedCount() { return this.accessedCount; }
	
	public void setAccessedCount(long count) { this.accessedCount = count; }
	
	public void moveToCKDIndex(int index) {
		if (index < 0) {
			this.currCountIndex = 0;
		} else if (index >= this.recordCount) {
			this.currCountIndex = this.recordCount - 1;
		} else {
			this.currCountIndex = index;
		}
	}
	
	/** Find the CountArea with the given ID (record number) and set it as current record
	 * for the next operations.
	 * 
	 * @param id the ID to look for
	 * @return true if the record was found resp. false if this record-id (record-number)
	 *   is not defined in this track.
	 */
	public boolean searchIdEqual(int id) {
		if (id == 0) {
			this.currCountIndex = COUNT_INDEX_ZERO;
			return true;
		}
		for (int i = 0; i < this.recordCount; i++) {
			if (this.countAreas[i].recordNo == id) {
				this.currCountIndex = i;
				return true;
			}
		}
		return false;
	}
	
	/** Find the CountArea with the record number higher than the given ID and set it as
	 *  current record for the next operations.
	 * 
	 * @param id the ID to look for
	 * @return true if a record was found resp. false if a record-id (record-number) higher than
	 *   'id' is not defined in this track.
	 */
	public boolean searchIdHigh(int id) {
		for (int i = 0; i < this.recordCount; i++) {
			if (this.countAreas[i].recordNo > id) {
				this.currCountIndex = i;
				return true;
			}
		}
		return false;
	}
	
	/** Find a record on the track with the key matching the passed search criteria.
	 * 
	 * @param key the buffer containing the key to search starting at offset 0. 
	 * @param len length of the key to search
	 * @param matchEqual does a record with an equal key match the search criteria?
	 * @param matchHigh does a record with a higher key match the search criteria?
	 * @return if a record was found resp. false if a record-key equal or higher than
	 *   'key' is not defined in this track.
	 */
	public boolean searchKey(byte[] key, int len, boolean matchEqual, boolean matchHigh) {
		if (len < 1) { return false; } // no key to compare with...? (there's no searchLow or searchEqualOrLow for CKD)
		if (!matchEqual && !matchHigh) { return false; } // what was it to be looked for?
		if (this.unpacked == null) { throw new IllegalStateException("Missing access()-call before track data i/o"); }
		
		for (int i = 0; i < this.recordCount; i++) {
			boolean isMatch = true;
			CountArea ca = this.countAreas[i];
			if (ca.keyLen < 1) { continue; } // this record has no key
			if (ca.keyLen != len && !matchHigh) { continue; } // must be equal, but only same length can be exact equal
			int last = ((len > ca.keyLen) ? ca.keyLen : len) - 1;
			for (int uk = ca.startPos, k = 0; k <= last; k++, uk++) {
				int valDisk = (this.unpacked[uk] & 0xFF);
				int valSearch = (key[k] & 0xFF);
				
				if (matchHigh && valDisk > valSearch) { break; } // matching so far and this key byte is high: done
				if (valDisk == valSearch) {
					if (k == last && !matchEqual && (ca.keyLen > len)) {
						// equal on common length but record key is longer => cannot match for pure matchHigh
						isMatch = false; // loop will end anyway because if condition "k == last" 
					}
					continue; // matching so far
				}
				
				// no match, so end comparing this key
				isMatch = false;
				break;
			}
			if (isMatch) { // so we found a key searched for...
				this.currCountIndex = i;
				return true;
			}
		}
		return false; // no matching key
	}
	
	/**
	 * Read the data area of the last searched record.
	 *  
	 * @param memTarget the memory target for data transfer
	 * @param dataLength the number of bytes requested for read
	 * @return the outcome of the memory transfer
	 */
	public int readData(iDeviceIO memTarget, int dataLength) {
		if (this.unpacked == null) { throw new IllegalStateException("Missing access()-call before track data i/o"); }
		if (this.currCountIndex == COUNT_INDEX_ZERO) { this.currCountIndex = 0; } // "read next record except record zero"
		if (this.currCountIndex < 0 || this.currCountIndex >= this.recordCount) { throw new IllegalStateException("No valid searchId() before track data i/o"); }
		
		CountArea ca = this.countAreas[this.currCountIndex++];
		int recordOffset = ca.startPos + ca.keyLen;
		int recordLen = ca.dataLen;
		
		return memTarget.transfer(this.unpacked, recordOffset, recordLen);
	}
	
	/**
	 * Read the key and data area of the last searched record.
	 *  
	 * @param memTarget the memory target for data transfer
	 * @param dataLength the number of bytes requested for read
	 * @return the outcome of the memory transfer
	 */
	public int readKeyAndData(iDeviceIO memTarget, int dataLength) {
		if (this.unpacked == null) { throw new IllegalStateException("Missing access()-call before track data i/o"); }
		if (this.currCountIndex == COUNT_INDEX_ZERO) { this.currCountIndex = 0; } // "read next record except record zero"
		if (this.currCountIndex < 0 || this.currCountIndex >= this.recordCount) { throw new IllegalStateException("No valid searchId() before track data i/o"); }
		
		CountArea ca = this.countAreas[this.currCountIndex++];
		int recordOffset = ca.startPos;
		int recordLen = ca.dataLen + ca.keyLen;
		
		return memTarget.transfer(this.unpacked, recordOffset, recordLen);
	}
	
	/**
	 * Read the complete content (count, key and data area) of the last searched record.
	 *  
	 * @param memTarget the memory target for data transfer
	 * @param dataLength the number of bytes requested for read
	 * @return the outcome of the memory transfer
	 */
	public int readCountKeyAndData(iDeviceIO memTarget, int dataLength, byte[] buffer) {
		if (this.unpacked == null) { throw new IllegalStateException("Missing access()-call before track data i/o"); }
		if (this.currCountIndex == COUNT_INDEX_ZERO) { this.currCountIndex = 0; } // "read next record except record zero"
		if (this.currCountIndex < 0 || this.currCountIndex >= this.recordCount) { throw new IllegalStateException("No valid searchId() before track data i/o"); }
		
		CountArea ca = this.countAreas[this.currCountIndex++];
		int recordOffset = ca.startPos;
		int recordLen = ca.dataLen + ca.keyLen;
		int totalLen = recordLen + 8; // 8 bytes for the Count area
		
		if (buffer == null || buffer.length < totalLen) {
			throw new IllegalStateException("Buffer is null or too small for count + key + data");
		}
		buffer[0] = (byte)((this.homeAddressCylNo >> 8) & 0xFF); 
		buffer[1] = (byte)(this.homeAddressCylNo & 0xFF);
		buffer[2] = (byte)((this.homeAddressHead >> 8) & 0xFF); 
		buffer[3] = (byte)(this.homeAddressHead & 0xFF);
		buffer[4] = (byte)(ca.recordNo & 0xFF);
		buffer[5] = (byte)(ca.keyLen & 0xFF);
		buffer[6] = (byte)((ca.dataLen >> 8) & 0xFF); 
		buffer[7] = (byte)(ca.dataLen & 0xFF);
		System.arraycopy(this.unpacked, recordOffset, buffer, 8, recordLen);
		
		return memTarget.transfer(buffer, 0, totalLen);
	}
	
	/**
	 * Read the content of record zero of the track.
	 *  
	 * @param memTarget the memory target for data transfer
	 * @param dataLength the number of bytes requested for read
	 * @return the outcome of the memory transfer
	 */
	public int readRecordZero(iDeviceIO memTarget, int dataLength) {
		return memTarget.transfer(this.recordZeroRaw, 0, this.recordZeroRaw.length);
	}
	
	/**
	 * Read the count area of the last searched record.
	 *  
	 * @param memTarget the memory target for data transfer
	 * @param dataLength the number of bytes requested for read
	 * @param buffer a temporary buffer with at least 8 bytes provided by the invoker
	 * @return the outcome of the memory transfer
	 */
	public int readCount(iDeviceIO memTarget, int dataLength, byte[] buffer) {
		if (this.unpacked == null) { throw new IllegalStateException("Missing access()-call before track data i/o"); }
		if (this.currCountIndex >= this.recordCount) { throw new IllegalStateException("No valid searchId() before track data i/o"); }
		if (this.currCountIndex == COUNT_INDEX_ZERO) { throw new IllegalStateException("Attempt to readCount() on record zero"); }
		
		CountArea ca = this.countAreas[this.currCountIndex++];
		int totalLen = 8; // 8 bytes for the Count area
		
		if (buffer == null || buffer.length < totalLen) {
			throw new IllegalStateException("Buffer is null or too small for count");
		}
		buffer[0] = (byte)((this.homeAddressCylNo >> 8) & 0xFF); 
		buffer[1] = (byte)(this.homeAddressCylNo & 0xFF);
		buffer[2] = (byte)((this.homeAddressHead >> 8) & 0xFF); 
		buffer[3] = (byte)(this.homeAddressHead & 0xFF);
		buffer[4] = (byte)(ca.recordNo & 0xFF);
		buffer[5] = (byte)(ca.keyLen & 0xFF);
		buffer[6] = (byte)((ca.dataLen >> 8) & 0xFF); 
		buffer[7] = (byte)(ca.dataLen & 0xFF);
		
		return memTarget.transfer(buffer, 0, totalLen);
	}
	
	/**
	 * Read the home address area of the track.
	 *  
	 * @param memTarget the memory target for data transfer
	 * @param dataLength the number of bytes requested for read
	 * @return the outcome of the memory transfer
	 */
	public int readHomeAddress(iDeviceIO memTarget, int dataLength) {
		return memTarget.transfer(this.homeAddressRaw, 0, this.homeAddressRaw.length);
	}
	
	
	/* never used and what was it intended for???
	public int readSector(iDeviceIO memTarget, int dataLength, byte[] buffer) {
		if (buffer == null || buffer.length < 1) {
			throw new IllegalStateException("Buffer is null or too small for sector");
		}
		buffer[0] = (byte)0;
		return memTarget.transfer(buffer, 0, 1);
	}
	*/
	
	// fill the remaining bytes of a record not written by a write operation
	private void fillRecordTail(int recordOffset, int recordLen, int bytesDiff) {
		if (bytesDiff >= 0) { return; }
		int limit = recordOffset + recordLen;
		int from = limit + bytesDiff;
		for (int i = from; i < limit; i++) { this.unpacked[i] = 0; }
	}
	
	/**
	 * Write to the data area of the last searched record.
	 * 
	 * @param memSource the memory source for data transfer
	 * @param dataLength the number of bytes provided for write
	 * @return the outcome of the memory transfer
	 */
	public int writeData(iDeviceIO memSource, int dataLength) {
		if (this.unpacked == null) { throw new IllegalStateException("Missing access()-call before track data i/o"); }
		if (this.currCountIndex >= this.recordCount) { throw new IllegalStateException("No valid searchId() before track data i/o"); }
		if (this.currCountIndex == COUNT_INDEX_ZERO) { throw new IllegalStateException("Attempt to writeData() on record zero"); }
		
		CountArea ca = this.countAreas[this.currCountIndex++];
		int recordOffset = ca.startPos + ca.keyLen;
		int recordLen = ca.dataLen;
		
		int bytesDiff = memSource.transfer(this.unpacked, recordOffset, recordLen);
		this.fillRecordTail(recordOffset, recordLen, bytesDiff);
		this.unpackedIsChanged = true;
		
		return bytesDiff;
	}
	
	/**
	 * Write to the key and data area of the last searched record.
	 * 
	 * @param memSource the memory source for data transfer
	 * @param dataLength the number of bytes provided for write
	 * @return the outcome of the memory transfer
	 */
	public int writeKeyAndData(iDeviceIO memSource, int dataLength) {
		if (this.unpacked == null) { throw new IllegalStateException("Missing access()-call before track data i/o"); }
		if (this.currCountIndex >= this.recordCount) { throw new IllegalStateException("No valid searchId() before track data i/o"); }
		if (this.currCountIndex == COUNT_INDEX_ZERO) { throw new IllegalStateException("Attempt to writeKeyAndData() on record zero"); }
		
		CountArea ca = this.countAreas[this.currCountIndex++];
		int recordOffset = ca.startPos;
		int recordLen = ca.dataLen + ca.keyLen;
		
		int bytesDiff = memSource.transfer(this.unpacked, recordOffset, recordLen);
		this.fillRecordTail(recordOffset, recordLen, bytesDiff);
		this.unpackedIsChanged = true;
		
		return bytesDiff;
	}
	
	// formatting write commands
	
	/**
	 * Prepare the track for subsequent formatting operations starting
	 * at the current (last searched) record
	 */
	public void beginFormat() {
		if (this.formatting) { return; }
		if (this.unpacked == null) { throw new IllegalStateException("Missing access()-call before track data i/o"); }
		
		// from where to clear the unpacked track data contents
		int cleanupStartAt = 0;
		
		// truncate the counts: formatting starts at current and  including(!) Count-Element
		if (this.currCountIndex < 0 || this.currCountIndex >= this.recordCount) {
			this.currCountIndex = 0;
		} else {
			cleanupStartAt = this.countAreas[this.currCountIndex].startPos;
		}
		this.recordCount = this.currCountIndex;
		
		// clear the unpacked track data contents
		for (int i = this.recordCount; i < this.countAreas.length; i++) {
			this.countAreas[i] = null;
		}
		for (int i = cleanupStartAt; i < this.unpacked.length; i++) {
			this.unpacked[i] = (byte)0x00;
		}
		
		// set state for formatting
		this.trackSize = cleanupStartAt;
		this.packedIschanged = true;
		this.formatting = true;
	}
	
	/**
	 * Append a new record to the track behind the current record
	 * @param recordNo the number value for the new record
	 * @param keyLen the key length for the new record
	 * @param dataLen the data length for the new record
	 * @param keyAndData the bytes to fill (keyLen + dataLen) bytes in the record with
	 * @param sourceStart offset of first byte in keyAndData to use
	 * @param sourceLen number of bytes to take from keyAndData
	 */
	public void appendCountKeyAndData(int recordNo, int keyLen, int dataLen, byte[] keyAndData, int sourceStart, int sourceLen) {
		// create the new Count area entry
		this.countAreas[this.recordCount++] = new CountArea(recordNo, keyLen, dataLen, this.trackSize);
		
		// copy possibly provided formatting content to the record's data
		int toPos = this.trackSize;
		int copyLen = keyLen + dataLen;
		if (copyLen > sourceLen) { copyLen = sourceLen; }
		for (int i = sourceStart; i < copyLen; i++) {
			this.unpacked[toPos++] = keyAndData[i];
		}
		
		// finalize track status
		this.trackSize += keyLen + dataLen;
	}
}
