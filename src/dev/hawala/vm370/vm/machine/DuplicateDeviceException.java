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

public class DuplicateDeviceException extends Exception {
	private static final long serialVersionUID = -8452992462267071712L;
	
	private final int cuu;
	
	public DuplicateDeviceException(int cuu) {
		super(String.format("Duplicate device %03X for this virtual machine", cuu));
		this.cuu = cuu;
	}
	
	public int getCuu() { return this.cuu; }
}
