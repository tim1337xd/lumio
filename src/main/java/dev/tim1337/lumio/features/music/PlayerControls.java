package dev.tim1337.lumio.features.music;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.api.experimental.motion.AnimClock;
import dev.tim1337.lumio.api.experimental.motion.Spring;
import dev.tim1337.lumio.mixin.accessor.AbstractContainerScreenAccessor;
import dev.tim1337.lumio.mixin.accessor.ChatComponentAccessor;
import dev.tim1337.lumio.ui.Glyphs;
import dev.tim1337.lumio.ui.HudEditScreen;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.ui.Ui;
import dev.tim1337.lumio.ui.UiSounds;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Mth;

/**
 * The controls of the music players (the small player and the window): previous, play/pause and next as glyphs with hover and press states, the
 * play/pause icon morphing between its two shapes, and the progress bar as a seek bar (hover grows it and shows a knob and the time under the
 * cursor, a click jumps there, dragging scrubs with the lyrics previewing the line at that time, and the song seeks on release).
 * <p>
 * They can only be used while the mouse is free (the chat or another screen is open); while playing normally the player is only shown, so no
 * click ever goes to it by accident, and the controls fade out. In the HUD editor they show as a preview and do nothing. A control the player
 * cannot do (seeking or skipping in some Windows players) is dimmed and says so. Every action goes through {@link MusicService}, which shows it
 * at once and corrects it from the player's next report. Positions are in the player's own units; the player tells where it stands on screen.
 */
final class PlayerControls {
	static final int PREVIOUS = 0;
	static final int PLAY_PAUSE = 1;
	static final int NEXT = 2;
	private static final int BUTTONS = 3;
	private static final MusicService.Action[] ACTIONS = {MusicService.Action.PREVIOUS, MusicService.Action.PLAY_PAUSE, MusicService.Action.NEXT};
	/** How quickly the controls fade in and out, per second. */
	private static final float REVEAL_RATE = Ui.HOVER_RATE;
	/** A pressed button shrinks to this and springs back. */
	private static final float PRESSED_SCALE = 0.84f;
	/** Hover rings: their opacity at rest and when hovered, and how much larger a button's hit area is than its glyph. */
	private static final float RING_REST = 0.0f;
	private static final float RING_HOVER = 0.16f;
	private static final float HIT_SLACK = 3f;
	/** The seek bar: thin at rest, thicker when hovered, the knob's radius, and how far above and below it the mouse still hits it. */
	private static final float BAR_THIN = 3f;
	private static final float BAR_THICK = 5f;
	private static final float KNOB = 4f;
	private static final float BAR_HIT = 6f;
	/** A dimmed control, and the tooltip's room around its text and its distance from the cursor. */
	private static final float DIMMED = 0.35f;
	private static final int TIP_PAD = 4;
	private static final int TIP_HEIGHT = 13;
	private static final int TIP_BELOW = 10;
	private static final int TIP_COLOR = 0xEE14141C;
	/** Room around the chat that belongs to it: beside its lines, and its input line below them. */
	private static final int CHAT_SLACK = 8;
	private static final int CHAT_INPUT = 40;
	private static final String CANNOT_SEEK = "This player doesn't support seeking";
	private static final String CANNOT_SKIP = "This player doesn't support skipping";
	private static final String CANNOT_GO_BACK = "This player doesn't support going back";

	private final Spring[] hover = {Spring.smooth(Spring.SNAPPY), Spring.smooth(Spring.SNAPPY), Spring.smooth(Spring.SNAPPY)};
	private final Spring[] press = {Spring.damped(0.75f, Spring.DEFAULT), Spring.damped(0.75f, Spring.DEFAULT), Spring.damped(0.75f, Spring.DEFAULT)};
	private final Spring morph = Spring.smooth(Spring.DEFAULT);
	private final Spring barHover = Spring.smooth(Spring.SNAPPY);
	private final AnimClock clock = new AnimClock();
	private final float[] buttonX = new float[BUTTONS];
	private float buttonY;
	private float buttonSize;
	private float barX;
	private float barY;
	private float barWidth;
	private boolean barShown;
	/** Where the player stands on screen and its scale, for the mouse in its units. */
	private float originX;
	private float originY;
	private float scale = 1f;
	private float mouseX = Float.NaN;
	private float mouseY = Float.NaN;
	private boolean free;
	private boolean preview;
	private float reveal;
	private boolean scrubbing;
	private float scrubFraction;
	private String tip;
	private float tipX;
	private float tipY;

