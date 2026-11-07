package dev.tim1337.lumio.render;

/**
 * World coordinates for the sky shader, which reads its parameters as bytes. A coordinate is kept whole: a signed 32-bit block number in one texel
 * (red is the lowest byte) and its fraction in 1/256 of a block in a byte of another. Nothing wraps inside the world (its border is far below
 * 2^31), so walking across x = 0 or z = 0 is an ordinary step: an earlier form kept coordinates wrapped at 65536 blocks, which turned -0.1 into
 * 65535.9, a place with other clouds and another aurora. The shader adds the bytes in the order of {@link #decodeLikeShader}, which is exact in
 * 32-bit floats for every coordinate within 2^24 blocks of the origin.
 */
public final class SkyCoordinates {
	/** Fractions of a block per step of the fraction byte. */
	static final int FRACTION_STEPS = 256;
	private static final long SIGN_OFFSET = 1L << 31;
	private static final long BLOCK_SPACE = 1L << 32;

	private SkyCoordinates() {
	}

	/** @return the block of the coordinate, rounded down (also below zero), as the unsigned 32 bits the four bytes of a texel carry */
	public static long whole(double coordinate) {
		return Math.floorMod((long) Math.floor(coordinate) + SIGN_OFFSET, BLOCK_SPACE);
	}

	/** @return one byte of {@link #whole}: 0 is the lowest */
	public static int wholeByte(long whole, int index) {
		return (int) (whole >>> (index * 8) & 0xFF);
	}

	/** @return the part of the coordinate above its block, in 1/256 of a block, from 0 to 255 */
	public static int fraction(double coordinate) {
		double above = coordinate - Math.floor(coordinate);
		return Math.min(FRACTION_STEPS - 1, (int) (above * FRACTION_STEPS));
	}

	/**
	 * The coordinate as the shader rebuilds it from the bytes, in 32-bit floats and in its order: the highest byte with the sign offset first (a
	 * whole multiple of 2^24, exact), then the lower bytes, then the fraction.
	 */
	public static float decodeLikeShader(int byte0, int byte1, int byte2, int byte3, int fraction) {
		float high = (float) byte3 * 16777216f - 2147483648f;
		float block = high + ((float) byte2 * 65536f + (float) byte1 * 256f + (float) byte0);
		return block + (float) fraction / FRACTION_STEPS;
	}
}
