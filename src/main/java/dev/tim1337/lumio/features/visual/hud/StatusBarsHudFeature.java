package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.render.WorldBackdrop;
import dev.tim1337.lumio.api.experimental.ui.ExtraStatusBar;
import dev.tim1337.lumio.api.experimental.ui.SpeedStatusBar;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.ActionBarParts;
import dev.tim1337.lumio.api.StatusStat;
import dev.tim1337.lumio.api.StatusValueProvider;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.SettingsView;
import dev.tim1337.lumio.config.setting.ActionRowSetting;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.MultiSelectSetting;
import dev.tim1337.lumio.hud.PanelLook;
import dev.tim1337.lumio.ui.PagePreview;
import dev.tim1337.lumio.ui.PanelTheme;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.config.setting.StringSetting;
import dev.tim1337.lumio.features.visual.hud.StatusBar.Kind;
import dev.tim1337.lumio.ui.EditableHud;
import dev.tim1337.lumio.hud.HudSimulation;
import dev.tim1337.lumio.ui.HudEditScreen;
import dev.tim1337.lumio.ui.HudParts;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.WidthSnap;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Health, mana, hunger, air, SkyBlock's vitality and drill fuel, and experience, as bars stacked above the hotbar: the stack takes the hotbar's
 * width and grows upwards, rows share the same edges, and up to three bars can share a row at equal widths. A bar pulled out of the stack in the
 * HUD editor floats on its own or lands left or right of the hotbar, compact, leaving the space above it free. Hunger and air only join the stack while they matter. On Hypixel SkyBlock, health and mana are SkyBlock's values
 * (parsed from the action bar), with the defense as a pill in the health bar.
 */
public class StatusBarsHudFeature extends Feature {
	/** The default stack, from the top down: bars of a row share it. */
	public static final String DEFAULT_STACK = "speed;arrows;fuel,vitality;food,air;health,mana;xp";
	private static final int SAMPLE_ARROWS = 2880;
	private static final String SAMPLE_ARROW_NAME = "Flint Arrow";
	private static final int SAMPLE_SPEED = 340;
	private static final int SAMPLE_SPEED_CAP = 400;
	/** How much nearer to white the Speed bar is at its cap. */
	private static final float SPEED_CAP_BRIGHTER = 0.75f;
	private static final String ARROWS_ID = "arrows";
	private static final String SPEED_ID = "speed";
	private static final Set<String> IDS = Set.of("health", "mana", "food", "air", "vitality", "fuel", "arrows", "speed", "xp");
	/** Width of a floating bar at size 1: half the hotbar, like a bar that shares a row with one other. */
	private static final int FLOAT_WIDTH = 112;
	/** Health below this share pulses, once a second. */
	private static final float LOW_HEALTH = 0.25f;
	private static final double PULSE_PER_SECOND = Math.PI * 2.0;
	/** The red screen edge at low health: its depth as a share of the smaller screen side, and its strength. */
	private static final float EDGE_DEPTH = 0.09f;
	private static final float EDGE_OPACITY = 0.12f;
	private static final float EDGE_PULSE = 0.06f;
	/** How far a dragged bar may be from the stack and still land in it, in GUI pixels. */
	private static final float STACK_REACH = 24f;
	/** A bar this near the end of a row (as a share of the row width) shares that row. */
	private static final float SHARE_EDGE = 0.22f;
	/** The soft empty slot showing where a dragged bar lands. */
	private static final int SLOT_FILL = 0x1AFFFFFF;
	/** The bars beside the hotbar: their size against the stack's (17 instead of 20), the gap to the hotbar, their width at size 1 and how wide it may get. */
	private static final float COMPACT = 0.85f;
	private static final float SIDE_GAP = 8f;
	private static final float SIDE_WIDTH = 84f;
	private static final float SIDE_MOST_WIDTH = 200f;
	/** How near a spot the mouse lands a bar there, and how near it has to be for the bar to be fully compact, in GUI pixels at size 1. */
	private static final float SIDE_REACH = 30f;
	private static final float SIDE_FULL_REACH = 12f;
	/** How near the screen's edges the bars beside the hotbar may go. */
	private static final float SCREEN_MARGIN = 2f;
	/** The spots while a bar is dragged: empty, and the one the bar would land in. */
	private static final int SPOT_FILL = 0x17FFFFFF;
	private static final int SPOT_EDGE = 0x33FFFFFF;
	private static final int SPOT_LIT = 0x47FFFFFF;
	private static final int SPOT_LABEL = 0x8CFFFFFF;
	private static final String SIDE_LABEL = "Beside the hotbar";
	private static final String ABOVE_LABEL = "Above";
	/** The narrowest a bar may be with its value still readable, and the experience bar, in GUI pixels at size 1. */
	private static final int MIN_BAR_WIDTH = 40;
	private static final int MIN_XP_WIDTH = 30;
	/** The chip naming the stack in the editor. */
	private static final int CHIP_HEIGHT = 14;

	// what the stack shows (the chips of the settings card)
	private final BooleanSetting showHealth = add(new BooleanSetting("show_health", Component.literal("Health"), true), "Shows");
	private final BooleanSetting showMana = add(new BooleanSetting("show_mana", Component.literal("Mana"), true), "Shows");
	private final BooleanSetting showXp = add(new BooleanSetting("show_xp", Component.literal("Experience"), true), "Shows");
	private final BooleanSetting showFood = add(new BooleanSetting("show_food", Component.literal("Hunger"), true), "Shows");
	private final BooleanSetting showAir = add(new BooleanSetting("show_air", Component.literal("Air"), true), "Shows");
	private final BooleanSetting showDefense = add(new BooleanSetting("show_defense", Component.literal("Defense"), true), "Shows");
	private final BooleanSetting showAbsorption = add(new BooleanSetting("show_absorption", Component.literal("Absorption"), true), "Shows");
	private final BooleanSetting showOverflow = add(new BooleanSetting("show_overflow_mana", Component.literal("Overflow mana"), true), "Shows");
	private final BooleanSetting showVitality = add(new BooleanSetting("show_vitality", Component.literal("Vitality"), true), "Shows");
	private final BooleanSetting showFuel = add(new BooleanSetting("show_drill_fuel", Component.literal("Drill fuel"), true), "Shows");
	private final BooleanSetting showArrows = add(new BooleanSetting("show_arrows", Component.literal("Arrows"), true), "Shows");
	private final BooleanSetting showSpeed = add(new BooleanSetting("show_speed", Component.literal("Speed"), false), "Shows");

	// numbers
	private final BooleanSetting maximum = add(new BooleanSetting("maximum", Component.literal("Show maximum"), true), "Numbers");
	private final BooleanSetting separators = add(new BooleanSetting("separators", Component.literal("Thousands separator"), true), "Numbers");
	private final BooleanSetting fuelPercent = add(new BooleanSetting("fuel_percent", Component.literal("Fuel in percent"), false), "Numbers");

	// behavior
	private final BooleanSetting skyblockHealth = add(new BooleanSetting("skyblock_health", Component.literal("SkyBlock health"), true), "General",
		"Instead of vanilla hearts");
	private final BooleanSetting hideVanilla = add(new BooleanSetting("hide_vanilla", Component.literal("Hide vanilla bars"), true), "General");
	private final BooleanSetting lowHealthEdge = add(new BooleanSetting("low_health_edge", Component.literal("Red screen edge at low health"), true),
		"General");

	/** How absorption and overflow mana show: apart from the value (gold after the red, a purple strip, "+375"), or added to it. */
	public enum ExtraMode {
		SEPARATE("Show separately"),
		ADD("Add to health and mana");

		private final Component displayName;

