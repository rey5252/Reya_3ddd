package com.reya.managarden.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.reya.managarden.Config;
import com.reya.managarden.Flowers;
import com.reya.managarden.Format;
import com.reya.managarden.GreenhouseBlockEntity;
import com.reya.managarden.GreenhouseMenu;
import com.reya.managarden.ManaGarden;
import com.reya.managarden.UpgradeKind;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import vazkii.botania.api.BotaniaForgeCapabilities;
import vazkii.botania.api.mana.ManaItem;

/**
 * The Mana Greenhouse's GUI: a deep navy panel with rounded corners and a bright green edge, wound with
 * curly leaf vines and hung with little gold lights, eight dark slots outlined in green ringing the mana
 * heart (textures/gui/greenhouse.png, made by tools/gen_gui.py). This draws what moves: the mana in the
 * heart with its waves and bubbles, mana running along the vines from each growing flower to the heart,
 * the growth bar, the charge gauge, the buttons, the twinkling lights, the title on its parchment
 * scroll, the red close button, sparkles, and the garden keeper (see {@link Mascot}) at the left.
 * <p>
 * It opens like a flower: a bud swells in the middle and blooms, the panel grows out of it, a little
 * past its size and back, the inventory unrolls under it like a scroll, petals scatter, and the keeper
 * hops in and waves. Closing, the scroll rolls up and the panel folds back into the heart.
 */
public class GreenhouseScreen extends AbstractContainerScreen<GreenhouseMenu> {
    static final ResourceLocation PANEL = new ResourceLocation(ManaGarden.MODID, "textures/gui/greenhouse.png");
    static final ResourceLocation WIDGETS = new ResourceLocation(ManaGarden.MODID, "textures/gui/greenhouse_widgets.png");
    static final ResourceLocation BLOOM = new ResourceLocation(ManaGarden.MODID, "textures/gui/greenhouse_bloom.png");
    /** The panel texture has a margin round the GUI for the vines sticking out. */
    private static final int M = 12, TEX_W = GreenhouseMenu.WIDTH + 2 * M, TEX_H = GreenhouseMenu.HEIGHT + 2 * M;
    private static final int WIDGETS_W = 256, WIDGETS_H = 128;
    /** Where the buttons' icons, the heart's glass shine and the growth bar's fills are on the widget sheet. */
    private static final int ICON_V = 16, SHINE_U = 160, SHINE_V = 0, FILL_V = 80, FILL_PAUSED_V = 88;
    private static final int BLOOM_SIZE = 64, BLOOM_FRAMES = 8;

    // layout (menu coordinates), the same as tools/gen_gui.py
    private static final int HEART_X = GreenhouseMenu.HEART_X, HEART_Y = GreenhouseMenu.HEART_Y, ORB_R = 19;
    private static final int GAUGE_X1 = 223, GAUGE_Y1 = 42, GAUGE_X2 = 235, GAUGE_Y2 = 104;
    private static final int BAR_X1 = 52, BAR_Y1 = 115, BAR_X2 = 204, BAR_Y2 = 121;
    private static final int BUTTON_REDSTONE_X = 18, BUTTON_OUTPUT_X = 222, BUTTON_Y = 111, BUTTON_SIZE = 16;
    /** The machine panel's height, and the inventory panel hanging under it (x from, to). */
    private static final int MACHINE_H = 146, INV_PANEL_X1 = 36, INV_PANEL_X2 = 220;
    /**
     * The title scroll over the panel's top edge: its pieces on the widget sheet (rollers 16 wide, tiles of
     * paper 8 wide, two kinds), how far above the panel it hangs, and the paper's room beside the name.
     */
    private static final int SCROLL_H = 20, SCROLL_CAP = 16, SCROLL_V = 56, SCROLL_TILE_U = 32, SCROLL_TILE_W = 8,
            SCROLL_UP = 12, SCROLL_PAD = 6;
    /** The red close button on the panel's top-right corner, and its place on the sheet. */
    private static final int CLOSE_X = 245, CLOSE_Y = -8, CLOSE_SIZE = 16, CLOSE_U = 104, CLOSE_V = 0;
    /** The soft glow under the gold lights' twinkle, on the sheet. */
    private static final int GLOW_U = 104, GLOW_V = 16, GLOW_SIZE = 9;
    /** The row of the frame the mana runs along (tools/gen_gui.py draws it). */
    private static final int VEIN = 3;
    /** The gold lights on the vines: their middles, as in tools/gen_gui.py. */
    private static final int[][] LIGHTS = {
            {21, -7}, {-6, 21}, {-7, 63}, {-9, -9}, {238, -8}, {263, 17}, {264, 48}, {265, -9},
            {20, 155}, {-9, 126}, {-8, 101}, {-9, 153}, {234, 153}, {264, 124}, {266, 98}, {265, 153}};

    /**
     * The opening: the bud blooms in BLOOM_MS, the machine panel grows from PANEL_FROM for PANEL_MS, the
     * inventory unrolls under it from ROLL_FROM for ROLL_MS; input waits for READY_MS.
     */
    private static final long BLOOM_MS = 420L, PANEL_FROM = 200L, PANEL_MS = 480L, ROLL_FROM = 430L, ROLL_MS = 260L,
            READY_MS = ROLL_FROM + ROLL_MS;
    /** How far the machine panel's leaves and daisies hang below it. */
    private static final int MACHINE_SKIRT = 6;

    private static final int MANA_DEEP = 0xFF1B64B8, MANA_TOP = 0xFF55D9F7, MANA_FOAM = 0xFFA6F6FF;

    private final long openedAt = Util.getMillis();
    private final Mascot mascot = new Mascot();
    private final List<Mote> motes = new ArrayList<>();
    private final RandomSource random = RandomSource.create();
    private float shownMana = -1.0F, shownProgress, shownCharge;
    private int lastCycles = -1, lastLucky = -1;
    private long cycleAt = -100000L, luckyAt = -100000L, lastFrame;
    private int lastGainShown;
    private boolean opened;
    /** Half the width of the title scroll with its rollers, as last drawn (to keep clicks on it from dropping items). */
    private int scrollHalf = 70;

