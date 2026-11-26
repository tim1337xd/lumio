package dev.tim1337.lumio.mixin.accessor;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Map;

/** The game's tables of key mappings (by name and by key) and a mapping's count of presses, for key combinations ({@code KeyCombos}). */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("MAP")
	static Map<InputConstants.Key, List<KeyMapping>> lumio$map() {
		throw new AssertionError();
	}

	@Accessor("ALL")
	static Map<String, KeyMapping> lumio$all() {
		throw new AssertionError();
	}

	@Accessor("clickCount")
	int lumio$clickCount();

	@Accessor("clickCount")
	void lumio$setClickCount(int count);
}
