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
public class Cpu370BcTest_Decimal extends AbstractCpu370BcTest {
	
	public Cpu370BcTest_Decimal(Class<? extends Cpu370Bc> cpuClass) {
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
	
	private final int IA_PGM_INTR_BASE = 0x00700000;
	
	/*
	** Tests:  0x4E -- CVD [RX] - Convert to Decimal
	*/

	@Test
	public void x4E_RX_ConvertToDecimal_01_X2zero_B2_DISP_plus12345678() {
		setCC(CC2);                       // for later comparison
		setGPR(3, 12345678);              // R3: source value
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_4E, _30, _21, _80        // CVD R3 -> (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkCC(CC2);            // must stay unchanged
		
		checkMemB(0x010180, _00, _00, _00, _01, _23, _45, _67, _8C);
	}

	@Test
	public void x4E_RX_ConvertToDecimal_02_X2_B2_DISP_minus12345678() {
		setCC(CC1);                       // for later comparison
		setGPR(3, -12345678);             // R3: source value
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_4E, _31, _20, _80        // CVD R3 -> (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkCC(CC1);            // must stay unchanged
		
		checkMemB(0x010180, _00, _00, _00, _01, _23, _45, _67, _8D);
	}

	@Test
	public void x4E_RX_ConvertToDecimal_03_X2_B2zero_DISP_zero() {
		setCC(CC3);                       // for later comparison
		setGPR(3, 0);                     // R3: source value
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_4E, _31, _00, _80        // CVD R3 -> (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkCC(CC3);            // must stay unchanged
		
		checkMemB(0x010180, _00, _00, _00, _00, _00, _00, _00, _0C);
	}

	@Test
	public void x4E_RX_ConvertToDecimal_04_X2zero_B2_DISP_maxInt() {
		setCC(CC0);                       // for later comparison
		setGPR(3, 0x7FFFFFFF);            // R3: source value
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_4E, _30, _21, _80        // CVD R3 -> (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkCC(CC0);            // must stay unchanged
		
		checkMemB(0x010180, _00, _00, _02, _14, _74, _83, _64, _7C);
	}

	@Test
	public void x4E_RX_ConvertToDecimal_05_X2zero_B2_DISP_minInt() {
		setCC(CC2);                       // for later comparison
		setGPR(3, 0x80000000);            // R3: source value
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_4E, _30, _21, _80        // CVD R3 -> (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkCC(CC2);            // must stay unchanged
		
		checkMemB(0x010180, _00, _00, _02, _14, _74, _83, _64, _8D);
	}
	
	/*
	** Tests:  0x4F -- CVB [RX] - Convert to Binary
	*/

	@Test
	public void x4F_RX_ConvertToBinary_01_X2zero_B2_DISP_plus12345678() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010180, _00, _00, _00, _01, _23, _45, _67, _8C); // at 0x010180: source value
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_4F, _30, _21, _80        // CVB R3 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkCC(CC2);            // must stay unchanged
		