    public GreenhouseScreen(GreenhouseMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = GreenhouseMenu.WIDTH;
        imageHeight = GreenhouseMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        // centre the keeper and the panel together, and the scroll with the panel
        int left = Mascot.WIDTH - Mascot.OVERLAP;
        leftPos = Math.max(left + 2, (width - imageWidth - left) / 2 + left);
        if (leftPos + imageWidth > width) leftPos = (width - imageWidth) / 2;
        topPos = Math.max(SCROLL_UP + 2, (height - imageHeight - SCROLL_UP) / 2 + SCROLL_UP);
        if (topPos + imageHeight > height) topPos = Math.max(0, height - imageHeight);
    }

    private long age() {
        return Util.getMillis() - openedAt;
    }

    /** Where the GUI draws outside its panel: the keeper, the title scroll, the vines round the frame. */
    public List<net.minecraft.client.renderer.Rect2i> extraAreas() {
        List<net.minecraft.client.renderer.Rect2i> areas = new ArrayList<>();
        areas.add(new net.minecraft.client.renderer.Rect2i(leftPos + Mascot.X, topPos + Mascot.Y, Mascot.WIDTH, Mascot.HEIGHT));
        areas.add(new net.minecraft.client.renderer.Rect2i(leftPos - M, topPos - SCROLL_UP - 2, imageWidth + 2 * M, SCROLL_UP + 2));
        areas.add(new net.minecraft.client.renderer.Rect2i(leftPos - M, topPos - M, imageWidth + 2 * M, MACHINE_H + 2 * M));
        return areas;
    }

    private boolean ready() {
        return age() >= READY_MS;
    }

