package com.reya.alfheimheart.machine.infuser;

import java.util.List;

import javax.annotation.Nullable;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineConfig;
import com.reya.alfheimheart.machine.Units;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import vazkii.botania.api.recipe.ManaInfusionRecipe;
import vazkii.botania.api.recipe.StateIngredient;
import vazkii.botania.client.fx.SparkleParticleData;
import vazkii.botania.client.fx.WispParticleData;
import vazkii.botania.common.handler.BotaniaSounds;

/**
 * The Mana Infuser: a mana pool's crafting in a block. Whatever its inputs hold that a mana pool turns into
 * something else (manasteel, mana diamonds and pearls, mana powder, glass, string, quartz...) it infuses, a few
 * of a kind at once; a catalyst in its special slot (an Alchemy or a Conjuration Catalyst) works as the block
 * under a pool does: its recipes come first.
 */
public class ManaInfuserBlockEntity extends MachineBlockEntity {
    public static final int CATALYST = SPECIAL_START;
    public static final int SLOTS = SPECIAL_START + 1;

    public ManaInfuserBlockEntity(BlockPos pos, BlockState state) {
        super(AlfheimHeart.MANA_INFUSER_BE.get(), pos, state, 1);
    }

    @Override
    public int capacity() {
        return MachineConfig.INFUSER.capacity.get();
    }

    @Override
    protected int chargeRate() {
        return MachineConfig.INFUSER.chargeRate.get();
    }

    @Override
    protected int drawPerTick() {
        return MachineConfig.INFUSER.drawPerTick.get();
    }

    @Override
    protected boolean acceptsInput(ItemStack stack) {
        return takesInput(level, stack);
    }

    @Override
    protected boolean acceptsSpecial(int index, ItemStack stack) {
        return takesCatalyst(level, stack);
    }

    @Override
    protected int specialLimit(int index) {
        return 1;
    }

    public static boolean takesInput(@Nullable Level level, ItemStack stack) {
        return Units.<ManaInfusionRecipe>takes(level, ManaInfusionRecipe.TYPE_ID, "mana_infusion", stack, ManaInfusionRecipe::getIngredients);
    }

    public static boolean takesCatalyst(@Nullable Level level, ItemStack stack) {
        return stack.getItem() instanceof BlockItem && Units.<ManaInfusionRecipe>takes(level, ManaInfusionRecipe.TYPE_ID,
                "mana_infusion/catalyst", stack, r -> {
                    StateIngredient catalyst = r.getRecipeCatalyst();
                    return catalyst == null ? List.of() : List.of(Ingredient.of(catalyst.getDisplayedStacks().stream()));
                });
    }

    /** The block a catalyst item stands for, as if the infuser stood on it (air without one). */
    public static BlockState catalystState(ItemStack stack) {
        return stack.getItem() instanceof BlockItem block ? block.getBlock().defaultBlockState() : Blocks.AIR.defaultBlockState();
    }

    /** The recipe a mana pool on this catalyst would use for the stack: a catalyst's recipes come first, as in a pool. */
    @Nullable
    public static ManaInfusionRecipe recipeFor(Level level, ItemStack stack, BlockState catalyst) {
        ManaInfusionRecipe plain = null;
        for (ManaInfusionRecipe recipe : Units.<Container, ManaInfusionRecipe>recipes(level, ManaInfusionRecipe.TYPE_ID)) {
            if (!recipe.matches(stack)) continue;
            StateIngredient needs = recipe.getRecipeCatalyst();
            if (needs == null) {
                if (plain == null) plain = recipe;
            } else if (needs.test(catalyst)) {
                return recipe;
            }
        }
        return plain;
    }

    @Nullable
    @Override
    protected Job findJob(Level level) {
        return find(level, items);
    }

    /**
     * The craft the inputs allow: the first slot whose item the mana infuses, as many of it at once as the batch
     * and the room in the outputs allow (at least one), mana for each.
     */
    @Nullable
    public static Job find(@Nullable Level level, IItemHandler items) {
        if (level == null) return null;
        BlockState catalyst = catalystState(items.getStackInSlot(CATALYST));
        for (int i = 0; i < INPUTS; i++) {
            ItemStack stack = items.getStackInSlot(INPUT_START + i);
            if (stack.isEmpty()) continue;
            ManaInfusionRecipe recipe = recipeFor(level, stack, catalyst);
            if (recipe == null) continue;
            ItemStack one = recipe.getRecipeOutput(level.registryAccess(), stack.copyWithCount(1));
            if (one.isEmpty()) continue;
            int n = Math.min(stack.getCount(), MachineConfig.INFUSER_BATCH.get());
            int fits = Units.room(items, OUTPUT_START, SPECIAL_START, one) / Math.max(1, one.getCount());
            n = Math.max(1, Math.min(n, fits));
            int[] taken = new int[SLOTS];
            taken[INPUT_START + i] = n;
            ItemStack out = one.copyWithCount(one.getCount() * n);
            return new Job(recipe.getId(), taken, List.of(out), recipe.getManaToConsume() * n, MachineConfig.INFUSER.craftTicks.get(),
                    List.of(stack.copyWithCount(n)));
        }
        return null;
    }

    @Override
    protected void onCrafted(Level level, BlockPos pos, Job job) {
        level.playSound(null, pos, BotaniaSounds.manaPoolCraft, SoundSource.BLOCKS, 0.4F, 4.0F);
    }

    @Override
    protected void onClientCrafted() {
        Level level = getLevel();
        if (level == null) return;
        BlockPos pos = getBlockPos();
        double x = pos.getX() + 0.5D, y = pos.getY() + 0.8D, z = pos.getZ() + 0.5D;
        for (int i = 0; i < 16; i++) {
            double a = i / 16.0D * Math.PI * 2.0D;
            level.addParticle(WispParticleData.wisp(0.2F, 0.4F, 0.85F, 1.0F, 0.5F), x + Math.cos(a) * 0.3D, y, z + Math.sin(a) * 0.3D,
                    0.0D, 0.03D, 0.0D);
        }
        for (int i = 0; i < 6; i++) {
            level.addParticle(SparkleParticleData.sparkle(1.2F, 0.6F, 0.9F, 1.0F, 5), x + (level.random.nextDouble() - 0.5D) * 0.6D,
                    y + 0.2D + level.random.nextDouble() * 0.4D, z + (level.random.nextDouble() - 0.5D) * 0.6D, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alfheimheart.mana_infuser");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ManaInfuserMenu(id, inventory, this, items, data);
    }
}
