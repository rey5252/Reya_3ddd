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

/**
 * Mirror-symmetric farm GUI, coloured by tier: lasso on the left, a window with the caught mob
 * slowly turning in the middle, loot grid on the right, a progress bar underneath and the player
 * inventory centred at the bottom. Drawn entirely in code.
 */
public class MobFarmScreen extends AbstractContainerScreen<MobFarmMenu> {
    // Symmetric layout, all mirrored around x = 108
    private static final int LEFT_PANEL_X = 8;
    private static final int RIGHT_PANEL_X = 150;
    private static final int PANEL_W = 58;
    private static final int WINDOW_X = 72;
    private static final int WINDOW_W = 72;
    private static final int PANEL_Y = 18;
    private static final int PANEL_H = 64;
    private static final int BAR_X = 8;
    private static final int BAR_Y = 88;
    private static final int BAR_W = 200;
    private static final int BAR_H = 8;

    private static final int BG_TOP = 0xF41A1A24;
    private static final int BG_BOTTOM = 0xF40F0F16;
    private static final int PANEL_BG = 0x90000000;
    private static final int SLOT = 0xFF26262F;
    private static final int SLOT_SHADE = 0xFF15151B;
    private static final int TEXT = 0xFFE8E8F0;
    private static final int TEXT_DIM = 0xFF9A9AAE;

    public MobFarmScreen(MobFarmMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = MobFarmMenu.WIDTH;
        imageHeight = MobFarmMenu.HEIGHT;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);
        renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        FarmTier tier = menu.tier();
        int x = leftPos;
        int y = topPos;
        int x2 = x + imageWidth;
        int y2 = y + imageHeight;

        // Body with a tier-coloured frame, mirrored gems in the corners and a crest on top.
        g.fill(x - 1, y - 1, x2 + 1, y2 + 1, 0xFF000000);
        g.fill(x, y, x2, y2, tier.dark);
        g.fill(x + 1, y + 1, x2 - 1, y2 - 1, tier.color);
        g.fill(x + 1, y + 1, x2 - 1, y + 2, tier.light);
        g.fillGradient(x + 3, y + 3, x2 - 3, y2 - 3, BG_TOP, BG_BOTTOM);
        drawGem(g, x + 1, y + 1, tier);
        drawGem(g, x2 - 6, y + 1, tier);
        drawGem(g, x + 1, y2 - 6, tier);
        drawGem(g, x2 - 6, y2 - 6, tier);
        int mid = x + imageWidth / 2;
        for (int i = 0; i < 4; i++) {
            g.fill(mid - 5 + i, y - 1 - i, mid + 5 - i, y - i, i == 3 ? tier.light : tier.color);
        }

        // Three panels, mirrored around the middle.
        drawPanel(g, x + LEFT_PANEL_X, y + PANEL_Y, PANEL_W, PANEL_H, tier);
        drawPanel(g, x + RIGHT_PANEL_X, y + PANEL_Y, PANEL_W, PANEL_H, tier);
        drawWindow(g, x + WINDOW_X, y + PANEL_Y, tier);

        // Slots
        for (Slot slot : menu.slots) {
            drawSlot(g, x + slot.x, y + slot.y, slot.index == 0 ? tier : null);
        }

        drawProgress(g, x + BAR_X, y + BAR_Y, tier);

