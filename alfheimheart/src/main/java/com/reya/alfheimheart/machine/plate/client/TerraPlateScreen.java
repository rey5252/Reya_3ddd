package com.reya.alfheimheart.machine.plate.client;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import com.reya.alfheimheart.greenhouse.Format;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineLayouts.Plate;
import com.reya.alfheimheart.machine.MachineStatus;
import com.reya.alfheimheart.machine.client.Gfx;
import com.reya.alfheimheart.machine.client.MachineScreen;
import com.reya.alfheimheart.machine.plate.TerraPlateBlockEntity;
import com.reya.alfheimheart.machine.plate.TerraPlateMenu;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * The Terrestrial Plate's GUI, the celestial astrolabe (terra_plate.png; tools/machines/astrolabe.py): the plate
 * the heart of an astrolabe under the night sky, its six inputs the points of a hexagram (the sky's triangle in
 * mana blue, the earth's in green), its mana gauge an arc on the astrolabe's left; a channel out to a moon tower
 * of outputs on the right.
 * <p>
 * Stars twinkle and an aurora wavers in the sky, brighter while the plate works. When the inputs make terrasteel,
 * the lenses that give to it glow and the hexagram's lines through them light; as the craft charges, light runs
 * along them in to the plate, whose sun lights ray by ray, and a ball of light grows in its core, from mana blue
 * to terrasteel green. When it is done the ball bursts, the ingredients fall into the core, a green bolt runs
 * along the channel to the tower and the moon flashes as the ingot lands. The gauge's arc fills from its foot,
 * its mana shimmering, a light at its head.
 */
public class TerraPlateScreen extends MachineScreen<TerraPlateMenu> {
    private static final int CX = Plate.CX, CY = Plate.CY;
    private static final int BLUE = 0x3F9BFF, GREEN = 0x5DFF74, WHITE = 0xF2FFF4, SKY = 0x55D9F7, EARTH = 0x3FD866;
    private static final float ARC_FROM = Plate.ARC_FROM * Mth.DEG_TO_RAD, ARC_TO = Plate.ARC_TO * Mth.DEG_TO_RAD;

    private static final List<int[]> RING = new ArrayList<>();
    @SuppressWarnings("unchecked")
    private static final List<int[]>[] RAYS = new List[8];

    static {
        for (int k = 0; k < 8; k++) RAYS[k] = new ArrayList<>();
        int r = Plate.PLATE_R;
        for (int y = -r; y < r; y++) {
            for (int x = -r; x < r; x++) {
                float px = x + 0.5F, py = y + 0.5F;
                float d = Mth.sqrt(px * px + py * py);
                if (Math.abs(d - Plate.SUN_RING_R) < 0.6F) {
                    RING.add(new int[]{x, y});
                    continue;
                }
                float ang = (float) Math.atan2(py, px);
                int k = Math.round(ang / (PI / 4.0F));
                float off = Math.abs(ang - k * PI / 4.0F) * d;
                boolean ray = d > Plate.RAY_FROM && d < Plate.RAY_TO && off < 0.6F, tip = d >= Plate.RAY_TO && d < Plate.TIP_TO && off < 1.1F;
                // the rays light from the top, clockwise
                if (ray || tip) RAYS[Math.floorMod(k + 2, 8)].add(new int[]{x, y, tip ? 1 : 0});
            }
        }
    }

    @Nullable
    private MachineBlockEntity.Job job;
    private final boolean[] used = new boolean[6];
    private final float[] glow = new float[6];
    private List<ItemStack> falling = List.of();
    private final List<float[]> fallingFrom = new ArrayList<>();
    private long moteAt, landedAt = -100000L;

    public TerraPlateScreen(TerraPlateMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    protected String key() {
        return "terra_plate";
    }

    @Override
    protected int titleColour() {
        return 0xFFF4F8FF;
    }

    @Override
    protected int titleShadow() {
        return 0xFF03050D;
    }

    @Override
    protected int lightColour() {
        return 0xCFE2FF;
    }

    @Override
    protected int[] veinColours() {
        return new int[]{0xF2FFF4, 0x8DF5A0, 0x3FD866};
    }

    private static boolean sky(int i) {
        for (int s : Plate.SKY) if (s == i) return true;
        return false;
    }

    private static int pointColour(int i) {
        return sky(i) ? SKY : EARTH;
    }

    private float[] socket(int i) {
        return new float[]{layout.inputs[i][0] + 8.0F, layout.inputs[i][1] + 8.0F};
    }

    private void refresh(long t) {
        if (!itemsChanged(t) || minecraft == null || minecraft.level == null) return;
        job = TerraPlateBlockEntity.find(minecraft.level, menu.items());
        for (int i = 0; i < used.length; i++) {
            ItemStack stack = menu.items().getStackInSlot(i);
            used[i] = job != null && !stack.isEmpty() && job.units().stream().anyMatch(u -> ItemStack.isSameItemSameTags(u, stack));
        }
    }

    static int lerpColour(int a, int b, float p) {
        p = Mth.clamp(p, 0.0F, 1.0F);
        int r = Math.round(Mth.lerp(p, a >> 16 & 0xFF, b >> 16 & 0xFF));
        int g = Math.round(Mth.lerp(p, a >> 8 & 0xFF, b >> 8 & 0xFF));
        int bl = Math.round(Mth.lerp(p, a & 0xFF, b & 0xFF));
        return r << 16 | g << 8 | bl;
    }

    // ------------------------------------------------------------------ the heart

    @Override
    protected void drawHeart(GuiGraphics g, long t, float dt) {
        refresh(t);
        boolean busy = working();
        float p = busy ? shownProgress() : 0.0F;
        int c = lerpColour(BLUE, GREEN, p);
        long since = t - craftedAt;
        float flash = since < 600L ? 1.0F - since / 600.0F : 0.0F;

        drawAurora(g, t, busy ? 0.55F + 0.45F * p : 0.4F);
        drawStars(g, t);

        // the hexagram's lines: the triangles of the lenses that give to the craft
        if (job != null) {
            for (int tri = 0; tri < 2; tri++) {
                boolean skyTri = tri == 0;
                int[] pts = new int[3];
                int n = 0;
                boolean any = false;
                for (int i = 0; i < 6; i++) {
                    if (sky(i) == skyTri) {
                        pts[n++] = i;
                        any |= used[i];
                    }
                }
                if (!any) continue;
                int col = skyTri ? SKY : EARTH;
                float a = busy ? 0.3F + 0.5F * p : 0.14F;
                for (int e = 0; e < 3; e++) {
                    float[] s1 = socket(pts[e]), s2 = socket(pts[(e + 1) % 3]);
                    Gfx.beam(g, leftPos + s1[0], topPos + s1[1], leftPos + s2[0], topPos + s2[1], 3.0F, Gfx.argb(col, a), Gfx.argb(col, a), true);
                }
            }
        }
        // the lenses' glow, and light running in from them to the plate
        for (int i = 0; i < 6; i++) {
            float target = used[i] ? (busy ? 0.45F + 0.3F * p + 0.15F * Mth.sin(t / 180.0F + i) : 0.25F) : 0.0F;
            target = Math.max(target, used[i] ? flash : 0.0F);
            glow[i] += (target - glow[i]) * Math.min(1.0F, dt * 6.0F);
            float[] at = socket(i);
            if (glow[i] > 0.01F) {
                Gfx.radial(g, leftPos + at[0], topPos + at[1], 19.0F, Gfx.argb(pointColour(i), glow[i] * 0.55F), Gfx.argb(pointColour(i), 0.0F), true);
            }
            if (used[i] && busy) {
                Gfx.beam(g, leftPos + at[0], topPos + at[1], leftPos + CX, topPos + CY, 2.0F, Gfx.argb(pointColour(i), 0.15F + 0.35F * p),
                        Gfx.argb(c, 0.35F + 0.5F * p), true);
                float k = ((t / 600.0F) + i * 0.29F) % 1.0F;
                Gfx.radial(g, leftPos + Mth.lerp(k, at[0], CX), topPos + Mth.lerp(k, at[1], CY), 3.0F, Gfx.argb(0xFFFFFF, 0.5F + 0.4F * p),
                        Gfx.argb(pointColour(i), 0.0F), true);
            }
        }
        if (busy && job != null && t - moteAt > 90L) {
            moteAt = t;
            int i = random.nextInt(6);
            if (used[i]) {
                float[] at = socket(i);
                float dx = CX - at[0], dy = CY - at[1], len = Math.max(1.0F, Mth.sqrt(dx * dx + dy * dy));
                float speed = 18.0F + 22.0F * p;
                drift(at[0], at[1], dx / len * speed, dy / len * speed, len / speed, random.nextBoolean() ? pointColour(i) : GOLD_LIGHT);
            }
        }

        // the sun on the plate: its rays light one by one as the craft charges, its ring brightens
        g.drawManaged(() -> sun(g, t, busy, p, c, flash));
        // the ball of light in the core
        float ball = Math.max(busy ? 0.3F + 0.7F * p : job != null ? 0.16F : 0.0F, flash);
        if (ball > 0.02F) {
            float r = 7.0F + 6.0F * p + 14.0F * flash;
            Gfx.radial(g, leftPos + CX, topPos + CY, r, Gfx.argb(c, Math.min(1.0F, ball * (0.85F + 0.15F * Mth.sin(t / 110.0F)))),
                    Gfx.argb(c, 0.0F), true);
            Gfx.radial(g, leftPos + CX, topPos + CY, 2.0F + 3.0F * p, Gfx.argb(lerpColour(c, WHITE, 0.6F), ball), Gfx.argb(WHITE, 0.0F), true);
        }
        // the channel out to the tower: a flicker while it works, a green bolt along it as the ingot goes
        float bx1 = Plate.BEAM[0], by = Plate.BEAM[1], bx2 = Plate.BEAM[2];
        if (busy) {
            Gfx.beam(g, leftPos + bx1, topPos + by, leftPos + bx2, topPos + by, 2.0F, Gfx.argb(c, 0.1F + 0.15F * p), Gfx.argb(c, 0.05F), true);
        }
        if (since < 480L) {
            float e = since / 480.0F;
            float head = bx1 + (bx2 - bx1) * Math.min(1.0F, e * 1.6F);
            Gfx.beam(g, leftPos + bx1, topPos + by, leftPos + head, topPos + by, 3.0F, Gfx.argb(GREEN, 0.2F * (1.0F - e)), Gfx.argb(WHITE, 0.9F * (1.0F - e)),
                    true);
        }
        // the moon: it glows while the plate works, flashes as the ingot lands
        long landed = t - landedAt;
        float moon = Math.max(busy ? 0.15F + 0.1F * Mth.sin(t / 400.0F) : 0.06F, landed < 800L ? 1.0F - landed / 800.0F : 0.0F);
        Gfx.radial(g, leftPos + Plate.MOON[0] + 0.5F, topPos + Plate.MOON[1] + 0.5F, Plate.MOON[2] + 8.0F, Gfx.argb(0xFFF4D8, moon * 0.6F),
                0x00FFF4D8, true);
    }

    private void sun(GuiGraphics g, long t, boolean busy, float p, int c, float flash) {
        int lit = busy ? Math.min(8, (int) (p * 8.0F)) : 0;
        for (int k = 0; k < 8; k++) {
            float a;
            if (k < lit) a = 0.85F;
            else if (busy && k == lit) a = 0.2F + 0.4F * (0.5F + 0.5F * Mth.sin(t / 90.0F));
            else a = job != null ? 0.12F + 0.1F * Mth.sin(t / 400.0F + k * 0.8F) : 0.0F;
            a = Math.max(a, flash);
            if (a <= 0.02F) continue;
            for (int[] px : RAYS[k]) {
                int col = px[2] == 1 ? WHITE : c;
                g.fill(leftPos + CX + px[0], topPos + CY + px[1], leftPos + CX + px[0] + 1, topPos + CY + px[1] + 1, (int) (a * 255) << 24 | col);
            }
        }
        float ring = Math.max(busy ? 0.25F + 0.6F * p : job != null ? 0.12F : 0.0F, flash);
        if (ring > 0.02F) {
            for (int[] px : RING) {
                g.fill(leftPos + CX + px[0], topPos + CY + px[1], leftPos + CX + px[0] + 1, topPos + CY + px[1] + 1, (int) (ring * 200) << 24 | c);
            }
        }
    }

    /** Three curtains of aurora wavering across the top of the sky, shimmering along their length, green to violet. */
    private void drawAurora(GuiGraphics g, long t, float strength) {
        float time = t / 1000.0F;
        int x1 = 8, x2 = layout.width - 8, step = 4;
        int n = (x2 - x1) / step + 1;
        float[] xs = new float[n], ys = new float[n];
        int[] colours = new int[n];
        int[] tints = {0x3DFFB0, 0x2EC8E6, 0xA67CFF};
        float[] bases = {34.0F, 50.0F, 22.0F}, heights = {26.0F, 20.0F, 16.0F}, strengths = {0.36F, 0.28F, 0.2F};
        for (int band = 0; band < 3; band++) {
            float speed = 0.5F + band * 0.22F;
            for (int i = 0; i < n; i++) {
                float x = x1 + i * step;
                xs[i] = leftPos + x;
                ys[i] = topPos + bases[band] + 7.0F * Mth.sin(x * 0.04F + time * speed + band) + 3.0F * Mth.sin(x * 0.11F - time * 1.3F);
                float edge = Math.min(1.0F, Math.min(x - x1, x2 - x) / 40.0F);
                float shimmer = 0.5F + 0.5F * Mth.sin(x * 0.07F + time * 2.0F + band * 1.7F);
                int tint = Gfx.mix(0xFF000000 | tints[band], 0xFF000000 | tints[(band + 1) % 3], 0.5F + 0.5F * Mth.sin(x * 0.02F + time * 0.4F));
                colours[i] = Gfx.argb(tint, strengths[band] * strength * edge * shimmer);
            }
            Gfx.ribbon(g, xs, ys, heights[band], colours, true);
        }
    }

    /** The bright stars twinkle on their own beats. */
    private void drawStars(GuiGraphics g, long t) {
        for (int i = 0; i < Plate.STARS.length; i++) {
            float beat = 0.5F + 0.5F * Mth.sin(t / (700.0F + i * 90.0F) + i * 2.3F);
            float a = beat * beat * beat;
            if (a < 0.05F) continue;
            int s = Plate.STAR_SIZE;
            glowBlit(g, Plate.STARS[i][0] + 0.5F - s / 2.0F, Plate.STARS[i][1] + 0.5F - s / 2.0F, Plate.STAR_U, Plate.STAR_V, s, s, 0xDDE8FF, a);
        }
    }

    // ------------------------------------------------------------------ the arc gauge

    @Override
    protected void drawGauge(GuiGraphics g, long t, float mana) {
        float m = Mth.clamp(mana, 0.0F, 1.0F);
        float r1 = Plate.ARC_R1 + 0.6F, r2 = Plate.ARC_R2 - 0.4F;
        float end = ARC_FROM + (ARC_TO - ARC_FROM) * m;
        float time = t / 1000.0F;
        if (m > 0.0F) {
            Gfx.arc(g, leftPos + CX, topPos + CY, r1, r2, ARC_FROM, end, k -> {
                float along = k * m;
                int base = lerpColour(0x1B64B8, 0x8FEFFF, along);
                float shimmer = 0.85F + 0.15F * Mth.sin(along * 30.0F - time * 4.0F);
                return Gfx.argb(lerpColour(0x000000, base, shimmer), 0.95F);
            }, false, false);
            // its head: a soft light where the mana reaches
            float hx = CX + Mth.cos(end) * (Plate.ARC_R1 + Plate.ARC_R2) / 2.0F, hy = CY + Mth.sin(end) * (Plate.ARC_R1 + Plate.ARC_R2) / 2.0F;
            Gfx.radial(g, leftPos + hx, topPos + hy, 6.0F, Gfx.argb(0xF2FFFF, 0.55F + 0.2F * Mth.sin(t / 150.0F)), 0x00A6F6FF, true);
            if (working()) {
                float k = (t % 1600L) / 1600.0F;
                float a = ARC_FROM + (end - ARC_FROM) * k;
                Gfx.radial(g, leftPos + CX + Mth.cos(a) * (r1 + r2) / 2.0F, topPos + CY + Mth.sin(a) * (r1 + r2) / 2.0F, 4.0F, 0x80FFFFFF,
                        0x00FFFFFF, true);
            }
        }
        if (menu.status() == MachineStatus.NO_MANA) {
            float beat = 0.5F + 0.5F * Mth.sin(t / 260.0F);
            Gfx.arc(g, leftPos + CX, topPos + CY, r1, r2, end, ARC_TO, k -> Gfx.argb(0xC82C26, 0.1F + 0.25F * beat), false, false);
        }
    }

    @Override
    protected boolean overGauge(int mx, int my) {
        float dx = mx - CX, dy = my - CY;
        float d = Mth.sqrt(dx * dx + dy * dy);
        if (d < Plate.ARC_R1 - 2 || d > Plate.ARC_R2 + 3) return false;
        float a = (float) Math.atan2(dy, dx);
        if (a < 0) a += Gfx.TAU;
        return a >= ARC_FROM - 0.06F && a <= ARC_TO + 0.06F;
    }

    // ------------------------------------------------------------------ over the items

    @Override
    protected void drawOverItems(GuiGraphics g, long t) {
        long since = t - craftedAt;
        if (since < 420L && !falling.isEmpty()) {
            float e = easeOut(since / 420.0F);
            for (int i = 0; i < falling.size() && i < fallingFrom.size(); i++) {
                float[] from = fallingFrom.get(i);
                floatingItem(g, falling.get(i), Mth.lerp(e, from[0], CX), Mth.lerp(e, from[1], CY), 0.8F * (1.0F - 0.85F * e));
            }
        }
        if (job != null && !job.outputs().isEmpty() && working()) {
            // what the plate makes, taking shape in the core
            float p = shownProgress();
            floatingItem(g, job.outputs().get(0), CX, CY, 0.25F + 0.45F * p);
        }
    }

    @Override
    protected void onCraft(long t) {
        falling = new ArrayList<>();
        fallingFrom.clear();
        for (int i = 0; i < used.length; i++) {
            if (!used[i]) continue;
            ItemStack stack = menu.items().getStackInSlot(i);
            if (stack.isEmpty()) continue;
            falling.add(stack.copyWithCount(1));
            fallingFrom.add(socket(i));
        }
        burst(CX, CY, 16, GREEN, 1.4F);
        burst(CX, CY, 8, WHITE, 0.9F);
    }

    @Override
    protected void onLanded(long t) {
        landedAt = t;
        burst(Plate.MOON[0], Plate.MOON[1], 8, 0xFFF4D8, 0.7F);
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    protected void heartTooltip(List<Component> tip, int mx, int my) {
        float dx = mx - CX, dy = my - CY;
        if (dx * dx + dy * dy > Plate.LIMB_R2 * Plate.LIMB_R2) return;
        tip.add(Component.translatable("gui.alfheimheart.terra_plate.plate").withStyle(ChatFormatting.GOLD));
        tip.add(status());
        if (job != null && !job.outputs().isEmpty()) {
            tip.add(Component.translatable("gui.alfheimheart.makes", job.outputs().get(0).getHoverName()).withStyle(ChatFormatting.WHITE));
            tip.add(Component.translatable("gui.alfheimheart.cost", Format.mana(job.mana())).withStyle(ChatFormatting.AQUA));
            if (menu.working()) tip.add(progressLine());
        } else {
            tip.add(Component.translatable("gui.alfheimheart.terra_plate.hint").withStyle(ChatFormatting.GRAY));
        }
    }

    @Override
    protected void inputSlotTooltip(List<Component> tip, int index) {
        boolean s = sky(index);
        tip.add(Component.translatable(s ? "gui.alfheimheart.terra_plate.point.sky" : "gui.alfheimheart.terra_plate.point.earth")
                .withStyle(st -> st.withColor(s ? SKY : EARTH)));
        tip.add(Component.translatable("gui.alfheimheart.terra_plate.slot.input.tip").withStyle(ChatFormatting.GRAY));
        tip.add(Component.translatable("gui.alfheimheart.terra_plate.points").withStyle(ChatFormatting.DARK_GRAY));
    }
}
