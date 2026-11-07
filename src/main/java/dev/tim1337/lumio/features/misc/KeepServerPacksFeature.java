package dev.tim1337.lumio.features.misc;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.api.experimental.util.Debug;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.config.setting.StringSetting;
import dev.tim1337.lumio.mixin.accessor.DownloadedPackSourceAccessor;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.PackReloadConfig;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.repository.Pack;

import java.net.URL;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/**
 * Keep server resource packs: the game reloads all of its resources whenever the packs of a server change, so when a server's pack is applied,
 * again when it is removed as the player leaves, and again when the same pack comes back on the next join or server change of a network. With this
 * feature the reload is left out when the packs the game asks for are exactly the packs its resources hold, and a server's packs stay loaded after
 * leaving until another server or a world of the player's own needs other ones.
 * <p>
 * Everything else stays the game's own: the prompt, the "disabled" setting of a server, the download (the game keeps the files it downloaded) and
 * every answer to the server. A left-out reload only ends sooner: the game's own success callback runs on the next tick, so the server hears "loaded"
 * as it would from a fast computer. Nothing is sent, changed or cancelled on the connection.
 * <p>
 * The packs of the server last played on are also loaded with the game at its start (from the game's own downloads, when their files are still
 * there and unchanged; nothing is downloaded then), so even the first join does not reload. Like kept packs after leaving, they are active in the
 * main menu until another server or a world of the player's own needs other ones. Only the server's address and the packs' ids and file names are
 * kept, in a private setting; the files are the ones the game downloaded, in its own folder. With the debug log on, what the server sends and what
 * is decided are written to the log, without any account data. The decisions themselves are in {@link ServerPackMemory}.
 */
public class KeepServerPacksFeature extends Feature {
	private static final String DOWNLOADS = "downloads";

	/** A load of no server packs, which removes kept packs; nothing waits for its result. */
	private static final PackReloadConfig.Callbacks NO_PACKS = new PackReloadConfig.Callbacks() {
		@Override
		public void onSuccess() {
		}

		@Override
		public void onFailure(boolean recovered) {
		}

		@Override
		public List<PackReloadConfig.IdAndPath> packsToLoad() {
			return List.of();
		}
	};

	/** The server packs the game's resources hold, as the game lists them; null when that is unknown (after a failed load), then nothing is left out. */
	private List<PackReloadConfig.IdAndPath> loaded = List.of();
	/** The address of the server the loaded packs came from, or null. */
	private String loadedFrom;
	/** Whether the player left a server and its packs were kept loaded instead of being removed. */
	private boolean kept;
	/** From leaving a server until the next server is joined or a world loads: the removal of the packs is left out. */
	private boolean leaving;
	/** Whether the server being joined asked for its packs already: then they are on their way, and kept packs are not removed before them. */
	private boolean requestedSinceJoin;
	/** The server being joined or played on, from the start of its configuration (when it sends its packs), or null in a world of the player's own. */
	private ServerData joined;
	/** The packs to load again at the next start: the server's address, then a line per pack with its id and file name. Hidden and private. */
	private final StringSetting saved = add(new StringSetting("saved_packs", Component.literal("Saved server packs"), "", false));

	public KeepServerPacksFeature() {
		super("keep_server_packs", "Keep server resource packs",
			"Joining a server again, also after restarting the game, or changing servers on a network does not reload the game's resources when the "
				+ "server's resource pack is the same. The kept pack stays active in the main menu until you join another server or open a world.",
			true);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("layers");
		summary("No reload when a server's pack did not change");
		saved.visibleWhen(() -> false);
		saved.hideFromRecent();
	}

	/**
	 * The game is about to reload its resources for the server packs it lists.
	 *
	 * @param reloadRunning whether a reload of the game's is still running: then nothing is left out, so its result goes where the game expects it
	 * @return whether the reload can be left out: the resources already hold exactly these packs, or the player left and the packs are kept
	 */
	public boolean skipReload(List<PackReloadConfig.IdAndPath> requested, boolean reloadRunning) {
		if (!leaving) {
			requestedSinceJoin = true;
		}
		if (isEnabled() && ServerPackMemory.skip(requested, loaded, leaving, reloadRunning)) {
			kept = leaving && !loaded.isEmpty();
			Debug.log("Keep server resource packs: reload left out for {} ({})", files(requested), leaving ? "kept after leaving" : "already loaded");
			return true;
		}
		Debug.log("Keep server resource packs: reload for {}; the resources held {}{}", files(requested), loaded == null ? "unknown packs" : files(loaded),
			reloadRunning ? " (a reload was still running)" : "");
		loaded = List.copyOf(requested);
		kept = false;
		loadedFrom = requested.isEmpty() || joined == null ? null : joined.ip;
		save(requested);
		return false;
	}

	/** The player left the server: until the next server or world, the removal of its packs is left out. */
	public void leaving() {
		leaving = true;
		requestedSinceJoin = false;
	}

