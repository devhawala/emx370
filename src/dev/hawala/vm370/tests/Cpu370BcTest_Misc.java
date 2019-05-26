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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import dev.hawala.vm370.vm.machine.Cpu370Bc;
import dev.hawala.vm370.vm.machine.PSWException;
import dev.hawala.vm370.vm.machine.PSWException.PSWProblemType;

@RunWith(value = Parameterized.class)
public class Cpu370BcTest_Misc extends AbstractCpu370BcTest {
	
	public Cpu370BcTest_Misc(Class<? extends Cpu370Bc> cpuClass) {
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
	
	private final int IA_SVC_INTR_BASE = 0x00220000;
	private final int IA_PGM_INTR_BASE = 0x00770000;
	
	
	/*
	** Tests:  0x0A -- SVC [RR] - Supervisor Call
	*/
	
	@Test
	public void x0A_RS_SupervisorCall_01() {
		setIntrNewPSW(Intr_SVC_NewPSW, IA_SVC_INTR_BASE); // set IA for SVC interrupt handler
	
		setCC(CC1);                 // for later comparison
		
		setInstructions(
				_0A, _42            // SVC 42
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_SVC_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkSavedPSW(Intr_SVC_OldPSW, (short)66, 1, 1, CodeBase+2); // ILC=2, CC=1
	}
	
	@Test
	public void x0A_RS_SupervisorCall_02() {
		setIntrNewPSW(Intr_SVC_NewPSW, IA_SVC_INTR_BASE); // set IA for SVC interrupt handler
	
		setCC(CC3);                 // for later comparison
		
		setInstructions(
				_0A, _F0            // SVC 240
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_SVC_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkSavedPSW(Intr_SVC_OldPSW, (short)240, 1, 3, CodeBase+2); // ILC=2, CC=3
	}
	
	
	/*
	** Tests:  0x44 -- EX [RX] - Execute
	*/
	
	@Test
	public void x44_RX_Execute_01a() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		// subject instruction: AR - Add Register
		setMemF(0x010180, 0x1A000000);    // address: 0x010180 -> AR R0,R0
		
		// modification of the subject instruction
		setGPR(4, 0x71111178);            // R4 = bits 24-31 -> 0x78 meaning R7,R8
		
		// setup R7 and R8
		setGPR(7, 0x11111111);
		setGPR(8, 0x33333333);
		
		// setup EX instruction and run it
		setCC(CC3);
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_44, _41, _20, _80        // EX R4,(base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		
		// check the correct result
		checkCC(CC2);
		checkGPR(7, 0x44444444);
		checkGPR(8, 0x33333333);
		checkMemF(0x010180, 0x1A000000);
	}
	
	@Test
	public void x44_RX_Execute_01b() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		// subject instruction: AR - Add Register
		setMemF(0x010180, 0x1A280000);    // address: 0x010180 -> AR R2,R8
		
		// modification of the subject instruction
		setGPR(4, 0x71111150);            // R4 = bits 24-31 -> 0x50 modifies R2,R8 to: R7,R8
		
		// setup R7 and R8
		setGPR(7, 0x11111111);
		setGPR(8, 0x33333333);
		
		// setup EX instruction and run it
		setCC(CC3);
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_44, _41, _20, _80        // EX R4,(base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		
		// check the correct result
		checkCC(CC2);
		checkGPR(7, 0x44444444);
		checkGPR(8, 0x33333333);
		checkMemF(0x010180, 0x1A280000);
	}
	
	@Test
	public void x44_RX_Execute_01c() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		// subject instruction: AR - Add Register
		setMemF(0x010180, 0x1A280000);    // address: 0x010180 -> AR R2,R8
		
		// modification of the subject instruction
		setGPR(4, 0x71111178);            // R4 = bits 24-31 -> 0x78 modifies R2,R8 to: R7,R8
		
		// setup R7 and R8
		setGPR(7, 0x11111111);
		setGPR(8, 0x33333333);
		
		// setup EX instruction and run it
		setCC(CC3);
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_44, _41, _20, _80        // EX R4,(base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		
		// check the correct result
		checkCC(CC2);
		checkGPR(7, 0x44444444);
		checkGPR(8, 0x33333333);
		checkMemF(0x010180, 0x1A280000);
	}
	
