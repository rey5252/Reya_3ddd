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
 * Mob / boss panels at the top of the screen in one of several translucent {@link PanelStyle}s
 * (night sky, royal gold, inferno, frost, sakura, forest, minimal). Panels fade and
 * slide in and out, the health bar eases towards the real value and leaves a pale "ghost" of
 * the damage just taken, and a shimmer sweeps across it. Boss panels add "Damage Progress";
 * below them sits the same panel for the mob under the crosshair.
 */
public final class BossPanelOverlay {
    private static final int MIN_WIDTH = 200;
    private static final int GAP = 6;
    private static final int MAX_PANELS = 3;
    private static final long FADE_MS = 220L;

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
    private static long previewUntil;

    /** Show a panel for the player for a moment, so a newly picked style can be seen. */
    public static void preview() {
        previewUntil = Util.getMillis() + 2500L;
    }
    private static long lastFrame = Util.getMillis();

    public static void render(ForgeGui gui, GuiGraphics g, float partialTick, int screenWidth, int screenHeight) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.player == null || mc.screen instanceof MovePanelScreen) return;

        PanelStyle style = ClientConfig.style();
        long now = Util.getMillis();
        float dt = Math.min(0.1F, (now - lastFrame) / 1000.0F);
        lastFrame = now;

        // What should be on screen this frame: boss panels, then the hovered mob.
        List<BossInfoPacket> wanted = new ArrayList<>();
        List<BossInfoPacket> bosses = ClientBossData.current();
        for (int i = 0; i < bosses.size() && i < MAX_PANELS; i++) wanted.add(bosses.get(i));
        LivingEntity hovered = HoveredEntity.find(partialTick);
        if (hovered == null && now < previewUntil) hovered = mc.player; // preview after switching style
        LivingEntity shownMob = hovered;
        if (shownMob != null && bosses.stream().noneMatch(b -> b.entityId() == shownMob.getId())) {
            wanted.add(hoverInfo(shownMob));
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

        // Where the player put the panel: horizontal centre and top edge, as fractions of the screen.
        int centerX = Math.round((float) ClientConfig.panelX() * screenWidth);
        int y = Math.round((float) ClientConfig.panelY() * screenHeight);
        for (Anim anim : order) {
            float alpha = anim.goneAt >= 0
                    ? 1.0F - (now - anim.goneAt) / (float) FADE_MS
                    : Math.min(1.0F, (now - anim.appearedAt) / (float) FADE_MS);
            alpha = Mth.clamp(alpha, 0.0F, 1.0F);
            float ease = 1.0F - (1.0F - alpha) * (1.0F - alpha);

            updateHealth(anim, dt, now);
            int width = panelWidth(mc.font, anim.info, screenWidth);
            int slide = Math.round((1.0F - ease) * -10.0F);
            int px = Mth.clamp(centerX - width / 2, 4, Math.max(4, screenWidth - width - 4));
            int py = Mth.clamp(y, 4, Math.max(4, screenHeight - panelHeight(anim.info) - 4));
            int height = drawPanel(g, mc, style, anim, px, py + slide, width, alpha, now);
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

    public static int panelHeight(BossInfoPacket boss) {
        return 32 + (boss.rows().isEmpty() ? 0 : 14 + boss.rows().size() * 10) + 2;
    }

    /** Wide enough for the name and the health text. */
    private static int panelWidth(Font font, BossInfoPacket boss, int screenWidth) {
        int needed = 32 + font.width(boss.name()) + 12 + font.width(hpText(boss)) + 8;
        return Math.min(Math.max(MIN_WIDTH, needed), screenWidth - 20);
    }

    // ------------------------------------------------------------------ drawing

    /** Draws one panel and returns its height. */
    private static int drawPanel(GuiGraphics g, Minecraft mc, PanelStyle st, Anim anim, int x, int y, int width,
                                 float alpha, long now) {
        Font font = mc.font;
        BossInfoPacket boss = anim.info;
        List<BossInfoPacket.Row> rows = boss.rows();
        int height = panelHeight(boss);
        int x2 = x + width;
        int y2 = y + height;
        // Text with an alpha below 4 is drawn fully opaque by the font renderer, so skip the last frames.
        if (alpha < 0.05F) return height;

        // many 1px fills: batch them into one draw
        g.drawManaged(() -> drawBox(g, st, x, y, x2, y2, boss.entityId(), alpha, now));

        // Portrait
        int px = x + 5;
        int py = y + 5;
        int edge = fade(st.portraitEdge, alpha);
        g.fill(px, py, px + 22, py + 22, fade(st.portraitBg, alpha * 0.5F));
        g.fill(px - 1, py - 1, px + 23, py, edge);
        g.fill(px - 1, py + 22, px + 23, py + 23, edge);
        g.fill(px - 1, py, px, py + 22, edge);
        g.fill(px + 22, py, px + 23, py + 22, edge);
        if (alpha > 0.6F) drawPortrait(g, mc, boss, px, py, px + 22, py + 22);

        // Name and health numbers
        int textLeft = x + 32;
        String hp = hpText(boss);
        int hpWidth = font.width(hp);
        String name = font.plainSubstrByWidth(boss.name().getString(), width - 32 - hpWidth - 14);
        g.drawString(font, name, textLeft, y + 6, fade(st.nameText, alpha), st.textShadow);
        g.drawString(font, hp, x2 - 7 - hpWidth, y + 6, fade(st.hpText, alpha), st.textShadow);

        g.drawManaged(() -> drawHealthBar(g, st, anim, textLeft, y + 18, x2 - 7 - textLeft, alpha, now));

        if (rows.isEmpty()) return height;

        // "Damage Progress"
        Component header = Component.translatable("bossdamage.damage_progress");
        g.drawString(font, header, x + 7, y + 35, fade(st.header, alpha), st.textShadow);
        int lineX = x + 7 + font.width(header) + 5;
        g.fill(lineX, y + 39, x2 - 7, y + 40, fade(st.divider, alpha));

        String self = mc.player.getGameProfile().getName();
        for (int i = 0; i < rows.size(); i++) {
            BossInfoPacket.Row row = rows.get(i);
            int ry = y + 47 + i * 10;
            int color = fade(row.name().equals(self) ? st.self : st.other, alpha);
            // little person icon
            g.fill(x + 8, ry + 1, x + 10, ry + 3, color);
            g.fill(x + 7, ry + 4, x + 11, ry + 7, color);
            String label = (i + 1) + " : " + font.plainSubstrByWidth(row.name(), 110);
            g.drawString(font, label, x + 14, ry, color, st.textShadow);
            String percent = String.format("%.2f%%", row.percent());
            g.drawString(font, percent, x2 - 8 - font.width(percent), ry, color, st.textShadow);
        }
        return height;
    }

    /** Background, scenery, particles and frame of a style; also used by the style menu. */
    public static void drawBox(GuiGraphics g, PanelStyle st, int x, int y, int x2, int y2, int seed, float alpha, long now) {
        g.fillGradient(x + 1, y + 1, x2 - 1, y2 - 1, fade(st.bgTop, alpha), fade(st.bgBottom, alpha));
        drawScenery(g, st, x, y, x2, y2, seed, alpha, now);
        drawParticles(g, st, x, y, x2, y2, seed, alpha, now);
        drawFrame(g, st, x, y, x2, y2, alpha);
    }

    private static final Anim PREVIEW = new Anim(new BossInfoPacket(0, new ResourceLocation("minecraft", "player"),
            Component.empty(), 20.0F, 20.0F, List.of()), 0L);
    private static long lastPreviewFrame;

    /**
     * A sample panel for the style menu: the player's portrait, a health bar that keeps taking hits
     * (so the ghost and shimmer show) and a made-up damage leaderboard. Returns its height.
     */
    public static int drawPreview(GuiGraphics g, Minecraft mc, PanelStyle st, int x, int y, int width, long now) {
        float max = 200.0F;
        float health = max * (1.0F - 0.14F * ((now / 1200L) % 6L));
        String self = mc.player != null ? mc.player.getGameProfile().getName() : "You";
        List<BossInfoPacket.Row> rows = List.of(
                new BossInfoPacket.Row(self, 64.21F),
                new BossInfoPacket.Row("Steve", 21.48F),
                new BossInfoPacket.Row("Alex", 9.74F));
        PREVIEW.info = new BossInfoPacket(Integer.MIN_VALUE, // no real entity: portrait shows the dragon head icon
                new ResourceLocation("minecraft", "ender_dragon"),
                Component.translatable("entity.minecraft.ender_dragon"), health, max, rows);
        float dt = Math.min(0.1F, (now - lastPreviewFrame) / 1000.0F);
        lastPreviewFrame = now;
        updateHealth(PREVIEW, dt, now);
        return drawPanel(g, mc, st, PREVIEW, x, y, width, 1.0F, now);
    }

    /** Scenery along the bottom edge: city skyline, hills, flames or grass. */
    private static void drawScenery(GuiGraphics g, PanelStyle st, int x, int y, int x2, int y2, int seed,
                                    float alpha, long now) {
        int base = y2 - 1;
        float t = now / 1000.0F;
        switch (st.scenery) {
            case SKYLINE -> {
                for (int bx = x + 2; bx < x2 - 2; ) {
                    int w = 5 + hash(seed, bx, 1) % 7;
                    int h = 4 + hash(seed, bx, 2) % 9;
                    g.fill(bx, base - h - 3, Math.min(bx + w, x2 - 2), base, fade(st.sceneryBack, alpha));
                    bx += w + 1;
                }
                for (int bx = x + 4; bx < x2 - 3; ) {
                    int w = 4 + hash(seed, bx, 3) % 6;
                    int h = 2 + hash(seed, bx, 4) % 7;
                    g.fill(bx, base - h, Math.min(bx + w, x2 - 2), base, fade(st.sceneryFront, alpha));
                    if (h > 4 && hash(seed, bx, 5) % 3 == 0) {
                        g.fill(bx + 1, base - h + 2, bx + 2, base - h + 3, fade(st.window, alpha));
                    }
                    bx += w + 2 + hash(seed, bx, 6) % 4;
                }
            }
            case HILLS -> {
                float phase = (seed % 100) * 0.37F;
                for (int cx = x + 1; cx < x2 - 1; cx++) {
                    int back = 6 + Math.round(Mth.sin(cx * 0.05F + phase) * 3 + Mth.sin(cx * 0.13F) * 2);
                    int front = 3 + Math.round(Mth.sin(cx * 0.09F + phase * 2) * 2 + Mth.sin(cx * 0.23F + 1) * 1.5F);
                    g.fill(cx, base - back, cx + 1, base, fade(st.sceneryBack, alpha));
                    g.fill(cx, base - front, cx + 1, base, fade(st.sceneryFront, alpha));
                }
            }
            case FLAMES -> {
                for (int cx = x + 1; cx < x2 - 1; cx++) {
                    float n = hash(seed, cx, 11) % 100 / 100.0F;
                    int back = 4 + Math.round(Math.abs(Mth.sin(cx * 0.31F + t * 5.0F + n * 3)) * 6);
                    int front = 2 + Math.round(Math.abs(Mth.sin(cx * 0.47F - t * 7.0F + n * 5)) * 4);
                    g.fill(cx, base - back, cx + 1, base, fade(st.sceneryBack, alpha));
                    g.fill(cx, base - front, cx + 1, base, fade(st.sceneryFront, alpha));
                }
            }
            case GRASS -> {
                g.fill(x + 1, base - 1, x2 - 1, base, fade(st.sceneryFront, alpha));
                for (int cx = x + 2; cx < x2 - 2; cx += 2) {
                    int h = 2 + hash(seed, cx, 12) % 5;
                    int sway = Math.round(Mth.sin(t * 2.0F + cx * 0.2F));
                    int color = fade(cx % 4 == 0 ? st.sceneryFront : st.sceneryBack, alpha);
                    g.fill(cx, base - h + 1, cx + 1, base, color);
                    g.fill(cx + sway, base - h, cx + sway + 1, base - h + 1, color);
                }
            }
            case NEBULA -> {
                // Soft drifting clouds of colour.
                for (int i = 0; i < 3; i++) {
                    int cx = x + (int) ((hash(seed, i, 13) % 1000 / 1000.0F * (x2 - x)) + Mth.sin(t * 0.15F + i) * 12);
                    int cy = y + 6 + hash(seed, i, 14) % Math.max(1, y2 - y - 12);
                    int color = i % 2 == 0 ? st.sceneryFront : st.sceneryBack;
                    for (int r = 16; r > 0; r -= 4) {
                        g.fill(Math.max(x + 1, cx - r * 2), Math.max(y + 1, cy - r / 2),
                                Math.min(x2 - 1, cx + r * 2), Math.min(y2 - 1, cy + r / 2), fade(color, alpha * 0.6F));
                    }
                }
            }
            case WAVES -> {
                for (int cx = x + 1; cx < x2 - 1; cx++) {
                    int back = 5 + Math.round(Mth.sin(cx * 0.12F + t * 1.6F) * 2.0F);
                    int front = 3 + Math.round(Mth.sin(cx * 0.18F - t * 2.3F + 1.0F) * 1.6F);
                    g.fill(cx, base - back, cx + 1, base, fade(st.sceneryBack, alpha));
                    g.fill(cx, base - front, cx + 1, base, fade(st.sceneryFront, alpha));
                    g.fill(cx, base - front, cx + 1, base - front + 1, fade(0xA0FFFFFF, alpha));
                }
            }
            case STAINS -> {
                // Old-map blotches and a faint dashed route.
                for (int i = 0; i < 4; i++) {
                    int cx = x + 30 + hash(seed, i, 15) % Math.max(1, x2 - x - 40);
                    int cy = y + 4 + hash(seed, i, 16) % Math.max(1, y2 - y - 8);
                    int r = 3 + hash(seed, i, 17) % 5;
                    g.fill(cx - r, cy - r / 2, cx + r, cy + r / 2, fade(st.sceneryFront, alpha));
                    g.fill(cx - r / 2, cy - r, cx + r / 2, cy + r, fade(st.sceneryBack, alpha));
                }
                for (int cx = x + 32; cx < x2 - 8; cx += 6) {
                    int cy = y2 - 5 - Math.round(Mth.sin(cx * 0.08F + seed) * 2);
                    g.fill(cx, cy, cx + 3, cy + 1, fade(0x406A3E1E, alpha));
                }
            }
            case NONE -> {
            }
        }
    }

    /** Animated decorations: twinkling stars/sparkles, rising embers, falling snow/petals, fireflies. */
    private static void drawParticles(GuiGraphics g, PanelStyle st, int x, int y, int x2, int y2, int seed,
                                      float alpha, long now) {
        if (st.particles == PanelStyle.Particles.NONE) return;
        float t = now / 1000.0F;
        int w = Math.max(1, x2 - x - 6);
        int h = Math.max(1, y2 - y - 6);
        int count = Math.max(6, w * h / 260);
        for (int i = 0; i < count; i++) {
            float phase = hash(seed, i, 9) % 1000 / 1000.0F;
            float speed = 0.6F + hash(seed, i, 10) % 100 / 100.0F;
            int bx = x + 3 + hash(seed, i, 7) % w;
            int by = y + 3 + hash(seed, i, 8) % h;
            switch (st.particles) {
                case STARS, SPARKLES -> {
                    float twinkle = 0.25F + 0.75F * Mth.square(Mth.sin(t * (1.2F + speed) + phase * Mth.TWO_PI));
                    boolean plus = st.particles == PanelStyle.Particles.SPARKLES ? i % 2 == 0 : i % 5 == 0;
                    int c = fade(plus ? st.accent : st.particle, alpha * twinkle);
                    if (plus) {
                        g.fill(bx, by - 1, bx + 1, by + 2, c);
                        g.fill(bx - 1, by, bx + 2, by + 1, c);
                    } else {
                        g.fill(bx, by, bx + 1, by + 1, c);
                    }
                }
                case EMBERS -> {
                    float p = (t * speed * 0.35F + phase) % 1.0F;
                    int py = y2 - 3 - Math.round(p * h);
                    int px = bx + Math.round(Mth.sin(t * 2.0F + i) * 2);
                    int c = fade(p < 0.5F ? st.particle : st.accent, alpha * (1.0F - p));
                    g.fill(px, py, px + 1, py + 1, c);
                }
                case SNOW, PETALS -> {
                    float p = (t * speed * 0.18F + phase) % 1.0F;
                    int py = y + 3 + Math.round(p * h);
                    int drift = st.particles == PanelStyle.Particles.PETALS ? 5 : 2;
                    int px = bx + Math.round(Mth.sin(t * 1.3F + i * 1.7F) * drift);
                    float fadeEdges = Math.min(1.0F, Math.min(p, 1.0F - p) * 6.0F);
                    int c = fade(st.particle, alpha * fadeEdges * 0.9F);
                    if (st.particles == PanelStyle.Particles.PETALS) {
                        g.fill(px, py, px + 2, py + 1, c);
                    } else {
                        g.fill(px, py, px + 1, py + 1, c);
                    }
                }
                case FIREFLIES -> {
                    if (i % 2 == 1) continue;
                    int px = bx + Math.round(Mth.sin(t * 0.7F * speed + i) * 8);
                    int py = by + Math.round(Mth.cos(t * 0.9F * speed + i * 1.3F) * 4);
                    float glow = 0.3F + 0.7F * Mth.square(Mth.sin(t * 2.2F + phase * Mth.TWO_PI));
                    g.fill(px - 1, py - 1, px + 2, py + 2, fade(st.particle, alpha * glow * 0.25F));
                    g.fill(px, py, px + 1, py + 1, fade(st.particle, alpha * glow));
                }
                case BUBBLES -> {
                    if (i % 2 == 1) continue;
                    float p = (t * speed * 0.3F + phase) % 1.0F;
                    int py = y2 - 3 - Math.round(p * h);
                    int px = bx + Math.round(Mth.sin(t * 3.0F + i) * 1.5F);
                    int c = fade(st.particle, alpha * (1.0F - p) * 0.9F);
                    if (i % 4 == 0) {
                        g.fill(px, py - 1, px + 2, py, c);
                        g.fill(px, py + 2, px + 2, py + 3, c);
                        g.fill(px - 1, py, px, py + 2, c);
                        g.fill(px + 2, py, px + 3, py + 2, c);
                    } else {
                        g.fill(px, py, px + 1, py + 1, c);
                    }
                }
                case NONE -> {
                }
            }
        }
    }

    /** Rounded frame with style-specific corner ornaments. */
    private static void drawFrame(GuiGraphics g, PanelStyle st, int x, int y, int x2, int y2, float alpha) {
        if (st.frameType == PanelStyle.Frame.RAINBOW) {
            drawRainbowRim(g, x, y, x2, y2, alpha);
        }
        int c = fade(st.frame, alpha);
        boolean thick = st.frameType == PanelStyle.Frame.GEMS || st.frameType == PanelStyle.Frame.WOOD
                || st.frameType == PanelStyle.Frame.RIVETS || st.frameType == PanelStyle.Frame.RAINBOW;
        int t = thick ? 2 : 1;
        g.fill(x + 2, y, x2 - 2, y + t, c);
        g.fill(x + 2, y2 - t, x2 - 2, y2, c);
        g.fill(x, y + 2, x + t, y2 - 2, c);
        g.fill(x2 - t, y + 2, x2, y2 - 2, c);
        g.fill(x + 1, y + 1, x + 2, y + 2, c);
        g.fill(x2 - 2, y + 1, x2 - 1, y + 2, c);
        g.fill(x + 1, y2 - 2, x + 2, y2 - 1, c);
        g.fill(x2 - 2, y2 - 2, x2 - 1, y2 - 1, c);
        if (st.frameType == PanelStyle.Frame.RAINBOW) {
            g.fill(x, y, x + 2, y + 2, c);
            g.fill(x2 - 2, y, x2, y + 2, c);
            g.fill(x, y2 - 2, x + 2, y2, c);
            g.fill(x2 - 2, y2 - 2, x2, y2, c);
        }

        int inner = fade(st.frameInner, alpha);
        g.fill(x + 2, y + t + 1, x2 - 2, y + t + 2, inner);
        g.fill(x + 2, y2 - t - 2, x2 - 2, y2 - t - 1, inner);

        int accent = fade(st.accent, alpha);
        switch (st.frameType) {
            case SNOWFLAKES -> {
                drawSnowflake(g, x + 3, y + 3, c);
                drawSnowflake(g, x2 - 4, y + 3, c);
                drawSnowflake(g, x + 3, y2 - 4, c);
                drawSnowflake(g, x2 - 4, y2 - 4, c);
            }
            case GEMS -> {
                int stud = fade(0xFF8C6A1E, alpha);
                for (int i = x + 10; i < x2 - 10; i += 10) {
                    g.fill(i, y, i + 1, y + 1, stud);
                    g.fill(i, y2 - 1, i + 1, y2, stud);
                }
                drawGem(g, x - 2, y - 2, accent, alpha);
                drawGem(g, x2 - 4, y - 2, accent, alpha);
                drawGem(g, x - 2, y2 - 4, accent, alpha);
                drawGem(g, x2 - 4, y2 - 4, accent, alpha);
            }
            case FLOWERS -> {
                drawFlower(g, x + 3, y + 3, c, accent);
                drawFlower(g, x2 - 4, y + 3, c, accent);
                drawFlower(g, x + 3, y2 - 4, c, accent);
                drawFlower(g, x2 - 4, y2 - 4, c, accent);
            }
            case EMBERS -> {
                g.fill(x + 2, y + 2, x + 4, y + 4, accent);
                g.fill(x2 - 4, y + 2, x2 - 2, y + 4, accent);
                g.fill(x + 2, y2 - 4, x + 4, y2 - 2, accent);
                g.fill(x2 - 4, y2 - 4, x2 - 2, y2 - 2, accent);
            }
            case ORNATE -> {
                // Glow around the frame and a pointed crest on top.
                int glow = fade(st.frame, alpha * 0.25F);
                g.fill(x - 1, y + 2, x, y2 - 2, glow);
                g.fill(x2, y + 2, x2 + 1, y2 - 2, glow);
                g.fill(x + 2, y - 1, x2 - 2, y, glow);
                g.fill(x + 2, y2, x2 - 2, y2 + 1, glow);
                int mid = (x + x2) / 2;
                for (int i = 0; i < 4; i++) g.fill(mid - 4 + i, y - 1 - i, mid + 5 - i, y - i, c);
                g.fill(mid, y - 3, mid + 1, y - 2, accent);
                for (int i = 0; i < 3; i++) g.fill(mid - 3 + i, y2 + i, mid + 4 - i, y2 + i + 1, c);
                drawSnowflake(g, x + 3, y + 3, accent);
                drawSnowflake(g, x2 - 4, y + 3, accent);
                drawSnowflake(g, x + 3, y2 - 4, accent);
                drawSnowflake(g, x2 - 4, y2 - 4, accent);
            }
            case FOAM -> {
                // Bubbly foam along the top edge.
                for (int fx = x + 3; fx < x2 - 3; fx += 5) {
                    int r = 1 + (fx / 5) % 2;
                    g.fill(fx, y - r, fx + r + 2, y + 1, c);
                }
                for (int fx = x + 6; fx < x2 - 6; fx += 9) {
                    g.fill(fx, y2 - 1, fx + 3, y2 + 1, c);
                }
            }
            case WOOD -> {
                // Plank grain and dark corner brackets.
                int grain = fade(0xFF4A2A10, alpha);
                for (int gx = x + 8; gx < x2 - 8; gx += 12) {
                    g.fill(gx, y, gx + 5, y + 1, grain);
                    g.fill(gx + 4, y2 - 1, gx + 9, y2, grain);
                }
                int metal = fade(st.accent, alpha);
                g.fill(x - 1, y - 1, x + 4, y + 4, metal);
                g.fill(x2 - 4, y - 1, x2 + 1, y + 4, metal);
                g.fill(x - 1, y2 - 4, x + 4, y2 + 1, metal);
                g.fill(x2 - 4, y2 - 4, x2 + 1, y2 + 1, metal);
            }
            case RIVETS -> {
                int rivet = fade(0xFFC8CCD8, alpha);
                int dark = fade(0xFF3A3C44, alpha);
                for (int rx = x + 6; rx < x2 - 5; rx += 12) {
                    g.fill(rx, y, rx + 2, y + 2, rivet);
                    g.fill(rx + 1, y + 1, rx + 2, y + 2, dark);
                    g.fill(rx, y2 - 2, rx + 2, y2, rivet);
                    g.fill(rx + 1, y2 - 1, rx + 2, y2, dark);
                }
                g.fill(x + 2, y + 2, x + 5, y + 5, accent);
                g.fill(x2 - 5, y + 2, x2 - 2, y + 5, accent);
            }
            case RAINBOW -> {
                // the rim is the decoration
            }
            case SIMPLE -> {
            }
        }
    }

    /**
     * A 2px rim hugging the frame from outside, drawn one pixel at a time so it lines up exactly,
     * with only the outermost corner pixel cut. Colours flow around it.
     */
    private static void drawRainbowRim(GuiGraphics g, int x, int y, int x2, int y2, float alpha) {
        float shift = Util.getMillis() / 3000.0F;
        float perimeter = 2.0F * ((x2 - x) + (y2 - y)) + 16.0F;
        for (int px = x - 2; px < x2 + 2; px++) {
            float top = px - (x - 2);
            float bottom = (x2 - x + 4) + (y2 - y + 4) + (x2 + 1 - px);
            boolean end = px == x - 2 || px == x2 + 1;
            // outer row skips the corner pixel, inner row is full
            if (!end) g.fill(px, y - 2, px + 1, y - 1, rainbow(top, perimeter, shift, alpha));
            g.fill(px, y - 1, px + 1, y, rainbow(top, perimeter, shift, alpha));
            g.fill(px, y2, px + 1, y2 + 1, rainbow(bottom, perimeter, shift, alpha));
            if (!end) g.fill(px, y2 + 1, px + 1, y2 + 2, rainbow(bottom, perimeter, shift, alpha));
        }
        for (int py = y; py < y2; py++) {
            float right = (x2 - x + 4) + (py - y);
            float left = 2 * (x2 - x + 4) + (y2 - y) + (y2 - 1 - py);
            g.fill(x2, py, x2 + 2, py + 1, rainbow(right, perimeter, shift, alpha));
            g.fill(x - 2, py, x, py + 1, rainbow(left, perimeter, shift, alpha));
        }
    }

    private static int rainbow(float position, float perimeter, float shift, float alpha) {
        float hue = (position / perimeter + shift) % 1.0F;
        return fade(0xFF000000 | Mth.hsvToRgb(hue, 0.75F, 1.0F), alpha);
    }

    private static void drawSnowflake(GuiGraphics g, int cx, int cy, int c) {
        g.fill(cx - 2, cy, cx + 3, cy + 1, c);
        g.fill(cx, cy - 2, cx + 1, cy + 3, c);
        g.fill(cx - 1, cy - 1, cx, cy, c);
        g.fill(cx + 1, cy - 1, cx + 2, cy, c);
        g.fill(cx - 1, cy + 1, cx, cy + 2, c);
        g.fill(cx + 1, cy + 1, cx + 2, cy + 2, c);
    }

    private static void drawFlower(GuiGraphics g, int cx, int cy, int petal, int center) {
        g.fill(cx - 1, cy - 2, cx + 2, cy - 1, petal);
        g.fill(cx - 1, cy + 2, cx + 2, cy + 3, petal);
        g.fill(cx - 2, cy - 1, cx - 1, cy + 2, petal);
        g.fill(cx + 2, cy - 1, cx + 3, cy + 2, petal);
        g.fill(cx, cy, cx + 1, cy + 1, center);
    }

    private static void drawGem(GuiGraphics g, int x, int y, int gem, float alpha) {
        g.fill(x, y, x + 6, y + 6, fade(0xFF2A8A9A, alpha));
        g.fill(x + 1, y + 1, x + 5, y + 5, gem);
        g.fill(x + 1, y + 1, x + 3, y + 3, fade(0xFFFFFFFF, alpha));
    }

    private static void drawHealthBar(GuiGraphics g, PanelStyle st, Anim anim, int bx, int by, int bw, float alpha, long now) {
        float max = Math.max(1.0F, anim.info.maxHealth());
        int filled = Math.round(bw * Mth.clamp(anim.shownHealth / max, 0.0F, 1.0F));
        int ghost = Math.round(bw * Mth.clamp(anim.ghostHealth / max, 0.0F, 1.0F));

        // Rounded track
        int edge = fade(0xA0000010, alpha);
        g.fill(bx, by - 1, bx + bw, by, edge);
        g.fill(bx, by + 6, bx + bw, by + 7, edge);
        g.fill(bx - 1, by, bx, by + 6, edge);
        g.fill(bx + bw, by, bx + bw + 1, by + 6, edge);
        g.fill(bx, by, bx + bw, by + 6, fade(st.barBg, alpha));

        // Ghost of recent damage
        if (ghost > filled) g.fill(bx + filled, by, bx + ghost, by + 6, fade(st.barGhost, alpha * 0.85F));

        // Gradient fill with a highlight on top
        for (int px = 0; px < filled; px++) {
            float t = bw <= 1 ? 1.0F : (float) px / (bw - 1);
            g.fill(bx + px, by, bx + px + 1, by + 6, fade(lerp(st.barFrom, st.barTo, t), alpha));
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
            int scale = Math.max(1, (int) (18.0F / Math.max(0.5F, size)));
            int centerX = (x1 + x2) / 2;
            int feetY = y2 - 1 - Math.max(0, (int) ((18.0F - living.getBbHeight() * scale) / 2.0F));
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
