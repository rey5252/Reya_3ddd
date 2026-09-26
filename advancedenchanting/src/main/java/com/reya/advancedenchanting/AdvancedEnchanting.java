package com.reya.advancedenchanting;

import com.reya.advancedenchanting.network.Net;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(AdvancedEnchanting.MODID)
public class AdvancedEnchanting {
    public static final String MODID = "advancedenchanting";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Block> TABLE = BLOCKS.register("advanced_enchanting_table",
            () -> new AdvancedTableBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_RED)
                    .strength(5.0F, 1200.0F)
                    .sound(SoundType.STONE)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(state -> 7)));
    public static final RegistryObject<Item> TABLE_ITEM = ITEMS.register("advanced_enchanting_table",
            () -> new BlockItem(TABLE.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> TABLE_UPGRADE = ITEMS.register("table_upgrade",
            () -> new TableUpgradeItem(new Item.Properties().stacksTo(16).rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<BlockEntityType<AdvancedTableBlockEntity>> TABLE_BE = BLOCK_ENTITIES.register("advanced_enchanting_table",
            () -> BlockEntityType.Builder.of(AdvancedTableBlockEntity::new, TABLE.get()).build(null));
    public static final RegistryObject<MenuType<AdvancedTableMenu>> TABLE_MENU = MENUS.register("advanced_enchanting_table",
            () -> IForgeMenuType.create((windowId, inv, data) -> new AdvancedTableMenu(windowId, inv)));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("advancedenchanting", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.advancedenchanting"))
            .icon(() -> new ItemStack(TABLE_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(TABLE_ITEM.get());
                output.accept(TABLE_UPGRADE.get());
            })
            .build());

    public AdvancedEnchanting() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        Net.register();
    }
}
