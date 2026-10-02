package com.reya.boundlessrouters.module;

import java.util.List;

import javax.annotation.Nullable;

import com.reya.boundlessrouters.RouterConfig;
import com.reya.boundlessrouters.network.TransferFxPacket;
import com.reya.boundlessrouters.router.RouterBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
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
    /** How far it reaches, in blocks (range upgrades), or RouterBlockEntity.INFINITE. */
    public final int range;
    /** Whether the router has a redstone signal. */
    public final boolean powered;
    /** Whether the router keeps quiet (muffler upgrade). */
    public final boolean quiet;
    private final int[] weak, strong;
    private final List<TransferFxPacket.Flight> flights;

    public ModuleContext(RouterBlockEntity router, ServerLevel level, BlockPos pos, Direction facing, int slot, ItemStack module,
                         ModuleSettings settings, int itemsPerRun, int range, boolean powered, boolean quiet, int[] weak, int[] strong,
                         List<TransferFxPacket.Flight> flights) {
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
        this.flights = flights;
    }

    // ------------------------------------------------------------------ reach

    public boolean infinite() {
        return range == RouterBlockEntity.INFINITE;
    }

    /** How far a sender looks along its direction and an extruder builds: the range, never past the scan limit. */
    public int reach() {
        int limit = RouterConfig.SCAN_LIMIT.get();
        return infinite() ? limit : Math.min(range, limit);
    }

    /** Whether the router reaches a point in its own level. */
    public boolean reaches(Vec3 point) {
        if (infinite()) return true;
        return Vec3.atCenterOf(pos).distanceToSqr(point) <= (double) range * range;
    }

    /** Whether the router reaches a place a module is bound to (another dimension only with an infinite range upgrade). */
    public boolean reaches(Target target) {
        if (!target.dim().equals(level.dimension())) return infinite() && RouterConfig.INFINITE_OTHER_DIMENSIONS.get();
        return reaches(Vec3.atCenterOf(target.pos()));
    }

    // ------------------------------------------------------------------ what players see

    /** An item flying from one point to another along a line in the module's colour (not with a muffler). */
    public void show(Vec3 from, Vec3 to, ItemStack item) {
        if (quiet || item.isEmpty() || flights.size() >= TransferFxPacket.MAX) return;
        flights.add(new TransferFxPacket.Flight(from, to, item.copyWithCount(1), settings.kind().colour()));
    }

    /** An item going from the router to a block (from face to face, so it is seen between them). */
    public void showTo(BlockPos target, ItemStack item) {
        if (target.equals(pos)) return;
        show(faceTowards(pos, target), faceTowards(target, pos), item);
    }

    /** An item coming from a block into the router. */
    public void showFrom(BlockPos source, ItemStack item) {
        if (source.equals(pos)) return;
        show(faceTowards(source, pos), faceTowards(pos, source), item);
    }

    /** Where the line from block a's middle to block b's leaves block a. */
    public static Vec3 faceTowards(BlockPos a, BlockPos b) {
        Vec3 ca = Vec3.atCenterOf(a), d = Vec3.atCenterOf(b).subtract(ca);
        double m = Math.max(Math.abs(d.x), Math.max(Math.abs(d.y), Math.abs(d.z)));
        return m == 0.0D ? ca : ca.add(d.scale(0.52D / m));
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
