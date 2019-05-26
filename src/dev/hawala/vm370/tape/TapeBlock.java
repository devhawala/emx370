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

package dev.hawala.vm370.tape;

/**
 * A single tape block in memory representation for emx370 tape handling.
 * 
 * <p>
 * A tape is represented as double linked chain of tape blocks, which are either
 * data blocks or tape marks. The head and tail of the chain are owned by the
 * tape device and are not part of the tape content.
 * </p>
 * 
 * @author Dr. Hans-Walter Latz, Berlin (Germany), 2015
 * 
 */
public class TapeBlock {

	private final boolean isTapemark; // flag: if true, the block is empty and a tape mark
	
	private final byte[] blockBytes; // the block content: if null, the block is empty
	
	private TapeBlock prev = null; // the previous block in the tape sequence
	private TapeBlock next = null; // the next block in the tape sequence
	
	/**
	 * Constructor to create an unchained empty tape block not being a tape mark.
	 */
	public TapeBlock() {
		this.isTapemark = false;
		this.blockBytes = null;
	}
	
	/**
	 * Constructor to append a tape mark to {@code after}, putting it in the chain between
	 * {@code after} and {@after.next}.
	 *  
	 * @param after the block node to which the new block is to be appended.
	 */
	public TapeBlock(TapeBlock after) {
		this.isTapemark = true;
		this.blockBytes = null;
		
		this.prev = after;
		after.next = this;
	}
	
	/**
	 * Constructor to append a data block with the content specified to {@code after},
	 * putting it in the chain between {@code after} and {@after.next}.
	 *  
	 * @param after the block node to which the new block is to be appended.
	 * @param data the content of the data block.
	 */
	public TapeBlock(TapeBlock after, byte[] data) {
		this.isTapemark = false;
		this.blockBytes = data;
		
		this.prev = after;
		if (after != null) { after.next = this; }
	}
	
	/**
	 * Check if this block is a tape mark.
	 * 
	 * @return {@code true} if this block is a tape mark.
	 */
	public boolean isTapemark() {
		return this.isTapemark;
	}
	
	/**
	 * Get the data content of the tape block.
	 * 
	 * @return the data byte representing the content of the tape or
	 *   {@code null} if the block is empty or a tape mark.
	 */
	public byte[] getBlockData() {
		return this.blockBytes;
	}
	
	/**
	 * Get the next sequential block in the chain.
	 * 
	 * @return the next block in the chain.
	 */
	public TapeBlock getNext() {
		return this.next;
	}
	
	/**
	 * Get the previous sequential block in the chain.
	 * 
	 * @return the prevous block in the chain.
	 */
	public TapeBlock getPrev() {
		return this.prev;
	}
	
	/**
	 * Append the specified block(-chain) as sequential next
	 * of this block. 
	 * 
	 * @param other the block that is to be appended.
	 */
	public void append(TapeBlock other) {
		this.next = other;
		if (other != null) { other.prev = this; }
	}
}
