package com.reya.bossdamage;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.reya.bossdamage.network.BossInfoPacket;
import com.reya.bossdamage.network.DamageNumberPacket;
import com.reya.bossdamage.network.ModNetwork;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.CriticalHitEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Server side: remembers how much damage each player dealt to each boss and every half second
 * sends nearby players the boss's health and the damage leaderboard.
 */
public class BossDamageTracker {
    /** Entities that get a panel: forge:bosses (dragon, wither, modded bosses), elder guardian, warden. */
    public static final TagKey<EntityType<?>> TRACKED =
            TagKey.create(Registries.ENTITY_TYPE, new ResourceLocation(BossDamage.MOD_ID, "tracked"));

    private static final double RANGE = 96.0D;
    private static final int UPDATE_TICKS = 10;
    private static final int MAX_ROWS = 5;

    private final Set<LivingEntity> active = Collections.newSetFromMap(new IdentityHashMap<>());
    /** boss UUID -> player UUID -> damage dealt */
    private final Map<UUID, Map<UUID, Float>> damage = new HashMap<>();
    private final Map<UUID, String> playerNames = new HashMap<>();

    private static boolean isTracked(Entity entity) {
        return entity instanceof LivingEntity && entity.getType().is(TRACKED);
    }

    @SubscribeEvent
    public void onJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide && isTracked(event.getEntity())) {
            active.add((LivingEntity) event.getEntity());
        }
    }

    @SubscribeEvent
    public void onLeave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel().isClientSide || !isTracked(entity)) return;
        LivingEntity boss = (LivingEntity) entity;
        active.remove(boss);

        // The ender dragon never "dies" normally, it is removed after its death animation,
        // so a KILLED removal is the one place that works for every boss.
        if (entity.getRemovalReason() == Entity.RemovalReason.KILLED) {
            sendUpdate(boss, 0.0F);
            announce(boss);
        }
        damage.remove(entity.getUUID());
    }

    /** Players whose current attack is a critical hit, and on which entity. */
    private final Map<UUID, Integer> critTargets = new HashMap<>();

    @SubscribeEvent
    public void onCriticalHit(CriticalHitEvent event) {
        boolean crit = event.getResult() == Event.Result.ALLOW
                || (event.getResult() == Event.Result.DEFAULT && event.isVanillaCritical());
        if (crit) {
            critTargets.put(event.getEntity().getUUID(), event.getTarget().getId());
        } else {
            critTargets.remove(event.getEntity().getUUID());
        }
    }

    @SubscribeEvent
    public void onDamage(LivingDamageEvent event) {
        LivingEntity boss = event.getEntity();
        if (boss.level().isClientSide) return;
        sendDamageNumber(event);
        if (!isTracked(boss)) return;
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) return;

        float dealt = Math.min(event.getAmount(), boss.getHealth());
        if (dealt <= 0.0F) return;
        damage.computeIfAbsent(boss.getUUID(), k -> new HashMap<>()).merge(player.getUUID(), dealt, Float::sum);
        playerNames.put(player.getUUID(), player.getGameProfile().getName());
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % UPDATE_TICKS != 0) return;
        for (LivingEntity boss : List.copyOf(active)) {
            if (boss.isRemoved()) {
                active.remove(boss);
                continue;
            }
            // Vanilla-style bosses always show; others (elder guardian, warden) only once someone hits them.
            if (boss.getType().is(Tags.EntityTypes.BOSSES) || damage.containsKey(boss.getUUID())) {
                sendUpdate(boss, boss.getHealth());
            }
        }
    }

    /** Floating damage number for everyone who can see the entity. */
    private void sendDamageNumber(LivingDamageEvent event) {
        LivingEntity target = event.getEntity();
        float amount = event.getAmount();
        if (amount <= 0.0F) return;

        boolean crit = false;
        if (event.getSource().getDirectEntity() instanceof ServerPlayer player) {
            Integer critTarget = critTargets.remove(player.getUUID());
            crit = critTarget != null && critTarget == target.getId();
        }
        ModNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new DamageNumberPacket(target.getX(), target.getY() + target.getBbHeight() + 0.25D, target.getZ(), amount, crit));
    }

    private List<BossInfoPacket.Row> leaderboard(LivingEntity boss, int limit) {
        Map<UUID, Float> dealt = damage.getOrDefault(boss.getUUID(), Map.of());
        float max = Math.max(1.0F, boss.getMaxHealth());
        List<BossInfoPacket.Row> rows = new ArrayList<>();
        dealt.entrySet().stream()
                .sorted(Map.Entry.<UUID, Float>comparingByValue().reversed())
                .limit(limit)
                .forEach(e -> rows.add(new BossInfoPacket.Row(
                        playerNames.getOrDefault(e.getKey(), "?"), e.getValue() / max * 100.0F)));
        return rows;
    }

    private List<ServerPlayer> nearbyPlayers(LivingEntity boss) {
        if (!(boss.level() instanceof ServerLevel level)) return List.of();
        return level.getPlayers(p -> p.distanceToSqr(boss) < RANGE * RANGE);
    }

    private void sendUpdate(LivingEntity boss, float health) {
        List<ServerPlayer> players = nearbyPlayers(boss);
        if (players.isEmpty()) return;
        ResourceLocation type = ForgeRegistries.ENTITY_TYPES.getKey(boss.getType());
        BossInfoPacket packet = new BossInfoPacket(boss.getId(),
                type == null ? new ResourceLocation("minecraft", "pig") : type,
                boss.getDisplayName(), health, boss.getMaxHealth(), leaderboard(boss, MAX_ROWS));
        for (ServerPlayer player : players) {
            ModNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet);
        }
    }

    /** Chat summary when a boss is killed. */
    private void announce(LivingEntity boss) {
        List<BossInfoPacket.Row> rows = leaderboard(boss, 3);
        if (rows.isEmpty()) return;
        List<ServerPlayer> players = nearbyPlayers(boss);
        Component title = Component.translatable("bossdamage.defeated", boss.getDisplayName()).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer player : players) {
            player.sendSystemMessage(title);
            for (int i = 0; i < rows.size(); i++) {
                BossInfoPacket.Row row = rows.get(i);
                player.sendSystemMessage(Component.literal(String.format(" %d. %s — %.2f%%", i + 1, row.name(), row.percent()))
                        .withStyle(i == 0 ? ChatFormatting.YELLOW : ChatFormatting.GRAY));
            }
        }
    }
}
