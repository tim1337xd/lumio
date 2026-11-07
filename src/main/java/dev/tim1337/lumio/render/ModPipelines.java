package dev.tim1337.lumio.render;

import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.ui.title.StartupTransition;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The mod's shader pipelines. They are not registered with the game as required pipelines: the game aborts resource loading when one of those
 * fails to compile and the screen stays black. A GPU that rejects one of the mod's shaders only loses that effect, which then falls back to its
 * non-shader version or is not drawn: whatever draws with a pipeline asks {@link #usable} first, as binding one that did not compile ends the game.
 * This is a safety net, for both graphics backends; no effect relies on it.
 * <p>
 * To avoid compiling a shader on first use (a stall of a fraction of a second mid-game), all of them are compiled ahead of time. The startup and
 * main menu ones (menu world, rounded shapes, glass) compile one per frame as soon as the game has loaded the shaders, while its loading screen
 * still shows ({@link MenuWarmup}); the rest compile one per tick once startup and the menu camera have come to rest, since a compile during
 * motion caused a visible stutter. They are recompiled after every resource reload.
 */
public final class ModPipelines {
	private static final List<RenderPipeline> ALL = new ArrayList<>();
	/** Pipelines used by startup and the main menu, compiled first, all at once. */
	private static final List<RenderPipeline> STARTUP = new ArrayList<>();
	private static final Set<RenderPipeline> FAILED = new HashSet<>();
	/** Pipelines compiled since the last resource reload. */
	private static final Set<RenderPipeline> COMPILED = new HashSet<>();
	private static int next;
	/** Goes up with every reload of the shaders, for those that keep the answer of {@link #usable}. */
	private static int generation;
	/** Ticks since the loading screen closed; the remaining pipelines wait until the menu camera has descended. */
	private static int settledTicks;

	private ModPipelines() {
	}

	/** Registers a pipeline to be compiled ahead of its first use. */
	public static RenderPipeline register(RenderPipeline pipeline) {
		ALL.add(pipeline);
		return pipeline;
	}

	/** Registers a pipeline used by startup and the main menu; it is compiled as soon as loading finishes. */
	public static RenderPipeline registerForStartup(RenderPipeline pipeline) {
		STARTUP.add(pipeline);
		return register(pipeline);
	}

	/** After the game loaded its shaders again (resource pack change, F3+T) they are recompiled, since the game discarded the old ones. */
	public static void resourcesReloaded() {
		next = 0;
		settledTicks = 0;
		FAILED.clear();
		COMPILED.clear();
		generation++;
	}

	/** @return whether a pipeline can be bound: it is compiled now if that has not happened yet, and one that does not compile is never bound */
	public static boolean usable(RenderPipeline pipeline) {
		if (!shadersLoaded()) {
			return false;
		}
		compile(pipeline);
		return !FAILED.contains(pipeline);
	}

	/** @return whether a pipeline can be compiled yet: before the game has loaded its shaders, the files of the mod are out of its reach on some versions */
	public static boolean shadersLoaded() {
		//#if 26.3
		//$ return generation > 0;
		//#else
		return true;
		//#endif
	}

	/** @return whether a pipeline compiles on this system; it is compiled now, if that has not happened yet */
	public static boolean compiles(RenderPipeline pipeline) {
		//#if 26.3
		//$ return RenderSystem.getCompiledPipelineNullable(pipeline) != null;
		//#else
		return RenderSystem.getDevice().precompilePipeline(pipeline).isValid();
		//#endif
	}

	/** @return the number of the shaders' last reload; an answer of {@link #usable} kept from an earlier one is out of date */
	public static int generation() {
		return generation;
	}

	private static boolean loading;

	/** Compiles the next startup and main menu shader that is not compiled yet. @return its name, or null when all of them are */
	public static String compileNextStartup() {
		if (!shadersLoaded()) {
			return null;
		}
		for (RenderPipeline pipeline : STARTUP) {
			if (!COMPILED.contains(pipeline)) {
				compile(pipeline);
				return pipeline.getLocation().getPath();
			}
		}
		return null;
	}

	/** Called every tick: compiles one pipeline while the game is not loading and startup has come to rest. */
	public static void tick(Minecraft client) {
		if (client.gui.overlay() != null) {
			// resources are reloading: the rest waits until it is done (the shaders' reload starts it over, see MenuWarmup)
			loading = true;
			return;
		}
		if (loading) {
			loading = false;
			settledTicks = 0;
		}
		// wait until the menu camera has finished descending out of startup, plus a short delay
		if (StartupTransition.running() || ++settledTicks < 30) {
			return;
		}
		while (next < ALL.size() && COMPILED.contains(ALL.get(next))) {
			next++;
		}
		if (next >= ALL.size()) {
			return;
		}
		compile(ALL.get(next++));
	}

	private static void compile(RenderPipeline pipeline) {
		if (!shadersLoaded() || !COMPILED.add(pipeline)) {
			return;
		}
		try {
			if (!compiles(pipeline)) {
				FAILED.add(pipeline);
			}
		} catch (RuntimeException | Error e) {
			FAILED.add(pipeline);
		}
		if (FAILED.contains(pipeline)) {
			Logs.warn("The shader {} of the mod could not be built on this system: that effect is left out", pipeline.getLocation());
		}
	}
}
