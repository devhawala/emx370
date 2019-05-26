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

import dev.hawala.vm370.vm.machine.Cpu370Bc;
import dev.hawala.vm370.vm.machine.PSWException;
import dev.hawala.vm370.vm.machine.iInterruptSource;
import dev.hawala.vm370.vm.machine.iProcessorEventTracker;

/**
 * Synchronous execution of I/O operations with a specific device in the main memory context of a CPU.
 * 
 * With synchronous meaning that the following is true:
 * - no parallel I/O operations
 * - no I/O operations while the CPU executes
 * 
 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2015
 */

public class DeviceHandler implements iDeviceIO, iInterruptSource {
	
	// constants for the Condition-Code represented as byte (but in fact 2 bits)
	protected final byte CC0 = (byte)0;
	protected final byte CC1 = (byte)1;
	protected final byte CC2 = (byte)2;
	protected final byte CC3 = (byte)3;
	
	// the CPU's main memory
	private byte[] mem;
	
	// the device to handle
	private final iDevice devUnit;
	private final int devCuu;
	private final byte intrMaskBit;
	
	// the logger for events
	private final iProcessorEventTracker eventLogger;
	
	/** Construct the new device handler for a given device at the given CUU.
	 * 
	 * @param dev the device to handle
	 * @param cuu the device number of the device (purely informative)
	 */
	public DeviceHandler(byte[] mainMemory, iDevice dev, int cuu, iProcessorEventTracker eventTracker) {
		this.mem = mainMemory;
		this.devUnit = dev;
		this.devCuu = cuu & 0xFFFF; // limit to 16 significant bits (short), a the interrupt code in the PSW is 16 bits long 
		int channel = this.devCuu >> 8;
		if (channel < 7) {
			this.intrMaskBit = (byte)(0x80 >> channel); // bit 0..5
		} else {
			this.intrMaskBit = (byte)0x02; // bit 6
		}
		this.eventLogger = eventTracker;
	}
	
	/**
	 * Get the device unit number (CUU).
	 * @return the device unit number (CUU).
	 */
	public int getCUU() { return this.devCuu; }
	
	/**
	 * Get the channel number for the device handled by this instance. This is in fact the
	 * "C" part of the "CUU" device address.
	 * 
	 * @return the channel number of the device.
	 */
	public int getChannelNo() {
		return this.devCuu >> 8;
	}
	
	/**
	 * Get the device handled by this instance.
	 * @return the device.
	 */
	public iDevice getDevice() { return this.devUnit; }
	
	// is a completion interrupt to be enqueued?
	private boolean enqueueCompletionInterrupt = false;
	
	/**
	 * Was the CCW(-chain) executed to the point where the "channel" or the device
	 * did begin some work?
	 *   
	 * @return true if the first CCW was somehow executed by the channel/device resp. false
	 *   if checking the first CCW stopped with a CC != 0 (i.e. no I/O was initiated and
	 *   therefore no interrupt will follow to indicate the I/O end) 
	 */
	public boolean hasPendingCompletionInterrupt() {
		return this.enqueueCompletionInterrupt;
	}
	
	/**
	 * Clear a pending completion interrupt on the device.
	 */
	public void clearPendingCompletionInterrupt() {
		this.enqueueCompletionInterrupt = false;
	}
	
	/**
	 * Consume the pending completion interrupt by initiating the interrupt
	 * on the given CPU.
	 * 
	 * The interrupt is initiated by saving the current PSW to the old-PSW
	 * storage location for the interrupt type and loading the PSW from the
	 * new-PSW location for the interrupt.
	 * 
	 * @param cpu the CPU to be interrupted.
	 */
	public void initiateCompletionInterrupt(Cpu370Bc cpu) throws PSWException {
		cpu.initiateInterrupt(
				56,                  // location of program interrupt old PSW
				120,                 // location of program interrupt old PSW
				(short)this.devCuu); // intrCode (for I/O devices: CUU of the device attachment)
		this.storeCSW();
		this.enqueueCompletionInterrupt = false;
	}
	
	/**
	 * Is there one (or more) pending interrupt(s) signaling the availability of
	 * asynchronous events?
	 *    
	 * @return true if an asynchronous event interrupt is waiting to be initiated. 
	 */
	public boolean hasPendingAsyncInterrupt() {
		return this.devUnit.hasPendingAsyncInterrupt();
	}
	