	@Test
	public void x44_RX_Execute_02a_specException_targetIsEX() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		// subject instruction: AR - Add Register
		setMemF(0x010180, 0x44000000);    // address: 0x010180 -> EX
		
		// modification of the subject instruction
		setGPR(4, 0x71111178);            // R4 = bits 24-31 -> 0x78 meaning R7,R8
		
		// setup EX instruction and run it
		setCC(CC3);
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_44, _41, _20, _80        // EX R4,(base=R2,index=R1,offset=0x080)
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_EXECUTE_EXCEPTION, 2, 3, CodeBase+4); // ILC=2, CC=3
	}
	
	@Test
	public void x44_RX_Execute_02b_specException_targetAtOddAddress() {
		setIntrNewPSW(Intr_Program_NewPSW, IA_PGM_INTR_BASE); // set IA for ProgramInterrupt handler
		
		// subject instruction: AR - Add Register
		setMemF(0x010180, 0x001A0000);    // address: 0x010181 -> AR R0,R0
		
		// modification of the subject instruction
		setGPR(4, 0x71111178);            // R4 = bits 24-31 -> 0x78 meaning R7,R8
		
		// setup R7 and R8
		setGPR(7, 0x11111111);
		setGPR(8, 0x33333333);
		
		// setup EX instruction and run it
		setCC(CC3);
		setGPR(2, 0x010000);              // set base register for addressing
		setGPR(1, 0x000100);              // set index register for addressing
		setInstructions(
				_44, _41, _20, _81        // EX R4,(base=R2,index=R1,offset=0x081)
		);
		execute(1); // do one instruction
		
		checkIL(0);                 // instruction length code loaded from new PSW
		checkIA(IA_PGM_INTR_BASE);  // instruction address at end loaded from new PSW
		checkCC(CC0);               // condition code loaded from new PSW
		
		checkSavedPSW(Intr_Program_OldPSW, INTR_PGM_SPECIFICATION_EXCEPTION, 2, 3, CodeBase+4); // ILC=2, CC=3
	}
	
	@Test
	public void x09_RR_InsertStorageKey() {
		// initialize the protection keys
		for (int i = 0; i < 8192; i++) {
			this.cpu.pokeProtectionKey(i, _90);
		}
		this.cpu.pokeProtectionKey(1234, _A0);
		
		// setup registers
		setGPR(8, 0x12345678); // r1 = target register where to insert the storage key
		int pageAddr = (1234 * 2048) | 0x78000000;
		setGPR(3, pageAddr);   // r2 = page address for which the storage key is to be fetched
		
		// do the ISK instruction
		setCC(CC3); // for checking
		setInstructions(
				_09, _83                // ISK R8,R3
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		
		// check the correct result
		checkCC(CC3); // must be unchanged
		checkGPR(3, pageAddr);
		checkGPR(8, 0x123456A0);
	}
	
	@Test
	public void x08_RR_SetStorageKey() {
		// initialize the protection keys
		for (int i = 0; i < 8192; i++) {
			this.cpu.pokeProtectionKey(i, _90);
		}
		
		// setup registers
		setGPR(8, 0x123456FF); // r1 = register from where to get the new storage key
		int pageAddr = (1234 * 2048) | 0x78000000;
		setGPR(3, pageAddr);  // r2 = page address for which the protection key is to be set
		
		// do the ISK instruction
		setCC(CC3); // for checking
		setInstructions(
				_08, _83                // SSK R8,R3
		);
		execute(1); // do one instruction
		checkIL(1);              // instruction length code
		checkIA(CodeBase+2);     // instruction address at end
		
		// check the correct result
		checkCC(CC3); // must be unchanged
		checkGPR(3, pageAddr);// must be unchanged
		checkGPR(8, 0x123456FF);// must be unchanged
		for (int i = 0; i < 8192; i++) {
		  int zeKey = this.cpu.peekProtectionKey(i);
		  if (i == 1234) {
			  assertEquals("key of modified page", _FE, zeKey);
		  } else {
			  assertEquals("key of unmodified page", _90, zeKey);
		  }
		}
	}
	
	@Test
	public void x80_RS_SetSystemMask() {
		// check initial state
		assertFalse("channel interrupt mask bit 0", this.cpu.getPswIntrMaskBit(0));
		assertFalse("channel interrupt mask bit 1", this.cpu.getPswIntrMaskBit(1));
		assertFalse("channel interrupt mask bit 2", this.cpu.getPswIntrMaskBit(2));
		assertFalse("channel interrupt mask bit 3", this.cpu.getPswIntrMaskBit(3));
		assertFalse("channel interrupt mask bit 4", this.cpu.getPswIntrMaskBit(4));
		assertFalse("channel interrupt mask bit 5", this.cpu.getPswIntrMaskBit(5));
		assertFalse("channel interrupt mask bit others", this.cpu.getPswIntrMaskBit(6));
		assertFalse("external interrupt mask", this.cpu.getPswIntrMaskBit(7));
		
		setMemB(0x010F80, _F1); // interrupt mask: 0..3: enabled, 4+5+others disabled, external enabled
		setGPR(1, 0x010000);    // R1: base register for addressing
		
		// execute LPSW 
		setInstructions(
				_80, _26, _1F, _80        // SSM (R1,0xF80)  [register spec R2,R6 is ignored!] 
		);
		execute(1);              // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		
		// check the interrupt mask loaded
		assertTrue("channel interrupt mask bit 0", this.cpu.getPswIntrMaskBit(0));
		assertTrue("channel interrupt mask bit 1", this.cpu.getPswIntrMaskBit(1));
		assertTrue("channel interrupt mask bit 2", this.cpu.getPswIntrMaskBit(2));
		assertTrue("channel interrupt mask bit 3", this.cpu.getPswIntrMaskBit(3));
		assertFalse("channel interrupt mask bit 4", this.cpu.getPswIntrMaskBit(4));
		assertFalse("channel interrupt mask bit 5", this.cpu.getPswIntrMaskBit(5));
		assertFalse("channel interrupt mask bit others", this.cpu.getPswIntrMaskBit(6));
		assertTrue("external interrupt mask", this.cpu.getPswIntrMaskBit(7));
	}
	
	@Test
	public void x82_RS_LoadPSW_Ok() {
		// prepare a new PSW in memory
		setMemB(0x010F80, 
				_F1, // interrupt mask: 0..3: enabled, 4+5+others disabled, external enabled
				_96, // protection mask: 0x09, BC(mode=0), MachineCheck enabled, Wait state, Supervisor state (ignored)
				_12, _34, // interruption code (must be ignored)
				_A9, // ILC=2 (ignored), CC=2, ProgramMask: FixedPoint-Overflow: 1, DecimalOverflow: 0, ExponentUnderflow: 0, Significance: 1
				_76,_54, _32 // instruction address
				);
		setGPR(1, 0x010000);              // R1: base register for addressing
		
		// execute LPSW 
		setInstructions(
				_82, _26, _1F, _80        // LPSW (R1,0xF80)  [register spec R2,R6 is ignored!] 
		);
		execute(1); // do one instruction
		// explicitly tested below: instruction length code
		// explicitly tested below: instruction address at end
		
		// check the PSW loaded
		assertTrue("channel interrupt mask bit 0", this.cpu.getPswIntrMaskBit(0));
		assertTrue("channel interrupt mask bit 1", this.cpu.getPswIntrMaskBit(1));
		assertTrue("channel interrupt mask bit 2", this.cpu.getPswIntrMaskBit(2));
		assertTrue("channel interrupt mask bit 3", this.cpu.getPswIntrMaskBit(3));
		assertFalse("channel interrupt mask bit 4", this.cpu.getPswIntrMaskBit(4));
		assertFalse("channel interrupt mask bit 5", this.cpu.getPswIntrMaskBit(5));
		assertFalse("channel interrupt mask bit others", this.cpu.getPswIntrMaskBit(6));
		assertTrue("external interrupt mask", this.cpu.getPswIntrMaskBit(7));
		
		assertEquals("protection key", 0x09, this.cpu.getPswProtectionKey());
		assertTrue("machine check enabled", this.cpu.isMachineCheckIntrEnabled());
		assertTrue("wait state", this.cpu.isWaitState());
		assertTrue("problem state", this.cpu.isProblemState());
		
		assertEquals("interruption code", 0, this.cpu.getPswInterruptionCode());
		
		assertEquals("ilc", 0, this.cpu.getPswInstructionLengthCode());
		assertEquals("condition code", (byte)2, this.cpu.getPswConditionCode());
		assertTrue("ProgramMask - FixedPoint-Overflow", this.cpu.isPswProgramMaskFixedOverflow());
		assertFalse("ProgramMask - DecimalOverflow-Overflow", this.cpu.isPswProgramMaskDecimalOverflow());
		assertFalse("ProgramMask - Exponent-Underflow", this.cpu.isPswProgramMaskExponentUnderflow());
		assertTrue("ProgramMask - Significance", this.cpu.isPswProgramMaskSignificance());
		
		assertEquals("instruction address", 0x765432, this.cpu.getPswInstructionAddress());
	}
	
	@Test
	public void x82_RS_LoadPSW_DisabledWaitState() {
		// prepare a new PSW in memory
		setMemB(0x010F80, 
				_00, // all interrupts disabled
				_96, // protection mask: 0x09, BC(mode=0), MachineCheck enabled, Wait state, Supervisor state (ignored)
				_12, _34, // interruption code (must be ignored)
				_A9, // ILC=2 (ignored), CC=2, ProgramMask: FixedPoint-Overflow: 1, DecimalOverflow: 0, ExponentUnderflow: 0, Significance: 1
				_76,_54, _32 // instruction address
				);
		setGPR(1, 0x010000);              // R1: base register for addressing
		
		// execute LPSW 
		setInstructions(
				_82, _26, _1F, _80        // LPSW (R1,0xF80)  [register spec R2,R6 is ignored!] 
		);
		try {
			this.cpu.execInstruction(0);
			fail("missing expected PSWException(DisableWaitState");
		} catch (PSWException e) {
			assertEquals("PSWException reason", PSWProblemType.DisabledWait, e.getProblemType());
		}
		
		// check the PSW loaded
		assertFalse("channel interrupt mask bit 0", this.cpu.getPswIntrMaskBit(0));
		assertFalse("channel interrupt mask bit 1", this.cpu.getPswIntrMaskBit(1));
		assertFalse("channel interrupt mask bit 2", this.cpu.getPswIntrMaskBit(2));
		assertFalse("channel interrupt mask bit 3", this.cpu.getPswIntrMaskBit(3));
		assertFalse("channel interrupt mask bit 4", this.cpu.getPswIntrMaskBit(4));
		assertFalse("channel interrupt mask bit 5", this.cpu.getPswIntrMaskBit(5));
		assertFalse("channel interrupt mask bit others", this.cpu.getPswIntrMaskBit(6));
		assertFalse("external interrupt mask", this.cpu.getPswIntrMaskBit(7));
		
		assertEquals("protection key", 0x09, this.cpu.getPswProtectionKey());
		assertTrue("machine check enabled", this.cpu.isMachineCheckIntrEnabled());
		assertTrue("wait state", this.cpu.isWaitState());
		assertTrue("problem state", this.cpu.isProblemState());
		
		assertEquals("interruption code", 0, this.cpu.getPswInterruptionCode());
		
		assertEquals("ilc", 0, this.cpu.getPswInstructionLengthCode());
		assertEquals("condition code", (byte)2, this.cpu.getPswConditionCode());
		assertTrue("ProgramMask - FixedPoint-Overflow", this.cpu.isPswProgramMaskFixedOverflow());
		assertFalse("ProgramMask - DecimalOverflow-Overflow", this.cpu.isPswProgramMaskDecimalOverflow());
		assertFalse("ProgramMask - Exponent-Underflow", this.cpu.isPswProgramMaskExponentUnderflow());
		assertTrue("ProgramMask - Significance", this.cpu.isPswProgramMaskSignificance());
		
		assertEquals("instruction address", 0x765432, this.cpu.getPswInstructionAddress());
	}
	
	@Test
	public void x82_RS_LoadPSW_ECMode() {
		// prepare a new PSW in memory
		setMemB(0x010F80, 
				_F1, // interrupt mask: 0..3: enabled, 4+5+others disabled, external enabled
				_9E, // protection mask: 0x09, BC(mode=0), MachineCheck enabled, Wait state, Supervisor state (ignored)
				_12, _34, // interruption code (must be ignored)
				_A9, // ILC=2 (ignored), CC=2, ProgramMask: FixedPoint-Overflow: 1, DecimalOverflow: 0, ExponentUnderflow: 0, Significance: 1
				_76,_54, _32 // instruction address
				);
		setGPR(1, 0x010000);              // R1: base register for addressing
		
		// execute LPSW 
		setInstructions(
				_82, _26, _1F, _80        // LPSW (R1,0xF80)  [register spec R2,R6 is ignored!] 
		);
		try {
			this.cpu.execInstruction(0);
			fail("missing expected PSWException(ECMode");
		} catch (PSWException e) {
			assertEquals("PSWException reason", PSWProblemType.ECMode, e.getProblemType());
		}
		
		// check the PSW loaded
		assertTrue("channel interrupt mask bit 0", this.cpu.getPswIntrMaskBit(0));
		assertTrue("channel interrupt mask bit 1", this.cpu.getPswIntrMaskBit(1));
		assertTrue("channel interrupt mask bit 2", this.cpu.getPswIntrMaskBit(2));
		assertTrue("channel interrupt mask bit 3", this.cpu.getPswIntrMaskBit(3));
		assertFalse("channel interrupt mask bit 4", this.cpu.getPswIntrMaskBit(4));
		assertFalse("channel interrupt mask bit 5", this.cpu.getPswIntrMaskBit(5));
		assertFalse("channel interrupt mask bit others", this.cpu.getPswIntrMaskBit(6));
		assertTrue("external interrupt mask", this.cpu.getPswIntrMaskBit(7));
		
		assertEquals("protection key", 0x09, this.cpu.getPswProtectionKey());
		assertTrue("machine check enabled", this.cpu.isMachineCheckIntrEnabled());
		assertTrue("wait state", this.cpu.isWaitState());
		assertTrue("problem state", this.cpu.isProblemState());
		
		assertEquals("interruption code", 0, this.cpu.getPswInterruptionCode());
		
		assertEquals("ilc", 0, this.cpu.getPswInstructionLengthCode());
		assertEquals("condition code", (byte)2, this.cpu.getPswConditionCode());
		assertTrue("ProgramMask - FixedPoint-Overflow", this.cpu.isPswProgramMaskFixedOverflow());
		assertFalse("ProgramMask - DecimalOverflow-Overflow", this.cpu.isPswProgramMaskDecimalOverflow());
		assertFalse("ProgramMask - Exponent-Underflow", this.cpu.isPswProgramMaskExponentUnderflow());
		assertTrue("ProgramMask - Significance", this.cpu.isPswProgramMaskSignificance());
		
		assertEquals("instruction address", 0x765432, this.cpu.getPswInstructionAddress());
	}
	
	@Test
	public void xAC_RS_StoreThenAndSystemMask() {
		// set the start system mask
		this.cpu.setIntrMask(_7C); // 0111-1100
		
		// execute STNSM
		setCC(CC2);                       // for comparision
		setGPR(1, 0x010000);              // R1: base register for addressing
		setInstructions(
				_AC, _F8, _1F, _80        // STNSM x'F8',(R1,0xF80) 
		);
		execute(1);              // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		
		// check outcome
		checkCC(CC2);
		checkMemB(0x010F80, _7C);
		// new system mask must be 0x78 = 0111-1000
		assertFalse("channel interrupt mask bit 0", this.cpu.getPswIntrMaskBit(0));
		assertTrue("channel interrupt mask bit 1", this.cpu.getPswIntrMaskBit(1));
		assertTrue("channel interrupt mask bit 2", this.cpu.getPswIntrMaskBit(2));
		assertTrue("channel interrupt mask bit 3", this.cpu.getPswIntrMaskBit(3));
		assertTrue("channel interrupt mask bit 4", this.cpu.getPswIntrMaskBit(4));
		assertFalse("channel interrupt mask bit 5", this.cpu.getPswIntrMaskBit(5));
		assertFalse("channel interrupt mask bit others", this.cpu.getPswIntrMaskBit(6));
		assertFalse("external interrupt mask", this.cpu.getPswIntrMaskBit(7));
	}
	
	@Test
	public void xAC_RS_StoreThenOrSystemMask() {
		// set the start system mask
		this.cpu.setIntrMask(_7C); // 0111-1100
		
		// execute STOSM
		setCC(CC2);                       // for comparision
		setGPR(1, 0x010000);              // R1: base register for addressing
		setInstructions(
				_AD, _F1, _1F, _80        // STNSM x'F8',(R1,0xF80) 
		);
		execute(1);              // do one instruction
		checkIL(2);              // instruction length code
		checkIA(CodeBase+4);     // instruction address at end
		
		// check outcome
		checkCC(CC2);
		checkMemB(0x010F80, _7C);
		// new system mask must be 0xFD = 1111-1101
		assertTrue("channel interrupt mask bit 0", this.cpu.getPswIntrMaskBit(0));
		assertTrue("channel interrupt mask bit 1", this.cpu.getPswIntrMaskBit(1));
		assertTrue("channel interrupt mask bit 2", this.cpu.getPswIntrMaskBit(2));
		assertTrue("channel interrupt mask bit 3", this.cpu.getPswIntrMaskBit(3));
		assertTrue("channel interrupt mask bit 4", this.cpu.getPswIntrMaskBit(4));
		assertTrue("channel interrupt mask bit 5", this.cpu.getPswIntrMaskBit(5));
		assertFalse("channel interrupt mask bit others", this.cpu.getPswIntrMaskBit(6));
		assertTrue("external interrupt mask", this.cpu.getPswIntrMaskBit(7));
	}
	
	private static final int _OK = 0;  // instruction known and problem-state
	private static final int _IV = -1; // invalid instruction (unknown)
	private static final int _P1 = 1;  // privileged instruction and return-code is the 1-byte opcode
	private static final int _P2 = 2;  // privileged instruction and return-code is the 2-byte opcode
	private static final int _P4 = 4;  // privileged instruction and return-code is the 4-byte opcode
	
	private static int instr2returntype[] = {
	/*        0    1    2    3    4    5    6    7    8    9    A    B    C    D    E    F    */
	/* 0 */  _IV, _IV, _IV, _IV, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK,
	/* 1 */  _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK,
	/* 2 */  _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK,
	/* 3 */  _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK,
	/* 4 */  _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK,
	/* 5 */  _OK, _IV, _IV, _IV, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK,
	/* 6 */  _OK, _IV, _IV, _IV, _IV, _IV, _IV, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK,
	/* 7 */  _OK, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK,
	/* 8 */  _OK, _IV, _OK, _P4, _P1, _P1, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK,
	/* 9 */  _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _IV, _IV, _IV, _P2, _P2, _P2, _P2,
	/* A */  _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _OK, _OK, _P1, _IV,
	/* B */  _IV, _P1, _P2, _IV, _IV, _IV, _P1, _P1, _IV, _IV, _OK, _OK, _IV, _OK, _OK, _OK,
	/* C */  _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV,
	/* D */  _IV, _OK, _OK, _OK, _OK, _OK, _OK, _OK, _IV, _IV, _IV, _IV, _OK, _OK, _OK, _OK,
	/* E */  _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV, _IV,
	/* F */  _OK, _OK, _OK, _OK, _IV, _IV, _IV, _IV, _OK, _OK, _OK, _OK, _OK, _OK, _IV, _IV
	};
	
	@Test
	public void xXX_ALL_ReturnValuePerOpcode_and_Statistics() {
		long cpuStartCount = this.cpu.getTotalInstructions();
		
		for (int i = 0; i < instr2returntype.length; i++) {
			setMemB(0x00000400, (byte)0x1A, (byte)0x00); // place instruction AR R0,R0 at 0x00000400 (for EX)
			for (int r = 0; r < 16; r++) { setGPR(r,0); }// reset registers
			this.setInstructions((byte)i, (byte)0x44, (byte)0x44, (byte)0x00); // results in address 0x000400 for base- or indexed addressing (offset 0x400 with all registers having value zero)
			int res = 0x00FFFFFF; // keep the compiler happy
			try {
				res = cpu.execInstruction(0);
			} catch (Exception exc) {
				exc.printStackTrace();
				fail(String.format("Instruction 0x%02X raised exception: %s", i, exc.getMessage()));
			}
			int rettype = instr2returntype[i];
			if (rettype== _OK) {
				if (res != 0) {
					assertEquals(String.format("Result of cpu.execInstruction() for known instruction 0x%02X", i), 0, res);
				}
			} else if (rettype == _IV) {
				if (res != -1) {
					assertEquals(String.format("Result of cpu.execInstruction() for invalid/unimplemented instruction 0x%02X", i), -1, res);
				}
			} else if (rettype == _P1) {
				if (res != i) {
					assertEquals(String.format("Result of cpu.execInstruction() for privileged(1) instruction 0x%02X", i), i, res);
				}
			} else if (rettype == _P2) {
				int expRes = (i << 8) | 0x44;
				if (res != expRes) {
					assertEquals(String.format("Result of cpu.execInstruction() for privileged(2) instruction 0x%02X", i), expRes, res);
				}
			} else if (rettype == _P4) {
				int expRes = (i << 24) | 0x00444400;
				if (res != expRes) {
					assertEquals(String.format("Result of cpu.execInstruction() for DIAG = privileged(4) instruction 0x%02X", i), expRes, res);
				}
			} else {
				fail(String.format("Invalid returntype %d specified for instruction 0x%02X raised exception: %s", rettype, i));
			}
		}
		
		// test CPU-method getTotalInstructions(): expected instructions executed = 256 opcodes + 1 as EX-target
		assertEquals("Number of executed CPU instructions", 257, this.cpu.getTotalInstructions() - cpuStartCount);
		
		// dump instructions statistics if available (hard setting in Cpu370Bc)
		if (this.cpu.hasInstructionStatistics()) {
			this.cpu.dumpInstructionStatistics(false, true);
			System.out.println();
			this.cpu.dumpInstructionStatistics(true, false);
		}
	}
	
	private final static int PERF_INSN_COUNT = 1000000;
	
	private void innerPerTest(String what) {
		long cpuStartCount = this.cpu.getTotalInstructions();
		long startTime = System.nanoTime();
		execute(PERF_INSN_COUNT); // do 1.000.000 instructions (loops)
		long stopTime = System.nanoTime();
		
		assertEquals("Number of executed instructions", PERF_INSN_COUNT, this.cpu.getTotalInstructions() - cpuStartCount);
		
		long estimatedTime = stopTime - startTime;
		System.out.printf("\nExecution time for %d %s in nanoSecs: %d\n", PERF_INSN_COUNT, what, estimatedTime);
		float mips = ((float)PERF_INSN_COUNT * 1000f) / estimatedTime;
		System.out.printf("Estimated MIPS: %2.3f\n", mips);		
	}
	
	@Test
	public void perfTest01_BCR() {
		
		setCC(CC2);
		setGPR(1, CodeBase);              // R1: branch address => jump back to same instruction!
		setInstructions(
				_07, _21                  // BCR CC2 <- R1
		);
		
		this.innerPerTest("BCR-loops");
	}
	
	@Test
	public void perfTest02_BC() {
		
		setCC(CC2);
		setGPR(1, CodeBase - 0x0EFE);     // set index register for addressing
		setInstructions(
				_47, _21, _0E, _FE        // BC CC2 <- (base=0,index=R1,offset=0xEFE)
		);
		
		this.innerPerTest("BC-loops");
	}
	
	@Test
	public void perfTest03_L_A_ST_BC() {
		
		// R1: index register for addressing in BC
		// R2: base register for addressing in L, A, ST
		// R3: computation register in A and target / source for L / ST
		
		setGPR(2, 0x010000);              // R2 base register for addressing
		setMemF(0x010180, 0x12345000);    // 1st number at address: 0x010180
		setMemF(0x010184, 0x00000678);    // 2nd number at address: 0x010184
		// result is stored at address: 0x010188
		
		setGPR(1, CodeBase - 0x0EFE);     // set index register for addressing
		setInstructions(
				_58, _30, _21, _80,       // L R3 <- (base=R2,index=0,offset=0x180)
				_5A, _30, _21, _84,       // A R3 <- (base=R2,index=0,offset=0x184), result is positive (=> CC = 2)
				_50, _30, _21, _88,       // ST (base=R2,index=0,offset=0x188) <- R3
				_47, _21, _0E, _FE        // BC CC2 <- (base=0,index=R1,offset=0xEFE)
		);
		
		this.innerPerTest("Load+Add+STore+BC-loops");
		
		if (this.cpu.hasInstructionStatistics()) {
			this.cpu.dumpInstructionStatistics(true, true);
		}
	}
}
