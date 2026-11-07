package dev.tim1337.lumio.features.visual.screen;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.features.visual.screen.chat.ChatScroll;
import dev.tim1337.lumio.features.visual.screen.chat.ChatLayout;
import dev.tim1337.lumio.features.visual.screen.chat.ChatRules;
import dev.tim1337.lumio.mixin.accessor.ChatComponentAccessor;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MacTheme;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * What the chat adds around the messages: its panel when it is open, a soft background behind lines that mention the player, the channel's stripe, the time, the sender's head, a line under
 * links, and Copy and Reply on the line under the mouse. It draws around the game's lines, in the room {@link ChatLayout} keeps; only Copy and Reply can lie on the end of a full line, while the mouse is on it. Lines are read once per frame, as many as
 * are shown.
 */
final class ChatDecor {
	private static final float PANEL_RADIUS = 8f;
	private static final float DIVIDER_OPACITY = 0.08f;
	private static final float ROW_RADIUS = 3.5f;
	/** The stripe: wide, and how far it stays from the top and bottom of a message. */
	private static final float STRIPE_WIDTH = 1.5f;
	private static final int STRIPE_INSET = 2;
	private static final float TIME_TEXT = 0.55f;
	private static final int TIME_COLOR = 0x61FFFFFF;
	private static final int FONT_HEIGHT = 8;
	/** How tall the UI font is at its full size, in GUI pixels. */
	private static final float UI_LINE = 9f;
	private static final int ACTION_FILL = 0x1AFFFFFF;
	private static final int ACTION_HOVER = 0x40FFFFFF;
	private static final int ACTION_GAP = 2;
	private static final int ACTION_PAD = 4;
	private static final int ACTION_HEIGHT = 9;
	private static final float ACTION_TEXT = 0.55f;
	private static final float ACTION_RADIUS = 3f;
	private static final int ACTION_TEXT_COLOR = 0xCCFFFFFF;
	private static final float MENTION_OPACITY = 0.2f;
	private static final float HOVER_OPACITY = 0.06f;
	private static final float DARK_PANEL = 0.9f;
	/** What the lines lie on while the chat is closed, faint and fading with them, so they read over a bright world. */
	private static final float CLOSED_OPACITY = 0.35f;
	private static final float CLOSED_RADIUS = 6f;
	private static final int CLOSED_MARGIN = 2;
	private static final int CLOSED_COLOR = 0xFF0C0C12;
	private static final float SCROLL_WIDTH = 1.5f;
	private static final int SCROLL_INSET = 2;
	private static final int SCROLL_LEAST = 8;
	private static final int SCROLL_COLOR = 0x4DFFFFFF;
	private static final float INPUT_RADIUS = 5f;
	private static final int INPUT_FILL = 0x4D000000;
	private static final int INPUT_RING = 0x88000000;
	private static final int INPUT_PAD = 6;
	/** The panel under the list of suggestions: how far it reaches around the entries, how opaque it is at least (it lies over the lines), and the selected row: the game's row height, its corners and how strong its mark is. */
	private static final int SUGGESTIONS_PAD = 3;
	private static final float SUGGESTIONS_OPACITY = 0.92f;
	private static final int SUGGESTION_ROW = CommandSuggestions.LINE_HEIGHT;
	private static final float SELECTED_RADIUS = 3f;
	private static final float SELECTED_OPACITY = 0.3f;
	private static final float HINT_TEXT = 0.575f;
	private static final int HINT_COLOR = 0x73FFFFFF;
	/** How long a line is shown before it fades, in the game's ticks, and its fade, as the game has them. */
	private static final float FADE_TICKS = 200f;
	private static final float FADE_SHARE = 10f;
	private static final String COPY = "Copy";
	private static final String REPLY = "Reply";
	private static final String SUGGESTIONS_HINT = "Tab for suggestions";
	private static final String CHANNEL_MARK = " · ";
	private static final String TAB_HINT = "Tab";
	/** Behind Copy and Reply where a line reaches them: they lie on its end only while the mouse is on it. */
	private static final int ACTIONS_BACKING = 0xF0141418;
	private static final int ACTIONS_BACKING_MARGIN = 2;
	private static final float ACTIONS_BACKING_RADIUS = 4f;
	/** A line less visible than this is not there. */
	static final float LEAST_ALPHA = 0.01f;
	/** The letters of the time, as strings once: it is drawn letter by letter so its digits line up. */
	private static final String[] LETTERS = new String[128];

