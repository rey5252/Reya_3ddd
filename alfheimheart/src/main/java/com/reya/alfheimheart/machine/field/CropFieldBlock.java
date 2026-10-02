package com.reya.alfheimheart.machine.field;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Crop Field's block: a bed of tilled soil in a livingwood frame (the renderer grows the crops in it). */
public class CropFieldBlock extends MachineBlock {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

    public CropFieldBlock(Properties properties) {
        super(properties, AlfheimHeart.CROP_FIELD_BE, SHAPE);
    }
}
