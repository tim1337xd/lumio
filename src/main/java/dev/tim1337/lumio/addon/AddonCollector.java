package dev.tim1337.lumio.addon;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.AddonContext;
import dev.tim1337.lumio.api.AddonInfo;
import dev.tim1337.lumio.api.BlockTint;
import dev.tim1337.lumio.api.ChatListener;
import dev.tim1337.lumio.api.ChatTabProvider;
import dev.tim1337.lumio.api.LumioAddon;
import dev.tim1337.lumio.api.EntityHighlighter;
import dev.tim1337.lumio.api.FeatureSetProvider;
import dev.tim1337.lumio.api.HudSimulator;
import dev.tim1337.lumio.api.InfoSegment;
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
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.AddonPage;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.setting.SettingFactory;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.ui.EditableHud;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Collects what the add-ons register while they are initialized, then hands it to {@link Addons} as fixed arrays. An add-on's features, panels
 * and pages are the API's types; the main mod keeps them through their adapters ({@link Adapters}).
 */
final class AddonCollector {
	private final List<EntityHighlighter> highlighters = new ArrayList<>();
	private final List<ChatListener> chatListeners = new ArrayList<>();
	private final List<SuggestionProvider> suggestions = new ArrayList<>();
	private final List<ScreenHooks> screenHooks = new ArrayList<>();
	private final List<InfoValueProvider> infoValues = new ArrayList<>();
	private StatusValueProvider status;
	private TabCardProvider tabCards;
	private final List<HudSimulator> simulators = new ArrayList<>();
	private final List<FeatureSetProvider> featureSets = new ArrayList<>();
	private SetupProvider setup;
	private String setupAddon;
	private PlaceFeatureProvider placeFeatures;
	private ScoreboardProvider scoreboard;
	private final List<TooltipLineProvider> tooltipLines = new ArrayList<>();
	private final List<ChatTabProvider> chatTabs = new ArrayList<>();
	private InventoryColumn inventoryColumn;
	private final List<BlockTint> blockTints = new ArrayList<>();
	private final Map<String, Supplier<List<String>>> diagnostics = new LinkedHashMap<>();
	private final Map<EditableHud, String> huds = new LinkedHashMap<>();
	private LocationProvider location;
	/** Where the next feature of an add-on goes in the feature list: after the Interface page, in the order of registration. */
	private int insertAt = -1;

	/**
	 * Initializes one add-on: what it registers is kept only if it starts without an error. @return whether it started; when it did not, the
	 *         error is logged once and nothing of it stays registered
	 */
	boolean initialize(LumioAddon addon, AddonInfo info) {
		Context context = new Context(info);
		int made = Adapters.made();
		try {
			addon.onInitialize(context);
		} catch (RuntimeException | LinkageError e) {
			context.discard();
			Adapters.forgetFrom(made);
			Logs.error("The add-on {} could not start", info.id(), e);
			return false;
		}
		context.commit();
		return true;
	}

	void freeze(List<AddonInfo> loaded, List<AddonInfo> disabled, Map<String, String> reasons) {
		Addons.loaded = List.copyOf(loaded);
		Addons.disabled = List.copyOf(disabled);
		Addons.reasons = Map.copyOf(reasons);
		Addons.highlighters = highlighters.toArray(Addons.NO_HIGHLIGHTERS);
		Addons.chatListeners = chatListeners.toArray(Addons.NO_LISTENERS);
		Addons.suggestions = suggestions.toArray(new SuggestionProvider[0]);
		Addons.screenHooks = screenHooks.toArray(new ScreenHooks[0]);
		Addons.infoValues = infoValues.toArray(new InfoValueProvider[0]);
		Addons.status = status;
		Addons.tabCards = tabCards;
		Addons.simulators = simulators.toArray(new HudSimulator[0]);
		Addons.featureSets = featureSets.toArray(new FeatureSetProvider[0]);
		Addons.setup = setup;
		Addons.setupAddon = setupAddon;
		Addons.placeFeatures = placeFeatures;
		Addons.scoreboard = scoreboard;
		Addons.tooltipLines = tooltipLines.toArray(new TooltipLineProvider[0]);
		Addons.chatTabs = chatTabs.toArray(new ChatTabProvider[0]);
		Addons.inventoryColumn = inventoryColumn;
		Addons.blockTints = blockTints.toArray(new BlockTint[0]);
		Addons.diagnostics = Collections.unmodifiableMap(new LinkedHashMap<>(diagnostics));
		Addons.location = location;
		Addons.huds = Collections.unmodifiableMap(new LinkedHashMap<>(huds));
	}

	private int nextIndex() {
		if (insertAt < 0) {
			insertAt = FeatureManager.features().indexOf(ModFeatures.INTERFACE) + 1;
		}
		return insertAt++;
	}

	/**
	 * The context of one add-on. What it registers is kept as steps and done only once its {@link LumioAddon#onInitialize} returned
	 * without an error, so an add-on that fails halfway leaves nothing behind.
	 */
	private final class Context implements AddonContext {
		private final AddonInfo info;
		private final List<Runnable> staged = new ArrayList<>();
		private boolean done;

		Context(AddonInfo info) {
			this.info = info;
		}

