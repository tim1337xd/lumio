package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.hud.HudPanelFeature;
import dev.tim1337.lumio.mixin.accessor.BossHealthOverlayAccessor;
import dev.tim1337.lumio.ui.HudEditScreen;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The vanilla boss bars in the mod's style, as a panel that can be moved and resized in the HUD editor: the name, the bar in the boss color or the
 * menu color, and optionally the remaining percentage.
 */
public class BossBarHudFeature extends HudPanelFeature {
	public enum Look {
		SLIM("Slim"),
		PANEL("Panel");

		private final Component displayName;

		Look(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	private final EnumSetting<Look> look = add(new EnumSetting<>("look", Component.literal("Style"), Look.SLIM, Look::displayName, EnumSetting.Style.SEGMENTED), "General");
	private final BooleanSetting showName = add(new BooleanSetting("show_name", Component.literal("Boss name"), true), "General");
	private final EnumSetting<BarFont> font = add(new EnumSetting<>("font", Component.literal("Font"), BarFont.MENU,
		BarFont::displayName, EnumSetting.Style.SEGMENTED), "General");
	private final BooleanSetting showPercent = add(new BooleanSetting("show_percent", Component.literal("Health in percent"), false), "General");
	private final BooleanSetting bossColor = add(new BooleanSetting("boss_color", Component.literal("Boss color"), true), "General", "Off: the accent color");
	private final FloatSetting barWidth = add(new FloatSetting("bar_width", Component.literal("Bar width"), 220f, 120f, 400f, 0), "General");
	private final FloatSetting barHeight = add(new FloatSetting("bar_height", Component.literal("Bar height"), 7f, 3f, 16f, 0), "General");
	private final FloatSetting most = add(new FloatSetting("most", Component.literal("Max bars"), 3f, 1f, 8f, 0), "General");

	private static final int PAD = 8;
	private static final int GAP = 7;

	private record Entry(String name, float progress, int rgb, boolean sample) {
	}

	private List<Entry> entries = List.of();
	/** Displayed progress per bar, trailing the game's value slightly, so a hit bar slides down instead of jumping. */
	private final Map<String, Float> shownProgress = new HashMap<>();
	private long lastFrame;

	public BossBarHudFeature() {
		super("boss_bars", "Boss bars", "The boss bars of the game, in the style of the mod. Move them in the editor of the layout.", false, Anchor.CENTER, false, 0f, 12f);
		iconRgb(SystemColors.RED_RGB);
		iconName("crown");
		summary("The boss bars in the style of the mod");
		showPercent.visibleWhen(() -> true);
		barHeight.under(look, () -> look.get() == Look.SLIM || look.get() == Look.PANEL);
	}

	/** @return whether the vanilla boss bars are hidden because they are drawn here */
	public boolean replacesVanilla() {
		return isEnabled();
	}

	private static int rgbOf(BossEvent.BossBarColor color) {
		return switch (color) {
			case PINK -> 0xFF6FA5;
			case BLUE -> 0x4D8DFF;
			case RED -> SystemColors.RED_RGB;
			case GREEN -> SystemColors.GREEN_RGB;
			case YELLOW -> SystemColors.YELLOW_RGB;
			case PURPLE -> SystemColors.PURPLE_RGB;
			case WHITE -> 0xF5F5F7;
		};
	}

	/** The accent color as last seen: picking a color signals it should be visible, so it turns off the boss colors. */
	private int seenAccent = -1;

	@Override
	protected void prepare(Minecraft client) {
		// while following the menu color, a menu color change is not a pick of this color
		int accentNow = accent.usesMenu() ? 1 << 24 : accent.get() & 0xFFFFFF;
		if (seenAccent != -1 && accentNow != seenAccent && bossColor.get()) {
			bossColor.set(false);
			FeatureManager.saveSoon();
		}
		seenAccent = accentNow;
		List<Entry> list = new ArrayList<>();
		var overlay = client.gui.hud.getBossOverlay();
		for (LerpingBossEvent event : ((BossHealthOverlayAccessor) overlay).lumio$events().values()) {
			if (list.size() >= Math.round(most.get())) {
				break;
			}
			list.add(new Entry(event.getName().getString(), event.getProgress(), bossColor.get() ? rgbOf(event.getColor()) : accent.get() & 0xFFFFFF, false));
		}
		if (list.isEmpty() && (HudEditScreen.isOpen() || simulated())) {
			list.add(new Entry("Boss", 0.62f, SystemColors.PURPLE_RGB, true));
		}
		entries = list;
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0.016f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
		lastFrame = now;
		float follow = 1f - (float) Math.exp(-dt * 9f);
		for (Entry entry : list) {
			float before = shownProgress.getOrDefault(entry.name(), entry.progress());
			shownProgress.put(entry.name(), before + (entry.progress() - before) * follow);
		}
		if (shownProgress.size() > 40) {
			shownProgress.clear();
		}
	}

	@Override
	protected boolean shown(Minecraft client) {
		return !entries.isEmpty();
	}

	private int entryHeight() {
		return Math.round(barHeight.get()) + (showName.get() ? 13 : 0) + (look.get() == Look.PANEL ? 10 : 0);
	}

	/** @return the width of the widest name, so long names (Hypixel event bars) stay inside the panel */
	private int widestName() {
		if (!showName.get()) {
			return 0;
		}
		var font = Minecraft.getInstance().font;
		Ui measure = new Ui(null, font, HudStyle.palette(), 0xFFFFFFFF, 0f, false);
		int widest = 0;
		for (Entry entry : entries) {
			widest = Math.max(widest, measure.width(entry.name()));
		}
		return widest;
	}

	@Override
	protected int panelWidth() {
		boolean panel = look.get() == Look.PANEL;
		// the name gets as much side padding as the bar has in the panel, plus a little, since text looks cramped against a rounded edge
		return Math.max(Math.round(barWidth.get()), widestName() + (panel ? 8 : 0)) + (panel ? PAD * 2 : 0);
	}

	@Override
	protected int panelHeight() {
		int count = Math.max(1, entries.size());
		return count * entryHeight() + (count - 1) * GAP + (look.get() == Look.PANEL ? 0 : 0);
	}

	@Override
	public void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		// the boss bars can use their own font: it applies to what they draw and nothing else
		Ui.setFontOverride(font.get().override());
		try {
			super.render(graphics, deltaTracker);
		} finally {
			Ui.setFontOverride(null);
		}
	}

