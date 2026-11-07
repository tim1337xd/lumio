package dev.tim1337.lumio.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MenuLayoutTest {
	private static final float EXACT = 0.001f;

	@Test
	void theWidthIsItsShareBetweenItsLimits() {
		assertEquals(806, MenuLayout.width(960), "1080p at GUI scale 2");
		assertEquals(880, MenuLayout.width(1920), "4K at GUI scale 2: scaled up from here");
		assertEquals(600, MenuLayout.width(480), "a small screen: scaled down from here");
	}

	@Test
	void theHeightIsItsShareBetweenItsLimits() {
		assertEquals(497, MenuLayout.height(540));
		assertEquals(540, MenuLayout.height(1080));
		assertEquals(400, MenuLayout.height(270));
	}

	@Test
	void itTakesTheSameShareOfTheScreenAt1080pAnd4k() {
		float at1080p = MenuLayout.width(960) * MenuLayout.defaultZoom(960, 540) * 2;
		float at4k = MenuLayout.width(1920) * MenuLayout.defaultZoom(1920, 1080) * 2;
		assertEquals(1612f, at1080p, 1f);
		assertEquals(2 * at1080p, at4k, 4f);
	}

	@Test
	void theWindowFitsAnyScreen() {
		int[][] screens = {{1920, 1080}, {3840, 2160}, {1366, 768}, {2560, 1440}, {854, 480}, {3440, 1440}};
		for (int[] screen : screens) {
			for (int gui = 1; gui <= 9; gui++) {
				int width = screen[0] / gui;
				int height = screen[1] / gui;
				if (width < 320 || height < 240) {
					continue;
				}
				float zoom = MenuLayout.defaultZoom(width, height);
				assertTrue(MenuLayout.width(width) * zoom <= width + EXACT, screen[0] + " at GUI scale " + gui);
				assertTrue(MenuLayout.height(height) * zoom <= height + EXACT, screen[0] + " at GUI scale " + gui);
			}
		}
	}

	@Test
	void theSizeDependsOnTheScreenAlone() {
		assertEquals(MenuLayout.defaultZoom(960, 540), MenuLayout.defaultZoom(960, 540), EXACT);
		assertEquals(1f, MenuLayout.autoZoom(960, 806), 0.01f);
		assertEquals(1.83f, MenuLayout.autoZoom(1920, 880), 0.01f);
	}
}
