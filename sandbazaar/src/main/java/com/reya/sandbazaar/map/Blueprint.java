package com.reya.sandbazaar.map;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A box of block-state strings ("sandstone_stairs[facing=east]"). It knows nothing about Minecraft classes, so the
 * layout can be generated, rendered and checked without the game.
 */
public final class Blueprint {
    public static final String AIR = "air";

    /** A sign whose four lines are the translations of {@code sign.sandbazaar.<key>.1..4}. */
    public record Sign(int x, int y, int z, String key) {}

    /** A named place to teleport to. */
    public record Mark(int x, int y, int z, float yaw) {}

    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    public final int minY;
    public final int maxY;

    /** 0 leaves the world alone, anything else is a palette index plus one. */
    private final short[] cells;
    private final List<String> palette = new ArrayList<>();
    private final Map<String, Short> ids = new HashMap<>();
    private final List<Sign> signs = new ArrayList<>();

    public Blueprint(int sizeX, int sizeZ, int minY, int maxY) {
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.minY = minY;
        this.maxY = maxY;
        this.sizeY = maxY - minY + 1;
        this.cells = new short[sizeX * sizeY * sizeZ];
    }

    public boolean contains(int x, int y, int z) {
        return x >= 0 && x < sizeX && z >= 0 && z < sizeZ && y >= minY && y <= maxY;
    }

    private int index(int x, int y, int z) {
        return ((y - minY) * sizeZ + z) * sizeX + x;
    }

    /** Sets a block; positions outside the box are ignored. */
    public void set(int x, int y, int z, String state) {
        if (!contains(x, y, z)) {
            return;
        }
        Short id = ids.get(state);
        if (id == null) {
            palette.add(state);
            id = (short) palette.size();
            ids.put(state, id);
        }
        cells[index(x, y, z)] = id;
    }

    /** The block at a position, or null when the position is outside the box or left untouched. */
    public String get(int x, int y, int z) {
        if (!contains(x, y, z)) {
            return null;
        }
        short id = cells[index(x, y, z)];
        return id == 0 ? null : palette.get(id - 1);
    }

    public boolean isAir(int x, int y, int z) {
        return AIR.equals(get(x, y, z));
    }

    /** Palette index of a position, or -1 when it is left untouched. */
    public int paletteIndex(int x, int y, int z) {
        return cells[index(x, y, z)] - 1;
    }

    public List<String> palette() {
        return Collections.unmodifiableList(palette);
    }

    public void addSign(int x, int y, int z, String key) {
        signs.add(new Sign(x, y, z, key));
    }

    public List<Sign> signs() {
        return Collections.unmodifiableList(signs);
    }
}
