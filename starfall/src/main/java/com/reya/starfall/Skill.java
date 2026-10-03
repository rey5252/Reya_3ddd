package com.reya.starfall;

import net.minecraft.network.chat.Component;

/** The weapons the remote is wired to. Timings are in ticks from the moment the button is pressed. */
public enum Skill {
    RAILGUN("ss01", "railgun", 0xFF2A2A, 360),
    GUNGNIR("ss03", "gungnir", 0xFF8A2A, 400),
    SEVEN_STARS("ss04", "seven_stars", 0xB070FF, 520);

    /** The film plays while the target is marked (twelve seconds); the weapon lands when it ends. */
    public static final int MARK = 240;
    /** Gungnir's needle takes this long to come down after the film. */
    public static final int NEEDLE_FALL = 8;
    public static final int GUNGNIR_IMPACT = MARK + NEEDLE_FALL;
    /** The shock ring takes this long to reach the crater's rim. */
    public static final int SHOCK_TIME = 40;
    /** Seven Stars: a star needs this long to come down, and they land this far apart. */
    public static final int STAR_FALL = 16;
    public static final int STAR_GAP = 20;
    public static final int FIRST_STAR = MARK + 10;
    /** Seven Stars: the lines ignite one after another, then the whole figure flares. */
    public static final int IGNITE = MARK + 160;
    public static final int LINE_GAP = 8;
    public static final int LINE_BURN = 16;
    public static final int FLARE = MARK + 232;

    public final String code;
    public final String id;
    public final int color;
    public final int duration;

    Skill(String code, String id, int color, int duration) {
        this.code = code;
        this.id = id;
        this.color = color;
        this.duration = duration;
    }

    public static Skill byIndex(int index) {
        Skill[] all = values();
        return all[Math.floorMod(index, all.length)];
    }

    public Skill next() {
        return byIndex(ordinal() + 1);
    }

    public Component title() {
        return Component.translatable("skill.starfall." + id);
    }

    public Component tag() {
        return Component.translatable("skill.starfall." + id + ".code");
    }

    public static int starImpact(int star) {
        return FIRST_STAR + star * STAR_GAP;
    }

    public static int lineStart(int line) {
        return IGNITE + line * LINE_GAP;
    }
}
