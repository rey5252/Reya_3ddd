package com.reya.alfheimheart.machine.infuser;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The Mana Infuser's block: a little mana pool of livingrock on a livingwood column. */
public class ManaInfuserBlock extends MachineBlock {
    private static final VoxelShape SHAPE = Shapes.or(Block.box(2, 0, 2, 14, 2, 14), Block.box(4, 2, 4, 12, 6, 12),
            Block.box(1, 6, 1, 15, 12, 15));

    public ManaInfuserBlock(Properties properties) {
        super(properties, AlfheimHeart.MANA_INFUSER_BE, SHAPE);
    }
}
