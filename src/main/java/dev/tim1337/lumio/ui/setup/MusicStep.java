package dev.tim1337.lumio.ui.setup;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.features.music.MusicFeature;
import dev.tim1337.lumio.features.music.MusicService;
import dev.tim1337.lumio.features.music.SourceSetupScreen;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.client.input.KeyEvent;

/**
 * Step four: where the music comes from: any player on Windows, Cider, a Spotify account, or none. The choice applies
 * at once, so the source card can say whether it found a song and the preview shows the real song. A click on Cider leads to its token in this step
 * ({@link CiderSetupPanel}); Spotify's sign-in, with its browser and its own app, opens the mod's guide and comes back here.
 */
final class MusicStep implements SetupStep {
	private static final int GAP = 16;
	/** The left column's share of the width, as in the mockup (1.35 to 1). */
	private static final float LEFT_SHARE = 1.35f / 2.35f;
	private static final int CARD_HEIGHT = 37;
	private static final int CARD_GAP = 6;
	private static final int TILE = 26;
	private static final int LABEL_GAP = 12;
	private static final int SECTION_GAP = 12;

	/** A source card. */
	private enum Source {
		WINDOWS, CIDER, SPOTIFY, NONE
	}

	private final SetupScreen screen;
	private final CiderSetupPanel ciderPanel = new CiderSetupPanel(this::ciderConnected, this::closeCider);
	/** Whether the token entry of Cider shows in place of the cards, and whether it found a working token. */
	private boolean ciderOpen;
	private boolean ciderWorks;

	MusicStep(SetupScreen screen) {
		this.screen = screen;
	}

	private void openCider() {
		ciderPanel.open();
		ciderOpen = true;
	}

	private void closeCider() {
		ciderOpen = false;
	}

	private void ciderConnected() {
		ciderWorks = true;
		ciderOpen = false;
	}

	@Override
	public void shown() {
		ciderOpen = false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return ciderOpen && ciderPanel.keyPressed(event);
	}

	@Override
	public boolean charTyped(int codepoint) {
		return ciderOpen && ciderPanel.charTyped(codepoint);
	}

	@Override
	public String name() {
		return "Music";
	}

	/** @return the source card in use: none while the music is off, Windows also for the automatic choice */
	static Source current() {
		MusicFeature music = ModFeatures.MUSIC;
		if (!music.isEnabled()) {
			return Source.NONE;
		}
		return switch (music.player()) {
			case CIDER -> Source.CIDER;
			case SPOTIFY -> Source.SPOTIFY;
			default -> Source.WINDOWS;
		};
	}

	/** @return the music as the summary of the last step says it, such as "Windows player" */
	static String summary() {
		return switch (current()) {
			case NONE -> "No music";
			case CIDER -> "Cider";
			case SPOTIFY -> "Spotify account";
			case WINDOWS -> "Windows player";
		};
	}

	private static void choose(Source source) {
		MusicFeature music = ModFeatures.MUSIC;
		if (source == Source.NONE) {
			music.setEnabled(false);
			return;
		}
		music.setEnabled(true);
		music.setPlayer(switch (source) {
			case CIDER -> MusicFeature.Player.CIDER;
			case SPOTIFY -> MusicFeature.Player.SPOTIFY;
			default -> MusicFeature.Player.WINDOWS;
		});
	}

