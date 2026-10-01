package com.reya.alfheimheart.machine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.annotation.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.IItemHandlerModifiable;

/**
 * Botania's recipes as the machines use them: Botania's altars and plates take their inputs one item at a
 * time, and a recipe matches exactly the items it lists, so a machine picks one item per ingredient out of its
 * input slots (the most particular ingredients first), shows the recipe just those, and knows how many to take
 * from each slot.
 */
public final class Units {
    /** One item for each ingredient: how many from each slot, and the single items themselves. */
    public record Pick(int[] taken, List<ItemStack> units) {
        public Container container() {
            return new SimpleContainer(units.toArray(new ItemStack[0]));
        }
    }

    /** A recipe type from Botania (by id: the type objects are not part of its API). */
    @SuppressWarnings("unchecked")
    @Nullable
    public static <T extends Recipe<?>> RecipeType<T> type(ResourceLocation id) {
        return (RecipeType<T>) BuiltInRegistries.RECIPE_TYPE.get(id);
    }

    /** The recipes of a type, those with the most ingredients first (so a smaller recipe never eats a bigger one's items). */
    public static <C extends Container, T extends Recipe<C>> List<T> recipes(@Nullable Level level, ResourceLocation typeId) {
        RecipeType<T> type = type(typeId);
        if (level == null || type == null) return List.of();
        List<T> all = new ArrayList<>(level.getRecipeManager().getAllRecipesFor(type));
        all.sort(Comparator.comparingInt((T r) -> -r.getIngredients().size()));
        return all;
    }

    /**
     * One item for each of the ingredients from the slots [from, to), or null if some ingredient finds none.
     * The ingredients that fewest items fit choose first, so a broad one (a tag) doesn't take what a narrow one
     * needs; the items come back in the recipe's own order (Botania's recipes match an item to the first
     * ingredient that takes it, so in that order they always match).
     */
    @Nullable
    public static Pick pick(IItemHandler items, int from, int to, List<Ingredient> ingredients) {
        int[] left = new int[to - from];
        for (int i = 0; i < left.length; i++) left[i] = items.getStackInSlot(from + i).getCount();
        List<Integer> order = new ArrayList<>();
        for (int k = 0; k < ingredients.size(); k++) {
            if (!ingredients.get(k).isEmpty()) order.add(k);
        }
        if (order.isEmpty()) return null;
        order.sort(Comparator.comparingInt(k -> ingredients.get(k).getItems().length));
        int[] taken = new int[to - from];
        ItemStack[] chosen = new ItemStack[ingredients.size()];
        for (int k : order) {
            Ingredient ingredient = ingredients.get(k);
            for (int i = 0; i < left.length && chosen[k] == null; i++) {
                if (left[i] <= 0) continue;
                ItemStack stack = items.getStackInSlot(from + i);
                if (ingredient.test(stack)) {
                    left[i]--;
                    taken[i]++;
                    chosen[k] = stack.copyWithCount(1);
                }
            }
            if (chosen[k] == null) return null;
        }
        List<ItemStack> units = new ArrayList<>();
        for (ItemStack unit : chosen) {
            if (unit != null) units.add(unit);
        }
        return new Pick(taken, units);
    }

    /** For each set of recipes (the server's, the client's; a reload makes new ones): the items each key's recipes take. */
    private static final Map<RecipeManager, Map<String, Set<Item>>> KNOWN = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * Whether some recipe of the type takes the item, by what `ingredients` gives of each recipe (its inputs, its
     * reagent...). Worked out once for each set of recipes (they change on a reload) under `key`.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static <T extends Recipe<?>> boolean takes(@Nullable Level level, ResourceLocation typeId, String key, ItemStack stack,
                                                      Function<T, List<Ingredient>> ingredients) {
        if (level == null || stack.isEmpty()) return false;
        RecipeManager manager = level.getRecipeManager();
        Map<String, Set<Item>> byKey = KNOWN.computeIfAbsent(manager, m -> new ConcurrentHashMap<>());
        Set<Item> known = byKey.get(key);
        if (known == null) {
            Set<Item> items = new HashSet<>();
            RecipeType type = type(typeId);
            if (type != null) {
                for (Object recipe : manager.getAllRecipesFor(type)) {
                    for (Ingredient ingredient : ingredients.apply((T) recipe)) {
                        if (ingredient == null) continue;
                        for (ItemStack option : ingredient.getItems()) items.add(option.getItem());
                    }
                }
            }
            known = items;
            byKey.put(key, known);
        }
        return known.contains(stack.getItem());
    }

    /** How many of the stack's item the slots [from, to) still have room for. */
    public static int room(IItemHandler items, int from, int to, ItemStack stack) {
        int room = 0;
        for (int i = from; i < to; i++) {
            ItemStack s = items.getStackInSlot(i);
            int limit = Math.min(stack.getMaxStackSize(), items.getSlotLimit(i));
            if (s.isEmpty()) room += limit;
            else if (ItemStack.isSameItemSameTags(s, stack)) room += Math.max(0, limit - s.getCount());
        }
        return room;
    }

    /** Whether some slot in [from, to) holds an item the ingredient takes. */
    public static boolean any(IItemHandler items, int from, int to, Ingredient ingredient) {
        for (int i = from; i < to; i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!stack.isEmpty() && ingredient.test(stack)) return true;
        }
        return false;
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
        if (!simulate && items instanceof IItemHandlerModifiable modifiable) {
            for (int i = 0; i < slots.length; i++) modifiable.setStackInSlot(from + i, slots[i]);
        }
        return true;
    }

    private Units() {
    }
}
