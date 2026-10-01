package com.reya.alfheimheart.dev;

import static com.reya.alfheimheart.dev.AutoShot.*;

import java.util.List;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlock;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.altar.RuneAltarBlockEntity;
import com.reya.alfheimheart.machine.apothecary.PetalApothecaryBlockEntity;
import com.reya.alfheimheart.machine.farm.PetalFarmBlockEntity;
import com.reya.alfheimheart.machine.field.CropFieldBlockEntity;
import com.reya.alfheimheart.machine.infuser.ManaInfuserBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.network.NetworkHooks;

/**
 * The machines' scene: the Runic Altar, the Terrestrial Plate and the Mana Infuser side by side on livingrock,
 * the Pure Daisy, the Petal Apothecary and the Petal Farm in a row before them, a creative mana pool behind
 * each, their slots full of what they work on; each in the world, its GUI opening, at work, with its heart's
 * tooltip; the infuser with a catalyst; the lexicon page.
 */
final class MachineShots {
    static final BlockPos ALTAR = new BlockPos(70, -60, 0), PLATE = ALTAR.east(3), INFUSER = ALTAR.east(6);
    static final BlockPos DAISY = ALTAR.south(4), APOTHECARY = DAISY.east(3), FARM = DAISY.east(6);
    static final BlockPos MINE = ALTAR.west(4), FIELD = MINE.south(4);
    private static final String ENTRY = "basics/alfheimheart_machines";

