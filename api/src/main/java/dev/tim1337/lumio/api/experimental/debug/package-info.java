/**
 * Debug hooks: probes in Lumio and its add-ons that report what they do, and the few switches a debug tool may turn. Nothing listens in a normal
 * install, and then every probe costs one read of {@link dev.tim1337.lumio.api.experimental.debug.DebugHooks#ACTIVE}: guard each one with it, so
 * no text is built, no lambda made and no GPU query sent while it is false.
 *
 * <p><b>Experimental:</b> this may change or go in any version, without a new API version (changes are listed in the API changelog).
 * Third-party add-ons should prefer the stable API; Lumio's own add-ons may use both.
 */
package dev.tim1337.lumio.api.experimental.debug;
