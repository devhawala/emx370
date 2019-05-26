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

package dev.hawala.vm370.cons;

import java.io.IOException;

import dev.hawala.vm370.ebcdic.EbcdicHandler;
import dev.hawala.vm370.mecaff.ByteBuffer;
import dev.hawala.vm370.mecaff.Vm3270Console;
import dev.hawala.vm370.stream3270.AidCode3270;
import dev.hawala.vm370.stream3270.DataOutStream3270;
import dev.hawala.vm370.stream3270.OrderCode3270;

/**
 * Emulation of a CP style layout 3270 screen console for DIAG-x58 display
 * data operations.
 * 
 * This is in fact a MECAFF-fullscreen application having a 80x24 screen
 * with a simulated status (RUNNING etc) in the right bottom corner, an
 * input field on the first column of the 23-th line etc.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public class Screen3270Emulator {

	// the MECAFF-console to be used
	private final Vm3270Console console3270;
	
	// the 3270 output data stream builder borrowed form MECAFF
	private final DataOutStream3270 os = new DataOutStream3270();
	
	// the input / output buffers
	private final ByteBuffer outByteBuf = new ByteBuffer(8192, 512);
	private final ByteBuffer promptByteBuf = new ByteBuffer(256, 128);
	
	// text to put on the input prompt area
	private String inputZonePrefill = null;
	
	// constructor
	public Screen3270Emulator(Vm3270Console console) {
		this.console3270 = console;
	}
	
	// the content of the 24 PF keys
	private EbcdicHandler[] pfKeys = new EbcdicHandler[24];
	
	// set the content of the PF-key 'no'
	public void setPF(int no, EbcdicHandler command) {
		if (no < 0 || no > 23) { return; }
		this.pfKeys[no] = command;
	}
	
	// perform the DIAG-x58 display data operation
	// returns: successfull?
	public boolean display(ByteBuffer data, int atLine, boolean clear) throws IOException {
		if (atLine > 21) {
			// output area has lines 0..21 (22 lines), so writing beyond is suppressed
			return false;
		}
		
		this.inputZonePrefill = null;
		this.currHistory = this.nextHistory;
		
		// prepare 3270 output stream
		this.os.clear();
		boolean inFullscreen = false;
		if (!clear) {
			inFullscreen = this.console3270.acquireFullScreen(true);
		}
		if (!inFullscreen) {
			this.os.cmdEraseWrite(false, true, false);
			inFullscreen = this.console3270.acquireFullScreen(false);
		} else {
			this.os.cmdWrite(false, true, false);
		}
		if (!inFullscreen) {
			return false; // cannot access 3270 screen...
		}
		this.os.setBufferAddress(atLine + 1, 1); // these coordinates are 1-based
		
		// format the lines into our internal buffer, making sure the data fits on the
		// 80x22 output area when starting at line "atLine"
		int remainingLines = 22 - atLine;
		byte[] txt = data.getInternalBuffer();
		int len = data.getLength();
		int curr = 0;
		int col = 0;
		while(curr < len && remainingLines > 0) {
			byte c = txt[curr++];
			if (c == (byte)0x15) {
				if (remainingLines > 1) {
					// fill rest of this output line with blanks
					while(col < 80) {
						this.os.appendEbcdic((byte)0x40);
						col++;
					}
				}
				col = 0;
				remainingLines--;
			} else if (c == OrderCode3270.SF.getCode()) {
				// start field
				this.os.appendEbcdic(c);
				this.os.appendEbcdic(txt[curr++]);
				col++;
			} else if (c == OrderCode3270.SFE.getCode()) {
				// start field extended
				this.os.appendEbcdic(c);
				byte pairCount = txt[curr++];
				this.os.appendEbcdic(c);
				while(pairCount > 0) {
					this.os.appendEbcdic(txt[curr++]);
					this.os.appendEbcdic(txt[curr++]);
					pairCount--;
				}
				col++;
			} else if (c == OrderCode3270.SA.getCode()) {
				// set attribute (works in-place, so not "blank" screen position)
				this.os.appendEbcdic(c);
				this.os.appendEbcdic(txt[curr++]);
				this.os.appendEbcdic(txt[curr++]);
			} else if (c == OrderCode3270.MF.getCode()) {
				// modify field
				// TODO: use the correct command length and "blank" screen position
				this.os.appendEbcdic(c);
				this.os.appendEbcdic(txt[curr++]); // more bytes??
				// col++; // TODO: does MF leave a blank position?????
			} else {
				this.os.appendEbcdic(c);
				col++;
			}
			if (col > 79) {
				col = 0;
				remainingLines--;
			}
		}
		if (remainingLines > 0 && col > 0) {
			// fill rest of the last output line with blanks
			while(col < 80) {
				this.os.appendEbcdic((byte)0x40);
				col++;
			}
		}
		
		// postpare 3270 output stream: create CP-look-alike input zone and status area
		this.os
			.setBufferAddress(22, 80)
			.startField(false, false, false, false, false)
			.insertCursor()
			.repeatToAddress(24, 80, (byte)0x00)
			.setBufferAddress(24, 60)
			.startField(true, false, false, false, false)
			.appendUnicode("Running");
		
		// finalize: display on screen and successfully done
		this.outByteBuf.clear();
		this.os.appendTo(this.outByteBuf);
		this.console3270.writeFullscreen(this.outByteBuf);
		return true;
	}
	
	// display "VM READ" in the status area and initiate the fullsreen read
	// returns: successfull?
	public boolean promptUser() throws IOException {
		// is the screen ready for input (do we own it)?
		if (!this.console3270.mayReadFullScreen()) {
			return false;
		}
		
		// acquire screen ownership
		if (!this.console3270.acquireFullScreen(false)) {
			// screen ownership was lost => re-acquire
			if (!this.console3270.acquireFullScreen(true)) { return false; }
			// clear screen
			this.promptByteBuf.clear();
			this.os.clear().cmdEraseWrite(false, true, false);
			this.os.appendTo(this.promptByteBuf);
			this.console3270.writeFullscreen(this.promptByteBuf);
			// redisplay previous content
			this.console3270.writeFullscreen(this.outByteBuf);
		}
		
		// set new status
		this.os.clear()
			.cmdWrite(false, true, false)
			.setBufferAddress(23, 1)
			.insertCursor()
			.repeatToAddress(24, 59, (byte)0x00)
			.setBufferAddress(24, 61)
			.appendUnicode("Vm Read");
		if (this.inputZonePrefill != null) {
			String line = (this.inputZonePrefill.length() < 132) ? this.inputZonePrefill : this.inputZonePrefill.substring(0, 132);
			this.os
				.setBufferAddress(23,1)
				.appendUnicode(line);
		}
		this.promptByteBuf.clear();
		this.os.appendTo(this.promptByteBuf);
		this.console3270.writeFullscreen(this.promptByteBuf);
		
		// initiate read and successfully done
		this.console3270.readFullScreen(0x7FFFFFFF, 0);
		return true;
	}
	
	// simulation of CPs history buffer
	private String[] cmdHistory = new String[4];
	private int nextHistory = 0;
	private int currHistory = 0;
	
	// process the input from the MECAFF-console 
	// returns: repeat prompt user?
	public boolean handleInput(ByteBuffer input, EbcdicHandler output) {
		byte[] buffer = input.getInternalBuffer();
		int count = input.getLength();
		
		// no input available so far
		output.reset();
		
		// is there enough input (how that?)
		if (count < 3) { return false; }
		
		// process input according to AID code
		AidCode3270 aid = AidCode3270.map(buffer[0]);
		
		// Enter? => return input field content after putting it into command history
		if (aid == AidCode3270.Enter) {
			if (count > 6) { output.appendEbcdic(buffer, 6, count - 6); }
			// DBG System.err.printf("-- Screen3270Emulator.handleInput() ENTER -> '%s'\n", output.getString());
			this.cmdHistory[this.nextHistory++] = output.getString();
			this.nextHistory %= this.cmdHistory.length;
			this.currHistory = this.nextHistory;
			return false;
		}
		
		// PA1? => enter CP
		if (aid == AidCode3270.PA01) {
			output.appendUnicode("#CP");
			// DBG System.err.printf("-- Screen3270Emulator.handleInput() PA1 -> '#CP'\n");
			return false;
		}
		
		// PF-Key?
		//   if PF-key content starts with "IMMED ": send ontent as user input (without IMMED)
		//   if PF-key content starts with "DELAYED ": put content into the input field
		//   if PF-key content is "RETRIEVE": use history to put a previous input into the input field 
		//   else: (ignore)
		int pfIndex = aid.getKeyIndex();
		if (pfIndex < 1 || pfIndex > 24) { return false; }
		// DBG System.err.printf("-- Screen3270Emulator.handleInput() PF %d \n", pfIndex);
		EbcdicHandler pfCmd = this.pfKeys[pfIndex - 1];
		if (pfCmd != null) {
			String cmd = pfCmd.getString();
			if (cmd.startsWith("IMMED ")) {
				if (cmd.length() > 6) { // length of 'IMMED '
					output.appendUnicode(cmd.substring(6));
				}
			} else if (cmd.startsWith("DELAYED ")) {
				if (cmd.length() > 8) {
					this.inputZonePrefill = cmd.substring(8);
				}
				return true; // repeat get user input
			} else if (cmd.equals("RETRIEVE")) {
				int limit = this.currHistory;
				cmd = this.cmdHistory[this.currHistory--];
				if (this.currHistory < 0) { this.currHistory = this.cmdHistory.length - 1; }
				while(cmd == null && this.currHistory != limit) {
					cmd = this.cmdHistory[this.currHistory--];
					if (this.currHistory < 0) { this.currHistory = this.cmdHistory.length - 1; }
				}
				this.inputZonePrefill = cmd;
				return true; // repeat get user input
			}
		}
		
		return false;
	}
}
