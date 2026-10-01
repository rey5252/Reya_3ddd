package com.reya.boundlessrouters.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.reya.boundlessrouters.RouterConfig;
import com.reya.boundlessrouters.module.ModuleSettings.Operation;
import com.reya.boundlessrouters.module.ModuleSettings.Strategy;
import com.reya.boundlessrouters.util.Blocks;
import com.reya.boundlessrouters.util.Targets;
import com.reya.boundlessrouters.util.Transfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;
import net.minecraftforge.items.wrapper.PlayerArmorInvWrapper;
import net.minecraftforge.items.wrapper.PlayerMainInvWrapper;
import net.minecraftforge.items.wrapper.PlayerOffhandInvWrapper;

/**
 * What each kind of module does when its router runs. Each returns whether it did anything (a module set to
 * terminate stops the modules after it only then).
 */
public final class ModuleBehaviours {
    public static boolean run(ModuleContext ctx) {
        return switch (ctx.settings.kind()) {
            case SENDER -> send(ctx);
            case PULLER -> pull(ctx);
            case DISTRIBUTOR -> distribute(ctx);
            case DROPPER -> drop(ctx, false);
            case FLINGER -> drop(ctx, true);
            case PLACER -> place(ctx);
            case BREAKER -> breakBlock(ctx);
            case VACUUM -> vacuum(ctx);
            case VOID -> destroy(ctx);
            case PLAYER -> player(ctx);
            case DETECTOR -> detect(ctx);
            case EXTRUDER -> extrude(ctx);
        };
    }

    /** The buffer's stack, if the module's filter lets it work with it. */
    private static ItemStack buffered(ModuleContext ctx) {
        ItemStack stack = ctx.buffer().getStackInSlot(0);
        return !stack.isEmpty() && ctx.filter.test(stack) ? stack : ItemStack.EMPTY;
    }

    /** An inventory other than the router's own buffer (a module bound to its own router would just churn). */
    @Nullable
    private static IItemHandler other(ModuleContext ctx, @Nullable IItemHandler handler) {
        return handler == ctx.buffer() ? null : handler;
    }

    // ------------------------------------------------------------------ moving items

    private static boolean send(ModuleContext ctx) {
        if (buffered(ctx).isEmpty()) return false;
        IItemHandler to = null;
        List<Target> targets = ctx.settings.targets();
        if (!targets.isEmpty()) {
            to = other(ctx, Targets.inventoryAt(ctx.level, targets.get(0)));
        } else {
            Direction dir = ctx.direction();
            if (dir == null) return false;
            // the first inventory along the module's direction, through whatever stands between
            for (int k = 1; k <= ctx.range && to == null; k++) {
                BlockPos at = ctx.pos.relative(dir, k);
                if (!ctx.level.isLoaded(at)) break;
                to = other(ctx, Targets.inventoryAt(ctx.level, at, dir.getOpposite()));
            }
        }
        return to != null && Transfer.move(ctx.buffer(), to, ctx.itemsPerRun, ctx.filter) > 0;
    }

    private static boolean pull(ModuleContext ctx) {
        IItemHandler from;
        List<Target> targets = ctx.settings.targets();
        if (!targets.isEmpty()) {
            from = Targets.inventoryAt(ctx.level, targets.get(0));
        } else {
            Direction dir = ctx.direction();
            if (dir == null) return false;
            from = Targets.inventoryAt(ctx.level, ctx.pos.relative(dir), dir.getOpposite());
        }
        from = other(ctx, from);
        return from != null && Transfer.move(from, ctx.buffer(), ctx.itemsPerRun, ctx.filter) > 0;
    }

    private static boolean distribute(ModuleContext ctx) {
        if (buffered(ctx).isEmpty()) return false;
        List<Target> targets = ctx.settings.targets();
        if (targets.isEmpty()) return false;
        Strategy strategy = ctx.settings.strategy();
        for (int i : order(ctx, targets, strategy)) {
            IItemHandler to = other(ctx, Targets.inventoryAt(ctx.level, targets.get(i)));
            if (to == null) continue;
            if (Transfer.move(ctx.buffer(), to, ctx.itemsPerRun, ctx.filter) > 0) {
                if (strategy == Strategy.ROUND_ROBIN) {
                    ctx.settings.setNext((i + 1) % targets.size());
                    ctx.changed();
                }
                return true;
            }
        }
        return false;
    }

