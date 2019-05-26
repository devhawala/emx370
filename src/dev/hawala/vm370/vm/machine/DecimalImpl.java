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

import java.math.BigDecimal;

/**
 * Implementation of decimal instruction functionality.
 * 
 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2015
 */
public class DecimalImpl {
	
	protected final int DATA_EXCEPTION = 0x00070000;
	protected final int DECIMAL_DIVIDE_EXCEPTION = 0x000B0000;
	protected final int SPECIFICATION_EXCEPTION = 0x00060000;
	protected final int FIXEDPOINT_DIVICE_EXCEPTION = 0x00090000;
	
	protected int ccFromCompare(int result) {
		if (result < 0) { return 1; } // result less than zero, first operand low
		if (result > 0) { return 2; } // result greater than zero, first operand high
		return 0; // result zero, operands equal
	}
	
	/*
	** Decimal instructions
	** ====================
	** 
	** Instruction implementations return an integer with:
	**  high 16 bits: interruption code for a program interruption (0x0000 for: no interruption)
	**  low 16 bits : PSW condition code (0..3)
	*/
	
	public int instrAdd(byte oldCC, byte[] mem, int addr1, int len1, int addr2, int len2) {
		BigDecimal op1 = this.packedToBig(mem, addr1, len1);
		if (op1 == null) { return DATA_EXCEPTION | oldCC; }
		BigDecimal op2 = this.packedToBig(mem, addr2, len2);
		if (op2 == null) { return DATA_EXCEPTION | oldCC; }
		
		BigDecimal res = op1.add(op2);
		boolean outOfSpace = this.bigToPacked(res, mem, addr1, len1);
		if (outOfSpace) { return 3; } // overflow, let the invoker decide if program interruption must occur
		return this.ccFromCompare(res.signum());
	}
	
	public int instrCompare(byte oldCC, byte[] mem, int addr1, int len1, int addr2, int len2) {
		BigDecimal op1 = this.packedToBig(mem, addr1, len1);
		if (op1 == null) { return DATA_EXCEPTION | oldCC; }
		BigDecimal op2 = this.packedToBig(mem, addr2, len2);
		if (op2 == null) { return DATA_EXCEPTION | oldCC; }
		
		return this.ccFromCompare(op1.compareTo(op2));
	}
	
	public int instrDivide(byte oldCC, byte[] mem, int addr1, int len1, int addr2, int len2) {
		if (len2 > 8) { return SPECIFICATION_EXCEPTION | oldCC; } // divisor may have up to 15 digits and sign
		if (len2 >= len1) { return SPECIFICATION_EXCEPTION | oldCC; } // divisor length must be less than dividend length
		
		BigDecimal op1 = this.packedToBig(mem, addr1, len1);
		if (op1 == null) { return DATA_EXCEPTION | oldCC; }
		BigDecimal op2 = this.packedToBig(mem, addr2, len2);
		if (op2 == null) { return DATA_EXCEPTION | oldCC; }
		if (op2.signum() == 0) { return DECIMAL_DIVIDE_EXCEPTION | oldCC; } // attempt to divide by zero
		
		BigDecimal[] res = op1.divideAndRemainder(op2);
		
		byte origSignByte = mem[addr1 + len1 - 1];
		int quotLen = len1 - len2;
		boolean outOfSpace = this.bigToPacked(res[0], mem, addr1, quotLen);
		if (outOfSpace) {  // quotient too large to be represented
			this.bigToPacked(op1, mem, addr1, len1); // restore op1 ("The operation is suppressed ... The divisor and dividend remain unchanged in theier storage locations")
			mem[addr1 + len1 - 1] = origSignByte;
			return DECIMAL_DIVIDE_EXCEPTION | oldCC;
		}
		this.bigToPacked(res[1], mem, addr1 + quotLen, len2);
		
		return oldCC; // condition code remains unchanged
	}
	