	/**
	 * Clear a pending async interrupt on the device.
	 */
	public void clearPendingAsyncInterrupt() {
		if (this.devUnit.hasPendingAsyncInterrupt()) {
			this.devUnit.consumeNextAsyncInterrupt();
		}
	}
	
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
	public void initiateAsyncInterrupt(Cpu370Bc cpu) throws PSWException {
		if (this.devUnit.hasPendingAsyncInterrupt()) {
			this.devUnit.consumeNextAsyncInterrupt();
			cpu.initiateInterrupt(
					56,                  // location of i/o interrupt old PSW
					120,                 // location of i/o interrupt new PSW
					(short)this.devCuu); // intrCode (for I/O devices: CUU of the device attachment)
			
			// store async interrupt CSW: has all zeros except for the Attention bit in the Unit Status byte
			this.storeAttentionPSW();
		}
	}
	
	/**
	 * Get the PSW interrupt mask bit relevant for this interrupt source.
	 * 
	 * (bit 0..5 for channel 0..5, bit 6 for all other I/O sources, bit 7 for external interrupts)
	 *  
	 * @return the interrupt mask bit for this interrupt source
	 */
	public byte getIntrMask() {
		return this.intrMaskBit;
	}

	// Channel Status Word
	// (double-word stored in memory at memory location 64d)
	private byte cswKey = (byte)0x00;  // upper 4 bits of the first CSW byte
	private byte cswCC = CC0;          // lower 2 bits of the first CSW byte
	private int cswCCwAddress = 0;     // lower 24 bits of the first CSW word (second to fourth byte)
	private byte cswUnitStatus = 0;    // fifth byte of the CSW
	private byte cswChannelStatus = 0; // sixth byte of the CSW
	private int cswCount = 0;          // last 2 bytes of the CSW
	
	/**
	 * Return the CC associated with the last device operation.
	 *  
	 * @return the current CC value for this device's CSW.
	 */
	public byte getCswCC() {
		return this.cswCC;
	}
	
	public int getCswCCwAddress() {
		return cswCCwAddress;
	}

	public byte getCswUnitStatus() {
		return cswUnitStatus;
	}

	public byte getCswChannelStatus() {
		return cswChannelStatus;
	}

	public int getCswCount() {
		return cswCount;
	}

	/**
	 * Store the complete CSW for the device in memory.  
	 */
	public void storeCSW() {
		this.mem[64] = (byte)((this.cswKey & 0xF0) | (cswCC & 0x03));
		this.mem[65] = (byte)((this.cswCCwAddress >> 16) & 0xFF);
		this.mem[66] = (byte)((this.cswCCwAddress >> 8) & 0xFF);
		this.mem[67] = (byte)(this.cswCCwAddress & 0xFF);
		this.mem[68] = this.cswUnitStatus;
		this.mem[69] = this.cswChannelStatus;
		this.mem[70] = (byte)((this.cswCount >> 8) & 0xFF);
		this.mem[71] = (byte)(this.cswCount & 0xFF);
	}
	
	/**
	 * Store a CSW for an asynchronous Attention interrupt.
	 */
	public void storeAttentionPSW() {
		this.mem[64] = 0;
		this.mem[65] = 0;
		this.mem[66] = 0;
		this.mem[67] = 0;
		this.mem[68] = (byte)0x80; // Attention bit
		this.mem[69] = 0;
		this.mem[70] = 0;
		this.mem[71] = 0;
	}
	
	/**
	 * Store the status portion of the CSW for the device in memory.  
	 */
	public void storeCSWStatus() {
		this.mem[68] = this.cswUnitStatus;
		this.mem[69] = this.cswChannelStatus;
	}
	
	/** Process the CCW resp. CCW-chain specified by the CAW location for the
	 *  device of this handler.
	 * 
	 * @return true if the CCW(-chain) was successfully completed resp.
	 *   false if some problem occured.
	 */
	public boolean processCAW() {
		// process the CCW(s) at the memory channel address word (CAW)
		int caw
			= ((this.mem[73] & 0xFF) << 16)
			| ((this.mem[74] & 0xFF) << 8)
			| (this.mem[75] & 0xFF);
		byte protectionKey = (byte)(this.mem[72] & 0xF0);
		return this.processFromAddress(caw, protectionKey);
	}
	
	public boolean processFromBytes(byte[] zeBytes, int from) {
		byte[] memOrig = this.mem;
		try {
			this.mem = zeBytes;
			return this.processFromAddress(from, (byte)0);
		} finally {
			this.mem = memOrig;
		}
	}
	
