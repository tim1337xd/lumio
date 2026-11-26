package dev.tim1337.lumio.features.visual.screen.chat;

import net.minecraft.ChatFormatting;
import net.minecraft.client.ComponentCollector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.StringSplitter;
import net.minecraft.client.gui.Font;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

/**
 * Wraps a message as the game does, without the space it puts before each following row: in the chat's own columns, those rows start right under the message's text. Every row ends inside the
 * width, a word longer than it broken inside. A server's centered line loses its padding and its rows are marked to be centered in the chat ({@link ChatCentering}).
 */
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
		return rows(collector.getResultOrEmpty(), width, font.getSplitter(), text -> Language.getInstance().getVisualOrder(text));
	}

	/**
	 * @param splitter measures and breaks the text
	 * @param order    turns a row into what is drawn
	 * @return the message's rows; those of a centered message are {@link ChatCenteredRow}s
	 */
	static List<FormattedCharSequence> rows(FormattedText message, int width, StringSplitter splitter, Function<FormattedText, FormattedCharSequence> order) {
		List<FormattedCharSequence> rows = new ArrayList<>();
		FormattedText centered = ChatCentering.unpadded(message, splitter);
		if (centered != null) {
			splitter.splitLines(centered, width, Style.EMPTY, (row, continues) -> rows.add(new ChatCenteredRow(order.apply(row), Mth.ceil(splitter.stringWidth(row)))));
		} else {
			splitter.splitLines(message, width, Style.EMPTY, (row, continues) -> rows.add(order.apply(row)));
		}
		if (rows.isEmpty()) {
			rows.add(FormattedCharSequence.EMPTY);
		}
		return rows;
	}
}
