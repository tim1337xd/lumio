package dev.tim1337.lumio.config.setting;

import com.google.gson.JsonPrimitive;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** One to three glow colors: adding stops at three, removing at one, the others move up, and what configs saved so far loads as it was. */
class GlowColorsSettingTest {
	private static final int BLUE = 0x0000FF;
	private static final int PINK = 0xFF00FF;
	private static final int RED = 0xFF0000;

	private FloatSetting count;
	private ColorSetting[] colors;
	private GlowColorsSetting row;

	@BeforeEach
	void threeColors() {
		count = new FloatSetting("glow_count", Component.literal("Colors"), 3f, 1f, 3f, 0);
		colors = new ColorSetting[]{new ColorSetting("glow_color_1", Component.literal("1"), BLUE), new ColorSetting("glow_color_2", Component.literal("2"), PINK),
			new ColorSetting("glow_color_3", Component.literal("3"), RED)};
		row = new GlowColorsSetting("glow_colors", Component.literal("Glow colors"), count, colors);
	}

	@Test
	void anOldConfigKeepsItsThreeColors() {
		count.load(new JsonPrimitive(3f));
		colors[2].load(new JsonPrimitive("#112233"));
		assertEquals(3, row.count());
		assertEquals(0x112233, row.slot(2).get() & 0xFFFFFF);
		assertFalse(row.canAdd());
		assertTrue(row.canRemove());
	}

	@Test
	void removingMovesTheOthersUp() {
		row.remove(0);
		assertEquals(2, row.count());
		assertEquals(PINK, row.slot(0).get() & 0xFFFFFF);
		assertEquals(RED, row.slot(1).get() & 0xFFFFFF);
	}

	@Test
	void theLastColorStays() {
		row.remove(1);
		row.remove(1);
		assertEquals(1, row.count());
		assertFalse(row.canRemove());
		row.remove(0);
		assertEquals(1, row.count());
		assertEquals(BLUE, row.slot(0).get() & 0xFFFFFF);
	}

	@Test
	void addingStopsAtThree() {
		row.remove(2);
		assertTrue(row.canAdd());
		row.add();
		assertEquals(3, row.count());
		row.add();
		assertEquals(3, row.count());
	}

	@Test
	void anIndexOutsideDoesNothing() {
		row.remove(7);
		row.remove(-1);
		assertEquals(3, row.count());
	}

	@Test
	void aCountInAConfigOutsideOfTheRangeIsKeptToIt() {
		count.load(new JsonPrimitive(9f));
		assertEquals(3, row.count());
		count.load(new JsonPrimitive(0f));
		assertEquals(1, row.count());
	}
}
