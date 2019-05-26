/*
** This file is part of the external MECAFF process implementation.
** (MECAFF :: Multiline External Console And Fullscreen Facility 
**            for VM/370 R6 SixPack 1.2)
**
** This software is provided "as is" in the hope that it will be useful, with
** no promise, commitment or even warranty (explicit or implicit) to be
** suited or usable for any particular purpose.
** Using this software is at your own risk!
**
** Written by Dr. Hans-Walter Latz, Berlin (Germany), 2011,2012
** Released to the public domain.
*/

package dev.hawala.vm370.mecaff;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import dev.hawala.vm370.Log;
import dev.hawala.vm370.ebcdic.EbcdicHandler;

/**
 * Telnet 3270 binary mode negotiator: attempts to enter the 3270 binary mode
 * with the freshly connected terminal.
 * <p>
 * If this fails, the connection is continued is standard (non-3270) mode.
 * <p>If the 3270 negotiation succeeds, the basic terminal * capabilities are
 * determined by the terminal type. Optionally,  the real terminal characteristics
 * are determined with a WSF-query if the terminal type name indicates that extended
 * 3270 functionality (the WSF-query itself) is supported (and WSF-querying is not
 * deactivated).
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 */
public class TerminalTypeNegotiator {

	protected Log logger = Log.getLogger();
	
	// the connection number (terminal line id) for this connection
	private final int connectionNo;
	
	// the socket of the terminal connection
	private final Socket terminalSideSocket;
	
	// the streams to communicate with the terminal side
	protected InputStream isFromTerm = null;
	protected OutputStream osToTerm = null;
	
	// is the connection already closed?
	protected boolean closed = false;
	
	// the terminal characteristics as determined through the telnet negotiation
	// resp. the WSF-query.
	protected String terminalType;
	protected String terminalLuName = null;
	protected EbcdicHandler ebcdicTerminalType = new EbcdicHandler();
	protected boolean canExtended = false;
	protected boolean canColors = false;
	protected boolean canExtHighLight = false;
	protected boolean canAltScreenSize = false;
	protected int numAltRows = 0;
	protected int numAltCols = 0;
	
	protected boolean isIn320Mode = false;
	
	/**
	 * Construct and initialize the base filter (parent class) for this instance.
	 * @param connectionNo the connection counter value of this filter (for logging).
	 * @param terminalSideSocket the socket connected to the terminal.
	 * @param stickToPredefinedTerminalTypes if <code>false</code>, no WSF-query will be performed
	 *   to get the detailed terminal's capability information.
	 * @param minColorCount number of colors the terminal must at least support to be accepted
	 *   as color terminal.
	 */
	public TerminalTypeNegotiator(
			int connectionNo,
			Socket terminalSideSocket,
			boolean stickToPredefinedTerminalTypes,
			short minColorCount
			) {
		this.connectionNo = connectionNo;
		this.terminalSideSocket = terminalSideSocket;
		
		try {
			this.isFromTerm = this.terminalSideSocket.getInputStream();
			this.osToTerm = this.terminalSideSocket.getOutputStream();
		} catch (Exception e) {
			this.logger.error("unable to open streams from sockets");
			this.closeAll();
			return;
		}
		
		try {
			this.negotiateTerminal3270Mode(stickToPredefinedTerminalTypes, minColorCount);
		} catch (IOException exc) {
			this.terminalType = null;
			this.logger.error("Error while negotiating 3270 protocol with terminal, IOException: ", exc.getMessage());
			return;
		}
	}
	
	public void closeAll() {
		if (this.closed) { return; }
		
		try { if (this.isFromTerm != null) { this.isFromTerm.close(); } } catch(Exception e) {}
		try { if (this.osToTerm != null) { this.osToTerm.close(); } } catch(Exception e) {}
		
		try { if (!this.terminalSideSocket.isClosed()) { this.terminalSideSocket.close(); } } catch(Exception e) {}
		
		this.closed = true;	
	}
	
	/*
	 * Getters for the terminal and connection characteristics as determined on construction.
	 */

