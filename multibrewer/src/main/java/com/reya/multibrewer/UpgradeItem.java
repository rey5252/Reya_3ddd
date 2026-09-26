package com.reya.multibrewer;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Goes into the brewer's upgrade slots; up to four of each kind count. */
public class UpgradeItem extends Item {
    public enum Kind { SPEED, EFFICIENCY, POTENCY }

    public final Kind kind;

    public UpgradeItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.multibrewer." + kind.name().toLowerCase(java.util.Locale.ROOT) + "_upgrade.tip")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.multibrewer.upgrade.stack").withStyle(ChatFormatting.DARK_GRAY));
    }
}
