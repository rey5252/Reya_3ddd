package com.reya.mobfarm.client;

import org.joml.Quaternionf;

import com.reya.mobfarm.FarmTier;
import com.reya.mobfarm.farm.MobFarmBlockEntity;
import com.reya.mobfarm.farm.MobFarmMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Mirror-symmetric farm GUI, coloured by tier: lasso on the left, a window with the caught mob
 * slowly turning in the middle, loot grid on the right, a progress bar underneath and the player
 * inventory centred at the bottom. Drawn entirely in code.
 */
public class MobFarmScreen extends AbstractContainerScreen<MobFarmMenu> {
    // Symmetric layout, all mirrored around the middle of the body (x = BODY_X + 108)
    private static final int B = MobFarmMenu.BODY_X;
    private static final int LEFT_PANEL_X = B + 8;
    private static final int RIGHT_PANEL_X = B + 150;
    private static final int PANEL_W = 58;
    private static final int WINDOW_X = B + 72;
    private static final int WINDOW_W = 72;
    private static final int PANEL_Y = 18;
    private static final int PANEL_H = 64;
    private static final int BAR_X = B + 8;
    private static final int BAR_Y = 88;
    private static final int BAR2_Y = 99;
    private static final int BAR_W = 200;
    private static final int BAR_H = 6;

    private static final int TEXT = 0xFFF4ECDC;
    private static final int TEXT_DIM = 0xFFC9B9A0;

    /** Ornate palette per tier: background, its shadow, trim (and its light/dark), slot face. */
    private record Palette(int bg, int bgDark, int trim, int trimLight, int trimDark) {
    }

    private static Palette palette(FarmTier tier) {
        return switch (tier) {
            case WOODEN -> new Palette(0xFF6B3F1F, 0xFF4A2A12, 0xFFC9913F, 0xFFE8B96A, 0xFF7A5222);
            case STONE -> new Palette(0xFF4E5258, 0xFF33363B, 0xFFB9BEC6, 0xFFE6E9EE, 0xFF6C7178);
            case IRON -> new Palette(0xFF3C4A5E, 0xFF27303E, 0xFFC8D2DE, 0xFFF2F6FA, 0xFF6E7A88);
            case GOLDEN -> new Palette(0xFF7A2020, 0xFF521414, 0xFFE3B341, 0xFFFFE08A, 0xFF8A6414);
            case DIAMOND -> new Palette(0xFF1C5563, 0xFF123A44, 0xFFE3B341, 0xFFFFE08A, 0xFF8A6414);
            case NETHERITE -> new Palette(0xFF3A2340, 0xFF24152A, 0xFFE0A080, 0xFFFFD0B8, 0xFF8A5A48);
        };
    }

    // Light sage slot faces, like carved stone set into the panel
    private static final int SLOT_FACE = 0xFF9AA69A;
    private static final int SLOT_LIGHT = 0xFFC4CEC2;
    private static final int SLOT_DARK = 0xFF5E685E;

    public MobFarmScreen(MobFarmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = MobFarmMenu.WIDTH;
        imageHeight = MobFarmMenu.HEIGHT;
    }

