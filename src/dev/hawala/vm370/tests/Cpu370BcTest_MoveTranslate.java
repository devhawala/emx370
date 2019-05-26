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
public class Cpu370BcTest_MoveTranslate extends AbstractCpu370BcTest {
	
	public Cpu370BcTest_MoveTranslate(Class<? extends Cpu370Bc> cpuClass) {
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
	
	private final int IA_PGM_INTR_BASE = 0x007F0000;
	
	/*
	** Tests:  0x92 -- MVI [SI] - Move Character Immediate
	*/

	@Test
	public void x92_SI_MoveCharacterImmediate_77() {
		setCC(CC3);                       // for later comparison
		setMemF(0x010180,                 // fill the target area with reference data
			0x22222222,
			0x33333333,
			0x44444444,
			0x55555555
			);
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_92, _77, _21, _83,       // MVI x'77' -> (base=R2,index=0,offset=0x183)
				_92, _77, _21, _86,       // MVI x'77' -> (base=R2,index=0,offset=0x186)
				_92, _77, _21, _89,       // MVI x'77' -> (base=R2,index=0,offset=0x189)
				_92, _77, _21, _8C        // MVI x'77' -> (base=R2,index=0,offset=0x18C)
		);
		execute(4); // do 4 instructions
		checkIL(2);
		checkIA(CodeBase+16);    // instruction address at end
		checkCC(CC3);            // must be unchanged
		
		// verify the expected bytes were changed
		checkMemF(0x010180, 0x22222277);
		checkMemF(0x010184, 0x33337733);
		checkMemF(0x010188, 0x44774444);
		checkMemF(0x01018C, 0x77555555);
	}

	@Test
	public void x92_SI_MoveCharacterImmediate_AA() {
		setCC(CC3);                       // for later comparison
		setMemF(0x010180,                 // fill the target area with reference data
			0x22222222,
			0x33333333,
			0x44444444,
			0x55555555
			);
		setGPR(2, 0x010000);              // set base register for addressing
		setInstructions(
				_92, _AA, _21, _83,       // MVI x'AA' -> (base=R2,index=0,offset=0x183)
				_92, _AA, _21, _86,       // MVI x'AA' -> (base=R2,index=0,offset=0x186)
				_92, _AA, _21, _89,       // MVI x'AA' -> (base=R2,index=0,offset=0x189)
				_92, _AA, _21, _8C        // MVI x'AA' -> (base=R2,index=0,offset=0x18C)
		);
		execute(4); // do 4 instructions
		checkIL(2);
		checkIA(CodeBase+16);    // instruction address at end
		checkCC(CC3);            // must be unchanged
		
		// verify the expected bytes were changed
		checkMemF(0x010180, 0x222222AA);
		checkMemF(0x010184, 0x3333AA33);
		checkMemF(0x010188, 0x44AA4444);
		checkMemF(0x01018C, 0xAA555555);
	}
	
	/*
	** Tests:  0xD2 -- MVC [SS] - Move Characters
	*/

