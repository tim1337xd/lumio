package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.LocationProvider;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointColors;
import dev.tim1337.lumio.api.experimental.waypoint.AddonWaypoints;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointCards;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.config.setting.StringSetting;
import dev.tim1337.lumio.features.visual.screen.chat.ChatActions;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.LumioChat;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.render.LateSubmits;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Waypoints: marked places in groups, shown in the world, on a map and on their page under Extras. Points are kept per server and world, and per
 * profile in an add-on's world (a SkyBlock profile). Everything is drawn on this client only; nothing is sent.
 */
public class WaypointsFeature extends Feature {
	/** How often the scope (server, world, profile) is checked, in ticks. */
	private static final int SCOPE_CHECK_TICKS = 20;
	private static final int RECORDING_HEIGHT = 16;
	private static final int RECORDING_PAD = 8;
	private static final int RECORDING_DOT = 6;
	/** How far the pill sits above the bottom of the screen: over the hotbar and its bars. */
	private static final int RECORDING_ABOVE_BOTTOM = 72;
	/** How much of the screen's smaller side the big map takes. */
	private static final float BIG_MAP_SHARE = 0.62f;

	private final WaypointStore store = WaypointStore.inConfig();
	private final WaypointsPageSetting page = add(new WaypointsPageSetting("waypoints_page", this));
	/** The group and the color the place card had last, so the next point goes where the last one went. */
	private final StringSetting lastGroup = add(new StringSetting("last_group", Component.literal("Last group"), "", false));
	private final StringSetting lastColor = add(new StringSetting("last_color", Component.literal("Last color"), "", false));
	private final BooleanSetting chatButtons = add(new BooleanSetting("chat_buttons", Component.literal("\"+ Waypoint\" buttons"), true), CHAT,
		"After coordinates in chat");
	private final FloatSetting temporaryMinutes = add(new FloatSetting("temporary_minutes", Component.literal("Duration"), 3f, 1f, 10f, 0).unit(" min"), CHAT,
		"For waypoints from chat");
	/** The default of "Restart at 1 in a new lobby" for routes made from now on; each route has its own. */
	private final BooleanSetting restartNewRoutes = add(new BooleanSetting("restart_new_routes", Component.literal("Restart per lobby"),
		false), "Routes", "For new routes; each can differ");
	private final LobbyWatch lobby = new LobbyWatch();
	private int lobbyTick;
	private String lobbyId;
	private final BooleanSetting removeReached = add(new BooleanSetting("remove_reached", Component.literal("Remove when reached"), true), CHAT);
	private final FloatSetting mostTemporary = add(new FloatSetting("most_temporary", Component.literal("Max temporary"), 5f, 1f, 10f, 0), CHAT);
	/** The waypoints from chat: never in a group until saved, gone after a while. */
	private final TemporaryWaypoints temporary = new TemporaryWaypoints();
	private Object temporaryWorld;
	private static final String CHAT = "Chat";
	private static final long MILLIS_PER_MINUTE = 60_000L;
	/** One look for all waypoints, also those of add-ons. */
	private final WaypointLookSettings look = new WaypointLookSettings();
	private final WaypointPlacing placing = new WaypointPlacing(this);
	private final WaypointRenderer renderer = new WaypointRenderer(this);
	private final WaypointMapView bigMap = new WaypointMapView();
	private final double[] bigMapPlayer = new double[2];
	private boolean started;
	private int tick;
	/** The group shown on the page, by its id; "" for the first. */
	private String selected = "";

	public WaypointsFeature() {
		super("waypoints", "Waypoints", "Mark places, plan routes, and share them.", false, false);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("pin");
		lastGroup.visibleWhen(() -> false);
		lastColor.visibleWhen(() -> false);
		lastGroup.hideFromRecent();
		lastColor.hideFromRecent();
		for (dev.tim1337.lumio.config.setting.Setting<?> setting : look.settings()) {
			add(setting, WaypointLookSettings.GROUP);
		}
	}

	@Override
	public boolean neverTabbed() {
		return true;
	}

	public WaypointStore store() {
		return store;
	}

