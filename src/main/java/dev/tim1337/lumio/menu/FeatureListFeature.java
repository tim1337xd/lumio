package dev.tim1337.lumio.menu;

import dev.tim1337.lumio.addon.Adapters;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.api.feature.AddonPage;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.api.setting.Toggle;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.ActionRowSetting;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.FeatureCardsSetting;
import dev.tim1337.lumio.config.setting.FeatureGroupSetting;
import dev.tim1337.lumio.config.setting.FeatureRowSetting;
import dev.tim1337.lumio.config.setting.NoteSetting;
import dev.tim1337.lumio.config.setting.PageLinkSetting;
import dev.tim1337.lumio.config.setting.PageTilesSetting;
import dev.tim1337.lumio.config.setting.Setting;

import com.google.gson.JsonObject;

import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * A page listing features in groups, each with a switch and a button that opens its settings in a separate window. Features named with
 * {@link #inline} show all of their settings under the switch instead. The page persists nothing itself; the features keep their own settings.
 */
public class FeatureListFeature extends Feature implements AddonPage {
	private final Map<String, List<Feature>> categories = new LinkedHashMap<>();
	private final Map<Feature, FeatureRowSetting> rows = new IdentityHashMap<>();
	/** The feature rows of each group, created once. */
	private final Map<String, FeatureGroupSetting> grids = new HashMap<>();
	private final Map<String, FeatureCardsSetting> cardGrids = new HashMap<>();
	private final Map<Setting<?>, String> sections = new IdentityHashMap<>();
	/** Features that show all of their settings inline, however many there are. */
	private final Set<Feature> everything = Collections.newSetFromMap(new IdentityHashMap<>());

	private boolean tabbed;

	/** The feature this sidebar page is: choosing the page shows its settings, with no list before them. */
	private Feature direct;

	public FeatureListFeature direct(Feature feature) {
		direct = feature;
		feature.asSidebarPage();
		return category("", feature);
	}

	public Feature direct() {
		return direct;
	}

	/** The groups flagged as work in progress, by title; the note and the button at the top of each, made once. */
	private final java.util.Set<String> workInProgressGroups = new java.util.HashSet<>();
	private final Map<String, NoteSetting> workNotes = new HashMap<>();
	private final Map<String, ActionRowSetting> workReports = new HashMap<>();
	/** What the note at the top of such a group says, and where problems are reported (the place the README names for feedback). */
	public static final String WORK_IN_PROGRESS_NOTE = "These features are still being tested on Hypixel. Some may be wrong or unfinished. Please report what doesn't work.";
	private static final String REPORT_URL = "https://github.com/tim1337xd/Lumio/issues";

	/**
	 * Flags a group as work in progress: a tag after its tab's name, the note and a report button at its top, and the tag on each of its
	 * features. One line where the page is defined; taking it out takes all of that away.
	 */
	@Override
	public FeatureListFeature workInProgress(String group) {
		workInProgressGroups.add(group);
		for (Feature feature : categories.getOrDefault(group, List.of())) {
			feature.workInProgress();
		}
		return this;
	}

	/** @return whether a group (a tab of the page) is flagged as work in progress */
	public boolean isWorkInProgress(String group) {
		return workInProgressGroups.contains(group);
	}

	/** Shows the page's groups as tabs, one at a time. */
	public FeatureListFeature tabbed() {
		tabbed = true;
		return this;
	}

	public boolean isTabbed() {
		return tabbed;
	}

	@Override
	public boolean alwaysTabbed() {
		return tabbed;
	}

	/** @return whether the page lists features whose settings open in the pane, rather than showing all of them on the page (Music) */
	public boolean opensFeatures() {
		for (List<Feature> features : categories.values()) {
			for (Feature feature : features) {
				if (!everything.contains(feature)) {
					return true;
				}
			}
		}
		return false;
	}

	/** @return whether all settings of the feature are on this page */
	public boolean holdsAll(Feature feature) {
		return everything.contains(feature);
	}

	/** The page's master switch (an add-on's, such as SkyBlock's), which turns all its features off and hides them meanwhile; or null. */
	private BooleanSetting master;

	/** @return whether the page has a master switch */
	public boolean hasMaster() {
		return master != null;
	}

	/** @return how many features the page lists, shown next to its name in the sidebar */
	public int featureCount() {
		int count = 0;
		for (List<Feature> features : categories.values()) {
			count += features.size();
		}
		return count;
	}

	/** Gives the page a master switch: its features are off and unlisted while it is off. */
	public FeatureListFeature master(Toggle switchOn) {
		BooleanSetting own = (BooleanSetting) switchOn;
		master = own;
		categories.values().forEach(features -> features.forEach(feature -> feature.underMaster(own)));
		return this;
	}

	public FeatureListFeature inline(Feature... features) {
		everything.addAll(List.of(features));
		return this;
	}

	public FeatureListFeature(String id, String name, String description, int rgb, String icon) {
		super(id, name, description, true, false);
		iconRgb(rgb);
		iconName(icon);
	}

	/** @return whether the feature is in one of this page's groups */
	public boolean holds(Feature feature) {
		for (List<Feature> features : categories.values()) {
			if (features.contains(feature)) {
				return true;
			}
		}
		return false;
	}

	/** Page-level settings shown at the top, applying to all its features (such as "Only in SkyBlock"); persisted with the page. */
	private final List<Setting<?>> leading = new ArrayList<>();
	private String leadingSection = "";

	public FeatureListFeature lead(String section, AddonSetting<?>... settings) {
		leadingSection = section;
		for (AddonSetting<?> setting : settings) {
			leading.add((Setting<?>) setting);
		}
		return this;
	}

	/** Adds features to a group of the list. */
	public FeatureListFeature category(String title, Feature... features) {
		allListed = null;
		categories.computeIfAbsent(title, key -> new ArrayList<>()).addAll(List.of(features));
		return this;
	}

	/** Adds a category of an add-on's features. */
	@Override
	public FeatureListFeature category(String name, AddonFeature... features) {
		allListed = null;
		List<Feature> own = categories.computeIfAbsent(name, key -> new ArrayList<>());
		for (AddonFeature feature : features) {
			own.add(Adapters.featureOf(feature));
		}
		return this;
	}

	/** The feature whose switch is in the page's header instead of a row of the list, or null. */
	private Feature headerSwitch;

	/** Puts the feature's switch into the page's header: for a page that is all about one feature, such as music. */
	public FeatureListFeature switchInHeader(Feature feature) {
		headerSwitch = feature;
		return this;
	}

	/** @return the feature whose switch is in the page's header, or null */
	public Feature headerSwitch() {
		return headerSwitch;
	}

	// --------------------------------------------------------------------------------------- groups as pages of their own

	/** What a group shows as a page of its own: its description, its icon on a tile in its color, and when the player is where it is used. */
	private record PageLook(String description, int rgb, String icon, BooleanSupplier here) {
	}

	private final Map<String, PageLook> pageLooks = new LinkedHashMap<>();
	private List<FeatureListFeature> subPages;
	/** The page this one is a group of, or null. */
	private FeatureListFeature parentPage;
	/** Whole pages of their own listed under this one ({@link #under}), after the group pages. */
	private final List<FeatureListFeature> adoptedPages = new ArrayList<>();
	/** Whether this page is one of those, not made from a group. */
	private boolean adopted;
	/** Whether the page shows its groups as tiles leading to their pages instead of listing their features. */
	private boolean overview;
	private BooleanSupplier hereCheck;
	private boolean here;
	private PageTilesSetting tiles;
	/** Notes at the end of group pages that point to another group page, by the group's title: the text and the other page's title. */
	private final Map<String, String[]> pageNotes = new HashMap<>();
	private PageLinkSetting note;

	/**
	 * Makes a group a page of its own, listed under this page in the sidebar.
	 *
	 * @param here whether the player is where the group's features are used (on a mining island, in the Garden), or null
	 */
	public FeatureListFeature page(String title, String description, int rgb, String icon, BooleanSupplier here) {
		pageLooks.put(title, new PageLook(description, rgb, icon, here));
		return this;
	}

	/**
	 * Ends a group page ({@link #page}) with a note that points to another group page, with a link that opens it.
	 *
	 * @param text   the note, which names the other page
	 * @param target the other page's title
	 */
	public FeatureListFeature pageNote(String page, String text, String target) {
		pageNotes.put(page, new String[] {text, target});
		return this;
	}

	/** Shows the groups as tiles that lead to their pages ({@link #page}), below the page's own settings. */
	public FeatureListFeature overview() {
		overview = true;
		return this;
	}

	/** @return the pages of the groups made pages with {@link #page}, created once; they are registered as features of their own */
	public List<FeatureListFeature> subPages() {
		if (subPages == null) {
			subPages = new ArrayList<>();
			for (Map.Entry<String, PageLook> entry : pageLooks.entrySet()) {
				String title = entry.getKey();
				PageLook look = entry.getValue();
				FeatureListFeature page = new FeatureListFeature(id() + "_" + title.toLowerCase(Locale.ROOT).replace(' ', '_'), title, look.description(),
					look.rgb(), look.icon());
				page.parentPage = this;
				page.master = master;
				page.hereCheck = look.here();
				// a long page lists its features under the groups they declared, one under the other; a short one stays one plain list
				List<Feature> listed = categories.getOrDefault(title, List.of());
				Map<String, List<Feature>> groups = PageGroups.split(listed, Feature::pageGroupName);
				if (groups == null) {
					page.category("", listed.toArray(new Feature[0]));
				} else {
					groups.forEach((group, features) -> page.category(group, features.toArray(new Feature[0])));
				}
				String[] pageNote = pageNotes.get(title);
				if (pageNote != null) {
					page.note = new PageLinkSetting("page_note", pageNote[0], pageNote[1], () -> subPage(pageNote[1]));
				}
				subPages.add(page);
			}
			subPages.addAll(adoptedPages);
		}
		return subPages;
	}

	/** @return the page's groups in order, each with its features (a plain list has the one group "") */
	public Map<String, List<Feature>> groups() {
		return Collections.unmodifiableMap(categories);
	}

	/** @return the group page with the title, or null */
	private FeatureListFeature subPage(String title) {
		for (FeatureListFeature page : subPages()) {
			if (page.nameText().equals(title)) {
				return page;
			}
		}
		return null;
	}

	/** @return whether the page has group pages under it in the sidebar */
	public boolean hasSubPages() {
		return !pageLooks.isEmpty() || !adoptedPages.isEmpty();
	}

	/**
	 * Lists this page under another one in the sidebar, as the Chat page under Extras: it keeps its id, its settings and its links, and only moves.
	 * Called before the menu first opens.
	 */
	@Override
	public FeatureListFeature here(BooleanSupplier current) {
		hereCheck = current;
		return this;
	}

	@Override
	public FeatureListFeature under(AddonPage parent) {
		return under((FeatureListFeature) parent);
	}

	public FeatureListFeature under(FeatureListFeature parent) {
		parentPage = parent;
		adopted = true;
		parent.adoptedPages.add(this);
		return this;
	}

	/** @return whether the page was made from a group of its parent ({@link #page}), not listed under it with {@link #under} */
	public boolean isGroupPage() {
		return parentPage != null && !adopted;
	}

	/** @return the page this page is a group of, or null */
	public FeatureListFeature parentPage() {
		return parentPage;
	}

	/** @return where the page is: "SkyBlock  ›  Mining" for a group of a page, otherwise its name */
	public String path() {
		return parentPage == null ? nameText() : parentPage.nameText() + "  ›  " + nameText();
	}

	/** @return every feature of the page, in its groups' order; a new list, for callers that keep it */
	public List<Feature> listedFeatures() {
		List<Feature> all = new ArrayList<>();
		for (List<Feature> features : categories.values()) {
			all.addAll(features);
		}
		return all;
	}

	/** All features the page lists, over all its groups, in order; made once and again after a group was added. */
	private List<Feature> allListed;

	/** @return every feature the page lists, in order: the one group of a plain list, or all named groups of a long page one after the other */
	public List<Feature> firstGroup() {
		if (categories.size() == 1) {
			return categories.values().iterator().next();
		}
		if (allListed == null) {
			List<Feature> all = new ArrayList<>();
			categories.values().forEach(all::addAll);
			allListed = all;
		}
		return allListed;
	}

	/** Checks whether the player is where the page's features are used; the menu calls this once per second, not every frame. */
	public void refreshHere() {
		here = hereCheck != null && hereCheck.getAsBoolean();
	}

	/** @return whether the player was where the page's features are used at the last check */
	public boolean isHere() {
		return here;
	}

	private static List<Setting<?>> allOf(Feature feature) {
		List<Setting<?>> list = new ArrayList<>();
		for (Setting<?> setting : feature.pageSettings()) {
			if (setting.isVisible() && !setting.isEditorOnly() && !setting.key().equals("enabled")) {
				list.add(setting);
			}
		}
		return list;
	}

	@Override
	public List<Setting<?>> settings() {
		List<Setting<?>> list = new ArrayList<>();
		for (Setting<?> setting : leading) {
			sections.put(setting, leadingSection);
			list.add(setting);
		}
		if (master != null && !master.get()) {
			return list;
		}
		if (overview) {
			// the groups are pages of their own: tiles lead there
			if (tiles == null) {
				tiles = new PageTilesSetting("page_tiles", Component.literal("Categories"), subPages());
			}
			sections.put(tiles, "Categories");
			list.add(tiles);
			return list;
		}
		for (Map.Entry<String, List<Feature>> category : categories.entrySet()) {
			if (workInProgressGroups.contains(category.getKey())) {
				// a group still being tested says so at its top, with where to report what does not work
				for (Feature feature : category.getValue()) {
					feature.workInProgress();
				}
				NoteSetting workNote = workNotes.computeIfAbsent(category.getKey(), key -> new NoteSetting(
					"work_note_" + key.toLowerCase(Locale.ROOT).replace(' ', '_'), WORK_IN_PROGRESS_NOTE));
				sections.put(workNote, category.getKey());
				list.add(workNote);
				ActionRowSetting report = workReports.computeIfAbsent(category.getKey(), key -> new ActionRowSetting(
					"work_report_" + key.toLowerCase(Locale.ROOT).replace(' ', '_'), Component.literal("Report a problem"),
					new ActionRowSetting.Look(() -> null, () -> -1, () -> "Report a problem", () -> "Opens the issues page in your browser, after you confirm", () -> null,
						() -> null, () -> "Open…"),
					() -> net.minecraft.client.gui.screens.ConfirmLinkScreen.confirmLinkNow(net.minecraft.client.Minecraft.getInstance().gui.screen(),
						java.net.URI.create(REPORT_URL))));
				sections.put(report, category.getKey());
				list.add(report);
			}
			// the features of a group are rows on one card; each row opens the feature's settings window
			List<Feature> cards = category.getValue().stream().filter(feature -> !everything.contains(feature)).toList();
			// a group whose features all chose settings for a card shows them as cards in a grid (the Dungeons page)
			if (!cards.isEmpty() && cards.stream().allMatch(feature -> !feature.cardSettings().isEmpty())) {
				FeatureCardsSetting grid = cardGrids.computeIfAbsent(category.getKey(), key -> new FeatureCardsSetting(
					"cards_" + key.toLowerCase(Locale.ROOT).replace(' ', '_'), Component.literal(key), cards));
				sections.put(grid, category.getKey());
				list.add(grid);
			} else if (!cards.isEmpty()) {
				FeatureGroupSetting grid = grids.computeIfAbsent(category.getKey(), key -> new FeatureGroupSetting(
					"grid_" + key.toLowerCase(Locale.ROOT).replace(' ', '_'), Component.literal(key), cards));
				sections.put(grid, category.getKey());
				list.add(grid);
			}
			// the features named with inline() show their switch row and all of their settings under it; the others are on the card above
			for (Feature feature : category.getValue()) {
				if (!everything.contains(feature)) {
					continue;
				}
				FeatureRowSetting row = rows.computeIfAbsent(feature, key -> new FeatureRowSetting(key, false));
				// a feature whose switch is in the header gets no row of its own
				if (feature != headerSwitch) {
					sections.put(row, category.getKey());
					list.add(row);
				}
				for (Setting<?> setting : allOf(feature)) {
					// groups defined by the feature itself keep their names
					sections.put(setting, setting.section() != null ? setting.section() : category.getKey());
					list.add(setting);
				}
			}
		}
		if (note != null) {
			sections.put(note, "");
			list.add(note);
		}
		return list;
	}

	@Override
	public String sectionOf(Setting<?> setting) {
		return sections.get(setting);
	}

	/** Only the page's own settings are persisted here; the features persist theirs. */
	@Override
	public JsonObject save() {
		JsonObject json = new JsonObject();
		for (Setting<?> setting : leading) {
			var value = setting.save();
			if (value != null) {
				json.add(setting.key(), value);
			}
		}
		return json;
	}

	@Override
	public void load(JsonObject json) {
		for (Setting<?> setting : leading) {
			if (json.has(setting.key())) {
				setting.load(json.get(setting.key()));
			}
		}
	}
}
