package com.reya.managarden.dev;

import java.util.ArrayList;
import java.util.List;

import com.reya.managarden.GreenhouseBlockEntity;
import com.reya.managarden.ManaGarden;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.registries.ForgeRegistries;
import org.lwjgl.glfw.GLFW;

/**
 * Dev-only: with MANAGARDEN_AUTOSHOT=true the client makes a flat world, plants a greenhouse full of
 * Botania flowers in a little garden, photographs it closed and with its bud open, opens its GUI and
 * photographs the opening animation, the settled GUI, a tooltip and the keeper talking, then quits.
 */
@Mod.EventBusSubscriber(modid = ManaGarden.MODID, value = Dist.CLIENT)
public final class AutoShot {
    private static final boolean ON = "true".equals(System.getenv("MANAGARDEN_AUTOSHOT"));
    private static final int BASE = -60;
    private static final BlockPos POS = new BlockPos(0, BASE, 0);
    private static final String[] FLOWERS = {"endoflame", "hydroangeas", "thermalily", "rosa_arcana", "munchdew",
            "kekimurus", "gourmaryllis", "spectrolus"};
    private static final List<Step> STEPS = new ArrayList<>();
    private static int ticks, step, since;
    private static boolean worldAsked;

    private record Step(int delay, Runnable action) {
    }

