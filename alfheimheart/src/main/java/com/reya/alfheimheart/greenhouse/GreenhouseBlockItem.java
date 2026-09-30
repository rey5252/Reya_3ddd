package com.reya.alfheimheart.greenhouse;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** The greenhouse keeps its mana when broken; the item says how much it carries. */
public class GreenhouseBlockItem extends BlockItem {
    public GreenhouseBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static int storedMana(ItemStack stack) {
        CompoundTag tag = BlockItem.getBlockEntityData(stack);
        return tag == null ? 0 : tag.getInt(GreenhouseBlockEntity.TAG_MANA);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.alfheimheart.mana_greenhouse.tip").withStyle(ChatFormatting.GRAY));
        int mana = storedMana(stack);
        if (mana > 0) {
            tooltip.add(Component.translatable("block.alfheimheart.mana_greenhouse.stored", Format.mana(mana)).withStyle(ChatFormatting.AQUA));
        }
    }
}
