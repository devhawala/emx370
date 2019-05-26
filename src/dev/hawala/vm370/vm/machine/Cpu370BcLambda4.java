/*
** This file is part of the emx370 emulator.
**
** This software is provided "as is" in the hope that it will be useful,
** with no promise, commitment or even warranty (explicit or implicit)
** to be suited or usable for any particular purpose.
** Using this software is at your own risk!
**
** Written by Dr. Hans-Walter Latz, Berlin (Germany), 2016
** Released to the public domain.
*/

package dev.hawala.vm370.vm.machine;

import java.util.Arrays;

import dev.hawala.vm370.vm.machine.FloatImpl.FloatRepresentation;

/**
 * Fourth Java8-compatible implementation of S/370 instructions using
 * function pointers.
 * <br/>
 * This class is simply a variation of {@link Cpu370BcLambda2} having
 * the same function interface, but with the instruction implemented
 * as methods of this class instead of lambda expressions stored in
 * member variables.
 * <br/>
 * (the purpose of the variation is simply verifying the impact of the
 * change on the execution speed of the variation compared to the original
 * function pointer implementation in class {@link Cpu370BcLambda}) 
 * 
 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2016
 */
public class Cpu370BcLambda4 extends Cpu370Bc {

	/**
	 * Definition of the function pointer type for
	 * instruction implementations in this class.
	 */
	@FunctionalInterface
	public interface Instr370 {
		
		/**
		 * Implementation entry point for an S/370 instruction.
		 * 
		 * @param at
		 * 		instruction pointer for the instruction being executed.
		 * @param ib2
		 * 		first byte following the instruction byte (dispatching to
		 * 		this instruction)
		 * @param updPSW
		 * 		flag indicating if the PSW may/must be updated, this flag
		 * 		will be [@code false} if the instruction is invoked by
		 * 		the EX-instruction.
		 * @return
		 * 		execution state, see method {@link Cpu370Bc.execInstruction(int)}.
		 * @throws PSWException
		 * 		a PSW with a non-processing state was loaded
		 */
		public int execute(int at, byte ib2, boolean updPSW) throws PSWException;
		
	}
	
	/*
	 * general unknown instruction: let the VM stop.
	 */
	private Instr370 invInstr = (at, ib2, updPSW) -> EXECSTATE_UNKNOWN_INSTRUCTION;
	
	
	/*
	 * RR instructions: R1,R2 (with R1 may be the mask)
	 */
	
	// template RR Instruction
	@SuppressWarnings("unused")
	private int tmplRR(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op1 = this.gpr[r1];
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}
		
