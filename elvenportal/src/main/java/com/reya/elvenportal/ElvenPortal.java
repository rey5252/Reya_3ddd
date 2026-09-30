package com.reya.elvenportal;

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
 * Elven Portal, a Botania addon: a whole Alfheim gateway folded into one block. Items put in it (by hand,
 * by hoppers and pipes, or thrown on it) go to the elves, who trade them by every elven trade recipe:
 * Botania's own, the ore trades of this mod and any a pack adds. Each trade costs mana, which the portal
 * takes from mana spreaders and from the mana pools beside it.
 */
@Mod(ElvenPortal.MODID)
public class ElvenPortal {
    public static final String MODID = "elvenportal";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

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

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("elvenportal", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.elvenportal"))
            .icon(() -> new ItemStack(PORTAL_ITEM.get()))
            .displayItems((params, output) -> output.accept(PORTAL_ITEM.get()))
            .build());

    public ElvenPortal() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, Config.SPEC);
    }
}
