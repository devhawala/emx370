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

import java.math.BigInteger;


import static dev.hawala.vm370.ebcdic.PlainHex.*;

/**
 * Implementation of floating point instructions.
 * 
 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2015
 */
public class FloatImpl {
	
	private static class NoLogger implements iProcessorEventTracker {
		@Override
		public void logLine(String line, Object... args) { }
	}
	
	private final iProcessorEventTracker logger;
	
	/*
	** Representation of a S/370 floating point value including basic operations 
	*/
	
	public static class FloatRepresentation {
		public boolean isZero = true;
		public boolean isPositive = true;
		private BigInteger fraction = BigInteger.ZERO;
		private int hexExp = 0;
		
		private final String prefix;
		private final iProcessorEventTracker logger;
		
		private FloatRepresentation(String name, iProcessorEventTracker tracker) {
			this.prefix = name;
			this.logger = tracker;
			this.logger.logLine("FloatRepresantion(%s) allocated", name);
			if (tracker == null) {
				this.logger.logLine("****** ERROR: FloatRepresentation() => tracker is null");
			}
		}
		
		@Override
		public String toString() {
			if (this.isZero) { return String.format("[%s: zero]", this.prefix); }
			return String.format(
							"[%s: %s 0x%s exp %d]",
							this.prefix,
							(this.isPositive) ? "+" : "-",
							this.fraction.toString(16),
							this.hexExp
							);
		}
		
		private byte[] memBuffer = new byte[8];
		
		private static final BigInteger NormHigh  = new BigInteger("0FFFFFFFFFFFFFF", 16);
		private static final BigInteger NormLow   = new BigInteger("010000000000000", 16);
		
		private static final BigInteger RoundMask = new BigInteger("0000000FFFFFFFF", 16);
		private static final BigInteger RoundHalf = new BigInteger("00000007FFFFFFF", 16);
		private static final BigInteger RoundAdd  = new BigInteger("000000100000000", 16);
		
		public void setFrom(int valFraction, int valHexExp) {
			this.setFrom(((long)valFraction << 32), valHexExp);
		}
		
		public void setFrom(long valFraction, int valHexExp) {
			//this.logger.logLine("%s.setFrom( valFraction: 0x%016d, valHexExp: %d )", this.prefix, valFraction, valHexExp);
			this.isZero = (valFraction == 0L);
			this.isPositive = (valFraction > 0);
			this.fraction = new BigInteger(Long.toString(Math.abs(valFraction)));
			this.hexExp = 0;
			this.normalizeFraction();
			this.hexExp = valHexExp;
		}
		
		public void setFrom(FloatRepresentation other) {
			//this.logger.logLine("%s.setFrom( other: %s )", this.prefix, other.toString());
			if (this == other) { return; }
			this.isZero = other.isZero;
			this.isPositive = other.isPositive;
			this.fraction = other.fraction;
			this.hexExp = other.hexExp;
			this.normalizeFraction();
		}
		
		public double asDouble() {
			double absVal = this.fraction.doubleValue() * Math.pow(16.0d, (double)(this.hexExp - 14));
			return (this.isPositive) ? absVal : -absVal;
		}
		
		private void normalizeFraction() {
			if (this.fraction.equals(BigInteger.ZERO)) {
				this.isZero = true;
			}
			if (this.isZero) {
				this.setZero();
				//this.logger.logLine("%s.normalizeFraction() -> zero", this.prefix);
				return;
			}
			
			//this.logger.logLine("NormLow  = 0x%016X", NormLow.longValue());
			//this.logger.logLine("fraction = 0x%016X", this.fraction.longValue());
			//this.logger.logLine("NormHigh = 0x%016X", NormHigh.longValue());
			
			while(NormHigh.compareTo(this.fraction) < 0) {
				this.fraction = this.fraction.shiftRight(4);
				this.hexExp++;
				//this.logger.logLine("shiftright(4) => fraction = 0x%016Xn", this.fraction.longValue());
			}
			while(NormLow.compareTo(this.fraction) > 0) {
				this.fraction = this.fraction.shiftLeft(4);
				this.hexExp--;
				//this.logger.logLine("shiftLeft(4) => fraction = 0x%016X", this.fraction.longValue());
			}
			
			//this.logger.logLine("  %s - normalizeFraction() => %s", this.prefix, this.toString());
			
			if (this.fraction.equals(BigInteger.ZERO)) {
				this.setZero();
				this.logger.logLine(" !!!!! FloatRepresentation got zero fraction in normalizeFraction()");
			}
		}
		
