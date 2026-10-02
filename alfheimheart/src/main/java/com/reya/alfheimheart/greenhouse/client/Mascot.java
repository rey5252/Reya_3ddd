package com.reya.alfheimheart.greenhouse.client;

import java.util.List;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reya.alfheimheart.AlfheimHeart;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;

/**
 * The garden keeper's portrait: the face of a little elf mage with white twin tails, calm green eyes
 * and red drop earrings, in a small navy frame with a green edge and curling vines (textures/gui/mascot.png,
 * drawn by tools/gen_mascot.py) hanging beside the greenhouse GUI's top-left corner. It slides in when
 * the GUI opens and she smiles hello; she breathes and blinks, smiles while the mouse is over her, beams
 * when a cycle is lucky, the gem on the frame twinkles whenever a cycle's mana comes in, and when clicked
 * she says what the greenhouse needs or gives a tip in a speech bubble under the portrait.
 * <p>
 * Sheet: the portrait twice (breathing out and in, her head a pixel up), then the face parts drawn over
 * it: eyes (open, half, shut, happy, starry) and mouths (smile, open, happy).
 */
final class Mascot {
    static final ResourceLocation TEX = new ResourceLocation(AlfheimHeart.MODID, "textures/gui/mascot.png");
    static final int TEX_W = 256, TEX_H = 128;
    static final int WIDTH = 88, HEIGHT = 74;
    /** Where the portrait hangs, from the GUI's corner: beside the panel's top-left corner. */
    static final int X = -104, Y = 4;
    /** How far it reaches over the panel's frame (less than nothing: it keeps clear of the frame's vines). */
    static final int OVERLAP = X + WIDTH;
    /** The face parts: where they go on the portrait, and where they are on the sheet. */
    static final int EYES_X = 24, EYES_Y = 38, EYES_W = 40, EYES_H = 18, EYES_U = 176, EYES_V = 0;
    static final int MOUTH_X = 39, MOUTH_Y = 57, MOUTH_W = 10, MOUTH_H = 6, MOUTH_U = 216, MOUTH_V = 0;
    /** The mana gem on the frame's bottom, the gold lights on two of its corners and her earring, for their twinkles. */
    static final int GEM_X = 44, GEM_Y = 71, LIGHT_A_X = 84, LIGHT_A_Y = 3, LIGHT_B_X = 3, LIGHT_B_Y = 70, EARRING_X = 18, EARRING_Y = 56;

    private static final int EYES_OPEN = 0, EYES_HALF = 1, EYES_SHUT = 2, EYES_HAPPY = 3, EYES_STARS = 4;
    private static final int MOUTH_SMILE = 0, MOUTH_OPEN = 1, MOUTH_HAPPY = 2;
    private static final long ENTER_FROM = 380L, ENTER_MS = 420L;

    private long nextBlink = Util.getMillis() + 2600L, blinkUntil;
    private long happyUntil, speakUntil, gemUntil;
    private Component speech;
    private long speechAt;
    /** Where her bubble was last drawn (screen coordinates; 0 wide while she is quiet). */
    private int bubbleX, bubbleY, bubbleW, bubbleH;

    boolean contains(int mx, int my) {
        return mx >= X && mx < X + WIDTH && my >= Y && my < Y + HEIGHT;
    }

    /** A cycle's mana came in: the gem on the frame twinkles. */
    void pulse() {
        gemUntil = Math.max(gemUntil, Util.getMillis() + 420L);
    }

    /** Clicked: she smiles and says something. */
    void poke(Component text) {
        long now = Util.getMillis();
        say(text, now, 4200L);
        happyUntil = now + 1500L;
    }

