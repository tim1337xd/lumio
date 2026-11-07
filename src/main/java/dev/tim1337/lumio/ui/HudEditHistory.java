package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.FeatureManager;

import com.google.gson.JsonObject;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Undo and redo in the HUD editor. A step remembers the settings of the features it changed, before and after: a move, a resize, a style
 * change, showing or hiding an element, or a reset. A drag is one step, begun when it starts and finished when it ends. The steps last as long
 * as the editor is open.
 */
final class HudEditHistory {
	/** The most steps kept; older ones are dropped. */
	private static final int MOST = 100;

	private record Step(Feature[] features, JsonObject[] before, JsonObject[] after) {
	}

	private final Deque<Step> undo = new ArrayDeque<>();
	private final Deque<Step> redo = new ArrayDeque<>();
	private Feature[] pending;
	private JsonObject[] pendingBefore;

	/** Begins a step that changes these features; a step already begun is finished first. */
	void begin(Feature... features) {
		if (pending != null) {
			finish();
		}
		pending = features;
		pendingBefore = save(features);
	}

	/** Finishes the begun step; it is kept only if something changed. */
	void finish() {
		if (pending == null) {
			return;
		}
		JsonObject[] after = save(pending);
		boolean changed = false;
		for (int i = 0; i < after.length; i++) {
			changed |= !after[i].equals(pendingBefore[i]);
		}
		if (changed) {
			undo.push(new Step(pending, pendingBefore, after));
			while (undo.size() > MOST) {
				undo.removeLast();
			}
			redo.clear();
		}
		pending = null;
		pendingBefore = null;
	}

	boolean canUndo() {
		return !undo.isEmpty();
	}

	boolean canRedo() {
		return !redo.isEmpty();
	}

	void undo() {
		finish();
		if (!undo.isEmpty()) {
			Step step = undo.pop();
			load(step.features(), step.before());
			redo.push(step);
		}
	}

	void redo() {
		finish();
		if (!redo.isEmpty()) {
			Step step = redo.pop();
			load(step.features(), step.after());
			undo.push(step);
		}
	}

	private static JsonObject[] save(Feature[] features) {
		JsonObject[] saved = new JsonObject[features.length];
		for (int i = 0; i < features.length; i++) {
			saved[i] = features[i].save();
		}
		return saved;
	}

	private static void load(Feature[] features, JsonObject[] saved) {
		for (int i = 0; i < features.length; i++) {
			features[i].load(saved[i]);
		}
		FeatureManager.saveSoon();
	}
}
