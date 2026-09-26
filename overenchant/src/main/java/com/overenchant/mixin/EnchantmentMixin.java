package com.overenchant.mixin;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Levels above X have no roman numeral key; show them as plain numbers. */
@Mixin(Enchantment.class)
public abstract class EnchantmentMixin {
    @Inject(method = "getFullname", at = @At("RETURN"), cancellable = true)
    private void overenchant$fullname(int level, CallbackInfoReturnable<Component> cir) {
        if (level <= 10) return;
        Enchantment self = (Enchantment) (Object) this;
        MutableComponent name = Component.translatable(self.getDescriptionId());
        name.withStyle(self.isCurse() ? ChatFormatting.RED : ChatFormatting.GRAY);
        name.append(CommonComponents.SPACE).append(Component.literal(Integer.toString(level)));
        cir.setReturnValue(name);
    }
}
