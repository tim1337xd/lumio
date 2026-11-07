package dev.tim1337.lumio.features.misc.keybinds;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** A keybind saved with the key numbers of Minecraft 26.2 keeps its key on a version with SDL's numbers. */
class GlfwKeysTest {
	@Test
	void lettersAndDigits() {
		assertEquals(4, GlfwKeys.toSdl('A'));
		assertEquals(29, GlfwKeys.toSdl('Z'));
		assertEquals(30, GlfwKeys.toSdl('1'));
		assertEquals(38, GlfwKeys.toSdl('9'));
		assertEquals(39, GlfwKeys.toSdl('0'));
	}

	@Test
	void functionKeys() {
		assertEquals(58, GlfwKeys.toSdl(290));
		assertEquals(69, GlfwKeys.toSdl(301));
		assertEquals(104, GlfwKeys.toSdl(302));
		assertEquals(115, GlfwKeys.toSdl(313));
	}

	@Test
	void singleKeys() {
		assertEquals(44, GlfwKeys.toSdl(32));
		assertEquals(41, GlfwKeys.toSdl(256));
		assertEquals(40, GlfwKeys.toSdl(257));
		assertEquals(88, GlfwKeys.toSdl(335));
		assertEquals(98, GlfwKeys.toSdl(320));
		assertEquals(97, GlfwKeys.toSdl(329));
		assertEquals(225, GlfwKeys.toSdl(340));
		assertEquals(228, GlfwKeys.toSdl(345));
	}

	@Test
	void whatIsNoKey() {
		assertEquals(0, GlfwKeys.toSdl(-1));
		assertEquals(0, GlfwKeys.toSdl(0));
		assertEquals(0, GlfwKeys.toSdl(1000));
	}
}
