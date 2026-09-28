package com.reya.goldenquarry;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/**
 * Goes into the quarry's upgrade slots; each item in the stacks counts, up to the kind's maximum.
 * Fortune comes in levels (2, 5, 10): the highest one in the slots or in a fortune block beside
 * the quarry is used.
 */
public class QuarryUpgradeItem extends Item {
    public enum Kind {
        SPEED(8), RANGE(8), FORTUNE(1), SILK_TOUCH(1), SMELTING(1);

        public final int max;

        Kind(int max) {
            this.max = max;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public final Kind kind;
    /** Fortune level for fortune upgrades, 0 for the others. */
    public final int level;

    public QuarryUpgradeItem(Kind kind, Properties properties) {
        this(kind, 0, properties);
    }

    public QuarryUpgradeItem(Kind kind, int level, Properties properties) {
        super(properties);
        this.kind = kind;
        this.level = level;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (kind == Kind.FORTUNE) {
            tooltip.add(Component.translatable("item.goldenquarry.fortune_upgrade.tip", level).withStyle(ChatFormatting.GRAY));
            return;
        }
        tooltip.add(Component.translatable("item.goldenquarry." + kind.id() + "_upgrade.tip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.goldenquarry.upgrade.max", kind.max).withStyle(ChatFormatting.DARK_GRAY));
    }
}
