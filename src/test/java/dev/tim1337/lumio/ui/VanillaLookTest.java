package dev.tim1337.lumio.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The game's own controls in the mod's look are boxes with small corners, never pills: the radius rule itself, and that every mixin that draws
 * such a control takes its radius from the rule. And the slider's layout: the value is whole, the knob stays on its line, nothing is tinted at 0.
 */
class VanillaLookTest {
	private static final Path MIXINS = Path.of("src", "main", "java", "dev", "tim1337", "lumio", "mixin", "gui");
	/** The mixins that draw a control of the game in the mod's look. */
	private static final List<String> RESTYLED = List.of("AbstractButtonMixin.java", "AbstractSliderButtonMixin.java", "AbstractSelectionListMixin.java", "EditBoxMixin.java",
		"CheckboxMixin.java", "MenuTabButtonMixin.java");
	/** A call that draws a box: its radius is its fifth argument (after x, y, width and height), or the sixth after the Ui of a panel or a glass button. */
	private static final Pattern BOX = Pattern.compile("\\b(?:ui\\.rounded|ui\\.ring|ui\\.roundedSmooth|ui\\.roundedGradient|HudStyle\\.panel|Glass\\.button)\\(([^;]*)\\);");
	private static final Pattern RADIUS_NAME = Pattern.compile("(?:radius|corner|grip)\\w*", Pattern.CASE_INSENSITIVE);

	@Test
	void aCornerIsSmallAndNeverMoreThanItsShareOfTheHeight() {
		for (float height = 1f; height <= 80f; height += 0.5f) {
			float radius = VanillaLook.radius(height);
			assertTrue(radius <= VanillaLook.BOX_RADIUS + 1e-6f, "at most the box radius at a height of " + height);
			assertTrue(radius <= height * VanillaLook.MOST_OF_HEIGHT + 1e-6f, "at most 15 % of a height of " + height);
			assertTrue(radius >= 0f);
		}
		// a button of the game is 20 high: 3 GUI pixels, 6 on the screen at GUI scale 2; a list's row gets the full 4 (8 on the screen)
		assertEquals(3f, VanillaLook.radius(20f), 1e-6f);
		assertEquals(4f, VanillaLook.radius(36f), 1e-6f);
		assertEquals(0.15f, VanillaLook.MOST_OF_HEIGHT);
	}

	/** Fails when a restyled control is drawn with a radius that does not come from the rule: a number, a Radius constant, half its height. */
	@Test
	void everyRestyledControlTakesItsRadiusFromTheRule() throws IOException {
		for (String name : RESTYLED) {
			String source = Files.readString(MIXINS.resolve(name));
			Matcher box = BOX.matcher(source);
			int found = 0;
			while (box.find()) {
				found++;
				String[] arguments = box.group(1).split(",");
				int at = box.group(0).startsWith("HudStyle.panel") || box.group(0).startsWith("Glass.button") ? 5 : 4;
				assertTrue(arguments.length > at, name + ": " + box.group(0));
				String radius = arguments[at].strip();
				assertTrue(radius.startsWith("VanillaLook.") || RADIUS_NAME.matcher(radius).matches(), name + ": the radius of " + box.group(0) + " is " + radius);
			}
			assertTrue(found > 0, name + " draws no box the test knows");
			// the names a radius may have are only ever given a value by the rule
			for (String line : source.split("\n")) {
				if (line.matches("\\s*(?:float|int)\\s+(?:radius|corner|grip)\\w*\\s*=.*")) {
					assertTrue(line.contains("VanillaLook."), name + ": " + line.strip());
				}
			}
			assertFalse(source.contains("Radius.pill") || source.contains("Radius.SM") || source.contains("Radius.MD") || source.contains("Radius.LG"), name + " uses a radius of the menu's own");
		}
	}

	/** No mixin of the game's screens goes back to half the height for a corner. */
	@Test
	void noRestyledControlIsAPill() throws IOException {
		try (Stream<Path> files = Files.list(MIXINS)) {
			for (Path file : files.filter(path -> RESTYLED.contains(path.getFileName().toString())).toList()) {
				String source = Files.readString(file);
				assertFalse(Pattern.compile("Math\\.min\\([^;]*height\\s*/\\s*2").matcher(source).find(), file.getFileName() + " takes half its height for a corner");
			}
		}
	}

