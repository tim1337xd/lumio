package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.draw.TextSelection;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointColors;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointCards;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The small card beside the crosshair that names a new waypoint: its name (filled in as "Waypoint N"), its color and its group (both as last time),
 * where it is and the area its group shows in. Enter keeps it, Escape goes back; Ctrl+V takes coordinates from the clipboard. The world stays in
 * view and the game goes on.
 */
public final class WaypointPlaceScreen extends Screen {
	private static final int WIDTH = 200;
	private static final int HEIGHT = 86;
	private static final int PAD = 9;
	private static final int FIELD_HEIGHT = 20;
	private static final int DOT = 10;
	private static final int DOT_STEP = 15;
	private static final int OFFSET_X = 28;
	private static final int OFFSET_Y = 46;
	private static final int MAX_NAME = Waypoint.MAX_NAME;
	private static final int CARET_BLINK_MILLIS = 530;

	private final WaypointsFeature feature;
	private final TextSelection selection = new TextSelection();
	private final WaypointMenu menu = new WaypointMenu();
	private int x;
	private int y;
	private int z;
	private String name;
	private int colorIndex;
	private WaypointGroup group;
	private int cardX;
	private int cardY;
	private int groupX;
	private int groupWidth;
	private int dotsY;
	private long lastFrame;
	/** For an add-on's own point (see WaypointCards): its heading, and where the result goes instead of a group; null for the player's own. */
	private String title = "NEW WAYPOINT";
	private Consumer<WaypointCards.Placed> done;

	/** A card for an add-on's own point: no group, the result goes back to the add-on. */
	public WaypointPlaceScreen(WaypointsFeature feature, int x, int y, int z, String title, String name, Consumer<WaypointCards.Placed> done) {
		this(feature, x, y, z);
		this.title = title;
		this.name = name;
		this.done = done;
	}

	public WaypointPlaceScreen(WaypointsFeature feature, int x, int y, int z) {
		super(Component.literal("New waypoint"));
		this.feature = feature;
		this.x = x;
		this.y = y;
		this.z = z;
		this.group = feature.lastGroup();
		this.colorIndex = feature.lastColorIndex(group);
		this.name = "Waypoint " + ((group == null ? 0 : group.points().size()) + 1);
		selection.selectAll();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		// the world stays as it is behind the card
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		long now = System.nanoTime();
		float delta = lastFrame == 0 ? 0f : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		Ui ui = new Ui(graphics, font, HudStyle.palette(), ModFeatures.MENU.accentArgb(), delta, true);
		cardX = Math.min(width / 2 + OFFSET_X, width - WIDTH - 4);
		cardY = Math.max(4, Math.min(height / 2 - OFFSET_Y, height - HEIGHT - 4));
		boolean menuOpen = menu.open();
		int hoverX = menuOpen ? -1 : mouseX;
		int hoverY = menuOpen ? -1 : mouseY;
		ui.shadow(cardX, cardY, WIDTH, HEIGHT, Radius.LG, 10, 0.45f);
		ui.rounded(cardX, cardY, WIDTH, HEIGHT, Radius.LG, Ui.withOpacity(0xFF141826, 0.94f));
		ui.ring(cardX, cardY, WIDTH, HEIGHT, Radius.LG, 1, 0x26FFFFFF);
		int left = cardX + PAD;
		int right = cardX + WIDTH - PAD;
		ui.sectionLabel(title, left, cardY + 8, 0xB3FFFFFF);
		String coords = x + ", " + y + ", " + z;
		ui.textAtScaled(coords, right - ui.widthScaled(coords, TextScale.SMALL), cardY + 8, TextScale.SMALL, 0x99FFFFFF);
		// the name
		int fieldY = cardY + 20;
		ui.rounded(left, fieldY, WIDTH - 2 * PAD, FIELD_HEIGHT, Radius.SM, 0x33000000);
		ui.ring(left, fieldY, WIDTH - 2 * PAD, FIELD_HEIGHT, Radius.SM, 1, ui.accent);
		float hintWidth = ui.widthScaled("name", TextScale.SMALL);
		String shown = ui.fit(name, WIDTH - 2 * PAD - 16 - Math.round(hintWidth));
		int middle = fieldY + FIELD_HEIGHT / 2;
		selection.draw(ui, left + 6, middle, Math.round(ui.boldDrawnWidthScaled(shown, TextScale.BODY, 0xFFFFFFFF)));
		ui.textAtScaledBold(shown, left + 6, middle - 4f, TextScale.BODY, 0xFFFFFFFF);
		if (!selection.all() && System.currentTimeMillis() / CARET_BLINK_MILLIS % 2 == 0) {
			ui.fill(left + 7 + Math.round(ui.boldWidthScaled(shown, TextScale.BODY)), middle - 5, 1, 10, 0xFFFFFFFF);
		}
		ui.textAtScaled("name", right - 6 - hintWidth, middle - 3.5f, TextScale.SMALL, 0x66FFFFFF);
		// the colors and the group
		dotsY = fieldY + FIELD_HEIGHT + 8;
		for (int i = 0; i < WaypointColors.count(); i++) {
			float centerX = left + DOT / 2f + i * DOT_STEP;
			float centerY = dotsY + DOT / 2f;
			if (i == colorIndex) {
				ui.ringAt(centerX, centerY, DOT + 5, 1.2f, 0xFFFFFFFF);
			}
			ui.discAt(centerX, centerY, DOT, 0xFF000000 | WaypointColors.get(i));
		}
		if (done == null) {
			drawGroup(ui, right, hoverX, hoverY);
		}
		drawKeys(ui, left, right);
		menu.render(ui, mouseX, mouseY, width, height);
	}

