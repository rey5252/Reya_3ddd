package com.reya.starfall.carve;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** The carving still owed to a dimension. Saved with the world, so craters finish after a restart too. */
public final class CarveData extends SavedData {
    private static final String NAME = "starfall_carves";

    final List<CarveJob> jobs = new ArrayList<>();

    public static CarveData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(CarveData::load, CarveData::new, NAME);
    }

    public void add(CarveJob job) {
        if (job.pending.isEmpty()) return;
        jobs.add(job);
        setDirty();
    }

    public int jobCount() {
        return jobs.size();
    }

    public int pendingChunks() {
        int n = 0;
        for (CarveJob job : jobs) n += job.pendingChunks();
        return n;
    }

    public void clear() {
        jobs.clear();
        setDirty();
    }

    private static CarveData load(CompoundTag tag) {
        CarveData data = new CarveData();
        ListTag list = tag.getList("Jobs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CarveJob job = CarveJob.load(list.getCompound(i));
            if (job != null) data.jobs.add(job);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (CarveJob job : jobs) list.add(job.save());
        tag.put("Jobs", list);
        return tag;
    }
}
