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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

import dev.hawala.vm370.ebcdic.EbcdicHandler;
import dev.hawala.vm370.vm.iSvcInterceptor;
import dev.hawala.vm370.vm.device.DeviceHandler;
import dev.hawala.vm370.vm.device.iDevice;

/**
 * Abstract base class for the emulation of a subset S/370 CPU in BC mode
 * as seen from inside a VM/370 virtual machine.
 * 
 * Restrictions on memory access with respect to the S/370-PrincOps:
 * -> no memory access synchronisation (instructions: TS, BCR(15,0), CS, CDS)
 *    rationale:
 *      -> single CPU design
 *      -> I/O data transfers occur between CPU instructions (DIAG-code, I/O-interrupts)
 * -> no address protection (simplification)
 * -> no DAT (dynamic address translation), each CPU/VM has the full 24 bit addressable
 *    memory mapped (mapping virtual segments must be emulated by copying the segment content)
 * 
 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2015
 */
public abstract class Cpu370Bc implements iProcessorEventTracker {

	/*
	** this CPU's (expanded) Processor Status Word (PSW)
	*/
	protected boolean pswIntrChannelEnabled[] = {false, false, false, false, false, false};
	protected boolean pswIntrIoEnabled = false;
	protected boolean pswIntrExternalEnabled = false;
	protected boolean pswAnyIntrEnabled = false;
	protected byte pswIntrMaskByte = (byte)0x00; 
	protected short pswProtectionKey = 0;
	protected boolean pswEcMode = false; // never changed, but not final for later extensions...
	protected boolean pswMachineCheckIntrEnabled = false;
	protected boolean pswWaitState = false;
	protected boolean pswProblemState = true; // never changed, but not final for later extensions...
	protected short pswInterruptionCode = 0;
	protected short pswInstructionLengthCode = 0; // number of half-words in last instruction: 1 | 2 | 3
	protected byte pswConditionCode = 0;
	protected boolean pswProgramMaskFixedOverflow = false;
	protected boolean pswProgramMaskDecimalOverflow = false;
	protected boolean pswProgramMaskExponentUnderflow = false;
	protected boolean pswProgramMaskSignificance = false;
	protected int pswInstructionAddress = 0;
	
	// constants for the Condition-Code represented as byte (but in fact 2 bits)
	protected final byte CC0 = (byte)0;
	protected final byte CC1 = (byte)1;
	protected final byte CC2 = (byte)2;
	protected final byte CC3 = (byte)3;
	protected final byte CCINV = (byte)-1;

	// constants for program exception types
	protected final short INTR_PGM_OPERATION_EXCEPTION     = (short)0x0001; // invalid operation/instruction code
	protected final short INTR_PGM_PRIVILEGE_EXCEPTION     = (short)0x0002;
	protected final short INTR_PGM_EXECUTE_EXCEPTION       = (short)0x0003;
	protected final short INTR_PGM_ADDRESSING_EXCEPTION    = (short)0x0005;
	protected final short INTR_PGM_SPECIFICATION_EXCEPTION = (short)0x0006; // see PrincOps-1987, 6-26
	protected final short INTR_PGM_DATA_EXCEPTION          = (short)0x0007;
	protected final short INTR_PGM_FIXEDPOINT_OVERFLOW     = (short)0x0008;
	protected final short INTR_PGM_FIXEDPOINT_DIVIDE       = (short)0x0009;
	protected final short INTR_PGM_DECIMAL_OVERFLOW        = (short)0x000A;
	protected final short INTR_PGM_DECIMAL_DIVIDE          = (short)0x000B;
	protected final short INTR_PGM_EXPONENT_OVERFLOW       = (short)0x000C;
	protected final short INTR_PGM_EXPONENT_UNDERFLOW      = (short)0x000D;
	protected final short INTR_PGM_SIGNIFICANCE            = (short)0x000E;
	protected final short INTR_PGM_FLOAT_DIVIDE            = (short)0x000F;
	
	/**
	 * Set the cpu interrupt enabled flags from a byte holding the mask
	 *   
	 * @param b the interrupt mask byte.
	 */
	public void setIntrMask(byte b) {
		this.pswIntrMaskByte = b;
		this.pswIntrChannelEnabled[0] = (b & (byte)0x80) != 0;
		this.pswIntrChannelEnabled[1] = (b & (byte)0x40) != 0;
		this.pswIntrChannelEnabled[2] = (b & (byte)0x20) != 0;
		this.pswIntrChannelEnabled[3] = (b & (byte)0x10) != 0;
		this.pswIntrChannelEnabled[4] = (b & (byte)0x08) != 0;
		this.pswIntrChannelEnabled[5] = (b & (byte)0x04) != 0;
		this.pswIntrIoEnabled = (b & (byte)0x02) != 0;
		this.pswIntrExternalEnabled = (b & (byte)0x01) != 0;
		this.pswAnyIntrEnabled = (b != 0);
	}
	
	/**
	 * Load the CPU expanded PSW from the given memory location
	 * from the CPU's memory.
	 * 
	 * @param at offset of the PSW in the CPU's memory.
	 * @throws PSWException if the resulting PSW state is invalid for the CPU.
	 */
	public void readPswFrom(int at) throws PSWException {
		this.readPswFrom(this.mem, at);
	}
	
	/**
	 * Load the CPU expanded PSW from the given memory location.
	 * 
	 * @param from the memory from where to load the PSW.
	 * @param at offset of the PSW in {@code from}.
	 * @throws PSWException if the resulting PSW state is invalid for the CPU.
	 */
	public void readPswFrom(byte[] from, int at) throws PSWException {
		// byte 1: interrupt mask bits
		byte b = from[at];
		this.setIntrMask(b);
		
		// byte 2: protection key, bc/ec mode, machine check mask, wait state, problem state
		b = from[at + 1];
		this.pswProtectionKey = (short)((b & 0xF0) >> 4);
		this.pswEcMode = false; // this one is constant/fixed
		this.pswMachineCheckIntrEnabled = (b & (byte)0x04) != 0;
		this.pswWaitState = (b & (byte)0x02) != 0;
		this.pswProblemState = true; // this one is constant/fixed
		
		// byte 3 .. 4: interruption code (ignored when loading the PSW!)
		this.pswInterruptionCode = 0;
		
		// byte 5: ILC, CC, program mask
		b = from[at + 4];
		this.pswInstructionLengthCode = 0;
		this.pswConditionCode = (byte)((b & 0x30) >> 4);
		this.pswProgramMaskFixedOverflow = (b & (byte)0x08) != 0;
		this.pswProgramMaskDecimalOverflow = (b & (byte)0x04) != 0;
		this.pswProgramMaskExponentUnderflow = (b & (byte)0x02) != 0;
		this.pswProgramMaskSignificance = (b & (byte)0x01) != 0;
		
		// byte 6 .. 8: instruction address
		this.pswInstructionAddress = ((from[at + 5] & 0xFF) << 16) + ((from[at + 6] & 0xFF) << 8) + (from[at + 7] & 0xFF);
		
		// throw exception if entering disabled wait state (wait but all interrupts disabled)
		if (this.pswWaitState && from[at] == 0) {
			throw new PSWException(PSWException.PSWProblemType.DisabledWait, at);
		}
		
		// throw exception if attempting to enter unsupported EC state
		if ((from[at + 1] & 0x08) != 0) {
			throw new PSWException(PSWException.PSWProblemType.ECMode, at);
		}
	}
	
