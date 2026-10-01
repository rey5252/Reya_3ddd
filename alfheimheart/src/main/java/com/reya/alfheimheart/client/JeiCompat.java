package com.reya.alfheimheart.client;

import java.util.List;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.greenhouse.client.GreenhouseScreen;
import com.reya.alfheimheart.machine.altar.client.RuneAltarScreen;
import com.reya.alfheimheart.machine.apothecary.client.PetalApothecaryScreen;
import com.reya.alfheimheart.machine.daisy.client.PureDaisyScreen;
import com.reya.alfheimheart.machine.farm.client.PetalFarmScreen;
import com.reya.alfheimheart.machine.field.client.CropFieldScreen;
import com.reya.alfheimheart.machine.orechid.client.OrechidMineScreen;
import com.reya.alfheimheart.machine.MachineLayout;
import com.reya.alfheimheart.machine.MachineLayouts;
import com.reya.alfheimheart.machine.client.MachineScreen;
import com.reya.alfheimheart.machine.infuser.client.ManaInfuserScreen;
import com.reya.alfheimheart.machine.plate.client.TerraPlateScreen;
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
import vazkii.botania.api.recipe.ManaInfusionRecipe;
import vazkii.botania.api.recipe.OrechidRecipe;
import vazkii.botania.api.recipe.PetalApothecaryRecipe;
import vazkii.botania.api.recipe.PureDaisyRecipe;
import vazkii.botania.api.recipe.RunicAltarRecipe;
import vazkii.botania.api.recipe.TerrestrialAgglomerationRecipe;
import vazkii.botania.common.crafting.MarimorphosisRecipe;
import vazkii.botania.common.crafting.OrechidIgnemRecipe;

/**
 * Only loaded when JEI is installed: information pages for the blocks and items, the Elven Portal and the
 * machines as catalysts of Botania's categories (and the arrows in their GUIs opening them), and the areas the
 * GUIs draw outside their panels, so JEI's item lists keep clear of them.
 */
