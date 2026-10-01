// Written by tools/machines/layouts.py from its numbers: change them there and run it (check_layout.py
// makes sure this is current).
package com.reya.alfheimheart.machine;

/** Every machine's GUI layout (see {@link MachineLayout}), and the numbers its screen draws its own parts at. */
public final class MachineLayouts {
    public static final MachineLayout RUNE_ALTAR = new MachineLayout("rune_altar", false, 256, 248, 156,
            new int[][]{{99, 23}, {133, 23}, {159, 45}, {165, 79}, {148, 108}, {84, 108}, {67, 79}, {73, 45}},
            new int[][]{{203, 52}, {221, 52}, {203, 70}, {221, 70}, {203, 88}, {221, 88}},
            new int[][]{{116, 120}},
            new int[]{48, 166, 224, 40, 216}, new int[]{245, -8}, new int[]{-5, -8}, new int[]{25, 129},
            new int[]{124, 59}, new int[]{124, 78}, new int[]{220, 78}, new int[]{21, 18, 45, 126},
            new int[][]{{115, 69, 18, 18}}, new int[][]{{3, 152}, {252, 152}, {43, 244}, {212, 244}}, new int[]{3, 3, 252, 152});
    public static final MachineLayout TERRA_PLATE = new MachineLayout("terra_plate", false, 256, 248, 156,
            new int[][]{{106, 24}, {146, 47}, {146, 93}, {106, 116}, {66, 93}, {66, 47}},
            new int[][]{{208, 52}, {208, 70}, {208, 88}},
            new int[0][],
            new int[]{48, 166, 224, 40, 216}, new int[]{245, -8}, new int[]{-5, -8}, new int[]{24, 128},
            new int[]{216, 30}, new int[]{114, 78}, new int[]{216, 78}, new int[]{44, 16, 84, 140},
            new int[][]{{106, 70, 16, 16}}, new int[][]{{3, 152}, {252, 152}, {43, 244}, {212, 244}}, new int[]{3, 3, 252, 152});
    public static final MachineLayout MANA_INFUSER = new MachineLayout("mana_infuser", true, 240, 214, 124,
            new int[][]{{18, 30}, {36, 30}, {54, 30}, {18, 48}, {36, 48}, {54, 48}, {18, 66}, {36, 66}, {54, 66}},
            new int[][]{{168, 30}, {186, 30}, {204, 30}, {168, 48}, {186, 48}, {204, 48}, {168, 66}, {186, 66}, {204, 66}},
            new int[][]{{112, 84}},
            new int[]{40, 132, 190, 28, 212}, new int[]{229, -8}, new int[]{18, 102}, new int[]{206, 102},
            new int[]{120, 66}, new int[]{120, 27}, new int[]{194, 56}, new int[]{50, 105, 178, 117},
            new int[][]{{74, 53, 12, 9}, {155, 53, 12, 9}}, new int[][]{{1, 1}, {1, 122}, {238, 122}, {29, 212}, {210, 212}}, new int[]{3, 3, 236, 120});
    public static final MachineLayout PURE_DAISY = new MachineLayout("pure_daisy", true, 240, 214, 124,
            new int[][]{{18, 30}, {36, 30}, {54, 30}, {18, 48}, {36, 48}, {54, 48}, {18, 66}, {36, 66}, {54, 66}},
            new int[][]{{168, 30}, {186, 30}, {204, 30}, {168, 48}, {186, 48}, {204, 48}, {168, 66}, {186, 66}, {204, 66}},
            new int[0][],
            new int[]{40, 132, 190, 28, 212}, new int[]{229, -8}, new int[]{18, 102}, new int[]{206, 102},
            new int[]{120, 19}, new int[]{120, 52}, new int[]{194, 56}, new int[]{50, 105, 178, 117},
            new int[][]{{74, 53, 12, 9}, {155, 53, 12, 9}}, new int[][]{{1, 1}, {1, 122}, {238, 122}, {29, 212}, {210, 212}}, new int[]{3, 3, 236, 120});
    public static final MachineLayout PETAL_APOTHECARY = new MachineLayout("petal_apothecary", true, 240, 214, 124,
            new int[][]{{18, 30}, {36, 30}, {54, 30}, {18, 48}, {36, 48}, {54, 48}, {18, 66}, {36, 66}, {54, 66}},
            new int[][]{{168, 30}, {186, 30}, {204, 30}, {168, 48}, {186, 48}, {204, 48}, {168, 66}, {186, 66}, {204, 66}},
            new int[][]{{112, 84}},
            new int[]{40, 132, 190, 28, 212}, new int[]{229, -8}, new int[]{18, 102}, new int[]{206, 102},
            new int[]{120, 57}, new int[]{120, 22}, new int[]{194, 56}, new int[]{50, 105, 178, 117},
            new int[][]{{74, 53, 12, 9}, {155, 53, 12, 9}}, new int[][]{{1, 1}, {1, 122}, {238, 122}, {29, 212}, {210, 212}}, new int[]{3, 3, 236, 120});
    public static final MachineLayout PETAL_FARM = new MachineLayout("petal_farm", true, 240, 214, 124,
            new int[][]{{18, 30}, {36, 30}, {54, 30}, {18, 48}, {36, 48}, {54, 48}, {18, 66}, {36, 66}, {54, 66}},
            new int[][]{{168, 30}, {186, 30}, {204, 30}, {168, 48}, {186, 48}, {204, 48}, {168, 66}, {186, 66}, {204, 66}},
            new int[][]{{112, 84}},
            new int[]{40, 132, 190, 28, 212}, new int[]{229, -8}, new int[]{18, 102}, new int[]{206, 102},
            new int[]{120, 62}, new int[]{120, 44}, new int[]{194, 56}, new int[]{50, 105, 178, 117},
            new int[][]{{74, 53, 12, 9}, {155, 53, 12, 9}}, new int[][]{{1, 1}, {1, 122}, {238, 122}, {29, 212}, {210, 212}}, new int[]{3, 3, 236, 120});
    public static final MachineLayout ORECHID_MINE = new MachineLayout("orechid_mine", true, 240, 214, 124,
            new int[][]{{18, 30}, {36, 30}, {54, 30}, {18, 48}, {36, 48}, {54, 48}, {18, 66}, {36, 66}, {54, 66}},
            new int[][]{{168, 30}, {186, 30}, {204, 30}, {168, 48}, {186, 48}, {204, 48}, {168, 66}, {186, 66}, {204, 66}},
            new int[0][],
            new int[]{40, 132, 190, 28, 212}, new int[]{229, -8}, new int[]{18, 102}, new int[]{206, 102},
            new int[]{120, 31}, new int[]{120, 50}, new int[]{194, 56}, new int[]{50, 105, 178, 117},
            new int[][]{{74, 53, 12, 9}, {155, 53, 12, 9}}, new int[][]{{1, 1}, {1, 122}, {238, 122}, {29, 212}, {210, 212}}, new int[]{3, 3, 236, 120});
    public static final MachineLayout CROP_FIELD = new MachineLayout("crop_field", true, 240, 214, 124,
            new int[][]{{18, 30}, {36, 30}, {54, 30}, {18, 48}, {36, 48}, {54, 48}, {18, 66}, {36, 66}, {54, 66}},
            new int[][]{{168, 30}, {186, 30}, {204, 30}, {168, 48}, {186, 48}, {204, 48}, {168, 66}, {186, 66}, {204, 66}},
            new int[][]{{112, 84}},
            new int[]{40, 132, 190, 28, 212}, new int[]{229, -8}, new int[]{18, 102}, new int[]{206, 102},
            new int[]{120, 76}, new int[]{120, 50}, new int[]{194, 56}, new int[]{50, 105, 178, 117},
            new int[][]{{74, 53, 12, 9}, {155, 53, 12, 9}}, new int[][]{{1, 1}, {1, 122}, {238, 122}, {29, 212}, {210, 212}}, new int[]{3, 3, 236, 120});

