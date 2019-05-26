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

package dev.hawala.vm370.print;

import dev.hawala.vm370.spool.iSpoolCloseableFile;

/**
 * Functionality of a "real" (not spooled) print target, i.e. an external file
 * of arbitrary format.
 * <p>
 * An instance implementing this interface is good for exactly one
 * print job, i.e. after invoking {@link close()} or {@link purge()}
 * the print target will ignore any operations and a new instance
 * must be created for new print jobs.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 */
public interface iPrintSink extends iSpoolCloseableFile {
	
	/**
	 * Output a single print line at the current position as defined
	 * by previous {@link spaceLines} or {@link skipToChannel} calls,
	 * placing the output position back to this position again.
	 * <p>
	 * If possible, when 2 consecutive calls to this method occur,
	 * the second call should overprint (not replace) the output
	 * of the previous call. 
	 * 
	 * @param line the text to be written as a single line.
	 */
	public void printLine(String line);
	
	/**
	 * Advance the current position by the given number of lines,
	 * possibly moving to a new page.
	 * 
	 * @param count the nmber of lines to advance.
	 */
	public void spaceLines(int count);
	
	/**
	 * Move the current position to the next page and advance there
	 * by the number of lines associated with the {@code channel}
	 * specified.
	 *  
	 * @param channel the logical position on the new page,
	 *   valid values are 0..11.
	 */
	public void skipToChannel(int channel);
	
	/**
	 * Define the line offsets on a new page associated with
	 * the channels 0..11, starting with channel 0. Superfluous
	 * positions are ignored, channels not specified are left
	 * unchanged, with the initial offset being 0 for all channels,
	 * meaning the channels will move to the top of a new page
	 * by default.
	 * 
	 * @param lineOffsets the line offsets on a new page for the
	 *   channels
	 * @return the device itself for command chainign.
	 */
	public iPrintSink channelSpacingLines(int... lineOffsets);
	
}
