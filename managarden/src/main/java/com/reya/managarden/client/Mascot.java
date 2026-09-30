package com.reya.managarden.client;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reya.managarden.ManaGarden;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The garden keeper: a little elf mage with long white twin tails, calm green eyes and red drop
 * earrings, in a white coat and capelet trimmed with gold (textures/gui/mascot.png, drawn by
 * tools/gen_mascot.py), standing at the greenhouse GUI's left edge with her staff in one hand while the
 * Heart of the Greenhouse floats over her other palm. She hops in when the GUI opens and waves,
 * breathes, blinks, the heart bobs and the staff's orb twinkles; the heart glows whenever a cycle's mana
 * comes in, she raises it when a cycle is lucky, and when poked she says what the greenhouse needs or
 * gives a tip in a speech bubble.
 * <p>
 * Sheet: two body frames (breathing out and in, without her free arm), then the face parts drawn over
 * the body: eyes (open, half, shut, happy, starry) and mouths (smile, open, happy), then her free arm
 * (palm up, raised, two waving frames) and the heart (and the heart glowing).
 */
final class Mascot {
    static final ResourceLocation TEX = new ResourceLocation(ManaGarden.MODID, "textures/gui/mascot.png");
    static final int TEX_W = 256, TEX_H = 256;
    static final int WIDTH = 96, HEIGHT = 136;
    /** Where she stands, from the GUI's corner: her feet on the machine panel's bottom line. */
    static final int X = -92, Y = 146 - HEIGHT;
    /** How far her twin tail reaches over the panel's frame. */
    static final int OVERLAP = X + WIDTH;
    /** The face parts: where they go on the body, and where they are on the sheet. */
    static final int EYES_X = 28, EYES_Y = 36, EYES_W = 40, EYES_H = 18, EYES_U = 192, EYES_V = 0;
    static final int MOUTH_X = 43, MOUTH_Y = 55, MOUTH_W = 10, MOUTH_H = 6, MOUTH_U = 232, MOUTH_V = 0;
    /** Her free arm: drawn over the body at ARM_X, ARM_Y; the poses side by side on the sheet. */
    static final int ARM_X = 56, ARM_Y = 48, ARM_W = 36, ARM_H = 48, ARM_U = 0, ARM_V = 136;
    private static final int POSE_REST = 0, POSE_RAISE = 1, POSE_WAVE = 2;
    /** The floating heart on the sheet (the glowing one beside it), and where its middle is in each pose. */
    static final int HEART_U = 144, HEART_V = 136, HEART_W = 20, HEART_H = 18;
    static final int[][] HEART_AT = {{70, 74}, {73, 67}, {73, 82}, {73, 82}};
    /** The staff's orb, for its twinkle. */
    static final int ORB_X = 22, ORB_Y = 14;

    private static final int EYES_OPEN = 0, EYES_HALF = 1, EYES_SHUT = 2, EYES_HAPPY = 3, EYES_STARS = 4;
    private static final int MOUTH_SMILE = 0, MOUTH_OPEN = 1, MOUTH_HAPPY = 2;
    private static final long ENTER_FROM = 380L, ENTER_MS = 520L;

    private long nextBlink = Util.getMillis() + 2600L, blinkUntil;
    private long happyUntil, waveUntil, speakUntil, raiseUntil, glowUntil;
    private Component speech;
    private long speechAt;
    /** Where her bubble was last drawn (screen coordinates; 0 wide while she is quiet). */
    private int bubbleX, bubbleY, bubbleW, bubbleH;

    boolean contains(int mx, int my) {
        return mx >= X + 14 && mx < X + WIDTH - 14 && my >= Y + 4 && my < Y + HEIGHT;
    }

    /** A cycle's mana came in: the heart glows for a moment. */
    void pulse() {
        glowUntil = Math.max(glowUntil, Util.getMillis() + 380L);
    }

    /** Poked: she waves and says something. */
    void poke(Component text) {
        long now = Util.getMillis();
        say(text, now, 4200L);
        happyUntil = now + 1500L;
        waveUntil = now + 1400L;
    }

    /** A lucky cycle: she beams and cheers. */
    void cheer(Component text) {
        long now = Util.getMillis();
        happyUntil = now + 2200L;
        raiseUntil = now + 1300L;
        glowUntil = now + 1300L;
        if (speakUntil < now) say(text, now, 2200L);
    }

