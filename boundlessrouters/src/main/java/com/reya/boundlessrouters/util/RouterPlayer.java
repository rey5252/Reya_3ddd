package com.reya.boundlessrouters.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.mojang.authlib.GameProfile;
import com.reya.boundlessrouters.BoundlessRouters;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The router's hands: a player that isn't there, one for each level, standing in a router and looking the way a
 * module works. It acts as a player would where a fake one falls short: it stands on the ground (so it digs at a
 * player's speed), its every hit is a full one, and what it starts using (a bow, food) it finishes.
 */
@Mod.EventBusSubscriber(modid = BoundlessRouters.MODID)
public class RouterPlayer extends FakePlayer {
    private static final GameProfile PROFILE = new GameProfile(UUID.fromString("6b1d3a58-2c4e-4f17-9a3b-8e5d0c2f7a41"), "[BoundlessRouter]");
    private static final Map<ServerLevel, RouterPlayer> PLAYERS = new WeakHashMap<>();

    private RouterPlayer(ServerLevel level) {
        super(level, PROFILE);
    }

    public static RouterPlayer get(ServerLevel level) {
        return PLAYERS.computeIfAbsent(level, RouterPlayer::new);
    }

    @SubscribeEvent
    public static void onUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) PLAYERS.remove(level);
    }

    @Override
    public boolean onGround() {
        return true;
    }

    @Override
    public float getAttackStrengthScale(float adjustTicks) {
        return 1.0F;
    }

    /**
     * Stands it with its eyes at a point, looking along a direction (up or down: still turned to the router's front),
     * holding an item and nothing else, sneaking or not.
     */
    public void ready(Vec3 eyes, Direction look, Direction facing, ItemStack held, boolean sneak) {
        getInventory().clearContent();
        getInventory().selected = 0;
        setPos(eyes.x, eyes.y - getEyeHeight(), eyes.z);
        float yaw = (look.getAxis().isHorizontal() ? look : facing).toYRot();
        float pitch = look == Direction.UP ? -90.0F : look == Direction.DOWN ? 90.0F : 0.0F;
        setYRot(yaw);
        setXRot(pitch);
        setYHeadRot(yaw);
        yRotO = yaw;
        xRotO = pitch;
        setShiftKeyDown(sneak);
        setItemInHand(InteractionHand.MAIN_HAND, held);
        // it doesn't tick, so nothing it used before is still cooling down
        if (!held.isEmpty()) getCooldowns().removeCooldown(held.getItem());
    }

    /** Lets go of what it started using: a bow or a trident drawn all the way and loosed, food or a potion finished. */
    public void finishUsing() {
        if (!isUsingItem()) return;
        ItemStack using = getUseItem();
        UseAnim anim = using.getUseAnimation();
        if (anim == UseAnim.EAT || anim == UseAnim.DRINK) {
            completeUsingItem();
        } else if (anim == UseAnim.BOW || anim == UseAnim.SPEAR || anim == UseAnim.CROSSBOW) {
            useItemRemaining = Math.max(0, using.getUseDuration() - 40);
            releaseUsingItem();
        } else {
            stopUsingItem();
        }
    }

    /** What it holds now, out of its hand. */
    public ItemStack takeHeld() {
        ItemStack held = getMainHandItem();
        setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        return held;
    }

    /** Whatever else ended up on it (a filled bucket, a bottle), taken off it; and it stops sneaking and closes what it opened. */
    public List<ItemStack> takeRest() {
        List<ItemStack> rest = new ArrayList<>();
        for (int i = 0; i < getInventory().getContainerSize(); i++) {
            ItemStack stack = getInventory().removeItemNoUpdate(i);
            if (!stack.isEmpty()) rest.add(stack);
        }
        setShiftKeyDown(false);
        if (containerMenu != inventoryMenu) closeContainer();
        return rest;
    }
}
