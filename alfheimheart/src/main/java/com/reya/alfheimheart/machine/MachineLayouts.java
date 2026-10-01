// Written by tools/machines/layouts.py from its numbers: change them there and run it (check_layout.py
// makes sure this is current).
package com.reya.alfheimheart.machine;

/** Every machine's GUI layout (see {@link MachineLayout}), and the numbers its screen draws its own parts at. */
public final class MachineLayouts {
    public static final MachineLayout RUNE_ALTAR = new MachineLayout("rune_altar", 256, 248, 156,
            new int[][]{{99, 23}, {133, 23}, {159, 45}, {165, 79}, {148, 108}, {84, 108}, {67, 79}, {73, 45}},
            new int[][]{{203, 52}, {221, 52}, {203, 70}, {221, 70}, {203, 88}, {221, 88}},
            new int[][]{{116, 120}},
            new int[]{48, 166, 224, 40, 216}, new int[]{245, -8}, new int[]{-5, -8}, new int[]{25, 129},
            new int[]{124, 59}, new int[]{124, 78}, new int[]{220, 78}, new int[]{21, 18, 45, 126},
            new int[][]{{115, 69, 18, 18}}, new int[][]{{3, 152}, {252, 152}, {43, 244}, {212, 244}}, new int[]{3, 3, 252, 152});
    public static final MachineLayout TERRA_PLATE = new MachineLayout("terra_plate", 256, 248, 156,
            new int[][]{{106, 24}, {146, 47}, {146, 93}, {106, 116}, {66, 93}, {66, 47}},
            new int[][]{{208, 52}, {208, 70}, {208, 88}},
            new int[0][],
            new int[]{48, 166, 224, 40, 216}, new int[]{245, -8}, new int[]{-5, -8}, new int[]{24, 128},
            new int[]{216, 30}, new int[]{114, 78}, new int[]{216, 78}, new int[]{44, 16, 84, 140},
            new int[][]{{106, 70, 16, 16}}, new int[][]{{3, 152}, {252, 152}, {43, 244}, {212, 244}}, new int[]{3, 3, 252, 152});
    public static final MachineLayout MANA_INFUSER = new MachineLayout("mana_infuser", 256, 248, 156,
            new int[][]{{26, 44}, {44, 44}, {26, 62}, {44, 62}, {26, 80}, {44, 80}},
            new int[][]{{194, 44}, {212, 44}, {194, 62}, {212, 62}, {194, 80}, {212, 80}},
            new int[][]{{120, 117}},
            new int[]{48, 166, 224, 40, 216}, new int[]{245, -8}, new int[]{-5, -8}, new int[]{20, 128},
            new int[]{128, 89}, new int[]{128, 56}, new int[]{212, 70}, new int[]{82, 56, 174, 105},
            new int[][]{{118, 22, 20, 20}}, new int[][]{{3, 152}, {252, 152}, {43, 244}, {212, 244}}, new int[]{3, 3, 252, 152});
    public static final MachineLayout PURE_DAISY = new MachineLayout("pure_daisy", 256, 248, 156,
            new int[][]{{88, 46}, {116, 46}, {144, 46}, {144, 74}, {144, 102}, {116, 102}, {88, 102}, {88, 74}},
            new int[][]{{203, 54}, {221, 54}, {203, 72}, {221, 72}, {203, 90}, {221, 90}},
            new int[0][],
            new int[]{48, 166, 224, 40, 216}, new int[]{245, -8}, new int[]{-5, -8}, new int[]{26, 126},
            new int[]{124, 131}, new int[]{124, 82}, new int[]{220, 80}, new int[]{15, 42, 53, 119},
            new int[][]{{115, 73, 18, 18}}, new int[][]{{3, 152}, {252, 152}, {43, 244}, {212, 244}}, new int[]{3, 3, 252, 152});
    public static final MachineLayout PETAL_APOTHECARY = new MachineLayout("petal_apothecary", 256, 248, 156,
            new int[][]{{56, 96}, {61, 72}, {75, 51}, {96, 37}, {120, 32}, {144, 37}, {165, 51}, {179, 72}, {184, 96}},
            new int[][]{{216, 38}, {216, 56}, {216, 74}, {216, 92}},
            new int[][]{{120, 127}},
            new int[]{48, 166, 224, 40, 216}, new int[]{245, -8}, new int[]{-5, -8}, new int[]{22, 128},
            new int[]{128, 119}, new int[]{128, 70}, new int[]{224, 74}, new int[]{13, 56, 47, 121},
            new int[][]{{119, 61, 18, 18}}, new int[][]{{3, 152}, {252, 152}, {43, 244}, {212, 244}}, new int[]{3, 3, 252, 152});
    public static final MachineLayout PETAL_FARM = new MachineLayout("petal_farm", 256, 248, 156,
            new int[][]{{86, 40}, {120, 40}, {154, 40}, {86, 84}, {120, 84}, {154, 84}},
            new int[][]{{203, 30}, {225, 30}, {203, 52}, {225, 52}, {203, 74}, {225, 74}, {203, 96}, {225, 96}},
            new int[][]{{120, 127}},
            new int[]{48, 166, 224, 40, 216}, new int[]{245, -8}, new int[]{-5, -8}, new int[]{22, 124},
            new int[]{128, 66}, new int[]{128, 70}, new int[]{225, 70}, new int[]{12, 60, 62, 114},
            new int[0][], new int[][]{{3, 152}, {252, 152}, {43, 244}, {212, 244}}, new int[]{3, 3, 252, 152});
    public static final MachineLayout ORECHID_MINE = new MachineLayout("orechid_mine", 256, 248, 156,
            new int[][]{{19, 101}, {37, 101}, {19, 119}, {37, 119}},
            new int[][]{{188, 52}, {206, 52}, {224, 52}, {188, 70}, {206, 70}, {224, 70}, {188, 88}, {206, 88}, {224, 88}},
            new int[0][],
            new int[]{48, 166, 224, 40, 216}, new int[]{245, -8}, new int[]{-5, -8}, new int[]{66, 128},
            new int[]{121, 28}, new int[]{121, 66}, new int[]{215, 79}, new int[]{21, 10, 47, 66},
            new int[][]{{112, 57, 18, 18}}, new int[][]{{3, 152}, {252, 152}, {43, 244}, {212, 244}}, new int[]{3, 3, 252, 152});
    public static final MachineLayout CROP_FIELD = new MachineLayout("crop_field", 256, 248, 156,
            new int[][]{{78, 56}, {114, 56}, {150, 56}, {78, 94}, {114, 94}, {150, 94}},
            new int[][]{{189, 56}, {207, 56}, {225, 56}, {189, 74}, {207, 74}, {225, 74}, {189, 92}, {207, 92}, {225, 92}},
            new int[][]{{114, 127}},
            new int[]{48, 166, 224, 40, 216}, new int[]{245, -8}, new int[]{-5, -8}, new int[]{24, 128},
            new int[]{122, 84}, new int[]{122, 84}, new int[]{215, 83}, new int[]{20, 57, 44, 123},
            new int[0][], new int[][]{{3, 152}, {252, 152}, {43, 244}, {212, 244}}, new int[]{3, 3, 252, 152});

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