	protected final byte ED_DigitSelector = (byte)0x20;
	protected final byte ED_SignificanceStarter= (byte)0x21;
	protected final byte ED_FieldSeparator = (byte)0x22;
	protected final byte PACKEDZERO = (byte)0xF0;
	
	public int[] instrEditAndMark(byte oldCC, byte[] mem, int addr1, int len1, int addr2, int len2, int oldR1) {
		boolean isSignificant = false;  // significance indicator (state machine)
		byte fillByte = mem[addr1];     // first byte is the fillByte to use
		int[] result = {DATA_EXCEPTION | oldCC, oldR1}; // result of the method: int[2] = { exception?/newCC, newR1 }
		int nextSrcPos = addr2;         // position of next byte to read from op2   
		byte currSrcByte = (byte)0;     // last byte extracted from op2 
		boolean rightNibble = false;    // is the next digit to consume the right digit in currSrcByte?
		boolean lastFieldIsZero = true; // true if: last field edited has zero length or all used digits were zero
		
		// interpret all bytes in the pattern with the finite state machine (skipping the fill byte at the beginning)
		for (int i = addr1 + 1; i < addr1 + len1; i++) {
			byte patByte = mem[i];
			if (patByte == ED_DigitSelector || patByte == ED_SignificanceStarter) {
				// get the next digit to insert and the possibly positive sign state of a new packed byte
				byte srcDigit;
				boolean rightBitsArePlus = false;
				if (rightNibble) {
					srcDigit = (byte)(currSrcByte & 0x0F);
					rightNibble = false;
				} else {
					currSrcByte = mem[nextSrcPos++];
					rightNibble = true;
					srcDigit = (byte)((currSrcByte >> 4) & 0xF);
					int rightBits = currSrcByte & 0x0F;
					rightBitsArePlus = (rightBits > 9 && rightBits != 11 && rightBits != 13); // sign bits but not negative
				}
				if (srcDigit > (byte)9) {
					// digit contains sign -> data exception
					result[1] |= (oldR1 & 0xFF000000);
					return result;
				}
				if (srcDigit > 0) { lastFieldIsZero = false; }
				srcDigit = (byte)(srcDigit | 0xF0); // expand digit to zone format
				
				// check the action to do with the finite state machine
				if (mem[i] == ED_DigitSelector) {
					if (isSignificant) {
						mem[i] = srcDigit;
						isSignificant = !rightBitsArePlus;
					} else {
						if (srcDigit == PACKEDZERO) {
							mem[i] = fillByte;
						} else {
							mem[i] = srcDigit;
							isSignificant = !rightBitsArePlus;
							result[1] = i;
						}
					}
				} else { // patByte == ED_SignificanceStarter
					if (isSignificant) {
						mem[i] = srcDigit;
						isSignificant = !rightBitsArePlus;
					} else {
						if (srcDigit == PACKEDZERO) {
							mem[i] = fillByte;
							isSignificant = !rightBitsArePlus;
						} else {
							mem[i] = srcDigit;
							isSignificant = !rightBitsArePlus;
							result[1] = i;
						}
					}
				}
			} else if (patByte == ED_FieldSeparator) {
				mem[i] = fillByte;
				isSignificant = false;
				lastFieldIsZero = true;
				rightNibble = false;
			} else {
				// message byte
				if (isSignificant) {
					mem[i] = patByte;
				} else {
					mem[i] = fillByte;
				}
			}
		}
		
		// done: set new CC, set bits 0..7 of new R1 to the old value and we're done
		result[0] = (lastFieldIsZero) ? (byte)0 : (isSignificant) ? (byte)1 : (byte)2;
		result[1] |= (oldR1 & 0xFF000000);
		return result;
	}
	
