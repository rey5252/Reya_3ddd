package com.reya.extraenchants.loot;

import com.mojang.serialization.Codec;
import com.reya.extraenchants.ExtraEnchants;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;

public final class ModLootModifiers {
    public static final DeferredRegister<Codec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(ForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, ExtraEnchants.MOD_ID);

    static {
        LOOT_MODIFIERS.register("auto_smelt", AutoSmeltModifier.CODEC);
        LOOT_MODIFIERS.register("telekinesis", TelekinesisModifier.CODEC);
    }

    private ModLootModifiers() {
    }
}
