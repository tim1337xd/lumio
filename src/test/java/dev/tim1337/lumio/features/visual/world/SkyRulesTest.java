package dev.tim1337.lumio.features.visual.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** That the sky stays steady through walking, warps and jumps of time. */
class SkyRulesTest {
	private static final float DAY = 0.8f;
	private static final float NIGHT = -0.8f;

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
		for (int i = 0; i < 1600; i++) {
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
		for (int i = 0; i < Math.round(SkyLook.SkyClock.FADE_SECONDS / 0.05f) - 1; i++) {
			clock.update(3f, 4f, 3f, 7, 0.05f);
			assertEquals(1.0003f, clock.sun(), 1e-6f);
			assertTrue(clock.shown() < shown);
			shown = clock.shown();
		}
		clock.update(3f, 4f, 3f, 7, 0.05f);
		assertEquals(3f, clock.sun(), 1e-6f);
		assertEquals(4f, clock.moon(), 1e-6f);
		assertEquals(3f, clock.stars(), 1e-6f);
		for (int i = 0; i <= Math.round(SkyLook.SkyClock.FADE_SECONDS / 0.05f); i++) {
			clock.update(3f, 4f, 3f, 7, 0.05f);
		}
		assertEquals(1f, clock.shown(), 1e-6f);
		assertTrue(clock.settled());
	}

	@Test
	void theLightOfTheSkyGlidesThroughAJump() {
		SkyLook.SkyClock clock = new SkyLook.SkyClock();
		clock.update(1f, 2f, 1f, 7, 0.05f);
		float before = clock.lightSun();
		float biggest = 0f;
		for (int i = 0; i < 1600; i++) {
			clock.update(3f, 4f, 3f, 7, 0.016f);
			biggest = Math.max(biggest, Math.abs(clock.lightSun() - before));
			before = clock.lightSun();
		}
		assertTrue(biggest < 0.03f, "no frame moves it far: " + biggest);
		assertEquals(3f, clock.lightSun(), 0.01f, "and it arrives");
	}

