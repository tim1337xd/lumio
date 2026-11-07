package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.FloatSetting;
import dev.tim1337.lumio.hud.HudPanelFeature;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** The inventory contents in the info bar style, visible without opening the inventory. */
public class InventoryHudFeature extends HudPanelFeature {
	public enum When {
		ALWAYS("Always"),
		SNEAKING("While sneaking");

		private final Component displayName;

		When(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	private static final int SLOT = 18;
	private static final int PAD = 6;

	private final BooleanSetting decorations = add(new BooleanSetting("decorations", Component.literal("Item counts and durability"), true), "General");
	private final BooleanSetting emptySlots = add(new BooleanSetting("empty_slots", Component.literal("Empty slots"), true), "General");
	/** Panel width: spacing between the columns, spreading the slots and widening the panel while items keep their size. */
	private final FloatSetting width = add(new FloatSetting("width", Component.literal("Spacing"), 0f, 0f, 1f, 2), "General", "0 is tight");

	public InventoryHudFeature() {
		super("inventory_hud", "Inventory", "The things in your inventory, always in view.", false, Anchor.RIGHT, false, 12f, 90f);
		iconRgb(SystemColors.ORANGE_RGB);
		iconName("grid");
		summary("The things in your inventory, always in view");

	}

	private int rowCount() {
		return 3;
	}

	/** @return the distance from one column to the next: a slot plus the configured spacing */
	private int step() {
		return SLOT + Math.round(width.get() * 14f);
	}

	@Override
	protected int panelWidth() {
		return PAD * 2 + 8 * step() + SLOT;
	}

	@Override
	protected int panelHeight() {
		return PAD * 2 + rowCount() * SLOT;
	}

	@Override
	protected boolean shown(Minecraft client) {
		return true;
	}

	private static final int SAMPLE_EVERY = 3;
	private static final net.minecraft.world.item.Item[] SAMPLE = {net.minecraft.world.item.Items.DIAMOND, net.minecraft.world.item.Items.ENDER_PEARL, net.minecraft.world.item.Items.GOLDEN_APPLE, net.minecraft.world.item.Items.IRON_PICKAXE, net.minecraft.world.item.Items.BOOK};

	@Override
	protected void drawPanel(Ui ui, GuiGraphicsExtractor graphics, Minecraft client) {
		drawGlass(ui, panelWidth(), panelHeight(), 12f);
		var inventory = client.player.getInventory();
		for (int row = 0; row < rowCount(); row++) {
			for (int column = 0; column < 9; column++) {
				ItemStack stack = inventory.getItem(9 + row * 9 + column);
				// a preview shows what the panel is for, also with an empty inventory
				if (stack.isEmpty() && simulated() && (row + column * 2) % SAMPLE_EVERY == 0) {
					stack = SAMPLE[(row + column) % SAMPLE.length].getDefaultInstance();
				}
				int slotX = PAD + column * step();
				int slotY = PAD + row * SLOT;
				if (stack.isEmpty()) {
					if (emptySlots.get()) {
						ui.rounded(slotX + 1, slotY + 1, SLOT - 2, SLOT - 2, Radius.SM, HudStyle.slot());
					}
					continue;
				}
				ui.rounded(slotX + 1, slotY + 1, SLOT - 2, SLOT - 2, Radius.SM, HudStyle.slot());
				drawStack(graphics, client, stack, slotX + 1, slotY + 1, decorations.get());
			}
		}
	}
}
