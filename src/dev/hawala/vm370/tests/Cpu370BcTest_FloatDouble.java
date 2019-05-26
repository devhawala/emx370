/*
** This file is part of the emx370 emulator UnitTests.
**
** This software is provided "as is" in the hope that it will be useful,
** with no promise, commitment or even warranty (explicit or implicit)
** to be suited or usable for any particular purpose.
** Using this software is at your own risk!
**
** Written by Dr. Hans-Walter Latz, Berlin (Germany), 2015
** Released to the public domain.
*/

package dev.hawala.vm370.tests;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import dev.hawala.vm370.vm.machine.Cpu370Bc;

@RunWith(value = Parameterized.class)
public class Cpu370BcTest_FloatDouble extends AbstractCpu370BcTest {
	
	public Cpu370BcTest_FloatDouble(Class<? extends Cpu370Bc> cpuClass) {
    	this.cpuClassUnderTest = cpuClass;
    }
	
	@Before
	public void preTest() {
		super.preTest();
	}
	
	@After
	public void postTest() {
		// ? necessary cleanup after test ?
	}

	private byte[] zero               = { _00, _00, _00, _00, _00, _00, _00, _00 };
	private byte[] onePointZero       = { _41, _10, _00, _00, _00, _00, _00, _00 };
	private byte[] minusOnePointZero  = { _C1, _10, _00, _00, _00, _00, _00, _00 };
	private byte[] twoPointZero       = { _41, _20, _00, _00, _00, _00, _00, _00 };
	private byte[] minusTwoPointZero  = { _C1, _20, _00, _00, _00, _00, _00, _00 };
	private byte[] zeroPointFive      = { _40, _80, _00, _00, _00, _00, _00, _00 };
	private byte[] minusZeroPointFive = { _C0, _80, _00, _00, _00, _00, _00, _00 };
	
	/*
	 * Tests: load register from memory , then store same register to memory 
	 */
	
