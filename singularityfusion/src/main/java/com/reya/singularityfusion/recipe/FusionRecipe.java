package com.reya.singularityfusion.recipe;

import java.util.List;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.reya.singularityfusion.SingularityFusion;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.crafting.CraftingHelper;

/**
 * A singularity fusion: a catalyst in the core, one ingredient on each graviton pylon (in any order, every pylon used),
 * energy the core must hold, and how long the fusion takes. As JSON:
 * <pre>{
 *   "type": "singularityfusion:fusion",
 *   "catalyst": {"item": "minecraft:nether_star"},
 *   "ingredients": [{"item": "minecraft:echo_shard", "count": 2}, {"tag": "forge:ingots/netherite"}],
 *   "result": {"item": "singularityfusion:singularity_shard"},
 *   "energy": 64000000,
 *   "time": 200
 * }</pre>
 * An ingredient with a count stands for that many pylons.
 */
public class FusionRecipe implements Recipe<Container> {
    private final ResourceLocation id;
    private final Ingredient catalyst;
    private final NonNullList<Ingredient> ingredients;
    private final ItemStack result;
    private final long energy;
    private final int time;

    public FusionRecipe(ResourceLocation id, Ingredient catalyst, NonNullList<Ingredient> ingredients, ItemStack result, long energy, int time) {
        this.id = id;
        this.catalyst = catalyst;
        this.ingredients = ingredients;
        this.result = result;
        this.energy = energy;
        this.time = time;
    }

    public Ingredient catalyst() {
        return catalyst;
    }

    /** The energy the core must hold to start, all spent when the fusion ends. */
    public long energy() {
        return energy;
    }

    /** Ticks a fusion takes (before the config's multiplier). */
    public int time() {
        return time;
    }

    public ItemStack result() {
        return result;
    }

    /** Whether a catalyst and the pylons' items (one each, the empty pylons left out) make this recipe. */
    public boolean matches(ItemStack catalyst, List<ItemStack> pylonItems) {
        if (catalyst.isEmpty() || !this.catalyst.test(catalyst) || pylonItems.size() != ingredients.size()) return false;
        return assign(pylonItems, new boolean[ingredients.size()], 0);
    }

    /** Gives each item an ingredient of its own (trying the others when one leads nowhere). */
    private boolean assign(List<ItemStack> items, boolean[] used, int i) {
        if (i == items.size()) return true;
        for (int k = 0; k < ingredients.size(); k++) {
            if (used[k] || !ingredients.get(k).test(items.get(i))) continue;
            used[k] = true;
            if (assign(items, used, i + 1)) return true;
            used[k] = false;
        }
        return false;
    }

    /** The core matches with its pylons (matches(catalyst, items)), not a container. */
    @Override
    public boolean matches(Container container, Level level) {
        return false;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registries) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registries) {
        return result;
    }

    @Override
    public NonNullList<Ingredient> getIngredients() {
        return ingredients;
    }

    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SingularityFusion.FUSION_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return SingularityFusion.FUSION_TYPE.get();
    }

    public static class Serializer implements RecipeSerializer<FusionRecipe> {
        @Override
        public FusionRecipe fromJson(ResourceLocation id, JsonObject json) {
            Ingredient catalyst = Ingredient.fromJson(GsonHelper.getNonNull(json, "catalyst"));
            NonNullList<Ingredient> ingredients = NonNullList.create();
            JsonArray list = GsonHelper.getAsJsonArray(json, "ingredients");
            for (JsonElement element : list) {
                Ingredient ingredient = Ingredient.fromJson(element);
                int count = element.isJsonObject() ? GsonHelper.getAsInt(element.getAsJsonObject(), "count", 1) : 1;
                for (int i = 0; i < count; i++) ingredients.add(ingredient);
            }
            if (ingredients.isEmpty()) throw new JsonParseException("A fusion recipe needs at least one ingredient");
            ItemStack result = CraftingHelper.getItemStack(GsonHelper.getAsJsonObject(json, "result"), true);
            long energy = GsonHelper.getAsLong(json, "energy");
            int time = GsonHelper.getAsInt(json, "time", 200);
            if (energy < 0 || time < 1) throw new JsonParseException("A fusion's energy can't be negative nor its time under a tick");
            return new FusionRecipe(id, catalyst, ingredients, result, energy, time);
        }

        @Override
        public FusionRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            Ingredient catalyst = Ingredient.fromNetwork(buf);
            int n = buf.readVarInt();
            NonNullList<Ingredient> ingredients = NonNullList.create();
            for (int i = 0; i < n; i++) ingredients.add(Ingredient.fromNetwork(buf));
            ItemStack result = buf.readItem();
            long energy = buf.readVarLong();
            int time = buf.readVarInt();
            return new FusionRecipe(id, catalyst, ingredients, result, energy, time);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, FusionRecipe recipe) {
            recipe.catalyst.toNetwork(buf);
            buf.writeVarInt(recipe.ingredients.size());
            for (Ingredient ingredient : recipe.ingredients) ingredient.toNetwork(buf);
            buf.writeItem(recipe.result);
            buf.writeVarLong(recipe.energy);
            buf.writeVarInt(recipe.time);
        }
    }
}
