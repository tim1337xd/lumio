package dev.tim1337.lumio.features.music;

/**
 * How far ahead of the playback position the lyrics' clock runs, kept steady. The lead that is wanted changes with the next line (a long line
 * comes a little earlier than a short one); taken as it is, the clock jumped ahead by up to a third of a second when the line after a short one
 * was long, over a whole short line, and stepped back when it was short again, so a line started with the progress of the one before. Here the
 * lead only drifts toward what is wanted, a little per second of song, so the clock always moves forward, a bit faster or slower than the song,
 * and passes every line's start. A seek takes the wanted lead at once.
 */
final class LyricLead {
	/** A step of the position larger than this is a seek. */
	static final long SEEK_MS = 2000L;
	/** The lead changes by at most this share of the song's time that passed. */
	static final float DRIFT = 0.15f;

	private long lastPosition = Long.MIN_VALUE;
	private float lead;

	/**
	 * @param position the playback position, in milliseconds
	 * @param wanted   the lead wanted now, in milliseconds
	 * @return the lead to use at this position
	 */
	long at(long position, long wanted) {
		if (lastPosition == Long.MIN_VALUE || Math.abs(position - lastPosition) > SEEK_MS) {
			lead = wanted;
		} else {
			float most = Math.abs(position - lastPosition) * DRIFT;
			lead += Math.clamp(wanted - lead, -most, most);
		}
		lastPosition = position;
		return Math.round(lead);
	}
}
