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
 * Definition of the relevant status flag generated at channel level by the CCW-processor.
 * 
 * (most flags defined by the PrincOps are not relevant as unsupported like Program controlled Interrupt
 * or related to hardware failures not simulated here)
 * 
 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2015
 *
 */
public final class iDeviceChannelStatus {

	/**
	 * All channel status bits set to zero. 
	 */
	public final static int OK = 0;
	
	/** Long/short block on input or output data transfer between memory and device.
	 * 
	 */
	public final static int INCORRECT_LENGTH = 0x40;
	
	/** CCW programming error, with the relevant variants:
	 * - invalid CCW address specification (not a double-word address in CAW or transfer-in-channel)
	 * - invalid command code (4 low-order zeroes)
	 * - invalid count (count = 0)
	 * - invalid IDAW address specification (first IDAW not on a word boundary)
	 * - invalid IDAW specification (bits 0-7 not zero, subsequent IDAW not a page boundary)
	 * - invalid CAW format (required zero bits not zero)
	 * - invalid CCW format (required zero bits not zero)
	 * - invalid sequence (CAW specifies a transfer-in-channel or 2 consecutive transfer-in-channel)
	 */
	public final static int PROGRAM_CHECK = 0x20;
	
	/*
	 * not available status flags:
	 * - Program-Controlled Interruption: not supported (as an interrupt can occur only after the complete chain is processed)
	 * - Protection-Check: not relevant (no protection available)
	 * - Channel Data Check: not relevant (no machine parity errors when transferring data)
	 * - Channel Control Check: not relevant (no detectable machine malfunctions or damages)
	 * - Interface Control Check: not relevant (no detectable I/O device malfunctioning)
	 * - Chaining Check: not relevant (no data rate mismatches provoking channel overruns) 
	 */
}
