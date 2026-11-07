package dev.tim1337.lumio.hud;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.SettingsView;
import dev.tim1337.lumio.config.setting.ActionRowSetting;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ButtonSetting;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.DerivedEnumSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.Setting;

import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Whether a HUD panel looks as the global HUD theme says or as it was set itself. With the theme, the panel's own settings hold the theme's values, so drawing reads them as ever and never looks at
 * the theme. A change of the theme is copied to them, and any other change of one of them (in its Look sheet or in the HUD editor) makes the panel Custom, keeping what it shows. A saved panel
 * without this choice is from before it: it stays exactly as it was, and is Custom when that differs from the theme.
 */
public final class PanelLook {
	/** Where a panel's look comes from. */
	public enum Mode {
		THEME("Theme default"),
		CUSTOM("Custom");

		private final Component displayName;

		Mode(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The strength of a panel's glow, kept in two switches: the glow, and whether it is the soft one. */
	public enum Glow {
		OFF("Off"),
		SOFT("Soft"),
		STRONG("Strong");

		private final Component displayName;

		Glow(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The group of the Look row on a page. */
	public static final String ROW_GROUP = "Appearance";
	/** How much of the glow the soft one is. */
	public static final float SOFT_GLOW = 0.55f;

	/** Three steps of the finer line spacing; a value set before them stays as it is and shows as the nearest. */
	public enum Spacing {
		TIGHT("Tight", 0.85f),
		NORMAL("Normal", 1f),
		AIRY("Airy", 1.25f);

		private final Component displayName;
		private final float factor;

		Spacing(String displayName, float factor) {
			this.displayName = Component.literal(displayName);
			this.factor = factor;
		}

		public Component displayName() {
			return displayName;
		}

		public float factor() {
			return factor;
		}

		/** @return the step nearest to a factor */
		public static Spacing nearest(float factor) {
			Spacing best = NORMAL;
			for (Spacing step : values()) {
				if (Math.abs(step.factor - factor) < Math.abs(best.factor - factor)) {
					best = step;
				}
			}
			return best;
		}
	}

	/** A panel's own setting, and what it holds with the theme. */
	private record Link<T>(Setting<T> own, Supplier<T> theme, BooleanSupplier applies) {
		boolean differs() {
			return applies.getAsBoolean() && !Objects.equals(own.get(), theme.get());
		}

		void copy() {
			if (applies.getAsBoolean()) {
				own.setQuietly(theme.get());
			}
		}
	}

	private static final List<PanelLook> ALL = new ArrayList<>();
	/** Set while values are copied or loaded, which are not the player's changes. */
	private static boolean quiet;

	private final EnumSetting<Mode> mode;
	private final List<Link<?>> links = new ArrayList<>();
	private final List<ColorSetting> accents = new ArrayList<>();
	private Supplier<ColorSetting> themeAccent;
	/** Saved without the choice: settled against the theme once everything is loaded. */
	private boolean undecided;

	public PanelLook(EnumSetting<Mode> mode) {
		this.mode = mode;
		ALL.add(this);
		mode.whenSet(() -> {
			if (!quiet && themed()) {
				copyTheme();
			}
		});
	}

	/** @return whether the panel looks as the theme says */
	public boolean themed() {
		return mode.get() == Mode.THEME;
	}

	public EnumSetting<Mode> mode() {
		return mode;
	}

	/** Makes a setting of the panel part of its look. */
	public <T> void follow(Setting<T> own, Supplier<T> theme) {
		follow(own, theme, () -> true);
	}

	/** The same for a setting that only counts while a condition holds, such as a custom width while the width is custom. */
	public <T> void follow(Setting<T> own, Supplier<T> theme, BooleanSupplier applies) {
		links.add(new Link<>(own, theme, applies));
		own.whenSet(this::edited);
	}

	/** The same for the accent color, which can also be "the menu's". */
	public void followAccent(ColorSetting own, Supplier<ColorSetting> theme) {
		accents.add(own);
		themeAccent = theme;
		own.whenSet(this::edited);
	}

	/** One of the panel's settings changed: the panel goes on with what it shows, as Custom. */
	private void edited() {
		if (!quiet && themed()) {
			mode.setQuietly(Mode.CUSTOM);
		}
	}

	/** While saved values go in, which are no changes of the player's. */
	public static void loading(Runnable load) {
		boolean before = quiet;
		quiet = true;
		try {
			load.run();
		} finally {
			quiet = before;
		}
	}

	/** A panel's saved values were read: without a saved choice they are from before it. */
	public void markUndecided() {
		undecided = true;
	}

	/** @return whether the panel's values differ from the theme's */
	public boolean differs() {
		for (Link<?> link : links) {
			if (link.differs()) {
				return true;
			}
		}
		ColorSetting theme = themeAccent == null ? null : themeAccent.get();
		for (ColorSetting own : accents) {
			if (theme != null && (own.usesMenu() != theme.usesMenu() || !own.usesMenu() && own.rawRgb() != theme.rawRgb())) {
				return true;
			}
		}
		return false;
	}

	/** Puts the theme's values into the panel's settings, without this counting as a change. */
	public void copyTheme() {
		loading(() -> {
			for (Link<?> link : links) {
				link.copy();
			}
			ColorSetting theme = themeAccent == null ? null : themeAccent.get();
			for (ColorSetting own : accents) {
				if (theme != null) {
					own.setUsesMenu(theme.usesMenu());
					own.setQuietly(theme.rawRgb());
				}
			}
		});
	}

	/** @return how many of the panel's look settings are off their default only because they hold the theme's values, which the player did not change */
	public int changedByTheme() {
		if (!themed()) {
			return 0;
		}
		int count = 0;
		for (Link<?> link : links) {
			if (!link.own().isDefault()) {
				count++;
			}
		}
		for (ColorSetting own : accents) {
			if (!own.isDefault()) {
				count++;
			}
		}
		return count;
	}

	private void settle() {
		if (undecided) {
			undecided = false;
			loading(() -> mode.setQuietly(differs() ? Mode.CUSTOM : Mode.THEME));
		}
		if (themed()) {
			copyTheme();
		}
	}

	/** Everything is loaded, or the theme changed: every panel with the theme takes its values. */
	public static void settleAll() {
		for (PanelLook look : ALL) {
			look.settle();
		}
	}

	/** @return the one row a page has for the look: where it comes from, and the button to its sheet */
	public ActionRowSetting row(Runnable open) {
		ActionRowSetting row = new ActionRowSetting("look_row", Component.literal("Look"), new ActionRowSetting.Look(() -> null, () -> -1, () -> "Look", this::summary, () -> null,
			() -> mode.get().displayName().getString(), () -> "Customize…"), open);
		row.setSection(ROW_GROUP);
		return row;
	}

	private String summary() {
		if (!themed()) {
			return "Its own look, on this panel only";
		}
		return ModFeatures.MENU.hudTheme().displayName().getString() + " · accent and glow from the HUD theme";
	}

	/**
	 * @param id     the sheet's id, of the feature it belongs to
	 * @param values the groups of values, which can be changed only for Custom
	 * @return the look in one sheet: first whether it follows the theme, then the values, and at the end the way back to the theme
	 */
	public SettingsView sheet(String id, List<SettingsView.Group> values) {
		List<SettingsView.Group> groups = new ArrayList<>();
		groups.add(new SettingsView.Group("Look", List.of(mode)));
		for (SettingsView.Group group : values) {
			groups.add(group);
			for (Setting<?> setting : group.settings()) {
				setting.disabledWhen(this::themed, "Set to Custom to change this");
			}
		}
		ButtonSetting back = new ButtonSetting("look_back", Component.literal("Back to theme default"), Component.literal("Clear"), false, () -> {
			mode.set(Mode.THEME);
			FeatureManager.saveSoon();
		});
		back.setDescription("Applies to this panel");
		back.disabledWhen(this::themed);
		groups.add(new SettingsView.Group("", List.of(back)));
		return new SettingsView(id + "_look", "Look", "", "sliders", SystemColors.GRAY_RGB, groups).asSheet();
	}

	/** @return a row for the strength of a glow kept in two switches */
	public static EnumSetting<Glow> glowRow(String key, BooleanSetting glow, BooleanSetting soft) {
		return new DerivedEnumSetting<>(key, Component.literal("Glow"), Glow.OFF, Glow::displayName, EnumSetting.Style.SEGMENTED,
			() -> !glow.get() ? Glow.OFF : soft.get() ? Glow.SOFT : Glow.STRONG,
			value -> {
				glow.set(value != Glow.OFF);
				soft.set(value == Glow.SOFT);
			});
	}

	/** @return a row for a line spacing kept as a factor */
	public static EnumSetting<Spacing> spacingRow(String key, Setting<Float> spacing, float least, float most) {
		return new DerivedEnumSetting<>(key, Component.literal("Line spacing"), Spacing.NORMAL, Spacing::displayName, EnumSetting.Style.SEGMENTED,
			() -> Spacing.nearest(spacing.get()),
			value -> spacing.set(Mth.clamp(value.factor(), least, most)));
	}
}