        // Divider above the inventory, with little diamonds at both ends
        int dy = y + MobFarmMenu.INV_Y - 16;
        g.fill(x + 12, dy, x2 - 12, dy + 1, tier.dark);
        g.fill(x + 10, dy - 1, x + 12, dy + 2, tier.color);
        g.fill(x2 - 12, dy - 1, x2 - 10, dy + 2, tier.color);
    }

    private static void drawGem(GuiGraphics g, int x, int y, FarmTier tier) {
        g.fill(x, y, x + 5, y + 5, tier.dark);
        g.fill(x + 1, y + 1, x + 4, y + 4, tier.light);
        g.fill(x + 1, y + 1, x + 2, y + 2, 0xFFFFFFFF);
    }

    private static void drawPanel(GuiGraphics g, int x, int y, int w, int h, FarmTier tier) {
        g.fill(x - 1, y - 1, x + w + 1, y + h + 1, tier.dark);
        g.fill(x, y, x + w, y + h, PANEL_BG);
        g.fill(x, y, x + w, y + 1, (tier.color & 0x00FFFFFF) | 0x80000000);
    }

    private static void drawSlot(GuiGraphics g, int x, int y, FarmTier highlight) {
        if (highlight != null) {
            g.fill(x - 3, y - 3, x + 19, y + 19, highlight.dark);
            g.fill(x - 2, y - 2, x + 18, y + 18, highlight.color);
        }
        g.fill(x - 1, y - 1, x + 17, y + 17, SLOT_SHADE);
        g.fill(x, y, x + 17, y + 17, 0xFF3A3A46);
        g.fill(x, y, x + 16, y + 16, SLOT);
    }

    /** Window with the caught mob turning on a little pedestal. */
    private void drawWindow(GuiGraphics g, int x, int y, FarmTier tier) {
        int x2 = x + WINDOW_W;
        int y2 = y + PANEL_H;
        g.fill(x - 1, y - 1, x2 + 1, y2 + 1, tier.color);
        g.fillGradient(x, y, x2, y2, 0xFF0C0C12, (tier.dark & 0x00FFFFFF) | 0xFF000000);
        // pedestal
        int cx = x + WINDOW_W / 2;
        g.fill(cx - 18, y2 - 9, cx + 18, y2 - 7, tier.dark);
        g.fill(cx - 16, y2 - 10, cx + 16, y2 - 9, tier.color);

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

    private void drawProgress(GuiGraphics g, int x, int y, FarmTier tier) {
        g.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, 0xFF000000);
        g.fill(x, y, x + BAR_W, y + BAR_H, 0xFF1A1A22);

        int status = menu.status();
        float fraction = status == MobFarmBlockEntity.STATUS_NO_MOB ? 0.0F
                : Mth.clamp(menu.progress() / (float) menu.maxProgress(), 0.0F, 1.0F);
        int filled = Math.round(BAR_W * fraction);
        for (int px = 0; px < filled; px++) {
            float t = (float) px / Math.max(1, BAR_W - 1);
            g.fill(x + px, y, x + px + 1, y + BAR_H, lerp(tier.dark, tier.light, t));
        }
        g.fill(x, y, x + filled, y + 1, 0x60FFFFFF);
        if (status == MobFarmBlockEntity.STATUS_RUNNING && filled > 0) {
            int sweep = (int) ((Util.getMillis() % 1600L) / 1600.0F * (BAR_W + 20)) - 10;
            for (int d = -5; d <= 5; d++) {
                int sx = sweep + d;
                if (sx < 0 || sx >= filled) continue;
                int alpha = (int) (0x50 * (1.0F - Math.abs(d) / 6.0F));
                g.fill(x + sx, y, x + sx + 1, y + BAR_H, (alpha << 24) | 0xFFFFFF);
            }
        }
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        FarmTier tier = menu.tier();
        // Title, centred
        g.drawString(font, title, (imageWidth - font.width(title)) / 2, 7, tier.light, true);

        // Left panel: caption above the lasso, speed below it; right panel is the loot grid.
        int leftCenter = LEFT_PANEL_X + PANEL_W / 2;
        Component lasso = Component.translatable("gui.mobfarm.lasso");
        g.drawString(font, lasso, leftCenter - font.width(lasso) / 2, PANEL_Y + 5, TEXT_DIM, false);
        Component speed = Component.translatable("gui.mobfarm.speed", tier.ticks / 20);
        g.drawString(font, speed, leftCenter - font.width(speed) / 2, PANEL_Y + PANEL_H - 13, TEXT, false);

        // Status under the progress bar, centred
        Component status = statusText();
        g.drawString(font, status, (imageWidth - font.width(status)) / 2, BAR_Y + BAR_H + 4, TEXT, false);

        Component inv = playerInventoryTitle;
        g.drawString(font, inv, (imageWidth - font.width(inv)) / 2, MobFarmMenu.INV_Y - 12, TEXT_DIM, false);
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
