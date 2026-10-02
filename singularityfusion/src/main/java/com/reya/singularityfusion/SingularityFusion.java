package com.reya.singularityfusion;

import com.mojang.logging.LogUtils;
import com.reya.singularityfusion.block.CreativeCellBlock;
import com.reya.singularityfusion.block.CreativeCellBlockEntity;
import com.reya.singularityfusion.block.FusionCoreBlock;
import com.reya.singularityfusion.block.FusionCoreBlockEntity;
import com.reya.singularityfusion.block.GravitonPylonBlock;
import com.reya.singularityfusion.block.GravitonPylonBlockEntity;
import com.reya.singularityfusion.item.DescribedItem;
import com.reya.singularityfusion.menu.FusionCoreMenu;
import com.reya.singularityfusion.network.Net;
import com.reya.singularityfusion.recipe.FusionRecipe;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
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
 * Singularity Fusion: a fusion crafting structure beyond chaos. A fusion core on a void casing foundation, graviton
 * pylons round it holding the ingredients, energy poured into the core, and above it a black hole that grows as it
 * fills: the singularity that fuses what the pylons hold, with the core's catalyst, into what lies beyond.
 */
@Mod(SingularityFusion.MODID)
public class SingularityFusion {
    public static final String MODID = "singularityfusion";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, MODID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, MODID);
    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    // ------------------------------------------------------------------ blocks

    private static BlockBehaviour.Properties voidStone() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_BLACK).strength(8.0F, 1200.0F).requiresCorrectToolForDrops()
                .sound(SoundType.DEEPSLATE_TILES);
    }

    /** The foundation and the build: dark engraved tiles, one with a glowing rune, one with a glowing seam. */
    public static final RegistryObject<Block> VOID_CASING = BLOCKS.register("void_casing", () -> new Block(voidStone()));
    public static final RegistryObject<Block> VOID_CASING_RUNE = BLOCKS.register("void_casing_rune",
            () -> new Block(voidStone().lightLevel(s -> 7)));
    public static final RegistryObject<Block> VOID_CASING_SEAM = BLOCKS.register("void_casing_seam",
            () -> new Block(voidStone().lightLevel(s -> 5)));
    public static final RegistryObject<Block> FUSION_CORE = BLOCKS.register("fusion_core",
            () -> new FusionCoreBlock(voidStone().strength(25.0F, 3600.0F).lightLevel(s -> s.getValue(FusionCoreBlock.LIT) ? 15 : 6)));
    public static final RegistryObject<Block> GRAVITON_PYLON = BLOCKS.register("graviton_pylon",
            () -> new GravitonPylonBlock(voidStone().strength(12.0F, 1200.0F).lightLevel(s -> 9)));
    public static final RegistryObject<Block> CREATIVE_CELL = BLOCKS.register("creative_cell",
            () -> new CreativeCellBlock(voidStone().strength(50.0F, 3600000.0F).lightLevel(s -> 12)));

    public static final RegistryObject<Item> VOID_CASING_ITEM = block(VOID_CASING, Rarity.COMMON);
    public static final RegistryObject<Item> VOID_CASING_RUNE_ITEM = block(VOID_CASING_RUNE, Rarity.COMMON);
    public static final RegistryObject<Item> VOID_CASING_SEAM_ITEM = block(VOID_CASING_SEAM, Rarity.COMMON);
    public static final RegistryObject<Item> FUSION_CORE_ITEM = block(FUSION_CORE, Rarity.EPIC);
    public static final RegistryObject<Item> GRAVITON_PYLON_ITEM = block(GRAVITON_PYLON, Rarity.RARE);
    public static final RegistryObject<Item> CREATIVE_CELL_ITEM = block(CREATIVE_CELL, Rarity.EPIC);

    private static RegistryObject<Item> block(RegistryObject<Block> block, Rarity rarity) {
        return ITEMS.register(block.getId().getPath(), () -> new DescribedItem.Placed(block.get(), new Item.Properties().rarity(rarity)));
    }

    // ------------------------------------------------------------------ items

    /** A crystal that bends gravity: what pylons and the core are built of. */
    public static final RegistryObject<Item> GRAVITON_CRYSTAL = ITEMS.register("graviton_crystal",
            () -> new DescribedItem(new Item.Properties().rarity(Rarity.UNCOMMON), false));
    /** A splinter of a singularity: the first thing the core fuses. */
    public static final RegistryObject<Item> SINGULARITY_SHARD = ITEMS.register("singularity_shard",
            () -> new DescribedItem(new Item.Properties().rarity(Rarity.RARE), false));
    /** A star crushed to a point, its light still turning round it. */
    public static final RegistryObject<Item> COLLAPSED_STAR = ITEMS.register("collapsed_star",
            () -> new DescribedItem(new Item.Properties().rarity(Rarity.EPIC), true));
    /** The heart of a black hole, held: beyond chaos. */
    public static final RegistryObject<Item> EVENT_HORIZON_CORE = ITEMS.register("event_horizon_core",
            () -> new DescribedItem(new Item.Properties().rarity(Rarity.EPIC).stacksTo(16).fireResistant(), true));

    // ------------------------------------------------------------------ block entities, menus, recipes

    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<FusionCoreBlockEntity>> FUSION_CORE_BE = BLOCK_ENTITIES.register("fusion_core",
            () -> BlockEntityType.Builder.of(FusionCoreBlockEntity::new, FUSION_CORE.get()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<GravitonPylonBlockEntity>> GRAVITON_PYLON_BE = BLOCK_ENTITIES.register("graviton_pylon",
            () -> BlockEntityType.Builder.of(GravitonPylonBlockEntity::new, GRAVITON_PYLON.get()).build(null));
    @SuppressWarnings("ConstantConditions")
    public static final RegistryObject<BlockEntityType<CreativeCellBlockEntity>> CREATIVE_CELL_BE = BLOCK_ENTITIES.register("creative_cell",
            () -> BlockEntityType.Builder.of(CreativeCellBlockEntity::new, CREATIVE_CELL.get()).build(null));

    public static final RegistryObject<MenuType<FusionCoreMenu>> FUSION_CORE_MENU = MENUS.register("fusion_core",
            () -> IForgeMenuType.create(FusionCoreMenu::fromNetwork));

    public static final RegistryObject<RecipeType<FusionRecipe>> FUSION_TYPE = RECIPE_TYPES.register("fusion",
            () -> RecipeType.simple(new net.minecraft.resources.ResourceLocation(MODID, "fusion")));
    public static final RegistryObject<RecipeSerializer<FusionRecipe>> FUSION_SERIALIZER = RECIPE_SERIALIZERS.register("fusion",
            FusionRecipe.Serializer::new);

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup." + MODID))
            .icon(() -> new ItemStack(FUSION_CORE_ITEM.get()))
            .displayItems((parameters, output) -> {
                output.accept(FUSION_CORE_ITEM.get());
                output.accept(GRAVITON_PYLON_ITEM.get());
                output.accept(VOID_CASING_ITEM.get());
                output.accept(VOID_CASING_RUNE_ITEM.get());
                output.accept(VOID_CASING_SEAM_ITEM.get());
                output.accept(GRAVITON_CRYSTAL.get());
                output.accept(SINGULARITY_SHARD.get());
                output.accept(COLLAPSED_STAR.get());
                output.accept(EVENT_HORIZON_CORE.get());
                output.accept(CREATIVE_CELL_ITEM.get());
            })
            .build());

    public SingularityFusion() {
        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        RECIPE_TYPES.register(bus);
        RECIPE_SERIALIZERS.register(bus);
        TABS.register(bus);
        bus.addListener(this::setup);
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, FusionConfig.SPEC);
    }

    private void setup(FMLCommonSetupEvent event) {
        event.enqueueWork(Net::register);
    }
}