	private final ChatFeature chat;
	/** The actions drawn on the line under the mouse, from the last frame, for clicks: in the chat's scaled pixels; a negative left for none. */
	private int actionsLine = -1;
	private int copyLeft = -1;
	private int copyWidth;
	private int replyLeft = -1;
	private int replyWidth;
	private int actionsTop;
	/** While the closed chat's backing is worked out: the highest shown line, and how visible the most visible line is. */
	private int closedTop;
	private int closedBottom;
	private float closedAlpha;
	private boolean closedDrawn;

	ChatDecor(ChatFeature chat) {
		this.chat = chat;
	}

	private static Ui ui(GuiGraphicsExtractor graphics, Font font) {
		var menu = ModFeatures.MENU;
		return new Ui(graphics, font, HudStyle.palette(), menu.accentArgb(), 0f, false);
	}

	/** Draws what lies behind the lines: the backgrounds of lines, and while the chat is closed a faint backing. */
	void before(GuiGraphicsExtractor graphics, Font font, int tick, int mouseX, int mouseY, boolean open) {
		chat.followLines(font, tick, open);
		Ui ui = ui(graphics, font);
		int width = chat.panelWidthInChat();
		int rowWidth = width - 2 * ChatLayout.ROW_INSET;
		int under = open ? chat.lineAt(graphics.guiHeight(), mouseX, mouseY) : -1;
		closedDrawn = open || chat.background() == ChatFeature.Background.NONE;
		if (!closedDrawn) {
			closedTop = Integer.MAX_VALUE;
			closedBottom = Integer.MIN_VALUE;
			closedAlpha = 0f;
			forLines(graphics, tick, false, (line, index, top, height, alpha, info) -> {
				closedTop = Math.min(closedTop, top);
				closedBottom = Math.max(closedBottom, top + height);
				closedAlpha = Math.max(closedAlpha, alpha);
			});
		}
		forLines(graphics, tick, open, (line, index, top, height, alpha, info) -> {
			if (!closedDrawn) {
				closedDrawn = true;
				ui.rounded(0, closedTop - CLOSED_MARGIN, width, closedBottom - closedTop + 2 * CLOSED_MARGIN, CLOSED_RADIUS, Ui.withOpacity(CLOSED_COLOR, CLOSED_OPACITY * closedAlpha));
			}
			if (info.mention()) {
				ui.rounded(ChatLayout.ROW_INSET, top, rowWidth, height, ROW_RADIUS, Ui.withOpacity(ui.accent, MENTION_OPACITY * alpha));
			}
			if (index == under) {
				ui.rounded(ChatLayout.ROW_INSET, top, rowWidth, height, ROW_RADIUS, Ui.withOpacity(0xFFFFFFFF, HOVER_OPACITY));
			}
		});
	}

	/** The panel, from its top bar to below the input; everything of the open chat lies inside it. */
	private void drawPanel(Ui ui, int screenHeight) {
		int left = chat.panelLeft();
		int width = chat.panelWidth();
		int top = chat.panelTop(screenHeight);
		int height = chat.panelBottom(screenHeight) - top;
		MacTheme theme = chat.background() == ChatFeature.Background.DARK ? MacTheme.DARK : chat.panelTheme();
		HudStyle.setPanelTheme(theme);
		try {
			HudStyle.panel(ui, left, top, width, height, PANEL_RADIUS, chat.background() == ChatFeature.Background.DARK ? DARK_PANEL : HudStyle.opacity(), chat.panelGlow());
		} finally {
			HudStyle.setPanelTheme(null);
		}
		if (chat.barShown()) {
			ui.fill(left + 1, top + ChatLayout.BAR - 1, width - 2, 1, Ui.withOpacity(0xFFFFFFFF, theme.lineOpacity(DIVIDER_OPACITY)));
		}
	}

