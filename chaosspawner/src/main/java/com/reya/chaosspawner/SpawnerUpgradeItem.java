package com.reya.chaosspawner;

import java.util.List;
import java.util.Locale;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Goes into the spawner's upgrade slots; every item in the stacks counts. */
public class SpawnerUpgradeItem extends Item {
    public enum Kind { SPEED, LOOTING, QUANTITY, EXPERIENCE }

    public final Kind kind;

    public SpawnerUpgradeItem(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.chaosspawner." + kind.name().toLowerCase(Locale.ROOT) + "_upgrade.tip")
                .withStyle(ChatFormatting.GRAY));
    }
}
