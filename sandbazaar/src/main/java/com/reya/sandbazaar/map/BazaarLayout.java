package com.reya.sandbazaar.map;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lays out the Sunbaked Bazaar: a walled desert town with the attackers' caravan yard in the west, the defenders'
 * fort in the east and two sites either side of the fort - a caravanserai (A) and a palm court (B). Three ways lead
 * across: the north lane, the market street through the well plaza, and the sunken stepwell in the south.
 *
 * <p>x runs west to east, z north to south, and y = 0 is the block the streets are paved with.
 */
public final class BazaarLayout {
    public static final int W = 104;
    public static final int D = 88;
    public static final int MIN_Y = -6;
    public static final int MAX_Y = 27;

    private static final int MASS = Integer.MIN_VALUE;
    private static final int SKY = Integer.MAX_VALUE;
    private static final String AIR = Blueprint.AIR;

    private static final int NORTH = 0;
    private static final int EAST = 1;
    private static final int SOUTH = 2;
    private static final int WEST = 3;
    private static final int[] DX = {0, 1, 0, -1};
    private static final int[] DZ = {-1, 0, 1, 0};
    private static final String[] DIR = {"north", "east", "south", "west"};

    private static final int CAMP = 1;
    private static final int STREET = 2;
    private static final int ALLEY = 3;
    private static final int MARKET = 4;
    private static final int PLAZA = 5;
    private static final int SITE_A = 6;
    private static final int SITE_B = 7;
    private static final int FORT = 8;
    private static final int PIT = 9;
    private static final int PASSAGE = 10;
    private static final int COURT = 11;

    private static final int SAND = 0;
    private static final int SMOOTH = 1;
    private static final int MUD = 2;
    private static final int PLASTER = 3;
    private static final int TERRA = 4;
    private static final int WALL = 5;

    /** Places worth teleporting to: both spawns, both sites and the well plaza. */
    public static final Map<String, Blueprint.Mark> MARKS;

    static {
        Map<String, Blueprint.Mark> marks = new LinkedHashMap<>();
        marks.put("attack", new Blueprint.Mark(13, 1, 44, -90));
        marks.put("defend", new Blueprint.Mark(88, 1, 45, 90));
        marks.put("a", new Blueprint.Mark(81, 1, 26, 180));
        marks.put("b", new Blueprint.Mark(82, 1, 67, 0));
        marks.put("plaza", new Blueprint.Mark(52, 1, 49, 180));
        MARKS = Collections.unmodifiableMap(marks);
    }

    private static final String[] GLYPH_A = {".###.", "#...#", "#####", "#...#", "#...#"};
    private static final String[] GLYPH_B = {"####.", "#...#", "####.", "#...#", "####."};

    private final Blueprint bp = new Blueprint(W, D, MIN_Y, MAX_Y);
    /** Surface block height of a walkable cell, or MASS where a building stands. */
    private final int[][] floor = new int[W][D];
    /** Height of the ceiling block over a walkable cell, or SKY. */
    private final int[][] ceil = new int[W][D];
    /** Roof height of the building standing on (or over) a cell. */
    private final int[][] top = new int[W][D];
    private final int[][] style = new int[W][D];
    private final int[][] lot = new int[W][D];
    private final int[][] zone = new int[W][D];
    /** The direction a flight of steps climbs towards, or -1. */
    private final int[][] stair = new int[W][D];
    private final List<int[]> lots = new ArrayList<>();
    /** Cells holding plain building mass that still has to be given its masonry. */
    private final BitSet raw = new BitSet(W * D * (MAX_Y - MIN_Y + 1));

    private BazaarLayout() {}

    public static Blueprint build() {
        BazaarLayout layout = new BazaarLayout();
        layout.lots();
        layout.zones();
        layout.emit();
        layout.styleWalls();
        layout.parapets();
        layout.facades();
        layout.roofs();
        layout.decorate();
        layout.fixDoors();
        return layout.bp;
    }

    // ---------------------------------------------------------------------------------------------- town blocks

