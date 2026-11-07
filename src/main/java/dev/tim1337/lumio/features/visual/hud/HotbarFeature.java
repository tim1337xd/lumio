package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.SettingsView;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.hud.HudPanelFeature;
import dev.tim1337.lumio.ui.EditableHud;
import dev.tim1337.lumio.ui.HudEditScreen;
import dev.tim1337.lumio.ui.HudParts;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.ui.Sweep;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.motion.Critical;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The hotbar on glass: nine slots with thin separators, the selected slot in an accent ring with a soft glow that glides from slot to slot, item
 * counts, durability lines and cooldowns that sweep around. Its look (Standard or Slim) is shared with the status bars stacked above it. The held
 * item name is drawn here too, above the stack.
 */
public class HotbarFeature extends HudPanelFeature {
	/** Item size; items are centered in their slots. */
	private static final int ITEM = 16;
	/** Standard look: slot size, the gap holding a separator, the padding around the slots and the corner radius. */
	private static final int SLOT = 22;
	private static final int SLOT_GAP = 3;
	private static final int PAD = 3;
	private static final float RADIUS = 8f;
	private static final float SLOT_RADIUS = 6f;
	/** Slim look: smaller slots, closer together, on a lighter panel. */
	private static final int SLIM_SLOT = 20;
	private static final int SLIM_GAP = 2;
	private static final int SLIM_PAD = 2;
	private static final float SLIM_RADIUS = 6f;
	private static final float SLIM_PANEL_OPACITY = 0.6f;
	/** Height of the separators between slots, the accent fill of the selected slot and its glow. */
	private static final int SEPARATOR_HEIGHT = 11;
	private static final float SEPARATOR_OPACITY = 0.13f;
	private static final float SELECTED_FILL = 0.2f;
	private static final float GLOW_OPACITY = 0.38f;
	private static final float GLOW_REACH = 7f;
	/** The darkening of a slot whose item cools down. */
	private static final int COOLDOWN_SHADE = 0x9E08080E;
	/** How much of the sample cooldown on the first item in the HUD editor is left. */
	private static final float EDITOR_COOLDOWN = 0.4f;
	/** The cooldown's clickable part in the editor: the slot's upper share, clear of the count and the durability line at its bottom. */
	private static final float COOLDOWN_PART_SHARE = 0.6f;
	/** Durability line: its inset from the slot sides and bottom, and its height. */
	private static final float DURABILITY_INSET = 4f;
	private static final float DURABILITY_BOTTOM = 2.5f;
	private static final float DURABILITY_HEIGHT = 1.5f;
	private static final int DURABILITY_TRACK = 0x80000000;
	private static final int FULL_DURABILITY = 13;
	/** Item counts and slot numbers: text sizes. */
	private static final float COUNT_SCALE = 0.72f;
	private static final float NUMBER_SCALE = 0.55f;
	/** Gap between the hotbar and the offhand slot beside it. */
	private static final int OFFHAND_GAP = 6;
	/** The selection snaps to its slot when this close, since the spring spends long on the last bit. */
	private static final float SELECTION_SETTLED = 0.03f;

	private final EnumSetting<HudLook> look = add(new EnumSetting<>("look", Component.literal("Look"), HudLook.STANDARD, HudLook::displayName,
		EnumSetting.Style.SEGMENTED), "Look");

	private final BooleanSetting separators = add(new BooleanSetting("separators", Component.literal("Separators"), true), "Shows");
	private final BooleanSetting slotBoxes = add(new BooleanSetting("slot_boxes", Component.literal("Slot boxes"), false), "Shows");
	private final BooleanSetting numbers = add(new BooleanSetting("numbers", Component.literal("Slot numbers"), false), "Shows");
	private final BooleanSetting itemCounts = add(new BooleanSetting("item_counts", Component.literal("Item counts"), true), "Shows");
	private final BooleanSetting durability = add(new BooleanSetting("durability", Component.literal("Durability"), true), "Shows");
	private final BooleanSetting cooldowns = add(new BooleanSetting("cooldowns", Component.literal("Cooldowns"), true), "Shows");
	private final BooleanSetting selectedGlow = add(new BooleanSetting("selected_glow", Component.literal("Selected glow"), true), "Shows");

