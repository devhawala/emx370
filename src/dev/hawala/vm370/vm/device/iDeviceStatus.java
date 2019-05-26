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

package dev.hawala.vm370.vm.device;

/**
 * Definition of the status flag bits delivered by devices after processing
 * CCW commands (also called: unit status).
 * 
 * (but: here only relevant status for devices which do not work asynchronously,
 * i.e. each operation always completes before returning to the CCW-processor,
 * so status bits signaling on-going operations are irrelevant and not defined)
 * 
 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2015
 *
 */
public final class iDeviceStatus {
	
	/**
	 * All unit status bits set to zero. 
	 */
	public final static int OK = 0;
	
	/**
	 * Asynchronous condition that is relevant to the program.
	 * (see PrincOps-1975 page 229)
	 * 
	 * here relevant in combination with:
	 * - DEVICE_END .. causes command chaining to be suppressed.
	 */
	public final static int ATTENTION = 0x80;
	
	/**
	 * (see PrincOps-1975 page 229)
	 * 
	 * here relevant in combination with:
	 * - DEVICE_END .. the normal sequence of commands must be modified
	 *   (meaning: in command chaining fetch next CCW at 16 bytes offset from
	 *    current CCW instead of normal 8 bytes offset for direct sequencing)
	 */
	public final static int STATUS_MODIFIER = 0x40;
	
	/** The data transfer part of the operation is completed.
	 * 
	 *  here: as CCWs are executed synchronously, channel end and device end
	 *  are normally presented together by the device if the operation is successful.
	 *  
	 */
	public final static int CHANNEL_END = 0x08;
	
	/** The device has successfully completed the operation of the CCW. 
	 * 
	 */
	public final static int DEVICE_END = 0x04;
	
	/** An unusual condition was detected with additional details available
	 * through a sense command.
	 * 
	 * If presented with channel end and status modifier, the command retry is
	 * initiated.
	 * 
	 */
	public final static int UNIT_CHECK = 0x02;
	
	/** A condition "that does not usually occur" was detected by the device,
	 * with each command of a particular device having at most one such condition
	 * being signaled with this status.
	 *  
	 * (see PrincOps-1975 page 233)
	 */
	public final static int UNIT_EXCEPTION = 0x01;
	
	/** Overrule a possible IDeviceChannelStatus.INCORRECT_LENGTH indication
	 * set during data transfer, as the device corrected the length mismatch
	 * internally.
	 */
	public final static int INCORRECT_LENGTH_IS_OK = 0x0100;
}