    /** The numbers the Mana Infuser's fountain is drawn at. */
    public static final class Infuser {
        public static final int CX = 128;
        public static final int CY = 56;
        public static final int RIM_RX = 46;
        public static final int RIM_RY = 17;
        public static final int OPEN_RX = 40;
        public static final int OPEN_RY = 13;
        public static final int DEPTH = 32;
        public static final int WATER_EMPTY = 27;
        public static final int WATER_FULL = 2;
        public static final int[][] ALCOVES = {{19, 30, 69, 106}, {187, 30, 237, 106}};
        public static final int CATALYST_RING_U = 128;
        public static final int CATALYST_RING_V = 32;
        public static final int CATALYST_RING_SIZE = 28;

        private Infuser() {
        }
    }

    /** The numbers the Pure Daisy's garden is drawn at. */
    public static final class Daisy {
        public static final int CX = 124;
        public static final int CY = 82;
        public static final int CELL = 28;
        public static final int[][] RING = {{-1, -1}, {0, -1}, {1, -1}, {1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}};
        public static final int[] BASKET = {194, 44, 246, 116};
        public static final int[] DROP = {34, 100, 17, 56};
        public static final int[][] DROP_ROWS = {{116, 32, 36}, {115, 30, 38}, {114, 28, 40}, {113, 26, 42}, {112, 25, 43}, {111, 24, 44}, {110, 23, 45}, {109, 22, 46}, {108, 22, 46}, {107, 21, 47}, {106, 21, 47}, {105, 20, 48}, {104, 20, 48}, {103, 20, 48}, {102, 20, 48}, {101, 20, 48}, {100, 20, 48}, {99, 20, 48}, {98, 20, 48}, {97, 20, 48}, {96, 20, 48}, {95, 20, 48}, {94, 20, 48}, {93, 20, 48}, {92, 20, 48}, {91, 20, 48}, {90, 20, 48}, {89, 21, 47}, {88, 21, 47}, {87, 21, 47}, {86, 21, 47}, {85, 22, 46}, {84, 22, 46}, {83, 22, 46}, {82, 23, 45}, {81, 23, 45}, {80, 23, 45}, {79, 24, 44}, {78, 24, 44}, {77, 24, 44}, {76, 25, 43}, {75, 25, 43}, {74, 26, 42}, {73, 26, 42}, {72, 27, 41}, {71, 27, 41}, {70, 27, 41}, {69, 28, 40}, {68, 28, 40}, {67, 29, 39}, {66, 29, 39}, {65, 30, 38}, {64, 30, 38}, {63, 31, 37}, {62, 31, 37}, {61, 31, 37}, {60, 32, 36}, {59, 32, 36}, {58, 33, 35}, {57, 33, 35}, {56, 33, 35}};
        public static final int BUTTERFLY_U = 128;
        public static final int BUTTERFLY_V = 32;
        public static final int BUTTERFLY_SIZE = 7;
        public static final int PETAL_U = 144;
        public static final int PETAL_V = 32;

