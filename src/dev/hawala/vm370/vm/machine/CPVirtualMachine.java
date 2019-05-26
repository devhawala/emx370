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

package dev.hawala.vm370.vm.machine;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import static dev.hawala.vm370.ebcdic.PlainHex.*;
import static dev.hawala.vm370.ebcdic.Ebcdic.*;
import dev.hawala.vm370.cons.ConsoleCommandCodes;
import dev.hawala.vm370.cons.ConsoleSimple;
import dev.hawala.vm370.ebcdic.EbcdicHandler;
import dev.hawala.vm370.vm.device.DeviceHandler;
import dev.hawala.vm370.vm.device.iDevice;
import dev.hawala.vm370.vm.device.iDeviceChannelStatus;
import dev.hawala.vm370.vm.device.iDeviceStatus;

/**
 * Implementation of a virtual machine similar to the environment
 * provided by CP in VM/370.
 * <p>
 * This class uses and extends the functionality of a {@link Cpu370Bc} (which
 * provides the problem state and some privileged instructions) with
 * the "higher level" instructions (DIAG, I/O) based on a set of devices,
 * named segments and a command interpreter created externally and
 * registered with the virtual machine as instance of this class.  
 * </p>
 * <p>
 * A virtual machine runs a CPU (executes instructions and I/O operations)
 * under control of the command interpreter, which directs the VM to
 * ipl an OS and repeatedly run a number of instructions until execution
 * automatically ends (wait PSW, return to command interpreter, ...).  
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2015
 */
public class CPVirtualMachine {
	
	// the name of this VM
	protected final String vmNameIso;
	protected final EbcdicHandler vmName;
	
	// the CP command processor where to send DIAG-x'08' CP commands
	protected final iCommandExecutor cpCommandExecutor;
	
	// class and name of the cpu type to use when creating VMs
	private static Class<?> cpuClass = Cpu370BcLambda.class;
	private static String cpuType = "Lambda1";
	
	public static String getCpuType() {
		return cpuType;
	}

	// set the cpu type to use for the next (!) created VM/CP
	public static void setCpuType(String name) {
		switch(name.toLowerCase()) {
		case "basic": cpuClass = Cpu370BcBasic.class; cpuType = "Basic"; return;
		case "jumptable": cpuClass = Cpu370BcJumpTable.class; cpuType = "JumpTable"; return;
		case "lambda1": cpuClass = Cpu370BcLambda.class; cpuType = "Lambda1"; return;
		case "lambda2": cpuClass = Cpu370BcLambda2.class; cpuType = "Lambda2"; return;
		case "lambda3": cpuClass = Cpu370BcLambda3.class; cpuType = "Lambda3"; return;
		case "lambda4": cpuClass = Cpu370BcLambda4.class; cpuType = "Lambda4"; return;
		default: throw new IllegalArgumentException("Unknown 370 BC CPU type");
		}
	}
	
	// the CPU for the VM
	public final Cpu370Bc cpu;
	
	// the measured speed of an emulated S/370 CPU on the underlying hardware, used: 
	// -> to compute the CPU time used so far
	// -> to compute the number of instructions for the CPU to release control after a max. runtime
	private final static int PERF_INSN_COUNT = 100000;
	protected static int instructionsPerMillisecond = 0;
	
	// the intended max. runtime for a single call to cpu.execute() in millisecs and the
	// corresponding max. number of instructions, based on the performance measurement
	// (i.e. the control returns at most after this time to the simulated CP == this class)
	// (this counts only for "pure" CPU insructions, as each I/O oder other privileged
	// instruction (DIAG) will return to the simulated CP before this max. interval is elapsed) 
	private final static int MAX_MILLISECONDS_PER_RUN = 10; 
	protected static int maxInstructionsPerRun = 2048;

	// the devices attached to this VM
	protected final ArrayList<DeviceHandler> devices = new ArrayList<DeviceHandler>();
	
	// the console device (one of the above devices)
	protected DeviceHandler consoleDevice = null;
	
	// the named segments that the VM can load or possibly ipl
	protected final ArrayList<NamedSegment> namedSegments = new ArrayList<NamedSegment>();
	
	// the state of the VM
	protected boolean needsIpl = true; // does the VM have a runnable guest OS? (not if: initial, "disabled wait", unimplemented instruction / DIAG)
	protected boolean doHalt = false;  // is there currently a request to leave the running state?
	
	// milliseconds to wait if in enable wait state before checking interrupts or other state change
	private final static int IDLE_MSECS_FOR_ENABLED_WAIT = 10;
	
	/*
	 * construction
	 */
	
	public CPVirtualMachine(String username, iCommandExecutor cpCommandExecutor) throws Exception {
		// create the CPU
		this.cpu = (Cpu370Bc)cpuClass.newInstance();
		
		// baptise this VM
		if (username == null) {
			throw new IllegalArgumentException("No username specified for the new VM");
		}
		username = username.trim();
		if (username.length() == 0 || username.length() > 8) {
			throw new IllegalArgumentException("No valid username specified for the new VM");
		}
		this.vmNameIso = username;
		this.vmName = new EbcdicHandler((username + "       ").toUpperCase().substring(0,8));
		
		// store the CP command processor
		if (cpCommandExecutor == null) {
			throw new IllegalArgumentException("No CP command processor given");
		}
		this.cpCommandExecutor = cpCommandExecutor;
		
		// measure an "average" CPU speed if not yet done
		if (instructionsPerMillisecond == 0) {
			// measure CPU performance:
			this.recomputeCpuPerformance(false); // first run: exercise the code to prepare JIT
			try { Thread.sleep(20); } catch (InterruptedException e) { } // wait a bit
			this.recomputeCpuPerformance(true);  //
			
			// remove all changes from the CPU 
			this.cpu.resetEngine();
			this.cpu.resetTotalInstructions();
		}
	}
	
	public String getIsoName() {
		return this.vmNameIso;
	}
	
	public void recomputeCpuPerformance(boolean verbose) {
		
		// location of instructions
		int codeBase = 0x021000;
		
		// save state of cpu/memory
		int oldR1 = this.cpu.getGPR(1);
		int oldR2 = this.cpu.getGPR(2);
		int oldR3 = this.cpu.getGPR(3);
		int insnLoc0 = this.cpu.peekMainMemInt(codeBase);
		int insnLoc1 = this.cpu.peekMainMemInt(codeBase + 4);
		int insnLoc2 = this.cpu.peekMainMemInt(codeBase + 8);
		int insnLoc3 = this.cpu.peekMainMemInt(codeBase + 12);
		int dataLoc0 = this.cpu.peekMainMemInt(0x022180);
		int dataLoc1 = this.cpu.peekMainMemInt(0x022184);
		int dataLoc2 = this.cpu.peekMainMemInt(0x022188);
		byte[] oldPsw = {0,0,0,0,0,0,0,0};
		this.cpu.writePswTo(oldPsw, 0);
		boolean doLiveLog = Cpu370Bc.getLiveLogging();
		Cpu370Bc.setLiveLogging(false);
		
		// register usage for instructions:
		// R1: index register for addressing in BC
		// R2: base register for addressing in L, A, ST
		// R3: computation register in A and target / source for L / ST
		
		this.cpu.setGPR(2, 0x022000);              // R2 base register for addressing
		this.cpu.pokeMainMem(0x022180, 0x12345000);// 1st number at address: 0x022180
		this.cpu.pokeMainMem(0x022184, 0x00000678);// 2nd number at address: 0x022184
		// result is stored at address: 0x010188
		
		this.cpu.setGPR(1, codeBase - 0x0EFE);     // set index register for addressing
		
		// instruction sequence for measuring: repeat { LoadRegister - Add - StoreRegister - Jump-Back } 100.000 times
		byte[] insns = {
				_58, _30, _21, _80,       // L R3 <- (base=R2,index=0,offset=0x180)
				_5A, _30, _21, _84,       // A R3 <- (base=R2,index=0,offset=0x184), result is positive (=> CC = 2)
				_50, _30, _21, _88,       // ST (base=R2,index=0,offset=0x188) <- R3
				_47, _21, _0E, _FE        // BC CC2 <- (base=0,index=R1,offset=0xEFE)
		};
		this.cpu.pokeMainMem(codeBase, insns, 0, insns.length);
		this.cpu.setPswInstructionAddress(codeBase);
		
		// run the instructions
		long cpuStartCount = this.cpu.getTotalInstructions();
		long stopTime = 0;
		long startTime = System.nanoTime();
		try {
			this.cpu.execute(PERF_INSN_COUNT); // do 100.000 instructions (loops)
			stopTime = System.nanoTime();
		} catch (PSWException e) {
			// ignored
		} 
		
		// compute performance parameters
		long cpuEndCount = this.cpu.getTotalInstructions();
		long duration = stopTime - startTime;
		if (duration <= 0 || (cpuEndCount - cpuStartCount) < PERF_INSN_COUNT) {
			// something went wrong, not a good sign :-(
			instructionsPerMillisecond = 1000; // assume a 1 MIPS machine
			if (verbose) {
				this.signalProblem("Warning: unable to measure CPU performance, assuming 1 MIPS");
			}
		} else {
			instructionsPerMillisecond = (int)((PERF_INSN_COUNT * 1000000L) / duration);
			if (verbose) { 
				this.signalProblem(
						"Info: measured CPU performance: %d instructions/ms (%d insns in %d nanosecs)", instructionsPerMillisecond, PERF_INSN_COUNT,
						duration);
			}
		}
		maxInstructionsPerRun = instructionsPerMillisecond * MAX_MILLISECONDS_PER_RUN;
		
		// restore cpu/memory state
		this.cpu.setGPR(1, oldR1);
		this.cpu.setGPR(2, oldR2);
		this.cpu.setGPR(3, oldR3);
		this.cpu.pokeMainMem(codeBase, insnLoc0);
		this.cpu.pokeMainMem(codeBase + 4, insnLoc1);
		this.cpu.pokeMainMem(codeBase + 8, insnLoc2);
		this.cpu.pokeMainMem(codeBase + 12, insnLoc3);
		this.cpu.pokeMainMem(0x022180, dataLoc0);
		this.cpu.pokeMainMem(0x022184, dataLoc1);
		this.cpu.pokeMainMem(0x022188, dataLoc2);
		try { this.cpu.readPswFrom(oldPsw, 0); } catch (PSWException e) { }
		Cpu370Bc.setLiveLogging(doLiveLog);
	}
	