		// instruction code
		return EXECSTATE_UNKNOWN_INSTRUCTION;
	};
	
	// SPM - Set Program Mask
	private int x04_SPM(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int op1 = this.gpr[r1];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// SPM - Set Program Mask
		this.pswConditionCode = (byte)((op1 >> 28) & 0x03);
		this.pswProgramMaskFixedOverflow = (op1 & 0x08000000) != 0;
		this.pswProgramMaskDecimalOverflow = (op1 & 0x04000000) != 0;
		this.pswProgramMaskExponentUnderflow = (op1 & 0x02000000) != 0;
		this.pswProgramMaskSignificance = (op1 & 0x01000000) != 0;
		return 0; // ok
	};
	
	// BALR - Branch and link register
	private int x05_BALR(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// BALR - Branch and link register
		this.gpr[r1] = ((this.pswInstructionLengthCode & 0x03) << 30)
					 | ((this.pswConditionCode & 0x03) << 28)
					 | ((this.pswProgramMaskFixedOverflow) ? 0x08000000 : 0)
					 | ((this.pswProgramMaskDecimalOverflow) ? 0x04000000 : 0)
					 | ((this.pswProgramMaskExponentUnderflow) ? 0x02000000 : 0)
					 | ((this.pswProgramMaskSignificance) ? 0x01000000 : 0)
					 | (this.pswInstructionAddress & MemMask);
		if (r2 != 0) {
			this.pswInstructionAddress = op2 & MemMask;
		}
		return 0; // ok
	};
	
	// BCTR - Branch on count register
	private int x06_BCTR(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op1 = this.gpr[r1];
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// BCTR - Branch on count register
		this.gpr[r1] = --op1;
		if (op1 != 0 && r2 != 0) {
			this.pswInstructionAddress = op2 & MemMask;
		}
		return 0; // ok
	};
	
	// BCR - Branch on condition register
	private int x07_BCR(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// BCR - Branch on condition register
		// r1 is the mask for condition codes
		if (r1 == 0 || r2 == 0) {
			// "branch never" (NOOP)
			return 0; // ok
		} else if (r1 == 0x0F
				   || (this.pswConditionCode == CC0 && (r1 & 0x08) != 0)
				   || (this.pswConditionCode == CC1 && (r1 & 0x04) != 0)
				   || (this.pswConditionCode == CC2 && (r1 & 0x02) != 0)
				   || (this.pswConditionCode == CC3 && (r1 & 0x01) != 0)) {
			this.pswInstructionAddress = op2 & MemMask;
		}
		return 0; // ok
	};
	
	// SSK - Set Storage Key
	private int x08_SSK(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// SSK - Set Storage Key
		// (handled as non-privileged instruction, as protection/access-tracking is unsupported)
		// (specification exceptions due to invalid bits in registers are not generated)
		int pageNo = (this.gpr[r2] & 0x00FFFFFF) >> 11;
		byte key = (byte)(this.gpr[r1] & 0x000000FE);
		this.pageKeys[pageNo] = key;
		return 0;
	};
	
	// ISK - Insert Storage Key
	private int x09_ISK(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op1 = this.gpr[r1];
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// ISK - Insert Storage Key
		// (handled as non-privileged instruction, as protection/access-tracking is unsupported)
		// (specification exceptions due to invalid bits in registers are not generated)
		int pageNo = (op2 & 0x00FFFFFF) >> 11;
		// new value for R1 in BC mode (see PrincOps-75, page 105)
		int newVal = (op1 & 0xFFFFFF00) | (this.pageKeys[pageNo] & 0xF0);
		this.gpr[r1] = newVal;
		return 0;
	};
	
	// SVC - Supervisor call
	private int x0A_SVC(int at, byte ib2, boolean updPSW) throws PSWException {		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// SVC - Supervisor call
		if (this.svcLogInterceptor != null) {
			this.svcLogInterceptor.interceptSvc(ib2 & 0xFF, this);
		}
//		if (INSNS_LOG) {
//			if (instrByte2 == (byte)0xCA) { this.dumpSVC202(); }
//		}
		this.pswInterruptionCode = (short)(ib2 & 0x00FF);
		writePswTo(this.mem, 32);
		readPswFrom(this.mem, 96);
		return 0; // ok
	};
	
	// BSM - Branch and set mode
	// not a PrincOps-1975 instruction, but possibly used by
	// 31-bit capable software
	// => tell the program that this is "old" hardware
	private int x0B_BSM(int at, byte ib2, boolean updPSW) throws PSWException {		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}
		
		// BSM - Branch and set mode
		this.doProgramInterrupt(INTR_PGM_OPERATION_EXCEPTION);
		return 0;
	};
	
	// BASSM - Branch, save, set mode
	// not a PrincOps-1975 instruction, but possibly used by
	// 31-bit capable software
	// => tell the program that this is "old" hardware 
	private int x0C_BASSM(int at, byte ib2, boolean updPSW) throws PSWException {		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// BASSM - Branch, save, set mode
		this.doProgramInterrupt(INTR_PGM_OPERATION_EXCEPTION);
		return 0;
	};
	
	// BASM - Branch and save register
	private int x0D_BASM(int at, byte ib2, boolean updPSW) throws PSWException {
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// BASM - Branch and save register
		// not a PrincOps-1975 instruction, but possibly used by
		// 31-bit capable software
		// => tell the program that this is "old" hardware 
		this.doProgramInterrupt(INTR_PGM_OPERATION_EXCEPTION);
		return 0;
	};
	
	// MVCL - Mode characters long
	private int x0E_MVCL(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// MVCL - Mode characters long
		// specification exception if one of r1 or r2 is not even 
		if ((r1 & 0x01) != 0 || (r2 & 0x01) != 0) {
			this.doProgramInterrupt(INTR_PGM_SPECIFICATION_EXCEPTION);
			return 0; // ok
		}
		
		// extract copy/fill specs from registers
		int dstAddr = this.gpr[r1] & 0x00FFFFFF;
		int dstLen = this.gpr[r1+1] & 0x00FFFFFF;
		int srcAddr = this.gpr[r2] & 0x00FFFFFF;
		int srcLen = this.gpr[r2+1] & 0x00FFFFFF;
		byte pad = (byte)((this.gpr[r2+1] >> 24) & 0xFF);
		
		// check some special cases of "nothing to be done"
		if (dstLen == 0) {
			// no movement at all -> set CC, clear the bit 0..7 of addresses and done we are
			this.pswConditionCode = (srcLen == 0) ? CC0 : CC1;
			this.gpr[r1] &= 0x00FFFFFF;
			this.gpr[r2] &= 0x00FFFFFF;
			return 0; // ok
		}
		if (dstAddr == srcAddr && srcLen >= dstLen) {
			// we would copy the source (all or a part) onto itself
			// -> adjust registers to the values as if copying had occurred and done we are
			this.pswConditionCode = (srcLen > dstLen) ? CC1 : CC0;
			this.gpr[r1] = (dstAddr + dstLen) & MemMask;
			this.gpr[r1+1] &= 0xFF000000;
			this.gpr[r2] = (srcAddr + dstLen) & MemMask; // dstLen! (because of: srcLen >= dstLen)
			this.gpr[r2+1] -= dstLen;                    // dstLen! (because of: srcLen >= dstLen) 
			return 0; // ok
		}
		
		// destructive overlap?
		// (destructive means: dstAddr is somewhere in the range srcAddr..srcAddr+srcLen-1
		// including a possible wrap-around at the 16m boundary, so copying the source overwrites
		// some of the source bytes copied *later* in the operation, so dstAddr == srcAddr is not
		// really destructive, as no real move is effectively performed)
		if (dstLen > 1 && srcLen > 1) { // copying more than a single byte?
			int srcLastUsed = (srcAddr + ((srcLen < dstLen) ? srcLen : dstLen) - 1) & MemMask;
			if ((srcAddr < srcLastUsed // no wrap-around for the source
					&& (dstAddr > srcAddr && dstAddr <= srcLastUsed))
				|| (srcAddr > srcLastUsed) // source wraps at 16 limit (srcAddr == srcLastUsed cannot be reached as at most 16m-1 bytes can be copied) 
					&& (dstAddr > srcAddr || dstAddr <= srcLastUsed)) {
				this.pswConditionCode = CC3; // we have a destructive overlap
				this.gpr[r1] &= 0x00FFFFFF;
				this.gpr[r2] &= 0x00FFFFFF;
				return 0;
			}
		}
		
		// set CC based on source/destination lengths (before those get counted down = destroyed) 
		this.pswConditionCode = (dstLen < srcLen) ? CC1 : (dstLen > srcLen) ? CC2 : CC0;

		// copy source bytes to destination
		while(dstLen > 0 && srcLen > 0) {
			int dstCopyEnd = Math.min(dstAddr + dstLen - 1, MemMask);
			int dstCopyLen = (dstCopyEnd - dstAddr) + 1;
			int srcCopyEnd = Math.min(srcAddr + srcLen - 1, MemMask);
			int srcCopyLen = (srcCopyEnd - srcAddr) + 1;
			int copyLen = Math.min(dstCopyLen, srcCopyLen);
			if (srcAddr != dstAddr) {
				System.arraycopy(this.mem, srcAddr, this.mem, dstAddr, copyLen);
			}
			dstLen -= copyLen;
			srcLen -= copyLen;
			dstAddr = (dstAddr + copyLen) & MemMask;
			srcAddr = (srcAddr + copyLen) & MemMask;
		}
		
		// fill up destination with the pad byte if required
		while(dstLen > 0) {
			int dstFillEndPlusOne = Math.min(dstAddr + dstLen, MemMask + 1);
			int dstFillLen = (dstFillEndPlusOne - dstAddr);
			Arrays.fill(this.mem,  dstAddr, dstFillEndPlusOne, pad);
			dstLen -= dstFillLen;
			dstAddr  = (dstAddr + dstFillLen) & MemMask;
		}
		
		// store final values back into registers
		this.gpr[r1] = dstAddr;
		this.gpr[r1+1] &= 0xFF000000; // destination bytes count is zero but bits 0..7 must be preserved
		this.gpr[r2] = srcAddr;
		this.gpr[r2+1] = (this.gpr[r2+1] & 0xFF000000) | (srcLen & 0x00FFFFFF); // implant remaining source count preserving bits 0..7
		
		// done
		return 0; // ok
	};
	
	// CLCL - Compare logical characters long
	private int x0F_CLCL(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// CLCL - Compare logical characters long
		// specification exception if one of r1 or r2 is not even
		if ((r1 & 0x01) != 0 || (r2 & 0x01) != 0) {
			this.doProgramInterrupt(INTR_PGM_SPECIFICATION_EXCEPTION);
			return 0; // ok
		}
		int addr1 = this.gpr[r1] & 0x00FFFFFF;
		int len1 = this.gpr[r1+1] & 0x00FFFFFF;
		int addr2 = this.gpr[r2] & 0x00FFFFFF;
		int len2 = this.gpr[r2+1] & 0x00FFFFFF;
		int pad = (this.gpr[r2+1] >> 24) & 0xFF;
		
		byte newCC = CCINV;
		
		while(len1 > 0 && len2 > 0 && newCC < 0) {
			int b1 = this.mem[addr1] & 0xFF;
			int b2 = this.mem[addr2] & 0xFF;
			
			if (b1 == b2) {
				addr1++;
				addr1 &= MemMask;
				len1--;
				
				addr2++;
				addr2 &= MemMask;
				len2--;
				
				continue;
			}
			newCC = (b1 < b2) ? CC1 : CC2;
		}
		
		if (len1 == 0 && len2 == 0 && newCC < 0) { newCC = CC0; }
		
		if (len1 == 0 && newCC < 0) {
			while(len2 > 0 && newCC < 0) {
				int b2 = this.mem[addr2] & 0xFF;
				
				if (pad == b2) {
					addr2++;
					addr2 &= MemMask;
					len2--;
					continue;
				}
				newCC = (pad < b2) ? CC1 : CC2;
			}
		} else if (len2 == 0 && newCC < 0) {
			while(len1 > 0 && newCC < 0) {
				int b1 = this.mem[addr1] & 0xFF;
				
				if (b1 == pad) {
					addr1++;
					addr1 &= MemMask;
					len1--;
					continue;
				}
				newCC = (b1 < pad) ? CC1 : CC2;
			}
		}
		if (newCC < 0) { newCC = CC0; }
		
		/* nicht PrincOps-konform: oberstes byte in R1/R2 muss 0 werden, Adresse wird dort immer eingetragen
		this.gpr[r1] = ((newCC == 0) ? 0 : this.gpr[r1] & 0xFF000000) | addr1;
		this.gpr[r1+1] = (this.gpr[r1+1] & 0xFF000000) | (len1 & 0x00FFFFFF);
		this.gpr[r2] = ((newCC == 0) ? 0 : this.gpr[r2] & 0xFF000000) | addr2;
		this.gpr[r2+1] = (pad << 24) | (len2 & 0x00FFFFFF);
		*/
		
		this.gpr[r1] = addr1;
		this.gpr[r1+1] = (this.gpr[r1+1] & 0xFF000000) | (len1 & 0x00FFFFFF);
		this.gpr[r2] = addr2;
		this.gpr[r2+1] = (pad << 24) | (len2 & 0x00FFFFFF);
		this.pswConditionCode = newCC;
		return 0; // ok
	};
	
	// LPR - Load positive
	private int x10_LPR(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// LPR - Load positive
		if (op2 == 0x80000000) { // is the "maximum negative number" to be complemented?
			this.gpr[r1] = op2; // "the number remains unchanged", but is it to be assigned??
			this.pswConditionCode = CC3; // this is an overflow...
			if (this.pswProgramMaskFixedOverflow) {
				this.doProgramInterrupt(INTR_PGM_FIXEDPOINT_OVERFLOW);
			}
		} else {
			this.gpr[r1] = (op2 < 0) ? - op2 : op2;
			this.pswConditionCode = (op2 == 0) ? CC0 : CC2;
		}
		return 0; // ok
	};
	
	// LNR - Load negative
	private int x11_LNR(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// LNR - Load negative
		this.gpr[r1] = (op2 > 0) ? - op2 : op2;
		this.pswConditionCode = (op2 == 0) ? CC0 : CC1;
		return 0; // ok
	};
	
	// LTR - Load and test register
	private int x12_LTR(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// LTR - Load and test register
		this.gpr[r1] = op2;
		this.pswConditionCode = (op2 < 0) ? CC1 : (op2 > 0) ? CC2 : CC0;
		return 0; // ok
	};
	
	// LCR - Load complement registers
	private int x13_LCR(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// LCR - Load complement registers
		if (op2 == 0x80000000) { // is the "maximum negative number" to be complemented?
			this.gpr[r1] = op2; // "the number remains unchanged" => but: is it really to be transferred??? 
			this.pswConditionCode = CC3; // we have an overflow (of course)
			if (this.pswProgramMaskFixedOverflow) {
				this.doProgramInterrupt(INTR_PGM_FIXEDPOINT_OVERFLOW);
			}
		} else {
			this.gpr[r1] = - op2;
			this.pswConditionCode = (op2 > 0) ? CC1 : (op2 < 0) ? CC2 : CC0;
		}
		return 0; // ok
	};
	
	// NR - And registers
	private int x14_NR(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op1 = this.gpr[r1];
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// NR - And registers
		int res = op1 & op2;
		this.gpr[r1] = res;
		this.pswConditionCode = (res == 0) ? CC0 : CC1;
		return 0; // ok
	};
	
	// CLR - Compare logical registers
	private int x15_CLR(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op1 = this.gpr[r1];
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// CLR - Compare logical registers
		// a bit tricky, as Java has no unsigned types, so a logical compare works as follows:
		// -> first compare for identity, removing this option from further tests
		// -> then compare for bit-0 (left most bit) deviation:
		//    if this sign bit differs (one is negative, one is positive), then the negative
		//    value (bit-0 is set) is the higher logical value
		// -> after resetting bit-0, the remaining bits 1-31 can be compared for a logical compare  
		//    as a signed compare
		if (op1 == op2) {
			this.pswConditionCode = CC0;  // operands are equal
		} else if (op1 < 0 && op2 >= 0) { // op1 has bit0 set, but op2 not
			this.pswConditionCode = CC2;  // => op1 logically high
		} else if (op1 >= 0 && op2 < 0) { // op2 has bit0 set, but op1 not
			this.pswConditionCode = CC1;  // => op2 logically high
		} else {
			// if we are here: bit0 of both operands is same, but operands differ 
			op1 &= 0x7FFFFFFF;
			op2 &= 0x7FFFFFFF;
			this.pswConditionCode = (op1 < op2) ? CC1 : CC2;
		}
		return 0; // ok
	};
	
	// OR - Or registers
	private int x16_OR(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op1 = this.gpr[r1];
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// OR - Or registers
		int res = op1 | op2;
		this.gpr[r1] = res;
		this.pswConditionCode = (res == 0) ? CC0 : CC1;
		return 0; // ok
	};
	
	// XR -Exclusive or registers
	private int x17_XR(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op1 = this.gpr[r1];
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// XR -Exclusive or registers
		int res = op1 ^ op2;
		this.gpr[r1] = res;
		this.pswConditionCode = (res == 0) ? CC0 : CC1;
		return 0; // ok
	};
	
	// LR - Load register
	private int x18_LR(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// LR - Load register
		this.gpr[r1] = op2;
		return 0; // ok
	};
	
	// CR - Compare registers
	private int x19_CR(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op1 = this.gpr[r1];
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// CR - Compare registers
		this.pswConditionCode = (op1 < op2) ? CC1 : (op1 > op2) ? CC2 : CC0;
		return 0; // ok
	};
	
	// AR - Add registers
	private int x1A_AR(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op1 = this.gpr[r1];
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// AR - Add registers
		int res = op1 + op2;
		this.gpr[r1] = res;
		this.pswConditionCode 
			= (res > 0) ? (op1 < 0 && op2 < 0) ? CC3 : CC2
			: (res < 0) ? (op1 >= 0 && op2 >= 0) ? CC3 : CC1
			: (op1 < 0 && op2 < 0) ? CC3 : CC0;
		if (this.pswConditionCode == CC3 && this.pswProgramMaskFixedOverflow) {
			this.doProgramInterrupt(INTR_PGM_FIXEDPOINT_OVERFLOW);
		}
		return 0; // ok
	};
	
	// SR - Subtract registers
	private int x1B_SR(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op1 = this.gpr[r1];
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// SR - Subtract registers
		int res = op1 - op2;
		this.gpr[r1] = res;
		this.pswConditionCode 
			= (res > 0) ? (op1 < 0 && op2 >= 0) ? CC3 : CC2
			: (res < 0) ? (op1 >= 0 && op2 < 0) ? CC3 : CC1
			: (op1 < 0 && op2 >= 0) ? CC3 : CC0;
		if (this.pswConditionCode == CC3 && this.pswProgramMaskFixedOverflow) {
			this.doProgramInterrupt(INTR_PGM_FIXEDPOINT_OVERFLOW);
		}
		return 0; // ok
	};
	
	// MR - Multiply registers
	private int x1C_MR(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op1 = this.gpr[r1];
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// MR - Multiply registers
		// specification exception if r1 is not even
		if ((r1 & 0x01) != 0) {
			this.doProgramInterrupt(INTR_PGM_SPECIFICATION_EXCEPTION);
			return 0; // ok
		}
		op1 = this.gpr[r1+1]; // "the multiplicand is taken from the odd register of the pair"
		long result = (long)op1 * (long)op2;
		this.gpr[r1] = (int)(result >> 32);
		this.gpr[r1+1] = (int)(result & 0xFFFFFFFFL);
		return 0; // ok
	};
	
	// DR - Divide Registers
	private int x1D_DR(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op1 = this.gpr[r1];
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// DR - Divide Registers
		// specification exception if r1 is not even
		if ((r1 & 0x01) != 0) {
			this.doProgramInterrupt(INTR_PGM_SPECIFICATION_EXCEPTION);
			return 0; // ok
		}
		if (op2 == 0) {
			this.doProgramInterrupt(INTR_PGM_FIXEDPOINT_DIVIDE);
			return 0; // ok
		}
		long dividend = ((long)op1 << 32) | (((long)this.gpr[r1+1]) & 0xFFFFFFFFL);
		long quotient = dividend / op2;
		if (quotient < -2147483648L || quotient > 2147483647L) {
			this.doProgramInterrupt(INTR_PGM_FIXEDPOINT_DIVIDE);
			return 0; // ok
		}
		long remainder = dividend % op2;
		this.gpr[r1] = (int)remainder;
		this.gpr[r1+1] = (int)quotient;
		return 0; // ok
	};
	
	// ALR - Add logical registers
	private int x1E_ALR(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op1 = this.gpr[r1];
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// ALR - Add logical registers
		long op1l = (op1 < 0) ? 4294967296L + op1 : op1;
		long op2l = (op2 < 0) ? 4294967296L + op2 : op2;
		long res = op1l + op2l;
		byte newCC = (res > 4294967295L) ? CC2 : CC0;
		res = (res & 0xFFFFFFFFL);
		if (res != 0) { newCC++; }
		this.gpr[r1] = (int)res;
		this.pswConditionCode = newCC;
		return 0; // ok
	};
	
	// SLR - Subtract logical registers
	private int x1F_SLR(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		int op1 = this.gpr[r1];
		int op2 = this.gpr[r2];
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}

		// SLR - Subtract logical registers
		long op1l = (op1 < 0) ? 4294967296L + op1 : op1;
		long op2l = (op2 < 0) ? 4294967296L + op2 : op2;
		// ????????????????????????????????????????????????????????????????
		// the logical subtraction is considered to be performed by adding
		// the one's complement of the second operand and a low-order one
		// to the first operand
		// ????????????????????????????????????????????????????????????????
		long res = op1l - op2l; // ????????????????????????????????????????
		this.gpr[r1] = (int)(res & 0xFFFFFFFFL);
		this.pswConditionCode = (res == 0) ? CC2 : (res < 0) ? CC3 : CC1;
		return 0; // ok
	};
	
	
	/*
	 * RX or MX instructions: R1,D2(X2,B2) (with R1 may be the mask)
	 */
	
	// template RX Instruction
	@SuppressWarnings("unused")
	private int tmplRX(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}
		
		// instruction code
		return EXECSTATE_UNKNOWN_INSTRUCTION;
	};
	
	// STH - Store halfword
	private int x40_STH(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// STH - Store halfword
		this.mem[addr2++] = (byte)((op1 & 0xFF00) >> 8);
		this.mem[addr2] = (byte)(op1 & 0xFF);
		return 0; // ok
	};
	
	// LA - Load address
	private int x41_LA(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// LA - Load address
		this.gpr[r1] = addr2;
		return 0; // ok
	};
	
	// STC - Store character
	private int x42_STC(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// STC - Store character
		this.mem[addr2] = (byte)(op1 & 0xFF);
		return 0; // ok
	};
	
	// IC - Insert character
	private int x43_IC(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// IC - Insert character
		this.gpr[r1] = (op1 & 0xFFFFFF00) | (this.mem[addr2] & 0xFF);
		return 0; // ok
	};
	
	// EX - Execute
	private int x44_EX(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// EX - Execute
		// specification exception if address of EX instruction is not even
		if ((addr2 & 0x01) != 0) {
			this.doProgramInterrupt(INTR_PGM_SPECIFICATION_EXCEPTION);
			return 0; // ok
		}
		// execute exception if subject instruction is EX
		if (this.mem[addr2] == (byte)0x44) {
			this.doProgramInterrupt(INTR_PGM_EXECUTE_EXCEPTION);
			return 0; // ok
		}
		byte newByte2 = (r1 == 0) ? this.mem[addr2+1] : (byte)(this.mem[addr2+1] | (op1 & 0xFF));
		int exParam = (newByte2 << 24) | addr2 | 0x00000001; // ensure exParam is not 0 even if byte2 and addr3 are...  
		return this.execInstruction(exParam);
	};
	
	// BAL - Branch and link
	private int x45_BAL(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// BAL - Branch and link
		this.gpr[r1] = ((this.pswInstructionLengthCode & 0x03) << 30)
					 | ((this.pswConditionCode & 0x03) << 28)
					 | ((this.pswProgramMaskFixedOverflow) ? 0x08000000 : 0)
					 | ((this.pswProgramMaskDecimalOverflow) ? 0x04000000 : 0)
					 | ((this.pswProgramMaskExponentUnderflow) ? 0x02000000 : 0)
					 | ((this.pswProgramMaskSignificance) ? 0x01000000 : 0)
					 | (this.pswInstructionAddress & MemMask);
		this.pswInstructionAddress = addr2;
		return 0; // ok
	};
	
	// BCT - Branch on count
	private int x46_BCT(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// BCT - Branch on count
		this.gpr[r1] = --op1;
		if (op1 != 0) {
			this.pswInstructionAddress = addr2;
		}
		return 0; // ok
	};
	
	// BC - Branch on condition
	private int x47_BC(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// BC - Branch on condition
		if (r1 == 0) {
			// "branch never" (NOOP)
			return 0; // ok
		} else if (r1 == 0x0F
				   || (this.pswConditionCode == CC0 && (r1 & 0x08) != 0)
				   || (this.pswConditionCode == CC1 && (r1 & 0x04) != 0)
				   || (this.pswConditionCode == CC2 && (r1 & 0x02) != 0)
				   || (this.pswConditionCode == CC3 && (r1 & 0x01) != 0)) {
			this.pswInstructionAddress = addr2;
		}
		return 0; // ok
	};
	
	// LH - Load halfword
	private int x48_LH(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// LH - Load halfword
		short op2 = (short)(((this.mem[addr2]&0xFF)<<8) | (this.mem[addr2+1]&0xFF));
		this.gpr[r1] = op2;
		return 0; // ok
	};
	
	// CH - Compare halfword
	private int x49_CH(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// CH - Compare halfword
		short op2 = (short)(((this.mem[addr2]&0xFF)<<8) | (this.mem[addr2+1]&0xFF));
		this.pswConditionCode = (op1 < op2) ? CC1 : (op1 > op2) ? CC2 : CC0;
		return 0; // ok
	};
	
	// AH - Add halfword
	private int x4A_AH(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// AH - Add halfword
		short op2 = (short)(((this.mem[addr2]&0xFF)<<8) | (this.mem[addr2+1]&0xFF));
		int res = op1 + op2;
		this.gpr[r1] = res;
		this.pswConditionCode 
			= (res > 0) ? (op1 < 0 && op2 < 0) ? CC3 : CC2
			: (res < 0) ? (op1 >= 0 && op2 >= 0) ? CC3 : CC1
			: (op1 < 0 && op2 < 0) ? CC3 : CC0;
		if (this.pswConditionCode == CC3 && this.pswProgramMaskFixedOverflow) {
			this.doProgramInterrupt(INTR_PGM_FIXEDPOINT_OVERFLOW);
		}
		return 0; // ok
	};
	
	// SH - Substract halfword
	private int x4B_SH(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// SH - Substract halfword
		short op2 = (short)(((this.mem[addr2]&0xFF)<<8) | (this.mem[addr2+1]&0xFF));
		int res = op1 - op2;
		this.gpr[r1] = res;
		this.pswConditionCode 
			= (res > 0) ? (op1 < 0 && op2 >= 0) ? CC3 : CC2
			: (res < 0) ? (op1 >= 0 && op2 < 0) ? CC3 : CC1
			: (op1 < 0 && op2 >= 0) ? CC3 : CC0;
		if (this.pswConditionCode == CC3 && this.pswProgramMaskFixedOverflow) {
			this.doProgramInterrupt(INTR_PGM_FIXEDPOINT_OVERFLOW);
		}
		return 0; // ok
	};
	
	// MH - Multiply halfword
	private int x4C_MH(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// MH - Multiply halfword
		short op2 = (short)(((this.mem[addr2]&0xFF)<<8) | (this.mem[addr2+1]&0xFF));
		long result = (long)op1 * (long)op2;
		this.gpr[r1] = (int)(result & 0xFFFFFFFFL); // PrincOps-75, p. 136: "The bits to the left of the 32 low-order bits are not tested for significance; no overflow indication is given."
		return 0; // ok
	};
	
	// BAS - Branch and save
	// not a PrincOps-1975 instruction, but possibly used by
	// 31-bit capable software
	// => tell the program that this is "old" hardware 
	private int x4D_BAS(int at, byte ib2, boolean updPSW) throws PSWException {
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// BAS - Branch and save
		this.doProgramInterrupt(INTR_PGM_OPERATION_EXCEPTION);
		return 0;
	};
	
	// CVD - Convert to decimal
	private int x4E_CVD(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// CVD - Convert to decimal
		this.decimalImpl.instrConvertToPacked(this.gpr[r1], this.mem, addr2);
		return 0; // ok
	};
	
	// CVB - Convert to binary
	private int x4F_CVB(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// CVB - Convert to binary
		int[] result = this.decimalImpl.instrConvertToBinary(this.gpr[r1], this.mem, addr2);
		this.gpr[r1] = result[1];
		if (result[0] > 0) {
			this.doProgramInterrupt((short)(result[0] >> 16));
		}
		return 0; // ok
	};
	
	// ST - Store
	private int x50_ST(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// ST - Store
		this.mem[addr2++] = (byte)((op1 >> 24) & 0xFF);
		this.mem[addr2++] = (byte)((op1 >> 16) & 0xFF);
		this.mem[addr2++] = (byte)((op1 >> 8) & 0xFF);
		this.mem[addr2] = (byte)(op1 & 0xFF);
		return 0; // ok
	};
	
	// N - And
	private int x54_N(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// N - And
		int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
		int res = op1 & op2;
		this.gpr[r1] = res;
		this.pswConditionCode= (res == 0) ? CC0 : CC1;
		return 0;
	};
	
	// CL - Compare logical
	private int x55_CL(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// CL - Compare logical
		// (see CLR)
		int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
		if (op1 == op2) {
			this.pswConditionCode = CC0;  // operands are equal
		} else if (op1 < 0 && op2 >= 0) { // op1 has bit0 set, but op2 not
			this.pswConditionCode = CC2;  // => op1 logically high
		} else if (op1 >= 0 && op2 < 0) { // op2 has bit0 set, but op1 not
			this.pswConditionCode = CC1;  // => op2 logically high
		} else {
			// if we are here: bit0 of both operands is same, but operands differ 
			op1 &= 0x7FFFFFFF;
			op2 &= 0x7FFFFFFF;
			this.pswConditionCode = (op1 < op2) ? CC1 : CC2;
		}
		return 0; // ok
	};
	
	// O - Or
	private int x56_O(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// O - Or
		int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
		int res = op1 | op2;
		this.gpr[r1] = res;
		this.pswConditionCode = (res == 0) ? CC0 : CC1;
		return 0;
	};
	
	// X - Exclusive or
	private int x57_X(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// X - Exclusive or
		int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
		int res = op1 ^ op2;
		this.gpr[r1] = res;
		this.pswConditionCode= (res == 0) ? CC0 : CC1;
		return 0;
	};
	
	// L - Load
	private int x58_L(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// L - Load
		int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
		this.gpr[r1] = op2;
		return 0; // ok
	};
	
	// C - Compare
	private int x59_C(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// C - Compare
		int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
		this.pswConditionCode = (op1 < op2) ? CC1 : (op1 > op2) ? CC2 : CC0;
		return 0; // ok
	};
	
	// A - Add
	private int x5A_A(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// A - Add
		int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
		int res = op1 + op2;
		this.gpr[r1] = res;
		this.pswConditionCode 
			= (res > 0) ? (op1 < 0 && op2 < 0) ? CC3 : CC2
			: (res < 0) ? (op1 >= 0 && op2 >= 0) ? CC3 : CC1
			: (op1 < 0 && op2 < 0) ? CC3 : CC0;
		if (this.pswConditionCode == CC3 && this.pswProgramMaskFixedOverflow) {
			this.doProgramInterrupt(INTR_PGM_FIXEDPOINT_OVERFLOW);
		}
		return 0; // ok
	};
	
	// S - Substract
	private int x5B_S(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// S - Substract
		int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
		int res = op1 - op2;
		this.gpr[r1] = res;
		this.pswConditionCode 
			= (res > 0) ? (op1 < 0 && op2 >= 0) ? CC3 : CC2
			: (res < 0) ? (op1 >= 0 && op2 < 0) ? CC3 : CC1
			: (op1 < 0 && op2 >= 0) ? CC3 : CC0;
		if (this.pswConditionCode == CC3 && this.pswProgramMaskFixedOverflow) {
			this.doProgramInterrupt(INTR_PGM_FIXEDPOINT_OVERFLOW);
		}
		return 0; // ok
	};
	
	// M - Multiply
	private int x5C_M(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// M - Multiply
		// specification exception if r1 is not even
		if ((r1 & 0x01) != 0) {
			this.doProgramInterrupt(INTR_PGM_SPECIFICATION_EXCEPTION);
			return 0; // ok
		}
		op1 = this.gpr[r1+1]; // "the multiplicand is taken from the odd register of the pair"
		int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
		long result = (long)op1 * (long)op2;
		this.gpr[r1] = (int)(result >> 32);
		this.gpr[r1+1] = (int)(result & 0xFFFFFFFF);
		return 0; // ok
	};
	
	// D - Divide
	private int x5D_D(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// D - Divide
		// specification exception if r1 is not even
		if ((r1 & 0x01) != 0) {
			this.doProgramInterrupt(INTR_PGM_SPECIFICATION_EXCEPTION);
			return 0; // ok
		}
		int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
		if (op2 == 0) {
			this.doProgramInterrupt(INTR_PGM_FIXEDPOINT_DIVIDE);
			return 0; // ok
		}
		long dividend = ((long)op1 << 32) | (((long)this.gpr[r1+1]) & 0xFFFFFFFFL);
		long quotient = dividend / op2;
		if (quotient < -2147483648L || quotient > 2147483647L) {
			this.doProgramInterrupt(INTR_PGM_FIXEDPOINT_DIVIDE);
			return 0; // ok
		}
		long remainder = dividend % op2;
		this.gpr[r1] = (int)remainder;
		this.gpr[r1+1] = (int)quotient;
		return 0; // ok
	};
	
	// AL - Add logical
	private int x5E_AL(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// AL - Add logical
		int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
		long op1l = (op1 < 0) ? 4294967296L + op1 : op1;
		long op2l = (op2 < 0) ? 4294967296L + op2 : op2;
		long res = op1l + op2l;
		byte newCC = (res > 4294967295L) ? CC2 : CC0;
		res = (res & 0xFFFFFFFFL);
		if (res != 0) { newCC++; }
		this.gpr[r1] = (int)res;
		this.pswConditionCode = newCC;
		return 0; // ok
	};
	
	// SL - Substract logical
	private int x5F_SL(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		int op1 = this.gpr[r1];
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// SL - Substract logical
		int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
		long op1l = (op1 < 0) ? 4294967296L + op1 : op1;
		long op2l = (op2 < 0) ? 4294967296L + op2 : op2;
		// ????????????????????????????????????????????????????????????????
		// the logical subtraction is considered to be performed by adding
		// the one's complement of the second operand and a low-order one
		// to the first operand
		// ????????????????????????????????????????????????????????????????
		long res = op1l - op2l; // ????????????????????????????????????????
		this.gpr[r1] = (int)(res & 0xFFFFFFFFL);
		this.pswConditionCode = (res == 0) ? CC2 : (res < 0) ? CC3 : CC1;
		return 0; // ok
	};
	
	
	/*
	 * RS instructions: R1,R3,D2(B2)
	 */
	
	// template RS Instruction
	@SuppressWarnings("unused")
	private int tmplRS(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r3 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}
		
		// instruction code
		return EXECSTATE_UNKNOWN_INSTRUCTION;
	};
	
	// SSM   - Set Systen Mask (implemented as unprivileged)
	private int x80_SSM(int at, byte ib2, boolean updPSW) {
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// SSM   - Set Systen Mask (implemented as unprivileged)
		byte b = this.mem[addr2];
		this.setIntrMask(b);
		return 0;
	};
	
	// LPSW - Load PSW (implemented as unprivileged)
	private int x82_LPSW(int at, byte ib2, boolean updPSW) throws PSWException {
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// LPSW - Load PSW (implemented as unprivileged)
		// specification exception if addr2 is not on a doubleword boundary
		if ((addr2 & 0x07) != 0) {
			this.doProgramInterrupt(INTR_PGM_SPECIFICATION_EXCEPTION);
			return 0; // ok
		}
		this.readPswFrom(this.mem, addr2);
//		if (INSNS_LOG) { this.checkSVC202end(); }
		return 0;
	};
	
	// BXH - Branch on index high
	private int x86_BXH(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r3 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// BXH - Branch on index high
		int incrValue = this.gpr[r3];
		int cmpValue = ((r3 & 1) > 0)  // is the register number r3 even or odd? 
					 ? this.gpr[r3]    // -> odd: register r3 also holds the compare value
					 : this.gpr[r3+1]; // -> even: register r3+1 holds the compare value
		int sum = this.gpr[r1] + incrValue;
		this.gpr[r1] = sum;
		if (sum > cmpValue) {
			// sum value is high => do the branch
			this.pswInstructionAddress = addr2;
		}
		return 0; // ok
	};
	
	// BXLE - Branch on index low/equal
	private int x87_BXLE(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r3 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// BXLE - Branch on index low/equal
		int incrValue = this.gpr[r3];
		int cmpValue = ((r3 & 1) > 0)  // is the register number r3 even or odd? 
					 ? this.gpr[r3]    // -> odd: register r3 also holds the compare value
					 : this.gpr[r3+1]; // -> even: register r3+1 holds the compare value
		int sum = this.gpr[r1] + incrValue;
		this.gpr[r1] = sum;
		if (sum <= cmpValue) {
			// sum value is low or equal => do the branch
			this.pswInstructionAddress = addr2;
		}
		return 0; // ok
	};
	
	// SRL - Shift right single logical
	private int x88_SRL(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// SRL - Shift right single logical
		int shiftBy = addr2 & 0x3F; // lower 6 bits
		this.gpr[r1] = (shiftBy > 31) ? 0 : ((this.gpr[r1] >> shiftBy) & ShiftRightMasks[shiftBy]);
		return 0; // ok
	};
	
	// SLL - Shift left single logical
	private int x89_SLL(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// SLL - Shift left single logical
		int shiftBy = addr2 & 0x3F; // lower 6 bits
		this.gpr[r1] = (shiftBy > 31) ? 0 : this.gpr[r1] << shiftBy;
		return 0; // ok
	};
	
	// SRA - Shift right single
	private int x8A_SRA(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// SRA - Shift right single
		int shiftBy = addr2 & 0x3F; // lower 6 bits
		int val = this.gpr[r1] >> shiftBy;
		this.gpr[r1] = val;
		this.pswConditionCode = (val < 0) ? CC1 : (val > 0) ? CC2 : CC0;
		return 0; // ok
	};
	
	// SLA - Shift left single
	private int x8B_SLA(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// SLA - Shift left single
		int shiftBy = addr2 & 0x3F; // lower 6 bits
		int val = this.gpr[r1];
		int signBit = val & 0x80000000;
		boolean hadOverflow = false;
		val &= 0x7FFFFFFF;
		for (int i = 0; i < shiftBy; i++) {
			val <<= 1;
			if ((val & 0x80000000) != signBit) { hadOverflow = true; }
		}
		
		// set the value as sign + 31bits
		val = (val & 0x7FFFFFFF) | signBit;
		this.gpr[r1] = val;
		
		// set condition code checking for overflow
		if (hadOverflow) {
			this.pswConditionCode = CC3;
			if (this.pswProgramMaskFixedOverflow) {
				this.doProgramInterrupt(INTR_PGM_FIXEDPOINT_OVERFLOW);
			}
		} else if (val < 0) {
			this.pswConditionCode = CC1;
		} else if (val > 0) {
			this.pswConditionCode = CC2;
		} else { 
			this.pswConditionCode = CC0;
		}
		return 0; // ok
	};
	
	// SRDL - Shift right double logical
	private int x8C_SRDL(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// SRDL - Shift right double logical
		// specification exception if r1 is odd
		if ((r1 & 0x01) != 0) {
			this.doProgramInterrupt(INTR_PGM_SPECIFICATION_EXCEPTION);
			return 0; // ok
		}
		int shiftBy = addr2 & 0x3F; // lower 6 bits
		if (shiftBy > 31) {
			int val = this.gpr[r1];
			shiftBy -= 32;
			val >>= shiftBy;
			this.gpr[r1] = 0;
			this.gpr[r1+1] = val & ShiftRightMasks[shiftBy];
		} else {
			long val = ((long)this.gpr[r1] << 32) | (((long)this.gpr[r1+1]) & 0xFFFFFFFFL);
			val >>= shiftBy;
			this.gpr[r1] = (int)(val >> 32) & ShiftRightMasks[shiftBy];
			this.gpr[r1+1] = (int)(val & 0xFFFFFFFFL);
		}
		return 0; // ok
	};
	
	// SLDL - Shift left double logical
	private int x8D_SLDL(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// SLDL - Shift left double logical
		// specification exception if r1 is odd
		if ((r1 & 0x01) != 0) {
			this.doProgramInterrupt(INTR_PGM_SPECIFICATION_EXCEPTION);
			return 0; // ok
		}
		int shiftBy = addr2 & 0x3F; // lower 6 bits
		long val = ((long)this.gpr[r1] << 32) | (((long)this.gpr[r1+1]) & 0xFFFFFFFFL);
		val <<= shiftBy;
		this.gpr[r1] = (int)(val >> 32);
		this.gpr[r1+1] = (int)(val & 0xFFFFFFFFL);
		return 0; // ok
	};
	
	// SRDA - Shift right double
	private int x8E_SRDA(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// SRDA - Shift right double
		// specification exception if r1 is odd
		if ((r1 & 0x01) != 0) {
			this.doProgramInterrupt(INTR_PGM_SPECIFICATION_EXCEPTION);
			return 0; // ok
		}
		int shiftBy = addr2 & 0x3F; // lower 6 bits
		long val = ((long)this.gpr[r1] << 32) | (((long)this.gpr[r1+1]) & 0xFFFFFFFFL);
		val >>= shiftBy;
		this.gpr[r1] = (int)(val >> 32);
		this.gpr[r1+1] = (int)(val & 0xFFFFFFFFL);
		this.pswConditionCode = (val < 0) ? CC1 : (val > 0) ? CC2 : CC0;
		return 0; // ok
	};
	
	// SLDA - Shift left double
	private int x8F_SLDA(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// SLDA - Shift left double
		// specification exception if r1 is odd
		if ((r1 & 0x01) != 0) {
			this.doProgramInterrupt(INTR_PGM_SPECIFICATION_EXCEPTION);
			return 0; // ok
		}
		int shiftBy = addr2 & 0x3F; // lower 6 bits
		long val = ((long)this.gpr[r1] << 32) | (((long)this.gpr[r1+1]) & 0xFFFFFFFFL);
		boolean isNeg = (val < 0);
		boolean hadOverflow = false;
		for (int i = 0; i < shiftBy; i++) {
			val <<= 1;
			if ((val < 0) != isNeg) {
				// PrincOps is unclear if shifting stops as soon as overflow occurs or continues over all bits
				// but the example indicates that shifting continues
				hadOverflow = true;
			}
		}

		// set the even target register with sign + high-31bits
		this.gpr[r1] = (int)(val >> 32) & 0x7FFFFFFF;
		if (isNeg) { this.gpr[r1] |= 0x80000000; } 
		
		// set odd target register with lower-32bits 
		this.gpr[r1+1] = (int)(val & 0xFFFFFFFFL);
		
		// set condition code, checking for overflow
		if (hadOverflow) {
			this.pswConditionCode = CC3;
			if (this.pswProgramMaskFixedOverflow) {
				this.doProgramInterrupt(INTR_PGM_FIXEDPOINT_OVERFLOW);
			}
		} else if (isNeg) {
			this.pswConditionCode = CC1;
		} else if (val == 0) {
			this.pswConditionCode = CC0;
		} else { 
			this.pswConditionCode = CC2;
		}
		
		return 0; // ok
	};
	
	// STM - Store Multiple
	private int x90_STM(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r3 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// STM - Store Multiple
		int curr = r1;
		while(true) {
			int val = this.gpr[curr];
			this.mem[addr2++] = (byte)((val >> 24) & 0xFF);
			this.mem[addr2++] = (byte)((val >> 16) & 0xFF);
			this.mem[addr2++] = (byte)((val >> 8) & 0xFF);
			this.mem[addr2++] = (byte)(val & 0xFF);
			addr2 &= MemMask;
			if (curr == r3) { break; }
			curr++;
			if (curr >= 16) { curr = 0; }
		}
		return 0; // ok
	};
	
	// LM - Load multiple
	private int x98_LM(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r3 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}
		
		// LM - Load multiple
		int curr = r1;
		while(true) {
			this.gpr[curr] = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
			addr2 = (addr2 + 4) & MemMask;
			if (curr == r3) { break; }
			curr++;
			if (curr >= 16) { curr = 0; }
		}
		return 0; // ok
	};
	
	// privileged instruction with 2-byte opcode starting with 0xB2
	private int xB2_PrivInstr(int at, byte ib2, boolean updPSW) {
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// privileged instruction with 2-byte opcode starting with 0xB2
		// *some* of these instructions are implemented as non-privileged
		// if they involve only ressources inside this CPU implementation
		// and they are harmless enough in the context of this CPU implementation
		if (ib2 == (byte)0x0B) {
			// IPK - Insert PSW Key
			int r2 = this.gpr[2];
			r2 &= 0x0FFFFFFFF;
			r2 |= this.pswProtectionKey << 28;
			this.gpr[2] = r2;
			return 0;
		} else if (ib2 == (byte)0x13) {
			// RRB - Reset Reference Bit
			// (memory usage tracking is not available)
			this.pswConditionCode = CC2; // Reference bit one, change bit zero
			return 0;
		} else if (ib2 == (byte)0x0A) {
			// SKPA - Set PSW Key From Address
			this.pswProtectionKey = (short)((addr2 >> 4) & 0x0F); // bits 24-27 of the second operand
			return 0;
		}
		// all other privileged instruction must be handled by the invoker
		return 0x0000B200 | (ib2 & 0xFF);
	};
	
	// CS - Compare and swap
	private int xBA_CS(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int r3 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// CS - Compare and swap
		// specification exception if addr2 is not on a word boundary
		if ((addr2 & 0x03) != 0) {
			this.doProgramInterrupt(INTR_PGM_SPECIFICATION_EXCEPTION);
			return 0; // ok
		}
		int op1 = this.gpr[r1];
		int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
		if (op1 == op2) {
			int val = this.gpr[r3];
			this.mem[addr2++] = (byte)((val >> 24) & 0xFF);
			this.mem[addr2++] = (byte)((val >> 16) & 0xFF);
			this.mem[addr2++] = (byte)((val >> 8) & 0xFF);
			this.mem[addr2++] = (byte)(val & 0xFF);
			this.pswConditionCode = CC0;
		} else {
			this.gpr[r1] = op2;
			this.pswConditionCode = CC1;
		}
		return 0; // ok
	};
	
	// CDS - Compare double and swap
	private int xBB_CDS(int at, byte ib2, boolean updPSW) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int r3 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// CDS - Compare double and swap
		// specification exception if r1/r3 are not even or addr2 is not on a doubleword boundary
		if ((r1 & 0x01) != 0 || (r3 & 0x01) != 0 || (addr2 & 0x07) != 0) {
			this.doProgramInterrupt(INTR_PGM_SPECIFICATION_EXCEPTION);
			return 0; // ok
		}
		int op1a = this.gpr[r1];
		int op1b = this.gpr[r1+1];
		int op2a = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
		int op2b = ((this.mem[addr2+4]&0xFF)<<24) | ((this.mem[addr2+5]&0xFF)<<16) | ((this.mem[addr2+6]&0xFF)<<8) | (this.mem[addr2+7]&0xFF);
		if (op1a == op2a && op1b == op2b) {
			int val = this.gpr[r3];
			this.mem[addr2++] = (byte)((val >> 24) & 0xFF);
			this.mem[addr2++] = (byte)((val >> 16) & 0xFF);
			this.mem[addr2++] = (byte)((val >> 8) & 0xFF);
			this.mem[addr2++] = (byte)(val & 0xFF);
			val = this.gpr[r3+1];
			this.mem[addr2++] = (byte)((val >> 24) & 0xFF);
			this.mem[addr2++] = (byte)((val >> 16) & 0xFF);
			this.mem[addr2++] = (byte)((val >> 8) & 0xFF);
			this.mem[addr2++] = (byte)(val & 0xFF);
			this.pswConditionCode = CC0;
		} else {
			this.gpr[r1] = op2a;
			this.gpr[r1+1] = op2b;
			this.pswConditionCode = CC1;
		}
		return 0; // ok
	};
	
	// CLM - Compare logical under Mask
	private int xBD_CLM(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r3 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// CLM - Compare logical under Mask
		if (r3 == 0) {
			this.pswConditionCode = CC0;
			return 0; // ok
		}
		
		int opr1 = this.gpr[r1];
		int op1, op2;
		if ((r3 & 0x08) != 0) { 
			op1 = (opr1 >> 24) & 0xFF;
			op2 = this.mem[addr2++] & 0xFF;
		    if (op1 != op2) {
		    	this.pswConditionCode = (op1 < op2) ? CC1: CC2;
				return 0; // ok
		    }
		}
		if ((r3 & 0x04) != 0) { 
			op1 = (opr1 >> 16) & 0xFF;
			op2 = this.mem[addr2++] & 0xFF;
		    if (op1 != op2) {
		    	this.pswConditionCode = (op1 < op2) ? CC1: CC2;
				return 0; // ok
		    }
		}
		if ((r3 & 0x02) != 0) { 
			op1 = (opr1 >> 8) & 0xFF;
			op2 = this.mem[addr2++] & 0xFF;
		    if (op1 != op2) {
		    	this.pswConditionCode = (op1 < op2) ? CC1: CC2;
				return 0; // ok
		    }
		}
		if ((r3 & 0x01) != 0) { 
			op1 = opr1 & 0xFF;
			op2 = this.mem[addr2++] & 0xFF;
		    if (op1 != op2) {
		    	this.pswConditionCode = (op1 < op2) ? CC1: CC2;
				return 0; // ok
		    }
		}
		
		this.pswConditionCode = CC0;
		return 0; // ok
	};
	
	// STCM - Store characters under mask
	private int xBE_STCM(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r3 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// STCM - Store characters under mask
		int val = this.gpr[r1];
		if ((r3 & 0x08) != 0) { this.mem[addr2++] = (byte)((val >> 24) & 0xFF); }
		if ((r3 & 0x04) != 0) { this.mem[addr2++] = (byte)((val >> 16) & 0xFF); }
		if ((r3 & 0x02) != 0) { this.mem[addr2++] = (byte)((val >> 8) & 0xFF); }
		if ((r3 & 0x01) != 0) { this.mem[addr2++] = (byte)(val & 0xFF); }
		return 0; // ok
	};
	
	// ICM - Insert character under mask (r3 => mask)
	private int xBF_ICM(int at, byte ib2, boolean updPSW) {
		int r1 = (ib2 & 0xF0) >> 4;
		int r3 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// ICM - Insert character under mask (r3 => mask)
		int val = this.gpr[r1];
		byte firstByte = this.mem[addr2];
		byte bits = 0; // collects if "all inserted bits are zero" 
		if ((r3 & 0x08) != 0) {
			byte b = this.mem[addr2++];
			bits |= b;
			val &= 0x00FFFFFF;
			val |= ((b & 0xFF) << 24);
		}
		if ((r3 & 0x04) != 0) {
			byte b = this.mem[addr2++];
			bits |= b;
			val &= 0xFF00FFFF;
			val |= ((b & 0xFF) << 16);
		}
		if ((r3 & 0x02) != 0) {
			byte b = this.mem[addr2++];
			bits |= b;
			val &= 0xFFFF00FF;
			val |= ((b & 0xFF) << 8);
		}
		if ((r3 & 0x01) != 0) {
			byte b = this.mem[addr2++];
			bits |= b;
			val &= 0xFFFFFF00;
			val |= (b & 0xFF);
		}
		this.gpr[r1] = val;
		this.pswConditionCode = (bits == 0 || r3 == 0) ? CC0 : ((firstByte & 0x80) != 0) ? CC1 : CC2;
		return 0; // ok
	};
	
	
	/*
	 * SI instructions: D1(B1),I2
	 */
	
	// template SI Instruction
	@SuppressWarnings("unused")
	private int tmplSI(int at, byte ib2, boolean updPSW) {
		int i2 = ib2 & 0xFF;
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}
		
		// instruction code
		return EXECSTATE_UNKNOWN_INSTRUCTION;
	};
	
	// TM - Test under Mask
	private int x91_TM(int at, byte ib2, boolean updPSW) {
		int i2 = ib2 & 0xFF;
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}
		
		// TM - Test under Mask
		byte testedBits = (byte)(this.mem[addr1] & (byte)i2);
		if (i2 == 0 || testedBits == 0) {
			this.pswConditionCode = CC0;
		} else if (testedBits == (byte)i2) {
			this.pswConditionCode = CC3;
		} else {
			this.pswConditionCode = CC1;
		}
		return 0; // ok
	};
	
	// MVI - Move immediate
	private int x92_MVI(int at, byte ib2, boolean updPSW) {
		int i2 = ib2 & 0xFF;
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// MVI - Move immediate
		this.mem[addr1] = (byte)i2;
		return 0; // ok
	};
	
	// TS - Test and set
	private int x93_TS(int at, byte ib2, boolean updPSW) {
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// TS - Test and set
		// WARNING: no S/370-CPU sync performed/needed, since only one single CPU is emulated 
		this.pswConditionCode = ((this.mem[addr1] & (byte)0x80) != 0) ? CC1 : CC0;
		this.mem[addr1] = (byte)0xFF;
		return 0; // ok
	};
	
	// NI - And immediate
	private int x94_NI(int at, byte ib2, boolean updPSW) {
		int i2 = ib2 & 0xFF;
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// NI - And immediate
		int op1 = this.mem[addr1] & 0xFF;
		int res = (op1 & i2)  & 0xFF;
		this.mem[addr1] = (byte)res;
		this.pswConditionCode = (res == 0) ? CC0 : CC1;
		return 0; // ok
	};
	
	// CLI - Compare logical immediate
	private int x95_CLI(int at, byte ib2, boolean updPSW) {
		int i2 = ib2 & 0xFF;
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// CLI - Compare logical immediate
		int op1 = this.mem[addr1] & 0xFF;
		if (op1 == i2) {
			this.pswConditionCode = CC0;
		} else {
			this.pswConditionCode = (op1 < i2) ? CC1 : CC2;
		}
		return 0; // ok
	};
	
	// OI - Or immediate
	private int x96_OI(int at, byte ib2, boolean updPSW) {
		int i2 = ib2 & 0xFF;
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// OI - Or immediate
		int op1 = this.mem[addr1] & 0xFF;
		int res = (op1 | i2)  & 0xFF;
		this.mem[addr1] = (byte)res;
		this.pswConditionCode = (res == 0) ? CC0 : CC1;
		return 0; // ok
	};
	
	// XI - Exclusive or immediate
	private int x97_XI(int at, byte ib2, boolean updPSW) {
		int i2 = ib2 & 0xFF;
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// XI - Exclusive or immediate
		int op1 = this.mem[addr1] & 0xFF;
		int res = (op1 ^ i2)  & 0xFF;
		this.mem[addr1] = (byte)res;
		this.pswConditionCode = (res == 0) ? CC0 : CC1;
		return 0; // ok
	};
	
	// STNSM - Store Then AND System Mask
	private int xAC_STNSM(int at, byte ib2, boolean updPSW) {
		int i2 = ib2 & 0xFF;
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// STNSM - Store Then AND System Mask
		this.mem[addr1] = this.pswIntrMaskByte;
		byte newMask = (byte)(this.pswIntrMaskByte & i2);
		this.setIntrMask(newMask);
		return 0; // ok
	};
	
	// STOSM - Store Then OR System Mask
	private int xAD_STOSM(int at, byte ib2, boolean updPSW) {
		int i2 = ib2 & 0xFF;
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}

		// STOSM - Store Then OR System Mask
		this.mem[addr1] = this.pswIntrMaskByte;
		byte newMask = (byte)(this.pswIntrMaskByte | i2);
		this.setIntrMask(newMask);
		return 0; // ok
	};
	
	/*
	 * SS instructions: D1(L1,B1),D2(L2,B2) or D1(LL,B1),D2(B2)
	 */
	
	// template SS Instruction
	@SuppressWarnings("unused")
	private int tmplSS(int at, byte ib2, boolean updPSW) {
		int ll = (ib2 & 0xFF) + 1; // 1..256
		int l1 = ((ib2 & 0xF0) >> 4) + 1; // 1..16
		int l2 = (ib2 & 0x0F) + 1; // 1..16
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}
		
		// instruction code
		return EXECSTATE_UNKNOWN_INSTRUCTION;
	};
	
	// MVN - Move numerics
	private int xD1_MVN(int at, byte ib2, boolean updPSW) {
		int ll = (ib2 & 0xFF) + 1; // 1..256
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// MVN - Move numerics
		while(ll-- > 0) {
			byte b = (byte)(this.mem[addr1] & (byte)0xF0);
			this.mem[addr1++] = (byte)((this.mem[addr2++] & 0x0F) | b); 
		}
		return 0; // ok
	};
	
	// MVC - Move characters
	private int xD2_MVC(int at, byte ib2, boolean updPSW) {
		int ll = (ib2 & 0xFF) + 1; // 1..256
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// MVC - Move characters
		while(ll-- > 0) {
			this.mem[addr1++] = this.mem[addr2++]; 
		}
		return 0; // ok
	};
	
	// MVZ - Move zones
	private int xD3_MVZ(int at, byte ib2, boolean updPSW) {
		int ll = (ib2 & 0xFF) + 1; // 1..256
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// MVZ - Move zones
		while(ll-- > 0) {
			byte b = (byte)(this.mem[addr1] & (byte)0x0F);
			this.mem[addr1++] = (byte)((this.mem[addr2++] & 0xF0) | b); 
		}
		return 0; // ok
	};
	
	// NC - And characters
	private int xD4_NC(int at, byte ib2, boolean updPSW) {
		int ll = (ib2 & 0xFF) + 1; // 1..256
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// NC - And characters
		int op1, op2, res;
		this.pswConditionCode = CC0;
		while(ll-- > 0) {
			op1 = this.mem[addr1] & 0xFF;
			op2 = this.mem[addr2++] & 0xFF;
			res = op1 & op2;
			this.mem[addr1++] = (byte)res;
			if (res != 0) { this.pswConditionCode = CC1; }
		}
		return 0; // ok
	};
	
	// CLC - Compare logical characters
	private int xD5_CLC(int at, byte ib2, boolean updPSW) {
		int ll = (ib2 & 0xFF) + 1; // 1..256
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// CLC - Compare logical characters
		int op1, op2;
		while(ll-- > 0) {
			op1 = this.mem[addr1++] & 0xFF;
			op2 = this.mem[addr2++] & 0xFF;
			if (op1 == op2) { continue; }
			this.pswConditionCode = (op1 < op2) ? CC1 : CC2;
			return 0; // ok
		}
		
		this.pswConditionCode = CC0;
		return 0; // ok
	};
	
	// OC - Or characters
	private int xD6_OC(int at, byte ib2, boolean updPSW) {
		int ll = (ib2 & 0xFF) + 1; // 1..256
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// OC - Or characters
		int op1, op2, res;
		this.pswConditionCode = CC0;
		while(ll-- > 0) {
			op1 = this.mem[addr1] & 0xFF;
			op2 = this.mem[addr2++] & 0xFF;
			res = op1 | op2;
			this.mem[addr1++] = (byte)res;
			if (res != 0) { this.pswConditionCode = CC1; }
		}
		return 0; // ok
	};
	
	// XC - Exclusive or characters
	private int xD7_XC(int at, byte ib2, boolean updPSW) {
		int ll = (ib2 & 0xFF) + 1; // 1..256
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// XC - Exclusive or characters
		int op1, op2, res;
		this.pswConditionCode = CC0;
		while(ll-- > 0) {
			op1 = this.mem[addr1] & 0xFF;
			op2 = this.mem[addr2++] & 0xFF;
			res = op1 ^ op2;
			this.mem[addr1++] = (byte)res;
			if (res != 0) { this.pswConditionCode = CC1; }
		}
		return 0; // ok
	};
	
	// TR - Translate
	private int xDC_TR(int at, byte ib2, boolean updPSW) {
		int ll = (ib2 & 0xFF) + 1; // 1..256
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// TR - Translate
		for (int i = 0; i < ll; i++) {
			int a1 = (addr1 + i) & MemMask;
			int src = this.mem[a1] & 0xFF;
			this.mem[a1] = this.mem[(addr2 + src) & MemMask];
		}
		return 0;
	};
	
	// TRT - Translate and Test
	private int xDD_TRT(int at, byte ib2, boolean updPSW) {
		int ll = (ib2 & 0xFF) + 1; // 1..256
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// TRT - Translate and Test
		int i = 0;
		this.pswConditionCode = CC0;
		while (i < ll) {
			int a1 = (addr1 + i++) & MemMask;
			int src = this.mem[a1] & 0xFF;
			byte fnCode = this.mem[(addr2 + src) & MemMask];
			if (fnCode != 0) {
				this.gpr[1] = (this.gpr[1] & 0xFF000000) | a1;
				this.gpr[2] = (this.gpr[2] & 0xFFFFFF00) | (fnCode & 0x000000FF); 
				this.pswConditionCode = (i == ll) ? CC2 : CC1;
				return 0;
			}
		}
		return 0; // ok
	};
	
	// ED - Edit
	private int xDE_ED(int at, byte ib2, boolean updPSW) throws PSWException {
		int ll = (ib2 & 0xFF) + 1; // 1..256
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// ED - Edit
		int[] res = this.decimalImpl.instrEditAndMark(
				this.pswConditionCode,
				this.mem,
				addr1, ll,
				addr2, 0,
				this.gpr[1]);
		if (res[0] > CC3) {
			this.doProgramInterrupt((short)(res[0] >> 16));
			return 0; // ok
		}
		this.pswConditionCode = (byte)res[0];
		return 0; // ok
	};
	
	// EDMK - Edit and mark
	private int xDF_EDMK(int at, byte ib2, boolean updPSW) throws PSWException {
		int ll = (ib2 & 0xFF) + 1; // 1..256
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// EDMK - Edit and mark
		int[] res = this.decimalImpl.instrEditAndMark(
				this.pswConditionCode,
				this.mem,
				addr1, ll,
				addr2, 0,
				this.gpr[1]);
		this.gpr[1] = res[1];
		if (res[0] > CC3) {
			this.doProgramInterrupt((short)(res[0] >> 16));
			return 0; // ok
		}
		this.pswConditionCode = (byte)res[0];
		return 0; // ok
	};
	
	// SRP - shift and round decimal
	private int xF0_SRP(int at, byte ib2, boolean updPSW) throws PSWException {
		int l1 = ((ib2 & 0xF0) >> 4) + 1; // 1..16
		int l2 = (ib2 & 0x0F) + 1; // 1..16
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// SRP - shift and round decimal
		int res = this.decimalImpl.instrShiftAndRound(
				this.pswConditionCode, 
				this.mem,
				addr1, l1,
				addr2,
				l2 - 1);
		if (res > CC3) {
			this.doProgramInterrupt((short)(res >> 16));
			return 0; // ok
		}
		this.pswConditionCode = (byte)res;
		if (this.pswConditionCode == CC3 && this.pswProgramMaskDecimalOverflow) {
			this.doProgramInterrupt(INTR_PGM_DECIMAL_OVERFLOW);
		}
		return 0; // ok
	};
	
	// MVO - Move with offset
	private int xF1_MVO(int at, byte ib2, boolean updPSW) {
		int l1 = ((ib2 & 0xF0) >> 4) + 1; // 1..16
		int l2 = (ib2 & 0x0F) + 1; // 1..16
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// MVO - Move with offset
		this.decimalImpl.instrMoveWithOffset(
				this.mem,
				addr1, l1,
				addr2, l2);
		return 0; // ok
	};
	
	// PACK - Pack
	private int xF2_PACK(int at, byte ib2, boolean updPSW) {
		int l1 = ((ib2 & 0xF0) >> 4) + 1; // 1..16
		int l2 = (ib2 & 0x0F) + 1; // 1..16
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// PACK - Pack
		this.decimalImpl.instrPack(
				this.mem,
				addr1, l1,
				addr2, l2);
		return 0; // ok
	};
	
	// UNPK - Unpack
	private int xF3_UNPK(int at, byte ib2, boolean updPSW) {
		int l1 = ((ib2 & 0xF0) >> 4) + 1; // 1..16
		int l2 = (ib2 & 0x0F) + 1; // 1..16
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// UNPK - Unpack
		this.decimalImpl.instrUnpack(
				this.mem,
				addr1, l1,
				addr2, l2);
		return 0; // ok
	};
	
	// ZAP - Zero add packed
	private int xF8_ZAP(int at, byte ib2, boolean updPSW) throws PSWException {
		int l1 = ((ib2 & 0xF0) >> 4) + 1; // 1..16
		int l2 = (ib2 & 0x0F) + 1; // 1..16
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// ZAP - Zero add packed
		int res = this.decimalImpl.instrZeroAndAdd(
				this.pswConditionCode, 
				this.mem,
				addr1, l1,
				addr2, l2);
		if (res > CC3) {
			this.doProgramInterrupt((short)(res >> 16));
			return 0; // ok
		}
		this.pswConditionCode = (byte)res;
		if (this.pswConditionCode == CC3 && this.pswProgramMaskDecimalOverflow) {
			this.doProgramInterrupt(INTR_PGM_DECIMAL_OVERFLOW);
		}
		return 0; // ok
	};
	
	// CP - Compare packed (decimal)
	private int xF9_CP(int at, byte ib2, boolean updPSW) throws PSWException {
		int l1 = ((ib2 & 0xF0) >> 4) + 1; // 1..16
		int l2 = (ib2 & 0x0F) + 1; // 1..16
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// CP - Compare packed (decimal)
		int res = this.decimalImpl.instrCompare(
				this.pswConditionCode, 
				this.mem,
				addr1, l1,
				addr2, l2);
		if (res > CC3) {
			this.doProgramInterrupt((short)(res >> 16));
			return 0; // ok
		}
		this.pswConditionCode = (byte)res;
		return 0; // ok
	};
	
	// AP - Add packed (decimal)
	private int xFA_AP(int at, byte ib2, boolean updPSW) throws PSWException {
		int l1 = ((ib2 & 0xF0) >> 4) + 1; // 1..16
		int l2 = (ib2 & 0x0F) + 1; // 1..16
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// AP - Add packed (decimal)
		int res = this.decimalImpl.instrAdd(
				this.pswConditionCode, 
				this.mem,
				addr1, l1,
				addr2, l2);
		if (res > CC3) {
			this.doProgramInterrupt((short)(res >> 16));
			return 0; // ok
		}
		this.pswConditionCode = (byte)res;
		if (this.pswConditionCode == CC3 && this.pswProgramMaskDecimalOverflow) {
			this.doProgramInterrupt(INTR_PGM_DECIMAL_OVERFLOW);
		}
		return 0; // ok
	};
	
	// SP - Subtract packed (decimal)
	private int xFB_SP(int at, byte ib2, boolean updPSW) throws PSWException {
		int l1 = ((ib2 & 0xF0) >> 4) + 1; // 1..16
		int l2 = (ib2 & 0x0F) + 1; // 1..16
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// SP - Subtract packed (decimal)
		int res = this.decimalImpl.instrSubtract(
				this.pswConditionCode, 
				this.mem,
				addr1, l1,
				addr2, l2);
		if (res > CC3) {
			this.doProgramInterrupt((short)(res >> 16));
			return 0; // ok
		}
		this.pswConditionCode = (byte)res;
		if (this.pswConditionCode == CC3 && this.pswProgramMaskDecimalOverflow) {
			this.doProgramInterrupt(INTR_PGM_DECIMAL_OVERFLOW);
		}
		return 0; // ok
	};
	
	// MP - multiply packed (decimal)
	private int xFC_MP(int at, byte ib2, boolean updPSW) throws PSWException {
		int l1 = ((ib2 & 0xF0) >> 4) + 1; // 1..16
		int l2 = (ib2 & 0x0F) + 1; // 1..16
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// MP - multiply packed (decimal)
		int res = this.decimalImpl.instrMultiply(
				this.pswConditionCode, 
				this.mem,
				addr1, l1,
				addr2, l2);
		if (res > CC3) {
			this.doProgramInterrupt((short)(res >> 16));
			return 0; // ok
		}
		// the condition code remains unchanged
		return 0; // ok
	};
	
	// DP - Divide packed
	private int xFD_DP(int at, byte ib2, boolean updPSW) throws PSWException {
		int l1 = ((ib2 & 0xF0) >> 4) + 1; // 1..16
		int l2 = (ib2 & 0x0F) + 1; // 1..16
		byte ib3 = this.mem[at+2];
		int b1 = (ib3 & 0xF0) >> 4;
		int ddd1 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
		byte ib5 = this.mem[at+4];
		int b2 = (ib5 & 0xF0) >> 4;
		int ddd2 = ((ib5 & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
		int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 3 half-words
		if (updPSW) {
			this.pswInstructionAddress += 6;
			this.pswInstructionLengthCode = 3;
		}

		// DP - Divide packed
		int res = this.decimalImpl.instrDivide(
				this.pswConditionCode, 
				this.mem,
				addr1, l1,
				addr2, l2);
		if (res > CC3) {
			this.doProgramInterrupt((short)(res >> 16));
			return 0; // ok
		}
		// The condition code remains unchanged
		return 0; // ok
	};
	
	
	/*
	 * RR floating-point instructions: R1,R2
	 */
	
	public interface FpRRCode {
		public int execute(byte oldCC, FloatRepresentation f1, FloatRepresentation f2, boolean allowUnderflow, boolean allowSignificance);
	}
	
	private int fpRR(byte ib2, boolean updPSW, FpRRCode code) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int r2 = ib2 & 0x0F;
		
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 2;
			this.pswInstructionLengthCode = 1;
		}
		
		// do the operation
		if (r1 != 0 && r1 != 2 && r1 != 4 && r1 != 6) { return INTR_PGM_SPECIFICATION_EXCEPTION; }
		if (r2 != 0 && r2 != 2 && r2 != 4 && r2 != 6) { return INTR_PGM_SPECIFICATION_EXCEPTION; }
		FloatRepresentation f1 = this.floatImpl.fpr[r1 / 2];
		FloatRepresentation f2 = this.floatImpl.fpr[r2 / 2];
		int res = code.execute(
				this.pswConditionCode, f1, f2,
				this.pswProgramMaskExponentUnderflow, this.pswProgramMaskSignificance);
		if (res == -1) {
			// unknown instruction
			return -1;
		}
		if (res > CC3) {
			this.doProgramInterrupt((short)(res >> 16));
			return 0; // ok
		}
		this.pswConditionCode = (byte)res;
		return 0; // ok
	}
	
	// LPDR - LOAD POSITIVE (long)
	private int x20_LPDR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
						f1.setFrom(f2);
						f1.isPositive = true;
						return (f1.isZero) ? 0 : 2;
					});
	}
	
	// LNDR - LOAD NEGATIVE (long)
	private int x21_LNDR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					f1.setFrom(f2);
					f1.isPositive = false;
					return (f1.isZero) ? 0 : 1;
				});
	}
	
	// LTDR - LOAD AND TEST (long)
	private int x22_LTDR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					f1.setFrom(f2);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), allowUnderflow, false);
				});
	}
	
	// LCDR - LOAD COMPLEMENT (long)
	private int x23_LCDR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					f1.setFrom(f2);
					f1.isPositive = !f1.isPositive;
					return FloatImpl.generateResult(oldCC, f1, f1.test(), allowUnderflow, false);
				});
	}
	
	// HDR - HALVE (long)
	private int x24_HDR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					f1.halveFrom(f2);
					return FloatImpl.generateResult(oldCC, f1, allowUnderflow, allowSignificance);
				});
	}
	
	// LRDR - LOAD ROUNDED (extended to long)
	private int x25_LRDR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					return INTR_PGM_OPERATION_EXCEPTION; // Feature "extended floating-point" not installe
				});
	}
	
	// MXR - MULTIPLY (extended)
	private int x26_MXR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					return INTR_PGM_OPERATION_EXCEPTION; // Feature "extended floating-point" not installed
				});
	}
	
	// MXDR - MULTIPLY (long to extended)
	private int x27_MXDR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
				return INTR_PGM_OPERATION_EXCEPTION; // Feature "extended floating-point" not installed
			});
	}
	
	// LDR - LOAD (long)
	private int x28_LDR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					f1.setFrom(f2);
					return oldCC;
				});
	}
	
	// CDR - COMPARE (long)
	private int x29_CDR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					return FloatImpl.CMP2CC[f1.compareWith(f2) + 1];
				});
	}
	
	// ADR - ADD NORMALIZED (long)
	private int x2A_ADR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					allowSignificance &= f1.isZero;
					f1.addOrSubtract(f2, false);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
				});
	}
	
	// SDR - SUBTRACT NORMALIZED (long)
	private int x2B_SDR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					allowSignificance &= f1.isZero;
					f1.addOrSubtract(f2, true);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
				});
	}
	
	// MDR - MULTIPLY (long)
	private int x2C_MDR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					allowSignificance &= f1.isZero;
					f1.multiply(f2);
					return FloatImpl.generateResult(oldCC, f1, allowUnderflow, allowSignificance);
				});
	}
	
	// DDR - DIVIDE (long)
	private int x2D_DDR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					if (f2.isZero) { return INTR_PGM_FLOAT_DIVIDE; }
					allowSignificance &= f1.isZero;
					f1.divide(f2);
					return FloatImpl.generateResult(oldCC, f1, allowUnderflow, allowSignificance);
				});
	}
	
	// AWR - ADD UNNORMALIZED (long)
	private int x2E_AWR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					allowSignificance &= f1.isZero;
					f1.addOrSubtract(f2, false);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), false, allowSignificance);
				});
	}
	
	// SWR - SUBTRACT UNNORMALIZED (long)
	private int x2F_SWR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					allowSignificance &= f1.isZero;
					f1.addOrSubtract(f2, true);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), false, allowSignificance);
				});
	}
	
	// LPER - LOAD POSITIVE (short)
	private int x30_LPER(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					f1.setFrom(f2);
					f1.isPositive = true;
					return (f1.isZero) ? 0 : 2;
				});
	}
	
	// LNER - LOAD NEGATIVE (short)
	private int x31_LNER(int at, byte ib2, boolean updPSW) throws PSWException  {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					f1.setFrom(f2);
					f1.isPositive = false;
					return (f1.isZero) ? 0 : 1;
				});
	}
	
	// LTER - LOAD AND TEST (short)
	private int x32_LTER(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					f1.setFrom(f2);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
				});
	}
	
	// LCER - LOAD COMPLEMENT (short)
	private int x33_LCER(int at, byte ib2, boolean updPSW) throws PSWException { 
	return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					f1.setFrom(f2);
					f1.isPositive = !f1.isPositive;
					return FloatImpl.generateResult(oldCC, f1, f1.test(), allowUnderflow, false);
				});
	}
	
	// HER - HALVE (short)
	private int x34_HER(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					f1.halveFrom(f2);
					return FloatImpl.generateResult(oldCC, f1, allowUnderflow, allowSignificance);
				});
	}
	
	// LRER - LOAD ROUNDED (long to short)
	private int x35_LRER(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					f1.halveFrom(f2);
					f1.roundToShort();
					return FloatImpl.generateResult(oldCC, f1, allowUnderflow, false);
				});
	}
	
	// AXR - ADD NORMALIZED (extended)
	private int x36_AXR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					return INTR_PGM_OPERATION_EXCEPTION; // Feature "extended floating-point" not installed
				});
	}
	
	// SXR - SUBTRACT NORMALIZED (extended)
	private int x37_SXR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					return INTR_PGM_OPERATION_EXCEPTION; // Feature "extended floating-point" not installed
				});
	}
	
	// LER - LOAD (short)
	private int x38_LER(int at, byte ib2, boolean updPSW) throws PSWException { 
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					f1.setFrom(f2);
					return oldCC;
				});
	}
	
	// CER - COMPARE (short)
	private int x39_CER(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					return FloatImpl.CMP2CC[f1.compareWith(f2) + 1];
				});
	}
	
	// AER - ADD NORMALIZED (short)
	private int x3A_AER(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					allowSignificance &= f1.isZero;
					f1.addOrSubtract(f2, false);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
				});
	}
	
	// SER - SUBTRACT NORMALIZED (short)
	private int x3B_SER(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					allowSignificance &= f1.isZero;
					f1.addOrSubtract(f2, true);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
				});
	}
	
	// MER - MULTIPLY (short to long)
	private int x3C_MER(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					allowSignificance &= f1.isZero;
					f1.multiply(f2);
					return FloatImpl.generateResult(oldCC, f1, allowUnderflow, allowSignificance);
				});
	}
	
	// DER - DIVIDE (short)
	private int x3D_DER(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					if (f2.isZero) { return INTR_PGM_FLOAT_DIVIDE; }
					allowSignificance &= f1.isZero;
					f1.divide(f2);
					return FloatImpl.generateResult(oldCC, f1, allowUnderflow, allowSignificance);
				});
	}
	
	// AUR - ADD UNNORMALIZED (short)
	private int x3E_AUR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					allowSignificance &= f1.isZero;
					f1.addOrSubtract(f2, false);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), false, allowSignificance);
				});
	}
	
	// SUR - SUBTRACT UNNORMALIZED (short)
	private int x3F_SUR(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRR(ib2, updPSW, (oldCC, f1, f2, allowUnderflow, allowSignificance) -> {
					allowSignificance &= f1.isZero;
					f1.addOrSubtract(f2, true);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), false, allowSignificance);
				});
	}
	
	
	/*
	 * RX floating point instructions: R1,D2(X2,B2)
	 */
	
	public interface FpRXCode {
		public int execute(byte oldCC, FloatRepresentation f1, int addr, boolean allowUnderflow, boolean allowSignificance);
	}
	
	private int fpRX(int at, byte ib2, boolean updPSW, FpRXCode code) throws PSWException {
		int r1 = (ib2 & 0xF0) >> 4;
		int x2 = ib2 & 0x0F;
		byte ib3 = this.mem[at+2];
		int b2 = (ib3 & 0xF0) >> 4;
		int ddd2 = ((ib3 & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
		int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
		
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}
		
		// do the operation
		if (r1 != 0 && r1 != 2 && r1 != 4 && r1 != 6) { return INTR_PGM_SPECIFICATION_EXCEPTION; }
		FloatRepresentation f1 = this.floatImpl.fpr[r1 / 2];
		int res = code.execute(
				this.pswConditionCode, f1, addr2,
				this.pswProgramMaskExponentUnderflow, this.pswProgramMaskSignificance);
		if (res == -1) {
			// unknown instruction..?
			return -1;
		}
		if (res > CC3) {
			this.doProgramInterrupt((short)(res >> 16));
			return 0; // ok
		}
		this.pswConditionCode = (byte)res;
		return 0; // ok
	}
	
	
	// STD - STORE (long)
	private int x60_STD(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					f1.externalize(this.mem, addr, true);
					return oldCC;
				});
	}
	
	// XD - MULTIPLY (long to extended)
	private int x67_XD(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					return INTR_PGM_OPERATION_EXCEPTION; // Feature "extended floating-point" not installed
				});
	}
	
	// LD - LOAD (long)
	private int x68_LD(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					f1.internalize(mem, addr, true);
					return oldCC;
				});
	}
	
	// CD - COMPARE (long)
	private int x69_CD(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					this.floatImpl.temp.internalize(mem, addr, true);
					return FloatImpl.CMP2CC[f1.compareWith(this.floatImpl.temp) + 1];
				});
	}
	
	// AD - ADD NORMALIZED (long)
	private int x6A_AD(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					this.floatImpl.temp.internalize(mem, addr, true);
					allowSignificance &= f1.isZero;
					f1.addOrSubtract(this.floatImpl.temp, false);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
				});
	}
	
	// SD - SUBTRACT NORMALIZED (long)
	private int x6B_SD(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					this.floatImpl.temp.internalize(mem, addr, true);
					allowSignificance &= f1.isZero;
					f1.addOrSubtract(this.floatImpl.temp, true);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
				});
	}
	
	// MD - MULTIPLY (long)
	private int x6C_MD(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					this.floatImpl.temp.internalize(mem, addr, true);
					allowSignificance &= f1.isZero;
					f1.multiply(this.floatImpl.temp);
					return FloatImpl.generateResult(oldCC, f1, allowUnderflow, allowSignificance);
				});
	}
	
	// DD - DIVIDE (long)
	private int x6D_DD(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					this.floatImpl.temp.internalize(mem, addr, true);
					if (this.floatImpl.temp.isZero) { return INTR_PGM_FLOAT_DIVIDE; }
					allowSignificance &= f1.isZero;
					f1.divide(this.floatImpl.temp);
					return FloatImpl.generateResult(oldCC, f1, allowUnderflow, allowSignificance);
				});
	}
	
	// AW - ADD UNNORMALIZED (long)
	private int x6E_AW(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					this.floatImpl.temp.internalize(mem, addr, true);
					allowSignificance &= f1.isZero;
					f1.addOrSubtract(this.floatImpl.temp, false);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), false, allowSignificance);
				});
	}
	
	// SW - SUBTRACT UNNORMALIZED (long)
	private int x6F_SW(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					this.floatImpl.temp.internalize(mem, addr, true);
					allowSignificance &= f1.isZero;
					f1.addOrSubtract(this.floatImpl.temp, true);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), false, allowSignificance);
				});
	}
	
	// STE - STORE (short)
	private int x70_STE(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					f1.externalize(mem, addr, false);
					return oldCC;
				});
	}
	
	// LE - LOAD (short)
	private int x78_LE(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					f1.internalize(mem, addr, false);
					return oldCC;
				});
	}
	
	// CE - COMPARE (short)
	private int x79_CE(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					this.floatImpl.temp.internalize(mem, addr, false);
					return FloatImpl.CMP2CC[f1.compareWith(this.floatImpl.temp) + 1];
				});
	}
	
	// AE - ADD NORMALIZED (short)
	private int x7A_AE(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					this.floatImpl.temp.internalize(mem, addr, false);
					allowSignificance &= f1.isZero;
					f1.addOrSubtract(this.floatImpl.temp, false);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
				});
	}
	
	// SE - SUBTRACT NORMALIZED (short)
	private int x7B_SE(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					this.floatImpl.temp.internalize(mem, addr, false);
					allowSignificance &= f1.isZero;
					f1.addOrSubtract(this.floatImpl.temp, true);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
				});
	}
	
	// ME - MULTIPLY (short to long)
	private int x7C_ME(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					this.floatImpl.temp.internalize(mem, addr, false);
					allowSignificance &= f1.isZero;
					f1.multiply(this.floatImpl.temp);
					return FloatImpl.generateResult(oldCC, f1, allowUnderflow, allowSignificance);
				});
	}
	
	// DE - DIVIDE (short)
	private int x7D_DE(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					this.floatImpl.temp.internalize(mem, addr, false);
					if (this.floatImpl.temp.isZero) { return INTR_PGM_FLOAT_DIVIDE; }
					allowSignificance &= f1.isZero;
					f1.divide(this.floatImpl.temp);
					return FloatImpl.generateResult(oldCC, f1, allowUnderflow, allowSignificance);
				});
	}
	
	// AU - ADD UNNORMALIZED (short)
	private int x7E_AU(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					this.floatImpl.temp.internalize(mem, addr, false);
					allowSignificance &= f1.isZero;
					f1.addOrSubtract(this.floatImpl.temp, false);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), false, allowSignificance);
				});
	}
	
	// SU - SUBTRACT UNNORMALIZED (short)
	private int x7F_SU(int at, byte ib2, boolean updPSW) throws PSWException {
		return this.fpRX(at, ib2, updPSW, (oldCC, f1, addr, allowUnderflow, allowSignificance) -> {
					this.floatImpl.temp.internalize(mem, addr, false);
					allowSignificance &= f1.isZero;
					f1.addOrSubtract(this.floatImpl.temp, true);
					return FloatImpl.generateResult(oldCC, f1, f1.test(), false, allowSignificance);
				});
	}
	
	/*
	 * privileged instructions
	 */
	
	// DIAG - Diagnose
	private int x83_DIAG(int at, byte ib2, boolean updPSW) {
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}
				
		return 
			0x83000000 
			| ((ib2 & 0xFF) << 16)
			| ((this.mem[at+2] & 0xFF)  << 8)
			| (this.mem[at+3] & 0xFF);
	};
		
	// WRD - Write Direct
	private int x84_WRD(int at, byte ib2, boolean updPSW) {
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}
				
		return 0x00000084;
	};
	
	// RDD - Read Direct
	private int x85_RRD(int at, byte ib2, boolean updPSW) {
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}
				
		return 0x00000085;
	};
	
	// SIO - Start IO (fast)
	private int x9C_SIO(int at, byte ib2, boolean updPSW) {
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}
				
		return 
			0x00009C00
			| (ib2 & 0xFF);
	};
		
	// TIO - Test IO 
	private int x9D_TIO(int at, byte ib2, boolean updPSW) {
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}
				
		return
			0x00009D00
			| (ib2 & 0xFF);
	};
		
	// HIO - Halt IO
	private int x9E_HIO(int at, byte ib2, boolean updPSW) {
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}
				
		return
			0x00009E00
			| (ib2 & 0xFF);
	};
		
	// TCH - Test channel
	private int x9F_TCH(int at, byte ib2, boolean updPSW) {
		// instruction length: 1 half-word
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}
				
		return
			0x00009F00
			| (ib2 & 0xFF);
	};
	
	// LRA - Load Real Address
	private int xB1_LRA(int at, byte ib2, boolean updPSW) {
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}
				
		return 0x000000B1;
	};
	
	// STCTL - Store Control
	private int xB6_STCTL(int at, byte ib2, boolean updPSW) {
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}
				
		return 0x000000B6;
	};
	
	// LCTL - Load Control
	private int xB7_LCTL(int at, byte ib2, boolean updPSW) {
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}
				
		return 0x000000B7;
	};
	
	// SIGP - Signal Processor
	private int xAE_SIGP(int at, byte ib2, boolean updPSW) {
		// instruction length: 2 half-words
		if (updPSW) {
			this.pswInstructionAddress += 4;
			this.pswInstructionLengthCode = 2;
		}
				
		return 0x000000AE;
	};
		
	/*
	 * instruction table: opcode -> implementation
	 */
	private Instr370[] instructions = {
		/* 00 .. 0F */
			/*00*/ invInstr,
			/*01*/ invInstr,
			/*02*/ invInstr,
			/*03*/ invInstr,
			/*04*/ this::x04_SPM,
			/*05*/ this::x05_BALR,
			/*06*/ this::x06_BCTR,
			/*07*/ this::x07_BCR,
			/*08*/ this::x08_SSK,
			/*09*/ this::x09_ISK,
			/*0A*/ this::x0A_SVC,
			/*0B*/ this::x0B_BSM,
			/*0C*/ this::x0C_BASSM,
			/*0D*/ this::x0D_BASM,
			/*0E*/ this::x0E_MVCL,
			/*0F*/ this::x0F_CLCL,
		/* 10 .. 1F */
			/*10*/ this::x10_LPR,
			/*11*/ this::x11_LNR,
			/*12*/ this::x12_LTR,
			/*13*/ this::x13_LCR,
			/*14*/ this::x14_NR,
			/*15*/ this::x15_CLR,
			/*16*/ this::x16_OR,
			/*17*/ this::x17_XR,
			/*18*/ this::x18_LR,
			/*19*/ this::x19_CR,
			/*1A*/ this::x1A_AR,
			/*1B*/ this::x1B_SR,
			/*1C*/ this::x1C_MR,
			/*1D*/ this::x1D_DR,
			/*1E*/ this::x1E_ALR,
			/*1F*/ this::x1F_SLR,
		/* 20 .. 2F */
			/*20*/ this::x20_LPDR,
			/*21*/ this::x21_LNDR,
			/*22*/ this::x22_LTDR,
			/*23*/ this::x23_LCDR,
			/*24*/ this::x24_HDR,
			/*25*/ this::x25_LRDR,
			/*26*/ this::x26_MXR,
			/*27*/ this::x27_MXDR,
			/*28*/ this::x28_LDR,
			/*29*/ this::x29_CDR,
			/*2A*/ this::x2A_ADR,
			/*2B*/ this::x2B_SDR,
			/*2C*/ this::x2C_MDR,
			/*2D*/ this::x2D_DDR,
			/*2E*/ this::x2E_AWR,
			/*2F*/ this::x2F_SWR,
		/* 30 .. 3F */
			/*30*/ this::x30_LPER,
			/*31*/ this::x31_LNER,
			/*32*/ this::x32_LTER,
			/*33*/ this::x33_LCER,
			/*34*/ this::x34_HER,
			/*35*/ this::x35_LRER,
			/*36*/ this::x36_AXR,
			/*37*/ this::x37_SXR,
			/*38*/ this::x38_LER,
			/*39*/ this::x39_CER,
			/*3A*/ this::x3A_AER,
			/*3B*/ this::x3B_SER,
			/*3C*/ this::x3C_MER,
			/*3D*/ this::x3D_DER,
			/*3E*/ this::x3E_AUR,
			/*3F*/ this::x3F_SUR,
		/* 40 .. 4F */
			/*40*/ this::x40_STH,
			/*41*/ this::x41_LA,
			/*42*/ this::x42_STC,
			/*43*/ this::x43_IC,
			/*44*/ this::x44_EX,
			/*45*/ this::x45_BAL,
			/*46*/ this::x46_BCT,
			/*47*/ this::x47_BC,
			/*48*/ this::x48_LH,
			/*49*/ this::x49_CH,
			/*4A*/ this::x4A_AH,
			/*4B*/ this::x4B_SH,
			/*4C*/ this::x4C_MH,
			/*4D*/ this::x4D_BAS,
			/*4E*/ this::x4E_CVD,
			/*4F*/ this::x4F_CVB,
		/* 50 .. 5F */
			/*50*/ this::x50_ST,
			/*51*/ invInstr,
			/*52*/ invInstr,
			/*53*/ invInstr,
			/*54*/ this::x54_N,
			/*55*/ this::x55_CL,
			/*56*/ this::x56_O,
			/*57*/ this::x57_X,
			/*58*/ this::x58_L,
			/*59*/ this::x59_C,
			/*5A*/ this::x5A_A,
			/*5B*/ this::x5B_S,
			/*5C*/ this::x5C_M,
			/*5D*/ this::x5D_D,
			/*5E*/ this::x5E_AL,
			/*5F*/ this::x5F_SL,
		/* 60 .. 6F */
			/*60*/ this::x60_STD,
			/*61*/ invInstr,
			/*62*/ invInstr,
			/*63*/ invInstr,
			/*64*/ invInstr,
			/*65*/ invInstr,
			/*66*/ invInstr,
			/*67*/ this::x67_XD,
			/*68*/ this::x68_LD,
			/*69*/ this::x69_CD,
			/*6A*/ this::x6A_AD,
			/*6B*/ this::x6B_SD,
			/*6C*/ this::x6C_MD,
			/*6D*/ this::x6D_DD,
			/*6E*/ this::x6E_AW,
			/*6F*/ this::x6F_SW,
		/* 70 .. 7F */
			/*70*/ this::x70_STE,
			/*71*/ invInstr,
			/*72*/ invInstr,
			/*73*/ invInstr,
			/*74*/ invInstr,
			/*75*/ invInstr,
			/*76*/ invInstr,
			/*77*/ invInstr,
			/*78*/ this::x78_LE,
			/*79*/ this::x79_CE,
			/*7A*/ this::x7A_AE,
			/*7B*/ this::x7B_SE,
			/*7C*/ this::x7C_ME,
			/*7D*/ this::x7D_DE,
			/*7E*/ this::x7E_AU,
			/*7F*/ this::x7F_SU,
		/* 80 .. 8F */
			/*80*/ this::x80_SSM,
			/*81*/ invInstr,
			/*82*/ this::x82_LPSW,
			/*83*/ this::x83_DIAG,
			/*84*/ this::x84_WRD,
			/*85*/ this::x85_RRD,
			/*86*/ this::x86_BXH,
			/*87*/ this::x87_BXLE,
			/*88*/ this::x88_SRL,
			/*89*/ this::x89_SLL,
			/*8A*/ this::x8A_SRA,
			/*8B*/ this::x8B_SLA,
			/*8C*/ this::x8C_SRDL,
			/*8D*/ this::x8D_SLDL,
			/*8E*/ this::x8E_SRDA,
			/*8F*/ this::x8F_SLDA,
		/* 90 .. 9F */
			/*90*/ this::x90_STM,
			/*91*/ this::x91_TM,
			/*92*/ this::x92_MVI,
			/*93*/ this::x93_TS,
			/*94*/ this::x94_NI,
			/*95*/ this::x95_CLI,
			/*96*/ this::x96_OI,
			/*97*/ this::x97_XI,
			/*98*/ this::x98_LM,
			/*99*/ invInstr,
			/*9A*/ invInstr,
			/*9B*/ invInstr,
			/*9C*/ this::x9C_SIO,
			/*9D*/ this::x9D_TIO,
			/*9E*/ this::x9E_HIO,
			/*9F*/ this::x9F_TCH,
		/* A0 .. AF */
			/*A0*/ invInstr,
			/*A1*/ invInstr,
			/*A2*/ invInstr,
			/*A3*/ invInstr,
			/*A4*/ invInstr,
			/*A5*/ invInstr,
			/*A6*/ invInstr,
			/*A7*/ invInstr,
			/*A8*/ invInstr,
			/*A9*/ invInstr,
			/*AA*/ invInstr,
			/*AB*/ invInstr,
			/*AC*/ this::xAC_STNSM,
			/*AD*/ this::xAD_STOSM,
			/*AE*/ this::xAE_SIGP,
			/*AF*/ invInstr,
		/* B0 .. BF */
			/*B0*/ invInstr,
			/*B1*/ this::xB1_LRA,
			/*B2*/ this::xB2_PrivInstr,
			/*B3*/ invInstr,
			/*B4*/ invInstr,
			/*B5*/ invInstr,
			/*B6*/ this::xB6_STCTL,
			/*B7*/ this::xB7_LCTL,
			/*B8*/ invInstr,
			/*B9*/ invInstr,
			/*BA*/ this::xBA_CS,
			/*BB*/ this::xBB_CDS,
			/*BC*/ invInstr,
			/*BD*/ this::xBD_CLM,
			/*BE*/ this::xBE_STCM,
			/*BF*/ this::xBF_ICM,
		/* C0 .. CF */
			/*C0*/ invInstr,
			/*C1*/ invInstr,
			/*C2*/ invInstr,
			/*C3*/ invInstr,
			/*C4*/ invInstr,
			/*C5*/ invInstr,
			/*C6*/ invInstr,
			/*C7*/ invInstr,
			/*C8*/ invInstr,
			/*C9*/ invInstr,
			/*CA*/ invInstr,
			/*CB*/ invInstr,
			/*CC*/ invInstr,
			/*CD*/ invInstr,
			/*CE*/ invInstr,
			/*CF*/ invInstr,
		/* D0 .. DF */
			/*D0*/ invInstr,
			/*D1*/ this::xD1_MVN,
			/*D2*/ this::xD2_MVC,
			/*D3*/ this::xD3_MVZ,
			/*D4*/ this::xD4_NC,
			/*D5*/ this::xD5_CLC,
			/*D6*/ this::xD6_OC,
			/*D7*/ this::xD7_XC,
			/*D8*/ invInstr,
			/*D9*/ invInstr,
			/*DA*/ invInstr,
			/*DB*/ invInstr,
			/*DC*/ this::xDC_TR,
			/*DD*/ this::xDD_TRT,
			/*DE*/ this::xDE_ED,
			/*DF*/ this::xDF_EDMK,
		/* E0 .. EF */
			/*E0*/ invInstr,
			/*E1*/ invInstr,
			/*E2*/ invInstr,
			/*E3*/ invInstr,
			/*E4*/ invInstr,
			/*E5*/ invInstr,
			/*E6*/ invInstr,
			/*E7*/ invInstr,
			/*E8*/ invInstr, // MVCIN
			/*E9*/ invInstr,
			/*EA*/ invInstr,
			/*EB*/ invInstr,
			/*EC*/ invInstr,
			/*ED*/ invInstr,
			/*EE*/ invInstr,
			/*EF*/ invInstr,
		/* F0 .. FF */
			/*F0*/ this::xF0_SRP,
			/*F1*/ this::xF1_MVO,
			/*F2*/ this::xF2_PACK,
			/*F3*/ this::xF3_UNPK,
			/*F4*/ invInstr,
			/*F5*/ invInstr,
			/*F6*/ invInstr,
			/*F7*/ invInstr,
			/*F8*/ this::xF8_ZAP,
			/*F9*/ this::xF9_CP,
			/*FA*/ this::xFA_AP,
			/*FB*/ this::xFB_SP,
			/*FC*/ this::xFC_MP,
			/*FD*/ this::xFD_DP,
			/*FE*/ invInstr,
			/*FF*/ invInstr
	};


	public int execInstruction(int exAt) throws PSWException {
		// get the instruction code
		boolean notEX = (exAt == 0); 
		int at = (notEX) ? this.pswInstructionAddress : (exAt & MemMask & 0xFFFFFFFE);
		byte instrByte2 = (notEX) ? this.mem[at+1] : (byte)((exAt >> 24) & 0x000000FF);
		int instr = this.mem[at] & 0xFF;
		
		// count instructions
		this.insnTotal++;
		
		// log instructions if activated
		if (INSNS_LOG) {
			byte nibble1 = (byte)(instr & 0xF0);
			if (nibble1 == (byte)0x00 || nibble1 == (byte)0x10) {
				// RR or MR instruction: R1,R2 (with R1 may be the mask)
				int r1 = (instrByte2 & 0xF0) >> 4;
				int r2 = instrByte2 & 0x0F;
				int op1 = this.gpr[r1];
				int op2 = this.gpr[r2];

				logInstruction(
						_insnformatpatterns[instr],
						_mnemonics[instr], this.mem[at], at,
						r1, op1, r2, op2);
				
			} else if (nibble1 == (byte)0x40 || nibble1 == (byte)0x50) {
				// RX or MX instruction: R1,D2(X2,B2) (with R1 may be the mask)
				int r1 = (instrByte2 & 0xF0) >> 4;
				int x2 = instrByte2 & 0x0F;
				int b2 = (this.mem[at+2] & 0xF0) >> 4;
				int ddd2 = ((this.mem[at+2] & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
				int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
				int op1 = this.gpr[r1];

				logInstruction(
						_insnformatpatterns[instr],
						_mnemonics[instr], this.mem[at], at,
						r1, op1, ddd2, x2, b2, addr2);
				
			} else if (nibble1 == (byte)0x80 || nibble1 == (byte)0xB0 || instr == 0x98 || instr == 0x90) {
				// RS instruction: R1,R3,D2(B2)
				int r1 = (instrByte2 & 0xF0) >> 4;
				int r3 = instrByte2 & 0x0F;
				int b2 = (this.mem[at+2] & 0xF0) >> 4;
				int ddd2 = ((this.mem[at+2] & 0x0F) << 8) + (this.mem[at+3] & 0x00FF);
				int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;

				logInstruction(
						_insnformatpatterns[instr],
						_mnemonics[instr], this.mem[at], at,
						r1, r3, ddd2, b2, addr2);
				
			} else if (nibble1 == (byte)0x90 || nibble1 == (byte)0xA0) {
				// SI instruction: D1(B1),I2
				int i2 = instrByte2 & 0xFF;
				int b1 = (this.mem[at+2] & 0xF0) >> 4;
				int ddd1 = ((this.mem[at+2] & 0x0F) << 8) + (this.mem[at+3] & 0xFF);
				int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;

				logInstruction(
						_insnformatpatterns[instr],
						_mnemonics[instr], this.mem[at], at,
						ddd1, b1, i2&0xFF, addr1);
				
			} else if (nibble1 == (byte)0xD0 || nibble1 == (byte)0xE0 || nibble1 == (byte)0xF0) {
				// SS instruction: D1(L1,B1),D2(L2,B2) or D1(LL,B1),D2(B2)
				int ll = (instrByte2 & 0xFF) + 1; // 1..256
				int l1 = ((instrByte2 & 0xF0) >> 4) + 1; // 1..16
				int l2 = (instrByte2 & 0x0F) + 1; // 1..16
				int b1 = (this.mem[at+2] & 0xF0) >> 4;
				int ddd1 = ((this.mem[at+2] & 0x0F) << 8) + (this.mem[at+3] & 0x00FF);
				int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
				int b2 = (this.mem[at+4] & 0xF0) >> 4;
				int ddd2 = ((this.mem[at+4] & 0x0F) << 8) + (this.mem[at+5] & 0x00FF);
				int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;

				logInstruction(
						_insnformatpatterns[instr],
						_mnemonics[instr], this.mem[at], at,
						ddd1, l1, b1, ddd2, l2, b2, ll, addr1, addr2);
				
			} else if (nibble1 == (byte)0x20 || nibble1 == (byte)0x30 ) {
				// RR floating-point instruction: R1,R2
				int r1 = (instrByte2 & 0xF0) >> 4;
				int r2 = instrByte2 & 0x0F;

				logInstruction(
						_insnformatpatterns[instr],
						_mnemonics[instr], this.mem[at], at,
						r1, 0, r2, 0, this.floatImpl.getRegVal(r1), this.floatImpl.getRegVal(r2));
				
			} else if (nibble1 == (byte)0x60 || nibble1 == (byte)0x70) {
				// RX floating point instruction: R1,D2(X2,B2)
				int r1 = (instrByte2 & 0xF0) >> 4;
				int x2 = instrByte2 & 0x0F;
				int b2 = (this.mem[at+2] & 0xF0) >> 4;
				int ddd2 = ((this.mem[at+2] & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
				int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
				
				logInstruction(
						_insnformatpatterns[instr],
						_mnemonics[instr], this.mem[at], at,
						r1, 0, ddd2, x2, b2, addr2, this.floatImpl.getRegVal(r1));
			}
		}
		
		// execute the instruction
		int res = this.instructions[instr].execute(at, instrByte2, notEX);
		
		// done if instruction was handled successfully
		if (res == 0) { return 0; }
		
		// save relevant data of unhandled privileged instruction
		byte nibble1 = (byte)(instr & 0xF0);
		if (nibble1 == (byte)0x00 || nibble1 == (byte)0x10 || nibble1 == (byte)0x20 || nibble1 == (byte)0x30) {
			// RR or MR instruction: R1,R2 (with R1 may be the mask)
			int r1 = (instrByte2 & 0xF0) >> 4;
			int r2 = instrByte2 & 0x0F;
			
			this.setPrivOps_R1_R2(at, r1, r2);
			
		} else if (nibble1 == (byte)0x40 || nibble1 == (byte)0x50 || nibble1 == (byte)0x60 || nibble1 == (byte)0x70) {
			// RX or MX instruction: R1,D2(X2,B2) (with R1 may be the mask)
			int x2 = instrByte2 & 0x0F;
			int b2 = (this.mem[at+2] & 0xF0) >> 4;
			int ddd2 = ((this.mem[at+2] & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
			int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
			
			this.setPrivOps_InsByte2_Addr(at, instrByte2, addr2);
			
		} else if (nibble1 == (byte)0x80 || nibble1 == (byte)0xB0 || instr == (byte)0x98 || instr == (byte)0x90) {
			// RS instruction: R1,R3,D2(B2)
			int b2 = (this.mem[at+2] & 0xF0) >> 4;
			int ddd2 = ((this.mem[at+2] & 0x0F) << 8) + (this.mem[at+3] & 0x00FF);
			int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
			
			this.setPrivOps_InsByte2_Addr(at, instrByte2, addr2);
			
		} else if (nibble1 == (byte)0x90 || nibble1 == (byte)0xA0) {
			// SI instruction: D1(B1),I2
			int b1 = (this.mem[at+2] & 0xF0) >> 4;
			int ddd1 = ((this.mem[at+2] & 0x0F) << 8) + (this.mem[at+3] & 0xFF);
			int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
			
			this.setPrivOps_InsByte2_Addr(at, instrByte2, addr1);
			
		} else if (nibble1 == (byte)0xD0 || nibble1 == (byte)0xE0 || nibble1 == (byte)0xF0) {
			// SS instruction: D1(L1,B1),D2(L2,B2) or D1(LL,B1),D2(B2)
			
			this.setPrivOps_InsByte2_Addr(at, 0, 0); // there are no privileged instructions with this upper nibble
		}
		
		// done
		return res;
	}
}
