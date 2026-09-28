package com.reya.goldenquarry;

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
import net.minecraft.world.level.material.PushReaction;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(GoldenQuarry.MODID)
public class GoldenQuarry {
    public static final String MODID = "goldenquarry";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Block> QUARRY = BLOCKS.register("golden_quarry",
            () -> new QuarryBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.GOLD)
                    .strength(4.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .pushReaction(PushReaction.BLOCK)
                    .lightLevel(state -> QuarryBlock.isLower(state) ? 0 : 10)));
    public static final RegistryObject<Item> QUARRY_ITEM = ITEMS.register("golden_quarry",
            () -> new BlockItem(QUARRY.get(), new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<Item> SPEED_UPGRADE = upgrade("speed_upgrade", QuarryUpgradeItem.Kind.SPEED);
    public static final RegistryObject<Item> RANGE_UPGRADE = upgrade("range_upgrade", QuarryUpgradeItem.Kind.RANGE);
    public static final RegistryObject<Item> FORTUNE_UPGRADE_2 = fortune(2);
    public static final RegistryObject<Item> FORTUNE_UPGRADE_5 = fortune(5);
    public static final RegistryObject<Item> FORTUNE_UPGRADE_10 = fortune(10);
    public static final RegistryObject<Block> FORTUNE_BLOCK_2 = fortuneBlock(2);
    public static final RegistryObject<Block> FORTUNE_BLOCK_5 = fortuneBlock(5);
    public static final RegistryObject<Block> FORTUNE_BLOCK_10 = fortuneBlock(10);
    public static final RegistryObject<Item> FORTUNE_BLOCK_2_ITEM = blockItem(FORTUNE_BLOCK_2, Rarity.UNCOMMON);
    public static final RegistryObject<Item> FORTUNE_BLOCK_5_ITEM = blockItem(FORTUNE_BLOCK_5, Rarity.RARE);
    public static final RegistryObject<Item> FORTUNE_BLOCK_10_ITEM = blockItem(FORTUNE_BLOCK_10, Rarity.EPIC);
    public static final RegistryObject<Item> SILK_TOUCH_UPGRADE = upgrade("silk_touch_upgrade", QuarryUpgradeItem.Kind.SILK_TOUCH);
    public static final RegistryObject<Item> SMELTING_UPGRADE = upgrade("smelting_upgrade", QuarryUpgradeItem.Kind.SMELTING);
    public static final RegistryObject<BlockEntityType<QuarryBlockEntity>> QUARRY_BE = BLOCK_ENTITIES.register("golden_quarry",
            () -> BlockEntityType.Builder.of(QuarryBlockEntity::new, QUARRY.get()).build(null));
    public static final RegistryObject<MenuType<QuarryMenu>> QUARRY_MENU = MENUS.register("golden_quarry",
            () -> IForgeMenuType.create(QuarryMenu::new));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("goldenquarry", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.goldenquarry"))
            .icon(() -> new ItemStack(QUARRY_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(QUARRY_ITEM.get());
                output.accept(SPEED_UPGRADE.get());
                output.accept(RANGE_UPGRADE.get());
                output.accept(FORTUNE_UPGRADE_2.get());
                output.accept(FORTUNE_UPGRADE_5.get());
                output.accept(FORTUNE_UPGRADE_10.get());
                output.accept(FORTUNE_BLOCK_2_ITEM.get());
                output.accept(FORTUNE_BLOCK_5_ITEM.get());
                output.accept(FORTUNE_BLOCK_10_ITEM.get());
                output.accept(SILK_TOUCH_UPGRADE.get());
                output.accept(SMELTING_UPGRADE.get());
            })
            .build());

    private static RegistryObject<Item> fortune(int level) {
        Rarity rarity = level >= 10 ? Rarity.EPIC : level >= 5 ? Rarity.RARE : Rarity.UNCOMMON;
        return ITEMS.register("fortune_upgrade_" + level,
                () -> new QuarryUpgradeItem(QuarryUpgradeItem.Kind.FORTUNE, level, new Item.Properties().stacksTo(1).rarity(rarity)));
    }

    private static RegistryObject<Block> fortuneBlock(int level) {
        return BLOCKS.register("fortune_block_" + level, () -> new FortuneBoosterBlock(level, BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(4.0F, 1200.0F)
                .sound(SoundType.METAL)
                .requiresCorrectToolForDrops()
                .noOcclusion()
                .lightLevel(state -> 7)));
    }

    private static RegistryObject<Item> blockItem(RegistryObject<Block> block, Rarity rarity) {
        return ITEMS.register(block.getId().getPath(), () -> new BlockItem(block.get(), new Item.Properties().rarity(rarity)));
    }

    private static RegistryObject<Item> upgrade(String name, QuarryUpgradeItem.Kind kind) {
        return ITEMS.register(name, () -> new QuarryUpgradeItem(kind, new Item.Properties().stacksTo(kind.max)));
    }

    public GoldenQuarry() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