	/*
	 * signal important problems: unsupported instruction, unsupported DIAG-function, ...
	 * 
	 * => write to user's terminal
	 * => write to (Java) console if it is not the user's terminal
	 */
	
	private EbcdicHandler signalBuffer = new EbcdicHandler(2048);
	private byte[] signalCcwTemplate = {
			ConsoleCommandCodes.WriteAddCR, // CCW command
			_00, _00, _08,                  // address (string to output is directly behind the CCW) 
			_00, _00                        // flags + filler
			                                // to be added: 2 bytes for count
			};
	
	protected void signalProblem(String pattern, Object... params) {
		String line = String.format(pattern, params);
		
		// signal the problem through the user's terminal with a device CCW
		if (this.consoleDevice != null) {
			int lineLen = line.length();
			byte b0 = (byte)((lineLen & 0xFF) >> 8);
			byte b1 = (byte)(lineLen & 0xFF);
			this.signalBuffer
				// clear
				.reset()
				// add first 6 bytes of the Write-CCW
				.appendEbcdic(this.signalCcwTemplate, 0, this.signalCcwTemplate.length)
				// add the count field to the CCW
				.appendEbcdicChar(b0).appendEbcdicChar(b1)
				// add the text to output
				.appendUnicode(line);
			// output the CCW with the text
			this.consoleDevice.processFromBytes(this.signalBuffer.getRawBytes(), 0);
		}
		
		// output the same message on the Java console if it is not also the user's console
		if (this.consoleDevice == null || !(this.consoleDevice.getDevice() instanceof ConsoleSimple)) {
			System.err.println(this.vmNameIso + ": " + line);
		}
	}
	
	public void disableRunning(String pattern, Object... params) {
		this.signalProblem(pattern, params);
		this.disableRunning();
	}
	
	public void disableRunning() {
		this.signalProblem("Virtual machine halted and needs IPL.");
		this.needsIpl = true;
		this.doHalt = true;
	}
	
	/*
	 * halted state management
	 */
	
	public void requestHalt() {
		synchronized(this) {
			this.doHalt = true;
		}
	}
	
	protected boolean isToBeHalted() {
		boolean haltIt;
		synchronized(this) {
			haltIt = this.doHalt;
			this.doHalt = false;
		}
		return haltIt;
	}
	
	
	/*
	 * configuration methods (adding/removing devices, segments, ...) 
	 */
	
	public void setConsoleDevice(DeviceHandler dev) throws DuplicateDeviceException {
		if (dev == null) {
			if (this.consoleDevice == null) { return; }
			this.removeDevice(this.consoleDevice.getCUU());
			this.consoleDevice = null;
			return;
		}
		
		int devClass = dev.getDevice().getVDevInfo() & 0xFF000000;
		if (devClass == 0x80000000 || devClass == 0x40000000) {
			if (this.consoleDevice != null) { this.devices.remove(this.consoleDevice); }
			this.consoleDevice = null;
			this.addDevice(dev);
		}
	}
	
	public void addDevice(DeviceHandler dev) throws DuplicateDeviceException {
		// check if device is already present or if the CUU is already defined
		int newCuu = dev.getCUU();
		for (DeviceHandler d : this.devices) {
			if (d == dev) { return; }
			if (newCuu == d.getCUU()) {
				throw new DuplicateDeviceException(newCuu);
			}
		}
		
		// add the device
		this.devices.add(dev);
		
		// the first terminal device is used as console (and cannot be removed)
		if (this.consoleDevice == null) {
			int devClass = dev.getDevice().getVDevInfo() & 0xFF000000;
			if (devClass == 0x80000000 || devClass == 0x40000000) {
				this.consoleDevice = dev;
			}
		}
	}
	
	public DeviceHandler createDeviceHandler(
				iDevice forDevice,
				int forCuu,
				iProcessorEventTracker eventTracker
				) throws DuplicateDeviceException {
		// check if the CUU is already defined
		for (DeviceHandler d : this.devices) {
			if (forCuu == d.getCUU()) {
				throw new DuplicateDeviceException(forCuu);
			}
		}
		
		return this.cpu.allocateDeviceHandler(forDevice, forCuu, eventTracker);
	}
	
	public DeviceHandler getDevice(int cuu) {
		if (cuu == -1) { return this.consoleDevice; }
		for (DeviceHandler d : this.devices) {
			if (d.getCUU() == cuu) { return d; }
		}
		return null;
	}
	
	public void removeDevice(int cuu) {
		DeviceHandler dev = null;
		for (DeviceHandler d : this.devices) {
			if (d.getCUU() == cuu) {
				dev = d;
				break;
			}
		}
		if (dev == this.consoleDevice) { return; } // the console cannot be removed!!!
		if (dev != null) { this.devices.remove(dev); }
	}
	
	public List<String> getQueryVirtualList(String type) {
		DeviceHandler[] ds = this.devices.toArray(new DeviceHandler[this.devices.size()]);
		Arrays.sort(ds, new Comparator<DeviceHandler>() {
			@Override
			public int compare(DeviceHandler left, DeviceHandler right) {
				return (left.getCUU() < right.getCUU()) ? -1 : +1;
			}});
		List<String> qvl = new ArrayList<String>();
		for (DeviceHandler d : ds) {
			String s = d.getDevice().getCpQueryStatusLine(d.getCUU());
			if (type == null || s.startsWith(type)) { qvl.add(s); }
		}
		return qvl;
	}
	
	public String getQueryVirtualString(int cuu) {
		DeviceHandler d = this.getDevice(cuu);
		if (d == null) { return null; }
		return d.getDevice().getCpQueryStatusLine(d.getCUU());
	}
	
	public void addKnownNamedSegment(NamedSegment seg) {
		String name = seg.getName();
		for (int i = 0; i < this.namedSegments.size(); i++) {
			if (this.namedSegments.get(i).getName().equals(name)) {
				this.namedSegments.set(i, seg);
				return;
			}
		}
		this.namedSegments.add(seg);
	}
	
