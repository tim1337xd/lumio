package dev.tim1337.lumio.features.music;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.music.CutoutPlace.Where;

import org.junit.jupiter.api.Test;

/**
 * A placed block of lyrics stays where it is: turning the camera by any angle, walking or flying, and changing the field of view never move or
 * end one that is seen; the next line joins it. Only fully out of view for more than two seconds ends it. The film keeps its own rules.
 */
class CutoutPlaceTest {
	private static final double HALF_WIDTH = 0.18;
	private static final double HALF_HEIGHT = 0.3;
	/** A block as the game places it: its middle 8 blocks ahead of where the player stood, half its size in degrees across and up. */
	private static final double[] BLOCK = {0.0, 1.0, 8.0};
	private static final double HALF_WIDTH_DEGREES = 12.0;
	private static final double HALF_HEIGHT_DEGREES = 8.0;
	private static final double ASPECT = 16.0 / 9.0;

	private static Where where(double x, double y) {
		return CutoutPlace.where(true, x, y, HALF_WIDTH, HALF_HEIGHT);
	}

	/** @return where the block lies for a camera at an eye position looking at a yaw and pitch, with a field of view */
	private static Where seen(double eyeX, double eyeY, double eyeZ, double yaw, double pitch, double halfFovY) {
		double[] at = new double[2];
		boolean inFront = WorldLyricView.project(BLOCK[0] - eyeX, BLOCK[1] - eyeY, BLOCK[2] - eyeZ, yaw, pitch, halfFovY, ASPECT, at);
		return CutoutPlace.where(inFront, at[0], at[1], WorldLyricView.share(HALF_WIDTH_DEGREES, halfFovY) / ASPECT,
			WorldLyricView.share(HALF_HEIGHT_DEGREES, halfFovY));
	}

	/** Whatever the view does, the next line joins a block that is seen (whether it is replaced as unreadable is LyricReadable's). */
	private static void neverEndedWhileSeen(Where where) {
		if (where != Where.OUTSIDE) {
			assertTrue(CutoutPlace.joins(where), "the next line joins a block in view");
		}
	}

	@Test
	void turningTheCameraByAnyAngleNeverEndsASeenBlock() {
		int seen = 0;
		int unseen = 0;
		for (int yaw = -180; yaw < 180; yaw += 3) {
			for (int pitch = -90; pitch <= 90; pitch += 5) {
				Where where = seen(0.0, 1.6, 0.0, yaw, pitch, 35.0);
				neverEndedWhileSeen(where);
				seen += where != Where.OUTSIDE ? 1 : 0;
				unseen += where == Where.OUTSIDE ? 1 : 0;
			}
		}
		assertTrue(seen > 0 && unseen > 0, "both cases came up");
	}

	@Test
	void walkingThirtyBlocksAndChangingTheFieldOfViewNeverEndASeenBlock() {
		for (double walked = 0.0; walked <= 30.0; walked += 0.5) {
			for (double halfFovY : new double[] {20.0, 35.0, 55.0}) {
				// walking past it, away from it, up and to the side, looking ahead and back at it
				neverEndedWhileSeen(seen(0.0, 1.6, walked, 0.0, 0.0, halfFovY));
				neverEndedWhileSeen(seen(0.0, 1.6, -walked, 0.0, 0.0, halfFovY));
				neverEndedWhileSeen(seen(walked, 1.6 + walked / 3.0, 0.0, 45.0, 10.0, halfFovY));
				neverEndedWhileSeen(seen(0.0, 1.6, walked, 180.0, 0.0, halfFovY));
			}
		}
		// the block straight ahead stays where it is: it is in view and the next line joins it
		assertEquals(Where.INSIDE, seen(0.0, 1.6, 0.0, 0.0, 0.0, 35.0));
		// a step to the side, a wider view or a jump keep it in view
		assertTrue(CutoutPlace.joins(seen(3.0, 2.8, 2.0, 0.0, -5.0, 55.0)));
	}

	@Test
	void aBlockAtTheEdgeOfTheViewStaysAndTakesTheNextLine() {
		for (Where where : new Where[] {where(0.9, 0.0), where(-0.95, 0.0), where(0.0, 0.85), where(1.1, 0.0)}) {
			assertEquals(Where.EDGE, where);
			assertTrue(CutoutPlace.joins(where), "partly seen: the next line joins it, nothing is placed anew");
		}
	}

	@Test
	void outOfViewTheNextLineIsPlacedFresh() {
		for (Where where : new Where[] {where(1.5, 0.0), where(0.0, -1.5), CutoutPlace.where(false, 0.0, 0.0, HALF_WIDTH, HALF_HEIGHT)}) {
			assertEquals(Where.OUTSIDE, where);
			assertFalse(CutoutPlace.joins(where), "a line starting while it is out of view is placed fresh in view");
		}
	}

	@Test
	void theFilmKeepsItsOwnQuickerRules() {
		assertTrue(CutoutPlace.clearShot(Where.OUTSIDE, CutoutPlace.SHOT_OUTSIDE_MS + 1L));
		assertFalse(CutoutPlace.clearShot(Where.EDGE, 60_000L));
		assertTrue(CutoutPlace.keepsShot(Where.INSIDE));
		assertFalse(CutoutPlace.keepsShot(Where.EDGE), "the film places its next line well inside the picture");
	}
}
