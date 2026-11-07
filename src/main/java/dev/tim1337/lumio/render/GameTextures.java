package dev.tim1337.lumio.render;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.api.experimental.util.Logs;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;

import java.io.InputStream;
import java.util.Optional;

/**
 * Vanilla textures for the menu world, packed into one sheet of 8 by 2 tiles of 16x16 pixels in the order the {@code menu_world} shader expects.
 * They are read from the loaded resources, so they come from the game or the active resource pack. The sheet is built once; an unreadable
 * texture becomes a gray tile instead of an error.
 */
public final class GameTextures {
	private static GameTextures instance;
	private final Identifier id = Lumio.id("menu_world_textures");

	/** @return the sheet, built on first access (needs the game's texture manager, which exists whenever anything is drawn) */
	public static GameTextures get() {
		if (instance == null) {
			instance = new GameTextures();
		}
		return instance;
	}

	/** Draws one tile of the sheet as a block, scaled; tile indices follow the shader: 0 grass top, 1 grass side, 2 dirt, 3 stone, 4 sand, 5 snow, 6 log, 7 log top, 8 leaves, 15 the green edge of the grass side. */
	public void blit(GuiGraphicsExtractor graphics, int tile, int x, int y, int size, int color) {
		graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, (tile % COLUMNS) * (float) TILE, (tile / COLUMNS) * (float) TILE, size, size,
			TILE, TILE, TILE * COLUMNS, TILE * ROWS, color);
	}
	static final int TILE = 16;
	static final int COLUMNS = 8;
	static final int ROWS = 3;

	/** Tile order, matching the shader's {@code T_*} constants. */
	private static final String[] NAMES = {
		"grass_block_top", "grass_block_side", "dirt", "stone", "sand", "snow", "oak_log", "oak_log_top",
		"oak_leaves", "short_grass", "wheat_stage7", "poppy", "dandelion", "cornflower", "water_still", "grass_block_side_overlay",
		"carrots_stage3", "potatoes_stage3", "dirt_path_top", "dirt_path_side", "torch", "oak_planks"
	};

	private final DynamicTexture texture;

	private GameTextures() {
		NativeImage sheet = new NativeImage(NativeImage.Format.RGBA, TILE * COLUMNS, TILE * ROWS, true);
		for (int i = 0; i < NAMES.length; i++) {
			copyTile(sheet, i, NAMES[i]);
		}
		texture = new DynamicTexture(() -> "Lumio menu world textures", sheet);
		Minecraft.getInstance().getTextureManager().register(id, texture);
		texture.upload();
	}

	GpuTextureView view() {
		return texture.getTextureView();
	}

	private static void copyTile(NativeImage sheet, int index, String name) {
		int left = index % COLUMNS * TILE;
		int top = index / COLUMNS * TILE;
		Identifier id = Identifier.withDefaultNamespace("textures/block/" + name + ".png");
		Optional<Resource> resource = Minecraft.getInstance().getResourceManager().getResource(id);
		try (InputStream in = resource.isPresent() ? resource.get().open() : null) {
			if (in != null) {
				try (NativeImage image = NativeImage.read(in)) {
					// higher-resolution packs are sampled from their first animation frame, one pixel per step; water strips are many frames tall
					int step = Math.max(1, image.getWidth() / TILE);
					int[] pixels = image.getPixelsABGR();
					for (int y = 0; y < TILE; y++) {
						for (int x = 0; x < TILE; x++) {
							sheet.setPixelABGR(left + x, top + y, pixels[y * step * image.getWidth() + x * step]);
						}
					}
				}
				return;
			}
		} catch (Exception e) {
			Logs.warn("The texture {} could not be read for the menu world", name, e);
		}
		for (int y = 0; y < TILE; y++) {
			for (int x = 0; x < TILE; x++) {
				sheet.setPixelABGR(left + x, top + y, 0xFF808080);
			}
		}
	}
}
