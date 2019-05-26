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

import java.util.Arrays;
import java.util.Collection;

import org.junit.runners.Parameterized.Parameters;

import dev.hawala.vm370.vm.machine.Cpu370Bc;
import dev.hawala.vm370.vm.machine.Cpu370BcBasic;
import dev.hawala.vm370.vm.machine.Cpu370BcJumpTable;
import dev.hawala.vm370.vm.machine.Cpu370BcLambda;
import dev.hawala.vm370.vm.machine.Cpu370BcLambda2;
import dev.hawala.vm370.vm.machine.Cpu370BcLambda3;
import dev.hawala.vm370.vm.machine.Cpu370BcLambda4;

/**
 * Abstract class with common functionality for unit-tests.
 * 
 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2015
 */
public class AbstractCpu370BcTest {
	
	protected static final Object[][] CPU_IMPLEMENTATIONS_TO_BE_TESTED =
        {
            {   Cpu370BcBasic.class      },
            {   Cpu370BcJumpTable.class  },
            {   Cpu370BcLambda.class     },
            {   Cpu370BcLambda2.class    },
            {   Cpu370BcLambda3.class    },
            {   Cpu370BcLambda4.class    }
        };
	
	@Parameters
    public static Collection<Object[]> testParams()
    {
        return Arrays.asList(CPU_IMPLEMENTATIONS_TO_BE_TESTED);
    }
    
    protected Class<? extends Cpu370Bc> cpuClassUnderTest = Cpu370Bc.class; // Cpu370Bc is abstract, so instantiation will fail!!

	protected Cpu370Bc cpu = null;
	protected final int CodeBase = 0x020000;
	
	protected void preTest() {
		if (cpu == null) {
//			cpu = new Cpu370Bc();
//			cpu = new Cpu370BcJumpTable();
//			cpu = new Cpu370BcLambda();
//			cpu = new Cpu370BcLambda2();
//			cpu = new Cpu370BcLambda3();
//			cpu = new Cpu370BcLambda4();
			try {
				cpu = cpuClassUnderTest.newInstance();
			} catch (InstantiationException | IllegalAccessException e) {
				fail("Unable to instantiate CPU");
			}
		} else {
			cpu.resetEngine();
		}
	}

	protected void setGPR(int no, int value) {
		cpu.setGPR(no, value);
	}

	protected void setCC(byte cc) {
		cpu.setPswConditionCode((byte)(cc & 0x03));
	}

	protected void setMemB(int addr, byte... bytes) {
		for (byte b : bytes) {
			cpu.pokeMainMem(addr++ & 0x00FFFFFF, b);
		}
	}

	protected void setMemH(int addr, short... halfwords) {
		for (short halfword : halfwords) {
			cpu.pokeMainMem(addr, halfword);
			addr += 2;
		}
	}

	protected void setMemF(int addr, int... words) {
		for (int word : words) {
			cpu.pokeMainMem(addr, word);
			addr += 4;
		}
	}

	protected final short INTR_PGM_OPERATION_EXCEPTION     = (short)0x0001;
	protected final short INTR_PGM_PRIVILEDGE_EXCEPTION    = (short)0x0002;
	protected final short INTR_PGM_EXECUTE_EXCEPTION       = (short)0x0003;
	protected final short INTR_PGM_ADDRESSING_EXCEPTION    = (short)0x0005;
	protected final short INTR_PGM_SPECIFICATION_EXCEPTION = (short)0x0006;
	protected final short INTR_PGM_DATA_EXCEPTION          = (short)0x0007;
	protected final short INTR_PGM_FIXEDPOINT_OVERFLOW     = (short)0x0008;
	protected final short INTR_PGM_FIXEDPOINT_DIVIDE       = (short)0x0009;
	protected final short INTR_PGM_DECIMAL_OVERFLOW        = (short)0x000A;
	protected final short INTR_PGM_DECIMAL_DIVIDE          = (short)0x000B;
	protected final short INTR_PGM_EXPONENT_OVERFLOW       = (short)0x000C;
	protected final short INTR_PGM_EXPONENT_UNDERFLOW      = (short)0x000D;
	protected final short INTR_PGM_SIGNIFICANCE            = (short)0x000E;
	protected final short INTR_PGM_FLOAT_DIVIDE            = (short)0x000F;

