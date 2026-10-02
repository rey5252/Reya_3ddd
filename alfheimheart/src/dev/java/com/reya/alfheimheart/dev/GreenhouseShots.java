package com.reya.alfheimheart.dev;

import static com.reya.alfheimheart.dev.AutoShot.*;

import java.util.List;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.greenhouse.GreenhouseBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.network.NetworkHooks;

/**
 * The Mana Greenhouse's scene: full of Botania flowers in a little garden. Photographed closed and with its
 * bud open, its GUI opening, settled, with a tooltip and the keeper talking, closing, its lexicon pages and
 * the Wand of the Forest's HUD.
 */
final class GreenhouseShots {
    static final BlockPos POS = new BlockPos(0, -60, 0);
    private static final String[] FLOWERS = {"endoflame", "hydroangeas", "thermalily", "rosa_arcana", "munchdew",
            "kekimurus", "gourmaryllis", "spectrolus"};
    private static final String ENTRY = "generating_flowers/alfheimheart_greenhouse";

    static void steps(List<Step> s) {
        s.add(new Step(80, GreenhouseShots::setUp));
        s.add(new Step(60, () -> eye(POS, 2.3D, 1.45D, 2.0D, 0.5D, 0.55D, 0.5D)));
        s.add(new Step(40, () -> shot("greenhouse_block.png")));
        s.add(new Step(5, () -> eye(POS, 4.5D, 2.6D, 4.0D, 0.5D, 0.3D, 0.0D)));
        s.add(new Step(30, () -> shot("greenhouse_garden.png")));
        // the GUI at scale 3: the opening frames, then settled
        s.add(new Step(5, () -> guiScale(3)));
        s.add(new Step(5, () -> eye(POS, 2.3D, 1.45D, 2.0D, 0.5D, 0.55D, 0.5D)));
        s.add(new Step(20, AutoShot::mouseAway));
        s.add(new Step(5, GreenhouseShots::open));
        s.add(new Step(3, () -> shot("greenhouse_gui_open_1.png")));
        s.add(new Step(4, () -> shot("greenhouse_gui_open_2.png")));
        s.add(new Step(4, () -> shot("greenhouse_gui_open_3.png")));
        s.add(new Step(50, () -> shot("greenhouse_gui.png")));
        s.add(new Step(5, () -> mouseAtGui(128, 60)));
        s.add(new Step(10, () -> shot("greenhouse_gui_heart.png")));
        s.add(new Step(5, () -> mouseAtGui(253, 0)));
        s.add(new Step(10, () -> shot("greenhouse_gui_close.png")));
        s.add(new Step(5, () -> clickGui(-26, 70)));
        s.add(new Step(12, () -> shot("greenhouse_gui_keeper.png")));
        s.add(new Step(5, AutoShot::mouseAway));
        // the bud stays open while the server thinks the GUI is open: hide the screen only
        s.add(new Step(40, AutoShot::closeScreen));
        s.add(new Step(5, () -> Minecraft.getInstance().options.hideGui = true));
        s.add(new Step(30, () -> shot("greenhouse_block_open.png")));
        s.add(new Step(5, () -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) mc.player.closeContainer();
        }));
        // the GUI at scale 4 (a 1080p screen's auto scale), then closing by the close button
        s.add(new Step(30, () -> guiScale(4)));
        s.add(new Step(5, GreenhouseShots::open));
        s.add(new Step(40, () -> shot("greenhouse_gui_scale4.png")));
        s.add(new Step(2, () -> clickGui(253, 0)));
        s.add(new Step(2, () -> shot("greenhouse_gui_closing.png")));
        s.add(new Step(20, () -> guiScale(3)));
        // the lexicon
        s.add(new Step(10, () -> grant("botania:main/runic_altar_pickup")));
        s.add(new Step(30, () -> openLexicon(ENTRY, 0)));
        s.add(new Step(30, () -> shot("greenhouse_lexicon.png")));
        s.add(new Step(5, () -> openLexicon("generating_flowers/alfheimheart_upgrades", 2)));
        s.add(new Step(30, () -> shot("greenhouse_lexicon_upgrades.png")));
        s.add(new Step(5, AutoShot::closeScreen));
        // the Wand of the Forest's HUD (a camera this high keeps the crosshair on the block)
        s.add(new Step(5, () -> Minecraft.getInstance().options.hideGui = false));
        s.add(new Step(5, () -> eye(POS, 2.3D, 1.8D, 2.0D, 0.5D, 0.45D, 0.5D)));
        s.add(new Step(30, () -> shot("greenhouse_wand_hud.png")));
    }

    static void ukSteps(List<Step> s) {
        s.add(new Step(5, () -> eye(POS, 2.3D, 1.45D, 2.0D, 0.5D, 0.55D, 0.5D)));
        s.add(new Step(20, GreenhouseShots::open));
        s.add(new Step(40, () -> clickGui(-26, 70)));
        s.add(new Step(12, () -> shot("greenhouse_gui_uk.png")));
        s.add(new Step(5, () -> openLexicon(ENTRY, 0)));
        s.add(new Step(30, () -> shot("greenhouse_lexicon_uk.png")));
        s.add(new Step(5, AutoShot::closeScreen));
    }

    /** A greenhouse full of flowers and upgrades, some mana in it, in a little garden with a mana pool. */
    private static void setUp() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            level.setDayTime(5000L);
            level.setBlock(POS, AlfheimHeart.GREENHOUSE.get().defaultBlockState(), 3);
            if (level.getBlockEntity(POS) instanceof GreenhouseBlockEntity be) {
                for (int i = 0; i < FLOWERS.length; i++) {
                    be.items().setStackInSlot(GreenhouseBlockEntity.FLOWER_START + i, new ItemStack(item("botania", FLOWERS[i])));
                }
                be.items().setStackInSlot(GreenhouseBlockEntity.UPGRADE_START, new ItemStack(AlfheimHeart.SPEED_UPGRADE.get(), 4));
                be.items().setStackInSlot(GreenhouseBlockEntity.UPGRADE_START + 1, new ItemStack(AlfheimHeart.CAPACITY_UPGRADE.get(), 2));
                be.items().setStackInSlot(GreenhouseBlockEntity.UPGRADE_START + 2, new ItemStack(AlfheimHeart.LUCK_UPGRADE.get(), 6));
                be.items().setStackInSlot(GreenhouseBlockEntity.UPGRADE_START + 3, new ItemStack(AlfheimHeart.YIELD_UPGRADE.get(), 3));
                CompoundTag tag = be.saveWithoutMetadata();
                tag.putInt(GreenhouseBlockEntity.TAG_MANA, 420_000);
                be.load(tag);
                be.setChanged();
            }
            // a little garden: a mana pool, mystical flowers and grass
            level.setBlock(POS.offset(3, 0, -1), block("botania", "mana_pool").defaultBlockState(), 3);
            String[] colours = {"white", "pink", "light_blue", "magenta", "yellow", "lime", "cyan", "purple"};
            int k = 0;
            for (int x = -4; x <= 5; x++) {
                for (int z = -4; z <= 4; z++) {
                    if (Math.abs(x) <= 1 && Math.abs(z) <= 1 || x == 3 && z == -1) continue;
                    if (Math.hypot(x - 2.3D, z - 2.0D) < 1.8D) continue;          // the camera's spot
                    long h = (x * 73856093L) ^ (z * 19349663L);
                    if (Math.floorMod(h, 5) == 0) {
                        level.setBlock(POS.offset(x, 0, z), block("botania", colours[k++ % colours.length] + "_mystical_flower").defaultBlockState(), 3);
                    } else if (Math.floorMod(h, 3) == 0) {
                        level.setBlock(POS.offset(x, 0, z), Blocks.GRASS.defaultBlockState(), 3);
                    }
                }
            }
            ServerPlayer p = player();
            // the mod's items in the inventory, to see their icons in the GUIs
            ItemStack stored = new ItemStack(AlfheimHeart.GREENHOUSE_ITEM.get());
            CompoundTag entity = new CompoundTag();
            entity.putInt(GreenhouseBlockEntity.TAG_MANA, 125_000);
            net.minecraft.world.item.BlockItem.setBlockEntityData(stored, AlfheimHeart.GREENHOUSE_BE.get(), entity);
            ItemStack[] kit = {stored, new ItemStack(AlfheimHeart.PORTAL_ITEM.get()), new ItemStack(AlfheimHeart.GREENHOUSE_HEART.get()),
                    new ItemStack(AlfheimHeart.UPGRADE_BASE.get(), 12), new ItemStack(AlfheimHeart.SPEED_UPGRADE.get(), 2),
                    new ItemStack(AlfheimHeart.LUCK_UPGRADE.get(), 3), stack("botania", "livingwood_log", 32),
                    stack("botania", "manasteel_ingot", 16), stack("botania", "mana_tablet", 1)};
            for (int i = 0; i < kit.length; i++) p.getInventory().setItem(9 + i, kit[i]);
            p.getInventory().setItem(0, new ItemStack(item("botania", "twig_wand")));
            p.getInventory().selected = 0;
        });
        worldView();
    }

    private static void open() {
        beforeGui();
        MinecraftServer server = server();
        server.execute(() -> {
            if (server.overworld().getBlockEntity(POS) instanceof GreenhouseBlockEntity be) {
                // an empty mana tablet to charge, so the gauge fills while we look
                if (be.items().getStackInSlot(GreenhouseBlockEntity.CHARGE_SLOT).isEmpty()) {
                    be.items().setStackInSlot(GreenhouseBlockEntity.CHARGE_SLOT, new ItemStack(item("botania", "mana_tablet")));
                }
                NetworkHooks.openScreen(player(), be, POS);
            }
        });
    }

    private GreenhouseShots() {
    }
}
