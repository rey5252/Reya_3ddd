package com.reya.domgun;

import java.util.List;

import javax.annotation.Nullable;

import com.reya.domgun.network.Net;
import com.reya.domgun.network.ShotPacket;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.PacketDistributor;
import org.joml.Vector3f;

/**
 * The DOM assault rifle. Hold right-click to fire (full auto), sneak to aim down sights, R to reload.
 * Bullets are hitscan: there is no projectile entity, the shot is a ray trace on the server.
 */
public class DomGunItem extends Item {
    /** Result codes carried by {@link ShotPacket} for the hit marker. */
    public static final int MISS = 0, HIT = 1, HEADSHOT = 2, KILL = 3;

    private static final String AMMO = "Ammo";
    private static final String RELOAD_END = "ReloadEnd";
    private static final String NEXT_SHOT = "NextShot";
    private static final DustParticleOptions TRACER = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.35F), 0.6F);
    private static final DustParticleOptions BLOOD = new DustParticleOptions(new Vector3f(0.7F, 0.05F, 0.05F), 0.8F);

    public DomGunItem(Properties properties) {
        super(properties);
    }

    // ---- state stored in the stack's NBT ----

    public static int getAmmo(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getInt(AMMO) : 0;
    }

    public static void setAmmo(ItemStack stack, int ammo) {
        stack.getOrCreateTag().putInt(AMMO, Math.max(0, ammo));
    }

    public static long reloadEnd(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getLong(RELOAD_END) : 0L;
    }

    public static boolean isReloading(ItemStack stack, Level level) {
        long end = reloadEnd(stack);
        return end > 0 && level.getGameTime() < end;
    }

    private static void clearReload(ItemStack stack) {
        if (stack.hasTag()) stack.getTag().remove(RELOAD_END);
    }

    private static long nextShot(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().getLong(NEXT_SHOT) : 0L;
    }

    // ---- ammo in the inventory ----

    public static int countAmmo(Player player) {
        int total = 0;
        Inventory inv = player.getInventory();
        for (ItemStack s : inv.items) if (s.is(DomGun.DOM_AMMO.get())) total += s.getCount();
        for (ItemStack s : inv.offhand) if (s.is(DomGun.DOM_AMMO.get())) total += s.getCount();
        return total;
    }

    private static int takeAmmo(Player player, int wanted) {
        int taken = 0;
        Inventory inv = player.getInventory();
        for (List<ItemStack> list : List.of(inv.items, inv.offhand)) {
            for (ItemStack s : list) {
                if (taken >= wanted) return taken;
                if (!s.is(DomGun.DOM_AMMO.get())) continue;
                int n = Math.min(s.getCount(), wanted - taken);
                s.shrink(n);
                taken += n;
            }
        }
        return taken;
    }

    // ---- using the gun ----

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (hand != InteractionHand.MAIN_HAND || isReloading(stack, level)) return InteractionResultHolder.fail(stack);
        if (getAmmo(stack) <= 0) {
            if (!level.isClientSide && player instanceof ServerPlayer sp && !startReload(sp, stack)) emptyClick(sp);
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remainingUseDuration) {
        if (level.isClientSide || !(living instanceof ServerPlayer player)) return;
        if (isReloading(stack, level)) {
            player.stopUsingItem();
            return;
        }
        long now = level.getGameTime();
        if (now < nextShot(stack)) return;
        if (getAmmo(stack) <= 0) {
            player.stopUsingItem();
            if (!startReload(player, stack)) emptyClick(player);
            return;
        }
        stack.getOrCreateTag().putLong(NEXT_SHOT, now + Config.FIRE_DELAY.get());
        setAmmo(stack, getAmmo(stack) - 1);
        fire((ServerLevel) level, player);
    }

    /** Finishes a running reload; drops it when the gun leaves the hand. */
    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slot, boolean selected) {
        if (level.isClientSide || !(entity instanceof ServerPlayer player)) return;
        long end = reloadEnd(stack);
        if (end <= 0) return;
        if (!selected) {
            clearReload(stack);
            return;
        }
        if (level.getGameTime() < end) return;
        clearReload(stack);
        int need = Config.MAGAZINE_SIZE.get() - getAmmo(stack);
        if (need <= 0) return;
        int got = player.getAbilities().instabuild ? need : takeAmmo(player, need);
        if (got <= 0) return;
        setAmmo(stack, getAmmo(stack) + got);
        level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 0.9F, 1.1F);
    }

    /** Starts a reload. Returns false when there is nothing to do or nothing to load. */
    public static boolean startReload(ServerPlayer player, ItemStack stack) {
        Level level = player.level();
        if (isReloading(stack, level) || getAmmo(stack) >= Config.MAGAZINE_SIZE.get()) return false;
        if (!player.getAbilities().instabuild && countAmmo(player) <= 0) return false;
        stack.getOrCreateTag().putLong(RELOAD_END, level.getGameTime() + Config.RELOAD_TICKS.get());
        player.stopUsingItem();
        level.playSound(null, player.blockPosition(), SoundEvents.CROSSBOW_LOADING_START, SoundSource.PLAYERS, 0.9F, 1.2F);
        return true;
    }

    public static void emptyClick(ServerPlayer player) {
        player.level().playSound(null, player.blockPosition(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.6F);
        if (countAmmo(player) <= 0) player.displayClientMessage(Component.translatable("domgun.message.no_ammo"), true);
    }

    // ---- the shot ----

    private static void fire(ServerLevel level, ServerPlayer player) {
        boolean ads = player.isShiftKeyDown();
        double spread = ads ? Config.ADS_SPREAD.get() : Config.HIP_SPREAD.get();
        if (!ads) {
            if (player.isSprinting()) spread *= 2.0;
            if (!player.onGround()) spread *= 2.5;
        }
        var rng = player.getRandom();
        float yaw = player.getYRot() + (float) Mth.clamp(rng.nextGaussian() * spread * 0.5, -spread, spread);
        float pitch = player.getXRot() + (float) Mth.clamp(rng.nextGaussian() * spread * 0.5, -spread, spread);
        Vec3 dir = Vec3.directionFromRotation(pitch, yaw);

        Vec3 start = player.getEyePosition();
        double range = Config.RANGE.get();
        Vec3 end = start.add(dir.scale(range));
        BlockHitResult blockHit = level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = blockHit.getType() == HitResult.Type.MISS ? end : blockHit.getLocation();
        EntityHitResult entityHit = ProjectileUtil.getEntityHitResult(level, player, start, stop,
                new AABB(start, stop).inflate(1.0), e -> !e.isSpectator() && e.isPickable() && e.isAlive());

        int result = MISS;
        Vec3 impact = stop;
        if (entityHit != null) {
            Entity target = entityHit.getEntity();
            impact = entityHit.getLocation();
            double dist = start.distanceTo(impact);
            double falloffStart = Config.FALLOFF_START.get();
            double falloff = dist <= falloffStart ? 1.0
                    : Mth.clamp(1.0 - 0.5 * (dist - falloffStart) / Math.max(1.0, range - falloffStart), 0.5, 1.0);
            float damage = (float) (Config.DAMAGE.get() * falloff);
            boolean head = target instanceof LivingEntity living && impact.y >= living.getEyeY() - 0.2;
            if (head) damage *= Config.HEADSHOT_MULTIPLIER.get().floatValue();
            target.invulnerableTime = 0;
            if (target.hurt(level.damageSources().playerAttack(player), damage)) {
                result = !target.isAlive() ? KILL : head ? HEADSHOT : HIT;
            }
            level.sendParticles(BLOOD, impact.x, impact.y, impact.z, 5, 0.1, 0.1, 0.1, 0.0);
        } else if (blockHit.getType() != HitResult.Type.MISS) {
            BlockPos pos = blockHit.getBlockPos();
            BlockState state = level.getBlockState(pos);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, state), impact.x, impact.y, impact.z, 8, 0.08, 0.08, 0.08, 0.05);
            level.playSound(null, pos, state.getSoundType().getHitSound(), SoundSource.BLOCKS, 0.6F, 1.4F);
        }

        // muzzle smoke and a tracer line
        Vec3 muzzle = start.add(dir.scale(1.2)).add(0.0, -0.12, 0.0);
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 2, 0.02, 0.02, 0.02, 0.01);
        double total = start.distanceTo(impact);
        for (double d = 2.5; d < total; d += 2.0) {
            Vec3 p = start.add(dir.scale(d));
            level.sendParticles(TRACER, p.x, p.y - 0.1, p.z, 1, 0.0, 0.0, 0.0, 0.0);
        }

        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_BLAST,
                SoundSource.PLAYERS, 2.0F, 1.7F + rng.nextFloat() * 0.1F);
        level.playSound(null, player.getX(), player.getEyeY(), player.getZ(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.PLAYERS, 0.3F, 1.9F);

        if (result == HIT) player.playNotifySound(SoundEvents.ARROW_HIT_PLAYER, SoundSource.PLAYERS, 0.6F, 1.5F);
        else if (result == HEADSHOT) player.playNotifySound(SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 1.2F);
        else if (result == KILL) player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8F, 0.8F);

        float kick = (float) (Config.RECOIL.get() * (ads ? 0.6 : 1.0)) * (0.85F + rng.nextFloat() * 0.3F);
        float sway = (rng.nextFloat() - 0.5F) * kick * 0.7F;
        Net.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new ShotPacket(kick, sway, result));
    }

    // ---- item behaviour ----

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.NONE;
    }

    @Override
    public int getUseDuration(ItemStack stack) {
        return 72000;
    }

    /** Ammo and cooldown change the NBT every shot; that must not replay the equip animation. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean shouldCauseBlockBreakReset(ItemStack oldStack, ItemStack newStack) {
        return oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.min(13, Math.round(13.0F * getAmmo(stack) / Config.MAGAZINE_SIZE.get()));
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xF0A020;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.domgun.dom_gun.ammo", getAmmo(stack), Config.MAGAZINE_SIZE.get())
                .withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("item.domgun.dom_gun.tip_fire").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.domgun.dom_gun.tip_ads").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.domgun.dom_gun.tip_reload").withStyle(ChatFormatting.GRAY));
    }
}
