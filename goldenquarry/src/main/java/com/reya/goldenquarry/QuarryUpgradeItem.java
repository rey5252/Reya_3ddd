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
 * Goes into the converter's upgrade slots, one of each kind: autosmelt, stack and the infinite engine
 * on the right; the fortune module (levels 2, 5, 10) in its own slot left of the bars.
 */
public class QuarryUpgradeItem extends Item {
    public enum Kind {
        FORTUNE(1), SMELTING(1), STACK(1), INFINITE(1);

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
    }
}
