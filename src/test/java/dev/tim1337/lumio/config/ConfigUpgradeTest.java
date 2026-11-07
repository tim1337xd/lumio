package dev.tim1337.lumio.config;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The upgrade mechanism: a step runs only on settings older than its version, in order. */
class ConfigUpgradeTest {
	@Test
	void theTrailsBecomeOneFeature() {
		JsonObject both = json("{\"arrow_trails\":{\"enabled\":true,\"shots\":{\"on_arrows\":true,\"on_pearls\":false,\"on_self\":true},\"style\":\"ELECTRIC\",\"electric_color\":{\"color\":\"#45D4FF\",\"menu\":true,\"opacity\":1.0},\"pearl_color\":{\"color\":\"#A050FF\",\"menu\":false,\"opacity\":1.0},\"length\":9.0,\"max_arrows\":64.0},\"swing_trail\":{\"enabled\":true,\"look\":\"STREAKS\",\"color\":{\"color\":\"#0700FF\",\"opacity\":0.9},\"ghosts\":24.0,\"width\":0.7,\"after\":300.0}}");
		ConfigUpgrade.oneTrailsFeature(both);
		JsonObject trails = both.getAsJsonObject("arrow_trails");
		assertFalse(both.has("swing_trail"), "one feature");
		assertTrue(trails.get("on_arrows").getAsBoolean());
		assertFalse(trails.get("on_pearls").getAsBoolean());
		assertTrue(trails.get("on_self").getAsBoolean(), "what has no place on the page stays under Advanced");
		assertEquals("RIBBON", trails.get("style").getAsString());
		assertEquals("ACCENT", trails.get("color_source").getAsString());
		assertEquals(9.0f, trails.get("length").getAsFloat());
		assertEquals(64.0f, trails.get("max_arrows").getAsFloat());
		assertEquals("CUSTOM", trails.get("pearl_colors").getAsString());
		assertTrue(trails.get("on_swings").getAsBoolean());
		assertEquals("RIBBON", trails.get("swing_style").getAsString());
		assertEquals("CUSTOM", trails.get("swing_colors").getAsString());
		assertEquals("#0700FF", trails.getAsJsonObject("swing_color").get("color").getAsString());
		assertEquals(24.0f, trails.get("swing_count").getAsFloat());
		assertEquals(300.0f, trails.get("fade_time").getAsFloat());
		assertFalse(trails.has("electric_color"));
	}

	@Test
	void aSwingTrailThatWasOnAloneStaysAlone() {
		JsonObject alone = json("{\"arrow_trails\":{\"enabled\":false,\"style\":\"FIRE\"},\"swing_trail\":{\"enabled\":true,\"look\":\"OUTLINES\"}}");
		ConfigUpgrade.oneTrailsFeature(alone);
		JsonObject trails = alone.getAsJsonObject("arrow_trails");
		assertTrue(trails.get("enabled").getAsBoolean());
		assertFalse(trails.get("on_arrows").getAsBoolean());
		assertFalse(trails.get("on_pearls").getAsBoolean());
		assertEquals("GHOST", trails.get("swing_style").getAsString(), "the old copies");
		assertEquals("OUTLINES", trails.get("swing_copies").getAsString());
		assertEquals("COMET", trails.get("style").getAsString());
	}

	@Test
	void arrowsAloneKeepTheSwingOffAndStarsKeepCircling() {
		JsonObject stars = json("{\"arrow_trails\":{\"enabled\":true,\"style\":\"STARS\",\"shots\":{\"on_self\":true},\"star_color\":{\"color\":\"#BF5AF2\",\"menu\":false}},\"swing_trail\":{\"enabled\":false}}");
		ConfigUpgrade.oneTrailsFeature(stars);
		JsonObject trails = stars.getAsJsonObject("arrow_trails");
		assertFalse(trails.get("on_swings").getAsBoolean());
		assertEquals("SPARKS", trails.get("style").getAsString());
		assertTrue(trails.get("stars_around").getAsBoolean());
		assertFalse(trails.get("on_self").getAsBoolean());
		JsonObject noSwing = json("{\"arrow_trails\":{\"enabled\":true,\"style\":\"FIRE\"}}");
		ConfigUpgrade.oneTrailsFeature(noSwing);
		assertFalse(noSwing.getAsJsonObject("arrow_trails").get("on_swings").getAsBoolean());
		ConfigUpgrade.oneTrailsFeature(json("{}"));
	}