    /** Whether the point is on her speech bubble. */
    boolean bubbleContains(double mx, double my) {
        return bubbleW > 0 && Util.getMillis() < speakUntil
                && mx >= bubbleX && mx < bubbleX + bubbleW && my >= bubbleY && my < bubbleY + bubbleH;
    }

    /** Clicked away: the bubble fades at once. */
    void hush() {
        long now = Util.getMillis();
        if (speakUntil > now + 250L) speakUntil = now + 250L;
    }

    private void say(Component text, long now, long ms) {
        speech = text;
        speechAt = now;
        speakUntil = now + ms;
    }

    void draw(GuiGraphics g, int left, int top, long t, boolean hover) {
        long now = Util.getMillis();
        if (t < ENTER_FROM) return;
        float enter = Math.min(1.0F, (t - ENTER_FROM) / (float) ENTER_MS);
        // hops in from the left: slides with a little overshoot and a hop
        float slide = 1.0F - backOut(enter);
        int ox = Math.round(-36.0F * slide);
        int hop = enter < 1.0F ? Math.round(-Math.abs(Mth.sin(enter * Mth.PI * 2.0F)) * 6.0F * (1.0F - enter)) : 0;
        int x = left + X + ox, y = top + Y + hop;

        // breathing: the second body frame (shoulders and hair a pixel up) for a moment every 1.8 s
        int body = (t / 900L) % 2L == 1L ? 1 : 0;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (enter < 1.0F) g.setColor(1.0F, 1.0F, 1.0F, Math.min(1.0F, enter * 2.5F));
        g.blit(TEX, x, y, body * WIDTH, 0, WIDTH, HEIGHT, TEX_W, TEX_H);

        // face
        if (now >= nextBlink) {
            blinkUntil = now + 150L;
            nextBlink = now + 2200L + (now * 7919L % 2600L);
        }
        boolean happy = now < happyUntil || hover;
        int eyes;
        if (now < blinkUntil) eyes = now > blinkUntil - 50L || now < blinkUntil - 100L ? EYES_HALF : EYES_SHUT;
        else if (now < happyUntil && (now / 260L) % 4L == 0L) eyes = EYES_STARS;
        else eyes = happy ? EYES_HAPPY : EYES_OPEN;
        // on the breathing frame her head is a pixel higher, and her face with it
        int lift = body == 1 ? 1 : 0;
        g.blit(TEX, x + EYES_X, y + EYES_Y - lift, EYES_U, EYES_V + eyes * EYES_H, EYES_W, EYES_H, TEX_W, TEX_H);
        int mouth = now < speakUntil && (now - speechAt) < 1600L ? ((now / 140L) % 2L == 0L ? MOUTH_OPEN : MOUTH_SMILE)
                : happy ? MOUTH_HAPPY : MOUTH_SMILE;
        g.blit(TEX, x + MOUTH_X, y + MOUTH_Y - lift, MOUTH_U, MOUTH_V + mouth * MOUTH_H, MOUTH_W, MOUTH_H, TEX_W, TEX_H);

        // her free arm: palm up, raised when a cycle is lucky, or waving (two frames swinging) when she
        // greets or is poked
        long since = t - (ENTER_FROM + ENTER_MS);
        boolean waving = now < waveUntil || since < 1100L;
        int pose = waving ? POSE_WAVE + (int) ((now / 170L) % 2L) : now < raiseUntil ? POSE_RAISE : POSE_REST;
        g.blit(TEX, x + ARM_X, y + ARM_Y - lift, ARM_U + pose * ARM_W, ARM_V, ARM_W, ARM_H, TEX_W, TEX_H);

        // the heart floats over her palm, bobbing, and glows when mana comes in
        int bob = Math.round(Mth.sin(now / 420.0F) * 1.5F);
        boolean glow = now < glowUntil;
        int hx = x + HEART_AT[pose][0] - HEART_W / 2, hy = y + HEART_AT[pose][1] - HEART_H / 2 - lift + bob;
        g.blit(TEX, hx, hy, HEART_U + (glow ? HEART_W : 0), HEART_V, HEART_W, HEART_H, TEX_W, TEX_H);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);

