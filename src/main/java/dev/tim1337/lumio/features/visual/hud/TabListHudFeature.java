package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.TabCard;
import dev.tim1337.lumio.api.TabCardProvider;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.api.experimental.util.ServerText;
import dev.tim1337.lumio.api.experimental.util.TabSections;
import dev.tim1337.lumio.config.SettingsView;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.config.setting.Setting;
import dev.tim1337.lumio.hud.HudPanelFeature;
import dev.tim1337.lumio.mixin.accessor.PlayerTabOverlayAccessor;
import dev.tim1337.lumio.ui.HudEditScreen;
import dev.tim1337.lumio.ui.HudParts;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerFaceExtractor;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.GameType;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The player list while its key is held, wide like the vanilla one: the server's header and footer centered, the players in columns of up to
 * twenty (head, rank tag in its color, name, ping bars; you first and highlighted, then in the server's order), and on SkyBlock the info columns
 * of the list as cards in a grid on the right. While it is open the layout stays still: columns, widths and card sizes are set when it opens and
 * only grow, players keep their spot and fade in or out there, and cards update in place. It opens and closes as one piece, fading and growing
 * from its top center. Only who and what the server lists is shown.
 */
public class TabListHudFeature extends HudPanelFeature {
	/**
	 * A player of the list: a stable key (the profile's id), the name as the server shows it split into the rank tag before the name and the
	 * name; {@code real} is false for rows a server fakes without a player behind them.
	 */
	public record Entry(String key, Component tag, Component name, PlayerSkin skin, int latency, String score, boolean self, boolean real) {
	}

	/**
	 * Moves the players a server writes into its text columns ("[502] Steve123") from the info lines to the players, without doubles: one who is listed already keeps the place
	 * and takes the column's name, with its colors; the server's own row of the player often has none.
	 *
	 * @param skins  the head of a line, or null
	 * @param self   the player's own name
	 * @return the info lines that are left
	 */
	public static List<Component> takeColumnPlayers(List<Entry> players, List<Component> info, java.util.function.Function<Component, PlayerSkin> skins, String self) {
		List<Component> left = new ArrayList<>();
		java.util.Map<String, Integer> known = new java.util.HashMap<>();
		for (int i = 0; i < players.size(); i++) {
			known.put(TabPlayerRows.key(players.get(i).name().getString().split(" ", 2)[0]), i);
		}
		boolean inPlayers = false;
		// a section that lists names (Guests, Coop): its lines look like players, but they are its information, and stay for its card
		boolean inNames = false;
		for (Component line : info) {
			String plain = ServerText.clean(line, 0xFFFFFFFF).getString().strip();
			if (TabPlayerRows.isPlayersHeading(plain)) {
				inPlayers = true;
				inNames = false;
				left.add(line);
				continue;
			}
			if (TabPlayerRows.isOtherHeading(plain)) {
				inPlayers = false;
				inNames = false;
			}
			if (TabSections.isNameSection(plain)) {
				inNames = true;
				inPlayers = false;
			} else if (plain.isEmpty()) {
				inNames = false;
			}
			if (inNames) {
				left.add(line);
				continue;
			}
			String name = plain.isEmpty() ? null : TabPlayerRows.nameIn(plain, inPlayers);
			if (name == null) {
				left.add(line);
				continue;
			}
			Component[] split = splitName(ServerText.clean(line, 0xFFFFFFFF), name);
			Integer at = known.get(TabPlayerRows.key(name));
			if (at != null) {
				Entry was = players.get(at);
				players.set(at, new Entry(was.key(), split[0], split[1], was.skin(), was.latency(), was.score(), was.self(), was.real()));
			} else if (players.size() < MAX_PLAYERS) {
				boolean isSelf = name.equalsIgnoreCase(self);
				Entry entry = new Entry("column:" + name, split[0], split[1], skins.apply(line), -1, null, isSelf, true);
				if (isSelf) {
					players.addFirst(entry);
					known.replaceAll((key, index) -> index + 1);
					known.put(TabPlayerRows.key(name), 0);
				} else {
					players.add(entry);
					known.put(TabPlayerRows.key(name), players.size() - 1);
				}
			}
		}
		return left;
	}

	/** What the list shows, read from the game: the players, the lines of the info columns, the header and the footer. */
	public record Source(List<Entry> players, List<Component> info, Component header, Component footer) {
	}

	// sizes, in panel pixels
	private static final int PAD = 10;
	private static final float RADIUS = 10f;
	private static final int CELL_WIDTH = 118;
	private static final int MOST_CELL_WIDTH = 170;
	private static final int CELL_HEIGHT = 15;
	private static final int CELL_GAP = 3;
	private static final int HEAD = 8;
	private static final int SECTION_ROW = 13;
	private static final int HEADER_LINE = 10;
	private static final int HEADER_GAP = 6;
	private static final int AREA_GAP = 10;
	private static final int CARD_WIDTH = 107;
	private static final int CARD_GAP = 5;
	private static final int CARD_PAD = 6;
	private static final int CARD_TITLE = 11;
	private static final int CARD_ROW = 10;
	private static final int SKILL_ROW = 13;
	private static final float CARD_RADIUS = 6f;

	/** The widest a card gets for what it shows; what does not fit even then is shortened, and shows in full under the mouse. */
	private static final int MOST_CARD_WIDTH = 200;
	/** A little room for a value that grows while the list is open ("in 9s", "in 10s"), so the card keeps its width. */
	private static final int VALUE_ROOM = 6;
	private static final float LABEL_VALUE_GAP = 6f;
	/** Small capitals with room between them, a little wider than they are measured. */
	private static final float CAPTION_WIDER = 1.2f;
	// text sizes: names regular, rank tags small and semibold, card values semibold
	private static final float NAME_SIZE = 0.85f;
	private static final float TAG_SIZE = 0.62f;
	private static final float CARD_TEXT = 0.72f;
	private static final float FOOTER_SIZE = 0.85f;
	private static final float HINT_SCALE = 0.72f;
	private static final int CELL_FILL = 0x0DFFFFFF;
	/** The fill of every second row: very slightly lighter. */
	private static final int CELL_FILL_ZEBRA = 0x16FFFFFF;
	/** The separators: between the player columns, under the header and over the footer; a little stronger between the players and the cards. */
	private static final float SEPARATOR = 0.08f;
	private static final float AREA_SEPARATOR = 0.13f;
	private static final int CARD_FILL = 0x0DFFFFFF;
	private static final int CARD_EDGE = 0x14FFFFFF;
	private static final float SELF_FILL = 0.22f;
	private static final float SCROLL_LEAST = 12f;
	private static final float SCROLL_WIDTH = 2f;
	private static final float SCROLL_OPACITY = 0.3f;
	/** The most players listed, and the share of the screen the list may fill. */
	private static final int MAX_PLAYERS = 80;
	private static final float MOST_SCREEN_SHARE = 0.9f;
	/** How often the list is read, how long opening and closing take, and how long a player fades in or out. */
	private static final long REBUILD_NANOS = 100_000_000L;
	private static final float OPEN_SECONDS = 0.15f;
	private static final float OPEN_SCALE = 0.04f;
	private static final float PLAYER_FADE_SECONDS = 0.25f;
	private static final String HINT = "You first, then by the server's order";
	private static final List<Entry> SAMPLE = List.of(
		sample("[MVP+]", 0x55FFFF, "You", 24, true),
		sample("", 0, "Alex", 61, false),
		sample("[VIP]", 0x55FF55, "Notch", 140, false),
		sample("[MVP]", 0xFFAA00, "Jeb", 90, false),
		sample("", 0, "Herobrine", 320, false));
	private static final List<Component> SAMPLE_INFO = List.of(
		Component.literal("Area: ").withStyle(style -> style.withBold(true).withColor(0x55FFFF))
			.append(Component.literal("Dwarven Mines").withStyle(style -> style.withBold(false).withColor(0x55FFFF))),
		Component.literal(" Server: ").append(Component.literal("mini12A").withStyle(style -> style.withColor(0x888888))),
		Component.empty(),
		Component.literal("Skills:").withStyle(style -> style.withBold(true).withColor(0xFFFF55)),
		Component.literal(" Mining 50: ").append(Component.literal("35.2%").withStyle(style -> style.withColor(0x55FFFF))),
		Component.literal(" Farming 47: ").append(Component.literal("71.8%").withStyle(style -> style.withColor(0x55FFFF))));

	private static Entry sample(String tag, int color, String name, int latency, boolean self) {
		return new Entry(name, Component.literal(tag).withStyle(style -> style.withColor(color)), Component.literal(name), null, latency, null, self, true);
	}

	private final BooleanSetting showHeads = add(new BooleanSetting("show_heads", Component.literal("Heads"), true), "Shows");
	private final BooleanSetting showTags = add(new BooleanSetting("show_tags", Component.literal("Rank tags"), true), "Shows");
	private final BooleanSetting showPing = add(new BooleanSetting("show_ping", Component.literal("Ping"), true), "Shows");
	private final BooleanSetting headerFooter = add(new BooleanSetting("header_footer", Component.literal("Header and footer"), true), "Shows");
	private final BooleanSetting infoCards = add(new BooleanSetting("info_cards", Component.literal("Info cards"), true), "Shows");
	private final BooleanSetting showScores = add(new BooleanSetting("show_scores", Component.literal("Scores"), true), "Shows");
	private final BooleanSetting pingNumber = add(new BooleanSetting("ping_number", Component.literal("Ping as number"), false), "General", "Off: bars");
	private final BooleanSetting markSelf = add(new BooleanSetting("mark_self", Component.literal("Highlight yourself"), true), "General");
	private final FloatSetting columnWidth = add(new FloatSetting("column_width", Component.literal("Min column width"), 0f, 0f, 320f, 0), "General",
		"0 is the standard width");

	/** A player's spot in the list: it keeps its place while the list is open, and its player fades in or out there. */
	private static final class Spot {
		String key;
		Entry entry;
		HudText tag;
		HudText name;
		String shownTag = "";
		String shownName = "";
		boolean present;
		float alpha;
	}

	/** An info card: it keeps its place while the list is open; its content updates in place, and it only ever grows. */
	private static final class Card {
		final String title;
		final String caption;
		HudText[] labels = new HudText[0];
		HudText[] values = new HudText[0];
		float[] progress = new float[0];
		int[] barColors = new int[0];
		String signature = "";
		int height;
		/** The room of each row's value: it only grows, so a changing value never pushes its label. */
		float[] valueRooms = new float[0];
		int x;
		int y;

		Card(String title) {
			this.title = title;
			this.caption = title.toUpperCase(Locale.ROOT);
		}
	}

	private final List<Spot> spots = new ArrayList<>();
	private final List<Card> cards = new ArrayList<>();
	private List<HudText> header = List.of();
	private List<HudText> footer = List.of();
	private String headerKey = "";
	private String footerKey = "";
	private String countLabel = "PLAYERS";
	private int playerCount = -1;
	private boolean wanted;
	private boolean session;
	private long preparedAt;
	private long fadeAt;
	private int width = 200;
	private int height = 80;
	// the layout, set when the list opens and changed only when the players cross a column step, the widths grow or the screen changes
	private int columns = 1;
	private int rowsPerColumn = 1;
	private int visibleRows = 1;
	/** The share of its size the list was laid out for when it opened, on a small screen. */
	private float keptSize = 1f;
	private int playerScroll;
	private int cellWidth = CELL_WIDTH;
	private int cardColumns;
	private boolean cardsBelow;
	/** How wide the info cards are: as wide as the widest needs, the same for all, so the grid stays in line. It only grows while the list is open. */
	private int cardWidth = CARD_WIDTH;
	/** The whole text of a cut row under the mouse, and where. */
	private String hoveredText;
	private float hoveredX;
	private float hoveredY;
	private int contentLeft = PAD;
	private int playersTop;
	private int layoutScreenWidth = -1;
	private int layoutScreenHeight = -1;
	private boolean relayout = true;
	private final Spring open = Spring.smooth(OPEN_SECONDS);

	public TabListHudFeature() {
		super("tab_list_hud", "Player list", "The list of players that is shown while you hold the key for it.", false, Anchor.CENTER, false, 0f, 14f);
		iconRgb(SystemColors.PURPLE_RGB);
		iconName("people");
		summary("The player list in the style of the mod");
		offerWidth(CELL_WIDTH * 3 + 2 * PAD);
	}

	// ----------------------------------------------------------------------------------------------------- settings card

	public boolean hidesVanilla() {
		return isEnabled();
	}

	// ----------------------------------------------------------------------------------------------------- reading

	/** @return whether a row is a real player: Hypixel's fake rows have names starting with "!" and, on Hypixel, no version 4 UUID */
	private static boolean isRealPlayer(PlayerInfo info, boolean hypixel) {
		String name = info.getProfile().name();
		if (name == null || name.isEmpty() || name.startsWith("!")) {
			return false;
		}
		return !hypixel || info.getProfile().id().version() == 4;
	}

	/** Reads the list from the game: the players in the server's order with you first, the info lines, the header and the footer. */
	private Source source(Minecraft client, Objective objective, Scoreboard scoreboard) {
		var connection = client.player.connection;
		UUID self = client.player.getUUID();
		var server = client.getCurrentServer();
		boolean hypixel = server != null && server.ip != null && server.ip.toLowerCase(Locale.ROOT).contains("hypixel");
		// an add-on reads its world's info columns (SkyBlock's) as cards; the other text entries are then not players
		TabCardProvider reader = Addons.tabCards();
		boolean addonInfo = reader != null && reader.active();
		List<PlayerInfo> infos = new ArrayList<>(connection.getListedOnlinePlayers());
		infos.sort(Comparator.<PlayerInfo>comparingInt(info -> info.getGameMode() == GameType.SPECTATOR ? 1 : 0)
			.thenComparingInt(info -> -info.getTabListOrder())
			.thenComparing(info -> info.getTeam() == null ? "" : info.getTeam().getName())
			.thenComparing(info -> info.getProfile().name(), String.CASE_INSENSITIVE_ORDER));
		List<Entry> players = new ArrayList<>();
		List<Component> info = new ArrayList<>();
		java.util.Map<Component, PlayerSkin> columnHeads = new java.util.IdentityHashMap<>();
		for (PlayerInfo playerInfo : infos) {
			Component shown = client.gui.hud.getTabList().getNameForDisplay(playerInfo);
			boolean real = isRealPlayer(playerInfo, hypixel);
			if (!real && addonInfo) {
				// the world's text columns become the info cards
				info.add(shown);
				columnHeads.put(shown, playerInfo.getSkin());
				continue;
			}
			if (players.size() >= MAX_PLAYERS) {
				continue;
			}
			String score = null;
			if (objective != null && showScores.get()) {
				ReadOnlyScoreInfo value = scoreboard.getPlayerScoreInfo(ScoreHolder.fromGameProfile(playerInfo.getProfile()), objective);
				score = value == null ? null : String.valueOf(value.value());
			}
			boolean isSelf = playerInfo.getProfile().id().equals(self);
			Component[] split = splitName(shown, playerInfo.getProfile().name());
			Entry entry = new Entry(playerInfo.getProfile().id().toString(), split[0], split[1], playerInfo.getSkin(), playerInfo.getLatency(), score,
				isSelf, real);
			if (isSelf) {
				players.addFirst(entry);
			} else {
				players.add(entry);
			}
		}
		if (addonInfo) {
			info = takeColumnPlayers(players, info, columnHeads::get, client.player.getGameProfile().name());
		}
		var overlay = (PlayerTabOverlayAccessor) client.gui.hud.getTabList();
		return new Source(players, info, overlay.lumio$header(), overlay.lumio$footer());
	}

	/** @return the shown name split into the rank tag before the player's name and the name with what follows it */
	private static Component[] splitName(Component shown, String profileName) {
		String text = shown.getString();
		int at = profileName == null || profileName.isEmpty() ? -1 : text.indexOf(profileName);
		if (at <= 0) {
			return new Component[]{null, shown};
		}
		return new Component[]{ServerText.slice(shown, 0, at), ServerText.slice(shown, at, text.length())};
	}

	@Override
	protected void prepare(Minecraft client) {
		Scoreboard scoreboard = client.level.getScoreboard();
		Objective objective = scoreboard.getDisplayObjective(DisplaySlot.LIST);
		var connection = client.player.connection;
		wanted = client.options.keyPlayerList.isDown() && (!client.isLocalServer() || connection.getListedOnlinePlayers().size() > 1 || objective != null);
		boolean editing = HudEditScreen.isOpen() || simulated();
		boolean target = wanted || editing;
		float openNow = open.follow(target ? 1f : 0f);
		if (!ModFeatures.MENU.animations()) {
			openNow = open.snap(target ? 1f : 0f);
		}
		if (!target && openNow < 0.01f) {
			// fully closed: the next opening lays the list out fresh, in the server's order
			session = false;
			preparedAt = 0;
			return;
		}
		if (!session) {
			session = true;
			spots.clear();
			cards.clear();
			cellWidth = CELL_WIDTH;
			cardWidth = CARD_WIDTH;
			columns = 0;
			rowsPerColumn = 0;
			playerScroll = 0;
			relayout = true;
		}
		advanceFades();
		long now = System.nanoTime();
		if (preparedAt != 0 && now - preparedAt < REBUILD_NANOS) {
			if (screenChanged(client)) {
				layout(client);
			}
			return;
		}
		preparedAt = now;
		Source source = source(client, objective, scoreboard);
		List<Entry> players = source.players().size() < 2 && editing ? SAMPLE : source.players();
		List<Component> info = source.info().isEmpty() && editing ? SAMPLE_INFO : source.info();
		updateSpots(client.font, players);
		if (infoCards.get() || editing) {
			updateCards(info);
		}
		updateHeaderFooter(source, editing);
		int real = 0;
		for (Entry entry : players) {
			real += entry.real() ? 1 : 0;
		}
		if (real != playerCount) {
			playerCount = real;
			countLabel = "PLAYERS  ·  " + real;
		}
		if (relayout || screenChanged(client) || spots.size() > columns * rowsPerColumn) {
			layout(client);
		}
	}

	private boolean screenChanged(Minecraft client) {
		return client.getWindow().getGuiScaledWidth() != layoutScreenWidth || client.getWindow().getGuiScaledHeight() != layoutScreenHeight;
	}

	/** Lets players that came fade in and players that left fade out, on real time, in their spots. */
	private void advanceFades() {
		long now = System.nanoTime();
		float dt = fadeAt == 0L ? 0f : Math.min(0.1f, (now - fadeAt) / 1_000_000_000f);
		fadeAt = now;
		float step = ModFeatures.MENU.animations() ? dt / PLAYER_FADE_SECONDS : 1f;
		for (Spot spot : spots) {
			spot.alpha = spot.present ? Math.min(1f, spot.alpha + step) : Math.max(0f, spot.alpha - step);
		}
	}

	/**
	 * Puts the players into their spots: one still listed keeps its spot and has its texts updated in place, one gone fades out there, and a new
	 * one takes the first free spot (or a new one at the end) and fades in. Nobody moves.
	 */
	private void updateSpots(Font font, List<Entry> players) {
		Map<String, Entry> byKey = new HashMap<>();
		for (Entry entry : players) {
			byKey.put(entry.key(), entry);
		}
		boolean fresh = spots.isEmpty();
		for (Spot spot : spots) {
			Entry entry = spot.key == null ? null : byKey.remove(spot.key);
			spot.present = entry != null;
			if (entry != null) {
				fill(spot, entry);
			}
		}
		for (Entry entry : players) {
			if (!byKey.containsKey(entry.key())) {
				continue;
			}
			byKey.remove(entry.key());
			Spot spot = null;
			for (Spot free : spots) {
				if (!free.present && free.alpha <= 0f) {
					spot = free;
					break;
				}
			}
			if (spot == null) {
				spot = new Spot();
				spots.add(spot);
			}
			spot.key = entry.key();
			spot.present = true;
			spot.alpha = fresh || !ModFeatures.MENU.animations() ? 1f : 0f;
			spot.shownName = "";
			spot.shownTag = "";
			fill(spot, entry);
		}
		// widths only grow while the list is open: a longer name widens the columns, a shorter one never narrows them
		int needed = cellWidth;
		for (Spot spot : spots) {
			if (spot.present) {
				needed = Math.max(needed, cellNeed(font, spot));
			}
		}
		needed = Math.min(Math.max(needed, Math.max(CELL_WIDTH, Math.round(columnWidth.get()))), MOST_CELL_WIDTH);
		if (needed > cellWidth) {
			cellWidth = needed;
			relayout = true;
		}
	}

	/** Updates a spot's texts, building them again only when the shown text changed. */
	private void fill(Spot spot, Entry entry) {
		spot.entry = entry;
		int secondary = HudStyle.secondary();
		String tagText = entry.tag() == null ? "" : entry.tag().getString();
		if (!tagText.equals(spot.shownTag) || spot.name == null) {
			spot.shownTag = tagText;
			spot.tag = entry.tag() == null || ServerText.blank(entry.tag()) ? null : new HudText(ServerText.clean(entry.tag(), secondary), TAG_SIZE, true);
		}
		String nameText = entry.name().getString();
		if (!nameText.equals(spot.shownName) || spot.name == null) {
			spot.shownName = nameText;
			spot.name = new HudText(ServerText.clean(entry.name(), secondary), NAME_SIZE, false);
		}
	}

	/** @return the width a player's cell wants for its whole name */
	private int cellNeed(Font font, Spot spot) {
		float need = 8f + (showHeads.get() ? HEAD + 4 : 0) + (spot.tag != null && showTags.get() ? spot.tag.naturalWidth(font) + 3f : 0f)
			+ spot.name.naturalWidth(font) + (showPing.get() ? 14f : 0f);
		return (int) Math.ceil(need);
	}

	/** @return how wide a card wants to be, to show all it has */
	private static int cardNeed(Font font, Card card) {
		float need = font.width(card.caption) * TextScale.SMALL * CAPTION_WIDER;
		for (int i = 0; i < card.labels.length; i++) {
			float row = card.labels[i].naturalWidth(font);
			if (card.values[i] != null) {
				row += card.values[i].naturalWidth(font) + LABEL_VALUE_GAP;
			}
			need = Math.max(need, row);
		}
		return Math.min(MOST_CARD_WIDTH, (int) Math.ceil(need) + 2 * CARD_PAD + 1 + VALUE_ROOM);
	}

	/** The info lines with their legacy codes turned into styles, reused. */
	private final List<Component> cleanInfo = new ArrayList<>();

	/** Reads the info cards: a card already shown keeps its place and size and updates its content; a new card is added at the end. */
	private void updateCards(List<Component> info) {
		int secondary = HudStyle.secondary();
		// the server writes many lines with legacy color codes in their text: cleaned first, a spacer of only a reset code reads as blank
		// and a heading in the bold code as bold
		cleanInfo.clear();
		for (Component line : info) {
			cleanInfo.add(ServerText.clean(line, secondary));
		}
		TabCardProvider reader = Addons.tabCards();
		if (reader == null) {
			return;
		}
		for (TabCard read : reader.cards(cleanInfo)) {
			Card card = null;
			for (Card known : cards) {
				if (known.title.equals(read.title())) {
					card = known;
					break;
				}
			}
			if (card == null) {
				card = new Card(read.title());
				cards.add(card);
				relayout = true;
			}
			StringBuilder signature = new StringBuilder();
			for (TabCard.Row row : read.rows()) {
				signature.append(row.label().getString()).append('\u0000').append(row.value() == null ? "" : row.value().getString()).append('\n');
			}
			if (signature.toString().equals(card.signature)) {
				continue;
			}
			card.signature = signature.toString();
			List<TabCard.Row> rows = new ArrayList<>();
			for (TabCard.Row row : read.rows()) {
				// spacer rows (nothing but spaces or formatting codes) are left out
				if (!ServerText.blank(row.label()) || row.value() != null && !ServerText.blank(row.value())) {
					rows.add(row);
				}
			}
			card.valueRooms = java.util.Arrays.copyOf(card.valueRooms, rows.size());
			card.labels = new HudText[rows.size()];
			card.values = new HudText[rows.size()];
			card.progress = new float[rows.size()];
			card.barColors = new int[rows.size()];
			int h = CARD_PAD * 2 + (card.title.isEmpty() ? 0 : CARD_TITLE);
			for (int i = 0; i < rows.size(); i++) {
				TabCard.Row row = rows.get(i);
				card.labels[i] = new HudText(ServerText.clean(row.label(), secondary), CARD_TEXT, false);
				card.values[i] = row.value() == null ? null : new HudText(ServerText.clean(row.value(), secondary), CARD_TEXT, true);
				card.progress[i] = row.progress();
				card.barColors[i] = row.barRgb();
				h += row.progress() >= 0f ? SKILL_ROW : CARD_ROW;
			}
			// cards only grow while the list is open, so the grid never jumps
			if (h - 1 > card.height) {
				card.height = h - 1;
				relayout = true;
			}
			int need = cardNeed(Minecraft.getInstance().font, card);
			if (need > cardWidth) {
				cardWidth = need;
				relayout = true;
			}
		}
	}

	private void updateHeaderFooter(Source source, boolean editing) {
		Component head = headerFooter.get() || editing ? source.header() : null;
		Component foot = headerFooter.get() || editing ? source.footer() : null;
		String headText = head == null ? "" : head.getString();
		String footText = foot == null ? "" : foot.getString();
		Font font = Minecraft.getInstance().font;
		if (!headText.equals(headerKey)) {
			headerKey = headText;
			header = lines(font, head, 1f);
			relayout = true;
		}
		if (!footText.equals(footerKey)) {
			footerKey = footText;
			footer = lines(font, foot, FOOTER_SIZE);
			relayout = true;
		}
	}

	/** @return the visible lines of a header or footer, each cleaned and as a text; spacer lines are left out */
	private static List<HudText> lines(Font font, Component text, float size) {
		if (text == null || ServerText.blank(text)) {
			return List.of();
		}
		List<HudText> out = new ArrayList<>();
		int secondary = HudStyle.secondary();
		for (FormattedText line : font.getSplitter().splitLines(text, Integer.MAX_VALUE / 2, Style.EMPTY)) {
			Component component = toComponent(line);
			if (!ServerText.blank(component)) {
				out.add(new HudText(ServerText.clean(component, secondary), size, false));
			}
		}
		return out;
	}

	private static Component toComponent(FormattedText line) {
		MutableComponent out = Component.empty();
		line.visit((style, part) -> {
			out.append(Component.literal(part).setStyle(style));
			return Optional.empty();
		}, Style.EMPTY);
		return out;
	}

	// ----------------------------------------------------------------------------------------------------- layout

	/**
	 * Lays the list out wide: player columns of up to twenty, as many as fit in nine tenths of the screen (or the width the player set), and the
	 * cards in a grid of up to three columns beside them, the arrangement as low as possible; only where they do not fit beside the players do
	 * the cards go below them.
	 */
	private void layout(Minecraft client) {
		relayout = false;
		boolean sameScreen = layoutScreenWidth == client.getWindow().getGuiScaledWidth() && layoutScreenHeight == client.getWindow().getGuiScaledHeight();
		layoutScreenWidth = client.getWindow().getGuiScaledWidth();
		layoutScreenHeight = client.getWindow().getGuiScaledHeight();
		float size = Math.max(0.5f, scale.get());
		int screenRoom = Math.round(layoutScreenWidth * MOST_SCREEN_SHARE / size);
		int room = customWidth() ? Math.min(widthOr(screenRoom), screenRoom) : screenRoom;
		int roomHeight = Math.round(layoutScreenHeight * MOST_SCREEN_SHARE / size);
		boolean withCards = !cards.isEmpty() && (infoCards.get() || HudEditScreen.isOpen());
		int[] heights = new int[withCards ? cards.size() : 0];
		for (int i = 0; i < heights.length; i++) {
			heights[i] = cards.get(i).height;
		}
		int headerHeight = header.isEmpty() ? 0 : header.size() * HEADER_LINE + HEADER_GAP;
		int footerHeight = footer.isEmpty() ? 0 : footer.size() * HEADER_LINE + HEADER_GAP;
		TabLayout.Sizes sizes = new TabLayout.Sizes(PAD, cellWidth, CELL_HEIGHT, CELL_GAP, SECTION_ROW, AREA_GAP, cardWidth, CARD_GAP, headerHeight + footerHeight);
		// while it is open the columns stay: only a player more than they hold makes them longer
		boolean keep = sameScreen && columns > 0;
		TabLayout.Plan plan = keep ? TabLayout.plan(spots.size(), heights, sizes, Math.round(room / keptSize), Math.round(roomHeight / keptSize), columns, rowsPerColumn) : TabLayout.planSmallest(spots.size(), heights, sizes, room, roomHeight);
		if (!keep) {
			keptSize = Math.min(1f, Math.min((float) room / (plan.contentWidth() + 2f * PAD), (float) roomHeight / (plan.bodyHeight() + headerHeight + footerHeight + 2f * PAD)));
		}
		columns = plan.columns();
		rowsPerColumn = plan.rows();
		visibleRows = plan.visibleRows();
		playerScroll = Math.min(playerScroll, rowsPerColumn - visibleRows);
		cardColumns = plan.cardColumns();
		cardsBelow = plan.cardsBelow();
		int natural = plan.contentWidth() + 2 * PAD;
		widthLimits(CELL_WIDTH + 2 * PAD, natural);
		width = Math.max(customWidth() ? widthOr(natural) : natural, natural);
		contentLeft = (width - plan.contentWidth()) / 2;
		playersTop = PAD + headerHeight;
		height = 2 * PAD + headerHeight + plan.bodyHeight() + footerHeight;
		if (cardColumns > 0) {
			int playersWidth = columns * cellWidth + (columns - 1) * CELL_GAP;
			int playersHeight = SECTION_ROW + visibleRows * (CELL_HEIGHT + CELL_GAP) - CELL_GAP;
			int cardsLeft = cardsBelow ? contentLeft : contentLeft + playersWidth + AREA_GAP;
			int cardsTop = (cardsBelow ? playersTop + playersHeight + AREA_GAP : playersTop) + SECTION_ROW;
			TabLayout.Masonry masonry = TabLayout.masonry(heights, cardColumns, CARD_GAP);
			for (int i = 0; i < heights.length; i++) {
				Card card = cards.get(i);
				card.x = cardsLeft + masonry.column()[i] * (cardWidth + CARD_GAP);
				card.y = cardsTop + masonry.top()[i];
			}
		}
	}

	/** @return whether the wheel moved the player rows: they scroll while the list is open and has more rows than it shows; the hotbar keeps the wheel otherwise */
	public boolean scrollPlayers(double notches) {
		if (!isEnabled() || !wanted || notches == 0.0 || rowsPerColumn <= visibleRows) {
			return false;
		}
		playerScroll = Math.clamp(playerScroll - (int) Math.signum(notches), 0, rowsPerColumn - visibleRows);
		return true;
	}

	@Override
	protected boolean shown(Minecraft client) {
		return wanted || open.value() > 0.01f;
	}

	/** Lists with many players are drawn smaller until they fit on screen, never larger; in the editor the list is small until selected. */
	@Override
	protected float fit(int width, int height, int guiWidth, int guiHeight, float scale) {
		// no more of the screen than its share, in both directions; the top keeps its distance from the screen's edge
		float room = Math.min(Math.min(guiWidth * MOST_SCREEN_SHARE, guiWidth - 12f) / (width * scale), Math.min(guiHeight * MOST_SCREEN_SHARE, guiHeight - 12f - 14f) / (height * scale));
		float fitted = Math.max(0.35f, Math.min(1f, room));
		boolean editing = HudEditScreen.isOpen() || simulated();
		float wantedSize = !editing || HudEditScreen.isSelected(this) ? 1f : EDITOR_SIZE;
		return fitted * editorSize.follow(wantedSize);
	}

	/** Scale of the list in the HUD editor while not selected, as a fraction of its size. */
	private static final float EDITOR_SIZE = 0.38f;
	private final Spring editorSize = Spring.bouncy(Spring.GENTLE);

	@Override
	protected int panelWidth() {
		return width;
	}

	@Override
	protected int panelHeight() {
		return height;
	}

	// ----------------------------------------------------------------------------------------------------- drawing

	@Override
	protected void drawPanel(Ui ui, GuiGraphicsExtractor graphics, Minecraft client) {
		float amount = Math.max(0f, Math.min(1f, open.value()));
		var pose = graphics.pose();
		pose.pushMatrix();
		// opening and closing: everything fades and grows together, from the top center where the list is anchored
		float grow = 1f - OPEN_SCALE * (1f - amount);
		pose.translate(width / 2f, 0f);
		pose.scale(grow, grow);
		pose.translate(-width / 2f, 0f);
		float alphaBefore = Ui.alpha();
		Ui.setAlpha(alphaBefore * amount);
		try {
			drawGlass(ui, width, height, RADIUS);
			drawContent(ui, graphics, client.font, amount);
		} finally {
			Ui.setAlpha(alphaBefore);
			pose.popMatrix();
		}
	}

	private void drawContent(Ui ui, GuiGraphicsExtractor graphics, Font font, float alpha) {
		int text = Ui.withOpacity(HudStyle.text(), alpha);
		int secondary = Ui.withOpacity(HudStyle.secondary(), alpha);
		float headerAlpha = HudParts.alpha(headerFooter.get());
		int y = PAD;
		for (int i = 0; i < header.size(); i++) {
			HudText line = header.get(i);
			line.layout(font, width - 2f * PAD);
			line.draw(graphics, font, (width - line.width()) / 2f, y + i * HEADER_LINE, Ui.withOpacity(i == 0 ? text : secondary, headerAlpha), false);
		}
		if (!header.isEmpty()) {
			part("Header and footer", headerFooter, PAD, y, width - 2 * PAD, header.size() * HEADER_LINE);
		}
		int playersWidth = columns * cellWidth + (columns - 1) * CELL_GAP;
		ui.sectionLabel(countLabel, contentLeft, playersTop + 2, secondary);
		float hintWidth = ui.widthScaled(HINT, HINT_SCALE);
		if (hintWidth + ui.width(countLabel) + 12 < playersWidth) {
			ui.textAtScaled(HINT, contentLeft + playersWidth - hintWidth, playersTop + 2.5f, HINT_SCALE, Ui.withOpacity(secondary, 0.8f));
		}
		int cellsTop = playersTop + SECTION_ROW;
		drawSeparators(ui, playersWidth, cellsTop, alpha);
		for (int i = 0; i < spots.size(); i++) {
			Spot spot = spots.get(i);
			if (spot.alpha <= 0f || spot.entry == null) {
				continue;
			}
			int column = i / rowsPerColumn;
			int row = i % rowsPerColumn - playerScroll;
			if (row < 0 || row >= visibleRows) {
				continue;
			}
			drawCell(ui, graphics, font, spot, contentLeft + column * (cellWidth + CELL_GAP), cellsTop + row * (CELL_HEIGHT + CELL_GAP),
				alpha * spot.alpha, (row + playerScroll) % 2 == 1);
		}
		if (rowsPerColumn > visibleRows) {
			// how far the rows are scrolled: thin, in the gap at the right of the players
			float track = visibleRows * (CELL_HEIGHT + CELL_GAP) - CELL_GAP;
			float thumb = Math.max(SCROLL_LEAST, track * visibleRows / rowsPerColumn);
			float at = (track - thumb) * playerScroll / (rowsPerColumn - visibleRows);
			ui.roundedSmooth(contentLeft + playersWidth + 1f, cellsTop + at, SCROLL_WIDTH, thumb, SCROLL_WIDTH / 2f, Ui.withOpacity(HudStyle.text(), SCROLL_OPACITY * alpha));
		}
		if (cardColumns > 0 && !cards.isEmpty()) {
			float cardsAlpha = HudParts.alpha(infoCards.get()) * alpha;
			int left = Integer.MAX_VALUE;
			int top = Integer.MAX_VALUE;
			int right = 0;
			int bottom = 0;
			for (Card card : cards) {
				drawCard(ui, graphics, font, card, cardsAlpha);
				left = Math.min(left, card.x);
				top = Math.min(top, card.y);
				right = Math.max(right, card.x + cardWidth);
				bottom = Math.max(bottom, card.y + card.height);
			}
			ui.sectionLabel("INFO", left, top - SECTION_ROW + 2, Ui.withOpacity(secondary, HudParts.alpha(infoCards.get())));
			part("Info cards", infoCards, left, top - SECTION_ROW, right - left, bottom - top + SECTION_ROW);
			drawHovered(ui, cardsAlpha);
		}
		if (!footer.isEmpty()) {
			int footerTop = height - PAD - footer.size() * HEADER_LINE;
			for (int i = 0; i < footer.size(); i++) {
				HudText line = footer.get(i);
				line.layout(font, width - 2f * PAD);
				line.draw(graphics, font, (width - line.width()) / 2f, footerTop + i * HEADER_LINE + 1, Ui.withOpacity(secondary, headerAlpha), false);
			}
			part("Header and footer", headerFooter, PAD, footerTop, width - 2 * PAD, footer.size() * HEADER_LINE);
		}
	}

	/**
	 * Draws the thin separators of the layout, each one screen pixel: between the player columns, between the players and the info cards, under
	 * the header and over the footer. They come from the layout, set when the list opens, so they stay still while values change, and fade with
	 * the panel.
	 */
	private void drawSeparators(Ui ui, int playersWidth, int cellsTop, float alpha) {
		int line = HudStyle.line(SEPARATOR * alpha);
		int playersBottom = cellsTop + visibleRows * (CELL_HEIGHT + CELL_GAP) - CELL_GAP;
		for (int column = 1; column < columns; column++) {
			int x = contentLeft + column * (cellWidth + CELL_GAP) - (CELL_GAP + 1) / 2;
			ui.fill(x, cellsTop, 1, playersBottom - cellsTop, line);
		}
		if (cardColumns > 0 && !cards.isEmpty()) {
			int strong = HudStyle.line(AREA_SEPARATOR * alpha * HudParts.alpha(infoCards.get()));
			if (cardsBelow) {
				int y = playersBottom + AREA_GAP / 2;
				ui.fill(contentLeft, y, Math.max(playersWidth, width - 2 * contentLeft), 1, strong);
			} else {
				int x = contentLeft + playersWidth + AREA_GAP / 2;
				ui.fill(x, playersTop, 1, height - PAD - footerHeight() - playersTop, strong);
			}
		}
		if (!header.isEmpty()) {
			ui.fill(PAD, playersTop - HEADER_GAP / 2 - 1, width - 2 * PAD, 1, line);
		}
		if (!footer.isEmpty()) {
			ui.fill(PAD, height - PAD - footer.size() * HEADER_LINE - HEADER_GAP / 2, width - 2 * PAD, 1, line);
		}
	}

	/** @return the height the footer takes at the bottom, with its gap; 0 without one */
	private int footerHeight() {
		return footer.isEmpty() ? 0 : footer.size() * HEADER_LINE + HEADER_GAP;
	}

	/** Draws a player: head, rank tag, name and ping bars in a soft cell, you in the accent; everything with the cell's alpha. */
	private void drawCell(Ui ui, GuiGraphicsExtractor graphics, Font font, Spot spot, int x, int y, float alpha, boolean lighter) {
		Entry entry = spot.entry;
		int accent = accentArgb();
		if (entry.self() && markSelf.get()) {
			ui.rounded(x, y, cellWidth, CELL_HEIGHT, 4f, Ui.withOpacity(accent, SELF_FILL * alpha));
			ui.ring(x, y, cellWidth, CELL_HEIGHT, 4f, 1, Ui.withOpacity(accent, alpha));
		} else {
			// every second row a little lighter, so a row is easy to follow across the cell
			ui.rounded(x, y, cellWidth, CELL_HEIGHT, 4f, Ui.withOpacity(lighter ? CELL_FILL_ZEBRA : CELL_FILL, alpha));
		}
		float cursor = x + 4f;
		int middle = y + CELL_HEIGHT / 2;
		if (showHeads.get() && entry.real()) {
			if (entry.skin() != null) {
				// the head fades with the panel, like everything else in it
				PlayerFaceExtractor.extractRenderState(graphics, entry.skin(), Math.round(cursor), middle - HEAD / 2, HEAD,
					Ui.withOpacity(0xFFFFFFFF, alpha));
			} else {
				ui.rounded(Math.round(cursor), middle - HEAD / 2, HEAD, HEAD, 2f, Ui.withOpacity(sampleHead(entry), alpha));
			}
			cursor += HEAD + 4f;
		}
		float right = x + cellWidth - 4f;
		if (showPing.get() && entry.real()) {
			if (pingNumber.get()) {
				String ping = String.valueOf(entry.latency());
				float pingWidth = ui.widthScaled(ping, TAG_SIZE);
				ui.textAtScaled(ping, right - pingWidth, middle - 3f, TAG_SIZE, Ui.withOpacity(HudStyle.secondary(), alpha));
				right -= pingWidth + 4f;
			} else {
				drawPing(ui, Math.round(right - 9f), middle, entry.latency(), alpha);
				right -= 13f;
			}
		}
		if (entry.score() != null) {
			float scoreWidth = ui.widthScaled(entry.score(), TAG_SIZE);
			ui.textAtScaled(entry.score(), right - scoreWidth, middle - 3f, TAG_SIZE, Ui.withOpacity(accent, alpha));
			right -= scoreWidth + 4f;
		}
		int textColor = Ui.withOpacity(HudStyle.text(), alpha);
		if (spot.tag != null && showTags.get()) {
			spot.tag.layout(font, (right - cursor) * 0.45f);
			spot.tag.draw(graphics, font, cursor, middle - 4f * TAG_SIZE, textColor, false);
			cursor += spot.tag.width() + 3f;
		}
		spot.name.layout(font, Math.max(8f, right - cursor));
		spot.name.draw(graphics, font, cursor, middle - 4.5f * NAME_SIZE, textColor, false);
	}

	/** @return a head color for the sample players of the editor, which have no skin */
	private static int sampleHead(Entry entry) {
		return 0xFF000000 | (entry.name().getString().hashCode() & 0x7F7F7F) | 0x404040;
	}

	/** Draws four rising connection bars, as many lit as the connection is good, green to red. */
	private static void drawPing(Ui ui, int x, int middle, int latency, float alpha) {
		int lit = latency < 0 ? 0 : latency < 100 ? 4 : latency < 250 ? 3 : latency < 500 ? 2 : 1;
		int color = lit >= 3 ? SystemColors.GREEN : lit == 2 ? SystemColors.YELLOW : SystemColors.RED;
		for (int i = 0; i < 4; i++) {
			float h = 2f + i * 1.25f;
			ui.roundedSmooth(x + i * 2.4f, middle + 2.5f - h, 1.5f, h, 0.5f,
				Ui.withOpacity(i < lit ? color : Ui.withOpacity(HudStyle.text(), 0.18f), alpha));
		}
	}

	/** Notes a cut row under the mouse, while the mouse is free (a screen is open): its whole text shows over the panel. */
	private void noteHovered(Ui ui, Font font, HudText label, float room, float x, float y) {
		Minecraft client = Minecraft.getInstance();
		if (client.gui.screen() == null || label.naturalWidth(font) <= room) {
			return;
		}
		org.joml.Vector2f at = ui.graphics.pose().transformPosition(x, y, new org.joml.Vector2f());
		org.joml.Vector2f end = ui.graphics.pose().transformPosition(x + room, y + CARD_ROW, new org.joml.Vector2f());
		double mouseX = client.mouseHandler.getScaledXPos(client.getWindow());
		double mouseY = client.mouseHandler.getScaledYPos(client.getWindow());
		if (mouseX >= at.x && mouseX < end.x && mouseY >= at.y && mouseY < end.y) {
			hoveredText = label.source().getString();
			hoveredX = x;
			hoveredY = y + CARD_ROW;
		}
	}

	private void drawHovered(Ui ui, float alpha) {
		if (hoveredText == null) {
			return;
		}
		float textWidth = ui.widthScaled(hoveredText, CARD_TEXT);
		ui.roundedSmooth(hoveredX - 3f, hoveredY, textWidth + 6f, 11f, 3f, Ui.withOpacity(0xFF0A0A10, 0.92f * alpha));
		ui.textAtScaled(hoveredText, hoveredX, hoveredY + 2f, CARD_TEXT, Ui.withOpacity(0xFFFFFFFF, alpha));
		hoveredText = null;
	}

	/** Draws an info card: its title in small spaced capitals, then its rows, label left and value right in reserved room, skills with a bar. */
	private void drawCard(Ui ui, GuiGraphicsExtractor graphics, Font font, Card card, float alpha) {
		if (alpha <= 0f) {
			return;
		}
		ui.rounded(card.x, card.y, cardWidth, card.height, CARD_RADIUS, Ui.withOpacity(CARD_FILL, alpha));
		ui.ring(card.x, card.y, cardWidth, card.height, CARD_RADIUS, 1, Ui.withOpacity(CARD_EDGE, alpha));
		int y = card.y + CARD_PAD;
		int inner = cardWidth - 2 * CARD_PAD;
		if (!card.title.isEmpty()) {
			ui.sectionLabel(card.caption, card.x + CARD_PAD, y, Ui.withOpacity(HudStyle.secondary(), alpha));
			y += CARD_TITLE;
		}
		int labelColor = Ui.withOpacity(Ui.mix(HudStyle.text(), HudStyle.secondary(), 0.35f), alpha);
		int valueColor = Ui.withOpacity(HudStyle.text(), alpha);
		for (int i = 0; i < card.labels.length; i++) {
			// a value is never cut, and a label gives way only to the value of its own row
			if (card.values[i] != null) {
				card.values[i].layout(font, 0f);
				card.valueRooms[i] = Math.max(card.valueRooms[i], card.values[i].width());
			}
			if (card.values[i] != null) {
				card.values[i].draw(graphics, font, card.x + CARD_PAD + inner - card.values[i].width(), y + 1f, valueColor, false);
			}
			float labelRoom = Math.max(8f, card.values[i] != null ? inner - card.valueRooms[i] - LABEL_VALUE_GAP : inner);
			card.labels[i].layout(font, labelRoom);
			card.labels[i].draw(graphics, font, card.x + CARD_PAD, y + 1f, labelColor, false);
			noteHovered(ui, font, card.labels[i], labelRoom, card.x + CARD_PAD, y);
			if (card.progress[i] >= 0f) {
				float barY = y + CARD_ROW;
				ui.roundedSmooth(card.x + CARD_PAD, barY, inner, 1.5f, 0.75f, Ui.withOpacity(0x1FFFFFFF, alpha));
				ui.roundedSmooth(card.x + CARD_PAD, barY, inner * card.progress[i], 1.5f, 0.75f, Ui.withOpacity(card.barColors[i] >= 0 ? 0xFF000000 | card.barColors[i] : accentArgb(), alpha));
				y += SKILL_ROW;
			} else {
				y += CARD_ROW;
			}
		}
	}
}
