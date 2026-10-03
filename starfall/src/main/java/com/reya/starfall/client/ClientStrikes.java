package com.reya.starfall.client;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.reya.starfall.network.StrikePacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

/** The strikes this client is showing. */
public final class ClientStrikes {
    private static final List<ClientStrike> STRIKES = new ArrayList<>();

    public static void add(StrikePacket packet) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ClientStrike strike = new ClientStrike(packet);
        STRIKES.removeIf(s -> s.id == strike.id);
        STRIKES.add(strike);
        if (strike.caster) {
            // with films off only the impact report shows
            Film.play(strike.skill, strike.start, strike.target, strike.radius);
            if (!ClientConfig.films()) Film.stop();
        }
    }

    /** When the newest strike started (level game time), or -1: used by the showcase recorder. */
    public static long latestStart() {
        return STRIKES.isEmpty() ? -1L : STRIKES.get(STRIKES.size() - 1).start;
    }

    static void tick() {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) {
            STRIKES.clear();
            return;
        }
        Vec3 listener = mc.gameRenderer.getMainCamera().getPosition();
        for (Iterator<ClientStrike> it = STRIKES.iterator(); it.hasNext(); ) {
            ClientStrike s = it.next();
            if (s.expired(level.getGameTime())) {
                it.remove();
                continue;
            }
            s.tick(level, listener);
        }
    }

    static void clear() {
        STRIKES.clear();
        Film.clear();
    }

    private static float age(ClientStrike s, ClientLevel level, float partial) {
        return level.getGameTime() - s.start + partial;
    }

    static void render(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || STRIKES.isEmpty()) return;
        float partial = event.getPartialTick();
        Camera camera = event.getCamera();
        Matrix4f mat = event.getPoseStack().last().pose();
        double far = mc.gameRenderer.getRenderDistance() * 3.5D;

        Fx solid = Fx.begin(mat, camera, far, false, false);
        for (ClientStrike s : STRIKES) s.renderSolid(solid, age(s, level, partial), level);
        solid.end();

        Fx glow = Fx.begin(mat, camera, far, true, false);
        for (ClientStrike s : STRIKES) s.renderGlow(glow, age(s, level, partial), level);
        glow.end();

        Fx xray = Fx.begin(mat, camera, far, true, true);
        for (ClientStrike s : STRIKES) s.renderXray(xray, age(s, level, partial));
        xray.end();
    }

    /** The strongest flash on screen right now, as ARGB (0 for none). */
    static int flash(float partial) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        float strength = ClientConfig.flash();
        if (level == null || STRIKES.isEmpty() || strength <= 0.0F) return 0;
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        boolean strobe = strength >= 0.5F;
        int best = 0;
        for (ClientStrike s : STRIKES) {
            int f = s.flash(age(s, level, partial), eye, strobe);
            if (((f >>> 24) & 0xFF) > ((best >>> 24) & 0xFF)) best = f;
        }
        return Fx.scaleAlpha(best, strength);
    }

    /** The most urgent evacuation warning for where the camera is, or null: the strike's line and its seconds left. */
    static ClientStrike.Warning warning(float partial) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || STRIKES.isEmpty()) return null;
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        ClientStrike.Warning best = null;
        for (ClientStrike s : STRIKES) {
            ClientStrike.Warning w = s.warning(age(s, level, partial), eye);
            if (w != null && (best == null || w.seconds() < best.seconds())) best = w;
        }
        return best;
    }

    /** Total camera shake right now, in degrees. */
    static float shake(float partial) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null || STRIKES.isEmpty() || !ClientConfig.shake()) return 0.0F;
        Vec3 eye = mc.gameRenderer.getMainCamera().getPosition();
        float total = 0.0F;
        for (ClientStrike s : STRIKES) total += s.shake(age(s, level, partial), eye);
        return Math.min(total, 4.0F);
    }

    private ClientStrikes() {
    }
}
