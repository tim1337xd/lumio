package dev.tim1337.lumio.api.experimental.util;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.particles.ParticleOptions;

/**
 * The particles the server sends, as they arrive, for add-ons that read a mark the server draws with particles (such as a mining target). Only
 * observed: the game shows them as always. Called on the game's thread, once per packet; listeners must return quickly and keep nothing per call.
 */
public final class ServerParticles {
	/** One packet of particles from the server. */
	@FunctionalInterface
	public interface Listener {
		/**
		 * @param particle what kind of particle, with its options
		 * @param x        where they are sent from
		 * @param count    how many, 0 for one particle sent with a speed and direction
		 */
		void particle(ParticleOptions particle, double x, double y, double z, int count);
	}

	/** Says which of the server's particles the player chose not to see. */
	@FunctionalInterface
	public interface Hider {
		/** @return whether this packet's particles are not shown; the game then skips the packet, as if it never came */
		boolean hide(ParticleOptions particle, double x, double y, double z, int count);
	}

	private static final List<Listener> LISTENERS = new ArrayList<>();
	private static Listener[] listening = new Listener[0];
	private static final List<Hider> HIDERS = new ArrayList<>();
	private static Hider[] hiding = new Hider[0];
	private static boolean warned;

	private ServerParticles() {
	}

	/** Listens to the server's particles; added once while an add-on starts. */
	public static void listen(Listener listener) {
		LISTENERS.add(listener);
		listening = LISTENERS.toArray(new Listener[0]);
	}

	/**
	 * Hides some of the server's particles, only from view (nothing is sent, the server's state is untouched); added once while an add-on starts.
	 * Asked after the listeners, so they still see every particle. Called on the game's thread, once per packet: return quickly.
	 */
	public static void hide(Hider hider) {
		HIDERS.add(hider);
		hiding = HIDERS.toArray(new Hider[0]);
	}

	/** @return whether anything listens or hides: without either, Lumio skips reading the packet */
	public static boolean listened() {
		return listening.length > 0 || hiding.length > 0;
	}

	/**
	 * A packet of particles from the server; called by Lumio on the game's thread.
	 *
	 * @return whether an add-on hides it
	 */
	public static boolean received(ParticleOptions particle, double x, double y, double z, int count) {
		Listener[] now = listening;
		for (Listener listener : now) {
			try {
				listener.particle(particle, x, y, z, count);
			} catch (RuntimeException e) {
				// logged once; a lambda for Guard would be made for every packet
				if (!warned) {
					warned = true;
					Logs.warn("A server particle listener failed and is skipped where it fails again", e);
				}
			}
		}
		for (Hider hider : hiding) {
			try {
				if (hider.hide(particle, x, y, z, count)) {
					return true;
				}
			} catch (RuntimeException e) {
				// a failing hider hides nothing: the particles show as the game sends them
				if (!warned) {
					warned = true;
					Logs.warn("A server particle hider failed and is skipped where it fails again", e);
				}
			}
		}
		return false;
	}
}
