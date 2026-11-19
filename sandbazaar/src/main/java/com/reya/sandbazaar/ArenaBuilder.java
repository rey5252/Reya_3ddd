package com.reya.sandbazaar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reya.sandbazaar.map.BazaarLayout;
import com.reya.sandbazaar.map.Blueprint;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import org.slf4j.Logger;

/** Puts the arena into a world and takes it out again. */
public final class ArenaBuilder {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Tell clients, but drop nothing and wake no redstone. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;

    /** @param unknown block states of the blueprint this game does not know; they were left out */
    public record Result(int placed, int unknown) {}

    private ArenaBuilder() {}

    /** Where the arena's corner goes for someone standing at {@code feet}: just east of them, centred on them. */
    public static BlockPos originFor(BlockPos feet) {
        return feet.offset(3, -1, -BazaarLayout.D / 2);
    }

    /** Builds the arena with its (0, 0, 0) at {@code origin} and remembers the land it replaces. */
    public static Result build(ServerLevel level, BlockPos origin) {
        Blueprint bp = BazaarLayout.build();
        HolderLookup<Block> blocks = level.holderLookup(Registries.BLOCK);
        List<String> names = bp.palette();
        BlockState[] states = new BlockState[names.size()];
        int unknown = 0;
        for (int i = 0; i < states.length; i++) {
            try {
                states[i] = BlockStateParser.parseForBlock(blocks, names.get(i), false).blockState();
            } catch (CommandSyntaxException e) {
                LOGGER.warn("Sunbaked Bazaar: skipping unknown block state '{}'", names.get(i));
                states[i] = Blocks.AIR.defaultBlockState();
                unknown++;
            }
        }

        int count = bp.sizeX * bp.sizeY * bp.sizeZ;
        BlockState[] target = new BlockState[count];
        int[] before = new int[count];
        List<BlockState> beforePalette = new ArrayList<>();
        Map<BlockState, Integer> beforeIds = new HashMap<>();
        List<CompoundTag> beforeEntities = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int y = bp.minY; y <= bp.maxY; y++) {
            for (int z = 0; z < bp.sizeZ; z++) {
                for (int x = 0; x < bp.sizeX; x++, i++) {
                    int id = bp.paletteIndex(x, y, z);
                    if (id < 0) {
                        before[i] = -1;
                        continue;
                    }
                    target[i] = states[id];
                    pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);
                    BlockState old = level.getBlockState(pos);
                    Integer oldId = beforeIds.get(old);
                    if (oldId == null) {
                        oldId = beforePalette.size();
                        beforePalette.add(old);
                        beforeIds.put(old, oldId);
                    }
                    before[i] = oldId;
                    if (old.hasBlockEntity()) {
                        BlockEntity entity = level.getBlockEntity(pos);
                        if (entity != null) {
                            beforeEntities.add(entity.saveWithFullMetadata());
                            Clearable.tryClear(entity);
                        }
                    }
                }
            }
        }
        ArenaData.get(level).remember(origin, bp.sizeX, bp.sizeY, bp.sizeZ, bp.minY, beforePalette, before, beforeEntities);

        int placed = place(level, origin, bp.sizeX, bp.sizeY, bp.sizeZ, bp.minY, target, true);

        for (Blueprint.Sign sign : bp.signs()) {
            pos.set(origin.getX() + sign.x(), origin.getY() + sign.y(), origin.getZ() + sign.z());
            if (level.getBlockEntity(pos) instanceof SignBlockEntity entity) {
                SignText text = entity.getFrontText();
                for (int line = 0; line < 4; line++) {
                    text = text.setMessage(line, Component.translatable("sign.sandbazaar." + sign.key() + "." + (line + 1)));
                }
                entity.setText(text, true);
                entity.setText(text, false);
                entity.setWaxed(true);
            }
        }
        return new Result(placed, unknown);
    }

    /** Puts back the land the arena of this dimension replaced. Returns the blocks restored, or -1 without an arena. */
    public static int undo(ServerLevel level) {
        ArenaData data = ArenaData.get(level);
        BlockPos origin = data.origin();
        if (origin == null) {
            return -1;
        }
        int[] cells = data.cells();
        BlockState[] target = new BlockState[cells.length];
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int y = 0; y < data.sizeY(); y++) {
            for (int z = 0; z < data.sizeZ(); z++) {
                for (int x = 0; x < data.sizeX(); x++, i++) {
                    if (cells[i] < 0) {
                        continue;
                    }
                    target[i] = data.palette().get(cells[i]);
                    pos.set(origin.getX() + x, origin.getY() + data.minY() + y, origin.getZ() + z);
                    BlockState now = level.getBlockState(pos);
                    if (now.hasBlockEntity()) {
                        Clearable.tryClear(level.getBlockEntity(pos));
                    }
                    // lanterns, signs, carpets and the like go first and quietly, before the walls they hang on
                    if (!now.isAir() && now.getFluidState().isEmpty() && !now.isCollisionShapeFullBlock(level, pos)) {
                        level.setBlock(pos, air, FLAGS | Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
        int placed = place(level, origin, data.sizeX(), data.sizeY(), data.sizeZ(), data.minY(), target, false);
        for (CompoundTag tag : data.blockEntities()) {
            BlockPos at = BlockEntity.getPosFromTag(tag);
            BlockEntity entity = level.getBlockEntity(at);
            if (entity != null) {
                entity.load(tag);
                entity.setChanged();
                level.getChunkSource().blockChanged(at);
            }
        }
        data.forget();
        return placed;
    }

    /**
     * Sets every non-null state of a box. Solid blocks, air and water go first, bottom up; whatever leans on them
     * (doors, lanterns, carpets, signs...) follows, so nothing is placed before its support.
     *
     * @param connect work out fence, wall and bar connections for the second group, as /setblock does
     */
    private static int place(ServerLevel level, BlockPos origin, int sizeX, int sizeY, int sizeZ, int minY, BlockState[] target,
            boolean connect) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int placed = 0;
        for (int pass = 0; pass < 2; pass++) {
            int i = 0;
            for (int y = 0; y < sizeY; y++) {
                for (int z = 0; z < sizeZ; z++) {
                    for (int x = 0; x < sizeX; x++, i++) {
                        BlockState state = target[i];
                        if (state == null) {
                            continue;
                        }
                        pos.set(origin.getX() + x, origin.getY() + minY + y, origin.getZ() + z);
                        boolean plain = state.isAir() || !state.getFluidState().isEmpty() || state.isCollisionShapeFullBlock(level, pos);
                        if (plain != (pass == 0)) {
                            continue;
                        }
                        if (!plain && connect) {
                            BlockState shaped = Block.updateFromNeighbourShapes(state, level, pos);
                            if (!shaped.isAir()) {
                                state = shaped;
                            }
                        }
                        if (level.getBlockState(pos) != state && level.setBlock(pos, state, FLAGS)) {
                            placed++;
                        }
                    }
                }
            }
        }
        return placed;
    }
}
