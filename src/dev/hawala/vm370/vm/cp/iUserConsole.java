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
package dev.hawala.vm370.vm.cp;

import dev.hawala.vm370.ebcdic.EbcdicHandler;
import dev.hawala.vm370.mecaff.Vm3270Console.Attr;
import dev.hawala.vm370.mecaff.Vm3270Console.ConsoleElement;
import dev.hawala.vm370.vm.device.iDevice;

/**
 * Root class for the sub-classing "tree" building up the CP command interpreter
 * and the user console types allowing the interaction of the user with the
 * virtual machine running in the emulation.
 * 
 * <p>
 * This abstract class represents the interface provided by the user console, which
 * is connected to the user's terminal emulator and implements the command interpreter
 * presented to the user (CP and emulator commands), for the console {@link iDevice}
 * used by the virtual machine for SIO operations with the user's terminal.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public abstract class iUserConsole {
	
	/**
	 * The outcome delivered by the user console (connected to the remote
	 * terminal emulator) to the related console device (used by the machine
	 * reading from the user console) when the device polls for user input
	 * as the VM explicitly requests input (i.e. performs a VM READ) through
	 * the device.
	 */
	public enum eUserInputState {
		
		/** the is no user input available, the user is now explicitly prompted for input */
		NoneAvailableUserPrompted,
		
		/** the user is already prompted for input, but none is available so far */
		NoneAvailable,
		
		/** the user provided input to the prompt */
		ReadFromPrompt,
		
		/** the user typed ahead and provided input before being prompted for */
		ReadFromBuffer,
		
	}

	/**
	 * Get the next text line entered at the user console, prompting with "VM READ" or equivalent
	 * if no input line is buffered.
	 * 
	 * @param buffer The buffer where to place the (possibly empty) input line.
	 * @return result of the user input attempt. 
	 */
	public abstract eUserInputState getNextUserInputForVMREAD(EbcdicHandler buffer);
	
	/**
	 * Get the number of lines currently entered by the user before being prompted for.
	 * 
	 * @return number of user input lines waiting for processing.
	 */
	public abstract int getEnqueuedUserInput();
	
	/**
	 * Write an ebcdic output line to the user console, appending a NewLine.
	 * @param line the text to be written to the console
	 */
	public abstract void writeAddCR(EbcdicHandler line);
	
	/**
	 * Write an ebcdic output line as is to the user console (without appending a NewLine).
	 * @param line the text to be written to the console
	 */
	public abstract void writeNoCR(EbcdicHandler line);
	
	/**
	 * Write an ISO-Latin-1 output line to the user console.
	 * @param line the text to be written to the console
	 */
	public abstract void writeln(String line);
	
	/**
	 * Write an ISO-Latin-1 output line as pattern with arguments to the console.
	 * @param line the format pattern to be written to the console
	 * @param args the substitution values for the format pattern 
	 */
	public abstract void writef(String pattern, Object... args);
	
	/**
	 * Define the behavior of a PF-key in 3270 console mode of a display (3270) terminal.
	 * The 3270 mode is the emulation of a plain 3270 terminal with the screen
	 * setup like standard CP with 22 display lines, the input zone starting at (22,1)
	 * and with the static indication at the lower right.  
	 * @param pfKey the number (0..23) of the PF-key to be set
	 * @param cmdline the command string to be used when the PF-key is pressed, with the
	 *   first word being one of the following: DELAYED, IMMED, RETRIEVED (see CP SET command) 
	 */
	public void setCpPFkey(int pfKey, EbcdicHandler cmdline) {}
	
	/**
	 * Define the behavior of a PF-key in the MECAFF console mode of a display (3270) terminal.
	 * The MECAFF mode is the standard.
	 * 
	 * @param pfKey the number (0..23) of the PF-key to be set
	 * @param cmdline the command to be executed when the PF-key is pressed 
	 */
	public void setMecaffPFKey(int pfKey, String cmdline) {}
	
	/**
	 * Get the behavior of a PF-key in the MECAFF console mode of a display (3270) terminal.
	 * 
	 * @param pfKey
	 * @return the command associated with the PF key in MECAFF console mode.
	 */
	public String getMecaffPFKey(int pfKey) { return null; }
	
	/**
	 * Set the current flow mode state for the MECAF console.
	 * 
	 * @param flowMode {@code true} if the console should not enter the "More..." state
	 *   when a screen full has been output resp. {@code false} if the console should
	 *   use the "More..." state.
	 */
	public void setMecaffFlowMode(boolean flowMode) {}
	
	/**
	 * Set the display attribute for the given screen element.
	 * 
	 * @param element the screen element to change.
	 * @param attr the attribute to use for the element.
	 */
	public void setMecaffAttr(ConsoleElement element, Attr attr) {}
}
