package dev.tim1337.lumio.hud;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The auto-fit: shrinking to a share of the screen, and moving panels off the ones of higher priority. */
class HudFitTest {
	private static final float EPS = 0.01f;

	@Test
	void anElementThatFitsKeepsItsSize() {
		assertEquals(1f, HudFit.shrink(200, 40, 960, 540, HudFit.INFO_BAR_WIDTH_SHARE, 1f), EPS);
	}

	@Test
	void aWideInfoBarShrinksToNinetyPercentOfTheWidth() {
		// 600 wide on a 480 wide screen: 432 is 90 %
		assertEquals(432f / 600f, HudFit.shrink(600, 20, 480, 270, HudFit.INFO_BAR_WIDTH_SHARE, 1f), EPS);
	}

	@Test
	void aTallPanelShrinksToFortyFivePercentOfTheHeight() {
		// a scoreboard of 15 rows, 160 tall, on a 180 tall screen (720p at GUI scale 4): 81 is 45 %
		assertEquals(81f / 160f, HudFit.shrink(100, 160, 320, 180, HudFit.PANEL_WIDTH_SHARE, HudFit.PANEL_HEIGHT_SHARE), EPS);
	}

	@Test
	void itNeverGrowsAndStopsAtTheLeast() {
		assertEquals(1f, HudFit.shrink(10, 10, 3840, 2160, 0.9f, 0.45f), EPS);
		assertEquals(HudFit.LEAST, HudFit.shrink(10000, 10000, 320, 180, 0.9f, 0.45f), EPS);
	}

	@Test
	void panelsMoveAlongTheEdgeTheyAreAnchoredAt() {
		assertEquals(HudFit.ALONG_Y, HudFit.along(HudAnchors.END, HudAnchors.MIDDLE));
		assertEquals(HudFit.ALONG_X, HudFit.along(HudAnchors.MIDDLE, HudAnchors.START));
		assertEquals(HudFit.ALONG_BOTH, HudFit.along(HudAnchors.START, HudAnchors.END));
		assertEquals(HudFit.ALONG_Y, HudFit.along(HudAnchors.MIDDLE, HudAnchors.MIDDLE));
	}

	@Test
	void boxesThatDoNotOverlapStayWhereTheyAre() {
		float[] boxes = {100, 200, 182, 22, 300, 10, 80, 60};
		float[] shifts = new float[4];
		HudFit.resolve(boxes, new int[]{0, HudFit.ALONG_Y}, 2, 480, 270, shifts);
		assertEquals(0f, shifts[2], EPS);
		assertEquals(0f, shifts[3], EPS);
	}

	@Test
	void theScoreboardMovesUpOffTheHotbar() {
		// the hotbar at the bottom center (fixed), the scoreboard on the right, sliding down over it: it goes up along the right edge
		float[] boxes = {150, 240, 182, 22, 300, 200, 80, 60};
		float[] shifts = new float[4];
		HudFit.resolve(boxes, new int[]{0, HudFit.ALONG_Y}, 2, 480, 270, shifts);
		assertEquals(0f, shifts[2], EPS);
		assertEquals(240 - HudFit.MARGIN - 60 - 200, shifts[3], EPS);
	}

	@Test
	void aPanelGoesBesideAHigherOneAlongItsEdge() {
		// a panel at the top center (moves across) overlapping the info bar's right end: it goes right of it
		float[] boxes = {100, 0, 200, 20, 250, 5, 60, 20};
		float[] shifts = new float[4];
		HudFit.resolve(boxes, new int[]{0, HudFit.ALONG_X}, 2, 480, 270, shifts);
		assertEquals(300 + HudFit.MARGIN - 250, shifts[2], EPS);
		assertEquals(0f, shifts[3], EPS);
	}

	@Test
	void theLowerPriorityPanelMovesNotTheHigher() {
		// two movable panels at the same place: the first (higher priority) stays, the second moves below it
		float[] boxes = {10, 10, 80, 40, 10, 20, 80, 40};
		float[] shifts = new float[4];
		HudFit.resolve(boxes, new int[]{HudFit.ALONG_Y, HudFit.ALONG_Y}, 2, 480, 270, shifts);
		assertEquals(0f, shifts[1], EPS);
		assertEquals(50 + HudFit.MARGIN - 20, shifts[3], EPS);
	}

	@Test
	void aPanelOffTheScreenComesBackOnIt() {
		// a panel placed for a larger screen: its left at 400 on a 320 wide screen
		float[] boxes = {400, 50, 80, 40};
		float[] shifts = new float[2];
		HudFit.resolve(boxes, new int[]{HudFit.ALONG_Y}, 1, 320, 180, shifts);
		assertEquals(320 - HudFit.MARGIN - 80 - 400, shifts[0], EPS);
		assertEquals(0f, shifts[1], EPS);
	}

	@Test
	void aCornerPanelTriesItsSideEdgeFirstThenTheBottomEdge() {
		// a panel in the bottom right corner, a wall of fixed boxes above it up to the top: up is no way, so it goes left along the bottom
		float[] boxes = {380, 0, 100, 240, 400, 230, 70, 30};
		float[] shifts = new float[4];
		HudFit.resolve(boxes, new int[]{0, HudFit.ALONG_BOTH}, 2, 480, 270, shifts);
		assertEquals(380 - HudFit.MARGIN - 70 - 400, shifts[2], EPS);
		assertEquals(0f, shifts[3], EPS);
	}

	@Test
	void withNoFreePlaceItStaysOnTheScreen() {
		// a fixed box fills the whole screen
		float[] boxes = {0, 0, 480, 270, 100, 100, 50, 50};
		float[] shifts = new float[4];
		HudFit.resolve(boxes, new int[]{0, HudFit.ALONG_BOTH}, 2, 480, 270, shifts);
		assertEquals(0f, shifts[2], EPS);
		assertEquals(0f, shifts[3], EPS);
	}

	@Test
	void aChatOverTheHotbarStaysAndMovesNothingBesideTheHotbar() {
		// the hotbar (fixed), a chat reaching down over it, a panel beside the hotbar that the chat also reaches, and a panel above that it reaches
		float[] boxes = {150, 248, 182, 22, 2, 150, 320, 110, 336, 240, 60, 28, 200, 100, 80, 60};
		int[] axes = {0, HudFit.ALONG_Y, HudFit.ALONG_Y, HudFit.ALONG_Y};
		float[] shifts = new float[8];
		HudFit.resolve(boxes, axes, new int[]{HudFit.UNDER, HudFit.OVER, HudFit.UNDER, 0}, 4, 480, 270, shifts);
		assertEquals(0f, shifts[3], EPS);
		assertEquals(0f, shifts[5], EPS);
		// a panel that is not the hotbar's still keeps off the chat
		assertEquals(150 - HudFit.MARGIN - 60 - 100, shifts[7], EPS);
	}

	@Test
	void withoutKindsTheChatMovesUpOffTheHotbarAsBefore() {
		float[] boxes = {150, 248, 182, 22, 2, 150, 320, 110};
		float[] shifts = new float[4];
		HudFit.resolve(boxes, new int[]{0, HudFit.ALONG_Y}, null, 2, 480, 270, shifts);
		assertEquals(248 - HudFit.MARGIN - 110 - 150, shifts[3], EPS);
	}
}