	@Test
	void theBlockOverlayKeepsItsLookInTheNewRows() {
		JsonObject outline = json("{\"block_overlay\":{\"mode\":\"OUTLINE\",\"color\":{\"color\":\"#FF0000\",\"menu\":false},\"fill_opacity\":0.3}}");
		ConfigUpgrade.blockOverlayRows(outline);
		JsonObject one = outline.getAsJsonObject("block_overlay");
		assertEquals("OUTLINE", one.get("mode").getAsString());
		assertEquals("CUSTOM", one.get("color_source").getAsString());
		assertEquals(0f, one.get("fill_opacity").getAsFloat(), "an outline stays without a tint");

		JsonObject fill = json("{\"block_overlay\":{\"mode\":\"FILL\",\"color\":{\"color\":\"#FF0000\",\"menu\":true},\"fill_opacity\":0.3}}");
		ConfigUpgrade.blockOverlayRows(fill);
		JsonObject two = fill.getAsJsonObject("block_overlay");
		assertEquals("OUTLINE", two.get("mode").getAsString(), "the fill style is gone");
		assertEquals("ACCENT", two.get("color_source").getAsString());
		assertEquals(0.3f, two.get("fill_opacity").getAsFloat(), "and its tint stays");

		JsonObject shader = json("{\"block_overlay\":{\"mode\":\"SHADER\",\"fill_opacity\":0.5}}");
		ConfigUpgrade.blockOverlayRows(shader);
		JsonObject three = shader.getAsJsonObject("block_overlay");
		assertEquals("SHADER", three.get("mode").getAsString());
		assertEquals("RAINBOW", three.get("color_source").getAsString());
		assertEquals(0.5f, three.get("fill_opacity").getAsFloat());

		JsonObject glow = json("{\"block_overlay\":{\"mode\":\"GLOW\",\"fill_opacity\":0.4}}");
		ConfigUpgrade.blockOverlayRows(glow);
		assertEquals(0.4f, glow.getAsJsonObject("block_overlay").get("fill_opacity").getAsFloat());
		ConfigUpgrade.blockOverlayRows(json("{}"));
	}

	private static final List<ConfigUpgrade.Step> STEPS = List.of(
		new ConfigUpgrade.Step(2, root -> root.addProperty("order", "second")),
		new ConfigUpgrade.Step(3, root -> root.addProperty("order", root.get("order").getAsString() + ",third")));

	private static JsonObject json(String text) {
		return JsonParser.parseString(text).getAsJsonObject();
	}

	@Test
	void onlyNewerStepsRunInOrder() {
		JsonObject oldest = json("{\"_version\":1}");
		ConfigUpgrade.run(oldest, STEPS);
		assertEquals("second,third", oldest.get("order").getAsString());

		JsonObject current = json("{\"_version\":3}");
		ConfigUpgrade.run(current, STEPS);
		assertFalse(current.has("order"), "settings of the newest version stay as they are");
	}

	@Test
	void aMissingOrBrokenVersionCountsAsTheOldest() {
		assertEquals(0, ConfigUpgrade.versionOf(json("{}")));
		assertEquals(0, ConfigUpgrade.versionOf(json("{\"_version\":\"new\"}")));
		assertEquals(0, ConfigUpgrade.versionOf(json("{\"_version\":-4}")));
		assertEquals(1, ConfigUpgrade.versionOf(json("{\"_version\":1}")));
	}

	private static float speedFromOldFactor(String factor) {
		JsonObject old = json("{\"held_item_transform\":{\"swing_speed\":" + factor + "}}");
		ConfigUpgrade.upgrade(old);
		JsonObject item = old.getAsJsonObject("held_item_transform");
		assertFalse(item.has("swing_speed"), "the old key is gone");
		return item.get("swing_rate").getAsFloat();
	}

