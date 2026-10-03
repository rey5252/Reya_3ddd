package com.reya.starfall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reya.starfall.carve.CarveData;
import com.reya.starfall.carve.CarveEngine;
import com.reya.starfall.network.Net;
import com.reya.starfall.network.RemotePressPacket;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import org.slf4j.Logger;

/** Runs the strikes in flight and the carving they leave behind; also the /starfall command. */
public final class StarfallManager {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<ServerLevel, List<Strike>> STRIKES = new HashMap<>();

    /** Ticks from pressing the remote to the button bottoming out, when the weapon fires. */
    public static final int PRESS_FIRE = 9;
    private static final List<Press> PRESSES = new ArrayList<>();

    /** A press in progress: the cover is flipping open and the thumb is on its way to the button. */
    private record Press(ServerLevel level, UUID player, Skill skill, @Nullable BlockPos target, float yaw, long fireAt) {
    }

    /**
     * The player pressed the remote: their hand comes up, the thumb flips the cover and pushes the button, and
     * the weapon fires {@link #PRESS_FIRE} ticks later at what the crosshair was on when they pressed.
     * Returns false if there was nothing to aim at (the button still clicks, but nothing answers).
     */
    public static boolean press(ServerPlayer player, Skill skill) {
        ServerLevel level = player.serverLevel();
        BlockPos target = findTarget(player, Config.RANGE.get());
        PRESSES.add(new Press(level, player.getUUID(), skill, target, player.getYRot(), level.getGameTime() + PRESS_FIRE));
        Net.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> player), new RemotePressPacket(player.getId()));
        level.playSound(null, player.blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.6F, 1.8F);
        return target != null;
    }

    private static void tickPresses(ServerLevel level) {
        for (Iterator<Press> it = PRESSES.iterator(); it.hasNext(); ) {
            Press press = it.next();
            if (press.level() != level || level.getGameTime() < press.fireAt()) continue;
            it.remove();
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(press.player());
            BlockPos at = player != null ? player.blockPosition() : press.target();
            if (at != null) {
                level.playSound(null, at, SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 1.0F, 0.6F);
            }
            if (press.target() == null) {
                if (player != null) player.displayClientMessage(Component.translatable("message.starfall.no_target"), true);
                continue;
            }
            start(level, press.skill(), press.target(), press.yaw(), player != null && player.level() == level ? player : null);
        }
    }

    /** Fires a weapon at whatever the player's crosshair is on, right away. */
    public static boolean cast(ServerPlayer player, Skill skill) {
        BlockPos target = findTarget(player, Config.RANGE.get());
        if (target == null) {
            player.displayClientMessage(Component.translatable("message.starfall.no_target"), true);
            return false;
        }
        start(player.serverLevel(), skill, target, player.getYRot(), player);
        return true;
    }

    public static void start(ServerLevel level, Skill skill, BlockPos target, float yaw, @Nullable ServerPlayer caster) {
        Strike strike = new Strike(level, skill, target, yaw, caster);
        STRIKES.computeIfAbsent(level, l -> new ArrayList<>()).add(strike);
        strike.announce();
        if (caster != null) {
            caster.displayClientMessage(Component.translatable("message.starfall.locked", skill.tag(), skill.title(),
                    target.getX(), target.getY(), target.getZ()).withStyle(s -> s.withColor(skill.color)), true);
        }
        LOGGER.info("{} fired {} at {} in {}", caster == null ? "Server" : caster.getGameProfile().getName(), skill.id,
                target.toShortString(), level.dimension().location());
    }

    /**
     * The block under the crosshair, up to {@code range} blocks away. Stops at the first chunk that isn't
     * loaded rather than loading it, so a long look into the distance can't stall the server.
     */
    @Nullable
    public static BlockPos findTarget(ServerPlayer player, int range) {
        ServerLevel level = player.serverLevel();
        Vec3 from = player.getEyePosition();
        Vec3 to = from.add(player.getLookAngle().scale(range));
        ClipContext context = new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player);
        BlockHitResult miss = BlockHitResult.miss(to, Direction.UP, BlockPos.containing(to));
        BlockHitResult hit = BlockGetter.traverseBlocks(from, to, context, (ctx, pos) -> {
            if (level.isOutsideBuildHeight(pos)) return null;
            if (!level.getChunkSource().hasChunk(pos.getX() >> 4, pos.getZ() >> 4)) return miss;
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) return null;
            VoxelShape shape = ctx.getBlockShape(state, level, pos);
            return level.clipWithInteractionOverride(from, to, pos, shape, state);
        }, ctx -> miss);
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    @SubscribeEvent
    public void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) return;
        if (!PRESSES.isEmpty()) tickPresses(level);
        List<Strike> strikes = STRIKES.get(level);
        if (strikes != null) {
            for (Iterator<Strike> it = strikes.iterator(); it.hasNext(); ) {
                if (it.next().tick()) it.remove();
            }
        }
        CarveEngine.tick(level);
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        STRIKES.clear();
        PRESSES.clear();
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        List<String> ids = new ArrayList<>();
        for (Skill skill : Skill.values()) ids.add(skill.id);
        dispatcher.register(Commands.literal("starfall")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("cast")
                        .then(Commands.argument("skill", StringArgumentType.word())
                                .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(ids, builder))
                                .executes(ctx -> castCommand(ctx, null))
                                .then(Commands.argument("pos", BlockPosArgument.blockPos())
                                        .executes(ctx -> castCommand(ctx, BlockPosArgument.getLoadedBlockPos(ctx, "pos"))))))
                .then(Commands.literal("status").executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    CarveData data = CarveData.get(level);
                    List<Strike> strikes = STRIKES.get(level);
                    int flying = strikes == null ? 0 : strikes.size();
                    ctx.getSource().sendSuccess(() -> Component.translatable("command.starfall.status",
                            flying, data.jobCount(), data.pendingChunks()), false);
                    return data.jobCount();
                }))
                .then(Commands.literal("stop").executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    CarveData.get(level).clear();
                    STRIKES.remove(level);
                    ctx.getSource().sendSuccess(() -> Component.translatable("command.starfall.stopped"), true);
                    return 1;
                })));
    }

    private static int castCommand(CommandContext<CommandSourceStack> ctx, @Nullable BlockPos pos) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        String id = StringArgumentType.getString(ctx, "skill");
        Skill skill = null;
        for (Skill s : Skill.values()) if (s.id.equals(id)) skill = s;
        if (skill == null) {
            source.sendFailure(Component.translatable("command.starfall.unknown", id));
            return 0;
        }
        ServerPlayer player = source.getPlayer();
        if (pos == null) {
            if (player == null) {
                source.sendFailure(Component.translatable("command.starfall.need_pos"));
                return 0;
            }
            pos = findTarget(player, Config.RANGE.get());
            if (pos == null) {
                source.sendFailure(Component.translatable("message.starfall.no_target"));
                return 0;
            }
        }
        float yaw = player != null ? player.getYRot() : source.getRotation().y;
        start(source.getLevel(), skill, pos, yaw, player);
        return 1;
    }
}