	/**
	 * A server is being joined: its configuration starts, in which it sends its packs. Its packs are its own from now on, so they are no longer
	 * kept as after leaving; packs kept from before stay until its configuration ends.
	 */
	public void joining(ServerData server) {
		joined = server;
		leaving = false;
		requestedSinceJoin = false;
	}

	/**
	 * The configuration of a server ended. Kept packs stay for the server they came from (it sends them again, and the reload is left out); for
	 * another server, or one whose packs the player turned off, they are removed now, unless the server already asked for its own packs.
	 */
	public void configurationFinished() {
		if (kept && !requestedSinceJoin && (!isEnabled() || joined == null || !joined.ip.equals(loadedFrom)
			|| joined.getResourcePackStatus() == ServerData.ServerPackStatus.DISABLED)) {
			Debug.log("Keep server resource packs: the kept packs are removed, they belong to another server");
			removeKept();
		}
	}

	/** A world of the player's own is loading: kept packs of a server are removed. */
	public void worldLoading() {
		leaving = false;
		joined = null;
		if (kept) {
			removeKept();
		}
	}

	/** A load of server packs failed and the game recovered without some of them: what the resources hold is unknown until the next load. */
	public void loadFailed() {
		loaded = null;
		kept = false;
	}

	/** Removes kept packs with the game's own load of no server packs, which reloads the resources as the game would have on leaving. */
	private void removeKept() {
		kept = false;
		((DownloadedPackSourceAccessor) Minecraft.getInstance().getDownloadedPackSource()).lumio$startReload(NO_PACKS);
	}

	/**
	 * Remembers the packs just loaded for the next start: those of a server, from the game's downloads. Anything else (no packs, or a world's own
	 * pack from elsewhere) clears what was saved.
	 */
	private void save(List<PackReloadConfig.IdAndPath> packs) {
		String text = ServerPackMemory.written(loadedFrom, packs, downloads());
		if (!text.equals(saved.get())) {
			saved.set(text);
			FeatureManager.saveSoon();
		}
	}

	/**
	 * At the start of the game, just before its first load of resources: the packs saved last time are given to the game's own pack loader, so
	 * they are part of that load, and the next join of their server leaves its reload out. Packs whose file the game no longer has, or whose file
	 * changed, load nothing: the game starts without them. Nothing is downloaded.
	 */
	public void restore(DownloadedPackSource source, Path gameDirectory) {
		String text = saved.get();
		if (!isEnabled() || text.isEmpty()) {
			return;
		}
		// still before the game's first frame, as part of its start: the files are read and their hashes checked here, so the start load can
		// include them
		List<PackReloadConfig.IdAndPath> packs = ServerPackMemory.restorable(text, gameDirectory.resolve(DOWNLOADS).toAbsolutePath().normalize());
		if (packs.isEmpty()) {
			Debug.log("Keep server resource packs: the saved packs are no longer in the game's downloads, or changed; the game starts without them");
			return;
		}
		DownloadedPackSourceAccessor accessor = (DownloadedPackSourceAccessor) source;
		List<Pack> opened = accessor.lumio$loadRequestedPacks(packs);
		if (opened == null) {
			Debug.log("Keep server resource packs: a saved pack could not be opened; the game starts without it");
			return;
		}
		accessor.lumio$setPackSource(DownloadedPackSourceAccessor.lumio$configureSource(opened));
		// as after leaving that server: its packs are kept, and stay for it
		loaded = List.copyOf(packs);
		loadedFrom = ServerPackMemory.server(text);
		kept = true;
		leaving = true;
		Debug.log("Keep server resource packs: loaded with the game's start: {} of {}", files(packs), loadedFrom);
	}

	/** With the debug log on: a pack the server sent, by its id, hash and address (without its query, which could hold more than the file's name). */
	public void serverSent(UUID id, URL url, String hash) {
		if (Debug.on()) {
			String address = url == null ? "none" : url.getProtocol() + "://" + url.getHost() + url.getPath();
			Debug.log("Keep server resource packs: the server sent pack {} with hash {} from {}; the resources hold {}", id,
				hash == null || hash.isEmpty() ? "none" : hash, address, loaded == null ? "unknown packs" : files(loaded));
		}
	}

	/** With the debug log on: the server removed its packs (all of them, or the one with this id). */
	public void serverRemoved(UUID id) {
		Debug.log("Keep server resource packs: the server removed {}", id == null ? "all its packs" : "pack " + id);
	}

	/** @return the packs' file names (their hashes), for the debug log */
	private static String files(List<PackReloadConfig.IdAndPath> packs) {
		if (packs.isEmpty()) {
			return "no packs";
		}
		StringBuilder text = new StringBuilder();
		for (PackReloadConfig.IdAndPath pack : packs) {
			text.append(text.isEmpty() ? "" : ", ").append(pack.path().getFileName());
		}
		return text.toString();
	}

	private static Path downloads() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve(DOWNLOADS).toAbsolutePath().normalize();
	}

	@Override
	public Feature homePage() {
		return ModFeatures.MENUS;
	}
}
