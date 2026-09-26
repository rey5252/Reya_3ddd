package com.reya.mobfarm.farm;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.reya.mobfarm.FarmTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.EnchantedBookItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.items.IItemHandler;

/**
 * What the enchanted books in the side slots do:
 * Efficiency = faster cycles, Looting = more loot, Fire Aspect = cooked drops,
 * Sweeping Edge = extra kills per cycle. Levels from several books add up.
 */
public record FarmUpgrades(int efficiency, int looting, boolean fire, int sweeping) {
    public static final FarmUpgrades NONE = new FarmUpgrades(0, 0, false, 0);
    private static final int MAX_LEVEL = 10;

    public static boolean isUpgrade(ItemStack stack) {
        return stack.is(Items.ENCHANTED_BOOK);
    }

    public static FarmUpgrades of(IItemHandler items) {
        int efficiency = 0;
        int looting = 0;
        boolean fire = false;
        int sweeping = 0;
        for (int i = MobFarmBlockEntity.UPGRADE_START; i < MobFarmBlockEntity.UPGRADE_START + MobFarmBlockEntity.UPGRADE_COUNT; i++) {
            ItemStack stack = items.getStackInSlot(i);
            if (!isUpgrade(stack)) continue;
            Map<Enchantment, Integer> enchants = EnchantmentHelper.deserializeEnchantments(EnchantedBookItem.getEnchantments(stack));
            efficiency += enchants.getOrDefault(Enchantments.BLOCK_EFFICIENCY, 0);
            looting += enchants.getOrDefault(Enchantments.MOB_LOOTING, 0);
            fire |= enchants.getOrDefault(Enchantments.FIRE_ASPECT, 0) > 0;
            sweeping += enchants.getOrDefault(Enchantments.SWEEPING_EDGE, 0);
        }
        return new FarmUpgrades(Math.min(MAX_LEVEL, efficiency), Math.min(MAX_LEVEL, looting), fire,
                Math.min(MAX_LEVEL, sweeping));
    }

    /** Cycle length: each Efficiency level cuts it by a third of the base time, never below half a second. */
    public int ticks(FarmTier tier) {
        return Math.max(10, Math.round(tier.ticks / (1.0F + 0.5F * efficiency)));
    }

    public int kills() {
        return 1 + sweeping;
    }

    public boolean isEmpty() {
        return efficiency == 0 && looting == 0 && !fire && sweeping == 0;
    }

    /** Lines for the GUI tooltip. */
    public List<Component> describe(FarmTier tier) {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("gui.mobfarm.upgrades").withStyle(ChatFormatting.GOLD));
        if (isEmpty()) {
            lines.add(Component.translatable("gui.mobfarm.upgrades.none").withStyle(ChatFormatting.GRAY));
            lines.add(Component.translatable("gui.mobfarm.upgrades.hint.efficiency").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("gui.mobfarm.upgrades.hint.looting").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("gui.mobfarm.upgrades.hint.fire").withStyle(ChatFormatting.DARK_GRAY));
            lines.add(Component.translatable("gui.mobfarm.upgrades.hint.sweeping").withStyle(ChatFormatting.DARK_GRAY));
            return lines;
        }
        if (efficiency > 0) {
            lines.add(Component.translatable("gui.mobfarm.upgrades.efficiency",
                    Math.round(100.0F * tier.ticks / ticks(tier)) - 100, String.format("%.1f", ticks(tier) / 20.0F))
                    .withStyle(ChatFormatting.AQUA));
        }
        if (looting > 0) {
            lines.add(Component.translatable("gui.mobfarm.upgrades.looting", looting).withStyle(ChatFormatting.GREEN));
        }
        if (fire) {
            lines.add(Component.translatable("gui.mobfarm.upgrades.fire").withStyle(ChatFormatting.RED));
        }
        if (sweeping > 0) {
            lines.add(Component.translatable("gui.mobfarm.upgrades.sweeping", kills()).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        return lines;
    }
}
