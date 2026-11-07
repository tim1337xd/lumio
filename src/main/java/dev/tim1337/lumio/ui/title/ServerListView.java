package dev.tim1337.lumio.ui.title;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import com.mojang.blaze3d.platform.InputConstants;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.ui.Confirm;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.ServerStatusPinger;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.server.LanServer;
import net.minecraft.client.server.LanServerDetection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.server.network.EventLoopGroupHolder;
import net.minecraft.util.FormattedCharSequence;

import java.net.UnknownHostException;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The server list in the main menu: the game's own list of saved servers, pinged the way the game's Multiplayer screen
 * does, so both stay in sync. A field filters the servers by name and address, and joins the selection or a typed address; the typed address
 * can be saved as a server. Rows show the server's icon, name, address and message of the day, its ping and its players; they can be edited,
 * moved, deleted or dragged to a new place. Worlds opened to LAN on the network appear below.
 */
public final class ServerListView extends TitleListView {
	private static final int PING_THREADS = 3;
	private static final int[] TILE_COLORS = {0xFFFF9F0A, 0xFF5E5CE6, 0xFF0A84FF, 0xFF30D158, 0xFFBF5AF2, 0xFFFF375F, 0xFF64D2FF};
	private static final int OFFLINE_TILE = 0xFF48484A;
	private static final int OLD_VERSION_TEXT = 0xFFFFD479;
	private static final int GOOD_PING = SystemColors.GREEN;
	private static final int FAIR_PING = SystemColors.YELLOW;
	private static final int BAD_PING = SystemColors.RED;
	private static final float FORM_HEIGHT = 168f;
	private static final float LINK_HEIGHT = 22f;
	private static final String[] PACK_LABELS = {"Prompt", "Enabled", "Disabled"};
	private static final ServerData.ServerPackStatus[] PACK_STATUS = {ServerData.ServerPackStatus.PROMPT, ServerData.ServerPackStatus.ENABLED,
		ServerData.ServerPackStatus.DISABLED};

	private final Screen title;
	private final Minecraft minecraft = Minecraft.getInstance();
	private ServerList servers;
	private ServerStatusPinger pinger;
	private ExecutorService pingThreads;
	private final List<ServerData> all = new ArrayList<>();
	private final Map<ServerData, Icon> icons = new IdentityHashMap<>();
	private final Map<ServerData, String[]> texts = new IdentityHashMap<>();
	/** The line of each server's message of the day that the row shows, made when the server's status changes. */
	private final Map<ServerData, FormattedCharSequence> motds = new IdentityHashMap<>();
	private LanServerDetection.LanServerList lanList;
	private LanServerDetection.LanServerDetector lanDetector;
	private List<LanServer> lan = List.of();
	private int online;
	private String subtitle = "";
	// the form to save the typed address
	private boolean formOpen;
	private final TitleField formName = new TitleField("Name");
	private final TitleField formAddress = new TitleField("Address");
	private int formPack;
	/** The layout scale, for tooltips, which are placed in screen pixels. */
	public float scale = 1f;

	/** A server's icon: the bytes it came from, and the texture once uploaded. */
	private static final class Icon {
		byte[] from;
		FaviconTexture texture;
		volatile NativeImage decoded;
		boolean ready;
	}

	public ServerListView(Screen title) {
		super("Address, or search your servers");
		this.title = title;
	}

	@Override
	public void open() {
		servers = new ServerList(minecraft);
		servers.load();
		all.clear();
		for (int i = 0; i < servers.size(); i++) {
			all.add(servers.get(i));
		}
		pinger = new ServerStatusPinger();
		pingThreads = Executors.newFixedThreadPool(PING_THREADS, task -> {
			Thread thread = new Thread(task, "Lumio server ping");
			thread.setDaemon(true);
			return thread;
		});
		for (ServerData data : all) {
			ping(data);
		}
		lanList = new LanServerDetection.LanServerList();
		try {
			lanDetector = new LanServerDetection.LanServerDetector(lanList);
			lanDetector.start();
		} catch (Exception error) {
			Logs.warn("Worlds opened to LAN can not be found", error);
			lanDetector = null;
		}
		formOpen = false;
		super.open();
	}

