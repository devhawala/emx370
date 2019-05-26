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
public class Cpu370BcTest_Shift extends AbstractCpu370BcTest {
	
	public Cpu370BcTest_Shift(Class<? extends Cpu370Bc> cpuClass) {
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
	
	/*
	** local constants
	*/
	
	private final int IA_PGM_INTR_BASE = 0x00500000;
	
	
	/*
	** Tests:  0x8F -- SLDA [RS] - Shift Left Double
	*/
	
	@Test
	public void x8F_RS_ShiftLeftDouble_01_oddOp1_Intr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(1, 0x00123456);            // op1 in R1-2
		setGPR(2, 0x89ABCDEF);
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8F, _10, _A0, _00        // SLDA R1,(R10,0x000)	shift R1-2 left by 1 bit 
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(1, 0x00123456);    // op1 must be unchanged 
		checkGPR(2, 0x89ABCDEF);
		checkGPR(10, 0x010001);     // op2 must be unchanged
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 2, 0, CodeBase+4); // ILC=2, CC=unchanged 
	}
	
	@Test
	public void x8F_RS_ShiftLeftDouble_02_positive_shiftBy_1() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8F, _20, _A0, _00        // SLDA R2,(R10,0x000)	shift R2-3 left by 1 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // result is greater than zero
		checkGPR(2, 0x002468AD); // expected result in R2-3 (1 bit = 1 carried from R3 over to R2)
		checkGPR(3, 0x13579BDE);
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x8F_RS_ShiftLeftDouble_03_positive_shiftBy_10() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8F, _20, _A0, _09        // SLDA R2,(R10,0x009)	shift R2-3 left by 10 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // result is greater than zero
		checkGPR(2, 0x48D15A26); // expected result in R2-3 (10 bit carried from R3 over to R2)
		checkGPR(3, 0xAF37BC00);
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x8F_RS_ShiftLeftDouble_03a_positive_shiftBy_11_CC3() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8F, _20, _00, _0B        // SLDA R2,(R0,0x00B)	shift R2-3 left by 11 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC3);            // overflow (bit 1 shifted differs from sign bit)
		checkGPR(2, 0x11A2B44D); // expected result in R2-3 (11 bit carried from R3 over to R2) [not 0x91A2B44D because "The sign remains unchanged"]
		checkGPR(3, 0x5E6F7800); 
	}
	
	@Test
	public void x8F_RS_ShiftLeftDouble_03a_positive_shiftBy_11_CC3_Intr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setProgramMask(true, false, false, false); // enable fixed-point exceptions
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8F, _20, _00, _0B        // SLDA R2,(R0,0x00B)	shift R2-3 left by 11 bits 
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(2, 0x11A2B44D); // expected result in R2-3 (11 bit carried from R3 over to R2) [not 0x91A2B44D because "The sign remains unchanged"]
		checkGPR(3, 0x5E6F7800);
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 2, 3, CodeBase+4); // ILC=2, CC=3
	}
	
	@Test
	public void x8F_RS_ShiftLeftDouble_04_negative_shiftBy_1() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0xF0123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8F, _20, _A0, _00        // SLDA R2,(R10,0x000)	shift R2-3 left by 1 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // result is less than zero
		checkGPR(2, 0xE02468AD); // expected result in R2-3 (1 bit = 1 carried from R3 over to R2)
		checkGPR(3, 0x13579BDE);
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x8F_RS_ShiftLeftDouble_05_negative_shiftBy_3() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0xF0123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8F, _20, _A0, _02        // SLDA R2,(R10,0x002)	shift R2-3 left by 3 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // result is less than zero
		checkGPR(2, 0x8091A2B4); // expected result in R2-3 (3 bit carried from R3 over to R2)
		checkGPR(3, 0x4D5E6F78);
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x8F_RS_ShiftLeftDouble_06a_negative_shiftBy_4_CC3() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0xF0123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8F, _20, _00, _04        // SLDA R2,(R0,0x004)	shift R2-3 left by 4 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC3);            // overflow (bit 1 shifted differs from sign bit)
		checkGPR(2, 0x81234568); // expected result in R2-3 (4 bit carried from R3 over to R2)
		checkGPR(3, 0x9ABCDEF0); 
	}
	
	@Test
	public void x8F_RS_ShiftLeftDouble_06b_negative_shiftBy_11_CC3_Intr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setProgramMask(true, false, false, false); // enable fixed-point exceptions
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0xF0123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8F, _20, _00, _0B        // SLDA R2,(R0,0x00B)	shift R2-3 left by 11 bits 
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(2, 0x91A2B44D); // expected result in R2-3 (11 bit carried from R3 over to R2)
		checkGPR(3, 0x5E6F7800);
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 2, 3, CodeBase+4); // ILC=2, CC=3
	}
	
	@Test
	public void x8F_RS_ShiftLeftDouble_07a_positive_noShift() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8F, _20, _00, _00        // SLDA R2,(R0,0x000)	shift R2-3 left by 0 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // result is positive
		checkGPR(2, 0x00123456); // expected result in R2-3 (11 bit carried from R3 over to R2)
		checkGPR(3, 0x89ABCDEF); 
	}
	
	@Test
	public void x8F_RS_ShiftLeftDouble_07b_negative_noShift() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0x80123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8F, _20, _00, _00        // SLDA R2,(R0,0x000)	shift R2-3 left by 0 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // result is negative
		checkGPR(2, 0x80123456); // expected result in R2-3 (11 bit carried from R3 over to R2)
		checkGPR(3, 0x89ABCDEF); 
	}
	
	@Test
	public void x8F_RS_ShiftLeftDouble_08_zero_shiftBy_5() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00000000);            // op1 in R2-3
		setGPR(3, 0x00000000);
		setInstructions(
				_8F, _20, _A0, _05        // SLDA R2,(R10,0x005)	shift R2-3 left by 5 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // result is zero
		checkGPR(2, 0x00000000); // expected result in R2-3 (1 bit = 1 carried from R3 over to R2)
		checkGPR(3, 0x00000000); 
	}
	
	
	/*
	** Tests:  0x8D -- SLDL [RS] - Shift Left Double Logical
	*/
	
	@Test
	public void x8D_RS_ShiftLeftDoubleLogical_01_oddOp1_Intr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(1, 0x00123456);            // op1 in R1-2
		setGPR(2, 0x89ABCDEF);
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8D, _10, _A0, _00        // SLDL R1,(R10,0x000)	shift R1-2 left by 1 bit 
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(1, 0x00123456);    // op1 must be unchanged 
		checkGPR(2, 0x89ABCDEF);
		checkGPR(10, 0x010001);     // op2 must be unchanged
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 2, 0, CodeBase+4); // ILC=2, CC=unchanged 
	}
	
	@Test
	public void x8D_RS_ShiftLeftDoubleLogical_02_positive_shiftBy_1() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8D, _20, _A0, _00        // SLDL R2,(R10,0x000)	shift R2-3 left by 1 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 0x002468AD); // expected result in R2-3 (1 bit = 1 carried from R3 over to R2)
		checkGPR(3, 0x13579BDE);
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x8D_RS_ShiftLeftDoubleLogical_03_positive_shiftBy_10() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC2);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8D, _20, _A0, _09        // SLDL R2,(R10,0x009)	shift R2-3 left by 10 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // must be unchanged
		checkGPR(2, 0x48D15A26); // expected result in R2-3 (10 bit carried from R3 over to R2)
		checkGPR(3, 0xAF37BC00);
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x8D_RS_ShiftLeftDoubleLogical_04_positive_shiftBy_11() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC1);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8D, _20, _00, _0B        // SLDL R2,(R0,0x00B)	shift R2-3 left by 11 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // must be unchanged
		checkGPR(2, 0x91A2B44D); // expected result in R2-3 (11 bit carried from R3 over to R2)
		checkGPR(3, 0x5E6F7800); 
	}
	
	@Test
	public void x8D_RS_ShiftLeftDoubleLogical_05_positive_shiftBy_63() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8D, _20, _0F, _FF        // SLDL R2,(R0,0xFFF)	shift R2-3 left by 63 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // must be unchanged
		checkGPR(2, 0x80000000); // expected result in R2-3 (11 bit carried from R3 over to R2)
		checkGPR(3, 0x00000000); 
	}
	
	@Test
	public void x8D_RS_ShiftLeftDoubleLogical_06_negative_shiftBy_4() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC1);                       // for later comparision
		setGPR(2, 0xF0123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8D, _20, _0F, _04        // SLDL R2,(R0,0xF04)	shift R2-3 left by 4 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // must be unchanged
		checkGPR(2, 0x01234568); // expected result in R2-3 (11 bit carried from R3 over to R2)
		checkGPR(3, 0x9ABCDEF0); 
	}
	
	@Test
	public void x8D_RS_ShiftLeftDoubleLogical_07a_positive_noShift() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8D, _20, _00, _00        // SLDL R2,(R0,0x000)	shift R2-3 left by 0 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // must be unchanged
		checkGPR(2, 0x00123456); // expected result in R2-3 (11 bit carried from R3 over to R2)
		checkGPR(3, 0x89ABCDEF); 
	}
	
	@Test
	public void x8D_RS_ShiftLeftDoubleLogical_07b_negative_noShift() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0x80123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8D, _20, _00, _00        // SLDL R2,(R0,0x000)	shift R2-3 left by 0 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // must be unchanged
		checkGPR(2, 0x80123456); // expected result in R2-3 (11 bit carried from R3 over to R2)
		checkGPR(3, 0x89ABCDEF); 
	}
	
	
	/*
	** Tests:  0x8B -- SLA [RS] - Shift Left
	*/
	
	@Test
	public void x8B_RS_ShiftLeft_01_positive_shiftBy_1() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8B, _20, _A0, _00        // SLA R2,(R10,0x000)	shift R2 left by 1 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // result is greater than zero
		checkGPR(2, 0x002468AC); // expected result in R2
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x8B_RS_ShiftLeft_02_positive_shiftBy_10() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8B, _20, _A0, _09        // SLA R2,(R10,0x009)	shift R2 left by 10 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // result is greater than zero
		checkGPR(2, 0x48D15800); // expected result in R2
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x8B_RS_ShiftLeft_03a_positive_shiftBy_11_CC3() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2
		setInstructions(
				_8B, _20, _00, _0B        // SLA R2,(R0,0x00B)	shift R2 left by 11 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC3);            // overflow (bit 1 shifted differs from sign bit)
		checkGPR(2, 0x11A2B000); // expected result in R2
	}
	
	@Test
	public void x8B_RS_ShiftLeft_03a_positive_shiftBy_11_CC3_Intr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setProgramMask(true, false, false, false); // enable fixed-point exceptions
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2
		setInstructions(
				_8B, _20, _00, _0B        // SLA R2,(R0,0x00B)	shift R2 left by 11 bits 
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(2, 0x11A2B000); // expected result in R2
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 2, 3, CodeBase+4); // ILC=2, CC=3
	}
	
	@Test
	public void x8B_RS_ShiftLeft_04_negative_shiftBy_1() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0xF0123456);            // op1 in R2
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8B, _20, _A0, _00        // SLA R2,(R10,0x000)	shift R2 left by 1 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // result is less than zero
		checkGPR(2, 0xE02468AC); // expected result in R2
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x8B_RS_ShiftLeft_05_negative_shiftBy_3() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0xF0123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);            // just for checking
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8B, _20, _A0, _02        // SLA R2,(R10,0x002)	shift R2 left by 3 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // result is less than zero
		checkGPR(2, 0x8091A2B0); // expected result in R2
		checkGPR(3, 0x89ABCDEF); // must be unchanged
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x8B_RS_ShiftLeft_06a_negative_shiftBy_4_CC3() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0xF0123456);            // op1 in R2
		setInstructions(
				_8B, _20, _00, _04        // SLA R2,(R0,0x004)	shift R2 left by 4 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC3);            // overflow (bit 1 shifted differs from sign bit)
		checkGPR(2, 0x81234560); // expected result in R2
	}
	
	@Test
	public void x8B_RS_ShiftLeft_06b_negative_shiftBy_11_CC3_Intr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setProgramMask(true, false, false, false); // enable fixed-point exceptions
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0xF0123456);            // op1 in R2
		setInstructions(
				_8B, _20, _00, _0B        // SLA R2,(R0,0x00B)	shift R2 left by 11 bits 
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(2, 0x91A2B000); // expected result in R2
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 2, 3, CodeBase+4); // ILC=2, CC=unchanged
	}
	
	@Test
	public void x8B_RS_ShiftLeft_07a_positive_noShift() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2
		setInstructions(
				_8B, _20, _00, _00        // SLA R2,(R0,0x000)	shift R2 left by 0 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // result is positive
		checkGPR(2, 0x00123456); // expected result in R2
	}
	
	@Test
	public void x8B_RS_ShiftLeft_07b_negative_noShift() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0x80123456);            // op1 in R2
		setInstructions(
				_8B, _20, _00, _00        // SLA R2,(R0,0x000)	shift R2 left by 0 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // result is negative
		checkGPR(2, 0x80123456); // expected result in R2
	}
	
	@Test
	public void x8B_RS_ShiftLeft_07a_zero_shiftBy_5() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00000000);            // op1 in R2
		setInstructions(
				_8B, _20, _00, _05        // SLA R2,(R0,0x005)	shift R2 left by 5 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // result is positive
		checkGPR(2, 0x00000000); // expected result in R2
	}
	
	
	/*
	** Tests:  0x89 -- SLL [RS] - Shift Left Logical
	*/
	
	@Test
	public void x89_RS_ShiftLeftLogical_01_positive_shiftBy_1() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2
		setGPR(3, 0x89ABCDEF);            // just for checking
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_89, _20, _A0, _00        // SLL R2,(R10,0x000)	shift R2 left by 1 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 0x002468AC); // expected result in R2
		checkGPR(3, 0x89ABCDEF); // must be unchanged
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x89_RS_ShiftLeftLogical_02_positive_shiftBy_10() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC2);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2
		setGPR(3, 0x89ABCDEF);            // just for checking
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_89, _20, _A0, _09        // SLL R2,(R10,0x009)	shift R2 left by 10 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // must be unchanged
		checkGPR(2, 0x48D15800); // expected result in R2
		checkGPR(3, 0x89ABCDEF); // must be unchanged
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x89_RS_ShiftLeftLogical_03_positive_shiftBy_11() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC1);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2
		setInstructions(
				_89, _20, _00, _0B        // SLL R2,(R0,0x00B)	shift R2 left by 11 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // must be unchanged
		checkGPR(2, 0x91A2B000); // expected result in R2
	}
	
	@Test
	public void x89_RS_ShiftLeftLogical_04_positive_shiftBy_63() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2
		setInstructions(
				_89, _20, _0F, _FF        // SLL R2,(R0,0xFFF)	shift R2 left by 63 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // must be unchanged
		checkGPR(2, 0x00000000); // expected result in R2
	}
	
	@Test
	public void x89_RS_ShiftLeftLogical_05_negative_shiftBy_4() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC1);                       // for later comparision
		setGPR(2, 0xF0123456);            // op1 in R2
		setInstructions(
				_89, _20, _0F, _04        // SLL R2,(R0,0xF04)	shift R2 left by 4 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // must be unchanged
		checkGPR(2, 0x01234560); // expected result in R2
	}
	
	@Test
	public void x89_RS_ShiftLeftLogical_06a_positive_noShift() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2
		setInstructions(
				_89, _20, _00, _00        // SLL R2,(R0,0x000)	shift R2 left by 0 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // must be unchanged
		checkGPR(2, 0x00123456); // expected result in R2
	}
	
	@Test
	public void x89_RS_ShiftLeftLogical_07b_negative_noShift() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0x80123456);            // op1 in R2
		setInstructions(
				_89, _20, _00, _00        // SLL R2,(R0,0x000)	shift R2-3 left by 0 bits 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // must be unchanged
		checkGPR(2, 0x80123456); // expected result in R2
	}
	
	
	/*
	** Tests:  0x8E -- SRDA [RS] - Shift Right Double
	*/
	
	@Test
	public void x8E_RS_ShiftRightDouble_01_oddOp1_Intr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(1, 0x00123456);            // op1 in R1-2
		setGPR(2, 0x89ABCDEF);
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8E, _10, _A0, _00        // SRDA R1,(R10,0x000)	shift R1-2 right by 1 bit 
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(1, 0x00123456);    // op1 must be unchanged 
		checkGPR(2, 0x89ABCDEF);
		checkGPR(10, 0x010001);     // op2 must be unchanged
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 2, 0, CodeBase+4); // ILC=2, CC=unchanged 
	}
	
	@Test
	public void x8E_RS_ShiftRightDouble_02_positive_shiftBy_1() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8E, _20, _A0, _00        // SRDA R2,(R10,0x000)	shift R2-3 right by 1 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // result is greater than zero
		checkGPR(2, 0x00091A2B); // expected result in R2-3 (1 bit = 1 carried from R2 over to R3)
		checkGPR(3, 0x44D5E6F7);
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x8E_RS_ShiftRightDouble_03_negative_shiftBy_1() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0xF0123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8E, _20, _A0, _00        // SRDA R2,(R10,0x000)	shift R2-3 right by 1 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // result is less than zero
		checkGPR(2, 0xF8091A2B); // expected result in R2-3 (1 bit = 1 carried from R2 over to R3)
		checkGPR(3, 0x44D5E6F7);
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x8E_RS_ShiftRightDouble_04_positive_shiftBy_0() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8E, _20, _00, _00        // SRDA R2,(R10,0x000)	shift R2-3 right by 0 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // result is greater than zero
		checkGPR(2, 0x00123456); // expected result in R2-3
		checkGPR(3, 0x89ABCDEF); 
	}
	
	@Test
	public void x8E_RS_ShiftRightDouble_05_negative_shiftBy_0() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x80123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8E, _20, _00, _00        // SRDA R2,(R10,0x000)	shift R2-3 right by 0 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // result is less than zero
		checkGPR(2, 0x80123456); // expected result in R2-3
		checkGPR(3, 0x89ABCDEF); 
	}
	
	@Test
	public void x8E_RS_ShiftRightDouble_06_positive_shiftBy_63() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8E, _20, _00, _FF        // SRDA R2,(R10,0x0FF)	shift R2-3 right by 63 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // result is zero
		checkGPR(2, 0x00000000); // expected result in R2-3
		checkGPR(3, 0x00000000); 
	}
	
	@Test
	public void x8E_RS_ShiftRightDouble_07_negative_shiftBy_63() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x80123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8E, _20, _0F, _FF        // SRDA R2,(R10,0xFFF)	shift R2-3 right by 63 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // result is less than zero
		checkGPR(2, 0xFFFFFFFF); // expected result in R2-3
		checkGPR(3, 0xFFFFFFFF); 
	}
	
	@Test
	public void x8E_RS_ShiftRightDouble_08_zero_shiftBy_5() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00000000);            // op1 in R2-3
		setGPR(3, 0x00000000);
		setInstructions(
				_8E, _20, _00, _05        // SRDA R2,(R10,0x005)	shift R2-3 right by 0 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // result is greater than zero
		checkGPR(2, 0x00000000); // expected result in R2-3
		checkGPR(3, 0x00000000); 
	}
	
	@Test
	public void x8E_RS_ShiftRightDouble_09_negative_shiftBy_4() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0xF0123456);            // op1 in R2-3
		setGPR(3, 0x89ABCDEF);
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8E, _20, _A0, _03        // SRDA R2,(R10,0x000)	shift R2-3 right by 4 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // result is less than zero
		checkGPR(2, 0xFF012345); // expected result in R2-3 (4 bits = digit 6 carried from R2 over to R3)
		checkGPR(3, 0x689ABCDE);
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	
	/*
	** Tests:  0x8C -- SRDL [RS] - Shift Right Double Logical
	*/
	
	@Test
	public void x8C_RS_ShiftRightDoubleLogical_01_oddOp1_Intr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(1, 0x00123456);            // op1 in R1-2
		setGPR(2, 0x89ABCDEF);
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8C, _10, _A0, _00        // SRDL R1,(R10,0x000)	shift R1-2 right by 1 bit 
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(1, 0x00123456);    // op1 must be unchanged 
		checkGPR(2, 0x89ABCDEF);
		checkGPR(10, 0x010001);     // op2 must be unchanged
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 2, 0, CodeBase+4); // ILC=2, CC=unchanged 
	}
	
	@Test
	public void x8C_RS_ShiftRightDoubleLogical_02_positive_shiftBy_1() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R1-2
		setGPR(3, 0x89ABCDEF);
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8C, _20, _A0, _00        // SRDL R2,(R10,0x000)	shift R2-3 right by 1 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 0x00091A2B); // expected result in R2-3 (1 bit = 1 carried from R2 over to R3)
		checkGPR(3, 0x44D5E6F7);
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x8C_RS_ShiftRightDoubleLogical_03_negative_shiftBy_1() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0xF0123456);            // op1 in R1-2
		setGPR(3, 0x89ABCDEF);
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8C, _20, _A0, _00        // SRDL R2,(R10,0x000)	shift R2-3 right by 1 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 0x78091A2B); // expected result in R2-3 (1 bit = 1 carried from R2 over to R3)
		checkGPR(3, 0x44D5E6F7);
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x8C_RS_ShiftRightDoubleLogical_04_positive_shiftBy_0() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R1-2
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8C, _20, _00, _00        // SRDL R2,(R10,0x000)	shift R2-3 right by 0 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // must be unchanged
		checkGPR(2, 0x00123456); // expected result in R2-3
		checkGPR(3, 0x89ABCDEF); 
	}
	
	@Test
	public void x8C_RS_ShiftRightDoubleLogical_05_negative_shiftBy_0() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC1);                       // for later comparision
		setGPR(2, 0x80123456);            // op1 in R1-2
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8C, _20, _00, _00        // SRDL R2,(R10,0x000)	shift R2-3 right by 0 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // must be unchanged
		checkGPR(2, 0x80123456); // expected result in R2-3
		checkGPR(3, 0x89ABCDEF); 
	}
	
	@Test
	public void x8C_RS_ShiftRightDoubleLogical_06_positive_shiftBy_63() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC2);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R1-2
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8C, _20, _00, _FF        // SRDL R2,(R10,0x0FF)	shift R2-3 right by 63 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // must be unchanged
		checkGPR(2, 0x00000000); // expected result in R2-3
		checkGPR(3, 0x00000000); 
	}
	
	@Test
	public void x8C_RS_ShiftRightDoubleLogical_07_negative_shiftBy_63() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x80123456);            // op1 in R1-2
		setGPR(3, 0x89ABCDEF);
		setInstructions(
				_8C, _20, _0F, _FF        // SRDL R2,(R10,0xFFF)	shift R2-3 right by 63 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 0x00000000); // expected result in R2-3
		checkGPR(3, 0x00000001); 
	}
	
	@Test
	public void x8C_RS_ShiftRightDoubleLogical_08_zero_shiftBy_5() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00000000);            // op1 in R1-2
		setGPR(3, 0x00000000);
		setInstructions(
				_8C, _20, _00, _05        // SRDL R2,(R10,0x005)	shift R2-3 right by 0 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 0x00000000); // expected result in R2-3
		checkGPR(3, 0x00000000); 
	}
	
	
	/*
	** Tests:  0x8A -- SRA [RS] - Shift Right
	*/
	
	@Test
	public void x8A_RS_ShiftRight_01_positive_shiftBy_1() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R1
		setGPR(3, 0x89ABCDEF);            // for checking
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8A, _20, _A0, _00        // SRA R2,(R10,0x000)	shift R2 right by 1 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // result is greater than zero
		checkGPR(2, 0x00091A2B); // expected result in R2-3 (1 bit = 1 carried from R2 over to R3)
		checkGPR(3, 0x89ABCDEF); // must be unchanged
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x8A_RS_ShiftRight_02_negative_shiftBy_1() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0xF0123456);            // op1 in R1-2
		setGPR(3, 0x89ABCDEF);            // for checking
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_8A, _20, _A0, _00        // SRA R2,(R10,0x000)	shift R2 right by 1 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // result is less than zero
		checkGPR(2, 0xF8091A2B); // expected result in R2
		checkGPR(3, 0x89ABCDEF); // must be unchanged
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x8A_RS_ShiftRight_03_positive_shiftBy_0() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R1
		setInstructions(
				_8A, _20, _00, _00        // SRA R2,(R10,0x000)	shift R2 right by 0 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // result is greater than zero
		checkGPR(2, 0x00123456); // expected result in R2
	}
	
	@Test
	public void x8A_RS_ShiftRight_04_negative_shiftBy_0() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x80123456);            // op1 in R1
		setInstructions(
				_8A, _20, _00, _00        // SRA R2,(R10,0x000)	shift R2 right by 0 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // result is less than zero
		checkGPR(2, 0x80123456); // expected result in R2
	}
	
	@Test
	public void x8A_RS_ShiftRight_05a_positive_shiftBy_63() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x7F123456);            // op1 in R1
		setGPR(3, 0x89ABCDEF);            // for checking
		setInstructions(
				_8A, _20, _00, _FF        // SRA R2,(R10,0x0FF)	shift R2 right by 63 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // result is zero
		checkGPR(2, 0x00000000); // expected result in R2-3
		checkGPR(3, 0x89ABCDEF); // must stay unchanged 
	}
	
	@Test
	public void x8A_RS_ShiftRight_05b_positive_shiftBy_30() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x7F123456);            // op1 in R1
		setGPR(3, 0x89ABCDEF);            // for checking
		setInstructions(
				_8A, _20, _00, _1E        // SRA R2,(R10,0x0FF)	shift R2 right by 63 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // result is greater than zero
		checkGPR(2, 0x00000001); // expected result in R2
		checkGPR(3, 0x89ABCDEF); // must stay unchanged 
	}
	
	@Test
	public void x8A_RS_ShiftRight_06a_negative_shiftBy_63() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x80123456);            // op1 in R1
		setGPR(3, 0x89ABCDEF);            // for checking
		setInstructions(
				_8A, _20, _0F, _FF        // SRA R2,(R10,0xFFF)	shift R2 right by 63 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // result is less than zero
		checkGPR(2, 0xFFFFFFFF); // expected result in R2
		checkGPR(3, 0x89ABCDEF); // must stay unchanged 
	}
	
	@Test
	public void x8A_RS_ShiftRight_06b_negative_shiftBy_31() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x80123456);            // op1 in R1
		setGPR(3, 0x89ABCDEF);            // for checking
		setInstructions(
				_8A, _20, _00, _1F        // SRA R2,(R10,0xFFF)	shift R2 right by 31 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // result is less than zero
		checkGPR(2, 0xFFFFFFFF); // expected result in R2
		checkGPR(3, 0x89ABCDEF); // must stay unchanged 
	}
	
	@Test
	public void x8A_RS_ShiftRight_07_zero_shiftBy_5() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00000000);            // op1 in R1
		setInstructions(
				_8A, _20, _00, _05        // SRA R2,(R10,0x005)	shift R2 right by 0 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // result is greater than zero
		checkGPR(2, 0x00000000); // expected result in R2
	}
	
	
	/*
	** Tests:  0x88 -- SRL [RS] - Shift Right Logical
	*/
	
	@Test
	public void x88_RS_ShiftRightLogical_02_positive_shiftBy_1() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_88, _20, _A0, _00        // SRL R2,(R10,0x000)	shift R2 right by 1 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 0x00091A2B); // expected result in R2
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x88_RS_ShiftRightLogical_03_negative_shiftBy_1() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0xF0123456);            // op1 in R2
		setGPR(10, 0x010001);             // R10: base register for 2. Operand 
		setInstructions(
				_88, _20, _A0, _00        // SRL R2,(R10,0x000)	shift R2 right by 1 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 0x78091A2B); // expected result in R2
		checkGPR(10, 0x010001);  // op2 must be unchanged 
	}
	
	@Test
	public void x88_RS_ShiftRightLogical_04_positive_shiftBy_0() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC0);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2
		setInstructions(
				_88, _20, _00, _00        // SRL R2,(R10,0x000)	shift R2 right by 0 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // must be unchanged
		checkGPR(2, 0x00123456); // expected result in R2
	}
	
	@Test
	public void x88_RS_ShiftRightLogical_05_negative_shiftBy_0() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC1);                       // for later comparision
		setGPR(2, 0x80123456);            // op1 in R2
		setInstructions(
				_88, _20, _00, _00        // SRL R2,(R10,0x000)	shift R2 right by 0 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // must be unchanged
		checkGPR(2, 0x80123456); // expected result in R2-3
	}
	
	@Test
	public void x88_RS_ShiftRightLogical_06a_positive_shiftBy_63() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC2);                       // for later comparision
		setGPR(2, 0x00123456);            // op1 in R2
		setGPR(3, 0x89ABCDEF);            // for checking
		setInstructions(
				_88, _20, _00, _FF        // SRL R2,(R10,0x0FF)	shift R2 right by 63 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // must be unchanged
		checkGPR(2, 0x00000000); // expected result in R2
		checkGPR(3, 0x89ABCDEF); // must be unchanged
	}
	
	@Test
	public void x88_RS_ShiftRightLogical_06b_positive_shiftBy_30() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC2);                       // for later comparision
		setGPR(2, 0x7F123456);            // op1 in R2
		setGPR(3, 0x89ABCDEF);            // for checking
		setInstructions(
				_88, _20, _00, _1E        // SRL R2,(R10,0x0FF)	shift R2 right by 30 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // must be unchanged
		checkGPR(2, 0x00000001); // expected result in R2
		checkGPR(3, 0x89ABCDEF); // must be unchanged
	}
	
	@Test
	public void x88_RS_ShiftRightLogical_07a_negative_shiftBy_63() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x80123456);            // op1 in R2
		setGPR(3, 0x89ABCDEF);            // just for checking
		setInstructions(
				_88, _20, _0F, _FF        // SRL R2,(R10,0xFFF)	shift R2 right by 63 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 0x00000000); // expected result in R2-3
		checkGPR(3, 0x89ABCDEF); // must be unchanged 
	}
	
	@Test
	public void x88_RS_ShiftRightLogical_07b_negative_shiftBy_31() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x80123456);            // op1 in R2
		setGPR(3, 0x89ABCDEF);            // just for checking
		setInstructions(
				_88, _20, _00, _1F        // SRL R2,(R10,0xFFF)	shift R2 right by 31 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 0x00000001); // expected result in R2-3
		checkGPR(3, 0x89ABCDEF); // must be unchanged 
	}
	
	@Test
	public void x88_RS_ShiftRightLogical_08_zero_shiftBy_5() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x00000000);            // op1 in R2
		setInstructions(
				_88, _20, _00, _05        // SRL R2,(R10,0x005)	shift R2 right by 0 bit 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 0x00000000); // expected result in R2
	}

}
