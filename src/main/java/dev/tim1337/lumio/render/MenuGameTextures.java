package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.api.experimental.util.Logs;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.fabricmc.fabric.api.resource.v1.ResourceLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.InputStream;

/**
 * The game's own textures that the main menu's world is made of, so it looks like the game: the blocks (grass, stone, planks, logs, the path, snow,
 * water and the rest), the crops by growth stage and the particles (cherry leaves, leaves, fireflies). They are read from the game's resources
 * (so resource packs change them too) while the game loads, on a loading thread, and put together into two sheets of square tiles, 8 across and
 * 6 down, which the {@code menu_world} shader samples: one of the blocks in the order of its {@code T_*} numbers, one of the crops and the
 * particles. Nothing of them is shipped with the mod.
 */
public final class MenuGameTextures extends SimplePreparableReloadListener<NativeImage[]> {
	// where each texture is on the crop and particle sheet; the shader uses the same numbers
	public static final int WHEAT = 0;
	public static final int CARROTS = 8;
	public static final int POTATOES = 12;
	public static final int CHERRY = 16;
	public static final int LEAF = 28;
	public static final int FIREFLY = 40;
	/** How many textures a cherry leaf or a leaf particle can have, one picked per particle. */
	public static final int LEAF_SPRITES = 12;
	private static final int COLUMNS = 8;
	private static final int ROWS = 6;
	/** The size of a tile: the game's 16 pixels, or more for a resource pack with larger textures, up to this. */
	private static final int MAX_TILE = 64;
	private static final int MIN_TILE = 16;
	/**
	 * The block sheet, in the order of the shader's {@code T_*} numbers. The bare stalks of winter have no texture in the game: its short dry
	 * grass stands in for them.
	 */
	private static final String[] BLOCKS = {"grass_block_top", "grass_block_side", "dirt", "stone", "farmland", "snow", "oak_log", "oak_log_top",
		"oak_leaves", "short_grass", "wheat_stage7", "poppy", "dandelion", "cornflower", "water_still", "grass_block_side_overlay", "carrots_stage3",
		"potatoes_stage3", "dirt_path_top", "dirt_path_side", "lantern", "oak_planks", "stone_bricks", "mossy_stone_bricks", "cracked_stone_bricks",
		"cobblestone", "mossy_cobblestone", "hay_block_side", "hay_block_top", "vine", "iron_chain",
		"wheat_stage2", "potatoes_stage3", "short_dry_grass", "ice", "grass_block_snow", "allium", "carrots_stage1", "spruce_planks"};

	private static final Identifier ID = Lumio.id("menu_world_game");
	private static final Identifier BLOCKS_ID = Lumio.id("menu_world_blocks");
	private static DynamicTexture texture;
	private static DynamicTexture blocks;
	private static boolean warned;

	private MenuGameTextures() {
	}

	/** Registers the sheets to be made on every resource load, starting with the one while the game starts. */
	public static void register() {
		ResourceLoader.get(PackType.CLIENT_RESOURCES).registerReloadListener(ID, new MenuGameTextures());
	}

	/** @return the crop and particle sheet, or null while it has not been made (before the first load finished) */
	public static GpuTextureView view() {
		return texture == null ? null : texture.getTextureView();
	}

	/** @return the block sheet, or null while it has not been made (before the first load finished) */
	public static GpuTextureView blocksView() {
		return blocks == null ? null : blocks.getTextureView();
	}

	private static Identifier[] cropsAndParticles() {
		Identifier[] all = new Identifier[FIREFLY + 1];
		for (int stage = 0; stage < 8; stage++) {
			all[WHEAT + stage] = Identifier.withDefaultNamespace("textures/block/wheat_stage" + stage + ".png");
		}
		for (int stage = 0; stage < 4; stage++) {
			all[CARROTS + stage] = Identifier.withDefaultNamespace("textures/block/carrots_stage" + stage + ".png");
			all[POTATOES + stage] = Identifier.withDefaultNamespace("textures/block/potatoes_stage" + stage + ".png");
		}
		for (int i = 0; i < LEAF_SPRITES; i++) {
			all[CHERRY + i] = Identifier.withDefaultNamespace("textures/particle/cherry_" + i + ".png");
			all[LEAF + i] = Identifier.withDefaultNamespace("textures/particle/leaf_" + i + ".png");
		}
		all[FIREFLY] = Identifier.withDefaultNamespace("textures/particle/firefly.png");
		return all;
	}

	private static Identifier[] blocks() {
		Identifier[] all = new Identifier[BLOCKS.length];
		for (int i = 0; i < BLOCKS.length; i++) {
			all[i] = Identifier.withDefaultNamespace("textures/block/" + BLOCKS[i] + ".png");
		}
		return all;
	}

	/** Reads the textures and puts them on the two sheets, on a loading thread. A missing one stays empty. */
	@Override
	protected NativeImage[] prepare(ResourceManager resources, ProfilerFiller profiler) {
		// the particles are smaller than the blocks: only the crops decide the size of that sheet's tiles
		return new NativeImage[]{sheet(resources, cropsAndParticles(), CHERRY), sheet(resources, blocks(), BLOCKS.length)};
	}

	/** @param sizing how many of the textures, from the first, decide the size of the tiles */
	private static NativeImage sheet(ResourceManager resources, Identifier[] sources, int sizing) {
		NativeImage[] read = new NativeImage[sources.length];
		int tile = MIN_TILE;
		for (int i = 0; i < sources.length; i++) {
			read[i] = read(resources, sources[i]);
			if (read[i] != null && i < sizing) {
				tile = Math.max(tile, Math.min(MAX_TILE, read[i].getWidth()));
			}
		}
		NativeImage sheet = new NativeImage(NativeImage.Format.RGBA, COLUMNS * tile, ROWS * tile, true);
		for (int i = 0; i < read.length; i++) {
			NativeImage image = read[i];
			if (image == null) {
				continue;
			}
			// the first square of the picture (an animated texture has its frames below each other), scaled to the tile without blending
			int size = Math.min(image.getWidth(), image.getHeight());
			int left = (i % COLUMNS) * tile;
			int top = (i / COLUMNS) * tile;
			for (int py = 0; py < tile; py++) {
				for (int px = 0; px < tile; px++) {
					sheet.setPixel(left + px, top + py, image.getPixel(px * size / tile, py * size / tile));
				}
			}
			image.close();
		}
		return sheet;
	}

	private static NativeImage read(ResourceManager resources, Identifier id) {
		var resource = resources.getResource(id);
		if (resource.isEmpty()) {
			warnOnce(id, null);
			return null;
		}
		try (InputStream in = resource.get().open()) {
			return NativeImage.read(in);
		} catch (Exception error) {
			warnOnce(id, error);
			return null;
		}
	}

	private static void warnOnce(Identifier id, Exception error) {
		if (!warned) {
			warned = true;
			Logs.warn("A texture of the game for the main menu's world is missing ({}); it is left empty", id, error);
		}
	}

	/** Uploads the sheets, on the render thread, replacing the ones of the last load. */
	@Override
	protected void apply(NativeImage[] sheets, ResourceManager resources, ProfilerFiller profiler) {
		// new textures each time, as a resource pack can change the size of the tiles; registering them releases the last ones
		texture = new DynamicTexture(() -> "Lumio menu world crops and particles", sheets[0]);
		Minecraft.getInstance().getTextureManager().register(ID, texture);
		blocks = new DynamicTexture(() -> "Lumio menu world blocks", sheets[1]);
		Minecraft.getInstance().getTextureManager().register(BLOCKS_ID, blocks);
	}
}
