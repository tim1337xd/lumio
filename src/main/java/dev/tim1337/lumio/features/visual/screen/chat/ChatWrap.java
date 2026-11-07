package dev.tim1337.lumio.features.visual.screen.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.ComponentCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Wraps a message as the game does, without the space it puts before each following row: in the chat's own columns, those rows start right under the message's text. */
public final class ChatWrap {
	private ChatWrap() {
	}

	/** @return the message's rows */
	public static List<FormattedCharSequence> rows(FormattedText message, int width, Font font) {
		boolean colors = Minecraft.getInstance().options.chatColors().get();
		ComponentCollector collector = new ComponentCollector();
		message.visit((style, text) -> {
			collector.append(FormattedText.of(colors ? text : ChatFormatting.stripFormatting(text), style));
			return Optional.empty();
		}, Style.EMPTY);
		List<FormattedCharSequence> rows = new ArrayList<>();
		font.getSplitter().splitLines(collector.getResultOrEmpty(), width, Style.EMPTY, (row, continues) -> rows.add(Language.getInstance().getVisualOrder(row)));
		if (rows.isEmpty()) {
			rows.add(FormattedCharSequence.EMPTY);
		}
		return rows;
	}
}
