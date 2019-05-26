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

package dev.hawala.vm370.tests;

import dev.hawala.vm370.dasd.ckdc.CkdcDrive;

public class TestDriveLoad {
	
	private static void loadDrive(String fn) throws Exception {
		long startTS = System.nanoTime();
		
		CkdcDrive drive = new CkdcDrive(null, fn, null);
		
		long endTS = System.nanoTime();
		
		long loadTime = endTS - startTS;
		long loadSecs = loadTime / 1000000000L;
		long loadMSecs = (loadTime / 1000000) - (loadSecs * 1000);
		System.out.printf("Loadtime for %s : %d.%d seconds\n", fn, loadSecs, loadMSecs);
	}

	public static void main(String[] args) {
		try {
			long startTS = System.nanoTime();
			
			loadDrive("cp_cmsuser/cmsuser_191.ckdc");
			loadDrive("cp_cmsuser/cmsuser_192.ckdc");
			loadDrive("cp_cmsuser/cmsuser_193.ckdc");
			loadDrive("cp_cmsuser/cmsuser_194.ckdc");
			loadDrive("cp_cmsuser/cmsuser_195.ckdc");
			
			loadDrive("cp_shared/shared_190.ckdc");
			loadDrive("cp_shared/shared_19D.ckdc");
			loadDrive("cp_shared/shared_19E.ckdc");
			
			long endTS = System.nanoTime();
			
			long loadTime = endTS - startTS;
			long loadSecs = loadTime / 1000000000L;
			long loadMSecs = (loadTime / 1000000) - (loadSecs * 1000);
			System.out.printf("\nTotal Loadtime : %d.%d seconds\n", loadSecs, loadMSecs);
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
