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
import java.io.InputStream;

/**
 * Print target writing output to a postscript file.
 * <p>
 * This print sink supports portrait (66 lines with 80 chars) and
 * landscape (66 lines with 132 chars) paper orientation, currently
 * always as A4 paper format.
 * <p>
 * Optionally, each second print line can print a light-green bar
 * under the text, emulation old-fashioned "endless" paper.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 *
 */
public class PrintSinkPostscript extends PrintFileSinkBase {

	public PrintSinkPostscript(String outputDirectory, boolean haveGreenbars, boolean portrait) throws IOException {
		super(outputDirectory, "ps");
		
		InputStream fis = this.getClass().getResourceAsStream("print.ps");
		if (fis == null) {
			throw new IOException("Postscript print macros template 'print.ps' not found");
		}
		try {
			byte[] bytes = new byte[256];
			int count = fis.read(bytes, 0, bytes.length);
			while(count > 0) {
				this.write(bytes, count);
				count = fis.read(bytes, 0, bytes.length);
			}			
		} finally {
			fis.close();
		}
		
		// start postscript print job
		this.println("");
		if (haveGreenbars) { this.println("prt-greenbars"); }
		if (portrait) {
			this.println("prt-a4-portrait");
		} else { 
			this.println("prt-a4-landscape");
		}
		this.println("");
	}

	@Override
	public void printLine(String line) {
		this.write("(");
		for (byte b : line.getBytes()) {
			if (b == '(') {
				this.write("\\(");
			} else if (b == ')') {
				this.write("\\)");
			} else if (b == '\\') {
				this.write("\\\\");
			} else if (b >= 0) {
				this.write(b);
			} else {
				this.printf("\\%03o", 256 + b);
			}
		}
		this.println(") prt-line");
	}

	@Override
	public void spaceLines(int count) {
		if (count < 1) { return; }
		this.printf("%d prt-advance-lines\n", count);
	}

	@Override
	public void skipToChannel(int channel) {
		if (channel < 0 || channel >= this.channelSpacingLines.length) { channel = 0; }
		this.printf("%d prt-skip-to-channel\n", this.channelSpacingLines[channel]);
	}
	
	@Override
	public void close(String closeInfo) {
		this.println("");
		this.println("prt-close");
		this.println("");
		if (closeInfo != null && !closeInfo.isEmpty()) {
			this.printf("%%%%Title: %s\n", closeInfo);
		}
		super.close(closeInfo);
	}
}