    /** A lucky cycle: she beams and cheers. */
    void cheer(Component text) {
        long now = Util.getMillis();
        happyUntil = now + 2200L;
        gemUntil = now + 900L;
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
        // slides in from the left with a little overshoot, fading in
        int x = left + X + Math.round(-24.0F * (1.0F - backOut(enter))), y = top + Y;

        // breathing: the second portrait (her head a pixel up) for a moment every 1.8 s
        int frame = (t / 900L) % 2L == 1L ? 1 : 0;
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        if (enter < 1.0F) g.setColor(1.0F, 1.0F, 1.0F, Math.min(1.0F, enter * 2.5F));
        g.blit(TEX, x, y, frame * WIDTH, 0, WIDTH, HEIGHT, TEX_W, TEX_H);

        // her face: blinking now and then; smiling hello, while the mouse is over her, and when happy
        if (now >= nextBlink) {
            blinkUntil = now + 150L;
            nextBlink = now + 2200L + (now * 7919L % 2600L);
        }
        boolean greeting = t - (ENTER_FROM + ENTER_MS) < 1100L;
        boolean happy = now < happyUntil || hover || greeting;
        int eyes;
        if (now < blinkUntil) eyes = now > blinkUntil - 50L || now < blinkUntil - 100L ? EYES_HALF : EYES_SHUT;
        else if (now < happyUntil && (now / 260L) % 4L == 0L) eyes = EYES_STARS;
        else eyes = happy ? EYES_HAPPY : EYES_OPEN;
        int lift = frame;
        g.blit(TEX, x + EYES_X, y + EYES_Y - lift, EYES_U, EYES_V + eyes * EYES_H, EYES_W, EYES_H, TEX_W, TEX_H);
        int mouth = now < speakUntil && (now - speechAt) < 1600L ? ((now / 140L) % 2L == 0L ? MOUTH_OPEN : MOUTH_SMILE)
                : happy ? MOUTH_HAPPY : MOUTH_SMILE;
        g.blit(TEX, x + MOUTH_X, y + MOUTH_Y - lift, MOUTH_U, MOUTH_V + mouth * MOUTH_H, MOUTH_W, MOUTH_H, TEX_W, TEX_H);
        g.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        if (enter < 1.0F) return;

        // twinkles: the gem when mana comes in, the gold lights on the corners and her earring now and then
        if (now < gemUntil) {
            float a = Mth.sin((gemUntil - now) / 420.0F * Mth.PI);
            GreenhouseScreen.sparkle(g, x + GEM_X, y + GEM_Y, 0x7FF4FF, Math.abs(a));
        }
        long p = now % 2300L;
        if (p < 420L) GreenhouseScreen.sparkle(g, x + LIGHT_A_X, y + LIGHT_A_Y, 0xA6F6FF, Mth.sin(p / 420.0F * Mth.PI));
        long r = (now + 1500L) % 2900L;
        if (r < 420L) GreenhouseScreen.sparkle(g, x + LIGHT_B_X, y + LIGHT_B_Y, 0xA6F6FF, Mth.sin(r / 420.0F * Mth.PI));
        long q = (now + 1150L) % 3100L;
        if (q < 360L) GreenhouseScreen.sparkle(g, x + EARRING_X, y + EARRING_Y - lift, 0xFFB0A8, Mth.sin(q / 360.0F * Mth.PI) * 0.8F);
    }

    /**
     * Her speech bubble: a rounded parchment box with a dark outline under the portrait, its tail pointing
     * up at her, kept left of the panel.
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
        int anchorX = left + X + WIDTH / 2, anchorY = top + Y + HEIGHT;
        // centred under the portrait, but kept off the panel and the leaves on its frame
        int bx = Math.min(anchorX - bw / 2, left - 12 - bw);
        bx = Mth.clamp(bx, 2, Math.max(2, screenW - bw - 2));
        int by = anchorY + 6;
        bubbleX = bx - 1;
        bubbleY = by - 4;
        bubbleW = bw + 2;
        bubbleH = bh + 5;
        g.pose().pushPose();
        g.pose().translate(0.0F, 0.0F, 300.0F);
        int alpha = (int) (a * 255.0F) << 24;
        int outline = alpha | 0x4E3417, fill = alpha | 0xFFF9E0, shadow = alpha | 0xEAD092;
        // rounded box
        g.fill(bx + 1, by, bx + bw - 1, by + bh, outline);
        g.fill(bx, by + 1, bx + bw, by + bh - 1, outline);
        g.fill(bx + 1, by + 1, bx + bw - 1, by + bh - 1, fill);
        g.fill(bx + 1, by + bh - 2, bx + bw - 1, by + bh - 1, shadow);
        // the tail, pointing up at the portrait
        int tx = Mth.clamp(anchorX - 2, bx + 4, bx + bw - 6);
        g.fill(tx, by, tx + 4, by + 1, fill);
        g.fill(tx + 1, by - 1, tx + 3, by, fill);
        g.fill(tx - 1, by, tx, by + 1, outline);
        g.fill(tx + 4, by, tx + 5, by + 1, outline);
        g.fill(tx, by - 1, tx + 1, by, outline);
        g.fill(tx + 3, by - 1, tx + 4, by, outline);
        g.fill(tx + 1, by - 2, tx + 3, by - 1, outline);
        g.fill(tx + 2, by - 3, tx + 3, by - 2, outline);
        // a tiny leaf on the bubble's corner
        g.fill(bx + bw - 5, by - 1, bx + bw - 2, by, alpha | 0x4E9C35);
        g.fill(bx + bw - 4, by - 2, bx + bw - 2, by - 1, alpha | 0x7CCB47);
        int ty = by + 4;
        int textAlpha = Math.max(8, (int) (a * 255.0F)) << 24;
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, bx + 5, ty, textAlpha | 0x3A2612, false);
            ty += 10;
        }
        g.pose().popPose();
    }

    private static float backOut(float p) {
        float c1 = 1.7F, c3 = c1 + 1.0F, q = p - 1.0F;
        return 1.0F + c3 * q * q * q + c1 * q * q;
    }
}
