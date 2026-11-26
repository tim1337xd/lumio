package dev.tim1337.lumio.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Only the elements the open chat lies over are dimmed, never hidden, and they return when it closes. */
class HudUnderChatTest {
	private static final int[] CHAT = {4, 300, 440, 240};
	private static final long SECOND = 1_000_000_000L;

	@Test
	void onlyOverlappedElementsCount() {
		assertTrue(HudUnderChat.under(new int[] {389, 500, 182, 22}, CHAT), "the hotbar's left end is under the chat");
		assertFalse(HudUnderChat.under(new int[] {444, 500, 182, 22}, CHAT), "beside the chat");
		assertFalse(HudUnderChat.under(new int[] {100, 100, 80, 40}, CHAT), "above the chat");
		assertFalse(HudUnderChat.under(new int[] {389, 500, 182, 22}, null), "the chat is closed");
		assertFalse(HudUnderChat.under(new int[] {389, 500, 0, 0}, CHAT), "an element not shown");
	}

	@Test
	void anElementDimsToAboutAThirdAndReturns() {
		HudUnderChat.Fade fade = new HudUnderChat.Fade();
		assertEquals(1f, fade.step(false, true, SECOND));
		float partly = fade.step(true, true, SECOND + SECOND / 20);
		assertTrue(partly < 1f && partly > HudUnderChat.DIMMED, "on its way");
		assertEquals(HudUnderChat.DIMMED, fade.step(true, true, 2 * SECOND), 1e-6f);
		assertEquals(HudUnderChat.DIMMED, fade.step(true, true, 3 * SECOND), 1e-6f, "never less: it is not hidden");
		assertEquals(1f, fade.step(false, true, 4 * SECOND), 1e-6f, "back when the chat closes");
		assertEquals(HudUnderChat.DIMMED, fade.step(true, false, 4 * SECOND + 1), 1e-6f, "at once without animations");
	}

	@Test
	void theShadeFollowsTheDimming() {
		assertEquals(0f, HudUnderChat.shade(1f), 1e-6f);
		assertEquals(HudUnderChat.SHADE, HudUnderChat.shade(HudUnderChat.DIMMED), 1e-6f);
	}
}
