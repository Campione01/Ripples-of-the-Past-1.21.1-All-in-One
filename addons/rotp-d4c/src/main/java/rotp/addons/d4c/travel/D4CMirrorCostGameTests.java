package rotp.addons.d4c.travel;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import rotp.addons.d4c.D4CAddon;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * R772.1 measurement, not a rule: the server-thread time of the crossing copy when both levels generate alike, so
 * the whole column height of the 9 by 9 chunks is compared (with the biome view, rotp_d4c:parallel_world and the
 * Overworld do). Run on the End and its made parallel at the main island, the only alike pair this server has.
 * Generation of both sides is done before the clock starts. One log line, read by the report.
 */
@GameTestHolder(D4CAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class D4CMirrorCostGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();

    private D4CMirrorCostGameTests() {}

    @GameTest(template = "empty", batch = "d4cMirrorCost", timeoutTicks = 400)
    public static void theFullHeightCopyIsTimed(GameTestHelper helper) {
        ServerLevel end = helper.getLevel().getServer().getLevel(Level.END);
        ServerLevel copy = helper.getLevel().getServer().getLevel(
                D4CDimensionTargets.levelKey(D4CParallelLevels.parallelIdOf(D4CDimensionTargets.END)));
        helper.assertTrue(end != null && copy != null, "No End pair here.");
        helper.assertTrue(D4CParallelTerrainMirror.generatesAlike(end, copy),
                "The End and its parallel do not generate alike: the full-height copy is not reached.");
        BlockPos feet = new BlockPos(40, 70, 40);
        ChunkPos center = new ChunkPos(feet);
        int radius = D4CParallelMirrorRules.CHUNK_RADIUS;
        long generateStart = System.nanoTime();
        int sections = 0;
        int full = 0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                LevelChunk from = end.getChunk(center.x + dx, center.z + dz);
                LevelChunk to = copy.getChunk(center.x + dx, center.z + dz);
                for (int index = 0; index < from.getSectionsCount(); index++) {
                    LevelChunkSection a = from.getSection(index);
                    LevelChunkSection b = to.getSection(index);
                    if (!(a.hasOnlyAir() && b.hasOnlyAir())) {
                        sections++;
                    }
                    if (a.getBlockState(0, 0, 0).isSolid() && a.getBlockState(15, 15, 15).isSolid()) {
                        full++;
                    }
                }
            }
        }
        long generated = System.nanoTime() - generateStart;
        long first = time(end, copy, feet);
        String firstCount = D4CParallelTerrainMirror.lastCopy().toString();
        long second = time(end, copy, feet);
        String secondCount = D4CParallelTerrainMirror.lastCopy().toString();
        double perSection = sections > 0 ? second / 1000.0D / sections : 0.0D;
        LOGGER.info("D4C mirror cost: End pair at {}, 81 chunks, {} of {} sections not empty on either side, "
                        + "{} solid-cornered; generation {} ms; first copy {} ms ({}), second copy {} ms ({}), "
                        + "{} us per non-empty section",
                feet, sections, 81 * end.getSectionsCount(), full, generated / 1_000_000L,
                first / 1_000_000.0D, firstCount, second / 1_000_000.0D, secondCount,
                String.format("%.2f", perSection));

        // a large build: 64 by 64 by 32 blocks, 16 chunks by 2 sections
        BlockPos corner = new BlockPos(feet.getX() - 32 & ~15, 96, feet.getZ() - 32 & ~15);
        fill(end, corner, Blocks.OBSIDIAN.defaultBlockState());
        long build = time(end, copy, feet);
        String buildCount = D4CParallelTerrainMirror.lastCopy().toString();
        fill(end, corner, Blocks.AIR.defaultBlockState());
        long cleared = time(end, copy, feet);
        String clearedCount = D4CParallelTerrainMirror.lastCopy().toString();
        LOGGER.info("D4C mirror cost, large build: copy {} ms ({}), clearing it {} ms ({})",
                build / 1_000_000.0D, buildCount, cleared / 1_000_000.0D, clearedCount);
        helper.succeed();
    }

    private static void fill(ServerLevel level, BlockPos corner, BlockState state) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = 0; x < 64; x++) {
            for (int z = 0; z < 64; z++) {
                for (int y = 0; y < 32; y++) {
                    level.setBlock(pos.set(corner.getX() + x, corner.getY() + y, corner.getZ() + z), state,
                            Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }

    private static long time(ServerLevel end, ServerLevel copy, BlockPos feet) {
        long start = System.nanoTime();
        D4CParallelTerrainMirror.copy(end, copy, feet);
        return System.nanoTime() - start;
    }
}
