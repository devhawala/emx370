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

package dev.hawala.vm370.dasd;

import dev.hawala.vm370.vm.device.iDevice;
import dev.hawala.vm370.vm.machine.iProcessorEventTracker;

/**
 * Extension of the {@link iDevice} interface for DASD devices.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public interface iDasd extends iDevice {
	
	/**
	 * Set the event tracker for logging from the device.
	 *  
	 * @param eventTracker the new logger or {@code null} for no logging.
	 */
	public void setEventTracker(iProcessorEventTracker eventTracker);
	
	/**
	 * get the write protection state of the device.
	 * 
	 * @return {@code true} if the device is write protected.
	 */
	public boolean isWriteProtected();

	/**
	 * Set the device in write protected mode.
	 */
	public void setWriteProtected();
	
	/**
	 * Check if data on the device was changed.
	 * 
	 * @return {@code true} if data was written on the drive.
	 */
	public boolean needsSaving();
	
	/**
	 * Save the complete content of the device or only the changes to the drive
	 * to disk, depending on which parameter is passed as non-{@code null}.
	 *   
	 * @param deltaFile if non-{@code null}, save all changes relatively to the
	 *   base file to this file, so the drive's content can be restored by loading
	 *   the base file and the changes from this delta file.
	 * @param baseFile if no delta file is given, save the complete drive content
	 *   to this file.
	 * @throws Exception if a problem occured while savong the drive's content.
	 */
	public void saveTo(String deltaFile, String baseFile) throws Exception;
	
}
