package dev.tim1337.lumio.features.visual.screen.chat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The "[Lumio]" name's two colors: both bright enough on the dark chat, a soft difference between them, light colors left as they are. */
class ChatNameColorsTest {
	@Test
	void darkAccentsAreLiftedAndBothEndsReadable() {
		for (int accent : new int[] {0x0A84FF, 0x5B00FF, 0x1E1E8C, 0x8B0000, 0x30D158, 0xFFD60A, 0x000000}) {
			int[] colors = ChatNameColors.fromAccent(accent);
			for (int color : colors) {
				assertTrue(ChatNameColors.luminance(color) >= ChatNameColors.LEAST_LUMINANCE - 0.02, Integer.toHexString(accent));
			}
		}
	}

	@Test
	void aSoftGradientNotOneColor() {
		int[] colors = ChatNameColors.fromAccent(0x0A84FF);
		assertNotEquals(colors[0], colors[1]);
	}

	@Test
	void lightColorsStay() {
		assertEquals(0xFFD60A, ChatNameColors.readable(0xFFD60A));
	}
}
