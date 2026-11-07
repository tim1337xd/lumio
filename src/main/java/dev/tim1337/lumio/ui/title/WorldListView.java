package dev.tim1337.lumio.ui.title;

import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.render.MenuWorld;
import dev.tim1337.lumio.render.RoundedImage;
import dev.tim1337.lumio.ui.Confirm;
import dev.tim1337.lumio.ui.MacTitleScreen;
import dev.tim1337.lumio.ui.Ui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.AlertScreen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.FaviconTexture;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.level.storage.LevelSummary;

import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The world list in the main menu: the saved worlds, read in the background with placeholder rows meanwhile, sorted by
 * when they were last played. A field filters them by name; New world opens the game's world creation. Rows show the world's icon (or a crop
 * of the season's picture), name, when it was played, its game mode and version; a "⋯" menu edits, backs up, re-creates, opens the folder of,
 * or deletes a world. Opening goes through the game's own flow, so its safety prompts (backup of an old world, experimental features) stay.
 */
public final class WorldListView extends TitleListView {
	private static final int PLACEHOLDER_ROWS = 4;
	private static final int OLD_VERSION_TEXT = 0xFFFFD479;
	private static final int HARDCORE_TEXT = 0xFFFF9F94;
	private static final float ICON_WIDTH = 60f;
	private static final float ICON_HEIGHT = 42f;
	private static final float PLAY_WIDTH = 56f;

	private final Screen title;
	private final Minecraft minecraft = Minecraft.getInstance();
	private final ExecutorService files = Executors.newSingleThreadExecutor(task -> {
		Thread thread = new Thread(task, "Lumio world list");
		thread.setDaemon(true);
		return thread;
	});
	private volatile List<LevelSummary> loaded;
	private final List<World> worlds = new ArrayList<>();
	private boolean loading;

	/** A world with its texts, made once, and its icon. */
	private static final class World {
		final LevelSummary summary;
		final String name;
		final String line;
		final boolean older;
		final boolean hardcore;
		FaviconTexture icon;
		volatile NativeImage decoded;
		boolean iconReady;

		World(LevelSummary summary, long now) {
			this.summary = summary;
			this.name = summary.getLevelName();
			this.hardcore = summary.isHardcore();
			this.older = summary.shouldBackup();
			String mode = hardcore ? "Hardcore" : capitalized(summary.getGameMode().getName());
			this.line = "Played " + ListFilter.ago(summary.getLastPlayed(), now) + "  ·  " + mode + "  ·  " + summary.getWorldVersionName().getString();
		}

		private static String capitalized(String text) {
			return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
		}
	}

	public WorldListView(Screen title) {
		super("Search your worlds");
		this.title = title;
	}

	@Override
	public void open() {
		load();
		super.open();
	}

	@Override
	public void dispose() {
		for (World world : worlds) {
			if (world.icon != null) {
				world.icon.close();
			}
		}
		worlds.clear();
		refilter();
	}

	/** Reads the worlds in the background, so the menu never waits for many of them. */
	private void load() {
		loading = true;
		loaded = null;
		var source = minecraft.getLevelSource();
		CompletableFuture.supplyAsync(() -> {
			try {
				return source.findLevelCandidates();
			} catch (Exception error) {
				Logs.warn("The worlds could not be listed", error);
				return null;
			}
		}, files).thenCompose(candidates -> candidates == null ? CompletableFuture.completedFuture(List.<LevelSummary>of())
			: source.loadLevelSummaries(candidates)).whenComplete((summaries, error) -> loaded = summaries == null ? List.of() : summaries);
	}

	@Override
	public void tick() {
		List<LevelSummary> arrived = loaded;
		if (arrived == null) {
			return;
		}
		loaded = null;
		loading = false;
		for (World world : worlds) {
			if (world.icon != null) {
				world.icon.close();
			}
		}
		worlds.clear();
		long now = System.currentTimeMillis();
		List<Long> played = new ArrayList<>(arrived.size());
		for (LevelSummary summary : arrived) {
			played.add(summary.getLastPlayed());
		}
		for (int index : ListFilter.byLastPlayed(played)) {
			World world = new World(arrived.get(index), now);
			worlds.add(world);
			readIcon(world);
		}
		refilter();
	}

	/** Reads a world's icon file in the background; it is uploaded on the render thread when drawn. */
	private void readIcon(World world) {
		var path = world.summary.getIcon();
		files.submit(() -> {
			try {
				if (path != null && Files.isRegularFile(path)) {
					try (var in = Files.newInputStream(path)) {
						world.decoded = NativeImage.read(in);
					}
				}
			} catch (Exception error) {
				world.decoded = null;
			}
		});
	}

	@Override
	int loadingRows() {
		return loading ? PLACEHOLDER_ROWS : 0;
	}

	@Override
	void refilter() {
		List<String> names = new ArrayList<>(worlds.size());
		for (World world : worlds) {
			names.add(world.name);
		}
		visible = ListFilter.matching(names, null, field.text());
		if (selected >= visible.size()) {
			selected = visible.isEmpty() ? -1 : 0;
		}
	}

	@Override
	String overline() {
		return "SINGLEPLAYER";
	}

	@Override
	String heading() {
		return "Worlds";
	}

	@Override
	String subtitle() {
		return loading ? "Reading your worlds…" : worlds.size() == 1 ? "1 world" : worlds.size() + " worlds";
	}

	@Override
	String primaryLabel() {
		return "New world";
	}

	@Override
	void primary() {
		CreateWorldScreen.openFresh(minecraft, () -> minecraft.gui.setScreen(title));
	}

	@Override
	String sectionLabel() {
		return "RECENT";
	}

	@Override
	String sectionNote() {
		return "Sorted by last played";
	}

	@Override
	void activate(int index) {
		LevelSummary summary = worlds.get(index).summary;
		if (!summary.primaryActionActive()) {
			return;
		}
		MacTitleScreen.forgetLastWorld();
		minecraft.createWorldOpenFlows().openWorld(summary.getLevelId(), () -> minecraft.gui.setScreen(title));
	}

	@Override
	List<String> menu(int index) {
		return List.of("Edit", "Back up", "Re-create", "Open folder", "Delete…");
	}

	@Override
	void menuAction(int index, int item) {
		LevelSummary summary = worlds.get(index).summary;
		String id = summary.getLevelId();
		var source = minecraft.getLevelSource();
		try {
			switch (item) {
				case 0 -> {
					var access = source.validateAndCreateAccess(id);
					minecraft.gui.setScreen(EditWorldScreen.create(minecraft, access, changed -> {
						closeQuietly(access);
						if (changed) {
							load();
						}
						minecraft.gui.setScreen(title);
					}));
				}
				case 1 -> {
					var access = source.validateAndCreateAccess(id);
					EditWorldScreen.makeBackupAndShowToast(access).whenComplete((done, error) -> closeQuietly(access));
				}
				case 2 -> recreate(id);
				case 3 -> Util.getPlatform().openPath(source.getLevelPath(id));
				default -> Confirm.ask("Delete " + summary.getLevelName() + "?", "This world is deleted for good. This can not be undone.", "Delete", "Cancel",
					() -> delete(id));
			}
		} catch (Exception error) {
			Logs.warn("The world {} could not be opened for this", id, error);
			minecraft.gui.setScreen(new AlertScreen(() -> minecraft.gui.setScreen(title), Component.translatable("selectWorld.access_failure"),
				Component.literal(String.valueOf(error.getMessage()))));
		}
	}

	/** Re-creates a world with its settings, as the game's world list does. */
	private void recreate(String id) {
		try (var access = minecraft.getLevelSource().validateAndCreateAccess(id)) {
			var pair = minecraft.createWorldOpenFlows().recreateWorldData(access);
			var settings = pair.getFirst();
			var context = pair.getSecond();
			var packs = CreateWorldScreen.createTempDataPackDirFromExistingWorld(access.getLevelPath(LevelResource.DATAPACK_DIR), minecraft);
			context.validate();
			if (context.options().isOldCustomizedWorld()) {
				minecraft.gui.setScreen(new ConfirmScreen(proceed -> minecraft.gui.setScreen(proceed
					? CreateWorldScreen.createFromExisting(minecraft, () -> minecraft.gui.setScreen(title), settings, context, packs) : title),
					Component.translatable("selectWorld.recreate.customized.title"), Component.translatable("selectWorld.recreate.customized.text"),
					CommonComponents.GUI_PROCEED, CommonComponents.GUI_CANCEL));
			} else {
				minecraft.gui.setScreen(CreateWorldScreen.createFromExisting(minecraft, () -> minecraft.gui.setScreen(title), settings, context, packs));
			}
		} catch (Exception error) {
			Logs.warn("The world {} could not be re-created", id, error);
			minecraft.gui.setScreen(new AlertScreen(() -> minecraft.gui.setScreen(title), Component.translatable("selectWorld.recreate.error.title"),
				Component.translatable("selectWorld.recreate.error.text")));
		}
	}

	/** Deletes a world in the background, then reads the list again. */
	private void delete(String id) {
		var source = minecraft.getLevelSource();
		files.submit(() -> {
			try (var access = source.createAccess(id)) {
				access.deleteLevel();
			} catch (Exception error) {
				Logs.warn("The world {} could not be deleted", id, error);
			}
			minecraft.execute(this::load);
		});
		selected = -1;
	}

	private static void closeQuietly(AutoCloseable access) {
		try {
			access.close();
		} catch (Exception ignored) {
			// already closed or gone: nothing left to release
		}
	}

	@Override
	boolean clickInRow(int index, double mouseX, double mouseY, float x, float y, float width) {
		float playX = x + width - PLAY_WIDTH;
		if (mouseX >= playX && mouseX < x + width && mouseY >= y + (ROW - 28f) / 2f && mouseY < y + (ROW + 28f) / 2f) {
			dev.tim1337.lumio.ui.UiSounds.click();
			activate(index);
			return true;
		}
		return false;
	}

	@Override
	void drawRow(Ui ui, int index, float x, float y, float width, boolean hot, float alpha, int mouseX, int mouseY) {
		if (index >= worlds.size()) {
			return;
		}
		World world = worlds.get(index);
		float iconX = x + 10f;
		float iconY = y + (ROW - ICON_HEIGHT) / 2f;
		if (!world.iconReady && world.decoded != null) {
			world.icon = FaviconTexture.forWorld(minecraft.getTextureManager(), world.summary.getLevelId());
			world.icon.upload(world.decoded);
			world.decoded = null;
			world.iconReady = true;
		}
		if (world.iconReady) {
			roundedTexture(ui, world.icon.textureLocation(), iconX, iconY, ICON_WIDTH, ICON_HEIGHT, 9f, Ui.withOpacity(0xFFFFFFFF, alpha));
		} else {
			// no icon: a crop of a season's picture, different for every world
			int season = Math.floorMod(world.name.hashCode(), 4);
			var picture = MenuWorld.thumbnail(season);
			if (picture != null) {
				float u = 0.25f + 0.3f * (Math.floorMod(world.name.hashCode() >> 3, 100) / 100f);
				RoundedImage.draw(ui.graphics, picture, Math.round(iconX), Math.round(iconY), Math.round(ICON_WIDTH), Math.round(ICON_HEIGHT), 9f, u, 0.75f,
					u + 0.3f, 0.35f, Ui.withOpacity(0xFFFFFFFF, alpha));
			} else {
				ui.roundedSmooth(iconX, iconY, ICON_WIDTH, ICON_HEIGHT, 9f, Ui.withOpacity(0xFF3A3F55, alpha));
			}
		}
		ui.ring(Math.round(iconX), Math.round(iconY), Math.round(ICON_WIDTH), Math.round(ICON_HEIGHT), 9f, 1, Ui.withOpacity(0xFFFFFFFF, 0.16f * alpha));
		float textX = iconX + ICON_WIDTH + 13f;
		float room = x + width - textX - (hot ? PLAY_WIDTH + 12f : 14f);
		ui.textAtScaledBold(ui.fitScaled(world.name, room, 16f * TEXT), textX, y + 15f, 16f * TEXT, Ui.withOpacity(0xFFFFFFFF, alpha));
		float lineScale = 12.5f * TEXT;
		String line = ui.fitScaled(world.line, room - (world.older ? 90f : 0f), lineScale);
		ui.textAtScaled(line, textX, y + 38f, lineScale, Ui.withOpacity(world.hardcore ? HARDCORE_TEXT : 0xFFFFFFFF, (world.hardcore ? 0.9f : 0.62f) * alpha));
		if (world.older) {
			ui.textAtScaled("  ·  older version", textX + ui.widthScaled(line, lineScale), y + 38f, lineScale, Ui.withOpacity(OLD_VERSION_TEXT, alpha));
		}
		if (hot && world.summary.primaryActionActive()) {
			float playX = x + width - PLAY_WIDTH;
			ui.roundedSmooth(playX, y + (ROW - 28f) / 2f, PLAY_WIDTH, 28f, 14f, Ui.withOpacity(ui.accent, alpha));
			float scale = 13f * TEXT;
			ui.textAtScaledBold("Play", playX + PLAY_WIDTH / 2f - ui.widthScaled("Play", scale) / 2f, y + ROW / 2f - 4.5f * scale, scale,
				Ui.withOpacity(0xFFFFFFFF, alpha));
		}
	}
}
