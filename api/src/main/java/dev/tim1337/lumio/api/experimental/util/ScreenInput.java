package dev.tim1337.lumio.api.experimental.util;

//#if 26.3
//$ import com.mojang.blaze3d.platform.InputConstants;
//$ import org.lwjgl.sdl.SDLKeyboard;
//#endif

import net.minecraft.client.input.MouseButtonEvent;

/**
 * Mouse buttons and modifier keys in one meaning on every Minecraft version: the game's numbers for them changed between versions. What the mod and its
 * add-ons compare, keep in a config or hand to each other goes through here. Keys stay the game's own numbers ({@code InputConstants.KEY_...}).
 */
public final class ScreenInput {
	/** The left mouse button, as every control of the mod and its add-ons gets it. */
	public static final int LEFT = 0;
	/** The right mouse button. */
	public static final int RIGHT = 1;
	/** The middle mouse button. */
	public static final int MIDDLE = 2;

	/** Either Shift key, in the value of {@link #modifiers}. */
	public static final int SHIFT = 1;
	/** Either Ctrl key. */
	public static final int CONTROL = 2;
	/** Either Alt key. */
	public static final int ALT = 4;
	/** Either system key (Windows, Command). */
	public static final int SUPER = 8;

	private ScreenInput() {
	}

	/** @return the button of a click, as {@link #LEFT}, {@link #RIGHT} or {@link #MIDDLE} */
	public static int button(MouseButtonEvent event) {
		return button(event.button());
	}

	/** @return the game's number of a mouse button, as {@link #LEFT}, {@link #RIGHT} or {@link #MIDDLE} */
	public static int button(int gameButton) {
		//#if 26.3
		//$ return switch (gameButton) {
		//$ 	case InputConstants.MOUSE_BUTTON_LEFT -> LEFT;
		//$ 	case InputConstants.MOUSE_BUTTON_RIGHT -> RIGHT;
		//$ 	case InputConstants.MOUSE_BUTTON_MIDDLE -> MIDDLE;
		//$ 	default -> gameButton - 1;
		//$ };
		//#else
		return gameButton;
		//#endif
	}

	/** @return the game's number of a button that {@link #button} gave, to bind a key mapping to it */
	public static int gameButton(int button) {
		//#if 26.3
		//$ return switch (button) {
		//$ 	case LEFT -> InputConstants.MOUSE_BUTTON_LEFT;
		//$ 	case RIGHT -> InputConstants.MOUSE_BUTTON_RIGHT;
		//$ 	case MIDDLE -> InputConstants.MOUSE_BUTTON_MIDDLE;
		//$ 	default -> button + 1;
		//$ };
		//#else
		return button;
		//#endif
	}

	/** @return the game's modifier keys as {@link #SHIFT}, {@link #CONTROL}, {@link #ALT} and {@link #SUPER}; left and right count the same */
	public static int modifiers(int gameModifiers) {
		//#if 26.3
		//$ return ((gameModifiers & InputConstants.MOD_SHIFT) != 0 ? SHIFT : 0) | ((gameModifiers & InputConstants.MOD_CONTROL) != 0 ? CONTROL : 0)
		//$ 	| ((gameModifiers & InputConstants.MOD_ALT) != 0 ? ALT : 0) | ((gameModifiers & InputConstants.MOD_SUPER) != 0 ? SUPER : 0);
		//#else
		return gameModifiers;
		//#endif
	}

	/**
	 * For a shortcut such as Ctrl+V, where what counts is what the key is labeled with, not where it lies on the keyboard (on some layouts Z and Y change
	 * places).
	 *
	 * @param key    the game's number of a pressed key
	 * @param letter the letter, in capitals
	 * @return whether the key is the one with this letter on the keyboard of the player
	 */
	public static boolean letter(int key, char letter) {
		//#if 26.3
		//$ return SDLKeyboard.SDL_GetKeyFromScancode(key, (short) 0, false) == Character.toLowerCase(letter);
		//#else
		return key == letter;
		//#endif
	}

	//#if 26.3
	//$ private static final Object TEXT_OWNER = new Object();
	//#endif
	private static boolean textWanted;
	private static boolean textOn;

	/**
	 * Says that a text field has the focus now; to be called every frame while it has. The game asks the operating system for typed text by itself only for its own text
	 * fields. Without this, on Minecraft 26.3 (SDL's text input) a field of the mod never gets a character, whatever the player types, and on 26.2 it gets
	 * them but no input method (IME) works for it.
	 */
	public static void wantText() {
		textWanted = true;
	}

	/** Called once per frame by the main mod: asks for typed text or stops asking, as the fields said. */
	public static void applyText() {
		boolean wanted = textWanted;
		textWanted = false;
		var input = net.minecraft.client.Minecraft.getInstance().textInputManager();
		//#if 26.3
		//$ if (wanted) {
		//$ 	// asked again every frame: a text field of the game may have taken and let go of it in between
		//$ 	input.startTextInput(TEXT_OWNER);
		//$ } else if (textOn) {
		//$ 	input.stopTextInput(TEXT_OWNER);
		//$ }
		//#else
		if (wanted && !textOn) {
			input.startTextInput();
		} else if (!wanted && textOn) {
			input.stopTextInput();
		}
		//#endif
		textOn = wanted;
	}

	/** @return the game's number of the minus key of the number block */
	public static int numpadMinus() {
		//#if 26.3
		//$ return NUMPAD_MINUS_SCANCODE;
		//#else
		return NUMPAD_MINUS_KEY;
		//#endif
	}

	//#if 26.3
	//$ private static final int NUMPAD_MINUS_SCANCODE = 86;
	//#else
	private static final int NUMPAD_MINUS_KEY = 333;
	//#endif
}
