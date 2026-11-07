package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.config.setting.NavListSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import java.util.List;

/**
 * The rows of an overview in one card: per row an icon tile, the name, the value in gray with a status dot where it has one, and a chevron; the
 * whole row opens its sub-page. The values are read twice a second, not every frame. A line under the card says what the rows do.
 */
public class NavListControl extends Control {
	private static final int ROW = 34;
	private static final int PAD = 10;
	private static final int ICON_TILE = 20;
	private static final int ICON = 12;
	private static final int ICON_GAP = 10;
	private static final int CHEVRON = 8;
	private static final int DOT = 3;
	private static final int NOTE_GAP = 10;
	private static final int NOTE_LINE = 10;
	private static final int NOTE_LINES = 2;
	/** The values are read again this often while shown. */
	private static final long VALUE_REFRESH_MILLIS = 500L;

	private final NavListSetting setting;
	private final List<NavListSetting.Item> items;
	private final String[] values;
	private final int[] dots;
	private long readAt;
	/** The note under the card, wrapped when measured for its width. */
	private List<String> noteLines = List.of();
	private int noteWidth = -1;

	public NavListControl(NavListSetting setting) {
		this.setting = setting;
		this.items = setting.items();
		this.values = new String[items.size()];
		this.dots = new int[items.size()];
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
		if (setting.note() != null && width != noteWidth) {
			noteWidth = width;
			noteLines = ui.wrapScaled(setting.note(), width - 8, TextScale.SMALL, NOTE_LINES);
		}
		return items.size() * ROW + (noteLines.isEmpty() ? 0 : NOTE_GAP + noteLines.size() * NOTE_LINE);
	}

	private void readValues() {
		long now = System.currentTimeMillis();
		if (values[0] != null && now - readAt < VALUE_REFRESH_MILLIS) {
			return;
		}
		readAt = now;
		for (int i = 0; i < items.size(); i++) {
			NavListSetting.Item item = items.get(i);
			values[i] = item.value() == null ? "" : item.value().get();
			dots[i] = item.dot() == null ? -1 : item.dot().getAsInt();
		}
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		readValues();
		Palette palette = ui.palette;
		int cardHeight = items.size() * ROW;
		ui.rounded(x, y, width, cardHeight, Radius.LG, palette.card());
		for (int i = 0; i < items.size(); i++) {
			NavListSetting.Item item = items.get(i);
			int top = y + i * ROW;
			int middle = top + ROW / 2;
			boolean over = mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + ROW;
			float hover = hover(ui, i, over);
			if (hover > 0f) {
				// follows the card's rounded corners on the first and last row
				float radius = i == 0 || i == items.size() - 1 ? Radius.LG : 0f;
				ui.rounded(x, top, width, ROW, radius, Ui.withOpacity(palette.hover(), hover * 0.7f));
			}
			int textX = x + PAD + ICON_TILE + ICON_GAP;
			if (i > 0) {
				ui.fill(textX - 2, top, x + width - PAD - textX + 2, 1, Ui.withOpacity(palette.text(), palette.glass() ? 0.1f : 0.07f));
			}
			ui.iconTile(item.icon(), x + PAD, middle - ICON_TILE / 2, ICON_TILE, ICON, Radius.SM, 0xFF000000 | item.rgb(), 0xFFFFFFFF);
			int right = x + width - PAD;
			var pose = ui.graphics.pose();
			pose.pushMatrix();
			pose.translate(right - CHEVRON / 2f, middle);
			pose.rotate((float) (-Math.PI / 2.0));
			ui.icon("chevron", -CHEVRON / 2, -CHEVRON / 2, CHEVRON, Ui.withOpacity(palette.textSecondary(), 0.8f));
			pose.popMatrix();
			right -= CHEVRON + 10;
			int titleWidth = Math.round(ui.boldWidthScaled(item.title(), 1f));
			int room = Math.max(20, right - textX - titleWidth - 16);
			String value = ui.fitScaled(values[i], room, TextScale.SMALL);
			float valueWidth = ui.widthScaled(value, TextScale.SMALL);
			ui.textAtScaled(value, right - valueWidth, middle - 4f, TextScale.SMALL, palette.textSecondary());
			if (dots[i] >= 0) {
				ui.circle(Math.round(right - valueWidth - 8), middle, DOT, 0xFF000000 | dots[i]);
			}
			ui.textAtScaledBold(item.title(), textX, middle - 4.5f, 1f, palette.text());
		}
		ui.ring(x, y, width, cardHeight, Radius.LG, 1, palette.cardBorder());
		for (int k = 0; k < noteLines.size(); k++) {
			ui.textAtScaled(noteLines.get(k), x + 4, y + cardHeight + NOTE_GAP + k * NOTE_LINE, TextScale.SMALL, palette.textSecondary());
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0 || mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + items.size() * ROW) {
			return false;
		}
		UiSounds.click();
		items.get((int) ((mouseY - y) / ROW)).open().run();
		return true;
	}
}
