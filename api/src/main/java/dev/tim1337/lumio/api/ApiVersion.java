package dev.tim1337.lumio.api;

/**
 * A version of the add-on API: major.minor.patch. A new minor version only adds; a new major version may change or remove what was deprecated
 * before. An add-on built for 1.2 runs with every 1.x from 1.2 on.
 */
public record ApiVersion(int major, int minor, int patch) implements Comparable<ApiVersion> {
	/** @return whether this version has everything of the given one: the same major version, and at least its minor version */
	public boolean provides(int wantedMajor, int wantedMinor) {
		return major == wantedMajor && minor >= wantedMinor;
	}

	@Override
	public int compareTo(ApiVersion other) {
		if (major != other.major) {
			return Integer.compare(major, other.major);
		}
		if (minor != other.minor) {
			return Integer.compare(minor, other.minor);
		}
		return Integer.compare(patch, other.patch);
	}

	@Override
	public String toString() {
		return major + "." + minor + "." + patch;
	}

	/** @return the version written as "1.2.3" (a missing minor or patch counts as 0, as in "<2") @throws IllegalArgumentException if it is not one */
	public static ApiVersion parse(String text) {
		String[] parts = text.strip().split("\\.");
		if (parts.length > 3) {
			throw new IllegalArgumentException("Not an API version: " + text);
		}
		try {
			return new ApiVersion(Integer.parseInt(parts[0]), parts.length > 1 ? Integer.parseInt(parts[1]) : 0, parts.length > 2 ? Integer.parseInt(parts[2]) : 0);
		} catch (NumberFormatException e) {
			throw new IllegalArgumentException("Not an API version: " + text, e);
		}
	}
}
