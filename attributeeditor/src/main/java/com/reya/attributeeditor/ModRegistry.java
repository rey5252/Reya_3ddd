package com.reya.attributeeditor;

import com.reya.attributeeditor.table.AttributeTableBlock;
import com.reya.attributeeditor.table.AttributeTableMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, AttributeEditor.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AttributeEditor.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, AttributeEditor.MOD_ID);

    public static final RegistryObject<Block> ATTRIBUTE_TABLE = BLOCKS.register("attribute_table",
            () -> new AttributeTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .requiresCorrectToolForDrops()
                    .strength(5.0F, 1200.0F)
                    .sound(SoundType.STONE)
                    .lightLevel(state -> 7)));

    public static final RegistryObject<Item> ATTRIBUTE_TABLE_ITEM = ITEMS.register("attribute_table",
            () -> new BlockItem(ATTRIBUTE_TABLE.get(), new Item.Properties()));

    public static final RegistryObject<MenuType<AttributeTableMenu>> ATTRIBUTE_TABLE_MENU = MENUS.register("attribute_table",
            () -> IForgeMenuType.create((id, inventory, data) -> new AttributeTableMenu(id, inventory)));

    private ModRegistry() {
    }
}
