package com.reya.singularityfusion.block;

import java.util.Locale;

/** What a fusion core is doing, or what it waits for: shown in its screen (gui.singularityfusion.status.*). */
public enum FusionStatus {
    /** It doesn't stand on a 3x3 of void casing. */
    NO_FOUNDATION,
    /** Something stands in the space over it where the singularity forms. */
    BLOCKED,
    /** No graviton pylon round it. */
    NO_PYLONS,
    /** The catalyst and the pylons' items make no recipe. */
    NO_RECIPE,
    /** Its result has no room in the output. */
    OUTPUT_FULL,
    /** It doesn't hold the energy the recipe takes. */
    NO_ENERGY,
    /** Everything is in place: it can start. */
    READY,
    /** A fusion is under way. */
    FUSING;

    private static final FusionStatus[] ALL = values();

    public static FusionStatus byId(int id) {
        return id >= 0 && id < ALL.length ? ALL[id] : NO_FOUNDATION;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Whether the structure is whole (the singularity can form over it, whatever the recipe). */
    public boolean formed() {
        return this != NO_FOUNDATION && this != BLOCKED && this != NO_PYLONS;
    }
}
