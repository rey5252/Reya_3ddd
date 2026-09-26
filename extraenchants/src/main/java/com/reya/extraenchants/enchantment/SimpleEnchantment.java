package com.reya.extraenchants.enchantment;

import java.util.function.Predicate;

import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.AxeItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentCategory;

/** Data-only enchantment; all behaviour lives in event handlers and loot modifiers. */
public class SimpleEnchantment extends Enchantment {
    private final int maxLevel;
    private final int minCost;
    private final int costPerLevel;
    private final boolean alsoAxes;
    private final Predicate<Enchantment> compatible;

    public SimpleEnchantment(Rarity rarity, EnchantmentCategory category, int maxLevel, int minCost, int costPerLevel,
                             boolean alsoAxes, Predicate<Enchantment> compatible) {
        super(rarity, category, new EquipmentSlot[]{EquipmentSlot.MAINHAND});
        this.maxLevel = maxLevel;
        this.minCost = minCost;
        this.costPerLevel = costPerLevel;
        this.alsoAxes = alsoAxes;
        this.compatible = compatible;
    }

    @Override
    public int getMaxLevel() {
        return maxLevel;
    }

    @Override
    public int getMinCost(int level) {
        return minCost + (level - 1) * costPerLevel;
    }

    @Override
    public int getMaxCost(int level) {
        return getMinCost(level) + 50;
    }

    @Override
    public boolean canEnchant(ItemStack stack) {
        return super.canEnchant(stack) || (alsoAxes && stack.getItem() instanceof AxeItem);
    }

    @Override
    protected boolean checkCompatibility(Enchantment other) {
        return super.checkCompatibility(other) && compatible.test(other);
    }
}
