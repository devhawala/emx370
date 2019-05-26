/*
** This file is part of the emx370 emulator.
**
** This software is provided "as is" in the hope that it will be useful,
** with no promise, commitment or even warranty (explicit or implicit)
** to be suited or usable for any particular purpose.
** Using this software is at your own risk!
**
** Written by Dr. Hans-Walter Latz, Berlin (Germany), 2016
** Released to the public domain.
*/

package dev.hawala.vm370.print;

import dev.hawala.vm370.ebcdic.EbcdicHandler;
import dev.hawala.vm370.spool.iSpoolDevice;
import dev.hawala.vm370.vm.device.iDeviceIO;
import dev.hawala.vm370.vm.device.iDeviceStatus;
import dev.hawala.vm370.vm.machine.iProcessorEventTracker;

/**
 * Implementation of a 1403 printer.
 * 
 * see: A24-3510-0_360-25_funcChar_Jan68, pp. 39++
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 */

public class PrintDev1403 implements iSpoolDevice {
	
	// the log sink for our log outputs
	private iProcessorEventTracker eventLogger;
	
	// a /dev/null event tracker
	private static class NullTracker implements iProcessorEventTracker {
		public void logLine(String line, Object... args) {};
	}
	
	// immutable data for this printer (as owned by this VM)
	private final String username;
	private final String outputDirectory;
	
	// where we are currently printing to
	private iPrintSink currentSink;
	
	public PrintDev1403(iProcessorEventTracker eventTracker, String username, String outputDirectory) {
		this.eventLogger = (eventTracker == null) ? new NullTracker() : eventTracker;
		this.username = username;
		this.outputDirectory = outputDirectory;
		
		// the current sink == null => not in use
		this.currentSink = null;
		
		// initialize character map
		this.resetCharMap();
	}
	
	private iPrintSink getDefaultSink() {
		try {
			 return new PrintSinkAsciiControlFile(this.outputDirectory);
		} catch(Exception e) {
			System.out.printf("** PrintDev1403: unable to create simple print sink :: %s", e.getLocalizedMessage());
			return new iPrintSink() {
				@Override public void printLine(String line) { }
				@Override public void spaceLines(int count) { }
				@Override public void skipToChannel(int channel) { }
				@Override public void close(String closeInfo) { }
				@Override public void purge() { }
				@Override public iPrintSink channelSpacingLines(int... lineOffsets) { return this; }
			};
		}
	}
	
	// current CP SPOOL characteristics for this printer
	private char spoolClass = 'A';      // spool class
	private boolean spoolCont = false;  // continuous printing/spooling?
	private boolean spoolHold = false;  // printing/spooling on hold?
	private int spoolCopies = 1;        // (ignored) copies to print
	private String spoolToUser = null;  // (ignored) printer spools to this user's reader resp. if null: prints for owning user of this device
	
	@Override
	public boolean isReader() {
		return false;
	}
	
	@Override
	public char getSpoolClass() {
		return this.spoolClass;
	}
	
	@Override
	public void setSpoolClass(char newSpoolClass) {
		this.spoolClass = newSpoolClass;
	}
	
	private void ensureSink() {
		if (this.currentSink != null) { return; }
		if (Character.isDigit(this.spoolClass)) {
			this.userBoxMap();
		} else {
			this.resetCharMap();
		}
		try {
			switch(this.spoolClass) {
			
			case 'A':
				this.currentSink = this.getDefaultSink();
				break;
				
			case 'B':
				this.currentSink = new PrintSinkAsciiAsaFile(this.outputDirectory);
				break;
				
			case 'C':
			case '0':
				this.currentSink = new PrintSinkPostscript(this.outputDirectory, true, true);
				break;
				
			case 'D':
			case '1':
				this.currentSink = new PrintSinkPostscript(this.outputDirectory, true, false);
				break;
				
			case 'E':
			case '2':
				this.currentSink = new PrintSinkPostscript(this.outputDirectory, false, true);
				break;
				
			case 'F':
			case '3':
				this.currentSink = new PrintSinkPostscript(this.outputDirectory, false, false);
				break;
				
			case 'G':
			case '4':
				this.currentSink = new PrintSinkPdf(this.outputDirectory, true, true);
				break;
				
			case 'H':
			case '5':
				this.currentSink = new PrintSinkPdf(this.outputDirectory, true, false);
				break;
				
			case 'I':
			case '6':
				this.currentSink = new PrintSinkPdf(this.outputDirectory, false, true);
				break;
				
			case 'J':
			case '7':
				this.currentSink = new PrintSinkPdf(this.outputDirectory, false, false);
				break;
				
			default:
				this.currentSink = this.getDefaultSink();
				break;
				
			}
		} catch (Exception e) {
			this.resetCharMap();
			this.currentSink = this.getDefaultSink();
		}
	}
	
