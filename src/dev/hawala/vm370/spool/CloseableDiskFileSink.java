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

package dev.hawala.vm370.spool;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;

/**
 * Common close functionality for spool output files written to disk
 * outside a spool system.
 * <p>
 * This implementation creates to output file in a target directory,
 * using a unique name for the file. When the file is closed, the 
 * closing information passed via the CP CLOSE command is used to
 * extend the filename, hopefully adding information about the 
 * item written  (mostly the CMS filename filetype components).
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2016
 */
public abstract class CloseableDiskFileSink implements iSpoolCloseableFile {

	private static int uniquer = 0;
	
	private final String outputDirectory;
	private final String extension;
	private final File destFile;
	
	private final String outputFilenameBase;
	protected String outputFilename;
	
	/**
	 * the output stream to be used by spool sink implementations using this
	 * functionality.
	 */
	protected PrintStream sink = null;
	
	/**
	 * Generate a unique number to make the filename unique even
	 * when opened at the same time(stamp).
	 * @return
	 */
	private static synchronized int getUniquer() {
		return ++uniquer;
	}

	/**
	 * Common constructor, creating an uniquely named file which may be renamed
	 * when closed with an appropriate closing information to contain this closing
	 * information (mostly the CMS filename filetype components).
	 *  
	 * @param outputDirectory the target directory for output files.
	 * @param extension the extension to use for the file.
	 * @throws IOException if the file cannot be created.
	 */
	protected CloseableDiskFileSink(String outputDirectory, String prefix, String extension) throws IOException {
		this.outputDirectory = outputDirectory;
		this.extension = extension;
		
		this.outputFilenameBase = this.outputDirectory + "/" + prefix + "_" + System.currentTimeMillis() + "_" + getUniquer();
		this.outputFilename = this.outputFilenameBase + "." + this.extension;
		this.destFile = new File(this.outputFilename);
		this.destFile.createNewFile();
		this.sink = new PrintStream(this.destFile);
	}

	@Override
	public void close(String closeInfo) {
		if (this.sink == null) { return; }
		this.sink.close();
		this.sink = null;
		
		if (closeInfo == null) { closeInfo = ""; }
		String[] components = closeInfo.split(" ");
		String newFilename = this.outputFilenameBase;
		for (String c : components) {
			String part = c.trim();
			if (part.length() > 0) {
				newFilename += "_" + part;
			}
		}
		newFilename += "." + this.extension;
		File finalFile = new File(newFilename);
		if (!finalFile.exists() && this.destFile.renameTo(finalFile)) {
			this.outputFilename = newFilename;
		}
	}

	@Override
	public void purge() {
		if (this.sink == null) { return; }
		this.sink.close();
		this.sink = null;
		this.destFile.delete();
	}

}
