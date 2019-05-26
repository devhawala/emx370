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

import java.util.ArrayList;
import java.util.List;

import dev.hawala.vm370.spool.iSpoolDevice;
import dev.hawala.vm370.vm.device.iDeviceIO;
import dev.hawala.vm370.vm.device.iDeviceStatus;
import dev.hawala.vm370.vm.machine.iProcessorEventTracker;

/**
 * Implementation of a 2504 card reader.
 * 
 * see: A24-3510-0_360-25_funcChar_Jan68.pdf, pp. 111++
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 */

public class CardDev2540Reader implements iSpoolDevice {
	
	// the log sink for our log outputs
	private iProcessorEventTracker eventLogger;
	
	// a /dev/null event tracker
	private static class NullTracker implements iProcessorEventTracker {
		public void logLine(String line, Object... args) {};
	}
	
	public CardDev2540Reader(iProcessorEventTracker eventTracker) {
		this.eventLogger = (eventTracker == null) ? new NullTracker() : eventTracker;
	}
	
	// the card sources to process
	private final List<iCardSource> cardSources = new ArrayList<iCardSource>();
	
	// the card source currently being read
	private iCardSource currSource = null;
	
	public synchronized void enqueueCardSource(iCardSource src) {
		if (src == null) {
			throw new IllegalArgumentException("Attempt to enqueue a null iCardSource");
		}
		this.cardSources.add(src);
	}
	
	private int getNextCardSourceIndex() {
		if (this.spoolClass == '*') {
			return (this.cardSources.isEmpty()) ? -1 : 0;
		}
		for (int i = 0; i < this.cardSources.size(); i++) {
			iCardSource s = this.cardSources.get(i);
			if (s.getSpoolClass() == this.spoolClass) {
				return i;
			}
		}
		return -1;
	}
	
	private synchronized boolean hasCardSource() {
		return (this.getNextCardSourceIndex() >= 0);
	}
	
	private synchronized iCardSource getCardSource() {
		int idx = this.getNextCardSourceIndex();
		if (idx < 0) {
			throw new IllegalStateException("No iCardSource available with acceptable spool class");
		}
		return this.cardSources.remove(idx);
	}

	// current CP SPOOL characteristics for this reader
	private char spoolClass = '*';      // spool class
	private boolean spoolCont = false;  // continuous reading/spooling?
	private boolean spoolHold = false;  // reading/spooling on hold?
	private boolean spoolEof = true;    // present end-of-file (in unit-exception condition) at end of a spool file (true) or continue with next spool file (false)?  
	
	@Override
	public boolean isReader() {
		return true;
	}
	
	@Override
	public char getSpoolClass() {
		return this.spoolClass;
	}
	
	@Override
	public void setSpoolClass(char newSpoolClass) {
		this.spoolClass = newSpoolClass;
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
		return this.spoolEof;
	}
	
	@Override
	public void setSpoolEof(boolean newEof) {
		this.spoolEof = newEof;
	}
	
	@Override
	public void setSpoolCopies(int newCopies) {
		// ignored, meaningless on a reader
	}
	
	@Override
	public int getSpoolCopies() {
		return 0;
	}
	
	// temp!
	@Override
	public String getSpoolToUser() {
		return null; // meaningless on a reader
	}
	
	@Override
	public void setSpoolToUser(String newToUser) {
		// ignored, meaningless on a reader
	}
	
	@Override
	public void close(String closeInfo) {
		// ignored, meaningless on a reader
	}
	
	@Override
	public void purge() {
		// ignored, meaningless on a reader
	}
	
	/*
	 * sense information
	 */
	
	// the 2540 (reader) has 1 sense byte
	private byte[] senseBytes = { 0 };
	
	
	// the only "non-hardware"-sense bit (other bits used are hardware-related or ignored here)
	private static final int Sense_CommandReject = (byte)0x80;
	
	// attempt to read, but no iCardSource is enqueued => CSW = ChannelEnd + DeviceEnd + UnitCheck 
	private static final int Sense_InterventionRequired = (byte)0x40;
	
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
	
	private int exitEof() {
		if (this.spoolEof) {
			this.resetSense();
			this.eventLogger.logLine("       => UNIT_EXCEPTION (end-of-file with SPOOL EOF)");
			return iDeviceStatus.UNIT_EXCEPTION;
		} else {
			this.senseBytes[0] = Sense_InterventionRequired;
			this.eventLogger.logLine("       => UNIT_CHECK, DEVICE_END, UNIT_EXCEPTION (end-of-file with SPOOL EOF ~ InterventionRequired)");
			return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END | iDeviceStatus.UNIT_EXCEPTION;
		}
	}
	
	private int exitNoCardSource() {
		this.senseBytes[0] = Sense_InterventionRequired;
		this.eventLogger.logLine("       => UNIT_CHECK, DEVICE_END, UNIT_CHECK [InterventionRequired]");
		return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END | iDeviceStatus.UNIT_EXCEPTION;
	}
	
	private int exitInvCmd() {
		this.senseBytes[0] = Sense_CommandReject;
		this.eventLogger.logLine("       => UNIT_CHECK, DEVICE_END [CommandReject]");
		return iDeviceStatus.UNIT_CHECK | iDeviceStatus.DEVICE_END;
	}
	
	/*
	 * CCW operations
	 */

	@Override
	public void resetState() {
		// nothing to reset...
	}

