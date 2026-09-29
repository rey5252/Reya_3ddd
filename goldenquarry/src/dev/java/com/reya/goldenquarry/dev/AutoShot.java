package com.reya.goldenquarry.dev;

import java.util.ArrayList;
import java.util.List;

import com.reya.goldenquarry.GoldenQuarry;
import com.reya.goldenquarry.QuarryBlock;
import com.reya.goldenquarry.QuarryBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

/**
 * Dev-only: with GOLDENQUARRY_AUTOSHOT=true the client makes a flat world, places the quarry, takes
 * screenshots from all sides, from above and of the open GUI (upgrades in it, fortune ones in the
 * hotbar), then quits.
 */
@Mod.EventBusSubscriber(modid = GoldenQuarry.MODID, value = Dist.CLIENT)
public final class AutoShot {
    private static final boolean ON = "true".equals(System.getenv("GOLDENQUARRY_AUTOSHOT"));
    private static final BlockPos POS = new BlockPos(0, -60, 0);
    private static final List<Step> STEPS = new ArrayList<>();
    private static int ticks, step, since;
    private static boolean worldAsked;

    private record Step(int delay, Runnable action) {
    }

    static {
        STEPS.add(new Step(80, AutoShot::setUp));
        for (int deg = 0; deg < 360; deg += 45) {
            int d = deg;
            STEPS.add(new Step(40, () -> look(3.2D, d, 0.4D, 16.0F)));
            STEPS.add(new Step(30, () -> shot("quarry_side_" + String.format("%03d", d) + ".png")));
        }
        STEPS.add(new Step(20, () -> look(1.6D, 30, 4.0D, 66.0F)));
        STEPS.add(new Step(40, () -> shot("quarry_top.png")));
        STEPS.add(new Step(20, () -> look(3.0D, 30, 0.0D, 10.0F)));
        STEPS.add(new Step(30, AutoShot::openGui));
        STEPS.add(new Step(50, AutoShot::mouseAway));
        STEPS.add(new Step(20, () -> shot("quarry_gui.png")));
        // let it run (redstone signal off): with stack and infinite engine the whole input is
        // converted in the first operation, with fortune X and autosmelt
        STEPS.add(new Step(10, () -> signal(false)));
        STEPS.add(new Step(80, () -> shot("quarry_gui_converted.png")));
        // a smaller GUI scale, so the name plate above the GUI fits on the screen too
        STEPS.add(new Step(10, () -> guiScale(2)));
        STEPS.add(new Step(20, () -> shot("quarry_gui_small.png")));
        STEPS.add(new Step(10, () -> guiScale(0)));
        // the reference photo's view: sunset, narrow view, drill resting (a redstone signal stops it)
        STEPS.add(new Step(20, AutoShot::photoSetUp));
        // close up, the way the in-game reference shots (reference/ingame) look at it
        STEPS.add(new Step(60, () -> look(1.5D, 0, 0.0D, 34.0F)));
        STEPS.add(new Step(60, () -> shot("ref_front.png")));
        STEPS.add(new Step(20, () -> look(1.5D, 90, 0.0D, 34.0F)));
        STEPS.add(new Step(60, () -> shot("ref_side.png")));
        STEPS.add(new Step(20, () -> look(2.0D, 45, 0.3D, 34.0F)));
        STEPS.add(new Step(60, () -> shot("ref_diagonal.png")));
        STEPS.add(new Step(20, () -> look(0.05D, 0, 1.4D, 89.9F)));
        STEPS.add(new Step(60, () -> shot("ref_top.png")));
        // from where reference/ingame/1.png was taken (camera fitted to the block's corners), and
        // from just outside the side opening, low, like the close-ups of the drill
        STEPS.add(new Step(20, () -> eye(-1.246D, 0.61D, 0.574D, -90.0F, 3.0F)));
        STEPS.add(new Step(60, () -> shot("ref_cam1.png")));
        STEPS.add(new Step(20, () -> eye(-0.35D, 0.45D, 0.5D, -90.0F, -15.0F)));
        STEPS.add(new Step(60, () -> shot("ref_inside.png")));
        // the vacuum chest: it pulls in gold nuggets lying round it (64, 64, 64 and 18, as the
        // reference GUI shows them), then its GUI and the block, and its working area
        STEPS.add(new Step(10, AutoShot::setUpVacuum));
        STEPS.add(new Step(60, () -> vacuumEye(1.6D, 1.9D, 2.4D, 150.0F, 32.5F)));
        STEPS.add(new Step(60, () -> shot("vacuum_block.png")));
        STEPS.add(new Step(10, () -> guiScale(2)));
        STEPS.add(new Step(20, AutoShot::openVacuumGui));
        // it opens out of a galaxy: a few moments of that
        STEPS.add(new Step(4, () -> shot("vacuum_open_1.png")));
        STEPS.add(new Step(6, () -> shot("vacuum_open_2.png")));
        STEPS.add(new Step(6, () -> shot("vacuum_open_3.png")));
        STEPS.add(new Step(24, AutoShot::mouseAway));
        STEPS.add(new Step(20, () -> shot("vacuum_gui.png")));
        // the catchers' act goes on: a few more moments of it
        for (int k = 1; k <= 3; k++) {
            int n = k;
            STEPS.add(new Step(26, () -> shot("vacuum_gui_" + n + ".png")));
        }
        STEPS.add(new Step(10, () -> Minecraft.getInstance().setScreen(null)));
        STEPS.add(new Step(10, () -> guiScale(0)));
        STEPS.add(new Step(10, AutoShot::vacuumArea));
        STEPS.add(new Step(20, () -> vacuumEye(12.0D, 9.0D, 14.0D, 140.0F, 25.0F)));
        STEPS.add(new Step(60, () -> shot("vacuum_area.png")));
        STEPS.add(new Step(60, () -> Minecraft.getInstance().stop()));
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

    private static void setUp() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setDayTime(6000L);
            BlockState lower = GoldenQuarry.QUARRY.get().defaultBlockState()
                    .setValue(QuarryBlock.FACING, Direction.NORTH);
            level.setBlock(POS, lower, 3);
            if (level.getBlockEntity(POS) instanceof QuarryBlockEntity be) {
                be.input().setStackInSlot(0, new ItemStack(Items.IRON_ORE, 64));
                be.input().setStackInSlot(1, new ItemStack(Items.DEEPSLATE_GOLD_ORE, 48));
                be.input().setStackInSlot(2, new ItemStack(Items.DIAMOND_ORE, 16));
                be.input().setStackInSlot(3, new ItemStack(Items.REDSTONE_ORE, 32));
                for (int i = 0; i < 6; i++) be.output().setStackInSlot(i, new ItemStack(Items.IRON_INGOT, 64));
                be.upgrades().setStackInSlot(0, new ItemStack(GoldenQuarry.STACK_UPGRADE.get()));
                be.upgrades().setStackInSlot(1, new ItemStack(GoldenQuarry.INFINITE_UPGRADE.get()));
                be.upgrades().setStackInSlot(2, new ItemStack(GoldenQuarry.SMELTING_UPGRADE.get()));
                // paused by a redstone signal, so the ores stay in for the pictures
                level.setBlock(POS.below(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
                be.upgrades().setStackInSlot(QuarryBlockEntity.FORTUNE_SLOT, new ItemStack(GoldenQuarry.FORTUNE_UPGRADE_10.get()));
                be.getCapability(ForgeCapabilities.ENERGY).ifPresent(e -> {
                    for (int i = 0; i < 20; i++) e.receiveEnergy(100_000, false);
                });
            }
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.getInventory().setItem(0, new ItemStack(GoldenQuarry.FORTUNE_UPGRADE_2.get()));
            p.getInventory().setItem(1, new ItemStack(GoldenQuarry.FORTUNE_UPGRADE_5.get()));
            p.getInventory().setItem(2, new ItemStack(GoldenQuarry.FORTUNE_UPGRADE_10.get()));
            p.getInventory().setItem(3, new ItemStack(GoldenQuarry.QUARRY_ITEM.get()));
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
        });
        mc.getTutorial().setStep(TutorialSteps.NONE);
        mc.options.hideGui = true;
        mc.options.bobView().set(false);
        mc.options.fov().set(55);
    }

