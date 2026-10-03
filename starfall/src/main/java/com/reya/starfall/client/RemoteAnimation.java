package com.reya.starfall.client;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;

/**
 * The remote's press, as a timeline in ticks: the hand comes up, the thumb flips the cover open on its hinge,
 * the button goes down (the weapon fires as it bottoms out, at the same tick as on the server), the thumb
 * comes away, and after a while the cover swings shut again.
 */
public final class RemoteAnimation {
    /** When the button bottoms out; matches the server's delay before firing. */
    public static final int FIRE = 9;
    public static final float COVER_OPEN = -105.0F;
    private static final float LENGTH = 70.0F;
    private static final Map<Integer, Float> STARTS = new HashMap<>();

    /** The local player pressed their remote. */
    public static void startLocal() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) startFor(mc.player.getId());
    }

    /** Someone pressed their remote (the local player, or another player the server told us about). */
    public static void startFor(int entityId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Float running = STARTS.get(entityId);
        float now = mc.level.getGameTime() + mc.getFrameTime();
        // the server echoes our own press to nobody but others; still, never restart a press that is under way
        if (running != null && now - running < FIRE + 4) return;
        STARTS.put(entityId, now);
    }

    /** Ticks since this entity pressed its remote, or -1 if it isn't pressing one now. */
    public static float age(int entityId, float partial) {
        Minecraft mc = Minecraft.getInstance();
        Float start = STARTS.get(entityId);
        if (start == null || mc.level == null) return -1.0F;
        float age = mc.level.getGameTime() + partial - start;
        if (age < 0.0F || age > LENGTH) {
            if (age > LENGTH) STARTS.remove(entityId);
            return -1.0F;
        }
        return age;
    }

    static void clear() {
        STARTS.clear();
    }

    /** How far the hand has lifted the remote towards the eyes, 0..1. */
    static float raise(float t) {
        if (t < 0.0F) return 0.0F;
        if (t < 4.0F) return smooth(t / 4.0F);
        if (t < 14.0F) return 1.0F;
        if (t < 22.0F) return 1.0F - smooth((t - 14.0F) / 8.0F);
        return 0.0F;
    }

    /** The cover's angle about its hinge, in degrees (0 closed, {@link #COVER_OPEN} open). */
    static float cover(float t) {
        if (t < 2.0F) return 0.0F;
        if (t < 5.0F) return COVER_OPEN * backOut((t - 2.0F) / 3.0F);
        if (t < 50.0F) return COVER_OPEN;
        if (t < 58.0F) return COVER_OPEN * (1.0F - smooth((t - 50.0F) / 8.0F));
        return 0.0F;
    }

    /** How far the button is pushed in, in model pixels. */
    static float press(float t) {
        float depth = 0.42F;
        if (t < 7.6F) return 0.0F;
        if (t < 8.8F) return depth * smooth((t - 7.6F) / 1.2F);
        if (t < 10.2F) return depth;
        if (t < 11.4F) return depth * (1.0F - smooth((t - 10.2F) / 1.2F));
        return 0.0F;
    }

    /** Extra brightness of the button's glow: it flares when it bottoms out. */
    static float flare(float t) {
        if (t < 8.4F || t > 20.0F) return 0.0F;
        return t < 9.2F ? (t - 8.4F) / 0.8F : 1.0F - (t - 9.2F) / 10.8F;
    }

    static float smooth(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        return x * x * (3.0F - 2.0F * x);
    }

    /** Ease out with a little overshoot, so the cover snaps open. */
    static float backOut(float x) {
        x = Mth.clamp(x, 0.0F, 1.0F);
        float c1 = 1.70158F, c3 = c1 + 1.0F;
        return 1.0F + c3 * (float) Math.pow(x - 1.0F, 3) + c1 * (float) Math.pow(x - 1.0F, 2);
    }

    private RemoteAnimation() {
    }
}
