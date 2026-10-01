package com.reya.alfheimheart.machine.apothecary;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Petal Apothecary's block: a livingrock bowl of water on a livingwood stem (the renderer draws the water). */
public class PetalApothecaryBlock extends MachineBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(3, 0, 3, 13, 2, 13), Block.box(5, 2, 5, 11, 7, 11),
            Block.box(1, 7, 1, 15, 13, 15));

    public PetalApothecaryBlock(Properties properties) {
        super(properties, AlfheimHeart.PETAL_APOTHECARY_BE, SHAPE);
    }
}
