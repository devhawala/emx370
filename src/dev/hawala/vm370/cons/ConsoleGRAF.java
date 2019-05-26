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

import java.io.IOException;

import dev.hawala.vm370.ebcdic.EbcdicHandler;
import dev.hawala.vm370.mecaff.ByteBuffer;
import dev.hawala.vm370.mecaff.IVm3270ConsoleCompletedSink;
import dev.hawala.vm370.mecaff.TerminalTypeNegotiator;
import dev.hawala.vm370.mecaff.Vm3270Console;
import dev.hawala.vm370.stream3270.CommandCode3270;
import dev.hawala.vm370.stream3270.OrderCode3270;
import dev.hawala.vm370.vm.cp.iUserConsole;
import dev.hawala.vm370.vm.device.iDeviceIO;
import dev.hawala.vm370.vm.device.iDeviceStatus;
import dev.hawala.vm370.vm.machine.Cpu370Bc;

/**
 * Implementation of a 3270 device.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public class ConsoleGRAF extends ConsoleSimple {
	
	/**
	 * Interface-compatible wrapper around a iDeviceIO-(Memory=>Device)-object
	 * filtering out 3270 orders which would corrupt the screen layout, letting
	 * pass harmless orders (like setting color or highlight)
	 */
	private static class Fullscreen3270OrdersFilter implements iDeviceIO {
		
		// the currently wrapped iDeviceIO
		private iDeviceIO source;
		
		// the the iDeviceIO to be used now/next
		public iDeviceIO useSource(iDeviceIO src) {
			this.source = src;
			return this;
		}

		// filter orders in data bytes to be displayed
		@Override
		public int transfer(byte[] devMemory, int offset, int length) {
			int result = this.source.transfer(devMemory, offset, length);
			int limit = offset + ((result >= 0) ? length : length + result);
			
			// replace all "evil" fullscreen orders by blanks or reset bits
			int from = offset;
			int to = offset;
			while (from < limit) {
				byte b = devMemory[from++];
				if (b == OrderCode3270.SBA.getCode()) {
					// SBA - Set Buffer Address
					// skip SBA and 2 address bytes
					System.err.println("..... Display Data: had SBA");
					from += 2;
				} else if (b == OrderCode3270.RA.getCode()) {
					// RA - repeat to Address
					// skip RA and 2 address bytes and the byte to be repeated
					System.err.println("..... Display Data: had RA");
					from += 3;
				} else if (b == OrderCode3270.EUA.getCode()) {
					// EUA - Erase Unprotected to Address
					// skip EUA and 2 address bytes
					System.err.println("..... Display Data: had EUA");
					from += 2;
				} else if (b == OrderCode3270.SF.getCode()) {
					// SF - Start Field
					// keep the command but add protected and remove modified bits
					System.err.println("..... Display Data: had SF");
					devMemory[to++] = b;
					b = devMemory[from++];
					b |= (byte)0x20;
					b &= (byte)0xFE;
					devMemory[to++] = b;
				} else if (b == OrderCode3270.SFE.getCode()) {
					// SFE - Start Field Extended
					// keep SFE and pairs but patch field attributes pair like for SF
					System.err.println("..... Display Data: had SFE");
					devMemory[to++] = b;
					byte pairCount = devMemory[from++];
					devMemory[to++] = pairCount;
					for (int i = 0; i < pairCount; i++) {
						byte what = devMemory[from++];
						devMemory[to++] = what;
						b = devMemory[from++];
						if (what == (byte)0xC0) { // basic field attributes
							// add protected and remove modified bits
							b |= (byte)0x20;
							b &= (byte)0xFE;
						}
						devMemory[to++] = b;
					}
				} else if (b == OrderCode3270.SA.getCode()) {
					// ignored: SA - Set Attribute (not evil)
					System.err.println("..... Display Data: had SA");
					// SA order has 3 bytes (incl. order)
					devMemory[to++] = b;
					devMemory[to++] = devMemory[from++];
					devMemory[to++] = devMemory[from++];
				} else if (b != OrderCode3270.IC.getCode() && b != OrderCode3270.PT.getCode()) {
					// if not a 1-byte order: keep this byte
					devMemory[to++] = b;
				}
				// ignored: MF - Modify field (cannot modify a field, as SBA is filtered out)
				// ignored: SA - Set Attribute (not evil)
			}
			
			// return the adjusted result
			return result + (to - from);
		}
		
	}
	
	// the 3270 order filter object used by this device 
	private Fullscreen3270OrdersFilter orders3270Filter = new Fullscreen3270OrdersFilter();
	
	// the related user console connected to the terminal emulator
	// and which in fact created this I/O device
	private final Vm3270Console console3270;
	
	// the terminal negociator providing us the terminal chararteristics
	private final TerminalTypeNegotiator termTypeNegociator3270;
	
	// the buffer for DIAG-x58 screen display (non-fullscreen)
	private ByteBuffer out3270Buffer = new ByteBuffer(8192, 1024); 
	
	// is the 3270 console in (real) fullscreen mode?
	private boolean isInFullscreenMode = false;
	
	// is there fullscreen input available?
	private volatile boolean hasFullscreenIntrPending = false;
	
	// emulator for the VM/370 layout style screen used for DIAG-x58 screen display (non-fullscreen) 
	private final Screen3270Emulator screen3270;
	private final EbcdicHandler screen3270Input = new EbcdicHandler();
	private volatile boolean emulating3270 = false;
	private boolean clearScreenOnNextDisplay = false;
	
	// the input data from the MECAFF-console
	private ByteBuffer in3270buffer = null;
	private IVm3270ConsoleCompletedSink in3270CompletedCallBack = null;

	// constructor
	public ConsoleGRAF(iUserConsole userConsole, int pseudoLine, Vm3270Console console3270, TerminalTypeNegotiator negociator) {
		super(userConsole, pseudoLine);
		this.console3270 = console3270;
		this.termTypeNegociator3270 = negociator;
		this.screen3270 = new Screen3270Emulator(console3270);
	}
	
	// get the device information for QUERY VIRTUAL
	// see: iDevice
	@Override
	public String getCpQueryStatusLine(int asCuu) {
		return String.format(
				"CONS %03X ON GRAF %03X",
				asCuu,
				this.pseudoLine);
	}
	
	// is the fullscreen input available?
	private boolean havingFullscreenInput() {
		synchronized(this) {
			return this.in3270buffer != null;
		}
	}
	
	// check for attention interrupt
	// see: iDevice
	@Override
	public boolean hasPendingAsyncInterrupt() {
		// if we own the 3270 screen but dont't yet have an input ready to deliver:
		// check if some input arrived since we last checked 
		if (this.isInFullscreenMode && !this.havingFullscreenInput()) {
			try {
				// timeout == 0 => query user input availability status and send input data if available
				this.console3270.readFullScreen(0, 0);
				// if the 3270 console has input, it will send it and this will set this.hasFullscreenIntrPending
			} catch (IOException e) {
				// ignored
			}
		}
		
		// if there is fullscreen data, the response is: yes, there is an async (Attention) interrupt pending
		if (this.hasFullscreenIntrPending) { return true; }
		
		// else: what is the response of the superclass?
		return super.hasPendingAsyncInterrupt();
	}

	// remove one pending attention interrupt
	// see: iDevice
	@Override
	public void consumeNextAsyncInterrupt() {
		if (this.hasFullscreenIntrPending) {
			this.hasFullscreenIntrPending = false;
			// DBG System.out.printf("-- ConsoleGRAF: consumed pending fullscreen async interrupt\n");
			return;
		}
		super.consumeNextAsyncInterrupt();
	}

	// prepare processing of a new CCW-chain
	// see: iDevice
	@Override
	public void resetState() {
		// nothing to reset here (currently), inform our superclass
		super.resetState();
	}
	
	// perform a control I/O operation
	// see: iDevice
	@Override
	public int control(int opcode, int dataLength, iDeviceIO memSource) {
		if (opcode == ConsoleCommandCodes.CheckFullscreenSupport) {
			return iDeviceStatus.DEVICE_END; // yes we can
		}

		// Clear Screen (subfunction for Display Data)?
		// (this will only have an effect on the next real display operation)  
		if (opcode == ConsoleCommandCodes.ClearScreen) {
			this.clearScreenOnNextDisplay = true;
			return iDeviceStatus.DEVICE_END; // done
		}
		
		// Display Data ?
		if ((opcode & 0x07) == 0x07) {
			// display data at line xxxxxB after clearing screen
			int atLine = (opcode >> 3) & 0x1F;
			int startOffset = this.out3270Buffer.clear().reserveSpace(dataLength);
			this.orders3270Filter.useSource(memSource).transfer(this.out3270Buffer.getInternalBuffer(), startOffset, dataLength);
			try {
				this.emulating3270 = this.screen3270.display(this.out3270Buffer, atLine, true);
			} catch(IOException e) {
				this.emulating3270 = false; 
			}
			this.clearScreenOnNextDisplay = false; // as this has already cleared the screen
			if (this.emulating3270) {
				return iDeviceStatus.DEVICE_END;
			}
			return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
		}
		
		// delegate all other operations to the simple console device
		return super.control(opcode, dataLength, memSource);
	}

	// perform a read I/O operation
	// see: iDevice
	@Override
	public int read(int opcode, int dataLength, iDeviceIO memTarget) {
		
		// read after Display Data?
		if (this.emulating3270) {
			try {
				synchronized(this) {
					while (this.screen3270.promptUser()) {
						while(this.in3270buffer == null) {
							this.wait();
						}
						boolean doRepeat = this.screen3270.handleInput(this.in3270buffer, this.screen3270Input);
						this.in3270buffer  = null;
						if (this.in3270CompletedCallBack != null) {
							this.in3270CompletedCallBack.transferCompleted();
							this.in3270CompletedCallBack = null;
						}
						if (!doRepeat) {
							memTarget.transfer(this.screen3270Input.getRawBytes(), 0, this.screen3270Input.getLength());
							this.emulating3270 = false;
							return iDeviceStatus.DEVICE_END;
						}
					}
					// prompting the user failed (= we lost the fullscreen session)
					// => cancel 3270 emulation
					// => proceed with a "normal" read
					this.emulating3270 = false;
					this.console3270.readFullScreen(-42424242, 0); // make sure screen ownership is released
				}
			} catch (Exception e) {
				this.emulating3270 = false;
				return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
			}
		}
		
		// 3215 mode read?
		if (opcode < ConsoleCommandCodes.Limit3215) {
			return super.read(opcode, dataLength, memTarget);
		}
		
		// do some logging
		if (Cpu370Bc.getLiveLogging()) {
			String opName;
			switch(opcode) {
			case ConsoleCommandCodes.Read_FS_RB: opName = "ReadBuffer "; break;
			case ConsoleCommandCodes.Read_FS_RM: opName = "ReadModified"; break;
			default :                            opName = "????????????";
			}
			System.out.printf("         read  -> opcode: %s ; dataLength = %d\n", opName, dataLength);
		}
		
		// fullscreen read (read buffer and read modified cannot be differentiated by MECAFF-console, sorry)
		if (opcode == ConsoleCommandCodes.Read_FS_RB || opcode == ConsoleCommandCodes.Read_FS_RM) {
			if (!this.isInFullscreenMode) {
				return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
			}
			try {
				synchronized(this) {
					while(this.in3270buffer == null) {
						this.wait();
					}
					memTarget.transfer(this.in3270buffer.getInternalBuffer(), 0, this.in3270buffer.getLength());
					this.in3270buffer = null;
					if (this.in3270CompletedCallBack != null) {
						this.in3270CompletedCallBack.transferCompleted();
						this.in3270CompletedCallBack = null;
					}
				}
				this.hasFullscreenIntrPending = false; // reset interrupt, as the input was read
				this.isInFullscreenMode = false;
				return iDeviceStatus.DEVICE_END;
			} catch(Exception e) {
				return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
			}
		} else {
			return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
		}
	}
	
	// perform a write I/O operation
	// see: iDevice
	@Override
	public int write(int opcode, int dataLength, iDeviceIO memSource) {
		
		// Display Data ?
		if ((opcode & 0x05) == 0x05) {
			// display data at line xxxxxB without clearing screen
			int atLine = (opcode >> 3) & 0x1F;
			int startOffset = this.out3270Buffer.clear().reserveSpace(dataLength);
			this.orders3270Filter.useSource(memSource).transfer(this.out3270Buffer.getInternalBuffer(), startOffset, dataLength);
			try {
				this.emulating3270 = this.screen3270.display(this.out3270Buffer, atLine, this.clearScreenOnNextDisplay);
			} catch(IOException e) {
				this.emulating3270 = false; 
			}
			this.clearScreenOnNextDisplay = false;
			if (this.emulating3270) {
				return iDeviceStatus.DEVICE_END;
			} else {
				return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
			}
		}
		
		if (this.emulating3270) {
			// a "normal" operation follows a 3270 Display Data operation: leave 3270 Display data mode
			try {
				this.console3270.readFullScreen(-42424242, 0); // make sure screen ownership is released
				this.emulating3270 = false;
			} catch (IOException e) {
				return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
			}
		}
		
		// 3215 mode write?
		if (opcode < ConsoleCommandCodes.Limit3215) {
			return super.write(opcode, dataLength, this.orders3270Filter.useSource(memSource));
		}
		
		// do some logging
		if (Cpu370Bc.getLiveLogging()) {
			String opName;
			switch(opcode) {
			case ConsoleCommandCodes.Write_FS_WSF: opName = "WriteStructuredField"; break;
			case ConsoleCommandCodes.Write_FS_EW : opName = "EraseWrite          "; break;
			case ConsoleCommandCodes.Write_FS_EWA: opName = "EraseWriteAlternate "; break;
			default :                              opName = "Write               ";
			}
			System.out.printf("         write -> opcode: %s ; dataLength = %d\n", opName, dataLength);
		}
		
		// WSF - write structured field
		// the response is simulated by returning the original response of the terminal
		// during the initial telnet negociations
		if (opcode == ConsoleCommandCodes.Write_FS_WSF) {
			// check if we have a response from initial negociations to deliver...
			ByteBuffer wsfResponse = this.termTypeNegociator3270.getLastWsfResult();
			if (wsfResponse == null) {
				// not supported
				return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
			}
			// enqueue simulated input from the console
			synchronized(this) {
				this.in3270buffer = wsfResponse;
				this.notifyAll();
			}
			this.hasFullscreenIntrPending = true; // inform the VM of available input (even if faked)
			this.isInFullscreenMode = true; // pretend to be in fullscreen mode to allow a fullscreen read 
			return iDeviceStatus.DEVICE_END;
		}
		
		// fullscreen write
		try {
			// determine the 3270 command
			byte cmd3270
				= (opcode == ConsoleCommandCodes.Write_FS_EW) ? CommandCode3270.EW
				: (opcode == ConsoleCommandCodes.Write_FS_EWA) ? CommandCode3270.EWA
				: CommandCode3270.W;
			
			// acquire the 3270 screen
			this.isInFullscreenMode = this.console3270.acquireFullScreen(cmd3270 == CommandCode3270.W);
			if (!this.isInFullscreenMode) {
				// return special device status X'8E'
				return iDeviceStatus.ATTENTION | iDeviceStatus.CHANNEL_END | iDeviceStatus.DEVICE_END | iDeviceStatus.UNIT_CHECK; 
			}
			
			// get the bytes to write
			int startOffset = this.out3270Buffer.clear().append(cmd3270).reserveSpace(dataLength);
			memSource.transfer(this.out3270Buffer.getInternalBuffer(), startOffset, dataLength);
			
			// write to screen
			this.console3270.writeFullscreen(this.out3270Buffer);
			
		} catch (IOException e) {
			return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
		}
		
		return iDeviceStatus.DEVICE_END;
	}
	
	/*
	 * device capabilites for DIAG-x24
	 */

	// get the virtual device information for DIAG-x24
	// see: iDevice
	@Override
	public int getVDevInfo() {
		// this is a virtual 3270 graphic terminal device, no device status, no device flags 
		return 0x8000A010;
	}

	// get the real device information for DIAG-x24
	// see: iDevice
	@Override
	public int getRDevInfo() {
		// this is a simulated 3270 graphic terminal device with 130 characters line length and is a ...
		String termtype = this.termTypeNegociator3270.getTerminalType();
		if (termtype != null && termtype.startsWith("IBM-32")) {
			if (termtype.indexOf("-4") > 6) {
				return 0x40010450; // ... 80 cols x 43 lines model
			} else if (termtype.lastIndexOf("-3") > 6) {
				return 0x40010350; // ... 80 cols x 32 lines model
			} else {
				return 0x40010250; // ... 80 cols x 24 lines model
			}
		} else {
			return 0x40040250; // ... default model (IBM-DYNAMIC / not IBM-32xx-y[..])
		}
	}
	
	/*
	 * PF handling
	 */
	
	public void setCpPFkey(int pfKey, EbcdicHandler cmdline) {
		this.screen3270.setPF(pfKey, cmdline);
	}
	
	/*
	 * 3270 terminal input handler (MECAFF-console interfacing)
	 */
	
	public boolean sendPF03() {
		// PA3 has currently no function in the emulated CP  
		return false; // no output to drain away (at least as far as we know) 
	}

	public void sendFullScreenInput(ByteBuffer buffer, IVm3270ConsoleCompletedSink completedCallBack) {
		synchronized(this) {
			this.in3270buffer = buffer;
			this.in3270CompletedCallBack = completedCallBack;
			if (!this.emulating3270) {
				this.hasFullscreenIntrPending = true;
				// DBG System.out.printf("++ ConsoleGRAF: added pending fullscreen async interrupt\n");
			}
			this.notifyAll();
		}
	}

	public void sendFullScreenDataAvailability(boolean isAvailable) {
		// ignored, as only meaningfull if a MECAFF console client is working (but this simulates a "read" 3270 terminal!)
	}

	public void sendFullScreenTimedOut() {
		// ignored, as only meaningfull if a MECAFF console client is working (but this simulates a "read" 3270 terminal!)
	}
}
