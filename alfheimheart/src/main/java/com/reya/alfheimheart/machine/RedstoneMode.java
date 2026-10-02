package com.reya.alfheimheart.machine;

/** A machine's redstone setting, cycled by the button in its GUI. */
public enum RedstoneMode {
    /** Works whatever the redstone. */
    IGNORE,
    /** Works only while powered. */
    HIGH,
    /** Works only while not powered. */
    LOW;

    public boolean allows(boolean powered) {
        return this == IGNORE || (this == HIGH) == powered;
    }

    public RedstoneMode next() {
        return values()[(ordinal() + 1) % values().length];
    }

    public static RedstoneMode byId(int id) {
        return id >= 0 && id < values().length ? values()[id] : IGNORE;
    }
}
