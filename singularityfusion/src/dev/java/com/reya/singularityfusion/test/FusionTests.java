package com.reya.singularityfusion.test;

import com.reya.singularityfusion.SingularityFusion;
import com.reya.singularityfusion.block.FusionCoreBlockEntity;
import com.reya.singularityfusion.block.FusionStatus;
import com.reya.singularityfusion.block.GravitonPylonBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * The fusion structure at work in a real world, run by `gradlew runGameTestServer` (CI fails if any fails): each
 * test builds a core on its foundation with four pylons round it in an empty room, and looks at what it sees and does.
 */
@GameTestHolder(SingularityFusion.MODID)
@PrefixGameTestTemplate(false)
public class FusionTests {
    /** A room nine blocks square: its stone floor is at y = 1 (a test's y = 0 is the structure block's), so tests build at y = 2. */
    private static final String ROOM = "empty";
    private static final BlockPos CORE = new BlockPos(4, 3, 4);
    private static final BlockPos[] PYLONS = {new BlockPos(1, 3, 4), new BlockPos(7, 3, 4), new BlockPos(4, 3, 1), new BlockPos(4, 3, 7)};
    /** The cheapest fusion: a nether star with two echo shards and two dragon's breaths makes a singularity shard. */
    private static final long SHARD_ENERGY = 64_000_000L;

    // ------------------------------------------------------------------ helpers