	/** Draws what lies around the lines' text: stripes, the time, heads, the line under links, the scroll bar, and the actions of the line under the mouse. */
	void after(GuiGraphicsExtractor graphics, Font font, int tick, int mouseX, int mouseY, boolean open) {
		Ui ui = ui(graphics, font);
		var lines = ((ChatComponentAccessor) Minecraft.getInstance().gui.hud.getChat()).lumio$lines();
		int under = open ? chat.lineAt(graphics.guiHeight(), mouseX, mouseY) : -1;
		int timeWidth = chat.timeColumn();
		boolean heads = chat.heads();
		int textLeft = ChatLayout.textLeft(timeWidth, heads);
		int panelWidth = chat.panelWidthInChat();
		actionsLine = -1;
		forLines(graphics, tick, open, (line, index, top, height, alpha, info) -> {
			int textY = top + (height - FONT_HEIGHT) / 2;
			boolean first = index + 1 >= lines.size() || lines.get(index + 1).parent() != line.parent();
			int stripe = chat.stripeColor(info);
			if (stripe != 0) {
				// a message's rows make one stripe, away from the message's top and bottom
				int stripeTop = top + (first ? STRIPE_INSET : 0);
				int stripeBottom = top + height - (line.endOfEntry() ? STRIPE_INSET : 0);
				ui.roundedSmooth(ChatLayout.ROW_INSET, stripeTop, STRIPE_WIDTH, stripeBottom - stripeTop, STRIPE_WIDTH / 2f, Ui.withOpacity(0xFF000000 | stripe, alpha));
			}
			if (first && timeWidth > 0 && info.time() != null && !info.plain().isBlank()) {
				drawTime(ui, info.time(), ChatLayout.timeLeft(), textY + (FONT_HEIGHT - UI_LINE * TIME_TEXT) / 2f, alpha);
			}
			if (first && heads && info.sender() != null) {
				drawHead(graphics, info, ChatLayout.headLeft(timeWidth), textY, alpha);
			}
			if (info.link() && chat.links()) {
				underlineLink(ui, font, line, textLeft, textY, alpha);
			}
			if (index == under && chat.hoverActions()) {
				drawActions(ui, index, info, textY, mouseX, mouseY, graphics.guiHeight(), panelWidth, textLeft + font.width(line.content()));
			}
		});
		if (open) {
			drawScrollBar(graphics, ui, panelWidth);
		}
	}

	/** The time, with its digits in cells of one width, so the times line up from line to line. */
	private static void drawTime(Ui ui, String time, float left, float y, float alpha) {
		float x = left;
		float digit = ChatFeature.digitWidth(ui);
		int color = Ui.withOpacity(TIME_COLOR, alpha);
		for (int i = 0; i < time.length(); i++) {
			char letter = time.charAt(i);
			String shown = letter(letter);
			if (letter >= '0' && letter <= '9') {
				ui.textAtScaled(shown, x + (digit - ui.widthScaled(shown, TIME_TEXT)) / 2f, y, TIME_TEXT, color);
				x += digit;
			} else {
				ui.textAtScaled(shown, x, y, TIME_TEXT, color);
				x += ui.widthScaled(shown, TIME_TEXT);
			}
		}
	}

	private static String letter(char letter) {
		if (letter >= LETTERS.length) {
			return String.valueOf(letter);
		}
		String known = LETTERS[letter];
		if (known == null) {
			known = String.valueOf(letter);
			LETTERS[letter] = known;
		}
		return known;
	}

