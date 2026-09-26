package com.reya.multibrewer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraftforge.items.IItemHandler;

/**
 * What the brewer makes from its slots. Shared by the block entity (to brew) and the screen
 * (to show the result before it's made).
 *
 * <ul>
 *   <li>Every effect of every input potion ends up in one potion. The same effect at the same level
 *       adds up its time; at different levels the stronger one wins.</li>
 *   <li>Redstone: +50% time (+25% more per potency upgrade).</li>
 *   <li>Glowstone: +1 level (+1 more for every two potency upgrades).</li>
 * </ul>
 */
public final class BrewLogic {
    public static final int IN_1 = 0, IN_2 = 1, IN_3 = 2, OUTPUT = 3, FUEL = 4, DURATION = 5, POWER = 6, UP_1 = 7, UP_2 = 8;
    public static final int SLOTS = 9;

    private BrewLogic() {
    }

    public static boolean isPotion(ItemStack stack) {
        return stack.is(Items.POTION) || stack.is(Items.SPLASH_POTION) || stack.is(Items.LINGERING_POTION);
    }

    public static int upgrades(IItemHandler items, UpgradeItem.Kind kind) {
        int n = 0;
        for (int slot = UP_1; slot <= UP_2; slot++) {
            ItemStack s = items.getStackInSlot(slot);
            if (s.getItem() instanceof UpgradeItem u && u.kind == kind) n += s.getCount();
        }
        return Math.min(n, 4);
    }

    public static int brewTicks(IItemHandler items) {
        return Math.max(10, (int) (Config.BREW_TICKS.get() * Math.pow(0.7D, upgrades(items, UpgradeItem.Kind.SPEED))));
    }

    /** Chance that a brew doesn't use up fuel, redstone or glowstone. */
    public static float saveChance(IItemHandler items) {
        return 0.15F * upgrades(items, UpgradeItem.Kind.EFFICIENCY);
    }

    public static float durationBonus(IItemHandler items) {
        return 0.5F + 0.25F * upgrades(items, UpgradeItem.Kind.POTENCY);
    }

    public static int levelBonus(IItemHandler items) {
        return 1 + upgrades(items, UpgradeItem.Kind.POTENCY) / 2;
    }

    /** The potion this brew would make, or null when there is nothing to do. */
    @Nullable
    public static ItemStack result(IItemHandler items) {
        int potions = 0;
        boolean splash = false;
        boolean lingering = false;
        Map<MobEffect, MobEffectInstance> merged = new LinkedHashMap<>();
        for (int slot = IN_1; slot <= IN_3; slot++) {
            ItemStack s = items.getStackInSlot(slot);
            if (!isPotion(s)) continue;
            potions++;
            splash |= s.is(Items.SPLASH_POTION);
            lingering |= s.is(Items.LINGERING_POTION);
            for (MobEffectInstance e : PotionUtils.getMobEffects(s)) {
                MobEffectInstance old = merged.get(e.getEffect());
                if (old == null || e.getAmplifier() > old.getAmplifier()) {
                    merged.put(e.getEffect(), copy(e, e.getAmplifier(), e.getDuration()));
                } else if (e.getAmplifier() == old.getAmplifier()) {
                    merged.put(e.getEffect(), copy(e, old.getAmplifier(), old.getDuration() + e.getDuration()));
                }
            }
        }
        boolean longer = items.getStackInSlot(DURATION).is(Items.REDSTONE);
        boolean stronger = items.getStackInSlot(POWER).is(Items.GLOWSTONE_DUST);
        if (potions == 0 || merged.isEmpty() || potions < 2 && !longer && !stronger) return null;

        int maxTicks = Config.MAX_MINUTES.get() * 1200;
        int maxAmp = Config.MAX_LEVEL.get() - 1;
        float timeBonus = durationBonus(items);
        int levelBonus = levelBonus(items);
        List<MobEffectInstance> out = new ArrayList<>();
        for (MobEffectInstance e : merged.values()) {
            int amp = stronger ? Math.min(maxAmp, e.getAmplifier() + levelBonus) : e.getAmplifier();
            int dur = e.getDuration();
            if (longer && !e.getEffect().isInstantenous()) dur = Math.round(dur * (1.0F + timeBonus));
            if (!e.getEffect().isInstantenous()) dur = Math.min(maxTicks, dur);
            out.add(copy(e, amp, dur));
        }

        ItemStack potion = new ItemStack(lingering ? Items.LINGERING_POTION : splash ? Items.SPLASH_POTION : Items.POTION);
        PotionUtils.setCustomEffects(potion, out);
        potion.getOrCreateTag().putInt("CustomPotionColor", PotionUtils.getColor(out));
        potion.setHoverName(Component.translatable("item.multibrewer.mixed_potion").withStyle(s -> s.withItalic(false)));
        return potion;
    }

    private static MobEffectInstance copy(MobEffectInstance e, int amp, int dur) {
        return new MobEffectInstance(e.getEffect(), dur, amp, e.isAmbient(), e.isVisible(), e.showIcon());
    }
}
