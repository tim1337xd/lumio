package dev.tim1337.lumio.ui.title;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.render.RoundedImage;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiGlow;
import dev.tim1337.lumio.ui.UiSounds;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * A list in the main menu's left column, in the glass style of its buttons: Back, a header, a field
 * with a main button, a card of rows that scrolls inside itself, and the rows' "⋯" menus. The rows and their actions come from the server or
 * world list. The parts come in one after another. Sizes are in the main menu's layout units, the pixels of its mockup.
 */
public abstract class TitleListView {
	static final float LEFT = 96f;
	static final float TOP = 132f;
	static final float WIDTH = 460f;
	static final float GAP = 16f;
	static final float ROW = 66f;
	static final float CARD_PAD = 6f;
	static final float TEXT = 1f / 9.6f;
	private static final float BACK_WIDTH = 78f;
	private static final float BACK_HEIGHT = 32f;
	private static final float HEADER = 104f;
	private static final float FIELD_CARD = 54f;
	private static final float SECTION = 14f;
	/** The lowest the card may reach, above the footer. */
	private static final float BOTTOM_ROOM = 92f;
	private static final float MORE = 30f;
	private static final float MENU_WIDTH = 210f;
	private static final float MENU_ITEM = 32f;
	private static final float SCROLL_STEP = 50f;
	private static final float DRAG_START = 5f;
	private static final float ENTRANCE_SECONDS = 0.4f;
	private static final float STAGGER_SECONDS = 0.04f;
	private static final float SLIDE = 20f;
	private static final long DOUBLE_CLICK_MILLIS = 350L;

	final TitleField field;
	private final Spring scroll = Spring.smooth(Spring.SNAPPY);
	private final Spring highlight = Spring.smooth(Spring.SNAPPY);
	private final Spring highlightAmount = Spring.smooth(Spring.SNAPPY);
	private final Spring menuOpen = Spring.smooth(Spring.SNAPPY);
	private float scrollTarget;
	/** The filtered rows: positions in the full list. */
	List<Integer> visible = new ArrayList<>();
	int selected = -1;
	private int hovered = -1;
	private int menuFor = -1;
	private float menuX;
	private float menuY;
	private long openedAt;
	private long lastClick;
	private int lastClickRow = -1;
	// dragging a row to a new place
	private int pressedRow = -1;
	private double pressY;
	private boolean dragging;
	private float dragOffset;
	// the main button and the card as last drawn
	private float buttonX;
	private float buttonWidth;
	private float cardTop;
	private float cardHeight;
	private float contentHeight;

	TitleListView(String placeholder) {
		field = new TitleField(placeholder);
	}

	// --------------------------------------------------------------------------------------------- what the lists provide

	abstract String overline();

	abstract String heading();

	abstract String subtitle();

	abstract String primaryLabel();

	/** The main button (Join, New world), and Enter in the field. */
	abstract void primary();

	abstract String sectionLabel();

	abstract String sectionNote();

	/** Draws one row (a position in the full list) in its place; hot when hovered or selected. */
	abstract void drawRow(Ui ui, int index, float x, float y, float width, boolean hot, float alpha, int mouseX, int mouseY);

	/** The row was double-clicked or Enter was pressed: play the world or join the server. */
	abstract void activate(int index);

	/** @return the entries of a row's "⋯" menu; the last one is Delete… and shown in red */
	abstract List<String> menu(int index);

	abstract void menuAction(int index, int item);

	/** Filters the rows again after the field's text or the data changed. */
	abstract void refilter();

	/** Space below the field for the list's own parts (the form to save a server), and drawing it. */
	float extraHeight() {
		return 0f;
	}

	void drawExtra(Ui ui, float x, float y, float alpha, int mouseX, int mouseY) {
	}

	boolean clickExtra(double mouseX, double mouseY, float y) {
		return false;
	}

	/** Space and drawing below the card (the LAN section). */
	float afterHeight() {
		return 0f;
	}

	void drawAfter(Ui ui, float x, float y, float alpha) {
	}

	/** A click on a part of a hovered or selected row (the world's Play pill). @return whether it was taken */
	boolean clickInRow(int index, double mouseX, double mouseY, float x, float y, float width) {
		return false;
	}

	boolean clickAfter(double mouseX, double mouseY, float top) {
		return false;
	}