	public int instrMultiply(byte oldCC, byte[] mem, int addr1, int len1, int addr2, int len2) {
		if (len2 > 8) { return SPECIFICATION_EXCEPTION | oldCC; } // multiplier may have up to 15 digits and sign
		if (len2 >= len1) { return SPECIFICATION_EXCEPTION | oldCC; } // multiplier length must be less than multiplicand length
		
		BigDecimal op1 = this.packedToBig(mem, addr1, len1);
		if (op1 == null) { return DATA_EXCEPTION | oldCC; }
		BigDecimal op2 = this.packedToBig(mem, addr2, len2);
		if (op2 == null) { return DATA_EXCEPTION | oldCC; }
		
		// the multiplicand must have at least as many bytes of leftmost zeros as the number of bytes in the multiplier
		int op1NonZeroBytes = (op1.precision() / 2) + 1;
		if ((len1 - op1NonZeroBytes) < len2) { return SPECIFICATION_EXCEPTION | oldCC; }
		
		BigDecimal res = op1.multiply(op2);
		this.bigToPacked(res, mem, addr1, len1);
		
		return oldCC; // condition code remains unchanged
	}
	
	public int instrShiftAndRound(byte oldCC, byte[] mem, int addr1, int len1, int addr2, int roundDigit) {
		int lastNibble = mem[addr1 + len1 - 1] & 0x0F; // sign bits
		int digits = (len1 << 1) - 1; // number of digits in op1 (2 digits per byte less the sign nibble)		
		int shiftBy = ((addr2 & 0x20) != 0) ? addr2 | 0xFFFFFFE0 : addr2 & 0x1F; // 5 significant bits with sign extension
		int leadingZeroes = 0;
		boolean isNegative;
		boolean isOverflow = false;
		
		// get and check the sign
		if (lastNibble == 10 || lastNibble == 12 || lastNibble == 14 || lastNibble == 15) {
			isNegative = false;
			lastNibble = 12; // preferred positive
		} else if (lastNibble == 11 || lastNibble == 13) {
			isNegative = true;
			lastNibble = 13; // preferred negative
		} else {
			// invalid bits in the sign position
			return DATA_EXCEPTION | oldCC;
		}
		
		// check for invalid bytes in operands
		if (!this.validPacked(mem, addr1, len1)) { return DATA_EXCEPTION | oldCC; }
		if (roundDigit > 0x09) { return DATA_EXCEPTION | oldCC; }
		
		// count leading zeroes
		for (int i = addr1; i < addr1 + len1; i++) {
			if (mem[i] == 0) {
				leadingZeroes += 2;
			} else if ((mem[i] & 0xF0) == 0) {
				leadingZeroes++;
				break;
			}
		}
		
		// is there anything to do anyway?
		if (leadingZeroes == digits) {
			// shifting a true zero always leads to zero...
			// ... but any zero is to be set to positive zero (in absence of overflow, as it is here)
			mem[addr1 + len1 - 1] = (byte)12;
			return 0; // CC = 0
		} else if (shiftBy == 0) {
			// no shifting...
			// ... replace the sign nibble with the preferred one
			mem[addr1 + len1 - 1] &= 0xF0;
			mem[addr1 + len1 - 1] |= (lastNibble & 0x0F);
			return (isNegative) ? 1 : 2;
		}
		
		// so we must do real shifting:
		// we use an unpacked digit array (right-aligned) and only shift the limits of the relevant digit-range around 
		byte[] digit = new byte[32]; // max. 31 digits plus one for a carry-generated digit when shifting right
		this.unpack(mem, addr1, len1, digit, 0, 32, (byte)0x00, false);
		int validTo = 31; // position of last significant digit
		int validFrom = validTo + leadingZeroes + 1 - digits; // position of first significant digit
		if (shiftBy < 0) {
			// shift right
			shiftBy = -shiftBy; // use a positive shift value for shift counting
			if (shiftBy > (digits - leadingZeroes)) {
				// shifting right more digits than significant digits means:
				// -> all available digits will disappear
				// -> even a possible new carry digit by rounding up each time would disappear
				// -> the result *must* be zero
				validFrom = 32; // no significant digits available
				leadingZeroes = digits; // all digits are zero now
			} else if (roundDigit == 0) {
				// right-shift without rounding
				leadingZeroes += shiftBy;
				validTo -= shiftBy;
			}	else {
				// use the heavy machinery, right-shifting digit by digit and rounding
				byte roundAbove = (byte)(9 - roundDigit); // rounding for a shifted out digit occurs if the digit is higher than that
				while(shiftBy-- > 0 && validFrom <= validTo) {
					boolean carry = (digit[validTo] > roundAbove);
					if (carry) {
						// add one to the current number before shifting out the last digit 
						for (int i = validTo - 1; i >= validFrom; i--) {
							if (carry) { digit[i]++; }
							carry = (digit[i] > 9);
							if (carry) { digit[i] = 0; }
						}
						if (carry) {
							// rounding up adds a new digit at the left...
							// as shifting is at most up to the original number of digits, this
							// new digit will remain and be the final result
							validFrom--;
							leadingZeroes--;
							digit[validFrom] = 1;
						}
					}
					// shift out the last digit 
					validTo--;
					leadingZeroes++;
				}
			}
		} else {
			// shift left
			leadingZeroes -= shiftBy;
			if (leadingZeroes < 0) {
				// "If one or more nonzero digits are shifted out during left shift, decimal overflow occurs."
				isOverflow = true;
				// "The operation is completed" (PrincOps-1987)
				validFrom -= leadingZeroes; // shift out and ignore the overflowing digits 
			}
		}
		
		// construct the new packed result
		boolean resultIsZero = true;
		boolean leftNibble = true; // does the next significant digit to insert start a new packed byte?  
		int pos = addr1; // current packed byte position where to insert
		byte currByte = (byte)0;
		// first zero out the packed number
		for (int i = addr1; i < addr1 + len1; i++) { mem[i] = (byte)0; }
		// find the place for the first significant digit
		while(leadingZeroes > 0) {
			if (leadingZeroes > 1) {
				pos++;
				leadingZeroes -= 2;
			} else {
				leftNibble = false;
				leadingZeroes = 0;
			}
		}
		// insert the remaining significant bits
		while(validFrom <= validTo && validFrom < 32) {
			if (leftNibble) {
				currByte = (byte)(digit[validFrom++] << 4);
				leftNibble = false;
			} else {
				currByte |= (byte)(digit[validFrom++] & 0x0F);
				mem[pos++] = currByte;
				leftNibble = true;
			}
			if (currByte != 0) { resultIsZero = false; }
		}
		if (!leftNibble) { mem[pos] = currByte; }
		// insert the preferred sign nibble into the last packed byte
		// but: in absence of overflow, the sign of a zero result is made positive
		mem[addr1 + len1 - 1] &= 0xF0;
		mem[addr1 + len1 - 1] |= (resultIsZero && !isOverflow) ? 0x0C : (lastNibble & 0x0F);
		
		// return new CC
		return (isOverflow) ? (byte)3 : (resultIsZero) ? (byte)0 : (isNegative) ? (byte)1 : (byte)2;
	}
	
