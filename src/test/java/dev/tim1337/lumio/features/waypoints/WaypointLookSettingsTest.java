package dev.tim1337.lumio.features.waypoints;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.api.experimental.waypoint.WaypointLook;
import dev.tim1337.lumio.api.experimental.waypoint.WaypointStyle;
import dev.tim1337.lumio.config.setting.Setting;

import org.junit.jupiter.api.Test;

/** The waypoint look: its defaults are the look waypoints always had, and what each setting changes. */
class WaypointLookSettingsTest {
	private static final WaypointStyle DEFAULT = WaypointStyle.DEFAULT;

	@Test
	void theSettingsDefaultsAreTheOldLook() {
		WaypointLookSettings look = new WaypointLookSettings();
		look.refresh();
		assertEquals(DEFAULT, look.style());
		for (Setting<?> setting : look.settings()) {
			assertTrue(setting.isDefault(), setting.key());
			assertTrue(setting.key().startsWith("look_"), setting.key());
		}
	}

	@Test
	void theDefaultLookDrawsAsBefore() {
		assertEquals(1f, DEFAULT.textSize());
		assertEquals(WaypointStyle.TextScaling.SMALLER_FAR, DEFAULT.textScaling(), "as waypoints always were");
		assertTrue(DEFAULT.showName());
		assertTrue(DEFAULT.showDistance());
		assertTrue(DEFAULT.textShadow());
		assertFalse(DEFAULT.textBackground(), "off by default: each group decides");
		assertEquals(WaypointStyle.Marker.BOX, DEFAULT.marker());
		assertEquals(2f, DEFAULT.lineWidth());
		assertEquals(WaypointLook.BEAM_HEIGHT, DEFAULT.beamHeight());
		assertEquals(0, DEFAULT.maxDistance());
		// the box, the fill and the beam exactly as a group has them
		assertTrue(DEFAULT.boxed());
		assertEquals(0.28f, DEFAULT.fillOpacity(0.28f));
		assertTrue(DEFAULT.beamOn(true));
		assertFalse(DEFAULT.beamOn(false), "a group without a beam gets none");
		assertFalse(DEFAULT.iconLabel());
		// the label: the size it had at every distance, fading out at the point, drawn however far
		for (double distance : new double[] {0, 4, 8, 40, 128, 600}) {
			assertEquals(WaypointLook.labelSize(distance), DEFAULT.labelScale(distance));
			assertTrue(DEFAULT.inReach(distance));
		}
		assertEquals(0f, DEFAULT.nearOpacity(1.0));
		assertEquals(0.5f, DEFAULT.nearOpacity(1.75), 1e-6);
		assertEquals(1f, DEFAULT.nearOpacity(2.5));
	}

	@Test
	void whatTheSettingsChange() {
		WaypointStyle outline = with(WaypointStyle.Marker.OUTLINE, true);
		assertTrue(outline.boxed());
		assertEquals(0f, outline.fillOpacity(0.28f), "the outline alone has no fill");
		WaypointStyle beam = with(WaypointStyle.Marker.BEAM, true);
		assertFalse(beam.boxed());
		assertTrue(beam.beamOn(false), "the beam marker stands on every point");
		assertFalse(with(WaypointStyle.Marker.BEAM, false).beamOn(true), "beams off: none anywhere");
		WaypointStyle icon = with(WaypointStyle.Marker.ICON, true);
		assertFalse(icon.boxed());
		assertFalse(icon.beamOn(true));
		assertTrue(icon.iconLabel());
		// a stronger fill never gets past opaque
		WaypointStyle strong = new WaypointStyle(1f, WaypointStyle.TextScaling.SMALLER_FAR, true, true, true, false, WaypointStyle.Marker.BOX, 4f, 2f, true, 40f, 1f, false, 64, 1f);
		assertEquals(0.56f, strong.fillOpacity(0.28f), 1e-6);
		assertEquals(1f, strong.fillOpacity(0.6f));
		assertEquals(1f, strong.nearOpacity(0.5), "no fading close by");
		assertTrue(strong.inReach(64));
		assertFalse(strong.inReach(64.5));
		// text that shrinks with the distance: full size close by, half at twice the distance, never tiny
		WaypointStyle shrinking = new WaypointStyle(1.5f, WaypointStyle.TextScaling.SIGN, true, true, true, false, WaypointStyle.Marker.BOX, 2f, 1f, true, 80f, 1f, true, 0, 1f);
		assertEquals(1.5f, shrinking.labelScale(3));
		assertEquals(1.5f, shrinking.labelScale(8));
		assertEquals(0.75f, shrinking.labelScale(16), 1e-6);
		assertEquals(1.5f * 0.2f, shrinking.labelScale(500), 1e-6);
		// truly steady: the same size at any distance, and the text size scales it as it does the others
		WaypointStyle steady = new WaypointStyle(1.5f, WaypointStyle.TextScaling.STEADY, true, true, true, false, WaypointStyle.Marker.BOX, 2f, 1f, true, 80f, 1f, true, 0, 1f);
		for (double distance : new double[] {0, 8, 64, 128, 900}) {
			assertEquals(1.5f, steady.labelScale(distance));
		}
		// slightly smaller far away: full to 8 blocks, down to about 65 % at 128
		WaypointStyle smaller = new WaypointStyle(2f, WaypointStyle.TextScaling.SMALLER_FAR, true, true, true, false, WaypointStyle.Marker.BOX, 2f, 1f, true, 80f, 1f, true, 0, 1f);
		assertEquals(2f, smaller.labelScale(8));
		assertEquals(2f * 0.65f, smaller.labelScale(128), 1e-6);
		assertEquals(2f * 0.65f, smaller.labelScale(4000), 1e-6);
	}

	@Test
	void aKindOfWaypointMayHaveItsOwnLook() {
		WaypointStyle own = with(WaypointStyle.Marker.OUTLINE, false);
		assertSame(DEFAULT, WaypointStyle.pick(true, DEFAULT, own), "the general look while its switch is on");
		assertSame(own, WaypointStyle.pick(false, DEFAULT, own));
		assertSame(DEFAULT, WaypointStyle.pick(false, DEFAULT, null));
	}

	private static WaypointStyle with(WaypointStyle.Marker marker, boolean beam) {
		return new WaypointStyle(1f, WaypointStyle.TextScaling.SMALLER_FAR, true, true, true, false, marker, 2f, 1f, beam, 80f, 1f, true, 0, 1f);
	}
}
