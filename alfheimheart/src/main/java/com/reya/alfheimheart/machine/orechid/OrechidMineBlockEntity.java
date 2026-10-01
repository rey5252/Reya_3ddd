package com.reya.alfheimheart.machine.orechid;

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
import com.reya.alfheimheart.machine.Units;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
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
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.items.IItemHandler;
import vazkii.botania.api.recipe.OrechidRecipe;
import vazkii.botania.client.fx.SparkleParticleData;

/**
 * The Orechid Mine: Botania's orechids in one block. Stone and deepslate in its inputs turn into ores by the
 * orechid's recipes, netherrack into nether ores by the orechid ignem's, and stone into metamorphic stones by the
 * marimorphosis' (whichever a block's kind is in), each pick weighed as the flowers weigh it, a few blocks at a
 * time, for the flowers' mana a block.
 */
public class OrechidMineBlockEntity extends MachineBlockEntity {
    public static final MachineLayout LAYOUT = MachineLayouts.ORECHID_MINE;
    /** Its slots (its layout's): the inputs, the outputs from OUTPUT_START, the special slots from SPECIAL_START. */
    public static final int INPUTS = LAYOUT.inputCount(), OUTPUTS = LAYOUT.outputCount(), OUTPUT_START = LAYOUT.outputStart(),
            SPECIAL_START = LAYOUT.specialStart();
    public static final int SLOTS = SPECIAL_START;
    /** The recipe types it works by, in the order it tries them. */
    public static final ResourceLocation[] TYPES = {OrechidRecipe.TYPE_ID, OrechidRecipe.IGNEM_TYPE_ID, OrechidRecipe.MARIMORPHOSIS_TYPE_ID};

    public OrechidMineBlockEntity(BlockPos pos, BlockState state) {
        super(AlfheimHeart.ORECHID_MINE_BE.get(), pos, state, LAYOUT);
    }

    @Override
    public int capacity() {
        return MachineConfig.MINE.capacity.get();
    }

    @Override
    protected int chargeRate() {
        return MachineConfig.MINE.chargeRate.get();
    }

    @Override
    protected int drawPerTick() {
        return MachineConfig.MINE.drawPerTick.get();
    }

    @Override
    protected boolean acceptsInput(ItemStack stack) {
        return takesInput(level, stack);
    }

    public static boolean takesInput(@Nullable Level level, ItemStack stack) {
        if (!(stack.getItem() instanceof BlockItem)) return false;
        for (ResourceLocation type : TYPES) {
            if (Units.<OrechidRecipe>takes(level, type, "orechid_mine/" + type.getPath(), stack,
                    r -> List.of(Ingredient.of(r.getInput().getDisplayedStacks().stream())))) {
                return true;
            }
        }
        return false;
    }

    /** The mana a block costs by a recipe type (the flowers' own). */
    public static int cost(ResourceLocation type) {
        ForgeConfigSpec.IntValue value = type.equals(OrechidRecipe.IGNEM_TYPE_ID) ? MachineConfig.MINE_IGNEM
                : type.equals(OrechidRecipe.MARIMORPHOSIS_TYPE_ID) ? MachineConfig.MINE_MARIMORPHOSIS : MachineConfig.MINE_ORECHID;
        return value.get();
    }

    /** What a block may become: the recipes of the first type that takes it (empty if none). */
    public static List<OrechidRecipe> candidates(Level level, ItemStack stack, @Nullable ResourceLocation[] typeOut) {
        if (!(stack.getItem() instanceof BlockItem block)) return List.of();
        BlockState state = block.getBlock().defaultBlockState();
        for (ResourceLocation type : TYPES) {
            List<OrechidRecipe> found = new ArrayList<>();
            for (OrechidRecipe recipe : Units.<Container, OrechidRecipe>recipes(level, type)) {
                if (recipe.getWeight() > 0 && recipe.getInput().test(state)) found.add(recipe);
            }
            if (!found.isEmpty()) {
                if (typeOut != null) typeOut[0] = type;
                return found;
            }
        }
        return List.of();
    }

