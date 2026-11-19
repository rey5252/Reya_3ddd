import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import javax.imageio.ImageIO;

import com.reya.sandbazaar.map.BazaarLayout;
import com.reya.sandbazaar.map.Blueprint;

/**
 * Draws the arena without the game and walks it like a player would.
 *
 * <pre>
 * javac -d out src/main/java/com/reya/sandbazaar/map/*.java tools/Preview.java
 * java -cp out Preview out
 * </pre>
 *
 * Writes top.png, iso_se.png, iso_nw.png, reach.png and palette.txt into the given directory, and fails when a
 * teleport point cannot be reached, a place can be entered but not left, or a roof can be climbed.
 */
public final class Preview {
    private static final int FULL = 0;
    private static final int NONE = 1;      // nothing to bump into
    private static final int TALL = 2;      // fences and walls: too high to hop onto
    private static final int SMALL = 3;     // in the way, but not something to stand on

    private static Blueprint bp;
    private static int w;
    private static int d;

    public static void main(String[] args) throws Exception {
        File out = new File(args.length > 0 ? args[0] : ".");
        out.mkdirs();
        bp = BazaarLayout.build();
        w = bp.sizeX;
        d = bp.sizeZ;
        Files.write(new File(out, "palette.txt").toPath(), new TreeSet<>(bp.palette()));
        ImageIO.write(top(), "png", new File(out, "top.png"));
        ImageIO.write(iso(false, 0, 0, w - 1, d - 1, bp.maxY, 8), "png", new File(out, "iso_se.png"));
        ImageIO.write(iso(true, 0, 0, w - 1, d - 1, bp.maxY, 8), "png", new File(out, "iso_nw.png"));
        if (args.length >= 5) {
            int cut = args.length >= 6 ? Integer.parseInt(args[5]) : bp.maxY;
            boolean flip = args.length >= 7 && args[6].equals("nw");
            ImageIO.write(iso(flip, Integer.parseInt(args[1]), Integer.parseInt(args[2]), Integer.parseInt(args[3]),
                    Integer.parseInt(args[4]), cut, 20), "png", new File(out, "crop.png"));
            return;
        }
        boolean ok = walk(new File(out, "reach.png"));
        System.out.println(bp.palette().size() + " block states, " + bp.signs().size() + " signs");
        if (!ok) {
            System.exit(1);
        }
    }

    // ------------------------------------------------------------------------------------------ what blocks are

    private static String name(String state) {
        int i = state.indexOf('[');
        return i < 0 ? state : state.substring(0, i);
    }

    private static int kind(String state) {
        if (state == null) {
            return FULL;
        }
        String n = name(state);
        if (n.equals("air") || n.equals("water") || n.endsWith("_carpet") || n.endsWith("_sign") || n.endsWith("_banner")
                || n.equals("grass") || n.equals("fern") || n.equals("poppy") || n.equals("dandelion") || n.equals("azure_bluet")
                || n.equals("glow_lichen") || n.equals("lily_pad")) {
            return NONE;
        }
        if (n.endsWith("_fence") || n.endsWith("_wall")) {
            return TALL;
        }
        if (n.equals("lantern") || n.equals("chain") || n.equals("iron_bars") || n.startsWith("potted_") || n.equals("flower_pot")
                || n.equals("cocoa") || n.equals("lightning_rod") || n.equals("decorated_pot")) {
            return SMALL;
        }
        return FULL;
    }

    private static int kind(int x, int y, int z) {
        if (y > bp.maxY) {
            return NONE;
        }
        if (x < 0 || x >= w || z < 0 || z >= d) {
            return y >= 1 ? NONE : FULL;
        }
        return kind(bp.get(x, y, z));
    }

    private static final Map<String, Color> COLOURS = new HashMap<>();

