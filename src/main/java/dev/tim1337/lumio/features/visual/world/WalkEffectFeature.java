package dev.tim1337.lumio.features.visual.world;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Light rising from the ground as the player walks. With every step a wave spreads from the feet, lighting up the block tops as it passes over
 * them; alternatively rings spread over the ground, or sparks rise from it.
 */
public class WalkEffectFeature extends Feature {
	public enum Style {
		BLOCK_PULSE("Block pulse"),
		RIPPLES("Ripples"),
		SPARKS("Sparks");

		private final Component displayName;

		Style(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** What triggers the effect. */
	public enum Trigger {
		STEPS("Steps"),
		LANDING("Landing"),
		BOTH("Both");

		private final Component displayName;

		Trigger(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	/** A block the wave lights up, and when: further blocks light up later, each once. */
	private record Tile(int delay, int x, int z, double top, float strength) {
	}

	/** A wave spreading from a step over the blocks, precomputed at once so it is deterministic. */
	private static final class Wave {
		final List<Tile> tiles = new ArrayList<>();
		final float power;
		int age;
		int last;

		Wave(float power) {
			this.power = power;
		}
	}
	private static final float BLOCKS_PER_TICK = 0.7f;

	private final EnumSetting<Style> style = add(new EnumSetting<>("style", Component.literal("Effect"), Style.BLOCK_PULSE, Style::displayName, EnumSetting.Style.DROPDOWN), "General");
	private final ColorSetting color = add(new ColorSetting("color", Component.literal("Color"), SystemColors.TEAL_RGB).followsMenu().withOpacity(1f), "General");
	private final FloatSetting reach = add(new FloatSetting("reach", Component.literal("Reach"), 5f, 2f, 9f, 1), "General");
	private final FloatSetting intensity = add(new FloatSetting("intensity", Component.literal("Strength"), 0.7f, 0.1f, 1f, 2), "General");
	private final EnumSetting<Trigger> trigger = add(new EnumSetting<>("trigger", Component.literal("Shows on"), Trigger.BOTH, Trigger::displayName, EnumSetting.Style.SEGMENTED), "General");
	private final BooleanSetting sprintOnly = add(new BooleanSetting("sprint_only", Component.literal("Sprint only"), false), "General");
	private final BooleanSetting glow = add(new BooleanSetting("glow", Component.literal("Glow in the dark"), true), "General", "As bright at night as by day");

	private final List<Wave> waves = new ArrayList<>();
	private double lastX;
	private double lastZ;
	private boolean hasLast;
	private double stepped;
	private boolean wasOnGround = true;
	private double lastVerticalSpeed;
	private int sinceStep = 99;
	private int airTicks;
	private double fastestFall;
	/** The highest point while airborne, to measure the fall height. */
	private double airTop;

	public WalkEffectFeature() {
		super("walk_effects", "Walk effects", "Light that comes up from the ground as you walk.", false);
		iconRgb(SystemColors.INDIGO_RGB);
		iconName("footsteps");
		summary("Light that comes up from the ground as you walk");
		addResetButton();
	}

	/** Called every game tick. */
	public void tick(Minecraft client) {
		ClientLevel level = client.level;
		LocalPlayer player = client.player;
		if (level == null || player == null || client.isPaused() || !isEnabled()) {
			waves.clear();
			hasLast = false;
			return;
		}
		double moved = hasLast ? Math.hypot(player.getX() - lastX, player.getZ() - lastZ) : 0;
		lastX = player.getX();
		lastZ = player.getZ();
		hasLast = true;
		sinceStep++;

		boolean onGround = player.onGround();
		Trigger when = trigger.get();
		// a landing: the player was airborne and is now on the ground, after a fall of more than a step
		if (!onGround) {
			if (airTicks == 0) {
				airTop = player.getY();
			}
			airTicks++;
			airTop = Math.max(airTop, player.getY());
			fastestFall = Math.min(fastestFall, Math.min(lastVerticalSpeed, player.getDeltaMovement().y));
		}
		boolean landed = onGround && !wasOnGround;
		// a landing is a fall that lasted a few ticks while moving downwards, which stepping down a stair is not
		if (landed && airTicks >= 3 && fastestFall < -0.14 && !player.isInWater() && !player.getAbilities().flying && when != Trigger.STEPS) {
			// stronger the higher the fall in blocks, up to about 26 blocks: speed alone maxes out after a few blocks
				float power = Mth.clamp(0.9f + (float) (airTop - player.getY()) * 0.08f, 0.9f, 3f);
			effect(client, level, player, power);
			stepped = 0;
		}
		if (landed) {
			airTicks = 0;
			fastestFall = 0;
		}
		wasOnGround = onGround;
		lastVerticalSpeed = player.getDeltaMovement().y;

		boolean walking = onGround && !player.isInWater() && moved > 0.015 && (!sprintOnly.get() || player.isSprinting());
		if (walking && when != Trigger.LANDING) {
			stepped += moved;
			double stride = (player.isSprinting() ? 1.35 : 0.95) * (style.get() == Style.RIPPLES ? 1.9 : 1.0);
			if (stepped >= stride && sinceStep >= 3) {
				stepped = 0;
				sinceStep = 0;
				effect(client, level, player, 1f);
			}
		} else if (!walking) {
			stepped = Math.min(stepped, 0.5);
		}
		// advance the waves over the blocks: each tile lights up when the wave reaches it
		Iterator<Wave> iterator = waves.iterator();
		while (iterator.hasNext()) {
			Wave wave = iterator.next();
			wave.age++;
			for (Tile tile : wave.tiles) {
				if (tile.delay() == wave.age) {
					light(level, tile, wave.power);
				}
			}
			if (wave.age > wave.last) {
				iterator.remove();
			}
		}
	}
	private int rgb() {
		return color.get();
	}

	/** @return the light intensity, given by the color's opacity */
	private float opacity() {
		return color.opacity();
	}

	private static final int MAX_PARTICLES = 2500;

	private void effect(Minecraft client, ClientLevel level, LocalPlayer player, float power) {
		if (WalkParticle.alive() > MAX_PARTICLES) {
			return;
		}
		RandomSource random = level.getRandom();
		switch (style.get()) {
			case BLOCK_PULSE -> waves.add(makeWave(level, player, power));
			case RIPPLES -> {
				// a step makes one ripple, a landing two; every ripple is a few stacked rings forming its crest
					boolean landing = power != 1f;
					int ripples = power >= 2f ? 3 : landing ? 2 : 1;
					float calm = landing ? 1f : 0.7f;
					for (int i = 0; i < ripples; i++) {
						float end = reach.get() * power * (0.6f - 0.2f * i);
						for (int layer = 0; layer < 4; layer++) {
							float step = layer / 3f;
							WalkParticle ring = new WalkParticle(level, player.getX(), player.getY() + 0.02 + layer * 0.002, player.getZ(), AmbienceParticles.sprite("ring"), true,
								0.3f, end * (1f - 0.07f * layer), 0.9f * intensity.get() * opacity() * calm * (1f - 0.22f * step), 24 + i * 8, 4, glow.get(), 0, 0, 0)
								.lift((0.05f + 0.09f * layer) * Math.min(power, 1.6f));
							tint(ring, 0.25f * step);
							client.particleEngine.add(ring);
						}
					}
				}
			case SPARKS -> {
				// lights rising from the ground around the player, drifting up past the legs and fading out
				int count = Math.round((14 + random.nextInt(6)) * power);
				for (int i = 0; i < count; i++) {
					// around the player and mostly in front, where they are visible: those right at the feet are below the camera
					double facing = Math.toRadians(player.getYRot());
					double angle = Math.PI / 2 + facing + (random.nextDouble() - 0.5) * Math.toRadians(230);
					double distance = 0.7 + random.nextDouble() * 1.7 * Math.min(power, 1.5);
					float size = 0.13f + random.nextFloat() * 0.1f;
					WalkParticle spark = new WalkParticle(level, player.getX() + Math.cos(angle) * distance, player.getY() + 0.05 + random.nextDouble() * 0.35, player.getZ() + Math.sin(angle) * distance,
						AmbienceParticles.sprite("spark"), false, size, size * 0.3f, Math.min(1f, intensity.get() * 1.5f) * opacity(), 34 + random.nextInt(24), 5, glow.get(),
						0.022 + random.nextDouble() * 0.028, Math.cos(angle) * 0.003, Math.sin(angle) * 0.003);
					tint(spark, 0.72f);
					client.particleEngine.add(spark);
				}
			}
		}
	}
	private void tint(WalkParticle particle) {
		tint(particle, 0f);
	}

	/** Tints a particle with the color plus some white, which makes small lights shine more. */
	private void tint(WalkParticle particle, float white) {
		int rgb = rgb();
		particle.setColor(Mth.lerp(white, (rgb >> 16 & 0xFF) / 255f, 1f), Mth.lerp(white, (rgb >> 8 & 0xFF) / 255f, 1f), Mth.lerp(white, (rgb & 0xFF) / 255f, 1f));
	}

	/** Collects the blocks around a step that the wave passes over, in rings from the block the player stands on. */
	private Wave makeWave(ClientLevel level, LocalPlayer player, float power) {
		Wave wave = new Wave(power);
		float max = reach.get() * power;
		int centerX = Mth.floor(player.getX());
		int centerZ = Mth.floor(player.getZ());
		int span = Mth.ceil(max);
		for (int bx = centerX - span; bx <= centerX + span; bx++) {
			for (int bz = centerZ - span; bz <= centerZ + span; bz++) {
				double distance = Math.hypot(bx - centerX, bz - centerZ);
				if (distance > max || !level.hasChunk(SectionPos.blockToSectionCoord(bx), SectionPos.blockToSectionCoord(bz))) {
					continue;
				}
				// search for the floor from the player's height downwards, so a roof or cave ceiling above does not matter
				double top = floorAt(level, bx, bz, player.getY());
				// only blocks at about the player's height, not far above or below
				if (Double.isNaN(top)) {
					continue;
				}
				int delay = 1 + (int) Math.round(distance / BLOCKS_PER_TICK);
				float strength = (float) Math.pow(1.0 - distance / (max + 0.8), 1.2);
				wave.tiles.add(new Tile(delay, bx, bz, top, strength));
				wave.last = Math.max(wave.last, delay);
			}
		}
		return wave;
	}

	/** @return the top height of the first standable block from just above the feet down to a few blocks below, or NaN if there is none */
	private static double floorAt(ClientLevel level, int bx, int bz, double feetY) {
		BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
		int from = (int) Math.floor(feetY + 0.9);
		int to = (int) Math.floor(feetY - 2.6);
		for (int by = from; by >= to; by--) {
			pos.set(bx, by, bz);
			BlockState state = level.getBlockState(pos);
			var shape = state.getCollisionShape(level, pos);
			if (!shape.isEmpty()) {
				return by + shape.max(Direction.Axis.Y);
			}
		}
		return Double.NaN;
	}

	private void light(ClientLevel level, Tile tile, float power) {
		if (WalkParticle.alive() > MAX_PARTICLES) {
			return;
		}
		WalkParticle particle = new WalkParticle(level, tile.x() + 0.5, tile.top() + 0.015, tile.z() + 0.5, AmbienceParticles.sprite("tile"), true,
			0.5f, 0.5f, 0.9f * intensity.get() * opacity() * tile.strength() * Math.min(1.5f, power), 20, 4, glow.get(), 0, 0, 0);
		tint(particle);
		Minecraft.getInstance().particleEngine.add(particle);
	}
}