	// store our PSW at the given memory location
	public void writePswTo(byte[] to, int at) {
		// byte 1: interrupt mask bits
		byte b = (byte) (
		  ((this.pswIntrChannelEnabled[0]) ? (byte)0x80 : 0)
		| ((this.pswIntrChannelEnabled[1]) ? (byte)0x40 : 0)
		| ((this.pswIntrChannelEnabled[2]) ? (byte)0x20 : 0)
		| ((this.pswIntrChannelEnabled[3]) ? (byte)0x10 : 0)
		| ((this.pswIntrChannelEnabled[4]) ? (byte)0x08 : 0)
		| ((this.pswIntrChannelEnabled[5]) ? (byte)0x04 : 0)
		| ((this.pswIntrIoEnabled) ? (byte)0x02 : 0)
		| ((this.pswIntrExternalEnabled) ? (byte)0x01 : 0));
		to[at] = b;
		
		// byte 2: protection key, bc/ec mode, machine check mask, wait state, problem state
		b = (byte) (
		  (byte)((this.pswProtectionKey & 0x0F) << 4)
		/* ec mode: always 0 !! | ((this.pswEcMode) ? (byte)0x08 : 0) */ 
		| ((this.pswMachineCheckIntrEnabled) ? (byte)0x04 : 0)
		| ((this.pswWaitState) ? (byte)0x02 : 0)
		| (byte)0x01); // problem state is fixed to on 
		to[at + 1] = b;
		
		// byte 3 .. 4: interruption code
		to[at + 2] = (byte)((this.pswInterruptionCode >> 8) & 0xFF);
		to[at + 3] = (byte)(this.pswInterruptionCode & 0xFF);
		
		// byte 5: ILC, CC, program mask
		b = (byte) (
		  (byte)((this.pswInstructionLengthCode & 0x03) << 6)
		| (byte)((this.pswConditionCode & 0x03) << 4)
		| ((this.pswProgramMaskFixedOverflow) ? (byte)0x08 : 0)
		| ((this.pswProgramMaskDecimalOverflow) ? (byte)0x04 : 0)
		| ((this.pswProgramMaskExponentUnderflow) ? (byte)0x02 : 0)
		| ((this.pswProgramMaskSignificance) ? (byte)0x01 : 0));
		to[at + 4] = b;
		
		// byte 6 .. 8: instruction address
		to[at + 5] = (byte)((this.pswInstructionAddress >> 16) & 0xFF);
		to[at + 6] = (byte)((this.pswInstructionAddress >> 8) & 0xFF);
		to[at + 7] = (byte)(this.pswInstructionAddress & 0xFF);
	}
	
	/**
	 * Check if the CPU is in an enabled wait state (wait state with at least one
	 * interrupt source allowed).
	 * 
	 * @return {@code true} if the CPU waits with at least one interrupt source enabled. 
	 */
	public boolean isInEnabledWaitState() {
		return (this.pswWaitState && this.pswIntrMaskByte != 0);
	}
	
	/**
	 * Initiate a program exception interrupt for the given exception code.
	 * 
	 * @param intrCode the interrupt code for the program exception.
	 * @throws PSWException if the resulting PSW state is invalid for the CPU.
	 */
	protected void doProgramInterrupt(short intrCode) throws PSWException {
		this.initiateInterrupt(
				40,    // (decimal) location of program interrupt old PSW
				104,   // (decimal) location of program interrupt new PSW
				intrCode);
	}
	
	/**
	 * Initiate a CPU interrupt given the old and new PSW locations and
	 * the given interrupt code for the (old) PSW.
	 * 
	 * @param oldPswLocation the location where to store the old PSW 
	 * @param newPswLocation the location where to load the new PSW from
	 * @param intrCode the interrupt code to insert into the old PSW
	 * @throws PSWException  if the resulting PSW state is invalid for the CPU.
	 */
	public void initiateInterrupt(int oldPswLocation, int newPswLocation, short intrCode)  throws PSWException {
		// For I/O, external,supervisor-call, and program interruptions, the interruption code
		// comprises 16 bits and is placed in the old PSW when the old PSW specifies the BC mode
		// (PrincOps-1975, p.70)
		this.pswInterruptionCode = intrCode; // so put it in the current PSW so it is saved to the old-PSW location
		
		// save current PSW and switch to the interrupt handler's PSW 
		this.writePswTo(this.mem, oldPswLocation); 
		this.readPswFrom(this.mem, newPswLocation);
	}
	
	/*
	** other CPU components 
	*/
	
	public Cpu370Bc(byte[] memPreallocated) {
		if (memPreallocated.length < MEM_SIZE) {
			throw new IllegalArgumentException("Invalid size of preallocated memory for CPU");
		}
		this.mem = memPreallocated;
	}
	
	public Cpu370Bc() {
		ByteBuffer byteBuffer = ByteBuffer.allocate(MEM_SIZE);
		if (byteBuffer.hasArray()) {
			this.mem = byteBuffer.array();
			this.byteBuffer = byteBuffer;
		} else {
			// TODO: DROP THE ByteBuffer !!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
			this.mem  = new byte[MEM_SIZE];
		}
	}
	
	// general purpose registers
	protected int[] gpr = new int[16];

	protected final int GprMask = 0x0F; // used to constrain a gpr-index to the allowed values
	
	// main memory: always 16M
	public final int MEM_SIZE = 16 * 1024 * 1024;
	protected ByteBuffer byteBuffer = null;
	protected byte[] mem = null;
	protected final int MemMask = 0x00FFFFFF; // used to constrain memory accesses to "physical" limits
	
	// storage keys for the (non-existent) protection resp. access-tracking of the 2048-byte memory pages
	// 1 byte per 2048-byte page :: upper 4 bits: page protection key, lower 4 bits: page access tracking
	public final int PAGE_COUNT = MEM_SIZE / 2048; // this should be 8192
	protected byte[] pageKeys = new byte[PAGE_COUNT];
	
	// missing ~ unimplemented:
	// - control registers (irrelevant in BC mode)
	
	/*
	** public machine state access (used by: privileged level or unit tests)
	*/
	
