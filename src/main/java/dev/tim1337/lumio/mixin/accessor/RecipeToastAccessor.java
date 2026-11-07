package dev.tim1337.lumio.mixin.accessor;

import net.minecraft.client.gui.components.toasts.RecipeToast;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

/** The recipes of a recipe toast and which one it shows now, to draw it in the mod's style. */
@Mixin(RecipeToast.class)
public interface RecipeToastAccessor {
	/** The entries are of a private type of the game; see {@link RecipeEntryAccessor}. */
	@Accessor("recipeItems")
	List<?> lumio$items();

	@Accessor("displayedRecipeIndex")
	int lumio$shown();
}
