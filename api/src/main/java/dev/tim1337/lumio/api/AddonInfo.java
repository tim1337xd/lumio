package dev.tim1337.lumio.api;

/**
 * What the Add-ons page shows of an add-on.
 *
 * @param id          a unique id, also the name of its settings file ("lumio-skyblock")
 * @param name        its name, as players read it
 * @param version     its own version
 * @param apiRange    the API versions it works with, as in fabric.mod.json (">=1.0.0 <2.0.0")
 * @param description one short sentence about what it adds
 */
public record AddonInfo(String id, String name, String version, String apiRange, String description) {
}