	@Test
	void aSmallCorrectionOfTheServerGlides() {
		SkyLook.SkyClock clock = new SkyLook.SkyClock();
		clock.update(1f, 2f, 1f, 7, 0.016f);
		clock.update(1.01f, 2.01f, 1.01f, 7, 0.016f);
		assertTrue(clock.settled(), "no jump to hide");
		assertTrue(clock.sun() > 1f && clock.sun() < 1.002f, "the sun does not hop: " + clock.sun());
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

	/** An island world of the Nether's kind: no bedrock in the top layer anywhere, whatever is built below it. */
	private static final SkyLook.TopLayer ISLAND = new SkyLook.TopLayer() {
		@Override
		public boolean loaded(int chunkX, int chunkZ) {
			return true;
		}

		@Override
		public boolean bedrock(int x, int z) {
			return false;
		}
	};

	/** The vanilla Nether: its top layer is bedrock everywhere. */
	private static final SkyLook.TopLayer NETHER = new SkyLook.TopLayer() {
		@Override
		public boolean loaded(int chunkX, int chunkZ) {
			return true;
		}

		@Override
		public boolean bedrock(int x, int z) {
			return true;
		}
	};

	@Test
	void noPlaceOnAnIslandWithoutACeilingTurnsTheSkyOff() {
		SkyLook.Ceiling ceiling = new SkyLook.Ceiling();
		Object island = new Object();
		for (int chunkX = -40; chunkX <= 40; chunkX += 3) {
			for (int chunkZ = -40; chunkZ <= 40; chunkZ += 3) {
				ceiling.look(island, chunkX, chunkZ, ISLAND);
				assertTrue(ceiling.hasSky(), "the sky at chunk " + chunkX + ", " + chunkZ);
			}
		}
	}

	@Test
	void theVanillaNetherHasNoSky() {
		SkyLook.Ceiling ceiling = new SkyLook.Ceiling();
		Object nether = new Object();
		ceiling.look(nether, 0, 0, NETHER);
		assertTrue(ceiling.decided());
		assertFalse(ceiling.hasSky());
		ceiling.look(nether, 30, -12, NETHER);
		assertFalse(ceiling.hasSky(), "anywhere in it");
	}

	@Test
	void aFewBedrockColumnsAreNoCeiling() {
		SkyLook.Ceiling ceiling = new SkyLook.Ceiling();
		// bedrock in the top layer of one chunk in five, such as a build of a server's island
		ceiling.look(new Object(), 0, 0, new SkyLook.TopLayer() {
			@Override
			public boolean loaded(int chunkX, int chunkZ) {
				return true;
			}

			@Override
			public boolean bedrock(int x, int z) {
				return Math.floorMod(x >> 4, 5) == 0;
			}
		});
		assertTrue(ceiling.hasSky());
	}

	@Test
	void theDecisionIsMadeOncePerWorld() {
		SkyLook.Ceiling ceiling = new SkyLook.Ceiling();
		Object island = new Object();
		ceiling.look(island, 0, 0, ISLAND);
		assertTrue(ceiling.decidedFor(island));
		// in the same world nothing changes it, not even bedrock above wherever the player goes next
		ceiling.look(island, 5, 5, NETHER);
		assertTrue(ceiling.hasSky(), "kept until the player leaves the world");
		// another world is looked at anew
		Object nether = new Object();
		assertFalse(ceiling.decidedFor(nether));
		ceiling.look(nether, 0, 0, NETHER);
		assertFalse(ceiling.hasSky());
		ceiling.look(nether, 0, 0, ISLAND);
		assertFalse(ceiling.hasSky(), "kept in that world too");
	}

	@Test
	void theSkyWaitsUntilEnoughIsLoaded() {
		SkyLook.Ceiling ceiling = new SkyLook.Ceiling();
		Object world = new Object();
		// only the player's chunk: no decision yet, and no sky, so the vanilla Nether never flashes one
		SkyLook.TopLayer onlyHere = new SkyLook.TopLayer() {
			@Override
			public boolean loaded(int chunkX, int chunkZ) {
				return chunkX == 0 && chunkZ == 0;
			}

			@Override
			public boolean bedrock(int x, int z) {
				return false;
			}
		};
		ceiling.look(world, 0, 0, onlyHere);
		assertFalse(ceiling.decided());
		assertFalse(ceiling.hasSky());
		ceiling.look(world, 0, 0, ISLAND);
		assertTrue(ceiling.hasSky());
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
	void allFlyOneWayAFewDegreesApart() {
		SkyMeteors meteors = new SkyMeteors();
		java.util.Set<Long> ways = new java.util.HashSet<>();
		int seen = 0;
		for (int i = 0; i < 4000; i++) {
			meteors.update(0.05f, 1f, 1f, true, true);
			for (int m = 0; m < meteors.count(); m++) {
				float across = (float) Math.sqrt(meteors.dx[m] * meteors.dx[m] + meteors.dz[m] * meteors.dz[m]);
				float heading = (float) Math.atan2(meteors.dz[m], meteors.dx[m]);
				assertEquals(SkyMeteors.HEADING, heading, SkyMeteors.HEADING_SPREAD + 1e-4f, "one heading across the world");
				float descent = -meteors.dy[m] / across;
				assertTrue(descent > 0f, "they go down");
				assertTrue(descent <= SkyMeteors.DESCENT + SkyMeteors.DESCENT_SPREAD + 1e-4f, "not steeper than the shared diagonal: " + descent);
				ways.add((long) Math.round(heading * 10000f));
				seen++;
			}
		}
		assertTrue(seen > 100);
		assertTrue(ways.size() > 20, "each a little off the shared way, so they do not look copied");
	}

	@Test
	void theyFlyAtSixtyPercentOfTheirOldSpeedWithStreaksAsLongAsBefore() {
		SkyMeteors meteors = new SkyMeteors();
		while (meteors.count() == 0) {
			meteors.update(0.05f, 1f, 1f, true, false);
		}
		assertEquals(0.6f, SkyMeteors.PACE);
		assertTrue(meteors.speed[0] <= 170f * 1.4f * SkyMeteors.PACE + 1e-3f, "slowed: " + meteors.speed[0]);
		// the streak covers as many blocks as before: its seconds grow by what the speed lost
		assertEquals(0.3f, SkyMeteors.streakSeconds(0.3f) * SkyMeteors.PACE, 1e-5f);
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
	void aRoofNeverTakesTheCloudsAway() {
		// in a house, a cave or under a tree the clouds show wherever the sky does on the screen: nothing about the camera's place hides them
		assertFalse(SkyLook.underCover(false, false), "any world nobody knows more about");
		assertFalse(SkyLook.underCover(true, false), "a known world under the open sky");
		assertFalse(SkyLook.underCover(false, true));
	}

	@Test
	void aWorldUndergroundHasNoClouds() {
		assertTrue(SkyLook.underCover(true, true), "the mines");
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
