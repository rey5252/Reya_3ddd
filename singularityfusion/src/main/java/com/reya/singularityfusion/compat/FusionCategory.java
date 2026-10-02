package com.reya.singularityfusion.compat;

import java.util.Locale;

import com.reya.singularityfusion.SingularityFusion;
import com.reya.singularityfusion.client.FusionPage;
import com.reya.singularityfusion.gui.Layouts;
import com.reya.singularityfusion.recipe.FusionRecipe;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * JEI's page for a singularity fusion (drawn by {@link FusionPage}, in the core's screen's style): the pylons'
 * ingredients on an orbit round the catalyst (the singularity turning behind it), an arrow to what it makes, the
 * energy it takes (in OP) and how long.
 */
public class FusionCategory implements IRecipeCategory<FusionRecipe> {
    public static final RecipeType<FusionRecipe> TYPE = RecipeType.create(SingularityFusion.MODID, "fusion", FusionRecipe.class);

    private final IDrawable background, icon;

    public FusionCategory(IGuiHelper gui) {
        background = gui.createBlankDrawable(Layouts.JEI[0], Layouts.JEI[1]);
        icon = gui.createDrawableIngredient(VanillaTypes.ITEM_STACK, new ItemStack(SingularityFusion.FUSION_CORE_ITEM.get()));
    }

    @Override
    public RecipeType<FusionRecipe> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.singularityfusion.fusion");
    }

    @Override
    public IDrawable getBackground() {
        return background;
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FusionRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, Layouts.JEI_CENTER[0] - 8, Layouts.JEI_CENTER[1] - 8).addIngredients(recipe.catalyst())
                .addTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.singularityfusion.catalyst")
                        .withStyle(ChatFormatting.LIGHT_PURPLE)));
        int n = recipe.getIngredients().size();
        for (int i = 0; i < n; i++) {
            int[] at = FusionPage.slot(i, n);
            builder.addSlot(RecipeIngredientRole.INPUT, at[0], at[1]).addIngredients(recipe.getIngredients().get(i))
                    .addTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.singularityfusion.ingredient")
                            .withStyle(ChatFormatting.GRAY)));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, Layouts.JEI_OUTPUT[0], Layouts.JEI_OUTPUT[1]).addItemStack(recipe.result());
    }

    @Override
    public void draw(FusionRecipe recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        Component energy = Component.translatable("jei.singularityfusion.energy", String.format(Locale.ROOT, "%,d", recipe.energy()));
        Component time = Component.translatable("jei.singularityfusion.time", String.format(Locale.ROOT, "%.1f", recipe.time() / 20.0F));
        FusionPage.draw(g, recipe.getIngredients().size(), energy, time);
    }
}
