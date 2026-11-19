package com.reya.sandbazaar;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Where the arena of a dimension stands, and the land it replaced - kept with the world, so the arena can still be
 * removed after a restart.
 */
public final class ArenaData extends SavedData {
    private static final String NAME = "sandbazaar_arena";

    /** World position of the blueprint's (0, 0, 0), or null while no arena stands. */
    @Nullable
    private BlockPos origin;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private int minY;
    private final List<BlockState> palette = new ArrayList<>();
    /** What stood in each cell of the box as an index into the palette; -1 for cells the arena left alone. */
    private int[] cells = new int[0];
    private final List<CompoundTag> blockEntities = new ArrayList<>();

    public static ArenaData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(tag -> load(tag, level.holderLookup(Registries.BLOCK)), ArenaData::new, NAME);
    }

    public boolean exists() {
        return origin != null;
    }

    @Nullable
    public BlockPos origin() {
        return origin;
    }

    public int sizeX() {
        return sizeX;
    }

    public int sizeY() {
        return sizeY;
    }

    public int sizeZ() {
        return sizeZ;
    }

    public int minY() {
        return minY;
    }

    public List<BlockState> palette() {
        return palette;
    }

    public int[] cells() {
        return cells;
    }

    public List<CompoundTag> blockEntities() {
        return blockEntities;
    }

    public void remember(BlockPos origin, int sizeX, int sizeY, int sizeZ, int minY, List<BlockState> palette, int[] cells,
            List<CompoundTag> blockEntities) {
        this.origin = origin.immutable();
        this.sizeX = sizeX;
        this.sizeY = sizeY;
        this.sizeZ = sizeZ;
        this.minY = minY;
        this.palette.clear();
        this.palette.addAll(palette);
        this.cells = cells;
        this.blockEntities.clear();
        this.blockEntities.addAll(blockEntities);
        setDirty();
    }

    public void forget() {
        origin = null;
        palette.clear();
        cells = new int[0];
        blockEntities.clear();
        setDirty();
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        if (origin == null) {
            return tag;
        }
        tag.put("Origin", NbtUtils.writeBlockPos(origin));
        tag.putInt("SizeX", sizeX);
        tag.putInt("SizeY", sizeY);
        tag.putInt("SizeZ", sizeZ);
        tag.putInt("MinY", minY);
        ListTag states = new ListTag();
        for (BlockState state : palette) {
            states.add(NbtUtils.writeBlockState(state));
        }
        tag.put("Palette", states);
        tag.putIntArray("Cells", cells);
        ListTag entities = new ListTag();
        entities.addAll(blockEntities);
        tag.put("BlockEntities", entities);
        return tag;
    }

    private static ArenaData load(CompoundTag tag, HolderGetter<Block> blocks) {
        ArenaData data = new ArenaData();
        if (!tag.contains("Origin", Tag.TAG_COMPOUND)) {
            return data;
        }
        data.origin = NbtUtils.readBlockPos(tag.getCompound("Origin"));
        data.sizeX = tag.getInt("SizeX");
        data.sizeY = tag.getInt("SizeY");
        data.sizeZ = tag.getInt("SizeZ");
        data.minY = tag.getInt("MinY");
        ListTag states = tag.getList("Palette", Tag.TAG_COMPOUND);
        for (int i = 0; i < states.size(); i++) {
            data.palette.add(NbtUtils.readBlockState(blocks, states.getCompound(i)));
        }
        data.cells = tag.getIntArray("Cells");
        ListTag entities = tag.getList("BlockEntities", Tag.TAG_COMPOUND);
        for (int i = 0; i < entities.size(); i++) {
            data.blockEntities.add(entities.getCompound(i));
        }
        return data;
    }
}
