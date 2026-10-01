package com.reya.alfheimheart.machine.orechid;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Orechid Mine's block: a livingrock pit framed in livingwood, an orechid growing over it (the renderer draws it). */
public class OrechidMineBlock extends MachineBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 10, 16);

    public OrechidMineBlock(Properties properties) {
        super(properties, AlfheimHeart.ORECHID_MINE_BE, SHAPE);
    }
}
