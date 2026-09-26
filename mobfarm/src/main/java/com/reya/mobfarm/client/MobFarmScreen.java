package com.reya.mobfarm.client;

import java.util.Locale;

import org.joml.Quaternionf;

import com.reya.mobfarm.FarmTier;
import com.reya.mobfarm.MobFarm;
import com.reya.mobfarm.farm.MobFarmBlockEntity;
import com.reya.mobfarm.farm.MobFarmMenu;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Farm GUI. The frame, slots, horns, sparkles and bar tracks are one pixel-art texture per tier
 * (made by tools/gen_gui.py); this class only draws what moves: the turning mob, bar fills,
 * the seconds bar, the lock, the title plaque and the text.
 */
public class MobFarmScreen extends AbstractContainerScreen<MobFarmMenu> {
    /** The texture has a 4px margin around the screen area for corner caps and clips. */
    private static final int MARGIN = 4;
    private static final int TEX_W = MobFarmMenu.WIDTH + 2 * MARGIN;
    private static final int TEX_H = MobFarmMenu.HEIGHT + 2 * MARGIN;

    private static final int B = MobFarmMenu.BODY_X;
    private static final int LEFT_CENTER = B + 37;
    private static final int WINDOW_X = B + 72;
    private static final int WINDOW_W = 72;
    private static final int WINDOW_Y = 18;
    private static final int WINDOW_H = 64;
    private static final int BAR_X = B + 24;
    private static final int BAR_W = 168;
    private static final int BAR_Y = 88;
    private static final int BAR2_Y = 99;
    private static final int BAR_H = 6;
    private static final int TAB_Y = 22;
    private static final int TAB_H = 58;

    private static final int TEXT = 0xFFF4ECDC;
    private static final int TEXT_DIM = 0xFFD8C8AE;
    private static final int PLAQUE_TEXT = 0xFF3A3530;

    /** Trim colours per tier (light, normal, dark), matching the texture. */
    private static int[] trim(FarmTier tier) {
        return switch (tier) {
            case WOODEN -> new int[]{0xFFECBE6E, 0xFFC9913F, 0xFF764E1E};
            case STONE -> new int[]{0xFFE8EBF0, 0xFFB9BEC6, 0xFF686D74};
            case IRON -> new int[]{0xFFF4F7FB, 0xFFC8D2DE, 0xFF6A7684};
            case GOLDEN, DIAMOND -> new int[]{0xFFFFE28C, 0xFFE3B341, 0xFF886012};
            case NETHERITE -> new int[]{0xFFFFD2BA, 0xFFE0A080, 0xFF885846};
        };
    }

    private float shownProgress;
    private float shownFullness;
    private long lastFrame = Util.getMillis();

    public MobFarmScreen(MobFarmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = MobFarmMenu.WIDTH;
        imageHeight = MobFarmMenu.HEIGHT;
    }

    private ResourceLocation texture() {
        return new ResourceLocation(MobFarm.MOD_ID,
                "textures/gui/mob_farm_" + menu.tier().id.toLowerCase(Locale.ROOT) + ".png");
    }

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
        g.blit(texture(), leftPos - MARGIN, topPos - MARGIN, 0, 0, TEX_W, TEX_H, TEX_W, TEX_H);

        int[] t = trim(menu.tier());
        drawTitlePlaque(g, leftPos + imageWidth / 2, topPos - 13, t);
        drawWindow(g, leftPos + WINDOW_X, topPos + WINDOW_Y, t);

