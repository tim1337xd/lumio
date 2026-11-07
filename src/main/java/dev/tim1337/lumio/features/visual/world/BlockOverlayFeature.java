package dev.tim1337.lumio.features.visual.world;

import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.experimental.draw.BlockStyle;
import dev.tim1337.lumio.api.experimental.util.BreakingProgress;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.mixin.accessor.GameModeBreakingAccessor;
import dev.tim1337.lumio.render.BlockHighlight;
import dev.tim1337.lumio.render.BlockOverlayDraw;
import dev.tim1337.lumio.ui.Ui;

import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;

import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Replaces the thin black outline of the block looked at with a clean edge, a soft glow, or colors that flow along the edges. While mining the faces fill as the block breaks, and it pulses when it does. Only drawn: what is mined and when stays the
 * game's. The drawing itself is in {@link BlockOverlayDraw}.
 */
public class BlockOverlayFeature extends Feature {
	private static final String GROUP = "Look";
	private static final String MINING = "While mining";
	private static final String SHAPE = "Shape";
	private static final long NANOS_PER_SECOND = 1_000_000_000L;
	private static final float LONGEST_FRAME_SECONDS = 0.1f;
	/** How quickly the shown progress of breaking follows the real one. */
	private static final float BREAKING_RATE = 14f;

	private final EnumSetting<BlockOverlayLook.Style> mode = add(new EnumSetting<>("mode", Component.literal("Style"), BlockOverlayLook.Style.OUTLINE, BlockOverlayLook.Style::displayName, EnumSetting.Style.SEGMENTED), GROUP);
	private final EnumSetting<BlockOverlayLook.Colors> colors = add(new EnumSetting<>("color_source", Component.literal("Colors"), BlockOverlayLook.Colors.CUSTOM, BlockOverlayLook.Colors::displayName, EnumSetting.Style.SEGMENTED), GROUP);
	private final ColorSetting color = add(new ColorSetting("color", Component.literal("Color"), 0xFFFFFF), GROUP);
	private final FloatSetting lineWidth = add(new FloatSetting("line_width", Component.literal("Width"), 2.5f, 1f, 6f, 1), GROUP);
	private final FloatSetting fillOpacity = add(new FloatSetting("fill_opacity", Component.literal("Fill"), 0.2f, 0f, 0.8f, 2).percent(), GROUP, "How much the faces are tinted");
	private final FloatSetting speed = add(new FloatSetting("shader_speed", Component.literal("Speed"), 1f, 0.2f, 3f, 1), GROUP);
	private final BooleanSetting showBreaking = add(new BooleanSetting("show_breaking", Component.literal("Show breaking progress"), true), MINING, "The faces fill as the block breaks");
	private final BooleanSetting breakPulse = add(new BooleanSetting("break_pulse", Component.literal("Pulse when it breaks"), true), MINING);
	private final BooleanSetting fullBlock = add(new BooleanSetting("full_block", Component.literal("Full block"), false), SHAPE, "A full cube instead of the real shape (slabs, stairs)");

	private long lastFrame;
	private float frameSeconds;
	private long frames;
	/** The block looked at: seen in which frame, how visible its box is. */
	private BlockPos shownPos;
	private VoxelShape shownShape;
	private long seenFrame = -2L;
	private float shownAlpha;
	/** The block before it, fading out. */
	private BlockPos leavingPos;
	private VoxelShape leavingShape;
	private float leavingAlpha;
	/** The block being mined, as it was when mining it began. */
	private BlockPos minedPos;
	private BlockState minedState;
	private VoxelShape minedShape;
	private float breakingShown;
	/** Whether the progress of this frame came from an add-on that knows the server's timing, and since when the fill is full (for its one pulse). */
	private boolean breakingKnown;
	private long fullSince;
	private static final long DONE_PULSE_MILLIS = 220L;
	private static final float DONE_PULSE_LIGHT = 0.35f;
	/** The block that broke, pulsing. */
	private BlockPos pulsePos;
	private VoxelShape pulseShape;
	private long pulseAt;

	public BlockOverlayFeature() {
		super("block_overlay", "Block overlay", "The box around the block you look at.", false);
		iconRgb(SystemColors.BLUE_RGB);
		iconName("cube");
		summary("Highlights the block you look at");
		color.under(colors, () -> colors.get() == BlockOverlayLook.Colors.CUSTOM);
		speed.under(mode, () -> mode.get() == BlockOverlayLook.Style.SHADER);
	}

