package dev.tim1337.lumio.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;

import java.util.List;
import java.util.function.Predicate;

/** Exposes what is needed to find the message under the mouse: the displayed lines, the scroll offset and the chat size. */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("trimmedMessages")
	List<GuiMessage.Line> lumio$lines();

	@Accessor("chatScrollbarPos")
	int lumio$scroll();

	@Accessor("chatScrollbarPos")
	void lumio$setScroll(int lines);

	@Invoker("getLineHeight")
	int lumio$lineHeight();

	@Invoker("getScale")
	double lumio$scale();

	@Invoker("getWidth")
	int lumio$width();

	@Invoker("getHeight")
	int lumio$height();

	@Accessor("visibleMessageFilter")
	Predicate<GuiMessage> lumio$filter();

	@Invoker("refreshTrimmedMessages")
	void lumio$refresh();
}