	public NamedSegment findNamedSegment(String name) {
		for (NamedSegment seg : this.namedSegments) {
			if (seg.getName().equals(name)) { return seg; }
		}
		return null;
	}
	
	public void iplFromSegment(String name) throws PSWException {
		// reset the state (real CP does this first also)
		this.needsIpl = true;
		this.cpu.resetEngine();
		this.resetIntervalTimer();
		
		// check the segment validity
		if (name == null || name.length() == 0) {
			throw new IllegalArgumentException("Segment name is null or empty");
		}
		NamedSegment segment = this.findNamedSegment(name);
		if (segment == null) {
			throw new IllegalArgumentException("Named segment not available to virtual machine");
		}
		if (!segment.canIpl()) {
			throw new IllegalArgumentException("Named segment cannot be IPL-ed");
		}
		
		// do the initial program load
		segment.loadSegment(this.cpu, true);
		this.needsIpl = false;
	}
	
	/*
	 * Diagnose instruction
	 */
	
	private boolean logDiagnose = false;
	
	public void setLogDiagnose(boolean doLog) {
		this.logDiagnose = doLog;
	}
	
	private volatile long diagX18Count = 0;
	private volatile long diagX20Count = 0;
	
	public long getDiagX18Count() { return this.diagX18Count; }
	public long getDiagX20Count() { return this.diagX20Count; }
	
	private volatile long diagX18NanoSecs = 0;
	private volatile long diagX20NanoSecs = 0;
	
	public long getDiagX18NanoSecs() { return this.diagX18NanoSecs; }
	public long getDiagX20NanoSecs() { return this.diagX20NanoSecs; }
	
	/**
	 * Emulate a CP diagnose instruction.
	 *  
	 * @param diagInstructionCode the complete 32-bit diagnose instruction.
	 */
	public void processDiagnose(int diagInstructionCode) {
		int rx = (diagInstructionCode & 0x00F00000) >> 20;
		int ry = (diagInstructionCode & 0x000F0000) >> 16;
		int diagCode = diagInstructionCode & 0xFFFF;
		
		// DBG this.cpu.logLine("   -> DIAG %d,%d,X'%02X'", rx,ry,diagCode);
		
		switch(diagCode) {
		
		case 0x00:  // Store Extended Identification Code
			if (logDiagnose) { this.cpu.logLine("  Diagnose x'00' rx: %d ; ry: %d  -- Store Extended Identification Code", rx, ry); }
			this.doDiagX00(rx, ry);
			break;
			
		case 0x08:  // Virtual Console Function
			if (logDiagnose) { this.cpu.logLine("  Diagnose x'08' rx: %d ; ry: %d  -- Virtual Console Function", rx, ry); }
			this.doDiagX08(rx, ry);
			break;
			
		case 0x10: // Release Pages
			if (logDiagnose) { this.cpu.logLine("  Diagnose x'10' rx: %d ; ry: %d  -- Release Pages", rx, ry); }
			// ignored
			break;
			
		case 0x0C:  // Pseudo Timer
			if (logDiagnose) { this.cpu.logLine("  Diagnose x'0C' rx: %d ; ry: %d  -- Pseudo Timer", rx, ry); }
			this.doDiagX0C(rx, ry);
			break;
			
		case 0x18:  // Standard DASD I/O
			if (logDiagnose) { this.cpu.logLine("  Diagnose x'18' rx: %d ; ry: %d  -- Standard DASD I/O", rx, ry); }
			this.diagX18Count++;
			long startD18NanoSecs = System.nanoTime();
			this.doDiagX18(rx, ry);
			this.diagX18NanoSecs += System.nanoTime() - startD18NanoSecs;
			break;
			
		case 0x20:  // General I/O
			if (logDiagnose) { this.cpu.logLine("  Diagnose x'20' rx: %d ; ry: %d  -- General I/O", rx, ry); }
			this.diagX20Count++;
			long startD20NanoSecs = System.nanoTime();
			this.doDiagX20(rx, ry);
			this.diagX20NanoSecs += System.nanoTime() - startD20NanoSecs;
			break;
			
		case 0x24:  // Device Type and Features
			if (logDiagnose) { this.cpu.logLine("  Diagnose x'24' rx: %d ; ry: %d  -- Device Type and Features", rx, ry); }
			this.doDiagX24(rx, ry);
			break;
			
		case 0x58:  // 3270 Virtual Console Interface
			if (logDiagnose) { this.cpu.logLine("  Diagnose x'58' rx: %d ; ry: %d  -- 3270 Virtual Console Interface", rx, ry); }
			this.doDiagX58(rx, ry);
			break;
			
		case 0x5C:  // Error Message Editing
			if (logDiagnose) { this.cpu.logLine("  Diagnose x'5C' rx: %d ; ry: %d  -- Error Message Editing", rx, ry); }
			this.doDiagX5C(rx, ry);
			break;
			
		case 0x60:  // Determine the Virtual Machine Storage Size
			if (logDiagnose) { this.cpu.logLine("  Diagnose x'60' rx: %d ; ry: %d  -- Determine the Virtual Machine Storage Size", rx, ry); }
			this.doDiagX60(rx, ry);
			break;
			
		case 0x64:  // Finding, Loading and Purging a Named Segment
			if (logDiagnose) { this.cpu.logLine("  Diagnose x'64' rx: %d ; ry: %d  -- Finding, Loading and Purging a Named Segment", rx, ry); }
			this.doDiagX64(rx, ry);
			break;
			
		case 0x8004: // ???
			if (logDiagnose) { this.cpu.logLine("  Diagnose x'8004' rx: %d ; ry: %d  -- ??? ??? ???", rx, ry); }
			// ignored
			break;
			
		default:
			int insnAddr = this.cpu.getPrivOpAddr();
			int vx = this.cpu.getGPR(rx);
			int vy = this.cpu.getGPR(ry);
			System.out.printf(
					"\n** Unsupported/invalid DIAG opcode at 0x%06X: DIAG x'%04X' %d, %d (Rx=0x%08X , Ry=0x%08X)\n",
					insnAddr, diagCode, rx, ry, vx, vy);
			System.out.printf("** Data at Ry^:\n  0x", vy);
			for (int i = 0; i < 32; i++) {
				System.out.printf(" %02X", this.cpu.mem[vy + i]);
			}
			System.out.printf("\n  0x", vy);
			for (int i = 0; i < 32; i++) {
				System.out.printf(" %02X", this.cpu.mem[vy + 32 + i]);
			}
			System.out.println();
			if (logDiagnose) { 
				this.disableRunning(
						"Unsupported/invalid DIAG opcode at 0x%06X: DIAG x'%04X' %d, %d",
						insnAddr, diagCode, rx, ry);
			}
			return;
			
		}
	}
	
	// DIAG X'00' -- Store Extended Identification Code
	private void doDiagX00(int rx, int ry) {
		// get the parameters
		int addr = this.cpu.getGPR(rx);
		int count = this.cpu.getGPR(ry);
		
		// prepare the result bytes, modeled after the DIAG-X'00' result of a VM/370R6 SixPack 1.2
		byte[] u = this.vmName.getRawBytes();
		byte[] res = {  
			// System Name
			_V,_M,_Slash,_3,_7,_0,_Blank,_Blank,
			// Version Number
			_6F,_FA,_FC,
			// Version code
			_FD,
			// MCEL
			_00,_00,
			// Processor Address
			_00,_00,
			// Userid
			u[0],u[1],u[2],u[3],u[4],u[5],u[6],u[7],
			// Program Product Bit Map
			_00,_00,_00,_00,_00,_00,_00,_00
		};
		
		// transfer the result into the memory
		this.cpu.pokeMainMem(addr, res, 0, Math.min(res.length, count));
		
		// done
		return;
	}
	
	// DIAG X'08' -- Virtual Console Function
	private EbcdicHandler diag08Commands = new EbcdicHandler(133);
	private EbcdicHandler diag08Results = new EbcdicHandler(8192);
	
