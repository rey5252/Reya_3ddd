package com.reya.chaosspawner.client;

import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;

/** One display-only entity per mob type, reused for the cage renderer and the GUI preview. */
public final class ClientEntities {
    private static final Map<EntityType<?>, LivingEntity> CACHE = new HashMap<>();

    @Nullable
    public static LivingEntity get(@Nullable EntityType<?> type) {
        Minecraft mc = Minecraft.getInstance();
        if (type == null || mc.level == null) return null;
        LivingEntity cached = CACHE.get(type);
        if (cached != null && cached.level() == mc.level) return cached;

        Entity created;
        try {
            created = type.create(mc.level);
        } catch (RuntimeException e) {
            return null;
        }
        if (!(created instanceof LivingEntity living)) return null;
        CACHE.put(type, living);
        return living;
    }

    private ClientEntities() {
    }
}
