package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.render.WorldBackdrop;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;

/** The pictures in the large previews of pages whose subject cannot be drawn itself in a window: the chat with sample lines, and the main menu. They follow the page's settings as they change. */
public final class PagePictures {
	/** What the chat's page has on. */
	public record ChatLook(boolean tabs, boolean time, boolean twelveHours, boolean heads, boolean stripe, boolean highlight, boolean compact, boolean background) {
	}

	/** What the main menu's page has on. */
	public record MenuLook(boolean songCard, boolean lyrics, boolean styledButtons, boolean animated) {
	}

	private record Line(String time12, String time24, int stripe, int head, String text, int color) {
	}

	private static final Line[] CHAT_LINES = {
		new Line("2:28", "14:28", 0, SystemColors.ORANGE, "Steve123: anyone selling a bow?", 0xFFFFFFFF),
		new Line("2:29", "14:29", SystemColors.BLUE, SystemColors.BLUE, "Party > Bob: ready to go?", 0xFF8FB8FF),
		new Line("2:29", "14:29", 0, 0, "You received 1,200 coins", 0xFFC8C8C8),
		new Line("2:31", "14:31", SystemColors.GREEN, SystemColors.PURPLE, "Guild > Eve: anyone up for later?", 0xFF8FE3A0),
		new Line("2:31", "14:31", 0, 0, "A rare drop!", 0xFFFF79C6),
	};

	private static final int PANEL_MARGIN = 14;
	private static final int PAD = 8;
	private static final int ROW = 13;
	private static final int TABS_ROW = 18;
	private static final int INPUT = 16;
	private static final int HEAD = 7;
	private static final int STRIPE = 2;
	private static final int TIME_WIDTH = 28;
	private static final float SMALL = TextScale.SMALL;

	private PagePictures() {
	}

	/** The chat with a few sample lines, in the middle of the preview. */
	public static void chat(Ui ui, ChatLook look, int x, int y, int width, int height) {
		WorldBackdrop.draw(ui, x, y, width, height, Radius.LG);
		Palette palette = ui.palette;
		int panelWidth = width - 2 * PANEL_MARGIN;
		int panelHeight = (look.tabs() ? TABS_ROW : 0) + CHAT_LINES.length * ROW + INPUT + 2 * PAD;
		int left = x + PANEL_MARGIN;
		int top = y + (height - panelHeight) / 2;
		if (look.background()) {
			ui.rounded(left, top, panelWidth, panelHeight, Radius.LG, Ui.withOpacity(palette.window() | 0xFF000000, 0.86f));
			ui.ring(left, top, panelWidth, panelHeight, Radius.LG, 1, palette.cardBorder());
		}
		int rowY = top + PAD;
		if (look.tabs()) {
			int tabX = left + PAD;
			for (String tab : new String[]{"All", "Party", "Guild"}) {
				tabX += ui.chip(tab, tabX, rowY + 6) + 4;
			}
			rowY += TABS_ROW;
		}
		for (Line line : CHAT_LINES) {
			int textX = left + PAD;
			if (look.stripe() && line.stripe() != 0) {
				ui.rounded(left + 3, rowY + 1, STRIPE, ROW - 2, 1f, line.stripe());
			}
			if (look.time()) {
				ui.textAtScaled(look.twelveHours() ? line.time12() : line.time24(), textX, rowY + 2, SMALL, palette.textSecondary());
				textX += TIME_WIDTH;
			}
			if (look.heads() && line.head() != 0) {
				ui.rounded(textX, rowY + 1, HEAD, HEAD, 1f, line.head());
				textX += HEAD + 4;
			}
			String text = line.text();
			if (look.compact() && line == CHAT_LINES[2]) {
				text += " (x3)";
			}
			text = ui.fitScaled(text, left + panelWidth - PAD - textX, SMALL);
			if (look.highlight() && line == CHAT_LINES[0]) {
				ui.rounded(textX - 2, rowY, Math.round(ui.widthScaled(text, SMALL)) + 4, ROW - 2, 2f, Ui.withOpacity(SystemColors.YELLOW, 0.25f));
			}
			ui.textAtScaled(text, textX, rowY + 2, SMALL, line.color());
			rowY += ROW;
		}
		ui.rounded(left + PAD, rowY + 2, panelWidth - 2 * PAD, INPUT - 2, Radius.SM, Ui.withOpacity(palette.text(), 0.06f));
	}

	/** The main menu: its greeting, the last world or server, its buttons, and what plays. */
	public static void mainMenu(Ui ui, MenuLook look, int x, int y, int width, int height) {
		WorldBackdrop.draw(ui, x, y, width, height, Radius.LG);
		Palette palette = ui.palette;
		int left = x + PANEL_MARGIN;
		int right = x + width - PANEL_MARGIN;
		if (look.songCard()) {
			int chip = ui.chipWidth("Your song");
			ui.chip("Your song", right - chip, y + PANEL_MARGIN + 6);
		}
		int middle = y + height / 2;
		ui.textAtScaled("Good evening", left, middle - 46, SMALL, palette.textSecondary());
		ui.textAtScaledTitle("Ready to play?", left, middle - 36, TextScale.TITLE, 0xFFFFFFFF);
		int card = right - left;
		int cardTop = middle - 8;
		ui.rounded(left, cardTop, card, 30, Radius.MD, Ui.withOpacity(palette.window() | 0xFF000000, 0.86f));
		ui.rounded(left + 6, cardTop + 6, 18, 18, Radius.SM, SystemColors.ORANGE);
		ui.textAtScaled("Your last server", left + 30, cardTop + 11, SMALL, 0xFFFFFFFF);
		int buttonTop = cardTop + 36;
		int half = (card - 6) / 2;
		int buttonColor = look.styledButtons() ? Ui.withOpacity(palette.window() | 0xFF000000, 0.86f) : 0xFF6B6B6B;
		ui.rounded(left, buttonTop, half, 20, look.styledButtons() ? Radius.SM : 0f, buttonColor);
		ui.rounded(left + half + 6, buttonTop, half, 20, look.styledButtons() ? Radius.SM : 0f, buttonColor);
		ui.textCentered("Options", left + half / 2, buttonTop + 10, 0xFFFFFFFF);
		ui.textCentered("Quit", left + half + 6 + half / 2, buttonTop + 10, 0xFFFFFFFF);
		if (look.lyrics()) {
			ui.textCentered("The line that is sung right now", x + width / 2, y + height - PANEL_MARGIN - 18, 0xFFFFFFFF);
		}
	}
}