	@Test
	void theOldSwingFactorBecomesTheSpeedOfTheSamePace() {
		assertEquals(Math.log(0.57), speedFromOldFactor("0.57"), 0.0001, "0.57: about -0.56, still slower than the game");
		assertEquals(0f, speedFromOldFactor("1.0"), 0.0001f, "1.0: the game's");
		assertEquals(Math.log(2.0), speedFromOldFactor("2.0"), 0.0001);
	}

	@Test
	void theOldSwingFactorStopsAtTheEdgesOfTheNewRange() {
		assertEquals(-2f, speedFromOldFactor("0.05"), 0.0001f, "the old setting's lowest, ln is about -3");
		assertEquals(-2f, speedFromOldFactor("0.1353"), 0.001f, "just at the edge");
		assertEquals(1f, speedFromOldFactor("2.7183"), 0.001f, "just at the edge");
		assertEquals(1f, speedFromOldFactor("3.0"), 0.0001f, "the old setting's highest, ln is about 1.1");
	}

	@Test
	void nonsenseInTheOldSwingFactorIsTheGamesSpeed() {
		assertEquals(0f, speedFromOldFactor("0"), 0.0001f);
		assertEquals(0f, speedFromOldFactor("-1"), 0.0001f);
	}

	@Test
	void anOldSwingFactorThatIsNoNumberLeavesNothing() {
		JsonObject old = json("{\"held_item_transform\":{\"swing_speed\":\"fast\"}}");
		ConfigUpgrade.upgrade(old);
		JsonObject item = old.getAsJsonObject("held_item_transform");
		assertFalse(item.has("swing_speed"));
		assertFalse(item.has("swing_rate"));
	}

	@Test
	void theSwingConversionRunsOnce() {
		JsonObject converted = json("{\"held_item_transform\":{\"swing_speed\":0.57,\"swing_rate\":0.5}}");
		ConfigUpgrade.upgrade(converted);
		assertEquals(0.5f, converted.getAsJsonObject("held_item_transform").get("swing_rate").getAsFloat(), 0.0001f, "a speed already there stays");
		JsonObject current = json("{\"_version\":" + ConfigUpgrade.VERSION + ",\"held_item_transform\":{\"swing_rate\":-0.56}}");
		ConfigUpgrade.upgrade(current);
		assertEquals(-0.56f, current.getAsJsonObject("held_item_transform").get("swing_rate").getAsFloat(), 0.0001f);
	}

	@Test
	void anOlderConfigKeepsItsThemeWithoutGlow() {
		JsonObject old = json("{\"menu\":{\"theme\":\"DARK\"},\"armor_hud\":{\"glow\":true}}");
		ConfigUpgrade.upgrade(old);
		assertEquals(false, old.getAsJsonObject("menu").get("hud_glow").getAsBoolean());
		assertEquals(true, old.getAsJsonObject("armor_hud").get("glow").getAsBoolean());
	}

	@Test
	void aThemeGlowOfTheNewFormatStays() {
		JsonObject current = json("{\"_version\":" + ConfigUpgrade.VERSION + ",\"menu\":{\"hud_glow\":true}}");
		ConfigUpgrade.upgrade(current);
		assertEquals(true, current.getAsJsonObject("menu").get("hud_glow").getAsBoolean());
	}

	@Test
	void aConfigWithoutTheMenuPartGetsNone() {
		JsonObject part = json("{\"armor_hud\":{\"glow\":true}}");
		ConfigUpgrade.upgrade(part);
		assertFalse(part.has("menu"));
	}

	private static JsonObject radiance(String json) {
		JsonObject root = JsonParser.parseString("{\"held_item_outline\":" + json + "}").getAsJsonObject();
		ConfigUpgrade.radianceRows(root);
		return root.getAsJsonObject("held_item_outline");
	}

