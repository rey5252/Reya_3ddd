package com.reya.boundlessrouters.module;

import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import com.reya.boundlessrouters.router.RelativeDirection;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraftforge.network.NetworkHooks;

/**
 * A module, to plug into a router. Using it opens its settings; sneaking, using it on a block binds it there (a
 * sender, puller or distributor: the inventory it works with, anywhere), using it in the air binds a player
 * module to its user, or unbinds the others.
 */
public class ModuleItem extends Item {
    private final ModuleKind kind;

    public ModuleItem(ModuleKind kind, Properties properties) {
        super(properties);
        this.kind = kind;
    }

    public ModuleKind kind() {
        return kind;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null) return InteractionResult.PASS;
        Level level = context.getLevel();
        if (player.isShiftKeyDown() && kind.binding().places()) {
            if (!level.isClientSide) {
                Target target = new Target(level.dimension(), context.getClickedPos(), context.getClickedFace());
                boolean bound = new ModuleSettings(context.getItemInHand()).toggleTarget(target);
                player.displayClientMessage(Component.translatable(bound ? "message.boundlessrouters.bound" : "message.boundlessrouters.unbound",
                        target.coords(), Component.translatable("direction.boundlessrouters." + target.face().getName()), target.dimName())
                        .withStyle(bound ? ChatFormatting.GREEN : ChatFormatting.YELLOW), true);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) openSettings(serverPlayer, context.getHand());
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ModuleSettings settings = new ModuleSettings(stack);
            if (player.isShiftKeyDown() && kind.binding() == ModuleKind.Binding.PLAYER) {
                settings.setPlayer(player.getUUID(), player.getGameProfile().getName());
                player.displayClientMessage(Component.translatable("message.boundlessrouters.player_bound", player.getGameProfile().getName())
                        .withStyle(ChatFormatting.GREEN), true);
            } else if (player.isShiftKeyDown() && kind.binding().places()) {
                settings.apply(ModuleSettings.Setting.CLEAR_TARGETS, 0);
                player.displayClientMessage(Component.translatable("message.boundlessrouters.cleared").withStyle(ChatFormatting.YELLOW), true);
            } else {
                openSettings(serverPlayer, hand);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    public static void openSettings(ServerPlayer player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(stack.getItem() instanceof ModuleItem)) return;
        NetworkHooks.openScreen(player, new SimpleMenuProvider((id, inventory, p) -> ModuleMenu.forHand(id, inventory, hand), stack.getHoverName()),
                buf -> ModuleMenu.writeHand(buf, hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".desc").withStyle(ChatFormatting.GRAY));
        ModuleSettings settings = new ModuleSettings(stack);
        if (kind.directional()) {
            RelativeDirection dir = settings.direction();
            tooltip.add(Component.translatable("tooltip.boundlessrouters.direction",
                    Component.translatable("direction.boundlessrouters." + dir.key())).withStyle(ChatFormatting.DARK_AQUA));
        }
        List<Target> targets = settings.targets();
        if (kind.binding() == ModuleKind.Binding.PLACE && !targets.isEmpty()) {
            Target t = targets.get(0);
            tooltip.add(Component.translatable("tooltip.boundlessrouters.target", t.coords(), t.dimName()).withStyle(ChatFormatting.GREEN));
        } else if (kind.binding() == ModuleKind.Binding.PLACES) {
            tooltip.add(Component.translatable("tooltip.boundlessrouters.targets", targets.size()).withStyle(ChatFormatting.GREEN));
        } else if (kind.binding() == ModuleKind.Binding.PLAYER) {
            UUID id = settings.playerId();
            tooltip.add(id == null
                    ? Component.translatable("tooltip.boundlessrouters.no_player").withStyle(ChatFormatting.RED)
                    : Component.translatable("tooltip.boundlessrouters.player", settings.playerName()).withStyle(ChatFormatting.GREEN));
        }
        long listed = settings.filterItems().stream().filter(s -> !s.isEmpty()).count();
        if (listed > 0) {
            tooltip.add(Component.translatable(settings.blacklist() ? "tooltip.boundlessrouters.blacklist" : "tooltip.boundlessrouters.whitelist", listed)
                    .withStyle(ChatFormatting.GOLD));
        }
        if (kind.binding().places() || kind.binding() == ModuleKind.Binding.PLAYER) {
            tooltip.add(Component.translatable("tooltip.boundlessrouters.bind_hint." + kind.binding().name().toLowerCase(java.util.Locale.ROOT))
                    .withStyle(ChatFormatting.DARK_GRAY));
        }
        tooltip.add(Component.translatable("tooltip.boundlessrouters.settings_hint").withStyle(ChatFormatting.DARK_GRAY));
    }
}
