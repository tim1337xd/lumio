package dev.tim1337.lumio.features.visual.screen;

import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.TooltipLineProvider;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.config.setting.Setting;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Item tooltips: their frame, their size, scrolling the long ones, and where the lines of add-ons go. What the game sends stays exactly as it is; see
 * {@link dev.tim1337.lumio.ui.tooltip.TooltipPainter}.
 */
public class ItemTooltipsFeature extends Feature {
	private static final String LOOK = "Look";
	private static final String ADDED_INFO = "Added info";
	private static final String ADDED_LINES = "Added lines";

	/** The frame around the tooltip; the only thing that changes about it. */
	public enum Frame {
		LUMIO("Lumio"),
		VANILLA("Vanilla");

		private final Component displayName;

		Frame(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** Where the block of added lines goes: never between what the game sends. */
	public enum Place {
		END("At the end"),
		TOP("At the top");

		private final Component displayName;

		Place(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** When the block of added lines shows. */
	public enum Show {
		ALWAYS("Always"),
		SHIFT("While Shift is held"),
		NEVER("Never");

		private final Component displayName;

		Show(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	private final EnumSetting<Frame> frame = add(new EnumSetting<>("frame", Component.literal("Frame"), Frame.LUMIO, Frame::displayName, EnumSetting.Style.SEGMENTED), LOOK);
	private final FloatSetting size = add(new FloatSetting("size", Component.literal("Size"), 1f, 0.6f, 1.5f, 2).percent().step(0.05f).doubleClickResets(), LOOK,
		"Its own size, not the GUI scale");
	private final BooleanSetting scroll = add(new BooleanSetting("scroll", Component.literal("Scroll long tooltips"), true), LOOK, "Mouse wheel; Shift for faster");
	private final EnumSetting<Place> place = add(new EnumSetting<>("added_place", Component.literal("Place"), Place.END, Place::displayName, EnumSetting.Style.SEGMENTED), ADDED_INFO);
	private final EnumSetting<Show> show = add(new EnumSetting<>("added_show", Component.literal("Show added info"), Show.ALWAYS, Show::displayName, EnumSetting.Style.DROPDOWN), ADDED_INFO);
	private List<Setting<?>> shown;

	public ItemTooltipsFeature() {
		super("item_tooltips", "Item tooltips", "Their frame and size. The game's text stays as it is; Lumio only adds.", true);
		iconRgb(SystemColors.INDIGO_RGB);
		iconName("sliders");
	}

	/** The feature's own rows and the rows of the add-ons' lines, which are saved with their add-on. */
	@Override
	public List<Setting<?>> shownSettings() {
		if (shown == null) {
			List<Setting<?>> rows = new ArrayList<>(outline() != null ? super.shownSettings() : settings());
			for (TooltipLineProvider provider : Addons.tooltipLines()) {
				for (AddonSetting<?> setting : provider.lineSettings()) {
					rows.add((Setting<?>) setting);
				}
			}
			shown = List.copyOf(rows);
		}
		return shown;
	}

	@Override
	public String sectionOf(Setting<?> setting) {
		return settings().contains(setting) ? super.sectionOf(setting) : ADDED_LINES;
	}

	public Frame frame() {
		return frame.get();
	}

	/** @return the size of a tooltip as a share of its usual size, whatever the GUI scale is */
	public float size() {
		return size.get();
	}

	public boolean scrolls() {
		return scroll.get();
	}

	public Place place() {
		return place.get();
	}

	public Show show() {
		return show.get();
	}
}