	@Override
	public void draw(SetupKit kit, int x, int y, int width, int height, boolean wide) {
		MusicFeature music = ModFeatures.MUSIC;
		int left = wide ? Math.round((width - GAP) * LEFT_SHARE) : width;
		if (ciderOpen) {
			ciderPanel.draw(kit, x, y, left, height);
			drawPreview(kit, x, y, width, height, left, wide, true);
			return;
		}
		int top = kit.heading("Music while you play", "See what is playing, with synced lyrics. Optional.", x, y, left) + SECTION_GAP;
		kit.label("WHERE YOUR MUSIC COMES FROM", x, top);
		top += LABEL_GAP;
		Source current = current();

		// any player on Windows: says whether something plays right now
		String windowsStatus = !MusicFeature.onWindows() ? "Windows only"
			: MusicFeature.playsFrom(MusicFeature.Player.WINDOWS) ? "Found a song" : "Nothing playing";
		boolean windowsFound = MusicFeature.onWindows() && MusicFeature.playsFrom(MusicFeature.Player.WINDOWS);
		drawSource(kit, Source.WINDOWS, x, top, left, "window", 0x8E8E93, 0x636366, "Any player on Windows", "Spotify app, browser, Apple Music and more",
			current == Source.WINDOWS ? windowsStatus : null, windowsFound, null, null);
		top += CARD_HEIGHT + CARD_GAP;

		// Cider: the card leads to its token, in this step; it only becomes the source once a token works
		boolean ciderConnected = ciderWorks || MusicFeature.playsFrom(MusicFeature.Player.CIDER);
		String ciderStatus = ciderConnected ? "Connected" : music.ciderNeedsToken() ? "Needs a token" : "Waiting for Cider";
		drawSource(kit, Source.CIDER, x, top, left, "note", 0xFF375F, 0xFF2D55, "Cider", "For Apple Music, with exact timing",
			current == Source.CIDER ? ciderStatus : null, ciderConnected, "Connect", this::openCider);
		top += CARD_HEIGHT + CARD_GAP;

		// a Spotify account: Sign in opens the guide, which comes back here
		boolean signedIn = MusicService.spotifyConnected();
		String spotifyStatus = MusicFeature.playsFrom(MusicFeature.Player.SPOTIFY) ? "Found a song" : signedIn ? "Signed in" : null;
		drawSource(kit, Source.SPOTIFY, x, top, left, "play", 0x30D158, 0x1DB954, "Spotify account", "Sign in for covers and full song info",
			current == Source.SPOTIFY ? spotifyStatus : null, signedIn, signedIn ? null : "Sign in", () -> {
				// signed in, it only needs choosing; otherwise the guide chooses it once the sign-in is done, so leaving the guide changes nothing
				if (signedIn) {
					choose(Source.SPOTIFY);
				} else {
					screen.open(SourceSetupScreen.spotify(screen));
				}
			});
		top += CARD_HEIGHT + CARD_GAP;

		drawSource(kit, Source.NONE, x, top, left, "close", 0x48484A, 0x2C2C2E, "No music", "You can turn it on later", null, false,
			current == Source.NONE ? null : "Choose", () -> choose(Source.NONE));
		drawPreview(kit, x, y, width, height, left, wide, current != Source.NONE);
	}

	private static void drawPreview(SetupKit kit, int x, int y, int width, int height, int left, boolean wide, boolean musicOn) {
		if (wide) {
			int previewX = x + left + GAP;
			kit.label("PREVIEW", previewX, y);
			SetupPreview.music(kit, previewX, y + LABEL_GAP, width - left - GAP, height - LABEL_GAP, musicOn);
		}
	}

	/**
	 * A source as a card: chosen with the accent ring, with what it found right now (green when it works), or a button such as "Connect".
	 *
	 * @param status what the source found, shown on the chosen card; null for none
	 * @param button the text of the card's button, or null for none
	 */
	private static void drawSource(SetupKit kit, Source source, int x, int y, int width, String icon, int light, int dark, String name, String text,
								   String status, boolean good, String button, Runnable action) {
		Ui ui = kit.ui;
		Palette palette = ui.palette;
		Runnable pick = action != null ? action : () -> choose(source);
		float hover = kit.area("source_" + source.name(), x, y, width, CARD_HEIGHT, SetupKit.CARD_RADIUS, pick);
		float chosen = kit.follow("source_on_" + source.name(), current() == source ? 1f : 0f);
		kit.card(x, y, width, CARD_HEIGHT, chosen, hover);
		kit.tile(icon, x + 9, y + (CARD_HEIGHT - TILE) / 2, TILE, light, dark);
		int textX = x + 9 + TILE + 8;
		int right = x + width - 9;
		if (status != null) {
			int color = good ? Ui.mix(SystemColors.GREEN, palette.light() ? 0xFF000000 : 0xFFFFFFFF, 0.35f)
				: status.startsWith("Needs") ? SystemColors.ORANGE : Ui.withOpacity(palette.textSecondary(), 0.8f);
			float statusWidth = ui.boldWidthScaled(status, SetupKit.SMALL);
			ui.textAtScaledBold(status, right - statusWidth, y + CARD_HEIGHT / 2f - 3.5f, SetupKit.SMALL, color);
			if (good) {
				ui.roundedSmooth(right - statusWidth - 9, y + CARD_HEIGHT / 2f - 2.5f, 5, 5, 2.5f, SystemColors.GREEN);
				right -= 9;
			}
			right -= Math.round(statusWidth) + 8;
		} else if (button != null) {
			int buttonWidth = kit.chipWidth(button);
			kit.chipButton("source_button_" + source.name(), button, right - buttonWidth, y + (CARD_HEIGHT - 19) / 2, buttonWidth, 19, pick);
			right -= buttonWidth + 8;
		}
		int room = right - textX;
		ui.textAtScaledBold(kit.fit(name, room, SetupKit.BODY), textX, y + 8, SetupKit.BODY, palette.text());
		ui.textAtScaled(kit.fit(text, room, SetupKit.SMALL), textX, y + 20, SetupKit.SMALL, palette.textSecondary());
	}

}
