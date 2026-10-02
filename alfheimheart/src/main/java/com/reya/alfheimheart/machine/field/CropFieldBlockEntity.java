package com.reya.alfheimheart.machine.field;

import java.util.ArrayList;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CactusBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SugarCaneBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.items.IItemHandler;
import vazkii.botania.client.fx.WispParticleData;

/**
 * The Crop Field: an Agricarnation's field in one block. Seeds and plants in its inputs (wheat, carrots, potatoes,
 * beetroots, melon and pumpkin seeds, nether wart, sugar cane, cactus, berries, cocoa beans, and the crops of
 * other mods) aren't used up: every cycle each is harvested as its grown plant would be, by the plant's own loot,
 * for a little mana a harvest; bone meal in the slot under the field doubles a cycle's harvest.
 */
public class CropFieldBlockEntity extends MachineBlockEntity {
    public static final MachineLayout LAYOUT = MachineLayouts.CROP_FIELD;
    /** Its slots (its layout's): the inputs, the outputs from OUTPUT_START, the special slots from SPECIAL_START. */
    public static final int INPUTS = LAYOUT.inputCount(), OUTPUTS = LAYOUT.outputCount(), OUTPUT_START = LAYOUT.outputStart(),
            SPECIAL_START = LAYOUT.specialStart();
    public static final int FERTILIZER = SPECIAL_START;
    public static final int SLOTS = SPECIAL_START + 1;
    private static final ResourceLocation JOB = new ResourceLocation(AlfheimHeart.MODID, "crop_field");

    public CropFieldBlockEntity(BlockPos pos, BlockState state) {
        super(AlfheimHeart.CROP_FIELD_BE.get(), pos, state, LAYOUT);
    }

    @Override
    public int capacity() {
        return MachineConfig.FIELD.capacity.get();
    }

    @Override
    protected int chargeRate() {
        return MachineConfig.FIELD.chargeRate.get();
    }

    @Override
    protected int drawPerTick() {
        return MachineConfig.FIELD.drawPerTick.get();
    }

    @Override
    protected boolean acceptsInput(ItemStack stack) {
        return grown(stack) != null;
    }

    @Override
    protected boolean acceptsSpecial(int index, ItemStack stack) {
        return stack.is(Items.BONE_MEAL);
    }

    /** The grown plant a seed or plant item grows into, or null if it isn't one the field grows. */
    @Nullable
    public static BlockState grown(ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem item)) return null;
        Block block = item.getBlock();
        if (block instanceof CropBlock crop) return crop.getStateForAge(crop.getMaxAge());
        if (block instanceof StemBlock stem) return stem.getFruit().defaultBlockState();
        if (block instanceof NetherWartBlock) return block.defaultBlockState().setValue(NetherWartBlock.AGE, NetherWartBlock.MAX_AGE);
        if (block instanceof SweetBerryBushBlock) return block.defaultBlockState().setValue(SweetBerryBushBlock.AGE, SweetBerryBushBlock.MAX_AGE);
        if (block instanceof CocoaBlock) return block.defaultBlockState().setValue(CocoaBlock.AGE, CocoaBlock.MAX_AGE);
        if (block instanceof SugarCaneBlock || block instanceof CactusBlock) return block.defaultBlockState();
        if (block instanceof BushBlock && !(block instanceof net.minecraft.world.level.block.SaplingBlock)) return block.defaultBlockState();
        return null;
    }

    /** What a grown plant gives when it is picked (its own loot, as broken by hand); on the client, the plant's item. */
    private static List<ItemStack> harvest(Level level, BlockPos pos, ItemStack seed, BlockState grown) {
        if (!(level instanceof ServerLevel server)) return List.of(seed.copyWithCount(1));
        LootParams.Builder params = new LootParams.Builder(server).withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .withParameter(LootContextParams.TOOL, ItemStack.EMPTY);
        return grown.getDrops(params);
    }

    @Nullable
    @Override
    protected Job findJob(Level level) {
        return find(level, getBlockPos(), items);
    }

    /**
     * A cycle: each seed or plant in the inputs is harvested as its grown plant, once and once more for every
     * few of it (up to a limit); bone meal doubles it all.
     */
    @Nullable
    public static Job find(@Nullable Level level, BlockPos pos, IItemHandler items) {
        if (level == null) return null;
        List<ItemStack> units = new ArrayList<>();
        List<ItemStack> outputs = new ArrayList<>();
        boolean fed = items.getStackInSlot(FERTILIZER).is(Items.BONE_MEAL);
        int per = MachineConfig.FIELD_PER_SEEDS.get(), most = MachineConfig.FIELD_MOST.get();
        int harvests = 0;
        for (int i = 0; i < INPUTS; i++) {
            ItemStack stack = items.getStackInSlot(INPUT_START + i);
            BlockState grown = stack.isEmpty() ? null : grown(stack);
            if (grown == null) continue;
            units.add(stack.copyWithCount(1));
            int times = Math.min(most, 1 + stack.getCount() / per) * (fed ? 2 : 1);
            for (int k = 0; k < times; k++) {
                for (ItemStack drop : harvest(level, pos, stack, grown)) merge(outputs, drop);
            }
            harvests += times;
        }
        if (units.isEmpty()) return null;
        int[] taken = new int[SLOTS];
        if (fed) taken[FERTILIZER] = 1;
        return new Job(JOB, taken, outputs, harvests * MachineConfig.FIELD_MANA.get(), MachineConfig.FIELD.craftTicks.get(), units);
    }

    private static void merge(List<ItemStack> into, ItemStack drop) {
        if (drop.isEmpty()) return;
        for (ItemStack s : into) {
            if (ItemStack.isSameItemSameTags(s, drop)) {
                s.grow(drop.getCount());
                return;
            }
        }
        into.add(drop.copy());
    }

    @Override
    protected void onCrafted(Level level, BlockPos pos, Job job) {
        level.playSound(null, pos, SoundEvents.CROP_BREAK, SoundSource.BLOCKS, 0.7F, 1.1F);
    }

    @Override
    protected void onClientCrafted() {
        Level level = getLevel();
        if (level == null) return;
        BlockPos pos = getBlockPos();
        for (int i = 0; i < 10; i++) {
            level.addParticle(WispParticleData.wisp(0.15F, 0.6F, 1.0F, 0.4F, 0.8F), pos.getX() + 0.2D + level.random.nextDouble() * 0.6D,
                    pos.getY() + 0.8D, pos.getZ() + 0.2D + level.random.nextDouble() * 0.6D, 0.0D, 0.04D, 0.0D);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alfheimheart.crop_field");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new CropFieldMenu(id, inventory, this, items, data);
    }
}