@JeiPlugin
public class JeiCompat implements IModPlugin {
    /** Botania's own category for elven trades (the same id and recipe class as its JEI plugin registers). */
    private static final RecipeType<ElvenTradeRecipe> ELVEN_TRADE = RecipeType.create("botania", "elven_trade", ElvenTradeRecipe.class);
    private static final RecipeType<RunicAltarRecipe> RUNIC_ALTAR = RecipeType.create("botania", "runic_altar", RunicAltarRecipe.class);
    private static final RecipeType<TerrestrialAgglomerationRecipe> TERRA_PLATE = RecipeType.create("botania", "terra_plate",
            TerrestrialAgglomerationRecipe.class);
    private static final RecipeType<ManaInfusionRecipe> MANA_POOL = RecipeType.create("botania", "mana_pool", ManaInfusionRecipe.class);
    private static final RecipeType<PureDaisyRecipe> PURE_DAISY = RecipeType.create("botania", "pure_daisy", PureDaisyRecipe.class);
    private static final RecipeType<PetalApothecaryRecipe> PETALS = RecipeType.create("botania", "petals", PetalApothecaryRecipe.class);
    private static final RecipeType<OrechidRecipe> ORECHID = RecipeType.create("botania", "orechid", OrechidRecipe.class);
    private static final RecipeType<OrechidIgnemRecipe> ORECHID_IGNEM = RecipeType.create("botania", "orechid_ignem", OrechidIgnemRecipe.class);
    private static final RecipeType<MarimorphosisRecipe> MARIMORPHOSIS = RecipeType.create("botania", "marimorphosis",
            MarimorphosisRecipe.class);

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
        info(registration, AlfheimHeart.RUNE_ALTAR_ITEM.get(), "rune_altar");
        info(registration, AlfheimHeart.TERRA_PLATE_ITEM.get(), "terra_plate");
        info(registration, AlfheimHeart.MANA_INFUSER_ITEM.get(), "mana_infuser");
        info(registration, AlfheimHeart.PURE_DAISY_ITEM.get(), "pure_daisy");
        info(registration, AlfheimHeart.PETAL_APOTHECARY_ITEM.get(), "petal_apothecary");
        info(registration, AlfheimHeart.PETAL_FARM_ITEM.get(), "petal_farm");
        info(registration, AlfheimHeart.ORECHID_MINE_ITEM.get(), "orechid_mine");
        info(registration, AlfheimHeart.CROP_FIELD_ITEM.get(), "crop_field");
    }

    private static void info(IRecipeRegistration registration, ItemLike item, String key) {
        registration.addIngredientInfo(new ItemStack(item), VanillaTypes.ITEM_STACK, Component.translatable("jei.alfheimheart.info." + key));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(new ItemStack(AlfheimHeart.PORTAL_ITEM.get()), ELVEN_TRADE);
        registration.addRecipeCatalyst(new ItemStack(AlfheimHeart.RUNE_ALTAR_ITEM.get()), RUNIC_ALTAR);
        registration.addRecipeCatalyst(new ItemStack(AlfheimHeart.TERRA_PLATE_ITEM.get()), TERRA_PLATE);
        registration.addRecipeCatalyst(new ItemStack(AlfheimHeart.MANA_INFUSER_ITEM.get()), MANA_POOL);
        registration.addRecipeCatalyst(new ItemStack(AlfheimHeart.PURE_DAISY_ITEM.get()), PURE_DAISY);
        registration.addRecipeCatalyst(new ItemStack(AlfheimHeart.PETAL_APOTHECARY_ITEM.get()), PETALS);
        registration.addRecipeCatalyst(new ItemStack(AlfheimHeart.ORECHID_MINE_ITEM.get()), ORECHID, ORECHID_IGNEM, MARIMORPHOSIS);
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
        machine(registration, RuneAltarScreen.class);
        machine(registration, TerraPlateScreen.class);
        machine(registration, ManaInfuserScreen.class);
        machine(registration, PureDaisyScreen.class);
        machine(registration, PetalApothecaryScreen.class);
        machine(registration, PetalFarmScreen.class);
        machine(registration, OrechidMineScreen.class);
        machine(registration, CropFieldScreen.class);
        // the arrows into and out of the portal show its trades; each machine's click areas (its layout's) its recipes
        registration.addRecipeClickArea(PortalScreen.class, 74, 53, 12, 9, ELVEN_TRADE);
        registration.addRecipeClickArea(PortalScreen.class, 155, 53, 12, 9, ELVEN_TRADE);
        clickAreas(registration, RuneAltarScreen.class, MachineLayouts.RUNE_ALTAR, RUNIC_ALTAR);
        clickAreas(registration, TerraPlateScreen.class, MachineLayouts.TERRA_PLATE, TERRA_PLATE);
        clickAreas(registration, ManaInfuserScreen.class, MachineLayouts.MANA_INFUSER, MANA_POOL);
        clickAreas(registration, PureDaisyScreen.class, MachineLayouts.PURE_DAISY, PURE_DAISY);
        clickAreas(registration, PetalApothecaryScreen.class, MachineLayouts.PETAL_APOTHECARY, PETALS);
        clickAreas(registration, OrechidMineScreen.class, MachineLayouts.ORECHID_MINE, ORECHID, ORECHID_IGNEM, MARIMORPHOSIS);
    }

    private static <T extends MachineScreen<?>> void machine(IGuiHandlerRegistration registration, Class<T> screen) {
        registration.addGuiContainerHandler(screen, new IGuiContainerHandler<T>() {
            @Override
            public List<Rect2i> getGuiExtraAreas(T s) {
                return s.extraAreas();
            }
        });
    }

    private static <T extends MachineScreen<?>> void clickAreas(IGuiHandlerRegistration registration, Class<T> screen, MachineLayout layout,
                                                                RecipeType<?>... types) {
        for (int[] area : layout.clickAreas) registration.addRecipeClickArea(screen, area[0], area[1], area[2], area[3], types);
    }
}
