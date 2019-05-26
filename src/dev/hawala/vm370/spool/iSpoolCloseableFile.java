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

package dev.hawala.vm370.spool;

/**
 * Common close functionality for spool files.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 */
public interface iSpoolCloseableFile {
	
	/**
	 * Close the output file and output job. No more output
	 * will be written to the file if a write operation is
	 * invoked. 
	 * 
	 * @param closeInfo the NAME parameter value of the CP CLOSE
	 *   command.
	 */
	public void close(String closeInfo);

	/**
	 * Close the file and output job and drop any data written so far.
	 */
	public void purge();

}
