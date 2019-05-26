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
import static dev.hawala.vm370.CommandTokens.getHexloc;
import static dev.hawala.vm370.CommandTokens.getInt;
import static dev.hawala.vm370.CommandTokens.isToken;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import dev.hawala.vm370.CommandTokens.CmdError;
import dev.hawala.vm370.CommandTokens.Tokenizer;
import dev.hawala.vm370.card.CardDev2540Puncher;
import dev.hawala.vm370.card.CardDev2540Reader;
import dev.hawala.vm370.card.CardSourceAsciiFile;
import dev.hawala.vm370.card.CardSourceEbcdicFile;
import dev.hawala.vm370.card.iCardSource;
import dev.hawala.vm370.dasd.iDasd;
import dev.hawala.vm370.dasd.ckdc.CkdcDrive;
import dev.hawala.vm370.dasd.fba.FbaDrive;
import dev.hawala.vm370.ebcdic.EbcdicHandler;
import dev.hawala.vm370.mecaff.Vm3270Console.Attr;
import dev.hawala.vm370.mecaff.Vm3270Console.Color;
import dev.hawala.vm370.mecaff.Vm3270Console.ConsoleElement;
import dev.hawala.vm370.print.PrintDev1403;
import dev.hawala.vm370.spool.iSpoolDevice;
import dev.hawala.vm370.tape.TapeDrive;
import dev.hawala.vm370.vm.iSvcInterceptor;
import dev.hawala.vm370.vm.device.DeviceHandler;
import dev.hawala.vm370.vm.device.iDevice;
import dev.hawala.vm370.vm.machine.CPVirtualMachine;
import dev.hawala.vm370.vm.machine.Cpu370Bc;
import dev.hawala.vm370.vm.machine.DuplicateDeviceException;
import dev.hawala.vm370.vm.machine.NamedSegment;
import dev.hawala.vm370.vm.machine.iCommandExecutor;
import dev.hawala.vm370.vm.machine.iProcessorEventTracker;

/**
 * Implementation of emx370 emulator commands used to build up and
 * control the function of a single virtual machine.
 * 
 * <p>
 * Emulator commands are introduced by a : (colon) to differentiate them
 * from "original" CP commands.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015,2016
 *
 */
public abstract class CPCommandInterpreterEmulator extends iUserConsole implements iCommandExecutor {

	// the virtual machine for a logged on user or null if no user is currently logged on
	protected CPVirtualMachine vm = null;
	
	// if 'vm' != null: is this vm in a runnable state?
	// (runnable: some program was IPL-ed and did not enter a disabled wait state
	//            or some other invalid state, like a recent DEFINE STORAGE) 
	protected boolean vmCanRun = false;
	
	// milli-seconds since the current virtual machine was created (i.e. logged on)
	private long msecConnected = 0;
	
	// exception used if a command cannot be handled at the current nesting level (sub-CP
	// loop) to jump to the top level interpreter level and reissue the command there
	public static class ReturnToMainLoopException extends RuntimeException {
		private static final long serialVersionUID = 6501145662707890610L; // keep the eclipse editor happy
	}
	
	// reduce pending commands to the one passed in the exception and process this command
	protected abstract void resetInputQueueTo(String command);
	
	
	/*
	 * the emulator command interpreter
	 */
	
	private int lastRC = 0; // returncode of the last executed CP command
	
	protected void setLastRC(int rc) {
		this.lastRC = rc;
	}
	
	protected int getLastRC() {
		return this.getLastRC(true);
	}
	
	protected int getLastRC(boolean reset) {
		int rc = this.lastRC;
		if (reset) { this.lastRC = 0; }
		return rc;
	}
	
	/**
	 * Prepare a CP error message using the arguments and the current SET EMSG settings,
	 * write it out (resp. buffer it for DIAG-x08) and extract the CP return code to the
	 * "last CP RC" and return the passed directive for "leave VM READ sub-CP-command-loop".
	 * 
	 * @param leaveLoop is a CP command loop inside a VM READ to be left?
	 * @param pattern the message pattern with the mandatory(!) CP 10-bytes error code with
	 *   the error number at offsets 6..8 at the beginning of the message line. 
	 * @param args the arguments for the string pattern.
	 * @return the value of parameter 'leaveLoop'.
	 */
	protected boolean emsg(boolean leaveLoop, String pattern, Object... args) {
		String line = String.format(pattern, args);
		if (line.length() < 12) {
			this.setLastRC(-1);
			this.writeln(line);
		} else {
			String rcString = line.substring(6,9);
			int rc = -1;
			try {
				rc = Integer.parseInt(rcString);
			} catch(NumberFormatException e) {
				// ignored
			}
			this.setLastRC(rc);
			if(this.vm != null) {
				if (!this.vm.isVmmText()) { line = line.substring(0, 10); }
				if (!this.vm.getVmmCode()) { if (line.length() > 10) { line = line.substring(10); } else { line = ""; } }
			}
			line = line.trim();
			if (line.length() > 0) { this.writeln(line); }
		}
		
		return leaveLoop;
	}
	
	/**
	 * Prepare a CP error message using the arguments and the current SET EMSG settings,
	 * write it out (resp. buffer it for DIAG-x08) and extract the CP return code to the
	 * "last CP RC" and return false for "leave VM READ sub-CP-command-loop".
	 *     
	 * 
	 * @param pattern the message pattern with the mandatory(!) CP 10-bytes error code with
	 *   the error number at offsets 6..8 at the beginning of the message line. 
	 * @param args the arguments for the string pattern.
	 * @return always 'false'.
	 */
	protected boolean emsg(String pattern, Object... args) {
		return this.emsg(false, pattern, args);
	}
	
	protected abstract boolean executeCPCommand(String line, boolean isSubCP, boolean suppressUnknownCommandMessage);
	
