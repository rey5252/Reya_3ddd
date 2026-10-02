package com.reya.singularityfusion.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import com.reya.singularityfusion.SingularityFusion;
import com.reya.singularityfusion.block.FusionCoreBlockEntity;
import com.reya.singularityfusion.block.GravitonPylonBlockEntity;
import com.reya.singularityfusion.compat.JeiCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import org.lwjgl.glfw.GLFW;

/**
 * Dev-only: with SINGULARITYFUSION_AUTOSHOT=true the client makes a flat world, builds the structure (a core on its
 * casing floor, eight pylons round it holding the event horizon core's ingredients) and photographs it at night: empty,
 * half full and full (the singularity growing), close up and from below, by day; the core's screen; a fusion from its
 * beams to its flash and shock wave and the singularity shrinking after; the screen in Ukrainian; and quits. The
 * screenshots land in run/screenshots.
 */
@Mod.EventBusSubscriber(modid = SingularityFusion.MODID, value = Dist.CLIENT)
public final class AutoShot {
    private static final boolean ON = "true".equals(System.getenv("SINGULARITYFUSION_AUTOSHOT"));
    private static final BlockPos CORE = new BlockPos(0, -59, 0);
    private static final int[][] PYLONS = {{5, 0}, {4, 4}, {0, 5}, {-4, 4}, {-5, 0}, {-4, -4}, {0, -5}, {4, -4}};
    private static final Item[] INGREDIENTS = {Items.NETHER_STAR, Items.ECHO_SHARD, Items.NETHERITE_BLOCK, Items.TOTEM_OF_UNDYING,
            Items.NETHER_STAR, Items.ECHO_SHARD, Items.NETHERITE_BLOCK, Items.TOTEM_OF_UNDYING};
    private static final List<Step> STEPS = new ArrayList<>();
    private static int ticks, step, since;
    private static boolean worldAsked;

    /** Waits `delay` ticks after the step before, then until `ready` (at most a minute), then does `action`. */
    record Step(int delay, BooleanSupplier ready, Runnable action) {
        Step(int delay, Runnable action) {
            this(delay, () -> true, action);
        }
    }