	/**
	 * Has the connection (socket) to the terminal been closed due because the
	 * streams on the socket couldn't be opened?
	 */
	public boolean isClosed() { return this.closed; }

	/**
	 * Is the connection in binary 3270 mode?
	 * @return <c>false</c> if the connection is in plain telnet mode ("ascii terminal") or 
	 *  <c>true</c> if it is in binary 3270 block mode. 
	 */
	public boolean isIn320Mode() { return this.isIn320Mode; }
		
	/**
	 * Get the connection number associated with the terminal. 
	 * @return
	 */
	public int getConnectionNo() { return this.connectionNo; }

	/**
	 * Get the socket for the terminal connection.
	 * @return
	 */
	public Socket getTerminalSideSocket() { return this.terminalSideSocket; }

	/**
	 * Get the input stream (terminal to host) for the connection.
	 * @return
	 */
	public InputStream getIsFromTerm() { return this.isFromTerm; }

	/**
	 * Get the output stream (host to terminal) for the connection.
	 * @return
	 */
	public OutputStream getOsToTerm() { return this.osToTerm; }

	/**
	 * Get the terminal type name ("IBM-3278-2" or the like) in iso-latin encoding.
	 * @return
	 */
	public String getTerminalType() { return this.terminalType; }

	/**
	 * Get the terminal type name ("IBM-3278-2" or the like) in EBCDIC encoding.
	 * @return
	 */
	public EbcdicHandler getEbcdicTerminalType() { return this.ebcdicTerminalType; }

	/**
	 * Get the LU-String (Logical Unit) of the connection or <c>null</c> if none is specified.
	 * @return
	 */
	public String getTerminalLuName() { return this.terminalLuName; }

	/**
	 * Does the 3270 terminal support extended 3270 protocol features? 
	 * @return
	 */
	public boolean canExtended() { return this.canExtended; }

	/**
	 * Is the 3270 terminal a color terminal?
	 * @return
	 */
	public boolean canColors() { return this.canColors; }

	/**
	 * Does the 3270 terminal support extended text highlightning?
	 * @return
	 */
	public boolean canExtHighLight() { return this.canExtHighLight; }

	/**
	 * Does the 3270 terminal provide an alternative screen size (beyond standard 80x24)? 
	 * @return
	 */
	public boolean canAltScreenSize() { return this.canAltScreenSize; }

	/**
	 * Get the number of rows of the alternative screen size.
	 * @return
	 */
	public int getNumAltRows() { return this.numAltRows; }

	/**
	 * Get the number of lines of the alternative screen size.
	 * @return
	 */
	public int getNumAltCols() { return this.numAltCols; }
	
	/*
	 * Items for Telnet 3270 negotiation with a connected terminal 
	 */
	
	private static byte[] TN_EOR = { (byte)0xFF, (byte)0xEF };
	
	private static byte[] TN_DO_TERMINAL_TYPE 
							= { (byte)0xFF, (byte)0xFD, (byte)0x18 };
	private static byte[] TN_WILL_TERMINAL_TYPE 
							= { (byte)0xFF, (byte)0xFB, (byte)0x18 };
	
	private static byte[] TN_SE 
						= { (byte)0xFF, (byte)0xF0 };
	private static byte[] TN_SB_SEND_TERMINAL_TYPE 
							= { (byte)0xFF, (byte)0xFA, (byte)0x18, (byte)0x01, (byte)0xFF, (byte)0xF0 }; // incl. TN_SE
	private static byte[] TN_SB_TERMINAL_TYPE_IS 
							= { (byte)0xFF, (byte)0xFA, (byte)0x18, (byte)0x00 }; // + <name> + TN_SE
	
	private static byte[] TN_DO_END_OF_RECORD 
							= { (byte)0xFF, (byte)0xFD, (byte)0x19 };
	private static byte[] TN_WILL_END_OF_RECORD 
							= { (byte)0xFF, (byte)0xFB, (byte)0x19 };
	
