package dev.tim1337.lumio.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.tim1337.lumio.ModFeatures;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;
import dev.tim1337.lumio.menu.FeatureListFeature;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the HUD editor offers: every element has a live feature behind it (registered, and reachable on a page of the menu), a name of its own, and
 * a way to show in the editor while it is not active; and its label says the name only, with the size while it is resized.
 */
class HudEditElementsTest {
	/** The elements that are no panels and show in the editor whenever they are switched on, so they need no sample of their own. */
	private static final Set<String> SHOWN_WHEN_ON = Set.of("Info bar", "Now playing", "Item name", "Held item", "Chat");

	@BeforeAll
	static void start() {
		SharedConstants.tryDetectVersion();
		Bootstrap.bootStrap();
		ModFeatures.createAll();
	}

	@Test
	void everyElementHasALiveFeature() {
		List<EditableHud> elements = ModFeatures.editableHuds();
		assertFalse(elements.isEmpty());
		for (EditableHud element : elements) {
			Feature feature = element.hudFeature();
			assertNotNull(feature, element.hudName() + " has no feature");
			assertTrue(FeatureManager.features().contains(feature), element.hudName() + ": its feature is not registered");
			assertTrue(onAPage(feature), element.hudName() + ": its feature is on no page of the menu");
		}
	}

	/** @return whether the feature is listed on a page of the menu; the waypoint map is set per group on the Waypoints page, which stands for it */
	private static boolean onAPage(Feature feature) {
		if (feature == ModFeatures.WAYPOINT_MAP) {
			return onAPage(ModFeatures.WAYPOINTS);
		}
		for (Feature page : FeatureManager.features()) {
			if (page instanceof FeatureListFeature list && (list.holds(feature) || list.direct() == feature)) {
				return true;
			}
		}
		return false;
	}

	@Test
	void everyElementAppearsOnceUnderItsOwnName() {
		Map<EditableHud, Boolean> seen = new IdentityHashMap<>();
		Set<String> names = new HashSet<>();
		for (EditableHud element : ModFeatures.editableHuds()) {
			assertEquals(null, seen.put(element, true), element.hudName() + " is listed twice");
			String name = element.hudName();
			assertTrue(name != null && !name.isBlank() && name.equals(name.strip()), "an element without a proper name: '" + name + "'");
			assertTrue(Character.isUpperCase(name.charAt(0)), name + " should start with a capital");
			assertTrue(names.add(name), "two elements are called " + name);
		}
	}

	@Test
	void everyElementCanShowInTheEditor() {
		for (EditableHud element : ModFeatures.editableHuds()) {
			assertTrue(element.hudPreviewable() || SHOWN_WHEN_ON.contains(element.hudName()),
				element.hudName() + " has no sample for the editor and is not known to show whenever it is on");
		}
	}

	@Test
	void aSongInTheInfoBarIsShownThere() {
		assertEquals(null, ModFeatures.INFO_BAR.hudShownIn(), "only an element that shows inside another names one");
		assertEquals("Shown in the info bar", ModFeatures.MUSIC.hudShownInNote());
		for (EditableHud element : ModFeatures.editableHuds()) {
			EditableHud in = element.hudShownIn();
			assertTrue(in == null || in != element && in.hudShown() && !element.hudShown() && !element.hudShownInNote().isBlank(), element.hudName());
		}
	}

	@Test
	void theSizeLimitsAreARange() {
		for (EditableHud element : ModFeatures.editableHuds()) {
			assertTrue(element.hudMinScale() > 0f && element.hudMinScale() <= element.hudMaxScale(), element.hudName());
		}
	}

	@Test
	void theLabelSaysTheNameOnly() {
		assertEquals("Scoreboard", HudEditLabel.text("Scoreboard", false, "1.10×"));
		assertEquals("Scoreboard", HudEditLabel.text("Scoreboard", true, ""));
		assertEquals("Scoreboard", HudEditLabel.text("Scoreboard", true, null));
	}

	@Test
	void theLabelAddsTheSizeWhileResizing() {
		assertEquals("Scoreboard  ·  1.10×", HudEditLabel.text("Scoreboard", true, "1.10×"));
	}
}
