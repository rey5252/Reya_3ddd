package com.reya.alfheimheart.machine.daisy;

import java.util.List;

import javax.annotation.Nullable;

import com.reya.alfheimheart.AlfheimHeart;
import com.reya.alfheimheart.machine.MachineBlockEntity;
import com.reya.alfheimheart.machine.MachineConfig;
import com.reya.alfheimheart.machine.Units;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import vazkii.botania.api.recipe.PureDaisyRecipe;
import vazkii.botania.client.fx.SparkleParticleData;
import vazkii.botania.client.fx.WispParticleData;

/**
 * The Pure Daisy: Botania's pure daisy with its eight blocks folded into one. Blocks in its inputs (stone, logs,
 * netherrack, snow, sand...) turn into what the daisy makes of them (livingrock, livingwood, cobblestone, ice...)
 * by every pure daisy recipe, eight at a time, in the daisy's own time. It needs no mana, but mana from the pools
 * beside it hurries it along: twice as fast while it has some.
 */
public class PureDaisyBlockEntity extends MachineBlockEntity {
    public static final int SLOTS = SPECIAL_START;
    private static final ItemStack NOTHING = ItemStack.EMPTY;

    public PureDaisyBlockEntity(BlockPos pos, BlockState state) {
        super(AlfheimHeart.PURE_DAISY_BE.get(), pos, state, 0);
    }

    @Override
    public int capacity() {
        return MachineConfig.DAISY_CAPACITY.get();
    }

    @Override
    protected int chargeRate() {
        return 1;
    }

    @Override
    protected int drawPerTick() {
        return MachineConfig.DAISY_DRAW.get();
    }

    /** Mana hurries the daisy: a tick more for every tick it has the mana for. */
    @Override
    protected int extraTicks() {
        int cost = MachineConfig.DAISY_BOOST.get();
        if (cost <= 0 || mana < cost) return 0;
        mana -= cost;
        setChanged();
        return 1;
    }

    /** Whether the daisy works faster now (it has the mana to). */
    public boolean hurried() {
        int cost = MachineConfig.DAISY_BOOST.get();
        return cost > 0 && mana >= cost;
    }

    @Override
    protected boolean acceptsInput(ItemStack stack) {
        return takesInput(level, stack);
    }

    public static boolean takesInput(@Nullable Level level, ItemStack stack) {
        return stack.getItem() instanceof BlockItem && Units.<PureDaisyRecipe>takes(level, PureDaisyRecipe.TYPE_ID, "pure_daisy", stack,
                r -> List.of(Ingredient.of(r.getInput().getDisplayedStacks().stream())));
    }

    /** What the daisy makes of a block's item (as the block it places), or empty. */
    public static ItemStack purified(Level level, ItemStack stack, @Nullable PureDaisyRecipe[] which) {
        if (!(stack.getItem() instanceof BlockItem block)) return NOTHING;
        BlockState state = block.getBlock().defaultBlockState();
        for (PureDaisyRecipe recipe : Units.<Container, PureDaisyRecipe>recipes(level, PureDaisyRecipe.TYPE_ID)) {
            if (!recipe.getInput().test(state)) continue;
            Item out = recipe.getOutputState().getBlock().asItem();
            if (out == Items.AIR) continue;
            if (which != null) which[0] = recipe;
            return new ItemStack(out);
        }
        return NOTHING;
    }

    @Nullable
    @Override
    protected Job findJob(Level level) {
        return find(level, items);
    }

    /** The craft the inputs allow: up to eight blocks of the first slot the daisy purifies, in its recipe's time. */
    @Nullable
    public static Job find(@Nullable Level level, IItemHandler items) {
        if (level == null) return null;
        for (int i = 0; i < INPUTS; i++) {
            ItemStack stack = items.getStackInSlot(INPUT_START + i);
            if (stack.isEmpty()) continue;
            PureDaisyRecipe[] which = new PureDaisyRecipe[1];
            ItemStack one = purified(level, stack, which);
            if (one.isEmpty()) continue;
            int n = Math.min(stack.getCount(), MachineConfig.DAISY_BATCH.get());
            int fits = Units.room(items, OUTPUT_START, SPECIAL_START, one);
            n = Math.max(1, Math.min(n, fits));
            int[] taken = new int[SLOTS];
            taken[INPUT_START + i] = n;
            return new Job(which[0].getId(), taken, List.of(one.copyWithCount(n)), 0, Math.max(1, which[0].getTime()),
                    List.of(stack.copyWithCount(n)));
        }
        return null;
    }

    @Override
    protected void onCrafted(Level level, BlockPos pos, Job job) {
        level.playSound(null, pos, SoundEvents.AMETHYST_BLOCK_CHIME, SoundSource.BLOCKS, 0.8F, 1.4F);
    }

    @Override
    protected void onClientCrafted() {
        Level level = getLevel();
        if (level == null) return;
        BlockPos pos = getBlockPos();
        double x = pos.getX() + 0.5D, y = pos.getY() + 0.9D, z = pos.getZ() + 0.5D;
        for (int i = 0; i < 16; i++) {
            double a = i / 16.0D * Math.PI * 2.0D;
            level.addParticle(WispParticleData.wisp(0.2F, 1.0F, 1.0F, 1.0F, 0.6F), x + Math.cos(a) * 0.4D, y, z + Math.sin(a) * 0.4D,
                    0.0D, 0.02D, 0.0D);
        }
        for (int i = 0; i < 6; i++) {
            level.addParticle(SparkleParticleData.sparkle(1.2F, 1.0F, 1.0F, 1.0F, 6), x + (level.random.nextDouble() - 0.5D) * 0.8D,
                    y + level.random.nextDouble() * 0.4D, z + (level.random.nextDouble() - 0.5D) * 0.8D, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alfheimheart.pure_daisy");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new PureDaisyMenu(id, inventory, this, items, data);
    }
}
