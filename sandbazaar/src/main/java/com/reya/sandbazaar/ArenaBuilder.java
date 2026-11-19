package com.reya.sandbazaar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reya.sandbazaar.map.BazaarLayout;
import com.reya.sandbazaar.map.Blueprint;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;

/** Puts the arena into a world and takes it out again. */
public final class ArenaBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Tell clients, but drop nothing and wake no redstone. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

    /** @param unknown block states of the blueprint this game does not know; they were left out */
    public record Result(int placed, int unknown) {}

    private ArenaBuilder() {}

    /** Where the arena's corner goes for someone standing at {@code feet}: just east of them, centred on them. */
    public static BlockPos originFor(BlockPos feet) {
        return feet.offset(3, -1, -BazaarLayout.D / 2);
    }

    /**
     * Builds the arena with its (0, 0, 0) at {@code origin} and remembers the land it replaces. Whatever rises above
     * the arena inside its footprint - hills, trees, sand that would otherwise pour in - is cleared up to the sky.
     */
    public static Result build(ServerLevel level, BlockPos origin) {
        Blueprint bp = BazaarLayout.build();
        HolderLookup<Block> blocks = level.holderLookup(Registries.BLOCK);
        List<String> names = bp.palette();
        BlockState[] states = new BlockState[names.size()];
        int unknown = 0;
        for (int i = 0; i < states.length; i++) {
            try {
                states[i] = BlockStateParser.parseForBlock(blocks, names.get(i), false).blockState();
            } catch (CommandSyntaxException e) {
                LOGGER.warn("Sunbaked Bazaar: skipping unknown block state '{}'", names.get(i));
                states[i] = Blocks.AIR.defaultBlockState();
                unknown++;
            }
        }

        // heights are only known for loaded chunks
        for (int cx = origin.getX() >> 4; cx <= (origin.getX() + bp.sizeX - 1) >> 4; cx++) {
            for (int cz = origin.getZ() >> 4; cz <= (origin.getZ() + bp.sizeZ - 1) >> 4; cz++) {
                level.getChunk(cx, cz);
            }
        }
        int bottom = origin.getY() + bp.minY;
        int top = origin.getY() + bp.maxY;
        for (int z = 0; z < bp.sizeZ; z++) {
            for (int x = 0; x < bp.sizeX; x++) {
                top = Math.max(top, level.getHeight(Heightmap.Types.WORLD_SURFACE, origin.getX() + x, origin.getZ() + z) - 1);
            }
        }
        top = Math.min(top, level.getMaxBuildHeight() - 1);
        int sizeY = top - bottom + 1;

        int count = bp.sizeX * sizeY * bp.sizeZ;
        BlockState[] target = new BlockState[count];
        int[] before = new int[count];
        List<BlockState> beforePalette = new ArrayList<>();
        Map<BlockState, Integer> beforeIds = new HashMap<>();
        List<CompoundTag> beforeEntities = new ArrayList<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int y = bottom; y <= top; y++) {
            int by = y - origin.getY();
            for (int z = 0; z < bp.sizeZ; z++) {
                for (int x = 0; x < bp.sizeX; x++, i++) {
                    pos.set(origin.getX() + x, y, origin.getZ() + z);
                    BlockState old = level.getBlockState(pos);
                    BlockState wanted;
                    if (by <= bp.maxY) {
                        int id = bp.paletteIndex(x, by, z);
                        wanted = id < 0 ? null : states[id];
                    } else {
                        wanted = old.isAir() ? null : air;
                    }
                    if (wanted == null) {
                        before[i] = -1;
                        continue;
                    }
                    target[i] = wanted;
                    Integer oldId = beforeIds.get(old);
                    if (oldId == null) {
                        oldId = beforePalette.size();
                        beforePalette.add(old);
                        beforeIds.put(old, oldId);
                    }
                    before[i] = oldId;
                    if (old.hasBlockEntity()) {
                        BlockEntity entity = level.getBlockEntity(pos);
                        if (entity != null) {
                            // its contents come back with undo, so they must not spill out now
                            beforeEntities.add(entity.saveWithFullMetadata());
                            Clearable.tryClear(entity);
                        }
                    }
                }
            }
        }
        ArenaData.get(level).remember(origin, bp.sizeX, sizeY, bp.sizeZ, bp.minY, beforePalette, before, beforeEntities);

        int placed = place(level, origin, bp.sizeX, sizeY, bp.sizeZ, bp.minY, target);

        for (Blueprint.Sign sign : bp.signs()) {
            pos.set(origin.getX() + sign.x(), origin.getY() + sign.y(), origin.getZ() + sign.z());
            if (level.getBlockEntity(pos) instanceof SignBlockEntity entity) {
                SignText text = entity.getFrontText();
                for (int line = 0; line < 4; line++) {
                    text = text.setMessage(line, Component.translatable("sign.sandbazaar." + sign.key() + "." + (line + 1)));
                }
                entity.setText(text, true);
                entity.setText(text, false);
                entity.setWaxed(true);
            }
        }
        return new Result(placed, unknown);
    }

    /**
     * Sets every non-null state of a box. Solid blocks, air and water go first, bottom up; whatever leans on them
     * (doors, lanterns, carpets, signs...) follows with its fence, wall and bar connections worked out, as /setblock
     * does - so nothing is placed before its support.
     */
    private static int place(ServerLevel level, BlockPos origin, int sizeX, int sizeY, int sizeZ, int minY, BlockState[] target) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int pass = 0; pass < 2; pass++) {
            int i = 0;
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    for (int x = 0; x < sizeX; x++, i++) {
                        BlockState state = target[i];
                        if (state == null) {
                            continue;
                        }
                        pos.set(origin.getX() + x, origin.getY() + minY + y, origin.getZ() + z);
                        boolean plain = state.isAir() || !state.getFluidState().isEmpty() || state.isCollisionShapeFullBlock(level, pos);
                        if (plain != (pass == 0)) {
                            continue;
                        }
                        if (!plain) {
                            BlockState shaped = Block.updateFromNeighbourShapes(state, level, pos);
                            if (!shaped.isAir()) {
                                state = shaped;
                            }
                        }
                        if (level.getBlockState(pos) != state && level.setBlock(pos, state, FLAGS)) {
                            placed++;
                        }
                    }
                }
            }
        }
        return placed;
    }

    /**
     * Puts back the land the arena of this dimension replaced, block for block. Returns the blocks restored, or -1
     * when there is no arena.
     */
    public static int undo(ServerLevel level) {
        ArenaData data = ArenaData.get(level);
        BlockPos origin = data.origin();
        if (origin == null) {
            return -1;
        }
        int sizeX = data.sizeX();
        int sizeY = data.sizeY();
        int sizeZ = data.sizeZ();
        int bottom = origin.getY() + data.minY();
        int[] cells = data.cells();
        // every block goes back exactly as it was found, without asking its neighbours: otherwise a vine or a torch
        // put back before the block it hangs on would fall off again
        int quiet = FLAGS | Block.UPDATE_KNOWN_SHAPE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int restored = 0;
        int i = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++, i++) {
                    if (cells[i] < 0) {
                        continue;
                    }
                    BlockState state = data.palette().get(cells[i]);
                    pos.set(origin.getX() + x, bottom + y, origin.getZ() + z);
                    if (level.getBlockState(pos) != state && level.setBlock(pos, state, quiet)) {
                        restored++;
                    }
                }
            }
        }
        // sand over a cave or a waterfall caught mid-air stood still before; being put back must not set them off
        BoundingBox box = new BoundingBox(origin.getX(), bottom, origin.getZ(),
                origin.getX() + sizeX - 1, bottom + sizeY - 1, origin.getZ() + sizeZ - 1);
        level.getBlockTicks().clearArea(box);
        level.getFluidTicks().clearArea(box);

        for (CompoundTag tag : data.blockEntities()) {
            BlockPos at = BlockEntity.getPosFromTag(tag);
            BlockEntity entity = level.getBlockEntity(at);
            if (entity != null) {
                entity.load(tag);
                entity.setChanged();
                level.getChunkSource().blockChanged(at);
            }
        }

        // fences, walls and the like just outside the box get to see their old neighbours again
        BlockPos.MutableBlockPos outside = new BlockPos.MutableBlockPos();
        i = 0;
        for (int y = 0; y < sizeY; y++) {
            for (int z = 0; z < sizeZ; z++) {
                for (int x = 0; x < sizeX; x++, i++) {
                    if (cells[i] < 0) {
                        continue;
                    }
                    for (Direction direction : Direction.values()) {
                        int nx = x + direction.getStepX();
                        int ny = y + direction.getStepY();
                        int nz = z + direction.getStepZ();
                        if (nx >= 0 && nx < sizeX && ny >= 0 && ny < sizeY && nz >= 0 && nz < sizeZ) {
                            continue;
                        }
                        pos.set(origin.getX() + x, bottom + y, origin.getZ() + z);
                        outside.setWithOffset(pos, direction);
                        level.neighborShapeChanged(direction.getOpposite(), level.getBlockState(pos), outside, pos, FLAGS, 512);
                    }
                }
            }
        }
        data.forget();
        return restored;
    }
}
