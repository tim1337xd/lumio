package dev.tim1337.lumio.api;

import java.util.List;

/**
 * A place an add-on knows, such as a SkyBlock island.
 *
 * @param key     its key in settings ("crimson_isle")
 * @param name    its name, as players read it ("Crimson Isle")
 * @param aliases the names the server shows for it, in lower case
 */
public record Place(String key, String name, List<String> aliases) {
	public Place {
		aliases = List.copyOf(aliases);
	}
}
