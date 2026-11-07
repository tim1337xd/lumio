package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.TextScale;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Choices as cards in a grid: each with its icon on a tile in its color (or a small picture of the choice), a radio button, its name, what it is,
 * and its current state as a colored chip. The chosen card has an accent frame. A click chooses a card. Cards are as tall as the tallest
 * card's content.
 */
public class OptionCardsControl<E extends Enum<E>> extends Control {
	/** Gap between cards, the same as between the cards of a page. */
	private static final int GAP = 8;
	/** Cards narrower than this would squeeze their text: the grid uses fewer columns then. */
	private static final int MIN_CARD_WIDTH = 150;
	/** Cards with a picture have short names and can be narrower, so a few fit in one row. */
	private static final int MIN_PICTURE_CARD_WIDTH = 110;
	/** Four cards with pictures stay in one row down to this width: then with a shorter picture and their names only, as in the mockup. */
	private static final int MIN_ROW_CARD_WIDTH = 60;
	private static final int ROW_CARD_COUNT = 4;
	private static final float ROW_PICTURE_ASPECT = 0.34f;
	private static final int MIN_ROW_PICTURE_HEIGHT = 24;
	private static final int PAD = 8;
	private static final int ICON_TILE = 20;
	private static final int ICON = 12;
	/** The radio mark: its ring and its dot, both even so the dot sits exactly in the middle of the ring at every GUI scale. */
	private static final int RADIO = 10;
	private static final float RADIO_DOT = 4f;
	private static final int NAME_Y = 30;
	private static final int TEXT_Y = 43;
	private static final int LINE = 9;
	private static final int TEXT_LINES = 2;
	private static final int PICTURE_TEXT_LINES = 3;
	private static final int CHIP_HEIGHT = 14;
	/** Height of a picture relative to its width, and its bounds in menu pixels. */
	private static final float PICTURE_ASPECT = 0.5f;
	private static final int MIN_PICTURE_HEIGHT = 40;
	private static final int MAX_PICTURE_HEIGHT = 64;
	private static final int PICTURE_NAME_GAP = 8;
	private static final int NAME_HEIGHT = 12;

	private final EnumSetting<E> setting;
	private final Runnable onChanged;
	private final List<E> choices;
	/** The names of the choices, as text once. */
	private final String[] names;
	/** Whether the cards show pictures, known once they are measured. */
	private boolean pictures;
	private int cardHeight;
	/** The description of every card, wrapped when measured for the width of a card. */
	private final List<List<String>> descriptions = new ArrayList<>();
	private int wrappedRoom = -1;
	private int pictureHeight;

	public OptionCardsControl(EnumSetting<E> setting, Runnable onChanged) {
		this.setting = setting;
		this.onChanged = onChanged;
		this.choices = setting.choices();
		this.names = new String[choices.size()];
		for (int i = 0; i < names.length; i++) {
			names[i] = setting.nameOf(choices.get(i)).getString();
		}
	}

	@Override
	public boolean isBlock() {
		return true;
	}

	@Override
	public boolean hasLabel() {
		return false;
	}

	@Override
	public boolean bare() {
		return !setting.hasOptionCardsInGroup();
	}

	/** @return whether the cards lie in one row with short pictures and without their descriptions */
	private boolean oneRow() {
		return pictures && choices.size() == ROW_CARD_COUNT && (width - GAP * (ROW_CARD_COUNT - 1)) / ROW_CARD_COUNT >= MIN_ROW_CARD_WIDTH;
	}

	private int columns() {
		if (oneRow()) {
			return ROW_CARD_COUNT;
		}
		int min = pictures ? MIN_PICTURE_CARD_WIDTH : MIN_CARD_WIDTH;
		int fit = Math.max(1, (width + GAP) / (min + GAP));
		// two columns rather than three for four cards: two even rows instead of three and a single one
		return choices.size() == 4 && fit == 3 ? 2 : Math.min(fit, choices.size());
	}

	private int cardWidth() {
		return (width - GAP * (columns() - 1)) / columns();
	}

	@Override
	protected int measure(Ui ui, int width) {
		this.width = width;
		pictures = false;
		for (E choice : choices) {
			pictures |= setting.optionCardOf(choice).picture() != null;
		}
		int room = cardWidth() - 2 * PAD;
		boolean row = oneRow();
		pictureHeight = row ? Math.max(MIN_ROW_PICTURE_HEIGHT, Math.round(room * ROW_PICTURE_ASPECT))
			: Mth.clamp(Math.round(room * PICTURE_ASPECT), MIN_PICTURE_HEIGHT, MAX_PICTURE_HEIGHT);
		if (room != wrappedRoom) {
			wrappedRoom = room;
			descriptions.clear();
			for (E choice : choices) {
				descriptions.add(row ? List.of() : ui.wrapScaled(setting.optionCardOf(choice).description(), room, TextScale.SMALL, pictures ? PICTURE_TEXT_LINES : TEXT_LINES));
			}
		}
		// every card as tall as the tallest content, so a row stays even
		cardHeight = 0;
		for (int i = 0; i < choices.size(); i++) {
			EnumSetting.OptionCard card = setting.optionCardOf(choices.get(i));
			int lines = descriptions.get(i).size();
			int height = textY() + lines * LINE + (card.state() != null ? 6 + CHIP_HEIGHT : 0) + PAD;
			cardHeight = Math.max(cardHeight, height);
		}
		int rows = (choices.size() + columns() - 1) / columns();
		return rows * cardHeight + Math.max(0, rows - 1) * GAP;
	}