	/**
	 * Reset the CPU state by setting the PSW and memory to all zeroes.
	 */
	public void resetEngine() {
		// re-initialize PSW ... zero out the PSW (with one exception: always in problem state!)
		for (int i = 0; i < this.pswIntrChannelEnabled.length; i++) {
			this.pswIntrChannelEnabled[i] = false;
		}
		this.pswIntrIoEnabled = false;
		this.pswIntrExternalEnabled = false;
		this.pswProtectionKey = 0;
		this.pswEcMode = false;
		this.pswMachineCheckIntrEnabled = false;
		this.pswWaitState = false;
		this.pswProblemState = true;
		this.pswInterruptionCode = 0;
		this.pswInstructionLengthCode = 0;
		this.pswConditionCode = 0;
		this.pswProgramMaskFixedOverflow = false;
		this.pswProgramMaskDecimalOverflow = false;
		this.pswProgramMaskExponentUnderflow = false;
		this.pswProgramMaskSignificance = false;
		this.pswInstructionAddress = 0;
		
		// clear general registers
		for (int i = 0; i < this.gpr.length; i++) {
			this.gpr[i] = 0;
		}
		
		// clear floating point registers
		this.floatImpl.resetRegisters();
		
		// clear main memory
		java.util.Arrays.fill(this.mem, (byte)0x00); // fill 16 MiB => duration ~ 15 ms (Core2,2.4MHz,32bit)
		
		// reset observed location references
		this.resetWatches();
	}
	
	public void setGPR(int r, int value) {
		if (r >= 0 && r < this.gpr.length) {
			this.gpr[r] = value;
		}
	}
	
	public int getGPR(int r) {
		if (r >= 0 && r < this.gpr.length) {
			return this.gpr[r];
		}
		throw new IllegalArgumentException("Invalid general register index");
	}

	public short getPswInterruptionCode() {
		return this.pswInterruptionCode;
	}

	public byte getPswConditionCode() {
		return this.pswConditionCode;
	}
	
	public boolean getPswIntrMaskBit(int bitNo) { // 0..7
		if ( bitNo < 0 || bitNo > 7) { throw new IllegalArgumentException("mask bit no. out of range"); }
		if (bitNo < 6) { return this.pswIntrChannelEnabled[bitNo]; }
		if (bitNo == 6) { return this.pswIntrIoEnabled; }
		return this.pswIntrExternalEnabled;
	}
	
	public short getPswProtectionKey() { return this.pswProtectionKey; }
	
	public boolean isECMode() { return this.pswEcMode; }
	
	public boolean isMachineCheckIntrEnabled() { return this.pswMachineCheckIntrEnabled; }
	
	public boolean isWaitState() { return this.pswWaitState; }
	
	public boolean isProblemState() { return this.pswProblemState; }

	public void setPswConditionCode(byte pswConditionCode) {
		this.pswConditionCode = pswConditionCode;
	}

	public boolean isPswProgramMaskFixedOverflow() {
		return this.pswProgramMaskFixedOverflow;
	}

	public void setPswProgramMaskFixedOverflow(boolean pswProgramMaskFixedOverflow) {
		this.pswProgramMaskFixedOverflow = pswProgramMaskFixedOverflow;
	}

	public boolean isPswProgramMaskDecimalOverflow() {
		return this.pswProgramMaskDecimalOverflow;
	}

	public void setPswProgramMaskDecimalOverflow(boolean pswProgramMaskDecimalOverflow) {
		this.pswProgramMaskDecimalOverflow = pswProgramMaskDecimalOverflow;
	}

	public boolean isPswProgramMaskExponentUnderflow() {
		return this.pswProgramMaskExponentUnderflow;
	}

	public void setPswProgramMaskExponentUnderflow(boolean pswProgramMaskExponentUnderflow) {
		this.pswProgramMaskExponentUnderflow = pswProgramMaskExponentUnderflow;
	}

	public boolean isPswProgramMaskSignificance() {
		return this.pswProgramMaskSignificance;
	}

	public void setPswProgramMaskSignificance(boolean pswProgramMaskSignificance) {
		this.pswProgramMaskSignificance = pswProgramMaskSignificance;
	}

	public int getPswInstructionAddress() {
		return this.pswInstructionAddress;
	}

	public void setPswInstructionAddress(int pswInstructionAddress) {
		this.pswInstructionAddress = pswInstructionAddress;
	}
	
	public int getPswInstructionLengthCode() {
		return this.pswInstructionLengthCode;
	}
	
	public void pokeMainMem(int addr, byte val) {
		if (addr < 0 || addr >= this.mem.length) {
			new IllegalArgumentException("Invalid main memory address for byte write access");
		}
		this.mem[addr] = val;
	}
	
	public void pokeMainMem(int addr, short val) {
		if (addr < 0 || addr >= (this.mem.length - 1)) {
			new IllegalArgumentException("Invalid main memory address for halfword write access");
		}
		this.mem[addr] = (byte)(val >> 8);
		this.mem[addr+1] = (byte)(val & 0xFF);
	}
	
	public void pokeMainMem(int addr, int val) {
		if (addr < 0 || addr >= (this.mem.length - 3)) {
			new IllegalArgumentException("Invalid main memory address for fullword write access");
		}
		this.mem[addr] = (byte)(val >> 24);
		this.mem[addr+1] = (byte)((val >> 16) & 0xFF);
		this.mem[addr+2] = (byte)((val >> 8) & 0xFF);
		this.mem[addr+3] = (byte)(val & 0xFF);
	}
	
	public void pokeMainMem(int addr, long val) {
		if (addr < 0 || addr >= (this.mem.length - 7)) {
			new IllegalArgumentException("Invalid main memory address for doubleword write access");
		}
		this.pokeMainMem(addr, (int)((val >> 32) & 0xFFFFFFFF));
		this.pokeMainMem(addr+4, (int)(val & 0xFFFFFFFF));
	}
	
	public void pokeMainMem(int addr, byte[] src, int srcFrom, int srcLen) {
		// check bounds of source and target ranges
		if (srcFrom < 0) { srcFrom = 0; }
		if ((srcFrom + srcLen) > src.length) { srcLen = src.length - srcFrom; }
		if (srcLen < 1) { return; }
		if (srcLen > MEM_SIZE) { srcLen = MEM_SIZE; }
		addr &= MemMask;
		
		// copy bytes, possibly wrapping at memory limit
		if ((addr + srcLen) > MEM_SIZE) {
			int copyLen = MEM_SIZE - addr;
			System.arraycopy(src, srcFrom, this.mem, addr, copyLen);
			addr = 0;
			srcFrom += copyLen;
			srcLen -= copyLen;
		}
		System.arraycopy(src, srcFrom, this.mem, addr, srcLen);
	}
	
	public void clearMainMem(int addr, int len) {
		if (addr < 0) { addr = 0; }
		if ((addr + len) > MEM_SIZE) { len = MEM_SIZE - addr; }
		if (len < 1) { return; }
		Arrays.fill(this.mem, addr, addr + len, (byte)0x00);
	}
	