        private Daisy() {
        }
    }

    /** The numbers the Petal Apothecary's table is drawn at. */
    public static final class Apothecary {
        public static final int CX = 128;
        public static final int CY = 104;
        public static final int RIM_RX = 24;
        public static final int RIM_RY = 8;
        public static final int WATER_RX = 20;
        public static final int WATER_RY = 5;
        public static final int[] FLOWER = {128, 70};
        public static final int[] SHELF = {207, 24, 243, 118};
        public static final int[] FLASK = {30, 104, 15, 44, 4};
        public static final int[][] FLASK_ROWS = {{118, 29, 31}, {117, 26, 34}, {116, 24, 36}, {115, 23, 37}, {114, 22, 38}, {113, 21, 39}, {112, 20, 40}, {111, 20, 40}, {110, 19, 41}, {109, 19, 41}, {108, 18, 42}, {107, 18, 42}, {106, 18, 42}, {105, 18, 42}, {104, 18, 42}, {103, 18, 42}, {102, 18, 42}, {101, 18, 42}, {100, 18, 42}, {99, 18, 42}, {98, 19, 41}, {97, 19, 41}, {96, 20, 40}, {95, 20, 40}, {94, 28, 32}, {93, 28, 32}, {92, 28, 32}, {91, 28, 32}, {90, 28, 32}, {89, 28, 32}, {88, 28, 32}, {87, 28, 32}, {86, 28, 32}, {85, 28, 32}, {84, 28, 32}, {83, 28, 32}, {82, 28, 32}, {81, 28, 32}, {80, 28, 32}, {79, 28, 32}, {78, 28, 32}, {77, 28, 32}, {76, 28, 32}, {75, 28, 32}, {74, 28, 32}, {73, 28, 32}, {72, 28, 32}, {71, 28, 32}, {70, 28, 32}, {69, 28, 32}, {68, 28, 32}, {67, 28, 32}, {66, 28, 32}, {65, 28, 32}, {64, 28, 32}};
        public static final int PETAL_U = 128;
        public static final int PETAL_V = 32;
        public static final int STEAM_U = 136;
        public static final int STEAM_V = 32;
        public static final int STEAM_SIZE = 9;

        private Apothecary() {
        }
    }

    /** The numbers the Petal Farm's greenhouse is drawn at. */
    public static final class Farm {
        public static final int CX = 128;
        public static final int CY = 74;
        public static final int[][] SHELVES = {{74, 64, 182}, {74, 108, 182}};
        public static final int[] JARS = {197, 22, 247, 118};
        public static final int[] SUN = {128, 44, 40};
        public static final int[] CAN = {14, 74, 46, 112, 6};
        public static final int[][] CAN_ROWS = {{111, 17, 43}, {110, 17, 43}, {109, 17, 43}, {108, 17, 43}, {107, 17, 43}, {106, 17, 43}, {105, 17, 43}, {104, 17, 43}, {103, 17, 43}, {102, 17, 43}, {101, 17, 43}, {100, 17, 43}, {99, 17, 43}, {98, 17, 43}, {97, 17, 43}, {96, 17, 43}, {95, 17, 43}, {94, 17, 43}, {93, 17, 43}, {92, 17, 43}, {91, 17, 43}, {90, 17, 43}, {89, 17, 43}, {88, 17, 43}, {87, 17, 43}, {86, 17, 43}, {85, 17, 43}, {84, 17, 43}, {83, 17, 43}, {82, 17, 43}, {81, 17, 43}, {80, 17, 43}, {79, 17, 43}, {78, 17, 43}, {77, 17, 43}};
        public static final int PETAL_U = 128;
        public static final int PETAL_V = 32;