	private void doDiagX08(int rx, int ry) {
		int cpCmdAddr = this.cpu.getGPR(rx) & 0x00FFFFFF;
		int cpCmdLen = this.cpu.getGPR(ry);
		
		// check for NOOP => switch back to CP command processor
		if (cpCmdLen == 0) {
			this.requestHalt(); // stop instruction execution loop and return to invoker (CP command interpreter loop)
			return;
		}
		
		// run the CP command(s)
		boolean processToBuffer = ((cpCmdLen & 0x40000000) != 0);
		int len = cpCmdLen & 0x00FFFFFF; // drop flags 
		this.diag08Commands.reset();
		for (int i = 0; i < len; i++) {
			this.diag08Commands.appendEbcdicChar(this.cpu.peekMainMemByte(cpCmdAddr + i));
		}
		// DBG System.err.printf("------ DIAG-x08 command: '%s'\n", this.diag08Commands.getString());
		this.diag08Results.reset();
		int rc = this.cpCommandExecutor.processCommandBuffer(
				this.diag08Commands,
				(processToBuffer) ? this.diag08Results : null);
		
		// DBG: tell the user about unprocessable commands
		if (rc != 0) {
			System.err.printf("------ DIAG-x08: rc = %d ~ command: '%s'\n", rc, this.diag08Commands.getString());
		}
		
		// put the results into memory if requested
		if (processToBuffer) {
			// if rx and ry are setup correctly, transfer the result and set the ry+1 register and CC
			if (Math.abs(rx - ry) > 1 && rx != 15 && ry != 15) {
				int dstAddr = this.cpu.getGPR(rx+1) & 0x00FFFFFF;
				int dstLen = this.cpu.getGPR(ry+1);
				if (dstLen > this.diag08Results.getLength()) {
					this.cpu.pokeMainMem(dstAddr, this.diag08Results.getRawBytes(), 0, this.diag08Results.getLength());
					this.cpu.setGPR(ry+1, this.diag08Results.getLength());
					this.cpu.setPswConditionCode((byte)0);
				} else {
					this.cpu.pokeMainMem(dstAddr, this.diag08Results.getRawBytes(), 0,dstLen);
					this.cpu.setGPR(ry+1, this.diag08Results.getLength() - dstLen);
					this.cpu.setPswConditionCode((byte)1);
				}
			} else {
				this.cpu.setGPR(ry+1, 0);
				this.cpu.setPswConditionCode((byte)1);
			}
		}

		// put the return code form CP into ry
		this.cpu.setGPR(ry, rc);
		
		// done
		return;
	}
	
	// DIAG X'0C' -- Pseudo Timer
	private SimpleDateFormat fmt = new SimpleDateFormat("MM/dd/yyHH:mm:ss");
	private EbcdicHandler diag0cTS = new EbcdicHandler(16);
	
	public long getVirtualCpuMicrosecs() {
		return (this.cpu.getTotalInstructions() * 1000L) / instructionsPerMillisecond;
	}
	
	public long getRealCpuMicrosecs() {
		return this.getVirtualCpuMicrosecs();
	}
	
	private void doDiagX0C(int rx, int ry) {
		// virtual = real cpu time consumed
		long cpuMicrosecs = this.getVirtualCpuMicrosecs(); 
		
		// current date/time string in the format (16 ebcdic bytes): mm/dd/yyhh:mm:ss
		String timeStamp = this.fmt.format(new Date());
		this.diag0cTS.reset().appendUnicode(timeStamp);
		
		// store all that at the position indicated by rx
		int location = this.cpu.getGPR(rx) & 0x00FFFFFF;
		this.cpu.pokeMainMem(location, this.diag0cTS.getRawBytes(), 0, 16);
		this.cpu.pokeMainMem(location+16, cpuMicrosecs);
		this.cpu.pokeMainMem(location+24, cpuMicrosecs);
		
		// done
		return;
	}
	
	// DIAG X'18' -- Standard DASD I/O
	private void doDiagX18(int rx, int ry) {
		int cuu = this.cpu.getGPR(rx);
		int ccwAddr = this.cpu.getGPR(ry) & 0x00FFFFFF;
		//int readWriteCount = this.cpu.getGPR(15); // what could we need this parameter for?
		
		// is the virtual device defined?
		DeviceHandler handler = this.getDevice(cuu);
		if (handler == null) {
			this.cpu.setGPR(15, 1); // "Device not attached"
			this.cpu.setPswConditionCode((byte)1);
			return;
		}
		
		// is it a DASD device?
		iDevice dev = handler.getDevice();
		int vdevInfo = dev.getVDevInfo();
		int devClass = (vdevInfo >> 24) & 0xFF;
		if (devClass != 0x04 && devClass != 0x01) { // is it of DASD {ckd,fba} class?
			this.cpu.setGPR(15, 2); // "Device not 2319, 2314, 3330, 3340, or 3350"
			this.cpu.setPswConditionCode((byte)1);
			return;
		}
		
		// execute the CCW chain
		boolean ioOk = handler.processFromAddress(ccwAddr, _00); // protection key is a dummy...
		if (ioOk) {
			this.cpu.setPswConditionCode((byte)0); // "I/O complete with no errors"
			return;
		}
		
		/*
		 * interpret the problem to get a more precise error reason...
		 */

		// was I/O processing even attempted?
		if (!handler.hasPendingCompletionInterrupt()) {
			
			// CCW on double aligned address?
			if ((ccwAddr & 0x07) != 0) {
				this.cpu.setGPR(15, 5); // "pointer to CCW string not doubleword aligned"
				this.cpu.setPswConditionCode((byte)2);
				return;
			}
			
			// other possible CCW structure related errors not defined in Systems Programmers Guide (1981)... ?
			
			// TODO: fall-through is not a good idea, but what else to do without an error code / CC ?? 
		}
		
		byte channelStatus = handler.getCswChannelStatus();
		if ((channelStatus & iDeviceChannelStatus.PROGRAM_CHECK) != 0) {
			// not defined in Systems Programmers Guide (1981)... ?
			this.cpu.setGPR(15, 5); // intentional misuse for all variants: "pointer to CCW string not doubleword aligned"
			this.cpu.setPswConditionCode((byte)2);
			return;
		}
		
		byte sense0 = handler.getDevice().getSenseByte(0);
		if (sense0 == (byte)0x01) {
			// Seek check => we match this to "Cylinder number not in range of user's disk"
			this.cpu.setGPR(15, 4);
			this.cpu.setPswConditionCode((byte)1);
			return;
		}
		
		byte sense1 = handler.getDevice().getSenseByte(1);
		if (sense1 == (byte)0x02) {
			// Write inhibited
			this.cpu.setGPR(15, 3); // "Attempt to write on a read-only disk"
			this.cpu.setPswConditionCode((byte)1);
			return;
		}
		
		// fall back error state
		this.cpu.setGPR(15, 13); // "Sense bytes are available if user issues a SENSE command"
		this.cpu.setPswConditionCode((byte)3); // Uncorrectable I/O error
		
		return;
	}
	
	// DIAG X'20' -- General I/O
	private void doDiagX20(int rx, int ry) {
		int cuu = this.cpu.getGPR(rx);
		int ccwAddr = this.cpu.getGPR(ry) & 0x00FFFFFF;
		
		// is the virtual device defined?
		DeviceHandler handler = this.getDevice(cuu);
		if (handler == null) {
			this.cpu.setGPR(15, 1); // "Device is either not attached or the virtual channel is dedicated."
			this.cpu.setPswConditionCode((byte)1);
			return;
		}
		
		// execute the CCW chain
		boolean ioOk = handler.processFromAddress(ccwAddr, _00); // protection key is a dummy...
		if (ioOk) {
			this.cpu.setPswConditionCode((byte)0); // "I/O completed with no errors"
			return;
		}
		
		// unit exception ?
		byte devStatus = handler.getCswUnitStatus();
		if ((devStatus & iDeviceStatus.UNIT_EXCEPTION) != 0) {
			this.cpu.setGPR(15, 2); // "Unit exception bit in device status byte=l"
			this.cpu.setPswConditionCode((byte)2);
			return;
		}
		
		// anything else: permanent I/O error
		// => set rightmost 2 ry-bytes to the first 2 sense bytes
		// => CC3
		int ryVal = this.cpu.getGPR(ry);
		byte sense0 = handler.getDevice().getSenseByte(0);
		byte sense1 = handler.getDevice().getSenseByte(1);
		ryVal &= 0xFFFF0000;
		ryVal |= ((sense0 & 0xFF) << 8) | (sense1 & 0xFF);
		this.cpu.setGPR(ry, ryVal);
		this.cpu.setGPR(15, 13); // "permanent I/O error"
		this.cpu.setPswConditionCode((byte)3);
		
		return;
	}
	
