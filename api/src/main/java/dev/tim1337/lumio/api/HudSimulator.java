package dev.tim1337.lumio.api;

import dev.tim1337.lumio.api.feature.FeatureView;

import java.util.List;

/**
 * An add-on's simulations for the HUD editor: while one runs, the editor shows only the elements that belong to it, with sample content, and
 * nothing in the game or in saved data changes. Moments are short animations (a rare drop) the editor can play once.
 */
public interface HudSimulator {
	/** @return what the Simulate menu offers, in order; fixed */
	List<Simulation> simulations();

	/** @return the moments it can play once, by name; fixed */
	List<String> moments();

	/** Plays a moment once, as if it happened now. */
	void playMoment(int index);

	/** A simulation starts (or another option of it is chosen). */
	void start(String id, int option);

	/** The simulation ends. */
	void stop();

	/** @return whether the element of a feature is shown in the editor during the running simulation */
	boolean shows(FeatureView feature);

	/** @return whether the element of a feature shows sample content during the running simulation */
	boolean simulates(FeatureView feature);
}