	public byte peekMainMemByte(int addr) {
		if (addr < 0 || addr >= this.mem.length) {
			new IllegalArgumentException("Invalid main memory address for byte read access");
		}
		return this.mem[addr];
	}
	
	public short peekMainMemShort(int addr) {
		if (addr < 0 || addr >= (this.mem.length - 1)) {
			new IllegalArgumentException("Invalid main memory address for halfword read access");
		}
		return (short)((((this.mem[addr]&0xFF) << 8) | (this.mem[addr+1]&0xFF)) & 0xFFFF);
	}
	
	public int peekMainMemInt(int addr) {
		if (addr < 0 || addr >= (this.mem.length - 3)) {
			new IllegalArgumentException("Invalid main memory address for word read access");
		}
		return ((this.mem[addr]&0xFF)<<24) | ((this.mem[addr+1]&0xFF)<<16) | ((this.mem[addr+2]&0xFF)<<8) | (this.mem[addr+3]&0xFF);
	}
	
	public void peekMainMem(int addr, byte[] trg, int trgFrom, int trgLen) {
		// check bounds of source and target ranges
		if (trgFrom < 0) { trgFrom = 0; }
		if ((trgFrom + trgLen) > trg.length) { trgLen = trg.length - trgFrom; }
		if (trgLen < 1) { return; }
		if (trgLen > MEM_SIZE) { trgLen = MEM_SIZE; }
		addr &= MemMask;
		
		// copy bytes, possibly wrapping at memory limit
		if ((addr + trgLen) > MEM_SIZE) {
			int copyLen = MEM_SIZE - addr;
			System.arraycopy(this.mem, addr, trg, trgFrom, copyLen);
			addr = 0;
			trgFrom += copyLen;
			trgLen -= copyLen;
		}
		System.arraycopy(this.mem, addr, trg, trgFrom, trgLen);
	}
	
	public void pokeProtectionKey(int pageNo, byte key) {
		pageNo = pageNo % this.pageKeys.length;
		this.pageKeys[pageNo] = key;
	}
	
	public byte peekProtectionKey(int pageNo) {
		pageNo = pageNo % this.pageKeys.length;
		return this.pageKeys[pageNo];
	}
	
	/*
	** Utility function 
	*/
	
	public DeviceHandler allocateDeviceHandler(iDevice dev, int cuu, iProcessorEventTracker eventTracker) {
		return new DeviceHandler(this.mem, dev, cuu, eventTracker);
	}
	
	/*
	** optional logging and instruction usage statistics
	*/
	
	// log instructions into the ring buffer?
	// (if false, then there will also be no instruction statistics available!) 
	protected static final boolean INSNS_LOG = false;
	
	// if logging instructions: write lines to output after putting them into the ring buffer?
	protected static boolean INSNS_LOG_IMMEDIATE = false;
	
	// length of the ring buffer
	private static final int LOG_QUEUE_LEN = 1024; 
	
	// if logging instructions: usage counters for each instruction
	// (well: first instruction byte only => some/most privileged instructions are summarized under 0xB2) 
	private final long[] _opcodeCounts = new long[256];
	
	// the ring buffer for logged instructions
	private final String[] _logLines = new String[LOG_QUEUE_LEN];
	private final Object[][] _logArgs = new Object[LOG_QUEUE_LEN][];
	private int _currLogEntry = 0;
	
	// the mnemonics for the first instruction byte, with the following exceptions:
	// ??    -> unknown S/370 instruction (as of PrincOps of 1975 with some additional problem-state instructions up to 1987)
	// privI -> privileged instructions grouped under prefix-opcode 0xB2 (i.e. the real instruction identified by 2nd byte) 
	protected final static String[] _mnemonics = {
	/*	0       1       2       3       4       5       6       7       8       9       A       B       C       D       E       F       */     
/*0*/	"??",   "??",   "??",   "??",   "SPM",  "BALR", "BCTR", "BCR",  "SSK",  "ISK",  "SVC",  "BSM",  "BASSM","BASM", "MVCL", "CLCL",
/*1*/	"LPR",  "LNR",  "LTR",  "LCR",  "NR",   "CLR",  "OR",   "XR",   "LR",   "CR",   "AR",   "SR",   "MR",   "DR",   "ALR",  "SLR",
/*2*/	"LPDR", "LNDR", "LTDR", "LCDR", "HDR",  "LRDR", "MXR",  "MXDR", "LDR",  "CDR",  "ADR",  "SDR",  "MDR",  "DDR",  "AWR",  "SWR",
/*3*/	"LPER", "LNER", "LTER", "LCER", "HER",  "LRER", "AXR",  "SXR",  "LER",  "CER",  "AER",  "SER",  "MER",  "DER",  "AUR",  "SUR",  
/*4*/	"STH",  "LA",   "STC",  "IC",   "EX",   "BAL",  "BCT",  "BC",   "LH",   "CH",   "AH",   "SH",   "MH",   "BAS",  "CVD",  "CVB",
/*5*/	"ST",   "??",   "??",   "??",   "N",    "CL",   "O",    "X",    "L",    "C",    "A",    "S",    "M",    "D",    "AL",   "SL",
/*6*/	"STD",  "??",   "??",   "??",   "??",   "??",   "??",   "MXD",  "LD",   "CD",   "AD",   "SD",   "MD",   "DD",   "AW",   "SW",
/*7*/	"STE",  "??",   "??",   "??",   "??",   "??",   "??",   "??",   "LE",   "CE",   "AE",   "SE",   "ME",   "DE",   "AU",   "SU",
/*8*/	"SSM",  "??",   "LPSW", "DIAG", "WRD",  "RDD",  "BXH",  "BXLE", "SRL",  "SLL",  "SRA",  "SLA",  "SRDL", "SLDL", "SRDA", "SLDA",
/*9*/	"STM",  "TM",   "MVI",  "TS",   "NI",   "CLI",  "OI",   "XI",   "LM",   "??",   "??",   "??",   "SIO",  "TIO",  "HIO",  "TCH",
/*A*/	"??",   "??",   "??",   "??",   "??",   "??",   "??",   "??",   "??",   "??",   "??",   "??",   "STNSM","STOSM","SIGP", "??",
/*B*/	"??",   "LRA",  "privI","??",   "??",   "??",   "STCTL","LCTL", "??",   "??",   "CS",   "CDS",  "??",   "CLM",  "STCM", "ICM",
/*C*/	"??",   "??",   "??",   "??",   "??",   "??",   "??",   "??",   "??",   "??",   "??",   "??",   "??",   "??",   "??",   "??",
/*D*/	"??",   "MVN",  "MVC",  "MVZ",  "NC",   "CLC",  "OC",   "XC",   "??",   "??",   "??",   "??",   "TR",   "TRT",  "ED",   "EDMK",
/*E*/	"??",   "??",   "??",   "??",   "??",   "??",   "??",   "??",   "MVCIN","??",   "??",   "??",   "??",   "??",   "??",   "??",
/*F*/	"SRP",  "MVO",  "PACK", "UNPK", "??",   "??",   "??",   "??",   "ZAP",  "CP",   "AP",   "SP",   "MP",   "DP",   "??",   "??"
	};
	
