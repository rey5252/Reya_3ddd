package com.reya.starfall;

import com.reya.starfall.client.ClientConfig;
import com.reya.starfall.network.Net;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * The Stellar Remote: a hand-held trigger wired to weapons hanging far out in the dark.
 * This is an original implementation of the "galaxy weapon" idea — no third-party code or assets.
 */
@Mod(Starfall.MODID)
public class Starfall {
    public static final String MODID = "starfall";

    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** The one-button remote. */
    public static final RegistryObject<Item> STELLAR_REMOTE = ITEMS.register("stellar_remote",
            () -> new StellarRemoteItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    /** The kilometre-long needle SS-03 leaves standing: nothing breaks it. */
    public static final RegistryObject<Block> STAR_NEEDLE = BLOCKS.register("star_needle",
            () -> new Block(BlockBehaviour.Properties.copy(Blocks.OBSIDIAN)
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(-1.0F, 3_600_000.0F)
                    .sound(SoundType.NETHERITE_BLOCK)
                    .lightLevel(s -> 3)));

    /** The glowing star-core crystal SS-04 leaves in each crater. */
    public static final RegistryObject<Block> STAR_CORE = BLOCKS.register("star_core",
            () -> new Block(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.DIAMOND)
                    .strength(5.0F, 1200.0F)
                    .sound(SoundType.AMETHYST)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(s -> 15)));

    public static final RegistryObject<Item> STAR_NEEDLE_ITEM = ITEMS.register("star_needle",
            () -> new BlockItem(STAR_NEEDLE.get(), new Item.Properties()));
    public static final RegistryObject<Item> STAR_CORE_ITEM = ITEMS.register("star_core",
            () -> new BlockItem(STAR_CORE.get(), new Item.Properties().rarity(Rarity.RARE)));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("starfall", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.starfall"))
            .icon(() -> new ItemStack(STELLAR_REMOTE.get()))
            .displayItems((params, output) -> {
                output.accept(STELLAR_REMOTE.get());
                output.accept(STAR_CORE_ITEM.get());
                output.accept(STAR_NEEDLE_ITEM.get());
            })
            .build());

    /** The remote also sits in the vanilla Combat tab. */
    private void addToTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.COMBAT) event.accept(STELLAR_REMOTE);
    }

    public Starfall() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ITEMS.register(bus);
        BLOCKS.register(bus);
        TABS.register(bus);
        bus.addListener(this::addToTabs);
        Net.register();
        MinecraftForge.EVENT_BUS.register(new StarfallManager());
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
        ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
    }
}
