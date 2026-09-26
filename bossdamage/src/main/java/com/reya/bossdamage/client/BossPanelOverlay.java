package com.reya.bossdamage.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.reya.bossdamage.network.BossInfoPacket;
import net.minecraft.Util;
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
 * Mob / boss panels at the top of the screen in a translucent "night sky" style: gradient sky,
 * twinkling stars, a pink city skyline, a light frame with snowflake corners. Panels fade and
 * slide in and out, the health bar eases towards the real value and leaves a pale "ghost" of
 * the damage just taken, and a shimmer sweeps across it. Boss panels add "Damage Progress";
 * below them sits the same panel for the mob under the crosshair.
 */
public final class BossPanelOverlay {
    private static final int MIN_WIDTH = 200;
    private static final int TOP = 6;
    private static final int GAP = 6;
    private static final int MAX_PANELS = 3;
    private static final long FADE_MS = 220L;

    // Night-sky palette (ARGB; alpha is multiplied by the panel's fade)
    private static final int SKY_TOP = 0xB0101030;
    private static final int SKY_BOTTOM = 0xB0331C55;
    private static final int FRAME = 0xF0E4E4FA;
    private static final int FRAME_INNER = 0x50FFFFFF;
    private static final int SKYLINE = 0x70C2489A;
    private static final int SKYLINE_BACK = 0x40E070C0;
    private static final int WINDOW = 0xB0FFE08A;
    private static final int STAR = 0xFFFFFFFF;
    private static final int STAR_MINT = 0xFFBFFFF0;
    private static final int PORTRAIT_BG = 0x60080820;
    private static final int PORTRAIT_EDGE = 0xB0DCDCF5;
    private static final int NAME = 0xFFFFFFFF;
    private static final int HP_TEXT = 0xFFD9D4F2;
    private static final int BAR_BG = 0x90200A24;
    private static final int BAR_FROM = 0xFFB0103A;
    private static final int BAR_TO = 0xFFFF5C8A;
    private static final int BAR_GHOST = 0xFFFFD6E6;
    private static final int DIVIDER = 0x60E4E4FA;
    private static final int HEADER = 0xFFFFD27A;
    private static final int SELF = 0xFF7CFFB0;
    private static final int OTHER = 0xFFEDEAFF;

    /** Per-panel animation state, keyed by entity id. */
    private static final class Anim {
        BossInfoPacket info;
        float shownHealth;
        float ghostHealth;
        long ghostHoldUntil;
        long appearedAt;
        long goneAt = -1L;

        Anim(BossInfoPacket info, long now) {
            this.info = info;
            this.shownHealth = info.health();
            this.ghostHealth = info.health();
            this.appearedAt = now;
        }
    }

