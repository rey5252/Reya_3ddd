package com.reya.bossdamage.client;

import javax.annotation.Nullable;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;

/** Finds the mob under the crosshair, however far away (up to {@link #RANGE} blocks), without hitting it. */
public final class HoveredEntity {
    public static final double RANGE = 128.0D;
    /** Keep showing the last mob briefly after looking away, so the panel doesn't flicker. */
    private static final long LINGER_MS = 400L;

    @Nullable
    private static LivingEntity last;
    private static long lastSeen;

    @Nullable
    public static LivingEntity find(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        Entity camera = mc.getCameraEntity();
        if (camera == null || mc.level == null) return null;

        LivingEntity hit = raycast(camera, partialTick);
        long now = Util.getMillis();
        if (hit != null) {
            last = hit;
            lastSeen = now;
            return hit;
        }
        if (last != null && (last.isRemoved() || now - lastSeen > LINGER_MS)) {
            last = null;
        }
        return last;
    }

    @Nullable
    private static LivingEntity raycast(Entity camera, float partialTick) {
        Vec3 eye = camera.getEyePosition(partialTick);
        Vec3 look = camera.getViewVector(partialTick);
        Vec3 end = eye.add(look.scale(RANGE));

        // Walls block the view.
        HitResult block = camera.pick(RANGE, partialTick, false);
        double maxDistSqr = block.getType() == HitResult.Type.MISS ? RANGE * RANGE : block.getLocation().distanceToSqr(eye);

        AABB area = camera.getBoundingBox().expandTowards(look.scale(RANGE)).inflate(1.0D);
        EntityHitResult result = ProjectileUtil.getEntityHitResult(camera, eye, end, area,
                e -> !e.isSpectator() && e.isPickable() && !e.isInvisible() && toLiving(e) != null, maxDistSqr);
        return result == null ? null : toLiving(result.getEntity());
    }

    /** Living mobs, and the parent of multipart mobs like the ender dragon. */
    @Nullable
    private static LivingEntity toLiving(Entity entity) {
        if (entity instanceof PartEntity<?> part) entity = part.getParent();
        if (entity instanceof ArmorStand || !(entity instanceof LivingEntity living) || !living.isAlive()) return null;
        return living;
    }

    private HoveredEntity() {
    }
}
