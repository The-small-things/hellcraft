package net.thesmallthings.hellcraft.music;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OggLengthTest {
	/** A minimal Ogg page: "OggS", version, flags, 8-byte granule, then the rest of a 27-byte header and a body. */
	private static void page(ByteArrayOutputStream out, long granule, byte[] body) {
		out.writeBytes(new byte[]{'O', 'g', 'g', 'S', 0, 0});
		for (int k = 0; k < 8; k++) {
			out.write((int) (granule >>> (8 * k)) & 0xFF);
		}
		out.writeBytes(new byte[13]); // serial, sequence, checksum, segment count (zeroed)
		out.writeBytes(body);
	}

	private static byte[] vorbisIdHeader(int rate) {
		byte[] h = new byte[30];
		h[0] = 0x01;
		System.arraycopy("vorbis".getBytes(), 0, h, 1, 6);
		h[11] = 2; // channels
		h[12] = (byte) rate;
		h[13] = (byte) (rate >>> 8);
		h[14] = (byte) (rate >>> 16);
		h[15] = (byte) (rate >>> 24);
		return h;
	}

	@Test
	void readsDurationFromLastGranuleAndRate() {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		page(out, 0, vorbisIdHeader(44100));
		page(out, 44100L * 10, new byte[64]);
		page(out, 44100L * 183 + 22050, new byte[64]);
		assertEquals(183.5, OggLength.seconds(out.toByteArray()), 1e-9);
	}

	@Test
	void rejectsGarbage() {
		assertEquals(-1, OggLength.seconds(new byte[100]));
	}
}