	/** Process the CCW resp. CCW-chain specified by the CAW location for the
	 *  device of this handler.
	 * 
	 * @param addr address of the (first) CCW to process with the device
	 * @param protectionKey the protection key to use
	 * @return true if the CCW(-chain) was successfully completed resp.
	 *   false if some problem occurred.
	 */
	public boolean processFromAddress(int addr, byte protectionKey) {
		// the same single device may one used by one VM at a time
		synchronized(this.devUnit) {
			// reset pending interrupt
			this.enqueueCompletionInterrupt = false;
			// TODO: remove this handler from the CPU's interrupt queue if already enqueued
			
			//System.out.printf("       --- Begin of CCW interpetation for DEV %03X\n", this.devCuu);
			
			this.eventLogger.logLine(".. Begin of CCW interpetation for DEV %03X", this.devCuu);
			
			// initialize the interpretation state
			this.cswKey = protectionKey;
			this.cswCCwAddress = addr;
			this.cswCC = CC0;
			this.cswUnitStatus = 0;
			this.cswChannelStatus = 0;
			this.cswCount = 0;
			this.devUnit.resetState();
			
			// interpret the CCW-chain
			int unitStatus = 0;
			boolean executeCommand = this.resolveCcw(addr, true);
			while (executeCommand) {
				// as the CCW command is about to be executed, the CCW was plausible enough, so:
				// - the SIO(F) goes beyond the initial check and accesses the device
				// - an interrupt is required to signal the end of the total operation 
				this.enqueueCompletionInterrupt = true;
				
				// check the command type and invoke the device accordingly
				// (invalid command and transfer-in-channel are handled by resolveCcw())
				int totalDataLength = this.computeChainedDataLen(this.currCcwAddress);
				int cmd = this.currCommand & 0xFF;
				if ((cmd & 0x0F) == 0x04) {
					// sense
					this.transferDeviceToMemory = true;
					unitStatus = this.devUnit.sense(cmd, totalDataLength, this);
				} else if ((cmd & 0x0F) == 0x0C) {
					// read backward
					unitStatus = iDeviceStatus.UNIT_CHECK;
					this.cswChannelStatus = iDeviceChannelStatus.PROGRAM_CHECK;
					break;
				} else if ((cmd & 0x03) == 0x01) {
					// write
					if (this.currCD || this.currIDA || this.currSKIP) {
						this.transferDeviceToMemory = false;
						unitStatus = this.devUnit.write(cmd, totalDataLength, this);
					} else {
						unitStatus = this.devUnit.write(cmd, totalDataLength,
										(this.currDataLen > 12)
										? this.memoryToDeviceBlockWise.reInit(this.mem, this.currDataAddr, this.currDataLen)
										: this.memoryToDeviceByteWise.reInit(this.mem, this.currDataAddr, this.currDataLen));
					}
				} else if ((cmd & 0x03) == 0x02) {
					// read
					if (this.currCD || this.currIDA || this.currSKIP) {
						this.transferDeviceToMemory = true;
						unitStatus = this.devUnit.read(cmd, totalDataLength, this);
					} else {
						unitStatus = this.devUnit.read(cmd, totalDataLength,
								(this.currDataLen > 12)
								? this.deviceToMemoryBlockWise.reInit(this.mem, this.currDataAddr, this.currDataLen)
								: this.deviceToMemoryByteWise.reInit(this.mem, this.currDataAddr, this.currDataLen));
					}
				} else if ((cmd & 0x03) == 0x03) {
					// control
					if (this.currCD || this.currIDA || this.currSKIP) {
						this.transferDeviceToMemory = false;
						unitStatus = this.devUnit.control(cmd, totalDataLength, this);
					} else {
						unitStatus = this.devUnit.control(cmd, totalDataLength,
										(this.currDataLen > 12)
										? this.memoryToDeviceBlockWise.reInit(this.mem, this.currDataAddr, this.currDataLen)
										: this.memoryToDeviceByteWise.reInit(this.mem, this.currDataAddr, this.currDataLen));
					}
				}
				
				// check the resulting status
				if ((unitStatus & iDeviceStatus.INCORRECT_LENGTH_IS_OK) != 0
						&& (this.cswChannelStatus & iDeviceChannelStatus.INCORRECT_LENGTH) != 0) {
					this.cswChannelStatus ^= iDeviceChannelStatus.INCORRECT_LENGTH;
				}
				unitStatus &= 0xFF; // reduce to relevant status bits
				if ((unitStatus & iDeviceStatus.UNIT_CHECK) != 0
						|| (unitStatus & iDeviceStatus.UNIT_EXCEPTION) != 0) {
					break; // end chain processing if there was a problem with the command
				}
				if ((unitStatus & iDeviceStatus.STATUS_MODIFIER) != 0
						&& (unitStatus & iDeviceStatus.DEVICE_END) != 0
						&& this.currCC) {
					this.cswCCwAddress += 8; // normal sequence of commands must be modified: skip one CCW
				}
				unitStatus = 0; // clear status to OK in case of command chain end 
				
				// check if a next CCW must be processed
				executeCommand = this.currCC && this.resolveCcw(this.cswCCwAddress, true);
			}
			
			// set final state
			this.cswUnitStatus |= unitStatus;
			
			// check for a problem during CCW chain execution
			boolean result = false;
			if (this.cswChannelStatus == iDeviceChannelStatus.INCORRECT_LENGTH && this.cswUnitStatus == 0) {
				// almost OK: only incorrect length => signal "normal" end of CCW chain: channel is done, but device told a problem  
				this.cswUnitStatus |= iDeviceStatus.CHANNEL_END;
				result = true; // success..
			} else if (this.cswChannelStatus != 0 || this.cswUnitStatus != 0) {
				// there was a problem 
				if ((this.cswUnitStatus & iDeviceStatus.UNIT_EXCEPTION) != 0
					|| (this.cswChannelStatus & iDeviceChannelStatus.PROGRAM_CHECK) != 0) {
					this.cswCC = CC1; // CSW stored (well: must be "stored" by our caller) 
				} else {
					// must be unit check or channel check...
					this.cswCC = CC3; // not operational
				}
				this.cswUnitStatus |= iDeviceStatus.CHANNEL_END;
				result = false; // failed...
			} else {
				// so everything was OK... 
				this.cswUnitStatus |= iDeviceStatus.DEVICE_END | iDeviceStatus.CHANNEL_END;
				result = true; // success..
			}
			
			this.eventLogger.logLine(".. End of CCW interpetation for DEV %03X cswCC=%d (%s):%s%s%s%s%s%s%s%s%s",
					this.devCuu, 
					this.cswCC,
					(result) ? "OK" : "FAIL",
					(this.cswUnitStatus == 0 && this.cswChannelStatus == 0) ? " OK" : "",
					((this.cswUnitStatus & iDeviceStatus.ATTENTION) != 0) ? " Attention" : "",
					((this.cswUnitStatus & iDeviceStatus.STATUS_MODIFIER) != 0) ? " StatusModifier" : "",
					((this.cswUnitStatus & iDeviceStatus.CHANNEL_END) != 0) ? " ChannelEnd" : "",
					((this.cswUnitStatus & iDeviceStatus.DEVICE_END) != 0) ? " DeviceEnd" : "",
					((this.cswUnitStatus & iDeviceStatus.UNIT_CHECK) != 0) ? " UnitCheck" : "",
					((this.cswUnitStatus & iDeviceStatus.UNIT_EXCEPTION) != 0) ? " UnitException" : "",
					((this.cswChannelStatus & iDeviceChannelStatus.INCORRECT_LENGTH) != 0) ? " IncorrectLength" : "",
					((this.cswChannelStatus & iDeviceChannelStatus.PROGRAM_CHECK) != 0) ? " ProgramCheck" : ""
					);
			return result;
		}
	}

