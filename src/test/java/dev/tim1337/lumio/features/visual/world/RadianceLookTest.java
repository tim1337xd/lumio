package dev.tim1337.lumio.features.visual.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.render.EchoLines;

import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

/** Styles fade into each other, an echo gets longer with its length, and the places it comes from stay in a fixed ring. */
class RadianceLookTest {
	private static final long MILLIS = 1_000_000L;

	@Test
	void aStyleFadesIn() {
		RadianceLook.Blend blend = new RadianceLook.Blend();
		blend.update(RadianceLook.Style.OFF, 1L);
		assertTrue(blend.idle());
		blend.update(RadianceLook.Style.GLOW, 1L + MILLIS);
		assertEquals(RadianceLook.Style.GLOW, blend.shown());
		blend.update(RadianceLook.Style.GLOW, 1L + 126 * MILLIS);
		assertEquals(0.5f, blend.level(), 0.01f);
		blend.update(RadianceLook.Style.GLOW, 1L + 400 * MILLIS);
		assertEquals(1f, blend.level());
	}

	@Test
	void theOldStyleFadesOutBeforeTheNewOneShows() {
		RadianceLook.Blend blend = shownGlow();
		blend.update(RadianceLook.Style.ECHO, 1000 * MILLIS + 100 * MILLIS);
		assertEquals(RadianceLook.Style.GLOW, blend.shown());
		assertEquals(0.6f, blend.level(), 0.01f);
		blend.update(RadianceLook.Style.ECHO, 1000 * MILLIS + 300 * MILLIS);
		assertEquals(RadianceLook.Style.ECHO, blend.shown());
		assertEquals(0f, blend.level());
		blend.update(RadianceLook.Style.ECHO, 1000 * MILLIS + 400 * MILLIS);
		assertEquals(0.4f, blend.level(), 0.01f);
	}

	@Test
	void levelNeverJumps() {
		RadianceLook.Blend blend = shownGlow();
		float before = blend.level();
		long now = 1000 * MILLIS;
		RadianceLook.Style[] order = {RadianceLook.Style.ECHO, RadianceLook.Style.OFF, RadianceLook.Style.GLOW, RadianceLook.Style.ECHO};
		for (int i = 0; i < 400; i++) {
			now += 16 * MILLIS;
			blend.update(order[i / 100], now);
			assertTrue(Math.abs(blend.level() - before) <= 16f / 250f + 0.001f, "at frame " + i);
			before = blend.level();
		}
	}

	@Test
	void offFadesOutAndThenIsIdle() {
		RadianceLook.Blend blend = shownGlow();
		blend.update(RadianceLook.Style.OFF, 1000 * MILLIS + 100 * MILLIS);
		assertFalse(blend.idle());
		blend.update(RadianceLook.Style.OFF, 1000 * MILLIS + 400 * MILLIS);
		assertTrue(blend.idle());
	}

	private static RadianceLook.Blend shownGlow() {
		RadianceLook.Blend blend = new RadianceLook.Blend();
		blend.update(RadianceLook.Style.GLOW, 1L);
		blend.update(RadianceLook.Style.GLOW, 500 * MILLIS);
		blend.update(RadianceLook.Style.GLOW, 1000 * MILLIS);
		return blend;
	}

	@Test
	void aLongerEchoReachesFurther() {
		assertTrue(RadianceLook.echoSpanNanos(0f) < RadianceLook.echoSpanNanos(0.5f));
		assertTrue(RadianceLook.echoSpanNanos(0.5f) < RadianceLook.echoSpanNanos(1f));
	}

	@Test
	void theFanIsGoneBeforeTheArmIsBack() {
		long swing = 300 * MILLIS;
		assertTrue(RadianceLook.fanNanos(0.5f) + RadianceLook.echoSpanNanos(0.5f) < swing);
		assertTrue(RadianceLook.echoSpanNanos(0.5f) <= 200 * MILLIS);
		assertTrue(RadianceLook.fanNanos(1f) > RadianceLook.fanNanos(0f));
	}

	@Test
	void theHistoryWorksOutPlacesBetweenItsSamples() {
		PoseHistory history = new PoseHistory();
		history.record(100 * MILLIS, new Matrix4f().translation(0f, 0f, 0f));
		history.record(110 * MILLIS, new Matrix4f().translation(1f, 0f, 0f));
		Matrix4f into = new Matrix4f();
		assertTrue(history.between(105 * MILLIS, into));
		assertEquals(0.5f, into.m30(), 0.001f);
		assertFalse(history.between(50 * MILLIS, into));
	}

