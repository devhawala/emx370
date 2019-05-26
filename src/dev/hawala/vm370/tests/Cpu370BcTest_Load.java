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
public class Cpu370BcTest_Load extends AbstractCpu370BcTest {
	
	public Cpu370BcTest_Load(Class<? extends Cpu370Bc> cpuClass) {
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
	** Tests:  0x58 -- L [RX] - Load
	*/

	@Test
	public void x58_RX_Load_01_X2zero_B2_DISP() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_58, _30, _21, _80        // L R3 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0x12345678); // value that must have been loaded
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void x58_RX_Load_02_X2_B2_DISP() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180, 0x87654321);    // address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_58, _31, _20, _80        // L R3 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0x87654321); // value that must have been loaded
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void x58_RX_Load_03_X2_B2zero_DISP() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // address: 0x010180
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_58, _31, _00, _80        // L R3 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0x12345678); // value that must have been loaded
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void x58_RX_Load_04_X2_B2_DISPwrong() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_58, _31, _20, _90        // L R3 <- (base=R2,index=R1,offset=0x090)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0);          // value that must have been loaded (except above address: RAM is zeroed out)
		checkCC(CC2);            // must stay unchanged
	}

	
	/*
	** Tests:  0x18 -- LR [RR] - Load Register
	*/

	@Test
	public void x18_RR_LoadRegister_01() {
		setCC(CC3);
		setGPR(0,  0xFF000000);
		setGPR(1,  0x11111111);
		setGPR(2,  0x22222222);
		setGPR(3,  0x33333333);
		setGPR(4,  0x44444444);
		setGPR(5,  0x55555555);
		setGPR(6,  0x66666666);
		setGPR(7,  0x77777777);
		setGPR(8,  0x88888888);
		setGPR(9,  0x99999999);
		setGPR(10, 0xAAAAAAAA);
		setGPR(11, 0xBBBBBBBB);
		setGPR(12, 0xCCCCCCCC);
		setGPR(13, 0xDDDDDDDD);
		setGPR(14, 0xEEEEEEEE);
		setGPR(15, 0xFFFFFFFF);
		setInstructions(
				_18, _2A           // LR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(0,  0xFF000000);
		checkGPR(1,  0x11111111);
		checkGPR(2,  0xAAAAAAAA);  // R2 must have been overwritten with the content of R10
		checkGPR(3,  0x33333333);
		checkGPR(4,  0x44444444);
		checkGPR(5,  0x55555555);
		checkGPR(6,  0x66666666);
		checkGPR(7,  0x77777777);
		checkGPR(8,  0x88888888);
		checkGPR(9,  0x99999999);
		checkGPR(10, 0xAAAAAAAA);
		checkGPR(11, 0xBBBBBBBB);
		checkGPR(12, 0xCCCCCCCC);
		checkGPR(13, 0xDDDDDDDD);
		checkGPR(14, 0xEEEEEEEE);
		checkGPR(15, 0xFFFFFFFF);
		checkCC(CC3);
	}
	
	/*
	** Tests:  0x41 -- LA [RX] - Load Address
	*/

	@Test
	public void x41_RX_LoadAddress_01_X2zero_B2_DISP() {
		setCC(CC2);                       // for later comparison
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_41, _30, _21, _80        // L R3 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0x00010180); // value that must have been loaded
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void x41_RX_LoadAddress_02_X2_B2_DISP() {
		setCC(CC2);                       // for later comparison
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_41, _31, _20, _80        // L R3 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0x00010180); // value that must have been loaded
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void x41_RX_LoadAddress_03_X2_B2zero_DISP() {
		setCC(CC2);                       // for later comparison
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_41, _31, _00, _80        // L R3 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0x00010180); // value that must have been loaded
		checkCC(CC2);            // must stay unchanged
	}

	
	/*
	** Tests:  0x12 -- LTR [RR] - Load and Test Register
	*/

	@Test
	public void x12_RR_LoadAndTestRegister_01_lessZero() {
		setCC(CC3);
		setGPR(2,  0x22222222);
		setGPR(10, 0xAAAAAAAA);
		setInstructions(
				_12, _2A           // LTR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2,  0xAAAAAAAA);  // R2 must have been overwritten with the content of R10
		checkGPR(10, 0xAAAAAAAA);
		checkCC(CC1);              // result is less than zero
	}

	@Test
	public void x12_RR_LoadAndTestRegister_02_zero() {
		setCC(CC3);
		setGPR(2,  0x22222222);
		setGPR(10, 0x00000000);
		setInstructions(
				_12, _2A           // LTR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2,  0x00000000);  // R2 must have been overwritten with the content of R10
		checkGPR(10, 0x00000000);
		checkCC(CC0);              // result is zero
	}

	@Test
	public void x12_RR_LoadAndTestRegister_03_greaterZero() {
		setCC(CC3);
		setGPR(2,  0x22222222);
		setGPR(10, 0x33333333);
		setInstructions(
				_12, _2A           // LTR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2,  0x33333333);  // R2 must have been overwritten with the content of R10
		checkGPR(10, 0x33333333);
		checkCC(CC2);              // result is greater than zero
	}

	
	/*
	** Tests:  0x13 -- LCR [RR] - Load Complement Register
	*/

	@Test
	public void x13_RR_LoadComplementRegister_01_lessZero() {
		setCC(CC3);
		setGPR(2,  0x22222222);
		setGPR(10, 42);
		setInstructions(
				_13, _2A           // LCR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2, -42);
		checkGPR(10, 42);
		checkCC(CC1);              // result is less than zero
	}

	@Test
	public void x13_RR_LoadComplementRegister_02_zero() {
		setCC(CC3);
		setGPR(2,  0x22222222);
		setGPR(10, 0x00000000);
		setInstructions(
				_13, _2A           // LCR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2,  0x00000000);
		checkGPR(10, 0x00000000);
		checkCC(CC0);              // result is zero
	}

	@Test
	public void x12_RR_LoadComplementRegister_03_greaterZero() {
		setCC(CC3);
		setGPR(2,  0x22222222);
		setGPR(10, -42);
		setInstructions(
				_13, _2A           // LCR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2, 42);  // R2 must have been overwritten with the content of R10
		checkGPR(10, -42);
		checkCC(CC2);              // result is greater than zero
	}

	@Test
	public void x13_RR_LoadComplementRegister_04a_overflow() {
		setCC(CC3);
		setGPR(2,  0x22222222);
		setGPR(10, 0x80000000);
		setInstructions(
				_13, _2A           // LR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2,  0x80000000);
		checkGPR(10, 0x80000000);
		checkCC(CC3);              // overflow
	}

	@Test
	public void x13_RR_LoadComplementRegister_04b_overflowInterrupt() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC3);
		setGPR(2,  0x22222222);
		setGPR(10, 0x80000000);
		setInstructions(
				_13, _2A           // LR R2 <- R10
		);
		execute(1);
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(2,  0x80000000);  // R2 must have been overwritten with the content of R10
		checkGPR(10, 0x80000000);
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 1, 3, CodeBase+2); // ILC=2, CC=3
	}
	
	/*
	** Tests:  0x48 -- LH [RX] - Load Halfword
	*/

	@Test
	public void x48_RX_LoadHalfword_01_X2zero_B2_DISP() {
		setCC(CC3);                       // for later comparison
		setMemH(0x010180, (short)12345);  // op2 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_48, _A0, _21, _80        // LH R10 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, 12345);     // value loaded into R10
		checkMemH(0x010180, (short)12345); // must stay unchanged
		checkCC(CC3);            // must stay unchanged
	}

	@Test
	public void x48_RX_LoadHalfword_02_X2_B2_DISP() {
		setCC(CC2);                       // for later comparison
		setMemH(0x010180, (short)-12345); // op2 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_48, _A1, _20, _80        // LH R10 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, -12345);    // value loaded into R10
		checkMemH(0x010180, (short)-12345); // must stay unchanged
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void x48_RX_LoadHalfword_03_X2_B2zero_DISP() {
		setCC(CC1);                       // for later comparison
		setMemH(0x010180, (short)12345);  // op2 at address: 0x010180
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_48, _A1, _00, _80        // LH R10 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, 12345);     // value loaded into R10
		checkMemH(0x010180, (short)12345); // must stay unchanged
		checkCC(CC1);            // must stay unchanged
	}

	
	/*
	** Tests:  0x98 -- LM [RS] - Load Multiple
	*/

	@Test
	public void x98_RS_LoadMultiple_01_single() {
		setMemF(0x010F80,         // op2 = register-data at address: 0x010F80
			0x55555555
			);
		setCC(CC3);
		setGPR(0,  0xFF00FF00);
		setGPR(1,  0x00010000);              // R1: base register for 2. Operand
		setGPR(2,  0xFF00FF00);
		setGPR(3,  0xFF00FF00);
		setGPR(4,  0xFF00FF00);
		setGPR(5,  0xFF00FF00);
		setGPR(6,  0xFF00FF00);
		setGPR(7,  0xFF00FF00);
		setGPR(8,  0xFF00FF00);
		setGPR(9,  0xFF00FF00);
		setGPR(10, 0xFF00FF00);
		setGPR(11, 0xFF00FF00);
		setGPR(12, 0xFF00FF00);
		setGPR(13, 0xFF00FF00);
		setGPR(14, 0xFF00FF00);
		setGPR(15, 0xFF00FF00);
		setInstructions(
				_98, _55, _1F, _80        // LM R5,R5,(R1,0xF80) 
		);
		execute(1);
		checkIL(2); 
		checkIA(CodeBase+4);
		
		checkGPR(0,  0xFF00FF00);
		checkGPR(1,  0x00010000);
		checkGPR(2,  0xFF00FF00);
		checkGPR(3,  0xFF00FF00);
		checkGPR(4,  0xFF00FF00);
		checkGPR(5,  0x55555555);
		checkGPR(6,  0xFF00FF00);
		checkGPR(7,  0xFF00FF00);
		checkGPR(8,  0xFF00FF00);
		checkGPR(9,  0xFF00FF00);
		checkGPR(10, 0xFF00FF00);
		checkGPR(11, 0xFF00FF00);
		checkGPR(12, 0xFF00FF00);
		checkGPR(13, 0xFF00FF00);
		checkGPR(14, 0xFF00FF00);
		checkGPR(15, 0xFF00FF00);
		checkCC(CC3);
	}

	@Test
	public void x98_RS_LoadMultiple_02_noWrap() {
		setMemF(0x010F80,         // op2 = register-data at address: 0x010F80
			0x44444444,
			0x55555555,
			0x66666666,
			0x77777777,
			0x88888888,
			0x99999999,
			0xAAAAAAAA,
			0xBBBBBBBB
			);
		setCC(CC2);
		setGPR(0,  0xFF00FF00);
		setGPR(1,  0x00010000);              // R1: base register for 2. Operand
		setGPR(2,  0xFF00FF00);
		setGPR(3,  0xFF00FF00);
		setGPR(4,  0xFF00FF00);
		setGPR(5,  0xFF00FF00);
		setGPR(6,  0xFF00FF00);
		setGPR(7,  0xFF00FF00);
		setGPR(8,  0xFF00FF00);
		setGPR(9,  0xFF00FF00);
		setGPR(10, 0xFF00FF00);
		setGPR(11, 0xFF00FF00);
		setGPR(12, 0xFF00FF00);
		setGPR(13, 0xFF00FF00);
		setGPR(14, 0xFF00FF00);
		setGPR(15, 0xFF00FF00);
		setInstructions(
				_98, _4B, _1F, _80        // LM R4,R11,(R1,0xF80) 
		);
		execute(1);
		checkIL(2); 
		checkIA(CodeBase+4);

		checkGPR(0,  0xFF00FF00);
		checkGPR(1,  0x00010000);
		checkGPR(2,  0xFF00FF00);
		checkGPR(3,  0xFF00FF00);
		checkGPR(4,  0x44444444);
		checkGPR(5,  0x55555555);
		checkGPR(6,  0x66666666);
		checkGPR(7,  0x77777777);
		checkGPR(8,  0x88888888);
		checkGPR(9,  0x99999999);
		checkGPR(10, 0xAAAAAAAA);
		checkGPR(11, 0xBBBBBBBB);
		checkGPR(12, 0xFF00FF00);
		checkGPR(13, 0xFF00FF00);
		checkGPR(14, 0xFF00FF00);
		checkGPR(15, 0xFF00FF00);
		
		checkCC(CC2);
	}

	@Test
	public void x98_RS_LoadMultiple_03_wrap() {
		setMemF(0x010F80,         // op2 = register-data at address: 0x010F80
			0x88888888,
			0x99999999,
			0xAAAAAAAA,
			0xBBBBBBBB,
			0xCCCCCCCC,
			0xDDDDDDDD,
			0xEEEEEEEE,
			0xFFFFFFFF,
			0x00000000,
			0x11111111,
			0x22222222,
			0x33333333
			);
		setCC(CC1);
		setGPR(0,  0xFF00FF00);
		setGPR(1,  0x00010000);              // R1: base register for 2. Operand
		setGPR(2,  0xFF00FF00);
		setGPR(3,  0xFF00FF00);
		setGPR(4,  0xFF00FF00);
		setGPR(5,  0xFF00FF00);
		setGPR(6,  0xFF00FF00);
		setGPR(7,  0xFF00FF00);
		setGPR(8,  0xFF00FF00);
		setGPR(9,  0xFF00FF00);
		setGPR(10, 0xFF00FF00);
		setGPR(11, 0xFF00FF00);
		setGPR(12, 0xFF00FF00);
		setGPR(13, 0xFF00FF00);
		setGPR(14, 0xFF00FF00);
		setGPR(15, 0xFF00FF00);
		setInstructions(
				_98, _83, _1F, _80        // LM R8,R3,(R1,0xF80) 
		);
		execute(1);
		checkIL(2); 
		checkIA(CodeBase+4);

		checkGPR(0,  0x00000000);
		checkGPR(1,  0x11111111);
		checkGPR(2,  0x22222222);
		checkGPR(3,  0x33333333);
		checkGPR(4,  0xFF00FF00);
		checkGPR(5,  0xFF00FF00);
		checkGPR(6,  0xFF00FF00);
		checkGPR(7,  0xFF00FF00);
		checkGPR(8,  0x88888888);
		checkGPR(9,  0x99999999);
		checkGPR(10, 0xAAAAAAAA);
		checkGPR(11, 0xBBBBBBBB);
		checkGPR(12, 0xCCCCCCCC);
		checkGPR(13, 0xDDDDDDDD);
		checkGPR(14, 0xEEEEEEEE);
		checkGPR(15, 0xFFFFFFFF);
		
		checkCC(CC1);
	}

	
	/*
	** Tests:  0x11 -- LNR [RR] - Load Negative Register
	*/

	@Test
	public void x11_RR_LoadNegativeRegister_01_op2positive() {
		setCC(CC3);
		setGPR(2,  0x22222222);
		setGPR(10, 42);
		setInstructions(
				_11, _2A           // LNR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2, -42);
		checkGPR(10, 42);
		checkCC(CC1);              // result is less than zero
	}

	@Test
	public void x11_RR_LoadNegativeRegister_02_op2zero() {
		setCC(CC3);
		setGPR(2,  0x22222222);
		setGPR(10, 0);
		setInstructions(
				_11, _2A           // LNR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2, 0);
		checkGPR(10, 0);
		checkCC(CC0);              // result is zero
	}

	@Test
	public void x11_RR_LoadNegativeRegister_03_op2negative() {
		setCC(CC3);
		setGPR(2,  0x22222222);
		setGPR(10, -42);
		setInstructions(
				_11, _2A           // LNR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2, -42);
		checkGPR(10, -42);
		checkCC(CC1);              // result is less than zero
	}

	
	/*
	** Tests:  0x10 -- LPR [RR] - Load Positive Register
	*/

	@Test
	public void x10_RR_LoadPositiveRegister_01_op2positive() {
		setCC(CC3);
		setGPR(2,  0x22222222);
		setGPR(10, 42);
		setInstructions(
				_10, _2A           // LPR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2,  42);
		checkGPR(10, 42);
		checkCC(CC2);              // result is greater than zero
	}

	@Test
	public void x10_RR_LoadPositiveRegister_02_op2zero() {
		setCC(CC3);
		setGPR(2,  0x22222222);
		setGPR(10, 0);
		setInstructions(
				_10, _2A           // LPR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2, 0);
		checkGPR(10, 0);
		checkCC(CC0);              // result is zero
	}

	@Test
	public void x10_RR_LoadPositiveRegister_03_op2negative() {
		setCC(CC3);
		setGPR(2,  0x22222222);
		setGPR(10, -42);
		setInstructions(
				_10, _2A           // LPR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2,   42);
		checkGPR(10, -42);
		checkCC(CC2);              // result is greater than zero
	}

	@Test
	public void x10_RR_LoadPositiveRegister_04a_overflow() {
		setCC(CC0);
		setGPR(2,  0x22222222);
		setGPR(10, 0x80000000);
		setInstructions(
				_10, _2A           // LPR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2,  0x80000000);
		checkGPR(10, 0x80000000);
		checkCC(CC3);              // overflow
	}

	@Test
	public void x10_RR_LoadPositiveRegister_04b_overflowInterrupt() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler 
		setProgramMask(true, false, false, false); // enable ProgramInterrupts for FixedOverflow
		
		setCC(CC0);
		setGPR(2,  0x22222222);
		setGPR(10, 0x80000000);
		setInstructions(
				_10, _2A           // LPR R2 <- R10
		);
		execute(1);
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(2,  0x80000000);
		checkGPR(10, 0x80000000);
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_OVERFLOW, 1, 3, CodeBase+2); // ILC=2, CC=3
	}
	
	/*
	** Tests:  0x43 -- IC [RX] - Insert Character
	*/

	@Test
	public void x43_RX_InsertCharacter_01_X2zero_B2_DISP() {
		setCC(CC2);                       // for later comparison
		setGPR(3, 0x34567890);            // value in target register R3
		setMemF(0x010180, 0x12345678);    // address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_43, _30, _21, _80        // IC R3 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0x34567812); // R3 must have been modified on the most right byte
		checkMemF(0x010180, 0x12345678); // must stay unchanged
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void x43_RX_InsertCharacter_02_X2_B2_DISP() {
		setCC(CC3);                       // for later comparison
		setGPR(3, 0x34567890);            // value in target register R3
		setMemF(0x010180, 0x87654321);    // address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_43, _31, _20, _80        // IC R3 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0x34567887); // R3 must have been modified on the most right byte
		checkMemF(0x010180, 0x87654321); // must stay unchanged
		checkCC(CC3);            // must stay unchanged
	}

	@Test
	public void x43_RX_InsertCharacter_03_X2_B2zero_DISP() {
		setCC(CC1);                       // for later comparison
		setGPR(3, 0x34567890);            // value in target register R3
		setMemF(0x010180, 0x12345678);    // address: 0x010180
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_43, _31, _00, _80        // IC R3 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0x34567812); // R3 must have been modified on the most right byte
		checkMemF(0x010180, 0x12345678); // must stay unchanged
		checkCC(CC1);            // must stay unchanged
	}
	
	
	/*
	** Tests:  0xBF -- ICM [RS] - Insert Characters under Mask
	*/
	
	@Test
	public void xBF_RS_InsertCharactersUnderMask_01a_mask_0000() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x66666666);            // op1 in R2
		setMemF(0x010F80, 0x12345678);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BF, _20, _1F, _80        // ICM R2,0000b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // all bits inserted are zeros or mask is zero
		checkGPR(2, 0x66666666); // op1 is unchanged
		checkMemF(0x010F80, 0x12345678); // op2 must be unchanged
	}
	
	@Test
	public void xBF_RS_InsertCharactersUnderMask_01b_mask_1111_resultZero() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x66666666);            // op1 in R2
		setMemF(0x010F80, 0x00000000);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BF, _2F, _1F, _80        // ICM R2,1111b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // all bits inserted are zeros or mask is zero
		checkGPR(2, 0x00000000); // result value in op1
		checkMemF(0x010F80, 0x00000000); // op2 must be unchanged
	}
	
	@Test
	public void xBF_RS_InsertCharactersUnderMask_02a_mask_1111_CC2() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x66666666);            // op1 in R2
		setMemF(0x010F80, 0x12345678);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BF, _2F, _1F, _80        // ICM R2,1111b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // first bit inserted is zero and not all inserted bits are zeros
		checkGPR(2, 0x12345678); // op1 must be unchanged
		checkMemF(0x010F80, 0x12345678); // op2 must be unchanged
	}
	
	@Test
	public void xBF_RS_InsertCharactersUnderMask_02b_mask_0101_CC2() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x66666666);            // op1 in R2
		setMemF(0x010F80, 0x12345678);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BF, _25, _1F, _80        // ICM R2,0101b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // first bit inserted is zero and not all inserted bits are zeros
		checkGPR(2, 0x66126634); // op1 must be unchanged
		checkMemF(0x010F80, 0x12345678); // op2 must be unchanged
	}
	
	@Test
	public void xBF_RS_InsertCharactersUnderMask_02c_mask_0010_CC2() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x66666666);            // op1 in R2
		setMemF(0x010F80, 0x12345678);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BF, _22, _1F, _80        // ICM R2,00101b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // first bit inserted is zero and not all inserted bits are zeros
		checkGPR(2, 0x66661266); // op1 must be unchanged
		checkMemF(0x010F80, 0x12345678); // op2 must be unchanged
	}
	
	@Test
	public void xBF_RS_InsertCharactersUnderMask_03a_mask_1111_CC1() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x66666666);            // op1 in R2
		setMemF(0x010F80, 0xFEDCBA98);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BF, _2F, _1F, _80        // ICM R2,1111b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // first bit inserted is one
		checkGPR(2, 0xFEDCBA98); // op1 must be unchanged
		checkMemF(0x010F80, 0xFEDCBA98); // op2 must be unchanged
	}
	
	@Test
	public void xBF_RS_InsertCharactersUnderMask_03b_mask_0101_CC1() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x66666666);            // op1 in R2
		setMemF(0x010F80, 0xFEDCBA98);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BF, _25, _1F, _80        // ICM R2,0101b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // first bit inserted is one
		checkGPR(2, 0x66FE66DC); // op1 must be unchanged
		checkMemF(0x010F80, 0xFEDCBA98); // op2 must be unchanged
	}
	
	@Test
	public void xBF_RS_InsertCharactersUnderMask_03c_mask_0010_CC1() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x66666666);            // op1 in R2
		setMemF(0x010F80, 0xFEDCBA98);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BF, _22, _1F, _80        // ICM R2,1111b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // first bit inserted is one
		checkGPR(2, 0x6666FE66); // op1 must be unchanged
		checkMemF(0x010F80, 0xFEDCBA98); // op2 must be unchanged
	}
}
