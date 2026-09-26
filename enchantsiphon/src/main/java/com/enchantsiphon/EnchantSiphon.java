package com.enchantsiphon;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
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

@Mod(EnchantSiphon.MODID)
public class EnchantSiphon {
    public static final String MODID = "enchantsiphon";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Block> SIPHON = BLOCKS.register("enchant_siphon",
            () -> new SiphonBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(5.0F, 1200.0F)
                    .sound(SoundType.STONE)
                    .noOcclusion()
                    .lightLevel(state -> 7)));
    public static final RegistryObject<Item> SIPHON_ITEM = ITEMS.register("enchant_siphon",
            () -> new BlockItem(SIPHON.get(), new Item.Properties()));
    public static final RegistryObject<MenuType<SiphonMenu>> SIPHON_MENU = MENUS.register("enchant_siphon",
            () -> IForgeMenuType.create((windowId, inv, data) -> new SiphonMenu(windowId, inv)));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("enchantsiphon", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.enchantsiphon"))
            .icon(() -> new ItemStack(SIPHON_ITEM.get()))
            .displayItems((params, output) -> output.accept(SIPHON_ITEM.get()))
            .build());

    public EnchantSiphon() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