    private static FusionCoreBlockEntity build(GameTestHelper h, boolean foundation) {
        if (foundation) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    h.setBlock(CORE.offset(dx, -1, dz), (dx + dz) % 2 == 0 ? SingularityFusion.VOID_CASING.get() : SingularityFusion.VOID_CASING_RUNE.get());
                }
            }
        }
        h.setBlock(CORE, SingularityFusion.FUSION_CORE.get());
        for (BlockPos p : PYLONS) h.setBlock(p, SingularityFusion.GRAVITON_PYLON.get());
        return (FusionCoreBlockEntity) h.getBlockEntity(CORE);
    }

    private static GravitonPylonBlockEntity pylon(GameTestHelper h, int i) {
        return (GravitonPylonBlockEntity) h.getBlockEntity(PYLONS[i]);
    }

    /** Puts the shard recipe on the pylons and in the core, and energy enough (or not). */
    private static void shardRecipe(GameTestHelper h, FusionCoreBlockEntity core, long energy) {
        Item[] items = {Items.ECHO_SHARD, Items.DRAGON_BREATH, Items.ECHO_SHARD, Items.DRAGON_BREATH};
        for (int i = 0; i < items.length; i++) pylon(h, i).setItem(new ItemStack(items[i]));
        core.items().setStackInSlot(FusionCoreBlockEntity.CATALYST, new ItemStack(Items.NETHER_STAR));
        core.setEnergy(energy);
    }

    // ------------------------------------------------------------------ the structure

    @GameTest(template = ROOM)
    public static void theCoreFindsItsPylons(GameTestHelper h) {
        FusionCoreBlockEntity core = build(h, true);
        h.succeedWhen(() -> {
            h.assertTrue(core.pylons().size() == 4, "the core should find 4 pylons, found " + core.pylons().size());
            h.assertTrue(core.status() == FusionStatus.NO_RECIPE, "with nothing on them it waits for a recipe, not " + core.status());
            for (int i = 0; i < PYLONS.length; i++) {
                h.assertTrue(h.absolutePos(CORE).equals(pylon(h, i).core()), "pylon " + i + " should serve the core");
            }
        });
    }

    @GameTest(template = ROOM)
    public static void noFoundationNoSingularity(GameTestHelper h) {
        FusionCoreBlockEntity core = build(h, false);
        core.setEnergy(1_000_000_000L);
        h.succeedWhen(() -> {
            h.assertTrue(core.status() == FusionStatus.NO_FOUNDATION, "without its casing floor it should say so, not " + core.status());
            h.assertTrue(core.charge() == 0.0F, "and no singularity forms over it");
        });
    }

    @GameTest(template = ROOM)
    public static void halfFullHalfGrown(GameTestHelper h) {
        FusionCoreBlockEntity core = build(h, true);
        core.setEnergy(core.capacity() / 2);
        h.succeedWhen(() -> {
            h.assertTrue(core.status().formed(), "the structure should be whole, not " + core.status());
            h.assertTrue(Math.abs(core.charge() - 0.5F) < 0.001F, "half full, the singularity should be half grown, not " + core.charge());
        });
    }

    @GameTest(template = ROOM)
    public static void somethingOverTheCoreBlocksIt(GameTestHelper h) {
        FusionCoreBlockEntity core = build(h, true);
        h.setBlock(CORE.above(4), Blocks.STONE);
        h.succeedWhen(() -> h.assertTrue(core.status() == FusionStatus.BLOCKED, "a block where the singularity forms blocks it, not " + core.status()));
    }

    // ------------------------------------------------------------------ fusing

    @GameTest(template = ROOM, timeoutTicks = 400)
    public static void aFusionMakesAShard(GameTestHelper h) {
        FusionCoreBlockEntity core = build(h, true);
        shardRecipe(h, core, 100_000_000L);
        h.runAfterDelay(3, () -> h.assertTrue(core.start(), "the fusion should start, but the core says " + core.status()));
        h.succeedWhen(() -> {
            h.assertFalse(core.fusing(), "still fusing");
            h.assertTrue(core.items().getStackInSlot(FusionCoreBlockEntity.OUTPUT).is(SingularityFusion.SINGULARITY_SHARD.get()), "no shard made");
            h.assertTrue(core.energy() == 100_000_000L - SHARD_ENERGY, "the fusion should take its energy, left " + core.energy());
            h.assertTrue(core.items().getStackInSlot(FusionCoreBlockEntity.CATALYST).isEmpty(), "the catalyst should be used up");
            h.assertTrue(pylon(h, 0).item().isEmpty(), "the echo shards should be used up");
            h.assertTrue(pylon(h, 1).item().is(Items.GLASS_BOTTLE), "dragon's breath leaves its bottle");
        });
    }

    @GameTest(template = ROOM)
    public static void withoutEnergyItWontStart(GameTestHelper h) {
        FusionCoreBlockEntity core = build(h, true);
        shardRecipe(h, core, SHARD_ENERGY - 1);
        h.runAfterDelay(3, () -> {
            h.assertFalse(core.start(), "it shouldn't start without the energy the fusion takes");
            h.assertTrue(core.status() == FusionStatus.NO_ENERGY, "it should say it needs energy, not " + core.status());
            h.succeed();
        });
    }

    @GameTest(template = ROOM)
    public static void wrongItemsMakeNothing(GameTestHelper h) {
        FusionCoreBlockEntity core = build(h, true);
        shardRecipe(h, core, 100_000_000L);
        pylon(h, 2).setItem(new ItemStack(Items.DIRT));
        h.runAfterDelay(3, () -> {
            h.assertTrue(core.status() == FusionStatus.NO_RECIPE, "dirt on a pylon makes no recipe, but it says " + core.status());
            h.assertFalse(core.start(), "so it can't start");
            h.succeed();
        });
    }

    @GameTest(template = ROOM, timeoutTicks = 200)
    public static void takingAnItemStopsTheFusion(GameTestHelper h) {
        FusionCoreBlockEntity core = build(h, true);
        shardRecipe(h, core, 100_000_000L);
        h.runAfterDelay(3, () -> h.assertTrue(core.start(), "the fusion should start, but the core says " + core.status()));
        h.runAfterDelay(30, () -> pylon(h, 0).setItem(ItemStack.EMPTY));
        h.runAfterDelay(60, () -> {
            h.assertFalse(core.fusing(), "the fusion should have stopped");
            h.assertTrue(core.energy() == 100_000_000L, "a stopped fusion spends nothing, left " + core.energy());
            h.assertTrue(core.items().getStackInSlot(FusionCoreBlockEntity.CATALYST).is(Items.NETHER_STAR), "and keeps its catalyst");
            h.assertTrue(core.items().getStackInSlot(FusionCoreBlockEntity.OUTPUT).isEmpty(), "and makes nothing");
            h.succeed();
        });
    }

    @GameTest(template = ROOM)
    public static void aRedstonePulseStartsIt(GameTestHelper h) {
        FusionCoreBlockEntity core = build(h, true);
        shardRecipe(h, core, 100_000_000L);
        h.runAfterDelay(5, () -> h.setBlock(CORE.east(), Blocks.REDSTONE_BLOCK));
        h.succeedWhen(() -> h.assertTrue(core.fusing(), "a redstone signal coming on should start the fusion (" + core.status() + ")"));
    }

    // ------------------------------------------------------------------ energy

    @GameTest(template = ROOM)
    public static void energyGoesInNotOut(GameTestHelper h) {
        FusionCoreBlockEntity core = build(h, true);
        IEnergyStorage energy = core.getCapability(ForgeCapabilities.ENERGY, Direction.UP).resolve().orElseThrow();
        h.assertTrue(energy.receiveEnergy(5000, false) == 5000, "the core should take energy");
        h.assertTrue(core.energy() == 5000L, "and hold it, holds " + core.energy());
        h.assertTrue(energy.extractEnergy(1000, false) == 0, "but give none out");
        h.succeed();
    }

    @GameTest(template = ROOM)
    public static void aCreativeCellFillsTheCore(GameTestHelper h) {
        FusionCoreBlockEntity core = build(h, true);
        h.setBlock(CORE.west(), SingularityFusion.CREATIVE_CELL.get());
        h.succeedWhen(() -> h.assertTrue(core.energy() == core.capacity(), "a creative cell beside it should fill it, it holds " + core.energy()));
    }
}