		public void internalize(byte[] mem, int at, boolean isLong) {
			// load the fraction bytes
			this.isZero = true;
			this.memBuffer[0] = _00;
			int max = (isLong) ? 8 : 4;
			for (int i = 1; i < max; i++) {
				byte b = mem[at+i];
				if (b != _00) { this.isZero = false; }
				this.memBuffer[i] = b;
			}
			if (!isLong) { for (int i = 4; i < 8; i++) { this.memBuffer[i] = _00; } }
			
			// setup internal data
			if (this.isZero) {
				this.isPositive = true;
				this.hexExp = 0;
				this.fraction = BigInteger.ZERO;
			} else {
				byte b = mem[at];
				this.isPositive = ((b & 0x80) == 0);
				this.hexExp = (b & 0x7F) - 64;
				this.fraction = new BigInteger(this.memBuffer);
				this.normalizeFraction();
			}
			
			//this.logger.logLine("%s: internalized(%s) from 0x%06X: %s", this.prefix, (isLong) ? "long"  :"short", at, this.toString());
		}
		
		public void externalize(byte[] mem, int at, boolean asLong) {
			int max = (asLong) ? 8 : 4;
			
			this.normalizeFraction(); // redundant
			
			if (this.isZero) {
				for (int i = 0; i < max; i++) { mem[at + i] = (byte)0; }
				return;
			}
			
			byte characteristic = (byte)((this.hexExp < -64) ? 0 : (this.hexExp > 63) ? 127 : this.hexExp + 64);
			if (!this.isPositive) { characteristic |= _80; }
			mem[at] = characteristic;
			
			long val = this.fraction.longValue();
			int shiftby = 48;
			for (int i = 1; i < max; i++) {
				mem[at + i] = (byte)((val >> shiftby) & 0xFF);
				shiftby -= 8;
			}
		}
		
		public void addOrSubtract(FloatRepresentation other, boolean doSubtract) { 
			BigInteger myFrac;
			int myExp;
			boolean myIsPositive;
			
			BigInteger otherFrac;
			boolean otherIsPositive;
			
			int expDelta;
			
			if (this.hexExp >= other.hexExp) {
				myFrac = this.fraction;
				myExp = this.hexExp;
				myIsPositive = this.isPositive;
				otherFrac = other.fraction;
				otherIsPositive = (doSubtract) ? !other.isPositive : other.isPositive;
				expDelta = this.hexExp - other.hexExp;
			} else {
				myFrac = other.fraction;
				myExp = other.hexExp;
				myIsPositive = (doSubtract) ? !other.isPositive : other.isPositive;
				otherFrac = this.fraction;
				otherIsPositive = this.isPositive;
				expDelta = other.hexExp - this.hexExp;
			}
			
			
			if (expDelta > 15) {
				// we would shift out all digits including a guard, so "other" is counted as zero
				this.fraction = myFrac;
				this.hexExp = myExp;
				this.isPositive = myIsPositive;
				this.normalizeFraction();
				return;
			}
			

			if (expDelta == 14) {
				// simulate a guard 
				otherFrac = otherFrac.shiftRight(13 * 4);
				myFrac = myFrac.shiftLeft(4);
				myExp--;
			} else {
				otherFrac = otherFrac.shiftRight(expDelta * 4);
			}
			
			
			if (myIsPositive == otherIsPositive) {
				myFrac = myFrac.add(otherFrac);
			} else {
				int cmp = myFrac.compareTo(otherFrac);
				if (cmp == 0) {
					myFrac = BigInteger.ZERO;
				} else if (cmp > 0) {
					myFrac = myFrac.subtract(otherFrac);
				} else {
					myFrac = otherFrac.subtract(myFrac);
					myIsPositive = otherIsPositive;
				}
			}
			
			this.fraction = myFrac;
			this.hexExp = myExp;
			this.isPositive = myIsPositive;
			this.isZero = false; // let normalizeFraction decide this
			this.normalizeFraction();
			//this.logger.logLine("%s - after addOrSubtract() => %s", this.prefix, this.toString());
		}
		
