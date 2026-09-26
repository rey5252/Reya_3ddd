package com.overenchant.mixin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla clamps stored levels to 255; read them as they are. */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
    @Inject(method = "getEnchantmentLevel(Lnet/minecraft/nbt/CompoundTag;)I", at = @At("HEAD"), cancellable = true)
    private static void overenchant$noClamp(CompoundTag tag, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(Math.max(0, tag.getInt("lvl")));
    }
}