	// DIAG X'24' -- Device Type and Features
	private void doDiagX24(int rx, int ry) {
		int cuu = this.cpu.getGPR(rx);
		if (logDiagnose) { this.cpu.logLine("     -> cuu = 0x%03X", cuu); }
		DeviceHandler handler = this.getDevice(cuu);
		
		// check for CONS device (cuu -1)
		if (cuu == -1 && this.consoleDevice != null) {
			handler = this.consoleDevice;
			cuu =  handler.getCUU();
			if (logDiagnose) { this.cpu.logLine("     => cuu = 0x%03X", cuu); }
			this.cpu.setGPR(rx, cuu);
		}
		
		// do we know the device?
		if (handler == null) {
			this.cpu.setPswConditionCode((byte)3); // "invalid device address or virtual device does  not exist"
			return;
		}
		
		// TODO: if virtual device is defined but not bound to a real device (TAPE, GRAF or the like)
		// => CC2 + rx + ry
		
		// OK, we know the device => set ry, ry+1 and CC0
		iDevice dev = handler.getDevice();
		int vdevInfo = dev.getVDevInfo();
		this.cpu.setGPR(ry, vdevInfo);
		if (logDiagnose) {
			this.cpu.logLine("     => vdev info = 0x%08X", vdevInfo);
			this.cpu.logLine("        -> VDEVTYPC = 0x%02X   (virtual device type class)", (vdevInfo >> 24) & 0xFF);
			this.cpu.logLine("        -> VDEVTYPE = 0x%02X   (virtual device type)", (vdevInfo >> 16) & 0xFF);
			this.cpu.logLine("        -> VDEVSTAT = 0x%02X   (virtual device status)", (vdevInfo >> 8) & 0xFF);
			this.cpu.logLine("        -> VDEVFLAG = 0x%02X   (virtual device flags)", vdevInfo & 0xFF);
		}
		if (ry < 15) {
			int rdevInfo = dev.getRDevInfo();
			this.cpu.setGPR(ry+1, rdevInfo);
			if (logDiagnose) {
				this.cpu.logLine("     => rdev info = 0x%08X", rdevInfo);
				this.cpu.logLine("        -> RDEVTYPC = 0x%02X   (real device type class)", (rdevInfo >> 24) & 0xFF);
				this.cpu.logLine("        -> RDEVTYPE = 0x%02X   (real device type)", (rdevInfo >> 16) & 0xFF);
				this.cpu.logLine("        -> RDEVMDL  = 0x%02X   (real device model number)", (rdevInfo >> 8) & 0xFF);
				this.cpu.logLine("        -> RDEVFTR  = 0x%02X   (real device feature code)", rdevInfo & 0xFF);
			}
		}
		this.cpu.setPswConditionCode((byte)0);
		if (logDiagnose) { this.cpu.logLine("     => CC = 0"); }
		
		return;
	}
	
	// DIAG X'58' -- 3270 Virtual Console Interface
	
	private void doDiagX58(int rx, int ry) {
		int ccwAddr = this.cpu.getGPR(rx);
		int cuu = this.cpu.getGPR(ry) & 0x0000FFFF;
		
		// is the virtual device defined?
		DeviceHandler handler = this.getDevice(cuu);
		if (handler == null) {
			this.cpu.setPswConditionCode((byte)1);
			return;
		}
		
		// we must replace the command of 1st command by a combination of command and CTL byte,
		// as our channel handler ignores and thus does not pass the CTL byte (defined as being
		// ignored by PrincOps!)
		byte op1 = this.cpu.peekMainMemByte(ccwAddr);
		byte ctl = this.cpu.peekMainMemByte(ccwAddr + 5);
		try {
			if (op1 == (byte)0x19 && ctl == (byte)0xFF) {
				this.cpu.pokeMainMem(ccwAddr, (byte)ConsoleCommandCodes.ClearScreen);
			} else if (op1 == (byte)0x19) {
				// display data in the 3270 CP console screen emulation (a MECAFF fullscreen
				// application) at a specific line with/without clearing the 3270 screen 
				int orderPart = ((ctl & 0x80) != 0)
						? 0x07  // clear screen before writing (control)
						: 0x05; // no clear screen (write)
				int linePart = (ctl & 0x1F) << 3; // max. 32 lines supported !!!!
				this.cpu.pokeMainMem(ccwAddr, (byte)(orderPart | linePart));
			} else if (op1 == (byte)0x29) {
				// fullscreen write
				if (ctl == (byte)0x80 || ctl == (byte)0x90) {
					// Erase/Write
					this.cpu.pokeMainMem(ccwAddr, (byte)ConsoleCommandCodes.Write_FS_EW);
				} else if (ctl == (byte)0xC0 || ctl == (byte)0xD0 || ctl == (byte)0x40) {
					// Erase/Write Alternate
					this.cpu.pokeMainMem(ccwAddr, (byte)ConsoleCommandCodes.Write_FS_EWA);
				} else if (ctl == (byte)0x20) {
					// Write Structured Field 
					this.cpu.pokeMainMem(ccwAddr, (byte)ConsoleCommandCodes.Write_FS_WSF);
				} else {
					// anything else is: Write
					this.cpu.pokeMainMem(ccwAddr, (byte)ConsoleCommandCodes.Write_FS_W);
				}
			} else if (op1 == (byte)0x2A) {
				// fullscreen read
				if ((ctl & (byte)0x80) != 0) {
					// Read Modified
					this.cpu.pokeMainMem(ccwAddr, (byte)ConsoleCommandCodes.Read_FS_RM);
				} else {
					// Read Buffer
					this.cpu.pokeMainMem(ccwAddr, (byte)ConsoleCommandCodes.Read_FS_RB);
				}
			} // anything else is passed unchanged
			
			// execute the CCW chain
			boolean ioOk = handler.processFromAddress(ccwAddr, _00); // protection key is a dummy...
			if (ioOk) {
				this.cpu.setPswConditionCode((byte)0); // "I/O completed with no errors"
			} else {
				this.cpu.setPswConditionCode((byte)1); // "I/O completed with errors"
			}
			
		} finally {
			// restore the command of the first CCW
			this.cpu.pokeMainMem(ccwAddr, op1);
		}
	}
	
	// DIAG X'5C' -- Error Message Editing
	
	private boolean vmmCode = false; // include the code in the result?
	private boolean vmmText = true;  // include the message text in the result?
	
	public boolean getVmmCode() { return this.vmmCode; }

	public void setVmmCode(boolean vmmcode) { this.vmmCode = vmmcode; }

	public boolean isVmmText() { return this.vmmText; }

	public void setVmmText(boolean vmmtext) { this.vmmText = vmmtext; }

	private void doDiagX5C(int rx, int ry) {
		int msgAddr = this.cpu.getGPR(rx);
		int msgLen = this.cpu.getGPR(ry);
		
		if (this.vmmCode && !this.vmmText) {
			msgLen = 10;
		} else if (!this.vmmCode && this.vmmText) {
			// skip 10 bytes for the code and 1 blank
			msgAddr += 11;
			msgLen -= 11;
		} else if (!this.vmmCode && !this.vmmText) {
			msgLen = 0;
		}
		this.cpu.setGPR(rx, msgAddr);
		this.cpu.setGPR(ry, msgLen);
		
		return;
	}
	
	// DIAG X'60' -- Determine the Virtual Machine Storage Size
	
	// a VM always has the full 16M, this value is simply the value
	// lied to the invoker and set by the SET STORAGE command 
	// initially 15M, to let CMS load CMSSEG
    // (CMS will not do it if more the 15M...)
	private int simulatedStorageSize = 15 * 1024 * 1024;
	
	public int getSimulatedStorageSize() { return simulatedStorageSize; }

	public void setSimulatedStorageSize(int simulatedStorageSize) {
		this.simulatedStorageSize = simulatedStorageSize;
		this.needsIpl = true;
	}

	private void doDiagX60(int rx, int ry) {
		this.cpu.setGPR(rx, this.simulatedStorageSize);
		return;
	}
	
