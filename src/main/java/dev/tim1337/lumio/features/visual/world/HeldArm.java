package dev.tim1337.lumio.features.visual.world;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.PlayerModelPart;

/** Draws the local player's arm in the first person, as the game draws the empty hand: their skin, its slim or wide arm, and the sleeves as the skin layers say. */
public final class HeldArm {
	private HeldArm() {
	}

	/** Draws the arm at the pose, unless the player is invisible, as the game leaves it out then. */
	public static void submit(PoseStack pose, SubmitNodeCollector collector, int light, HumanoidArm arm) {
		Minecraft client = Minecraft.getInstance();
		LocalPlayer player = client.player;
		if (player == null || player.isInvisible() || !(client.getEntityRenderDispatcher().getRenderer(player) instanceof AvatarRenderer<?> renderer)) {
			return;
		}
		Identifier skin = player.getSkin().body().texturePath();
		if (arm == HumanoidArm.RIGHT) {
			renderer.renderRightHand(pose, collector, light, skin, player.isModelPartShown(PlayerModelPart.RIGHT_SLEEVE));
		} else {
			renderer.renderLeftHand(pose, collector, light, skin, player.isModelPartShown(PlayerModelPart.LEFT_SLEEVE));
		}
	}
}
