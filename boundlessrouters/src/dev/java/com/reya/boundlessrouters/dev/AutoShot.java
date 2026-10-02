package com.reya.boundlessrouters.dev;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.reya.boundlessrouters.BoundlessRouters;
import com.reya.boundlessrouters.module.ModuleKind;
import com.reya.boundlessrouters.module.ModuleMenu;
import com.reya.boundlessrouters.module.ModuleSettings;
import com.reya.boundlessrouters.module.ModuleSettings.Setting;
import com.reya.boundlessrouters.module.Target;
import com.reya.boundlessrouters.router.RelativeDirection;
import com.reya.boundlessrouters.router.RouterBlock;
import com.reya.boundlessrouters.router.RouterBlockEntity;
import com.reya.boundlessrouters.upgrade.UpgradeKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.network.NetworkHooks;
import org.lwjgl.glfw.GLFW;

/**
 * Dev-only: with BOUNDLESSROUTERS_AUTOSHOT=true the client makes a flat world, sets up a working router (chests
 * round it, modules, upgrades) and photographs it: the block at work, its GUI with light running to its modules,
 * tooltips, the settings of several modules (opened by a real click on a gear too), then the same in Ukrainian;
 * and quits. The screenshots land in run/screenshots.
 */
@Mod.EventBusSubscriber(modid = BoundlessRouters.MODID, value = Dist.CLIENT)
public final class AutoShot {
    private static final boolean ON = "true".equals(System.getenv("BOUNDLESSROUTERS_AUTOSHOT"));
    private static final BlockPos ROUTER = new BlockPos(0, -60, 0);
    private static final List<Step> STEPS = new ArrayList<>();
    private static int ticks, step, since;
    private static boolean worldAsked;

    record Step(int delay, Runnable action) {
    }

