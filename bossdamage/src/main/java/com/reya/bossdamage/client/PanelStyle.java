package com.reya.bossdamage.client;

/**
 * Looks for the mob / boss panel. Switch in game with the "Change panel style" key (K by default)
 * or in config/bossdamage-client.toml.
 */
public enum PanelStyle {
    NIGHT_SKY(0xB0101030, 0xB0331C55, 0xF0E4E4FA, 0x50FFFFFF, 0xFFBFFFF0,
            Frame.SNOWFLAKES, Scenery.SKYLINE, 0x70C2489A, 0x40E070C0, 0xB0FFE08A, Particles.STARS, 0xFFFFFFFF,
            0xFFFFFFFF, 0xFFD9D4F2, 0x90200A24, 0xFFB0103A, 0xFFFF5C8A, 0xFFFFD6E6,
            0xFFFFD27A, 0x60E4E4FA, 0xB0DCDCF5, 0x60080820, 0xFF7CFFB0, 0xFFEDEAFF),

    ROYAL_GOLD(0xC0120E24, 0xC0241A3E, 0xFFD9A93A, 0x60F3D27A, 0xFF7FE8F0,
            Frame.GEMS, Scenery.NONE, 0, 0, 0, Particles.SPARKLES, 0xFFF3D27A,
            0xFFFFF4D6, 0xFFF3E6C0, 0x90180808, 0xFF8C1A1A, 0xFFE03A3A, 0xFFFFF0C0,
            0xFFF0C040, 0x80D9A93A, 0xFFD9A93A, 0x80000010, 0xFF7CFC7C, 0xFFF3E6C0),

    INFERNO(0xB0200604, 0xB0501008, 0xF0FF8A2A, 0x50FFC060, 0xFFFFE070,
            Frame.EMBERS, Scenery.FLAMES, 0x90FF5A10, 0x60FFB020, 0, Particles.EMBERS, 0xFFFFA030,
            0xFFFFF0E0, 0xFFFFD0B0, 0x90200400, 0xFF7A0A00, 0xFFFFB020, 0xFFFFE0A0,
            0xFFFFB040, 0x70FF8A2A, 0xC0FF8A2A, 0x70100000, 0xFFFFF070, 0xFFFFE0D0),

    FROST(0xB00A2438, 0xB0184A66, 0xF0CFF6FF, 0x50FFFFFF, 0xFFFFFFFF,
            Frame.SNOWFLAKES, Scenery.HILLS, 0xC0E8F6FF, 0x60A8D8F0, 0, Particles.SNOW, 0xFFFFFFFF,
            0xFFFFFFFF, 0xFFD4F4FF, 0x90062030, 0xFF1060B0, 0xFF70E4FF, 0xFFE8FBFF,
            0xFFBFF4FF, 0x60CFF6FF, 0xC0CFF6FF, 0x60021018, 0xFF9CFFD0, 0xFFE8FBFF),

    SAKURA(0xB03A1030, 0xB0602048, 0xF0FFD0E6, 0x50FFFFFF, 0xFFFFF27A,
            Frame.FLOWERS, Scenery.HILLS, 0x80C0508A, 0x50E090C0, 0, Particles.PETALS, 0xFFFFB0D0,
            0xFFFFFFFF, 0xFFFFE0F0, 0x90300818, 0xFFC02060, 0xFFFF90C0, 0xFFFFF0F6,
            0xFFFFE0F0, 0x60FFD0E6, 0xC0FFD0E6, 0x60200010, 0xFFB0FFB0, 0xFFFFF0F6),

    FOREST(0xB0081A10, 0xB0183A20, 0xF0A8E6A0, 0x40FFFFFF, 0xFFFFE070,
            Frame.FLOWERS, Scenery.GRASS, 0x90307A30, 0x6050A050, 0, Particles.FIREFLIES, 0xFFE8FF70,
            0xFFF4FFF0, 0xFFD8F0D0, 0x90061206, 0xFF1A7A20, 0xFF70E060, 0xFFE8FFD8,
            0xFFE8FF90, 0x60A8E6A0, 0xC0A8E6A0, 0x60020A02, 0xFFFFF070, 0xFFE8FFE0),

    /** Violet galaxy with a glowing ornate frame (like the purple "custom ESC" menu). */
    COSMIC(0xC00C0826, 0xC0241046, 0xF0A070FF, 0x606040C0, 0xFF60E0FF,
            Frame.ORNATE, Scenery.NEBULA, 0x305020A0, 0x302060C0, 0, Particles.STARS, 0xFFFFFFFF,
            0xFFFFFFFF, 0xFFD8CCFF, 0x90100828, 0xFF5A1AB0, 0xFFFF5ACD, 0xFFF0D8FF,
            0xFFC8A8FF, 0x70A070FF, 0xE0A070FF, 0x80060318, 0xFF7CFFE0, 0xFFE8E0FF),

    /** Teal sea with rolling waves, rising bubbles and a foamy frame. */
    OCEAN(0xB0083A4A, 0xB0105E6E, 0xF0E8FFFF, 0x6080F0F0, 0xFFB8FFF4,
            Frame.FOAM, Scenery.WAVES, 0x9020B8B0, 0x6040D8D0, 0, Particles.BUBBLES, 0xFFD8FFFF,
            0xFFFFFFFF, 0xFFD8FFFA, 0x90022028, 0xFF0A7A8C, 0xFF40F0D0, 0xFFE0FFFA,
            0xFFFFE070, 0x60E8FFFF, 0xE0E8FFFF, 0x60021820, 0xFFFFF070, 0xFFE8FFFC),

