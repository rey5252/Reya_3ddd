package com.reya.managarden;

import javax.annotation.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import vazkii.botania.api.BotaniaAPI;

/**
 * A little glass greenhouse on a livingrock base: the flowers grow inside on a bed of soil, and a
 * bud of four livingwood petals closes it at the top, which blooms open while someone looks inside.
 */
public class GreenhouseBlock extends BaseEntityBlock {
    /** Lit while the flowers are growing. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0.0D, 0.0D, 0.0D, 16.0D, 4.0D, 16.0D),
            Block.box(1.0D, 4.0D, 1.0D, 15.0D, 13.0D, 15.0D),
            Block.box(0.5D, 13.0D, 0.5D, 15.5D, 14.0D, 15.5D),
            Block.box(3.0D, 14.0D, 3.0D, 13.0D, 16.0D, 13.0D));

    public GreenhouseBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(ACTIVE, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(ACTIVE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new GreenhouseBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide
                ? createTickerHelper(type, ManaGarden.GREENHOUSE_BE.get(), GreenhouseBlockEntity::clientTick)
                : createTickerHelper(type, ManaGarden.GREENHOUSE_BE.get(), GreenhouseBlockEntity::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof GreenhouseBlockEntity be && player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp, be, pos);
        }
        return InteractionResult.CONSUME;
    }

    /** Scheduled by the openers counter: looks again who still has the greenhouse open. */
    @Override
    @SuppressWarnings("deprecation")
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getBlockEntity(pos) instanceof GreenhouseBlockEntity be) be.recheckOpeners();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof GreenhouseBlockEntity be) {
            for (int i = 0; i < GreenhouseBlockEntity.SLOTS; i++) {
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

    /** Comparators read how full the mana store is. */
    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof GreenhouseBlockEntity be ? be.comparatorSignal() : 0;
    }

    /**
     * While it grows, mana sparkles rise from the flower bed inside and drift up out of the bud, in
     * the flowers' own colours now and then; a tiny cyan glint circles the core.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(ACTIVE) || !(level.getBlockEntity(pos) instanceof GreenhouseBlockEntity be)) return;
        int color = random.nextInt(3) == 0 ? be.randomFlowerColor(random) : 0x4CD7FF;
        float r = (color >> 16 & 0xFF) / 255.0F, g = (color >> 8 & 0xFF) / 255.0F, b = (color & 0xFF) / 255.0F;
        double x = pos.getX() + 0.25D + random.nextDouble() * 0.5D;
        double z = pos.getZ() + 0.25D + random.nextDouble() * 0.5D;
        double y = pos.getY() + 0.3D + random.nextDouble() * 0.4D;
        BotaniaAPI.instance().sparkleFX(level, x, y, z, r, g, b, 0.6F + random.nextFloat() * 0.6F, 4 + random.nextInt(3));
        if (random.nextInt(3) == 0) {
            BotaniaAPI.instance().sparkleFX(level, pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.3D, pos.getY() + 1.05D,
                    pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.3D, 0.55F, 0.95F, 1.0F, 1.0F, 6);
        }
    }
}
