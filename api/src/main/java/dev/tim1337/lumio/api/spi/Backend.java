package dev.tim1337.lumio.api.spi;

import java.util.ServiceLoader;

/**
 * The bridge from the API to the running Lumio: its {@link Services}, found once through {@link ServiceLoader} (Lumio lists
 * them in META-INF/services). Add-ons never use this class; they use the API classes, which call through it.
 */
public final class Backend {
	private static volatile Services services;

	private Backend() {
	}

	/** @return the services @throws IllegalStateException when Lumio is not there */
	public static Services get() {
		Services current = services;
		if (current == null) {
			synchronized (Backend.class) {
				if (services == null) {
					services = ServiceLoader.load(Services.class, Backend.class.getClassLoader()).findFirst()
						.orElseThrow(() -> new IllegalStateException("Lumio is not installed"));
				}
				current = services;
			}
		}
		return current;
	}
}