    static void steps(List<Step> s) {
        s.add(new Step(10, MachineShots::setUp));
        s.add(new Step(60, () -> eye(ALTAR, 3.5D, 2.7D, 4.6D, 3.5D, 0.4D, 0.5D)));
        s.add(new Step(80, () -> shot("machines_scene.png")));
        s.add(new Step(5, () -> eye(ALTAR, 1.5D, 1.8D, 2.0D, 0.5D, 0.85D, 0.5D)));
        s.add(new Step(30, () -> shot("rune_altar_block.png")));
        s.add(new Step(8, () -> shot("rune_altar_block_2.png")));
        s.add(new Step(5, () -> eye(PLATE, 1.5D, 1.5D, 1.9D, 0.5D, 0.5D, 0.5D)));
        s.add(new Step(30, () -> shot("terra_plate_block.png")));
        s.add(new Step(25, () -> shot("terra_plate_block_2.png")));
        s.add(new Step(5, () -> eye(INFUSER, 1.3D, 1.7D, 1.7D, 0.5D, 0.7D, 0.5D)));
        s.add(new Step(30, () -> shot("mana_infuser_block.png")));
        // the GUIs
        s.add(new Step(5, () -> eye(ALTAR, 0.5D, 1.9D, 2.6D, 0.5D, 0.6D, 0.5D)));
        s.add(new Step(10, AutoShot::mouseAway));
        s.add(new Step(5, () -> open(ALTAR)));
        s.add(new Step(1, AutoShot::mouseAway));
        s.add(new Step(2, () -> shot("rune_altar_gui_open.png")));
        s.add(new Step(40, () -> shot("rune_altar_gui.png")));
        s.add(new Step(12, () -> shot("rune_altar_gui_2.png")));
        s.add(new Step(5, () -> mouseAtGui(124, 66)));
        s.add(new Step(10, () -> shot("rune_altar_gui_tip.png")));
        s.add(new Step(5, () -> mouseAtGui(124, 128)));
        s.add(new Step(10, () -> shot("rune_altar_gui_reagent.png")));
        s.add(new Step(5, () -> mouseAtGui(75, 87)));
        s.add(new Step(10, () -> shot("rune_altar_gui_socket.png")));
        s.add(new Step(5, AutoShot::mouseAway));
        s.add(new Step(5, AutoShot::closeScreen));
        s.add(new Step(5, () -> eye(PLATE, 0.5D, 1.9D, 2.6D, 0.5D, 0.4D, 0.5D)));
        s.add(new Step(10, () -> open(PLATE)));
        s.add(new Step(2, AutoShot::mouseAway));
        s.add(new Step(28, () -> shot("terra_plate_gui.png")));
        s.add(new Step(30, () -> shot("terra_plate_gui_2.png")));
        s.add(new Step(30, () -> shot("terra_plate_gui_3.png")));
        s.add(new Step(5, () -> mouseAtGui(114, 70)));
        s.add(new Step(10, () -> shot("terra_plate_gui_tip.png")));
        s.add(new Step(5, () -> mouseAtGui(114, 124)));
        s.add(new Step(10, () -> shot("terra_plate_gui_point.png")));
        s.add(new Step(5, AutoShot::mouseAway));
        s.add(new Step(5, AutoShot::closeScreen));
        s.add(new Step(5, () -> eye(INFUSER, 0.5D, 1.9D, 2.6D, 0.5D, 0.6D, 0.5D)));
        s.add(new Step(10, () -> open(INFUSER)));
        s.add(new Step(1, AutoShot::mouseAway));
        s.add(new Step(30, () -> shot("mana_infuser_gui.png")));
        s.add(new Step(9, () -> shot("mana_infuser_gui_2.png")));
        s.add(new Step(5, () -> mouseAtGui(120, 40)));
        s.add(new Step(10, () -> shot("mana_infuser_gui_tip.png")));
        s.add(new Step(5, AutoShot::mouseAway));
        s.add(new Step(5, () -> catalyst(stack("botania", "alchemy_catalyst", 1))));
        s.add(new Step(30, () -> shot("mana_infuser_gui_alchemy.png")));
        s.add(new Step(5, () -> catalyst(ItemStack.EMPTY)));
        s.add(new Step(5, AutoShot::closeScreen));
        // the second row: the Pure Daisy, the Petal Apothecary, the Petal Farm
        s.add(new Step(5, AutoShot::worldView));
        s.add(new Step(5, () -> eye(DAISY, 3.5D, 2.6D, 4.4D, 3.5D, 0.3D, 0.5D)));
        s.add(new Step(40, () -> shot("garden_scene.png")));
        s.add(new Step(5, () -> eye(DAISY, 1.4D, 1.6D, 1.8D, 0.5D, 0.6D, 0.5D)));
        s.add(new Step(30, () -> shot("pure_daisy_block.png")));
        s.add(new Step(5, () -> eye(APOTHECARY, 1.3D, 1.7D, 1.7D, 0.5D, 0.75D, 0.5D)));
        s.add(new Step(30, () -> shot("petal_apothecary_block.png")));
        s.add(new Step(5, () -> eye(FARM, 1.4D, 1.6D, 1.8D, 0.5D, 0.5D, 0.5D)));
        s.add(new Step(30, () -> shot("petal_farm_block.png")));
        s.add(new Step(5, () -> eye(DAISY, 0.5D, 1.9D, 2.6D, 0.5D, 0.6D, 0.5D)));
        s.add(new Step(10, () -> open(DAISY)));
        s.add(new Step(1, AutoShot::mouseAway));
        s.add(new Step(40, () -> shot("pure_daisy_gui.png")));
        s.add(new Step(20, () -> shot("pure_daisy_gui_2.png")));
        s.add(new Step(5, () -> mouseAtGui(124, 82)));
        s.add(new Step(10, () -> shot("pure_daisy_gui_tip.png")));
        s.add(new Step(5, AutoShot::mouseAway));
        s.add(new Step(5, AutoShot::closeScreen));
        s.add(new Step(5, () -> eye(APOTHECARY, 0.5D, 1.9D, 2.6D, 0.5D, 0.6D, 0.5D)));
        s.add(new Step(10, () -> open(APOTHECARY)));
        s.add(new Step(1, AutoShot::mouseAway));
        s.add(new Step(25, () -> shot("petal_apothecary_gui.png")));
        s.add(new Step(15, () -> shot("petal_apothecary_gui_2.png")));
        s.add(new Step(5, () -> mouseAtGui(128, 98)));
        s.add(new Step(10, () -> shot("petal_apothecary_gui_tip.png")));
        s.add(new Step(5, AutoShot::mouseAway));
        s.add(new Step(5, AutoShot::closeScreen));
        s.add(new Step(5, () -> eye(FARM, 0.5D, 1.9D, 2.6D, 0.5D, 0.6D, 0.5D)));
        s.add(new Step(10, () -> open(FARM)));
        s.add(new Step(1, AutoShot::mouseAway));
        s.add(new Step(40, () -> shot("petal_farm_gui.png")));
        s.add(new Step(30, () -> shot("petal_farm_gui_2.png")));
        s.add(new Step(5, () -> mouseAtGui(120, 40)));
        s.add(new Step(10, () -> shot("petal_farm_gui_tip.png")));
        s.add(new Step(5, AutoShot::mouseAway));
        s.add(new Step(5, AutoShot::closeScreen));
        // the Orechid Mine and the Crop Field, to the west
        s.add(new Step(5, AutoShot::worldView));
        s.add(new Step(5, () -> eye(MINE, 1.5D, 1.7D, 2.0D, 0.5D, 0.6D, 0.5D)));
        s.add(new Step(30, () -> shot("orechid_mine_block.png")));
        s.add(new Step(5, () -> eye(FIELD, 1.5D, 1.6D, 1.9D, 0.5D, 0.5D, 0.5D)));
        s.add(new Step(40, () -> shot("crop_field_block.png")));
        s.add(new Step(5, () -> eye(MINE, 0.5D, 1.9D, 2.6D, 0.5D, 0.6D, 0.5D)));
        s.add(new Step(10, () -> open(MINE)));
        s.add(new Step(1, AutoShot::mouseAway));
        s.add(new Step(30, () -> shot("orechid_mine_gui.png")));
        s.add(new Step(15, () -> shot("orechid_mine_gui_2.png")));
        s.add(new Step(5, () -> mouseAtGui(131, 22)));
        s.add(new Step(10, () -> shot("orechid_mine_gui_tip.png")));
        s.add(new Step(5, AutoShot::mouseAway));
        s.add(new Step(5, AutoShot::closeScreen));
        s.add(new Step(5, () -> eye(FIELD, 0.5D, 1.9D, 2.6D, 0.5D, 0.6D, 0.5D)));
        s.add(new Step(10, () -> open(FIELD)));
        s.add(new Step(1, AutoShot::mouseAway));
        s.add(new Step(60, () -> shot("crop_field_gui.png")));
        s.add(new Step(60, () -> shot("crop_field_gui_2.png")));
        s.add(new Step(5, () -> mouseAtGui(120, 40)));
        s.add(new Step(10, () -> shot("crop_field_gui_tip.png")));
        s.add(new Step(5, AutoShot::mouseAway));
        s.add(new Step(5, AutoShot::closeScreen));
        // the lexicon
        s.add(new Step(10, () -> grant("botania:main/runic_altar_pickup")));
        s.add(new Step(30, () -> openLexicon(ENTRY, 0)));
        s.add(new Step(30, () -> shot("machines_lexicon.png")));
        s.add(new Step(5, () -> openLexicon(ENTRY, 2)));
        s.add(new Step(30, () -> shot("machines_lexicon_altar.png")));
        s.add(new Step(5, AutoShot::closeScreen));
    }

