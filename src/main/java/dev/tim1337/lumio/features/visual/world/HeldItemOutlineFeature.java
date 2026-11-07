package dev.tim1337.lumio.features.visual.world;

import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.ColorSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.render.EchoFeedback;
import dev.tim1337.lumio.render.OutlineItem;
import dev.tim1337.lumio.render.OutlineKind;
import dev.tim1337.lumio.render.OutlineStyle;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Light around the shape of the item the local player holds, and optionally around the hand.
 * <p>
 * Vanilla's outline only works through the entity glow target, which is never composited in the first person hand pass, so it is drawn by the mod instead.
 * Glow is built around the item's on-screen silhouette, for any item, see {@link dev.tim1337.lumio.render.ScreenGlow}; Echo draws thin lines of light along
 * the edge of a flat item and streaks along its motion, see {@link RadianceEcho}.
 */
public class HeldItemOutlineFeature extends Feature {
	private static final String LOOK = "Look";
	private static final String WHERE = "Where";
	private static final long RARITY_REREAD_NANOS = 500_000_000L;

	private final EnumSetting<RadianceLook.Style> style = add(new EnumSetting<>("mode", Component.literal("Style"), RadianceLook.Style.GLOW, RadianceLook.Style::displayName, EnumSetting.Style.SEGMENTED), LOOK);
	private final FloatSetting echoLength = add(new FloatSetting("echo_length", Component.literal("Echo length"), 0.5f, 0f, 1f, 2).percent().offAtZero(), LOOK);
	/** A slow flowing shimmer in Echo's light, also while standing still; 0 is none. */
	private final FloatSetting echoShimmer = add(new FloatSetting("echo_shimmer", Component.literal("Shimmer"), 0.5f, 0f, 1f, 2).percent(), LOOK);
	private final EnumSetting<TrailLook.Colors> colors = add(new EnumSetting<>("color_source", Component.literal("Color"), TrailLook.Colors.ACCENT, TrailLook.Colors::displayName, EnumSetting.Style.SEGMENTED), LOOK);
	private final ColorSetting color = add(new ColorSetting("color", Component.literal("Custom color"), 0x00E5FF), LOOK);
	private final FloatSetting glowStrength = add(new FloatSetting("glow_strength", Component.literal("Strength"), 0.7f, 0.05f, 1f, 2), LOOK);
	private final FloatSetting width = add(new FloatSetting("width", Component.literal("Width"), 1f, 0.25f, 2f, 2), LOOK);
	private final BooleanSetting armFirst = add(new BooleanSetting("arm_first_person", Component.literal("Around the hand too"), false), WHERE);
	private final EnumSetting<RadianceLook.ShowsOn> showsOn = add(new EnumSetting<>("shows_on", Component.literal("Shows on"), RadianceLook.ShowsOn.ALL, RadianceLook.ShowsOn::displayName, EnumSetting.Style.SEGMENTED), WHERE);

	/** The item kinds that get the light under Chosen. */
	private final ItemKinds kinds = new ItemKinds();

	private final RadianceLook.Blend blend = new RadianceLook.Blend();
	private final RadianceEcho echo = new RadianceEcho();
	private ItemStack rarityStack;
	private int rarityRgb;
	private long rarityRead;

	public HeldItemOutlineFeature() {
		super("held_item_outline", "Radiance", "Light around the shape of what you hold.", true);

		iconRgb(SystemColors.BLUE_RGB);
		iconName("sun");
		summary("Light around the shape of what you hold");
		add(kinds.setting(), WHERE);
		addResetButton();
		kinds.setting().visibleWhen(() -> showsOn.get() == RadianceLook.ShowsOn.CHOSEN);
		echoLength.visibleWhen(() -> style.get() == RadianceLook.Style.ECHO);
		echoShimmer.visibleWhen(() -> style.get() == RadianceLook.Style.ECHO);
		color.under(colors, () -> colors.get() == TrailLook.Colors.CUSTOM);
	}

