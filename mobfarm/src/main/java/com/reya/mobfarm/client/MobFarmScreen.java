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

        // Title banner that fades out softly at both ends
        int mid = x + MobFarmMenu.BODY_W / 2;
        int tw = font.width(title) / 2 + 6;
        g.fill(mid - tw, y + 4, mid + tw, y + 16, p.bgDark);
        g.fill(mid - tw, y + 16, mid + tw, y + 17, p.trimDark);
        int fade = 14;
        for (int i = 0; i < fade; i++) {
            float a = 1.0F - (i + 1) / (float) (fade + 1);
            int bg = withAlpha(p.bgDark, a);
            int line = withAlpha(p.trimDark, a);
            g.fill(mid - tw - 1 - i, y + 4, mid - tw - i, y + 16, bg);
            g.fill(mid + tw + i, y + 4, mid + tw + i + 1, y + 16, bg);
            g.fill(mid - tw - 1 - i, y + 16, mid - tw - i, y + 17, line);
            g.fill(mid + tw + i, y + 16, mid + tw + i + 1, y + 17, line);
        }
        drawDiamond(g, mid - tw - fade - 3, y + 10, p);
        drawDiamond(g, mid + tw + fade + 2, y + 10, p);

        // Lasso socket, loot box and the mob window, mirrored around the middle
        drawSocketBox(g, leftPos + LEFT_PANEL_X, y + PANEL_Y, PANEL_W, PANEL_H, p);
        drawSocketBox(g, leftPos + RIGHT_PANEL_X, y + PANEL_Y, PANEL_W, PANEL_H, p);
        drawCurl(g, leftPos + WINDOW_X - 3, y + PANEL_Y + 2, p, true);
        drawCurl(g, leftPos + WINDOW_X + WINDOW_W + 2, y + PANEL_Y + 2, p, false);

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

    /** Double trim with filigree corners and diamonds halfway down the sides. */
    private static void drawOrnateBox(GuiGraphics g, int x, int y, int x2, int y2, Palette p) {
        g.fill(x - 1, y - 1, x2 + 1, y2 + 1, 0xFF000000);
        g.fill(x, y, x2, y2, p.trimDark);
        g.fill(x + 1, y + 1, x2 - 1, y2 - 1, p.trim);
        g.fill(x + 1, y + 1, x2 - 1, y + 2, p.trimLight);
        g.fill(x + 2, y + 2, x2 - 2, y2 - 2, p.bgDark);
        g.fill(x + 3, y + 3, x2 - 3, y2 - 3, p.bg);
        // faint woven pattern
        for (int py = y + 6; py < y2 - 6; py += 4) {
            for (int px = x + 6 + (py / 4 % 2) * 2; px < x2 - 6; px += 4) {
                g.fill(px, py, px + 1, py + 1, (p.bgDark & 0xFFFFFF) | 0x60000000);
            }
        }
        // inner thin trim line
        outline(g, x + 4, y + 4, x2 - 4, y2 - 4, (p.trimDark & 0xFFFFFF) | 0xC0000000);
        drawCorner(g, x + 2, y + 2, 1, 1, p);
        drawCorner(g, x2 - 3, y + 2, -1, 1, p);
        drawCorner(g, x + 2, y2 - 3, 1, -1, p);
        drawCorner(g, x2 - 3, y2 - 3, -1, -1, p);
        int my = (y + y2) / 2;
        drawDiamond(g, x + 1, my, p);
        drawDiamond(g, x2 - 2, my, p);
    }

    private static void outline(GuiGraphics g, int x, int y, int x2, int y2, int c) {
        g.fill(x, y, x2, y + 1, c);
        g.fill(x, y2 - 1, x2, y2, c);
        g.fill(x, y, x + 1, y2, c);
        g.fill(x2 - 1, y, x2, y2, c);
    }

    /** Filigree corner: an L of trim with a curl and a dot, pointing inward along (dx, dy). */
    private static void drawCorner(GuiGraphics g, int x, int y, int dx, int dy, Palette p) {
        for (int i = 0; i < 9; i++) {
            px(g, x + i * dx, y, p.trim);
            px(g, x, y + i * dy, p.trim);
            px(g, x + i * dx, y + dy, i < 7 ? p.trimLight : p.trim);
            px(g, x + dx, y + i * dy, i < 7 ? p.trimLight : p.trim);
        }
        // little square gem in the corner
        for (int i = 2; i < 5; i++) for (int j = 2; j < 5; j++) px(g, x + i * dx, y + j * dy, p.trim);
        px(g, x + 3 * dx, y + 3 * dy, p.trimLight);
        // curl
        px(g, x + 6 * dx, y + 3 * dy, p.trim);
        px(g, x + 7 * dx, y + 4 * dy, p.trim);
        px(g, x + 3 * dx, y + 6 * dy, p.trim);
        px(g, x + 4 * dx, y + 7 * dy, p.trim);
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

    /** Swirl beside the mob window; mirrored for the right side. */
    private static void drawCurl(GuiGraphics g, int x, int y, Palette p, boolean left) {
        int d = left ? -1 : 1;
        int[][] shape = {{0, 0}, {1, 0}, {2, 1}, {3, 2}, {3, 3}, {2, 4}, {1, 4}, {1, 3}, {2, 2}, {0, 5}, {0, 6}, {1, 7}, {2, 8}};
        for (int[] s : shape) px(g, x + s[0] * d, y + s[1], p.trim);
        px(g, x + 2 * d, y + 3, p.trimLight);
    }

    private static void drawSocketBox(GuiGraphics g, int x, int y, int w, int h, Palette p) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, p.trimDark);
        g.fill(x, y, x + w, y + h, p.bgDark);
        g.fill(x, y, x + w, y + 1, (p.trim & 0xFFFFFF) | 0x90000000);
        px(g, x, y, p.trim);
        px(g, x + w - 1, y, p.trim);
        px(g, x, y + h - 1, p.trim);
        px(g, x + w - 1, y + h - 1, p.trim);
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
        g.fill(x, y, x + w, y + TAB_H, p.trimDark);
        g.fill(x + 1, y + 1, x + w - 1, y + TAB_H - 1, p.trim);
        g.fill(x + 2, y + 2, x + w - 2, y + TAB_H - 2, p.bg);
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
        g.fill(x - 2, y - 2, x2 + 2, y2 + 2, p.trimDark);
        g.fill(x - 1, y - 1, x2 + 1, y2 + 1, p.trim);
        g.fill(x - 1, y - 1, x2 + 1, y, p.trimLight);
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

        Component name = type.getDescription();
        String text = font.plainSubstrByWidth(name.getString(), WINDOW_W - 4);
        g.drawString(font, text, cx - font.width(text) / 2, y + 3, TEXT, true);
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

    private static void drawBar(GuiGraphics g, int x, int y, float fraction, int bright, int deep, Palette p, boolean shimmer) {
        // orb icon
        g.fill(x + 1, y - 1, x + 5, y + BAR_H + 1, p.trimDark);
        g.fill(x, y, x + 6, y + BAR_H, p.trimDark);
        g.fill(x + 1, y, x + 5, y + BAR_H, bright);
        g.fill(x + 1, y + BAR_H - 2, x + 5, y + BAR_H, deep);
        px(g, x + 2, y + 1, 0xFFFFFFFF);
        // track
        int bx = x + 10;
        int bw = BAR_W - 10;
        g.fill(bx - 1, y - 1, bx + bw + 1, y + BAR_H + 1, p.trimDark);
        g.fill(bx, y, bx + bw, y + BAR_H, 0xFF141016);
        int filled = fraction > 0.002F ? Math.max(2, Math.round(bw * fraction)) : 0;
        for (int i = 0; i < filled; i += 2) {
            float t = (float) i / Math.max(1, bw - 1);
            g.fill(bx + i, y, bx + Math.min(filled, i + 2), y + BAR_H, lerp(deep, bright, t));
        }
        g.fill(bx, y, bx + filled, y + 1, 0x70FFFFFF);
        if (shimmer && filled > 0) {
            int sweep = (int) ((Util.getMillis() % 1600L) / 1600.0F * (bw + 20)) - 10;
            for (int d = -5; d <= 5; d++) {
                int sx = sweep + d;
                if (sx < 0 || sx >= filled) continue;
                int alpha = (int) (0x50 * (1.0F - Math.abs(d) / 6.0F));
                g.fill(bx + sx, y, bx + sx + 1, y + BAR_H, (alpha << 24) | 0xFFFFFF);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        Palette p = palette(menu.tier());
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, 7, p.trimLight, true);

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
