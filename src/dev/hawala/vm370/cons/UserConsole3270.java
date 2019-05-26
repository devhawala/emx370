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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;

import dev.hawala.vm370.ebcdic.EbcdicHandler;
import dev.hawala.vm370.ebcdic.EbcdicTextPipeline;
import dev.hawala.vm370.mecaff.ByteBuffer;
import dev.hawala.vm370.mecaff.IVm3270ConsoleCompletedSink;
import dev.hawala.vm370.mecaff.IVm3270ConsoleInputSink;
import dev.hawala.vm370.mecaff.TerminalTypeNegotiator;
import dev.hawala.vm370.mecaff.Vm3270Console;
import dev.hawala.vm370.mecaff.Vm3270Console.Attr;
import dev.hawala.vm370.mecaff.Vm3270Console.ConsoleElement;
import dev.hawala.vm370.mecaff.Vm3270Console.InputState;
import dev.hawala.vm370.vm.device.iDevice;

/**
 * User console for a 3270 terminal.
 * 
 * <p>
 * This class is in fact an adapter for the generic user console {@link UserCommandsConsole}
 * interfacing it to a MECAFF-console, providing an iDevice to the virtual machine for SIO and
 * DIAG-x58 operations on the MECAFF-console. 
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public class UserConsole3270 extends UserCommandsConsole implements IVm3270ConsoleInputSink {
	
	// the negociator used for the terminal connection
	private final TerminalTypeNegotiator conn3270;
	
	// the telnet I/O streams to the connection terminal emulator
	private final InputStream is;
	private final OutputStream os;
	
	// the MECAFF-console and its telated items
	private final Vm3270Console console3270;
	private final Term2Console term2Console;
	private final EbcdicTextPipeline pipeline2console;
	
	// the console iDevice related with this user console
	private final ConsoleGRAF consoleDevice;
	
	// the user input lines queue from the the MECAFF-console 
	private final ArrayList<String> userInputLines = new ArrayList<String>();
	
	// constructor: startup everything
	public UserConsole3270(ThreadGroup thrGroup, int pseudoLine, TerminalTypeNegotiator conn3270, short sendDelayMs) {
		super(thrGroup);
		// save the connection objects
		this.conn3270 = conn3270;
		this.is = conn3270.getIsFromTerm();
		this.os = conn3270.getOsToTerm();
		
		// create a MECAFF 320 user interaction console
		this.console3270 = new Vm3270Console(thrGroup, this, this.os, conn3270.getNumAltRows(), conn3270.getNumAltCols(), conn3270.canExtended(), sendDelayMs);
		
		// create and start the thread reading the terminal's InputStream and stuffing input in the MECAFF console 
		this.term2Console = new Term2Console(thrGroup, this.is, this.console3270);
		
		// create the line buffer from the VM to the MECAFF console
		this.pipeline2console = new EbcdicTextPipeline(thrGroup, this.console3270, null, "pipeline2console");
		
		// create the 3270 device for the VM
		this.consoleDevice = new ConsoleGRAF(this, pseudoLine, this.console3270, this.conn3270);
		
		// let the CP command interpreter start reading user input
		this.startWork();
	}
	
	// shutdown the terminal connection
	@Override
	public void shutdown() {
		System.out.println("+++ UserConsole3270.shutdown()");
		try { this.os.close(); } catch (IOException e) { }
		try { this.is.close(); } catch (IOException e) { }
		this.consoleDevice.shutdown();
		this.term2Console.shutdown();
		this.pipeline2console.shutdown();
		this.console3270.close();
		super.shutdown();
	}
	
	/*
	 * input stream reader => feed incoming data from the terminal emulator into the MECAFF-console
	 */
	private class Term2Console implements Runnable {
		
		private final InputStream src;
		private final Vm3270Console trg;
		
		private final Thread thr;
		private volatile boolean doStop = false;
		
		public Term2Console(ThreadGroup thrGroup, InputStream is, Vm3270Console console3270) {
			this.src = is;
			this.trg = console3270;
			
			this.thr = new Thread(thrGroup, this, thrGroup.getName() + " UserConsole3270.Term2Console(is -> console3270)");
			this.thr.start();
		}
		
		public void shutdown() {
			this.doStop = true;
			this.thr.interrupt();
		}

		@Override
		public void run() {
			byte[] buffer = new byte[8192];
			try {
				int count = this.src.read(buffer);
				while(!this.doStop && count >= 0) {
					this.trg.processBytesFromTerminal(buffer, count);
					count = this.src.read(buffer);
				}
			}
			catch(Exception e) {
				// ignored
				System.out.printf("** terminal connection lost (%s)\n", e.getMessage());
			}
			if (!this.doStop) {
				// System.out.println("** shutting down UserConsole3270");
				UserConsole3270.this.shutdown();
			}
			// System.out.println("** leaving Term2Console.run();");
		}
	}
	
	/*
	 * Items for abstract class: UserCommandsConsole 
	 */

	@Override
	protected iDevice getConsoleDevice() {
		return this.consoleDevice; 
	}
	
	@Override
	public void setCpPFkey(int pfKey, EbcdicHandler cmdline) {
		this.consoleDevice.setCpPFkey(pfKey, cmdline);
	}
	
	@Override
	public void setMecaffPFKey(int pfKey, String cmdline) {
		this.console3270.setPfCommand(pfKey, cmdline);
	}
	
	@Override
	public String getMecaffPFKey(int pfKey) {
		return this.console3270.getPfCommand(pfKey);
	}
	
	@Override
	public void setMecaffFlowMode(boolean flowMode) {
		this.console3270.setFlowMode(flowMode);
	}
	
	@Override
	public void setMecaffAttr(ConsoleElement element, Attr attr) {
		this.console3270.setAttr(element, attr);
	}

	@Override
	protected String getNextUserInputLine() throws IOException {
		String nextLine;
		synchronized(this.userInputLines) {
			while (this.userInputLines.size() == 0) {
				try {
					this.userInputLines.wait();
				} catch(InterruptedException e) {
					return null;
				}
			}
			nextLine = this.userInputLines.get(0);
			this.userInputLines.remove(0);
		}
		return nextLine;
	}

	@Override
	protected void switchToPromptState(PromptState state) {
		try {
			switch(state) {
			case CpRead : this.console3270.setInputState(InputState.CpRead); break;
			case VmRead : this.console3270.setInputState(InputState.VmRead); break;
			case PwRead : this.console3270.setInputState(InputState.PwRead); break;
			default: this.console3270.setInputState(InputState.Running); break;
			}
		} catch (IOException e) {
			// ignored
		}
	}
	
	// some strings from the host that we need to recognize the end of a vm-session
	private final static String Vm370LogoffPattern = "LOGOFF AT [0-9]{2}:[0-9]{2}:[0-9]{2} .*";
	private final static String Vm370DisconnectPattern = "DISCONNECT AT [0-9]{2}:[0-9]{2}:[0-9]{2} .*";
	private final static String Vm370Online = "emx370 Online";
	
	// was there a line written to the terminal indicating that the session ended?
	private boolean lastWasSessionEndStart = false;

	@Override
	public void writeAddCR(EbcdicHandler line) {
		this.pipeline2console.appendLine(line);

		String uniString = line.getString();
		if (this.lastWasSessionEndStart && (uniString.equals(Vm370Online))) {
			try { Thread.sleep(500); this.console3270.endCurrentSession(true); } catch (Exception e) { }
			this.lastWasSessionEndStart = false;
		}
		if (uniString.matches(Vm370LogoffPattern) || uniString.matches(Vm370DisconnectPattern)) {
			this.lastWasSessionEndStart = true;
		}
	}

	@Override
	public void writeNoCR(EbcdicHandler line) {
		// TODO handle correctly: append to current line
		// (not easy for a 3270: collect calls to writeNoCR() and finalize on writeAddCR() or change prompt state) 
		this.writeAddCR(line);
	}
	
	/*
	 * Items for interface: IVm3270ConsoleInputSink
	 */
	
	private void sendUserInput(String inputLine) {
		synchronized(this.userInputLines) {
			this.userInputLines.add(inputLine);
			this.userInputLines.notifyAll();
		}
	}

	@Override
	public void sendUserInput(EbcdicHandler inputLine) throws IOException {
		this.sendUserInput(inputLine.getString());
	}

	@Override
	public boolean sendInterrupt_CP(EbcdicHandler drainGuard)
			throws IOException {
		this.sendUserInput(this.getImmediateCpPrefix());
		return false;
	}

	@Override
	public boolean sendInterrupt_HT(EbcdicHandler drainGuard)
			throws IOException {
		this.sendUserInput("HT");
		this.sendUserInput(drainGuard.getString());
		return true;
	}

	@Override
	public boolean sendInterrupt_HX(EbcdicHandler drainGuard)
			throws IOException {
		this.sendUserInput("HX");
		return false;
	}

	@Override
	public boolean sendPF03() throws IOException {
		return this.consoleDevice.sendPF03();
	}

	@Override
	public void sendFullScreenInput(ByteBuffer buffer, IVm3270ConsoleCompletedSink completedCallBack) throws IOException {
		this.consoleDevice.sendFullScreenInput(buffer, completedCallBack);
	}

	@Override
	public void sendFullScreenDataAvailability(boolean isAvailable) throws IOException {
		this.consoleDevice.sendFullScreenDataAvailability(isAvailable);
	}

	@Override
	public void sendFullScreenTimedOut() throws IOException {
		this.consoleDevice.sendFullScreenTimedOut();
	}

}
