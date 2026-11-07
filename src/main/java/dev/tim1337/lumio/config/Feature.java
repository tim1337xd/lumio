package dev.tim1337.lumio.config;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.feature.FeatureView;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ButtonSetting;
import dev.tim1337.lumio.config.setting.KeySetting;
import dev.tim1337.lumio.config.setting.LinksRowSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.ui.PagePreview;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import dev.tim1337.lumio.ui.PanelTheme;
import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Base class of every feature of the mod. Settings added in the constructor are persisted automatically and shown on the feature's settings
 * page.
 */
public abstract class Feature implements FeatureView {
	private final String id;
	private final Component name;
	private final Component description;
	// the same texts as plain strings, which menus draw every frame
	private final String nameText;
	private final String descriptionText;
	/** One line of what the feature does, for the feature lists; null to use the description. */
	private String summary;
	private final List<Setting<?>> settings = new ArrayList<>();
	private final BooleanSetting enabled;
	private final List<Feature> children = new ArrayList<>();
	private Feature parent;
	private int iconRgb = SystemColors.GRAY_RGB;
	private String iconName = "sliders";

	protected Feature(String id, String name, String description, boolean enabledByDefault) {
		this(id, name, description, enabledByDefault, true);
	}

	/** @param hasSwitch whether the feature can be turned off; the switch is shown at the top of its page */
	protected Feature(String id, String name, String description, boolean enabledByDefault, boolean hasSwitch) {
		this.id = id;
		this.name = Component.literal(name);
		this.description = Component.literal(description);
		this.nameText = name;
		this.descriptionText = description;
		this.enabled = new BooleanSetting("enabled", Component.literal("Turned on"), enabledByDefault);
		this.enabled.setDescription("Off: nothing of this feature is drawn or changed");
		if (hasSwitch) {
			settings.add(enabled);
		}
	}

	protected <S extends AddonSetting<?>> S add(S setting) {
		// every setting is one of the main mod's, also those an add-on made through the API
		settings.add((Setting<?>) setting);
		return setting;
	}

	/** @param section title of the settings group this setting belongs to on the feature's page */
	protected <S extends AddonSetting<?>> S add(S setting, String section) {
		((Setting<?>) setting).setSection(section);
		return add(setting);
	}

	/** @param description short plain-language line shown under the setting's name */
	protected <S extends AddonSetting<?>> S add(S setting, String section, String description) {
		setting.setDescription(description);
		return add(setting, section);
	}

	/** Sets the description of an already added setting. */
	protected static <S extends Setting<?>> S describe(S setting, String description) {
		setting.setDescription(description);
		return setting;
	}

	private final Set<String> collapsibleSections = new HashSet<>();

	/** Makes a settings group collapsible: only its first setting is shown until a button expands the rest. */
	protected void collapsible(String section) {
		collapsibleSections.add(section);
	}

	public boolean isCollapsible(String section) {
		return section != null && collapsibleSections.contains(section);
	}

	/**
	 * @return settings groups shown after all others on the page: the panel appearance (size, background, theme), which every panel has, comes after
	 *         the settings specific to the panel
	 */
	public Set<String> trailingSections() {
		return Set.of();
	}

	/** @return whether the settings window always uses tabs, however few settings there are; used by features that bundle several helpers */
	public boolean alwaysTabbed() {
		return false;
	}

	// ---------------------------------------------------------------------------------------------- the page's outline

	private PageOutline outline;
	private SettingsView pageAdvanced;
	private LinksRowSetting pageLinks;
	private final Map<Setting<?>, String> outlineGroups = new IdentityHashMap<>();
	private List<Setting<?>> outlineRows;
	/** Whether a page of the sidebar is this feature: without the settings pane's footer, its row of links has the reset too. */
	private boolean sidebarPage;