	@Test
	public void xD2_SS_MoveCharacters_01_len16() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180,                 // fill the target area with reference data
			0x22222222,
			0x33333333,
			0xAAAAAAAA,
			0xFEFEFEFE
			);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_D2, _0F, _20, _01, _31, _80 // MVC len=16, (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC2);            // must be unchanged

		// check the target memory region
		checkMemF(0x200000, 0x00222222);
		checkMemF(0x200004, 0x22333333);
		checkMemF(0x200008, 0x33AAAAAA);
		checkMemF(0x20000C, 0xAAFEFEFE);
		checkMemF(0x200010, 0xFE000000);
	}

	@Test
	public void xD2_SS_MoveCharacters_02_len256() {
		setCC(CC1);                       // for later comparison
		setMemF(0x010180,                 // fill the target area with reference data
			0x22222222, 0x33333333, 0x44444444, 0x55555555,
			0x12121212, 0x23232323, 0x34343434, 0x45454545,
			0xF1F1F1F1, 0xF2F2F2F2, 0xF3F3F3F3, 0xF4F4F4F4,
			0x99999999, 0x88888888, 0x77777777, 0x66666666,
			0x22222222, 0x33333333, 0x44444444, 0x55555555,
			0x12121212, 0x23232323, 0x34343434, 0x45454545,
			0xF1F1F1F1, 0xF2F2F2F2, 0xF3F3F3F3, 0xF4F4F4F4,
			0x99999999, 0x88888888, 0x77777777, 0x66666666,
			0x22222222, 0x33333333, 0x44444444, 0x55555555,
			0x12121212, 0x23232323, 0x34343434, 0x45454545,
			0xF1F1F1F1, 0xF2F2F2F2, 0xF3F3F3F3, 0xF4F4F4F4,
			0x99999999, 0x88888888, 0x77777777, 0x66666666,
			0x22222222, 0x33333333, 0x44444444, 0x55555555,
			0x12121212, 0x23232323, 0x34343434, 0x45454545,
			0xF1F1F1F1, 0xF2F2F2F2, 0xF3F3F3F3, 0xF4F4F4F4,
			0x99999999, 0x88888888, 0x77777777, 0x66666666
			);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_D2, _FF, _20, _01, _31, _80 // MVC len=256, (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC1);            // must be unchanged

		// check the target memory region
		checkMemF(0x200000, 0x00222222);
		checkMemF(0x200004, 0x22333333);
		checkMemF(0x200008, 0x33444444);
		checkMemF(0x20000C, 0x44555555);
		checkMemF(0x200010, 0x55121212);
		checkMemF(0x200014, 0x12232323);
		checkMemF(0x200018, 0x23343434);
		checkMemF(0x20001C, 0x34454545);
		checkMemF(0x200020, 0x45F1F1F1);
		checkMemF(0x200024, 0xF1F2F2F2);
		checkMemF(0x200028, 0xF2F3F3F3);
		checkMemF(0x20002C, 0xF3F4F4F4);
		checkMemF(0x200030, 0xF4999999);
		checkMemF(0x200034, 0x99888888);
		checkMemF(0x200038, 0x88777777);
		checkMemF(0x20003C, 0x77666666);
		checkMemF(0x200040, 0x66222222);
		checkMemF(0x200044, 0x22333333);
		checkMemF(0x200048, 0x33444444);
		checkMemF(0x20004C, 0x44555555);
		checkMemF(0x200050, 0x55121212);
		checkMemF(0x200054, 0x12232323);
		checkMemF(0x200058, 0x23343434);
		checkMemF(0x20005C, 0x34454545);
		checkMemF(0x200060, 0x45F1F1F1);
		checkMemF(0x200064, 0xF1F2F2F2);
		checkMemF(0x200068, 0xF2F3F3F3);
		checkMemF(0x20006C, 0xF3F4F4F4);
		checkMemF(0x200070, 0xF4999999);
		checkMemF(0x200074, 0x99888888);
		checkMemF(0x200078, 0x88777777);
		checkMemF(0x20007C, 0x77666666);
		checkMemF(0x200080, 0x66222222);
		checkMemF(0x200084, 0x22333333);
		checkMemF(0x200088, 0x33444444);
		checkMemF(0x20008C, 0x44555555);
		checkMemF(0x200090, 0x55121212);
		checkMemF(0x200094, 0x12232323);
		checkMemF(0x200098, 0x23343434);
		checkMemF(0x20009C, 0x34454545);
		checkMemF(0x2000A0, 0x45F1F1F1);
		checkMemF(0x2000A4, 0xF1F2F2F2);
		checkMemF(0x2000A8, 0xF2F3F3F3);
		checkMemF(0x2000AC, 0xF3F4F4F4);
		checkMemF(0x2000B0, 0xF4999999);
		checkMemF(0x2000B4, 0x99888888);
		checkMemF(0x2000B8, 0x88777777);
		checkMemF(0x2000BC, 0x77666666);
		checkMemF(0x2000C0, 0x66222222);
		checkMemF(0x2000C4, 0x22333333);
		checkMemF(0x2000C8, 0x33444444);
		checkMemF(0x2000CC, 0x44555555);
		checkMemF(0x2000D0, 0x55121212);
		checkMemF(0x2000D4, 0x12232323);
		checkMemF(0x2000D8, 0x23343434);
		checkMemF(0x2000DC, 0x34454545);
		checkMemF(0x2000E0, 0x45F1F1F1);
		checkMemF(0x2000E4, 0xF1F2F2F2);
		checkMemF(0x2000E8, 0xF2F3F3F3);
		checkMemF(0x2000EC, 0xF3F4F4F4);
		checkMemF(0x2000F0, 0xF4999999);
		checkMemF(0x2000F4, 0x99888888);
		checkMemF(0x2000F8, 0x88777777);
		checkMemF(0x2000FC, 0x77666666);
		checkMemF(0x200100, 0x66000000);
	}

	@Test
	public void xD2_SS_MoveCharacters_03_fill65a() {
		setCC(CC0);                       // for later comparison
		setMemB(0x010180, _71);           // seed for filling
		setGPR(2, 0x010000);              // set base register for op1&op2 addressing (source&target)
		setInstructions(
				_D2, _41, _21, _81, _21, _80 // MVC len=42, (base=R2,offset=0x181) <- (base=R2,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC0);            // must be unchanged
		
		// 0x010180                      ::  initially _71
		// 0x010181 -[len=_42]-> 0x018C2 :: filled with content of 0x010180
		checkMemB(0x1017F, _00); // before
		checkMemB(0x10180, _71); // initialized
		for (int i = 0x10181; i <= 0x101C2; i++) {
			checkMemB(i, _71);   // filled by MVC
		}
		checkMemB(0x101C3, _00); // after
	}

	@Test
	public void xD2_SS_MoveCharacters_03_fill6b() {
		setCC(CC0);                       // for later comparison
		setMemB(0x010180, _91);           // seed for filling
		setGPR(2, 0x010000);              // set base register for op1&op2 addressing (source&target)
		setInstructions(
				_D2, _41, _21, _81, _21, _80 // MVC len=42, (base=R2,offset=0x181) <- (base=R2,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC0);            // must be unchanged
		
		// 0x010180                      ::  initially _71
		// 0x010181 -[len=_42]-> 0x018C2 :: filled with content of 0x010180
		checkMemB(0x1017F, _00); // before
		checkMemB(0x10180, _91); // initialized
		for (int i = 0x10181; i <= 0x101C2; i++) {
			checkMemB(i, _91);   // filled by MVC
		}
		checkMemB(0x101C3, _00); // after
	}
	
	/*
	** Tests:  0xD1 -- MVN [SS] - Move Numerics
	*/

	@Test
	public void xD1_SS_01_MoveNumerics_16() {
		setCC(CC1);                       // for later comparison
		setMemF(0x010180,                 // source numerics
			0x72737475,
			0xA6A7A8A9,
			0x1A1B1C1D,
			0x9E9F9091
			);
		setMemF(0x200000,                 // target zone
				0xAAAAAAAA,
				0xBBBBBBBB,
				0xFFFFFFFF,
				0x77777777,
				0x88888888
				);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_D1, _0F, _20, _01, _31, _80 // MVC len=16, (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC1);            // must be unchanged
		
		// verify the expected changes were done in the target zone
		checkMemF(0x200000, 0xAAA2A3A4);
		checkMemF(0x200004, 0xB5B6B7B8);
		checkMemF(0x200008, 0xF9FAFBFC);
		checkMemF(0x20000C, 0x7D7E7F70);
		checkMemF(0x200010, 0x81888888);
	}

	@Test
	public void xD1_SS_02_MoveNumerics_256() {
		setCC(CC3);                       // for later comparison
		setMemF(0x010180,                 // source numerics (for start target zone)
			0x72737475,
			0xA6A7A8A9,
			0x1A1B1C1D,
			0x9E9F9091
			);
		setMemF(0x200000,                 // target zone start
				0xAAAAAAAA,
				0xBBBBBBBB,
				0xFFFFFFFF,
				0x77777777,
				0x88888888
				);
		setMemF(0x2000FC,                 // target zone end
				0xFFFFFFFF,
				0xFFFFFFFF);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_D1, _FF, _20, _01, _31, _80 // MVC len=256, (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);    // instruction address at end
		checkCC(CC3);            // must be unchanged
		
		// verify the expected changes were done in the target zone
		checkMemF(0x200000, 0xAAA2A3A4); // target zone start
		checkMemF(0x200004, 0xB5B6B7B8);
		checkMemF(0x200008, 0xF9FAFBFC);
		checkMemF(0x20000C, 0x7D7E7F70);
		checkMemF(0x200010, 0x81808080);

		checkMemF(0x2000FC, 0xF0F0F0F0); // target zone end
		checkMemF(0x200100, 0xF0FFFFFF);
	}
	
	/*
	** Tests:  0xF1 -- MVO [SS] - Move With Offset
	*/

	@Test
	public void xF1_SI_01_MoveWithOffset_s16_t16() {
		setCC(CC1);                       // for later comparison
		setMemF(0x010180,                 // source area
			0x72737475,
			0xA6A7A8A9,
			0x1A1B1C1D,
			0x9E9F9091
			);
		setMemF(0x200000,                 // target area
				0xAAAAAAAA,
				0xBBBBBBBB,
				0xFFFFFFFF,
				0x77777777,
				0x88888888
				);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_F1, _FF, _20, _01, _31, _80 // MVO (len=16,base=R2,offset=0x001) <- (len=16,base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC1);            // must be unchanged
		
		// verify the expected changes were done in the target zone
		checkMemF(0x200000, 0xAA273747);
		checkMemF(0x200004, 0x5A6A7A8A);
		checkMemF(0x200008, 0x91A1B1C1);
		checkMemF(0x20000C, 0xD9E9F909);
		checkMemF(0x200010, 0x18888888);
	}

	@Test
	public void xF1_SI_02_MoveWithOffset_s15_t16() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180,                 // source area
			0x72737475,
			0xA6A7A8A9,
			0x1A1B1C1D,
			0x9E9F9091
			);
		setMemF(0x200000,                 // target area
				0xAAAAAAAA,
				0xBBBBBBBB,
				0xFFFFFFFF,
				0x77777777,
				0x88888888
				);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_F1, _FE, _20, _01, _31, _81 // MVO (len=16,base=R2,offset=0x001) <- (len=15,base=R3,offset=0x181)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC2);            // must be unchanged
		
		// verify the expected changes were done in the target zone
		checkMemF(0x200000, 0xAA073747);
		checkMemF(0x200004, 0x5A6A7A8A);
		checkMemF(0x200008, 0x91A1B1C1);
		checkMemF(0x20000C, 0xD9E9F909);
		checkMemF(0x200010, 0x18888888);
	}

	@Test
	public void xF1_SI_03_MoveWithOffset_s14_t16() {
		setCC(CC3);                       // for later comparison
		setMemF(0x010180,                 // source area
			0x72737475,
			0xA6A7A8A9,
			0x1A1B1C1D,
			0x9E9F9091
			);
		setMemF(0x200000,                 // target area
				0xAAAAAAAA,
				0xBBBBBBBB,
				0xFFFFFFFF,
				0x77777777,
				0x88888888
				);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_F1, _FD, _20, _01, _31, _82 // MVO (len=16,base=R2,offset=0x001) <- (len=14,base=R3,offset=0x182)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC3);            // must be unchanged
		
		// verify the expected changes were done in the target zone
		checkMemF(0x200000, 0xAA000747);
		checkMemF(0x200004, 0x5A6A7A8A);
		checkMemF(0x200008, 0x91A1B1C1);
		checkMemF(0x20000C, 0xD9E9F909);
		checkMemF(0x200010, 0x18888888);
	}

	@Test
	public void xF1_SI_04_MoveWithOffset_s15_t13() {
		setCC(CC0);                       // for later comparison
		setMemF(0x010180,                 // source area
			0x72737475,
			0xA6A7A8A9,
			0x1A1B1C1D,
			0x9E9F9091
			);
		setMemF(0x200000,                 // target area
				0xAAAAAAAA,
				0xBBBBBBBB,
				0xFFFFFFFF,
				0x77777777,
				0x88888888
				);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_F1, _CE, _20, _04, _31, _81 // MVO (len=13,base=R2,offset=0x001) <- (len=15,base=R3,offset=0x181)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC0);            // must be unchanged
		
		// verify the expected changes were done in the target zone
		checkMemF(0x200000, 0xAAAAAAAA);
		checkMemF(0x200004, 0x5A6A7A8A);
		checkMemF(0x200008, 0x91A1B1C1);
		checkMemF(0x20000C, 0xD9E9F909);
		checkMemF(0x200010, 0x18888888);
	}
	
	/*
	** Tests:  0xD3 -- MVZ [SS] - Move Zones
	*/

	@Test
	public void xD3_SS_01_MoveZones_16() {
		setCC(CC1);                       // for later comparison
		setMemF(0x010180,                 // source numerics
			0x72737475,
			0xA6A7A8A9,
			0x1A1B1C1D,
			0x9E9F9091
			);
		setMemF(0x200000,                 // target zone
				0xAAAAAAAA,
				0xBBBBBBBB,
				0xFFFFFFFF,
				0x77777777,
				0x88888888
				);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_D3, _0F, _20, _01, _31, _80 // MVZ len=16, (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC1);            // must be unchanged
		
		// verify the expected changes were done in the target zone
		checkMemF(0x200000, 0xAA7A7A7A);
		checkMemF(0x200004, 0x7BABABAB);
		checkMemF(0x200008, 0xAF1F1F1F);
		checkMemF(0x20000C, 0x17979797);
		checkMemF(0x200010, 0x98888888);
	}

	@Test
	public void xD3_SS_02_MoveZones_256() {
		setCC(CC3);                       // for later comparison
		setMemF(0x010180,                 // source numerics (for start target zone)
			0x72737475,
			0xA6A7A8A9,
			0x1A1B1C1D,
			0x9E9F9091
			);
		setMemF(0x200000,                 // target zone start
				0xAAAAAAAA,
				0xBBBBBBBB,
				0xFFFFFFFF,
				0x77777777,
				0x88888888
				);
		setMemF(0x2000FC,                 // target zone end
				0xFFFFFFFF,
				0xFFFFFFFF);
		setGPR(2, 0x200000);              // set base register for op1 addressing (target)
		setGPR(3, 0x010000);              // set base register for op2 addressing (source)
		setInstructions(
				_D3, _FF, _20, _01, _31, _80 // MVZ len=256, (base=R2,offset=0x001) <- (base=R3,offset=0x180)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);    // instruction address at end
		checkCC(CC3);            // must be unchanged
		
		// verify the expected changes were done in the target zone
		checkMemF(0x200000, 0xAA7A7A7A); // target tone start
		checkMemF(0x200004, 0x7BABABAB);
		checkMemF(0x200008, 0xAF1F1F1F);
		checkMemF(0x20000C, 0x17979797);
		checkMemF(0x200010, 0x98080808);

		checkMemF(0x2000FC, 0x0F0F0F0F); // target zone end
		checkMemF(0x200100, 0x0FFFFFFF);
	}
	
	/*
	** Tests:  0xDC -- TR [SS] - Translate
	*/

	@Test
	public void xDC_SS_01_Translate_16() {
		setCC(CC1);                       // for later comparison
		setMemF(0x010180,                 // op1 = source and target
			0x72737475,
			0xA6A7A8A9,
			0x1A1B1C1D,
			0x9E9F9091
			);
		setMemF(0x200000,                  // translation table
				0x10111213, 0x14151617, 0x18191A1B, 0x1C1D1E1F,
				0x20212223, 0x24252627, 0x28292A2B, 0x2C2D2E2F,
				0x30313233, 0x34353637, 0x38393A3B, 0x3C3D3E3F,
				0x40414243, 0x44454647, 0x48494A4B, 0x4C4D4E4F,
				0x50515253, 0x54555657, 0x58595A5B, 0x5C5D5E5F,
				0x60616263, 0x64656667, 0x68696A6B, 0x6C6D6E6F,
				0x70717273, 0x74757677, 0x78797A7B, 0x7C7D7E7F,
				0x80818283, 0x84858687, 0x88898A8B, 0x8C8D8E8F,
				0x90919293, 0x94959697, 0x98999A9B, 0x9C9D9E9F,
				0xA0A1A2A3, 0xA4A5A6A7, 0xA8A9AAAB, 0xACADAEAF,
				0xB0B1B2B3, 0xB4B5B6B7, 0xB8B9BABB, 0xBCBDBEBF,
				0xC0C1C2C3, 0xC4C5C6C7, 0xC8C9CACB, 0xCCCDCECF,
				0xD0D1D2D3, 0xD4D5D6D7, 0xD8D9DADB, 0xDCDDDEDF,
				0xE0E1E2E3, 0xE4E5E6E7, 0xE8E9EAEB, 0xECEDEEEF,
				0xF0F1F2F3, 0xF4F5F6F7, 0xF8F9FAFB, 0xFCFDFEFF,
				0x00010203, 0x04050607, 0x08090A0B, 0x0C0D0E0F
				);
		setGPR(2, 0x200000);              // set base register for op2 addressing (table)
		setGPR(3, 0x010000);              // set base register for op1 addressing (source)
		setInstructions(
				_DC, _0F, _31, _80, _20, _00 // TR len=16, (base=R3,offset=0x180) with table (base=R2,offset=0x000)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC1);            // must be unchanged
		
		// verify the expected changes were done in the target zone
		checkMemF(0x010180, 0x82838485);
		checkMemF(0x010184, 0xB6B7B8B9);
		checkMemF(0x010188, 0x2A2B2C2D);
		checkMemF(0x01018C, 0xAEAFA0A1);
	}

	@Test
	public void xDC_SS_02_Translate_256() {
		setCC(CC2);                       // for later comparison
		setMemF(0x010180,                 // op1 = source and target
				0x30313233, 0x34353637, 0x38393A3B, 0x3C3D3E3F,
				0x40414243, 0x44454647, 0x48494A4B, 0x4C4D4E4F,
				0x50515253, 0x54555657, 0x58595A5B, 0x5C5D5E5F,
				0x60616263, 0x64656667, 0x68696A6B, 0x6C6D6E6F,
				0x70717273, 0x74757677, 0x78797A7B, 0x7C7D7E7F,
				0x80818283, 0x84858687, 0x88898A8B, 0x8C8D8E8F,
				0x90919293, 0x94959697, 0x98999A9B, 0x9C9D9E9F,
				0xA0A1A2A3, 0xA4A5A6A7, 0xA8A9AAAB, 0xACADAEAF,
				0xB0B1B2B3, 0xB4B5B6B7, 0xB8B9BABB, 0xBCBDBEBF,
				0xC0C1C2C3, 0xC4C5C6C7, 0xC8C9CACB, 0xCCCDCECF,
				0xD0D1D2D3, 0xD4D5D6D7, 0xD8D9DADB, 0xDCDDDEDF,
				0xE0E1E2E3, 0xE4E5E6E7, 0xE8E9EAEB, 0xECEDEEEF,
				0xF0F1F2F3, 0xF4F5F6F7, 0xF8F9FAFB, 0xFCFDFEFF,
				0x00010203, 0x04050607, 0x08090A0B, 0x0C0D0E0F,
				0x10111213, 0x14151617, 0x18191A1B, 0x1C1D1E1F,
				0x20212223, 0x24252627, 0x28292A2B, 0x2C2D2E2F
				);
		setMemF(0x200000,                  // translation table
				0x10111213, 0x14151617, 0x18191A1B, 0x1C1D1E1F,
				0x20212223, 0x24252627, 0x28292A2B, 0x2C2D2E2F,
				0x30313233, 0x34353637, 0x38393A3B, 0x3C3D3E3F,
				0x40414243, 0x44454647, 0x48494A4B, 0x4C4D4E4F,
				0x50515253, 0x54555657, 0x58595A5B, 0x5C5D5E5F,
				0x60616263, 0x64656667, 0x68696A6B, 0x6C6D6E6F,
				0x70717273, 0x74757677, 0x78797A7B, 0x7C7D7E7F,
				0x80818283, 0x84858687, 0x88898A8B, 0x8C8D8E8F,
				0x90919293, 0x94959697, 0x98999A9B, 0x9C9D9E9F,
				0xA0A1A2A3, 0xA4A5A6A7, 0xA8A9AAAB, 0xACADAEAF,
				0xB0B1B2B3, 0xB4B5B6B7, 0xB8B9BABB, 0xBCBDBEBF,
				0xC0C1C2C3, 0xC4C5C6C7, 0xC8C9CACB, 0xCCCDCECF,
				0xD0D1D2D3, 0xD4D5D6D7, 0xD8D9DADB, 0xDCDDDEDF,
				0xE0E1E2E3, 0xE4E5E6E7, 0xE8E9EAEB, 0xECEDEEEF,
				0xF0F1F2F3, 0xF4F5F6F7, 0xF8F9FAFB, 0xFCFDFEFF,
				0x00010203, 0x04050607, 0x08090A0B, 0x0C0D0E0F
				);
		setGPR(2, 0x200000);              // set base register for op2 addressing (table)
		setGPR(3, 0x010000);              // set base register for op1 addressing (source)
		setInstructions(
				_DC, _FF, _31, _80, _20, _00 // TR len=16, (base=R3,offset=0x180) with table (base=R2,offset=0x000)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		checkCC(CC2);            // must be unchanged
		
		// verify the expected changes were done in the target zone
		setMemF(0x01017C,
				0x00000000, 0x00000000, 0x00000000, 0x00000000,
				0x40414243, 0x44454647, 0x48494A4B, 0x4C4D4E4F,
				0x50515253, 0x54555657, 0x58595A5B, 0x5C5D5E5F,
				0x60616263, 0x64656667, 0x68696A6B, 0x6C6D6E6F,
				0x70717273, 0x74757677, 0x78797A7B, 0x7C7D7E7F,
				0x80818283, 0x84858687, 0x88898A8B, 0x8C8D8E8F,
				0x90919293, 0x94959697, 0x98999A9B, 0x9C9D9E9F,
				0xA0A1A2A3, 0xA4A5A6A7, 0xA8A9AAAB, 0xACADAEAF,
				0xB0B1B2B3, 0xB4B5B6B7, 0xB8B9BABB, 0xBCBDBEBF,
				0xC0C1C2C3, 0xC4C5C6C7, 0xC8C9CACB, 0xCCCDCECF,
				0xD0D1D2D3, 0xD4D5D6D7, 0xD8D9DADB, 0xDCDDDEDF,
				0xE0E1E2E3, 0xE4E5E6E7, 0xE8E9EAEB, 0xECEDEEEF,
				0xF0F1F2F3, 0xF4F5F6F7, 0xF8F9FAFB, 0xFCFDFEFF,
				0x00010203, 0x04050607, 0x08090A0B, 0x0C0D0E0F,
				0x10111213, 0x14151617, 0x18191A1B, 0x1C1D1E1F,
				0x20212223, 0x24252627, 0x28292A2B, 0x2C2D2E2F,
				0x30313233, 0x34353637, 0x38393A3B, 0x3C3D3E3F,
				0x00000000, 0x00000000, 0x00000000, 0x00000000
		);
	}
	
	/*
	** Tests:  0xDD -- TRT [SS] - Translate And Test
	*/

	@Test
	public void xDD_SS_01_TranslateAndTest_CC0() {
		setCC(CC3);                       // for later comparison
		setMemF(0x010180,                 // op1 = source
			0x72737475,
			0xA6A7A8A9,
			0x1A1B1C1D,
			0x9E9F9091
			);
		// begin function table at 0x200000
		setMemB(0x200081, _33);
		// end function table
		setGPR(1, 0xAABBCCDD);
		setGPR(2, 0xFFEEDDCC);
		setGPR(4, 0x200000);              // set base register for op2 addressing (table)
		setGPR(5, 0x010000);              // set base register for op1 addressing (source)
		setInstructions(
				_DD, _0F, _51, _80, _40, _00 // TRT len=16, (base=R5,offset=0x180) with table (base=R4,offset=0x000)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		
		// check results
		checkCC(CC0);            // all function bytes are zero
		checkGPR(1, 0xAABBCCDD);
		checkGPR(2, 0xFFEEDDCC);
	}

	@Test
	public void xDD_SS_02_TranslateAndTest_CC1a() {
		setCC(CC3);                       // for later comparison
		setMemF(0x010180,                 // op1 = source
			0x72737475,
			0xA6A7A8A9,
			0x1A1B1C1D,
			0x9E9F9091
			);
		// begin function table at 0x200000
		setMemB(0x200081, _33);
		setMemB(0x20001B, _44);
		// end function table
		setGPR(1, 0xAABBCCDD);
		setGPR(2, 0xFFEEDDCC);
		setGPR(4, 0x200000);              // set base register for op2 addressing (table)
		setGPR(5, 0x010000);              // set base register for op1 addressing (source)
		setInstructions(
				_DD, _0F, _51, _80, _40, _00 // TRT len=16, (base=R5,offset=0x180) with table (base=R4,offset=0x000)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		
		// check results
		checkCC(CC1);            // non-zero function byte before first operand is exhausted
		checkGPR(1, 0xAA010189);
		checkGPR(2, 0xFFEEDD44);
	}

	@Test
	public void xDD_SS_02_TranslateAndTest_CC1b() {
		setCC(CC3);                       // for later comparison
		setMemF(0x010180,                 // op1 = source
			0x72737475,
			0xA6A7A8A9,
			0x1ACB1C1D,
			0x9E9F9091
			);
		// begin function table at 0x200000
		setMemB(0x200081, _33);
		setMemB(0x2000CB, _44);
		// end function table
		setGPR(1, 0xAABBCCDD);
		setGPR(2, 0xFFEEDDCC);
		setGPR(4, 0x200000);              // set base register for op2 addressing (table)
		setGPR(5, 0x010000);              // set base register for op1 addressing (source)
		setInstructions(
				_DD, _0F, _51, _80, _40, _00 // TRT len=16, (base=R5,offset=0x180) with table (base=R4,offset=0x000)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		
		// check results
		checkCC(CC1);            // non-zero function byte before first operand is exhausted
		checkGPR(1, 0xAA010189);
		checkGPR(2, 0xFFEEDD44);
	}

	@Test
	public void xDD_SS_02_TranslateAndTest_CC1c() {
		setCC(CC3);                       // for later comparison
		setMemF(0x010180,                 // op1 = source
			0x72737475,
			0xA6A7A8A9,
			0x1ACB1C1D,
			0x9E9F9091
			);
		// begin function table at 0x200000
		setMemB(0x200081, _33);
		setMemB(0x2000CB, _F4);
		// end function table
		setGPR(1, 0xAABBCCDD);
		setGPR(2, 0xFFEEDDCC);
		setGPR(4, 0x200000);              // set base register for op2 addressing (table)
		setGPR(5, 0x010000);              // set base register for op1 addressing (source)
		setInstructions(
				_DD, _0F, _51, _80, _40, _00 // TRT len=16, (base=R5,offset=0x180) with table (base=R4,offset=0x000)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		
		// check results
		checkCC(CC1);            // non-zero function byte before first operand is exhausted
		checkGPR(1, 0xAA010189);
		checkGPR(2, 0xFFEEDDF4);
	}

	@Test
	public void xDD_SS_03_TranslateAndTest_CC2() {
		setCC(CC3);                       // for later comparison
		setMemF(0x010180,                 // op1 = source
			0x72737475,
			0xA6A7A8A9,
			0x1A1B1C1D,
			0x9E9F9091
			);
		// begin function table at 0x200000
		setMemB(0x200081, _33);
		setMemB(0x200091, _77);
		// end function table
		setGPR(1, 0xAABBCCDD);
		setGPR(2, 0xFFEEDDCC);
		setGPR(4, 0x200000);              // set base register for op2 addressing (table)
		setGPR(5, 0x010000);              // set base register for op1 addressing (source)
		setInstructions(
				_DD, _0F, _51, _80, _40, _00 // TRT len=16, (base=R5,offset=0x180) with table (base=R4,offset=0x000)
		);
		execute(1); // do one instructions
		checkIL(3);
		checkIA(CodeBase+6);     // instruction address at end
		
		// check results
		checkCC(CC2);            // the last function bytes is non-zero
		checkGPR(1, 0xAA01018F);
		checkGPR(2, 0xFFEEDD77);
	}
	
	/*
	** Tests:  0x0E -- MVCL [RR] - Move Character Long
	** 
	** Test combinations for good cases:
	** -> target (T) and source (S) zones:
	**    -> nonOverlap
	**    -> overlap (effectively: start(T) < start(S), as not destructive = good case)
	**    -> same (start(T) = start(S))
	** -> position relative to 16m boundary:
	**    -> A :: both zones below
	**    -> B :: source zone wraps at 16m
	**    -> C :: if non-overlap : T below, S above (effectively: S below T)
	**            if overlap     : 16 between S-start and T-end
	**    -> D:: target zone wraps at 16m, source above 16m (effectively: S below T)
	** -> length of zones:
	**    -> TgtS :: len(T) > len(S)
	**    -> TeqS :: len(T) = len(S)
	**    -> TltS :: len(T) < len(S)
	*/
	
	private final int AddrMask = 0x00FFFFFF; 
	
	private void innerTestMvclGood(int toAddr, int toLen, int fromAddr, int fromLen) {
		int srcLen = Math.min(fromLen, toLen);
		int fillAddr = toAddr + srcLen;
		int fillLen = (srcLen < toLen) ? toLen - srcLen : 0;
		int expectedCC = (toLen == fromLen) ? 0 : (toLen < fromLen) ? 1 : 2;
		
		// prepare the target and source areas
		byte fillChar = _FF;
		for (int i = 0; i < toLen; i++) {
			setMemB((toAddr + i) & AddrMask, _DD);
		}
		for (int i = 0; i < fromLen; i++) {
			setMemB((fromAddr + i) & AddrMask, _AA);
		}
		
		// setup general registers
		setGPR(4, toAddr);
		setGPR(5, toLen);
		setGPR(6, fromAddr);
		setGPR(7, fromLen | (fillChar << 24));
		
		// do the MVCL
		setInstructions(
				_0E, _46         // MVCL R4,R6
		);
		execute(1); // do one instructions
		checkIL(1);
		checkIA(CodeBase+2);     // instruction address at end
		checkCC((byte)expectedCC);
		
		// check the target area
		for (int i = 0; i < srcLen; i++) {
			checkMemB((toAddr + i) & AddrMask, _AA);
		}
		for (int i = 0; i < fillLen; i++) {
			checkMemB((fillAddr + i) & AddrMask, fillChar);
		}
	}

	@Test
	public void x0E_RR_01_MVCL_nonOverlap_wrapA_TltS() {
		innerTestMvclGood(0x510000, 122, 0x620000, 233); 
	}

	@Test
	public void x0E_RR_02_MVCL_nonOverlap_wrapA_TeqS() {
		innerTestMvclGood(0x510000, 233, 0x620000, 233); 
	}

	@Test
	public void x0E_RR_03_MVCL_nonOverlap_wrapA_TgtS() {
		innerTestMvclGood(0x510000, 266, 0x620000, 233); 
	}

	@Test
	public void x0E_RR_04_MVCL_nonOverlap_wrapB_TltS() {
		innerTestMvclGood(0x510000, 122, 0xFFFF80, 233); // S at 16m - 128 bytes
	}

	@Test
	public void x0E_RR_05_MVCL_nonOverlap_wrapB_TeqS() {
		innerTestMvclGood(0x510000, 233, 0xFFFF80, 233); // S at 16m - 128 bytes
	}

	@Test
	public void x0E_RR_06_MVCL_nonOverlap_wrapB_TgtS() {
		innerTestMvclGood(0x510000, 266, 0xFFFF80, 233); // S at 16m - 128 bytes
	}

	@Test
	public void x0E_RR_07_MVCL_nonOverlap_wrapC_TltS() {
		innerTestMvclGood(0x510000, 122, 0x01001000, 233); // S at 16m + 4096 bytes => effectively at 0x001000
	}

	@Test
	public void x0E_RR_08_MVCL_nonOverlap_wrapC_TeqS() {
		innerTestMvclGood(0x510000, 233, 0x01001000, 233); // S at 16m + 4096 bytes => effectively at 0x001000
	}

	@Test
	public void x0E_RR_09_MVCL_nonOverlap_wrapC_TgtS() {
		innerTestMvclGood(0x510000, 266, 0x01001000, 233); // S at 16m + 4096 bytes => effectively at 0x001000
	}

	@Test
	public void x0E_RR_10_MVCL_nonOverlap_wrapD_TltS() {
		innerTestMvclGood(0xFFFF80, 122, 0x01001000, 233); // T at 16m - 64 ; S at effectively at 0x001000
	}

	@Test
	public void x0E_RR_11_MVCL_nonOverlap_wrapD_TeqS() {
		innerTestMvclGood(0xFFFF80, 233, 0x01001000, 233); // T at 16m - 64 ; S at effectively at 0x001000
	}

	@Test
	public void x0E_RR_12_MVCL_nonOverlap_wrapD_TgtS() {
		innerTestMvclGood(0xFFFF80, 266, 0x01001000, 233); // T at 16m - 64 ; S at effectively at 0x001000
	}

	@Test
	public void x0E_RR_21_MVCL_overlap_wrapA_TltS() {
		int base = 0x00510000;
		innerTestMvclGood(base, 0x00010000 - 233, base + 0x00008000, 0x00010000); 
	}

	@Test
	public void x0E_RR_22_MVCL_overlap_wrapA_TeqS() {
		int base = 0x00510000;
		innerTestMvclGood(base, 0x00010000, base + 0x00008000, 0x00010000); 
	}

	@Test
	public void x0E_RR_23_MVCL_verlap_wrapA_TgtS() {
		int base = 0x00510000;
		innerTestMvclGood(base, 0x00010000 + 233, base + 0x00008000, 0x00010000); 
	}

	@Test
	public void x0E_RR_24_MVCL_overlap_wrapB_TltS() {
		int base = 0x01000000 - 72500;
		innerTestMvclGood(base, 0x00010000 - 233, base + 0x00008000, 0x00010000); 
	}

	@Test
	public void x0E_RR_25_MVCL_overlap_wrapB_TeqS() {
		int base = 0x01000000 - 72500;
		innerTestMvclGood(base, 0x00010000, base + 0x00008000, 0x00010000); 
	}

	@Test
	public void x0E_RR_26_MVCL_verlap_wrapB_TgtS() {
		int base = 0x01000000 - 72500;
		innerTestMvclGood(base, 0x00010000 + 233, base + 0x00008000, 0x00010000); 
	}

	@Test
	public void x0E_RR_27_MVCL_overlap_wrapC_TltS() {
		int base = 0x01000000 - 48000;
		innerTestMvclGood(base, 0x00010000 - 233, base + 0x00008000, 0x00010000); 
	}

	@Test
	public void x0E_RR_28_MVCL_overlap_wrapC_TeqS() {
		int base = 0x01000000 - 48000;
		innerTestMvclGood(base, 0x00010000, base + 0x00008000, 0x00010000); 
	}

	@Test
	public void x0E_RR_29_MVCL_verlap_wrapC_TgtS() {
		int base = 0x01000000 - 48000;
		innerTestMvclGood(base, 0x00010000 + 233, base + 0x00008000, 0x00010000); 
	}

	@Test
	public void x0E_RR_30_MVCL_overlap_wrapD_TltS() {
		int base = 0x01000000 - 4096;
		innerTestMvclGood(base, 0x00010000 - 233, base + 0x00008000, 0x00010000); 
	}

	@Test
	public void x0E_RR_31_MVCL_overlap_wrapD_TeqS() {
		int base = 0x01000000 - 4096;
		innerTestMvclGood(base, 0x00010000, base + 0x00008000, 0x00010000); 
	}

	@Test
	public void x0E_RR_32_MVCL_overlap_wrapD_TgtS() {
		int base = 0x01000000 - 4096;
		innerTestMvclGood(base, 0x00010000 + 233, base + 0x00008000, 0x00010000); 
	}

	@Test
	public void x0E_RR_41_MVCL_sameStart_wrapA_TltS() {
		int base = 0x00510000;
		innerTestMvclGood(base, 0x00010000 - 233, base, 0x00010000); 
	}

	@Test
	public void x0E_RR_42_MVCL_sameStart_wrapA_TeqS() {
		int base = 0x00510000;
		innerTestMvclGood(base, 0x00010000, base, 0x00010000); 
	}

	@Test
	public void x0E_RR_43_MVCL_sameStart_wrapA_TgtS() {
		int base = 0x00510000;
		innerTestMvclGood(base, 0x00010000 + 233, base, 0x00010000); 
	}

	@Test
	public void x0E_RR_44_MVCL_sameStart_wrapB_TltS() {
		int base = 0x01000000 - 4096;
		innerTestMvclGood(base, 0x00010000 - 233, base, 0x00010000); 
	}

	@Test
	public void x0E_RR_45_MVCL_sameStart_wrapB_TeqS() {
		int base = 0x01000000 - 4096;
		innerTestMvclGood(base, 0x00010000, base, 0x00010000); 
	}

	@Test
	public void x0E_RR_46_MVCL_sameStart_wrapB_TgtS() {
		int base = 0x01000000 - 4096;
		innerTestMvclGood(base, 0x00010000 + 233, base, 0x00010000); 
	}

	@Test
	public void x0E_RR_51_MVCL_Slen0_wrapA() {
		int base = 0x00510000;
		innerTestMvclGood(base, 0x00010000 + 233, base + 0x00020000, 0); 
	}

	@Test
	public void x0E_RR_52_MVCL_Slen0_wrapB() {
		int base = 0x01000000 - 4096;
		innerTestMvclGood(base, 0x00010000 - 233, base - 0x00020000, 0);
	}
	
	//
	// destructiveOverlapA:
	//   T:         +-----------+   (32k..96k)
	//   S:  +-----------+          (0k..64k)
	//
	// destructiveOverlapB:
	//   T:     +--------+          (8k..32k)
	//   S:  +------------------+   (0k..64k)
	// 
	private void innerTestMvclDestructiveOverlap(int toAddr, int toLen, int fromAddr, int fromLen) {
		byte fillChar = _FF;
		
		// setup general registers
		setGPR(4, toAddr);
		setGPR(5, toLen);
		setGPR(6, fromAddr);
		setGPR(7, fromLen | (fillChar << 24));
		
		// do the MVCL
		setInstructions(
				_0E, _46         // MVCL R4,R6
		);
		execute(1); // do one instructions
		checkIL(1);
		checkIA(CodeBase+2);     // instruction address at end
		checkCC((byte)3);
	}

	@Test
	public void x0E_RR_60_MVCL_destructiveOverlapA_wrapA() {
		int base = 0x01000000 - 128000;
		innerTestMvclDestructiveOverlap(base + 0x00008000, 0x00010000, base, 0x00010000);
	}

	@Test
	public void x0E_RR_61_MVCL_destructiveOverlapA_wrapB() {
		int base = 0x01000000 - 72000;
		innerTestMvclDestructiveOverlap(base + 0x00008000, 0x00010000, base, 0x00010000);
	}

	@Test
	public void x0E_RR_62_MVCL_destructiveOverlapA_wrapC() {
		int base = 0x01000000 - 48000;
		innerTestMvclDestructiveOverlap(base + 0x00008000, 0x00010000, base, 0x00010000);
	}

	@Test
	public void x0E_RR_63_MVCL_destructiveOverlapA_wrapD() {
		int base = 0x01000000 - 16000;
		innerTestMvclDestructiveOverlap(base + 0x00008000, 0x00010000, base, 0x00010000);
	}

	@Test
	public void x0E_RR_64_MVCL_destructiveOverlapB_wrapA() {
		int base = 0x01000000 - 128000;
		innerTestMvclDestructiveOverlap(base + 0x00002000, 0x0004000, base, 0x00010000);
	}

	@Test
	public void x0E_RR_65_MVCL_destructiveOverlapB_wrapB() {
		int base = 0x01000000 - 48000;
		innerTestMvclDestructiveOverlap(base + 0x00002000, 0x0004000, base, 0x00010000);
	}

	@Test
	public void x0E_RR_66_MVCL_destructiveOverlapB_wrapC() {
		int base = 0x01000000 - 20000;
		innerTestMvclDestructiveOverlap(base + 0x00002000, 0x0004000, base, 0x00010000);
	}

	@Test
	public void x0E_RR_67_MVCL_destructiveOverlapB_wrapD() {
		int base = 0x01000000 - 4000;
		innerTestMvclDestructiveOverlap(base + 0x00002000, 0x0004000, base, 0x00010000);
	}

	@Test
	public void x0E_RR_70_MVCL_invR1() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC2);                       // for later comparison
		setInstructions(
				_0E, _36                  // MVCL R3,R6
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 1, 2, CodeBase+2); // ILC=2, CC=unchanged
	}

	@Test
	public void x0E_RR_71_MVCL_invR2() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		setCC(CC2);                       // for later comparison
		setInstructions(
				_0E, _47                  // MVCL R4,R7
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 1, 2, CodeBase+2); // ILC=2, CC=unchanged
	}
}
