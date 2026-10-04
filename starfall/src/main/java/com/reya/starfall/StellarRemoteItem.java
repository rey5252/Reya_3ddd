package com.reya.starfall;

import java.util.List;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reya.starfall.client.RemoteAnimation;
import com.reya.starfall.client.RemotePose;
import com.reya.starfall.client.RemoteRenderer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.fml.DistExecutor;

/**
 * One button under a hazard-striped flip cover. Right click flips the cover and presses the button, which fires
 * the selected weapon; sneak + right click steps to the next.
 */
public class StellarRemoteItem extends Item {
    private static final String TAG_SKILL = "Skill";

    public StellarRemoteItem(Properties properties) {
        super(properties);
    }

    public static Skill skill(ItemStack stack) {
        return stack.hasTag() ? Skill.byIndex(stack.getTag().getInt(TAG_SKILL)) : Skill.RAILGUN;
    }

    public static void setSkill(ItemStack stack, Skill skill) {
        stack.getOrCreateTag().putInt(TAG_SKILL, skill.ordinal());
    }

    /** Selects a weapon on the remote the player is holding (main hand first) and says so. */
    public static boolean select(ServerPlayer player, Skill skill) {
        for (InteractionHand hand : InteractionHand.values()) {
            ItemStack stack = player.getItemInHand(hand);
            if (stack.getItem() instanceof StellarRemoteItem) {
                setSkill(stack, skill);
                player.displayClientMessage(Component.translatable("message.starfall.selected", skill.tag(), skill.title())
                        .withStyle(s -> s.withColor(skill.color)), true);
                player.level().playSound(null, player.blockPosition(), Sounds.UI_SELECT.get(), SoundSource.PLAYERS, 0.5F, 1.0F);
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer serverPlayer) select(serverPlayer, skill(stack).next());
            return InteractionResultHolder.consume(stack);
        }
        // the cooldown also covers the press itself, so the button can't be pressed twice mid-animation
        int cooldown = Math.max(20, Config.COOLDOWN.get() * 20);
        if (level.isClientSide) {
            // the hand comes up and the thumb flips the cover: the server fires when the button is down
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> RemoteAnimation::startLocal);
            player.getCooldowns().addCooldown(this, cooldown);
            return InteractionResultHolder.consume(stack);
        }
        if (player instanceof ServerPlayer serverPlayer) {
            player.getCooldowns().addCooldown(this, StarfallManager.press(serverPlayer, skill(stack)) ? cooldown : 20);
        }
        return InteractionResultHolder.consume(stack);
    }

    /** Picking another skill changes the item's tag; that shouldn't drop the remote out of the hand and back. */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return RemoteRenderer.get();
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack,
                                                   float partialTick, float equipProcess, float swingProcess) {
                return RemoteRenderer.handTransform(pose, player, arm, partialTick, equipProcess);
            }

            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return RemotePose.of(entity);
            }
        });
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        Skill skill = skill(stack);
        tooltip.add(Component.translatable("tooltip.starfall.selected", skill.tag(), skill.title())
                .withStyle(s -> s.withColor(skill.color)));
        tooltip.add(Component.translatable("tooltip.starfall.skill." + skill.id).withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.starfall.use").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.starfall.keys").withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.starfall.warning").withStyle(ChatFormatting.RED));
    }
}