		public void multiply(FloatRepresentation other) {
			if (this.isZero || other.isZero) {
				this.isZero = true;
				this.isPositive = true;
				this.hexExp = 0;
				this.fraction = BigInteger.ZERO;
				return;
			}
			
			BigInteger newFraction = this.fraction.multiply(other.fraction).shiftRight(14 * 4);
			int newHexExp = this.hexExp + other.hexExp;
			boolean newIsPositive = (this.isPositive == other.isPositive) ? true : false;
			
			this.fraction = newFraction;
			this.hexExp = newHexExp;
			this.isPositive = newIsPositive;
			this.isZero = false; // let normalizeFraction decide this
			this.normalizeFraction();
		}
		
		public void divide(FloatRepresentation other) {
			if (other.isZero) { // should have bee tested by invoker, but just to be sure...
				throw new ArithmeticException("Division by zero");
			}
			if (this.isZero) { return; } // dividing zero will change nothing
			
			BigInteger newFraction = this.fraction.shiftLeft(14 * 4).divide(other.fraction);
			int newHexExp = this.hexExp - other.hexExp;
			boolean newIsPositive = (this.isPositive == other.isPositive) ? true : false;

			this.fraction = newFraction;
			this.hexExp = newHexExp;
			this.isPositive = newIsPositive;
			this.isZero = false; // let normalizeFraction decide this
			this.normalizeFraction();
		}
		
		// returns :: -1 => 'this' is smaller, 0 => both are equal, 1 => this is larger
		public int compareWith(FloatRepresentation other) {
			
			// obvious cases
			if (this.isZero && other.isZero) { return 0; } // equal...
			if (!this.isPositive && other.isPositive) { return -1; } // other is larger...
			if (this.isPositive && !other.isPositive) { return 1; } // this is larger...
			
			// here: both have same sign and the fraction is always normalized
			// => result depends on exponent and fraction
			int signCorrection = (this.isPositive) ? 1 : -1;
			if (this.hexExp == other.hexExp) {
				return signCorrection * this.fraction.compareTo(other.fraction);
			} else if (this.hexExp < other.hexExp) {
				return signCorrection * -1;
			} else {
				return signCorrection;
			}
		}
		
		// returns: -1 => negative, 0 => zero, 1 => positive
		public int test() {
			if (this.isZero) { return 0; }
			return  (this.isPositive) ? 1 : -1; 
		}
		
		public void halveFrom(FloatRepresentation other) {
			if (other.isZero) {
				this.isZero = true;
				this.isPositive = true;
				this.hexExp = 0;
				this.fraction = BigInteger.ZERO;
				return;
			}
			
			this.isPositive = other.isPositive;
			this.hexExp = other.hexExp;
			this.fraction = other.fraction.shiftRight(1);
			this.isZero = false; // let normalizeFraction decide this
			this.normalizeFraction();
		}
		
		public void setSign(boolean toPositive) {
			if (!this.isZero) { this.isPositive = toPositive; }
		}
		
		public void roundToShort() {
			if (this.isZero) { return; }
			
			BigInteger rest = this.fraction.and(RoundMask);
			BigInteger newFraction = this.fraction.subtract(rest);
			if (rest.compareTo(RoundHalf) > 0) { newFraction.add(RoundAdd); }
			this.fraction = newFraction;
			this.isZero = false; // let normalizeFraction decide this
			this.normalizeFraction();
		}
		
		public void setZero() {
			this.isZero = true;
			this.isPositive = true;
			this.hexExp = 0;
			this.fraction = BigInteger.ZERO;
		}
	}
	
	/*
	** S/370 floating point instructions implementation 
	** 
	** Returns an integer with:
	**  high 16 bits: interruption code for a program interruption (0x0000 for: no interruption)
	**  low 16 bits : PSW condition code (0..3)
	** with special case -1: instruction not implemented 
	**
	** RR instructions: r1 and r2 valid, addr <- -1
	** RX instructions: r1 and addr valid, r2 <- -1
	*/
	
	// floating point registers 0..4 (S/370: 0/2/4/6)
	public final FloatRepresentation fpr[];
	
	// floating point handler for loading from memory in RX instructions  
	public final FloatRepresentation temp;
	
	/**
	 * Constructor to set up floating point registers
	 * 
	 * @param tracker logger for significant state tracking
	 */
	public FloatImpl(iProcessorEventTracker tracker) {
		this.logger = (tracker != null) ? tracker : new NoLogger();
		this.logger.logLine("FloatImpl allocated");
		
		this.fpr = new FloatRepresentation[4];
		this.fpr[0] = new FloatRepresentation("fpr[0]", this.logger);
		this.fpr[1] = new FloatRepresentation("fpr[2]", this.logger);
		this.fpr[2] = new FloatRepresentation("fpr[4]", this.logger);
		this.fpr[3] = new FloatRepresentation("fpr[6]", this.logger);
		this.temp = new FloatRepresentation("temp", this.logger);
	}
	