	/** @return the top of a card's name: below the picture, or below the icon tile */
	private int nameY() {
		return pictures ? PAD + pictureHeight + PICTURE_NAME_GAP : NAME_Y;
	}

	private int textY() {
		return pictures ? nameY() + NAME_HEIGHT + 2 : TEXT_Y;
	}

	private int cardX(int i) {
		return x + (i % columns()) * (cardWidth() + GAP);
	}

	private int cardY(int i) {
		return y + (i / columns()) * (cardHeight + GAP);
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		Palette palette = ui.palette;
		int cardWidth = cardWidth();
		int room = cardWidth - 2 * PAD;
		for (int i = 0; i < choices.size(); i++) {
			E choice = choices.get(i);
			EnumSetting.OptionCard card = setting.optionCardOf(choice);
			boolean selected = choice == setting.get();
			int cx = cardX(i);
			int cy = cardY(i);
			float hover = hover(ui, i, mouseX >= cx && mouseX < cx + cardWidth && mouseY >= cy && mouseY < cy + cardHeight);
			if (selected) {
				ui.rounded(cx - 2, cy - 2, cardWidth + 4, cardHeight + 4, Radius.LG + 2, Ui.withOpacity(ui.accent, 0.2f));
			}
			ui.rounded(cx, cy, cardWidth, cardHeight, Radius.LG, palette.card());
			if (hover > 0f || selected) {
				ui.rounded(cx, cy, cardWidth, cardHeight, Radius.LG, Ui.withOpacity(palette.hover(), selected ? 1f : hover));
			}
			ui.ring(cx, cy, cardWidth, cardHeight, Radius.LG, 1, selected ? ui.accent : palette.cardBorder());

			int nameY = cy + nameY();
			float radioX = cx + cardWidth - PAD - RADIO / 2f;
			float radioY;
			int nameRoom = room;
			if (card.picture() != null) {
				card.picture().draw(ui, cx + PAD, cy + PAD, room, pictureHeight);
				// the radio sits on the name's line, after the name
				radioY = nameY + NAME_HEIGHT / 2f - 1f;
				nameRoom = room - RADIO - 6;
			} else {
				ui.iconTile(card.icon(), cx + PAD, cy + PAD, ICON_TILE, ICON, Radius.SM, 0xFF000000 | card.rgb(), 0xFFFFFFFF);
				// centered on the icon tile's row
				radioY = cy + PAD + ICON_TILE / 2f;
			}
			if (oneRow()) {
				// short names under short pictures: the frame says which is chosen, and the name has the whole width
				String name = ui.fit(names[i], room);
				ui.textAtScaledBold(name, cx + (cardWidth - ui.width(name)) / 2f, nameY, 1f, selected ? palette.text() : palette.textSecondary());
			} else {
				ui.ringAt(radioX, radioY, RADIO, 1f, selected ? ui.accent : Ui.withOpacity(palette.textSecondary(), 0.7f));
				if (selected) {
					ui.discAt(radioX, radioY, RADIO_DOT, ui.accent);
				}
				ui.textAtScaledBold(ui.fit(names[i], nameRoom), cx + PAD, nameY, 1f, palette.text());
			}
			List<String> lines = descriptions.get(i);
			for (int k = 0; k < lines.size(); k++) {
				ui.textAtScaled(lines.get(k), cx + PAD, cy + textY() + k * LINE, TextScale.SMALL, palette.textSecondary());
			}
			if (card.state() != null) {
				chip(ui, card.state().get(), cx + PAD, cy + cardHeight - PAD - CHIP_HEIGHT, room);
			}
		}
	}

	/** Draws a state as a chip: tinted in its color, or neutral */
	private static void chip(Ui ui, EnumSetting.OptionState state, int cx, int cy, int room) {
		if (state == null || state.text().isEmpty()) {
			return;
		}
		Palette palette = ui.palette;
		String text = ui.fitScaled(state.text(), room - 10, TextScale.SMALL);
		int width = Math.round(ui.widthScaled(text, TextScale.SMALL)) + 10;
		boolean neutral = state.rgb() < 0;
		int color = 0xFF000000 | Math.max(0, state.rgb());
		ui.rounded(cx, cy, width, CHIP_HEIGHT, Radius.XS, neutral ? palette.control() : Ui.withOpacity(color, 0.16f));
		ui.textAtScaled(text, cx + 5, cy + 3f, TextScale.SMALL, neutral ? palette.textSecondary() : Ui.mix(palette.text(), color, 0.6f));
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		if (button != 0) {
			return false;
		}
		int cardWidth = cardWidth();
		for (int i = 0; i < choices.size(); i++) {
			int cx = cardX(i);
			int cy = cardY(i);
			if (mouseX >= cx && mouseX < cx + cardWidth && mouseY >= cy && mouseY < cy + cardHeight) {
				E choice = choices.get(i);
				if (choice != setting.get()) {
					setting.choose(choice, () -> {
						UiSounds.tick(choices.indexOf(choice) / (float) Math.max(1, choices.size() - 1));
						onChanged.run();
					});
				}
				return true;
			}
		}
		return false;
	}
}
