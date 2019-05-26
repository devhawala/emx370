/*
** This file is part of the emx370 emulator.
**
** This software is provided "as is" in the hope that it will be useful, with
** no promise, commitment or even warranty (explicit or implicit) to be
** suited or usable for any particular purpose.
** Using this software is at your own risk!
**
** Written by Dr. Hans-Walter Latz, Berlin (Germany), 2015
** Released to the public domain.
*/

package dev.hawala.vm370.vm.device;


/**
 * Public interface for S/370-PrincOps-simulating devices.
 *  
 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2015
 *
 */
public interface iDevice {
	
	/**
	 * Prepare the internal device state for a new I/O operation sequence
	 * initiated by a SIO(F) or DIAG instruction.
	 */
	public void resetState();
	
	/**
	 * Perform a 'read'-type operation on the device.
	 * 
	 * @param opcode
	 * 		the complete operation byte (flags including the operation
	 * 		code) of the operation.
	 * @param dataLength
	 * 		the length of the memory area specified with the CCW command.
	 * @param memTarget
	 * 		the data transfer object instance for transferring data
	 * 		from the device to the memory.  
	 * @return
	 * 		the unit status resulting of the operation as ORed flags defined
	 * 		in IDeviceStatus. 
	 */
	public int read(int opcode, int dataLength, iDeviceIO memTarget);
	
	/**
	 * Perform a 'write'-type operation on the device.
	 * 
	 * @param opcode
	 * 		the complete operation byte (flags including the operation
	 * 		code) of the operation.
	 * @param dataLength
	 * 		the length of the memory area specified with the CCW command.
	 * @param memSource
	 * 		the data transfer object instance for transferring data
	 * 		from the memory to the device.  
	 * @return
	 * 		the unit status resulting of the operation as ORed flags defined
	 * 		in IDeviceStatus. 
	 */
	public int write(int opcode, int dataLength, iDeviceIO memSource);
	
	/**
	 * Perform a 'sense'-type operation on the device.
	 * 
	 * @param opcode
	 * 		the complete operation byte (flags including the operation
	 * 		code) of the operation.
	 * @param dataLength
	 * 		the length of the memory area specified with the CCW command. 
	 * @param memTarget
	 * 		the data transfer object instance for transferring data
	 * 		from the device to the memory.  
	 * @return
	 * 		the unit status resulting of the operation as ORed flags defined
	 * 		in IDeviceStatus. 
	 */
	public int sense(int opcode, int dataLength, iDeviceIO memTarget);
	
	/**
	 * Perform a 'control'-type operation on the device.
	 * 
	 * @param opcode
	 * 		the complete operation byte (flags including the operation
	 * 		code) of the operation.
	 * @param dataLength
	 * 		the length of the memory area specified with the CCW command. 
	 * @param memSource
	 * 		the data transfer object instance for transferring data
	 * 		from the memory to the device.  
	 * @return
	 * 		the unit status resulting of the operation as ORed flags defined
	 * 		in IDeviceStatus. 
	 */
	public int control(int opcode, int dataLength, iDeviceIO memSource);
	
	/* ... not supported:
	public int readBackward(int flags, int dataLength, IDeviceIO memTarget);
	*/
	
	/**
	 * Is there one (or more) pending interrupt(s) signaling the availability of
	 * asynchronous events?
	 *    
	 * @return true if an asynchronous event interrupt is waiting to be initiated. 
	 */
	public boolean hasPendingAsyncInterrupt();
	
	/**
	 * Consume the next pending asynchronous event interrupt by initiating the interrupt
	 * on the given CPU. (for example by decrementing the number of waiting asynchronous
	 * interrupts)
	 */
	public void consumeNextAsyncInterrupt();
	
	/**
	 * Issue an Attention interrupt at next occasion, if supported by the device. 
	 */
	public void doAttentionInterrupt();
	
	/**
	 * Get the virtual device information data for DIAG-x24 (field Ry).
	 * @return the 4 bytes for the VDEV result register.
	 */
	public int getVDevInfo();
	
	/**
	 * Get the real device information data for DIAG-x24 (field Ry+1).
	 * @return the 4 bytes for the RDEV result register.
	 */
	public int getRDevInfo();
	
	/**
	 * Get the sense byte at the specified index, return a zero byte (0x00) if
	 * the index is out of range. 
	 * 
	 * @param index the sense byte position to return (0-based)
	 * @return the sense byte at position 'index'
	 */
	public byte getSenseByte(int index);
	
	/**
	 * Get the device type name for this device (e.g. CONS, DASD, TAPE)
	 * as used by CP
	 * 
	 * @return the printable device type name.
	 */
	public String getCpDeviceTypeName();
	
	/**
	 * Get the status line for the CP command {@code QUERY [VIRTUAL] [type] [cuu]}
	 * 
	 * @param asCuu the channel and unit number (CUU) of this device in the VM
	 * @return the status string
	 */
	public String getCpQueryStatusLine(int asCuu);
	
}