	// DIAG X'64' -- Finding, Loading and Purging a Named Segment
	protected final ArrayList<NamedSegment> loadedSegments = new ArrayList<NamedSegment>();
	private EbcdicHandler segname = new EbcdicHandler();
	
	private void doDiagX64(int rx, int ry) {
		// get the parameters
		int segNameAddress = this.cpu.getGPR(rx);
		int subcode = this.cpu.getGPR(ry);
		
		// get the segment name and the segment
		this.segname.reset();
		for (int i = 0; i < 8; i++) {
			byte b = this.cpu.peekMainMemByte(segNameAddress + i);
			if (b == _40) { break; }
			this.segname.appendEbcdicChar(b);
		}
		String segName = this.segname.getString().toUpperCase();
		NamedSegment segment = this.findNamedSegment(segName);
		
		if (logDiagnose) { this.cpu.logLine("     -> subCode = 0x%02X, segment name = '%s'", subcode, segName); }
		
		// signal the error if the segment is undefined
		if (segment == null) {
			this.cpu.setPswConditionCode(_02);
			this.cpu.setGPR(ry, 44); // named segment does not exists
			return;
		}
		
		// process the requested subFunction
		if (subcode == 0x0C) {        // FINDSYS --  Finds the starting address of the named segment
			this.cpu.setPswConditionCode((this.loadedSegments.contains(segment)) ? _00 : _01);
			this.cpu.setGPR(rx, segment.getFirstMemAddress());
			this.cpu.setGPR(ry, segment.getLastMemAddress());
		} else if (subcode == 0x00    // LOADSYS -- Loads a named segment in shared mode
				|| subcode == 0x04) { // LOADSYS -- Loads a named segment in nonshared mode
			// as the System Programmer's Guide leaves undefined what happens in the segment is already loaded:
			// re-load it => overwrite changes!
			try {
				segment.loadSegment(this.cpu, false);
			} catch (PSWException e) {
				// cannot occur: no IPL => PSW unchanged => no PSW exception
			}
			this.cpu.setPswConditionCode(_00);
			this.cpu.setGPR(rx, segment.getFirstMemAddress());
			if (!this.loadedSegments.contains(segment)) {
				this.loadedSegments.add(segment);
			}
		} else if (subcode == 0x08) { // PURGESYS -- Releases the named segment from virtual storage
			if (this.loadedSegments.contains(segment)) {
				segment.purgeSegmentSpace(this.cpu);
				this.cpu.setPswConditionCode(_00);
				this.loadedSegments.remove(segment);
			} else {
				// when the segment was not loaded, the System Programmer's Guide is unclear if 
				// => CC1 ("was not found in the virtual machine")
				// or
				// => CC2 / ry = 44 ("was not previously loaded via LOADSYS")
				// we go on with CC1 because CC2/ry=44 is for "invalid segment" in all other cases...
				this.cpu.setPswConditionCode(_01); 
			}
		} else {
			// invalid subcode : no condition code / ry-value defined for that in the System Programmer's Guide :-(
			this.cpu.setPswConditionCode(_03);
			this.cpu.setGPR(ry, -1);
		}
		return;
	}
	
	/*
	 * privileged instructions (not handled as problem state instruction by the CPU)
	 */
	
	private volatile long sioCount = 0;
	private volatile long sioNanoSecs = 0;
	
	public long getSioCount() { return this.sioCount; }
	public long getSioNanoSecs() { return this.sioNanoSecs; }
	
	/**
	 * Process an I/O instruction.
	 *  
	 * @param insnCode the instruction code of the I/O operation.
	 */
	protected void processIO(int insnCode) {
		// get the device(handler) for the I/O operation
		int devCuu = this.cpu.getPrivOpAddr(); // the address resulting of SI-parameter D2(B2) is the device address
		DeviceHandler dev = this.getDevice(devCuu);
		if (dev == null) {
			// requested device does not exist in this virtual machine
			this.cpu.setPswConditionCode((byte)3); // Channel not operational
			return;
		}
		
		// interpret & execute the instruction
		switch(insnCode) {
		
		case 0x9C00: // SIO - Start I/O
		case 0x9C01: // SIOF - Start I/O Fast Release (not really supported, falling back to SIO)
			this.sioCount++;
			long startNanosecs = System.nanoTime();
			// execute the CCW chain on this device
			boolean ioOk = dev.processCAW();
			// check the outcome of the I/O operation
			if (ioOk && dev.hasPendingCompletionInterrupt()) {
				// something has been done on the device, so the SIO(F) operation succeeded and more
				// information about the result of the I/O will be available with the completion interrupt
				// or a TIO instruction on this device 
				this.cpu.enqueueInterrupt(dev);
				this.cpu.setPswConditionCode((byte)0);
			} else {
				// the CCW(-start) was not OK, so the SIO(F) operation itself failed
				// or some CCW in the run ended with a CHECK or EXCEPTION problem 
				dev.clearPendingCompletionInterrupt();
				dev.storeCSWStatus();
				this.cpu.setPswConditionCode(dev.getCswCC());
			}
			this.sioNanoSecs += System.nanoTime() - startNanosecs;
			break;
			
		case 0x9D00: // TIO - Test I/O
		case 0x9D01: // CLRIO - Clear I/O (not really supported, falling back to TIO)
			// as all I/O occurs synchronously, the case "Channel or subchannel busy"
			// cannot occur, so only CC=1 (an interrupt is pending) or CC=0 (available,
			// meaning no interrupt pending) can be generated
			if (!dev.hasPendingCompletionInterrupt() && !dev.hasPendingAsyncInterrupt()) {
				// no interrupt pending => available
				this.cpu.setPswConditionCode((byte)0);
				break;
			}
			
			// bring device status information into memory and clear
			// the enqueued interrupt, as it is "consumed" by this test
			// if both interrupt types are pending, completion interrupts
			// have priority, leaving an Attention interrupt for signaling
			// external activity
			if (dev.hasPendingCompletionInterrupt()) {
				dev.storeCSW();
				dev.clearPendingCompletionInterrupt();
				this.cpu.dequeueInterrupt(dev, true, false);
			} else {
				dev.storeAttentionPSW();
				dev.clearPendingAsyncInterrupt();
				this.cpu.dequeueInterrupt(dev, false, true);
			}
			
			// set condition code
			this.cpu.setPswConditionCode((byte)1); // CSW stored
			break;
			
		case 0x9E00: // HIO - Halt I/O
		case 0x9E01: // HDV - Halt Device
			// all I/O operations are executed synchronously, so there is nothing to halt when
			// this instruction is executed. Therefore, we fall back to the following state
			// (see "programming note" for HDV in PrincOps-1975, p. 202):
			//    Condition Code 1 with Zeros in the Status
			//    Field of the CSW indicates that the addressed
			//    device is selected and signaled to terminate the
			//    current operation, if any.
			dev.storeCSW();                    // let the device store its own status
			this.cpu.pokeMainMem(68, (byte)0); // overwrite the bytes ...
			this.cpu.pokeMainMem(69, (byte)0); // ... of the status field
			this.cpu.setPswConditionCode((byte)1); // signal the stored CSW
			break;
			
		case 0x9F00: // TCH - Test Channel
			this.cpu.setPswConditionCode((byte)0); // Channel available
			break;
		
		}
	}
	
