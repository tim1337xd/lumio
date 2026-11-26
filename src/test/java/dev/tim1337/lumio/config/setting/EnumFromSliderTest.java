package dev.tim1337.lumio.config.setting;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonPrimitive;

import net.minecraft.network.chat.Component;

import org.junit.jupiter.api.Test;

/** A slider that became a choice of numbers ("Colors 2 / 3") keeps what was saved: the number loads as the choice labelled with it. */
class EnumFromSliderTest {
	private enum Count {
		TWO, THREE
	}

	private static EnumSetting<Count> setting() {
		return new EnumSetting<>("count", Component.literal("Colors"), Count.TWO, count -> Component.literal(count == Count.TWO ? "2" : "3"));
	}

	@Test
	void aSavedNumberLoadsAsItsChoice() {
		EnumSetting<Count> count = setting();
		count.load(new JsonPrimitive(3f));
		assertEquals(Count.THREE, count.get());
		count.load(new JsonPrimitive(2));
		assertEquals(Count.TWO, count.get());
	}

	@Test
	void namesStillLoadAndOtherNumbersAreLeftAlone() {
		EnumSetting<Count> count = setting();
		count.load(new JsonPrimitive("THREE"));
		assertEquals(Count.THREE, count.get());
		count.load(new JsonPrimitive(7));
		assertEquals(Count.THREE, count.get(), "no choice is labelled 7: the value stays");
	}
}
