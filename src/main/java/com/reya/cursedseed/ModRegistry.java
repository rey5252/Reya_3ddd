package com.reya.cursedseed;

import com.reya.cursedseed.block.CursedEarthBlock;
import com.reya.cursedseed.item.CursedSeedItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRegistry {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, CursedSeed.MOD_ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, CursedSeed.MOD_ID);

    public static final RegistryObject<Block> CURSED_EARTH = BLOCKS.register("cursed_earth",
            () -> new CursedEarthBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_BLACK)
                    .strength(0.6F)
                    .sound(SoundType.GRAVEL)
                    .randomTicks()));

    public static final RegistryObject<Item> CURSED_EARTH_ITEM = ITEMS.register("cursed_earth",
            () -> new BlockItem(CURSED_EARTH.get(), new Item.Properties()));

    public static final RegistryObject<Item> CURSED_SEED = ITEMS.register("cursed_seed",
            () -> new CursedSeedItem(new Item.Properties().rarity(Rarity.RARE)));

    private ModRegistry() {
    }
}