	@Override
	protected void drawPanel(Ui ui, GuiGraphicsExtractor graphics, Minecraft client) {
		int width = panelWidth();
		int y = 0;
		boolean panel = look.get() == Look.PANEL;
		int barW = Math.round(barWidth.get());
		int barH = Math.round(barHeight.get());
		for (Entry entry : entries) {
			int height = entryHeight();
			// center the bar when a long name makes the panel wider than it
			int x = (width - barW) / 2;
			int top = y;
			if (panel) {
				if (hasBackground()) {
					HudStyle.panel(ui, 0, y, width, height, Math.min(14f, height / 2f), HudStyle.opacity(), glow.get());
				}
				top = y + 5;
			}
			int color = 0xFF000000 | entry.rgb();
			float progress = Mth.clamp(shownProgress.getOrDefault(entry.name(), entry.progress()), 0f, 1f);
			if (showName.get()) {
				ui.textCentered(entry.name(), width / 2, top + 5, HudStyle.text());
				top += 13;
			}
			// the bar: a dark track and a single-colored fill with the same rounded ends
			ui.rounded(x, top, barW, barH, barH / 2f, 0xB0000000);
			int fill = Math.max(barH, Math.round(barW * progress));
			if (progress > 0.004f) {
				ui.rounded(x, top, Math.min(barW, fill), barH, barH / 2f, color);
			}
			ui.ring(x, top, barW, barH, barH / 2f, 1, Ui.withOpacity(color, 0.55f));
			if (showPercent.get()) {
				String text = Math.round(progress * 100f) + "%";
				ui.textCentered(text, x + barW / 2, top + barH / 2, 0xFFFFFFFF);
			}
			y += height + GAP;
		}
	}
}
