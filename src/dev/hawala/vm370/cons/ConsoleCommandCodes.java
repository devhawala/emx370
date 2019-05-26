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

package dev.hawala.vm370.cons;

import dev.hawala.vm370.vm.device.iDevice;

/**
 * Definition of the command codes for 3215 or 3270 type console devices as passed from the
 * console {@link iDevice} (used in SIO  resp. DIAG instructions) to the related UserConsole
 * for execution of the I/O operation on the terminal.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public final class ConsoleCommandCodes {
	
	/*
	 * serial console I/O operations (3215 style)
	 */

	public static final int Read = 0x0A; // read
	
	public static final int WriteNoCR = 0x01; // write
	
	public static final int WriteAddCR = 0x09; // write
	
	public static final int ControlNOOP = 0x03; // control
	
	public static final int Limit3215 = 0x10; // limit for serial (3215) operations

	/*
	 * full screen I/O operations: special codes used for communication DIAG-x58 => 3270-capable-iDevice
	 */
	
	public static final int Write_FS_W = 0x11; // write - W - WRITE
	
	public static final int Write_FS_EW = 0x21; // write - EW - ERASE/WRITE
	
	public static final int Write_FS_EWA = 0x31; // write - EW - ERASE/WRITE ALTERNATE
	
	public static final int Write_FS_WSF = 0x41; // write - WSF - WRITE STRUCTURED FIELD
	
	public static final int Read_FS_RB = 0x1A; // read - RB - READ BUFFER
	
	public static final int Read_FS_RM = 0x2A; // read - RM - READ MODIFIED
	
	public static final int CheckFullscreenSupport = 0x63; // control
	
	/*
	 * DIAG-x58 "Display Data" operations: emulation of a 3270 console layouted in VM/370 style
	 */
	
	// the 3270 screen layout emulation has 24 lines, the start line is specified in
	// the first 5 bits, the remaining 3 bits detailing the display operation:
	// Bits: xxxxx101 (Write)   => display data at line xxxxxB without clearing screen
	//       xxxxx111 (Control) => display data at line xxxxxB after clearing screen
	//
	
	// but: CTL = 0xFF = clear output area, but write nothing
	public static final int ClearScreen = 0xFF; // control
}
