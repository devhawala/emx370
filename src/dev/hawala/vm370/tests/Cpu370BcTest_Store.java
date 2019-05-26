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
public class Cpu370BcTest_Store extends AbstractCpu370BcTest {
	
	public Cpu370BcTest_Store(Class<? extends Cpu370Bc> cpuClass) {
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
	** Tests:  0x50 -- ST [RX] - Store
	*/

	@Test
	public void x50_RX_Store_01_X2zero_B2_DISP() {
		setCC(CC2);                       // for later comparison
		setGPR(3, 0x12345678);            // register with value to be stored
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_50, _30, _21, _84        // ST (base=R2,index=0,offset=0x184) <- R3
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkMemF(0x010180, 0, 0x12345678, 0); // value that must have been stored at 0x010184
		checkGPR(3, 0x12345678); // must stay unchanged 
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void x50_RX_Store_02_X2_B2_DISP() {
		setCC(CC1);                       // for later comparison
		setGPR(3, 0x87654321);            // register with value to be stored
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_50, _31, _20, _84        // ST (base=R2,index=R1,offset=0x084) <- R3
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkMemF(0x010180, 0, 0x87654321, 0); // value that must have been stored at 0x010184
		checkGPR(3, 0x87654321); // must stay unchanged 
		checkCC(CC1);            // must stay unchanged
	}

	@Test
	public void x50_RX_Store_03_X2_B2zero_DISP() {
		setCC(CC3);                       // for later comparison
		setGPR(3, 0x12345678);            // register with value to be stored
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_50, _31, _0E, _84        // ST (base=0,index=R1,offset=0xE84) <- R3
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkMemF(0x010F80, 0, 0x12345678, 0); // value that must have been stored at 0x010F84
		checkGPR(3, 0x12345678); // must stay unchanged 
		checkCC(CC3);            // must stay unchanged
	}
	
	/*
	** Tests:  0x42 -- STC [RX] - Store Character
	*/

	@Test
	public void x42_RX_StoreCharacter_01_X2zero_B2_DISP() {
		setCC(CC2);                       // for later comparison
		setGPR(3, 0x12345678);            // register with value to be stored
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_42, _30, _21, _84        // STC (base=R2,index=0,offset=0x184) <- R3
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkMemF(0x010180, 0, 0x78000000, 0); // value that must have been stored at 0x010184
		checkGPR(3, 0x12345678); // must stay unchanged 
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void x42_RX_StoreCharacter_02_X2_B2_DISP() {
		setCC(CC1);                       // for later comparison
		setGPR(3, 0x22446688);            // register with value to be stored
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_42, _31, _20, _84        // STC (base=R2,index=R1,offset=0x084) <- R3
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkMemF(0x010180, 0, 0x88000000, 0); // value that must have been stored at 0x010184
		checkGPR(3, 0x22446688); // must stay unchanged 
		checkCC(CC1);            // must stay unchanged
	}

	@Test
	public void x42_RX_StoreCharacter_03_X2_B2zero_DISP() {
		setCC(CC3);                       // for later comparison
		setGPR(3, 0x12345678);            // register with value to be stored
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_42, _31, _0E, _84        // STC (base=0,index=R1,offset=0xE84) <- R3
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkMemF(0x010F80, 0, 0x78000000, 0); // value that must have been stored at 0x010F84
		checkGPR(3, 0x12345678); // must stay unchanged 
		checkCC(CC3);            // must stay unchanged
	}
	
	/*
	** Tests:  0xBE -- STCM [RS] - Store Character under Mask
	*/

	@Test
	public void xBE_RS_StoreCharacterUnderMask_01_mask_1111() {
		setCC(CC2);                       // for later comparison
		setGPR(3, 0x12345678);            // register with value to be stored
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_BE, _3F, _21, _84        // STCM (base=R2,offset=0x184) <- R3, mask=1111b
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkMemF(0x010180, 0, 0x12345678, 0); // value that must have been stored at 0x010184
		checkGPR(3, 0x12345678); // must stay unchanged 
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void xBE_RS_StoreCharacterUnderMask_02_mask_0110() {
		setCC(CC2);                       // for later comparison
		setGPR(3, 0x8899AABB);            // register with value to be stored
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_BE, _36, _21, _84        // STCM (base=R2,offset=0x184) <- R3, mask=0110b
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkMemF(0x010180, 0, 0x99AA0000, 0); // value that must have been stored at 0x010184
		checkGPR(3, 0x8899AABB); // must stay unchanged 
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void xBE_RS_StoreCharacterUnderMask_03_mask_1001() {
		setCC(CC2);                       // for later comparison
		setGPR(3, 0x12345678);            // register with value to be stored
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_BE, _39, _21, _84        // STCM (base=R2,offset=0x184) <- R3, mask=1001b
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkMemF(0x010180, 0, 0x12780000, 0); // value that must have been stored at 0x010184
		checkGPR(3, 0x12345678); // must stay unchanged 
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void xBE_RS_StoreCharacterUnderMask_04_mask_0000() {
		setCC(CC2);                       // for later comparison
		setGPR(3, 0x12345678);            // register with value to be stored
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_BE, _30, _21, _84        // STCM (base=R2,offset=0x184) <- R3, mask=1001b
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkMemF(0x010180, 0, 0x00000000, 0); // value that must have been stored at 0x010184
		checkGPR(3, 0x12345678); // must stay unchanged 
		checkCC(CC2);            // must stay unchanged
	}
	
	/*
	** Tests:  0x40 -- STH [RX] - Store Halfword
	*/

	@Test
	public void x40_RX_StoreHalfword_01_X2zero_B2_DISP() {
		setCC(CC2);                       // for later comparison
		setGPR(3, 0x12345678);            // register with value to be stored
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_40, _30, _21, _84        // STH (base=R2,index=0,offset=0x184) <- R3
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkMemF(0x010180, 0, 0x56780000, 0); // value that must have been stored at 0x010184
		checkGPR(3, 0x12345678); // must stay unchanged 
		checkCC(CC2);            // must stay unchanged
	}

	@Test
	public void x40_RX_StoreHalfword_02_X2_B2_DISP() {
		setCC(CC1);                       // for later comparison
		setGPR(3, 0x8899AABB);            // register with value to be stored
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_40, _31, _20, _84        // STH (base=R2,index=R1,offset=0x084) <- R3
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkMemF(0x010180, 0, 0xAABB0000, 0); // value that must have been stored at 0x010184
		checkGPR(3, 0x8899AABB); // must stay unchanged 
		checkCC(CC1);            // must stay unchanged
	}

	@Test
	public void x40_RX_StoreHalfword_03_X2_B2zero_DISP() {
		setCC(CC3);                       // for later comparison
		setGPR(3, 0x12345678);            // register with value to be stored
		setGPR(1, 0x010100);              // set index register for addressing
		setInstructions(
				_40, _31, _0E, _84        // STH (base=0,index=R1,offset=0xE84) <- R3
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		checkMemF(0x010F80, 0, 0x56780000, 0); // value that must have been stored at 0x010F84
		checkGPR(3, 0x12345678); // must stay unchanged 
		checkCC(CC3);            // must stay unchanged
	}

	
	/*
	** Tests:  0x90 -- STM [RS] - Store Multiple
	*/

	@Test
	public void x90_RS_StoreMultiple_01_R11_R11() {
		setCC(CC2);
		setGPR(0,  0xFF000000);
		setGPR(1,  0x11111111);
		setGPR(2,  0x00010000);           // set base register for addressing
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
				_90, _BB, _2F, _84           // STM (base=R2,offset=0x184) <- R3..R10
		);
		execute(1);
		checkIL(2); 
		checkIA(CodeBase+4);
		checkMemF(0x010F80,  // values that must have been stored at 0x010F84
			0x00000000,
			0xBBBBBBBB,
			0x00000000
			); 
		checkGPR(0,  0xFF000000);   // registers must stay unchanged
		checkGPR(1,  0x11111111);
		checkGPR(2,  0x00010000);
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
		checkCC(CC2);
	}

	@Test
	public void x90_RS_StoreMultiple_02_R3_R10() {
		setCC(CC3);
		setGPR(0,  0xFF000000);
		setGPR(1,  0x11111111);
		setGPR(2,  0x00010000);           // set base register for addressing
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
				_90, _3A, _2F, _84           // STM (base=R2,offset=0x184) <- R3..R10
		);
		execute(1);
		checkIL(2); 
		checkIA(CodeBase+4);
		checkMemF(0x010F80,  // values that must have been stored at 0x010F84
			0x00000000,
			0x33333333,
			0x44444444,
			0x55555555,
			0x66666666,
			0x77777777,
			0x88888888,
			0x99999999,
			0xAAAAAAAA,
			0x00000000
			); 
		checkGPR(0,  0xFF000000);   // registers must stay unchanged
		checkGPR(1,  0x11111111);
		checkGPR(2,  0x00010000);
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

	@Test
	public void x90_RS_StoreMultiple_03_R10_R3() {
		setCC(CC3);
		setGPR(0,  0xFF000000);
		setGPR(1,  0x11111111);
		setGPR(2,  0x00010000);           // set base register for addressing
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
				_90, _A3, _2F, _84           // STM (base=R2,offset=0x184) <- R3..R10
		);
		execute(1);
		checkIL(2); 
		checkIA(CodeBase+4);
		checkMemF(0x010F80,  // values that must have been stored at 0x010F84
			0x00000000,
			0xAAAAAAAA,
			0xBBBBBBBB,
			0xCCCCCCCC,
			0xDDDDDDDD,
			0xEEEEEEEE,
			0xFFFFFFFF,
			0xFF000000,
			0x11111111,
			0x00010000,
			0x33333333,
			0x00000000
			); 
		checkGPR(0,  0xFF000000);   // registers must stay unchanged
		checkGPR(1,  0x11111111);
		checkGPR(2,  0x00010000);
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

}