	public int instrSubtract(byte oldCC, byte[] mem, int addr1, int len1, int addr2, int len2) {
		BigDecimal op1 = this.packedToBig(mem, addr1, len1);
		if (op1 == null) { return DATA_EXCEPTION | oldCC; }
		BigDecimal op2 = this.packedToBig(mem, addr2, len2);
		if (op2 == null) { return DATA_EXCEPTION | oldCC; }
		
		BigDecimal res = op1.subtract(op2);
		boolean outOfSpace = this.bigToPacked(res, mem, addr1, len1);
		if (outOfSpace) { return 3; } // overflow, let the invoker decide if program interruption must occur
		return this.ccFromCompare(res.signum());
	}
	
	public int instrZeroAndAdd(byte oldCC, byte[] mem, int addr1, int len1, int addr2, int len2) {
		BigDecimal op2 = this.packedToBig(mem, addr2, len2);
		if (op2 == null) { return DATA_EXCEPTION | oldCC; }

		boolean outOfSpace = this.bigToPacked(op2, mem, addr1, len1);
		if (outOfSpace) { return 3; } // overflow, let the invoker decide if program interruption must occur
		return this.ccFromCompare(op2.signum());
	}
	

	/*
	** General instructions
	** ====================
	** 
	*/
	
	public int[] instrConvertToBinary(int r1, byte[] mem, int addr2) {
		int[] res = {DATA_EXCEPTION, r1};
		BigDecimal op2 = this.packedToBig(mem, addr2, 8);
		if (op2 == null) { return res; }
		
		res[0] = FIXEDPOINT_DIVICE_EXCEPTION;
		long value = op2.longValue();
		if (value < -2147483648 || value > 2147483647) {
			res[1] = (int)(value & 0x00000000FFFFFFFFL);
			return res;
		} else {
			res[0] = 0;
			res[1] = (int)value;
		}
		return res;
	}
	
