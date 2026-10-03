package com.reya.starfall.carve;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/** SS-04: a constellation line burned into the land as a trench with a floor of magma and fire. */
public final class TrenchShape extends CarveShape {
    private final double ax, az, bx, bz;
    private final double halfWidth;
    private final int depth;
    /** The craters at either end already cover this much of the line. */
    private final double skipA, skipB;

    public TrenchShape(double ax, double az, double bx, double bz, double halfWidth, int depth, double skipA, double skipB) {
        this.ax = ax;
        this.az = az;
        this.bx = bx;
        this.bz = bz;
        this.halfWidth = halfWidth;
        this.depth = depth;
        this.skipA = skipA;
        this.skipB = skipB;
    }

    /** Where along the line (0 at A, 1 at B) the nearest point to (x, z) is. */
    private double along(double x, double z) {
        double vx = bx - ax, vz = bz - az;
        double len2 = vx * vx + vz * vz;
        if (len2 < 1.0E-6D) return 0.0D;
        return clamp(((x - ax) * vx + (z - az) * vz) / len2, 0.0D, 1.0D);
    }

    private double distance(double x, double z) {
        double t = along(x, z);
        double px = ax + (bx - ax) * t - x, pz = az + (bz - az) * t - z;
        return Math.sqrt(px * px + pz * pz);
    }

    @Override
    public LongArrayList chunks() {
        LongArrayList out = new LongArrayList();
        double reach = halfWidth + 12.0D;
        int minX = (int) Math.floor((Math.min(ax, bx) - reach) / 16.0D), maxX = (int) Math.floor((Math.max(ax, bx) + reach) / 16.0D);
        int minZ = (int) Math.floor((Math.min(az, bz) - reach) / 16.0D), maxZ = (int) Math.floor((Math.max(az, bz) + reach) / 16.0D);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (distance(x * 16.0D + 8.0D, z * 16.0D + 8.0D) <= reach) out.add(ChunkPos.asLong(x, z));
            }
        }
        // burns from A towards B
        out.sort((long a, long b) -> Double.compare(along(ChunkPos.getX(a) * 16.0D + 8.0D, ChunkPos.getZ(a) * 16.0D + 8.0D),
                along(ChunkPos.getX(b) * 16.0D + 8.0D, ChunkPos.getZ(b) * 16.0D + 8.0D)));
        return out;
    }

    @Override
    public void carveColumn(ServerLevel level, LevelChunk chunk, int x, int z, BlockPos.MutableBlockPos pos) {
        double px = x + 0.5D, pz = z + 0.5D;
        double d = distance(px, pz);
        if (d > halfWidth) return;
        double da = Math.hypot(px - ax, pz - az), db = Math.hypot(px - bx, pz - bz);
        if (da < skipA * 0.8D || db < skipB * 0.8D) return;
        int ground = ground(chunk, x, z, pos);
        if (ground == Integer.MIN_VALUE) return;
        // lakes and seas put the fire out
        if (!chunk.getBlockState(pos.set(x, ground, z)).getFluidState().isEmpty()) return;
        double u = d / Math.max(0.5D, halfWidth);
        int floor = Math.max(chunk.getMinBuildHeight() + 1, ground - (int) Math.round(depth * (1.0D - 0.5D * u * u)));
        for (int y = top(chunk, x, z); y > floor; y--) {
            clear(level, chunk, pos.set(x, y, z), false);
        }
        boolean fire = Noise.hash(x, z, 91) < 0.4D;
        BlockState bed = fire ? Blocks.NETHERRACK.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
        set(level, chunk, pos.set(x, floor, z), bed);
        if (fire) set(level, chunk, pos.set(x, floor + 1, z), Blocks.FIRE.defaultBlockState());
    }

    @Override
    protected String type() {
        return "trench";
    }

    @Override
    protected void write(CompoundTag tag) {
        tag.putDouble("AX", ax);
        tag.putDouble("AZ", az);
        tag.putDouble("BX", bx);
        tag.putDouble("BZ", bz);
        tag.putDouble("W", halfWidth);
        tag.putInt("Depth", depth);
        tag.putDouble("SkipA", skipA);
        tag.putDouble("SkipB", skipB);
    }

    static TrenchShape read(CompoundTag tag) {
        return new TrenchShape(tag.getDouble("AX"), tag.getDouble("AZ"), tag.getDouble("BX"), tag.getDouble("BZ"),
                tag.getDouble("W"), tag.getInt("Depth"), tag.getDouble("SkipA"), tag.getDouble("SkipB"));
    }
}