	private static byte[] TN_DO_BINARY 
							= { (byte)0xFF, (byte)0xFD, (byte)0x00 };
	private static byte[] TN_WILL_BINARY 
							= { (byte)0xFF, (byte)0xFB, (byte)0x00 };
	
	private boolean pingPong(byte[] request, byte[] response, byte[] buffer, String failMsg) throws IOException {
		this.osToTerm.write(request);
		this.osToTerm.flush();
		int count = this.isFromTerm.read(buffer);
		if (count != response.length) { 
			this.logger.error(failMsg + " - response.length");
			return false; 
		}
		for (int i = 0; i < count; i++) {
			if (buffer[i] != response[i]) { 
				this.logger.error(failMsg + " - response[" + i +"]");
				return false; 
			}
		}
		this.logger.debug(failMsg + " - OK");
		return true;
	}
	
	private boolean isPresent(byte[] what, byte[] in, int at, String failMsg) {
		if (in.length < at || in.length < (at + what.length - 1)) { 
			if (failMsg != null) { this.logger.error(failMsg + " - to short"); }
			return false; 
		}
		for (int i = 0; i < what.length; i++) {
			if (what[i] != in[at+i]) { 
				if (failMsg != null) { this.logger.error(failMsg + " - different at src[" + i + "]"); }
				return false; 
			}
		}
		if (failMsg != null) { this.logger.debug(failMsg + " - present in response at offset " + at); }
		return true;
	}
	
