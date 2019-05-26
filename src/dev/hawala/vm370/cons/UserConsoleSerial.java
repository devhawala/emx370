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

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;

import dev.hawala.vm370.ebcdic.EbcdicHandler;
import dev.hawala.vm370.vm.device.iDevice;

/**
 * User console for a simple serial (~3215) terminal.
 * 
 * <p>
 * This class is a simple adapter for the generic user console {@link UserCommandsConsole}
 * doing simple input / output on the streams to the remote telnet client. 
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public class UserConsoleSerial extends UserCommandsConsole {
	
	// the streams to the remote telnet client
	private final PrintStream prs;
	private final DataInputStream dis;

	// our ptompt strings sent when user input is expected
	private final EbcdicHandler promptCpRead = new EbcdicHandler("CP read > ");
	private final EbcdicHandler promptPwRead = new EbcdicHandler("PWDread > ");
	private final EbcdicHandler promptVmRead = new EbcdicHandler("VM read > ");
	
	// is echoing the user input to be suppressed?
	private boolean inPwdMode = false; 
	
	// the console iDevice related with this user console
	private final ConsoleSimple consoleCONS;
	
	// constructor: startup everything
	public UserConsoleSerial(ThreadGroup thrGroup, int pseudoLine, InputStream is, OutputStream os) {
		super(thrGroup);
		this.dis = new DataInputStream(is);
		if (os instanceof PrintStream) {
			this.prs = (PrintStream)os;
		} else {
			this.prs = new PrintStream(os);
		}
		
		this.consoleCONS = new ConsoleSimple(this, pseudoLine); // no 3270 capabilities for fullscreen or the like...
		
		this.startWork();
	}
	
	// shutdown the terminal connection
	@Override
	public void shutdown() {
		this.prs.close();
		try {
			this.dis.close();
		} catch (IOException e) {
			// ignored...
		}
		this.consoleCONS.shutdown();
		super.shutdown();
	}
	
	/*
	 * process user input
	 */
	
	// buffer for collecting the byte-wise incoming input line
	private final byte[] lineBuffer = new byte[256];
	
	// byte-sequence sent to do a single backspace
	private final static byte[] BS_ECHO_BYTES = { (byte)0x08 , (byte)0x20 , (byte)0x08 }; // BS, blank, BS
	
	// collect the single bytes coming from the telnet client on each key stroke,
	// echoing the character entered, handling password mode and backspace character
	// (very simplistic and old fashioned input routine, could be improved some day) 
	private String readLine() {
		int lineBufferPos = 0;
		try {
			byte b = this.dis.readByte();
			while (b != (byte)0x0A) {
				if (b == (byte)0x08) {
					if (lineBufferPos > 0) {
						lineBufferPos--;
						if (!this.inPwdMode) {
							this.prs.write(BS_ECHO_BYTES);
							this.prs.flush();
						}
					}
				} else if (b != (byte)0x0D) {
					if (lineBufferPos < this.lineBuffer.length) {
						this.lineBuffer[lineBufferPos++] = b;
						if (!this.inPwdMode) {
							this.prs.write(b);
							this.prs.flush();
						}
					}
				}
				b = this.dis.readByte();
			}
			this.prs.println();
			this.prs.flush();
			return new String(lineBuffer, 0, lineBufferPos);
		} catch (IOException e) {
			return null;
		}
	}
	
	/*
	 * Items for abstract class: UserCommandsConsole 
	 */

	@Override
	protected iDevice getConsoleDevice() {
		return this.consoleCONS;
	}

	@Override
	protected String getNextUserInputLine() throws IOException {
		if (this.doShutdown) { return null; }
		String line = this.readLine();
		if (line == null) {
			this.shutdown();
		} else if (this.inPwdMode) {
			// pwd mode lasts only for one input request
			this.inPwdMode = false;
		}
		return line;
	}

	@Override
	protected void switchToPromptState(PromptState state) {
		switch(state) {
		case CpRead:
			this.writeNoCR(promptCpRead);
			break;
		case VmRead:
			this.writeNoCR(promptVmRead);
			break;
		case PwRead:
			this.inPwdMode = true;
			this.writeNoCR(promptPwRead);
			break;
		default:
		}
	}

	@Override
	public void writeAddCR(EbcdicHandler line) {
		System.out.flush();
		System.err.flush();
		this.prs.println(line.getString());
		this.prs.flush();	
	}

	@Override
	public void writeNoCR(EbcdicHandler line) {
		System.out.flush();
		System.err.flush();
		this.prs.print(line.getString());
		this.prs.flush();
	}
}