	/** Starts a frame of the player: where it stands on screen and at which scale, for the mouse in its units. */
	void begin(float x, float y, float playerScale) {
		Minecraft client = Minecraft.getInstance();
		originX = x;
		originY = y;
		scale = Math.max(0.01f, playerScale);
		var screen = client.gui.screen();
		free = screen != null;
		preview = screen instanceof HudEditScreen;
		if (free) {
			var window = client.getWindow();
			double guiX = client.mouseHandler.xpos() * window.getGuiScaledWidth() / Math.max(1, window.getScreenWidth());
			double guiY = client.mouseHandler.ypos() * window.getGuiScaledHeight() / Math.max(1, window.getScreenHeight());
			mouseX = (float) ((guiX - originX) / scale);
			mouseY = (float) ((guiY - originY) / scale);
		} else {
			mouseX = Float.NaN;
			mouseY = Float.NaN;
			scrubbing = false;
		}
		if (scrubbing) {
			scrubFraction = fractionAt(mouseX);
		}
		tip = null;
		barShown = false;
	}

	/**
	 * Fades the controls in or out: in while the mouse is free and the player wants them (the window always, the small player while the mouse is
	 * over it).
	 */
	void reveal(boolean wanted) {
		float dt = clock.tick();
		float target = free && wanted ? 1f : 0f;
		reveal = ModFeatures.MENU.animations() ? reveal + (target - reveal) * (1f - (float) Math.exp(-dt * REVEAL_RATE)) : target;
		if (reveal < 0.002f) {
			reveal = 0f;
		}
	}

	/** @return how far the controls are shown, from 0 to 1 */
	float reveal() {
		return reveal;
	}

	/** @return whether the mouse is over an area of the player, in its units; never while the mouse is not free */
	boolean over(float x, float y, float width, float height) {
		return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
	}

	/** @return whether the song bar is being dragged, so the lyrics preview the line at {@link #scrubMs} */
	boolean scrubbing() {
		return scrubbing;
	}

	/** @return the song time the bar is dragged to */
	long scrubMs() {
		MusicService.Track track = MusicService.track();
		return track == null ? 0L : Math.round(scrubFraction * track.durationMs());
	}

	/** @return the progress to show: where the bar is dragged to while scrubbing, otherwise the song's */
	float shownProgress(float progress) {
		return scrubbing ? scrubFraction : progress;
	}

	/** A media key was pressed: its button animates as if clicked. */
	void pressed(MusicService.Action action) {
		for (int i = 0; i < BUTTONS; i++) {
			if (ACTIONS[i] == action) {
				press[i].snap(1f);
			}
		}
	}

	// ----------------------------------------------------------------------------------------------------- drawing