    /** The numbers the Runic Altar's sanctum is drawn at. */
    public static final class Altar {
        public static final int CX = 124;
        public static final int CY = 78;
        public static final int ALTAR_R = 22;
        public static final int HOLLOW_R = 10;
        public static final int INNER_R = 28;
        public static final int SOCKET_R = 50;
        public static final int OUTER_R = 64;
        public static final int[] RUNE_COLOURS = {0xFF5A3C, 0xFF9C33, 0xFFE04A, 0x86E04C, 0x6CF0EC, 0x4A86FF, 0xAE92FF, 0xFF74D2};
        public static final int REAGENT_COLOUR = 0xA6F6FF;
        public static final float[][] SPOKES = {{110.7F, 41.5F, 113.9F, 50.3F}, {137.3F, 41.5F, 134.1F, 50.3F}, {156.9F, 59.0F, 149.5F, 63.2F}, {162.8F, 84.8F, 153.1F, 83.1F}, {147.5F, 106.0F, 143.0F, 100.6F}, {100.5F, 106.0F, 105.0F, 100.6F}, {85.2F, 84.8F, 94.9F, 83.1F}, {91.1F, 59.0F, 98.5F, 63.2F}, {124.0F, 117.5F, 124.0F, 107.5F}};
        public static final int[] NICHE = {196, 22, 244, 120};
        public static final int[] GAUGE = {28, 34, 38, 118};
        public static final int GLYPH_U = 0;
        public static final int GLYPH_V = 112;
        public static final int GLYPH_SIZE = 9;
        public static final int MANA_GLYPH_U = 72;
        public static final int MANA_GLYPH_V = 112;
        public static final int HALO_SOCKET_U = 128;
        public static final int HALO_SOCKET_V = 32;
        public static final int HALO_SOCKET_SIZE = 26;
        public static final int FILL_U = 232;
        public static final int FILL_V = 0;

        private Altar() {
        }
    }

    /** The numbers the Terrestrial Plate's astrolabe is drawn at. */
    public static final class Plate {
        public static final int CX = 114;
        public static final int CY = 78;
        public static final int PLATE_R = 23;
        public static final float CORE_R = 6.5F;
        public static final int SOCKET_R = 46;
        public static final int[] SKY = {0, 2, 4};
        public static final int LIMB_R1 = 54;
        public static final int LIMB_R2 = 59;
        public static final int ARC_R1 = 63;
        public static final int ARC_R2 = 69;
        public static final int ARC_FROM = 120;
        public static final int ARC_TO = 240;
        public static final int[] TOWER = {197, 46, 235, 124};
        public static final int[] MOON = {216, 30, 11};
        public static final int[] BEAM = {173, 78, 197, 78};
        public static final float SUN_RING_R = 13.5F;
        public static final float RAY_FROM = 8.5F;
        public static final float RAY_TO = 19.0F;
        public static final float TIP_TO = 20.2F;
        public static final int[][] STARS = {{16, 22}, {60, 14}, {180, 18}, {246, 64}, {152, 140}, {14, 98}, {238, 138}, {90, 146}};
        public static final int STAR_U = 128;
        public static final int STAR_V = 32;
        public static final int STAR_SIZE = 9;

        private Plate() {
        }
    }

    private MachineLayouts() {
    }
}
