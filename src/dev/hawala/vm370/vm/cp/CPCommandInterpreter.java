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
package dev.hawala.vm370.vm.cp;

import static dev.hawala.vm370.CommandTokens.getCuu;
import static dev.hawala.vm370.CommandTokens.isToken;

import dev.hawala.vm370.CommandTokens.CmdError;
import dev.hawala.vm370.CommandTokens.Tokenizer;
import dev.hawala.vm370.ebcdic.Ebcdic;
import dev.hawala.vm370.ebcdic.EbcdicHandler;
import dev.hawala.vm370.spool.iSpoolDevice;
import dev.hawala.vm370.vm.device.DeviceHandler;
import dev.hawala.vm370.vm.machine.DuplicateDeviceException;
import dev.hawala.vm370.vm.machine.PSWException;

/**
 * Implementation of a subset of CP commands intended to be compatible
 * with VM/370-R6.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public abstract class CPCommandInterpreter extends CPCommandInterpreterEmulator {
	
	private byte ebcdicLineSeparator = Ebcdic._Hash;
	
	private String lineSeparator = "#";
	
	private String cpImmediatePrefix = "#CP";
	
	public byte getEbcdicLineSeparator() { return this.ebcdicLineSeparator; }
	
	public String getIsoLineSeparator() { return this.lineSeparator; }
	
	public String getImmediateCpPrefix() { return this.cpImmediatePrefix; }
	
	private boolean terminalAttnModeCP = false;
	
	protected void setTerminalAttnMode(boolean mode) {
		synchronized(this) { this.terminalAttnModeCP = mode; }	
	}
	
	protected boolean isTerminalAttnModeCP() {
		synchronized(this) { return this.terminalAttnModeCP; }
	}
	
	/*
	 * Console Logo
	 */
	
	private static final String[] emx370Logo = {
	   //12345678901234567890123456789012345678901234567890123456789012345678901234567890
		"emx370 Online",
		"",
		"                   EEEEEEEE           33333  7777777  00000",
		"                  EE                 33   33 77   77 00   00",
		"                 EE                       33     77  00  000",
		"                EEEEE   M M  x    xX   3333     77   00 0 00",
		"               EE      M M M  X xX        33   77    000  00",
		"              EE       M M M  xxx    33   33   77    00   00",
		"             EEEEEEEE  M M M X   X    33333    77     00000",
		"",
		"     (first complete IPL of CMS under emx370 on 16.06.2015 at 20:19:23)",
		""
	};
	
	protected void writeLogo() {
		for (String line : emx370Logo) {
			this.writeln(line);
		}
	}
	
	/*
	 * PF keys
	 */
	
	private EbcdicHandler[] pfKeys = new EbcdicHandler[24]; // 0 based: 0..23 for PF01..PF24
	
	// termPfKey: 1..24
	// returns: OK or not
	private boolean defineCpPFKey(int termPfKey, EbcdicHandler cmdline) {
		if (termPfKey < 1 || termPfKey > 24) { return false; }
		int pfKey = termPfKey - 1;
		
		this.pfKeys[pfKey] = cmdline;
		this.setCpPFkey(pfKey, cmdline);
		
		return true;
	}
	
	
	/*
	 * main CP command interpreter
	 *  -> dispatches to emulator commands if the first character is a ':'
	 *  -> implements some "simple" commands (LOGON/LOGIN, BEGIN, IPL)
	 *  -> dispatches to the corresponding command implementation for all other known CP commands
	 */
	
	private static final String EMSG_UNKNOWN_CP_COMMAND     = "DMKCFC001E ?CP: %s";
	private static final String EMSG_LOGON_MISSING_PARAM    = "DMKLOG020E USERID MISSING OR INVALID";
	private static final String EMSG_LOGOFF_INVALID_PARAM   = "DMKLOG020E INVALID LOGOFF PARAM, USE NOSYNC?";
	private static final String EMSG_IPL_MISSING_PARAM      = "DMKCFG026E OPERAND MISSING OR INVALID";
	private static final String EMSG_IPL_DEVICE_NOT_FOUND   = "DMKCFG040E DEV %03X DOES NOT EXIST";
	private static final String EMSG_IPL_INV_DEVICE         = "DMKVMI022E VADDR MISSING OR INVALID";
	private static final String EMSG_NOT_RUNNABLE           = "DMKDSP450W CP ENTERED; VIRTUAL MACHINE NOT IN RUNNABLE STATE";
	
	private static final String MSG_TRAILING_PARAMS = "Trailing parameters ignored";
	
	private static final int RC_OK = 0;
	

	protected boolean executeCPCommand(String line, boolean isSubCP, boolean suppressUnknownCommandMessage) {
		this.setLastRC(RC_OK);
		
		// sanity checks
		if (line == null) { return false; }
		line = line.trim();
		if (line.length() == 0) { return false; }
		
		// check for comment
		if (line.startsWith("*")) {
			return false;
		}
		
		// check for emulator command
		if (line.startsWith(":")) {
			return this.executeEmulatorCommand(line, isSubCP);
		}
		
		// tokenize the line
		Tokenizer tokens = new Tokenizer(line);
		String cmd = tokens.nextUpper();
		
		// see what command this is..
		if ((isToken(cmd, "LOGON", 1) || isToken(cmd, "LOGIN", 4)) && this.vm == null) {			
			String vmName = tokens.nextUpper();
			if (vmName == null) {
				return this.emsg(EMSG_LOGON_MISSING_PARAM);
			}
			if (tokens.hasMore()) { 
				this.writeln(MSG_TRAILING_PARAMS);
			}
			
			// TODO: ask for password (what to compare??)
			
			// create the VM and issue the LOGON line
			this.createUserVm(vmName);
			
			return false;
		}
			
		if (isToken(cmd, "QUERY", 1)) {
			return this.cmdCP_QUERY(tokens);
		}
		
		// all other following commands require to be logged on
		if (this.vm == null) {
			return this.emsg(EMSG_UNKNOWN_CP_COMMAND, cmd);
		}
			
		if (isToken(cmd, "SET")) {
			return this.cmdCP_SET(tokens);
		}
		
		if (isToken(cmd, "SPOOL")) {
			return this.cmdCP_SPOOL(tokens);
		}
		
		if (isToken(cmd, "CLOSE")) {
			return this.cmdCP_CLOSE(tokens);
		}
			
		if (isToken(cmd, "TERMINAL", 4)) {
			return this.cmdCP_TERMINAL(tokens);
		}
		
		if (isToken(cmd, "DEFINE", 3)) {
			return this.cmdCP_DEFINE(tokens, isSubCP);
		}
		
		if (isToken(cmd, "LINK")) {
			return this.cmdCP_LINK(tokens);
		}
		
		if (isToken(cmd, "DETACH", 3)) {
			return this.cmdCP_DETACH(tokens);
		}
		
		if (isToken(cmd, "BEGIN", 1)) {
			if (tokens.hasMore()) { 
				this.writeln(MSG_TRAILING_PARAMS);
			}
			if (isSubCP) {
				return true;
			} else if (this.vmCanRun) {
				this.vmCanRun = this.vm.run();
			} else {
				return this.emsg(EMSG_NOT_RUNNABLE);
			}
			return false;
		}
		
		if (isToken(cmd, "IPL", 1)) {
			String systemName = tokens.nextUpper();
			boolean doRun = true;
			if (systemName == null) {
				return this.emsg(EMSG_IPL_MISSING_PARAM);
			}
			while (tokens.hasMore()) {
				String s = tokens.nextUpper();
				if (isToken(s,"RESET", 3)) {
					this.vm.cpu.resetEngine();
				} else if (isToken(s, "NORUN", 3)) {
					doRun = false;
				} else {
					this.writeln(MSG_TRAILING_PARAMS);
					break;
				}
			}
			if (isSubCP) {
				// can't ipl here from a cp command entered from a VM READ
				this.resetInputQueueTo(line);
				throw new ReturnToMainLoopException();
			}
			
			this.vmCanRun = false;
			try {
				boolean deviceIpled = false;
				try {
					int cuu = getCuu(systemName);
					deviceIpled = true;
					DeviceHandler dev = this.vm.getDevice(cuu);
					if (dev != null) {
						// let's do the device ipl, simulating the behavior described in PrincOps-1975 pp. 54-55
						// ...first: place a IPL CCW start at address 0:
						//   cmd: read (0x02), address: 0, flags: CC+SLI; length: 24
						// (command: "read with modifier bits set to zero" => this is "Read Initial Program Load" on a DASD device)
						this.vm.cpu.pokeMainMem(0x000000, 0x02_000000_60_00_0018L);
						if (dev.processFromAddress(0x000000, (byte)0x0)) { // CCW at address 0, protection key 0
							// then as loading ipl data from device was successful:
							// ... second:  place ipl device cuu in PSW and load the PSW
							this.vm.cpu.pokeMainMem(0x000002, (short)cuu); // assuming BC-mode: place the device in memory PSW (absolute address 2-3)
							this.vm.cpu.readPswFrom(0x000000);             // load the PSW just loaded
							this.vmCanRun = true;
						} else {
							this.vm.disableRunning(EMSG_IPL_INV_DEVICE);
							return this.emsg(EMSG_NOT_RUNNABLE);
						}
					} else {
						this.vm.disableRunning(EMSG_IPL_DEVICE_NOT_FOUND, cuu);
						return this.emsg(EMSG_NOT_RUNNABLE);
					}
				} catch (CmdError e) {
					// ignored: not a valid device number, so try a named segment
				}
				
				if (!deviceIpled) {
					// load the segment including the ipl settings (PSW, registers)
					this.vm.iplFromSegment(systemName);
					this.vmCanRun = true;
				}
				
				// start running the instructions just loaded
				if (doRun) { this.vmCanRun = this.vm.run(); }
			} catch(IllegalArgumentException e) {
				this.writef("ERROR: %s\n", e.getMessage());
				return this.emsg(EMSG_NOT_RUNNABLE);
			} catch (PSWException e) {
				switch(e.getProblemType()) {
					case ECMode: this.writeln("ERROR: attempt to IPL EC mode client OS"); break;
					case DisabledWait: this.writeln("ERROR: client OS ipls into disabled wait PSW state"); break;
					default: this.writeln("ERROR: client OS ipls into unknown/invalid PSW state"); break;
				}
				this.vmCanRun = false;
				return this.emsg(EMSG_NOT_RUNNABLE);
			}
			return false;
		}
		
		if (isToken(cmd, "LOGOFF", 3) || isToken(cmd, "LOGOUT", 3)) {
			if (isSubCP) {
				// can't logoff here from a cp command entered from a VM READ
				this.resetInputQueueTo(line);
				throw new ReturnToMainLoopException();
			}
			
			boolean preventSync = false;
			if (tokens.hasMore()) {
				String s = tokens.nextUpper();
				if (isToken(s, "NOSYNC", 4)) {
					preventSync = true;
				} else {
					return this.emsg(EMSG_LOGOFF_INVALID_PARAM); 
				} 
			}

			this.shutdownUserVm(preventSync);
			this.writeLogo();
			return false;
		}
		
		// unknown CP command...
		if (suppressUnknownCommandMessage) {
			this.setLastRC(1);
			return false;
		} else {
			return this.emsg(EMSG_UNKNOWN_CP_COMMAND, cmd);
		}
	}
	
	/*
	 * SET command
	 */
	
	private static final String EMSG_SET_MISSING_INVALID_PARAM = "DMKCFC026E OPERAND MISSING OR INVALID";
	
	private final static int NO_PFNO = 0x80000001;
	private final static int INV_PFNO = 0x80000000;
	private int getPfKeyNo(String subcmd) {
		int keyNo = INV_PFNO;
		if (subcmd.length() == 3) {
			char c = subcmd.charAt(2);
			keyNo = c - '0';
			if (keyNo < 0 || keyNo > 9) {
				return INV_PFNO;
			}
		} else if (subcmd.length() == 4) {
			char c1 = subcmd.charAt(2);
			char c2 = subcmd.charAt(3);
			int i1 = c1 - '0';
			int i2 = c2 - '0';
			if (i1 < 0 || i1 > 9 || i2 < 0 || i2 > 9) {
				return INV_PFNO;
			}
			keyNo = (i1 * 10) + i2;
		} else if (subcmd.length() == 2) {
			return NO_PFNO;
		} else {
			return INV_PFNO;
		}
		return keyNo;
	}
	
	// subcmd
	private boolean cmdCP_SET(Tokenizer tokens) {
		String subcmd = tokens.nextUpper();
		if (subcmd == null) {
			return this.emsg(EMSG_SET_MISSING_INVALID_PARAM);
		}
		
		if (isToken(subcmd, "EMSG")) {
			String param = tokens.nextUpper();
			if (isToken(param, "ON")) {
				this.vm.setVmmCode(true);
				this.vm.setVmmText(true);
			} else if (isToken(param, "OFF")) {
				this.vm.setVmmCode(false);
				this.vm.setVmmText(false);
			} else if (isToken(param, "CODE")) {
				this.vm.setVmmCode(true);
				this.vm.setVmmText(false);
			} else if (isToken(param, "TEXT")) {
				this.vm.setVmmCode(false);
				this.vm.setVmmText(true);
			} else {
				return this.emsg(EMSG_SET_MISSING_INVALID_PARAM);
			}
			return false;
		} else if (subcmd.startsWith("PF")) {
			// get key no
			int keyNo = this.getPfKeyNo(subcmd);
			if (keyNo == INV_PFNO || keyNo == INV_PFNO || keyNo < 1 || keyNo > 24) {
				return this.emsg(EMSG_SET_MISSING_INVALID_PARAM);
			}
			// get and normalize commandtext
			String option = tokens.peekNextUpper();
			String cmdline = null;
			if (option.equals("RECALL") || option.equals("RETRIEVE")) {
				cmdline = "RETRIEVE";
			} else if (option.equals("IMMED") || option.equals("TAB") || option.equals("COPY")) {
				tokens.next();
				if (tokens.hasMore()) {
					cmdline = option + " " + tokens.getRemaining();
				}
			} else if (option.equals("UNDEFINED")) {
				tokens.next();
				if (tokens.hasMore()) {
					cmdline = "DELAYED UNDEFINED " + tokens.getRemaining();
				}
			} else {
				if (option.equals("DELAYED")) { tokens.next(); }
				if (tokens.hasMore()) {
					cmdline = "DELAYED " + tokens.getRemaining();
				}
			}
			this.defineCpPFKey(keyNo, (cmdline != null) ? new EbcdicHandler(cmdline) : null);
			return false;
		}
		
		return this.emsg(EMSG_UNKNOWN_CP_COMMAND, subcmd);
	}
	
	private int getMemValue(String val) {
		int result = 0;
		int pos = 0;
		int len = val.length();
		while(pos < len) {
			char c = val.charAt(pos++);
			if (c >= '0' && c <= '9') {
				result = (result * 10) + (c - '0');
			} else if (c == 'M' && pos == (len) && result <= 16) {
				return result * 1024 * 1024;
			} else if (c == 'K' && pos == (len) && result <= (16 * 1024)) {
				return result * 1024;
			} else {
				return -1;
			}
		}
		return result;
	}
	
	/*
	 * SPOOL command
	 */

	private static final String EMSG_SPOOL_OPERAND_MISSING        = "DMKCSP026E OPERAND MISSING OR INVALID";
	private static final String EMSG_SPOOL_MISSING_INVALID_VADDR  = "DMKCSP022E VADDR MISSING OR INVALID";
	private static final String EMSG_SPOOL_INVALID_CLASS          = "DMKCSP028E CLASS MISSING OR INVALID";
	private static final String EMSG_SPOOL_DEV_NOT_FOUND          = "DMKCSP040E DEV %s DOES NOT EXIST";
	
	private boolean cmdCP_SPOOL(Tokenizer tokens) {
		String devname = tokens.nextUpper();
		if (devname == null) {
			return this.emsg(EMSG_SPOOL_MISSING_INVALID_VADDR);
		}
		
		final iSpoolDevice dev;
		final boolean allowClassStar;
		if ("PRT".equals(devname) || "00E".equals(devname)) {
			dev = this.prtDevice;
			allowClassStar = false;
		} else if ("PUN".equals(devname) || "00D".equals(devname)) {
			dev = this.punDevice;
			allowClassStar = false;
		} else if ("RDR".equals(devname) || "00C".equals(devname)) {
			dev = this.rdrDevice;
			allowClassStar = true;
		} else {
			return this.emsg(EMSG_CLOSE_MISSING_INVALID_VADDR);
		} 
		if (dev == null) {
			return this.emsg(EMSG_SPOOL_DEV_NOT_FOUND, devname);
		}
		
		String param = tokens.nextUpper();
		while (param != null) {
			if (isToken(param, "CLASS", 2)) {
				String devClass = tokens.nextUpper();
				if (devClass == null || devClass.isEmpty() || devClass.length() > 1) {
					return this.emsg(EMSG_SPOOL_INVALID_CLASS);
				}
				char classChar = devClass.charAt(0);
				if ((classChar >= 'A' && classChar <= 'Z') || (classChar >= '0' && classChar <= '9')) {
					dev.setSpoolClass(classChar);
				} else if (classChar == '*' && allowClassStar) {
					dev.setSpoolClass(classChar);
				} else {
					return this.emsg(EMSG_SPOOL_INVALID_CLASS);
				}
			} else if (isToken(param, "CONT", 2)) {
				dev.setSpoolCont(true);
			} else if (isToken(param, "NOCONT", 3)) {
				dev.setSpoolCont(false);
			} else if (isToken(param, "HOLD", 2)) {
				dev.setSpoolHold(true);
			} else if (isToken(param, "NOHOLD", 3)) {
				dev.setSpoolHold(false);
			} else if (isToken(param, "EOF")) {
				dev.setSpoolEof(true);
			} else if (isToken(param, "NOEOF", 3)) {
				dev.setSpoolEof(false);
			} else if (isToken(param, "COPY", 2)) {
				String copies = tokens.nextUpper();
				try {
					dev.setSpoolCopies(Integer.parseInt(copies));
				} catch (NumberFormatException e) {
					return this.emsg(EMSG_SPOOL_OPERAND_MISSING);
				}
			} else {
				// TODO: handle real spooling params: [[OFF [To|For][userid|*|SYSTEM]]
				return this.emsg(EMSG_SPOOL_OPERAND_MISSING);
			}
			param = tokens.nextUpper();
		}
		return false;
	}
	
	
	
	/*
	 * CLOSE command
	 */
	
	private static final String EMSG_CLOSE_MISSING_INVALID_VADDR  = "DMKCSQ022E VADDR MISSING OR INVALID";
	private static final String EMSG_CLOSE_INVALID_OPTION         = "DMKCSQ003E INVALID OPTION - %s";
	private static final String EMSG_CLOSE_DEV_NOT_FOUND          = "DMKCSQ040E DEV %s DOES NOT EXIST";
	private static final String EMSG_CLOSE_DISTCODE_INV_MISSING   = "DMKCSQ032E DISTCODE MISSING OR INVALID";
	
	private boolean cmdCP_CLOSE(Tokenizer tokens) {
		String devname = tokens.nextUpper();
		if (devname == null) {
			return this.emsg(EMSG_CLOSE_MISSING_INVALID_VADDR);
		}
		
		if ("PRT".equals(devname) || "00E".equals(devname) || "PUN".equals(devname) || "00D".equals(devname)) {
			iSpoolDevice dev = ("PRT".equals(devname) || "00E".equals(devname)) ? this.prtDevice : this.punDevice; 
			if (dev == null) {
				return this.emsg(EMSG_CLOSE_DEV_NOT_FOUND, devname);
			}
			boolean hold = dev.isSpoolHold();
			String param = tokens.nextUpper();
			while(param != null) {
				if (isToken(param, "PURGE", 2)) {
					dev.purge();
					return false;
				} else if (isToken(param, "HOLD", 2)) {
					hold = true;
				} else if (isToken(param, "NOHOLD", 3)) {
					hold = false;
				} else if (isToken(param, "DIST", 2)) {
					String distCode = tokens.nextUpper();
					if (distCode == null) {
						return this.emsg(EMSG_CLOSE_DISTCODE_INV_MISSING);
					}
				} else if (isToken(param, "NAME", 1)) {
					// TODO: react on hold
					dev.close(tokens.getRemaining());
					return false;
				} else {
					return this.emsg(EMSG_CLOSE_INVALID_OPTION, param);
				}
				param = tokens.nextUpper();
			}
			// TODO: react on hold
			dev.close(null);
			return false;
		} else if ("RDR".equals(devname) || "00C".equals(devname)) {
			// TODO: currently ignored as card sources auto-close
			System.err.printf("CP CLOSE unimplemented: %s\n", tokens.getLine());
			return false;
		} else {
			return this.emsg(EMSG_CLOSE_MISSING_INVALID_VADDR);
		}
		
	}
	
	/*
	 * TERMINAL command
	 */
	
	private static final String EMSG_TERM_MISSING_PARAM      = "DMKCFT026E OPERAND MISSING OR INVALID";
	private static final String EMSG_TERM_INVALID_PARAM      = "DMKCFT002E INVALID OPERAND - %s";
	private static final String EMSG_TERM_MODE_MISSING_PARAM = "DMKCFT026E OPERAND MISSING OR INVALID";
	
	// subcmd
	private boolean cmdCP_TERMINAL(Tokenizer tokens) {
		String subcmd = tokens.nextUpper();
		if (subcmd == null) {
			return this.emsg(EMSG_TERM_MISSING_PARAM);
		}
		
		if (isToken(subcmd, "MODE")) {
			String param = tokens.nextUpper();
			if (isToken(param, "VM")) {
				this.setTerminalAttnMode(false);
			} else if (isToken(param, "CP")) {
				this.setTerminalAttnMode(true);
			} else if (param == null) {
				return this.emsg(EMSG_TERM_MODE_MISSING_PARAM);
			} else {
				return this.emsg(EMSG_TERM_INVALID_PARAM, param);
			}
			return false;
		} else if (subcmd.startsWith("PF")) {
			// currently ignored...
			return false;
		}
		
		return this.emsg(EMSG_UNKNOWN_CP_COMMAND, subcmd);
	}
	
	/*
	 * QUERY command
	 */
	
	private static final String EMSG_QUERY_MISSING_PARAM         = "DMKCFC026E OPERAND MISSING OR INVALID";
	private static final String EMSG_QUERY_NOT_LOGGED_ON         = "DMKCQG045E %s NOT LOGGED ON";
	private static final String EMSG_QUERY_DEV_DOES_NOT_EXIST    = "DMKCQG040E DEV %03X DOES NOT EXIST";
	
	private boolean cmdCP_QUERY(Tokenizer tokens) {
		String subcmd = tokens.nextUpper();
		if (subcmd == null) {
			return this.emsg(EMSG_QUERY_MISSING_PARAM);
		}
		
		if (isToken(subcmd, "TIME", 1)) {
			this.writef("TIME IS %s\n", this.getTimeDateString());
			this.writeln(this.getUsedTimeString());
			return false;
		}
		
		// all other following commands require to be logged on
		if (this.vm == null) {
			// QUERY speciality: unknown subcommands are interpreted as user names
			// TODO: extend when multiuser capability is implemented  
			return this.emsg(EMSG_QUERY_NOT_LOGGED_ON, subcmd);
		}
		
		if (subcmd.startsWith("PF")) {
			// get key no
			int keyNo = this.getPfKeyNo(subcmd);
			if (keyNo >= 1 && keyNo <= 24) {
				EbcdicHandler setting = this.pfKeys[keyNo - 1];
				this.writef("PF%02d %s", keyNo, (setting != null) ? setting.getString() : "UNDEFINED");
				return false;
			} else if (keyNo == NO_PFNO) {
				for (int i = 1; i <= 24; i++) {
					EbcdicHandler setting = this.pfKeys[i - 1];
					this.writef("PF%02d %s", i, (setting != null) ? setting.getString() : "UNDEFINED");
				}
				return false;
			} // if not a PF-key => the user asked for a logged on user...
		}
		
		if ("USERID".equals(subcmd)) {
			this.writeln(this.vm.getIsoName());
			return false;
		}
		
		// from here: [VIRTUAL] [CONS|DASD] cuu

		if (isToken(subcmd, "VIRTUAL", 1)) {
			subcmd = tokens.nextUpper();
			if (subcmd == null || "ALL".equals(subcmd)) {
				// output all devices of the vm
				for (String line : this.vm.getQueryVirtualList(null)) {
					this.writeln(line);
				}
				return false;
			}
		}
		
		String reqType = null;
		if (isToken(subcmd, "DASD", 2)) {
			reqType = "DASD";
			subcmd = tokens.nextUpper();
			if (subcmd == null) {
				// output DASD devices of the vm
				for (String line : this.vm.getQueryVirtualList("DASD")) {
					this.writeln(line);
				}
				return false;
			}
		} else if (isToken(subcmd, "CONS", 2)) {
			reqType = "CONS";
			subcmd = tokens.nextUpper();
			if (subcmd == null) {
				// output CONS devices of the vm
				for (String line : this.vm.getQueryVirtualList("CONS")) {
					this.writeln(line);
				}
				return false;
			}
		}
		try {
			int cuu = Integer.parseInt(subcmd, 16);
			if (cuu <= 0xFFF) {
				String line = this.vm.getQueryVirtualString(cuu);
				if (line != null) {
					if (reqType == null || line.startsWith(reqType)) {
						this.writeln(line);
						return false;
					}	
				}
				return this.emsg(EMSG_QUERY_DEV_DOES_NOT_EXIST, cuu);
			} 
		} catch(NumberFormatException e) {
			// ignored: it's not a cuu, so continue with checking if it is a userid
		}
		
		
		// QUERY speciality: unknown subcommands are interpreted as user names
		// TODO: extend when multiuser capability is implemented  
		return this.emsg(EMSG_QUERY_NOT_LOGGED_ON, subcmd);
	}
	
	
	
	/*
	 * DEFINE command
	 */
	
	private static String EMSG_DEFINE_MISSING_PARAM         = "DMKDEF026E OPERAND MISSING OR INVALID";
	private static String EMSG_DEFINE_STORAGE_MISSING_PARAM = "DMKDEH025E STORAGE MISSING OR INVALID";
	
	private boolean cmdCP_DEFINE(Tokenizer tokens, boolean isSubCP) {
		String subcmd = tokens.nextUpper();
		if (subcmd == null) {
			return this.emsg(EMSG_DEFINE_MISSING_PARAM);
		}
		
		if (isToken(subcmd, "STORAGE", 4)) {
			String param = tokens.nextUpper();
			if (param == null) {
				return this.emsg(EMSG_DEFINE_STORAGE_MISSING_PARAM);
			}
			int memSize = this.getMemValue(param);
			if (memSize < (256*1024) || memSize > (16*1024*1024) ){
				return this.emsg(EMSG_DEFINE_STORAGE_MISSING_PARAM);
			}
			if (isSubCP) {
				// can't define storage here from a cp command entered from a VM READ
				this.resetInputQueueTo(tokens.getLine());
				throw new ReturnToMainLoopException();
			}
			this.vm.setSimulatedStorageSize(memSize);
			this.vmCanRun = false;
			return this.emsg(true, EMSG_NOT_RUNNABLE);
		}
		
		return this.emsg(EMSG_DEFINE_MISSING_PARAM);
	}
	
	/*
	 * LINK command
	 */
	
	private static final String EMSG_LINK_MISSING_PARAM      = "DMKLNK020E OPERAND MISSING OR INVALID";
	private static final String EMSG_LINK_NOT_FOUND          = "DMKLNK107E %s %03X NOT LINKED; NOT IN CP DIRECTORY";
	private static final String EMSG_LINK_DUPLICATE_DEV      = "DMKLNK110E %s %03X NOT LINKED; DASD %03X ALREADY DEFINED";
	
	private boolean cmdCP_LINK(Tokenizer tokens) {
		String user = tokens.nextUpper();
		if (user == null) { return this.emsg(EMSG_LINK_MISSING_PARAM); }
		if (isToken(user, "TO", 1)) {
			user = tokens.nextUpper();
			if (user == null) { return this.emsg(EMSG_LINK_MISSING_PARAM); }
		}
		
		String cuuString = tokens.nextUpper();
		if (cuuString == null) { return this.emsg(EMSG_LINK_MISSING_PARAM); }
		int srcCuu = getCuu(cuuString);
		
		cuuString = tokens.nextUpper();
		if (cuuString == null) { return this.emsg(EMSG_LINK_MISSING_PARAM); }
		if (isToken(cuuString, "AS", 1)) {
			cuuString = tokens.nextUpper();
			if (cuuString == null) { return this.emsg(EMSG_LINK_MISSING_PARAM); }
		}
		int trgCuu = getCuu(cuuString);
		
		// currently ignored:  [mode] [[PASS=]password]
		
		try {
			this.linkSharedDrive(trgCuu, user, srcCuu);
		} catch(DuplicateDeviceException e) {
			return this.emsg(EMSG_LINK_DUPLICATE_DEV, user, srcCuu, trgCuu);
		} catch (CmdError e) {
			return this.emsg(EMSG_LINK_NOT_FOUND, user, srcCuu);
		}
		this.writef("DASD %03X LINKED R/O", trgCuu);
		
		return false;
	}

	/*
	 * DETACH command
	 */
	
	private static final String EMSG_DETACH_MISSING_PARAM    = "DMKVDD022E OPERAND MISSING OR INVALID";
	private static final String EMSG_DETACH_UNDEFINED_DEV    = "DMKVDD040E DEV %03X DOES NOT EXIST";
	private static final String EMSG_DETACH_CONSOLE_DEV      = "DMKVDD041E DEV %03X IS CONSOLE, NOT DETACHED";
	
	private boolean cmdCP_DETACH(Tokenizer tokens) {
		String cuuString = tokens.nextUpper();
		if (cuuString == null) { return this.emsg(EMSG_DETACH_MISSING_PARAM); }
		int cuu = getCuu(cuuString);
		
		DeviceHandler dev = this.vm.getDevice(cuu);
		if (dev == null) {
			return this.emsg(EMSG_DETACH_UNDEFINED_DEV, cuu);
		}
		if (dev == this.vm.getDevice(-1)) {
			return this.emsg(EMSG_DETACH_CONSOLE_DEV, cuu);
		}
		this.saveDeviceBeforeDetach(cuu);
		this.vm.removeDevice(cuu);
		this.writef("%s %03X DETACHED", dev.getDevice().getCpDeviceTypeName(), cuu);
		
		return false;
	}
}
