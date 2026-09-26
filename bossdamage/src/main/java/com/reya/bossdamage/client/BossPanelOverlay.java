package com.reya.bossdamage.client;

import java.util.List;

import com.reya.bossdamage.network.BossInfoPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.SpawnEggItem;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Boss panel at the top of the screen: icon, name, "health / max" and a red bar, then
 * "Damage Progress" with each player's share of the boss's max health.
 */
public final class BossPanelOverlay {
    private static final int MIN_WIDTH = 200;
    private static final int TOP = 4;
    private static final int GAP = 4;
    private static final int MAX_PANELS = 3;

    private static final int BORDER = 0xFFE0559A;
    private static final int BG = 0xE0100E1C;
    private static final int ICON_BG = 0xFF1C1A2C;
    private static final int ICON_EDGE = 0xFF5A5670;
    private static final int NAME = 0xFFFFFFFF;
    private static final int HP_TEXT = 0xFFD8D8E0;
    private static final int BAR_BG = 0xFF2A0C10;
    private static final int BAR = 0xFFC8202A;
    private static final int BAR_LIGHT = 0xFFFF5A5A;
    private static final int DIVIDER = 0xFF3A3450;
    private static final int HEADER = 0xFFE0B040;
    private static final int HEADER_LINE = 0xFF6A5A30;
    private static final int SELF = 0xFF55FF55;
    private static final int OTHER = 0xFFE0E0E0;

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;