	// the printf-patterns to log the instruction types
	// require based arguments: mnemonic, opcode, instruction_address
	protected final static String _NN = "0x%3$06X  0x%2$02X %1$-5s"; // base format: location opcode mnemonic
	
	// RR instruction format: r1,r2
	// required additional arguments: r1, op1, r2, op2 
	private final static String _RR = _NN + " [RR] R%4$d,R%6$d   { 0x%5$08X , 0x%7$08X }";
	private final static String _MR = _NN + " [mR] 0x%4$X,R%6$d   { 0x%5$08X , 0x%7$08X }"; // r1 is mask
	private final static String _R1 = _NN + " [R ] R%4$d   { 0x%5$08X }"; // r2 is unused
	private final static String fRR = _NN + " <RR> R%4$d,R%6$d    { fpr1: %8$s , fpr2 : %9$s }"; // RR for floating-point
	
	// RX instruction format: r1,d2(x2,b2)
	// required additional arguments: r1, op1, ddd2, x2, b2, addr2
	private final static String _RX = _NN + " [RX] R%4$d,%6$d(X%7$d,B%8$d)   { r: 0x%5$08X , addr2: 0x%9$08X }";
	private final static String _MX = _NN + " [mX] 0x%4$X,%6$d(X%7$d,B%8$d)   { addr2: 0x%9$08X }"; // r1 is mask
	private final static String fRX = _NN + " <RX> R%4$d,%6$d(X%7$d,B%8$d)   { fpr: %10$s , addr2: 0x%9$08X }"; // RX for floating-point
	
	// RS instruction format: r1,r3,d2(b2)
	// required additional arguments: r1, r3, ddd2, b2, addr2
	private final static String _RS = _NN + " [RS] R%4$d,R%5$d,%6$d(B%7$d)   { addr2: 0x%8$08X }";
	private final static String _rS = _NN + " [rS] R%4$d,%6$d(B%7$d)   { addr2: 0x%8$08X }"; // r3 is unused
	private final static String _mS = _NN + " [mS] R%4$d,0x%5$X,%6$d(B%7$d)   { addr2: 0x%8$08X }"; // r3 is mask
	private final static String __S = _NN + " [ S] %6$d(B%7$d)   { addr2: 0x%8$08X }"; // r1 and r3 are unused
	
	// SI instruction format: d1(b1),i2
	// required additional arguments: ddd1, b1, i2, addr1
	private final static String _SI = _NN + " [SI] %4$d(B%5$d),0x%6$02X   { addr1: 0x%7$08X }";
	private final static String _S_ = _NN + " [S ] 0x%6$02X %4$d(B%5$d)   { addr1: 0x%7$08X }"; // 2nd byte discriminates privileged instruction
	
	// SS instruction format: d1(l1,b1),d2(l2,b2)  or  d1(ll,b1),d2(b2)
	// required additional arguments: ddd1, l1, b1, ddd2, l2, b2, ll, addr1, addr2
	private final static String _SS = _NN + " [SS] %4$d(%5$d,B%6$d),%7$d(%8$d,B%9$d)   { addr1: 0x%11$08X , addr1: 0x%12$08X }";
	private final static String _ss = _NN + " [ss] %4$d(%10$d,B%6$d),%7$d(B%9$d)   { addr1: 0x%11$08X , addr1: 0x%12$08X }";
	
	// the printf-pattern to issue the instructions based on first instruction byte
	protected final static String[] _insnformatpatterns = {
		/*        0    1    2    3    4    5    6    7    8    9    A    B    C    D    E    F    */
		/* 0 */  _NN, _NN, _NN, _NN, _R1, _RR, _RR, _MR, _RR, _RR, _RR, _RR, _RR, _RR, _RR, _RR,
		/* 1 */  _RR, _RR, _RR, _RR, _RR, _RR, _RR, _RR, _RR, _RR, _RR, _RR, _RR, _RR, _RR, _RR,
		/* 2 */  fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR,
		/* 3 */  fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR, fRR,
		/* 4 */  _RX, _RX, _RX, _RX, _RX, _RX, _RX, _MX, _RX, _RX, _RX, _RX, _RX, _RX, _RX, _RX,
		/* 5 */  _RX, _NN, _NN, _NN, _RX, _RX, _RX, _RX, _RX, _RX, _RX, _RX, _RX, _RX, _RX, _RX,
		/* 6 */  fRX, _NN, _NN, _NN, _NN, _NN, _NN, fRX, fRX, fRX, fRX, fRX, fRX, fRX, fRX, fRX,
		/* 7 */  fRX, _NN, _NN, _NN, _NN, _NN, _NN, _NN, fRX, fRX, fRX, fRX, fRX, fRX, fRX, fRX,
		/* 8 */  __S, _NN, __S, _NN, _SI, _SI, _RS, _RS, _rS, _rS, _rS, _rS, _rS, _rS, _rS, _rS,
		/* 9 */  _RS, _SI, _SI, _SI, _SI, _SI, _SI, _SI, _RS, _NN, _NN, _NN, _S_, _S_, _S_, _S_,
		/* A */  _NN, _NN, _NN, _NN, _NN, _NN, _NN, _NN, _NN, _NN, _NN, _NN, _SI, _SI, _RS, _NN,
		/* B */  _NN, _RX, _NN, _NN, _NN, _NN, _RS, _RS, _NN, _NN, _RS, _RS, _NN, _mS, _mS, _mS,
		/* C */  _NN, _NN, _NN, _NN, _NN, _NN, _NN, _NN, _NN, _NN, _NN, _NN, _NN, _NN, _NN, _NN,
		/* D */  _NN, _ss, _ss, _ss, _ss, _ss, _ss, _ss, _NN, _NN, _NN, _NN, _ss, _ss, _ss, _ss,
		/* E */  _NN, _NN, _NN, _NN, _NN, _NN, _NN, _NN, _ss, _NN, _NN, _NN, _NN, _NN, _NN, _NN,
		/* F */  _SS, _SS, _SS, _SS, _NN, _NN, _NN, _NN, _SS, _SS, _SS, _SS, _SS, _SS, _NN, _NN
	};
	
	public String getMnemonic(byte instructionCode) {
		int index = (instructionCode & 0xFF);
		return _mnemonics[index];
	}
	
	// instruction opcode (byte) MUST be 2nd argument (i.e. at index 1)!
	protected void logInstruction(String line, Object... args) {
		int opcode = ((Byte)args[1]).byteValue();
		if (opcode < 0) { opcode = 256 + opcode; }
		if (opcode >= 0 && opcode < 256) {
			this._opcodeCounts[opcode]++;
		}
		this.logLine(false, true, line, args);
	}
	
