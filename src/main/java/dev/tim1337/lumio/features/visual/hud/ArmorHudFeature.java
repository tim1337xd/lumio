package dev.tim1337.lumio.features.visual.hud;

import dev.tim1337.lumio.api.experimental.draw.Radius;
import dev.tim1337.lumio.api.experimental.draw.SystemColors;
import dev.tim1337.lumio.config.setting.BooleanSetting;
import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.hud.HudPanelFeature;
import dev.tim1337.lumio.ui.HudEditScreen;
import dev.tim1337.lumio.ui.HudStyle;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;

/** The worn armor in the info bar style, with each piece's remaining durability. The vanilla armor bar can be hidden. */
public class ArmorHudFeature extends HudPanelFeature {
	public enum Direction {
		HORIZONTAL("Across"),
		VERTICAL("Down");

		private final Component displayName;

		Direction(String displayName) {
			this.displayName = Component.literal(displayName);
		}

		public Component displayName() {
			return displayName;
		}
	}

	private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
	private static final int SLOT = 22;
	private static final int PAD = 6;

	private final EnumSetting<Direction> direction = add(new EnumSetting<>("direction", Component.literal("Direction"), Direction.HORIZONTAL, Direction::displayName, EnumSetting.Style.SEGMENTED), "General");
	private final BooleanSetting wear = add(new BooleanSetting("wear", Component.literal("Durability bars"), true), "General");
	private final BooleanSetting percent = add(new BooleanSetting("percent", Component.literal("Durability in percent"), false), "General");
	private final BooleanSetting hideVanilla = add(new BooleanSetting("hide_vanilla", Component.literal("Hide vanilla armor bar"), true), "General");
	private final BooleanSetting dividers = add(new BooleanSetting("dividers", Component.literal("Dividers"), false), "General");
	private final BooleanSetting emptySlots = add(new BooleanSetting("empty_slots", Component.literal("Empty slots"), false), "General");
	private final float[] shownWear = {-1f, -1f, -1f, -1f};

	public ArmorHudFeature() {
		super("armor_hud", "Armor", "The armor you wear, with how worn it is.", false, Anchor.LEFT, true, 12f, 4f);
		iconRgb(SystemColors.TEAL_RGB);
		iconName("shield");
		summary("The armor you wear, with how worn it is");

	}

	public boolean hidesVanillaBar() {
		return isEnabled() && hideVanilla.get();
	}

	private boolean vertical() {
		return direction.get() == Direction.VERTICAL;
	}

	private int rowHeight() {
		return SLOT + (wear.get() ? 5 : 0) + (percent.get() ? 9 : 0);
	}

	@Override
	protected int panelWidth() {
		return vertical() ? PAD * 2 + SLOT + (percent.get() ? 24 : 0) : PAD * 2 + 4 * SLOT;
	}

	@Override
	protected int panelHeight() {
		return vertical() ? PAD * 2 + 4 * (SLOT + (wear.get() ? 5 : 0)) : PAD * 2 + rowHeight();
	}

	/** Without armor the panel would be an empty glass pane: it is not drawn, unless it is configured to show empty slots. */
	@Override
	protected boolean shown(Minecraft client) {
		if (emptySlots.get()) {
			return true;
		}
		for (EquipmentSlot slot : SLOTS) {
			if (!client.player.getItemBySlot(slot).isEmpty()) {
				return true;
			}
		}
		return false;
	}

	private static final net.minecraft.world.item.Item[] SAMPLE = {net.minecraft.world.item.Items.IRON_HELMET, net.minecraft.world.item.Items.DIAMOND_CHESTPLATE, net.minecraft.world.item.Items.IRON_LEGGINGS, net.minecraft.world.item.Items.GOLDEN_BOOTS};

	@Override
	protected void drawPanel(Ui ui, GuiGraphicsExtractor graphics, Minecraft client) {
		drawGlass(ui, panelWidth(), panelHeight(), 12f);
		// in the editor it also shows without armor, with the slots it would fill, so it is not an empty box
		boolean slots = emptySlots.get() || HudEditScreen.isOpen() || simulated();
		int accentColor = accentArgb();
		if (dividers.get()) {
			for (int i = 1; i < 4; i++) {
				if (vertical()) {
					ui.fill(PAD + 4, PAD + i * (SLOT + (wear.get() ? 5 : 0)) - 1, SLOT - 8 + (percent.get() ? 24 : 0), 1, HudStyle.line(0.16f));
				} else {
					ui.fill(PAD + i * SLOT, PAD + 4, 1, rowHeight() - 8, HudStyle.line(0.16f));
				}
			}
		}
		for (int i = 0; i < 4; i++) {
			ItemStack stack = client.player.getItemBySlot(SLOTS[i]);
			// a preview shows what the panel is for, also without armor on
			if (stack.isEmpty() && simulated()) {
				stack = SAMPLE[i].getDefaultInstance();
			}
			int slotX = vertical() ? PAD : PAD + i * SLOT;
			int slotY = vertical() ? PAD + i * (SLOT + (wear.get() ? 5 : 0)) : PAD;
			if (stack.isEmpty()) {
				if (slots) {
					ui.rounded(slotX + 2, slotY + 2, SLOT - 4, SLOT - 4, Radius.SM, HudStyle.slot());
				}
				continue;
			}
			ui.rounded(slotX + 2, slotY + 2, SLOT - 4, SLOT - 4, Radius.SM, HudStyle.slot());
			drawStack(graphics, client, stack, slotX + 3, slotY + 3, false);
			float left = stack.isDamageableItem() ? 1f - (float) stack.getDamageValue() / stack.getMaxDamage() : 1f;
			shownWear[i] = shownWear[i] < 0f ? left : shownWear[i] + (left - shownWear[i]) * 0.2f;
			if (wear.get()) {
				int barX = slotX + 3;
				int barY = slotY + SLOT + 1;
				ui.rounded(barX, barY, SLOT - 6, 2, Radius.pill(2), Ui.withOpacity(HudStyle.text(), 0.16f));
				float amount = Mth.clamp(shownWear[i], 0f, 1f);
				// green when intact, red when almost broken
				int color = Ui.mix(SystemColors.RED, SystemColors.GREEN, amount);
				ui.rounded(barX, barY, Math.max(1, Math.round((SLOT - 6) * amount)), 2, Radius.pill(2), stack.isDamageableItem() ? color : accentColor);
			}
			if (percent.get() && stack.isDamageableItem()) {
				String text = Math.round(left * 100f) + "%";
				if (vertical()) {
					ui.textMiddle(text, slotX + SLOT + 4, slotY + SLOT / 2, HudStyle.text());
				} else {
					ui.textCentered(text, slotX + SLOT / 2, slotY + SLOT + (wear.get() ? 11 : 6), HudStyle.text());
				}
			}
		}
	}
}
