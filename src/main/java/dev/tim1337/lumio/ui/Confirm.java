package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.menu.MenuFeature;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.function.Consumer;

/**
 * A yes/no question as a menu-styled card in front of the current screen, which stays visible dimmed behind it. The previous screen returns
 * either way; the action only runs on yes. Enter means yes, Escape means no. A question can carry an option, off until switched on, with the
 * lines it concerns listed under it (such as the keybinds a shared config would add).
 */
public final class Confirm extends Screen {
	private static final int CARD_W = 300;
	private static final int PAD = 16;
	private static final int BUTTON_H = 24;
	private static final int OPTION_H = 22;
	private static final int SWITCH_W = 30;
	private static final int SWITCH_H = 16;
	private static final int DETAIL_H = 10;
	private static final int DETAIL_INDENT = 12;
	/** The width of one column of listed lines when they need more than one, the gap between columns, and the fewest rows per column. */
	private static final int COLUMN_W = 190;
	private static final int COLUMN_GAP = 12;
	private static final int MIN_ROWS = 4;
	/** The space kept free around the card on a small screen. */
	private static final int SCREEN_MARGIN = 10;

	private final Screen back;
	private final String text;
	private final String yes;
	private final String no;
	private final Consumer<Boolean> onYes;
	private final String option;
	private final List<String> details;
	private boolean optionOn;
	private final Spring optionKnob = Spring.smooth(Spring.SNAPPY);
	private int optionY;
	private int detailColumns = 1;
	private int detailRows;
	private final Spring open = Spring.bouncy(Spring.DEFAULT);
	private final Spring yesHover = Spring.smooth(Spring.SNAPPY);
	private final Spring noHover = Spring.smooth(Spring.SNAPPY);
	private int cardW = CARD_W;
	private int cardX;
	private int cardY;
	private int cardH;
	private int buttonY;
	private int buttonW;

	private Confirm(Screen back, String title, String text, String yes, String no, String option, List<String> details, Consumer<Boolean> onYes) {
		super(Component.literal(title));
		this.back = back;
		this.text = text;
		this.yes = yes;
		this.no = no;
		this.onYes = onYes;
		this.option = option;
		this.details = details;
	}

	public static void ask(String title, String text, String yes, String no, Runnable onYes) {
		Minecraft client = Minecraft.getInstance();
		client.setScreenAndShow(new Confirm(client.gui.screen(), title, text, yes, no, null, List.of(), withOption -> onYes.run()));
	}

	/**
	 * Asks with an option that is off until the player switches it on; the lines it concerns are listed under it, so they are seen before
	 * anything changes. @param onYes runs on yes, with whether the option was on
	 */
	public static void askWithOption(String title, String text, String option, List<String> details, String yes, String no, Consumer<Boolean> onYes) {
		Minecraft client = Minecraft.getInstance();
		client.setScreenAndShow(new Confirm(client.gui.screen(), title, text, yes, no, option, List.copyOf(details), onYes));
	}

	@Override
	public boolean isPauseScreen() {
		return back == null || back.isPauseScreen();
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// draw the asking screen behind the card, without mouse hover
		if (back != null) {
			back.extractRenderState(graphics, -1000, -1000, partialTick);
			graphics.nextStratum();
		}
		open.follow(1f);
		float amount = Mth.clamp(open.value(), 0f, 1.1f);
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, font, HudStyle.palette(), 0xFF000000 | MenuFeature.accentRgb(), 0f, true);
		graphics.fill(0, 0, width, height, Ui.withOpacity(0xFF000000, 0.45f * Mth.clamp(amount, 0f, 1f)));

		List<String> lines = ui.wrap(text, CARD_W - PAD * 2, 6);
		int baseH = PAD + 14 + 8 + lines.size() * 11 + 14 + BUTTON_H + PAD;
		int optionHeight = 0;
		cardW = CARD_W;
		if (option != null) {
			// every listed line is shown: in more columns when they do not fit under each other
			int rowsFit = Math.max(MIN_ROWS, (height - SCREEN_MARGIN * 2 - baseH - 8 - OPTION_H - 2) / DETAIL_H);
			detailColumns = Math.max(1, (details.size() + rowsFit - 1) / rowsFit);
			detailRows = details.isEmpty() ? 0 : (details.size() + detailColumns - 1) / detailColumns;
			if (detailColumns > 1) {
				cardW = Math.min(width - SCREEN_MARGIN * 2, Math.max(CARD_W, PAD * 2 + DETAIL_INDENT + detailColumns * COLUMN_W
					+ (detailColumns - 1) * COLUMN_GAP));
			}
			optionHeight = 8 + OPTION_H + 2 + detailRows * DETAIL_H;
		}
		cardH = baseH + optionHeight;
		cardX = width / 2 - cardW / 2;
		cardY = height / 2 - cardH / 2;
		buttonW = (cardW - PAD * 2 - 8) / 2;
		buttonY = cardY + cardH - PAD - BUTTON_H;

