package com.reya.goldenquarry.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.Mth;

/**
 * Purple tentacles round the vacuum chest's GUI, drawn pixel by pixel: they come out from behind the
 * frame, thick at the root and thin at the tip, waves run along them into the chest, they are drawn
 * back in and reach out again, and the light suckers on their side slide in. Drawn before the GUI,
 * so the frame covers their roots.
 */
final class Tentacles {
    /** Root (GUI pixels), direction out, length, phase. */
    private record Tentacle(float x, float y, float dx, float dy, float length, float phase) {
    }

    private static final int OUTLINE = 0x10041E;
    private static final int[] BODY = {0x240846, 0x351068, 0x4C1A8E, 0x6828B4, 0x8A40D8, 0xAE62F2, 0xD08CFF};
    private static final int SUCKER = 0xE8B8FF;
    private final Tentacle[] tentacles;

    /** Tentacles for a GUI of this width: at the top corners, down the sides and at the bottom corners. */
    Tentacles(int width, int frameBottom) {
        float r = width - 1;
        tentacles = new Tentacle[]{
                new Tentacle(5, 14, -0.8F, -0.6F, 30, 0.0F),
                new Tentacle(32, 12, -0.3F, -0.95F, 24, 1.7F),
                new Tentacle(4, 46, -1.0F, -0.1F, 22, 3.1F),
                new Tentacle(r - 5, 14, 0.8F, -0.6F, 30, 2.3F),
                new Tentacle(r - 32, 12, 0.3F, -0.95F, 24, 4.2F),
                new Tentacle(r - 4, 46, 1.0F, -0.1F, 22, 0.9F),
                new Tentacle(4, 100, -1.0F, 0.2F, 26, 5.0F),
                new Tentacle(r - 4, 96, 1.0F, 0.2F, 26, 1.2F),
                new Tentacle(8, 152, -0.85F, 0.55F, 28, 3.6F),
                new Tentacle(18, frameBottom + 12, -0.45F, 0.9F, 22, 0.4F),
                new Tentacle(r - 8, 152, 0.85F, 0.55F, 28, 2.8F),
                new Tentacle(r - 18, frameBottom + 12, 0.45F, 0.9F, 22, 5.6F)};
    }

    private static int width(float f) {
        return f < 0.22F ? 4 : f < 0.5F ? 3 : f < 0.8F ? 2 : 1;
    }

    /** The point s pixels out along the tentacle: {x, y}. */
    private static float[] point(Tentacle tc, float len, float t, float s) {
        float f = s / len;
        float wave = Mth.sin(s * 0.34F + t * 4.2F + tc.phase) * 3.4F * (float) Math.pow(f, 0.8D);
        return new float[]{tc.x + tc.dx * s - tc.dy * wave, tc.y + tc.dy * s + tc.dx * wave};
    }

    void draw(GuiGraphics g, int left, int top) {
        float t = Util.getMillis() / 1000.0F;
        for (Tentacle tc : tentacles) {
            float len = tc.length * (0.6F + 0.4F * (0.5F + 0.5F * Mth.sin(t * 1.3F + tc.phase)));
            // the dark outline first, then the body over it
            for (int pass = 0; pass < 2; pass++) {
                for (float s = 0.0F; s <= len; s += 0.5F) {
                    float f = s / len;
                    float[] p = point(tc, len, t, s);
                    int px = left + Math.round(p[0]), py = top + Math.round(p[1]);
                    int w = width(f);
                    int alpha = f < 0.85F ? 255 : (int) (255 * (1.0F - (f - 0.85F) / 0.15F));
                    if (alpha <= 0) continue;
                    int x0 = px - w / 2, y0 = py - w / 2;
                    if (pass == 0) {
                        g.fill(x0 - 1, y0 - 1, x0 + w + 1, y0 + w + 1, alpha << 24 | OUTLINE);
                    } else {
                        g.fill(x0, y0, x0 + w, y0 + w, alpha << 24 | BODY[Math.min(BODY.length - 1, (int) (f * BODY.length))]);
                    }
                }
            }
            // the suckers, sliding in along one side
            float shift = (t * 9.0F + tc.phase * 3.0F) % 4.0F;
            for (float s = 4.0F - shift; s <= len * 0.8F; s += 4.0F) {
                if (s <= 1.0F) continue;
                int w = width(s / len);
                if (w < 2) continue;
                float[] p = point(tc, len, t, s);
                float off = w / 2.0F - 0.5F;
                int px = left + Math.round(p[0] - tc.dy * off), py = top + Math.round(p[1] + tc.dx * off);
                g.fill(px, py, px + 1, py + 1, 0xC8000000 | SUCKER);
            }
        }
    }
}
