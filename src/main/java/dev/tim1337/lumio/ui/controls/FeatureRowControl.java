package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.config.Feature;
import dev.tim1337.lumio.config.setting.FeatureSwitchSetting;
import dev.tim1337.lumio.ui.Control;
import dev.tim1337.lumio.ui.MacScreen;
import dev.tim1337.lumio.ui.Ui;

import net.minecraft.network.chat.Component;

/** A feature's switch and the button that opens its settings, side by side, to the right of its name. */
public class FeatureRowControl extends Control {
	private static final int EDIT_WIDTH = 56;
	private static final int SWITCH_WIDTH = 34;
	private static final int GAP = 10;

	private final SwitchControl toggle;
	private final ButtonControl edit;

	public FeatureRowControl(Feature feature, boolean editable, Runnable onChanged) {
		this.toggle = feature.isSwitchable() ? new SwitchControl(new FeatureSwitchSetting("row_" + feature.id(), feature.name(), () -> feature), onChanged) : null;
		this.edit = editable ? new ButtonControl(Component.literal("Edit"), false, () -> MacScreen.openEdit(feature)) : null;
	}

	@Override
	protected int measure(Ui ui, int width) {
		return 22;
	}

	@Override
	public int inlineWidth(Ui ui, int available) {
		int total = (edit != null ? EDIT_WIDTH : 0) + (toggle != null ? SWITCH_WIDTH : 0) + (edit != null && toggle != null ? GAP : 0);
		return Math.min(available, Math.max(total, 1));
	}

	@Override
	public boolean hasEditButton() {
		return edit != null && toggle != null;
	}

	@Override
	public boolean isLoneButton() {
		return edit != null && toggle == null;
	}

	/** Places the switch at the right edge, like in every row, and the Edit button to its left. */
	private void arrange(Ui ui) {
		int right = x + width;
		if (toggle != null) {
			toggle.place(ui, right - SWITCH_WIDTH, y + 1, SWITCH_WIDTH);
			right -= SWITCH_WIDTH + GAP;
		}
		if (edit != null) {
			edit.place(ui, right - EDIT_WIDTH, y, EDIT_WIDTH);
		}
	}

	@Override
	public void render(Ui ui, int mouseX, int mouseY) {
		arrange(ui);
		if (toggle != null) {
			toggle.render(ui, mouseX, mouseY);
		}
		if (edit != null) {
			edit.render(ui, mouseX, mouseY);
		}
	}

	@Override
	public boolean mouseClicked(double mouseX, double mouseY, int button) {
		return (toggle != null && toggle.mouseClicked(mouseX, mouseY, button)) || (edit != null && edit.mouseClicked(mouseX, mouseY, button));
	}

	@Override
	public void mouseReleased(double mouseX, double mouseY) {
		if (edit != null) {
			edit.mouseReleased(mouseX, mouseY);
		}
		if (toggle != null) {
			toggle.mouseReleased(mouseX, mouseY);
		}
	}
}