		ExtraMode(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	private final EnumSetting<ExtraMode> extraMode = add(new EnumSetting<>("extra_mode", Component.literal("Absorption and overflow"),
		ExtraMode.SEPARATE, ExtraMode::displayName, EnumSetting.Style.SEGMENTED), "Numbers");
	/** How large the numbers of the bars are: small (as before), medium or large; the bars' heights follow. */
	public enum TextSize {
		SMALL("Small", 1f),
		MEDIUM("Medium", 1.25f),
		LARGE("Large", 1.5f);

		private final Component displayName;
		final float factor;

		TextSize(String displayName, float factor) {
			this.displayName = Component.literal(displayName);
			this.factor = factor;
		}

		public Component displayName() {
			return displayName;
		}
	}

	private final EnumSetting<TextSize> textSize = add(new EnumSetting<>("text_size", Component.literal("Text size"), TextSize.MEDIUM,
		TextSize::displayName, EnumSetting.Style.SEGMENTED), "Numbers");

	/** The soft colors of the absorption and the overflow after the value. */
	private static final int ABSORPTION_TEXT = 0xFFFFE08A;
	private static final int OVERFLOW_TEXT = 0xFFE2C2FF;

	// colors of the fills
	private final ColorSetting healthColor = color("health_color", "Health", 0xF24A40);
	private final ColorSetting manaColor = color("mana_color", "Mana", 0x3A86F8);
	private final ColorSetting foodColor = color("food_color", "Hunger", 0xD98F3A);
	private final ColorSetting airColor = color("air_color", "Air", 0x5EC8F4);
	private final ColorSetting vitalityColor = color("vitality_color", "Vitality", 0xA81C1C);
	private final ColorSetting fuelColor = color("fuel_color", "Drill fuel", 0x2FD27A);
	private final ColorSetting arrowsColor = color("arrows_color", "Arrows", 0xA8702C);
	private final ColorSetting speedColor = color("speed_color", "Speed", 0xDCDCE0);

	{
		// what only an add-on gives the bars (SkyBlock's mana, vitality, drill fuel and own health) is offered only while one does
		BooleanSupplier fromAddon = () -> Addons.status() != null;
		for (Setting<?> setting : List.of(skyblockHealth, showMana, showOverflow, showVitality, showFuel, showArrows, showSpeed, showDefense, fuelPercent, manaColor, vitalityColor,
			fuelColor, arrowsColor, speedColor)) {
			setting.visibleWhen(fromAddon);
		}
	}
	private final ColorSetting xpColor = color("xp_color", "Experience", 0x6FD84B);
	private final ColorSetting absorptionColor = color("absorption_color", "Absorption", 0xF4C043);
	private final ColorSetting overflowColor = color("overflow_color", "Overflow mana", 0xB57BFF);

	/** The stack, from the top down (see {@link BarStackLayout}); bars missing from it float. */
	private final StringSetting stack = add(new StringSetting("stack", Component.literal("Stack"), DEFAULT_STACK, false), "Layout");

	/** The bars left and right of the hotbar, from the top down (see {@link BarStackLayout}). */
	private final StringSetting sideLeft = add(new StringSetting("side_left", Component.literal("Left of the hotbar"), "", false), "Layout");
	private final StringSetting sideRight = add(new StringSetting("side_right", Component.literal("Right of the hotbar"), "", false), "Layout");
	/** How wide the bars beside the hotbar are, the same on both sides, in GUI pixels at size 1. */
	private final FloatSetting sideWidth = add(new FloatSetting("side_width", Component.literal("Width beside the hotbar"), SIDE_WIDTH, MIN_BAR_WIDTH,
		SIDE_MOST_WIDTH, 0), "Layout");

	/** The width of the stack: the hotbar's, or the player's own. */
	public enum StackWidth {
		MATCH_HOTBAR("Match hotbar"),
		CUSTOM("Custom");

		private final Component displayName;

		StackWidth(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	private final EnumSetting<StackWidth> stackWidthMode = add(new EnumSetting<>("stack_width_mode", Component.literal("Stack width"),
		StackWidth.MATCH_HOTBAR, StackWidth::displayName, EnumSetting.Style.SEGMENTED), "Layout");
	/** The stack's own width, in GUI pixels at size 1. */
	private final FloatSetting stackWidth = add(new FloatSetting("stack_width", Component.literal("Custom width"), 228f, 60f, 800f, 0), "Layout");
	/** How the bars of a shared row split it, as weights per bar ("health:1.3,mana:0.7"); a bar without one has 1, so rows split equally. */
	private final StringSetting rowWeights = add(new StringSetting("row_weights", Component.literal("Row splits"), "", false), "Layout");
	private final Map<String, Float> weights = new HashMap<>();
	private String parsedWeights;

	/** The kinds of bar, once: {@code values()} makes a new array at every call. */
	private static final Kind[] KINDS = Kind.values();
	private final StatusBar[] bars = new StatusBar[KINDS.length];
	private final Slot[] slots = new Slot[KINDS.length];
	/** The stack as last parsed, and the text it was parsed from. */
	private List<List<String>> rows = new ArrayList<>();
	private String parsedStack;
	/** The bars of the two sides as last parsed, the texts they came from, and which side fits on the screen now. */
	private List<List<String>> sides = List.of(List.of(), List.of());
	private String parsedLeft;
	private String parsedRight;
	private final boolean[] sideFits = {true, true};
	private final boolean[] parsedFits = {true, true};
	/** The side each bar is shown on now, by {@link Kind} order. */
	private final int[] sideOfBar = new int[KINDS.length];
	/** Whether any bar is shown now. */
	private boolean visibleNow;

	public StatusBarsHudFeature() {
		super("status_bars", "Status bars", "Health, mana, hunger, air and experience as bars stacked above the hotbar.", false);
		iconRgb(SystemColors.RED_RGB);
		iconName("heart");
		summary("Health, mana, hunger, air and experience as bars");
		stack.visibleWhen(() -> false);
		rowWeights.visibleWhen(() -> false);
		sideLeft.visibleWhen(() -> false);
		sideRight.visibleWhen(() -> false);
		sideWidth.visibleWhen(() -> false);
		stackWidth.under(stackWidthMode, () -> stackWidthMode.get() == StackWidth.CUSTOM);
		for (Kind kind : KINDS) {
			bars[kind.ordinal()] = new StatusBar(kind);
			slots[kind.ordinal()] = new Slot(kind);
		}
	}

	private ColorSetting color(String key, String name, int rgb) {
		return add(new ColorSetting(key, Component.literal(name), rgb).withOpacity(1f).inRow("Bar colors"), "Colors");
	}

	// ----------------------------------------------------------------------------------------------------- settings card

	// ------------------------------------------------------------------------------------------------- the page's rows

	/** Where the bars' look comes from: the global HUD theme, or their own. They have no panel, so a theme is all there is to choose. */
	private final EnumSetting<PanelLook.Mode> lookMode = add(new EnumSetting<>("look_mode", Component.literal("Look"), PanelLook.Mode.THEME, PanelLook.Mode::displayName,
		EnumSetting.Style.SEGMENTED), "Panel");
	private final PanelLook look = new PanelLook(lookMode);
	private final EnumSetting<PanelTheme> panelTheme = add(new EnumSetting<>("panel_theme", Component.literal("Theme"), PanelTheme.HUD, PanelTheme::displayName, EnumSetting.Style.SEGMENTED), "Panel");
	{
		look.follow(panelTheme, () -> PanelTheme.HUD);
	}
	private ActionRowSetting lookRow;
	private SettingsView lookView;
	private MultiSelectSetting barsRow;

	private SettingsView lookView() {
		if (lookView == null) {
			lookView = look.sheet(id(), List.of(new SettingsView.Group("Theme", List.of(panelTheme))));
		}
		return lookView;
	}

	@Override
	public Setting<?> settingOf(String key) {
		if (key.equals("look_row")) {
			if (lookRow == null) {
				lookRow = look.row(() -> MacScreen.openSubPage(lookView()));
			}
			return lookRow;
		}
		if (key.equals("bars_row")) {
			if (barsRow == null) {
				barsRow = new MultiSelectSetting("bars_row", Component.literal("Bars"), List.of(showHealth, showMana, showXp, showFood, showAir, showVitality, showFuel, showArrows, showSpeed)).asChips().viewOnly();
			}
			return barsRow;
		}
		return super.settingOf(key);
	}

	@Override
	public Feature viewOf(Setting<?> setting) {
		return setting == panelTheme || setting == lookMode ? lookView() : super.viewOf(setting);
	}

	@Override
	protected void afterReset() {
		PanelLook.settleAll();
	}

	/** The real bars, live, large enough to read, as they stand above the hotbar. */
	@Override
	public PagePreview preview() {
		return outline() == null ? null : barsPreview;
	}

	private static final int PREVIEW_MARGIN = 14;
	private static final float PREVIEW_BOTTOM_SHARE = 0.72f;

	private final PagePreview barsPreview = new PagePreview() {
		@Override
		public void draw(Ui menu, int x, int y, int width, int height) {
			WorldBackdrop.draw(menu, x, y, width, height, Radius.LG);
			Minecraft client = Minecraft.getInstance();
			if (client.level == null || client.player == null || !isEnabled()) {
				menu.textCentered(isEnabled() ? "Shows in a world" : "Switched off", x + width / 2, y + height / 2, menu.palette.textSecondary());
				return;
			}
			GuiGraphicsExtractor graphics = menu.graphics;
			float zoom = Ui.zoom();
			float s = (width - 2f * PREVIEW_MARGIN) / Math.max(1f, stackWidth.get());
			var pose = graphics.pose();
			graphics.enableScissor(x, y, x + width, y + height);
			pose.pushMatrix();
			Ui.setZoom(zoom * s);
			try {
				pose.translate(x + width / 2f - graphics.guiWidth() / 2f * s, y + height * PREVIEW_BOTTOM_SHARE - graphics.guiHeight() * s);
				pose.scale(s, s);
				render(graphics, null);
			} finally {
				Ui.setZoom(zoom);
				pose.popMatrix();
				graphics.disableScissor();
			}
		}

		@Override
		public boolean tall() {
			return true;
		}

		@Override
		public String note() {
			return lowHealthEdge.get() ? "Low health edge on" : "Your bars, live";
		}
	};

	/** A layout from before the arrows or the speed gets their bar in the stack, above the others; where it was moved beside the hotbar it stays. */
	@Override
	public void load(com.google.gson.JsonObject json) {
		addToStack(json, ARROWS_ID, showArrows);
		addToStack(json, SPEED_ID, showSpeed);
		PanelLook.loading(() -> super.load(json));
		if (!json.has(lookMode.key())) {
			look.markUndecided();
		}
	}

	private void addToStack(com.google.gson.JsonObject json, String id, BooleanSetting itsSwitch) {
		com.google.gson.JsonElement saved = json.get(stack.key());
		if (saved != null && saved.isJsonPrimitive() && !saved.getAsString().contains(id) && !json.has(itsSwitch.key())) {
			json.addProperty(stack.key(), id + ";" + saved.getAsString());
		}
	}

	/** @return the switch of a bar */
	private BooleanSetting switchOf(Kind kind) {
		return switch (kind) {
			case HEALTH -> showHealth;
			case MANA -> showMana;
			case FOOD -> showFood;
			case AIR -> showAir;
			case VITALITY -> showVitality;
			case FUEL -> showFuel;
			case ARROWS -> showArrows;
			case SPEED -> showSpeed;
			case XP -> showXp;
		};
	}

	/** @return the switches of what the stack shows, in the order of its settings card */
	public List<BooleanSetting> shows() {
		if (Addons.status() == null) {
			// mana, defense, overflow, vitality and drill fuel only come from an add-on (SkyBlock's action bar)
			return List.of(showHealth, showXp, showFood, showAir, showAbsorption);
		}
		return List.of(showHealth, showMana, showXp, showFood, showAir, showDefense, showAbsorption, showOverflow, showVitality, showFuel, showArrows, showSpeed);
	}

	/** @return the text size of the bars, which the hotbar's card offers too */
	public EnumSetting<TextSize> textSizeSetting() {
		return textSize;
	}

	/** @return how absorption and overflow show, which the hotbar's card offers too */
	public EnumSetting<ExtraMode> extraModeSetting() {
		return extraMode;
	}

	/** @return the setting of the red screen edge at low health, which the hotbar's card offers too */
	public BooleanSetting lowHealthEdgeSetting() {
		return lowHealthEdge;
	}

	public boolean wantsFuel() {
		return isEnabled() && showFuel.get();
	}

	private boolean wantsAnyStat() {
		return showHealth.get() && skyblockHealth.get() || showMana.get() || showVitality.get() || showFuel.get() || showDefense.get();
	}

	/** @return whether a text belongs to the world of the add-on that gives the bars their values (SkyBlock's action bar) */
	private static boolean isAddonText(Component message) {
		StatusValueProvider status = Addons.status();
		return status != null && status.owns(message);
	}

	private static boolean fresh(StatusStat stat) {
		StatusValueProvider status = Addons.status();
		return status != null && status.fresh(stat);
	}

	private static int value(StatusStat stat) {
		return Addons.status().value(stat);
	}

	private static int max(StatusStat stat) {
		return Addons.status().max(stat);
	}

	/** Parses an action bar as sent by the server, before other mods modify it. The text is not changed here; {@link #filter} removes parts. */
	public void observe(Component message) {
		if (message != null && isEnabled() && wantsAnyStat() && isAddonText(message)) {
			Addons.status().observe(message, skyblockHealth.get(), showMana.get());
		}
	}

	/** Called with every action bar text: parses it and returns the text to display. */
	public Component filter(Component message) {
		if (message == null || !isEnabled() || !wantsAnyStat() || !isAddonText(message)) {
			return message;
		}
		// whether and what to remove is configured on the action bar page
		boolean take = ModFeatures.ACTION_BAR != null && ModFeatures.ACTION_BAR.takesOut();
		int remove = ModFeatures.ACTION_BAR == null ? ActionBarParts.USUAL : ModFeatures.ACTION_BAR.removeMask();
		return Addons.status().filter(message, skyblockHealth.get(), showMana.get(), take, remove);
	}

	/** @return the elements movable in the HUD editor: every bar */
	public List<EditableHud> editables() {
		if (Addons.status() != null) {
			return List.of(slots);
		}
		// mana, vitality and drill fuel only come from an add-on: without one the editor has nothing to say about them
		return List.of(slots[Kind.HEALTH.ordinal()], slots[Kind.FOOD.ordinal()], slots[Kind.AIR.ordinal()], slots[Kind.XP.ordinal()]);
	}

	/** @return whether the vanilla health and food bars are hidden */
	public boolean hidesVanillaHealthFood() {
		return isEnabled() && hideVanilla.get();
	}

	/** @return whether the vanilla armor bar is hidden: the armor is the defense pill in the health bar */
	public boolean hidesVanillaArmor() {
		return isEnabled() && hideVanilla.get();
	}

	/** @return whether the vanilla experience number and bar are hidden */
	public boolean hidesVanillaXp() {
		return isEnabled() && hideVanilla.get();
	}

	/** @return whether the vanilla air bubbles are hidden */
	public boolean hidesVanillaAir() {
		return isEnabled() && hideVanilla.get() && showAir.get();
	}

	/**
	 * @return how far the chat must move up so its lowest line clears the bars it would overlap, in GUI pixels: the chat occupies the bottom-left
	 *         corner, where floating bars or a narrow screen can put bars too
	 */
	public int chatLift(int chatBottom, int chatTop, int chatRight) {
		if (!isEnabled() || !visibleNow) {
			return 0;
		}
		int lift = 0;
		for (StatusBar bar : bars) {
			int[] box = bar.bounds;
			if (!bar.present || box[2] <= 0 || box[0] >= chatRight || box[1] >= chatBottom || box[1] + box[3] <= chatTop) {
				continue;
			}
			lift = Math.max(lift, chatBottom - box[1] + 3);
		}
		return lift;
	}

	private HudLook look() {
		return ModFeatures.HOTBAR == null ? HudLook.STANDARD : ModFeatures.HOTBAR.look();
	}

	private boolean slim() {
		return look() == HudLook.SLIM;
	}

	/** @return a bar's height in bar pixels, in the current look */
	private int barHeight(Kind kind) {
		if (kind == Kind.XP) {
			return slim() ? StatusBarPainter.slimXpHeight() : StatusBarPainter.xpHeight();
		}
		return slim() ? StatusBarPainter.slimHeight() : StatusBarPainter.barHeight();
	}

	// ---------------------------------------------------------------------------------------------------------- values

	/**
	 * @return whether a bar shows its sample in the HUD editor: the editor shows what would show now, with its real values. The sample is for a bar the editor's
	 *         list asked for while it is not active here, and for all of them while an activity is simulated.
	 */
	private boolean sampled(Kind kind) {
		return HudEditScreen.isOpen() && (HudSimulation.active() || slots[kind.ordinal()].previewed);
	}

	/** Reads every bar's values and whether it is shown now. */
	private void prepare(Minecraft client) {
		Player player = client.player;
		boolean editing = HudEditScreen.isOpen();
		boolean hurts = client.gameMode == null || client.gameMode.canHurtPlayer();
		boolean grouped = separators.get();
		boolean showMax = maximum.get() || editing;

		StatusBar health = bar(Kind.HEALTH);
		boolean healthSample = sampled(Kind.HEALTH);
		health.present = showHealth.get() && (hurts || healthSample);
		if (health.present) {
			if (skyblockHealth.get() && fresh(StatusStat.HEALTH)) {
				// SkyBlock includes absorption in the health (68,451 of 8,076): the bar shows health, and the excess as absorption
				int current = value(StatusStat.HEALTH);
				int most = max(StatusStat.HEALTH);
				int absorbed = showAbsorption.get() ? Math.max(0, current - most) : 0;
				withExtra(health, Math.min(current, most), most, absorbed, grouped, showMax);
				health.defense(fresh(StatusStat.DEFENSE) ? value(StatusStat.DEFENSE) : 0, grouped);
			} else {
				int current = healthSample ? 17 : (int) Math.ceil(player.getHealth());
				int most = healthSample ? 20 : Math.max(1, (int) Math.ceil(player.getMaxHealth()));
				int absorption = healthSample || !showAbsorption.get() ? 0 : (int) Math.ceil(player.getAbsorptionAmount());
				withExtra(health, current, most, absorption, grouped, showMax);
				health.defense(healthSample ? 12 : player.getArmorValue(), grouped);
			}
			shareWithExtra(health);
		}

		StatusBar mana = bar(Kind.MANA);
		boolean manaFresh = fresh(StatusStat.MANA);
		mana.present = showMana.get() && (manaFresh || sampled(Kind.MANA));
		if (mana.present) {
			int over = !showOverflow.get() ? 0 : manaFresh ? value(StatusStat.OVERFLOW_MANA) : 60;
			withExtra(mana, manaFresh ? value(StatusStat.MANA) : 410, manaFresh ? max(StatusStat.MANA) : 500, over, grouped, showMax);
			shareWithExtra(mana);
		}

		// hunger and air only while they matter: hunger below full, air under water or while it refills
		StatusBar food = bar(Kind.FOOD);
		int foodLevel = player.getFoodData().getFoodLevel();
		food.present = showFood.get() && (hurts && foodLevel < 20 || sampled(Kind.FOOD));
		if (food.present) {
			food.values(foodLevel >= 20 ? 15 : foodLevel, 20, 0, grouped, showMax, false);
		}
		StatusBar air = bar(Kind.AIR);
		int airLeft = player.getAirSupply();
		int airMost = Math.max(1, player.getMaxAirSupply());
		air.present = showAir.get() && (hurts && (player.isUnderWater() || airLeft < airMost) || sampled(Kind.AIR));
		if (air.present) {
			// shown in bubbles like the vanilla bar: ten for a full supply
			int bubbles = Mth.clamp(Mth.ceil(airLeft * 10f / airMost), 0, 10);
			air.values(airLeft >= airMost && !player.isUnderWater() ? 6 : bubbles, 10, 0, grouped, showMax, false);
		}

		StatusBar vitality = bar(Kind.VITALITY);
		boolean vitalityFresh = fresh(StatusStat.VITALITY);
		vitality.present = showVitality.get() && (vitalityFresh || sampled(Kind.VITALITY));
		if (vitality.present) {
			vitality.values(vitalityFresh ? value(StatusStat.VITALITY) : 96, vitalityFresh ? Math.max(1, max(StatusStat.VITALITY)) : 126, 0, grouped,
				showMax, false);
		}
		StatusBar fuel = bar(Kind.FUEL);
		boolean fuelFresh = fresh(StatusStat.FUEL);
		fuel.present = showFuel.get() && (fuelFresh || sampled(Kind.FUEL));
		if (fuel.present) {
			int current = fuelFresh ? value(StatusStat.FUEL) : 91_118;
			int most = fuelFresh ? Math.max(1, max(StatusStat.FUEL)) : 100_000;
			if (fuelPercent.get()) {
				fuel.percentValue(current, most);
			} else {
				fuel.values(current, most, 0, grouped, showMax, false);
			}
		}

		StatusBar arrows = bar(Kind.ARROWS);
		boolean arrowsShown = ExtraStatusBar.shown();
		arrows.present = showArrows.get() && (arrowsShown || sampled(Kind.ARROWS));
		if (arrows.present) {
			arrows.named(arrowsShown ? ExtraStatusBar.value() : SAMPLE_ARROWS, arrowsShown ? ExtraStatusBar.most() : SAMPLE_ARROWS, arrowsShown ? ExtraStatusBar.name() : SAMPLE_ARROW_NAME, arrowsShown ? ExtraStatusBar.numberRgb() : 0, grouped);
		}
		StatusBar speed = bar(Kind.SPEED);
		boolean speedShown = SpeedStatusBar.shown();
		speed.present = showSpeed.get() && (speedShown || sampled(Kind.SPEED));
		if (speed.present) {
			speed.named(speedShown ? SpeedStatusBar.value() : SAMPLE_SPEED, speedShown ? SpeedStatusBar.most() : SAMPLE_SPEED_CAP, "", 0, false);
		}
		StatusBar xp = bar(Kind.XP);
		boolean xpActive = client.gameMode == null || client.gameMode.hasExperience();
		xp.present = showXp.get() && (xpActive || sampled(Kind.XP));
		if (xp.present) {
			xp.experience(xpActive ? player.experienceLevel : 12, xpActive ? player.experienceProgress : 0.6f, player.totalExperience, xpActive && !editing);
		}

		boolean animated = ModFeatures.MENU.animations();
		visibleNow = false;
		for (StatusBar bar : bars) {
			visibleNow |= bar.present;
			if (bar.present) {
				bar.advanceFill(animated);
			}
		}
	}

	/**
	 * Sets a value with its absorption or overflow: shown apart ("7,605 +375", gold or a purple strip), or added to it, filling the bar by the sum
	 * with no extra color. The real share without the extra is kept either way, for the low health warning.
	 */
	private void withExtra(StatusBar bar, int current, int most, int extra, boolean grouped, boolean showMax) {
		if (extraMode.get() == ExtraMode.ADD) {
			bar.values(current + extra, most, 0, grouped, showMax, false);
		} else {
			bar.values(current, most, extra, grouped, showMax, true);
		}
		bar.realFraction = Mth.clamp(current / (float) Math.max(1, most), 0f, 1f);
	}

	/** Health with absorption and mana with overflow share their bar: its own color for the value, and the other one after it for the extra. */
	private void shareWithExtra(StatusBar bar) {
		if (bar.extra > 0 && extraMode.get() == ExtraMode.SEPARATE) {
			float whole = Math.max(1, bar.maximum + bar.extra);
			bar.fraction = Mth.clamp(bar.current / whole, 0f, 1f);
			bar.extraFraction = Mth.clamp(bar.extra / whole, 0f, 1f - bar.fraction);
		}
	}

	private StatusBar bar(Kind kind) {
		return bars[kind.ordinal()];
	}

	// ---------------------------------------------------------------------------------------------------------- layout

	/** The hotbar this frame: left, top, width and size. */
	private final float[] hotbar = new float[4];
	// the rows of the stack as placed this frame, without a dragged bar: top and bottom of each, and whether a bar may join it
	private float[] rowTops = new float[8];
	private float[] rowBottoms = new float[8];
	private boolean[] rowShares = new boolean[8];
	private int placedRows;
	/** The rows placed this frame, by index into {@link #rows}, so a drop between them maps back to the saved rows. */
	private int[] rowIndex = new int[8];
	/** The soft slot where a dragged bar lands, and whether it shows. */
	private final Spring slotX = Spring.smooth(Spring.DEFAULT);
	private final Spring slotY = Spring.smooth(Spring.DEFAULT);
	private final Spring slotWidth = Spring.smooth(Spring.DEFAULT);
	private float slotHeight;
	private boolean slotShown;
	private boolean slotPlaced;
	/** Top of the stack (the highest row) this frame, or NaN without rows. */
	private float stackTopNow = Float.NaN;
	private long laidOutFrame = -1L;

	/** Lays the bars out for this frame, once: the item name above the stack asks for it before the bars are drawn. */
	void layoutOnce(Minecraft client, int guiWidth, int guiHeight) {
		long frame = HudStack.frame();
		if (frame == laidOutFrame) {
			return;
		}
		laidOutFrame = frame;
		if (!isEnabled() || client.player == null) {
			HudStack.setBarsTop(Float.NaN);
			return;
		}
		StatusBarPainter.textSize(textSize.get().factor);
		prepare(client);
		layout(guiWidth, guiHeight);
		HudStack.setBarsTop(stackTopNow);
	}

	private void parseStack() {
		if (stack.get().equals(parsedStack) && sideLeft.get().equals(parsedLeft) && sideRight.get().equals(parsedRight)
			&& sideFits[BarStackLayout.LEFT] == parsedFits[BarStackLayout.LEFT] && sideFits[BarStackLayout.RIGHT] == parsedFits[BarStackLayout.RIGHT]) {
			return;
		}
		parsedStack = stack.get();
		parsedLeft = sideLeft.get();
		parsedRight = sideRight.get();
		parsedFits[BarStackLayout.LEFT] = sideFits[BarStackLayout.LEFT];
		parsedFits[BarStackLayout.RIGHT] = sideFits[BarStackLayout.RIGHT];
		List<String> left = BarStackLayout.parseSide(parsedLeft, IDS, List.of());
		List<String> right = BarStackLayout.parseSide(parsedRight, IDS, left);
		sides = List.of(left, right);
		rows = BarStackLayout.parse(parsedStack, IDS);
		BarStackLayout.withSides(rows, left, right, sideFits[BarStackLayout.LEFT], sideFits[BarStackLayout.RIGHT], Kind.XP.id);
		for (Kind kind : KINDS) {
			sideOfBar[kind.ordinal()] = BarStackLayout.NO_SIDE;
		}
		for (int side = BarStackLayout.LEFT; side <= BarStackLayout.RIGHT; side++) {
			if (sideFits[side]) {
				for (String id : sides.get(side)) {
					sideOfBar[Kind.byId(id).ordinal()] = side;
				}
			}
		}
	}

	/** Places every shown bar: in its row of the stack above the hotbar, or at its own place when it floats. */
	private void layout(int guiWidth, int guiHeight) {
		HudStack.hotbar(guiWidth, guiHeight, hotbar);
		measureSides(guiWidth);
		parseStack();
		parseWeights();
		float s = hotbar[3];
		// the stack is centered on the hotbar: as wide as it, or as the player set it, never narrower than its rows allow
		float width = stackWidthMode.get() == StackWidth.MATCH_HOTBAR ? hotbar[2] : Math.max(minStackWidth(), stackWidth.get()) * s;
		float left = hotbar[0] + hotbar[2] / 2f - width / 2f;
		stackLeft = left;
		stackWidthNow = width;
		float gap = HudStack.GAP * s;
		Slot dragged = dragging;
		BarStackLayout.Drop drop = dragged != null && dragInStack ? dragDrop : BarStackLayout.Drop.NONE;
		int dropRow = drop == BarStackLayout.Drop.NONE ? -1 : drop.row();

		// from the hotbar upwards: the lowest row first
		float y = hotbar[1] - gap;
		float top = Float.NaN;
		placedRows = 0;
		int shownRows = countShownRows(dragged);
		ensureRowRoom(shownRows + 1);
		// rows are walked from the bottom; placed rows are stored from the top down afterwards, so count them first
		int placedFromBottom = 0;
		slotShift = 0f;
		boolean slotAsRow = dropRow >= 0 && !drop.share();
		if (slotAsRow && dropRow == shownRows) {
			// a new row right above the hotbar
			y = placeSlotRow(left, width, y, gap, s, dragged);
			top = y + gap;
		}
		int shownIndex = shownRows;
		for (int r = rows.size() - 1; r >= 0; r--) {
			List<String> row = rows.get(r);
			int count = 0;
			boolean xpRow = false;
			int rowHeight = 0;
			for (String id : row) {
				StatusBar bar = bar(Kind.byId(id));
				if (bar.present && slots[bar.kind.ordinal()] != dragged) {
					count++;
					xpRow |= bar.kind == Kind.XP;
					rowHeight = Math.max(rowHeight, barHeight(bar.kind));
				}
			}
			if (count == 0) {
				continue;
			}
			shownIndex--;
			boolean sharedHere = drop.share() && dropRow == shownIndex;
			if (sharedHere) {
				rowHeight = Math.max(rowHeight, barHeight(dragged.kind));
			}
			float h = rowHeight * s;
			float rowTop = y - h;
			int columns = count + (sharedHere ? 1 : 0);
			float room = width - (columns - 1) * gap;
			// a row splits by its bars' weights; while a bar is dropped into it, all share it equally
			float weightSum = 0f;
			for (String id : row) {
				StatusBar bar = bar(Kind.byId(id));
				if (bar.present && slots[bar.kind.ordinal()] != dragged) {
					weightSum += sharedHere ? 1f : weightOf(id);
				}
			}
			weightSum += sharedHere ? 1f : 0f;
			float cursor = left;
			if (sharedHere && drop.left()) {
				showSlot(cursor, rowTop, room / columns, h, dragged);
				cursor += room / columns + gap;
			}
			for (String id : row) {
				StatusBar bar = bar(Kind.byId(id));
				if (!bar.present || slots[bar.kind.ordinal()] == dragged) {
					continue;
				}
				float share = room * (sharedHere ? 1f : weightOf(id)) / weightSum;
				bar.stacked = true;
				bar.targetX = cursor;
				bar.targetWidth = share;
				bar.targetHeight = barHeight(bar.kind) * s;
				bar.targetY = rowTop + (h - bar.targetHeight) / 2f;
				bar.targetScale = s;
				cursor += share + gap;
			}
			if (sharedHere && !drop.left()) {
				showSlot(cursor, rowTop, room / columns, h, dragged);
			}
			// the rows where they rest without the empty slot: the drop is found from these, so the slot opening does not move the target
			rowTops[placedFromBottom] = rowTop + slotShift;
			rowBottoms[placedFromBottom] = y + slotShift;
			rowShares[placedFromBottom] = !xpRow && count < BarStackLayout.MOST_PER_ROW;
			rowIndex[placedFromBottom] = r;
			placedFromBottom++;
			y = rowTop - gap;
			top = rowTop;
			if (slotAsRow && dropRow == shownIndex) {
				y = placeSlotRow(left, width, y, gap, s, dragged);
				top = y + gap;
			}
		}
		placedRows = placedFromBottom;
		reverseRows();
		if (dropRow < 0) {
			slotShown = false;
		}
		stackTopNow = top;
		placeSide(BarStackLayout.LEFT, guiHeight, dragged);
		placeSide(BarStackLayout.RIGHT, guiHeight, dragged);

		for (StatusBar bar : bars) {
			boolean placed = BarStackLayout.contains(rows, bar.kind.id) || sideOfBar[bar.kind.ordinal()] != BarStackLayout.NO_SIDE;
			if (!bar.present || placed && slots[bar.kind.ordinal()] != dragged) {
				continue;
			}
			// a floating bar: at its own place and size
			Slot slot = slots[bar.kind.ordinal()];
			bar.stacked = false;
			float size = slot.scale.get();
			bar.targetScale = size;
			bar.targetWidth = slot.floatWidth() * size;
			bar.targetHeight = barHeight(bar.kind) * size;
			bar.targetX = guiWidth / 2f + slot.x.get() - bar.targetWidth / 2f;
			bar.targetY = guiHeight - slot.y.get() - bar.targetHeight;
		}
		if (dragged != null) {
			// the dragged bar follows the mouse at the size it was picked up with, and turns compact as it gets close to a spot beside the hotbar
			StatusBar bar = bar(dragged.kind);
			bar.stacked = false;
			float near = dragSide == BarStackLayout.NO_SIDE ? 0f : dragNear;
			bar.targetWidth = Mth.lerp(near, dragWidth, sideWidthNow);
			bar.targetHeight = Mth.lerp(near, dragHeight, barHeight(dragged.kind) * s * COMPACT);
			bar.targetScale = Mth.lerp(near, dragScale, s * COMPACT);
			// it stays under the mouse at the place it was taken by
			bar.targetX = dragX + grabX - grabX / Math.max(1f, dragWidth) * bar.targetWidth;
			bar.targetY = dragY + grabY - grabY / Math.max(1f, dragHeight) * bar.targetHeight;
		}
		boolean animated = ModFeatures.MENU.animations();
		for (StatusBar bar : bars) {
			boolean snap = !animated || !bar.placed || slots[bar.kind.ordinal()] == dragged;
			bar.shown.follow(bar.present ? 1f : 0f);
			if (!animated) {
				bar.shown.snap(bar.present ? 1f : 0f);
			}
			if (!bar.present) {
				if (bar.shown.value() < 0.01f) {
					bar.placed = false;
				}
				continue;
			}
			if (snap) {
				bar.x.snap(bar.targetX);
				bar.y.snap(bar.targetY);
				bar.width.snap(bar.targetWidth);
				bar.height.snap(bar.targetHeight);
				bar.placed = true;
			} else {
				bar.x.follow(bar.targetX);
				bar.y.follow(bar.targetY);
				bar.width.follow(bar.targetWidth);
				bar.height.follow(bar.targetHeight);
			}
			bar.drawnScale = bar.targetScale;
			bar.bounds[0] = Math.round(bar.x.value());
			bar.bounds[1] = Math.round(bar.y.value());
			bar.bounds[2] = Math.round(bar.width.value());
			bar.bounds[3] = Math.round(bar.height.value());
		}
		if (slotShown) {
			if (!animated || !slotPlaced) {
				slotX.snap(slotTargetX);
				slotY.snap(slotTargetY);
				slotWidth.snap(slotTargetWidth);
				slotPlaced = true;
			} else {
				slotX.follow(slotTargetX);
				slotY.follow(slotTargetY);
				slotWidth.follow(slotTargetWidth);
			}
		} else {
			slotPlaced = false;
		}
	}

	/** Finds out how wide the bars beside the hotbar are now and whether each side fits on the screen. */
	private void measureSides(int guiWidth) {
		float s = hotbar[3];
		float gap = SIDE_GAP * s;
		float leftRoom = hotbar[0] - HudStack.offhandRoom(true) - gap - SCREEN_MARGIN;
		float rightRoom = guiWidth - (hotbar[0] + hotbar[2]) - HudStack.offhandRoom(false) - gap - SCREEN_MARGIN;
		float least = MIN_BAR_WIDTH * s;
		sideFits[BarStackLayout.LEFT] = leftRoom >= least;
		sideFits[BarStackLayout.RIGHT] = rightRoom >= least;
		float width = sideWidth.get() * s;
		width = sideFits[BarStackLayout.LEFT] ? Math.min(width, leftRoom) : width;
		sideWidthNow = sideFits[BarStackLayout.RIGHT] ? Math.min(width, rightRoom) : width;
		sideX[BarStackLayout.LEFT] = BarStackLayout.sideX(BarStackLayout.LEFT, hotbar[0], hotbar[2], HudStack.offhandRoom(true), gap, sideWidthNow);
		sideX[BarStackLayout.RIGHT] = BarStackLayout.sideX(BarStackLayout.RIGHT, hotbar[0], hotbar[2], HudStack.offhandRoom(false), gap, sideWidthNow);
	}

	/** Places a side's bars, centered on the hotbar, and the empty slot where a dragged bar would land. */
	private void placeSide(int side, int guiHeight, Slot dragged) {
		float compact = hotbar[3] * COMPACT;
		float gap = HudStack.GAP * hotbar[3];
		float x = sideX[side];
		float hotbarHeight = HudStack.hotbarHeight();
		float middle = hotbar[1] + hotbarHeight / 2f;
		// where the bars rest without the slot: the drop is found from these, so the slot opening does not move the target
		int count = 0;
		float restHeight = 0f;
		boolean fits = sideFits[side];
		for (String id : sides.get(side)) {
			StatusBar bar = bar(Kind.byId(id));
			if (fits && bar.present && slots[bar.kind.ordinal()] != dragged) {
				restHeight += (count == 0 ? 0f : gap) + barHeight(bar.kind) * compact;
				count++;
			}
		}
		restCount[side] = count;
		float y = sideTop(middle, restHeight, guiHeight);
		restTop[side] = y;
		int index = 0;
		for (String id : sides.get(side)) {
			StatusBar bar = bar(Kind.byId(id));
			if (fits && bar.present && slots[bar.kind.ordinal()] != dragged) {
				float height = barHeight(bar.kind) * compact;
				restMiddles[side][index++] = y + height / 2f;
				y += height + gap;
			}
		}
		boolean slotHere = dragged != null && dragSide == side;
		float slotH = slotHere ? barHeight(dragged.kind) * compact : 0f;
		float total = restHeight + (slotHere && count > 0 ? gap : 0f) + slotH;
		float top = sideTop(middle, total, guiHeight);
		y = top;
		index = 0;
		for (String id : sides.get(side)) {
			StatusBar bar = bar(Kind.byId(id));
			if (!fits || !bar.present || slots[bar.kind.ordinal()] == dragged) {
				continue;
			}
			if (slotHere && index == dragSideIndex) {
				showSlot(x, y, sideWidthNow, slotH, dragged);
				y += slotH + gap;
			}
			bar.stacked = false;
			bar.targetX = x;
			bar.targetY = y;
			bar.targetWidth = sideWidthNow;
			bar.targetHeight = barHeight(bar.kind) * compact;
			bar.targetScale = compact;
			y += bar.targetHeight + gap;
			index++;
		}
		if (slotHere && dragSideIndex >= index) {
			showSlot(x, y, sideWidthNow, slotH, dragged);
		}
		float[] spot = spots[side];
		spot[0] = x;
		spot[1] = Math.min(top, hotbar[1]);
		spot[2] = sideWidthNow;
		spot[3] = Math.max(top + total, hotbar[1] + hotbarHeight) - spot[1];
	}

	/** @return the top of a group of bars centered on the hotbar, kept on the screen */
	private static float sideTop(float middle, float height, int guiHeight) {
		return BarStackLayout.sideTop(middle, height, guiHeight, SCREEN_MARGIN);
	}

	/** @return whether a side takes the dragged bar now */
	private boolean sideOpen(int side) {
		return sideFits[side] && restCount[side] < BarStackLayout.MOST_PER_SIDE;
	}

	/** How far the empty slot row pushed the rows above it up. */
	private float slotShift;
	private float slotTargetX;
	private float slotTargetY;
	private float slotTargetWidth;

	private void showSlot(float x, float y, float width, float height, Slot dragged) {
		slotShown = true;
		slotTargetX = x;
		slotTargetY = y;
		slotTargetWidth = width;
		slotHeight = height;
	}

	/** Places the empty slot as a row of its own below y. @return the y above it */
	private float placeSlotRow(float left, float width, float y, float gap, float s, Slot dragged) {
		float h = barHeight(dragged.kind) * s;
		showSlot(left, y - h, width, h, dragged);
		slotShift += h + gap;
		return y - h - gap;
	}

	/** @return how many rows of the stack have a bar shown now, not counting the dragged one */
	private int countShownRows(Slot dragged) {
		int count = 0;
		for (List<String> row : rows) {
			for (String id : row) {
				StatusBar bar = bar(Kind.byId(id));
				if (bar.present && slots[bar.kind.ordinal()] != dragged) {
					count++;
					break;
				}
			}
		}
		return count;
	}

	private void ensureRowRoom(int count) {
		if (rowTops.length < count) {
			rowTops = new float[count];
			rowBottoms = new float[count];
			rowShares = new boolean[count];
			rowIndex = new int[count];
		}
	}

	/** Turns the rows placed from the bottom up into rows from the top down, as the drop search expects them. */
	private void reverseRows() {
		for (int i = 0, j = placedRows - 1; i < j; i++, j--) {
			float top = rowTops[i];
			rowTops[i] = rowTops[j];
			rowTops[j] = top;
			float bottom = rowBottoms[i];
			rowBottoms[i] = rowBottoms[j];
			rowBottoms[j] = bottom;
			boolean shares = rowShares[i];
			rowShares[i] = rowShares[j];
			rowShares[j] = shares;
			int index = rowIndex[i];
			rowIndex[i] = rowIndex[j];
			rowIndex[j] = index;
		}
	}

	// ---------------------------------------------------------------------------------------------------------- drawing

	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft client = Minecraft.getInstance();
		if (!isEnabled() || client.level == null || client.player == null) {
			HudStack.setBarsTop(Float.NaN);
			return;
		}
		layoutOnce(client, graphics.guiWidth(), graphics.guiHeight());
		boolean editing = HudEditScreen.isOpen();
		graphics.nextStratum();
		Ui.setFontOverride(Boolean.FALSE);
		try {
			HudStyle.setPanelTheme(panelTheme.get().theme());
			Ui ui = new Ui(graphics, client.font, HudStyle.palette(), accentArgb(), 0f, false);
			drawLowHealthEdge(ui, graphics, editing);
			if (editing) {
				drawEditorChrome(ui);
			}
			for (StatusBar bar : bars) {
				Slot slot = slots[bar.kind.ordinal()];
				slot.parts.clear();
				if (bar.shown.value() > 0.01f && bar.placed) {
					drawBar(ui, bar, slot);
				}
			}
		} finally {
			Ui.setFontOverride(null);
		}
	}

	private int accentArgb() {
		return ModFeatures.MENU == null ? SystemColors.BLUE : ModFeatures.MENU.accentArgb();
	}

	private void drawBar(Ui ui, StatusBar bar, Slot slot) {
		float s = bar.drawnScale;
		float width = bar.width.value() / s;
		float alpha = Mth.clamp(bar.shown.value(), 0f, 1f);
		boolean slim = slim();
		float low = 0f;
		if (bar.kind == Kind.HEALTH && bar.realFraction < LOW_HEALTH && bar.realFraction > 0f) {
			low = pulse();
		}
		var pose = ui.graphics.pose();
		// on whole screen pixels, so the bar, its pill and its text share the pixel grid and nothing sits half a pixel off
		float pixel = Ui.screenPixel();
		float originX = Math.round(bar.x.value() / pixel) * pixel;
		float originY = Math.round(bar.y.value() / pixel) * pixel;
		pose.pushMatrix();
		Ui.setZoom(s);
		try {
			pose.translate(originX, originY);
			pose.scale(s, s);
			int color = colorOf(bar.kind);
			if (bar.kind == Kind.SPEED && bar.realFraction >= 1f) {
				color = Ui.mix(color, 0xFFFFFFFF, SPEED_CAP_BRIGHTER);
			}
			if (bar.kind == Kind.XP) {
				if (slim) {
					StatusBarPainter.slimXp(ui, bar, width, color, alpha);
				} else {
					StatusBarPainter.standardXp(ui, bar, width, color, alpha);
				}
			} else {
				boolean health = bar.kind == Kind.HEALTH;
				int extraColor = health ? absorptionColor.argb() : overflowColor.argb();
				float defense = health ? HudParts.alpha(showDefense.get()) : 0f;
				float max = bar.kind == Kind.ARROWS || bar.kind == Kind.SPEED ? 1f : HudParts.alpha(maximum.get());
				if (slim) {
					StatusBarPainter.slim(ui, bar, width, color, extraColor, health ? ABSORPTION_TEXT : OVERFLOW_TEXT, low, defense, max, alpha);
				} else {
					StatusBarPainter.standard(ui, bar, width, color, extraColor, health ? ABSORPTION_TEXT : OVERFLOW_TEXT, low, defense, max, alpha);
				}
				if (HudEditScreen.isOpen()) {
					addParts(ui, bar, slot, width, s);
				}
			}
		} finally {
			pose.popMatrix();
			Ui.setZoom(1f);
		}
	}

	/** Records the parts of a bar that a click in the editor switches: the defense pill and the maximum. */
	private void addParts(Ui ui, StatusBar bar, Slot slot, float width, float s) {
		float x = bar.x.value();
		float y = bar.y.value();
		float h = bar.height.value();
		if (!slim() && bar.kind == Kind.HEALTH) {
			float[] pill = StatusBarPainter.defensePill(ui, bar, width);
			if (pill != null) {
				slot.parts.add("Defense", showDefense, x + pill[0] * s, y, pill[1] * s, h);
			}
		}
		if (!bar.maxText.isEmpty()) {
			// the maximum follows the value; its place is close enough for a click
			float valueEnd = 12.5f + ui.boldWidthScaled(bar.valueText, 0.72f);
			slot.parts.add("Maximum", maximum, x + valueEnd * s, y, ui.boldWidthScaled(bar.maxText, 0.6f) * s + 4f, h);
		}
	}

	private int colorOf(Kind kind) {
		return switch (kind) {
			case HEALTH -> healthColor.argb();
			case MANA -> manaColor.argb();
			case FOOD -> foodColor.argb();
			case AIR -> airColor.argb();
			case VITALITY -> vitalityColor.argb();
			case FUEL -> fuelColor.argb();
			case ARROWS -> arrowsColor.argb();
			case SPEED -> speedColor.argb();
			case XP -> xpColor.argb();
		};
	}

	/** @return the low health pulse now: one gentle swell per second, from 0 to 1 */
	private static float pulse() {
		if (!ModFeatures.MENU.animations()) {
			return 0.6f;
		}
		double seconds = System.nanoTime() / 1_000_000_000.0;
		return (float) (0.5 + 0.5 * Math.sin(seconds * PULSE_PER_SECOND));
	}

	/** Draws the very subtle red screen edge at low health, pulsing with the bar. */
	private void drawLowHealthEdge(Ui ui, GuiGraphicsExtractor graphics, boolean editing) {
		StatusBar health = bar(Kind.HEALTH);
		if (editing || !lowHealthEdge.get() || !health.present || health.realFraction >= LOW_HEALTH || health.realFraction <= 0f) {
			return;
		}
		int width = graphics.guiWidth();
		int height = graphics.guiHeight();
		int depth = Math.max(4, Math.round(Math.min(width, height) * EDGE_DEPTH));
		int edge = Ui.withOpacity(SystemColors.RED, EDGE_OPACITY + EDGE_PULSE * pulse());
		int clear = edge & 0x00FFFFFF;
		ui.verticalGradient(0, 0, width, depth, edge, clear);
		ui.verticalGradient(0, height - depth, width, depth, clear, edge);
		ui.horizontalGradient(0, 0, depth, height, edge, clear);
		ui.horizontalGradient(width - depth, 0, depth, height, clear, edge);
	}

	/** In the editor: the chip naming the stack beside its top row, and the soft slot where a dragged bar lands. */
	private void drawEditorChrome(Ui ui) {
		if (!Float.isNaN(stackTopNow)) {
			String label = "Bars";
			float s = hotbar[3];
			float chipWidth = ui.width(label) + 18f;
			// beside the top row on the left: above the stack sit the item name and the action bar
			var pose = ui.graphics.pose();
			pose.pushMatrix();
			pose.translate(hotbar[0] - (chipWidth + 4f) * s, stackTopNow);
			pose.scale(s, s);
			ui.roundedSmooth(0f, 0f, chipWidth, CHIP_HEIGHT, Radius.SM, 0x1FFFFFFF);
			ui.icon("move", 4, (CHIP_HEIGHT - 7) / 2, 7, 0xB3FFFFFF);
			ui.textMiddle(label, 13, CHIP_HEIGHT / 2, 0xD9FFFFFF);
			pose.popMatrix();
		}
		if (dragging != null) {
			drawSpots(ui);
		}
		if (slotShown) {
			boolean beside = dragSide != BarStackLayout.NO_SIDE;
			ui.roundedSmooth(slotX.value(), slotY.value(), slotWidth.value(), slotHeight, 4f * hotbar[3], beside ? SPOT_LIT : SLOT_FILL);
		}
	}

	/** While a bar is dragged: the free spots above and beside the hotbar. */
	private void drawSpots(Ui ui) {
		float s = hotbar[3];
		float height = barHeight(Kind.HEALTH) * s * COMPACT;
		float gap = HudStack.GAP * s;
		float radius = 4f * s;
		for (int side = BarStackLayout.LEFT; side <= BarStackLayout.RIGHT; side++) {
			if (!sideOpen(side) || dragSide == side) {
				continue;
			}
			int count = restCount[side];
			// an empty side shows the two rows it usually holds; a side with bars shows the next row on top of them
			int free = count == 0 ? 2 : 1;
			float total = free * height + (free - 1) * gap;
			float y = count == 0 ? hotbar[1] + HudStack.hotbarHeight() / 2f - total / 2f : restTop[side] - gap - height;
			for (int i = 0; i < free; i++) {
				drawSpot(ui, sideX[side], y + i * (height + gap), sideWidthNow, height, radius);
			}
		}
		var pose = ui.graphics.pose();
		if (dragSide == BarStackLayout.NO_SIDE && !dragInStack) {
			float top = (Float.isNaN(stackTopNow) ? hotbar[1] : stackTopNow) - gap - height;
			drawSpot(ui, stackLeft, top, stackWidthNow, height, radius);
			pose.pushMatrix();
			pose.translate(stackLeft + stackWidthNow / 2f, top + height / 2f);
			pose.scale(s, s);
			ui.textCentered(ABOVE_LABEL, 0, 0, SPOT_LABEL);
			pose.popMatrix();
		}
	}

	/** A free spot: a soft fill with a fine edge, so it shows over a bright world as well as a dark one. */
	private static void drawSpot(Ui ui, float x, float y, float width, float height, float radius) {
		ui.roundedSmooth(x, y, width, height, radius, SPOT_FILL);
		ui.ring(Math.round(x), Math.round(y), Math.round(width), Math.round(height), radius, 1, SPOT_EDGE);
	}

	// ---------------------------------------------------------------------------------------------------------- dragging

	/** The bar being dragged in the editor, where it is drawn, and where it would land. */
	private Slot dragging;
	private float dragX;
	private float dragY;
	private float dragWidth;
	private float dragHeight;
	private float dragScale;
	private boolean dragInStack;
	private BarStackLayout.Drop dragDrop = BarStackLayout.Drop.NONE;
	/** Where the mouse holds the dragged bar, from its corner at the size it was picked up with. */
	private float grabX;
	private float grabY;
	/** The spot beside the hotbar the dragged bar would land in, where among the bars there, and how close it is (1 fully compact). */
	private int dragSide = BarStackLayout.NO_SIDE;
	private int dragSideIndex;
	private float dragNear;
	/** The spots beside the hotbar this frame: left edge, each spot as {x, y, width, height}, and where the bars rest without a dragged one. */
	private final float[] sideX = new float[2];
	private final float[][] spots = new float[2][4];
	private final float[][] restMiddles = new float[2][BarStackLayout.MOST_PER_SIDE];
	private final float[] restTop = new float[2];
	private final int[] restCount = new int[2];
	private float sideWidthNow;
	private final float[] aboveSpot = new float[4];

	// ---------------------------------------------------------------------------------------------------------- widths

	/** The stack's left edge and width this frame, in GUI pixels. */
	private float stackLeft;
	private float stackWidthNow;

	private void parseWeights() {
		if (rowWeights.get().equals(parsedWeights)) {
			return;
		}
		parsedWeights = rowWeights.get();
		weights.clear();
		for (String part : parsedWeights.split(",")) {
			int colon = part.indexOf(':');
			if (colon <= 0 || part.length() - colon > 8) {
				continue;
			}
			String id = part.substring(0, colon).strip();
			try {
				float weight = Float.parseFloat(part.substring(colon + 1).strip());
				if (IDS.contains(id) && Float.isFinite(weight)) {
					weights.put(id, Mth.clamp(weight, MIN_WEIGHT, MOST_WEIGHT));
				}
			} catch (NumberFormatException ignored) {
				// a broken entry is left out; the bar then has the equal share
			}
		}
	}

	private float weightOf(String id) {
		Float weight = weights.get(id);
		return weight == null ? 1f : weight;
	}

	private void saveWeights() {
		StringBuilder text = new StringBuilder();
		for (Map.Entry<String, Float> entry : weights.entrySet()) {
			if (Math.abs(entry.getValue() - 1f) > 0.005f) {
				text.append(text.isEmpty() ? "" : ",").append(entry.getKey()).append(':').append(Math.round(entry.getValue() * 1000f) / 1000f);
			}
		}
		rowWeights.set(text.toString());
		parseWeights();
	}

	/** The weights a bar's share of its row may have, so neither bar of a split gets too narrow. */
	private static final float MIN_WEIGHT = 0.2f;
	private static final float MOST_WEIGHT = 5f;

	/** @return the narrowest the stack may be, in GUI pixels at size 1: its fullest row at the narrowest bars */
	private float minStackWidth() {
		int most = 1;
		for (List<String> row : rows) {
			most = Math.max(most, row.size());
		}
		return most * MIN_BAR_WIDTH + (most - 1) * HudStack.GAP;
	}

	/** What an edge of a bar sets when dragged: the stack's width, the split with the bar beside it, or the width of a floating bar. */
	private enum WidthEdge {
		STACK, SPLIT, FREE, SIDE
	}

	private WidthEdge widthEdge = WidthEdge.FREE;
	/** For a split: the bar on the left of the border and the one on the right, and their widths when the drag started. */
	private StatusBar splitLeft;
	private StatusBar splitRight;
	private float splitTotal;
	private float widthFixed;

	/** @return the shown bar beside a stacked bar in its row (-1 left, 1 right), or null at the row's end */
	private StatusBar neighbor(Kind kind, int side) {
		int row = BarStackLayout.rowOf(rows, kind.id);
		if (row < 0) {
			return null;
		}
		List<String> ids = rows.get(row);
		int at = ids.indexOf(kind.id);
		for (int i = at + side; i >= 0 && i < ids.size(); i += side) {
			StatusBar bar = bar(Kind.byId(ids.get(i)));
			if (bar.present) {
				return bar;
			}
		}
		return null;
	}

	private void widthStart(Slot slot, boolean right) {
		StatusBar bar = bar(slot.kind);
		int side = sideOfBar[slot.kind.ordinal()];
		if (side != BarStackLayout.NO_SIDE) {
			// only the outer end moves: the inner one stays lined up at the hotbar
			widthEdge = WidthEdge.SIDE;
			widthFixed = side == BarStackLayout.LEFT ? bar.x.value() + bar.width.value() : bar.x.value();
			return;
		}
		if (!slot.stacked()) {
			widthEdge = WidthEdge.FREE;
			widthFixed = right ? bar.x.value() : bar.x.value() + bar.width.value();
			return;
		}
		StatusBar other = neighbor(slot.kind, right ? 1 : -1);
		if (other == null) {
			widthEdge = WidthEdge.STACK;
			return;
		}
		widthEdge = WidthEdge.SPLIT;
		splitLeft = right ? bar : other;
		splitRight = right ? other : bar;
		splitTotal = splitLeft.targetWidth + splitRight.targetWidth;
		widthFixed = splitLeft.targetX;
	}

	private String dragWidth(Slot slot, boolean right, float mouseX, boolean free) {
		float s = Math.max(0.01f, hotbar[3]);
		float reach = WidthSnap.REACH;
		switch (widthEdge) {
			case STACK -> {
				// both edges move: the stack stays centered on the hotbar
				float wanted = 2f * Math.abs(mouseX - (hotbar[0] + hotbar[2] / 2f));
				if (!free) {
					wanted = WidthSnap.snap(wanted, reach, hotbar[2], hotbar[2] / 2f);
				}
				if (!free && Math.abs(wanted - hotbar[2]) < 0.5f) {
					stackWidthMode.set(StackWidth.MATCH_HOTBAR);
					return "Match hotbar";
				}
				stackWidthMode.set(StackWidth.CUSTOM);
				stackWidth.set(Mth.clamp((float) Math.round(Math.max(minStackWidth(), wanted / s)), stackWidth.min(), stackWidth.max()));
				return WidthSnap.label(stackWidth.get());
			}
			case SPLIT -> {
				float least = MIN_BAR_WIDTH * s;
				float leftWidth = Mth.clamp(mouseX - widthFixed, least, splitTotal - least);
				if (!free) {
					leftWidth = WidthSnap.snap(leftWidth, reach, splitTotal / 2f);
				}
				float both = weightOf(splitLeft.kind.id) + weightOf(splitRight.kind.id);
				weights.put(splitLeft.kind.id, both * leftWidth / splitTotal);
				weights.put(splitRight.kind.id, both * (splitTotal - leftWidth) / splitTotal);
				saveWeights();
				return Math.abs(leftWidth - splitTotal / 2f) < 0.5f ? "Equal" : Math.round(100f * leftWidth / splitTotal) + " %";
			}
			case SIDE -> {
				float wanted = Math.abs(mouseX - widthFixed) / s;
				if (!free) {
					wanted = WidthSnap.snap(wanted, reach / s, SIDE_WIDTH);
				}
				sideWidth.set(Mth.clamp((float) Math.round(wanted), sideWidth.min(), sideWidth.max()));
				return WidthSnap.label(sideWidth.get());
			}
			default -> {
				float size = Math.max(0.01f, slot.scale.get());
				float wanted = Math.abs(mouseX - widthFixed) / size;
				if (!free) {
					wanted = WidthSnap.snap(wanted, reach / size, FLOAT_WIDTH, hotbar[2] / s, hotbar[2] / s / 2f);
				}
				float least = slot.kind == Kind.XP ? MIN_XP_WIDTH : MIN_BAR_WIDTH;
				wanted = Math.max(least, wanted);
				slot.freeWidth.set(!free && Math.abs(wanted - FLOAT_WIDTH) < 0.5f ? 0f : Mth.clamp((float) Math.round(wanted), least, slot.freeWidth.max()));
				// the edge not dragged stays where it was: the bar is placed from its center
				float drawn = slot.floatWidth() * size;
				float center = right ? widthFixed + drawn / 2f : widthFixed - drawn / 2f;
				slot.x.set(Mth.clamp(center - slot.lastGuiWidth / 2f, slot.x.min(), slot.x.max()));
				return WidthSnap.label(slot.floatWidth());
			}
		}
	}

	private void resetWidth(Slot slot, boolean right) {
		widthStart(slot, right);
		switch (widthEdge) {
			case STACK -> {
				stackWidthMode.reset();
				stackWidth.reset();
			}
			case SPLIT -> {
				weights.remove(splitLeft.kind.id);
				weights.remove(splitRight.kind.id);
				saveWeights();
			}
			case SIDE -> sideWidth.reset();
			default -> slot.freeWidth.reset();
		}
	}

	/** Moves the dragged bar and finds out whether it is near enough to the stack to land in it, and where. */
	private void dragTo(float x, float y) {
		dragX = x;
		dragY = y;
		float left = stackLeft;
		float right = stackLeft + stackWidthNow;
		float top = placedRows > 0 ? rowTops[0] : hotbar[1] - (HudStack.GAP + barHeight(dragging.kind)) * hotbar[3];
		float bottom = hotbar[1];
		float dx = Math.max(0f, Math.max(left - (x + dragWidth), x - right));
		float dy = Math.max(0f, Math.max(top - (y + dragHeight), y - bottom));
		// the spots beside the hotbar go by the mouse: a wide bar taken by its end would otherwise reach them from far away
		float mouseX = x + grabX;
		float mouseY = y + grabY;
		aboveSpot[0] = left;
		aboveSpot[1] = top;
		aboveSpot[2] = right - left;
		aboveSpot[3] = bottom - top;
		float reach = SIDE_REACH * hotbar[3];
		dragSide = BarStackLayout.nearestSide(mouseX, mouseY, sideOpen(BarStackLayout.LEFT) ? spots[BarStackLayout.LEFT] : null,
			sideOpen(BarStackLayout.RIGHT) ? spots[BarStackLayout.RIGHT] : null, aboveSpot, reach);
		if (dragSide != BarStackLayout.NO_SIDE) {
			float full = SIDE_FULL_REACH * hotbar[3];
			dragNear = Mth.clamp(1f - (BarStackLayout.distance(spots[dragSide], mouseX, mouseY) - full) / Math.max(1f, reach - full), 0f, 1f);
			dragSideIndex = BarStackLayout.sideIndexAt(restMiddles[dragSide], restCount[dragSide], mouseY);
			dragInStack = false;
			dragDrop = BarStackLayout.Drop.NONE;
			return;
		}
		dragInStack = Math.hypot(dx, dy) <= STACK_REACH * hotbar[3];
		if (!dragInStack) {
			dragDrop = BarStackLayout.Drop.NONE;
			return;
		}
		boolean[] shares = rowShares;
		if (dragging.kind == Kind.XP) {
			// the thin experience bar keeps a row of its own
			shares = new boolean[placedRows];
		}
		BarStackLayout.Drop drop = BarStackLayout.dropAt(rowTops, rowBottoms, placedRows, left, stackWidthNow, shares, x + dragWidth / 2f,
			y + dragHeight / 2f, stackWidthNow * SHARE_EDGE);
		dragDrop = drop;
	}

	/** Lands the dragged bar: in the stack where its slot shows, or floating where it was let go. */
	private void drop() {
		Slot slot = dragging;
		List<List<String>> changed = BarStackLayout.copy(rows);
		// what only shows in the stack for lack of room stays on its side, and comes back there when the room is back
		List<String> left = new ArrayList<>(sides.get(BarStackLayout.LEFT));
		List<String> right = new ArrayList<>(sides.get(BarStackLayout.RIGHT));
		left.remove(slot.kind.id);
		right.remove(slot.kind.id);
		left.forEach(id -> BarStackLayout.remove(changed, id));
		right.forEach(id -> BarStackLayout.remove(changed, id));
		boolean beside = dragSide != BarStackLayout.NO_SIDE;
		if (beside) {
			BarStackLayout.remove(changed, slot.kind.id);
			List<String> side = dragSide == BarStackLayout.LEFT ? left : right;
			side.add(savedSideIndex(side), slot.kind.id);
			weights.remove(slot.kind.id);
			saveWeights();
		} else if (dragInStack && dragDrop != BarStackLayout.Drop.NONE) {
			// the drop counts the rows shown now; map it to the saved rows, which may hold rows without a bar shown now
			BarStackLayout.Drop saved = savedDrop(changed, slot.kind.id);
			BarStackLayout.apply(changed, saved, slot.kind.id);
			// the row it joins splits equally again
			int joined = BarStackLayout.rowOf(changed, slot.kind.id);
			for (String id : changed.get(joined)) {
				weights.remove(id);
			}
			saveWeights();
		} else {
			BarStackLayout.remove(changed, slot.kind.id);
			int guiWidth = slot.lastGuiWidth;
			int guiHeight = slot.lastGuiHeight;
			// it keeps the size of the stack it came from; floating, it is as wide as a bar sharing a row
			float size = Mth.clamp(dragScale, slot.scale.min(), slot.scale.max());
			slot.scale.set(size);
			dragWidth = FLOAT_WIDTH * size;
			slot.x.set(Mth.clamp(dragX + dragWidth / 2f - guiWidth / 2f, slot.x.min(), slot.x.max()));
			slot.y.set(Mth.clamp(guiHeight - dragY - dragHeight, slot.y.min(), slot.y.max()));
		}
		stack.set(BarStackLayout.format(changed));
		sideLeft.set(BarStackLayout.formatSide(left));
		sideRight.set(BarStackLayout.formatSide(right));
		if (!beside) {
			StatusBar bar = bar(slot.kind);
			// everything glides into place from where it is now
			bar.x.snap(dragX);
			bar.y.snap(dragY);
			bar.width.snap(dragWidth);
			bar.height.snap(dragHeight);
		}
		dragging = null;
		dragSide = BarStackLayout.NO_SIDE;
		dragInStack = false;
		dragDrop = BarStackLayout.Drop.NONE;
		slotShown = false;
	}

	/** @return where in a side's saved list the bar goes: the drop counts the bars shown now, and the list may hold bars not shown now */
	private int savedSideIndex(List<String> side) {
		int shown = 0;
		for (int i = 0; i < side.size(); i++) {
			if (shown == dragSideIndex) {
				return i;
			}
			if (bar(Kind.byId(side.get(i))).present) {
				shown++;
			}
		}
		return side.size();
	}

	/** @return the drop translated from the rows shown now to the saved rows (without the dragged bar) */
	private BarStackLayout.Drop savedDrop(List<List<String>> saved, String id) {
		BarStackLayout.remove(saved, id);
		int shown = dragDrop.row();
		if (dragDrop.share()) {
			int row = rowIndexWithout(saved, shown);
			return new BarStackLayout.Drop(row, true, dragDrop.left());
		}
		// a new row: before the saved row shown at that index, or after the last one shown
		if (shown >= placedRows) {
			int last = placedRows == 0 ? -1 : rowIndexWithout(saved, placedRows - 1);
			return new BarStackLayout.Drop(last + 1, false, false);
		}
		return new BarStackLayout.Drop(rowIndexWithout(saved, shown), false, false);
	}

	/** @return the index in the saved rows (without the dragged bar) of the row shown at an index */
	private int rowIndexWithout(List<List<String>> saved, int shown) {
		int original = rowIndex[Mth.clamp(shown, 0, Math.max(0, placedRows - 1))];
		if (original >= rows.size()) {
			return saved.size();
		}
		// the same row list in the copy: find it by its first bar that is not the dragged one
		List<String> row = rows.get(original);
		for (String id : row) {
			int at = BarStackLayout.rowOf(saved, id);
			if (at >= 0) {
				return at;
			}
		}
		return Math.min(original, saved.size());
	}

	// ------------------------------------------------------------------------------------------------- the bars in the editor

	/** A bar as an element of the HUD editor: dragged into, within and out of the stack, or placed freely when it floats. */
	private final class Slot implements EditableHud {
		final Kind kind;
		final FloatSetting x;
		final FloatSetting y;
		final FloatSetting scale;
		/** The width of the bar while it floats, in GUI pixels at size 1, or 0 for the standard width. */
		final FloatSetting freeWidth;
		final HudParts parts = new HudParts();
		/** The editor's list asked for this bar while it is not active here. */
		boolean previewed;
		int lastGuiWidth = 854;
		int lastGuiHeight = 480;

		Slot(Kind kind) {
			this.kind = kind;
			x = add(new FloatSetting(kind.id + "_x", Component.literal(kind.title + " across"), 0f, -4000f, 4000f, 0), "Layout");
			y = add(new FloatSetting(kind.id + "_y", Component.literal(kind.title + " up"), 60f, -4000f, 8000f, 0), "Layout");
			scale = add(new FloatSetting(kind.id + "_scale", Component.literal(kind.title + " size"), 1f, 0.3f, 3f, 2), "Layout");
			freeWidth = add(new FloatSetting(kind.id + "_free_width", Component.literal(kind.title + " width"), 0f, 0f, 800f, 0), "Layout");
			freeWidth.visibleWhen(() -> false);
			x.visibleWhen(() -> false);
			y.visibleWhen(() -> false);
			scale.visibleWhen(() -> false);
		}

		private StatusBar bar() {
			return bars[kind.ordinal()];
		}

		float floatWidth() {
			return freeWidth.get() > 0f ? freeWidth.get() : FLOAT_WIDTH;
		}

		@Override
		public boolean hudResizableWidth() {
			return true;
		}

		@Override
		public void hudWidthStart(boolean right) {
			Minecraft client = Minecraft.getInstance();
			lastGuiWidth = client.getWindow().getGuiScaledWidth();
			lastGuiHeight = client.getWindow().getGuiScaledHeight();
			widthStart(this, right);
		}

		@Override
		public String hudDragWidth(boolean right, float mouseX, boolean free) {
			return dragWidth(this, right, mouseX, free);
		}

		@Override
		public void hudResetWidth(boolean right) {
			resetWidth(this, right);
		}

		private boolean stacked() {
			parseStack();
			return BarStackLayout.contains(rows, kind.id);
		}

		@Override
		public String hudSizeNote() {
			return placed() ? "Size of the hotbar" : null;
		}

		@Override
		public String hudDragStatus() {
			return dragging == this && dragSide != BarStackLayout.NO_SIDE ? SIDE_LABEL : "";
		}

		/** @return whether the bar has a place of its own in the stack or beside the hotbar, rather than floating */
		private boolean placed() {
			parseStack();
			return sideOfBar[kind.ordinal()] != BarStackLayout.NO_SIDE || BarStackLayout.contains(rows, kind.id);
		}

		@Override
		public boolean hudWidthEdge(boolean right) {
			parseStack();
			int side = sideOfBar[kind.ordinal()];
			return side == BarStackLayout.NO_SIDE || right == (side == BarStackLayout.RIGHT);
		}

		@Override
		public String hudName() {
			return kind.title;
		}

		@Override
		public String hudIconName() {
			return kind.icon;
		}

		@Override
		public int hudIconRgb() {
			return colorOf(kind) & 0xFFFFFF;
		}

		@Override
		public boolean hudShown() {
			return isEnabled() && bar().present;
		}

		@Override
		public boolean hudEnabled() {
			return isEnabled() && switchOf(kind).get();
		}

		@Override
		public boolean hudPreviewable() {
			return true;
		}

		@Override
		public void hudPreview(boolean on) {
			previewed = on;
		}

		@Override
		public int[] hudBounds() {
			return bar().bounds;
		}

		@Override
		public HudParts hudParts() {
			return parts;
		}

		@Override
		public void hudDragStart() {
			StatusBar bar = bar();
			Minecraft client = Minecraft.getInstance();
			lastGuiWidth = client.getWindow().getGuiScaledWidth();
			lastGuiHeight = client.getWindow().getGuiScaledHeight();
			dragging = this;
			boolean beside = sideOfBar[kind.ordinal()] != BarStackLayout.NO_SIDE;
			// away from the hotbar, a bar taken from beside it has its usual size again
			dragWidth = beside ? bar.width.value() / COMPACT : bar.width.value();
			dragHeight = beside ? bar.height.value() / COMPACT : bar.height.value();
			dragScale = beside ? bar.drawnScale / COMPACT : bar.drawnScale;
			var window = client.getWindow();
			float mouseX = (float) (client.mouseHandler.xpos() * lastGuiWidth / Math.max(1, window.getScreenWidth()));
			float mouseY = (float) (client.mouseHandler.ypos() * lastGuiHeight / Math.max(1, window.getScreenHeight()));
			grabX = Mth.clamp((mouseX - bar.x.value()) / Math.max(1f, bar.width.value()), 0f, 1f) * dragWidth;
			grabY = Mth.clamp((mouseY - bar.y.value()) / Math.max(1f, bar.height.value()), 0f, 1f) * dragHeight;
			dragX = bar.x.value() + (bar.width.value() - dragWidth) * grabX / Math.max(1f, dragWidth);
			dragY = bar.y.value() + (bar.height.value() - dragHeight) * grabY / Math.max(1f, dragHeight);
			dragTo(dragX, dragY);
		}

		@Override
		public void hudMove(int dx, int dy) {
			if (dragging == this) {
				dragTo(dragX + dx, dragY + dy);
				return;
			}
			if (!placed()) {
				// a floating bar moved with the keys
				x.set(Mth.clamp(x.get() + dx, x.min(), x.max()));
				y.set(Mth.clamp(y.get() - dy, y.min(), y.max()));
			}
		}

		/** A bar follows the mouse as it is: its place in the stack is found from the mouse, not from lines to snap to. */
		@Override
		public boolean hudSnaps() {
			return false;
		}

		@Override
		public void hudDragEnd() {
			if (dragging == this) {
				drop();
			}
		}

		@Override
		public float hudScale() {
			return placed() ? 1f : scale.get();
		}

		@Override
		public void hudSetScale(float value) {
			// a bar in its place takes the hotbar's size; only a floating bar has its own
			if (!placed()) {
				scale.set(Mth.clamp(value, scale.min(), scale.max()));
			}
		}

		@Override
		public float hudAnchorX(int[] box) {
			return box[0] + box[2] / 2f;
		}

		@Override
		public float hudRadius() {
			return 4f * bar().drawnScale;
		}

		@Override
		public float hudScaleAt(int mouseX, int mouseY) {
			int[] box = bar().bounds;
			return (mouseX - (box[0] + box[2] / 2f)) * 2f / floatWidth();
		}

		@Override
		public Feature hudFeature() {
			return StatusBarsHudFeature.this;
		}

		@Override
		public void hudReset() {
			x.reset();
			y.reset();
			scale.reset();
			freeWidth.reset();
			stack.reset();
			sideLeft.reset();
			sideRight.reset();
			sideWidth.reset();
		}
	}
}