	@Override
	public void close() {
		super.close();
		if (pinger != null) {
			pinger.removeAll();
		}
		if (pingThreads != null) {
			pingThreads.shutdownNow();
		}
		if (lanDetector != null) {
			lanDetector.interrupt();
			lanDetector = null;
		}
	}

	@Override
	public void dispose() {
		for (Icon icon : icons.values()) {
			if (icon.texture != null) {
				icon.texture.close();
			}
		}
		icons.clear();
	}

	/** Pings a server the way the game's Multiplayer screen does: its status, players and icon, on a few background threads. */
	private void ping(ServerData data) {
		if (data.state() != ServerData.State.INITIAL) {
			return;
		}
		data.setState(ServerData.State.PINGING);
		data.motd = Component.empty();
		data.status = Component.empty();
		ServerStatusPinger owner = pinger;
		pingThreads.submit(() -> {
			try {
				owner.pingServer(data, () -> minecraft.execute(this::statusChanged), () -> {
					data.setState(data.protocol == SharedConstants.getProtocolVersion() ? ServerData.State.SUCCESSFUL : ServerData.State.INCOMPATIBLE);
					minecraft.execute(this::statusChanged);
				}, EventLoopGroupHolder.remote(minecraft.options.useNativeTransport()));
			} catch (UnknownHostException error) {
				data.setState(ServerData.State.UNREACHABLE);
				minecraft.execute(this::statusChanged);
			} catch (Exception error) {
				data.setState(ServerData.State.UNREACHABLE);
				minecraft.execute(this::statusChanged);
			}
		});
	}

	private void statusChanged() {
		texts.clear();
		motds.clear();
		online = 0;
		for (ServerData data : all) {
			if (data.state() == ServerData.State.SUCCESSFUL) {
				online++;
			}
		}
		subtitle = online + " of " + all.size() + " online";
	}

	@Override
	public void tick() {
		if (pinger != null) {
			pinger.tick();
		}
		if (lanList != null) {
			// null while nothing changed since the last look
			List<LanServer> found = lanList.takeDirtyServers();
			if (found != null) {
				lan = found;
			}
		}
	}

	@Override
	void refilter() {
		List<String> names = new ArrayList<>(all.size());
		List<String> addresses = new ArrayList<>(all.size());
		for (ServerData data : all) {
			names.add(data.name);
			addresses.add(data.ip);
		}
		visible = ListFilter.matching(names, addresses, field.text());
		if (selected >= visible.size()) {
			selected = visible.isEmpty() ? -1 : 0;
		}
		statusChanged();
	}

	@Override
	String overline() {
		return "MULTIPLAYER";
	}

	@Override
	String heading() {
		return "Servers";
	}

	@Override
	String subtitle() {
		return subtitle;
	}

	@Override
	String primaryLabel() {
		return "Join";
	}

	@Override
	String sectionLabel() {
		return "SAVED · " + all.size();
	}

	@Override
	String sectionNote() {
		return "Drag to reorder";
	}

	/** Joins the selected server, the first one matching the field, or the typed address. */
	@Override
	void primary() {
		int index = selectedIndex();
		if (index < 0 && !visible.isEmpty() && !field.text().isBlank()) {
			index = visible.get(0);
		}
		if (index >= 0) {
			join(all.get(index));
		} else if (ListFilter.looksLikeAddress(field.text())) {
			String address = field.text().strip();
			join(entryFor(address));
		}
	}

	private void join(ServerData data) {
		ConnectScreen.startConnecting(title, minecraft, ServerAddress.parseString(data.ip), data, false, null);
	}

