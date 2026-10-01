package com.reya.alfheimheart.machine;

import java.util.List;

import javax.annotation.Nullable;

import com.reya.alfheimheart.greenhouse.Format;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** A machine keeps its mana when broken; its item says what the machine does and how much mana it carries. */
public class MachineBlockItem extends BlockItem {
    public MachineBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    public static int storedMana(ItemStack stack) {
        CompoundTag tag = BlockItem.getBlockEntityData(stack);
        return tag == null ? 0 : tag.getInt(MachineBlockEntity.TAG_MANA);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".tip").withStyle(ChatFormatting.GRAY));
        int mana = storedMana(stack);
        if (mana > 0) {
            tooltip.add(Component.translatable("block.alfheimheart.machine.stored", Format.mana(mana)).withStyle(ChatFormatting.AQUA));
        }
    }
}
