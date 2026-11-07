package dev.tim1337.lumio.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A config's name becomes a file name in the configs folder: it can never point into another folder or be a name Windows refuses. */
class ConfigStoreTest {
	@Test
	void separatorsAndReservedCharactersAreRemoved() {
		assertEquals("....evil", ConfigStore.fileName("../../evil"));
		assertEquals("..evil", ConfigStore.fileName("..\\evil"));
		assertEquals("Cevil", ConfigStore.fileName("C:evil"));
		assertEquals("abc", ConfigStore.fileName("a*b?c\"<>|"));
		// control characters such as line breaks and tabs are removed, not turned into spaces
		assertEquals("linebreak", ConfigStore.fileName("line\nbreak"));
		assertEquals("tab", ConfigStore.fileName("t\tab"));
	}

	@Test
	void noNameLeavesTheFolder() {
		for (String name : new String[]{"..", "../x", "/etc/passwd", "\\\\server\\share", "C:\\Windows\\x", ".\u0000."}) {
			String file = ConfigStore.fileName(name);
			assertFalse(file.contains("/") || file.contains("\\") || file.contains(":"), file);
			assertFalse(file.matches("\\.+"), file);
		}
	}

	@Test
	void windowsDeviceNamesAndDotNamesGetAPrefix() {
		assertEquals("_CON", ConfigStore.fileName("CON"));
		assertEquals("_lpt1", ConfigStore.fileName("lpt1"));
		assertEquals("_..", ConfigStore.fileName(".."));
		assertEquals("Console", ConfigStore.fileName("Console"));
	}

	@Test
	void whiteSpaceIsTidiedAndLongNamesCut() {
		assertEquals("My dark theme", ConfigStore.fileName("  My   dark   theme  "));
		assertTrue(ConfigStore.fileName("x".repeat(100)).length() <= 40);
	}
}