		private void stage(Runnable step) {
			if (done) {
				throw new IllegalStateException("Add-on " + info.id() + " registers after its start; everything is registered in onInitialize");
			}
			staged.add(step);
		}

		/** Does what the add-on registered, once it started. */
		void commit() {
			done = true;
			staged.forEach(Runnable::run);
			staged.clear();
		}

		/** Drops what the add-on registered, after it failed to start. */
		void discard() {
			done = true;
			staged.clear();
		}

		@Override
		public SettingFactory settings() {
			return MainSettingFactory.INSTANCE;
		}

		@Override
		public AddonInfo addon() {
			return info;
		}

		@Override
		public <F extends AddonFeature> F registerFeature(F feature) {
			stage(() -> {
				Feature own = Adapters.featureOf(feature);
				FeatureManager.registerAddonFeature(nextIndex(), own, info.id());
				// only pages appear in the sidebar; every other feature is reached from a page
				own.unlist();
			});
			return feature;
		}

		@Override
		public void registerPage(AddonPage page) {
			if (!(page instanceof Feature)) {
				throw new IllegalArgumentException("Not a page made with AddonPage.create: " + page.id());
			}
			stage(() -> {
				FeatureManager.registerAddonFeature(nextIndex(), ((Feature) page), info.id());
				for (AddonPage sub : page.subPages()) {
					if (sub instanceof Feature subPage) {
						FeatureManager.registerAddonFeature(nextIndex(), subPage, info.id());
					}
				}
			});
		}

		@Override
		public void registerHud(AddonPanel panel, String group) {
			stage(() -> {
				huds.put((EditableHud) Adapters.featureOf(panel), group);
			});
		}

		@Override
		public void registerLocation(LocationProvider provider) {
			stage(() -> {
				if (location != null) {
					Logs.warn("Add-on {}: another add-on already tells where the player is; this one is not used", info.id());
					return;
				}
				location = provider;
			});
		}

		@Override
		public void registerChatListener(ChatListener listener) {
			stage(() -> {
				chatListeners.add(listener);
			});
		}

		@Override
		public void registerHighlighter(EntityHighlighter highlighter) {
			stage(() -> {
				highlighters.add(highlighter);
			});
		}

		@Override
		public void registerSuggestions(SuggestionProvider provider) {
			stage(() -> {
				suggestions.add(provider);
			});
		}

		@Override
		public void registerScreenHooks(ScreenHooks hooks) {
			stage(() -> {
				screenHooks.add(hooks);
			});
		}

		@Override
		public void registerInfoValues(InfoValueProvider provider) {
			stage(() -> {
				infoValues.add(provider);
				// its segments join the bar's list now, before the settings are read
				for (InfoSegment segment : provider.segments()) {
					ModFeatures.INFO_BAR.addSegment(segment);
				}
			});
		}

		@Override
		public void registerInventoryColumn(InventoryColumn column) {
			stage(() -> {
				if (inventoryColumn == null) {
					inventoryColumn = column;
				}
			});
		}

		@Override
		public void registerBlockTint(BlockTint tint) {
			stage(() -> {
				blockTints.add(tint);
			});
		}

		@Override
		public void registerChatTab(ChatTabProvider provider) {
			stage(() -> {
				chatTabs.add(provider);
			});
		}

		@Override
		public void registerTooltipLines(TooltipLineProvider provider) {
			stage(() -> {
				tooltipLines.add(provider);
			});
		}

		@Override
		public void registerScoreboard(ScoreboardProvider provider) {
			stage(() -> {
				if (scoreboard == null) {
					scoreboard = provider;
				}
			});
		}

		@Override
		public void registerPlaceFeatures(PlaceFeatureProvider provider) {
			stage(() -> {
				if (placeFeatures == null) {
					placeFeatures = provider;
				}
			});
		}

		@Override
		public void registerSetup(SetupProvider provider) {
			stage(() -> {
				if (setup != null) {
					Logs.warn("Add-on {}: another add-on already has a part in the setup; this one is not used", info.id());
					return;
				}
				setup = provider;
				setupAddon = info.id();
			});
		}

		@Override
		public void registerFeatureSets(FeatureSetProvider provider) {
			stage(() -> {
				featureSets.add(provider);
			});
		}

		@Override
		public void registerSimulator(HudSimulator simulator) {
			stage(() -> {
				simulators.add(simulator);
			});
		}

		@Override
		public void registerTabCards(TabCardProvider provider) {
			stage(() -> {
				if (tabCards != null) {
					Logs.warn("Add-on {}: another add-on already reads the tab list; this one is not used", info.id());
					return;
				}
				tabCards = provider;
			});
		}

		@Override
		public void registerStatusValues(StatusValueProvider provider) {
			stage(() -> {
				if (status == null || provider.priority() > status.priority()) {
					status = provider;
				}
			});
		}

		@Override
		public void registerDiagnostics(String name, Supplier<List<String>> lines) {
			stage(() -> {
				String word = AddonSwitches.clean(name);
				if (word.isEmpty() || diagnostics.putIfAbsent(word, lines) != null) {
					Logs.warn("Add-on {}: the command word {} is taken or not usable", info.id(), name);
				}
			});
		}
	}
}