        g.drawManaged(() -> {
            drawBar(g, leftPos + BAR_X, topPos + BAR_Y, shownProgress, 0xFFE8463C, 0xFF801612,
                    menu.status() == MobFarmBlockEntity.STATUS_RUNNING);
            drawBar(g, leftPos + BAR_X, topPos + BAR2_Y, shownFullness, 0xFF64E2F0, 0xFF126C7C, false);
            for (Slot slot : menu.slots) {
                boolean upgrade = slot.index >= MobFarmBlockEntity.UPGRADE_START && slot.index < MobFarmBlockEntity.SLOT_COUNT;
                if (upgrade && !slot.hasItem()) drawGhostBook(g, leftPos + slot.x, topPos + slot.y);
            }
            if (!menu.upgrades().isEmpty()) drawTabGlow(g, t);
            if (menu.status() == MobFarmBlockEntity.STATUS_REDSTONE) {
                drawLock(g, leftPos + B + MobFarmMenu.BODY_W - 30, topPos + 9, t);
            }
        });
    }

    // ---------------------------------------------------------------- pieces

    /** Stone name plate hanging over the top edge, with trim caps at its ends. */
    private void drawTitlePlaque(GuiGraphics g, int mid, int y, int[] t) {
        int half = font.width(title) / 2 + 8;
        int x1 = mid - half;
        int x2 = mid + half;
        g.fill(x1 - 1, y - 1, x2 + 1, y + 13, 0xFF0A090C);
        g.fill(x1, y, x2, y + 12, 0xFFB4B9B2);
        g.fill(x1, y, x2, y + 1, 0xFFDDE2DA);
        g.fill(x1, y + 11, x2, y + 12, 0xFF7C827A);
        g.fill(x1 + 2, y + 2, x2 - 2, y + 3, 0x30000000);
        for (int side : new int[]{x1 - 5, x2 + 1}) {
            g.fill(side - 1, y + 1, side + 5, y + 11, 0xFF0A090C);
            g.fill(side, y + 2, side + 4, y + 10, t[1]);
            g.fill(side, y + 2, side + 4, y + 3, t[0]);
            g.fill(side, y + 9, side + 4, y + 10, t[2]);
        }
    }

    /** The turning mob inside the window frame from the texture, its name and the seconds bar. */
    private void drawWindow(GuiGraphics g, int x, int y, int[] t) {
        int x2 = x + WINDOW_W;
        int y2 = y + WINDOW_H;
        int cx = x + WINDOW_W / 2;

        EntityType<?> type = menu.mobType();
        LivingEntity entity = ClientEntities.get(type);
        if (entity == null) {
            Component hint = Component.translatable("gui.mobfarm.insert_lasso");
            g.drawWordWrap(font, hint, x + 5, y + 22, WINDOW_W - 10, TEXT_DIM);
            return;
        }

        float height = Math.max(0.3F, entity.getBbHeight());
        float width = Math.max(0.3F, entity.getBbWidth());
        int scale = (int) Math.max(4.0F, Math.min(38.0F / height, 44.0F / width));
        float spin = (Util.getMillis() % 12000L) / 12000.0F * 360.0F;

        g.enableScissor(x, y, x2, y2);
        renderSpinning(g, cx, y2 - 10, scale, spin, entity);
        g.disableScissor();
        g.drawManaged(() -> drawSecondsBar(g, x + 5, y2 - 5, WINDOW_W - 10, t));

        String text = font.plainSubstrByWidth(type.getDescription().getString(), WINDOW_W - 6);
        g.drawString(font, text, cx - font.width(text) / 2, y + 3, TEXT, true);
    }

    /** One segment per second of the cycle (at most 20); segments go out as the time runs down. */
    private void drawSecondsBar(GuiGraphics g, int x, int y, int w, int[] t) {
        int segments = Mth.clamp((menu.maxProgress() + 19) / 20, 1, 20);
        float lit = (1.0F - shownProgress) * segments;
        int gap = 1;
        int segW = Math.max(1, (w - gap * (segments - 1)) / segments);
        int total = segW * segments + gap * (segments - 1);
        int sx = x + (w - total) / 2;
        for (int i = 0; i < segments; i++) {
            int x1 = sx + i * (segW + gap);
            g.fill(x1, y, x1 + segW, y + 3, 0xC0000000);
            float fill = Mth.clamp(lit - i, 0.0F, 1.0F);
            if (fill > 0.0F) {
                int fw = Math.max(1, Math.round(segW * fill));
                g.fill(x1, y, x1 + fw, y + 3, t[0]);
                g.fill(x1, y + 2, x1 + fw, y + 3, t[1]);
            }
        }
    }

    /** Fill for a bar track from the texture: glossy, with a glowing tip and moving stripes. */
    private static void drawBar(GuiGraphics g, int bx, int y, float fraction, int bright, int deep, boolean animate) {
        int y2 = y + BAR_H;
        int filled = fraction > 0.002F ? Math.max(2, Math.round(BAR_W * fraction)) : 0;
        if (filled == 0) return;
        for (int i = 0; i < filled; i += 2) {
            float k = 0.35F + 0.65F * i / (float) (BAR_W - 1);
            g.fill(bx + i, y, bx + Math.min(filled, i + 2), y2, lerp(deep, bright, k));
        }
        g.fill(bx, y, bx + filled, y + 1, lerp(bright, 0xFFFFFFFF, 0.55F));
        g.fill(bx, y2 - 1, bx + filled, y2, lerp(deep, 0xFF000000, 0.35F));
        if (animate) {
            int shift = (int) ((Util.getMillis() / 60L) % 8L);
            for (int i = -8 + shift; i < filled; i += 8) {
                for (int r = 1; r < BAR_H - 1; r++) {
                    int sx = i + r;
                    if (sx >= 0 && sx + 2 <= filled) g.fill(bx + sx, y + r, bx + sx + 2, y + r + 1, 0x26FFFFFF);
                }
            }
        }
        int tip = bx + filled;
        g.fill(tip - 1, y, tip, y2, lerp(bright, 0xFFFFFFFF, 0.7F));
        if (filled < BAR_W) g.fill(tip, y, Math.min(bx + BAR_W, tip + 2), y2, (bright & 0xFFFFFF) | 0x50000000);
    }

    private static void drawGhostBook(GuiGraphics g, int x, int y) {
        g.fill(x + 4, y + 3, x + 12, y + 13, 0x40000000);
        g.fill(x + 5, y + 4, x + 11, y + 12, 0x30FFFFFF);
        g.fill(x + 7, y + 3, x + 8, y + 13, 0x40000000);
    }

    /** Pulsing sparks on the tabs while books are in. */
    private void drawTabGlow(GuiGraphics g, int[] t) {
        float pulse = 0.5F + 0.5F * Mth.sin(Util.getMillis() / 300.0F);
        int c = ((int) (0x60 + 0x9F * pulse) << 24) | (t[0] & 0xFFFFFF);
        for (int tabX : new int[]{leftPos + 14, leftPos + imageWidth - 15}) {
            for (int ty : new int[]{topPos + TAB_Y + 6, topPos + TAB_Y + TAB_H - 7}) {
                g.fill(tabX - 1, ty, tabX + 2, ty + 1, c);
                g.fill(tabX, ty - 1, tabX + 1, ty + 2, c);
            }
        }
    }

    /** Trim-coloured tile with a white padlock, like the reference. */
    private static void drawLock(GuiGraphics g, int x, int y, int[] t) {
        g.fill(x - 1, y - 1, x + 12, y + 12, 0xFF0A090C);
        g.fill(x, y, x + 11, y + 11, t[1]);
        g.fill(x, y, x + 11, y + 1, t[0]);
        g.fill(x + 4, y + 2, x + 7, y + 3, 0xFFFFFFFF);
        g.fill(x + 3, y + 3, x + 4, y + 5, 0xFFFFFFFF);
        g.fill(x + 7, y + 3, x + 8, y + 5, 0xFFFFFFFF);
        g.fill(x + 2, y + 5, x + 9, y + 9, 0xFFFFFFFF);
        g.fill(x + 5, y + 6, x + 6, y + 8, t[2]);
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

    // ---------------------------------------------------------------- smooth bars

    /**
     * The server sends numbers once a tick, so the bars would step. Progress advances with the
     * partial tick between updates; fullness eases toward its new value.
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
        if (progress < shownProgress - 0.25F) {
            shownProgress = progress;   // a new cycle: snap back instead of sliding backwards
        } else {
            shownProgress += (progress - shownProgress) * Math.min(1.0F, dt * 20.0F);
        }
        shownFullness += (fillFraction() - shownFullness) * Math.min(1.0F, dt * 6.0F);
    }

    /** Any used slot counts half its share at once, the other half grows with the stack. */
    private float fillFraction() {
        float total = 0.0F;
        for (int i = MobFarmBlockEntity.OUTPUT_START; i < MobFarmBlockEntity.OUTPUT_START + MobFarmBlockEntity.OUTPUT_COUNT; i++) {
            ItemStack stack = menu.slots.get(i).getItem();
            if (!stack.isEmpty()) total += 0.5F + 0.5F * stack.getCount() / (float) stack.getMaxStackSize();
        }
        return total / MobFarmBlockEntity.OUTPUT_COUNT;
    }

    // ---------------------------------------------------------------- text

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, -10, PLAQUE_TEXT, false);

        Component lasso = Component.translatable("gui.mobfarm.lasso");
        g.drawString(font, lasso, LEFT_CENTER - font.width(lasso) / 2, 26, TEXT_DIM, true);
        Component speed = Component.translatable("gui.mobfarm.speed", String.format("%.1f", menu.maxProgress() / 20.0F));
        g.drawString(font, speed, LEFT_CENTER - font.width(speed) / 2, 67, TEXT, true);

        Component status = statusText();
        g.drawString(font, status, (imageWidth - font.width(status)) / 2, BAR2_Y + BAR_H + 4, TEXT, true);
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
