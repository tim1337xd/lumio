package dev.tim1337.lumio.features.misc.keybinds;

import org.junit.jupiter.api.Test;
import com.mojang.blaze3d.platform.InputConstants;
import dev.tim1337.lumio.api.experimental.util.ScreenInput;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Custom keybinds: which one a key press sends, where they work, one command per press, and when two of them conflict. */
class CommandBindsTest {
	/** Modifiers a keybind ignores: not among the ones it can have. */
	private static final int CAPS_LOCK = 16;
	private static final int NUM_LOCK = 32;

	private static final CommandBinds.Spot WORLD = new CommandBinds.Spot(false, null, false);
	private static final CommandBinds.Spot HUB = new CommandBinds.Spot(true, "hub", false);
	private static final CommandBinds.Spot CRIMSON = new CommandBinds.Spot(true, "crimson_isle", false);
	private static final CommandBinds.Spot CRIMSON_SCREEN = new CommandBinds.Spot(true, "crimson_isle", true);

	private static CommandBind bind(int key, int modifiers, String command, CommandBind.Where where, String... islands) {
		CommandBind bind = new CommandBind();
		bind.key = key;
		bind.modifiers = modifiers;
		bind.command = command;
		bind.where = where;
		bind.islands.addAll(List.of(islands));
		return bind;
	}

	@Test
	void keysAndModifiers() {
		CommandBind plain = bind(InputConstants.KEY_1, 0, "/pv", CommandBind.Where.EVERYWHERE);
		CommandBind ctrl = bind(InputConstants.KEY_1, ScreenInput.CONTROL, "/pets", CommandBind.Where.EVERYWHERE);
		List<CommandBind> binds = List.of(plain, ctrl);
		assertSame(plain, CommandBinds.match(binds, InputConstants.KEY_1, 0, WORLD));
		assertSame(ctrl, CommandBinds.match(binds, InputConstants.KEY_1, ScreenInput.CONTROL, WORLD));
		// Caps Lock and Num Lock do not change the keybind
		assertSame(plain, CommandBinds.match(binds, InputConstants.KEY_1, CAPS_LOCK | NUM_LOCK, WORLD));
		assertNull(CommandBinds.match(binds, InputConstants.KEY_1, ScreenInput.SHIFT, WORLD));
		assertNull(CommandBinds.match(binds, InputConstants.KEY_2, 0, WORLD));
	}

	@Test
	void islands() {
		CommandBind everywhere = bind(InputConstants.KEY_G, 0, "/hub", CommandBind.Where.EVERYWHERE);
		CommandBind skyBlock = bind(InputConstants.KEY_H, 0, "/warp hub", CommandBind.Where.SKYBLOCK);
		CommandBind crimson = bind(InputConstants.KEY_J, 0, "/warp ci", CommandBind.Where.ISLANDS, "crimson_isle", "kuudra");
		// outside SkyBlock only the keybinds for everywhere work
		assertTrue(CommandBinds.applies(everywhere, WORLD));
		assertFalse(CommandBinds.applies(skyBlock, WORLD));
		assertFalse(CommandBinds.applies(crimson, WORLD));
		assertTrue(CommandBinds.applies(skyBlock, HUB));
		assertFalse(CommandBinds.applies(crimson, HUB));
		assertTrue(CommandBinds.applies(crimson, CRIMSON));
		assertFalse(CommandBinds.applies(crimson, new CommandBinds.Spot(false, "crimson_isle", false)));
	}

	@Test
	void screens() {
		CommandBind bind = bind(InputConstants.KEY_G, 0, "/hub", CommandBind.Where.EVERYWHERE);
		assertFalse(CommandBinds.applies(bind, CRIMSON_SCREEN));
		bind.onlyWithoutScreen = false;
		assertTrue(CommandBinds.applies(bind, CRIMSON_SCREEN));
	}

	@Test
	void sharedKeyOnOtherIslands() {
		CommandBind crimson = bind(InputConstants.KEY_G, 0, "/warp ci", CommandBind.Where.ISLANDS, "crimson_isle");
		CommandBind hub = bind(InputConstants.KEY_G, 0, "/warp hub", CommandBind.Where.ISLANDS, "hub");
		List<CommandBind> binds = List.of(crimson, hub);
		assertSame(crimson, CommandBinds.match(binds, InputConstants.KEY_G, 0, CRIMSON));
		assertSame(hub, CommandBinds.match(binds, InputConstants.KEY_G, 0, HUB));
		assertNull(CommandBinds.conflictOf(binds, crimson));
	}

	@Test
	void conflicts() {
		CommandBind crimson = bind(InputConstants.KEY_G, 0, "/warp ci", CommandBind.Where.ISLANDS, "crimson_isle", "kuudra");
		CommandBind kuudra = bind(InputConstants.KEY_G, 0, "/pv", CommandBind.Where.ISLANDS, "kuudra");
		CommandBind skyBlock = bind(InputConstants.KEY_G, 0, "/pets", CommandBind.Where.SKYBLOCK);
		CommandBind everywhere = bind(InputConstants.KEY_G, 0, "/hub", CommandBind.Where.EVERYWHERE);
		CommandBind ctrl = bind(InputConstants.KEY_G, ScreenInput.CONTROL, "/bz", CommandBind.Where.EVERYWHERE);
		CommandBind noIsland = bind(InputConstants.KEY_G, 0, "/bz", CommandBind.Where.ISLANDS);
		assertTrue(CommandBinds.overlap(crimson, kuudra));
		assertTrue(CommandBinds.overlap(crimson, skyBlock));
		assertTrue(CommandBinds.overlap(skyBlock, everywhere));
		assertFalse(CommandBinds.overlap(everywhere, ctrl));
		assertFalse(CommandBinds.overlap(noIsland, everywhere));
		assertSame(kuudra, CommandBinds.conflictOf(List.of(crimson, kuudra), crimson));
		// a switched-off keybind does not conflict
		kuudra.enabled = false;
		assertNull(CommandBinds.conflictOf(List.of(crimson, kuudra), crimson));
	}

