package com.reya.alfheimheart.machine.farm;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Petal Farm's block: a livingwood planter of soil (the renderer plants the flowers in it). */
public class PetalFarmBlock extends MachineBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

    public PetalFarmBlock(Properties properties) {
        super(properties, AlfheimHeart.PETAL_FARM_BE, SHAPE);
    }
}
