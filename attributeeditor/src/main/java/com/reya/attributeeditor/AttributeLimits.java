package com.reya.attributeeditor;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import com.mojang.logging.LogUtils;
import com.reya.attributeeditor.table.TableRow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Vanilla caps attributes (armor 30, toughness 20, health 1024, damage 2048...). This raises the
 * upper cap of every Minecraft and Forge attribute so edited items work with any value.
 * Lower caps are kept, so e.g. max health can never drop to zero or below.
 */
public final class AttributeLimits {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static void lift() {
        Field maxField = findMaxField();
        if (maxField == null) {
            LOGGER.error("Could not find RangedAttribute's max value field; vanilla attribute caps stay in place");
            return;
        }

        int lifted = 0;
        for (Attribute attribute : ForgeRegistries.ATTRIBUTES.getValues()) {
            ResourceLocation id = ForgeRegistries.ATTRIBUTES.getKey(attribute);
            if (id == null || !(id.getNamespace().equals("minecraft") || id.getNamespace().equals("forge"))) continue;
            if (!(attribute instanceof RangedAttribute ranged) || ranged.getMaxValue() >= TableRow.MAX_ABS_VALUE) continue;
            try {
                maxField.setDouble(ranged, TableRow.MAX_ABS_VALUE);
                lifted++;
            } catch (IllegalAccessException e) {
                LOGGER.error("Could not raise the cap of {}", id, e);
            }
        }
        LOGGER.info("Raised the upper cap of {} attributes to {}", lifted, TableRow.MAX_ABS_VALUE);
    }

    /**
     * RangedAttribute has two double fields, minValue and maxValue. Their names differ between
     * development and production, so find maxValue by its value instead of its name.
     */
    private static Field findMaxField() {
        RangedAttribute probe = new RangedAttribute("attributeeditor.probe", 1.0D, -7.0D, 7.0D);
        for (Field field : RangedAttribute.class.getDeclaredFields()) {
            if (field.getType() != double.class || Modifier.isStatic(field.getModifiers())) continue;
            try {
                field.setAccessible(true);
                if (field.getDouble(probe) == 7.0D) return field;
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.warn("Could not read {}", field, e);
            }
        }
        return null;
    }

    private AttributeLimits() {
    }
}
