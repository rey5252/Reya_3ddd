package com.reya.cursedseed;

import java.util.UUID;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Makes monsters born on Cursed Earth tougher. */
public final class Empowerment {
    private static final String TAG = CursedSeed.MOD_ID + ":empowered";

    private static final UUID HEALTH_ID = UUID.fromString("5b0c1f2e-6a3d-4a8e-9a57-1c9e2f4b7d01");
    private static final UUID DAMAGE_ID = UUID.fromString("5b0c1f2e-6a3d-4a8e-9a57-1c9e2f4b7d02");
    private static final UUID SPEED_ID = UUID.fromString("5b0c1f2e-6a3d-4a8e-9a57-1c9e2f4b7d03");
    private static final UUID ARMOR_ID = UUID.fromString("5b0c1f2e-6a3d-4a8e-9a57-1c9e2f4b7d04");

    public static void empower(Mob mob) {
        CompoundTag data = mob.getPersistentData();
        if (data.getBoolean(TAG)) return;
        data.putBoolean(TAG, true);

        addModifier(mob, Attributes.MAX_HEALTH, HEALTH_ID, "Cursed health",
                Config.HEALTH_BONUS.get(), AttributeModifier.Operation.MULTIPLY_TOTAL);
        addModifier(mob, Attributes.ATTACK_DAMAGE, DAMAGE_ID, "Cursed damage",
                Config.DAMAGE_BONUS.get(), AttributeModifier.Operation.MULTIPLY_TOTAL);
        addModifier(mob, Attributes.MOVEMENT_SPEED, SPEED_ID, "Cursed speed",
                Config.SPEED_BONUS.get(), AttributeModifier.Operation.MULTIPLY_TOTAL);
        addModifier(mob, Attributes.ARMOR, ARMOR_ID, "Cursed armor",
                Config.ARMOR_BONUS.get(), AttributeModifier.Operation.ADDITION);
        mob.setHealth(mob.getMaxHealth());

        // Lets undead survive their first ten minutes of daylight.
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60 * 10, 0, false, false));
    }

    private static void addModifier(Mob mob, Attribute attribute, UUID id, String name,
                                    double amount, AttributeModifier.Operation operation) {
        if (amount <= 0) return;
        AttributeInstance instance = mob.getAttribute(attribute);
        if (instance != null && instance.getModifier(id) == null) {
            instance.addPermanentModifier(new AttributeModifier(id, name, amount, operation));
        }
    }

    private Empowerment() {
    }
}
