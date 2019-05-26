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

import java.io.IOException;

import dev.hawala.vm370.spool.CloseableDiskFileSink;

/**
 * Abstract printer target, implementing the common items, mainly
 * the file I/O operations for a print file to be written to the
 * user's output directory.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 *
 */
public abstract class PrintFileSinkBase extends CloseableDiskFileSink implements iPrintSink {
	
	private static int MAX_LINES_PER_PAGE = 66;
	protected final int[] channelSpacingLines = { 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0 };
	
	public PrintFileSinkBase(String outputDirectory, String extension) throws IOException {
		super(outputDirectory, "prt", extension);
	}

	@Override
	public iPrintSink channelSpacingLines(int... lineOffsets) {
		int currentChannel = 0;
		for (int offset : lineOffsets) {
			if (offset >= 0 && offset < (MAX_LINES_PER_PAGE - 1)) {
				this.channelSpacingLines[currentChannel] = offset;
			}
			currentChannel++;
			if (currentChannel >= this.channelSpacingLines.length) {
				return this;
			}
		}
		return this;
	}
	
	protected void write(String s) {
		if (this.sink == null) { return; }
		this.sink.print(s);
	}
	
	protected void write(char c) {
		if (this.sink == null) { return; }
		this.sink.print(c);
	}
	
	protected void write(byte b) {
		if (this.sink == null) { return; }
		this.sink.write(b);
	}
	
	protected void write(byte[] bytes) {
		if (this.sink == null) { return; }
		try {
			this.sink.write(bytes);
		} catch (IOException e) {
			// ignored
		}
	}
	
	protected void write(byte[] bytes, int count) {
		if (this.sink == null) { return; }
		this.sink.write(bytes, 0, count);
	}
	
	protected void println(String s) {
		if (this.sink == null) { return; }
		this.sink.println(s);
	}
	
	protected void printf(String s, Object... parameters) {
		if (this.sink == null) { return; }
		this.sink.printf(s, parameters);
	}
}
