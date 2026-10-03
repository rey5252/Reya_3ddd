package com.reya.starfall.carve;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/** One shape being carved: the chunks still to do, and how far into a chunk the work got. */
public final class CarveJob {
    final CarveShape shape;
    final LongArrayList pending;
    final Long2IntOpenHashMap progress = new Long2IntOpenHashMap();
    /** Circles (x, z, radius) where a creative player stood: the ground under their feet is spared. */
    final List<double[]> spared = new ArrayList<>();

    public CarveJob(CarveShape shape) {
        this(shape, shape.chunks());
    }

    private CarveJob(CarveShape shape, LongArrayList pending) {
        this.shape = shape;
        this.pending = pending;
    }

    public void spare(double x, double z, double radius) {
        spared.add(new double[]{x, z, radius});
    }

    public int pendingChunks() {
        return pending.size();
    }

    /** Carves column {@code index} (0..255) of the chunk. */
    void carve(ServerLevel level, LevelChunk chunk, int index, BlockPos.MutableBlockPos pos) {
        ChunkPos cp = chunk.getPos();
        int x = cp.getMinBlockX() + (index & 15);
        int z = cp.getMinBlockZ() + (index >> 4);
        for (double[] c : spared) {
            double dx = x + 0.5D - c[0], dz = z + 0.5D - c[1];
            if (dx * dx + dz * dz <= c[2] * c[2]) return;
        }
        shape.carveColumn(level, chunk, x, z, pos);
    }

    CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Shape", shape.save());
        tag.putLongArray("Pending", pending.toLongArray());
        long[] keys = progress.keySet().toLongArray();
        int[] values = new int[keys.length];
        for (int i = 0; i < keys.length; i++) values[i] = progress.get(keys[i]);
        tag.putLongArray("ProgressChunks", keys);
        tag.putIntArray("ProgressColumns", values);
        ListTag circles = new ListTag();
        for (double[] c : spared) {
            CompoundTag ct = new CompoundTag();
            ct.putDouble("X", c[0]);
            ct.putDouble("Z", c[1]);
            ct.putDouble("R", c[2]);
            circles.add(ct);
        }
        tag.put("Spared", circles);
        return tag;
    }

    static CarveJob load(CompoundTag tag) {
        CarveShape shape = CarveShape.load(tag.getCompound("Shape"));
        if (shape == null) return null;
        CarveJob job = new CarveJob(shape, new LongArrayList(tag.getLongArray("Pending")));
        long[] keys = tag.getLongArray("ProgressChunks");
        int[] values = tag.getIntArray("ProgressColumns");
        for (int i = 0; i < Math.min(keys.length, values.length); i++) job.progress.put(keys[i], values[i]);
        ListTag circles = tag.getList("Spared", Tag.TAG_COMPOUND);
        for (int i = 0; i < circles.size(); i++) {
            CompoundTag ct = circles.getCompound(i);
            job.spare(ct.getDouble("X"), ct.getDouble("Z"), ct.getDouble("R"));
        }
        return job;
    }
}
