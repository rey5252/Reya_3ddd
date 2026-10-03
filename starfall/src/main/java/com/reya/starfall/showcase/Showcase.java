package com.reya.starfall.showcase;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.function.BooleanSupplier;

import com.mojang.logging.LogUtils;
import com.reya.starfall.Config;
import com.reya.starfall.Skill;
import com.reya.starfall.Starfall;
import com.reya.starfall.client.ClientStrikes;
import com.reya.starfall.client.Film;
import com.reya.starfall.client.RemoteAnimation;
import com.reya.starfall.client.SkillScreen;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Development-only recorder (left out of the mod jar): with {@code -Dstarfall.showcase=true} the client makes a
 * world, fires all three weapons and saves screenshots of the films and the strikes, then quits. CI runs it on a
 * virtual display so the screenshots can be looked at without a game.
 */
@Mod.EventBusSubscriber(modid = Starfall.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class Showcase {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean ENABLED = Boolean.getBoolean("starfall.showcase");
    private static final Deque<BooleanSupplier> SCRIPT = new ArrayDeque<>();
    private static boolean created, scripted;
    private static int ticks, shots;
    private static String pendingShot;
    private static long strikeStart = -1;
    private static int baseX, baseY, baseZ;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (ticks > 20 * 60 * 25) {
            LOGGER.error("[showcase] took too long, quitting");
            mc.stop();
            return;
        }
        if (!created) {
            if (mc.level == null && mc.getOverlay() == null && mc.screen != null && ticks > 60) createWorld(mc);
            return;
        }
        if (mc.player == null || mc.level == null) return;
        if (!scripted) {
            scripted = true;
            script(mc);
        }
        while (!SCRIPT.isEmpty() && pendingShot == null) {
            boolean done;
            try {
                done = SCRIPT.peekFirst().getAsBoolean();
            } catch (RuntimeException e) {
                LOGGER.error("[showcase] step failed", e);
                done = true;
            }
            if (!done) break;
            SCRIPT.pollFirst();
        }
        if (SCRIPT.isEmpty() && pendingShot == null) {
            LOGGER.info("[showcase] finished with {} screenshots", shots);
            mc.stop();
        }
    }

    /** Screenshots are taken right after a frame has been drawn, so the film overlay is in them. */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent event) {
        if (!ENABLED || event.phase != TickEvent.Phase.END || pendingShot == null) return;
        Minecraft mc = Minecraft.getInstance();
        String name = String.format(Locale.ROOT, "%02d_%s.png", ++shots, pendingShot);
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {
        });
        LOGGER.info("[showcase] screenshot {}", name);
        pendingShot = null;
    }

    private static void createWorld(Minecraft mc) {
        created = true;
        LOGGER.info("[showcase] creating world");
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_DOFIRETICK).set(false, null);
        rules.getRule(GameRules.RULE_SENDCOMMANDFEEDBACK).set(false, null);
        LevelSettings settings = new LevelSettings("Starfall Showcase", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                rules, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("starfall_showcase", settings, new WorldOptions(20261003L, true, false),
                WorldPresets::createNormalWorldDimensions);
    }

    // ------------------------------------------------------------------ the script

    private static void step(BooleanSupplier s) {
        SCRIPT.addLast(s);
    }

    private static void run(Runnable r) {
        step(() -> {
            r.run();
            return true;
        });
    }

    private static void waitTicks(int n) {
        int[] left = {n};
        step(() -> --left[0] <= 0);
    }

    private static void cmd(String command) {
        run(() -> {
            LOGGER.info("[showcase] /{}", command);
            Minecraft.getInstance().player.connection.sendCommand(command);
        });
    }

    private static void shot(String name) {
        run(() -> {
            Minecraft.getInstance().getToasts().clear();
            pendingShot = name;
        });
    }

    private static void fly() {
        run(() -> {
            Minecraft mc = Minecraft.getInstance();
            mc.player.getAbilities().flying = true;
            mc.player.onUpdateAbilities();
        });
    }

    private static void tp(int dx, int dy, int dz, float yaw, float pitch) {
        run(() -> {
            String c = String.format(Locale.ROOT, "tp @s %d %d %d %.1f %.1f", baseX + dx, baseY + dy, baseZ + dz, yaw, pitch);
            LOGGER.info("[showcase] /{}", c);
            Minecraft.getInstance().player.connection.sendCommand(c);
        });
        fly();
    }

    /** Teleports to (dx, dz) from the base, {@code up} blocks above the ground there (if it's loaded). */
    private static void tpGround(int dx, int dz, int up, float yaw, float pitch) {
        run(() -> {
            Minecraft mc = Minecraft.getInstance();
            int x = baseX + dx, z = baseZ + dz;
            int y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) + up;
            String c = String.format(Locale.ROOT, "tp @s %d %d %d %.1f %.1f", x, y, z, yaw, pitch);
            LOGGER.info("[showcase] /{}", c);
            mc.player.connection.sendCommand(c);
        });
        fly();
    }

    /** Waits until the client has the chunk at (dx, dz) from the base, so its ground height is known. */
    private static void awaitChunk(int dx, int dz) {
        int[] waited = {0};
        step(() -> {
            Minecraft mc = Minecraft.getInstance();
            int x = baseX + dx, z = baseZ + dz;
            boolean ready = mc.level.getChunkSource().hasChunk(x >> 4, z >> 4)
                    && mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) > mc.level.getMinBuildHeight();
            if (!ready && ++waited[0] % 100 == 0) LOGGER.info("[showcase] waiting for the chunk at {} {}", x, z);
            return ready || waited[0] > 1200;
        });
    }

    /** Fires a skill at ground level at (dx, dz) from the base, then waits for the strike to reach the client. */
    private static void cast(String skill, int dx, int dz) {
        awaitChunk(dx, dz);
        run(() -> {
            Minecraft mc = Minecraft.getInstance();
            int x = baseX + dx, z = baseZ + dz;
            int y = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z) - 1;
            String c = String.format(Locale.ROOT, "starfall cast %s %d %d %d", skill, x, y, z);
            LOGGER.info("[showcase] /{}", c);
            mc.player.connection.sendCommand(c);
        });
        awaitStrike();
    }

    /** Presses the remote in hand for real: the cover flips, the button goes down, and the server fires. */
    private static void pressRemote() {
        run(() -> {
            Minecraft mc = Minecraft.getInstance();
            LOGGER.info("[showcase] pressing the remote");
            mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        });
    }

    /** Waits for the next strike to reach the client, giving up after a while so the rest still runs. */
    private static void awaitStrike() {
        long[] before = {-2};
        int[] waited = {0};
        step(() -> {
            if (before[0] == -2) before[0] = ClientStrikes.latestStart();
            long now = ClientStrikes.latestStart();
            if (now >= 0 && now != before[0]) {
                strikeStart = now;
                return true;
            }
            if (++waited[0] > 300) {
                LOGGER.error("[showcase] the strike never came");
                strikeStart = Minecraft.getInstance().level.getGameTime();
                return true;
            }
            return false;
        });
    }

    /** Waits until the current strike is {@code t} ticks old. */
    private static void at(int t) {
        step(() -> Minecraft.getInstance().level.getGameTime() - strikeStart >= t);
    }

    private static void script(Minecraft mc) {
        baseX = mc.player.blockPosition().getX();
        baseZ = mc.player.blockPosition().getZ();
        baseY = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING, baseX, baseZ);
        LOGGER.info("[showcase] base at {} {} {}", baseX, baseY, baseZ);
        // smaller than the defaults so each strike fits in the view of a software-rendered client
        Config.RAILGUN_RADIUS.set(56);
        Config.GUNGNIR_RADIUS.set(72);
        Config.SEVEN_SPAN.set(320);
        Config.SEVEN_CRATER_SCALE.set(2.2D);
        Config.COOLDOWN.set(0);

        waitTicks(100);
        cmd("time set 6000");
        cmd("weather clear 1000000");
        cmd("give @s starfall:stellar_remote");
        run(() -> mc.player.getInventory().selected = 0);

        // ---- the remote itself: idle, the cover flipped by the thumb, the press, and from the front
        awaitChunk(0, 0);
        tpGround(0, 0, 0, 135.0F, 8.0F);
        waitTicks(80);
        shot("remote_idle");
        run(RemoteAnimation::startLocal);
        waitTicks(3);
        shot("remote_thumb_on_the_cover");
        waitTicks(2);
        shot("remote_cover_flipping");
        waitTicks(3);
        shot("remote_thumb_on_the_button");
        waitTicks(1);
        shot("remote_button_pressed");
        waitTicks(30);
        run(() -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
        waitTicks(10);
        shot("remote_third_person");
        run(RemoteAnimation::startLocal);
        waitTicks(7);
        shot("remote_third_person_cover_open");
        run(() -> mc.options.setCameraType(CameraType.FIRST_PERSON));
        waitTicks(60);

        // ---- the strike menu
        run(SkillScreen::open);
        waitTicks(14);
        shot("menu");
        run(() -> {
            if (mc.screen instanceof SkillScreen screen) screen.highlight(2);
        });
        waitTicks(6);
        shot("menu_seven_stars");
        run(() -> {
            if (mc.screen instanceof SkillScreen screen) screen.showDetails(1);
        });
        waitTicks(6);
        shot("menu_details");
        run(() -> mc.setScreen(null));
        waitTicks(20);

        // ---- SS-01 Railgun, fired by pressing the remote
        tp(0, 50, -130, 0.0F, 18.0F);
        waitTicks(160);
        shot("world_before");
        pressRemote();
        waitTicks(4);
        shot("ss01_press");
        awaitStrike();
        at(12);
        shot("ss01_film_ascent");
        at(38);
        shot("ss01_film_earth");
        at(62);
        shot("ss01_film_saturn");
        at(94);
        shot("ss01_film_galaxy");
        at(128);
        shot("ss01_film_railgun_charging");
        at(149);
        shot("ss01_film_fire");
        at(Skill.MARK + 34);
        shot("ss01_beam_impact_confirmed");
        at(Skill.MARK + 76);
        shot("ss01_beam_fading");
        at(Skill.MARK + 420);
        tp(0, 95, -110, 0.0F, 42.0F);
        waitTicks(80);
        shot("ss01_hole");
        tp(30, 70, -40, 20.0F, 62.0F);
        waitTicks(60);
        shot("ss01_hole_down");

        // ---- SS-03 Gungnir
        tp(700, 60, -160, 0.0F, 16.0F);
        waitTicks(260);
        cast("gungnir", 700, 0);
        at(10);
        shot("ss03_film_ascent");
        at(36);
        shot("ss03_film_jupiter");
        at(62);
        shot("ss03_film_accelerator");
        at(100);
        shot("ss03_film_seven_laps");
        at(118);
        shot("ss03_film_flung");
        at(150);
        shot("ss03_film_descent");
        run(Film::stop);
        at(156);
        shot("ss03_ember_marker");
        at(Skill.MARK + 5);
        shot("ss03_needle_falling");
        at(Skill.GUNGNIR_IMPACT + 12);
        shot("ss03_shock_ring");
        at(Skill.GUNGNIR_IMPACT + 34);
        shot("ss03_impact_confirmed");
        at(Skill.GUNGNIR_IMPACT + 420);
        tp(700, 100, -150, 0.0F, 34.0F);
        waitTicks(80);
        shot("ss03_crater_and_needle");
        cmd("time set 13800");
        awaitChunk(700, -36);
        tpGround(700, -36, 3, 0.0F, -12.0F);
        waitTicks(80);
        shot("ss03_needle_at_night");
        cmd("time set 6000");

        // ---- SS-04 Seven Stars, at night
        cmd("time set 14500");
        tp(0, 150, 760, 0.0F, 48.0F);
        waitTicks(300);
        cast("seven_stars", 0, 880);
        at(66);
        shot("ss04_film_stars_wake");
        at(112);
        shot("ss04_film_stellar_array");
        at(142);
        shot("ss04_film_fire");
        run(Film::stop);
        at(150);
        shot("ss04_projection");
        at(Skill.starImpact(0) - 6);
        shot("ss04_star_falling");
        at(Skill.starImpact(0) + 2);
        shot("ss04_first_impact");
        at(Skill.starImpact(4) + 8);
        shot("ss04_craters");
        at(Skill.lineStart(3) + 6);
        shot("ss04_lines_ignite");
        at(Skill.FLARE + 6);
        shot("ss04_flare");
        at(Skill.FLARE + 400);
        tp(0, 120, 800, 0.0F, 62.0F);
        waitTicks(120);
        shot("ss04_burned_into_the_land");
        cmd("time set 6000");
        waitTicks(40);
        shot("ss04_burned_into_the_land_by_day");
    }

    private Showcase() {
    }
}