	@Test
	void theOutlineBecomesAGlowAndFireAFlame() {
		assertEquals("GLOW", radiance("{\"mode\":\"OUTLINE\"}").get("mode").getAsString());
		assertEquals("GLOW", radiance("{}").get("mode").getAsString());
		assertEquals("GLOW", radiance("{\"mode\":\"GLOW\"}").get("mode").getAsString());
		assertEquals("FLAME", radiance("{\"mode\":\"FIRE\"}").get("mode").getAsString());
	}

	@Test
	void theRimLightKeepsItsLookUnderAdvanced() {
		JsonObject rim = radiance("{\"mode\":\"NEON\"}");
		assertEquals("GLOW", rim.get("mode").getAsString());
		assertEquals("RIM", rim.get("glow_look").getAsString());
		assertFalse(radiance("{\"mode\":\"GLOW\"}").has("glow_look"));
	}

	@Test
	void radianceKeepsItsColor() {
		JsonObject menu = radiance("{\"color\":{\"color\":\"#00E5FF\",\"menu\":true}}");
		assertEquals("ACCENT", menu.get("color_source").getAsString());
		assertFalse(menu.getAsJsonObject("color").has("menu"));
		assertEquals("#00E5FF", menu.getAsJsonObject("color").get("color").getAsString());
		assertEquals("CUSTOM", radiance("{\"color\":{\"color\":\"#FF0000\",\"menu\":false}}").get("color_source").getAsString());
		assertEquals("CUSTOM", radiance("{}").get("color_source").getAsString());
	}

	@Test
	void theHandBecomesARowAndTheRestStaysInTheList() {
		JsonObject moved = radiance("{\"show_in\":{\"first_person\":true,\"arm_first_person\":true,\"mobs\":true}}");
		assertTrue(moved.get("arm_first_person").getAsBoolean());
		assertFalse(moved.getAsJsonObject("show_in").has("arm_first_person"));
		assertTrue(moved.getAsJsonObject("show_in").get("mobs").getAsBoolean());
		assertFalse(radiance("{\"show_in\":{\"arm_first_person\":false}}").get("arm_first_person").getAsBoolean());
	}

	@Test
	void theItemKindsBecomeAllWeaponsOrChosen() {
		assertEquals("ALL", radiance("{}").get("shows_on").getAsString());
		assertEquals("ALL", radiance("{\"affects\":{\"for_tools\":true,\"for_weapons\":true,\"for_blocks\":true,\"for_food\":true,\"for_other\":true}}").get("shows_on").getAsString());
		assertEquals("WEAPONS", radiance("{\"affects\":{\"for_tools\":false,\"for_weapons\":true,\"for_blocks\":false,\"for_food\":false,\"for_other\":false}}").get("shows_on").getAsString());
		JsonObject chosen = radiance("{\"affects\":{\"for_tools\":true,\"for_weapons\":true,\"for_blocks\":false,\"for_food\":false,\"for_other\":false}}");
		assertEquals("CHOSEN", chosen.get("shows_on").getAsString());
		assertTrue(chosen.getAsJsonObject("affects").get("for_tools").getAsBoolean());
		assertEquals("CHOSEN", radiance("{\"affects\":{\"for_tools\":true,\"for_weapons\":false,\"for_blocks\":false,\"for_food\":false,\"for_other\":false}}").get("shows_on").getAsString());
	}

	@Test
	void radianceLeavesItsOtherValuesAndThePreviewGoes() {
		JsonObject kept = radiance("{\"preview\":true,\"width\":1.5,\"glow_strength\":0.4,\"flame_speed\":2.0}");
		assertFalse(kept.has("preview"));
		assertEquals(1.5f, kept.get("width").getAsFloat());
		assertEquals(0.4f, kept.get("glow_strength").getAsFloat());
		assertEquals(2f, kept.get("flame_speed").getAsFloat());
	}

	private static JsonObject ambience(String json) {
		JsonObject root = JsonParser.parseString("{\"ambience\":" + json + "}").getAsJsonObject();
		ConfigUpgrade.ambienceRows(root);
		return root.getAsJsonObject("ambience");
	}

