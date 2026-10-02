package com.reya.singularityfusion.item;

import java.util.List;

import javax.annotation.Nullable;

import com.reya.singularityfusion.SingularityFusion;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

/** An item with a few lines about it in its tooltip (tooltip.singularityfusion.NAME.1, .2, ...), shimmering if it is one of the great ones. */
public class DescribedItem extends Item {
    private final boolean foil;

    public DescribedItem(Properties properties, boolean foil) {
        super(properties);
        this.foil = foil;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return foil || super.isFoil(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        lines(BuiltInRegistries.ITEM.getKey(this).getPath(), tooltip);
    }

    /** The tooltip lines there are for a name. */
    public static void lines(String name, List<Component> tooltip) {
        for (int i = 1; i <= 6; i++) {
            String key = "tooltip." + SingularityFusion.MODID + "." + name + "." + i;
            if (!Language.getInstance().has(key)) break;
            tooltip.add(Component.translatable(key).withStyle(ChatFormatting.GRAY));
        }
    }

    /** A block's item, with its lines. */
    public static class Placed extends BlockItem {
        public Placed(Block block, Properties properties) {
            super(block, properties);
        }

        @Override
        public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
            lines(BuiltInRegistries.ITEM.getKey(this).getPath(), tooltip);
        }
    }
}
