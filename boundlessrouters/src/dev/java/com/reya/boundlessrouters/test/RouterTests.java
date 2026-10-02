package com.reya.boundlessrouters.test;

import java.util.List;
import java.util.function.Consumer;

import com.reya.boundlessrouters.BoundlessRouters;
import com.reya.boundlessrouters.module.ModuleKind;
import com.reya.boundlessrouters.module.ModuleSettings;
import com.reya.boundlessrouters.module.ModuleSettings.Setting;
import com.reya.boundlessrouters.module.Target;
import com.reya.boundlessrouters.router.RelativeDirection;
import com.reya.boundlessrouters.router.RouterBlock;
import com.reya.boundlessrouters.router.RouterBlockEntity;
import com.reya.boundlessrouters.upgrade.UpgradeKind;
import com.reya.boundlessrouters.util.Targets;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.items.IItemHandler;

/**
 * The routers at work in a real world, run by `gradlew runGameTestServer` (CI fails if any fails): each test
 * places a router in an empty room, puts modules, upgrades and items in, runs its modules once or lets it run,
 * and looks at where the items went.
 */
@GameTestHolder(BoundlessRouters.MODID)
@PrefixGameTestTemplate(false)
public class RouterTests {
    /** A room nine blocks square: its stone floor is at y = 1 (a test's y = 0 is the structure block's), so tests work at y = 2. */
    private static final String ROOM = "empty";

    // ------------------------------------------------------------------ helpers

    private static RouterBlockEntity router(GameTestHelper h, BlockPos at, Direction facing) {
        h.setBlock(at, BoundlessRouters.ROUTER.get().defaultBlockState().setValue(RouterBlock.FACING, facing));
        return (RouterBlockEntity) h.getBlockEntity(at);
    }

    private static void run(GameTestHelper h, RouterBlockEntity router) {
        router.runModules(h.getLevel(), router.getBlockPos());
    }

    private static ItemStack module(ModuleKind kind, Consumer<ModuleSettings> setup) {
        ItemStack stack = new ItemStack(BoundlessRouters.module(kind));
        setup.accept(new ModuleSettings(stack));
        return stack;
    }

    private static ItemStack upgrades(UpgradeKind kind, int count) {
        return new ItemStack(BoundlessRouters.upgrade(kind), count);
    }

    private static IItemHandler inventory(BlockEntity be) {
        return be.getCapability(ForgeCapabilities.ITEM_HANDLER, null).resolve().orElseThrow();
    }

    private static int count(IItemHandler inventory, Item item) {
        int n = 0;
        for (int i = 0; i < inventory.getSlots(); i++) {
            ItemStack stack = inventory.getStackInSlot(i);
            if (stack.is(item)) n += stack.getCount();
        }
        return n;
    }

    private static int count(GameTestHelper h, BlockPos at, Item item) {
        return count(inventory(h.getBlockEntity(at)), item);
    }

    private static int buffered(RouterBlockEntity router) {
        return router.buffer().getStackInSlot(0).getCount();
    }

    private static void expect(GameTestHelper h, int actual, int expected, String what) {
        h.assertTrue(actual == expected, what + ": expected " + expected + ", got " + actual);
    }

    // ------------------------------------------------------------------ moving items

