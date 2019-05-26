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

/**
 * Wrapper around an {@link InputStream} providing additional
 * functions for reading from the stream as well as buffered
 * reading.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public class InputStreamReader {

	private final InputStream is;
	
	private byte[] b = new byte[32];
	private int currBuffered = 0;
	private int currPos = 0;

	/**
	 * Constructor.
	 * 
	 * @param stream the stream to be wrapped.
	 */
	public InputStreamReader(InputStream stream) {
		this.is = stream;
	}
	
	/**
	 * Buffer the given amount of bytes.
	 * 
	 * @param count the number of bytes to be buffered.
	 * @throws IOException
	 */
	public void load(int count) throws IOException {
		if (count > this.b.length) { this.b = new byte[count]; }
		if (this.is.read(this.b, 0, count) < count) {
			throw new IOException("Failed to read " + count + " bytes from input stream");
		}
		this.currBuffered = count;
		this.currPos = 0;
	}
	
	/**
	 * Read 1 byte from the buffer an return it as unsigned integer.
	 * 
	 * @return the unsigned value of the byte consumed from the buffer.
	 * @throws IOException
	 */
	public int readInt1() throws IOException {
		if ((this.currBuffered - this.currPos) < 1) {
			throw new IOException("Failed to read 1-byte int");
		}
		return this.b[this.currPos++] & 0xFF;
	}
	
	/**
	 * Read 2 bytes from the buffer an return it as unsigned integer.
	 * 
	 * @return the unsigned value of the 2 bytes consumed from the buffer.
	 * @throws IOException
	 */
	public int readInt2() throws IOException {
		if ((this.currBuffered - this.currPos) < 2) {
			throw new IOException("Failed to read 2-byte int");
		}
		return ((this.b[this.currPos++] & 0xFF) << 8) | (this.b[this.currPos++] & 0xFF);
	}
	
	/**
	 * Read 3 bytes from the buffer an return it as unsigned integer.
	 * 
	 * @return the unsigned value of the 3 bytes consumed from the buffer.
	 * @throws IOException
	 */
	public int readInt3() throws IOException {
		if ((this.currBuffered - this.currPos) < 3) {
			throw new IOException("Failed to read 3-byte int");
		}
		return ((this.b[this.currPos++] & 0xFF) << 16) | ((this.b[this.currPos++] & 0xFF) << 8) | (this.b[this.currPos++] & 0xFF);
	}
	
	/**
	 * Read 4 bytes from the buffer an return it as signed integer.
	 * 
	 * @return the signed value of the 4 bytes consumed from the buffer.
	 * @throws IOException
	 */
	public int readInt4() throws IOException {
		if ((this.currBuffered - this.currPos) < 4) {
			throw new IOException("Failed to read 4-byte int");
		}
		return ((this.b[this.currPos++] & 0xFF) << 24) | ((this.b[this.currPos++] & 0xFF) << 16) | ((this.b[this.currPos++] & 0xFF) << 8) | (this.b[this.currPos++] & 0xFF);
	}
	
	/**
	 * Clear the buffer and fill the passed byte-array from the wrapped stream.
	 * 
	 * @param to the byte-array to be filled
	 * @return the number of bytes read.
	 * @throws IOException
	 */
	public int read(byte[] to) throws IOException {
		this.currBuffered = 0;
		return this.is.read(to);
	}
	
	/**
	 * Clear the buffer and fill the specified range of the passed byte-array
	 * from the wrapped stream.
	 * 
	 * @param to the byte-array to be filled
	 * @param start the begin of the byte-range to be filled
	 * @param count the number of bytes to read
	 * @return the number of bytes read.
	 * @throws IOException
	 */
	public int read(byte[] to, int start, int count) throws IOException {
		this.currBuffered = 0;
		return this.is.read(to, start, count);
	}
	
	/**
	 * Clear the buffer and skip the specified byte count in the wrapped stream.
	 * 
	 * @param count the number of bytes to skip.
	 * @return the number of bytes effectively skipped.
	 * @throws IOException
	 */
	public long skip(long count) throws IOException {
		this.currBuffered = 0;
		return this.is.skip(count);
	}
	
	/**
	 * Close the wrapped stream and clear the buffer.
	 * 
	 * @throws IOException
	 */
	public void close() throws IOException {
		this.currBuffered = 0;
		this.is.close();
	}
	
}