	@Test
	void snowKeepsFallingWhereItFellAndABlizzardStaysOne() {
		JsonObject snow = ambience("{\"mode\":\"SNOWFALL\"}");
		assertEquals("SNOW", snow.get("mode").getAsString());
		assertTrue(snow.get("snow_everywhere").getAsBoolean());
		assertFalse(snow.has("snow_look"));
		JsonObject blizzard = ambience("{\"mode\":\"BLIZZARD\"}");
		assertEquals("SNOW", blizzard.get("mode").getAsString());
		assertEquals("BLIZZARD", blizzard.get("snow_look").getAsString());
		assertEquals("SNOW", ambience("{}").get("mode").getAsString());
	}

	@Test
	void firefliesStayWhereTheyWere() {
		JsonObject flies = ambience("{\"mode\":\"FIREFLIES\",\"firefly_amount\":3.0}");
		assertEquals("FIREFLIES", flies.get("mode").getAsString());
		assertTrue(flies.get("fireflies_anywhere").getAsBoolean());
		assertEquals(3f, flies.get("firefly_amount").getAsFloat());
	}

	@Test
	void embersBecomeASwitchAndThePetalsGo() {
		JsonObject fire = ambience("{\"mode\":\"FIRE\",\"ember_amount\":2.0}");
		assertEquals("AUTO", fire.get("mode").getAsString());
		assertTrue(fire.get("embers").getAsBoolean());
		assertEquals(2f, fire.get("ember_amount").getAsFloat());
		JsonObject petals = ambience("{\"mode\":\"SAKURA\",\"petal_color\":{\"color\":\"#FFB3CC\"}}");
		assertEquals("AUTO", petals.get("mode").getAsString());
		assertFalse(petals.has("petal_color"));
		assertTrue(petals.get("falling_leaves").getAsBoolean());
	}

	@Test
	void noneStaysWithoutParticlesAndKeepsTheWind() {
		JsonObject none = ambience("{\"mode\":\"NONE\",\"wind_streaks\":true,\"fog\":0.8}");
		assertEquals("AUTO", none.get("mode").getAsString());
		assertEquals(0f, none.get("intensity").getAsFloat());
		assertEquals(0f, none.get("fog").getAsFloat());
		assertFalse(none.get("light_shafts").getAsBoolean());
		assertFalse(none.get("island_mood").getAsBoolean());
		assertTrue(none.get("wind_streaks").getAsBoolean());
	}

	@Test
	void ambienceKeepsItsNumbers() {
		JsonObject kept = ambience("{\"mode\":\"SNOWFALL\",\"intensity\":1.5,\"particle_size\":2.0,\"distance\":90.0,\"fog\":0.3,\"indoors\":false}");
		assertEquals(1.5f, kept.get("intensity").getAsFloat());
		assertEquals(2f, kept.get("particle_size").getAsFloat());
		assertEquals(90f, kept.get("distance").getAsFloat());
		assertEquals(0.3f, kept.get("fog").getAsFloat());
		assertFalse(kept.get("indoors").getAsBoolean());
	}

	private static JsonObject sky(String json) {
		JsonObject root = JsonParser.parseString("{\"sky\":" + json + "}").getAsJsonObject();
		ConfigUpgrade.skyStyles(root);
		return root.getAsJsonObject("sky");
	}

	@Test
	void theStormBecomesHeavyClouds() {
		JsonObject storm = sky("{\"theme\":\"STORM\",\"volumetric_clouds\":true,\"storm_color\":0.3}");
		assertEquals("CLOUDS", storm.get("style").getAsString());
		assertEquals(0.9f, storm.get("cloud_cover").getAsFloat());
		assertFalse(storm.has("theme"));
		assertFalse(storm.has("volumetric_clouds"));
		assertEquals(0.3f, storm.get("storm_color").getAsFloat());
	}

