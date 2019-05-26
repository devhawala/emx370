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

/**
 * Print target writing output to an ASCII file with controls
 * bytes (carriage-return. line-feed, form-feed).
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 *
 */
public class PrintSinkAsciiControlFile extends PrintFileSinkBase {
	
	private final byte CR = (byte)0x0D;
	private final byte LF = (byte)0x0A;
	private final byte FF = (byte)0x0C;

	public PrintSinkAsciiControlFile(String outputDirectory) throws IOException {
		super(outputDirectory, "txt");
	}

	@Override
	public void printLine(String line) {
		this.write(line);
		this.write(CR);
	}

	@Override
	public void spaceLines(int count) {
		for (int i = 0; i < count; i++) {
			this.write(LF);
		}
	}

	@Override
	public void skipToChannel(int channel) {
		this.write(FF);
		if (channel >= 0 && channel < this.channelSpacingLines.length) {
			for (int i = 0; i < this.channelSpacingLines[channel]; i++) {
				this.write(LF);
			}
		}
	}
	
}