	// fields and flags of the CCW currently active
	private int currCcwAddress = 0; // setting this also sets cswCcwAddress (as curr + 8)
	private boolean currCD = false;
	private boolean currCC = false;
	private boolean currSLI = false;
	private boolean currSKIP = false;
	private boolean currIDA = false;
	private byte currCommand = 0;
	private int currDataLen = 0;
	private int currDataAddr = 0; // data address value (directly from the CCW)
	private int currDataMemAddr = 0; // address of the next memory byte to transfer (depends on IDA-flag)
	
	/** Switch our internal state to the CCW at the given address, possibly traversing
	 * a transfer-in-channel CCW.  
	 * 
	 * @param ccwAddr the address of the CCW to switch to
	 * @param allowTIC is a transfer-in-channel acceptable or (if false) generate a PROGRAM_CHECK if#
	 *   the new CCW is a transfer-in-channel
	 * @return was the switch successful? (if false: cswChannelStatus became != 0)
	 */
	private boolean resolveCcw(int ccwAddr, boolean allowTIC) {
		// is the channel process ready for processing (or did we already have a problem)? 
		if (this.cswChannelStatus != 0) { return false; } // we should not have been called!
		
		// check for: invalid CAW format (required zero bits not zero)
		if ((ccwAddr & 0x07) != 0) {
			this.eventLogger.logLine(".. resolveCcw(0x%06X,%s) :: invalid CAW format (required zero bits not zero)", ccwAddr, (allowTIC) ? "true" : "false");
			this.cswChannelStatus = iDeviceChannelStatus.PROGRAM_CHECK;
			return false;
		}
		
		/* this check must be deactivated, as these 2 lower bits of DIAG-x58(0x19) CCWs
		 * are often set by EDIT or FLIST
		 * 
		// check for: invalid CCW format (required zero bits not zero)
		if ((this.mem[ccwAddr + 4] & 0x03) != 0) {
			this.eventLogger.logLine(".. resolveCcw(0x%06X,%s) :: invalid CCW format (required zero bits not zero)", ccwAddr, (allowTIC) ? "true" : "false");
			this.cswChannelStatus = IDeviceChannelStatus.PROGRAM_CHECK;
			return false;
		}*/
		
		// the I/O command to execute
		byte command = this.mem[ccwAddr];
		
		// check for: invalid command code (4 low-order bits are zeroes)
		// (but not for ChainData)
		if (!this.currCD && (command & 0x0F) == 0) {
			this.eventLogger.logLine(".. resolveCcw(0x%06X,%s) :: invalid command code (4 low-order bits are zeroes)", ccwAddr, (allowTIC) ? "true" : "false");
			this.cswChannelStatus = iDeviceChannelStatus.PROGRAM_CHECK;
			return false;
		}
		
		// the data address for the I/O command
		int dataAddress
				= ((this.mem[ccwAddr + 1] & 0xFF) << 16)
				| ((this.mem[ccwAddr + 2] & 0xFF) << 8)
				| (this.mem[ccwAddr + 3] & 0xFF);
		
		// if transfer-in-channel: jump to the new CCW chain
		if ((command & 0x0F) == 0x08) {
			// check for: invalid sequence (2 consecutive transfer-in-channel)
			if (allowTIC) {
				return this.resolveCcw(dataAddress, false);
			} else {
				this.eventLogger.logLine(".. resolveCcw(0x%06X,%s) :: TIC to TIC sequence", ccwAddr, (allowTIC) ? "true" : "false");
				this.cswChannelStatus = iDeviceChannelStatus.PROGRAM_CHECK;
				return false;
			}
		}
		
		// the number of bytes to transfer 
		int count = ((this.mem[ccwAddr + 6] & 0xFF) << 8) | (this.mem[ccwAddr + 7] & 0xFF);
		
		// check for: invalid count (count = 0)
		if (count == 0) {
			this.eventLogger.logLine(".. resolveCcw(0x%06X,%s) :: invalid count (count = 0)", ccwAddr, (allowTIC) ? "true" : "false");
			this.cswChannelStatus = iDeviceChannelStatus.PROGRAM_CHECK;
			return false;
		}
		
		// if indirect addressing check for:
		// - invalid IDAW address specification (first IDAW not on a word boundary)
		// - invalid IDAW specification (bits 0-7 not zero)
		boolean ida = (this.mem[ccwAddr + 4] & 0x04) != 0;
		if (ida && ( (dataAddress & 0x03) != 0 || (dataAddress & 0xFF000000) != 0)) {
			this.eventLogger.logLine(".. resolveCcw(0x%06X,%s) :: invalid IDAW address specification", ccwAddr, (allowTIC) ? "true" : "false");
			this.cswChannelStatus = iDeviceChannelStatus.PROGRAM_CHECK;
			return false;
		}
		
		// set the relevant CSW fields
		this.cswCCwAddress = ccwAddr + 8; // as specified: this points to the "next" CCW
		this.cswCount = count; // before any transfer: the "unused" data area length is the complete CCW data length
		
		// extract the fields and flags of the channel command word
		if (!this.currCD) { this.currCommand = command; } // ChainData cannot not change the running device command 
		this.currCcwAddress = ccwAddr;
		this.currCD = (this.mem[ccwAddr + 4] & 0x80) != 0;
		this.currCC = (this.mem[ccwAddr + 4] & 0x40) != 0;
		this.currSLI = (this.mem[ccwAddr + 4] & 0x20) != 0;
		this.currSKIP = (this.mem[ccwAddr + 4] & 0x10) != 0;
		this.currIDA = ida;
		this.currDataAddr = dataAddress;
		this.currDataLen = count;
		if (ida) {
			if (this.mem[dataAddress] != 0) {
				this.eventLogger.logLine(".. resolveCcw(0x%06X,%s) :: invalid IDA segments address specification", ccwAddr, (allowTIC) ? "true" : "false");
				this.cswChannelStatus = iDeviceChannelStatus.PROGRAM_CHECK;
				return false;
			}
			this.currDataMemAddr
				= ((this.mem[dataAddress + 1] & 0xFF) << 16)
				| ((this.mem[dataAddress + 2] & 0xFF) << 8)
				| (this.mem[dataAddress + 3] & 0xFF);
		} else {
			this.currDataMemAddr = dataAddress;
		}
		
		this.eventLogger.logLine(".. CCW @ 0x%06X :: 0x %02X%02X%02X%02X %02X%02X%02X%02X = cmd: 0x%02X ; data: 0x%06X ; len: %-5d ; flags: 0x%2X =%s%s%s%s%s",
				ccwAddr,
				this.mem[ccwAddr], this.mem[ccwAddr + 1], this.mem[ccwAddr + 2], this.mem[ccwAddr + 3],
				this.mem[ccwAddr + 4], this.mem[ccwAddr + 5], this.mem[ccwAddr + 6], this.mem[ccwAddr + 7],
				command, dataAddress, count, this.mem[ccwAddr + 4],
				(this.currCD) ? " CD" : "",
				(this.currCC) ? " CC" : "",
				(this.currSLI) ? " SLI" : "",
				(this.currSKIP) ? " SKIP" : "",
				(this.currIDA) ? " IDA" : "");
		return true;
	}
	