	protected void logInfo(String line, Object... args) {
		this.logLine(false, false, line, args);
	}
	
	protected void logWarning(String line, Object... args) {
		this.logLine(false, false, line, args);
	}
	
	public void logLine(String line, Object... args) {
		this.logLine(true, false, line, args);
	}
	
	private int lineCount = 1;
	
	public void logLine(boolean forceWrite, boolean addLineCOunt, String line, Object... args) {
		this._logLines[this._currLogEntry] = line;
		this._logArgs[this._currLogEntry++] = args;
		if (this._currLogEntry >= LOG_QUEUE_LEN) { this._currLogEntry = 0; }
		if (INSNS_LOG_IMMEDIATE || forceWrite) {
			if (addLineCOunt) {
				System.out.printf("   %6d >> ", this.lineCount++);
			} else {
				System.out.print("          >> ");
			}
			System.out.printf(line, args);
			System.out.println();
		}
	}
	
	public void dumpLastInstructions(int count) {
		if (!INSNS_LOG) { return; }
		if (count < 1) { return; }
		if (count > LOG_QUEUE_LEN) { count = LOG_QUEUE_LEN; }
		int curr = _currLogEntry - count;
		if (curr < 0) { curr += LOG_QUEUE_LEN; }
		while(count > 0) {
			String line = this._logLines[curr];
			Object[] args = this._logArgs[curr];
			System.out.printf(line, args);
			System.out.println();
			count--;
			curr++;
			if (curr >= LOG_QUEUE_LEN) {
				curr = 0;
			}
		}
	}
	
	public static boolean getLiveLogging() { return INSNS_LOG_IMMEDIATE & INSNS_LOG; }
	
	public static void setLiveLogging(boolean doLog) { INSNS_LOG_IMMEDIATE = doLog; }
	
	public boolean hasInstructionStatistics() { return INSNS_LOG; }
	
	private static final String[] _insnModes = { "??", "RR", "RX", "RS", "SI", "SS" };
	
	private static class InsnStat implements Comparable<InsnStat> {
		public final int opcode;
		public final String mnemonic;
		public final int formatType;
		public final String format;
		public final long count;
		
		public InsnStat(int code, long count) {
			this.opcode = code;
			this.count = count;
			
			this.mnemonic = (this.opcode > 255) ? "??" : _mnemonics[this.opcode];
			byte nibble1 = (byte)(opcode & 0xF0);
			if (nibble1 == (byte)0x00 || nibble1 == (byte)0x10) {
				this.formatType = 1; // RR
			} else if (nibble1 == (byte)0x40 || nibble1 == (byte)0x50 || opcode == (byte)0xB1) {
				this.formatType = 2; // RX
			} else if (nibble1 == (byte)0x80 || nibble1 == (byte)0xB0 || opcode == (byte)0x98 || opcode == (byte)0x90) {
				this.formatType = 3; // RS
			} else if (nibble1 == (byte)0x90) {
				this.formatType = 4; // SI
			} else if (nibble1 == (byte)0xD0 || nibble1 == (byte)0xE0 || nibble1 == (byte)0xF0) {
				this.formatType = 5; // SS
			} else {
				this.formatType = 0; // unknown...?
			}
			this.format = _insnModes[this.formatType];
		}
		
		// sort descending by usage frequency
		public int compareTo(InsnStat other) {
	        if (this.count == other.count) { 
	        	return (this.opcode == other.opcode) ? 0 : (this.opcode < other.opcode) ? -1 : 1;
	        }
	        return (this.count < other.count) ? 1 : -1;
	    }
	}
	
	public void dumpInstructionStatistics(boolean sortByFrequency, boolean alsoInstructionFormatStatistics) {
		if (!INSNS_LOG) {
			System.out.println("No instruction statistics available (instruction logging disabled)");
			return;
		}
		
		ArrayList<InsnStat> stats = new ArrayList<InsnStat>();
		long[] formatCounts = new long[6];
		for (int i = 0; i < _opcodeCounts.length; i++) {
			if (_opcodeCounts[i] > 0) {
				InsnStat s = new InsnStat(i, _opcodeCounts[i]);
				stats.add(s);
				formatCounts[s.formatType] += s.count;
			}
		}
		
		if (alsoInstructionFormatStatistics) {
			System.out.println("Instruction format statistics");
			for (int i = 1; i < formatCounts.length; i++) {
				System.out.printf("  %s : %d\n", _insnModes[i], formatCounts[i]);
			}
		}
		
		if (sortByFrequency) { Collections.sort(stats); }
		System.out.printf("Instruction statistics (sorted by %s)\n", (sortByFrequency) ? "frequency" : "opcode");
		for (InsnStat stat : stats) {
			System.out.printf("0x%02X %-6s %s %d\n", stat.opcode, stat.mnemonic, stat.format, stat.count);
		}
	}
	
	/*
	 * Debugging support
	 */
	
	private int svc202RetIA = -1;
	private EbcdicHandler svc202tmp = new EbcdicHandler();
	
	protected void dumpSVC202() {
		int svc202fn = this.gpr[1];
		this.svc202tmp.reset().appendEbcdic(this.mem, svc202fn, 8);
		String fn = this.svc202tmp.getString();
		this.logInfo("SVC-202 begin, function = '%s'", fn);
		if (fn.equals("RDBUF   ") || fn.equals("WRBUF   ")) {
			this.svc202RetIA = this.pswInstructionAddress;
			int recNo = ((this.mem[svc202fn + 26] & 0xFF) << 8) | (this.mem[svc202fn + 27] & 0xFF);
			int buffer = ((this.mem[svc202fn + 29] & 0xFF) << 16) | ((this.mem[svc202fn + 30] & 0xFF) << 8) | (this.mem[svc202fn + 31] & 0xFF);
			int bufferSize = ((this.mem[svc202fn + 32] & 0xFF) << 24) | ((this.mem[svc202fn + 33] & 0xFF) << 16) | ((this.mem[svc202fn + 34] & 0xFF) << 8) | (this.mem[svc202fn + 35] & 0xFF);
			String recfm = this.svc202tmp.reset().appendEbcdic(this.mem, svc202fn + 36, 1).getString();
			int recCount = ((this.mem[svc202fn + 38] & 0xFF) << 8) | (this.mem[svc202fn + 39] & 0xFF);
			this.svc202tmp.reset()
				.appendEbcdic(this.mem, svc202fn + 8, 8)
				.appendUnicode(".")
				.appendEbcdic(this.mem, svc202fn + 16, 8)
				.appendUnicode(".")
				.appendEbcdic(this.mem, svc202fn + 24, 2);
			this.logInfo("  %s recNo %04d, count %04d of '%s' (buffer: 0x%06X, length: %d, recfm: %s)", fn.trim(), recNo, recCount, this.svc202tmp.getString(), buffer, bufferSize, recfm);
		} else {
			int matches = (fn.equals("FINIS   ")) ? 1 : 0;
			for (int i = 0; i < 6; i++) {
				this.svc202tmp.reset().appendEbcdic(this.mem, svc202fn + 8 + (8 * i), 8);
				if (matches > 0 && this.svc202tmp.getString().equals("TESTCMD ")) { matches++; }
				if (matches > 0 && this.svc202tmp.getString().equals("C       ")) { matches++; }
				byte[] raw = this.svc202tmp.getRawBytes();
				if (raw[0] == (byte)0xFF) { break; }
				this.logInfo("   0x %02X %02X %02X %02X %02X %02X %02X %02X = '%s'",
						raw[0], raw[1], raw[2], raw[3], raw[4], raw[5], raw[6], raw[7], this.svc202tmp.getString());
			}/*
			if (matches == 3) {
				INSNS_LOG_IMMEDIATE = true;
			}*/
		}
	}
	
