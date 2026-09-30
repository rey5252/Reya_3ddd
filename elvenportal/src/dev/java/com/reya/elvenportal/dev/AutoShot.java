package com.reya.elvenportal.dev;

import java.util.ArrayList;
import java.util.List;

import com.reya.elvenportal.ElvenPortal;
import com.reya.elvenportal.PortalBlock;
import com.reya.elvenportal.PortalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
 * Dev-only: with ELVENPORTAL_AUTOSHOT=true the client makes a flat world, sets an Elven Portal on a
 * livingrock step with a creative mana pool behind it and things for the elves in its slots, photographs it
 * trading, opens its GUI and photographs the opening, the settled GUI, its tooltips, an item sent through the
 * portal, the redstone setting, the closing, the lexicon pages and the Wand of the Forest's HUD, then the
 * GUI and the lexicon in Ukrainian, and quits.
 */
@Mod.EventBusSubscriber(modid = ElvenPortal.MODID, value = Dist.CLIENT)
public final class AutoShot {
    private static final boolean ON = "true".equals(System.getenv("ELVENPORTAL_AUTOSHOT"));
    private static final int BASE = -60;
    private static final BlockPos POS = new BlockPos(0, BASE, 0);
    private static final String ENTRY = "alfhomancy/elvenportal_portal";
    private static final List<Step> STEPS = new ArrayList<>();
    private static int ticks, step, since;
    private static boolean worldAsked;

    private record Step(int delay, Runnable action) {
    }