	/** Which side of the hotbar the offhand slot is on. */
	public enum OffhandSide {
		LEFT("Left"),
		RIGHT("Right");

		private final Component displayName;

		OffhandSide(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	private final EnumSetting<OffhandSide> offhandSide = add(new EnumSetting<>("offhand_side", Component.literal("Offhand side"), OffhandSide.LEFT,
		OffhandSide::displayName, EnumSetting.Style.SEGMENTED), "General");
	private final BooleanSetting scrollAnimation = add(new BooleanSetting("scroll_animation", Component.literal("Glide between slots"), true), "General");
	private final BooleanSetting lift = add(new BooleanSetting("lift", Component.literal("Raise selected item"), true), "General");

	private final BooleanSetting itemName = add(new BooleanSetting("item_name", Component.literal("Item name"), true), "Item name",
		"Above the stack when you switch items");
	private final BooleanSetting tidyName = add(new BooleanSetting("tidy_name", Component.literal("Without stars and \"(Click)\""), true), "Item name");

	/** Held item name style: the name in its color, a pill with the item and name, or a card like the pet panel's with the rarity below. */
	public enum NameStyle {
		TEXT("Text"),
		PILL("Pill"),
		CARD("Card");

		private final Component displayName;

		NameStyle(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	private final EnumSetting<NameStyle> nameStyle = add(new EnumSetting<>("name_style", Component.literal("Style"),
		NameStyle.TEXT, NameStyle::displayName, EnumSetting.Style.SEGMENTED), "Item name");
	/** The glow around the item name has its own switch; the hotbar's glow only applies to the hotbar. */
	private final BooleanSetting nameGlow = add(new BooleanSetting("name_glow", Component.literal("Glow"), true), "Item name");
	/** The rarity of a SkyBlock item, in the last lore lines: "LEGENDARY SWORD". */
	private static final Pattern RARITY = Pattern.compile("^(?:a )?(COMMON|UNCOMMON|RARE|EPIC|LEGENDARY|MYTHIC|DIVINE|SPECIAL|VERY SPECIAL|ULTIMATE|ADMIN)\\b.*$");
	private static final int CARD_PAD = 7;
	private static final int CARD_ICON = 26;
	private static final int CARD_HEIGHT = CARD_PAD * 2 + CARD_ICON;
	private static final int PILL_HEIGHT = 24;
	/** The name in the Text style: its size, and the soft dark light behind it that keeps it readable over a bright world. */
	private static final float NAME_SCALE = 1f;
	private static final int NAME_SHADOW = 0x8C000000;
	private static final int NAME_BACKDROP = 0x30000000;
	/** How long the name stays, and how long it takes to appear and to fade. */
	private static final float NAME_SECONDS = 1.7f;
	private static final float NAME_APPEAR = 0.28f;
	private static final float NAME_FADE = 0.25f;
	/** Upgrade stars of a SkyBlock item and the circled master star numbers that follow its name. */
	private static final Pattern STARS = Pattern.compile("[\\u272A\\u272F\\u2605\\u2606\\u278A-\\u2793]+");
	private static final Pattern CLICK = Pattern.compile("\\s*\\((?:Click|Right Click|Left Click)\\)\\s*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern SPACES = Pattern.compile("\\s{2,}");

	/** Cached count texts, drawn every frame. */
	private static final String[] COUNTS = new String[100];
	private static final String[] SLOT_NUMBERS = {"1", "2", "3", "4", "5", "6", "7", "8", "9"};

	/** The displayed name, without the decorations SkyBlock appends to item names (these are shown elsewhere, not meant to be read in a name). */
	private String tidiedFrom = "";
	private String tidiedTo = "";

	private String shownName(String name) {
		if (!tidyName.get()) {
			return name;
		}
		// the name is the same every frame: compute it once
		if (name.equals(tidiedFrom)) {
			return tidiedTo;
		}
		tidiedFrom = name;
		tidiedTo = tidied(name);
		return tidiedTo;
	}

	/** The item whose name was read last, its name at the time, and the result: the same item with the same name is not re-read every frame. */
	private ItemStack namedStack;
	private Object namedComponent;
	private String namedText = "";
	private int namedColor;

	private String nameOf(ItemStack stack) {
		Object component = stack.get(DataComponents.CUSTOM_NAME);
		if (stack != namedStack || component != namedComponent) {
			namedStack = stack;
			namedComponent = component;
			namedText = stack.getHoverName().getString();
			namedColor = colorOf(stack);
		}
		return namedText;
	}

	private static String tidied(String name) {
		String tidy = SPACES.matcher(CLICK.matcher(STARS.matcher(name).replaceAll("")).replaceAll("")).replaceAll(" ").strip();
		return tidy.isEmpty() ? name : tidy;
	}

	/** Name offset from its place above the stack and its scale; set in the HUD editor. */
	private final FloatSetting nameDx = add(new FloatSetting("name_dx", Component.literal("Name across"), 0f, -2000f, 2000f, 0), "General");
	private final FloatSetting nameDy = add(new FloatSetting("name_dy", Component.literal("Name down"), 0f, -2000f, 2000f, 0), "General");
	private final FloatSetting nameScale = add(new FloatSetting("name_scale", Component.literal("Name size"), 1f, 0.5f, 3f, 2), "General");
	private int[] nameBounds = {0, 0, 0, 0};

	private float selection = -1f;
	private final Spring selectionSpring = Spring.smooth(Spring.DEFAULT);
	/** What the shown name was built for, and when it appeared, for the animation. */
	private String nameKey = "";
	private long nameStart;

	public HotbarFeature() {
		super("hotbar", "Hotbar", "The hotbar on glass, with the status bars stacked above it.", false, Anchor.CENTER, true, 0f, 4f);
		iconRgb(SystemColors.GREEN_RGB);
		iconName("hotbar");
		summary("The hotbar in the style of the mod");
		// the hotbar is one of two panels that glow by default (with the info bar); the other panels do not
		glow.setDefault(true);
		nameDx.visibleWhen(() -> false);
		nameDy.visibleWhen(() -> false);
		nameScale.visibleWhen(() -> false);
		tidyName.under(itemName);
		nameStyle.under(itemName);
		nameGlow.under(itemName, () -> nameStyle.get() != NameStyle.TEXT);
	}

	// ----------------------------------------------------------------------------------------------------- settings card

	/** @return the look shared by the hotbar and the status bars */
	public EnumSetting<HudLook> lookSetting() {
		return look;
	}

	public HudLook look() {
		return look.get();
	}

	/** @return whether the vanilla held item name is hidden because it is shown here */
	public boolean hidesVanillaName() {
		return isEnabled() && itemName.get();
	}

	private boolean slim() {
		return look.get() == HudLook.SLIM;
	}

	private int slot() {
		return slim() ? SLIM_SLOT : SLOT;
	}

	private int gap() {
		return slim() ? SLIM_GAP : SLOT_GAP;
	}

	private int pad() {
		return slim() ? SLIM_PAD : PAD;
	}

	private float radius() {
		return slim() ? SLIM_RADIUS : RADIUS;
	}

	@Override
	protected int panelWidth() {
		return pad() * 2 + 9 * slot() + 8 * gap();
	}

	@Override
	protected int panelHeight() {
		return pad() * 2 + slot();
	}

	/** @return the left edge of a slot, in panel pixels */
	private int slotX(int index) {
		return pad() + index * (slot() + gap());
	}

	// ----------------------------------------------------------------------------------------------------- the hotbar

	@Override
	protected void drawPanel(Ui ui, GuiGraphicsExtractor graphics, Minecraft client) {
		int width = panelWidth();
		int height = panelHeight();
		int slot = slot();
		int top = pad();
		if (hasBackground()) {
			float opacity = HudStyle.opacity() * (1f - seeThrough.get()) * (slim() ? SLIM_PANEL_OPACITY : 1f);
			HudStyle.panel(ui, 0, 0, width, height, radius(), opacity, glow.get() && !slim());
		}
		Inventory inventory = client.player.getInventory();
		int selected = inventory.getSelectedSlot();
		// The selection springs to the slot and continues from its current position and velocity when another slot is selected mid-flight: slow
		// scrolling glides, and fast scrolling over many slots does not lag behind.
		boolean animate = scrollAnimation.get() && ModFeatures.MENU.animations();
		if (selection < 0f || !animate) {
			selectionSpring.snap(selected);
		}
		selection = Mth.clamp(selectionSpring.follow(selected), 0f, 8f);
		if (Math.abs(selection - selected) < SELECTION_SETTLED) {
			selection = selected;
		}

		int accent = accentArgb();
		float slotBoxAlpha = HudParts.alpha(slotBoxes.get());
		for (int i = 0; i < 9; i++) {
			int x = slotX(i);
			if (slotBoxAlpha > 0f) {
				ui.rounded(x, top, slot, slot, SLOT_RADIUS, Ui.withOpacity(HudStyle.slot(), slotBoxAlpha));
			}
			part("Slot boxes", slotBoxes, x, top, slot, slot);
		}
		drawSeparators(ui, top, slot);
		drawSelection(ui, top, slot, accent);

		float countAlpha = HudParts.alpha(itemCounts.get());
		float durabilityAlpha = HudParts.alpha(durability.get());
		float partial = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true);
		int inset = (slot - ITEM) / 2;
		int guiScale = Math.max(1, Math.round(client.getWindow().getGuiScale() * drawnScale()));
		for (int i = 0; i < 9; i++) {
			ItemStack stack = inventory.getItem(i);
			int x = slotX(i);
			if (stack.isEmpty()) {
				continue;
			}
			float near = Math.max(0f, 1f - Math.abs(i - selection));
			float eased = near * near * (3f - 2f * near);
			// the selected item rises slightly, eased at sub-pixel precision so it moves smoothly instead of in steps
			float rise = lift.get() ? -1.5f * eased : 0f;
			var pose = graphics.pose();
			pose.pushMatrix();
			pose.translate(0f, rise);
			graphics.item(stack, x + inset, top + inset);
			pose.popMatrix();
			drawDecorations(ui, client, stack, x, top, slot, countAlpha, durabilityAlpha, partial, guiScale);
		}
		if (HudParts.alpha(numbers.get()) > 0f) {
			int color = Ui.withOpacity(HudStyle.text(), 0.55f * HudParts.alpha(numbers.get()));
			for (int i = 0; i < 9; i++) {
				ui.textAtScaled(SLOT_NUMBERS[i], slotX(i) + 2f, top + 1.5f, NUMBER_SCALE, color);
			}
		}
		for (int i = 0; i < 9; i++) {
			part("Slot numbers", numbers, slotX(i), top, 6, 6);
		}
		drawOffhand(ui, graphics, client, width, height, partial, guiScale);
	}

	/** Draws the thin separators between slots; the ones next to the selected slot fade away as it comes near. */
	private void drawSeparators(Ui ui, int top, int slot) {
		float alpha = HudParts.alpha(separators.get());
		int gap = gap();
		for (int i = 1; i < 9; i++) {
			float lineX = slotX(i) - (gap + 1) / 2f;
			part("Separators", separators, lineX - 1f, top, 3f, slot);
			if (alpha <= 0f) {
				continue;
			}
			float away = Math.min(Math.abs(i - selection), Math.abs(i - selection - 1f));
			float visible = Math.min(1f, away * 2f);
			if (visible > 0.02f) {
				ui.roundedSmooth(lineX, top + (slot - SEPARATOR_HEIGHT) / 2f, 1f, SEPARATOR_HEIGHT, 0.5f,
					HudStyle.line(SEPARATOR_OPACITY * visible * alpha));
			}
		}
	}

	/** Draws the selected slot: an accent ring over a soft accent fill, with a glow, at the gliding selection. */
	private void drawSelection(Ui ui, int top, int slot, int accent) {
		float x = pad() + selection * (slot() + gap());
		float glowAlpha = HudParts.alpha(selectedGlow.get());
		if (glowAlpha > 0f && !slim()) {
			ui.softLight(x + slot / 2f, top + slot / 2f, slot / 2f + GLOW_REACH, Ui.withOpacity(accent, GLOW_OPACITY * glowAlpha));
		}
		ui.roundedSmooth(x, top, slot, slot, SLOT_RADIUS, Ui.withOpacity(accent, SELECTED_FILL));
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate(x - Math.round(x), 0f);
		ui.ring(Math.round(x), top, slot, slot, SLOT_RADIUS, 1, accent);
		pose.popMatrix();
		part("Selected glow", selectedGlow, x, top, slot, slot);
	}

	/** Draws a stack's count, durability line and cooldown, each as its switch says. */
	private void drawDecorations(Ui ui, Minecraft client, ItemStack stack, int x, int top, int slot, float countAlpha, float durabilityAlpha,
		float partial, int guiScale) {
		// off means no cooldown overlay at all; in the editor a cooling slot (or a sample on the first item) shows it, faded while off, to click
		float left = client.player.getCooldowns().getCooldownPercent(stack, partial);
		if (left <= 0f && HudEditScreen.isOpen() && stack == client.player.getInventory().getItem(0)) {
			left = EDITOR_COOLDOWN;
		}
		float cooldownAlpha = HudParts.alpha(cooldowns.get());
		if (left > 0f && cooldownAlpha > 0f) {
			Sweep.draw(ui, x, top, slot, slot, SLOT_RADIUS, left, Ui.withOpacity(COOLDOWN_SHADE, cooldownAlpha), guiScale);
		}
		if (left > 0f) {
			part("Cooldowns", cooldowns, x, top, slot, slot * COOLDOWN_PART_SHARE);
		}
		if (stack.isBarVisible()) {
			float lineX = x + DURABILITY_INSET;
			float lineY = top + slot - DURABILITY_BOTTOM - DURABILITY_HEIGHT;
			float lineWidth = slot - 2 * DURABILITY_INSET;
			if (durabilityAlpha > 0f) {
				ui.roundedSmooth(lineX, lineY, lineWidth, DURABILITY_HEIGHT, DURABILITY_HEIGHT / 2f, Ui.withOpacity(DURABILITY_TRACK, durabilityAlpha));
				float filled = lineWidth * Mth.clamp(stack.getBarWidth() / (float) FULL_DURABILITY, 0f, 1f);
				ui.roundedSmooth(lineX, lineY, filled, DURABILITY_HEIGHT, DURABILITY_HEIGHT / 2f,
					Ui.withOpacity(0xFF000000 | stack.getBarColor(), durabilityAlpha));
			}
			part("Durability", durability, lineX, lineY - 1f, lineWidth, DURABILITY_HEIGHT + 2f);
		}
		int count = stack.getCount();
		if (count > 1) {
			String text = count < COUNTS.length ? countText(count) : String.valueOf(count);
			float textWidth = ui.boldWidthScaled(text, COUNT_SCALE);
			float textX = x + slot - 1.5f - textWidth;
			float textY = top + slot - 7f;
			if (countAlpha > 0f) {
				ui.textAtScaledBold(text, textX + 0.5f, textY + 0.5f, COUNT_SCALE, Ui.withOpacity(NAME_SHADOW, countAlpha));
				ui.textAtScaledBold(text, textX, textY, COUNT_SCALE, Ui.withOpacity(0xFFFFFFFF, countAlpha));
			}
			part("Item counts", itemCounts, textX - 1f, textY - 1f, textWidth + 2f, 8f);
		}
	}

	private static String countText(int count) {
		String text = COUNTS[count];
		if (text == null) {
			text = COUNTS[count] = String.valueOf(count);
		}
		return text;
	}

	/** @return the room the offhand tile takes beside the hotbar on a side, in GUI pixels: none while it is on the other side or the offhand is empty */
	public float offhandRoom(boolean left) {
		Player player = Minecraft.getInstance().player;
		if (player == null || player.getOffhandItem().isEmpty() || (offhandSide.get() == OffhandSide.LEFT) != left) {
			return 0f;
		}
		return (OFFHAND_GAP + slot() + 2f * pad()) * drawnScale();
	}

	/** Draws the offhand slot on its own small glass tile beside the hotbar, while the offhand holds something. */
	private void drawOffhand(Ui ui, GuiGraphicsExtractor graphics, Minecraft client, int width, int height, float partial, int guiScale) {
		ItemStack stack = client.player.getOffhandItem();
		if (stack.isEmpty()) {
			return;
		}
		int slot = slot();
		int tile = slot + 2 * pad();
		int tileX = offhandSide.get() == OffhandSide.LEFT ? -OFFHAND_GAP - tile : width + OFFHAND_GAP;
		if (hasBackground()) {
			float opacity = HudStyle.opacity() * (1f - seeThrough.get()) * (slim() ? SLIM_PANEL_OPACITY : 1f);
			HudStyle.panel(ui, tileX, 0, tile, height, radius(), opacity, false);
		}
		int x = tileX + pad();
		int inset = (slot - ITEM) / 2;
		graphics.item(stack, x + inset, pad() + inset);
		drawDecorations(ui, client, stack, x, pad(), slot, HudParts.alpha(itemCounts.get()), HudParts.alpha(durability.get()), partial, guiScale);
	}

	// ----------------------------------------------------------------------------------------------------- the item name

	/**
	 * Draws the held item name above the stack when another item is selected: it grows in from slightly smaller without overshoot, stays for a
	 * while, then fades and sinks away. It can be moved and resized in the HUD editor, where it is always shown.
	 */
	public void renderName(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		Minecraft client = Minecraft.getInstance();
		if (!isEnabled() || !itemName.get() || client.level == null || client.player == null) {
			return;
		}
		ItemStack stack = client.player.getInventory().getSelectedItem();
		boolean editing = HudEditScreen.isOpen();
		String name = stack.isEmpty() ? (editing ? "Item name" : "") : shownName(nameOf(stack));
		long now = System.nanoTime();
		if (!name.equals(nameKey)) {
			nameKey = name;
			nameStart = now;
		}
		if (name.isEmpty()) {
			return;
		}
		float t = (now - nameStart) / 1_000_000_000f;
		float alpha = 1f;
		float size = 1f;
		float sink = 0f;
		if (!editing) {
			if (t > NAME_SECONDS) {
				return;
			}
			boolean animated = ModFeatures.MENU.animations();
			float appear = animated ? Critical.progress(t, NAME_APPEAR) : 1f;
			float out = t > NAME_SECONDS - NAME_FADE ? (NAME_SECONDS - t) / NAME_FADE : 1f;
			float fadeOut = animated ? out * out * (3f - 2f * out) : out > 0f ? 1f : 0f;
			alpha = appear * fadeOut;
			size = (0.95f + 0.05f * appear) * (0.98f + 0.02f * fadeOut);
			sink = (1f - appear) * 5f + (1f - fadeOut) * 3f;
		}

		float hotbarScale = HudStack.scale(graphics.guiWidth(), graphics.guiHeight());
		float own = hotbarScale * nameScale.get();
		Ui.setZoom(own);
		Ui ui = new Ui(graphics, client.font, HudStyle.palette(), accentArgb(), 0f, false);
		NameStyle style = nameStyle.get();
		ItemStack shownStack = stack.isEmpty() ? new ItemStack(Items.DIAMOND_SWORD) : stack;
		int tint = stack.isEmpty() ? SystemColors.PURPLE_RGB : namedColor;
		String sub = style == NameStyle.CARD ? (stack.isEmpty() ? "LEGENDARY SWORD" : subtitleOf(stack)) : "";
		float textWidth = style == NameStyle.TEXT ? ui.boldWidthScaled(name, NAME_SCALE) : ui.width(name);
		float width = switch (style) {
			case TEXT -> textWidth;
			case PILL -> textWidth + 44;
			case CARD -> CARD_PAD + CARD_ICON + 9 + Math.max(textWidth, ui.width(sub)) + CARD_PAD + 4;
		};
		int height = switch (style) {
			case TEXT -> HudStack.NAME_ROW - 2;
			case PILL -> PILL_HEIGHT;
			case CARD -> CARD_HEIGHT;
		};
		float centerX = HudStack.centerX(graphics.guiWidth(), graphics.guiHeight()) + nameDx.get();
		float bottom = HudStack.nameBottom(graphics.guiWidth(), graphics.guiHeight()) + nameDy.get();
		nameBounds = new int[]{Math.round(centerX - width * own / 2f), Math.round(bottom - height * own), Math.round(width * own), Math.round(height * own)};
		var pose = graphics.pose();
		pose.pushMatrix();
		try {
			pose.translate(centerX, bottom + sink);
			pose.scale(own * size, own * size);
			pose.translate(-width / 2f, -height);
			switch (style) {
				case TEXT -> drawNameText(ui, name, textWidth, height, tint, alpha);
				case PILL -> {
					if (hasBackground()) {
						HudStyle.panel(ui, 0, 0, Math.round(width), height, height / 2f, HudStyle.opacity() * alpha, nameGlow.get());
					}
					graphics.item(shownStack, 5, 4);
					ui.text(name, 26, 8, Ui.withOpacity(HudStyle.text(), alpha));
				}
				case CARD -> drawNameCard(ui, graphics, shownStack, name, sub, Math.round(width), alpha, tint);
			}
		} finally {
			pose.popMatrix();
			Ui.setZoom(1f);
		}
	}

	/** Draws the name as text in the item's color, with a soft dark light behind it and a shadow, like the mockup's text shadow. */
	private static void drawNameText(Ui ui, String name, float width, int height, int tint, float alpha) {
		float middle = height / 2f;
		ui.softLight(width / 2f, middle, width / 2f + 8f, height * 0.8f, Ui.withOpacity(NAME_BACKDROP, alpha));
		float y = middle - 4.5f * NAME_SCALE;
		ui.textAtScaledBold(name, 0.6f, y + 0.8f, NAME_SCALE, Ui.withOpacity(NAME_SHADOW, alpha));
		ui.textAtScaledBold(name, 0f, y, NAME_SCALE, Ui.withOpacity(0xFF000000 | HudStyle.readable(tint), alpha));
	}

	/** Draws the name card, like the pet's: the item on a round plate in its rarity color, the name in that color, and the rarity below. */
	private void drawNameCard(Ui ui, GuiGraphicsExtractor graphics, ItemStack stack, String name, String sub, int width, float alpha, int rgb) {
		float alphaBefore = Ui.alpha();
		Ui.setAlpha(alphaBefore * alpha);
		try {
			if (hasBackground()) {
				HudStyle.panel(ui, 0, 0, width, CARD_HEIGHT, Radius.XL, HudStyle.opacity(), nameGlow.get());
			}
			int tint = 0xFF000000 | rgb;
			ui.circle(CARD_PAD + CARD_ICON / 2, CARD_PAD + CARD_ICON / 2, CARD_ICON / 2, Ui.withOpacity(tint, 0.22f));
			ui.ring(CARD_PAD, CARD_PAD, CARD_ICON, CARD_ICON, CARD_ICON / 2f, 1, Ui.withOpacity(tint, 0.8f));
			graphics.item(stack, CARD_PAD + (CARD_ICON - 16) / 2, CARD_PAD + (CARD_ICON - 16) / 2);
			int x = CARD_PAD + CARD_ICON + 9;
			int nameY = sub.isEmpty() ? CARD_PAD + CARD_ICON / 2 - 4 : CARD_PAD + 2;
			ui.text(name, x + 1, nameY + 1, 0x88000000);
			ui.text(name, x, nameY, tint);
			if (!sub.isEmpty()) {
				ui.text(sub, x, CARD_PAD + CARD_ICON - 11, HudStyle.secondary());
			}
		} finally {
			Ui.setAlpha(alphaBefore);
		}
	}

	/** @return an item's name color as the game shows it (its rarity on SkyBlock), or the text color if the name has none */
	private static int colorOf(ItemStack stack) {
		int[] found = {-1};
		stack.getHoverName().visit((style, part) -> {
			if (found[0] < 0 && style.getColor() != null && !part.isBlank()) {
				found[0] = style.getColor().getValue();
			}
			return Optional.empty();
		}, Style.EMPTY);
		if (found[0] < 0) {
			found[0] = HudStyle.text();
		}
		return found[0] & 0xFFFFFF;
	}

	/** @return the line below the name on the card: a SkyBlock item's rarity ("LEGENDARY SWORD"), or the stack count */
	private static String subtitleOf(ItemStack stack) {
		var lore = stack.get(DataComponents.LORE);
		if (lore != null) {
			List<Component> lines = lore.lines();
			for (int i = lines.size() - 1; i >= 0 && i >= lines.size() - 4; i--) {
				String plain = TextColors.noColors(lines.get(i).getString()).strip();
				if (RARITY.matcher(plain).matches()) {
					return plain;
				}
			}
		}
		return stack.getCount() > 1 ? stack.getCount() + "x" : "";
	}

	// ------------------------------------------------------------------------------------- the item name in the HUD editor

	private final EditableHud nameHud = new EditableHud() {
		@Override
		public String hudName() {
			return "Item name";
		}

		@Override
		public boolean hudShown() {
			return isEnabled() && itemName.get();
		}

		@Override
		public int[] hudBounds() {
			return nameBounds;
		}

		@Override
		public void hudMove(int dx, int dy) {
			nameDx.set(Mth.clamp(nameDx.get() + dx, nameDx.min(), nameDx.max()));
			nameDy.set(Mth.clamp(nameDy.get() + dy, nameDy.min(), nameDy.max()));
		}

		@Override
		public float hudScale() {
			return nameScale.get();
		}

		@Override
		public void hudSetScale(float value) {
			nameScale.set(Mth.clamp(value, nameScale.min(), nameScale.max()));
		}

		@Override
		public void hudReset() {
			nameDx.reset();
			nameDy.reset();
			nameScale.reset();
		}

		@Override
		public Feature hudFeature() {
			return HotbarFeature.this;
		}

		/** In the editor, the name's glow switch toggles the name glow, not the hotbar's. */
		@Override
		public BooleanSetting hudGlowSetting() {
			return hasBackground() && nameStyle.get() != NameStyle.TEXT ? nameGlow : null;
		}

		@Override
		public float hudRadius() {
			float base = switch (nameStyle.get()) {
				case TEXT -> 4f;
				case PILL -> PILL_HEIGHT / 2f;
				case CARD -> 12f;
			};
			return base * drawnScale() * nameScale.get();
		}
	};

	/** @return the item name as a separate element in the HUD editor */
	public EditableHud nameHud() {
		return nameHud;
	}
}
