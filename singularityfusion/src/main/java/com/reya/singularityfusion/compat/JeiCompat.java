package com.reya.singularityfusion.compat;

import java.util.List;

import javax.annotation.Nullable;

import com.reya.singularityfusion.SingularityFusion;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Only loaded when JEI is installed: the fusions get a page of their own, the core and the pylons as its catalysts. */
@JeiPlugin
public class JeiCompat implements IModPlugin {
    @Nullable
    private static IJeiRuntime runtime;

    /** Opens JEI's fusion page (for the dev screenshots); false if JEI hasn't started. */
    public static boolean showFusions() {
        if (runtime == null) return false;
        runtime.getRecipesGui().showTypes(List.of(FusionCategory.TYPE));
        return true;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        runtime = jeiRuntime;
    }

    @Override
    public ResourceLocation getPluginUid() {
        return new ResourceLocation(SingularityFusion.MODID, "jei");
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new FusionCategory(registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        if (Minecraft.getInstance().level == null) return;
        registration.addRecipes(FusionCategory.TYPE,
                Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(SingularityFusion.FUSION_TYPE.get()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(SingularityFusion.FUSION_CORE_ITEM.get()), FusionCategory.TYPE);
        registration.addRecipeCatalyst(new ItemStack(SingularityFusion.GRAVITON_PYLON_ITEM.get()), FusionCategory.TYPE);
    }
}
