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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;
import static dev.hawala.vm370.ebcdic.PlainHex.*;
import dev.hawala.vm370.cons.ConsoleCommandCodes;
import dev.hawala.vm370.cons.ConsoleSimple;
import dev.hawala.vm370.cons.UserConsoleSerial;
import dev.hawala.vm370.dasd.ckdc.CkdDriveType;
import dev.hawala.vm370.dasd.ckdc.CkdcDrive;
import dev.hawala.vm370.dasd.ckdc.Vm370DdrCkdcLoader;
import dev.hawala.vm370.ebcdic.EbcdicHandler;
import dev.hawala.vm370.vm.device.DeviceHandler;
import dev.hawala.vm370.vm.device.iDevice;
import dev.hawala.vm370.vm.device.iDeviceChannelStatus;
import dev.hawala.vm370.vm.device.iDeviceIO;
import dev.hawala.vm370.vm.device.iDeviceStatus;
import dev.hawala.vm370.vm.machine.iProcessorEventTracker;

public class DeviceHandlerTest_Basics {
	
	private final static int CcwFlags_None = 0;
	private final static int CcwFlags_CD = 0x80;
	private final static int CcwFlags_CC = 0x40;
	private final static int CcwFlags_SLI = 0x20;
	private final static int CcwFlags_SKIP = 0x10;
	private final static int CcwFlags_PCI = 0x08;
	private final static int CcwFlags_IDA = 0x04;
	
	private static byte mkCmdTIC() { return (byte)0xF8; }
	private static byte mkCmdWrite(int flags) { return (byte)((flags & 0xFC) | 0x01); }
	private static byte mkCmdRead(int flags) { return (byte)((flags & 0xFC) | 0x02); }
	//private static byte mkCmdReadBackwards(int flags) { return (byte)((flags & 0xF0) | 0x0C); }
	private static byte mkCmdControl(int flags) { return (byte)((flags & 0xFC) | 0x03); }
	private static byte mkCmdSense(int flags) { return (byte)((flags & 0xF0) | 0x04); }
	private static byte mkCmdInvalid(int flags) { return (byte)(flags & 0xF0); }
	
	private final static int ADDR_CAW = 72;
	private final static int ADDR_CSW = 64; 
	
	// the memory used for tests
	private final byte[] mem = new byte[8192];
	
	private static class EventLogger implements iProcessorEventTracker {
		public void logLine(String line, Object... args) {
			System.out.print("   >> ");
			System.out.printf(line, args);
			System.out.println();
		}
	}
	
	private final iProcessorEventTracker eventLogger = new EventLogger();
	
	@Before
	public void preTest() {
		java.util.Arrays.fill(this.mem, (byte)0x00);
	}
	
	// returns location of following CCW
	private int setCCW(int at, byte command, int address, int flags, int count) {
		this.mem[at+0] = command;
		this.mem[at+1] = (byte)((address >> 16) & 0xFF);
		this.mem[at+2] = (byte)((address >> 8) & 0xFF);
		this.mem[at+3] = (byte)(address & 0xFF);
		this.mem[at+4] = (byte)(flags & 0xFF);
		this.mem[at+5] = (byte)0;
		this.mem[at+6] = (byte)((count >> 8) & 0xFF);
		this.mem[at+7] = (byte)(count & 0xFF);
		return at + 8;
	}
	
	private void setCAW(int addr, byte protKey) {
		this.mem[ADDR_CAW] = protKey;
		this.mem[ADDR_CAW + 1] = (byte)((addr >> 16) & 0xFF);
		this.mem[ADDR_CAW + 2] = (byte)((addr >> 8) & 0xFF);
		this.mem[ADDR_CAW + 3] = (byte)(addr & 0xFF);
	}
	
	private void putIdaAddr(int at, int addr) {
		this.mem[at] = 0;
		this.mem[at + 1] = (byte)((addr >> 16) & 0xFF);
		this.mem[at + 2] = (byte)((addr >> 8) & 0xFF);
		this.mem[at + 3] = (byte)(addr & 0xFF);
	}
	
	private static class TestDevice implements iDevice {
		
		// control information for test
		private int lenControl = 1;
		private int lenWrite = 1;
		private byte[] readData = null;
		private byte[] senseData = null;
		private int resultRead = 0;
		private int resultWrite = 0;
		private int resultSense = 0;
		private int resultControl = 0;
		
		public TestDevice setWriteLen(int l) { this.lenWrite = l; return this; }
		public TestDevice setControlLen(int l) { this.lenControl = l; return this; }
		public TestDevice setReadData(byte[] data) { this.readData = data; return this; }
		public TestDevice setSenseData(byte[] data) { this.senseData = data; return this; }
		public TestDevice setReadResult(int r) { this.resultRead = r; return this; }
		public TestDevice setWriteResult(int r) { this.resultWrite = r; return this; }
		public TestDevice setSenseResult(int r) { this.resultSense = r; return this; }
		public TestDevice setControlResult(int r) { this.resultControl = r; return this; }
		
		// result information from test
		private boolean hadReset = false;
		private boolean hadRead = false;
		private int datalenRead = 0;
		private int ioOutRead = 0;
		private boolean hadWrite = false;
		private int datalenWrite = 0;
		private int ioOutWrite = 0;
		private boolean hadSense = false;
		private int datalenSense = 0;
		private int ioOutSense = 0;
		private boolean hadControl = false;
		private int datalenControl = 0;
		private int ioOutControl = 0;
		private byte[] dataWritten = null;
		private byte[] dataControlled = null;
		
		public boolean getHadReset() { return this.hadReset; }
		public boolean getHadRead() { return this.hadRead; }
		public boolean getHadWrite() { return this.hadWrite; }
		public boolean getHadSense() { return this.hadSense; }
		public boolean getHadControl() { return this.hadControl; }
		public int getReadDatalen() { return this.datalenRead; }
		public int getWriteDatalen() { return this.datalenWrite; }
		public int getSenseDatalen() { return this.datalenSense; }
		public int getControlDatalen() { return this.datalenControl; }
		public int getReadIoResult() { return this.ioOutRead; }
		public int getWriteIoResult() { return this.ioOutWrite; }
		public int getSenseIoResult() { return this.ioOutSense; }
		public int getControlIoResult() { return this.ioOutControl; }
		public byte[] getDataWritten() { return this.dataWritten; }
		public byte[] getDataControlled() { return this.dataControlled; }

		@Override
		public void resetState() {
			this.hadReset = true;
		}

		@Override
		public int read(int opcode, int dataLength, iDeviceIO memTarget) {
			this.hadRead = true;
			this.datalenRead = dataLength;
			if (this.readData == null) { throw new IllegalStateException("no readData specified for test device"); }
			this.ioOutRead = memTarget.transfer(this.readData, 0, this.readData.length);
			return this.resultRead;
		}

		@Override
		public int write(int opcode, int dataLength, iDeviceIO memSource) {
			this.hadWrite = true;
			this.datalenWrite = dataLength;
			if (this.lenWrite < 1) { throw new IllegalStateException("invalid writeLen specified for test device"); }
			this.dataWritten = new byte[this.lenWrite];
			this.ioOutWrite = memSource.transfer(this.dataWritten, 0, this.lenWrite);
			return this.resultWrite;
		}

		@Override
		public int sense(int opcode, int dataLength, iDeviceIO memTarget) {
			this.hadSense = true;
			this.datalenSense = dataLength;
			if (this.senseData == null) { throw new IllegalStateException("no senseData specified for test device"); }
			this.ioOutSense = memTarget.transfer(this.senseData, 0, this.senseData.length);
			return this.resultSense;
		}

		@Override
		public int control(int opcode, int dataLength, iDeviceIO memSource) {
			this.hadControl = true;
			this.datalenControl = dataLength;
			if (this.lenControl < 1) { return this.resultControl; }
			this.dataControlled = new byte[this.lenControl];
			this.ioOutControl = memSource.transfer(this.dataControlled, 0, this.lenControl);
			return this.resultControl;
		}
		
		@Override
		public boolean hasPendingAsyncInterrupt() { return false; }
		
		@Override
		public void consumeNextAsyncInterrupt() {}
		
		@Override
		public void doAttentionInterrupt() {}
		
		@Override
		public int getRDevInfo() { return 0; }
		
		@Override
		public int getVDevInfo() { return 0; }
		
		@Override
		public byte getSenseByte(int index) { return (byte)0; }
		
		@Override
		public String getCpDeviceTypeName() {
			return "XXXX";
		}
		
		@Override
		public String getCpQueryStatusLine(int asCuu) {
			return "XXXX " + asCuu;
		}
	}
	