    /** The order a distributor tries its targets in. */
    private static List<Integer> order(ModuleContext ctx, List<Target> targets, Strategy strategy) {
        int n = targets.size();
        List<Integer> order = new ArrayList<>(n);
        if (strategy == Strategy.ROUND_ROBIN) {
            int start = Math.floorMod(ctx.settings.next(), n);
            for (int k = 0; k < n; k++) order.add((start + k) % n);
            return order;
        }
        for (int k = 0; k < n; k++) order.add(k);
        if (strategy == Strategy.RANDOM) {
            Collections.shuffle(order, new java.util.Random(ctx.level.random.nextLong()));
            return order;
        }
        // other dimensions count as farthest
        Comparator<Integer> byDistance = Comparator.comparingDouble(i -> {
            Target t = targets.get(i);
            return t.dim().equals(ctx.level.dimension()) ? t.pos().distSqr(ctx.pos) : Double.MAX_VALUE;
        });
        order.sort(strategy == Strategy.NEAREST ? byDistance : byDistance.reversed());
        return order;
    }

    // ------------------------------------------------------------------ into the world

    private static boolean drop(ModuleContext ctx, boolean fling) {
        Direction dir = ctx.direction();
        if (dir == null || buffered(ctx).isEmpty()) return false;
        BlockPos front = ctx.pos.relative(dir);
        if (ctx.level.getBlockState(front).isCollisionShapeFullBlock(ctx.level, front)) return false;
        ItemStack out = ctx.buffer().extractItem(0, ctx.itemsPerRun, false);
        if (out.isEmpty()) return false;
        Vec3 at = Vec3.atCenterOf(ctx.pos).add(dir.getStepX() * 0.7D, dir.getStepY() * 0.7D - (dir.getAxis().isHorizontal() ? 0.15D : 0.0D),
                dir.getStepZ() * 0.7D);
        ItemEntity item = new ItemEntity(ctx.level, at.x, at.y, at.z, out);
        if (fling) {
            item.setDeltaMovement(flingVelocity(ctx, dir));
            item.setPickUpDelay(20);
        } else {
            item.setDeltaMovement(dir.getStepX() * 0.05D, dir.getStepY() * 0.05D, dir.getStepZ() * 0.05D);
            item.setPickUpDelay(10);
        }
        ctx.level.addFreshEntity(item);
        if (!ctx.quiet) ctx.level.levelEvent(fling ? 1002 : 1000, ctx.pos, 0);
        return true;
    }

    /** A flinger's throw: its speed along its direction, turned up by its pitch and aside by its yaw. */
    private static Vec3 flingVelocity(ModuleContext ctx, Direction dir) {
        float yaw = (dir.getAxis().isHorizontal() ? dir : ctx.facing).toYRot() + ctx.settings.yaw();
        float pitch = (dir == Direction.UP ? -90.0F : dir == Direction.DOWN ? 90.0F : 0.0F) - ctx.settings.pitch();
        return Vec3.directionFromRotation(pitch, yaw).scale(ctx.settings.speed());
    }

    private static boolean place(ModuleContext ctx) {
        Direction dir = ctx.direction();
        ItemStack stack = buffered(ctx);
        if (dir == null || !(stack.getItem() instanceof BlockItem)) return false;
        if (!Blocks.place(ctx.level, ctx.pos, dir, ctx.facing, ctx.pos.relative(dir), stack)) return false;
        ctx.buffer().extractItem(0, 1, false);
        return true;
    }

    private static ItemStack tool(ModuleSettings settings) {
        ItemStack tool = new ItemStack(Items.NETHERITE_PICKAXE);
        if (settings.silk()) tool.enchant(Enchantments.SILK_TOUCH, 1);
        else if (settings.fortune() > 0) tool.enchant(Enchantments.BLOCK_FORTUNE, settings.fortune());
        return tool;
    }

    private static boolean breakBlock(ModuleContext ctx) {
        Direction dir = ctx.direction();
        if (dir == null) return false;
        BlockPos at = ctx.pos.relative(dir);
        BlockState state = ctx.level.getBlockState(at);
        ItemStack asItem = new ItemStack(state.getBlock().asItem());
        if (asItem.isEmpty() ? !ctx.settings.blacklist() : !ctx.filter.test(asItem)) return false;
        return breakInto(ctx, dir, at, tool(ctx.settings));
    }

    /** Breaks a block into the buffer, if the buffer takes some of what it drops; the rest falls where it stood. */
    private static boolean breakInto(ModuleContext ctx, Direction dir, BlockPos at, ItemStack tool) {
        Blocks.Breaking breaking = Blocks.tryBreak(ctx.level, ctx.pos, dir, ctx.facing, at, tool);
        if (breaking == null) return false;
        List<ItemStack> drops = breaking.drops();
        if (!drops.isEmpty() && drops.stream().noneMatch(drop -> Transfer.accepts(ctx.buffer(), drop))) return false;
        Blocks.doBreak(ctx.level, ctx.pos, dir, ctx.facing, at, tool, breaking.exp(), ctx.quiet);
        for (ItemStack drop : drops) {
            ItemStack left = Transfer.insert(ctx.buffer(), drop);
            if (!left.isEmpty()) Block.popResource(ctx.level, at, left);
        }
        return true;
    }

