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

import dev.hawala.vm370.ebcdic.EbcdicHandler;
import dev.hawala.vm370.spool.CloseableDiskFileSink;

/**
 * Card sink for punching to the file system as ASCII file.
 * <p>
 * All cards will be truncated to 80 bytes or trimmed to remove
 * trailing blanks. The file will have the extension {@code txt}.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 */
public class CardSinkAsciiFile extends CloseableDiskFileSink implements iCardSink {
	
	/**
	 * Create the card sink for punch, creating the output file
	 * to the specified directory.
	 * 
	 * @param outputDirectory the target directory for punch files.
	 * @throws IOException if the file cannot be created.
	 */
	public CardSinkAsciiFile(String outputDirectory) throws IOException {
		super(outputDirectory, "pun", "txt");
	}	
	
	private final EbcdicHandler card = new EbcdicHandler(80);

	@Override
	public void writeCard(byte[] buffer, int offset, int length) {
		if (this.sink == null) { return; }
		this.card.reset().appendEbcdic(buffer, offset, length).strip();
		this.sink.println(this.card.getString());
	}
	
}
