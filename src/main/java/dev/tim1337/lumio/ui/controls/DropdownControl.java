package dev.tim1337.lumio.ui.controls;

import dev.tim1337.lumio.config.setting.EnumSetting;
import dev.tim1337.lumio.ui.UiSounds;

import java.util.List;

/** The choices of a setting as a dropdown. */
public class DropdownControl<E extends Enum<E>> extends ListDropdownControl {
	private static final int HEIGHT = 22;

	private final EnumSetting<E> setting;
	private final Runnable onChanged;
	private final List<E> choices;

	public DropdownControl(EnumSetting<E> setting, Runnable onChanged) {
		super(HEIGHT);
		this.setting = setting;
		this.onChanged = onChanged;
		this.choices = setting.choices();
	}

	@Override
	protected int count() {
		return choices.size();
	}

	@Override
	protected String name(int index) {
		return setting.nameOf(choices.get(index)).getString();
	}

	@Override
	protected int selectedIndex() {
		return choices.indexOf(setting.get());
	}

	@Override
	protected void pick(int index) {
		setting.set(choices.get(index));
		UiSounds.tick((float) index / Math.max(1, choices.size() - 1));
		onChanged.run();
	}
}
