package dev.tim1337.lumio.mixin.hud;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.util.HudCover;
import dev.tim1337.lumio.mixin.accessor.HudAccessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Action bar parsing and replacement, and hiding the HUD in cinematic mode. */
@Mixin(Hud.class)
public abstract class HudMixin {
	/**
	 * The small fire icon of the Visual Filter, where the burning overlay is hidden: left of the hotbar. Made on first use, never in a static
	 * field: this class's fields become the game's Hud's, made before items have their components, where an item stack crashes the start.
	 */
	@Unique
	private net.minecraft.world.item.ItemStack lumio$fireIcon;
	@Unique
	private static final int FIRE_ICON_LEFT = 91 + 22;
	@Unique
	private static final int FIRE_ICON_UP = 20;

	@Inject(method = "extractRenderState", at = @At("TAIL"))
	private void lumio$fireIcon(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
		Minecraft client = Minecraft.getInstance();
		if (ModFeatures.VISUAL_FILTER == null || !ModFeatures.VISUAL_FILTER.fireIcon() || client.player == null || !client.player.isOnFire()
			|| client.player.fireImmune()) {
			return;
		}
		if (lumio$fireIcon == null) {
			lumio$fireIcon = new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.FIRE_CHARGE);
		}
		graphics.item(lumio$fireIcon, graphics.guiWidth() / 2 - FIRE_ICON_LEFT, graphics.guiHeight() - FIRE_ICON_UP);
	}

	/** Hypixel SkyBlock shows mana in the action bar: it is parsed here and can be stripped, to be displayed as a bar instead. */
	@ModifyVariable(method = "setOverlayMessage", at = @At("HEAD"), argsOnly = true)
	private Component lumio$mana(Component message) {
		Component shown = ModFeatures.STATUS == null ? message : ModFeatures.STATUS.filter(message);
		if (ModFeatures.ACTION_BAR != null) {
			ModFeatures.ACTION_BAR.accept(shown);
		}
		return shown;
	}

	/** The action bar is drawn by the mod's panel when that is enabled. */
	/** The Camera module can hide the purple swirl of a Nether portal; nausea from an effect keeps its own overlay. */
	@Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
	private void lumio$portalOverlay(CallbackInfo ci) {
		if (ModFeatures.VISUAL_FILTER != null && ModFeatures.VISUAL_FILTER.hidePortal()) {
			ci.cancel();
		}
	}

	@Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void lumio$actionBar(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
		if (ModFeatures.ACTION_BAR != null && ModFeatures.ACTION_BAR.hidesVanilla()) {
			ci.cancel();
		}
	}

	/**
	 * Under a screen that covers the game (see {@link HudCover}) the HUD is skipped. In cinematic mode the HUD (the chat included) is skipped; only the film's letterbox, vignette and title card are drawn, and the chat while
	 * the chat screen is open over the film.
	 */
	@Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
	private void lumio$cinematic(GuiGraphicsExtractor graphics, DeltaTracker tracker, CallbackInfo ci) {
		Screen screen = Minecraft.getInstance().gui.screen();
		if (screen != null && HudCover.covers(screen)) {
			ci.cancel();
			return;
		}
		if (ModFeatures.CINEMATIC != null && ModFeatures.CINEMATIC.hidesHud()) {
			ModFeatures.CINEMATIC.renderOverlay(graphics, tracker);
			if (Minecraft.getInstance().gui.screen() instanceof ChatScreen) {
				((HudAccessor) this).lumio$extractChat(graphics, tracker);
			}
			ci.cancel();
		}
	}
}
