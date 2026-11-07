package dev.tim1337.lumio.features.visual.world;

import dev.tim1337.lumio.Lumio;
import dev.tim1337.lumio.api.experimental.util.Debug;

import org.joml.Vector3fc;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.data.AtlasIds;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;

/** The ambience's tick: it simulates the wind, and hands on to {@link AmbienceField}. Also the sprites of the walk effects. */
public final class AmbienceParticles {
	/** The direction the wind blows from. */
	private static final double WIND_ANGLE = 0.6;
	private static final double WIND = 0.5;

	private static double windX;
	private static double windZ;
	private static float gust;

	private AmbienceParticles() {
	}

	/** @return the wind in blocks per tick, for a particle fully carried by it */
	public static double windX() {
		return windX;
	}

	public static double windZ() {
		return windZ;
	}

	/** @return the on-screen angle of a movement direction, for particles drawn as lines */
	public static float screenAngle(double dx, double dy, double dz) {
		Camera camera = Minecraft.getInstance().gameRenderer.mainCamera();
		Vector3fc left = camera.leftVector();
		Vector3fc up = camera.upVector();
		double toRight = -(dx * left.x() + dy * left.y() + dz * left.z());
		double toUp = dx * up.x() + dy * up.y() + dz * up.z();
		return (float) Math.atan2(toUp, toRight);
	}

	/** Called every tick; errors here are caught and do not stop it. */
	public static void tick(Minecraft client, AmbienceFeature ambience) {
		ClientLevel level = client.level;
		if (level == null || !ambience.isEnabled() && ambience.field().pool().size() == 0) {
			ambience.field().tick(client, ambience);
			return;
		}
		try {
			if (!client.isPaused()) {
				// the wind varies, and so does the gust, which is slower and visible in the fog
				long time = level.getGameTime();
				float gustWave = 0.5f + 0.5f * (float) (Math.sin(time * 0.011) * 0.6 + Math.sin(time * 0.0037 + 1.3) * 0.4);
				gust = Mth.clamp(gustWave, 0f, 1f);
				double strength = WIND * (0.55 + 0.9 * gust);
				windX = Math.cos(WIND_ANGLE) * strength * 0.05;
				windZ = Math.sin(WIND_ANGLE) * strength * 0.05;
			}
			ambience.field().tick(client, ambience);
		} catch (RuntimeException e) {
			SPRITES.clear();
			spriteAtlas = null;
			Debug.log("Ambience tick failed", e);
		}
	}

	private static final Map<String, TextureAtlasSprite> SPRITES = new HashMap<>();
	private static Object spriteAtlas;

	/** @return a mod sprite, cached: a lookup builds an identifier and searches the atlas, and particles spawn by the hundreds */
	static TextureAtlasSprite sprite(String name) {
		var atlas = Minecraft.getInstance().getAtlasManager().getAtlasOrThrow(AtlasIds.PARTICLES);
		if (atlas != spriteAtlas) {
			// the atlas is rebuilt on resource reload, invalidating the cached sprites
			SPRITES.clear();
			spriteAtlas = atlas;
		}
		TextureAtlasSprite found = SPRITES.get(name);
		if (found == null) {
			found = atlas.getSprite(Lumio.id(name));
			if (found == atlas.missingSprite()) {
				found = atlas.getSprite(Lumio.id("particle/" + name));
			}
			SPRITES.put(name, found);
		}
		return found;
	}
}
