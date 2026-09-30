package com.reya.alfheimheart.portal.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.portal.Format;
import com.reya.alfheimheart.portal.PortalBlockEntity;
import com.reya.alfheimheart.portal.PortalMenu;
import com.reya.alfheimheart.portal.Trades;
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
 * The Elven Portal's GUI, in the mod's living-wood style: livingwood planks framed with a strip of livingrock,
 * mana crystals on the corners, the title on a livingrock plaque (textures/gui/elven_portal.png, made by
 * tools/portal/gen_gui.py). In the middle stands the elven moon gate, as the block is: a ring of cream
 * stone rimmed in gold with eight runes carved round it, vines and a gem, on a pedestal; the slots for the
 * elves are on its left, the slots for what they send back (rimmed in gold) on its right, the mana bar under
 * it. This draws what moves: the portal's swirl inside the ring (it opens, a little past its size, while the
 * portal has mana for a trade, and closes when it hasn't), the runes lighting up (one after another while
 * the elves trade), the gem (its colour is the portal's state), the natura crystals floating up while the
 * portal is open, the arrows lighting up on each trade, the traded item flying into the portal and its trade
 * flying out, the mana, the buttons, the title on its livingrock plaque, the red close button and the
 * twinkling lights.
 * <p>
 * It opens like a portal: a swirl opens in the middle and the panel grows out of it, a little past its size
 * and back. Closing, the panel folds back into the swirl, which closes.
 */
public class PortalScreen extends AbstractContainerScreen<PortalMenu> {
    static final ResourceLocation PANEL = new ResourceLocation(AlfheimHeart.MODID, "textures/gui/elven_portal.png");
    static final ResourceLocation WIDGETS = new ResourceLocation(AlfheimHeart.MODID, "textures/gui/elven_portal_widgets.png");
    static final ResourceLocation SWIRL = new ResourceLocation(AlfheimHeart.MODID, "textures/gui/portal_swirl.png");
    /** The panel texture has a margin round the GUI for the vines sticking out. */
    private static final int M = 12, TEX_W = PortalMenu.WIDTH + 2 * M, TEX_H = PortalMenu.HEIGHT + 2 * M;
    private static final int WIDGETS_W = 256, WIDGETS_H = 128;

    // layout (menu coordinates), the same as tools/gen_gui.py and tools/gen_widgets.py
    /** The machine panel's height, and the inventory panel hanging under it (x from, to). */
    private static final int MACHINE_H = 124, INV_PANEL_X1 = 28, INV_PANEL_X2 = 212;
    /** The portal inside the gate's ring, and the swirl's frames (side by side on their sheet). */
    private static final int SWIRL_X = 97, SWIRL_Y = 33, SWIRL_W = 46, SWIRL_H = 46, SWIRL_FRAMES = 16, SWIRL_FRAME_MS = 90;
    /** The whole gate with its pedestal, for its tooltip. */
    private static final int GATE_X1 = 84, GATE_Y1 = 20, GATE_X2 = 156, GATE_Y2 = 100;
    /** The gem in the ring's top: its middle pixel, and the four gems on the sheet (trading, idle, stuck, stopped). */
    private static final int GEM_X = 120, GEM_Y = 28, GEM_SIZE = 7, GEM_U = 160, GEM_V = 16;
    /** The runes carved round the ring (their glyphs' top-left corners), and their lit glyphs on the sheet. */
    private static final int[][] RUNES = {{144, 65}, {129, 80}, {107, 80}, {92, 65}, {92, 43}, {107, 28}, {129, 28}, {144, 43}};
    private static final int RUNE_U = 0, RUNE_V = 96, RUNE_SIZE = 5;
    /** The natura crystals resting on the pedestal's lower step (sprites' top-left), and their sprites (dim, lit). */
    private static final int[][] CRYSTALS = {{94, 85}, {142, 85}};
    private static final int CRYSTAL_U = 48, CRYSTAL_V = 96, CRYSTAL_W = 5, CRYSTAL_H = 8;
    /** The arrows into and out of the portal, and their lit forms on the sheet (lit, then brighter). */
    private static final int ARROW_IN_X = 74, ARROW_OUT_X = 155, ARROW_Y = 53, ARROW_W = 12, ARROW_H = 9, ARROW_U = 160, ARROW_V = 0;
    private static final int BAR_X1 = 64, BAR_Y1 = 108, BAR_X2 = 176, BAR_Y2 = 114, FILL_V = 80;
    private static final int BUTTON_REDSTONE_X = 18, BUTTON_Y = 102, BUTTON_SIZE = 16, ICON_V = 16;
    /** The pool light under the output slots: dark, or lit while a mana pool stands beside the portal. */
    private static final int POOL_X = 206, POOL_Y = 102, POOL_SIZE = 16, POOL_U = 192, POOL_V = 0;
    /**
     * The title scroll over the panel's top edge: its pieces on the widget sheet (rollers 16 wide, tiles of
     * paper 8 wide, two kinds), how far above the panel it hangs, and the paper's room beside the name.
     */
    private static final int SCROLL_H = 20, SCROLL_CAP = 16, SCROLL_V = 56, SCROLL_TILE_U = 32, SCROLL_TILE_W = 8,
            SCROLL_UP = 12, SCROLL_PAD = 6;
    /** The red close button on the panel's top-right corner, and its place on the sheet. */
    private static final int CLOSE_X = 229, CLOSE_Y = -8, CLOSE_SIZE = 16, CLOSE_U = 104, CLOSE_V = 0;
    /** The soft glow under the gold lights' twinkle, on the sheet. */
    private static final int GLOW_U = 104, GLOW_V = 16, GLOW_SIZE = 9;
    /** The row of the frame the mana runs along (tools/gen_gui.py draws it). */
    private static final int VEIN = 3;
    /**
     * The lights that twinkle, as in tools/portal/gen_gui.py: the mana crystals on the panels' corners, then (from
     * GOLD_LIGHTS on) the gold lights on the gate's vines.
     */
    private static final int[][] LIGHTS = {{1, 1}, {1, 122}, {238, 122}, {29, 212}, {210, 212}, {85, 72}, {153, 39}};
    private static final int GOLD_LIGHTS = 5;
    /** Where traded items fly from and to: the middle of the input slots, the portal, the middle of the output slots. */
    private static final int INPUT_MID_X = 44, OUTPUT_MID_X = 194, GRID_MID_Y = 56, PORTAL_X = 120, PORTAL_Y = 56;

    /** The opening: the swirl opens in SWIRL_MS, the panel grows from PANEL_FROM for PANEL_MS; input waits for READY_MS. */
    private static final long SWIRL_MS = 260L, PANEL_FROM = 90L, PANEL_MS = 380L, READY_MS = PANEL_FROM + PANEL_MS;
    /** The closing: the panel folds back into the swirl, which closes. */
    private static final long CLOSE_MS = 280L;
    /** A trade: the item flies into the portal, then what the elves send back flies out to the output slots. */
    private static final long FLY_IN_MS = 360L, FLY_OUT_FROM = 260L, FLY_OUT_MS = 440L, FLASH_MS = 450L;

    private static final float PI = (float) Math.PI;
    private static final int MANA_BRIGHT = 0xA6F6FF, GREEN_LIGHT = 0xB6F59A, GOLD_LIGHT = 0xFFE27A, PINK_LIGHT = 0xFF9AD8;

    private final long openedAt = Util.getMillis();
    private final List<Mote> motes = new ArrayList<>();
    private final List<Flight> flights = new ArrayList<>();
    private final RandomSource random = RandomSource.create();
    private float shownMana = 0.0F, shownOpen = -1.0F, shownRising;
    private int lastTrades = -1;
    private long tradeAt = -100000L, lastFrame, closingAt = -1L;
    private boolean welcomed;
    /** Half the width of the title scroll with its rollers, as last drawn (to keep clicks on it from dropping items). */
    private int scrollHalf = 60;

    public PortalScreen(PortalMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = PortalMenu.WIDTH;
        imageHeight = PortalMenu.HEIGHT;
    }

    @Override
    protected void init() {
        super.init();
        // leave room for the scroll over the panel
        topPos = Math.max(SCROLL_UP + 2, (height - imageHeight - SCROLL_UP) / 2 + SCROLL_UP);
        if (topPos + imageHeight > height) topPos = Math.max(0, height - imageHeight);
    }

    private long age() {
        return Util.getMillis() - openedAt;
    }

    private boolean ready() {
        return age() >= READY_MS;
    }

    /** Where the GUI draws outside its panel: the title scroll and the vines round the frame. */
    public List<Rect2i> extraAreas() {
        List<Rect2i> areas = new ArrayList<>();
        areas.add(new Rect2i(leftPos - M, topPos - SCROLL_UP - 2, imageWidth + 2 * M, SCROLL_UP + 2));
        areas.add(new Rect2i(leftPos - M, topPos - M, imageWidth + 2 * M, MACHINE_H + 2 * M));
        return areas;
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
        super.render(g, mouseX, mouseY, partialTick);
        drawFlights(g, t);
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);             // over the items
        drawMotes(g, true);
        g.pose().popPose();
        renderTooltip(g, mouseX, mouseY);
        if ((hoveredSlot == null || !hoveredSlot.hasItem()) && menu.getCarried().isEmpty()) ownTooltips(g, mouseX, mouseY);
    }

    /** Notices each new trade, to light the arrows, flash the swirl and send the items flying. */
    private void track(long t) {
        if (menu.capacity() <= 1) return;                   // the menu's numbers haven't arrived yet
        int trades = menu.trades();
        if (lastTrades >= 0 && trades != lastTrades) {
            tradeAt = t;
            if (flights.size() < 6) flights.add(new Flight(menu.lastIn(), menu.lastOut(), t));
        }
        lastTrades = trades;
    }

    /** The opening: a swirl opens in the middle, the panel grows out of it and the swirl settles into the gate. */
    private void renderOpening(GuiGraphics g, long t, float partialTick) {
        if (t >= PANEL_FROM) {
            float p = Math.min(1.0F, (t - PANEL_FROM) / (float) PANEL_MS);
            renderPanel(g, backOut(p), partialTick);
        }
        float grow = easeOut(Math.min(1.0F, t / (float) SWIRL_MS));
        float fade = t < SWIRL_MS ? Math.min(1.0F, t / 60.0F) : Math.max(0.0F, 1.0F - (t - SWIRL_MS) / (float) (READY_MS - SWIRL_MS));
        drawBigSwirl(g, t, 0.1F + 1.25F * grow, fade);
        if (t >= READY_MS - 120L && !welcomed) {
            welcomed = true;
            burst(PORTAL_X, PORTAL_Y, 16, GOLD_LIGHT, 1.4F);
            burst(PORTAL_X, PORTAL_Y, 10, PINK_LIGHT, 1.1F);
            burst(PORTAL_X, PORTAL_Y, 12, GREEN_LIGHT, 0.9F);
        }
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 250.0F);
        drawMotes(g, true);
        g.pose().popPose();
    }

    /** The closing: the panel folds back into the swirl, which shows as it comes and then closes to a point. */
    private void renderClosing(GuiGraphics g, long t, float partialTick) {
        float p = Mth.clamp(t / (float) CLOSE_MS, 0.0F, 1.0F);
        float s = 1.0F - p * p * (2.2F - 1.2F * p);
        if (s > 0.02F) renderPanel(g, s, partialTick);
        float a = p < 0.45F ? p / 0.45F : Math.max(0.0F, 1.0F - (p - 0.45F) / 0.55F);
        drawBigSwirl(g, age(), 0.05F + 1.2F * (1.0F - p * p), a);
    }

    /** The GUI at a scale round the portal (nothing hovered while it moves). */
    private void renderPanel(GuiGraphics g, float scale, float partialTick) {
        int cx = leftPos + PORTAL_X, cy = topPos + PORTAL_Y;
        g.pose().pushPose();
        g.pose().translate(cx, cy, 0.0F);
        g.pose().scale(scale, scale, 1.0F);
        g.pose().translate(-cx, -cy, 0.0F);
        super.render(g, -1000, -1000, partialTick);
        g.pose().popPose();
    }

    /** The swirl over everything, round the portal's middle, for the opening and the closing. */
    private void drawBigSwirl(GuiGraphics g, long t, float scale, float alpha) {
        if (alpha <= 0.01F || scale <= 0.01F) return;
        int frame = (int) (t / SWIRL_FRAME_MS % SWIRL_FRAMES);
        g.pose().pushPose();
        g.pose().translate(leftPos + PORTAL_X, topPos + PORTAL_Y, 200.0F);
        g.pose().scale(scale, scale, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1.0F, 1.0F, 1.0F, alpha);
        g.blit(SWIRL, -SWIRL_W / 2, -SWIRL_H / 2, frame * SWIRL_W, 0, SWIRL_W, SWIRL_H, SWIRL_W * SWIRL_FRAMES, SWIRL_H);
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

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        long t = age();
        long now = Util.getMillis();
        float dt = lastFrame == 0L ? 0.0F : Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;
        updateMotes(dt);
        idleMotes();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.blit(PANEL, leftPos - M, topPos - M, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);
        drawLights(g, t);
        drawTitle(g);
        drawClose(g, mouseX, mouseY);
        drawSwirl(g, t, dt);
        drawRunes(g, t);
        drawCrystals(g, t);
        g.drawManaged(() -> {
            drawMotes(g, false);
            drawGemGlow(g, t);
            drawVeinPulse(g, t);
        });
        drawGem(g);
        drawArrows(g, t);
        drawManaBar(g, t, dt);
        drawButtons(g, mouseX, mouseY, t);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // the title is on the scroll over the panel; the inventory needs no label
    }

    // ------------------------------------------------------------------ the portal

    /**
     * The swirl inside the ring: it opens from the middle, a little past its size and back, while the portal
     * has mana for a trade, and closes when it hasn't; it breathes, and each trade flashes it brighter.
     */
    private void drawSwirl(GuiGraphics g, long t, float dt) {
        float target = menu.open() ? 1.0F : 0.0F;
        if (shownOpen < 0.0F) shownOpen = menu.capacity() <= 1 ? 0.0F : target;
        shownOpen = target > shownOpen ? Math.min(target, shownOpen + dt * 2.2F) : Math.max(target, shownOpen - dt * 2.8F);
        float open = target > 0.0F ? popOut(shownOpen) : easeOut(shownOpen);
        if (open < 0.02F) return;
        int frame = (int) (t / SWIRL_FRAME_MS % SWIRL_FRAMES);
        float breathe = 0.9F + 0.1F * Mth.sin(t / 420.0F);
        g.pose().pushPose();
        g.pose().translate(leftPos + SWIRL_X + SWIRL_W / 2, topPos + SWIRL_Y + SWIRL_H / 2, 0.0F);
        g.pose().scale(open, open, 1.0F);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1.0F, 1.0F, 1.0F, Math.min(1.0F, shownOpen * 2.0F) * breathe);
        g.blit(SWIRL, -SWIRL_W / 2, -SWIRL_H / 2, frame * SWIRL_W, 0, SWIRL_W, SWIRL_H, SWIRL_W * SWIRL_FRAMES, SWIRL_H);
        long since = t - tradeAt;
        if (since >= 0L && since < FLASH_MS) {
            // a trade: the same frame again, added on, fading
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
            g.setColor(1.0F, 1.0F, 1.0F, 0.6F * Math.min(1.0F, open) * (1.0F - since / (float) FLASH_MS));
            g.blit(SWIRL, -SWIRL_W / 2, -SWIRL_H / 2, frame * SWIRL_W, 0, SWIRL_W, SWIRL_H, SWIRL_W * SWIRL_FRAMES, SWIRL_H);
            RenderSystem.defaultBlendFunc();
        }
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        g.pose().popPose();
    }

    /**
     * The runes round the ring: dark while the portal is shut, glowing softly while it is open, lit one after
     * another round the ring while the elves trade, and all at once when a trade goes through.
     */
    private void drawRunes(GuiGraphics g, long t) {
        float open = Math.max(0.0F, shownOpen);
        if (open <= 0.02F) return;
        boolean trading = menu.status() == PortalBlockEntity.Status.TRADING;
        long since = t - tradeAt;
        float flash = since >= 0L && since < FLASH_MS ? 1.0F - since / (float) FLASH_MS : 0.0F;
        float lead = (t / 110.0F) % RUNES.length;               // the rune the light has reached, going clockwise
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int k = 0; k < RUNES.length; k++) {
            float a = 0.3F + 0.12F * Mth.sin(t / 500.0F + k * 0.8F);
            if (trading) {
                float behind = (lead - k + RUNES.length) % RUNES.length;     // how far the light has passed it
                a = Math.max(a, 1.0F - behind / 3.0F);
            }
            a = Math.min(1.0F, Math.max(a, flash)) * open;
            if (a <= 0.02F) continue;
            g.setColor(1.0F, 1.0F, 1.0F, a);
            g.blit(WIDGETS, leftPos + RUNES[k][0], topPos + RUNES[k][1], RUNE_U + k * RUNE_SIZE, RUNE_V, RUNE_SIZE, RUNE_SIZE,
                    WIDGETS_W, WIDGETS_H);
        }
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** The natura crystals on the pedestal: resting while the portal is shut, floating up and bobbing, lit, while it is open. */
    private void drawCrystals(GuiGraphics g, long t) {
        float open = easeOut(Math.max(0.0F, shownOpen));
        RenderSystem.enableBlend();
        for (int i = 0; i < CRYSTALS.length; i++) {
            int lift = Math.round(open * (3.0F + 1.5F * Mth.sin(t / 420.0F + i * 2.1F)));
            int x = leftPos + CRYSTALS[i][0], y = topPos + CRYSTALS[i][1] - lift;
            g.blit(WIDGETS, x, y, CRYSTAL_U + (open > 0.5F ? CRYSTAL_W : 0), CRYSTAL_V, CRYSTAL_W, CRYSTAL_H, WIDGETS_W, WIDGETS_H);
            if (open > 0.5F) {
                float beat = 0.5F + 0.5F * Mth.sin(t / 300.0F + i * 1.3F);
                if (beat > 0.93F) sparkle(g, x + 2, y + 2, GREEN_LIGHT, (beat - 0.93F) / 0.07F);
            }
        }
    }

    /** Opens past 1 and back: the portal swells a little past its size and settles. */
    private static float popOut(float p) {
        float c1 = 1.9F, c3 = c1 + 1.0F, q = Mth.clamp(p, 0.0F, 1.0F) - 1.0F;
        return Math.max(0.0F, 1.0F + c3 * q * q * q + c1 * q * q);
    }

    /** The gem in the ring's top: green while trading, gold while waiting, red when stuck, grey when stopped by redstone. */
    private int gemKind() {
        return switch (menu.status()) {
            case TRADING -> 0;
            case IDLE -> 1;
            case NO_MANA, OUTPUT_FULL -> 2;
            case REDSTONE -> 3;
        };
    }

    private void drawGem(GuiGraphics g) {
        RenderSystem.enableBlend();
        g.blit(WIDGETS, leftPos + GEM_X - GEM_SIZE / 2, topPos + GEM_Y - GEM_SIZE / 2, GEM_U + gemKind() * GEM_SIZE, GEM_V,
                GEM_SIZE, GEM_SIZE, WIDGETS_W, WIDGETS_H);
    }

    /** Light spilling from the gem over the ring: a quick pulse while trading, a slow one when stuck. */
    private void drawGemGlow(GuiGraphics g, long t) {
        int kind = gemKind();
        if (kind == 1 || kind == 3) return;
        boolean trading = kind == 0;
        float beat = 0.5F + 0.5F * Mth.sin(t / (trading ? 170.0F : 380.0F));
        long since = t - tradeAt;
        float flash = trading && since >= 0L && since < FLASH_MS ? 1.0F - since / (float) FLASH_MS : 0.0F;
        float strength = Math.max(0.35F + 0.45F * beat, flash);
        int rgb = trading ? GREEN_LIGHT : 0xFF5A4A;
        int cx = leftPos + GEM_X, cy = topPos + GEM_Y;
        for (int dy = -7; dy <= 7; dy++) {
            for (int dx = -7; dx <= 7; dx++) {
                double d = Math.sqrt(dx * dx + dy * dy);
                if (d <= 4.6D || d > 7.5D) continue;
                float f = (float) (1.0D - (d - 4.6D) / 2.9D);
                int a = (int) (strength * 120.0F * f * f);
                if (a > 3) g.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, a << 24 | rgb);
            }
        }
    }

    /** The arrows glow while the elves trade, and flash in turn on each trade: into the portal, then out. */
    private void drawArrows(GuiGraphics g, long t) {
        boolean trading = menu.status() == PortalBlockEntity.Status.TRADING;
        long since = t - tradeAt;
        drawArrow(g, ARROW_IN_X, t, since, trading);
        drawArrow(g, ARROW_OUT_X, t, since - FLY_OUT_FROM, trading);
    }

    private void drawArrow(GuiGraphics g, int x, long t, long since, boolean trading) {
        float flash = since >= 0L && since < 400L ? 1.0F - since / 400.0F : 0.0F;
        float glow = trading ? 0.3F + 0.15F * Mth.sin(t / 240.0F + x) : 0.0F;
        float a = Math.max(flash, glow);
        if (a <= 0.02F) return;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        g.setColor(1.0F, 1.0F, 1.0F, a);
        g.blit(WIDGETS, leftPos + x, topPos + ARROW_Y, ARROW_U + (flash > 0.5F ? ARROW_W : 0), ARROW_V, ARROW_W, ARROW_H, WIDGETS_W, WIDGETS_H);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    /** The mana store: fills smoothly, a shine runs over it while mana comes in, and it blinks red when too low for a trade. */
    private void drawManaBar(GuiGraphics g, long t, float dt) {
        float target = Mth.clamp(menu.mana() / (float) menu.capacity(), 0.0F, 1.0F);
        if (menu.capacity() <= 1) target = 0.0F;
        float before = shownMana;
        shownMana += (target - shownMana) * Math.min(1.0F, dt * 6.0F);
        // is mana coming in? (smoothed, so it doesn't flicker as trades take it out again)
        shownRising += ((shownMana > before + 1.0E-5F || menu.status() == PortalBlockEntity.Status.TRADING ? 1.0F : 0.0F) - shownRising)
                * Math.min(1.0F, dt * 3.0F);
        int x1 = leftPos + BAR_X1, y1 = topPos + BAR_Y1, y2 = topPos + BAR_Y2;
        int w = Math.round((BAR_X2 - BAR_X1) * shownMana);
        if (w > 0) {
            RenderSystem.enableBlend();
            g.blit(WIDGETS, x1, y1, 0, FILL_V, w, BAR_Y2 - BAR_Y1, WIDGETS_W, WIDGETS_H);
            if (shownRising > 0.05F) {
                int sx = x1 + (int) ((t / 7L) % (w + 30)) - 15;
                for (int k = 0; k < 6; k++) {
                    int xx = sx + k;
                    int a = (int) ((40 + (k < 3 ? k : 5 - k) * 25) * shownRising);
                    if (xx >= x1 && xx < x1 + w) g.fill(xx, y1 + 1, xx + 1, y2 - 1, a << 24 | 0xFFFFFF);
                }
            }
            g.fill(x1 + w - 1, y1, x1 + w, y2, 0xFF000000 | MANA_BRIGHT);
        }
        if (menu.status() == PortalBlockEntity.Status.NO_MANA) {
            float beat = 0.5F + 0.5F * Mth.sin(t / 260.0F);
            g.fill(x1 + Math.max(0, w), y1, leftPos + BAR_X2, y2, (int) (20 + beat * 60) << 24 | 0xC82C26);
        }
    }

    /**
     * Mana runs round the vein inside the frame while the elves trade: two drops with a white head and a
     * cyan tail, lighting the panel beside them (as in Mana Garden's GUI).
     */
    private void drawVeinPulse(GuiGraphics g, long t) {
        if (menu.status() != PortalBlockEntity.Status.TRADING) return;
        int x1 = VEIN, y1 = VEIN, x2 = PortalMenu.WIDTH - 1 - VEIN, y2 = MACHINE_H - 1 - VEIN;
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

    // ------------------------------------------------------------------ traded items flying

    /** A trade's items: the one that went in flies from the input slots into the portal, its trade out to the output slots. */
    private static final class Flight {
        final ItemStack in, out;
        final long at;
        boolean arrived;

        Flight(ItemStack in, ItemStack out, long at) {
            this.in = in;
            this.out = out;
            this.at = at;
        }
    }

    private void drawFlights(GuiGraphics g, long t) {
        for (Iterator<Flight> it = flights.iterator(); it.hasNext(); ) {
            Flight f = it.next();
            long a = t - f.at;
            if (a < FLY_IN_MS) {
                // along a low arc into the portal, shrinking as it goes in
                float p = a / (float) FLY_IN_MS, e = p * p * (3.0F - 2.0F * p);
                float x = Mth.lerp(e, INPUT_MID_X, PORTAL_X), y = Mth.lerp(e, GRID_MID_Y, PORTAL_Y) - 10.0F * Mth.sin(e * PI);
                flyingItem(g, f.in, x, y, 1.0F - 0.85F * p * p);
            } else if (!f.arrived) {
                f.arrived = true;
                burst(PORTAL_X, PORTAL_Y, 8, GREEN_LIGHT, 0.7F);
            }
            long b = a - FLY_OUT_FROM;
            if (b >= 0L && b < FLY_OUT_MS) {
                // out of the portal, growing to full size on the way to the output slots
                float p = b / (float) FLY_OUT_MS, e = easeOut(p);
                float x = Mth.lerp(e, PORTAL_X, OUTPUT_MID_X), y = Mth.lerp(e, PORTAL_Y, GRID_MID_Y) - 10.0F * Mth.sin(e * PI);
                flyingItem(g, f.out, x, y, 0.15F + 0.85F * easeOut(Math.min(1.0F, p * 1.6F)));
            } else if (b >= FLY_OUT_MS) {
                burst(OUTPUT_MID_X, GRID_MID_Y, 7, GOLD_LIGHT, 0.6F);
                it.remove();
            }
        }
    }

    private void flyingItem(GuiGraphics g, ItemStack stack, float x, float y, float scale) {
        if (stack.isEmpty() || scale <= 0.02F) return;
        g.pose().pushPose();
        g.pose().translate(leftPos + x, topPos + y, 100.0F);    // over the items in the slots
        g.pose().scale(scale, scale, 1.0F);
        g.renderItem(stack, -8, -8);
        g.pose().popPose();
    }

    // ------------------------------------------------------------------ buttons and plates

    private void drawButtons(GuiGraphics g, int mouseX, int mouseY, long t) {
        int x = leftPos + BUTTON_REDSTONE_X, y = topPos + BUTTON_Y;
        boolean hot = ready() && inside(BUTTON_REDSTONE_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY);
        RenderSystem.enableBlend();
        g.blit(WIDGETS, x, y, hot ? 16 : 0, 0, BUTTON_SIZE, BUTTON_SIZE, WIDGETS_W, WIDGETS_H);
        g.blit(WIDGETS, x + 2, y + 2, menu.redstone().ordinal() * 12, ICON_V, 12, 12, WIDGETS_W, WIDGETS_H);
        // the pool light: a little mana pool, lit while one stands beside the portal
        boolean pool = menu.hasPool();
        g.blit(WIDGETS, leftPos + POOL_X, topPos + POOL_Y, POOL_U + (pool ? POOL_SIZE : 0), POOL_V, POOL_SIZE, POOL_SIZE, WIDGETS_W, WIDGETS_H);
        if (pool) {
            float beat = 0.5F + 0.5F * Mth.sin(t / 300.0F);
            if (beat > 0.9F) sparkle(g, leftPos + POOL_X + 7, topPos + POOL_Y + 4, MANA_BRIGHT, (beat - 0.9F) / 0.1F);
        }
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
        RenderSystem.enableBlend();
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
        RenderSystem.enableBlend();
        g.blit(WIDGETS, leftPos + CLOSE_X, topPos + CLOSE_Y, CLOSE_U + state * CLOSE_SIZE, CLOSE_V, CLOSE_SIZE, CLOSE_SIZE,
                WIDGETS_W, WIDGETS_H);
    }

    /**
     * The mana crystals on the corners and the gold lights on the gate's vines twinkle: a soft halo round each,
     * every one on its own beat, calm while the portal waits and livelier while the elves trade; now and then
     * one flashes a sparkle.
     */
    private void drawLights(GuiGraphics g, long t) {
        boolean busy = menu.status() == PortalBlockEntity.Status.TRADING;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        for (int i = 0; i < LIGHTS.length; i++) {
            float beat = 0.5F + 0.5F * Mth.sin(t / (busy ? 330.0F : 560.0F) + i * 1.9F);
            float a = (busy ? 0.4F : 0.25F) + beat * beat * (busy ? 0.6F : 0.5F);
            int x = leftPos + LIGHTS[i][0], y = topPos + LIGHTS[i][1];
            boolean gold = i >= GOLD_LIGHTS;
            if (gold) g.setColor(1.0F, 0.86F, 0.42F, a);
            else g.setColor(0.6F, 0.95F, 1.0F, a);
            g.blit(WIDGETS, x - GLOW_SIZE / 2, y - GLOW_SIZE / 2, GLOW_U, GLOW_V, GLOW_SIZE, GLOW_SIZE, WIDGETS_W, WIDGETS_H);
            g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
            if (beat > 0.94F && ready()) sparkle(g, x, y, gold ? 0xFFE47D : MANA_BRIGHT, (beat - 0.94F) / 0.06F);
        }
    }

    // ------------------------------------------------------------------ input

    private boolean inside(int x, int y, int w, int h, double mouseX, double mouseY) {
        double mx = mouseX - leftPos, my = mouseY - topPos;
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /** Over the portal inside the ring. */
    private boolean overPortal(double mouseX, double mouseY) {
        double u = (mouseX - leftPos - PORTAL_X) / (SWIRL_W / 2.0D), v = (mouseY - topPos - PORTAL_Y) / (SWIRL_H / 2.0D);
        return u * u + v * v <= 1.0D;
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
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PortalMenu.BUTTON_REDSTONE);
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
            if (overPortal(mouseX, mouseY)) {
                ItemStack carried = menu.getCarried();
                if (!carried.isEmpty() && Trades.accepts(minecraft.level, carried)) {
                    // an item dropped into the portal goes to the elves
                    minecraft.gameMode.handleInventoryButtonClick(menu.containerId, PortalMenu.BUTTON_SEND);
                    burst(PORTAL_X, PORTAL_Y, 10, GREEN_LIGHT, 0.8F);
                    return true;
                }
                if (carried.isEmpty()) {
                    // a touch sends a ripple of motes through it
                    burst((int) mouseX - leftPos, (int) mouseY - topPos, 8, random.nextBoolean() ? GOLD_LIGHT : PINK_LIGHT, 0.7F);
                    minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.AMETHYST_BLOCK_CHIME, 1.4F + random.nextFloat() * 0.4F, 0.5F));
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    protected boolean hasClickedOutside(double mouseX, double mouseY, int left, int top, int button) {
        // the scroll and the close button are part of the GUI: clicking them doesn't throw items out
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
        if (inside(CLOSE_X, CLOSE_Y, CLOSE_SIZE, CLOSE_SIZE, mouseX, mouseY)) {
            tip.add(Component.translatable("gui.alfheimheart.portal.close"));
        } else if (inside(BUTTON_REDSTONE_X, BUTTON_Y, BUTTON_SIZE, BUTTON_SIZE, mouseX, mouseY)) {
            tip.add(Component.translatable("gui.alfheimheart.portal.redstone"));
            tip.add(Component.translatable("gui.alfheimheart.portal.redstone." + menu.redstone().name().toLowerCase(Locale.ROOT))
                    .withStyle(ChatFormatting.GRAY));
        } else if (inside(POOL_X, POOL_Y, POOL_SIZE, POOL_SIZE, mouseX, mouseY)
                || inside(BAR_X1 - 14, BAR_Y1 - 3, BAR_X2 - BAR_X1 + 16, BAR_Y2 - BAR_Y1 + 6, mouseX, mouseY)) {
            tip.add(Component.translatable("gui.alfheimheart.portal.mana", Format.mana(menu.mana()), Format.mana(menu.capacity()))
                    .withStyle(ChatFormatting.AQUA));
            tip.add(Component.translatable(menu.hasPool() ? "gui.alfheimheart.portal.pool" : "gui.alfheimheart.portal.no_pool").withStyle(ChatFormatting.GRAY));
            if (menu.status() == PortalBlockEntity.Status.NO_MANA) tip.add(status());
        } else if (inside(GATE_X1, GATE_Y1, GATE_X2 - GATE_X1, GATE_Y2 - GATE_Y1, mouseX, mouseY)) {
            tip.add(Component.translatable("gui.alfheimheart.portal.portal").withStyle(ChatFormatting.GREEN));
            tip.add(status());
            tip.add(Component.translatable("gui.alfheimheart.portal.cost", Format.mana(menu.cost())).withStyle(ChatFormatting.AQUA));
            tip.add(Component.translatable("gui.alfheimheart.portal.speed", perSecond(menu.tradeTicks())).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("gui.alfheimheart.portal.offered", menu.offeredTrades()).withStyle(ChatFormatting.GRAY));
            tip.add(Component.translatable("gui.alfheimheart.portal.portal.drop").withStyle(ChatFormatting.DARK_GRAY));
        } else if (hoveredSlot != null && hoveredSlot.index < PortalBlockEntity.SLOTS && !hoveredSlot.hasItem()) {
            boolean input = hoveredSlot.index < PortalBlockEntity.OUTPUT_START;
            String key = input ? "gui.alfheimheart.portal.slot.input" : "gui.alfheimheart.portal.slot.output";
            tip.add(Component.translatable(key).withStyle(input ? ChatFormatting.GREEN : ChatFormatting.GOLD));
            tip.add(Component.translatable(key + ".tip").withStyle(ChatFormatting.GRAY));
        }
        if (!tip.isEmpty()) g.renderComponentTooltip(font, tip, mouseX, mouseY);
    }

    private Component status() {
        PortalBlockEntity.Status s = menu.status();
        ChatFormatting colour = switch (s) {
            case TRADING -> ChatFormatting.GREEN;
            case IDLE -> ChatFormatting.YELLOW;
            case NO_MANA, OUTPUT_FULL -> ChatFormatting.RED;
            case REDSTONE -> ChatFormatting.GRAY;
        };
        return Component.translatable("gui.alfheimheart.portal.status." + s.name().toLowerCase(Locale.ROOT)).withStyle(colour);
    }

    /** Trades a second for a trade every `ticks` ticks: "4", or "1.5". */
    private static String perSecond(int ticks) {
        float s = 20.0F / Math.max(1, ticks);
        return s == Math.round(s) ? Integer.toString(Math.round(s)) : String.format(Locale.ROOT, "%.1f", s);
    }

    // ------------------------------------------------------------------ motes: sparkles, and motes drawn into the portal

    /** A little light over the GUI (menu coordinates, pixels per second). */
    private static final class Mote {
        float x, y, vx, vy, life, age;
        int color;
        /** A spark over everything, or a mote drawn along the swirl's arms into the portal's heart. */
        boolean front;
        float angle;
    }

    private void burst(int x, int y, int n, int color, float speed) {
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

    /** While the portal is open, gold and pink motes are drawn in along its arms, more of them while the elves trade. */
    private void idleMotes() {
        if (!ready() || motes.size() > 60 || shownOpen < 0.7F) return;
        if (random.nextInt(menu.status() == PortalBlockEntity.Status.TRADING ? 7 : 16) != 0) return;
        Mote m = new Mote();
        m.angle = random.nextFloat() * 2.0F * PI;
        m.life = 1.4F + random.nextFloat() * 1.2F;
        m.color = random.nextInt(3) == 0 ? PINK_LIGHT : random.nextInt(4) == 0 ? 0xF4FFE8 : GOLD_LIGHT;
        m.x = PORTAL_X + Mth.cos(m.angle) * (SWIRL_W / 2.0F - 3.0F);
        m.y = PORTAL_Y + Mth.sin(m.angle) * (SWIRL_H / 2.0F - 4.0F);
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
            if (m.front) {
                m.x += m.vx * dt;
                m.y += m.vy * dt;
                m.vx *= 1.0F - 3.0F * dt;
                m.vy *= 1.0F - 3.0F * dt;
            } else {
                // from the rim to the heart, turning with the swirl (clockwise), faster as it nears the middle
                float r = 1.0F - m.age / m.life;
                m.angle += dt * (1.0F + 3.0F * (1.0F - r));
                m.x = PORTAL_X + Mth.cos(m.angle) * r * (SWIRL_W / 2.0F - 3.0F);
                m.y = PORTAL_Y + Mth.sin(m.angle) * r * (SWIRL_H / 2.0F - 4.0F);
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
                float a = Math.min(1.0F, p * 5.0F) * Math.min(1.0F, (1.0F - p) * 4.0F) * shownOpen;
                g.fill(x, y, x + 1, y + 1, (int) (a * 230) << 24 | m.color);
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

    // ------------------------------------------------------------------ easing

    /** Eases out past 1 and back: the panel swells a little past its size and settles. */
    private static float backOut(float p) {
        float c1 = 1.4F, c3 = c1 + 1.0F, q = p - 1.0F;
        return Math.max(0.0F, 1.0F + c3 * q * q * q + c1 * q * q);
    }

    private static float easeOut(float p) {
        float q = 1.0F - Mth.clamp(p, 0.0F, 1.0F);
        return 1.0F - q * q * q;
    }
}
