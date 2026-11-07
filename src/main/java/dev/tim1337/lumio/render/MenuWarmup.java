package dev.tim1337.lumio.render;

import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.ui.title.StartupTransition;
import dev.tim1337.lumio.util.FrameTimes;

import net.minecraft.util.Util;

/**
 * Prepares everything the way into the main menu needs while the Mojang screen still shows, in small slices, one per frame: the menu's shaders
 * are compiled one by one, then the menu world's targets and buffers are made (its textures are the game's, read with the resource load). That
 * is what the transition needs. Then, a slice per frame wherever the main menu runs, its loading background is drawn
 * once and the four season pictures are made. After every resource reload it starts over, as the game discards compiled shaders.
 * <p>
 * The game has its shaders only at the very end of loading, and compiles a shader in one go: the menu world's takes a second or more. At game
 * start the compiling therefore waits until the Mojang screen's bar has reached its end, so it falls on the finished, still screen instead of
 * stopping the bar on its way ({@link dev.tim1337.lumio.ui.title.StartupTransition#barResting()}).
 */
public final class MenuWarmup {
	private enum Step {
		IDLE, COMPILE, PREPARE, SNAPSHOT, THUMBNAILS, DONE
	}

	private static final int THUMBNAILS = 4;
	/**
	 * The main menu's classes. Loading a class (and applying the mixins to it) takes a moment, and the menu and its parts are first made in the
	 * frame loading ends; they are loaded ahead on a background thread instead, without running anything of them.
	 */
	private static final String[] MENU_CLASSES = {"dev.tim1337.lumio.ui.MacTitleScreen", "dev.tim1337.lumio.ui.title.SeasonPopover",
		"dev.tim1337.lumio.ui.title.TitleListView", "dev.tim1337.lumio.ui.title.ServerListView",
		"dev.tim1337.lumio.ui.title.WorldListView", "dev.tim1337.lumio.ui.title.TitleField",
		"dev.tim1337.lumio.ui.title.ListFilter", "dev.tim1337.lumio.features.music.MenuIsland",
		"dev.tim1337.lumio.render.MenuParticles", "dev.tim1337.lumio.render.RoundedImage", "dev.tim1337.lumio.util.TimeOfDay"};
	private static boolean classesLoading;
	private static Step step = Step.IDLE;
	private static int thumbnails;

	private MenuWarmup() {
	}

	/** Loads the main menu's classes on a background thread, once, early during the game's loading. */
	public static void loadClasses() {
		if (classesLoading) {
			return;
		}
		classesLoading = true;
		Util.backgroundExecutor().execute(() -> {
			for (String name : MENU_CLASSES) {
				try {
					Class.forName(name, false, MenuWarmup.class.getClassLoader());
				} catch (ClassNotFoundException | LinkageError error) {
					Logs.warn("A class of the main menu could not be loaded ahead: {}", name, error);
				}
			}
		});
	}

	/** The game has loaded its shaders in a resource load: the slices start over from the compiling. */
	public static void shadersReady() {
		ModPipelines.resourcesReloaded();
		step = Step.COMPILE;
		thumbnails = 0;
	}

	/** @return whether what the way into the main menu needs is prepared: the shaders and the world's targets and textures */
	public static boolean ready() {
		return step.ordinal() >= Step.SNAPSHOT.ordinal();
	}

	/**
	 * Does the next slice of work, at most one per frame.
	 *
	 * @return what was done, for the frame time log, or null if nothing was
	 */
	public static String step() {
		long start = System.nanoTime();
		String done = slice();
		if (done != null && FrameTimes.enabled()) {
			Logs.info("Main menu preparation: {} took {} ms", done, (System.nanoTime() - start) / 1_000_000L);
		}
		return done;
	}

	private static String slice() {
		try {
			return switch (step) {
				case IDLE, DONE -> null;
				case COMPILE -> {
					if (!StartupTransition.barResting()) {
						yield null;
					}
					String compiled = ModPipelines.compileNextStartup();
					if (compiled == null) {
						step = Step.PREPARE;
						yield slice();
					}
					yield "compile " + compiled;
				}
				case PREPARE -> {
					MenuWorld.prepare();
					step = Step.SNAPSHOT;
					yield "prepare the menu world";
				}
				case SNAPSHOT -> {
					// the game's crop and particle textures come with the same resource load; the pictures wait for them
					if (MenuGameTextures.view() == null || MenuGameTextures.blocksView() == null) {
						yield null;
					}
					MenuWorld.snapshot();
					step = Step.THUMBNAILS;
					yield "loading background";
				}
				case THUMBNAILS -> {
					MenuWorld.prepareThumbnails();
					if (++thumbnails >= THUMBNAILS) {
						step = Step.DONE;
					}
					yield "season picture";
				}
			};
		} catch (RuntimeException error) {
			Logs.warn("The main menu could not be prepared ahead", error);
			step = Step.DONE;
			return null;
		}
	}

	/**
	 * Finishes at once what the way into the main menu needs and is not done yet: for a load so fast that the slices are not through.
	 *
	 * @return what was done, or null if nothing was left
	 */
	public static String finishNeeded() {
		String last = null;
		for (int i = 0; i < 64 && !ready() && step != Step.IDLE; i++) {
			String done = step();
			last = done != null ? done : last;
		}
		return last;
	}
}
