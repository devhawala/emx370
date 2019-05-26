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

import dev.hawala.vm370.ebcdic.EbcdicHandler;

/**
 * Definition of the command processor for the DIAG-X08
 * CP command processor.
 * 
 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2015
 *
 */
public interface iCommandExecutor {

	/**
	 * Process one or more commands, appending the messages the given buffer or
	 * sending the result messages to the user's console if no output buffer is given.
	 * 
	 * @param commandBuffer the command(s) to be executed.
	 * @param outputBuffer the buffer into which the result messages are to be appended
	 * 		or null if messages are t send to the user's console.
	 * @return the completion code for command(s). 
	 */
	public int processCommandBuffer(
						EbcdicHandler commandBuffer,
						EbcdicHandler outputBuffer
						);
	
	
	/**
	 * Check if some commands arrived asynchronously and process them.
	 */
	public void executePendingAsyncCommands();
}
