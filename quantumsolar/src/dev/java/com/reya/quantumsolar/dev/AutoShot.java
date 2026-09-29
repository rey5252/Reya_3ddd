package com.reya.quantumsolar.dev;

import java.util.ArrayList;
import java.util.List;

import com.reya.quantumsolar.PanelBlockEntity;
import com.reya.quantumsolar.QuantumSolar;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

/**
 * Dev-only: with QUANTUMSOLAR_AUTOSHOT=true the client makes a flat world and builds the blocks up as
 * the reference picture has them (two flights of steps meeting in a corner: the solar panels on the
 * left, the quantum generators on the right), photographs them, opens a panel's and a generator's
 * GUI, then quits.
 */
@Mod.EventBusSubscriber(modid = QuantumSolar.MODID, value = Dist.CLIENT)
public final class AutoShot {
    private static final boolean ON = "true".equals(System.getenv("QUANTUMSOLAR_AUTOSHOT"));
    private static final int BASE = -60;
    /** The picture's rows, nearest first: indices into Panels.ALL, from the outer end to the corner. */
    private static final int[][] LEFT = {{0, 1, 2, 3}, {4, 5, 6, 7, 8, 9}, {10, 11, 12, 13, 14, 15, 16, 17}, {18, 19, 20, 21, 22, 23, 24, 25}};
    private static final int[][] RIGHT = {{26, 27, 28, 29, 30}, {31, 32, 33, 34, 35}, {36, 37, 38, 39, 40, 41}, {42, 43, 44, 45, 46, 47}};
    private static final List<Step> STEPS = new ArrayList<>();
    private static int ticks, step, since;
    private static boolean worldAsked;

    private record Step(int delay, Runnable action) {
    }

    static {
        STEPS.add(new Step(80, AutoShot::build));
        STEPS.add(new Step(60, () -> eye(-9.0D, 7.5D, 11.0D, 0.0D, 1.5D, -1.0D)));
        STEPS.add(new Step(80, () -> shot("blocks.png")));
        STEPS.add(new Step(10, () -> eye(-5.5D, 3.2D, 4.5D, -3.0D, 0.5D, -1.0D)));
        STEPS.add(new Step(60, () -> shot("blocks_left.png")));
        STEPS.add(new Step(10, () -> eye(-3.0D, 3.8D, 9.0D, 1.5D, 1.0D, 3.0D)));
        STEPS.add(new Step(60, () -> shot("blocks_right.png")));
        STEPS.add(new Step(10, () -> guiScale(3)));
        STEPS.add(new Step(10, () -> open(new BlockPos(-3, BASE + 2, -2))));
        STEPS.add(new Step(40, () -> shot("gui_solar.png")));
        STEPS.add(new Step(10, () -> Minecraft.getInstance().setScreen(null)));
        STEPS.add(new Step(10, () -> open(new BlockPos(3, BASE + 3, 3))));
        STEPS.add(new Step(40, () -> shot("gui_quantum.png")));
        STEPS.add(new Step(40, () -> Minecraft.getInstance().stop()));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        if (!worldAsked) {
            if (mc.level == null && mc.screen != null && ticks > 100) {
                createWorld(mc);
                worldAsked = true;
            }
            return;
        }
        if (mc.player == null || mc.getSingleplayerServer() == null) {
            since = ticks;
            return;
        }
        if (step < STEPS.size() && ticks - since >= STEPS.get(step).delay()) {
            STEPS.get(step).action().run();
            step++;
            since = ticks;
        }
    }

    private static void createWorld(Minecraft mc) {
        mc.getTutorial().setStep(TutorialSteps.NONE);
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings("autoshot", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("autoshot", settings, new WorldOptions(1L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    private static MinecraftServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    /**
     * The left flight runs out to the west from the corner and climbs to the north, the right one
     * runs out to the south and climbs to the east; each step is one block up and one back, so
     * every block's top and front show, as in the picture.
     */
    private static void build() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setDayTime(6000L);
            for (int r = 0; r < LEFT.length; r++) {
                int[] row = LEFT[r];
                for (int i = 0; i < row.length; i++) {
                    BlockPos pos = new BlockPos(-row.length + i, BASE + r, -r);
                    for (int y = BASE; y <= BASE + r; y++) place(level, pos.atY(y), row[i]);
                }
            }
            for (int r = 0; r < RIGHT.length; r++) {
                int[] row = RIGHT[r];
                for (int i = 0; i < row.length; i++) {
                    BlockPos pos = new BlockPos(r, BASE + r, i);
                    for (int y = BASE; y <= BASE + r; y++) place(level, pos.atY(y), row[i]);
                }
            }
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
        });
        mc.getTutorial().setStep(TutorialSteps.NONE);
        mc.options.hideGui = true;
        mc.options.bobView().set(false);
        mc.options.fov().set(60);
    }

    private static void place(ServerLevel level, BlockPos pos, int index) {
        level.setBlock(pos, QuantumSolar.PANEL_BLOCKS.get(index).get().defaultBlockState(), 3);
    }

    /** Spectator camera at x, y, z (blocks from the corner, y from the floor) looking at tx, ty, tz. */
    private static void eye(double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.setGameMode(GameType.SPECTATOR);
            p.connection.teleport(x, BASE + y - 1.62D, z, yaw, pitch);
        });
    }

    private static void open(BlockPos pos) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            if (server.overworld().getBlockEntity(pos) instanceof PanelBlockEntity be) NetworkHooks.openScreen(p, be, pos);
        });
        Minecraft.getInstance().options.hideGui = false;
    }

    private static void guiScale(int scale) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.guiScale().set(scale);
        mc.resizeDisplay();
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {
        });
    }
}