	// general exceptions
	protected static final int OPERATION_EXCEPTION = 0x00010000;
	protected static final int SPECIFICATION_EXCEPTION = 0x00060000;
	
	// Floating point exceptions see PrincOps-75 pp. 78/79:
	// -> Exponent-Overflow Exception (not maskable)
	protected static final int EXPONENT_OVERFLOW_EXCEPTION = 0x000C0000;
	// -> Exponent-Underflow Exception (maskable with BC-mode PSW bit 38 => controls result on Exception!)
	protected static final int EXPONENT_UNDERFLOW_EXCEPTION = 0x000D0000;
	// -> Significance Exception (maskable with BC-mode PSW bit 39 => controls result on Exception!)
	protected static final int SIGNIFICANCE_EXCEPTION = 0x000E0000;
	// -> Floating-Point-Divide Exception
	protected static final int FLOATINGPOINT_DIVIDE_EXCEPTION = 0x000F0000;
	
	// CC codes for commparision results
	public static int[] CMP2CC = { 1, 0, 2 };
	
	// value of a fp register for instruction tracing
	public String getRegVal(int regNo) {
		if (regNo != 0 && regNo != 2 && regNo != 4 && regNo != 6) {
			return String.format("[invalid fp-reg %d]", regNo);
		}
		return this.fpr[regNo / 2].toString();
	}
	
	public static int generateResult(byte oldCC, FloatRepresentation f, boolean allowUnderflow, boolean allowSignificance) {
		return generateResult(oldCC, f, 0x7F, allowUnderflow, allowSignificance);
	}
	
	public static int generateResult(byte oldCC, FloatRepresentation f, int cmpvalue, boolean allowUnderflow, boolean allowSignificance) {
		int res = (cmpvalue > -2 && cmpvalue < 2) ? CMP2CC[cmpvalue + 1] : oldCC;
		if (f.hexExp > 63) {
			// overflow exception
			f.hexExp -= 128;
			return res | EXPONENT_OVERFLOW_EXCEPTION;
		}
		if (f.hexExp < -64) {
			if (allowUnderflow) {
				f.hexExp += 128;
				return res | EXPONENT_UNDERFLOW_EXCEPTION;
			}
			f.setZero();
			return (cmpvalue > -2 && cmpvalue < 2) ? 0 : oldCC; // CC is now "equal" if there was an instruction result or else: old CC
		}
		if (f.isZero && allowSignificance) {
			return res | SIGNIFICANCE_EXCEPTION;
		}
		
		return res;
	}
	
	public void resetRegisters() {
		this.fpr[0].setZero();
		this.fpr[1].setZero();
		this.fpr[2].setZero();
		this.fpr[3].setZero();
	}
	
	public int executeInstruction(
			byte oldCC, byte[] mem,
			byte instr, int r1, int r2, int addr,
			boolean allowUnderflow, boolean allowSignificance) {
		if (instr < (byte)0x60) {
			return this.executeRRInstruction(oldCC, mem, instr, r1, r2, allowUnderflow, allowSignificance);
		} else {
			return this.executeRXInstruction(oldCC, mem, instr, r1, addr, allowUnderflow, allowSignificance);
		}
	}
	