	public void instrConvertToPacked(int r1, byte[] mem, int addr2) {
		BigDecimal op1 = new BigDecimal(r1);
		this.bigToPacked(op1, mem, addr2, 8); // cannot overflow
	}
	
	public void instrMoveWithOffset(byte[] mem, int addr1, int len1, int addr2, int len2) {
		int from = addr2 + len2 - 1; // current source byte in op2
		int to = addr1 + len1 - 1; // current target byte in op1
		byte src = 0; // current byte from op2
		byte trg = (byte)(mem[to] & 0x0F); // current new byte for op1
		
		while(from >= addr2 && to >= addr1) {
			src = mem[from--];
			trg |= (byte) ((src << 4) & 0xF0);
			mem[to--] = trg;
			trg = (byte)((src >> 4) & 0x0F);
		}
		if (to >= addr1) { mem[to--] = trg; }
		while(to >= addr1) { mem[to--] = 0; }
	}
	
	public void instrPack(byte[] mem, int addr1, int len1, int addr2, int len2) {
		int from = addr2 + len2 - 1; // current source byte in op2
		int to = addr1 + len1 - 1; // current target byte in op1
		boolean rightNibble = true; // will the next digit be inserted in the right 4 bits of the target
		byte packed = 0;
		
		// handle the most right zoned byte: exchange the nibbles to place the sign at the right and the digit at the left
		mem[to--] = (byte)(((mem[from] << 4) & 0xF0) | ((mem[from]) >> 4) & 0x0F);
		from--;
		
		// pack the other digits from op2
		while(from >= addr2 && to >= addr1) {
			if (rightNibble) {
				packed = (byte)(mem[from--] & 0x0F);
				rightNibble = false;
			} else {
				packed |= (byte)((mem[from--] << 4) & 0xF0);
				mem[to--] = packed;
				rightNibble = true;
			}
		}
		if (!rightNibble && to >= addr1) {
			mem[to--] = packed;
		}
		
		// fill op1 with zeroes if necessary
		while(to >= addr1) {
			mem[to--] = 0;
		}
	}
	
	public void instrUnpack(byte[] mem, int addr1, int len1, int addr2, int len2) {
		this.unpack(mem, addr2, len2, mem, addr1, len1, PACKEDZERO, true);
	}
	
	/*
	** Utilities
	*/
	
	static final int asciiZero = 48;
	static final char[] asciiDigits = {'0','1','2','3','4','5','6','7','8','9'};
	static final char[] workDigits = new char[32]; // largest packed decimal is 16 bytes => 31 digits + sign
	