	/** Lays the page out as the outline says. Names change here; what is saved stays under its keys. Called once, when the mod's outlines are read. */
	public void useOutline(PageOutline outline) {
		if (!outline.onlyNames()) {
			this.outline = outline;
		}
		outlineRows = null;
		pageAdvanced = null;
		for (PageOutline.Row row : outline.rows()) {
			Setting<?> setting = settingOf(row.key());
			if (setting != null && row.label() != null) {
				setting.rename(row.label());
			}
		}
	}

	/** @return the setting with a key, also one a feature makes for its page only, such as its Look row; null when there is none */
	public Setting<?> settingOf(String key) {
		for (Setting<?> setting : settings) {
			if (setting.key().equals(key)) {
				return setting;
			}
		}
		return null;
	}

	/** @return the page a feature without a page of its own lives on, as a row there; null for one with a place of its own */
	public Feature homePage() {
		return null;
	}

	/** Says that a page of the sidebar is this feature, not a list that opens it in the settings pane. */
	public void asSidebarPage() {
		sidebarPage = true;
		outlineRows = null;
	}

	/** @return the outline of the page, or null */
	public PageOutline outline() {
		return outline;
	}

	private List<Setting<?>> outlineRows() {
		if (outlineRows == null) {
			List<Setting<?>> rows = new ArrayList<>();
			for (PageOutline.Row row : outline.main()) {
				Setting<?> setting = settingOf(row.key());
				if (setting != null) {
					rows.add(setting);
					outlineGroups.put(setting, row.group());
				}
			}
			boolean advanced = !advancedSettings().isEmpty();
			if (advanced || sidebarPage) {
				pageLinks = new LinksRowSetting("page_links", "sliders", advanced ? "Advanced…" : "", () -> {
					if (advanced) {
						MacScreen.openSubPage(pageAdvanced());
					}
				}, sidebarPage ? "Reset page" : "", () -> {
					resetSettings();
					FeatureManager.saveSoon();
				}).confirmRight();
				outlineGroups.put(pageLinks, "");
				rows.add(pageLinks);
			}
			outlineRows = rows;
		}
		return outlineRows;
	}

	private List<SettingsView.Group> advancedSettings() {
		if (outline == null) {
			return List.of();
		}
		Map<String, List<Setting<?>>> groups = new LinkedHashMap<>();
		for (PageOutline.Row row : outline.advanced()) {
			Setting<?> setting = settingOf(row.key());
			if (setting != null) {
				groups.computeIfAbsent(row.group(), group -> new ArrayList<>()).add(setting);
			}
		}
		List<SettingsView.Group> list = new ArrayList<>();
		groups.forEach((title, settings) -> list.add(new SettingsView.Group(title, settings)));
		return list;
	}

	/** @return the sheet with what is under Advanced */
	public SettingsView pageAdvanced() {
		if (pageAdvanced == null) {
			pageAdvanced = new SettingsView(id + "_advanced", "Advanced", "", "sliders", SystemColors.GRAY_RGB, advancedSettings()).asSheet();
		}
		return pageAdvanced;
	}

	/** @return the settings shown on the feature's page: all of them, or for a feature with a settings card the card (the rest is under More) */
	public List<Setting<?>> shownSettings() {
		return outline != null ? outlineRows() : settings();
	}

	/** @return the sheet or sub-page a setting is shown on, for the search; null for one on the feature's own page */
	public Feature viewOf(Setting<?> setting) {
		if (outline == null) {
			return null;
		}
		for (PageOutline.Row row : outline.developer()) {
			if (row.key().equals(setting.key())) {
				return ModFeatures.MENU.developerView();
			}
		}
		return pageAdvanced().settings().contains(setting) ? pageAdvanced() : null;
	}

	/** @return whether the page has tighter rows and groups, to keep all of it in view as far as it can */
	public boolean compactRows() {
		return false;
	}

	/** @return whether this sub-page lies over its page as a sheet, with a title, a line under it and Done, and not with a path back */
	public boolean isSheet() {
		return false;
	}

