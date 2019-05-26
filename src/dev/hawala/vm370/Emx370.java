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

package dev.hawala.vm370;

import static dev.hawala.vm370.CommandTokens.*;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;

import dev.hawala.vm370.CommandTokens.Tokenizer;
import dev.hawala.vm370.cons.UserConsole3270;
import dev.hawala.vm370.cons.UserConsoleSerial;
import dev.hawala.vm370.mecaff.TerminalTypeNegotiator;
import dev.hawala.vm370.vm.cp.CPCommandInterpreterEmulator;
import dev.hawala.vm370.vm.machine.CPVirtualMachine;

/**
 * Main class for the emx370 emulator.
 * 
 * <p>
 * This program provides a simulation of the inside view of virtual
 * machines as provided by the CP (control program) of a VM/360 R6 system.
 * Such a VM has:
 * </p>
 * <ul>
 * <li>a S/370 CPU in BC mode (as defined by PrincOps of 1975)</li>
 * <li>16 MBytes main Memory</li>
 * <li>a 3215 or 3270 console</li>
 * <li>a set of minidisks, either exclusively dedicated to this VM,
 *     typically accessed in R/W mode, or shared with other VMs in 
 *     R/O mode (like system minidisks)</li>
 * <li>optionally one or more tape devices</li>
 * <li>optionally a card reader (RDR), a card puncher (PUN) resp.
 * a printer (PRT), each interfacing files on the external OS where
 * the emulator runs.</li>
 * </ul>
 * <p>
 * Except for shared minidisks, all these resources are owned by exactly
 * one VM, so running 3 VMs simultaneously in this emulator will allocate 3
 * CPUs (class instances), 3 x 16 MByte for main memory etc. 
 * </p>
 * <p>
 * This main program uses a configuration file defining the resources
 * shared by all VMs and also provides a command interpreter on
 * stdin/stdout allowing to shutdown the emx370 system. 
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public class Emx370 {
	
	/**
	 * Interface that a console running a VM's CP command interpreter
	 * through a terminal connection must implement. 
	 */
	public interface iTerminalConsole {
		
		/**
		 * Start the CP command interpreter loop for the connected terminal,
		 * handling CP and emulator commands, for eventually creating virtual
		 * machines (sequentially each time a user logs on and then off). 
		 */
		public void mainLoop();
		
		/**
		 * shutdown the connection, possibly logging out the current user if
		 * still logged on.
		 */
		public void shutdown();
		
	}
	
	// the logger used here
	private static Log logger = Log.getLogger();
	
	// command for converting a PS to a PDF file, in Java path notation
	private static String ps2pdfCommand = "ps2pdf";
	
	// the tcp/ip port for ingoing connections
	// (the connection type (3215 or 3270) is determined through the
	// negotiation with the remote terminal emulator)
	private static int listenPort = 3278;
	
	// disallow a WSF-query to the 3270 terminal to determine its capabilities?
	private static boolean stickToPredefinedTerminalTypes = false;
	
	// the minimal number of colors to be supported for the terminal to
	// accepted as a color terminal
	private static short minColorCount = 4;
	
	// the list of current active connections (a list entry may be null, meaning
	// this connection was closed in the meantime)
	private static ArrayList<ConnectionHandler> connections = new ArrayList<ConnectionHandler>();
	
	// base CUU used to simulate the real device number or terminal line number
	// to which a remote terminal emulator is connected to:
	//     "real CUU" = base CUU + <index in connections>
	private static final int REAL_TERMINAL_DEVICE_BASE = 0x010;
	
	/**
	 * Get the configured command line program (with full path) for conversion
	 * of PS to PDF files.
	 * 
	 * @return the PS to PDF conversion command.
	 */
	public static String getPs2PdfCommand() {
		return ps2pdfCommand;
	}
	
	// get the next index to be used in <connections> for a freshly connected
	// terminal emulator 
	private static int findFreeTerminalLine() {
		for (int i = 0; i < connections.size(); i++) {
			if (connections.get(i) == null) { return i; }
		}
		return connections.size();
	}
	
	/**
	 * Manager for a single terminal connection, running the CP command
	 * interpreter bound to this connection in a separate thread.
	 */
	private static class ConnectionHandler implements Runnable {
		
		// the CP interpreter for the console
		private final iTerminalConsole terminalConsole;
		
		// the index of this connection in <connections>
		private final int connectionIndex;
		
		// the thread running the mainloop for the connection's CP interaction
		private final Thread thread;
		
		// constructor: starts the thread for the the CP interaction
		public ConnectionHandler(ThreadGroup thrGroup, iTerminalConsole terminalConsole, int connectionIndex) {
			this.terminalConsole = terminalConsole;
			this.connectionIndex = connectionIndex;
			
			this.thread = new Thread(thrGroup, this, thrGroup.getName() + " ConnectionHandler -> run-mainLoop()");
			this.thread.start();
		}
		
		// force closing the remote connection
		public void shutdown() {
			this.terminalConsole.shutdown(); // bring the CP interpreted loop to terminate itself
			this.thread.interrupt(); // just to be sure...
		}

		// run the CP interpreter loop until it terminates itself 
		@Override
		public void run() {
			this.terminalConsole.mainLoop();
			synchronized(connections) {
				System.out.printf(
						"Closed connection %d (line %03X)\n",
						this.connectionIndex,
						this.connectionIndex + REAL_TERMINAL_DEVICE_BASE);
				connections.set(this.connectionIndex, null);
			}
		}
		
	}
	
	/**
	 * Implementation of the socket listener waiting for new terminal
	 * connections in a separate thread, starting a new ConnectionHandler
	 * for each connection.
	 */
	private static class ConnectionListener implements Runnable {
		
		private final ServerSocket serviceSocket;
		
		private final Thread thread;
		private volatile boolean doShutdown = false;
		
		// constructor: start listening on the given connection port
		public ConnectionListener(int listenPort) throws Exception {
			try {
				this.serviceSocket = new ServerSocket(listenPort);
			} catch (IOException  e) {
				throw new Exception("** Unable to open service socket on port: " + listenPort, e);
			}
			
			this.thread = new Thread(this, "ConnectionListener");
			this.thread.start();
		}
		
		// stop listening for new connections
		public void shutdown() {
			this.doShutdown = true;
			try {
				this.serviceSocket.close();
			} catch (IOException exc) {
				// ignored
			}
		}

		// thread implementation: wait for new connections and start a new 
		// ConnectionHandler for each.
		@Override
		public void run() {
			while(!this.doShutdown) {
				Socket terminalSideSocket = null;
				
				//  wait for a terminal opening a connection to us...
				try {
					terminalSideSocket = this.serviceSocket.accept();
				} catch (IOException exc) {
					
					if (this.doShutdown) { return; }
					logger.error("** Error listening on service socket: " + exc.getMessage());
					logger.info("** retrying listening on service socket...");
					continue;
				}
				
				// handle the new connection
				synchronized(connections) {
					// get the simulated terminal line number
					int connNo = findFreeTerminalLine();
					int pseudoLine = connNo + REAL_TERMINAL_DEVICE_BASE;
					ThreadGroup threadBasegroup = new ThreadGroup("Conn #" + connNo);
					
					// get the terminal type and get a matching console type
					TerminalTypeNegotiator termType = new TerminalTypeNegotiator(connNo, terminalSideSocket, stickToPredefinedTerminalTypes, minColorCount);
					if (termType.isClosed()) { continue; } // connection not workable
					iTerminalConsole console = (termType.isIn320Mode())
										? new UserConsole3270(threadBasegroup, pseudoLine, termType, (short)0)
										: new UserConsoleSerial(threadBasegroup, pseudoLine, termType.getIsFromTerm(), termType.getOsToTerm());
										
					// start the sessin for the terminal
					ConnectionHandler connHandler = new ConnectionHandler(threadBasegroup, console, connNo);
					if (connNo < connections.size()) {
						connections.set(connNo, connHandler);
					} else {
						connections.add(connHandler);
					}
					System.out.printf(
							"Accepted connection %d (line %03X, type: %s)\n",
							connNo,
							pseudoLine,
							(termType.isIn320Mode()) ? "3270" : "3215"
							);
				}
			}
		}
	}
	
	/*
	 * main command interpreter running on System.out / System.in,
	 * allowing to define shared minidisks and to shutdown the whole
	 * emx370 system. 
	 */
	
	private static final String SYS_MISSING_PARAM = "Missing parameter %s";
	
	private static void tell(String msg) {
		System.out.println(msg);
	}
	
	private static void tell(String format, Object... args) {
		System.out.printf(format, args);
	}
	
	// execute a single command, either from System.in or the system startup script 
	// returns: terminate the whole emx370 system?
	private static boolean executeSystemCommand(String line) {
		Tokenizer tokens = new Tokenizer(line);
		String cmd = tokens.nextUpper();
		
		if (cmd == null || cmd.length() == 0 || cmd.startsWith("#")) {
			// ignore empty lines or comment lines
			return false;
		}
		
		try { // all problems with parameters raise an exception which is caught for all commands
			
			// command: SHAREDCKDC <username> <cuu> <basefile-spec>
			if (cmd.equals("SHAREDCKDC")) {
				String arg = tokens.nextUpper();
				if (arg == null) { throw new CmdError(SYS_MISSING_PARAM, "username"); }
				String username = arg;
				
				arg = tokens.nextUpper();
				if (arg == null) { throw new CmdError(SYS_MISSING_PARAM, "cuu"); }
				int cuu = getCuu(arg);
				
				String basefileName = tokens.next();
				if (basefileName == null) { throw new CmdError(SYS_MISSING_PARAM, "basefile-spec"); }
				
				String id = CPCommandInterpreterEmulator.loadSharedDrive(username, cuu, basefileName);
				tell("Loaded shared CKDC with identifier: %s\n", id);
				
				return false;
			}
			
			// command: CPUTYPE <type>
			if (isToken(cmd, "CPUTYPE", 3)) {
				String arg = tokens.nextUpper();
				if (arg == null) {
					tell("CPU type for next LOGON: %s\n", CPVirtualMachine.getCpuType());
					return false;
				}
				if (tokens.next() != null) {
					tell("Extra parameter(s) ignored\n");
				}
				try {
					CPVirtualMachine.setCpuType(arg);
					tell("CPU type for next LOGON is now: %s\n", CPVirtualMachine.getCpuType());
				} catch(Exception e) {
					tell(e.getLocalizedMessage());
					return false;
				}
				return false;
			}
			
			// command: SHUTDOWN CONFIRMED
			if (isToken(cmd, "SHUTDOWN")) {
				String arg = tokens.nextUpper();
				if (!"CONFIRMED".equals(arg)) {
					tell("Magic word missing");
					return false;
				}
				arg = tokens.next();
				if (arg != null) {
					tell("Too much input!!");
					return false;
				}
				tell("... SHUTTING DOWN THE SYSTEM !!!!!");
				return true;
			}
			
			// command: PS2PDFcommand <ps2pdf-command>
			if (isToken(cmd, "PS2PDFCOMMAND")) {
				String arg = tokens.getRemaining();
				if (arg == null) {
					tell("missing argument <ps2pdf-command>");
					return false;
				}
				ps2pdfCommand = arg;
				return true;
			}
			
			// command: HElp
			if (isToken(cmd, "HELP", 2)) {
				tell("Possible system commands:");
				tell("  SHAREDCKDC <username> <cuu> <basefile-spec>");
				tell("  PS2PDFCOMMAND <ps2pdf-command>");
				tell("  SHUTDOWN CONFIRMED");
				return false;
			}
			
			tell("Error: invalid system command: " + line);
				
		} catch(Throwable thr) {
			tell("Error for command %s: %s\n", cmd, thr.getMessage());
		}
		
		return false;
	}
	
	// run a system script (and ignoring requests to shut down the system!)
	@SuppressWarnings("deprecation")
	private static void loadSystemScript(String filename) {
		File scriptFile = new File(filename);
		if (scriptFile == null || !scriptFile.exists() || !scriptFile.isFile() || !scriptFile.canRead()) {
			tell("Error: script file '%s' not found  or not readable\n", filename);
			return;
		}
		
		try {
			FileInputStream is = new FileInputStream(filename);
			DataInputStream dis = new DataInputStream(is);
			String line = dis.readLine();
			while(line != null) {
				executeSystemCommand(line);
				line = dis.readLine();
			}
			dis.close();
			is.close();
		} catch (FileNotFoundException e) {
			// should not happen, as file existence has been checked explicitly before calling this routine 
		} catch (IOException e) {
			tell("Error reading script file '%s': %s\n", filename, e.getMessage());
		}
	}

	/**
	 * Main entry point of the emx370 program
	 * @param args
	 */
	@SuppressWarnings("deprecation")
	public static void main(String[] args) {
		
		// load system script (should define shared CKD minidisks)
		tell("## Initializing emx370 system");
		loadSystemScript("emx370.script");
		
		// start the background listener for telnet connections
		tell("## Starting listener on port " + listenPort);
		ConnectionListener listener;
		try {
			listener = new ConnectionListener(listenPort);
		} catch (Exception  e) {
			logger.error(e.getMessage());
			logger.error("Program startup aborted");
			return;
		}

		// start the main console and await commands from stdin
		// until the shutdown is requested
		try {
			DataInputStream dis = new DataInputStream(System.in);
			String line;
			line = dis.readLine();
			while(line != null) {			
				if (executeSystemCommand(line)) { break; }
				line = dis.readLine();
			}
			dis.close();
		} catch (IOException e) {
			tell("Error reading from console, terminating emx370 system!");
		}
		
		// if we are here, the shutdown of the emulator has been requested
		// on the main console
		tell("## Shutting emx370 system");
		
		// shutdown the connection listener
		listener.shutdown();

		// shutdown all current terminal connections
		for (ConnectionHandler h : connections) {
			if (h != null) { h.shutdown(); }
		}
		
		// shutdown the logger's background thread (looking for configuration changes)
		Log.shutdown();
		
		// done: exit program
		tell("## emx370 was successfully shut down, exiting program now.");
	}

}
