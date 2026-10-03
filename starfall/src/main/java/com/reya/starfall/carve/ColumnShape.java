package com.reya.starfall.carve;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;

/** SS-01: the whole column of the world inside the beam is cut away, from the build limit to the void. */
public final class ColumnShape extends CarveShape {
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
        return disc(cx, cz, radius);
    }

    @Override
    public void carveColumn(ServerLevel level, LevelChunk chunk, int x, int z, BlockPos.MutableBlockPos pos) {
        double dx = x + 0.5D - cx, dz = z + 0.5D - cz;
        if (dx * dx + dz * dz > radius * radius) return;
        int min = chunk.getMinBuildHeight();
        // top down, so the heightmap only ever steps one block at a time
        for (int y = top(chunk, x, z); y >= min; y--) {
            clear(level, chunk, pos.set(x, y, z), bedrock);
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
