package com.reya.quantumsolar;

import java.util.ArrayList;
import java.util.List;

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
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

@Mod(QuantumSolar.MODID)
public class QuantumSolar {
    public static final String MODID = "quantumsolar";

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** The blocks and their items, in Panels.ALL's order. */
    public static final List<RegistryObject<Block>> PANEL_BLOCKS = new ArrayList<>();
    public static final List<RegistryObject<Item>> PANEL_ITEMS = new ArrayList<>();

    static {
        for (Panels.Panel p : Panels.ALL) {
            RegistryObject<Block> block = BLOCKS.register(p.id(), () -> new PanelBlock(p, BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(3.0F, 12.0F)
                    .sound(SoundType.METAL)
                    .requiresCorrectToolForDrops()
                    .lightLevel(state -> p.quantum() ? 7 : 0)));
            PANEL_BLOCKS.add(block);
            Rarity rarity = p.quantum() ? (p.tier() >= 16 ? Rarity.EPIC : Rarity.RARE) : p.tier() >= 18 ? Rarity.RARE : p.tier() >= 10 ? Rarity.UNCOMMON : Rarity.COMMON;
            PANEL_ITEMS.add(ITEMS.register(p.id(), () -> new BlockItem(block.get(), new Item.Properties().rarity(rarity))));
        }
    }

    public static final RegistryObject<BlockEntityType<PanelBlockEntity>> PANEL_BE = BLOCK_ENTITIES.register("panel",
            () -> BlockEntityType.Builder.of(PanelBlockEntity::new, PANEL_BLOCKS.stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));
    public static final RegistryObject<MenuType<PanelMenu>> PANEL_MENU = MENUS.register("panel", () -> IForgeMenuType.create(PanelMenu::new));
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("quantumsolar", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.quantumsolar"))
            .icon(() -> new ItemStack(PANEL_ITEMS.get(PANEL_ITEMS.size() - 1).get()))
            .displayItems((params, output) -> PANEL_ITEMS.forEach(i -> output.accept(i.get())))
            .build());

    public QuantumSolar() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        TABS.register(bus);
    }
}