    static {
        STEPS.add(new Step(20, AutoShot::setUp));
        STEPS.add(new Step(40, () -> eye(3.2D, 2.4D, 3.6D, 0.5D, 0.5D, 0.5D)));
        STEPS.add(new Step(5, AutoShot::worldView));
        STEPS.add(new Step(60, () -> shot("router_block.png")));
        STEPS.add(new Step(5, () -> eye(1.7D, 1.35D, 2.3D, 0.5D, 0.5D, 0.5D)));
        STEPS.add(new Step(30, () -> shot("router_front.png")));
        // from the side: the line from the router to the chest it sends to, items flying along it
        STEPS.add(new Step(5, () -> eye(4.6D, 1.5D, 2.0D, 0.5D, 0.55D, 2.0D)));
        STEPS.add(new Step(30, () -> shot("router_lines.png")));
        STEPS.add(new Step(3, () -> shot("router_lines_2.png")));
        // the router's GUI, at work
        STEPS.add(new Step(5, AutoShot::beforeGui));
        STEPS.add(new Step(5, AutoShot::openRouter));
        STEPS.add(new Step(2, AutoShot::mouseAway));
        STEPS.add(new Step(40, () -> shot("router_gui.png")));
        STEPS.add(new Step(3, () -> shot("router_gui_2.png")));
        STEPS.add(new Step(4, () -> shot("router_gui_3.png")));
        STEPS.add(new Step(5, () -> mouseAtGui(17, 33)));
        STEPS.add(new Step(10, () -> shot("router_gui_redstone.png")));
        STEPS.add(new Step(5, () -> mouseAtGui(30, 50)));
        STEPS.add(new Step(10, () -> shot("router_gui_info.png")));
        STEPS.add(new Step(5, () -> mouseAtGui(48, 93)));
        STEPS.add(new Step(10, () -> shot("router_gui_module.png")));
        // a real click on the second module's gear opens its settings
        STEPS.add(new Step(5, () -> clickAtGui(51, 109)));
        STEPS.add(new Step(30, AutoShot::mouseAway));
        STEPS.add(new Step(10, () -> shot("module_gui_from_router.png")));
        STEPS.add(new Step(5, AutoShot::closeScreen));
        // each kind of module's settings
        String[] names = {"sender", "puller", "distributor", "flinger", "breaker", "vacuum", "player", "detector", "extruder"};
        for (int i = 0; i < names.length; i++) {
            int slot = i;
            String name = names[i];
            STEPS.add(new Step(5, () -> openModule(slot)));
            STEPS.add(new Step(2, AutoShot::mouseAway));
            STEPS.add(new Step(25, () -> shot("module_" + name + ".png")));
            STEPS.add(new Step(5, AutoShot::closeScreen));
        }
        STEPS.add(new Step(5, () -> openModule(0)));
        STEPS.add(new Step(5, () -> mouseAtGui(150, 35)));
        STEPS.add(new Step(10, () -> shot("module_gui_tooltip.png")));
        STEPS.add(new Step(5, () -> mouseAtGui(38, 52)));
        STEPS.add(new Step(10, () -> shot("module_gui_direction.png")));
        STEPS.add(new Step(5, AutoShot::closeScreen));
        // in Ukrainian
        STEPS.add(new Step(5, () -> language("uk_ua")));
        STEPS.add(new Step(60, AutoShot::openRouter));
        STEPS.add(new Step(2, AutoShot::mouseAway));
        STEPS.add(new Step(40, () -> shot("router_gui_uk.png")));
        STEPS.add(new Step(5, AutoShot::closeScreen));
        STEPS.add(new Step(5, () -> openModule(2)));
        STEPS.add(new Step(2, AutoShot::mouseAway));
        STEPS.add(new Step(25, () -> shot("module_distributor_uk.png")));
        STEPS.add(new Step(5, AutoShot::closeScreen));
        STEPS.add(new Step(5, () -> openModule(3)));
        STEPS.add(new Step(2, AutoShot::mouseAway));
        STEPS.add(new Step(25, () -> shot("module_flinger_uk.png")));
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
        if (step < STEPS.size() && ticks - since >= STEPS.get(step).delay()) {
            try {
                STEPS.get(step).action().run();
            } catch (RuntimeException e) {
                BoundlessRouters.LOGGER.error("AutoShot step {} failed", step, e);
            }
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
        rules.getRule(GameRules.RULE_ANNOUNCE_ADVANCEMENTS).set(false, null);
        LevelSettings settings = new LevelSettings("autoshot", GameType.CREATIVE, false, Difficulty.PEACEFUL, true, rules,
                WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("autoshot", settings, new WorldOptions(1L, false, false),
                access -> access.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
    }

    // ------------------------------------------------------------------ the scene

    private static ItemStack module(ModuleKind kind, Consumer<ModuleSettings> setup) {
        ItemStack stack = new ItemStack(BoundlessRouters.module(kind));
        setup.accept(new ModuleSettings(stack));
        return stack;
    }

    /**
     * A router facing south between chests: one behind it full of cobblestone (a puller takes from it), one three
     * blocks ahead (a sender finds it and fills it), two more a distributor shares iron out to; and every other
     * kind of module set up. Each run the sender sends what the puller took the run before, so the buffer is
     * seldom empty and a detector sees it.
     */
    private static void setUp() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) level.setBlockAndUpdate(ROUTER.offset(x, -1, z), Blocks.POLISHED_DEEPSLATE.defaultBlockState());
            }
            level.setBlockAndUpdate(ROUTER, BoundlessRouters.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, Direction.SOUTH));
            BlockPos back = ROUTER.north(), front = ROUTER.south(3), left = ROUTER.offset(3, 0, 2), right = ROUTER.offset(-3, 0, 2);
            for (BlockPos pos : new BlockPos[]{back, front, left, right}) level.setBlockAndUpdate(pos, Blocks.CHEST.defaultBlockState());
            if (level.getBlockEntity(back) != null) {
                IItemHandler chest = level.getBlockEntity(back).getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().orElseThrow();
                for (int i = 0; i < chest.getSlots(); i++) chest.insertItem(i, new ItemStack(Items.COBBLESTONE, 64), false);
            }
            if (!(level.getBlockEntity(ROUTER) instanceof RouterBlockEntity router)) return;
            router.setOwner(player().getGameProfile());
            var dim = level.dimension();
            ItemStack[] modules = {
                    module(ModuleKind.SENDER, s -> {
                    }),
                    module(ModuleKind.PULLER, s -> s.apply(Setting.DIRECTION, RelativeDirection.BACK.ordinal())),
                    module(ModuleKind.DISTRIBUTOR, s -> {
                        s.toggleTarget(new Target(dim, left, Direction.UP));
                        s.toggleTarget(new Target(dim, right, Direction.UP));
                        s.apply(Setting.STRATEGY, 0);
                        s.apply(Setting.BLACKLIST, 0);
                        s.setFilterItems(List.of(new ItemStack(Items.IRON_INGOT)));
                    }),
                    module(ModuleKind.FLINGER, s -> {
                        s.apply(Setting.SPEED, 15);
                        s.apply(Setting.PITCH, 35);
                        s.apply(Setting.YAW, -10);
                        s.apply(Setting.REDSTONE, 1);
                    }),
                    module(ModuleKind.BREAKER, s -> {
                        s.apply(Setting.DIRECTION, RelativeDirection.DOWN.ordinal());
                        s.apply(Setting.FORTUNE, 5);
                        s.apply(Setting.REDSTONE, 1);
                    }),
                    module(ModuleKind.VACUUM, s -> s.apply(Setting.RADIUS, 12)),
                    module(ModuleKind.PLAYER, s -> {
                        s.setPlayer(player().getUUID(), player().getGameProfile().getName());
                        s.apply(Setting.SECTION, 3);
                        s.apply(Setting.OPERATION, 1);
                        s.apply(Setting.REDSTONE, 1);
                        s.apply(Setting.BLACKLIST, 0);
                        s.setFilterItems(List.of(new ItemStack(Items.DIAMOND), new ItemStack(Items.EMERALD)));
                    }),
                    module(ModuleKind.DETECTOR, s -> {
                        s.apply(Setting.DIRECTION, RelativeDirection.LEFT.ordinal());
                        s.apply(Setting.POWER, 12);
                        s.apply(Setting.STRONG, 1);
                        s.apply(Setting.BLACKLIST, 0);
                        s.setFilterItems(List.of(new ItemStack(Items.COBBLESTONE)));
                        s.apply(Setting.MATCH_TAGS, 1);
                    }),
                    module(ModuleKind.EXTRUDER, s -> {
                        s.apply(Setting.DIRECTION, RelativeDirection.UP.ordinal());
                        s.apply(Setting.REDSTONE, 1);
                    }),
            };
            for (int i = 0; i < modules.length; i++) router.modules().setStackInSlot(i, modules[i]);
            router.upgrades().setStackInSlot(0, new ItemStack(BoundlessRouters.upgrade(UpgradeKind.SPEED), 7));
            router.upgrades().setStackInSlot(1, new ItemStack(BoundlessRouters.upgrade(UpgradeKind.STACK), 3));
            router.upgrades().setStackInSlot(2, new ItemStack(BoundlessRouters.upgrade(UpgradeKind.RANGE_2), 1));
        });
    }

    private static void openRouter() {
        beforeGui();
        MinecraftServer server = server();
        server.execute(() -> {
            if (server.overworld().getBlockEntity(ROUTER) instanceof RouterBlockEntity router) NetworkHooks.openScreen(player(), router, ROUTER);
        });
    }

    private static void openModule(int slot) {
        beforeGui();
        MinecraftServer server = server();
        server.execute(() -> {
            if (!(server.overworld().getBlockEntity(ROUTER) instanceof RouterBlockEntity router)) return;
            ItemStack module = router.modules().getStackInSlot(slot);
            NetworkHooks.openScreen(player(), new SimpleMenuProvider((id, inventory, p) -> ModuleMenu.forRouter(id, inventory, router, slot),
                    module.getHoverName()), buf -> ModuleMenu.writeRouter(buf, ROUTER, slot));
        });
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

    /** The camera at x, y, z (blocks from the router) looking at tx, ty, tz. */
    static void eye(double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = player();
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.connection.teleport(ROUTER.getX() + x, ROUTER.getY() + y - 1.62D, ROUTER.getZ() + z, yaw, pitch);
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
            BoundlessRouters.LOGGER.warn("AutoShot: can't move the mouse", e);
        }
    }

    static void mouseAtGui(int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        double scale = mc.getWindow().getGuiScale();
        setMouse((screen.getGuiLeft() + x + 0.5D) * scale, (screen.getGuiTop() + y + 0.5D) * scale);
    }

    /** A left click at a point of the open screen, through the game's own mouse handler (as a player's comes). */
    static void clickAtGui(int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        mouseAtGui(x, y);
        String on = screen.getClass().getSimpleName();
        try {
            java.lang.reflect.Method press = net.minecraft.client.MouseHandler.class.getDeclaredMethod("onPress", long.class, int.class, int.class,
                    int.class);
            press.setAccessible(true);
            long window = mc.getWindow().getWindow();
            press.invoke(mc.mouseHandler, window, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_PRESS, 0);
            press.invoke(mc.mouseHandler, window, GLFW.GLFW_MOUSE_BUTTON_LEFT, GLFW.GLFW_RELEASE, 0);
            BoundlessRouters.LOGGER.info("AutoShot: click at {},{} on {}", x, y, on);
        } catch (java.lang.reflect.InvocationTargetException e) {
            BoundlessRouters.LOGGER.error("AutoShot: click at {},{} on {} crashed", x, y, on, e.getCause());
        } catch (ReflectiveOperationException e) {
            BoundlessRouters.LOGGER.warn("AutoShot: can't click", e);
        }
    }

    static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {
        });
    }

    private AutoShot() {
    }
}
