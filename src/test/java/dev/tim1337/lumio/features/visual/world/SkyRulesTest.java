package dev.tim1337.lumio.features.visual.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** What Auto picks and when it may change, and that the sky stays steady through walking, warps and jumps of time. */
class SkyRulesTest {
	private static final float DAY = 0.8f;
	private static final float NIGHT = -0.8f;

	@Test
	void dayHasCloudsAndAClearNightTheLights() {
		SkyLook.AutoPick pick = new SkyLook.AutoPick();
		assertEquals(SkyLook.Pick.CLOUDS, pick.update(DAY, 0f));
		SkyLook.Pick night = pick.update(NIGHT, 0f);
		assertTrue(night == SkyLook.Pick.AURORA || night == SkyLook.Pick.STARS);
	}

	@Test
	void rainHasClouds() {
		SkyLook.AutoPick pick = new SkyLook.AutoPick();
		pick.update(NIGHT, 0f);
		assertEquals(SkyLook.Pick.CLOUDS, pick.update(NIGHT, 1f));
	}

	@Test
	void theNightSkiesTakeTurns() {
		SkyLook.AutoPick pick = new SkyLook.AutoPick();
		pick.update(DAY, 0f);
		SkyLook.Pick first = pick.update(NIGHT, 0f);
		pick.update(DAY, 0f);
		SkyLook.Pick second = pick.update(NIGHT, 0f);
		assertNotEquals(first, second);
	}

	@Test
	void autoStaysThroughTheNightAndTheDay() {
		SkyLook.AutoPick pick = new SkyLook.AutoPick();
		pick.update(DAY, 0f);
		SkyLook.Pick night = pick.update(NIGHT, 0f);
		for (float sun = -0.8f; sun < -0.1f; sun += 0.01f) {
			assertEquals(night, pick.update(sun, 0f));
		}
		pick.update(DAY, 0f);
		for (float sun = 0.1f; sun < 1f; sun += 0.01f) {
			assertEquals(SkyLook.Pick.CLOUDS, pick.update(sun, 0.1f));
		}
	}

	@Test
	void hoveringAroundDuskOrALittleRainDoesNotFlip() {
		SkyLook.AutoPick pick = new SkyLook.AutoPick();
		pick.update(NIGHT, 0f);
		SkyLook.Pick night = pick.update(-0.06f, 0f);
		for (int i = 0; i < 200; i++) {
			assertEquals(night, pick.update(i % 2 == 0 ? 0.04f : -0.04f, i % 2 == 0 ? 0.3f : 0.4f));
		}
	}

	@Test
	void walkingMovesTheEyeWithTheCamera() {
		SkyLook.Eye eye = new SkyLook.Eye();
		eye.update(100, 70, 100, 0.05);
		eye.update(100.5, 70, 100, 0.05);
		assertEquals(100.5, eye.x(), 0.001);
		for (int i = 0; i < 1000; i++) {
			eye.update(100.5 + i, 70, 100, 0.05);
		}
		assertEquals(1099.5, eye.x(), 0.001);
	}

	@Test
	void flyingFastIsNoWarp() {
		SkyLook.Eye eye = new SkyLook.Eye();
		eye.update(0, 70, 0, 0.05);
		eye.update(40, 70, 0, 0.05);
		assertEquals(40.0, eye.x(), 0.001);
	}

	@Test
	void aWarpLeavesTheEyeWhereItIs() {
		SkyLook.Eye eye = new SkyLook.Eye();
		eye.update(100, 70, 100, 0.05);
		eye.update(5000, 70, -3000, 0.05);
		assertEquals(100.0, eye.x(), 0.001);
		assertEquals(100.0, eye.z(), 0.001);
		// and it goes on from there, step by step
		eye.update(5001, 70, -3000, 0.05);
		assertEquals(101.0, eye.x(), 0.001);
	}

	@Test
	void afterAWarpTheHeightComesBackSlowly() {
		SkyLook.Eye eye = new SkyLook.Eye();
		eye.update(0, 70, 0, 0.05);
		eye.update(0, 170, 0, 0.0);
		assertEquals(70.0, eye.y(), 0.001);
		double before = eye.y();
		for (int i = 0; i < 400; i++) {
			eye.update(0, 170, 0, 0.05);
			assertTrue(Math.abs(eye.y() - before) < 2.0, "step " + i);
			before = eye.y();
		}
		assertEquals(170.0, eye.y(), 0.5);
	}

