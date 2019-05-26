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
public class Cpu370BcTest_Logic extends AbstractCpu370BcTest {
	
	public Cpu370BcTest_Logic(Class<? extends Cpu370Bc> cpuClass) {
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
	
	/**************
	***************   ** AND **  ****  ****  ****  ****  ****  ****  ****  ****  ****  ****
	**************/
	
	
	/*
	** Tests:  0x54 -- N [RX] - And
	*/

	@Test
	public void x54_RX_And_01_X2zero_B2_DISP() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010F80, 0xFFFFFFFF);    // op2 at address: 0x010180
		setGPR(3,         0x93C6F00F);    // op1 in R3
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_54, _30, _2F, _80        // N R3 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3,       0x93C6F00F); // AND result value 
		checkCC(CC1);            // result is non-zero
	}

	@Test
	public void x54_RX_And_02_X2_B2_DISP() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180, 0xFFFFFFFF);    // op2 at address: 0x010180
		setGPR(3,         0x93C6F00F);    // op1 in R3
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_54, _31, _20, _80        // N R3 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3,       0x93C6F00F); // AND result value 
		checkCC(CC1);            // result is non-zero
	}

	@Test
	public void x54_RX_And_03a_X2_B2zero_DISP() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010190, 0x12345678);    // op1 at address: 0x010180
		setGPR(3,         0x12345678);    // op1 in R3
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_54, _31, _00, _90        // N R3 <- (base=0,index=R1,offset=0x090)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0x12345678); /// AND result value 
		checkCC(CC1);            // result is non-zero
	}

	@Test
	public void x54_RX_And_0b_X2_B2zero_DISP() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010190, 0x87654321);    // op1 at address: 0x010180
		setGPR(3,         0x87654321);    // op1 in R3
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_54, _31, _00, _90        // N R3 <- (base=0,index=R1,offset=0x090)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0x87654321); /// AND result value 
		checkCC(CC1);            // result is non-zero
	}

	@Test
	public void x54_RX_And_04_X2_B2_ResultZero() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010190, 0xFFFFFFFF);    // op1 at address: 0x010180
		setGPR(3,         0x00000000);    // op1 in R3
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_54, _31, _20, _90        // N R3 <- (base=R2,index=R1,offset=0x090)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0);          // AND result value 
		checkCC(CC0);            // result is zero
	}
	
	
	/*
	** Tests:  0x14 -- NR [RR] - And Register
	*/

	@Test
	public void x14_RR_AndRegister_01_ResultNonZero() {
		setCC(CC3);
		setGPR(2,  0xFFFFFFFF);
		setGPR(10, 0x93C6F00F);
		setInstructions(
				_14, _2A           // NR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2,  0x93C6F00F);  // R2 must have been overwritten with the content of R10
		checkCC(CC1);              // result is non-zero
	}

	@Test
	public void x14_RR_AndRegister_02_ResultZero() {
		setCC(CC3);
		setGPR(2,  0x6C390FF0);
		setGPR(10, 0x93C6F00F);
		setInstructions(
				_14, _2A           // NR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2, 0x00000000);   // R2 must have been overwritten with the content of R10
		checkCC(CC0);              // result is zero
	}
	
	
	/*
	** Tests:  0x94 -- NI [SI] - And Immediate
	*/

	@Test
	public void x94_SI_AndImmediate_01_B2_DISP_ResultNonZero() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,_FF);            // op1 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_94, _93, _2F, _80        // NI x93 -> (base=R2,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _93); // AND result value 
		checkCC(CC1);             // result is non-zero
	}

	@Test
	public void x94_SI_AndImmediate_02_B2_zeroDISP_ResultZero() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,_6C);            // op1 at address: 0x010180
		setGPR(2, 0x010F80);              // set base register for addressing
		setInstructions(
				_94, _93, _20, _00        // NI x93 -> (base=R2,offset=0x000)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _00); // AND result value 
		checkCC(CC0);             // result is zero
	}
	
	
	/*
	** Tests:  0xD4 -- NC [SS] - And Characters
	*/

	@Test
	public void xD4_SS_AndCharacters_01_ResultNonZero() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,                 // op1 at address: 0x010180, 32 characters
				_93, _FF, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _71, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _17, _00, _00, _FF, _93
				);
		setGPR(2, 0x010F80);              // set base register for addressing of op1
		setMemB(0x020FC0,                 // op2 at address: 0x020FC0, 32 characters
				_FF, _93, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _71, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _17, _00, _00, _93, _FF
				);
		setGPR(3, 0x020000);              // set base register for addressing of op2
		setInstructions(
				_D4, _1F, _20, _00, _3F, _C0 // NC (base=R2,offset=0x000) <- (base=R3,offset=0xFC0), length=32
		);
		execute(1); // do one instruction
		checkIL(3);               // instruction length code
		checkIA(CodeBase+6);      // instruction address at end
		checkMemB(0x010F80,       // AND result value
				_93, _93, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _71, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _17, _00, _00, _93, _93
				);
		checkCC(CC1);             // result is non-zero
	}

	@Test
	public void xD4_SS_AndCharacters_02_ResultZero() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,                 // op1 at address: 0x010180, 32 characters
				_93, _6C, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _6C, _93
				);
		setGPR(2, 0x010F80);              // set base register for addressing of op1
		setMemB(0x020FC0,                 // op2 at address: 0x020FC0, 32 characters
				_6C, _93, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _93, _6C
				);
		setGPR(3, 0x020000);              // set base register for addressing of op2
		setInstructions(
				_D4, _1F, _20, _00, _3F, _C0 // NC (base=R2,offset=0x000) <- (base=R3,offset=0xFC0), length=32
		);
		execute(1); // do one instruction
		checkIL(3);               // instruction length code
		checkIA(CodeBase+6);      // instruction address at end
		checkMemB(0x010F80,       // AND result value
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00
				);
		checkCC(CC0);             // result is non-zero
	}


	
	/**************
	***************   ** OR **  ****  ****  ****  ****  ****  ****  ****  ****  ****  **** 
	**************/
	
	
	/*
	** Tests:  0x56 -- O [RX] - Or
	*/

	@Test
	public void x56_RX_Or_01_X2zero_B2_DISP() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010F80, 0x93C6F00F);    // op2 at address: 0x010180
		setGPR(3,         0x6C390FF0);    // op1 in R3
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_56, _30, _2F, _80        // O R3 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3,       0xFFFFFFFF); // OR result value 
		checkCC(CC1);            // result is non-zero
	}

	@Test
	public void x56_RX_Or_02_X2_B2_DISP() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180, 0x6C390FF0);    // op2 at address: 0x010180
		setGPR(3,         0x93C6F00F);    // op1 in R3
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_56, _31, _20, _80        // O R3 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3,       0xFFFFFFFF); // OR result value 
		checkCC(CC1);            // result is non-zero
	}

	@Test
	public void x56_RX_Or_03_X2_B2zero_DISP() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // op1 at address: 0x010180
		setGPR(3,         0x12345678);    // op1 in R3
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_56, _31, _00, _80        // O R3 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0x12345678); /// OR result value 
		checkCC(CC1);            // result is non-zero
	}

	@Test
	public void x56_RX_Or_04_X2_B2_ResultNonZero() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180, 0xFFFFFFFF);    // op1 at address: 0x010180
		setGPR(3,         0x00000000);    // op1 in R3
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_56, _31, _20, _80        // O R3 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0xFFFFFFFF);          // OR result value 
		checkCC(CC1);            // result is zero
	}

	@Test
	public void x56_RX_Or_04_X2_B2_ResultZero() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180, 0x00000000);    // op1 at address: 0x010180
		setGPR(3,         0x00000000);    // op1 in R3
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_56, _31, _20, _80        // O R3 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0x00000000);          // OR result value 
		checkCC(CC0);            // result is zero
	}
	
	
	/*
	** Tests:  0x16 -- OR [RR] - Or Register
	*/

	@Test
	public void x16_RR_OrRegister_01_ResultNonZero() {
		setCC(CC3);
		setGPR(2,  0x6C390FF0);
		setGPR(10, 0x93C6F00F);
		setInstructions(
				_16, _2A           // OR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2,  0xFFFFFFFF);  // OR result
		checkCC(CC1);              // result is non-zero
	}

	@Test
	public void x16_RR_OrRegister_02_ResultZero() {
		setCC(CC3);
		setGPR(2,  0x00000000);
		setGPR(10, 0x00000000);
		setInstructions(
				_16, _2A           // OR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2, 0x00000000);   // OR result
		checkCC(CC0);              // result is zero
	}
	
	
	/*
	** Tests:  0x96 -- OI [SI] - Or Immediate
	*/

	@Test
	public void x96_SI_OrImmediate_01a_B2_DISP_ResultNonZero() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,_6C);            // op1 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_96, _93, _2F, _80        // OI x93 -> (base=R2,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _FF); // OR result value 
		checkCC(CC1);             // result is non-zero
	}

	@Test
	public void x96_SI_OrImmediate_01b_B2_DISP_ResultNonZero() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,_93);            // op1 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_96, _6C, _2F, _80        // OI x6C -> (base=R2,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _FF); // OR result value 
		checkCC(CC1);             // result is non-zero
	}

	@Test
	public void x96_SI_OrImmediate_02_B2_zeroDISP_ResultZero() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,_00);            // op1 at address: 0x010180
		setGPR(2, 0x010F80);              // set base register for addressing
		setInstructions(
				_96, _00, _20, _00        // OI x93 -> (base=R2,offset=0x000)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _00); // OR result value 
		checkCC(CC0);             // result is zero
	}
	
	
	/*
	** Tests:  0xD6 -- NC [SS] - Or Characters
	*/

	@Test
	public void xD6_SS_OrCharacters_01_ResultNonZero() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,                 // op1 at address: 0x010180, 32 characters
				_93, _6C, _00, _10, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _F0, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _6C, _93
				);
		setGPR(2, 0x010F80);              // set base register for addressing of op1
		setMemB(0x020FC0,                 // op2 at address: 0x020FC0, 32 characters
				_6C, _93, _00, _01, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _0F, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _93, _6C
				);
		setGPR(3, 0x020000);              // set base register for addressing of op2
		setInstructions(
				_D6, _1F, _20, _00, _3F, _C0 // OC (base=R2,offset=0x000) <- (base=R3,offset=0xFC0), length=32
		);
		execute(1); // do one instruction
		checkIL(3);               // instruction length code
		checkIA(CodeBase+6);      // instruction address at end
		checkMemB(0x010F80,       // OR result value
				_FF, _FF, _00, _11, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _FF, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _FF, _FF
				);
		checkCC(CC1);             // result is non-zero
	}

	@Test
	public void xD6_SS_OrCharacters_02_ResultZero() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,                 // op1 at address: 0x010180, 32 characters
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00
				);
		setGPR(2, 0x010F80);              // set base register for addressing of op1
		setMemB(0x020FC0,                 // op1 at address: 0x020FC0, 32 characters
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00
				);
		setGPR(3, 0x020000);              // set base register for addressing of op2
		setInstructions(
				_D6, _1F, _20, _00, _3F, _C0 // OC (base=R2,offset=0x000) <- (base=R3,offset=0xFC0), length=32
		);
		execute(1); // do one instruction
		checkIL(3);               // instruction length code
		checkIA(CodeBase+6);      // instruction address at end
		checkMemB(0x010F80,       // OR result value
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00
				);
		checkCC(CC0);             // result is non-zero
	}


	
	/**************
	***************   ** XOR **  ****  ****  ****  ****  ****  ****  ****  ****  ****  **** 
	**************/
	
	
	/*
	** Tests:  0x57 -- X [RX] - Xor
	*/

	@Test
	public void x57_RX_Xor_01_X2zero_B2_DISP_ResultNonZero() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010F80, 0x6C390FF0);    // op2 at address: 0x010180
		setGPR(3,         0xFFFFFFFF);    // op1 in R3
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_57, _30, _2F, _80        // X R3 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3,       0x93C6F00F); // XOR result value 
		checkCC(CC1);            // result is non-zero
	}

	@Test
	public void x57_RX_Xor_02a_X2_B2_DISP_ResultNonZero() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180, 0x6C390FF0);    // op2 at address: 0x010180
		setGPR(3,         0x93C6F00F);    // op1 in R3
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_57, _31, _20, _80        // X R3 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3,       0xFFFFFFFF); // XOR result value 
		checkCC(CC1);            // result is non-zero
	}

	@Test
	public void x57_RX_Xor_02b_X2_B2_DISP_ResultNonZero() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180, 0x93C6F00F);    // op2 at address: 0x010180
		setGPR(3,         0x6C390FF0);    // op1 in R3
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_57, _31, _20, _80        // X R3 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3,       0xFFFFFFFF); // XOR result value 
		checkCC(CC1);            // result is non-zero
	}

	@Test
	public void x57_RX_Xor_03_X2_B2zero_DISP_ResultNonZero() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180, 0xFFFFFFFF);    // op1 at address: 0x010180
		setGPR(3,         0x93C6F00F);    // op1 in R3
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_57, _31, _00, _80        // X R3 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0x6C390FF0); /// XOR result value 
		checkCC(CC1);            // result is non-zero
	}

	@Test
	public void x57_RX_Xor_04_X2_B2_ResultNonZero() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180, 0xFFFFFFFF);    // op1 at address: 0x010180
		setGPR(3,         0x00000000);    // op1 in R3
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_57, _31, _20, _80        // X R3 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0xFFFFFFFF);          // XOR result value 
		checkCC(CC1);            // result is zero
	}

	@Test
	public void x57_RX_Xor_04a_X2_B2_ResultZero() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180, 0x00000000);    // op1 at address: 0x010180
		setGPR(3,         0x00000000);    // op1 in R3
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_57, _31, _20, _80        // X R3 <- (base=R2,index=R1,offset=0x090)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0x00000000);          // XOR result value 
		checkCC(CC0);            // result is zero
	}

	@Test
	public void x57_RX_Xor_04b_X2_B2_ResultZero() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180, 0x12345678);    // op1 at address: 0x010180
		setGPR(3,         0x12345678);    // op1 in R3
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_57, _31, _20, _80        // X R3 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0x00000000);          // XOR result value 
		checkCC(CC0);            // result is zero
	}

	@Test
	public void x57_RX_Xor_04c_X2_B2_ResultZero() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180, 0x87654321);    // op1 at address: 0x010180
		setGPR(3,         0x87654321);    // op1 in R3
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_57, _31, _20, _80        // X R3 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(3, 0x00000000);          // XOR result value 
		checkCC(CC0);            // result is zero
	}
	
	
	/*
	** Tests:  0x17 -- XR [RR] - Xor Register
	*/

	@Test
	public void x17_RR_XorRegister_01a_ResultNonZero() {
		setCC(CC3);
		setGPR(2,  0x6C390FF0);
		setGPR(10, 0x93C6F00F);
		setInstructions(
				_17, _2A           // XR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2,  0xFFFFFFFF);  // XOR result
		checkCC(CC1);              // result is non-zero
	}

	@Test
	public void x17_RR_XorRegister_01b_ResultNonZero() {
		setCC(CC3);
		setGPR(2,  0xFFFFFFFF);
		setGPR(10, 0x93C6F00F);
		setInstructions(
				_17, _2A           // XR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2,  0x6C390FF0);  // XOR result
		checkCC(CC1);              // result is non-zero
	}

	@Test
	public void x17_RR_XorRegister_02a_ResultZero() {
		setCC(CC3);
		setGPR(2,  0x00000000);
		setGPR(10, 0x00000000);
		setInstructions(
				_17, _2A           // XR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2, 0x00000000);   // XOR result
		checkCC(CC0);              // result is zero
	}

	@Test
	public void x17_RR_XorRegister_02b_ResultZero() {
		setCC(CC3);
		setGPR(2,  0x12345678);
		setGPR(10, 0x12345678);
		setInstructions(
				_17, _2A           // XR R2 <- R10
		);
		execute(1);
		checkIL(1); 
		checkIA(CodeBase+2);
		checkGPR(2, 0x00000000);   // XOR result
		checkCC(CC0);              // result is zero
	}
	
	
	/*
	** Tests:  0x97 -- XI [SI] - Xor Immediate
	*/

	@Test
	public void x97_SI_XorImmediate_01_B2_DISP_ResultNonZero() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80, _6C);           // op1 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_97, _93, _2F, _80        // XI x93 -> (base=R2,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _FF); // XOR result value 
		checkCC(CC1);             // result is non-zero
	}

	@Test
	public void x97_SI_XorImmediate_02a_B2_DISP_ResultNonZero() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80, _6C);           // op1 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_97, _FF, _2F, _80        // XI xFF -> (base=R2,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _93); // XOR result value 
		checkCC(CC1);             // result is non-zero
	}

	@Test
	public void x97_SI_XorImmediate_02b_B2_DISP_ResultNonZero() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80, _FF);           // op1 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_97, _6C, _2F, _80        // XI x6C -> (base=R2,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _93); // XOR result value 
		checkCC(CC1);             // result is non-zero
	}

	@Test
	public void x97_SI_XorImmediate_03_B2_zeroDISP_ResultZero() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80, _93);           // op1 at address: 0x010180
		setGPR(2, 0x010F80);              // set base register for addressing
		setInstructions(
				_97, _93, _20, _00        // XI x93 -> (base=R2,offset=0x000)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _00); // XOR result value 
		checkCC(CC0);             // result is zero
	}
	
	
	/*
	** Tests:  0xD7 -- XC [SS] - Xor Characters
	*/

	@Test
	public void xD7_SS_XorCharacters_01_ResultNonZero() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,                 // op1 at address: 0x010180, 32 characters
				_93, _6C, _FF, _FF, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _6C, _93
				);
		setGPR(2, 0x010F80);              // set base register for addressing of op1
		setMemB(0x020FC0,                 // op1 at address: 0x020FC0, 32 characters
				_6C, _93, _93, _6C, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _93, _6C
				);
		setGPR(3, 0x020000);              // set base register for addressing of op2
		setInstructions(
				_D7, _1F, _20, _00, _3F, _C0 // XC (base=R2,offset=0x000) <- (base=R3,offset=0xFC0), length=32
		);
		execute(1); // do one instruction
		checkIL(3);               // instruction length code
		checkIA(CodeBase+6);      // instruction address at end
		checkMemB(0x010F80,       // XOR result value
				_FF, _FF, _6C, _93, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _FF, _FF
				);
		checkCC(CC1);             // result is non-zero
	}

	@Test
	public void xD7_SS_XorCharacters_02_ResultZero() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,                 // op1 at address: 0x010180, 32 characters
				_93, _6C, _00, _FF, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _93, _6C
				);
		setGPR(2, 0x010F80);              // set base register for addressing of op1
		setMemB(0x020FC0,                 // op2 at address: 0x020FC0, 32 characters
				_93, _6C, _00, _FF, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _93, _6C
				);
		setGPR(3, 0x020000);              // set base register for addressing of op2
		setInstructions(
				_D7, _1F, _20, _00, _3F, _C0 // XC (base=R2,offset=0x000) <- (base=R3,offset=0xFC0), length=32
		);
		execute(1); // do one instruction
		checkIL(3);               // instruction length code
		checkIA(CodeBase+6);      // instruction address at end
		checkMemB(0x010F80,       // XOR result value
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00
				);
		checkCC(CC0);             // result is non-zero
	}
	
}
