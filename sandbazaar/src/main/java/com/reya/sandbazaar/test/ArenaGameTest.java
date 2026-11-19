package com.reya.sandbazaar.test;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Builds the whole arena in the test world, lets it settle, checks that every block is still what the blueprint
 * asked for (nothing fell, flowed away or popped off its wall), then removes it and checks the land is back.
 * Run with {@code ./gradlew runGameTestServer}; it is not part of normal play.
 */
@GameTestHolder(SandBazaar.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ArenaGameTest {
    private static final Logger LOGGER = LogUtils.getLogger();

    @GameTest(template = "empty", timeoutTicks = 400)
    public static void buildsAndRemovesTheArena(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO).offset(8, 30, 8);
        ChunkPos first = new ChunkPos(origin);
        ChunkPos last = new ChunkPos(origin.offset(BazaarLayout.W - 1, 0, BazaarLayout.D - 1));
        forceChunks(level, first, last, true);

        long start = System.currentTimeMillis();
        ArenaBuilder.Result result = ArenaBuilder.build(level, origin);
        LOGGER.info("Sunbaked Bazaar: built in {} ms, {} blocks placed", System.currentTimeMillis() - start, result.placed());
        check(result.unknown() == 0, result.unknown() + " block states of the blueprint are unknown to the game");
        check(ArenaData.get(level).exists(), "the arena was not recorded");

        helper.runAfterDelay(100, () -> {
            Blueprint bp = BazaarLayout.build();
            int wrong = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            Block[] expected = expected(level, bp);
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
                        if (found != expected[id] && !fountain) {
                            if (wrong++ < 20) {
                                LOGGER.error("Sunbaked Bazaar: at {} {} {} expected {} but found {}", x, y, z, bp.palette().get(id), found);
                            }
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
            LOGGER.info("Sunbaked Bazaar: removed in {} ms, {} blocks restored", System.currentTimeMillis() - undoStart, restored);
            check(!ArenaData.get(level).exists(), "the arena is still recorded after undo");
        });

        helper.runAfterDelay(160, () -> {
            int left = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int y = BazaarLayout.MIN_Y; y <= BazaarLayout.MAX_Y; y++) {
                for (int z = 0; z < BazaarLayout.D; z++) {
                    for (int x = 0; x < BazaarLayout.W; x++) {
                        pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                        if (!level.getBlockState(pos).isAir() && left++ < 20) {
                            LOGGER.error("Sunbaked Bazaar: {} left behind at {} {} {}", level.getBlockState(pos), x, y, z);
                        }
                    }
                }
            }
            check(left == 0, left + " blocks were left behind after undo");
            AABB box = new AABB(origin.offset(-8, BazaarLayout.MIN_Y - 40, -8),
                    origin.offset(BazaarLayout.W + 8, BazaarLayout.MAX_Y + 8, BazaarLayout.D + 8));
            int drops = level.getEntitiesOfClass(ItemEntity.class, box).size();
            check(drops == 0, drops + " items were dropped while building or removing the arena");
            forceChunks(level, first, last, false);
            helper.succeed();
        });
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

    private static void forceChunks(ServerLevel level, ChunkPos first, ChunkPos last, boolean forced) {
        for (int x = first.x; x <= last.x; x++) {
            for (int z = first.z; z <= last.z; z++) {
                level.setChunkForced(x, z, forced);
            }
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
