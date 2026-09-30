package com.reya.alfheimheart.greenhouse;

import java.util.Locale;

/** The four greenhouse upgrades; every item in the upgrade slots counts. */
public enum UpgradeKind {
    /** Growth: shorter cycles. */
    SPEED(0x7CF08A),
    /** Reservoir: a bigger mana store. */
    CAPACITY(0x4CC9FF),
    /** Fortune: a chance for a cycle to give more mana than usual. */
    LUCK(0xFFD35C),
    /** Abundance: more mana every cycle. */
    YIELD(0xFF8FC8);

    /** The colour its pips and glow take in the GUI. */
    public final int color;

    UpgradeKind(int color) {
        this.color = color;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
