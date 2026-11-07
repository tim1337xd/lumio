package dev.tim1337.lumio.ui.setup;

import net.minecraft.client.input.KeyEvent;

/** A step of the setup in its window: its name in the step indicator, and its content between the header and the footer. */
interface SetupStep {
	/** @return the step's name in the indicator at the top */
	String name();

	/**
	 * Draws the content of the step into its area, registering what reacts to the mouse with the kit.
	 *
	 * @param wide whether there is room for the right column with the preview; on small screens it is left out
	 */
	void draw(SetupKit kit, int x, int y, int width, int height, boolean wide);

	/** Called when the step is shown, to start what it needs (such as looking for a song). */
	default void shown() {
	}

	/** @return whether the step used the key, such as a text field of its own */
	default boolean keyPressed(KeyEvent event) {
		return false;
	}

	/** @return whether the step took a press of the left button, with controls of its own */
	default boolean mouseClicked(double mouseX, double mouseY) {
		return false;
	}

	default void mouseDragged(double mouseX, double mouseY) {
	}

	default void mouseReleased(double mouseX, double mouseY) {
	}

	/** @return whether the step took the typed character */
	default boolean charTyped(int codepoint) {
		return false;
	}
}
