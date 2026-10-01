package com.reya.alfheimheart.machine.apothecary;

import java.util.List;

import javax.annotation.Nullable;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineConfig;
import com.reya.alfheimheart.machine.MachineLayout;
import com.reya.alfheimheart.machine.MachineLayouts;
import com.reya.alfheimheart.machine.Units;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import vazkii.botania.api.recipe.PetalApothecaryRecipe;
import vazkii.botania.client.fx.SparkleParticleData;
import vazkii.botania.client.fx.WispParticleData;

/**
 * The Petal Apothecary: Botania's petal apothecary that fills itself. Its inputs make flowers by every petal
 * apothecary recipe (the generating and functional flowers, and whatever a pack adds), the seeds in the slot
 * under the bowl finishing each, as seeds thrown into Botania's apothecary do; a little mana stands for its water.
 */
public class PetalApothecaryBlockEntity extends MachineBlockEntity {
    public static final MachineLayout LAYOUT = MachineLayouts.PETAL_APOTHECARY;
    /** Its slots (its layout's): the inputs, the outputs from OUTPUT_START, the special slots from SPECIAL_START. */
    public static final int INPUTS = LAYOUT.inputCount(), OUTPUTS = LAYOUT.outputCount(), OUTPUT_START = LAYOUT.outputStart(),
            SPECIAL_START = LAYOUT.specialStart();
    public static final int REAGENT = SPECIAL_START;
    public static final int SLOTS = SPECIAL_START + 1;

    public PetalApothecaryBlockEntity(BlockPos pos, BlockState state) {
        super(AlfheimHeart.PETAL_APOTHECARY_BE.get(), pos, state, LAYOUT);
    }

    @Override
    public int capacity() {
        return MachineConfig.APOTHECARY.capacity.get();
    }

    @Override
    protected int chargeRate() {
        return MachineConfig.APOTHECARY.chargeRate.get();
    }

    @Override
    protected int drawPerTick() {
        return MachineConfig.APOTHECARY.drawPerTick.get();
    }

    @Override
    protected boolean acceptsInput(ItemStack stack) {
        return takesInput(level, stack);
    }

    @Override
    protected boolean acceptsSpecial(int index, ItemStack stack) {
        return takesReagent(level, stack);
    }

    public static boolean takesInput(@Nullable Level level, ItemStack stack) {
        return Units.<PetalApothecaryRecipe>takes(level, PetalApothecaryRecipe.TYPE_ID, "petal_apothecary", stack,
                PetalApothecaryRecipe::getIngredients);
    }

    public static boolean takesReagent(@Nullable Level level, ItemStack stack) {
        return Units.<PetalApothecaryRecipe>takes(level, PetalApothecaryRecipe.TYPE_ID, "petal_apothecary/reagent", stack,
                r -> List.of(r.getReagent()));
    }

    @Nullable
    @Override
    protected Job findJob(Level level) {
        return find(level, items, true);
    }

    /**
     * The craft the slots allow: the recipe with the most ingredients that the inputs make, seeds in the reagent
     * slot (unless `needReagent` is false: the screen asks that to tell what is missing).
     */
    @Nullable
    public static Job find(@Nullable Level level, IItemHandler items, boolean needReagent) {
        if (level == null) return null;
        ItemStack reagent = items.getStackInSlot(REAGENT);
        for (PetalApothecaryRecipe recipe : Units.<Container, PetalApothecaryRecipe>recipes(level, PetalApothecaryRecipe.TYPE_ID)) {
            if (needReagent && (reagent.isEmpty() || !recipe.getReagent().test(reagent))) continue;
            Units.Pick pick = Units.pick(items, INPUT_START, OUTPUT_START, recipe.getIngredients());
            if (pick == null) continue;
            Container container = pick.container();
            if (!recipe.matches(container, level)) continue;
            ItemStack out = recipe.assemble(container, level.registryAccess());
            if (out.isEmpty()) continue;
            int[] taken = new int[SLOTS];
            System.arraycopy(pick.taken(), 0, taken, INPUT_START, INPUTS);
            taken[REAGENT] = 1;
            return new Job(recipe.getId(), taken, List.of(out), MachineConfig.APOTHECARY_MANA.get(),
                    MachineConfig.APOTHECARY.craftTicks.get(), pick.units());
        }
        return null;
    }

    @Override
    protected void onCrafted(Level level, BlockPos pos, Job job) {
        level.playSound(null, pos, SoundEvents.GENERIC_SPLASH, SoundSource.BLOCKS, 0.25F, 1.6F);
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.6F, 1.8F);
    }

    @Override
    protected void onClientCrafted() {
        Level level = getLevel();
        if (level == null) return;
        BlockPos pos = getBlockPos();
        double x = pos.getX() + 0.5D, y = pos.getY() + 0.85D, z = pos.getZ() + 0.5D;
        for (int i = 0; i < 14; i++) {
            float r = 0.6F + level.random.nextFloat() * 0.4F, g = 0.4F + level.random.nextFloat() * 0.6F, b = 0.6F + level.random.nextFloat() * 0.4F;
            level.addParticle(WispParticleData.wisp(0.18F, r, g, b, 0.6F), x, y, z, (level.random.nextDouble() - 0.5D) * 0.06D, 0.05D,
                    (level.random.nextDouble() - 0.5D) * 0.06D);
        }
        for (int i = 0; i < 5; i++) {
            level.addParticle(SparkleParticleData.sparkle(1.1F, 1.0F, 0.8F, 1.0F, 5), x + (level.random.nextDouble() - 0.5D) * 0.6D,
                    y + 0.2D + level.random.nextDouble() * 0.3D, z + (level.random.nextDouble() - 0.5D) * 0.6D, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alfheimheart.petal_apothecary");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PetalApothecaryMenu(id, inventory, this, items, data);
    }
}
