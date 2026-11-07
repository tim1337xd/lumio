package dev.tim1337.lumio.api.spi;

import dev.tim1337.lumio.api.draw.Canvas;
import dev.tim1337.lumio.api.feature.AddonPanel;
import dev.tim1337.lumio.api.feature.PanelPicture;
import dev.tim1337.lumio.api.feature.SettingGroup;
import dev.tim1337.lumio.api.setting.Choice;
import dev.tim1337.lumio.api.setting.Toggle;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.function.Supplier;

/** The main mod's side of an {@link AddonPanel}: the panel as the HUD and its editor know it. */
public interface PanelHandle extends FeatureHandle {
	void offerNoBackground();

	boolean hasBackground();

	void offerCardSize();

	int cardWidth();

	int line(int base);

	int accentArgb();

	void part(String name, Toggle setting, float x, float y, float width, float height);

	void drawGlass(Canvas canvas, int width, int height, float radius);

	boolean simulated();

	void drawStack(GuiGraphicsExtractor graphics, Minecraft client, ItemStack stack, int slotX, int slotY, boolean decorations, int boxX, int boxY,
		int boxWidth, int boxHeight);

	<E extends Enum<E>> void offerModes(Choice<E> setting);

	void withoutHeader();

	int contentTop();

	/** Gives the panel the switches of the family header's two parts. */
	void offerHeader();

	void familyHeader(Canvas canvas, int width, String icon, int[] tile, String title, String meta, int metaColor, boolean dot);

	/** @return the seconds since the panel's last frame */
	float frameSeconds();

	void render(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker);

	int[] hudBounds();

	float hudScale();

	boolean hudShown();

	/** @return the radius the panel was last drawn with, scaled */
	float baseHudRadius();

	/** @return whether the HUD editor has this panel selected */
	boolean selected();
}
