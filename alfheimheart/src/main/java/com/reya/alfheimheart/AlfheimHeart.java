package com.reya.alfheimheart;

import com.mojang.logging.LogUtils;
import com.reya.alfheimheart.greenhouse.GreenhouseBlock;
import com.reya.alfheimheart.greenhouse.GreenhouseBlockEntity;
import com.reya.alfheimheart.greenhouse.GreenhouseBlockItem;
import com.reya.alfheimheart.greenhouse.GreenhouseMenu;
import com.reya.alfheimheart.greenhouse.HintItem;
import com.reya.alfheimheart.greenhouse.UpgradeItem;
import com.reya.alfheimheart.greenhouse.UpgradeKind;
import com.reya.alfheimheart.machine.MachineBlock;
import com.reya.alfheimheart.machine.MachineBlockItem;
import com.reya.alfheimheart.machine.MachineConfig;
import com.reya.alfheimheart.machine.altar.RuneAltarBlock;
import com.reya.alfheimheart.machine.altar.RuneAltarBlockEntity;
import com.reya.alfheimheart.machine.altar.RuneAltarMenu;
import com.reya.alfheimheart.machine.infuser.ManaInfuserBlock;
import com.reya.alfheimheart.machine.infuser.ManaInfuserBlockEntity;
import com.reya.alfheimheart.machine.infuser.ManaInfuserMenu;
import com.reya.alfheimheart.machine.plate.TerraPlateBlock;
import com.reya.alfheimheart.machine.plate.TerraPlateBlockEntity;
import com.reya.alfheimheart.machine.plate.TerraPlateMenu;
import com.reya.alfheimheart.portal.PortalBlock;
import com.reya.alfheimheart.portal.PortalBlockEntity;
import com.reya.alfheimheart.portal.PortalBlockItem;
import com.reya.alfheimheart.portal.PortalMenu;
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
 * Heart of Alfheim, a Botania addon: Botania's work folded into single blocks, each with its own GUI and
 * animations. The Mana Greenhouse grows generating flowers that make mana on their own; the Elven Portal
 * is a whole Alfheim gateway in one block that trades by every elven trade recipe and refines ores; the
 * machines (the Runic Altar, the Terrestrial Plate, the Mana Infuser) craft by Botania's recipes from their
 * slots, with mana from pools and spreaders.
 */
@Mod(AlfheimHeart.MODID)
public class AlfheimHeart {
    public static final String MODID = "alfheimheart";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // ------------------------------------------------------------------ the Mana Greenhouse

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

    // ------------------------------------------------------------------ the Elven Portal

