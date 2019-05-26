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
 * Print target writing output to an ASCII file with an
 * additional first column containing an ASA control character
 * (blank = new-line, + = overwrite, 1 = new-page etc.).
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 *
 */
public class PrintSinkAsciiAsaFile extends PrintFileSinkBase {
	
	private static final char[] channelControlChars = { '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C' };
	
	private boolean writeControlChar = true;

	public PrintSinkAsciiAsaFile(String outputDirectory) throws IOException {
		super(outputDirectory, "txt");
	}

	@Override
	public void printLine(String line) {
		// no spacing / channel-movement so far? => then overwrite
		if (this.writeControlChar) { this.write('+'); } // Suppress space before printing
		this.println(line);
		this.writeControlChar = true;
	}

	@Override
	public void spaceLines(int count) {
		if (!this.writeControlChar) { 
			this.println("");
			this.writeControlChar = true;
		}
		if (count < 1) { return; }
		while(count > 3) {
			this.println("-"); // Space 3 lines before printing
			count -= 3;
		}
		if (count == 3) {
			this.write('-'); // Space 3 lines before printing
		} else if (count == 2) {
			this.write('0'); // Space 2 lines before printing
		} else {
			this.write(' '); // Space 1 line before printing
		}
		this.writeControlChar = false;
	}

	@Override
	public void skipToChannel(int channel) {
		if (!this.writeControlChar) { this.println(""); }
		if (channel < 0 || channel >= channelControlChars.length) {
			this.write('1');
		} else {
			this.write(channelControlChars[channel]);
		}
		this.writeControlChar = false;
	}
}