	private static final SimpleDateFormat DATEFMT = new SimpleDateFormat("MM/dd/yy");
	private static final SimpleDateFormat TIMEFMT = new SimpleDateFormat("HH:mm:ss");
	private String[] WEEKDAY = {"SUNDAY", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY"};
	
	// TODO: TimeZone is not provided and issued as: (local)
	@SuppressWarnings("deprecation")
	protected String getTimeDateString() {
		Date now = new Date();
		return String.format("%s LOCAL %s %s", TIMEFMT.format(now), WEEKDAY[now.getDay()], DATEFMT.format(now));
	}
	
	private String getTimespanString(long msecs) {
		int seconds = (int)(msecs / 1000);
		int secPart = seconds % 60;
		int minPart = ((seconds - secPart) / 60) % 60;
		int hourPart = seconds / 3600;
		return String.format("%02d:%02d:%02d", hourPart, minPart, secPart);
	}
	
	protected String getUsedTimeString() {
		if (this.vm == null) {
			return "NOT CONNECTED / LOGGED ON";
		}
		
		long connectMSecs =  System.currentTimeMillis() - this.msecConnected;
		return   "CONNECT= " + this.getTimespanString(connectMSecs)
			   + " VIRTCPU= " + this.getTimespanString(this.vm.getVirtualCpuMicrosecs())
			   + " TOTCPU= " + this.getTimespanString(this.vm.getRealCpuMicrosecs());
	}
	
	private static class DeviceEventTracker implements iProcessorEventTracker {
		
		private final iProcessorEventTracker realLogger;
		
		private boolean doLog = false;
		
		public DeviceEventTracker(iProcessorEventTracker logger) {
			this.realLogger = logger;
		}
		
		public void setLogging(boolean doit) { this.doLog = doit; }

		@Override
		public void logLine(String line, Object... args) {
			if (this.doLog) { this.realLogger.logLine(line, args); }
		}
		
	};
	
	private static abstract class WritableDevice {
		
		protected final int cuu;
		
		public WritableDevice(int cuu) {
			this.cuu = cuu;
		}
		
		public int getCuu() { return this.cuu; }
		
		public abstract boolean needsSaving(boolean detaching); // detaching == false => LOGOFF of the VM
		
		// returns: failed?
		public abstract String save();
	}
	
	private static class WritableTape extends WritableDevice {
		
		private final TapeDrive drive;
		
		public WritableTape(TapeDrive drive, int unit) {
			super(unit);
			this.drive = drive;
		}

		@Override
		public boolean needsSaving(boolean detaching) {
			return this.drive.needsSaving();
		}

		@Override
		public String save() {
			this.drive.dismountTapeFile();
			return null;
		}
	}
	
	private static class WritableCkdc extends WritableDevice {
		private final iDasd drive;
		private final String basefileName;
		
		public WritableCkdc(iDasd d, String fn, int unit) {
			super(unit);
			this.drive = d;
			this.basefileName = fn;
		}
		
		public boolean needsSaving(boolean detaching) {
			return !detaching && this.drive.needsSaving();
		}
		
		// returns: failed?
		public String save() {
			String oldDeltaFn = this.basefileName + ".delta_old";
			String deltaFn = this.basefileName + ".delta";
			
			String what = "delete old .delta_old file";
			try {
				File oldDelta = new File(oldDeltaFn);
				if (oldDelta != null && oldDelta.exists()) {
					oldDelta.delete();
				}
				
				what = "rename .delta to .old_delta";
				File delta = new File(deltaFn);
				if (delta != null && delta.exists()) {
					oldDelta = new File(oldDeltaFn);
					oldDelta.renameTo(oldDelta);
				}
				
				what = "save drive changes to .delta";
				this.drive.saveTo(deltaFn, null);
				
				what = "delete .delta_old file";
				oldDelta = new File(oldDeltaFn);
				if (oldDelta != null && oldDelta.exists()) {
					oldDelta.delete();
				}
			} catch (Exception e) {
				return String.format("Error: unable to %s (%s)\n", what, e.getMessage());
			}
			
			return null;
		}
	}
	
	private static iDasd loadDrive(String basefileName, boolean writeProtected) throws Exception {
		if (basefileName == null || basefileName.length() == 0) {
			throw new CmdError("no base file name specified for drive");
		}
		
		File baseFile = new File(basefileName);
		if (baseFile == null || !baseFile.exists() || !baseFile.isFile() || !baseFile.canRead()) {
			throw new CmdError("specified base file for drive not existent or readable");
		}
		
		String deltafileName = basefileName + ".delta";
		File deltaFile = new File(deltafileName);
		if (deltaFile == null || !deltaFile.exists() || !deltaFile.isFile() || !deltaFile.canRead()) {
			deltaFile = null;
			deltafileName = null;
		} else if (!writeProtected && !deltaFile.canWrite()) {
			writeProtected = true;
		}
		
		iDasd drive
			= (basefileName.endsWith(".fba"))
			? new FbaDrive(basefileName)
			: new CkdcDrive(deltafileName, basefileName, null);
		if (writeProtected) { drive.setWriteProtected(); }
		
		return drive;
	}
	
	private static Map<String, iDasd> sharedDrives = new HashMap<String, iDasd>();
	
	public static String loadSharedDrive(String username, int cuu, String basefileName) throws Exception {
		iDasd drive = loadDrive(basefileName, true);
		String key = String.format("%s.%03X", username, cuu);
		synchronized(sharedDrives) {
			sharedDrives.put(key, drive);
		}
		return key;
	}
	
	public void linkSharedDrive(int cuu, String identifier) throws DuplicateDeviceException {
		if (identifier == null) { throw new CmdError(EM_MISSING_PARAM, "shared-identifier"); }
		
		if (this.ownDrives.containsKey(identifier)) {
			DeviceHandler dev = this.vm.createDeviceHandler(this.ownDrives.get(identifier), cuu, this.deviceEventTracker);
			((iDasd)dev.getDevice()).setEventTracker(this.deviceEventTracker);
			this.vm.addDevice(dev);
			return;
		}
		
		synchronized(sharedDrives) {
			if (!sharedDrives.containsKey(identifier)) {
				throw new CmdError("Shared DASD not defined: %s", identifier);
			}
			DeviceHandler dev = this.vm.createDeviceHandler(sharedDrives.get(identifier), cuu, this.deviceEventTracker);
			this.vm.addDevice(dev);
		}
	}
	
	public void linkSharedDrive(int cuu, String username, int userCuu) throws DuplicateDeviceException {
		String identifier = String.format("%s.%03X", username, userCuu);
		this.linkSharedDrive(cuu, identifier);
	}
	
	private Map<String, iDasd> ownDrives = new HashMap<String, iDasd>();
	
	private ArrayList<WritableDevice> writableDevices = new ArrayList<WritableDevice>();
	
	protected static final int RDR_DEVICE = 12; // 0x00C
	protected CardDev2540Reader rdrDevice = null;
	protected DeviceHandler rdrDeviceHandler = null;
	
	protected static final int PUN_DEVICE = 13; // 0x00D
	protected iSpoolDevice punDevice = null;
	protected DeviceHandler punDeviceHandler = null; 
	
	protected static final int PRT_DEVICE = 14; // 0x00E
	protected iSpoolDevice prtDevice = null;
	protected DeviceHandler prtDeviceHandler = null;
	
	private DeviceEventTracker deviceEventTracker = null;
	
	private void dasdLoadDisk(int cuu, String basefileName, boolean writeProtected) throws Exception {
		if (this.vm.getDevice(cuu) != null) {
			throw new CmdError("device %03D already present in virtual machine", cuu);
		}
		
		if (this.deviceEventTracker == null) {
			this.deviceEventTracker = new DeviceEventTracker(this.vm.cpu);
		}
		
		iDasd drive = loadDrive(basefileName, writeProtected);
		drive.setEventTracker(this.deviceEventTracker);
		if (drive.isWriteProtected() && !writeProtected) {
			this.writef("Warning: writable drive %03X has R/O delta file, forcing write-protection", cuu);
		}
		
		DeviceHandler dev = this.vm.createDeviceHandler(drive, cuu, this.deviceEventTracker);
		this.vm.addDevice(dev);
		if (!writeProtected) {
			this.writableDevices.add(new WritableCkdc(drive, basefileName, cuu));
		}
		
		String identifier = String.format("%s.%03X", this.vm.getIsoName(), cuu);
		this.ownDrives.put(identifier, drive);
	}
	
	protected void saveDeviceBeforeDetach(int cuu) {
		for (WritableDevice d : this.writableDevices) {
			if (d.getCuu() == cuu) {
				if (d.needsSaving(true)) { // true <=> detaching device
					String msg = d.save();
					if (msg != null) {
						this.writeln(msg);
					}
				}
				this.writableDevices.remove(d);
				return;
			}
		}
	}
	
	private void saveDasdDrive(int cuu) {
		for (WritableDevice d : this.writableDevices) {
			if (d.getCuu() == cuu) {
				String msg = d.save();
				if (msg != null) {
					this.writeln(msg);
				}
				return;
			}
		}
		this.writef("CUU %03X not attached or not writable\n", cuu);
	}
	
	private void saveAllDrives() {
		for (WritableDevice d : this.writableDevices) {
			if (d.needsSaving(false)) { // false <=> logoff
				String msg = d.save();
				if (msg != null) {
					this.writeln(msg);
				}
			}
		}
	}
	
	private void segmentLoad(String name, String fileName, int pageCount, int loadAtPage, boolean iplable) throws IOException {
		File segmentFile = new File(fileName);
		if (segmentFile == null || !segmentFile.exists() || !segmentFile.isFile() || !segmentFile.canRead()) {
			throw new CmdError("specified segment file not existent or readable");
		}
		
		NamedSegment segment = new NamedSegment(name,  fileName, pageCount, loadAtPage * 4096, iplable);
		this.vm.addKnownNamedSegment(segment);
	}
	
	private static final String EM_MISSING_PARAM = "Missing parameter %s";
	
	private long lastInfo = 0;
	
	private static String getAvgMillisecs(long nanoSecs, long count) {
		if (count == 0) { return "           "; }
		long microSecs = nanoSecs / (count * 1000);
		long mSecs = microSecs / 1000;
		long frac = microSecs % 1000;
		return String.format("%7d.%03d", mSecs, frac);
	}
	
	private static class SvcLogInterceptor implements iSvcInterceptor {
		
		private final byte[] mem = new byte[8];
		private final EbcdicHandler svc202tmp = new EbcdicHandler();
		
		private void dump8(int at, Cpu370Bc cpu) {
			for (int i = 0; i < 8; i++) {
				mem[i] = cpu.peekMainMemByte(at + i);
			}
			svc202tmp.reset().appendEbcdic(mem, 0, 8);
			String s = svc202tmp.toString();
			System.out.printf(
				"     %c%c%c%c%c%c%c%c   0x %02X %02X %02X %02X %02X %02X %02X %02X\n",
				s.charAt(0), s.charAt(1), s.charAt(2), s.charAt(3),
				s.charAt(4), s.charAt(5), s.charAt(6), s.charAt(7),
				mem[0], mem[1], mem[2], mem[3],
				mem[4], mem[5], mem[6], mem[7]);
		}

		@Override
		public boolean interceptSvc(int svcNo, Cpu370Bc cpu) {
			System.out.printf("** SVC %d @ ia = 0x%06X\n", svcNo, cpu.getPswInstructionAddress());
			if (svcNo != 202) { return false; }
			
			int svcData = cpu.getGPR(1) & 0x00FFFFFF;
			System.out.printf(
					"     r1 = 0x%08X\n",
					svcData);
			
			// function
			dump8(svcData, cpu);
			svcData += 8;
			
			// first 4 double-words...
			for (int i = 0; i < 4; i++) {
				if (cpu.peekMainMemByte(svcData) == (byte)0xFF) { break; }
				dump8(svcData, cpu);
				svcData += 8;
			}
			
			System.out.println("** -------------------------------------");
			
			return false;
		}
	}
	
	private SvcLogInterceptor svcLogInterceptor = new SvcLogInterceptor();
	
	private class VmFile {
		private final EbcdicHandler fn;
		private final EbcdicHandler ft;
		private final boolean isRecfmF;
		private final int lrecl;
		
		public VmFile(EbcdicHandler filename, EbcdicHandler filetype, boolean isFixed, int lineLen) {
			this.fn = new EbcdicHandler().append(filename);
			this.ft = new EbcdicHandler().append(filetype);
			this.isRecfmF = isFixed;
			this.lrecl = lineLen;
		}
		
		private final ArrayList<byte[]> lines = new ArrayList<>();

		public EbcdicHandler getFn() {
			return fn;
		}

		public EbcdicHandler getFt() {
			return ft;
		}

		public boolean isRecfmF() {
			return isRecfmF;
		}

		public int getLrecl() {
			return lrecl;
		}

		public ArrayList<byte[]> getLines() {
			return lines;
		}
		
		public void append(byte[] buffer, int len) {
			if (len < 1) { return; }
			if (len > buffer.length) { len = buffer.length; }
			byte[] rec = new byte[len];
			System.arraycopy(buffer, 0, rec, 0, len);
			this.lines.add(rec);
		}
	}
	
	private final static ArrayList<VmFile> vmFileExchange = new ArrayList<>(); 
	
	protected boolean executeEmulatorCommand(String line, boolean isSubCP) {
		Tokenizer tokens = new Tokenizer(line);
		String cmd = tokens.nextUpper();
		
		try {
		
			if (this.vm == null) {
				throw new CmdError("virtual machine not active (no user logged on)");
			}
			
			// :CONS PF <nn> [cmd]
			// :CONS FLowmode ON|OFf
			// :CONS ATtr NORMal|ECHOinput|FSBG|CONSolestate|CMDInput Default|Blue|Red|Pink|Green|Turquoise|Yellow|White [HIGHLight]
			if (cmd.equals(":CONS")) {
				String subcmd = tokens.nextUpper();
				if (subcmd == null) { throw new CmdError(EM_MISSING_PARAM, "subcommand for :CONS"); }
				
				String arg = tokens.nextUpper();
				
				if (subcmd.equals("PF")) {
					if (arg == null) {
						for (int i = 1; i <= 24; i++) {
							String pfCmd = this.getMecaffPFKey(i);
							if (pfCmd != null && pfCmd.length() > 0) {
								this.writef(" PF%02d: %s\n", i, pfCmd);
							}
						}
						return false;
					}
					int pfKey = getInt(arg);
					String pfCmd = tokens.getRemaining();
					this.setMecaffPFKey(pfKey, pfCmd);
				} else if (isToken(subcmd, "FLOWMODE", 2)) {
					if (arg == null) { throw new CmdError(EM_MISSING_PARAM, "subcommand argument for :CONS FLOWMODE"); }
					if (arg.equals("ON")) {
						this.setMecaffFlowMode(true);
					} else if (isToken(arg, "OFF", 3)) {
						this.setMecaffFlowMode(false);
					} else {
						throw new CmdError("Invalid parameter for :CONS FLOWMODE: " + arg);
					}
				} else if (isToken(subcmd, "ATTR", 2)) {
					if (arg == null) { throw new CmdError(EM_MISSING_PARAM, "subcommand argument for :CONS ATTR"); }
					ConsoleElement elem = null;
					if (isToken(arg, "NORNAL", 4)) { elem = ConsoleElement.OutNormal; }
					if (isToken(arg, "ECHOINPUT", 4)) { elem = ConsoleElement.OutEchoInput; }
					if (isToken(arg, "FSBG")) { elem = ConsoleElement.OutFsBg; }
					if (isToken(arg, "CONSOLESTATE", 4)) { elem = ConsoleElement.ConsoleState; }
					if (isToken(arg, "CMDINPUT", 4)) { elem = ConsoleElement.CmdInput; }
					if (elem == null) { throw new CmdError("Invalid parameter for :CONS ATTR: " + arg); }
					
					arg = tokens.nextUpper();
					if (arg == null) { throw new CmdError(EM_MISSING_PARAM, "color argument for :CONS ATTR"); }
					Color color = null;
					if (isToken(arg, "DEFAULT", 1)) { color = Color.Default; }
					if (isToken(arg, "BLUE", 1)) { color = Color.Blue; }
					if (isToken(arg, "RED", 1)) { color = Color.Red; }
					if (isToken(arg, "PINK", 1)) { color = Color.Pink; }
					if (isToken(arg, "GREEN", 1)) { color = Color.Green; }
					if (isToken(arg, "TURQUOISE", 1)) { color = Color.Turquoise; }
					if (isToken(arg, "YELLOW", 1)) { color = Color.Yellow; }
					if (isToken(arg, "WHITE", 1)) { color = Color.White; }
					if (color == null) { throw new CmdError("Invalid color for :CONS ATTR: " + arg); }
					
					arg = tokens.nextUpper();
					boolean highlight = false;
					if (arg != null) {
						if (isToken(arg, "HIGHLIGHT", 5)) { highlight = true; }
						else {
							throw new CmdError("Invalid parameter for :CONS ATTR: " + arg);
						}
					}
					
					Attr attr = new Attr(color, highlight);
					this.setMecaffAttr(elem, attr);
				} else {
					throw new CmdError("Invalid :CONS subcommand: " + subcmd);
				}
				return false;
			}
			
			// :RDR CREate
			// :RDR ENQueue [ [ASCii] [LENgth n] [TRUNCate] | EBCdic] [CLass c] <filename>
			if (cmd.equals(":RDR")) {
				String arg = tokens.nextUpper();
				if (arg == null) { throw new CmdError(EM_MISSING_PARAM, "subcommand for :RDR"); }
				
				if (isToken(arg, "CREATE", 3)) {
					if (this.rdrDevice != null) {
						throw new CmdError("RDR Device %03X already attached", RDR_DEVICE);
					}
					DeviceHandler dev00C = this.vm.getDevice(RDR_DEVICE);
					if (dev00C != null) {
						throw new CmdError("Device %03X already defined, but not a RDR", RDR_DEVICE);
					}
					this.rdrDevice = new CardDev2540Reader(this.deviceEventTracker);
					this.rdrDeviceHandler = this.vm.createDeviceHandler(rdrDevice, RDR_DEVICE, this.deviceEventTracker);
					this.vm.addDevice(this.rdrDeviceHandler);
					return false;
				} else if (isToken(arg, "ENQUEUE", 3)) {
					if (this.rdrDevice == null) {
						throw new CmdError("No RDR device present in VM");
					}
					iCardSource source = null;
					String filename = "not specified";
					try {
						String opt = tokens.peekNextUpper();
						boolean hadAsciiOption = false;
						boolean hadEbcdicOption = false;
						int cardlen = 80;
						char spoolClass = 'A';
						boolean breakLongLines = true;
						boolean fillWithZeros = false;
						while (true) {
							if (isToken(opt, "ASCII", 3)) {
								tokens.next(); // consume the token
								hadAsciiOption = true;
								opt = tokens.peekNextUpper();
							} else if (isToken(opt, "CLASS", 2)) {
								tokens.next(); // consume the token
								String classString = tokens.nextUpper();
								if (classString == null || classString.length() > 1) {
									throw new CmdError("Missing or invalid spool class specification for :RDR ENQU");
								}
								spoolClass = classString.charAt(0);
								opt = tokens.peekNextUpper();
							} else if (isToken(opt, "LENGTH", 3)) {
								tokens.next(); // consume the token
								hadAsciiOption = true;
								arg = tokens.nextUpper();
								if (arg == null) { throw new CmdError("Missing value for LENGTH parameter of :RDR:ENQUEUE"); }
								cardlen = getInt(arg);				
								opt = tokens.peekNextUpper();
							} else if (isToken(opt, "TRUNCATE", 5)) {
								tokens.next(); // consume the token
								hadAsciiOption = true;
								breakLongLines = false;
								opt = tokens.peekNextUpper();
							} else if (isToken(opt, "EBCDIC", 3)) {
								tokens.next(); // consume the token
								hadEbcdicOption = true;
								opt = tokens.peekNextUpper();
							} else if (isToken(opt, "ZEROFILL", 4)) {
								tokens.next(); // consume the token
								hadEbcdicOption = true;
								fillWithZeros = true;
								opt = tokens.peekNextUpper();
							} else {
								if (hadAsciiOption && hadEbcdicOption) {
									throw new CmdError("Both ASCII and EBCDIC options specified for :RDR ENQUEUE");
								}
								filename = tokens.getRemaining();
								if (filename == null) {
									throw new CmdError("Missing filename for :RDR ENQUEUE");
								}
								if (hadEbcdicOption) {
									source = new CardSourceEbcdicFile(filename, spoolClass, fillWithZeros);
								} else {
									source = new CardSourceAsciiFile(filename, spoolClass, cardlen, breakLongLines);
								}
								break;
							}
						}
					} catch (CmdError e) {
						throw e;
					} catch(Exception e) {
						throw new CmdError("Error accessing file for :RDR ENQUEUE, name: %s", filename);
					}
					if (source == null) {
						throw new CmdError("Missing parameters for :RDR ENQUEUE");
					}
					this.rdrDevice.enqueueCardSource(source);
					return false;
				} else {
					throw new CmdError("Invalid :RDR subcommand: " + arg);
				}
			}
			
			// :PRT CREate directory
			if (cmd.equals(":PRT")) {
				String arg = tokens.nextUpper();
				if (arg == null) { throw new CmdError(EM_MISSING_PARAM, "subcommand for :PRT"); }
				
				if (isToken(arg, "CREATE", 3)) {
					if (this.prtDevice != null) {
						throw new CmdError("PRT Device %03X already attached", PRT_DEVICE);
					}
					DeviceHandler dev00E = this.vm.getDevice(PRT_DEVICE);
					if (dev00E != null) {
						throw new CmdError("Device %03X already defined, but not a PRT", PRT_DEVICE);
					}
					String dirName = tokens.getRemaining();
					if (dirName == null) { throw new CmdError(EM_MISSING_PARAM, "directory name for :PRT CREATE"); }
					this.prtDevice = new PrintDev1403(this.deviceEventTracker, this.vm.getIsoName(), dirName);
					this.prtDeviceHandler = this.vm.createDeviceHandler(prtDevice, PRT_DEVICE, this.deviceEventTracker);
					this.vm.addDevice(this.prtDeviceHandler);
					return false;
				} else {
					throw new CmdError("Invalid :PRT subcommand: " + arg);
				}
			}
				
			// :PUN CREate directory
			if (cmd.equals(":PUN")) {
				String arg = tokens.nextUpper();
				if (arg == null) { throw new CmdError(EM_MISSING_PARAM, "subcommand for :PUN"); }
				
				if (isToken(arg, "CREATE", 3)) {
					if (this.punDevice != null) {
						throw new CmdError("PUN Device %03X already attached", PUN_DEVICE);
					}
					DeviceHandler dev00D = this.vm.getDevice(PUN_DEVICE);
					if (dev00D != null) {
						throw new CmdError("Device %03X already defined, but not a PUN", PUN_DEVICE);
					}
					String dirName = tokens.getRemaining();
					if (dirName == null) { throw new CmdError(EM_MISSING_PARAM, "directory name for :PUN CREATE"); }
					this.punDevice = new CardDev2540Puncher(this.deviceEventTracker, this.vm.getIsoName(), dirName);
					this.punDeviceHandler = this.vm.createDeviceHandler(punDevice, PUN_DEVICE, this.deviceEventTracker);
					this.vm.addDevice(this.punDeviceHandler);
					return false;
				} else {
					throw new CmdError("Invalid :PUN subcommand: " + arg);
				}
			}
			
			// :TAPe <cuu> ATTach
			// :TAPe <cuu> MOUnt [WITHRing|WRitable] <filename>
			// :TAPe <cuu> CREate <filename>
			// :TAPe <cuu> DISmount
			// :TAPe <cuu> INFo
			if (isToken(cmd, ":TAPE", 4)) {
				String arg = tokens.nextUpper();
				if (arg == null) { throw new CmdError(EM_MISSING_PARAM, "cuu"); } 
				
				int cuu = getCuu(arg);
				DeviceHandler devHandler = this.vm.getDevice(cuu);
				
				arg = tokens.nextUpper();
				if (arg == null) { throw new CmdError(EM_MISSING_PARAM, "subcommand for :TAPE"); }
				
				boolean doAttach = false;
				boolean doMount = false;
				boolean doDismount = false;
				boolean doInfo = false;
				boolean doCreateIfNew = false;
				boolean doWritable = false;
				
				if (isToken(arg, "ATTACH", 3)) {
					doAttach = true;
				} else if (isToken(arg, "MOUNT", 3)) {
					doMount = true;
				} else if (isToken(arg, "CREATE", 3)) {
					doMount = true;
					doCreateIfNew = true;
					doWritable = true;
				} else if (isToken(arg, "DISMOUNT", 3)) {
					doDismount = true;
				} else if (isToken(arg, "INFO", 3)) {
					doInfo = true;
				} else {
					throw new CmdError("Invalid :TAPE subcommand: " + arg);
				}
				
				if (doDismount || doInfo) {
					if (devHandler == null) {
						throw new CmdError("Device %03X is not attached", cuu);
					}
					iDevice dev = devHandler.getDevice();
					if (dev instanceof TapeDrive) {
						TapeDrive tape = (TapeDrive)dev;
						if (!tape.hasMountedTape()) {
							this.writef("Tape %03X: no tape file mounted\n", cuu);
							return false;
						}
						if (doDismount) {
							tape.dismountTapeFile();
							this.writef("Tape %03X: tape dismounted\n", cuu);
						} else {
							this.writef(
									"Tape %03X: %s%s %d blocks / %d bytes, file: %s\n",
									cuu,
									(tape.isReadOnly()) ? "R/O" : "R/W",
									(tape.isModified()) ? " (modified)" : "",
									tape.getCurrBlockCount(),
									tape.getCurrBytes(),
									tape.getTapeFilename());
						}
						return false;
					}
					throw new CmdError("Device at %03X not not a tape device", cuu);
				}
				
				if (devHandler != null && !(devHandler.getDevice() instanceof TapeDrive)) {
					throw new CmdError("Device at %03X not not a tape device", cuu);
				}
				
				if (devHandler == null) {
					TapeDrive tapeDrive = new TapeDrive(this.deviceEventTracker);
					devHandler = this.vm.createDeviceHandler(tapeDrive, cuu, this.deviceEventTracker);
					this.vm.addDevice(devHandler);
					this.writableDevices.add(new WritableTape(tapeDrive, cuu));
					this.writef("TAPE %03X ATTACHED\n", cuu);
				} else if (doAttach) {
					this.writef("Tape %03X already attached\n", cuu);
					return false;
				}
				
				if (doAttach) {
					return false; // here we are sure that there is a tape device at cuu
				}
				
				// so this is MOUNT or CREATE
				String tapefileName = tokens.next();
				if (tapefileName != null
					&& doMount
					&& (isToken(tapefileName.toUpperCase(), "WITHRING", 5)
						|| isToken(tapefileName.toUpperCase(), "WRITABLE", 3))) {
					doWritable = true;
					tapefileName = tokens.next();
				}
				if (tapefileName == null) { throw new CmdError(EM_MISSING_PARAM, "tape-filename"); }
				TapeDrive drive = (TapeDrive)devHandler.getDevice();
				StringBuilder sb = new StringBuilder();
				drive.mountTapefile(tapefileName, doCreateIfNew, doWritable, sb);
				if (sb.length() > 0) {
					this.writef("%s\n", sb.toString());
				}
				if (drive.hasMountedTape()) {
					this.writef("Tape %03X: tape mounted\n", cuu);
				}
				return false;
			}
			
			// :DASDLOAD [readonly] <cuu> <basefile-spec>
			if (cmd.equals(":DASDLOAD")) {
				String arg = tokens.nextUpper();
				if (arg == null) { throw new CmdError(EM_MISSING_PARAM, "cuu"); }
				
				boolean writeProtected = false;
				if (isToken(arg, "READONLY") || isToken(arg, "RO")) {
					writeProtected = true;
					arg = tokens.nextUpper();
				}
				if (arg == null) { throw new CmdError(EM_MISSING_PARAM, "cuu"); }
				int cuu = getCuu(arg);
				
				String basefileName = tokens.next();
				if (basefileName == null) { throw new CmdError(EM_MISSING_PARAM, "basefile-spec"); }
				
				this.dasdLoadDisk(cuu, basefileName, writeProtected);
				
				return false;
			}
			
			// :DASDSHARED <cuu> <shared-identifier>
			if (cmd.equals(":DASDSHARED")) {
				String arg = tokens.nextUpper();
				if (arg == null) { throw new CmdError(EM_MISSING_PARAM, "cuu"); }
				int cuu = getCuu(arg);
				
				String identifier = tokens.nextUpper();
				if (identifier == null) { throw new CmdError(EM_MISSING_PARAM, "shared-identifier"); }
				
				synchronized(sharedDrives) {
					if (!sharedDrives.containsKey(identifier)) {
						throw new CmdError("Shared DASD not defined: %s", identifier);
					}
					this.linkSharedDrive(cuu, identifier);
				}
				
				return false;
			}
			
			// :SYNC [cuu]
			if (cmd.equals(":SYNC")) {
				String cuuString = tokens.nextUpper();
				if (cuuString != null) {
					int cuu = getCuu(cuuString);
					this.saveDasdDrive(cuu);
				} else {
					this.saveAllDrives();
				}
				return false;
			}
			
			// :SEGMENT <name> [ipl] <page-count> <load-at-page> <file-spec> 
			if (cmd.equals(":SEGMENT")) {
				String segName = tokens.nextUpper();
				if (segName == null) { throw new CmdError(EM_MISSING_PARAM, "name"); }
				
				String arg = tokens.nextUpper();
				boolean iplable = false;
				if (isToken(arg, "IPL")) {
					iplable = true;
					arg = tokens.nextUpper();
				}
				
				if (arg == null) { throw new CmdError(EM_MISSING_PARAM, "page-count"); }
				int pageCount = getInt(arg);
				
				arg = tokens.nextUpper();
				if (arg == null) { throw new CmdError(EM_MISSING_PARAM, "load-at-page"); }
				int loadAtPage = getInt(arg);
				
				String filename = tokens.next();
				if (filename == null) { throw new CmdError(EM_MISSING_PARAM, "filename"); }
				
				this.segmentLoad(segName, filename, pageCount, loadAtPage, iplable);
				
				return false;
			}
			
			// :IPL <segment>|<cuu> [RESet] [NORun]
			if (cmd.equals(":IPL")) {
				String cpCmd = line.substring(1);
				this.executeCPCommand(cpCmd, isSubCP, false);
				return false;
			}
			
			// :WATCH Byte|Halfword|Word|Dword|Clear <hexloc> 
			if (cmd.equals(":WATCH")) {
				String subcmd = tokens.nextUpper();
				if (subcmd == null) { throw new CmdError(EM_MISSING_PARAM, "sub-command"); }
				String xloc = tokens.nextUpper();
				if (xloc == null) { throw new CmdError(EM_MISSING_PARAM, "hex-location"); }
				int at = getHexloc(xloc);
				if (isToken(subcmd, "BYTE", 1)) {
					this.vm.cpu.setWatchByte(at);
				} else if (isToken(subcmd, "HALFWORD", 1)) {
					this.vm.cpu.setWatchHWord(at);
				} else if (isToken(subcmd, "WORD", 1)) {
					this.vm.cpu.setWatchWord(at);
				} else if (isToken(subcmd, "DWORD", 1)) {
					this.vm.cpu.setWatchDWord(at);
				} else if (isToken(subcmd, "CLEAR", 1)) {
					this.vm.cpu.clearWatch(at);
				} else {
					throw new CmdError("Invalid :WATCH sub-command '%s'\n", subcmd);
				}
				return false;
			}
			
			// :TRace CCWs|DIAGs|INSTructions ON|OFf
			if (isToken(cmd, ":TRACE", 3)) {
				String what = tokens.nextUpper();
				if (what == null) { throw new CmdError(EM_MISSING_PARAM, "what"); }
				
				String onOff = tokens.nextUpper();
				if (onOff == null) { throw new CmdError(EM_MISSING_PARAM, "ON|OFf"); }
				boolean on = false;
				if (isToken(onOff, "ON")) {
					on = true;
				} else if (!isToken(onOff, "OFF", 2)) {
					throw new CmdError("Invalid parameter to :TRACE : %s", onOff);
				}
				
				if (isToken(what, "CCWS", 3)) {
					this.deviceEventTracker.setLogging(on);
				} else if (isToken(what, "INSTRUCTIONS", 4)) {
					Cpu370Bc.setLiveLogging(on);
				} else if (isToken(what, "DIAGS", 4)) {
					this.vm.setLogDiagnose(on);
				} else if (isToken(what, "SVCS", 3)) {
					this.vm.cpu.setSvcLogInterceptor((on) ? this.svcLogInterceptor : null);
				} else {
					throw new CmdError("Invalid target for :TRACE : %s", what);
				}
				
				return false;
			}
			
			// :TIMERintr ON|OFf
			if (isToken(cmd, ":TIMERINTR", 6)) {
				String onOff = tokens.nextUpper();
				if (onOff == null) { throw new CmdError(EM_MISSING_PARAM, "ON|OFf"); }
				boolean on = false;
				if (isToken(onOff, "ON")) {
					on = true;
				} else if (!isToken(onOff, "OFF", 2)) {
					throw new CmdError("Invalid parameter to :TRACE : %s", onOff);
				}
				this.vm.setDoIntervalTimerInterrupts(on);
				return false;
			}
			
			// :INFO
			if (isToken(cmd, ":INFO")) {
				long insns = this.vm.cpu.getTotalInstructions();
				long diagX18 = this.vm.getDiagX18Count();
				long diagX20 = this.vm.getDiagX20Count();
				long sio = this.vm.getSioCount();
				String d18MSecs = getAvgMillisecs(this.vm.getDiagX18NanoSecs(), diagX18);
				String d20MSecs = getAvgMillisecs(this.vm.getDiagX20NanoSecs(), diagX20);
				String sioMSecs = getAvgMillisecs(this.vm.getSioNanoSecs(), sio);
				long now = System.currentTimeMillis();
				if (this.lastInfo > 0) {
					long msecs = now - this.lastInfo;
					this.writef("Seconds since last :INFO : %4d.%03d\n", msecs/1000, msecs%1000);
				}
				this.writef("#instruction : %8d ; #diagX18 : %8d ; #diagX20 : %8d ; #SIO : %8d\n",
						insns, diagX18, diagX20, sio);
				this.writef("             avg. msecs :         %s ;         %s ;     %s\n",
						d18MSecs, d20MSecs, sioMSecs); 
				this.lastInfo = now;
				return false;
			}
			
			// :PERF
			if (isToken(cmd, ":PERF")) {
				if (isSubCP) {
					this.writeln(":PERF cannot be used from #CP");
					return false;
				}
				this.vm.recomputeCpuPerformance(true);
				return false;
			}
			
			// :STATS
			if (isToken(cmd, ":STATS")) {
				if (this.vm.cpu.hasInstructionStatistics()) {
					this.vm.cpu.dumpInstructionStatistics(true, true);
				} else {
					this.writeln("Sorry: instruction statistics not available.");
					this.writeln(" -> please recompile emulator with INSNS_LOG = true (Cpu370Bc)");
				}
				return false;
			}
			
			// :COMM
			if (isToken(cmd, ":COMM")) {
				String subCmd = tokens.nextUpper();
				if (subCmd == null) { throw new CmdError(EM_MISSING_PARAM, "<subCmd> = Echo|Read fn ft"); }
				if (isToken(subCmd, "ECHO", 1)) {
					String rest = tokens.getRemaining();
					HostIO.run(this.vm, h -> {
						h.wrterm(new EbcdicHandler(rest));
					});
				} else if (isToken(subCmd, "READ", 1)) {
					String fn = tokens.nextUpper();
					String ft = tokens.nextUpper();
					String fmi = tokens.nextUpper();
					if (fn == null || ft == null) { 
						this.writeln(":COMM READ : missing fn or ft parameter");
						return false;
					}
					String fm = (fmi == null) ? "A1" : fmi ;
					HostIO.run(this.vm, h -> {
						int rc = h.fsopen(new EbcdicHandler(fn), new EbcdicHandler(ft), new EbcdicHandler(fm), true, 80);
						if (rc != 0) {
							this.writef(":COMM READ : FSOPEN() -> rc = %d", rc);
							return;
						}
						this.writef(":COMM - file has RECFM %s LRECL %d", 
								h.fscbIsRecfmF() ? "F" : "V",
								h.fscbGetLrecl());
						EbcdicHandler readLine = new EbcdicHandler();
						byte[] buffer = new byte[256];
						rc = h.fsread(buffer, buffer.length);
						if (rc < 0) {
							this.writef(":COMM READ : FSREAD() -> rc = %d", -rc);
							return;
						}
						while(rc > 0) {
							readLine.reset().appendEbcdic(buffer, 0, rc);
							this.writef("line: %s", readLine.toString());
							rc = h.fsread(buffer, buffer.length);
						}
						rc = h.fsclose();
						this.writef(":COMM READ : FSCLOSE() -> rc = %d", rc);
					});
				} else if (isToken(subCmd, "WRITE", 1)) {
					String fn = tokens.nextUpper();
					String ft = tokens.nextUpper();
					String fmi = tokens.nextUpper();
					if (fn == null || ft == null) { 
						this.writeln(":COMM WRITE : missing fn or ft parameter");
						return false;
					}
					String fm = (fmi == null) ? "A1" : fmi ;
					String[] lines = {
						"Line 1 from CP command interpreter",
						"Line 2",
						"Line 3 also there",
						"This is the last line form CP command interpreter"
					};
					HostIO.run(this.vm, h -> {
						int rc = h.fsopen(new EbcdicHandler(fn), new EbcdicHandler(ft), new EbcdicHandler(fm), false, 80);
						if (rc != 0 && rc != 28) {
							this.writef(":COMM WRITE : FSOPEN() -> rc = %d", rc);
							return;
						}
						EbcdicHandler writeLine = new EbcdicHandler();
						for (String l : lines) {
							writeLine.reset().appendUnicode(l);
							rc = h.fswrite(writeLine.getRawBytes(), writeLine.getLength());
							if (rc != 0) {
								this.writef(":COMM WRITE : FSWRITE() -> rc = %d", rc);
								break;
							}
						}
						rc = h.fsclose();
						this.writef(":COMM WRITE : FSCLOSE() -> rc = %d", rc);
					});
				} else if (isToken(subCmd, "PUT", 2)) {
					String fn = tokens.nextUpper();
					String ft = tokens.nextUpper();
					String fmi = tokens.nextUpper();
					if (fn == null || ft == null) { 
						this.writeln(":COMM PUT : missing fn or ft parameter");
						return false;
					}
					String fm = (fmi == null) ? "A1" : fmi ;
					HostIO.run(this.vm, h -> {
						EbcdicHandler efn = new EbcdicHandler(fn);
						EbcdicHandler eft = new EbcdicHandler(ft);
						int rc = h.fsopen(efn, eft, new EbcdicHandler(fm), true, 80);
						if (rc != 0) {
							this.writef(":COMM PUT : FSOPEN() -> rc = %d", rc);
							return;
						}
						this.writef(":COMM - file has RECFM %s LRECL %d", 
								h.fscbIsRecfmF() ? "F" : "V",
								h.fscbGetLrecl());
						VmFile vmf = new VmFile(efn, eft, h.fscbIsRecfmF(), h.fscbGetLrecl());
						byte[] buffer = new byte[65536];
						rc = h.fsread(buffer, buffer.length);
						if (rc < 0) {
							this.writef(":COMM PUT : FSREAD() -> rc = %d", -rc);
							return;
						}
						while(rc > 0) {
							vmf.append(buffer, rc);
							rc = h.fsread(buffer, buffer.length);
						}
						rc = h.fsclose();
						this.writef(":COMM PUT : FSCLOSE() -> rc = %d", rc);
						if (rc == 0) {
							vmFileExchange.add(vmf);
						}
					});
				} else if (isToken(subCmd, "LIST", 2)) {
					for(VmFile vmf : vmFileExchange) {
						this.writef(
							" %-8s %-8s   RECFM %s  LRECL %5d", 
							vmf.getFn().toString(), vmf.getFt().toString(), vmf.isRecfmF ? "V":"F", vmf.getLrecl());
					}
				} else if (isToken(subCmd, "GET", 2)) {
					String fn = tokens.nextUpper();
					String ft = tokens.nextUpper();
					String fmi = tokens.nextUpper();
					if (fn == null || ft == null) { 
						this.writeln(":COMM GET : missing fn or ft parameter");
						return false;
					}
					String fm = (fmi == null) ? "A1" : fmi ;
					
					// find the file
					VmFile vmFile = null;
					for (VmFile vmf : vmFileExchange) {
						if (vmf.getFn().toString().equals(fn) || vmf.getFt().toString().equals(ft)) {
							vmFile = vmf;
							break;
						}
					}
					if (vmFile == null) {
						this.writef(":COMM GET Error -- file %s %n not in VM file exchange", fn, ft);
						return false;
					}
					this.writef(":COMM GET: file has RECFM %s LRECL %d", 
							vmFile.isRecfmF() ? "F":"V", vmFile.getLrecl());
					
					// copy the content 
					VmFile f = vmFile;
					HostIO.run(this.vm, h -> {
						int rc = h.fsopen(new EbcdicHandler(fn), new EbcdicHandler(ft), new EbcdicHandler(fm), f.isRecfmF(), f.getLrecl());
						if (rc != 0 && rc != 28) {
							this.writef(":COMM GET : FSOPEN() -> rc = %d", rc);
							return;
						}
						for (byte[] rec : f.getLines()) {
							rc = h.fswrite(rec, rec.length);
							if (rc != 0) {
								this.writef(":COMM GET : FSWRITE() -> rc = %d", rc);
								break;
							}
						}
						rc = h.fsclose();
						this.writef(":COMM GET : FSCLOSE() -> rc = %d", rc);
					
						// drop the exchange file
						if (rc == 0) {
							vmFileExchange.remove(f);
						}
					});
				} else if (isToken(subCmd, "GETNEXT", 4)) {
					String fmi = tokens.nextUpper();
					EbcdicHandler fm = new EbcdicHandler((fmi == null) ? "A1" : fmi);
					
					if (vmFileExchange.size() < 1) {
						this.writef(":COMM GETNEXT Error -- no more file in VM file exchange");
						return false;
					}
					
					// get the next file
					VmFile vmFile = vmFileExchange.get(0);
					this.writef(":COMM GETNEXT: file has RECFM %s LRECL %d", 
							vmFile.isRecfmF() ? "F":"V", vmFile.getLrecl());
					
					// copy the content 
					VmFile f = vmFile;
					HostIO.run(this.vm, h -> {
						int rc = h.fsopen(f.getFn(), f.getFt(), fm, f.isRecfmF(), f.getLrecl());
						if (rc != 0 && rc != 28) {
							this.writef(":COMM GETNEXT : FSOPEN() -> rc = %d", rc);
							return;
						}
						for (byte[] rec : f.getLines()) {
							rc = h.fswrite(rec, rec.length);
							if (rc != 0) {
								this.writef(":COMM GETNEXT : FSWRITE() -> rc = %d", rc);
								break;
							}
						}
						rc = h.fsclose();
						this.writef(":COMM GETNEXT : FSCLOSE() -> rc = %d", rc);
					
						// drop the exchange file
						if (rc == 0) {
							vmFileExchange.remove(f);
						}
					});
				} else {
					this.writef("Unknwon :COMM subcommand %s", subCmd);
				}
				return false;
			}
			
			// ?? :PRint <0x...|....> [<count>] => print memory area in hex and ebcdic
			
			// ?? :Reg <n> [<m>] => print register n [to m]
			
			// ?? :PSw => print PSW
			
			// ?? :BReak [<0x...|....>] => set break at (or list breaks without parameters)
			
			// ?? :CLear <0x...|....> => clear break at
			
			// ?? :CONTinue
			
			// :Help
			if (isToken(cmd, ":HELP", 2)) {
				this.writef("\n"
						+ "Emulator commands:\n"
						+ "  :CONS PF <nn> [cmd]\n"
						+ "  :CONS FLowmode ON|OFf\n"
						+ "  :CONS ATtr NORMal|ECHOinput|FSBG|CONSolestate|CMDInput Default|Blue|Red|Pink|Green|Turquoise|Yellow|White [HIGHLight]\n"
						+ "  :RDR CREate\n"
						+ "  :RDR ENQueue [ [ASCii] [LENgth n] [TRUNCate] | EBCdic [ZEROfill] ] [CLass c] <filename>\n"
						+ "  :PRT CREate <directory>\n"
						+ "  :PUN CREate <directory>\n"
						+ "  :DASDLOAD [READONLY|RO] <cuu> <basefile-spec>\n"
						+ "  :DASDSHARED <cuu> <shared-identifier>\n"
						+ "  :TAPe <cuu> ATTach\n"
						+ "  :TAPe <cuu> MOUnt [WITHRing|WRitable] <filename>\n"
						+ "  :TAPe <cuu> CREate <filename>\n"
						+ "  :TAPe <cuu> DISmount\n"
						+ "  :TAPe <cuu> INFo\n"
						+ "  :SYNC [<cuu>]\n"
						+ "  :SEGMENT <name> [IPL] <page-count> <load-at-page> <file-spec> \n"
						+ "  :IPL <sysname>|<cuu> [RESet] [NORun]\n"
						+ "  :TIMERintr ON|OFf\n"
						+ "  :WATCH Byte|Halfword|Word|Dword|Clear <hexloc> \n"
						+ "  :TRace CCWs|DIAGs|INSTructions|SVCs ON|OFf\n"
						+ "  :INFO\n"
						+ "  :PERF\n"
						+ ((this.vm.cpu.hasInstructionStatistics()) ? "  :STATS\n" : "")
						+ "  :Help\n"
						+ "\n"
						);
				return false;
			}
			
		
		} catch(Throwable thr) {
			return this.emsg("EMUCMD099E (Cmd: %s): %s\n", cmd, thr.getMessage());
		}
		
		return this.emsg("EMUCMD088E Error: unknown emulator command '%s'\n", cmd);
	}
	
	/*
	 * Creation of a virtual machine
	 */
	
	// create the console device for the VM specific to the type of
	// terminal attached
	protected abstract iDevice getConsoleDevice();
	
	// the only lines executed have a : in the first column, any other lines are considered comments 
	@SuppressWarnings("deprecation")
	protected void createUserVm(String username) {
		// check the existence of the user definition
		String filename = username.toLowerCase() + ".logonscript";
		File scriptFile = new File(filename);
		if (scriptFile == null || !scriptFile.exists() || !scriptFile.isFile() || !scriptFile.canRead()) {
			this.writef("USER '%s' NOT DEFINED\n", username);
			return;
		}
		
		// create the VM
		try {
			this.vm = new CPVirtualMachine(username, this);
		} catch(Exception e) {
			this.writef("Error creating CPU: %s\n", e.getLocalizedMessage());
			return;
		}
		this.msecConnected = System.currentTimeMillis();
		this.deviceEventTracker = new DeviceEventTracker(this.vm.cpu);
		
		// create the console device and bind the VM to it
		iDevice consoleDevice = getConsoleDevice();
		DeviceHandler consoleDeviceHandler;
		try {
			consoleDeviceHandler = this.vm.createDeviceHandler(consoleDevice, 0x009, this.deviceEventTracker);
			this.vm.setConsoleDevice(consoleDeviceHandler);
		} catch (DuplicateDeviceException e) {
			// cannot happen, as this is the first device
		}
		
		String nowString = this.getTimeDateString();
		this.writeln(String.format("LOGON AT %s", nowString));
		System.out.printf("VM '%s' logged on at %s\n", this.vm.getIsoName(), nowString);
		
		// run the logonscript for this user
		try {
			FileInputStream is = new FileInputStream(filename);
			DataInputStream dis = new DataInputStream(is);
			String line = dis.readLine();
			while(line != null) {
				if (line.startsWith(":")) {
					this.executeEmulatorCommand(line, false);
				}
				line = dis.readLine();
			}
			dis.close();
			is.close();
		} catch (FileNotFoundException e) {
			// should not happen, as file existence has been checked explicitly above 
		} catch (IOException e) {
			this.writef("Error reading script file: ", e.getMessage());
		}
	}
	
	protected void shutdownUserVm() {
		this.shutdownUserVm(false);
	}
	
	protected void shutdownUserVm(boolean noSyncDisks) {
		
		// just to be sure
		if (this.vm == null) { return; }
		
		// commit all modifications minidisks to their delta-files
		if (noSyncDisks) {
			this.writeln("NOT SYNCHRONIZING DISKS ON USER REQUEST AT LOGOFF");
		} else {
			this.saveAllDrives();
		}
		
		// tell the user about logoff
		String nowString = this.getTimeDateString();
		this.writeln(this.getUsedTimeString());
		this.writeln("LOGOFF AT " + nowString);
		this.writeln("");
		
		// tell the main console about it
		System.out.printf("VM '%s' logged off at %s\n", this.vm.getIsoName(), nowString);
		
		// shutdown the VM and free all resources
		this.vm.requestHalt(); // fall back to the main CP command loop 
		this.lastInfo = 0;
		this.rdrDevice = null;
		this.rdrDeviceHandler = null;
		this.punDevice = null;
		this.punDeviceHandler = null;
		this.prtDevice = null;
		this.prtDeviceHandler = null;
		this.writableDevices.clear();
		this.ownDrives.clear();
		this.vm = null;
		try {
			Thread.sleep(10); // 10ms giving other threads a chance to end and cleanup
		} catch (InterruptedException e) { }
		System.gc();
	}
}