	private void drawHead(GuiGraphicsExtractor graphics, ChatFeature.Info info, int x, int y, float alpha) {
		var connection = Minecraft.getInstance().getConnection();
		PlayerInfo player = connection == null ? null : connection.getPlayerInfo(info.sender());
		if (player != null) {
			PlayerFaceExtractor.extractRenderState(graphics, player.getSkin(), x, y, ChatLayout.HEAD, Ui.withOpacity(0xFFFFFFFF, alpha));
		}
	}

	private static void underlineLink(Ui ui, Font font, GuiMessage.Line line, int textLeft, int y, float alpha) {
		String text = ChatFeature.textOf(line.content());
		int[] link = ChatRules.link(text);
		if (link != null) {
			int from = font.width(text.substring(0, link[0]));
			int width = font.width(text.substring(link[0], link[1]));
			ui.fill(textLeft + from, y + FONT_HEIGHT, width, 1, Ui.withOpacity(ui.accent, alpha));
		}
	}

	private void drawActions(Ui ui, int index, ChatFeature.Info info, int y, int mouseX, int mouseY, int guiHeight, int panelWidth, int textRight) {
		int right = ChatLayout.contentRight(panelWidth);
		actionsLine = index;
		actionsTop = y - (ACTION_HEIGHT - FONT_HEIGHT) / 2;
		double[] inChat = chat.toChat(guiHeight, mouseX, mouseY);
		replyLeft = -1;
		boolean reply = chat.replyPrefix(info) != null;
		if (reply) {
			replyWidth = Math.round(ui.widthScaled(REPLY, ACTION_TEXT)) + 2 * ACTION_PAD;
			replyLeft = right - replyWidth;
			right = replyLeft - ACTION_GAP;
		}
		copyWidth = Math.round(ui.widthScaled(COPY, ACTION_TEXT)) + 2 * ACTION_PAD;
		copyLeft = right - copyWidth;
		if (textRight + ACTIONS_BACKING_MARGIN > copyLeft) {
			int backingLeft = copyLeft - ACTIONS_BACKING_MARGIN;
			int backingRight = (reply ? replyLeft + replyWidth : copyLeft + copyWidth) + ACTIONS_BACKING_MARGIN;
			ui.rounded(backingLeft, actionsTop - 1, backingRight - backingLeft, ACTION_HEIGHT + 2, ACTIONS_BACKING_RADIUS, ACTIONS_BACKING);
		}
		if (reply) {
			drawAction(ui, REPLY, replyLeft, replyWidth, inChat);
		}
		drawAction(ui, COPY, copyLeft, copyWidth, inChat);
	}

	private void drawAction(Ui ui, String text, int left, int width, double[] mouse) {
		boolean over = mouse[0] >= left && mouse[0] < left + width && mouse[1] >= actionsTop && mouse[1] < actionsTop + ACTION_HEIGHT;
		ui.rounded(left, actionsTop, width, ACTION_HEIGHT, ACTION_RADIUS, over ? ACTION_HOVER : ACTION_FILL);
		ui.textAtScaled(text, left + ACTION_PAD, actionsTop + (ACTION_HEIGHT - UI_LINE * ACTION_TEXT) / 2f, ACTION_TEXT, ACTION_TEXT_COLOR);
	}

	/** @return which action of the line under the mouse a click hit: Copy is 0, Reply 1, none -1 */
	int actionAt(int line, double chatX, double chatY) {
		if (line != actionsLine || chatY < actionsTop || chatY >= actionsTop + ACTION_HEIGHT) {
			return -1;
		}
		if (replyLeft >= 0 && chatX >= replyLeft && chatX < replyLeft + replyWidth) {
			return 1;
		}
		return copyLeft >= 0 && chatX >= copyLeft && chatX < copyLeft + copyWidth ? 0 : -1;
	}