	@Override
	void activate(int index) {
		join(all.get(index));
	}

	@Override
	List<String> menu(int index) {
		return List.of("Edit", "Move up", "Move down", "Delete…");
	}

	@Override
	void menuAction(int index, int item) {
		ServerData data = all.get(index);
		switch (item) {
			case 0 -> {
				ServerData edited = new ServerData(data.name, data.ip, ServerData.Type.OTHER);
				edited.copyFrom(data);
				minecraft.gui.setScreen(new ManageServerScreen(title, Component.translatable("manageServer.edit.title"), ok -> {
					if (ok) {
						data.copyNameIconFrom(edited);
						data.ip = edited.ip;
						data.setResourcePackStatus(edited.getResourcePackStatus());
						data.setState(ServerData.State.INITIAL);
						servers.save();
						ping(data);
					}
					minecraft.gui.setScreen(title);
				}, edited));
			}
			case 1 -> move(index, Math.max(0, index - 1));
			case 2 -> move(index, Math.min(all.size() - 1, index + 1));
			default -> Confirm.ask("Delete " + data.name + "?", "The server is removed from your list.", "Delete", "Cancel", () -> {
				servers.remove(data);
				servers.save();
				all.remove(data);
				Icon icon = icons.remove(data);
				if (icon != null && icon.texture != null) {
					icon.texture.close();
				}
				selected = -1;
				refilter();
			});
		}
		refilter();
	}

	@Override
	boolean reorderable() {
		return true;
	}

	/** Moves a server to a new place in the game's list, one step at a time as the game's list does. */
	@Override
	void move(int from, int to) {
		int at = from;
		while (at != to) {
			int next = at < to ? at + 1 : at - 1;
			servers.swap(at, next);
			at = next;
		}
		servers.save();
		all.clear();
		for (int i = 0; i < servers.size(); i++) {
			all.add(servers.get(i));
		}
	}

	// ---------------------------------------------------------------------------------------------------------------- rows

