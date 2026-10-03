package com.reya.starfall.carve;

import com.reya.starfall.NeedleBlock;
import com.reya.starfall.PlateBlock;
import com.reya.starfall.Starfall;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * A bowl pressed into the land. With a fixed floor height (SS-03) everything above it is planed away and
 * shallow dips are filled, so the land comes out flat; without one (SS-04 stars) the bowl follows the ground.
 */
public final class CraterShape extends CarveShape {
    /** Floor follows the ground instead of a fixed height. */
    public static final int RELATIVE = Integer.MIN_VALUE;
    /** How far below the floor dips are filled to level them out. */
    private static final int MAX_FILL = 24;

    public enum Palette { MOLTEN, STARRY }

    private final int cx, cy, cz;
    private final double radius;
    private final int depth;
    private final Palette palette;
    private final boolean needle, core;

    public CraterShape(int cx, int cy, int cz, double radius, int depth, Palette palette, boolean needle, boolean core) {
        this.cx = cx;
        this.cy = cy;
        this.cz = cz;
        this.radius = radius;
        this.depth = depth;
        this.palette = palette;
        this.needle = needle;
        this.core = core;
    }

    @Override
    public LongArrayList chunks() {
        return disc(cx + 0.5D, cz + 0.5D, radius);
    }

    @Override
    public void carveColumn(ServerLevel level, LevelChunk chunk, int x, int z, BlockPos.MutableBlockPos pos) {
        double dx = x - cx, dz = z - cz;
        double d2 = dx * dx + dz * dz;
        if (d2 > radius * radius) return;
        double t = Math.sqrt(d2) / radius;
        int base = cy;
        if (base == RELATIVE) {
            base = ground(chunk, x, z, pos);
            if (base == Integer.MIN_VALUE) return;
        }
        int min = chunk.getMinBuildHeight();
        int floor = Math.max(min + 1, base - (int) Math.round(depth * (1.0D - t * t)));

        for (int y = top(chunk, x, z); y > floor; y--) {
            clear(level, chunk, pos.set(x, y, z), false);
        }
        // the needle stands two by two on the crater's centre, from the bottom of the world to the build limit
        if (needle && (x == cx || x == cx - 1) && (z == cz || z == cz - 1)) {
            int quarter = x == cx ? (z == cz ? 0 : 3) : (z == cz ? 1 : 2);
            BlockState shaft = Starfall.STAR_NEEDLE.get().defaultBlockState().setValue(NeedleBlock.QUARTER, quarter);
            for (int y = min; y < chunk.getMaxBuildHeight(); y++) {
                int part = y > floor && y <= floor + 3 ? NeedleBlock.FOOT
                        : Math.floorMod(y - floor - 4, 6) == 0 ? NeedleBlock.BAND : NeedleBlock.PLAIN;
                set(level, chunk, pos.set(x, y, z), shaft.setValue(NeedleBlock.PART, part));
            }
            return;
        }
        // level out what lies below the floor, as long as it isn't a canyon
        int solid = floor;
        while (solid > floor - MAX_FILL && solid > min) {
            BlockState state = chunk.getBlockState(pos.set(x, solid, z));
            if (!state.isAir() && state.getFluidState().isEmpty()) break;
            solid--;
        }
        if (solid <= floor - MAX_FILL || solid <= min) return;
        for (int y = solid + 1; y < floor; y++) {
            set(level, chunk, pos.set(x, y, z), fill());
        }
        double n = Noise.patch(x, z, palette.ordinal() * 31 + 7);
        BlockState surface = surface(x, z, t, n);
        set(level, chunk, pos.set(x, floor, z), surface);
        BlockState below = chunk.getBlockState(pos.set(x, floor - 1, z));
        if (!below.isAir() && below.getFluidState().isEmpty()) set(level, chunk, pos, under());

        if (core && x == cx && z == cz) {
            set(level, chunk, pos.set(x, floor, z), Blocks.CRYING_OBSIDIAN.defaultBlockState());
            set(level, chunk, pos.set(x, floor + 1, z), Starfall.STAR_CORE.get().defaultBlockState());
        }
    }

    private BlockState surface(int x, int z, double t, double n) {
        if (palette == Palette.MOLTEN) {
            // planed flat into plates with a glowing hex grid, scorched at the rim
            if (t < 0.88D) return ((PlateBlock) Starfall.SCORCHED_PLATE.get()).at(x, z);
            if (t < 0.95D) return n < 0.5D ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
            return n < 0.5D ? Blocks.COARSE_DIRT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        }
        if (t < 0.25D) return n < 0.45D ? Blocks.AMETHYST_BLOCK.defaultBlockState() : n < 0.75D ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
        if (t < 0.6D) return n < 0.2D ? Blocks.CRYING_OBSIDIAN.defaultBlockState() : n < 0.4D ? Blocks.AMETHYST_BLOCK.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
        if (t < 0.85D) return n < 0.5D ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
        return n < 0.4D ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.TUFF.defaultBlockState();
    }

    private BlockState under() {
        return palette == Palette.MOLTEN ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
    }

    private BlockState fill() {
        return palette == Palette.MOLTEN ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.DEEPSLATE.defaultBlockState();
    }

    @Override
    protected String type() {
        return "crater";
    }

    @Override
    protected void write(CompoundTag tag) {
        tag.putInt("X", cx);
        tag.putInt("Y", cy);
        tag.putInt("Z", cz);
        tag.putDouble("R", radius);
        tag.putInt("Depth", depth);
        tag.putString("Palette", palette.name());
        tag.putBoolean("Needle", needle);
        tag.putBoolean("Core", core);
    }

    static CraterShape read(CompoundTag tag) {
        Palette palette;
        try {
            palette = Palette.valueOf(tag.getString("Palette"));
        } catch (IllegalArgumentException e) {
            palette = Palette.MOLTEN;
        }
        return new CraterShape(tag.getInt("X"), tag.getInt("Y"), tag.getInt("Z"), tag.getDouble("R"), tag.getInt("Depth"),
                palette, tag.getBoolean("Needle"), tag.getBoolean("Core"));
    }
}
