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
public class Cpu370BcTest_FloatSingle extends AbstractCpu370BcTest {
	
	public Cpu370BcTest_FloatSingle(Class<? extends Cpu370Bc> cpuClass) {
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

	private byte[] zero               = { _00, _00, _00, _00 };
	private byte[] onePointZero       = { _41, _10, _00, _00 };
	private byte[] minusOnePointZero  = { _C1, _10, _00, _00 };
	private byte[] twoPointZero       = { _41, _20, _00, _00 };
	private byte[] minusTwoPointZero  = { _C1, _20, _00, _00 };
	private byte[] zeroPointFive      = { _40, _80, _00, _00 };
	private byte[] minusZeroPointFive = { _C0, _80, _00, _00 };
	
	/*
	 * Tests: load register from memory , then store same register to memory 
	 */
	
	private void innerTestLE_STE(int freg, byte[] value) {
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
		byte b1to = (byte)((freg << 4) | 5);
		setInstructions(
				_78, b1from, _20, _80,     // LE R0 <- (base=R2,index=R1,offset=0x080)=0x010180
				_70, b1to, _60, _40        // STE R0 -> (base=R6,index=R5,offset=0x040)=0x200140
		);
		execute(2); // do two instructions
		checkIL(2); // instruction length code of last instruction
		checkIA(CodeBase+8);     // instruction address at end
		checkMemB(0x010180, value); // input value must stay unchanged
		checkMemB(0x200140, value); // output value must have been written there
		checkMemB(0x20013F, _FF); // the bytes before and behind
		checkMemB(0x200144, _FF); // the store location must be unchanged
		checkCC(CC0);            // must stay unchanged
	}
	
	@Test
	public void base_LE_STE_One() {
		this.innerTestLE_STE(0, onePointZero);
		this.innerTestLE_STE(2, onePointZero);
		this.innerTestLE_STE(4, onePointZero);
		this.innerTestLE_STE(6, onePointZero);
	}
	
	@Test
	public void base_LE_STE_Two() {
		this.innerTestLE_STE(0, twoPointZero);
		this.innerTestLE_STE(2, twoPointZero);
		this.innerTestLE_STE(4, twoPointZero);
		this.innerTestLE_STE(6, twoPointZero);
	}
	
	/*
	 * Tests: load register A from memory , load register B from register A , store register B to memory
	 */
	
	private void innerTestLE_LER_STE(int freg, int viaReg, byte[] value) {
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
				_78, b1from, _20, _80,     // LE freg <- (base=R2,index=R1,offset=0x080)=0x010180
				_38, regToFrom,            // LER viaReg <- freg
				_70, b1to, _60, _40        // STE viaReg -> (base=R6,index=R5,offset=0x040)=0x200140
		);
		execute(3); // do three instructions
		checkIL(2); // instruction length code of last instruction
		checkIA(CodeBase+10);     // instruction address at end
		checkMemB(0x010180, value); // input value must stay unchanged
		checkMemB(0x200140, value); // output value must have been writtern there
		checkMemB(0x20013F, _FF); // the bytes before and behind
		checkMemB(0x200144, _FF); // the store location must be unchanged
		checkCC(CC0);            // must stay unchanged
	}
	
	@Test
	public void base_LE_LER_STE_One() {
		this.innerTestLE_LER_STE(0, 2, onePointZero);
		this.innerTestLE_LER_STE(2, 4, onePointZero);
		this.innerTestLE_LER_STE(4, 6, onePointZero);
		this.innerTestLE_LER_STE(6, 0, onePointZero);
	}
	
	@Test
	public void base_LE_LER_STE_Two() {
		this.innerTestLE_LER_STE(0, 6, twoPointZero);
		this.innerTestLE_LER_STE(2, 4, twoPointZero);
		this.innerTestLE_LER_STE(4, 2, twoPointZero);
		this.innerTestLE_LER_STE(6, 0, twoPointZero);
	}
	
	/*
	 * Tests: load register A and B from memory , do "A opRR B" , store result to memory
	 */
	
	private byte[] p1 = { _42 , _14 , _00 , _00 }; // +20.0 
	private byte[] p2 = { _42 , _1F , _1F , _97 }; // +31.123398 (intended: +31.1234)
	private byte[] r1 = { _42 , _33 , _1F , _97 }; // +51.123398 (intended: +51.1234) = p1 + p2
	private byte[] r2 = { _C1 , _B1 , _F9 , _70 }; // -11.123398 (intended: -11.1234) = p1 - p2
	private byte[] r3 = { _43 , _26 , _E7 , _7C }; // +622.46773 (intended: +622.468) = p1 * p2
	private byte[] r4 = { _40 , _A4 , _81 , _A7 }; // +0.642603  (intended: +0.64260) = p1 / p2

