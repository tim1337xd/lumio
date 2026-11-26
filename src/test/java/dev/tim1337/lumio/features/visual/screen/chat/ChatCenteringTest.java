package dev.tim1337.lumio.features.visual.screen.chat;

import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A server's centered lines are centered in the chat's own width, every row ends inside that width, and what is clicked lies where it is drawn. */
class ChatCenteringTest {
	/** As the game's font: a space is 4 wide, a letter 6, bold one more. */
	private static final StringSplitter SPLITTER = new StringSplitter((codePoint, style) -> (codePoint == ' ' ? 4f : 6f) + (style.isBold() ? 1f : 0f));
	private static final String REWARDS = "You have 4 unclaimed event rewards!";
	private static final String CLAIM = "CLICK HERE to claim them!";

	/** @return a line padded as a server centers it for the game's chat: spaces until the text's middle is at the middle */
	private static String centered(String text) {
		float half = SPLITTER.stringWidth(text) / 2f;
		StringBuilder padded = new StringBuilder();
		for (float pad = 0f; pad + half < 154f; pad += 4f) {
			padded.append(' ');
		}
		return padded.append(text).toString();
	}

	private static List<FormattedCharSequence> rows(FormattedText message, int width) {
		return ChatWrap.rows(message, width, SPLITTER, text -> sink -> text.visit((style, part) -> {
			part.codePoints().forEach(codePoint -> sink.accept(0, style, codePoint));
			return Optional.empty();
		}, Style.EMPTY).isEmpty());
	}

	private static String text(FormattedCharSequence row) {
		StringBuilder out = new StringBuilder();
		row.accept((position, style, codePoint) -> {
			out.appendCodePoint(codePoint);
			return true;
		});
		return out.toString();
	}

	@Test
	void aPaddedCenteredLineIsRecentredAndStaysInsideTheWidth() {
		for (int width : new int[] {180, 240, 313, 420}) {
			for (String line : new String[] {REWARDS, CLAIM}) {
				List<FormattedCharSequence> rows = rows(FormattedText.of(centered(line)), width);
				StringBuilder joined = new StringBuilder();
				for (FormattedCharSequence row : rows) {
					ChatCenteredRow centered = assertInstanceOf(ChatCenteredRow.class, row, line + " at " + width);
					int left = ChatCentering.left(width, centered.width());
					assertTrue(left >= 0 && left + centered.width() <= width, "inside the width " + width);
					// in the middle: the room left and right of it differs by a pixel at most
					assertTrue(Math.abs(left - (width - centered.width() - left)) <= 1, "centered at " + width);
					assertFalse(text(row).startsWith(" "), "no padding left");
					joined.append(joined.isEmpty() ? "" : " ").append(text(row).strip());
				}
				assertEquals(line, joined.toString(), "the text itself is unchanged");
			}
		}
	}

	@Test
	void aCenteredLineTooLongWrapsAndEveryRowIsCentered() {
		List<FormattedCharSequence> rows = rows(FormattedText.of(centered(REWARDS)), 120);
		assertTrue(rows.size() > 1);
		for (FormattedCharSequence row : rows) {
			assertTrue(assertInstanceOf(ChatCenteredRow.class, row).width() <= 120);
		}
	}

	@Test
	void longLinesWrapInsideTheWidth() {
		String talk = "Alex: a long line of plain talk that goes on and on past the width of the chat so that it has to be wrapped into rows";
		String word = "averylongwordwithoutanyspaces".repeat(6);
		for (int width : new int[] {100, 233, 313}) {
			for (String line : new String[] {talk, word, "   " + talk}) {
				List<FormattedCharSequence> rows = rows(FormattedText.of(line), width);
				assertTrue(rows.size() > 1, "wrapped at " + width);
				for (FormattedCharSequence row : rows) {
					assertFalse(row instanceof ChatCenteredRow, "plain talk is not centered");
					assertTrue(SPLITTER.stringWidth(row) <= width, "a row of " + SPLITTER.stringWidth(row) + " in " + width);
				}
			}
		}
	}

	@Test
	void onlyLinesWithTheirMiddleAtTheGamesMiddleAreCentered() {
		assertNotNull(ChatCentering.unpadded(FormattedText.of(centered(CLAIM)), SPLITTER));
		assertNull(ChatCentering.unpadded(FormattedText.of(CLAIM), SPLITTER), "no padding");
		assertNull(ChatCentering.unpadded(FormattedText.of("    - a list entry"), SPLITTER), "indented, not centered");
		assertNull(ChatCentering.unpadded(FormattedText.of("  " + CLAIM), SPLITTER), "two spaces are no centering");
		assertNull(ChatCentering.unpadded(FormattedText.of("          "), SPLITTER), "only spaces");
		assertTrue(ChatCentering.padded(100f, 112f));
		assertFalse(ChatCentering.padded(40f, 112f));
	}

	@Test
	void legacyColorCodesInThePaddingAreNoText() {
		FormattedText unpadded = ChatCentering.unpadded(FormattedText.of("§r" + centered("§6§lCLICK HERE §r§bto claim!")), SPLITTER);
		assertNotNull(unpadded);
		assertEquals("CLICK HERE to claim!", unpadded.getString());
	}

	@Test
	void clickAreasMatchAfterRecentering() {
		// the click of "CLICK HERE" keeps to its letters: the row holds the style, and drawing and clicking both start at the same left
		Style click = Style.EMPTY.withBold(true).withClickEvent(new ClickEvent.RunCommand("/rewards"));
		int pad = centered(CLAIM).length() - CLAIM.length();
		FormattedText message = FormattedText.composite(FormattedText.of(" ".repeat(pad)), FormattedText.of("CLICK HERE", click), FormattedText.of(" to claim them!"));
		int width = 240;
		List<FormattedCharSequence> rows = rows(message, width);
		assertEquals(1, rows.size());
		ChatCenteredRow row = assertInstanceOf(ChatCenteredRow.class, rows.getFirst());
		int left = ChatCentering.left(width, row.width());
		// where each letter is drawn, and whether a click there runs the command: the first ten letters, from the row's left on
		float[] x = {left};
		int[] letters = {0};
		row.accept((position, style, codePoint) -> {
			boolean clickable = style.getClickEvent() != null;
			assertEquals(letters[0] < "CLICK HERE".length(), clickable, "letter " + letters[0] + " at " + x[0]);
			assertTrue(x[0] >= left && x[0] < left + row.width(), "inside the drawn row");
			x[0] += (codePoint == ' ' ? 4f : 6f) + (style.isBold() ? 1f : 0f);
			letters[0]++;
			return true;
		});
		assertEquals(CLAIM.length(), letters[0]);
		assertEquals(left + row.width(), Math.round(x[0]), "the row's width is that of its letters");
	}
}