	/** Draws a texture of the game (an icon) with rounded corners. */
	static void roundedTexture(Ui ui, Identifier texture, float x, float y, float width, float height, float radius, int color) {
		var loaded = Minecraft.getInstance().getTextureManager().getTexture(texture);
		RoundedImage.draw(ui.graphics, loaded.getTextureView(), Math.round(x), Math.round(y), Math.round(width), Math.round(height), radius, color);
	}

	/** @return whether rows can be dragged to a new place */
	boolean reorderable() {
		return false;
	}

	void move(int from, int to) {
	}

	/** @return placeholder rows drawn as a shimmer while the list is loading, 0 when loaded */
	int loadingRows() {
		return 0;
	}

	public void tick() {
	}

	/** Called when the list is shown. */
	public void open() {
		openedAt = System.nanoTime();
		selected = -1;
		menuFor = -1;
		scroll.snap(0f);
		scrollTarget = 0f;
		refilter();
	}

	/**
	 * Called when the list goes away or the game opens a screen over the menu (an edit screen, a delete question). The rows stay, because the
	 * menu is still drawn behind such a screen.
	 */
	public void close() {
		field.focus(false);
		menuFor = -1;
	}

	/** Called when the list is gone for good: frees what its rows hold (icons). */
	public void dispose() {
	}

	// ------------------------------------------------------------------------------------------------------------- drawing

	/** @return how far a part has come in, from 0 to 1, by its place in the order */
	float appear(int order) {
		float t = (System.nanoTime() - openedAt) / 1_000_000_000f - order * STAGGER_SECONDS;
		float p = Mth.clamp(t / ENTRANCE_SECONDS, 0f, 1f);
		return 1f - (1f - p) * (1f - p) * (1f - p);
	}

	float fieldTop() {
		return TOP + BACK_HEIGHT + GAP + HEADER + GAP;
	}

	private float sectionTop() {
		return fieldTop() + FIELD_CARD + GAP + extraHeight();
	}

	/** Draws the list; shown fades the whole of it with the column's switch. */
	public void draw(Ui ui, float layoutHeight, int mouseX, int mouseY, float shown) {
		var pose = ui.graphics.pose();
		// Back
		float in = appear(0) * shown;
		pose.pushMatrix();
		pose.translate((1f - in) * -SLIDE, 0f);
		boolean overBack = mouseX >= LEFT && mouseX < LEFT + BACK_WIDTH && mouseY >= TOP && mouseY < TOP + BACK_HEIGHT;
		HudStyle.panel(ui, Math.round(LEFT), Math.round(TOP), Math.round(BACK_WIDTH), Math.round(BACK_HEIGHT), BACK_HEIGHT / 2f, HudStyle.opacity() * in,
			UiGlow.Target.TITLE, in);
		if (overBack) {
			ui.roundedSmooth(LEFT, TOP, BACK_WIDTH, BACK_HEIGHT, BACK_HEIGHT / 2f, Ui.withOpacity(0xFFFFFFFF, 0.10f * in));
		}
		pose.pushMatrix();
		pose.translate(LEFT + 16f, TOP + BACK_HEIGHT / 2f);
		pose.rotate((float) (Math.PI / 2.0));
		ui.icon("chevron", -8, -8, 16, Ui.withOpacity(0xFFFFFFFF, in));
		pose.popMatrix();
		ui.textAtScaled("Back", LEFT + 28f, TOP + BACK_HEIGHT / 2f - 4.5f * 13.5f * TEXT, 13.5f * TEXT, Ui.withOpacity(0xFFFFFFFF, in));
		pose.popMatrix();

		// the header
		in = appear(1) * shown;
		pose.pushMatrix();
		pose.translate((1f - in) * -SLIDE, 0f);
		float headerTop = TOP + BACK_HEIGHT + GAP;
		ui.textAtScaledBold(overline(), LEFT, headerTop, 12.5f * TEXT, Ui.withOpacity(0xFFFFFFFF, 0.72f * in));
		ui.textAtScaledTitle(heading(), LEFT, headerTop + 24f, 42f * TEXT, Ui.withOpacity(0xFFFFFFFF, in));
		ui.textAtScaled(subtitle(), LEFT, headerTop + 78f, 14f * TEXT, Ui.withOpacity(0xFFFFFFFF, 0.7f * in));
		pose.popMatrix();

		// the field and the main button
		in = appear(2) * shown;
		pose.pushMatrix();
		pose.translate((1f - in) * -SLIDE, 0f);
		float top = fieldTop();
		HudStyle.panel(ui, Math.round(LEFT), Math.round(top), Math.round(WIDTH), Math.round(FIELD_CARD), 18f, HudStyle.opacity() * in, UiGlow.Target.TITLE, in);
		float buttonScale = 14.5f * TEXT;
		buttonWidth = ui.widthScaled(primaryLabel(), buttonScale) + 32f;
		buttonX = LEFT + WIDTH - CARD_PAD - buttonWidth;
		field.draw(ui, LEFT + CARD_PAD, top + CARD_PAD, buttonX - 8f - LEFT - CARD_PAD, FIELD_CARD - 2 * CARD_PAD, in);
		boolean overButton = mouseX >= buttonX && mouseX < buttonX + buttonWidth && mouseY >= top + CARD_PAD && mouseY < top + FIELD_CARD - CARD_PAD;
		ui.roundedSmooth(buttonX, top + CARD_PAD, buttonWidth, FIELD_CARD - 2 * CARD_PAD, 12f, Ui.withOpacity(Ui.mix(ui.accent, 0xFFFFFFFF, overButton ? 0.12f : 0f), in));
		ui.textAtScaledBold(primaryLabel(), buttonX + 16f, top + FIELD_CARD / 2f - 4.5f * buttonScale, buttonScale, Ui.withOpacity(0xFFFFFFFF, in));
		drawExtra(ui, LEFT, top + FIELD_CARD + GAP, in, mouseX, mouseY);
		pose.popMatrix();

		// the section and its card
		in = appear(3) * shown;
		float section = sectionTop();
		pose.pushMatrix();
		pose.translate((1f - in) * -SLIDE, 0f);
		ui.textAtScaledBold(sectionLabel(), LEFT + 6f, section, 12f * TEXT, Ui.withOpacity(0xFFFFFFFF, 0.66f * in));
		String note = sectionNote();
		float noteScale = 12f * TEXT;
		ui.textAtScaled(note, LEFT + WIDTH - 6f - ui.widthScaled(note, noteScale), section, noteScale, Ui.withOpacity(0xFFFFFFFF, 0.66f * in));
		pose.popMatrix();
		drawCard(ui, layoutHeight, section + SECTION + GAP * 0.6f, mouseX, mouseY, shown);
		in = appear(5) * shown;
		pose.pushMatrix();
		pose.translate((1f - in) * -SLIDE, 0f);
		drawAfter(ui, LEFT, cardTop + cardHeight + GAP, in);
		pose.popMatrix();
	}