	protected BigDecimal packedToBig(byte[] data, int at, int len) {
		int to = 1;
		int last = at + len - 1;
		int nibble, b;
		
		// just to be sure: limit to 16 bytes (largest packed decimal)
		if (len > 16) { len = 16; }
		
		// unpack the first len-1 bytes, each giving 2 digits
		for (int i = at; i < last; i++) {
			b = data[i];
			nibble = (b & 0xF0) >> 4;
			if (nibble > 9) { return null; }
			workDigits[to++] = asciiDigits[nibble];
			nibble = b & 0x0F;
			if (nibble > 9) { return null; }
			workDigits[to++] = asciiDigits[nibble];
		}
		
		// unpack the last packed byte: 1 digit and the sign
		b = data[last];
		nibble = (b & 0xF0) >> 4;
		if (nibble > 9) { return null; }
		workDigits[to++] = asciiDigits[nibble];
		nibble = b & 0x0F;
		if (nibble == 10 || nibble == 12 || nibble == 14 || nibble == 15) {
			workDigits[0] = '+';
		} else if (nibble == 11 || nibble == 13) {
			workDigits[0] = '-';
		} else {
			return null;
		}
		
		// create the result
		return new BigDecimal(workDigits, 0, to);
	}
	
	protected boolean /* out of space? */ bigToPacked(BigDecimal val, byte[] data, int at, int len) {
		// the single ascii bytes for val 
		byte[] digits = val.toPlainString().getBytes();
		int currDigit = digits.length - 1;
		
		// last position in data to fill
		int pos = at + len - 1;
		
		// the plain string is at least X or -X, so we can create the last packed byte
		int firstDigit = (digits[0] == '-') ? 1 : 0;
		int n2 = (firstDigit > 0) ? 13 : 12;
		int n1 = digits[currDigit--] - asciiZero;
		data[pos--] = (byte)((n1 << 4) | n2);
		
		// create the packed bytes from right to left as long there are both source digits and target space left 
		while(pos >= at && currDigit >= firstDigit) {
			n2 = digits[currDigit--] - asciiZero;
			n1 = (currDigit >= firstDigit) ? digits[currDigit--] - asciiZero : 0;
			data[pos--] = (byte)((n1 << 4) | n2);
		}
		
		// if there are still source digits left => signal out of space
		if (currDigit >= firstDigit) {
			return true; // some digits left over, but target space exhausted...
		}
		
		// no more source digits => fill packed bytes to the left with zeroes 
		while(pos >= at) { data[pos--] = (byte)0; }
		
		// done without space problems
		return false;
	}
	
	protected boolean validPacked(byte[] mem, int at, int len) {
		int curr = at + len - 1;
		byte b = mem[curr--];
		byte signBits = (byte)(b & 0x0F); // (byte)(b >> 4);
		if (signBits < 0x0A) { return false; }
		if ((b & 0xF0) > 0x90) { return false; }
		while(curr >= at) {
			b = mem[curr--];
			if ((b & 0x0F) > 0x09) { return false; }
			if ((b & 0xF0) > 0x90) { return false; }
		}
		return true;
	}
	
	protected byte unpack(
			byte[] pkd, int pkdAt, int pkdLen,
			byte[] unp, int unpAt, int unpLen,
			byte unpLeftBits,
			boolean insertSignBits) {
		int currp = pkdAt + pkdLen - 1;
		int curru = unpAt + unpLen - 1;
		byte b = pkd[currp--];
		byte signBits = (byte)((b << 4) & 0xF0);
		unp[curru--] = (byte)(((b >> 4) & 0x0F) | ((insertSignBits) ? signBits : unpLeftBits));
		while (currp >= pkdAt && curru >= unpAt) {
			b = pkd[currp--];
			unp[curru--] = (byte)((b & 0x0F) | unpLeftBits);
			if (curru < unpAt) { break; }
			unp[curru--] = (byte)(((b >> 4) & 0x0F) | unpLeftBits);
		}
		while(curru >= unpAt) { unp[curru--] = unpLeftBits; }
		
		return (byte)(signBits >> 4);
	}
}
