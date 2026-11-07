package dev.tim1337.lumio.addon;

import dev.tim1337.lumio.api.AddonInfo;
import dev.tim1337.lumio.api.BlockTint;
import dev.tim1337.lumio.api.ChatListener;
import dev.tim1337.lumio.api.ChatTabProvider;
import dev.tim1337.lumio.api.EntityHighlighter;
import dev.tim1337.lumio.api.FeatureSetProvider;
import dev.tim1337.lumio.api.HudSimulator;
import dev.tim1337.lumio.api.InfoValueProvider;
import dev.tim1337.lumio.api.InventoryColumn;
import dev.tim1337.lumio.api.LocationProvider;
import dev.tim1337.lumio.api.PlaceFeatureProvider;
import dev.tim1337.lumio.api.ScoreboardProvider;
import dev.tim1337.lumio.api.TooltipLineProvider;
import dev.tim1337.lumio.api.ScreenHooks;
import dev.tim1337.lumio.api.SetupProvider;
import dev.tim1337.lumio.api.StatusValueProvider;
import dev.tim1337.lumio.api.SuggestionProvider;
import dev.tim1337.lumio.api.TabCardProvider;
import dev.tim1337.lumio.ui.EditableHud;

import net.minecraft.client.gui.screens.Screen;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What the add-ons registered, collected once at the start into fixed arrays and lists: the game's hot paths (mixins called per entity, per
 * message, per frame) only loop over an array, which is empty without add-ons, and never look anything up.
 */
public final class Addons {
	static final EntityHighlighter[] NO_HIGHLIGHTERS = {};
	static final ChatListener[] NO_LISTENERS = {};

	/** The add-ons that were initialized, in the order they were found; the disabled ones are not among them. */
	static List<AddonInfo> loaded = List.of();
	/** The add-ons that were found but switched off. */
	static List<AddonInfo> disabled = List.of();
	/** Why each add-on in {@link #disabled} was left out, by its id. */
	static Map<String, String> reasons = Map.of();
	static EntityHighlighter[] highlighters = NO_HIGHLIGHTERS;
	static ChatListener[] chatListeners = NO_LISTENERS;
	static SuggestionProvider[] suggestions = {};
	static ScreenHooks[] screenHooks = {};
	static InfoValueProvider[] infoValues = {};
	/** The status values with the highest priority, or null. */
	static StatusValueProvider status;
	static TabCardProvider tabCards;
	static HudSimulator[] simulators = {};
	static FeatureSetProvider[] featureSets = {};
	static SetupProvider setup;
	static String setupAddon;
	static PlaceFeatureProvider placeFeatures;
	static ScoreboardProvider scoreboard;
	static TooltipLineProvider[] tooltipLines = {};
	static ChatTabProvider[] chatTabs = {};
	static InventoryColumn inventoryColumn;
	static BlockTint[] blockTints = {};
	/** What the add-ons describe for /lumio, by command word. */
	static Map<String, Supplier<List<String>>> diagnostics = Map.of();
	static LocationProvider location;
	/** The HUD elements of the add-ons, with the group each is listed in. */
	static Map<EditableHud, String> huds = Map.of();

	private Addons() {
	}

	public static List<AddonInfo> loaded() {
		return loaded;
	}

	public static List<AddonInfo> disabled() {
		return disabled;
	}

	/** @return why an add-on that was found is not running ("Off", "Not loaded: needs API …"), or null when it runs */
	public static String whyLeftOut(String id) {
		return reasons.get(id);
	}

	/** @return the outline providers, for the mixins; an empty array without add-ons */
	public static EntityHighlighter[] highlighters() {
		return highlighters;
	}

	public static ChatListener[] chatListeners() {
		return chatListeners;
	}

	public static SuggestionProvider[] suggestions() {
		return suggestions;
	}

	public static Map<String, Supplier<List<String>>> diagnostics() {
		return diagnostics;
	}

	/** @return the values for the status bars from an add-on (the one with the highest priority), or null for the game's own */
	public static StatusValueProvider status() {
		return status;
	}

	/** @return the column of items in the inventory from an add-on, or null */
	public static InventoryColumn inventoryColumn() {
		return inventoryColumn;
	}

	/** @return the outline color of a block from the add-ons, or -1 */
	public static int blockTint(net.minecraft.core.BlockPos pos) {
		for (BlockTint tint : blockTints) {
			int color = tint.color(pos);
			if (color >= 0) {
				return color;
			}
		}
		return -1;
	}

	/** @return the chat's tabs from add-ons; the array is kept, for reading only */
	public static ChatTabProvider[] chatTabs() {
		return chatTabs;
	}

	/** @return what adds lines to the tooltips of items; the array is kept, for reading only */
	public static TooltipLineProvider[] tooltipLines() {
		return tooltipLines;
	}

	public static ScoreboardProvider scoreboard() {
		return scoreboard;
	}

	public static PlaceFeatureProvider placeFeatures() {
		return placeFeatures;
	}

	public static SetupProvider setup() {
		return setup;
	}

	/** @return the id of the add-on with a part in the setup, or null */
	public static String setupAddon() {
		return setupAddon;
	}

	public static FeatureSetProvider[] featureSets() {
		return featureSets;
	}

	public static HudSimulator[] simulators() {
		return simulators;
	}

	public static TabCardProvider tabCards() {
		return tabCards;
	}

	public static InfoValueProvider[] infoValues() {
		return infoValues;
	}

	public static ScreenHooks[] screenHooks() {
		return screenHooks;
	}

	/** @return the add-on that draws this screen itself, or null (also for anything but a screen) */
	public static ScreenHooks takenOver(Object screen) {
		if (screenHooks.length == 0 || !(screen instanceof Screen shown)) {
			return null;
		}
		for (ScreenHooks hooks : screenHooks) {
			if (hooks.takesOver(shown)) {
				return hooks;
			}
		}
		return null;
	}

	/** @return whether the player is under ground in an add-on's world (the mines of SkyBlock), where the sky's ambience stays away */
	public static boolean underGround() {
		return location != null && location.inWorld() && location.has("mines");
	}

	/** @return where the player is in an add-on's world, or null without such an add-on */
	public static LocationProvider location() {
		return location;
	}

	/** @return the add-ons' HUD elements and their groups, in the order they were registered */
	public static Map<EditableHud, String> huds() {
		return huds;
	}
}