	private void drawCard(Ui ui, float layoutHeight, float top, int mouseX, int mouseY, float shown) {
		int loading = loadingRows();
		int rows = loading > 0 ? loading : visible.size();
		contentHeight = Math.max(1, rows) * ROW;
		float room = Math.max(ROW, layoutHeight - BOTTOM_ROOM - afterHeight() - top - 2 * CARD_PAD);
		cardTop = top;
		cardHeight = Math.min(contentHeight, room) + 2 * CARD_PAD;
		float maxScroll = Math.max(0f, contentHeight - (cardHeight - 2 * CARD_PAD));
		scrollTarget = Mth.clamp(scrollTarget, 0f, maxScroll);
		float offset = scroll.follow(ui, scrollTarget);
		float in = appear(4) * shown;
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate((1f - in) * -SLIDE, 0f);
		HudStyle.panel(ui, Math.round(LEFT), Math.round(cardTop), Math.round(WIDTH), Math.round(cardHeight), 22f, HudStyle.opacity() * in, UiGlow.Target.TITLE, in);
		float inner = cardTop + CARD_PAD;
		float innerHeight = cardHeight - 2 * CARD_PAD;
		ui.graphics.enableScissor(Math.round(LEFT), Math.round(inner), Math.round(LEFT + WIDTH), Math.round(inner + innerHeight));
		float rowWidth = WIDTH - 2 * CARD_PAD;
		if (loading > 0) {
			// shimmer placeholders while the worlds are read in the background
			float phase = (System.nanoTime() / 1_000_000_000f) % 1.6f / 1.6f;
			for (int i = 0; i < loading; i++) {
				float y = inner + i * ROW;
				float light = 0.05f + 0.05f * (float) Math.max(0.0, Math.sin((phase - i * 0.12f) * Math.PI * 2.0));
				ui.roundedSmooth(LEFT + CARD_PAD + 10f, y + 12f, 60f, 42f, 9f, Ui.withOpacity(0xFFFFFFFF, light * in));
				ui.roundedSmooth(LEFT + CARD_PAD + 84f, y + 18f, 150f, 12f, 6f, Ui.withOpacity(0xFFFFFFFF, light * in));
				ui.roundedSmooth(LEFT + CARD_PAD + 84f, y + 38f, 220f, 10f, 5f, Ui.withOpacity(0xFFFFFFFF, light * 0.8f * in));
			}
		} else if (visible.isEmpty()) {
			ui.textAtScaled(field.text().isBlank() ? "Nothing here yet" : "Nothing found", LEFT + CARD_PAD + 14f, inner + ROW / 2f - 6f, 14f * TEXT,
				Ui.withOpacity(0xFFFFFFFF, 0.55f * in));
		} else {
			boolean inside = mouseX >= LEFT && mouseX < LEFT + WIDTH && mouseY >= inner && mouseY < inner + innerHeight;
			hovered = inside && !dragging ? (int) ((mouseY - inner + offset) / ROW) : -1;
			if (hovered >= visible.size()) {
				hovered = -1;
			}
			int lit = dragging ? pressedRow : hovered >= 0 ? hovered : selected;
			float amount = Mth.clamp(highlightAmount.follow(ui, lit >= 0 ? 1f : 0f), 0f, 1f);
			if (lit >= 0) {
				if (amount < 0.05f) {
					highlight.snap(lit * ROW);
				}
				float y = inner - offset + highlight.follow(ui, lit * ROW) + (dragging ? dragOffset : 0f);
				ui.roundedSmooth(LEFT + CARD_PAD, y, rowWidth, ROW, 15f, Ui.withOpacity(0xFFFFFFFF, 0.12f * amount * in));
			}
			for (int i = 0; i < visible.size(); i++) {
				float y = inner - offset + i * ROW;
				if (dragging && i == pressedRow) {
					y += dragOffset;
				} else if (dragging) {
					y += shiftForDrag(i);
				}
				if (y + ROW < inner || y > inner + innerHeight) {
					continue;
				}
				float rowIn = appear(4 + i) * shown;
				boolean hot = i == hovered || i == selected;
				drawRow(ui, visible.get(i), LEFT + CARD_PAD, y, rowWidth - (hot ? MORE + 6f : 0f), hot, rowIn, mouseX, mouseY);
				if (hot) {
					drawMore(ui, LEFT + CARD_PAD + rowWidth - MORE - 6f, y + (ROW - MORE) / 2f, mouseX, mouseY, rowIn);
				}
			}
		}
		ui.graphics.disableScissor();
		// a thin scrollbar when the rows do not fit
		if (maxScroll > 0f) {
			float track = innerHeight - 8f;
			float thumb = Math.max(24f, track * innerHeight / contentHeight);
			float thumbY = inner + 4f + (track - thumb) * (offset / maxScroll);
			ui.roundedSmooth(LEFT + WIDTH - 5f, thumbY, 3f, thumb, 1.5f, Ui.withOpacity(0xFFFFFFFF, 0.35f * in));
		}
		pose.popMatrix();
	}

