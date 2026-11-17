package rotp.addons.d4c.travel;

import java.util.ArrayList;
import java.util.List;

import rotp.addons.d4c.D4CAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * R772.1: the crossing copy compares each section before it passes it block by block, and brings what a block
 * entity holds. Run on the End and its made parallel, the only pair here that generates alike.
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CMirrorCompareGameTests {
    private static final BlockPos FEET = new BlockPos(-60, 64, 0);

    private D4CMirrorCompareGameTests() {}

    @GameTest(template = "empty", batch = "d4cMirrorCompare", timeoutTicks = 400)
    public static void equalSectionsAreLeftAndTheCopyEqualsTheOrigin(GameTestHelper helper) {
        ServerLevel end = end(helper);
        ServerLevel copy = copy(helper);
        load(end, copy, FEET);
        D4CParallelTerrainMirror.copy(end, copy, FEET);
        D4CParallelTerrainMirror.copy(end, copy, FEET);
        D4CParallelTerrainMirror.CopyCount again = D4CParallelTerrainMirror.lastCopy();
        helper.assertTrue(
                again.blocks == 0 && again.blockEntities == 0
                        && again.equalSections > 0 && again.equalSections >= again.scannedSections,
                "A copy over an equal area: " + again);

        // three changed blocks in one section and a section filled anew
        List<BlockPos> changed = List.of(FEET.offset(3, 40, 5), FEET.offset(4, 40, 5), FEET.offset(-20, 41, 9));
        for (BlockPos pos : changed) {
            end.setBlock(pos, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        D4CParallelTerrainMirror.copy(end, copy, FEET);
        D4CParallelTerrainMirror.CopyCount after = D4CParallelTerrainMirror.lastCopy();
        try {
            helper.assertTrue(after.blocks == changed.size(),
                    "Changed blocks written " + after.blocks + " times, expected " + changed.size() + ": " + after);
            List<String> differ = differences(end, copy, FEET, D4CParallelMirrorRules.CHUNK_RADIUS);
            helper.assertTrue(differ.isEmpty(),
                    "After the copy the parallel differs from the End over the whole height at " + differ);
        }
        finally {
            for (BlockPos pos : changed) {
                end.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
            D4CParallelTerrainMirror.copy(end, copy, FEET);
        }
        helper.succeed();
    }

    // owner's decision of R772.1, as 1.16: a written block gets a fresh block entity, and what block entities hold
    // never crosses (copying it would duplicate what a player stores, again at every crossing)
    @GameTest(template = "empty", batch = "d4cMirrorChest", timeoutTicks = 400)
    public static void aChestsContentsStayOnTheirSide(GameTestHelper helper) {
        ServerLevel end = end(helper);
        ServerLevel copy = copy(helper);
        load(end, copy, FEET);
        BlockPos at = FEET.offset(5, 30, 5);
        BlockState chest = Blocks.CHEST.defaultBlockState();
        try {
            end.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
            copy.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
            D4CParallelTerrainMirror.copy(end, copy, FEET);
            end.setBlockAndUpdate(at, chest);
            ((ChestBlockEntity) end.getBlockEntity(at)).setItem(0, new ItemStack(Items.DIAMOND, 3));
            D4CParallelTerrainMirror.copy(end, copy, FEET);
            D4CParallelTerrainMirror.CopyCount made = D4CParallelTerrainMirror.lastCopy();
            helper.assertTrue(
                    copy.getBlockState(at) == chest
                            && copy.getBlockEntity(at) instanceof ChestBlockEntity parallel
                            && parallel.isEmpty()
                            && made.blocks == 1 && made.blockEntities == 1,
                    "The chest did not come over empty: " + copy.getBlockState(at) + ", " + made + ", holds "
                            + (copy.getBlockEntity(at) instanceof ChestBlockEntity held ? held.getItem(0) : null));

            // only the contents change on either side: the blocks stay equal and nothing is written
            ((ChestBlockEntity) end.getBlockEntity(at)).setItem(0, new ItemStack(Items.EMERALD, 5));
            ((ChestBlockEntity) copy.getBlockEntity(at)).setItem(1, new ItemStack(Items.STICK, 2));
            D4CParallelTerrainMirror.copy(end, copy, FEET);
            D4CParallelTerrainMirror.CopyCount count = D4CParallelTerrainMirror.lastCopy();
            ChestBlockEntity parallel = (ChestBlockEntity) copy.getBlockEntity(at);
            helper.assertTrue(
                    count.blocks == 0 && count.blockEntities == 0
                            && parallel.getItem(0).isEmpty()
                            && parallel.getItem(1).is(Items.STICK) && parallel.getItem(1).getCount() == 2,
                    "Changed contents in an unchanged section: " + count + ", the parallel chest holds "
                            + parallel.getItem(0) + " and " + parallel.getItem(1));
        }
        finally {
            end.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
            copy.setBlockAndUpdate(at, Blocks.AIR.defaultBlockState());
        }
        helper.succeed();
    }

    // a build past the crossing's budget: the arrival's 3x3 is copied at the crossing, the rest over the next ticks
    @GameTest(template = "empty", batch = "d4cMirrorQueue", timeoutTicks = 600)
    public static void aLargeBuildIsCopiedNearFirstAndFinishedOverTicks(GameTestHelper helper) {
        ServerLevel end = end(helper);
        ServerLevel copy = copy(helper);
        BlockPos feet = new BlockPos(300, 64, 300);
        load(end, copy, feet);
        BlockPos near = new BlockPos(272, 80, 272);
        BlockPos far = new BlockPos(360, 100, 360);
        // a run that failed may have left its build: both sides start clear,
        // what earlier crossings queued is finished, and the area made equal
        fill(end, near, Blocks.AIR.defaultBlockState());
        fill(copy, near, Blocks.AIR.defaultBlockState());
        end.setBlock(far, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        copy.setBlock(far, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        drain();
        D4CParallelTerrainMirror.copy(end, copy, feet);
        drain();
        fill(end, near, Blocks.OBSIDIAN.defaultBlockState());
        end.setBlock(far, Blocks.GOLD_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS);
        D4CParallelTerrainMirror.copy(end, copy, feet);
        D4CParallelTerrainMirror.CopyCount count = D4CParallelTerrainMirror.lastCopy();
        List<String> nearDiffer = differences(end, copy, feet, D4CParallelTerrainMirror.NEAR_RADIUS);
        helper.assertTrue(count.deferredChunks > 0 && nearDiffer.isEmpty(),
                "The crossing did not copy the arrival's 3x3 and queue the rest: " + count + ", near differs at "
                        + nearDiffer);
        helper.succeedWhen(() -> {
            helper.assertTrue(!D4CParallelTerrainMirror.pending(), "copies still queued");
            List<String> differ = differences(end, copy, feet, D4CParallelMirrorRules.CHUNK_RADIUS);
            helper.assertTrue(differ.isEmpty(), "after the queue the parallel differs at " + differ);
            fill(end, near, Blocks.AIR.defaultBlockState());
            fill(copy, near, Blocks.AIR.defaultBlockState());
            end.setBlock(far, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            copy.setBlock(far, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        });
    }

    private static void drain() {
        for (int round = 0; round < 1000 && D4CParallelTerrainMirror.pending(); round++) {
            D4CParallelTerrainMirror.tick();
        }
    }

    // 48 by 48 by 64 blocks over the 3x3 chunks from corner
    private static void fill(ServerLevel level, BlockPos corner, BlockState state) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 48; x++) {
            for (int z = 0; z < 48; z++) {
                for (int y = 0; y < 64; y++) {
                    level.setBlock(pos.set(corner.getX() + x, corner.getY() + y, corner.getZ() + z), state,
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    private static List<String> differences(ServerLevel end, ServerLevel copy, BlockPos feet, int radius) {
        List<String> differ = new ArrayList<>();
        ChunkPos center = new ChunkPos(feet);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
            for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
                LevelChunk from = end.getChunk(cx, cz);
                LevelChunk to = copy.getChunk(cx, cz);
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (int y = end.getMinBuildHeight(); y < end.getMaxBuildHeight(); y++) {
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            pos.set(from.getPos().getMinBlockX() + lx, y, from.getPos().getMinBlockZ() + lz);
                            BlockState state = from.getBlockState(pos);
                            BlockState wanted = D4CParallelMirrorRules.mirroredState(
                                    state, D4CParallelTerrainMirror.isPortal(state), air);
                            if (to.getBlockState(pos) != wanted && differ.size() < 10) {
                                differ.add(pos.toShortString());
                            }
                        }
                    }
                }
            }
        }
        return differ;
    }

    private static void load(ServerLevel end, ServerLevel copy, BlockPos feet) {
        ChunkPos center = new ChunkPos(feet);
        int radius = D4CParallelMirrorRules.CHUNK_RADIUS;
        for (int cx = center.x - radius; cx <= center.x + radius; cx++) {
            for (int cz = center.z - radius; cz <= center.z + radius; cz++) {
                end.getChunk(cx, cz);
                copy.getChunk(cx, cz);
            }
        }
    }

    private static ServerLevel end(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        helper.assertTrue(end != null, "No End here.");
        return end;
    }

    private static ServerLevel copy(GameTestHelper helper) {
        ServerLevel copy = helper.getLevel().getServer().getLevel(
                D4CDimensionTargets.levelKey(D4CParallelLevels.parallelIdOf(D4CDimensionTargets.END)));
        helper.assertTrue(copy != null, "No made parallel of the End here.");
        return copy;
    }
}