        // the staff's orb twinkles now and then, and a mote of mana drifts up from the heart
        long p = now % 1900L;
        if (p < 420L && enter >= 1.0F) {
            float a = Mth.sin(p / 420.0F * Mth.PI);
            GreenhouseScreen.sparkle(g, x + ORB_X - 2, y + ORB_Y - 2, 0xFFB0A8, a);
        }
        long q = (now + 950L) % 2300L;
        if (q < 520L && enter >= 1.0F) {
            float a = Mth.sin(q / 520.0F * Mth.PI) * 0.8F;
            int d = (int) (q / 65L);
            GreenhouseScreen.sparkle(g, hx + HEART_W / 2 + 6 + d / 2, hy - 2 - d, 0x7FF4FF, a);
        }
    }

    /**
     * Her speech bubble: a rounded cream box with a dark outline and a tail pointing at her head,
     * above her and left of the panel if there is room, else beside her head over the panel.
     */
    void drawSpeech(GuiGraphics g, Font font, int left, int top, int screenW) {
        long now = Util.getMillis();
        bubbleW = 0;
        if (speech == null || now >= speakUntil) return;
        float a = Math.min(1.0F, Math.min((now - speechAt) / 120.0F, (speakUntil - now) / 250.0F));
        if (a <= 0.0F) return;
        List<FormattedCharSequence> lines = font.split(speech, 118);
        int w = 0;
        for (FormattedCharSequence l : lines) w = Math.max(w, font.width(l));
        int bw = w + 10, bh = lines.size() * 10 + 7;
        int headX = left + X + WIDTH / 2 - 2, headY = top + Y + 4;
        // centred over her head, but kept off the panel and the leaves on its frame
        int bx = Math.min(headX - bw / 2, left - 9 - bw);
        bx = Mth.clamp(bx, 2, Math.max(2, screenW - bw - 2));
        int by = headY - bh - 5;
        boolean above = by >= 1;
        if (!above) {
            bx = left + X + WIDTH - 12;
            by = Math.max(2, headY + 2);
        }
        bubbleX = bx - 1;
        bubbleY = by - 2;
        bubbleW = bw + 2;
        bubbleH = bh + 5;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        int alpha = (int) (a * 255.0F) << 24;
        int outline = alpha | 0x2A1A12, fill = alpha | 0xFFF9EC, shadow = alpha | 0xE4D8BE;
        // rounded box
        g.fill(bx + 1, by, bx + bw - 1, by + bh, outline);
        g.fill(bx, by + 1, bx + bw, by + bh - 1, outline);
        g.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, fill);
        g.fill(bx + 1, by + bh - 2, bx + bw - 1, by + bh - 1, shadow);
        if (above) {
            int tx = Mth.clamp(headX, bx + 4, bx + bw - 6);
            g.fill(tx, by + bh - 1, tx + 4, by + bh, fill);
            g.fill(tx + 1, by + bh, tx + 3, by + bh + 1, fill);
            g.fill(tx + 1, by + bh + 1, tx + 2, by + bh + 3, outline);
            g.fill(tx - 1, by + bh - 1, tx, by + bh, outline);
            g.fill(tx + 4, by + bh - 1, tx + 5, by + bh, outline);
            g.fill(tx, by + bh, tx + 1, by + bh + 1, outline);
            g.fill(tx + 3, by + bh, tx + 4, by + bh + 1, outline);
            g.fill(tx + 2, by + bh + 1, tx + 3, by + bh + 2, outline);
        } else {
            g.fill(bx - 2, by + 5, bx + 1, by + 7, fill);
            g.fill(bx - 3, by + 4, bx - 1, by + 5, outline);
            g.fill(bx - 3, by + 7, bx - 1, by + 8, outline);
            g.fill(bx - 4, by + 5, bx - 3, by + 7, outline);
        }
        // a tiny leaf on the bubble's corner
        g.fill(bx + bw - 5, by - 1, bx + bw - 2, by, alpha | 0x4E9C35);
        g.fill(bx + bw - 4, by - 2, bx + bw - 2, by - 1, alpha | 0x7CCB47);
        int ty = by + 4;
        int textAlpha = Math.max(8, (int) (a * 255.0F)) << 24;
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, bx + 5, ty, textAlpha | 0x3A2A22, false);
            ty += 10;
        }
        g.pose().popPose();
    }

    private static float backOut(float p) {
        float c1 = 1.7F, c3 = c1 + 1.0F, q = p - 1.0F;
        return 1.0F + c3 * q * q * q + c1 * q * q;
    }
}
