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
public class Cpu370BcTest_Branch extends AbstractCpu370BcTest {
	
	public Cpu370BcTest_Branch(Class<? extends Cpu370Bc> cpuClass) {
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
	** Tests:  0x45 -- BAL [RX] - Branch And Link
	*/
	
	@Test
	public void x45_RX_BranchAndLink_01_X2zero_B2_DISP() {
		// setup data for link information
		setCC(CC1);
		setProgramMask(true, false, false, true);
		
		// do the test
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_45, _30, _2F, _80        // BAL R3 <- (base=R2,index=0,offset=0xF80)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(0x00010F80);     // instruction address at end 
		checkCC(CC1);            // must be unchanged
		
		// expected link information
		checkGPR(3, 0x99000000 + CodeBase + 4);  
	}

	@Test
	public void x45_RX_BranchAndLink_02_X2_B2_DISP() {
		// setup data for link information
		setCC(CC2);
		setProgramMask(false, true, false, true);
		
		// do the test
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_45, _31, _20, _80        // BAL R3 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(0x00010180);     // instruction address at end
		checkCC(CC2);            // must be unchanged
		
		// expected link information
		checkGPR(3, 0xA5000000 + CodeBase + 4);  
	}

	@Test
	public void x45_RX_BranchAndLink_03_X2_B2zero_DISP() {
		// setup data for link information
		setCC(CC3);
		setProgramMask(true, false, true, false);
		
		// do the test
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_45, _31, _00, _90        // BAL R3 <- (base=0,index=R1,offset=0x090)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(0x00010190);     // instruction address at end
		checkCC(CC3);            // must be unchanged
		
		// expected link information
		checkGPR(3, 0xBA000000 + CodeBase + 4);  
	}

	@Test
	public void x45_RX_BranchAndLink_04_X2_B2zero_DISP() {
		// setup data for link information
		setCC(CC0);
		setProgramMask(false, true, true, false);
		
		// do the test
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_45, _31, _0E, _FE        // BAL R3 <- (base=0,index=R1,offset=0xEFE)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(0x00010FFE);     // instruction address at end
		checkCC(CC0);            // must be unchanged
		
		// expected link information
		checkGPR(3, 0x86000000 + CodeBase + 4);  
	}
	
	
	/*
	** Tests:  0x05 -- BALR [RR] - Branch And Link Register
	*/
	
	@Test
	public void x05_RR_BranchAndLinkRegister_01() {
		// setup data for link information
		setCC(CC1);
		setProgramMask(true, false, false, true);
		
		// do the test
		setGPR(4, 0x12345678);            // branch target: 0x34567(
		setInstructions(
				_05, _34                  // BALR R3 <- R4
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(0x00345678);     // instruction address at end 
		checkCC(CC1);            // must be unchanged
		
		// expected link information
		checkGPR(3, 0x59000000 + CodeBase + 2);  
	}

	@Test
	public void x05_RX_BranchAndLinkRegister_02() {
		// setup data for link information
		setCC(CC2);
		setProgramMask(false, true, false, true);
		
		// do the test
		setGPR(4, 0x99987654);            // branch target: 0x987654
		setInstructions(
				_05, _34                  // BALR R3 <- R4
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(0x00987654);     // instruction address at end
		checkCC(CC2);            // must be unchanged
		
		// expected link information
		checkGPR(3, 0x65000000 + CodeBase + 2);  
	}

	@Test
	public void x05_RX_BranchAndLinkRegister_03() {
		// setup data for link information
		setCC(CC3);
		setProgramMask(true, false, true, false);
		
		// do the test
		setGPR(1, 0x99223344);              // branch target: 0x223344
		setInstructions(
				_05, _31                    // BALR R3 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(0x00223344);     // instruction address at end
		checkCC(CC3);            // must be unchanged
		
		// expected link information
		checkGPR(3, 0x7A000000 + CodeBase + 2);  
	}

	@Test
	public void x05_RX_BranchAndLinkRegister_04() {
		// setup data for link information
		setCC(CC0);
		setProgramMask(false, true, true, false);
		
		// do the test
		setGPR(12, 0x12121212);            // branch target: 0x121212
		setInstructions(
				_05, _3C                   // BALR R3 <- R12
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(0x00121212);     // instruction address at end
		checkCC(CC0);            // must be unchanged
		
		// expected link information
		checkGPR(3, 0x46000000 + CodeBase + 2);  
	}

	@Test
	public void x05_RX_BranchAndLinkRegister_05_NoBranch() {
		// setup data for link information
		setCC(CC3);
		setProgramMask(false, true, true, false);
		
		// do the test
		setGPR(0, 0x12121212);             // branch target: 0x121212, but R0 => no branch!
		setInstructions(
				_05, _30                   // BALR R3 <- R12
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase + 2);   // instruction address at end (R0 => no branch!)
		checkCC(CC3);            // must be unchanged
		
		// expected link information
		checkGPR(3, 0x76000000 + CodeBase + 2);  
	}
	
	@Test
	public void x05_RR_BranchAndLinkRegister_06_sameReg() {
		// setup data for link information
		setCC(CC1);
		setProgramMask(true, false, false, true);
		
		// do the test
		setGPR(3, 0x12345678);            // branch target: 0x34567(
		setInstructions(
				_05, _33                  // BALR R3 <- R3
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(0x00345678);     // instruction address at end 
		checkCC(CC1);            // must be unchanged
		
		// expected link information
		checkGPR(3, 0x59000000 + CodeBase + 2);  
	}
	
	
	/*
	** Tests:  0x47 -- BC [RX] - Branch on Condition
	*/
	
	@Test
	public void x47_RX_BranchOnCondition_01a_X2zero_B2_DISP_CC0_Branched() {
		setCC(CC0);
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_47, _80, _2F, _80        // BC CC0 <- (base=R2,index=0,offset=0xF80)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(0x00010F80);     // instruction address at end (branched)
		checkCC(CC0);            // must be unchanged
	}
	
	@Test
	public void x47_RX_BranchOnCondition_01b_X2zero_B2_DISP_CC0_NotBranched() {
		setCC(CC0);
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_47, _70, _2F, _80        // BC !CC0 <- (base=R2,index=0,offset=0xF80)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end (not branched)
		checkCC(CC0);            // must be unchanged
	}

	@Test
	public void x47_RX_BranchOnCondition_02a_X2_B2_DISP_CC1_Branched() {
		setCC(CC1);
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_47, _41, _20, _80        // BC CC1 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(0x00010180);     // instruction address at end (branched)
		checkCC(CC1);            // must be unchanged
	}

	@Test
	public void x47_RX_BranchOnCondition_02b_X2_B2_DISP_CC1_NotBranched() {
		setCC(CC1);
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_47, _B1, _20, _80        // BC !CC1 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end (not branched)
		checkCC(CC1);            // must be unchanged
	}

	@Test
	public void x47_RX_BranchOnCondition_03a_X2_B2zero_DISP_CC2_Branched() {
		setCC(CC2);
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_47, _21, _00, _90        // BC CC2 <- (base=0,index=R1,offset=0x090)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(0x00010190);     // instruction address at end (branched)
		checkCC(CC2);            // must be unchanged
	}

	@Test
	public void x47_RX_BranchOnCondition_03b_X2_B2zero_DISP_CC2_NotBranched() {
		setCC(CC2);
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_47, _D1, _00, _90        // BC !CC2 <- (base=0,index=R1,offset=0x090)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end (not branched)
		checkCC(CC2);            // must be unchanged
	}

	@Test
	public void x47_RX_BranchOnCondition_04a_X2_B2zero_DISP_CC3_Branched() {
		setCC(CC3);
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_47, _11, _0E, _FE        // BC CC3 <- (base=0,index=R1,offset=0xEFE)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(0x00010FFE);     // instruction address at end (branched)
		checkCC(CC3);            // must be unchanged
	}

	@Test
	public void x47_RX_BranchOnCondition_04b_X2_B2zero_DISP_CC3_NotBranched() {
		setCC(CC3);
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_47, _E1, _0E, _FE        // BC !CC3 <- (base=0,index=R1,offset=0xEFE)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end (not branched)
		checkCC(CC3);            // must be unchanged
	}
	
	
	/*
	** Tests:  0x07 -- BCR [RR] - Branch on Condition Register
	*/
	
	@Test
	public void x07_RR_BranchOnConditionRegister_01a_CC0_Branched() {
		setCC(CC0);
		setGPR(2, 0x010F80);              // R2: branch address
		setInstructions(
				_07, _82                  // BC CC0 <- R2
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(0x00010F80);     // instruction address at end (branched)
		checkCC(CC0);            // must be unchanged
	}
	
	@Test
	public void x07_RR_BranchOnConditionRegister_01b_CC0_NotBranched() {
		setCC(CC0);
		setGPR(2, 0x010F80);              // R2: branch address
		setInstructions(
				_07, _72                  // BC !CC0 <- R2
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase + 2);   // instruction address at end (not branched)
		checkCC(CC0);            // must be unchanged
	}

	@Test
	public void x07_RR_BranchOnConditionRegister_02a_CC1_Branched() {
		setCC(CC1);
		setGPR(2, 0x010180);              // R2: branch address
		setInstructions(
				_07, _42                  // BCR CC1 <- R2
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(0x00010180);     // instruction address at end (branched)
		checkCC(CC1);            // must be unchanged
	}

	@Test
	public void x07_RR_BranchOnConditionRegister_02b_CC1_NotBranched() {
		setCC(CC1);
		setGPR(2, 0x010180);              // R2: branch address
		setInstructions(
				_07, _B2                  // BCR !CC1 <- R2
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase + 2);   // instruction address at end (not branched)
		checkCC(CC1);            // must be unchanged
	}

	@Test
	public void x07_RR_BranchOnConditionRegister_03a_CC2_Branched() {
		setCC(CC2);
		setGPR(1, 0x010190);              // R1: branch address
		setInstructions(
				_07, _21                  // BCR CC2 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(0x00010190);     // instruction address at end (branched)
		checkCC(CC2);            // must be unchanged
	}

	@Test
	public void x07_RR_BranchOnConditionRegister_03b_CC2_NotBranched() {
		setCC(CC2);
		setGPR(1, 0x010190);              // R1: branch address
		setInstructions(
				_07, _D1                  // BCR !CC2 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase + 2);   // instruction address at end (not branched)
		checkCC(CC2);            // must be unchanged
	}

	@Test
	public void x07_RR_BranchOnConditionRegister_04a_CC3_Branched() {
		setCC(CC3);
		setGPR(1, 0x010FFE);              // R1: branch address
		setInstructions(
				_07, _11                  // BCR CC3 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(0x00010FFE);     // instruction address at end (branched)
		checkCC(CC3);            // must be unchanged
	}

	@Test
	public void x07_RR_BranchOnConditionRegister_04b_CC3_NotBranched() {
		setCC(CC3);
		setGPR(1, 0x010FFE);              // R1: branch address
		setInstructions(
				_07, _E1                  // BCR !CC3 <- R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase + 2);   // instruction address at end (not branched)
		checkCC(CC3);            // must be unchanged
	}

	@Test
	public void x07_RR_BranchOnConditionRegister_05_AnyCC_R0_NotBranched() {
		setCC(CC3);
		setGPR(0, 0x010FFE);              // R0: branch address
		setInstructions(
				_07, _F0                  // BCR unconditional <- R0
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase + 2);   // instruction address at end (not branched)
		checkCC(CC3);            // must be unchanged
	}
	
	
	/*
	** Tests:  0x46 -- BCT [RX] - Branch on Count
	*/
	
	@Test
	public void x46_RX_BranchOnCount_01_X2zero_B2_DISP_Branched() {
		setCC(CC1);
		setGPR(8, 2);                     // set register to be tested
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_46, _80, _2F, _80        // BCT R8 , (base=R2,index=0,offset=0xF80)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(0x00010F80);     // instruction address at end (branched)
		checkCC(CC1);            // must be unchanged
		checkGPR(8, 1);          // must be decremented
	}
	
	@Test
	public void x46_RX_BranchOnCount_02_X2_B2_DISP_Branched() {
		setCC(CC2);
		setGPR(8, 3);                     // set register to be tested
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_46, _81, _20, _80        // BCT R8 , (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(0x00010180);     // instruction address at end
		checkCC(CC2);            // must be unchanged
		checkGPR(8, 2);          // must be decremented
	}

	@Test
	public void x46_RX_BranchOnCount_03_X2_B2zero_DISP_Branched() {
		setCC(CC3);
		setGPR(8, -3);                    // set register to be tested
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_46, _81, _00, _90        // BCT R8 , (base=0,index=R1,offset=0x090)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(0x00010190);     // instruction address at end
		checkCC(CC3);            // must be unchanged
		checkGPR(8, -4);          // must be decremented
	}

	@Test
	public void x46_RX_BranchOnCount_04_X2_B2zero_DISP_NotBranched() {
		setCC(CC3);
		setGPR(8, 1);
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_46, _81, _0E, _FE        // BCT R8 , (base=0,index=R1,offset=0xEFE)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end (no branch)
		checkCC(CC3);            // must be unchanged
		checkGPR(8, 0);          // must be decremented
	}
	
	
	/*
	** Tests:  0x06 -- BCTR [RR] - Branch on Count Register
	*/
	
	@Test
	public void x06_RR_BranchOnCountRegister_01_Branched() {
		setCC(CC1);
		setGPR(8, 2);                     // R8: set register to be tested
		setGPR(2, 0x010F80);              // R2: branch address 
		setInstructions(
				_06, _82                  // BCTR R8 , R2 
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(0x00010F80);     // instruction address at end (branched)
		checkCC(CC1);            // must be unchanged
		checkGPR(8, 1);          // must be decremented
	}
	
	@Test
	public void x06_RR_BranchOnCountRegister_02_Branched() {
		setCC(CC1);
		setGPR(8, 0);                     // R8: set register to be tested
		setGPR(2, 0x010F80);              // R2: branch address 
		setInstructions(
				_06, _82                  // BCTR R8 , R2 
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(0x00010F80);     // instruction address at end (branched)
		checkCC(CC1);            // must be unchanged
		checkGPR(8, -1);         // must be decremented
	}
	
	@Test
	public void x06_RR_BranchOnCountRegister_03_NotBranched() {
		setCC(CC1);
		setGPR(8, 1);                     // R8: set register to be tested
		setGPR(2, 0x010F80);              // R2: branch address 
		setInstructions(
				_06, _82                  // BCTR R8 , R2 
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase + 2);   // instruction address at end (not branched)
		checkCC(CC1);            // must be unchanged
		checkGPR(8, 0);          // must be decremented
	}
	
	@Test
	public void x06_RR_BranchOnCountRegister_04_R0_NotBranched() {
		setCC(CC1);
		setGPR(8, 42);                    // R8: set register to be tested
		setGPR(0, 0x010F80);              // R0: branch address 
		setInstructions(
				_06, _80                  // BCTR R8 , R0 
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase + 2);   // instruction address at end (not branched)
		checkCC(CC1);            // must be unchanged
		checkGPR(8, 41);         // must be decremented
	}
	
	
	/*
	** Tests:  0x86 -- BXH [RS] - Branch on Index High
	*/
	
	@Test
	public void x86_RS_BranchOnIndexHigh_01_R3odd_NotBranched() {
		setCC(CC3);
		setGPR(2, -1);                    // R2: 1. summand and target
		setGPR(5, 1);                     // R5: increment and comparand (because reg-no is odd) 
		setGPR(1, 0x010000);              // R1: base register 
		setInstructions(
				_86, _25, _1F, _80        // BXH R2,R5,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end (result not high => not branched)
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 0);          // must be incremented by the value in R5
	}
	
	@Test
	public void x86_RS_BranchOnIndexHigh_02_R3odd_NotBranched() {
		setCC(CC3);
		setGPR(2, 0);                     // R2: 1. summand and target
		setGPR(5, 1);                     // R5: increment and comparand (because reg-no is odd) 
		setGPR(1, 0x010000);              // R1: base register 
		setInstructions(
				_86, _25, _1F, _80        // BXH R2,R5,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end (result not high => not branched)
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 1);          // must be incremented by the value in R5
	}
	
	@Test
	public void x86_RS_BranchOnIndexHigh_03_R3odd_Branched() {
		setCC(CC3);
		setGPR(2, 1);                     // R2: 1. summand and target
		setGPR(5, 1);                     // R5: increment and comparand (because reg-no is odd) 
		setGPR(1, 0x010000);              // R1: base register 
		setInstructions(
				_86, _25, _1F, _80        // BXH R2,R5,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(0x00010F80);     // instruction address at end (result high => branched)
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 2);          // must be incremented by the value in R5
	}
	
	@Test
	public void x86_RS_BranchOnIndexHigh_04_R3even_NotBranched() {
		setCC(CC3);
		setGPR(2, 0);                     // R2: 1. summand and target
		setGPR(4, 2);                     // R4: increment
		setGPR(5, 3);                     // R5: comparand (because reg-no is even)
		setGPR(1, 0x010000);              // R1: base register 
		setInstructions(
				_86, _24, _1F, _80        // BXH R2,R5,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end (result not high => not branched)
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 2);          // must be incremented by the value in R5
	}
	
	@Test
	public void x86_RS_BranchOnIndexHigh_05_R3even_NotBranched() {
		setCC(CC3);
		setGPR(2, 1);                     // R2: 1. summand and target
		setGPR(4, 2);                     // R4: increment
		setGPR(5, 3);                     // R5: comparand (because reg-no is even)
		setGPR(1, 0x010000);              // R1: base register 
		setInstructions(
				_86, _24, _1F, _80        // BXH R2,R5,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end (result not high => not branched)
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 3);          // must be incremented by the value in R5
	}
	
	@Test
	public void x86_RS_BranchOnIndexHigh_06_R3even_Branched() {
		setCC(CC3);
		setGPR(2, 2);                     // R2: 1. summand and target
		setGPR(4, 2);                     // R4: increment
		setGPR(5, 3);                     // R5: comparand (because reg-no is even)
		setGPR(1, 0x010000);              // R1: base register 
		setInstructions(
				_86, _24, _1F, _80        // BXH R2,R5,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(0x00010F80);     // instruction address at end (result high => branched)
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 4);          // must be incremented by the value in R5
	}
	
	
	/*
	** Tests:  0x87 -- BXLE [RS] - Branch on Index Low or Equal
	*/
	
	@Test
	public void x87_RS_BranchOnIndexLowOrEqual_01_R3odd_Branched() {
		setCC(CC3);
		setGPR(2, -1);                    // R2: 1. summand and target
		setGPR(5, 1);                     // R5: increment and comparand (because reg-no is odd) 
		setGPR(1, 0x010000);              // R1: base register 
		setInstructions(
				_87, _25, _1F, _80        // BXLE R2,R5,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(0x00010F80);     // instruction address at end (result low or equal => branched)
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 0);          // must be incremented by the value in R5
	}
	
	@Test
	public void x87_RS_BranchOnIndexLowOrEqual_02_R3odd_Branched() {
		setCC(CC3);
		setGPR(2, 0);                     // R2: 1. summand and target
		setGPR(5, 1);                     // R5: increment and comparand (because reg-no is odd) 
		setGPR(1, 0x010000);              // R1: base register 
		setInstructions(
				_87, _25, _1F, _80        // BXLE R2,R5,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(0x00010F80);     // instruction address at end (result low or equal => branched)
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 1);          // must be incremented by the value in R5
	}
	
	@Test
	public void x87_RS_BranchOnIndexLowOrEqual_03_R3odd_NotBranched() {
		setCC(CC3);
		setGPR(2, 1);                     // R2: 1. summand and target
		setGPR(5, 1);                     // R5: increment and comparand (because reg-no is odd) 
		setGPR(1, 0x010000);              // R1: base register 
		setInstructions(
				_87, _25, _1F, _80        // BXLE R2,R5,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end (result high => not branched)
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 2);          // must be incremented by the value in R5
	}
	
	@Test
	public void x87_RS_BranchOnIndexLowOrEqual_04_R3even_Branched() {
		setCC(CC3);
		setGPR(2, 0);                     // R2: 1. summand and target
		setGPR(4, 2);                     // R4: increment
		setGPR(5, 3);                     // R5: comparand (because reg-no is even)
		setGPR(1, 0x010000);              // R1: base register 
		setInstructions(
				_87, _24, _1F, _80        // BXLE R2,R5,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(0x00010F80);     // instruction address at end (result low or equal => branched)
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 2);          // must be incremented by the value in R5
	}
	
	@Test
	public void x87_RS_BranchOnIndexLowOrEqual_05_R3even_Branched() {
		setCC(CC3);
		setGPR(2, 1);                     // R2: 1. summand and target
		setGPR(4, 2);                     // R4: increment
		setGPR(5, 3);                     // R5: comparand (because reg-no is even)
		setGPR(1, 0x010000);              // R1: base register 
		setInstructions(
				_87, _24, _1F, _80        // BXLE R2,R5,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(0x00010F80);     // instruction address at end (result low or equal => branched)
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 3);          // must be incremented by the value in R5
	}
	
	@Test
	public void x87_RS_BranchOnIndexLowOrEqual_06_R3even_NotBranched() {
		setCC(CC3);
		setGPR(2, 2);                     // R2: 1. summand and target
		setGPR(4, 2);                     // R4: increment
		setGPR(5, 3);                     // R5: comparand (because reg-no is even)
		setGPR(1, 0x010000);              // R1: base register 
		setInstructions(
				_87, _24, _1F, _80        // BXLE R2,R5,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end (result high => not branched)
		checkCC(CC3);            // must be unchanged
		checkGPR(2, 4);          // must be incremented by the value in R5
	}


}