	@Test
	void theLightsAndTheStarsKeepTheirStyle() {
		assertEquals("AURORA", sky("{\"theme\":\"NORTHERN_LIGHTS\"}").get("style").getAsString());
		JsonObject stars = sky("{\"theme\":\"FALLING_STARS\",\"star_speed\":2.0,\"star_density\":0.4}");
		assertEquals("STARS", stars.get("style").getAsString());
		assertEquals(2f, stars.get("star_speed").getAsFloat());
		assertEquals(0.4f, stars.get("star_density").getAsFloat());
	}

	@Test
	void thePaintedSkiesStayUnderAdvanced() {
		assertEquals("NEBULA", sky("{\"theme\":\"NEBULA\"}").get("painted").getAsString());
		assertEquals("DEEP_SPACE", sky("{\"theme\":\"DEEP_SPACE\"}").get("painted").getAsString());
		assertEquals("FIRE", sky("{\"theme\":\"FIRE\"}").get("painted").getAsString());
		assertEquals("NEBULA", sky("{}").get("painted").getAsString());
		assertFalse(sky("{\"theme\":\"FIRE\"}").has("style"));
	}

	@Test
	void theSkyKeepsItsOtherValues() {
		JsonObject kept = sky("{\"theme\":\"NEBULA\",\"mode\":\"CYCLE\",\"brightness\":1.5,\"lightning\":2.0,\"quality\":\"FAST\",\"also_show\":{\"clouds\":true}}");
		assertEquals("CYCLE", kept.get("mode").getAsString());
		assertEquals(1.5f, kept.get("brightness").getAsFloat());
		assertEquals(2f, kept.get("lightning").getAsFloat());
		assertEquals("FAST", kept.get("quality").getAsString());
		assertTrue(kept.getAsJsonObject("also_show").get("clouds").getAsBoolean());
	}

	@Test
	void theFlamesLoseTheirOwnSettings() {
		JsonObject root = JsonParser.parseString("{\"held_item_outline\":{\"mode\":\"FLAME\",\"flame_speed\":2.0,\"embers\":true,\"wrap_flames\":true,\"flame_layers\":3.0,\"advanced_flames\":true,\"flame_origin\":\"BOTTOM\",\"flame_height\":2.0,\"flame_turbulence\":1.5,\"width\":1.5}}").getAsJsonObject();
		ConfigUpgrade.noFlameSettings(root);
		JsonObject radiance = root.getAsJsonObject("held_item_outline");
		assertEquals(2, radiance.size());
		assertEquals("FLAME", radiance.get("mode").getAsString());
		assertEquals(1.5f, radiance.get("width").getAsFloat());
	}

	@Test
	void theLightningSwitchBecomesAChoice() {
		JsonObject on = JsonParser.parseString("{\"sky\":{\"storm_lightning\":true}}").getAsJsonObject();
		ConfigUpgrade.lightningWhen(on);
		assertEquals("WITH_RAIN", on.getAsJsonObject("sky").get("lightning_when").getAsString());
		assertFalse(on.getAsJsonObject("sky").has("storm_lightning"));
		JsonObject off = JsonParser.parseString("{\"sky\":{\"storm_lightning\":false}}").getAsJsonObject();
		ConfigUpgrade.lightningWhen(off);
		assertEquals("OFF", off.getAsJsonObject("sky").get("lightning_when").getAsString());
		ConfigUpgrade.lightningWhen(new JsonObject());
	}

	@Test
	void flameBecomesEcho() {
		JsonObject root = JsonParser.parseString("{\"held_item_outline\":{\"mode\":\"FLAME\",\"width\":1.5}}").getAsJsonObject();
		ConfigUpgrade.radianceWithoutFlame(root);
		assertEquals("ECHO", root.getAsJsonObject("held_item_outline").get("mode").getAsString());
		assertEquals(1.5f, root.getAsJsonObject("held_item_outline").get("width").getAsFloat());
		JsonObject glow = JsonParser.parseString("{\"held_item_outline\":{\"mode\":\"GLOW\"}}").getAsJsonObject();
		ConfigUpgrade.radianceWithoutFlame(glow);
		assertEquals("GLOW", glow.getAsJsonObject("held_item_outline").get("mode").getAsString(), "other styles stay");
		ConfigUpgrade.radianceWithoutFlame(new JsonObject());
	}