	/** @return how tall the scroll bar's thumb is */
	static int thumbHeight(int track, int perPage, int total) {
		return Math.max(SCROLL_LEAST, track * perPage / total);
	}

	/** The scroll bar: thin and round, inside the panel in the rows' right padding. */
	private void drawScrollBar(GuiGraphicsExtractor graphics, Ui ui, int panelWidth) {
		ChatComponent component = Minecraft.getInstance().gui.hud.getChat();
		var access = (ChatComponentAccessor) component;
		int total = access.lumio$lines().size();
		int perPage = component.getLinesPerPage();
		if (total <= perPage) {
			return;
		}
		int height = Math.max(1, access.lumio$lineHeight());
		float scale = chat.scale();
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(chat.panelLeft(), chat.moveY());
		pose.scale(scale, scale);
		try {
			int bottom = Mth.floor(chat.baseBottom(graphics.guiHeight()) / scale);
			int track = perPage * height;
			int thumb = thumbHeight(track, perPage, total);
			float y = bottom - thumb - ChatScroll.thumbFromBottom(chat.scrollPosition(), total - perPage, track, thumb);
			ui.roundedSmooth(panelWidth - SCROLL_INSET - SCROLL_WIDTH, y, SCROLL_WIDTH, thumb, SCROLL_WIDTH / 2f, SCROLL_COLOR);
		} finally {
			pose.popMatrix();
		}
	}

	/** The input in the mod's style, inside the panel: where the player types, and which channel it goes to. */
	void drawInput(GuiGraphicsExtractor graphics, Font font, EditBox input, int screenHeight) {
		Ui ui = ui(graphics, font);
		// first of all that the open chat draws: its tabs and lines lie on the panel
		if (chat.background() != ChatFeature.Background.NONE) {
			drawPanel(ui, screenHeight);
		}
		int left = chat.panelLeft() + ChatLayout.INPUT_SIDE;
		int width = chat.panelWidth() - 2 * ChatLayout.INPUT_SIDE;
		int top = chat.inputTop(screenHeight);
		ui.rounded(left, top, width, ChatLayout.INPUT_HEIGHT, INPUT_RADIUS, INPUT_FILL);
		if (!chat.searchFocused()) {
			ui.ring(left, top, width, ChatLayout.INPUT_HEIGHT, INPUT_RADIUS, 1, INPUT_RING & 0xFF000000 | chat.accentArgb() & 0xFFFFFF);
		}
		// the game's field without its frame: its text starts at its top
		input.setX(left + INPUT_PAD);
		input.setY(top + (ChatLayout.INPUT_HEIGHT - FONT_HEIGHT) / 2);
		int fieldWidth = Math.max(1, width - 2 * INPUT_PAD);
		if (input.getWidth() != fieldWidth) {
			input.setWidth(fieldWidth);
			// what is typed stays in view in the new width; a selection is left as it is
			if (input.getHighlighted().isEmpty()) {
				input.moveCursorTo(input.getCursorPosition(), false);
			}
		}
		String channel = chat.typingChannel() + CHANNEL_MARK + SUGGESTIONS_HINT;
		int room = width - 3 * INPUT_PAD - font.width(input.getValue());
		String hint = switch (ChatLayout.hintFor(room, hintWidth(ui, channel), hintWidth(ui, SUGGESTIONS_HINT), hintWidth(ui, TAB_HINT))) {
			case CHANNEL -> channel;
			case SUGGESTIONS -> SUGGESTIONS_HINT;
			case TAB -> TAB_HINT;
			case NONE -> null;
		};
		if (hint != null) {
			ui.textAtScaled(hint, left + width - INPUT_PAD - hintWidth(ui, hint), top + (ChatLayout.INPUT_HEIGHT - UI_LINE * HINT_TEXT) / 2f, HINT_TEXT, HINT_COLOR);
		}
	}

