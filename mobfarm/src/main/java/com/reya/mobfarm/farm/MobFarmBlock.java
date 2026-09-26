package com.reya.mobfarm.farm;

import java.util.List;

import javax.annotation.Nullable;

import com.reya.mobfarm.FarmTier;
import com.reya.mobfarm.ModRegistry;
import com.reya.mobfarm.lasso.LassoItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;

public class MobFarmBlock extends BaseEntityBlock {
    /** Base plate plus the glass case on top of it. */
    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(0, 0, 0, 16, 2, 16),
            Block.box(1, 2, 1, 15, 14, 15));

    public final FarmTier tier;

    public MobFarmBlock(FarmTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MobFarmBlockEntity(pos, state);
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
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, ModRegistry.MOB_FARM.get(), MobFarmBlockEntity::serverTick);
    }

    /** Right-click with a caught lasso puts it straight in; otherwise opens the GUI. */
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;
        if (!(level.getBlockEntity(pos) instanceof MobFarmBlockEntity farm)) return InteractionResult.PASS;

        ItemStack held = player.getItemInHand(hand);
        if (LassoItem.hasMob(held) && farm.getLasso().isEmpty()) {
            farm.setLasso(held.copy());
            if (!player.getAbilities().instabuild) player.setItemInHand(hand, ItemStack.EMPTY);
            return InteractionResult.CONSUME;
        }
        NetworkHooks.openScreen((ServerPlayer) player, farm, pos);
        return InteractionResult.CONSUME;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof MobFarmBlockEntity farm) {
            for (ItemStack stack : farm.drops()) {
                Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), stack);
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("block.mobfarm.tooltip.speed", tier.ticks / 20).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("block.mobfarm.tooltip.use").withStyle(ChatFormatting.DARK_GRAY));
    }
}
