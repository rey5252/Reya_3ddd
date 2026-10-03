package com.reya.starfall.carve;

import com.reya.starfall.Starfall;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Container;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * A piece of land a weapon reshapes. The work is done one column at a time, chunk by chunk, so a crater
 * hundreds of blocks wide is spread over many ticks, and chunks that aren't loaded yet are done when they are.
 * Carving a column again gives the same result, so an interrupted chunk is simply done over.
 */
public abstract class CarveShape {
    /** Send to clients, no neighbour shape updates, no drops: the land is erased, not mined. */
    protected static final int FLAGS = 2 | 16 | 32;
    protected static final BlockState AIR = Blocks.AIR.defaultBlockState();

    /** Chunks the shape touches, in the order they should be carved. */
    public abstract LongArrayList chunks();

    /** Reshapes one column; the column is known to be inside the chunk. */
    public abstract void carveColumn(ServerLevel level, LevelChunk chunk, int x, int z, BlockPos.MutableBlockPos pos);

    protected abstract String type();

    protected abstract void write(CompoundTag tag);

    public final CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Type", type());
        write(tag);
        return tag;
    }

    public static CarveShape load(CompoundTag tag) {
        return switch (tag.getString("Type")) {
            case "column" -> ColumnShape.read(tag);
            case "crater" -> CraterShape.read(tag);
            case "trench" -> TrenchShape.read(tag);
            default -> null;
        };
    }

    /** Chunks within {@code radius} of a point, nearest first. */
    protected static LongArrayList disc(double cx, double cz, double radius) {
        LongArrayList out = new LongArrayList();
        int minX = (int) Math.floor((cx - radius) / 16.0D), maxX = (int) Math.floor((cx + radius) / 16.0D);
        int minZ = (int) Math.floor((cz - radius) / 16.0D), maxZ = (int) Math.floor((cz + radius) / 16.0D);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                double nx = clamp(cx, x * 16.0D, x * 16.0D + 16.0D);
                double nz = clamp(cz, z * 16.0D, z * 16.0D + 16.0D);
                if ((nx - cx) * (nx - cx) + (nz - cz) * (nz - cz) <= radius * radius) out.add(ChunkPos.asLong(x, z));
            }
        }
        out.sort((long a, long b) -> Double.compare(dist2(a, cx, cz), dist2(b, cx, cz)));
        return out;
    }

    private static double dist2(long chunk, double cx, double cz) {
        double x = ChunkPos.getX(chunk) * 16.0D + 8.0D - cx;
        double z = ChunkPos.getZ(chunk) * 16.0D + 8.0D - cz;
        return x * x + z * z;
    }

    protected static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    /** Highest block in the column, or the bottom of the world if it's empty. */
    protected static int top(LevelChunk chunk, int x, int z) {
        return Math.min(chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) + 1, chunk.getMaxBuildHeight() - 1);
    }

    /**
     * The real ground of a column: the highest block that isn't air, a plant, a log or leaves.
     * Returns {@code Integer.MIN_VALUE} if the column has none.
     */
    protected static int ground(LevelChunk chunk, int x, int z, BlockPos.MutableBlockPos pos) {
        int min = chunk.getMinBuildHeight();
        for (int y = top(chunk, x, z); y >= min; y--) {
            BlockState state = chunk.getBlockState(pos.set(x, y, z));
            if (state.isAir() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.canBeReplaced()
                    || state.is(BlockTags.SNOW) && !state.is(Blocks.SNOW_BLOCK)) {
                if (state.getFluidState().isEmpty()) continue;
            }
            return y;
        }
        return Integer.MIN_VALUE;
    }

    /** Erases one block. The needle is never erased, and bedrock only when allowed. */
    protected static void clear(ServerLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos, boolean bedrock) {
        BlockState state = chunk.getBlockState(pos);
        if (state.isAir() || state.is(Starfall.STAR_NEEDLE.get())) return;
        if (!bedrock && state.is(Blocks.BEDROCK)) return;
        set(level, chunk, pos, AIR);
    }

    /** Places a block, emptying any container first so the land doesn't spill thousands of items. */
    protected static void set(ServerLevel level, LevelChunk chunk, BlockPos.MutableBlockPos pos, BlockState state) {
        BlockState old = chunk.getBlockState(pos);
        if (old == state || old.is(Starfall.STAR_NEEDLE.get()) && !state.is(Starfall.STAR_NEEDLE.get())) return;
        if (old.hasBlockEntity()) {
            BlockEntity be = chunk.getBlockEntity(pos);
            if (be instanceof Container container) container.clearContent();
        }
        level.setBlock(pos, state, FLAGS);
    }
}
