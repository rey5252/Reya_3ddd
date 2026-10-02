package com.reya.alfheimheart.machine.farm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineConfig;
import com.reya.alfheimheart.machine.MachineLayout;
import com.reya.alfheimheart.machine.MachineLayouts;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.registries.ForgeRegistries;
import vazkii.botania.client.fx.WispParticleData;

/**
 * The Petal Farm: Botania's mystical flowers (and their tall kin) planted in its inputs aren't used up: every
 * cycle each kind gives petals of its colour, more for more flowers of it, for a little mana a petal; bone meal
 * in the slot under the bed doubles a cycle's petals.
 */
public class PetalFarmBlockEntity extends MachineBlockEntity {
    public static final MachineLayout LAYOUT = MachineLayouts.PETAL_FARM;
    /** Its slots (its layout's): the inputs, the outputs from OUTPUT_START, the special slots from SPECIAL_START. */
    public static final int INPUTS = LAYOUT.inputCount(), OUTPUTS = LAYOUT.outputCount(), OUTPUT_START = LAYOUT.outputStart(),
            SPECIAL_START = LAYOUT.specialStart();
    public static final int FERTILIZER = SPECIAL_START;
    public static final int SLOTS = SPECIAL_START + 1;
    private static final ResourceLocation JOB = new ResourceLocation(AlfheimHeart.MODID, "petal_farm");
    private static final String[] FLOWERS = {"_mystical_flower", "_double_flower"};

    public PetalFarmBlockEntity(BlockPos pos, BlockState state) {
        super(AlfheimHeart.PETAL_FARM_BE.get(), pos, state, LAYOUT);
    }

    @Override
    public int capacity() {
        return MachineConfig.FARM.capacity.get();
    }

    @Override
    protected int chargeRate() {
        return MachineConfig.FARM.chargeRate.get();
    }

    @Override
    protected int drawPerTick() {
        return MachineConfig.FARM.drawPerTick.get();
    }

    @Override
    protected boolean acceptsInput(ItemStack stack) {
        return !petalOf(stack).isEmpty();
    }

    @Override
    protected boolean acceptsSpecial(int index, ItemStack stack) {
        return stack.is(Items.BONE_MEAL);
    }

    /** The petal of a mystical flower's colour (Botania's {colour}_mystical_flower or {colour}_double_flower), or empty. */
    public static ItemStack petalOf(ItemStack flower) {
        if (flower.isEmpty()) return ItemStack.EMPTY;
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(flower.getItem());
        if (id == null || !"botania".equals(id.getNamespace())) return ItemStack.EMPTY;
        String path = id.getPath();
        for (String suffix : FLOWERS) {
            if (!path.endsWith(suffix)) continue;
            Item petal = ForgeRegistries.ITEMS.getValue(new ResourceLocation("botania", path.substring(0, path.length() - suffix.length()) + "_petal"));
            return petal == null || petal == Items.AIR ? ItemStack.EMPTY : new ItemStack(petal);
        }
        return ItemStack.EMPTY;
    }

    @Nullable
    @Override
    protected Job findJob(Level level) {
        return find(level, items);
    }

    /**
     * A cycle: each kind of flower in the inputs gives a petal of its colour, and one more for every few flowers
     * of it (up to a limit); bone meal doubles them all.
     */
    @Nullable
    public static Job find(@Nullable Level level, IItemHandler items) {
        if (level == null) return null;
        Map<Item, Integer> petals = new LinkedHashMap<>();
        List<ItemStack> units = new ArrayList<>();
        int per = MachineConfig.FARM_PER_FLOWERS.get(), most = MachineConfig.FARM_MOST.get();
        for (int i = 0; i < INPUTS; i++) {
            ItemStack stack = items.getStackInSlot(INPUT_START + i);
            ItemStack petal = petalOf(stack);
            if (petal.isEmpty()) continue;
            petals.merge(petal.getItem(), Math.min(most, 1 + stack.getCount() / per), (a, b) -> Math.min(most, a + b));
            units.add(stack.copyWithCount(1));
        }
        if (petals.isEmpty()) return null;
        boolean fed = items.getStackInSlot(FERTILIZER).is(Items.BONE_MEAL);
        List<ItemStack> outputs = new ArrayList<>();
        int total = 0;
        for (Map.Entry<Item, Integer> e : petals.entrySet()) {
            int n = e.getValue() * (fed ? 2 : 1);
            outputs.add(new ItemStack(e.getKey(), n));
            total += n;
        }
        int[] taken = new int[SLOTS];
        if (fed) taken[FERTILIZER] = 1;
        return new Job(JOB, taken, outputs, total * MachineConfig.FARM_MANA.get(), MachineConfig.FARM.craftTicks.get(), units);
    }

    @Override
    protected void onCrafted(Level level, BlockPos pos, Job job) {
        level.playSound(null, pos, SoundEvents.AZALEA_LEAVES_PLACE, SoundSource.BLOCKS, 0.6F, 1.3F);
    }

    @Override
    protected void onClientCrafted() {
        Level level = getLevel();
        if (level == null) return;
        BlockPos pos = getBlockPos();
        for (int i = 0; i < 12; i++) {
            float r = 0.5F + level.random.nextFloat() * 0.5F, g = 0.5F + level.random.nextFloat() * 0.5F, b = 0.5F + level.random.nextFloat() * 0.5F;
            level.addParticle(WispParticleData.wisp(0.15F, r, g, b, 0.8F), pos.getX() + 0.2D + level.random.nextDouble() * 0.6D,
                    pos.getY() + 0.8D, pos.getZ() + 0.2D + level.random.nextDouble() * 0.6D, 0.0D, 0.04D, 0.0D);
        }
    }

    /** Petals drift off the flowers while the farm works. */
    @Override
    protected void tickClient(Level level, BlockPos pos, BlockState state) {
        if (!clientWorking() || clientAge() % 12L != 0L) return;
        float r = 0.6F + level.random.nextFloat() * 0.4F, g = 0.5F + level.random.nextFloat() * 0.5F, b = 0.6F + level.random.nextFloat() * 0.4F;
        level.addParticle(WispParticleData.wisp(0.12F, r, g, b, 1.2F), pos.getX() + 0.2D + level.random.nextDouble() * 0.6D, pos.getY() + 0.9D,
                pos.getZ() + 0.2D + level.random.nextDouble() * 0.6D, (level.random.nextDouble() - 0.5D) * 0.01D, 0.012D,
                (level.random.nextDouble() - 0.5D) * 0.01D);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alfheimheart.petal_farm");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PetalFarmMenu(id, inventory, this, items, data);
    }
}
