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
public class Cpu370BcTest_Compare extends AbstractCpu370BcTest {
	
	public Cpu370BcTest_Compare(Class<? extends Cpu370Bc> cpuClass) {
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
	
	private final int IA_PGM_INTR_BASE = 0x00300000;
	
	
	/*
	** Tests:  0x59 -- C [RX] - Compare
	*/

	@Test
	public void x59_RX_Compare_01_X2zero_B2_DISP_ops_positive_equal() {
		setCC(CC3);                       // for later comparison
		setGPR(10, 12345678);             // R10 has op1 
		setMemF(0x010180, 12345678);      // op2 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_59, _A0, _21, _80        // C R10 <-> (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, 12345678);  // must stay unchanged
		checkMemF(0x010180, 12345678); // must stay unchanged
		checkCC(CC0);            // operands are equal
	}

	@Test
	public void x59_RX_Compare_02_X2_B2_DISP_ops_negative_equal() {
		setCC(CC3);                       // for later comparison
		setGPR(10, -12345678);            // R10 has op1 
		setMemF(0x010180, -12345678);     // op2 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_59, _A1, _20, _80        // C R10 <-> (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, -12345678); // must stay unchanged
		checkMemF(0x010180, -12345678); // must stay unchanged
		checkCC(CC0);            // operands are equal
	}

	@Test
	public void x59_RX_Compare_03a_X2_B2zero_DISP_ops_positive_CC1() {
		setCC(CC3);                       // for later comparison
		setGPR(10, 12345677);             // R10 has op1 
		setMemF(0x010180, 12345678);      // op2 at address: 0x010180
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_59, _A1, _00, _80        // C R10 <-> (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, 12345677);  // must stay unchanged
		checkMemF(0x010180, 12345678); // must stay unchanged
		checkCC(CC1);            // first operand is low
	}

	@Test
	public void x59_RX_Compare_03b_X2_B2zero_DISP_ops_positive_CC2() {
		setCC(CC3);                       // for later comparison
		setGPR(10, 12345679);             // R10 has op1 
		setMemF(0x010180, 12345678);      // op2 at address: 0x010180
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_59, _A1, _00, _80        // C R10 <-> (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, 12345679);  // must stay unchanged
		checkMemF(0x010180, 12345678); // must stay unchanged
		checkCC(CC2);            // first operand is high
	}

	@Test
	public void x59_RX_Compare_04a_X2_B2_DISP_ops_negative_CC2() {
		setCC(CC3);                       // for later comparison
		setGPR(10, -12345677);            // R10 has op1 
		setMemF(0x010180, -12345678);     // op2 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_59, _A1, _20, _80        // C R10 <-> (base=R2,index=R1,offset=0x090)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, -12345677); // must stay unchanged
		checkMemF(0x010180, -12345678); // must stay unchanged
		checkCC(CC2);            // first operand is high
	}

	@Test
	public void x59_RX_Compare_04b_X2_B2_DISP_ops_negative_CC1() {
		setCC(CC3);                       // for later comparison
		setGPR(10, -12345679);            // R10 has op1 
		setMemF(0x010180, -12345678);     // op2 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_59, _A1, _20, _80        // C R10 <-> (base=R2,index=R1,offset=0x090)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, -12345679); // must stay unchanged
		checkMemF(0x010180, -12345678); // must stay unchanged
		checkCC(CC1);            // first operand is low
	}
	
	/*
	** Tests:  0x19 -- CR [RR] - Compare Register
	*/

	@Test
	public void x19_RR_CompareRegister_01_ops_zero_equal() {
		setCC(CC3);                       // for later comparison
		setGPR(4, 0);                     // op1 in R4 = 0 
		setGPR(1, 0);                     // op2 in R1 = 0
		setInstructions(
				_19, _41                  // CR R4 <-> R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(1, 0);          // must stay unchanged
		checkGPR(4, 0);          // must stay unchanged
		checkCC(CC0);            // operands are equal
	}

	@Test
	public void x19_RR_CompareRegister_02_ops_positive_equal() {
		setCC(CC3);                       // for later comparison
		setGPR(4, 42);                    // op1 in R4 = 0 
		setGPR(1, 42);                    // op2 in R1 = 0
		setInstructions(
				_19, _41                  // CR R4 <-> R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(1, 42);         // must stay unchanged
		checkGPR(4, 42);         // must stay unchanged
		checkCC(CC0);            // operands are equal
	}

	@Test
	public void x19_RR_CompareRegister_01_ops_positive_CC1() {
		setCC(CC3);                       // for later comparison
		setGPR(4, 41);                    // op1 in R4 = 0 
		setGPR(1, 42);                    // op2 in R1 = 0
		setInstructions(
				_19, _41                  // CR R4 <-> R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(1, 42);         // must stay unchanged
		checkGPR(4, 41);         // must stay unchanged
		checkCC(CC1);            // first operand is low
	}

	@Test
	public void x19_RR_CompareRegister_01_ops_negative_CC2() {
		setCC(CC3);                       // for later comparison
		setGPR(4, -41);                   // op1 in R4 = 0 
		setGPR(1, -42);                   // op2 in R1 = 0
		setInstructions(
				_19, _41                  // CR R4 <-> R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(1, -42);        // must stay unchanged
		checkGPR(4, -41);        // must stay unchanged
		checkCC(CC2);            // first operand is high
	}
	
	
	/*
	** Tests:  0xBA -- CS [RS] - Compare and Swap
	*/
	
	@Test
	public void xBA_RS_CompareAndSwap_01_ops_equal_CC0() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 334455);                // op1 in R2
		setMemF(0x010F80, 334455);        // op2 at address: 0x010F80
		setGPR(5, 0x87654321);            // op3 in R5 
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BA, _25, _1F, _80        // CS R2,R5,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // op1 and op2 are equal, second operand replaced by third operand
		checkGPR(2, 334455);     // op1 must be unchanged
		checkMemF(0x010F80, 0x87654321); // op2 is replaced by op3
		checkGPR(5, 0x87654321); // op3 must be unchanged 
	}
	
	@Test
	public void xBA_RS_CompareAndSwap_02_ops_notEqual_CC1() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 334455);                // op1 in R2
		setMemF(0x010F80, 334433);        // op2 at address: 0x010F80
		setGPR(5, 0x87654321);            // op3 in R5 
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BA, _25, _1F, _80        // CS R2,R5,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // op1 and op2 are unequal, first operand replaced by second operand
		checkGPR(2, 334433);     // op1 is replaced by op2
		checkMemF(0x010F80, 334433); // op2 must be unchanged 
		checkGPR(5, 0x87654321); // op3 must be unchanged 
	}
	
	@Test
	public void xBA_RS_CompareAndSwap_03_specificationException() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 334455);                // op1 in R2
		setMemF(0x010F81, 334433);        // op2 at address: 0x010F81 (not at word boundary => specification exception)
		setGPR(5, 0x87654321);            // op3 in R5 
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BA, _25, _1F, _81        // CS R2,R5,(R1,0xF81) 
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(2, 334455);         // op1 must be unchanged 
		checkMemF(0x010F81, 334433); // op2 must be unchanged 
		checkGPR(5, 0x87654321);     // op3 must be unchanged
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 2, 3, CodeBase+4); // ILC=2, CC=3
	}
	
	
	/*
	** Tests:  0xBB -- CDS [RS] - Compare Double and Swap
	*/
	
	@Test
	public void xBB_RS_CompareDoubleAndSwap_01_ops_equal_CC0() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 334455);                // op1 in R2-R3
		setGPR(3, 667788);
		setMemF(0x010F80, 334455, 667788);// op2 at address: 0x010F80
		setGPR(6, 0x87654321);            // op3 in R6-R7
		setGPR(7, 0x12345678);
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BB, _26, _1F, _80        // CDS R2,R6,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // op1 and op2 are equal, second operand replaced by third operand
		checkGPR(2, 334455);     // op1 must be unchanged
		checkGPR(3, 667788);
		checkMemF(0x010F80, 0x87654321, 0x12345678); // op2 is replaced by op3
		checkGPR(6, 0x87654321); // op3 must be unchanged
		checkGPR(7, 0x12345678);
	}
	
	@Test
	public void xBB_RS_CompareDoubleAndSwap_02_ops_notEqual_CC1() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 334433);                // op1 in R2-R3
		setGPR(3, 667788);
		setMemF(0x010F80, 334455, 667766);// op2 at address: 0x010F80
		setGPR(6, 0x87654321);            // op3 in R6-R7
		setGPR(7, 0x12345678);
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BB, _26, _1F, _80        // CDS R2,R6,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // op1 and op2 are unequal, first operand replaced by second operand
		checkGPR(2, 334455);     // op1 is replaced by op2
		checkGPR(3, 667766);
		checkMemF(0x010F80, 334455, 667766); // op2 must be unchanged
		checkGPR(6, 0x87654321); // op3 must be unchanged
		checkGPR(7, 0x12345678);
	}
	
	@Test
	public void xBB_RS_CompareDoubleAndSwap_03_ops_specification_op1odd() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(3, 334433);                // op1 in R3-R4 => specification exception
		setGPR(4, 667788);
		setMemF(0x010F80, 334455, 667766);// op2 at address: 0x010F80
		setGPR(6, 0x87654321);            // op3 in R6-R7
		setGPR(7, 0x12345678);
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BB, _36, _1F, _80        // CDS R3,R6,(R1,0xF80) 
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(3, 334433);     // op1 must be unchanged
		checkGPR(4, 667788);
		checkMemF(0x010F80, 334455, 667766); // op2 must be unchanged
		checkGPR(6, 0x87654321); // op3 must be unchanged
		checkGPR(7, 0x12345678);
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 2, 3, CodeBase+4); // ILC=2, CC=3
	}
	
	@Test
	public void xBB_RS_CompareDoubleAndSwap_04_ops_specification_op3odd() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 334433);                // op1 in R2-R3
		setGPR(3, 667788);
		setMemF(0x010F80, 334455, 667766);// op2 at address: 0x010F80
		setGPR(7, 0x87654321);            // op3 in R7-R8 => specification exception
		setGPR(8, 0x12345678);
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BB, _27, _1F, _80        // CDS R2,R7,(R1,0xF80) 
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(2, 334433);     // op1 must be unchanged
		checkGPR(3, 667788);
		checkMemF(0x010F80, 334455, 667766); // op2 must be unchanged
		checkGPR(7, 0x87654321); // op3 must be unchanged
		checkGPR(8, 0x12345678);
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 2, 3, CodeBase+4); // ILC=2, CC=3
	}
	
	@Test
	public void xBB_RS_CompareDoubleAndSwap_04_ops_specification_op2notDWboundary() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);                       // for later comparision
		setGPR(2, 334433);                // op1 in R2-R3
		setGPR(3, 667788);
		setMemF(0x010F84, 334455, 667766);// op2 at address: 0x010F84 => specification exception
		setGPR(6, 0x87654321);            // op3 in R6-R7
		setGPR(7, 0x12345678);
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BB, _26, _1F, _84        // CDS R2,R6,(R1,0xF84) 
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkGPR(2, 334433);     // op1 must be unchanged
		checkGPR(3, 667788);
		checkMemF(0x010F84, 334455, 667766); // op2 must be unchanged
		checkGPR(6, 0x87654321); // op3 must be unchanged
		checkGPR(7, 0x12345678);
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 2, 3, CodeBase+4); // ILC=2, CC=3
	}
	
	/*
	** Tests:  0x49 -- CH [RX] - Compare Halfword
	*/

	@Test
	public void x49_RX_CompareHalfword_01_X2zero_B2_DISP_ops_positive_equal() {
		setCC(CC3);                       // for later comparison
		setGPR(10, 12345);                // R10 has op1 
		setMemH(0x010180, (short)12345);  // op2 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_49, _A0, _21, _80        // CH R10 <-> (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, 12345);     // must stay unchanged
		checkMemH(0x010180, (short)12345); // must stay unchanged
		checkCC(CC0);            // operands are equal
	}

	@Test
	public void x49_RX_CompareHalfword_02_X2_B2_DISP_ops_negative_equal() {
		setCC(CC3);                       // for later comparison
		setGPR(10, -12345);               // R10 has op1 
		setMemH(0x010180, (short)-12345); // op2 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_49, _A1, _20, _80        // CH R10 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, -12345);    // must stay unchanged
		checkMemH(0x010180, (short)-12345); // must stay unchanged
		checkCC(CC0);            // operands are equal
	}

	@Test
	public void x49_RX_CompareHalfword_03_X2_B2zero_DISP_ops_positive_CC1() {
		setCC(CC3);                       // for later comparison
		setGPR(10, 12344);                // R10 has op1 
		setMemH(0x010180, (short)12345);  // op2 at address: 0x010180
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_49, _A1, _00, _80        // CH R10 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, 12344);     // must stay unchanged
		checkMemH(0x010180, (short)12345); // must stay unchanged
		checkCC(CC1);            // first operand is low
	}

	@Test
	public void x49_RX_CompareHalfword_04_X2_B2_DISP_ops_negative_CC2() {
		setCC(CC3);                       // for later comparison
		setGPR(10, -12344);               // R10 has op1 
		setMemH(0x010180, (short)-12345); // op2 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_49, _A1, _20, _80        // CH R10 <- (base=R2,index=R1,offset=0x090)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, -12344);    // must stay unchanged
		checkMemH(0x010180, (short)-12345); // must stay unchanged
		checkCC(CC2);            // first operand is high
	}
	
	
	/*
	** Tests:  0x55 -- CL [RX] - Compare Logical
	*/

	@Test
	public void x55_RX_CompareLogical_01a_X2zero_B2_DISP_ops_lowerhalf_equal() {
		setCC(CC3);                       // for later comparison
		setGPR(10, 0x12345678);           // R10 has op1 
		setMemF(0x010180, 0x12345678);    // op2 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_55, _A0, _21, _80        // CL R10 <-> (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, 0x12345678);// must stay unchanged
		checkMemF(0x010180, 0x12345678); // must stay unchanged
		checkCC(CC0);            // operands are equal
	}

	@Test
	public void x55_RX_CompareLogical_01b_X2_B2_DISP_ops_upperhalf_equal() {
		setCC(CC3);                       // for later comparison
		setGPR(10, 0xF2345678);           // R10 has op1 
		setMemF(0x010180, 0xF2345678);    // op2 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_55, _A1, _20, _80        // CL R10 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, 0xF2345678);// must stay unchanged
		checkMemF(0x010180, 0xF2345678); // must stay unchanged
		checkCC(CC0);            // operands are equal
	}

	@Test
	public void x55_RX_CompareLogical_01c_X2zero_B2_DISP_ops_zero_equal() {
		setCC(CC3);                       // for later comparison
		setGPR(10, 0);                    // R10 has op1 
		setMemF(0x010180, 0);             // op2 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_55, _A0, _21, _80        // CL R10 <-> (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, 0);         // must stay unchanged
		checkMemF(0x010180, 0);  // must stay unchanged
		checkCC(CC0);            // operands are equal
	}

	@Test
	public void x55_RX_CompareLogical_02a_X2zero_B2_DISP_ops_lowerhalf_op1low() {
		setCC(CC3);                       // for later comparison
		setGPR(10, 0x12345677);           // R10 has op1 
		setMemF(0x010180, 0x12345678);    // op2 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_55, _A0, _21, _80        // CL R10 <-> (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, 0x12345677);// must stay unchanged
		checkMemF(0x010180, 0x12345678); // must stay unchanged
		checkCC(CC1);            // first operand is low
	}

	@Test
	public void x55_RX_CompareLogical_02b_X2_B2_DISP_ops_upperhalf_op1low() {
		setCC(CC3);                       // for later comparison
		setGPR(10, 0xF2345677);           // R10 has op1 
		setMemF(0x010180, 0xF2345678);    // op2 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_55, _A1, _20, _80        // CL R10 <-> (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, 0xF2345677);// must stay unchanged
		checkMemF(0x010180, 0xF2345678); // must stay unchanged
		checkCC(CC1);            // first operand is low
	}

	@Test
	public void x55_RX_CompareLogical_02c_X2_B2_DISP_ops_lowerupper_op1low() {
		setCC(CC3);                       // for later comparison
		setGPR(10, 0x12345678);           // R10 has op1 
		setMemF(0x010180, 0xF2345678);    // op2 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_55, _A1, _20, _80        // CL R10 <-> (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, 0x12345678);// must stay unchanged
		checkMemF(0x010180, 0xF2345678); // must stay unchanged
		checkCC(CC1);            // first operand is low
	}

	@Test
	public void x55_RX_CompareLogical_03a_X2_B2zero_DISP_ops_lowerhalf_op1high() {
		setCC(CC3);                       // for later comparison
		setGPR(10, 0x12345679);           // R10 has op1 
		setMemF(0x010180, 0x12345678);    // op2 at address: 0x010180
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_55, _A1, _00, _80        // CL R10 <-> (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, 0x12345679);// must stay unchanged
		checkMemF(0x010180, 0x12345678); // must stay unchanged
		checkCC(CC2);            // first operand is high
	}

	@Test
	public void x55_RX_CompareLogical_03b_X2_B2zero_DISP_ops_upperhalf_op1high() {
		setCC(CC3);                       // for later comparison
		setGPR(10, 0xF2345679);           // R10 has op1 
		setMemF(0x010180, 0xF2345678);    // op2 at address: 0x010180
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_55, _A1, _00, _80        // CL R10 <-> (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, 0xF2345679);// must stay unchanged
		checkMemF(0x010180, 0xF2345678); // must stay unchanged
		checkCC(CC2);            // first operand is high
	}

	@Test
	public void x55_RX_CompareLogical_03c_X2_B2zero_DISP_ops_upperlower_op1high() {
		setCC(CC3);                       // for later comparison
		setGPR(10, 0xF2345679);           // R10 has op1 
		setMemF(0x010180, 0x12345678);    // op2 at address: 0x010180
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_55, _A1, _00, _80        // CL R10 <-> (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkGPR(10, 0xF2345679);// must stay unchanged
		checkMemF(0x010180, 0x12345678); // must stay unchanged
		checkCC(CC2);            // first operand is high
	}
	
	/*
	** Tests:  0x15 -- CR [RR] - Compare Logical Register
	*/

	@Test
	public void x15_RR_CompareLogicalRegister_01a_ops_zero_equal() {
		setCC(CC3);                       // for later comparison
		setGPR(4, 0);                     // op1 in R4 = 0 
		setGPR(1, 0);                     // op2 in R1 = 0
		setInstructions(
				_15, _41                  // CLR R4 <-> R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(1, 0);          // must stay unchanged
		checkGPR(4, 0);          // must stay unchanged
		checkCC(CC0);            // operands are equal
	}

	@Test
	public void x15_RR_CompareLogicalRegister_01b_ops_lowerhalf_equal() {
		setCC(CC3);                       // for later comparison
		setGPR(4, 0x12345678);            // op1 in R4 = 0 
		setGPR(1, 0x12345678);            // op2 in R1 = 0
		setInstructions(
				_15, _41                  // CLR R4 <-> R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(1, 0x12345678); // must stay unchanged
		checkGPR(4, 0x12345678); // must stay unchanged
		checkCC(CC0);            // operands are equal
	}

	@Test
	public void x15_RR_CompareLogicalRegister_01c_ops_upperhalf_equal() {
		setCC(CC3);                       // for later comparison
		setGPR(4, 0xF2345678);            // op1 in R4 = 0 
		setGPR(1, 0xF2345678);            // op2 in R1 = 0
		setInstructions(
				_15, _41                  // CLR R4 <-> R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(1, 0xF2345678); // must stay unchanged
		checkGPR(4, 0xF2345678); // must stay unchanged
		checkCC(CC0);            // operands are equal
	}

	@Test
	public void x15_RR_CompareLogicalRegister_02a_ops_lowerhalf_op1low() {
		setCC(CC3);                       // for later comparison
		setGPR(4, 0x12345677);            // op1 in R4 = 0 
		setGPR(1, 0x12345678);            // op2 in R1 = 0
		setInstructions(
				_15, _41                  // CLR R4 <-> R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(1, 0x12345678); // must stay unchanged
		checkGPR(4, 0x12345677); // must stay unchanged
		checkCC(CC1);            // first operand is low
	}

	@Test
	public void x15_RR_CompareLogicalRegister_02b_ops_upperhalf_op1low() {
		setCC(CC3);                       // for later comparison
		setGPR(4, 0xF2345677);            // op1 in R4 = 0 
		setGPR(1, 0xF2345678);            // op2 in R1 = 0
		setInstructions(
				_15, _41                  // CLR R4 <-> R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(1, 0xF2345678); // must stay unchanged
		checkGPR(4, 0xF2345677); // must stay unchanged
		checkCC(CC1);            // first operand is low
	}

	@Test
	public void x15_RR_CompareLogicalRegister_02c_ops_lowerupper_op1low() {
		setCC(CC3);                       // for later comparison
		setGPR(4, 0x12345678);            // op1 in R4 = 0 
		setGPR(1, 0xF2345678);            // op2 in R1 = 0
		setInstructions(
				_15, _41                  // CLR R4 <-> R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(1, 0xF2345678); // must stay unchanged
		checkGPR(4, 0x12345678); // must stay unchanged
		checkCC(CC1);            // first operand is low
	}

	@Test
	public void x15_RR_CompareLogicalRegister_03a_ops_lowerhalf_op1high() {
		setCC(CC3);                       // for later comparison
		setGPR(4, 0x12345679);            // op1 in R4 = 0 
		setGPR(1, 0x12345678);            // op2 in R1 = 0
		setInstructions(
				_15, _41                  // CLR R4 <-> R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(1, 0x12345678); // must stay unchanged
		checkGPR(4, 0x12345679); // must stay unchanged
		checkCC(CC2);            // first operand is low
	}

	@Test
	public void x15_RR_CompareLogicalRegister_03b_ops_upperhalf_op1high() {
		setCC(CC3);                       // for later comparison
		setGPR(4, 0xF2345679);            // op1 in R4 = 0 
		setGPR(1, 0xF2345678);            // op2 in R1 = 0
		setInstructions(
				_15, _41                  // CLR R4 <-> R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(1, 0xF2345678); // must stay unchanged
		checkGPR(4, 0xF2345679); // must stay unchanged
		checkCC(CC2);            // first operand is low
	}

	@Test
	public void x15_RR_CompareLogicalRegister_03c_ops_lowerupper_op1high() {
		setCC(CC3);                       // for later comparison
		setGPR(4, 0xF2345678);            // op1 in R4 = 0 
		setGPR(1, 0x12345678);            // op2 in R1 = 0
		setInstructions(
				_15, _41                  // CLR R4 <-> R1
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		checkGPR(1, 0x12345678); // must stay unchanged
		checkGPR(4, 0xF2345678); // must stay unchanged
		checkCC(CC2);            // first operand is low
	}
	
	
	/*
	** Tests:  0x95 -- CLI [SI] - Compare Logical Immediate
	*/

	@Test
	public void x95_SI_CompareLogicalImmediate_01a_B2_DISP_zero_equal() {
		setCC(CC3);                       // for later comparison
		setMemB(0x010F80, _00);           // op1 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_95, _00, _2F, _80        // CLI x00 <-> (base=R2,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _00); // must be unchanged 
		checkCC(CC0);             // result is non-zero
	}

	@Test
	public void x95_SI_CompareLogicalImmediate_01b_B2_DISP_lowerhalf_equal() {
		setCC(CC3);                       // for later comparison
		setMemB(0x010F80, _22);           // op1 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_95, _22, _2F, _80        // CLI x22 <-> (base=R2,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _22); // must be unchanged 
		checkCC(CC0);             // result is non-zero
	}

	@Test
	public void x95_SI_CompareLogicalImmediate_01c_B2_DISP_upperhalf_equal() {
		setCC(CC3);                       // for later comparison
		setMemB(0x010F80, _F9);           // op1 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_95, _F9, _2F, _80        // CLI xF9 <-> (base=R2,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _F9); // must be unchanged 
		checkCC(CC0);             // result is non-zero
	}

	@Test
	public void x95_SI_CompareLogicalImmediate_02b_B2_DISP_lowerhalf_op1low() {
		setCC(CC3);                       // for later comparison
		setMemB(0x010F80, _21);           // op1 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_95, _22, _2F, _80        // CLI x22 <-> (base=R2,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _21); // must be unchanged 
		checkCC(CC1);             // first operand is low
	}

	@Test
	public void x95_SI_CompareLogicalImmediate_02b_B2_DISP_upperhalf_op1low() {
		setCC(CC3);                       // for later comparison
		setMemB(0x010F80, _F8);           // op1 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_95, _F9, _2F, _80        // CLI xF9 <-> (base=R2,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _F8); // must be unchanged 
		checkCC(CC1);             // first operand is low
	}

	@Test
	public void x95_SI_CompareLogicalImmediate_02c_B2_DISP_lowerupper_op1low() {
		setCC(CC3);                       // for later comparison
		setMemB(0x010F80, _79);           // op1 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_95, _F9, _2F, _80        // CLI xF9 <-> (base=R2,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _79); // must be unchanged 
		checkCC(CC1);             // first operand is low
	}

	@Test
	public void x95_SI_CompareLogicalImmediate_03b_B2_DISP_lowerhalf_op1high() {
		setCC(CC3);                       // for later comparison
		setMemB(0x010F80, _23);           // op1 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_95, _22, _2F, _80        // CLI x22 <-> (base=R2,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _23); // must be unchanged 
		checkCC(CC2);             // first operand is high
	}

	@Test
	public void x95_SI_CompareLogicalImmediate_03b_B2_DISP_upperhalf_op1high() {
		setCC(CC3);                       // for later comparison
		setMemB(0x010F80, _FA);           // op1 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_95, _F9, _2F, _80        // CLI xF9 <-> (base=R2,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _FA); // must be unchanged 
		checkCC(CC2);             // first operand is high
	}

	@Test
	public void x95_SI_CompareLogicalImmediate_03c_B2_DISP_upperlower_op1high() {
		setCC(CC3);                       // for later comparison
		setMemB(0x010F80, _F9);           // op1 at address: 0x010180
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_95, _79, _2F, _80        // CLI xF9 <-> (base=R2,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);               // instruction length code
		checkIA(CodeBase+4);      // instruction address at end
		checkMemB(0x010F80, _F9); // must be unchanged 
		checkCC(CC2);             // first operand is high
	}
	
	
	/*
	** Tests:  0xD5 -- CLC [SS] - Compare Logical Characters
	*/

	@Test
	public void xD5_SS_CompareLogicalCharacters_01_equal() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,                 // op1 at address: 0x010180, 32 characters
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _F0
				);
		setGPR(2, 0x010F80);              // set base register for addressing of op1
		setMemB(0x020FC0,                 // op2 at address: 0x020FC0, 32 characters
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _F0
				);
		setGPR(3, 0x020000);              // set base register for addressing of op2
		setInstructions(
				_D5, _1F, _20, _00, _3F, _C0 // CLC (base=R2,offset=0x000) <-> (base=R3,offset=0xFC0), length=32
		);
		execute(1); // do one instruction
		checkIL(3);               // instruction length code
		checkIA(CodeBase+6);      // instruction address at end
		checkMemB(0x010F80,       // op1 must stay unchanged
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _F0
				);
		checkMemB(0x020FC0,       // op2 must stay unchanged
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _F0
				);
		checkCC(CC0);             // operands are equal
	}

	@Test
	public void xD5_SS_CompareLogicalCharacters_02a_lowerhalf_op1low() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,                 // op1 at address: 0x010180, 32 characters
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _11
				);
		setGPR(2, 0x010F80);              // set base register for addressing of op1
		setMemB(0x020FC0,                 // op2 at address: 0x020FC0, 32 characters
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _12
				);
		setGPR(3, 0x020000);              // set base register for addressing of op2
		setInstructions(
				_D5, _1F, _20, _00, _3F, _C0 // CLC (base=R2,offset=0x000) <-> (base=R3,offset=0xFC0), length=32
		);
		execute(1); // do one instruction
		checkIL(3);               // instruction length code
		checkIA(CodeBase+6);      // instruction address at end
		checkMemB(0x010F80,       // op1 must stay unchanged
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _11
				);
		checkMemB(0x020FC0,       // op2 must stay unchanged
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _12
				);
		checkCC(CC1);             // operand 1 is low
	}

	@Test
	public void xD5_SS_CompareLogicalCharacters_02b_upperhalf_op1low() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,                 // op1 at address: 0x010180, 32 characters
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _F1
				);
		setGPR(2, 0x010F80);              // set base register for addressing of op1
		setMemB(0x020FC0,                 // op2 at address: 0x020FC0, 32 characters
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _F2
				);
		setGPR(3, 0x020000);              // set base register for addressing of op2
		setInstructions(
				_D5, _1F, _20, _00, _3F, _C0 // CLC (base=R2,offset=0x000) <-> (base=R3,offset=0xFC0), length=32
		);
		execute(1); // do one instruction
		checkIL(3);               // instruction length code
		checkIA(CodeBase+6);      // instruction address at end
		checkMemB(0x010F80,       // op1 must stay unchanged
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _F1
				);
		checkMemB(0x020FC0,       // op2 must stay unchanged
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _F2
				);
		checkCC(CC1);             // operand 1 is low
	}

	@Test
	public void xD5_SS_CompareLogicalCharacters_02c_lowerupper_op1low() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,                 // op1 at address: 0x010180, 32 characters
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _11
				);
		setGPR(2, 0x010F80);              // set base register for addressing of op1
		setMemB(0x020FC0,                 // op2 at address: 0x020FC0, 32 characters
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _F2
				);
		setGPR(3, 0x020000);              // set base register for addressing of op2
		setInstructions(
				_D5, _1F, _20, _00, _3F, _C0 // CLC (base=R2,offset=0x000) <-> (base=R3,offset=0xFC0), length=32
		);
		execute(1); // do one instruction
		checkIL(3);               // instruction length code
		checkIA(CodeBase+6);      // instruction address at end
		checkMemB(0x010F80,       // op1 must stay unchanged
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _11
				);
		checkMemB(0x020FC0,       // op2 must stay unchanged
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _F2
				);
		checkCC(CC1);             // operand 1 is low
	}

	@Test
	public void xD5_SS_CompareLogicalCharacters_03a_lowerhalf_op1high() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,                 // op1 at address: 0x010180, 32 characters
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _13
				);
		setGPR(2, 0x010F80);              // set base register for addressing of op1
		setMemB(0x020FC0,                 // op2 at address: 0x020FC0, 32 characters
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _12
				);
		setGPR(3, 0x020000);              // set base register for addressing of op2
		setInstructions(
				_D5, _1F, _20, _00, _3F, _C0 // CLC (base=R2,offset=0x000) <-> (base=R3,offset=0xFC0), length=32
		);
		execute(1); // do one instruction
		checkIL(3);               // instruction length code
		checkIA(CodeBase+6);      // instruction address at end
		checkMemB(0x010F80,       // op1 must stay unchanged
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _13
				);
		checkMemB(0x020FC0,       // op2 must stay unchanged
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _12
				);
		checkCC(CC2);             // operand 1 is high
	}

	@Test
	public void xD5_SS_CompareLogicalCharacters_03b_upperhalf_op1high() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,                 // op1 at address: 0x010180, 32 characters
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _F3
				);
		setGPR(2, 0x010F80);              // set base register for addressing of op1
		setMemB(0x020FC0,                 // op2 at address: 0x020FC0, 32 characters
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _F2
				);
		setGPR(3, 0x020000);              // set base register for addressing of op2
		setInstructions(
				_D5, _1F, _20, _00, _3F, _C0 // CLC (base=R2,offset=0x000) <-> (base=R3,offset=0xFC0), length=32
		);
		execute(1); // do one instruction
		checkIL(3);               // instruction length code
		checkIA(CodeBase+6);      // instruction address at end
		checkMemB(0x010F80,       // op1 must stay unchanged
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _F3
				);
		checkMemB(0x020FC0,       // op2 must stay unchanged
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _F2
				);
		checkCC(CC2);             // operand 1 is high
	}

	@Test
	public void xD5_SS_CompareLogicalCharacters_03c_lowerupper_op1high() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010F80,                 // op1 at address: 0x010180, 32 characters
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _F3
				);
		setGPR(2, 0x010F80);              // set base register for addressing of op1
		setMemB(0x020FC0,                 // op2 at address: 0x020FC0, 32 characters
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _12
				);
		setGPR(3, 0x020000);              // set base register for addressing of op2
		setInstructions(
				_D5, _1F, _20, _00, _3F, _C0 // CLC (base=R2,offset=0x000) <-> (base=R3,offset=0xFC0), length=32
		);
		execute(1); // do one instruction
		checkIL(3);               // instruction length code
		checkIA(CodeBase+6);      // instruction address at end
		checkMemB(0x010F80,       // op1 must stay unchanged
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _F3
				);
		checkMemB(0x020FC0,       // op2 must stay unchanged
				_12, _34, _56, _78, _9A, _BC, _DE, _F0,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_00, _00, _00, _00, _00, _00, _00, _00,
				_12, _34, _56, _78, _9A, _BC, _DE, _12
				);
		checkCC(CC2);             // operand 1 is high
	}
	
	
	/*
	** Tests:  0xBD -- CLM [RS] - Compare Logical Characters under Mask
	*/
	
	@Test
	public void xBD_RS_CompareLogicalCharactersUnderMask_01_mask_1111_equal() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x117788FF);            // op1 in R2
		setMemF(0x010F80, 0x117788FF);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BD, _2F, _1F, _80        // CLM R2,1111b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // characters under mask in op1 and op2 are equal
		checkGPR(2, 0x117788FF);     // op1 must be unchanged
		checkMemF(0x010F80, 0x117788FF); // op2 must be unchanged
	}
	
	@Test
	public void xBD_RS_CompareLogicalCharactersUnderMask_02a_mask_1111_op1low_lowerhalf() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x11778811);            // op1 in R2
		setMemF(0x010F80, 0x11778812);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BD, _2F, _1F, _80        // CLM R2,1111b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // op1 low in characters under mask
		checkGPR(2, 0x11778811);     // op1 must be unchanged
		checkMemF(0x010F80, 0x11778812); // op2 must be unchanged
	}
	
	@Test
	public void xBD_RS_CompareLogicalCharactersUnderMask_02b_mask_1111_op1low_upperhalf() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x117788FE);            // op1 in R2
		setMemF(0x010F80, 0x117788FF);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BD, _2F, _1F, _80        // CLM R2,1111b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // op1 low in characters under mask
		checkGPR(2, 0x117788FE);     // op1 must be unchanged
		checkMemF(0x010F80, 0x117788FF); // op2 must be unchanged
	}
	
	@Test
	public void xBD_RS_CompareLogicalCharactersUnderMask_02c_mask_1111_op1low_lowerupper() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x11778811);            // op1 in R2
		setMemF(0x010F80, 0x117788FF);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BD, _2F, _1F, _80        // CLM R2,1111b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // op1 low in characters under mask
		checkGPR(2, 0x11778811);     // op1 must be unchanged
		checkMemF(0x010F80, 0x117788FF); // op2 must be unchanged
	}
	
	@Test
	public void xBD_RS_CompareLogicalCharactersUnderMask_03a_mask_1111_op1high_lowerhalf() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x11778812);            // op1 in R2
		setMemF(0x010F80, 0x11778811);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BD, _2F, _1F, _80        // CLM R2,1111b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // op1 low in characters under mask
		checkGPR(2, 0x11778812);     // op1 must be unchanged
		checkMemF(0x010F80, 0x11778811); // op2 must be unchanged
	}
	
	@Test
	public void xBD_RS_CompareLogicalCharactersUnderMask_03b_mask_1111_op1high_upperhalf() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x117788FF);            // op1 in R2
		setMemF(0x010F80, 0x117788FE);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BD, _2F, _1F, _80        // CLM R2,1111b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // op1 low in characters under mask
		checkGPR(2, 0x117788FF);     // op1 must be unchanged
		checkMemF(0x010F80, 0x117788FE); // op2 must be unchanged
	}
	
	@Test
	public void xBD_RS_CompareLogicalCharactersUnderMask_03c_mask_1111_op1high_upperlower() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x117788FF);            // op1 in R2
		setMemF(0x010F80, 0x11778811);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BD, _2F, _1F, _80        // CLM R2,1111b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC2);            // op1 low in characters under mask
		checkGPR(2, 0x117788FF);     // op1 must be unchanged
		checkMemF(0x010F80, 0x11778811); // op2 must be unchanged
	}
	
	@Test
	public void xBD_RS_CompareLogicalCharactersUnderMask_04a_mask_1000_equal() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x117788FF);            // op1 in R2
		setMemF(0x010F80, 0x11000000);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BD, _28, _1F, _80        // CLM R2,1000b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // op1 low in characters under mask
		checkGPR(2, 0x117788FF); // op1 must be unchanged
		checkMemF(0x010F80, 0x11000000); // op2 must be unchanged
	}
	
	@Test
	public void xBD_RS_CompareLogicalCharactersUnderMask_04b_mask_0100_equal() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x117788FF);            // op1 in R2
		setMemF(0x010F80, 0x77000000);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BD, _24, _1F, _80        // CLM R2,0100b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // op1 low in characters under mask
		checkGPR(2, 0x117788FF); // op1 must be unchanged
		checkMemF(0x010F80, 0x77000000); // op2 must be unchanged
	}
	
	@Test
	public void xBD_RS_CompareLogicalCharactersUnderMask_04c_mask_0010_equal() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x117788FF);            // op1 in R2
		setMemF(0x010F80, 0x88000000);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BD, _22, _1F, _80        // CLM R2,0010b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // op1 low in characters under mask
		checkGPR(2, 0x117788FF); // op1 must be unchanged
		checkMemF(0x010F80, 0x88000000); // op2 must be unchanged
	}
	
	@Test
	public void xBD_RS_CompareLogicalCharactersUnderMask_04d_mask_0001_equal() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x117788FF);            // op1 in R2
		setMemF(0x010F80, 0xFF000000);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BD, _21, _1F, _80        // CLM R2,0001b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // op1 low in characters under mask
		checkGPR(2, 0x117788FF); // op1 must be unchanged
		checkMemF(0x010F80, 0xFF000000); // op2 must be unchanged
	}
	
	@Test
	public void xBD_RS_CompareLogicalCharactersUnderMask_05a_mask_1010_equal() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x117788FF);            // op1 in R2
		setMemF(0x010F80, 0x11880000);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BD, _2A, _1F, _80        // CLM R2,1010b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // op1 low in characters under mask
		checkGPR(2, 0x117788FF); // op1 must be unchanged
		checkMemF(0x010F80, 0x11880000); // op2 must be unchanged
	}
	
	@Test
	public void xBD_RS_CompareLogicalCharactersUnderMask_05b_mask_0101_equal() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x117788FF);            // op1 in R2
		setMemF(0x010F80, 0x77FF0000);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BD, _25, _1F, _80        // CLM R2,0101b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // op1 low in characters under mask
		checkGPR(2, 0x117788FF); // op1 must be unchanged
		checkMemF(0x010F80, 0x77FF0000); // op2 must be unchanged
	}
	
	@Test
	public void xBD_RS_CompareLogicalCharactersUnderMask_05c_mask_0110_equal() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x117788FF);            // op1 in R2
		setMemF(0x010F80, 0x77880000);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BD, _26, _1F, _80        // CLM R2,0110b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // op1 low in characters under mask
		checkGPR(2, 0x117788FF); // op1 must be unchanged
		checkMemF(0x010F80, 0x77880000); // op2 must be unchanged
	}
	
	@Test
	public void xBD_RS_CompareLogicalCharactersUnderMask_05d_mask_1001_equal() {
		setCC(CC3);                       // for later comparision
		setGPR(2, 0x117788FF);            // op1 in R2
		setMemF(0x010F80, 0x11FF0000);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_BD, _29, _1F, _80        // CLM R2,1001b,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // op1 low in characters under mask
		checkGPR(2, 0x117788FF); // op1 must be unchanged
		checkMemF(0x010F80, 0x11FF0000); // op2 must be unchanged
	}
	
	private void xBD_RS_InnerChk_CLM(int gprVal, int memVal, byte mask, byte ccVal) {
		setCC(CC3);                   // for later comparision
		setGPR(2, gprVal);            // op1 in R2
		setMemF(0x010F80, memVal);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);          // R1: base register for 2. Operand 
		setInstructions(
				_BD, (byte)(0x20 | (mask & 0x0F)), _1F, _80        // CLM R2,mask,(R1,0xF80) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(ccVal);          // expected CC
		checkGPR(2, gprVal); // op1 must be unchanged
		checkMemF(0x010F80, memVal); // op2 must be unchanged
	}
	
	@Test
	public void xBD_RS_CLM_10a() {
		this.xBD_RS_InnerChk_CLM(0x66000000, 0x77000000, (byte)0x08, CC1);
	}
	
	@Test
	public void xBD_RS_CLM_10b() {
		this.xBD_RS_InnerChk_CLM(0x77000000, 0x88000000, (byte)0x08, CC1);
	}
	
	@Test
	public void xBD_RS_CLM_10c() {
		this.xBD_RS_InnerChk_CLM(0x88000000, 0x89000000, (byte)0x08, CC1);
	}
	
	@Test
	public void xBD_RS_CLM_11a() {
		this.xBD_RS_InnerChk_CLM(0x77000000, 0x77000000, (byte)0x08, CC0);
	}
	
	@Test
	public void xBD_RS_CLM_11b() {
		this.xBD_RS_InnerChk_CLM(0x88000000, 0x88000000, (byte)0x08, CC0);
	}
	
	@Test
	public void xBD_RS_CLM_12a() {
		this.xBD_RS_InnerChk_CLM(0x77000000, 0x66000000, (byte)0x08, CC2);
	}
	
	@Test
	public void xBD_RS_CLM_12b() {
		this.xBD_RS_InnerChk_CLM(0x88000000, 0x77000000, (byte)0x08, CC2);
	}
	
	@Test
	public void xBD_RS_CLM_12c() {
		this.xBD_RS_InnerChk_CLM(0x89000000, 0x88000000, (byte)0x08, CC2);
	}
	
	@Test
	public void xBD_RS_CLM_20a() {
		this.xBD_RS_InnerChk_CLM(0x00660000, 0x77000000, (byte)0x04, CC1);
	}
	
	@Test
	public void xBD_RS_CLM_20b() {
		this.xBD_RS_InnerChk_CLM(0x00770000, 0x88000000, (byte)0x04, CC1);
	}
	
	@Test
	public void xBD_RS_CLM_20c() {
		this.xBD_RS_InnerChk_CLM(0x00880000, 0x89000000, (byte)0x04, CC1);
	}
	
	@Test
	public void xBD_RS_CLM_21a() {
		this.xBD_RS_InnerChk_CLM(0x00770000, 0x77000000, (byte)0x04, CC0);
	}
	
	@Test
	public void xBD_RS_CLM_21b() {
		this.xBD_RS_InnerChk_CLM(0x00880000, 0x88000000, (byte)0x04, CC0);
	}
	
	@Test
	public void xBD_RS_CLM_22a() {
		this.xBD_RS_InnerChk_CLM(0x00770000, 0x660000, (byte)0x04, CC2);
	}
	
	@Test
	public void xBD_RS_CLM_22b() {
		this.xBD_RS_InnerChk_CLM(0x00880000, 0x77000000, (byte)0x04, CC2);
	}
	
	@Test
	public void xBD_RS_CLM_22c() {
		this.xBD_RS_InnerChk_CLM(0x0000890000, 0x88000000, (byte)0x04, CC2);
	}
	
	@Test
	public void xBD_RS_CLM_30a() {
		this.xBD_RS_InnerChk_CLM(0x00006600, 0x77000000, (byte)0x02, CC1);
	}
	
	@Test
	public void xBD_RS_CLM_30b() {
		this.xBD_RS_InnerChk_CLM(0x00007700, 0x88000000, (byte)0x02, CC1);
	}
	
	@Test
	public void xBD_RS_CLM_30c() {
		this.xBD_RS_InnerChk_CLM(0x00008800, 0x89000000, (byte)0x02, CC1);
	}
	
	@Test
	public void xBD_RS_CLM_31a() {
		this.xBD_RS_InnerChk_CLM(0x00007700, 0x77000000, (byte)0x02, CC0);
	}
	
	@Test
	public void xBD_RS_CLM_31b() {
		this.xBD_RS_InnerChk_CLM(0x00008800, 0x88000000, (byte)0x02, CC0);
	}
	
	@Test
	public void xBD_RS_CLM_32a() {
		this.xBD_RS_InnerChk_CLM(0x00007700, 0x66000000, (byte)0x02, CC2);
	}
	
	@Test
	public void xBD_RS_CLM_32ba() {
		this.xBD_RS_InnerChk_CLM(0x00008800, 0x77000000, (byte)0x02, CC2);
	}
	
	@Test
	public void xBD_RS_CLM_32c() {
		this.xBD_RS_InnerChk_CLM(0x00008900, 0x88000000, (byte)0x02, CC2);
	}
	
	@Test
	public void xBD_RS_CLM_40a() {
		this.xBD_RS_InnerChk_CLM(0x00000066, 0x77000000, (byte)0x01, CC1);
	}
	
	@Test
	public void xBD_RS_CLM_40b() {
		this.xBD_RS_InnerChk_CLM(0x00000077, 0x88000000, (byte)0x01, CC1);
	}
	
	@Test
	public void xBD_RS_CLM_40c() {
		this.xBD_RS_InnerChk_CLM(0x00000088, 0x89000000, (byte)0x01, CC1);
	}
	
	@Test
	public void xBD_RS_CLM_41a() {
		this.xBD_RS_InnerChk_CLM(0x00000077, 0x77000000, (byte)0x01, CC0);
	}
	
	@Test
	public void xBD_RS_CLM_41b() {
		this.xBD_RS_InnerChk_CLM(0x00000088, 0x88000000, (byte)0x01, CC0);
	}
	
	@Test
	public void xBD_RS_CLM_42a() {
		this.xBD_RS_InnerChk_CLM(0x00000077, 0x66000000, (byte)0x01, CC2);
	}
	
	@Test
	public void xBD_RS_CLM_42ba() {
		this.xBD_RS_InnerChk_CLM(0x00000088, 0x77000000, (byte)0x01, CC2);
	}
	
	@Test
	public void xBD_RS_CLM_42c() {
		this.xBD_RS_InnerChk_CLM(0x00000089, 0x88000000, (byte)0x01, CC2);
	}
	
	
	/*
	** Tests:  0x93 -- TS [RS] - Test and Set
	*/
	
	@Test
	public void x93_RS_TestAndSet_01_CC0() {
		setCC(CC3);                       // for later comparision
		setMemB(0x010F81, (byte)0x77);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_93, _00, _1F, _81        // TS (R1,0xF81) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // bit 0 of the addressed byte was 0
		checkMemF(0x010F80, 0x00FF0000); // value placed in op2
	}
	
	@Test
	public void x93_RS_TestAndSet_02_CC1() {
		setCC(CC3);                       // for later comparision
		setMemB(0x010F81, (byte)0x87);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_93, _00, _1F, _81        // TS (R1,0xF81) 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // bit 0 of the addressed byte was 1
		checkMemF(0x010F80, 0x00FF0000); // value placed in op2
	}
	
	
	/*
	** Tests:  0x91 -- TM [SI] - Test under Mask
	*/
	
	@Test
	public void x91_SI_TestUnderMask_01_CC0_mask_zero() {
		setCC(CC2);                       // for later comparision
		setMemB(0x010F81, (byte)0x77);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_91, _00, _1F, _81        // TM (R1,0xF81), mask:00000000b 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // all mask bits are 0
	}
	
	@Test
	public void x91_SI_TestUnderMask_02_CC0_mask_nonZero() {
		setCC(CC2);                       // for later comparision
		setMemB(0x010F81, (byte)0x77);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_91, _88, _1F, _81        // TM (R1,0xF81), mask:10001000b 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC0);            // all op1 bits under mask are 0
	}
	
	@Test
	public void x91_SI_TestUnderMask_03_CC1() {
		setCC(CC2);                       // for later comparision
		setMemB(0x010F81, (byte)0x77);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_91, _FF, _1F, _81        // TM (R1,0xF81), mask:00000000b 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC1);            // some op1 bits under mask are 1 (but not all)
	}
	
	@Test
	public void x91_SI_TestUnderMask_04_CC3() {
		setCC(CC2);                       // for later comparision
		setMemB(0x010F81, (byte)0x77);    // op2 at address: 0x010F80
		setGPR(1, 0x010000);              // R1: base register for 2. Operand 
		setInstructions(
				_91, _77, _1F, _81        // TM (R1,0xF81), mask:10001000b 
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase + 4);   // instruction address at end
		checkCC(CC3);            // all op1 bits under mask are 1
	}
	
	
	/*
	** Tests:  0x0F -- CLCL [RR] - Compare Logical Character Long
	** 
	** Test combinations for left (L) and right (R) zones:
	** -> position relative to 16m boundary:
	**    -> A :: both zones below
	**    -> B :: right zone wraps at 16m
	**    -> C :: L below, R above (effectively: R below L)
	**    -> D:: left zone wraps at 16m, right above 16m (effectively: R below L)
	** -> length of zones:
	**    -> LgtR :: len(L) > len(R)
	**    -> LeqR :: len(L) = len(R)
	**    -> LltR :: len(L) < len(R)
	*/
	
	//  ... wrap A
	
	private void innerTestClcl(int atLeft, byte[] left, int atRight, byte[] right, byte pad,
	                           int expectedCC, int expectedDiffAt) {
		// setup memory
		int leftLen = (left != null) ? left.length : 0;
		int rightLen = (right != null) ? right.length : 0;
		if (leftLen > 0) { setMemB(atLeft, left); }
		if (rightLen > 0 ) { setMemB(atRight, right); }
		
		// setup general registers
		setGPR(4, 0xAB000000 | (atLeft & 0x00FFFFFF));
		setGPR(5, 0xCD000000 | leftLen);
		setGPR(6, 0x54000000 | (atRight & 0x00FFFFFF));
		setGPR(7, (pad << 24) | rightLen);
		
		// do the MVCL
		setCC(CC3);             // for comparision (cannot occur)
		setInstructions(
				_0F, _46         // CLCL R4,R6
		);
		execute(1); // do one instructions
		checkIL(1);
		checkIA(CodeBase+2);     // instruction address at end
		
		// check expected/expectable results
		checkCC((byte)expectedCC);
		
		// int fullLen = Math.max(leftLen, rightLen);
		// if (expectedCC == 0) { expectedDiffAt = fullLen; }
		int leftDiff = (expectedCC == 0) ? leftLen : Math.min(leftLen, expectedDiffAt);
		int rightDiff = (expectedCC == 0) ? rightLen : Math.min(rightLen, expectedDiffAt);
		
		/*
		// old: bits 0..7 of rx/ry were left unchanged in non-match case (which was wrong according to PrincOps75)
		checkGPR(4, ((expectedCC != 0) ? 0xAB000000 : 0) | ((atLeft + leftDiff) & 0x00FFFFFF));
		checkGPR(6, ((expectedCC != 0) ? 0x54000000 : 0) | ((atRight + rightDiff) & 0x00FFFFFF));
		*/
		checkGPR(4, (atLeft + leftDiff) & 0x00FFFFFF);
		checkGPR(6, (atRight + rightDiff) & 0x00FFFFFF);
		
		checkGPR(5, 0xCD000000 | Math.max(0, leftLen - leftDiff));
		checkGPR(7, (pad << 24) | Math.max(0, rightLen - rightDiff));
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_01_LeqR_wrapA_eq() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x00200000,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				0, -1
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_02_LltR_wrapA_eq() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x00200000,
				// byte[] right
				new byte[] {_01, _02, _03, _04, _A3, _A3},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				0, -1
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_03_LgtR_wrapA_eq() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04, _A3, _A3},
				// int atRight
				0x00200000,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				0, -1
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_04_LeqR_wrapA_CC1() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x00200000,
				// byte[] right
				new byte[] {_01, _02, _03, _05},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				1, 3
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_05_LltR_wrapA_CC1() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x00200000,
				// byte[] right
				new byte[] {_01, _02, _03, _04, _A3, _A4},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				1, 5
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_06_LgtR_wrapA_CC1() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04, _A3, _A2},
				// int atRight
				0x00200000,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				1, 5
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_07_LeqR_wrapA_CC2() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _05},
				// int atRight
				0x00200000,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				2, 3
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_08_LltR_wrapA_CC2() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x00200000,
				// byte[] right
				new byte[] {_01, _02, _03, _04, _A3, _A2},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				2, 5
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_09_LgtR_wrapA_CC2() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04, _A3, _A4},
				// int atRight
				0x00200000,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				2, 5
				);
	}
	
	// ... wrap B
	
	@Test
	public void x0F_RR_CompareCharacterLong_11_LeqR_wrapB_eq() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x00FFFFFE,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				0, -1
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_12_LltR_wrapB_eq() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x00FFFFFE,
				// byte[] right
				new byte[] {_01, _02, _03, _04, _A3, _A3},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				0, -1
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_13_LgtR_wrapB_eq() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04, _A3, _A3},
				// int atRight
				0x00FFFFFE,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				0, -1
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_14_LeqR_wrapB_CC1() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x00FFFFFE,
				// byte[] right
				new byte[] {_01, _02, _03, _05},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				1, 3
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_15_LltR_wrapB_CC1() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x00FFFFFE,
				// byte[] right
				new byte[] {_01, _02, _03, _04, _A3, _A4},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				1, 5
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_16_LgtR_wrapB_CC1() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04, _A3, _A2},
				// int atRight
				0x00FFFFFE,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				1, 5
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_17_LeqR_wrapB_CC2() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _05},
				// int atRight
				0x00FFFFFE,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				2, 3
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_18_LltR_wrapB_CC2() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x00FFFFFE,
				// byte[] right
				new byte[] {_01, _02, _03, _04, _A3, _A2},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				2, 5
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_19_LgtR_wrapB_CC2() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04, _A3, _A4},
				// int atRight
				0x00FFFFFE,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				2, 5
				);
	}
	
	// ... wrap C
	
	@Test
	public void x0F_RR_CompareCharacterLong_21_LeqR_wrapC_eq() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				0, -1
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_22_LltR_wrapC_eq() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _04, _A3, _A3},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				0, -1
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_23_LgtR_wrapC_eq() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04, _A3, _A3},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				0, -1
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_24_LeqR_wrapC_CC1() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _05},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				1, 3
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_25_LltR_wrapC_CC1() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _04, _A3, _A4},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				1, 5
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_26_LgtR_wrapC_CC1() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04, _A3, _A2},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				1, 5
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_27_LeqR_wrapC_CC2() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _05},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				2, 3
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_28_LltR_wrapC_CC2() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _04, _A3, _A2},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				2, 5
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_29_LgtR_wrapC_CC2() {
		innerTestClcl(
				// int atLeft
				0x00100000,
				// byte[] left
				new byte[] {_01, _02, _03, _04, _A3, _A4},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				2, 5
				);
	}
	
	// ... wrap D
	
	@Test
	public void x0F_RR_CompareCharacterLong_31_LeqR_wrapD_eq() {
		innerTestClcl(
				// int atLeft
				0x01001000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				0, -1
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_32_LltR_wrapD_eq() {
		innerTestClcl(
				// int atLeft
				0x01001000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _04, _A3, _A3},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				0, -1
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_33_LgtR_wrapD_eq() {
		innerTestClcl(
				// int atLeft
				0x01001000,
				// byte[] left
				new byte[] {_01, _02, _03, _04, _A3, _A3},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				0, -1
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_34_LeqR_wrapD_CC1() {
		innerTestClcl(
				// int atLeft
				0x01001000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _05},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				1, 3
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_35_LltR_wrapD_CC1() {
		innerTestClcl(
				// int atLeft
				0x01001000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _04, _A3, _A4},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				1, 5
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_36_LgtR_wrapD_CC1() {
		innerTestClcl(
				// int atLeft
				0x01001000,
				// byte[] left
				new byte[] {_01, _02, _03, _04, _A3, _A2},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				1, 5
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_37_LeqR_wrapD_CC2() {
		innerTestClcl(
				// int atLeft
				0x01001000,
				// byte[] left
				new byte[] {_01, _02, _03, _05},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				2, 3
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_38_LltR_wrapD_CC2() {
		innerTestClcl(
				// int atLeft
				0x01001000,
				// byte[] left
				new byte[] {_01, _02, _03, _04},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _04, _A3, _A2},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				2, 5
				);
	}
	
	@Test
	public void x0F_RR_CompareCharacterLong_39_LgtR_wrapD_CC2() {
		innerTestClcl(
				// int atLeft
				0x01001000,
				// byte[] left
				new byte[] {_01, _02, _03, _04, _A3, _A4},
				// int atRight
				0x01008000,
				// byte[] right
				new byte[] {_01, _02, _03, _04},
				// byte pad
                _A3,
				// int expectedCC, int expectedDiffAt
				2, 5
				);
	}
	
	// ... specification exceptions

	@Test
	public void x0F_RR_CompareCharacterLong_41_invR1() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC2);                       // for later comparison
		setInstructions(
				_0F, _36                  // CLCL R3,R6
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 1, 2, CodeBase+2); // ILC=2, CC=unchanged
	}

	@Test
	public void x0F_RR_CompareCharacterLong_41_invR2() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC2);                       // for later comparison
		setInstructions(
				_0F, _47                  // CLCL R4,R7
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 1, 2, CodeBase+2); // ILC=2, CC=unchanged
	}

}
