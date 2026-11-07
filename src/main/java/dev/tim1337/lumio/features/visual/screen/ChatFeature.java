package dev.tim1337.lumio.features.visual.screen;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.util.TextColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.ChatTabProvider;
import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.api.setting.AddonSetting;
import dev.tim1337.lumio.config.SettingsView;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ButtonSetting;
import dev.tim1337.lumio.config.setting.DerivedEnumSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.OrderedListSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.config.setting.StringSetting;
import dev.tim1337.lumio.features.visual.screen.chat.ChatAutoWidth;
import dev.tim1337.lumio.features.visual.screen.chat.ChatLayout;
import dev.tim1337.lumio.features.visual.screen.chat.ChatRules;
import dev.tim1337.lumio.features.visual.screen.chat.ChatSearchLook;
import dev.tim1337.lumio.features.visual.screen.chat.ChatTabFit;
import dev.tim1337.lumio.mixin.accessor.CommandSuggestionsAccessor;
import dev.tim1337.lumio.mixin.accessor.SuggestionsListAccessor;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.mixin.accessor.ChatComponentAccessor;
import dev.tim1337.lumio.ui.PagePictures;
import dev.tim1337.lumio.ui.PagePreview;
import dev.tim1337.lumio.ui.Eased;
import dev.tim1337.lumio.ui.EditableHud;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.ui.MacTheme;
import dev.tim1337.lumio.ui.PanelTheme;
import dev.tim1337.lumio.ui.Toasts;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.InputConstants;


import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.net.URI;
import java.time.LocalTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Chat improvements: a timestamp before every message, repeated messages within ten minutes collapsed into "(2)", "(3)", and a tab row above the
 * input that filters to Hypixel party, guild or private messages and switches the channel the player writes to; plus chat search and copying.
 */
public class ChatFeature extends Feature implements EditableHud {
	enum Channel {
		ALL("All", "a"),
		PARTY("Party", "p"),
		GUILD("Guild", "g"),
		OFFICER("Officer", "o"),
		DM("Private", null);

		private final String label;
		/** The Hypixel "/chat" argument that switches to this channel, or null if there is none. */
		private final String command;

		Channel(String label, String command) {
			this.label = label;
			this.command = command;
		}
	}

	/** What is known of a message, which never changes it: how it read, its channel, who wrote it, whether it mentions the player, how often it came, how wide its time is, whether it has a link and which add-on's tab it is for. */
	record Info(String original, String plain, Channel channel, String sender, boolean mention, int count, String time, boolean link, String addonTab) {
	}

	/** One tab: what it is called, its channel if it has one, and which messages it shows. */
	private record Tab(String id, String label, Channel channel, Predicate<Info> shows) {
	}

	/** The background of the open chat. */
	public enum Background {
		GLASS("Glass"),
		DARK("Dark"),
		NONE("None");

		private final Component displayName;

