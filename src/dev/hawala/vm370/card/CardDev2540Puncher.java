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

package dev.hawala.vm370.card;

import dev.hawala.vm370.spool.iSpoolDevice;
import dev.hawala.vm370.vm.device.iDeviceIO;
import dev.hawala.vm370.vm.device.iDeviceStatus;
import dev.hawala.vm370.vm.machine.iProcessorEventTracker;

/**
 * Implementation of a 2504 card puncher.
 * 
 * see: A24-3510-0_360-25_funcChar_Jan68.pdf, pp. 111++
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 */
public class CardDev2540Puncher implements iSpoolDevice {
	
	// the log sink for our log outputs
	private iProcessorEventTracker eventLogger;
	
	// a /dev/null event tracker
	private static class NullTracker implements iProcessorEventTracker {
		public void logLine(String line, Object... args) {};
	}
	
	// immutable data for this printer (as owned by this VM)
	private final String username;
	private final String outputDirectory;
	
	// the current punch sink
	private iCardSink currentSink;
	
	public CardDev2540Puncher(iProcessorEventTracker eventTracker, String username, String outputDirectory) {
		this.eventLogger = (eventTracker == null) ? new NullTracker() : eventTracker;
		this.username = username;
		this.outputDirectory = outputDirectory;
		
		// the current sink == null => not in use
		this.currentSink = null;
	}
	
	private iCardSink getDefaultSink() {
		try {
			return new CardSinkAsciiFile(this.outputDirectory);
		} catch (Exception e) {
			return new iCardSink() {
				@Override public void writeCard(byte[] buffer, int offset, int length) {}
				@Override public void close(String closeInfo) {}
				@Override public void purge() {}				
			};
		}
	}
	
	// current CP SPOOL characteristics for this printer
	private char spoolClass = 'A';      // spool class
	private boolean spoolCont = false;  // continuous punching/spooling?
	private boolean spoolHold = false;  // punching/spooling on hold?
	private int spoolCopies = 1;        // (ignored) copies to punch
	private String spoolToUser = null;  // (ignored) puncher spools to this user's reader resp. if null: punches for owning user of this device
	
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
		try {
			if (this.spoolClass >= '0' && this.spoolClass <= '9') {
				this.currentSink = new CardSinkEbcdicFile(this.outputDirectory);
			} else  {
				this.currentSink = new CardSinkAsciiFile(this.outputDirectory);
			}
		} catch (Exception e) {
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
		// ignored, meaningless on a puncher
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
	
	/*
	 * sense data
	 */
	
	private byte[] senseBytes = { 0 };
	
	// sense byte values
	private static final byte Sense_CommandReject        = (byte)0x80;
	
	private void resetSense() {
		this.senseBytes[0] = 0x00;
	}
	
	/*
	 * CSW bits
	 */
	
	private int exitOk() {
		this.resetSense();
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
		// nothing to reset..
	}

	@Override
	public int control(int opcode, int dataLength, iDeviceIO memSource) {
		switch(opcode) {

		case 0x03: { // Control no-op
			this.eventLogger.logLine(".. .. CardDev2540Puncher: control(NO-OP)");
			return this.exitOk();
		}

		default:
			// all control other commands are ignored, as valid ones are feeder commands (irrelevant) or invalid (ignored)... 
			this.eventLogger.logLine(".. .. CardDev2540Puncher: (ignored) control(opcode = 0x%02X, dataLength = %d)", opcode, dataLength);
			return this.exitOk();
		}
	}

	@Override
	public int read(int opcode, int dataLength, iDeviceIO memTarget) {
		this.eventLogger.logLine(".. .. CardDev2540Puncher: invalid read(opcode = 0x%02X, dataLength = %d)", opcode, dataLength);
		return this.exitInvCmd();
	}

	private byte[] cardBuffer = new byte[iCardSink.CARDLENGTH];
	
	@Override
	public int write(int opcode, int dataLength, iDeviceIO memSource) {
		switch(opcode)
		{
		
		// WRITE, FEED, SELECT STACKER
		case 0x01:   // stacker P1
	    case 0x41:   // stacker P2
	    case 0x81: { // stacker RP3
			this.eventLogger.logLine(".. .. CardDev2540Puncher: write [ 0x%02X, dataLength = %d ]", opcode, dataLength);
			
			this.ensureSink();
			
			int count = memSource.transfer(this.cardBuffer, 0, this.cardBuffer.length);
			int cardLength = (count < 0) ?  this.cardBuffer.length + count : this.cardBuffer.length;
			this.currentSink.writeCard(this.cardBuffer, 0, cardLength);
			
			return this.exitOk();
		}
			
		default: // any other...
			this.eventLogger.logLine(".. .. CardDev2540Puncher: invalid write(opcode = 0x%02X, dataLength = %d)", opcode, dataLength);
			return this.exitInvCmd();
			
		}
	}

	@Override
	public int sense(int opcode, int dataLength, iDeviceIO memTarget) {
		switch(opcode)
		{
			
		case 0x04: { // Sense
			this.eventLogger.logLine(".. .. CardDev2540Puncher: sense(SENSE) [ dataLength = %d ]", dataLength);
			memTarget.transfer(this.senseBytes, 0, this.senseBytes.length);
			this.resetSense(); // reset at it has been read (where else to reset it?)
			return this.exitOk();
		}
			
		default: // any other...
			this.eventLogger.logLine(".. .. CardDev2540Puncher: invalid sense(opcode = 0x%02X, dataLength = %d)", opcode, dataLength);
			return this.exitInvCmd();
			
		}
	}
	
	@Override
	public boolean hasPendingAsyncInterrupt() {
		// no Attention interrupts on a puncher
		return false;
	}

	@Override
	public void consumeNextAsyncInterrupt() {
		// no Attention interrupts on a puncher
	}

	@Override
	public void doAttentionInterrupt() {
		// no Attention interrupts on a puncher
	}

	@Override
	public int getVDevInfo() {
		// CP on VM/370R6 Sixpack 1.2 delivers for a virtual puncher:
		// - 0x10...... : UNIT RECORD OUTPUT DEVICE CLASS
		// - 0x..82.... : for a 2540 CARD PUNCH
		// - 0x....00.. : virtual device status
		// - 0x......00 : virtual device flags
		return 0x10820000;
	}

	@Override
	public int getRDevInfo() {
		// return the value delivered by CP on VM/370R6 Sixpack 1.2 for a virtual puncher 
		return 0x00000040;
	}

	@Override
	public byte getSenseByte(int index) {
		if (index == 0) { return this.senseBytes[0]; }
		return (byte)0x00;
	}

	@Override
	public String getCpDeviceTypeName() {
		return "PUN";
	}

	@Override
	public String getCpQueryStatusLine(int asCuu) {
		String statusLines = String.format(
				"PUN  %03X CL %c  %s %s COPY %02d    READY\n" +
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