    static void ukSteps(List<Step> s) {
        s.add(new Step(5, () -> eye(ALTAR, 0.5D, 1.9D, 2.6D, 0.5D, 0.6D, 0.5D)));
        s.add(new Step(20, () -> open(ALTAR)));
        s.add(new Step(40, () -> mouseAtGui(124, 66)));
        s.add(new Step(10, () -> shot("rune_altar_gui_uk.png")));
        s.add(new Step(5, AutoShot::closeScreen));
        s.add(new Step(5, () -> eye(INFUSER, 0.5D, 1.9D, 2.6D, 0.5D, 0.6D, 0.5D)));
        s.add(new Step(20, () -> open(INFUSER)));
        s.add(new Step(40, () -> mouseAtGui(120, 40)));
        s.add(new Step(10, () -> shot("mana_infuser_gui_uk.png")));
        s.add(new Step(5, AutoShot::closeScreen));
        s.add(new Step(5, () -> eye(FARM, 0.5D, 1.9D, 2.6D, 0.5D, 0.6D, 0.5D)));
        s.add(new Step(20, () -> open(FARM)));
        s.add(new Step(40, () -> mouseAtGui(120, 40)));
        s.add(new Step(10, () -> shot("petal_farm_gui_uk.png")));
        s.add(new Step(5, AutoShot::closeScreen));
    }

