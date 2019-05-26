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

import dev.hawala.vm370.spool.iSpoolCloseableFile;

/**
 * Functionality of a "real" (not spooled) punch target, i.e. an external file
 * of arbitrary format.
 * <p>
 * An instance implementing this interface is good for exactly one
 * punch job, i.e. after invoking {@link close()} or {@link purge()}
 * the punch target will ignore any operations and a new instance
 * must be created for new punch jobs.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 */
public interface iCardSink extends iSpoolCloseableFile {
	
	/**
	 * The maximum length of a punch card.
	 */
	public final int CARDLENGTH = 80;

	/**
	 * Output a single card content from the specified region
	 * of the given buffer.
	 * <p>
	 * The card content will be truncated or filled up to a length of 80 bytes.
	 * </p>
	 * 
	 * @param buffer the buffer with the data to write.
	 * @param offset the offset of the first byte in the buffer.
	 * @param length the number of bytes to read as card content.
	 */
	public void writeCard(byte[] buffer, int offset, int length);

}