	@Override
	void drawRow(Ui ui, int index, float x, float y, float width, boolean hot, float alpha, int mouseX, int mouseY) {
		ServerData data = all.get(index);
		ServerData.State state = data.state();
		boolean offline = state == ServerData.State.UNREACHABLE;
		float tileX = x + 10f;
		float tileY = y + (ROW - 42f) / 2f;
		Icon icon = icon(data);
		if (icon != null && icon.ready && icon.texture != null) {
			roundedTexture(ui, icon.texture.textureLocation(), tileX, tileY, 42f, 42f, Radius.LG, Ui.withOpacity(0xFFFFFFFF, alpha));
		} else {
			int color = offline ? OFFLINE_TILE : TILE_COLORS[Math.floorMod(data.name.hashCode(), TILE_COLORS.length)];
			ui.roundedGradient(Math.round(tileX), Math.round(tileY), 42, 42, Radius.LG, Ui.withOpacity(color, alpha),
				Ui.withOpacity(Ui.mix(color, 0xFF000000, 0.25f), alpha));
			String letter = offline ? "?" : data.name.isBlank() ? "?" : data.name.substring(0, 1).toUpperCase(Locale.ROOT);
			float scale = 17f * TEXT;
			ui.textAtScaledBold(letter, tileX + 21f - ui.widthScaled(letter, scale) / 2f, tileY + 21f - 4.5f * scale, scale, Ui.withOpacity(0xFFFFFFFF, alpha));
		}
		// ping and players on the right
		float right = x + width - 14f;
		float small = 12f * TEXT;
		String[] text = texts.computeIfAbsent(data, ServerListView::rowTexts);
		String ping = text[0];
		float pingWidth = ui.widthScaled(ping, 13f * TEXT);
		ui.textAtScaledBold(ping, right - pingWidth, y + 16f, 13f * TEXT, Ui.withOpacity(0xFFFFFFFF, alpha));
		drawBars(ui, right - pingWidth - 21f, y + 16f, state, data.ping, alpha);
		String players = text[1];
		float playersWidth = ui.widthScaled(players, small);
		float playersX = right - playersWidth;
		ui.textAtScaled(players, playersX, y + 38f, small, Ui.withOpacity(0xFFFFFFFF, 0.6f * alpha));
		if (!data.playerList.isEmpty() && mouseX >= playersX && mouseX < right && mouseY >= y + 34f && mouseY < y + 50f) {
			List<FormattedCharSequence> lines = new ArrayList<>(data.playerList.size());
			for (Component line : data.playerList) {
				lines.add(line.getVisualOrderText());
			}
			ui.graphics.setTooltipForNextFrame(lines, Math.round(mouseX * scale), Math.round(mouseY * scale));
		}
		// name and the line under it
		float textX = tileX + 42f + 13f;
		float room = Math.min(playersX, right - pingWidth - 26f) - textX - 10f;
		ui.textAtScaledBold(ui.fitScaled(data.name, room, 16f * TEXT), textX, y + 15f, 16f * TEXT, Ui.withOpacity(0xFFFFFFFF, alpha));
		float lineY = y + 38f;
		float lineScale = 12.5f * TEXT;
		if (offline) {
			ui.textAtScaled("Can't reach the server", textX, lineY, lineScale, Ui.withOpacity(0xFFFFFFFF, 0.45f * alpha));
		} else if (state == ServerData.State.INCOMPATIBLE) {
			ui.textAtScaled(ui.fitScaled(text[3], room, lineScale), textX, lineY, lineScale, Ui.withOpacity(OLD_VERSION_TEXT, alpha));
		} else {
			String address = text[2];
			ui.textAtScaled(ui.fitScaled(address, room, lineScale), textX, lineY, lineScale, Ui.withOpacity(0xFFFFFFFF, 0.62f * alpha));
			float used = ui.widthScaled(address, lineScale);
			if (used < room - 20f) {
				drawMotd(ui, data, textX + used, lineY, room - used, lineScale, alpha);
			}
		}
	}

	/** The message of the day in its own colors, on one line, cut with "…": the game's font keeps the server's formatting. */
	private void drawMotd(Ui ui, ServerData data, float x, float y, float room, float textScale, float alpha) {
		var font = minecraft.font;
		float factor = textScale * 1.05f;
		int width = Math.round(room / factor);
		FormattedCharSequence line = motds.computeIfAbsent(data, ServerListView::motdLine);
		boolean cut = font.width(line) > width;
		var pose = ui.graphics.pose();
		pose.pushMatrix();
		pose.translate(x, y - 0.5f);
		pose.scale(factor, factor);
		ui.graphics.enableScissor(0, -1, cut ? width - font.width("…") : width, 10);
		ui.graphics.text(font, line, 0, 0, Ui.withOpacity(0xFFFFFFFF, 0.62f * alpha), false);
		ui.graphics.disableScissor();
		if (cut) {
			ui.graphics.text(font, "…", width - font.width("…"), 0, Ui.withOpacity(0xFFFFFFFF, 0.62f * alpha), false);
		}
		pose.popMatrix();
	}

	/**
	 * @return the first line of the message of the day that has text, in its own colors, without the spaces before it: servers put them there
	 *         to center the message in the game's list, and in the row they pushed it far to the right
	 */
	private static FormattedCharSequence motdLine(ServerData data) {
		List<MutableComponent> lines = new ArrayList<>();
		lines.add(Component.empty());
		// whether the line has its first character that is not a space yet
		boolean[] started = {false};
		data.motd.visit((style, text) -> {
			String[] parts = text.split("\n", -1);
			for (int i = 0; i < parts.length; i++) {
				if (i > 0) {
					lines.add(Component.empty());
					started[0] = false;
				}
				String part = started[0] ? parts[i] : parts[i].stripLeading();
				if (!part.isEmpty()) {
					lines.getLast().append(Component.literal(part).withStyle(style));
					started[0] = true;
				}
			}
			return Optional.empty();
		}, Style.EMPTY);
		for (MutableComponent line : lines) {
			if (!line.getString().isBlank()) {
				return line.getVisualOrderText();
			}
		}
		return FormattedCharSequence.EMPTY;
	}