    static {
        STEPS.add(new Step(80, AutoShot::setUp));
        STEPS.add(new Step(60, () -> eye(2.3D, 1.45D, 2.0D, 0.5D, 0.55D, 0.5D)));
        STEPS.add(new Step(40, () -> shot("block.png")));
        STEPS.add(new Step(5, () -> eye(-1.2D, 1.0D, 2.6D, 0.5D, 0.5D, 0.5D)));
        STEPS.add(new Step(30, () -> shot("block_front.png")));
        STEPS.add(new Step(5, () -> eye(0.5D, 3.0D, 1.8D, 0.5D, 0.6D, 0.5D)));
        STEPS.add(new Step(30, () -> shot("block_top.png")));
        STEPS.add(new Step(5, () -> eye(4.5D, 2.6D, 4.0D, 0.5D, 0.3D, 0.0D)));
        STEPS.add(new Step(30, () -> shot("garden.png")));
        // the GUI, first at scale 3: the opening frames, then settled
        STEPS.add(new Step(5, () -> guiScale(3)));
        STEPS.add(new Step(5, () -> eye(2.3D, 1.45D, 2.0D, 0.5D, 0.55D, 0.5D)));
        STEPS.add(new Step(20, AutoShot::mouseAway));
        STEPS.add(new Step(5, AutoShot::open));
        STEPS.add(new Step(2, () -> shot("gui_open_1.png")));
        STEPS.add(new Step(3, () -> shot("gui_open_2.png")));
        STEPS.add(new Step(3, () -> shot("gui_open_3.png")));
        STEPS.add(new Step(4, () -> shot("gui_open_4.png")));
        STEPS.add(new Step(6, () -> shot("gui_open_5.png")));
        STEPS.add(new Step(50, () -> shot("gui.png")));
        STEPS.add(new Step(37, () -> shot("gui_later.png")));
        STEPS.add(new Step(5, () -> mouseAtGui(128, 60)));
        STEPS.add(new Step(10, () -> shot("gui_heart.png")));
        STEPS.add(new Step(5, () -> clickGui(-26, 70)));
        STEPS.add(new Step(12, () -> shot("gui_keeper.png")));
        STEPS.add(new Step(5, AutoShot::mouseAway));
        // the bud stays open while the server thinks the GUI is open: hide the screen only
        STEPS.add(new Step(40, () -> Minecraft.getInstance().setScreen(null)));
        STEPS.add(new Step(5, () -> Minecraft.getInstance().options.hideGui = true));
        STEPS.add(new Step(30, () -> shot("block_open.png")));
        STEPS.add(new Step(5, () -> eye(0.5D, 3.0D, 1.8D, 0.5D, 0.6D, 0.5D)));
        STEPS.add(new Step(20, () -> shot("block_open_top.png")));
        STEPS.add(new Step(5, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.closeContainer();
        }));
        STEPS.add(new Step(8, () -> eye(2.3D, 1.45D, 2.0D, 0.5D, 0.55D, 0.5D)));
        STEPS.add(new Step(4, () -> shot("block_closing.png")));
        // the GUI at scale 4 (a 1080p screen's auto scale): does it all fit?
        STEPS.add(new Step(30, () -> guiScale(4)));
        STEPS.add(new Step(5, AutoShot::open));
        STEPS.add(new Step(40, () -> shot("gui_scale4.png")));
        // and closing (Esc): the panel folds back into the heart
        STEPS.add(new Step(2, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.screen != null) mc.screen.onClose();
        }));
        STEPS.add(new Step(2, () -> shot("gui_closing.png")));
        // the greenhouse's pages in the Lexica Botania
        STEPS.add(new Step(10, AutoShot::unlockLexicon));
        STEPS.add(new Step(30, () -> openLexicon("generating_flowers/managarden_greenhouse", 0)));
        STEPS.add(new Step(30, () -> shot("lexicon.png")));
        STEPS.add(new Step(5, () -> openLexicon("generating_flowers/managarden_greenhouse", 4)));
        STEPS.add(new Step(30, () -> shot("lexicon_recipes.png")));
        STEPS.add(new Step(5, () -> openLexicon("generating_flowers/managarden_upgrades", 2)));
        STEPS.add(new Step(30, () -> shot("lexicon_upgrades.png")));
        // the Wand of the Forest's HUD over the greenhouse
        STEPS.add(new Step(5, () -> Minecraft.getInstance().setScreen(null)));
        STEPS.add(new Step(5, () -> eye(2.3D, 1.45D, 2.0D, 0.5D, 0.55D, 0.5D)));
        STEPS.add(new Step(30, () -> shot("wand_hud.png")));
        // and all of it in Ukrainian: the plate, the keeper's words, the lexicon
        STEPS.add(new Step(5, () -> language("uk_ua")));
        STEPS.add(new Step(40, AutoShot::open));
        STEPS.add(new Step(40, () -> clickGui(-26, 70)));
        STEPS.add(new Step(12, () -> shot("gui_uk.png")));
        STEPS.add(new Step(5, () -> openLexicon("generating_flowers/managarden_greenhouse", 0)));
        STEPS.add(new Step(30, () -> shot("lexicon_uk.png")));
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
                ManaGarden.LOGGER.error("AutoShot step {} failed", step, e);
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

    private static MinecraftServer server() {
        return Minecraft.getInstance().getSingleplayerServer();
    }

    private static Item item(String namespace, String path) {
        Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(namespace, path));
        return item == null ? net.minecraft.world.item.Items.AIR : item;
    }

    private static Block block(String namespace, String path) {
        Block block = ForgeRegistries.BLOCKS.getValue(new ResourceLocation(namespace, path));
        return block == null ? Blocks.AIR : block;
    }

    /** A greenhouse full of flowers and upgrades, some mana in it, in a little garden with a mana pool. */
    private static void setUp() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setDayTime(5000L);
            level.setBlock(POS, ManaGarden.GREENHOUSE.get().defaultBlockState(), 3);
            if (level.getBlockEntity(POS) instanceof GreenhouseBlockEntity be) {
                for (int i = 0; i < FLOWERS.length; i++) {
                    be.items().setStackInSlot(GreenhouseBlockEntity.FLOWER_START + i, new ItemStack(item("botania", FLOWERS[i])));
                }
                be.items().setStackInSlot(GreenhouseBlockEntity.UPGRADE_START, new ItemStack(ManaGarden.SPEED_UPGRADE.get(), 4));
                be.items().setStackInSlot(GreenhouseBlockEntity.UPGRADE_START + 1, new ItemStack(ManaGarden.CAPACITY_UPGRADE.get(), 2));
                be.items().setStackInSlot(GreenhouseBlockEntity.UPGRADE_START + 2, new ItemStack(ManaGarden.LUCK_UPGRADE.get(), 6));
                be.items().setStackInSlot(GreenhouseBlockEntity.UPGRADE_START + 3, new ItemStack(ManaGarden.YIELD_UPGRADE.get(), 3));
                CompoundTag tag = be.saveWithoutMetadata();
                tag.putInt(GreenhouseBlockEntity.TAG_MANA, 420_000);
                be.load(tag);
                be.setChanged();
            }
            // a little garden: a mana pool, livingrock path, mystical flowers and grass
            level.setBlock(POS.offset(3, 0, -1), block("botania", "mana_pool").defaultBlockState(), 3);
            String[] colours = {"white", "pink", "light_blue", "magenta", "yellow", "lime", "cyan", "purple"};
            int k = 0;
            for (int x = -4; x <= 5; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (Math.abs(x) <= 1 && Math.abs(z) <= 1 || x == 3 && z == -1) continue;
                    // keep the cameras' spots clear
                    if (Math.hypot(x - 2.3D, z - 2.0D) < 1.8D || Math.hypot(x + 1.2D, z - 2.6D) < 1.8D) continue;
                    long h = (x * 73856093L) ^ (z * 19349663L);
                    if (Math.floorMod(h, 5) == 0) {
                        level.setBlock(POS.offset(x, 0, z), block("botania", colours[k++ % colours.length] + "_mystical_flower").defaultBlockState(), 3);
                    } else if (Math.floorMod(h, 3) == 0) {
                        level.setBlock(POS.offset(x, 0, z), Blocks.GRASS.defaultBlockState(), 3);
                    }
                }
            }
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            // the mod's items in the inventory, to see their icons in the GUI
            ItemStack stored = new ItemStack(ManaGarden.GREENHOUSE_ITEM.get());
            CompoundTag entity = new CompoundTag();
            entity.putInt(GreenhouseBlockEntity.TAG_MANA, 125_000);
            net.minecraft.world.item.BlockItem.setBlockEntityData(stored, ManaGarden.GREENHOUSE_BE.get(), entity);
            ItemStack[] kit = {stored, new ItemStack(ManaGarden.GREENHOUSE_HEART.get()), new ItemStack(ManaGarden.UPGRADE_BASE.get(), 12),
                    new ItemStack(ManaGarden.SPEED_UPGRADE.get(), 2), new ItemStack(ManaGarden.CAPACITY_UPGRADE.get()),
                    new ItemStack(ManaGarden.LUCK_UPGRADE.get(), 3), new ItemStack(ManaGarden.YIELD_UPGRADE.get()),
                    new ItemStack(item("botania", "endoflame"), 4), new ItemStack(item("botania", "mana_tablet"))};
            for (int i = 0; i < kit.length; i++) p.getInventory().setItem(9 + i, kit[i]);
            p.getInventory().setItem(0, new ItemStack(item("botania", "twig_wand")));
            p.getInventory().selected = 0;
        });
        mc.getTutorial().setStep(TutorialSteps.NONE);
        mc.options.hideGui = true;
        mc.options.bobView().set(false);
        mc.options.fov().set(70);
    }

    /** Camera at x, y, z (blocks from the greenhouse's corner) looking at tx, ty, tz. */
    private static void eye(double x, double y, double z, double tx, double ty, double tz) {
        double dx = tx - x, dy = ty - y, dz = tz - z;
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.connection.teleport(POS.getX() + x, POS.getY() + y - 1.62D, POS.getZ() + z, yaw, pitch);
        });
    }

    private static void open() {
        Minecraft.getInstance().options.hideGui = false;
        // the advancement and recipe toasts of the items given at the start would cover the view
        Minecraft.getInstance().getToasts().clear();
        MinecraftServer server = server();
        server.execute(() -> {
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            if (server.overworld().getBlockEntity(POS) instanceof GreenhouseBlockEntity be) {
                // an empty mana tablet to charge, so the gauge fills while we look
                if (be.items().getStackInSlot(GreenhouseBlockEntity.CHARGE_SLOT).isEmpty()) {
                    be.items().setStackInSlot(GreenhouseBlockEntity.CHARGE_SLOT, new ItemStack(item("botania", "mana_tablet")));
                }
                NetworkHooks.openScreen(p, be, POS);
            }
        });
    }

    /** The lexicon shows an entry once its advancement is earned: earn the greenhouse's. */
    private static void unlockLexicon() {
        MinecraftServer server = server();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "advancement grant @a until botania:main/runic_altar_pickup"));
    }

    /** Opens the Lexica Botania at an entry's page (through Patchouli's API, which only runs here). */
    private static void openLexicon(String entry, int page) {
        Minecraft.getInstance().getToasts().clear();
        try {
            Class<?> api = Class.forName("vazkii.patchouli.api.PatchouliAPI");
            Object instance = api.getMethod("get").invoke(null);
            Class<?> type = Class.forName("vazkii.patchouli.api.PatchouliAPI$IPatchouliAPI");
            type.getMethod("openBookEntry", ResourceLocation.class, ResourceLocation.class, int.class)
                    .invoke(instance, new ResourceLocation("botania", "lexicon"), new ResourceLocation("botania", entry), page);
        } catch (ReflectiveOperationException e) {
            ManaGarden.LOGGER.warn("AutoShot: can't open the lexicon", e);
        }
    }

    private static void language(String code) {
        Minecraft mc = Minecraft.getInstance();
        mc.setScreen(null);
        mc.getLanguageManager().setSelected(code);
        mc.options.languageCode = code;
        mc.reloadResourcePacks();
    }

    private static void guiScale(int scale) {
        Minecraft mc = Minecraft.getInstance();
        mc.options.guiScale().set(scale);
        mc.resizeDisplay();
    }

    /** Mouse away from the GUI, so no tooltip covers it. */
    private static void mouseAway() {
        setMouse(4.0D, 4.0D);
    }

    /**
     * Puts the mouse at a window position: the virtual screen doesn't report the cursor moving,
     * so the game's own record of it is set too.
     */
    private static void setMouse(double x, double y) {
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
            ManaGarden.LOGGER.warn("AutoShot: can't move the mouse", e);
        }
    }

    /** Mouse over a point of the GUI (menu coordinates). */
    private static void mouseAtGui(int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        double scale = mc.getWindow().getGuiScale();
        setMouse((screen.getGuiLeft() + x + 0.5D) * scale, (screen.getGuiTop() + y + 0.5D) * scale);
    }

    private static void clickGui(int x, int y) {
        Minecraft mc = Minecraft.getInstance();
        if (!(mc.screen instanceof AbstractContainerScreen<?> screen)) return;
        mouseAtGui(x, y);
        screen.mouseClicked(screen.getGuiLeft() + x + 0.5D, screen.getGuiTop() + y + 0.5D, 0);
    }

    private static void shot(String name) {
        Minecraft mc = Minecraft.getInstance();
        Screenshot.grab(mc.gameDirectory, name, mc.getMainRenderTarget(), msg -> {
        });
    }

    private AutoShot() {
    }
}
