/*
** This file is part of the emx370 emulator UnitTests.
**
** This software is provided "as is" in the hope that it will be useful,
** with no promise, commitment or even warranty (explicit or implicit)
** to be suited or usable for any particular purpose.
** Using this software is at your own risk!
**
** Written by Dr. Hans-Walter Latz, Berlin (Germany), 2015
** Released to the public domain.
*/

package dev.hawala.vm370.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Random;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

import org.junit.Assert;

public class DasdCkdcTest {

	// @Test
	public void testCompression() {
		byte[] src = new byte[12124];
		byte[] packed = null;
		byte[] unpacked = new byte[65536];
		int unpackCount = -1;
		
		/*
		// fill with junk data (12124 bytes => 385 bytes)
		for (int i = 0; i < src.length; i++) {
			src[i] = (byte)((i + 33) & 0xFF);
		}
		*/
		
		/*
		// fill with unified data (12124 bytes => 34 bytes)
		for (int i = 0; i < src.length; i++) { src[i] = (byte)0; }
		*/
		
		// /*
		// fill with random data
		Random rnd = new Random(42);
		for (int i = 0; i < src.length; i++) {
			src[i] = (byte)(rnd.nextInt(255) - 128);
		}
		// */
		
		// compress it
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			DeflaterOutputStream dos = new DeflaterOutputStream(baos);
			dos.write(src, 0, src.length);
			dos.finish();
			packed = baos.toByteArray();
			dos.close();
			baos.close();
		} catch(IOException exc) {
			fail("IOException while compressing");
		}
		
		// decompress it
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(packed);
			InflaterInputStream iis = new InflaterInputStream(bais);
			unpackCount = 0;
			int cnt = iis.read(unpacked);
			while (cnt >= 0) {
				unpackCount += cnt;
				cnt = iis.read(unpacked, unpackCount, unpacked.length - unpackCount);
			}
			if (unpackCount < unpacked.length) { Arrays.fill(unpacked, unpackCount, unpacked.length, (byte)0x00); }
			iis.close();
			bais.close();
		} catch(IOException exc) {
			fail("IOException while de-compressing");
		}
		
		// check outcomes
		assertEquals("Length of unpacked data", src.length, unpackCount);
		for (int i = 0; i < src.length; i++) {
			if (src[i] != unpacked[i]) {
				assertEquals(String.format("src /unpacked at offset %d", i), src[i], unpacked[i]);
			}
		}
		Assert.fail(String.format("SUCCESS ... Compression: %d => %d", src.length, packed.length));
	}
	
}