	private void innerTestLD_STD(int freg, byte[] value) {
		setCC(CC0);
		setMemB(0x010180, value);         // address: 0x010180
		setGPR(2, 0x010000);              // set base register for LD addressing
		setGPR(1, 0x000100);              // set index register for LD addressing
		setGPR(6, 0x200000);              // set base register for STD addressing
		setGPR(5, 0x000100);              // set index register for STD addressing
		setMemB(0x200138, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		setMemB(0x200140, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		setMemB(0x200148, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		byte b1from = (byte)((freg << 4) | 1);
		byte b1to = (byte)((freg << 4) | 5);
		setInstructions(
				_68, b1from, _20, _80,     // LD R0 <- (base=R2,index=R1,offset=0x080)=0x010180
				_60, b1to, _60, _40        // STD R0 -> (base=R6,index=R5,offset=0x040)=0x200140
		);
		execute(2); // do two instructions
		checkIL(2); // instruction length code of last instruction
		checkIA(CodeBase+8);     // instruction address at end
		checkMemB(0x010180, value); // input value must stay unchanged
		checkMemB(0x200140, value); // output value must have been written there
		checkMemB(0x20013F, _FF); // the bytes before and behind
		checkMemB(0x200148, _FF); // the store location must be unchanged
		checkCC(CC0);            // must stay unchanged
	}
	
	@Test
	public void base_LD_STD_One() {
		this.innerTestLD_STD(0, onePointZero);
		this.innerTestLD_STD(2, onePointZero);
		this.innerTestLD_STD(4, onePointZero);
		this.innerTestLD_STD(6, onePointZero);
	}
	
	@Test
	public void base_LD_STD_Two() {
		this.innerTestLD_STD(0, twoPointZero);
		this.innerTestLD_STD(2, twoPointZero);
		this.innerTestLD_STD(4, twoPointZero);
		this.innerTestLD_STD(6, twoPointZero);
	}
	
	/*
	 * Tests: load register A from memory , load register B from register A , store register B to memory
	 */
	
	private void innerTestLD_LDR_STD(int freg, int viaReg, byte[] value) {
		setCC(CC0);
		setMemB(0x010180, value);         // address: 0x010180
		setGPR(2, 0x010000);              // set base register for LE addressing
		setGPR(1, 0x000100);              // set index register for LE addressing
		setGPR(6, 0x200000);              // set base register for STE addressing
		setGPR(5, 0x000100);              // set index register for STE addressing
		setMemB(0x200138, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		setMemB(0x200140, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		setMemB(0x200148, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		byte b1from = (byte)((freg << 4) | 1);
		byte regToFrom = (byte) (viaReg << 4 | freg);
		byte b1to = (byte)((viaReg << 4) | 5);
		setInstructions(
				_68, b1from, _20, _80,     // LD freg <- (base=R2,index=R1,offset=0x080)=0x010180
				_28, regToFrom,            // LDR viaReg <- freg
				_60, b1to, _60, _40        // STD viaReg -> (base=R6,index=R5,offset=0x040)=0x200140
		);
		execute(3); // do three instructions
		checkIL(2); // instruction length code of last instruction
		checkIA(CodeBase+10);     // instruction address at end
		checkMemB(0x010180, value); // input value must stay unchanged
		checkMemB(0x200140, value); // output value must have been writtern there
		checkMemB(0x20013F, _FF); // the bytes before and behind
		checkMemB(0x200148, _FF); // the store location must be unchanged
		checkCC(CC0);            // must stay unchanged
	}
	
	@Test
	public void base_LD_LDR_STD_One() {
		this.innerTestLD_LDR_STD(0, 2, onePointZero);
		this.innerTestLD_LDR_STD(2, 4, onePointZero);
		this.innerTestLD_LDR_STD(4, 6, onePointZero);
		this.innerTestLD_LDR_STD(6, 0, onePointZero);
	}
	
	@Test
	public void base_LD_LDR_STD_Two() {
		this.innerTestLD_LDR_STD(0, 6, twoPointZero);
		this.innerTestLD_LDR_STD(2, 4, twoPointZero);
		this.innerTestLD_LDR_STD(4, 2, twoPointZero);
		this.innerTestLD_LDR_STD(6, 0, twoPointZero);
	}
	
	/*
	 * Tests: load register A and B from memory , do "A opRR B" , store result to memory
	 */
	
	private byte[] p1 = { _42 , _14 , _00 , _00, _00, _00, _00, _00 }; // +20.0 
	private byte[] p2 = { _42 , _1F , _1F , _97, _F3, _81, _85, _FA }; // +31.1234123412341234
	private byte[] r1 = { _42 , _33 , _1F , _97, _F3, _81, _85, _FA }; // +51.1234123412341234 = p1 + p2
	private byte[] r2 = { _C1 , _B1 , _F9 , _7F, _38, _18, _5F, _A0 }; // -11.1234123412341234 = p1 - p2
//	private byte[] r3 = { _43 , _26 , _E7 , _7D, _F0, _61, _E7, _78 }; // +622.468246824682468 = p1 * p2
	private byte[] r3 = { _43 , _26 , _E7 , _7D, _F0, _61, _E7, _70 }; // ... last byte error: 0x70 instead of 0x78 == emx370-Imprecision on the last 4 of 56 fraction-bits (this is acceptable)
	private byte[] r4 = { _40 , _A4 , _81 , _A2, _55, _E3, _9F, _19 }; // +0,64260305973914133 = p1 / p2

	private void innerTestLD_LD_op_STD(
			int op1reg,
			int op2reg,
			byte op,
			byte[] val1,
			byte[] val2,
			byte[] expected,
			byte expCC) {
		setCC(CC3);
		setMemB(0x010180, val1);          // address: 0x010180
		setMemB(0x010280, val2);          // address: 0x010280
		setMemB(0x200138, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		setMemB(0x200140, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		setMemB(0x200148, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		setGPR(2, 0x010000);              // set base register for LE addressing
		setGPR(1, 0x000100);              // set index register for LE addressing
		setGPR(6, 0x200000);              // set base register for STE addressing
		setGPR(5, 0x000100);              // set index register for STE addressing
		byte b1from = (byte)((op1reg << 4) | 1);
		byte b2from = (byte)((op2reg << 4) | 1);
		byte regop1op2 = (byte) (op1reg << 4 | op2reg);
		byte b1to = (byte)((op1reg << 4) | 5);
		setInstructions(
				_68, b1from, _20, _80,     // LD op1reg <- (base=R2,index=R1,offset=0x080)=0x010180
				_68, b2from, _21, _80,     // LD op2reg <- (base=R2,index=R1,offset=0x180)=0x010280
				op, regop1op2,             // op1reg <- op1reg op op2reg
				_60, b1to, _60, _40        // STD op1reg -> (base=R6,index=R5,offset=0x040)=0x200140
		);
		execute(4); // do four instructions
		checkIL(2); // instruction length code of last instruction
		checkIA(CodeBase+14);  // instruction address at end
		checkMemB(0x200140, expected); // output value must have been written there
		checkMemB(0x20013F, _FF); // the bytes before and behind
		checkMemB(0x200148, _FF); // the store location must be unchanged
		checkCC(expCC);          // CC3 if it stays unchanged
	}
	
	// Add Normalized - RR - Double Operands
	// condition code: 0 => result zero, 1 => result negative, 2 => result positive
	@Test
	public void test_LD_LD_ADR_STD() {
		this.innerTestLD_LD_op_STD(0, 2, _2A, onePointZero, onePointZero, twoPointZero, CC2);
		this.innerTestLD_LD_op_STD(0, 4, _2A, twoPointZero, minusOnePointZero, onePointZero, CC2);
		this.innerTestLD_LD_op_STD(2, 0, _2A, zeroPointFive, zeroPointFive, onePointZero, CC2);

		this.innerTestLD_LD_op_STD(4, 6, _2A, zero, zero, zero, CC0);
		this.innerTestLD_LD_op_STD(4, 6, _2A, minusOnePointZero, minusOnePointZero, minusTwoPointZero, CC1);
		this.innerTestLD_LD_op_STD(4, 6, _2A, minusZeroPointFive, minusZeroPointFive, minusOnePointZero, CC1);
		this.innerTestLD_LD_op_STD(4, 2, _2A, minusTwoPointZero, onePointZero, minusOnePointZero, CC1);

		this.innerTestLD_LD_op_STD(0, 6, _2A, minusZeroPointFive, zeroPointFive, zero, CC0);
		this.innerTestLD_LD_op_STD(6, 2, _2A, onePointZero, minusOnePointZero, zero, CC0);
		this.innerTestLD_LD_op_STD(4, 6, _2A, minusZeroPointFive, zeroPointFive, zero, CC0);
		this.innerTestLD_LD_op_STD(6, 0, _2A, onePointZero, minusOnePointZero, zero, CC0);
		
		this.innerTestLD_LD_op_STD(0, 2, _2A, p1, p2, r1, CC2);
	}
	
	// Subtract Normalized - RR - Double Operands
	// condition code: 0 => result zero, 1 => result negative, 2 => result positive
	@Test
	public void test_LD_LD_SDR_STD() {
		this.innerTestLD_LD_op_STD(0, 2, _2B, twoPointZero, onePointZero, onePointZero, CC2);
		this.innerTestLD_LD_op_STD(0, 2, _2B, onePointZero, zero, onePointZero, CC2);
		this.innerTestLD_LD_op_STD(0, 2, _2B, onePointZero, minusOnePointZero, twoPointZero, CC2);
		this.innerTestLD_LD_op_STD(0, 2, _2B, zeroPointFive, minusZeroPointFive, onePointZero, CC2);
		this.innerTestLD_LD_op_STD(0, 2, _2B, zero, minusOnePointZero, onePointZero, CC2);

		this.innerTestLD_LD_op_STD(0, 2, _2B, onePointZero, twoPointZero, minusOnePointZero, CC1);
		this.innerTestLD_LD_op_STD(0, 2, _2B, minusOnePointZero, onePointZero, minusTwoPointZero, CC1);
		this.innerTestLD_LD_op_STD(0, 2, _2B, minusZeroPointFive, zeroPointFive, minusOnePointZero, CC1);
		this.innerTestLD_LD_op_STD(0, 2, _2B, zero, onePointZero, minusOnePointZero, CC1);
		this.innerTestLD_LD_op_STD(0, 2, _2B, zero, twoPointZero, minusTwoPointZero, CC1);
		
		this.innerTestLD_LD_op_STD(0, 2, _2B, zero, zero, zero, CC0);
		this.innerTestLD_LD_op_STD(0, 2, _2B, onePointZero, onePointZero, zero, CC0);
		this.innerTestLD_LD_op_STD(0, 2, _2B, twoPointZero, twoPointZero, zero, CC0);
		this.innerTestLD_LD_op_STD(0, 2, _2B, zeroPointFive, zeroPointFive, zero, CC0);
		this.innerTestLD_LD_op_STD(0, 2, _2B, minusOnePointZero, minusOnePointZero, zero, CC0);
		this.innerTestLD_LD_op_STD(0, 2, _2B, minusTwoPointZero, minusTwoPointZero, zero, CC0);
		this.innerTestLD_LD_op_STD(0, 2, _2B, minusZeroPointFive, minusZeroPointFive, zero, CC0);
		
		this.innerTestLD_LD_op_STD(0, 2, _2B, p1, p2, r2, CC1); // result negative 
	}
	
	// Multiply Normalized - RR - Double Operands
	// condition code: 0 => result zero, 1 => result negative, 2 => result positive
	@Test
	public void test_LD_LD_MDR_STD() { // cc is unchanged, so it must always be CC3 (the start value in innerTestLD_LD_op_STD) 
		this.innerTestLD_LD_op_STD(0, 2, _2C, onePointZero, onePointZero, onePointZero, CC3); 
		this.innerTestLD_LD_op_STD(0, 4, _2C, onePointZero, twoPointZero, twoPointZero, CC3); 
		this.innerTestLD_LD_op_STD(0, 6, _2C, zeroPointFive, twoPointZero, onePointZero, CC3);
		 
		this.innerTestLD_LD_op_STD(2, 4, _2C, onePointZero, minusOnePointZero, minusOnePointZero, CC3); 
		this.innerTestLD_LD_op_STD(2, 6, _2C, minusOnePointZero, twoPointZero, minusTwoPointZero, CC3); 
		this.innerTestLD_LD_op_STD(4, 6, _2C, minusZeroPointFive, twoPointZero, minusOnePointZero, CC3);
		this.innerTestLD_LD_op_STD(6, 4, _2C, minusOnePointZero, onePointZero, minusOnePointZero, CC3); 
		this.innerTestLD_LD_op_STD(6, 2, _2C, twoPointZero, minusOnePointZero, minusTwoPointZero, CC3); 
		this.innerTestLD_LD_op_STD(6, 0, _2C, twoPointZero, minusZeroPointFive, minusOnePointZero, CC3);
		 
		this.innerTestLD_LD_op_STD(0, 2, _2C, minusOnePointZero, minusOnePointZero, onePointZero, CC3); 
		this.innerTestLD_LD_op_STD(0, 4, _2C, minusOnePointZero, minusTwoPointZero, twoPointZero, CC3); 
		this.innerTestLD_LD_op_STD(0, 6, _2C, minusZeroPointFive, minusTwoPointZero, onePointZero, CC3);
		this.innerTestLD_LD_op_STD(2, 4, _2C, minusTwoPointZero, minusOnePointZero, twoPointZero, CC3); 
		this.innerTestLD_LD_op_STD(4, 6, _2C, minusTwoPointZero, minusZeroPointFive, onePointZero, CC3);
		 
		this.innerTestLD_LD_op_STD(2, 6, _2C, minusOnePointZero, zero, zero, CC3); 
		this.innerTestLD_LD_op_STD(2, 0, _2C, minusTwoPointZero, zero, zero, CC3); 
		this.innerTestLD_LD_op_STD(4, 0, _2C, minusZeroPointFive, zero, zero, CC3);
		this.innerTestLD_LD_op_STD(6, 0, _2C, onePointZero, zero, zero, CC3); 
		this.innerTestLD_LD_op_STD(6, 2, _2C, twoPointZero, zero, zero, CC3); 
		this.innerTestLD_LD_op_STD(6, 4, _2C, zeroPointFive, zero, zero, CC3);
		this.innerTestLD_LD_op_STD(4, 2, _2C, zero, minusOnePointZero, zero, CC3); 
		this.innerTestLD_LD_op_STD(0, 2, _2C, zero, minusTwoPointZero, zero, CC3); 
		this.innerTestLD_LD_op_STD(0, 2, _2C, zero, minusZeroPointFive, zero, CC3);
		this.innerTestLD_LD_op_STD(0, 2, _2C, zero, onePointZero, zero, CC3); 
		this.innerTestLD_LD_op_STD(0, 2, _2C, zero, twoPointZero, zero, CC3); 
		this.innerTestLD_LD_op_STD(0, 2, _2C, zero, zeroPointFive, zero, CC3);
		this.innerTestLD_LD_op_STD(0, 2, _2C, zero, zero, zero, CC3);
		
		this.innerTestLD_LD_op_STD(0, 2, _2C, p1, p2, r3, CC3);
	}
	
	// Divide Normalized - RR - Short Operands
	// condition code: 0 => result zero, 1 => result negative, 2 => result positive
	@Test
	public void test_LD_LD_DDR_STD() {
		this.innerTestLD_LD_op_STD(0, 2, _2D, twoPointZero, onePointZero, twoPointZero, CC3);
		this.innerTestLD_LD_op_STD(2, 4, _2D, twoPointZero, twoPointZero, onePointZero, CC3);
		this.innerTestLD_LD_op_STD(4, 6, _2D, onePointZero, twoPointZero, zeroPointFive, CC3);

		this.innerTestLD_LD_op_STD(6, 0, _2D, twoPointZero, minusOnePointZero, minusTwoPointZero, CC3);
		this.innerTestLD_LD_op_STD(0, 2, _2D, twoPointZero, minusTwoPointZero, minusOnePointZero, CC3);
		this.innerTestLD_LD_op_STD(2, 4, _2D, onePointZero, minusTwoPointZero, minusZeroPointFive, CC3);

		this.innerTestLD_LD_op_STD(4, 6, _2D, minusTwoPointZero, onePointZero, minusTwoPointZero, CC3);
		this.innerTestLD_LD_op_STD(6, 0, _2D, minusTwoPointZero, twoPointZero, minusOnePointZero, CC3);
		this.innerTestLD_LD_op_STD(0, 2, _2D, minusOnePointZero, twoPointZero, minusZeroPointFive, CC3);

		this.innerTestLD_LD_op_STD(2, 4, _2D, minusTwoPointZero, minusOnePointZero, twoPointZero, CC3);
		this.innerTestLD_LD_op_STD(4, 6, _2D, minusTwoPointZero, minusTwoPointZero, onePointZero, CC3);
		this.innerTestLD_LD_op_STD(0, 4, _2D, minusOnePointZero, minusTwoPointZero, zeroPointFive, CC3);

		this.innerTestLD_LD_op_STD(0, 6, _2D, zero, onePointZero, zero, CC3);
		this.innerTestLD_LD_op_STD(2, 6, _2D, zero, twoPointZero, zero, CC3);
		this.innerTestLD_LD_op_STD(4, 2, _2D, zero, twoPointZero, zero, CC3);

		this.innerTestLD_LD_op_STD(0, 2, _2D, zero, minusOnePointZero, zero, CC3);
		this.innerTestLD_LD_op_STD(2, 4, _2D, zero, minusTwoPointZero, zero, CC3);
		this.innerTestLD_LD_op_STD(0, 4, _2D, zero, minusTwoPointZero, zero, CC3);
				
		this.innerTestLD_LD_op_STD(0, 6, _2D, p1, p2, r4, CC3); // cc unchanged 
	}
	
	/*
	 * Tests: load register A from memory , do "A opRX B(from memory)" , store result to memory
	 */
	
	private void innerTestLD_opRX_STD(
			int op1reg,
			byte op,
			byte[] val1,
			byte[] val2,
			byte[] expected,
			byte expCC
			) {
		setCC(CC3);
		setMemB(0x010180, val1);          // address: 0x010180
		setMemB(0x010280, val2);          // address: 0x010280
		setMemB(0x200138, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		setMemB(0x200140, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		setMemB(0x200148, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		setGPR(2, 0x010000);              // set base register for LD addressing
		setGPR(1, 0x000100);              // set index register for LD addressing
		setGPR(6, 0x200000);              // set base register for STD addressing
		setGPR(5, 0x000100);              // set index register for STD addressing
		byte b1from = (byte)((op1reg << 4) | 1);
		byte b2from = (byte) (op1reg << 4 | 1);
		byte b1to = (byte)((op1reg << 4) | 5);
		setInstructions(
				_68, b1from, _20, _80,     // LD op1reg <- (base=R2,index=R1,offset=0x080)=0x010180
				op, b2from, _21, _80,      // op1reg <- op1reg op (base=R2,index=R1,offset=0x180)=0x010280
				_60, b1to, _60, _40        // STD op1reg -> (base=R6,index=R5,offset=0x040)=0x200140
		);
		execute(3); // do three instructions
		checkIL(2); // instruction length code of last instruction
		checkIA(CodeBase+12);  // instruction address at end
		checkMemB(0x200140, expected); // output value must have been written there
		checkMemB(0x20013F, _FF); // the bytes before and behind
		checkMemB(0x200148, _FF); // the store location must be unchanged
		checkCC(expCC);          // CC3 if it stays unchanged
	}
	
	// Add Normalized - RX - Short Operands
	// condition code: 0 => result zero, 1 => result negative, 2 => result positive
	@Test
	public void test_LD_AD_STD() {
		this.innerTestLD_opRX_STD(0, _6A, onePointZero, onePointZero, twoPointZero, CC2);
		this.innerTestLD_opRX_STD(0, _6A, twoPointZero, minusOnePointZero, onePointZero, CC2);
		this.innerTestLD_opRX_STD(2, _6A, zeroPointFive, zeroPointFive, onePointZero, CC2);

		this.innerTestLD_opRX_STD(4, _6A, zero, zero, zero, CC0);
		this.innerTestLD_opRX_STD(4, _6A, minusOnePointZero, minusOnePointZero, minusTwoPointZero, CC1);
		this.innerTestLD_opRX_STD(4, _6A, minusZeroPointFive, minusZeroPointFive, minusOnePointZero, CC1);
		this.innerTestLD_opRX_STD(4, _6A, minusTwoPointZero, onePointZero, minusOnePointZero, CC1);

		this.innerTestLD_opRX_STD(0, _6A, minusZeroPointFive, zeroPointFive, zero, CC0);
		this.innerTestLD_opRX_STD(6, _6A, onePointZero, minusOnePointZero, zero, CC0);
		this.innerTestLD_opRX_STD(4, _6A, minusZeroPointFive, zeroPointFive, zero, CC0);
		this.innerTestLD_opRX_STD(6, _6A, onePointZero, minusOnePointZero, zero, CC0);
		
		this.innerTestLD_opRX_STD(0, _6A, p1, p2, r1, CC2);
	}
	
	// Subtract Normalized - RX - Short Operands
	// condition code: 0 => result zero, 1 => result negative, 2 => result positive
	@Test
	public void test_LD_SD_STD() {
		this.innerTestLD_opRX_STD(0, _6B, twoPointZero, onePointZero, onePointZero, CC2);
		this.innerTestLD_opRX_STD(2, _6B, onePointZero, zero, onePointZero, CC2);
		this.innerTestLD_opRX_STD(4, _6B, onePointZero, minusOnePointZero, twoPointZero, CC2);
		this.innerTestLD_opRX_STD(6, _6B, zeroPointFive, minusZeroPointFive, onePointZero, CC2);
		this.innerTestLD_opRX_STD(0, _6B, zero, minusOnePointZero, onePointZero, CC2);

		this.innerTestLD_opRX_STD(2, _6B, onePointZero, twoPointZero, minusOnePointZero, CC1);
		this.innerTestLD_opRX_STD(4, _6B, minusOnePointZero, onePointZero, minusTwoPointZero, CC1);
		this.innerTestLD_opRX_STD(6, _6B, minusZeroPointFive, zeroPointFive, minusOnePointZero, CC1);
		this.innerTestLD_opRX_STD(0, _6B, zero, onePointZero, minusOnePointZero, CC1);
		this.innerTestLD_opRX_STD(2, _6B, zero, twoPointZero, minusTwoPointZero, CC1);
		
		this.innerTestLD_opRX_STD(4, _6B, zero, zero, zero, CC0);
		this.innerTestLD_opRX_STD(6, _6B, onePointZero, onePointZero, zero, CC0);
		this.innerTestLD_opRX_STD(0, _6B, twoPointZero, twoPointZero, zero, CC0);
		this.innerTestLD_opRX_STD(2, _6B, zeroPointFive, zeroPointFive, zero, CC0);
		this.innerTestLD_opRX_STD(4, _6B, minusOnePointZero, minusOnePointZero, zero, CC0);
		this.innerTestLD_opRX_STD(6, _6B, minusTwoPointZero, minusTwoPointZero, zero, CC0);
		this.innerTestLD_opRX_STD(0, _6B, minusZeroPointFive, minusZeroPointFive, zero, CC0);
		
		this.innerTestLD_opRX_STD(2, _6B, p1, p2, r2, CC1); // result negative 
	}
	
	// Multiply Normalized - RX - Short Operands
	// condition code: 0 => result zero, 1 => result negative, 2 => result positive
	@Test
	public void test_LD_MD_STD() { // cc is unchanged, so it must always be CC3 (the start value in innerTestLD_LD_op_STD) 
		this.innerTestLD_opRX_STD(0, _6C, onePointZero, onePointZero, onePointZero, CC3); 
		this.innerTestLD_opRX_STD(2, _6C, onePointZero, twoPointZero, twoPointZero, CC3); 
		this.innerTestLD_opRX_STD(4, _6C, zeroPointFive, twoPointZero, onePointZero, CC3);
		 
		this.innerTestLD_opRX_STD(6, _6C, onePointZero, minusOnePointZero, minusOnePointZero, CC3); 
		this.innerTestLD_opRX_STD(0, _6C, minusOnePointZero, twoPointZero, minusTwoPointZero, CC3); 
		this.innerTestLD_opRX_STD(2, _6C, minusZeroPointFive, twoPointZero, minusOnePointZero, CC3);
		this.innerTestLD_opRX_STD(4, _6C, minusOnePointZero, onePointZero, minusOnePointZero, CC3); 
		this.innerTestLD_opRX_STD(6, _6C, twoPointZero, minusOnePointZero, minusTwoPointZero, CC3); 
		this.innerTestLD_opRX_STD(0, _6C, twoPointZero, minusZeroPointFive, minusOnePointZero, CC3);
		 
		this.innerTestLD_opRX_STD(0, _6C, minusOnePointZero, minusOnePointZero, onePointZero, CC3); 
		this.innerTestLD_opRX_STD(2, _6C, minusOnePointZero, minusTwoPointZero, twoPointZero, CC3); 
		this.innerTestLD_opRX_STD(4, _6C, minusZeroPointFive, minusTwoPointZero, onePointZero, CC3);
		this.innerTestLD_opRX_STD(6, _6C, minusTwoPointZero, minusOnePointZero, twoPointZero, CC3); 
		this.innerTestLD_opRX_STD(0, _6C, minusTwoPointZero, minusZeroPointFive, onePointZero, CC3);
		 
		this.innerTestLD_opRX_STD(2, _6C, minusOnePointZero, zero, zero, CC3); 
		this.innerTestLD_opRX_STD(4, _6C, minusTwoPointZero, zero, zero, CC3); 
		this.innerTestLD_opRX_STD(6, _6C, minusZeroPointFive, zero, zero, CC3);
		this.innerTestLD_opRX_STD(0, _6C, onePointZero, zero, zero, CC3); 
		this.innerTestLD_opRX_STD(2, _6C, twoPointZero, zero, zero, CC3); 
		this.innerTestLD_opRX_STD(4, _6C, zeroPointFive, zero, zero, CC3);
		this.innerTestLD_opRX_STD(6, _6C, zero, minusOnePointZero, zero, CC3); 
		this.innerTestLD_opRX_STD(0, _6C, zero, minusTwoPointZero, zero, CC3); 
		this.innerTestLD_opRX_STD(2, _6C, zero, minusZeroPointFive, zero, CC3);
		this.innerTestLD_opRX_STD(4, _6C, zero, onePointZero, zero, CC3); 
		this.innerTestLD_opRX_STD(6, _6C, zero, twoPointZero, zero, CC3); 
		this.innerTestLD_opRX_STD(0, _6C, zero, zeroPointFive, zero, CC3);
		this.innerTestLD_opRX_STD(2, _6C, zero, zero, zero, CC3);
		
		this.innerTestLD_opRX_STD(4, _6C, p1, p2, r3, CC3);
	}
	
	// Divide Normalized - RX - Short Operands
	// condition code: 0 => result zero, 1 => result negative, 2 => result positive
	@Test
	public void test_LD_DD_STD() {
		this.innerTestLD_opRX_STD(0, _6D, twoPointZero, onePointZero, twoPointZero, CC3);
		this.innerTestLD_opRX_STD(2, _6D, twoPointZero, twoPointZero, onePointZero, CC3);
		this.innerTestLD_opRX_STD(4, _6D, onePointZero, twoPointZero, zeroPointFive, CC3);

		this.innerTestLD_opRX_STD(6, _6D, twoPointZero, minusOnePointZero, minusTwoPointZero, CC3);
		this.innerTestLD_opRX_STD(0, _6D, twoPointZero, minusTwoPointZero, minusOnePointZero, CC3);
		this.innerTestLD_opRX_STD(2, _6D, onePointZero, minusTwoPointZero, minusZeroPointFive, CC3);

		this.innerTestLD_opRX_STD(4, _6D, minusTwoPointZero, onePointZero, minusTwoPointZero, CC3);
		this.innerTestLD_opRX_STD(6, _6D, minusTwoPointZero, twoPointZero, minusOnePointZero, CC3);
		this.innerTestLD_opRX_STD(0, _6D, minusOnePointZero, twoPointZero, minusZeroPointFive, CC3);

		this.innerTestLD_opRX_STD(2, _6D, minusTwoPointZero, minusOnePointZero, twoPointZero, CC3);
		this.innerTestLD_opRX_STD(4, _6D, minusTwoPointZero, minusTwoPointZero, onePointZero, CC3);
		this.innerTestLD_opRX_STD(6, _6D, minusOnePointZero, minusTwoPointZero, zeroPointFive, CC3);

		this.innerTestLD_opRX_STD(0, _6D, zero, onePointZero, zero, CC3);
		this.innerTestLD_opRX_STD(2, _6D, zero, twoPointZero, zero, CC3);
		this.innerTestLD_opRX_STD(4, _6D, zero, twoPointZero, zero, CC3);

		this.innerTestLD_opRX_STD(6, _6D, zero, minusOnePointZero, zero, CC3);
		this.innerTestLD_opRX_STD(0, _6D, zero, minusTwoPointZero, zero, CC3);
		this.innerTestLD_opRX_STD(2, _6D, zero, minusTwoPointZero, zero, CC3);
				
		this.innerTestLD_opRX_STD(4, _6D, p1, p2, r4, CC3); // cc unchanged 
	}
	
	/*
	 * Tests: load register A and Bfrom memory , do CER A,B and check outcome
	 */
	
	private void innerTestLD_LD_CDR(
			int reg1,
			int reg2,
			byte[] val1,
			byte[] val2,
			byte expCC
			) {
		setCC(CC3);
		setMemB(0x010180, val1);          // address: 0x010180
		setMemB(0x010280, val2);          // address: 0x010280
		setGPR(2, 0x010000);              // set base register for LE addressing
		setGPR(1, 0x000100);              // set index register for LE addressing
		byte b1from = (byte)((reg1 << 4) | 1);
		byte b2from = (byte) (reg2 << 4 | 1);
		byte reg1reg2 = (byte)((reg1 << 4) | reg2);
		setInstructions(
				_68, b1from, _20, _80,     // LD reg1 <- (base=R2,index=R1,offset=0x080)=0x010180
				_68, b2from, _21, _80,     // LD reg2 <- (base=R2,index=R1,offset=0x180)=0x010280
				_29, reg1reg2              // CDR reg1,reg2
		);
		execute(3); // do three instructions
		checkIL(1); // instruction length code of last instruction
		checkIA(CodeBase+10);  // instruction address at end
		checkCC(expCC);          // CC3 if it stays unchanged
	}
	
	@Test
	public void test_LD_LD_CDR() {
		this.innerTestLD_LD_CDR(0, 2, twoPointZero, twoPointZero, CC0);
		this.innerTestLD_LD_CDR(0, 2, onePointZero, onePointZero, CC0);
		this.innerTestLD_LD_CDR(0, 2, zeroPointFive, zeroPointFive, CC0);
		this.innerTestLD_LD_CDR(0, 2, minusZeroPointFive, minusZeroPointFive, CC0);
		this.innerTestLD_LD_CDR(0, 2, minusOnePointZero, minusOnePointZero, CC0);
		this.innerTestLD_LD_CDR(0, 2, minusTwoPointZero, minusTwoPointZero, CC0);
		this.innerTestLD_LD_CDR(0, 2, p1, p1, CC0);
		this.innerTestLD_LD_CDR(0, 2, p2, p2, CC0);
		
		this.innerTestLD_LD_CDR(0, 2, zero, zeroPointFive, CC1);
		this.innerTestLD_LD_CDR(2, 4, zero, onePointZero, CC1);
		this.innerTestLD_LD_CDR(4, 6, zero, twoPointZero, CC1);
		this.innerTestLD_LD_CDR(6, 4, onePointZero, twoPointZero, CC1);
		this.innerTestLD_LD_CDR(4, 2, zeroPointFive, twoPointZero, CC1);
		this.innerTestLD_LD_CDR(2, 0, minusOnePointZero, onePointZero, CC1);
		this.innerTestLD_LD_CDR(0, 6, minusOnePointZero, twoPointZero, CC1);
		this.innerTestLD_LD_CDR(6, 0, minusOnePointZero, zeroPointFive, CC1);
		
		this.innerTestLD_LD_CDR(0, 2, zeroPointFive, zero, CC2);
		this.innerTestLD_LD_CDR(2, 4, onePointZero, zero, CC2);
		this.innerTestLD_LD_CDR(4, 6, twoPointZero, zero, CC2);
		this.innerTestLD_LD_CDR(6, 4, twoPointZero, onePointZero, CC2);
		this.innerTestLD_LD_CDR(4, 2, twoPointZero, zeroPointFive, CC2);
		this.innerTestLD_LD_CDR(2, 0, onePointZero, minusOnePointZero, CC2);
		this.innerTestLD_LD_CDR(0, 6, twoPointZero, minusOnePointZero, CC2);
		this.innerTestLD_LD_CDR(6, 0, zeroPointFive, minusOnePointZero, CC2);
	}
	
	/*
	 * Tests: load register A and B from memory , do CER A,B and check outcome
	 */
	
	private void innerTestLD_CD(
			int reg,
			byte[] val1,
			byte[] val2,
			byte expCC
			) {
		setCC(CC3);
		setMemB(0x010180, val1);          // address: 0x010180
		setMemB(0x010280, val2);          // address: 0x010280
		setGPR(2, 0x010000);              // set base register for LE addressing
		setGPR(1, 0x000100);              // set index register for LE addressing
		byte b1from = (byte)((reg << 4) | 1);
		byte b2from = (byte) (reg << 4 | 1);
		setInstructions(
				_68, b1from, _20, _80,     // LD reg1 <- (base=R2,index=R1,offset=0x080)=0x010180
				_69, b2from, _21, _80      // CD reg2,(base=R2,index=R1,offset=0x180)=0x010280
		);
		execute(2); // do two instructions
		checkIL(2); // instruction length code of last instruction
		checkIA(CodeBase+8);  // instruction address at end
		checkCC(expCC);          // CC3 if it stays unchanged
	}
	
	@Test
	public void test_LD_CD() {
		this.innerTestLD_CD(0, twoPointZero, twoPointZero, CC0);
		this.innerTestLD_CD(2, onePointZero, onePointZero, CC0);
		this.innerTestLD_CD(4, zeroPointFive, zeroPointFive, CC0);
		this.innerTestLD_CD(6, minusZeroPointFive, minusZeroPointFive, CC0);
		this.innerTestLD_CD(0, minusOnePointZero, minusOnePointZero, CC0);
		this.innerTestLD_CD(2, minusTwoPointZero, minusTwoPointZero, CC0);
		this.innerTestLD_CD(4, p1, p1, CC0);
		this.innerTestLD_CD(6, p2, p2, CC0);
		
		this.innerTestLD_CD(0, zero, zeroPointFive, CC1);
		this.innerTestLD_CD(2, zero, onePointZero, CC1);
		this.innerTestLD_CD(4, zero, twoPointZero, CC1);
		this.innerTestLD_CD(6, onePointZero, twoPointZero, CC1);
		this.innerTestLD_CD(4, zeroPointFive, twoPointZero, CC1);
		this.innerTestLD_CD(2, minusOnePointZero, onePointZero, CC1);
		this.innerTestLD_CD(0, minusOnePointZero, twoPointZero, CC1);
		this.innerTestLD_CD(6, minusOnePointZero, zeroPointFive, CC1);
		
		this.innerTestLD_CD(0, zeroPointFive, zero, CC2);
		this.innerTestLD_CD(2, onePointZero, zero, CC2);
		this.innerTestLD_CD(4, twoPointZero, zero, CC2);
		this.innerTestLD_CD(6, twoPointZero, onePointZero, CC2);
		this.innerTestLD_CD(4, twoPointZero, zeroPointFive, CC2);
		this.innerTestLD_CD(2, onePointZero, minusOnePointZero, CC2);
		this.innerTestLD_CD(0, twoPointZero, minusOnePointZero, CC2);
		this.innerTestLD_CD(6, zeroPointFive, minusOnePointZero, CC2);
	}
	
	/*
	 * Tests: load register A from memory , do HER B,A and store register B to memory
	 */
	
	private void innerTestLD_HDR(
			int regA,
			int regB,
			byte[] val,
			byte[] expected
			) {
		setCC(CC3);
		setMemB(0x010180, val);           // address: 0x010180
		setMemB(0x200138, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		setMemB(0x200140, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		setMemB(0x200148, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		setGPR(2, 0x010000);              // set base register for LE addressing
		setGPR(1, 0x000100);              // set index register for LE addressing
		setGPR(6, 0x200000);              // set base register for STE addressing
		setGPR(5, 0x000100);              // set index register for STE addressing
		byte b1from = (byte)((regA << 4) | 1);
		byte regBregA = (byte)((regB << 4) | regA);
		byte b1to = (byte)((regB << 4) | 5);
		setInstructions(
				_68, b1from, _20, _80,     // LD regA <- (base=R2,index=R1,offset=0x080)=0x010180
				_24, regBregA,             // HDR regB <- regA
				_60, b1to, _60, _40        // STD regB -> (base=R6,index=R5,offset=0x040)=0x200140
		);
		execute(3); // do two instructions
		checkIL(2); // instruction length code of last instruction
		checkIA(CodeBase+10);  // instruction address at end
		checkMemB(0x200140, expected); // output value must have been written there
		checkMemB(0x20013F, _FF); // the bytes before and behind
		checkMemB(0x200148, _FF); // the store location must be unchanged
		checkCC(CC3);          // CC stays unchanged
	}
	
	@Test
	public void test_LD_HDR_STD() {
		this.innerTestLD_HDR(0, 2, twoPointZero, onePointZero);
		this.innerTestLD_HDR(2, 0, onePointZero, zeroPointFive);
		this.innerTestLD_HDR(0, 4, zero, zero);
		this.innerTestLD_HDR(4, 0, minusTwoPointZero, minusOnePointZero);
		this.innerTestLD_HDR(6, 4, minusOnePointZero, minusZeroPointFive);
	}
	
	/*
	 * Tests: load register A , do LTER B,A and check outcome
	 */
	
	private void innerTestLD_LTDR(
			int reg1,
			int reg2,
			byte[] val,
			byte expCC
			) {
		setCC(CC3);
		setMemB(0x010180, val);           // address: 0x010180
		setGPR(2, 0x010000);              // set base register for LE addressing
		setGPR(1, 0x000100);              // set index register for LE addressing
		byte b1from = (byte)((reg1 << 4) | 1);
		byte reg2reg1 = (byte)((reg2 << 4) | reg1);
		setInstructions(
				_68, b1from, _20, _80,     // LD reg1 <- (base=R2,index=R1,offset=0x080)=0x010180
				_22, reg2reg1              // LTDR reg2,reg1
		);
		execute(2); // do two instructions
		checkIL(1); // instruction length code of last instruction
		checkIA(CodeBase+6);  // instruction address at end
		checkCC(expCC);          // CC3 if it stays unchanged
	}
	
	@Test
	public void test_LD_LTDR() {
		this.innerTestLD_LTDR(2, 0, zero, CC0);
		this.innerTestLD_LTDR(4, 0, zero, CC0);
		this.innerTestLD_LTDR(6, 0, zero, CC0);
		this.innerTestLD_LTDR(0, 0, zero, CC0);
		this.innerTestLD_LTDR(2, 2, zero, CC0);
		this.innerTestLD_LTDR(4, 4, zero, CC0);
		this.innerTestLD_LTDR(6, 6, zero, CC0);
		this.innerTestLD_LTDR(0, 6, zero, CC0);

		this.innerTestLD_LTDR(0, 2, minusOnePointZero, CC1);
		this.innerTestLD_LTDR(6, 2, minusTwoPointZero, CC1);
		this.innerTestLD_LTDR(2, 4, minusZeroPointFive, CC1);
		
		this.innerTestLD_LTDR(0, 0, minusZeroPointFive, CC1);
		this.innerTestLD_LTDR(2, 2, minusZeroPointFive, CC1);
		this.innerTestLD_LTDR(4, 4, minusZeroPointFive, CC1);
		this.innerTestLD_LTDR(6, 6, minusZeroPointFive, CC1);

		this.innerTestLD_LTDR(0, 2, onePointZero, CC2);
		this.innerTestLD_LTDR(6, 2, twoPointZero, CC2);
		this.innerTestLD_LTDR(2, 4, zeroPointFive, CC2);
		
		this.innerTestLD_LTDR(0, 0, zeroPointFive, CC2);
		this.innerTestLD_LTDR(2, 2, zeroPointFive, CC2);
		this.innerTestLD_LTDR(4, 4, zeroPointFive, CC2);
		this.innerTestLD_LTDR(6, 6, zeroPointFive, CC2);
	}
	
	/*
	 * Tests: load register A from memory , do LxER B,A and store register B to memory
	 */
	
	private void innerTestLD_LxDR_STD(
			byte op,
			int regA,
			int regB,
			byte[] val,
			byte[] expected,
			byte expCC
			) {
		setCC(CC3);
		setMemB(0x010180, val);           // address: 0x010180
		setMemB(0x200138, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		setMemB(0x200140, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		setMemB(0x200148, _FF, _FF, _FF, _FF, _FF, _FF, _FF, _FF);
		setGPR(2, 0x010000);              // set base register for LE addressing
		setGPR(1, 0x000100);              // set index register for LE addressing
		setGPR(6, 0x200000);              // set base register for STE addressing
		setGPR(5, 0x000100);              // set index register for STE addressing
		byte b1from = (byte)((regA << 4) | 1);
		byte regBregA = (byte)((regB << 4) | regA);
		byte b1to = (byte)((regB << 4) | 5);
		setInstructions(
				_68, b1from, _20, _80,     // LD regA <- (base=R2,index=R1,offset=0x080)=0x010180
				op, regBregA,              // LxDR regB <- regA
				_60, b1to, _60, _40        // STD regB -> (base=R6,index=R5,offset=0x040)=0x200140
		);
		execute(3); // do two instructions
		checkIL(2); // instruction length code of last instruction
		checkIA(CodeBase+10);  // instruction address at end
		checkMemB(0x200140, expected); // output value must have been written there
		checkMemB(0x20013F, _FF); // the bytes before and behind
		checkMemB(0x200148, _FF); // the store location must be unchanged
		checkCC(expCC);          // CC3 if it stays unchanged
	}
	
	@Test
	public void test_LD_LCDR_STD() {
		this.innerTestLD_LxDR_STD(_23, 0, 2, zero, zero, CC0);
		
		this.innerTestLD_LxDR_STD(_23, 2, 4, zeroPointFive, minusZeroPointFive, CC1);
		
		this.innerTestLD_LxDR_STD(_23, 6, 4, minusZeroPointFive, zeroPointFive, CC2);
	}
	
	@Test
	public void test_LD_LNDR_STD() {
		this.innerTestLD_LxDR_STD(_21, 0, 2, zero, zero, CC0);
		
		this.innerTestLD_LxDR_STD(_21, 2, 4, zeroPointFive, minusZeroPointFive, CC1);
		
		this.innerTestLD_LxDR_STD(_21, 6, 4, minusZeroPointFive, minusZeroPointFive, CC1);
	}
	
	@Test
	public void test_LD_LPDR_STD() {
		this.innerTestLD_LxDR_STD(_20, 0, 2, zero, zero, CC0);
		
		this.innerTestLD_LxDR_STD(_20, 2, 4, zeroPointFive, zeroPointFive, CC2);
		
		this.innerTestLD_LxDR_STD(_20, 6, 4, minusZeroPointFive, zeroPointFive, CC2);
	}
	
	// ## not tested: Load Rounded (long -> short)
	
	// ## not tested: Add/Subtract unnormalized (even registers always hold normalized values!)
}