    private static final Map<Integer, Anim> ANIMS = new HashMap<>();
    private static long lastFrame = Util.getMillis();

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null) return;

        long now = Util.getMillis();
        float dt = Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;

        // What should be on screen this frame: boss panels, then the hovered mob.
        List<BossInfoPacket> wanted = new ArrayList<>();
        List<BossInfoPacket> bosses = ClientBossData.current();
        for (int i = 0; i < bosses.size() && i < MAX_PANELS; i++) wanted.add(bosses.get(i));
        LivingEntity hovered = HoveredEntity.find(partialTick);
        if (hovered != null && bosses.stream().noneMatch(b -> b.entityId() == hovered.getId())) {
            wanted.add(hoverInfo(hovered));
        }

        for (BossInfoPacket info : wanted) {
            Anim anim = ANIMS.get(info.entityId());
            if (anim == null || anim.goneAt >= 0) {
                anim = new Anim(info, now);
                ANIMS.put(info.entityId(), anim);
            }
            anim.info = info;
        }
        for (Anim anim : ANIMS.values()) {
            boolean present = wanted.stream().anyMatch(w -> w.entityId() == anim.info.entityId());
            if (!present && anim.goneAt < 0) anim.goneAt = now;
        }

        // Draw in "wanted" order, then the ones fading out underneath.
        List<Anim> order = new ArrayList<>();
        for (BossInfoPacket info : wanted) order.add(ANIMS.get(info.entityId()));
        for (Anim anim : ANIMS.values()) if (anim.goneAt >= 0) order.add(anim);

        int y = TOP;
        for (Anim anim : order) {
            float alpha = anim.goneAt >= 0
                    ? 1.0F - (now - anim.goneAt) / (float) FADE_MS
                    : Math.min(1.0F, (now - anim.appearedAt) / (float) FADE_MS);
            alpha = Mth.clamp(alpha, 0.0F, 1.0F);
            float ease = 1.0F - (1.0F - alpha) * (1.0F - alpha);

            updateHealth(anim, dt, now);
            int width = panelWidth(mc.font, anim.info, screenWidth);
            int slide = Math.round((1.0F - ease) * -10.0F);
            int height = drawPanel(g, mc, anim, (screenWidth - width) / 2, y + slide, width, alpha, now);
            if (anim.goneAt < 0) y += height + GAP;
        }

        Iterator<Anim> it = ANIMS.values().iterator();
        while (it.hasNext()) {
            Anim anim = it.next();
            if (anim.goneAt >= 0 && now - anim.goneAt > FADE_MS) it.remove();
        }
    }

    /** Health eases toward the real value; the ghost waits a moment, then drains after it. */
    private static void updateHealth(Anim anim, float dt, long now) {
        float target = anim.info.health();
        if (target < anim.shownHealth - 0.01F) {
            anim.ghostHoldUntil = now + 450L;
            if (anim.ghostHealth < anim.shownHealth) anim.ghostHealth = anim.shownHealth;
        }
        anim.shownHealth += (target - anim.shownHealth) * Math.min(1.0F, dt * 12.0F);
        if (Math.abs(target - anim.shownHealth) < 0.01F) anim.shownHealth = target;

        if (anim.ghostHealth < anim.shownHealth) {
            anim.ghostHealth = anim.shownHealth;
        } else if (now > anim.ghostHoldUntil) {
            anim.ghostHealth += (anim.shownHealth - anim.ghostHealth) * Math.min(1.0F, dt * 4.0F);
        }
    }

    private static BossInfoPacket hoverInfo(LivingEntity entity) {
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(entity.getType());
        return new BossInfoPacket(entity.getId(), type == null ? new ResourceLocation("minecraft", "pig") : type,
                entity.getDisplayName(), entity.getHealth(), entity.getMaxHealth(), List.of());
    }

    private static String hpText(BossInfoPacket boss) {
        return compact(boss.health()) + " / " + compact(boss.maxHealth());
    }

    /** Wide enough for the name and the health text. */
    private static int panelWidth(Font font, BossInfoPacket boss, int screenWidth) {
        int needed = 32 + font.width(boss.name()) + 12 + font.width(hpText(boss)) + 8;
        return Math.min(Math.max(MIN_WIDTH, needed), screenWidth - 20);
    }

    // ------------------------------------------------------------------ drawing

    /** Draws one panel and returns its height. */
    private static int drawPanel(GuiGraphics g, Minecraft mc, Anim anim, int x, int y, int width, float alpha, long now) {
        Font font = mc.font;
        BossInfoPacket boss = anim.info;
        List<BossInfoPacket.Row> rows = boss.rows();
        int height = 32 + (rows.isEmpty() ? 0 : 14 + rows.size() * 10) + 2;
        int x2 = x + width;
        int y2 = y + height;
        // Text with an alpha below 4 is drawn fully opaque by the font renderer, so skip the last frames.
        if (alpha < 0.05F) return height;

        drawSky(g, x, y, x2, y2, boss.entityId(), alpha, now);
        drawFrame(g, x, y, x2, y2, alpha);

        // Portrait
        int px = x + 5;
        int py = y + 5;
        g.fill(px - 1, py - 1, px + 23, py + 23, fade(PORTRAIT_EDGE, alpha));
        g.fill(px, py, px + 22, py + 22, fade(PORTRAIT_BG, alpha));
        if (alpha > 0.6F) drawPortrait(g, mc, boss, px, py, px + 22, py + 22);

        // Name and health numbers
        int textLeft = x + 32;
        String hp = hpText(boss);
        int hpWidth = font.width(hp);
        String name = font.plainSubstrByWidth(boss.name().getString(), width - 32 - hpWidth - 14);
        g.drawString(font, name, textLeft, y + 6, fade(NAME, alpha), true);
        g.drawString(font, hp, x2 - 7 - hpWidth, y + 6, fade(HP_TEXT, alpha), true);

        drawHealthBar(g, anim, textLeft, y + 18, x2 - 7 - textLeft, alpha, now);

        if (rows.isEmpty()) return height;

        // "Damage Progress"
        Component header = Component.translatable("bossdamage.damage_progress");
        g.drawString(font, header, x + 7, y + 35, fade(HEADER, alpha), true);
        int lineX = x + 7 + font.width(header) + 5;
        g.fill(lineX, y + 39, x2 - 7, y + 40, fade(DIVIDER, alpha));

        String self = mc.player.getGameProfile().getName();
        for (int i = 0; i < rows.size(); i++) {
            BossInfoPacket.Row row = rows.get(i);
            int ry = y + 47 + i * 10;
            int color = fade(row.name().equals(self) ? SELF : OTHER, alpha);
            // little person icon
            g.fill(x + 8, ry + 1, x + 10, ry + 3, color);
            g.fill(x + 7, ry + 4, x + 11, ry + 7, color);
            String label = (i + 1) + " : " + font.plainSubstrByWidth(row.name(), 110);
            g.drawString(font, label, x + 14, ry, color, true);
            String percent = String.format("%.2f%%", row.percent());
            g.drawString(font, percent, x2 - 8 - font.width(percent), ry, color, true);
        }
        return height;
    }

    /** Gradient night sky with twinkling stars and a city skyline along the bottom. */
    private static void drawSky(GuiGraphics g, int x, int y, int x2, int y2, int seed, float alpha, long now) {
        g.fillGradient(x + 1, y + 1, x2 - 1, y2 - 1, fade(SKY_TOP, alpha), fade(SKY_BOTTOM, alpha));

        // Skyline: two layers of buildings with a few lit windows.
        int base = y2 - 1;
        for (int bx = x + 2; bx < x2 - 2; ) {
            int w = 5 + hash(seed, bx, 1) % 7;
            int h = 4 + hash(seed, bx, 2) % 9;
            g.fill(bx, base - h - 3, Math.min(bx + w, x2 - 2), base, fade(SKYLINE_BACK, alpha));
            bx += w + 1;
        }
        for (int bx = x + 4; bx < x2 - 3; ) {
            int w = 4 + hash(seed, bx, 3) % 6;
            int h = 2 + hash(seed, bx, 4) % 7;
            int right = Math.min(bx + w, x2 - 2);
            g.fill(bx, base - h, right, base, fade(SKYLINE, alpha));
            if (h > 4 && hash(seed, bx, 5) % 3 == 0) {
                g.fill(bx + 1, base - h + 2, bx + 2, base - h + 3, fade(WINDOW, alpha));
            }
            bx += w + 2 + hash(seed, bx, 6) % 4;
        }

        // Stars: 1px dots and a few mint "plus" sparkles, each twinkling at its own pace.
        int area = (x2 - x) * (y2 - y);
        int count = Math.max(6, area / 260);
        for (int i = 0; i < count; i++) {
            int sx = x + 3 + hash(seed, i, 7) % Math.max(1, x2 - x - 6);
            int sy = y + 3 + hash(seed, i, 8) % Math.max(1, (y2 - y) - 14);
            float phase = (hash(seed, i, 9) % 1000) / 1000.0F * Mth.TWO_PI;
            float speed = 1.2F + (hash(seed, i, 10) % 100) / 60.0F;
            float twinkle = 0.25F + 0.75F * Mth.square(Mth.sin(now / 1000.0F * speed + phase));
            if (i % 5 == 0) {
                int c = fade(STAR_MINT, alpha * twinkle);
                g.fill(sx, sy - 1, sx + 1, sy + 2, c);
                g.fill(sx - 1, sy, sx + 2, sy + 1, c);
            } else {
                g.fill(sx, sy, sx + 1, sy + 1, fade(STAR, alpha * twinkle * 0.8F));
            }
        }
    }

    /** Light frame with rounded corners and little snowflakes on the corners. */
    private static void drawFrame(GuiGraphics g, int x, int y, int x2, int y2, float alpha) {
        int c = fade(FRAME, alpha);
        g.fill(x + 2, y, x2 - 2, y + 1, c);
        g.fill(x + 2, y2 - 1, x2 - 2, y2, c);
        g.fill(x, y + 2, x + 1, y2 - 2, c);
        g.fill(x2 - 1, y + 2, x2, y2 - 2, c);
        g.fill(x + 1, y + 1, x + 2, y + 2, c);
        g.fill(x2 - 2, y + 1, x2 - 1, y + 2, c);
        g.fill(x + 1, y2 - 2, x + 2, y2 - 1, c);
        g.fill(x2 - 2, y2 - 2, x2 - 1, y2 - 1, c);

        int inner = fade(FRAME_INNER, alpha);
        g.fill(x + 2, y + 2, x2 - 2, y + 3, inner);
        g.fill(x + 2, y2 - 3, x2 - 2, y2 - 2, inner);

        drawSnowflake(g, x + 3, y + 3, c);
        drawSnowflake(g, x2 - 4, y + 3, c);
        drawSnowflake(g, x + 3, y2 - 4, c);
        drawSnowflake(g, x2 - 4, y2 - 4, c);
    }

    private static void drawSnowflake(GuiGraphics g, int cx, int cy, int c) {
        g.fill(cx - 2, cy, cx + 3, cy + 1, c);
        g.fill(cx, cy - 2, cx + 1, cy + 3, c);
        g.fill(cx - 1, cy - 1, cx, cy, c);
        g.fill(cx + 1, cy - 1, cx + 2, cy, c);
        g.fill(cx - 1, cy + 1, cx, cy + 2, c);
        g.fill(cx + 1, cy + 1, cx + 2, cy + 2, c);
    }

    private static void drawHealthBar(GuiGraphics g, Anim anim, int bx, int by, int bw, float alpha, long now) {
        float max = Math.max(1.0F, anim.info.maxHealth());
        int filled = Math.round(bw * Mth.clamp(anim.shownHealth / max, 0.0F, 1.0F));
        int ghost = Math.round(bw * Mth.clamp(anim.ghostHealth / max, 0.0F, 1.0F));

        // Rounded track
        int edge = fade(0xA0000010, alpha);
        g.fill(bx, by - 1, bx + bw, by, edge);
        g.fill(bx, by + 6, bx + bw, by + 7, edge);
        g.fill(bx - 1, by, bx, by + 6, edge);
        g.fill(bx + bw, by, bx + bw + 1, by + 6, edge);
        g.fill(bx, by, bx + bw, by + 6, fade(BAR_BG, alpha));

        // Ghost of recent damage
        if (ghost > filled) g.fill(bx + filled, by, bx + ghost, by + 6, fade(BAR_GHOST, alpha * 0.85F));

        // Gradient fill with a highlight on top
        for (int px = 0; px < filled; px++) {
            float t = bw <= 1 ? 1.0F : (float) px / (bw - 1);
            g.fill(bx + px, by, bx + px + 1, by + 6, fade(lerp(BAR_FROM, BAR_TO, t), alpha));
        }
        g.fill(bx, by, bx + filled, by + 1, fade(0x70FFFFFF, alpha));

        // Shimmer: a soft light band sweeping across the fill every 2.5 s
        float sweep = (now % 2500L) / 2500.0F;
        int center = Math.round(sweep * (bw + 30)) - 15;
        for (int d = -6; d <= 6; d++) {
            int sx = center + d;
            if (sx < 0 || sx >= filled) continue;
            float strength = 1.0F - Math.abs(d) / 7.0F;
            g.fill(bx + sx, by, bx + sx + 1, by + 6, fade(0x60FFFFFF, alpha * strength));
        }
    }

    /**
     * Live 3D model of the mob, clipped to the portrait box; falls back to an item icon
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
                g.renderItem(iconFor(boss.type()), x1 + 3, y1 + 3);
            }
            g.disableScissor();
        } else {
            g.renderItem(iconFor(boss.type()), x1 + 3, y1 + 3);
        }
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

    // ------------------------------------------------------------------ helpers

    /** Multiplies a colour's alpha by {@code alpha}. */
    private static int fade(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * Mth.clamp(alpha, 0.0F, 1.0F));
        return (a << 24) | (argb & 0xFFFFFF);
    }

    private static int lerp(int from, int to, float t) {
        int a = (int) Mth.lerp(t, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF);
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int gr = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return (a << 24) | (r << 16) | (gr << 8) | b;
    }

    /** Stable pseudo-random number, so stars and buildings stay put for the same mob. */
    private static int hash(int seed, int a, int b) {
        int h = seed * 0x9E3779B1 + a * 0x85EBCA6B + b * 0xC2B2AE35;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        return h & 0x7FFFFFFF;
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
