package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.config.setting.ActionRowSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import java.util.List;

/**
 * A card row that opens something: an icon tile, the title with a line under it, and on the right either a colored state and a button, or a short
 * text and a chevron (then the whole row is the button).
 */
public class ActionRowControl extends Control {
	private static final int HEIGHT = 40;
	private static final int PAD = 10;
	private static final int ICON_TILE = 20;
	private static final int ICON = 13;
	private static final int BUTTON_HEIGHT = 22;
	private static final int BUTTON_PAD = 10;
	private static final int DOT = 3;
	private static final int CHEVRON = 8;
	/** A second line of the text under the title, which wraps instead of being cut. */
	private static final int SUB_LINE = 10;
	private static final int MOST_SUB_LINES = 2;

	private final ActionRowSetting setting;
	/** Where the button was drawn, for clicks. */
	private int buttonX;
	private int buttonWidth;
	/** How many lines the text under the title took when last drawn: the row grows for the second one. */
	private int subLines = 1;

	public ActionRowControl(ActionRowSetting setting) {
		this.setting = setting;
	}

	@Override
	public boolean isBlock() {
		return true;
	}

	@Override
	public boolean hasLabel() {
		return false;
	}

	@Override
	public boolean bare() {
		return true;
	}

	@Override
	protected int measure(Ui ui, int width) {
		return HEIGHT + (subLines - 1) * SUB_LINE;
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		ActionRowSetting.Look look = setting.look();
		String label = look.button() == null ? null : look.button().get();
		boolean whole = label == null;
		boolean over = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
		float hover = hover(ui, 0, whole && over);
		ui.rounded(x, y, width, height, Radius.LG, palette.card());
		if (hover > 0f) {
			ui.rounded(x, y, width, height, Radius.LG, Ui.withOpacity(palette.hover(), hover));
		}
		ui.ring(x, y, width, height, Radius.LG, 1, palette.cardBorder());
		int middle = y + height / 2;
		int textX = x + PAD;
		String icon = look.icon() == null ? null : look.icon().get();
		if (icon != null) {
			ui.iconTile(icon, textX, middle - ICON_TILE / 2, ICON_TILE, ICON, Radius.SM, 0xFF000000 | look.iconRgb().getAsInt(), 0xFFFFFFFF);
			textX += ICON_TILE + 9;
		}
		int right = x + width - PAD;
		if (whole) {
			var pose = ui.graphics.pose();
			pose.pushMatrix();
			pose.translate(right - CHEVRON / 2f, middle);
			pose.rotate((float) (-Math.PI / 2.0));
			ui.icon("chevron", -CHEVRON / 2, -CHEVRON / 2, CHEVRON, Ui.withOpacity(palette.textSecondary(), 0.8f));
			pose.popMatrix();
			right -= CHEVRON + 8;
			String text = look.right() == null ? null : look.right().get();
			if (text != null) {
				float w = ui.widthScaled(text, TextScale.SMALL);
				ui.textAtScaled(text, right - w, middle - 4f, TextScale.SMALL, palette.textSecondary());
				right -= Math.round(w) + 10;
			}
			buttonWidth = 0;
		} else {
			buttonWidth = Math.round(ui.widthScaled(label, TextScale.BODY)) + 2 * BUTTON_PAD;
			buttonX = right - buttonWidth;
			boolean onButton = over && mouseX >= buttonX;
			float press = hover(ui, 1, onButton);
			int buttonY = middle - BUTTON_HEIGHT / 2;
			ui.rounded(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT, Radius.SM, Ui.mix(palette.control(), palette.hover(), press));
			ui.ring(buttonX, buttonY, buttonWidth, BUTTON_HEIGHT, Radius.SM, 1, Ui.withOpacity(palette.text(), 0.14f));
			ui.textAtScaled(label, buttonX + BUTTON_PAD, middle - 4f, TextScale.BODY, palette.text());
			right = buttonX - 8;
			String beside = look.right() == null ? null : look.right().get();
			if (beside != null) {
				int chip = ui.chipWidth(beside);
				ui.chip(beside, right - chip, middle);
				right -= chip + 8;
			}
			EnumSetting.OptionState state = look.state() == null ? null : look.state().get();
			if (state != null && !state.text().isEmpty()) {
				int color = state.rgb() < 0 ? palette.textSecondary() : Ui.mix(palette.text(), 0xFF000000 | state.rgb(), 0.75f);
				String text = ui.fitScaled(state.text(), Math.max(40, (right - textX) / 2f), TextScale.SMALL);
				float w = ui.widthScaled(text, TextScale.SMALL);
				ui.textAtScaled(text, right - w, middle - 4f, TextScale.SMALL, color);
				if (state.rgb() >= 0) {
					ui.circle(Math.round(right - w - 7), middle, DOT, 0xFF000000 | state.rgb());
				}
				right -= Math.round(w) + 16;
			}
		}
		String sub = look.subtext() == null ? null : look.subtext().get();
		int room = right - textX;
		if (sub == null || sub.isEmpty()) {
			subLines = 1;
			ui.textAtScaledBold(ui.fit(look.title().get(), room), textX, middle - 4.5f, 1f, palette.text());
		} else {
			List<String> lines = ui.wrapScaled(sub, room, TextScale.SMALL, MOST_SUB_LINES);
			subLines = Math.max(1, lines.size());
			float top = middle - 10f - (subLines - 1) * SUB_LINE / 2f;
			ui.textAtScaledBold(ui.fit(look.title().get(), room), textX, top, 1f, palette.text());
			for (int i = 0; i < lines.size(); i++) {
				ui.textAtScaled(lines.get(i), textX, top + 13f + i * SUB_LINE, TextScale.SMALL, palette.textSecondary());
			}
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || mouseY < y || mouseY >= y + height || mouseX < x || mouseX >= x + width) {
			return false;
		}
		if (setting.look().button() != null && (mouseX < buttonX || mouseX >= buttonX + buttonWidth)) {
			return false;
		}
		UiSounds.click();
		setting.run();
		return true;
	}
}