    private void lots() {
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                floor[x][z] = MASS;
                ceil[x][z] = SKY;
                stair[x][z] = -1;
            }
        }
        split(0, 0, W - 1, D - 1, 1);

        // the town wall and its towers
        lot(0, 0, W - 1, 1, 8, WALL);
        lot(0, D - 2, W - 1, D - 1, 8, WALL);
        lot(0, 0, 1, D - 1, 8, WALL);
        lot(W - 2, 0, W - 1, D - 1, 8, WALL);
        lot(0, 39, 3, 49, 11, WALL);
        // the caravanserai is ringed by high walls, so nobody leaves by its gallery roof
        lot(58, 0, 65, 22, 11, SAND);
        lot(66, 0, 96, 4, 11, SAND);
        lot(97, 7, W - 1, 35, 11, SAND);
        // the fort
        lot(71, 32, 99, 35, 9, WALL);
        lot(71, 55, 99, 59, 9, WALL);
        lot(97, 36, W - 1, 54, 9, WALL);
        lot(61, 36, 72, 42, 9, WALL);
        lot(61, 48, 72, 54, 9, WALL);
        // the gatehouse between the caravan yard and the market
        lot(23, 38, 26, 50, 10, WALL);
        lot(0, 0, 6, 6, 12, WALL);
        lot(W - 7, 0, W - 1, 6, 13, WALL);
        lot(0, D - 7, 6, D - 1, 12, WALL);
        lot(W - 7, D - 7, W - 1, D - 1, 12, WALL);
    }

    private void split(int x1, int z1, int x2, int z2, int seed) {
        int w = x2 - x1 + 1;
        int d = z2 - z1 + 1;
        if (w <= 13 && d <= 13) {
            int h = hash(seed, x1, z1);
            int[] styles = {SAND, SAND, SAND, SAND, SMOOTH, SMOOTH, MUD, MUD, PLASTER, TERRA};
            lot(x1, z1, x2, z2, 7 + h % 5, styles[(h / 7) % styles.length]);
        } else if (w >= d) {
            int cut = x1 + 6 + hash(seed, w, 17) % (w - 11);
            split(x1, z1, cut - 1, z2, seed * 31 + 7);
            split(cut, z1, x2, z2, seed * 31 + 19);
        } else {
            int cut = z1 + 6 + hash(seed, d, 23) % (d - 11);
            split(x1, z1, x2, cut - 1, seed * 31 + 7);
            split(x1, cut, x2, z2, seed * 31 + 19);
        }
    }

    private void lot(int x1, int z1, int x2, int z2, int height, int st) {
        int id = lots.size();
        lots.add(new int[] {x1, z1, x2, z2});
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                top[x][z] = height;
                style[x][z] = st;
                lot[x][z] = id;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- streets

    private void zones() {
        open(CAMP, 4, 30, 22, 58, 0);                // attackers' caravan yard
        open(ALLEY, 12, 15, 16, 29, 0);              // north alley
        open(STREET, 12, 8, 60, 14, 0);              // north lane
        roofed(PASSAGE, 61, 9, 65, 13, 0, 5);        // caravanserai gate
        open(SITE_A, 66, 5, 96, 31, 0);              // site A: the caravanserai
        open(ALLEY, 28, 15, 31, 28, 0);              // potters' alley
        open(ALLEY, 32, 25, 35, 41, 0);
        open(ALLEY, 48, 15, 51, 35, 0);              // spice alley
        roofed(PASSAGE, 52, 23, 65, 26, 0, 5);       // covered way into A
        roofed(PASSAGE, 23, 42, 26, 46, 0, 5);       // yard gate
        open(MARKET, 27, 42, 37, 46, 0);             // market street, with a dogleg
        open(MARKET, 33, 47, 37, 53, 0);
        open(MARKET, 38, 49, 43, 53, 0);
        open(PLAZA, 44, 36, 60, 54, 0);              // well plaza
        open(STREET, 61, 43, 72, 47, 0);             // fort lane
        open(FORT, 73, 36, 96, 54, 0);               // defenders' fort
        open(STREET, 80, 32, 85, 35, 0);             // fort to A
        open(STREET, 80, 55, 85, 59, 0);             // fort to B
        open(SITE_B, 64, 60, 89, 83, 0);             // site B: the palm court
        open(SITE_B, 90, 60, 96, 83, 1);             // and its terrace
        open(ALLEY, 50, 55, 54, 58, 0);              // dyers' alley
        steps(50, 59, 54, 63, NORTH, 0);
        open(PIT, 25, 64, 58, 80, -5);               // the stepwell
        open(PIT, 25, 79, 54, 79, -4);
        open(PIT, 25, 80, 54, 80, -3);
        open(ALLEY, 12, 59, 16, 73, 0);              // south alley
        open(ALLEY, 17, 69, 18, 73, 0);
        steps(19, 69, 23, 73, WEST, 0);
        open(PIT, 24, 69, 24, 73, -5);
        steps(59, 65, 63, 69, EAST, 0);              // stepwell up to B
        roofed(PIT, 59, 76, 63, 79, -5, 0);          // low tunnel to B
        steps(64, 76, 68, 79, EAST, 0);

        // closed courtyards, seen from the roofs and through nothing else
        open(COURT, 20, 19, 24, 25, 0);
        open(COURT, 39, 18, 44, 22, 0);
        open(COURT, 39, 30, 41, 38, 0);
        open(COURT, 26, 50, 30, 60, 0);
        open(COURT, 38, 57, 46, 61, 0);
    }

    private void open(int zn, int x1, int z1, int x2, int z2, int y) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                floor[x][z] = y;
                ceil[x][z] = SKY;
                zone[x][z] = zn;
                stair[x][z] = -1;
            }
        }
    }

    private void roofed(int zn, int x1, int z1, int x2, int z2, int y, int ceiling) {
        open(zn, x1, z1, x2, z2, y);
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                ceil[x][z] = ceiling;
                top[x][z] = Math.max(top[x][z], ceiling + 2);
            }
        }
    }

    /** A flight of steps climbing towards {@code up} and arriving at height {@code high}. */
    private void steps(int x1, int z1, int x2, int z2, int up, int high) {
        open(ALLEY, x1, z1, x2, z2, high);
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                int i = switch (up) {
                    case EAST -> x2 - x;
                    case WEST -> x - x1;
                    case SOUTH -> z2 - z;
                    default -> z - z1;
                };
                floor[x][z] = high - i;
                stair[x][z] = up;
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- raw volume

    private void emit() {
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                int low = -1;
                for (int nx = x - 1; nx <= x + 1; nx++) {
                    for (int nz = z - 1; nz <= z + 1; nz++) {
                        if (walkable(nx, nz)) {
                            low = Math.min(low, floor[nx][nz] - 1);
                        }
                    }
                }
                int y = low;
                if (floor[x][z] == MASS) {
                    for (; y <= top[x][z]; y++) {
                        fill(x, y, z);
                    }
                } else {
                    int f = floor[x][z];
                    for (; y < f; y++) {
                        fill(x, y, z);
                    }
                    bp.set(x, y++, z, stair[x][z] >= 0 ? "sandstone_stairs[facing=" + DIR[stair[x][z]] + "]" : paving(x, z));
                    if (ceil[x][z] != SKY) {
                        for (; y < ceil[x][z]; y++) {
                            bp.set(x, y, z, AIR);
                        }
                        bp.set(x, y++, z, "smooth_sandstone");
                        for (; y <= top[x][z]; y++) {
                            fill(x, y, z);
                        }
                    }
                }
                for (; y <= MAX_Y; y++) {
                    bp.set(x, y, z, AIR);
                }
            }
        }
    }

    private void fill(int x, int y, int z) {
        bp.set(x, y, z, "sandstone");
        raw.set(((y - MIN_Y) * D + z) * W + x);
    }

    private String paving(int x, int z) {
        int h = hash(x, z, 11) % 100;
        switch (zone[x][z]) {
            case CAMP:
                return h < 72 ? "sand" : h < 92 ? "sandstone" : "coarse_dirt";
            case MARKET:
                return h < 60 ? "smooth_sandstone" : h < 85 ? "cut_sandstone" : "sandstone";
            case PLAZA:
                if (x == 44 || x == 60 || z == 36 || z == 54) {
                    return "terracotta";
                }
                return Math.floorMod(x - 52, 4) == 0 || Math.floorMod(z - 45, 4) == 0 ? "cut_sandstone" : "smooth_sandstone";
            case SITE_A:
                return x % 5 == 1 || z % 5 == 0 ? "cut_sandstone" : "smooth_sandstone";
            case SITE_B:
                return h < 50 ? "smooth_sandstone" : h < 85 ? "sandstone" : "cut_sandstone";
            case FORT:
                return h < 75 ? "smooth_sandstone" : "cut_sandstone";
            case PIT:
                return h < 60 ? "smooth_sandstone" : h < 85 ? "stone_bricks" : "mossy_stone_bricks";
            case PASSAGE:
                return "smooth_sandstone";
            case COURT:
                return h < 70 ? "smooth_sandstone" : "sandstone";
            default:
                return h < 55 ? "sand" : h < 85 ? "sandstone" : "smooth_sandstone";
        }
    }

    // ---------------------------------------------------------------------------------------------- masonry

    private void styleWalls() {
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    if (!raw.get(((y - MIN_Y) * D + z) * W + x)) {
                        continue;
                    }
                    boolean side = false;
                    for (int d = 0; d < 4; d++) {
                        int nx = x + DX[d];
                        int nz = z + DZ[d];
                        side |= inside(nx, nz) ? bp.isAir(nx, y, nz) : y >= 1;
                    }
                    boolean building = floor[x][z] == MASS || y > floor[x][z];
                    String block;
                    if (y < 1 || !building) {
                        block = side ? pitBlock(x, y, z) : "sandstone";
                    } else if (y == top[x][z]) {
                        block = side ? bandBlock(style[x][z]) : roofBlock(x, z);
                    } else {
                        block = side ? wallBlock(style[x][z], x, y, z, top[x][z]) : "sandstone";
                    }
                    bp.set(x, y, z, block);
                }
            }
        }
    }

    private static String wallBlock(int st, int x, int y, int z, int roof) {
        int h = hash(x * 31 + y, z, 5) % 100;
        boolean base = y == 1;
        boolean band = roof >= 10 && y == 5;
        switch (st) {
            case SMOOTH:
                return base ? "sandstone" : band ? "cut_sandstone" : h < 8 ? "sandstone" : "smooth_sandstone";
            case MUD:
                return base ? "packed_mud" : h < 12 ? "packed_mud" : "mud_bricks";
            case PLASTER:
                return base ? "sandstone" : band ? "terracotta" : h < 6 ? "smooth_sandstone" : "white_terracotta";
            case TERRA:
                return base ? "smooth_sandstone" : band ? "cut_sandstone" : h < 8 ? "packed_mud" : "terracotta";
            case WALL:
                return base || band ? "cut_sandstone" : h < 10 ? "smooth_sandstone" : "sandstone";
            default:
                return base ? "cut_sandstone" : band ? "smooth_sandstone" : h < 8 ? "smooth_sandstone" : "sandstone";
        }
    }

    private static String bandBlock(int st) {
        switch (st) {
            case MUD:
                return "mud_bricks";
            case PLASTER:
                return "white_terracotta";
            case SAND:
                return "smooth_sandstone";
            default:
                return "cut_sandstone";
        }
    }

    private String roofBlock(int x, int z) {
        int h = hash(lot[x][z], 5, 9) % 4;
        if (h == 3) {
            return style[x][z] == MUD ? "packed_mud" : "sandstone";
        }
        return hash(x, z, 13) % 10 < 2 ? "sandstone" : "smooth_sandstone";
    }

    private static String pitBlock(int x, int y, int z) {
        if (Math.floorMod(y, 3) == 0) {
            return "cut_sandstone";
        }
        int h = hash(x * 17 + y, z, 21) % 100;
        if (y <= -3) {
            return h < 25 ? "mossy_stone_bricks" : h < 60 ? "stone_bricks" : "sandstone";
        }
        return h < 10 ? "smooth_sandstone" : "sandstone";
    }

    private void parapets() {
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                if (!roofed(x, z)) {
                    continue;
                }
                int t = top[x][z];
                boolean edge = false;
                for (int nx = x - 1; nx <= x + 1; nx++) {
                    for (int nz = z - 1; nz <= z + 1; nz++) {
                        edge |= !inside(nx, nz) || !roofed(nx, nz) || top[nx][nz] < t;
                    }
                }
                if (edge) {
                    String block = bandBlock(style[x][z]);
                    bp.set(x, t + 1, z, block);
                    if (style[x][z] == WALL && ((x + z) & 1) == 0) {
                        bp.set(x, t + 2, z, block);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- doors, windows

    private void facades() {
        for (int x = 1; x < W - 1; x++) {
            for (int z = 1; z < D - 1; z++) {
                if (floor[x][z] != MASS || style[x][z] == WALL) {
                    continue;
                }
                for (int d = 0; d < 4; d++) {
                    facade(x, z, d);
                }
            }
        }
    }

    /** Maybe puts a door or shuttered windows into the wall cell (x, z) that looks out towards d. */
    private void facade(int x, int z, int d) {
        int ox = x + DX[d];
        int oz = z + DZ[d];
        if (!street(ox, oz)) {
            return;
        }
        int f = floor[ox][oz];
        int lx = DZ[d];
        int lz = DX[d];
        for (int s = -1; s <= 1; s += 2) {
            int wx = x + lx * s;
            int wz = z + lz * s;
            if (!mass(wx, wz) || !street(wx + DX[d], wz + DZ[d]) || floor[wx + DX[d]][wz + DZ[d]] != f) {
                return;
            }
        }
        int bx = x - DX[d];
        int bz = z - DZ[d];
        if (!mass(bx, bz)) {
            return;
        }
        int roof = Math.min(top[x][z], top[bx][bz]);
        int along = (DX[d] != 0 ? z : x) + lot[x][z] * 3;
        String wood = hash(lot[x][z], 1, 31) % 3 == 0 ? "dark_oak" : "spruce";
        boolean timber = style[x][z] == MUD || style[x][z] == PLASTER || style[x][z] == TERRA;
        String lintel = timber ? "stripped_spruce_log[axis=" + (DX[d] != 0 ? "z" : "x") + "]" : "cut_sandstone";
        if (Math.floorMod(along, 7) == 3 && hash(x, z, 40 + d) % 3 != 0 && roof >= f + 5) {
            bp.set(x, f + 1, z, wood + "_door[facing=" + DIR[d] + ",half=lower]");
            bp.set(x, f + 2, z, wood + "_door[facing=" + DIR[d] + ",half=upper]");
            bp.set(x, f + 3, z, lintel);
        } else if (Math.floorMod(along, 4) == 1) {
            String shutter = wood + "_trapdoor[facing=" + DIR[d] + ",open=true]";
            if (roof >= f + 6 && hash(x, z, 50 + d) % 4 != 0) {
                bp.set(x, f + 3, z, shutter);
                bp.set(x, f + 4, z, shutter);
                bp.set(x, f + 5, z, lintel);
            }
            if (roof >= f + 10 && hash(x, z, 60 + d) % 4 != 0) {
                bp.set(x, f + 7, z, shutter);
                bp.set(x, f + 8, z, shutter);
            }
        }
    }

    /** Half a door left over where something was built across it turns back into wall. */
    private void fixDoors() {
        for (int x = 0; x < W; x++) {
            for (int z = 0; z < D; z++) {
                for (int y = MIN_Y; y <= MAX_Y; y++) {
                    String s = bp.get(x, y, z);
                    if (s == null || !s.contains("_door[")) {
                        continue;
                    }
                    String other = bp.get(x, s.contains("half=lower") ? y + 1 : y - 1, z);
                    if (other == null || !other.contains("_door[")) {
                        bp.set(x, y, z, "sandstone");
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------- roofs

    private void roofs() {
        for (int i = 0; i < lots.size(); i++) {
            int[] r = lots.get(i);
            int cx = (r[0] + r[2]) / 2;
            int cz = (r[1] + r[3]) / 2;
            if (lot[cx][cz] != i || style[cx][cz] == WALL) {
                continue;
            }
            int y = top[cx][cz] + 1;
            int h = hash(i, 3, 7) % 10;
            if (h < 3 && plainRoof(cx - 3, cz - 3, cx + 3, cz + 3)) {
                dome(cx, cz, y, 3);
            } else if (h < 3 && plainRoof(cx - 2, cz - 2, cx + 2, cz + 2)) {
                dome(cx, cz, y, 2);
            } else if (h < 6 && plainRoof(cx - 1, cz - 1, cx + 1, cz + 1)) {
                windcatcher(cx, cz, y);
            } else if (h < 8 && plainRoof(cx - 2, cz - 1, cx + 2, cz + 1)) {
                roofTerrace(cx, cz, y);
            }
        }
    }

    private boolean plainRoof(int x1, int z1, int x2, int z2) {
        if (!inside(x1, z1) || !inside(x2, z2)) {
            return false;
        }
        int t = top[x1][z1];
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (floor[x][z] != MASS || top[x][z] != t || !bp.isAir(x, t + 1, z)) {
                    return false;
                }
            }
        }
        return true;
    }

    private void dome(int cx, int cz, int y, int r) {
        for (int dy = 0; dy <= r; dy++) {
            double limit = r * r - dy * dy + 0.8;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz <= limit) {
                        bp.set(cx + dx, y + dy, cz + dz, "smooth_sandstone");
                    }
                }
            }
        }
        bp.set(cx, y + r + 1, cz, "sandstone_wall");
    }

    /** A wind tower: the chimney-like shaft that cools a desert house. */
    private void windcatcher(int cx, int cz, int y) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean corner = dx != 0 && dz != 0;
                for (int i = 0; i < 4; i++) {
                    bp.set(cx + dx, y + i, cz + dz, corner ? "cut_sandstone" : i < 2 || (dx == 0 && dz == 0) ? "sandstone" : "stripped_spruce_log");
                }
                bp.set(cx + dx, y + 4, cz + dz, "smooth_sandstone_slab");
            }
        }
    }

    private void roofTerrace(int cx, int cz, int y) {
        String colour = hash(cx, cz, 77) % 2 == 0 ? "red" : "cyan";
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 0; dz++) {
                bp.set(cx + dx, y, cz + dz, (dx == 0 ? "white" : colour) + "_carpet");
            }
        }
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                bp.set(cx + dx, y, cz + dz, "spruce_fence");
                bp.set(cx + dx, y + 1, cz + dz, "spruce_fence");
            }
        }
        awning(cx - 1, cz - 1, cx + 1, cz + 1, y + 2, colour, true);
        bp.set(cx - 2, y, cz - 1, "decorated_pot");
        bp.set(cx + 2, y, cz + 1, "barrel[facing=up]");
        bp.set(cx + 2, y, cz, "potted_cactus");
    }

    // ---------------------------------------------------------------------------------------------- furnishing

    private void decorate() {
        yard();
        northLane();
        alleys();
        siteA();
        market();
        plaza();
        fort();
        siteB();
        stepwell();
        courtyards();
        townGate();
    }

    private void yard() {
        tent(5, 32, 9, EAST, "red");
        tent(5, 51, 9, EAST, "orange");
        tent(18, 49, 22, WEST, "brown");
        bp.set(13, 1, 39, "campfire");
        bp.set(11, 1, 39, "stripped_spruce_log[axis=z]");
        bp.set(15, 1, 39, "stripped_spruce_log[axis=z]");
        bp.set(13, 1, 41, "stripped_spruce_log[axis=x]");
        palm(20, 33, 7);
        palm(6, 44, 6);
        palm(21, 57, 6);
        // somewhere to leave the camel
        for (int x = 17; x <= 20; x++) {
            bp.set(x, 1, 37, "spruce_fence");
        }
        bp.set(16, 1, 37, "spruce_sign[rotation=4]");
        bp.addSign(16, 1, 37, "parking");
        bp.set(18, 1, 38, "hay_block");
        bp.set(19, 1, 38, "hay_block");
        bp.set(20, 1, 38, "water_cauldron[level=3]");
        crates(5, 40, 2, 6, 40, 1, 5, 41, 1);
        crates(21, 30, 2, 22, 30, 1, 21, 31, 1);
        crates(9, 58, 1, 10, 58, 2, 10, 57, 1);
        wallBanner(22, 4, 41, WEST, "red");
        wallBanner(22, 4, 47, WEST, "red");
        wallSign(22, 2, 48, WEST, "camp");
    }

    private void northLane() {
        for (int x : new int[] {22, 36, 44, 56}) {
            archAcrossZ(x, 8, 14, 5);
        }
        wallBanner(21, 7, 11, WEST, "orange");
        wallBanner(57, 7, 11, EAST, "orange");
        stall(39, 8, 42, 9, NORTH, "lime", null);
        palm(18, 13, 6);
        palm(53, 9, 7);
        crates(33, 8, 2, 34, 8, 1, 33, 9, 1);
        crates(46, 14, 2, 45, 14, 1, 46, 13, 1);
        crates(58, 8, 1, 58, 9, 2);
    }

    private void alleys() {
        // north alley
        archAcrossX(22, 12, 16, 5);
        crates(12, 18, 2, 13, 18, 1);
        crates(16, 26, 1, 16, 27, 2);

        // potters' alley
        archAcrossX(21, 28, 31, 5);
        archAcrossX(35, 32, 35, 5);
        for (int z : new int[] {17, 18, 23}) {
            bp.set(28, 1, z, "decorated_pot");
        }
        bp.set(31, 1, 16, "terracotta");
        bp.set(31, 2, 16, "orange_terracotta");
        bp.set(31, 1, 17, "brown_terracotta");
        bp.set(31, 2, 17, "potted_dead_bush");
        bp.set(35, 1, 26, "blast_furnace[facing=west]");
        bp.set(35, 1, 25, "clay");
        bp.set(35, 2, 25, "decorated_pot");
        bp.set(32, 1, 31, "decorated_pot");
        bp.set(32, 1, 32, "terracotta");
        bp.set(32, 2, 32, "flower_pot");
        bp.set(35, 1, 38, "yellow_terracotta");
        bp.set(35, 1, 39, "decorated_pot");

        // spice alley
        archAcrossX(19, 48, 51, 5);
        archAcrossX(31, 48, 51, 5);
        wallBanner(49, 6, 20, SOUTH, "yellow");
        wallBanner(50, 6, 30, NORTH, "red");
        bp.set(48, 1, 27, "orange_concrete_powder");
        bp.set(48, 1, 28, "yellow_concrete_powder");
        bp.set(48, 2, 28, "red_concrete_powder");
        bp.set(48, 1, 29, "brown_concrete_powder");
        crates(51, 17, 2, 51, 18, 1);
        wallSign(51, 2, 21, WEST, "spice");

        // covered way into A and the gate under the caravanserai wall
        for (int x : new int[] {54, 58, 62}) {
            bp.set(x, 5, 24 + (x / 4) % 2, "ochre_froglight");
        }
        crates(57, 23, 2, 58, 23, 1);
        bp.set(63, 5, 11, "ochre_froglight");
        for (int z = 9; z <= 13; z++) {
            bp.set(61, 4, z, "iron_bars");
            bp.set(65, 4, z, "iron_bars");
        }

        // yard gate
        bp.set(24, 5, 44, "ochre_froglight");
        for (int z = 42; z <= 46; z++) {
            bp.set(23, 4, z, "iron_bars");
        }

        // fort lane and the two short ways out of the fort
        crates(65, 43, 2, 65, 44, 2, 66, 43, 1);
        crates(68, 47, 2, 68, 46, 2, 69, 47, 1);
        archAcrossX(33, 80, 85, 5);
        archAcrossX(57, 80, 85, 5);
        wallBanner(82, 6, 32, NORTH, "blue");
        wallBanner(83, 6, 58, SOUTH, "blue");

        // dyers' alley
        archAcrossX(58, 50, 54, 5);
        bp.set(50, 1, 56, "water_cauldron[level=3]");
        bp.set(50, 1, 57, "water_cauldron[level=3]");
        bp.set(54, 1, 55, "magenta_wool");
        bp.set(54, 2, 55, "yellow_wool");
        bp.set(54, 1, 56, "light_blue_wool");
        wallBanner(50, 4, 55, EAST, "magenta");
        wallBanner(54, 4, 57, WEST, "yellow");
        wallBanner(54, 4, 58, WEST, "lime");
        wallBanner(51, 6, 57, NORTH, "cyan");
        wallBanner(53, 6, 57, NORTH, "pink");

        // south alley
        archAcrossX(64, 12, 16, 5);
        crates(12, 62, 2, 13, 62, 1);
        crates(16, 67, 1, 16, 68, 2);
        wallSign(17, 2, 69, SOUTH, "stepwell");
    }

    private void siteA() {
        // the galleries along the north and east walls, with a walkable roof
        for (int x = 66; x <= 96; x++) {
            for (int z = 5; z <= 25; z++) {
                if (z <= 8 || x >= 93) {
                    bp.set(x, 5, z, "smooth_sandstone");
                }
            }
        }
        for (int x = 69; x <= 93; x += 4) {
            pillar(x, 8, 1, 4);
            if (x < 93) {
                bp.set(x + 1, 4, 8, "sandstone_stairs[facing=west,half=top]");
            }
            bp.set(x - 1, 4, 8, "sandstone_stairs[facing=east,half=top]");
        }
        for (int z = 12; z <= 24; z += 4) {
            pillar(93, z, 1, 4);
            bp.set(93, 4, z - 1, "sandstone_stairs[facing=south,half=top]");
            if (z < 24) {
                bp.set(93, 4, z + 1, "sandstone_stairs[facing=north,half=top]");
            }
        }
        for (int x = 66; x <= 93; x++) {
            bp.set(x, 6, 8, "sandstone_wall");
        }
        for (int z = 9; z <= 25; z++) {
            bp.set(93, 6, z, "sandstone_wall");
        }
        for (int i = 1; i <= 5; i++) {
            for (int x = 94; x <= 96; x++) {
                for (int y = 1; y < i; y++) {
                    bp.set(x, y, 31 - i, "cut_sandstone");
                }
                bp.set(x, i, 31 - i, "sandstone_stairs[facing=north]");
            }
        }
        for (int x = 71; x <= 87; x += 8) {
            bp.set(x, 4, 6, "lantern[hanging=true]");
        }
        bp.set(95, 4, 10, "lantern[hanging=true]");
        bp.set(95, 4, 18, "lantern[hanging=true]");

        // the marked ground in the middle
        frame(75, 13, 87, 23, 0, "cut_red_sandstone");
        floorLetter(GLYPH_A, 79, 16, 0);
        wallLetter(GLYPH_A, 65, 20, 0, -1, 3);
        wallSign(66, 2, 14, EAST, "site_a");

        crates(70, 16, 2, 70, 17, 2, 71, 16, 1, 71, 17, 1, 70, 18, 1);
        crates(76, 14, 2, 77, 14, 1, 76, 15, 1);
        crates(86, 21, 1, 86, 22, 2, 87, 22, 1);
        crates(79, 27, 2, 79, 28, 1, 78, 28, 1);
        crates(88, 26, 2, 88, 27, 1, 89, 27, 1);
        crates(68, 28, 2, 68, 29, 1);
        bp.set(67, 1, 31, "hay_block");
        bp.set(67, 2, 31, "hay_block");
        bp.set(68, 1, 31, "hay_block");
        bp.set(69, 1, 31, "water_cauldron[level=3]");
        palm(73, 29, 7);
    }

    private void market() {
        stall(27, 42, 30, 42, NORTH, "red", "sand");
        stall(28, 46, 31, 46, SOUTH, "yellow", null);
        stall(33, 48, 33, 52, WEST, "light_blue", null);
        stall(38, 49, 40, 49, NORTH, "orange", "kebab");
        stall(41, 53, 43, 53, SOUTH, "red", null);
        for (int x = 31; x <= 34; x++) {
            bp.set(x, 1, 44, x % 2 == 0 ? "red_carpet" : "orange_carpet");
        }
        bp.set(35, 6, 42, AIR);
        archAcrossX(50, 33, 37, 5);
    }

    private void plaza() {
        well(52, 45);
        stall(44, 36, 47, 37, NORTH, "red", null);
        stall(54, 36, 58, 37, NORTH, "cyan", null);
        stall(44, 39, 45, 44, WEST, "yellow", null);
        stall(56, 53, 59, 54, SOUTH, "orange", null);
        stall(59, 49, 60, 52, EAST, "purple", null);
        stall(59, 38, 60, 41, EAST, "lime", null);
        crates(47, 47, 2, 48, 47, 1, 47, 48, 1);
        crates(56, 41, 2, 57, 41, 1, 56, 40, 1);
        palm(56, 50, 7);
        minaret(64, 39);
    }

    private void fort() {
        bp.set(84, 1, 45, "chiseled_sandstone");
        for (int y = 2; y <= 6; y++) {
            bp.set(84, y, 45, "stripped_spruce_log");
        }
        bp.set(84, 7, 45, "lantern");
        wallBanner(84, 6, 44, NORTH, "blue");
        wallBanner(84, 6, 46, SOUTH, "blue");
        frame(78, 40, 90, 50, 0, "cyan_terracotta");
        for (int z = 38; z <= 42; z += 2) {
            bp.set(96, 1, z, "hay_block");
            bp.set(96, 2, z, "target");
        }
        bp.set(74, 1, 36, "grindstone[face=floor]");
        bp.set(75, 1, 36, "anvil");
        bp.set(76, 1, 36, "smithing_table");
        bp.set(77, 1, 36, "barrel[facing=up]");
        for (int x = 88; x <= 91; x++) {
            bp.set(x, 1, 54, "spruce_stairs[facing=south]");
        }
        crates(74, 49, 2, 75, 49, 1, 74, 50, 1);
        crates(79, 37, 2, 78, 37, 1);
        crates(87, 53, 1, 86, 53, 2);
        crates(93, 50, 2, 94, 50, 1, 93, 51, 1);
        wallBanner(73, 4, 42, EAST, "blue");
        wallBanner(73, 4, 48, EAST, "blue");
        wallSign(73, 2, 50, EAST, "fort");
    }

    private void siteB() {
        // the terrace along the east wall, with two pergolas
        for (int z = 60; z <= 83; z++) {
            bp.set(90, 1, z, "cut_sandstone");
        }
        for (int z = 70; z <= 73; z++) {
            bp.set(89, 1, z, "sandstone_stairs[facing=east]");
        }
        pergola(91, 62, 95, 67);
        pergola(91, 76, 95, 81);
        for (int z = 63; z <= 66; z++) {
            bp.set(96, 2, z, "spruce_stairs[facing=east]");
        }
        bp.set(91, 2, 60, "potted_cactus");
        bp.set(91, 2, 83, "potted_cactus");

        // the pool
        for (int x = 68; x <= 76; x++) {
            for (int z = 62; z <= 68; z++) {
                boolean rimX = x == 68 || x == 76;
                boolean rimZ = z == 62 || z == 68;
                if (rimX || rimZ) {
                    bp.set(x, 1, z, rimX && rimZ ? "chiseled_sandstone" : "cut_sandstone");
                } else {
                    bp.set(x, 0, z, "prismarine_bricks");
                    bp.set(x, 1, z, "water");
                }
            }
        }
        bp.set(72, 1, 65, "chiseled_sandstone");
        bp.set(72, 2, 65, "chiseled_sandstone");
        bp.set(72, 3, 65, "water");
        bp.set(70, 2, 64, "lily_pad");
        bp.set(74, 2, 66, "lily_pad");
        bp.set(75, 2, 63, "lily_pad");

        // the marked ground
        frame(77, 71, 87, 80, 0, "cut_red_sandstone");
        floorLetter(GLYPH_B, 80, 73, 0);
        wallLetter(GLYPH_B, 72, 59, 1, 0, 3);
        wallSign(78, 2, 60, SOUTH, "site_b");

        // the trench the tunnel comes up through
        for (int x = 64; x <= 68; x++) {
            bp.set(x, 1, 75, "sandstone_wall");
            bp.set(x, 1, 80, "sandstone_wall");
        }
        bp.set(61, 0, 77, "ochre_froglight");

        // a strip of garden under the south wall
        String[] plants = {"grass", "fern", "poppy", "grass", "flowering_azalea", "dandelion", "grass", "azure_bluet"};
        for (int x = 72; x <= 87; x++) {
            bp.set(x, 0, 83, "grass_block");
            bp.set(x, 1, 83, plants[hash(x, 83, 3) % plants.length]);
        }
        palm(78, 62, 7);
        palm(66, 72, 6);
        palm(88, 82, 7);
        palm(70, 82, 6);

        crates(71, 77, 2, 71, 78, 1);
        crates(66, 63, 1, 67, 63, 2, 66, 64, 1);
        crates(86, 72, 2, 86, 73, 1, 85, 72, 1);
        crates(79, 61, 2, 79, 62, 1, 78, 61, 1);
        crates(87, 61, 2, 87, 62, 1);
        crates(88, 70, 1);
    }

    private void stepwell() {
        // a covered walk along the north wall
        for (int x = 25; x <= 47; x++) {
            for (int z = 64; z <= 67; z++) {
                bp.set(x, 0, z, "smooth_sandstone");
            }
        }
        for (int x = 27; x <= 47; x += 4) {
            pillar(x, 67, -4, -1);
            if (x < 47) {
                bp.set(x + 1, -1, 67, "sandstone_stairs[facing=west,half=top]");
            }
            bp.set(x - 1, -1, 67, "sandstone_stairs[facing=east,half=top]");
        }
        for (int x = 29; x <= 45; x += 8) {
            bp.set(x, -1, 65, "lantern[hanging=true]");
        }

        // the water
        for (int x = 36; x <= 47; x++) {
            for (int z = 70; z <= 77; z++) {
                if (x == 36 || x == 47 || z == 70 || z == 77) {
                    bp.set(x, -5, z, hash(x, z, 4) % 3 == 0 ? "mossy_stone_bricks" : "stone_bricks");
                } else {
                    bp.set(x, -6, z, "mossy_stone_bricks");
                    bp.set(x, -5, z, "water");
                }
            }
        }
        for (int x = 36; x <= 47; x += 11) {
            for (int z = 70; z <= 77; z += 7) {
                pillar(x, z, -4, 0);
                bp.set(x, 1, z, "lantern");
            }
        }
        bp.set(39, -4, 72, "lily_pad");
        bp.set(43, -4, 75, "lily_pad");
        bp.set(45, -4, 72, "lily_pad");

        // things left on the steps, and lichen on the damp walls
        bp.set(28, -2, 80, "decorated_pot");
        bp.set(29, -2, 80, "decorated_pot");
        bp.set(40, -2, 80, "potted_fern");
        bp.set(52, -2, 80, "decorated_pot");
        bp.set(33, -3, 79, "moss_carpet");
        bp.set(34, -3, 79, "moss_carpet");
        bp.set(48, -3, 79, "moss_carpet");
        for (int z = 68; z <= 78; z++) {
            if (hash(58, z, 6) % 3 == 0 && !walkable(59, z)) {
                bp.set(58, -2 - hash(z, 1, 2) % 2, z, "glow_lichen[east=true]");
            }
        }
        for (int x = 26; x <= 57; x++) {
            if (hash(x, 80, 6) % 4 == 0 && x >= 55) {
                bp.set(x, -3, 80, "glow_lichen[south=true]");
            }
        }
        crates(30, 72, 2, 31, 72, 1, 30, 73, 1);
        crates(52, 68, 1, 53, 68, 2);
        crates(55, 74, 2, 55, 75, 1);
    }

    private void courtyards() {
        bp.set(22, 0, 22, "grass_block");
        palm(22, 22, 6);
        bp.set(20, 1, 19, "decorated_pot");
        bp.set(24, 1, 25, "potted_cactus");

        for (int x = 41; x <= 42; x++) {
            for (int z = 20; z <= 21; z++) {
                bp.set(x, 0, z, "water");
            }
        }
        bp.set(41, 1, 20, "lily_pad");
        bp.set(39, 1, 18, "decorated_pot");
        bp.set(44, 1, 22, "barrel[facing=up]");

        for (int z = 31; z <= 37; z++) {
            bp.set(40, 0, z, "grass_block");
            bp.set(40, 1, z, z % 3 == 0 ? "flowering_azalea" : "grass");
        }

        bp.set(28, 0, 55, "grass_block");
        palm(28, 55, 7);
        bp.set(26, 1, 50, "spruce_stairs[facing=west]");
        bp.set(26, 1, 51, "spruce_stairs[facing=west]");
        for (int z = 57; z <= 59; z++) {
            bp.set(28, 1, z, "red_carpet");
            bp.set(29, 1, z, "white_carpet");
        }

        bp.set(42, 0, 59, "grass_block");
        palm(42, 59, 6);
        bp.set(38, 1, 57, "decorated_pot");
        bp.set(46, 1, 61, "barrel[facing=up]");
    }

    /** The big west gate - shut, since the caravan is already inside. */
    private void townGate() {
        for (int z = 42; z <= 46; z++) {
            for (int y = 1; y <= 5; y++) {
                boolean frame = z == 42 || z == 46 || y == 5;
                bp.set(0, y, z, frame ? "cut_sandstone" : "dark_oak_planks");
            }
        }
        bp.set(0, 5, 44, "chiseled_sandstone");
    }

    // ---------------------------------------------------------------------------------------------- props

    /** Each triple is x, z and the height of one stack. */
    private void crates(int... spec) {
        for (int i = 0; i < spec.length; i += 3) {
            int x = spec[i];
            int z = spec[i + 1];
            int f = floor[x][z];
            for (int n = 1; n <= spec[i + 2]; n++) {
                int h = hash(x, z, 90 + n) % 10;
                boolean last = n == spec[i + 2];
                bp.set(x, f + n, z, h < 4 ? "barrel[facing=up]" : h < 6 ? "barrel[facing=east]" : h < 8 ? "spruce_planks"
                        : h == 8 ? "hay_block" : last ? "composter" : "barrel[facing=south]");
            }
        }
    }

    private void palm(int x, int z, int height) {
        int f = floor[x][z];
        for (int y = f + 1; y <= f + height; y++) {
            bp.set(x, y, z, "jungle_log");
        }
        int t = f + height;
        leaf(x, t + 1, z);
        for (int d = 0; d < 4; d++) {
            leaf(x + DX[d], t + 1, z + DZ[d]);
            leaf(x + DX[d] * 2, t + 1, z + DZ[d] * 2);
            leaf(x + DX[d] * 3, t, z + DZ[d] * 3);
            leaf(x + DX[d] * 4, t - 1, z + DZ[d] * 4);
            int e = (d + 1) % 4;
            leaf(x + DX[d] + DX[e], t, z + DZ[d] + DZ[e]);
            leaf(x + (DX[d] + DX[e]) * 2, t - 1, z + (DZ[d] + DZ[e]) * 2);
        }
        int side = hash(x, z, 8) % 4;
        if (bp.isAir(x + DX[side], t, z + DZ[side])) {
            bp.set(x + DX[side], t, z + DZ[side], "cocoa[age=2,facing=" + DIR[(side + 2) % 4] + "]");
        }
    }

    private void leaf(int x, int y, int z) {
        if (bp.isAir(x, y, z)) {
            bp.set(x, y, z, "jungle_leaves[persistent=true]");
        }
    }

    private void pillar(int x, int z, int y1, int y2) {
        for (int y = y1; y < y2; y++) {
            bp.set(x, y, z, "cut_sandstone");
        }
        bp.set(x, y2, z, "chiseled_sandstone");
    }

    /** A striped cloth roof: trapdoors carrying carpet. */
    private void awning(int x1, int z1, int x2, int z2, int y, String colour, boolean stripesAlongX) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                bp.set(x, y, z, "spruce_trapdoor[half=top]");
                bp.set(x, y + 1, z, ((stripesAlongX ? x - x1 : z - z1) % 2 == 0 ? colour : "white") + "_carpet");
            }
        }
    }

    /** A market stall against the wall on its {@code wall} side: goods by the wall, a counter in front if it is two deep. */
    private void stall(int x1, int z1, int x2, int z2, int wall, String colour, String signKey) {
        boolean alongX = DX[wall] == 0;
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                int f = floor[x][z];
                int depth = switch (wall) {
                    case NORTH -> z - z1;
                    case SOUTH -> z2 - z;
                    case WEST -> x - x1;
                    default -> x2 - x;
                };
                boolean end = alongX ? x == x1 || x == x2 : z == z1 || z == z2;
                if (depth == 0) {
                    goods(x, z);
                } else if (end) {
                    for (int y = f + 1; y <= f + 3; y++) {
                        bp.set(x, y, z, "spruce_fence");
                    }
                } else {
                    bp.set(x, f + 1, z, "spruce_slab[type=top]");
                    int h = hash(x, z, 61) % 5;
                    if (h < 2) {
                        bp.set(x, f + 2, z, h == 0 ? "potted_red_tulip" : "lantern");
                    }
                }
            }
        }
        int f = floor[x1][z1];
        awning(x1, z1, x2, z2, f + 4, colour, alongX);
        if (signKey != null) {
            int sx = alongX ? (x1 + x2) / 2 : wall == WEST ? x1 : x2;
            int sz = alongX ? (wall == NORTH ? z1 : z2) : (z1 + z2) / 2;
            wallSign(sx, f + 3, sz, (wall + 2) % 4, signKey);
        }
    }

    private void goods(int x, int z) {
        int f = floor[x][z];
        switch (hash(x, z, 60) % 9) {
            case 0 -> {
                bp.set(x, f + 1, z, "barrel[facing=up]");
                bp.set(x, f + 2, z, "barrel[facing=up]");
            }
            case 1 -> {
                bp.set(x, f + 1, z, "barrel[facing=up]");
                bp.set(x, f + 2, z, "potted_cactus");
            }
            case 2 -> bp.set(x, f + 1, z, "hay_block");
            case 3 -> bp.set(x, f + 1, z, "melon");
            case 4 -> bp.set(x, f + 1, z, "pumpkin");
            case 5 -> bp.set(x, f + 1, z, "decorated_pot");
            case 6 -> {
                bp.set(x, f + 1, z, "barrel[facing=up]");
                bp.set(x, f + 2, z, "lantern");
            }
            case 7 -> bp.set(x, f + 1, z, "composter");
            default -> {
                bp.set(x, f + 1, z, "spruce_planks");
                bp.set(x, f + 2, z, "decorated_pot");
            }
        }
    }

    /** A tent five blocks wide (along z) with its mouth towards {@code mouth} (east or west). */
    private void tent(int x1, int z1, int x2, int mouth, String colour) {
        int back = mouth == EAST ? x1 : x2;
        for (int x = x1; x <= x2; x++) {
            String cloth = ((x - x1) % 2 == 0 ? colour : "white") + "_wool";
            for (int y = 1; y <= 2; y++) {
                bp.set(x, y, z1, cloth);
                bp.set(x, y, z1 + 4, cloth);
            }
            bp.set(x, 3, z1 + 1, cloth);
            bp.set(x, 3, z1 + 3, cloth);
            bp.set(x, 4, z1 + 2, cloth);
            if (x != back) {
                for (int z = z1 + 1; z <= z1 + 3; z++) {
                    bp.set(x, 1, z, (z == z1 + 2 ? colour : "white") + "_carpet");
                }
            }
        }
        for (int z = z1 + 1; z <= z1 + 3; z++) {
            bp.set(back, 1, z, "white_wool");
            bp.set(back, 2, z, "white_wool");
        }
        bp.set(back, 3, z1 + 2, "white_wool");
        int inner = mouth == EAST ? back + 1 : back - 1;
        bp.set(inner, 1, z1 + 1, "barrel[facing=up]");
        bp.set(inner, 2, z1 + 1, "lantern");
    }

    private void pergola(int x1, int z1, int x2, int z2) {
        int f = floor[x1][z1];
        for (int x = x1; x <= x2; x += x2 - x1) {
            for (int z = z1; z <= z2; z += z2 - z1) {
                for (int y = f + 1; y <= f + 3; y++) {
                    bp.set(x, y, z, "spruce_fence");
                }
            }
        }
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                boolean rim = x == x1 || x == x2 || z == z1 || z == z2;
                if (rim) {
                    bp.set(x, f + 4, z, "spruce_slab");
                } else if (hash(x, z, 15) % 3 != 0) {
                    bp.set(x, f + 4, z, "jungle_leaves[persistent=true]");
                }
            }
        }
    }

    private void well(int cx, int cz) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                boolean corner = dx != 0 && dz != 0;
                if (dx == 0 && dz == 0) {
                    bp.set(cx, 0, cz, "stone_bricks");
                    bp.set(cx, 1, cz, "water");
                } else {
                    bp.set(cx + dx, 1, cz + dz, corner ? "chiseled_sandstone" : "cut_sandstone");
                }
                if (corner) {
                    bp.set(cx + dx, 2, cz + dz, "spruce_fence");
                    bp.set(cx + dx, 3, cz + dz, "spruce_fence");
                }
                bp.set(cx + dx, 4, cz + dz, dx == 0 && dz == 0 ? "spruce_planks" : "spruce_slab");
            }
        }
        bp.set(cx, 3, cz, "chain");
        bp.set(cx, 2, cz, "chain");
        wallSign(cx, 1, cz - 2, NORTH, "well");
    }

    /** The tower over the plaza: a square shaft, a gallery and a green copper cap. */
    private void minaret(int cx, int cz) {
        int base = top[cx][cz] + 1;
        for (int y = base; y <= 14; y++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    boolean corner = Math.abs(dx) == 2 && Math.abs(dz) == 2;
                    bp.set(cx + dx, y, cz + dz, corner || y % 4 == 2 ? "cut_sandstone" : "sandstone");
                }
            }
        }
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                boolean rim = Math.abs(dx) == 3 || Math.abs(dz) == 3;
                bp.set(cx + dx, 15, cz + dz, rim ? "smooth_sandstone_slab[type=top]" : "smooth_sandstone");
                if (rim) {
                    bp.set(cx + dx, 16, cz + dz, "sandstone_wall");
                }
            }
        }
        for (int y = 16; y <= 20; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    boolean slit = y >= 17 && y <= 18 && (dx == 0) != (dz == 0);
                    bp.set(cx + dx, y, cz + dz, slit ? "spruce_trapdoor[open=true,facing="
                            + DIR[dx == 0 ? (dz < 0 ? NORTH : SOUTH) : (dx < 0 ? WEST : EAST)] + "]" : "smooth_sandstone");
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) + Math.abs(dz) <= 3) {
                    bp.set(cx + dx, 21, cz + dz, "oxidized_cut_copper");
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                bp.set(cx + dx, 22, cz + dz, "oxidized_cut_copper");
            }
        }
        bp.set(cx, 23, cz, "oxidized_cut_copper");
        bp.set(cx, 24, cz, "lightning_rod");
    }

    /** A buttress arch over a street running north-south: it spans x1..x2 at the given z and springs at y. */
    private void archAcrossX(int z, int x1, int x2, int y) {
        arch(x1, z, 1, 0, x2 - x1 + 1, y);
    }

    /** A buttress arch over a street running east-west: it spans z1..z2 at the given x and springs at y. */
    private void archAcrossZ(int x, int z1, int z2, int y) {
        arch(x, z1, 0, 1, z2 - z1 + 1, y);
    }

    private void arch(int x, int z, int sx, int sz, int n, int y) {
        String near = "sandstone_stairs[half=top,facing=" + (sx != 0 ? "west" : "north") + "]";
        String far = "sandstone_stairs[half=top,facing=" + (sx != 0 ? "east" : "south") + "]";
        int ex = x + sx * (n - 1);
        int ez = z + sz * (n - 1);
        bp.set(x, y, z, near);
        bp.set(ex, y, ez, far);
        int beam = y + 1;
        if (n >= 6) {
            bp.set(x, y + 1, z, "cut_sandstone");
            bp.set(ex, y + 1, ez, "cut_sandstone");
            bp.set(x + sx, y + 1, z + sz, near);
            bp.set(ex - sx, y + 1, ez - sz, far);
            beam = y + 2;
        }
        for (int i = 0; i < n; i++) {
            bp.set(x + sx * i, beam, z + sz * i, "cut_sandstone");
        }
        bp.set(x + sx * (n / 2), beam - 1, z + sz * (n / 2), "lantern[hanging=true]");
    }

    /** Outlines a rectangle of floor. */
    private void frame(int x1, int z1, int x2, int z2, int y, String block) {
        for (int x = x1; x <= x2; x++) {
            for (int z = z1; z <= z2; z++) {
                if (x == x1 || x == x2 || z == z1 || z == z2) {
                    bp.set(x, y, z, block);
                }
            }
        }
    }

    /** Paints a letter on the ground, to be read looking north. */
    private void floorLetter(String[] glyph, int x, int z, int y) {
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 5; col++) {
                if (glyph[row].charAt(col) == '#') {
                    bp.set(x + col, y, z + row, "red_terracotta");
                }
            }
        }
    }

    /** Paints a letter on a plastered panel of wall; (x, z) is its left column and (stepX, stepZ) leads to the right. */
    private void wallLetter(String[] glyph, int x, int z, int stepX, int stepZ, int bottom) {
        for (int col = -1; col <= 5; col++) {
            for (int row = -1; row <= 5; row++) {
                boolean on = col >= 0 && col < 5 && row >= 0 && row < 5 && glyph[row].charAt(col) == '#';
                bp.set(x + stepX * col, bottom + 4 - row, z + stepZ * col, on ? "red_terracotta" : "smooth_sandstone");
            }
        }
    }

    /** A sign in the given cell, hung on the wall behind it and facing {@code facing}. */
    private void wallSign(int x, int y, int z, int facing, String key) {
        support(x, y, z, facing);
        bp.set(x, y, z, "spruce_wall_sign[facing=" + DIR[facing] + "]");
        bp.addSign(x, y, z, key);
    }

    private void wallBanner(int x, int y, int z, int facing, String colour) {
        support(x, y, z, facing);
        bp.set(x, y, z, colour + "_wall_banner[facing=" + DIR[facing] + "]");
    }

    /** Makes sure the block a sign or banner hangs on is solid wall rather than a shutter or door. */
    private void support(int x, int y, int z, int facing) {
        int bx = x - DX[facing];
        int bz = z - DZ[facing];
        String behind = bp.get(bx, y, bz);
        if (behind != null && (behind.contains("door[") || AIR.equals(behind))) {
            bp.set(bx, y, bz, "sandstone");
        }
    }

    // ---------------------------------------------------------------------------------------------- helpers

    private static boolean inside(int x, int z) {
        return x >= 0 && x < W && z >= 0 && z < D;
    }

    private boolean walkable(int x, int z) {
        return inside(x, z) && floor[x][z] != MASS;
    }

    private boolean mass(int x, int z) {
        return inside(x, z) && floor[x][z] == MASS;
    }

    /** A building stands here, or over the passage here. */
    private boolean roofed(int x, int z) {
        return floor[x][z] == MASS || ceil[x][z] != SKY;
    }

    /** Level open ground a house front can look onto. */
    private boolean street(int x, int z) {
        return walkable(x, z) && ceil[x][z] == SKY && stair[x][z] < 0 && zone[x][z] != PIT;
    }

    private static int hash(int a, int b, int c) {
        int h = a * 0x9E3779B1 ^ b * 0x85EBCA6B ^ c * 0xC2B2AE35;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return h & 0x7fffffff;
    }
}