    // ------------------------------------------------------------------ render

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        long t = age();
        track(t);
        if (closingAt >= 0L) {
            renderClosing(g, Util.getMillis() - closingAt, partialTick);
            return;
        }
        if (t < READY_MS) {
            renderOpening(g, t, partialTick);
            return;
        }
        opened = true;
        super.render(g, mouseX, mouseY, partialTick);
        drawMotes(g, t, true);
        mascot.drawSpeech(g, font, leftPos, topPos, width);
        renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot == null || !hoveredSlot.hasItem()) ownTooltips(g, mouseX, mouseY);
    }

    /** Notices finished and lucky cycles, to flash the heart and scatter sparks. */
    private void track(long t) {
        if (menu.capacity() <= 1) return;                 // the menu's numbers haven't arrived yet
        int cycles = menu.cycles(), lucky = menu.luckyCycles();
        if (lastCycles >= 0 && cycles != lastCycles) {
            cycleAt = t;
            lastGainShown = menu.lastGain();
            burst(HEART_X, HEART_Y, 10, 0x55D9F7, 0.9F, false);
            mascot.pulse();
        }
        if (lastLucky >= 0 && lucky != lastLucky) {
            luckyAt = t;
            burst(HEART_X, HEART_Y, 22, 0xFFD35C, 1.4F, false);
            mascot.cheer(Component.translatable("gui.managarden.keeper.lucky"));
        }
        lastCycles = cycles;
        lastLucky = lucky;
    }

    /**
     * The opening: the bud in the middle swells and blooms (its frames), the panel grows out of it
     * with a little overshoot, its inventory rolled up under it, which then unrolls; the bloom fades
     * and its petals scatter.
     */
    private void renderOpening(GuiGraphics g, long t, float partialTick) {
        int cx = leftPos + HEART_X, cy = topPos + HEART_Y;
        if (t >= PANEL_FROM) {
            float p = Math.min(1.0F, (t - PANEL_FROM) / (float) PANEL_MS);
            float roll = t < ROLL_FROM ? 0.0F : easeOut((t - ROLL_FROM) / (float) ROLL_MS);
            renderPanel(g, backOut(p), roll, partialTick);
        }
        if (t < BLOOM_MS + 260L) {
            float grow = Math.min(1.0F, t / (float) BLOOM_MS);
            float fade = t < BLOOM_MS ? Math.min(1.0F, t / 90.0F) : Math.max(0.0F, 1.0F - (t - BLOOM_MS) / 260.0F);
            int frame = Math.min(BLOOM_FRAMES - 1, (int) (grow * BLOOM_FRAMES));
            float scale = 0.55F + 1.15F * (1.0F - (1.0F - grow) * (1.0F - grow));
            g.pose().pushPose();
            g.pose().translate(cx, cy, 200.0F);
            g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-25.0F * (1.0F - grow)));
            g.pose().scale(scale, scale, 1.0F);
            RenderSystem.enableBlend();
            g.setColor(1.0F, 1.0F, 1.0F, fade);
            g.blit(BLOOM, -BLOOM_SIZE / 2, -BLOOM_SIZE / 2, frame * BLOOM_SIZE, 0, BLOOM_SIZE, BLOOM_SIZE,
                    BLOOM_SIZE * BLOOM_FRAMES, BLOOM_SIZE);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            g.pose().popPose();
        }
        if (t >= BLOOM_MS - 80L && !petalsOut) {
            petalsOut = true;
            burst(HEART_X, HEART_Y, 26, 0xFFC4E2, 2.2F, true);
            burst(HEART_X, HEART_Y, 12, 0xFFFFFF, 1.6F, true);
            burst(HEART_X, HEART_Y, 14, 0x55D9F7, 1.2F, false);
        }
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 250.0F);
        drawMotes(g, t, true);
        g.pose().popPose();
    }

    private boolean petalsOut;

    /** Closing: the inventory rolls up, then the panel folds back into the heart and the bloom closes into a bud. */
    private static final long ROLL_UP_MS = 120L, FOLD_FROM = 90L, CLOSE_MS = 320L;
    private long closingAt = -1L;

    /**
     * The GUI at a scale round the heart, the inventory unrolled so far (0 to 1) under the machine
     * panel, its rolled-up rest drawn as a livingrock roll at the bottom.
     */
    private void renderPanel(GuiGraphics g, float scale, float roll, float partialTick) {
        int cx = leftPos + HEART_X, cy = topPos + HEART_Y;
        boolean clipped = roll < 1.0F;
        int shown = Math.round((imageHeight - MACHINE_H - MACHINE_SKIRT) * roll);
        int bottom = Math.round(cy + (MACHINE_H + MACHINE_SKIRT + shown - HEART_Y) * scale);
        if (clipped) g.enableScissor(0, 0, width, Math.max(0, bottom));
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.pose().translate(-cx, -cy, 0.0F);
        super.render(g, -1000, -1000, partialTick);           // nothing hovered while it moves
        g.pose().popPose();
        if (!clipped) return;
        g.disableScissor();
        // the roll: a parchment cylinder across the inventory panel, lit from above
        int x1 = Math.round(cx + (INV_PANEL_X1 - 1 - HEART_X) * scale), x2 = Math.round(cx + (INV_PANEL_X2 + 1 - HEART_X) * scale);
        int y = bottom - 1;
        g.fill(x1, y - 1, x2, y, 0x50000000);
        g.fill(x1, y, x2, y + 5, 0xFF4E3417);
        g.fill(x1 + 1, y + 1, x2 - 1, y + 2, 0xFFFFF9E0);
        g.fill(x1 + 1, y + 2, x2 - 1, y + 3, 0xFFF8E8B8);
        g.fill(x1 + 1, y + 3, x2 - 1, y + 4, 0xFFCBAA68);
    }

    private void renderClosing(GuiGraphics g, long t, float partialTick) {
        // the inventory rolls up first, then the panel folds into the heart as the bloom closes
        float roll = 1.0F - Math.min(1.0F, t / (float) ROLL_UP_MS);
        float p = Mth.clamp((t - FOLD_FROM) / (float) (CLOSE_MS - FOLD_FROM), 0.0F, 1.0F);
        int cx = leftPos + HEART_X, cy = topPos + HEART_Y;
        float s = 1.0F - p * p * (2.2F - 1.2F * p);
        if (s > 0.02F) renderPanel(g, s, roll * roll * (3.0F - 2.0F * roll), partialTick);
        if (t < FOLD_FROM) return;
        int frame = Math.max(0, Math.min(BLOOM_FRAMES - 1, (int) ((1.0F - p) * BLOOM_FRAMES)));
        float fade = p < 0.6F ? p / 0.6F : Math.max(0.0F, 1.0F - (p - 0.6F) / 0.4F);
        g.pose().pushPose();
        g.pose().translate(cx, cy, 200.0F);
        g.pose().mulPose(com.mojang.math.Axis.ZP.rotationDegrees(20.0F * p));
        float bs = 0.6F + 1.1F * (1.0F - p);
        g.pose().scale(bs, bs, 1.0F);
        RenderSystem.enableBlend();
        g.setColor(1.0F, 1.0F, 1.0F, fade);
        g.blit(BLOOM, -BLOOM_SIZE / 2, -BLOOM_SIZE / 2, frame * BLOOM_SIZE, 0, BLOOM_SIZE, BLOOM_SIZE,
                BLOOM_SIZE * BLOOM_FRAMES, BLOOM_SIZE);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.pose().popPose();
    }

    /** Esc or the inventory key: play the closing first (a second press closes at once). */
    @Override
    public void onClose() {
        if (closingAt < 0L && ready()) {
            closingAt = Util.getMillis();
            return;
        }
        super.onClose();
    }

    /** While it folds up, Esc or the inventory key closes it at once; other keys wait. */
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
        // the closing done: close for real between frames
        if (closingAt >= 0L && Util.getMillis() - closingAt >= CLOSE_MS) super.onClose();
    }

    /** Eases out past 1 and back: the panel swells a little past its size and settles. */
    private static float backOut(float p) {
        float c1 = 1.4F, c3 = c1 + 1.0F, q = p - 1.0F;
        return Math.max(0.0F, 1.0F + c3 * q * q * q + c1 * q * q);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        long t = age();
        long now = Util.getMillis();
        float dt = lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        updateMotes(dt);
        idleMotes(t);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(PANEL, leftPos - M, topPos - M, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
        drawLights(g, t);
        drawTitle(g);
        drawClose(g, mouseX, mouseY);
        boolean hoverKeeper = ready() && mascot.contains(mouseX - leftPos, mouseY - topPos);
        mascot.draw(g, leftPos, topPos, t, hoverKeeper);
        RenderSystem.enableBlend();
        g.drawManaged(() -> {
            drawMotes(g, t, false);
            drawHeart(g, t);
            drawFlows(g, t);
            drawSlotGlow(g, t);
            drawGrowthBar(g, t);
            drawGauge(g, t);
            drawVeinPulse(g, t);
        });
        drawButtons(g, mouseX, mouseY);
        drawHeartGlass(g, t);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // the title is on the plate over the panel; the inventory needs no label
        long t = age();
        if (t - cycleAt < 1400L && lastGainShown > 0) {
            // the mana a cycle brought rises through the heart's glass and fades at its top
            float p = (t - cycleAt) / 1400.0F;
            boolean lucky = t - luckyAt < 1400L;
            String s = "+" + Format.shortMana(lastGainShown);
            float in = Math.min(1.0F, p * 8.0F), out = Math.min(1.0F, (1.0F - p) * 2.5F);
            int alpha = (int) (255 * Math.min(in, out));
            if (alpha > 12) {
                int color = alpha << 24 | (lucky ? 0xFFE27A : 0xF2FFFF);
                // the dark edge fades faster than the text, so the number doesn't leave a smudge behind
                // (and text under alpha 4 would be drawn opaque)
                int edgeAlpha = alpha * alpha * 3 / (4 * 255);
                int edge = edgeAlpha << 24 | (lucky ? 0x5A3606 : 0x0B2F5C);
                int x = HEART_X - font.width(s) / 2, y = HEART_Y - 2 - Math.round(easeOut(p) * 11.0F);
                g.pose().pushPose();
                g.pose().translate(0.0F, 0.0F, 180.0F);     // over the planted flowers' items
                if (edgeAlpha >= 4) {
                    g.drawString(font, s, x - 1, y, edge, false);
                    g.drawString(font, s, x + 1, y, edge, false);
                    g.drawString(font, s, x, y - 1, edge, false);
                    g.drawString(font, s, x, y + 1, edge, false);
                }
                g.drawString(font, s, x, y, color, false);
                g.pose().popPose();
            }
        }
    }

    private static float easeOut(float p) {
        float q = 1.0F - Mth.clamp(p, 0.0F, 1.0F);
        return 1.0F - q * q * q;
    }

    // ------------------------------------------------------------------ the heart

    /**
     * The mana in the glass heart, filled to the store's level: deep blue at the bottom, bright at the
     * top, a wavy surface with foam, bubbles rising, a flash when a cycle ends (gold when lucky).
     */
    private void drawHeart(GuiGraphics g, long t) {
        float target = menu.mana() / (float) menu.capacity();
        target = Mth.clamp(target, 0.0F, 1.0F);
        if (shownMana < 0.0F) shownMana = 0.0F;
        shownMana += (target - shownMana) * (opened ? 0.08F : 0.03F);
        int cx = leftPos + HEART_X, cy = topPos + HEART_Y;
        int r = ORB_R - 2;
        float level = shownMana;
        if (level > 0.0005F) {
            float surface = cy + r - level * 2.0F * r;
            double time = t / 1000.0D;
            for (int x = -r; x < r; x++) {
                double px = x + 0.5D;
                double half = Math.sqrt(Math.max(0.0D, r * r - px * px));
                int top = (int) Math.ceil(cy - half), bottom = (int) Math.floor(cy + half);
                double wave = Math.sin(x * 0.45D + time * 3.1D) * 0.9D + Math.sin(x * 0.9D - time * 2.3D) * 0.5D;
                int s = (int) Math.round(surface + (level < 0.98F ? wave : 0.0D));
                int from = Math.max(top, s);
                if (from >= bottom) continue;
                int col = cx + x;
                for (int y = from; y < bottom; y++) {
                    float depth = (y - s) / (float) Math.max(1, bottom - s);
                    int c = y == from && s >= top ? MANA_FOAM : lerpColor(MANA_TOP, MANA_DEEP, Math.min(1.0F, depth * 1.6F + 0.15F));
                    g.fill(col, y, col + 1, y + 1, c);
                }
                // a lighter streak down the left side of the glass
                if (x == -r + 3 || x == -r + 4) {
                    g.fill(col, from + 1, col + 1, bottom - 2, 0x3CFFFFFF);
                }
            }
            // bubbles rising through the mana
            for (int k = 0; k < 7; k++) {
                long h = hash(k * 977L + 13L);
                double period = 1.6D + (h % 100) / 60.0D;
                double p = ((t / 1000.0D + (h >> 8) % 1000 / 1000.0D * period) % period) / period;
                int bx = cx - r + 4 + (int) ((h >> 20) % (2 * r - 8));
                int by = (int) Math.round(cy + r - 2 - p * 2 * r);
                double half = Math.sqrt(Math.max(0.0D, r * r - Math.pow(bx - cx + 0.5D, 2)));
                if (by > surface + 1 && by < cy + half - 1) g.fill(bx, by, bx + 1, by + 1, 0x90E8FFFF);
            }
        }
        // flash when a cycle finished
        long since = t - cycleAt;
        if (since < 500L) {
            float a = 1.0F - since / 500.0F;
            boolean lucky = t - luckyAt < 500L;
            int glow = lucky ? 0xFFE27A : 0xC8F8FF;
            ringGlow(g, cx, cy, ORB_R + 2 + (int) (since / 60L), (int) (a * 180) << 24 | glow);
        }
    }

    /** The glass's shine over the mana (a bright curved streak and a glint), from the widget sheet. */
    private void drawHeartGlass(GuiGraphics g, long t) {
        RenderSystem.enableBlend();
        g.blit(WIDGETS, leftPos + HEART_X - ORB_R, topPos + HEART_Y - ORB_R, SHINE_U, SHINE_V, 2 * ORB_R, 2 * ORB_R, WIDGETS_W, WIDGETS_H);
        // a glint that runs round the rim now and then
        long period = 5200L;
        long p = t % period;
        if (p < 700L) {
            double a = Math.PI * 1.1D + p / 700.0D * Math.PI * 0.8D;
            int x = leftPos + HEART_X + (int) Math.round(Math.cos(a) * (ORB_R - 1));
            int y = topPos + HEART_Y + (int) Math.round(Math.sin(a) * (ORB_R - 1));
            sparkle(g, x, y, 0xFFFFFF, 1.0F - Math.abs(p - 350L) / 350.0F);
        }
    }

    private void ringGlow(GuiGraphics g, int cx, int cy, int r, int argb) {
        for (int k = 0; k < 64; k++) {
            double a = k / 64.0D * Math.PI * 2.0D;
            int x = cx + (int) Math.round(Math.cos(a) * r), y = cy + (int) Math.round(Math.sin(a) * r);
            g.fill(x, y, x + 1, y + 1, argb);
        }
    }

    // ------------------------------------------------------------------ mana along the vines

    /**
     * While it grows, each planted flower sends drops of mana along its vine into the heart: they
     * leave in the flower's own colour and turn mana blue on the way.
     */
    private void drawFlows(GuiGraphics g, long t) {
        IItemHandler items = menu.items();
        boolean running = menu.running();
        for (int i = 0; i < GreenhouseBlockEntity.FLOWERS; i++) {
            ItemStack stack = items.getStackInSlot(GreenhouseBlockEntity.FLOWER_START + i);
            if (stack.isEmpty()) continue;
            int[] pos = GreenhouseMenu.FLOWER_POS[i];
            double sx = pos[0] + 8, sy = pos[1] + 8;
            double dx = sx - HEART_X, dy = sy - HEART_Y, d = Math.sqrt(dx * dx + dy * dy);
            double ux = dx / d, uy = dy / d;
            double x0 = Math.round(sx - ux * 10.5D), y0 = Math.round(sy - uy * 10.5D);
            double x1 = Math.round(HEART_X + ux * (ORB_R + 1)), y1 = Math.round(HEART_Y + uy * (ORB_R + 1));
            int flower = Flowers.color(stack) | 0xFF000000;
            if (!running) {
                // a dim, still vein
                continue;
            }
            for (int k = 0; k < 2; k++) {
                double p = ((t + i * 173L + k * 600L) % 1200L) / 1200.0D;
                int x = leftPos + (int) Math.round(x0 + (x1 - x0) * p);
                int y = topPos + (int) Math.round(y0 + (y1 - y0) * p);
                int c = lerpColor(brighten(flower), 0xFFA6F6FF, (float) p);
                g.fill(x, y, x + 1, y + 1, c);
                int glow = 0x70000000 | (c & 0xFFFFFF);
                if (Math.abs(ux) > Math.abs(uy) * 2) {
                    g.fill(x - 1, y, x, y + 1, glow);
                    g.fill(x + 1, y, x + 2, y + 1, glow);
                } else if (Math.abs(uy) > Math.abs(ux) * 2) {
                    g.fill(x, y - 1, x + 1, y, glow);
                    g.fill(x, y + 1, x + 1, y + 2, glow);
                } else {
                    g.fill(x - 1, y - 1, x, y, glow);
                    g.fill(x + 1, y + 1, x + 2, y + 2, glow);
                }
            }
        }
    }

    /** Planted flowers glow softly in their own colour; the glow breathes. */
    private void drawSlotGlow(GuiGraphics g, long t) {
        IItemHandler items = menu.items();
        for (int i = 0; i < GreenhouseBlockEntity.FLOWERS; i++) {
            ItemStack stack = items.getStackInSlot(GreenhouseBlockEntity.FLOWER_START + i);
            if (stack.isEmpty()) continue;
            int[] pos = GreenhouseMenu.FLOWER_POS[i];
            float breathe = 0.5F + 0.5F * Mth.sin(t / 700.0F + i * 0.8F);
            int alpha = (int) ((menu.running() ? 40 : 18) + breathe * (menu.running() ? 40 : 12));
            int c = alpha << 24 | brighten(Flowers.color(stack)) & 0xFFFFFF;
            int x = leftPos + pos[0], y = topPos + pos[1];
            g.fill(x + 3, y + 1, x + 13, y + 11, c);
            g.fill(x + 1, y + 3, x + 15, y + 9, c);
        }
    }

    // ------------------------------------------------------------------ bars

    /** Growth: fills as the cycle goes on; young green at the start, bright at the end, a shine running over it. */
    private void drawGrowthBar(GuiGraphics g, long t) {
        float target = menu.progress() / (float) menu.cycleTicks();
        if (target < shownProgress - 0.3F) shownProgress = 0.0F;
        shownProgress += (target - shownProgress) * 0.35F;
        int x1 = leftPos + BAR_X1, y1 = topPos + BAR_Y1, y2 = topPos + BAR_Y2;
        int w = Math.round((BAR_X2 - BAR_X1) * Mth.clamp(shownProgress, 0.0F, 1.0F));
        if (w <= 0) return;
        boolean running = menu.running();
        g.blit(WIDGETS, x1, y1, 0, running ? FILL_V : FILL_PAUSED_V, w, BAR_Y2 - BAR_Y1, WIDGETS_W, WIDGETS_H);
        if (running) {
            // a shine sweeping along the filled part
            int sx = x1 + (int) ((t / 6L) % (w + 30)) - 15;
            for (int k = 0; k < 6; k++) {
                int xx = sx + k;
                if (xx >= x1 && xx < x1 + w) g.fill(xx, y1 + 1, xx + 1, y2 - 1, (40 + (k < 3 ? k : 5 - k) * 25) << 24 | 0xFFFFFF);
            }
            // the growing tip
            g.fill(x1 + w - 1, y1, x1 + w, y2, 0xFFE4FAA8);
        }
    }

    /** The charge gauge: how full the mana item in the charge slot is. */
    private void drawGauge(GuiGraphics g, long t) {
        ItemStack stack = menu.items().getStackInSlot(GreenhouseBlockEntity.CHARGE_SLOT);
        ManaItem mana = stack.isEmpty() ? null : stack.getCapability(BotaniaForgeCapabilities.MANA_ITEM).resolve().orElse(null);
        float target = mana == null || mana.getMaxMana() <= 0 ? 0.0F : mana.getMana() / (float) mana.getMaxMana();
        shownCharge += (Mth.clamp(target, 0.0F, 1.0F) - shownCharge) * 0.2F;
        int h = Math.round((GAUGE_Y2 - GAUGE_Y1) * shownCharge);
        if (h <= 0) return;
        int x1 = leftPos + GAUGE_X1, x2 = leftPos + GAUGE_X2, y2 = topPos + GAUGE_Y2, top = y2 - h;
        for (int y = top; y < y2; y++) {
            float depth = (y - top) / (float) Math.max(1, GAUGE_Y2 - GAUGE_Y1);
            g.fill(x1, y, x2, y + 1, lerpColor(MANA_TOP, MANA_DEEP, Math.min(1.0F, depth * 1.4F)));
        }
        g.fill(x1, top, x2, top + 1, MANA_FOAM);
        g.fill(x1 + 2, top + 1, x1 + 3, y2 - 1, 0x40FFFFFF);
        boolean charging = target < 1.0F && menu.mana() > 0 && mana != null;
        if (charging) {
            // drops falling from the top into the item's mana
            int p = (int) (t / 40L % Math.max(1, GAUGE_Y2 - GAUGE_Y1 - h));
            g.fill(x1 + 5, topPos + GAUGE_Y1 + p, x1 + 7, topPos + GAUGE_Y1 + p + 2, 0xC0A6F6FF);
        } else if (target >= 1.0F) {
            float a = 0.5F + 0.5F * Mth.sin(t / 250.0F);
            g.fill(x1, top, x2, top + 2, (int) (a * 200) << 24 | 0xFFFFFF);
        }
    }

    /**
     * Mana runs round the vein inside the frame while the greenhouse works: two drops with a white head
     * and a cyan tail, lighting the panel beside them.
     */
    private void drawVeinPulse(GuiGraphics g, long t) {
        if (!menu.running()) return;
        int x1 = VEIN, y1 = VEIN, x2 = GreenhouseMenu.WIDTH - 1 - VEIN, y2 = MACHINE_H - 1 - VEIN;
        int perimeter = 2 * (x2 - x1) + 2 * (y2 - y1);
        for (int k = 0; k < 2; k++) {
            int head = (int) ((t / 22L + k * perimeter / 2) % perimeter);
            for (int j = 0; j < 18; j++) {
                int d = head - j;
                if (d < 0) d += perimeter;
                int[] p = veinPoint(d, x1, y1, x2, y2);
                if ((p[0] == x1 || p[0] == x2) && (p[1] == y1 || p[1] == y2)) continue;   // the rounded corner turns inwards here
                float f = j / 18.0F;
                int a = (int) (230 * (1.0F - f) * (1.0F - f)) + 10;
                int c = j == 0 ? 0xF2FFFF : j < 3 ? 0xA6F6FF : 0x55D9F7;
                int x = leftPos + p[0], y = topPos + p[1];
                g.fill(x, y, x + 1, y + 1, a << 24 | c);
                if (j < 7) {
                    // its light on the panel, one pixel inwards
                    int ix = p[0] == x1 ? 1 : p[0] == x2 ? -1 : 0, iy = p[1] == y1 ? 1 : p[1] == y2 ? -1 : 0;
                    int glow = (int) (70 * (1.0F - j / 7.0F)) << 24 | 0x55D9F7;
                    g.fill(x + ix, y + iy, x + ix + 1, y + iy + 1, glow);
                }
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

    // ------------------------------------------------------------------ buttons and plates

    private void drawButtons(GuiGraphics g, int mouseX, int mouseY) {
        drawButton(g, BUTTON_REDSTONE_X, mouseX, mouseY, false, menu.redstone().ordinal());
        drawButton(g, BUTTON_OUTPUT_X, mouseX, mouseY, !menu.output(), menu.output() ? (menu.bound() ? 5 : 3) : 4);
    }

    /** A leaf-green square button (widget sheet row 0) with its icon (row 1). */
    private void drawButton(GuiGraphics g, int bx, int mouseX, int mouseY, boolean off, int icon) {
        int x = leftPos + bx, y = topPos + BUTTON_Y;
        boolean hot = ready() && inside(bx, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY);
        int u = off ? 48 : hot ? 16 : 0;
        g.blit(WIDGETS, x, y, u, 0, BUTTON_SIZE, BUTTON_SIZE, WIDGETS_W, WIDGETS_H);
        g.blit(WIDGETS, x + 2, y + 2, icon * 12, ICON_V, 12, 12, WIDGETS_W, WIDGETS_H);
    }

    /**
     * The block's name on a parchment scroll over the panel's top edge, centred: the paper is tiled to
     * the name's width (two kinds of tile alternate, so it doesn't repeat), a roller at each end.
     */
    private void drawTitle(GuiGraphics g) {
        int mid = leftPos + imageWidth / 2;
        String title = getTitle().getString();
        int textW = PlateFont.width(title);
        int paper = textW + 2 * SCROLL_PAD, x0 = mid - paper / 2, x1 = x0 + paper;
        int top = topPos - SCROLL_UP;
        scrollHalf = paper / 2 + SCROLL_CAP;
        for (int x = x0, k = 0; x < x1; x += SCROLL_TILE_W, k++) {
            g.blit(WIDGETS, x, top, SCROLL_TILE_U + (k & 1) * SCROLL_TILE_W, SCROLL_V, Math.min(SCROLL_TILE_W, x1 - x), SCROLL_H,
                    WIDGETS_W, WIDGETS_H);
        }
        g.blit(WIDGETS, x0 - SCROLL_CAP, top, 0, SCROLL_V, SCROLL_CAP, SCROLL_H, WIDGETS_W, WIDGETS_H);
        g.blit(WIDGETS, x1, top, SCROLL_CAP, SCROLL_V, SCROLL_CAP, SCROLL_H, WIDGETS_W, WIDGETS_H);
        // pressed into the paper: a pale edge under the dark ink
        PlateFont.draw(g, title, mid - textW / 2 + 1, top + 9, 0xFFFFF9E0);
        PlateFont.draw(g, title, mid - textW / 2, top + 8, 0xFF4E3417);
    }

    /** The red close button on the panel's top-right corner: brighter under the mouse, pressed while the GUI folds up. */
    private void drawClose(GuiGraphics g, int mouseX, int mouseY) {
        boolean hot = ready() && inside(CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE, mouseX, mouseY);
        int state = closingAt >= 0L ? 2 : hot ? 1 : 0;
        g.blit(WIDGETS, leftPos + CLOSE_X, topPos + CLOSE_Y, CLOSE_U + state * CLOSE_SIZE, CLOSE_V, CLOSE_SIZE, CLOSE_SIZE,
                WIDGETS_W, WIDGETS_H);
    }

    /**
     * The gold lights on the vines twinkle: a soft halo round each, every one on its own beat, calm while
     * the greenhouse rests and livelier while its flowers grow; now and then one flashes a sparkle.
     */
    private void drawLights(GuiGraphics g, long t) {
        boolean busy = menu.running();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int i = 0; i < LIGHTS.length; i++) {
            float beat = 0.5F + 0.5F * Mth.sin(t / (busy ? 330.0F : 560.0F) + i * 1.9F);
            float a = (busy ? 0.4F : 0.25F) + beat * beat * (busy ? 0.6F : 0.5F);
            int x = leftPos + LIGHTS[i][0], y = topPos + LIGHTS[i][1];
            g.setColor(1.0F, 0.86F, 0.42F, a);
            g.blit(WIDGETS, x - GLOW_SIZE / 2, y - GLOW_SIZE / 2, GLOW_U, GLOW_V, GLOW_SIZE, GLOW_SIZE, WIDGETS_W, WIDGETS_H);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (beat > 0.94F && ready()) sparkle(g, x, y, 0xFFE47D, (beat - 0.94F) / 0.06F);
        }
    }

    // ------------------------------------------------------------------ input

    private boolean inside(int x, int y, int w, int h, double mouseX, double mouseY) {
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
            int id = -1;
            if (inside(BUTTON_REDSTONE_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
                id = GreenhouseMenu.BUTTON_REDSTONE;
            } else if (inside(BUTTON_OUTPUT_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
                id = hasShiftDown() && menu.bound() ? GreenhouseMenu.BUTTON_UNBIND : GreenhouseMenu.BUTTON_OUTPUT;
            }
            if (id >= 0) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (mascot.bubbleContains(mouseX, mouseY)) {
                mascot.hush();
                return true;
            }
            if (mascot.contains((int) mouseX - leftPos, (int) mouseY - topPos)) {
                mascot.poke(tip());
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.6F, 0.6F));
                burst(Mascot.X + Mascot.WIDTH / 2, Mascot.Y + Mascot.HEIGHT - 12, 8, 0xFFC4E2, 1.0F, true);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        // the scroll, the close button, the keeper and her bubble are part of the GUI: clicking them doesn't throw items out
        if (mascot.contains((int) mouseX - leftPos, (int) mouseY - topPos) || mascot.bubbleContains(mouseX, mouseY)) return false;
        if (mouseY >= top - SCROLL_UP && mouseY < top && Math.abs(mouseX - (left + imageWidth / 2.0D)) < scrollHalf) return false;
        if (inside(CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE, mouseX, mouseY)) return false;
        double mx = mouseX - left, my = mouseY - top;
        boolean inMachine = mx >= 0 && mx < imageWidth && my >= 0 && my < MACHINE_H;
        boolean inInventory = mx >= INV_PANEL_X1 && mx < INV_PANEL_X2 && my >= MACHINE_H && my < imageHeight;
        return !inMachine && !inInventory;
    }

    /** What the keeper says when poked: what the greenhouse needs, or a tip. */
    private Component tip() {
        GreenhouseBlockEntity.Status status = menu.status();
        if (status == GreenhouseBlockEntity.Status.NO_FLOWERS) return Component.translatable("gui.managarden.keeper.no_flowers");
        if (status == GreenhouseBlockEntity.Status.FULL && !menu.hasTarget()) return Component.translatable("gui.managarden.keeper.full");
        if (status == GreenhouseBlockEntity.Status.REDSTONE) return Component.translatable("gui.managarden.keeper.redstone");
        int n = (int) (Util.getMillis() / 1000L % 6L);
        return Component.translatable("gui.managarden.keeper.tip" + n);
    }

    // ------------------------------------------------------------------ tooltips

    private void ownTooltips(GuiGraphics g, int mouseX, int mouseY) {
        List<Component> tip = new ArrayList<>();
        int mx = mouseX - leftPos, my = mouseY - topPos;
        if ((mx - HEART_X) * (mx - HEART_X) + (my - HEART_Y) * (my - HEART_Y) <= (ORB_R + 1) * (ORB_R + 1)) {
            heartTooltip(tip);
        } else if (inside(BAR_X1 - 14, BAR_Y1 - 3, BAR_X2 - BAR_X1 + 16, BAR_Y2 - BAR_Y1 + 6, mouseX, mouseY)) {
            int percent = Math.round(100.0F * menu.progress() / menu.cycleTicks());
            tip.add(Component.translatable("gui.managarden.growth", percent).withStyle(ChatFormatting.GREEN));
            tip.add(Component.translatable("gui.managarden.cycle", seconds(menu.cycleTicks())).withStyle(ChatFormatting.GRAY));
            tip.add(status());
        } else if (inside(GAUGE_X1 - 2, GAUGE_Y1 - 2, GAUGE_X2 - GAUGE_X1 + 4, GAUGE_Y2 - GAUGE_Y1 + 4, mouseX, mouseY)) {
            ItemStack stack = menu.items().getStackInSlot(GreenhouseBlockEntity.CHARGE_SLOT);
            ManaItem mana = stack.isEmpty() ? null : stack.getCapability(BotaniaForgeCapabilities.MANA_ITEM).resolve().orElse(null);
            tip.add(Component.translatable("gui.managarden.charge").withStyle(ChatFormatting.AQUA));
            if (mana != null) {
                tip.add(Component.translatable("gui.managarden.mana_of", Format.mana(mana.getMana()), Format.mana(mana.getMaxMana()))
                        .withStyle(ChatFormatting.GRAY));
            } else {
                tip.add(Component.translatable("gui.managarden.slot.charge").withStyle(ChatFormatting.GRAY));
            }
        } else if (inside(CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE, mouseX, mouseY)) {
            tip.add(Component.translatable("gui.managarden.close"));
        } else if (inside(BUTTON_REDSTONE_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
            tip.add(Component.translatable("gui.managarden.redstone"));
            tip.add(Component.translatable("gui.managarden.redstone." + menu.redstone().name().toLowerCase(java.util.Locale.ROOT))
                    .withStyle(ChatFormatting.GRAY));
        } else if (inside(BUTTON_OUTPUT_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
            tip.add(Component.translatable("gui.managarden.output"));
            tip.add(Component.translatable(menu.output() ? "gui.managarden.output.on" : "gui.managarden.output.off")
                    .withStyle(menu.output() ? ChatFormatting.GREEN : ChatFormatting.GRAY));
            tip.add(Component.translatable(menu.hasTarget() ? "gui.managarden.output.target" : "gui.managarden.output.none")
                    .withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable(menu.bound() ? "gui.managarden.output.bound" : "gui.managarden.output.bind")
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else if (hoveredSlot != null && hoveredSlot.index < GreenhouseBlockEntity.SLOTS && !hoveredSlot.hasItem()) {
            int i = hoveredSlot.index;
            if (i < GreenhouseBlockEntity.UPGRADE_START) {
                tip.add(Component.translatable("gui.managarden.slot.flower"));
                tip.add(Component.translatable("gui.managarden.slot.flower.tip").withStyle(ChatFormatting.GRAY));
            } else if (i < GreenhouseBlockEntity.CHARGE_SLOT) {
                tip.add(Component.translatable("gui.managarden.slot.upgrade"));
                tip.add(Component.translatable("gui.managarden.slot.upgrade.tip", Config.MAX_UPGRADES.get()).withStyle(ChatFormatting.GRAY));
            } else {
                tip.add(Component.translatable("gui.managarden.slot.charge"));
            }
        }
        if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mouseX, mouseY);
    }

    private void heartTooltip(List<Component> tip) {
        tip.add(Component.translatable("gui.managarden.mana", Format.mana(menu.mana()), Format.mana(menu.capacity()))
                .withStyle(ChatFormatting.AQUA));
        tip.add(Component.translatable("gui.managarden.rate", Format.mana(Math.round(menu.manaPerSecond()))).withStyle(ChatFormatting.WHITE));
        tip.add(Component.translatable("gui.managarden.per_cycle", Format.mana(menu.manaPerCycle()), seconds(menu.cycleTicks()))
                .withStyle(ChatFormatting.GRAY));
        if (menu.luckPermille() > 0) {
            tip.add(Component.translatable("gui.managarden.luck", String.format(java.util.Locale.ROOT, "%.0f", menu.luckPermille() / 10.0D),
                    menu.luckBonusPercent()).withStyle(ChatFormatting.GOLD));
        }
        if (menu.kinds() > 1) {
            tip.add(Component.translatable("gui.managarden.harmony", menu.kinds(),
                    Math.round(Config.HARMONY_PER_KIND.get() * 100.0D * (menu.kinds() - 1))).withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        for (UpgradeKind kind : UpgradeKind.values()) {
            int n = menu.upgrades(kind);
            if (n > 0) tip.add(Component.translatable("gui.managarden.upgrade." + kind.id(), n).withStyle(ChatFormatting.DARK_AQUA));
        }
        tip.add(status());
    }

    private Component status() {
        GreenhouseBlockEntity.Status s = menu.status();
        ChatFormatting colour = s == GreenhouseBlockEntity.Status.RUNNING ? ChatFormatting.GREEN : ChatFormatting.RED;
        return Component.translatable("gui.managarden.status." + s.name().toLowerCase(java.util.Locale.ROOT)).withStyle(colour);
    }

    private static String seconds(int ticks) {
        float s = ticks / 20.0F;
        return s == Math.round(s) ? Integer.toString(Math.round(s)) : String.format(java.util.Locale.ROOT, "%.1f", s);
    }

    // ------------------------------------------------------------------ motes: sparkles, petals, pollen

    /** A little thing flying over the GUI (menu coordinates, pixels per second). */
    private static final class Mote {
        float x, y, vx, vy, life, age;
        int color;
        boolean petal, front;
        float spin;
    }

    private void burst(int x, int y, int n, int color, float speed, boolean petals) {
        for (int i = 0; i < n; i++) {
            Mote m = new Mote();
            double a = random.nextDouble() * Math.PI * 2.0D;
            float v = (20.0F + random.nextFloat() * 60.0F) * speed;
            m.x = x;
            m.y = y;
            m.vx = (float) Math.cos(a) * v;
            m.vy = (float) Math.sin(a) * v - (petals ? 25.0F : 0.0F);
            m.life = petals ? 1.3F + random.nextFloat() * 0.9F : 0.5F + random.nextFloat() * 0.5F;
            m.color = color;
            m.petal = petals;
            m.front = true;
            m.spin = random.nextFloat() * 6.0F;
            motes.add(m);
        }
    }

    /** Pollen and mana motes drifting up over the panel now and then. */
    private void idleMotes(long t) {
        if (!ready() || motes.size() > 60) return;
        if (random.nextInt(menu.running() ? 18 : 45) == 0) {
            Mote m = new Mote();
            m.x = 14 + random.nextInt(GreenhouseMenu.WIDTH - 28);
            m.y = MACHINE_H - 14;
            m.vx = (random.nextFloat() - 0.5F) * 4.0F;
            m.vy = -6.0F - random.nextFloat() * 8.0F;
            m.life = 3.0F + random.nextFloat() * 3.0F;
            m.color = random.nextInt(3) == 0 ? 0xE4FAA8 : 0xA6F6FF;
            motes.add(m);
        }
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
            if (m.petal) {
                m.vx *= 1.0F - 1.6F * dt;
                m.vy = m.vy * (1.0F - 1.2F * dt) + 38.0F * dt;
                m.x += Mth.sin(m.age * 5.0F + m.spin) * 12.0F * dt;
            } else if (m.front) {
                m.vx *= 1.0F - 3.0F * dt;
                m.vy *= 1.0F - 3.0F * dt;
            } else {
                m.x += Mth.sin(m.age * 1.7F + m.spin) * 3.0F * dt;
            }
        }
    }

    private void drawMotes(GuiGraphics g, long t, boolean front) {
        for (Mote m : motes) {
            if (m.front != front) continue;
            float left = 1.0F - m.age / m.life;
            float a = Math.min(1.0F, left * 3.0F) * (m.front ? 1.0F : Math.min(1.0F, m.age * 2.0F) * 0.7F);
            int x = leftPos + Math.round(m.x), y = topPos + Math.round(m.y);
            if (m.petal) {
                int c = (int) (a * 255) << 24 | m.color;
                int dark = (int) (a * 255) << 24 | darken(m.color);
                boolean flat = ((int) (m.age * 8.0F + m.spin) & 1) == 0;
                if (flat) {
                    g.fill(x, y, x + 2, y + 1, c);
                    g.fill(x + 1, y + 1, x + 3, y + 2, dark);
                } else {
                    g.fill(x, y, x + 1, y + 2, c);
                    g.fill(x + 1, y + 1, x + 2, y + 3, dark);
                }
            } else if (m.front) {
                sparkle(g, x, y, m.color, a);
            } else {
                g.fill(x, y, x + 1, y + 1, (int) (a * 200) << 24 | m.color);
            }
        }
    }

    /** A four-pointed twinkle: a bright middle with fainter arms. */
    static void sparkle(GuiGraphics g, int x, int y, int rgb, float a) {
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

    // ------------------------------------------------------------------ colour helpers

    static int lerpColor(int a, int b, float t) {
        t = Mth.clamp(t, 0.0F, 1.0F);
        int aa = a >>> 24, ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
        int ba = b >>> 24, br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
        return (int) (aa + (ba - aa) * t) << 24 | (int) (ar + (br - ar) * t) << 16 | (int) (ag + (bg - ag) * t) << 8 | (int) (ab + (bb - ab) * t);
    }

    /** Flower colours are often dark (Botania's HUD colours); lift them so they glow on the dark panel. */
    private static int brighten(int rgb) {
        int r = rgb >> 16 & 0xFF, g = rgb >> 8 & 0xFF, b = rgb & 0xFF;
        int max = Math.max(r, Math.max(g, b));
        if (max < 200) {
            float k = 200.0F / Math.max(1, max);
            r = Math.min(255, (int) (r * k));
            g = Math.min(255, (int) (g * k));
            b = Math.min(255, (int) (b * k));
        }
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int darken(int rgb) {
        return (rgb >> 16 & 0xFF) * 3 / 4 << 16 | (rgb >> 8 & 0xFF) * 3 / 4 << 8 | (rgb & 0xFF) * 3 / 4;
    }

    static long hash(long x) {
        x ^= x >>> 33;
        x *= 0xFF51AFD7ED558CCDL;
        x ^= x >>> 33;
        x *= 0xC4CEB9FE1A85EC53L;
        x ^= x >>> 33;
        return x & Long.MAX_VALUE;
    }
}
