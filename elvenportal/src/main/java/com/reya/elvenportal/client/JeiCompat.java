package com.reya.elvenportal.client;

import java.util.List;

import com.reya.elvenportal.ElvenPortal;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import vazkii.botania.api.recipe.ElvenTradeRecipe;

/**
 * Only loaded when JEI is installed: the portal makes Botania's elven trades (JEI's "Elven Trade" pages show
 * it beside the Alfheim portal, and the arrows in its GUI open them), it has an information page, and JEI's
 * item lists keep clear of the vines and the title scroll round its GUI.
 */
@JeiPlugin
public class JeiCompat implements IModPlugin {
    /** Botania's own category for elven trades (the same id and recipe class as its JEI plugin registers). */
    private static final RecipeType<ElvenTradeRecipe> ELVEN_TRADE = RecipeType.create("botania", "elven_trade", ElvenTradeRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(ElvenPortal.MODID, "jei");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addIngredientInfo(new ItemStack(ElvenPortal.PORTAL_ITEM.get()), VanillaTypes.ITEM_STACK,
                Component.translatable("jei.elvenportal.info.portal"));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(ElvenPortal.PORTAL_ITEM.get()), ELVEN_TRADE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(PortalScreen.class, new IGuiContainerHandler<PortalScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(PortalScreen screen) {
                return screen.extraAreas();
            }
        });
        // the arrows into and out of the portal show the elven trades
        registration.addRecipeClickArea(PortalScreen.class, 74, 53, 12, 9, ELVEN_TRADE);
        registration.addRecipeClickArea(PortalScreen.class, 155, 53, 12, 9, ELVEN_TRADE);
    }
}
