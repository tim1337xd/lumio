package dev.tim1337.lumio.ui.setup;

import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;

import java.util.List;

/**
 * Step two: the main menu the game greets the player with, as two large cards with a picture each (the living world, or the game's own title
 * screen with a small settings button), and the startup animation and the smooth loading screens as two switch cards. The startup animation
 * leads into the living world, so with the classic main menu it is greyed out and off. What is picked here is applied when the setup ends.
 */
final class MenuStep implements SetupStep {
	private static final int SECTION_GAP = 12;
	private static final int CARD_GAP = 10;
	private static final int CARD_PAD = 6;
	/** The gap between a card's picture and its name, and the step of its text lines. */
	private static final int TEXT_GAP = 8;
	private static final int NAME_HEIGHT = 13;
	private static final int LINE = 10;
	private static final int RADIO = 14;
	private static final int SWITCH_HEIGHT = 35;
	private static final int SWITCH_GAP = 8;

	/** A main menu to pick: whether it is the living world, its name and its line of text. */
	private record Choice(boolean livingWorld, String name, String text) {
	}

	private static final List<Choice> CHOICES = List.of(
		new Choice(true, "Living world", "Seasons, your music, worlds and servers"),
		new Choice(false, "Classic", "Minecraft's own main menu, with a small settings button"));

	private final SetupScreen screen;

	MenuStep(SetupScreen screen) {
		this.screen = screen;
	}

	@Override
	public String name() {
		return "Main menu";
	}

	@Override
	public void draw(SetupKit kit, int x, int y, int width, int height, boolean wide) {
		Ui ui = kit.ui;
		Palette palette = ui.palette;
		SetupChoices choices = screen.choices;
		int top = kit.heading("Your main menu", "Pick how the game greets you. You can switch any time in the menu settings.", x, y, width)
			+ SECTION_GAP;

		// the cards: as tall as the pictures in the mockup's shape, or less where the switches below would not fit
		int cardWidth = (width - CARD_GAP) / 2;
		int textRoom = cardWidth - 2 * CARD_PAD - 6 - RADIO - 8;
		int lines = 1;
		for (Choice choice : CHOICES) {
			lines = Math.max(lines, Math.min(2, kit.wrap(choice.text(), textRoom, SetupKit.SMALL).size()));
		}
		int textHeight = NAME_HEIGHT + lines * LINE;
		int pictureWidth = cardWidth - 2 * CARD_PAD;
		int room = y + height - top - SECTION_GAP - SWITCH_HEIGHT - 2 * CARD_PAD - TEXT_GAP - textHeight;
		int pictureHeight = Math.min(Math.round(pictureWidth * MenuPictures.ASPECT), room);
		int cardHeight = 2 * CARD_PAD + pictureHeight + TEXT_GAP + textHeight;
		for (int i = 0; i < CHOICES.size(); i++) {
			Choice choice = CHOICES.get(i);
			int cardX = x + i * (cardWidth + CARD_GAP);
			float hover = kit.area("menu" + i, cardX, top, cardWidth, cardHeight, SetupKit.CARD_RADIUS, () -> choices.setLivingWorld(choice.livingWorld()));
			float chosen = kit.follow("menu_on" + i, choices.livingWorld() == choice.livingWorld() ? 1f : 0f);
			kit.card(cardX, top, cardWidth, cardHeight, chosen, hover);
			int pictureX = cardX + CARD_PAD;
			int pictureY = top + CARD_PAD;
			if (choice.livingWorld()) {
				MenuPictures.livingWorld(kit, pictureX, pictureY, pictureWidth, pictureHeight);
			} else {
				MenuPictures.classic(kit, pictureX, pictureY, pictureWidth, pictureHeight);
			}
			int textX = pictureX + 3;
			int textY = pictureY + pictureHeight + TEXT_GAP;
			ui.textAtScaledBold(kit.fit(choice.name(), textRoom, SetupKit.BODY), textX, textY, SetupKit.BODY, palette.text());
			List<String> wrapped = kit.wrap(choice.text(), textRoom, SetupKit.SMALL);
			for (int line = 0; line < Math.min(lines, wrapped.size()); line++) {
				String text = line == lines - 1 && wrapped.size() > lines ? kit.fit(wrapped.get(line) + " …", textRoom, SetupKit.SMALL) : wrapped.get(line);
				ui.textAtScaled(text, textX, textY + NAME_HEIGHT + line * LINE, SetupKit.SMALL, palette.textSecondary());
			}
			kit.radio(cardX + cardWidth - CARD_PAD - 3 - RADIO, textY + (textHeight - RADIO) / 2 - 1, RADIO, chosen);
		}
		top += cardHeight + SECTION_GAP;

		int switchWidth = (width - SWITCH_GAP) / 2;
		kit.switchCard("startup", x, top, switchWidth, SWITCH_HEIGHT, "sparkle", 0xFF9F0A, 0xFF375F, "Startup animation",
			"The Mojang screen flows into the world", choices.startupAnimation(), choices.livingWorld(), choices::toggleStartupAnimation);
		kit.switchCard("loading", x + switchWidth + SWITCH_GAP, top, switchWidth, SWITCH_HEIGHT, "swoosh", 0x64D2FF, 0x0A84FF,
			"Smooth loading screens", "One calm screen for joining and loading", choices.smoothLoading(), true, choices::toggleSmoothLoading);
	}
}
