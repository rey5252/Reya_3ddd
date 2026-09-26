package com.reya.attributeeditor;

import java.util.Arrays;
import java.util.Map;

import javax.annotation.Nullable;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/**
 * /itemattr set &lt;attribute&gt; &lt;value&gt; [slot] – set a value on the held item
 * /itemattr remove &lt;attribute&gt; [slot]      – remove an attribute from the held item
 * /itemattr list                               – show the held item's attributes
 * /itemattr reset                              – restore the held item's default attributes
 * /itemattr unbreakable                        – toggle unbreakable on the held item
 * /itemattr reload                             – reload config/attributeeditor.json
 */
public final class ItemAttrCommand {
    private static final SimpleCommandExceptionType NO_ITEM =
            new SimpleCommandExceptionType(Component.translatable("commands.attributeeditor.no_item"));
    private static final DynamicCommandExceptionType BAD_SLOT =
            new DynamicCommandExceptionType(slot -> Component.translatable("commands.attributeeditor.bad_slot", slot));

    private static final SuggestionProvider<CommandSourceStack> SLOTS = (context, builder) ->
            SharedSuggestionProvider.suggest(Arrays.stream(EquipmentSlot.values()).map(EquipmentSlot::getName), builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext buildContext) {
        dispatcher.register(Commands.literal("itemattr")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("set")
                        .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg())
                                        .executes(c -> set(c, null))
                                        .then(Commands.argument("slot", StringArgumentType.word()).suggests(SLOTS)
                                                .executes(c -> set(c, slotArg(c)))))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("attribute", ResourceArgument.resource(buildContext, Registries.ATTRIBUTE))
                                .executes(c -> remove(c, null))
                                .then(Commands.argument("slot", StringArgumentType.word()).suggests(SLOTS)
                                        .executes(c -> remove(c, slotArg(c))))))
                .then(Commands.literal("list").executes(ItemAttrCommand::list))
                .then(Commands.literal("reset").executes(ItemAttrCommand::reset))
                .then(Commands.literal("unbreakable").executes(ItemAttrCommand::unbreakable))
                .then(Commands.literal("reload").executes(ItemAttrCommand::reload)));
    }

    private static EquipmentSlot slotArg(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        String name = StringArgumentType.getString(c, "slot");
        EquipmentSlot slot = AttributeValues.parseSlot(name);
        if (slot == null) throw BAD_SLOT.create(name);
        return slot;
    }

    private static ItemStack heldItem(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = c.getSource().getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) throw NO_ITEM.create();
        return stack;
    }

    private static int set(CommandContext<CommandSourceStack> c, @Nullable EquipmentSlot slotArg) throws CommandSyntaxException {
        ItemStack stack = heldItem(c);
        Attribute attribute = ResourceArgument.getAttribute(c, "attribute").value();
        double value = DoubleArgumentType.getDouble(c, "value");
        EquipmentSlot slot = slotArg != null ? slotArg : AttributeValues.defaultSlot(stack);

        ItemAttributeEditing.set(stack, attribute, slot, value);

        c.getSource().sendSuccess(() -> Component.translatable("commands.attributeeditor.set",
                Component.translatable(attribute.getDescriptionId()), format(value), slot.getName()), true);
        return 1;
    }

    private static int remove(CommandContext<CommandSourceStack> c, @Nullable EquipmentSlot slot) throws CommandSyntaxException {
        ItemStack stack = heldItem(c);
        Attribute attribute = ResourceArgument.getAttribute(c, "attribute").value();

        int removed = ItemAttributeEditing.remove(stack, attribute, slot);
        c.getSource().sendSuccess(() -> Component.translatable("commands.attributeeditor.removed",
                Component.translatable(attribute.getDescriptionId()), removed), true);
        return removed;
    }

    private static int list(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ItemStack stack = heldItem(c);
        CommandSourceStack source = c.getSource();
        source.sendSuccess(() -> Component.translatable("commands.attributeeditor.list", stack.getHoverName())
                .withStyle(ChatFormatting.GOLD), false);

        int count = 0;
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            for (Map.Entry<Attribute, AttributeModifier> e : stack.getAttributeModifiers(slot).entries()) {
                MutableComponent line = Component.literal(" " + slot.getName() + ": ").withStyle(ChatFormatting.GRAY)
                        .append(Component.translatable(e.getKey().getDescriptionId()).withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(" = " + format(AttributeValues.displayed(e.getKey(), e.getValue()))
                                + opSuffix(e.getValue())).withStyle(ChatFormatting.GREEN))
                        .append(Component.literal("  (" + AttributeValues.key(e.getKey()) + ")").withStyle(ChatFormatting.DARK_GRAY));
                source.sendSuccess(() -> line, false);
                count++;
            }
        }
        if (count == 0) {
            source.sendSuccess(() -> Component.translatable("commands.attributeeditor.list.empty").withStyle(ChatFormatting.GRAY), false);
        }
        return count;
    }

    private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ItemStack stack = heldItem(c);
        ItemAttributeEditing.reset(stack);
        c.getSource().sendSuccess(() -> Component.translatable("commands.attributeeditor.reset"), true);
        return 1;
    }

    private static int unbreakable(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ItemStack stack = heldItem(c);
        boolean now = ItemAttributeEditing.toggleUnbreakable(stack);
        c.getSource().sendSuccess(() -> Component.translatable(now
                ? "commands.attributeeditor.unbreakable.on" : "commands.attributeeditor.unbreakable.off"), true);
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> c) {
        int rules = ItemAttributeConfig.load();
        if (rules < 0) {
            c.getSource().sendFailure(Component.translatable("commands.attributeeditor.reload.failed"));
            return 0;
        }
        c.getSource().sendSuccess(() -> Component.translatable("commands.attributeeditor.reload", rules), true);
        return rules;
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(Math.round(value * 1000) / 1000.0);
    }

    private static String opSuffix(AttributeModifier modifier) {
        return switch (modifier.getOperation()) {
            case ADDITION -> "";
            case MULTIPLY_BASE, MULTIPLY_TOTAL -> " (x" + format(modifier.getAmount()) + ")";
        };
    }

    private ItemAttrCommand() {
    }
}