    /** Camera round the quarry: angle 0 = from the south, 90 = from the west... */
    private static void look(double distance, int deg, double up, float pitch) {
        double a = Math.toRadians(deg);
        double x = POS.getX() + 0.5D + distance * Math.sin(a);
        double z = POS.getZ() + 0.5D + distance * Math.cos(a);
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.connection.teleport(x, POS.getY() + up, z, 180.0F - deg, pitch);
            p.getAbilities().flying = up > 0.5D;
            p.onUpdateAbilities();
        });
    }

    private static void signal(boolean on) {
        MinecraftServer server = server();
        server.execute(() -> server.overworld().setBlock(POS.below(), (on ? net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK
                : net.minecraft.world.level.block.Blocks.DIRT).defaultBlockState(), 3));
    }

    /** Spectator camera with the eye at the given place (in blocks from the quarry's corner). */
    private static void eye(double x, double y, double z, float yaw, float pitch) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.setGameMode(GameType.SPECTATOR);
            p.connection.teleport(POS.getX() + x, POS.getY() + y - 1.62D, POS.getZ() + z, yaw, pitch);
        });
    }

    private static void openGui() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            if (server.overworld().getBlockEntity(POS) instanceof QuarryBlockEntity be) NetworkHooks.openScreen(p, be, POS);
        });
        Minecraft.getInstance().options.hideGui = false;
    }

    private static final BlockPos VACUUM = new BlockPos(24, -60, 0);

    private static void setUpVacuum() {
        Minecraft.getInstance().options.hideGui = true;
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setBlock(VACUUM, GoldenQuarry.VACUUM_CHEST.get().defaultBlockState(), 3);
            int[] counts = {64, 64, 64, 18};
            for (int i = 0; i < counts.length; i++) {
                net.minecraft.world.entity.item.ItemEntity e = new net.minecraft.world.entity.item.ItemEntity(level,
                        VACUUM.getX() + 2.5D + i, VACUUM.getY() + 0.2D, VACUUM.getZ() + 3.5D, new ItemStack(Items.GOLD_NUGGET, counts[i]));
                e.setDeltaMovement(0.0D, 0.0D, 0.0D);
                level.addFreshEntity(e);
            }
        });
    }

    private static void openVacuumGui() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            if (server.overworld().getBlockEntity(VACUUM) instanceof com.reya.goldenquarry.VacuumChestBlockEntity be) {
                NetworkHooks.openScreen(p, be, VACUUM);
            }
        });
        Minecraft.getInstance().options.hideGui = false;
    }

    private static void vacuumArea() {
        Minecraft.getInstance().options.hideGui = true;
        MinecraftServer server = server();
        server.execute(() -> {
            if (server.overworld().getBlockEntity(VACUUM) instanceof com.reya.goldenquarry.VacuumChestBlockEntity be) be.toggleShowArea();
        });
    }

    /** Spectator camera with the eye at the given place (in blocks from the vacuum chest's corner). */
    private static void vacuumEye(double x, double y, double z, float yaw, float pitch) {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.setGameMode(GameType.SPECTATOR);
            p.connection.teleport(VACUUM.getX() + x, VACUUM.getY() + y - 1.62D, VACUUM.getZ() + z, yaw, pitch);
        });
    }

    private static void guiScale(int scale) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.guiScale().set(scale);
        mc.resizeDisplay();
    }

    private static void photoSetUp() {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);
        mc.options.hideGui = true;
        mc.options.fov().set(70);
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setDayTime(6000L);
            level.setBlock(POS.below(), net.minecraft.world.level.block.Blocks.REDSTONE_BLOCK.defaultBlockState(), 3);
        });
    }

    /** Mouse away from the slots, so no tooltip covers the GUI. */
    private static void mouseAway() {
        Minecraft mc = Minecraft.getInstance();
        org.lwjgl.glfw.GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), 4.0D, 4.0D);
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> { });
    }

    private AutoShot() {
    }
}
