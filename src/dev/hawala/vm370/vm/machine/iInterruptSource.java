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
 * Interface for items which can generate interrupts for a CPU.
 * 
 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2015
 *
 */

public interface iInterruptSource {
	
	/**
	 * Is there a pending interrupt signaling the completion of an I/O operation?
	 * 
	 * @return true if a completion interrupt is waiting to be initiated.
	 */
	public boolean hasPendingCompletionInterrupt();
	
	/**
	 * Consume the pending completion interrupt by initiating the interrupt
	 * on the given CPU.
	 * 
	 * The interrupt is to be initiated by saving the current PSW to the old-PSW
	 * storage location for the interrupt type and loading the PSW from the new-PSW
	 * location for the interrupt.
	 * 
	 * @param cpu the CPU to be interrupted.
	 */
	public void initiateCompletionInterrupt(Cpu370Bc cpu) throws PSWException;
	
	/**
	 * Is there one (or more) pending interrupt(s) signaling the availability of
	 * asynchronous events?
	 *    
	 * @return true if an asynchronous event interrupt is waiting to be initiated. 
	 */
	public boolean hasPendingAsyncInterrupt();
	
	/**
	 * Consume a pending asynchronous event interrupt by initiating the interrupt
	 * on the given CPU.
	 * 
	 * The interrupt is initiated by saving the current PSW to the old-PSW
	 * storage location for the interrupt type and loading the PSW from the
	 * new-PSW location for this interrupt type.
	 * 
	 * @param cpu the CPU to be interrupted.
	 */
	public void initiateAsyncInterrupt(Cpu370Bc cpu) throws PSWException;
	
	/**
	 * Get the PSW interrupt mask bit relevant for this interrupt source.
	 * 
	 * (bit 0..5 for channel 0..5, bit 6 for all other I/O sources, bit 7 for external interrupts)
	 *  
	 * @return the interrupt mask bit for this interrupt source
	 */
	public byte getIntrMask();

}
