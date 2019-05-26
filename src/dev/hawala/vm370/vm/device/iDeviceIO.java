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
 * Adapter for data transfer between a device and main memory as defined by a CCW(-chain). 
 * 
 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2015
 *
 */

public interface iDeviceIO {
	
	/** Transfers data between main memory areas (as defined by the initiating CCW
	 *  and possible data chained CCWs) and the device buffer area, with the transfer
	 *  direction being specified by the operation type (read, write, control, sense)
	 *  to which this IDeviceIO instance is passed.
	 *  For the device, it must be a contiguous data area, whereas a possible
	 *  fragmentation of CPU memory is handled by the implementation this method.
	 *  
	 *  This method does the complete data transfer for a single I/O operation, it is
	 *  not possible to split the data transfer into sequential calls to this method.
	 *  
	 *  In case of a channel error (invalid CCW content or the like), the operation
	 *  is either not started or aborted during data chaining or IDA interpretation,
	 *  returning the area length comparison result at the point of stopping the
	 *  data transfer.
	 * 
	 * @param devMemory the device's data area to be read or written
	 * @param offset the start position of the target device data area
	 * @param length the length of the target device data area to be read or written
	 * @return the remaining count of main memory area at the end of the transfer,
	 *   with the following meanings:
	 *   0 (zero):
	 *      the memory data area length was identical to the parameter 'length'
	 *   positive value:
	 *      the number of bytes that the memory data area was larger than the
	 *      device's area (parameter 'length'), i.e. the number of memory bytes not
	 *      used in data transfer.
	 *   negative value:
	 *      the number of bytes that the memory area was shorter than the area
	 *      presented by the device, i.e. (as absolute value) the number of device
	 *      bytes not used in data transfer.
	 */
	public int transfer(byte[] devMemory, int offset, int length);
}