	private void checkCSWStatus(int expectedUnitStatus, int expectedChannelStatus) {
		assertEquals("CSW UnitStatus", (byte)expectedUnitStatus, this.mem[ADDR_CSW+4]);
		assertEquals("CSW ChannelStatus", (byte)expectedChannelStatus, this.mem[ADDR_CSW+5]);
	}
	
	private void checkCSW(
			byte expectedProtKey,
			byte expectedCC,
			int expectedCcwAddress,
			int expectedUnitStatus,
			int expectedChannelStatus,
			int expectedCount) {
		byte protKey = (byte)(this.mem[ADDR_CSW] & 0xF0);
		assertEquals("CSW ProtectionKey", expectedProtKey, protKey);
		
		byte cc = (byte)(this.mem[ADDR_CSW] & 0x03);
		assertEquals("CSW CC", expectedCC, cc);
		
		int ccwAddr = (this.mem[ADDR_CSW+1] << 16) | (this.mem[ADDR_CSW+2] << 8) | this.mem[ADDR_CSW+3];
		assertEquals("CSW CCW Address", expectedCcwAddress, ccwAddr);
		
		this.checkCSWStatus(expectedUnitStatus, expectedChannelStatus);
		
		int count = (this.mem[ADDR_CSW+6] << 8) | this.mem[ADDR_CSW+7];
		assertEquals("CSW Count", expectedCount, count);
	}
	
	@Test
	public void test_read_SingleCCW_OK() {
		// setup CAW
		int ccwAddr = 1024;
		byte protKey = _50;
		this.setCAW(ccwAddr, protKey);
		
		// single CCW: read 33 bytes to location 2048
		int readPos = 2048;
		int readCount = 33;
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(0x00), readPos, CcwFlags_None, readCount);
		
		// setup a device and a device-handler for that
		byte[] readData = {
				_10, _11, _12, _13, _14, _15, _16, _17, _18, _19,
				_20, _21, _22, _23, _24, _25, _26, _27, _28, _29,
				_30, _31, _32, _33, _34, _35, _36, _37, _38, _39,
				_40, _41, _42
		};
		TestDevice dev = new TestDevice()
			.setReadData(readData)
			.setReadResult(iDeviceStatus.DEVICE_END);
		DeviceHandler d = new DeviceHandler(this.mem, dev, 0x123, this.eventLogger);
		
		// set guard bytes before/after read target area and run the channel program
		this.mem[readPos-1] = _99;
		this.mem[readPos+readCount] = _99;
		boolean wasOk = d.processCAW();
		d.storeCSW();
		
		// check outcome
		assertEquals("Outcome of processCAW()", true, wasOk);
		assertTrue("hadReset()", dev.getHadReset());
		assertTrue("hadRead()", dev.getHadRead());
		assertFalse("hadWrite()", dev.getHadWrite());
		assertFalse("hadControl()", dev.getHadControl());
		assertFalse("hadsense()", dev.getHadSense());
		
		assertEquals("Guard before read target area", _99, this.mem[readPos-1]);
		assertEquals("Guard after read target area", _99, this.mem[readPos+readCount]);
		for (int i = 0; i < readCount; i++) {
			if (this.mem[readPos + i] != readData[i]) {
				assertEquals("Byte in read target area at offset " + i, readData[i], this.mem[readPos + i]);
			}
		}
		assertEquals("Result of IDevice.read().dataLength", readCount, dev.getReadDatalen());
		assertEquals("Result of IDeviceIO.transfer() for IDevice.read()", 0, dev.getReadIoResult());
		
