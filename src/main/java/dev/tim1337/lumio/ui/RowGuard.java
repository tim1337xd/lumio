package dev.tim1337.lumio.ui;

import dev.tim1337.lumio.api.experimental.util.Logs;
import dev.tim1337.lumio.config.setting.Setting;

import java.util.HashSet;
import java.util.Set;

/**
 * Keeps one broken row from emptying a settings page: whatever a row throws while it is checked, built, measured or drawn is caught here, logged
 * once with the row's id, and the row is left out, while the rest of the page is shown.
 */
public final class RowGuard {
	/** Rows already logged, so a row that fails every frame logs only once; bounded, as rows are few. */
	private static final Set<String> REPORTED = new HashSet<>();
	private static final int MOST_REPORTED = 256;

	private RowGuard() {
	}

	/** Logs a row's failure, once per row. */
	public static void report(String id, RuntimeException e) {
		if (REPORTED.size() < MOST_REPORTED && REPORTED.add(id)) {
			Logs.error("The settings row {} failed and is left out", id, e);
		}
	}

	/** @return whether a setting is shown; one whose condition throws is not */
	public static boolean visible(Setting<?> setting) {
		try {
			return setting.isVisible();
		} catch (RuntimeException e) {
			report(setting.key(), e);
			return false;
		}
	}

	/** @return the control of a setting, or null when it cannot be built */
	public static Control control(Setting<?> setting, Runnable onChanged) {
		try {
			return setting.createControl(onChanged);
		} catch (RuntimeException e) {
			report(setting.key(), e);
			return null;
		}
	}
}
