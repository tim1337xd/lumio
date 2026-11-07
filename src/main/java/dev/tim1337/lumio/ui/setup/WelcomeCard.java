package dev.tim1337.lumio.ui.setup;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.ui.Glass;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiGlow;

import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.Locale;

/**
 * The first step: a smaller card of its own in the middle of the screen, without the window's header and footer. It greets the player by name,
 * says in three cards what the mod does, and starts the setup, or skips it with the recommendations or with everything off.
 */
final class WelcomeCard {
	private static final int WIDTH = 410;
	private static final int HEIGHT = 362;
	private static final int PAD = 28;
	private static final int ICON = 49;
	private static final int CARD_GAP = 8;
	private static final int CARD_HEIGHT = 76;
	private static final int CARD_ICON = 22;
	private static final int BUTTON_HEIGHT = 26;
	private static final float TITLE = 2.8f;
	private static final float INTRO = 1.05f;
	private static final int INDIGO = 0x5E5CE6;

	/** The three cards: icon, colors of the tile, title and text. */
	private record Point(String icon, int light, int dark, String title, String text) {
	}

	private static final Point LOOK = new Point("sparkle", 0xBF5AF2, INDIGO, "A fresh look", "Menus, HUD and a living main menu.");
	private static final Point MUSIC = new Point("note", 0xFF375F, 0xBF5AF2, "Your music", "Now playing with synced lyrics.");

	/** @return the cards: the look, the add-on's world when one offers it, the music */
	private static List<Point> points() {
		SetupGroup world = SetupGroup.world();
		if (world == null) {
			return List.of(LOOK, MUSIC);
		}
		return List.of(LOOK, new Point(world.icon(), world.light(), world.dark(), "Made for " + world.shortName(), SetupGroup.provider().welcomeLine()),
			MUSIC);
	}

	private final SetupScreen screen;

	WelcomeCard(SetupScreen screen) {
		this.screen = screen;
	}

	void draw(SetupKit kit, int screenWidth, int screenHeight) {
		Ui ui = kit.ui;
		Palette palette = ui.palette;
		int x = (screenWidth - WIDTH) / 2;
		int y = (screenHeight - HEIGHT) / 2;
		float alpha = Ui.alpha();
		if (palette.glass()) {
			Glass.body(ui, x, y, WIDTH, HEIGHT, Radius.XXL + 4, alpha, true, UiGlow.Target.MENU);
			Glass.edges(ui, x, y, WIDTH, HEIGHT, Radius.XXL + 4, alpha, true, UiGlow.Target.MENU);
		} else {
			HudStyle.panel(ui, x, y, WIDTH, HEIGHT, Radius.XXL + 4, HudStyle.opacity() * alpha, UiGlow.Target.MENU, alpha);
		}
		Ui.setAlpha(alpha);
		float middle = x + WIDTH / 2f;
		int top = y + PAD;

		// the mod's icon in the accent, and the greeting
		kit.tile("sparkle", Math.round(middle - ICON / 2f), top, ICON, ui.accent & 0xFFFFFF, INDIGO);
		top += ICON + 17;
		Minecraft client = Minecraft.getInstance();
		String name = client.getUser() != null ? client.getUser().getName() : "player";
		kit.centered("WELCOME, " + name.toUpperCase(Locale.ROOT), middle, top + 3, SetupKit.SMALL, true, Ui.withOpacity(palette.textSecondary(), 0.9f));
		top += 13;
		float titleWidth = ui.titleWidthScaled("Lumio", TITLE);
		ui.textAtScaledTitle("Lumio", middle - titleWidth / 2f, top, TITLE, palette.text());
		top += 31;
		String tools = SetupGroup.world() == null ? "" : ", with " + SetupGroup.world().shortName() + " tools when you need them";
		kit.centered("A cleaner, more beautiful Minecraft" + tools + ".", middle, top + 4, INTRO, false, palette.textSecondary());
		kit.centered("Set it up in about a minute.", middle, top + 16, INTRO, false, palette.textSecondary());
		top += 37;

		// what the mod does, in three cards
		int inner = WIDTH - 2 * PAD;
		List<Point> points = points();
		int cardWidth = (inner - (points.size() - 1) * CARD_GAP) / points.size();
		for (int i = 0; i < points.size(); i++) {
			Point point = points.get(i);
			int cardX = x + PAD + i * (cardWidth + CARD_GAP);
			kit.box(cardX, top, cardWidth, CARD_HEIGHT, SetupKit.CARD_RADIUS);
			kit.tile(point.icon(), cardX + 10, top + 10, CARD_ICON, point.light(), point.dark());
			ui.textAtScaledBold(kit.fit(point.title(), cardWidth - 20, SetupKit.BODY), cardX + 10, top + 39, SetupKit.BODY, palette.text());
			int lineY = top + 52;
			for (String line : kit.wrap(point.text(), cardWidth - 20, SetupKit.SMALL)) {
				ui.textAtScaled(line, cardX + 10, lineY, SetupKit.SMALL, palette.textSecondary());
				lineY += 10;
			}
		}
		top += CARD_HEIGHT + 17;

		kit.primaryButton("start", "Get started", x + PAD, top, inner, BUTTON_HEIGHT, () -> screen.go(SetupScreen.MENU));
		top += BUTTON_HEIGHT + 11;
		// the two ways to skip, side by side under the button
		String recommended = "Skip and use the recommended setup";
		String off = "Skip and turn everything off";
		float gap = 16f;
		float both = ui.widthScaled(recommended, SetupKit.SMALL) + gap + ui.widthScaled(off, SetupKit.SMALL);
		float linkX = middle - both / 2f;
		linkX += kit.link("skip_recommended", recommended, linkX, top, SetupKit.SMALL, screen::skipRecommended) + gap;
		kit.link("skip_off", off, linkX, top, SetupKit.SMALL, screen::skipEverythingOff);
		kit.centered("Everything can be changed later in the settings.", middle, y + HEIGHT - 18, SetupKit.TINY,
			false, Ui.withOpacity(palette.textSecondary(), 0.7f));
	}
}
