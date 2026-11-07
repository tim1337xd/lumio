package dev.tim1337.lumio.features.visual.screen.chat;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.util.FormattedCharSequence;

import org.joml.Matrix3x2f;

import java.util.function.Consumer;

/**
 * Stands between the game's chat and what it draws to. The message text goes through as it is. Left out are the game's dark band behind each line, the bar of a message's tag at its left and the game's scroll bar, since
 * the chat has a panel and a stripe and a bar of its own. Everything moves by where the chat is; drawing and clicks both come through here, so the lines are clicked where they are shown.
 */
public final class ChatLookAccess implements ChatComponent.ChatGraphicsAccess {
	private final ChatComponent.ChatGraphicsAccess game;
	private final float offsetX;
	private final float offsetY;
	private final int textLift;

	public ChatLookAccess(ChatComponent.ChatGraphicsAccess game, float offsetX, float offsetY, int textLift) {
		this.game = game;
		this.offsetX = offsetX;
		this.offsetY = offsetY;
		this.textLift = textLift;
	}

	@Override
	public void updatePose(Consumer<Matrix3x2f> change) {
		game.updatePose(pose -> {
			pose.translate(offsetX, offsetY);
			change.accept(pose);
		});
	}

	@Override
	public void fill(int left, int top, int right, int bottom, int color) {
		// nothing of the game's: see the class
	}

	@Override
	public boolean handleMessage(int y, float alpha, FormattedCharSequence text) {
		return game.handleMessage(y - textLift, alpha, text);
	}

	@Override
	public void handleTag(int left, int top, int right, int bottom, float alpha, GuiMessageTag tag) {
		// nothing of the game's: see the class
	}

	@Override
	public void handleTagIcon(int left, int y, boolean hovered, GuiMessageTag tag, GuiMessageTag.Icon icon) {
		game.handleTagIcon(left, y - textLift, hovered, tag, icon);
	}
}