    static {
        String[] table = {
            "sand", "DBCFA3", "sandstone", "D8CB9B", "smooth_sandstone", "DFD3A9", "cut_sandstone", "D2C48F",
            "chiseled_sandstone", "CFC08A", "sandstone_stairs", "D4C795", "sandstone_wall", "D4C795",
            "smooth_sandstone_slab", "DFD3A9", "cut_red_sandstone", "BA6321", "coarse_dirt", "77553B",
            "mud_bricks", "89674F", "packed_mud", "8E6A4F", "terracotta", "985E43", "white_terracotta", "D1B2A1",
            "red_terracotta", "8F3D2E", "orange_terracotta", "A15325", "brown_terracotta", "4D3323",
            "yellow_terracotta", "BA8523", "cyan_terracotta", "565B5B", "stone_bricks", "7A7A7A",
            "mossy_stone_bricks", "737A69", "prismarine_bricks", "63AB9E", "water", "3F76E4", "grass_block", "7CBD6B",
            "jungle_log", "554419", "jungle_leaves", "3E9E2B", "spruce_planks", "735531", "spruce_slab", "735531",
            "spruce_fence", "735531", "spruce_stairs", "735531", "spruce_trapdoor", "67502E", "dark_oak_trapdoor", "4A3218",
            "spruce_door", "6A5030", "dark_oak_door", "4C331A", "dark_oak_planks", "432B14", "stripped_spruce_log", "73593A",
            "barrel", "8C6A3F", "hay_block", "A68B0C", "composter", "70482A", "melon", "6F9118", "pumpkin", "C57618",
            "decorated_pot", "7A3D2D", "campfire", "E08A1C", "lantern", "F2C649", "ochre_froglight", "F7E6A0",
            "oxidized_cut_copper", "4F997E", "lightning_rod", "C46D52", "iron_bars", "8A8C8B", "chain", "3E4453",
            "target", "E2AAA0", "anvil", "444444", "grindstone", "8E8E8E", "smithing_table", "39393F",
            "blast_furnace", "505050", "clay", "A0A6B3", "glow_lichen", "70837A", "lily_pad", "208030",
            "moss_carpet", "596E2D", "flowering_azalea", "6E8A3C", "water_cauldron", "4A4B4B",
            "white", "E9ECEC", "orange", "F07613", "magenta", "BD44B3", "light_blue", "3AAFD9", "yellow", "F8C627",
            "lime", "70B919", "pink", "ED8DAC", "cyan", "158991", "purple", "792AAC", "blue", "35399D",
            "brown", "724728", "red", "A12722",
        };
        for (int i = 0; i < table.length; i += 2) {
            COLOURS.put(table[i], new Color(Integer.parseInt(table[i + 1], 16)));
        }
    }

    private static Color colour(String state) {
        String n = name(state);
        Color c = COLOURS.get(n);
        if (c != null) {
            return c;
        }
        for (String suffix : new String[] {"_wool", "_carpet", "_wall_banner", "_concrete_powder"}) {
            if (n.endsWith(suffix)) {
                c = COLOURS.get(n.substring(0, n.length() - suffix.length()));
                if (c != null) {
                    return c;
                }
            }
        }
        return n.startsWith("potted_") || n.equals("flower_pot") ? new Color(0x7A3D2D) : n.endsWith("_sign") ? new Color(0x735531)
                : new Color(0x66AA55);
    }

    private static Color shade(Color c, double f) {
        return new Color(clamp(c.getRed() * f), clamp(c.getGreen() * f), clamp(c.getBlue() * f));
    }

    private static int clamp(double v) {
        return (int) Math.max(0, Math.min(255, v));
    }

    // ------------------------------------------------------------------------------------------ pictures

    private static int surface(int x, int z) {
        for (int y = bp.maxY; y >= bp.minY; y--) {
            String s = bp.get(x, y, z);
            if (s != null && !s.equals("air")) {
                return y;
            }
        }
        return bp.minY;
    }