	/** @return how far a row moves to make room for the dragged one */
	private float shiftForDrag(int index) {
		int target = dragTarget();
		if (pressedRow < target && index > pressedRow && index <= target) {
			return -ROW;
		}
		if (pressedRow > target && index < pressedRow && index >= target) {
			return ROW;
		}
		return 0f;
	}

	private int dragTarget() {
		return Mth.clamp(Math.round(pressedRow + dragOffset / ROW), 0, visible.size() - 1);
	}

	private void drawMore(Ui ui, float x, float y, int mouseX, int mouseY, float alpha) {
		boolean over = mouseX >= x && mouseX < x + MORE && mouseY >= y && mouseY < y + MORE;
		ui.roundedSmooth(x, y, MORE, MORE, MORE / 2f, Ui.withOpacity(0xFFFFFFFF, (over ? 0.18f : 0.08f) * alpha));
		for (int i = -1; i <= 1; i++) {
			ui.roundedSmooth(x + MORE / 2f + i * 6f - 1.5f, y + MORE / 2f - 1.5f, 3f, 3f, 1.5f, Ui.withOpacity(0xFFFFFFFF, alpha));
		}
	}

	/** Draws the open "⋯" menu over everything. */
	public void drawMenu(Ui ui, int mouseX, int mouseY) {
		float open = Mth.clamp(menuOpen.follow(ui, menuFor >= 0 ? 1f : 0f), 0f, 1f);
		if (open <= 0.01f || lastMenu == null) {
			return;
		}
		float height = lastMenu.size() * MENU_ITEM + 12f + 9f;
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate(menuX, menuY);
		pose.scale(0.94f + 0.06f * open, 0.9f + 0.1f * open);
		pose.translate(-menuX, -menuY);
		ui.shadow(Math.round(menuX), Math.round(menuY), Math.round(MENU_WIDTH), Math.round(height), 14f, 12, 0.5f * open);
		ui.roundedSmooth(menuX, menuY, MENU_WIDTH, height, 14f, Ui.withOpacity(0xFF121624, 0.94f * open));
		ui.ring(Math.round(menuX), Math.round(menuY), Math.round(MENU_WIDTH), Math.round(height), 14f, 1, Ui.withOpacity(0xFFFFFFFF, 0.16f * open));
		float y = menuY + 6f;
		for (int i = 0; i < lastMenu.size(); i++) {
			boolean delete = i == lastMenu.size() - 1;
			if (delete) {
				ui.fill(Math.round(menuX + 12f), Math.round(y + 4f), Math.round(MENU_WIDTH - 24f), 1, Ui.withOpacity(0xFFFFFFFF, 0.12f * open));
				y += 9f;
			}
			boolean over = mouseX >= menuX + 6f && mouseX < menuX + MENU_WIDTH - 6f && mouseY >= y && mouseY < y + MENU_ITEM;
			if (over) {
				ui.roundedSmooth(menuX + 6f, y, MENU_WIDTH - 12f, MENU_ITEM, 8f, Ui.withOpacity(0xFFFFFFFF, 0.10f * open));
			}
			ui.textAtScaled(lastMenu.get(i), menuX + 16f, y + MENU_ITEM / 2f - 4.5f * 14f * TEXT, 14f * TEXT,
				Ui.withOpacity(delete ? 0xFFFF9F94 : 0xFFFFFFFF, open));
			y += MENU_ITEM;
		}
		pose.popMatrix();
	}

