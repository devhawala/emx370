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

/**
 * Definition of a source for card content for a RDR device.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 *
 */
public interface iCardSource {
	
	/**
	 * Get the spool class of this card source.
	 * 
	 * @return the spool class
	 */
	char getSpoolClass();
	
	/**
	 * Get the maximal card length that will be delivered by this source.
	 * 
	 * @return the nominal card length of the source.
	 */
	int getNominalCardLength();

	/**
	 * Read a single card and put the content into the specified region
	 * of the given buffer, returning the number of bytes read.  
	 * 
	 * @param buffer the buffer where to read to.
	 * @param offset the offset of the first byte in {@code buffer}
	 *   to write to.
	 * @param length the maximum number of bytes to transfer
	 * @return the number of bytes effectively transferred (as max. value
	 *   of available bytes in the card resp. {@code length} limited by the
	 *   remaining length due to {@code offset}) or {@code -1} if there
	 *   are no more cards in the source.
	 */
	int readCard(byte[] buffer, int offset, int length);
	
	/**
	 * Stop using this card source, freeing all resources used by this
	 * card source.
	 * <p>
	 * Subsequent invocations of {@code readCard()} will return {@code -1}.
	 */
	void close();
	
}
