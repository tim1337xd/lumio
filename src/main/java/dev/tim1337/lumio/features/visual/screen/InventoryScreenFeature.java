package dev.tim1337.lumio.features.visual.screen;

import dev.tim1337.lumio.addon.Addons;
import dev.tim1337.lumio.api.InventoryColumn;
import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.menu.MenuFeature;
import dev.tim1337.lumio.mixin.accessor.AbstractContainerScreenAccessor;
import dev.tim1337.lumio.mixin.accessor.SlotAccessor;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

/**
 * The inventory screen in the mod's style: glass instead of the vanilla texture, a frame per slot, and optionally the offhand slot and recipe book
 * removed. Slot contents, interaction and the player model remain vanilla.
 */
public class InventoryScreenFeature extends Feature {
	/** The offhand slot index in the player inventory (not in the menu): the same for every screen containing the inventory. */
	private static final int OFF_HAND_SLOT = 40;

	private final BooleanSetting hideOffHand = add(new BooleanSetting("hide_off_hand", Component.literal("Hide offhand slot"), true), "General");
	private final BooleanSetting hideRecipeBook = add(new BooleanSetting("hide_recipe_book", Component.literal("Hide recipe book"), true), "General");

	private final BooleanSetting effects = add(new BooleanSetting("effects", Component.literal("Effects beside the inventory"), true), "General");
	private final BooleanSetting chests = add(new BooleanSetting("chests", Component.literal("Apply to chests"), true), "General");
	/** The menu glow around the inventory and chests, which can be disabled here separately. */
	private final BooleanSetting glow = add(new BooleanSetting("glow", Component.literal("Glow"), false), "General");

	public InventoryScreenFeature() {
		super("inventory_screen", "Inventory screen", "The inventory in the style of the mod, with the off hand slot and the recipe book left out if you like.", true);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("backpack");
		summary("The inventory screen in the style of the mod");
		addResetButton();
	}

	/** @return whether the list of potion effects beside the inventory is left out, also with the style of this feature off; the effect icons in the HUD stay */
	public boolean hidesEffects() {
		return !effects.get();
	}

	/** @return whether the slot is the offhand slot, which is removed from the inventory */
	public boolean hidesOffHand(Slot slot) {
		return isEnabled() && hideOffHand.get() && slot.container instanceof Inventory && slot.getContainerSlot() == OFF_HAND_SLOT
			&& Minecraft.getInstance().gui.screen() instanceof InventoryScreen;
	}

	/** @return whether the recipe book should be removed from the inventory screen being built, per the setting */
	public boolean wantsRecipeBookHidden() {
		return isEnabled() && hideRecipeBook.get();
	}

	/** @return whether the open screen's recipe book is hidden */
	public boolean hidesRecipeBook() {
		return wantsRecipeBookHidden() && Minecraft.getInstance().gui.screen() instanceof InventoryScreen;
	}

	/** @return whether chest screens (and server menus built as chests) are styled as well */
	public boolean stylesChests() {
		return isEnabled() && chests.get();
	}

