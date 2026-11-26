package dev.tim1337.lumio.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Disable everything" and its Undo: everything that was on goes off, and Undo puts back exactly what was on, also a feature made of parts with only
 * some of them on (not all its parts), while what was off stays off.
 */
class SwitchAllOffTest {
	private static Plain onBefore;
	private static Plain offBefore;
	private static Whole whole;

	private static final class Plain extends Feature {
		Plain(String id, boolean on) {
			super(id, id, "", on);
		}
	}

	/** A feature made of two parts, as the corpses are: its switch is on while either part is. */
	private static final class Whole extends Feature {
		final Plain first = new Plain("switch_test_part_a", true);
		final Plain second = new Plain("switch_test_part_b", false);

		Whole() {
			super("switch_test_whole", "Whole", "", true, false);
			addChild(first);
			addChild(second);
		}
	}

	@BeforeAll
	static void register() {
		onBefore = FeatureManager.register(new Plain("switch_test_on", true));
		offBefore = FeatureManager.register(new Plain("switch_test_off", false));
		whole = FeatureManager.register(new Whole());
	}

	/** @return every switch of every feature and part as it is now */
	private static Map<Feature, Boolean> switches() {
		Map<Feature, Boolean> all = new HashMap<>();
		for (Feature feature : FeatureManager.features()) {
			all.put(feature, feature.ownSwitch());
			for (Feature part : feature.children()) {
				all.put(part, part.ownSwitch());
			}
		}
		return all;
	}

	@Test
	void undoPutsBackExactlyWhatWasOn() {
		Map<Feature, Boolean> before = switches();
		assertTrue(whole.isAnyOn());

		FeatureManager.SwitchedOff off = FeatureManager.switchAllOff();
		assertEquals(List.of(), FeatureManager.switchedOn(), "nothing is on");
		assertFalse(onBefore.isEnabled());
		assertFalse(whole.isAnyOn());
		assertTrue(off.features() >= 2, "it counts the features that were on");

		off.restore();
		assertEquals(before, switches(), "every switch as it was");
		assertTrue(onBefore.isEnabled());
		assertFalse(offBefore.isEnabled(), "what was off stays off");
		assertTrue(whole.first.isEnabled());
		assertFalse(whole.second.isEnabled(), "only the part that was on comes back");
	}
}
