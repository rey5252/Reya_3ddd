package com.reya.singularityfusion.compat;

import java.util.Locale;

import com.reya.singularityfusion.SingularityFusion;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * JEI's page for a singularity fusion: the pylons' ingredients on a ring round the catalyst (which sits in a little
 * viewport with the singularity in it), an arrow to what it makes, the energy it takes (in OP) and how long.
 */
public class FusionCategory implements IRecipeCategory<FusionRecipe> {
    public static final RecipeType<FusionRecipe> TYPE = RecipeType.create(SingularityFusion.MODID, "fusion", FusionRecipe.class);
    private static final ResourceLocation PANEL = new ResourceLocation(SingularityFusion.MODID, "textures/gui/fusion_core.png");
    private static final int FRAME = 0xFF8A4FE0, CORNER = 0xFFD6B4FF, ENERGY = 0xFFD7A8FF, TIME = 0xFF9C93B4;

    private final IDrawable background, icon;

    public FusionCategory(IGuiHelper gui) {
        background = gui.drawableBuilder(PANEL, Layouts.SHEET_JEI[0], Layouts.SHEET_JEI[1], Layouts.JEI[0], Layouts.JEI[1])
                .setTextureSize(Layouts.W, Layouts.TEX_H).build();
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

    /** Where the ingredient i of n sits (its item's top left): round the ring, from the top, clockwise. */
    private static int[] slot(int i, int n) {
        double a = -Math.PI / 2.0D + 2.0D * Math.PI * i / Math.max(1, n);
        return new int[] {(int) Math.round(Layouts.JEI_CENTER[0] + Layouts.JEI_RING_R * Math.cos(a)) - 8,
                (int) Math.round(Layouts.JEI_CENTER[1] + Layouts.JEI_RING_R * Math.sin(a)) - 8};
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, FusionRecipe recipe, IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, Layouts.JEI_CENTER[0] - 8, Layouts.JEI_CENTER[1] - 8).addIngredients(recipe.catalyst())
                .addTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.singularityfusion.catalyst")
                        .withStyle(ChatFormatting.LIGHT_PURPLE)));
        int n = recipe.getIngredients().size();
        for (int i = 0; i < n; i++) {
            int[] at = slot(i, n);
            builder.addSlot(RecipeIngredientRole.INPUT, at[0], at[1]).addIngredients(recipe.getIngredients().get(i))
                    .addTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.singularityfusion.ingredient")
                            .withStyle(ChatFormatting.GRAY)));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, Layouts.JEI_OUTPUT[0], Layouts.JEI_OUTPUT[1]).addItemStack(recipe.result());
    }

    @Override
    public void draw(FusionRecipe recipe, IRecipeSlotsView slots, GuiGraphics g, double mouseX, double mouseY) {
        // violet frames round the slots, drawn as outlines so the singularity shows round the catalyst
        frame(g, Layouts.JEI_CENTER[0] - 9, Layouts.JEI_CENTER[1] - 9);
        int n = recipe.getIngredients().size();
        for (int i = 0; i < n; i++) {
            int[] at = slot(i, n);
            g.fill(at[0] - 1, at[1] - 1, at[0] + 17, at[1] + 17, 0xC00C0816);
            frame(g, at[0] - 1, at[1] - 1);
        }
        g.fill(Layouts.JEI_OUTPUT[0] - 1, Layouts.JEI_OUTPUT[1] - 1, Layouts.JEI_OUTPUT[0] + 17, Layouts.JEI_OUTPUT[1] + 17, 0xC00C0816);
        frame(g, Layouts.JEI_OUTPUT[0] - 1, Layouts.JEI_OUTPUT[1] - 1);
        Font font = Minecraft.getInstance().font;
        Component energy = Component.translatable("jei.singularityfusion.energy", String.format(Locale.ROOT, "%,d", recipe.energy()));
        g.drawString(font, energy, 5, Layouts.JEI_TEXT_Y, ENERGY, false);
        Component time = Component.translatable("jei.singularityfusion.time", String.format(Locale.ROOT, "%.1f", recipe.time() / 20.0F));
        g.drawString(font, time, Layouts.JEI[0] - 5 - font.width(time), Layouts.JEI_TEXT_Y, TIME, false);
    }

    /** An 18 square frame: a violet line with lighter corners. */
    private static void frame(GuiGraphics g, int x, int y) {
        g.fill(x, y, x + 18, y + 1, FRAME);
        g.fill(x, y + 17, x + 18, y + 18, FRAME);
        g.fill(x, y, x + 1, y + 18, FRAME);
        g.fill(x + 17, y, x + 18, y + 18, FRAME);
        g.fill(x, y, x + 1, y + 1, CORNER);
        g.fill(x + 17, y, x + 18, y + 1, CORNER);
        g.fill(x, y + 17, x + 1, y + 18, CORNER);
        g.fill(x + 17, y + 17, x + 18, y + 18, CORNER);
    }
}