    @GameTest(template = ROOM)
    public static void senderSendsIntoTheChestInFront(GameTestHelper h) {
        BlockPos at = new BlockPos(4, 2, 4), chest = at.south();
        RouterBlockEntity router = router(h, at, Direction.SOUTH);
        h.setBlock(chest, Blocks.CHEST);
        router.modules().setStackInSlot(0, module(ModuleKind.SENDER, s -> {
        }));
        router.buffer().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 5));
        run(h, router);
        expect(h, count(h, chest, Items.COBBLESTONE), 1, "a run without upgrades moves one item");
        expect(h, buffered(router), 4, "the buffer after one run");
        router.upgrades().setStackInSlot(0, upgrades(UpgradeKind.STACK, 2));
        run(h, router);
        expect(h, count(h, chest, Items.COBBLESTONE), 5, "two stack upgrades move four");
        h.succeed();
    }

    @GameTest(template = ROOM)
    public static void senderLooksAlongItsDirectionThroughBlocks(GameTestHelper h) {
        BlockPos at = new BlockPos(1, 2, 4), chest = new BlockPos(7, 2, 4);
        RouterBlockEntity router = router(h, at, Direction.EAST);
        for (int x = 2; x < 7; x++) h.setBlock(new BlockPos(x, 2, 4), Blocks.STONE);
        h.setBlock(chest, Blocks.CHEST);
        router.modules().setStackInSlot(0, module(ModuleKind.SENDER, s -> {
        }));
        router.buffer().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 3));
        run(h, router);
        expect(h, count(h, chest, Items.IRON_INGOT), 1, "the chest six blocks ahead, behind stone");
        h.succeed();
    }

    @GameTest(template = ROOM)
    public static void boundSenderReachesAnotherDimension(GameTestHelper h) {
        BlockPos at = new BlockPos(4, 2, 4);
        RouterBlockEntity router = router(h, at, Direction.NORTH);
        ServerLevel nether = h.getLevel().getServer().getLevel(Level.NETHER);
        h.assertTrue(nether != null, "the nether exists");
        BlockPos far = new BlockPos(12345, 64, -6789);
        nether.setBlockAndUpdate(far, Blocks.CHEST.defaultBlockState());
        router.modules().setStackInSlot(0, module(ModuleKind.SENDER, s -> s.toggleTarget(new Target(Level.NETHER, far, Direction.UP))));
        router.upgrades().setStackInSlot(0, upgrades(UpgradeKind.STACK, 2));
        router.upgrades().setStackInSlot(1, upgrades(UpgradeKind.RANGE_3, 1));
        router.buffer().setStackInSlot(0, new ItemStack(Items.DIAMOND, 3));
        run(h, router);
        IItemHandler there = Targets.inventoryAt(nether, far, Direction.UP);
        h.assertTrue(there != null, "the chest in the nether");
        expect(h, count(there, Items.DIAMOND), 0, "another dimension is out of reach without an infinite range upgrade");
        router.upgrades().setStackInSlot(1, upgrades(UpgradeKind.INFINITE_RANGE, 1));
        run(h, router);
        expect(h, count(there, Items.DIAMOND), 3, "diamonds sent to the nether");
        expect(h, buffered(router), 0, "the buffer after sending");
        for (int i = 0; i < there.getSlots(); i++) there.extractItem(i, 64, false);
        nether.setBlockAndUpdate(far, Blocks.AIR.defaultBlockState());
        h.succeed();
    }

    @GameTest(template = ROOM)
    public static void boundPlacesOutOfRangeWaitForARangeUpgrade(GameTestHelper h) {
        BlockPos at = new BlockPos(1, 2, 1), chest = new BlockPos(8, 5, 8);
        RouterBlockEntity router = router(h, at, Direction.SOUTH);
        h.setBlock(chest, Blocks.CHEST);
        router.modules().setStackInSlot(0, module(ModuleKind.SENDER, s -> s.toggleTarget(new Target(h.getLevel().dimension(), h.absolutePos(chest), Direction.UP))));
        router.buffer().setStackInSlot(0, new ItemStack(Items.IRON_INGOT, 4));
        run(h, router);
        expect(h, count(h, chest, Items.IRON_INGOT), 0, "a chest ten blocks off is out of the router's eight");
        router.upgrades().setStackInSlot(0, upgrades(UpgradeKind.RANGE, 1));
        run(h, router);
        expect(h, count(h, chest, Items.IRON_INGOT), 1, "within sixteen with a range upgrade I");
        h.succeed();
    }

    @GameTest(template = ROOM)
    public static void pullerPullsFromBehind(GameTestHelper h) {
        BlockPos at = new BlockPos(4, 2, 4), chest = at.north();
        RouterBlockEntity router = router(h, at, Direction.SOUTH);
        h.setBlock(chest, Blocks.CHEST);
        inventory(h.getBlockEntity(chest)).insertItem(0, new ItemStack(Items.IRON_INGOT, 10), false);
        router.modules().setStackInSlot(0, module(ModuleKind.PULLER, s -> s.apply(Setting.DIRECTION, RelativeDirection.BACK.ordinal())));
        router.upgrades().setStackInSlot(0, upgrades(UpgradeKind.STACK, 3));
        run(h, router);
        expect(h, buffered(router), 8, "eight pulled with three stack upgrades");
        expect(h, count(h, chest, Items.IRON_INGOT), 2, "left in the chest");
        h.succeed();
    }

    @GameTest(template = ROOM)
    public static void whitelistTakesOnlyWhatItLists(GameTestHelper h) {
        BlockPos at = new BlockPos(4, 2, 4), chest = at.south();
        RouterBlockEntity router = router(h, at, Direction.SOUTH);
        h.setBlock(chest, Blocks.CHEST);
        router.modules().setStackInSlot(0, module(ModuleKind.SENDER, s -> {
            s.apply(Setting.BLACKLIST, 0);
            s.setFilterItems(List.of(new ItemStack(Items.DIRT)));
        }));
        router.buffer().setStackInSlot(0, new ItemStack(Items.COBBLESTONE, 5));
        run(h, router);
        expect(h, count(h, chest, Items.COBBLESTONE), 0, "cobblestone isn't listed");
        router.buffer().setStackInSlot(0, new ItemStack(Items.DIRT, 5));
        run(h, router);
        expect(h, count(h, chest, Items.DIRT), 1, "dirt is");
        h.succeed();
    }

    @GameTest(template = ROOM)
    public static void terminationStopsTheModulesAfter(GameTestHelper h) {
        BlockPos at = new BlockPos(4, 2, 4), chest = at.south();
        RouterBlockEntity router = router(h, at, Direction.SOUTH);
        h.setBlock(chest, Blocks.CHEST);
        router.modules().setStackInSlot(0, module(ModuleKind.SENDER, s -> s.apply(Setting.TERMINATE, 1)));
        router.modules().setStackInSlot(1, module(ModuleKind.VOID, s -> {
        }));
        router.buffer().setStackInSlot(0, new ItemStack(Items.DIRT, 5));
        run(h, router);
        expect(h, count(h, chest, Items.DIRT), 1, "the sender sent");
        expect(h, buffered(router), 4, "the void didn't run after it");
        new ModuleSettings(router.modules().getStackInSlot(0)).apply(Setting.TERMINATE, 0);
        run(h, router);
        expect(h, buffered(router), 2, "without termination both ran");
        h.succeed();
    }

    @GameTest(template = ROOM)
    public static void distributorTakesTurns(GameTestHelper h) {
        BlockPos at = new BlockPos(4, 2, 4), a = new BlockPos(1, 2, 1), b = new BlockPos(7, 2, 7);
        RouterBlockEntity router = router(h, at, Direction.SOUTH);
        h.setBlock(a, Blocks.CHEST);
        h.setBlock(b, Blocks.CHEST);
        Level level = h.getLevel();
        router.modules().setStackInSlot(0, module(ModuleKind.DISTRIBUTOR, s -> {
            s.toggleTarget(new Target(level.dimension(), h.absolutePos(a), Direction.UP));
            s.toggleTarget(new Target(level.dimension(), h.absolutePos(b), Direction.UP));
        }));
        router.buffer().setStackInSlot(0, new ItemStack(Items.DIRT, 4));
        run(h, router);
        run(h, router);
        expect(h, count(h, a, Items.DIRT), 1, "the first target's turn");
        expect(h, count(h, b, Items.DIRT), 1, "the second target's turn");
        h.succeed();
    }

    @GameTest(template = ROOM)
    public static void voidDestroys(GameTestHelper h) {
        RouterBlockEntity router = router(h, new BlockPos(4, 2, 4), Direction.SOUTH);
        router.modules().setStackInSlot(0, module(ModuleKind.VOID, s -> {
        }));
        router.upgrades().setStackInSlot(0, upgrades(UpgradeKind.STACK, 2));
        router.buffer().setStackInSlot(0, new ItemStack(Items.ROTTEN_FLESH, 5));
        run(h, router);
        expect(h, buffered(router), 1, "four destroyed");
        h.succeed();
    }

    // ------------------------------------------------------------------ into the world

    @GameTest(template = ROOM)
    public static void dropperDropsInFront(GameTestHelper h) {
        BlockPos at = new BlockPos(4, 2, 4);
        RouterBlockEntity router = router(h, at, Direction.SOUTH);
        router.modules().setStackInSlot(0, module(ModuleKind.DROPPER, s -> {
        }));
        router.buffer().setStackInSlot(0, new ItemStack(Items.APPLE, 3));
        run(h, router);
        h.assertItemEntityPresent(Items.APPLE, at.south(), 1.5D);
        expect(h, buffered(router), 2, "one dropped");
        h.succeed();
    }

    @GameTest(template = ROOM)
    public static void placerPlacesAndBreakerBreaks(GameTestHelper h) {
        BlockPos at = new BlockPos(4, 2, 4), front = at.south();
        RouterBlockEntity router = router(h, at, Direction.SOUTH);
        router.modules().setStackInSlot(0, module(ModuleKind.PLACER, s -> {
        }));
        router.buffer().setStackInSlot(0, new ItemStack(Items.STONE, 2));
        run(h, router);
        h.assertBlockPresent(Blocks.STONE, front);
        expect(h, buffered(router), 1, "one placed");

        router.buffer().setStackInSlot(0, ItemStack.EMPTY);
        router.modules().setStackInSlot(0, module(ModuleKind.BREAKER, s -> {
        }));
        run(h, router);
        h.assertBlockPresent(Blocks.AIR, front);
        expect(h, count(inventory(router), Items.COBBLESTONE), 1, "stone breaks to cobblestone");

        router.buffer().setStackInSlot(0, ItemStack.EMPTY);
        h.setBlock(front, Blocks.STONE);
        router.modules().setStackInSlot(0, module(ModuleKind.BREAKER, s -> s.apply(Setting.SILK, 1)));
        run(h, router);
        expect(h, count(inventory(router), Items.STONE), 1, "with silk touch stone comes off whole");
        h.succeed();
    }

    @GameTest(template = ROOM)
    public static void vacuumPicksUpItemsAround(GameTestHelper h) {
        RouterBlockEntity router = router(h, new BlockPos(4, 2, 4), Direction.SOUTH);
        router.modules().setStackInSlot(0, module(ModuleKind.VACUUM, s -> {
        }));
        h.spawnItem(Items.GOLD_INGOT, 6.5F, 2.2F, 6.5F);
        h.spawnItem(Items.GOLD_INGOT, 2.5F, 2.2F, 1.5F);
        run(h, router);
        expect(h, count(inventory(router), Items.GOLD_INGOT), 2, "both picked up");
        h.succeed();
    }

    @GameTest(template = ROOM)
    public static void detectorSignalsWhileTheBufferMatches(GameTestHelper h) {
        BlockPos at = new BlockPos(4, 2, 4);
        RouterBlockEntity router = router(h, at, Direction.SOUTH);
        router.modules().setStackInSlot(0, module(ModuleKind.DETECTOR, s -> s.apply(Setting.POWER, 9)));
        router.buffer().setStackInSlot(0, new ItemStack(Items.DIRT));
        run(h, router);
        expect(h, router.signal(Direction.SOUTH, false), 9, "the detector's signal out of the front");
        expect(h, h.getLevel().getSignal(h.absolutePos(at), Direction.NORTH), 9, "the signal the block in front gets");
        router.buffer().setStackInSlot(0, ItemStack.EMPTY);
        run(h, router);
        expect(h, router.signal(Direction.SOUTH, false), 0, "no signal with the buffer empty");
        h.succeed();
    }

    @GameTest(template = ROOM)
    public static void detectorDoesNotPowerItsOwnRouter(GameTestHelper h) {
        BlockPos at = new BlockPos(4, 2, 4);
        RouterBlockEntity router = router(h, at, Direction.SOUTH);
        h.setBlock(at.south(), Blocks.REDSTONE_WIRE);
        router.modules().setStackInSlot(0, module(ModuleKind.DETECTOR, s -> {
        }));
        router.buffer().setStackInSlot(0, new ItemStack(Items.DIRT));
        run(h, router);
        expect(h, h.getLevel().getBlockState(h.absolutePos(at.south())).getValue(RedStoneWireBlock.POWER), 15, "the dust the detector powers");
        h.assertTrue(!router.powered(), "the router isn't powered by its own signal coming back");
        h.setBlock(at.east(), Blocks.REDSTONE_BLOCK);
        h.assertTrue(router.powered(), "a signal from elsewhere powers it");
        h.succeed();
    }

    @GameTest(template = ROOM)
    public static void extruderBuildsWithASignalAndTakesBackWithout(GameTestHelper h) {
        BlockPos at = new BlockPos(1, 2, 4);
        RouterBlockEntity router = router(h, at, Direction.EAST);
        router.modules().setStackInSlot(0, module(ModuleKind.EXTRUDER, s -> {
        }));
        router.buffer().setStackInSlot(0, new ItemStack(Items.STONE, 5));
        h.setBlock(at.above(), Blocks.REDSTONE_BLOCK);
        h.assertTrue(router.powered(), "the redstone block powers the router");
        run(h, router);
        run(h, router);
        run(h, router);
        for (int x = 2; x <= 4; x++) h.assertBlockPresent(Blocks.STONE, new BlockPos(x, 2, 4));
        expect(h, buffered(router), 2, "three put out");
        h.setBlock(at.above(), Blocks.AIR);
        h.assertTrue(!router.powered(), "no signal now");
        run(h, router);
        h.assertBlockPresent(Blocks.AIR, new BlockPos(4, 2, 4));
        expect(h, buffered(router), 3, "the last one taken back");
        h.succeed();
    }

    // ------------------------------------------------------------------ the router itself

    @GameTest(template = ROOM)
    public static void upgradesSpeedStackAndRange(GameTestHelper h) {
        RouterBlockEntity router = router(h, new BlockPos(4, 2, 4), Direction.SOUTH);
        expect(h, router.interval(), 20, "ticks between runs without upgrades");
        router.upgrades().setStackInSlot(0, upgrades(UpgradeKind.SPEED, 9));
        expect(h, router.interval(), 2, "nine speed upgrades");
        router.upgrades().setStackInSlot(0, upgrades(UpgradeKind.SPEED, 64));
        expect(h, router.interval(), 1, "a router can run every tick");
        expect(h, router.itemsPerRun(), 1, "items a run without upgrades");
        router.upgrades().setStackInSlot(1, upgrades(UpgradeKind.STACK, 6));
        expect(h, router.itemsPerRun(), 64, "a full stack");
        expect(h, router.range(), 8, "how far modules reach without a range upgrade");
        router.upgrades().setStackInSlot(2, upgrades(UpgradeKind.RANGE, 1));
        expect(h, router.range(), 16, "range upgrade I");
        router.upgrades().setStackInSlot(3, upgrades(UpgradeKind.RANGE_3, 1));
        expect(h, router.range(), 64, "range upgrades don't add up: III counts");
        router.upgrades().setStackInSlot(3, upgrades(UpgradeKind.RANGE_2, 1));
        expect(h, router.range(), 32, "II");
        router.upgrades().setStackInSlot(4, upgrades(UpgradeKind.INFINITE_RANGE, 1));
        expect(h, router.range(), RouterBlockEntity.INFINITE, "the infinite one");
        h.succeed();
    }

    @GameTest(template = ROOM, timeoutTicks = 120)
    public static void routerRunsOnItsOwnUnlessToldNever(GameTestHelper h) {
        BlockPos at = new BlockPos(2, 2, 4), chest = at.south();
        BlockPos at2 = new BlockPos(6, 2, 4), chest2 = at2.south();
        RouterBlockEntity running = router(h, at, Direction.SOUTH);
        RouterBlockEntity never = router(h, at2, Direction.SOUTH);
        h.setBlock(chest, Blocks.CHEST);
        h.setBlock(chest2, Blocks.CHEST);
        for (RouterBlockEntity router : new RouterBlockEntity[]{running, never}) {
            router.modules().setStackInSlot(0, module(ModuleKind.SENDER, s -> {
            }));
            router.buffer().setStackInSlot(0, new ItemStack(Items.DIRT, 10));
        }
        never.cycleRedstone(false);
        never.cycleRedstone(false);
        never.cycleRedstone(false);
        h.runAfterDelay(50, () -> {
            h.assertTrue(count(h, chest, Items.DIRT) >= 2, "the router ran by itself (twice in fifty ticks)");
            expect(h, count(h, chest2, Items.DIRT), 0, "a router set to never doesn't run");
            h.succeed();
        });
    }

    @GameTest(template = ROOM)
    public static void brokenRouterKeepsItsModules(GameTestHelper h) {
        BlockPos at = new BlockPos(4, 2, 4);
        RouterBlockEntity router = router(h, at, Direction.SOUTH);
        router.modules().setStackInSlot(0, module(ModuleKind.SENDER, s -> {
        }));
        router.upgrades().setStackInSlot(0, upgrades(UpgradeKind.SPEED, 3));
        router.buffer().setStackInSlot(0, new ItemStack(Items.DIRT, 7));
        h.getLevel().destroyBlock(h.absolutePos(at), true);
        List<ItemEntity> drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(h.absolutePos(at)).inflate(2.0D));
        ItemStack routerItem = drops.stream().map(ItemEntity::getItem).filter(s -> s.is(BoundlessRouters.ROUTER_ITEM.get())).findFirst()
                .orElse(ItemStack.EMPTY);
        h.assertTrue(!routerItem.isEmpty(), "the router dropped itself");
        CompoundTag data = routerItem.getTagElement("BlockEntityTag");
        h.assertTrue(data != null && data.contains("Modules") && data.contains("Upgrades"), "the router item keeps its modules and upgrades");
        h.assertTrue(drops.stream().anyMatch(e -> e.getItem().is(Items.DIRT) && e.getItem().getCount() == 7), "the buffer spilled");
        h.succeed();
    }
}
