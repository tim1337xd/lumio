package dev.tim1337.lumio.api;

/**
 * An add-on of Lumio, found through the Fabric entrypoint {@value LumioApi#ENTRYPOINT}. It is initialized once, during the
 * start of the game after the features of Lumio exist and before the settings are read, and registers everything it adds there.
 */
public interface LumioAddon {
	/** Registers the add-on's features and providers; called once. */
	void onInitialize(AddonContext context);

	/**
	 * @return the add-on's name, version and the API it needs, or null to take them from the add-on's fabric.mod.json (its name, version and
	 *         description, and the custom value "lumio:api" for the API range)
	 */
	default AddonInfo info() {
		return null;
	}
}
