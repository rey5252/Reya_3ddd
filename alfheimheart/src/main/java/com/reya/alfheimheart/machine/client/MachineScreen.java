package com.reya.alfheimheart.machine.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.greenhouse.Format;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineMenu;
import com.reya.alfheimheart.machine.MachineStatus;
import com.reya.alfheimheart.portal.client.PlateFont;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * A mana machine's GUI in the mod's living-wood style: the machine's panel texture (tools/&lt;machine&gt;/gen_gui.py:
 * livingwood planks framed with livingrock, mana crystals on the corners, the input slots on the left, the output
 * slots on the right, the machine's heart between them, the mana bar under it), and over it what every machine's
 * GUI does alike: the title on a livingrock plaque, the close button, the crystals twinkling, mana running round
 * the frame while it works, the arrows lighting up, the mana bar, the redstone button, the pool light, the status
 * gem, the finished craft flying out to the output slots, sparkles; opening, the panel grows out of the machine's
 * heart a little past its size and settles, and closing, it folds back into it.
 * <p>
 * Each machine draws its heart ({@link #drawHeart}) and says what its tooltips over it are ({@link #heartTooltip}).
 */
public abstract class MachineScreen<M extends MachineMenu> extends AbstractContainerScreen<M> {
    public static final ResourceLocation WIDGETS = new ResourceLocation(AlfheimHeart.MODID, "textures/gui/machine_widgets.png");
    protected static final int M = 12, TEX_W = MachineMenu.WIDTH + 2 * M, TEX_H = MachineMenu.HEIGHT + 2 * M;
    protected static final int WIDGETS_W = 256, WIDGETS_H = 128;

    // the layout every machine's panel shares (tools/lib/machine_gui.py; tools/*/check_layout.py keep them in step)
    protected static final int MACHINE_H = 124, INV_PANEL_X1 = 28, INV_PANEL_X2 = 212;
    protected static final int ARROW_IN_X = 74, ARROW_OUT_X = 155, ARROW_Y = 53, ARROW_W = 12, ARROW_H = 9, ARROW_U = 160, ARROW_V = 0;
    protected static final int BAR_X1 = 64, BAR_Y1 = 108, BAR_X2 = 176, BAR_Y2 = 114, FILL_V = 80;
    protected static final int BUTTON_REDSTONE_X = 18, BUTTON_Y = 102, BUTTON_SIZE = 16, ICON_V = 16;
    protected static final int POOL_X = 206, POOL_Y = 102, POOL_SIZE = 16, POOL_U = 192, POOL_V = 0;
    protected static final int GEM_SIZE = 7, GEM_U = 160, GEM_V = 16;
    protected static final int SCROLL_H = 20, SCROLL_CAP = 16, SCROLL_V = 56, SCROLL_TILE_U = 32, SCROLL_TILE_W = 8,
            SCROLL_UP = 12, SCROLL_PAD = 6;
    protected static final int CLOSE_X = 229, CLOSE_Y = -8, CLOSE_SIZE = 16, CLOSE_U = 104, CLOSE_V = 0;
    protected static final int GLOW_U = 104, GLOW_V = 16, GLOW_SIZE = 9;
    protected static final int VEIN = 3;
    /** The mana crystals on the panels' corners (their middles), which twinkle. */
    protected static final int[][] LIGHTS = {{1, 1}, {1, 122}, {238, 122}, {29, 212}, {210, 212}};
    protected static final int INPUT_MID_X = 44, OUTPUT_MID_X = 194, GRID_MID_Y = 56;

    protected static final long GROW_MS = 380L, CLOSE_MS = 260L, FLASH_MS = 500L;
    protected static final int MANA_BRIGHT = 0xA6F6FF, GREEN_LIGHT = 0xB6F59A, GOLD_LIGHT = 0xFFE27A, PINK_LIGHT = 0xFF9AD8;
    protected static final float PI = (float) Math.PI;

    private final ResourceLocation panel;
    /** The middle of the machine's heart (menu coordinates): the panel grows from there, crafts fly out of it. */
    protected final int heartX, heartY;
    /** Where the status gem sits (its middle), or -1 for none. */
    protected final int gemX, gemY;

    protected final long openedAt = Util.getMillis();
    protected final List<Mote> motes = new ArrayList<>();
    private final List<Flight> flights = new ArrayList<>();
    protected final RandomSource random = RandomSource.create();
    private float shownMana, shownProgress;
    private int lastCrafts = -1;
    protected long craftedAt = -100000L;
    private long lastFrame, closingAt = -1L;
    private boolean welcomed;
    private int scrollHalf = 60;

    protected MachineScreen(M menu, Inventory inv, Component title, ResourceLocation panel, int heartX, int heartY, int gemX, int gemY) {
        super(menu, inv, title);
        this.panel = panel;
        this.heartX = heartX;
        this.heartY = heartY;
        this.gemX = gemX;
        this.gemY = gemY;
        imageWidth = MachineMenu.WIDTH;
        imageHeight = MachineMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        topPos = Math.max(SCROLL_UP + 2, (height - imageHeight - SCROLL_UP) / 2 + SCROLL_UP);
        if (topPos + imageHeight > height) topPos = Math.max(0, height - imageHeight);
    }

    protected long age() {
        return Util.getMillis() - openedAt;
    }

    protected boolean ready() {
        return age() >= GROW_MS;
    }

    /** Where the GUI draws outside its panel: the title plaque and the crystals. */
    public List<Rect2i> extraAreas() {
        List<Rect2i> areas = new ArrayList<>();
        areas.add(new Rect2i(leftPos - M, topPos - SCROLL_UP - 2, imageWidth + 2 * M, SCROLL_UP + 2));
        areas.add(new Rect2i(leftPos - M, topPos - M, imageWidth + 2 * M, MACHINE_H + 2 * M));
        return areas;
    }

    // ------------------------------------------------------------------ what each machine draws

    /** The machine's heart (called over the panel, under the slots' items). */
    protected abstract void drawHeart(GuiGraphics g, long t, float dt);

    /** Over the heart after the items (orbiting items and such). */
    protected void drawOverItems(GuiGraphics g, long t) {
    }

    /** The heart's tooltip, if the mouse is over it (else leave it empty). */
    protected abstract void heartTooltip(List<Component> tip, int mx, int my);

    /** The tooltip of an empty special slot (index among the special slots). */
    protected void specialSlotTooltip(List<Component> tip, int index) {
    }

    /** A craft just finished (the screen noticed the count change). */
    protected void onCraft(long t) {
    }

    /** The key of the machine's lang entries: gui.alfheimheart.&lt;key&gt;.… */
    protected abstract String key();

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        long t = age();
        track(t);
        if (closingAt >= 0L) {
            float p = Mth.clamp((Util.getMillis() - closingAt) / (float) CLOSE_MS, 0.0F, 1.0F);
            float s = 1.0F - p * p * (2.2F - 1.2F * p);
            if (s > 0.02F) renderScaled(g, s, partialTick);
            return;
        }
        if (t < GROW_MS) {
            renderScaled(g, backOut(t / (float) GROW_MS), partialTick);
            if (t >= GROW_MS - 140L && !welcomed) {
                welcomed = true;
                burst(heartX, heartY, 14, MANA_BRIGHT, 1.3F);
                burst(heartX, heartY, 10, GOLD_LIGHT, 1.0F);
            }
            g.pose().pushPose();
            g.pose().translate(0.0F, 0.0F, 300.0F);
            drawMotes(g, true);
            g.pose().popPose();
            return;
        }
        super.render(g, mouseX, mouseY, partialTick);
        drawOverItems(g, t);
        drawFlights(g, t);
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        drawMotes(g, true);
        g.pose().popPose();
        renderTooltip(g, mouseX, mouseY);
        if ((hoveredSlot == null || !hoveredSlot.hasItem()) && menu.getCarried().isEmpty()) ownTooltips(g, mouseX, mouseY);
    }

    private void renderScaled(GuiGraphics g, float scale, float partialTick) {
        int cx = leftPos + heartX, cy = topPos + heartY;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.pose().translate(-cx, -cy, 0.0F);
        super.render(g, -1000, -1000, partialTick);
        g.pose().popPose();
    }

    private void track(long t) {
        if (menu.capacity() <= 1) return;                   // the menu's numbers haven't arrived yet
        int crafts = menu.crafts();
        if (lastCrafts >= 0 && crafts != lastCrafts) {
            craftedAt = t;
            ItemStack out = menu.lastOutput();
            if (!out.isEmpty() && flights.size() < 6) flights.add(new Flight(out, t));
            burst(heartX, heartY, 10, GOLD_LIGHT, 0.9F);
            onCraft(t);
        }
        lastCrafts = crafts;
    }

    @Override
    public void onClose() {
        if (closingAt < 0L && ready()) {
            closingAt = Util.getMillis();
            return;
        }
        super.onClose();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (closingAt >= 0L) {
            if (keyCode == 256 || minecraft != null && minecraft.options.keyInventory.isActiveAndMatches(InputConstants.getKey(keyCode, scanCode))) {
                super.onClose();
            }
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (closingAt >= 0L && Util.getMillis() - closingAt >= CLOSE_MS) super.onClose();
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        long t = age();
        long now = Util.getMillis();
        float dt = lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        updateMotes(dt);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(panel, leftPos - M, topPos - M, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
        drawLights(g, t);
        drawTitle(g);
        drawClose(g, mouseX, mouseY);
        drawHeart(g, t, dt);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.drawManaged(() -> {
            drawMotes(g, false);
            drawVeinPulse(g, t);
        });
        drawGem(g, t);
        drawArrows(g, t);
        drawManaBar(g, t, dt);
        drawButtons(g, mouseX, mouseY, t);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // the title is on the plaque over the panel; the inventory needs no label
    }

    // ------------------------------------------------------------------ shared parts

    /** How far the craft is (0..1), smoothed. */
    protected float shownProgress() {
        return shownProgress;
    }

    protected boolean working() {
        return menu.status() == MachineStatus.WORKING;
    }

    private void drawGem(GuiGraphics g, long t) {
        if (gemX < 0) return;
        int kind = switch (menu.status()) {
            case WORKING -> 0;
            case IDLE -> 1;
            case NO_MANA, OUTPUT_FULL -> 2;
            case REDSTONE -> 3;
        };
        if (kind == 0 || kind == 2) {
            float beat = 0.5F + 0.5F * Mth.sin(t / (kind == 0 ? 170.0F : 380.0F));
            RenderSystem.enableBlend();
            if (kind == 0) g.setColor(0.7F, 1.0F, 0.6F, 0.4F + 0.5F * beat);
            else g.setColor(1.0F, 0.4F, 0.35F, 0.3F + 0.4F * beat);
            g.blit(WIDGETS, leftPos + gemX - GLOW_SIZE / 2, topPos + gemY - GLOW_SIZE / 2, GLOW_U, GLOW_V, GLOW_SIZE, GLOW_SIZE, WIDGETS_W, WIDGETS_H);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
        RenderSystem.enableBlend();
        g.blit(WIDGETS, leftPos + gemX - GEM_SIZE / 2, topPos + gemY - GEM_SIZE / 2, GEM_U + kind * GEM_SIZE, GEM_V, GEM_SIZE, GEM_SIZE,
                WIDGETS_W, WIDGETS_H);
    }

    /** The arrows glow while the machine works, and flash on each craft (into the heart, then out). */
    private void drawArrows(GuiGraphics g, long t) {
        boolean on = working();
        long since = t - craftedAt;
        drawArrow(g, ARROW_IN_X, t, on ? (t / 600L % 2L == 0L ? t % 600L : 1000L) : 1000L, on);
        drawArrow(g, ARROW_OUT_X, t, since, on);
    }

    private void drawArrow(GuiGraphics g, int x, long t, long since, boolean on) {
        float flash = since >= 0L && since < 400L ? 1.0F - since / 400.0F : 0.0F;
        float glow = on ? 0.3F + 0.15F * Mth.sin(t / 240.0F + x) : 0.0F;
        float a = Math.max(flash, glow);
        if (a <= 0.02F) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1.0F, 1.0F, 1.0F, a);
        g.blit(WIDGETS, leftPos + x, topPos + ARROW_Y, ARROW_U + (flash > 0.5F ? ARROW_W : 0), ARROW_V, ARROW_W, ARROW_H, WIDGETS_W, WIDGETS_H);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** The mana store: fills smoothly, a shine runs over it while it works, it blinks red when a craft waits for mana. */
    private void drawManaBar(GuiGraphics g, long t, float dt) {
        float target = menu.capacity() <= 1 ? 0.0F : Mth.clamp(menu.mana() / (float) menu.capacity(), 0.0F, 1.0F);
        shownMana += (target - shownMana) * Math.min(1.0F, dt * 6.0F);
        float p = menu.progress();
        shownProgress = p < shownProgress - 0.2F ? p : shownProgress + (p - shownProgress) * Math.min(1.0F, dt * 8.0F);
        int x1 = leftPos + BAR_X1, y1 = topPos + BAR_Y1, y2 = topPos + BAR_Y2;
        int w = Math.round((BAR_X2 - BAR_X1) * shownMana);
        if (w > 0) {
            RenderSystem.enableBlend();
            g.blit(WIDGETS, x1, y1, 0, FILL_V, w, BAR_Y2 - BAR_Y1, WIDGETS_W, WIDGETS_H);
            if (working()) {
                int sx = x1 + (int) ((t / 7L) % (w + 30)) - 15;
                for (int k = 0; k < 6; k++) {
                    int xx = sx + k;
                    if (xx >= x1 && xx < x1 + w) g.fill(xx, y1 + 1, xx + 1, y2 - 1, (40 + (k < 3 ? k : 5 - k) * 25) << 24 | 0xFFFFFF);
                }
            }
            g.fill(x1 + w - 1, y1, x1 + w, y2, 0xFF000000 | MANA_BRIGHT);
        }
        if (menu.status() == MachineStatus.NO_MANA) {
            float beat = 0.5F + 0.5F * Mth.sin(t / 260.0F);
            g.fill(x1 + Math.max(0, w), y1, leftPos + BAR_X2, y2, (int) (20 + beat * 60) << 24 | 0xC82C26);
        }
    }

    private void drawButtons(GuiGraphics g, int mouseX, int mouseY, long t) {
        int x = leftPos + BUTTON_REDSTONE_X, y = topPos + BUTTON_Y;
        boolean hot = ready() && inside(BUTTON_REDSTONE_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY);
        RenderSystem.enableBlend();
        g.blit(WIDGETS, x, y, hot ? 16 : 0, 0, BUTTON_SIZE, BUTTON_SIZE, WIDGETS_W, WIDGETS_H);
        g.blit(WIDGETS, x + 2, y + 2, menu.redstone().ordinal() * 12, ICON_V, 12, 12, WIDGETS_W, WIDGETS_H);
        boolean pool = menu.hasPool();
        g.blit(WIDGETS, leftPos + POOL_X, topPos + POOL_Y, POOL_U + (pool ? POOL_SIZE : 0), POOL_V, POOL_SIZE, POOL_SIZE, WIDGETS_W, WIDGETS_H);
        if (pool) {
            float beat = 0.5F + 0.5F * Mth.sin(t / 300.0F);
            if (beat > 0.9F) sparkle(g, leftPos + POOL_X + 7, topPos + POOL_Y + 4, MANA_BRIGHT, (beat - 0.9F) / 0.1F);
        }
    }

    private void drawTitle(GuiGraphics g) {
        int mid = leftPos + imageWidth / 2;
        String title = getTitle().getString();
        int textW = PlateFont.width(title);
        int paper = textW + 2 * SCROLL_PAD, x0 = mid - paper / 2, x1 = x0 + paper;
        int top = topPos - SCROLL_UP;
        scrollHalf = paper / 2 + SCROLL_CAP;
        RenderSystem.enableBlend();
        for (int x = x0, k = 0; x < x1; x += SCROLL_TILE_W, k++) {
            g.blit(WIDGETS, x, top, SCROLL_TILE_U + (k & 1) * SCROLL_TILE_W, SCROLL_V, Math.min(SCROLL_TILE_W, x1 - x), SCROLL_H,
                    WIDGETS_W, WIDGETS_H);
        }
        g.blit(WIDGETS, x0 - SCROLL_CAP, top, 0, SCROLL_V, SCROLL_CAP, SCROLL_H, WIDGETS_W, WIDGETS_H);
        g.blit(WIDGETS, x1, top, SCROLL_CAP, SCROLL_V, SCROLL_CAP, SCROLL_H, WIDGETS_W, WIDGETS_H);
        PlateFont.draw(g, title, mid - textW / 2 + 1, top + 9, 0xFFFFF9E0);
        PlateFont.draw(g, title, mid - textW / 2, top + 8, 0xFF4E3417);
    }

    private void drawClose(GuiGraphics g, int mouseX, int mouseY) {
        boolean hot = ready() && inside(CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE, mouseX, mouseY);
        int state = closingAt >= 0L ? 2 : hot ? 1 : 0;
        RenderSystem.enableBlend();
        g.blit(WIDGETS, leftPos + CLOSE_X, topPos + CLOSE_Y, CLOSE_U + state * CLOSE_SIZE, CLOSE_V, CLOSE_SIZE, CLOSE_SIZE,
                WIDGETS_W, WIDGETS_H);
    }

    /** The mana crystals twinkle: a soft halo round each on its own beat, livelier while the machine works. */
    private void drawLights(GuiGraphics g, long t) {
        boolean busy = working();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int i = 0; i < LIGHTS.length; i++) {
            float beat = 0.5F + 0.5F * Mth.sin(t / (busy ? 330.0F : 560.0F) + i * 1.9F);
            float a = (busy ? 0.4F : 0.25F) + beat * beat * (busy ? 0.6F : 0.5F);
            int x = leftPos + LIGHTS[i][0], y = topPos + LIGHTS[i][1];
            g.setColor(0.6F, 0.95F, 1.0F, a);
            g.blit(WIDGETS, x - GLOW_SIZE / 2, y - GLOW_SIZE / 2, GLOW_U, GLOW_V, GLOW_SIZE, GLOW_SIZE, WIDGETS_W, WIDGETS_H);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (beat > 0.94F && ready()) sparkle(g, x, y, MANA_BRIGHT, (beat - 0.94F) / 0.06F);
        }
    }

    /** Mana runs round the livingrock strip in the frame while the machine works. */
    private void drawVeinPulse(GuiGraphics g, long t) {
        if (!working()) return;
        int x1 = VEIN, y1 = VEIN, x2 = MachineMenu.WIDTH - 1 - VEIN, y2 = MACHINE_H - 1 - VEIN;
        int perimeter = 2 * (x2 - x1) + 2 * (y2 - y1);
        for (int k = 0; k < 2; k++) {
            int head = (int) ((t / 22L + k * perimeter / 2) % perimeter);
            for (int j = 0; j < 18; j++) {
                int d = head - j;
                if (d < 0) d += perimeter;
                int[] p = veinPoint(d, x1, y1, x2, y2);
                if ((p[0] == x1 || p[0] == x2) && (p[1] == y1 || p[1] == y2)) continue;
                float f = j / 18.0F;
                int a = (int) (230 * (1.0F - f) * (1.0F - f)) + 10;
                int c = j == 0 ? 0xF2FFFF : j < 3 ? 0xA6F6FF : 0x55D9F7;
                g.fill(leftPos + p[0], topPos + p[1], leftPos + p[0] + 1, topPos + p[1] + 1, a << 24 | c);
            }
        }
    }

    private static int[] veinPoint(int d, int x1, int y1, int x2, int y2) {
        int w = x2 - x1, h = y2 - y1;
        if (d < w) return new int[]{x1 + d, y1};
        d -= w;
        if (d < h) return new int[]{x2, y1 + d};
        d -= h;
        if (d < w) return new int[]{x2 - d, y2};
        d -= w;
        return new int[]{x1, y2 - d};
    }

    /**
     * A ring of light round (cx, cy) filled clockwise from the top as far as `progress`: a dim track, the filled
     * part bright with a glowing head (menu coordinates).
     */
    protected void progressRing(GuiGraphics g, int cx, int cy, float r, float progress, int rgb, long t) {
        int steps = Math.max(24, (int) (r * 7.0F));
        int last = Math.round(progress * steps);
        for (int k = 0; k < steps; k++) {
            float a = -PI / 2.0F + k / (float) steps * 2.0F * PI;
            int x = leftPos + cx + Math.round(Mth.cos(a) * r - 0.5F), y = topPos + cy + Math.round(Mth.sin(a) * r - 0.5F);
            int alpha;
            if (k < last) alpha = k >= last - 3 ? 255 : 170;
            else alpha = 40;
            g.fill(x, y, x + 1, y + 1, alpha << 24 | (k < last ? rgb : 0x0B2F5C));
        }
        if (progress > 0.0F && progress < 1.0F) {
            float a = -PI / 2.0F + progress * 2.0F * PI;
            sparkle(g, leftPos + cx + Math.round(Mth.cos(a) * r), topPos + cy + Math.round(Mth.sin(a) * r), rgb,
                    0.7F + 0.3F * Mth.sin(t / 90.0F));
        }
    }

    private int itemsHash = 1;
    private long itemsCheckedAt = -10000L;

    /** Whether the machine's slots changed since the last call (or a second went by): time to work out the craft again. */
    protected boolean itemsChanged(long t) {
        int hash = 1;
        for (int i = 0; i < menu.machineSlots(); i++) {
            ItemStack s = menu.items().getStackInSlot(i);
            hash = hash * 31 + (s.isEmpty() ? 0 : s.getItem().hashCode() * 7 + s.getCount());
        }
        if (hash == itemsHash && t - itemsCheckedAt < 1000L) return false;
        itemsHash = hash;
        itemsCheckedAt = t;
        return true;
    }

    /**
     * A ghost of what goes into an empty special slot (its item corner x, y): the item under a dark veil, and a red
     * rim pulsing round it when `missing` (a craft waits for it).
     */
    protected void ghostSlot(GuiGraphics g, int x, int y, ItemStack ghost, boolean missing, long t) {
        floatingItem(g, ghost, x + 8, y + 8, 1.0F);
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        g.fill(leftPos + x, topPos + y, leftPos + x + 16, topPos + y + 16, 0xA0200F08);
        if (missing) {
            int pulse = (int) (40 + 40 * (0.5F + 0.5F * Mth.sin(t / 160.0F)));
            g.renderOutline(leftPos + x - 1, topPos + y - 1, 18, 18, pulse * 2 << 24 | 0xFF5A4A);
        }
        g.pose().popPose();
    }

    /** A dark veil over a round part of the GUI (over the items there): a ghost of what isn't ready. */
    protected void dim(GuiGraphics g, float cx, float cy, float r, int argb) {
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        for (int y = (int) Math.floor(-r); y < r; y++) {
            float w = Mth.sqrt(Math.max(0.0F, r * r - (y + 0.5F) * (y + 0.5F)));
            int x1 = Math.round(cx - w), x2 = Math.round(cx + w);
            g.fill(leftPos + x1, topPos + Math.round(cy) + y, leftPos + x2, topPos + Math.round(cy) + y + 1, argb);
        }
        g.pose().popPose();
    }

    /** The colour of a petal or flower, by the dye colour its name starts with (Botania's red_petal...), else white. */
    public static int dyeColour(ItemStack stack) {
        ResourceLocation id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (id != null) {
            String path = id.getPath();
            for (net.minecraft.world.item.DyeColor dye : net.minecraft.world.item.DyeColor.values()) {
                if (path.startsWith(dye.getName() + "_")) {
                    int c = dye.getTextColor();
                    return c == 0 ? 0x3A3A3A : c;
                }
            }
        }
        return 0xFFFFFF;
    }

    /** An item drawn at a point of the GUI (menu coordinates, its middle), scaled, over the slots. */
    protected void floatingItem(GuiGraphics g, ItemStack stack, float x, float y, float scale) {
        if (stack.isEmpty() || scale <= 0.02F) return;
        g.pose().pushPose();
        g.pose().translate(leftPos + x, topPos + y, 100.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.renderItem(stack, -8, -8);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ the finished craft flying out

    private record Flight(ItemStack stack, long at) {
    }

    private void drawFlights(GuiGraphics g, long t) {
        for (Iterator<Flight> it = flights.iterator(); it.hasNext(); ) {
            Flight f = it.next();
            long a = t - f.at();
            if (a >= 480L) {
                burst(OUTPUT_MID_X, GRID_MID_Y, 7, GOLD_LIGHT, 0.6F);
                it.remove();
                continue;
            }
            float p = a / 480.0F, e = 1.0F - (1.0F - p) * (1.0F - p);
            float x = Mth.lerp(e, heartX, OUTPUT_MID_X), y = Mth.lerp(e, heartY, GRID_MID_Y) - 12.0F * Mth.sin(e * PI);
            floatingItem(g, f.stack(), x, y, 0.2F + 0.8F * Math.min(1.0F, p * 1.8F));
        }
    }

    // ------------------------------------------------------------------ input

    protected boolean inside(int x, int y, int w, int h, double mouseX, double mouseY) {
        double mx = mouseX - leftPos, my = mouseY - topPos;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!ready() || closingAt >= 0L) return true;
        if (button == 0 && minecraft != null && minecraft.gameMode != null) {
            if (inside(CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE, mouseX, mouseY)) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                onClose();
                return true;
            }
            if (inside(BUTTON_REDSTONE_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, MachineMenu.BUTTON_REDSTONE);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        if (mouseY >= top - SCROLL_UP && mouseY < top && Math.abs(mouseX - (left + imageWidth / 2.0D)) < scrollHalf) return false;
        if (inside(CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE, mouseX, mouseY)) return false;
        double mx = mouseX - left, my = mouseY - top;
        boolean inMachine = mx >= 0 && mx < imageWidth && my >= 0 && my < MACHINE_H;
        boolean inInventory = mx >= INV_PANEL_X1 && mx < INV_PANEL_X2 && my >= MACHINE_H && my < imageHeight;
        return !inMachine && !inInventory;
    }

    // ------------------------------------------------------------------ tooltips

    private void ownTooltips(GuiGraphics g, int mouseX, int mouseY) {
        List<Component> tip = new ArrayList<>();
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if (inside(CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE, mouseX, mouseY)) {
            tip.add(Component.translatable("gui.alfheimheart.close"));
        } else if (inside(BUTTON_REDSTONE_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
            tip.add(Component.translatable("gui.alfheimheart.redstone"));
            tip.add(Component.translatable("gui.alfheimheart.redstone." + menu.redstone().name().toLowerCase(Locale.ROOT))
                    .withStyle(ChatFormatting.GRAY));
        } else if (inside(POOL_X, POOL_Y, POOL_SIZE, POOL_SIZE, mouseX, mouseY)
                || inside(BAR_X1 - 14, BAR_Y1 - 3, BAR_X2 - BAR_X1 + 16, BAR_Y2 - BAR_Y1 + 6, mouseX, mouseY)) {
            tip.add(Component.translatable("gui.alfheimheart.mana", Format.mana(menu.mana()), Format.mana(menu.capacity()))
                    .withStyle(ChatFormatting.AQUA));
            tip.add(Component.translatable(menu.hasPool() ? "gui.alfheimheart.pool" : "gui.alfheimheart.no_pool").withStyle(ChatFormatting.GRAY));
            if (menu.status() == MachineStatus.NO_MANA) tip.add(status());
        } else if (hoveredSlot != null && hoveredSlot.index >= MachineBlockEntity.SPECIAL_START && hoveredSlot.index < menu.machineSlots()
                && !hoveredSlot.hasItem()) {
            specialSlotTooltip(tip, hoveredSlot.index - MachineBlockEntity.SPECIAL_START);
        } else if (hoveredSlot != null && hoveredSlot.index < MachineBlockEntity.SPECIAL_START && !hoveredSlot.hasItem()) {
            boolean input = hoveredSlot.index < MachineBlockEntity.OUTPUT_START;
            String k = input ? "gui.alfheimheart." + key() + ".slot.input" : "gui.alfheimheart.slot.output";
            tip.add(Component.translatable(k).withStyle(input ? ChatFormatting.GREEN : ChatFormatting.GOLD));
            tip.add(Component.translatable(k + ".tip").withStyle(ChatFormatting.GRAY));
        } else {
            heartTooltip(tip, mx, my);
        }
        if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mouseX, mouseY);
    }

    /** The status line, coloured. */
    protected Component status() {
        MachineStatus s = menu.status();
        ChatFormatting colour = switch (s) {
            case WORKING -> ChatFormatting.GREEN;
            case IDLE -> ChatFormatting.YELLOW;
            case NO_MANA, OUTPUT_FULL -> ChatFormatting.RED;
            case REDSTONE -> ChatFormatting.GRAY;
        };
        return Component.translatable("gui.alfheimheart.status." + s.name().toLowerCase(Locale.ROOT)).withStyle(colour);
    }

    /** The craft under way: "Crafting: 42% (1 200 / 5 200 mana)". */
    protected Component progressLine() {
        if (menu.jobCost() <= 0) {
            return Component.translatable("gui.alfheimheart.progress.time", Math.round(menu.progress() * 100.0F)).withStyle(ChatFormatting.AQUA);
        }
        return Component.translatable("gui.alfheimheart.progress", Math.round(menu.progress() * 100.0F),
                Format.mana(menu.jobCharged()), Format.mana(menu.jobCost())).withStyle(ChatFormatting.AQUA);
    }

    // ------------------------------------------------------------------ motes

    protected static final class Mote {
        float x, y, vx, vy, life, age;
        int color;
        boolean front;
    }

    protected void burst(int x, int y, int n, int color, float speed) {
        for (int i = 0; i < n; i++) {
            Mote m = new Mote();
            double a = random.nextDouble() * Math.PI * 2.0D;
            float v = (20.0F + random.nextFloat() * 60.0F) * speed;
            m.x = x;
            m.y = y;
            m.vx = (float) Math.cos(a) * v;
            m.vy = (float) Math.sin(a) * v;
            m.life = 0.5F + random.nextFloat() * 0.5F;
            m.color = color;
            m.front = true;
            motes.add(m);
        }
    }

    /** A mote drifting behind the items (the machine's heart spawns them). */
    protected void drift(float x, float y, float vx, float vy, float life, int color) {
        if (motes.size() > 80) return;
        Mote m = new Mote();
        m.x = x;
        m.y = y;
        m.vx = vx;
        m.vy = vy;
        m.life = life;
        m.color = color;
        motes.add(m);
    }

    private void updateMotes(float dt) {
        for (Iterator<Mote> it = motes.iterator(); it.hasNext(); ) {
            Mote m = it.next();
            m.age += dt;
            if (m.age >= m.life) {
                it.remove();
                continue;
            }
            m.x += m.vx * dt;
            m.y += m.vy * dt;
            if (m.front) {
                m.vx *= 1.0F - 3.0F * dt;
                m.vy *= 1.0F - 3.0F * dt;
            }
        }
    }

    private void drawMotes(GuiGraphics g, boolean front) {
        for (Mote m : motes) {
            if (m.front != front) continue;
            float p = m.age / m.life;
            int x = leftPos + Math.round(m.x), y = topPos + Math.round(m.y);
            if (m.front) {
                sparkle(g, x, y, m.color, Math.min(1.0F, (1.0F - p) * 3.0F));
            } else {
                float a = Math.min(1.0F, p * 5.0F) * Math.min(1.0F, (1.0F - p) * 4.0F);
                g.fill(x, y, x + 1, y + 1, (int) (a * 230) << 24 | m.color);
            }
        }
    }

    /** A four-pointed twinkle: a bright middle with fainter arms. */
    public static void sparkle(GuiGraphics g, int x, int y, int rgb, float a) {
        if (a <= 0.02F) return;
        int mid = (int) (a * 255) << 24 | 0xFFFFFF;
        int arm = (int) (a * 200) << 24 | rgb;
        int tip = (int) (a * 90) << 24 | rgb;
        g.fill(x, y, x + 1, y + 1, mid);
        g.fill(x - 1, y, x, y + 1, arm);
        g.fill(x + 1, y, x + 2, y + 1, arm);
        g.fill(x, y - 1, x + 1, y, arm);
        g.fill(x, y + 1, x + 1, y + 2, arm);
        if (a > 0.5F) {
            g.fill(x - 2, y, x - 1, y + 1, tip);
            g.fill(x + 2, y, x + 3, y + 1, tip);
            g.fill(x, y - 2, x + 1, y - 1, tip);
            g.fill(x, y + 2, x + 1, y + 3, tip);
        }
    }

    protected static float backOut(float p) {
        float c1 = 1.4F, c3 = c1 + 1.0F, q = Mth.clamp(p, 0.0F, 1.0F) - 1.0F;
        return Math.max(0.0F, 1.0F + c3 * q * q * q + c1 * q * q);
    }

    protected static float easeOut(float p) {
        float q = 1.0F - Mth.clamp(p, 0.0F, 1.0F);
        return 1.0F - q * q * q;
    }
}