	protected void checkSVC202end() {
		if (this.svc202RetIA == this.pswInstructionAddress) {
			this.logInfo("SVC-202 end");
			this.svc202RetIA = -1;
		}
	}
	
	/*
	** CPU instruction interpreter 
	*/
	
	protected long insnTotal = 0; // absolute number of instructions executed by this CPU
	
	public long getTotalInstructions() { return this.insnTotal; }
	
	public void resetTotalInstructions() { this.insnTotal = 0; }
	
	private int privInsnLocation = -1;
	private int privOpImmediate = -1;
	private int privOpRa = -1;
	private int privOpRb = -1;
	private int privOpAddr = -1;

	protected void setPrivOps_R1_R2(int at, int r1, int r2) {
		this.privInsnLocation = at;
		this.privOpImmediate = -1;
		this.privOpRa = r1;
		this.privOpRb = r2;
		this.privOpAddr = -1;
	}
	
	protected void setPrivOps_InsByte2_Addr(int at, int insByte2, int addr) {
		this.privInsnLocation = at;
		this.privOpImmediate = insByte2 & 0xFF;
		this.privOpRa = (insByte2 >> 4) & 0x0F;
		this.privOpRb = insByte2 & 0x0F;
		this.privOpAddr = addr;
	}

	public int getPrivInsnLocation() { return this.privInsnLocation; }

	public int getPrivOpImmediate() { return this.privOpImmediate; }

	public int getPrivOpRa() { return this.privOpRa; }

	public int getPrivOpRb() { return this.privOpRb; }

	public int getPrivOpAddr() { return this.privOpAddr; }
	
	// the implementation of decimal computations
	protected final DecimalImpl decimalImpl = new DecimalImpl();
	
	// the implementation of floating point computations (short + long, not extended!)
	protected final FloatImpl floatImpl = new FloatImpl((INSNS_LOG) ? this : null);
	
	// bitmasks to remove upper bits when right shifting signed integers 
	protected int[] ShiftRightMasks = {
			0xFFFFFFFF, 0x7FFFFFFF, 0x3FFFFFFF, 0x1FFFFFFF, 0x0FFFFFFF, 0x07FFFFFF, 0x03FFFFFF, 0x01FFFFFF,
			0x00FFFFFF, 0x007FFFFF, 0x003FFFFF, 0x001FFFFF, 0x000FFFFF, 0x0007FFFF, 0x0003FFFF, 0x0001FFFF,
			0x0000FFFF, 0x00007FFF, 0x00003FFF, 0x00001FFF, 0x00000FFF, 0x000007FF, 0x000003FF, 0x000001FF,
			0x000000FF, 0x0000007F, 0x0000003F, 0x0000001F, 0x0000000F, 0x00000007, 0x00000003, 0x00000001
	};
	
	protected iSvcInterceptor svcLogInterceptor = null;
	
	public void setSvcLogInterceptor(iSvcInterceptor interceptor) {
		this.svcLogInterceptor = interceptor;
	}
	
	/**
	 * Execute exactly 1 (one) S/370 instruction in BC mode. This method must invoke itself
	 * recursively to execute the target instruction of an EXecute instruction.
	 * 
	 * @param exAt
	 *   When zero (0): execute the instruction at the current instruction location indicated
	 *     by the IA in PSW.
	 *   When non-zero: this method is called recursively by the EX-instruction and the lower 24 bits
	 *     indicate the location of the address to be executed and the upper 8 bits indicate the second
	 *     instruction byte to use instead of the byte at exAt+1. The address should have the lowest bit
	 *     set to 1 to ensure that the EX recursion is recognized if both the byte and address are 0, as
	 *     the lowest bit can always be reset (as EX requires that the instruction address is even).
	 *     
	 * @return
	 *   zero (0) if the instruction was completely executed in this call, possibly initiating the
	 *     interrupt for an exception indicated by the instruction itself (specification, ...) or by the
	 *     outcome of the execution (overflow, ...)
	 *   non-zero if a privileged or I/O instruction was recognized, which must be executed by the privileged
	 *     level invoking this method. The return values for privileged instructions represent the instruction
	 *     encountered (see getPriv*() methods for the corresponding instruction parameters), depending on the
	 *     instruction code:
	 *       DIAG   -> 0x83xxyyzz :: return explicitly the complete DIAG instruction
	 *       0x9x.. -> 0x00009x.. :: return the 2-byte I/O-opcode, device address via getPrivOpAddr() method
	 *       0xB2.. -> 0x0000B2.. :: return the 2-byte opcode, instruction operands via getPrivOpXX() methods
	 *       others -> 0x000000.. :: return the 1-byte opcode, instruction operands via getPrivOpXX() methods
	 *     The special return value -1 indicates an unknown instruction: in this case, the PSW may or
	 *     may not be updated (instruction address, instruction length), so the execution of the VM must
	 *     be stopped (or a re-IPL initiated).
	 *     
	 * @throws PSWException
	 *    a PSW with a non-processing state was loaded 
	 */
	public abstract int execInstruction(int exAt) throws PSWException;
	
	private ArrayList<iInterruptSource> enqueuedInterrupts = new ArrayList<iInterruptSource>();
	
	private boolean checkInterrupts = false;
	
	private static int insnsBeforeInterrupt = 2;
	
	private int insnCountToNextInterrupt = insnsBeforeInterrupt;
	
	public static void setInstructionCountBeforeInterrupt(int count) {
		insnsBeforeInterrupt = count;
	}
	
	public void enqueueInterrupt(iInterruptSource intr) {
		if (!this.enqueuedInterrupts.contains(intr)) { this.enqueuedInterrupts.add(intr); }
		this.checkInterrupts = true;
	}
	
