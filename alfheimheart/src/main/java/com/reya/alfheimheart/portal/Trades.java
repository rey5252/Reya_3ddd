package com.reya.alfheimheart.portal;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import vazkii.botania.api.recipe.ElvenTradeRecipe;

/**
 * Botania's elven trades as the portal uses them: the recipes of the type botania:elven_trade (Botania's
 * own, this mod's ore trades and any a pack adds), which items the elves take, and finding a trade in a
 * row of slots.
 */
public final class Trades {
    /** No recipe takes more than a few of one item: this many of each slot are offered to the recipes. */
    private static final int UNITS_PER_SLOT = 8;

    /** Botania's recipe type, found by its id from the API (the type object itself is not part of the API). */
    @SuppressWarnings("unchecked")
    @Nullable
    private static RecipeType<ElvenTradeRecipe> type() {
        return (RecipeType<ElvenTradeRecipe>) BuiltInRegistries.RECIPE_TYPE.get(ElvenTradeRecipe.TYPE_ID);
    }

    public static List<ElvenTradeRecipe> recipes(@Nullable Level level) {
        RecipeType<ElvenTradeRecipe> type = type();
        if (level == null || type == null) return List.of();
        return level.getRecipeManager().getAllRecipesFor(type);
    }

    /**
     * A "return" recipe only hands back what went in: Botania's way of saying the elves don't want the
     * item (iron, diamonds, ender pearls). The portal doesn't take such items at all.
     */
    public static boolean isReturn(ElvenTradeRecipe recipe) {
        List<ItemStack> outputs = recipe.getOutputs();
        return outputs.size() == 1 && recipe.getIngredients().size() == 1 && recipe.containsItem(outputs.get(0));
    }

    /** Whether some real trade (not a return) takes the item. */
    public static boolean accepts(@Nullable Level level, ItemStack stack) {
        if (stack.isEmpty()) return false;
        for (ElvenTradeRecipe recipe : recipes(level)) {
            if (!isReturn(recipe) && recipe.containsItem(stack)) return true;
        }
        return false;
    }

    /** A trade the slots allow: the recipe, how many items it takes from each slot, and what comes back. */
    public record Match(ElvenTradeRecipe recipe, int[] taken, ItemStack firstInput, List<ItemStack> outputs) {
    }

    /**
     * The first trade the items in slots [from, to) allow. Botania's recipes match a list of single items
     * (its gateway splits whatever is thrown in), so the slots are offered as such a list, a few items of
     * each, and the items a recipe uses are traced back to their slots.
     */
    @Nullable
    public static Match find(Level level, IItemHandler items, int from, int to) {
        List<ItemStack> units = new ArrayList<>();
        Map<ItemStack, Integer> slotOf = new IdentityHashMap<>();
        for (int slot = from; slot < to; slot++) {
            ItemStack stack = items.getStackInSlot(slot);
            int n = Math.min(stack.getCount(), UNITS_PER_SLOT);
            for (int k = 0; k < n; k++) {
                ItemStack unit = stack.copyWithCount(1);
                units.add(unit);
                slotOf.put(unit, slot);
            }
        }
        if (units.isEmpty()) return null;
        for (ElvenTradeRecipe recipe : recipes(level)) {
            if (isReturn(recipe)) continue;
            Optional<List<ItemStack>> match = recipe.match(units);
            if (match.isEmpty() || match.get().isEmpty()) continue;
            List<ItemStack> used = match.get();
            int[] taken = new int[to - from];
            boolean traced = true;
            for (ItemStack unit : used) {
                Integer slot = slotOf.get(unit);
                if (slot == null) {
                    traced = false;
                    break;
                }
                taken[slot - from]++;
            }
            if (!traced) continue;
            List<ItemStack> outputs = new ArrayList<>();
            for (ItemStack out : recipe.getOutputs(used)) {
                if (!out.isEmpty()) outputs.add(out.copy());
            }
            if (outputs.isEmpty()) continue;
            return new Match(recipe, taken, used.get(0).copy(), outputs);
        }
        return null;
    }

    /**
     * Puts the stacks into the slots [from, to) if all of them fit (merging with what is there first, then
     * into empty slots); changes nothing and returns false if they don't.
     */
    public static boolean insertAll(IItemHandler items, int from, int to, List<ItemStack> stacks, boolean simulate) {
        ItemStack[] slots = new ItemStack[to - from];
        for (int i = 0; i < slots.length; i++) slots[i] = items.getStackInSlot(from + i).copy();
        for (ItemStack in : stacks) {
            ItemStack rest = in.copy();
            for (int i = 0; i < slots.length && !rest.isEmpty(); i++) {
                ItemStack s = slots[i];
                if (s.isEmpty() || !ItemStack.isSameItemSameTags(s, rest)) continue;
                int room = Math.min(s.getMaxStackSize(), items.getSlotLimit(from + i)) - s.getCount();
                int move = Math.min(room, rest.getCount());
                if (move <= 0) continue;
                s.grow(move);
                rest.shrink(move);
            }
            for (int i = 0; i < slots.length && !rest.isEmpty(); i++) {
                if (!slots[i].isEmpty()) continue;
                int move = Math.min(Math.min(rest.getMaxStackSize(), items.getSlotLimit(from + i)), rest.getCount());
                slots[i] = rest.copyWithCount(move);
                rest.shrink(move);
            }
            if (!rest.isEmpty()) return false;
        }
        if (!simulate && items instanceof net.minecraftforge.items.IItemHandlerModifiable modifiable) {
            for (int i = 0; i < slots.length; i++) modifiable.setStackInSlot(from + i, slots[i]);
        }
        return true;
    }

    private Trades() {
    }
}