	// compute the memory data length to transfer starting with the CCW
	// at the given address, cumulated along data chaining from this CCW,
	// possibly traversing transfer-in-channel CCWs.
	private int computeChainedDataLen(int zeCcwAddr) {
		int zeLen = 0;
		
		while(true) {
			// add the count of this CCW
			zeLen += ((this.mem[zeCcwAddr + 6] & 0xFF) << 8) | (this.mem[zeCcwAddr + 7] & 0xFF);
			
			// check Chain Data flag for continuation
			if ((this.mem[zeCcwAddr + 4] & 0x80) == 0) {
				return zeLen;
			}
			
			// proceed with next CCW
			zeCcwAddr += 8;
			if ((this.mem[zeCcwAddr] & 0x0F) == 0x08) {
				// transfer-in-channel => continue with CCW at dataAddress
				// attention: a TIC to TIC channel program error is not checked and ignored here
				zeCcwAddr  = ((this.mem[zeCcwAddr + 1] & 0xFF) << 16)
				       | ((this.mem[zeCcwAddr + 2] & 0xFF) << 8)
				       | (this.mem[zeCcwAddr + 3] & 0xFF);
			}
		}
	}
	
	// the channel transfer direction: device->memory(true) or memory->device(false)
	private boolean transferDeviceToMemory = false;
	
