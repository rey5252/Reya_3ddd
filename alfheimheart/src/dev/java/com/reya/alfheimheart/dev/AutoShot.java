package com.reya.alfheimheart.dev;

import java.util.ArrayList;
import java.util.List;

import com.reya.alfheimheart.AlfheimHeart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
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
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

/**
 * Dev-only: with ALFHEIMHEART_AUTOSHOT=true the client makes a flat world and photographs every block of the
 * mod in its own little scene ({@link GreenhouseShots}, {@link PortalShots}): in the world, its GUI opening,
 * settled, with tooltips, closing, its lexicon pages; then the GUIs and the lexicon in Ukrainian; and quits.
 * The screenshots land in run/screenshots, named after their scene.
 */
@Mod.EventBusSubscriber(modid = AlfheimHeart.MODID, value = Dist.CLIENT)
public final class AutoShot {
    private static final boolean ON = "true".equals(System.getenv("ALFHEIMHEART_AUTOSHOT"));
    private static final List<Step> STEPS = new ArrayList<>();
    private static int ticks, step, since;
    private static boolean worldAsked;

    public record Step(int delay, Runnable action) {
    }

    static {
        GreenhouseShots.steps(STEPS);
        PortalShots.steps(STEPS);
        STEPS.add(new Step(5, () -> language("uk_ua")));
        STEPS.add(new Step(40, () -> guiScale(3)));
        GreenhouseShots.ukSteps(STEPS);
        PortalShots.ukSteps(STEPS);
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
        // wait while the world loads or resources reload
        if (mc.player == null || mc.getSingleplayerServer() == null || mc.getOverlay() != null) {
            since = ticks;
            return;
        }
        if (step < STEPS.size() && ticks - since >= STEPS.get(step).delay()) {
            try {
                STEPS.get(step).action().run();
            } catch (RuntimeException e) {
                AlfheimHeart.LOGGER.error("AutoShot step {} failed", step, e);
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

    // ------------------------------------------------------------------ helpers for the scenes

    static MinecraftServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    static ServerPlayer player() {
        return server().getPlayerList().getPlayers().get(0);
    }

    static Item item(String namespace, String path) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(namespace, path));
        return item == null ? net.minecraft.world.item.Items.AIR : item;
    }

    static Block block(String namespace, String path) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(namespace, path));
        return block == null ? Blocks.AIR : block;
    }

    static ItemStack stack(String namespace, String path, int count) {
        return new ItemStack(item(namespace, path), count);
    }

    /** The world's look for the photos in it: no hand or hotbar, no bobbing. */
    static void worldView() {
        Minecraft mc = Minecraft.getInstance();
        mc.getTutorial().setStep(TutorialSteps.NONE);
        mc.options.hideGui = true;
        mc.options.bobView().set(false);
        mc.options.fov().set(70);
    }

    /** Camera at x, y, z (blocks from a scene's corner) looking at tx, ty, tz. */
    static void eye(BlockPos base, double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = player();
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            p.connection.teleport(base.getX() + x, base.getY() + y - 1.62D, base.getZ() + z, yaw, pitch);
        });
    }

    /** Before a GUI opens: the hand and hotbar back, no toasts over the view. */
    static void beforeGui() {
        Minecraft.getInstance().options.hideGui = false;
        Minecraft.getInstance().getToasts().clear();
    }

    static void closeScreen() {
        Minecraft.getInstance().setScreen(null);
    }

    /** The lexicon shows an entry once its advancement is earned. */
    static void grant(String advancement) {
        MinecraftServer server = server();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "advancement grant @a until " + advancement));
    }

    /** Opens the Lexica Botania at an entry's page (through Patchouli's API, which only runs here). */
    static void openLexicon(String entry, int page) {
        Minecraft.getInstance().getToasts().clear();
        try {
            Class<?> api = Class.forName("vazkii.patchouli.api.PatchouliAPI");
            Object instance = api.getMethod("get").invoke(null);
            Class<?> type = Class.forName("vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI");
            type.getMethod("openBookEntry", ResourceLocation.class, ResourceLocation.class, int.class)
                    .invoke(instance, new ResourceLocation("botania", "lexicon"), new ResourceLocation("botania", entry), page);
        } catch (ReflectiveOperationException e) {
            AlfheimHeart.LOGGER.warn("AutoShot: can't open the lexicon", e);
        }
    }

    static void language(String code) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);
        mc.getLanguageManager().setSelected(code);
        mc.options.languageCode = code;
        mc.reloadResourcePacks();
    }

    static void guiScale(int scale) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.guiScale().set(scale);
        mc.resizeDisplay();
    }

    /** Mouse away from the GUI, so no tooltip covers it. */
    static void mouseAway() {
        setMouse(4.0D, 4.0D);
    }

    /**
     * Puts the mouse at a window position: the virtual screen doesn't report the cursor moving,
     * so the game's own record of it is set too.
     */
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
            AlfheimHeart.LOGGER.warn("AutoShot: can't move the mouse", e);
        }
    }

    /** Mouse over a point of the GUI (menu coordinates). */
    static void mouseAtGui(int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        double scale = mc.getWindow().getGuiScale();
        setMouse((screen.getGuiLeft() + x + 0.5D) * scale, (screen.getGuiTop() + y + 0.5D) * scale);
    }

    static void clickGui(int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        mouseAtGui(x, y);
        screen.mouseClicked(screen.getGuiLeft() + x + 0.5D, screen.getGuiTop() + y + 0.5D, 0);
    }

    static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {
        });
    }

    private AutoShot() {
    }
}
