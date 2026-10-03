package com.reya.starfall.carve;

import java.util.Iterator;

import com.reya.starfall.Config;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Spends a few milliseconds of each server tick reshaping the land, oldest job first. Only chunks that are
 * already loaded are touched (nothing is generated just to be destroyed); the rest wait in the job until a
 * player comes close enough to load them.
 */
public final class CarveEngine {
    public static void tick(ServerLevel level) {
        CarveData data = CarveData.get(level);
        if (data.jobs.isEmpty()) return;
        long deadline = System.nanoTime() + Config.CARVE_MS.get() * 1_000_000L;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        boolean changed = false;
        outer:
        for (Iterator<CarveJob> it = data.jobs.iterator(); it.hasNext(); ) {
            CarveJob job = it.next();
            int i = 0;
            while (i < job.pending.size()) {
                long key = job.pending.getLong(i);
                LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
                if (chunk == null) {
                    i++;
                    continue;
                }
                changed = true;
                int column = job.progress.get(key);
                while (column < 256) {
                    job.carve(level, chunk, column, pos);
                    column++;
                    if (System.nanoTime() > deadline) break;
                }
                if (column < 256) {
                    job.progress.put(key, column);
                    break outer;
                }
                job.progress.remove(key);
                job.pending.removeLong(i);
                if (System.nanoTime() > deadline) break;
            }
            if (job.pending.isEmpty()) it.remove();
            if (System.nanoTime() > deadline) break;
        }
        if (changed) data.setDirty();
    }

    private CarveEngine() {
    }
}