    // Upgrade tabs on both sides
    private static final int TAB_Y = 24;
    private static final int TAB_H = 52;

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
        if (hoveredSlot == null || !hoveredSlot.hasItem()) {
            boolean overTab = inside(mouseX, mouseY, leftPos, topPos + TAB_Y, B, TAB_H)
                    || inside(mouseX, mouseY, leftPos + imageWidth - B, topPos + TAB_Y, B, TAB_H);
            if (overTab) {
                g.renderComponentTooltip(font, menu.upgrades().describe(menu.tier()), mouseX, mouseY);
            } else if (inside(mouseX, mouseY, leftPos + BAR_X, topPos + BAR_Y, BAR_W, BAR2_Y - BAR_Y + BAR_H)) {
                g.renderTooltip(font, statusText(), mouseX, mouseY);
            }
        }
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        updateSmoothBars(partialTick);
        // Thousands of 1px fills: draw them as one batch instead of one draw call each (that was the lag).
        g.drawManaged(() -> drawFrameParts(g));
        drawWindow(g, leftPos + WINDOW_X, topPos + PANEL_Y, menu.tier());
        g.drawManaged(() -> drawSlotsAndBars(g));
    }

    private void drawFrameParts(GuiGraphics g) {
        FarmTier tier = menu.tier();
        Palette p = palette(tier);
        drawTab(g, leftPos, topPos + TAB_Y, p, true);
        drawTab(g, leftPos + imageWidth - B - 3, topPos + TAB_Y, p, false);

        int x = leftPos + B;
        int y = topPos;
        int x2 = x + MobFarmMenu.BODY_W;
        int y2 = y + imageHeight;
        drawOrnateBox(g, x, y, x2, y2, p);

        // Title on a plaque hanging over the top edge; the freed space holds gold filigree.
        int mid = x + MobFarmMenu.BODY_W / 2;
        drawTitlePlaque(g, mid, y - 9, p);
        drawFiligree(g, mid, y, p);

        // Lasso socket, loot box and the mob window, mirrored around the middle
        drawSocketBox(g, leftPos + LEFT_PANEL_X, y + PANEL_Y, PANEL_W, PANEL_H, p);
        drawSocketBox(g, leftPos + RIGHT_PANEL_X, y + PANEL_Y, PANEL_W, PANEL_H, p);

        // Ornament line above the inventory
        int dy = y + MobFarmMenu.INV_Y - 8;
        g.fill(x + 16, dy, x2 - 16, dy + 1, p.trimDark);
        drawDiamond(g, x + 13, dy, p);
        drawDiamond(g, x2 - 14, dy, p);
        drawDiamond(g, mid, dy, p);

        // Lock in the top-right corner while redstone holds the farm
        if (menu.status() == MobFarmBlockEntity.STATUS_REDSTONE) drawLock(g, x2 - 20, y + 5, p);
    }

    private void drawSlotsAndBars(GuiGraphics g) {
        Palette p = palette(menu.tier());
        int y = topPos;
        for (Slot slot : menu.slots) {
            boolean machine = slot.index < MobFarmBlockEntity.SLOT_COUNT;
            if (machine) {
                drawStoneSlot(g, leftPos + slot.x, y + slot.y, p, slot.index == 0);
            } else {
                drawInventorySlot(g, leftPos + slot.x, y + slot.y, p);
            }
            boolean upgrade = slot.index >= MobFarmBlockEntity.UPGRADE_START && slot.index < MobFarmBlockEntity.SLOT_COUNT;
            if (upgrade && !slot.hasItem()) drawGhostBook(g, leftPos + slot.x, y + slot.y);
        }

        drawBars(g, leftPos + BAR_X, y, p);
    }

    private static int withAlpha(int color, float alpha) {
        return ((int) (((color >>> 24) & 0xFF) * alpha) << 24) | (color & 0xFFFFFF);
    }

    // ---------------------------------------------------------------- smooth bars

    private float shownProgress;
    private float shownFullness;
    private long lastFrame = Util.getMillis();

    /**
     * The server only sends numbers once a tick, so the bars would step. Progress is advanced
     * with the partial tick between updates, and fullness eases toward its new value.
     */
    private void updateSmoothBars(float partialTick) {
        long now = Util.getMillis();
        float dt = Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;

        float progress = 0.0F;
        if (menu.status() == MobFarmBlockEntity.STATUS_RUNNING) {
            progress = Mth.clamp((menu.progress() + partialTick) / menu.maxProgress(), 0.0F, 1.0F);
        } else if (menu.status() != MobFarmBlockEntity.STATUS_NO_MOB) {
            progress = Mth.clamp(menu.progress() / (float) menu.maxProgress(), 0.0F, 1.0F);
        }
        // A new cycle starts: snap back instead of sliding backwards across the bar.
        if (progress < shownProgress - 0.25F) {
            shownProgress = progress;
        } else {
            shownProgress += (progress - shownProgress) * Math.min(1.0F, dt * 20.0F);
        }

        float fullness = fillFraction();
        shownFullness += (fullness - shownFullness) * Math.min(1.0F, dt * 6.0F);
    }

    /**
     * How full the loot slots are. Any used slot counts for half of its share right away (so a
     * few items are already visible), the other half grows with the stack size.
     */
    private float fillFraction() {
        float total = 0.0F;
        for (int i = MobFarmBlockEntity.OUTPUT_START; i < MobFarmBlockEntity.OUTPUT_START + MobFarmBlockEntity.OUTPUT_COUNT; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (!stack.isEmpty()) total += 0.5F + 0.5F * stack.getCount() / (float) stack.getMaxStackSize();
        }
        return total / MobFarmBlockEntity.OUTPUT_COUNT;
    }

    private static final int BORDER_DARK = 0xFF1C1822;
    private static final int BORDER_MID = 0xFF2C2634;
    private static final int CLIP = 0xFF8A8E9A;
    private static final int CLIP_LIGHT = 0xFFC8CCD6;

    /**
     * Wide dark border with a thin trim line inside, square-spiral (Greek key) ornaments in the
     * corners, metal clips on the outer edge and twinkling sparks on the background.
     */
    private void drawOrnateBox(GuiGraphics g, int x, int y, int x2, int y2, Palette p) {
        g.fill(x - 1, y - 1, x2 + 1, y2 + 1, 0xFF000000);
        g.fill(x, y, x2, y2, BORDER_DARK);
        g.fill(x + 1, y + 1, x2 - 1, y + 2, BORDER_MID);
        g.fill(x + 1, y + 1, x + 2, y2 - 1, BORDER_MID);
        // thin trim line, then the background
        g.fill(x + 3, y + 3, x2 - 3, y2 - 3, p.trim);
        g.fill(x + 4, y + 4, x2 - 4, y2 - 4, p.bg);
        g.fill(x + 4, y + 4, x2 - 4, y + 5, p.bgDark);

        drawSparks(g, x + 6, y + 6, x2 - 6, y2 - 6, p);

        drawKey(g, x + 3, y + 3, 1, 1, p);
        drawKey(g, x2 - 4, y + 3, -1, 1, p);
        drawKey(g, x + 3, y2 - 4, 1, -1, p);
        drawKey(g, x2 - 4, y2 - 4, -1, -1, p);

        // metal clips on the outer edge, near each corner (mirrored)
        for (int cy : new int[]{y + 14, y2 - 20}) {
            drawClip(g, x - 3, cy);
            drawClip(g, x2, cy);
        }
        // little accent triangles in the outermost corners
        for (int i = 0; i < 3; i++) {
            g.fill(x2 - 3 + i, y + i, x2, y + i + 1, p.trim);
            g.fill(x, y + i, x + 3 - i, y + i + 1, p.trim);
            g.fill(x2 - 3 + i, y2 - 1 - i, x2, y2 - i, p.trim);
            g.fill(x, y2 - 1 - i, x + 3 - i, y2 - i, p.trim);
        }
    }

    /** Square spiral (Greek key) 7x7 sitting on the inner corner, oriented by (dx, dy). */
    private static void drawKey(GuiGraphics g, int x, int y, int dx, int dy, Palette p) {
        String[] key = {
                "1111111",
                "1000000",
                "1011111",
                "1010001",
                "1010101",
                "1010111",
                "1010000"};
        for (int r = 0; r < key.length; r++) {
            for (int c = 0; c < key[r].length(); c++) {
                int px = x + c * dx;
                int py = y + r * dy;
                if (key[r].charAt(c) == '1') {
                    px(g, px, py, (r + c) % 3 == 0 ? p.trimLight : p.trim);
                } else {
                    px(g, px, py, BORDER_DARK);
                }
            }
        }
    }

    private static void drawClip(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 3, y + 7, 0xFF000000);
        g.fill(x, y + 1, x + 3, y + 6, CLIP);
        g.fill(x, y + 1, x + 3, y + 2, CLIP_LIGHT);
        g.fill(x, y + 3, x + 3, y + 4, 0xFF5A5E6A);
    }

    /** Scattered gold sparks that slowly twinkle, always in the same places. */
    private static void drawSparks(GuiGraphics g, int x, int y, int x2, int y2, Palette p) {
        long now = Util.getMillis();
        int count = (x2 - x) * (y2 - y) / 220;
        for (int i = 0; i < count; i++) {
            int h = i * 0x9E3779B1;
            h ^= h >>> 15;
            h *= 0x2C1B3C6D;
            h ^= h >>> 13;
            int sx = x + Math.floorMod(h, x2 - x);
            int sy = y + Math.floorMod(h >>> 8, y2 - y);
            float twinkle = 0.35F + 0.65F * Mth.square(Mth.sin(now / 900.0F + (h & 0xFF) * 0.1F));
            int c = withAlpha((i % 3 == 0 ? p.trimLight : p.trim), twinkle * 0.8F);
            px(g, sx, sy, c);
            if (i % 7 == 0) {
                px(g, sx - 1, sy, withAlpha(p.trim, twinkle * 0.4F));
                px(g, sx + 1, sy, withAlpha(p.trim, twinkle * 0.4F));
                px(g, sx, sy - 1, withAlpha(p.trim, twinkle * 0.4F));
                px(g, sx, sy + 1, withAlpha(p.trim, twinkle * 0.4F));
            }
        }
    }

    private static void outline(GuiGraphics g, int x, int y, int x2, int y2, int c) {
        g.fill(x, y, x2, y + 1, c);
        g.fill(x, y2 - 1, x2, y2, c);
        g.fill(x, y, x + 1, y2, c);
        g.fill(x2 - 1, y, x2, y2, c);
    }

    private static void px(GuiGraphics g, int x, int y, int c) {
        g.fill(x, y, x + 1, y + 1, c);
    }

    private static void drawDiamond(GuiGraphics g, int cx, int cy, Palette p) {
        g.fill(cx, cy - 2, cx + 1, cy + 3, p.trimDark);
        g.fill(cx - 1, cy - 1, cx + 2, cy + 2, p.trim);
        g.fill(cx - 2, cy, cx + 3, cy + 1, p.trimDark);
        px(g, cx, cy, p.trimLight);
    }

    /** Same frame as the outer border, smaller: black edge, dark band, thin trim line. */
    private static void drawDarkFrame(GuiGraphics g, int x, int y, int x2, int y2, Palette p) {
        g.fill(x - 3, y - 2, x2 + 3, y2 + 2, 0xFF000000);
        g.fill(x - 2, y - 3, x2 + 2, y2 + 3, 0xFF000000);
        g.fill(x - 2, y - 2, x2 + 2, y2 + 2, BORDER_DARK);
        g.fill(x - 2, y - 2, x2 + 2, y - 1, BORDER_MID);
        g.fill(x - 1, y - 1, x2 + 1, y2 + 1, p.trim);
    }

    /** 5x5 square spiral in a box corner, oriented by (dx, dy). */
    private static void drawMiniKey(GuiGraphics g, int x, int y, int dx, int dy, Palette p) {
        String[] key = {"11111", "00001", "11101", "10001", "11111"};
        for (int r = 0; r < 5; r++) {
            for (int c = 0; c < 5; c++) {
                if (key[r].charAt(c) == '1') px(g, x + c * dx, y + r * dy, (r + c) % 4 == 0 ? p.trimLight : p.trim);
            }
        }
    }

    /** Recessed box in the outer-frame style, with small key spirals in its corners. */
    private static void drawSocketBox(GuiGraphics g, int x, int y, int w, int h, Palette p) {
        int x2 = x + w;
        int y2 = y + h;
        drawDarkFrame(g, x, y, x2, y2, p);
        g.fill(x, y, x2, y2, p.bgDark);
        g.fill(x, y, x2, y + 1, 0x40000000);
        drawMiniKey(g, x + 1, y + 1, 1, 1, p);
        drawMiniKey(g, x2 - 2, y + 1, -1, 1, p);
        drawMiniKey(g, x + 1, y2 - 2, 1, -1, p);
        drawMiniKey(g, x2 - 2, y2 - 2, -1, -1, p);
    }

    /** Title plaque: framed dark plate with rivets, hanging from two short chains. */
    private void drawTitlePlaque(GuiGraphics g, int mid, int y, Palette p) {
        int tw = font.width(title) / 2 + 8;
        int x1 = mid - tw;
        int x2 = mid + tw;
        g.fill(x1 - 1, y - 1, x2 + 1, y + 15, 0xFF000000);
        g.fill(x1, y, x2, y + 14, p.trimDark);
        g.fill(x1 + 1, y + 1, x2 - 1, y + 13, p.trim);
        g.fill(x1 + 1, y + 1, x2 - 1, y + 2, p.trimLight);
        g.fill(x1 + 2, y + 2, x2 - 2, y + 12, p.bgDark);
        px(g, x1 + 3, y + 7, p.trimLight);
        px(g, x2 - 4, y + 7, p.trimLight);
        // pointed ends
        for (int i = 0; i < 5; i++) {
            g.fill(x1 - 1 - i, y + 2 + i, x1, y + 12 - i, p.trim);
            g.fill(x2, y + 2 + i, x2 + 1 + i, y + 12 - i, p.trim);
        }
        px(g, x1 - 5, y + 7, p.trimLight);
        px(g, x2 + 4, y + 7, p.trimLight);
    }

    /**
     * Mirrored gold filigree between the plaque and the mob window: a centre gem with scrolls
     * running out to both sides and curling up at the ends.
     */
    private static void drawFiligree(GuiGraphics g, int mid, int top, Palette p) {
        // centre gem
        g.fill(mid - 2, top + 8, mid + 3, top + 13, p.trimDark);
        g.fill(mid - 1, top + 9, mid + 2, top + 12, p.trim);
        px(g, mid, top + 10, p.trimLight);
        int[][] half = {
                {3, 10}, {4, 9}, {5, 9}, {6, 8}, {7, 8}, {8, 8}, {9, 9}, {10, 10}, {10, 11}, {9, 12}, {8, 12}, {8, 11},
                {11, 9}, {12, 8}, {13, 8}, {14, 8}, {15, 9}, {16, 10}, {17, 11}, {18, 11}, {19, 11}, {20, 10},
                {21, 9}, {22, 9}, {23, 10}, {24, 11}, {25, 12}, {26, 12}, {27, 11}, {28, 10}, {28, 9}, {27, 8}, {26, 9}};
        for (int[] pt : half) {
            px(g, mid + pt[0], top + pt[1], p.trim);
            px(g, mid - pt[0], top + pt[1], p.trim);
        }
        int[][] shine = {{6, 8}, {13, 8}, {22, 9}};
        for (int[] pt : shine) {
            px(g, mid + pt[0], top + pt[1], p.trimLight);
            px(g, mid - pt[0], top + pt[1], p.trimLight);
        }
    }

    /** Machine slot: light carved-stone face in a gold frame (a thicker frame for the lasso). */
    private static void drawStoneSlot(GuiGraphics g, int x, int y, Palette p, boolean big) {
        int f = big ? 3 : 2;
        g.fill(x - f, y - f, x + 16 + f, y + 16 + f, p.trimDark);
        g.fill(x - f + 1, y - f + 1, x + 16 + f - 1, y + 16 + f - 1, p.trim);
        if (big) g.fill(x - 2, y - 2, x + 18, y - 1, p.trimLight);
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_DARK);
        g.fill(x, y, x + 16, y + 16, SLOT_FACE);
        g.fill(x, y + 15, x + 16, y + 16, SLOT_LIGHT);
        g.fill(x + 15, y, x + 16, y + 16, SLOT_LIGHT);
        g.fill(x, y, x + 16, y + 1, SLOT_DARK);
        g.fill(x, y, x + 1, y + 16, SLOT_DARK);
    }

    private static void drawInventorySlot(GuiGraphics g, int x, int y, Palette p) {
        g.fill(x - 1, y - 1, x + 17, y + 17, p.trimDark);
        g.fill(x, y, x + 16, y + 16, p.bgDark);
        g.fill(x, y + 15, x + 16, y + 16, (p.trim & 0xFFFFFF) | 0x50000000);
    }

    /** Side tab holding two upgrade slots, with sparkles that glow when books are in. */
    private void drawTab(GuiGraphics g, int x, int y, Palette p, boolean left) {
        int w = B + 3;
        g.fill(x - 1, y - 1, x + w + 1, y + TAB_H + 1, 0xFF000000);
        g.fill(x, y, x + w, y + TAB_H, BORDER_DARK);
        g.fill(x + 2, y + 2, x + w - 2, y + TAB_H - 2, p.trim);
        g.fill(x + 3, y + 3, x + w - 3, y + TAB_H - 3, p.bg);
        boolean active = !menu.upgrades().isEmpty();
        float pulse = active ? 0.5F + 0.5F * Mth.sin(Util.getMillis() / 300.0F) : 0.0F;
        int glow = ((int) (0x60 + 0x9F * pulse) << 24) | (p.trimLight & 0xFFFFFF);
        int cx = x + w / 2 + (left ? -1 : 1);
        g.fill(cx - 1, y + 3, cx + 2, y + 4, glow);
        g.fill(cx, y + 2, cx + 1, y + 5, glow);
        g.fill(cx - 1, y + TAB_H - 4, cx + 2, y + TAB_H - 3, glow);
        g.fill(cx, y + TAB_H - 5, cx + 1, y + TAB_H - 2, glow);
    }

    private static void drawGhostBook(GuiGraphics g, int x, int y) {
        g.fill(x + 4, y + 3, x + 12, y + 13, 0x40000000);
        g.fill(x + 5, y + 4, x + 11, y + 12, 0x30FFFFFF);
        g.fill(x + 7, y + 3, x + 8, y + 13, 0x40000000);
    }

    private static void drawLock(GuiGraphics g, int x, int y, Palette p) {
        g.fill(x + 2, y, x + 7, y + 1, p.trimLight);
        g.fill(x + 1, y + 1, x + 2, y + 4, p.trimLight);
        g.fill(x + 7, y + 1, x + 8, y + 4, p.trimLight);
        g.fill(x, y + 4, x + 9, y + 11, p.trim);
        g.fill(x + 4, y + 6, x + 5, y + 9, p.trimDark);
    }

    /** Window with the caught mob turning on a little pedestal. */
    private void drawWindow(GuiGraphics g, int x, int y, FarmTier tier) {
        int x2 = x + WINDOW_W;
        int y2 = y + PANEL_H;
        Palette p = palette(tier);
        drawDarkFrame(g, x, y, x2, y2, p);
        g.fillGradient(x, y, x2, y2, 0xFF0C0A10, p.bgDark);
        // pedestal
        int cx = x + WINDOW_W / 2;
        g.fill(cx - 18, y2 - 9, cx + 18, y2 - 7, p.trimDark);
        g.fill(cx - 16, y2 - 10, cx + 16, y2 - 9, p.trim);

        EntityType<?> type = menu.mobType();
        LivingEntity entity = ClientEntities.get(type);
        if (entity == null) {
            Component hint = Component.translatable("gui.mobfarm.insert_lasso");
            g.drawWordWrap(font, hint, x + 4, y + 22, WINDOW_W - 8, TEXT_DIM);
            return;
        }

        float height = Math.max(0.3F, entity.getBbHeight());
        float width = Math.max(0.3F, entity.getBbWidth());
        int scale = (int) Math.max(4.0F, Math.min(40.0F / height, 44.0F / width));
        float spin = (Util.getMillis() % 12000L) / 12000.0F * 360.0F;

        g.enableScissor(x, y, x2, y2);
        renderSpinning(g, cx, y2 - 10, scale, spin, entity);
        g.disableScissor();
        drawSecondsBar(g, x + 4, y2 - 5, WINDOW_W - 8, p);

        Component name = type.getDescription();
        String text = font.plainSubstrByWidth(name.getString(), WINDOW_W - 4);
        g.drawString(font, text, cx - font.width(text) / 2, y + 3, TEXT, true);
    }

    /**
     * Small segmented bar, one segment per second of the cycle (at most 20), showing how many
     * seconds are left until the next loot. Segments go out one by one from the right.
     */
    private void drawSecondsBar(GuiGraphics g, int x, int y, int w, Palette p) {
        int max = menu.maxProgress();
        int segments = Mth.clamp((max + 19) / 20, 1, 20);
        float left = 1.0F - shownProgress;
        float litSegments = left * segments;
        int gap = 1;
        int segW = Math.max(1, (w - gap * (segments - 1)) / segments);
        int total = segW * segments + gap * (segments - 1);
        int sx = x + (w - total) / 2;
        for (int i = 0; i < segments; i++) {
            int x1 = sx + i * (segW + gap);
            g.fill(x1, y, x1 + segW, y + 3, (p.bgDark & 0xFFFFFF) | 0xE0000000);
            float fill = Mth.clamp(litSegments - i, 0.0F, 1.0F);
            if (fill > 0.0F) {
                int fw = Math.max(1, Math.round(segW * fill));
                g.fill(x1, y, x1 + fw, y + 3, p.trimLight);
                g.fill(x1, y + 2, x1 + fw, y + 3, p.trim);
            }
        }
    }

    private static void renderSpinning(GuiGraphics g, int x, int y, int scale, float angle, LivingEntity entity) {
        float bodyRot = entity.yBodyRot;
        float yRot = entity.getYRot();
        float xRot = entity.getXRot();
        float headRotO = entity.yHeadRotO;
        float headRot = entity.yHeadRot;

        Quaternionf pose = new Quaternionf().rotateZ((float) Math.PI);
        Quaternionf camera = new Quaternionf().rotateX(-12.0F * Mth.DEG_TO_RAD);
        pose.mul(camera);
        entity.yBodyRot = angle;
        entity.setYRot(angle);
        entity.setXRot(0.0F);
        entity.yHeadRot = angle;
        entity.yHeadRotO = angle;
        try {
            InventoryScreen.renderEntityInInventory(g, x, y, scale, pose, camera, entity);
        } catch (RuntimeException ignored) {
            // some modded mobs can't be drawn in a GUI
        } finally {
            entity.yBodyRot = bodyRot;
            entity.setYRot(yRot);
            entity.setXRot(xRot);
            entity.yHeadRotO = headRotO;
            entity.yHeadRot = headRot;
        }
    }

    /** Two bars with orb icons: red = time to the next loot, cyan = how full the loot slots are. */
    private void drawBars(GuiGraphics g, int x, int top, Palette p) {
        int status = menu.status();
        drawBar(g, x, top + BAR_Y, shownProgress, 0xFFE0403A, 0xFF7A1410, p, status == MobFarmBlockEntity.STATUS_RUNNING);
        drawBar(g, x, top + BAR2_Y, shownFullness, 0xFF5FE0F0, 0xFF126A7A, p, false);
    }

    /**
     * Capsule bar: dark rounded frame with a trim line, glossy fill (highlight on top, shade at
     * the bottom, colour deepening to the left), a glowing tip, 10% notches, moving diagonal
     * stripes while the farm works, and a round gem icon in front.
     */
    private static void drawBar(GuiGraphics g, int x, int y, float fraction, int bright, int deep, Palette p, boolean animate) {
        drawGemIcon(g, x + 3, y + BAR_H / 2, bright, deep);

        int bx = x + 10;
        int bw = BAR_W - 10;
        int bx2 = bx + bw;
        int y2 = y + BAR_H;
        // rounded frame
        g.fill(bx - 2, y - 2, bx2 + 2, y2 + 2, 0xFF000000);
        g.fill(bx - 1, y - 2, bx2 + 1, y - 1, BORDER_DARK);
        g.fill(bx - 2, y - 1, bx2 + 2, y2 + 1, BORDER_DARK);
        g.fill(bx - 1, y2 + 1, bx2 + 1, y2 + 2, BORDER_DARK);
        g.fill(bx - 1, y - 1, bx2 + 1, y2 + 1, withAlpha(p.trim, 0.9F));
        // inset track
        g.fillGradient(bx, y, bx2, y2, 0xFF08070B, 0xFF1B1720);

        int filled = fraction > 0.002F ? Math.max(2, Math.round(bw * fraction)) : 0;
        if (filled > 0) {
            int mid = lerp(deep, bright, 0.5F);
            for (int i = 0; i < filled; i += 2) {
                float t = 0.35F + 0.65F * i / Math.max(1, bw - 1);
                int c = lerp(deep, bright, t);
                int w2 = Math.min(filled, i + 2);
                g.fill(bx + i, y + 1, bx + w2, y2 - 1, c);
            }
            g.fill(bx, y, bx + filled, y + 1, lerp(bright, 0xFFFFFFFF, 0.55F));   // glossy top
            g.fill(bx, y2 - 1, bx + filled, y2, lerp(deep, 0xFF000000, 0.35F));   // shaded bottom
            g.fill(bx, y + 1, bx + 1, y2 - 1, mid);

            if (animate) {
                // diagonal stripes drifting to the right
                int shift = (int) ((Util.getMillis() / 60L) % 8L);
                for (int i = -8 + shift; i < filled; i += 8) {
                    for (int r = 0; r < BAR_H - 2; r++) {
                        int sx = i + r;
                        if (sx >= 0 && sx < filled) g.fill(bx + sx, y + 1 + r, bx + sx + 2, y + 2 + r, 0x22FFFFFF);
                    }
                }
            }
            // glowing tip
            int tip = bx + filled;
            g.fill(tip - 1, y, tip, y2, lerp(bright, 0xFFFFFFFF, 0.7F));
            if (filled < bw) g.fill(tip, y, Math.min(bx2, tip + 2), y2, withAlpha(bright, 0.35F));
        }
        // notches every 10%
        for (int k = 1; k < 10; k++) {
            int nx = bx + bw * k / 10;
            g.fill(nx, y2 - 2, nx + 1, y2, 0x55000000);
        }
    }

    /** Round gem: dark outline, coloured body shaded to the bottom, white glint. */
    private static void drawGemIcon(GuiGraphics g, int cx, int cy, int bright, int deep) {
        g.fill(cx - 2, cy - 4, cx + 3, cy + 5, 0xFF000000);
        g.fill(cx - 3, cy - 3, cx + 4, cy + 4, 0xFF000000);
        g.fill(cx - 2, cy - 3, cx + 3, cy + 4, bright);
        g.fill(cx - 3, cy - 2, cx + 4, cy + 3, bright);
        g.fill(cx - 2, cy + 1, cx + 3, cy + 4, deep);
        g.fill(cx - 3, cy + 1, cx + 4, cy + 3, deep);
        px(g, cx - 1, cy - 2, 0xFFFFFFFF);
        px(g, cx - 2, cy - 1, 0xC0FFFFFF);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        Palette p = palette(menu.tier());
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, -6, p.trimLight, true);

        // Left socket: caption above the lasso, cycle time below it
        int leftCenter = LEFT_PANEL_X + PANEL_W / 2;
        Component lasso = Component.translatable("gui.mobfarm.lasso");
        g.drawString(font, lasso, leftCenter - font.width(lasso) / 2, PANEL_Y + 5, TEXT_DIM, false);
        Component speed = Component.translatable("gui.mobfarm.speed", String.format("%.1f", menu.maxProgress() / 20.0F));
        g.drawString(font, speed, leftCenter - font.width(speed) / 2, PANEL_Y + PANEL_H - 13, TEXT, false);

        // Status line under the bars, centred
        Component status = statusText();
        g.drawString(font, status, (imageWidth - font.width(status)) / 2, BAR2_Y + BAR_H + 3, TEXT, true);
    }

    private Component statusText() {
        return switch (menu.status()) {
            case MobFarmBlockEntity.STATUS_RUNNING -> {
                int percent = Math.round(100.0F * menu.progress() / menu.maxProgress());
                int seconds = Math.max(0, (menu.maxProgress() - menu.progress() + 19) / 20);
                yield Component.translatable("gui.mobfarm.status.running", percent, seconds);
            }
            case MobFarmBlockEntity.STATUS_REDSTONE -> Component.translatable("gui.mobfarm.status.redstone");
            case MobFarmBlockEntity.STATUS_FULL -> Component.translatable("gui.mobfarm.status.full");
            default -> Component.translatable("gui.mobfarm.status.no_mob");
        };
    }

    private static int lerp(int from, int to, float t) {
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int gr = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }
}