    /** The possible results of a block, each with its chance in parts of the total weight: for the GUI. */
    public static Map<Item, Integer> chances(Level level, ItemStack stack) {
        Map<Item, Integer> out = new LinkedHashMap<>();
        for (OrechidRecipe recipe : candidates(level, stack, null)) {
            List<ItemStack> shown = recipe.getOutput().getDisplayedStacks();
            if (shown.isEmpty()) continue;
            out.merge(shown.get(0).getItem(), recipe.getWeight(), Integer::sum);
        }
        return out;
    }

    @Nullable
    @Override
    protected Job findJob(Level level) {
        return find(level, items, level.random);
    }

    /**
     * The craft the inputs allow: a few blocks of the first slot the flowers take, each turned into what a
     * weighed pick of their recipes makes (picked anew each time it is asked: only the last ask's picks are made).
     */
    @Nullable
    public static Job find(@Nullable Level level, IItemHandler items, RandomSource random) {
        if (level == null) return null;
        for (int i = 0; i < INPUTS; i++) {
            ItemStack stack = items.getStackInSlot(INPUT_START + i);
            if (stack.isEmpty()) continue;
            ResourceLocation[] type = new ResourceLocation[1];
            List<OrechidRecipe> recipes = candidates(level, stack, type);
            if (recipes.isEmpty()) continue;
            int total = 0;
            for (OrechidRecipe recipe : recipes) total += recipe.getWeight();
            int n = Math.min(stack.getCount(), MachineConfig.MINE_BATCH.get());
            Map<Item, Integer> picked = new LinkedHashMap<>();
            for (int k = 0; k < n; k++) {
                int roll = random.nextInt(Math.max(1, total));
                OrechidRecipe chosen = recipes.get(recipes.size() - 1);
                for (OrechidRecipe recipe : recipes) {
                    roll -= recipe.getWeight();
                    if (roll < 0) {
                        chosen = recipe;
                        break;
                    }
                }
                BlockState out = chosen.getOutput().pick(random);   // a tag of ores can be empty
                Item item = out == null ? Items.AIR : out.getBlock().asItem();
                if (item != Items.AIR) picked.merge(item, 1, Integer::sum);
            }
            if (picked.isEmpty()) continue;
            List<ItemStack> outputs = new ArrayList<>();
            picked.forEach((item, count) -> outputs.add(new ItemStack(item, count)));
            int[] taken = new int[SLOTS];
            taken[INPUT_START + i] = n;
            return new Job(new ResourceLocation(AlfheimHeart.MODID, "orechid_mine/" + type[0].getPath()), taken, outputs,
                    cost(type[0]) * n, MachineConfig.MINE.craftTicks.get(), List.of(stack.copyWithCount(n)));
        }
        return null;
    }

    @Override
    protected void onCrafted(Level level, BlockPos pos, Job job) {
        level.playSound(null, pos, SoundEvents.STONE_BREAK, SoundSource.BLOCKS, 0.7F, 0.9F);
        level.playSound(null, pos, SoundEvents.AMETHYST_CLUSTER_BREAK, SoundSource.BLOCKS, 0.5F, 1.3F);
    }

    @Override
    protected void onClientCrafted() {
        Level level = getLevel();
        if (level == null) return;
        BlockPos pos = getBlockPos();
        for (int i = 0; i < 10; i++) {
            level.addParticle(SparkleParticleData.sparkle(1.3F, 0.6F + level.random.nextFloat() * 0.4F, 1.0F, 0.6F, 6),
                    pos.getX() + 0.2D + level.random.nextDouble() * 0.6D, pos.getY() + 0.9D + level.random.nextDouble() * 0.4D,
                    pos.getZ() + 0.2D + level.random.nextDouble() * 0.6D, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("container.alfheimheart.orechid_mine");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new OrechidMineMenu(id, inventory, this, items, data);
    }
}
