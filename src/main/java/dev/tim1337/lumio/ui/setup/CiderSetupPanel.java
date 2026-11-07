package dev.tim1337.lumio.ui.setup;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;
import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.features.music.CiderConnect;
import dev.tim1337.lumio.features.music.MusicService.CiderCheck;
import dev.tim1337.lumio.features.music.MusicService.CiderState;
import dev.tim1337.lumio.ui.Palette;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;

/**
 * Connecting Cider inside the music step: is Cider running, where the token is, the token, and its test, the same steps as the guide on the Music page
 * but in the setup's own window. A working token closes the panel and the source card says "Connected"; a wrong one is said in plain words and the
 * panel stays. It can always be left: the setup goes on without Cider. The token is masked, stored only with the music's settings once it works,
 * and never logged.
 */
final class CiderSetupPanel {
	private static final int SECTION_GAP = 16;
	private static final int FIELD_HEIGHT = 22;
	private static final int BUTTON_HEIGHT = 22;
	private static final int BUTTON_GAP = 6;
	private static final int ROW = 12;
	private static final int EYE = 12;
	private static final int EYE_ROOM = 22;
	private static final int FIELD_PAD = 8;
	private static final int STATUS_DOT = 3;
	private static final int LOCK = 9;
	private static final int MOST_TOKEN_CHARS = 128;
	private static final float CARET_PERIOD_SECONDS = 1f;
	private static final float CARET_SHOWN_SECONDS = 0.55f;
	private static final float MOST_FRAME_SECONDS = 0.1f;
	private static final String BULLETS = "•".repeat(MOST_TOKEN_CHARS);

	private final Runnable onConnected;
	private final Runnable onLeave;
	private CiderConnect connect = new CiderConnect();
	private String token = "";
	private boolean focused;
	private boolean reveal;
	private float time;
	private long last;

	CiderSetupPanel(Runnable onConnected, Runnable onLeave) {
		this.onConnected = onConnected;
		this.onLeave = onLeave;
	}

	/** Starts again with the token that is set; one that is set is tested at once. */
	void open() {
		connect = new CiderConnect();
		token = ModFeatures.MUSIC.ciderToken();
		reveal = false;
		focused = true;
		last = System.nanoTime();
		if (!token.isBlank()) {
			connect.test(token);
		}
	}

	private void advance() {
		long now = System.nanoTime();
		float dt = Math.min(MOST_FRAME_SECONDS, (now - last) / 1_000_000_000f);
		last = now;
		time += dt;
		CiderCheck answer = connect.advance(dt);
		if (answer != null) {
			if (answer.state() == CiderState.CONNECTED) {
				connected();
			} else {
				UiSounds.play(UiSounds.Cue.FAIL);
			}
		} else if (token.isBlank() && connect.needsNoToken()) {
			// Cider with its token setting off: there is nothing to ask for
			connected();
		}
	}

	private void connected() {
		ModFeatures.MUSIC.useCider(token.strip());
		UiSounds.play(UiSounds.Cue.DONE);
		onConnected.run();
	}

	private void check() {
		if (!token.isBlank() && !connect.testing()) {
			connect.test(token);
		}
	}

	private void paste() {
		String text = Minecraft.getInstance().keyboardHandler.getClipboard();
		token = text == null ? "" : text.strip();
		if (token.length() > MOST_TOKEN_CHARS) {
			token = token.substring(0, MOST_TOKEN_CHARS);
		}
		focused = true;
		check();
	}

	private void tryAgain() {
		connect.lookNow();
		check();
	}

