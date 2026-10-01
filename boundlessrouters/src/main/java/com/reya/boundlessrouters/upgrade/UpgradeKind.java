package com.reya.boundlessrouters.upgrade;

import java.util.Locale;

/** The upgrades a router takes, as many as its five slots hold: nothing caps their effect but sense. */
public enum UpgradeKind {
    /** Each takes two ticks off the time between runs, down to every tick. */
    SPEED(0xFFD84A),
    /** Each doubles the items a module moves a run, up to a full stack. */
    STACK(0xE8E8F0),
    /** Each adds four blocks to how far senders look and extruders reach. */
    RANGE(0x4FE3FF),
    /** Silences the router: no sounds, no particles. */
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
}
