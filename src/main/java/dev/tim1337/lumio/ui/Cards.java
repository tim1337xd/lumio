package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.motion.Spring;

import java.util.Map;
import java.util.WeakHashMap;

/** Tracks the highlight of every card in a list (a server, a world), so it animates on a spring instead of jumping. */
public final class Cards {
	private static final Map<Object, Spring> LIGHTS = new WeakHashMap<>();

	private Cards() {
	}

	/** @return the card's highlight from 0 to 1: partial while hovered, full while selected */
	public static float lit(Object entry, boolean over, boolean selected) {
		Spring spring = LIGHTS.computeIfAbsent(entry, key -> Spring.smooth(Spring.SNAPPY));
		return Math.max(0f, Math.min(1f, spring.follow(selected ? 1f : over ? 0.55f : 0f)));
	}
}
