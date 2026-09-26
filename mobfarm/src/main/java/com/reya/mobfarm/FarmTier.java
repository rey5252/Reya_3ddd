package com.reya.mobfarm;

import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.MapColor;

/** Mob farm tiers, slowest to fastest. {@code ticks} is the time for one round of loot. */
public enum FarmTier {
    WOODEN("wooden", 1200, 2.0F, SoundType.WOOD, MapColor.WOOD, false,
            0xFF8B5A2B, 0xFFC08A50, 0xFF4A2E14),
    STONE("stone", 900, 3.0F, SoundType.STONE, MapColor.STONE, true,
            0xFF7A7A7A, 0xFFB4B4B4, 0xFF3E3E3E),
    IRON("iron", 600, 4.0F, SoundType.METAL, MapColor.METAL, true,
            0xFFC8CCD2, 0xFFF2F4F7, 0xFF6E747C),
    GOLDEN("golden", 400, 4.0F, SoundType.METAL, MapColor.GOLD, true,
            0xFFE0B030, 0xFFFFE27A, 0xFF8A6410),
    DIAMOND("diamond", 240, 5.0F, SoundType.METAL, MapColor.DIAMOND, true,
            0xFF3ED8D0, 0xFFA8FFF8, 0xFF1A7A76),
    NETHERITE("netherite", 100, 6.0F, SoundType.NETHERITE_BLOCK, MapColor.COLOR_BLACK, true,
            0xFF4E444A, 0xFF8A7C84, 0xFF231C20);

    public final String id;
    public final int ticks;
    public final float hardness;
    public final SoundType sound;
    public final MapColor mapColor;
    public final boolean needsPickaxe;
    /** GUI colours: frame, highlight, shadow. */
    public final int color;
    public final int light;
    public final int dark;

    FarmTier(String id, int ticks, float hardness, SoundType sound, MapColor mapColor, boolean needsPickaxe,
             int color, int light, int dark) {
        this.id = id;
        this.ticks = ticks;
        this.hardness = hardness;
        this.sound = sound;
        this.mapColor = mapColor;
        this.needsPickaxe = needsPickaxe;
        this.color = color;
        this.light = light;
        this.dark = dark;
    }

    public static FarmTier byOrdinal(int ordinal) {
        FarmTier[] all = values();
        return all[Math.max(0, Math.min(all.length - 1, ordinal))];
    }
}
