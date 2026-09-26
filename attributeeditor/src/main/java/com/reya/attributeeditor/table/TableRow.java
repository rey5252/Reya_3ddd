package com.reya.attributeeditor.table;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;

/** One editable line in the Attribute Table: which attribute, its slider range and step. */
public record TableRow(Supplier<Attribute> attribute, double min, double max, double step) {
    public static final List<TableRow> ROWS = List.of(
            new TableRow(() -> Attributes.ATTACK_DAMAGE, 0.0D, 50.0D, 1.0D),
            new TableRow(() -> Attributes.ATTACK_SPEED, 0.1D, 10.0D, 0.1D),
            new TableRow(() -> Attributes.ATTACK_KNOCKBACK, 0.0D, 10.0D, 0.5D),
            new TableRow(() -> Attributes.ARMOR, 0.0D, 30.0D, 1.0D),
            new TableRow(() -> Attributes.ARMOR_TOUGHNESS, 0.0D, 20.0D, 1.0D),
            new TableRow(() -> Attributes.KNOCKBACK_RESISTANCE, 0.0D, 1.0D, 0.1D),
            new TableRow(() -> Attributes.MAX_HEALTH, 0.0D, 100.0D, 2.0D),
            new TableRow(() -> Attributes.MOVEMENT_SPEED, 0.0D, 0.5D, 0.01D),
            new TableRow(() -> Attributes.LUCK, 0.0D, 20.0D, 1.0D),
            new TableRow(ForgeMod.ENTITY_REACH, 0.0D, 10.0D, 0.5D),
            new TableRow(ForgeMod.BLOCK_REACH, 0.0D, 10.0D, 0.5D));

    /** Number of slider positions above the minimum. */
    public int maxSteps() {
        return (int) Math.round((max - min) / step);
    }

    public double valueAt(int steps) {
        return round(min + Math.max(0, Math.min(maxSteps(), steps)) * step);
    }

    public int stepsFor(double value) {
        return Math.max(0, Math.min(maxSteps(), (int) Math.round((value - min) / step)));
    }

    /** Levels needed to go from {@code from} to {@code to}: one per step upwards, lowering is free. */
    public int cost(double from, double to) {
        return Math.max(0, (int) Math.round((to - from) / step)) * AttributeTableMenu.LEVEL_COST;
    }

    public static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
