package com.reya.managarden;

import com.mojang.logging.LogUtils;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
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
import org.slf4j.Logger;

/**
 * Mana Garden, a Botania addon. The Mana Greenhouse holds up to eight generating flowers that make
 * mana on their own, with no fuel and no care; it keeps the mana and hands it on to Botania's pools,
 * spreaders and mana items. Its upgrades speed the flowers up, widen the store, bring luck (now and
 * then a cycle gives more than usual) and raise the yield.
 */
@Mod(ManaGarden.MODID)
public class ManaGarden {
    public static final String MODID = "managarden";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Block> GREENHOUSE = BLOCKS.register("mana_greenhouse",
            () -> new GreenhouseBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_LIGHT_GREEN)
                    .strength(2.5F, 1200.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .lightLevel(state -> state.getValue(GreenhouseBlock.ACTIVE) ? 12 : 6)));
    public static final RegistryObject<Item> GREENHOUSE_ITEM = ITEMS.register("mana_greenhouse",
            () -> new GreenhouseBlockItem(GREENHOUSE.get(), new Item.Properties().rarity(Rarity.EPIC)));

    public static final RegistryObject<Item> GREENHOUSE_HEART = ITEMS.register("greenhouse_heart",
            () -> new HintItem(new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<Item> UPGRADE_BASE = ITEMS.register("upgrade_base",
            () -> new HintItem(new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<Item> SPEED_UPGRADE = ITEMS.register("speed_upgrade",
            () -> new UpgradeItem(UpgradeKind.SPEED, new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> CAPACITY_UPGRADE = ITEMS.register("capacity_upgrade",
            () -> new UpgradeItem(UpgradeKind.CAPACITY, new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> LUCK_UPGRADE = ITEMS.register("luck_upgrade",
            () -> new UpgradeItem(UpgradeKind.LUCK, new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));
    public static final RegistryObject<Item> YIELD_UPGRADE = ITEMS.register("yield_upgrade",
            () -> new UpgradeItem(UpgradeKind.YIELD, new Item.Properties().stacksTo(16).rarity(Rarity.RARE)));

    public static final RegistryObject<BlockEntityType<GreenhouseBlockEntity>> GREENHOUSE_BE = BLOCK_ENTITIES.register("mana_greenhouse",
            () -> BlockEntityType.Builder.of(GreenhouseBlockEntity::new, GREENHOUSE.get()).build(null));
    public static final RegistryObject<MenuType<GreenhouseMenu>> GREENHOUSE_MENU = MENUS.register("mana_greenhouse",
            () -> IForgeMenuType.create(GreenhouseMenu::new));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("managarden", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.managarden"))
            .icon(() -> new ItemStack(GREENHOUSE_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(GREENHOUSE_ITEM.get());
                output.accept(GREENHOUSE_HEART.get());
                output.accept(UPGRADE_BASE.get());
                output.accept(SPEED_UPGRADE.get());
                output.accept(CAPACITY_UPGRADE.get());
                output.accept(LUCK_UPGRADE.get());
                output.accept(YIELD_UPGRADE.get());
            })
            .build());

    public ManaGarden() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
