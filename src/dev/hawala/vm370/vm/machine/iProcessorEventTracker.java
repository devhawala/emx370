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

package dev.hawala.vm370.vm.machine;

/**
 * Interface for logger object capable to trace the events related to CPU, Device-I/O or the like.
 * 
 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2015
 *
 */
public interface iProcessorEventTracker {

	/**
	 * Add a line to the event ring buffer.
	 * 
	 * If the text line passed contains placeholders, these will be substituted
	 * only when the event text is written out (late usage).
	 * 
	 * @param line text line to be logged possibly containing printf-like placeholders. 
	 * @param args values for placeholders in 'line'
	 */
	public void logLine(String line, Object... args);
	
}