        private Farm() {
        }
    }

    /** The numbers the Orechid Mine's cavern is drawn at. */
    public static final class Mine {
        public static final int CX = 121;
        public static final int CY = 66;
        public static final int ORE_R = 36;
        public static final int[][] ORES = {{135, 33}, {154, 52}, {154, 80}, {135, 99}, {107, 99}, {88, 80}, {88, 52}, {107, 33}};
        public static final int[] ORECHID = {121, 116};
        public static final int[] CART = {13, 95, 61, 139};
        public static final int[] CHEST = {182, 34, 248, 112};
        public static final int[] LANTERN = {34, 40, 11, 22};
        public static final int[][] LANTERN_ROWS = {{61, 28, 40}, {60, 28, 40}, {59, 28, 40}, {58, 27, 41}, {57, 27, 41}, {56, 27, 41}, {55, 27, 41}, {54, 27, 41}, {53, 27, 41}, {52, 26, 42}, {51, 26, 42}, {50, 26, 42}, {49, 26, 42}, {48, 26, 42}, {47, 26, 42}, {46, 26, 42}, {45, 26, 42}, {44, 26, 42}, {43, 26, 42}, {42, 26, 42}, {41, 26, 42}, {40, 26, 42}, {39, 26, 42}, {38, 26, 42}, {37, 26, 42}, {36, 26, 42}, {35, 26, 42}, {34, 26, 42}, {33, 26, 42}, {32, 26, 42}, {31, 26, 42}, {30, 26, 42}, {29, 26, 42}, {28, 26, 42}, {27, 26, 42}, {26, 27, 41}, {25, 27, 41}, {24, 27, 41}, {23, 27, 41}, {22, 27, 41}, {21, 27, 41}, {20, 28, 40}, {19, 28, 40}, {18, 28, 40}};
        public static final int[][] CRYSTALS = {{88, 22}, {160, 30}, {70, 70}, {172, 128}, {98, 138}, {150, 20}};

        private Mine() {
        }
    }

    /** The numbers the Crop Field's field is drawn at. */
    public static final class Field {
        public static final int CX = 122;
        public static final int CY = 76;
        public static final int[][] FURROWS = {{66, 50, 178, 78}, {66, 88, 178, 116}};
        public static final int[] CRATE = {182, 46, 248, 116};
        public static final int[] TUBE = {32, 92, 8, 27};
        public static final int[][] TUBE_ROWS = {{116, 26, 38}, {115, 26, 38}, {114, 26, 38}, {113, 26, 38}, {112, 26, 38}, {111, 26, 38}, {110, 26, 38}, {109, 26, 38}, {108, 26, 38}, {107, 26, 38}, {106, 26, 38}, {105, 26, 38}, {104, 26, 38}, {103, 26, 38}, {102, 26, 38}, {101, 26, 38}, {100, 26, 38}, {99, 26, 38}, {98, 26, 38}, {97, 26, 38}, {96, 26, 38}, {95, 26, 38}, {94, 26, 38}, {93, 26, 38}, {92, 26, 38}, {91, 26, 38}, {90, 26, 38}, {89, 26, 38}, {88, 26, 38}, {87, 26, 38}, {86, 26, 38}, {85, 26, 38}, {84, 26, 38}, {83, 26, 38}, {82, 26, 38}, {81, 26, 38}, {80, 26, 38}, {79, 26, 38}, {78, 26, 38}, {77, 26, 38}, {76, 26, 38}, {75, 26, 38}, {74, 26, 38}, {73, 26, 38}, {72, 26, 38}, {71, 26, 38}, {70, 26, 38}, {69, 26, 38}, {68, 26, 38}, {67, 26, 38}};
        public static final int[] CLOUD = {32, 46};
        public static final int[] SCARECROW = {176, 14};

        private Field() {
        }
    }

    private MachineLayouts() {
    }
}
