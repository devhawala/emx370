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

package dev.hawala.vm370.vm.machine;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;


/**
 * Representation of a named segment similar to VM/370-CP segments
 * (macro NAMESYS, module DMKSNT, command SAVESYS, diagnose x'64' etc.).
 * 
 * As true virtual memory is unsupported, loading/purging a segment is simulated
 * by copying the segments bytes in to a CPU's main memory resp. clearing (zero-ing
 * out) the corresponding memory area. 
 * 
 * @author Dr. Hans-Walter Latz, Berlin/Germany, 2015
 *
 */
public class NamedSegment {
	
	// segment name
	private final String name;
	
	// main memory address where to load the segment bytes
	private final int loadAt;
	
	// load the CPU state with IPL data from the named segment when loading the segment?
	private final boolean iplable;
	
	// the bytes to be placed in the main memory
	private final byte[] segmentBytes;
	
	// if iplable: the PSW to be loaded for IPL
	private final byte[] iplPsw = new byte[8];
	
	// if iplable: the general register values to be loaded for IPL
	private final int[] gpr = new int[16];
	
	// if iplable: the floating point registers to be loaded for IPL
	// (not yet supported) private final double[] fpr = new double[4];
	
	// if iplable: the control registers to be loaded for IPL
	private final int[] ctlr = new int[16];
	
	// if iplable: the protection key for the pages to be loaded for IPL
	private final byte[] memKeys;
	
	/**
	 * Constructor: define the characteristics of the named segment and prepare its usage.
	 * 
	 * @param name the name of the segment.
	 * @param fn the name of the file containing the segment's data.
	 * @param cpPageCount the size of the segment measured in CP pages (4096 bytes).
	 * @param loadAt the main memory location where the segment is to be loaded.
	 * @param iplable is the segment intended for an IPL (i.e. do the first 4096 bytes
	 *    of the segment file contain valid IPL data like initial PSW, register contents?).   
	 * @throws IllegalArgumentException a parameter ha an invalid or unplausible value
	 * @throws IOException the segment file cannot be read
	 */
	public NamedSegment(String name, String fn, int cpPageCount, int loadAt, boolean iplable)
			throws IllegalArgumentException, IOException {
		// check load parameters for plausibility
		if (name == null || name.length() == 0) {
			throw new IllegalArgumentException("Invalid segment name: " + name);
		}
		if (cpPageCount < 0) {
			throw new IllegalArgumentException("Invalid cpPageCount: " + cpPageCount);
		}
		if (loadAt < 0 || loadAt > 0x00FFFFFF || (loadAt % 4096) != 0) {
			throw new IllegalArgumentException("Invalid loadAt: " + loadAt);
		}
		
		// check the segment file for plausibility
		File f = new File(fn);
		if (!f.exists() || !f.canRead()) {
			throw new FileNotFoundException("Segment-File '" + fn + "' not found or not readable");
		}
		long fSize = f.length();
		if ((fSize % 4096) != 0 || fSize < 8192 || fSize > 0x01000000) {
			throw new FileNotFoundException("Segment-File '" + fn + "' has invalid file length (not multiple of 4096 or less than 1 page)");
		}
		if ((fSize - 4096) < (cpPageCount * 4096)) {
			throw new FileNotFoundException("Segment-File '" + fn + "' is too small for " + cpPageCount + " CP memory pages");
		}
		
		// allocate the segment data arrays
		int segmentSize = cpPageCount * 4096;
		int keyCount = (segmentSize / 4096) * 2; // 1 CP-page (4096) = 2 S/370-pages (2048) 
		this.segmentBytes = new byte[segmentSize];
		this.memKeys = new byte[keyCount];
		
		// load the data from the file (use segmentBytes as buffer for IPL data)
		FileInputStream is = new FileInputStream(f);
		// read ipl data page and get values if this segment can be ipl-ed
		is.read(this.segmentBytes, 0, 4096);
		if (iplable) {
			byte[] src = this.segmentBytes;
			int srcByte = 0;
			// PSW for IPL
			for (int i = 0; i < 8; i++) { this.iplPsw[i] = src[srcByte++]; }
			// general registers
			for (int i = 0; i < 16; i++) {
				int regValue
						= ((src[srcByte++] & 0xFF) << 24)
						+ ((src[srcByte++] & 0xFF) << 16)
						+ ((src[srcByte++] & 0xFF) << 8)
						+ (src[srcByte++] & 0xFF);
				this.gpr[i] = regValue;
			}
			// floating point registers (ignored as no float support yet)
			srcByte += (4 * 8);
			// control registers
			for (int i = 0; i < 16; i++) {
				int regValue
						= ((src[srcByte++] & 0xFF) << 24)
						+ ((src[srcByte++] & 0xFF) << 16)
						+ ((src[srcByte++] & 0xFF) << 8)
						+ (src[srcByte++] & 0xFF);
				this.ctlr[i] = regValue;
			}
			// protection keys for memory
			int keyNo = 0;
			for (int i = 0; i < cpPageCount; i++) {
				byte val = src[srcByte++];
				byte k0 = (byte)((val >> 4) & 0x0F);
				byte k1 = (byte)(val & 0x0F);
				this.memKeys[keyNo++] = k0;
				this.memKeys[keyNo++] = k1;
			}
		}
		// read segment bytes
		is.read(this.segmentBytes);
		// close segment file
		is.close();
		
		// finalize initialization
		this.name = name.trim().toUpperCase();
		this.loadAt = loadAt;
		this.iplable = iplable;
	}
	
	/**
	 * Get the name of the segment.
	 * @return the name of the segment.
	 */
	public String getName() { return this.name; }
	
	/**
	 * Copy the segments content into the main memory of the given CPU, possibly loading
	 * the CPU's state for an IPL with this segment.
	 *  
	 * @param cpu the CPU where to load the segment.
	 * @param doIpl is the CPU state to be initalized for IPL if the segment is iplable?
	 * @throws PSWException the loaded CPU state is invalid for this CPU.
	 */
	public void loadSegment(Cpu370Bc cpu, boolean doIpl) throws PSWException {
		cpu.pokeMainMem(this.loadAt, this.segmentBytes, 0, this.segmentBytes.length);
		if (this.iplable && doIpl) {
			// load general registers
			for (int i = 0; i < 16; i++) { cpu.setGPR(i, this.gpr[i]); }
			// (currently ignored) load FPRs and control registers
			// load PSW for IPL
			cpu.readPswFrom(this.iplPsw, 0);
		}
	}
	
	/**
	 * Clear the main memory area occupied by the segment in the given CPU.
	 *  
	 * @param cpu the CPU where to clear the segments load area.
	 */
	public void purgeSegmentSpace(Cpu370Bc cpu) {
		cpu.clearMainMem(this.loadAt, this.segmentBytes.length);
	}
	
	public int getFirstMemAddress() {
		return this.loadAt;
	}
	
	public int getLastMemAddress() {
		return this.loadAt + this.segmentBytes.length - 1;
	}
	
	public boolean canIpl() { return this.iplable; }

}