	// implementation of iDeviceIO
	@Override
	public int transfer(byte[] devMemory, int offset, int length) {
		
		while(this.currDataLen > 0 && length > 0 && this.cswChannelStatus == 0) {
			
			if (length < 8 || this.currIDA) {
				// transfer the next byte between memory and device
				if (this.transferDeviceToMemory) {
					if (!this.currSKIP) {
						this.mem[this.currDataMemAddr++] = devMemory[offset++];
					} else {
						offset++;
					}
				} else {
					devMemory[offset++] = this.mem[this.currDataMemAddr++];
				}
				this.currDataLen--;
				length--;
			} else {
				// use faster block based transfer 
				int chunkSize = (length <= this.currDataLen) ? length : this.currDataLen;
				if (this.transferDeviceToMemory) {
					if (!this.currSKIP) {
						System.arraycopy(devMemory, offset, this.mem, this.currDataMemAddr, chunkSize);
						this.currDataMemAddr += chunkSize;
					}
				} else {
					System.arraycopy(this.mem, this.currDataMemAddr, devMemory, offset, chunkSize);
					this.currDataMemAddr += chunkSize;
				}
				offset += chunkSize;
				length -= chunkSize;
				this.currDataLen -= chunkSize;
			}
			
			// handle memory position
			if (this.currDataLen == 0) {
				// memory area of this command is exhausted:
				// if data chaining => proceed with data field/length of the next CCW
				if (this.currCD) { this.resolveCcw(this.cswCCwAddress, true); }
			} else if (this.currIDA && !this.currSKIP && (this.currDataMemAddr & 0x000007FF) == 0) {
				// we are doing indirect addressing and the memory address just crossed a page boundary
				// => switch to the next indirect addressing word
				this.currDataAddr += 4;
				if (this.mem[this.currDataAddr] != 0) {
					this.cswChannelStatus = iDeviceChannelStatus.PROGRAM_CHECK;
				}
				this.currDataMemAddr
					= ((this.mem[this.currDataAddr + 1] & 0xFF) << 16)
					| ((this.mem[this.currDataAddr + 2] & 0xFF) << 8)
					| (this.mem[this.currDataAddr + 3] & 0xFF);
			}
		}
		
		// save the unused data count of the current CCW into the CSW 
		this.cswCount = this.currDataLen;
		
		// check for invalid length indication: here we set the basic state after the data
		// transfer, which can be overruled by the device which possibly knows better
		// (e.g. by filling a disk block with zeroes up to the device buffer length)
		if (!this.currSLI && (this.currDataLen > 0 || length > 0)) {
			this.cswChannelStatus |= iDeviceChannelStatus.INCORRECT_LENGTH;
		}
		
		// compute the return value representing the transfer outcome
		if (this.cswChannelStatus != 0) {
			return -length;
		} else if (this.currDataLen == 0) {
			return -length;
		} else if (length == 0) {
			int remainingMemLength = this.currDataLen;
			while(this.currCD && this.resolveCcw(this.cswCCwAddress, true)) {
				remainingMemLength += this.currDataLen;
			}
			return remainingMemLength; 
		}
		
		return -length; // we shouldn't logically get here ... but we must keep the compiler happy 
	}
	