	public void register() {
		// the targeted block: drawn here instead of the vanilla outline, which is suppressed (return false) while this is enabled
		LevelRenderEvents.BEFORE_BLOCK_OUTLINE.register((context, state) -> {
			if (!isEnabled() || state == null) {
				return true;
			}
			drawBlock(context, state.pos(), fullBlock.get() ? Shapes.block() : state.shape());
			return false;
		});
		LevelRenderEvents.COLLECT_SUBMITS.register(this::collect);
	}

	/** What goes on apart from the block looked at, also when none is: what fades out, and the pulse. */
	private void collect(LevelRenderContext context) {
		if (!isEnabled()) {
			return;
		}
		long now = System.nanoTime();
		frameSeconds = lastFrame == 0L ? 0f : Math.min(LONGEST_FRAME_SECONDS, (now - lastFrame) / (float) NANOS_PER_SECOND);
		lastFrame = now;
		frames++;
		boolean animated = dev.tim1337.lumio.ModFeatures.MENU.animations();
		if (shownPos != null && seenFrame < frames - 1L) {
			leave();
		}
		// a block that has broken leaves nothing to fade out over the air
		if (leavingPos != null && broken(leavingPos)) {
			leavingPos = null;
			leavingShape = null;
		}
		if (leavingPos != null) {
			leavingAlpha = animated ? BlockOverlayLook.fade(leavingAlpha, false, frameSeconds) : 0f;
			if (leavingAlpha <= 0f) {
				leavingPos = null;
				leavingShape = null;
			} else {
				BlockOverlayDraw.box(context, leavingPos, leavingShape, look(colorHere(), leavingAlpha));
			}
		}
		watchMining();
		if (pulsePos != null) {
			long age = (now - pulseAt) / 1_000_000L;
			if (age >= BlockOverlayLook.PULSE_MILLIS) {
				pulsePos = null;
				pulseShape = null;
			} else {
				BlockOverlayDraw.pulse(context, pulsePos, pulseShape, colorHere(), lineWidth.get(), age);
			}
		}
	}

	/** The block looked at starts fading out, unless it has broken: then it is gone at once. */
	private void leave() {
		if (shownPos != null && !broken(shownPos)) {
			leavingPos = shownPos;
			leavingShape = shownShape;
			leavingAlpha = shownAlpha;
		}
		shownPos = null;
		shownAlpha = 0f;
	}

	/** @return whether the block has turned into air on this client */
	private static boolean broken(BlockPos pos) {
		var level = Minecraft.getInstance().level;
		return level != null && level.getBlockState(pos).isAir();
	}

	/** A block that was being mined and is now something else has broken. */
	private void watchMining() {
		Minecraft client = Minecraft.getInstance();
		if (minedPos == null || client.level == null) {
			return;
		}
		BlockState now = client.level.getBlockState(minedPos);
		if (now != minedState) {
			if (breakPulse.get() && dev.tim1337.lumio.ModFeatures.MENU.animations()) {
				pulsePos = minedPos;
				pulseShape = minedShape;
				pulseAt = System.nanoTime();
			}
			if (minedPos.equals(leavingPos)) {
				leavingPos = null;
				leavingShape = null;
			}
			minedPos = null;
			minedState = null;
			minedShape = null;
			breakingShown = 0f;
		}
	}

	/** @return how far the block is broken: by the game, or by what the server said, as on servers that do their own mining */
	private float breakingOf(BlockPos pos) {
		Minecraft client = Minecraft.getInstance();
		if (client.gameMode == null) {
			return 0f;
		}
		// where an add-on knows how the server breaks it (SkyBlock's mining), that is the progress
		float known = BreakingProgress.of(pos);
		if (known >= 0f) {
			breakingKnown = true;
			return Math.min(known, 1f);
		}
		breakingKnown = false;
		var access = (GameModeBreakingAccessor) client.gameMode;
		float own = client.gameMode.isDestroying() && pos.equals(access.lumio$destroyPos()) ? access.lumio$destroyProgress() : 0f;
		return BlockOverlayLook.breaking(own, BlockBreaking.stageAt(pos));
	}

	// ------------------------------------------------------------------------------------------------ for others

	/** @return the overlay style the Pickobulus preview adopts: the configured one, or the fill while the overlay is disabled */
	public BlockStyle lookForOthers() {
		if (!isEnabled()) {
			return BlockStyle.FILL;
		}
		return switch (mode.get()) {
			case OUTLINE -> BlockStyle.OUTLINE;
			case GLOW -> BlockStyle.GLOW;
			case SHADER -> BlockStyle.SHADER;
		};
	}

	public float lineWidth() {
		return lineWidth.get();
	}

