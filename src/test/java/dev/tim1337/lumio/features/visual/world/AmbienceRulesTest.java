package dev.tim1337.lumio.features.visual.world;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.visual.world.AmbiencePool.Kind;

import org.junit.jupiter.api.Test;

/** What each style shows, where things may spawn, how they fade away, the caps and the wet ground. */
class AmbienceRulesTest {
	private static AmbienceLook.Scene scene() {
		AmbienceLook.Scene scene = new AmbienceLook.Scene();
		scene.sunUp = true;
		return scene;
	}

	private static AmbienceLook.Choice style(AmbienceLook.Style style) {
		AmbienceLook.Choice choice = new AmbienceLook.Choice();
		choice.style = style;
		return choice;
	}

	@Test
	void rainRainsWhateverTheWeatherIs() {
		AmbienceLook.Scene dry = scene();
		assertFalse(dry.raining);
		assertEquals(1f, AmbienceLook.share(Kind.RAIN, style(AmbienceLook.Style.RAIN), dry));
		assertEquals(1f, AmbienceLook.share(Kind.RIPPLE, style(AmbienceLook.Style.RAIN), dry));
		assertEquals(0f, AmbienceLook.share(Kind.FIREFLY, style(AmbienceLook.Style.RAIN), dry));
	}

	@Test
	void noRainWithoutASky() {
		AmbienceLook.Scene cave = scene();
		cave.underGround = true;
		assertEquals(0f, AmbienceLook.share(Kind.RAIN, style(AmbienceLook.Style.RAIN), cave));
		AmbienceLook.Scene nether = scene();
		nether.noSky = true;
		assertEquals(0f, AmbienceLook.share(Kind.RAIN, style(AmbienceLook.Style.RAIN), nether));
	}

	@Test
	void firefliesBelongToTheNightUnlessAskedOtherwise() {
		AmbienceLook.Scene scene = scene();
		AmbienceLook.Choice choice = style(AmbienceLook.Style.FIREFLIES);
		assertEquals(0f, AmbienceLook.share(Kind.FIREFLY, choice, scene));
		scene.night = true;
		assertEquals(1f, AmbienceLook.share(Kind.FIREFLY, choice, scene));
		assertEquals(0f, AmbienceLook.share(Kind.RAIN, choice, scene));
		scene.night = false;
		choice.firefliesAnywhere = true;
		assertEquals(1f, AmbienceLook.share(Kind.FIREFLY, choice, scene));
	}

	@Test
	void theExtrasAreOffBehindTheirFlag() {
		assertFalse(AmbienceLook.EXTRAS);
		AmbienceLook.Scene scene = scene();
		scene.night = true;
		scene.raining = true;
		scene.knownWorld = true;
		scene.moodAsh = true;
		scene.moodSparks = true;
		scene.moodDust = true;
		scene.moodWebs = true;
		scene.hintMist = true;
		for (Kind kind : new Kind[]{Kind.GLINT, Kind.DUST, Kind.LEAF, Kind.MIST, Kind.SHAFT, Kind.ASH, Kind.EMBER, Kind.WEB}) {
			assertEquals(0f, AmbienceLook.share(kind, style(AmbienceLook.Style.FIREFLIES), scene), kind.name());
		}
	}

	@Test
	void theFlagBringsThemBackWithTheirChoices() {
		AmbienceLook.Scene day = scene();
		AmbienceLook.Choice choice = style(AmbienceLook.Style.FIREFLIES);
		choice.shafts = true;
		choice.leaves = true;
		choice.water = true;
		assertEquals(1f, AmbienceLook.share(Kind.SHAFT, choice, day));
		assertEquals(1f, AmbienceLook.share(Kind.LEAF, choice, day));
		assertEquals(1f, AmbienceLook.share(Kind.GLINT, choice, day));
	}

	@Test
	void spawnSpotsFollowTheRules() {
		assertTrue(AmbienceLook.fireflySpot(true, true, false, false));
		assertFalse(AmbienceLook.fireflySpot(false, true, true, false), "only near grass");
		assertTrue(AmbienceLook.fireflySpot(false, true, false, true));
		assertTrue(AmbienceLook.ringSpot(true, true, true));
		assertFalse(AmbienceLook.ringSpot(false, true, true), "not on land");
		assertFalse(AmbienceLook.ringSpot(true, false, true), "not under something");
		assertFalse(AmbienceLook.ringSpot(true, true, false), "not under a roof");
	}

	@Test
	void theGroundIsOnlyWetInTheRainStyleAndWhenAskedFor() {
		assertEquals(0.7f, AmbienceLook.wetWanted(AmbienceLook.Style.RAIN, AmbienceLook.WetGround.PUDDLES, 0.7f));
		assertEquals(0.7f, AmbienceLook.wetWanted(AmbienceLook.Style.RAIN, AmbienceLook.WetGround.EVERYWHERE, 0.7f));
		assertEquals(0f, AmbienceLook.wetWanted(AmbienceLook.Style.RAIN, AmbienceLook.WetGround.OFF, 0.7f));
		assertEquals(0f, AmbienceLook.wetWanted(AmbienceLook.Style.FIREFLIES, AmbienceLook.WetGround.EVERYWHERE, 0.7f));
	}

	@Test
	void theWetGroundFadesOverSomeSeconds() {
		SkyLook.Slew wet = new SkyLook.Slew(4f);
		wet.update(1f, 1f);
		assertEquals(0.25f, wet.value(), 0.001f);
		for (int i = 0; i < 10; i++) {
			wet.update(1f, 1f);
		}
		assertEquals(1f, wet.value());
	}

