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
public class Cpu370BcTest_Arith extends AbstractCpu370BcTest {
	
	public Cpu370BcTest_Arith(Class<? extends Cpu370Bc> cpuClass) {
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
	
	private final int IA_PGM_INTR_BASE = 0x00100000;
	
	/*
	** Tests:  0x5A -- A [RX] - Add
	*/

	@Test
	public void x5A_RX_Add_01a_X2zero_B2_DISP_ResultPositive() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // address: 0x010180
		setGPR(4, 0x11111111);            // R4 = positive 1. summand and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5A, _40, _21, _80        // A R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x23456789); // expected result of the sum
		checkCC(CC2);            // sum is greater than zero
	}

	@Test
	public void x5A_RX_Add_01b_X2zero_B2_DISP_ResultNegative() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // address: 0x010180
		setGPR(4, 0xB669FD2E);            // R4 = negative 1. summand (-1234567890) and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5A, _40, _21, _80        // A R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0xC89E53A6); // expected result of the sum (-929147994)
		checkCC(CC1);            // sum is less than zero
	}

	@Test
	public void x5A_RX_Add_01c_X2zero_B2_DISP_ResultZero() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // address: 0x010180, value: 305419896
		setGPR(4, 0xEDCBA988);            // R4 = negative 1. summand (-305419896) and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5A, _40, _21, _80        // A R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0);          // expected result of the sum (0)
		checkCC(CC0);            // sum is zero
	}

	@Test
	public void x5A_RX_Add_02a_X2_B2_DISP_PositiveOverflow() {		
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // address: 0x010180
		setGPR(4, 0x71111111);            // R4 = 1. summand and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_5A, _41, _20, _80        // A R4 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x83456789); // expected result of the sum (negative due to overflow)
		checkCC(CC3);            // overflow
	}

	@Test
	public void x5A_RX_Add_02b_X2_B2zero_DISP_PositiveOverflow_Interrupt() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // address: 0x010180
		setGPR(4, 0x71111111);            // R4 = 1. summand and future result 
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_5A, _41, _00, _80        // A R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x83456789);    // expected result of the sum (negative due to overflow)
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 2, 3, CodeBase+4); // ILC=2, CC=3
	}

	@Test
	public void x5A_RX_Add_03a_X2_B2zero_DISP_NegativeOverflow() {		
		setCC(CC1);                       // for later comparison
		setMemF(0x010180, 0x80000000);    // address: 0x010180, value: MIN(int) = -2147483648
		setGPR(4, 0x80000001);            // R4 = 1. summand and future result, value: MIN(int)+1 = -2147483647
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_5A, _41, _00, _80        // A R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x00000001); // expected result of the sum (positive (+1) due to overflow)
		checkCC(CC3);            // overflow
	}

	@Test
	public void x5A_RX_Add_03b_X2_B2zero_DISP_NegativeOverflow_Interrupt() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC1);                       // for later comparison
		setMemF(0x010180, 0x80000000);    // address: 0x010180, value: MIN(int) = -2147483648
		setGPR(4, 0x80000001);            // R4 = 1. summand and future result, value: MIN(int)+1 = -2147483647
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_5A, _41, _00, _80        // A R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x00000001);    // expected result of the sum (positive (+1) due to overflow)
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 2, 3, CodeBase+4); // ILC=2, CC=3
	}
	
	private void inner_x5a_compute(int op1, int op2, int expected) {
		setMemF(0x010180, op2);
		setGPR(1, 0x010100);
		setGPR(4, op1);
		setInstructions(_5A, _41, _00, _80);        // A R4 <- (base=0,index=R1,offset=0x080)
		execute(1);
		checkGPR(4, expected);
	}

	@Test
	public void x5A_RX_Add_04_samples() {
		
		inner_x5a_compute(1, 1, 2);
		inner_x5a_compute(2, -2, 0);
		inner_x5a_compute(-1, -1, -2);
		
		inner_x5a_compute(0x7F00FF00, 0x00FF00FF, 0x7FFFFFFF);
		
		inner_x5a_compute(2147483647, -2147483648, -1);
		inner_x5a_compute(2147483647, -2147483647, 0);
		inner_x5a_compute(2147483647, -2147483646, 1);
		inner_x5a_compute(2147483647, -1, 2147483646);
		
	}

	
	/*
	** Tests:  0x1A -- AR [RR] - Add Register
	*/

	@Test
	public void x1A_RR_AddReg_01a_ResultPositive() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setGPR(1, 0x12345678);            // R1 = 2. summand
		setGPR(4, 0x11111111);            // R4 = positive 1. summand and future result 
		setInstructions(
				_1A, _41                  // AR R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0x23456789); // expected result of the sum
		checkCC(CC2);            // sum is greater than zero
	}

	@Test
	public void x1A_RR_AddReg_01b_ResultNegative() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setGPR(1, 0x12345678);            // R1 = 2. summand
		setGPR(4, 0xB669FD2E);            // R4 = negative 1. summand (-1234567890) and future result 
		setInstructions(
				_1A, _41                  // AR R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0xC89E53A6); // expected result of the sum (-929147994)
		checkCC(CC1);            // sum is less than zero
	}

	@Test
	public void x1A_RR_AddReg_01c_ResultZero() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setGPR(1, 0x12345678);            // R1 = 2. summand
		setGPR(4, 0xEDCBA988);            // R4 = negative 1. summand (-305419896) and future result 
		setInstructions(
				_1A, _41                  // AR R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0);          // expected result of the sum (-929147994)
		checkCC(CC0);            // sum is less than zero
	}

	@Test
	public void x1A_RR_AddReg_02a_PositiveOverflow() {
		setCC(CC0);                       // for later comparison
		setGPR(1, 0x12345678);            // R1 = 2. summand
		setGPR(4, 0x71111111);            // R4 = negative 1. summand (-305419896) and future result 
		setInstructions(
				_1A, _41                  // AR R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0x83456789); // expected result of the sum (negative due to overflow)
		checkCC(CC3);            // overflow
	}

	@Test
	public void x1A_RR_AddReg_02b__PositiveOverflow_Interrupt() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC1);                       // for later comparison
		setGPR(1, 0x12345678);            // R1 = 2. summand
		setGPR(4, 0x71111111);            // R4 = negative 1. summand (-305419896) and future result 
		setInstructions(
				_1A, _41                  // AR R4 <- R1
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x83456789); // expected result of the sum (negative due to overflow)
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 1, 3, CodeBase+2); // ILC=1, CC=3
	}

	@Test
	public void x1A_RR_AddReg_03a__NegativeOverflow() {
		setCC(CC1);                       // for later comparison
		setGPR(1, 0x80000000);            // R1 = 2. summand, value: MIN(int) = -2147483648
		setGPR(4, 0x80000001);            // R4 = 1. summand and future result, value: MIN(int)+1 = -2147483647
		setInstructions(
				_1A, _41                  // AR R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0x00000001);   // expected result of the sum (positive (+1) due to overflow)
		checkCC(CC3);            // overflow
	}

	@Test
	public void x1A_RR_AddReg_03b__NegativeOverflow_Interrupt() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC1);                       // for later comparison
		setGPR(1, 0x80000000);            // R1 = 2. summand, value: MIN(int) = -2147483648
		setGPR(4, 0x80000001);            // R4 = 1. summand and future result, value: MIN(int)+1 = -2147483647
		setInstructions(
				_1A, _41                  // AR R4 <- R1
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x00000001);   // expected result of the sum (positive (+1) due to overflow)
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 1, 3, CodeBase+2); // ILC=1, CC=3
	}
	
	private void inner_x1a_compute(int op1, int op2, int expected) {
		setGPR(1, op2);
		setGPR(4, op1);
		setInstructions(_1A, _41);        // AR R4 <- R1
		execute(1);
		checkGPR(4, expected);
	}

	@Test
	public void x1A_RR_Add_04_samples() {
		
		inner_x1a_compute(1, 1, 2);
		inner_x1a_compute(2, -2, 0);
		inner_x1a_compute(-1, -1, -2);
		
		inner_x1a_compute(0x7F00FF00, 0x00FF00FF, 0x7FFFFFFF);
		
		inner_x1a_compute(2147483647, -2147483648, -1);
		inner_x1a_compute(2147483647, -2147483647, 0);
		inner_x1a_compute(2147483647, -2147483646, 1);
		inner_x1a_compute(2147483647, -1, 2147483646);
		
	}
	
	/*
	** Tests:  0x4A -- AH [RX] - Add Halfword
	*/

	@Test
	public void x4A_RX_AddHalfword_01a_X2zero_B2_DISP_ResultPositive() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setMemH(0x010884, (short)0x5678); // address: 0x010180, value: 22136
		setGPR(4, 0x11111111);            // R4 = positive 1. summand and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_4A, _40, _28, _84        // AH R4 <- (base=R2,index=0,offset=0x884)
		);
		execute(1); // do one instruction
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x11116789); // expected result of the sum
		checkCC(CC2);            // sum is greater than zero
	}

	@Test
	public void x4A_RX_AddHalfword_01b_X2zero_B2_DISP_ResultNegative() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setMemH(0x010180, (short)0x5678); // address: 0x010180, value: 22136
		setGPR(4, 0xB669FD2E);            // R4 = negative 1. summand (-1234567890) and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_4A, _40, _21, _80        // AH R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0xB66A53A6); // expected result of the sum (-1234545754)
		checkCC(CC1);            // sum is less than zero
	}

	@Test
	public void x4A_RX_AddHalfword_01c_X2zero_B2_DISP_ResultZero() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setMemH(0x010F80, (short)0x5678);  // address: 0x010180, value: 22136
		setGPR(4, 0xFFFFA988);            // R4 = negative 1. summand (-22136) and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_4A, _40, _2F, _80        // AH R4 <- (base=R2,index=0,offset=0xF80)
		);
		execute(1); // do one instruction
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0);          // expected result of the sum (0)
		checkCC(CC0);            // sum is zero
	}

	@Test
	public void x4A_RX_AddHalfword_02a_X2_B2_DISP_PositiveOverflow() {		
		setCC(CC0);                       // for later comparison
		setMemH(0x010180, (short)0x7FFF); // address: 0x010180, value: 32767
		setGPR(4, 0x7FFF8002);            // R4 = 1. summand and future result, value: 2147450882
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_4A, _41, _20, _80        // AH R4 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x80000001); // expected result of the sum (negative due to overflow)
		checkCC(CC3);            // overflow
	}

	@Test
	public void x4A_RX_AddHalfword_02b_X2_B2zero_DISP_PositiveOverflow_Interrupt() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setMemH(0x010180, (short)0x7FFF); // address: 0x010180, value: 32767
		setGPR(4, 0x7FFF8002);            // R4 = 1. summand and future result, value: 2147450882
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_4A, _41, _00, _80        // AH R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x80000001);    // expected result of the sum (negative due to overflow)
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 2, 3, CodeBase+4); // ILC=2, CC=3
	}

	@Test
	public void x4A_RX_AddHalfword_03a_X2_B2zero_DISP_NegativeOverflow() {		
		setCC(CC1);                       // for later comparison
		setMemH(0x010180, (short)0xFFFE); // address: 0x010180, value: MIN(int) = -2
		setGPR(4, 0x80000001);            // R4 = 1. summand and future result, value: MIN(int)+1 = -2147483647
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_4A, _41, _00, _80        // AH R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x7FFFFFFF); // expected result of the sum (positive (+MAX(int)) due to overflow)
		checkCC(CC3);            // overflow
	}

	@Test
	public void x4A_RX_AddHalfword_03b_X2_B2zero_DISP_NegativeOverflow_Interrupt() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC1);                       // for later comparison
		setMemH(0x010180, (short)0xFFFE); // address: 0x010180, value: MIN(int) = -2
		setGPR(4, 0x80000001);            // R4 = 1. summand and future result, value: MIN(int)+1 = -2147483647
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_4A, _41, _00, _80        // AH R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x7FFFFFFF); // expected result of the sum (positive (+MAX(int)) due to overflow)
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 2, 3, CodeBase+4); // ILC=2, CC=3
	}
	
	private void inner_x4a_compute(int op1, int op2, int expected) {
		setMemH(0x010180, (short)op2);
		setGPR(1, 0x010100);
		setGPR(4, op1);
		setInstructions(_4A, _41, _00, _80);        // A R4 <- (base=0,index=R1,offset=0x080)
		execute(1);
		checkGPR(4, expected);
	}

	@Test
	public void x4A_RX_Add_04_samples() {
		
		inner_x4a_compute(1, 1, 2);
		inner_x4a_compute(2, -2, 0);
		inner_x4a_compute(-1, -1, -2);
		
		inner_x4a_compute(0x7F00FF00, 0x000000FF, 0x7F00FFFF);

		inner_x4a_compute(-32768, 32767, -1);
		inner_x4a_compute(32767, -32768, -1);
		inner_x4a_compute(32768, -32768, 0);
		inner_x4a_compute(2147483647, -32768, 2147450879);
		inner_x4a_compute(2147483647, -1, 2147483646);
		
	}
	
	/*
	** Tests:  0x5E -- AL [RX] - Add Logical
	*/

	@Test
	public void x5E_RX_AddLogical_01a_X2zero_B2_DISP_NonZeroNoCarry() {
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // address: 0x010180, value: 305419896
		setGPR(4, 0x11111111);            // R4 = positive 1. summand (286331153) and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5E, _40, _21, _80        // AL R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x23456789); // expected result of the sum
		checkCC(CC1);            // sum is not zero, with no carry
	}

	@Test
	public void x5E_RX_AddLogical_01b_X2zero_B2_DISP_ZeroCarry() {
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x00000001);    // address: 0x010180, value 1
		setGPR(4, 0xFFFFFFFF);            // R4 = 1. summand (4294967295) and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5E, _40, _21, _80        // AL R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x00000000); // expected result of the sum (-929147994)
		checkCC(CC2);            // sum is zero, with carry
	}

	@Test
	public void x5E_RX_AddLogical_01c_X2zero_B2_DISP_NonZeroCarry() {
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // address: 0x010180, value: 305419896
		setGPR(4, 0xEDCBA989);            // R4 = 1. summand (3989547401) and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5E, _40, _21, _80        // AL R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 1);          // expected result of the sum (1)
		checkCC(CC3);            // sum is non-zero with carry
	}

	@Test
	public void x5E_RX_AddLogical_01d_X2zero_B2_DISP_ZeroNoCarry() {
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0);             // address: 0x010180, value: 0
		setGPR(4, 0);                     // R4 = 1. summand (0) and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5E, _40, _21, _80        // AL R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0);          // expected result of the sum (0)
		checkCC(CC0);            // sum is zero with no carry
	}

	@Test
	public void x5E_RX_AddLogical_02_X2_B2_DISP_NonZeroNoCarry() {		
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x5FFFFFFF);    // address: 0x010180, value: 1610612735
		setGPR(4, 0x12345678);            // R4 = 1. summand (305419896) and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_5E, _41, _20, _80        // AL R4 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x72345677); // expected result of the sum (1916032631)
		checkCC(CC1);            // non-zero, no carry
	}

	@Test
	public void x5E_RX_AddLogical_03_X2_B2zero_DISP_NonZeroNoCarry() {		
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x5FFFFFFF);    // address: 0x010180, value: 1610612735
		setGPR(4, 0x12345678);            // R4 = 1. summand (305419896) and future result 
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_5E, _41, _00, _80        // AL R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x72345677); // expected result of the sum (1916032631)
		checkCC(CC1);            // non-zero, no carry
	}
	
	/*
	** Tests:  0x1E -- ALR [RR] - Add Logical Registers
	*/

	@Test
	public void x1E_RR_AddLogicalRegister_01_NonZeroNoCarry() {
		setCC(CC0);                       // for later comparison
		setGPR(1, 0x12345678);            // R1 = 305419896
		setGPR(4, 0x11111111);            // R4 = positive 1. summand (286331153) and future result 
		setInstructions(
				_1E, _41                  // ALR R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0x23456789); // expected result of the sum
		checkCC(CC1);            // sum is not zero, with no carry
	}

	@Test
	public void x1E_RR_AddLogicalRegister_02_ZeroCarry() {
		setCC(CC0);                       // for later comparison
		setGPR(1, 0x00000001);            // R1 = 1
		setGPR(4, 0xFFFFFFFF);            // R4 = 1. summand (4294967295) and future result 
		setInstructions(
				_1E, _41                  // ALR R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0);          // expected result of the sum
		checkCC(CC2);            // sum is zero, with carry
	}

	@Test
	public void x1E_RR_AddLogicalRegister_03_NonZeroCarry() {
		setCC(CC0);                       // for later comparison
		setGPR(1, 0x12345678);            // R1 = 305419896
		setGPR(4, 0xEDCBA989);            // R4 = 1. summand (3989547401) and future result 
		setInstructions(
				_1E, _41                  // ALR R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 1);          // expected result of the sum (1)
		checkCC(CC3);            // sum is non-zero with carry
	}

	@Test
	public void x1E_RR_AddLogicalRegister_04_ZeroNoCarry() {
		setCC(CC0);                       // for later comparison
		setGPR(1, 0);                     // address: 0x010180, value: 0
		setGPR(4, 0);                     // R4 = 1. summand (0) and future result 
		setInstructions(
				_1E, _41                  // ALR R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0);          // expected result of the sum (0)
		checkCC(CC0);            // sum is zero with no carry
	}
	
	/*
	** Tests:  0x5B -- S [RX] - Subtract
	*/

	@Test
	public void x5B_RX_Subtract_01a_X2zero_B2_DISP_ResultNegative() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // positive 2. operand at address: 0x010180
		setGPR(4, 0x11111111);            // R4 = positive 1. operand and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5B, _40, _21, _80        // S R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0xFEDCBA99); // expected result of the subtraction
		checkCC(CC1);            // result is less than zero
	}

	@Test
	public void x5B_RX_Subtract_01b_X2zero_B2_DISP_ResultPositive() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0xB669FD2E);    // negative 2. operand at address: 0x010180
		setGPR(4, 0x12345678);            // R4 = positive 1. operand and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5B, _40, _21, _80        // S R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x5BCA594A); // expected result of the subtraction
		checkCC(CC2);            // result is greater than zero
	}

	@Test
	public void x5B_RX_Subtract_01c_X2zero_B2_DISP_2positives_ResultZero() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // positive 2. operand at address: 0x010180
		setGPR(4, 0x12345678);            // R4 = same positive 1. summand and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5B, _40, _21, _80        // S R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0);          // expected result of the subtraction (0)
		checkCC(CC0);            // result is zero
	}

	@Test
	public void x5B_RX_Subtract_01c_X2zero_B2_DISP_2negatives_ResultZero() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0xEDCBA988);    // negative 2. operand at address: 0x010180
		setGPR(4, 0xEDCBA988);            // R4 = same negative 1. operand (-305419896) and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5B, _40, _21, _80        // S R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0);          // expected result of the sum (0)
		checkCC(CC0);            // result is zero
	}

	@Test
	public void x5B_RX_Subtract_03a_X2_B2zero_DISP_minInt_to_maxInt() {		
		setCC(CC1);                       // for later comparison
		setMemF(0x010180, 0x80000000);    // address: 0x010180, value: MIN(int) = -2147483648
		setGPR(4, 0xFFFFFFFF);            // R4 = 1. operand = -1 and future result
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_5B, _41, _00, _80        // S R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x7FFFFFFF); // expected result of the subtraction: MAX(int) = 2147483647
		checkCC(CC2);            // result is positive
	}

	@Test
	public void x5B_RX_Subtract_02a_X2_B2_DISP_NegativeOverflow() {		
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // positive 1. operand address: 0x010180
		setGPR(4, 0x81000000);            // R4 = large negative 1. operand and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_5B, _41, _20, _80        // S R4 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x6ECBA988); // expected result of the subtraction (positive due to overflow)
		checkCC(CC3);            // overflow
	}

	@Test
	public void x5B_RX_Subtract_02b_X2_B2zero_DISP_NegativeOverflow_Interrupt() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // positive 1. operand address: 0x010180
		setGPR(4, 0x81000000);            // R4 = large negative 1. operand and future result 
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_5B, _41, _00, _80        // S R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x6ECBA988); // expected result of the subtraction (positive due to overflow)
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 2, 3, CodeBase+4); // ILC=2, CC=3
	}

	@Test
	public void x5B_RX_Subtract_03a_X2_B2zero_DISP_PositiveOverflow() {		
		setCC(CC1);                       // for later comparison
		setMemF(0x010180, 0x80000000);    // address: 0x010180, value: MIN(int) = -2147483648
		setGPR(4, 0x7FFFFFFF);            // R4 = 1. operand and future result, value: MAX(int) = 2147483647
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_5B, _41, _00, _80        // S R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0xFFFFFFFF); // expected result (negative (-1) due to overflow)
		checkCC(CC3);            // overflow
	}

	@Test
	public void x5B_RX_Subtract_03b_X2_B2zero_DISP_PositiveOverflow_Interrupt() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC1);                       // for later comparison
		setMemF(0x010180, 0x80000000);    // address: 0x010180, value: MIN(int) = -2147483648
		setGPR(4, 0x7FFFFFFF);            // R4 = 1. operand and future result, value: MAX(int) = 2147483647
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_5B, _41, _00, _80        // S R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0xFFFFFFFF); // expected result (negative (-1) due to overflow)
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 2, 3, CodeBase+4); // ILC=2, CC=3
	}
	
	private void inner_x5b_compute(int op1, int op2, int expected) {
		setMemF(0x010180, op2);
		setGPR(1, 0x010100);
		setGPR(4, op1);
		setInstructions(_5B, _41, _00, _80);        // S R4 <- (base=0,index=R1,offset=0x080)
		execute(1);
		checkGPR(4, expected);
	}

	@Test
	public void x5B_RX_Subtract_04_samples() {
		
		inner_x5b_compute(1, 1, 0);
		inner_x5b_compute(2, -2, 4);
		inner_x5b_compute(-1, -1, 0);
		inner_x5b_compute(-33, 33, -66);
		
		inner_x5b_compute(0x7FFFFFFF, 0x00FF00FF, 0x7F00FF00);
		
		inner_x5b_compute(-1, 2147483647, -2147483648);
		inner_x5b_compute(0, 2147483647, -2147483647);
		inner_x5b_compute(1, 2147483647, -2147483646);
		inner_x5b_compute(2147483646, -1, 2147483647);
		
	}
	
	/*
	** Tests:  0x1B -- SR [RR] - Subtract Register
	*/

	@Test
	public void x1B_RX_SubtractRegister_01a_X2zero_B2_DISP_ResultNegative() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setGPR(1, 0x12345678);            // R1 = positive 2. operand
		setGPR(4, 0x11111111);            // R4 = positive 1. operand and future result 
		setInstructions(
				_1B, _41                  // S R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0xFEDCBA99); // expected result of the subtraction
		checkCC(CC1);            // result is less than zero
	}

	@Test
	public void x1B_RX_SubtractRegister_01b_X2zero_B2_DISP_ResultPositive() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setGPR(1, 0xB669FD2E);            // R1 = negative 2. operand
		setGPR(4, 0x12345678);            // R4 = positive 1. operand and future result 
		setInstructions(
				_1B, _41                  // S R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0x5BCA594A); // expected result of the subtraction
		checkCC(CC2);            // result is greater than zero
	}

	@Test
	public void x1B_RX_SubtractRegister_01c_X2zero_B2_DISP_2positives_ResultZero() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setGPR(1, 0x12345678);            // R1 = positive 2. operand
		setGPR(4, 0x12345678);            // R4 = same positive 1. summand and future result 
		setInstructions(
				_1B, _41                  // S R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0);          // expected result of the subtraction (0)
		checkCC(CC0);            // result is zero
	}

	@Test
	public void x1B_RX_SubtractRegister_01c_X2zero_B2_DISP_2negatives_ResultZero() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setGPR(1, 0xEDCBA988);            // R1 = negative 2. operand
		setGPR(4, 0xEDCBA988);            // R4 = same negative 1. operand (-305419896) and future result 
		setInstructions(
				_1B, _41                  // S R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0);          // expected result of the sum (0)
		checkCC(CC0);            // result is zero
	}

	@Test
	public void x1B_RX_SubtractRegister_03a_X2_B2zero_DISP_minInt_to_maxInt() {		
		setCC(CC1);                       // for later comparison
		setGPR(1, 0x80000000);            // R1 = 2. operand, value: MIN(int) = -2147483648
		setGPR(4, 0xFFFFFFFF);            // R4 = 1. operand = -1 and future result
		setInstructions(
				_1B, _41                  // S R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0x7FFFFFFF); // expected result of the subtraction: MAX(int) = 2147483647
		checkCC(CC2);            // result is positive
	}

	@Test
	public void x1B_RX_SubtractRegister_02a_X2_B2_DISP_NegativeOverflow() {		
		setCC(CC0);                       // for later comparison
		setGPR(1, 0x12345678);            // R1 = positive 1. operand
		setGPR(4, 0x81000000);            // R4 = large negative 1. operand and future result 
		setInstructions(
				_1B, _41                  // S R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0x6ECBA988); // expected result of the subtraction (positive due to overflow)
		checkCC(CC3);            // overflow
	}

	@Test
	public void x1B_RX_SubtractRegister_02b_X2_B2zero_DISP_NegativeOverflow_Interrupt() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setGPR(1, 0x12345678);            // R1 = positive 1. operand
		setGPR(4, 0x81000000);            // R4 = large negative 1. operand and future result 
		setInstructions(
				_1B, _41                  // S R4 <- R1
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x6ECBA988); // expected result of the subtraction (positive due to overflow)
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 1, 3, CodeBase+2); // ILC=1, CC=3
	}

	@Test
	public void x1B_RX_SubtractRegister_03a_X2_B2zero_DISP_PositiveOverflow() {		
		setCC(CC1);                       // for later comparison
		setGPR(1, 0x80000000);            // R1 = 2. operand, value: MIN(int) = -2147483648
		setGPR(4, 0x7FFFFFFF);            // R4 = 1. operand and future result, value: MAX(int) = 2147483647
		setInstructions(
				_1B, _41                  // S R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0xFFFFFFFF); // expected result (negative (-1) due to overflow)
		checkCC(CC3);            // overflow
	}

	@Test
	public void x1B_RX_SubtractRegister_03b_X2_B2zero_DISP_PositiveOverflow_Interrupt() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC1);                       // for later comparison
		setGPR(1, 0x80000000);            // R1 = 1. operand, value: MIN(int) = -2147483648
		setGPR(4, 0x7FFFFFFF);            // R4 = 1. operand and future result, value: MAX(int) = 2147483647
		setInstructions(
				_1B, _41                  // S R4 <- R1
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0xFFFFFFFF); // expected result (negative (-1) due to overflow)
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 1, 3, CodeBase+2); // ILC=1, CC=3
	}
	
	private void inner_x1b_compute(int op1, int op2, int expected) {
		setGPR(1, op2);
		setGPR(4, op1);
		setInstructions(_1B, _41);        // S R4 <- R1
		execute(1);
		checkGPR(4, expected);
	}

	@Test
	public void x1B_RX_SubtractRegister_04_samples() {
		
		inner_x1b_compute(1, 1, 0);
		inner_x1b_compute(2, -2, 4);
		inner_x1b_compute(-1, -1, 0);
		inner_x1b_compute(-33, 33, -66);
		
		inner_x1b_compute(0x7FFFFFFF, 0x00FF00FF, 0x7F00FF00);
		
		inner_x1b_compute(-1, 2147483647, -2147483648);
		inner_x1b_compute(0, 2147483647, -2147483647);
		inner_x1b_compute(1, 2147483647, -2147483646);
		inner_x1b_compute(2147483646, -1, 2147483647);
		
	}
	
	/*
	** Tests:  0x4B -- SH [RX] - Subtract Halfword
	*/

	@Test
	public void x4B_RX_SubtractHalfword_01a_X2zero_B2_DISP_ResultPositive() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setMemH(0x010884, (short)0xA988); // address: 0x010180, value: -22136
		setGPR(4, 0x11111111);            // R4 = positive 1. operand and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_4B, _40, _28, _84        // SH R4 <- (base=R2,index=0,offset=0x884)
		);
		execute(1); // do one instruction
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x11116789); // expected result of the subtraction
		checkCC(CC2);            // result is greater than zero
	}

	@Test
	public void x4B_RX_SubtractHalfword_01b_X2zero_B2_DISP_ResultNegative() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setMemH(0x010180, (short)0xA988); // address: 0x010180, value: -22136
		setGPR(4, 0xB669FD2E);            // R4 = negative 1. operand (-1234567890) and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_4B, _40, _21, _80        // SH R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0xB66A53A6); // expected result of the subtraction (-1234545754)
		checkCC(CC1);            // result is less than zero
	}

	@Test
	public void x4B_RX_SubtractHalfword_01c_X2zero_B2_DISP_ResultZero() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setMemH(0x010F80, (short)0xA988);  // address: 0x010180, value: -22136
		setGPR(4, 0xFFFFA988);            // R4 = negative 1. operand (-22136) and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_4B, _40, _2F, _80        // SH R4 <- (base=R2,index=0,offset=0xF80)
		);
		execute(1); // do one instruction
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0);          // expected result of the subtraction (0)
		checkCC(CC0);            // result is zero
	}

	@Test
	public void x4B_RX_SubtractHalfword_02a_X2_B2_DISP_PositiveOverflow() {		
		setCC(CC0);                       // for later comparison
		setMemH(0x010180, (short)0x8001); // address: 0x010180, value: -32767
		setGPR(4, 0x7FFF8002);            // R4 = 1. operand and future result, value: 2147450882
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_4B, _41, _20, _80        // SH R4 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x80000001); // expected result of the subtraction (negative due to overflow)
		checkCC(CC3);            // overflow
	}

	@Test
	public void x4B_RX_SubtractHalfword_02b_X2_B2zero_DISP_PositiveOverflow_Interrupt() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);                       // for later comparison
		setMemH(0x010180, (short)0x8001); // address: 0x010180, value: -32767
		setGPR(4, 0x7FFF8002);            // R4 = 1. operand and future result, value: 2147450882
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_4B, _41, _00, _80        // SH R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x80000001);    // expected result of the subtraction (negative due to overflow)
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 2, 3, CodeBase+4); // ILC=2, CC=3
	}

	@Test
	public void x4B_RX_SubtractHalfword_03a_X2_B2zero_DISP_NegativeOverflow() {		
		setCC(CC1);                       // for later comparison
		setMemH(0x010180, (short)2);      // address: 0x010180, value: +2
		setGPR(4, 0x80000001);            // R4 = 1. operand and future result, value: MIN(int)+1 = -2147483647
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_4B, _41, _00, _80        // SH R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x7FFFFFFF); // expected result of the subtraction (positive (+MAX(int)) due to overflow)
		checkCC(CC3);            // overflow
	}

	@Test
	public void x4B_RX_SubtractHalfword_03b_X2_B2zero_DISP_NegativeOverflow_Interrupt() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC1);                       // for later comparison
		setMemH(0x010180, (short)2);      // address: 0x010180, value: +2
		setGPR(4, 0x80000001);            // R4 = 1. summand and future result, value: MIN(int)+1 = -2147483647
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_4B, _41, _00, _80        // SH R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x7FFFFFFF); // expected result of the subtraction (positive (+MAX(int)) due to overflow)
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 2, 3, CodeBase+4); // ILC=2, CC=3
	}
	
	private void inner_x4b_compute(int op1, int op2, int expected) {
		setMemH(0x010180, (short)op2);
		setGPR(1, 0x010100);
		setGPR(4, op1);
		setInstructions(_4B, _41, _00, _80);        // SH R4 <- (base=0,index=R1,offset=0x080)
		execute(1);
		checkGPR(4, expected);
	}

	@Test
	public void x4B_RX_Subtract_04_samples() {
		
		inner_x4b_compute(1, 1, 0);
		inner_x4b_compute(2, -2, 4);
		inner_x4b_compute(-1, -1, 0);
		
		inner_x4b_compute(0x7F00FFFF, 0x000000FF, 0x7F00FF00);

		inner_x4b_compute(-32768, -32767, -1);
		//inner_x4b_compute(32767, 32768, -1); // 32768 is not a short...
		//inner_x4b_compute(32768, -32768, 65535);
		//inner_x4b_compute(2147483647, 32768, 2147450879);
		inner_x4b_compute(2147483647, 1, 2147483646);
		
	}
	
	/*
	** Tests:  0x5F -- SL [RX] - Subtract Logical
	*/

	@Test
	public void x5F_RX_SubtractLogical_01a_X2zero_B2_DISP_NonZeroNoCarry() {
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x11111111);    // 2. operand at address: 0x010180
		setGPR(4, 0x12345678);            // R4 = positive 1. operand (286331153) and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5F, _40, _21, _80        // SL R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x01234567); // expected result of the subtraction
		checkCC(CC1);            // result is not zero, with no carry
	}

	@Test
	public void x5F_RX_SubtractLogical_01b_X2zero_B2_DISP_ZeroCarry() {
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0xFFFFFFFF);    // 2. operand at address: 0x010180
		setGPR(4, 0xFFFFFFFF);            // R4 = 1. operand and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5F, _40, _21, _80        // SL R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x00000000); // expected result of the subtraction
		checkCC(CC2);            // result is zero, with carry
	}

	@Test
	public void x5F_RX_SubtractLogical_01c_X2zero_B2_DISP_NonZeroNoCarry() {
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x12345677);    // 2. operand at address: 0x010180
		setGPR(4, 0x12345678);            // R4 = 1. operand and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5F, _40, _21, _80        // SL R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 1);          // expected result of the subtraction
		checkCC(CC1);            // result is non-zero with carry
	}

	@Test
	public void x5F_RX_SubtractLogical_01d_X2zero_B2_DISP_ZeroNoCarry() {
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // address: 0x010180, value: 0
		setGPR(4, 0x12345678);            // R4 = 1. operand (0) and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5F, _40, _21, _80        // SL R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0);          // expected result of the subtraction
		checkCC(CC2);            // result is zero with carry ("A zero difference cannot be obtained without a carry-out of the sign position")
	}

	@Test
	public void x5F_RX_SubtractLogical_01e_X2zero_B2_DISP_ZeroNoCarry() {
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0);             // address: 0x010180, value: 0
		setGPR(4, 0);                     // R4 = 1. operand (0) and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5F, _40, _21, _80        // SL R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0);          // expected result of the subtraction
		checkCC(CC2);            // result is zero with carry ("A zero difference cannot be obtained without a carry-out of the sign position")
	}

	@Test
	public void x5F_RX_SubtractLogical_02_X2_B2_DISP_NonZeroNoCarry() {		
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // address: 0x010180
		setGPR(4, 0x5FFFFFFF);            // R4 = 1. operand and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_5F, _41, _20, _80        // SL R4 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x4DCBA987); // expected result of the subtraction
		checkCC(CC1);            // non-zero, no carry
	}

	@Test
	public void x5F_RX_SubtractLogical_03_X2_B2zero_DISP_NonZeroCarry() {		
		setCC(CC0);                       // for later comparison
		setMemF(0x010180, 0x5FFFFFFF);    // address: 0x010180
		setGPR(4, 0x12345678);            // R4 = 1. summand and future result 
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_5F, _41, _00, _80        // SL R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0xB2345679); // expected result of the subtraction
		checkCC(CC3);            // non-zero, with carry
	}
	
	/*
	** Tests:  0x1F -- SLR [RR] - Subtract Logical Register
	*/

	@Test
	public void x1F_RX_SubtractLogicalRegister_01a_X2zero_B2_DISP_NonZeroNoCarry() {
		setCC(CC0);                       // for later comparison
		setGPR(7, 0x11111111);            // R7: 2. operand
		setGPR(4, 0x12345678);            // R4 = positive 1. operand (286331153) and future result 
		setInstructions(
				_1F, _47                  // SLR R4 <- R7
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0x01234567); // expected result of the subtraction
		checkCC(CC1);            // result is not zero, with no carry
	}

	@Test
	public void x1F_RX_SubtractLogicalRegister_01b_X2zero_B2_DISP_ZeroCarry() {
		setCC(CC0);                       // for later comparison
		setGPR(7, 0xFFFFFFFF);            // R7: 2. operand
		setGPR(4, 0xFFFFFFFF);            // R4 = 1. operand and future result 
		setInstructions(
				_1F, _47                  // SLR R4 <- R7
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0x00000000); // expected result of the subtraction
		checkCC(CC2);            // result is zero, with carry
	}

	@Test
	public void x1F_RX_SubtractLogicalRegister_01c_X2zero_B2_DISP_NonZeroNoCarry() {
		setCC(CC0);                       // for later comparison
		setGPR(7, 0x12345677);            // R7: 2. operand
		setGPR(4, 0x12345678);            // R4 = 1. operand and future result 
		setInstructions(
				_1F, _47                  // SLR R4 <- R7
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 1);          // expected result of the subtraction (1)
		checkCC(CC1);            // result is non-zero with no carry
	}

	@Test
	public void x1F_RX_SubtractLogicalRegister_01d_X2zero_B2_DISP_ZeroNoCarry() {
		setCC(CC0);                       // for later comparison
		setGPR(7, 0x12345678);            // R7: 2. operand
		setGPR(4, 0x12345678);            // R4 = 1. operand (0) and future result 
		setInstructions(
				_1F, _47                  // SLR R4 <- R7
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0);          // expected result of the subtraction (0)
		checkCC(CC2);            // result is zero with carry ("A zero difference cannot be obtained without a carry-out of the sign position")
	}

	@Test
	public void x1F_RX_SubtractLogicalRegister_01e_X2zero_B2_DISP_ZeroNoCarry() {
		setCC(CC0);                       // for later comparison
		setGPR(7, 0);                     // R7: 2. operand
		setGPR(4, 0);                     // R4 = 1. operand (0) and future result 
		setInstructions(
				_1F, _47                  // SLR R4 <- R7
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0);          // expected result of the subtraction (0)
		checkCC(CC2);            // result is zero with carry ("A zero difference cannot be obtained without a carry-out of the sign position")
	}

	@Test
	public void x1F_RX_SubtractLogicalRegister_02_X2_B2_DISP_NonZeroNoCarry() {		
		setCC(CC0);                       // for later comparison
		setGPR(7, 0x12345678);            // R7: 2. operand
		setGPR(4, 0x5FFFFFFF);            // R4 = 1. operand and future result 
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_1F, _47                  // SLR R4 <- R7
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0x4DCBA987); // expected result of the subtraction
		checkCC(CC1);            // non-zero, no carry
	}

	@Test
	public void x1F_RR_SubtractLogicalRegister_03_X2_B2zero_DISP_NonZeroCarry() {		
		setCC(CC0);                       // for later comparison
		setGPR(7, 0x5FFFFFFF);            // R7: 2. operand
		setGPR(4, 0x12345678);            // R4 = 1. summand and future result 
		setInstructions(
				_1F, _47                  // SLR R4 <- R7
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0xB2345679); // expected result of the subtraction (1916032631)
		checkCC(CC3);            // non-zero, with carry
	}
	
	/*
	** Tests:  0x5C -- M [RX] - Multiply
	*/

	@Test
	public void x5C_RX_Multiply_01_X2zero_B2_DISP() {
		setCC(CC2);                       // for later comparison
		setGPR(5, 0x200);                 // R5 : 1. operand and target (R4-R5: "the multiplicand is taken from the odd register of the pair")
		setMemF(0x010180, 0x12345678);    // 2. operand at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5C, _40, _21, _80        // M R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x00000024); // higher 32 bit of the result
		checkGPR(5, 0x68ACF000); // lower 32 bit of the result
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void x5C_RX_Multiply_02_X2_B2_DISP() {
		setCC(CC3);                       // for later comparison
		setGPR(5, 0);                     // R5 : 1. operand and target (R4-R5: "the multiplicand is taken from the odd register of the pair")
		setMemF(0x010180, 0xF0000000);    // address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_5C, _41, _20, _80        // M R4 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x00000000); // higher 32 bit of the result
		checkGPR(5, 0x00000000); // lower 32 bit of the result
		checkCC(CC3);            // must stay unchanged
	}

	@Test
	public void x5C_RX_Multiply_03a_X2_B2zero_DISP_negative_positive() {
		setCC(CC0);                       // for later comparison
		setGPR(5, 0xFFFFFE00);            // R5 : 1. operand (negative 0x0200) and target (R4-R5: "the multiplicand is taken from the odd register of the pair")
		setMemF(0x010180, 0x12345678);    // 2. operand at address: 0x010180
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_5C, _41, _00, _80        // M R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0xFFFFFFDB); // higher 32 bit of the result (negative)
		checkGPR(5, 0x97531000); // lower 32 bit of the result
		checkCC(CC0);            // must stay unchanged
	}

	@Test
	public void x5C_RX_Multiply_03b_X2_B2zero_DISP_positive_negative() {
		setCC(CC1);                       // for later comparison
		setGPR(5, 0x12345678);            // R5 : 1. operand and target (R4-R5: "the multiplicand is taken from the odd register of the pair")
		setMemF(0x010180, 0xFFFFFE00);    // 2. operand at address: 0x010180 (negative 0x0200)
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_5C, _41, _00, _80        // M R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0xFFFFFFDB); // higher 32 bit of the result (negative)
		checkGPR(5, 0x97531000); // lower 32 bit of the result
		checkCC(CC1);            // must stay unchanged
	}

	@Test
	public void x5C_RX_Multiply_04_X2_B2_DISP_minInt_minInt() {
		setCC(CC3);                       // for later comparison
		setGPR(5, 0x80000000);            // R5 : 1. operand and target (R4-R5: "the multiplicand is taken from the odd register of the pair")
		setMemF(0x010180, 0x80000000);    // 2. operand address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_5C, _41, _20, _80        // M R4 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x40000000); // higher 32 bit of the result (negative x negative -> positive)
		checkGPR(5, 0x00000000); // lower 32 bit of the result
		checkCC(CC3);            // must stay unchanged
	}

	@Test
	public void x5C_RX_Multiply_05_X2_B2_DISP_oddOp1_Intr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparison
		setGPR(4, 0x87654321);            // for comparison
		setGPR(6, 0x44556677);            // for comparison
		setGPR(5, 0x12345678);            // R5 : 1. operand and target (R4-R5: "the multiplicand is taken from the odd register of the pair")
		setMemF(0x010180, 0x33333333);    // 2. operand at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_5C, _51, _20, _80        // M R5 <- (base=R2,index=R1,offset=0x080) => op1-register is odd => specification exception
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x87654321);    // must be unchanged 
		checkGPR(5, 0x12345678);    // must be unchanged
		checkGPR(6, 0x44556677);    // must be unchanged
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 2, 3, CodeBase+4); // ILC=2, CC=unchanged
	}
	
	/*
	** Tests:  0x1C -- MR [RR] - Multiply Register
	*/

	@Test
	public void x1C_RR_MultiplyRegister_01_X2zero_B2_DISP() {
		setCC(CC2);                       // for later comparison
		setGPR(5, 0x200);                 // R5 : 1. operand and target (R4-R5: "the multiplicand is taken from the odd register of the pair")
		setGPR(2, 0x12345678);            // R2 : 2. operand
		setInstructions(
				_1C, _42                  // MR R4 <- R2
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0x00000024); // higher 32 bit of the result
		checkGPR(5, 0x68ACF000); // lower 32 bit of the result
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void x1C_RR_MultiplyRegister_02_X2_B2_DISP() {
		setCC(CC3);                       // for later comparison
		setGPR(5, 0);                     // R5 : 1. operand and target (R4-R5: "the multiplicand is taken from the odd register of the pair")
		setGPR(2, 0xF0000000);            // R2 : 2. operand
		setInstructions(
				_1C, _42                  // MR R4 <- R2
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0x00000000); // higher 32 bit of the result
		checkGPR(5, 0x00000000); // lower 32 bit of the result
		checkCC(CC3);            // must stay unchanged
	}

	@Test
	public void x1C_RR_MultiplyRegister_03a_X2_B2zero_DISP_negative_positive() {
		setCC(CC0);                       // for later comparison
		setGPR(5, 0xFFFFFE00);            // R5 : 1. operand (negative 0x0200) and target (R4-R5: "the multiplicand is taken from the odd register of the pair")
		setGPR(2, 0x12345678);            // R2 : 2. operand at address: 0x010180
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_1C, _42                  // MR R4 <- R2
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0xFFFFFFDB); // higher 32 bit of the result (negative)
		checkGPR(5, 0x97531000); // lower 32 bit of the result
		checkCC(CC0);            // must stay unchanged
	}

	@Test
	public void x1C_RR_MultiplyRegister_03b_X2_B2zero_DISP_positive_negative() {
		setCC(CC1);                       // for later comparison
		setGPR(5, 0x12345678);            // R5 : 1. operand and target (R4-R5: "the multiplicand is taken from the odd register of the pair")
		setGPR(2, 0xFFFFFE00);            // R2 : 2. operand (negative 0x0200)
		setInstructions(
				_1C, _42                  // MR R4 <- R2
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0xFFFFFFDB); // higher 32 bit of the result (negative)
		checkGPR(5, 0x97531000); // lower 32 bit of the result
		checkCC(CC1);            // must stay unchanged
	}

	@Test
	public void x1C_RR_MultiplyRegister_04_X2_B2_DISP_minInt_minInt() {
		setCC(CC3);                       // for later comparison
		setGPR(5, 0x80000000);            // R5 : 1. operand and target (R4-R5: "the multiplicand is taken from the odd register of the pair")
		setGPR(2, 0x80000000);            // R2 : 2. operand
		setInstructions(
				_1C, _42                  // MR R4 <- R2
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0x40000000); // higher 32 bit of the result (negative x negative -> positive)
		checkGPR(5, 0x00000000); // lower 32 bit of the result
		checkCC(CC3);            // must stay unchanged
	}

	@Test
	public void x1C_RR_MultiplyRegister_05_X2_B2_DISP_oddOp1_Intr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparison
		setGPR(4, 0x87654321);            // for comparison
		setGPR(6, 0x44556677);            // for comparison
		setGPR(5, 0x12345678);            // R5 : 1. operand and target (R4-R5: "the multiplicand is taken from the odd register of the pair")
		setGPR(2, 0x33333333);            // R2 : 2. operand
		setInstructions(
				_1C, _52                  // MR R5 <- R2 => op1-register is odd => specification exception
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x87654321);    // must be unchanged 
		checkGPR(5, 0x12345678);    // must be unchanged
		checkGPR(6, 0x44556677);    // must be unchanged
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 1, 3, CodeBase+2); // ILC=1, CC=unchanged
	}
	
	/*
	** Tests:  0x4C -- MH [RX] - Multiply Halfword
	*/

	@Test
	public void x4C_RX_MultiplyHalfword_01_X2zero_B2_DISP() {
		setCC(CC2);                       // for later comparison
		setGPR(5, 0x12345678);            // R5 : 1. operand and target
		setMemH(0x010180, (short)0x200);  // 2. operand at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_4C, _50, _21, _80        // MH R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(5, 0x68ACF000); // lower 32 bit of the result
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void x4C_RX_MultiplyHalfword_02_X2_B2_DISP() {
		setCC(CC3);                       // for later comparison
		setGPR(5, 0);                     // R5 : 1. operand and target
		setMemH(0x010180, (short)0xF000); // address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_4C, _51, _20, _80        // MH R4 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(5, 0x00000000); // 32 bit result
		checkCC(CC3);            // must stay unchanged
	}

	@Test
	public void x4C_RX_MultiplyHelafword_03a_X2_B2zero_DISP_negative_positive() {
		setCC(CC0);                       // for later comparison
		setGPR(5, 0xFFFFFE00);            // R5 : 1. operand (negative 0x0200) and target
		setMemH(0x010180, (short)0x5678); // 2. operand at address: 0x010180
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_4C, _51, _00, _80        // MH R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(5, 0xFF531000); // 32 bit result (negative)
		checkCC(CC0);            // must stay unchanged
	}

	@Test
	public void x4C_RX_MultiplyHalfword_03b_X2_B2zero_DISP_positive_negative() {
		setCC(CC1);                       // for later comparison
		setGPR(5, 0x12345678);            // R5 : 1. operand and target
		setMemH(0x010180, (short)0xFE00); // 2. operand at address: 0x010180 (negative 0x0200)
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_4C, _51, _00, _80        // MH R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(5, 0x97531000); // 32 bit result (negative)
		checkCC(CC1);            // must stay unchanged
	}

	@Test
	public void x4C_RX_MultiplyHalfword_04_X2_B2_DISP_minInt_minInt() {
		setCC(CC3);                       // for later comparison
		setGPR(5, 0x80000000);            // R5 : 1. operand and target
		setMemH(0x010180, (short)0x8000); // 2. operand address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_4C, _51, _20, _80        // MH R4 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(5, 0x00000000); // 32 bit result
		checkCC(CC3);            // must stay unchanged
	}
	
	/*
	** Tests:  0x5D -- D [RX] - Divide
	*
	* (a)  292363196024 == 0x 00000044 12345678
	* (b) -292363196024 == 0x FFFFFFBB EDCBA988
	* 
	* (c)           291 == 0x 00000123
	* (d)          -291 == 0x FFFFFEDD
	* 
	* positive_by_positive:
	* (a) / (c) -> quot =  1004684522 = 0x 3BE244EA
 	*              rest =         122 = 0x 0000007A
 	*
 	* negative_by_positive:
	* (b) / (c) -> quot = -1004684522 = 0x C41DBB16
	*              rest =        -122 = 0x FFFFFF86
	* 
	* positive_by_negative:
	* (a) / (d) -> quot = -1004684522 = 0x C41DBB16
	*              rest =         122 = 0x 0000007A
	* 
	* negative_by_negative:
	* (b) / (d) -> quot =  1004684522 = 0x 3BE244EA
	*              rest =        -122 = 0x FFFFFF86
	*
	*/

	@Test
	public void x5D_RX_Divide_01a_X2zero_B2_DISP_positive_by_positive() {
		setCC(CC2);                       // for later comparison
		setGPR(4, 0x0000003F);            // R4-R5: dividend
		setGPR(5, 0x12345678);            // 
		setMemF(0x010180, 0x00001234);    // 2. operand at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_5D, _40, _21, _80        // D R4 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x000004B4); // 32 bit remainder
		checkGPR(5, 0x03770075); // 32 bit quotient
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void x5D_RX_Divide_01b_X2_B2_DISP_positive_by_positive() {
		setCC(CC3);                       // for later comparison
		setGPR(4, 0x00000044);            // R4-R5: dividend
		setGPR(5, 0x12345678);            // ...............
		setMemF(0x010180, 0x00000123);    // divisor at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_5D, _41, _20, _80        // D R4 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x0000007A); // 32 bit remainder
		checkGPR(5, 0x3BE244EA); // 32 bit quotient
		checkCC(CC3);            // must stay unchanged
	}

	@Test
	public void x5D_RX_Divide_02_X2_B2zero_DISP_negative_by_positive() {
		setCC(CC0);                       // for later comparison
		setGPR(4, 0xFFFFFFBB);            // R4-R5: dividend
		setGPR(5, 0xEDCBA988);            // ...............
		setMemF(0x010180, 0x00000123);    // divisor at address: 0x010180
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_5D, _41, _00, _80        // D R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0xFFFFFF86); // 32 bit remainder
		checkGPR(5, 0xC41DBB16); // 32 bit quotient
		checkCC(CC0);            // must stay unchanged
	}

	@Test
	public void x5D_RX_Divide_03_X2_B2zero_DISP_positive_by_negative() {
		setCC(CC1);                       // for later comparison
		setGPR(4, 0x00000044);            // R4-R5: dividend
		setGPR(5, 0x12345678);            // ...............
		setMemF(0x010180, 0xFFFFFEDD);    // divisor at address: 0x010180
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_5D, _41, _00, _80        // D R4 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0x0000007A); // 32 bit remainder
		checkGPR(5, 0xC41DBB16); // 32 bit quotient
		checkCC(CC1);            // must stay unchanged
	}

	@Test
	public void x5D_RX_Divide_04_X2_B2_DISP_negative_by_negative() {
		setCC(CC3);                       // for later comparison
		setGPR(4, 0xFFFFFFBB);            // R4-R5: dividend
		setGPR(5, 0xEDCBA988);            // ...............
		setMemF(0x010180, 0xFFFFFEDD);    // divisor at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_5D, _41, _20, _80        // D R4 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(4, 0xFFFFFF86); // 32 bit remainder
		checkGPR(5, 0x3BE244EA); // 32 bit quotient
		checkCC(CC3);            // must stay unchanged
	}

	@Test
	public void x5D_RX_Divide_05a_X2_B2_DISP_oddOp1_specificationIntr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparison
		setGPR(4, 0x87654321);            // R4-R5: dividend
		setGPR(5, 0x12345678);            // ...............
		setMemF(0x010180, 0x33333333);    // 2. operand at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_5D, _51, _20, _80        // D R5 <- (base=R2,index=R1,offset=0x080) => op1-register is odd => specification exception
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x87654321);    // must be unchanged 
		checkGPR(5, 0x12345678);    // must be unchanged
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 2, 3, CodeBase+4); // ILC=2, CC=unchanged
	}

	@Test
	public void x5D_RX_Divide_05b_X2_B2_DISP_divisorZero_decimalDivideIntr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparison
		setGPR(4, 0x87654321);            // R4-R5: dividend
		setGPR(5, 0x12345678);            // ...............
		setMemF(0x010180, 0x00000000);    // divisor at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_5D, _41, _20, _80        // D R4 <- (base=R2,index=R1,offset=0x080) => op1-register is odd => specification exception
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x87654321);    // must be unchanged 
		checkGPR(5, 0x12345678);    // must be unchanged
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_DIVIDE, 2, 3, CodeBase+4); // ILC=2, CC=unchanged
	}

	@Test
	public void x5D_RX_Divide_05c_X2_B2_DISP_resultTooLargePositive_decimalDivideIntr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparison
		setGPR(4, 0x00000044);            // R4-R5: dividend
		setGPR(5, 0x12345678);            // ...............
		setMemF(0x010180, 0x00000044);    // divisor at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_5D, _41, _20, _80        // D R4 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x00000044);    // must be unchanged 
		checkGPR(5, 0x12345678);    // must be unchanged
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_DIVIDE, 2, 3, CodeBase+4); // ILC=2, CC=unchanged
	}

	@Test
	public void x5D_RX_Divide_05d_X2_B2_DISP_resultTooLargeNegative_decimalDivideIntr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparison
		setGPR(4, 0xFFFFFFBB);            // R4-R5: dividend
		setGPR(5, 0xEDCBA988);            // ...............
		setMemF(0x010180, 0x00000044);    // divisor at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_5D, _41, _20, _80        // D R4 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0xFFFFFFBB);    // must be unchanged 
		checkGPR(5, 0xEDCBA988);    // must be unchanged
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_DIVIDE, 2, 3, CodeBase+4); // ILC=2, CC=unchanged
	}
	
	/*
	** Tests:  0x1D -- DR [RR] - Divide Register
	*
	* (a)  292363196024 == 0x 00000044 12345678
	* (b) -292363196024 == 0x FFFFFFBB EDCBA988
	* 
	* (c)           291 == 0x 00000123
	* (d)          -291 == 0x FFFFFEDD
	* 
	* positive_by_positive:
	* (a) / (c) -> quot =  1004684522 = 0x 3BE244EA
 	*              rest =         122 = 0x 0000007A
 	*
 	* negative_by_positive:
	* (b) / (c) -> quot = -1004684522 = 0x C41DBB16
	*              rest =        -122 = 0x FFFFFF86
	* 
	* positive_by_negative:
	* (a) / (d) -> quot = -1004684522 = 0x C41DBB16
	*              rest =         122 = 0x 0000007A
	* 
	* negative_by_negative:
	* (b) / (d) -> quot =  1004684522 = 0x 3BE244EA
	*              rest =        -122 = 0x FFFFFF86
	*
	*/

	@Test
	public void x1D_RR_DivideRegister_01a_X2zero_B2_DISP_positive_by_positive() {
		setCC(CC2);                       // for later comparison
		setGPR(4, 0x0000003F);            // R4-R5: dividend
		setGPR(5, 0x12345678);            // 
		setGPR(1, 0x00001234);            // R1: divisor
		setInstructions(
				_1D, _41                  // DR R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0x000004B4); // 32 bit remainder
		checkGPR(5, 0x03770075); // 32 bit quotient
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void x1D_RR_DivideRegister_01b_X2_B2_DISP_positive_by_positive() {
		setCC(CC3);                       // for later comparison
		setGPR(4, 0x00000044);            // R4-R5: dividend
		setGPR(5, 0x12345678);            // ...............
		setGPR(1, 0x00000123);            // R1: divisor
		setInstructions(
				_1D, _41                  // DR R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0x0000007A); // 32 bit remainder
		checkGPR(5, 0x3BE244EA); // 32 bit quotient
		checkCC(CC3);            // must stay unchanged
	}

	@Test
	public void x1D_RR_DivideRegister_02_X2_B2zero_DISP_negative_by_positive() {
		setCC(CC0);                       // for later comparison
		setGPR(4, 0xFFFFFFBB);            // R4-R5: dividend
		setGPR(5, 0xEDCBA988);            // ...............
		setGPR(1, 0x00000123);            // R1: divisor
		setInstructions(
				_1D, _41                  // DR R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0xFFFFFF86); // 32 bit remainder
		checkGPR(5, 0xC41DBB16); // 32 bit quotient
		checkCC(CC0);            // must stay unchanged
	}

	@Test
	public void x1D_RR_DivideRegister_03_X2_B2zero_DISP_positive_by_negative() {
		setCC(CC1);                       // for later comparison
		setGPR(4, 0x00000044);            // R4-R5: dividend
		setGPR(5, 0x12345678);            // ...............
		setGPR(1, 0xFFFFFEDD);            // R1: divisor
		setInstructions(
				_1D, _41                  // DR R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0x0000007A); // 32 bit remainder
		checkGPR(5, 0xC41DBB16); // 32 bit quotient
		checkCC(CC1);            // must stay unchanged
	}

	@Test
	public void x1D_RR_DivideRegister_04_X2_B2_DISP_negative_by_negative() {
		setCC(CC3);                       // for later comparison
		setGPR(4, 0xFFFFFFBB);            // R4-R5: dividend
		setGPR(5, 0xEDCBA988);            // ...............
		setGPR(1, 0xFFFFFEDD);            // R1: divisor
		setInstructions(
				_1D, _41                  // DR R4 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(4, 0xFFFFFF86); // 32 bit remainder
		checkGPR(5, 0x3BE244EA); // 32 bit quotient
		checkCC(CC3);            // must stay unchanged
	}

	@Test
	public void x1D_RR_DivideRegister_05a_X2_B2_DISP_oddOp1_specificationIntr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparison
		setGPR(4, 0x87654321);            // R4-R5: dividend
		setGPR(5, 0x12345678);            // ...............
		setGPR(1, 0x33333333);            // R1: divisor
		setInstructions(
				_1D, _51                  // DR R5 <- R1
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x87654321);    // must be unchanged 
		checkGPR(5, 0x12345678);    // must be unchanged
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 1, 3, CodeBase+2); // ILC=1, CC=unchanged
	}

	@Test
	public void x1D_RR_DivideRegister_05b_X2_B2_DISP_divisorZero_decimalDivideIntr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparison
		setGPR(4, 0x87654321);            // R4-R5: dividend
		setGPR(5, 0x12345678);            // ...............
		setGPR(1, 0x00000000);            // R1: divisor
		setInstructions(
				_1D, _41                 // DR R4 <- R1
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x87654321);    // must be unchanged 
		checkGPR(5, 0x12345678);    // must be unchanged
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_DIVIDE, 1, 3, CodeBase+2); // ILC=1, CC=unchanged
	}

	@Test
	public void x1D_RR_DivideRegister_05c_X2_B2_DISP_resultTooLargePositive_decimalDivideIntr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparison
		setGPR(4, 0x00000044);            // R4-R5: dividend
		setGPR(5, 0x12345678);            // ...............
		setGPR(1, 0x00000044);            // R1: divisor
		setInstructions(
				_1D, _41                  // DR R4 <- R1
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0x00000044);    // must be unchanged 
		checkGPR(5, 0x12345678);    // must be unchanged
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_DIVIDE, 1, 3, CodeBase+2); // ILC=1, CC=unchanged
	}

	@Test
	public void x1D_RR_DivideRegister_05d_X2_B2_DISP_resultTooLargeNegative_decimalDivideIntr() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparison
		setGPR(4, 0xFFFFFFBB);            // R4-R5: dividend
		setGPR(5, 0xEDCBA988);            // ...............
		setGPR(1, 0x00000044);            // R1: divisor
		setInstructions(
				_1D, _41                  // DR R4 <- R1
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(4, 0xFFFFFFBB);    // must be unchanged 
		checkGPR(5, 0xEDCBA988);    // must be unchanged
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_DIVIDE, 1, 3, CodeBase+2); // ILC=1, CC=unchanged
	}

}
