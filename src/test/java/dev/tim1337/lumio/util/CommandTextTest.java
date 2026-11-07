package dev.tim1337.lumio.util;

import dev.tim1337.lumio.api.experimental.util.CommandText;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Values from the item repository or saved files become a command argument only when they are one plain word. */
class CommandTextTest {
	@Test
	void itemIdsAndPageNumbersPass() {
		assertTrue(CommandText.safeArgument("ENCHANTED_DIAMOND"));
		assertTrue(CommandText.safeArgument("PET_SKIN_DRAGON;4"));
		assertTrue(CommandText.safeArgument("RUNE_MUSIC-1:3.5"));
		assertTrue(CommandText.safeArgument("12"));
	}

	@Test
	void extraArgumentsAndOtherCommandsAreRejected() {
		assertFalse(CommandText.safeArgument("DIAMOND coopadd someone"));
		assertFalse(CommandText.safeArgument("DIAMOND/"));
		assertFalse(CommandText.safeArgument("/pay"));
	}

	@Test
	void charactersTheServerRejectsAreRejected() {
		assertFalse(CommandText.safeArgument("DIAMOND\n/coopadd someone"));
		assertFalse(CommandText.safeArgument("DIAMOND\r"));
		assertFalse(CommandText.safeArgument("§cDIAMOND"));
		assertFalse(CommandText.safeArgument("DIAMOND\u007F"));
	}

	@Test
	void emptyMissingAndOverlongAreRejected() {
		assertFalse(CommandText.safeArgument(null));
		assertFalse(CommandText.safeArgument(""));
		assertTrue(CommandText.safeArgument("A".repeat(CommandText.LONGEST_ARGUMENT)));
		assertFalse(CommandText.safeArgument("A".repeat(CommandText.LONGEST_ARGUMENT + 1)));
	}
}