	/**
	 * @return the server list's entry for an address typed in, or a new hidden one, as the game's Direct Connect makes it: the entry keeps what is
	 *         chosen while joining (such as the answer to the resource pack prompt), so it is not asked again
	 */
	private ServerData entryFor(String address) {
		ServerData known = servers.get(address);
		if (known != null) {
			return known;
		}
		ServerData data = new ServerData(address, address, ServerData.Type.OTHER);
		servers.add(data, true);
		servers.save();
		return data;
	}

	/** @return the texts of a row: the ping, the players, the address with its separator, and the version a server needs */
	private static String[] rowTexts(ServerData data) {
		ServerData.State state = data.state();
		String ping = state == ServerData.State.SUCCESSFUL || state == ServerData.State.INCOMPATIBLE ? data.ping + " ms" : "–";
		String address = data.ip + (data.motd.getString().isBlank() ? "" : "  ·  ");
		return new String[]{ping, players(data), address, "Needs " + data.version.getString()};
	}

	private static String players(ServerData data) {
		if (data.state() == ServerData.State.UNREACHABLE) {
			return "–";
		}
		if (data.players == null) {
			return data.state() == ServerData.State.PINGING ? "Pinging…" : "";
		}
		if (data.players.max() >= 1000) {
			return NumberFormat.getIntegerInstance(Locale.ENGLISH).format(data.players.online()) + " online";
		}
		return data.players.online() + " / " + data.players.max();
	}

	private static void drawBars(Ui ui, float x, float y, ServerData.State state, long ping, float alpha) {
		int lit = state != ServerData.State.SUCCESSFUL && state != ServerData.State.INCOMPATIBLE ? 0 : ping < 150 ? 4 : ping < 300 ? 3 : ping < 600 ? 2 : 1;
		int color = lit >= 3 ? GOOD_PING : lit == 2 ? FAIR_PING : BAD_PING;
		for (int i = 0; i < 4; i++) {
			float height = 4f + i * 3f;
			ui.roundedSmooth(x + i * 5f, y + 10f - height, 3f, height, 1f, Ui.withOpacity(i < lit ? color : 0x33FFFFFF, alpha));
		}
	}

	/** @return the icon of a server, decoded in the background and uploaded on the render thread when it is new; null if it has none */
	private Icon icon(ServerData data) {
		byte[] bytes = data.getIconBytes();
		if (bytes == null) {
			return null;
		}
		Icon icon = icons.computeIfAbsent(data, key -> new Icon());
		if (icon.from != bytes && !pingThreads.isShutdown()) {
			icon.from = bytes;
			icon.ready = false;
			icon.decoded = null;
			pingThreads.submit(() -> {
				try {
					icon.decoded = NativeImage.read(bytes);
				} catch (Exception error) {
					icon.decoded = null;
				}
			});
		}
		if (!icon.ready && icon.decoded != null) {
			if (icon.texture == null) {
				icon.texture = FaviconTexture.forServer(minecraft.getTextureManager(), data.ip);
			}
			icon.texture.upload(icon.decoded);
			icon.decoded = null;
			icon.ready = true;
		}
		return icon;
	}

	// ------------------------------------------------------------------------------------------------ saving the typed address

	@Override
	float extraHeight() {
		return LINK_HEIGHT + (formOpen ? FORM_HEIGHT : 0f);
	}