	@Override
	public boolean isSpoolCont() {
		return this.spoolCont;
	}
	
	@Override
	public void setSpoolCont(boolean newCont) {
		this.spoolCont = newCont;
	}
	
	@Override
	public boolean isSpoolHold() {
		return this.spoolHold;
	}
	
	@Override
	public void setSpoolHold(boolean newHold) {
		this.spoolHold = newHold;
	}
	
	@Override
	public boolean isSpoolEof() {
		return false;
	}
	
	@Override
	public void setSpoolEof(boolean newEof) {
		// ignored, meaningless on a printer
	}
	
	@Override
	public void setSpoolCopies(int newCopies) {
		this.spoolCopies = (newCopies < 1) ? 1 : (newCopies > 99) ? 99 : newCopies; 
	}
	
	@Override
	public int getSpoolCopies() {
		return this.spoolCopies;
	}
	
	// temp!
	@Override
	public String getSpoolToUser() {
		return this.spoolToUser;
	}
	
	// temp!
	@Override
	public void setSpoolToUser(String newToUser) {
		this.spoolToUser = newToUser;
	}
	
	@Override
	public void close(String closeInfo) {
		if (this.currentSink == null) { return; }
		this.currentSink.close(closeInfo);
		this.currentSink = null;
	}
	
	@Override
	public void purge() {
		if (this.currentSink == null) { return; }
		this.currentSink.purge();
		this.currentSink = null;
	}
	
	// the 1403 has 1 sense byte
	private byte[] senseBytes = { 0 };
	
	// the only "non-hardware"-sense bit (other bits used are hardware-related or ignored here)
	private static final int Sense_CommandReject = (byte)0x80;
	
	private void resetSense() {
		this.senseBytes[0] = 0x00;
	}
	
	private int exitOk() {
		this.eventLogger.logLine("       => DEVICE_END");
		return iDeviceStatus.OK | iDeviceStatus.DEVICE_END;
	}
	
	private int exitInvCmd() {
		this.senseBytes[0] = Sense_CommandReject;
		this.eventLogger.logLine("       => UNIT_CHECK, DEVICE_END [CommandReject]");
		return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
	}
	

	@Override
	public void resetState() {
		// nothing to do
	}

	@Override
	public int control(int opcode, int dataLength, iDeviceIO memSource) {
		switch(opcode)
		{
			
		case 0x03: { // Control no-op
			this.eventLogger.logLine(".. .. PrintDev1403: control(NO-OP)");
			return this.exitOk();
		}

		case 0x0B: { // space 1 line
			this.spaceLines(1);
			return this.exitOk();
		}
		case 0x13: { // space 2 lines
			this.spaceLines(2);
			return this.exitOk();
		}
		case 0x1B: { // space 3 lines
			this.spaceLines(3);
			return this.exitOk();
		}
		case 0x8B: { // skip to channel 1
			this.skipToChannel(1);
			return this.exitOk();
		}
		case 0x93: { // skip to channel 2
			this.skipToChannel(2);
			return this.exitOk();
		}
		case 0x9B: { // skip to channel 3
			this.skipToChannel(3);
			return this.exitOk();
		}
		case 0xA3: { // skip to channel 4
			this.skipToChannel(4);
			return this.exitOk();
		}
		case 0xAB: { // skip to channel 5
			this.skipToChannel(5);
			return this.exitOk();
		}
		case 0xB3: { // skip to channel 6
			this.skipToChannel(6);
			return this.exitOk();
		}
		case 0xBB: { // skip to channel 7
			this.skipToChannel(7);
			return this.exitOk();
		}
		case 0xC3: { // skip to channel 8
			this.skipToChannel(8);
			return this.exitOk();
		}
		case 0xCB: { // skip to channel 9
			this.skipToChannel(9);
			return this.exitOk();
		}
		case 0xD3: { // skip to channel 10
			this.skipToChannel(10);
			return this.exitOk();
		}
		case 0xDB: { // skip to channel 11
			this.skipToChannel(11);
			return this.exitOk();
		}
		case 0xE3: { // skip to channel 12
			this.skipToChannel(12);
			return this.exitOk();
		}
			
		default: // any other...
			this.eventLogger.logLine(".. .. PrintDev1403: invalid control(opcode = 0x%02X, dataLength = %d)", opcode, dataLength);
			return this.exitInvCmd();
			
		}
	}

