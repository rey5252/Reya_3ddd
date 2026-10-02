package com.reya.boundlessrouters.upgrade;

import java.util.Locale;

import com.reya.boundlessrouters.RouterConfig;

/** The upgrades a router takes in its five slots. */
public enum UpgradeKind {
    /** Each takes two ticks off the time between runs, down to every tick. */
    SPEED(0xFFD84A),
    /** Each doubles the items a module moves a run, up to a full stack. */
    STACK(0xE8E8F0),
    /** Range I: modules reach 16 blocks. Range upgrades don't add up: the router's best one counts. */
    RANGE(0x4FE3FF),
    /** Range II: 32 blocks. */
    RANGE_2(0x4F86FF),
    /** Range III: 64 blocks. */
    RANGE_3(0xA66BFF),
    /** Infinite range: any distance, and other dimensions. */
    INFINITE_RANGE(0xFF4FD8),
    /** Silences the router: no sounds, no particles, no lines. */
    MUFFLER(0x9A8F86);

    private final int colour;

    UpgradeKind(int colour) {
        this.colour = colour;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    public int colour() {
        return colour;
    }

    public boolean isRange() {
        return this == RANGE || this == RANGE_2 || this == RANGE_3 || this == INFINITE_RANGE;
    }

    /** How far a router with this range upgrade reaches, in blocks (the infinite one: -1). */
    public int reach() {
        return switch (this) {
            case RANGE -> RouterConfig.RANGE_1.get();
            case RANGE_2 -> RouterConfig.RANGE_2.get();
            case RANGE_3 -> RouterConfig.RANGE_3.get();
            case INFINITE_RANGE -> -1;
            default -> 0;
        };
    }
}
