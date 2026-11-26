package dev.tim1337.lumio.ui;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** An icon that stands in a line of text is sized from that text, never in fixed pixels: at least 0.8 of the line's text height everywhere. */
class InlineIconTest {
	@Test
	void anInlineIconIsAtLeastFourFifthsOfItsText() {
		// the mod's font and the game's (its letters 8 of its 9 pixels), at GUI scales 2 to 4 and the HUD's own sizes
		for (float letters : new float[] {Ui.TEXT_HEIGHT, 8f}) {
			for (float scale : new float[] {0.5f, 0.75f, 1f, 1.5f, 2f, 3f, 4f}) {
				float text = letters * scale;
				float icon = Ui.inlineIconHeight(text);
				assertTrue(icon >= 0.8f * text, "an icon of " + icon + " beside text of " + text);
				assertTrue(icon <= text, "and no taller than the text");
			}
		}
	}

	@Test
	void theScoreboardAndTheInfoBarSizeTheirIconsFromTheirText() throws IOException {
		for (String file : new String[] {"features/visual/hud/ScoreboardHudFeature.java", "features/visual/hud/InfoBarRenderer.java"}) {
			String source = Files.readString(Path.of("src/main/java/dev/tim1337/lumio", file));
			assertTrue(source.contains("inlineIcon("), file + " draws its line icons with inlineIcon");
			assertFalse(source.contains("ui.icon(segment.icon"), file + " draws a segment's icon at a fixed size");
		}
	}
}