    private static BufferedImage top() {
        int s = 8;
        BufferedImage img = new BufferedImage(w * s, d * s, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                int y = surface(x, z);
                double f = 0.72 + 0.022 * (y + 6);
                if (x > 0 && surface(x - 1, z) > y) {
                    f -= 0.12;
                }
                if (z > 0 && surface(x, z - 1) > y) {
                    f -= 0.12;
                }
                g.setColor(shade(colour(bp.get(x, y, z)), f));
                g.fillRect(x * s, z * s, s, s);
            }
        }
        g.dispose();
        return img;
    }

    /** An isometric picture of the columns x1..x2, z1..z2 below the height {@code cut}; the rest is left out. */
    private static BufferedImage iso(boolean flip, int x1, int z1, int x2, int z2, int cut, int a) {
        int b = a / 2;
        int c = a + a / 8;
        int cw = x2 - x1 + 1;
        int cd = z2 - z1 + 1;
        int ox = cd * a + 20 - ((flip ? w - 1 - x2 : x1) - (flip ? d - 1 - z2 : z1)) * a;
        int oy = (cut + 2) * c + 20 - ((flip ? w - 1 - x2 : x1) + (flip ? d - 1 - z2 : z1)) * b;
        BufferedImage img = new BufferedImage((cw + cd) * a + 40, (cw + cd) * b + (cut - bp.minY + 3) * c + 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setColor(new Color(0xBFD9EE));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        for (int sum = 0; sum <= w + d - 2; sum++) {
            for (int vx = Math.max(0, sum - d + 1); vx <= Math.min(w - 1, sum); vx++) {
                int vz = sum - vx;
                int x = flip ? w - 1 - vx : vx;
                int z = flip ? d - 1 - vz : vz;
                int step = flip ? -1 : 1;
                if (x < x1 || x > x2 || z < z1 || z > z2) {
                    continue;
                }
                for (int y = bp.minY; y <= cut; y++) {
                    String s = bp.get(x, y, z);
                    if (s == null || s.equals("air")) {
                        continue;
                    }
                    boolean up = y == cut || clear(x, y + 1, z);
                    boolean south = z + step < z1 || z + step > z2 || clear(x, y, z + step);
                    boolean east = x + step < x1 || x + step > x2 || clear(x + step, y, z);
                    int k = kind(s);
                    String n = name(s);
                    boolean flat = n.endsWith("_carpet") || n.equals("lily_pad") || (n.endsWith("_trapdoor") && !s.contains("open=true"));
                    if (k == FULL && !flat && !up && !south && !east) {
                        continue;
                    }
                    Color col = colour(s);
                    int px = ox + (vx - vz) * a;
                    int py = oy + (vx + vz) * b - y * c;
                    double inset = k == FULL || n.equals("water") ? 0 : flat ? 0 : 0.3;
                    double lift = flat && s.contains("half=top") ? 0.85 : 0;
                    double height = flat ? 0.12 : n.endsWith("_slab") && !s.contains("type=top") ? 0.5 : n.equals("water") ? 0.85 : 1;
                    cube(g, px, py, a, b, c, inset, lift, height, col, up || k != FULL || flat, south || k != FULL || flat,
                            east || k != FULL || flat);
                }
            }
        }
        g.dispose();
        return img;
    }

    private static boolean clear(int x, int y, int z) {
        if (x < 0 || x >= w || z < 0 || z >= d || y > bp.maxY) {
            return true;
        }
        String s = bp.get(x, y, z);
        if (s == null) {
            return false;
        }
        String n = name(s);
        return kind(s) != FULL || n.endsWith("_slab") || n.endsWith("_trapdoor") && !s.contains("open=true");
    }

    private static void cube(Graphics2D g, int px, int py, int a, int b, int c, double inset, double lift, double height, Color col,
            boolean up, boolean south, boolean east) {
        double i = inset;
        double y0 = py - lift * c;
        double y1 = y0 - height * c;
        // corners of the footprint: north, east, south, west
        double[] fx = {px + (i - i) * a, px + (1 - i - i) * a, px, px - (1 - i - i) * a};
        double[] fy = {b * 2 * i, b * (1 - i + i), b * 2 * (1 - i), b * (1 - i + i)};
        if (up) {
            g.setColor(col);
            g.fillPolygon(poly(fx[0], y1 + fy[0], fx[1], y1 + fy[1], fx[2], y1 + fy[2], fx[3], y1 + fy[3]));
        }
        if (south) {
            g.setColor(shade(col, 0.8));
            g.fillPolygon(poly(fx[3], y1 + fy[3], fx[2], y1 + fy[2], fx[2], y0 + fy[2], fx[3], y0 + fy[3]));
        }
        if (east) {
            g.setColor(shade(col, 0.62));
            g.fillPolygon(poly(fx[2], y1 + fy[2], fx[1], y1 + fy[1], fx[1], y0 + fy[1], fx[2], y0 + fy[2]));
        }
    }

    private static Polygon poly(double... p) {
        Polygon polygon = new Polygon();
        for (int i = 0; i < p.length; i += 2) {
            polygon.addPoint((int) Math.round(p[i]), (int) Math.round(p[i + 1]));
        }
        return polygon;
    }

    // ------------------------------------------------------------------------------------------ walking

    private static int node(int x, int y, int z) {
        return ((y - bp.minY) * d + z) * w + x;
    }

    private static boolean standable(int x, int y, int z) {
        if (x < 0 || x >= w || z < 0 || z >= d || y <= bp.minY || y > bp.maxY) {
            return false;
        }
        return kind(x, y, z) == NONE && kind(x, y + 1, z) == NONE && kind(x, y - 1, z) == FULL;
    }

    /** Where a player standing at (x, y, z) can get to in one move. */
    private static List<int[]> moves(int x, int y, int z) {
        List<int[]> result = new ArrayList<>();
        int[] dx = {0, 1, 0, -1};
        int[] dz = {-1, 0, 1, 0};
        for (int dir = 0; dir < 4; dir++) {
            int nx = x + dx[dir];
            int nz = z + dz[dir];
            // a step up, which needs room to jump
            if (kind(x, y + 2, z) == NONE && standable(nx, y + 1, nz) && kind(nx, y + 2, nz) == NONE) {
                result.add(new int[] {nx, y + 1, nz});
            }
            // straight on, or down as far as it goes
            if (kind(nx, y, nz) == NONE && kind(nx, y + 1, nz) == NONE) {
                for (int ny = y; ny > bp.minY; ny--) {
                    if (kind(nx, ny, nz) != NONE) {
                        break;
                    }
                    if (standable(nx, ny, nz)) {
                        result.add(new int[] {nx, ny, nz});
                        break;
                    }
                }
            }
            // a running jump over a gap of up to three blocks
            for (int gap = 1; gap <= 3; gap++) {
                boolean free = kind(x, y + 2, z) == NONE;
                for (int i = 1; i <= gap + 1 && free; i++) {
                    int cx = x + dx[dir] * i;
                    int cz = z + dz[dir] * i;
                    free = kind(cx, y + 1, cz) == NONE && kind(cx, y + 2, cz) == NONE && (i == gap + 1 || kind(cx, y, cz) == NONE);
                }
                int tx = x + dx[dir] * (gap + 1);
                int tz = z + dz[dir] * (gap + 1);
                if (!free) {
                    break;
                }
                if (gap == 1 && standable(tx, y + 1, tz)) {
                    result.add(new int[] {tx, y + 1, tz});
                }
                if (standable(tx, y, tz)) {
                    result.add(new int[] {tx, y, tz});
                }
            }
        }
        return result;
    }

    private static boolean walk(File picture) throws Exception {
        boolean ok = true;
        Blueprint.Mark start = BazaarLayout.MARKS.get("attack");
        int total = w * d * bp.sizeY;
        boolean[] seen = new boolean[total];
        List<List<Integer>> back = new ArrayList<>();
        Map<Integer, Integer> slot = new HashMap<>();
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        if (!standable(start.x(), start.y(), start.z())) {
            System.out.println("FAIL: the attack mark is not a place to stand");
            return false;
        }
        seen[node(start.x(), start.y(), start.z())] = true;
        queue.add(new int[] {start.x(), start.y(), start.z()});
        List<int[]> reached = new ArrayList<>();
        while (!queue.isEmpty()) {
            int[] p = queue.poll();
            reached.add(p);
            for (int[] q : moves(p[0], p[1], p[2])) {
                int id = node(q[0], q[1], q[2]);
                slot.computeIfAbsent(id, k -> {
                    back.add(new ArrayList<>());
                    return back.size() - 1;
                });
                back.get(slot.get(id)).add(node(p[0], p[1], p[2]));
                if (!seen[id]) {
                    seen[id] = true;
                    queue.add(q);
                }
            }
        }
        // can every reached place get back to the start?
        boolean[] home = new boolean[total];
        ArrayDeque<Integer> rq = new ArrayDeque<>();
        int startId = node(start.x(), start.y(), start.z());
        home[startId] = true;
        rq.add(startId);
        while (!rq.isEmpty()) {
            Integer s = slot.get(rq.poll());
            if (s == null) {
                continue;
            }
            for (int from : back.get(s)) {
                if (!home[from]) {
                    home[from] = true;
                    rq.add(from);
                }
            }
        }

        for (Map.Entry<String, Blueprint.Mark> e : BazaarLayout.MARKS.entrySet()) {
            Blueprint.Mark m = e.getValue();
            boolean there = standable(m.x(), m.y(), m.z()) && seen[node(m.x(), m.y(), m.z())];
            System.out.println("mark " + e.getKey() + " at " + m.x() + " " + m.y() + " " + m.z() + ": " + (there ? "reachable" : "NOT REACHABLE"));
            ok &= there;
        }
        int traps = 0;
        int escapes = 0;
        int[][] level = new int[w][d];
        for (int[] row : level) {
            java.util.Arrays.fill(row, Integer.MIN_VALUE);
        }
        boolean[][] bad = new boolean[w][d];
        for (int[] p : reached) {
            int x = p[0];
            int y = p[1];
            int z = p[2];
            level[x][z] = Math.max(level[x][z], y);
            // the lowest roof in town is seven blocks up, so anyone standing that high has climbed out
            boolean escape = x == 0 || z == 0 || x == w - 1 || z == d - 1 || y >= 7;
            if (escape) {
                bad[x][z] = true;
                if (escapes++ < 15) {
                    System.out.println("ESCAPE: a player can stand at " + x + " " + y + " " + z);
                }
            }
            if (!home[node(x, y, z)]) {
                bad[x][z] = true;
                if (traps++ < 15) {
                    System.out.println("TRAP: no way back from " + x + " " + y + " " + z);
                }
            }
        }
        System.out.println(reached.size() + " places to stand, " + traps + " traps, " + escapes + " escapes");
        ok &= traps == 0 && escapes == 0;

        int s = 8;
        BufferedImage img = new BufferedImage(w * s, d * s, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        for (int x = 0; x < w; x++) {
            for (int z = 0; z < d; z++) {
                Color c;
                if (bad[x][z]) {
                    c = Color.RED;
                } else if (level[x][z] != Integer.MIN_VALUE) {
                    int y = level[x][z];
                    c = y < 1 ? new Color(60, 110 + (y + 5) * 20, 200) : y == 1 ? new Color(120, 200, 120) : new Color(240, 200 - Math.min(5, y) * 20, 60);
                } else {
                    c = kind(x, 1, z) == FULL && kind(x, 2, z) == FULL ? new Color(40, 40, 40) : new Color(110, 110, 110);
                }
                g.setColor(c);
                g.fillRect(x * s, z * s, s, s);
            }
        }
        g.setColor(Color.WHITE);
        for (Map.Entry<String, Blueprint.Mark> e : BazaarLayout.MARKS.entrySet()) {
            g.drawString(e.getKey(), e.getValue().x() * s - 8, e.getValue().z() * s + 4);
        }
        g.dispose();
        ImageIO.write(img, "png", picture);
        return ok;
    }
}