	public int executeRRInstruction(
			byte oldCC, byte[] mem,
			byte instr, int r1, int r2,
			boolean allowUnderflow, boolean allowSignificance) {
		
		if (r1 != 0 && r1 != 2 && r1 != 4 && r1 != 6) { return SPECIFICATION_EXCEPTION; }
		if (r2 != 0 && r2 != 2 && r2 != 4 && r2 != 6) { return SPECIFICATION_EXCEPTION; }
		FloatRepresentation f1 = this.fpr[r1 / 2];
		FloatRepresentation f2 = this.fpr[r2 / 2];
		
		// interpret the possible instructions
		switch(instr) {
		
		case (byte)0x20: {
			/* LPDR - LOAD POSITIVE (long) */
			f1.setFrom(f2);
			f1.isPositive = true;
			return (f1.isZero) ? 0 : 2;
		}

		case (byte)0x21: {
			/* LNDR - LOAD NEGATIVE (long) */
			f1.setFrom(f2);
			f1.isPositive = false;
			return (f1.isZero) ? 0 : 1;
		}

		case (byte)0x22: {
			/* LTDR - LOAD AND TEST (long) */
			f1.setFrom(f2);
			return generateResult(oldCC, f1, f1.test(), allowUnderflow, false);
		}
		
		case (byte)0x23: {
			/* LCDR - LOAD COMPLEMENT (long) */
			f1.setFrom(f2);
			f1.isPositive = !f1.isPositive;
			return generateResult(oldCC, f1, f1.test(), allowUnderflow, false);
		}

		case (byte)0x24: {
			/* HDR - HALVE (long) */
			f1.halveFrom(f2);
			return generateResult(oldCC, f1, allowUnderflow, allowSignificance);
		}

		case (byte)0x25: {
			/* LRDR - LOAD ROUNDED (extended to long) */
			return OPERATION_EXCEPTION; // Feature "extended floating-point" not installed
		}

		case (byte)0x26: {
			/* MXR - MULTIPLY (extended) */
			return OPERATION_EXCEPTION; // Feature "extended floating-point" not installed
		}

		case (byte)0x27: {
			/* MXDR - MULTIPLY (long to extended) */
			return OPERATION_EXCEPTION; // Feature "extended floating-point" not installed
		}

		case (byte)0x28: {
			/* LDR - LOAD (long) */
			f1.setFrom(f2);
			return oldCC;
		}

		case (byte)0x29: {
			/* CDR - COMPARE (long) */
			return CMP2CC[f1.compareWith(f2) + 1];
		}

		case (byte)0x2A: {
			/* ADR - ADD NORMALIZED (long) */
			allowSignificance &= f1.isZero;
			f1.addOrSubtract(f2, false);
			return generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
		}

		case (byte)0x2B: {
			/* SDR - SUBTRACT NORMALIZED (long) */
			allowSignificance &= f1.isZero;
			f1.addOrSubtract(f2, true);
			return generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
		}

		case (byte)0x2C: {
			/* MDR - MULTIPLY (long) */
			allowSignificance &= f1.isZero;
			f1.multiply(f2);
			return generateResult(oldCC, f1, allowUnderflow, allowSignificance);
		}

		case (byte)0x2D: {
			/* DDR - DIVIDE (long) */
			if (f2.isZero) { return FLOATINGPOINT_DIVIDE_EXCEPTION; }
			allowSignificance &= f1.isZero;
			f1.divide(f2);
			return generateResult(oldCC, f1, allowUnderflow, allowSignificance);
		}

		case (byte)0x2E: {
			/* AWR - ADD UNNORMALIZED (long) */
			allowSignificance &= f1.isZero;
			f1.addOrSubtract(f2, false);
			return generateResult(oldCC, f1, f1.test(), false, allowSignificance);
		}

		case (byte)0x2F: {
			/* SWR - SUBTRACT UNNORMALIZED (long) */
			allowSignificance &= f1.isZero;
			f1.addOrSubtract(f2, true);
			return generateResult(oldCC, f1, f1.test(), false, allowSignificance);
		}

		case (byte)0x30: {
			/* LPER - LOAD POSITIVE (short) */
			f1.setFrom(f2);
			f1.isPositive = true;
			return (f1.isZero) ? 0 : 2;
		}

		case (byte)0x31: {
			/* LNER - LOAD NEGATIVE (short) */
			f1.setFrom(f2);
			f1.isPositive = false;
			return (f1.isZero) ? 0 : 1;
		}

		case (byte)0x32: {
			/* LTER - LOAD AND TEST (short) */
			f1.setFrom(f2);
			return generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
		}

		case (byte)0x33: {
			/* LCER - LOAD COMPLEMENT (short) */
			f1.setFrom(f2);
			f1.isPositive = !f1.isPositive;
			return generateResult(oldCC, f1, f1.test(), allowUnderflow, false);
		}

		case (byte)0x34: {
			/* HER - HALVE (short) */
			f1.halveFrom(f2);
			return generateResult(oldCC, f1, allowUnderflow, allowSignificance);
		}

		case (byte)0x35: {
			/* LRER - LOAD ROUNDED (long to short) */
			f1.halveFrom(f2);
			f1.roundToShort();
			return generateResult(oldCC, f1, allowUnderflow, false);
		}

		case (byte)0x36: {
			/* AXR - ADD NORMALIZED (extended) */
			return OPERATION_EXCEPTION; // Feature "extended floating-point" not installed
		}

		case (byte)0x37: {
			/* SXR - SUBTRACT NORMALIZED (extended) */
			return OPERATION_EXCEPTION; // Feature "extended floating-point" not installed
		}

		case (byte)0x38: {
			/* LER - LOAD (short) */
			f1.setFrom(f2);
			return oldCC;
		}

		case (byte)0x39: {
			/* CER - COMPARE (short) */
			return CMP2CC[f1.compareWith(f2) + 1];
		}

		case (byte)0x3A: {
			/* AER - ADD NORMALIZED (short) */
			allowSignificance &= f1.isZero;
			f1.addOrSubtract(f2, false);
			return generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
		}

		case (byte)0x3B: {
			/* SER - SUBTRACT NORMALIZED (short) */
			allowSignificance &= f1.isZero;
			f1.addOrSubtract(f2, true);
			return generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
		}

		case (byte)0x3C: {
			/* MER - MULTIPLY (short to long) */
			allowSignificance &= f1.isZero;
			f1.multiply(f2);
			return generateResult(oldCC, f1, allowUnderflow, allowSignificance);
		}

		case (byte)0x3D: {
			/* DER - DIVIDE (short) */
			if (f2.isZero) { return FLOATINGPOINT_DIVIDE_EXCEPTION; }
			allowSignificance &= f1.isZero;
			f1.divide(f2);
			return generateResult(oldCC, f1, allowUnderflow, allowSignificance);
		}

		case (byte)0x3E: {
			/* AUR - ADD UNNORMALIZED (short) */
			allowSignificance &= f1.isZero;
			f1.addOrSubtract(f2, false);
			return generateResult(oldCC, f1, f1.test(), false, allowSignificance);
		}

		case (byte)0x3F: {
			/* SUR - SUBTRACT UNNORMALIZED (short) */
			allowSignificance &= f1.isZero;
			f1.addOrSubtract(f2, true);
			return generateResult(oldCC, f1, f1.test(), false, allowSignificance);
		}
		
		default:
			// well, this is really an unknown instruction...
			return -1;
				
		}
	}
	