	private static JsonObject skyColors(String json) {
		JsonObject root = JsonParser.parseString("{\"sky\":" + json + "}").getAsJsonObject();
		ConfigUpgrade.skyStyleColors(root);
		return root.getAsJsonObject("sky");
	}

	@Test
	void theAuroraKeepsItsColors() {
		assertFalse(skyColors("{\"style\":\"AURORA\",\"palette\":\"GREEN\"}").has("aurora_colors"));
		JsonObject pink = skyColors("{\"style\":\"AURORA\",\"palette\":\"PINK\"}");
		assertEquals("CUSTOM", pink.get("aurora_colors").getAsString());
		assertEquals("#FF6FB5", pink.get("aurora_color_1").getAsString());
		assertFalse(pink.has("palette"));
		JsonObject own = skyColors("{\"style\":\"AURORA\",\"palette\":\"CUSTOM\",\"color\":{\"color\":\"#123456\",\"menu\":false}}");
		assertEquals("#123456", own.get("aurora_color_1").getAsString());
		assertEquals("ACCENT", skyColors("{\"style\":\"AURORA\",\"palette\":\"PINK\",\"color\":{\"color\":\"#123456\",\"menu\":true}}").get("aurora_colors").getAsString());
	}

	@Test
	void theColorGoesToTheStyleItWasUsedFor() {
		JsonObject stars = skyColors("{\"style\":\"STARS\",\"color\":{\"color\":\"#123456\",\"menu\":false}}");
		assertEquals("CUSTOM", stars.get("star_colors").getAsString());
		assertEquals("#123456", stars.get("star_color").getAsString());
		assertFalse(stars.has("cloud_colors"));
		assertFalse(stars.has("aurora_colors"));
		JsonObject clouds = skyColors("{\"style\":\"CLOUDS\",\"storm_color\":0.5,\"color\":{\"color\":\"#123456\",\"menu\":false}}");
		assertEquals("#123456", clouds.get("cloud_color").getAsString());
		assertFalse(clouds.has("storm_color"));
	}

	@Test
	void whatWasNeverChosenStaysNatural() {
		assertFalse(skyColors("{\"style\":\"STARS\",\"color\":{\"color\":\"#9A4DFF\",\"menu\":false}}").has("star_colors"));
		assertFalse(skyColors("{\"style\":\"CLOUDS\",\"storm_color\":0.0,\"color\":{\"color\":\"#123456\",\"menu\":false}}").has("cloud_colors"));
		JsonObject painted = skyColors("{\"style\":\"AUTO\",\"painted\":\"NEBULA\",\"color\":{\"color\":\"#123456\",\"menu\":false}}");
		assertFalse(painted.has("star_colors"));
		assertEquals("#123456", painted.getAsJsonObject("color").get("color").getAsString());
	}

