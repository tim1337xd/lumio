package dev.tim1337.lumio.ui;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The list of add-ons already seen in the setup: one id per line, whatever the line endings, an empty file means none. */
class SetupAddonsTest {
	@SuppressWarnings("unchecked")
	private static Set<String> parse(String text) throws ReflectiveOperationException {
		Class<?> type = Class.forName("dev.tim1337.lumio.ui.setup.SetupAddons");
		Method parse = type.getDeclaredMethod("parse", String.class);
		parse.setAccessible(true);
		return (Set<String>) parse.invoke(null, text);
	}

	@Test
	void readsOneIdPerLine() throws ReflectiveOperationException {
		assertEquals(Set.of(), parse(""));
		assertEquals(Set.of("lumio-skyblock"), parse("lumio-skyblock"));
		assertEquals(Set.of("a", "b"), parse("a\r\n  b  \n\n"));
	}
}
