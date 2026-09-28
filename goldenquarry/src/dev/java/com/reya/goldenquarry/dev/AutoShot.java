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
                be.common().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
                be.common().setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
                be.common().setStackInSlot(2, new ItemStack(Items.COBBLESTONE, 25));
                for (int i = 0; i < 20; i++) be.valuables().setStackInSlot(i, new ItemStack(Items.IRON_INGOT, 64));
                be.upgrades().setStackInSlot(0, new ItemStack(GoldenQuarry.RANGE_UPGRADE.get()));
                be.upgrades().setStackInSlot(1, new ItemStack(GoldenQuarry.SPEED_UPGRADE.get()));
                be.upgrades().setStackInSlot(2, new ItemStack(GoldenQuarry.SMELTING_UPGRADE.get()));
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

    private static void openGui() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            if (server.overworld().getBlockEntity(POS) instanceof QuarryBlockEntity be) NetworkHooks.openScreen(p, be, POS);
        });
        Minecraft.getInstance().options.hideGui = false;
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
