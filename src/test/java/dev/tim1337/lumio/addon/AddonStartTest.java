package dev.tim1337.lumio.addon;

import dev.tim1337.lumio.api.AddonContext;
import dev.tim1337.lumio.api.AddonInfo;
import dev.tim1337.lumio.api.ChatListener;
import dev.tim1337.lumio.api.LumioAddon;
import dev.tim1337.lumio.api.feature.AddonFeature;
import dev.tim1337.lumio.config.FeatureManager;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An add-on that fails while it starts leaves nothing registered; one that starts keeps what it registered, and only then. */
class AddonStartTest {
	private static final AddonInfo FAILING = new AddonInfo("test-failing", "Failing", "1.0.0", ">=1.0.0 <2.0.0", "Throws halfway.");
	private static final AddonInfo WORKING = new AddonInfo("test-working", "Working", "1.0.0", ">=1.0.0 <2.0.0", "Starts.");

	/** A feature of a test add-on. */
	private static final class TestFeature extends AddonFeature {
		TestFeature() {
			super("test_feature", "Test", "A feature of a test add-on.", true);
		}
	}

	/** Registers a feature and a chat listener, then fails before it is done. */
	private static final class FailingAddon implements LumioAddon {
		TestFeature feature;

		@Override
		public void onInitialize(AddonContext context) {
			feature = context.registerFeature(new TestFeature());
			context.registerChatListener(new ChatListener() {
			});
			throw new IllegalStateException("failing halfway, on purpose");
		}
	}

	@AfterEach
	void nothingLeftForOtherTests() {
		new AddonCollector().freeze(List.of(), List.of(), Map.of());
	}

	@Test
	void aFailedStartLeavesNothingRegistered() {
		int features = FeatureManager.features().size();
		AddonCollector collector = new AddonCollector();
		FailingAddon addon = new FailingAddon();
		assertFalse(collector.initialize(addon, FAILING));
		collector.freeze(List.of(), List.of(FAILING), Map.of());
		assertEquals(0, Addons.chatListeners().length, "its chat listener is not kept");
		assertEquals(features, FeatureManager.features().size(), "its feature is not in the feature list");
		assertNotNull(addon.feature, "the feature was made before the failure");
		assertNull(Adapters.featureOf(addon.feature), "and is forgotten");
	}

	@Test
	void aStartedAddonKeepsWhatItRegistered() {
		AddonCollector collector = new AddonCollector();
		AddonContext[] kept = new AddonContext[1];
		assertTrue(collector.initialize(context -> {
			kept[0] = context;
			context.registerChatListener(new ChatListener() {
			});
		}, WORKING));
		collector.freeze(List.of(WORKING), List.of(), Map.of());
		assertEquals(1, Addons.chatListeners().length);
		assertThrows(IllegalStateException.class, () -> kept[0].registerChatListener(new ChatListener() {
		}), "registering after the start is refused");
	}
}
