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
package dev.hawala.vm370.vm;

import dev.hawala.vm370.vm.machine.Cpu370Bc;

/**
 * Interface for SVC handler that can be registered to handle a specific
 * SVC code when the cpu processes a SVC instruction. 
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */

public interface iSvcInterceptor {

	/**
	 * Process a SVC call, possibly replacing the implementation inside the
	 * virtual machine.  
	 * 
	 * @param svcNo the number of the supervisor call
	 * @param cpu the CPU processing the SVC instruction, with the instruction
	 *   address already pointing at the instruction directly behind the
	 *   SVC instruction.
	 * @return {@code true} if the SVC was completely handled and processing
	 *   of the instruction must be skipped (in this case, the interceptor must
	 *   have set the PSW state as required by the SVC, e.g. CC or the instruction
	 *   address pointing behind the error continuation address in the case of
	 *   a SVC 202 call).
	 */
	public boolean interceptSvc(int svcNo, Cpu370Bc cpu);
	
}
