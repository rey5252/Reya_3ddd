package com.reya.boundlessrouters;

import java.util.EnumMap;
import java.util.Map;

import com.mojang.logging.LogUtils;
import com.reya.boundlessrouters.module.ModuleItem;
import com.reya.boundlessrouters.module.ModuleKind;
import com.reya.boundlessrouters.module.ModuleMenu;
import com.reya.boundlessrouters.network.Net;
import com.reya.boundlessrouters.router.RouterBlock;
import com.reya.boundlessrouters.router.RouterBlockEntity;
import com.reya.boundlessrouters.router.RouterBlockItem;
import com.reya.boundlessrouters.router.RouterMenu;
import com.reya.boundlessrouters.upgrade.UpgradeItem;
import com.reya.boundlessrouters.upgrade.UpgradeKind;
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
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

/**
 * Boundless Routers: one block, the Item Router, and modules plugged into it that move items around the world in
 * every way: pull them out of inventories and send them anywhere, in any dimension; spread them over many targets;
 * drop and fling them; place and break blocks; vacuum up dropped items; void them; reach into a player's
 * inventory; extend lines of blocks; signal redstone when they come. Nothing caps it: no range, no dimension, no
 * upgrade limit, a router as fast as a tick. Inspired by desht's Modular Routers.
 */
@Mod(BoundlessRouters.MODID)
public class BoundlessRouters {
    public static final String MODID = "boundlessrouters";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<Block> ROUTER = BLOCKS.register("router",
            () -> new RouterBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.METAL)
                    .strength(3.0F, 1200.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> state.getValue(RouterBlock.ACTIVE) ? 7 : 0)));
    public static final RegistryObject<Item> ROUTER_ITEM = ITEMS.register("router",
            () -> new RouterBlockItem(ROUTER.get(), new Item.Properties().rarity(Rarity.UNCOMMON)));
    public static final RegistryObject<BlockEntityType<RouterBlockEntity>> ROUTER_BE = BLOCK_ENTITIES.register("router",
            () -> BlockEntityType.Builder.of(RouterBlockEntity::new, ROUTER.get()).build(null));

    public static final RegistryObject<Item> BLANK_MODULE = ITEMS.register("blank_module", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> BLANK_UPGRADE = ITEMS.register("blank_upgrade", () -> new Item(new Item.Properties()));

    public static final Map<ModuleKind, RegistryObject<Item>> MODULES = new EnumMap<>(ModuleKind.class);
    public static final Map<UpgradeKind, RegistryObject<Item>> UPGRADES = new EnumMap<>(UpgradeKind.class);

    static {
        for (ModuleKind kind : ModuleKind.values()) {
            MODULES.put(kind, ITEMS.register(kind.id() + "_module", () -> new ModuleItem(kind, new Item.Properties().stacksTo(1))));
        }
        for (UpgradeKind kind : UpgradeKind.values()) {
            UPGRADES.put(kind, ITEMS.register(kind.id() + "_upgrade", () -> new UpgradeItem(kind, new Item.Properties())));
        }
    }

    public static final RegistryObject<MenuType<RouterMenu>> ROUTER_MENU = MENUS.register("router",
            () -> IForgeMenuType.create(RouterMenu::new));
    public static final RegistryObject<MenuType<ModuleMenu>> MODULE_MENU = MENUS.register("module",
            () -> IForgeMenuType.create(ModuleMenu::fromNetwork));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MODID))
            .icon(() -> new ItemStack(ROUTER_ITEM.get()))
            .displayItems((parameters, output) -> {
                output.accept(ROUTER_ITEM.get());
                output.accept(BLANK_MODULE.get());
                for (ModuleKind kind : ModuleKind.values()) output.accept(MODULES.get(kind).get());
                output.accept(BLANK_UPGRADE.get());
                for (UpgradeKind kind : UpgradeKind.values()) output.accept(UPGRADES.get(kind).get());
            })
            .build());

    public BoundlessRouters() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        bus.addListener(this::setup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, RouterConfig.SPEC);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
    }

    public static Item module(ModuleKind kind) {
        return MODULES.get(kind).get();
    }

    public static Item upgrade(UpgradeKind kind) {
        return UPGRADES.get(kind).get();
    }
}