    static {
        STEPS.add(new Step(20, AutoShot::setUp));
        STEPS.add(new Step(30, () -> time(18000L)));
        // empty: the structure stands, no singularity over it
        STEPS.add(new Step(5, () -> eye(11.0D, 2.6D, 9.5D, 0.5D, 4.2D, 0.5D)));
        STEPS.add(new Step(5, AutoShot::worldView));
        STEPS.add(new Step(80, () -> shot("structure_empty.png")));
        // half full: it has grown to half its size; full: all of it, and all that goes on round it
        STEPS.add(new Step(5, () -> energy(0.5D)));
        STEPS.add(new Step(150, () -> shot("structure_half.png")));
        STEPS.add(new Step(5, () -> energy(1.0D)));
        STEPS.add(new Step(150, () -> shot("structure_full.png")));
        STEPS.add(new Step(4, () -> shot("structure_full_2.png")));
        STEPS.add(new Step(5, () -> eye(6.2D, 5.2D, 6.0D, 0.5D, 6.0D, 0.5D)));
        STEPS.add(new Step(30, () -> shot("hole_close.png")));
        STEPS.add(new Step(5, () -> eye(3.0D, 1.6D, 2.2D, 0.5D, 6.0D, 0.5D)));
        STEPS.add(new Step(30, () -> shot("hole_from_below.png")));
        STEPS.add(new Step(5, () -> eye(-9.0D, 6.6D, 3.5D, 0.5D, 6.0D, 0.5D)));
        STEPS.add(new Step(30, () -> shot("hole_level.png")));
        STEPS.add(new Step(5, () -> eye(-7.5D, 3.4D, 5.5D, 0.5D, 4.0D, 0.5D)));
        STEPS.add(new Step(30, () -> shot("pylons.png")));
        STEPS.add(new Step(5, () -> time(6000L)));
        STEPS.add(new Step(5, () -> eye(11.0D, 2.6D, 9.5D, 0.5D, 4.2D, 0.5D)));
        STEPS.add(new Step(40, () -> shot("structure_day.png")));
        STEPS.add(new Step(5, () -> time(18000L)));
        // the core's screen, ready
        STEPS.add(new Step(20, AutoShot::openCore));
        STEPS.add(new Step(2, AutoShot::mouseAway));
        STEPS.add(new Step(40, () -> shot("gui_ready.png")));
        STEPS.add(new Step(5, () -> mouseAtGui(17, 84)));
        STEPS.add(new Step(10, () -> shot("gui_energy_tooltip.png")));
        STEPS.add(new Step(5, () -> mouseAtGui(35, 32)));
        STEPS.add(new Step(10, () -> shot("gui_pylon_tooltip.png")));
        STEPS.add(new Step(5, () -> mouseAtGui(128, 116)));
        STEPS.add(new Step(10, () -> shot("gui_start_tooltip.png")));
        STEPS.add(new Step(5, AutoShot::closeScreen));
        // a fusion, from the world
        STEPS.add(new Step(5, () -> eye(11.0D, 2.6D, 9.5D, 0.5D, 4.2D, 0.5D)));
        STEPS.add(new Step(10, AutoShot::worldView));
        STEPS.add(new Step(5, AutoShot::start));
        STEPS.add(new Step(5, () -> progress() >= 0.22F, () -> shot("fusion_beams.png")));
        STEPS.add(new Step(5, () -> progress() >= 0.5F, () -> shot("fusion_inject.png")));
        STEPS.add(new Step(5, () -> progress() >= 0.58F, AutoShot::openCore));
        STEPS.add(new Step(2, AutoShot::mouseAway));
        STEPS.add(new Step(10, () -> shot("gui_fusing.png")));
        STEPS.add(new Step(2, AutoShot::closeScreen));
        STEPS.add(new Step(1, () -> eye(11.0D, 2.6D, 9.5D, 0.5D, 4.2D, 0.5D)));
        STEPS.add(new Step(2, AutoShot::worldView));
        STEPS.add(new Step(5, () -> progress() >= 0.93F, () -> shot("fusion_collapse.png")));
        STEPS.add(new Step(1, () -> !fusing(), () -> shot("fusion_flash.png")));
        STEPS.add(new Step(5, () -> shot("fusion_shock.png")));
        STEPS.add(new Step(20, () -> shot("fusion_result.png")));
        STEPS.add(new Step(140, () -> shot("fusion_after.png")));
        // the screen in Ukrainian, the singularity half its size now
        STEPS.add(new Step(5, () -> language("uk_ua")));
        STEPS.add(new Step(80, AutoShot::openCore));
        STEPS.add(new Step(2, AutoShot::mouseAway));
        STEPS.add(new Step(40, () -> shot("gui_uk.png")));
        STEPS.add(new Step(5, () -> mouseAtGui(17, 84)));
        STEPS.add(new Step(10, () -> shot("gui_uk_energy_tooltip.png")));
        STEPS.add(new Step(5, AutoShot::closeScreen));
        // JEI's page for the fusions, when JEI is there
        STEPS.add(new Step(5, () -> language("en_us")));
        STEPS.add(new Step(80, AutoShot::jeiPage));
        STEPS.add(new Step(2, AutoShot::mouseAway));
        STEPS.add(new Step(30, () -> shot("jei_fusion.png")));
        STEPS.add(new Step(5, AutoShot::closeScreen));
        STEPS.add(new Step(10, () -> Minecraft.getInstance().stop()));
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
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) {
            since = ticks;
            return;
        }
        if (step >= STEPS.size()) return;
        Step s = STEPS.get(step);
        int waited = ticks - since;
        if (waited < s.delay() || !s.ready().getAsBoolean() && waited < s.delay() + 1200) return;
        try {
            s.action().run();
        } catch (RuntimeException e) {
            SingularityFusion.LOGGER.error("AutoShot step {} failed", step, e);
        }
        step++;
        since = ticks;
    }

    private static void createWorld(Minecraft mc) {
        mc.getTutorial().setStep(TutorialSteps.NONE);
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        rules.getRule(GameRules.RULE_WEATHER_CYCLE).set(false, null);
        rules.getRule(GameRules.RULE_DOMOBSPAWNING).set(false, null);
        rules.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, null);
        LevelSettings settings = new LevelSettings("autoshot", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("autoshot", settings, new WorldOptions(1L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    // ------------------------------------------------------------------ the scene

    /**
     * A floor of polished deepslate with a ring of seamed casing; the core on its casing floor; eight pylons on
     * casing pedestals round it, each holding one of the event horizon core's ingredients; a collapsed star in the core.
     */
    private static void setUp() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            for (int x = -9; x <= 9; x++) {
                for (int z = -9; z <= 9; z++) {
                    double r = Math.sqrt(x * x + z * z);
                    Block floor = r > 6.6D && r < 7.6D ? SingularityFusion.VOID_CASING_SEAM.get() : Blocks.POLISHED_DEEPSLATE;
                    level.setBlockAndUpdate(CORE.offset(x, -2, z), floor.defaultBlockState());
                }
            }
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Block casing = dx == 0 && dz == 0 ? SingularityFusion.VOID_CASING_RUNE.get()
                            : dx == 0 || dz == 0 ? SingularityFusion.VOID_CASING_SEAM.get() : SingularityFusion.VOID_CASING.get();
                    level.setBlockAndUpdate(CORE.offset(dx, -1, dz), casing.defaultBlockState());
                }
            }
            level.setBlockAndUpdate(CORE, SingularityFusion.FUSION_CORE.get().defaultBlockState());
            for (int i = 0; i < PYLONS.length; i++) {
                BlockPos at = CORE.offset(PYLONS[i][0], 0, PYLONS[i][1]);
                level.setBlockAndUpdate(at.below(), SingularityFusion.VOID_CASING_RUNE.get().defaultBlockState());
                level.setBlockAndUpdate(at, SingularityFusion.GRAVITON_PYLON.get().defaultBlockState());
                if (level.getBlockEntity(at) instanceof GravitonPylonBlockEntity pylon) pylon.setItem(new ItemStack(INGREDIENTS[i]));
            }
            onCore(core -> core.items().setStackInSlot(FusionCoreBlockEntity.CATALYST, new ItemStack(SingularityFusion.COLLAPSED_STAR.get())));
        });
    }

    private static void onCore(Consumer<FusionCoreBlockEntity> action) {
        MinecraftServer server = server();
        server.execute(() -> {
            if (server.overworld().getBlockEntity(CORE) instanceof FusionCoreBlockEntity core) action.accept(core);
        });
    }

    private static void energy(double fraction) {
        onCore(core -> core.setEnergy((long) (core.capacity() * fraction)));
    }

    private static void start() {
        onCore(core -> {
            if (!core.start()) SingularityFusion.LOGGER.warn("AutoShot: the fusion didn't start: {}", core.status());
        });
    }

    private static void time(long dayTime) {
        MinecraftServer server = server();
        server.execute(() -> server.overworld().setDayTime(dayTime));
    }

    /** The client's view of the core. */
    private static FusionCoreBlockEntity clientCore() {
        Minecraft mc = Minecraft.getInstance();
        return mc.level != null && mc.level.getBlockEntity(CORE) instanceof FusionCoreBlockEntity core ? core : null;
    }

    private static float progress() {
        FusionCoreBlockEntity core = clientCore();
        return core == null ? 0.0F : core.progress(0.0F);
    }

    private static boolean fusing() {
        FusionCoreBlockEntity core = clientCore();
        return core != null && core.fusing();
    }

    private static void jeiPage() {
        beforeGui();
        if (!ModList.get().isLoaded("jei") || !JeiCompat.showFusions()) SingularityFusion.LOGGER.warn("AutoShot: JEI's page can't be opened");
    }

    /** Opens the core's screen, standing near it first (a menu closes on a player more than 8 blocks off). */
    private static void openCore() {
        beforeGui();
        eye(3.4D, 1.7D, 2.8D, 0.5D, 5.5D, 0.5D);
        onCore(core -> NetworkHooks.openScreen(player(), core, CORE));
    }

    // ------------------------------------------------------------------ helpers

    static MinecraftServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    static ServerPlayer player() {
        return server().getPlayerList().getPlayers().get(0);
    }

    static void worldView() {
        Minecraft mc = Minecraft.getInstance();
        mc.options.hideGui = true;
        mc.options.bobView().set(false);
        mc.options.fov().set(70);
    }

    static void beforeGui() {
        Minecraft.getInstance().options.hideGui = false;
        Minecraft.getInstance().getToasts().clear();
    }

    /** The camera at x, y, z (blocks from the core's corner) looking at tx, ty, tz. */
    static void eye(double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = player();
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.connection.teleport(CORE.getX() + x, CORE.getY() + y - 1.62D, CORE.getZ() + z, yaw, pitch);
        });
    }

    static void closeScreen() {
        Minecraft.getInstance().setScreen(null);
    }

    static void language(String code) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);
        mc.getLanguageManager().setSelected(code);
        mc.options.languageCode = code;
        mc.reloadResourcePacks();
    }

    static void mouseAway() {
        setMouse(4.0D, 4.0D);
    }

    /** Puts the mouse at a window position (the virtual screen doesn't report moves, so the game's record too). */
    static void setMouse(double x, double y) {
        Minecraft mc = Minecraft.getInstance();
        GLFW.glfwSetCursorPos(mc.getWindow().getWindow(), x, y);
        try {
            java.lang.reflect.Field fx = net.minecraft.client.MouseHandler.class.getDeclaredField("xpos");
            java.lang.reflect.Field fy = net.minecraft.client.MouseHandler.class.getDeclaredField("ypos");
            fx.setAccessible(true);
            fy.setAccessible(true);
            fx.setDouble(mc.mouseHandler, x);
            fy.setDouble(mc.mouseHandler, y);
        } catch (ReflectiveOperationException e) {
            SingularityFusion.LOGGER.warn("AutoShot: can't move the mouse", e);
        }
    }

    static void mouseAtGui(int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        double scale = mc.getWindow().getGuiScale();
        setMouse((screen.getGuiLeft() + x + 0.5D) * scale, (screen.getGuiTop() + y + 0.5D) * scale);
    }

    static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {
        });
    }

    private AutoShot() {
    }
}
