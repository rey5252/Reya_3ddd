package com.reya.cursedseed.block;

import java.util.Optional;

import com.reya.cursedseed.Config;
import com.reya.cursedseed.Empowerment;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Black, cursed ground. Monsters keep spawning on top of it (even in daylight)
 * and every monster born here is stronger than normal. Slowly spreads over
 * nearby grass and dirt, faster in darkness.
 */
public class CursedEarthBlock extends Block {

    public CursedEarthBlock(Properties properties) {
        super(properties);
    }

    /** Blocks that the Cursed Seed can curse and that Cursed Earth can spread to. */
    public static boolean canCurse(BlockState state) {
        return state.is(Blocks.GRASS_BLOCK)
                || state.is(Blocks.DIRT)
                || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.PODZOL)
                || state.is(Blocks.MYCELIUM)
                || state.is(Blocks.ROOTED_DIRT);
    }

    /** The block above must be open so monsters can stand on the cursed block. */
    public static boolean isExposed(BlockGetter level, BlockPos pos) {
        BlockPos above = pos.above();
        return !level.getBlockState(above).isSolidRender(level, above);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (random.nextDouble() < Config.SPAWN_CHANCE.get()) {
            trySpawnMonster(level, pos, random);
        }
        if (Config.SPREAD.get()) {
            trySpread(level, pos, random);
        }
    }

    private void trySpread(ServerLevel level, BlockPos pos, RandomSource random) {
        boolean dark = level.getMaxLocalRawBrightness(pos.above()) <= 7;
        if (random.nextInt(dark ? 2 : 10) != 0) return;

        BlockPos target = pos.offset(random.nextInt(3) - 1, random.nextInt(3) - 1, random.nextInt(3) - 1);
        if (canCurse(level.getBlockState(target)) && isExposed(level, target)) {
            level.setBlockAndUpdate(target, defaultBlockState());
        }
    }

    private void trySpawnMonster(ServerLevel level, BlockPos pos, RandomSource random) {
        if (level.getDifficulty() == Difficulty.PEACEFUL) return;
        if (!level.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING)) return;

        BlockPos spawnPos = pos.above();
        if (!level.getBlockState(spawnPos).getCollisionShape(level, spawnPos).isEmpty()) return;

        double x = spawnPos.getX() + 0.5D;
        double y = spawnPos.getY();
        double z = spawnPos.getZ() + 0.5D;
        if (!level.hasNearbyAlivePlayer(x, y, z, Config.PLAYER_RANGE.get())) return;

        AABB area = new AABB(pos).inflate(8.0D);
        if (level.getEntitiesOfClass(Monster.class, area).size() >= Config.MAX_NEARBY_MONSTERS.get()) return;

        Optional<MobSpawnSettings.SpawnerData> entry = level.getBiome(spawnPos).value()
                .getMobSettings().getMobs(MobCategory.MONSTER).getRandom(random);
        if (entry.isEmpty()) return;

        Entity entity = entry.get().type.create(level);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) entity.discard();
            return;
        }

        mob.moveTo(x, y, z, random.nextFloat() * 360.0F, 0.0F);
        if (!level.noCollision(mob)) {
            mob.discard();
            return;
        }

        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spawnPos), MobSpawnType.NATURAL, null, null);
        Empowerment.empower(mob);
        level.addFreshEntityWithPassengers(mob);

        level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 0.5D, z, 12, 0.3D, 0.5D, 0.3D, 0.02D);
        level.sendParticles(ParticleTypes.SOUL, x, y + 0.2D, z, 4, 0.3D, 0.2D, 0.3D, 0.01D);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(8) == 0 && isExposed(level, pos)) {
            level.addParticle(ParticleTypes.SMOKE,
                    pos.getX() + random.nextDouble(), pos.getY() + 1.05D, pos.getZ() + random.nextDouble(),
                    0.0D, 0.02D, 0.0D);
        }
    }
}