		checkGPR(3, 12345678);   // expected value
	}

	@Test
	public void x4F_RX_ConvertToBinary_02_X2_B2_DISP_minus12345678() {
		setCC(CC1);                       // for later comparison
		setMemB(0x010180, _00, _00, _00, _01, _23, _45, _67, _8D); // at 0x010180: source value
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_4F, _31, _20, _80        // CVB R3 <- (base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkCC(CC1);            // must stay unchanged
		
		checkGPR(3, -12345678);  // expected value
	}

	@Test
	public void x4F_RX_ConvertToBinary_03_X2_B2zero_DISP_zero() {
		setCC(CC3);                       // for later comparison
		setMemB(0x010180, _00, _00, _00, _00, _00, _00, _00, _0C); // at 0x010180: source value
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_4F, _31, _00, _80        // CVB R3 <- (base=0,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkCC(CC3);            // must stay unchanged
		
		checkGPR(3, 0);          // expected value
	}

	@Test
	public void x4F_RX_ConvertToBinary_04_X2zero_B2_DISP_maxInt() {
		setCC(CC0);                       // for later comparison
		setMemB(0x010180, _00, _00, _02, _14, _74, _83, _64, _7C); // at 0x010180: source value
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_4F, _30, _21, _80        // CVB R3 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkCC(CC0);            // must stay unchanged

		checkGPR(3, 0x7FFFFFFF); // expected value
	}

	@Test
	public void x4F_RX_ConvertToBinary_05_X2zero_B2_DISP_minInt() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010180, _00, _00, _02, _14, _74, _83, _64, _8D); // at 0x010180: source value
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_4F, _30, _21, _80        // CVB R3 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkCC(CC2);            // must stay unchanged

		checkGPR(3, 0x80000000); // expected value
	}

	@Test
	public void x4F_RX_ConvertToBinary_06_X2zero_B2_DISP_tooLargePositive() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC2);                       // for later comparison
		setMemB(0x010180, _99, _99, _99, _99, _99, _99, _99, _9C); // at 0x010180: source value
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_4F, _30, _21, _80        // CVB R3 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW

		checkGPR(3, 0xA4C67FFF); // expected value
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_DIVIDE, 2, 2, CodeBase+4); // ILC=2, CC=unchanged
	}

	@Test
	public void x4F_RX_ConvertToBinary_07_X2zero_B2_DISP_tooLargeNegative() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC2);                       // for later comparison
		setMemB(0x010180, _99, _99, _99, _99, _99, _99, _99, _9D); // at 0x010180: source value
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_4F, _30, _21, _80        // CVB R3 <- (base=R2,index=0,offset=0x180)
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW

		checkGPR(3, 0x5B398001); // expected value
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_FIXEDPOINT_DIVIDE, 2, 2, CodeBase+4); // ILC=2, CC=unchanged
	}
	
	/*
	** Tests:  0xF2 -- PACK [SS] - Pack
	*/

	@Test
	public void xF2_SS_Pack_01_8unp_5p() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010180,                 // fill the target area with input data
			_F1, _F2, _F3, _F4, _F5, _F6, _F7, _C8
			);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_F2, _47, _20, _01, _31, _80 // PACK (len=5,base=R2,offset=0x001) <- (len=8,base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC2);            // must be unchanged

		// check the target memory region (packed starts at 0x200001)
		checkMemB(0x200000, _00, _01, _23, _45, _67, _8C, _00);
	}

	@Test
	public void xF2_SS_Pack_02_8unp_4p() {
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // fill the target area with input data
			_F1, _F2, _F3, _F4, _F5, _F6, _F7, _C8
			);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_F2, _37, _20, _01, _31, _80 // PACK (len=4,base=R2,offset=0x001) <- (len=8,base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC3);            // must be unchanged

		// check the target memory region (packed starts at 0x200001)
		checkMemB(0x200000, _00, _23, _45, _67, _8C, _00, _00);
	}

	@Test
	public void xF2_SS_Pack_03_8unp_6p() {
		setCC(CC1);                       // for later comparison
		setMemB(0x010180,                 // fill the target area with input data
			_F1, _F2, _F3, _F4, _F5, _F6, _F7, _C8
			);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_F2, _57, _20, _01, _31, _80 // PACK (len=6,base=R2,offset=0x001) <- (len=8,base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC1);            // must be unchanged

		// check the target memory region (packed starts at 0x200001)
		checkMemB(0x200000, _00, _00, _01, _23, _45, _67, _8C, _00);	
	}

	@Test
	public void xF2_SS_Pack_04_swapByteNibbles() {
		setCC(CC0);                       // for later comparison
		setMemB(0x010180,                 // fill the target area with input data
			_19
			);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_F2, _00, _20, _01, _31, _80 // PACK (len=6,base=R2,offset=0x001) <- (len=8,base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC0);            // must be unchanged

		// check the target memory region (packed starts at 0x200001)
		checkMemB(0x200000, _00, _91, _00);	
	}
	
	/*
	** Tests:  0xF3 -- UNPK [SS] - Unpack
	*/

	@Test
	public void xF3_SS_Unpack_01_5p_8unp() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010180,                 // fill the target area with input data
			_01, _23, _45, _67, _8C
			);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_F3, _74, _20, _01, _31, _80 // UNPK (len=8,base=R2,offset=0x001) <- (len=5,base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC2);            // must be unchanged

		// check the target memory region (packed starts at 0x200001)
		checkMemB(0x200000, _00, _F1, _F2, _F3, _F4, _F5, _F6, _F7, _C8, _00);
	}

	@Test
	public void xF3_SS_Unpack_02_5p_7unp() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010180,                 // fill the target area with input data
			_01, _23, _45, _67, _8C
			);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_F3, _64, _20, _01, _31, _80 // UNPK (len=7,base=R2,offset=0x001) <- (len=5,base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC2);            // must be unchanged

		// check the target memory region (packed starts at 0x200001)
		checkMemB(0x200000, _00, _F2, _F3, _F4, _F5, _F6, _F7, _C8, _00);
	}

	@Test
	public void xF3_SS_Unpack_03a_5p_9unp() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010180,                 // fill the target area with input data
			_01, _23, _45, _67, _8C
			);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_F3, _84, _20, _01, _31, _80 // UNPK (len=9,base=R2,offset=0x001) <- (len=5,base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC2);            // must be unchanged

		// check the target memory region (packed starts at 0x200001)
		checkMemB(0x200000, _00, _F0, _F1, _F2, _F3, _F4, _F5, _F6, _F7, _C8, _00);
	}

	@Test
	public void xF3_SS_Unpack_03b_5p_9unp() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010180,                 // fill the target area with input data
			_71, _23, _45, _67, _8C
			);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_F3, _84, _20, _01, _31, _80 // UNPK (len=9,base=R2,offset=0x001) <- (len=5,base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC2);            // must be unchanged

		// check the target memory region (packed starts at 0x200001)
		checkMemB(0x200000, _00, _F7, _F1, _F2, _F3, _F4, _F5, _F6, _F7, _C8, _00);
	}

	@Test
	public void xF3_SS_Unpack_04_swapByteNibbles() {
		setCC(CC2);                       // for later comparison
		setMemB(0x010180,                 // fill the target area with input data
			_49
			);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_F3, _00, _20, _01, _31, _80 // UNPK (len=1,base=R2,offset=0x001) <- (len=1,base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC2);            // must be unchanged

		// check the target memory region (packed starts at 0x200001)
		checkMemB(0x200000, _00, _94, _00);
	}
	
	/*
	** Tests:  0xDE -- ED [SS] - Edit
	*/

	@Test
	public void xDE_SS_Edit_01_positive() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: the decimal value
			_02, _57, _42, _6C
			);
		setMemB(0x200000,                 // op1: edit mask (here: example from Appendix A in PrincOps, P. 306)
				_00, _40, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _40, _C3, _D9, _00);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_DE, _0C, _20, _01, _31, _80 // ED len=13 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC2);            // last field is greater than zero

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _40, _40, _F2, _6B, _F5, _F7, _F4, _4B, _F2, _F6, _40, _40, _40, _00);
	}

	@Test
	public void xDE_SS_Edit_02_negative() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: the decimal value
			_02, _57, _42, _6D
			);
		setMemB(0x200000,                 // op1: edit mask (here: example from Appendix A in PrincOps, P. 306)
				_00, _40, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _40, _C3, _D9, _00);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_DE, _0C, _20, _01, _31, _80 // ED len=13 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC1);            // last field is less than zero

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _40, _40, _F2, _6B, _F5, _F7, _F4, _4B, _F2, _F6, _40, _C3, _D9, _00);
	}

	@Test
	public void xDE_SS_Edit_03_zeroPositive() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: the decimal value
			_00, _00, _00, _0C
			);
		setMemB(0x200000,                 // op1: edit mask (here: example from Appendix A in PrincOps, P. 306)
				_00, _40, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _40, _C3, _D9, _00);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_DE, _0C, _20, _01, _31, _80 // ED len=13 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC0);            // last field is zero

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _40, _40, _40, _40, _40, _40, _40, _4B, _F0, _F0, _40, _40, _40, _00);
	}

	@Test
	public void xDE_SS_Edit_03b_zeroNegative() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: the decimal value
			_00, _00, _00, _0D
			);
		setMemB(0x200000,                 // op1: edit mask (here: example from Appendix A in PrincOps, P. 306)
				_00, _40, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _40, _C3, _D9, _00);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_DE, _0C, _20, _01, _31, _80 // ED len=13 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC0);            // last field is zero

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _40, _40, _40, _40, _40, _40, _40, _4B, _F0, _F0, _40, _C3, _D9, _00);
	}

	@Test
	public void xDE_SS_Edit_04_missingPackedBytes() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: the decimal value
			_02, _57, _42, _6C
			);
		setMemB(0x200000,                 // op1: edit mask (here: example from Appendix A in PrincOps, P. 306)
				_00, _40, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _20, _40, _C3, _D9, _00);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_DE, _0D, _20, _01, _31, _80 // ED len=14 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _40, _40, _F2, _6B, _F5, _F7, _F4, _4B, _F2, _F6, _20, _40, _C3, _D9, _00);
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_DATA_EXCEPTION, 3, 3, CodeBase+6); // ILC=2, CC=unchanged
	}

	@Test
	public void xDE_SS_Edit_05a_2fields_lastPositive() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: 2 decimal values
			_02, _57, _42, _6D,
			_00, _12, _3C
			);
		setMemB(0x200000,                 // op1
				_00, _40, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _40, _C3, _D9, 
				_22, _40, _20, _20, _20, _21, _20, _00);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_DE, _13, _20, _01, _31, _80 // ED len=20 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC2);            // last field is greater than zero

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _40, _40, _F2, _6B, _F5, _F7, _F4, _4B, _F2, _F6, _40, _C3, _D9,
				_40, _40, _40, _40, _F1, _F2, _F3, _00);
	}

	@Test
	public void xDE_SS_Edit_05b_2fields_lastNegative() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: 2 decimal values
			_02, _57, _42, _6C,
			_00, _12, _3D
			);
		setMemB(0x200000,                 // op1, this time: $ as fill char
				_00, _5B, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _40, _C3, _D9, 
				_22, _40, _20, _20, _20, _21, _20, _40, _C3, _D9, _00); 
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_DE, _16, _20, _01, _31, _80 // ED len=23 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC1);            // last field is greater than zero

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _5B, _5B, _F2, _6B, _F5, _F7, _F4, _4B, _F2, _F6, _5B, _5B, _5B,
				_5B, _5B, _5B, _5B, _F1, _F2, _F3, _40, _C3, _D9, _00);
	}

	@Test
	public void xDE_SS_Edit_05c_2fields_lastPositiveZero() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: 2 decimal values
			_02, _57, _42, _6C,
			_00, _00, _0C
			);
		setMemB(0x200000,                 // op1, this time: * as fill char
				_00, _5C, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _40, _C3, _D9, 
				_22, _40, _20, _20, _20, _21, _20, _40, _C3, _D9, _00); 
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_DE, _16, _20, _01, _31, _80 // ED len=23 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC0);            // last field is greater than zero

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _5C, _5C, _F2, _6B, _F5, _F7, _F4, _4B, _F2, _F6, _5C, _5C, _5C,
				_5C, _5C, _5C, _5C, _5C, _5C, _F0, _5C, _5C, _5C, _00);
	}

	@Test
	public void xDE_SS_Edit_05d_2fields_lastNegativeZero() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: 2 decimal values
			_02, _57, _42, _6C,
			_00, _00, _0D
			);
		setMemB(0x200000,                 // op1, this time: $ as fill char
				_00, _5B, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _40, _C3, _D9, 
				_22, _40, _20, _20, _20, _21, _20, _40, _C3, _D9, _00); 
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_DE, _16, _20, _01, _31, _80 // ED len=23 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC0);            // last field is greater than zero

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _5B, _5B, _F2, _6B, _F5, _F7, _F4, _4B, _F2, _F6, _5B, _5B, _5B,
				_5B, _5B, _5B, _5B, _5B, _5B, _F0, _40, _C3, _D9, _00);
	}
	
	/*
	** Tests:  0xDF -- EDMK [SS] - Edit and Mark
	*/

	@Test
	public void xDF_SS_EditAndMark_01_positive() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: the decimal value
			_02, _57, _42, _6C
			);
		setMemB(0x200000,                 // op1: edit mask (here: example from Appendix A in PrincOps, P. 306)
				_00, _40, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _40, _C3, _D9, _00);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setGPR(1, 0x87654321);            // set target register with data to be replaced/modified
		setInstructions(
				_DF, _0C, _20, _01, _31, _80 // ED len=13 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC2);            // last field is greater than zero

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _40, _40, _F2, _6B, _F5, _F7, _F4, _4B, _F2, _F6, _40, _40, _40, _00);
		
		// check the position of the first significant position in R1
		checkGPR(1, 0x87200003);
	}

	@Test
	public void xDF_SS_EditAndMark_02_negative() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: the decimal value
			_02, _57, _42, _6D
			);
		setMemB(0x200000,                 // op1: edit mask (here: example from Appendix A in PrincOps, P. 306)
				_00, _40, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _40, _C3, _D9, _00);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setGPR(1, 0x87654321);            // set target register with data to be replaced/modified
		setInstructions(
				_DF, _0C, _20, _01, _31, _80 // ED len=13 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC1);            // last field is less than zero

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _40, _40, _F2, _6B, _F5, _F7, _F4, _4B, _F2, _F6, _40, _C3, _D9, _00);
		
		// check the position of the first significant position in R1
		checkGPR(1, 0x87200003);
	}

	@Test
	public void xDF_SS_EditAndMark_03_zeroPositive() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: the decimal value
			_00, _00, _00, _0C
			);
		setMemB(0x200000,                 // op1: edit mask (here: example from Appendix A in PrincOps, P. 306)
				_00, _40, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _40, _C3, _D9, _00);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setGPR(1, 0x87654321);            // set target register with data to be replaced/modified
		setInstructions(
				_DF, _0C, _20, _01, _31, _80 // ED len=13 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC0);            // last field is zero

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _40, _40, _40, _40, _40, _40, _40, _4B, _F0, _F0, _40, _40, _40, _00);
		
		// check the position of the first significant position in R1
		checkGPR(1, 0x87654321); // "the character address is not stored when significance is forced" <= this is the case here...
	}

	@Test
	public void xDF_SS_EditAndMark_03b_zeroNegative() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: the decimal value
			_00, _00, _00, _0D
			);
		setMemB(0x200000,                 // op1: edit mask (here: example from Appendix A in PrincOps, P. 306)
				_00, _40, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _40, _C3, _D9, _00);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setGPR(1, 0x87654321);            // set target register with data to be replaced/modified
		setInstructions(
				_DF, _0C, _20, _01, _31, _80 // ED len=13 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC0);            // last field is zero

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _40, _40, _40, _40, _40, _40, _40, _4B, _F0, _F0, _40, _C3, _D9, _00);
		
		// check the position of the first significant position in R1
		checkGPR(1, 0x87654321); // "the character address is not stored when significance is forced" <= this is the case here...
	}

	@Test
	public void xDF_SS_EditAndMark_04_missingPackedBytes() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: the decimal value
			_02, _57, _42, _6C
			);
		setMemB(0x200000,                 // op1: edit mask (here: example from Appendix A in PrincOps, P. 306)
				_00, _40, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _20, _40, _C3, _D9, _00);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setGPR(1, 0x87654321);            // set target register with data to be replaced/modified
		setInstructions(
				_DF, _0D, _20, _01, _31, _80 // ED len=14 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _40, _40, _F2, _6B, _F5, _F7, _F4, _4B, _F2, _F6, _20, _40, _C3, _D9, _00);
		
		// check the position of the first significant position in R1
		checkGPR(1, 0x87200003);  // first true significant position before the data exception occurs
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_DATA_EXCEPTION, 3, 3, CodeBase+6); // ILC=2, CC=unchanged
	}

	@Test
	public void xDF_SS_EditAndMark_05a_2fields_lastPositive() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: 2 decimal values
			_02, _57, _42, _6D,
			_00, _12, _3C
			);
		setMemB(0x200000,                 // op1
				_00, _40, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _40, _C3, _D9, 
				_22, _40, _20, _20, _20, _21, _20, _00);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setGPR(1, 0x87654321);            // set target register with data to be replaced/modified
		setInstructions(
				_DF, _13, _20, _01, _31, _80 // ED len=20 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC2);            // last field is greater than zero

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _40, _40, _F2, _6B, _F5, _F7, _F4, _4B, _F2, _F6, _40, _C3, _D9,
				_40, _40, _40, _40, _F1, _F2, _F3, _00);
		
		// check the position of the first significant position in R1
		checkGPR(1, 0x87200012); // first significant position of the second field
	}

	@Test
	public void xDF_SS_EditAndMark_05b_2fields_lastNegative() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: 2 decimal values
			_02, _57, _42, _6C,
			_00, _12, _3D
			);
		setMemB(0x200000,                 // op1, this time: $ as fill char
				_00, _5B, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _40, _C3, _D9, 
				_22, _40, _20, _20, _20, _21, _20, _40, _C3, _D9, _00); 
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setGPR(1, 0x87654321);            // set target register with data to be replaced/modified
		setInstructions(
				_DF, _16, _20, _01, _31, _80 // ED len=23 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC1);            // last field is greater than zero

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _5B, _5B, _F2, _6B, _F5, _F7, _F4, _4B, _F2, _F6, _5B, _5B, _5B,
				_5B, _5B, _5B, _5B, _F1, _F2, _F3, _40, _C3, _D9, _00);
		
		// check the position of the first significant position in R1
		checkGPR(1, 0x87200012); // first significant position of the second field
	}

	@Test
	public void xDF_SS_EditAndMark_05c_2fields_lastPositiveZero() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: 2 decimal values
			_02, _57, _42, _6C,
			_00, _00, _0C
			);
		setMemB(0x200000,                 // op1, this time: * as fill char
				_00, _5C, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _40, _C3, _D9, 
				_22, _40, _20, _20, _20, _21, _20, _40, _C3, _D9, _00); 
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setGPR(1, 0x87654321);            // set target register with data to be replaced/modified
		setInstructions(
				_DF, _16, _20, _01, _31, _80 // ED len=23 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC0);            // last field is greater than zero

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _5C, _5C, _F2, _6B, _F5, _F7, _F4, _4B, _F2, _F6, _5C, _5C, _5C,
				_5C, _5C, _5C, _5C, _5C, _5C, _F0, _5C, _5C, _5C, _00);
		
		// check the position of the first significant position in R1
		checkGPR(1, 0x87200003); // second field is zero, so it is the first significant position of the first field
	}

	@Test
	public void xDF_SS_EditAndMark_05d_2fields_lastNegativeZero() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		setCC(CC3);                       // for later comparison
		setMemB(0x010180,                 // op2: 2 decimal values
			_02, _57, _42, _6C,
			_00, _00, _0D
			);
		setMemB(0x200000,                 // op1, this time: $ as fill char
				_00, _5B, _20, _20, _6B, _20, _20, _21, _4B, _20, _20, _40, _C3, _D9, 
				_22, _40, _20, _20, _20, _21, _20, _40, _C3, _D9, _00); 
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setGPR(1, 0x87654321);            // set target register with data to be replaced/modified
		setInstructions(
				_DF, _16, _20, _01, _31, _80 // ED len=23 (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC0);            // last field is greater than zero

		// check the target memory region (result starts at 0x200001)
		checkMemB(0x200000,
				_00, _5B, _5B, _F2, _6B, _F5, _F7, _F4, _4B, _F2, _F6, _5B, _5B, _5B,
				_5B, _5B, _5B, _5B, _5B, _5B, _F0, _40, _C3, _D9, _00);
		
		// check the position of the first significant position in R1
		checkGPR(1, 0x87200003); // second field is zero, so it is the first significant position of the first field
	}
	
	/*
	** Tests:  0xFA -- AP [SS] - Add Decimal
	*/
	
	private void innerTest_AddDecimal(
			int p1At, byte[] p1,
			int p2At, byte[] p2,
			int cc, boolean doIntr, int dataException,
			byte[] result) {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		if (doIntr) { setProgramMask(false, true, false, false); }
		
		setCC((byte)(dataException & 0x03));
		
		setMemB(p1At, p1);
		setMemB(p2At, p2);
		
		setGPR(2, 0x33000000 | (p1At & 0x00FFFFFF));
		setGPR(3, 0x44000000 | (p2At & 0x00FFFFFF));
		byte lengthsByte = (byte)((((p1.length - 1) & 0x0F) << 4) | ((p2.length - 1) & 0xF));

		setInstructions(
				_FA, lengthsByte, _20, _00, _30, _00 // AP len=?? (base=R2,offset=0x000) <- (base=R3,offset=0x000)
		);
		execute(1); // do one instructions
		
		if (dataException >= 0) {
			// a data exception interrupt is expected
			checkIL(0);                 // instruction length code loaded from new PSW
			checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
			checkCC(CC0);               // condition code loaded from new PSW

			checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_DATA_EXCEPTION, 3, dataException, CodeBase+6); // ILC=3, CC=unchanged
		} else if (cc == CC3 && doIntr) {
			// overflow expected and the program-interrupt should have been signaled
			checkIL(0);                 // instruction length code loaded from new PSW
			checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
			checkCC(CC0);               // condition code loaded from new PSW

			checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_DECIMAL_OVERFLOW, 3, 3, CodeBase+6); // ILC=3, CC=3 (overflow)
		} else {
			checkCC((byte)cc);
			checkIL(3);
			checkIA(CodeBase+6);     // instruction address at end
		}
		
		if (dataException < 0) { checkMemB(p1At, result); } // result only present if all input data were ok (i.e. no data exception)
	}

	@Test
	public void xFA_SS_AddDecimal_01a() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3A}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0F}, // +100
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_00, _22, _3C} // +223 (preferred positive sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_01b() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{ _12, _3A}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _10, _0F}, // +100
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_22, _3C} // +223 (preferred positive sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_01c() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3D}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0B}, // -100
				// int cc, boolean doIntr, int dataException,
				1, false, -1,
				// byte[] result
				new byte[]{_00, _22, _3D} // -223 (preferred negative sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_01d() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{ _12, _3D}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _10, _0B}, // -100
				// int cc, boolean doIntr, int dataException,
				1, false, -1,
				// byte[] result
				new byte[]{_22, _3D} // -223 (preferred negative sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_02a() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3E}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0B}, // -100
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_00, _02, _3C} // +23 (preferred positive sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_02b() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _3A}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _10, _0B}, // -100
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_02, _3C} // +23 (preferred positive sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_02c() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3B}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0A}, // +100
				// int cc, boolean doIntr, int dataException,
				1, false, -1,
				// byte[] result
				new byte[]{_00, _02, _3D} // -23 (preferred negative sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_02d() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _3B}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _10, _0F}, // +100
				// int cc, boolean doIntr, int dataException,
				1, false, -1,
				// byte[] result
				new byte[]{_02, _3D} // -23 (preferred negative sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_03a() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3B}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_12, _3C}, // +123
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _00, _0C} // 0 (preferred positive sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_03b() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _3B}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _12, _3A}, // +123
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _0C} // 0 (preferred positive sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_03c() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3A}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_12, _3B}, // -123
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _00, _0C} // 0 (preferred positive sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_03d() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _3A}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _12, _3B}, // -123
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _0C} // 0 (preferred positive sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_04a_overflow_noIntr() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _3E}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_90, _0F}, // +900
				// int cc, boolean doIntr, int dataException,
				3, false, -1,
				// byte[] result
				new byte[]{_02, _3C} // +23 (preferred positive sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_04b_overflow_noIntr() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _3B}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_90, _0B}, // -900
				// int cc, boolean doIntr, int dataException,
				3, false, -1,
				// byte[] result
				new byte[]{_02, _3D} // -23 (preferred negative sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_05a_overflow_intr() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _3E}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_90, _0F}, // +900
				// int cc, boolean doIntr, int dataException,
				3, true, -1,
				// byte[] result
				new byte[]{_02, _3C} // +23 (preferred positive sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_05b_overflow_intr() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _3B}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_90, _0B}, // -900
				// int cc, boolean doIntr, int dataException,
				3, true, -1,
				// byte[] result
				new byte[]{_02, _3D} // -23 (preferred negative sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_06a_31digits() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3A}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _6A}, // positive
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _9C} // positive
				);
	}

	@Test
	public void xFA_SS_AddDecimal_06b_31digits() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3A}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _6D}, // negative
				// int cc, boolean doIntr, int dataException,
				1, false, -1,
				// byte[] result
				new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3D} // negative
				);
	}

	@Test
	public void xFA_SS_AddDecimal_06c_31digits() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3D}, // negative
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _6A}, // positive
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3C} // positive
				);
	}

	@Test
	public void xFA_SS_AddDecimal_06d_31digits() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3D}, // negative
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _6D}, //  negative
				// int cc, boolean doIntr, int dataException,
				1, false, -1,
				// byte[] result
				new byte[]{_99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _9D} // negative
				);
	}

	@Test
	public void xFA_SS_AddDecimal_06e_31digits() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _3D}, // negative
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3C}, //  positive
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _0C} // positive
				);
	}

	@Test
	public void xFA_SS_AddDecimal_07a_dataException() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _A2, _3A}, // data exception (invalid upper digit)
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0F}, // +100
				// int cc, boolean doIntr, int dataException,
				2, false, 0,
				// byte[] result
				new byte[]{_00, _22, _3C} // +223 (preferred positive sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_07b_dataException() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _1B, _3A}, // data exception (invalid lower digit)
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0F}, // +100
				// int cc, boolean doIntr, int dataException,
				2, false, 1,
				// byte[] result
				new byte[]{_00, _22, _3C} // +223 (preferred positive sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_07c_dataException() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _32}, // data exception (invalid sign)
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0F}, // +100
				// int cc, boolean doIntr, int dataException,
				2, false, 2,
				// byte[] result
				new byte[]{_00, _22, _3C} // +223 (preferred positive sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_07d_dataException() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3A},
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _AF}, // data exception (invalid upper digit)
				// int cc, boolean doIntr, int dataException,
				2, false, 0,
				// byte[] result
				new byte[]{_00, _22, _3C} // +223 (preferred positive sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_07e_dataException() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3A},
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_1B, _0F}, // data exception (invalid lower digit)
				// int cc, boolean doIntr, int dataException,
				2, false, 1,
				// byte[] result
				new byte[]{_00, _22, _3C} // +223 (preferred positive sign)
				);
	}

	@Test
	public void xFA_SS_AddDecimal_07f_dataException() {
		innerTest_AddDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _32},
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _03}, // data exception (invalid sign)
				// int cc, boolean doIntr, int dataException,
				2, false, 2,
				// byte[] result
				new byte[]{_00, _22, _3C} // +223 (preferred positive sign)
				);
	}

	/*
	** Tests:  0xF9 -- CP [SS] - Compare Decimal
	*/
	
	private void innerTest_CompareDecimal(
			int p1At, byte[] p1,
			int p2At, byte[] p2,
			int cc) { // cc < 0 => DataException
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC3);
		
		setMemB(p1At, p1);
		setMemB(p2At, p2);
		
		setGPR(2, 0x33000000 | (p1At & 0x00FFFFFF));
		setGPR(3, 0x44000000 | (p2At & 0x00FFFFFF));
		byte lengthsByte = (byte)((((p1.length - 1) & 0x0F) << 4) | ((p2.length - 1) & 0xF));

		setInstructions(
				_F9, lengthsByte, _20, _00, _30, _00 // AP len=?? (base=R2,offset=0x000) <- (base=R3,offset=0x000)
		);
		execute(1); // do one instructions
		
		if (cc < 0) {
			// a data exception interrupt is expected
			checkIL(0);                 // instruction length code loaded from new PSW
			checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
			checkCC(CC0);               // condition code loaded from new PSW

			checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_DATA_EXCEPTION, 3, 3, CodeBase+6); // ILC=3, CC=unchanged
		} else {
			checkCC((byte)cc);
			checkIL(3);
			checkIA(CodeBase+6);     // instruction address at end
		}
	}

	@Test
	public void xF9_SS_CompareDecimal_01a() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3A}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0F}, // +100
				// int cc: first operand is high
				2);
	}

	@Test
	public void xF9_SS_CompareDecimal_01b() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3E}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_12, _3F}, // +123
				// int cc: operands are equal
				0);
	}

	@Test
	public void xF9_SS_CompareDecimal_01c() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3C}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_12, _4F}, // +124
				// int cc: first operand is low
				1);
	}

	@Test
	public void xF9_SS_CompareDecimal_01d() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3B}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0D}, // -100
				// int cc: first operand is low
				1);
	}

	@Test
	public void xF9_SS_CompareDecimal_01e() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3D}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_12, _3B}, // -123
				// int cc: operands are equal
				0);
	}

	@Test
	public void xF9_SS_CompareDecimal_01f() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3D}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_12, _4D}, // -124
				// int cc: first operand is high
				2);
	}

	@Test
	public void xF9_SS_CompareDecimal_02a() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _0C}, // +0
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _0D}, // -0
				// int cc: operands are equal
				0);
	}

	@Test
	public void xF9_SS_CompareDecimal_02b() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _0D}, // -0
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _0C}, // +0
				// int cc: operands are equal
				0);
	}

	@Test
	public void xF9_SS_CompareDecimal_02c() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _0C}, // +0
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _0F}, // +0
				// int cc: operands are equal
				0);
	}

	@Test
	public void xF9_SS_CompareDecimal_02d() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _0D}, // -0
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _0D}, // -0
				// int cc: operands are equal
				0);
	}

	@Test
	public void xF9_SS_CompareDecimal_03a() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _2C}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3C}, //  positive
				// int cc: first operand is low
				1);
	}

	@Test
	public void xF9_SS_CompareDecimal_03b() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _3C}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3C}, //  positive
				// int cc: operands are equal
				0);
	}

	@Test
	public void xF9_SS_CompareDecimal_03c() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _4C}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3C}, //  positive
				// int cc: first operand is high
				2);
	}

	@Test
	public void xF9_SS_CompareDecimal_03d() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _2D}, // negative
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3D}, //  positive
				// int cc: first operand is high
				2);
	}

	@Test
	public void xF9_SS_CompareDecimal_03e() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _3D}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3D}, //  positive
				// int cc: operands are equal
				0);
	}

	@Test
	public void xF9_SS_CompareDecimal_03f() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _4D}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3D}, //  positive
				// int cc: first operand is low
				1);
	}

	@Test
	public void xF9_SS_CompareDecimal_04a_dataException() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _32}, // data exception (invalid sign)
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0F}, // +100
				// int cc: first operand is low
				-1);
	}

	@Test
	public void xF9_SS_CompareDecimal_04b_dataException() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _1F, _3D}, // data exception (invalid lower digit)
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0F}, // +100
				// int cc: first operand is low
				-1);
	}

	@Test
	public void xF9_SS_CompareDecimal_04c_dataException() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _F2, _3D}, // data exception (invalid upper digit)
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0F}, // +100
				// int cc: first operand is low
				-1);
	}

	@Test
	public void xF9_SS_CompareDecimal_04d_dataException() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3D}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _02}, // data exception (invalid sign)
				// int cc: first operand is low
				-1);
	}

	@Test
	public void xF9_SS_CompareDecimal_04e_dataException() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3D}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_1B, _0F}, // data exception (invalid lower digit)
				// int cc: first operand is low
				-1);
	}

	@Test
	public void xF9_SS_CompareDecimal_04f_dataException() {
		innerTest_CompareDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _F2, _3D}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _CF}, // data exception (invalid upper digit)
				// int cc: first operand is low
				-1);
	}

	/*
	** Tests:  0xFD -- DP [SS] - Divide Decimal
	*/
	
	private void innerTest_DivideDecimal(
			int p1At, byte[] p1,
			int p2At, byte[] p2,
			int cc, boolean divException, boolean dataException,
			byte[] result) {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC((byte)(cc & 0x03));
		
		setMemB(p1At, p1);
		setMemB(p2At, p2);
		
		setGPR(2, 0x33000000 | (p1At & 0x00FFFFFF));
		setGPR(3, 0x44000000 | (p2At & 0x00FFFFFF));
		byte lengthsByte = (byte)((((p1.length - 1) & 0x0F) << 4) | ((p2.length - 1) & 0xF));

		setInstructions(
				_FD, lengthsByte, _20, _00, _30, _00 // DP len=?? (base=R2,offset=0x000) <- (base=R3,offset=0x000)
		);
		execute(1); // do one instructions
		
		if (dataException) {
			// a data exception interrupt is expected
			checkIL(0);                 // instruction length code loaded from new PSW
			checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
			checkCC(CC0);               // condition code loaded from new PSW

			checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_DATA_EXCEPTION, 3, cc, CodeBase+6); // ILC=3, CC=unchanged
		} else if (divException) {
			// divide exception expected and the program-interrupt should have been signaled
			checkIL(0);                 // instruction length code loaded from new PSW
			checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
			checkCC(CC0);               // condition code loaded from new PSW

			checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_DECIMAL_DIVIDE, 3, cc, CodeBase+6); // ILC=3, CC=unchanged
		} else {
			checkCC((byte)cc);
			checkIL(3);
			checkIA(CodeBase+6);     // instruction address at end
		}
		
		if (!dataException) { checkMemB(p1At, result); } // result only present if all input data were ok (i.e. no data exception)
	}

	@Test
	public void xFD_SS_DivideDecimal_01a_appendixA() {
		innerTest_DivideDecimal(
				//int p1At, byte[] p1,
				0x0007000, new byte[]{_01, _23, _45, _67, _8C}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_32, _1D}, // negative
				//int cc, boolean divException, boolean dataException,
				1, false, false,
				//byte[] result
				new byte[] {
						_38, _46, _0D,  // quotient, negative (signs of op1 and op2 differ)
						_01, _8C        // remainder, positive (op1 is positive)
						}
				);
	}

	@Test
	public void xFD_SS_DivideDecimal_01b_appendixA() {
		innerTest_DivideDecimal(
				//int p1At, byte[] p1,
				0x0007000, new byte[]{_01, _23, _45, _67, _8C}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_32, _1A}, // positive
				//int cc, boolean divException, boolean dataException,
				2, false, false,
				//byte[] result
				new byte[] {
						_38, _46, _0C,  // quotient, positive (signs of op1 and op2 are same)
						_01, _8C        // remainder, positive (op1 is positive)
						}
				);
	}

	@Test
	public void xFD_SS_DivideDecimal_01c_appendixA() {
		innerTest_DivideDecimal(
				//int p1At, byte[] p1,
				0x0007000, new byte[]{_01, _23, _45, _67, _8B}, // negative
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_32, _1F}, // positive
				//int cc, boolean divException, boolean dataException,
				3, false, false,
				//byte[] result
				new byte[] {
						_38, _46, _0D,  // quotient, negative (signs of op1 and op2 differ)
						_01, _8D        // remainder, negative (op1 is negative)
						}
				);
	}

	@Test
	public void xFD_SS_DivideDecimal_01d_appendixA() {
		innerTest_DivideDecimal(
				//int p1At, byte[] p1,
				0x0007000, new byte[]{_01, _23, _45, _67, _8B}, // negative
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_32, _1B}, // negative
				//int cc, boolean divException, boolean dataException,
				0, false, false,
				//byte[] result
				new byte[] {
						_38, _46, _0C,  // quotient, positive (signs of op1 and op2 are same)
						_01, _8D        // remainder, negative (op1 is negative)
						}
				);
	}

	@Test
	public void xFD_SS_DivideDecimal_02_quotientTooLarge() {
		innerTest_DivideDecimal(
				//int p1At, byte[] p1,
				0x0007000, new byte[]{_09, _99, _99, _99, _9F}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_99, _9C}, // positive
				//int cc, boolean divException, boolean dataException,
				0, true, false,
				//byte[] result
				new byte[]{_09, _99, _99, _99, _9F} // unchanged!
				);
	}

	@Test
	public void xFD_SS_DivideDecimal_03_upperlimitsOk() {
		innerTest_DivideDecimal(
				//int p1At, byte[] p1,
				0x0007000, new byte[]{_09, _98, _99, _99, _9C}, // positive = 99899001 + 998
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_99, _9C}, // positive
				//int cc, boolean divException, boolean dataException,
				0, false, false,
				//byte[] result
				new byte[]{
						_99, _99, _9C, 
						_99, _8C}
				);
	}

	@Test
	public void xFD_SS_DivideDecimal_04a_divisorZeroPositive() {
		innerTest_DivideDecimal(
				//int p1At, byte[] p1,
				0x0007000, new byte[]{_09, _99, _99, _99, _9E}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _0C}, // zero (positive)
				//int cc, boolean divException, boolean dataException,
				0, true, false,
				//byte[] result
				new byte[]{_09, _99, _99, _99, _9E} // unchanged
				);
	}

	@Test
	public void xFD_SS_DivideDecimal_04a_divisorZeroNegative() {
		innerTest_DivideDecimal(
				//int p1At, byte[] p1,
				0x0007000, new byte[]{_09, _99, _99, _99, _9E}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _0D}, // zero (negative)
				//int cc, boolean divException, boolean dataException,
				0, true, false,
				//byte[] result
				new byte[]{_09, _99, _99, _99, _9E} // unchanged
				);
	}

	@Test
	public void xFD_SS_DivideDecimal_05a_dataException() {
		innerTest_DivideDecimal(
				//int p1At, byte[] p1,
				0x0007000, new byte[]{_09, _98, _99, _99, _AC}, // invalid upper digit
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_99, _9C}, // positive
				//int cc, boolean divException, boolean dataException,
				0, false, true,
				//byte[] result
				new byte[]{_09, _98, _99, _99, _9C} // unchanged
				);
	}

	@Test
	public void xFD_SS_DivideDecimal_05b_dataException() {
		innerTest_DivideDecimal(
				//int p1At, byte[] p1,
				0x0007000, new byte[]{_09, _98, _99, _9A, _9C}, // invalid lower digit
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_99, _9C}, // positive
				//int cc, boolean divException, boolean dataException,
				0, false, true,
				//byte[] result
				new byte[]{_09, _98, _99, _99, _9C} // unchanged
				);
	}

	@Test
	public void xFD_SS_DivideDecimal_05c_dataException() {
		innerTest_DivideDecimal(
				//int p1At, byte[] p1,
				0x0007000, new byte[]{_09, _98, _99, _99, _93}, // invalid sign
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_99, _9C}, // positive
				//int cc, boolean divException, boolean dataException,
				0, false, true,
				//byte[] result
				new byte[]{_09, _98, _99, _99, _9C} // unchanged
				);
	}

	@Test
	public void xFD_SS_DivideDecimal_05d_dataException() {
		innerTest_DivideDecimal(
				//int p1At, byte[] p1,
				0x0007000, new byte[]{_09, _98, _99, _99, _9C}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_99, _CC}, // invalid upper digit
				//int cc, boolean divException, boolean dataException,
				0, false, true,
				//byte[] result
				new byte[]{_09, _98, _99, _99, _9C} // unchanged
				);
	}

	@Test
	public void xFD_SS_DivideDecimal_05e_dataException() {
		innerTest_DivideDecimal(
				//int p1At, byte[] p1,
				0x0007000, new byte[]{_09, _98, _99, _99, _9C}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_9F, _9C}, // invalid lower digit
				//int cc, boolean divException, boolean dataException,
				0, false, true,
				//byte[] result
				new byte[]{_09, _98, _99, _99, _9C} // unchanged
				);
	}

	@Test
	public void xFD_SS_DivideDecimal_05f_dataException() {
		innerTest_DivideDecimal(
				//int p1At, byte[] p1,
				0x0007000, new byte[]{_09, _98, _99, _99, _9C}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_99, _98}, // invalid sign
				//int cc, boolean divException, boolean dataException,
				0, false, true,
				//byte[] result
				new byte[]{_09, _98, _99, _99, _9C} // unchanged
				);
	}

	/*
	** Tests:  0xFC -- MP [SS] - Multiply Decimal
	*/
	
	private void innerTest_MultiplyDecimal(
			int p1At, byte[] p1,
			int p2At, byte[] p2,
			int cc, boolean specException, boolean dataException,
			byte[] result) {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC((byte)(cc & 0x03));
		
		setMemB(p1At, p1);
		setMemB(p2At, p2);
		
		setGPR(2, 0x33000000 | (p1At & 0x00FFFFFF));
		setGPR(3, 0x44000000 | (p2At & 0x00FFFFFF));
		byte lengthsByte = (byte)((((p1.length - 1) & 0x0F) << 4) | ((p2.length - 1) & 0xF));

		setInstructions(
				_FC, lengthsByte, _20, _00, _30, _00 // MP len=?? (base=R2,offset=0x000) <- (base=R3,offset=0x000)
		);
		execute(1); // do one instructions
		
		if (dataException) {
			// a data exception interrupt is expected
			checkIL(0);                 // instruction length code loaded from new PSW
			checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
			checkCC(CC0);               // condition code loaded from new PSW

			checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_DATA_EXCEPTION, 3, cc, CodeBase+6); // ILC=3, CC=unchanged
		} else if (specException) {
			// specification excaption expected and the program-interrupt should have been signaled
			checkIL(0);                 // instruction length code loaded from new PSW
			checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
			checkCC(CC0);               // condition code loaded from new PSW

			checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 3, cc, CodeBase+6); // ILC=3, CC=unchanged
		} else {
			checkCC((byte)cc);
			checkIL(3);
			checkIA(CodeBase+6);     // instruction address at end
		}
		
		if (!dataException) { checkMemB(p1At, result); } // result only present if all input data were ok (i.e. no data exception)
	}

	@Test
	public void xFC_SS_MultiplyDecimal_01a_appendixA() {
		innerTest_MultiplyDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _38, _46, _0D}, // negative
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_32, _1D}, // negative
				// int cc, boolean specException, boolean dataException,
				1, false, false,
				//byte[] result
				new byte[]{_01, _23, _45, _66, _0C}
				);
	}

	@Test
	public void xFC_SS_MultiplyDecimal_01b_appendixA() {
		innerTest_MultiplyDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _38, _46, _0D}, // negative
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_32, _1A}, // positive
				// int cc, boolean specException, boolean dataException,
				2, false, false,
				//byte[] result
				new byte[]{_01, _23, _45, _66, _0D}
				);
	}

	@Test
	public void xFC_SS_MultiplyDecimal_01c_appendixA() {
		innerTest_MultiplyDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _38, _46, _0E}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_32, _1B}, // negative
				// int cc, boolean specException, boolean dataException,
				3, false, false,
				//byte[] result
				new byte[]{_01, _23, _45, _66, _0D}
				);
	}

	@Test
	public void xFC_SS_MultiplyDecimal_01d_appendixA() {
		innerTest_MultiplyDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _38, _46, _0E}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_32, _1C}, // positive
				// int cc, boolean specException, boolean dataException,
				0, false, false,
				//byte[] result
				new byte[]{_01, _23, _45, _66, _0C}
				);
	}

	@Test
	public void xFC_SS_MultiplyDecimal_01f_limits() {
		innerTest_MultiplyDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _99, _99, _9E}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_99, _9C}, // positive
				// int cc, boolean specException, boolean dataException,
				1, false, false,
				//byte[] result
				new byte[]{_09, _98, _99, _00, _1C}
				);
	}

	@Test
	public void xFC_SS_MultiplyDecimal_01g_maxLimits() {
		innerTest_MultiplyDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _00, _00, _00, _00, _00, _00, _99, _99, _99, _99, _99, _99, _99, _9C}, // 16 bytes = 31 digits, 8 zero-bytes
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_99, _99, _99, _99, _99, _99, _99, _9C}, // 8 bytes = 15 digits
				// int cc, boolean specException, boolean dataException,
				2, false, false,
				//byte[] result
				new byte[]{_09, _99, _99, _99, _99, _99, _99, _98, _00, _00, _00, _00, _00, _00, _00, _1C}
				);
	}

	@Test
	public void xFC_SS_MultiplyDecimal_02a_ok() {
		innerTest_MultiplyDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _00, _00, _00, _00, _00, _00, _38, _46, _0E}, // 11 bytes = 21 digits, 8 zero-bytes
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _00, _00, _00, _00, _00, _32, _1C}, // 8 bytes = 15 digits
				// int cc, boolean specException, boolean dataException,
				3, false, false,
				//byte[] result
				new byte[]{_00, _00, _00, _00, _00, _00, _01, _23, _45, _66, _0C}
				);
	}

	@Test
	public void xFC_SS_MultiplyDecimal_02b_specExc_divisor9bytes() {
		innerTest_MultiplyDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _00, _00, _00, _00, _00, _00, _00, _38, _46, _0E}, // 12 bytes = 23 digits
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _00, _00, _00, _00, _00, _00, _32, _1C}, // 9 bytes = 17 digits -> spec exception
				// int cc, boolean specException, boolean dataException,
				1, true, false,
				//byte[] result
				new byte[]{_00, _00, _00, _00, _00, _00, _00, _00, _00, _38, _46, _0E} // unchanged
				);
	}

	@Test
	public void xFC_SS_MultiplyDecimal_02c_specExc_l2_notless_l1() {
		innerTest_MultiplyDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _00, _00, _00, _38, _46, _0E}, // 8 bytes = 15 digits
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _00, _00, _00, _00, _00, _32, _1C}, // 8 bytes = length not less than op1 -> spec exception
				// int cc, boolean specException, boolean dataException,
				1, true, false,
				//byte[] result
				new byte[]{_00, _00, _00, _00, _00, _38, _46, _0E} // unchanged
				);
	}

	@Test
	public void xFC_SS_MultiplyDecimal_02d_specExc_missingLeftmostZeros() {
		innerTest_MultiplyDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _00, _00, _00, _38, _46, _0E}, // 8 bytes with 5 zero-bytes -> spec exception
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _00, _00, _00, _32, _1C}, // 6 bytes
				// int cc, boolean specException, boolean dataException,
				1, true, false,
				//byte[] result
				new byte[]{_00, _00, _00, _00, _00, _38, _46, _0E} // unchanged
				);
	}

	@Test
	public void xFC_SS_MultiplyDecimal_03a_dataException() {
		innerTest_MultiplyDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _38, _46, _DD}, // invalid upper digit
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_32, _1D}, // negative
				// int cc, boolean specException, boolean dataException,
				1, false, true,
				//byte[] result
				new byte[]{_00, _00, _38, _46, _DD}
				);
	}

	@Test
	public void xFC_SS_MultiplyDecimal_03b_dataException() {
		innerTest_MultiplyDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _38, _4A, _0D}, // invalid lower digit
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_32, _1D}, // negative
				// int cc, boolean specException, boolean dataException,
				1, false, true,
				//byte[] result
				new byte[]{_00, _00, _38, _4A, _0D}
				);
	}

	@Test
	public void xFC_SS_MultiplyDecimal_03c_dataException() {
		innerTest_MultiplyDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _38, _46, _04}, // invalid sign
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_32, _1D}, // negative
				// int cc, boolean specException, boolean dataException,
				1, false, true,
				//byte[] result
				new byte[]{_00, _00, _38, _46, _04}
				);
	}

	@Test
	public void xFC_SS_MultiplyDecimal_03d_dataException() {
		innerTest_MultiplyDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _38, _46, _0D}, // negative
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_32, _FD}, // invalid lower digit
				// int cc, boolean specException, boolean dataException,
				1, false, true,
				//byte[] result
				new byte[]{_00, _00, _38, _46, _0D}
				);
	}

	@Test
	public void xFC_SS_MultiplyDecimal_03e_dataException() {
		innerTest_MultiplyDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _38, _46, _0D}, // negative
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_3A, _1D}, // invalid upper digit
				// int cc, boolean specException, boolean dataException,
				1, false, true,
				//byte[] result
				new byte[]{_00, _00, _38, _46, _0D}
				);
	}

	@Test
	public void xFC_SS_MultiplyDecimal_03f_dataException() {
		innerTest_MultiplyDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _00, _38, _46, _0D}, // negative
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_32, _15}, // invalid sign
				// int cc, boolean specException, boolean dataException,
				1, false, true,
				//byte[] result
				new byte[]{_00, _00, _38, _46, _0D}
				);
	}
	
	/*
	** Tests:  0xFB -- SP [SS] - Subtract Decimal
	*/
	
	private void innerTest_SubtractDecimal(
			int p1At, byte[] p1,
			int p2At, byte[] p2,
			int cc, boolean doIntr, int dataException,
			byte[] result) {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		if (doIntr) { setProgramMask(false, true, false, false); }
		
		setCC((byte)(dataException & 0x03));
		
		setMemB(p1At, p1);
		setMemB(p2At, p2);
		
		setGPR(2, 0x33000000 | (p1At & 0x00FFFFFF));
		setGPR(3, 0x44000000 | (p2At & 0x00FFFFFF));
		byte lengthsByte = (byte)((((p1.length - 1) & 0x0F) << 4) | ((p2.length - 1) & 0xF));

		setInstructions(
				_FB, lengthsByte, _20, _00, _30, _00 // SP len=?? (base=R2,offset=0x000) <- (base=R3,offset=0x000)
		);
		execute(1); // do one instructions
		
		if (dataException >= 0) {
			// a data exception interrupt is expected
			checkIL(0);                 // instruction length code loaded from new PSW
			checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
			checkCC(CC0);               // condition code loaded from new PSW

			checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_DATA_EXCEPTION, 3, dataException, CodeBase+6); // ILC=3, CC=unchanged
		} else if (cc == CC3 && doIntr) {
			// overflow expected and the program-interrupt should have been signaled
			checkIL(0);                 // instruction length code loaded from new PSW
			checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
			checkCC(CC0);               // condition code loaded from new PSW

			checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_DECIMAL_OVERFLOW, 3, 3, CodeBase+6); // ILC=3, CC=3 (overflow)
		} else {
			checkCC((byte)cc);
			checkIL(3);
			checkIA(CodeBase+6);     // instruction address at end
		}
		
		if (dataException < 0) { checkMemB(p1At, result); } // result only present if all input data were ok (i.e. no data exception)
	}

	@Test
	public void xFB_SS_SubtractDecimal_01a() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3A}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0F}, // +100
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_00, _02, _3C} // +23 (preferred positive sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_01b() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{ _12, _3A}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _10, _0F}, // +100
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_02, _3C} // +23 (preferred positive sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_01c() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3D}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0B}, // -100
				// int cc, boolean doIntr, int dataException,
				1, false, -1,
				// byte[] result
				new byte[]{_00, _02, _3D} // -23 (preferred negative sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_01d() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{ _12, _3D}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _10, _0B}, // -100
				// int cc, boolean doIntr, int dataException,
				1, false, -1,
				// byte[] result
				new byte[]{_02, _3D} // -23 (preferred negative sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_02a() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3E}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0B}, // -100
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_00, _22, _3C} // +223 (preferred positive sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_02b() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _3A}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _10, _0B}, // -100
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_22, _3C} // +223 (preferred positive sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_02c() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3B}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0A}, // +100
				// int cc, boolean doIntr, int dataException,
				1, false, -1,
				// byte[] result
				new byte[]{_00, _22, _3D} // -223 (preferred negative sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_02d() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _3B}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _10, _0F}, // +100
				// int cc, boolean doIntr, int dataException,
				1, false, -1,
				// byte[] result
				new byte[]{_22, _3D} // -223 (preferred negative sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_03a() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3B}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_12, _3D}, // -123
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _00, _0C} // 0 (preferred positive sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_03b() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _3B}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _12, _3D}, // -123
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _0C} // 0 (preferred positive sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_03c() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3A}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_12, _3F}, // +123
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _00, _0C} // 0 (preferred positive sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_03d() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _3A}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_00, _12, _3E}, // +123
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _0C} // 0 (preferred positive sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_04a_overflow_noIntr() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _3E}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_90, _0D}, // -900
				// int cc, boolean doIntr, int dataException,
				3, false, -1,
				// byte[] result
				new byte[]{_02, _3C} // +23 (preferred positive sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_04b_overflow_noIntr() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _3B}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_90, _0A}, // +900
				// int cc, boolean doIntr, int dataException,
				3, false, -1,
				// byte[] result
				new byte[]{_02, _3D} // -23 (preferred negative sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_05a_overflow_intr() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _3E}, // +123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_90, _0B}, // -900
				// int cc, boolean doIntr, int dataException,
				3, true, -1,
				// byte[] result
				new byte[]{_02, _3C} // +23 (preferred positive sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_05b_overflow_intr() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _3B}, // -123
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_90, _0A}, // +900
				// int cc, boolean doIntr, int dataException,
				3, true, -1,
				// byte[] result
				new byte[]{_02, _3D} // -23 (preferred negative sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_06a_31digits() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3A}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _6D}, // negative
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _9C} // positive
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_06b_31digits() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3A}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _6F}, // positive
				// int cc, boolean doIntr, int dataException,
				1, false, -1,
				// byte[] result
				new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3D} // negative
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_06c_31digits() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3D}, // negative
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _6B}, // negative
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3C} // positive
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_06d_31digits() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3D}, // negative
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _66, _6E}, //  positive
				// int cc, boolean doIntr, int dataException,
				1, false, -1,
				// byte[] result
				new byte[]{_99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _99, _9D} // negative
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_06e_31digits() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _3F}, // positive
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3C}, //  positive
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _0C} // positive
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_06f_31digits() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _33, _3D}, // negative
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_33, _33, _33, _33, _33, _33, _33, _33, _33, _33,_33, _33, _33, _33, _33, _3B}, //  negative
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _00, _0C} // positive
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_07a_dataException() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _A2, _3A}, // data exception (invalid upper digit)
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0F}, // +100
				// int cc, boolean doIntr, int dataException,
				2, false, 0,
				// byte[] result
				new byte[]{_00, _22, _3C} // +223 (preferred positive sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_07b_dataException() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _1B, _3A}, // data exception (invalid lower digit)
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0F}, // +100
				// int cc, boolean doIntr, int dataException,
				2, false, 1,
				// byte[] result
				new byte[]{_00, _22, _3C} // +223 (preferred positive sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_07c_dataException() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _32}, // data exception (invalid sign)
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _0F}, // +100
				// int cc, boolean doIntr, int dataException,
				2, false, 2,
				// byte[] result
				new byte[]{_00, _22, _3C} // +223 (preferred positive sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_07d_dataException() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3A},
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _AF}, // data exception (invalid upper digit)
				// int cc, boolean doIntr, int dataException,
				2, false, 0,
				// byte[] result
				new byte[]{_00, _22, _3C} // +223 (preferred positive sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_07e_dataException() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _3A},
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_1B, _0F}, // data exception (invalid lower digit)
				// int cc, boolean doIntr, int dataException,
				2, false, 1,
				// byte[] result
				new byte[]{_00, _22, _3C} // +223 (preferred positive sign)
				);
	}

	@Test
	public void xFB_SS_SubtractDecimal_07f_dataException() {
		innerTest_SubtractDecimal(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_00, _12, _32},
				// int p2At, byte[] p2,
				0x0008000, new byte[]{_10, _03}, // data exception (invalid sign)
				// int cc, boolean doIntr, int dataException,
				2, false, 2,
				// byte[] result
				new byte[]{_00, _22, _3C} // +223 (preferred positive sign)
				);
	}
	
	/*
	** Tests:  0xF0 -- SRP [SS] - Shift and Round Decimal
	*/
	
	private void innerTest_ShiftAndRoundDecimal(
			int pAt, byte[] p,
			int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
			int roundingFactor,
			int cc, boolean doIntr, int dataException,
			byte[] result) {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		if (doIntr) { setProgramMask(false, true, false, false); }
		
		setCC((byte)(dataException & 0x03));
		
		setMemB(pAt, p);
		setGPR(2, 0x33000000 | (pAt & 0x00FFFFFF));
		
		byte op2Byte0 = (regVal == 0) ? _00 : _30;
		byte op2Byte1 = (regVal < 0) ? _00 : (byte)(shiftBy & 0x3F);
		if (regVal < 0) { setGPR(3, shiftBy); }
		if (regVal > 0) { setGPR(3, regVal << 6); }
		
		byte lengthsByte = (byte)((((p.length - 1) & 0x0F) << 4) | (roundingFactor & 0xF));

		setInstructions(
				_F0, lengthsByte, _20, _00, op2Byte0, op2Byte1 // SRP len=?? (base=R2,offset=0x000) <- (base=R3|R0,offset=shiftBy|0x000)
		);
		execute(1); // do one instructions
		
		if (dataException >= 0) {
			// a data exception interrupt is expected
			checkIL(0);                 // instruction length code loaded from new PSW
			checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
			checkCC(CC0);               // condition code loaded from new PSW

			checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_DATA_EXCEPTION, 3, dataException, CodeBase+6); // ILC=3, CC=unchanged
		} else if (cc == CC3 && doIntr) {
			// overflow expected and the program-interrupt should have been signaled
			checkIL(0);                 // instruction length code loaded from new PSW
			checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
			checkCC(CC0);               // condition code loaded from new PSW

			checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_DECIMAL_OVERFLOW, 3, 3, CodeBase+6); // ILC=3, CC=3 (overflow)
		} else {
			checkCC((byte)cc);
			checkIL(3);
			checkIA(CodeBase+6);     // instruction address at end
		}
		
		if (dataException < 0) { checkMemB(pAt, result); } // result only present if all input data were ok (i.e. no data exception)
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_01a_shiftLeft_1digits() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_00, _01, _23, _45, _67, _8C},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				1, -1,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_00, _12, _34, _56, _78, _0C} // positive must stay positive when shifting left
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_01b_shiftLeft_2digits() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_00, _01, _23, _45, _67, _8D},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				2, -1,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				1, false, -1,
				// byte[] result
				new byte[]{_01, _23, _45, _67, _80, _0D} // negative must stay negative when shifting left
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_01c_shiftLeft_3digits() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_00, _01, _23, _45, _67, _8C},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				3, -1,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_12, _34, _56, _78, _00, _0C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_01d_shiftLeft_4digits_overflowNoIntr() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_00, _01, _23, _45, _67, _8C},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				4, -1,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				3, false, -1,
				// byte[] result
				new byte[]{_23, _45, _67, _80, _00, _0C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_01e_shiftLeft_4digits_overflowIntr() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_00, _01, _23, _45, _67, _8C},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				4, -1,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				3, true, -1,
				// byte[] result
				new byte[]{_23, _45, _67, _80, _00, _0C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_01f_shiftLeft_allDigits_positive_overflowNoIntr() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_00, _01, _23, _45, _67, _8C},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				11, -1,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				3, false, -1,
				// byte[] result
				new byte[]{_00, _00, _00, _00, _00, _0C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_01g_shiftLeft_allDigits_positive_overflowIntr() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_00, _01, _23, _45, _67, _8C},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				11, -1,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				3, true, -1,
				// byte[] result
				new byte[]{_00, _00, _00, _00, _00, _0C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_01h_shiftLeft_allDigits_negative_overflowNoIntr() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_00, _01, _23, _45, _67, _8D},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				11, -1,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				3, false, -1,
				// byte[] result
				new byte[]{_00, _00, _00, _00, _00, _0D}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_01i_shiftLeft_allDigits_negative_overflowIntr() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_00, _01, _23, _45, _67, _8D},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				11, -1,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				3, true, -1,
				// byte[] result
				new byte[]{_00, _00, _00, _00, _00, _0D}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_02a_shiftRight_1digits() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_01, _23, _45, _67, _8C},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				-1, -1,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_00, _12, _34, _56, _7C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_02b_shiftRight_2digits() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_01, _23, _45, _67, _8C},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				-2, 0,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_00, _01, _23, _45, _6C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_02c_shiftRight_3digits() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_01, _23, _45, _67, _8C},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				-3, 0x123456,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_00, _00, _12, _34, _5C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_02d_shiftRight_7digits() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_01, _23, _45, _67, _8C},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				-7, -1,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_00, _00, _00, _00, _1C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_02e_shiftRight_8digits() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_01, _23, _45, _67, _8C},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				-8, 0,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _00, _00, _00, _0C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_03a_shiftRight_1digits_roundAbove6() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_01, _23, _45, _67, _8C},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				-1, -1,
				// int roundingFactor,
				3,
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_00, _12, _34, _56, _8C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_03b_shiftRight_2digits_roundAbove6() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_01, _23, _45, _67, _8C},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				-2, 0,
				// int roundingFactor,
				3,
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_00, _01, _23, _45, _7C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_03c_shiftRight_3digits_roundAbove6() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_01, _23, _45, _67, _8C},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				-3, 0x123456,
				// int roundingFactor,
				3,
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_00, _00, _12, _34, _6C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_03d_shiftRight_4digits_roundAbove6() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_01, _23, _45, _67, _8C},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				-4, 0x123456,
				// int roundingFactor,
				3,
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_00, _00, _01, _23, _4C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_04a_shiftRight_positive_allDigits_roundNone() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_99, _99, _99, _99, _9C},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				-9, 0x123456,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _00, _00, _00, _0C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_04b_shiftRight_positive_allDigits_roundAbove0() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_99, _99, _99, _99, _9C},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				-9, 0x123456,
				// int roundingFactor,
				9,
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_00, _00, _00, _00, _1C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_04c_shiftRight_negative_allDigits_roundNone() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_99, _99, _99, _99, _9D},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				-9, 0x123456,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _00, _00, _00, _0C} // !! zero is positive in absence of overflow !!
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_04d_shiftRight_negative_allDigits_roundAbove0() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_99, _99, _99, _99, _9D},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				-9, 0x123456,
				// int roundingFactor,
				9,
				// int cc, boolean doIntr, int dataException,
				1, false, -1,
				// byte[] result
				new byte[]{_00, _00, _00, _00, _1D}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_05a_noShift_positive() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_09, _99, _99, _99, _0F},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				0, 0x123456,
				// int roundingFactor,
				9,
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_09, _99, _99, _99, _0C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_05b_noShift_negative() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_09, _99, _99, _99, _0B},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				0, 0x123456,
				// int roundingFactor,
				9,
				// int cc, boolean doIntr, int dataException,
				1, false, -1,
				// byte[] result
				new byte[]{_09, _99, _99, _99, _0D}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_05c_noShift_zeroPositive() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_00, _00, _00, _00, _0E},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				0, 0x123456,
				// int roundingFactor,
				9,
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _00, _00, _00, _0C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_05d_noShift_zeroNegative() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_00, _00, _00, _00, _0B},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				0, 0x123456,
				// int roundingFactor,
				9,
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _00, _00, _00, _0C}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_06a_dataException() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_12, _34, _56, _78, _BB}, // invalid upper-digit
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				-1, 0x123456,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				0, false, 0,
				// byte[] result
				new byte[]{_12, _34, _56, _78, _BB}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_06b_dataException() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_12, _34, _56, _7B, _9B}, // invalid lower-digit
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				-1, 0x123456,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				0, false, 1,
				// byte[] result
				new byte[]{_12, _34, _56, _7B, _9B}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_06c_dataException() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_12, _34, _56, _78, _93}, // invalid sign
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				-1, 0x123456,
				// int roundingFactor,
				0,
				// int cc, boolean doIntr, int dataException,
				0, false, 2,
				// byte[] result
				new byte[]{_12, _34, _56, _78, _93}
				);
	}

	@Test
	public void xF0_SS_ShiftAndRoundDecimal_06d_dataException() {
		innerTest_ShiftAndRoundDecimal(
				// int pAt, byte[] p,
				0x0007000, new byte[]{_12, _34, _56, _78, _9B},
				// int shiftBy, int regVal, // regval < 0 => shiftBy only in Register, = 0 => shiftBy only in offset, > 0 => combined 
				-1, 0x123456,
				// int roundingFactor,
				11, // invalid rounding factor
				// int cc, boolean doIntr, int dataException,
				0, false, 3,
				// byte[] result
				new byte[]{_12, _34, _56, _78, _9B}
				);
	}
	
	/*
	** Tests:  0xF8 -- ZAP [SS] - Zero and Add Packed
	*/
	
	private void innerTest_ZeroAndAddPacked(
			int p1At, byte[] p1,
			int p2At, byte[] p2,
			int cc, boolean doIntr, int dataException,
			byte[] result) {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		if (doIntr) { setProgramMask(false, true, false, false); }
		
		setCC((byte)(dataException & 0x03));
		
		setMemB(p1At, p1);
		setMemB(p2At, p2);
		
		setGPR(2, 0x33000000 | (p1At & 0x00FFFFFF));
		setGPR(3, 0x44000000 | (p2At & 0x00FFFFFF));
		byte lengthsByte = (byte)((((p1.length - 1) & 0x0F) << 4) | ((p2.length - 1) & 0xF));

		setInstructions(
				_F8, lengthsByte, _20, _00, _30, _00 // ZAP len=?? (base=R2,offset=0x000) <- (base=R3,offset=0x000)
		);
		execute(1); // do one instructions
		
		if (dataException >= 0) {
			// a data exception interrupt is expected
			checkIL(0);                 // instruction length code loaded from new PSW
			checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
			checkCC(CC0);               // condition code loaded from new PSW

			checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_DATA_EXCEPTION, 3, dataException, CodeBase+6); // ILC=3, CC=unchanged
		} else if (cc == CC3 && doIntr) {
			// overflow expected and the program-interrupt should have been signaled
			checkIL(0);                 // instruction length code loaded from new PSW
			checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
			checkCC(CC0);               // condition code loaded from new PSW

			checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_DECIMAL_OVERFLOW, 3, 3, CodeBase+6); // ILC=3, CC=3 (overflow)
		} else {
			checkCC((byte)cc);
			checkIL(3);
			checkIA(CodeBase+6);     // instruction address at end
		}
		
		if (dataException < 0) { checkMemB(p1At, result); } // result only present if all input data were ok (i.e. no data exception)
	}

	@Test
	public void xF8_SS_ZeroAndAddPacked_01a_positive() {
		innerTest_ZeroAndAddPacked(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _34, _56, _78}, // invalid sign, but must be ignored
				// int p2At, byte[] p2,
				0x0007000, new byte[]{_00, _99, _88, _77, _6F}, // positive
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_99, _88, _77, _6C}
				);
	}

	@Test
	public void xF8_SS_ZeroAndAddPacked_01b_positive() {
		innerTest_ZeroAndAddPacked(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _34, _56, _CC}, // invalid upper digit, but must be ignored
				// int p2At, byte[] p2,
				0x0007000, new byte[]{_88, _77, _6F}, // positive
				// int cc, boolean doIntr, int dataException,
				2, false, -1,
				// byte[] result
				new byte[]{_00, _88, _77, _6C}
				);
	}

	@Test
	public void xF8_SS_ZeroAndAddPacked_01c_negative() {
		innerTest_ZeroAndAddPacked(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _34, _5F, _7C}, // invalid lower digit, but must be ignored
				// int p2At, byte[] p2,
				0x0007000, new byte[]{_00, _99, _88, _77, _6B}, // negative
				// int cc, boolean doIntr, int dataException,
				1, false, -1,
				// byte[] result
				new byte[]{_99, _88, _77, _6D}
				);
	}

	@Test
	public void xF8_SS_ZeroAndAddPacked_01d_negative() {
		innerTest_ZeroAndAddPacked(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _34, _56, _78}, // invalid sign, but must be ignored
				// int p2At, byte[] p2,
				0x0007000, new byte[]{_88, _77, _6B}, // negative
				// int cc, boolean doIntr, int dataException,
				1, false, -1,
				// byte[] result
				new byte[]{_00, _88, _77, _6D}
				);
	}

	@Test
	public void xF8_SS_ZeroAndAddPacked_01e_zeroPositive() {
		innerTest_ZeroAndAddPacked(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _34, _56, _78}, // invalid sign, but must be ignored
				// int p2At, byte[] p2,
				0x0007000, new byte[]{_00, _00, _0A}, // positive
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _00, _00, _0C}
				);
	}

	@Test
	public void xF8_SS_ZeroAndAddPacked_01f_zeroNegative() {
		innerTest_ZeroAndAddPacked(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _34, _56, _78}, // invalid sign, but must be ignored
				// int p2At, byte[] p2,
				0x0007000, new byte[]{_00, _00, _0B}, // negative
				// int cc, boolean doIntr, int dataException,
				0, false, -1,
				// byte[] result
				new byte[]{_00, _00, _00, _0C}
				);
	}

	@Test
	public void xF8_SS_ZeroAndAddPacked_02b_positive_overflow_noIntr() {
		innerTest_ZeroAndAddPacked(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _34, _56, _78}, // invalid sign, but must be ignored
				// int p2At, byte[] p2,
				0x0007000, new byte[]{_33, _99, _88, _77, _6F}, // positive
				// int cc, boolean doIntr, int dataException,
				3, false, -1,
				// byte[] result
				new byte[]{_99, _88, _77, _6C}
				);
	}

	@Test
	public void xF8_SS_ZeroAndAddPacked_02b_positive_overflow_intr() {
		innerTest_ZeroAndAddPacked(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _34, _56, _78}, // invalid sign, but must be ignored
				// int p2At, byte[] p2,
				0x0007000, new byte[]{_33, _99, _88, _77, _6F}, // positive
				// int cc, boolean doIntr, int dataException,
				3, true, -1,
				// byte[] result
				new byte[]{_99, _88, _77, _6C}
				);
	}

	@Test
	public void xF8_SS_ZeroAndAddPacked_02c_negative_overflow_noIntr() {
		innerTest_ZeroAndAddPacked(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _34, _56, _78}, // invalid sign, but must be ignored
				// int p2At, byte[] p2,
				0x0007000, new byte[]{_33, _99, _88, _77, _6B}, // negative
				// int cc, boolean doIntr, int dataException,
				3, false, -1,
				// byte[] result
				new byte[]{_99, _88, _77, _6D}
				);
	}

	@Test
	public void xF8_SS_ZeroAndAddPacked_02d_negative_overflow_intr() {
		innerTest_ZeroAndAddPacked(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _34, _56, _78}, // invalid sign, but must be ignored
				// int p2At, byte[] p2,
				0x0007000, new byte[]{_33, _99, _88, _77, _6D}, // negative
				// int cc, boolean doIntr, int dataException,
				3, true, -1,
				// byte[] result
				new byte[]{_99, _88, _77, _6D}
				);
	}

	@Test
	public void xF8_SS_ZeroAndAddPacked_03a_dataException() {
		innerTest_ZeroAndAddPacked(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _34, _56, _7C},
				// int p2At, byte[] p2,
				0x0007000, new byte[]{_33, _99, _88, _77, _63}, // invalid sign
				// int cc, boolean doIntr, int dataException,
				2, false, 1,
				// byte[] result
				new byte[]{_12, _34, _56, _7C}
				);
	}

	@Test
	public void xF8_SS_ZeroAndAddPacked_03b_dataException() {
		innerTest_ZeroAndAddPacked(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _34, _56, _7C},
				// int p2At, byte[] p2,
				0x0007000, new byte[]{_33, _99, _88, _7A, _6C}, // invalid lower digit
				// int cc, boolean doIntr, int dataException,
				2, false, 1,
				// byte[] result
				new byte[]{_12, _34, _56, _7C}
				);
	}

	@Test
	public void xF8_SS_ZeroAndAddPacked_03c_dataException() {
		innerTest_ZeroAndAddPacked(
				// int p1At, byte[] p1,
				0x0007000, new byte[]{_12, _34, _56, _7C},
				// int p2At, byte[] p2,
				0x0007000, new byte[]{_33, _99, _88, _77, _FC}, // invalid upper digit
				// int cc, boolean doIntr, int dataException,
				2, false, 1,
				// byte[] result
				new byte[]{_12, _34, _56, _7C}
				);
	}

}
