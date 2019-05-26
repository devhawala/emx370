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

import java.io.IOException;

import dev.hawala.vm370.spool.CloseableDiskFileSink;

/**
 * Card sink for punching to the file system as EBCDIC file.
 * <p>
 * All cards will be truncated/filled to 80 bytes, filling will be
 * done with EBCDIC blanks. The file will have the extension {@code data}.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 */
public class CardSinkEbcdicFile extends CloseableDiskFileSink implements iCardSink {
	
	/**
	 * Create the card sink for punch, creating the output file
	 * to the specified directory.
	 * 
	 * @param outputDirectory the target directory for punch files.
	 * @throws IOException if the file cannot be created.
	 */
	public CardSinkEbcdicFile(String outputDirectory) throws IOException {
		super(outputDirectory, "pun", "data");
	}

	@Override
	public void writeCard(byte[] buffer, int offset, int length) {
		if (this.sink == null) { return; }
		int written = 0;
		for(int i = offset; i < buffer.length && written < iCardSink.CARDLENGTH; i++, written++) {
			this.sink.write(buffer[i]);
		}
		while(written++ < iCardSink.CARDLENGTH) {
			this.sink.write((byte)0x40);
		}
	}
	
}
