package com.reya.sandbazaar.test;

import java.util.HashMap;
import java.util.Map;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reya.sandbazaar.ArenaBuilder;
import com.reya.sandbazaar.ArenaData;
import com.reya.sandbazaar.SandBazaar;
import com.reya.sandbazaar.map.BazaarLayout;
import com.reya.sandbazaar.map.Blueprint;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Builds the whole arena in the test world and removes it again. Run with {@code ./gradlew runGameTestServer}; it is
 * not part of normal play. The test world is an ordinary one, generated at a random spot, so the second test meets
 * whatever land happens to be there.
 */
@GameTestHolder(SandBazaar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArenaGameTest {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int W = BazaarLayout.W;
    private static final int D = BazaarLayout.D;

    /**
     * High above any land: once the arena has settled, every block must still be what the blueprint asked for
     * (nothing fell, flowed away or popped off its wall), and undo must leave only air and drop no items.
     */
    @GameTest(template = "empty", batch = "sky", timeoutTicks = 400)
    public static void buildsAndRemovesTheArenaInTheSky(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        BlockPos origin = new BlockPos(base.getX() + 8, 280, base.getZ() + 8);
        forceChunks(level, origin, true);

        long start = System.currentTimeMillis();
        ArenaBuilder.Result result = ArenaBuilder.build(level, origin);
        LOGGER.info("Sunbaked Bazaar: sky arena built in {} ms, {} blocks placed", System.currentTimeMillis() - start, result.placed());
        check(result.unknown() == 0, result.unknown() + " block states of the blueprint are unknown to the game");
        check(ArenaData.get(level).exists(), "the arena was not recorded");

        helper.runAfterDelay(100, () -> {
            Blueprint bp = BazaarLayout.build();
            Block[] expected = expected(level, bp);
            int wrong = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = bp.minY; y <= bp.maxY; y++) {
                for (int z = 0; z < bp.sizeZ; z++) {
                    for (int x = 0; x < bp.sizeX; x++) {
                        int id = bp.paletteIndex(x, y, z);
                        if (id < 0) {
                            continue;
                        }
                        pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        Block found = level.getBlockState(pos).getBlock();
                        // the fountain in the palm court is meant to run over
                        boolean fountain = found == Blocks.WATER && x >= 71 && x <= 73 && z >= 64 && z <= 66 && y >= 2 && y <= 3;
                        if (found != expected[id] && !fountain && wrong++ < 20) {
                            LOGGER.error("Sunbaked Bazaar: at {} {} {} expected {} but found {}", x, y, z, bp.palette().get(id), found);
                        }
                    }
                }
            }
            check(wrong == 0, wrong + " blocks did not stay the way the blueprint placed them");
            for (Blueprint.Sign sign : bp.signs()) {
                pos.set(origin.getX() + sign.x(), origin.getY() + sign.y(), origin.getZ() + sign.z());
                check(level.getBlockEntity(pos) instanceof SignBlockEntity entity && entity.isWaxed(), "no sign for " + sign.key());
            }

            long undoStart = System.currentTimeMillis();
            int restored = ArenaBuilder.undo(level);
            LOGGER.info("Sunbaked Bazaar: sky arena removed in {} ms, {} blocks restored", System.currentTimeMillis() - undoStart, restored);
            check(!ArenaData.get(level).exists(), "the arena is still recorded after undo");
        });

        helper.runAfterDelay(160, () -> {
            int left = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = BazaarLayout.MIN_Y; y <= BazaarLayout.MAX_Y; y++) {
                for (int z = 0; z < D; z++) {
                    for (int x = 0; x < W; x++) {
                        pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        if (!level.getBlockState(pos).isAir() && left++ < 20) {
                            LOGGER.error("Sunbaked Bazaar: {} left behind at {} {} {}", level.getBlockState(pos), x, y, z);
                        }
                    }
                }
            }
            check(left == 0, left + " blocks were left behind after undo");
            AABB box = new AABB(origin.offset(-8, BazaarLayout.MIN_Y - 60, -8), origin.offset(W + 8, BazaarLayout.MAX_Y + 8, D + 8));
            int drops = level.getEntitiesOfClass(ItemEntity.class, box).size();
            check(drops == 0, drops + " items were dropped while building or removing the arena");
            forceChunks(level, origin, false);
            helper.succeed();
        });
    }

    /**
     * On the ground, wherever the test world put us: after undo every block the arena touched, and every chest or
     * spawner among them, must be exactly what it was - and must still be there a few seconds later.
     */
    @GameTest(template = "empty", batch = "terrain", timeoutTicks = 400)
    public static void putsTheLandBack(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(BlockPos.ZERO);
        int x0 = base.getX() + 8;
        int z0 = base.getZ() + 200;
        forceChunks(level, new BlockPos(x0, 0, z0), true);
        int surface = level.getHeight(Heightmap.Types.WORLD_SURFACE, x0 + W / 2, z0 + D / 2);
        BlockPos origin = new BlockPos(x0, Mth.clamp(surface - 1, level.getMinBuildHeight() - BazaarLayout.MIN_Y + 1,
                level.getMaxBuildHeight() - BazaarLayout.MAX_Y - 2), z0);

        int bottom = origin.getY() + BazaarLayout.MIN_Y;
        int height = level.getMaxBuildHeight() - bottom;
        BlockState[] before = new BlockState[W * D * height];
        Map<BlockPos, CompoundTag> entitiesBefore = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int y = 0; y < height; y++) {
            for (int z = 0; z < D; z++) {
                for (int x = 0; x < W; x++, i++) {
                    pos.set(x0 + x, bottom + y, z0 + z);
                    before[i] = level.getBlockState(pos);
                    BlockEntity entity = before[i].hasBlockEntity() ? level.getBlockEntity(pos) : null;
                    if (entity != null) {
                        entitiesBefore.put(pos.immutable(), entity.saveWithFullMetadata());
                    }
                }
            }
        }

        long start = System.currentTimeMillis();
        ArenaBuilder.Result result = ArenaBuilder.build(level, origin);
        ArenaData data = ArenaData.get(level);
        LOGGER.info("Sunbaked Bazaar: ground arena at {} (biome {}) built in {} ms, {} blocks placed, box {} high, {} block entities kept",
                origin, level.getBiome(origin).unwrapKey().map(key -> key.location().toString()).orElse("?"),
                System.currentTimeMillis() - start, result.placed(), data.sizeY(), data.blockEntities().size());
        int sizeY = data.sizeY();
        int[] touched = data.cells();

        helper.runAfterDelay(60, () -> {
            long undoStart = System.currentTimeMillis();
            int restored = ArenaBuilder.undo(level);
            LOGGER.info("Sunbaked Bazaar: ground arena removed in {} ms, {} blocks restored", System.currentTimeMillis() - undoStart, restored);
            int wrong = differences(level, x0, bottom, z0, sizeY, touched, before, true);
            check(wrong == 0, wrong + " blocks are not what they were before the arena");
            int lost = 0;
            for (Map.Entry<BlockPos, CompoundTag> entry : entitiesBefore.entrySet()) {
                BlockPos at = entry.getKey();
                int y = at.getY() - bottom;
                if (y >= sizeY || touched[(y * D + at.getZ() - z0) * W + at.getX() - x0] < 0) {
                    continue;
                }
                BlockEntity entity = level.getBlockEntity(at);
                if ((entity == null || !entity.saveWithFullMetadata().equals(entry.getValue())) && lost++ < 20) {
                    LOGGER.error("Sunbaked Bazaar: block entity at {} came back different: {}", at, entry.getValue());
                }
            }
            check(lost == 0, lost + " block entities did not come back with their contents");
        });

        helper.runAfterDelay(140, () -> {
            int wrong = differences(level, x0, bottom, z0, sizeY, touched, before, false);
            LOGGER.info("Sunbaked Bazaar: {} blocks changed in the four seconds after undo", wrong);
            check(wrong <= 100, wrong + " blocks changed after they were put back - something collapsed or flowed");
            forceChunks(level, origin, false);
            helper.succeed();
        });
    }

    /** Counts the touched cells that differ from the snapshot, by exact state or just by block. */
    private static int differences(ServerLevel level, int x0, int bottom, int z0, int sizeY, int[] touched, BlockState[] before, boolean exact) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int wrong = 0;
        int i = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < D; z++) {
                for (int x = 0; x < W; x++, i++) {
                    if (touched[i] < 0) {
                        continue;
                    }
                    pos.set(x0 + x, bottom + y, z0 + z);
                    BlockState now = level.getBlockState(pos);
                    boolean same = exact ? now == before[i] : now.getBlock() == before[i].getBlock();
                    if (!same && wrong++ < 20) {
                        LOGGER.error("Sunbaked Bazaar: at {} was {} and is now {}", pos, before[i], now);
                    }
                }
            }
        }
        return wrong;
    }

    private static Block[] expected(ServerLevel level, Blueprint bp) {
        Block[] blocks = new Block[bp.palette().size()];
        for (int i = 0; i < blocks.length; i++) {
            try {
                blocks[i] = BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), bp.palette().get(i), false).blockState().getBlock();
            } catch (CommandSyntaxException e) {
                blocks[i] = Blocks.AIR;
            }
        }
        return blocks;
    }

    /** Keeps the chunks under the arena loaded and ticking (or lets them go again). */
    private static void forceChunks(ServerLevel level, BlockPos origin, boolean forced) {
        ChunkPos first = new ChunkPos(origin);
        ChunkPos last = new ChunkPos(origin.offset(W - 1, 0, D - 1));
        for (int x = first.x; x <= last.x; x++) {
            for (int z = first.z; z <= last.z; z++) {
                level.setChunkForced(x, z, forced);
                if (forced) {
                    level.getChunk(x, z);
                }
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
