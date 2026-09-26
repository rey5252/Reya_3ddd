package com.reya.extraenchants.enchantment;

import com.reya.extraenchants.ExtraEnchants;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantment.Rarity;
import net.minecraft.world.item.enchantment.EnchantmentCategory;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModEnchantments {
    public static final DeferredRegister<Enchantment> ENCHANTMENTS =
            DeferredRegister.create(ForgeRegistries.ENCHANTMENTS, ExtraEnchants.MOD_ID);

    /** Bow multishot: every shot fires 2 extra arrows per level. */
    public static final RegistryObject<Enchantment> VOLLEY = ENCHANTMENTS.register("volley",
            () -> new SimpleEnchantment(Rarity.RARE, EnchantmentCategory.BOW, 3, 20, 10, false, other -> true));

    /** More experience from ores (and some from ores that normally give none). */
    public static final RegistryObject<Enchantment> ORE_WISDOM = ENCHANTMENTS.register("ore_wisdom",
            () -> new SimpleEnchantment(Rarity.UNCOMMON, EnchantmentCategory.DIGGER, 3, 15, 9, false,
                    other -> other != Enchantments.SILK_TOUCH));

    /** More experience from killed mobs. */
    public static final RegistryObject<Enchantment> BATTLE_WISDOM = ENCHANTMENTS.register("battle_wisdom",
            () -> new SimpleEnchantment(Rarity.UNCOMMON, EnchantmentCategory.WEAPON, 3, 15, 9, true, other -> true));

    /** Mined blocks drop their smelted result. */
    public static final RegistryObject<Enchantment> AUTO_SMELT = ENCHANTMENTS.register("auto_smelt",
            () -> new SimpleEnchantment(Rarity.RARE, EnchantmentCategory.DIGGER, 1, 20, 0, false,
                    other -> other != Enchantments.SILK_TOUCH));

    /** Mined drops go straight into the inventory. */
    public static final RegistryObject<Enchantment> TELEKINESIS = ENCHANTMENTS.register("telekinesis",
            () -> new SimpleEnchantment(Rarity.UNCOMMON, EnchantmentCategory.DIGGER, 1, 15, 0, false, other -> true));

    /** Heals the attacker for part of the melee damage dealt. */
    public static final RegistryObject<Enchantment> LIFESTEAL = ENCHANTMENTS.register("lifesteal",
            () -> new SimpleEnchantment(Rarity.RARE, EnchantmentCategory.WEAPON, 3, 20, 10, true, other -> true));

    /** Chance for killed mobs to drop their head. */
    public static final RegistryObject<Enchantment> BEHEADING = ENCHANTMENTS.register("beheading",
            () -> new SimpleEnchantment(Rarity.RARE, EnchantmentCategory.WEAPON, 3, 15, 10, true, other -> true));

    /** Slows down hit targets. */
    public static final RegistryObject<Enchantment> FROST_ASPECT = ENCHANTMENTS.register("frost_aspect",
            () -> new SimpleEnchantment(Rarity.UNCOMMON, EnchantmentCategory.WEAPON, 2, 10, 20, true,
                    other -> other != Enchantments.FIRE_ASPECT));

    private ModEnchantments() {
    }
}