	public int executeRXInstruction(
			byte oldCC, byte[] mem,
			byte instr, int r1, int addr,
			boolean allowUnderflow, boolean allowSignificance) {
		
		if (r1 != 0 && r1 != 2 && r1 != 4 && r1 != 6) { return SPECIFICATION_EXCEPTION; }
		FloatRepresentation f1 = this.fpr[r1 / 2];
		
		// interpret the possible instructions
		switch(instr) {
		
		case (byte)0x60: {
			/* STD - STORE (long) */
			f1.externalize(mem, addr, true);
			return oldCC;
		}

		case (byte)0x67: {
			/* MXD - MULTIPLY (long to extended) */
			return OPERATION_EXCEPTION; // Feature "extended floating-point" not installed
		}

		case (byte)0x68: {
			/* LD - LOAD (long) */
			f1.internalize(mem, addr, true);
			return oldCC;
		}

		case (byte)0x69: {
			/* CD - COMPARE (long) */
			this.temp.internalize(mem, addr, true);
			return CMP2CC[f1.compareWith(this.temp) + 1];
		}

		case (byte)0x6A: {
			/* AD - ADD NORMALIZED (long) */
			this.temp.internalize(mem, addr, true);
			allowSignificance &= f1.isZero;
			f1.addOrSubtract(this.temp, false);
			return generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
		}

		case (byte)0x6B: {
			/* SD - SUBTRACT NORMALIZED (long) */
			this.temp.internalize(mem, addr, true);
			allowSignificance &= f1.isZero;
			f1.addOrSubtract(this.temp, true);
			return generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
		}

		case (byte)0x6C: {
			/* MD - MULTIPLY (long) */
			this.temp.internalize(mem, addr, true);
			allowSignificance &= f1.isZero;
			f1.multiply(this.temp);
			return generateResult(oldCC, f1, allowUnderflow, allowSignificance);
		}

		case (byte)0x6D: {
			/* DD - DIVIDE (long) */
			this.temp.internalize(mem, addr, true);
			if (this.temp.isZero) { return FLOATINGPOINT_DIVIDE_EXCEPTION; }
			allowSignificance &= f1.isZero;
			f1.divide(this.temp);
			return generateResult(oldCC, f1, allowUnderflow, allowSignificance);
		}

		case (byte)0x6E: {
			/* AW - ADD UNNORMALIZED (long) */
			this.temp.internalize(mem, addr, true);
			allowSignificance &= f1.isZero;
			f1.addOrSubtract(this.temp, false);
			return generateResult(oldCC, f1, f1.test(), false, allowSignificance);
		}

		case (byte)0x6F: {
			/* SW - SUBTRACT UNNORMALIZED (long) */
			this.temp.internalize(mem, addr, true);
			allowSignificance &= f1.isZero;
			f1.addOrSubtract(this.temp, true);
			return generateResult(oldCC, f1, f1.test(), false, allowSignificance);
		}

		case (byte)0x70: {
			/* STE - STORE (short) */
			f1.externalize(mem, addr, false);
			return oldCC;
		}

		case (byte)0x78: {
			/* LE - LOAD (short) */
			f1.internalize(mem, addr, false);
			return oldCC;
		}

		case (byte)0x79: {
			/* CE - COMPARE (short) */
			this.temp.internalize(mem, addr, false);
			return CMP2CC[f1.compareWith(this.temp) + 1];
		}

		case (byte)0x7A: {
			/* AE - ADD NORMALIZED (short) */
			this.temp.internalize(mem, addr, false);
			allowSignificance &= f1.isZero;
			f1.addOrSubtract(this.temp, false);
			return generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
		}

		case (byte)0x7B: {
			/* SE - SUBTRACT NORMALIZED (short) */
			this.temp.internalize(mem, addr, false);
			allowSignificance &= f1.isZero;
			f1.addOrSubtract(this.temp, true);
			return generateResult(oldCC, f1, f1.test(), allowUnderflow, allowSignificance);
		}

		case (byte)0x7C: {
			/* ME - MULTIPLY (short to long) */
			this.temp.internalize(mem, addr, false);
			allowSignificance &= f1.isZero;
			f1.multiply(this.temp);
			return generateResult(oldCC, f1, allowUnderflow, allowSignificance);
		}

		case (byte)0x7D: {
			/* DE - DIVIDE (short) */
			this.temp.internalize(mem, addr, false);
			if (this.temp.isZero) { return FLOATINGPOINT_DIVIDE_EXCEPTION; }
			allowSignificance &= f1.isZero;
			f1.divide(this.temp);
			return generateResult(oldCC, f1, allowUnderflow, allowSignificance);
		}

		case (byte)0x7E: {
			/* AU - ADD UNNORMALIZED (short) */
			this.temp.internalize(mem, addr, false);
			allowSignificance &= f1.isZero;
			f1.addOrSubtract(this.temp, false);
			return generateResult(oldCC, f1, f1.test(), false, allowSignificance);
		}

		case (byte)0x7F: {
			/* SU - SUBTRACT UNNORMALIZED (short) */
			this.temp.internalize(mem, addr, false);
			allowSignificance &= f1.isZero;
			f1.addOrSubtract(this.temp, true);
			return generateResult(oldCC, f1, f1.test(), false, allowSignificance);
		}
		
		default:
			// well, this is really an unknown instruction...
			return -1;
				
		}
	}
	