	/**
	 * Draws the panel under the game's list of suggestions: the chat panel's theme, corners and padding, a little more opaque, as it lies over the lines; the selected row marked
	 * softly in the accent. The game's entries are drawn over it, kept inside it until {@link #suggestionsDrawn}.
	 */
	void drawSuggestions(GuiGraphicsExtractor graphics, Font font, Rect2i list, int row) {
		Ui ui = ui(graphics, font);
		int left = list.getX() - SUGGESTIONS_PAD;
		int top = list.getY() - SUGGESTIONS_PAD;
		int width = list.getWidth() + 2 * SUGGESTIONS_PAD;
		int height = list.getHeight() + 2 * SUGGESTIONS_PAD;
		MacTheme theme = chat.background() == ChatFeature.Background.DARK ? MacTheme.DARK : chat.panelTheme();
		HudStyle.setPanelTheme(theme);
		try {
			HudStyle.panel(ui, left, top, width, height, INPUT_RADIUS, Math.max(HudStyle.opacity(), SUGGESTIONS_OPACITY), false);
		} finally {
			HudStyle.setPanelTheme(null);
		}
		int rows = list.getHeight() / SUGGESTION_ROW;
		if (row >= 0 && row < rows) {
			ui.rounded(list.getX() - 1, list.getY() + row * SUGGESTION_ROW, list.getWidth() + 2, SUGGESTION_ROW, SELECTED_RADIUS,
				Ui.withOpacity(0xFF000000 | chat.accentArgb() & 0xFFFFFF, SELECTED_OPACITY));
		}
		// an entry wider than the panel allows is cut at its side
		graphics.enableScissor(list.getX(), top, list.getX() + list.getWidth(), top + height);
	}

	void suggestionsDrawn(GuiGraphicsExtractor graphics) {
		graphics.disableScissor();
	}

	private static int hintWidth(Ui ui, String hint) {
		return Math.round(ui.widthScaled(hint, HINT_TEXT));
	}

	/** What is done for a shown line: in the chat's own pixels, with how visible it is. */
	@FunctionalInterface
	private interface LineWork {
		void at(GuiMessage.Line line, int index, int top, int height, float alpha, ChatFeature.Info info);
	}

	private void forLines(GuiGraphicsExtractor graphics, int tick, boolean open, LineWork work) {
		ChatComponent component = Minecraft.getInstance().gui.hud.getChat();
		var access = (ChatComponentAccessor) component;
		var lines = access.lumio$lines();
		int perPage = component.getLinesPerPage();
		int scroll = access.lumio$scroll();
		int height = Math.max(1, access.lumio$lineHeight());
		float scale = chat.scale();
		// as the game places its lines, so they meet: from the bottom in its scaled pixels, then moved
		int bottom = Mth.floor(chat.baseBottom(graphics.guiHeight()) / scale);
		var pose = graphics.pose();
		pose.pushMatrix();
		boolean clipped = chat.clipRows(graphics);
		pose.translate(chat.panelLeft(), chat.moveY() + chat.scrollShift());
		pose.scale(scale, scale);
		try {
			for (int shown = 0, rows = chat.rowsDrawn(perPage); shown < rows && scroll + shown < lines.size(); shown++) {
				int index = scroll + shown;
				GuiMessage.Line line = lines.get(index);
				float alpha = open ? 1f : alpha(tick, line);
				if (alpha > LEAST_ALPHA) {
					work.at(line, index, bottom - (shown + 1) * height, height, alpha, chat.infoOf(line.parent()));
				}
			}
		} finally {
			pose.popMatrix();
			if (clipped) {
				graphics.disableScissor();
			}
		}
	}

	/** @return how visible a line is while the chat is closed, as the game works it out */
	static float alpha(int tick, GuiMessage.Line line) {
		float age = (tick - line.addedTime()) / FADE_TICKS;
		float shown = Mth.clamp((1f - age) * FADE_SHARE, 0f, 1f);
		return shown * shown;
	}
}
