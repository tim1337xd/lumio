package dev.tim1337.lumio.api;

import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.AddonPage;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.setting.SettingFactory;

/**
 * What an add-on registers while it is initialized ({@link LumioAddon#onInitialize}). Everything is collected once, at the start of the
 * game; registering later is not possible. Features and pages keep the order they are registered in.
 */
public interface AddonContext {
	/** @return the factory of settings for the add-on's features */
	SettingFactory settings();

	/** @return the add-on being initialized */
	AddonInfo addon();

	/** Registers a feature; its settings are saved in the add-on's own settings file. @return the feature, for a constant */
	<F extends AddonFeature> F registerFeature(F feature);

	/** Registers a sidebar page and its sub-pages, listed after the Interface page; their features must be registered before. */
	void registerPage(AddonPage page);

	/** Registers an element of the HUD for the HUD editor, listed in a group of its own ("SkyBlock"). */
	void registerHud(AddonPanel panel, String group);

	/** Registers where the player is, in the world the add-on knows. */
	void registerLocation(LocationProvider provider);

	/** Registers a reader of chat messages and the action bar. */
	void registerChatListener(ChatListener listener);

	/** Registers outlines for entities. */
	void registerHighlighter(EntityHighlighter highlighter);

	/** Registers entries for the game's command suggestions. */
	void registerSuggestions(SuggestionProvider provider);

	/** Registers hooks into the game's container screens. */
	void registerScreenHooks(ScreenHooks hooks);

	/** Registers the values of info bar segments. */
	void registerInfoValues(InfoValueProvider provider);

	/** Registers values for the status bars, read from the action bar. */
	void registerStatusValues(StatusValueProvider provider);

	/** Registers the reading of the tab list's info columns as cards. */
	void registerTabCards(TabCardProvider provider);

	/** Registers simulations and moments for the HUD editor. */
	void registerSimulator(HudSimulator simulator);

	/** Registers the add-on's part of the feature sets on the Configs page. */
	void registerFeatureSets(FeatureSetProvider provider);

	/** Registers the add-on's part of the first setup ("How you play"). */
	void registerSetup(SetupProvider provider);

	/** Registers the features that fit where the player is, for the Home page's "You are in" card. */
	void registerPlaceFeatures(PlaceFeatureProvider provider);

	/** Registers the scoreboard's lines in the add-on's world. */
	void registerScoreboard(ScoreboardProvider provider);

	/** Registers a tab of the chat. */
	void registerChatTab(ChatTabProvider provider);

	/** Registers lines for the tooltips of items, in the one block Lumio adds to what the game sends. */
	void registerTooltipLines(TooltipLineProvider provider);

	/** Registers a column of items in the restyled inventory, next to the player. */
	void registerInventoryColumn(InventoryColumn column);

	/** Registers the color of the block outline for blocks the add-on cares about. */
	void registerBlockTint(BlockTint tint);

	/**
	 * Registers lines that describe what the add-on read (for help with problems), shown by {@code /lumio <name>}.
	 *
	 * @param name  the command word, in lower case ("skyblock")
	 * @param lines makes the lines when the command runs
	 */
	void registerDiagnostics(String name, java.util.function.Supplier<java.util.List<String>> lines);
}