		var pose = graphics.pose();
		pose.pushMatrix();
		float scale = 0.92f + 0.08f * amount;
		pose.translate(width / 2f, height / 2f);
		pose.scale(scale, scale);
		pose.translate(-width / 2f, -height / 2f);
		float alphaBefore = Ui.alpha();
		Ui.setAlpha(Mth.clamp(amount, 0f, 1f));
		try {
			HudStyle.panel(ui, cardX, cardY, cardW, cardH, Radius.XXL, Math.max(0.94f, HudStyle.opacity()));
			ui.textAtScaledBold(title.getString(), cardX + PAD, cardY + PAD, TextScale.BODY, HudStyle.text());
			int y = cardY + PAD + 14 + 8;
			for (String line : lines) {
				ui.textAt(line, cardX + PAD, y, HudStyle.secondary());
				y += 11;
			}
			if (option != null) {
				drawOption(ui, y + 8, mouseX, mouseY);
			}
			boolean overNo = over(mouseX, mouseY, cardX + PAD);
			boolean overYes = over(mouseX, mouseY, cardX + PAD + buttonW + 8);
			noHover.follow(overNo ? 1f : 0f);
			yesHover.follow(overYes ? 1f : 0f);
			int noX = cardX + PAD;
			int yesX = noX + buttonW + 8;
			ui.rounded(noX, buttonY, buttonW, BUTTON_H, BUTTON_H / 2f, Ui.withOpacity(0xFFFFFFFF, 0.08f + 0.08f * noHover.value()));
			ui.textCentered(no, noX + buttonW / 2, buttonY + BUTTON_H / 2, HudStyle.text());
			ui.rounded(yesX, buttonY, buttonW, BUTTON_H, BUTTON_H / 2f, Ui.mix(ui.accent, 0xFFFFFFFF, 0.14f * yesHover.value()));
			ui.textCentered(yes, yesX + buttonW / 2, buttonY + BUTTON_H / 2, 0xFFFFFFFF);
		} finally {
			Ui.setAlpha(alphaBefore);
			pose.popMatrix();
		}
	}

	/** Draws the option: its switch and label, then the lines it concerns. */
	private void drawOption(Ui ui, int top, int mouseX, int mouseY) {
		optionY = top;
		int left = cardX + PAD;
		int middle = top + OPTION_H / 2;
		if (overOption(mouseX, mouseY)) {
			ui.rounded(left - 4, top, cardW - PAD * 2 + 8, OPTION_H, Radius.MD, Ui.withOpacity(0xFFFFFFFF, 0.06f));
		}
		float on = Mth.clamp(optionKnob.follow(optionOn ? 1f : 0f), 0f, 1f);
		ui.rounded(left, middle - SWITCH_H / 2, SWITCH_W, SWITCH_H, Radius.pill(SWITCH_H), Ui.mix(Ui.withOpacity(0xFFFFFFFF, 0.2f), ui.accent, on));
		float knobX = left + SWITCH_H / 2f + on * (SWITCH_W - SWITCH_H);
		float knob = SWITCH_H - 4f;
		ui.roundedSmooth(knobX - knob / 2f, middle - knob / 2f, knob, knob, Radius.pill(knob), 0xFFFFFFFF);
		ui.textMiddle(option, left + SWITCH_W + 8, middle, HudStyle.text());
		int listTop = top + OPTION_H + 2;
		int lineColor = optionOn ? HudStyle.text() : HudStyle.secondary();
		int columnWidth = detailColumns == 1 ? cardW - PAD * 2 - DETAIL_INDENT
			: (cardW - PAD * 2 - DETAIL_INDENT - (detailColumns - 1) * COLUMN_GAP) / detailColumns;
		for (int i = 0; i < details.size(); i++) {
			int column = i / detailRows;
			int x = left + DETAIL_INDENT + column * (columnWidth + COLUMN_GAP);
			int y = listTop + (i % detailRows) * DETAIL_H;
			ui.textAtScaled(ui.fit(details.get(i), columnWidth), x, y, TextScale.SMALL, lineColor);
		}
	}

	private boolean overOption(double mouseX, double mouseY) {
		return option != null && mouseX >= cardX + PAD && mouseX < cardX + cardW - PAD && mouseY >= optionY && mouseY < optionY + OPTION_H;
	}

	private boolean over(double mouseX, double mouseY, int x) {
		return mouseX >= x && mouseX < x + buttonW && mouseY >= buttonY && mouseY < buttonY + BUTTON_H;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		if (ScreenInput.button(event) != 0) {
			return true;
		}
		if (overOption(event.x(), event.y())) {
			optionOn = !optionOn;
			UiSounds.toggle(optionOn);
			return true;
		}
		if (over(event.x(), event.y(), cardX + PAD + buttonW + 8)) {
			answer(true);
		} else if (over(event.x(), event.y(), cardX + PAD)) {
			answer(false);
		}
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER) {
			answer(true);
			return true;
		}
		if (event.key() == InputConstants.KEY_ESCAPE) {
			answer(false);
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public void onClose() {
		answer(false);
	}

	private boolean answered;

	private void answer(boolean accepted) {
		if (answered) {
			return;
		}
		answered = true;
		UiSounds.click();
		minecraft.setScreenAndShow(back);
		if (accepted) {
			onYes.accept(optionOn);
		}
	}
}