	@Test
	void theSkyRunsWithTheTimeAndNeverTurnsThroughAJump() {
		SkyLook.SkyClock clock = new SkyLook.SkyClock();
		clock.update(1f, 2f, 1f, 7, 0.05f);
		clock.update(1.0003f, 2.0003f, 1.0003f, 7, 0.05f);
		assertEquals(1.0003f, clock.sun(), 1e-6f, "the time moves it at once");
		assertTrue(clock.settled());
		// a jump: the old places stay while it fades out, then the new ones come at once, never one between
		float shown = 1f;
		for (int i = 0; i < 4; i++) {
			clock.update(3f, 4f, 3f, 7, 0.05f);
			assertEquals(1.0003f, clock.sun(), 1e-6f);
			assertTrue(clock.shown() < shown);
			shown = clock.shown();
		}
		clock.update(3f, 4f, 3f, 7, 0.05f);
		assertEquals(3f, clock.sun(), 1e-6f);
		assertEquals(4f, clock.moon(), 1e-6f);
		assertEquals(3f, clock.stars(), 1e-6f);
		for (int i = 0; i < 6; i++) {
			clock.update(3f, 4f, 3f, 7, 0.05f);
		}
		assertEquals(1f, clock.shown(), 1e-6f);
		assertTrue(clock.settled());
	}

	@Test
	void aNewWorldTakesTheTimeAtOnce() {
		SkyLook.SkyClock clock = new SkyLook.SkyClock();
		clock.update(1f, 2f, 1f, 7, 0.05f);
		clock.update(4f, 5f, 4f, 8, 0.05f);
		assertEquals(4f, clock.sun(), 1e-6f);
		assertEquals(1f, clock.shown(), 1e-6f);
	}

	@Test
	void aJumpOverTheFullTurnIsNone() {
		SkyLook.SkyClock clock = new SkyLook.SkyClock();
		clock.update(6.2831f, 0f, 6.2831f, 7, 0.05f);
		clock.update(0.0001f, 0.0002f, 0.0001f, 7, 0.05f);
		assertEquals(0.0001f, clock.sun(), 1e-6f);
		assertTrue(clock.settled());
	}

	@Test
	void theNetherIsOpenOnlyWithNothingAbove() {
		assertTrue(SkyLook.openOverNether(0, 0), "an island: nothing above");
		assertTrue(SkyLook.openOverNether(-20, 4));
		assertTrue(SkyLook.openOverNether(4, 2), "a small roof: open around");
		assertFalse(SkyLook.openOverNether(60, 0), "the roof of the Nether");
		assertFalse(SkyLook.openOverNether(3, 1));
	}

	@Test
	void aSlewTakesItsTimeWhateverTheDistance() {
		SkyLook.Slew slew = new SkyLook.Slew(6f);
		for (int i = 0; i < 59; i++) {
			slew.update(1f, 0.1f);
		}
		assertTrue(slew.value() < 1f);
		slew.update(1f, 0.2f);
		assertEquals(1f, slew.value(), 0.001f);
	}

	@Test
	void stylesFadeOutBeforeTheNextFadesIn() {
		SkyLook.Fade<String> fade = new SkyLook.Fade<>(3f);
		fade.update("aurora", 0.1f);
		assertEquals("aurora", fade.shown());
		for (int i = 0; i < 40; i++) {
			fade.update("aurora", 0.1f);
		}
		assertEquals(1f, fade.level());
		fade.update("stars", 1.5f);
		assertEquals("aurora", fade.shown());
		assertEquals(0.5f, fade.level(), 0.01f);
		fade.update("stars", 1.5f);
		assertEquals("stars", fade.shown());
		assertEquals(0f, fade.level());
		fade.update("stars", 1.5f);
		assertEquals(0.5f, fade.level(), 0.01f);
	}

	@Test
	void meteorsNeverPassTheirCap() {
		SkyMeteors meteors = new SkyMeteors();
		for (int i = 0; i < 2000; i++) {
			meteors.update(0.05f, 1f, 0.2f, true, true);
			assertTrue(meteors.count() <= SkyMeteors.CAP);
		}
		assertTrue(meteors.count() > 0);
	}

	@Test
	void meteorsFlyStraightAndComeAndGoSoftly() {
		SkyMeteors meteors = new SkyMeteors();
		while (meteors.count() == 0) {
			meteors.update(0.05f, 1f, 1f, true, false);
		}
		float startAlpha = meteors.alpha(0);
		assertTrue(startAlpha < meteors.light[0], "it starts faint");
		float x = meteors.x[0];
		float y = meteors.y[0];
		float dx = meteors.dx[0];
		float dy = meteors.dy[0];
		float speed = meteors.speed[0];
		meteors.update(0.1f, 0f, 1f, false, false);
		assertEquals(x + dx * speed * 0.1f, meteors.x[0], 0.01f);
		assertEquals(y + dy * speed * 0.1f, meteors.y[0], 0.01f);
	}

	@Test
	void nearOnesPassHighAndFarOnesFallDown() {
		SkyMeteors meteors = new SkyMeteors();
		float steepest = 0f;
		float flattest = 1f;
		for (int i = 0; i < 400; i++) {
			meteors.update(0.05f, 1f, 1f, true, false);
			for (int m = 0; m < meteors.count(); m++) {
				steepest = Math.max(steepest, -meteors.dy[m]);
				flattest = Math.min(flattest, -meteors.dy[m]);
			}
		}
		assertTrue(steepest > 0.4f, "far ones go down");
		assertTrue(flattest < 0.4f, "near ones go across");
	}