	private void drawGroup(Ui ui, int right, int hoverX, int hoverY) {
		String groupName = group == null ? "New group" : group.name();
		groupWidth = Math.min(90, ui.width(groupName) + 22);
		groupX = right - groupWidth;
		boolean overGroup = hoverX >= groupX && hoverX < right && hoverY >= dotsY - 3 && hoverY < dotsY + DOT + 3;
		ui.rounded(groupX, dotsY - 3, groupWidth, DOT + 6, Radius.XS, overGroup ? 0x33FFFFFF : 0x1FFFFFFF);
		ui.textMiddle(ui.fit(groupName, groupWidth - 18), groupX + 6, dotsY + DOT / 2, 0xFFFFFFFF);
		ui.icon("chevron", right - 11, dotsY + 1, 7, 0x99FFFFFF);
	}

	private void drawKeys(Ui ui, int left, int right) {
		// the keys, and where it shows
		int keysY = cardY + HEIGHT - 15;
		int keyX = key(ui, "↵", left, keysY);
		ui.textAtScaled("Save", keyX + 4, keysY - 3.5f, TextScale.SMALL, 0x99FFFFFF);
		keyX = key(ui, "Esc", keyX + 10 + Math.round(ui.widthScaled("Save", TextScale.SMALL)), keysY);
		ui.textAtScaled("Cancel", keyX + 4, keysY - 3.5f, TextScale.SMALL, 0x99FFFFFF);
		keyX += 4 + Math.round(ui.widthScaled("Cancel", TextScale.SMALL));
		String area = done != null ? "" : "Area: " + (group == null ? WaypointAreas.nameOf(WaypointAreas.forNewGroup().getFirst()) : WaypointAreas.summary(group));
		String fitted = ui.fitScaled(area, right - keyX - 10, TextScale.SMALL);
		ui.textAtScaled(fitted, right - ui.widthScaled(fitted, TextScale.SMALL), keysY - 3.5f, TextScale.SMALL, 0x80FFFFFF);
	}

	/** A key in a small box; @return where the text after it begins */
	private static int key(Ui ui, String text, int left, int middle) {
		int w = Math.round(ui.boldWidthScaled(text, TextScale.SMALL)) + 8;
		ui.rounded(left, middle - 5, w, 10, Radius.XS, 0x26FFFFFF);
		ui.textAtScaledBold(text, left + 4, middle - 3.5f, TextScale.SMALL, 0xFFFFFFFF);
		return left + w;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		double mouseX = event.x();
		double mouseY = event.y();
		if (menu.click(mouseX, mouseY)) {
			return true;
		}
		if (ScreenInput.button(event) != ScreenInput.LEFT) {
			return true;
		}
		if (mouseY >= dotsY - 3 && mouseY < dotsY + DOT + 3) {
			for (int i = 0; i < WaypointColors.count(); i++) {
				int left = cardX + PAD + i * DOT_STEP;
				if (mouseX >= left - 2 && mouseX < left + DOT + 3) {
					colorIndex = i;
					UiSounds.tick((float) i / WaypointColors.count());
					return true;
				}
			}
			if (done == null && mouseX >= groupX && mouseX < groupX + groupWidth) {
				openGroups();
				return true;
			}
		}
		if (mouseX < cardX || mouseX >= cardX + WIDTH || mouseY < cardY || mouseY >= cardY + HEIGHT) {
			onClose();
		}
		return true;
	}

	private void openGroups() {
		List<WaypointMenu.Item> items = new ArrayList<>();
		for (WaypointGroup each : feature.store().groups()) {
			if (!each.readOnly()) {
				items.add(WaypointMenu.Item.dot(each.name(), each.rgb(), () -> {
					group = each;
					colorIndex = feature.lastColorIndex(each);
				}));
			}
		}
		items.add(WaypointMenu.Item.of("+ New group", () -> group = null));
		menu.open(groupX + groupWidth, dotsY + DOT + 5, items);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		int key = event.key();
		int modifiers = ScreenInput.modifiers(event.modifiers());
		if (key == InputConstants.KEY_ESCAPE) {
			if (menu.open()) {
				menu.close();
			} else {
				onClose();
			}
			return true;
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			save();
			return true;
		}
		if ((modifiers & ScreenInput.CONTROL) != 0 && ScreenInput.letter(key, 'V')) {
			String clipboard = minecraft.keyboardHandler.getClipboard();
			if (done == null && feature.pasted(clipboard)) {
				onClose();
				return true;
			}
			CoordParser.Found found = CoordParser.first(clipboard);
			if (found != null) {
				x = found.x();
				y = found.y();
				z = found.z();
				if (!found.name().isEmpty()) {
					name = found.name();
					selection.selectAll();
				}
				return true;
			}
		}
		if (selection.key(key, modifiers, () -> name, value -> name = value, MAX_NAME)) {
			return true;
		}
		if (key == InputConstants.KEY_BACKSPACE && !name.isEmpty()) {
			name = name.substring(0, name.length() - 1);
			return true;
		}
		return true;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (event.codepoint() >= 32) {
			String next = selection.typed(name, new String(Character.toChars(event.codepoint())));
			if (next.length() <= MAX_NAME) {
				name = next;
			}
		}
		return true;
	}

	private void save() {
		if (done != null) {
			done.accept(new WaypointCards.Placed(name.isBlank() ? "Waypoint" : name.strip(), WaypointColors.get(colorIndex)));
			UiSounds.click();
			onClose();
			return;
		}
		feature.placeFromCard(group, new Waypoint(x, y, z, name.isBlank() ? "Waypoint" : name), WaypointColors.get(colorIndex));
		UiSounds.click();
		onClose();
	}
}