	public void dumpRegs() {
		this.logger.logLine("   %s", this.fpr[0]);
		this.logger.logLine("   %s", this.fpr[1]);
		this.logger.logLine("   %s", this.fpr[2]);
		this.logger.logLine("   %s", this.fpr[3]);
	}
	
	
	/*
	** Test code
	*/
	
	private static void dump(String prefix, byte[] v) {
		dump(prefix, v, null);
	}
	
	private static void dump(String prefix, byte[] v, FloatRepresentation f) {
		System.out.print(prefix);
		System.out.print("0x");
		for (int i = 0; i < v.length; i++) {
			System.out.printf(" %02X", v[i]);
		}
		if (f != null) {
			//System.out.printf(" = %f\n", f.asDouble());
			System.out.println(" = " + f.asDouble());
		} else {
			System.out.println();
		}
	}
	
	private static class SysemOutLogger implements iProcessorEventTracker {

		@Override
		public void logLine(String line, Object... args) {
			System.out.printf(line, args);
			System.out.println();
		}
	}
	
	private static void testAdd(byte[] val1, byte[] val2) {
		FloatRepresentation f1 = new FloatRepresentation("test1", new SysemOutLogger());
		FloatRepresentation f2 = new FloatRepresentation("test2", new SysemOutLogger());
		
		byte[] exported = (val1.length > 7 || val2.length > 7) ? new byte[8] : new byte[4];
		
		f1.internalize(val1, 0, (val1.length > 7));
		f2.internalize(val2, 0, (val2.length > 7));
		
		double expected = f1.asDouble() + f2.asDouble();
		
		dump("val1        : ", val1, f1);
		dump("val2        : ", val2, f2);
		f1.addOrSubtract(f2, false);
		f1.externalize(exported, 0, false);
		dump("val1 + val2 : ", exported, f1);
		
		double result = f1.asDouble();
		String isOK = (Math.abs((result - expected) / expected) < 0.00000001) ? "OK" : "not good enough!";
		System.out.println("Expected    :                  " + expected + " => " + isOK);
	}
	