	/**
	 * Perform the complete TN3270 negotiation with the 3270 terminal emulator freshly connected,
	 * entering binary communication mode is successful and do a WSF-query (if not disallowed)
	 * to get the detailed terminal's capability information.
	 * @param stickToPredefinedTerminalTypes if <code>false</code>, no WSF-query will be performed
	 *   to get the detailed terminal's capability information.
	 * @param minColorCount number of colors the terminal must at least support to be accepted
	 *   as color terminal.
	 * @return <true> if the negotiation was successful and binary transmission mode was entered.
	 * @throws IOException
	 */
	protected boolean negotiateTerminal3270Mode(
			boolean stickToPredefinedTerminalTypes,
			short minColorCount) throws IOException {
		byte[] buffer = new byte[256];
		
		// reset status info
		this.isIn320Mode = false;
		
		this.logger.info("Begin of 3270 negotiation with terminal");
		
		// get the terminal type
		if (!this.pingPong(TN_DO_TERMINAL_TYPE, TN_WILL_TERMINAL_TYPE, buffer, "DoTerminalType -> WillTerminalType")) { 
			return false; 
		}
		this.osToTerm.write(TN_SB_SEND_TERMINAL_TYPE);
		this.osToTerm.flush();
		this.logger.debug("sent TN_SB_SEND_TERMINAL_TYPE");
		int termRespLen = this.isFromTerm.read(buffer);
		this.logger.trace("... response.length: " + termRespLen);
		if (termRespLen <= (TN_SB_TERMINAL_TYPE_IS.length + TN_SE.length)) {
			// no space for a terminal name...
			this.logger.info("... response to TN_SB_SEND_TERMINAL_TYPE is to short, aborted !!");
			return false;
		}
		if (!this.isPresent(TN_SB_TERMINAL_TYPE_IS, buffer, 0, "SB_TERMINAL_TYPE_IS")
			|| !this.isPresent(TN_SE, buffer, termRespLen-2, "SE")) {
			return false;
		}
		this.terminalType = new String(buffer, TN_SB_TERMINAL_TYPE_IS.length, termRespLen - TN_SB_TERMINAL_TYPE_IS.length - TN_SE.length);
		this.logger.info("Terminaltype is: " + terminalType);		
		
		// begin end-of-record mode
		if (!this.pingPong(TN_DO_END_OF_RECORD, TN_WILL_END_OF_RECORD, buffer, "DoEndOfRecord -> WillEndOfRecord")
			|| !this.pingPong(TN_WILL_END_OF_RECORD, TN_DO_END_OF_RECORD, buffer, "WillEndOfRecord -> DoEndOfRecord")) {
			return false;
		}
		
		// begin end-of-record mode
		if (!this.pingPong(TN_DO_BINARY, TN_WILL_BINARY, buffer, "DoBinary -> WillBinary")
			|| !this.pingPong(TN_WILL_BINARY, TN_DO_BINARY, buffer, "WillBinary -> DoBinary")) {
			return false;
		}
		
		this.logger.info("End of 3270 negotiation with terminal - 3270 mode entered");
		
		String[] luCheck = this.terminalType.split("@");
		if (luCheck.length > 1) { 
			this.terminalType = luCheck[0];
			this.terminalLuName = luCheck[1];
		}
		
		this.ebcdicTerminalType.appendUnicode(this.terminalType);
		String[] parts = this.terminalType.split("-");
		if (parts.length > 2 && parts[0].equals("IBM") && parts[1].matches("[0-9]+")) {
			this.logger.info("seems to be an 3270-type terminal...");
			if (parts[1].equals("3277")) {
				this.isIn320Mode = true;
			} else if (parts[1].equals("3278")) {
				this.isIn320Mode = true;
				this.canExtHighLight = true;
			} else if (parts[1].equals("3279")) {
				this.isIn320Mode = true;
				this.canExtHighLight = true;
				this.canColors = true;
			}
			if (parts[2].equals("1")) {
				this.isIn320Mode = false; // 12 rows x 40 columns is NOT supported !
			} else if (parts[2].equals("3")) {
				this.canAltScreenSize = true;
				this.numAltCols = 80;
				this.numAltRows = 32;
			} else if (parts[2].equals("4")) {
				this.canAltScreenSize = true;
				this.numAltCols = 80;
				this.numAltRows = 43;
			} else if (parts[2].equals("5")) {
				this.canAltScreenSize = true;
				this.numAltCols = 132;
				this.numAltRows = 27;
			}
			this.canExtended = (parts.length > 3 && parts[3].equals("E"));
		} else if (this.terminalType.equals("IBM-DYNAMIC")) {
			this.canExtended = true;
	    }
		if (!stickToPredefinedTerminalTypes && this.canExtended && this.query3270Properties()) {
			if (this.pUsableHeight > 23 && this.pUsableWidth > 79) {
				this.isIn320Mode = true;
				if (this.pUsableHeight > 24 || this.pUsableWidth > 80) {
					this.canAltScreenSize = true;
					this.numAltCols = this.pUsableWidth;
					this.numAltRows = this.pUsableHeight;
				}
				this.canExtHighLight = (this.pHighlightCount >= 3);
				this.canColors = (this.pColorCount >= minColorCount);
			}
		}
		
		this.logger.info("Terminal props: rows = ", this.numAltRows, ", cols = ", this.numAltCols,
				", colors: ",(this.canColors) ? "yes":"no",
				"(color count:", this.pColorCount, ")",
				", extHighlight: ", (this.canExtHighLight) ? "yes":"no");
		
		if (!this.isIn320Mode) {
			byte[] cmd = { (byte)0xF5, (byte)0x00 }; // EW, no flags
			EbcdicHandler msg = new EbcdicHandler("Terminal type '" + this.terminalType + "' not supported (or -noDynamic is used), sorry");
			this.osToTerm.write(cmd);
			this.osToTerm.write(msg.getRawBytes(), 0, msg.getLength());
			this.osToTerm.write(TN_EOR);
			this.osToTerm.flush();
		}
		return this.isIn320Mode;
	}
	
	/*
	 * Items for the 3270 WSF-Query and interpretation of the response
	 */
	
	private static byte[] EDS_QUERY // 0xFF doubled for telnet escape
	  = { (byte)0xF3, (byte)0x00, (byte)0x05, (byte)0x01, (byte)0xFF, (byte)0xFF, (byte)0x02,
		(byte)0xFF, (byte)0xEF}; 
	
	private ByteBuffer wsfResultCopy = null;
	
	public ByteBuffer getLastWsfResult() {
		return this.wsfResultCopy;
	}
	
