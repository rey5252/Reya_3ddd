package com.reya.boundlessrouters.util;

import java.util.Comparator;

import javax.annotation.Nullable;

import com.reya.boundlessrouters.RouterConfig;
import com.reya.boundlessrouters.module.Target;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.WorldlyContainerHolder;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.SidedInvWrapper;

/** Finding the inventory at a place, here or anywhere a module is bound to. */
public final class Targets {
    /** Keeps a bound target's chunk loaded a while after each use (10 seconds). */
    public static final TicketType<ChunkPos> TICKET = TicketType.create("boundlessrouters_target", Comparator.comparingLong(ChunkPos::toLong), 200L);

    /** The inventory at a block, as seen from one of its faces, or null. The chunk must be loaded. */
    @Nullable
    public static IItemHandler inventoryAt(ServerLevel level, BlockPos pos, @Nullable Direction face) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be != null) {
            IItemHandler handler = be.getCapability(ForgeCapabilities.ITEM_HANDLER, face).resolve().orElse(null);
            if (handler != null) return handler;
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof WorldlyContainerHolder holder && face != null) {
            return new SidedInvWrapper(holder.getContainer(state, level, pos), face);
        }
        return null;
    }

    /**
     * The level a bound target is in, its chunk loaded if it wasn't (when the config lets it), or null: its
     * dimension is gone, or another dimension's targets are off, or its chunk isn't loaded and mustn't be.
     */
    @Nullable
    public static ServerLevel levelOf(ServerLevel from, Target target) {
        ServerLevel level = from.dimension().equals(target.dim()) ? from : from.getServer().getLevel(target.dim());
        if (level == null || (level != from && !RouterConfig.CROSS_DIMENSION.get())) return null;
        BlockPos pos = target.pos();
        if (!level.isInWorldBounds(pos)) return null;
        ChunkPos chunk = new ChunkPos(pos);
        if (!level.isLoaded(pos)) {
            if (!RouterConfig.LOAD_TARGETS.get()) return null;
            level.getChunkSource().addRegionTicket(TICKET, chunk, 1, chunk);
            level.getChunk(chunk.x, chunk.z);
        } else if (RouterConfig.LOAD_TARGETS.get() && level != from) {
            // keep a chunk in another dimension loaded while it is used
            level.getChunkSource().addRegionTicket(TICKET, chunk, 1, chunk);
        }
        return level;
    }

    /** The inventory a module is bound to, or null. */
    @Nullable
    public static IItemHandler inventoryAt(ServerLevel from, Target target) {
        ServerLevel level = levelOf(from, target);
        return level == null ? null : inventoryAt(level, target.pos(), target.face());
    }

    private Targets() {
    }
}