	protected final int Intr_Program_OldPSW = 40;
	protected final int Intr_Program_NewPSW = 104;
	
	protected final int Intr_SVC_OldPSW = 32;
	protected final int Intr_SVC_NewPSW = 96; 
	
	protected void setIntrNewPSW(int at, int ia) {
		cpu.pokeMainMem(at++, _00); // byte 1: intr mask bits
		cpu.pokeMainMem(at++, _01); // byte 2: prot. key, bc/ec mode, machine check mask, wait state, problem state
		cpu.pokeMainMem(at++, _00); // byte 3 .. 4: interruption code
		cpu.pokeMainMem(at++, _00);
		cpu.pokeMainMem(at++, _00); // byte 5: ILC, CC, program mask
		cpu.pokeMainMem(at++, (byte)((ia & 0x00FF0000) >> 16)); // byte 6 .. 8: instruction address
		cpu.pokeMainMem(at++, (byte)((ia & 0x0000FF00) >> 8));
		cpu.pokeMainMem(at++, (byte)(ia & 0x000000FF));
	}
	
	protected void setProgramMask(
			boolean fixedOverflow,
			boolean decimalOverflow,
			boolean exponentUnderflow,
			boolean significance) {
		cpu.setPswProgramMaskFixedOverflow(fixedOverflow);
		cpu.setPswProgramMaskDecimalOverflow(decimalOverflow);
		cpu.setPswProgramMaskExponentUnderflow(exponentUnderflow);
		cpu.setPswProgramMaskSignificance(significance);
	}

	protected void setInstructions(byte... instrs) {
		int ia = CodeBase;
		
		cpu.setPswInstructionAddress(ia);
		for(byte instr : instrs) {
			cpu.pokeMainMem(ia++, instr);
		}
	}

	protected void execute(int instrCount) {
		try {
		for (int i = 0; i < instrCount; i++) {
			int res = cpu.execInstruction(0); // normal execution (instruction at IA), not through EX-instruction
			if (res != 0 && i != (instrCount - 1)) {
				fail("Unexpected instruction outcome <> 0 in tested instruction sequence before last instruction");
			}
		}
		} catch(Exception exc) {
			fail("Unexpected exception in tested instruction sequence: " + exc.getMessage());
		}
	}

	protected final byte CC0 = (byte)0;
	protected final byte CC1 = (byte)1;
	protected final byte CC2 = (byte)2;
	protected final byte CC3 = (byte)3;
	
	protected void checkCC(byte cc) {
		assertEquals("ConditionCode", cc, cpu.getPswConditionCode());
	}

	protected void checkIA(int ia) {
		assertEquals("InstructionAddress", ia, cpu.getPswInstructionAddress());
	}

	protected void checkIL(int ilCode) {
		assertEquals("InstructionLengthCode", ilCode, cpu.getPswInstructionLengthCode());
	}
	
	protected void checkIntrCode(short intrCode) {
		
		assertEquals("InterruptionCode", intrCode, cpu.getPswInterruptionCode());
	}
	
	protected void checkSavedPSW(int at, short intrCode, int ilc, int cc, int ia) {
		assertEquals("Saved-PSW :: InterruptionCode", intrCode, cpu.peekMainMemShort(at+2));
		
		byte ilcAndCc = cpu.peekMainMemByte(at+4);
		assertEquals("Saved-PSW :: InstructionLengthCode", ilc, (byte)((ilcAndCc >> 6) & 0x03));
		assertEquals("Saved-PSW :: ConditionCode", cc, (byte)((ilcAndCc >> 4) & 0x03));
		
		assertEquals("Saved-PSW :: InstructionAddress", ia, cpu.peekMainMemInt(at+4) & 0x00FFFFFF);
	}

