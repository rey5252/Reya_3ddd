package com.reya.extraenchants;

import com.reya.extraenchants.enchantment.ModEnchantments;
import com.reya.extraenchants.loot.ModLootModifiers;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ExtraEnchants.MOD_ID)
public class ExtraEnchants {
    public static final String MOD_ID = "extraenchants";

    public ExtraEnchants() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModEnchantments.ENCHANTMENTS.register(modBus);
        ModLootModifiers.LOOT_MODIFIERS.register(modBus);

        MinecraftForge.EVENT_BUS.register(new EnchantmentEvents());
    }
}