	private List<String> lastMenu;

	// --------------------------------------------------------------------------------------------------------------- input

	/** @return whether the click was taken */
	public boolean click(double mouseX, double mouseY, int button, Runnable back) {
		if (menuFor >= 0) {
			int item = menuItemAt(mouseX, mouseY);
			int row = menuFor;
			menuFor = -1;
			if (item >= 0) {
				UiSounds.click();
				menuAction(visible.get(row), item);
			}
			return true;
		}
		if (button != 0) {
			return false;
		}
		if (mouseX >= LEFT && mouseX < LEFT + BACK_WIDTH && mouseY >= TOP && mouseY < TOP + BACK_HEIGHT) {
			back.run();
			return true;
		}
		float top = fieldTop();
		if (mouseY >= top && mouseY < top + FIELD_CARD && mouseX >= LEFT && mouseX < LEFT + WIDTH) {
			if (mouseX >= buttonX && mouseX < buttonX + buttonWidth) {
				UiSounds.click();
				primary();
			} else {
				field.focus(true);
			}
			return true;
		}
		field.focus(false);
		if (clickExtra(mouseX, mouseY, top + FIELD_CARD + GAP)) {
			return true;
		}
		if (clickAfter(mouseX, mouseY, cardTop + cardHeight + GAP)) {
			return true;
		}
		int row = rowAt(mouseX, mouseY);
		if (row >= 0) {
			float rowWidth = WIDTH - 2 * CARD_PAD;
			float rowY = cardTop + CARD_PAD - scrollTarget + row * ROW;
			float moreX = LEFT + CARD_PAD + rowWidth - MORE - 6f;
			if (mouseX >= moreX && mouseX < moreX + MORE && (row == hovered || row == selected)) {
				openMenu(row, moreX + MORE + 8f, rowY + 6f);
				return true;
			}
			if ((row == hovered || row == selected) && clickInRow(visible.get(row), mouseX, mouseY, LEFT + CARD_PAD, rowY, rowWidth - MORE - 6f)) {
				return true;
			}
			long now = System.currentTimeMillis();
			boolean doubled = row == lastClickRow && now - lastClick < DOUBLE_CLICK_MILLIS;
			lastClick = now;
			lastClickRow = row;
			selected = row;
			pressedRow = row;
			pressY = mouseY;
			dragOffset = 0f;
			if (doubled) {
				UiSounds.click();
				activate(visible.get(row));
			}
			return true;
		}
		return false;
	}