	/** Draws previous, play/pause and next, centered on a point, as far as they are revealed. */
	void drawButtons(Ui ui, float centerX, float centerY, float gap, float size, float alpha) {
		buttonY = centerY;
		buttonSize = size;
		buttonX[PREVIOUS] = centerX - gap;
		buttonX[PLAY_PAUSE] = centerX;
		buttonX[NEXT] = centerX + gap;
		float shown = alpha * reveal;
		morph.follow(ui, MusicService.isPlaying() ? 1f : 0f);
		for (int i = 0; i < BUTTONS; i++) {
			boolean can = allowed(i);
			boolean on = reveal > 0.5f && overButton(i);
			float lit = hover[i].follow(ui, on ? 1f : 0f);
			float pushed = press[i].follow(ui, 0f);
			float glyphSize = (i == PLAY_PAUSE ? size * 1.15f : size) * (1f - (1f - PRESSED_SCALE) * Mth.clamp(pushed, 0f, 1f));
			if (shown <= 0.004f) {
				continue;
			}
			float ring = (RING_REST + (RING_HOVER - RING_REST) * lit) * (can ? 1f : DIMMED);
			if (ring > 0.004f) {
				float radius = size * 0.62f;
				ui.roundedSmooth(buttonX[i] - radius, centerY - radius, radius * 2f, radius * 2f, radius, Ui.withOpacity(HudStyle.text(), ring * shown));
			}
			int color = Ui.withOpacity(HudStyle.text(), shown * (can ? 0.92f + 0.08f * lit : DIMMED));
			if (i == PLAY_PAUSE) {
				Glyphs.drawMorph(ui, morph.value(), buttonX[i], centerY, glyphSize, color);
			} else {
				Glyphs.draw(ui, i == PREVIOUS ? Glyphs.PREVIOUS : Glyphs.NEXT, buttonX[i], centerY, glyphSize, color);
			}
			if (on && !can) {
				showTip(i == NEXT ? CANNOT_SKIP : CANNOT_GO_BACK, buttonX[i], centerY + size / 2f);
			}
		}
	}

	/**
	 * Draws the song bar: thin at rest; revealed and hovered it grows a little and shows a knob and the time under the cursor; dragged, the time
	 * follows the drag. A player that cannot seek shows it dimmed.
	 *
	 * @param progress the song's progress, from 0 to 1
	 */
	void drawBar(Ui ui, float x, float y, float width, float progress, int accent, float alpha) {
		barX = x;
		barY = y;
		barWidth = width;
		barShown = true;
		boolean can = MusicService.canSeek();
		boolean on = reveal > 0.5f && overBar();
		float grow = barHover.follow(ui, (on || scrubbing) && can ? 1f : 0f) * reveal;
		float thickness = Mth.lerp(grow, BAR_THIN, BAR_THICK);
		float top = y - thickness / 2f;
		float done = Mth.clamp(shownProgress(progress), 0f, 1f);
		float dim = can ? 1f : Mth.lerp(reveal, 1f, DIMMED);
		ui.roundedSmooth(x, top, width, thickness, thickness / 2f, Ui.withOpacity(HudStyle.text(), 0.16f * alpha * dim));
		ui.roundedSmooth(x, top, Math.max(thickness, width * done), thickness, thickness / 2f, Ui.withOpacity(accent, alpha * dim));
		if (grow > 0.02f) {
			float knob = KNOB * grow;
			float knobX = x + width * done;
			ui.roundedSmooth(knobX - knob, y - knob, knob * 2f, knob * 2f, knob, Ui.withOpacity(0xFFFFFFFF, alpha * grow));
			MusicService.Track track = MusicService.track();
			if (track != null && track.durationMs() > 0) {
				float at = scrubbing ? scrubFraction : fractionAt(mouseX);
				float timeX = scrubbing ? knobX : x + width * at;
				showTip(MusicFeature.clock(Math.round(at * track.durationMs())), timeX, y + thickness);
			}
		}
		if (on && !can) {
			showTip(CANNOT_SEEK, Mth.clamp(mouseX, x, x + width), y + thickness);
		}
	}

	/** Draws the tooltip of this frame, if there is one, after the rest of the player so nothing covers it. */
	void drawTip(Ui ui, float alpha) {
		if (tip == null || reveal <= 0.5f) {
			return;
		}
		int width = ui.width(tip) + 2 * TIP_PAD;
		int left = Math.round(tipX - width / 2f);
		int top = Math.round(tipY + TIP_BELOW - TIP_HEIGHT / 2f);
		ui.rounded(left, top, width, TIP_HEIGHT, TIP_HEIGHT / 2f, Ui.withOpacity(TIP_COLOR, alpha * reveal));
		ui.textCentered(tip, Math.round(tipX), top + TIP_HEIGHT / 2, Ui.withOpacity(0xFFFFFFFF, alpha * reveal));
	}