    static {
        STEPS.add(new Step(80, AutoShot::setUp));
        // the portal in the world: opening as the mana comes in, then trading
        STEPS.add(new Step(40, () -> eye(2.0D, 1.35D, 2.6D, 0.5D, 0.5D, 0.5D)));
        STEPS.add(new Step(40, () -> shot("block.png")));
        STEPS.add(new Step(5, () -> eye(0.5D, 0.95D, 2.1D, 0.5D, 0.5D, 0.5D)));
        STEPS.add(new Step(21, () -> shot("block_front.png")));
        STEPS.add(new Step(7, () -> shot("block_trading.png")));
        STEPS.add(new Step(5, () -> eye(-1.9D, 1.6D, -1.4D, 0.5D, 0.45D, 0.5D)));
        STEPS.add(new Step(30, () -> shot("block_back.png")));
        STEPS.add(new Step(5, () -> eye(4.2D, 2.6D, 4.4D, 0.5D, 0.3D, 0.0D)));
        STEPS.add(new Step(30, () -> shot("scene.png")));
        // the GUI at scale 3: the opening frames, then settled, with trades flying
        STEPS.add(new Step(5, () -> guiScale(3)));
        STEPS.add(new Step(5, () -> eye(2.0D, 1.35D, 2.6D, 0.5D, 0.5D, 0.5D)));
        STEPS.add(new Step(20, AutoShot::mouseAway));
        STEPS.add(new Step(5, AutoShot::open));
        STEPS.add(new Step(2, () -> shot("gui_open_1.png")));
        STEPS.add(new Step(2, () -> shot("gui_open_2.png")));
        STEPS.add(new Step(2, () -> shot("gui_open_3.png")));
        STEPS.add(new Step(3, () -> shot("gui_open_4.png")));
        STEPS.add(new Step(3, () -> shot("gui_open_5.png")));
        STEPS.add(new Step(40, () -> shot("gui.png")));
        STEPS.add(new Step(3, () -> shot("gui_trade_1.png")));
        STEPS.add(new Step(3, () -> shot("gui_trade_2.png")));
        STEPS.add(new Step(3, () -> shot("gui_trade_3.png")));
        // tooltips: the portal, the mana, an empty output slot, the close button
        STEPS.add(new Step(5, () -> mouseAtGui(120, 60)));
        STEPS.add(new Step(10, () -> shot("gui_portal.png")));
        STEPS.add(new Step(5, () -> mouseAtGui(110, 111)));
        STEPS.add(new Step(10, () -> shot("gui_mana.png")));
        STEPS.add(new Step(5, () -> mouseAtGui(212, 74)));
        STEPS.add(new Step(10, () -> shot("gui_slot.png")));
        STEPS.add(new Step(5, () -> mouseAtGui(237, 0)));
        STEPS.add(new Step(10, () -> shot("gui_close.png")));
        // an item on the cursor clicked on the portal goes through to the elves
        STEPS.add(new Step(5, () -> carry(new ItemStack(item("botania", "livingwood"), 24))));
        STEPS.add(new Step(10, () -> mouseAtGui(126, 66)));
        STEPS.add(new Step(10, () -> shot("gui_carry.png")));
        STEPS.add(new Step(2, () -> clickGui(126, 66)));
        STEPS.add(new Step(8, () -> shot("gui_sent.png")));
        // the redstone button: "only with a signal", and there is none, so the portal closes
        STEPS.add(new Step(10, () -> clickGui(26, 110)));
        STEPS.add(new Step(70, () -> shot("gui_redstone.png")));
        STEPS.add(new Step(2, () -> clickGui(26, 110)));
        STEPS.add(new Step(2, () -> clickGui(26, 110)));
        STEPS.add(new Step(5, AutoShot::mouseAway));
        STEPS.add(new Step(40, () -> shot("gui_reopened.png")));
        // the GUI at scale 4 (a 1080p screen's auto scale): does it all fit?
        STEPS.add(new Step(5, () -> Minecraft.getInstance().setScreen(null)));
        STEPS.add(new Step(5, () -> guiScale(4)));
        STEPS.add(new Step(5, AutoShot::open));
        STEPS.add(new Step(40, () -> shot("gui_scale4.png")));
        // and closing, by a click on the red X in the corner: the panel folds back into the swirl
        STEPS.add(new Step(2, () -> clickGui(237, 0)));
        STEPS.add(new Step(2, () -> shot("gui_closing.png")));
        // the portal's pages in the Lexica Botania
        STEPS.add(new Step(10, AutoShot::unlockLexicon));
        STEPS.add(new Step(30, () -> openLexicon(ENTRY, 0)));
        STEPS.add(new Step(30, () -> shot("lexicon.png")));
        STEPS.add(new Step(5, () -> openLexicon(ENTRY, 2)));
        STEPS.add(new Step(30, () -> shot("lexicon_recipe.png")));
        STEPS.add(new Step(5, () -> openLexicon(ENTRY, 4)));
        STEPS.add(new Step(30, () -> shot("lexicon_trades.png")));
        // the Wand of the Forest's HUD over the portal
        STEPS.add(new Step(5, () -> Minecraft.getInstance().setScreen(null)));
        STEPS.add(new Step(5, () -> Minecraft.getInstance().options.hideGui = false));
        STEPS.add(new Step(5, () -> eye(0.5D, 1.6D, 2.4D, 0.5D, 0.45D, 0.5D)));
        STEPS.add(new Step(30, () -> shot("wand_hud.png")));
        // and in Ukrainian: the scroll, the tooltips, the lexicon
        STEPS.add(new Step(5, () -> language("uk_ua")));
        STEPS.add(new Step(40, () -> guiScale(3)));
        STEPS.add(new Step(5, AutoShot::open));
        STEPS.add(new Step(40, () -> mouseAtGui(120, 60)));
        STEPS.add(new Step(10, () -> shot("gui_uk.png")));
        STEPS.add(new Step(5, () -> openLexicon(ENTRY, 0)));
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
                ElvenPortal.LOGGER.error("AutoShot step {} failed", step, e);
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

    private static ItemStack stack(String namespace, String path, int count) {
        return new ItemStack(item(namespace, path), count);
    }

    /**
     * The portal facing south on a livingrock step, a creative mana pool behind it, a few flowers round it;
     * things for the elves in eight of its input slots (the ninth kept free for the item sent through the
     * GUI) and some trades already in its output slots.
     */
    private static void setUp() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setDayTime(6000L);
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    level.setBlock(POS.offset(x, -1, z), block("botania", "livingrock_bricks").defaultBlockState(), 3);
                }
            }
            level.setBlock(POS, ElvenPortal.PORTAL.get().defaultBlockState().setValue(PortalBlock.FACING, Direction.SOUTH), 3);
            level.setBlock(POS.north(), block("botania", "creative_pool").defaultBlockState(), 3);
            String[] colours = {"white", "pink", "light_blue", "magenta", "yellow", "lime", "cyan", "purple"};
            int k = 0;
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (Math.abs(x) <= 1 && Math.abs(z) <= 1) continue;
                    // keep the cameras' spots clear
                    if (Math.hypot(x - 2.0D, z - 2.6D) < 1.6D || Math.hypot(x + 1.9D, z + 1.4D) < 1.6D || Math.abs(x) <= 1 && z > 0) continue;
                    long h = (x * 73856093L) ^ (z * 19349663L);
                    if (Math.floorMod(h, 4) == 0) {
                        level.setBlock(POS.offset(x, 0, z), block("botania", colours[k++ % colours.length] + "_mystical_flower").defaultBlockState(), 3);
                    } else if (Math.floorMod(h, 3) == 0) {
                        level.setBlock(POS.offset(x, 0, z), Blocks.GRASS.defaultBlockState(), 3);
                    }
                }
            }
            fill(level);
            ServerPlayer p = server.getPlayerList().getPlayers().get(0);
            p.getAbilities().flying = true;
            p.onUpdateAbilities();
            // the mod's item and a few things the elves take in the inventory, to see their icons in the GUI
            ItemStack stored = new ItemStack(ElvenPortal.PORTAL_ITEM.get());
            CompoundTag entity = new CompoundTag();
            entity.putInt(PortalBlockEntity.TAG_MANA, 60_000);
            net.minecraft.world.item.BlockItem.setBlockEntityData(stored, ElvenPortal.PORTAL_BE.get(), entity);
            ItemStack[] kit = {stored, stack("botania", "livingwood_log", 32), stack("botania", "manasteel_ingot", 16),
                    stack("minecraft", "iron_ore", 20), stack("minecraft", "deepslate_diamond_ore", 3), stack("botania", "mana_pearl", 5),
                    stack("minecraft", "nether_quartz_ore", 12), stack("botania", "mana_diamond", 2), stack("minecraft", "iron_ingot", 9)};
            for (int i = 0; i < kit.length; i++) p.getInventory().setItem(9 + i, kit[i]);
            p.getInventory().setItem(0, new ItemStack(item("botania", "twig_wand")));
            p.getInventory().selected = 0;
        });
        mc.getTutorial().setStep(TutorialSteps.NONE);
        mc.options.hideGui = true;
        mc.options.bobView().set(false);
        mc.options.fov().set(70);
    }

    /** Refills the portal's input slots (the trades eat them) and puts a few earlier trades in its output slots. */
    private static void fill(ServerLevel level) {
        if (!(level.getBlockEntity(POS) instanceof PortalBlockEntity be)) return;
        ItemStack[] inputs = {stack("botania", "livingwood_log", 64), stack("botania", "manasteel_ingot", 64),
                stack("minecraft", "iron_ore", 64), stack("minecraft", "gold_ore", 48), stack("botania", "mana_diamond", 32),
                stack("botania", "mana_pearl", 32), stack("minecraft", "quartz", 64), stack("minecraft", "lapis_ore", 40)};
        for (int i = 0; i < inputs.length; i++) be.items().setStackInSlot(PortalBlockEntity.INPUT_START + i, inputs[i]);
        be.items().setStackInSlot(PortalBlockEntity.INPUT_START + 8, ItemStack.EMPTY);
        ItemStack[] outputs = {stack("botania", "dreamwood_log", 12), stack("botania", "elementium_ingot", 5),
                stack("minecraft", "raw_iron", 14), stack("botania", "dragonstone", 2), stack("botania", "pixie_dust", 7),
                stack("minecraft", "raw_gold", 6)};
        for (int i = 0; i < PortalBlockEntity.OUTPUTS; i++) {
            be.items().setStackInSlot(PortalBlockEntity.OUTPUT_START + i, i < outputs.length ? outputs[i] : ItemStack.EMPTY);
        }
        be.setChanged();
    }

    /** Camera at x, y, z (blocks from the portal's corner) looking at tx, ty, tz. */
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
            fill(server.overworld());
            if (server.overworld().getBlockEntity(POS) instanceof PortalBlockEntity be) NetworkHooks.openScreen(p, be, POS);
        });
    }

    /** Puts a stack on the cursor in the open GUI, on the server and in the client's copy of the menu. */
    private static void carry(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.containerMenu.setCarried(stack.copy());
        MinecraftServer server = server();
        server.execute(() -> server.getPlayerList().getPlayers().get(0).containerMenu.setCarried(stack.copy()));
    }

    /** The lexicon shows an entry once its advancement is earned: earn the portal's. */
    private static void unlockLexicon() {
        MinecraftServer server = server();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSuppressedOutput(),
                "advancement grant @a until botania:main/elf_portal_open"));
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
            ElvenPortal.LOGGER.warn("AutoShot: can't open the lexicon", e);
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
            ElvenPortal.LOGGER.warn("AutoShot: can't move the mouse", e);
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
