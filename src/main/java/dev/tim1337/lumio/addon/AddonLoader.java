package dev.tim1337.lumio.addon;

import dev.tim1337.lumio.config.PageOutlines;
import dev.tim1337.lumio.api.AddonInfo;
import dev.tim1337.lumio.api.LumioAddon;
import dev.tim1337.lumio.api.LumioApi;
import dev.tim1337.lumio.api.experimental.util.Logs;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Finds the add-ons (the Fabric entrypoint {@value LumioApi#ENTRYPOINT}) and initializes those that are switched on and work with this
 * API version, once, during the start. An add-on is switched off on the Add-ons page (from the next start) or with
 * {@code -Dlumio.disabledAddons=<id>,<id>}. An add-on that fails to initialize is reported in the log and left out; the game starts.
 */
public final class AddonLoader {
	/** The custom value in an add-on's fabric.mod.json with the API versions it works with. */
	private static final String API_RANGE_KEY = "lumio:api";
	private static final String DISABLED_PROPERTY = "lumio.disabledAddons";

	private static boolean done;

	private AddonLoader() {
	}

	/** Initializes the add-ons; called once, after the features of Lumio exist and before the settings are read. */
	public static void load() {
		if (done) {
			return;
		}
		done = true;
		Set<String> off = AddonSwitches.disabledIds(System.getProperty(DISABLED_PROPERTY, ""));
		List<AddonInfo> loaded = new ArrayList<>();
		List<AddonInfo> disabled = new ArrayList<>();
		Map<String, String> reasons = new LinkedHashMap<>();
		AddonCollector collector = new AddonCollector();
		for (EntrypointContainer<LumioAddon> container : FabricLoader.getInstance().getEntrypointContainers(LumioApi.ENTRYPOINT,
			LumioAddon.class)) {
			LumioAddon addon;
			try {
				addon = container.getEntrypoint();
			} catch (RuntimeException | LinkageError e) {
				Logs.error("The add-on of {} could not be created", container.getProvider().getMetadata().getId(), e);
				continue;
			}
			AddonInfo info = infoOf(addon, container.getProvider());
			if (off.contains(info.id())) {
				disabled.add(info);
				reasons.put(info.id(), AddonSwitches.enabled(info.id()) ? "Off for this start (start option)" : "Off");
				Logs.info("Add-on {} is switched off", info.id());
				continue;
			}
			if (!ApiRange.accepts(info.apiRange(), LumioApi.version())) {
				disabled.add(info);
				reasons.put(info.id(), "Not loaded: needs API " + info.apiRange() + ", this is " + LumioApi.VERSION);
				Logs.warn("Add-on {} needs the API {}, this is {}: it is left out", info.id(), info.apiRange(), LumioApi.version());
				continue;
			}
			if (collector.initialize(addon, info)) {
				loaded.add(info);
				PageOutlines.apply(info.id());
				Logs.info("Add-on {} {} loaded{}", info.id(), info.version(), buildOf(container.getProvider().getMetadata()));
			} else {
				disabled.add(info);
				reasons.put(info.id(), "Could not start; the log says why");
			}
		}
		collector.freeze(loaded, disabled, reasons);
	}

	/** @return the jar's build number for the log, or nothing */
	private static String buildOf(ModMetadata meta) {
		CustomValue build = meta.getCustomValue("lumio:build");
		return build != null && build.getType() == CustomValue.CvType.STRING ? " (build " + build.getAsString() + ")" : "";
	}

	/** @return the add-on's own description, or the one of the mod that brings it */
	private static AddonInfo infoOf(LumioAddon addon, ModContainer provider) {
		AddonInfo own = addon.info();
		if (own != null) {
			return own;
		}
		ModMetadata meta = provider.getMetadata();
		CustomValue range = meta.getCustomValue(API_RANGE_KEY);
		String apiRange = range != null && range.getType() == CustomValue.CvType.STRING ? range.getAsString() : "*";
		return new AddonInfo(meta.getId(), meta.getName(), meta.getVersion().getFriendlyString(), apiRange, meta.getDescription());
	}
}