	protected void checkGPR(int regno, int value) {
		int curr = cpu.getGPR(regno);
		if (curr == value) { return; }
		assertEquals("GPR[" + regno + "]", value, curr);
	}

	protected void checkMemB(int addr, byte... values) {
		for (int i = 0; i < values.length; i++) {
			byte curr = cpu.peekMainMemByte(addr);
			byte value = values[i];
			if (curr != value) {
				assertEquals(String.format("Memory-BYTE at 0x%06X", addr), value, curr);
				return;
			}
			addr++;
		}
	}

	protected void checkMemH(int addr, short... values) {
		for (int i = 0; i < values.length; i++) {
			short curr = cpu.peekMainMemShort(addr);
			short value = values[i];
			if (curr != value) {
				assertEquals(String.format("Memory-HALFWORD at 0x%06X", addr), value, curr);
				return;
			}
			addr += 2;
		}
	}

	protected void checkMemF(int addr, int... values) {
		for (int i = 0; i < values.length; i++) {
			int curr = cpu.peekMainMemInt(addr);
			int value = values[i];
			if (curr != value) {
				assertEquals(String.format("Memory-FULLWORD at 0x%06X", addr), value, curr);
				return;
			}
			addr += 4;
		}
	}

	protected static final byte _00 = (byte) 0x00;
	protected static final byte _01 = (byte) 0x01;
	protected static final byte _02 = (byte) 0x02;
	protected static final byte _03 = (byte) 0x03;
	protected static final byte _04 = (byte) 0x04;
	protected static final byte _05 = (byte) 0x05;
	protected static final byte _06 = (byte) 0x06;
	protected static final byte _07 = (byte) 0x07;
	protected static final byte _08 = (byte) 0x08;
	protected static final byte _09 = (byte) 0x09;
	protected static final byte _0A = (byte) 0x0A;
	protected static final byte _0B = (byte) 0x0B;
	protected static final byte _0C = (byte) 0x0C;
	protected static final byte _0D = (byte) 0x0D;
	protected static final byte _0E = (byte) 0x0E;
	protected static final byte _0F = (byte) 0x0F;
	protected static final byte _10 = (byte) 0x10;
	protected static final byte _11 = (byte) 0x11;
	protected static final byte _12 = (byte) 0x12;
	protected static final byte _13 = (byte) 0x13;
	protected static final byte _14 = (byte) 0x14;
	protected static final byte _15 = (byte) 0x15;
	protected static final byte _16 = (byte) 0x16;
	protected static final byte _17 = (byte) 0x17;
	protected static final byte _18 = (byte) 0x18;
	protected static final byte _19 = (byte) 0x19;
	protected static final byte _1A = (byte) 0x1A;
	protected static final byte _1B = (byte) 0x1B;
	protected static final byte _1C = (byte) 0x1C;
	protected static final byte _1D = (byte) 0x1D;
	protected static final byte _1E = (byte) 0x1E;
	protected static final byte _1F = (byte) 0x1F;
	protected static final byte _20 = (byte) 0x20;
	protected static final byte _21 = (byte) 0x21;
	protected static final byte _22 = (byte) 0x22;
	protected static final byte _23 = (byte) 0x23;
	protected static final byte _24 = (byte) 0x24;
	protected static final byte _25 = (byte) 0x25;
	protected static final byte _26 = (byte) 0x26;
	protected static final byte _27 = (byte) 0x27;
	protected static final byte _28 = (byte) 0x28;
	protected static final byte _29 = (byte) 0x29;
	protected static final byte _2A = (byte) 0x2A;
	protected static final byte _2B = (byte) 0x2B;
	protected static final byte _2C = (byte) 0x2C;
	protected static final byte _2D = (byte) 0x2D;
	protected static final byte _2E = (byte) 0x2E;
	protected static final byte _2F = (byte) 0x2F;
	protected static final byte _30 = (byte) 0x30;
	protected static final byte _31 = (byte) 0x31;
	protected static final byte _32 = (byte) 0x32;
	protected static final byte _33 = (byte) 0x33;
	protected static final byte _34 = (byte) 0x34;
	protected static final byte _35 = (byte) 0x35;
	protected static final byte _36 = (byte) 0x36;
	protected static final byte _37 = (byte) 0x37;
	protected static final byte _38 = (byte) 0x38;
	protected static final byte _39 = (byte) 0x39;
	protected static final byte _3A = (byte) 0x3A;
	protected static final byte _3B = (byte) 0x3B;
	protected static final byte _3C = (byte) 0x3C;
	protected static final byte _3D = (byte) 0x3D;
	protected static final byte _3E = (byte) 0x3E;
	protected static final byte _3F = (byte) 0x3F;
	protected static final byte _40 = (byte) 0x40;
	protected static final byte _41 = (byte) 0x41;
	protected static final byte _42 = (byte) 0x42;
	protected static final byte _43 = (byte) 0x43;
	protected static final byte _44 = (byte) 0x44;
	protected static final byte _45 = (byte) 0x45;
	protected static final byte _46 = (byte) 0x46;
	protected static final byte _47 = (byte) 0x47;
	protected static final byte _48 = (byte) 0x48;
	protected static final byte _49 = (byte) 0x49;
	protected static final byte _4A = (byte) 0x4A;
	protected static final byte _4B = (byte) 0x4B;
	protected static final byte _4C = (byte) 0x4C;
	protected static final byte _4D = (byte) 0x4D;
	protected static final byte _4E = (byte) 0x4E;
	protected static final byte _4F = (byte) 0x4F;
	protected static final byte _50 = (byte) 0x50;
	protected static final byte _51 = (byte) 0x51;
	protected static final byte _52 = (byte) 0x52;
	protected static final byte _53 = (byte) 0x53;
	protected static final byte _54 = (byte) 0x54;
	protected static final byte _55 = (byte) 0x55;
	protected static final byte _56 = (byte) 0x56;
	protected static final byte _57 = (byte) 0x57;
	protected static final byte _58 = (byte) 0x58;
	protected static final byte _59 = (byte) 0x59;
	protected static final byte _5A = (byte) 0x5A;
	protected static final byte _5B = (byte) 0x5B;
	protected static final byte _5C = (byte) 0x5C;
	protected static final byte _5D = (byte) 0x5D;
	protected static final byte _5E = (byte) 0x5E;
	protected static final byte _5F = (byte) 0x5F;
	protected static final byte _60 = (byte) 0x60;
	protected static final byte _61 = (byte) 0x61;
	protected static final byte _62 = (byte) 0x62;
	protected static final byte _63 = (byte) 0x63;
	protected static final byte _64 = (byte) 0x64;
	protected static final byte _65 = (byte) 0x65;
	protected static final byte _66 = (byte) 0x66;
	protected static final byte _67 = (byte) 0x67;
	protected static final byte _68 = (byte) 0x68;
	protected static final byte _69 = (byte) 0x69;
	protected static final byte _6A = (byte) 0x6A;
	protected static final byte _6B = (byte) 0x6B;
	protected static final byte _6C = (byte) 0x6C;
	protected static final byte _6D = (byte) 0x6D;
	protected static final byte _6E = (byte) 0x6E;
	protected static final byte _6F = (byte) 0x6F;
	protected static final byte _70 = (byte) 0x70;
	protected static final byte _71 = (byte) 0x71;
	protected static final byte _72 = (byte) 0x72;
	protected static final byte _73 = (byte) 0x73;
	protected static final byte _74 = (byte) 0x74;
	protected static final byte _75 = (byte) 0x75;
	protected static final byte _76 = (byte) 0x76;
	protected static final byte _77 = (byte) 0x77;
	protected static final byte _78 = (byte) 0x78;
	protected static final byte _79 = (byte) 0x79;
	protected static final byte _7A = (byte) 0x7A;
	protected static final byte _7B = (byte) 0x7B;
	protected static final byte _7C = (byte) 0x7C;
	protected static final byte _7D = (byte) 0x7D;
	protected static final byte _7E = (byte) 0x7E;
	protected static final byte _7F = (byte) 0x7F;
	protected static final byte _80 = (byte) 0x80;
	protected static final byte _81 = (byte) 0x81;
	protected static final byte _82 = (byte) 0x82;
	protected static final byte _83 = (byte) 0x83;
	protected static final byte _84 = (byte) 0x84;
	protected static final byte _85 = (byte) 0x85;
	protected static final byte _86 = (byte) 0x86;
	protected static final byte _87 = (byte) 0x87;
	protected static final byte _88 = (byte) 0x88;
	protected static final byte _89 = (byte) 0x89;
	protected static final byte _8A = (byte) 0x8A;
	protected static final byte _8B = (byte) 0x8B;
	protected static final byte _8C = (byte) 0x8C;
	protected static final byte _8D = (byte) 0x8D;
	protected static final byte _8E = (byte) 0x8E;
	protected static final byte _8F = (byte) 0x8F;
	protected static final byte _90 = (byte) 0x90;
	protected static final byte _91 = (byte) 0x91;
	protected static final byte _92 = (byte) 0x92;
	protected static final byte _93 = (byte) 0x93;
	protected static final byte _94 = (byte) 0x94;
	protected static final byte _95 = (byte) 0x95;
	protected static final byte _96 = (byte) 0x96;
	protected static final byte _97 = (byte) 0x97;
	protected static final byte _98 = (byte) 0x98;
	protected static final byte _99 = (byte) 0x99;
	protected static final byte _9A = (byte) 0x9A;
	protected static final byte _9B = (byte) 0x9B;
	protected static final byte _9C = (byte) 0x9C;
	protected static final byte _9D = (byte) 0x9D;
	protected static final byte _9E = (byte) 0x9E;
	protected static final byte _9F = (byte) 0x9F;
	protected static final byte _A0 = (byte) 0xA0;
	protected static final byte _A1 = (byte) 0xA1;
	protected static final byte _A2 = (byte) 0xA2;
	protected static final byte _A3 = (byte) 0xA3;
	protected static final byte _A4 = (byte) 0xA4;
	protected static final byte _A5 = (byte) 0xA5;
	protected static final byte _A6 = (byte) 0xA6;
	protected static final byte _A7 = (byte) 0xA7;
	protected static final byte _A8 = (byte) 0xA8;
	protected static final byte _A9 = (byte) 0xA9;
	protected static final byte _AA = (byte) 0xAA;
	protected static final byte _AB = (byte) 0xAB;
	protected static final byte _AC = (byte) 0xAC;
	protected static final byte _AD = (byte) 0xAD;
	protected static final byte _AE = (byte) 0xAE;
	protected static final byte _AF = (byte) 0xAF;
	protected static final byte _B0 = (byte) 0xB0;
	protected static final byte _B1 = (byte) 0xB1;
	protected static final byte _B2 = (byte) 0xB2;
	protected static final byte _B3 = (byte) 0xB3;
	protected static final byte _B4 = (byte) 0xB4;
	protected static final byte _B5 = (byte) 0xB5;
	protected static final byte _B6 = (byte) 0xB6;
	protected static final byte _B7 = (byte) 0xB7;
	protected static final byte _B8 = (byte) 0xB8;
	protected static final byte _B9 = (byte) 0xB9;
	protected static final byte _BA = (byte) 0xBA;
	protected static final byte _BB = (byte) 0xBB;
	protected static final byte _BC = (byte) 0xBC;
	protected static final byte _BD = (byte) 0xBD;
	protected static final byte _BE = (byte) 0xBE;
	protected static final byte _BF = (byte) 0xBF;
	protected static final byte _C0 = (byte) 0xC0;
	protected static final byte _C1 = (byte) 0xC1;
	protected static final byte _C2 = (byte) 0xC2;
	protected static final byte _C3 = (byte) 0xC3;
	protected static final byte _C4 = (byte) 0xC4;
	protected static final byte _C5 = (byte) 0xC5;
	protected static final byte _C6 = (byte) 0xC6;
	protected static final byte _C7 = (byte) 0xC7;
	protected static final byte _C8 = (byte) 0xC8;
	protected static final byte _C9 = (byte) 0xC9;
	protected static final byte _CA = (byte) 0xCA;
	protected static final byte _CB = (byte) 0xCB;
	protected static final byte _CC = (byte) 0xCC;
	protected static final byte _CD = (byte) 0xCD;
	protected static final byte _CE = (byte) 0xCE;
	protected static final byte _CF = (byte) 0xCF;
	protected static final byte _D0 = (byte) 0xD0;
	protected static final byte _D1 = (byte) 0xD1;
	protected static final byte _D2 = (byte) 0xD2;
	protected static final byte _D3 = (byte) 0xD3;
	protected static final byte _D4 = (byte) 0xD4;
	protected static final byte _D5 = (byte) 0xD5;
	protected static final byte _D6 = (byte) 0xD6;
	protected static final byte _D7 = (byte) 0xD7;
	protected static final byte _D8 = (byte) 0xD8;
	protected static final byte _D9 = (byte) 0xD9;
	protected static final byte _DA = (byte) 0xDA;
	protected static final byte _DB = (byte) 0xDB;
	protected static final byte _DC = (byte) 0xDC;
	protected static final byte _DD = (byte) 0xDD;
	protected static final byte _DE = (byte) 0xDE;
	protected static final byte _DF = (byte) 0xDF;
	protected static final byte _E0 = (byte) 0xE0;
	protected static final byte _E1 = (byte) 0xE1;
	protected static final byte _E2 = (byte) 0xE2;
	protected static final byte _E3 = (byte) 0xE3;
	protected static final byte _E4 = (byte) 0xE4;
	protected static final byte _E5 = (byte) 0xE5;
	protected static final byte _E6 = (byte) 0xE6;
	protected static final byte _E7 = (byte) 0xE7;
	protected static final byte _E8 = (byte) 0xE8;
	protected static final byte _E9 = (byte) 0xE9;
	protected static final byte _EA = (byte) 0xEA;
	protected static final byte _EB = (byte) 0xEB;
	protected static final byte _EC = (byte) 0xEC;
	protected static final byte _ED = (byte) 0xED;
	protected static final byte _EE = (byte) 0xEE;
	protected static final byte _EF = (byte) 0xEF;
	protected static final byte _F0 = (byte) 0xF0;
	protected static final byte _F1 = (byte) 0xF1;
	protected static final byte _F2 = (byte) 0xF2;
	protected static final byte _F3 = (byte) 0xF3;
	protected static final byte _F4 = (byte) 0xF4;
	protected static final byte _F5 = (byte) 0xF5;
	protected static final byte _F6 = (byte) 0xF6;
	protected static final byte _F7 = (byte) 0xF7;
	protected static final byte _F8 = (byte) 0xF8;
	protected static final byte _F9 = (byte) 0xF9;
	protected static final byte _FA = (byte) 0xFA;
	protected static final byte _FB = (byte) 0xFB;
	protected static final byte _FC = (byte) 0xFC;
	protected static final byte _FD = (byte) 0xFD;
	protected static final byte _FE = (byte) 0xFE;
	protected static final byte _FF = (byte) 0xFF;

	public AbstractCpu370BcTest() {
		super();
	}

}