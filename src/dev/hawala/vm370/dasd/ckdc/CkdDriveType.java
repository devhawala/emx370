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

package dev.hawala.vm370.dasd.ckdc;

import java.util.Hashtable;

/**
 * Known CKD drive types and their characteristics.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public enum CkdDriveType {
	
	// (geometry data partially taken from DMKDDR.ASSEMBLE )
	
	//        Name      Code     Cyls Hds TrckLen RecCnt CpDevType
	ckd2305(  "2305",   0x230500,  48,  8, 14568,  34, (byte)0x02),
	ckd2305_2("2305-2", 0x230502,  96,  8, 14568,  75, (byte)0x02),
	
	ckd2311(  "2311",   0x231100, 200, 10,  3625,  62, (byte)0x80),
	
	ckd2314(  "2314",   0x231400, 200, 20,  7294,  75, (byte)0x40),
	
	ckd3330(  "3330",   0x333001, 404, 19, 13165, 100, (byte)0x10),
	ckd3330_2("3330-2", 0x333011, 808, 19, 13165, 100, (byte)0x10),
	
	ckd3340(  "3340",   0x334001, 348, 12,  8535,  55, (byte)0x01),
	ckd3340_2("3340-2", 0x334002, 696, 12,  8535,  55, (byte)0x01),
	
	ckd3350(  "3350",   0x335000, 555, 30, 19254, 110, (byte)0x08),
	
	ckd3375(  "3375",   0x337502, 959, 12, 36000, 100, (byte)0x04),
	
	ckd3380(  "3380",   0x338002, 885, 15, 47968, 100, (byte)0x20),
	
	unknown("unknown",  0x000000,   0 , 0,     0,   0, (byte)0x00);

	private final String name;
	private final int code; 
	
	private final int maxCyls;
	private final int heads;
	private final int maxTrackLen;
	private final int maxRecordsPerTrack;
	
	private final byte cpDevClass = (byte)0x04; // Class DASD
	private final byte cpDevType;
	
	private static Hashtable<String,CkdDriveType> name2drive = null;
	private static Hashtable<Integer,CkdDriveType> code2drive = null;
		
	public String getName() { return this.name; }
	public int getCode() { return this.code; }
	public int getMaxCyls() { return this.maxCyls; }
	public int getHeads() { return this.heads; }
	public int getMaxTrackLen() { return this.maxTrackLen; }
	public int getMaxRecordsPerTrack() { return this.maxRecordsPerTrack; }
	public byte getCpDevClass() { return this.cpDevClass; }
	public byte getCpDevType() { return this.cpDevType; }
	
	private CkdDriveType(String name, int code, int maxCyls, int heads, int maxTrackLen, int maxRecordsPerTrack, byte cpDevType) {
		this.name = name;
		this.code = code;
		this.maxCyls = maxCyls;
		this.heads = heads;
		this.maxTrackLen = maxTrackLen;
		this.maxRecordsPerTrack = maxRecordsPerTrack;
		this.cpDevType = cpDevType;
	}
	
	public static CkdDriveType mapName(String which) {
		if (name2drive == null) {
			name2drive  = new Hashtable<String,CkdDriveType>();
			for (CkdDriveType t : CkdDriveType.values()) {
				name2drive.put(t.getName(), t);
			}
		}
		if (name2drive.containsKey(which)) {
			return name2drive.get(which);
		}
		return CkdDriveType.unknown;
	}
	
	public static CkdDriveType mapCode(int code) {
		Integer which = new Integer(code);
		if (code2drive == null) {
			code2drive  = new Hashtable<Integer,CkdDriveType>();
			for (CkdDriveType t : CkdDriveType.values()) {
				code2drive.put(new Integer(t.getCode()), t);
			}
		}
		if (code2drive.containsKey(which)) {
			return code2drive.get(which);
		}
		return CkdDriveType.unknown;
	}
}