	public void dequeueInterrupt(iInterruptSource intr, boolean forCompletion, boolean forAsyncEvent) {
		if (this.enqueuedInterrupts.contains(intr)) {
			if (!forCompletion && intr.hasPendingCompletionInterrupt()) { return; }
			if (!forAsyncEvent && intr.hasPendingAsyncInterrupt()) { return; }
			this.enqueuedInterrupts.remove(intr);
		}
		this.checkInterrupts = (this.enqueuedInterrupts.size() > 0);
	}
	
	public boolean isInterruptEnqueued(iInterruptSource intr) {
		return this.enqueuedInterrupts.contains(intr);
	}
	
	public boolean hasEnabledInterrupt() {
		for (iInterruptSource intr : this.enqueuedInterrupts) {		
			// check if we may initiate this interrupt
			if ((intr.getIntrMask() & this.pswIntrMaskByte) != 0) {
				return true; // yes: this interrupt source is not masked out
			}
		}
		return false;
	}
	
	/** Execution of instructions stopped due to an instruction unknown (undefined) to this CPU. */
	public final static int EXECSTATE_UNKNOWN_INSTRUCTION = -1;
	
	/** Execution of instructions stopped due to a enable wait state, resumable by an interrupt */
	public final static int EXECSTATE_ENABLED_WAIT = -2;
	
	/**
	 * Execute a sequence of instructions, initiating interrupts if necessary, either
	 * until the given maximum number of instructions is reached or a privileged
	 * instruction is reached.
	 * 
	 * @param maxInsnCount the number of instructions after which to stop processing
	 * @return (see method execInstruction())
	 * @throws PSWException a PSW with a non-processing state was loaded
	 */
	public int execute(int maxInsnCount) throws PSWException {
		// run at most 'maxInsnCount' instructions
		while(maxInsnCount-- > 0) {
			
			// check for an interrupt to initiate
			if (this.checkInterrupts && this.pswAnyIntrEnabled) {
				// is the "dead time" over? (from enqueuing to initiate resp. interrupt to interrupt) 
				if (this.insnCountToNextInterrupt-- < 1) {
					for (iInterruptSource intr : this.enqueuedInterrupts) {
						
						// check if we may initiate this interrupt
						if ((intr.getIntrMask() & this.pswIntrMaskByte) == 0) {
							// this interrupt source is masked out
							continue;
						}
						
						// initiate an interrupt for this source
						// priority: asynchronous before completion
						// reason: conslle Attention interrupts should bring the VM into VMREAD immediately...
						if (intr.hasPendingAsyncInterrupt()) {
							intr.initiateAsyncInterrupt(this);
						} else if (intr.hasPendingCompletionInterrupt()) {
							intr.initiateCompletionInterrupt(this);
						}
						
						// are all interrupts for source consumed?
						if (!intr.hasPendingCompletionInterrupt() && !intr.hasPendingAsyncInterrupt()) {
							this.enqueuedInterrupts.remove(intr);
						}
						
						// done
						this.insnCountToNextInterrupt = insnsBeforeInterrupt;
						break;
					}
				}
				
				// must we check for interrupts next time?
				this.checkInterrupts = (this.enqueuedInterrupts.size() > 0);
			}
			// if an interrupt was fired, we (highly) probably left a possible
			// prior wait state (by the new PSW for this interrupt class)...
			
			// check for a wait state entered by loading a new PSW
			// (a disable wait raises an exception when loading it, so this can only by an enabled wait)
			if (this.pswWaitState) { return EXECSTATE_ENABLED_WAIT; }
			
			// check for breakpoints and observed changes
			this.checkBreakpoints();
			this.checkWatches();
			
			// process the instruction at the current PSW-IA location
			int outcome= this.execInstruction(0);
			
			// return to invoker if the instruction is privileged or unknown/unsupported 
			if (outcome != 0) { return outcome; }
			
		}
		
		// if we are here: all instructions were known problem state instructions
		return 0; // ok
	}
	
	private ArrayList<Integer> breakpoints = new ArrayList<Integer>();
	
	public void resetBreakpoints() {
		this.breakpoints.clear();
	}
	
	public void addBreakpoint(int breakpointAddress) {
		for (Integer ia : this.breakpoints) {
			if (ia.intValue() == breakpointAddress) {
				return;
			}
		}
		this.breakpoints.add(breakpointAddress);
	}
	
	public void addBreakpoints(int... breakpointAddresses) {
		for (int ba : breakpointAddresses) {
			this.addBreakpoint(ba);
		}
	}
	
	private void checkBreakpoints() throws PSWException {
		for (Integer ia : this.breakpoints) {
			if (ia.intValue() == this.pswInstructionAddress) {
				throw new PSWException(PSWException.PSWProblemType.Breakpoint, -1);
			}
		}
	}
	
	/*
	 * observe memory locations for changes
	 */
	
	private class WatchFor {
		public final int address;
		public final int byteCount;
		
		private final byte[] ref = new byte[8];
		
		public WatchFor(int addr, int len) {
			addr &= MemMask;
			if (len > this.ref.length) { len = this.ref.length; }
			if ((addr + len) >= MEM_SIZE) { len = MEM_SIZE - addr; }
			this.address = addr;
			this.byteCount = len;
			this.reset();
		}
		
		public void check() {
			boolean changed = false;
			for (int i = 0; i < this.byteCount; i++) {
				if (mem[address + i] != ref[i]) {
					changed = true;
					break;
				}
			}
			if (!changed) { return; }
			
			System.out.printf("         ** memory location 0x%06X (%d): from 0x", this.address, this.byteCount);
			for (int i = 0; i < this.byteCount; i++) { System.out.printf("%02X", this.ref[i]); }
			System.out.printf("\n");
			System.out.printf("                                            to 0x");
			for (int i = 0; i < this.byteCount; i++) { System.out.printf("%02X", mem[this.address + i]); }
			System.out.printf("\n");
			System.out.printf(" press enter to continue ...>> ");
			// try { System.in.read(); } catch (IOException e) { }
			
			this.reset();
		}
		
		public void reset() {
			for (int i = 0; i < this.byteCount; i++) {
				this.ref[i] = mem[this.address + i];
			}
		}
	}
	
	private ArrayList<WatchFor> watches = new ArrayList<WatchFor>();
	
	private void resetWatches() {
		for(WatchFor w : this.watches) { w.reset(); }
	}
	
	private void checkWatches() {
		for(WatchFor w : this.watches) { w.check(); }
	}
	
	public void setWatchByte(int at) { this.watches.add(new WatchFor(at, 1)); }
	
	public void setWatchHWord(int at) { this.watches.add(new WatchFor(at, 2)); }
	
	public void setWatchWord(int at) { this.watches.add(new WatchFor(at, 4)); }
	
	public void setWatchDWord(int at) { this.watches.add(new WatchFor(at, 8)); }
	
	public void clearWatch(int at) {
		for (WatchFor w : this.watches) {
			if (w.address == at) {
				this.watches.remove(w);
				return;
			}
		}
	}
}
