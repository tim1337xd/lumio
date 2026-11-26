package dev.tim1337.lumio.api.experimental.filter;

import dev.tim1337.lumio.api.spi.Backend;

import net.minecraft.world.entity.Entity;

/**
 * Rules of an add-on in Lumio's Visual Filter: each a switch on the filter's page, in a section of its own; an entity a switched-on rule matches is
 * not drawn (it stays in the world, for whatever else reads it). Only what is drawn changes; nothing is sent. Add them while the add-on starts.
 */
public final class VisualFilters {
	/** A rule: which entities it leaves out. Asked once when an entity appears and again when its equipment changes, never every frame. */
	public interface Rule {
		boolean hides(Entity entity);
	}

	private VisualFilters() {
	}

	/**
	 * @param key         the switch's key, unique in the Visual Filter
	 * @param label       the switch's name
	 * @param description its one line under the name
	 * @param section     the section of the page it is listed in ("Dungeons")
	 * @param on          whether the switch is on at first
	 */
	public static void add(String key, String label, String description, String section, boolean on, Rule rule) {
		Backend.get().visualFilter(key, label, description, section, on, rule);
	}
}
