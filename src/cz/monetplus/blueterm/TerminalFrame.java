package cz.monetplus.blueterm;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Arrays;

import android.util.Log;

public class TerminalFrame {

	private static final String TAG = "TerminalFrame";

	/**
	 * Port 2Bytes
	 */
	private TerminalPorts port;

	/**
	 * Protokol data
	 */
	private byte[] data;

	/**
	 * CRC - PPP-FCS (RFC 1134)
	 */
	private int crc;

	public TerminalFrame() {
		super();
	}

	public TerminalFrame(byte[] data) {
		super();

		parseFrame(data);
	}

	public TerminalFrame(int i, byte[] data) {
		this.setPort(i);
		this.setData(data);
		this.setCrc(CRCFCS.pppfcs(CRCFCS.PPPINITFCS, createCountedPart()));
	}

	public TerminalPorts getPort() {
		return port;
	}

	public void setPort(int port) {
		this.port = TerminalPorts.valueOf(port & 0xFFFF);
	}

	public byte[] getData() {
		return data;
	}

	public void setData(byte[] data) {
		this.data = data;
	}

	public int getCrc() {
		return crc;
	}

	public void setCrc(int crc) {
		this.crc = crc;
	}

	private byte[] createCountedPart() {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();

		try {
			// stream.write(ByteBuffer.allocate(2).putInt(this.getPort()).array());
			stream.write((byte) (this.getPort().getPort() >> 8) & 0xFF);
			stream.write((byte) this.getPort().getPort() & 0xFF);
			stream.write(this.getData());
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}

		return stream.toByteArray();
	}

	public byte[] createFrame() {
		ByteArrayOutputStream stream = new ByteArrayOutputStream();

		try {
			// stream.write(ByteBuffer.allocate(2).putInt(this.getPort()).array());
			stream.write((byte) (this.getPort().getPort() >> 8) & 0xFF);
			stream.write((byte) this.getPort().getPort() & 0xFF);
			stream.write(this.getData());
			// stream.write(ByteBuffer.allocate(2).putInt(this.getCrc()).array());
			stream.write((byte) this.getCrc() & 0xFF);
			stream.write((byte) (this.getCrc() >> 8) & 0xFF);
		} catch (IOException e) {
			Log.e(TAG, e.getMessage());
		}

		return stream.toByteArray();
	}

	public void parseFrame(byte[] data) {
		if (data != null && data.length > 4) {
			this.setCrc((data[data.length - 1] << 8) + data[data.length - 2]);

			if (this.getCrc() == CRCFCS.pppfcs(CRCFCS.PPPINITFCS, data,
					data.length - 2)) {
				this.setPort((data[0] << 8) + data[1]);
				this.setData(Arrays.copyOfRange(data, 2, data.length - 1));
			} else {
				Log.d(TAG, "Corrupted terminal frame");
			}
		} else {
			Log.d(TAG, "Corrupted terminal frame");
		}
	}
}
