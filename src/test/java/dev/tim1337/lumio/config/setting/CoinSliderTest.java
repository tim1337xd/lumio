package dev.tim1337.lumio.config.setting;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A slider for coins on a logarithmic scale with snap values, as the drop toasts use it: its text, what may be typed, and its track. */
class CoinSliderTest {
	private static final float LOWEST = 100_000f;
	private static final float HIGHEST = 100_000_000f;

	private static FloatSetting coins() {
		return new FloatSetting("show_from", Component.literal("Show from"), 1_000_000f, LOWEST, HIGHEST, 0).logarithmic()
			.snapTo(500_000f, 1_000_000f, 5_000_000f, 10_000_000f, 50_000_000f).coins();
	}

	@Test
	void amountsAreShortWithoutZeroDecimals() {
		assertEquals("100k", CoinAmount.format(100_000));
		assertEquals("750k", CoinAmount.format(750_000));
		assertEquals("1M", CoinAmount.format(1_000_000));
		assertEquals("2.5M", CoinAmount.format(2_500_000));
		assertEquals("1.25M", CoinAmount.format(1_250_000));
		assertEquals("100M", CoinAmount.format(100_000_000));
		assertEquals("1.2B", CoinAmount.format(1_200_000_000));
		assertEquals("999", CoinAmount.format(999));
	}

	@Test
	void typedAmountsAreRead() {
		assertEquals(2_500_000d, CoinAmount.parse("2.5M"));
		assertEquals(750_000d, CoinAmount.parse("750k"));
		assertEquals(2_500_000d, CoinAmount.parse("2,5m"));
		assertEquals(1_000_000_000d, CoinAmount.parse("1b"));
		assertEquals(2_500_000d, CoinAmount.parse("2500000"));
		assertEquals(2_500_000d, CoinAmount.parse("2,500,000"));
		assertEquals(2_500_000d, CoinAmount.parse(" 2.5 M "));
		assertTrue(Double.isNaN(CoinAmount.parse("")));
		assertTrue(Double.isNaN(CoinAmount.parse("M")));
		assertTrue(Double.isNaN(CoinAmount.parse("abc")));
		assertTrue(Double.isNaN(CoinAmount.parse("1.2.3M")));
		assertTrue(Double.isNaN(CoinAmount.parse("12345678901234567890")));
	}

	@Test
	void theSliderShowsAndReadsCoins() {
		FloatSetting slider = coins();
		assertEquals("1M", slider.valueText());
		assertEquals(2_500_000f, slider.rounded(slider.parseTyped("2.5M")));
		assertEquals(750_000f, slider.rounded(slider.parseTyped("750k")));
		assertEquals(LOWEST, slider.rounded(slider.parseTyped("5k")), "below the range is the lowest value");
		assertEquals(HIGHEST, slider.rounded(slider.parseTyped("2B")), "above it the highest");
		assertTrue(Float.isNaN(slider.parseTyped("lots")));
	}

	@Test
	void theTrackIsLogarithmic() {
		FloatSetting slider = coins();
		assertEquals(0f, slider.fraction(LOWEST), 1e-6f);
		assertEquals(1f, slider.fraction(HIGHEST), 1e-6f);
		// 100k to 100M is three factors of ten: 1M a third along, 10M two thirds
		assertEquals(1f / 3f, slider.fraction(1_000_000f), 1e-4f);
		assertEquals(2f / 3f, slider.fraction(10_000_000f), 1e-4f);
		assertEquals(LOWEST, slider.valueAt(0f));
		assertEquals(HIGHEST, slider.valueAt(1f));
		// halfway is about 3.16M, kept to two digits while dragged
		assertEquals(3_200_000f, slider.valueAt(0.5f));
	}

	@Test
	void theKnobRestsOnSnapValuesNearby() {
		FloatSetting slider = coins();
		assertEquals(1_000_000f, slider.valueAt(slider.fraction(1_000_000f) + 0.015f));
		assertEquals(5_000_000f, slider.valueAt(slider.fraction(5_000_000f) - 0.015f));
		assertEquals(50_000_000f, slider.valueAt(slider.fraction(50_000_000f) + 0.01f));
		float between = slider.valueAt(slider.fraction(1_000_000f) + 0.06f);
		assertTrue(between > 1_000_000f && between < 5_000_000f, "away from them the knob moves freely: " + between);
	}

	@Test
	void plainSlidersStayLinear() {
		FloatSetting plain = new FloatSetting("size", Component.literal("Size"), 0.5f, 0f, 2f, 2);
		assertEquals(0.25f, plain.fraction(0.5f), 1e-6f);
		assertEquals(1f, plain.valueAt(0.5f), 1e-6f);
		assertEquals(0.75f, plain.parseTyped("0,75"), 1e-6f);
		FloatSetting percent = new FloatSetting("volume", Component.literal("Volume"), 0.6f, 0f, 1f, 2).percent();
		assertEquals(0.75f, percent.parseTyped("75%"), 1e-6f);
	}
}
