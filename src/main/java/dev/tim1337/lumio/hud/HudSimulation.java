package dev.tim1337.lumio.hud;

import dev.tim1337.lumio.addon.Adapters;
import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.HudSimulator;
import dev.tim1337.lumio.api.Simulation;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.ui.HudEditScreen;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the HUD editor simulates, offered by the add-ons ({@link HudSimulator}), so the elements of an activity can be placed without doing it.
 * It only exists inside the editor: elements show sample content there, and nothing in the game, the trackers or their saved data changes, nor
 * is anything sent. The last choice is remembered while the game runs and offered again the next time the editor opens.
 */
public final class HudSimulation {
	/** A simulation and the add-on that offers it. */
	public record Entry(HudSimulator simulator, Simulation simulation) {
	}

	/** A moment and the add-on that plays it. */
	public record Moment(HudSimulator simulator, int index, String name) {
	}

	private static List<Entry> entries;
	private static List<Moment> moments;
	private static Entry running;
	/** The panel being drawn in the live preview beside its page, which shows its sample like one simulated; only during that draw. */
	private static Feature previewed;

	/** Marks the panel drawn in the live preview beside its page, or null after it. */
	public static void previewing(Feature feature) {
		previewed = feature;
	}

	private static int option;

	private HudSimulation() {
	}

	/** @return every simulation of every add-on, in order; made once */
	public static List<Entry> entries() {
		if (entries == null) {
			List<Entry> found = new ArrayList<>();
			for (HudSimulator simulator : Addons.simulators()) {
				for (Simulation simulation : simulator.simulations()) {
					found.add(new Entry(simulator, simulation));
				}
			}
			entries = List.copyOf(found);
		}
		return entries;
	}

	/** @return every moment of every add-on, in order; made once */
	public static List<Moment> moments() {
		if (moments == null) {
			List<Moment> found = new ArrayList<>();
			for (HudSimulator simulator : Addons.simulators()) {
				List<String> names = simulator.moments();
				for (int i = 0; i < names.size(); i++) {
					found.add(new Moment(simulator, i, names.get(i)));
				}
			}
			moments = List.copyOf(found);
		}
		return moments;
	}

	/** Simulates an entry with one of its options (0 if it has none). */
	public static void start(Entry entry, int chosen) {
		if (running != null && running.simulator() != entry.simulator()) {
			running.simulator().stop();
		}
		running = entry;
		option = Math.max(0, Math.min(chosen, Math.max(0, entry.simulation().options().size() - 1)));
		entry.simulator().start(entry.simulation().id(), option);
	}

	public static void stop() {
		if (running != null) {
			running.simulator().stop();
		}
		running = null;
		option = 0;
	}

	/** @return whether something is simulated right now, which is only while the editor is open */
	public static boolean active() {
		return running != null && HudEditScreen.isOpen();
	}

	/** @return the simulated entry, also while the editor is closed (it is offered again when it opens), or null */
	public static Entry running() {
		return running;
	}

	public static int option() {
		return option;
	}

	/** @return whether the element of a feature is shown in the editor: always without a simulation, otherwise if it belongs to it */
	public static boolean shows(Feature feature) {
		return !active() || running.simulator().shows(Adapters.viewOf(feature));
	}

	/** @return whether the element of a feature is shown with sample content for the simulation */
	public static boolean simulates(Feature feature) {
		if (previewed == feature) {
			return true;
		}
		return active() && running.simulator().simulates(Adapters.viewOf(feature));
	}

	/** Plays a moment once, as if it happened now: the panels show it with their sample content. Nothing in the game or the trackers changes. */
	public static void playMoment(int index) {
		List<Moment> all = moments();
		if (index >= 0 && index < all.size()) {
			Moment moment = all.get(index);
			moment.simulator().playMoment(moment.index());
		}
	}

	/** @return what the chip next to the toolbar says: "Tarantula Broodfather · boss spawned" */
	public static String describe() {
		if (running == null) {
			return "";
		}
		Simulation simulation = running.simulation();
		return simulation.options().isEmpty() ? simulation.name() : simulation.name() + "  ·  " + simulation.options().get(option).toLowerCase(Locale.ROOT);
	}
}
