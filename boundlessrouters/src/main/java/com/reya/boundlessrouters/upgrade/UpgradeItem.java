package com.reya.boundlessrouters.upgrade;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** An upgrade, to put in a router's upgrade slots. */
public class UpgradeItem extends Item {
    private final UpgradeKind kind;

    public UpgradeItem(UpgradeKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public UpgradeKind kind() {
        return kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        if (kind.isRange() && kind.reach() > 0) {
            tooltip.add(Component.translatable("tooltip.boundlessrouters.reach", kind.reach()).withStyle(ChatFormatting.AQUA));
        }
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
    }

    /** The infinite range upgrade shimmers. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return kind == UpgradeKind.INFINITE_RANGE || super.isFoil(stack);
    }
}
