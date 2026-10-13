package net.thesmallthings.hellcraft.music;

/**
 * Reads the duration of an Ogg Vorbis file without decoding it: the granule position of the last
 * Ogg page is the total number of samples, and the Vorbis identification header gives the rate.
 */
public final class OggLength {
	private OggLength() {
	}

	/** Duration in seconds, or -1 if the data isn't a readable Ogg Vorbis stream. */
	public static double seconds(byte[] data) {
		int rate = sampleRate(data);
		long samples = lastGranule(data);
		if (rate <= 0 || samples <= 0) {
			return -1;
		}
		return samples / (double) rate;
	}

	static int sampleRate(byte[] data) {
		// the first packet is the identification header: 0x01 "vorbis" version(4) channels(1) rate(4)
		for (int i = 0; i + 16 < data.length && i < 4096; i++) {
			if (data[i] == 0x01 && data[i + 1] == 'v' && data[i + 2] == 'o' && data[i + 3] == 'r'
					&& data[i + 4] == 'b' && data[i + 5] == 'i' && data[i + 6] == 's') {
				return (int) readLE(data, i + 12, 4);
			}
		}
		return -1;
	}

	static long lastGranule(byte[] data) {
		for (int i = data.length - 27; i >= 0; i--) {
			if (data[i] == 'O' && data[i + 1] == 'g' && data[i + 2] == 'g' && data[i + 3] == 'S' && data[i + 4] == 0) {
				return readLE(data, i + 6, 8);
			}
		}
		return -1;
	}

	private static long readLE(byte[] data, int offset, int length) {
		long value = 0;
		for (int k = length - 1; k >= 0; k--) {
			value = (value << 8) | (data[offset + k] & 0xFF);
		}
		return value;
	}
}
