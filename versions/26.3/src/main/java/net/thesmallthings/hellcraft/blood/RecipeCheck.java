package net.thesmallthings.hellcraft.blood;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.thesmallthings.hellcraft.HellcraftMod;

import java.util.List;

/**
 * Checks at startup that real Blood Hearts and Fragments still fit Hellcraft's recipes. The recipes
 * (written by tools/gen_models.py) name the items' components exactly, so the recipe book can show
 * them properly; this catches any drift between that JSON and {@link BloodItems}.
 */
public final class RecipeCheck {
	private RecipeCheck() {
	}

	public static void run(MinecraftServer server) {
		ItemStack heart = BloodItems.heart(1);
		ItemStack fragment = BloodItems.fragment(1);
		int recipes = 0;
		int hearts = 0;
		boolean fragmentFits = false;
		for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			if (!holder.id().identifier().getNamespace().equals(HellcraftMod.MOD_ID)) {
				continue;
			}
			recipes++;
			List<Ingredient> ingredients = holder.value().placementInfo().ingredients();
			if (ingredients.stream().anyMatch(i -> i.test(heart))) {
				hearts++;
			} else {
				HellcraftMod.LOGGER.warn("Recipe {} does not accept a Blood Heart", holder.id().identifier());
			}
			fragmentFits |= ingredients.stream().anyMatch(i -> i.test(fragment));
		}
		HellcraftMod.LOGGER.info("Blood recipes accept Blood Hearts: {}/{}{}", hearts, recipes, fragmentFits ? "" : " (and no recipe accepts a Blood Fragment!)");
	}
}
