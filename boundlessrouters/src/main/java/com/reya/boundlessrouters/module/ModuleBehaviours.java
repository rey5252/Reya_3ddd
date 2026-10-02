package com.reya.boundlessrouters.module;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.google.common.collect.Multimap;
import com.reya.boundlessrouters.RouterConfig;
import com.reya.boundlessrouters.module.ModuleSettings.Operation;
import com.reya.boundlessrouters.module.ModuleSettings.Strategy;
import com.reya.boundlessrouters.router.RouterBlockEntity;
import com.reya.boundlessrouters.util.Blocks;
import com.reya.boundlessrouters.util.RouterPlayer;
import com.reya.boundlessrouters.util.Targets;
import com.reya.boundlessrouters.util.Transfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
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
            case ACTIVATOR -> activate(ctx);
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
        ItemStack what = buffered(ctx).copy();
        IItemHandler to = null;
        BlockPos where = null;
        List<Target> targets = ctx.settings.targets();
        if (!targets.isEmpty()) {
            Target target = targets.get(0);
            if (!ctx.reaches(target)) return false;
            to = other(ctx, Targets.inventoryAt(ctx.level, target));
            if (target.dim().equals(ctx.level.dimension())) where = target.pos();
        } else {
            Direction dir = ctx.direction();
            if (dir == null) return false;
            // the first inventory along the module's direction, through whatever stands between
            for (int k = 1; k <= ctx.reach() && to == null; k++) {
                BlockPos at = ctx.pos.relative(dir, k);
                if (!ctx.level.isLoaded(at)) break;
                to = other(ctx, Targets.inventoryAt(ctx.level, at, dir.getOpposite()));
                where = at;
            }
        }
        if (to == null || Transfer.move(ctx.buffer(), to, ctx.itemsPerRun, ctx.filter) <= 0) return false;
        if (where != null) ctx.showTo(where, what);
        return true;
    }

    private static boolean pull(ModuleContext ctx) {
        IItemHandler from;
        BlockPos where = null;
        List<Target> targets = ctx.settings.targets();
        if (!targets.isEmpty()) {
            Target target = targets.get(0);
            if (!ctx.reaches(target)) return false;
            from = Targets.inventoryAt(ctx.level, target);
            if (target.dim().equals(ctx.level.dimension())) where = target.pos();
        } else {
            Direction dir = ctx.direction();
            if (dir == null) return false;
            where = ctx.pos.relative(dir);
            from = Targets.inventoryAt(ctx.level, where, dir.getOpposite());
        }
        from = other(ctx, from);
        if (from == null || Transfer.move(from, ctx.buffer(), ctx.itemsPerRun, ctx.filter) <= 0) return false;
        if (where != null) ctx.showFrom(where, ctx.buffer().getStackInSlot(0));
        return true;
    }

    private static boolean distribute(ModuleContext ctx) {
        if (buffered(ctx).isEmpty()) return false;
        List<Target> targets = ctx.settings.targets();
        if (targets.isEmpty()) return false;
        Strategy strategy = ctx.settings.strategy();
        ItemStack what = buffered(ctx).copy();
        for (int i : order(ctx, targets, strategy)) {
            Target target = targets.get(i);
            if (!ctx.reaches(target)) continue;
            IItemHandler to = other(ctx, Targets.inventoryAt(ctx.level, target));
            if (to == null) continue;
            if (Transfer.move(ctx.buffer(), to, ctx.itemsPerRun, ctx.filter) > 0) {
                if (target.dim().equals(ctx.level.dimension())) ctx.showTo(target.pos(), what);
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
        ItemStack what = stack.copy();
        if (!Blocks.place(ctx.level, ctx.pos, dir, ctx.facing, ctx.pos.relative(dir), stack)) return false;
        ctx.buffer().extractItem(0, 1, false);
        ctx.showTo(ctx.pos.relative(dir), what);
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
        if (!drops.isEmpty()) ctx.showFrom(at, drops.get(0));
        for (ItemStack drop : drops) {
            ItemStack left = Transfer.insert(ctx.buffer(), drop);
            if (!left.isEmpty()) Block.popResource(ctx.level, at, left);
        }
        return true;
    }

    private static boolean vacuum(ModuleContext ctx) {
        int radius = Math.min(ctx.settings.radius(), ctx.reach());
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
            ctx.show(item.position().add(0.0D, 0.25D, 0.0D), ModuleContext.faceTowards(ctx.pos, item.blockPosition()), stack);
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
        if (player == null) return false;
        boolean here = player.level() == ctx.level;
        if (here ? !ctx.reaches(player.position()) : !(ctx.infinite() && RouterConfig.INFINITE_OTHER_DIMENSIONS.get())) return false;
        IItemHandler inventory = switch (ctx.settings.section()) {
            case MAIN -> new PlayerMainInvWrapper(player.getInventory());
            case ARMOR -> new PlayerArmorInvWrapper(player.getInventory());
            case OFFHAND -> new PlayerOffhandInvWrapper(player.getInventory());
            case ENDER -> new InvWrapper(player.getEnderChestInventory());
        };
        boolean extract = ctx.settings.operation() == Operation.EXTRACT;
        ItemStack what = buffered(ctx).copy();
        int moved = extract
                ? Transfer.move(inventory, ctx.buffer(), ctx.itemsPerRun, ctx.filter)
                : Transfer.move(ctx.buffer(), inventory, ctx.itemsPerRun, ctx.filter);
        if (moved <= 0) return false;
        if (here) {
            Vec3 at = player.position().add(0.0D, player.getBbHeight() * 0.6D, 0.0D);
            Vec3 face = ModuleContext.faceTowards(ctx.pos, player.blockPosition());
            if (extract) ctx.show(at, face, ctx.buffer().getStackInSlot(0));
            else ctx.show(face, at, what);
        }
        return true;
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
            if (extended >= ctx.reach()) return false;
            ItemStack stack = buffered(ctx);
            if (!(stack.getItem() instanceof BlockItem)) return false;
            ItemStack what = stack.copy();
            if (!Blocks.place(ctx.level, ctx.pos, dir, ctx.facing, ctx.pos.relative(dir, extended + 1), stack)) return false;
            ctx.buffer().extractItem(0, 1, false);
            ctx.showTo(ctx.pos.relative(dir, extended + 1), what);
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

    // ------------------------------------------------------------------ a player's clicks

    /** How far an activator reaches for a block or a creature, as a player's arm does. */
    private static final int ARM = 3;

    /**
     * An activator: the router's player stands at the router's face, holding the buffer's item (if the filter lets
     * it), and clicks. What it holds afterwards goes back into the buffer; anything else it is left with (a filled
     * bucket from a stack of empty ones) too, or falls in front of the router.
     */
    private static boolean activate(ModuleContext ctx) {
        Direction dir = ctx.direction();
        if (dir == null) return false;
        ItemStack inBuffer = ctx.buffer().getStackInSlot(0);
        ItemStack held = !inBuffer.isEmpty() && ctx.filter.test(inBuffer) ? ctx.buffer().extractItem(0, inBuffer.getCount(), false) : ItemStack.EMPTY;
        RouterPlayer player = RouterPlayer.get(ctx.level);
        Vec3 eyes = ModuleContext.faceTowards(ctx.pos, ctx.pos.relative(dir));
        player.ready(eyes, dir, ctx.facing, held, ctx.settings.sneak());
        try {
            return switch (ctx.settings.action()) {
                case USE_BLOCK -> useOnBlock(ctx, player, dir);
                case USE_AIR -> useInAir(ctx, player);
                case USE_ENTITY -> onCreature(ctx, player, dir, false);
                case HIT_BLOCK -> hitBlock(ctx, player, dir);
                case DIG_BLOCK -> dig(ctx, player, dir);
                case ATTACK -> onCreature(ctx, player, dir, true);
            };
        } finally {
            List<ItemStack> back = new ArrayList<>();
            back.add(player.takeHeld());
            back.addAll(player.takeRest());
            for (ItemStack stack : back) {
                ItemStack left = stack.isEmpty() ? stack : Transfer.insert(ctx.buffer(), stack);
                if (!left.isEmpty()) {
                    ItemEntity item = new ItemEntity(ctx.level, eyes.x, eyes.y - 0.2D, eyes.z, left);
                    item.setDeltaMovement(dir.getStepX() * 0.05D, dir.getStepY() * 0.05D, dir.getStepZ() * 0.05D);
                    ctx.level.addFreshEntity(item);
                }
            }
        }
    }

    /** The first block in a direction from the router within an arm's reach, or null. */
    @Nullable
    private static BlockPos reachBlock(ModuleContext ctx, Direction dir) {
        for (int k = 1; k <= ARM; k++) {
            BlockPos at = ctx.pos.relative(dir, k);
            if (!ctx.level.isLoaded(at)) return null;
            if (!ctx.level.getBlockState(at).isAir()) return at;
        }
        return null;
    }

    /** A right click on the near face of the block in front, as a player's: the block first (a lever, a door), then the item. */
    private static boolean useOnBlock(ModuleContext ctx, RouterPlayer player, Direction dir) {
        BlockPos at = reachBlock(ctx, dir);
        if (at == null) return false;
        Direction face = dir.getOpposite();
        Vec3 point = Vec3.atCenterOf(at).add(face.getStepX() * 0.5D, face.getStepY() * 0.5D, face.getStepZ() * 0.5D);
        InteractionResult result = player.gameMode.useItemOn(player, ctx.level, player.getMainHandItem(), InteractionHand.MAIN_HAND,
                new BlockHitResult(point, face, at, false));
        player.finishUsing();
        return result.consumesAction();
    }

    /** A right click in the air: throwing, scooping water with a bucket, drawing a bow (loosed at full draw). */
    private static boolean useInAir(ModuleContext ctx, RouterPlayer player) {
        InteractionResult result = player.gameMode.useItem(player, ctx.level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
        boolean using = player.isUsingItem();
        player.finishUsing();
        return result.consumesAction() || using;
    }

    /** A click on the nearest creature in front within an arm's reach: a right click (shearing, milking, feeding) or a hit. */
    private static boolean onCreature(ModuleContext ctx, RouterPlayer player, Direction dir, boolean attack) {
        BlockPos front = ctx.pos.relative(dir);
        AABB box = new AABB(front).expandTowards(dir.getStepX() * (ARM - 1.0D), dir.getStepY() * (ARM - 1.0D), dir.getStepZ() * (ARM - 1.0D));
        Vec3 eyes = player.getEyePosition();
        Entity target = null;
        double best = Double.MAX_VALUE;
        for (Entity e : ctx.level.getEntities(player, box, e -> e.isAlive() && !e.isSpectator() && !(e instanceof ItemEntity)
                && !(e instanceof ExperienceOrb) && (!attack || e.isAttackable()))) {
            double d = e.distanceToSqr(eyes);
            if (d < best) {
                best = d;
                target = e;
            }
        }
        if (target == null) return false;
        if (!attack) {
            InteractionResult result = player.interactOn(target, InteractionHand.MAIN_HAND);
            player.finishUsing();
            return result.consumesAction();
        }
        // its hit is as strong as the item makes a player's (a fake player doesn't tick, so wears its item's attributes only now)
        Multimap<Attribute, AttributeModifier> modifiers = player.getMainHandItem().getAttributeModifiers(EquipmentSlot.MAINHAND);
        player.getAttributes().addTransientAttributeModifiers(modifiers);
        try {
            player.attack(target);
        } finally {
            player.getAttributes().removeAttributeModifiers(modifiers);
        }
        return true;
    }

    /** A left click on the block in front: one hit (a note block sounds, redstone ore glows). */
    private static boolean hitBlock(ModuleContext ctx, RouterPlayer player, Direction dir) {
        BlockPos at = reachBlock(ctx, dir);
        if (at == null) return false;
        ctx.level.getBlockState(at).attack(ctx.level, at, player);
        return true;
    }

    /**
     * The left button held on the block in front: it digs as long as a player holding the buffer's item would (cracks
     * showing), then breaks it as a player: what it drops falls where it stood, the tool wears.
     */
    private static boolean dig(ModuleContext ctx, RouterPlayer player, Direction dir) {
        RouterBlockEntity.Dig dig = ctx.router.dig(ctx.slot);
        BlockPos at = reachBlock(ctx, dir);
        if (at == null) {
            dig.reset(ctx.level);
            return false;
        }
        BlockState state = ctx.level.getBlockState(at);
        if (state.getDestroySpeed(ctx.level, at) < 0.0F) {
            dig.reset(ctx.level);
            return false;
        }
        if (!at.equals(dig.pos) || state != dig.state) {
            // a new block: the first hit
            dig.reset(ctx.level);
            dig.pos = at;
            dig.state = state;
            state.attack(ctx.level, at, player);
        }
        // as much digging as a player gets done between two of the router's runs
        dig.progress += state.getDestroyProgress(player, ctx.level, at) * ctx.router.interval();
        if (dig.progress >= 1.0F) {
            dig.reset(ctx.level);
            return player.gameMode.destroyBlock(at);
        }
        ctx.level.destroyBlockProgress(dig.id, at, Math.min(9, (int) (dig.progress * 10.0F)));
        return true;
    }

    private ModuleBehaviours() {
    }
}
