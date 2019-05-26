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

import dev.hawala.vm370.Emx370;

/**
 * Print target for creating PDF files.
 * <p>
 * This is in fact an extension of the postscript target, attempting
 * to convert the created postscript file to PDF by invoking the
 * corresponding GhostScript command line tool if possible; the postscript
 * file is preserved in all cases, even if the conversion succeeds.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 *
 */
public class PrintSinkPdf extends PrintSinkPostscript {

	public PrintSinkPdf(String outputDirectory, boolean haveGreenbars, boolean portrait) throws IOException {
		super(outputDirectory, haveGreenbars, portrait);
	}
	
	@Override
	public void close(String closeInfo) {
		super.close(closeInfo);
		
		String pdfFilename = (this.outputFilename.endsWith(".ps"))
				? this.outputFilename.replace(".ps", ".pdf")
				: this.outputFilename + ".pdf";
				
		String[] pdfConvertCmd = {Emx370.getPs2PdfCommand(), this.outputFilename, pdfFilename};
		// System.out.println("## starting pdf conversion");
		try {
			/*Process p =*/ Runtime.getRuntime().exec(pdfConvertCmd, null, null);
		} catch (Exception e) {
			System.err.println("** PrintSinkPdf: unable to convert PS => PDF :: was PS2PDFCOMMAND correctly specified?");
			System.err.println("** PrintSinkPdf: " + e.getLocalizedMessage());
		}
		// System.out.println("## done pdf conversion");
	}
}
