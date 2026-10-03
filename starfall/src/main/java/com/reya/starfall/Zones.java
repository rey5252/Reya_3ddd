package com.reya.starfall;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

import javax.annotation.Nullable;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** What happens to whoever stands where a weapon lands. */
public final class Zones {
    /** Bypasses armour, totems, resistance and invulnerability: nothing survives it. */
    public static final ResourceKey<DamageType> STELLAR = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(Starfall.MODID, "stellar"));
    /** Gungnir's shock past the crater: an ordinary, armour-respecting blow. */
    public static final ResourceKey<DamageType> SHOCKWAVE = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(Starfall.MODID, "shockwave"));

    public enum Creative {
        /** Keep creative players hovering where they are (SS-01). */
        EVACUATE,
        /** Leave creative players alone (SS-03). */
        IGNORE,
        /** Leave them alone and spare the ground under their feet (SS-04). */
        SPARE
    }

    public static DamageSource source(ServerLevel level, ResourceKey<DamageType> type, @Nullable Entity attacker) {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type), attacker);
    }

    /**
     * Everything inside {@code inside(x, z)} dies. Returns the creative players found there (for SPARE).
     */
    public static List<Player> obliterate(ServerLevel level, AABB box, BiPredicate<Double, Double> inside,
                                          @Nullable Entity attacker, Creative creative) {
        DamageSource source = source(level, STELLAR, attacker);
        List<Entity> found = level.getEntities((Entity) null, box, e -> inside.test(e.getX(), e.getZ()));
        List<Player> spared = new ArrayList<>();
        for (Entity entity : found) {
            if (entity.isRemoved()) continue;
            if (entity instanceof Player player) {
                if (player.isSpectator()) continue;
                if (player.isCreative()) {
                    spared.add(player);
                    if (creative == Creative.EVACUATE) evacuate(level, player);
                    continue;
                }
            }
            kill(entity, source);
        }
        return spared;
    }

    /** Kills for good: past totems, second phases and cancelled damage. */
    public static void kill(Entity entity, DamageSource source) {
        if (entity instanceof LivingEntity living) {
            living.invulnerableTime = 0;
            living.hurt(source, Float.MAX_VALUE);
            if (living.isAlive()) living.kill();
            if (living.isAlive() && !(living instanceof Player)) living.discard();
        } else {
            entity.discard();
        }
    }

    /** A creative player in the beam stays where they are, hovering, while the ground goes out from under them. */
    private static void evacuate(ServerLevel level, Player player) {
        if (!(player instanceof ServerPlayer sp) || sp.getAbilities().flying) return;
        sp.getAbilities().flying = true;
        sp.onUpdateAbilities();
        sp.setDeltaMovement(sp.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
        sp.hurtMarked = true;
        sp.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 600, 0, false, false));
    }

    /** Gungnir's shock past the rim: throws and hurts, less the further out. */
    public static void shock(ServerLevel level, double cx, double cy, double cz, double inner, double outer,
                             @Nullable Entity attacker) {
        DamageSource source = source(level, SHOCKWAVE, attacker);
        AABB box = new AABB(cx - outer, level.getMinBuildHeight(), cz - outer, cx + outer, level.getMaxBuildHeight() + 64, cz + outer);
        for (Entity entity : level.getEntities((Entity) null, box, e -> e instanceof LivingEntity)) {
            double dx = entity.getX() - cx, dz = entity.getZ() - cz;
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d <= inner || d > outer) continue;
            if (entity instanceof Player p && (p.isSpectator() || p.isCreative())) continue;
            double f = 1.0D - (d - inner) / Math.max(1.0D, outer - inner);
            entity.hurt(source, (float) (4.0D + 26.0D * f));
            Vec3 away = new Vec3(dx, 0.0D, dz).normalize().scale(1.2D + 3.0D * f);
            entity.setDeltaMovement(entity.getDeltaMovement().add(away.x, 0.5D + 0.9D * f, away.z));
            entity.hurtMarked = true;
        }
    }

    private Zones() {
    }
}