	@Override
	public int read(int opcode, int dataLength, iDeviceIO memTarget) {
		this.eventLogger.logLine(".. .. PrintDev1403: invalid read(opcode = 0x%02X, dataLength = %d)", opcode, dataLength);
		return this.exitInvCmd();
	}

	@Override
	public int write(int opcode, int dataLength, iDeviceIO memSource) {
		switch(opcode) {
		case 0x01: { // write line without spacing after print
			this.printLine(memSource, 0);
			return this.exitOk();
		}
		case 0x09: { // write line, spacing 1 line after print
			this.printLine(memSource, 1);
			return this.exitOk();
		}
		case 0x11: { // write line, spacing 2 lines after print
			this.printLine(memSource, 2);
			return this.exitOk();
		}
		case 0x19: { // write line, spacing 3 lines after print
			this.printLine(memSource, 3);
			return this.exitOk();
		}
		case 0x89: { // write line, then skip to channel 1
			this.printLine(memSource, 1);
			this.skipToChannel(1);
			return this.exitOk();
		}
		case 0x91: { // write line, then skip to channel 2
			this.printLine(memSource, 1);
			this.skipToChannel(2);
			return this.exitOk();
		}
		case 0x99: { // write line, then skip to channel 3
			this.printLine(memSource, 1);
			this.skipToChannel(3);
			return this.exitOk();
		}
		case 0xA1: { // write line, then skip to channel 4
			this.printLine(memSource, 1);
			this.skipToChannel(4);
			return this.exitOk();
		}
		case 0xA9: { // write line, then skip to channel 5
			this.printLine(memSource, 1);
			this.skipToChannel(5);
			return this.exitOk();
		}
		case 0xB1: { // write line, then skip to channel 6
			this.printLine(memSource, 1);
			this.skipToChannel(6);
			return this.exitOk();
		}
		case 0xB9: { // write line, then skip to channel 7
			this.printLine(memSource, 1);
			this.skipToChannel(7);
			return this.exitOk();
		}
		case 0xC1: { // write line, then skip to channel 8
			this.printLine(memSource, 1);
			this.skipToChannel(8);
			return this.exitOk();
		}
		case 0xC9: { // write line, then skip to channel 9
			this.printLine(memSource, 1);
			this.skipToChannel(9);
			return this.exitOk();
		}
		case 0xD1: { // write line, then skip to channel 10
			this.printLine(memSource, 1);
			this.skipToChannel(10);
			return this.exitOk();
		}
		case 0xD9: { // write line, then skip to channel 11
			this.printLine(memSource, 1);
			this.skipToChannel(11);
			return this.exitOk();
		}
		case 0xE1: { // write line, then skip to channel 12
			this.printLine(memSource, 1);
			this.skipToChannel(12);
			return this.exitOk();
		}
		
		default:
			this.eventLogger.logLine(".. .. PrintDev1403: invalid write(opcode = 0x%02X, dataLength = %d)", opcode, dataLength);
			return this.exitInvCmd();
		}
	}
	
	private final byte[] charMap = new byte[256];
	
	private void resetCharMap() {
		for (int b = 0; b < 256; b++) {
			charMap[b] = (byte)b;
		}
	}
	
	private final static byte CROSS = (byte)0x4E; // +
	private final static byte VERT  = (byte)0x4F; // |
	private final static byte HOR   = (byte)0x60; // -
	
