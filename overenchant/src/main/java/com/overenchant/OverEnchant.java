package com.overenchant;

import com.overenchant.network.Net;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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

@Mod(OverEnchant.MODID)
public class OverEnchant {
    public static final String MODID = "overenchant";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Block> ENCHANTMENT_UPGRADER = BLOCKS.register("enchantment_upgrader",
            () -> new EnchantmentUpgraderBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE)
                    .strength(5.0F, 1200.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .lightLevel(state -> 7)));
    public static final RegistryObject<Item> ENCHANTMENT_UPGRADER_ITEM = ITEMS.register("enchantment_upgrader",
            () -> new BlockItem(ENCHANTMENT_UPGRADER.get(), new Item.Properties()));
    public static final RegistryObject<BlockEntityType<UpgraderBlockEntity>> UPGRADER_BE = BLOCK_ENTITIES.register("enchantment_upgrader",
            () -> BlockEntityType.Builder.of(UpgraderBlockEntity::new, ENCHANTMENT_UPGRADER.get()).build(null));
    public static final RegistryObject<MenuType<UpgraderMenu>> UPGRADER_MENU = MENUS.register("enchantment_upgrader",
            () -> IForgeMenuType.create((windowId, inv, data) -> new UpgraderMenu(windowId, inv)));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("overenchant", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.overenchant"))
            .icon(() -> new ItemStack(ENCHANTMENT_UPGRADER_ITEM.get()))
            .displayItems((params, output) -> output.accept(ENCHANTMENT_UPGRADER_ITEM.get()))
            .build());

    public OverEnchant() {
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
