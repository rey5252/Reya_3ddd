package com.reya.alfheimheart.machine.daisy;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Pure Daisy's block: a livingwood planter full of grass, the daisy growing in it (the renderer draws the daisy). */
public class PureDaisyBlock extends MachineBlock {
    private static final VoxelShape SHAPE = Block.box(1, 0, 1, 15, 9, 15);

    public PureDaisyBlock(Properties properties) {
        super(properties, AlfheimHeart.PURE_DAISY_BE, SHAPE);
    }
}
