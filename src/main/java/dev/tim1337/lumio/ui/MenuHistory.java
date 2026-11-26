package dev.tim1337.lumio.ui;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Objects;

/**
 * The way back and forward through what the menu showed, as a browser keeps it: every place visited is a step; back and forward move through
 * them; a new place after going back drops the steps ahead. Kept while the menu is open, forgotten when it closes. Plain logic, tested without
 * the game.
 *
 * @param <T> a place in the menu, compared with {@code equals}
 */
final class MenuHistory<T> {
	/** The most steps kept behind; the oldest go first. */
	static final int MOST_STEPS = 64;

	private final Deque<T> behind = new ArrayDeque<>();
	private final Deque<T> ahead = new ArrayDeque<>();
	private T current;

	/** Notes the place shown now: a new step, unless it is the place already shown. */
	void visit(T place) {
		if (current == null) {
			current = place;
			return;
		}
		if (Objects.equals(current, place)) {
			return;
		}
		behind.push(current);
		while (behind.size() > MOST_STEPS) {
			behind.removeLast();
		}
		ahead.clear();
		current = place;
	}

	/** Takes the place shown now as the current step, without a step of its own: what going back or forward led to, as it settled. */
	void replace(T place) {
		current = place;
	}

	/** @return the place one step back, now the current one; null at the start, where nothing changes */
	T back() {
		if (behind.isEmpty()) {
			return null;
		}
		ahead.push(current);
		current = behind.pop();
		return current;
	}

	/** @return the place one step forward, now the current one; null when there is none */
	T forward() {
		if (ahead.isEmpty()) {
			return null;
		}
		behind.push(current);
		current = ahead.pop();
		return current;
	}

	boolean canGoBack() {
		return !behind.isEmpty();
	}

	boolean canGoForward() {
		return !ahead.isEmpty();
	}

	T current() {
		return current;
	}
}