	private void innerTestLE_LE_op_STE(
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
				_78, b1from, _20, _80,     // LE op1reg <- (base=R2,index=R1,offset=0x080)=0x010180
				_78, b2from, _21, _80,     // LE op2reg <- (base=R2,index=R1,offset=0x180)=0x010280
				op, regop1op2,             // op1reg <- op1reg op op2reg
				_70, b1to, _60, _40        // STE op1reg -> (base=R6,index=R5,offset=0x040)=0x200140
		);
		execute(4); // do four instructions
		checkIL(2); // instruction length code of last instruction
		checkIA(CodeBase+14);  // instruction address at end
		checkMemB(0x200140, expected); // output value must have been written there
		checkMemB(0x20013F, _FF); // the bytes before and behind
		checkMemB(0x200144, _FF); // the store location must be unchanged
		checkCC(expCC);          // CC3 if it stays unchanged
	}
	
	// Add Normalized - RR - Short Operands
	// condition code: 0 => result zero, 1 => result negative, 2 => result positive
	@Test
	public void test_LE_LE_AER_STE() {
		this.innerTestLE_LE_op_STE(0, 2, _3A, onePointZero, onePointZero, twoPointZero, CC2);
		this.innerTestLE_LE_op_STE(0, 4, _3A, twoPointZero, minusOnePointZero, onePointZero, CC2);
		this.innerTestLE_LE_op_STE(2, 0, _3A, zeroPointFive, zeroPointFive, onePointZero, CC2);

		this.innerTestLE_LE_op_STE(4, 6, _3A, zero, zero, zero, CC0);
		this.innerTestLE_LE_op_STE(4, 6, _3A, minusOnePointZero, minusOnePointZero, minusTwoPointZero, CC1);
		this.innerTestLE_LE_op_STE(4, 6, _3A, minusZeroPointFive, minusZeroPointFive, minusOnePointZero, CC1);
		this.innerTestLE_LE_op_STE(4, 2, _3A, minusTwoPointZero, onePointZero, minusOnePointZero, CC1);

		this.innerTestLE_LE_op_STE(0, 6, _3A, minusZeroPointFive, zeroPointFive, zero, CC0);
		this.innerTestLE_LE_op_STE(6, 2, _3A, onePointZero, minusOnePointZero, zero, CC0);
		this.innerTestLE_LE_op_STE(4, 6, _3A, minusZeroPointFive, zeroPointFive, zero, CC0);
		this.innerTestLE_LE_op_STE(6, 0, _3A, onePointZero, minusOnePointZero, zero, CC0);
		
		this.innerTestLE_LE_op_STE(0, 2, _3A, p1, p2, r1, CC2);
	}
	
	// Subtract Normalized - RR - Short Operands
	// condition code: 0 => result zero, 1 => result negative, 2 => result positive
	@Test
	public void test_LE_LE_SER_STE() {
		this.innerTestLE_LE_op_STE(0, 2, _3B, twoPointZero, onePointZero, onePointZero, CC2);
		this.innerTestLE_LE_op_STE(0, 2, _3B, onePointZero, zero, onePointZero, CC2);
		this.innerTestLE_LE_op_STE(0, 2, _3B, onePointZero, minusOnePointZero, twoPointZero, CC2);
		this.innerTestLE_LE_op_STE(0, 2, _3B, zeroPointFive, minusZeroPointFive, onePointZero, CC2);
		this.innerTestLE_LE_op_STE(0, 2, _3B, zero, minusOnePointZero, onePointZero, CC2);

		this.innerTestLE_LE_op_STE(0, 2, _3B, onePointZero, twoPointZero, minusOnePointZero, CC1);
		this.innerTestLE_LE_op_STE(0, 2, _3B, minusOnePointZero, onePointZero, minusTwoPointZero, CC1);
		this.innerTestLE_LE_op_STE(0, 2, _3B, minusZeroPointFive, zeroPointFive, minusOnePointZero, CC1);
		this.innerTestLE_LE_op_STE(0, 2, _3B, zero, onePointZero, minusOnePointZero, CC1);
		this.innerTestLE_LE_op_STE(0, 2, _3B, zero, twoPointZero, minusTwoPointZero, CC1);
		
		this.innerTestLE_LE_op_STE(0, 2, _3B, zero, zero, zero, CC0);
		this.innerTestLE_LE_op_STE(0, 2, _3B, onePointZero, onePointZero, zero, CC0);
		this.innerTestLE_LE_op_STE(0, 2, _3B, twoPointZero, twoPointZero, zero, CC0);
		this.innerTestLE_LE_op_STE(0, 2, _3B, zeroPointFive, zeroPointFive, zero, CC0);
		this.innerTestLE_LE_op_STE(0, 2, _3B, minusOnePointZero, minusOnePointZero, zero, CC0);
		this.innerTestLE_LE_op_STE(0, 2, _3B, minusTwoPointZero, minusTwoPointZero, zero, CC0);
		this.innerTestLE_LE_op_STE(0, 2, _3B, minusZeroPointFive, minusZeroPointFive, zero, CC0);
		
		this.innerTestLE_LE_op_STE(0, 2, _3B, p1, p2, r2, CC1); // result negative 
	}
	
	// Multiply Normalized - RR - Short Operands
	// condition code: 0 => result zero, 1 => result negative, 2 => result positive
	@Test
	public void test_LE_LE_MER_STE() { // cc is unchanged, so it must always be CC3 (the start value in innerTestLE_LE_op_STE) 
		this.innerTestLE_LE_op_STE(0, 2, _3C, onePointZero, onePointZero, onePointZero, CC3); 
		this.innerTestLE_LE_op_STE(0, 4, _3C, onePointZero, twoPointZero, twoPointZero, CC3); 
		this.innerTestLE_LE_op_STE(0, 6, _3C, zeroPointFive, twoPointZero, onePointZero, CC3);
		 
		this.innerTestLE_LE_op_STE(2, 4, _3C, onePointZero, minusOnePointZero, minusOnePointZero, CC3); 
		this.innerTestLE_LE_op_STE(2, 6, _3C, minusOnePointZero, twoPointZero, minusTwoPointZero, CC3); 
		this.innerTestLE_LE_op_STE(4, 6, _3C, minusZeroPointFive, twoPointZero, minusOnePointZero, CC3);
		this.innerTestLE_LE_op_STE(6, 4, _3C, minusOnePointZero, onePointZero, minusOnePointZero, CC3); 
		this.innerTestLE_LE_op_STE(6, 2, _3C, twoPointZero, minusOnePointZero, minusTwoPointZero, CC3); 
		this.innerTestLE_LE_op_STE(6, 0, _3C, twoPointZero, minusZeroPointFive, minusOnePointZero, CC3);
		 
		this.innerTestLE_LE_op_STE(0, 2, _3C, minusOnePointZero, minusOnePointZero, onePointZero, CC3); 
		this.innerTestLE_LE_op_STE(0, 4, _3C, minusOnePointZero, minusTwoPointZero, twoPointZero, CC3); 
		this.innerTestLE_LE_op_STE(0, 6, _3C, minusZeroPointFive, minusTwoPointZero, onePointZero, CC3);
		this.innerTestLE_LE_op_STE(2, 4, _3C, minusTwoPointZero, minusOnePointZero, twoPointZero, CC3); 
		this.innerTestLE_LE_op_STE(4, 6, _3C, minusTwoPointZero, minusZeroPointFive, onePointZero, CC3);
		 
		this.innerTestLE_LE_op_STE(2, 6, _3C, minusOnePointZero, zero, zero, CC3); 
		this.innerTestLE_LE_op_STE(2, 0, _3C, minusTwoPointZero, zero, zero, CC3); 
		this.innerTestLE_LE_op_STE(4, 0, _3C, minusZeroPointFive, zero, zero, CC3);
		this.innerTestLE_LE_op_STE(6, 0, _3C, onePointZero, zero, zero, CC3); 
		this.innerTestLE_LE_op_STE(6, 2, _3C, twoPointZero, zero, zero, CC3); 
		this.innerTestLE_LE_op_STE(6, 4, _3C, zeroPointFive, zero, zero, CC3);
		this.innerTestLE_LE_op_STE(4, 2, _3C, zero, minusOnePointZero, zero, CC3); 
		this.innerTestLE_LE_op_STE(0, 2, _3C, zero, minusTwoPointZero, zero, CC3); 
		this.innerTestLE_LE_op_STE(0, 2, _3C, zero, minusZeroPointFive, zero, CC3);
		this.innerTestLE_LE_op_STE(0, 2, _3C, zero, onePointZero, zero, CC3); 
		this.innerTestLE_LE_op_STE(0, 2, _3C, zero, twoPointZero, zero, CC3); 
		this.innerTestLE_LE_op_STE(0, 2, _3C, zero, zeroPointFive, zero, CC3);
		this.innerTestLE_LE_op_STE(0, 2, _3C, zero, zero, zero, CC3);
		
		this.innerTestLE_LE_op_STE(0, 2, _3C, p1, p2, r3, CC3);
	}
	
	// Divide Normalized - RR - Short Operands
	// condition code: 0 => result zero, 1 => result negative, 2 => result positive
	@Test
	public void test_LE_LE_DER_STE() {
		this.innerTestLE_LE_op_STE(0, 2, _3D, twoPointZero, onePointZero, twoPointZero, CC3);
		this.innerTestLE_LE_op_STE(2, 4, _3D, twoPointZero, twoPointZero, onePointZero, CC3);
		this.innerTestLE_LE_op_STE(4, 6, _3D, onePointZero, twoPointZero, zeroPointFive, CC3);

		this.innerTestLE_LE_op_STE(6, 0, _3D, twoPointZero, minusOnePointZero, minusTwoPointZero, CC3);
		this.innerTestLE_LE_op_STE(0, 2, _3D, twoPointZero, minusTwoPointZero, minusOnePointZero, CC3);
		this.innerTestLE_LE_op_STE(2, 4, _3D, onePointZero, minusTwoPointZero, minusZeroPointFive, CC3);

		this.innerTestLE_LE_op_STE(4, 6, _3D, minusTwoPointZero, onePointZero, minusTwoPointZero, CC3);
		this.innerTestLE_LE_op_STE(6, 0, _3D, minusTwoPointZero, twoPointZero, minusOnePointZero, CC3);
		this.innerTestLE_LE_op_STE(0, 2, _3D, minusOnePointZero, twoPointZero, minusZeroPointFive, CC3);

		this.innerTestLE_LE_op_STE(2, 4, _3D, minusTwoPointZero, minusOnePointZero, twoPointZero, CC3);
		this.innerTestLE_LE_op_STE(4, 6, _3D, minusTwoPointZero, minusTwoPointZero, onePointZero, CC3);
		this.innerTestLE_LE_op_STE(0, 4, _3D, minusOnePointZero, minusTwoPointZero, zeroPointFive, CC3);

		this.innerTestLE_LE_op_STE(0, 6, _3D, zero, onePointZero, zero, CC3);
		this.innerTestLE_LE_op_STE(2, 6, _3D, zero, twoPointZero, zero, CC3);
		this.innerTestLE_LE_op_STE(4, 2, _3D, zero, twoPointZero, zero, CC3);

		this.innerTestLE_LE_op_STE(0, 2, _3D, zero, minusOnePointZero, zero, CC3);
		this.innerTestLE_LE_op_STE(2, 4, _3D, zero, minusTwoPointZero, zero, CC3);
		this.innerTestLE_LE_op_STE(0, 4, _3D, zero, minusTwoPointZero, zero, CC3);
				
		this.innerTestLE_LE_op_STE(0, 6, _3D, p1, p2, r4, CC3); // cc unchanged 
	}
	
	/*
	 * Tests: load register A from memory , do "A opRX B(from memory)" , store result to memory
	 */
	
	private void innerTestLE_opRX_STE(
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
		setGPR(2, 0x010000);              // set base register for LE addressing
		setGPR(1, 0x000100);              // set index register for LE addressing
		setGPR(6, 0x200000);              // set base register for STE addressing
		setGPR(5, 0x000100);              // set index register for STE addressing
		byte b1from = (byte)((op1reg << 4) | 1);
		byte b2from = (byte) (op1reg << 4 | 1);
		byte b1to = (byte)((op1reg << 4) | 5);
		setInstructions(
				_78, b1from, _20, _80,     // LE op1reg <- (base=R2,index=R1,offset=0x080)=0x010180
				op, b2from, _21, _80,      // op1reg <- op1reg op (base=R2,index=R1,offset=0x180)=0x010280
				_70, b1to, _60, _40        // STE op1reg -> (base=R6,index=R5,offset=0x040)=0x200140
		);
		execute(3); // do three instructions
		checkIL(2); // instruction length code of last instruction
		checkIA(CodeBase+12);  // instruction address at end
		checkMemB(0x200140, expected); // output value must have been written there
		checkMemB(0x20013F, _FF); // the bytes before and behind
		checkMemB(0x200144, _FF); // the store location must be unchanged
		checkCC(expCC);          // CC3 if it stays unchanged
	}
	
	// Add Normalized - RX - Short Operands
	// condition code: 0 => result zero, 1 => result negative, 2 => result positive
	@Test
	public void test_LE_AE_STE() {
		this.innerTestLE_opRX_STE(0, _7A, onePointZero, onePointZero, twoPointZero, CC2);
		this.innerTestLE_opRX_STE(0, _7A, twoPointZero, minusOnePointZero, onePointZero, CC2);
		this.innerTestLE_opRX_STE(2, _7A, zeroPointFive, zeroPointFive, onePointZero, CC2);

		this.innerTestLE_opRX_STE(4, _7A, zero, zero, zero, CC0);
		this.innerTestLE_opRX_STE(4, _7A, minusOnePointZero, minusOnePointZero, minusTwoPointZero, CC1);
		this.innerTestLE_opRX_STE(4, _7A, minusZeroPointFive, minusZeroPointFive, minusOnePointZero, CC1);
		this.innerTestLE_opRX_STE(4, _7A, minusTwoPointZero, onePointZero, minusOnePointZero, CC1);

		this.innerTestLE_opRX_STE(0, _7A, minusZeroPointFive, zeroPointFive, zero, CC0);
		this.innerTestLE_opRX_STE(6, _7A, onePointZero, minusOnePointZero, zero, CC0);
		this.innerTestLE_opRX_STE(4, _7A, minusZeroPointFive, zeroPointFive, zero, CC0);
		this.innerTestLE_opRX_STE(6, _7A, onePointZero, minusOnePointZero, zero, CC0);
		
		this.innerTestLE_opRX_STE(0, _7A, p1, p2, r1, CC2);
	}
	
	// Subtract Normalized - RX - Short Operands
	// condition code: 0 => result zero, 1 => result negative, 2 => result positive
	@Test
	public void test_LE_SE_STE() {
		this.innerTestLE_opRX_STE(0, _7B, twoPointZero, onePointZero, onePointZero, CC2);
		this.innerTestLE_opRX_STE(2, _7B, onePointZero, zero, onePointZero, CC2);
		this.innerTestLE_opRX_STE(4, _7B, onePointZero, minusOnePointZero, twoPointZero, CC2);
		this.innerTestLE_opRX_STE(6, _7B, zeroPointFive, minusZeroPointFive, onePointZero, CC2);
		this.innerTestLE_opRX_STE(0, _7B, zero, minusOnePointZero, onePointZero, CC2);

		this.innerTestLE_opRX_STE(2, _7B, onePointZero, twoPointZero, minusOnePointZero, CC1);
		this.innerTestLE_opRX_STE(4, _7B, minusOnePointZero, onePointZero, minusTwoPointZero, CC1);
		this.innerTestLE_opRX_STE(6, _7B, minusZeroPointFive, zeroPointFive, minusOnePointZero, CC1);
		this.innerTestLE_opRX_STE(0, _7B, zero, onePointZero, minusOnePointZero, CC1);
		this.innerTestLE_opRX_STE(2, _7B, zero, twoPointZero, minusTwoPointZero, CC1);
		
		this.innerTestLE_opRX_STE(4, _7B, zero, zero, zero, CC0);
		this.innerTestLE_opRX_STE(6, _7B, onePointZero, onePointZero, zero, CC0);
		this.innerTestLE_opRX_STE(0, _7B, twoPointZero, twoPointZero, zero, CC0);
		this.innerTestLE_opRX_STE(2, _7B, zeroPointFive, zeroPointFive, zero, CC0);
		this.innerTestLE_opRX_STE(4, _7B, minusOnePointZero, minusOnePointZero, zero, CC0);
		this.innerTestLE_opRX_STE(6, _7B, minusTwoPointZero, minusTwoPointZero, zero, CC0);
		this.innerTestLE_opRX_STE(0, _7B, minusZeroPointFive, minusZeroPointFive, zero, CC0);
		
		this.innerTestLE_opRX_STE(2, _7B, p1, p2, r2, CC1); // result negative 
	}
	
	// Multiply Normalized - RX - Short Operands
	// condition code: 0 => result zero, 1 => result negative, 2 => result positive
	@Test
	public void test_LE_ME_STE() { // cc is unchanged, so it must always be CC3 (the start value in innerTestLE_LE_op_STE) 
		this.innerTestLE_opRX_STE(0, _7C, onePointZero, onePointZero, onePointZero, CC3); 
		this.innerTestLE_opRX_STE(2, _7C, onePointZero, twoPointZero, twoPointZero, CC3); 
		this.innerTestLE_opRX_STE(4, _7C, zeroPointFive, twoPointZero, onePointZero, CC3);
		 
		this.innerTestLE_opRX_STE(6, _7C, onePointZero, minusOnePointZero, minusOnePointZero, CC3); 
		this.innerTestLE_opRX_STE(0, _7C, minusOnePointZero, twoPointZero, minusTwoPointZero, CC3); 
		this.innerTestLE_opRX_STE(2, _7C, minusZeroPointFive, twoPointZero, minusOnePointZero, CC3);
		this.innerTestLE_opRX_STE(4, _7C, minusOnePointZero, onePointZero, minusOnePointZero, CC3); 
		this.innerTestLE_opRX_STE(6, _7C, twoPointZero, minusOnePointZero, minusTwoPointZero, CC3); 
		this.innerTestLE_opRX_STE(0, _7C, twoPointZero, minusZeroPointFive, minusOnePointZero, CC3);
		 
		this.innerTestLE_opRX_STE(0, _7C, minusOnePointZero, minusOnePointZero, onePointZero, CC3); 
		this.innerTestLE_opRX_STE(2, _7C, minusOnePointZero, minusTwoPointZero, twoPointZero, CC3); 
		this.innerTestLE_opRX_STE(4, _7C, minusZeroPointFive, minusTwoPointZero, onePointZero, CC3);
		this.innerTestLE_opRX_STE(6, _7C, minusTwoPointZero, minusOnePointZero, twoPointZero, CC3); 
		this.innerTestLE_opRX_STE(0, _7C, minusTwoPointZero, minusZeroPointFive, onePointZero, CC3);
		 
		this.innerTestLE_opRX_STE(2, _7C, minusOnePointZero, zero, zero, CC3); 
		this.innerTestLE_opRX_STE(4, _7C, minusTwoPointZero, zero, zero, CC3); 
		this.innerTestLE_opRX_STE(6, _7C, minusZeroPointFive, zero, zero, CC3);
		this.innerTestLE_opRX_STE(0, _7C, onePointZero, zero, zero, CC3); 
		this.innerTestLE_opRX_STE(2, _7C, twoPointZero, zero, zero, CC3); 
		this.innerTestLE_opRX_STE(4, _7C, zeroPointFive, zero, zero, CC3);
		this.innerTestLE_opRX_STE(6, _7C, zero, minusOnePointZero, zero, CC3); 
		this.innerTestLE_opRX_STE(0, _7C, zero, minusTwoPointZero, zero, CC3); 
		this.innerTestLE_opRX_STE(2, _7C, zero, minusZeroPointFive, zero, CC3);
		this.innerTestLE_opRX_STE(4, _7C, zero, onePointZero, zero, CC3); 
		this.innerTestLE_opRX_STE(6, _7C, zero, twoPointZero, zero, CC3); 
		this.innerTestLE_opRX_STE(0, _7C, zero, zeroPointFive, zero, CC3);
		this.innerTestLE_opRX_STE(2, _7C, zero, zero, zero, CC3);
		
		this.innerTestLE_opRX_STE(4, _7C, p1, p2, r3, CC3);
	}
	
	// Divide Normalized - RX - Short Operands
	// condition code: 0 => result zero, 1 => result negative, 2 => result positive
	@Test
	public void test_LE_DE_STE() {
		this.innerTestLE_opRX_STE(0, _7D, twoPointZero, onePointZero, twoPointZero, CC3);
		this.innerTestLE_opRX_STE(2, _7D, twoPointZero, twoPointZero, onePointZero, CC3);
		this.innerTestLE_opRX_STE(4, _7D, onePointZero, twoPointZero, zeroPointFive, CC3);

		this.innerTestLE_opRX_STE(6, _7D, twoPointZero, minusOnePointZero, minusTwoPointZero, CC3);
		this.innerTestLE_opRX_STE(0, _7D, twoPointZero, minusTwoPointZero, minusOnePointZero, CC3);
		this.innerTestLE_opRX_STE(2, _7D, onePointZero, minusTwoPointZero, minusZeroPointFive, CC3);

		this.innerTestLE_opRX_STE(4, _7D, minusTwoPointZero, onePointZero, minusTwoPointZero, CC3);
		this.innerTestLE_opRX_STE(6, _7D, minusTwoPointZero, twoPointZero, minusOnePointZero, CC3);
		this.innerTestLE_opRX_STE(0, _7D, minusOnePointZero, twoPointZero, minusZeroPointFive, CC3);

		this.innerTestLE_opRX_STE(2, _7D, minusTwoPointZero, minusOnePointZero, twoPointZero, CC3);
		this.innerTestLE_opRX_STE(4, _7D, minusTwoPointZero, minusTwoPointZero, onePointZero, CC3);
		this.innerTestLE_opRX_STE(6, _7D, minusOnePointZero, minusTwoPointZero, zeroPointFive, CC3);

		this.innerTestLE_opRX_STE(0, _7D, zero, onePointZero, zero, CC3);
		this.innerTestLE_opRX_STE(2, _7D, zero, twoPointZero, zero, CC3);
		this.innerTestLE_opRX_STE(4, _7D, zero, twoPointZero, zero, CC3);

		this.innerTestLE_opRX_STE(6, _7D, zero, minusOnePointZero, zero, CC3);
		this.innerTestLE_opRX_STE(0, _7D, zero, minusTwoPointZero, zero, CC3);
		this.innerTestLE_opRX_STE(2, _7D, zero, minusTwoPointZero, zero, CC3);
				
		this.innerTestLE_opRX_STE(4, _7D, p1, p2, r4, CC3); // cc unchanged 
	}
	
	/*
	 * Tests: load register A and Bfrom memory , do CER A,B and check outcome
	 */
	
	private void innerTestLE_LE_CER(
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
				_78, b1from, _20, _80,     // LE reg1 <- (base=R2,index=R1,offset=0x080)=0x010180
				_78, b2from, _21, _80,     // LE reg2 <- (base=R2,index=R1,offset=0x180)=0x010280
				_39, reg1reg2              // CER reg1,reg2
		);
		execute(3); // do three instructions
		checkIL(1); // instruction length code of last instruction
		checkIA(CodeBase+10);  // instruction address at end
		checkCC(expCC);          // CC3 if it stays unchanged
	}
	
	@Test
	public void test_LE_LE_CER() {
		this.innerTestLE_LE_CER(0, 2, twoPointZero, twoPointZero, CC0);
		this.innerTestLE_LE_CER(0, 2, onePointZero, onePointZero, CC0);
		this.innerTestLE_LE_CER(0, 2, zeroPointFive, zeroPointFive, CC0);
		this.innerTestLE_LE_CER(0, 2, minusZeroPointFive, minusZeroPointFive, CC0);
		this.innerTestLE_LE_CER(0, 2, minusOnePointZero, minusOnePointZero, CC0);
		this.innerTestLE_LE_CER(0, 2, minusTwoPointZero, minusTwoPointZero, CC0);
		this.innerTestLE_LE_CER(0, 2, p1, p1, CC0);
		this.innerTestLE_LE_CER(0, 2, p2, p2, CC0);
		
		this.innerTestLE_LE_CER(0, 2, zero, zeroPointFive, CC1);
		this.innerTestLE_LE_CER(2, 4, zero, onePointZero, CC1);
		this.innerTestLE_LE_CER(4, 6, zero, twoPointZero, CC1);
		this.innerTestLE_LE_CER(6, 4, onePointZero, twoPointZero, CC1);
		this.innerTestLE_LE_CER(4, 2, zeroPointFive, twoPointZero, CC1);
		this.innerTestLE_LE_CER(2, 0, minusOnePointZero, onePointZero, CC1);
		this.innerTestLE_LE_CER(0, 6, minusOnePointZero, twoPointZero, CC1);
		this.innerTestLE_LE_CER(6, 0, minusOnePointZero, zeroPointFive, CC1);
		
		this.innerTestLE_LE_CER(0, 2, zeroPointFive, zero, CC2);
		this.innerTestLE_LE_CER(2, 4, onePointZero, zero, CC2);
		this.innerTestLE_LE_CER(4, 6, twoPointZero, zero, CC2);
		this.innerTestLE_LE_CER(6, 4, twoPointZero, onePointZero, CC2);
		this.innerTestLE_LE_CER(4, 2, twoPointZero, zeroPointFive, CC2);
		this.innerTestLE_LE_CER(2, 0, onePointZero, minusOnePointZero, CC2);
		this.innerTestLE_LE_CER(0, 6, twoPointZero, minusOnePointZero, CC2);
		this.innerTestLE_LE_CER(6, 0, zeroPointFive, minusOnePointZero, CC2);
	}
	
	/*
	 * Tests: load register A and B from memory , do CER A,B and check outcome
	 */
	
	private void innerTestLE_CE(
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
				_78, b1from, _20, _80,     // LE reg1 <- (base=R2,index=R1,offset=0x080)=0x010180
				_79, b2from, _21, _80      // CE reg2,(base=R2,index=R1,offset=0x180)=0x010280
		);
		execute(2); // do two instructions
		checkIL(2); // instruction length code of last instruction
		checkIA(CodeBase+8);  // instruction address at end
		checkCC(expCC);          // CC3 if it stays unchanged
	}
	
	@Test
	public void test_LE_CE() {
		this.innerTestLE_CE(0, twoPointZero, twoPointZero, CC0);
		this.innerTestLE_CE(2, onePointZero, onePointZero, CC0);
		this.innerTestLE_CE(4, zeroPointFive, zeroPointFive, CC0);
		this.innerTestLE_CE(6, minusZeroPointFive, minusZeroPointFive, CC0);
		this.innerTestLE_CE(0, minusOnePointZero, minusOnePointZero, CC0);
		this.innerTestLE_CE(2, minusTwoPointZero, minusTwoPointZero, CC0);
		this.innerTestLE_CE(4, p1, p1, CC0);
		this.innerTestLE_CE(6, p2, p2, CC0);
		
		this.innerTestLE_CE(0, zero, zeroPointFive, CC1);
		this.innerTestLE_CE(2, zero, onePointZero, CC1);
		this.innerTestLE_CE(4, zero, twoPointZero, CC1);
		this.innerTestLE_CE(6, onePointZero, twoPointZero, CC1);
		this.innerTestLE_CE(4, zeroPointFive, twoPointZero, CC1);
		this.innerTestLE_CE(2, minusOnePointZero, onePointZero, CC1);
		this.innerTestLE_CE(0, minusOnePointZero, twoPointZero, CC1);
		this.innerTestLE_CE(6, minusOnePointZero, zeroPointFive, CC1);
		
		this.innerTestLE_CE(0, zeroPointFive, zero, CC2);
		this.innerTestLE_CE(2, onePointZero, zero, CC2);
		this.innerTestLE_CE(4, twoPointZero, zero, CC2);
		this.innerTestLE_CE(6, twoPointZero, onePointZero, CC2);
		this.innerTestLE_CE(4, twoPointZero, zeroPointFive, CC2);
		this.innerTestLE_CE(2, onePointZero, minusOnePointZero, CC2);
		this.innerTestLE_CE(0, twoPointZero, minusOnePointZero, CC2);
		this.innerTestLE_CE(6, zeroPointFive, minusOnePointZero, CC2);
	}
	
	/*
	 * Tests: load register A from memory , do HER B,A and store register B to memory
	 */
	
	private void innerTestLE_HER(
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
				_78, b1from, _20, _80,     // LE regA <- (base=R2,index=R1,offset=0x080)=0x010180
				_34, regBregA,             // HER regB <- regA
				_70, b1to, _60, _40        // STE regB -> (base=R6,index=R5,offset=0x040)=0x200140
		);
		execute(3); // do two instructions
		checkIL(2); // instruction length code of last instruction
		checkIA(CodeBase+10);  // instruction address at end
		checkMemB(0x200140, expected); // output value must have been written there
		checkMemB(0x20013F, _FF); // the bytes before and behind
		checkMemB(0x200144, _FF); // the store location must be unchanged
		checkCC(CC3);          // CC stays unchanged
	}
	
	@Test
	public void test_LE_HER_STE() {
		this.innerTestLE_HER(0, 2, twoPointZero, onePointZero);
		this.innerTestLE_HER(2, 0, onePointZero, zeroPointFive);
		this.innerTestLE_HER(0, 4, zero, zero);
		this.innerTestLE_HER(4, 0, minusTwoPointZero, minusOnePointZero);
		this.innerTestLE_HER(6, 4, minusOnePointZero, minusZeroPointFive);
	}
	
	/*
	 * Tests: load register A , do LTER B,A and check outcome
	 */
	
	private void innerTestLE_LTER(
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
				_78, b1from, _20, _80,     // LE reg1 <- (base=R2,index=R1,offset=0x080)=0x010180
				_32, reg2reg1              // LTER reg2,reg1
		);
		execute(2); // do two instructions
		checkIL(1); // instruction length code of last instruction
		checkIA(CodeBase+6);  // instruction address at end
		checkCC(expCC);          // CC3 if it stays unchanged
	}
	
	@Test
	public void test_LE_LTER() {
		this.innerTestLE_LTER(2, 0, zero, CC0);
		this.innerTestLE_LTER(4, 0, zero, CC0);
		this.innerTestLE_LTER(6, 0, zero, CC0);
		this.innerTestLE_LTER(0, 0, zero, CC0);
		this.innerTestLE_LTER(2, 2, zero, CC0);
		this.innerTestLE_LTER(4, 4, zero, CC0);
		this.innerTestLE_LTER(6, 6, zero, CC0);
		this.innerTestLE_LTER(0, 6, zero, CC0);

		this.innerTestLE_LTER(0, 2, minusOnePointZero, CC1);
		this.innerTestLE_LTER(6, 2, minusTwoPointZero, CC1);
		this.innerTestLE_LTER(2, 4, minusZeroPointFive, CC1);
		
		this.innerTestLE_LTER(0, 0, minusZeroPointFive, CC1);
		this.innerTestLE_LTER(2, 2, minusZeroPointFive, CC1);
		this.innerTestLE_LTER(4, 4, minusZeroPointFive, CC1);
		this.innerTestLE_LTER(6, 6, minusZeroPointFive, CC1);

		this.innerTestLE_LTER(0, 2, onePointZero, CC2);
		this.innerTestLE_LTER(6, 2, twoPointZero, CC2);
		this.innerTestLE_LTER(2, 4, zeroPointFive, CC2);
		
		this.innerTestLE_LTER(0, 0, zeroPointFive, CC2);
		this.innerTestLE_LTER(2, 2, zeroPointFive, CC2);
		this.innerTestLE_LTER(4, 4, zeroPointFive, CC2);
		this.innerTestLE_LTER(6, 6, zeroPointFive, CC2);
	}
	
	/*
	 * Tests: load register A from memory , do LxER B,A and store register B to memory
	 */
	
	private void innerTestLE_LxER(
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
				_78, b1from, _20, _80,     // LE regA <- (base=R2,index=R1,offset=0x080)=0x010180
				op, regBregA,              // LxER regB <- regA
				_70, b1to, _60, _40        // STE regB -> (base=R6,index=R5,offset=0x040)=0x200140
		);
		execute(3); // do two instructions
		checkIL(2); // instruction length code of last instruction
		checkIA(CodeBase+10);  // instruction address at end
		checkMemB(0x200140, expected); // output value must have been written there
		checkMemB(0x20013F, _FF); // the bytes before and behind
		checkMemB(0x200144, _FF); // the store location must be unchanged
		checkCC(expCC);          // CC3 if it stays unchanged
	}
	
	@Test
	public void test_LE_LCER_STE() {
		this.innerTestLE_LxER(_33, 0, 2, zero, zero, CC0);
		
		this.innerTestLE_LxER(_33, 2, 4, zeroPointFive, minusZeroPointFive, CC1);
		
		this.innerTestLE_LxER(_33, 6, 4, minusZeroPointFive, zeroPointFive, CC2);
	}
	
	@Test
	public void test_LE_LNER_STE() {
		this.innerTestLE_LxER(_31, 0, 2, zero, zero, CC0);
		
		this.innerTestLE_LxER(_31, 2, 4, zeroPointFive, minusZeroPointFive, CC1);
		
		this.innerTestLE_LxER(_31, 6, 4, minusZeroPointFive, minusZeroPointFive, CC1);
	}
	
	@Test
	public void test_LE_LPER_STE() {
		this.innerTestLE_LxER(_30, 0, 2, zero, zero, CC0);
		
		this.innerTestLE_LxER(_30, 2, 4, zeroPointFive, zeroPointFive, CC2);
		
		this.innerTestLE_LxER(_30, 6, 4, minusZeroPointFive, zeroPointFive, CC2);
	}
	
	// ## not tested: Load Rounded (long -> short)
	
	// ## not tested: Add/Subtract unnormalized (even registers always hold normalized values!)
}
