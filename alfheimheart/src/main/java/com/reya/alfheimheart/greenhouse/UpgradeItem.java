package com.reya.alfheimheart.greenhouse;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** Goes into the greenhouse's upgrade slots; every item in the stacks counts, up to the config's limit per kind. */
public class UpgradeItem extends Item {
    public final UpgradeKind kind;

    public UpgradeItem(UpgradeKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.alfheimheart.upgrade.tip." + kind.id(), effect()).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.alfheimheart.upgrade.max", Config.MAX_UPGRADES.get()).withStyle(ChatFormatting.DARK_GRAY));
    }

    /** What one upgrade does, as the number its tooltip shows. */
    private String effect() {
        return switch (kind) {
            case SPEED -> percent(Config.SPEED_PER_UPGRADE.get());
            case CAPACITY -> Format.mana(Config.CAPACITY_PER_UPGRADE.get());
            case LUCK -> percent(Config.LUCK_PER_UPGRADE.get());
            case YIELD -> percent(Config.YIELD_PER_UPGRADE.get());
        };
    }

    private static String percent(double v) {
        return Math.round(v * 100.0D) + "%";
    }
}
