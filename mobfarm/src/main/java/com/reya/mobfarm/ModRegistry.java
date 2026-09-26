package com.reya.mobfarm;

import java.util.EnumMap;
import java.util.Map;

import com.reya.mobfarm.farm.MobFarmBlock;
import com.reya.mobfarm.farm.MobFarmBlockEntity;
import com.reya.mobfarm.farm.MobFarmMenu;
import com.reya.mobfarm.lasso.LassoItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MobFarm.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MobFarm.MOD_ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MobFarm.MOD_ID);
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(ForgeRegistries.MENU_TYPES, MobFarm.MOD_ID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MobFarm.MOD_ID);

    public static final RegistryObject<Item> LASSO = ITEMS.register("lasso",
            () -> new LassoItem(new Item.Properties().durability(64)));

    public static final Map<FarmTier, RegistryObject<Block>> FARMS = new EnumMap<>(FarmTier.class);

    static {
        for (FarmTier tier : FarmTier.values()) {
            RegistryObject<Block> block = BLOCKS.register(tier.id + "_mob_farm", () -> new MobFarmBlock(tier, properties(tier)));
            FARMS.put(tier, block);
            ITEMS.register(tier.id + "_mob_farm", () -> new BlockItem(block.get(), new Item.Properties()));
        }
    }

    public static final RegistryObject<BlockEntityType<MobFarmBlockEntity>> MOB_FARM = BLOCK_ENTITIES.register("mob_farm",
            () -> BlockEntityType.Builder.of(MobFarmBlockEntity::new,
                    FARMS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    public static final RegistryObject<MenuType<MobFarmMenu>> MOB_FARM_MENU = MENUS.register("mob_farm",
            () -> IForgeMenuType.create(MobFarmMenu::new));

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.mobfarm"))
            .icon(() -> new ItemStack(FARMS.get(FarmTier.DIAMOND).get()))
            .displayItems((params, output) -> {
                output.accept(LASSO.get());
                for (FarmTier tier : FarmTier.values()) output.accept(FARMS.get(tier).get());
            })
            .build());

    private static BlockBehaviour.Properties properties(FarmTier tier) {
        BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
                .mapColor(tier.mapColor)
                .strength(tier.hardness, 6.0F)
                .sound(tier.sound)
                .noOcclusion()
                .isSuffocating((state, level, pos) -> false)
                .isViewBlocking((state, level, pos) -> false);
        return tier.needsPickaxe ? props.requiresCorrectToolForDrops() : props;
    }

    private ModRegistry() {
    }
}
