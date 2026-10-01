package com.reya.boundlessrouters.router;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** The router's item: it keeps the modules and upgrades of a router that was broken, and says so. */
public class RouterBlockItem extends BlockItem {
    public RouterBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.boundlessrouters.router").withStyle(ChatFormatting.GRAY));
        CompoundTag data = BlockItem.getBlockEntityData(stack);
        if (data == null) return;
        int modules = data.getCompound("Modules").getList("Items", Tag.TAG_COMPOUND).size();
        int upgrades = 0;
        ListTag list = data.getCompound("Upgrades").getList("Items", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) upgrades += list.getCompound(i).getByte("Count");
        if (modules + upgrades > 0) {
            tooltip.add(Component.translatable("tooltip.boundlessrouters.router.holds", modules, upgrades).withStyle(ChatFormatting.AQUA));
        }
    }
}
