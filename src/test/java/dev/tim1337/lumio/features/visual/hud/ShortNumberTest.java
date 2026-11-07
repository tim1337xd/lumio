package dev.tim1337.lumio.features.visual.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** The bar numbers in short, for a bar too narrow for them in full. */
class ShortNumberTest {
	@Test
	void largeNumbersAreShortened() {
		assertEquals("950", StatusBar.shortNumber(950));
		assertEquals("12.4k", StatusBar.shortNumber(12_400));
		assertEquals("12.5k", StatusBar.shortNumber(12_450));
		assertEquals("3k", StatusBar.shortNumber(3_000));
		assertEquals("3.2k", StatusBar.shortNumber(3_200));
		assertEquals("1.2M", StatusBar.shortNumber(1_234_567));
	}
}
