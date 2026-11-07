package dev.tim1337.lumio.mixin.world;

import dev.tim1337.lumio.ModFeatures;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import net.minecraft.world.attribute.EnvironmentAttribute;
import net.minecraft.world.attribute.EnvironmentAttributeSystem;
import net.minecraft.world.attribute.SpatialAttributeInterpolator;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps the biome out of the sky and fog colors under the mod's sky. The camera's attribute probe asks the attribute system once per tick for each
 * value at the camera's position, which blends in the colors of the biomes around it, so the whole sky changed color at a biome border. While the
 * mod's sky replaces them, the probe gets the dimension's value instead: its base color with the time of day applied, the same in every biome.
 */
@Mixin(targets = "net.minecraft.world.attribute.EnvironmentAttributeProbe$ValueProbe")
public abstract class BiomeSkyColorMixin {
	@WrapOperation(method = "getValueFromLevel", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/attribute/EnvironmentAttributeSystem;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;Lnet/minecraft/world/phys/Vec3;Lnet/minecraft/world/attribute/SpatialAttributeInterpolator;)Ljava/lang/Object;"))
	private <Value> Value lumio$withoutBiome(EnvironmentAttributeSystem system, EnvironmentAttribute<Value> attribute, Vec3 position,
													   SpatialAttributeInterpolator interpolator, Operation<Value> original) {
		if (ModFeatures.SKY.replacesBiomeColor(system, attribute)) {
			return system.getDimensionValue(attribute);
		}
		return original.call(system, attribute, position, interpolator);
	}
}
