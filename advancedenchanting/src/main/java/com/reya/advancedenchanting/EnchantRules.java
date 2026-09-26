package com.reya.advancedenchanting;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;

/** Shared by the screen (to show) and the menu (to check): what can go on an item and what it costs. */
public final class EnchantRules {
    /** Midnight is at 18000; curses work two real minutes (2400 ticks) either side of it. */
    private static final long MIDNIGHT = 18000L;
    private static final long WINDOW = 2400L;

    private EnchantRules() {
    }

    public static boolean isBook(ItemStack stack) {
        return stack.is(Items.BOOK) || stack.is(Items.ENCHANTED_BOOK);
    }

    public static boolean canUse(ItemStack stack) {
        if (stack.isEmpty()) return false;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null && Config.BLACKLISTED_ITEMS.get().contains(id.toString())) return false;
        if (isBook(stack)) return true;
        return stack.isEnchantable() || stack.isEnchanted();
    }

    public static boolean blacklisted(Enchantment e) {
        ResourceLocation id = ForgeRegistries.ENCHANTMENTS.getKey(e);
        return id != null && Config.BLACKLISTED_ENCHANTMENTS.get().contains(id.toString());
    }

    /** Current enchantments of the item plus every other one that fits it, in a stable order. */
    public static Map<Enchantment, Integer> options(ItemStack stack) {
        Map<Enchantment, Integer> current = EnchantmentHelper.getEnchantments(stack);
        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        if (!canUse(stack)) return out;
        List<Enchantment> all = new ArrayList<>();
        for (Enchantment e : ForgeRegistries.ENCHANTMENTS.getValues()) {
            if (current.containsKey(e)) continue;
            if (blacklisted(e)) continue;
            boolean fits = isBook(stack) ? e.isAllowedOnBooks() : e.canEnchant(stack);
            if (!fits || e.isTreasureOnly() && !e.isCurse()) continue;
            all.add(e);
        }
        all.sort(Comparator.comparing(e -> String.valueOf(ForgeRegistries.ENCHANTMENTS.getKey(e))));
        out.putAll(current);
        for (Enchantment e : all) out.put(e, 0);
        return out;
    }

    public static int maxLevel(Enchantment e, int current) {
        return Math.max(e.getMaxLevel(), current);
    }

    public static boolean cursesOpen(Level level) {
        if (Config.CURSES_ANYTIME.get()) return true;
        long time = level.getDayTime() % 24000L;
        return level.getMoonPhase() == 0 && Math.abs(time - MIDNIGHT) <= WINDOW;
    }

    /** Can this enchantment be set to anything but its current level, given the other targets? */
    public static boolean available(Enchantment e, int current, Map<Enchantment, Integer> targets, Level level) {
        if (e.isCurse() && !cursesOpen(level)) return false;
        if (e.isTreasureOnly() && !e.isCurse()) return false;
        for (Map.Entry<Enchantment, Integer> other : targets.entrySet()) {
            if (other.getKey() == e || other.getValue() <= 0) continue;
            if (!e.isCompatibleWith(other.getKey())) return false;
        }
        return true;
    }

    /** Experience points for raising one enchantment from one level to another (lowering is free). */
    public static long cost(Enchantment e, int from, int to) {
        long sum = 0L;
        int weight = switch (e.getRarity()) {
            case COMMON -> 1;
            case UNCOMMON -> 2;
            case RARE -> 4;
            case VERY_RARE -> 8;
        };
        for (int l = Math.max(1, from + 1); l <= to; l++) {
            sum += (long) Math.max(1, e.getMinCost(l)) * weight;
        }
        return Math.round(sum * Config.COST_MULTIPLIER.get());
    }

    public static long totalCost(Map<Enchantment, Integer> current, Map<Enchantment, Integer> targets) {
        long sum = 0L;
        for (Map.Entry<Enchantment, Integer> t : targets.entrySet()) {
            sum += cost(t.getKey(), current.getOrDefault(t.getKey(), 0), t.getValue());
        }
        return sum;
    }

    /** All the experience points a player holds (levels and the bar). */
    public static long points(Player player) {
        return pointsForLevel(player.experienceLevel) + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    private static long pointsForLevel(int l) {
        if (l <= 16) return (long) l * l + 6L * l;
        if (l <= 31) return Math.round(2.5D * l * l - 40.5D * l + 360.0D);
        return Math.round(4.5D * l * l - 162.5D * l + 2220.0D);
    }
}
