package com.reya.alfheimheart.machine.altar;

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
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import vazkii.botania.api.recipe.RunicAltarRecipe;
import vazkii.botania.client.fx.SparkleParticleData;
import vazkii.botania.client.fx.WispParticleData;
import vazkii.botania.common.handler.BotaniaSounds;
import vazkii.botania.common.item.material.RuneItem;

/**
 * The Runic Altar: Botania's runic altar folded into one block. Its inputs make runes (and everything else) by
 * every runic altar recipe, a block of livingrock from its reagent slot finishing each, as on Botania's altar;
 * the runes among the ingredients aren't used up (Botania's altar gives them back), so they stay in the inputs.
 */
public class RuneAltarBlockEntity extends MachineBlockEntity {
    public static final MachineLayout LAYOUT = MachineLayouts.RUNE_ALTAR;
    /** Its slots (its layout's): the inputs, the outputs from OUTPUT_START, the special slots from SPECIAL_START. */
    public static final int INPUTS = LAYOUT.inputCount(), OUTPUTS = LAYOUT.outputCount(), OUTPUT_START = LAYOUT.outputStart(),
            SPECIAL_START = LAYOUT.specialStart();
    public static final int REAGENT = SPECIAL_START;
    public static final int SLOTS = SPECIAL_START + 1;

    public RuneAltarBlockEntity(BlockPos pos, BlockState state) {
        super(AlfheimHeart.RUNE_ALTAR_BE.get(), pos, state, LAYOUT);
    }

    @Override
    public int capacity() {
        return MachineConfig.ALTAR.capacity.get();
    }

    @Override
    protected int chargeRate() {
        return MachineConfig.ALTAR.chargeRate.get();
    }

    @Override
    protected int drawPerTick() {
        return MachineConfig.ALTAR.drawPerTick.get();
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
        return Units.<RunicAltarRecipe>takes(level, RunicAltarRecipe.TYPE_ID, "rune_altar", stack, RunicAltarRecipe::getIngredients);
    }

    public static boolean takesReagent(@Nullable Level level, ItemStack stack) {
        return Units.<RunicAltarRecipe>takes(level, RunicAltarRecipe.TYPE_ID, "rune_altar/reagent", stack, r -> List.of(r.getReagent()));
    }

    @Nullable
    @Override
    protected Job findJob(Level level) {
        return find(level, items, true);
    }

    /**
     * The craft the slots allow: the recipe with the most ingredients that the inputs make, a block of livingrock
     * in the reagent slot (unless `needReagent` is false: the screen asks that to tell what is missing).
     */
    @Nullable
    public static Job find(@Nullable Level level, IItemHandler items, boolean needReagent) {
        if (level == null) return null;
        ItemStack reagent = items.getStackInSlot(REAGENT);
        for (RunicAltarRecipe recipe : Units.<Container, RunicAltarRecipe>recipes(level, RunicAltarRecipe.TYPE_ID)) {
            if (needReagent && (reagent.isEmpty() || !recipe.getReagent().test(reagent))) continue;
            Units.Pick pick = Units.pick(items, INPUT_START, OUTPUT_START, recipe.getIngredients());
            if (pick == null) continue;
            Container container = pick.container();
            if (!recipe.matches(container, level)) continue;
            ItemStack out = recipe.assemble(container, level.registryAccess());
            if (out.isEmpty()) continue;
            int[] taken = new int[SLOTS];
            for (int i = 0; i < INPUTS; i++) {
                // the runes aren't used up
                boolean rune = items.getStackInSlot(INPUT_START + i).getItem() instanceof RuneItem;
                taken[INPUT_START + i] = rune ? 0 : pick.taken()[i];
            }
            taken[REAGENT] = 1;
            return new Job(recipe.getId(), taken, List.of(out), recipe.getManaUsage(), MachineConfig.ALTAR.craftTicks.get(), pick.units());
        }
        return null;
    }

    @Override
    protected void onCrafted(Level level, BlockPos pos, Job job) {
        level.playSound(null, pos, BotaniaSounds.runeAltarCraft, SoundSource.BLOCKS, 0.7F, 1.0F);
    }

    @Override
    protected void onJobStarted(Level level, BlockPos pos, Job job) {
        level.playSound(null, pos, BotaniaSounds.runeAltarStart, SoundSource.BLOCKS, 0.5F, 1.1F);
    }

    @Override
    protected void onClientCrafted() {
        Level level = getLevel();
        if (level == null) return;
        BlockPos pos = getBlockPos();
        double x = pos.getX() + 0.5D, y = pos.getY() + 0.95D, z = pos.getZ() + 0.5D;
        for (int i = 0; i < 24; i++) {
            double a = i / 24.0D * Math.PI * 2.0D;
            level.addParticle(WispParticleData.wisp(0.25F, 0.55F, 0.95F, 1.0F, 0.6F), x, y + 0.2D, z,
                    Math.cos(a) * 0.06D, 0.02D, Math.sin(a) * 0.06D);
        }
        for (int i = 0; i < 10; i++) {
            level.addParticle(SparkleParticleData.sparkle(1.4F, 0.7F, 1.0F, 1.0F, 6), x + (level.random.nextDouble() - 0.5D) * 0.8D,
                    y + 0.3D + level.random.nextDouble() * 0.5D, z + (level.random.nextDouble() - 0.5D) * 0.8D, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alfheimheart.rune_altar");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new RuneAltarMenu(id, inventory, this, items, data);
    }
}