	/**
	 * Execute a privileged instruction not already processed by the CPU as an unprivileged
	 * instruction.
	 *  
	 * @param insnCode the instruction code not processed by the CPU.
	 */
	protected void processPrivilegedInstruction(int insnCode) {
		if ((insnCode & 0xFF000000) == 0x83000000) {
			// DIAG -- Diagnose
			this.processDiagnose(insnCode);
			return;
		}

		int insnAt = this.cpu.getPrivInsnLocation();
		if  ((insnCode & 0xFFFFF000) == 0x00009000) {
			// I/O instruction
			this.processIO(insnCode);
		} else if  ((insnCode & 0xFFFFFF00) == 0xB200) {
			// 2-byte opcode, with the following instructions already implemented as non-privileged
			// by the CPU:
			//  - 0x0B = IPK - Insert PSW Key
			//  - 0x13 = RRB - Reset Reference Bit
			//  - 0x0A = SKPA - Set PSW Key From Address
			int subcode = insnCode & 0xFF;
			int addr = this.cpu.getPrivOpAddr();
			String unsupportedMsg = "Unsupported privileged instruction %s (0xB2%02X) at 0x%06X";
			String clockTimerMsg = "Unimplemented clock/timer priv. instruction %s (0xB2%02X) at 0x%06X";
			switch(subcode) {
			
			case 0x03: // STDIC - Store Channel ID
				// 'addr' is the CUU of a device for which the channel id is to be stored
				// here:
				// -> "Type" is selector for CUU 0xx and Block multiplexer for all others
				// -> "Channel Model Number" is 0 ("the channel model is implied by the channel type and the CPU model")
				// -> "Maximum IOEL Length" is 0 ("the channel never stores logout 
				DeviceHandler dev = this.getDevice(addr);
				if (dev == null) {
					// device not found
					this.cpu.setPswConditionCode((byte)3); // not operational
					break;
				}
				int channelId = (dev.getChannelNo() == 0) ? 0 : 0x10000000;
				this.cpu.pokeMainMem(168, channelId);
				this.cpu.setPswConditionCode((byte)0); // Channel ID correctly stored
				break;
			
			// timer instructions (very probably needing to be implemented)
				
			case 0x04: // SCK - Set clock
				this.disableRunning(clockTimerMsg, "SCK", subcode, insnAt);
				break;
				
			case 0x05: // STCK . Store Clock
				this.cpu.pokeMainMem(this.cpu.getPrivOpAddr(), this.getNextStoredTodTS());
				this.cpu.setPswConditionCode((byte)0);
				break;
				
			case 0x06: // SCKC - Set clock comparator
				this.disableRunning(clockTimerMsg, "SCKC", subcode, insnAt);
				break;
				
			case 0x07: // STCKC - Store Clock Comparator
				this.disableRunning(clockTimerMsg, "STCKC", subcode, insnAt);
				break;
				
			case 0x08: // SPT - Set CPU Timer
				this.disableRunning(clockTimerMsg, "SPT", subcode, insnAt);
				break;
				
			case 0x09: // STPT D2(B2) - Store CPU Timer
				this.disableRunning(clockTimerMsg, "STPT", subcode, insnAt);
				break;
				
				
			// unsupported(able) instructions in a virtual machine for CMS
				
			case 0x02: // STIDP - Store CPU ID
				// this.disableRunning(unsupportedMsg, "STIDP", subcode, insnAt);
				// TODO: make this configurable
				// Version: 0xFD
				// CPUID  : 0x098052
				// Model  : 0x4381
				// MCEL   : 0x0000
				long stdpid = 0xFD09805243810000L;
				this.cpu.pokeMainMem(this.cpu.getPrivOpAddr(), stdpid);
				break;
			
			case 0x10: // SPX - Set Prefix
				this.disableRunning(unsupportedMsg, "SPX", subcode, insnAt);
				break;
			
			case 0x11: // STPX - Store Prefix
				this.disableRunning(unsupportedMsg, "STPX", subcode, insnAt);
				break;
			
			case 0x12: // STAP - Store CPU Address
				this.disableRunning(unsupportedMsg, "STAP", subcode, insnAt);
				break;
			
			case 0x0D: // PTLB - Purge TLB
				this.disableRunning(unsupportedMsg, "PTLB", subcode, insnAt);
				break;
			
			default:
				this.disableRunning(
						"Unknown privileged instruction at 0x%06X : code = 0x%04X", 
						insnAt, insnCode);
				
			}
		} else {
			// 1-byte opcode, with the following instructions already implemented as non-privileged
			// by the CPU:
			//  - 0x08 = SSK   - Set Storage Key
			//  - 0x09 = ISK   - Insert Storage Key
			//  - 0x80 = SSM   - Set System Mask
			//  - 0x82 = LPSW  - Load PSW
			//  - 0xAC = STNSM - Store Then AND System Mask
			//  - 0xAD = STOSM - Store Then OR System Mask
			switch(insnCode) {
			
			case (byte)0x84: // WRD   - Write Direct
			case (byte)0x85: // RDD   - Read Direct
			case (byte)0xAE: // SIGP  - Signal Processor
			case (byte)0xB1: // LRA   - Load Real Address
			case (byte)0xB6: // STCTL - Store Control
			case (byte)0xB7: // LCTL  - Load Control
				this.disableRunning("Unsupported privileged instruction %s (0x%02X) at 0x06X", 
						this.cpu.getMnemonic((byte)insnCode), insnCode, insnAt);
			break;
			
			default:
				this.disableRunning(
						"Unknown instruction at 0x%06X : code = 0x%02X", 
						insnAt, insnCode);
				
			}
		}
	}
	
	/**
	 * Interrupt source enqueued for non-device sources (timer or the like)
	 */
	private static class ExternalInterrupt implements iInterruptSource {
		
		private final short interruptCode;
		
		private boolean activated = false;
		
		public ExternalInterrupt(short code) {
			this.interruptCode = code;
		}
		
		/**
		 * Let the interrupt source respond "yes" the next time it
		 * is asked if an interrupt is pending. 
		 */
		public void activate() { this.activated = true; }

		@Override
		public byte getIntrMask() {
			return (byte)0x01; // external interrupt
		}

		@Override
		public boolean hasPendingAsyncInterrupt() {
			return this.activated;
		}

		@Override
		public boolean hasPendingCompletionInterrupt() {
			return false; // no completion for external interrupt sources
		}

		@Override
		public void initiateAsyncInterrupt(Cpu370Bc cpu) throws PSWException {
			if (this.activated) {
				cpu.initiateInterrupt(
						24,                  // location of external interrupt old PSW
						88,                  // location of external interrupt new PSW
						this.interruptCode); // intrCode
				
				this.activated = false;
			}
		}

		@Override
		public void initiateCompletionInterrupt(Cpu370Bc cpu) {
			// ignored, as this is not a device...
			// throw new IllegalStateException("Attempt to initiate completion interrupt on external interrupt");
		}
		
	}
	
	/*
	 * clocks and timers
	 */
	
	private boolean doIntervalTimerInterrupts = true;
	
	public boolean isDoIntervalTimerInterrupts() {
		return this.doIntervalTimerInterrupts;
	}

	public void setDoIntervalTimerInterrupts(boolean doIntervalTimerInterrupts) {
		this.doIntervalTimerInterrupts = doIntervalTimerInterrupts;
	}

	// "now" in nanoseconds, set by getNow() and to be used by all other timer functions in a timer verification run
	private long nowInNanosecs;
	
	private void getNow() { this.nowInNanosecs = System.nanoTime(); } 
	
	// interval timer :: WORD at location 80d
	// see: PrincOps-1975 at pages 49 and 86
	private final static int ITIMER_LOCATION = 80;
	private final static long NSECS_FOR_INTERVALTIMING = 3333333; // 3,333 ms as nanosecs
	private long intervalTimerLastUpdate = System.nanoTime();
	private long intervalTimerNextCheck = System.currentTimeMillis() - 1; // force checking  
	private boolean intervalTimerLocation80Positive = true; // => next cpu.reset() will set location 80 to 0 => this would get true...
	private ExternalInterrupt intervalTimerInterruptSource = new ExternalInterrupt((short)0x0080);

	private void checkIntervalTimer() {
		long intervalElapsed = this.nowInNanosecs - this.intervalTimerLastUpdate;
		if (intervalElapsed > NSECS_FOR_INTERVALTIMING) {
			int intervals = (int)(intervalElapsed / NSECS_FOR_INTERVALTIMING);
			int itimer = this.cpu.peekMainMemInt(ITIMER_LOCATION);
			itimer -= (intervals * 256); // "the contents of the timer are reduced by one in bit position 23 every 1/300 of a second"
			this.cpu.pokeMainMem(ITIMER_LOCATION, itimer);
			this.intervalTimerLastUpdate += intervalElapsed * NSECS_FOR_INTERVALTIMING;
			this.intervalTimerNextCheck = (this.intervalTimerLastUpdate + NSECS_FOR_INTERVALTIMING) / 1000000; // nsecs -> msecs 
		}
		if (this.intervalTimerLocation80Positive) {
			if (this.cpu.peekMainMemInt(ITIMER_LOCATION) < 0) {
				if (this.doIntervalTimerInterrupts) {
					this.intervalTimerInterruptSource.activate();
					this.cpu.enqueueInterrupt(this.intervalTimerInterruptSource);
				}
				this.intervalTimerLocation80Positive = false;
			}
		} else if (this.cpu.peekMainMemInt(ITIMER_LOCATION) >= 0) {
			this.intervalTimerLocation80Positive = true;
		}
	}
	
