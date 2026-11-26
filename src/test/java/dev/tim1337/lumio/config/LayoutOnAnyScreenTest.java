package dev.tim1337.lumio.config;

import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.ui.EditableHud;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A config or preset made on another screen keeps the HUD's places and sizes in GUI units: a panel 8 units from its edge stays 8 units from it, and
 * no size is stretched (a preset made at 480x270 used to double every panel at 960x540). Only the music window, which has no anchor, moves with the
 * screen.
 */
class LayoutOnAnyScreenTest {
	private static final String ID = "layout_screen_test_panel";
	private static final float EPS = 0.001f;

	private static FloatSetting x;
	private static FloatSetting y;
	private static FloatSetting scale;
	private static FloatSetting windowX;

	/** A HUD element as a config stores it: a place from its anchor, a size, and an unanchored window place. */
	private static final class Panel extends Feature implements EditableHud {
		Panel() {
			super(ID, "Panel", "", true);
			x = add(new FloatSetting("x", Component.literal("Place across"), 0f, -4000f, 4000f, 0));
			y = add(new FloatSetting("y", Component.literal("Place down"), 0f, -4000f, 8000f, 0));
			scale = add(new FloatSetting("scale", Component.literal("Size"), 1f, 0.3f, 3f, 2));
			windowX = add(new FloatSetting("window_x", Component.literal("Window"), 0f, -4000f, 4000f, 0));
		}

		@Override
		public String hudName() {
			return "Panel";
		}

		@Override
		public boolean hudShown() {
			return true;
		}

		@Override
		public int[] hudBounds() {
			return new int[4];
		}

		@Override
		public void hudMove(int dx, int dy) {
		}

		@Override
		public float hudScale() {
			return scale.get();
		}

		@Override
		public void hudSetScale(float value) {
			scale.set(value);
		}

		@Override
		public void hudReset() {
		}
	}

	@BeforeAll
	static void register() {
		FeatureManager.register(new Panel());
	}

	@AfterEach
	void noScreen() {
		FeatureManagerAccess.screen(null);
	}

	private static void applyMadeAt(int width, int height) {
		String text = "{\"_screen\":{\"w\":" + width + ",\"h\":" + height + "},\"" + ID + "\":{\"x\":8,\"y\":10,\"scale\":0.85,\"window_x\":100}}";
		FeatureManagerAccess.apply(text, Set.of(ConfigPart.LAYOUT), feature -> feature.id().equals(ID));
	}

	@Test
	void aPresetFromASmallScreenKeepsItsSizesAndDistancesOnALargeOne() {
		// made at 1080p and GUI scale 4 (480x270), applied at 1440p and GUI scale 2 (1280x720)
		FeatureManagerAccess.screen(new int[]{1280, 720});
		applyMadeAt(480, 270);
		assertEquals(8f, x.get(), EPS);
		assertEquals(10f, y.get(), EPS);
		assertEquals(0.85f, scale.get(), EPS);
	}

	@Test
	void aConfigFromALargeScreenIsNotShrunkWhenApplied() {
		// the auto-fit shrinks what does not fit while it is drawn, and lets it grow back when there is room: nothing is lost in the config
		FeatureManagerAccess.screen(new int[]{320, 180});
		applyMadeAt(960, 540);
		assertEquals(0.85f, scale.get(), EPS);
		assertEquals(8f, x.get(), EPS);
	}

	@Test
	void theUnanchoredMusicWindowMovesWithTheScreen() {
		FeatureManagerAccess.screen(new int[]{960, 540});
		applyMadeAt(480, 270);
		assertEquals(200f, windowX.get(), EPS);
	}

	@Test
	void theSameScreenChangesNothing() {
		FeatureManagerAccess.screen(new int[]{480, 270});
		applyMadeAt(480, 270);
		assertEquals(100f, windowX.get(), EPS);
		assertEquals(0.85f, scale.get(), EPS);
	}
}