	private DeviceToMemoryByteWise deviceToMemoryByteWise = new DeviceToMemoryByteWise();
	
	private class DeviceToMemoryByteWise implements iDeviceIO {
		
		private byte[] theMem = null;
		private int theDataMemAddr = 0;
		private int theDataLen = 0;
		
		public iDeviceIO reInit(byte[] m, int addr, int len) {
			this.theMem = m;
			this.theDataMemAddr = addr;
			this.theDataLen = len;
			return this;
		}

		@Override
		public int transfer(byte[] devMemory, int offset, int length) {
			// transfer data
			while(this.theDataLen > 0 && length > 0) {
				this.theMem[this.theDataMemAddr++] = devMemory[offset++];
				this.theDataLen--;
				length--;
			}
			
			// save the unused data count of the current CCW into the CSW 
			cswCount = this.theDataLen;

			
			// check for invalid length indication: here we set the basic state after the data
			// transfer, which can be overruled by the device which possibly knows better
			// (e.g. by filling a disk block with zeroes up to the device buffer length)
			if (!currSLI && (this.theDataLen > 0 || length > 0)) {
				cswChannelStatus |= iDeviceChannelStatus.INCORRECT_LENGTH;
			}
			
			// compute the return value representing the transfer outcome
			if (cswChannelStatus != 0) {
				return -length;
			} else if (this.theDataLen == 0) {
				return -length;
			} else if (length == 0) {
				return this.theDataLen;
			}
		
		return -length; // we shouldn't logically get here ... but we must keep the compiler happy 
		}
	}
	
	private DeviceToMemoryBlockWise deviceToMemoryBlockWise = new DeviceToMemoryBlockWise();
	
	private class DeviceToMemoryBlockWise implements iDeviceIO {
		
		private byte[] theMem = null;
		private int theDataMemAddr = 0;
		private int theDataLen = 0;
		
		public iDeviceIO reInit(byte[] m, int addr, int len) {
			this.theMem = m;
			this.theDataMemAddr = addr;
			this.theDataLen = len;
			return this;
		}