	private void resetIntervalTimer() {
		this.intervalTimerLastUpdate = System.nanoTime();
		this.intervalTimerNextCheck = System.currentTimeMillis() - 1;  
		this.intervalTimerLocation80Positive = true;
	}
	
	// time of day clock (aka TOD)
	
	// seconds since IBM's base timestamp (1900-01-01 00:00:00) to Java's base timestamp (1970-01-01 00:00:00), both UTC
	// seconds per day * (70 years + number of leap days) * 1-Mio-Microseconds
	private final static long TOD_MICROSECS_1900_to_1970 = 86400L * (long)( 70 * 365 + (70 / 4) ) * 1000000L;
	
	// base values used to compute "now" in microseconds
	private long baseTSmicrosecs = System.currentTimeMillis() * 1000;
	private long baseTSnanosecs = System.nanoTime();
			
	// IBM's TOD DoubleWord is incremented at Bit-Pos 51 every microsecond, leaving the lower 12 bits unused...
	// => to avoid overflow on a long-value (signed in Java), having Bit 0 set since ~ 1971-05-11), we use
	//    only the microseconds (upper 52 bit shifted to the right by 12 bits)
	// => when storing the TOD-value, the lower 12 bits will be added, using them bit for uniqueness
	private long lastStoredTodTS = 0; // microseconds
	private int lastStoredTodTSUniquer = 0;
	
	private long getNowInMicrosecs() {
		long javaNowMicrosecs = this.baseTSmicrosecs + ((System.nanoTime() - this.baseTSnanosecs) / 1000);
		return javaNowMicrosecs + TOD_MICROSECS_1900_to_1970;
	}
	
	private long getNextStoredTodTS() {
		long toBeStoredTS;
		long nowTS = getNowInMicrosecs();
		if (this.lastStoredTodTS == nowTS) {
			this.lastStoredTodTSUniquer++;
			toBeStoredTS = (nowTS << 12) + (this.lastStoredTodTSUniquer & 0x0FFF); // hoping for less than 4096 STCK per microsecond
		} else {
			this.lastStoredTodTS = nowTS;
			this.lastStoredTodTSUniquer = 0;
			toBeStoredTS = (nowTS << 12);
		}
		return toBeStoredTS;
	}
	
	/*
	 * main run loop for the virtual machine:
	 * -> check if runnable
	 * -> check for timeouts on timers
	 * -> check for new interrupts from devices
	 * -> if CPU is in enable wait state: wait a while and repeat above
	 * -> run a number of CPU statements and check outcome, possibly executing a privileged instruction
	 * -> repeat above until a halt is requested from outside or a problem occured
	 * 
	 * returns if processing can be continued with another call to this routine (true) or if the VM
	 * has reached a state requiring a fresh IPL (because of a disabled wait, unimplemented instruction
	 * or DIAG-functionality or...)
	 */
	
	public boolean run() {
		while(true) {
			// check if runnable
			if (this.needsIpl) { return false; }
			
			// check if CP commands must be processed and/or if the VM has to fall
			// back into the CP command loop for user interaction
			this.cpCommandExecutor.executePendingAsyncCommands();
			if (this.isToBeHalted()) { return true; }
			
			// check timers
			this.getNow();
			if (this.intervalTimerNextCheck < System.currentTimeMillis()) {
				this.checkIntervalTimer();
			}
			// TODO: implement the other time comparators!
			
			// check for new async interrupts from devices
			// (completion interrupts are managed by I/O instructions!)
			for (DeviceHandler d : this.devices) {
				if (d.hasPendingAsyncInterrupt()) {
					this.cpu.enqueueInterrupt(d); // this one ignores interrupt sources already enqueued 
				}
			}
			
			// if CPU is in enabled wait state and no enabled interrupt pending: wait a while and repeat above
			if (this.cpu.isInEnabledWaitState() && !this.cpu.hasEnabledInterrupt()) {
				try {
					Thread.sleep(IDLE_MSECS_FOR_ENABLED_WAIT);
				} catch (InterruptedException e) {
					return true;
				}
				continue;
			}
			
			// run the program
			int outcome;
			try {
				// run a number of CPU statements for at most MAX_MILLISECONDS_PER_RUN (10 ms)
				outcome = this.cpu.execute(maxInstructionsPerRun);
				
				// check outcome, possibly executing a privileged instruction
				if (outcome == Cpu370Bc.EXECSTATE_ENABLED_WAIT) {
					continue;
				} else if (outcome == Cpu370Bc.EXECSTATE_UNKNOWN_INSTRUCTION) {
					int insnAt = this.cpu.getPrivInsnLocation(); // also valid for unknown instruction
					int insnCode = this.cpu.peekMainMemByte(insnAt) & 0xFF;
					String insnName = this.cpu.getMnemonic((byte)insnCode);
					this.disableRunning(
							"Unknown instruction at 0x%06X : code = 0x%02X (%s)", 
							insnAt, insnCode, insnName);
					return false;
				} else if (outcome != 0) {
					this.processPrivilegedInstruction(outcome);
				}
			} catch (PSWException e) {
				switch(e.getProblemType()) {
				
				case ECMode:
					this.disableRunning(
							"System entered unsupported EC control mode from PSW loaded from 0x%06X",
							e.getPswFrom());
					return false;
					
				case DisabledWait:
					this.disableRunning(
							"System entered Disabled Wait state with PSW loaded from 0x%06X",
							e.getPswFrom());
					return false;
					
				case Breakpoint: 
					// TODO: how to handle by a debugger...?
					return true;
				
				default:
					this.disableRunning(
							"System entered unknown/invalid state with PSW loaded from 0x%06X",
							e.getPswFrom());
					return false;
					
				}
			}
		}
		
		// unreachable code, says the compiler
		// return !this.needsIpl;
	}
	
	public boolean singleStep() {
		if (this.needsIpl) {
			this.signalProblem("** Virtual macine needs re-IPL, not stepped");
			return false;
		}
		if (this.cpu.isInEnabledWaitState()) {
			this.signalProblem("** Virtual machine still in disabled wait state, not stepped");
			return false;
		}
		
		// run one instruction
		int outcome;
		try {
			outcome = this.cpu.execute(1);
			
			// check outcome, possibly executing a privileged instruction
			if (outcome == Cpu370Bc.EXECSTATE_ENABLED_WAIT) {
				this.signalProblem("Info: ** Virtual machine entered disabled wait state **");
				return false;
			} else if (outcome == Cpu370Bc.EXECSTATE_UNKNOWN_INSTRUCTION) {
				int insnAt = this.cpu.getPrivInsnLocation(); // also valid for unknown instruction
				int insnCode = this.cpu.peekMainMemByte(insnAt) & 0xFF;
				String insnName = this.cpu.getMnemonic((byte)insnCode);
				this.disableRunning(
						"Unknown instruction at 0x%06X : code = 0x%02X (%s)", 
						insnAt, insnCode, insnName);
				return false;
			} else if (outcome != 0) {
				this.signalProblem("Info: ** privileged instruction **");
				this.processPrivilegedInstruction(outcome);
			}
		} catch (PSWException e) {
			switch(e.getProblemType()) {
			
			case ECMode:
				this.disableRunning(
						"System entered unsupported EC control mode from PSW loaded from 0x%06X",
						e.getPswFrom());
				return false;
				
			case DisabledWait:
				this.disableRunning(
						"System entered Disabled Wait state from PSW loaded from 0x%06X",
						e.getPswFrom());
				return false;
				
			case Breakpoint:
				return true; // TODO: how to handle by a debugger...?
			
			default:
				this.disableRunning(
						"System entered unknown/invalid state from PSW loaded from 0x%06X",
						e.getPswFrom());
				return false;
				
			}
		}
		
		return true;
	}
}
