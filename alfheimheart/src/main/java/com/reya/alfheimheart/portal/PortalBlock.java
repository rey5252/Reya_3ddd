package com.reya.alfheimheart.portal;

import com.reya.alfheimheart.AlfheimHeart;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import vazkii.botania.api.BotaniaAPI;

/**
 * A little elven moon gate: a ring of cream stone rimmed in gold, a vine with gold lights winding round it
 * and a green gem in its top, on a two-step pedestal. While it has mana for a trade the portal inside the
 * ring is open (the swirl, the runes, the lights and the floating crystals are drawn by
 * {@link com.reya.alfheimheart.portal.client.PortalRenderer}); it faces whoever placed it.
 */
public class PortalBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = BlockStateProperties.HORIZONTAL_FACING;
    /** The portal is open: it has mana for a trade and its redstone setting lets it work. */
    public static final BooleanProperty OPEN = BooleanProperty.create("open");

    // the gate as the model has it facing north (the ring across x), and turned a quarter (across z): the
    // pedestal's two steps and the ring's square, gem and all (solid, so mana bursts aimed at the portal land in it)
    private static final VoxelShape SHAPE_X = Shapes.or(
            Block.box(1.0D, 0.0D, 2.0D, 15.0D, 1.0D, 14.0D),
            Block.box(3.0D, 1.0D, 4.0D, 13.0D, 2.0D, 12.0D),
            Block.box(1.0D, 2.0D, 6.0D, 15.0D, 16.0D, 10.0D));
    private static final VoxelShape SHAPE_Z = Shapes.or(
            Block.box(2.0D, 0.0D, 1.0D, 14.0D, 1.0D, 15.0D),
            Block.box(4.0D, 1.0D, 3.0D, 12.0D, 2.0D, 13.0D),
            Block.box(6.0D, 2.0D, 1.0D, 10.0D, 16.0D, 15.0D));

    public PortalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(OPEN, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, OPEN);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_X : SHAPE_Z;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PortalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, AlfheimHeart.PORTAL_BE.get(), PortalBlockEntity::clientTick)
                : createTickerHelper(type, AlfheimHeart.PORTAL_BE.get(), PortalBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof PortalBlockEntity be && player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp, be, pos);
        }
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof PortalBlockEntity be) {
            for (int i = 0; i < PortalBlockEntity.SLOTS; i++) {
                ItemStack stack = be.items().getStackInSlot(i);
                if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    /** Comparators read how full the portal's mana store is, as they do a mana pool's. */
    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof PortalBlockEntity be ? be.comparatorSignal() : 0;
    }

    /** While open, little green and golden sparkles drift out of the portal inside the ring. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(OPEN)) return;
        Direction facing = state.getValue(FACING);
        for (int i = 0; i < 2; i++) {
            double across = (random.nextDouble() - 0.5D) * 0.55D, up = 9.0D / 16.0D + (random.nextDouble() - 0.5D) * 0.55D;
            double out = (random.nextDouble() - 0.5D) * 0.15D;
            double x = pos.getX() + 0.5D + facing.getStepZ() * across + facing.getStepX() * out;
            double z = pos.getZ() + 0.5D + facing.getStepX() * across + facing.getStepZ() * out;
            boolean gold = random.nextInt(4) == 0;
            BotaniaAPI.instance().sparkleFX(level, x, pos.getY() + up, z,
                    gold ? 1.0F : 0.35F, gold ? 0.85F : 1.0F, gold ? 0.35F : 0.45F, 0.5F + random.nextFloat() * 0.6F, 4 + random.nextInt(3));
        }
    }
}
