package com.reya.sandbazaar;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.Dynamic2CommandExceptionType;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import com.reya.sandbazaar.map.BazaarLayout;
import com.reya.sandbazaar.map.Blueprint;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * /bazaar build        - build the arena just east of you
 * /bazaar undo         - remove it and put the land back
 * /bazaar tp &lt;place&gt;   - go to attack, defend, a, b or plaza
 */
public final class BazaarCommand {
    private static final SimpleCommandExceptionType EXISTS =
            new SimpleCommandExceptionType(Component.translatable("commands.sandbazaar.build.exists"));
    private static final SimpleCommandExceptionType NONE =
            new SimpleCommandExceptionType(Component.translatable("commands.sandbazaar.none"));
    private static final Dynamic2CommandExceptionType HEIGHT =
            new Dynamic2CommandExceptionType((min, max) -> Component.translatable("commands.sandbazaar.build.height", min, max));
    private static final DynamicCommandExceptionType UNKNOWN_PLACE =
            new DynamicCommandExceptionType(place -> Component.translatable("commands.sandbazaar.tp.unknown", place));

    private static final SuggestionProvider<CommandSourceStack> PLACES = (context, builder) ->
            SharedSuggestionProvider.suggest(BazaarLayout.MARKS.keySet(), builder);

    private BazaarCommand() {}

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("bazaar")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("build").executes(BazaarCommand::build))
                .then(Commands.literal("undo").executes(BazaarCommand::undo))
                .then(Commands.literal("tp")
                        .then(Commands.argument("place", StringArgumentType.word()).suggests(PLACES)
                                .executes(BazaarCommand::teleport))));
    }

    private static int build(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        if (ArenaData.get(level).exists()) {
            throw EXISTS.create();
        }
        BlockPos origin = ArenaBuilder.originFor(BlockPos.containing(source.getPosition()));
        // the ground under the builder's feet becomes the street level
        int lowest = level.getMinBuildHeight() - BazaarLayout.MIN_Y + 1;
        int highest = level.getMaxBuildHeight() - BazaarLayout.MAX_Y - 1;
        if (origin.getY() + 1 < lowest || origin.getY() + 1 > highest) {
            throw HEIGHT.create(lowest, highest);
        }
        ArenaBuilder.Result result = ArenaBuilder.build(level, origin);
        source.sendSuccess(() -> Component.translatable("commands.sandbazaar.build.success", result.placed()), true);
        return result.placed();
    }

    private static int undo(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        int restored = ArenaBuilder.undo(source.getLevel());
        if (restored < 0) {
            throw NONE.create();
        }
        source.sendSuccess(() -> Component.translatable("commands.sandbazaar.undo.success", restored), true);
        return restored;
    }

    private static int teleport(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        BlockPos origin = ArenaData.get(level).origin();
        if (origin == null) {
            throw NONE.create();
        }
        String place = StringArgumentType.getString(context, "place");
        Blueprint.Mark mark = BazaarLayout.MARKS.get(place);
        if (mark == null) {
            throw UNKNOWN_PLACE.create(place);
        }
        player.teleportTo(level, origin.getX() + mark.x() + 0.5, origin.getY() + mark.y(), origin.getZ() + mark.z() + 0.5,
                mark.yaw(), 0);
        return 1;
    }
}
