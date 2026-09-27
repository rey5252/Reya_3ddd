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

/** Goes into the quarry's upgrade slots; each item in the stacks counts, up to the kind's maximum. */
public class QuarryUpgradeItem extends Item {
    public enum Kind {
        SPEED(8), RANGE(8), FORTUNE(3), SILK_TOUCH(1), SMELTING(1);

        public final int max;

        Kind(int max) {
            this.max = max;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public final Kind kind;

    public QuarryUpgradeItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.goldenquarry." + kind.id() + "_upgrade.tip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.goldenquarry.upgrade.max", kind.max).withStyle(ChatFormatting.DARK_GRAY));
    }
}