	@Test
	void eachKindStopsAtItsCap() {
		AmbiencePool pool = new AmbiencePool();
		for (Kind kind : AmbiencePool.KINDS) {
			for (int i = 0; i < kind.cap(); i++) {
				assertTrue(pool.spawn(kind, 0, 0, 0, 1f, 1f, 0, 100, 10) >= 0);
			}
			assertEquals(-1, pool.spawn(kind, 0, 0, 0, 1f, 1f, 0, 100, 10), kind.name());
			assertEquals(kind.cap(), pool.count(kind));
		}
	}

	@Test
	void aFullKindLeavesTheOthersRoom() {
		AmbiencePool pool = new AmbiencePool();
		for (int i = 0; i < Kind.RAIN.cap() + 10; i++) {
			pool.spawn(Kind.RAIN, 0, 0, 0, 1f, 1f, 0, 100, 10);
		}
		assertTrue(pool.spawn(Kind.LEAF, 0, 0, 0, 1f, 1f, 0, 100, 10) >= 0);
	}

	@Test
	void aParticleFadesInAndOutWithoutAJump() {
		AmbiencePool pool = new AmbiencePool();
		int i = pool.spawn(Kind.DUST, 0, 0, 0, 1f, 1f, 0, 100, 10);
		assertEquals(0f, pool.alpha(i));
		float before = 0f;
		for (int tick = 0; tick < 99; tick++) {
			pool.tick(0f, 0f);
			float now = pool.alpha(0);
			assertTrue(Math.abs(now - before) < 0.2f, "at tick " + tick);
			before = now;
		}
		pool.tick(0f, 0f);
		assertEquals(0, pool.size());
		assertEquals(0, pool.count(Kind.DUST));
	}

	@Test
	void outOfRangeFadesInsteadOfVanishing() {
		AmbiencePool pool = new AmbiencePool();
		pool.spawn(Kind.DUST, 100, 0, 0, 1f, 1f, 0, 1000, 10);
		pool.spawn(Kind.DUST, 5, 0, 0, 1f, 1f, 0, 1000, 10);
		for (int tick = 0; tick < 50; tick++) {
			pool.tick(0f, 0f);
		}
		pool.retireOutside(0, 0, 50);
		assertTrue(pool.retired(0));
		assertFalse(pool.retired(1));
		assertEquals(1f, pool.alpha(0), 0.001f, "it starts from what it showed");
		for (int tick = 0; tick <= AmbiencePool.RETIRE_TICKS; tick++) {
			pool.tick(0f, 0f);
		}
		assertEquals(1, pool.size());
		assertEquals(5.0, pool.x(0), 0.001);
	}

	@Test
	void retiringAKindLeavesTheOthers() {
		AmbiencePool pool = new AmbiencePool();
		pool.spawn(Kind.RAIN, 0, 0, 0, 1f, 1f, 0, 1000, 10);
		pool.spawn(Kind.LEAF, 0, 0, 0, 1f, 1f, 0, 1000, 10);
		pool.retireAll(Kind.RAIN);
		for (int tick = 0; tick <= AmbiencePool.RETIRE_TICKS; tick++) {
			pool.tick(0f, 0f);
		}
		assertEquals(0, pool.count(Kind.RAIN));
		assertEquals(1, pool.count(Kind.LEAF));
	}

	@Test
	void windCarriesOnlyWhatItShould() {
		AmbiencePool pool = new AmbiencePool();
		int rain = pool.spawn(Kind.RAIN, 0, 0, 0, 1f, 1f, 0, 100, 1);
		int shaft = pool.spawn(Kind.SHAFT, 0, 0, 0, 1f, 1f, 0, 100, 1);
		pool.tick(1f, 0f);
		assertTrue(pool.x(rain) > 0.0);
		assertEquals(0.0, pool.x(shaft));
	}

	@Test
	void firefliesKeepApart() {
		org.junit.jupiter.api.Assertions.assertTrue(AmbienceLook.fireflyCrowded(10.0, 64.0, 10.0, 11.5, 64.0, 10.5), "closer than the spacing");
		org.junit.jupiter.api.Assertions.assertTrue(AmbienceLook.fireflyCrowded(8.2, 64.2, 8.2, 11.7, 67.7, 11.7), "one cell holds one");
		org.junit.jupiter.api.Assertions.assertFalse(AmbienceLook.fireflyCrowded(10.0, 64.0, 10.0, 14.5, 64.0, 10.0), "apart and in other cells");
		org.junit.jupiter.api.Assertions.assertFalse(AmbienceLook.fireflyCrowded(-0.5, 64.0, 0.5, 3.5, 64.0, 0.5), "cells work below zero as well");
	}

	@Test
	void aTunnelHasRoomForFewer() {
		org.junit.jupiter.api.Assertions.assertEquals(100f, AmbienceLook.fireflyDensity(100f, 0.8f), 1e-4f, "open land: all of them");
		org.junit.jupiter.api.Assertions.assertEquals(20f, AmbienceLook.fireflyDensity(100f, 0.1f), 1e-4f, "a tunnel: a few, spread out");
		org.junit.jupiter.api.Assertions.assertEquals(12f, AmbienceLook.fireflyDensity(100f, 0f), 1e-4f, "never none at all");
	}
}
