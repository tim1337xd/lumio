package dev.tim1337.lumio.features.visual.hud;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.StringDecomposer;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A value for the right edge of a panel, with its digits in equal cells so a number does not shift as it changes. Each character is its own laid out
 * text, kept until the value changes; a value that holds no digit is just text.
 */
final class TabularText {
	private static final HudText DIGIT = new HudText(FormattedText.of("0"));

	private final Component source;
	private final List<HudText> characters = new ArrayList<>();
	private final List<Boolean> digits = new ArrayList<>();

	TabularText(Component source) {
		this.source = source;
		// color codes in the text are read here: a character on its own would show its code as a letter and lose its color
		source.visit((Style style, String part) -> {
			StringDecomposer.iterateFormatted(part, style, (position, characterStyle, codePoint) -> {
				String character = new String(Character.toChars(codePoint));
				characters.add(new HudText(Component.literal(character).withStyle(characterStyle)));
				digits.add(codePoint >= '0' && codePoint <= '9');
				return true;
			});
			return Optional.empty();
		}, Style.EMPTY);
	}

	Component source() {
		return source;
	}

	/** @return the width in panel pixels in the font in use now */
	float width(Font font) {
		float digit = DIGIT.naturalWidth(font);
		float total = 0f;
		for (int i = 0; i < characters.size(); i++) {
			total += digits.get(i) ? digit : characters.get(i).naturalWidth(font);
		}
		return total;
	}

	/** Draws the value ending at a right edge, each digit in the middle of its cell. */
	void draw(GuiGraphicsExtractor graphics, Font font, float right, float y, int color) {
		float digit = DIGIT.naturalWidth(font);
		float x = right;
		for (int i = characters.size() - 1; i >= 0; i--) {
			HudText character = characters.get(i);
			character.layout(font, 0f);
			float cell = digits.get(i) ? digit : character.width();
			x -= cell;
			character.draw(graphics, font, x + (cell - character.width()) / 2f, y, color, false);
		}
	}
}
