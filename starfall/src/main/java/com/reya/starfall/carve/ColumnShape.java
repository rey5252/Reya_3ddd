package com.reya.starfall.carve;

import com.reya.starfall.MoltenRockBlock;
import com.reya.starfall.Starfall;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * SS-01: the whole column of the world inside the beam is cut away, from the build limit to the void. Just
 * outside it the rock isn't erased but melted, so the shaft is lined with a wall that glows white-hot at the
 * lip and darker the deeper it goes.
 */
public final class ColumnShape extends CarveShape {
    /** How thick the melted wall is, in blocks. */
    private static final double WALL = 1.6D;

    private final double cx, cz, radius;
    private final boolean bedrock;

    public ColumnShape(double cx, double cz, double radius, boolean bedrock) {
        this.cx = cx;
        this.cz = cz;
        this.radius = radius;
        this.bedrock = bedrock;
    }

    @Override
    public LongArrayList chunks() {
        return disc(cx, cz, radius + WALL + 1.0D);
    }

    @Override
    public void carveColumn(ServerLevel level, LevelChunk chunk, int x, int z, BlockPos.MutableBlockPos pos) {
        double dx = x + 0.5D - cx, dz = z + 0.5D - cz;
        double d2 = dx * dx + dz * dz;
        if (d2 > (radius + WALL) * (radius + WALL)) return;
        int min = chunk.getMinBuildHeight();
        if (d2 > radius * radius) {
            wall(level, chunk, x, z, pos, min);
            return;
        }
        // top down, so the heightmap only ever steps one block at a time
        for (int y = top(chunk, x, z); y >= min; y--) {
            clear(level, chunk, pos.set(x, y, z), bedrock);
        }
    }

    /** Melts the solid blocks of a column just outside the beam, hottest at the ground and cooling with depth. */
    private void wall(ServerLevel level, LevelChunk chunk, int x, int z, BlockPos.MutableBlockPos pos, int min) {
        int lip = ground(chunk, x, z, pos);
        if (lip == Integer.MIN_VALUE) return;
        MoltenRockBlock rock = (MoltenRockBlock) Starfall.MOLTEN_ROCK.get();
        for (int y = lip; y >= min; y--) {
            BlockState state = chunk.getBlockState(pos.set(x, y, z));
            if (state.isAir() || state.canBeReplaced() || !state.getFluidState().isEmpty()
                    || state.is(Blocks.BEDROCK) || state.is(Starfall.STAR_NEEDLE.get())) continue;
            set(level, chunk, pos, rock.at(lip - y));
        }
    }

    @Override
    protected String type() {
        return "column";
    }

    @Override
    protected void write(CompoundTag tag) {
        tag.putDouble("X", cx);
        tag.putDouble("Z", cz);
        tag.putDouble("R", radius);
        tag.putBoolean("Bedrock", bedrock);
    }

    static ColumnShape read(CompoundTag tag) {
        return new ColumnShape(tag.getDouble("X"), tag.getDouble("Z"), tag.getDouble("R"), tag.getBoolean("Bedrock"));
    }
}
