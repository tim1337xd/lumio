package dev.tim1337.lumio.config.setting;

import java.util.Collection;
import java.util.Iterator;

/**
 * Whether two binds on the same key (or combination) get in each other's way. Each bind has a context: it acts in the world (no screen open), in
 * any menu, only in one menu, or while typing. Two binds clash only where both would act in the same situation. A bind of one menu is used
 * instead of a bind of any menu while its menu is open (a key per slot of the Loadouts menu, over the hotbar key): that is no clash, the more
 * specific one wins. Plain data, so it is unit tested.
 */
public final class KeyClash {
	/** How two binds on the same key stand to each other, seen from the first. */
	public enum Kind {
		/** They never act in the same situation. */
		NONE,
		/** Both act: a real conflict. */
		BOTH,
		/** The first is used instead of the second where the first acts. */
		OVERRIDES,
		/** The second is used instead of the first where the second acts. */
		OVERRIDDEN
	}

	/** Where a bind acts: in the world, in screens (in any menu, or only in the one named), or while typing. */
	public record Context(KeySetting.Where where, String menu) {
		public static final Context WORLD = new Context(KeySetting.Where.WORLD, null);
		public static final Context ANY_MENU = new Context(KeySetting.Where.SCREENS, null);
		public static final Context TYPING = new Context(KeySetting.Where.TYPING, null);

		/** @return the context of a bind that acts only while one menu is open */
		public static Context menu(String name) {
			return new Context(KeySetting.Where.SCREENS, name);
		}
	}

	/** How many names a hint lists before it counts the rest. */
	static final int MOST_NAMES = 2;

	private KeyClash() {
	}

	/** @return how a bind in one context stands to a bind on the same key in another */
	public static Kind between(Context own, Context other) {
		if (own.where() != other.where()) {
			return Kind.NONE;
		}
		if (own.where() != KeySetting.Where.SCREENS) {
			return Kind.BOTH;
		}
		if (own.menu() == null) {
			return other.menu() == null ? Kind.BOTH : Kind.OVERRIDDEN;
		}
		if (other.menu() == null) {
			return Kind.OVERRIDES;
		}
		return own.menu().equals(other.menu()) ? Kind.BOTH : Kind.NONE;
	}

	/**
	 * @param others the contexts of the other bind, which may act in more than one (a hotbar key: in the world and in menus)
	 * @return the strongest of the ways the two stand: a real conflict before an override
	 */
	public static Kind between(Context own, Collection<Context> others) {
		Kind result = Kind.NONE;
		for (Context other : others) {
			Kind kind = between(own, other);
			if (kind == Kind.BOTH) {
				return Kind.BOTH;
			}
			if (kind == Kind.OVERRIDES || result == Kind.NONE) {
				result = kind;
			}
		}
		return result;
	}

	/** @return the hint of a real conflict, "Also triggers: Drop Item, Music › Next song and 2 more"; null without names */
	public static String conflictHint(Collection<String> names) {
		return names.isEmpty() ? null : "Also triggers: " + listed(names);
	}

	/** @return the quiet note of a bind that wins in its menu, "Used instead of Hotbar Slot 1 while the Loadouts menu is open"; null without names */
	public static String overrideNote(Collection<String> names, String menu) {
		return names.isEmpty() || menu == null ? null : "Used instead of " + listed(names) + " while the " + menu + " menu is open";
	}

	/** @return at most two of the names (each once, in their order), then how many more there are */
	static String listed(Collection<String> names) {
		StringBuilder text = new StringBuilder();
		Iterator<String> each = names.iterator();
		for (int i = 0; i < MOST_NAMES && each.hasNext(); i++) {
			text.append(i == 0 ? "" : ", ").append(each.next());
		}
		int more = names.size() - MOST_NAMES;
		if (more > 0) {
			text.append(" and ").append(more).append(" more");
		}
		return text.toString();
	}
}