	@Test
	void theNewestCopyIsBrightestAndOlderOnesFadeToNothingAndThinner() {
		assertEquals(EchoLines.COPY_LIGHT, EchoLines.ageLight(0f), "the newest copy, against the rim");
		float before = 2f;
		for (int step = 0; step <= 10; step++) {
			float light = EchoLines.ageLight(step / 10f);
			assertTrue(light < before);
			before = light;
		}
		assertEquals(0f, EchoLines.ageLight(1f), "gone at the end of the echo");
		assertTrue(EchoLines.ageLight(0.5f) < 0.3f * EchoLines.COPY_LIGHT, "falls quickly");
		assertEquals(1f, EchoLines.ageThickness(0f));
		assertTrue(EchoLines.ageThickness(1f) < 1f && EchoLines.ageThickness(1f) > 0.5f, "older ones a little thinner");
	}

	@Test
	void streaksAreBrighterTheFasterAPointMoves() {
		assertEquals(0f, RadianceEcho.streakLight(5f), "a hand standing still leaves none");
		assertTrue(RadianceEcho.streakLight(40f) < RadianceEcho.streakLight(100f), "a faster point leaves a brighter streak");
		assertEquals(1f, RadianceEcho.streakLight(500f), "the brightest is as bright as the rim");
		assertEquals(1f, RadianceEcho.share(40f, 40f), 1e-6f, "the farthest keeps all");
		assertTrue(RadianceEcho.share(10f, 40f) < 0.05f, "an inner one keeps little");
	}

	@Test
	void theHistoryFindsTheLatestSampleBeforeATime() {
		PoseHistory history = new PoseHistory();
		assertNull(history.at(MILLIS));
		for (int i = 0; i < 10; i++) {
			history.record((100 + i * 10) * MILLIS, new Matrix4f().translation(i, 0f, 0f));
		}
		assertEquals(3f, history.at(135 * MILLIS).m30());
		assertEquals(9f, history.at(500 * MILLIS - 200 * MILLIS).m30());
		assertNull(history.at(50 * MILLIS));
	}

	@Test
	void aSampleFromBeforeAPauseIsNotUsed() {
		PoseHistory history = new PoseHistory();
		history.record(100 * MILLIS, new Matrix4f());
		assertNull(history.at(1000 * MILLIS));
	}

	@Test
	void samplesCloserThanTheGapAreSkipped() {
		PoseHistory history = new PoseHistory();
		history.record(100 * MILLIS, new Matrix4f().translation(1f, 0f, 0f));
		history.record(102 * MILLIS, new Matrix4f().translation(2f, 0f, 0f));
		assertEquals(1f, history.at(103 * MILLIS).m30());
	}

	@Test
	void theRingReusesItsMatrices() {
		PoseHistory history = new PoseHistory();
		long now = 0L;
		history.record(now, new Matrix4f());
		Matrix4f first = history.at(now);
		for (int i = 0; i < 256; i++) {
			now += 10 * MILLIS;
			history.record(now, new Matrix4f());
		}
		assertSame(first, history.at(now));
		now += 10 * MILLIS;
		history.record(now, new Matrix4f());
		assertNotSame(first, history.at(now));
	}

	@Test
	void nearSeesSmallAndBigMoves() {
		assertTrue(PoseHistory.near(new Matrix4f(), new Matrix4f().translation(0.01f, 0f, 0f)));
		assertFalse(PoseHistory.near(new Matrix4f(), new Matrix4f().translation(0.2f, 0f, 0f)));
	}

	@Test
	void echoCopiesFollowTheTipsTravelNotTheFrames() {
		float[] places = new float[24];
		// one long frame of 21 pixels leaves three copies, at 7, 14 and 21
		assertEquals(3, RadianceEcho.copyPlaces(0f, 21f, 7f, places));
		assertEquals(1f / 3f, places[0], 1e-5f);
		assertEquals(1f, places[2], 1e-5f);
		// the same travel in three frames of 7 pixels leaves the same three copies
		float carried = 0f;
		int copies = 0;
		for (int frame = 0; frame < 3; frame++) {
			copies += RadianceEcho.copyPlaces(carried, 7f, 7f, places);
			carried = RadianceEcho.carriedAfter(carried, 7f, 7f);
		}
		assertEquals(3, copies);
		// small steps add up until a copy is due
		assertEquals(0, RadianceEcho.copyPlaces(0f, 3f, 7f, places));
		assertEquals(1, RadianceEcho.copyPlaces(RadianceEcho.carriedAfter(0f, 3f, 7f), 5f, 7f, places));
		assertEquals(0.8f, places[0], 1e-5f);
	}

	@Test
	void echoLengthChangesHowLongTheTrailLasts() {
		assertEquals(0.5f, RadianceEcho.lengthFactor(0f), 1e-5f);
		assertEquals(1f, RadianceEcho.lengthFactor(0.5f), 1e-5f);
		assertEquals(2f, RadianceEcho.lengthFactor(1f), 1e-5f);
	}
}
