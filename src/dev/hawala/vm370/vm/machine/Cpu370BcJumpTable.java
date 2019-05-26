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

import java.util.Arrays;

/**
 * First Java6-compatible optimized implementation on S/370 instructions,
 * dispatching the opcodes with switch() instead of chained if statements,
 * allowing the Java-JIT compiler to use a jump table.
 * <br/>
 * As in the basic implementation the instructions are grouped by addressing
 * modes, allowing to extracted the instruction data for a set of possible
 * instructions and then dispatched with a switch() statement for the group. 
 * 
 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2015
 */
public class Cpu370BcJumpTable extends Cpu370Bc {

	public int execInstruction(int exAt) throws PSWException {
		int at = (exAt == 0) ? this.pswInstructionAddress : (exAt & MemMask & 0xFFFFFFFE);
		byte instr = this.mem[at];
		byte nibble1 = (byte)(instr & 0xF0);
		byte instrByte2 = (exAt == 0) ? this.mem[at+1] : (byte)((exAt >> 24) & 0x000000FF);
		
		this.insnTotal++;
		
		if (nibble1 == (byte)0x00 || nibble1 == (byte)0x10) {
			// RR or MR instruction: R1,R2 (with R1 may be the mask)
			int r1 = (instrByte2 & 0xF0) >> 4;
			int r2 = instrByte2 & 0x0F;
			int op1 = this.gpr[r1];
			int op2 = this.gpr[r2];
			
			// instruction length: 1 half-word
			if (exAt == 0) {
				this.pswInstructionAddress += 2;
				this.pswInstructionLengthCode = 1;
			}
			
			// interpret the possible instructions
			if (INSNS_LOG) {
				logInstruction(
						_insnformatpatterns[(instr < 0) ? 256 + instr : instr],
						_mnemonics[(instr < 0) ? 256 + instr : instr], instr, at,
						r1, op1, r2, op2);
			}
		
			switch(instr) {
			
			case (byte)0x04: {
				// SPM - Set Program Mask
				this.pswConditionCode = (byte)((op1 >> 28) & 0x03);
				this.pswProgramMaskFixedOverflow = (op1 & 0x08000000) != 0;
				this.pswProgramMaskDecimalOverflow = (op1 & 0x04000000) != 0;
				this.pswProgramMaskExponentUnderflow = (op1 & 0x02000000) != 0;
				this.pswProgramMaskSignificance = (op1 & 0x01000000) != 0;
				return 0; // ok
			}
			
			case (byte)0x05: {
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
			}
			
			case (byte)0x06: {
				// BCTR - Branch on count register
				this.gpr[r1] = --op1;
				if (op1 != 0 && r2 != 0) {
					this.pswInstructionAddress = op2 & MemMask;
				}
				return 0; // ok
			}
			
			case (byte)0x07: {
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
			}
			
			case (byte)0x08: {
				// SSK - Set Storage Key
				// (handled as non-privileged instruction, as protection/access-tracking is unsupported)
				// (specification exceptions due to invalid bits in registers are not generated)
				int pageNo = (this.gpr[r2] & 0x00FFFFFF) >> 11;
				byte key = (byte)(this.gpr[r1] & 0x000000FE);
				this.pageKeys[pageNo] = key;
				return 0;
			}
			
			case (byte)0x09: {
				// ISK - Insert Storage Key
				// (handled as non-privileged instruction, as protection/access-tracking is unsupported)
				// (specification exceptions due to invalid bits in registers are not generated)
				int pageNo = (op2 & 0x00FFFFFF) >> 11;
				// new value for R1 in BC mode (see PrincOps-75, page 105)
				int newVal = (op1 & 0xFFFFFF00) | (this.pageKeys[pageNo] & 0xF0);
				this.gpr[r1] = newVal;
				return 0;
			}
			
			case (byte)0x0A: {
				// SVC - Supervisor call
				if (this.svcLogInterceptor != null) {
					this.svcLogInterceptor.interceptSvc(instrByte2 & 0xFF, this);
				}
//				if (INSNS_LOG) {
//					if (instrByte2 == (byte)0xCA) { this.dumpSVC202(); }
//				}
				this.pswInterruptionCode = (short)(instrByte2 & 0x00FF);
				writePswTo(this.mem, 32);
				readPswFrom(this.mem, 96);
				return 0; // ok
			}
			
			case (byte)0x0B: { /* ################## */
				// BSM - Branch and set mode
				// not a PrincOps-1975 instruction, but possibly used by
				// 31-bit capable software
				// => tell the program that this is "old" hardware 
				this.doProgramInterrupt(INTR_PGM_OPERATION_EXCEPTION);
				return 0;
			}
			
			case (byte)0x0C: { /* ################## */
				// BASSM - Branch, save, set mode
				// not a PrincOps-1975 instruction, but possibly used by
				// 31-bit capable software
				// => tell the program that this is "old" hardware relative
				this.doProgramInterrupt(INTR_PGM_OPERATION_EXCEPTION);
				return 0;
			}
			
			case (byte)0x0D: {
				// BASM - Branch and save register
				// not a PrincOps-1975 instruction, but possibly used by
				// 31-bit capable software
				// => tell the program that this is "old" hardware 
				this.doProgramInterrupt(INTR_PGM_OPERATION_EXCEPTION);
				return 0;
			}
			
			case (byte)0x0E: {
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
			}
			
			case (byte)0x0F: {
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
				
				this.gpr[r1] = addr1;
				this.gpr[r1+1] = (this.gpr[r1+1] & 0xFF000000) | (len1 & 0x00FFFFFF);
				this.gpr[r2] = addr2;
				this.gpr[r2+1] = (pad << 24) | (len2 & 0x00FFFFFF);
				this.pswConditionCode = newCC;
				return 0; // ok
			}
			
			case (byte)0x10: {
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
			}
			
			case (byte)0x11: {
				// LNR - Load negative
				this.gpr[r1] = (op2 > 0) ? - op2 : op2;
				this.pswConditionCode = (op2 == 0) ? CC0 : CC1;
				return 0; // ok
			} 
			
			case (byte)0x12: {
				// LTR - Load and test register
				this.gpr[r1] = op2;
				this.pswConditionCode = (op2 < 0) ? CC1 : (op2 > 0) ? CC2 : CC0;
				return 0; // ok
			}
			
			case (byte)0x13: {
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
			}
			
			case (byte)0x14: {
				// NR - And registers
				int res = op1 & op2;
				this.gpr[r1] = res;
				this.pswConditionCode = (res == 0) ? CC0 : CC1;
				return 0; // ok
			}
			
			case (byte)0x15: {
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
			}
			
			case (byte)0x16: {
				// OR - Or registers
				int res = op1 | op2;
				this.gpr[r1] = res;
				this.pswConditionCode = (res == 0) ? CC0 : CC1;
				return 0; // ok
			}
			
			case (byte)0x17: {
				// XR -Exclusive or registers
				int res = op1 ^ op2;
				this.gpr[r1] = res;
				this.pswConditionCode = (res == 0) ? CC0 : CC1;
				return 0; // ok
			}
			
			case (byte)0x18: {
				// LR - Load register
				this.gpr[r1] = op2;
				return 0; // ok
			}
			
			case (byte)0x19: {
				// CR - Compare registers
				this.pswConditionCode = (op1 < op2) ? CC1 : (op1 > op2) ? CC2 : CC0;
				return 0; // ok
			}
			
			case (byte)0x1A: {
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
			}
			
			case (byte)0x1B: {
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
			}
			
			case (byte)0x1C: {
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
			}
			
			case (byte)0x1D: {
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
			}
			
			case (byte)0x1E: {
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
			}
			
			case (byte)0x1F: {
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
			}
				
			default:
				// well, this is really an unknown instruction...
				if (INSNS_LOG) { logWarning(" ## unimplemented instruction (opcode: 0x%02X) ##", instr); }
				// remember instruction operands in case it is a privileged instruction
				this.setPrivOps_R1_R2(at, r1, r2);
				return EXECSTATE_UNKNOWN_INSTRUCTION;
					
			}
			
		} else if (nibble1 == (byte)0x40 || nibble1 == (byte)0x50) {
			// RX or MX instruction: R1,D2(X2,B2) (with R1 may be the mask)
			int r1 = (instrByte2 & 0xF0) >> 4;
			int x2 = instrByte2 & 0x0F;
			int b2 = (this.mem[at+2] & 0xF0) >> 4;
			int ddd2 = ((this.mem[at+2] & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
			int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
			int op1 = this.gpr[r1];
			
			// instruction length: 2 half-words
			if (exAt == 0) {
				this.pswInstructionAddress += 4;
				this.pswInstructionLengthCode = 2;
			}
			
			if (INSNS_LOG) {
				logInstruction(
						_insnformatpatterns[(instr < 0) ? 256 + instr : instr],
						_mnemonics[(instr < 0) ? 256 + instr : instr], instr, at,
						r1, op1, ddd2, x2, b2, addr2);
			}
			
			// interpret the possible instructions
			switch(instr) {
			
			case (byte)0x40: {
				// STH - Store halfword
				this.mem[addr2++] = (byte)((op1 & 0xFF00) >> 8);
				this.mem[addr2] = (byte)(op1 & 0xFF);
				return 0; // ok
			}
			
			case (byte)0x41: {
				// LA - Load address
				this.gpr[r1] = addr2;
				return 0; // ok
			}
			
			case (byte)0x42: {
				// STC - Store character
				this.mem[addr2] = (byte)(op1 & 0xFF);
				return 0; // ok
			}
			
			case (byte)0x43: {
				// IC - Insert character
				this.gpr[r1] = (op1 & 0xFFFFFF00) | (this.mem[addr2] & 0xFF);
				return 0; // ok
			}
			
			case (byte)0x44: {
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
			}
			
			case (byte)0x45: {
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
			}
			
			case (byte)0x46: {
				// BCT - Branch on count
				this.gpr[r1] = --op1;
				if (op1 != 0) {
					this.pswInstructionAddress = addr2;
				}
				return 0; // ok
			}
			
			case (byte)0x47: {
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
			}
			
			case (byte)0x48: {
				// LH - Load halfword
				short op2 = (short)(((this.mem[addr2]&0xFF)<<8) | (this.mem[addr2+1]&0xFF));
				this.gpr[r1] = op2;
				return 0; // ok
			}
			
			case (byte)0x49: {
				// CH - Compare halfword
				short op2 = (short)(((this.mem[addr2]&0xFF)<<8) | (this.mem[addr2+1]&0xFF));
				this.pswConditionCode = (op1 < op2) ? CC1 : (op1 > op2) ? CC2 : CC0;
				return 0; // ok
			}
			
			case (byte)0x4A: {
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
			}
			
			case (byte)0x4B: {
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
			}
			
			case (byte)0x4C: {
				// MH - Multiply halfword
				short op2 = (short)(((this.mem[addr2]&0xFF)<<8) | (this.mem[addr2+1]&0xFF));
				long result = (long)op1 * (long)op2;
				this.gpr[r1] = (int)(result & 0xFFFFFFFFL); // PrincOps-75, p. 136: "The bits to the left of the 32 low-order bits are not tested for significance; no overflow indication is given."
				return 0; // ok
			}
			
			case (byte)0x4D: {
				// BAS - Branch and save
				// not a PrincOps-1975 instruction, but possibly used by
				// 31-bit capable software
				// => tell the program that this is "old" hardware 
				this.doProgramInterrupt(INTR_PGM_OPERATION_EXCEPTION);
				return 0;
			}
			
			case (byte)0x4E: {
				// CVD - Convert to decimal
				this.decimalImpl.instrConvertToPacked(this.gpr[r1], this.mem, addr2);
				return 0; // ok
			}
			
			case (byte)0x4F: {
				// CVB - Convert to binary
				int[] result = this.decimalImpl.instrConvertToBinary(this.gpr[r1], this.mem, addr2);
				this.gpr[r1] = result[1];
				if (result[0] > 0) {
					this.doProgramInterrupt((short)(result[0] >> 16));
				}
				return 0; // ok
			}
			
			case (byte)0x50: {
				// ST - Store
				this.mem[addr2++] = (byte)((op1 >> 24) & 0xFF);
				this.mem[addr2++] = (byte)((op1 >> 16) & 0xFF);
				this.mem[addr2++] = (byte)((op1 >> 8) & 0xFF);
				this.mem[addr2] = (byte)(op1 & 0xFF);
				return 0; // ok
			}
			
			case (byte)0x54: {
				// N - And
				int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
				int res = op1 & op2;
				this.gpr[r1] = res;
				this.pswConditionCode= (res == 0) ? CC0 : CC1;
				return 0;
			}
			
			case (byte)0x55: {
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
			}
			
			case (byte)0x56: {
				// O - Or
				int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
				int res = op1 | op2;
				this.gpr[r1] = res;
				this.pswConditionCode = (res == 0) ? CC0 : CC1;
				return 0;
			}
			
			case (byte)0x57: {
				// X - Exclusive or
				int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
				int res = op1 ^ op2;
				this.gpr[r1] = res;
				this.pswConditionCode= (res == 0) ? CC0 : CC1;
				return 0;
			}
			
			case (byte)0x59: {
				// C - Compare
				int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
				this.pswConditionCode = (op1 < op2) ? CC1 : (op1 > op2) ? CC2 : CC0;
				return 0; // ok
			}
			
			case (byte)0x58: {
				// L - Load
				int op2 = ((this.mem[addr2]&0xFF)<<24) | ((this.mem[addr2+1]&0xFF)<<16) | ((this.mem[addr2+2]&0xFF)<<8) | (this.mem[addr2+3]&0xFF);
				this.gpr[r1] = op2;
				return 0; // ok
			}
			
			case (byte)0x5A: {
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
			}
			
			case (byte)0x5B: {
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
			}
			
			case (byte)0x5C: {
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
			}
			
			case (byte)0x5D: {
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
			}
			
			case (byte)0x5E: {
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
			}
			
			case (byte)0x5F: {
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
			}
				
			default:
				// well, this is really an unknown instruction...
				if (INSNS_LOG) { logWarning(" ## unimplemented instruction (opcode: 0x%02X) ##", instr); }
				// remember instruction operands in case it is a privileged instruction
				this.setPrivOps_InsByte2_Addr(at, instrByte2, addr2);
				return EXECSTATE_UNKNOWN_INSTRUCTION;
					
			}
			
		} else if (nibble1 == (byte)0x80 || nibble1 == (byte)0xB0 || instr == (byte)0x98 || instr == (byte)0x90) {
			// RS instruction: R1,R3,D2(B2)
			int r1 = (instrByte2 & 0xF0) >> 4;
			int r3 = instrByte2 & 0x0F;
			int b2 = (this.mem[at+2] & 0xF0) >> 4;
			int ddd2 = ((this.mem[at+2] & 0x0F) << 8) + (this.mem[at+3] & 0x00FF);
			int addr2 = (ddd2 + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
			
			// instruction length: 2 half-words
			if (exAt == 0) {
				this.pswInstructionAddress += 4;
				this.pswInstructionLengthCode = 2;
			}
			
			if (INSNS_LOG) {
				logInstruction(
						_insnformatpatterns[(instr < 0) ? 256 + instr : instr],
						_mnemonics[(instr < 0) ? 256 + instr : instr], instr, at,
						r1, r3, ddd2, b2, addr2);
			}
			
			// interpret the possible instructions
			switch(instr) {
			
			case (byte)0x80: {
				// SSM   - Set Systen Mask (implemented as unprivileged)
				byte b = this.mem[addr2];
				this.setIntrMask(b);
				return 0;
			}
			
			case (byte)0x82: {
				 // LPSW - Load PSW (implemented as unprivileged)
				// specification exception if addr2 is not on a doubleword boundary
				if ((addr2 & 0x07) != 0) {
					this.doProgramInterrupt(INTR_PGM_SPECIFICATION_EXCEPTION);
					return 0; // ok
				}
				this.readPswFrom(this.mem, addr2);
//				if (INSNS_LOG) { this.checkSVC202end(); }
				return 0;
			}
			
			case (byte)0x83: {
				// DIAG - Diagnose
				// return the original 4 instruction bytes to the privilege level
				this.setPrivOps_InsByte2_Addr(at, instrByte2, addr2);
				return ((this.mem[at] & 0xFF) << 24) 
					 | ((this.mem[at+1] & 0xFF) << 16)
					 | ((this.mem[at+2] & 0xFF) << 8)
					 | (this.mem[at+3] & 0xFF);
			}
			
			case (byte)0x84: {
				// WRD   - Write Direct
				this.setPrivOps_InsByte2_Addr(at, instrByte2, addr2);
				return (instr & 0xFF);
			}
			
			case (byte)0x85: {
				// RDD   - Read Direct
				this.setPrivOps_InsByte2_Addr(at, instrByte2, addr2);
				return (instr & 0xFF);
			}
			
			case (byte)0x86: {
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
			}
			
			case (byte)0x87: {
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
			}
			
			case (byte)0x88: {
				// SRL - Shift right single logical
				int shiftBy = addr2 & 0x3F; // lower 6 bits
				this.gpr[r1] = (shiftBy > 31) ? 0 : ((this.gpr[r1] >> shiftBy) & ShiftRightMasks[shiftBy]);
				return 0; // ok
			}
			
			case (byte)0x89: {
				// SLL - Shift left single logical
				int shiftBy = addr2 & 0x3F; // lower 6 bits
				this.gpr[r1] = (shiftBy > 31) ? 0 : this.gpr[r1] << shiftBy;
				return 0; // ok
			}
			
			case (byte)0x8A: {
				// SRA - Shift right single
				int shiftBy = addr2 & 0x3F; // lower 6 bits
				int val = this.gpr[r1] >> shiftBy;
				this.gpr[r1] = val;
				this.pswConditionCode = (val < 0) ? CC1 : (val > 0) ? CC2 : CC0;
				return 0; // ok
			}
			
			case (byte)0x8B: {
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
			}
			
			case (byte)0x8C: {
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
			}
			
			case (byte)0x8D: {
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
			}
			
			case (byte)0x8E: {
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
			}
			
			case (byte)0x8F: {
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
			}
			
			case (byte)0x90: {
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
			}
			
			case (byte)0x98: {
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
			}
			
			case (byte)0xB1: {
				// LRA   - Load Real Address
				this.setPrivOps_InsByte2_Addr(at, instrByte2, addr2);
				return (instr & 0xFF);
			}
			
			case (byte)0xB2: {
				// privileged instruction with 2-byte opcode starting with 0xB2
				// *some* of these instructions are implemented as non-privileged
				// if they involve only ressources inside this CPU implementation
				// and they are harmless enough in the context of this CPU implementation
				if (instrByte2 == (byte)0x0B) {
					// IPK - Insert PSW Key
					int r2 = this.gpr[2];
					r2 &= 0x0FFFFFFFF;
					r2 |= this.pswProtectionKey << 28;
					this.gpr[2] = r2;
					return 0;
				} else if (instrByte2 == (byte)0x13) {
					// RRB - Reset Reference Bit
					// (memory usage tracking is not available)
					this.pswConditionCode = CC2; // Reference bit one, change bit zero
					return 0;
				} else if (instrByte2 == (byte)0x0A) {
					// SKPA - Set PSW Key From Address
					this.pswProtectionKey = (short)((addr2 >> 4) & 0x0F); // bits 24-27 of the second operand
					return 0;
				}
				// all other privileged instruction must be handled by the invoker
				// (or terminate execution if not implemented)
				this.setPrivOps_InsByte2_Addr(at, instrByte2, addr2);
				return ((instr & 0xFF) << 8) | (instrByte2 & 0xFF);
			}
			
			case (byte)0xB6: {
				// STCTL - Store Control
				this.setPrivOps_InsByte2_Addr(at, instrByte2, addr2);
				return (instr & 0xFF);
			}
			
			case (byte)0xB7: {
				// LCTL  - Load Control
				this.setPrivOps_InsByte2_Addr(at, instrByte2, addr2);
				return (instr & 0xFF);
			}
			
			case (byte)0xBA: {
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
			}
			
			case (byte)0xBB: {
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
			}
			
			case (byte)0xBD: {
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
			}
			
			case (byte)0xBE: {
				// STCM - Store characters under mask
				int val = this.gpr[r1];
				if ((r3 & 0x08) != 0) { this.mem[addr2++] = (byte)((val >> 24) & 0xFF); }
				if ((r3 & 0x04) != 0) { this.mem[addr2++] = (byte)((val >> 16) & 0xFF); }
				if ((r3 & 0x02) != 0) { this.mem[addr2++] = (byte)((val >> 8) & 0xFF); }
				if ((r3 & 0x01) != 0) { this.mem[addr2++] = (byte)(val & 0xFF); }
				return 0; // ok
			}
			
			case (byte)0xBF: {
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
			}
			
			default:
				// well, this is really an unknown instruction...
				if (INSNS_LOG) { logWarning(" ## unimplemented instruction (opcode: 0x%02X) ##", instr); }
				// remember instruction operands in case it is a privileged instruction
				this.setPrivOps_InsByte2_Addr(at, instrByte2, addr2);
				return EXECSTATE_UNKNOWN_INSTRUCTION;
					
			}
			
		} else if (nibble1 == (byte)0x90 || nibble1 == (byte)0xA0) {
			// SI instruction: D1(B1),I2
			int i2 = instrByte2 & 0xFF;
			int b1 = (this.mem[at+2] & 0xF0) >> 4;
			int ddd1 = ((this.mem[at+2] & 0x0F) << 8) + (this.mem[at+3] & 0xFF);
			int addr1 = (ddd1 + ((b1 == 0) ? 0 : this.gpr[b1])) & MemMask;
			
			// instruction length: 2 half-words
			if (exAt == 0) {
				this.pswInstructionAddress += 4;
				this.pswInstructionLengthCode = 2;
			}
			
			if (INSNS_LOG) {
				logInstruction(
						_insnformatpatterns[(instr < 0) ? 256 + instr : instr],
						_mnemonics[(instr < 0) ? 256 + instr : instr], instr, at,
						ddd1, b1, i2&0xFF, addr1);
			}
			
			// interpret the possible instructions
			switch(instr) {
			
			case (byte)0x91: {
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
			}
			
			case (byte)0x92: {
				// MVI - Move immediate
				this.mem[addr1] = (byte)i2;
				return 0; // ok
			}
			
			case (byte)0x93: {
				// TS - Test and set
				// WARNING: no S/370-CPU sync performed/needed, since only one single CPU is emulated 
				this.pswConditionCode = ((this.mem[addr1] & (byte)0x80) != 0) ? CC1 : CC0;
				this.mem[addr1] = (byte)0xFF;
				return 0; // ok
			}
			
			case (byte)0x94: {
				// NI - And immediate
				int op1 = this.mem[addr1] & 0xFF;
				int res = (op1 & i2)  & 0xFF;
				this.mem[addr1] = (byte)res;
				this.pswConditionCode = (res == 0) ? CC0 : CC1;
				return 0; // ok
			}
			
			case (byte)0x95: {
				// CLI - Compare logical immediate
				int op1 = this.mem[addr1] & 0xFF;
				if (op1 == i2) {
					this.pswConditionCode = CC0;
				} else {
					this.pswConditionCode = (op1 < i2) ? CC1 : CC2;
				}
				return 0; // ok
			}
			
			case (byte)0x96: {
				// OI - Or immediate
				int op1 = this.mem[addr1] & 0xFF;
				int res = (op1 | i2)  & 0xFF;
				this.mem[addr1] = (byte)res;
				this.pswConditionCode = (res == 0) ? CC0 : CC1;
				return 0; // ok
			}
			
			case (byte)0x97: {
				// XI - Exclusive or immediate
				int op1 = this.mem[addr1] & 0xFF;
				int res = (op1 ^ i2)  & 0xFF;
				this.mem[addr1] = (byte)res;
				this.pswConditionCode = (res == 0) ? CC0 : CC1;
				return 0; // ok
			}
			
			case (byte)0x9C:
			case (byte)0x9D:
			case (byte)0x9E:
			case (byte)0x9F: {
				// I/O instruction
				this.setPrivOps_InsByte2_Addr(at, instrByte2, addr1);
				return ((instr & 0xFF) << 8) | (instrByte2 & 0xFF);
			}
			
			case (byte)0xAC: {
				// STNSM - Store Then AND System Mask
				this.mem[addr1] = this.pswIntrMaskByte;
				byte newMask = (byte)(this.pswIntrMaskByte & i2);
				this.setIntrMask(newMask);
				return 0; // ok
			}
			
			case (byte)0xAD: {
				// STOSM - Store Then OR System Mask
				this.mem[addr1] = this.pswIntrMaskByte;
				byte newMask = (byte)(this.pswIntrMaskByte | i2);
				this.setIntrMask(newMask);
				return 0; // ok
			}
			
			case (byte)0xAE: {
				// SIGP  - Signal Processor
				this.setPrivOps_InsByte2_Addr(at, instrByte2, addr1);
				return (instr & 0xFF);
			}
				
			default:
				// well, this is really an unknown instruction...
				if (INSNS_LOG) { logWarning(" ## unimplemented instruction (opcode: 0x%02X) ##", instr); }
				// remember instruction operands in case it is a privileged instruction
				this.setPrivOps_InsByte2_Addr(at, instrByte2, addr1);
				return EXECSTATE_UNKNOWN_INSTRUCTION;
					
			}
			
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
			
			// instruction length: 3 half-words
			if (exAt == 0) {
				this.pswInstructionAddress += 6;
				this.pswInstructionLengthCode = 3;
			}
			
			if (INSNS_LOG) {
				logInstruction(
						_insnformatpatterns[(instr < 0) ? 256 + instr : instr],
						_mnemonics[(instr < 0) ? 256 + instr : instr], instr, at,
						ddd1, l1, b1, ddd2, l2, b2, ll, addr1, addr2);
			}
			
			// interpret the possible instructions
			switch(instr) {
			
			case (byte)0xD1: {
				// MVN - Move numerics
				while(ll-- > 0) {
					byte b = (byte)(this.mem[addr1] & (byte)0xF0);
					this.mem[addr1++] = (byte)((this.mem[addr2++] & 0x0F) | b); 
				}
				return 0; // ok
			}
			
			case (byte)0xD2: {
				// MVC - Move characters
				while(ll-- > 0) {
					this.mem[addr1++] = this.mem[addr2++]; 
				}
				return 0; // ok
			}
			
			case (byte)0xD3: {
				// MVZ - Move zones
				while(ll-- > 0) {
					byte b = (byte)(this.mem[addr1] & (byte)0x0F);
					this.mem[addr1++] = (byte)((this.mem[addr2++] & 0xF0) | b); 
				}
				return 0; // ok
			}
			
			case (byte)0xD4: {
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
			}
			
			case (byte)0xD5: {
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
			}
			
			case (byte)0xD6:{
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
			}
			
			case (byte)0xD7: {
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
			}
			
			case (byte)0xDC: {
				// TR - Translate
				for (int i = 0; i < ll; i++) {
					int a1 = (addr1 + i) & MemMask;
					int src = this.mem[a1] & 0xFF;
					this.mem[a1] = this.mem[(addr2 + src) & MemMask];
				}
				return 0;
			}
			
			case (byte)0xDD: {
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
			}
			
			case (byte)0xDE: {
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
			}
			
			case (byte)0xDF: {
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
			}
			
			case (byte)0xF0:  {
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
			}
			
			case (byte)0xF1: {
				// MVO - Move with offset
				this.decimalImpl.instrMoveWithOffset(
						this.mem,
						addr1, l1,
						addr2, l2);
				return 0; // ok
			}
			
			case (byte)0xF2: {
				// PACK - Pack
				this.decimalImpl.instrPack(
						this.mem,
						addr1, l1,
						addr2, l2);
				return 0; // ok
			}
			
			case (byte)0xF3: {
				// UNPK - Unpack
				this.decimalImpl.instrUnpack(
						this.mem,
						addr1, l1,
						addr2, l2);
				return 0; // ok
			}
			
			case (byte)0xF8: {
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
			}
			
			case (byte)0xF9: {
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
			}
			
			case (byte)0xFA: {
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
			}
			
			case (byte)0xFB: {
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
			}
			
			case (byte)0xFC: {
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
			}
			
			case (byte)0xFD: {
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
			}
			
			default:
				// well, this is really an unknown instruction...
				if (INSNS_LOG) { logWarning(" ## unimplemented instruction (opcode: 0x%02X) ##", instr); }
				// remember instruction operands in case it is a privileged instruction
				this.setPrivOps_InsByte2_Addr(at, 0, 0); // there are no privileged instructions with these upper nibbles
				return EXECSTATE_UNKNOWN_INSTRUCTION;
					
			}
			
		} else if (nibble1 == (byte)0x20 || nibble1 == (byte)0x30 ) {
			// RR floating-point instruction: R1,R2
			int r1 = (instrByte2 & 0xF0) >> 4;
			int r2 = instrByte2 & 0x0F;
			
			// instruction length: 1 half-word
			if (exAt == 0) {
				this.pswInstructionAddress += 2;
				this.pswInstructionLengthCode = 1;
			}
			
			if (INSNS_LOG) {
				logInstruction(
						_insnformatpatterns[(instr < 0) ? 256 + instr : instr],
						_mnemonics[(instr < 0) ? 256 + instr : instr], instr, at,
						r1, 0, r2, 0, this.floatImpl.getRegVal(r1), this.floatImpl.getRegVal(r2));
				/*
				this.logLine(
						_insnformatpatterns[(instr < 0) ? 256 + instr : instr],
						_mnemonics[(instr < 0) ? 256 + instr : instr], instr, at,
						r1, 0, r2, 0, this.floatImpl.getRegVal(r1), this.floatImpl.getRegVal(r2));
				this.logLine("registers before instruction:");
				this.floatImpl.dumpRegs();
				*/
			}
			
			int res = -1;
			try {
				res = this.floatImpl.executeRRInstruction(
						this.pswConditionCode, 
						this.mem,
						instr, r1, r2,
						this.pswProgramMaskExponentUnderflow, this.pswProgramMaskSignificance);
				/*
				this.logLine("registers after instruction:");
				this.floatImpl.dumpRegs();
				*/
			} catch (ArithmeticException e) {
				this.dumpLastInstructions(256);
				this.doProgramInterrupt(INTR_PGM_FLOAT_DIVIDE);
				return 0; // ok
			}
			if (res == -1) {
				// unknown instruction
				// remember instruction operands in case it is a privileged instruction
				this.setPrivOps_R1_R2(at, r1, r2);
				return -1;
			}
			if (res > CC3) {
				this.doProgramInterrupt((short)(res >> 16));
				return 0; // ok
			}
			this.pswConditionCode = (byte)res;
			return 0; // ok
			
		} else if (nibble1 == (byte)0x60 || nibble1 == (byte)0x70) {
			// RX floating point instruction: R1,D2(X2,B2)
			int r1 = (instrByte2 & 0xF0) >> 4;
			int x2 = instrByte2 & 0x0F;
			int b2 = (this.mem[at+2] & 0xF0) >> 4;
			int ddd2 = ((this.mem[at+2] & 0x0F) << 8) | (this.mem[at+3] & 0x00FF);
			int addr2 = (ddd2 + ((x2 == 0) ? 0 : this.gpr[x2]) + ((b2 == 0) ? 0 : this.gpr[b2])) & MemMask;
			
			// instruction length: 2 half-words
			if (exAt == 0) {
				this.pswInstructionAddress += 4;
				this.pswInstructionLengthCode = 2;
			}
			
			if (INSNS_LOG) {
				logInstruction(
						_insnformatpatterns[(instr < 0) ? 256 + instr : instr],
						_mnemonics[(instr < 0) ? 256 + instr : instr], instr, at,
						r1, 0, ddd2, x2, b2, addr2, this.floatImpl.getRegVal(r1));
				/*
				this.logLine(
						_insnformatpatterns[(instr < 0) ? 256 + instr : instr],
						_mnemonics[(instr < 0) ? 256 + instr : instr], instr, at,
						r1, 0, ddd2, x2, b2, addr2, this.floatImpl.getRegVal(r1));
				this.logLine("registers before instruction:");
				this.floatImpl.dumpRegs();
				*/
			}
			
			int res = -1;
			try {
				res = this.floatImpl.executeRXInstruction(
						this.pswConditionCode, 
						this.mem,
						instr, r1, addr2,
						this.pswProgramMaskExponentUnderflow, this.pswProgramMaskSignificance);
				/*
				this.logLine("registers after instruction:");
				this.floatImpl.dumpRegs();
				*/
			} catch (ArithmeticException e) {
				this.dumpLastInstructions(256);
				this.doProgramInterrupt(INTR_PGM_FLOAT_DIVIDE);
				return 0; // ok
			}
			if (res == -1) {
				// unknown instruction
				// remember instruction operands in case it is a privileged instruction
				this.setPrivOps_InsByte2_Addr(at, instrByte2, addr2);
				return -1;
			}
			if (res > CC3) {
				this.doProgramInterrupt((short)(res >> 16));
				return 0; // ok
			}
			this.pswConditionCode = (byte)res;
			return 0; // ok
			
		} else {
			this.setPrivOps_InsByte2_Addr(at, 0, 0);
			if (INSNS_LOG) {
				// addressing mode is unknown or not identifiable through the upper opcode nibble 
				logInstruction(
						_NN,
						_mnemonics[(instr < 0) ? 256 + instr : instr], instr, at);
			}
			if (INSNS_LOG) { logWarning(" ## unimplemented instruction (opcode: 0x%02X) ##", instr); }
			return EXECSTATE_UNKNOWN_INSTRUCTION;
		}
	}
}
