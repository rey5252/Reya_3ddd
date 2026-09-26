package com.reya.mobfarm.test;

import com.reya.mobfarm.FarmTier;
import com.reya.mobfarm.MobFarm;
import com.reya.mobfarm.ModRegistry;
import com.reya.mobfarm.farm.MobFarmBlockEntity;
import com.reya.mobfarm.lasso.LassoItem;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests, run on a real headless server with {@code ./gradlew runGameTestServer}
 * (CI does this on every push). Each test gets a small empty stone platform.
 */
@GameTestHolder(MobFarm.MOD_ID)
@PrefixGameTestTemplate(false)
public class MobFarmGameTests {
    private static final BlockPos FARM = new BlockPos(2, 1, 2);
    private static final BlockPos CHEST = new BlockPos(3, 1, 2);

    private static MobFarmBlockEntity placeFarm(GameTestHelper helper, FarmTier tier, EntityType<?> mob) {
        helper.setBlock(FARM, ModRegistry.FARMS.get(tier).get());
        MobFarmBlockEntity farm = (MobFarmBlockEntity) helper.getBlockEntity(FARM);
        farm.setLasso(LassoItem.withMob(ModRegistry.LASSO.get(), mob));
        return farm;
    }

    /** A cow in a netherite farm puts beef into the chest next to it. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void farmFillsChestWithLoot(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST);
        placeFarm(helper, FarmTier.NETHERITE, EntityType.COW);
        helper.succeedWhen(() -> helper.assertContainerContains(CHEST, Items.BEEF));
    }

    /** Loot counts as a player kill, so blazes drop rods (they need "killed by player"). */
    @GameTest(template = "empty", timeoutTicks = 1600)
    public static void blazeFarmDropsRods(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST);
        placeFarm(helper, FarmTier.NETHERITE, EntityType.BLAZE);
        helper.succeedWhen(() -> helper.assertContainerContains(CHEST, Items.BLAZE_ROD));
    }

    /** Without a chest, loot stays in the farm's own output slots. */
    @GameTest(template = "empty", timeoutTicks = 300)
    public static void lootWaitsInFarmWithoutChest(GameTestHelper helper) {
        MobFarmBlockEntity farm = placeFarm(helper, FarmTier.NETHERITE, EntityType.CHICKEN);
        helper.succeedWhen(() -> {
            boolean hasLoot = false;
            for (int i = MobFarmBlockEntity.OUTPUT_START; i < MobFarmBlockEntity.OUTPUT_START + MobFarmBlockEntity.OUTPUT_COUNT; i++) {
                if (!farm.getItems().getStackInSlot(i).isEmpty()) hasLoot = true;
            }
            helper.assertTrue(hasLoot, "farm should hold loot in its output slots");
        });
    }

    /** A redstone signal stops the farm. */
    @GameTest(template = "empty", timeoutTicks = 400)
    public static void redstoneStopsFarm(GameTestHelper helper) {
        helper.setBlock(CHEST, Blocks.CHEST);
        helper.setBlock(new BlockPos(1, 1, 2), Blocks.REDSTONE_BLOCK);
        placeFarm(helper, FarmTier.NETHERITE, EntityType.COW);
        helper.runAfterDelay(250, () -> {
            helper.assertContainerEmpty(CHEST);
            helper.succeed();
        });
    }

    /** Right-clicking a mob with an empty lasso catches it and removes the mob. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void lassoCatchesMob(GameTestHelper helper) {
        Cow cow = helper.spawn(EntityType.COW, new BlockPos(2, 1, 2));
        Player player = helper.makeMockPlayer();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModRegistry.LASSO.get()));
        player.interactOn(cow, InteractionHand.MAIN_HAND);

        ItemStack lasso = player.getItemInHand(InteractionHand.MAIN_HAND);
        helper.assertTrue(LassoItem.getType(lasso) == EntityType.COW, "lasso should hold a cow");
        helper.assertTrue(cow.isRemoved(), "the caught cow should be gone");
        helper.succeed();
    }

    /** Bosses can't be caught. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void lassoIgnoresBosses(GameTestHelper helper) {
        // Created but never added to the world, so it can't start its explosion.
        var wither = EntityType.WITHER.create(helper.getLevel());
        helper.assertTrue(wither != null, "could not create a wither");
        helper.assertFalse(LassoItem.canCapture(wither), "the wither must not be catchable");
        helper.succeed();
    }

    /** All farm recipes and the lasso recipe are loaded. */
    @GameTest(template = "empty", timeoutTicks = 20)
    public static void recipesLoad(GameTestHelper helper) {
        var recipes = helper.getLevel().getRecipeManager();
        helper.assertTrue(recipes.byKey(new ResourceLocation(MobFarm.MOD_ID, "lasso")).isPresent(), "lasso recipe missing");
        for (FarmTier tier : FarmTier.values()) {
            ResourceLocation id = new ResourceLocation(MobFarm.MOD_ID, tier.id + "_mob_farm");
            helper.assertTrue(recipes.byKey(id).isPresent(), "recipe missing: " + id);
        }
        helper.succeed();
    }

    /** Breaking a farm drops the lasso so the mob isn't lost. */
    @GameTest(template = "empty", timeoutTicks = 40)
    public static void breakingFarmDropsLasso(GameTestHelper helper) {
        placeFarm(helper, FarmTier.WOODEN, EntityType.PIG);
        helper.destroyBlock(FARM);
        helper.assertItemEntityPresent(ModRegistry.LASSO.get(), FARM, 2.0D);
        helper.succeed();
    }
}
