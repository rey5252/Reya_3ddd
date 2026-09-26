package com.reya.attributeeditor.table;

import java.util.List;
import java.util.function.Supplier;

import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.common.ForgeMod;

/**
 * One editable line in the Attribute Table. {@code sliderMin}/{@code sliderMax} only set the
 * slider's range; -/+ and typed values can go past them in either direction.
 */
public record TableRow(Supplier<Attribute> attribute, double sliderMin, double sliderMax, double step) {
    /** Values beyond this are rejected as nonsense (and would overflow the level cost). */
    public static final double MAX_ABS_VALUE = 1.0E9D;

    public static final List<TableRow> ROWS = List.of(
            new TableRow(() -> Attributes.ATTACK_DAMAGE, 0.0D, 50.0D, 1.0D),
            new TableRow(() -> Attributes.ATTACK_SPEED, 0.0D, 10.0D, 0.1D),
            new TableRow(() -> Attributes.ATTACK_KNOCKBACK, 0.0D, 10.0D, 0.5D),
            new TableRow(() -> Attributes.ARMOR, 0.0D, 30.0D, 1.0D),
            new TableRow(() -> Attributes.ARMOR_TOUGHNESS, 0.0D, 20.0D, 1.0D),
            new TableRow(() -> Attributes.KNOCKBACK_RESISTANCE, 0.0D, 1.0D, 0.1D),
            new TableRow(() -> Attributes.MAX_HEALTH, 0.0D, 100.0D, 2.0D),
            new TableRow(() -> Attributes.MOVEMENT_SPEED, 0.0D, 0.5D, 0.01D),
            new TableRow(() -> Attributes.LUCK, 0.0D, 20.0D, 1.0D),
            new TableRow(ForgeMod.ENTITY_REACH, 0.0D, 10.0D, 0.5D),
            new TableRow(ForgeMod.BLOCK_REACH, 0.0D, 10.0D, 0.5D));

    public static boolean isValid(double value) {
        return Double.isFinite(value) && Math.abs(value) <= MAX_ABS_VALUE;
    }

    /** Levels needed to go from {@code from} to {@code to}: one per started step upwards, lowering is free. */
    public long cost(double from, double to) {
        double steps = (to - from) / step;
        return steps <= 1.0E-6D ? 0L : (long) Math.ceil(steps - 1.0E-6D) * AttributeTableMenu.LEVEL_COST;
    }

    /** Position of {@code value} on the slider, 0..1. */
    public double sliderFraction(double value) {
        return Math.max(0.0D, Math.min(1.0D, (value - sliderMin) / (sliderMax - sliderMin)));
    }

    /** Slider position 0..1 to a value snapped to the step grid. */
    public double sliderValue(double fraction) {
        return round(sliderMin + Math.round(fraction * (sliderMax - sliderMin) / step) * step);
    }

    public static double round(double value) {
        return Math.round(value * 100.0D) / 100.0D;
    }
}
