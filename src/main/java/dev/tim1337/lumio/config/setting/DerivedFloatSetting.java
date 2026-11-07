package dev.tim1337.lumio.config.setting;

import com.google.gson.JsonElement;

import net.minecraft.network.chat.Component;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A slider that is not stored itself but read from and written to other settings, such as one slider for two values that go together. It saves
 * nothing; the settings it stands for do.
 */
public class DerivedFloatSetting extends FloatSetting {
	private final Supplier<Float> reader;
	private final Consumer<Float> writer;

	public DerivedFloatSetting(String key, Component label, float min, float max, int decimals, Supplier<Float> reader, Consumer<Float> writer) {
		super(key, label, min, min, max, decimals);
		this.reader = reader;
		this.writer = writer;
	}

	@Override
	public Float get() {
		return reader.get();
	}

	@Override
	public void set(Float value) {
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