	private static void testSubtract(byte[] val1, byte[] val2) {
		FloatRepresentation f1 = new FloatRepresentation("test1", new SysemOutLogger());
		FloatRepresentation f2 = new FloatRepresentation("test2", new SysemOutLogger());
		
		byte[] exported = (val1.length > 7 || val2.length > 7) ? new byte[8] : new byte[4];
		
		f1.internalize(val1, 0, (val1.length > 7));
		f2.internalize(val2, 0, (val2.length > 7));
		
		double expected = f1.asDouble() - f2.asDouble();
		
		dump("val1        : ", val1, f1);
		dump("val2        : ", val2, f2);
		f1.addOrSubtract(f2, true);
		f1.externalize(exported, 0, false);
		dump("val1 - val2 : ", exported, f1);
		
		double result = f1.asDouble();
		String isOK = (Math.abs((result - expected) / expected) < 0.00000001) ? "OK" : "not good enough!";
		System.out.println("Expected    :                  " + expected + " => " + isOK);
	}
	
	public static void main(String[] args) {
		
		FloatRepresentation f = new FloatRepresentation("test1", new SysemOutLogger());
		byte[] b4 = new byte[4];
		byte[] b8 = new byte[8];
		//f.check();
		
		f.setFrom(0x123456, 0);
		f.externalize(b4, 0, false);
		dump("f.setFrom(0x123456, 0): ", b4, f);
		
		System.out.println();
		
		
		byte[] fx82p1 = { _43, _08, _21, _00 };
		dump("fx82p1 original: ", fx82p1);
		f.internalize(fx82p1, 0, false);
		f.externalize(b4, 0, false);
		dump("in/out float   : ", b4);
		f.externalize(b8, 0, true);
		dump("in/out double  : ", b8);
		
		System.out.println();
		
		byte[] val1 = { _42 , _82, _10, _00 };
		byte[] val2 = { _41, _12,_34, _56 };
		
		testAdd(val1, val2);
		System.out.println();
		testAdd(val2, val1);
		System.out.println();
		testAdd(val1, val1);
		System.out.println();
		testAdd(val2, val2);
		
		testSubtract(val1, val2);
		System.out.println();
		testSubtract(val2, val1);
		System.out.println();
		testSubtract(val1, val1);
		System.out.println();
		testSubtract(val2, val2);
		
		byte[] val3 = {_41, _10,  _00, _00 };
		byte[] val4 = {_40, _80,  _00, _00 };
		
		testAdd(val3, val4);
		System.out.println();
		testAdd(val4, val3);
		System.out.println();
		testAdd(val3, val3);
		System.out.println();
		testAdd(val4, val4);
		
		testSubtract(val3, val4);
		System.out.println();
		testSubtract(val4, val3);
		System.out.println();
		testSubtract(val3, val3);
		System.out.println();
		testSubtract(val4, val4);
		
		/*
		f1.internalize(new byte[] {_40, _10,  _00, _00 }, 0, false);
		*/
		
		// error case while GCC TESTCMD ( CMS
		{
			FloatRepresentation f1 = new FloatRepresentation("test1", new SysemOutLogger());
			FloatRepresentation f2 = new FloatRepresentation("test2", new SysemOutLogger());
			
			f1.setFrom(0x80000002000000L, 8);
			f2.setFrom(0x80000000000000L, 8);
			System.out.printf("   %s\n", f1.toString());
			System.out.printf(" - %s\n", f2.toString());
			
			f1.addOrSubtract(f2, true);
			System.out.printf(" = %s\n", f1.toString());
		}
	}
}
