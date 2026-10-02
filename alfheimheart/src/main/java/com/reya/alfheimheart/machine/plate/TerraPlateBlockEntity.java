package com.reya.alfheimheart.machine.plate;

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
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;
import vazkii.botania.api.recipe.TerrestrialAgglomerationRecipe;
import vazkii.botania.client.fx.SparkleParticleData;
import vazkii.botania.client.fx.WispParticleData;
import vazkii.botania.common.handler.BotaniaSounds;

/**
 * The Terrestrial Plate: Botania's terrestrial agglomeration plate with no lapis and livingrock round it and no
 * sparks: its inputs make terrasteel (and whatever else the plate's recipes make), half a million mana a craft,
 * which it holds in a store of a million.
 */
public class TerraPlateBlockEntity extends MachineBlockEntity {
    public static final MachineLayout LAYOUT = MachineLayouts.TERRA_PLATE;
    /** Its slots (its layout's): the inputs, the outputs from OUTPUT_START, the special slots from SPECIAL_START. */
    public static final int INPUTS = LAYOUT.inputCount(), OUTPUTS = LAYOUT.outputCount(), OUTPUT_START = LAYOUT.outputStart(),
            SPECIAL_START = LAYOUT.specialStart();
    public static final int SLOTS = SPECIAL_START;

    public TerraPlateBlockEntity(BlockPos pos, BlockState state) {
        super(AlfheimHeart.TERRA_PLATE_BE.get(), pos, state, LAYOUT);
    }

    @Override
    public int capacity() {
        return MachineConfig.PLATE.capacity.get();
    }

    @Override
    protected int chargeRate() {
        return MachineConfig.PLATE.chargeRate.get();
    }

    @Override
    protected int drawPerTick() {
        return MachineConfig.PLATE.drawPerTick.get();
    }

    @Override
    protected boolean acceptsInput(ItemStack stack) {
        return takesInput(level, stack);
    }

    public static boolean takesInput(@Nullable Level level, ItemStack stack) {
        return Units.<TerrestrialAgglomerationRecipe>takes(level, TerrestrialAgglomerationRecipe.TYPE_ID, "terra_plate", stack,
                TerrestrialAgglomerationRecipe::getIngredients);
    }

    @Nullable
    @Override
    protected Job findJob(Level level) {
        return find(level, items);
    }

    /** The craft the inputs allow: the plate's recipe with the most ingredients that they make. */
    @Nullable
    public static Job find(@Nullable Level level, IItemHandler items) {
        if (level == null) return null;
        for (TerrestrialAgglomerationRecipe recipe : Units.<Container, TerrestrialAgglomerationRecipe>recipes(level,
                TerrestrialAgglomerationRecipe.TYPE_ID)) {
            Units.Pick pick = Units.pick(items, INPUT_START, OUTPUT_START, recipe.getIngredients());
            if (pick == null) continue;
            Container container = pick.container();
            if (!recipe.matches(container, level)) continue;
            ItemStack out = recipe.assemble(container, level.registryAccess());
            if (out.isEmpty()) continue;
            int[] taken = new int[SLOTS];
            System.arraycopy(pick.taken(), 0, taken, INPUT_START, INPUTS);
            return new Job(recipe.getId(), taken, List.of(out), recipe.getMana(), MachineConfig.PLATE.craftTicks.get(), pick.units());
        }
        return null;
    }

    @Override
    protected void onCrafted(Level level, BlockPos pos, Job job) {
        level.playSound(null, pos, BotaniaSounds.terrasteelCraft, SoundSource.BLOCKS, 0.8F, 1.0F);
    }

    @Override
    protected void onClientCrafted() {
        Level level = getLevel();
        if (level == null) return;
        BlockPos pos = getBlockPos();
        double x = pos.getX() + 0.5D, y = pos.getY() + 0.75D, z = pos.getZ() + 0.5D;
        for (int i = 0; i < 32; i++) {
            double a = i / 32.0D * Math.PI * 2.0D;
            float g = 0.75F + level.random.nextFloat() * 0.25F;
            level.addParticle(WispParticleData.wisp(0.3F, 0.25F, g, 0.4F, 0.8F), x, y, z, Math.cos(a) * 0.09D,
                    (level.random.nextDouble() - 0.3D) * 0.05D, Math.sin(a) * 0.09D);
        }
        for (int i = 0; i < 14; i++) {
            level.addParticle(SparkleParticleData.sparkle(1.8F, 0.4F, 1.0F, 0.5F, 8), x + (level.random.nextDouble() - 0.5D) * 1.0D,
                    y + level.random.nextDouble() * 0.8D, z + (level.random.nextDouble() - 0.5D) * 1.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** Its column of light reaches a few blocks up. */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.0D).expandTowards(0.0D, 2.5D, 0.0D);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alfheimheart.terra_plate");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new TerraPlateMenu(id, inventory, this, items, data);
    }
}