		this.checkCSW(
			protKey,
			_00,
			ccwAddr,
			iDeviceStatus.DEVICE_END | iDeviceStatus.CHANNEL_END,
			iDeviceChannelStatus.OK,
			0);
	}
	
	@Test
	public void test_read_CCWs_ChainData_ChainCommand_OK() {
		// setup CAW
		int ccwAddr = 1024;
		byte protKey = _90;
		this.setCAW(ccwAddr, protKey);
		
		// chained CCWs:
		// - control (no data used => SLI-flag, delivers status-modifier to skip TIC-CCW)
		// - chained command: transfer-in-channel
		// - chained command: read with chain-data:
		//   => read 12 bytes to location 2048
		//   => skip 11 bytes
		//   => read 10 bytes to location 4096
		//   device total: 33 bytes
		// - chained command: sense
		int readPos1 = 2048;
		int readCount1 = 12;
		int skipCount = 11;
		int readPos2 = 4096;
		int readCount2 = 10;
		int sensePos = 3072;
		int senseCount = 5;
		ccwAddr = this.setCCW(ccwAddr, mkCmdControl(0x70), 0x000000, CcwFlags_SLI | CcwFlags_CC, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdTIC(), ccwAddr - 8, CcwFlags_None, 0);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(0x00), readPos1, CcwFlags_CD, readCount1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdInvalid(0x00), readPos1, CcwFlags_SKIP | CcwFlags_CD, skipCount);
		ccwAddr = this.setCCW(ccwAddr, mkCmdInvalid(0xF0), readPos2, CcwFlags_CC, readCount2);
		ccwAddr = this.setCCW(ccwAddr, mkCmdSense(0x00), sensePos, CcwFlags_None, senseCount);
		
		// setup a device and a device-handler for the above CCW program
		byte[] readData = {
				_10, _11, _12, _13, _14, _15, _16, _17, _18, _19,
				_20, _21, _22, _23, _24, _25, _26, _27, _28, _29,
				_30, _31, _32, _33, _34, _35, _36, _37, _38, _39,
				_40, _41, _42
		};
		byte[] senseData = { _A1, _A2, _A3, _A4, _A4 };
		TestDevice dev = new TestDevice()
			.setControlLen(0)
			.setControlResult(iDeviceStatus.DEVICE_END | iDeviceStatus.STATUS_MODIFIER) // i.e.: skip next CCW
			.setReadData(readData)
			.setReadResult(iDeviceStatus.DEVICE_END)
			.setSenseData(senseData)
			.setSenseResult(iDeviceStatus.DEVICE_END);
		DeviceHandler d = new DeviceHandler(this.mem, dev, 0x123, this.eventLogger);
		
		// set guard bytes before/after read/sense target areas and run the channel program
		this.mem[readPos1-1] = _99;
		this.mem[readPos1+readCount1] = _99;
		this.mem[readPos2-1] = _99;
		this.mem[readPos2+readCount2] = _99;
		this.mem[sensePos-1] = _99;
		this.mem[sensePos+senseCount] = _99;
		boolean wasOk = d.processCAW();
		d.storeCSW();
		
		// check outcome
		assertEquals("Outcome of processCAW()", true, wasOk);
		assertTrue("hadReset()", dev.getHadReset());
		assertTrue("hadRead()", dev.getHadRead());
		assertFalse("hadWrite()", dev.getHadWrite());
		assertTrue("hadControl()", dev.getHadControl());
		assertTrue("hadsense()", dev.getHadSense());
		
		assertEquals("Guard before 1st read target area", _99, this.mem[readPos1-1]);
		assertEquals("Guard after 1st read target area", _99, this.mem[readPos1+readCount1]);
		for (int i = 0; i < readCount1; i++) {
			if (this.mem[readPos1 + i] != readData[i]) {
				assertEquals("Byte in 1st read target area at offset " + i, readData[i], this.mem[readPos1 + i]);
			}
		}
		
		assertEquals("Guard before 2nd read target area", _99, this.mem[readPos2-1]);
		assertEquals("Guard after 2nd read target area", _99, this.mem[readPos2+readCount2]);
		for (int i = 0; i < readCount2; i++) {
			if (this.mem[readPos2 + i] != readData[readCount1 + skipCount + i]) {
				assertEquals("Byte in 2nd read target area at offset " + i, readData[readCount1 + skipCount + i], this.mem[readPos2 + i]);
			}
		}

		assertEquals("Result of IDevice.read().dataLength", readCount1 + skipCount + readCount2, dev.getReadDatalen());
		assertEquals("Result of IDeviceIO.transfer() for IDevice.read()", 0, dev.getReadIoResult());
		
		assertEquals("Guard before sense target area", _99, this.mem[sensePos-1]);
		assertEquals("Guard after sense target area", _99, this.mem[sensePos+senseCount]);
		for (int i = 0; i < senseCount; i++) {
			if (this.mem[sensePos + i] != senseData[i]) {
				assertEquals("Byte in sense target area at offset " + i, senseData[i], this.mem[sensePos + i]);
			}
		}
		assertEquals("Result of IDevice.sense().dataLength", senseCount, dev.getSenseDatalen());
		assertEquals("Result of IDeviceIO.transfer() for IDevice.sense()", 0, dev.getSenseIoResult());
		
		this.checkCSW(
			protKey,
			_00,
			ccwAddr,
			iDeviceStatus.DEVICE_END | iDeviceStatus.CHANNEL_END,
			iDeviceChannelStatus.OK,
			0);
	}
	
	@Test
	public void test_write_CCWs_ChainCommand_OK() {
		// setup CAW
		int ccwAddr = 1024;
		byte protKey = _90;
		this.setCAW(ccwAddr, protKey);
		
		// chained CCWs:
		// - control (3 control bytes at 2000, delivers status-modifier to skip TIC-CCW)
		// - chained command: transfer-in-channel
		// - chained command: write 33 bytes from location 2048
		// - chained command: sense
		int writePos = 2048;
		int writeCount = 33;
		int sensePos = 3072;
		int senseCount = 5;
		ccwAddr = this.setCCW(ccwAddr, mkCmdControl(0x70), 2000, CcwFlags_CC, 3);
		ccwAddr = this.setCCW(ccwAddr, mkCmdTIC(), ccwAddr - 8, CcwFlags_None, 0);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(0x00), writePos, CcwFlags_CC, writeCount);
		ccwAddr = this.setCCW(ccwAddr, mkCmdSense(0x00), sensePos, CcwFlags_None, senseCount);
		
		// prepare the memory areas to be passed to the device
		this.mem[2000] = _C1;
		this.mem[2001] = _C2;
		this.mem[2002] = _C3;
		byte[] zeData = {
				_10, _11, _12, _13, _14, _15, _16, _17, _18, _19,
				_20, _21, _22, _23, _24, _25, _26, _27, _28, _29,
				_30, _31, _32, _33, _34, _35, _36, _37, _38, _39,
				_40, _41, _42
		};
		this.mem[writePos-1] = _99;
		for (int i = 0; i < writeCount; i++) { this.mem[writePos+i] = zeData[i]; }
		this.mem[writePos+writeCount] = _99;
		
		// setup a device and a device-handler for the above CCW program
		byte[] senseData = { _A1, _A2, _A3, _A4, _A4 };
		TestDevice dev = new TestDevice()
			.setControlLen(3)
			.setControlResult(iDeviceStatus.DEVICE_END | iDeviceStatus.STATUS_MODIFIER) // i.e.: skip next CCW
			.setWriteLen(128)
			.setWriteResult(iDeviceStatus.DEVICE_END | iDeviceStatus.INCORRECT_LENGTH_IS_OK)
			.setSenseData(senseData)
			.setSenseResult(iDeviceStatus.DEVICE_END);
		DeviceHandler d = new DeviceHandler(this.mem, dev, 0x123, this.eventLogger);
		
		// set guard bytes before/after sense target area and run the channel program
		this.mem[sensePos-1] = _99;
		this.mem[sensePos+senseCount] = _99;
		boolean wasOk = d.processCAW();
		d.storeCSW();
		
		// check outcome
		assertEquals("Outcome of processCAW()", true, wasOk);
		assertTrue("hadReset()", dev.getHadReset());
		assertFalse("hadRead()", dev.getHadRead());
		assertTrue("hadWrite()", dev.getHadWrite());
		assertTrue("hadControl()", dev.getHadControl());
		assertTrue("hadsense()", dev.getHadSense());
		
		byte[] controlledData = dev.getDataControlled();
		assertEquals("Size of control area in device", 3, controlledData.length);
		assertEquals("Byte coltrolled to device at offset 0", _C1, controlledData[0]);
		assertEquals("Byte coltrolled to device at offset 1", _C2, controlledData[1]);
		assertEquals("Byte coltrolled to device at offset 2", _C3, controlledData[2]);
		assertEquals("Result of IDevice.control().dataLength", 3, dev.getControlDatalen());
		assertEquals("Result of IDeviceIO.transfer() for IDevice.control()", 0, dev.getControlIoResult());
		
		byte[] writtenData = dev.getDataWritten();
		assertEquals("Size of write area in device", 128, writtenData.length);
		for (int i = 0; i < writtenData.length; i++) {
			if (i < writeCount) {
				if (writtenData[i] != zeData[i]) {
					assertEquals("Byte written to device at offset " + i, zeData[i], writtenData[i]);
				}
			} else {
				assertEquals("Not written byte in device at offset " + i, _00, writtenData[i]);
			}
		}
		assertEquals("Result of IDevice.write().dataLength", writeCount, dev.getWriteDatalen());
		assertEquals("Result of IDeviceIO.transfer() for IDevice.write()", -95, dev.getWriteIoResult());
		
		assertEquals("Guard before sense target area", _99, this.mem[sensePos-1]);
		assertEquals("Guard after sense target area", _99, this.mem[sensePos+senseCount]);
		for (int i = 0; i < senseCount; i++) {
			if (this.mem[sensePos + i] != senseData[i]) {
				assertEquals("Byte in sense target area at offset " + i, senseData[i], this.mem[sensePos + i]);
			}
		}
		assertEquals("Result of IDevice.sense().dataLength", senseCount, dev.getSenseDatalen());
		assertEquals("Result of IDeviceIO.transfer() for IDevice.sense()", 0, dev.getSenseIoResult());
		
		this.checkCSW(
			protKey,
			_00,
			ccwAddr,
			iDeviceStatus.DEVICE_END | iDeviceStatus.CHANNEL_END,
			iDeviceChannelStatus.OK,
			0);
	}
	
	@Test
	public void test_write_CCWs_ChainData_TIC_IDAW_ChainCommand_OK() {
		// setup CAW
		int ccwAddr = 1024;
		byte protKey = _F0;
		this.setCAW(ccwAddr, protKey);
		
		// complex data areas for the single write:
		// - 32 bytes at location 1500 (write command with ChainData)
		// - TIC to location 1104 (must be double-word address = last 3 bits must be zero)
		// - 2100 bytes in 3 IDA segments, IDA-list at 1200:
		//   -- at 2016: 32 bytes (up to page boundary)
		//   -- at 4096: 2048 bytes (up to page boundary)
		//   -- at 2048: 20 bytes
		// - 32 bytes at location 1600
		// (total: 2164 bytes to write)
		this.putIdaAddr(1200, 2016);   // len: 32
		this.putIdaAddr(1204,  4096);  // len: 2048
		this.putIdaAddr(1208, 2048);   // len: 20
		int sensePos = 900;
		int senseCount = 5;
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(0x00), 1500, CcwFlags_CD, 32);
		ccwAddr = this.setCCW(ccwAddr, mkCmdTIC(), 1104, CcwFlags_None, 0);
		ccwAddr = 1104;
		ccwAddr = this.setCCW(ccwAddr, mkCmdInvalid(0xF0), 1200, CcwFlags_CD | CcwFlags_IDA, 2100);
		ccwAddr = this.setCCW(ccwAddr, mkCmdInvalid(0xF0), 1600, CcwFlags_CC, 32);
		ccwAddr = this.setCCW(ccwAddr, mkCmdSense(0x00), sensePos, CcwFlags_None, senseCount);
		
		// fill source areas in memory
		byte[] zeData = { // 32 bytes
				_10, _11, _12, _13, _14, _15, _16, _17, _18, _19,
				_20, _21, _22, _23, _24, _25, _26, _27, _28, _29,
				_30, _31, _32, _33, _34, _35, _36, _37, _38, _39,
				_40, _41
		};
		for (int i = 0; i < 32; i++) { this.mem[1500 + i] = zeData[i]; } // 1st CCW area
		for (int i = 0; i < 32; i++) { this.mem[2016 + i] = zeData[i]; } // 2nd CCW 1st IDA area
		for (int i = 0; i < 2048; i++) { this.mem[4096 + i] = _D7; }     // 2nd CCW 2nd IDA area
		for (int i = 0; i < 20; i++) { this.mem[2048 + i] = zeData[i]; } // 2nd CCW 3rd IDA area
		for (int i = 0; i < 32; i++) { this.mem[1600 + i] = zeData[i]; } // 3rd CCW area
		
		// setup a device and a device-handler for the above CCW program
		byte[] senseData = { _A1, _A2, _A3, _A4, _A4 };
		TestDevice dev = new TestDevice()
			.setWriteLen(2164)
			.setWriteResult(iDeviceStatus.DEVICE_END)
			.setSenseData(senseData)
			.setSenseResult(iDeviceStatus.DEVICE_END);
		DeviceHandler d = new DeviceHandler(this.mem, dev, 0x123, this.eventLogger);
		
		// set guard bytes before/after sense target area and run the channel program
		this.mem[sensePos-1] = _99;
		this.mem[sensePos+senseCount] = _99;
		boolean wasOk = d.processCAW();
		d.storeCSW();
		
		// check outcome
		assertEquals("Outcome of processCAW()", true, wasOk);
		assertTrue("hadReset()", dev.getHadReset());
		assertFalse("hadRead()", dev.getHadRead());
		assertTrue("hadWrite()", dev.getHadWrite());
		assertFalse("hadControl()", dev.getHadControl());
		assertTrue("hadsense()", dev.getHadSense());
		
		byte[] writtenData = dev.getDataWritten();
		assertEquals("Size of write area in device", 2164, writtenData.length);
		int offset = 0;
		for (int i = 0; i < 32; i++) {
			if (writtenData[offset++] != zeData[i]) {
				assertEquals("Byte written to device at offset " + offset, zeData[i], writtenData[i]);
			}
		}
		for (int i = 0; i < 32; i++) {
			if (writtenData[offset++] != zeData[i]) {
				assertEquals("Byte written to device at offset " + offset, zeData[i], writtenData[i]);
			}
		}
		for (int i = 0; i < 2048; i++) {
			if (writtenData[offset++] != _D7) {
				assertEquals("Byte written to device at offset " + offset, _D7, writtenData[i]);
			}
		}
		for (int i = 0; i < 20; i++) {
			if (writtenData[offset++] != zeData[i]) {
				assertEquals("Byte written to device at offset " + offset, zeData[i], writtenData[i]);
			}
		}
		for (int i = 0; i < 32; i++) {
			if (writtenData[offset++] != zeData[i]) {
				assertEquals("Byte written to device at offset " + offset, zeData[i], writtenData[i]);
			}
		}
		assertEquals("Result of IDevice.write().dataLength", 2164, dev.getWriteDatalen());
		assertEquals("Result of IDeviceIO.transfer() for IDevice.write()", 0, dev.getWriteIoResult());
		
		assertEquals("Guard before sense target area", _99, this.mem[sensePos-1]);
		assertEquals("Guard after sense target area", _99, this.mem[sensePos+senseCount]);
		for (int i = 0; i < senseCount; i++) {
			if (this.mem[sensePos + i] != senseData[i]) {
				assertEquals("Byte in sense target area at offset " + i, senseData[i], this.mem[sensePos + i]);
			}
		}
		assertEquals("Result of IDevice.sense().dataLength", senseCount, dev.getSenseDatalen());
		assertEquals("Result of IDeviceIO.transfer() for IDevice.sense()", 0, dev.getSenseIoResult());
		
		this.checkCSW(
			protKey,
			_00,
			ccwAddr,
			iDeviceStatus.DEVICE_END | iDeviceStatus.CHANNEL_END,
			iDeviceChannelStatus.OK,
			0);
	}
	
	private static final byte OP_READ_ReadData = _06;
	private static final byte OP_READ_ReadCountKeyAndData = _1E;
	
	private static final byte OP_WRITE_SearchIdEqual = _31;
	private static final byte OP_WRITE_WriteData = _05;
	private static final byte OP_WRITE_WriteRecordZero = _15;
	private static final byte OP_WRITE_WriteCountKeyAndData = _1D;
	private static final byte OP_WRITE_WriteHomeAddress = _19;
	
	private static final byte OP_CONTROL_Seek = _07;
	private static final byte OP_CONTROL_SetSector = _23;
	private static final byte OP_CONTROL_SetFileMask = _1F;
	private static final byte OP_CONTROL_NoOperation = _03;
	
	private void readCmsRecord(DeviceHandler d, int cyl, int head, int record, int memTo, int memCount) {
		this.readCmsRecord(d, cyl, head, record, memTo, memCount, true);
	}
	
	private void readCmsRecord(DeviceHandler d, int cyl, int head, int record, int memTo, int memCount, boolean expectedOutcome) {
		// setup CAW
		int ccwAddr = 1024;
		byte protKey = _F0;
		this.setCAW(ccwAddr, protKey);
		
		// setup 00CCHHR bytes at 1200
		// -> Seek uses first 6 bytes: 00CCHH
		// -> SearchIdEqual used last 5 bytes: CCHHR 
		this.mem[1200] = _00;
		this.mem[1201] = _00;
		this.mem[1202] = (byte)((cyl >> 8) & 0xFF);
		this.mem[1203] = (byte)(cyl & 0xFF);
		this.mem[1204] = (byte)((head >> 8) & 0xFF);
		this.mem[1205] = (byte)(head & 0xFF);
		this.mem[1206] = (byte)(record & 0xFF);
		
		// setup some sector id at 1210
		this.mem[1210] = _0F; // 15
		
		// setup the CCW-chain (based on the CMS read chain)
		ccwAddr = this.setCCW(ccwAddr, mkCmdControl(OP_CONTROL_Seek), 1200, CcwFlags_CC, 6);
		ccwAddr = this.setCCW(ccwAddr, mkCmdControl(OP_CONTROL_SetSector), 1210, CcwFlags_CC, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_SearchIdEqual), 1202, CcwFlags_CC, 5);
		ccwAddr = this.setCCW(ccwAddr, mkCmdTIC(), ccwAddr - 8, CcwFlags_None, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadData), memTo, CcwFlags_SLI, memCount);
		
		// do the IO
		boolean wasOk = d.processCAW();
		assertEquals("Result of d.processCAW()", expectedOutcome, wasOk);
		d.storeCSW();
	}
	
	private void assertZeros(int from, int len) {
		int limit = from + len;
		while(from < limit) {
			if (this.mem[from] != _00) {
				assertEquals("Byte at mem location " + from, _00, this.mem[from]);
			}
			from++;
		}
	}
	
	private void assertBytes(int from, byte... expected) {
		for (int i = 0; i < expected.length; i++) {
			if (this.mem[from] != expected[i]) {
				assertEquals("Byte at mem location " + from, expected[i], this.mem[from]);
			}
			from++;
		}
	}
	
	private void assertKnownRecords(DeviceHandler d, int recBase, int recLen) { 
		
		// cyl 0 / head 0 / record 1 => all bytes are zero
		this.readCmsRecord(d, 0, 0, 1, recBase, recLen);
		this.assertZeros(recBase, recLen);
		System.out.println("** cyl 0 / head 0 / record 1 .. OK\n");
		
		// cyl 0 / head 0 / record 2 => all bytes are zero
		this.readCmsRecord(d, 0, 0, 2, recBase, recLen);
		this.assertZeros(recBase, recLen);
		System.out.println("** cyl 0 / head 0 / record 2 .. OK\n");
		
		// cyl 0 / head 0 / record 3 => starts with (ebcdic) "CMS=MNT191", remaining bytes are zero
		this.readCmsRecord(d, 0, 0, 3, recBase, recLen);
		this.assertBytes(recBase, _C3, _D4, _E2, _7E, _D4, _D5, _E3, _F1, _F9, _F1);
		this.assertZeros(recBase + 10, recLen - 10);
		System.out.println("** cyl 0 / head 0 / record 3 .. OK\n");
		
		// cyl 0 / head 14 / record 7
		this.readCmsRecord(d, 0, 14, 7, recBase, recLen);
		this.assertBytes(recBase, _40, _40, _40, _40, _40, _40, _40, _C3, _D4, _E2, _D3, _C9, _C2, _40, _40, _40);
		this.assertBytes(recBase + 0x0310, _F1,_C4,_F9,_F4,_F0,_40,_40,_40,_40,_40,_40,_40,_40,_40,_40,_40);
		System.out.println("** cyl 0 / head 14 / record 7 .. OK\n");
		
		// cyl 1 / head 18 / record 15
		this.readCmsRecord(d, 1, 18, 15, recBase, recLen);
		this.assertBytes(recBase, _D3,_C5,_40,_D4,_E2,_E2,_40,_E5,_E4,_C1,_40,_E2,_E3,_C1,_C7,_C9);
		this.assertBytes(recBase + 0x0310, _40,_D5,_E4,_C3,_D3,_C5,_E4,_E2,_40,_D3,_C9,_D5,_D2,_40,_C5,_C4);
		System.out.println("** cyl 1 / head 18 / record 15 .. OK\n");
		
		// cyl 3 / head 0 / record 5
		this.readCmsRecord(d, 3, 0, 5, recBase, recLen);
		this.assertBytes(recBase, _F7,_F2,_40,_C4,_F2,_F0,_F7,_40,_F4,_F4,_C2,_F8,_40,_C3,_F1,_C3);
		this.assertBytes(recBase + 0x0310, _40,_40,_40,_40,_F0,_F0,_F0,_F8,_F8,_C3,_40,_F1,_F2,_F6,_F6,_40);
		System.out.println("** cyl 3 / head 0 / record 5 .. OK\n");
		
		// cyl 4 / head 0 / record 17
		this.readCmsRecord(d, 4, 0, 17, recBase, recLen);
		this.assertBytes(recBase, _01,_48,_09,_2F,_00,_00,_01,_05,_06,_00,_00,_00,_00,_00,_00,_64);
		this.assertBytes(recBase + 0x0310, _AE,_00,_1D,_00,_00,_19,_00,_14,_00,_23,_00,_2B,_00,_4A,_00,_00);
		System.out.println("** cyl 4 / head 0 / record 17 .. OK\n");
		
		// cyl 10 / head 12 / record 1
		this.readCmsRecord(d, 10, 12, 1, recBase, recLen);
		this.assertBytes(recBase, _2F,_2F,_2F,_2F,_2F,_1B,_0D,_0E,_1F,_12,_0C,_0E,_2F,_0A,_0D,_0D);
		this.assertBytes(recBase + 0x0310, _01,_01,_00,_34,_C4,_10,_27,_00,_00,_12,_00,_14,_00,_1B,_00,_12);
		System.out.println("** cyl 10 / head 12 / record 1 .. OK\n");
		
		// cyl 12 / head 9 / record 17
		this.readCmsRecord(d, 12, 9, 17, recBase, recLen);
		this.assertBytes(recBase, _00,_00,_00,_00,_00,_12,_00,_38,_AF,_10,_21,_00,_00,_12,_00,_14);
		this.assertBytes(recBase + 0x0310, _00,_00,_0A,_02,_0D,_0C,_10,_04,_0A,_28,_00,_2D,_2F,_15,_12,_2A);
		System.out.println("** cyl 12 / head 9 / record 17 .. OK\n");
		
		// cyl 13 / head 6 / record 8
		this.readCmsRecord(d, 13, 6, 8, recBase, recLen);
		this.assertBytes(recBase, _03,_04,_00,_00,_00,_00,_00,_48,_AF,_11,_21,_00,_00,_12,_00,_14);
		this.assertBytes(recBase + 0x0310, _1F,_0A,_12,_18,_0B,_01,_48,_09,_2F,_00,_00,_04,_09,_02,_00,_00);
		System.out.println("** cyl 13 / head 6 / record 8 .. OK\n");
		
		// cyl 14 / head 23 / record 16
		this.readCmsRecord(d, 14, 23, 16, recBase, recLen);
		this.assertBytes(recBase, _95,_1F,_00,_0C,_01,_00,_31,_08,_00,_01,_00,_02,_FC,_85,_00,_0C);
		this.assertBytes(recBase + 0x0310, _00,_18,_80,_00,_31,_75,_00,_01,_00,_00,_87,_18,_1B,_0D,_1F,_03);
		System.out.println("** cyl 14 / head 23 / record 16 .. OK\n");
		
		// cyl 17 / head 29 / record 9
		this.readCmsRecord(d, 17, 29, 9, recBase, recLen);
		this.assertBytes(recBase, _80,_00,_2D,_57,_00,_00,_00,_00,_00,_01,_1B,_0C,_1E,_0C,_11,_0D);
		this.assertBytes(recBase + 0x0310, _0D,_1F,_00,_08,_95,_1F,_00,_0C,_01,_00,_31,_08,_00,_01,_00,_03);
		System.out.println("** cyl 17 / head 29 / record 9 .. OK\n");
		
		// cyl 26 / head 28 / record 5
		this.readCmsRecord(d, 26, 28, 5, recBase, recLen);
		this.assertBytes(recBase, _00,_00,_21,_00,_E3,_E8,_D7,_E4,_D5,_E2,_E4,_D7,_02,_C4,_D3,_01);
		this.assertBytes(recBase + 0x0310, _00,_00,_1B,_00,_00,_01,_00,_18,_00,_00,_21,_00,_E3,_E8,_D7,_E4);
		System.out.println("** cyl 26 / head 28 / record 5 .. OK\n");
		
		// cyl 28 / head 20 / record 11
		this.readCmsRecord(d, 28, 20, 11, recBase, recLen);
		this.assertBytes(recBase, _00,_00,_08,_21,_15,_02,_30,_04,_00,_00,_30,_2F,_18,_0F,_00,_14);
		this.assertBytes(recBase + 0x0310, _AF,_00,_21,_08,_00,_00,_00,_00,_00,_00,_05,_11,_30,_2A,_01,_30);
		System.out.println("** cyl 28 / head 20 / record 11 .. OK\n");
		
		// cyl 29 / head 4 / record 19
		this.readCmsRecord(d, 29, 4, 19, recBase, recLen);
		this.assertBytes(recBase, _02,_D9,_D3,_C4,_40,_40,_40,_40,_40,_40,_00,_38,_40,_40,_40,_40);
		this.assertBytes(recBase + 0x0310, _0D,_01,_F7,_48,_0C,_01,_F7,_4C,_D5,_C1,_E2,_F0,_F4,_F5,_F2,_F0);
		System.out.println("** cyl 29 / head 4 / record 19 .. OK\n");
		
		// cyl 34 / head 29 / record 19 (last cyl, last head, last record) => all bytes are zero
		this.readCmsRecord(d, 34, 29, 19, recBase, recLen);
		this.assertZeros(recBase, recLen);
		System.out.println("** cyl 34 / head 29 / record 19 .. OK\n"); 
		
	}
	
	private void writeCmsRecord(DeviceHandler d, int cyl, int head, int record, int memFrom, int memCount) {
		// setup CAW
		int ccwAddr = 1024;
		byte protKey = _F0;
		this.setCAW(ccwAddr, protKey);
		
		// setup 00CCHHR bytes at 1200
		// -> Seek uses first 6 bytes: 00CCHH
		// -> SearchIdEqual used last 5 bytes: CCHHR 
		this.mem[1200] = _00;
		this.mem[1201] = _00;
		this.mem[1202] = (byte)((cyl >> 8) & 0xFF);
		this.mem[1203] = (byte)(cyl & 0xFF);
		this.mem[1204] = (byte)((head >> 8) & 0xFF);
		this.mem[1205] = (byte)(head & 0xFF);
		this.mem[1206] = (byte)(record & 0xFF);
		
		// setup some sector id at 1210
		this.mem[1210] = _0F; // 15
		
		// setup the CCW-chain (based on the CMS read chain)
		ccwAddr = this.setCCW(ccwAddr, mkCmdControl(OP_CONTROL_Seek), 1200, CcwFlags_CC, 6);
		ccwAddr = this.setCCW(ccwAddr, mkCmdControl(OP_CONTROL_SetSector), 1210, CcwFlags_CC, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_SearchIdEqual), 1202, CcwFlags_CC, 5);
		ccwAddr = this.setCCW(ccwAddr, mkCmdTIC(), ccwAddr - 8, CcwFlags_None, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteData), memFrom, CcwFlags_SLI, memCount);
		
		// do the IO
		boolean wasOk = d.processCAW();
		System.out.println();
		assertTrue("Result of d.processCAW()", wasOk);
		d.storeCSW();
	}
	
	private final byte[] fillData = { _33, _EE, _55, _CC, _77, _AA, _99, _88, _BB, _66, _DD, _44, _FF };
	
	private void verifyModifiedRecord(DeviceHandler d, int cyl, int head, int record, int recBase, int recLen) {
		
		// clear buffer memory
		int curr = recBase;
		int limit = recBase + recLen;
		while(curr < limit) { this.mem[curr++] = _00; }
		
		// read the record
		this.readCmsRecord(d, cyl, head, record, recBase, recLen);
		
		// verify data
		int src = recBase;
		int trg = 0;
		for (int i = 0; i < recLen; i++) {
			byte ref = this.fillData[trg++];
			if (trg >= this.fillData.length) { trg = 0; }
			if (this.mem[src] != ref) {
				assertEquals("Expected modified the read byte at: " + src, ref, this.mem[src]);
			}
			src++;
		}
	}
	
	private void modifyRecords(DeviceHandler d, int recBase, int recLen) {
		
		// fill record i/o area with "known" data
		int src = 0;
		int trg = recBase;
		for (int i = 0; i < recLen; i++) {
			this.mem[trg++] = this.fillData[src++];
			if (src >= this.fillData.length) { src = 0; }
		}
		
		// write some records
		this.writeCmsRecord(d, 3, 0, 4, recBase, recLen);
		this.writeCmsRecord(d, 4, 0, 16, recBase, recLen);
		this.writeCmsRecord(d, 10, 12, 2, recBase, recLen);
		this.writeCmsRecord(d, 12, 9, 16, recBase, recLen);
		this.writeCmsRecord(d, 13, 6, 17, recBase, recLen);
		this.writeCmsRecord(d, 14, 23, 15, recBase, recLen);
		this.writeCmsRecord(d, 17, 29, 8, recBase, recLen);
		this.writeCmsRecord(d, 26, 28, 4, recBase, recLen);
		this.writeCmsRecord(d, 28, 20, 10, recBase, recLen);
		this.writeCmsRecord(d, 29, 4, 18, recBase, recLen);
		this.writeCmsRecord(d, 34, 29, 18, recBase, recLen);
	}
	
	private void assertModifiedRecords(DeviceHandler d, int recBase, int recLen) {
		this.verifyModifiedRecord(d, 3, 0, 4, recBase, recLen);
		this.verifyModifiedRecord(d, 4, 0, 16, recBase, recLen);
		this.verifyModifiedRecord(d, 10, 12, 2, recBase, recLen);
		this.verifyModifiedRecord(d, 12, 9, 16, recBase, recLen);
		this.verifyModifiedRecord(d, 13, 6, 17, recBase, recLen);
		this.verifyModifiedRecord(d, 14, 23, 15, recBase, recLen);
		this.verifyModifiedRecord(d, 17, 29, 8, recBase, recLen);
		this.verifyModifiedRecord(d, 26, 28, 4, recBase, recLen);
		this.verifyModifiedRecord(d, 28, 20, 10, recBase, recLen);
		this.verifyModifiedRecord(d, 29, 4, 18, recBase, recLen);
		this.verifyModifiedRecord(d, 34, 29, 18, recBase, recLen);
	}
	
	private static String DIR_TEST = "./test/";
	
	private static String FN_TEST_DDR_DRIVE = "minidisk_maint191_0..34.aws";
	private static String FN_TEST_DDR_SEGMENT = "ddr-NAMEDSYS-CMS-231-001-001.aws";

	@Test
	public void testCkdc3350Drive() {
		
		// loading a ddr tape and create a new drive
		String inFilename = DIR_TEST + FN_TEST_DDR_DRIVE;
		CkdcDrive drive = null;
		String what = "initial";
		try {
			what = "open input file";
			FileInputStream fis = new FileInputStream(inFilename);
			
			what = "read DDR tape";
			Vm370DdrCkdcLoader loader = new Vm370DdrCkdcLoader();
			drive = loader.loadDdrTapeToCkdcDrive(fis);
			
			what = "close input file";
			fis.close();
		} catch (Exception exc) {
			System.out.println("** Error while: " + what);
			exc.printStackTrace();
			fail("Unable to load 3350 CKDC drive from DDR file");
		}
		if (drive == null) { return; } // keep the compiler happy
		
		// setup the device handler for the drive
		drive.setEventTracker(this.eventLogger);
		DeviceHandler d = new DeviceHandler(this.mem, drive, 0x191, this.eventLogger);
		
		// exercise the fresh drive with read operations (operation known records)
		int recBase = 2048;
		int recLen = 800;
		
		this.assertKnownRecords(d, recBase, recLen);
		
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			drive.save(baos, false);
		} catch (Exception exc) {
			System.out.println("** Error while saving base file for drive");
			exc.printStackTrace();
			fail("Unable to save base file for drive");
		}
		byte[] driveBaseFile = baos.toByteArray();
		System.out.printf("\n**\n** size of driveBaseFile: %d bytes\n**\n\n", driveBaseFile.length);
		
		CkdcDrive drive2 = null;
		try {
			ByteArrayInputStream driveBaseFis = new ByteArrayInputStream(driveBaseFile);
			drive2 = new CkdcDrive(null, driveBaseFis, this.eventLogger);
			d = new DeviceHandler(this.mem, drive2, 0x191, this.eventLogger);
		} catch (Exception exc) {
			System.out.println("** Error while loading base file for drive");
			exc.printStackTrace();
			fail("Unable to load base file for drive");
		}
		
		this.assertKnownRecords(d, recBase, recLen);
		this.modifyRecords(d, recBase, recLen);
		this.assertModifiedRecords(d, recBase, recLen);
		this.assertKnownRecords(d, recBase, recLen);
		
		baos = new ByteArrayOutputStream();
		try {
			drive2.save(baos, true);
		} catch (Exception exc) {
			System.out.println("** Error while saving delta file for drive after changes");
			exc.printStackTrace();
			fail("Unable to save delta file for drive after changes");
		}
		byte[] driveDeltaFile = baos.toByteArray();
		System.out.printf("\n**\n** size of driveDeltaFile: %d bytes\n**\n\n", driveDeltaFile.length);
		
		CkdcDrive drive3 = null;
		try {
			ByteArrayInputStream driveBaseFis = new ByteArrayInputStream(driveBaseFile);
			ByteArrayInputStream driveDeltaFis = new ByteArrayInputStream(driveDeltaFile);
			drive3 = new CkdcDrive(driveDeltaFis, driveBaseFis, this.eventLogger);
			d = new DeviceHandler(this.mem, drive3, 0x191, this.eventLogger);
		} catch (Exception exc) {
			System.out.println("** Error while loading delta+base file for drive");
			exc.printStackTrace();
			fail("Unable to load delta+base file for drive");
		}
		
		this.assertKnownRecords(d, recBase, recLen);
		this.assertModifiedRecords(d, recBase, recLen);
	}
	
	private void checkBytes(byte[] data, int at, byte... req) {
		int src = 0;
		int trg = at;
		int limit = req.length;
		while(src < limit) {
			if (data[trg] != req[src]) {
				assertEquals("Byte at offset " + trg + " (src offset " + src + ")", req[src], data[trg]);
			}
			src++;
			trg++;
		}
	}

	@Test
	public void testCkdc3350SavedSegment() {
		
		// loading a ddr tape and create a new drive
		String inFilename = DIR_TEST + FN_TEST_DDR_SEGMENT;
		Vm370DdrCkdcLoader loader = new Vm370DdrCkdcLoader();
		String what = "initial";
		
		byte[] segmentData = null;
		
		try {
			what = "open input file";
			FileInputStream fis = new FileInputStream(inFilename);
			what = "open output stream";
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			
			what = "write the drive to disk";
			if (!loader.extractSavedSegment(fis, baos, 33)) {
				fail("Unable to extract segment data from CKD drive");
			}
			
			what = "close input file";
			fis.close();
			
			what = "access segment content from ByteArrayOutputStream";
			segmentData = baos.toByteArray();
		} catch (Exception exc) {
			System.out.println("** Error while: " + what);
			exc.printStackTrace();
			fail("Unable to load 3350 segemtnt drive from DDR file");
		}
		
		assertEquals("Size of segment file", 34 * 4096, segmentData.length);
		
		int baseOffset = 0;
		this.checkBytes(segmentData, baseOffset + 0x0000, _00,_04,_00,_CA,_80,_02,_01,_28,_00,_00,_00,_10,_00,_00,_08,_48);
		this.checkBytes(segmentData, baseOffset + 0x00A0, _C4,_00,_00,_00,_00,_00,_02,_00,_F6,_F6,_F6,_F6,_F6,_F6,_F6,_E6);

		
		baseOffset = 4096;
		this.checkBytes(segmentData, baseOffset + 0x0200, _C3,_D4,_E2,_40,_E5,_C5,_D9,_E2,_C9,_D6,_D5,_40,_F6,_4B,_F0,_40); // "CMS VERSION 6.0 "
		
		
		baseOffset = 33 * 4096;
		this.checkBytes(segmentData, baseOffset + 0x0A60, _C3,_D4,_E2,_C2,_C1,_E3,_C3,_C8,_FF,_FF,_FF,_FF,_FF,_FF,_FF,_FF);
		this.checkBytes(segmentData, baseOffset + 0x0F50, _A8,_AD,_D4,_02,_A8,_8B,_00,_00,_00,_00,_A8,_00,_A8,_00,_A8,_00);
	}
	
	private boolean /* success? */ doSense(DeviceHandler d, int memAt, int memCount) {
		// setup CAW
		int ccwAddr = 1024;
		byte protKey = _90;
		this.setCAW(ccwAddr, protKey);
		
		// setup the CCW
		ccwAddr = this.setCCW(ccwAddr, mkCmdSense(0x00), memAt, CcwFlags_SLI, memCount);
		
		// execute the CCW
		boolean wasOk = d.processCAW();
		d.storeCSW();
		return wasOk;
	}
	
	private void putBytes(int at, byte... bs) {
		for (int i = 0; i < bs.length; i++) {
			this.mem[at++] = bs[i];
		}
	}

	@Test
	public void testCkdcCreateNewDrive() {
		// relevant coordinates for subsequent checks
		int senseLocation = 3172;
		int senseLen = 3;
		int unitStatusLocation = ADDR_CSW + 4;
		int channelStatusLocation = ADDR_CSW + 5;
		
		// create a new 3350 drive from scratch
		Vm370DdrCkdcLoader loader = new Vm370DdrCkdcLoader();
		CkdcDrive drive = loader.createNewDrive(CkdDriveType.ckd3350, 42, "TST");
		assertNotNull("result of createDriveFile()", drive);
		
		// try to read a CMS record from an existing but unformatted track (no records on track)
		// => must fail with UnitCheck (inkl. ChannelEnd, DeviceEnd) in PSW and RecordNotFound in Sense-Byte[1]
		DeviceHandler d = new DeviceHandler(this.mem, drive, 0x299, this.eventLogger);
		drive.setEventTracker(this.eventLogger);
		this.eventLogger.logLine("");
		this.eventLogger.logLine("**");
		this.eventLogger.logLine("** read a CMS record from an existing but unformatted track (no records on track)");
		this.eventLogger.logLine("**");
		this.readCmsRecord(d, 
				1 /* cyl */, 1 /* head */, 1 /* record */,
				4096 /* memTo */, 800 /* memCount */,
				false /* expectedOutcome */);
		assertEquals("UnitStatus after failed read ( ChannelEnd + DeviceEnd + UnitCheck )", (byte)0x0E, this.mem[unitStatusLocation]);
		assertEquals("ChannelStatus after failed read", (byte)0x00, this.mem[channelStatusLocation]);
		
		// do a sense to check the reason for the read failure
		boolean senseOk = this.doSense(d, senseLocation, senseLen);
		assertTrue("success of a sense on drive after read failure", senseOk);
		assertEquals("sense-byte offset 0 after failed read", (byte)0x00, this.mem[senseLocation]);
		assertEquals("sense-byte offset 1 after failed read ( NoRecordFound )", (byte)0x08, this.mem[senseLocation+1]);
		assertEquals("sense-byte offset 2 after failed read", (byte)0x00, this.mem[senseLocation+2]);
		
		// try to read from cylinder 42 (does not exist)
		// => must fail with UnitCheck in CSW and CommandReject+SeekCheck in Sense-Byte[0]
		this.eventLogger.logLine("");
		this.eventLogger.logLine("**");
		this.eventLogger.logLine("** read from cylinder 42 (does not exist)");
		this.eventLogger.logLine("**");
		this.readCmsRecord(d, 
				42 /* cyl */, 0 /* head */, 0 /* record */,
				4096 /* memTo */, 800 /* memCount */,
				false /* expectedOutcome */);
		assertEquals("UnitStatus after failed read ( ChannelEnd + DeviceEnd + UnitCheck )", (byte)0x0E, this.mem[unitStatusLocation]);
		assertEquals("ChannelStatus after failed read", (byte)0x00, this.mem[channelStatusLocation]);
		senseOk = this.doSense(d, senseLocation, senseLen);
		assertTrue("success of a sense on drive after read failure", senseOk);
		assertEquals("sense-byte offset 0 after failed read ( CommandReject + SeekCheck )", (byte)0x81, this.mem[senseLocation]);
		assertEquals("sense-byte offset 1 after failed read", (byte)0x00, this.mem[senseLocation+1]);
		assertEquals("sense-byte offset 2 after failed read", (byte)0x00, this.mem[senseLocation+2]);
		
		/*
		** format track 0 with a CCW-chain like CMS would use
		*/
		this.eventLogger.logLine("");
		this.eventLogger.logLine("**");
		this.eventLogger.logLine("** format track 0 with a CCW-chain like CMS would use");
		this.eventLogger.logLine("**");
		
		// 00CCHHR field for 1st "Seek" + 1st "Search Id"
		putBytes(0x08D2,
			_00, _00, _00, _00, _00, _00, _00
			);
		
		// file mask for "Set File Mask"
		putBytes(0x0A26,
			_C0
			);
		
		// Data content for (1st) "Write Record Zero" and "Search Id"
		putBytes(0x08D4,
			_00, _00, _00, _00, _00, _00, _00, _08,   _00, _00, _00, _00, _00, _00, _00, _00
			);
		
		// value for "Set Sector"
		putBytes(0x0E88,
			_00
			);
		
		// 00CCHH field for 2nd "Seek" + "Write Home Address"
		putBytes(0x08E4,
			_00, _00, _00, _00, _00, _00
			);
		
		// Data content for (2nd) "Write Record Zero"
		putBytes(0x08E6,
			_00, _00, _00, _00, _00, _00, _00, _08,   _00, _00, _00, _00, _00, _00, _00, _00
			);
		
		// 8-byte record init sequences for "Write CKD" (only the C-part)
		putBytes(0x08F8,
			_00, _00, _00, _00,   _01, _00, _03, _20,
			_00, _00, _00, _00,   _02, _00, _03, _20,
			_00, _00, _00, _00,   _03, _00, _03, _20,
			_00, _00, _00, _00,   _04, _00, _03, _20,
			_00, _00, _00, _00,   _05, _00, _03, _20,
			_00, _00, _00, _00,   _06, _00, _03, _20,
			_00, _00, _00, _00,   _07, _00, _03, _20,
			_7F, _FF, _00, _FF,   _FF, _00, _03, _20, // offset 0x09930 has some dummy entry
			_00, _00, _00, _00,   _08, _00, _03, _20,
			_00, _00, _00, _00,   _09, _00, _03, _20,
			_00, _00, _00, _00,   _0A, _00, _03, _20,
			_00, _00, _00, _00,   _0B, _00, _03, _20,
			_00, _00, _00, _00,   _0C, _00, _03, _20,
			_00, _00, _00, _00,   _0D, _00, _03, _20,
			_00, _00, _00, _00,   _0E, _00, _03, _20,
			_00, _00, _00, _00,   _0F, _00, _03, _20,
			_00, _00, _00, _00,   _10, _00, _03, _20,
			_00, _00, _00, _00,   _11, _00, _03, _20,
			_00, _00, _00, _00,   _12, _00, _03, _20,
			_00, _00, _00, _00,   _13, _00, _03, _20,
			_00, _00, _00, _00,   _14, _00, _03, _20,
			_00, _00, _00, _00,   _15, _00, _03, _20,
			_00, _00, _00, _00,   _16, _00, _03, _20,
			_00, _00, _00, _00,   _17, _00, _03, _20,
			_00, _00, _00, _00,   _18, _00, _03, _20,
			_00, _00, _00, _00,   _19, _00, _03, _20,
			_00, _00, _00, _00,   _1A, _00, _03, _20,
			_00, _00, _00, _00,   _1B, _00, _03, _20,
			_00, _00, _00, _00,   _1C, _00, _03, _20,
			_00, _00, _00, _00,   _1D, _00, _03, _20,
			_00, _00, _00, _00,   _1E, _00, _03, _20,
			_00, _00, _00, _00,   _1F, _00, _03, _20,
			_00, _00, _00, _00,   _20, _00, _03, _20,
			_00, _00, _00, _00,   _21, _00, _03, _20,
			_00, _00, _00, _00,   _22, _00, _03, _20,
			_00, _00, _00, _00,   _23, _00, _03, _20,
			_00, _00, _00, _00,   _24, _00, _03, _20
			);
		
		// setup CAW
		int ccwAddr = 0x0580;
		byte protKey = _F0;
		this.setCAW(ccwAddr, protKey);
		
		// setup the CCW-chain (based on the CMS format chain)
		ccwAddr = this.setCCW(ccwAddr, mkCmdControl(OP_CONTROL_Seek), 0x08D2, CcwFlags_CC, 6);
		ccwAddr = this.setCCW(ccwAddr, mkCmdControl(OP_CONTROL_SetFileMask), 0x0A26, CcwFlags_CC | CcwFlags_SLI, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdTIC(), 0x05A8, CcwFlags_None, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteRecordZero), 0x08D4, CcwFlags_CC | CcwFlags_SLI, 16);
		ccwAddr = this.setCCW(ccwAddr, mkCmdTIC(), 0x05C0, CcwFlags_None, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdControl(OP_CONTROL_SetSector), 0x0E88, CcwFlags_CC | CcwFlags_SLI, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_SearchIdEqual), 0x08D4, CcwFlags_CC | CcwFlags_SLI, 5);
		ccwAddr = this.setCCW(ccwAddr, mkCmdTIC(), 0x05B0, CcwFlags_None, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x08F8, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0900, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0908, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0910, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0918, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0920, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0928, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdTIC(), 0x0618, CcwFlags_None, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdControl(OP_CONTROL_Seek), 0x08E4, CcwFlags_CC | CcwFlags_SLI, 6);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteHomeAddress), 0x08E5, CcwFlags_CC | CcwFlags_SLI, 5);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteRecordZero), 0x08E6, CcwFlags_CC | CcwFlags_SLI, 16);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0938, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0940, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0948, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0950, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0958, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0960, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0968, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0970, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0978, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0980, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0988, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0990, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdTIC(), 0x0700, CcwFlags_None, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x09A0, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x09A8, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x09B0, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x09B8, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x09C0, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x09C8, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x09D0, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x09D8, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x09E0, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x09E8, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x09F0, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x09F8, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0A00, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0A08, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0A10, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_WriteCountKeyAndData), 0x0A18, CcwFlags_CC | CcwFlags_SLI, 8);
		ccwAddr = this.setCCW(ccwAddr, mkCmdControl(OP_CONTROL_Seek), 0x08D2, CcwFlags_CC | CcwFlags_SLI, 6);
		ccwAddr = this.setCCW(ccwAddr, mkCmdControl(OP_CONTROL_SetSector), 0x0E88, CcwFlags_CC | CcwFlags_SLI, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(OP_WRITE_SearchIdEqual), 0x08D4, CcwFlags_CC | CcwFlags_SLI, 5);
		ccwAddr = this.setCCW(ccwAddr, mkCmdTIC(), 0x0710, CcwFlags_None, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdTIC(), 0x0768, CcwFlags_None, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(OP_READ_ReadCountKeyAndData), 0x0000, CcwFlags_CC | CcwFlags_SLI | CcwFlags_SKIP, 1);
		ccwAddr = this.setCCW(ccwAddr, mkCmdControl(OP_CONTROL_NoOperation), 0x0000, CcwFlags_SLI, 1);
		
		// process and check the CCW-chain
		boolean wasOk = d.processCAW();
		System.out.println();
		assertTrue("Result of d.processCAW()", wasOk);
		d.storeCSW();
		
		// location and length for read / write operations on track 0
		int recBase = 2048;
		int recLen = 800;
		
		// check if records on track 0 are correct
		this.readCmsRecord(d, 0, 0, 1, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 2, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 3, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 4, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 5, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 6, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 7, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 8, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 9, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 10, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 11, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 12, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 13, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 14, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 15, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 16, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 17, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 18, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 19, recBase, recLen); this.assertZeros(recBase, recLen);
		
		// save base file 
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try {
			drive.save(baos, false);
		} catch (Exception exc) {
			System.out.println("** Error while saving base file for drive");
			exc.printStackTrace();
			fail("Unable to save base file for drive");
		}
		byte[] driveBaseFile = baos.toByteArray();
		System.out.printf("\n**\n** size of driveBaseFile: %d bytes\n**\n\n", driveBaseFile.length);
		
		CkdcDrive drive2 = null;
		try {
			ByteArrayInputStream driveBaseFis = new ByteArrayInputStream(driveBaseFile);
			drive2 = new CkdcDrive(null, driveBaseFis, this.eventLogger);
			d = new DeviceHandler(this.mem, drive2, 0x399, this.eventLogger);
		} catch (Exception exc) {
			System.out.println("** Error while loading base file for drive");
			exc.printStackTrace();
			fail("Unable to load base file for drive");
		}
		
		// check if records on track 0 are correct
		this.readCmsRecord(d, 0, 0, 1, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 2, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 3, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 4, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 5, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 6, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 7, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 8, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 9, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 10, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 11, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 12, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 13, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 14, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 15, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 16, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 17, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 18, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 19, recBase, recLen); this.assertZeros(recBase, recLen);
		
		// fill record i/o area with "known" data
		int src = 0;
		int trg = recBase;
		for (int i = 0; i < recLen; i++) {
			this.mem[trg++] = this.fillData[src++];
			if (src >= this.fillData.length) { src = 0; }
		}
		
		// write 5 records (first, 3 underway, last) on track 0
		this.writeCmsRecord(d, 0, 0, 1, recBase, recLen);
		this.writeCmsRecord(d, 0, 0, 5, recBase, recLen);
		this.writeCmsRecord(d, 0, 0, 10, recBase, recLen);
		this.writeCmsRecord(d, 0, 0, 15, recBase, recLen);
		this.writeCmsRecord(d, 0, 0, 19, recBase, recLen);
		
		// check if records on track 0 are correct
		this.verifyModifiedRecord(d, 0, 0, 1, recBase, recLen);
		this.readCmsRecord(d, 0, 0, 2, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 3, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 4, recBase, recLen); this.assertZeros(recBase, recLen);
		this.verifyModifiedRecord(d, 0, 0, 5, recBase, recLen);
		this.readCmsRecord(d, 0, 0, 6, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 7, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 8, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 9, recBase, recLen); this.assertZeros(recBase, recLen);
		this.verifyModifiedRecord(d, 0, 0, 10, recBase, recLen);
		this.readCmsRecord(d, 0, 0, 11, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 12, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 13, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 14, recBase, recLen); this.assertZeros(recBase, recLen);
		this.verifyModifiedRecord(d, 0, 0, 15, recBase, recLen);
		this.readCmsRecord(d, 0, 0, 16, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 17, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 18, recBase, recLen); this.assertZeros(recBase, recLen);
		this.verifyModifiedRecord(d, 0, 0, 19, recBase, recLen);
		
		// save changes to delta file
		baos = new ByteArrayOutputStream();
		try {
			drive2.save(baos, true);
		} catch (Exception exc) {
			System.out.println("** Error while saving delta file for drive after changes");
			exc.printStackTrace();
			fail("Unable to save delta file for drive after changes");
		}
		byte[] driveDeltaFile = baos.toByteArray();
		System.out.printf("\n**\n** size of driveDeltaFile: %d bytes\n**\n\n", driveDeltaFile.length);
		
		// reload the complete drive
		CkdcDrive drive3 = null;
		try {
			ByteArrayInputStream driveBaseFis = new ByteArrayInputStream(driveBaseFile);
			ByteArrayInputStream driveDeltaFis = new ByteArrayInputStream(driveDeltaFile);
			drive3 = new CkdcDrive(driveDeltaFis, driveBaseFis, this.eventLogger);
			d = new DeviceHandler(this.mem, drive3, 0x499, this.eventLogger);
		} catch (Exception exc) {
			System.out.println("** Error while loading delta+base file for drive");
			exc.printStackTrace();
			fail("Unable to load delta+base file for drive");
		}
		
		// check if records on track 0 are correct
		this.verifyModifiedRecord(d, 0, 0, 1, recBase, recLen);
		this.readCmsRecord(d, 0, 0, 2, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 3, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 4, recBase, recLen); this.assertZeros(recBase, recLen);
		this.verifyModifiedRecord(d, 0, 0, 5, recBase, recLen);
		this.readCmsRecord(d, 0, 0, 6, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 7, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 8, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 9, recBase, recLen); this.assertZeros(recBase, recLen);
		this.verifyModifiedRecord(d, 0, 0, 10, recBase, recLen);
		this.readCmsRecord(d, 0, 0, 11, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 12, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 13, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 14, recBase, recLen); this.assertZeros(recBase, recLen);
		this.verifyModifiedRecord(d, 0, 0, 15, recBase, recLen);
		this.readCmsRecord(d, 0, 0, 16, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 17, recBase, recLen); this.assertZeros(recBase, recLen);
		this.readCmsRecord(d, 0, 0, 18, recBase, recLen); this.assertZeros(recBase, recLen);
		this.verifyModifiedRecord(d, 0, 0, 19, recBase, recLen);
		
	}
	
	// -- disabled-- @Test // requires manual interaction!!!!!
	public void testConsoleInput() {
		
		// setup CAW
		int ccwBase = 0x0500;
		int ccwAddr = ccwBase;
		byte protKey = _F0;
		this.setCAW(ccwBase, protKey);
		
		// create input device and handler
		ConsoleSimple cons = new ConsoleSimple(new UserConsoleSerial(new ThreadGroup("Test"), 0x1E, System.in, System.out), 0x1E);
		DeviceHandler d = new DeviceHandler(this.mem, cons, 0x009, this.eventLogger);
		
		// write out a line
		EbcdicHandler text = new EbcdicHandler("Sample output");
		text.addTo(this.mem, 2048);
		ccwAddr = this.setCCW(ccwAddr, mkCmdWrite(ConsoleCommandCodes.WriteAddCR), 2048, CcwFlags_CC, text.getLength());
		ccwAddr = this.setCCW(ccwAddr, mkCmdControl(ConsoleCommandCodes.ControlNOOP), 0x0000, CcwFlags_SLI, 1);
		boolean wasOk = d.processCAW();
		assertTrue("Result of d.processCAW() for console write", wasOk);
		d.storeCSW();
		
		// !! interactively !! read a line
		int readBufferLength = 134;
		ccwAddr = ccwBase;
		ccwAddr = this.setCCW(ccwAddr, mkCmdRead(ConsoleCommandCodes.Read), 2048, CcwFlags_CC | CcwFlags_SLI, readBufferLength);
		ccwAddr = this.setCCW(ccwAddr, mkCmdControl(ConsoleCommandCodes.ControlNOOP), 0x0000, CcwFlags_SLI, 1);
		wasOk = d.processCAW();
		assertTrue("Result of d.processCAW() for console read", wasOk);
		d.storeCSW();
		int rest = ((this.mem[70] & 0xFF) << 8) |(this.mem[71] & 0xFF);
		int inputLength = readBufferLength - rest;
		System.out.printf("result of user input: rest = %d => inputLength = %d\n", rest, inputLength);
	}
	
}
