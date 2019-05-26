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

/**
 * Interface for tape format providers, i.e. classes that allow to read and
 * write a tape file format.
 * 
 * <p>
 * The tape content is completely loaded uncompressed into memory as a chain
 * of tape blocks. So the tape file must fit into the heap besides the other
 * resources allocated to all VMs like main memory, disks etc.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public interface iTapeIo {
	
	/**
	 * Can the tape file associated with this instance be written (back)
	 * to disk?
	 *  
	 * @return {@code true} if the tape is to be treated a a writable tape.
	 */
	public boolean isWritable();

	/**
	 * Load the content of the tape into memory as list of tape blocks
	 * and place the block chain between {@code headLimit} and 
	 * {@code tailLimit].
	 * 
	 * @param headLimit the head of the block-chain where the tape blocks
	 *   must be chained behind.
	 * @param tailLimit the tail of the block chain where the tape blocks
	 *   must be chained before.
	 * @return the number of tape blocks read.
	 */
	public int readTapeFile(TapeBlock headLimit, TapeBlock tailLimit);

	/**
	 * Save the tape blocks between {@code headLimit} and {@code tailLimit}
	 * to disk.
	 * 
	 * @param headLimit the block in the chain before the first tape block to save.
	 * @param tailLimit the block in the chain after the last tape block to save. 
	 */
	public void writeTapeFile(TapeBlock headLimit, TapeBlock tailLimit);
	
	/**
	 * Get the name of the external tape file backing the tape loaded with this
	 * instance.
	 * 
	 * @return the tape filename.
	 */
	public String getTapeFilename();

}