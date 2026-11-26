package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.Random;

/**
 * The scroll of a Cut-out column as the player sees it: a sample song's rows on the stack, moved by the smooth song clock while the player's
 * position comes in polls that step it forward and back, and the lead changes with the lines. Sampled at 30 and at 240 frames a second: the place,
 * its speed and its acceleration have no step at line starts or clock corrections, and both frame rates give the same path.
 */
class CutoutMotionTest {
	private static final float ROOM = 260f;
	private static final float GAP = 12f;
	/** Rows of a song: fast lines, a slow one, a long pause, uneven heights. */
	private static final long[] STARTS = {1_000L, 1_700L, 2_300L, 3_000L, 3_600L, 4_200L, 7_900L, 9_400L, 10_100L, 16_800L, 17_300L, 17_900L, 18_400L,
		19_000L, 19_500L, 22_000L};
	private static final float[] HEIGHTS = {40f, 40f, 70f, 40f, 40f, 110f, 40f, 60f, 40f, 40f, 40f, 40f, 90f, 40f, 40f, 40f};
	private static final double END_SECONDS = 26.0;
	/** The player answers this often, and its position is taken up by a third of the difference each time (as the service does). */
	private static final double POLL_SECONDS = 0.5;

	private static CutoutScroll stack() {
		CutoutScroll stack = new CutoutScroll();
		float top = 0f;
		for (int i = 0; i < STARTS.length; i++) {
			stack.add(STARTS[i], top + HEIGHTS[i], ROOM);
			top += HEIGHTS[i] + GAP;
		}
		return stack;
	}

	/** A player whose reported position steps at each poll, by up to 36 ms forward or back, from a fixed seed. */
	private static final class Player {
		private final Random random = new Random(7L);
		private double positionMs;
		private double stampSeconds;
		private double nextPoll;

		/** @return its position now; the polls come at their own times, whatever the frame rate */
		double at(double seconds) {
			while (seconds >= nextPoll) {
				double counted = positionMs + (nextPoll - stampSeconds) * 1000.0;
				double reported = nextPoll * 1000.0 + (random.nextDouble() - 0.5) * 240.0;
				positionMs = counted + (reported - counted) * 0.3;
				stampSeconds = nextPoll;
				nextPoll += POLL_SECONDS;
			}
			return positionMs + (seconds - stampSeconds) * 1000.0;
		}

		double stamp() {
			return stampSeconds;
		}
	}

	/** @return the lead for the line coming at a position: longer and shorter lines take turns */
	private static double lead(double positionMs) {
		int line = 0;
		while (line < STARTS.length && STARTS[line] <= positionMs + 300.0) {
			line++;
		}
		return line % 2 == 0 ? 300.0 : 520.0;
	}

	/** @return the stack's place at every frame of a run at a frame rate */
	private static double[] run(int fps) {
		CutoutScroll stack = stack();
		SongClock clock = new SongClock();
		Player player = new Player();
		int frames = (int) Math.round(END_SECONDS * fps);
		double[] places = new double[frames + 1];
		for (int k = 0; k <= frames; k++) {
			double now = (double) k / fps;
			double position = player.at(now);
			clock.update(position, player.stamp(), lead(position), true, now);
			places[k] = stack.place(clock.ms());
		}
		return places;
	}

	@Test
	void thirtyAndTwoHundredFortyFramesGiveTheSamePath() {
		double[] slow = run(30);
		double[] fast = run(240);
		double most = 0.0;
		for (int k = 0; k < slow.length; k++) {
			most = Math.max(most, Math.abs(slow[k] - fast[k * 8]));
		}
		// a small fraction of a text unit, where a row is about 40: only a change of the lead is seen a frame later at 30 frames a second
		assertTrue(most < 0.5, "the paths differ by up to " + most);
	}

	@Test
	void placeSpeedAndAccelerationHaveNoSteps() {
		int fps = 240;
		double dt = 1.0 / fps;
		double[] places = run(fps);
		double mostSpeedStep = 0.0;
		double mostAccelerationStep = 0.0;
		double accelerationBefore = Double.NaN;
		for (int k = 2; k < places.length; k++) {
			double speed = (places[k] - places[k - 1]) / dt;
			double speedBefore = (places[k - 1] - places[k - 2]) / dt;
			double acceleration = (speed - speedBefore) / dt;
			assertTrue(places[k] >= places[k - 1] - 1e-9, "back at frame " + k);
			mostSpeedStep = Math.max(mostSpeedStep, Math.abs(speed - speedBefore));
			if (!Double.isNaN(accelerationBefore)) {
				mostAccelerationStep = Math.max(mostAccelerationStep, Math.abs(acceleration - accelerationBefore));
			}
			accelerationBefore = acceleration;
		}
		// per frame of 4 ms the speed changes by less than 2 units a second and the acceleration by less than 60 units a second squared, where a row
		// of 40 units drifts by in about a second: a step would be the whole speed or acceleration at once
		assertTrue(mostSpeedStep < 2.0, "speed step " + mostSpeedStep);
		assertTrue(mostAccelerationStep < 60.0, "acceleration step " + mostAccelerationStep);
	}

	@Test
	void theClockNeverJumpsNorRunsBackAndStaysWithTheSong() {
		SongClock clock = new SongClock();
		Player player = new Player();
		double dt = 1.0 / 144.0;
		clock.update(player.at(0.0), player.stamp(), 400.0, true, 0.0);
		double before = clock.ms();
		double speedBefore = Double.NaN;
		for (int k = 1; k < 144 * 20; k++) {
			double now = k * dt;
			double position = player.at(now);
			assertFalse(clock.update(position, player.stamp(), 400.0, true, now), "no cut at " + now);
			double speed = (clock.ms() - before) / (dt * 1000.0);
			assertTrue(speed > 1.0 - SongClock.MOST_LEAN - 0.05 && speed < 1.0 + SongClock.MOST_LEAN + 0.05, "speed " + speed + " at " + now);
			if (!Double.isNaN(speedBefore)) {
				assertTrue(Math.abs(speed - speedBefore) < 0.01, "the speed eases at " + now);
			}
			assertTrue(Math.abs(clock.ms() - (now * 1000.0 + 400.0)) < 120.0, "with the song at " + now + ": " + clock.ms());
			before = clock.ms();
			speedBefore = speed;
		}
	}

	@Test
	void aSeekIsACutAndAPauseStops() {
		SongClock clock = new SongClock();
		assertTrue(clock.update(10_000.0, 0.0, 300.0, true, 0.0));
		assertFalse(clock.update(10_016.0, 0.0, 300.0, true, 0.016));
		assertEquals(10_316.0, clock.ms(), 1e-6);
		assertTrue(clock.update(60_000.0, 0.03, 300.0, true, 0.032));
		assertEquals(60_300.0, clock.ms(), 1e-6);
		double at = clock.ms();
		assertFalse(clock.update(60_000.0, 0.5, 300.0, false, 0.5));
		assertFalse(clock.update(60_000.0, 0.5, 300.0, false, 1.5));
		assertEquals(at, clock.ms(), 1e-6);
		// playing again it goes on from where it stood
		assertFalse(clock.update(60_010.0, 1.5, 300.0, true, 1.51));
		assertTrue(Math.abs(clock.ms() - at) < 5.0, "from where it stood: " + clock.ms());
	}
}