	/** Draws the panel into the step's left column. */
	void draw(SetupKit kit, int x, int y, int width, int height) {
		advance();
		Ui ui = kit.ui;
		Palette palette = ui.palette;
		int top = kit.heading("Connect Cider", "Apple Music through Cider, with exact timing.", x, y, width) + SECTION_GAP;

		top = drawStatus(kit, x, top, width) + SECTION_GAP;

		kit.label("WHERE THE TOKEN IS", x, top);
		top += ROW;
		ui.textAtScaled(kit.fit("In Cider: Settings › Connectivity. Create a token and copy it.", width, SetupKit.BODY), x, top, SetupKit.BODY, palette.text());
		top += ROW + SECTION_GAP;

		kit.label("CIDER TOKEN", x, top);
		top += ROW;
		drawField(kit, x, top, width);
		top += FIELD_HEIGHT + BUTTON_GAP;

		int pasteWidth = kit.chipWidth("Paste") + 12;
		kit.secondaryButton("cider_paste", "Paste", x, top, pasteWidth, BUTTON_HEIGHT, this::paste);
		int checkWidth = kit.chipWidth("Check") + 12;
		if (!token.isBlank() && !connect.testing()) {
			kit.primaryButton("cider_check", "Check", x + pasteWidth + BUTTON_GAP, top, checkWidth, BUTTON_HEIGHT, this::check);
		}
		top += BUTTON_HEIGHT + BUTTON_GAP;
		drawAnswer(kit, x, top, width);

		// at the bottom: how to leave, and what happens with the token
		int bottom = y + height;
		kit.link("cider_later", "Skip for now: connect it later on the Music page", x, bottom - ROW, SetupKit.SMALL, onLeave);
		ui.icon("lock", x, bottom - 2 * ROW - 2, LOCK, palette.textSecondary());
		ui.textAtScaled(kit.fit("Stored only on this computer, never in shared configs.", width - LOCK - 5, SetupKit.TINY), x + LOCK + 5, bottom - 2 * ROW - 1,
			SetupKit.TINY, palette.textSecondary());
	}

	/** Whether Cider is running: looking for it, a green dot, or that it isn't, with "Try again". @return the y below it */
	private int drawStatus(SetupKit kit, int x, int y, int width) {
		Ui ui = kit.ui;
		Palette palette = ui.palette;
		int middle = y + ROW / 2;
		if (connect.found()) {
			ui.discAt(x + STATUS_DOT, middle, 2 * STATUS_DOT, SystemColors.GREEN);
			ui.textAtScaled("Cider is running", x + 2 * STATUS_DOT + 6, y + 2, SetupKit.BODY, palette.text());
			return y + ROW;
		}
		if (!connect.answered()) {
			spinner(ui, x + 5, middle);
			ui.textAtScaled("Looking for Cider…", x + 16, y + 2, SetupKit.BODY, palette.textSecondary());
			return y + ROW;
		}
		ui.discAt(x + STATUS_DOT, middle, 2 * STATUS_DOT, SystemColors.ORANGE);
		String again = "Try again";
		int againWidth = kit.chipWidth(again);
		ui.textAtScaled(kit.fit("Cider isn't running. Start it first.", width - againWidth - 2 * STATUS_DOT - 14, SetupKit.BODY), x + 2 * STATUS_DOT + 6, y + 2, SetupKit.BODY,
			palette.text());
		kit.chipButton("cider_again", again, x + width - againWidth, y - 3, againWidth, ROW + 6, this::tryAgain);
		return y + ROW;
	}

