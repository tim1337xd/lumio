package dev.tim1337.lumio.api.experimental.util;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** The game's list of potion effects beside the inventory: an add-on that draws something in its place asks for it to step aside while it does. */
public final class InventoryEffects {
	private static final List<BooleanSupplier> ASKING = new ArrayList<>();

	private InventoryEffects() {
	}

	/** Adds a check that is asked once per frame while an inventory is open: while it is true, the list is not drawn. */
	public static void hideWhile(BooleanSupplier check) {
		ASKING.add(check);
	}

	/** @return whether something asks for the list to step aside now */
	public static boolean hidden() {
		for (int i = 0; i < ASKING.size(); i++) {
			if (ASKING.get(i).getAsBoolean()) {
				return true;
			}
		}
		return false;
	}
}
