package dev.tim1337.lumio.config.setting;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The info bar's segment list with an add-on's segments: they are listed where the add-on asks, and while the add-on is not there their rows
 * stay in the saved list, with their place and state, but are not shown or used.
 */
class OrderedListAddonTest {
	private static OrderedListSetting list() {
		return new OrderedListSetting("segments", Component.literal("Segments"), List.of(
			new OrderedListSetting.Option("clock", "Clock", "clock", true),
			new OrderedListSetting.Option("fps", "FPS", "gauge", true),
			new OrderedListSetting.Option("coords", "Coordinates", "move", false)));
	}

	private static OrderedListSetting withAddon() {
		OrderedListSetting list = list();
		list.addOption(new OrderedListSetting.Option("skyblock_time", "Date", "sun", false), "clock");
		list.addOption(new OrderedListSetting.Option("purse", "Purse", "coins", false), "coords");
		return list;
	}

	@Test
	void anAddonPartIsListedAfterTheOneItNames() {
		assertEquals(List.of("clock", "skyblock_time", "fps", "coords", "purse"), withAddon().rows());
	}

	@Test
	void withoutTheAddonItsRowsAreKeptButNotShown() {
		OrderedListSetting before = withAddon();
		before.setOn("purse", true);
		before.setOn("fps", false);
		JsonElement saved = before.save();

		OrderedListSetting without = list();
		without.load(saved);
		assertEquals(List.of("clock", "fps", "coords"), without.rows(), "the add-on's rows are not shown");
		assertFalse(without.enabledInOrder().contains("purse"), "nor used");
		assertFalse(without.isOn("fps"), "the bar's own rows keep their state");

		OrderedListSetting back = withAddon();
		back.load(without.save());
		assertTrue(back.isOn("purse"), "the add-on's row comes back switched on, as it was");
		assertEquals(List.of("clock", "skyblock_time", "fps", "coords", "purse"), back.rows(), "in its place");
	}
}