        List<BossInfoPacket> bosses = ClientBossData.current();
        int y = TOP;
        int shown = 0;
        for (BossInfoPacket boss : bosses) {
            if (shown++ >= MAX_PANELS) break;
            int width = panelWidth(mc.font, boss, screenWidth);
            y += drawPanel(g, mc, boss, (screenWidth - width) / 2, y, width) + GAP;
        }
        if (bosses.size() > MAX_PANELS) {
            String more = "+" + (bosses.size() - MAX_PANELS);
            g.drawString(mc.font, more, (screenWidth - mc.font.width(more)) / 2, y, HP_TEXT, true);
        }
    }

    private static String hpText(BossInfoPacket boss) {
        return compact(boss.health()) + " / " + compact(boss.maxHealth());
    }

    /** Wide enough for the name and the health text, like the panel grows for long boss names. */
    private static int panelWidth(Font font, BossInfoPacket boss, int screenWidth) {
        int needed = 29 + font.width(boss.name()) + 12 + font.width(hpText(boss)) + 6;
        return Math.min(Math.max(MIN_WIDTH, needed), screenWidth - 20);
    }

    /** Draws one panel and returns its height. */
    private static int drawPanel(GuiGraphics g, Minecraft mc, BossInfoPacket boss, int x, int y, int width) {
        Font font = mc.font;
        List<BossInfoPacket.Row> rows = boss.rows();
        int height = 30 + (rows.isEmpty() ? 0 : 13 + rows.size() * 10) + 2;

        // Frame
        g.fill(x - 2, y - 2, x + width + 2, y + height + 2, BORDER);
        g.fill(x, y, x + width, y + height, BG);

        // Icon
        g.fill(x + 3, y + 3, x + 25, y + 25, ICON_EDGE);
        g.fill(x + 4, y + 4, x + 24, y + 24, ICON_BG);
        drawPortrait(g, mc, boss, x + 4, y + 4, x + 24, y + 24);

        // Name and health numbers
        String hp = hpText(boss);
        int hpWidth = font.width(hp);
        int textLeft = x + 29;
        int nameWidth = width - 29 - hpWidth - 10;
        String name = font.plainSubstrByWidth(boss.name().getString(), nameWidth);
        g.drawString(font, name, textLeft, y + 4, NAME, true);
        g.drawString(font, hp, x + width - 4 - hpWidth, y + 4, HP_TEXT, true);

        // Health bar
        int barX = textLeft;
        int barY = y + 16;
        int barW = width - 29 - 4;
        g.fill(barX - 1, barY - 1, barX + barW + 1, barY + 6, 0xFF000000);
        g.fill(barX, barY, barX + barW, barY + 5, BAR_BG);
        float fraction = boss.maxHealth() <= 0 ? 0 : Math.max(0, Math.min(1, boss.health() / boss.maxHealth()));
        int filled = Math.round(barW * fraction);
        // Gradient from dark red on the left to bright red at the tip.
        for (int px = 0; px < filled; px++) {
            float t = barW <= 1 ? 1.0F : (float) px / (barW - 1);
            g.fill(barX + px, barY, barX + px + 1, barY + 5, lerp(0xFF7A0E16, BAR, t));
        }
        g.fill(barX, barY, barX + filled, barY + 1, BAR_LIGHT);

        if (rows.isEmpty()) return height;

        // "Damage Progress" header with a line
        g.fill(x + 3, y + 29, x + width - 3, y + 30, DIVIDER);
        Component header = Component.translatable("bossdamage.damage_progress");
        g.drawString(font, header, x + 5, y + 33, HEADER, false);
        int lineX = x + 5 + font.width(header) + 4;
        g.fill(lineX, y + 37, x + width - 5, y + 38, HEADER_LINE);

        // Leaderboard
        String self = mc.player.getGameProfile().getName();
        for (int i = 0; i < rows.size(); i++) {
            BossInfoPacket.Row row = rows.get(i);
            int ry = y + 45 + i * 10;
            int color = row.name().equals(self) ? SELF : OTHER;
            g.fill(x + 5, ry, x + 6, ry + 7, color);
            g.fill(x + 8, ry + 2, x + 10, ry + 4, color);
            g.fill(x + 7, ry + 4, x + 11, ry + 7, color);
            String label = (i + 1) + " : " + font.plainSubstrByWidth(row.name(), 110);
            g.drawString(font, label, x + 14, ry, color, false);
            String percent = String.format("%.2f%%", row.percent());
            g.drawString(font, percent, x + width - 6 - font.width(percent), ry, color, false);
        }
        return height;
    }

    /**
     * Live 3D model of the boss, clipped to the portrait box; falls back to an item icon
     * when the entity isn't loaded on this client.
     */
    private static void drawPortrait(GuiGraphics g, Minecraft mc, BossInfoPacket boss, int x1, int y1, int x2, int y2) {
        Entity entity = mc.level == null ? null : mc.level.getEntity(boss.entityId());
        if (entity instanceof LivingEntity living && boss.health() > 0.0F) {
            float size = Math.max(living.getBbHeight(), living.getBbWidth());
            int scale = Math.max(1, (int) (15.0F / Math.max(0.5F, size)));
            int centerX = (x1 + x2) / 2;
            int feetY = y2 - 2;
            g.enableScissor(x1, y1, x2, y2);
            try {
                InventoryScreen.renderEntityInInventoryFollowsMouse(g, centerX, feetY, scale,
                        centerX - 30, y1 + 4, living);
            } catch (RuntimeException e) {
                g.renderItem(iconFor(boss.type()), x1 + 2, y1 + 2);
            }
            g.disableScissor();
        } else {
            g.renderItem(iconFor(boss.type()), x1 + 2, y1 + 2);
        }
    }

    private static int lerp(int from, int to, float t) {
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int gr = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    private static ItemStack iconFor(ResourceLocation typeId) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(typeId);
        if (type == EntityType.ENDER_DRAGON) return new ItemStack(Items.DRAGON_HEAD);
        if (type == EntityType.WITHER) return new ItemStack(Items.NETHER_STAR);
        if (type == EntityType.WARDEN) return new ItemStack(Items.SCULK_SHRIEKER);
        if (type == EntityType.ELDER_GUARDIAN) return new ItemStack(Items.PRISMARINE_CRYSTALS);
        SpawnEggItem egg = ForgeSpawnEggItem.fromEntityType(type);
        return egg != null ? new ItemStack(egg) : new ItemStack(Items.SKELETON_SKULL);
    }

    /** 172.8, 12.5K, 3.2M ... */
    public static String compact(float value) {
        String suffix = "";
        if (Math.abs(value) >= 1.0E9F) {
            value /= 1.0E9F;
            suffix = "B";
        } else if (Math.abs(value) >= 1.0E6F) {
            value /= 1.0E6F;
            suffix = "M";
        } else if (Math.abs(value) >= 1.0E4F) {
            value /= 1.0E3F;
            suffix = "K";
        }
        float rounded = Math.round(value * 10.0F) / 10.0F;
        String number = rounded == Math.round(rounded) ? String.valueOf(Math.round(rounded)) : String.valueOf(rounded);
        return number + suffix;
    }

    private BossPanelOverlay() {
    }
}