	private void userBoxMap() {
		this.charMap[0xAC] = CROSS;
		this.charMap[0xBC] = CROSS;
		this.charMap[0xAB] = CROSS;
		this.charMap[0xBB] = CROSS;
		
		this.charMap[0x6A] = VERT;
		
		this.charMap[0xBF] = HOR;		
	}
	
	private void map(int len) {
		for (int i = 0; i < len; i++) {
			this.lineBuffer[i] = this.charMap[this.lineBuffer[i] & 0xFF];
		}
	}
	
	
	private final byte[] lineBuffer = new byte[133];
	private final EbcdicHandler ebcdicBuffer = new EbcdicHandler(133);
	private void printLine(iDeviceIO memSource, int lineFeed) {
		this.ensureSink();
		
		int count = memSource.transfer(this.lineBuffer, 0, this.lineBuffer.length);
		int strLength = (count < 0) ?  this.lineBuffer.length + count : this.lineBuffer.length;
		this.map(strLength);
		this.ebcdicBuffer.reset().appendEbcdic(this.lineBuffer, 0, strLength);
		
		// System.out.printf("PRT(%s,+%d)[%03d]>>%s<<\n", this.username, lineFeed, strLength, this.ebcdicBuffer.getString());
		this.currentSink.printLine(this.ebcdicBuffer.getString());
		if (lineFeed > 0) {
			this.currentSink.spaceLines(lineFeed);
		}
	}
	
	private void spaceLines(int count) {
		this.ensureSink();
		
		// System.out.printf("PRT(%s) -- space %d lines\n", this.username, count);
		this.currentSink.spaceLines(count);
	}
	
	private void skipToChannel(int channel) {
		this.ensureSink();
		
		// System.out.printf("PRT(%s) -- skip to channel %d\n", this.username, channel);
		this.currentSink.skipToChannel(channel);
	}

	@Override
	public int sense(int opcode, int dataLength, iDeviceIO memTarget) {
		switch(opcode)
		{
			
		case 0x04: { // Sense
			this.eventLogger.logLine(".. .. PrintDev1403: sense(SENSE) [ dataLength = %d ]", dataLength);
			memTarget.transfer(this.senseBytes, 0, this.senseBytes.length);
			this.resetSense(); // reset at it has been read (where else to reset it?)
			return this.exitOk();
		}
			
		default: // any other...
			this.eventLogger.logLine(".. .. PrintDev1403: invalid sense(opcode = 0x%02X, dataLength = %d)", opcode, dataLength);
			return this.exitInvCmd();
			
		}
	}
	
	@Override
	public boolean hasPendingAsyncInterrupt() {
		// a printer does not have attention interrupts
		return false;
	}

	@Override
	public void consumeNextAsyncInterrupt() {
		// a printer does not have attention interrupts
	}

	@Override
	public void doAttentionInterrupt() {
		// a printer does not have attention interrupts
	}

	@Override
	public int getVDevInfo() {
		// CP on VM/370R6 Sixpack 1.2 delivers for a virtual printer:
		// - 0x10...... : UNIT RECORD OUTPUT DEVICE CLASS
		// - 0x..41.... : for a 1403 PRINTER
		// - 0x....00.. : virtual device status
		// - 0x......00 : virtual device flags
		return 0x10410000;
	}

	@Override
	public int getRDevInfo() {
		// return the value delivered by CP on VM/370R6 Sixpack 1.2 for a virtual printer 
		return 0x00000040;
	}

	@Override
	public byte getSenseByte(int index) {
		if (index == 0) { return this.senseBytes[0]; }
		return (byte)0x00;
	}

	@Override
	public String getCpDeviceTypeName() {
		return "PRT";
	}

	@Override
	public String getCpQueryStatusLine(int asCuu) {
		String statusLines = String.format(
				"PRT  %03X CL %c  %s %s COPY %02d    READY\n" +
				"     %03X %s %-8s DIST %-8s",
				asCuu,
				this.spoolClass,
				((this.spoolCont) ? "CONT  ": "NOCONT"),
				((this.spoolHold) ? "HOLD  ": "NOHOLD"),
				this.spoolCopies,
				asCuu,
				((this.spoolToUser == null) ? "FOR" : "TO "),
				((this.spoolToUser == null) ? this.username : this.spoolToUser),
				this.username
				);
		return statusLines;
	}

}
