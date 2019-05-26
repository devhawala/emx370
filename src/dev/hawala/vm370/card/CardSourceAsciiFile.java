/*
** This file is part of the emx370 emulator.
**
** This software is provided "as is" in the hope that it will be useful,
** with no promise, commitment or even warranty (explicit or implicit)
** to be suited or usable for any particular purpose.
** Using this software is at your own risk!
**
** Written by Dr. Hans-Walter Latz, Berlin (Germany), 2016
** Released to the public domain.
*/

package dev.hawala.vm370.card;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import dev.hawala.vm370.ebcdic.Ebcdic;

/**
 * Card source for ascii files, reading line by line as cards, ignoring control
 * characters except for line ends.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 */
public class CardSourceAsciiFile implements iCardSource {

	private final char spoolClass;
	private final int nominalCardLen;
	private final boolean breakLongLines;
	private final File source;
	
	private InputStream fis = null;
	private int lastByte = -1;
	
	private static final int CR = 0x0D;
	private static final int LF = 0x0A;
	
	private class TerminatedInputStream extends InputStream {
		@Override public int read() throws IOException { return -1; }
	};
	
	/**
	 * Construct the ascii file card source.
	 * 
	 * @param filename the name of the file for the cards.
	 * @param nominalCardLen the card length to present when a line-end is
	 *   encountered, filling up with blanks if necessary.
	 * @param breakLongLines if no line-end is encountered before {@nominalCardLen}
	 *   is reached: is the rest of the input line to be ignored ({@code false})
	 *   or is the rest of the input line to be presented as a new card ({@code true})? 
	 * @throws FileNotFoundException if the file identified by {@code filename}
	 *   is not found or is not readable. 
	 */
	public CardSourceAsciiFile(
			String filename,
			char spoolClass,
			int nominalCardLen,
			boolean breakLongLines
			) throws FileNotFoundException {
		File source = new File(filename);
		if (!source.exists() || !source.canRead()) {
			throw new FileNotFoundException();
		}
		
		if (nominalCardLen < 1) {
			throw new IllegalArgumentException("'nominalCardLen' must be at least 1");
		}
		
		this.spoolClass = spoolClass;
		this.nominalCardLen = nominalCardLen;
		this.breakLongLines = breakLongLines;
		this.source = source;
	}
	
	@Override
	public char getSpoolClass() {
		return this.spoolClass;
	}
	
	@Override
	public int getNominalCardLength() {
		return this.nominalCardLen;
	}
	
	private int get() {
		try {
			return this.fis.read();
		} catch (IOException e) {
			return -1;
		}
	}

	@Override
	public int readCard(byte[] buffer, int offset, int length) {
		if (this.fis == null) {
			// first card to read, so open the source file
			try {
				this.fis = new FileInputStream(this.source);
				this.lastByte = this.get();
				if (this.lastByte < 0) { this.close(); }
			} catch (FileNotFoundException e) {
				// the file existence was checked, so it was removed in the meantime...?
				this.lastByte = -1;
				this.fis = new TerminatedInputStream();
				return -1;
			}
		}
		
		// already at end?
		if (this.lastByte < 0) { return -1; }
		
		// invalid buffer specification? => nothing to read
		if (buffer == null) { return 0; }
		if (offset >= buffer.length) { return 0; }
		if (offset < 0) { return 0; }
		if (length < 1) { return 0; }

		// prepare buffer information
		int pos = offset;
		int currLen = 0;
		int maxLen = Math.min(length,  this.nominalCardLen);
		if ((offset + maxLen) > buffer.length) {
			maxLen = buffer.length - offset;
		}
		
		// read a card content
		while (currLen < maxLen && this.lastByte >= 0 && this.lastByte != LF) {
			// translate last byte encountered to EBCDIC and place it into the buffer 
			buffer[pos++] = Ebcdic.a2e((byte)this.lastByte);
			currLen++;
			
			// get next byte from the file
			this.lastByte = this.get();
			while (this.lastByte == CR) {
				this.lastByte = this.get();
			}
		}
		
		// handle card end
		if (this.lastByte == LF) {
			this.lastByte = this.get();
		} else if (!this.breakLongLines) {
			// there are still bytes on this line, but the next read may not see this rest as a new card
			// so drop the rest of the line
			while (this.lastByte >= 0 && this.lastByte != LF) {
				this.lastByte = this.get();
			}
			if (this.lastByte >= 0) {
				this.lastByte = this.get();
			}
		}
		
		// did we reach the end-of-file?
		if (this.lastByte < 0) {
			this.close();
			if (currLen == 0) {
				// we did not read a single byte since the last card, so the
				// file ended with a newline of the last line, so the previous
				// call to this method delivered the last card...
				return -1;
			}
		}
		
		// fill with (EBCDIC-) blanks up to maxLen
		while(currLen < maxLen) {
			buffer[pos++] = (byte)0x40;
			currLen++;
		}
		
		// done
		return currLen;
	}

	@Override
	public void close() {
		// is it open?
		if (this.fis != null) {
			// already closed?
			if (this.fis instanceof TerminatedInputStream) { return; }
			// close file source
			try { this.fis.close(); } catch (IOException e) { }
		}
		// block any further reading
		this.fis = new TerminatedInputStream();
		this.lastByte = -1;
	}

}
