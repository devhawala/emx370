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
 * Exception signaling the activation of a PSW preventing further processing
 * by the CPU.
 * 
 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2015
 *
 */
public class PSWException extends Exception {
	private static final long serialVersionUID = 4124765201077441023L;
	
	/**
	 * representation of the reason why the CPU cannot continue processing. 
	 * 
	 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2015
	 *
	 */
	public enum PSWProblemType {
		/** the new PSW specifies the WAIT state with all interrupts disabled. */
		DisabledWait,
		
		/** the new PSW specifies the extended control mode not supported by this CPU emulation */
		ECMode,
		
		/** the new PSW instruction address after matches a breakpoint address */
		Breakpoint
	};
	
	private final PSWProblemType problemType;
	private final int pswFrom;
	
	/**
	 * Construction of the exception specifying the preventing problem and the location
	 * from where the PSW was loaded.
	 * 
	 * @param problemType the problem which prevents processing.
	 * @param pswFrom the main memory location from where the PSW was loaded.
	 */
	public PSWException(PSWProblemType problemType, int pswFrom) {
		super("Invalid state in PSW: " + problemType);
		this.problemType = problemType;
		this.pswFrom = pswFrom;
	}
	
	/**
	 * Get the problem preventing further CPU processing.
	 * @return the problem type.
	 */
	public PSWProblemType getProblemType() { return this.problemType; } 
	
	/**
	 * Get the location from where the PSW was loaded from main memory.
	 * 
	 * @return the PSW address in main memory.
	 */
	public int getPswFrom() { return this.pswFrom; }
}