	/** @return the style on screen right now, which follows the chosen one through a fade; no cost at all while the feature is off and done fading */
	private RadianceLook.Style shown() {
		boolean on = isEnabled();
		if (!on && blend.idle()) {
			return RadianceLook.Style.OFF;
		}
		blend.update(on ? style.get() : RadianceLook.Style.OFF, System.nanoTime());
		return blend.shown();
	}

	/** @return the color of the light for what is in the hand; reads the item's name only now and then, as this runs each frame */
	private int rgb(ItemStack stack) {
		return switch (colors.get()) {
			case RARITY -> rarity(stack);
			case ACCENT -> MenuFeature.accentRgb();
			case CUSTOM -> color.get() & 0xFFFFFF;
		};
	}

	private int rarity(ItemStack stack) {
		long now = System.nanoTime();
		if (stack != rarityStack || now - rarityRead > RARITY_REREAD_NANOS) {
			rarityStack = stack;
			rarityRgb = TrailLook.rarityRgb(stack) & 0xFFFFFF;
			rarityRead = now;
		}
		return rarityRgb;
	}

	private boolean showsOn(ItemStack stack) {
		return switch (showsOn.get()) {
			case ALL -> true;
			case WEAPONS -> ItemKinds.isWeapon(stack);
			case CHOSEN -> kinds.affects(stack);
		};
	}

	/** @return whether the first person hand gets the radiance */
	public boolean glowsOnArm() {
		return armFirst.get() && shown() != RadianceLook.Style.OFF;
	}

	/** @return the look for the first person hand, in the color of what it holds */
	public OutlineStyle handStyle() {
		return bodyStyle(rgb(ItemKinds.mainHandStack()));
	}

	/** @return the style on screen now */
	public RadianceLook.Style shownStyle() {
		return blend.shown();
	}

	/** The first person hand is drawn with Echo shown: it goes into the after-glow, in the color of what it holds. */
	public void echoHand() {
		EchoFeedback.want(true, rgb(ItemKinds.mainHandStack()), glowStrength.get() * blend.level(), width.get(), RadianceEcho.lengthFactor(echoLength.get()), echoShimmer.get());
	}

	private OutlineStyle bodyStyle(int rgb) {
		// the rings around a body are drawn from the strength, not from a texture
		return new OutlineStyle(OutlineKind.GLOW, width.get(), glowStrength.get() * blend.level(), rgb, 0f, 1f);
	}

	/** Called before an item is submitted normally; submits the light around it. */
	public void submitOutline(OutlineItem item, ItemDisplayContext context, PoseStack pose, SubmitNodeCollector collector) {
		if (!HeldItem.isFirstPerson(context)) {
			return;
		}
		RadianceLook.Style now = shown();
		if (now == RadianceLook.Style.OFF) {
			return;
		}
		ItemStack stack = ItemKinds.stackInHand(context);
		if (!showsOn(stack)) {
			return;
		}
		int rgb = rgb(stack);
		float level = blend.level();
		// Echo: the item goes into the after-glow, which is made on the screen after the hand pass (see EchoFeedback); its edge between two frames
		// as well, when it moved far
		if (now == RadianceLook.Style.ECHO) {
			echo.submit(item, context, pose, echoLength.get() > 0f);
			EchoFeedback.want(true, rgb, glowStrength.get() * level, width.get(), RadianceEcho.lengthFactor(echoLength.get()), echoShimmer.get());
			return;
		}
		submitStyle(item, pose, collector, new OutlineStyle(OutlineKind.GLOW, width.get(), glowStrength.get(), rgb, 0f, level));
	}

	private void submitStyle(OutlineItem item, PoseStack pose, SubmitNodeCollector collector, OutlineStyle look) {
		for (int i = 0; i < item.lumio$layerCount(); i++) {
			item.lumio$layer(i).lumio$submitGlow(pose, collector, look);
		}
	}

}