	@Override
	public int control(int opcode, int dataLength, iDeviceIO memSource) {
		switch(opcode) {

		case 0x03: { // Control no-op
			this.eventLogger.logLine(".. .. CardDev2540Reader: control(NO-OP)");
			return this.exitOk();
		}

		default:
			// all control other commands are ignored, as valid ones are feeder commands (irrelevant) or invalid (ignored)... 
			this.eventLogger.logLine(".. .. CardDev2540Reader: (ignored) control(opcode = 0x%02X, dataLength = %d)", opcode, dataLength);
			return this.exitOk();
		}
	}
	
	private static final int MAX_CARD_LENGTH = 256;
	private byte[] cardBuffer = new byte[80];

	@Override
	public int read(int opcode, int dataLength, iDeviceIO memTarget) {
		// all read commands are treated the same, ignoring feeder-/stacker-controlling modifier bits...
		this.eventLogger.logLine(".. .. CardDev2540Reader: read(opcode = 0x%02X, dataLength = %d)", opcode, dataLength);
		
		// open the next card source if necessary and possible, extending the card buffer if necessary
		if (this.currSource == null) {
			this.eventLogger.logLine("      CardDev2540Reader: switching to next enqueued card source");
			if (!this.hasCardSource()) {
				return this.exitNoCardSource();
			}
			this.currSource = this.getCardSource();
			int newCardLen = this.currSource.getNominalCardLength();
			if (newCardLen > this.cardBuffer.length) {
				this.cardBuffer = new byte[Math.min(newCardLen, MAX_CARD_LENGTH)]; 
			}
		}
		
		// read a card
		int cardLen = this.currSource.readCard(this.cardBuffer, 0, this.cardBuffer.length);
		
		// did we reach the end of the source?
		if (cardLen < 0) {
			this.eventLogger.logLine("      CardDev2540Reader: end of card source");
			
			// close the current source
			this.currSource.close();
			this.currSource = null;
			
			// we reached the end of a "spool file" (== card source), so signal EOF if:
			// -> NOCONT 
			// or 
			// -> CONT but "reader spool" has no file in acceptable spool class (with "reader spool" == enqueued card sources)
			if (!this.spoolCont || !this.hasCardSource()) {
				this.eventLogger.logLine("      CardDev2540Reader: NOCONT or no card source in class %c", this.spoolClass);
				return this.exitEof();
			}
			
			// here we have CONT and at least one more spool class acceptable card source,
			// so continue reading from this card source by doing a recursive call to read(...)
			this.eventLogger.logLine("      CardDev2540Reader: CONT with next card source in class %c", this.spoolClass);
			return this.read(opcode, dataLength, memTarget);
		}
		
		// this was a "normal" card read => transfer data
		memTarget.transfer(this.cardBuffer, 0, cardLen);
		return this.exitOk();
	}

	@Override
	public int write(int opcode, int dataLength, iDeviceIO memSource) {
		this.eventLogger.logLine(".. .. CardDev2540Reader: invalid write(opcode = 0x%02X, dataLength = %d)", opcode, dataLength);
		return this.exitInvCmd();
	}

	@Override
	public int sense(int opcode, int dataLength, iDeviceIO memTarget) {
		switch(opcode)
		{
			
		case 0x04: { // Sense
			this.eventLogger.logLine(".. .. CardDev2540Reader: sense(SENSE) [ dataLength = %d ]", dataLength);
			memTarget.transfer(this.senseBytes, 0, this.senseBytes.length);
			this.resetSense(); // reset at it has been read (where else to reset it?)
			return this.exitOk();
		}
			
		default: // any other...
			this.eventLogger.logLine(".. .. CardDev2540Reader: invalid sense(opcode = 0x%02X, dataLength = %d)", opcode, dataLength);
			return this.exitInvCmd();
			
		}
	}
	
	@Override
	public boolean hasPendingAsyncInterrupt() {
		// does a card reader have attention interrupts...?
		return false;
	}

	@Override
	public void consumeNextAsyncInterrupt() {
		// does a card reader have attention interrupts...?
	}

	@Override
	public void doAttentionInterrupt() {
		// does a card reader have attention interrupts...?
	}

	@Override
	public int getVDevInfo() {
		// CP on VM/370R6 Sixpack 1.2 delivers for a virtual reader:
		// - 0x20...... : UNIT RECORD INPUT DEVICE CLASS
		// - 0x..82.... : for a 2540 CARD PUNCH
		// - 0x....00.. : virtual device status
		// - 0x......00 : virtual device flags
		return 0x20820000;
	}

	@Override
	public int getRDevInfo() {
		// return the value delivered by CP on VM/370R6 Sixpack 1.2 for a virtual reader 
		return 0x00000040;
	}

	@Override
	public byte getSenseByte(int index) {
		if (index == 0) { return this.senseBytes[0]; }
		return (byte)0x00;
	}

	@Override
	public String getCpDeviceTypeName() {
		return "RDR";
	}

	@Override
	public String getCpQueryStatusLine(int asCuu) {
		String statusLine = String.format(
				"RDR  %03X CL %c  %s %s %s      READY", 
				asCuu,
				this.spoolClass,
				(this.spoolCont) ? "  CONT" : "NOCONT",
				(this.spoolHold) ? "  HOLD" : "NOHOLD",
				(this.spoolEof)  ? "  EOF" : "NOEOF"
				);
		return statusLine;
	}

}
