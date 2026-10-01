package com.reya.boundlessrouters.module;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/** A place a module is bound to: a block in a dimension, and the face of it the module works through. */
public record Target(ResourceKey<Level> dim, BlockPos pos, Direction face) {
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dim", dim.location().toString());
        tag.putLong("Pos", pos.asLong());
        tag.putByte("Face", (byte) face.get3DDataValue());
        return tag;
    }

    @Nullable
    public static Target load(CompoundTag tag) {
        if (!tag.contains("Dim", Tag.TAG_STRING) || !tag.contains("Pos", Tag.TAG_LONG)) return null;
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Dim"));
        if (id == null) return null;
        return new Target(ResourceKey.create(Registries.DIMENSION, id), BlockPos.of(tag.getLong("Pos")),
                Direction.from3DDataValue(tag.getByte("Face")));
    }

    public boolean samePlace(Target other) {
        return dim.equals(other.dim) && pos.equals(other.pos);
    }

    /** "12, 64, -30" */
    public String coords() {
        return pos.getX() + ", " + pos.getY() + ", " + pos.getZ();
    }

    /** "the_nether", or "overworld" */
    public String dimName() {
        return dim.location().getPath();
    }
}
