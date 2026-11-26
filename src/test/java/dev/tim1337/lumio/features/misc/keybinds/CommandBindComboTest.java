package dev.tim1337.lumio.features.misc.keybinds;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import dev.tim1337.lumio.api.experimental.util.ScreenInput;

import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.List;
import java.util.Set;

/** Command keybinds with key combinations: kept and read, an old keybind unchanged, the most specific wins, conflicts. */
class CommandBindComboTest {
	private static final CommandBinds.Spot WORLD = new CommandBinds.Spot(false, null, false);
	private static final String TAB = "key.keyboard.tab";
	private static final String R = "key.keyboard.r";

	private static CommandBind bind(KeyCombo combo, String command) {
		CommandBind bind = new CommandBind();
		bind.setCombo(combo);
		bind.command = command;
		return bind;
	}

	@Test
	void aCombinationIsKeptAndRead() {
		CommandBind bind = bind(new KeyCombo(List.of(TAB), R), "/warp hub");
		assertEquals(InputConstants.KEY_R, bind.key);
		assertEquals(List.of(TAB), bind.holders);
		CommandBind read = CommandBind.load(bind.save());
		assertEquals(new KeyCombo(List.of(TAB), R), read.combo());
		// Ctrl as a holder stays a modifier, as before
		CommandBind ctrl = bind(KeyCombo.pressed(List.of("key.keyboard.left.control", R)), "/pets");
		assertEquals(ScreenInput.CONTROL, ctrl.modifiers);
		assertTrue(ctrl.holders.isEmpty());
		// a mouse button and the wheel as the trigger
		CommandBind mouse = bind(new KeyCombo(List.of(TAB), "key.mouse.4"), "/sb");
		assertEquals("key.mouse.4", CommandBind.load(mouse.save()).triggerName());
		CommandBind wheel = bind(new KeyCombo(List.of(TAB), KeyCombo.WHEEL_UP), "/sb");
		assertEquals(KeyCombo.WHEEL_UP, CommandBind.load(wheel.save()).triggerName());
	}

	@Test
	void anOldKeybindLoadsUnchanged() {
		String old = "{\"key\":" + InputConstants.KEY_R + ",\"key_name\":\"key.keyboard.r\",\"modifiers\":" + ScreenInput.SHIFT
			+ ",\"command\":\"/pv\",\"where\":\"EVERYWHERE\",\"enabled\":true}";
		CommandBind read = CommandBind.load(JsonParser.parseString(old));
		assertEquals(InputConstants.KEY_R, read.key);
		assertEquals(ScreenInput.SHIFT, read.modifiers);
		assertTrue(read.holders.isEmpty());
		assertTrue(read.trigger.isEmpty());
		// saved again, an older version still reads it the same: no new fields for a plain keybind
		assertFalse(read.save().has("holders"));
		assertFalse(read.save().has("trigger"));
	}

	@Test
	void theMostSpecificKeybindSends() {
		CommandBind plain = bind(KeyCombo.of(R), "/plain");
		CommandBind combo = bind(new KeyCombo(List.of(TAB), R), "/combo");
		List<CommandBind> binds = List.of(plain, combo);
		Set<String> tabHeld = Set.of(TAB);
		assertSame(combo, CommandBinds.match(binds, R, 0, tabHeld::contains, 0, WORLD));
		assertSame(plain, CommandBinds.match(binds, R, 0, key -> false, 0, WORLD));
		// a more specific bind of the game's or Lumio's on the key wins over a plain keybind
		assertNull(CommandBinds.match(List.of(plain), R, 0, tabHeld::contains, 2, WORLD));
		// one press sends once
		CommandBinds trigger = new CommandBinds();
		CommandBinds.Outcome first = trigger.key(binds, InputConstants.KEY_R, 0, InputConstants.PRESS, 10_000L, WORLD, tabHeld::contains, 0);
		assertSame(combo, first.send());
		CommandBinds.Outcome repeat = trigger.key(binds, InputConstants.KEY_R, 0, InputConstants.REPEAT, 10_050L, WORLD, tabHeld::contains, 0);
		assertNull(repeat.send());
	}

	@Test
	void conflictsAreTheSameKeys() {
		CommandBind plain = bind(KeyCombo.of(R), "/a");
		CommandBind combo = bind(new KeyCombo(List.of(TAB), R), "/b");
		CommandBind same = bind(new KeyCombo(List.of(TAB), R), "/c");
		assertFalse(CommandBinds.overlap(plain, combo));
		assertTrue(CommandBinds.overlap(combo, same));
	}
}