    /** Treasure-map parchment inside a wooden frame (like the pirate chest GUI). */
    PIRATE(0xF0EAD6A8, 0xF0D8BA84, 0xFF6A3E1E, 0x60FFF0C8, 0xFF2A4A5E,
            Frame.WOOD, Scenery.STAINS, 0x30804010, 0x20A06020, 0, Particles.NONE, 0,
            0xFF4A2A10, 0xFF6A4020, 0xA0503018, 0xFF8C1A10, 0xFFD04020, 0xFFFFF0C0,
            0xFF7A3A10, 0x806A3E1E, 0xFF6A3E1E, 0x60A07040, 0xFF1E6A1E, 0xFF4A2A10, false),

    /** Dark stone and steel with rivets (like the grey custom hotbar). */
    STEEL(0xD02A2C34, 0xD01C1E24, 0xFF8A8E98, 0x60C8CCD8, 0xFFFF8A2A,
            Frame.RIVETS, Scenery.NONE, 0, 0, 0, Particles.NONE, 0,
            0xFFF0F0F4, 0xFFC8CCD8, 0x90101014, 0xFF8C1A1A, 0xFFE03A3A, 0xFFFFE0C0,
            0xFFFFB040, 0x708A8E98, 0xFFFF8A2A, 0x80000000, 0xFF7CFC7C, 0xFFE0E0E8),

    /** Gold and black with an animated rainbow rim (like the jukebox GUI). */
    RAINBOW(0xD0100C08, 0xD02A1C08, 0xFFF0B030, 0x60FFD870, 0xFFFFD870,
            Frame.RAINBOW, Scenery.NONE, 0, 0, 0, Particles.SPARKLES, 0xFFFFD870,
            0xFFFFF4D6, 0xFFFFE0A0, 0x90100800, 0xFFC06010, 0xFFFFD040, 0xFFFFF4D0,
            0xFFFFD040, 0x80F0B030, 0xFFF0B030, 0x80000000, 0xFF7CFC7C, 0xFFFFF0D0),

    MINIMAL(0xA0000000, 0xA0101010, 0x70FFFFFF, 0x00000000, 0xFFFFFFFF,
            Frame.SIMPLE, Scenery.NONE, 0, 0, 0, Particles.NONE, 0,
            0xFFFFFFFF, 0xFFD0D0D0, 0x90202020, 0xFFB01020, 0xFFFF4040, 0xFFFFFFFF,
            0xFFFFFFFF, 0x50FFFFFF, 0x80FFFFFF, 0x60000000, 0xFF7CFC7C, 0xFFE0E0E0);

    public enum Frame { SIMPLE, SNOWFLAKES, GEMS, FLOWERS, EMBERS, ORNATE, FOAM, WOOD, RIVETS, RAINBOW }

    public enum Scenery { NONE, SKYLINE, HILLS, FLAMES, GRASS, NEBULA, WAVES, STAINS }

    public enum Particles { NONE, STARS, SPARKLES, EMBERS, SNOW, PETALS, FIREFLIES, BUBBLES }

    public final int bgTop, bgBottom, frame, frameInner, accent;
    public final Frame frameType;
    public final Scenery scenery;
    public final int sceneryFront, sceneryBack, window;
    public final Particles particles;
    public final int particle;
    public final int nameText, hpText, barBg, barFrom, barTo, barGhost, header, divider, portraitEdge, portraitBg, self, other;
    /** Text shadow looks muddy on light backgrounds like parchment. */
    public final boolean textShadow;

    PanelStyle(int bgTop, int bgBottom, int frame, int frameInner, int accent,
               Frame frameType, Scenery scenery, int sceneryFront, int sceneryBack, int window,
               Particles particles, int particle,
               int nameText, int hpText, int barBg, int barFrom, int barTo, int barGhost,
               int header, int divider, int portraitEdge, int portraitBg, int self, int other) {
        this(bgTop, bgBottom, frame, frameInner, accent, frameType, scenery, sceneryFront, sceneryBack, window,
                particles, particle, nameText, hpText, barBg, barFrom, barTo, barGhost,
                header, divider, portraitEdge, portraitBg, self, other, true);
    }

    PanelStyle(int bgTop, int bgBottom, int frame, int frameInner, int accent,
               Frame frameType, Scenery scenery, int sceneryFront, int sceneryBack, int window,
               Particles particles, int particle,
               int nameText, int hpText, int barBg, int barFrom, int barTo, int barGhost,
               int header, int divider, int portraitEdge, int portraitBg, int self, int other, boolean textShadow) {
        this.textShadow = textShadow;
        this.bgTop = bgTop;
        this.bgBottom = bgBottom;
        this.frame = frame;
        this.frameInner = frameInner;
        this.accent = accent;
        this.frameType = frameType;
        this.scenery = scenery;
        this.sceneryFront = sceneryFront;
        this.sceneryBack = sceneryBack;
        this.window = window;
        this.particles = particles;
        this.particle = particle;
        this.nameText = nameText;
        this.hpText = hpText;
        this.barBg = barBg;
        this.barFrom = barFrom;
        this.barTo = barTo;
        this.barGhost = barGhost;
        this.header = header;
        this.divider = divider;
        this.portraitEdge = portraitEdge;
        this.portraitBg = portraitBg;
        this.self = self;
        this.other = other;
    }

    public String translationKey() {
        return "bossdamage.style." + name().toLowerCase(java.util.Locale.ROOT);
    }

    public PanelStyle next() {
        PanelStyle[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
