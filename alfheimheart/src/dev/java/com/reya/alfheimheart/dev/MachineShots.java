package com.reya.alfheimheart.dev;

import static com.reya.alfheimheart.dev.AutoShot.*;

import java.util.List;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlock;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.altar.RuneAltarBlockEntity;
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
 * a creative mana pool behind each, their slots full of what they make runes, terrasteel and manasteel from;
 * each in the world, its GUI opening, at work, with its heart's tooltip; the infuser with a catalyst; the
 * lexicon page.
 */
final class MachineShots {
    static final BlockPos ALTAR = new BlockPos(70, -60, 0), PLATE = ALTAR.east(3), INFUSER = ALTAR.east(6);
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
        s.add(new Step(3, () -> shot("rune_altar_gui_open.png")));
        s.add(new Step(40, () -> shot("rune_altar_gui.png")));
        s.add(new Step(12, () -> shot("rune_altar_gui_2.png")));
        s.add(new Step(5, () -> mouseAtGui(120, 40)));
        s.add(new Step(10, () -> shot("rune_altar_gui_tip.png")));
        s.add(new Step(5, () -> mouseAtGui(120, 92)));
        s.add(new Step(10, () -> shot("rune_altar_gui_reagent.png")));
        s.add(new Step(5, AutoShot::mouseAway));
        s.add(new Step(5, AutoShot::closeScreen));
        s.add(new Step(5, () -> eye(PLATE, 0.5D, 1.9D, 2.6D, 0.5D, 0.4D, 0.5D)));
        s.add(new Step(10, () -> open(PLATE)));
        s.add(new Step(30, () -> shot("terra_plate_gui.png")));
        s.add(new Step(30, () -> shot("terra_plate_gui_2.png")));
        s.add(new Step(30, () -> shot("terra_plate_gui_3.png")));
        s.add(new Step(5, () -> mouseAtGui(120, 52)));
        s.add(new Step(10, () -> shot("terra_plate_gui_tip.png")));
        s.add(new Step(5, AutoShot::mouseAway));
        s.add(new Step(5, AutoShot::closeScreen));
        s.add(new Step(5, () -> eye(INFUSER, 0.5D, 1.9D, 2.6D, 0.5D, 0.6D, 0.5D)));
        s.add(new Step(10, () -> open(INFUSER)));
        s.add(new Step(30, () -> shot("mana_infuser_gui.png")));
        s.add(new Step(9, () -> shot("mana_infuser_gui_2.png")));
        s.add(new Step(5, () -> mouseAtGui(120, 40)));
        s.add(new Step(10, () -> shot("mana_infuser_gui_tip.png")));
        s.add(new Step(5, AutoShot::mouseAway));
        s.add(new Step(5, () -> catalyst(stack("botania", "alchemy_catalyst", 1))));
        s.add(new Step(30, () -> shot("mana_infuser_gui_alchemy.png")));
        s.add(new Step(5, () -> catalyst(ItemStack.EMPTY)));
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
        s.add(new Step(40, () -> mouseAtGui(120, 40)));
        s.add(new Step(10, () -> shot("rune_altar_gui_uk.png")));
        s.add(new Step(5, AutoShot::closeScreen));
        s.add(new Step(5, () -> eye(INFUSER, 0.5D, 1.9D, 2.6D, 0.5D, 0.6D, 0.5D)));
        s.add(new Step(20, () -> open(INFUSER)));
        s.add(new Step(40, () -> mouseAtGui(120, 40)));
        s.add(new Step(10, () -> shot("mana_infuser_gui_uk.png")));
        s.add(new Step(5, AutoShot::closeScreen));
    }

    /** The three machines facing south on livingrock bricks, a creative mana pool behind each, a few flowers round. */
    private static void setUp() {
        MinecraftServer server = server();
        server.execute(() -> {
            ServerLevel level = server.overworld();
            for (int x = -2; x <= 8; x++) {
                for (int z = -2; z <= 2; z++) {
                    level.setBlock(ALTAR.offset(x, -1, z), block("botania", "livingrock_bricks").defaultBlockState(), 3);
                }
            }
            place(level, ALTAR, AlfheimHeart.RUNE_ALTAR.get());
            place(level, PLATE, AlfheimHeart.TERRA_PLATE.get());
            place(level, INFUSER, AlfheimHeart.MANA_INFUSER.get());
            for (BlockPos pos : new BlockPos[]{ALTAR, PLATE, INFUSER}) {
                level.setBlock(pos.north(), block("botania", "creative_pool").defaultBlockState(), 3);
            }
            String[] colours = {"white", "pink", "light_blue", "magenta", "yellow", "lime", "cyan", "purple"};
            int k = 0;
            for (int x = -2; x <= 8; x++) {
                for (int z = -2; z <= 2; z += 4) {
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
            set(altar, MachineBlockEntity.OUTPUT_START, stack("botania", "rune_fire", 6), stack("botania", "rune_water", 4),
                    stack("botania", "rune_earth", 2));
            altar.items().setStackInSlot(RuneAltarBlockEntity.REAGENT, stack("botania", "livingrock", 32));
        }
        if (level.getBlockEntity(PLATE) instanceof MachineBlockEntity plate) {
            set(plate, MachineBlockEntity.INPUT_START, stack("botania", "manasteel_ingot", 16), stack("botania", "mana_pearl", 16),
                    stack("botania", "mana_diamond", 16));
            set(plate, MachineBlockEntity.OUTPUT_START, stack("botania", "terrasteel_ingot", 3));
        }
        if (level.getBlockEntity(INFUSER) instanceof ManaInfuserBlockEntity infuser) {
            set(infuser, MachineBlockEntity.INPUT_START, stack("minecraft", "iron_ingot", 64), stack("minecraft", "diamond", 32),
                    stack("minecraft", "ender_pearl", 16), stack("minecraft", "glass", 64));
            set(infuser, MachineBlockEntity.OUTPUT_START, stack("botania", "manasteel_ingot", 24));
        }
    }

    private static void set(MachineBlockEntity be, int from, ItemStack... stacks) {
        for (int i = 0; i < MachineBlockEntity.INPUTS; i++) {
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
