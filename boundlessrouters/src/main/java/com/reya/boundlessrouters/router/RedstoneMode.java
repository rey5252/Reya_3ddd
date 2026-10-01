package com.reya.boundlessrouters.router;

import java.util.Locale;

/** How a router heeds redstone: it runs always, only with a signal, only without one, never, or once a pulse. */
public enum RedstoneMode {
    ALWAYS, HIGH, LOW, NEVER, PULSE;

    private static final RedstoneMode[] ALL = values();

    public RedstoneMode next(boolean back) {
        return ALL[Math.floorMod(ordinal() + (back ? -1 : 1), ALL.length)];
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static RedstoneMode byId(int id) {
        return id >= 0 && id < ALL.length ? ALL[id] : ALWAYS;
    }
}
