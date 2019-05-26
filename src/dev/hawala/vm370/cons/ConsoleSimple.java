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

import java.util.ArrayList;

import dev.hawala.vm370.ebcdic.EbcdicHandler;
import dev.hawala.vm370.vm.cp.iUserConsole;
import dev.hawala.vm370.vm.cp.iUserConsole.eUserInputState;
import dev.hawala.vm370.vm.device.iDevice;
import dev.hawala.vm370.vm.device.iDeviceIO;
import dev.hawala.vm370.vm.device.iDeviceStatus;

/**
 * Implementation of a 3215 device.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public class ConsoleSimple implements iDevice {
	
	// the related user console connected to the terminal emulator
	// and which in fact created this I/O device
	private final iUserConsole userConsole;
	
	// the "real" terminal line the terminal is connected to
	protected final int pseudoLine;
	
	// the ebcdic line input buffer
	private final EbcdicHandler inBuffer = new EbcdicHandler(256);
	
	// the input lines ready to be returned for SIO read operations
	private final ArrayList<EbcdicHandler> pendingInputs = new ArrayList<EbcdicHandler>();
	
	// pending attention interrupts
	private int pendingAsyncInterrupts = 0;
	
	// buffer for write output to the terminal
	private final byte[] outBuffer = new byte[256];
	private final EbcdicHandler outEbcdic = new EbcdicHandler();
	
	// is this VM being shut down? => reject all I/O requests?
	private volatile boolean doShutdown = false;
	
	// logging info
	private final long startupTS = System.currentTimeMillis();
	private long ts() { return System.currentTimeMillis() - this.startupTS; }
	
	// throttling control for multiple inputs on the same line ("CMD1#CMD2#CMD3")
	// => prevent that interrupts for the same device (this one) are generated
	//    so fast that CMS replaces the last unprocessed input with the new one,
	//    so only the last input is effectively seen and processed
	private long nextAsyncInterruptAfter = System.currentTimeMillis();
	private static long INTR_DELAY_INPUT_TO_INTR = 100; // 100 ms
	private static long INTR_DELAY_INTR_TO_INTR = 300; // 300 ms
	
	// constructor
	public ConsoleSimple(iUserConsole userConsole, int pseudoLine) {
		this.userConsole = userConsole;
		this.pseudoLine = pseudoLine;
	}
	
	// start rejecting I/O operations
	public void shutdown() {
		this.doShutdown = true;
	}

	// check for attention interrupt
	// see: iDevice
	@Override
	public boolean hasPendingAsyncInterrupt() {
		synchronized(this.pendingInputs) {
			while(this.userConsole.getEnqueuedUserInput() > 0) {
				EbcdicHandler nextLine = new EbcdicHandler(256);
				this.userConsole.getNextUserInputForVMREAD(nextLine);
				this.pendingInputs.add(nextLine);
				this.pendingAsyncInterrupts++;
				// DBG System.out.printf("%7d ++ ConsoleSimple: added pending async interrupt, count now: %d\n", this.ts(), this.pendingAsyncInterrupts);
			}
		}
		if (this.pendingAsyncInterrupts < 1 || System.currentTimeMillis() < this.nextAsyncInterruptAfter) { return false; }
		return true;
	}

	// remove one pending attention interrupt
	// see: iDevice
	@Override
	public void consumeNextAsyncInterrupt() {
		this.nextAsyncInterruptAfter = System.currentTimeMillis() + INTR_DELAY_INTR_TO_INTR;
		this.pendingAsyncInterrupts--;
		// DBG System.out.printf("%7d -- ConsoleSimple: consumed pending Attention async interrupt, count now: %d\n", this.ts(), this.pendingAsyncInterrupts);
	}
	
	// enqueue an attention interrupt
	// see: iDevice
	@Override
	public void doAttentionInterrupt() {
		this.pendingAsyncInterrupts++;
		// DBG System.out.printf("%7d ++ ConsoleSimple: added Attention async interrupt, count now: %d\n", this.ts(), this.pendingAsyncInterrupts);
	}

	// prepare processing of a new CCW-chain
	// see: iDevice
	@Override
	public void resetState() {
		// nothing to reset (currently)
	}

	// perform a control I/O operation
	// see: iDevice
	@Override
	public int control(int opcode, int dataLength, iDeviceIO memSource) {
		switch(opcode)
		{		
		case ConsoleCommandCodes.ControlNOOP:
			return iDeviceStatus.DEVICE_END;
		
		default:
			return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
		}
	}

	// perform a read I/O operation
	// see: iDevice
	@Override
	public int read(int opcode, int dataLength, iDeviceIO memTarget) {
		// DBG System.out.printf("%7d ** ConsoleSimple.read() : opcode = 0x%02X\n", this.ts(), opcode);
		if (opcode != ConsoleCommandCodes.Read) {
			return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
		}
		
		if (this.doShutdown) {
			return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
		}
		
		EbcdicHandler src = null;
		
		synchronized(this.pendingInputs) {
			if (this.pendingInputs.size() > 0) {
				src = this.pendingInputs.get(0);
				this.pendingInputs.remove(0);
				// DBG System.out.printf("%7d ** ConsoleSimple.read() : used pendingInputs[0]\n", this.ts());
			}
		}
		if (src == null) {
			// DBG System.out.printf("%7d ** ConsoleSimple.read() : explicitely prompting user\n", this.ts());
			eUserInputState outcome = this.userConsole.getNextUserInputForVMREAD(this.inBuffer);
			while(outcome != eUserInputState.ReadFromBuffer && outcome != eUserInputState.ReadFromPrompt) {
				if (this.doShutdown) {
					return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
				}
				try { Thread.sleep(10); } catch(InterruptedException e) { }
				outcome = this.userConsole.getNextUserInputForVMREAD(this.inBuffer);
			}
			src = this.inBuffer;
		}
		
		// DBG System.out.printf("%7d ** ConsoleSimple.read() : inputline => '%s'\n", this.ts(), src.toString());
		this.nextAsyncInterruptAfter = System.currentTimeMillis() + INTR_DELAY_INPUT_TO_INTR;
		memTarget.transfer(src.getRawBytes(), 0, src.getLength());
		return iDeviceStatus.DEVICE_END;
	}

	// perform a write I/O operation
	// see: iDevice
	@Override
	public int write(int opcode, int dataLength, iDeviceIO memSource) {
		if (this.doShutdown) {
			return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
		}
		int transferCount = (dataLength > this.outBuffer.length) ? this.outBuffer.length : dataLength;
		memSource.transfer(this.outBuffer, 0, transferCount);
		this.outEbcdic.reset().appendEbcdic(this.outBuffer, 0, transferCount);
		switch(opcode)
		{
		case ConsoleCommandCodes.WriteNoCR:
			this.userConsole.writeNoCR(this.outEbcdic);
			return iDeviceStatus.DEVICE_END;
			
		case ConsoleCommandCodes.WriteAddCR:
			this.userConsole.writeAddCR(this.outEbcdic);
			return iDeviceStatus.DEVICE_END;
			
		default:
			return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
		}		
	}

	// perform a sense I/O operation
	// see: iDevice
	@Override
	public int sense(int opcode, int dataLength, iDeviceIO memTarget) {
		return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
	}

	// get the virtual device information for DIAG-x24
	// see: iDevice
	@Override
	public int getVDevInfo() {
		// this is a virtual 3215 non-graphic terminal device, no device status, no device flags 
		return 0x80000000;
	}

	// get the real device information for DIAG-x24
	// see: iDevice
	@Override
	public int getRDevInfo() {
		// this is a simulated 3215 non-graphic terminal device, default model with 130 characters line length
		return 0x80000082;
	}

	// get a single sense byte
	// see: iDevice
	@Override
	public byte getSenseByte(int index) {
		return (byte)0;
	}
	
	// get the printable device type name
	// see: iDevice
	@Override
	public String getCpDeviceTypeName() {
		return "CONS";
	}
	
	// get the device information for QUERY VIRTUAL
	// see: iDevice
	@Override
	public String getCpQueryStatusLine(int asCuu) {
		return String.format(
				"CONS %03X ON CONS %03X",
				asCuu,
				this.pseudoLine);
	}

}
