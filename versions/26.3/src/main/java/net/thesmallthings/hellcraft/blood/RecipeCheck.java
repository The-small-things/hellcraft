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
		int fragments = 0;
		for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
			if (!holder.id().identifier().getNamespace().equals(HellcraftMod.MOD_ID)) {
				continue;
			}
			recipes++;
			List<Ingredient> ingredients = holder.value().placementInfo().ingredients();
			if (ingredients.stream().anyMatch(i -> i.test(heart))) {
				hearts++;
			}
			if (ingredients.stream().anyMatch(i -> i.test(fragment))) {
				fragments++;
			}
		}
		HellcraftMod.LOGGER.info("Hellcraft recipes: {}; take Blood Hearts: {}; take Blood Fragments: {}", recipes, hearts, fragments);
	}
}
