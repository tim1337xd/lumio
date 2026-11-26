package dev.tim1337.lumio.ui.controls;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModuleKeysNamesTest {
	@Test
	void theFeatureNameInFrontOfAKeyComesOff() {
		assertEquals("Next shot", ModuleKeysControl.withoutOwner("Cinematic camera: next shot", "Cinematic camera"));
		assertEquals("Leave cinematic camera", ModuleKeysControl.withoutOwner("Leave cinematic camera", "Cinematic camera"));
		assertEquals("Protect item", ModuleKeysControl.withoutOwner("Protect item", ""));
	}
}