		Background(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** How wide the chat is: as its longest line wants it, or as set. */
	public enum WidthMode {
		AUTO("Auto"),
		FIXED("Fixed");

		private final Component displayName;

		WidthMode(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** How far lines are apart. */
	public enum Spacing {
		TIGHT("Tight", 2),
		NORMAL("Normal", 4),
		AIRY("Airy", 6);

		private final Component displayName;
		private final int extra;

		Spacing(String displayName, int extra) {
			this.displayName = Component.literal(displayName);
			this.extra = extra;
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** How the time before a message reads. */
	public enum Clock {
		OFF("Off"),
		H24("24 h"),
		H12("12 h");

		private final Component displayName;

		Clock(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** What an own tab looks for, by name. */
	public enum OwnFilter {
		CONTAINS("Contains", ChatRules.FilterMode.CONTAINS),
		STARTS_WITH("Starts with", ChatRules.FilterMode.STARTS_WITH),
		FROM_PLAYER("From player", ChatRules.FilterMode.FROM_PLAYER);

		private final Component displayName;
		private final ChatRules.FilterMode mode;

		OwnFilter(String displayName, ChatRules.FilterMode mode) {
			this.displayName = Component.literal(displayName);
			this.mode = mode;
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** The most rows of command suggestions shown at once; the list scrolls for the rest. */
	private static final int SUGGESTION_ROWS = 8;
	private static final int OWN_TABS = 4;
	private static final String OWN_TAB = "own";
	private static final String ALL_TAB = "all";
	/** The message to add instead of the original, and the earlier message it replaces, if any. */
	public record Processed(Component shown, Component remove) {
	}

	/** A repeat joins the earlier line only if that line's last repeat was less than this ago; after that it is a line of its own, and the old one keeps its number. */
	private static final long WINDOW_MS = 10 * 60 * 1000L;
	private static final Pattern STAMP = Pattern.compile("^\\d{1,2}:\\d\\d(?::\\d\\d)?(?:\\s[AP]M)?\\s+");
	/** Lines consisting only of symbols, such as Hypixel's separator bars, are not counted as messages. */
	private static final Pattern ONLY_SIGNS = Pattern.compile("^[\\W_]*$");
	private static final int TAB_HEIGHT = 13;
	/** Tab label scale: about 12.5 pixels at 1080p, as in the mockup; every tab has padding on both sides, and a small gap to the next. */
	private static final float TAB_TEXT = 0.625f;
	private static final int TAB_PAD = 6;
	private static final int TAB_GAP = 2;
	private static final float TAB_RADIUS = 4f;
	/** The button for the tabs that do not fit, with its small arrow. */
	private static final String MORE = "More";
	private static final int MORE_ICON = 6;
	private static final int MORE_ICON_GAP = 2;
	/** A chat not drawn for this long was closed. */
	private static final long CLOSED_AFTER_MILLIS = 300L;
	private static final int TAB_ACTIVE = 0x29FFFFFF;
	private static final int TAB_HOVER = 0x14FFFFFF;
	/** From the panel's side to what lies in its bar. */
	private static final int BAR_SIDE = 5;
	private static final int ICON_SIZE = 8;
	/** The size of the time's text, and how wide its column is for what it can show. */
	private static final float TIME_TEXT = 0.55f;
	private static final String TIME_24 = "00:00";
	private static final String TIME_24_SECONDS = "00:00:00";
	private static final String TIME_12 = "00:00 PM";
	private static final String TIME_12_SECONDS = "00:00:00 PM";

	private static final float DEFAULT_WIDTH = 320f;
	private static final float LEAST_WIDTH = 120f;
	private static final float MOST_WIDTH = 640f;
	private static final float WIDTH_STEP = 10f;
	private static final String LOOK = "Look";
	private static final String ADDED = "Added";
	private static final String TABS = "Tabs";

	private final EnumSetting<Background> background = add(new EnumSetting<>("background", Component.literal("Background"), Background.GLASS, Background::displayName, EnumSetting.Style.SEGMENTED), LOOK);
	private final EnumSetting<WidthMode> widthMode = add(new EnumSetting<>("width_mode", Component.literal("Width"), WidthMode.AUTO, WidthMode::displayName, EnumSetting.Style.SEGMENTED), LOOK);
	private final FloatSetting chatWidth = add(new FloatSetting("width", Component.literal("Fixed width"), DEFAULT_WIDTH, LEAST_WIDTH, MOST_WIDTH, 0).step(WIDTH_STEP), LOOK);
	private final FloatSetting maxWidth = add(new FloatSetting("max_width", Component.literal("Max width"), DEFAULT_WIDTH, LEAST_WIDTH, MOST_WIDTH, 0).step(WIDTH_STEP), LOOK);
	private final FloatSetting chatLines = add(new FloatSetting("height_lines", Component.literal("Height"), 10f, 4f, 30f, 0) {
		@Override
		public String format(float value) {
			return Math.round(value) + " lines";
		}
	}, LOOK);
	private final EnumSetting<Spacing> spacing = add(new EnumSetting<>("line_spacing", Component.literal("Line spacing"), Spacing.NORMAL, Spacing::displayName, EnumSetting.Style.SEGMENTED), LOOK);
	private final FloatSetting fadeAfter = add(new FloatSetting("fade_after", Component.literal("Fade after"), 10f, 3f, 60f, 0) {
		@Override
		public String format(float value) {
			return Math.round(value) + " s";
		}
	}, LOOK);
	private final EnumSetting<PanelTheme> panelTheme = add(new EnumSetting<>("panel_theme", Component.literal("Theme"), PanelTheme.values()[0], PanelTheme::displayName, EnumSetting.Style.DROPDOWN));
	// where the chat was moved to from its place at the bottom left, in the HUD editor
	private final FloatSetting offsetX = add(new FloatSetting("offset_x", Component.literal("Across"), 0f, -2000f, 2000f, 0));
	private final FloatSetting offsetY = add(new FloatSetting("offset_y", Component.literal("Down"), 0f, -2000f, 2000f, 0));

	private final BooleanSetting timestamps = add(new BooleanSetting("timestamps", Component.literal("Timestamps"), true));
	private final BooleanSetting twelveHours = add(new BooleanSetting("twelve_hours", Component.literal("12 hour clock"), false));
	private final EnumSetting<Clock> clock = add(new DerivedEnumSetting<>("clock", Component.literal("Timestamps"), Clock.H24, Clock::displayName, EnumSetting.Style.SEGMENTED,
		() -> !this.timestamps.get() ? Clock.OFF : this.twelveHours.get() ? Clock.H12 : Clock.H24, this::setClock), ADDED);
	private final BooleanSetting seconds = add(new BooleanSetting("seconds", Component.literal("Show seconds"), false), ADDED);
	private final BooleanSetting heads = add(new BooleanSetting("heads", Component.literal("Player heads"), true), ADDED);
	private final BooleanSetting stripe = add(new BooleanSetting("stripe", Component.literal("Channel stripe"), true), ADDED, "A thin bar for party, guild and private lines");
	private final ColorSetting partyColor = add(new ColorSetting("stripe_party", Component.literal("Party"), SystemColors.BLUE_RGB), ADDED);
	private final ColorSetting guildColor = add(new ColorSetting("stripe_guild", Component.literal("Guild"), SystemColors.GREEN_RGB), ADDED);
	private final ColorSetting privateColor = add(new ColorSetting("stripe_private", Component.literal("Private"), SystemColors.PINK_RGB), ADDED);
	private final BooleanSetting highlight = add(new BooleanSetting("highlight", Component.literal("Highlight my name and words"), true), ADDED);
	private final StringSetting highlightWords = add(new StringSetting("highlight_words", Component.literal("More words"), "", false), ADDED, "Separated by commas");
	private final BooleanSetting compact = add(new BooleanSetting("compact", Component.literal("Collapse repeats"), true), ADDED, "A small ×3 instead of the same line again");
	private final BooleanSetting hoverActions = add(new BooleanSetting("hover_actions", Component.literal("Copy and Reply on hover"), true), ADDED);
	private final BooleanSetting links = add(new BooleanSetting("links", Component.literal("Clickable links"), true), ADDED, "They open after the game's own question");
	private final BooleanSetting copy = add(new BooleanSetting("copy", Component.literal("Right-click to copy"), true), ADDED);
	private final BooleanSetting tabs = add(new BooleanSetting("tabs", Component.literal("Chat tabs"), true), TABS);
	private final OrderedListSetting tabOrder = add(new OrderedListSetting("tab_order", Component.literal("Tabs and their order"), ownedTabs()), TABS);
	private final ColorSetting accent = add(new ColorSetting("accent", Component.literal("Accent color"), SystemColors.BLUE_RGB).menuByDefault(), TABS, "Color of the input's ring");
	private final BooleanSetting glow = add(new BooleanSetting("glow", Component.literal("Tab glow"), false), TABS);
	private final FloatSetting history = add(new FloatSetting("history", Component.literal("History"), 1500f, 100f, 2000f, 0).step(100f), TABS, "Lines kept to scroll back to");
	private final BooleanSetting search = add(new BooleanSetting("search", Component.literal("Chat search"), true), TABS, "Button beside the tabs, or Ctrl+F");
	private final List<StringSetting> ownNames = new ArrayList<>();
	private final List<EnumSetting<OwnFilter>> ownModes = new ArrayList<>();
	private final List<StringSetting> ownTexts = new ArrayList<>();
	private final SettingsView ownTabsView = makeOwnTabs();
	private final ButtonSetting ownTabsButton = add(new ButtonSetting("own_tabs", Component.literal("Own tabs with a filter"), Component.literal("Edit"), false, () -> MacScreen.openEdit(ownTabsView)), TABS);
	private final FloatSetting tabsSize = add(new FloatSetting("tabs_size", Component.literal("Tab size"), 1f, 0.6f, 1.5f, 2), TABS);
	{
		tabsSize.visibleWhen(() -> tabs.get() || search.get());
	}

	/** @return the point the tabs and search are scaled around: the left of the panel's bar, in its middle */
	private float[] tabsAnchor() {
		return new float[]{barLeft(), tabTop + TAB_HEIGHT / 2f};
	}

	private int barLeft() {
		return panelLeft() + BAR_SIDE;
	}

	/** @return the right of the panel's bar, in the unscaled space of the tabs */
	private int barRight() {
		float s = tabsSize.get();
		return Math.round(barLeft() + (panelLeft() + panelWidth() - BAR_SIDE - barLeft()) / s);
	}

	/** @return a mouse position transformed into the unscaled space of the tabs */
	private double[] unscaled(double mouseX, double mouseY) {
		float s = tabsSize.get();
		float[] anchor = tabsAnchor();
		return new double[]{anchor[0] + (mouseX - anchor[0]) / s, anchor[1] + (mouseY - anchor[1]) / s};
	}

	/** Chat scale as a factor of the vanilla chat scale; changed in the HUD editor. */
	private final FloatSetting size = add(new FloatSetting("chat_size", Component.literal("Text size"), 1f, 0.3f, 2.5f, 2).percent(), LOOK);
	private float appliedSize = 1f;
	private int appliedLayout;
	private long sizeChangedAt;
	private boolean appliedKept;

	private final ChatRules.Repeats<Component> repeats = new ChatRules.Repeats<>(WINDOW_MS, MOST_REMEMBERED);
	/** What is known of the messages by what the chat holds for them, newest kept: the map of a bounded number. */
	private final Map<Component, Info> infos = new IdentityHashMap<>();
	private final ArrayDeque<Component> infoOrder = new ArrayDeque<>();
	private final ChatDecor decor = new ChatDecor(this);
	/** The tab that is open, by its id, and the tabs in their order, made again when they change. */
	private String openTab = ALL_TAB;
	private List<Tab> shownTabs = List.of();
	private int shownVersion = -1;
	private static final int OWN_FIELDS = 3;
	private final Object[] shownOwn = new Object[OWN_TABS * OWN_FIELDS];
	/** The channel a message typed now goes to, as far as is known: what was picked here and what the server last said. */
	private Channel typing = Channel.ALL;
	private final Eased markX = new Eased();
	private final Eased markWidth = new Eased();

	public ChatFeature() {
		super("chat", "Chat", "Messages stay as the game sends them. Lumio adds.", false);
		iconRgb(SystemColors.GREEN_RGB);
		iconName("bubble");
		summary("A calm panel with tabs, heads and highlights");
		addResetButton();
		seconds.under(timestamps);
		for (Setting<?> hidden : new Setting<?>[]{panelTheme, offsetX, offsetY, timestamps, twelveHours}) {
			hidden.visibleWhen(() -> false);
		}
		seconds.visibleWhen(timestamps::get);
		chatWidth.visibleWhen(() -> !auto());
		maxWidth.visibleWhen(this::auto);
		for (ColorSetting color : new ColorSetting[]{partyColor, guildColor, privateColor}) {
			color.under(stripe);
			color.inRow("Colors");
		}
		highlightWords.under(highlight);
		size.step(0.05f);
	}

	/** A config from before the Auto width keeps its fixed width. */
	@Override
	public void load(JsonObject json) {
		ChatAutoWidth.keepOldWidth(json);
		super.load(json);
	}

	private boolean auto() {
		return widthMode.get() == WidthMode.AUTO;
	}

	private void setClock(Clock value) {
		timestamps.set(value != Clock.OFF);
		twelveHours.set(value == Clock.H12);
	}

	private static List<OrderedListSetting.Option> ownedTabs() {
		List<OrderedListSetting.Option> options = new ArrayList<>(List.of(
			new OrderedListSetting.Option(ALL_TAB, "All", "bubble", true),
			new OrderedListSetting.Option("party", "Party", "bubble", true),
			new OrderedListSetting.Option("guild", "Guild", "bubble", true),
			new OrderedListSetting.Option("officer", "Officer", "bubble", false),
			new OrderedListSetting.Option("private", "Private", "bubble", true)));
		for (int i = 1; i <= OWN_TABS; i++) {
			options.add(new OrderedListSetting.Option(OWN_TAB + i, "Own tab " + i, "bubble", true));
		}
		return options;
	}

	private SettingsView makeOwnTabs() {
		List<SettingsView.Group> groups = new ArrayList<>();
		for (int i = 1; i <= OWN_TABS; i++) {
			String group = "Own tab " + i;
			StringSetting name = add(new StringSetting(OWN_TAB + i + "_name", Component.literal("Name"), "", false), group);
			EnumSetting<OwnFilter> mode = add(new EnumSetting<>(OWN_TAB + i + "_mode", Component.literal("Shows lines"), OwnFilter.CONTAINS, OwnFilter::displayName, EnumSetting.Style.SEGMENTED), group);
			StringSetting text = add(new StringSetting(OWN_TAB + i + "_text", Component.literal("Text"), "", false), group);
			ownNames.add(name);
			ownModes.add(mode);
			ownTexts.add(text);
			groups.add(new SettingsView.Group(group, List.of(name, mode, text)));
		}
		return new SettingsView("chat_own_tabs", "Own tabs", "Each with a name and a filter; one without a name is not shown.", "bubble", SystemColors.GREEN_RGB, groups);
	}

	/** The add-ons' tabs come after the channels, and are saved with the order; called once the add-ons have registered theirs, before the settings load. */
	public void addonsLoaded() {
		String after = "private";
		for (ChatTabProvider provider : Addons.chatTabs()) {
			tabOrder.addOption(new OrderedListSetting.Option(provider.tabId(), provider.title(), "bubble", true), after);
			after = provider.tabId();
		}
	}

	/** The feature's own rows and the rows of the add-ons' tabs, which are saved with their add-on. Each own tab's rows are on their sub-page. */
	@Override
	public List<Setting<?>> shownSettings() {
		if (outline() != null) {
			return super.shownSettings();
		}
		if (cardRows == null) {
			List<Setting<?>> rows = new ArrayList<>(settings());
			rows.removeAll(ownTabsView.settings());
			// the size of the text stands with the chat's other sizes
			rows.remove(size);
			rows.add(rows.indexOf(chatLines) + 1, size);
			int place = rows.indexOf(ownTabsButton);
			for (ChatTabProvider provider : Addons.chatTabs()) {
				for (AddonSetting<?> setting : provider.tabSettings()) {
					rows.add(place++, (Setting<?>) setting);
				}
			}
			cardRows = List.copyOf(rows);
		}
		return cardRows;
	}

	private List<Setting<?>> cardRows;

	@Override
	public PagePreview preview() {
		return outline() == null ? null : chatPreview;
	}

	private final PagePreview chatPreview = new PagePreview() {
		@Override
		public void draw(Ui ui, int x, int y, int width, int height) {
			PagePictures.chat(ui, new PagePictures.ChatLook(tabs.get(), timestamps.get(), twelveHours.get(), heads.get(), stripe.get(), highlight.get(), compact.get(), background.get() != Background.NONE), x, y, width, height);
		}

		@Override
		public boolean tall() {
			return true;
		}

		@Override
		public String note() {
			return "Your chat, with sample lines";
		}
	};

	@Override
	public String sectionOf(Setting<?> setting) {
		return settings().contains(setting) ? super.sectionOf(setting) : TABS;
	}

	@Override
	public Feature viewOf(Setting<?> setting) {
		if (ownTabsView.settings().contains(setting)) {
			return ownTabsView;
		}
		return super.viewOf(setting);
	}

	// ------------------------------------------------------------------------------------------------ HUD editor

	/** @return the chat scale factor relative to the vanilla setting, 1 when disabled */
	public float sizeFactor() {
		return isEnabled() ? size.get() : 1f;
	}

	/** @return how wide the game wraps messages, on the screen: what the panel leaves after all that is added to a line; the game's own setting while the feature is off */
	public int chatWidthSetting(int gameWidth) {
		if (!isEnabled()) {
			return gameWidth;
		}
		// the Auto width only draws the panel narrower: lines are wrapped for its most, so they are not wrapped anew when it changes
		int wrap = ChatLayout.wrapWidth(Math.round(widestPanel() / scale()), timeColumn(), heads.get());
		return Mth.ceil(wrap * scale());
	}

	/** @return the panel's width on the screen: in Auto as its lines want it, or as set */
	public int panelWidth() {
		int widest = widestPanel();
		if (!auto()) {
			return widest;
		}
		int now = autoWidth.width();
		return now < 0 ? widest : Math.min(now, widest);
	}

	/** @return the width the settings allow on the screen: the Fixed or Max width with the panel's own edges, at the text's size, and never wider than the screen */
	private int widestPanel() {
		return panelOf(auto() ? maxWidth.get() : chatWidth.get());
	}

	private int panelOf(float width) {
		int screen = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		return Math.min(Math.round((width + ChatLayout.PANEL_EXTRA) * size.get()), screen - 2 * ChatLayout.SCREEN_MARGIN);
	}

	/**
	 * Follows the longest shown line, for the Auto width: while the chat is open the lines on the screen, while it is closed those still showing. The lines are measured only when they change;
	 * called before the chat draws, so a wider line is never cut, not for a frame.
	 */
	void followLines(Font font, int tick, boolean open) {
		if (!auto()) {
			return;
		}
		ChatComponent component = Minecraft.getInstance().gui.hud.getChat();
		var access = (ChatComponentAccessor) component;
		var lines = access.lumio$lines();
		int perPage = rowsDrawn(component.getLinesPerPage());
		int scroll = access.lumio$scroll();
		int shown = 0;
		for (int i = 0; i < perPage && scroll + i < lines.size(); i++) {
			if (open || ChatDecor.alpha(tick, lines.get(scroll + i)) > ChatDecor.LEAST_ALPHA) {
				shown++;
			}
		}
		Object newest = lines.isEmpty() ? null : lines.get(0);
		if (open != measuredOpen || scroll != measuredScroll || shown != measuredShown || lines.size() != measuredCount || newest != measuredNewest) {
			measuredOpen = open;
			measuredScroll = scroll;
			measuredShown = shown;
			measuredCount = lines.size();
			measuredNewest = newest;
			measuredLongest = 0;
			for (int i = 0; i < perPage && scroll + i < lines.size(); i++) {
				if (open || ChatDecor.alpha(tick, lines.get(scroll + i)) > ChatDecor.LEAST_ALPHA) {
					measuredLongest = Math.max(measuredLongest, font.width(lines.get(scroll + i).content()));
				}
			}
		}
		int wanted = Mth.ceil(ChatLayout.panelFor(measuredLongest, timeColumn(), heads.get()) * scale());
		autoWidth.update(ChatAutoWidth.wanted(measuredLongest == 0 ? 0 : wanted, tabsNeed(font), panelOf(LEAST_WIDTH), widestPanel()), System.currentTimeMillis(), ModFeatures.MENU.animations());
	}

	private final ChatAutoWidth autoWidth = new ChatAutoWidth();
	private boolean measuredOpen;
	private int measuredScroll = -1;
	private int measuredShown = -1;
	private int measuredCount = -1;
	private Object measuredNewest;
	private int measuredLongest;

	/** @return the panel's width in the chat's own pixels */
	int panelWidthInChat() {
		return Math.round(panelWidth() / scale());
	}

	/** @return the left of the panel on the screen, where the player moved it, always on the screen */
	int panelLeft() {
		int screen = Minecraft.getInstance().getWindow().getGuiScaledWidth();
		return ChatLayout.keepOnScreen(CHAT_LEFT + Math.round(offsetX.get()), panelWidth(), screen);
	}

	/** @return how wide the time's column is in the chat's own pixels, with its digits in cells of one width; 0 with the time off */
	int timeColumn() {
		if (!timestamps.get()) {
			return 0;
		}
		String sample = twelveHours.get() ? (seconds.get() ? TIME_12_SECONDS : TIME_12) : (seconds.get() ? TIME_24_SECONDS : TIME_24);
		if (!sample.equals(measuredTime)) {
			measuredTime = sample;
			Font font = Minecraft.getInstance().font;
			float digit = Ui.measureScaled(font, "0", TIME_TEXT);
			float width = 0f;
			for (int i = 0; i < sample.length(); i++) {
				width += sample.charAt(i) == '0' ? digit : Ui.measureScaled(font, sample.substring(i, i + 1), TIME_TEXT);
			}
			measuredWidth = Mth.ceil(width);
		}
		return measuredWidth;
	}

	/** @return the width of the cell of one digit of the time */
	static float digitWidth(Ui ui) {
		return ui.widthScaled("0", TIME_TEXT);
	}

	private String measuredTime;
	private int measuredWidth;

	/** @return how many lines the chat is high, or 0 to leave it as the game has it */
	public int chatLinesSetting() {
		return isEnabled() ? Math.round(chatLines.get()) : 0;
	}

	/** @return how much further apart lines are than the game has them */
	public int extraLineHeight() {
		return isEnabled() ? spacing.get().extra : 0;
	}

	/** @return a tick moved so that the game's fade, which starts at nine and ends at ten seconds, ends after the set time */
	public int fadeTick(int tick) {
		return isEnabled() ? tick - Math.round((fadeAfter.get() - GAME_FADE_SECONDS) * TICKS_PER_SECOND) : tick;
	}

	private static final float GAME_FADE_SECONDS = 10f;
	private static final float TICKS_PER_SECOND = 20f;
	private static final int MOST_REMEMBERED = 2_500;

	/** Called every tick: once the size changed and settled, re-wraps the lines for the new width, which is expensive, so not on every step. */
	public void tick(Minecraft client) {
		boolean kept = keptOut();
		int layout = java.util.Objects.hash(Math.round(auto() ? maxWidth.get() : chatWidth.get()), spacing.get(), timestamps.get(), twelveHours.get(), seconds.get(), heads.get(), client.getWindow().getGuiScaledWidth());
		if ((kept != appliedKept || layout != appliedLayout) && client.gui != null && client.gui.hud != null) {
			appliedKept = kept;
			appliedLayout = layout;
			((ChatComponentAccessor) client.gui.hud.getChat()).lumio$refresh();
		}
		float now = sizeFactor();
		if (now != appliedSize) {
			if (sizeChangedAt == 0L) {
				sizeChangedAt = System.currentTimeMillis();
			}
			if (System.currentTimeMillis() - sizeChangedAt > 300L) {
				appliedSize = now;
				sizeChangedAt = 0L;
				if (client.gui != null && client.gui.hud != null) {
					((ChatComponentAccessor) client.gui.hud.getChat()).lumio$refresh();
				}
			}
		} else {
			sizeChangedAt = 0L;
		}
	}

	@Override
	public String hudName() {
		return "Chat";
	}

	@Override
	public boolean hudShown() {
		return isEnabled();
	}

	@Override
	public int[] hudBounds() {
		int screenHeight = Minecraft.getInstance().getWindow().getGuiScaledHeight();
		int top = panelTop(screenHeight);
		return new int[]{panelLeft(), top, panelWidth(), panelBottom(screenHeight) - top};
	}

	@Override
	public void hudMove(int dx, int dy) {
		// from where it is kept on the screen, so a drag back moves it at once
		offsetX.set((float) (panelLeft() - CHAT_LEFT + dx));
		offsetY.set(moveY() + (float) dy);
	}

	@Override
	public boolean hudMovable() {
		return true;
	}

	@Override
	public Feature hudFeature() {
		return this;
	}

	@Override
	public EnumSetting<PanelTheme> hudThemeSetting() {
		return panelTheme;
	}

	@Override
	public BooleanSetting hudGlowSetting() {
		return glow;
	}

	@Override
	public float hudRadius() {
		return 4f;
	}

	@Override
	public float hudMinScale() {
		return size.min();
	}

	@Override
	public float hudMaxScale() {
		return size.max();
	}

	@Override
	public float hudScale() {
		return size.get();
	}

	@Override
	public void hudSetScale(float scale) {
		size.set(Math.max(size.min(), Math.min(size.max(), scale)));
	}

	/** Scales from the bottom-left corner, where the game anchors it. */
	@Override
	public float hudScaleAt(int mouseX, int mouseY) {
		int[] box = hudBounds();
		return mouseX * hudScale() / Math.max(8f, box[2]);
	}

	@Override
	public float hudAnchorX(int[] box) {
		return box[0];
	}

	@Override
	public void hudReset() {
		size.reset();
		offsetX.reset();
		offsetY.reset();
	}

	// ------------------------------------------------------------------------------------------------ where it is, for what is drawn around the lines

	float scale() {
		return (float) ((ChatComponentAccessor) Minecraft.getInstance().gui.hud.getChat()).lumio$scale();
	}

	/** @return the left of the chat on the screen: of its panel, from which its columns are counted */
	float left() {
		return panelLeft();
	}

	/** @return where the lowest line ends on the screen before it is moved, above the status bars */
	int baseBottom(int screenHeight) {
		var access = (ChatComponentAccessor) Minecraft.getInstance().gui.hud.getChat();
		int bottom = screenHeight - CHAT_ABOVE_BOTTOM;
		// by the widest it gets, so the chat does not move up and down as its width follows its lines
		int lift = ModFeatures.STATUS == null ? 0 : ModFeatures.STATUS.chatLift(bottom, bottom - access.lumio$height(), liftWidth());
		return bottom - lift;
	}

	/** @return the width the status bars keep out of the way of */
	public int liftWidth() {
		return widestPanel();
	}

	/** @return where the lowest line ends on the screen */
	int chatBottom(int screenHeight) {
		return baseBottom(screenHeight) + Math.round(moveY());
	}

	boolean barShown() {
		return tabs.get() || search.get();
	}

	/** @return how far the panel is from the first line to its top */
	private int aboveLines() {
		return ChatLayout.LIST_TOP + (barShown() ? ChatLayout.BAR : 0);
	}

	/** @return where the panel of the open chat starts on the screen */
	int panelTop(int screenHeight) {
		var access = (ChatComponentAccessor) Minecraft.getInstance().gui.hud.getChat();
		return chatBottom(screenHeight) - Math.round(access.lumio$height() * scale()) - aboveLines();
	}

	/** @return where the panel of the open chat ends on the screen */
	int panelBottom(int screenHeight) {
		return chatBottom(screenHeight) + ChatLayout.BELOW_LINES;
	}

	/** @return where the input starts on the screen */
	int inputTop(int screenHeight) {
		return chatBottom(screenHeight) + ChatLayout.LIST_BOTTOM;
	}

	/**
	 * Places the game's list of suggestions by the input: above it, or below it if only that fits, left-aligned with the input's text and kept inside the panel and on the screen.
	 * The game places it once, where its own input would be; this runs every frame, as the panel can move.
	 */
	public void placeSuggestions(CommandSuggestions suggestions, EditBox input, int screenWidth, int screenHeight) {
		if (!isEnabled()) {
			return;
		}
		CommandSuggestions.SuggestionsList list = ((CommandSuggestionsAccessor) suggestions).lumio$suggestions();
		if (list != null) {
			Rect2i place = ((SuggestionsListAccessor) list).lumio$rect();
			int top = inputTop(screenHeight);
			place.setY(ChatLayout.listTop(place.getHeight(), top, top + ChatLayout.INPUT_HEIGHT, screenHeight));
			int width = ChatLayout.suggestionsWidth(place.getWidth(), panelLeft(), panelWidth(), screenWidth);
			place.setWidth(width);
			place.setX(ChatLayout.suggestionsLeft(input.getX(), width, panelLeft(), panelWidth(), screenWidth));
		}
	}

	/** @return how many rows the list of suggestions shows at most in the chat */
	public int suggestionRows() {
		return SUGGESTION_ROWS;
	}

	/** @return whether the game's list of suggestions is drawn in the chat's style: in the open chat, while the chat is the mod's */
	public boolean stylesSuggestions() {
		return isEnabled() && Minecraft.getInstance().gui.screen() instanceof ChatScreen;
	}

	/** Draws the panel under the game's list of suggestions, with the selected row ({@code row} from the top, or out of the list for none) marked. */
	public void drawSuggestions(GuiGraphicsExtractor graphics, Rect2i list, int row) {
		decor.drawSuggestions(graphics, Minecraft.getInstance().font, list, row);
	}

	/** The game drew its list's entries over the panel. */
	public void suggestionsDrawn(GuiGraphicsExtractor graphics) {
		decor.suggestionsDrawn(graphics);
	}

	/** @return how far the game's lines of what a command takes move, to lie by the input */
	public int usageShift(int lines, int screenHeight) {
		int top = inputTop(screenHeight);
		return isEnabled() ? ChatLayout.usageShift(lines, top, top + ChatLayout.INPUT_HEIGHT, screenHeight) : 0;
	}

	/** @return a place on the screen in the chat's own pixels */
	double[] toChat(int screenHeight, double mouseX, double mouseY) {
		float scale = scale();
		return new double[]{(mouseX - left()) / scale, (mouseY - moveY()) / scale};
	}

	/** @return which of the chat's lines a place on the screen is on, or -1 */
	int lineAt(int screenHeight, double mouseX, double mouseY) {
		ChatComponent component = Minecraft.getInstance().gui.hud.getChat();
		var access = (ChatComponentAccessor) component;
		float scale = scale();
		double fromBottom = Mth.floor(baseBottom(screenHeight) / scale) - (mouseY - moveY() - scrollShift()) / scale;
		double across = (mouseX - left()) / scale;
		int height = Math.max(1, access.lumio$lineHeight());
		if (fromBottom < 0 || across < 0 || across > panelWidthInChat()) {
			return -1;
		}
		int shown = (int) (fromBottom / height);
		int index = shown + access.lumio$scroll();
		return shown < rowsDrawn(component.getLinesPerPage()) && index < access.lumio$lines().size() ? index : -1;
	}

	Background background() {
		return background.get();
	}

	boolean panelGlow() {
		return glow.get();
	}

	/** @return the theme of the panel: the element's own, or glass */
	MacTheme panelTheme() {
		MacTheme own = panelTheme.get().theme();
		return own == null ? MacTheme.GLASS : own;
	}

	int accentArgb() {
		return 0xFF000000 | accent.get();
	}

	boolean heads() {
		return heads.get();
	}

	boolean links() {
		return links.get();
	}

	boolean hoverActions() {
		return hoverActions.get();
	}

	/** @return the color of a line's stripe as 0xRRGGBB, or 0 for none */
	int stripeColor(Info info) {
		if (!stripe.get()) {
			return 0;
		}
		return switch (info.channel()) {
			case PARTY -> partyColor.get() & 0xFFFFFF;
			case GUILD, OFFICER -> guildColor.get() & 0xFFFFFF;
			case DM -> privateColor.get() & 0xFFFFFF;
			case ALL -> 0;
		};
	}

	/** @return what a reply to a line starts with in the input, or null where there is no channel to answer in */
	String replyPrefix(Info info) {
		return switch (info.channel()) {
			case PARTY -> "/pc ";
			case GUILD -> "/gc ";
			case OFFICER -> "/oc ";
			case DM -> "/r ";
			case ALL -> null;
		};
	}

	String typingChannel() {
		return typing.label;
	}

	void before(GuiGraphicsExtractor graphics, Font font, int tick, int mouseX, int mouseY, boolean open) {
		decor.before(graphics, font, tick, mouseX, mouseY, open);
	}

	void after(GuiGraphicsExtractor graphics, Font font, int tick, int mouseX, int mouseY, boolean open) {
		decor.after(graphics, font, tick, mouseX, mouseY, open);
	}

	/** Draws what lies behind the lines of the chat. */
	public void drawBehind(GuiGraphicsExtractor graphics, Font font, int tick, int mouseX, int mouseY, boolean open) {
		if (isEnabled()) {
			scrolling.frame();
			before(graphics, font, tick, mouseX, mouseY, open);
			gameLinesClipped = clipRows(graphics);
		}
	}

	/** Draws what lies around the lines of the chat. */
	public void drawAround(GuiGraphicsExtractor graphics, Font font, int tick, int mouseX, int mouseY, boolean open) {
		if (gameLinesClipped) {
			graphics.disableScissor();
			gameLinesClipped = false;
		}
		if (isEnabled()) {
			after(graphics, font, tick, mouseX, mouseY, open);
		}
	}

	// ------------------------------------------------------------------------------------------------- scrolling

	private final ChatScrolling scrolling = new ChatScrolling(this);
	/** Whether the game's lines are being drawn cut at the rows. */
	private boolean gameLinesClipped;

	/** @return how far everything in the rows moves down in this frame, for the part of a line in view */
	public float scrollShift() {
		return isEnabled() ? scrolling.shift() : 0f;
	}

	/** @return how many rows are drawn */
	public int rowsDrawn(int perPage) {
		return scrolling.rows(perPage);
	}

	float scrollPosition() {
		return scrolling.position();
	}

	boolean clipRows(GuiGraphicsExtractor graphics) {
		return scrolling.clip(graphics);
	}

	public void wheel(double amount, boolean page) {
		scrolling.wheel(amount, page);
	}

	/** @return whether a key scrolled the chat: Page Up and Page Down */
	public boolean scrollKey(int key) {
		if (!isEnabled() || key != InputConstants.KEY_PAGEUP && key != InputConstants.KEY_PAGEDOWN) {
			return false;
		}
		scrolling.page(key == InputConstants.KEY_PAGEUP ? 1 : -1);
		return true;
	}

	public void gameScrolled(int lines) {
		scrolling.gameMoved(lines);
	}

	public void gameScrollReset() {
		scrolling.gameReset();
	}

	public int counterForNewLine(int counted) {
		return scrolling.counterForNewLine(counted);
	}

	public boolean pressScrollBar(double mouseX, double mouseY, int button, int screenHeight) {
		return isEnabled() && button == ScreenInput.LEFT && scrolling.press(mouseX, mouseY, screenHeight);
	}

	public boolean dragScrollBar(double mouseY, int screenHeight) {
		return isEnabled() && scrolling.drag(mouseY, screenHeight);
	}

	public void releaseScrollBar() {
		scrolling.release();
	}

	/** @return where the lines are moved on the screen, across: the game has them at 4 of its scaled pixels, and they belong in the text's column */
	public float moveX() {
		if (!isEnabled()) {
			return 0f;
		}
		return panelLeft() + (ChatLayout.textLeft(timeColumn(), heads.get()) - GAME_TEXT_LEFT) * scale();
	}

	/** @return where the lines are moved on the screen, down: kept so the whole panel is on the screen */
	public float moveY() {
		if (!isEnabled()) {
			return 0f;
		}
		Minecraft client = Minecraft.getInstance();
		int screenHeight = client.getWindow().getGuiScaledHeight();
		var access = (ChatComponentAccessor) client.gui.hud.getChat();
		int base = baseBottom(screenHeight);
		int above = Math.round(access.lumio$height() * scale()) + aboveLines();
		int height = above + ChatLayout.BELOW_LINES;
		int top = ChatLayout.keepOnScreen(base - above + Math.round(offsetY.get()), height, screenHeight);
		return top + above - base;
	}

	/** @return how far up a line's text moves from where the game puts it, to lie in the middle of its row */
	public int textLift() {
		if (!isEnabled()) {
			return 0;
		}
		int height = ((ChatComponentAccessor) Minecraft.getInstance().gui.hud.getChat()).lumio$lineHeight();
		double spacing = ChatLayout.gameSpacing(height);
		int gameOffset = (int) Math.round(GAME_TEXT_HEIGHT * (spacing + 1.0) - GAME_TEXT_HEIGHT / 2.0 * spacing);
		return height - (height - GAME_TEXT_HEIGHT) / 2 - gameOffset;
	}

	private static final int GAME_TEXT_LEFT = 4;
	private static final int GAME_TEXT_HEIGHT = 8;

	private static final int CHAT_LEFT = 4;
	private static final int CHAT_ABOVE_BOTTOM = 40;

	/** Draws the input in the mod's style, under what the game draws in it. */
	public void drawInput(GuiGraphicsExtractor graphics, Font font, EditBox input, int screenHeight) {
		if (isEnabled()) {
			decor.drawInput(graphics, font, input, screenHeight);
		}
	}

	/** @return a line's text */
	static String textOf(FormattedCharSequence content) {
		StringBuilder text = new StringBuilder();
		content.accept((position, style, codepoint) -> {
			text.appendCodePoint(codepoint);
			return true;
		});
		return text.toString();
	}

	// ------------------------------------------------------------------------------------------------------ messages

	/** @return how many chat messages are kept for scrolling back; vanilla keeps 100 */
	public int historyLines() {
		return isEnabled() ? Math.round(history.get()) : 100;
	}

	/** The filter installed on the chat: the tab filter combined with any filter that was already there (from another mod, such as SkySoft). */
	private Predicate<GuiMessage> applied;

	/**
	 * Combines the tab filter with the chat's existing filter. Another mod with its own tabs installs its filter when one of its tabs is selected,
	 * which replaced this one, and the tabs here then showed everything: so the existing filter is kept and a message must pass both.
	 */
	public void attach(ChatComponent chat) {
		Predicate<GuiMessage> current = ((ChatComponentAccessor) chat).lumio$filter();
		if (applied != null && current == applied) {
			return;
		}
		Predicate<GuiMessage> other = current;
		applied = other == null ? this::visible : message -> other.test(message) && visible(message);
		chat.setVisibleMessageFilter(applied);
	}

	/** Rebuilds the lines after a tab change with the active filter (or reinstalls it, keeping another mod's filter). */
	private void reapply(ChatComponent chat) {
		Predicate<GuiMessage> current = ((ChatComponentAccessor) chat).lumio$filter();
		if (applied != null && current == applied) {
			((ChatComponentAccessor) chat).lumio$refresh();
		} else {
			attach(chat);
		}
	}

	// SkyHanni and SkySoft abbreviate channel names ("G >", "P >", "O >") and some wrap them in brackets: all of these identify the channel
	private static final Pattern PARTY = Pattern.compile("^[^\\p{L}\\p{N}]{0,6}(?:Party|P)\\s*[>\u00BB\\]:]");
	private static final Pattern GUILD = Pattern.compile("^[^\\p{L}\\p{N}]{0,6}(?:Guild|G)\\s*[>\u00BB\\]:]");
	private static final Pattern OFFICER = Pattern.compile("^[^\\p{L}\\p{N}]{0,6}(?:Officer|O)\\s*[>\u00BB\\]:]");
	private static final Pattern DIRECT = Pattern.compile("^[^\\p{L}\\p{N}]{0,6}(?:From|To)\\s");
	/** A leading timestamp, also as written by other mods: "[18:39]", "18:39", "[6:39 PM]". */
	private static final Pattern ANY_STAMP = Pattern.compile("^\\[?\\d{1,2}:\\d\\d(?::\\d\\d)?(?:\\s?[AaPp][Mm])?]?\\s+");

	/** @return the text without color codes: Hypixel sends many messages as text containing them ("§2Guild > ..."), and a code's digit is not content */
	private static String plain(String text) {
		return text.indexOf('§') < 0 ? text : TextColors.noColors(text);
	}

	private static Channel classify(String text) {
		// whatever precedes the channel name (color codes, timestamps of this or other mods, a symbol, a server-inserted space) does not identify
		// the channel
		String plain = plain(text).stripLeading();
		for (int i = 0; i < 3; i++) {
			String cut = ANY_STAMP.matcher(plain).replaceFirst("").stripLeading();
			if (cut.equals(plain)) {
				break;
			}
			plain = cut;
		}
		if (PARTY.matcher(plain).find()) {
			return Channel.PARTY;
		}
		if (GUILD.matcher(plain).find()) {
			return Channel.GUILD;
		}
		if (OFFICER.matcher(plain).find()) {
			return Channel.OFFICER;
		}
		if (DIRECT.matcher(plain).find()) {
			return Channel.DM;
		}
		return Channel.ALL;
	}

	private boolean visible(GuiMessage message) {
		if (!isEnabled() || !tabs.get()) {
			return true;
		}
		Tab tab = tabNamed(openTab);
		Info info = infoOf(message);
		if (tab == null) {
			return true;
		}
		if (tab.shows().test(info)) {
			return true;
		}
		// Hypixel's confirmation when switching to this tab's channel is shown in the tab, as the response to selecting it; switches to other
		// channels do not belong to this tab
		if (tab.channel() == null) {
			return false;
		}
		var matcher = CHANNEL_SWITCH.matcher(info.plain());
		return matcher.find() && matcher.group(1).equalsIgnoreCase(tab.channel().name());
	}

	private Tab tabNamed(String id) {
		for (Tab tab : tabsNow()) {
			if (tab.id().equals(id)) {
				return tab;
			}
		}
		return null;
	}

	/** @return the tabs that show, in the player's order; the list is made again only when they change */
	private List<Tab> tabsNow() {
		// asked for every message when the chat is filtered anew: the own tabs are only compared, with nothing made
		if (shownVersion == tabOrder.version() && ownTabsSame()) {
			return shownTabs;
		}
		shownVersion = tabOrder.version();
		for (int i = 0; i < OWN_TABS; i++) {
			shownOwn[i * OWN_FIELDS] = ownNames.get(i).get();
			shownOwn[i * OWN_FIELDS + 1] = ownModes.get(i).get();
			shownOwn[i * OWN_FIELDS + 2] = ownTexts.get(i).get();
		}
		List<Tab> made = new ArrayList<>();
		for (String id : tabOrder.enabledInOrder()) {
			Tab tab = makeTab(id);
			if (tab != null) {
				made.add(tab);
			}
		}
		shownTabs = List.copyOf(made);
		return shownTabs;
	}

	private boolean ownTabsSame() {
		for (int i = 0; i < OWN_TABS; i++) {
			if (!ownNames.get(i).get().equals(shownOwn[i * OWN_FIELDS]) || ownModes.get(i).get() != shownOwn[i * OWN_FIELDS + 1] || !ownTexts.get(i).get().equals(shownOwn[i * OWN_FIELDS + 2])) {
				return false;
			}
		}
		return true;
	}

	private Tab makeTab(String id) {
		switch (id) {
			case ALL_TAB:
				return new Tab(id, "All", Channel.ALL, info -> !keptFromAll(info));
			case "party":
				return new Tab(id, "Party", Channel.PARTY, info -> info.channel() == Channel.PARTY);
			case "guild":
				return new Tab(id, "Guild", Channel.GUILD, info -> info.channel() == Channel.GUILD);
			case "officer":
				return new Tab(id, "Officer", Channel.OFFICER, info -> info.channel() == Channel.OFFICER);
			case "private":
				return new Tab(id, "Private", Channel.DM, info -> info.channel() == Channel.DM);
			default:
				break;
		}
		for (int i = 0; i < OWN_TABS; i++) {
			if (id.equals(OWN_TAB + (i + 1))) {
				String name = ownNames.get(i).get().strip();
				if (name.isEmpty()) {
					return null;
				}
				ChatRules.FilterMode mode = ownModes.get(i).get().mode;
				String text = ownTexts.get(i).get();
				return new Tab(id, name, null, info -> ChatRules.matchesFilter(info.plain(), mode, text));
			}
		}
		for (ChatTabProvider provider : Addons.chatTabs()) {
			if (provider.tabId().equals(id)) {
				return new Tab(id, provider.title(), null, info -> id.equals(info.addonTab()));
			}
		}
		return null;
	}

	/** @return whether a message belongs only in an add-on's tab, as the player chose */
	private static boolean keptFromAll(Info info) {
		if (info.addonTab() == null) {
			return false;
		}
		for (ChatTabProvider provider : Addons.chatTabs()) {
			if (provider.tabId().equals(info.addonTab())) {
				return provider.keptOutOfAll();
			}
		}
		return false;
	}

	private static boolean keptOut() {
		for (ChatTabProvider provider : Addons.chatTabs()) {
			if (provider.keptOutOfAll()) {
				return true;
			}
		}
		return false;
	}

	/** @return what is known of a message; one the feature has not seen, such as from before it was on, is read now */
	Info infoOf(GuiMessage message) {
		Info info = infos.get(message.content());
		if (info == null) {
			info = read(message.content(), message.content().getString(), 1, null);
			remember(message.content(), info);
		}
		return info;
	}

	private Info read(Component shown, String text, int count, String time) {
		String plain = plain(text);
		String player = Minecraft.getInstance().getUser() == null ? "" : Minecraft.getInstance().getUser().getName();
		boolean mention = isEnabled() && highlight.get() && !player.equals(ChatRules.sender(plain)) && ChatRules.mentions(plain, highlightWordsWith(player));
		String addonTab = null;
		for (ChatTabProvider provider : Addons.chatTabs()) {
			try {
				if (provider.matches(plain)) {
					addonTab = provider.tabId();
					break;
				}
			} catch (RuntimeException e) {
				// an add-on's tab is never worth a line of the chat
				Debug.log("The chat tab {} could not read a message: {}", provider.tabId(), e.toString());
			}
		}
		return new Info(text, plain, classify(text), ChatRules.sender(STAMP.matcher(plain).replaceFirst("")), mention, count, time, ChatRules.link(plain) != null, addonTab);
	}

	private List<String> highlightWordsWith(String player) {
		List<String> words = new ArrayList<>();
		words.add(player);
		for (String word : highlightWords.get().split(",")) {
			words.add(word);
		}
		return words;
	}

	private void remember(Component key, Info info) {
		infos.put(key, info);
		infoOrder.addLast(key);
		while (infoOrder.size() > MOST_REMEMBERED) {
			infos.remove(infoOrder.removeFirst());
		}
	}

	/** "You are now in the GUILD channel": the channel name matches the tab (PARTY, GUILD, OFFICER). */
	private static final Pattern CHANNEL_SWITCH = Pattern.compile("You are now in the ([A-Z]+) channel");

	/** @return the message as it should be added to the chat, or null to leave it as it is: the message itself, with its count after it when it repeats an earlier one, which it then replaces */
	public Processed process(Component original) {
		if (!isEnabled()) {
			return null;
		}
		String key = original.getString();
		String plainKey = plain(key);
		var switched = CHANNEL_SWITCH.matcher(plainKey);
		if (switched.find()) {
			for (Channel known : Channel.values()) {
				if (known.name().equalsIgnoreCase(switched.group(1))) {
					typing = known;
				}
			}
		}
		long now = System.currentTimeMillis();
		// a colored bar of symbols ("§9§m-----") is still only symbols: color code letters are not text
		boolean signs = ONLY_SIGNS.matcher(plainKey).matches();
		boolean counted = compact.get() && !signs;
		int count = counted ? repeats.count(key, now) : 1;
		Component remove = counted ? repeats.replaced(key, now) : null;
		LocalTime time = LocalTime.now();
		// the time lies in its own column beside the message, never in it
		String stamp = ChatRules.time(time.getHour(), time.getMinute(), time.getSecond(), twelveHours.get(), seconds.get());
		Info info = read(original, key, count, stamp);
		// a repeat has its count after the game's text, as a part of the line, so it wraps with it like any other word
		Component shown = count > 1 ? Component.empty().append(original).append(Component.literal(ChatRules.countText(count)).withStyle(ChatFormatting.GRAY)) : original;
		remember(shown, info);
		if (counted) {
			repeats.note(key, count, now, shown);
		}
		return count > 1 || remove != null ? new Processed(shown, remove) : null;
	}

	/** @return whether a message is only a line of symbols
 (a Hypixel separator bar), with or without a leading timestamp */
	public boolean isSeparator(Component content) {
		String text = STAMP.matcher(plain(content.getString())).replaceFirst("").strip();
		return !text.isBlank() && ONLY_SIGNS.matcher(text).matches();
	}

	/** @return whether a right click hit a chat message, which is then copied to the clipboard as received, without the timestamp */
	public boolean copyAt(double mouseX, double mouseY, int button) {
		if (!isEnabled() || !copy.get() || button != 1) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		int index = lineAt(client.getWindow().getGuiScaledHeight(), mouseX, mouseY);
		if (index < 0) {
			return false;
		}
		copyLine(client, index);
		return true;
	}

	/** Copies a line's message to the clipboard, as it was received. */
	private void copyLine(Minecraft client, int index) {
		var access = (ChatComponentAccessor) client.gui.hud.getChat();
		String text = infoOf(access.lumio$lines().get(index).parent()).original();
		// strip color codes some messages contain in their text (SkyHanni writes "§fVanguard Corpse§e:"): copy only the readable text
		text = plain(text);
		client.keyboardHandler.setClipboard(text);
		// confirm with a notification at the top right rather than the action bar, which is reserved for the server
		String preview = text.strip();
		Toasts.info("Message copied", preview.length() > 90 ? preview.substring(0, 87) + "..." : preview);
	}

	/**
	 * @return whether a click hit something added to a line: Copy, Reply or a link. Reply and Copy only fill the input or the clipboard, and a link opens only after the game's own question: nothing is ever sent.
	 */
	public boolean clickLine(double mouseX, double mouseY, int button, EditBox input) {
		if (!isEnabled() || button != ScreenInput.LEFT) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		int screenHeight = client.getWindow().getGuiScaledHeight();
		int index = lineAt(screenHeight, mouseX, mouseY);
		if (index < 0) {
			return false;
		}
		var access = (ChatComponentAccessor) client.gui.hud.getChat();
		GuiMessage.Line line = access.lumio$lines().get(index);
		Info info = infoOf(line.parent());
		if (hoverActions.get()) {
			double[] inChat = toChat(screenHeight, mouseX, mouseY);
			int action = decor.actionAt(index, inChat[0], inChat[1]);
			if (action == 0) {
				copyLine(client, index);
				return true;
			}
			if (action == 1 && replyPrefix(info) != null) {
				input.setValue(replyPrefix(info));
				input.setFocused(true);
				UiSounds.click();
				return true;
			}
		}
		return links.get() && info.link() && openLink(client, line, (mouseX - left()) / scale() - ChatLayout.textLeft(timeColumn(), heads.get()));
	}


	/** @return whether a click on a line was on its link, which then opens after the game's own question */
	private static boolean openLink(Minecraft client, GuiMessage.Line line, double across) {
		String text = textOf(line.content());
		int[] link = ChatRules.link(text);
		if (link == null) {
			return false;
		}
		int from = client.font.width(text.substring(0, link[0]));
		int to = from + client.font.width(text.substring(link[0], link[1]));
		if (across < from || across >= to) {
			return false;
		}
		try {
			// from what someone wrote: a link that is no address is not opened
			URI url = URI.create(ChatRules.openable(text.substring(link[0], link[1])));
			ConfirmLinkScreen.confirmLinkNow(client.gui.screen(), url);
		} catch (IllegalArgumentException e) {
			Debug.log("A link in the chat could not be read: {}", e.toString());
		}
		return true;
	}


	// ------------------------------------------------------------------------------------------------------ tabs

	/** The tabs in the bar at this width: those in the row with their places, those in More, and where More lies. */
	private record TabRow(List<Tab> row, int[] lefts, int[] widths, List<Tab> more, int moreLeft, int moreWidth) {
	}

	private TabRow tabRow;
	private List<Tab> tabRowFor;
	private String tabRowOpen;
	private int tabRowLeft;
	private int tabRowRoom;
	private float tabRowSharpness;

	/** @return how wide a tab is */
	private static int tabWidth(Font font, String label) {
		return Math.round(Ui.measureScaled(font, label, TAB_TEXT)) + TAB_PAD * 2;
	}

	private static int moreWidth(Font font) {
		return tabWidth(font, MORE) + MORE_ICON + MORE_ICON_GAP;
	}

	/** @return the row, worked out again only when the tabs, the open tab or the bar's room change */
	private TabRow tabRow(Font font, List<Tab> all) {
		int left = barLeft();
		int room = tabsRight() - left;
		if (tabRow != null && tabRowFor == all && tabRowOpen.equals(openTab) && tabRowLeft == left && tabRowRoom == room && tabRowSharpness == sharpness()) {
			return tabRow;
		}
		int[] widths = new int[all.size()];
		for (int i = 0; i < widths.length; i++) {
			widths[i] = tabWidth(font, all.get(i).label());
		}
		int more = moreWidth(font);
		ChatTabFit.Fit fit = ChatTabFit.of(widths, indexOfTab(all, openTab), room, more, TAB_GAP);
		List<Tab> row = new ArrayList<>();
		int[] lefts = new int[fit.row().length];
		int[] rowWidths = new int[fit.row().length];
		int x = left;
		for (int i = 0; i < fit.row().length; i++) {
			row.add(all.get(fit.row()[i]));
			lefts[i] = x;
			rowWidths[i] = widths[fit.row()[i]];
			x += rowWidths[i] + TAB_GAP;
		}
		List<Tab> over = new ArrayList<>();
		for (int index : fit.more()) {
			over.add(all.get(index));
		}
		tabRow = new TabRow(row, lefts, rowWidths, over, x, more);
		tabRowFor = all;
		tabRowOpen = openTab;
		tabRowLeft = left;
		tabRowRoom = room;
		tabRowSharpness = sharpness();
		return tabRow;
	}

	/** @return what text is measured for: its width in the font's own steps follows how many screen pixels a unit is, so it changes with the GUI scale and the tabs' size */
	private float sharpness() {
		return (float) Minecraft.getInstance().getWindow().getGuiScale() * tabsSize.get();
	}

	/** @return how wide the panel has to be on the screen for the whole tab row: all tabs and the search icon; what the Auto width stays above */
	private int tabsNeed(Font font) {
		if (!barShown()) {
			return 0;
		}
		int need = 0;
		if (tabs.get()) {
			List<Tab> all = tabsNow();
			if (all != needMeasuredFor || needSharpness != sharpness()) {
				// measured as they are drawn: at the tabs' size
				float zoom = Ui.zoom();
				Ui.setZoom(tabsSize.get());
				int[] widths = new int[all.size()];
				for (int i = 0; i < widths.length; i++) {
					widths[i] = tabWidth(font, all.get(i).label());
				}
				Ui.setZoom(zoom);
				needMeasuredFor = all;
				needSharpness = sharpness();
				needMeasured = ChatTabFit.needed(widths, TAB_GAP);
			}
			need = needMeasured;
		}
		if (search.get()) {
			need += TAB_HEIGHT + (need > 0 ? TAB_GAP : 0);
		}
		// one over: what is left for the row is rounded in the tabs' own scale
		return Mth.ceil((need + 1) * tabsSize.get()) + 2 * BAR_SIDE;
	}

	private List<Tab> needMeasuredFor;
	private int needMeasured;
	private float needSharpness;

	private final ChatMoreMenu moreMenu = new ChatMoreMenu();
	private long lastTabsFrame;
	private int tabTop = 120;

	/** Places the tabs in the middle of the panel's bar; they are drawn before the command suggestions, so those appear above them. */
	private int updateTabTop(int guiHeight) {
		tabTop = panelTop(guiHeight) + (ChatLayout.BAR - 1 - TAB_HEIGHT) / 2;
		return tabTop;
	}

	/** Draws the tabs over the chat screen. */
	public void renderTabs(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!isEnabled() || !barShown()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		updateTabTop(graphics.guiHeight());
		// a chat that was closed and reopened starts with the list of More closed
		long now = System.currentTimeMillis();
		if (now - lastTabsFrame > CLOSED_AFTER_MILLIS) {
			moreMenu.close();
		}
		lastTabsFrame = now;
		// tabs and search at the configured scale, around the left of the bar
		float s = tabsSize.get();
		float[] anchor = tabsAnchor();
		double[] mouse = unscaled(mouseX, mouseY);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(anchor[0], anchor[1]);
		pose.scale(s, s);
		pose.translate(-anchor[0], -anchor[1]);
		Ui.setZoom(s);
		try {
			renderTabsScaled(graphics, client, (int) Math.round(mouse[0]), (int) Math.round(mouse[1]));
		} finally {
			pose.popMatrix();
			Ui.setZoom(1f);
		}
	}

	/** @return where the tabs end: before the search icon if there is one, which always stays */
	private int tabsRight() {
		return search.get() ? barRight() - TAB_HEIGHT - TAB_GAP : barRight();
	}

	private void renderTabsScaled(GuiGraphicsExtractor graphics, Minecraft client, int mouseX, int mouseY) {
		Ui ui = new Ui(graphics, client.font, HudStyle.palette(), accentArgb(), 0f, false);
		int top = tabTop;
		if (tabs.get()) {
			attach(client.gui.hud.getChat());
			List<Tab> all = tabsNow();
			if (!all.isEmpty()) {
				// only a tab whose own name is longer than the whole bar still reaches its end: it is cut there, inside the panel
				graphics.enableScissor(barLeft(), top, tabsRight(), top + TAB_HEIGHT);
				try {
					drawTabs(ui, client, all, top, mouseX, mouseY);
				} finally {
					graphics.disableScissor();
				}
			}
		}
		if (search.get()) {
			renderSearch(ui, graphics, client, top, mouseX, mouseY);
		}
	}

	private void drawTabs(Ui ui, Minecraft client, List<Tab> all, int top, int mouseX, int mouseY) {
		TabRow row = tabRow(client.font, all);
		// the soft selection slides to the clicked tab
		int chosen = Math.max(0, indexOfTab(row.row(), openTab));
		// while the search is out only the active tab stays, at the left of the row
		float others = ChatSearchLook.otherTabs(searchEased);
		boolean answer = ChatSearchLook.tabsAnswer(searchOpen, searchEased);
		float lightX = markX.follow(row.lefts()[chosen]);
		float lightWidth = markWidth.follow(row.widths()[chosen]);
		ui.rounded(ChatSearchLook.activeLeft(Math.round(lightX), barLeft(), searchEased), top, Math.round(lightWidth), TAB_HEIGHT, TAB_RADIUS, TAB_ACTIVE);
		float textY = top + TAB_HEIGHT / 2f - 4.5f * TAB_TEXT;
		for (int i = 0; i < row.row().size(); i++) {
			boolean selected = i == chosen;
			if (!selected && others <= 0f) {
				continue;
			}
			int x = selected ? ChatSearchLook.activeLeft(row.lefts()[i], barLeft(), searchEased) : row.lefts()[i];
			int width = row.widths()[i];
			boolean hover = answer && mouseX >= x && mouseX < x + width && mouseY >= top && mouseY < top + TAB_HEIGHT;
			if (hover && !selected) {
				ui.rounded(x, top, width, TAB_HEIGHT, TAB_RADIUS, TAB_HOVER);
			}
			String label = row.row().get(i).label();
			if (selected) {
				ui.textAtScaledBold(label, x + TAB_PAD, textY, TAB_TEXT, 0xFFFFFFFF);
			} else {
				ui.textAtScaled(label, x + TAB_PAD, textY, TAB_TEXT, Ui.withOpacity(0xFFFFFFFF, (hover ? 1f : TAB_TEXT_OPACITY) * others));
			}
		}
		if (!row.more().isEmpty() && others > 0f) {
			int x = row.moreLeft();
			boolean hover = answer && mouseX >= x && mouseX < x + row.moreWidth() && mouseY >= top && mouseY < top + TAB_HEIGHT;
			if (hover || moreMenu.isOpen()) {
				ui.rounded(x, top, row.moreWidth(), TAB_HEIGHT, TAB_RADIUS, TAB_HOVER);
			}
			int color = Ui.withOpacity(0xFFFFFFFF, (hover || moreMenu.isOpen() ? 1f : TAB_TEXT_OPACITY) * others);
			ui.textAtScaled(MORE, x + TAB_PAD, textY, TAB_TEXT, color);
			ui.icon("chevron", x + row.moreWidth() - TAB_PAD - MORE_ICON, top + (TAB_HEIGHT - MORE_ICON) / 2, MORE_ICON, color);
		}
	}

	/** Draws the list of More over the lines, which the screen draws after the tabs: called last of all. */
	public void renderMoreMenu(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
		if (!isEnabled() || !tabs.get() || !moreMenu.isOpen()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		TabRow row = tabRow(client.font, tabsNow());
		if (row.more().isEmpty()) {
			moreMenu.close();
			return;
		}
		List<String> labels = new ArrayList<>();
		for (Tab tab : row.more()) {
			labels.add(tab.label());
		}
		graphics.nextStratum();
		float s = tabsSize.get();
		float[] anchor = tabsAnchor();
		double[] mouse = unscaled(mouseX, mouseY);
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(anchor[0], anchor[1]);
		pose.scale(s, s);
		pose.translate(-anchor[0], -anchor[1]);
		Ui.setZoom(s);
		try {
			Ui ui = new Ui(graphics, client.font, HudStyle.palette(), accentArgb(), 0f, false);
			int width = moreMenuWidth(client.font, row);
			ChatMoreMenu.draw(ui, labels, moreMenuLeft(row, width), ChatMoreMenu.top(tabTop, TAB_HEIGHT), width, TAB_TEXT, (int) Math.round(mouse[0]), (int) Math.round(mouse[1]));
		} finally {
			pose.popMatrix();
			Ui.setZoom(1f);
		}
	}

	private static int moreMenuWidth(Font font, TabRow row) {
		List<Integer> widths = new ArrayList<>();
		for (Tab tab : row.more()) {
			widths.add(Math.round(Ui.measureScaled(font, tab.label(), TAB_TEXT)));
		}
		return ChatMoreMenu.width(widths, row.moreWidth());
	}

	/** @return where the list of More starts: under its button, and inside the panel */
	private int moreMenuLeft(TabRow row, int width) {
		return Math.max(barLeft(), Math.min(row.moreLeft(), barRight() - width));
	}

	/** @return whether a click belonged to More: a tab of its list is selected; a click anywhere else closes the list and goes on */
	private boolean clickMore(Minecraft client, TabRow row, double mouseX, double mouseY) {
		if (!row.more().isEmpty() && mouseY >= tabTop && mouseY < tabTop + TAB_HEIGHT && mouseX >= row.moreLeft() && mouseX < row.moreLeft() + row.moreWidth()) {
			moreMenu.toggle();
			UiSounds.click();
			return true;
		}
		if (!moreMenu.isOpen()) {
			return false;
		}
		int width = moreMenuWidth(client.font, row);
		int index = ChatMoreMenu.rowAt(mouseX, mouseY, moreMenuLeft(row, width), ChatMoreMenu.top(tabTop, TAB_HEIGHT), width, row.more().size());
		moreMenu.close();
		if (index >= 0) {
			select(client, row.more().get(index));
			return true;
		}
		return false;
	}

	/** @return whether a tab was clicked: it is selected, and on Hypixel the chat channel switches with it */
	public boolean clickTabs(double mouseX, double mouseY, int button) {
		if (!isEnabled() || button != 0) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		// tabs are drawn scaled: transform the mouse into their space
		double[] mouse = unscaled(mouseX, mouseY);
		mouseX = mouse[0];
		mouseY = mouse[1];
		if (search.get() && clickSearch(client, mouseX, mouseY)) {
			return true;
		}
		if (!tabs.get()) {
			return false;
		}
		int top = tabTop;
		List<Tab> all = tabsNow();
		if (all.isEmpty()) {
			return false;
		}
		TabRow row = tabRow(client.font, all);
		if (!ChatSearchLook.tabsAnswer(searchOpen, searchEased)) {
			// the other tabs are not there: the active one stays what it is
			return mouseY >= top && mouseY < top + TAB_HEIGHT && mouseX >= barLeft() && mouseX < barLeft() + activeTabWidth();
		}
		if (clickMore(client, row, mouseX, mouseY)) {
			return true;
		}
		if (mouseY < top || mouseY >= top + TAB_HEIGHT) {
			return false;
		}
		for (int i = 0; i < row.row().size(); i++) {
			if (mouseX >= row.lefts()[i] && mouseX < row.lefts()[i] + row.widths()[i]) {
				select(client, row.row().get(i));
				return true;
			}
		}
		return false;
	}

	private static int indexOfTab(List<Tab> all, String id) {
		for (int i = 0; i < all.size(); i++) {
			if (all.get(i).id().equals(id)) {
				return i;
			}
		}
		return -1;
	}

	private void select(Minecraft client, Tab chosen) {
		if (chosen.id().equals(openTab)) {
			return;
		}
		openTab = chosen.id();
		List<Tab> all = tabsNow();
		UiSounds.tick((float) Math.max(0, indexOfTab(all, chosen.id())) / Math.max(1, all.size() - 1));

		// rebuild the chat lines with the filter
		ChatComponent chat = client.gui.hud.getChat();
		reapply(chat);
		chat.resetChatScroll();
		var server = client.getCurrentServer();
		if (chosen.channel() != null) {
			typing = chosen.channel();
		}
		if (chosen.channel() != null && chosen.channel().command != null && server != null && server.ip != null && server.ip.toLowerCase().contains("hypixel") && client.getConnection() != null) {
			client.getConnection().sendCommand("chat " + chosen.channel().command);
		}
	}

	// ------------------------------------------------------------------------------------------------------ search

	private static final float TAB_TEXT_OPACITY = 0.7f;
	/** From the field's right end to its close button, and from one button to the next. */
	private static final int FIELD_CLOSE = 8;
	private static final int FIELD_STEP = 11;

	private boolean searchOpen;
	private boolean searchFocused;
	private String query = "";
	/** Search field open progress from 0 to 1, and when the chat was last drawn: a reopened chat starts without a search. */
	private float openAmount;
	/** The same as it is seen, for the tabs, which are drawn first. */
	private float searchEased;
	private long lastSearchFrame;
	private long caretFrom;
	/** When the chat last jumped to a match, for the line flash. */
	private long jumpedAt;

	/** The matches: first and last chat line index of every matching message, newest first. */
	private final List<int[]> results = new ArrayList<>();
	private int current;
	/** The message of the selected match, so it stays selected when new messages arrive and the lines shift. */
	private GuiMessage currentMessage;
	/** What the matches were computed for: the query, and the line state (count and newest line). */
	private String resultsFor = null;
	private int resultsSize = -1;
	private Object resultsNewest;
	/** Lowercased text of every message, computed once: the search rescans thousands of them whenever something new arrives. */
	private final Map<GuiMessage, String> lowered = new IdentityHashMap<>();

	/** @return whether the search field has keyboard focus rather than the chat input */
	public boolean searchFocused() {
		return searchOpen && searchFocused && isEnabled() && search.get();
	}

	/** @return how wide the active tab is, or 0 where no tabs are shown */
	private int activeTabWidth() {
		if (!tabs.get()) {
			return 0;
		}
		List<Tab> all = tabsNow();
		if (all.isEmpty()) {
			return 0;
		}
		TabRow row = tabRow(Minecraft.getInstance().font, all);
		return row.widths()[Math.max(0, indexOfTab(row.row(), openTab))];
	}

	/** @return where the open search field starts: it takes the row, up to the active tab */
	private int fieldOpenLeft() {
		return ChatSearchLook.openLeft(barLeft(), activeTabWidth(), TAB_GAP);
	}

	/** Draws text in the search field, in the size of the tabs. */
	private static void fieldText(Ui ui, String text, int x, int middle, int color) {
		ui.textAtScaled(text, x, middle - 4.5f * TAB_TEXT, TAB_TEXT, color);
	}

	private static int fieldWidthOf(Ui ui, String text) {
		return Math.round(ui.widthScaled(text, TAB_TEXT));
	}

	private void openSearch() {
		searchOpen = true;
		searchFocused = true;
		moreMenu.close();
		caretFrom = System.currentTimeMillis();
		UiSounds.play(UiSounds.Cue.CLICK);
	}

	private void closeSearch() {
		searchOpen = false;
		searchFocused = false;
		query = "";
		results.clear();
		resultsFor = null;
		currentMessage = null;
		Minecraft.getInstance().gui.hud.getChat().resetChatScroll();
	}

	private String lowerOf(GuiMessage message) {
		String text = lowered.get(message);
		if (text == null) {
			text = plain(message.content().getString()).toLowerCase(Locale.ROOT);
			lowered.put(message, text);
		}
		return text;
	}

	/** Recomputes the matches only when the query or the chat lines changed, not every frame. */
	private void refreshResults(ChatComponent chat) {
		var lines = ((ChatComponentAccessor) chat).lumio$lines();
		Object newest = lines.isEmpty() ? null : lines.get(0);
		if (query.equals(resultsFor) && lines.size() == resultsSize && newest == resultsNewest) {
			return;
		}
		boolean newText = !query.equals(resultsFor);
		resultsFor = query;
		resultsSize = lines.size();
		resultsNewest = newest;
		results.clear();
		if (lowered.size() > lines.size() * 2 + 500) {
			lowered.clear();
		}
		String needle = query.strip().toLowerCase(Locale.ROOT);
		if (needle.isEmpty()) {
			currentMessage = null;
			return;
		}
		int keep = -1;
		for (int i = 0; i < lines.size(); i++) {
			GuiMessage parent = lines.get(i).parent();
			int start = i;
			while (i + 1 < lines.size() && lines.get(i + 1).parent() == parent) {
				i++;
			}
			if (lowerOf(parent).contains(needle)) {
				if (parent == currentMessage) {
					keep = results.size();
				}
				results.add(new int[]{start, i});
			}
		}
		if (newText || keep < 0) {
			// a new query starts at the newest match, and the chat scrolls there
			current = 0;
			currentMessage = results.isEmpty() ? null : lines.get(results.get(0)[0]).parent();
			if (!results.isEmpty()) {
				jumpTo(chat, 0);
			}
		} else {
			current = keep;
		}
	}

	/** Scrolls the chat so the match is centered in the visible area. */
	private void jumpTo(ChatComponent chat, int index) {
		if (results.isEmpty()) {
			return;
		}
		current = Math.floorMod(index, results.size());
		var access = (ChatComponentAccessor) chat;
		var lines = access.lumio$lines();
		int[] match = results.get(current);
		currentMessage = lines.get(match[0]).parent();
		int perPage = Math.max(1, chat.getLinesPerPage());
		int middle = (match[0] + match[1]) / 2;
		int target = Mth.clamp(middle - perPage / 2, 0, Math.max(0, lines.size() - perPage));
		chat.scrollChat(target - access.lumio$scroll());
		scrolling.jumpTo(target);
		jumpedAt = System.currentTimeMillis();
	}

	/** @return the match containing a line, or -1; matches are ordered by line, so this is a binary search */
	private int resultAt(int line) {
		int low = 0;
		int high = results.size() - 1;
		while (low <= high) {
			int mid = (low + high) >>> 1;
			int[] match = results.get(mid);
			if (line < match[0]) {
				high = mid - 1;
			} else if (line > match[1]) {
				low = mid + 1;
			} else {
				return mid;
			}
		}
		return -1;
	}

	private void renderSearch(Ui ui, GuiGraphicsExtractor graphics, Minecraft client, int top, int mouseX, int mouseY) {
		long now = System.currentTimeMillis();
		// a chat that was closed and reopened starts without a search
		if (now - lastSearchFrame > 300L && lastSearchFrame != 0L && searchOpen) {
			closeSearch();
			openAmount = 0f;
			searchEased = 0f;
		}
		float dt = lastSearchFrame == 0L ? 0f : Math.min(0.1f, (now - lastSearchFrame) / 1000f);
		lastSearchFrame = now;
		boolean animated = ModFeatures.MENU.animations();
		openAmount = ChatSearchLook.advance(openAmount, searchOpen, dt, animated);
		searchEased = ChatSearchLook.eased(openAmount);
		if (searchOpen) {
			refreshResults(client.gui.hud.getChat());
		}

		// the field grows to the left out of the icon at the bar's right end, over the tabs
		int right = barRight();
		int left = fieldOpenLeft();
		int shownLeft = ChatSearchLook.fieldLeft(right, TAB_HEIGHT, left, searchEased);
		int width = right - shownLeft;
		boolean overField = mouseX >= shownLeft && mouseX < right && mouseY >= top && mouseY < top + TAB_HEIGHT;
		if (searchFocused()) {
			ScreenInput.wantText();
		}
		// the tabs' own glass: closed a quiet button, open a field with a thin line, and a soft accent line when it has the keyboard
		ui.rounded(shownLeft, top, width, TAB_HEIGHT, TAB_RADIUS, Ui.mix(ChatSearchLook.BUTTON_FILL, ChatSearchLook.FIELD_FILL, searchEased));
		if (overField && !searchOpen) {
			ui.rounded(shownLeft, top, width, TAB_HEIGHT, TAB_RADIUS, TAB_HOVER);
		}
		if (searchEased > 0.01f) {
			if (searchFocused()) {
				ui.ring(shownLeft - 1, top - 1, width + 2, TAB_HEIGHT + 2, TAB_RADIUS + 1f, 1, Ui.withOpacity(ui.accent, ChatSearchLook.FOCUS_GLOW * searchEased));
				ui.ring(shownLeft, top, width, TAB_HEIGHT, TAB_RADIUS, 1, Ui.withOpacity(ui.accent, ChatSearchLook.FOCUS_LINE * searchEased));
			} else {
				ui.ring(shownLeft, top, width, TAB_HEIGHT, TAB_RADIUS, 1, Ui.withOpacity(ChatSearchLook.FIELD_LINE, searchEased));
			}
		}
		ui.icon("search", shownLeft + (TAB_HEIGHT - ICON_SIZE) / 2, top + (TAB_HEIGHT - ICON_SIZE) / 2, ICON_SIZE, Ui.withOpacity(0xFFFFFFFF, overField || searchOpen ? 1f : TAB_TEXT_OPACITY));
		if (openAmount < 0.05f) {
			return;
		}
		graphics.enableScissor(shownLeft, top, right, top + TAB_HEIGHT);
		try {
			float fade = Mth.clamp((openAmount - 0.3f) / 0.7f, 0f, 1f);
			int middle = top + TAB_HEIGHT / 2;
			// on the right: close button, previous/next match arrows, and the match count
			int closeX = right - FIELD_CLOSE;
			drawButton(ui, "close", closeX, middle, mouseX, mouseY, fade, 6);
			int textLeft = left + TAB_HEIGHT;
			int textRight = closeX - 4;
			if (!query.isBlank()) {
				int downX = closeX - FIELD_STEP;
				int upX = downX - FIELD_STEP;
				boolean any = !results.isEmpty();
				drawArrow(ui, graphics, upX, middle, true, mouseX, mouseY, any ? fade : fade * 0.35f);
				drawArrow(ui, graphics, downX, middle, false, mouseX, mouseY, any ? fade : fade * 0.35f);
				String count = any ? (current + 1) + " of " + results.size() : "No results";
				int countWidth = fieldWidthOf(ui, count);
				int countX = upX - 7 - countWidth;
				fieldText(ui, count, countX, middle, Ui.withOpacity(any ? 0xFFFFFFFF : SystemColors.RED_TEXT, 0.7f * fade));
				textRight = countX - 4;
			}
			int room = Math.max(10, textRight - textLeft);
			if (query.isEmpty()) {
				fieldText(ui, "Search the chat", textLeft, middle, Ui.withOpacity(0xFFFFFFFF, 0.45f * fade));
			} else {
				// show the end of the query when it is longer than the field
				String shown = query;
				while (shown.length() > 1 && fieldWidthOf(ui, shown) > room) {
					shown = shown.substring(1);
				}
				fieldText(ui, shown, textLeft, middle, Ui.withOpacity(0xFFFFFFFF, fade));
				textLeft += fieldWidthOf(ui, shown);
			}
			if (searchFocused() && ((now - caretFrom) / 530L) % 2 == 0) {
				int caretX = query.isEmpty() ? left + TAB_HEIGHT : textLeft + 1;
				ui.rounded(caretX, middle - 4, 1, 8, 0.5f, Ui.withOpacity(0xFFFFFFFF, fade));
			}
		} finally {
			graphics.disableScissor();
		}
	}

	private static void drawButton(Ui ui, String icon, int centerX, int centerY, int mouseX, int mouseY, float fade, int size) {
		boolean over = Math.abs(mouseX - centerX) <= 5 && Math.abs(mouseY - centerY) <= 6;
		// a small round button
		ui.rounded(centerX - 5, centerY - 5, 10, 10, Radius.pill(10), Ui.withOpacity(0xFFFFFFFF, (over ? 0.2f : 0.1f) * fade));
		ui.icon(icon, centerX - size / 2, centerY - size / 2, size, Ui.withOpacity(0xFFFFFFFF, (over ? 1f : 0.7f) * fade));
	}

	/** Draws an arrow button: up goes to the previous (older, higher) match, down to the next. */
	private static void drawArrow(Ui ui, GuiGraphicsExtractor graphics, int centerX, int centerY, boolean up, int mouseX, int mouseY, float fade) {
		boolean over = Math.abs(mouseX - centerX) <= 5 && Math.abs(mouseY - centerY) <= 6;
		if (over) {
			ui.rounded(centerX - 5, centerY - 5, 11, 11, Radius.pill(11), Ui.withOpacity(0xFFFFFFFF, 0.14f * fade));
		}
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(centerX, centerY);
		// the chevron points down by default
		if (up) {
			pose.rotate((float) Math.PI);
		}
		ui.icon("chevron", -3, -3, 6, Ui.withOpacity(0xFFFFFFFF, (over ? 1f : 0.7f) * fade));
		pose.popMatrix();
	}

	/**
	 * Highlights the matches in the visible lines: a soft highlight on every matching message, a stronger one on the selected match (flashing when the
	 * chat jumps to it), and the query marked within the text. Only on-screen lines are processed.
	 */
	public void renderMatches(GuiGraphicsExtractor graphics) {
		if (!isEnabled() || !search.get() || !searchOpen || results.isEmpty()) {
			return;
		}
		Minecraft client = Minecraft.getInstance();
		ChatComponent chat = client.gui.hud.getChat();
		long now = System.currentTimeMillis();
		var access = (ChatComponentAccessor) chat;
		var lines = access.lumio$lines();
		float scale = (float) access.lumio$scale();
		int scroll = access.lumio$scroll();
		int perPage = chat.getLinesPerPage();
		// the marks go where the lines are: the same place, the same rows, the text in its column
		int entryHeight = Math.max(1, access.lumio$lineHeight());
		int chatBottom = Mth.floor(baseBottom(graphics.guiHeight()) / scale);
		int panel = panelWidthInChat();
		int textColumn = ChatLayout.textLeft(timeColumn(), heads.get());
		String needle = query.strip().toLowerCase(Locale.ROOT);
		float flash = Math.max(0f, 1f - (now - jumpedAt) / 700f);

		// above the chat lines, which the screen draws after the tabs: a highlighter, light enough to read the text through it
		graphics.nextStratum();
		var pose = graphics.pose();
		pose.pushMatrix();
		boolean clipped = clipRows(graphics);
		pose.translate(left(), moveY() + scrollShift());
		pose.scale(scale, scale);
		Ui.setZoom(scale);
		Ui ui = new Ui(graphics, client.font, HudStyle.palette(), accentArgb(), 0f, false);
		try {
			for (int v = 0, rows = rowsDrawn(perPage); v < rows && scroll + v < lines.size(); v++) {
				int index = scroll + v;
				int match = resultAt(index);
				if (match < 0) {
					continue;
				}
				boolean chosen = match == current;
				int entryBottom = chatBottom - v * entryHeight;
				int entryTop = entryBottom - entryHeight;
				int textY = entryTop + (entryHeight - GAME_TEXT_HEIGHT) / 2;
				// the row, as wide as the chat background
				ui.rounded(ChatLayout.ROW_INSET, entryTop, panel - 2 * ChatLayout.ROW_INSET, entryHeight, Radius.XXS, Ui.withOpacity(ui.accent, (chosen ? 0.08f + 0.22f * flash : 0.04f)));
				// mark the query within the line, measured with the line's styles (bold is wider)
				var content = lines.get(index).content();
				StringBuilder text = new StringBuilder();
				content.accept((position, style, codepoint) -> {
					text.appendCodePoint(codepoint);
					return true;
				});
				String lower = text.toString().toLowerCase(Locale.ROOT);
				int from = 0;
				while (!needle.isEmpty() && from < lower.length() && (from = lower.indexOf(needle, from)) >= 0) {
					int startPoint = text.codePointCount(0, Math.min(from, text.length()));
					int endPoint = text.codePointCount(0, Math.min(from + needle.length(), text.length()));
					int x = widthUpTo(client.font, content, startPoint);
					// text width includes the spacing after its last glyph: end the mark at the glyph
					int w = widthUpTo(client.font, content, endPoint) - x - 1;
					int markHeight = Math.min(entryHeight, 10);
					if (chosen) {
						// a full mark would hide the letters under it: they come again, white, on top of it
						ui.ring(textColumn + x - 2, textY - 2, w + 4, markHeight + 2, 2f, 1, Ui.withOpacity(ui.accent, 0.3f));
						ui.rounded(textColumn + x - 1, textY - 1, w + 2, markHeight, 1.5f, ui.accent);
						graphics.text(client.font, lettersOf(content, startPoint, endPoint), textColumn + x, textY, 0xFFFFFFFF, false);
					} else {
						ui.rounded(textColumn + x - 1, textY - 1, w + 2, markHeight, 1.5f, Ui.withOpacity(ui.accent, 0.2f));
						ui.ring(textColumn + x - 1, textY - 1, w + 2, markHeight, 1.5f, 1, Ui.withOpacity(ui.accent, 0.53f));
					}
					from += needle.length();
				}
			}
		} finally {
			pose.popMatrix();
			Ui.setZoom(1f);
			if (clipped) {
				graphics.disableScissor();
			}
		}
	}

	/** @return some letters of a line, in their own style but white */
	private static FormattedCharSequence lettersOf(FormattedCharSequence content, int from, int to) {
		return sink -> {
			int[] count = {0};
			return content.accept((position, style, codepoint) -> {
				int at = count[0]++;
				return at < from || at >= to || sink.accept(position, style.withColor(0xFFFFFF), codepoint);
			});
		};
	}

	/** @return the rendered width of a line's first characters, including each character's style */
	private static int widthUpTo(Font font, FormattedCharSequence content, int letters) {
		if (letters <= 0) {
			return 0;
		}
		return font.width((FormattedCharSequence) sink -> {
			int[] count = {0};
			return content.accept((position, style, codepoint) -> count[0]++ < letters && sink.accept(position, style, codepoint));
		});
	}

	/** @return whether a click hit the search: opens it, focuses it, closes it, or goes to the previous or next match */
	private boolean clickSearch(Minecraft client, double mouseX, double mouseY) {
		int right = barRight();
		int top = tabTop;
		int fieldLeft = searchOpen ? fieldOpenLeft() : right - TAB_HEIGHT;
		boolean inside = mouseX >= fieldLeft && mouseX < right && mouseY >= top && mouseY < top + TAB_HEIGHT;
		if (!inside) {
			searchFocused = false;
			return false;
		}
		if (!searchOpen) {
			openSearch();
			return true;
		}
		int middle = top + TAB_HEIGHT / 2;
		int closeX = right - FIELD_CLOSE;
		ChatComponent chat = client.gui.hud.getChat();
		if (Math.abs(mouseX - closeX) <= 5 && Math.abs(mouseY - middle) <= 6) {
			closeSearch();
			UiSounds.play(UiSounds.Cue.CLOSE);
			return true;
		}
		if (!query.isBlank()) {
			int downX = closeX - FIELD_STEP;
			int upX = downX - FIELD_STEP;
			if (Math.abs(mouseX - upX) <= 5) {
				step(chat, 1);
				return true;
			}
			if (Math.abs(mouseX - downX) <= 5) {
				step(chat, -1);
				return true;
			}
		}
		searchFocused = true;
		caretFrom = System.currentTimeMillis();
		return true;
	}

	/** Moves to the next match up (older, 1) or down (newer, -1). */
	private void step(ChatComponent chat, int direction) {
		if (results.isEmpty()) {
			return;
		}
		jumpTo(chat, current + direction);
		UiSounds.tick(results.size() <= 1 ? 0.5f : (float) current / (results.size() - 1));
	}

	/** Handles a key in the chat: Ctrl+F opens the search, and while it is focused all keys go to it. @return whether the key was consumed */
	public boolean searchKey(int key, int modifiers) {
		if (isEnabled() && moreMenu.isOpen() && key == InputConstants.KEY_ESCAPE) {
			moreMenu.close();
			return true;
		}
		if (!isEnabled() || !search.get()) {
			return false;
		}
		boolean ctrl = (modifiers & ScreenInput.CONTROL) != 0;
		if (ctrl && ScreenInput.letter(key, 'F')) {
			if (searchOpen) {
				searchFocused = true;
				caretFrom = System.currentTimeMillis();
			} else {
				openSearch();
			}
			return true;
		}
		if (!searchFocused()) {
			return false;
		}
		Minecraft client = Minecraft.getInstance();
		ChatComponent chat = client.gui.hud.getChat();
		caretFrom = System.currentTimeMillis();
		switch (key) {
			case InputConstants.KEY_ESCAPE -> {
				closeSearch();
				UiSounds.play(UiSounds.Cue.CLOSE);
			}
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER ->
				step(chat, (modifiers & ScreenInput.SHIFT) != 0 ? -1 : 1);
			case InputConstants.KEY_UP -> step(chat, 1);
			case InputConstants.KEY_DOWN -> step(chat, -1);
			case InputConstants.KEY_TAB -> searchFocused = false;
			case InputConstants.KEY_BACKSPACE -> {
				if (ctrl) {
					// delete the last word, as in any text field
					String trimmed = query.stripTrailing();
					int space = trimmed.lastIndexOf(' ');
					query = space < 0 ? "" : trimmed.substring(0, space + 1);
				} else if (!query.isEmpty()) {
					query = query.substring(0, query.offsetByCodePoints(query.length(), -1));
				}
			}
			case InputConstants.KEY_V -> {
				if (ctrl) {
					String pasted = client.keyboardHandler.getClipboard().replaceAll("[\\r\\n]+", " ");
					query = (query + pasted).substring(0, Math.min(100, (query + pasted).length()));
				}
			}
			default -> {
			}
		}
		// every other key goes to the search too, so nothing is typed into the chat input while the search is focused
		return true;
	}

	/** Handles a typed character, which goes to the search while it is focused. @return whether it was consumed */
	public boolean searchChar(int codepoint, boolean allowed) {
		if (!searchFocused()) {
			return false;
		}
		if (allowed && query.length() < 100) {
			query += Character.toString(codepoint);
			caretFrom = System.currentTimeMillis();
		}
		return true;
	}
}
