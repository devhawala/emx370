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

/**
 * Card source for EBCDIC (i.e. binary) files, reading 80 raw bytes for a single
 * card, filling the last card with blanks or zeros if the files length is not
 * a multiple of 80.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 */
public class CardSourceEbcdicFile implements iCardSource {
	
	private static final int CARDLENGTH = 80;

	private final char spoolClass;
	private final boolean fillWithZeros;
	private final File source;
	
	private InputStream fis = null;
	private boolean done = false;

	public CardSourceEbcdicFile(
			String filename,
			char spoolClass,
			boolean fillWithZeros
			) throws FileNotFoundException {
		File source = new File(filename);
		if (!source.exists() || !source.canRead()) {
			throw new FileNotFoundException();
		}
		
		this.source = source;
		this.spoolClass = spoolClass;
		this.fillWithZeros = fillWithZeros;
	}

	@Override
	public char getSpoolClass() {
		return this.spoolClass;
	}

	@Override
	public int getNominalCardLength() {
		return CARDLENGTH;
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
		// already done with the source?
		if (this.done) { return -1; }
		
		// open the file on first read
		if (this.fis == null) {
			try {
				this.fis = new FileInputStream(this.source);
			} catch (FileNotFoundException e) {
				// the file existence was checked, so it was removed in the meantime...?
				this.done = true;
				return -1;
			}
		}
		
		// invalid buffer specification? => nothing to read
		if (buffer == null) { return 0; }
		if (offset >= buffer.length) { return 0; }
		if (offset < 0) { return 0; }
		if (length < 1) { return 0; }

		// prepare buffer information
		int pos = offset;
		int currLen = 0;
		int maxLen = Math.min(length,  CARDLENGTH);
		if ((offset + maxLen) > buffer.length) {
			maxLen = buffer.length - offset;
		}
		
		// read a card content
		while (currLen < maxLen) {
			int b = this.get();
			if (b < 0) {
				// end of input
				this.close();
				if (currLen == 0) { return -1; }
				break;
			}
			buffer[pos++] = (byte)b;
			currLen++;
		}
		
		// fill with fill byte up to maxLen
		while(currLen < maxLen) {
			buffer[pos++] = (this.fillWithZeros) ? (byte)0x00 : (byte)0x40;
			currLen++;
		}
		
		// done
		return currLen;
	}

	@Override
	public void close() {
		if (this.fis != null) {
			// close file source
			try { this.fis.close(); } catch (IOException e) { }
		}
		this.fis = null;
		this.done = true;
	}
}