	private void openMenu(int row, float x, float y) {
		menuFor = row;
		lastMenu = menu(visible.get(row));
		menuX = x;
		menuY = y;
		UiSounds.click();
	}

	private int menuItemAt(double mouseX, double mouseY) {
		if (lastMenu == null || mouseX < menuX || mouseX >= menuX + MENU_WIDTH) {
			return -1;
		}
		float y = menuY + 6f;
		for (int i = 0; i < lastMenu.size(); i++) {
			if (i == lastMenu.size() - 1) {
				y += 9f;
			}
			if (mouseY >= y && mouseY < y + MENU_ITEM) {
				return i;
			}
			y += MENU_ITEM;
		}
		return -1;
	}

	private int rowAt(double mouseX, double mouseY) {
		float inner = cardTop + CARD_PAD;
		if (mouseX < LEFT || mouseX >= LEFT + WIDTH || mouseY < inner || mouseY >= inner + cardHeight - 2 * CARD_PAD) {
			return -1;
		}
		int row = (int) ((mouseY - inner + scrollTarget) / ROW);
		return row >= 0 && row < visible.size() ? row : -1;
	}

	public boolean drag(double mouseY) {
		if (pressedRow < 0 || !reorderable() || !field.text().isBlank()) {
			return false;
		}
		if (!dragging && Math.abs(mouseY - pressY) > DRAG_START) {
			dragging = true;
		}
		if (dragging) {
			dragOffset = (float) (mouseY - pressY);
		}
		return dragging;
	}

	public void release() {
		if (dragging) {
			int target = dragTarget();
			if (target != pressedRow) {
				move(visible.get(pressedRow), visible.get(target));
				selected = target;
				refilter();
			}
		}
		dragging = false;
		pressedRow = -1;
	}

	public boolean scroll(double amount) {
		scrollTarget -= (float) amount * SCROLL_STEP;
		return true;
	}

	/** @return whether the key was taken; Escape goes back unless a menu or the field takes it */
	public boolean key(int key, int modifiers, Runnable back) {
		boolean control = (modifiers & ScreenInput.CONTROL) != 0;
		if (key == InputConstants.KEY_ESCAPE) {
			if (menuFor >= 0) {
				menuFor = -1;
			} else if (field.focused() && !field.text().isEmpty()) {
				field.setText("");
				refilter();
			} else {
				back.run();
			}
			return true;
		}
		if (control && ScreenInput.letter(key, 'F')) {
			field.focus(true);
			return true;
		}
		if (key == InputConstants.KEY_DOWN || key == InputConstants.KEY_UP) {
			if (!visible.isEmpty()) {
				selected = Mth.clamp(selected + (key == InputConstants.KEY_DOWN ? 1 : -1), 0, visible.size() - 1);
				revealSelected();
			}
			return true;
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			if (field.focused() || selected < 0) {
				primary();
			} else {
				activate(visible.get(selected));
			}
			return true;
		}
		if (key == InputConstants.KEY_DELETE && !field.focused() && selected >= 0) {
			List<String> items = menu(visible.get(selected));
			menuAction(visible.get(selected), items.size() - 1);
			return true;
		}
		int version = field.version();
		boolean taken = field.keyPressed(key, control);
		if (field.version() != version) {
			refilter();
		}
		return taken;
	}

	public boolean charTyped(int codepoint) {
		if (!field.focused()) {
			// typing anywhere starts a search
			if (Character.isLetterOrDigit(codepoint)) {
				field.focus(true);
			} else {
				return false;
			}
		}
		boolean taken = field.charTyped(codepoint);
		if (taken) {
			refilter();
		}
		return taken;
	}

	/** Scrolls so the selected row is in view. */
	private void revealSelected() {
		float innerHeight = cardHeight - 2 * CARD_PAD;
		float rowTop = selected * ROW;
		if (rowTop < scrollTarget) {
			scrollTarget = rowTop;
		} else if (rowTop + ROW > scrollTarget + innerHeight) {
			scrollTarget = rowTop + ROW - innerHeight;
		}
	}

	/** @return the selected row's position in the full list, or -1 */
	int selectedIndex() {
		return selected >= 0 && selected < visible.size() ? visible.get(selected) : -1;
	}
}