    private static boolean vacuum(ModuleContext ctx) {
        int radius = ctx.settings.radius();
        Direction dir = ctx.direction();
        Vec3 centre = Vec3.atCenterOf(dir == null ? ctx.pos : ctx.pos.relative(dir, radius + 1));
        AABB box = AABB.ofSize(centre, radius * 2 + 1, radius * 2 + 1, radius * 2 + 1);
        List<ItemEntity> items = ctx.level.getEntitiesOfClass(ItemEntity.class, box,
                item -> item.isAlive() && !item.hasPickUpDelay() && ctx.filter.test(item.getItem()));
        boolean took = false;
        for (ItemEntity item : items) {
            ItemStack stack = item.getItem();
            ItemStack left = Transfer.insert(ctx.buffer(), stack.copy());
            if (left.getCount() == stack.getCount()) continue;
            took = true;
            if (left.isEmpty()) item.discard();
            else item.setItem(left);
        }
        if (took && !ctx.quiet) {
            ctx.level.playSound(null, ctx.pos, SoundEvents.ITEM_PICKUP, SoundSource.BLOCKS, 0.25F, 0.8F + ctx.level.random.nextFloat() * 0.4F);
        }
        return took;
    }

    private static boolean destroy(ModuleContext ctx) {
        if (buffered(ctx).isEmpty()) return false;
        return !ctx.buffer().extractItem(0, ctx.itemsPerRun, false).isEmpty();
    }

    // ------------------------------------------------------------------ players

    private static boolean player(ModuleContext ctx) {
        UUID id = ctx.settings.playerId();
        if (id == null) return false;
        ServerPlayer player = ctx.level.getServer().getPlayerList().getPlayer(id);
        if (player == null || (player.level() != ctx.level && !RouterConfig.CROSS_DIMENSION.get())) return false;
        IItemHandler inventory = switch (ctx.settings.section()) {
            case MAIN -> new PlayerMainInvWrapper(player.getInventory());
            case ARMOR -> new PlayerArmorInvWrapper(player.getInventory());
            case OFFHAND -> new PlayerOffhandInvWrapper(player.getInventory());
            case ENDER -> new InvWrapper(player.getEnderChestInventory());
        };
        int moved = ctx.settings.operation() == Operation.EXTRACT
                ? Transfer.move(inventory, ctx.buffer(), ctx.itemsPerRun, ctx.filter)
                : Transfer.move(ctx.buffer(), inventory, ctx.itemsPerRun, ctx.filter);
        return moved > 0;
    }

    // ------------------------------------------------------------------ redstone and blocks

    private static boolean detect(ModuleContext ctx) {
        Direction dir = ctx.direction();
        if (dir == null || buffered(ctx).isEmpty()) return false;
        ctx.emit(dir, ctx.settings.power(), ctx.settings.strong());
        return true;
    }

    private static boolean extrude(ModuleContext ctx) {
        Direction dir = ctx.direction();
        if (dir == null) return false;
        int extended = ctx.settings.extended();
        if (ctx.powered) {
            if (extended >= ctx.range) return false;
            ItemStack stack = buffered(ctx);
            if (!(stack.getItem() instanceof BlockItem)) return false;
            if (!Blocks.place(ctx.level, ctx.pos, dir, ctx.facing, ctx.pos.relative(dir, extended + 1), stack)) return false;
            ctx.buffer().extractItem(0, 1, false);
            ctx.settings.setExtended(extended + 1);
            ctx.changed();
            return true;
        }
        if (extended <= 0) return false;
        BlockPos at = ctx.pos.relative(dir, extended);
        BlockState state = ctx.level.getBlockState(at);
        if (state.isAir() || state.canBeReplaced()) {
            // the block went some other way: the line is a block shorter
            ctx.settings.setExtended(extended - 1);
            ctx.changed();
            return true;
        }
        ItemStack silk = new ItemStack(Items.NETHERITE_PICKAXE);
        silk.enchant(Enchantments.SILK_TOUCH, 1);
        if (!breakInto(ctx, dir, at, silk)) return false;
        ctx.settings.setExtended(extended - 1);
        ctx.changed();
        return true;
    }

    private ModuleBehaviours() {
    }
}
