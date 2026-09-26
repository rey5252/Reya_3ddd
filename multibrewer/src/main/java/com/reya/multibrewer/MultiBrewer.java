package com.reya.multibrewer;

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

@Mod(MultiBrewer.MODID)
public class MultiBrewer {
    public static final String MODID = "multibrewer";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Block> BREWER = BLOCKS.register("multi_brewer",
            () -> new MultiBrewerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.5F, 6.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(state -> state.getValue(MultiBrewerBlock.BREWING) ? 10 : 4)));
    public static final RegistryObject<Item> BREWER_ITEM = ITEMS.register("multi_brewer",
            () -> new BlockItem(BREWER.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> SPEED_UPGRADE = ITEMS.register("speed_upgrade",
            () -> new UpgradeItem(UpgradeItem.Kind.SPEED, new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> EFFICIENCY_UPGRADE = ITEMS.register("efficiency_upgrade",
            () -> new UpgradeItem(UpgradeItem.Kind.EFFICIENCY, new Item.Properties().stacksTo(4)));
    public static final RegistryObject<Item> POTENCY_UPGRADE = ITEMS.register("potency_upgrade",
            () -> new UpgradeItem(UpgradeItem.Kind.POTENCY, new Item.Properties().stacksTo(4)));
    public static final RegistryObject<BlockEntityType<MultiBrewerBlockEntity>> BREWER_BE = BLOCK_ENTITIES.register("multi_brewer",
            () -> BlockEntityType.Builder.of(MultiBrewerBlockEntity::new, BREWER.get()).build(null));
    public static final RegistryObject<MenuType<MultiBrewerMenu>> BREWER_MENU = MENUS.register("multi_brewer",
            () -> IForgeMenuType.create(MultiBrewerMenu::new));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("multibrewer", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.multibrewer"))
            .icon(() -> new ItemStack(BREWER_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(BREWER_ITEM.get());
                output.accept(SPEED_UPGRADE.get());
                output.accept(EFFICIENCY_UPGRADE.get());
                output.accept(POTENCY_UPGRADE.get());
            })
            .build());

    public MultiBrewer() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
