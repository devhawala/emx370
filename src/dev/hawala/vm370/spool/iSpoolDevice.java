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

package dev.hawala.vm370.spool;

import dev.hawala.vm370.vm.device.iDevice;

/**
 * Definition of the interface to spooling for a spool-attachable
 * device (RDR, PUN, PRT).
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 *
 */
public interface iSpoolDevice extends iDevice {

	/**
	 * All: inquiry if this device is a data source (i.e. a RDR) or
	 * a data sink (i.e. a PRT or PUN).
	 * 
	 * @return {@code true} if this is a data source.
	 */
	boolean isReader();

	/**
	 * All: get the current spool class of this device.
	 * 
	 * @return the current spool class character (UNICODE, A-Z0-9)
	 */
	char getSpoolClass();

	/**
	 * All: set the spool class for this device. This spool class will
	 * be used for I/O after the next(!) non-hold(!) {@link close()}
	 * operation.
	 * 
	 * @param newSpoolClass the new spoll class character (UNICODE, A-Z0-9)
	 */
	void setSpoolClass(char newSpoolClass);

	/**
	 * All: is the device set up to do continuous I/O, i.e. to ignore
	 * a {@link close()} operation (data sinks) resp. end-of-file conditions
	 * (data sources).
	 *  
	 * @return the current CONT flag status.
	 */
	boolean isSpoolCont();

	/**
	 * All: set the continuous I/O flag for the spool device.
	 * 
	 * @param newCont the new CONT flag.
	 */
	void setSpoolCont(boolean newCont);

	/**
	 * ?? relevant ??
	 * @return current HOLD flag.
	 */
	boolean isSpoolHold();

	/**
	 * ?? relevant ??
	 * @param newCont the new HOLD flag.
	 */
	void setSpoolHold(boolean newCont);
	
	/**
	 * Readers: present end-of-file (in unit-exception condition) at end of
	 * a spool file (true) or continue with next spool file (false)?
	 * 
	 * @return current EOF flag.
	 */
	boolean isSpoolEof();
	
	/**
	 * Readers: set the EOF flag (see {@link isSpoolEof()}). 
	 * 
	 * @param newEof new EOF flag.
	 */
	void setSpoolEof(boolean newEof);

	/**
	 * ?? relevant ?? 
	 * @return
	 */
	int getSpoolCopies();
	
	/**
	 * ?? relevant ??
	 * @param newCopies current copies value
	 */
	void setSpoolCopies(int newCopies);

	// temp!
	/**
	 * ?? relevant ??
	 * @return ??
	 */
	String getSpoolToUser();

	// temp!
	/**
	 * ?? relevant ??
	 * @param newToUser ??
	 */
	void setSpoolToUser(String newToUser);

	/**
	 * Punch/Printer: close the current I/O item with the given closing
	 * information.
	 * It is the device responsibility to react on the (NO)HOLD status
	 * if I/O is not spooled but directly works on a real device.
	 * 
	 * @param closeInfo the value given for the NAME parameter of
	 *   the CP CLOSE command.
	 */
	void close(String closeInfo);
	
	/**
	 * Punch/Printer: Close the current I/O item and drop any data remaining
	 * or written so far.
	 */
	void purge();

}