	@Test
	void theLeftoversAreReadOnceAndDropped() {
		JsonObject root = JsonParser.parseString("{\"held_item_transform\":{\"z\":0.5,\"hand_size\":1.2,\"affects\":{},\"cooldown_dip\":true,\"swing_rate\":1.5,\"x\":0.2,\"ignore_haste\":true},"
			+ "\"arrow_trails\":{\"on_self\":true,\"star_count\":2.0,\"swing_count\":9.0,\"style\":\"COMET\",\"swing_style\":\"GHOST\"},"
			+ "\"held_item_outline\":{\"body_light\":0.3,\"show_in\":{},\"glow_look\":\"RIM\",\"width\":1.5},"
			+ "\"ambience\":{\"mode\":\"SNOW\",\"fog\":0.5,\"distance\":90.0,\"wind\":1.0,\"embers\":true,\"intensity\":1.5},"
			+ "\"sky\":{\"painted\":\"NEBULA\",\"mode\":\"CYCLE\",\"lightning\":1.0,\"sync_music\":true,\"color\":{},\"style\":\"CLOUDS\",\"brightness\":1.2,\"quality\":\"FAST\"}}").getAsJsonObject();
		ConfigUpgrade.dropLeftovers(root);
		JsonObject held = root.getAsJsonObject("held_item_transform");
		assertFalse(held.has("z"));
		assertFalse(held.has("hand_size"));
		assertFalse(held.has("affects"));
		assertEquals(1.5f, held.get("swing_rate").getAsFloat());
		assertEquals(0.2f, held.get("x").getAsFloat());
		assertTrue(held.get("ignore_haste").getAsBoolean());
		JsonObject trails = root.getAsJsonObject("arrow_trails");
		assertFalse(trails.has("on_self"));
		assertFalse(trails.has("swing_count"));
		assertEquals("GHOST", trails.get("swing_style").getAsString());
		assertEquals(1, root.getAsJsonObject("held_item_outline").size());
		JsonObject ambience = root.getAsJsonObject("ambience");
		assertEquals("FIREFLIES", ambience.get("mode").getAsString());
		assertEquals(2, ambience.size());
		JsonObject sky = root.getAsJsonObject("sky");
		assertFalse(sky.has("painted"));
		assertFalse(sky.has("mode"));
		assertFalse(sky.has("lightning"));
		assertEquals(1.2f, sky.get("brightness").getAsFloat());
		assertEquals("FAST", sky.get("quality").getAsString());
	}

	@Test
	void rainStaysRainAndEverythingElseBecomesFireflies() {
		for (String[] pair : new String[][]{{"RAIN", "RAIN"}, {"FIREFLIES", "FIREFLIES"}, {"AUTO", "FIREFLIES"}, {"SNOW", "FIREFLIES"}, {"SOMETHING", "FIREFLIES"}}) {
			JsonObject root = JsonParser.parseString("{\"ambience\":{\"mode\":\"" + pair[0] + "\"}}").getAsJsonObject();
			ConfigUpgrade.dropLeftovers(root);
			assertEquals(pair[1], root.getAsJsonObject("ambience").get("mode").getAsString());
		}
	}

	@Test
	void aSkyAlwaysShownStaysSo() {
		JsonObject root = JsonParser.parseString("{\"sky\":{\"mode\":\"ALWAYS\"}}").getAsJsonObject();
		ConfigUpgrade.dropLeftovers(root);
		assertEquals("ALWAYS", root.getAsJsonObject("sky").get("when").getAsString());
		assertFalse(root.getAsJsonObject("sky").has("mode"));
	}

	@Test
	void theTrailsGetTheirNewStyles() {
		for (String[] pair : new String[][]{{"RIBBON", "SILK"}, {"SPARKS", "STARDUST"}, {"COMET", "COMET"}, {"GHOST", "ECHO"}}) {
			JsonObject root = JsonParser.parseString("{\"arrow_trails\":{\"style\":\"" + pair[0] + "\",\"color_source\":\"RARITY\",\"on_swings\":true,\"pearl_style\":\"COMET\",\"length\":9.0,\"max_arrows\":20.0}}").getAsJsonObject();
			ConfigUpgrade.trailStyles(root);
			JsonObject trails = root.getAsJsonObject("arrow_trails");
			assertEquals(pair[1], trails.get("style").getAsString());
			assertEquals("ACCENT", trails.get("color_source").getAsString());
			assertFalse(trails.has("on_swings"));
			assertEquals("COMET", trails.get("pearl_style").getAsString(), "its own style stays");
			assertEquals(9f, trails.get("length").getAsFloat());
			assertEquals(20f, trails.get("max_arrows").getAsFloat());
		}
	}

	@Test
	void settingsOfTheCurrentFormatStayAsTheyAre() {
		JsonObject settings = json("{\"_version\":" + ConfigUpgrade.VERSION + ",\"sky\":{\"enabled\":true}}");
		JsonObject before = settings.deepCopy();
		ConfigUpgrade.upgrade(settings);
		assertEquals(before, settings);
	}
}
