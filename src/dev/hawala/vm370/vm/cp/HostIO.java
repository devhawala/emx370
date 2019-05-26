/*
** This file is part of the emx370 emulator.
**
** This software is provided "as is" in the hope that it will be useful,
** with no promise, commitment or even warranty (explicit or implicit)
** to be suited or usable for any particular purpose.
** Using this software is at your own risk!
**
** Written by Dr. Hans-Walter Latz, Berlin (Germany), 2015
** Released to the public domain.
*/

package dev.hawala.vm370.vm.cp;

import static dev.hawala.vm370.ebcdic.PlainHex.*;
import static dev.hawala.vm370.ebcdic.Ebcdic.*;

import dev.hawala.vm370.ebcdic.Ebcdic;
import dev.hawala.vm370.ebcdic.EbcdicHandler;
import dev.hawala.vm370.vm.machine.CPVirtualMachine;
import dev.hawala.vm370.vm.machine.Cpu370Bc;
import dev.hawala.vm370.vm.machine.PSWException;

/**
 * Access functionality to CMS functions for manipulating
 * files, allowing the command interpreter to access files
 * for transferring data between virtual machines.
 * <p>
 * It should be ensured that no CMS code is executing while
 * any of the functions in this class are invoked.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public class HostIO {
	
	@FunctionalInterface
	public interface Worker {
		public void execute(HostIO on);
	}
	
	private final static byte[] CMS_MACRO_INSTRUCTIONS = {
		// R10: must always be 0x02000 == load address for the following code (reason: absolute addresses in the following code)
		// R04: must be 0x02000 for file operations == address of the FSCB
		// ZEFILE   FSCB  'FILENAMEFILETYPEA1',RECFM=F,BUFFER=BUF,BSIZE=128
		/* +x00 */	_40,_40,_40,_40,_40,_40,_40,_40,   // 8 bytes : command to execute on the FSCB
		/* +x08 */	_C6,_C9,_D3,_C5,_D5,_C1,_D4,_C9,   // 8 bytes : FILENAME
		/* +x10 */	_C6,_C9,_D3,_C5,_E3,_E8,_D7,_C9,   // 8 bytes : FILETYPE
		/* +x18 */	_C1,_F1,                           // 2 bytes : A1 (filemode)
		/* +x1A */	_00,_00,                           // halfword: ?alignment?
		/* +x1C */	_00,_02,_10,_00,                   // fullword: buffer address
		/* +x20 */	_00,_00,_00,_80,                   // fullword: buffer size
		/* +x24 */	_C6,_40,                           // 2 bytes : recfm (F|V) and a blank
		/* +x26 */	_00,_01,                           // halfword: number of records to read/write
		/* +x28 */	_00,_00,_00,_00,                   // fullword: record number to read/write
			
		// XZEFILE  SR    15,15 (endmarker => separator)
		/* +x2C	 */	_1B,_FF,
		
		// FERASE   FSERASE FSCB=(4),
		/* +x2E	 */	_18,_14,                           // LR    1,4
		/* +x30	 */	_D2,_07,_10,_00,_A0,_F8,           // MVC   0(8,1),=CL8'ERASE'
		/* +x36	 */	_0A,_CA,                           // SVC   202
		/* +x38	 */	_00,_02,_00,_3C,                   // DC    AL4(*+4)
		
		// XFERASE  SR    15,15 (endmarker => breakpoint)
		/* +x3C	 */	_1B,_FF,
		
		// FCLOSE   FSCLOSE FSCB=(4)
		/* +x3E	 */	_18,_14,                           // LR    1,4
		/* +x40	 */	_D2,_07,_10,_00,_A1,_00,           // MVC   0(8,1),=CL8'FINIS'
		/* +x46	 */	_0A,_CA,                           // SVC   202
		/* +x48	 */	_00,_02,_00,_4C,                   // DC    AL4(*+4)
		
		// XFCLOSE  SR    15,15 (endmarker => breakpoint)
		/* +x4C	 */	_1B,_FF,
		
		// FOPEN    FSOPEN FSCB=(4)
		/* +x4E	 */	_18,_14,                           // LR    1,4
		/* +x50	 */	_D2,_03,_10,_28,_10,_1C,           // MVC   40(4,1),28(1)
		/* +x56	 */	_D2,_07,_10,_00,_A1,_08,           // MVC   0(8,1),=CL8'STATE'
		/* +x5C	 */	_0A,_CA,                           // SVC   202
		/* +x5E	 */	_00,_02,_00,_80,                   // DC    AL4(*+34)
		/* +x62	 */	_58,_F0,_10,_1C,                   // L     15,28(,1)
		/* +x66	 */	_D2,_03,_10,_1C,_10,_28,           // MVC   28(4,1),40(1)
		/* +x6C	 */	_D2,_01,_10,_18,_F0,_18,           // MVC   24(2,1),24(15)
		/* +x72	 */	_D2,_03,_10,_20,_F0,_20,           // MVC   32(4,1),32(15)
		/* +x78	 */	_D2,_00,_10,_24,_F0,_1E,           // MVC   36(1,1),30(15)
		/* +x7E	 */	_1B,_FF,                           // SR    15,15
		
		// XFOPEN   SR    15,15 (endmarker => breakpoint)
		/* +x80	 */	_1B,_FF,
		
		// FREAD    FSREAD FSCB=(4)
		/* +x82	 */	_18,_14, // LR    1,4
		/* +x84	 */	_D2,_07,_10,_00,_A1,_10,           // MVC   0(8,1),=CL8'RDBUF'
		/* +x8A	 */	_0A,_CA,                           // SVC   202
		/* +x8C	 */	_00,_02,_00,_90,                   // DC    AL4(*+4)
		/* +x90	 */	_58,_00,_10,_28,                   // L     0,40(,1)
		
		// XFREAD   SR    15,15 (endmarker => breakpoint)
		/* +x94	 */	_1B,_FF,
		
		// FSTATE   FSSTATE FSCB=(4)
		/* +x96	 */	_18,_14,                           // LR    1,4
		/* +x98	 */	_D2,_07,_10,_00,_A1,_08,           // MVC   0(8,1),=CL8'STATE'
		/* +x9E	 */	_D2,_03,_10,_28,_10,_1C,           // MVC   40(4,1),28(1)
		/* +xA4	 */	_0A,_CA,                           // SVC   202
		/* +xA6	 */	_00,_02,_00,_B8,                   // DC    AL4(DMS0007B)
		/* +xAA	 */	_58,_F0,_10,_1C,                   // L     15,28(,1)
		/* +xAE	 */	_D2,_03,_10,_1C,_10,_28,           // MVC   28(4,1),40(1)
		/* +xB4	 */	_18,_1F,                           // LR    1,15
		/* +xB6	 */	_1B,_FF,                           // SR    15,15
		// DMS0007B     EQU *
		
		// XFSTATE  SR    15,15 (endmarker => breakpoint)
		/* +xB8	 */	_1B,_FF,
		
		// FWRITE   FSWRITE FSCB=(4)
		/* +xBA	 */	_18,_14,                           // LR    1,4
		/* +xBC	 */	_D2,_07,_10,_00,_A1,_18,           // MVC   0(8,1),=CL8'WRBUF'
		/* +xC2	 */	_0A,_CA,                           // SVC   202
		/* +xC4	 */	_00,_02,_00,_C8,                   // DC    AL4(*+4)
		
		// XFWRITE  SR    15,15 (endmarker => breakpoint)
		/* +xC8	 */	_1B,_FF,
		
		// WTERM    WRTERM  (4),(5)   ... (4) = buffer, (5) = buffer-length
		/* +xCA	 */	_07,_00,                           // CNOP  0,4
		/* +xCC	 */	_50,_40,_A0,_E4,                   // ST    4,DMS0009B         STORE MESSAGE-ADDRESS
		/* +xD0	 */	_92,_01,_A0,_E4,                   // MVI   DMS0009B,X'01'     RESTORE FLAG
		/* +xD4	 */	_40,_50,_A0,_EA,                   // STH   5,DMS0009C+2             STORE LENGTH IN PLIST
		/* +xD8	 */	_45,_10,_A0,_EC,                   // BAL   1,DMS0009E      POINT R1 TO PLIST
		// DMS0009A
		/* +xDC	 */	_E3,_E8,_D7,_D3,_C9,_D5,_40,_40,   // DC   CL8'TYPLIN'
		// DMS0009B
		/* +xE4	 */	_01,_00,_00,_04,                   // DC   X'01',AL3(4)
		// DMS0009C
		/* +xE8	 */	_C2,_00,_00,_01,                   // DC   C'B',X'00',AL2(01)
		// DMS0009E 	
		/* +xEC	 */	_0A,_CA,                           // SVC   202            CALL CMS TO TYPE
		/* +xEE	 */	_00,_02,_00,_F2,                   // DC    AL4(*+4)
		
		// XWTERM   SR    15,15 (endmarker => breakpoint)
		/* +xF2	 */	_1B,_FF,
		
		// LTORG
		/* +xF4	 */	_00,_00,_00,_00,                   // (fills up to fullword)
		/* +xF8	 */	_C5,_D9,_C1,_E2,_C5,_40,_40,_40,   // =CL8'ERASE'
		/* +x100 */	_C6,_C9,_D5,_C9,_E2,_40,_40,_40,   // =CL8'FINIS'
		/* +x108 */	_E2,_E3,_C1,_E3,_C5,_40,_40,_40,   // =CL8'STATE'
		/* +x110 */	_D9,_C4,_C2,_E4,_C6,_40,_40,_40,   // =CL8'RDBUF'
		/* +x118 */	_E6,_D9,_C2,_E4,_C6,_40,_40,_40,   // =CL8'WRBUF'
		// BUF ?required?
		/* +x120 */	_40,_40,_40,_40,_40,_40,_40,_40    // DC CL8' '

		// END
	};
	
	private static final int FSCB_FILENAME = 0x020008;
	private static final int FSCB_FILETYPE = 0x020010;
	private static final int FSCB_FILEMODE = 0x020018;
	private static final int FSCB_RECFM    = 0x020024;
	private static final int FSCB_LRECL    = 0x020020;
	private static final byte[] EMPTY_FILEID = {
			_40,_40,_40,_40,_40,_40,_40,_40,
			_40,_40,_40,_40,_40,_40,_40,_40,
			_A,_1};
	
	private static final int FN_FSERASE = 0x02002E;
	private static final int FN_FSCLOSE = 0x02003E;
	private static final int FN_FSOPEN  = 0x02004E;
	private static final int FN_FSREAD  = 0x020082;
	private static final int FN_FSSTATE = 0x020096;
	private static final int FN_FSWRITE = 0x0200BA;
	private static final int FN_WRTERM  = 0x0200CA;
	
	private static final int CODE_BASE = 0x020000;
	private static final int CODE_LENGTH = CMS_MACRO_INSTRUCTIONS.length;
	private static final int DATA_BASE = 0x021000;
	private static final int DATA_LENGTH = 0x00010000;
	
	private final CPVirtualMachine vm;
	private final Cpu370Bc cpu;
	
	private final byte[] codeBuffer = new byte[CODE_LENGTH];
	private final byte[] dataBuffer = new byte[DATA_LENGTH];
	private final byte[] pswBuffer = new byte[8];
	private final int[] regsBuffer = new int[16];
	private final byte[] pswClear = { _00 , _00 , _00 , _00 , _00 , _00 , _00 , _00 };
	
	private HostIO(CPVirtualMachine vm) {
		this.vm = vm;
		this.cpu = vm.cpu;
	}
	
	private void prepare() {
		this.cpu.addBreakpoints(
				0x02002C, // end FSCB
				0x02003C, // end FERASE
				0x02004C, // end FSCLOSE
				0x020080, // end FSOPEN
				0x020094, // end FSREAD
				0x0200B8, // end FSSTATE
				0x0200C8, // end FSWRITE
				0x0200F2  // end WRTERM
				);
		this.cpu.peekMainMem(CODE_BASE, codeBuffer, 0, CODE_LENGTH);
		this.cpu.peekMainMem(DATA_BASE, dataBuffer, 0, DATA_LENGTH);
		this.cpu.writePswTo(this.pswBuffer, 0);
		for (int i = 0; i < 16; i++) {
			this.regsBuffer[i] = this.cpu.getGPR(i);
		}
		this.cpu.pokeMainMem(CODE_BASE, CMS_MACRO_INSTRUCTIONS, 0, CODE_LENGTH);
	}
	
	private void postpare() {
		this.cpu.resetBreakpoints();
		this.cpu.pokeMainMem(CODE_BASE, codeBuffer, 0, CODE_LENGTH);
		this.cpu.pokeMainMem(DATA_BASE, dataBuffer, 0, DATA_LENGTH);
		try { this.cpu.readPswFrom(pswBuffer, 0); } catch (PSWException e) { }
		for (int i = 0; i < 16; i++) {
			 this.cpu.setGPR(i, this.regsBuffer[i]);
		}
	}
	
	private int execute(int at) {
		return this.execute(at, CODE_BASE, this.cpu.getGPR(5));
	}
	
	private int execute(int at, int r4, int r5) {
		this.cpu.setGPR(10, CODE_BASE);
		this.cpu.setGPR(4, r4);
		this.cpu.setGPR(5, r5);
		try { this.cpu.readPswFrom(pswClear, 0); } catch (PSWException e) { }
		this.cpu.setPswInstructionAddress(at);
		this.vm.run();
		return this.cpu.getGPR(15); // return the CMS functions return code
	}
	
	private void setFscbFileId(EbcdicHandler fn, EbcdicHandler ft, EbcdicHandler fm) {
		this.cpu.pokeMainMem(FSCB_FILENAME, EMPTY_FILEID, 0, EMPTY_FILEID.length);
		
		byte[] src;
		int idx = 0;
		if (fn != null) {
			src = fn.getRawBytes();
			while(idx < fn.getLength() && idx < 8) {
				this.cpu.pokeMainMem(FSCB_FILENAME + idx, Ebcdic.uppercase(src[idx]));
				idx++;
			}
		}
		
		idx = 0;
		if (ft != null) {
			src = ft.getRawBytes();
			while(idx < ft.getLength() && idx < 8) {
				this.cpu.pokeMainMem(FSCB_FILETYPE + idx, Ebcdic.uppercase(src[idx]));
				idx++;
			}
		}
		
		if (fm == null) { return; }
		
		idx = 0;
		src = fm.getRawBytes();
		while(idx < fm.getLength() && idx < 2) {
			this.cpu.pokeMainMem(FSCB_FILEMODE + idx, Ebcdic.uppercase(src[idx]));
			idx++;
		}
	}
	
	public int wrterm(EbcdicHandler text) {
		int textLen = text.getLength();
		if (textLen > DATA_LENGTH) { textLen = DATA_LENGTH; }
		this.cpu.pokeMainMem(DATA_BASE,	text.getRawBytes(),	0, textLen);
		return this.execute(FN_WRTERM, DATA_BASE, textLen);
	}
	
	public int fsstate(EbcdicHandler fn, EbcdicHandler ft, EbcdicHandler fm) {
		this.setFscbFileId(fn, ft, fm);
		return this.execute(FN_FSSTATE);
	}
	
	public int fsstate(String fn, String ft, String fm) {
		return this.fsstate(new EbcdicHandler(fn), new EbcdicHandler(ft), new EbcdicHandler(fm));
	}
	
	public int fserase(EbcdicHandler fn, EbcdicHandler ft, EbcdicHandler fm) {
		this.setFscbFileId(fn, ft, fm);
		return this.execute(FN_FSERASE);
	}
	
	public int fserase(String fn, String ft, String fm) {
		return this.fserase(new EbcdicHandler(fn), new EbcdicHandler(ft), new EbcdicHandler(fm));
	}
	
	public int fsopen(EbcdicHandler fn, EbcdicHandler ft, EbcdicHandler fm, boolean isRecfmF, int lrecl) {
		this.setFscbFileId(fn, ft, fm);
		this.cpu.pokeMainMem(FSCB_RECFM, (isRecfmF) ? _F : _V);
		if (lrecl < 1) {
			this.cpu.pokeMainMem(FSCB_LRECL, (int)1);
		} else if (lrecl > 0x0000FFFF) {
			this.cpu.pokeMainMem(FSCB_LRECL, 0x0000FFFF);
		} else {
			this.cpu.pokeMainMem(FSCB_LRECL, lrecl);
		}
		return this.execute(FN_FSOPEN);
	}
	
	public boolean fscbIsRecfmF() {
		return this.cpu.peekMainMemByte(FSCB_RECFM) == _F;
	}
	
	public int fscbGetLrecl() {
		return this.cpu.peekMainMemInt(FSCB_LRECL);
	}
	
	public int fsopen(String fn, String ft, String fm, boolean isRecfmF, int lrecl) {
		return this.fsopen(new EbcdicHandler(fn), new EbcdicHandler(ft), new EbcdicHandler(fm), isRecfmF, lrecl);
	}
	
	public int fsclose() {
		return this.execute(FN_FSCLOSE);
	}
	
	public int fswrite(byte[] data, int len) {
		if (len < 1 || data == null || data.length < 1) {
			return 0;
		}
		if (len > data.length) { len = data.length; }
		if (len > 0x0000FFFF) { len = 0x0000FFFF; }
		if (this.cpu.peekMainMemByte(FSCB_RECFM) == _V) {
			this.cpu.pokeMainMem(FSCB_LRECL, len);
		}
		this.cpu.pokeMainMem(DATA_BASE, data, 0, len);
		return this.execute(FN_FSWRITE);
	}
	
	public int fsread(byte[] data, int maxLen) {
		int rc = this.execute(FN_FSREAD);
		if (rc != 0) { return -rc; }
		
		if (data == null) { return 0; }
		
		int lenRead = this.cpu.getGPR(0);
		if (lenRead > maxLen) { lenRead = maxLen; }
		if (lenRead > data.length) { lenRead = data.length; }
		this.cpu.peekMainMem(DATA_BASE, data, 0, lenRead);
		return lenRead;
	}

	public static void run(CPVirtualMachine vm, Worker worker) {
		HostIO hostio = new HostIO(vm);
		hostio.prepare();
		try {
			worker.execute(hostio);
		} finally {
			hostio.postpare();
		}
	}
}
