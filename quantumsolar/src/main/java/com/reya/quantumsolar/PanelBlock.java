package com.reya.quantumsolar;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.network.NetworkHooks;

/** A solar panel or quantum generator: makes FE and pushes it into its neighbours; right click shows it. */
public class PanelBlock extends BaseEntityBlock {
    private final Panels.Panel panel;

    public PanelBlock(Panels.Panel panel, Properties properties) {
        super(properties);
        this.panel = panel;
    }

    public Panels.Panel panel() {
        return panel;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PanelBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, QuantumSolar.PANEL_BE.get(), PanelBlockEntity::serverTick);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer sp && level.getBlockEntity(pos) instanceof PanelBlockEntity be) {
            NetworkHooks.openScreen(sp, be, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable BlockGetter level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(panel.quantum() ? "tooltip.quantumsolar.quantum" : "tooltip.quantumsolar.solar", panel.tier() + 1)
                .withStyle(panel.quantum() ? ChatFormatting.LIGHT_PURPLE : ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.quantumsolar.generation", Format.energy(panel.generation())).withStyle(ChatFormatting.GREEN));
        tooltip.add(Component.translatable("tooltip.quantumsolar.capacity", Format.energy(panel.capacity())).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(panel.quantum() ? "tooltip.quantumsolar.quantum.tip" : "tooltip.quantumsolar.solar.tip")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