	@Override
	void drawExtra(Ui ui, float x, float y, float alpha, int mouseX, int mouseY) {
		float linkY = y - 8f;
		ui.icon("plus", Math.round(x + 8f), Math.round(linkY + 1f), 14, Ui.withOpacity(0xFFFFFFFF, 0.7f * alpha));
		ui.textAtScaled(formOpen ? "Save as a server" : "Save the typed address as a server", x + 28f, linkY + 2f, 13f * TEXT,
			Ui.withOpacity(0xFFFFFFFF, 0.7f * alpha));
		if (!formOpen) {
			return;
		}
		float top = y + LINK_HEIGHT - 4f;
		ui.roundedSmooth(x, top, WIDTH, FORM_HEIGHT - 12f, 18f, Ui.withOpacity(0xFF0E0A1E, 0.55f * alpha));
		formName.draw(ui, x + 10f, top + 10f, WIDTH - 20f, 36f, alpha);
		formAddress.draw(ui, x + 10f, top + 52f, WIDTH - 20f, 36f, alpha);
		ui.textAtScaled("Server resource packs", x + 14f, top + 104f, 13f * TEXT, Ui.withOpacity(0xFFFFFFFF, 0.7f * alpha));
		float segmentX = x + WIDTH - 10f - 3 * 70f;
		for (int i = 0; i < PACK_LABELS.length; i++) {
			float sx = segmentX + i * 70f;
			if (i == formPack) {
				ui.roundedSmooth(sx, top + 96f, 68f, 26f, 8f, Ui.withOpacity(0xFFFFFFFF, 0.16f * alpha));
			}
			float scale = 12.5f * TEXT;
			ui.textAtScaled(PACK_LABELS[i], sx + 34f - ui.widthScaled(PACK_LABELS[i], scale) / 2f, top + 103f, scale, Ui.withOpacity(0xFFFFFFFF, alpha));
		}
		float buttonsY = top + 128f;
		ui.roundedSmooth(x + WIDTH - 10f - 90f, buttonsY, 90f, 26f, 8f, Ui.withOpacity(ui.accent, alpha));
		ui.textAtScaledBold("Save", x + WIDTH - 10f - 45f - ui.widthScaled("Save", 13f * TEXT) / 2f, buttonsY + 7f, 13f * TEXT, Ui.withOpacity(0xFFFFFFFF, alpha));
		ui.roundedSmooth(x + WIDTH - 10f - 190f, buttonsY, 90f, 26f, 8f, Ui.withOpacity(0xFFFFFFFF, 0.10f * alpha));
		ui.textAtScaled("Cancel", x + WIDTH - 10f - 145f - ui.widthScaled("Cancel", 13f * TEXT) / 2f, buttonsY + 7f, 13f * TEXT, Ui.withOpacity(0xFFFFFFFF, alpha));
	}

	@Override
	boolean clickExtra(double mouseX, double mouseY, float y) {
		float linkY = y - 8f;
		if (mouseY >= linkY - 4f && mouseY < linkY + 18f && mouseX >= LEFT && mouseX < LEFT + 280f) {
			formOpen = !formOpen;
			if (formOpen) {
				String typed = field.text().strip();
				formAddress.setText(ListFilter.looksLikeAddress(typed) ? typed : "");
				formName.setText(formAddress.text().isEmpty() ? "" : typed);
				formName.focus(true);
				formAddress.focus(false);
			}
			UiSounds.click();
			return true;
		}
		if (!formOpen) {
			return false;
		}
		float top = y + LINK_HEIGHT - 4f;
		if (mouseY < top || mouseY >= top + FORM_HEIGHT || mouseX < LEFT || mouseX >= LEFT + WIDTH) {
			return false;
		}
		formName.focus(mouseY >= top + 10f && mouseY < top + 46f);
		formAddress.focus(mouseY >= top + 52f && mouseY < top + 88f);
		float segmentX = LEFT + WIDTH - 10f - 3 * 70f;
		if (mouseY >= top + 96f && mouseY < top + 122f && mouseX >= segmentX) {
			formPack = Math.min(2, (int) ((mouseX - segmentX) / 70f));
			UiSounds.click();
		}
		float buttonsY = top + 128f;
		if (mouseY >= buttonsY && mouseY < buttonsY + 26f) {
			if (mouseX >= LEFT + WIDTH - 100f) {
				saveForm();
			} else if (mouseX >= LEFT + WIDTH - 200f) {
				formOpen = false;
				UiSounds.click();
			}
		}
		return true;
	}