	/** @return the animated gradient color at a position along the block, from 0 to 1, see {@link BlockHighlight#gradient} */
	public int shaderColor(float place, float alpha) {
		return BlockHighlight.gradient(place, alpha, color.get() & 0xFFFFFF, speed.get());
	}

	// ------------------------------------------------------------------------------------------------ targeted block

	private int colorHere() {
		return switch (colors.get()) {
			case ACCENT -> MenuFeature.accentRgb();
			case CUSTOM -> color.get() & 0xFFFFFF;
			case RAINBOW -> BlockOverlayLook.color(BlockOverlayLook.Colors.RAINBOW, 0, 0f, secondsNow(), speed.get());
		};
	}

	private static float secondsNow() {
		return (float) (System.nanoTime() / (double) NANOS_PER_SECOND % 3600.0);
	}

	private BlockOverlayDraw.Look look(int rgb, float alpha) {
		BlockOverlayLook.Colors source = colors.get() == BlockOverlayLook.Colors.RAINBOW && mode.get() == BlockOverlayLook.Style.OUTLINE ? BlockOverlayLook.Colors.CUSTOM : colors.get();
		return new BlockOverlayDraw.Look(mode.get(), source, rgb, lineWidth.get(), fillOpacity.get(), speed.get(), secondsNow(), alpha);
	}

	private void drawBlock(LevelRenderContext context, BlockPos pos, VoxelShape shape) {
		// the game can still name the block for a moment after it has turned into air
		if (broken(pos)) {
			shownPos = null;
			shownAlpha = 0f;
			seenFrame = frames;
			return;
		}
		boolean animated = dev.tim1337.lumio.ModFeatures.MENU.animations();
		if (!pos.equals(shownPos)) {
			leave();
			shownPos = pos.immutable();
			shownAlpha = 0f;
		}
		shownShape = shape;
		seenFrame = frames;
		shownAlpha = animated ? BlockOverlayLook.fade(shownAlpha, true, frameSeconds) : 1f;
		// the color an add-on gives the block (tick glide on the block being mined), or -1 for the overlay's own color
		int glideRgb = Addons.blockTint(pos);
		int rgb = glideRgb >= 0 ? glideRgb : colorHere();
		BlockOverlayDraw.Look look = look(rgb, shownAlpha);
		if (glideRgb >= 0) {
			// the flowing colors would hide the tick glide color: the mined block glows in that color instead
			look = new BlockOverlayDraw.Look(mode.get() == BlockOverlayLook.Style.SHADER ? BlockOverlayLook.Style.GLOW : mode.get(), BlockOverlayLook.Colors.CUSTOM, glideRgb, lineWidth.get(), fillOpacity.get(), speed.get(), secondsNow(), shownAlpha);
		}
		BlockOverlayDraw.box(context, pos, shape, look);

		float progress = breakingOf(pos);
		if (progress > 0f && !pos.equals(minedPos)) {
			Minecraft client = Minecraft.getInstance();
			minedPos = pos.immutable();
			minedState = client.level == null ? null : client.level.getBlockState(pos);
			minedShape = shape;
			breakingShown = 0f;
		} else if (progress <= 0f && pos.equals(minedPos)) {
			// let go before it broke
			minedPos = null;
			minedState = null;
			minedShape = null;
		}
		if (breakingKnown) {
			// an add-on that knows the server's timing (Tick glide): its progress as it is, smooth between ticks already, full exactly when it says,
			// and never back
			breakingShown = progress > 0f ? Math.max(breakingShown, progress) : 0f;
		} else {
			breakingShown += (progress - breakingShown) * (animated ? 1f - (float) Math.exp(-frameSeconds * BREAKING_RATE) : 1f);
		}
		// once full, one short brighter moment in the overlay's color, as a cue that it is done
		if (breakingKnown && breakingShown >= 1f && fullSince == 0L) {
			fullSince = System.currentTimeMillis();
		} else if (!breakingKnown || breakingShown < 1f) {
			fullSince = 0L;
		}
		if (showBreaking.get() && breakingShown > 0.002f) {
			float pulse = fullSince == 0L || !animated ? 0f : Math.max(0f, 1f - (System.currentTimeMillis() - fullSince) / (float) DONE_PULSE_MILLIS);
			int shownRgb = pulse > 0f ? Ui.mix(0xFF000000 | rgb, 0xFFFFFFFF, DONE_PULSE_LIGHT * pulse) & 0xFFFFFF : rgb;
			BlockOverlayDraw.breaking(context, pos, shape, shownRgb, breakingShown, shownAlpha);
		}
	}
}
