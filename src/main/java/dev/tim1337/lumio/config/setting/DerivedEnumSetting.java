package dev.tim1337.lumio.config.setting;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A choice that is not stored itself but read from and written to other settings, such as a size in three steps for a finer size slider, or one
 * switch for two settings that go together. It saves nothing; the settings it stands for do.
 */
public class DerivedEnumSetting<E extends Enum<E>> extends EnumSetting<E> {
	private final Supplier<E> reader;
	private final Consumer<E> writer;

	public DerivedEnumSetting(String key, Component label, E defaultValue, Function<E, Component> displayName, Style style, Supplier<E> reader,
							  Consumer<E> writer) {
		super(key, label, defaultValue, displayName, style);
		this.reader = reader;
		this.writer = writer;
	}

	@Override
	public E get() {
		return reader.get();
	}

	@Override
	public void set(E value) {
		writer.accept(value);
	}

	@Override
	public void reset() {
		// the settings it stands for are reset themselves
	}

	@Override
	public boolean isDefault() {
		return true;
	}

	@Override
	public JsonElement save() {
		return null;
	}

	@Override
	public void load(JsonElement json) {
	}
}