	@Test
	void withoutTheStyleTheLastOnesFinishAndNoneStart() {
		SkyMeteors meteors = new SkyMeteors();
		for (int i = 0; i < 100; i++) {
			meteors.update(0.05f, 1f, 1f, true, false);
		}
		for (int i = 0; i < 200; i++) {
			meteors.update(0.05f, 1f, 1f, false, false);
		}
		assertEquals(0, meteors.count());
	}

	@Test
	void flyBysOnlyComeWhenAskedFor() {
		SkyMeteors without = new SkyMeteors();
		for (int i = 0; i < 4000; i++) {
			without.update(0.05f, 0f, 1f, true, false);
		}
		assertEquals(0, without.count());
		SkyMeteors with = new SkyMeteors();
		boolean seen = false;
		for (int i = 0; i < 4000 && !seen; i++) {
			with.update(0.05f, 0f, 1f, true, true);
			seen = with.count() > 0;
		}
		assertTrue(seen);
		assertTrue(with.y[0] < 100f, "low over the player");
	}

	@Test
	void theWeatherSetsTheSky() {
		float[] clear = {0.47f, 0.65f, 1.0f};
		SkyLook.weatherSky(clear, 0f, 0f, 0f, 0, 0f);
		org.junit.jupiter.api.Assertions.assertArrayEquals(new float[]{0.47f, 0.65f, 1.0f}, clear, 1e-5f, "a clear sky stays as it is");
		float[] overcast = {0.47f, 0.65f, 1.0f};
		SkyLook.weatherSky(overcast, 1f, 0f, 0f, 0, 0f);
		org.junit.jupiter.api.Assertions.assertTrue(overcast[2] - overcast[0] < 0.53f * 0.7f, "cover makes it paler");
		float[] storm = {0.47f, 0.65f, 1.0f};
		SkyLook.weatherSky(storm, 1f, 1f, 1f, 0, 0f);
		org.junit.jupiter.api.Assertions.assertTrue(storm[0] + storm[1] + storm[2] < (0.47f + 0.65f + 1.0f) * 0.75f, "a storm darkens it");
		org.junit.jupiter.api.Assertions.assertTrue(storm[2] > storm[0], "and blue-grey");
		float[] tinted = {0.47f, 0.65f, 1.0f};
		SkyLook.weatherSky(tinted, 0f, 0f, 0f, 0xFF4040, 1f);
		org.junit.jupiter.api.Assertions.assertTrue(tinted[0] > 0.47f, "a color tints it gently");
	}

	@Test
	void underTheOpenSkyTheCloudsShow() {
		assertFalse(SkyLook.underCover(15, -3), "on open land");
		assertFalse(SkyLook.underCover(13, 6), "under a tree");
		assertFalse(SkyLook.underCover(10, 40), "deep in a cave, near its opening");
	}

	@Test
	void underCoverTheyGo() {
		assertTrue(SkyLook.underCover(0, 3), "no sky light: a cave or the mines");
		assertTrue(SkyLook.underCover(0, -5), "no sky light even if nothing is above");
		assertTrue(SkyLook.underCover(4, SkyLook.HIGH_CEILING_BLOCKS + 1), "a high ceiling with a little light from an opening");
		assertFalse(SkyLook.underCover(4, SkyLook.HIGH_CEILING_BLOCKS), "a lower ceiling with that light is a cave mouth");
	}

	@Test
	void lightningAlwaysStrikesAlsoInClearWeather() {
		assertEquals(0f, SkyLightning.stormFor(SkyLightning.When.OFF, 1f));
		assertEquals(0f, SkyLightning.stormFor(SkyLightning.When.WITH_RAIN, 0f), "with rain only: none in clear weather");
		assertEquals(0.8f, SkyLightning.stormFor(SkyLightning.When.WITH_RAIN, 0.8f));
		assertEquals(SkyLightning.CLEAR_STORM, SkyLightning.stormFor(SkyLightning.When.ALWAYS, 0f), "always: a light storm in clear weather");
		assertEquals(1f, SkyLightning.stormFor(SkyLightning.When.ALWAYS, 1f), "and as much as there is in a real one");
	}

	@Test
	void theWeatherLookRaisesTheCover() {
		assertEquals(0.55f, SkyLook.weatherCover(0.55f, 0f, 0f), 1e-6f, "Clear keeps the setting");
		assertEquals(0.8f, SkyLook.weatherCover(0.55f, 1f, 0f), 1e-6f, "Cloudy leaves fewer, smaller gaps");
		assertEquals(0.97f, SkyLook.weatherCover(0.55f, 0f, 1f), 1e-6f, "Storm leaves only rare small gaps");
		assertEquals(0.9f, SkyLook.weatherCover(0.9f, 1f, 0f), 1e-6f, "never lowers a higher cover");
		float halfway = SkyLook.weatherCover(0.55f, 0.5f, 0f);
		assertTrue(halfway > 0.55f && halfway < 0.8f, "fades in");
	}
}