	/** @return whether the footer of the settings card offers to copy the element's style to the other HUD panels */
	public boolean copiesStyle() {
		return false;
	}

	/** Copies the element's style (background, glow, theme, depth, see-through) to the other HUD panels. */
	public void copyStyleToOthers() {
	}

	/** Takes the theme of a panel whose style is copied; only what has a theme, and is not a panel, does something. */
	public void takePanelTheme(PanelTheme theme) {
	}

	/** Adds a button at the end of the page that resets every setting on it to its default. */
	protected void addResetButton() {
		add(new ButtonSetting("reset_page", Component.literal("Reset page"), Component.literal("Reset"), false,
			() -> {
				settings.forEach(Setting::reset);
				afterReset();
			}).confirm(), "Reset");
		settingOf("reset_page").visibleWhen(() -> outline == null);
	}

	/** Called after the page's settings were reset, for what depends on them. */
	protected void afterReset() {
	}

	/**
	 * Adds a key of the feature to its settings window, in the Keys group. Called when the key is registered, during mod initialization, which can
	 * be after the feature was created.
	 */
	protected void addKey(KeyMapping mapping, KeySetting.Where where) {
		add(new KeySetting(mapping, where).ownedBy(nameText), "Keys");
	}

	/** @return the title at the top of the feature's page; the name in the sidebar may be shorter */
	public String headerTitle() {
		return nameText;
	}

	/** @return a short text in a chip after the page title, such as the version on the home page, or null */
	public String headerChip() {
		return null;
	}

	/** @return the tooltip of the chip after the page title, or null for none */
	public String headerChipTooltip() {
		return null;
	}

	/** A button in a page's header, right-aligned on the title's line; a primary one is filled with the accent. */
	public record HeaderButton(String text, boolean primary, Runnable action) {
	}

	/** @return the buttons in the page's header, such as the changelog on the home page; none by default */
	public List<HeaderButton> headerButtons() {
		return List.of();
	}

	/** Called when the menu leaves the feature's page: another page is shown or the menu closes; such as to end something only tried there. */
	public void pageLeft() {
	}

	/** @return the live preview the menu draws beside the feature's page, or null for none */
	public PagePreview preview() {
		return null;
	}

	/** Sets the color of the feature's icon in the page list. */
	protected void iconRgb(int rgb) {
		this.iconRgb = rgb;
	}

	/** Sets the feature's icon in the page list. */
	protected void iconName(String name) {
		this.iconName = name;
	}

	public String iconName() {
		return iconName;
	}

	/** Shows a child feature on this feature's page: its settings appear there in groups, and it gets no page of its own. */
	protected void addChild(Feature child) {
		child.parent = this;
		children.add(child);
	}

	public Feature parent() {
		return parent;
	}

	private boolean listed = true;

	/** Removes the feature from the page list; it is opened from the feature list instead, with its settings in a separate window. */
	public void unlist() {
		listed = false;
	}

	/** @return whether the feature has its own page in the menu's sidebar */
	public boolean isListed() {
		return listed && parent == null;
	}

	/** @return title of the group a setting is shown in; pages that show settings of other features assign their own groups */
	public String sectionOf(Setting<?> setting) {
		String group = outlineGroups.get(setting);
		return group != null ? group : setting.section();
	}

	public boolean isChild() {
		return parent != null;
	}

	public List<Feature> children() {
		return children;
	}

	public int iconRgb() {
		return iconRgb;
	}

	public String id() {
		return id;
	}

	public Component name() {
		return name;
	}

	public Component description() {
		return description;
	}

	/** @return the name as plain text */
	public String nameText() {
		return nameText;
	}

	/** @return the description as plain text: the introduction of the feature's settings window */
	public String descriptionText() {
		return descriptionText;
	}

	/** Sets the one-line summary shown in the feature lists: what the feature does, never its settings or state. */
	protected void summary(String text) {
		this.summary = text;
	}

	/** @return the one-line summary for the feature lists, or the description if the feature has none */
	public String summary() {
		return summary != null ? summary : descriptionText;
	}

