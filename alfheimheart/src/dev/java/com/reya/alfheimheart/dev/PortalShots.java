package com.reya.alfheimheart.dev;

import static com.reya.alfheimheart.dev.AutoShot.*;

import java.util.List;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.portal.PortalBlock;
import com.reya.alfheimheart.portal.PortalBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Elven Portal's scene: the gate on a livingrock step with things for the elves in its slots, shut,
 * opening as a creative mana pool comes behind it, trading; its GUI opening, settled, trading, with its
 * tooltips, an item sent through, the redstone setting, closing; its lexicon pages and the wand's HUD.
 */
final class PortalShots {
    static final BlockPos POS = new BlockPos(40, -60, 0);
    private static final String ENTRY = "alfhomancy/alfheimheart_portal";

    static void steps(List<Step> s) {
        s.add(new Step(10, PortalShots::setUp));
        s.add(new Step(40, () -> eye(POS, 1.9D, 1.3D, 2.3D, 0.5D, 0.55D, 0.5D)));
        s.add(new Step(40, () -> shot("portal_block_closed.png")));
        s.add(new Step(2, PortalShots::placePool));
        s.add(new Step(8, () -> shot("portal_block_opening_1.png")));
        s.add(new Step(5, () -> shot("portal_block_opening_2.png")));
        s.add(new Step(40, () -> shot("portal_block.png")));
        s.add(new Step(5, () -> eye(POS, 0.5D, 0.75D, 1.75D, 0.5D, 0.56D, 0.5D)));
        s.add(new Step(21, () -> shot("portal_block_front.png")));
        s.add(new Step(5, () -> eye(POS, 4.2D, 2.6D, 4.4D, 0.5D, 0.3D, 0.0D)));
        s.add(new Step(30, () -> shot("portal_scene.png")));
        // the GUI: opening, settled, trading
        s.add(new Step(5, () -> eye(POS, 1.9D, 1.3D, 2.3D, 0.5D, 0.55D, 0.5D)));
        s.add(new Step(20, AutoShot::mouseAway));
        s.add(new Step(5, PortalShots::open));
        s.add(new Step(2, () -> shot("portal_gui_open_1.png")));
        s.add(new Step(3, () -> shot("portal_gui_open_2.png")));
        s.add(new Step(3, () -> shot("portal_gui_open_3.png")));
        s.add(new Step(40, () -> shot("portal_gui.png")));
        s.add(new Step(4, () -> shot("portal_gui_trade.png")));
        // tooltips
        s.add(new Step(5, () -> mouseAtGui(120, 60)));
        s.add(new Step(10, () -> shot("portal_gui_tip.png")));
        s.add(new Step(5, () -> mouseAtGui(110, 111)));
        s.add(new Step(10, () -> shot("portal_gui_mana.png")));
        // an item on the cursor clicked on the portal goes through to the elves
        s.add(new Step(5, () -> carry(new ItemStack(item("botania", "livingwood"), 24))));
        s.add(new Step(10, () -> mouseAtGui(126, 66)));
        s.add(new Step(2, () -> clickGui(126, 66)));
        s.add(new Step(8, () -> shot("portal_gui_sent.png")));
        // "works only with a signal", and there is none: the portal closes
        s.add(new Step(10, () -> clickGui(26, 110)));
        s.add(new Step(70, () -> shot("portal_gui_redstone.png")));
        s.add(new Step(2, () -> clickGui(26, 110)));
        s.add(new Step(2, () -> clickGui(26, 110)));
        s.add(new Step(5, AutoShot::mouseAway));
        // closing by the close button
        s.add(new Step(40, () -> clickGui(237, 0)));
        s.add(new Step(2, () -> shot("portal_gui_closing.png")));
        // the lexicon, the wand's HUD
        s.add(new Step(10, () -> grant("botania:main/elf_portal_open")));
        s.add(new Step(30, () -> openLexicon(ENTRY, 0)));
        s.add(new Step(30, () -> shot("portal_lexicon.png")));
        s.add(new Step(5, () -> openLexicon(ENTRY, 4)));
        s.add(new Step(30, () -> shot("portal_lexicon_trades.png")));
        s.add(new Step(5, AutoShot::closeScreen));
        s.add(new Step(5, () -> Minecraft.getInstance().options.hideGui = false));
        s.add(new Step(5, () -> eye(POS, 0.5D, 1.6D, 2.4D, 0.5D, 0.45D, 0.5D)));
        s.add(new Step(30, () -> shot("portal_wand_hud.png")));
    }

    static void ukSteps(List<Step> s) {
        s.add(new Step(5, () -> eye(POS, 1.9D, 1.3D, 2.3D, 0.5D, 0.55D, 0.5D)));
        s.add(new Step(20, PortalShots::open));
        s.add(new Step(40, () -> mouseAtGui(120, 60)));
        s.add(new Step(10, () -> shot("portal_gui_uk.png")));
        s.add(new Step(5, () -> openLexicon(ENTRY, 0)));
        s.add(new Step(30, () -> shot("portal_lexicon_uk.png")));
        s.add(new Step(5, AutoShot::closeScreen));
    }

    /** The portal facing south on a livingrock step, a few flowers round it, things for the elves in its slots. */
    private static void setUp() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            for (int x = -1; x <= 1; x++) {
                for (int z = -1; z <= 1; z++) {
                    level.setBlock(POS.offset(x, -1, z), block("botania", "livingrock_bricks").defaultBlockState(), 3);
                }
            }
            level.setBlock(POS, AlfheimHeart.PORTAL.get().defaultBlockState().setValue(PortalBlock.FACING, Direction.SOUTH), 3);
            String[] colours = {"white", "pink", "light_blue", "magenta", "yellow", "lime", "cyan", "purple"};
            int k = 0;
            for (int x = -4; x <= 4; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (Math.abs(x) <= 1 && Math.abs(z) <= 1 || Math.abs(x) <= 1 && z > 0) continue;
                    if (Math.hypot(x - 1.9D, z - 2.3D) < 1.6D) continue;
                    long h = (x * 73856093L) ^ (z * 19349663L);
                    if (Math.floorMod(h, 4) == 0) {
                        level.setBlock(POS.offset(x, 0, z), block("botania", colours[k++ % colours.length] + "_mystical_flower").defaultBlockState(), 3);
                    } else if (Math.floorMod(h, 3) == 0) {
                        level.setBlock(POS.offset(x, 0, z), Blocks.GRASS.defaultBlockState(), 3);
                    }
                }
            }
            fill(level);
        });
        worldView();
    }

    /** A creative mana pool behind the gate: the portal draws mana from it and opens. */
    private static void placePool() {
        MinecraftServer server = server();
        server.execute(() -> server.overworld().setBlock(POS.north(), block("botania", "creative_pool").defaultBlockState(), 3));
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

    private static void open() {
        beforeGui();
        MinecraftServer server = server();
        server.execute(() -> {
            fill(server.overworld());
            if (server.overworld().getBlockEntity(POS) instanceof PortalBlockEntity be) NetworkHooks.openScreen(player(), be, POS);
        });
    }

    /** Puts a stack on the cursor in the open GUI, on the server and in the client's copy of the menu. */
    private static void carry(ItemStack stack) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.containerMenu.setCarried(stack.copy());
        MinecraftServer server = server();
        server.execute(() -> player().containerMenu.setCarried(stack.copy()));
    }

    private PortalShots() {
    }
}
