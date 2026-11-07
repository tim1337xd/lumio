package dev.tim1337.lumio.features.visual.world;

import org.joml.Quaternionf;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;

/** A walk effect particle: either flat, lying on a block or the ground, or camera-facing. */
public class WalkParticle extends SingleQuadParticle {
	private static int alive;

	public static int alive() {
		return alive;
	}

	private final boolean flat;
	private final float startSize;
	private final float endSize;
	private final float peakAlpha;
	private final int fadeIn;
	private final boolean glow;
	private final double riseSpeed;
	private final double driftX;
	private final double driftZ;
	private final double baseY;
	/** Maximum lift of a flat particle over its lifetime: a ripple's crest rises and settles. */
	private float lift;

	public WalkParticle lift(float height) {
		this.lift = height;
		return this;
	}

	public WalkParticle(ClientLevel level, double x, double y, double z, TextureAtlasSprite sprite, boolean flat, float startSize, float endSize,
						float alpha, int lifetime, int fadeIn, boolean glow, double riseSpeed, double driftX, double driftZ) {
		super(level, x, y, z, sprite);
		alive++;
		this.flat = flat;
		this.startSize = startSize;
		this.endSize = endSize;
		this.peakAlpha = alpha;
		this.fadeIn = Math.max(1, fadeIn);
		this.glow = glow;
		this.riseSpeed = riseSpeed;
		this.driftX = driftX;
		this.driftZ = driftZ;
		this.baseY = y;
		this.hasPhysics = false;
		this.lifetime = lifetime;
		this.quadSize = startSize;
		this.alpha = 0f;
	}

	@Override
	public void remove() {
		if (!removed) {
			alive--;
		}
		super.remove();
	}

	@Override
	public Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	@Override
	protected int getLightCoords(float partialTick) {
		return glow ? 0xF000F0 : super.getLightCoords(partialTick);
	}

	@Override
	public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
		// size and alpha are computed for the frame time rather than the tick, so they change smoothly
		float life = Mth.clamp((age + partialTick) / lifetime, 0f, 1f);
		float in = Mth.clamp((age + partialTick) / fadeIn, 0f, 1f);
		in = in * in * (3f - 2f * in);
		float out = 1f - life;
		alpha = peakAlpha * in * out * Math.max(0.0001f, (float) Math.sqrt(out));
		quadSize = Mth.lerp(1f - (1f - life) * (1f - life) * (1f - life), startSize, endSize);
		if (flat) {
			if (lift > 0f) {
				// rises with the wave and settles again, computed per frame
				double height = baseY + lift * Math.sin(Math.PI * Mth.clamp(life * 1.25f, 0f, 1f));
				y = height;
				yo = height;
			}
			// lying flat, like the rings of the Warden's shriek
			extractRotatedQuad(state, camera, new Quaternionf().rotationX(-Mth.HALF_PI), partialTick);
		} else {
			super.extract(state, camera, partialTick);
		}
	}

	@Override
	public void tick() {
		xo = x;
		yo = y;
		zo = z;
		if (age++ >= lifetime) {
			remove();
			return;
		}
		y += riseSpeed;
		x += driftX;
		z += driftZ;

	}
}
