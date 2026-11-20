package org.eclipse.jgit.compatible;

import java.util.zip.DataFormatException;
import java.util.zip.Inflater;

/**
 * Android Inflater Compatible
 */
public class InflaterCompat extends Inflater {

	private static final byte[] oneByteArray = new byte[1];

	/**
	 * @param nowrap
	 *            if true then support GZIP compatible compression
	 */
	public InflaterCompat(boolean nowrap) {
		super(nowrap);
	}

	@SuppressWarnings("nls")
	@Override
	public int inflate(byte[] b, int off, int len) throws DataFormatException {
		if (len != 0) {
			return super.inflate(b, off, len);
		}

		int bytesInflated = super.inflate(oneByteArray, 0, 1);
		// have to pretend to want at least one byte so that
		// the finished flag is correctly set
		if (bytesInflated > 0) {
			throw new RuntimeException(
					"The Android Inflater Compat has served you ill, we were not supposed to read any data...");
		}
		return 0;
	}

	@Override
	public void end() {
		// DO NOT call end method on wrapped inflater, because the InflaterCache
		// will want to re-use it
	}
}