	public List<Setting<?>> settings() {
		return settings;
	}

	/**
	 * @return the settings a page that shows this feature's settings lists, in their order: all of them by default; a feature whose page shows only
	 *         a few and opens the rest in windows of their own gives those
	 */
	public List<Setting<?>> pageSettings() {
		return settings();
	}

	/**
	 * @return whether the feature's settings are laid out by an outline: on a page of the sidebar that is this feature, it has its header with icon and switch
	 */
	public boolean ownPage() {
		return outline != null;
	}

	public boolean isSwitchable() {
		return settings.contains(enabled) || !children.isEmpty();
	}

	/** Toggles the feature. A feature made of parts toggles all of them. @return whether it is enabled afterwards */
	public boolean toggle() {
		if (!children.isEmpty()) {
			boolean on = children.stream().noneMatch(Feature::isEnabled);
			children.forEach(child -> child.enabled.set(on));
			return on;
		}
		enabled.set(!enabled.get());
		return enabled.get();
	}

	/** Enables or disables the feature. A feature made of parts updates all of them. */
	public void setEnabled(boolean on) {
		if (!children.isEmpty()) {
			children.forEach(child -> child.enabled.set(on));
		} else {
			enabled.set(on);
		}
	}

	public boolean isEnabled() {
		return enabled.get() && (master == null || master.get());
	}

	/** @return whether the feature is on, as its switch shows it: a feature made of parts is on while any part is */
	public boolean isAnyOn() {
		if (children.isEmpty()) {
			return isEnabled();
		}
		for (Feature part : children) {
			if (part.isEnabled()) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Resets every setting of the feature and its parts to its default, including its keys, except the switches that turn them on: the feature
	 * stays on or off.
	 */
	public void resetSettings() {
		resetSettingsOf(this);
		for (Feature part : children) {
			resetSettingsOf(part);
		}
		afterReset();
	}

	private static void resetSettingsOf(Feature feature) {
		for (Setting<?> setting : feature.settings) {
			if (setting instanceof KeySetting key) {
				key.resetKey(); // keys are left alone by other resets, see KeySetting.reset
			} else if (setting != feature.enabled) {
				setting.reset();
			}
		}
	}

	/** @return how many settings of the feature and its parts differ from their defaults, not counting the switches that turn them on */
	public int changedSettings() {
		int count = changedSettingsOf(this);
		for (Feature part : children) {
			count += changedSettingsOf(part);
		}
		return count;
	}

	private static int changedSettingsOf(Feature feature) {
		int count = 0;
		for (Setting<?> setting : feature.settings) {
			if (setting != feature.enabled && !setting.isDefault()) {
				count++;
			}
		}
		return count;
	}

	/** @return whether the settings always open in a separate window instead of inline on the page, however few there are */
	public boolean ownWindow() {
		return false;
	}

	/** The master switch of the page this feature is on (an add-on's, such as SkyBlock's): while it is off, the feature is off; or null. */
	private BooleanSetting master;

	/** Puts the feature, and its parts, under a page's master switch. */
	public void underMaster(BooleanSetting master) {
		this.master = master;
		children.forEach(child -> child.underMaster(master));
	}

	/** @return whether the feature is under a page's master switch */
	public boolean hasMaster() {
		return master != null;
	}

	public JsonObject save() {
		JsonObject json = new JsonObject();
		for (Setting<?> setting : settings) {
			JsonElement value = setting.save();
			if (value != null) {
				json.add(setting.key(), value);
			}
		}
		return json;
	}

	public void load(JsonObject json) {
		for (Setting<?> setting : settings) {
			// a value that cannot be read keeps its default; the other settings still load
			try {
				setting.load(json.get(setting.key()));
			} catch (RuntimeException e) {
				Logs.warn("Could not load the setting {} of {}, it keeps its default", setting.key(), id(), e);
			}
		}
	}
}
