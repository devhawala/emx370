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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Implementation for the tape format provider for AWS tapes.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public class TapeIoAws implements iTapeIo {
	
	private final String filename;
	private final boolean canBewritten;
	
	private TapeIoAws(String filename, boolean canBewritten) {
		this.filename = filename;
		this.canBewritten = canBewritten;
	}
	
	public static boolean isAwsFilename(String fn) {
		return fn != null && fn.toLowerCase().endsWith(".aws");
	}
	
	public static iTapeIo get(String filename, boolean createIfNew, boolean wantsWritable, StringBuilder msgSink) {
		File f = new File(filename);
		if (!f.exists()) {
			if (createIfNew) {
				try {
					if (!f.createNewFile()) {
						msgSink
							.append("Unable to create new tape file: ")
							.append(filename);
						return null;
					}
				} catch (IOException e) {
					msgSink
					  .append("Error creating new tape file: ")
					  .append(filename);
					return null;
				}
			} else {
				msgSink
					.append("Tape file not found: ")
					.append(filename);
				return null;
			}
		}
		if (!f.canRead()) {
			msgSink
				.append("Unable to access tape file: ")
				.append(filename);
		return null;
		}
		boolean canBewritten = wantsWritable;
		if (wantsWritable && !f.canWrite()) {
			msgSink
				.append("Tape forced read/only, file not writable: ")
				.append(filename);
			canBewritten = false;
		}
		return new TapeIoAws(filename, canBewritten); 
	}
	
	private static class TapeFileEnd extends Exception {
		private static final long serialVersionUID = 1734444222273676276L;
	};
	
	public static byte readByte(InputStream is) throws TapeFileEnd, IOException {
		int b = is.read();
		if (b < 0) {
			throw new TapeFileEnd();
		}
		return (byte)(b & 0xFF);
	}
	
	public static int readInt(InputStream is) throws TapeFileEnd, IOException {
		int b0;
		int b1;
		
		// Attention: this is little-endian!!!
		b0 = readByte(is) & 0xFF;
		b1 = readByte(is) & 0xFF;
		return (b1 << 8) + b0;
	}
	
	public static void writeInt(OutputStream os, int value) throws IOException {
		// Attention: this is little-endian!!!
		os.write(value & 0xFF);
		os.write((value >> 8) & 0xFF);
	}
	
	/* (non-Javadoc)
	 * @see dev.hawala.vm370.tape.iTapeIo#readTapeFile(dev.hawala.vm370.tape.TapeBlock, dev.hawala.vm370.tape.TapeBlock)
	 */
	public int readTapeFile(TapeBlock headLimit, TapeBlock tailLimit) {
		int blockCount = -1;
		
		TapeBlock curr = headLimit;
		FileInputStream fis = null;
			
		try {
			fis = new FileInputStream(this.filename);
			
			while(fis.available() > 0) { // until a TapeFileEnd comes...
				int blockSize = readInt(fis);
				@SuppressWarnings("unused")
				int prevBlockSize = readInt(fis);
				byte flags0 = readByte(fis);
				@SuppressWarnings("unused")
				byte flags1 = readByte(fis);
				//System.out.printf("TapeIoAws : read block (blockSize = %d ; prevBlocksize = %d ; flags0 = 0x%02X ; flags1 = 0x%02X\n", blockSize, prevBlockSize, flags0, flags1); 
				if (blockSize > 0) { // can be max. 65536 (2 bytes little endian int)
					byte[] data = new byte[blockSize];
					int bytesRead = fis.read(data);
					if (bytesRead != blockSize) {
						//System.out.printf("######## TapeIoAws : blocksize = %d , but bytes read: %d\n", blockSize, bytesRead);
					}
					curr = new TapeBlock(curr, data);
				} else if ((flags0 & 0x40) != 0) {
					curr = new TapeBlock(curr);
				} else {
					curr = new TapeBlock(curr, new byte[0]);
				}
			}
		} catch (TapeFileEnd e) {
			// ignored as normal end signal...
		} catch (FileNotFoundException e) {
			// TODO: log this
		} catch (IOException e) {
			// TODO: log this
		} finally {
			if (fis != null) { try { fis.close(); } catch(Exception e) { } }
		}
		
		curr.append(tailLimit);
		return blockCount;
	}
	
	/* (non-Javadoc)
	 * @see dev.hawala.vm370.tape.iTapeIo#writeTapeFile(dev.hawala.vm370.tape.TapeBlock, dev.hawala.vm370.tape.TapeBlock)
	 */
	public void writeTapeFile(TapeBlock headLimit, TapeBlock tailLimit) {
		if (!this.canBewritten) {
			return;
		}
		
		FileOutputStream fos = null;
		try {
			fos = new FileOutputStream(this.filename);
			
			int prevLength = 0;
			TapeBlock curr = headLimit.getNext();
			while(curr != null && curr != tailLimit) {
				boolean isTapemark = curr.isTapemark();
				byte[] currData = curr.getBlockData();
				int currLength = (isTapemark || currData == null) ? 0 : currData.length;
				writeInt(fos, currLength);
				writeInt(fos, prevLength);
				if (isTapemark) {
					fos.write((byte)0x40); // tapemark
					fos.write((byte)0);
					//System.out.printf("TapeIoAws : write tapemark (blockSize = %d ; flags0 = 0x%02X\n", currLength, 0x40); 
				} else {
					fos.write((byte)0xA0); // same Hercules for CMS tapes...
					fos.write((byte)0);
					if (currData != null) { fos.write(currData); }
					//System.out.printf("TapeIoAws : write block (blockSize = %d ; flags0 = 0x%02X\n", currLength, 0);					
				}
				
				prevLength = currLength;
				curr = curr.getNext();
			}
			
			fos.close();
		} catch (FileNotFoundException e) {
			// TODO: log this
		} catch (IOException e) {
			// TODO: log this
		} finally {
			if (fos != null) { try { fos.close(); } catch(Exception e) { } }
		}
	}
	
	public boolean isWritable() {
		return this.canBewritten;
	}
	
	public String getTapeFilename() {
		return this.filename;
	}
}
