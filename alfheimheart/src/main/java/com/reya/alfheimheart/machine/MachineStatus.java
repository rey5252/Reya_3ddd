package com.reya.alfheimheart.machine;

/** What a machine is doing, as its GUI shows it (the colour of its gem, its status line). */
public enum MachineStatus {
    /** Charging a craft with mana. */
    WORKING,
    /** Nothing in it makes a recipe. */
    IDLE,
    /** A craft waits for mana. */
    NO_MANA,
    /** A craft waits for room in the output slots. */
    OUTPUT_FULL,
    /** Stopped by its redstone setting. */
    REDSTONE
}
