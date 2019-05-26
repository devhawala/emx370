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
import java.util.ArrayList;

import dev.hawala.vm370.Emx370;
import dev.hawala.vm370.ebcdic.EbcdicHandler;
import dev.hawala.vm370.vm.cp.CPCommandInterpreter;
import dev.hawala.vm370.vm.device.iDevice;

/**
 * Implementation of the common part of the terminal user consoles, providing the main
 * CP interpreter loop and the handling of a "sub-CP loop" entered by a "#CP" issued
 * on a waiting user prompt.
 * 
 * <p>
 * This abstract class extends the interpreter for CP commands, uses its 
 * infrastructure and implements abstract methods defined there.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public abstract class UserCommandsConsole
						extends CPCommandInterpreter /* is a: iUserConsole */
						implements Runnable, Emx370.iTerminalConsole {
	
	// the thread group for all threads for the connected terminal emulator
	private final ThreadGroup thrGroup;
	
	// the commands enqueued and waiting for processing
	private final ArrayList<String> inputQueue = new ArrayList<String>();
	
	// the input reader thread and the flag to stop it
	private Thread inputThread;
	protected volatile boolean doShutdown = false;
	
	// are we actively waiting for user input, i.e. if the prompt to
	// be (re)issued before waiting for the next command?
	private boolean readPending = false;
	
	// constructor (does not start the input thread!)
	protected UserCommandsConsole(ThreadGroup thrGroup) {
		this.thrGroup = thrGroup;
	}
	
	// start listening for input lines arriving asynchronously
	protected void startWork() {
		this.inputThread = new Thread(this.thrGroup, this, this.thrGroup.getName() + " UserCommandsConsole.startWork->run(getNextUserInputLine -> inputQueue)");
		this.inputThread.start();
	}
	
	// stop listening for input lines and if a user is currently logged on: halt the VM 
	public void shutdown() {
		this.doShutdown = true;
		if (this.vm != null) { this.vm.requestHalt(); }
		if (this.inputThread != null) { this.inputThread.interrupt(); }
	}
	
	// the input line reader method to be implemented by terminal type dependent
	// user consoles
	protected abstract String getNextUserInputLine() throws IOException;
	
	// the current prompt type to be displayed when requesting input from the user
	protected enum PromptState { VmRead, CpRead, PwRead, VmRunning }
	
	// the prompt switch method to be implemented by terminal type dependent
	// user consoles
	protected abstract void switchToPromptState(PromptState state); 
	
	// ebcdic buffers used by writeln() and writef()
	private EbcdicHandler writeBuffer = new EbcdicHandler(256);
	private static final EbcdicHandler EMPTYSTRING = new EbcdicHandler("");
	
	// the buffer to be used as output target instead of the console if
	// a CP command did not come form the console, must was issued by a
	// DIAG-X08 (virtual console function) with the buffering flag set
	private EbcdicHandler writeTarget = null;

	// write a line (resp. a set of lines) to the console or into the
	// CP output buffer
	// see: iUserConsole
	@Override
	public void writeln(String line) {
		if (this.writeTarget == null) {
			String[] singleLines = line.split("\n");
			for (String singleLine : singleLines) {
				this.writeBuffer.reset().appendUnicode(singleLine);
				this.writeAddCR(this.writeBuffer);
			}
			if (line.endsWith("\n\n")) { this.writeAddCR(EMPTYSTRING); }
		} else {
			if (this.writeTarget.getLength() > 0) {
				this.writeTarget.appendEbcdicChar((byte)0x15); // CP separator character
			}
			this.writeTarget.appendUnicode(line);
		}
	}

	// do a printf of a pattern with arguments to the console or into the
	// CP output buffer
	// see: iUserConsole
	@Override
	public void writef(String pattern, Object... args) {
		if (this.writeTarget == null) {
			String line = String.format(pattern, args);
			String[] singleLines = line.split("\n");  // if line ends with \n, there is no empty string at end...
			for (int i = 0; i < singleLines.length - 1; i++) {
				this.writeBuffer.reset().appendUnicode(singleLines[i]);
				this.writeAddCR(this.writeBuffer);
			}
			this.writeBuffer.reset().appendUnicode(singleLines[singleLines.length - 1]);
			if (line.endsWith("\n")) {
				this.writeAddCR(this.writeBuffer);
			} else {
				this.writeNoCR(this.writeBuffer);
			}
			if (line.endsWith("\n\n")) { this.writeAddCR(EMPTYSTRING); }
		} else {
			if (this.writeTarget.getLength() > 0) {
				this.writeTarget.appendEbcdicChar((byte)0x15); // CP separator character
			}
			String line = String.format(pattern, args);
			if (line.endsWith("\n")) { line = line.substring(0, line.length()-1); }
			this.writeTarget.appendUnicode(line);
		}
	}

	// implementation of the thread reading lines from the console and enqueuing them
	// into 'inputQueue', handing the following special user interaction cases:
	//  - the remote terminal emulator disappears
	//     => done
	//  - an empty line is entered although no input is expected (no prompt)
	//     => if first time:
	//          if VM is in MODE CP : halt the VM and enter CP
	//          else : enqueue an attention interrupt of the console device
	//     => if the attention interrupt is still pending
	//        (i.e.: second time and the VM did not honor the interrupt request)
	//        drop the pending attention interrupt, halt the VM and enter CP
	//  - split lines at # (resp. the current line separator char) and enqueue the
	//    parts as separate command lines but preserving the # for "#CP ..." lines
	@Override
	public void run() {
		while(!this.doShutdown) {
			try {
				String line = getNextUserInputLine();
				if (line == null) {
					this.writeln("User session ended by closing input source!");
					System.out.println("** User session ended by closing input source!");
					return;
				}
				synchronized(this.inputQueue) {
					if (line.length() == 0 && !this.readPending) {
						// System.err.println("empty input => halting VM"); // remove temporary code (tests only...)
						if (this.isTerminalAttnModeCP()) {
							this.vm.requestHalt();
						} else {
							iDevice consoleDev = this.vm.getDevice(-1).getDevice();
							if (consoleDev.hasPendingAsyncInterrupt()) {
								// 2nd Attention after the first one was not honored by the VM => interrupt to CP
								consoleDev.consumeNextAsyncInterrupt(); // clear pending Attention interrupt 
								this.vm.requestHalt(); // goto CP
							} else {
								// let the VM know the user wants to be noticed...
								consoleDev.doAttentionInterrupt();
							}
						}
					} else if (line.length() == 0) {
						this.inputQueue.add(line);
						this.readPending = false;
						this.inputQueue.notifyAll();
					} else {
						String hash = this.getIsoLineSeparator();
						String cpPrefix = this.getImmediateCpPrefix();
						while(line.length() > 0) {
							String addLine = line;
							int hashPos = line.indexOf(hash, 1);
							if (hashPos > 0) {
								addLine = line.substring(0,  hashPos);
								line = line.substring(hashPos);
							} else {
								line = "";
							}
							if (addLine.startsWith(hash) && !addLine.toUpperCase().startsWith(cpPrefix)) {
								// skip #
								if (addLine.length() > 0) {
									addLine = addLine.substring(1); 
								} else {
									addLine = "";
								}
							}
							this.inputQueue.add(addLine);
							if (this.readPending) {
								this.readPending = false;
								this.inputQueue.notifyAll();
							}
						}
					}
				}
			} catch (Exception exc) {
				// ignored...
			}
		}
	}
	
	// get the number of input lines waiting
	// see: iUserConsole
	@Override
	public int getEnqueuedUserInput() {
		synchronized(this.inputQueue) {
			return this.inputQueue.size();
		}
	}
	
	// check if the input queue next has one #CP or more commands in line and handle this
	// lines, either by entering a sub-CP command loop (if a line is exactly #CP) or by
	// processing the single commands introduced by #CP.
	// !! requires to be called with holding the lock on this.inputQueue !!
	private void runSubCpCommands() {
		
		// check if the user wants to execute CP commands
		String cpPrefix = this.getImmediateCpPrefix(); // usually #CP
		boolean inCp = (this.inputQueue.size() > 0 && this.inputQueue.get(0).toUpperCase().startsWith(cpPrefix));
		while (inCp) {
			if (this.inputQueue.get(0).toUpperCase().equals(cpPrefix)) {
				// exactly #CP without a command
				// => enter the sub-CP loop
				this.inputQueue.remove(0);
				boolean stayInCp = true;
				while (stayInCp) {
					String cmd = null;
					synchronized(this.inputQueue) {
						if (this.inputQueue.size() == 0) {
							// signal that we await a CP command input
							this.readPending = true; 
							this.switchToPromptState(PromptState.CpRead);
						}
						while (this.inputQueue.size() == 0) {
							try {
								this.inputQueue.wait(50);
							} catch (InterruptedException ie) {
								throw new ReturnToMainLoopException();
							}
						}
						cmd = this.inputQueue.get(0);
						this.inputQueue.remove(0);
						this.readPending = false;
						this.switchToPromptState(PromptState.VmRunning);
					}
					stayInCp = !this.executeCPCommand(cmd, true, false); // executeCPCommand() returns if a loop is to be left...
				}
			} else {
				// check if it is a single #CP command
				String cmd = this.inputQueue.get(0).substring(cpPrefix.length()); // cmd.length is > 0 as the line was not only "#CP"
				if (cmd.startsWith(" ")) {
					// it was #cp xxxx ... so it is a command intended to CP
					this.inputQueue.remove(0); // consume the input line
					cmd = cmd.trim();          // remove leading blanks
					this.executeCPCommand(cmd, true, false); // execute as CP command
				} else {
					// it was #cpxxxx ... so not a #CP command: remove the # and let the line flow in
					this.inputQueue.set(0, cmd.substring(1));
				}
			}
			
			// is next queued line also a #CP line?
			inCp = (this.inputQueue.size() > 0 && this.inputQueue.get(0).toUpperCase().startsWith(cpPrefix));
		}
	}

	// get a user input line for the VM (which actively requests a new line!)
	// see: iUserConsole
	@Override
	public eUserInputState getNextUserInputForVMREAD(EbcdicHandler buffer) {
		synchronized(this.inputQueue) {
			// check if the user wants to execute CP commands while the VM wants an input line
			this.runSubCpCommands();
			
			// check if a line is available for the VM, if not: prompt for it
			if (this.inputQueue.size() == 0) {
				if (this.readPending) { return eUserInputState.NoneAvailable; }
				this.readPending = true;
				this.switchToPromptState(PromptState.VmRead);
				return eUserInputState.NoneAvailableUserPrompted;
			}
			
			// we have a line for the VM, so fill the buffer provided and tell so...
			String line = this.inputQueue.get(0);
			this.inputQueue.remove(0);
			buffer.reset().appendUnicode(line);
			if (this.readPending) {
				this.readPending = false;
				return eUserInputState.ReadFromPrompt;
			}
			return eUserInputState.ReadFromBuffer;
		}
	}

	// drop a currently waiting input lines and enqueue the given command
	@Override
	protected void resetInputQueueTo(String command) {
		synchronized(this.inputQueue) {
			this.inputQueue.clear();
			this.inputQueue.add(command);
		}
	}

	// method cyclically called by the VM to check for CP commands entered
	// asynchronously by the user 
	// see: iCommandExecutor
	@Override
	public void executePendingAsyncCommands() {
		this.runSubCpCommands();
	}

	// execute one or more CP commands on behalf of DIAG-X08, possibly buffering command output
	// instead of writing it out
	// see: iCommandExecutor
	@Override
	public int processCommandBuffer(EbcdicHandler commandBuffer, EbcdicHandler outputBuffer) {
		try {
			if (outputBuffer != null) {
				outputBuffer.reset();
				this.writeTarget = outputBuffer; // output will go to the buffer instead of to the terminal
			}

			commandBuffer.appendEbcdicChar((byte)0x15); // append a line end to ensure that we will find the end!
			int len = commandBuffer.getLength();
			EbcdicHandler singleCommand = new EbcdicHandler(len);
			byte[] b = commandBuffer.getRawBytes();
			int curr = 0;
			while (curr < len) {
				byte c = b[curr++];
				if (c == (byte)0x15) {
					this.executeCPCommand(singleCommand.getString(), false, true);
					if (this.getLastRC(false) != 0) { break; } 
					singleCommand.reset();
				} else {
					singleCommand.appendEbcdicChar(c);
				}
			}
			
		} finally {
			this.writeTarget = null;
		}
		return this.getLastRC();
	}
	
	// the top level CP command interpreter loop which is started when a terminal
	// emulator connects, so initially there is no VM allocated to this interpreter.
	// When a LOGON is entered and the user name is valid, the script to initialize
	// the VM (load disks etc and do :IPL at end) is invoked an the VM runs as part
	// of the :IPL command: when the VM hatls for some reason, the :IPL command ends
	// and control returns to this loop.
	// While processing incoming user input, the VM possibly enters a sub-CP-loop
	// (see this.runSubCpCommands()) which returns to the VM (either running or
	// waiting for user input at VM READ).
	public void mainLoop() {
		// greet the user
		this.writeLogo();
		
		// while the terminal connection is alive (or tho be kept alive)
		while(!this.doShutdown) {
			try {
				// get the next CP command to process at top level
				String cpCommand = null;
				synchronized(this.inputQueue) {
					// if nothing is enqueued: show CP READ prompt
					if (this.inputQueue.size() == 0) {
						this.readPending = true;
						this.switchToPromptState(PromptState.CpRead);
					}
					
					// wait for an item in the input queue
					while (this.inputQueue.size() == 0) {
						try {
							this.inputQueue.wait(10); // wait 10 milliseconds
							if (this.doShutdown) {
								this.shutdownUserVm();
								return;
							}
						} catch (InterruptedException ie) {
							System.out.println("+++ UserCommandsConsole.mainLoop() => interrupted");
							return; // interrupting the main queue means exactly this: leave it immediately
						}
					}
					
					// get the CP command
					this.readPending = false;
					cpCommand = this.inputQueue.get(0);
					this.inputQueue.remove(0);
				}
				
				// process it
				this.executeCPCommand(cpCommand, false, false);
			} catch(ReturnToMainLoopException rtmException) {
				// ignored: the purpose is exactly to get here 
			}
		}
		this.shutdownUserVm();
	}
	
}
