package dev.tim1337.lumio.api;

import java.util.List;

/**
 * Something the HUD editor can simulate, so its elements can be placed without doing it (mining in the Dwarven Mines, a slayer boss).
 *
 * @param id      its id, unique within its simulator
 * @param name    its name in the Simulate menu
 * @param group   the heading it is listed under ("Mining")
 * @param options choices within it that change the HUD (a boss's phases), or empty
 */
public record Simulation(String id, String name, String group, List<String> options) {
	public Simulation {
		options = List.copyOf(options);
	}
}
