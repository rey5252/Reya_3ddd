package com.reya.managarden.client;

import java.util.List;

import com.reya.managarden.ManaGarden;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

/**
 * Only loaded when JEI is installed: tells JEI where the greenhouse GUI draws outside its panel (the
 * keeper, the name plates, the vines), so JEI's item lists keep clear of them, and gives the
 * greenhouse and its upgrades information pages.
 */
@JeiPlugin
public class JeiCompat implements IModPlugin {
    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(ManaGarden.MODID, "jei");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        info(registration, ManaGarden.GREENHOUSE_ITEM.get(), "greenhouse");
        info(registration, ManaGarden.GREENHOUSE_HEART.get(), "heart");
        info(registration, ManaGarden.UPGRADE_BASE.get(), "upgrades");
        info(registration, ManaGarden.SPEED_UPGRADE.get(), "upgrades");
        info(registration, ManaGarden.CAPACITY_UPGRADE.get(), "upgrades");
        info(registration, ManaGarden.LUCK_UPGRADE.get(), "upgrades");
        info(registration, ManaGarden.YIELD_UPGRADE.get(), "upgrades");
    }

    private static void info(IRecipeRegistration registration, ItemLike item, String key) {
        registration.addIngredientInfo(new ItemStack(item), VanillaTypes.ITEM_STACK, Component.translatable("jei.managarden.info." + key));
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(GreenhouseScreen.class, new IGuiContainerHandler<GreenhouseScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(GreenhouseScreen screen) {
                return screen.extraAreas();
            }
        });
    }
}
