package com.reya.starfall;

import java.util.List;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** One red button under a flip cover. Right click fires the selected weapon; sneak + right click steps to the next. */
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
                player.level().playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(),
                        SoundSource.PLAYERS, 0.4F, 1.6F);
                return true;
            }
        }
        return false;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide || !(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(stack);
        }
        if (player.isShiftKeyDown()) {
            select(serverPlayer, skill(stack).next());
            return InteractionResultHolder.success(stack);
        }
        Skill skill = skill(stack);
        if (StarfallManager.cast(serverPlayer, skill)) {
            // the flip cover snaps open and the button goes down
            level.playSound(null, player.blockPosition(), SoundEvents.IRON_TRAPDOOR_OPEN, SoundSource.PLAYERS, 0.8F, 1.7F);
            level.playSound(null, player.blockPosition(), SoundEvents.STONE_BUTTON_CLICK_ON, SoundSource.PLAYERS, 1.0F, 0.6F);
            int cooldown = Config.COOLDOWN.get() * 20;
            if (cooldown > 0) player.getCooldowns().addCooldown(this, cooldown);
        }
        return InteractionResultHolder.consume(stack);
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