    public static final RegistryObject<Block> PORTAL = BLOCKS.register("elven_portal",
            () -> new PortalBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.QUARTZ)
                    .strength(2.5F, 1200.0F)
                    .sound(SoundType.WOOD)
                    .noOcclusion()
                    .isViewBlocking((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .lightLevel(state -> state.getValue(PortalBlock.OPEN) ? 13 : 4)));
    public static final RegistryObject<Item> PORTAL_ITEM = ITEMS.register("elven_portal",
            () -> new PortalBlockItem(PORTAL.get(), new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<BlockEntityType<PortalBlockEntity>> PORTAL_BE = BLOCK_ENTITIES.register("elven_portal",
            () -> BlockEntityType.Builder.of(PortalBlockEntity::new, PORTAL.get()).build(null));
    public static final RegistryObject<MenuType<PortalMenu>> PORTAL_MENU = MENUS.register("elven_portal",
            () -> IForgeMenuType.create(PortalMenu::new));

    // ------------------------------------------------------------------ the machines

    public static final RegistryObject<Block> RUNE_ALTAR = BLOCKS.register("rune_altar",
            () -> new RuneAltarBlock(machine(MapColor.QUARTZ)));
    public static final RegistryObject<Item> RUNE_ALTAR_ITEM = ITEMS.register("rune_altar",
            () -> new MachineBlockItem(RUNE_ALTAR.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<BlockEntityType<RuneAltarBlockEntity>> RUNE_ALTAR_BE = BLOCK_ENTITIES.register("rune_altar",
            () -> BlockEntityType.Builder.of(RuneAltarBlockEntity::new, RUNE_ALTAR.get()).build(null));
    public static final RegistryObject<MenuType<RuneAltarMenu>> RUNE_ALTAR_MENU = MENUS.register("rune_altar",
            () -> IForgeMenuType.create(RuneAltarMenu::new));

    public static final RegistryObject<Block> TERRA_PLATE = BLOCKS.register("terra_plate",
            () -> new TerraPlateBlock(machine(MapColor.COLOR_BLUE)));
    public static final RegistryObject<Item> TERRA_PLATE_ITEM = ITEMS.register("terra_plate",
            () -> new MachineBlockItem(TERRA_PLATE.get(), new Item.Properties().rarity(Rarity.EPIC)));
    public static final RegistryObject<BlockEntityType<TerraPlateBlockEntity>> TERRA_PLATE_BE = BLOCK_ENTITIES.register("terra_plate",
            () -> BlockEntityType.Builder.of(TerraPlateBlockEntity::new, TERRA_PLATE.get()).build(null));
    public static final RegistryObject<MenuType<TerraPlateMenu>> TERRA_PLATE_MENU = MENUS.register("terra_plate",
            () -> IForgeMenuType.create(TerraPlateMenu::new));

    public static final RegistryObject<Block> MANA_INFUSER = BLOCKS.register("mana_infuser",
            () -> new ManaInfuserBlock(machine(MapColor.QUARTZ)));
    public static final RegistryObject<Item> MANA_INFUSER_ITEM = ITEMS.register("mana_infuser",
            () -> new MachineBlockItem(MANA_INFUSER.get(), new Item.Properties().rarity(Rarity.RARE)));
    public static final RegistryObject<BlockEntityType<ManaInfuserBlockEntity>> MANA_INFUSER_BE = BLOCK_ENTITIES.register("mana_infuser",
            () -> BlockEntityType.Builder.of(ManaInfuserBlockEntity::new, MANA_INFUSER.get()).build(null));
    public static final RegistryObject<MenuType<ManaInfuserMenu>> MANA_INFUSER_MENU = MENUS.register("mana_infuser",
            () -> IForgeMenuType.create(ManaInfuserMenu::new));

    private static BlockBehaviour.Properties machine(MapColor color) {
        return BlockBehaviour.Properties.of()
                .mapColor(color)
                .strength(2.5F, 1200.0F)
                .sound(SoundType.STONE)
                .noOcclusion()
                .isViewBlocking((state, level, pos) -> false)
                .isSuffocating((state, level, pos) -> false)
                .lightLevel(state -> state.getValue(MachineBlock.ACTIVE) ? 10 : 3);
    }

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("alfheimheart", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.alfheimheart"))
            .icon(() -> new ItemStack(PORTAL_ITEM.get()))
            .displayItems((params, output) -> {
                output.accept(PORTAL_ITEM.get());
                output.accept(GREENHOUSE_ITEM.get());
                output.accept(RUNE_ALTAR_ITEM.get());
                output.accept(TERRA_PLATE_ITEM.get());
                output.accept(MANA_INFUSER_ITEM.get());
                output.accept(GREENHOUSE_HEART.get());
                output.accept(UPGRADE_BASE.get());
                output.accept(SPEED_UPGRADE.get());
                output.accept(CAPACITY_UPGRADE.get());
                output.accept(LUCK_UPGRADE.get());
                output.accept(YIELD_UPGRADE.get());
            })
            .build());

    public AlfheimHeart() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        ModLoadingContext context = ModLoadingContext.get();
        context.registerConfig(ModConfig.Type.COMMON, com.reya.alfheimheart.greenhouse.Config.SPEC, MODID + "-greenhouse.toml");
        context.registerConfig(ModConfig.Type.COMMON, com.reya.alfheimheart.portal.Config.SPEC, MODID + "-portal.toml");
        context.registerConfig(ModConfig.Type.COMMON, MachineConfig.SPEC, MODID + "-machines.toml");
    }
}
