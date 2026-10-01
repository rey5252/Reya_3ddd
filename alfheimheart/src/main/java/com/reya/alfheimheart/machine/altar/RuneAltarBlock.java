package com.reya.alfheimheart.machine.altar;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Runic Altar's block: a livingrock plinth, a livingwood body, a livingrock top with crystals on its corners. */
public class RuneAltarBlock extends MachineBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(1, 0, 1, 15, 2, 15), Block.box(2, 2, 2, 14, 9, 14),
            Block.box(1, 9, 1, 15, 12, 15));

    public RuneAltarBlock(Properties properties) {
        super(properties, AlfheimHeart.RUNE_ALTAR_BE, SHAPE);
    }
}