	/** The token field, masked unless shown with the eye; a click focuses it, Ctrl+V pastes. */
	private void drawField(SetupKit kit, int x, int y, int width) {
		Ui ui = kit.ui;
		Palette palette = ui.palette;
		boolean wrong = wrongToken();
		kit.area("cider_field", x, y, width - EYE_ROOM, FIELD_HEIGHT, Radius.MD, () -> focused = true);
		kit.area("cider_eye", x + width - EYE_ROOM, y, EYE_ROOM, FIELD_HEIGHT, Radius.MD, () -> reveal = !reveal);
		ui.rounded(x, y, width, FIELD_HEIGHT, Radius.MD, Ui.withOpacity(palette.text(), 0.06f));
		ui.ring(x, y, width, FIELD_HEIGHT, Radius.MD, wrong ? 2 : 1, wrong ? SystemColors.RED : focused ? ui.accent : Ui.withOpacity(palette.text(), 0.16f));
		float textY = y + FIELD_HEIGHT / 2f - 4f;
		String shown = kit.fit(reveal ? token : BULLETS.substring(0, Math.min(token.length(), MOST_TOKEN_CHARS)), width - EYE_ROOM - 2 * FIELD_PAD, SetupKit.BODY);
		if (token.isEmpty() && !focused) {
			ui.textAtScaled("Paste it here (Ctrl+V)", x + FIELD_PAD, textY, SetupKit.BODY, palette.textSecondary());
		} else {
			ui.textAtScaled(shown, x + FIELD_PAD, textY, SetupKit.BODY, palette.text());
		}
		if (focused && time % CARET_PERIOD_SECONDS < CARET_SHOWN_SECONDS) {
			ui.fill(Math.round(x + FIELD_PAD + ui.widthScaled(shown, SetupKit.BODY) + 1), y + 6, 1, FIELD_HEIGHT - 12, palette.text());
		}
		ui.icon("eye", x + width - EYE_ROOM + (EYE_ROOM - EYE) / 2, y + (FIELD_HEIGHT - EYE) / 2, EYE, reveal ? ui.accent : palette.textSecondary());
	}

	/** @return whether Cider, running, turned the token down; while it is closed the status above says so instead */
	private boolean wrongToken() {
		return connect.found() && connect.tested() != null && connect.tested().state() == CiderState.NEEDS_TOKEN;
	}

	/** What the check found: nothing yet, testing, or a short error. */
	private void drawAnswer(SetupKit kit, int x, int y, int width) {
		Ui ui = kit.ui;
		if (connect.testing()) {
			spinner(ui, x + 5, y + ROW / 2);
			ui.textAtScaled("Checking…", x + 16, y + 2, SetupKit.BODY, ui.palette.textSecondary());
		} else if (wrongToken()) {
			int color = ui.palette.light() ? SystemColors.RED : Ui.mix(SystemColors.RED_TEXT, 0xFFFFFFFF, 0.3f);
			ui.textAtScaled(kit.fit("Cider didn't accept this token. Copy it again.", width, SetupKit.BODY), x, y + 2, SetupKit.BODY, color);
		}
	}

	/** A calm spinner: eight dots going round, the leading one brightest. */
	private void spinner(Ui ui, int x, int y) {
		for (int i = 0; i < 8; i++) {
			double angle = i * Math.PI / 4.0;
			float lead = ((time * 1.2f - i / 8f) % 1f + 1f) % 1f;
			ui.discAt(x + (float) Math.cos(angle) * 4f, y + (float) Math.sin(angle) * 4f, 2f, Ui.withOpacity(ui.palette.text(), 0.2f + 0.8f * (1f - lead)));
		}
	}

	/** @return whether the panel used the key */
	boolean keyPressed(KeyEvent event) {
		int key = event.key();
		boolean control = (ScreenInput.modifiers(event.modifiers()) & ScreenInput.CONTROL) != 0;
		switch (key) {
			case InputConstants.KEY_ESCAPE -> {
				onLeave.run();
				return true;
			}
			case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
				check();
				return true;
			}
			case InputConstants.KEY_V -> {
				if (control) {
					paste();
					return true;
				}
				return false;
			}
			case InputConstants.KEY_BACKSPACE -> {
				if (focused && !token.isEmpty()) {
					token = token.substring(0, token.length() - 1);
					connect.forgetTest();
				}
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	/** @return whether the panel took the typed character */
	boolean charTyped(int codepoint) {
		if (!focused || codepoint < ' ' || Character.isWhitespace(codepoint)) {
			return false;
		}
		if (token.length() < MOST_TOKEN_CHARS) {
			token += new String(Character.toChars(codepoint));
			connect.forgetTest();
		}
		return true;
	}
}