	private void showTip(String text, float x, float y) {
		tip = text;
		tipX = x;
		tipY = y;
	}

	// ------------------------------------------------------------------------------------------------------ mouse

	/**
	 * A click on the screen while the mouse is free. Only a visible, uncovered control takes it (the screen decided that before); the HUD editor
	 * only previews the controls, so nothing happens there.
	 *
	 * @return whether the click was on a control
	 */
	boolean click(double screenX, double screenY) {
		if (!free || preview || reveal <= 0.5f) {
			return false;
		}
		mouseX = (float) ((screenX - originX) / scale);
		mouseY = (float) ((screenY - originY) / scale);
		for (int i = 0; i < BUTTONS; i++) {
			if (overButton(i)) {
				if (allowed(i)) {
					UiSounds.click();
					press[i].snap(1f);
					MusicService.control(ACTIONS[i]);
				}
				return true;
			}
		}
		if (barShown && overBar()) {
			if (MusicService.canSeek() && MusicService.track() != null && MusicService.track().durationMs() > 0) {
				scrubbing = true;
				scrubFraction = fractionAt(mouseX);
			}
			return true;
		}
		return false;
	}

	/** @return whether the release ended a drag of the song bar, which then seeks there */
	boolean release() {
		if (!scrubbing) {
			return false;
		}
		scrubbing = false;
		MusicService.Track track = MusicService.track();
		if (track != null && track.durationMs() > 0) {
			MusicService.seek(Math.round(scrubFraction * track.durationMs()));
		}
		return true;
	}

	private boolean overButton(int i) {
		return buttonSize > 0f && Math.abs(mouseX - buttonX[i]) <= buttonSize / 2f + HIT_SLACK && Math.abs(mouseY - buttonY) <= buttonSize / 2f + HIT_SLACK;
	}

	private boolean overBar() {
		return mouseX >= barX - BAR_HIT && mouseX <= barX + barWidth + BAR_HIT && Math.abs(mouseY - barY) <= BAR_HIT;
	}

	private float fractionAt(float x) {
		return barWidth <= 0f || Float.isNaN(x) ? 0f : Mth.clamp((x - barX) / barWidth, 0f, 1f);
	}

	/**
	 * @return whether the screen has something of its own at a point: a widget, a container's window or a slot, the chat, a window of the menu, or
	 *         anything in the HUD editor. The screen always wins: such a click is the screen's, never the player's.
	 */
	static boolean screenHas(Screen screen, double x, double y) {
		if (screen instanceof HudEditScreen || screen instanceof MacScreen mac && mac.covers(x, y)) {
			return true;
		}
		if (screen instanceof AbstractContainerScreen<?> container) {
			var area = (AbstractContainerScreenAccessor) container;
			int left = area.lumio$left();
			int top = area.lumio$top();
			if (area.lumio$hoveredSlot() != null || x >= left && x < left + area.lumio$width() && y >= top
				&& y < top + area.lumio$height()) {
				return true;
			}
		}
		if (screen instanceof ChatScreen) {
			// the chat's lines and its input line
			var chat = (ChatComponentAccessor) Minecraft.getInstance().gui.hud.getChat();
			double chatScale = chat.lumio$scale();
			if (x <= chat.lumio$width() * chatScale + CHAT_SLACK && y >= screen.height - chat.lumio$height() * chatScale - CHAT_INPUT) {
				return true;
			}
		}
		return screen.getChildAt(x, y).isPresent();
	}

	private static boolean allowed(int button) {
		return switch (button) {
			case PREVIOUS -> MusicService.canPrevious();
			case NEXT -> MusicService.canNext();
			default -> true;
		};
	}
}
