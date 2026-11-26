package dev.tim1337.lumio.features.waypoints;

import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.api.experimental.util.ModFiles;
import dev.tim1337.lumio.api.experimental.util.Threads;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Where the groups are kept: those that show everywhere in one file, the others in a file per scope (a server, a singleplayer world, or a profile of
 * an add-on's world such as a SkyBlock profile), all in {@code config/lumio/waypoints/}. Files are read and written on a thread of their own,
 * written through a temporary file a moment after the last change, and at once when the scope changes or the game closes, so nothing is lost on a
 * crash, a server switch or a restart.
 */
public final class WaypointStore {
	static final String FOLDER = "waypoints";
	static final String GLOBAL = "global";
	/** How long after the last change the files are written, so a drag or a recording writes once. */
	static final long SAVE_DELAY_MILLIS = 2000L;
	private static final int MAX_SCOPE = 80;
	private static final Pattern NOT_IN_NAME = Pattern.compile("[^a-z0-9._-]");
	private static final ExecutorService FILES = Threads.daemonExecutor("Lumio waypoints");

	/** The folder, asked for only when a file is read or written: the config folder is not known yet while the features are made. */
	private final Supplier<Path> folder;
	private final List<WaypointGroup> global = new ArrayList<>();
	private final List<WaypointGroup> local = new ArrayList<>();
	/** Everything shown: the global groups, then the scope's; rebuilt when one of the two changes. */
	private final List<WaypointGroup> all = new ArrayList<>();
	private final List<WaypointGroup> allView = Collections.unmodifiableList(all);
	private String scope;
	private boolean globalLoaded;
	private boolean localLoaded;
	private long dirtySince;
	private boolean globalDirty;
	private boolean localDirty;
	/** Counts the scope changes, so a file read for an earlier scope is not taken in after the player moved on. */
	private int generation;
	private int changes;

	public WaypointStore(Supplier<Path> folder) {
		this.folder = folder;
	}

	/** @return the store in the mod's config folder */
	public static WaypointStore inConfig() {
		return new WaypointStore(() -> ModFiles.file(FOLDER));
	}

	/** @return a scope's file name: lower case, only letters, digits, dots, dashes and underscores, never empty */
	public static String scopeName(String scope) {
		String name = NOT_IN_NAME.matcher(scope.toLowerCase(Locale.ROOT)).replaceAll("_");
		if (name.length() > MAX_SCOPE) {
			name = name.substring(0, MAX_SCOPE);
		}
		return name.isEmpty() || name.equals(GLOBAL) || name.startsWith(".") ? "_" + name : name;
	}

	/** @return every group: those everywhere first, then the scope's; empty until they are read */
	public List<WaypointGroup> groups() {
		return allView;
	}

	/** @return a number that changes with every change to the groups, for views that cache what they show */
	public int changes() {
		return changes;
	}

	/** @return the current scope, or null outside a world */
	public String scope() {
		return scope;
	}

	/** @return whether the current scope's groups have been read */
	public boolean ready() {
		return globalLoaded && (scope == null || localLoaded);
	}

	/** Reads the global groups, once, in the background; the callback runs on the thread that takes the result in. */
	public void loadGlobal(Consumer<Runnable> mainThread) {
		FILES.execute(() -> {
			List<WaypointGroup> read = readFile(fileOf(GLOBAL));
			mainThread.accept(() -> {
				global.addAll(0, read);
				globalLoaded = true;
				rebuild();
			});
		});
	}

	/**
	 * Moves to another scope: the groups of the one before are written now, and the new one's are read in the background.
	 *
	 * @param scope the new scope, or null outside a world
	 */
	public void enter(String scope, Consumer<Runnable> mainThread) {
		String name = scope == null ? null : scopeName(scope);
		if (Objects.equals(name, this.scope)) {
			return;
		}
		saveNow();
		this.scope = name;
		local.clear();
		localLoaded = name == null;
		int mine = ++generation;
		rebuild();
		if (name == null) {
			return;
		}
		FILES.execute(() -> {
			List<WaypointGroup> read = readFile(fileOf(name));
			mainThread.accept(() -> {
				if (mine != generation) {
					return;
				}
				local.addAll(0, read);
				localLoaded = true;
				rebuild();
			});
		});
	}

	/** Adds a group: to those everywhere when it shows everywhere and no add-on's world is in play, otherwise to the scope's. */
	public void add(WaypointGroup group, boolean everywhere) {
		(everywhere || scope == null ? global : local).add(group);
		changed(group);
		rebuild();
	}

	public void remove(WaypointGroup group) {
		boolean wasGlobal = global.remove(group);
		boolean wasLocal = local.remove(group);
		if (wasGlobal || wasLocal) {
			globalDirty |= wasGlobal;
			localDirty |= wasLocal;
			touch();
			rebuild();
		}
	}

	/** Moves a group between those everywhere and the scope's, after its areas changed. */
	public void place(WaypointGroup group, boolean everywhere) {
		boolean isGlobal = global.contains(group);
		if (isGlobal == everywhere || (!everywhere && scope == null)) {
			return;
		}
		(isGlobal ? global : local).remove(group);
		(everywhere ? global : local).add(group);
		globalDirty = true;
		localDirty = true;
		touch();
		rebuild();
	}

	/** @return whether a group is kept with those everywhere */
	public boolean isGlobal(WaypointGroup group) {
		return global.contains(group);
	}

	/** Marks a group as changed: its file is written a moment after the last change. */
	public void changed(WaypointGroup group) {
		if (global.contains(group)) {
			globalDirty = true;
		} else {
			localDirty = true;
		}
		touch();
	}

	/** @return an id for a new group, unique among those known */
	public String newId() {
		while (true) {
			String id = Long.toString(System.nanoTime() & 0xFFFFFFFFFFL, Character.MAX_RADIX);
			if (find(id) == null) {
				return id;
			}
		}
	}

	public WaypointGroup find(String id) {
		for (WaypointGroup group : all) {
			if (group.id().equals(id)) {
				return group;
			}
		}
		return null;
	}

	/** Writes the changed files once the changes have settled; called every tick. */
	public void tick(long now) {
		if (dirtySince != 0 && now - dirtySince >= SAVE_DELAY_MILLIS) {
			saveNow();
		}
	}

	/** Writes what changed now, in the background: before a scope change and when the game closes. */
	public void saveNow() {
		dirtySince = 0;
		if (globalDirty && globalLoaded) {
			write(GLOBAL, global);
		}
		if (localDirty && localLoaded && scope != null) {
			write(scope, local);
		}
		globalDirty = false;
		localDirty = false;
	}

	/** Waits until every write so far is done; for the game closing and for tests. */
	public void flush() {
		try {
			FILES.submit(() -> {
			}).get();
		} catch (Exception e) {
			Thread.currentThread().interrupt();
		}
	}

	private void touch() {
		changes++;
		if (dirtySince == 0) {
			dirtySince = System.currentTimeMillis();
		}
	}

	private void rebuild() {
		all.clear();
		all.addAll(global);
		all.addAll(local);
		changes++;
	}

	private Path fileOf(String name) {
		return folder.get().resolve(name + ".json");
	}

	/** Writes a file on the file thread, where its path is also worked out (that makes the folder when it is missing). */
	private void write(String name, List<WaypointGroup> groups) {
		// made into text here, while nothing changes the groups; only the writing waits for the disk
		String text = new Gson().toJson(WaypointJson.write(groups));
		FILES.execute(() -> {
			Path file = fileOf(name);
			try {
				ModFiles.writeAtomically(file, text);
			} catch (IOException e) {
				Logs.warn("Could not keep the waypoints in {}", file.getFileName(), e);
			}
		});
	}

	private static List<WaypointGroup> readFile(Path file) {
		if (!Files.isRegularFile(file)) {
			return List.of();
		}
		try {
			JsonObject root = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
			List<String> seen = new ArrayList<>();
			return WaypointJson.read(root, saved -> {
				String id = saved == null || seen.contains(saved) ? "g" + seen.size() : saved;
				seen.add(id);
				return id;
			});
		} catch (IOException | RuntimeException e) {
			Logs.warn("Could not read the waypoints in {}", file.getFileName(), e);
			return List.of();
		}
	}
}