	/**
	 * Inner class allowing to read in a response chunk of a WSF-query and
	 * to access the components (plain byte, 8/16 bit unsigned integer).   
	 */
	private static class TnBuffer {
		private final byte[] buffer;
		private int count = 0;
		private int curr = 0;
		
		public TnBuffer(int length) {
			if (length < 16) { length = 16; }
			this.buffer = new byte[length];
		}
		
		public int read(InputStream is) throws IOException {
			int count = is.read(this.buffer);
			if (count < 0) { count = 0; }
			if (count > this.buffer.length) { count = this.buffer.length; }
			this.count = count;
			this.curr = 0;
			return count;
		}
		
		public ByteBuffer getDataCopy() {
			ByteBuffer copy = new ByteBuffer(this.count, 32);
			copy.append(this.buffer, 0, this.count);
			return copy;
		}
		
		public boolean isAtEnd() {
			if (this.curr >= this.count) { return true; }
			if (this.curr < (this.buffer.length - 1)
				&& this.buffer[this.curr] == (byte)0xFF
				&& this.buffer[this.curr+1] == (byte)0xEF) {
				return true; // tn-EOR counts as end-of-content
			}
			return false;
		}
		
		public byte next() { // assumes that no tn-negotiations occur, so only 0xFF-0xFF pairs will show up
			if (this.curr >= this.count) { return (byte)0x00; }
			byte b = this.buffer[this.curr++];
			if (b == (byte)0xFF) { this.curr++; }
			return b;
		}
		
		public int get8bitUnsigned() {
			byte b = this.next();
			if (b >= 0) { return (int)b; }
			return 256 + (int)(b);
		}
		
		public int get16bitUnsigned() {
			return (this.get8bitUnsigned() << 8) + this.get8bitUnsigned();
		}
	}
	
	/**
	 * Do a WSF-query to the terminal and collect the terminal capabilities from the response.
	 * @return <code>true</code> if the query was successful.
	 */
	protected boolean query3270Properties() {
		try {
			this.innerQuery3270Properties();
			return true;
		} catch (IOException exc) {
			return false;
		}
	}
	
