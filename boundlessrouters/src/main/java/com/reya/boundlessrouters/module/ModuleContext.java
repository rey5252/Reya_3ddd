package com.reya.boundlessrouters.module;

import javax.annotation.Nullable;

import com.reya.boundlessrouters.router.RouterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;

/** One module at work in its router: where, how, with what settings, and how much it may move. */
public final class ModuleContext {
    public final RouterBlockEntity router;
    public final ServerLevel level;
    public final BlockPos pos;
    /** The router's front. */
    public final Direction facing;
    public final int slot;
    public final ItemStack module;
    public final ModuleSettings settings;
    public final Filter filter;
    /** How many items a module moves each time its router runs (stack upgrades). */
    public final int itemsPerRun;
    /** How far it reaches (range upgrades). */
    public final int range;
    /** Whether the router has a redstone signal. */
    public final boolean powered;
    /** Whether the router keeps quiet (muffler upgrade). */
    public final boolean quiet;
    private final int[] weak, strong;

    public ModuleContext(RouterBlockEntity router, ServerLevel level, BlockPos pos, Direction facing, int slot, ItemStack module,
                         ModuleSettings settings, int itemsPerRun, int range, boolean powered, boolean quiet, int[] weak, int[] strong) {
        this.router = router;
        this.level = level;
        this.pos = pos;
        this.facing = facing;
        this.slot = slot;
        this.module = module;
        this.settings = settings;
        this.filter = settings.filter();
        this.itemsPerRun = itemsPerRun;
        this.range = range;
        this.powered = powered;
        this.quiet = quiet;
        this.weak = weak;
        this.strong = strong;
    }

    /** The module's direction in the world, or null for none. */
    @Nullable
    public Direction direction() {
        return settings.direction().toAbsolute(facing);
    }

    public ItemStackHandler buffer() {
        return router.buffer();
    }

    /** The module's own state changed (its stack's tag): the router saves it. */
    public void changed() {
        router.moduleChanged(slot);
    }

    /** A redstone signal out of one of the router's sides (the strongest wins). */
    public void emit(Direction side, int power, boolean strongSignal) {
        int i = side.get3DDataValue();
        weak[i] = Math.max(weak[i], power);
        if (strongSignal) strong[i] = Math.max(strong[i], power);
    }
}