	@Test
	void onePressOneCommand() {
		CommandBinds binds = new CommandBinds();
		CommandBind bind = bind(InputConstants.KEY_G, 0, "/warp ci", CommandBind.Where.EVERYWHERE);
		List<CommandBind> list = List.of(bind);
		CommandBinds.Outcome press = binds.key(list, InputConstants.KEY_G, 0, InputConstants.PRESS, 1000L, WORLD);
		assertTrue(press.taken());
		assertSame(bind, press.send());
		// held down: the repeats are taken, nothing is sent
		for (int i = 1; i <= 20; i++) {
			CommandBinds.Outcome repeat = binds.key(list, InputConstants.KEY_G, 0, InputConstants.REPEAT, 1000L + i * 100L, WORLD);
			assertTrue(repeat.taken());
			assertNull(repeat.send());
		}
		assertFalse(binds.key(list, InputConstants.KEY_G, 0, InputConstants.RELEASE, 3100L, WORLD).taken());
		assertSame(bind, binds.key(list, InputConstants.KEY_G, 0, InputConstants.PRESS, 3200L, WORLD).send());
	}

	@Test
	void cooldown() {
		CommandBinds binds = new CommandBinds();
		CommandBind first = bind(InputConstants.KEY_G, 0, "/warp ci", CommandBind.Where.EVERYWHERE);
		CommandBind second = bind(InputConstants.KEY_H, 0, "/warp hub", CommandBind.Where.EVERYWHERE);
		List<CommandBind> list = List.of(first, second);
		assertSame(first, binds.key(list, InputConstants.KEY_G, 0, InputConstants.PRESS, 1000L, WORLD).send());
		binds.key(list, InputConstants.KEY_G, 0, InputConstants.RELEASE, 1050L, WORLD);
		// within the cooldown a press is taken but sends nothing, also for another keybind
		CommandBinds.Outcome quick = binds.key(list, InputConstants.KEY_G, 0, InputConstants.PRESS, 1100L, WORLD);
		assertTrue(quick.taken());
		assertNull(quick.send());
		assertNull(binds.key(list, InputConstants.KEY_H, 0, InputConstants.PRESS, 1200L, WORLD).send());
		binds.key(list, InputConstants.KEY_H, 0, InputConstants.RELEASE, 1300L, WORLD);
		assertSame(second, binds.key(list, InputConstants.KEY_H, 0, InputConstants.PRESS, 1000L + CommandBinds.COOLDOWN_MILLIS, WORLD).send());
	}

	@Test
	void otherKeysAreLeftAlone() {
		CommandBinds binds = new CommandBinds();
		CommandBind bind = bind(InputConstants.KEY_G, 0, "/warp ci", CommandBind.Where.SKYBLOCK);
		List<CommandBind> list = List.of(bind);
		assertEquals(CommandBinds.Outcome.NONE, binds.key(list, InputConstants.KEY_H, 0, InputConstants.PRESS, 1000L, HUB));
		// outside SkyBlock the key stays the game's
		assertEquals(CommandBinds.Outcome.NONE, binds.key(list, InputConstants.KEY_G, 0, InputConstants.PRESS, 1000L, WORLD));
		assertEquals(CommandBinds.Outcome.NONE, binds.key(list, InputConstants.KEY_G, 0, InputConstants.REPEAT, 1100L, WORLD));
	}

	@Test
	void commands() {
		assertTrue(CommandBinds.validCommand("/warp ci"));
		assertTrue(CommandBinds.validCommand("/pv"));
		assertFalse(CommandBinds.validCommand("warp ci"));
		assertFalse(CommandBinds.validCommand("/"));
		assertFalse(CommandBinds.validCommand("/ warp"));
		assertFalse(CommandBinds.validCommand("//wand"));
		assertFalse(CommandBinds.validCommand("/warp ci\n/warp hub"));
		assertFalse(CommandBinds.validCommand("/" + "a".repeat(CommandBind.MOST_COMMAND)));
		// a keybind with a broken command sends nothing
		CommandBind bind = bind(InputConstants.KEY_G, 0, "warp ci", CommandBind.Where.EVERYWHERE);
		assertNull(CommandBinds.match(List.of(bind), InputConstants.KEY_G, 0, WORLD));
	}

	@Test
	void saveAndLoad() {
		CommandBind bind = bind(InputConstants.KEY_G, ScreenInput.CONTROL, "/warp ci", CommandBind.Where.ISLANDS, "kuudra");
		bind.onlyWithoutScreen = false;
		CommandBind loaded = CommandBind.load(bind.save());
		assertEquals(InputConstants.KEY_G, loaded.key);
		assertEquals(ScreenInput.CONTROL, loaded.modifiers);
		assertEquals("/warp ci", loaded.command);
		assertEquals(CommandBind.Where.ISLANDS, loaded.where);
		assertTrue(loaded.islands.contains("kuudra"));
		assertFalse(loaded.onlyWithoutScreen);
		assertNull(CommandBind.load(null));
	}
}