		@Override
		public int transfer(byte[] devMemory, int offset, int length) {
			// transfer data
			int chunkSize = (length <= this.theDataLen) ? length : this.theDataLen;
			System.arraycopy(devMemory, offset, this.theMem, this.theDataMemAddr, chunkSize);
			this.theDataMemAddr += chunkSize;
			offset += chunkSize;
			length -= chunkSize;
			this.theDataLen -= chunkSize;
			
			// save the unused data count of the current CCW into the CSW 
			cswCount = this.theDataLen;

			
			// check for invalid length indication: here we set the basic state after the data
			// transfer, which can be overruled by the device which possibly knows better
			// (e.g. by filling a disk block with zeroes up to the device buffer length)
			if (!currSLI && (this.theDataLen > 0 || length > 0)) {
				cswChannelStatus |= iDeviceChannelStatus.INCORRECT_LENGTH;
			}
			
			// compute the return value representing the transfer outcome
			if (cswChannelStatus != 0) {
				return -length;
			} else if (this.theDataLen == 0) {
				return -length;
			} else if (length == 0) {
				return this.theDataLen;
			}
		
		return -length; // we shouldn't logically get here ... but we must keep the compiler happy 
		}
	}
	
	private MemoryToDeviceByteWise memoryToDeviceByteWise = new MemoryToDeviceByteWise();
	
	private class MemoryToDeviceByteWise implements iDeviceIO {
		
		private byte[] theMem = null;
		private int theDataMemAddr = 0;
		private int theDataLen = 0;
		
		public iDeviceIO reInit(byte[] m, int addr, int len) {
			this.theMem = m;
			this.theDataMemAddr = addr;
			this.theDataLen = len;
			return this;
		}

		@Override
		public int transfer(byte[] devMemory, int offset, int length) {
			// transfer data
			while(this.theDataLen > 0 && length > 0) {
				devMemory[offset++] = this.theMem[this.theDataMemAddr++];
				this.theDataLen--;
				length--;
			}
			
			// save the unused data count of the current CCW into the CSW 
			cswCount = this.theDataLen;

			
			// check for invalid length indication: here we set the basic state after the data
			// transfer, which can be overruled by the device which possibly knows better
			// (e.g. by filling a disk block with zeroes up to the device buffer length)
			if (!currSLI && (this.theDataLen > 0 || length > 0)) {
				cswChannelStatus |= iDeviceChannelStatus.INCORRECT_LENGTH;
			}
			
			// compute the return value representing the transfer outcome
			if (cswChannelStatus != 0) {
				return -length;
			} else if (this.theDataLen == 0) {
				return -length;
			} else if (length == 0) {
				return this.theDataLen;
			}
		
		return -length; // we shouldn't logically get here ... but we must keep the compiler happy 
		}
	}
	
	private MemoryToDeviceBlockWise memoryToDeviceBlockWise = new MemoryToDeviceBlockWise();
	
	private class MemoryToDeviceBlockWise implements iDeviceIO {
		
		private byte[] theMem = null;
		private int theDataMemAddr = 0;
		private int theDataLen = 0;
		
		public iDeviceIO reInit(byte[] m, int addr, int len) {
			this.theMem = m;
			this.theDataMemAddr = addr;
			this.theDataLen = len;
			return this;
		}

		@Override
		public int transfer(byte[] devMemory, int offset, int length) {
			// transfer data
			int chunkSize = (length <= this.theDataLen) ? length : this.theDataLen;
			System.arraycopy(this.theMem, this.theDataMemAddr, devMemory, offset, chunkSize);
			this.theDataMemAddr += chunkSize;
			offset += chunkSize;
			length -= chunkSize;
			this.theDataLen -= chunkSize;
			
			// save the unused data count of the current CCW into the CSW 
			cswCount = this.theDataLen;

			
			// check for invalid length indication: here we set the basic state after the data
			// transfer, which can be overruled by the device which possibly knows better
			// (e.g. by filling a disk block with zeroes up to the device buffer length)
			if (!currSLI && (this.theDataLen > 0 || length > 0)) {
				cswChannelStatus |= iDeviceChannelStatus.INCORRECT_LENGTH;
			}
			
			// compute the return value representing the transfer outcome
			if (cswChannelStatus != 0) {
				return -length;
			} else if (this.theDataLen == 0) {
				return -length;
			} else if (length == 0) {
				return this.theDataLen;
			}
		
		return -length; // we shouldn't logically get here ... but we must keep the compiler happy 
		}
	}
	
}
