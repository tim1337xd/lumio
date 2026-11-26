package dev.tim1337.lumio.render;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.features.music.CutoutTestAccess;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.awt.Font;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.geom.AffineTransform;
import java.io.InputStream;

/** The lyrics' distance field atlas: every letter the words can have is in it, and its distances are on the right side of every edge. */
class SdfAtlasTest {
	private static SdfAtlas atlas;
	private static Font font;

	@BeforeAll
	static void build() throws Exception {
		try (InputStream in = SdfAtlasTest.class.getResourceAsStream("/assets/lumio/font/inter_bold.ttf")) {
			font = Font.createFont(Font.TRUETYPE_FONT, in);
		}
		atlas = SdfAtlas.build(font, SdfAtlas.LATIN, SdfText.EM, SdfText.RANGE, SdfText.SIZE);
	}

	@Test
	void theAtlasHasEveryLetterOfTheSymbolWordsAndTheUmlauts() {
		for (String word : CutoutTestAccess.allSymbolWords()) {
			assertTrue(atlas.covers(word), "a letter is missing in " + word);
		}
		for (char c : "äöüÄÖÜßéèêàçñ’“”–…".toCharArray()) {
			assertNotNull(atlas.glyph(c), "missing " + c);
		}
		assertTrue(atlas.covers("Wir fahren durch die Nacht und mein Herz brennt, Größe! Straße? ÜBER"));
	}

	@Test
	void theDistancesAreOnTheRightSideOfTheOutline() {
		FontRenderContext context = new FontRenderContext(null, true, true);
		Font sized = font.deriveFont((float) atlas.em);
		int checked = 0;
		int wrong = 0;
		for (char c : "aeghkmosxyzABGKMRSWäöüß&@%8".toCharArray()) {
			SdfAtlas.Glyph glyph = atlas.glyph(c);
			assertNotNull(glyph);
			Shape outline = sized.createGlyphVector(context, String.valueOf(c)).getGlyphOutline(0);
			int left = Math.round(glyph.left() * atlas.em);
			int top = Math.round(glyph.top() * atlas.em);
			Shape moved = AffineTransform.getTranslateInstance(-left, -top).createTransformedShape(outline);
			for (int y = 0; y < glyph.height(); y++) {
				for (int x = 0; x < glyph.width(); x++) {
					double truth = atlas.distance(glyph.atlasX() + x, glyph.atlasY() + y, 3);
					if (Math.abs(truth) < 1.0) {
						continue;
					}
					double r = atlas.distance(glyph.atlasX() + x, glyph.atlasY() + y, 0);
					double g = atlas.distance(glyph.atlasX() + x, glyph.atlasY() + y, 1);
					double b = atlas.distance(glyph.atlasX() + x, glyph.atlasY() + y, 2);
					double median = Math.max(Math.min(r, g), Math.min(Math.max(r, g), b));
					boolean inside = moved.contains(x + 0.5, y + 0.5);
					checked++;
					wrong += (median > 0) != inside || (truth > 0) != inside ? 1 : 0;
				}
			}
		}
		assertTrue(checked > 10_000);
		assertEquals(0, wrong, "pixels a pixel or more from the edge on the wrong side, of " + checked);
	}

	@Test
	void aSmoothShapeIsOneChannelAndACornerIsTwo() {
		java.util.List<java.util.List<SdfEdges.Edge>> square = SdfEdges.contours(new java.awt.Rectangle(0, 0, 10, 10));
		assertEquals(1, square.size());
		for (int i = 0; i < square.getFirst().size(); i++) {
			int a = square.getFirst().get(i).color;
			int b = square.getFirst().get((i + 1) % square.getFirst().size()).color;
			assertTrue(a != b && Integer.bitCount(a & b) == 1, "the edges at a corner share one channel");
		}
		// a circle of quadratic curves, as a TrueType font has them: their tangents meet smoothly
		java.awt.geom.Path2D.Double round = new java.awt.geom.Path2D.Double();
		int parts = 8;
		double reach = 10 / Math.cos(Math.PI / parts);
		round.moveTo(10, 0);
		for (int i = 0; i < parts; i++) {
			double middle = (i + 0.5) * 2 * Math.PI / parts;
			double end = (i + 1) * 2 * Math.PI / parts;
			round.quadTo(reach * Math.cos(middle), reach * Math.sin(middle), 10 * Math.cos(end), 10 * Math.sin(end));
		}
		round.closePath();
		java.util.List<java.util.List<SdfEdges.Edge>> circle = SdfEdges.contours(round);
		for (SdfEdges.Edge edge : circle.getFirst()) {
			assertEquals(SdfEdges.WHITE, edge.color, "a smooth contour has all channels");
		}
	}
}
