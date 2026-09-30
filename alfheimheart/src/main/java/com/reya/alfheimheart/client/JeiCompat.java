package com.reya.alfheimheart.client;

import java.util.List;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.greenhouse.client.GreenhouseScreen;
import com.reya.alfheimheart.portal.client.PortalScreen;
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
import net.minecraft.world.level.ItemLike;
import vazkii.botania.api.recipe.ElvenTradeRecipe;

/**
 * Only loaded when JEI is installed: information pages for the blocks and items, the Elven Portal as a
 * catalyst of Botania's elven trades (and the arrows in its GUI opening them), and the areas the GUIs draw
 * outside their panels, so JEI's item lists keep clear of them.
 */
@JeiPlugin
public class JeiCompat implements IModPlugin {
    /** Botania's own category for elven trades (the same id and recipe class as its JEI plugin registers). */
    private static final RecipeType<ElvenTradeRecipe> ELVEN_TRADE = RecipeType.create("botania", "elven_trade", ElvenTradeRecipe.class);

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(AlfheimHeart.MODID, "jei");
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        info(registration, AlfheimHeart.GREENHOUSE_ITEM.get(), "greenhouse");
        info(registration, AlfheimHeart.GREENHOUSE_HEART.get(), "heart");
        info(registration, AlfheimHeart.UPGRADE_BASE.get(), "upgrades");
        info(registration, AlfheimHeart.SPEED_UPGRADE.get(), "upgrades");
        info(registration, AlfheimHeart.CAPACITY_UPGRADE.get(), "upgrades");
        info(registration, AlfheimHeart.LUCK_UPGRADE.get(), "upgrades");
        info(registration, AlfheimHeart.YIELD_UPGRADE.get(), "upgrades");
        info(registration, AlfheimHeart.PORTAL_ITEM.get(), "portal");
    }

    private static void info(IRecipeRegistration registration, ItemLike item, String key) {
        registration.addIngredientInfo(new ItemStack(item), VanillaTypes.ITEM_STACK, Component.translatable("jei.alfheimheart.info." + key));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(AlfheimHeart.PORTAL_ITEM.get()), ELVEN_TRADE);
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(GreenhouseScreen.class, new IGuiContainerHandler<GreenhouseScreen>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(GreenhouseScreen screen) {
                return screen.extraAreas();
            }
        });
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
