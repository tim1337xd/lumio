package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** A placed block turns on the spot to face the player, upright and never rolled, and is replaced only at a line start after a second unreadable. */
class LyricReadableTest {
	private static final float FRAME = 1f / 60f;
	private static final long FRAME_MS = 16L;

	/** @return the block's face direction after its turn and lean, as the drawing makes them: {x, y, z} */
	private static double[] normal(float facing, float lean) {
		double[] axes = LyricUpright.axes(facing, lean);
		// right x up: the face looks along it
		double rx = axes[3];
		double ry = axes[4];
		double rz = axes[5];
		double ux = axes[0];
		double uy = axes[1];
		double uz = axes[2];
		return new double[] {ry * uz - rz * uy, rz * ux - rx * uz, rx * uy - ry * ux};
	}

	@Test
	void aBlockFacesThePlayerFromAnySideWithNoRoll() {
		for (int heading = 0; heading < 360; heading += 15) {
			for (double height : new double[] {-12.0, 0.0, 6.0}) {
				double dx = 20.0 * Math.sin(Math.toRadians(heading));
				double dz = 20.0 * Math.cos(Math.toRadians(heading));
				float[] faced = LyricReadable.facingEye(dx, height, dz);
				double[] axes = LyricUpright.axes(faced[0], faced[1]);
				assertEquals(0.0, axes[4], 1e-9, "no roll: its rows stay level, from " + heading + " degrees");
				assertTrue(axes[1] > 0.8, "upright");
				double[] face = normal(faced[0], faced[1]);
				double across = Math.hypot(dx, dz);
				double toward = (face[0] * dx + face[2] * dz) / across;
				assertTrue(toward > 0.85, "it faces the eye across the ground from " + heading + " degrees: " + toward);
			}
		}
		// right overhead it leans at most 30 degrees, never lies flat
		float[] above = LyricReadable.facingEye(0.5, -40.0, 0.5);
		assertTrue(Math.abs(above[1]) <= Math.toRadians(LyricReadable.MOST_PITCH_DEGREES) + 1e-6);
	}

	@Test
	void itTurnsSlowlyAndTheShortWayRound() {
		float angle = 0f;
		float target = (float) Math.toRadians(170.0);
		float after = LyricReadable.follow(angle, target, FRAME);
		assertTrue(after > 0f && after < target / 10f, "a slow follow, no snap: " + after);
		// from 170 to -170 degrees the short way is 20 degrees, not 340
		float near = LyricReadable.follow((float) Math.toRadians(170.0), (float) Math.toRadians(-170.0), 10f);
		assertEquals(Math.cos(Math.toRadians(-170.0)), Math.cos(near), 1e-3);
		assertEquals(Math.sin(Math.toRadians(-170.0)), Math.sin(near), 1e-3);
	}

	@Test
	void walkingAroundABlockNeverChangesItsPosition() {
		// the turn is all that is worked out: nothing in it takes or gives a place, so a block stays where it was put; here its place is checked
		// to be the same after a walk all round it, turning every frame
		double[] place = {100.0, 70.0, -40.0};
		double[] before = place.clone();
		float facing = 0f;
		float lean = 0f;
		for (int step = 0; step < 720; step++) {
			double heading = Math.toRadians(step / 2.0);
			double eyeX = place[0] + 15.0 * Math.sin(heading);
			double eyeZ = place[2] + 15.0 * Math.cos(heading);
			float[] faced = LyricReadable.facingEye(eyeX - place[0], 71.6 - place[1], eyeZ - place[2]);
			facing = LyricReadable.follow(facing, faced[0], FRAME);
			lean = LyricReadable.follow(lean, faced[1], FRAME);
		}
		assertTrue(java.util.Arrays.equals(before, place), "its place never changes");
		assertEquals(0.0, LyricUpright.axes(facing, lean)[4], 1e-9, "still no roll");
	}

	@Test
	void coveredForHalfASecondChangesNothing() {
		long unreadable = 0L;
		for (long t = 0; t < 500; t += FRAME_MS) {
			unreadable = LyricReadable.unreadableFor(unreadable, LyricReadable.unreadable(0.6, 0.0, 1.0), FRAME_MS);
		}
		assertFalse(LyricReadable.replaceAtLineStart(unreadable), "a tree passing before it");
		// it can be read again: the time starts over
		unreadable = LyricReadable.unreadableFor(unreadable, LyricReadable.unreadable(0.0, 0.0, 1.0), FRAME_MS);
		assertEquals(0L, unreadable);
	}

	@Test
	void coveredForTwoSecondsReplacesAtTheNextLineStartAndNotBefore() {
		long unreadable = 0L;
		for (long t = 0; t < 2_000; t += FRAME_MS) {
			unreadable = LyricReadable.unreadableFor(unreadable, LyricReadable.unreadable(0.5, 0.0, 1.0), FRAME_MS);
		}
		// in the middle of a line nothing asks: only a line start calls replaceAtLineStart, and then it is replaced
		assertTrue(LyricReadable.replaceAtLineStart(unreadable));
	}

	@Test
	void whatIsUnreadable() {
		assertFalse(LyricReadable.unreadable(0.3, 0.4, 1.0), "a little hidden and partly off the screen still reads");
		assertTrue(LyricReadable.unreadable(0.4, 0.0, 1.0), "more than a third hidden");
		assertTrue(LyricReadable.unreadable(0.0, 0.6, 1.0), "more than half off the screen");
		assertTrue(LyricReadable.unreadable(0.0, 0.0, 0.5), "far smaller than placed");
		assertTrue(LyricReadable.unreadable(0.0, 0.0, 1.8), "far larger than placed");
		assertFalse(LyricReadable.unreadable(0.0, 0.0, 0.65));
	}
}