	@Test
	void aSlidersValueIsWholeAndItsNameGivesWay() {
		assertEquals("Jukebox/Note Blocks", VanillaLook.sliderName("Jukebox/Note Blocks: 100%"));
		assertEquals("100%", VanillaLook.sliderValue("Jukebox/Note Blocks: 100%"));
		assertEquals("OFF", VanillaLook.sliderValue("Music: OFF"));
		assertEquals("FOV", VanillaLook.sliderName("FOV: Quake Pro"));
		assertEquals("Quake Pro", VanillaLook.sliderValue("FOV: Quake Pro"));
		assertEquals("Brightness", VanillaLook.sliderName("Brightness"));
		assertEquals("", VanillaLook.sliderValue("Brightness"));
		// the value keeps its whole width whatever the box: the name has what is left, down to nothing
		for (int width = 40; width <= 310; width += 10) {
			for (int value = 0; value <= 60; value += 12) {
				int room = VanillaLook.nameRoom(width, value);
				assertTrue(room >= 0);
				assertTrue(room == 0 || room + value + 2 * VanillaLook.SLIDER_SIDE + (value > 0 ? VanillaLook.SLIDER_GAP : 0) <= width, "name and value fit a box of " + width);
			}
		}
		assertEquals(150 - 2 * VanillaLook.SLIDER_SIDE, VanillaLook.nameRoom(150, 0), "no value: only the sides are kept");
	}

	@Test
	void theKnobStaysOnItsLineAndNothingIsTintedAtZero() {
		for (int width = 30; width <= 310; width += 20) {
			int x = 17;
			for (float amount = -0.2f; amount <= 1.2f; amount += 0.05f) {
				float knob = VanillaLook.knobX(x, width, amount);
				assertTrue(knob >= x + VanillaLook.LINE_INSET - 1e-4f, "on the line at the left");
				assertTrue(knob <= x + width - VanillaLook.LINE_INSET + 1e-4f, "on the line at the right");
				int tinted = VanillaLook.tinted(x, width, amount);
				assertTrue(tinted >= 0 && tinted <= width);
			}
			assertEquals(0, VanillaLook.tinted(x, width, 0f), "Off shows no tint");
			assertTrue(VanillaLook.tinted(x, width, 0.2f) > 0);
			assertEquals(x + VanillaLook.LINE_INSET, VanillaLook.knobX(x, width, 0f), 1e-4f);
			assertEquals(x + width - VanillaLook.LINE_INSET, VanillaLook.knobX(x, width, 1f), 1e-4f);
		}
		// the knob grows slightly under the pointer and while it is dragged, and is never large
		assertEquals(VanillaLook.KNOB_REST, VanillaLook.knobRadius(0f), 1e-6f);
		assertEquals(VanillaLook.KNOB_LIT, VanillaLook.knobRadius(1f), 1e-6f);
		assertTrue(VanillaLook.KNOB_LIT > VanillaLook.KNOB_REST && VanillaLook.KNOB_LIT <= VanillaLook.BOX_RADIUS);
	}

	@Test
	void atNothingASliderSaysOffWhereTheGameSaysSo() {
		assertEquals("Off", VanillaLook.shownValue(VanillaLook.sliderValue("Music: OFF")));
		assertEquals("0%", VanillaLook.shownValue(VanillaLook.sliderValue("Master Volume: 0%")), "what the game writes as a number stays one");
		assertEquals("Quake Pro", VanillaLook.shownValue("Quake Pro"));
	}

	@Test
	void aSwitchingButtonHasItsNameLeftAndItsValueRight() {
		assertEquals("Subtitles", VanillaLook.sliderName("Subtitles: OFF"));
		assertEquals("OFF", VanillaLook.sliderValue("Subtitles: OFF"));
		assertEquals("Graphics", VanillaLook.sliderName("Graphics: Fancy"));
		assertEquals("Fancy", VanillaLook.sliderValue("Graphics: Fancy"));
		assertEquals("", VanillaLook.sliderValue("Controls..."), "a plain button has no value and keeps its centered text");
	}
}