	/** Draws the glass for a chest: one panel covering the rows and the player inventory, a frame per slot, and their labels. */
	public void drawChestBackground(ContainerScreen screen, GuiGraphicsExtractor graphics, int x, int y, int width) {
		Minecraft client = Minecraft.getInstance();
		int height = ((AbstractContainerScreenAccessor) screen).lumio$height();
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, client.font, HudStyle.palette(), 0xFF000000 | MenuFeature.accentRgb(), 0f, false);
		HudStyle.panel(ui, x, y, width, height, Radius.XXL, HudStyle.opacity(), glow.get());
		int rows = screen.getMenu().getRowCount();
		for (Slot slot : screen.getMenu().slots) {
			if (slot.isActive()) {
				ui.rounded(x + slot.x - 1, y + slot.y - 1, 18, 18, Radius.SM, HudStyle.slot());
			}
		}
		int line = Ui.withOpacity(HudStyle.text(), 0.14f);
		ui.text(screen.getTitle().getString(), x + 8, y + 6, HudStyle.text());
		ui.fill(x + 8, y + 18 + rows * 18 + 4, width - 16, 1, line);
		ui.text(client.player == null ? "Inventory" : client.player.getInventory().getName().getString(), x + 8, y + height - 94, HudStyle.secondary());
		ui.fill(x + 8, y + height - 28 + 1, width - 16, 1, line);
	}

	/** How far the player inventory slots are moved down, for spacing to the crafting grid and between hotbar and inventory. */
	private static final int ROOM = 5;

	/** Moves the inventory and hotbar slots down, each by the spacing above it, and grows the screen accordingly. Idempotent: calling it again yields the same positions. */
	public void makeRoom(InventoryScreen screen) {
		for (Slot slot : screen.getMenu().slots) {
			if (!(slot.container instanceof Inventory)) {
				continue;
			}
			int index = slot.getContainerSlot();
			if (index >= 9 && index <= 35) {
				((SlotAccessor) slot).lumio$setY(84 + (index - 9) / 9 * 18 + ROOM);
			} else if (index >= 0 && index <= 8) {
				((SlotAccessor) slot).lumio$setY(142 + 2 * ROOM);
			}
		}
		var access = (AbstractContainerScreenAccessor) screen;
		access.lumio$setHeight(166 + 2 * ROOM);
		access.lumio$setTop((screen.height - (166 + 2 * ROOM)) / 2);
	}

	/** Draws the glass and slot frames in place of the vanilla texture, at its position. */
	public void drawBackground(InventoryScreen screen, GuiGraphicsExtractor graphics, int x, int y, int width, int height) {
		Minecraft client = Minecraft.getInstance();
		Ui.setZoom(1f);
		Ui ui = new Ui(graphics, client.font, HudStyle.palette(), 0xFF000000 | MenuFeature.accentRgb(), 0f, false);
		height = 166 + 2 * ROOM;
		HudStyle.panel(ui, x, y, width, height, Radius.XXL, HudStyle.opacity(), glow.get());
		// the player model gets its own box, like in the vanilla texture; slightly narrower to make room for the equipment column
		InventoryColumn column = Addons.inventoryColumn();
		boolean equipment = column != null && column.shown();
		// armor, player and equipment are separated, with a line between them
		int playerX = equipment ? x + 33 : x + 31;
		ui.rounded(playerX, y + 7, equipment ? 34 : 42, 72, Radius.MD, HudStyle.slot());
		int line = Ui.withOpacity(HudStyle.text(), 0.14f);
		ui.fill(x + 29, y + 12, 1, 62, line);
		ui.fill(x + 8, y + 84, width - 16, 1, line);
		ui.fill(x + 8, y + 146, width - 16, 1, line);
		if (equipment) {
			ui.fill(x + 70, y + 12, 1, 62, line);
			// drawn at the menu's own scale, on this screen's canvas
			Ui.setZoom(1f);
			column.draw(ui, x + 74, y + 7);
		}
		// the armor is one group in one outline, not four slots
		ui.rounded(x + 6, y + 6, 20, 74, Radius.SM, HudStyle.slot());
		ui.ring(x + 6, y + 6, 20, 74, Radius.SM, 1, Ui.withOpacity(ui.accent, 0.55f));
		for (Slot slot : screen.getMenu().slots) {
			boolean armor = slot.container instanceof Inventory && slot.x == 8 && slot.getContainerSlot() >= 36 && slot.getContainerSlot() <= 39;
			if (slot.isActive() && !armor) {
				ui.rounded(x + slot.x - 1, y + slot.y - 1, 18, 18, Radius.SM, HudStyle.slot());
			}
		}
		ui.text("Crafting", x + 97, y + 7, HudStyle.secondary());
		// arrow from the crafting grid to its result
		var pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(x + 145f, y + 36f);
		pose.rotate((float) (-Math.PI / 2.0));
		ui.icon("chevron", -5, -5, 10, Ui.withOpacity(HudStyle.secondary(), 0.8f));
		pose.popMatrix();
	}
}