	private int processSummary(TnBuffer buffer, int len) {
		this.logger.debug(".. QCode 'Summary', supported QCodes:");
		while(len > 0) {
			byte qcode = buffer.next();
			len--;
			switch (qcode) {
			case (byte)0x84:
				this.logger.debug("   0x84 Alphanumeric partitions");
			    break;
			case (byte)0x99:
				this.logger.debug("   0x99 Auxiliary device");
			    break;
			case (byte)0x9F:
				this.logger.debug("   0x9F Begin/End of file");
			    break;
			case (byte)0x85:
				this.logger.debug("   0x85 Character sets");
			    break;
			case (byte)0x86:
				this.logger.debug("   0x86 Colors");
			    break;
			case (byte)0xAB:
				this.logger.debug("   0xAB Cooperative processing requestor");
			    break;
			case (byte)0x98:
				this.logger.debug("   0x98 Data Chaining");
			    break;
			case (byte)0xA2:
				this.logger.debug("   0xA2 Data Streams");
			    break;
			case (byte)0x91:
				this.logger.debug("   0x91 DBCS-Asia");
			    break;
			case (byte)0xA0:
				this.logger.debug("   0xA0 Device characteristics");
			    break;
			case (byte)0x95:
				this.logger.debug("   0x95 Distributed Data Management");
			    break;
			case (byte)0x97:
				this.logger.debug("   0x97 Document Interchange Architecture");
			    break;
			case (byte)0xB5:
				this.logger.debug("   0xB5 Extendend drawing routine");
			    break;
			case (byte)0x8C:
				this.logger.debug("   0x8C Fields outlining");
			    break;
			case (byte)0x8A:
				this.logger.debug("   0x8A Field validation");
			    break;
			case (byte)0x90:
				this.logger.debug("   0x90 Format presentation");
			    break;
			case (byte)0x94:
				this.logger.debug("   0x94 Format storeage auxiliary device");
			    break;
			case (byte)0xB4:
				this.logger.debug("   0xB4 Grafic color");
			    break;
			case (byte)0xB6:
				this.logger.debug("   0xB6 Grafic symbol sets");
			    break;
			case (byte)0x87:
				this.logger.debug("   0x87 Highlighting");
			    break;
			case (byte)0x9E:
				this.logger.debug("   0x9E IBM auxiliary device");
			    break;
			case (byte)0x82:
				this.logger.debug("   0x82 Image");
			    break;
			case (byte)0xA6:
				this.logger.debug("   0xA6 Implicit partition");
			    break;
			case (byte)0xAA:
				this.logger.debug("   0xAA IOACA auxiliary device");
			    break;
			case (byte)0xB2:
				this.logger.debug("   0xB2 Line type");
			    break;
			case (byte)0x8B:
				this.logger.debug("   0x8B MSR control");
			    break;
			case (byte)0xFF:
				this.logger.debug("   0xFF Null");
			    break;
			case (byte)0x8F:
				this.logger.debug("   0x8F OEM auxiliary device");
			    break;
			case (byte)0xA7:
				this.logger.debug("   0xA7 Paper feed techniques");
			    break;
			case (byte)0x8E:
				this.logger.debug("   0x8E Partition characteristics");
			    break;
			case (byte)0xB3:
				this.logger.debug("   0xB3 Port");
			    break;
			case (byte)0xB1:
				this.logger.debug("   0xB1 Procedure");
			    break;
			case (byte)0x9C:
				this.logger.debug("   0x9C Product defined daat stream");
			    break;
			case (byte)0x88:
				this.logger.debug("   0x88 Reply modes");
			    break;
			case (byte)0xA1:
				this.logger.debug("   0xA1 RPQ names");
			    break;
			case (byte)0x92:
				this.logger.debug("   0x92 Save/restore format");
			    break;
			case (byte)0xB0:
				this.logger.debug("   0xB0 Segment");
			    break;
			case (byte)0xA9:
				this.logger.debug("   0xA9 Settable printer characteristics");
			    break;
			case (byte)0x96:
				this.logger.debug("   0x96 Storage pools");
			    break;
			case (byte)0x80:
				this.logger.debug("   0x80 Summary");
			    break;
			case (byte)0x83:
				this.logger.debug("   0x83 Text partitions");
			    break;
			case (byte)0xA8:
				this.logger.debug("   0xA8 Transparency");
			    break;
			case (byte)0x81:
				this.logger.debug("   0x81 Usable area");
			    break;
			case (byte)0x9A:
				this.logger.debug("   0x9A 3270 IPDS");
			    break;
			default:
				this.logger.debug(String.format("   0x%02d undefined in 3270-EDS-qcode"));
			    break;
			}
		}
		return len;
	}

	// collected geometry information of the terminal
	protected boolean pAdr14bit = false;
	protected boolean pAdr16bit = false;
	protected boolean pUsablesInPels = false;
	protected int pUsableWidth = 0;
	protected int pUsableHeight = 0;
	
	private int processUsableArea(TnBuffer buffer, int len) {
		this.logger.debug(".. QCode 'Usable Area'");
	
		int flags1 = buffer.next(); len--;
		this.logger.debug(String.format("    -> flags1 = %02x\n", flags1));
		int addrModes = flags1 & 0xF0;
		if (addrModes == 1) {
			this.pAdr14bit = true;
		} else if (addrModes == 3) {
			this.pAdr14bit = true;
			this.pAdr16bit = true;
		}
		
		int flags2  = buffer.next(); len--;
		this.logger.debug(String.format("    -> flags1 = %02x\n", flags2));
		this.pUsablesInPels = (flags2 & 0x04) != 0;
		
		this.pUsableWidth = buffer.get16bitUnsigned();
		len -= 2;
		this.pUsableHeight = buffer.get16bitUnsigned();
		len -= 2;
		
		this.logger.debug(String.format("    -> 14 adressing : %s\n", (this.pAdr14bit) ? "yes" : "no"));
		this.logger.debug(String.format("    -> 16 adressing : %s\n", (this.pAdr16bit) ? "yes" : "no"));
		this.logger.debug(String.format("    -> usablesInPels: %s\n", (this.pUsablesInPels) ? "yes" : "no"));
		this.logger.debug(String.format("    -> usable width : %d\n", this.pUsableWidth));
		this.logger.debug(String.format("    -> usable height: %d\n", this.pUsableHeight));
		
		return len;
	}
	
