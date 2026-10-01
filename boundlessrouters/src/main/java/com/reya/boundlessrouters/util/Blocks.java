package com.reya.boundlessrouters.util;

import java.util.List;

import com.reya.boundlessrouters.RouterConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.BlockEvent;

/** Placing and breaking blocks for the router, as a player would (so protection mods have their say). */
public final class Blocks {
    /**
     * Places one block of a stack at a spot, as though clicked onto it from the router's side; returns whether it
     * went (the stack itself isn't changed: take the item from where it came from).
     */
    public static boolean place(ServerLevel level, BlockPos router, Direction look, Direction facing, BlockPos at, ItemStack from) {
        if (!(from.getItem() instanceof BlockItem) || !level.isInWorldBounds(at)) return false;
        if (!level.getBlockState(at).canBeReplaced()) return false;
        ItemStack one = from.copyWithCount(1);
        FakePlayer player = FakePlayers.at(level, router, look, facing, one);
        try {
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(at), look.getOpposite(), at, false);
            InteractionResult result = one.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
            return result.consumesAction();
        } finally {
            FakePlayers.done(player);
        }
    }

    /** What breaking a block would give, and the experience, if it may be broken (null if not). */
    public record Breaking(List<ItemStack> drops, int exp) {
    }

    /**
     * Asks whether a block may be broken with a tool (never air, fluids or unbreakable blocks; protection mods may
     * refuse), and what it would drop.
     */
    @javax.annotation.Nullable
    public static Breaking tryBreak(ServerLevel level, BlockPos router, Direction look, Direction facing, BlockPos at, ItemStack tool) {
        BlockState state = level.getBlockState(at);
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.getDestroySpeed(level, at) < 0.0F) return null;
        FakePlayer player = FakePlayers.at(level, router, look, facing, tool);
        try {
            if (!RouterConfig.BREAK_ANY_TOOL.get() && !state.canHarvestBlock(level, at, player)) return null;
            BlockEvent.BreakEvent event = new BlockEvent.BreakEvent(level, at, state, player);
            if (MinecraftForge.EVENT_BUS.post(event)) return null;
            BlockEntity be = level.getBlockEntity(at);
            return new Breaking(Block.getDrops(state, level, at, be, player, tool), event.getExpToDrop());
        } finally {
            FakePlayers.done(player);
        }
    }

    /** Breaks a block (tryBreak said it may), with its sound and particles unless quiet, and drops its experience. */
    public static void doBreak(ServerLevel level, BlockPos router, Direction look, Direction facing, BlockPos at, ItemStack tool, int exp, boolean quiet) {
        BlockState state = level.getBlockState(at);
        FakePlayer player = FakePlayers.at(level, router, look, facing, tool);
        try {
            if (!quiet) level.levelEvent(2001, at, Block.getId(state));
            state.onDestroyedByPlayer(level, at, player, true, level.getFluidState(at));
            state.spawnAfterBreak(level, at, tool, false);
            if (exp > 0) ExperienceOrb.award(level, Vec3.atCenterOf(at), exp);
        } finally {
            FakePlayers.done(player);
        }
    }

    private Blocks() {
    }
}
