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

package dev.hawala.vm370;

/**
 * Line tokenizer items used by command interpreters.
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 *
 */
public final class CommandTokens {
	
	/**
	 * Exception thrown if a problem is encountered with the command line.
	 */
	public static class CmdError extends RuntimeException {
		private static final long serialVersionUID = 3260188294463774672L;
		public CmdError(String msg) { super(msg); }
		public CmdError(String pattern, Object... args) { super(String.format(pattern, args)); }
	}
	
	/**
	 * The string tokenizer proper.
	 */
	public static class Tokenizer {
		private final String line;
		private final String[] token;
		private int currToken = 0;
		
		/**
		 * Construct a tokenizer for the given line.
		 * 
		 * @param line the line to be tokenized.
		 */
		public Tokenizer(String line) {
			if (line == null || line.length() == 0) {
				this.line = "";
				this.token = new String[0];
			} else {
				this.line = line;
				this.token = line.split(" +");;
			}
		}
		
		/**
		 * Get and consume the next token or null if there are no more token. 
		 * @return the next token.
		 */
		public String next() {
			if (this.currToken >= this.token.length) { return null; }
			return this.token[this.currToken++];
		}
		
		/**
		 * Get and consume the next token in upper case or null if there are no more token. 
		 * @return the next uppercased token.
		 */
		public String nextUpper() {
			if (this.currToken >= this.token.length) { return null; }
			return this.token[this.currToken++].toUpperCase();
		}
		
		/**
		 * Get the next token, but do not consume it, so {@link next()} or
		 * {@link nextUpper()} will deliver this token.
		 * 
		 * @return the value of the next available token.
		 */
		public String peekNext() {
			if (this.currToken >= this.token.length) { return null; }
			return this.token[this.currToken];
		}
		
		/**
		 * Get the next token in upper case, but do not consume it, so {@link next()} or
		 * {@link nextUpper()} will deliver this token.
		 * 
		 * @return the uppercased value of the next available token.
		 */
		public String peekNextUpper() {
			if (this.currToken >= this.token.length) { return null; }
			return this.token[this.currToken].toUpperCase();
		}
		
		/**
		 * Get the remaining string behind the last delivered token.
		 *  
		 * @return the remaining string.
		 */
		public String getRemaining() {
			if (this.currToken >= this.token.length) { return null; }
			String txt = this.line;
			for (int i = 0; i < this.currToken; i++) {
				txt = txt.substring(this.token[i].length()).trim();
			}
			return txt;
		}
		
		/**
		 * Return if there are more token to get.
		 * 
		 * @return {@code true} if there still are token to consume.
		 */
		public boolean hasMore() { return (currToken < this.token.length); }
		
		/**
		 * Get the original line that was to be tokenized.
		 * @return the orininal string.
		 */
		public String getLine() { return this.line; }
	}
	
	/**
	 * Check if the token passed are same.
	 * 
	 * @param cand the candidate to be tested.
	 * @param token the token to which the candidate is to be compared
	 * @return {@code true} if candidate is equal to the token.
	 */
	public static boolean isToken(String cand, String token) {
		return isToken(cand, token, 65536);
	}
	
	/**
	 * Check if the token passed are same in the first 'minLen' character positions.
	 * 
	 * @param cand the candidate to be tested.
	 * @param token the token to which the candidate is to be compared
	 * @param minLen the minimal length that candidate and token must be same
	 * @return {@code true} if the candidate matches the token.
	 */
	public static boolean isToken(String cand, String token, int minLen) {
		if (cand == null) { return false; }
		int candLen = cand.length();
		int tokLen = token.length();
		if (minLen > tokLen) { minLen = tokLen; }
		if (candLen > tokLen) { return false; }
		return token.startsWith(cand) && candLen >= minLen;
	}
	
	/**
	 * Get the argument as integer value in decimal notation or raise a
	 * {@link CmdError} if it cannot be parsed.  
	 * @param arg the string with a decimal number to be parsed
	 * @return the integer value represented by 'arg'.
	 */
	public static int getInt(String arg) {
		try {
			int val = Integer.parseInt(arg);
			return val;
		} catch(NumberFormatException e) {
			// a new error is thrown below
		}
		throw new CmdError("'%s' is not a valid integer", arg);
	}
	
	/**
	 * Get the argument as integer value in in 3-digit hex. notation or raise a
	 * {@link CmdError} if it cannot be parsed.  
	 * @param arg the string with a decimal number to be parsed
	 * @return the 3-hex-digit value represented by 'arg'.
	 */
	public static int getCuu(String arg) {
		try {
			int val = Integer.parseInt(arg, 16);
			if (val <= 0xFFF) { return val; } 
		} catch(NumberFormatException e) {
			// a new error is thrown below
		}
		throw new CmdError("'%s' is not a valid CUU device address", arg);
	}
	
	/**
	 * Get the argument as integer value in hex. notation or raise a
	 * {@link CmdError} if it cannot be parsed.  
	 * @param arg the string with a decimal number to be parsed
	 * @return the value represented by 'arg'.
	 */
	public static int getHexloc(String arg) {
		try {
			int val = Integer.parseInt(arg, 16);
			if (val <= 0xFFFFFF) { return val; } 
		} catch(NumberFormatException e) {
			// a new error is thrown below
		}
		throw new CmdError("'%s' is not a valid hex location", arg);
	}

}
