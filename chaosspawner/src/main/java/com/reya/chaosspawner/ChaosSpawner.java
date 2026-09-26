package com.reya.chaosspawner;

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

@Mod(ChaosSpawner.MODID)
public class ChaosSpawner {
    public static final String MODID = "chaosspawner";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Block> SPAWNER = BLOCKS.register("chaos_spawner",
            () -> new ChaosSpawnerBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_CYAN)
                    .strength(5.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .noOcclusion()
                    .lightLevel(state -> 9)));
    public static final RegistryObject<Item> SPAWNER_ITEM = ITEMS.register("chaos_spawner",
            () -> new BlockItem(SPAWNER.get(), new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SOUL_CRYSTAL = ITEMS.register("soul_crystal",
            () -> new SoulCrystalItem(new Item.Properties()));
    public static final RegistryObject<Item> SPEED_UPGRADE = ITEMS.register("speed_upgrade",
            () -> new SpawnerUpgradeItem(SpawnerUpgradeItem.Kind.SPEED, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> LOOTING_UPGRADE = ITEMS.register("looting_upgrade",
            () -> new SpawnerUpgradeItem(SpawnerUpgradeItem.Kind.LOOTING, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> QUANTITY_UPGRADE = ITEMS.register("quantity_upgrade",
            () -> new SpawnerUpgradeItem(SpawnerUpgradeItem.Kind.QUANTITY, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<Item> EXPERIENCE_UPGRADE = ITEMS.register("experience_upgrade",
            () -> new SpawnerUpgradeItem(SpawnerUpgradeItem.Kind.EXPERIENCE, new Item.Properties().stacksTo(16)));
    public static final RegistryObject<BlockEntityType<ChaosSpawnerBlockEntity>> SPAWNER_BE = BLOCK_ENTITIES.register("chaos_spawner",
            () -> BlockEntityType.Builder.of(ChaosSpawnerBlockEntity::new, SPAWNER.get()).build(null));
    public static final RegistryObject<MenuType<ChaosSpawnerMenu>> SPAWNER_MENU = MENUS.register("chaos_spawner",
            () -> IForgeMenuType.create(ChaosSpawnerMenu::new));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("chaosspawner", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.chaosspawner"))
            .icon(() -> new ItemStack(SPAWNER_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(SPAWNER_ITEM.get());
                output.accept(SOUL_CRYSTAL.get());
                output.accept(SPEED_UPGRADE.get());
                output.accept(LOOTING_UPGRADE.get());
                output.accept(QUANTITY_UPGRADE.get());
                output.accept(EXPERIENCE_UPGRADE.get());
            })
            .build());

    public ChaosSpawner() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
