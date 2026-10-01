package com.reya.alfheimheart.machine.plate;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Terrestrial Plate's block: a livingwood base, the plate on it, livingrock posts with crystals on its corners. */
public class TerraPlateBlock extends MachineBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(0, 0, 0, 16, 5, 16), Block.box(0, 5, 0, 2, 6, 2),
            Block.box(14, 5, 0, 16, 6, 2), Block.box(0, 5, 14, 2, 6, 16), Block.box(14, 5, 14, 16, 6, 16));

    public TerraPlateBlock(Properties properties) {
        super(properties, AlfheimHeart.TERRA_PLATE_BE, SHAPE);
    }
}
