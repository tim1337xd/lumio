package dev.tim1337.lumio.features.visual.screen.chat;

import net.minecraft.util.FormattedCharSink;
import net.minecraft.util.FormattedCharSequence;

/**
 * A row of a centered message (see {@link ChatCentering}): the row's text, and its width, by which it is placed in the middle of the chat.
 *
 * @param row   the text
 * @param width its width in the chat's own pixels
 */
public record ChatCenteredRow(FormattedCharSequence row, int width) implements FormattedCharSequence {
	@Override
	public boolean accept(FormattedCharSink sink) {
		return row.accept(sink);
	}
}