	private void saveForm() {
		String address = formAddress.text().strip();
		if (!ListFilter.looksLikeAddress(address)) {
			formAddress.focus(true);
			return;
		}
		String name = formName.text().isBlank() ? address : formName.text().strip();
		ServerData data = new ServerData(name, address, ServerData.Type.OTHER);
		data.setResourcePackStatus(PACK_STATUS[formPack]);
		servers.add(data, false);
		servers.save();
		all.add(data);
		ping(data);
		formOpen = false;
		field.setText("");
		UiSounds.click();
		refilter();
	}

	@Override
	public boolean key(int key, int modifiers, Runnable back) {
		if (formOpen && (formName.focused() || formAddress.focused())) {
			boolean control = (modifiers & ScreenInput.CONTROL) != 0;
			if (key == InputConstants.KEY_ESCAPE) {
				formOpen = false;
				return true;
			}
			if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
				saveForm();
				return true;
			}
			if (key == InputConstants.KEY_TAB) {
				boolean name = formName.focused();
				formName.focus(!name);
				formAddress.focus(name);
				return true;
			}
			return formName.keyPressed(key, control) || formAddress.keyPressed(key, control);
		}
		return super.key(key, modifiers, back);
	}

	@Override
	public boolean charTyped(int codepoint) {
		if (formOpen && (formName.focused() || formAddress.focused())) {
			return formName.charTyped(codepoint) || formAddress.charTyped(codepoint);
		}
		return super.charTyped(codepoint);
	}

	// ---------------------------------------------------------------------------------------------------------------- LAN

	@Override
	float afterHeight() {
		return 22f + 22f + Math.max(1, lan.size()) * 22f;
	}

	@Override
	void drawAfter(Ui ui, float x, float y, float alpha) {
		ui.textAtScaledBold("LAN", x + 6f, y, 12f * TEXT, Ui.withOpacity(0xFFFFFFFF, 0.66f * alpha));
		String note = lan.isEmpty() ? "Searching…" : lan.size() + " found";
		float scale = 12f * TEXT;
		ui.textAtScaled(note, x + WIDTH - 6f - ui.widthScaled(note, scale), y, scale, Ui.withOpacity(0xFFFFFFFF, 0.66f * alpha));
		float rowY = y + 24f;
		if (lan.isEmpty()) {
			ui.textAtScaled("Worlds opened to LAN on your network show up here.", x + 8f, rowY, 13f * TEXT, Ui.withOpacity(0xFFFFFFFF, 0.55f * alpha));
			return;
		}
		for (LanServer server : lan) {
			ui.textAtScaled(ui.fitScaled(server.getMotd() + "  ·  " + server.getAddress(), WIDTH - 16f, 13f * TEXT), x + 8f, rowY, 13f * TEXT,
				Ui.withOpacity(0xFFFFFFFF, 0.8f * alpha));
			rowY += 22f;
		}
	}

	/** Joins a LAN world when its line is clicked. @return whether one was hit */
	@Override
	boolean clickAfter(double mouseX, double mouseY, float afterTop) {
		float rowY = afterTop + 24f;
		for (LanServer server : lan) {
			if (mouseY >= rowY - 4f && mouseY < rowY + 16f && mouseX >= LEFT && mouseX < LEFT + WIDTH) {
				UiSounds.click();
				join(new ServerData(server.getMotd(), server.getAddress(), ServerData.Type.LAN));
				return true;
			}
			rowY += 22f;
		}
		return false;
	}
}
