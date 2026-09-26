package com.reya.attributeeditor.table;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;

/** One editable line in the Attribute Table: which attribute, how much each click changes it, and its minimum. */
public record TableRow(Supplier<Attribute> attribute, double step, double min) {
    public static final List<TableRow> ROWS = List.of(
            new TableRow(() -> Attributes.ATTACK_DAMAGE, 1.0D, 0.0D),
            new TableRow(() -> Attributes.ATTACK_SPEED, 0.1D, 0.1D),
            new TableRow(() -> Attributes.ATTACK_KNOCKBACK, 0.5D, 0.0D),
            new TableRow(() -> Attributes.ARMOR, 1.0D, 0.0D),
            new TableRow(() -> Attributes.ARMOR_TOUGHNESS, 1.0D, 0.0D),
            new TableRow(() -> Attributes.KNOCKBACK_RESISTANCE, 0.1D, 0.0D),
            new TableRow(() -> Attributes.MAX_HEALTH, 2.0D, 0.0D),
            new TableRow(() -> Attributes.MOVEMENT_SPEED, 0.01D, 0.0D),
            new TableRow(() -> Attributes.LUCK, 1.0D, 0.0D),
            new TableRow(ForgeMod.ENTITY_REACH, 0.5D, 0.0D),
            new TableRow(ForgeMod.BLOCK_REACH, 0.5D, 0.0D));
}