	/** Reads the groups that show everywhere, and writes what changed when the game closes. Called during mod initialization. */
	public void start() {
		if (started) {
			return;
		}
		started = true;
		Minecraft client = Minecraft.getInstance();
		store.loadGlobal(client::execute);
		// the place card for an add-on's own points, such as room waypoints in dungeons
		WaypointCards.opener((x, y, z, title, name, done) -> client.execute(() -> client.gui.setScreen(new WaypointPlaceScreen(this, x, y, z, title, name,
			done))));
		// an add-on's own temporary waypoints, such as the Hotspot Radar's guess, in the look of the chat's
		AddonWaypoints.shower(new AddonWaypoints.Shower() {
			@Override
			public void show(String key, String name, int x, int y, int z) {
				show(key, name, x, y, z, 0, true);
			}

			@Override
			public void show(String key, String name, int x, int y, int z, int rgb, boolean label) {
				temporary.showOwned(key, name, x, y, z, rgb, label, System.currentTimeMillis());
			}

			@Override
			public void remove(String key) {
				temporary.removeOwned(key);
			}
		});
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			store.saveNow();
			store.flush();
		}, "Lumio waypoints"));
	}

	/** Registers the waypoint keys; called during mod initialization. */
	/** Lists a key of the waypoints in the feature's settings, in its Keys group, as a key field. */
	void listKey(net.minecraft.client.KeyMapping mapping) {
		addKey(mapping, dev.tim1337.lumio.config.setting.KeySetting.Where.WORLD);
	}

	public void registerKeys() {
		placing.registerKeys();
	}

	/** @return the place key, which an add-on lists on its own page too (its points in a dungeon room) */
	public net.minecraft.client.KeyMapping placeKey() {
		return placing.placeKey();
	}

	WaypointPlacing placing() {
		return placing;
	}

	/** A block the player broke, from the game: a route that is recorded takes it as its next point. */
	public void blockBroken(BlockPos pos) {
		placing.blockBroken(pos);
		renderer.blockBroken(pos);
	}

	/** Called every tick: follows the scope, and writes what changed a moment after the last change. */
	public void tick(Minecraft client) {
		long now = System.currentTimeMillis();
		if (++tick % SCOPE_CHECK_TICKS == 0) {
			store.enter(scopeOf(client), client::execute);
		}
		if (client.level != null) {
			WaypointAreas.refresh(client, now);
		}
		placing.tick(client);
		watchLobby(client, now);
		tickTemporary(client, now);
		renderer.tick(client);
		store.tick(now);
	}

	/**
	 * @return the scope the points are kept in: the profile in an add-on's world ("skyblock-&lt;profile&gt;"), else the server's address or the
	 *         singleplayer world's name, or null outside a world
	 */
	static String scopeOf(Minecraft client) {
		if (client.level == null) {
			return null;
		}
		LocationProvider location = Addons.location();
		if (location != null && location.inWorld() && !location.profileId().isEmpty()) {
			return location.worldName() + "-" + location.profileId();
		}
		ServerData server = client.getCurrentServer();
		if (server != null && server.ip != null) {
			return "server-" + server.ip;
		}
		var singleplayer = client.getSingleplayerServer();
		return singleplayer == null ? null : "world-" + singleplayer.getWorldData().getLevelName();
	}

	// ------------------------------------------------------------------------------------------------------ the page's state

	/** @return the group shown on the page: the one chosen, or the first; null when there is none */
	public WaypointGroup selectedGroup() {
		WaypointGroup group = store.find(selected);
		if (group == null && !store.groups().isEmpty()) {
			group = store.groups().getFirst();
			selected = group.id();
		}
		return group;
	}

	public void select(WaypointGroup group) {
		selected = group == null ? "" : group.id();
	}

	/** Restarts the routes set to restart in a new lobby, when the player joined one. */
	private void watchLobby(Minecraft client, long now) {
		if (++lobbyTick % SCOPE_CHECK_TICKS == 0 || client.level == null) {
			lobbyId = client.level == null ? null : readServerId(client);
		}
		if (lobby.update(client.level, lobbyId, now)) {
			for (WaypointGroup group : store.groups()) {
				if (group.restartInNewLobby() && group.show() == WaypointGroup.Show.NEXT) {
					group.restart();
					store.changed(group);
				}
			}
		}
	}

	/** @return the server's id from the scoreboard's lines ("m12AB" on Hypixel), or null when it shows none */
	private static String readServerId(Minecraft client) {
		net.minecraft.world.scores.Scoreboard scoreboard = client.level.getScoreboard();
		net.minecraft.world.scores.Objective objective = scoreboard.getDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR);
		if (objective == null) {
			return null;
		}
		for (net.minecraft.world.scores.PlayerScoreEntry entry : scoreboard.listPlayerScores(objective)) {
			String line = net.minecraft.world.scores.PlayerTeam.formatNameForTeam(scoreboard.getPlayersTeam(entry.owner()), entry.ownerName()).getString();
			String id = LobbyWatch.serverId(net.minecraft.ChatFormatting.stripFormatting(line));
			if (id != null) {
				return id;
			}
		}
		return null;
	}

	/** Makes a new group where the player is, named "Group N", and shows it on the page. */
	public WaypointGroup newGroup() {
		WaypointGroup group = new WaypointGroup(store.newId(), "Group " + (store.groups().size() + 1),
			WaypointColors.get(store.groups().size()));
		group.areas().addAll(WaypointAreas.forNewGroup());
		group.restartInNewLobby(restartNewRoutes.get());
		store.add(group, WaypointAreas.everywhere(group));
		select(group);
		return group;
	}

	/** Draws the beams, glows and route lines with the world. */
	public void collect(LevelRenderContext context) {
		if (!LateSubmits.wanted()) {
			renderer.collect(context);
		}
	}

	/** The same after the translucent terrain, when glows are drawn then (see {@link LateSubmits}): so water or glass behind a beam does not cover it. */
	public void collectLate(LevelRenderContext context) {
		if (LateSubmits.wanted()) {
			renderer.collect(context);
		}
	}

	/** Draws the labels of the points with the HUD, under everything else of it. */
	public void drawLabels(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		renderer.drawLabels(graphics, tracker);
	}

	/** @return the first group that is on, where the player is and has this map, or null */
	WaypointGroup groupWithMap(WaypointGroup.MapMode mode) {
		List<WaypointGroup> groups = store.groups();
		for (int i = 0; i < groups.size(); i++) {
			WaypointGroup group = groups.get(i);
			if (group.enabled() && group.map() == mode && !group.points().isEmpty() && WaypointAreas.here(group)) {
				return group;
			}
		}
		return null;
	}

	/**
	 * The big map while its key is held: a group whose Map is Big on key (or else the corner map's group), large in the middle of the screen,
	 * north up and fitted to its points, with the player.
	 */
	public void renderBigMap(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		Minecraft client = Minecraft.getInstance();
		if (placing.mapKey() == null || !placing.mapKey().isDown() || client.player == null || client.gui.screen() != null) {
			return;
		}
		WaypointGroup group = groupWithMap(WaypointGroup.MapMode.BIG);
		if (group == null) {
			group = groupWithMap(WaypointGroup.MapMode.CORNER);
		}
		if (group == null) {
			return;
		}
		Ui ui = new Ui(graphics, client.font, HudStyle.palette(), ModFeatures.MENU.accentArgb(), 0f, false);
		int size = Math.round(Math.min(graphics.guiWidth(), graphics.guiHeight()) * BIG_MAP_SHARE);
		int x = (graphics.guiWidth() - size) / 2;
		int y = (graphics.guiHeight() - size) / 2;
		ui.shadow(x, y, size, size, Radius.XL, 10, 0.4f);
		ui.rounded(x, y, size, size, Radius.XL, 0xE60B1220);
		ui.ring(x, y, size, size, Radius.XL, 1, 0x26FFFFFF);
		bigMapPlayer[0] = client.player.getX();
		bigMapPlayer[1] = client.player.getZ();
		int next = group.show() == WaypointGroup.Show.NEXT ? group.next() : -1;
		bigMap.draw(ui, x + 1, y + 1, size - 2, size - 2, group, bigMapPlayer, client.player.getYRot(), next, -1);
		ui.sectionLabel(group.name().toUpperCase(java.util.Locale.ROOT), x + 10, y + 9, 0xB3FFFFFF);
		WaypointMapView.drawNorth(ui, x + size - 14, y + 14);
	}

	/** The "Recording · N points" pill above the hotbar while a route is recorded. */
	public void renderRecording(GuiGraphicsExtractor graphics, DeltaTracker tracker) {
		if (!placing.recording()) {
			return;
		}
		Ui ui = new Ui(graphics, Minecraft.getInstance().font, HudStyle.palette(), ModFeatures.MENU.accentArgb(), 0f, false);
		int count = placing.recorded();
		String text = "Recording  ·  " + count + (count == 1 ? " point" : " points");
		int width = ui.width(text) + RECORDING_PAD * 2 + RECORDING_DOT + 5;
		int x = graphics.guiWidth() / 2 - width / 2;
		int y = graphics.guiHeight() - RECORDING_ABOVE_BOTTOM;
		ui.rounded(x, y, width, RECORDING_HEIGHT, Radius.box(RECORDING_HEIGHT), 0xCC141418);
		ui.ring(x, y, width, RECORDING_HEIGHT, Radius.box(RECORDING_HEIGHT), 1, 0x33FF453A);
		// the dot pulses once a second, as a recording light does
		float pulse = 0.6f + 0.4f * (float) Math.abs(Math.sin(System.currentTimeMillis() / 1000.0 * Math.PI));
		ui.discAt(x + RECORDING_PAD + RECORDING_DOT / 2f, y + RECORDING_HEIGHT / 2f, RECORDING_DOT, Ui.withOpacity(0xFFFF453A, pulse));
		ui.textMiddle(text, x + RECORDING_PAD + RECORDING_DOT + 5, y + RECORDING_HEIGHT / 2, 0xFFFF6B5E);
	}

	// ------------------------------------------------------------------------------------------------------ adding points

	/** @return the group the place card had last, or the first one points can be added to; null when there is none */
	WaypointGroup lastGroup() {
		WaypointGroup group = store.find(lastGroup.get());
		if (group != null && !group.readOnly()) {
			return group;
		}
		for (WaypointGroup each : store.groups()) {
			if (!each.readOnly()) {
				return each;
			}
		}
		return null;
	}

	/** @return the color the place card starts with: the one chosen last, or the group's */
	int lastColorIndex(WaypointGroup group) {
		String saved = lastColor.get();
		if (!saved.isEmpty()) {
			return WaypointColors.nearest(WaypointJson.parseColor(saved, WaypointColors.DEFAULT));
		}
		return group == null ? 0 : WaypointColors.nearest(group.rgb());
	}

	/** Keeps a point from the place card in its group (a new one when there is none), and remembers the group and the color. */
	void placeFromCard(WaypointGroup chosen, Waypoint point, int rgb) {
		WaypointGroup group = chosen;
		if (group == null) {
			group = newGroup();
			group.rename("Waypoints");
		}
		point.rgb(rgb == group.rgb() ? 0 : rgb);
		if (group.add(point)) {
			store.changed(group);
		}
		lastGroup.set(group.id());
		lastColor.set(WaypointJson.hex(rgb));
	}

	/** @return the group a recorded route goes into: the last one used, or a new route */
	WaypointGroup groupForRecording() {
		WaypointGroup group = lastGroup();
		if (group == null) {
			group = newGroup();
			group.rename("Route");
			group.show(WaypointGroup.Show.NEXT);
			lastGroup.set(group.id());
		}
		return group;
	}

	/**
	 * Adds the coordinates of a chat line, after a click on its "+ Waypoint", as a TEMPORARY waypoint: never into a group, gone after a few
	 * minutes; its line in the chat has Save, which keeps it in the last group. Only on this client.
	 */
	public void addFromChat(CoordParser.Found found, String sender) {
		String name = found.name().isEmpty() ? "Waypoint" : found.name();
		TemporaryWaypoints.Temp temp = temporary.add(found.x(), found.y(), found.z(), name, sender, System.currentTimeMillis(),
			Math.round(mostTemporary.get()));
		UiSounds.click();
		int minutes = Math.round(temporaryMinutes.get());
		ChatActions.post(Component.literal("Temporary waypoint “" + temp.point().name() + "” for " + minutes + (minutes == 1 ? " minute." : " minutes.")),
			"Save", ModFeatures.MENU.accentArgb() & 0xFFFFFF, () -> {
				WaypointGroup group = lastGroup();
				if (group == null) {
					group = newGroup();
					group.rename("Waypoints");
				}
				saveTemporary(temp, group);
			});
	}

	/** @return whether a "+ Waypoint" button follows coordinates players share in chat */
	/** @return the look all waypoints draw by */
	public WaypointLookSettings look() {
		return look;
	}

	public boolean chatButtons() {
		return chatButtons.get();
	}

	/** @return the temporary waypoints from chat, oldest first */
	List<TemporaryWaypoints.Temp> temporary() {
		return temporary.list();
	}

	/** @return the group the temporary waypoints are drawn through (never stored, listed or saved) */
	WaypointGroup temporaryGroup() {
		return temporary.group();
	}

	/** Keeps a temporary waypoint: it moves into a group, as a normal point, and is no longer temporary. */
	void saveTemporary(TemporaryWaypoints.Temp temp, WaypointGroup group) {
		if (temporary.take(temp) == null || group == null || group.readOnly()) {
			return;
		}
		String name = temp.sender().isEmpty() ? temp.name() : temp.name() + " (" + temp.sender() + ")";
		if (group.add(new Waypoint(temp.point().x(), temp.point().y(), temp.point().z(), name))) {
			store.changed(group);
			lastGroup.set(group.id());
			LumioChat.info(Component.literal("Saved “" + name + "” to " + group.name() + "."));
		}
	}

	/** The temporary waypoints: gone on a world change, when their time is over, or (if wanted) when reached. */
	private void tickTemporary(Minecraft client, long now) {
		if (client.level != temporaryWorld) {
			temporaryWorld = client.level;
			temporary.clear();
		}
		if (client.player != null && !temporary.list().isEmpty()) {
			temporary.tick(now, Math.round(temporaryMinutes.get()) * MILLIS_PER_MINUTE, removeReached.get(), client.player.getX(), client.player.getY(),
				client.player.getZ());
		}
	}

	/** @return whether a temporary waypoint is near the end of its time, drawn softer */
	boolean fading(int index) {
		List<TemporaryWaypoints.Temp> temps = temporary.list();
		return index >= 0 && index < temps.size()
			&& TemporaryWaypoints.fading(temps.get(index), System.currentTimeMillis(), Math.round(temporaryMinutes.get()) * MILLIS_PER_MINUTE);
	}

	/**
	 * Takes a pasted list of points to the import on the Waypoints page, opening the menu there.
	 *
	 * @return whether the text was a list (a single position is not)
	 */
	boolean pasted(String text) {
		WaypointImport.Result result = WaypointImport.read(text);
		if (result == null || result.points() < 2) {
			return false;
		}
		openImport(text);
		MacScreen.select(ModFeatures.WAYPOINTS_PAGE);
		Minecraft.getInstance().gui.setScreen(new MacScreen());
		return true;
	}

	// ------------------------------------------------------------------------------------------------------ import and export

	/** Whether the page shows import and export instead of the groups, and a text to take in when it opens. */
	private boolean importOpen;
	private String importText;

	boolean importOpen() {
		return importOpen;
	}

	/** Shows import and export on the page; with a text, that text is taken in as if pasted. */
	public void openImport(String text) {
		importOpen = true;
		importText = text;
	}

	/** @return the text to take in once, or null */
	String takeImportText() {
		String text = importText;
		importText = null;
		return text;
	}

	void closeImport() {
		importOpen = false;
		importText = null;
	}

	@Override
	public List<HeaderButton> headerButtons() {
		if (importOpen) {
			return List.of(new HeaderButton("Done", false, this::closeImport));
		}
		return List.of(new HeaderButton("Import", false, () -> openImport(null)), new HeaderButton("Export", false, () -> openImport(null)));
	}

	@Override
	public void pageLeft() {
		closeImport();
	}

	/** Whether the page shows a group's Settings tab rather than its Points; kept while the game runs, so the page opens where it was left. */
	private boolean settingsTab;

	boolean settingsTab() {
		return settingsTab;
	}

	void settingsTab(boolean shown) {
		settingsTab = shown;
	}

	/** Makes a copy of a group, with all its points and settings, named "… copy", where the group shows; and shows it on the page. */
	public WaypointGroup duplicate(WaypointGroup group) {
		WaypointGroup copy = WaypointJson.readGroup(WaypointJson.write(group), id -> store.newId());
		copy.rename(group.name() + " copy");
		store.add(copy, WaypointAreas.everywhere(copy));
		select(copy);
		return copy;
	}

	/** Makes a group for an imported list and shows it on the page. */
	WaypointGroup importGroup(String name, int rgb, String area, WaypointGroup.Show show, String source) {
		WaypointGroup group = new WaypointGroup(store.newId(), name, rgb);
		group.areas().add(area);
		group.show(show);
		group.source(source);
		group.restartInNewLobby(restartNewRoutes.get());
		store.add(group, WaypointAreas.everywhere(group));
		select(group);
		return group;
	}

	/** Takes in a change to a group's areas: one that now shows everywhere may move to the groups of every server. */
	public void areasChanged(WaypointGroup group) {
		store.changed(group);
		store.place(group, WaypointAreas.everywhere(group));
	}
}