    /** The three machines facing south on livingrock bricks, a creative mana pool behind each, a few flowers round. */
    private static void setUp() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            for (int x = -6; x <= 8; x++) {
                for (int z = -2; z <= 6; z++) {
                    level.setBlock(ALTAR.offset(x, -1, z), block("botania", "livingrock_bricks").defaultBlockState(), 3);
                }
            }
            place(level, ALTAR, AlfheimHeart.RUNE_ALTAR.get());
            place(level, PLATE, AlfheimHeart.TERRA_PLATE.get());
            place(level, INFUSER, AlfheimHeart.MANA_INFUSER.get());
            place(level, DAISY, AlfheimHeart.PURE_DAISY.get());
            place(level, APOTHECARY, AlfheimHeart.PETAL_APOTHECARY.get());
            place(level, FARM, AlfheimHeart.PETAL_FARM.get());
            place(level, MINE, AlfheimHeart.ORECHID_MINE.get());
            place(level, FIELD, AlfheimHeart.CROP_FIELD.get());
            for (BlockPos pos : new BlockPos[]{ALTAR, PLATE, INFUSER, DAISY, APOTHECARY, FARM, MINE, FIELD}) {
                level.setBlock(pos.north(), block("botania", "creative_pool").defaultBlockState(), 3);
            }
            String[] colours = {"white", "pink", "light_blue", "magenta", "yellow", "lime", "cyan", "purple"};
            int k = 0;
            for (int x = -2; x <= 8; x++) {
                for (int z = -2; z <= 6; z += 4) {
                    if (Math.floorMod(x * 7 + z, 3) == 0) {
                        level.setBlock(ALTAR.offset(x, 0, z), block("botania", colours[k++ % colours.length] + "_mystical_flower").defaultBlockState(), 3);
                    }
                }
            }
            fill(level);
        });
        worldView();
    }

    private static void place(ServerLevel level, BlockPos pos, Block block) {
        level.setBlock(pos, block.defaultBlockState().setValue(MachineBlock.FACING, Direction.SOUTH), 3);
    }

    /** Refills the machines' slots (their crafts eat them) and puts a few earlier crafts in their outputs. */
    private static void fill(ServerLevel level) {
        if (level.getBlockEntity(ALTAR) instanceof RuneAltarBlockEntity altar) {
            set(altar, MachineBlockEntity.INPUT_START, stack("botania", "mana_powder", 64), stack("botania", "manasteel_ingot", 64),
                    stack("minecraft", "nether_brick", 64), stack("minecraft", "gunpowder", 64), stack("minecraft", "nether_wart", 64));
            set(altar, altar.outputStart(), stack("botania", "rune_fire", 6), stack("botania", "rune_water", 4),
                    stack("botania", "rune_earth", 2));
            altar.items().setStackInSlot(RuneAltarBlockEntity.REAGENT, stack("botania", "livingrock", 32));
        }
        if (level.getBlockEntity(PLATE) instanceof MachineBlockEntity plate) {
            set(plate, MachineBlockEntity.INPUT_START, stack("botania", "manasteel_ingot", 16), stack("botania", "mana_pearl", 16),
                    stack("botania", "mana_diamond", 16));
            set(plate, plate.outputStart(), stack("botania", "terrasteel_ingot", 3));
        }
        if (level.getBlockEntity(INFUSER) instanceof ManaInfuserBlockEntity infuser) {
            set(infuser, MachineBlockEntity.INPUT_START, stack("minecraft", "iron_ingot", 64), stack("minecraft", "diamond", 32),
                    stack("minecraft", "ender_pearl", 16), stack("minecraft", "glass", 64));
            set(infuser, infuser.outputStart(), stack("botania", "manasteel_ingot", 24));
        }
        if (level.getBlockEntity(DAISY) instanceof MachineBlockEntity daisy) {
            set(daisy, MachineBlockEntity.INPUT_START, stack("minecraft", "stone", 64), stack("minecraft", "oak_log", 32),
                    stack("minecraft", "netherrack", 16));
            set(daisy, daisy.outputStart(), stack("botania", "livingrock", 24));
        }
        if (level.getBlockEntity(APOTHECARY) instanceof PetalApothecaryBlockEntity apothecary) {
            set(apothecary, MachineBlockEntity.INPUT_START, stack("botania", "brown_petal", 16), stack("botania", "red_petal", 16),
                    stack("botania", "light_gray_petal", 16));
            set(apothecary, apothecary.outputStart(), stack("botania", "endoflame", 2));
            apothecary.items().setStackInSlot(PetalApothecaryBlockEntity.REAGENT, stack("minecraft", "wheat_seeds", 16));
        }
        if (level.getBlockEntity(FARM) instanceof PetalFarmBlockEntity farm) {
            set(farm, MachineBlockEntity.INPUT_START, stack("botania", "pink_mystical_flower", 16), stack("botania", "light_blue_mystical_flower", 32),
                    stack("botania", "yellow_mystical_flower", 8), stack("botania", "magenta_mystical_flower", 12),
                    stack("botania", "white_mystical_flower", 4), stack("botania", "lime_mystical_flower", 20));
            set(farm, farm.outputStart(), stack("botania", "pink_petal", 9), stack("botania", "light_blue_petal", 12),
                    stack("botania", "yellow_petal", 5));
            farm.items().setStackInSlot(PetalFarmBlockEntity.FERTILIZER, stack("minecraft", "bone_meal", 16));
        }
        if (level.getBlockEntity(MINE) instanceof MachineBlockEntity mine) {
            set(mine, MachineBlockEntity.INPUT_START, stack("minecraft", "stone", 64), stack("minecraft", "netherrack", 32),
                    stack("minecraft", "deepslate", 16));
            set(mine, mine.outputStart(), stack("minecraft", "coal_ore", 9), stack("minecraft", "iron_ore", 5),
                    stack("minecraft", "copper_ore", 4), stack("minecraft", "gold_ore", 1));
        }
        if (level.getBlockEntity(FIELD) instanceof CropFieldBlockEntity field) {
            set(field, MachineBlockEntity.INPUT_START, stack("minecraft", "wheat_seeds", 32), stack("minecraft", "carrot", 16),
                    stack("minecraft", "potato", 16), stack("minecraft", "beetroot_seeds", 8), stack("minecraft", "melon_seeds", 4),
                    stack("minecraft", "sugar_cane", 8));
            set(field, field.outputStart(), stack("minecraft", "wheat", 12), stack("minecraft", "carrot", 20));
            field.items().setStackInSlot(CropFieldBlockEntity.FERTILIZER, stack("minecraft", "bone_meal", 16));
        }
    }

    /** Fills the inputs (from INPUT_START) or the outputs (from outputStart) with the stacks, emptying the rest of them. */
    private static void set(MachineBlockEntity be, int from, ItemStack... stacks) {
        int n = from == MachineBlockEntity.INPUT_START ? be.inputCount() : be.outputCount();
        for (int i = 0; i < n; i++) {
            be.items().setStackInSlot(from + i, i < stacks.length ? stacks[i] : ItemStack.EMPTY);
        }
        be.setChanged();
    }

    private static void open(BlockPos pos) {
        beforeGui();
        MinecraftServer server = server();
        server.execute(() -> {
            fill(server.overworld());
            if (server.overworld().getBlockEntity(pos) instanceof MachineBlockEntity be) NetworkHooks.openScreen(player(), be, pos);
        });
    }

    private static void catalyst(ItemStack stack) {
        MinecraftServer server = server();
        server.execute(() -> {
            if (server.overworld().getBlockEntity(INFUSER) instanceof ManaInfuserBlockEntity infuser) {
                infuser.items().setStackInSlot(ManaInfuserBlockEntity.CATALYST, stack.copy());
            }
        });
    }

    private MachineShots() {
    }
}
