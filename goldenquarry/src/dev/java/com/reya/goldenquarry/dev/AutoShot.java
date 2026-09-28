package com.reya.goldenquarry.dev;

import com.reya.goldenquarry.GoldenQuarry;
import com.reya.goldenquarry.QuarryBlock;
import com.reya.goldenquarry.QuarryBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
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
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;

/**
 * Dev-only: with GOLDENQUARRY_AUTOSHOT=true the client makes a flat world, places the quarry at
 * sunset, takes screenshots from the reference photo's angle, close up and of the open GUI, then quits.
 */
@Mod.EventBusSubscriber(modid = GoldenQuarry.MODID, value = Dist.CLIENT)
public final class AutoShot {
    private static final boolean ON = "true".equals(System.getenv("GOLDENQUARRY_AUTOSHOT"));
    private static final BlockPos POS = new BlockPos(0, -60, 0);
    private static int ticks, phase, since;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (!ON || event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        ticks++;
        int waited = ticks - since;
        switch (phase) {
            case 0 -> { if (mc.level == null && mc.screen != null && ticks > 100) { createWorld(mc); next(); } }
            case 1 -> { if (mc.player != null && mc.getSingleplayerServer() != null) next(); }
            case 2 -> { if (waited > 60) { setUp(mc); next(); } }
            case 3 -> { if (waited > 200) { shot(mc, "quarry_world.png"); next(); } }
            case 4 -> { if (waited > 20) { place(mc, 3.2D, 2.0F); next(); } }
            case 5 -> { if (waited > 60) { shot(mc, "quarry_closeup.png"); next(); } }
            case 6 -> { if (waited > 20) { openGui(mc); next(); } }
            case 7 -> { if (waited > 60) { shot(mc, "quarry_gui.png"); next(); } }
            case 8 -> { if (waited > 60) mc.stop(); }
            default -> { }
        }
    }

    private static void next() {
        phase++;
        since = ticks;
    }

    private static void createWorld(Minecraft mc) {
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        LevelSettings settings = new LevelSettings("autoshot", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("autoshot", settings, new WorldOptions(1L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    private static void setUp(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setDayTime(12600L);
            BlockState lower = GoldenQuarry.QUARRY.get().defaultBlockState()
                    .setValue(QuarryBlock.FACING, Direction.NORTH).setValue(QuarryBlock.HALF, DoubleBlockHalf.LOWER);
            level.setBlock(POS, lower, 3);
            level.setBlock(POS.above(), lower.setValue(QuarryBlock.HALF, DoubleBlockHalf.UPPER), 3);
            if (level.getBlockEntity(POS) instanceof QuarryBlockEntity be) {
                be.common().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 64));
                be.common().setStackInSlot(1, new ItemStack(Items.COBBLESTONE, 64));
                be.common().setStackInSlot(2, new ItemStack(Items.COBBLESTONE, 25));
                for (int i = 0; i < 20; i++) be.valuables().setStackInSlot(i, new ItemStack(Items.IRON_INGOT, 64));
            }
        });
        mc.options.hideGui = true;
        mc.options.fov().set(30);
        mc.options.bobView().set(false);
        place(mc, 8.5D, 4.0F);
    }

    /** Stands the player where the reference photo was taken from: 40 degrees round from the south face. */
    private static void place(Minecraft mc, double distance, float pitch) {
        MinecraftServer server = mc.getSingleplayerServer();
        double a = Math.toRadians(40.0D);
        double x = POS.getX() + 0.5D + distance * Math.sin(a);
        double z = POS.getZ() + 0.5D + distance * Math.cos(a);
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.connection.teleport(x, POS.getY(), z, 140.0F, pitch);
        });
    }

    private static void openGui(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            if (server.overworld().getBlockEntity(POS) instanceof QuarryBlockEntity be) NetworkHooks.openScreen(p, be, POS);
        });
        mc.options.hideGui = false;
    }

    private static void shot(Minecraft mc, String name) {
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), message -> { });
    }

    private AutoShot() {
    }
}