	// collected color count supported by the terminal
	protected int pColorCount = 0;
	
	private int processColors(TnBuffer buffer, int len) {
		this.logger.debug(".. QCode 'Colors'");
	
		int flags = buffer.next(); len--;
		int np = buffer.get8bitUnsigned(); len--;
		
		this.pColorCount = 0;
		this.logger.debug(String.format("    -> flags = 0x%08x\n", flags));
		this.logger.debug(String.format("    -> np = %d\n", np));
		while(np > 0) {
			int cav = buffer.get8bitUnsigned(); len--; np--;
			int ci = buffer.get8bitUnsigned(); len--; np--;
			this.logger.debug(String.format("      -> cav = 0x%02x  --  ci = 0x%02x\n", cav, ci));
			this.pColorCount++;
		}
		
		return len;
	}
	
	// collected number of highlightings supported by the terminal
	protected int pHighlightCount = 0;
	
	private int processHighlighting(TnBuffer buffer, int len) {
		this.logger.debug("   QCode 'Highlighting'");
	
		int np = buffer.get8bitUnsigned(); len--;
		
		this.pHighlightCount = 0;
		this.logger.debug(String.format("    -> np = %d\n", np));
		while(np > 0) {
			int vi = buffer.get8bitUnsigned(); len--;
			int ai = buffer.get8bitUnsigned(); len--;
			np--;
			this.logger.debug(String.format("      -> vi = 0x%02x - ai = 0x%02x\n", vi, ai));
			this.pHighlightCount++;
		}
	
		return len;
	}
	
	private int processAlphaPartitions(TnBuffer buffer, int len) {
		this.logger.debug(".. QCode 'Alphanumeric partitions'");
	
		int na = buffer.get8bitUnsigned(); len--;
		int m = buffer.get16bitUnsigned(); len--; len--;
		
		this.logger.debug(String.format("      -> max partition number    = %d\n", na));
		this.logger.debug(String.format("      -> total partition storage = %d\n", m));
		
		return len;
	}
		
	private void innerQuery3270Properties() throws IOException {
		TnBuffer buffer = new TnBuffer(4096);
		
		this.osToTerm.write(EDS_QUERY);
		this.osToTerm.flush();
		int count = buffer.read(this.isFromTerm);
		this.logger.debug("** query3270Properties(): received ", count, " from term");
		this.wsfResultCopy = buffer.getDataCopy();
		
		byte aid = buffer.next();
		if (aid != (byte)0x88) {
			this.logger.debug("** query3270Properties(): aid is not 0x88, anorting");
			return;
		}
		
		while(!buffer.isAtEnd()) {
			int sfLen = buffer.get16bitUnsigned();
			if (sfLen < 3) { return; } // no room for length and sfid
			byte sfid = buffer.next();
			sfLen -= 3; // consume len and sfid
			
			// interpret structured fields
			if (sfid == (byte)0x81) { // query reply
				byte qcode = buffer.next();
				sfLen--;
				this.logger.debug(String.format("** QCode 0x%02x (len=%d)\n", qcode, sfLen+4));
				switch (qcode) {
				case (byte)0x80:
					sfLen = this.processSummary(buffer, sfLen);
					break;
				case (byte)0x81:
					sfLen = this.processUsableArea(buffer, sfLen);
					break;
				case (byte)0x86:
					sfLen = this.processColors(buffer, sfLen);
					break;
				case (byte)0x87:
					sfLen = this.processHighlighting(buffer, sfLen);
					break;
				case (byte)0x84:
					sfLen = this.processAlphaPartitions(buffer, sfLen);
					break;
				default:
				}
			}
			
			while(sfLen > 0) {
				buffer.next(); // consume rest of structured field
				sfLen--;
			}
		}
	}
}
