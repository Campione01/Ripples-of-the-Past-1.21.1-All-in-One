package rotp.addons.kingcrimson.gametest;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.Budget;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.Cell;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.Outcome;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.RingRead;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.RingSpec;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonShatterFarDataGameTests {
    private static final RingSpec ONE_TILE = new RingSpec(0, 1, 0);

    private KingCrimsonShatterFarDataGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_far")
    public static void farSectionsKeepSignedCoordinatesAndAxes(GameTestHelper helper) {
        helper.assertTrue(KingCrimsonShatterFarData.sectionOf(-1, 2) == -1
                        && KingCrimsonShatterFarData.sectionOf(-128, 2) == -1
                        && KingCrimsonShatterFarData.sectionOf(-129, 2) == -2
                        && KingCrimsonShatterFarData.sectionOf(128, 2) == 1,
                "Section coordinates must floor-divide, including below and west of zero");
        helper.assertTrue(KingCrimsonShatterFarData.indexOf(-1, -1, -1, 2) == 32767
                        && KingCrimsonShatterFarData.indexOf(-128, -128, -128, 2) == 0
                        && KingCrimsonShatterFarData.indexOf(-124, -120, -116, 2) == 2145,
                "Section index must be x + 32*z + 1024*y with signed world coordinates");
        helper.assertTrue(ONE_TILE.supportsHeight(-4096, 4096) && !ONE_TILE.supportsHeight(-4097, 4096)
                        && !ONE_TILE.supportsHeight(-4096, 4097),
                "Build height must not wrap the engine's signed section-Y key");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_far")
    public static void farScanDistinguishesAbsentAirAndSolidSections(GameTestHelper helper) {
        FakeSections source = new FakeSections();
        source.put(0, 0, 1, 0, null, 15L << 56, true);
        source.put(0, 0, -1, 0, null, packed(7, 9), false);
        RingRead read = scan(ONE_TILE, 8, -33, 35, budget(8, 256), () -> false, source);
        helper.assertTrue(read.outcome() == Outcome.COMPLETE && read.stats().asked() == 3
                        && read.stats().missing() == 1 && read.stats().empty() == 1 && read.stats().cacheHits() == 1,
                "Air with sky light is empty but not absent, and scanning stops after all columns have a top");
        helper.assertTrue(read.cells().size() == 256 && read.stats().nonAirCells() == 256
                        && read.cells().stream().allMatch(cell -> cell.topY() == 0.0F && cell.blockId() == 7 && cell.biomeId() == 9),
                "Uniform solid must fill the unresolved columns at the top of the actual section");
        helper.assertTrue(source.acquired == 2 && source.released == 2 && source.diskReads == 2,
                "One reference is released per present section, including uniform air");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_far")
    public static void farScanRetainsCarriersAndFractionalHeights(GameTestHelper helper) {
        FakeSections source = new FakeSections();
        source.clearOnRelease = true;
        long[] upper = new long[32768];
        upper[KingCrimsonShatterFarData.indexOf(-16, 8, -16, 2)] = packed(25, 1);
        upper[KingCrimsonShatterFarData.indexOf(-16, 4, -16, 2)] = packed(0, 321) | 1L << 24 | 4L << 20 | 19L;
        upper[KingCrimsonShatterFarData.indexOf(-16, 0, -16, 2)] = packed(2, 4);
        upper[KingCrimsonShatterFarData.indexOf(-12, 0, -16, 2)] = packed(3, 5) | 1L << 24 | 6L << 20;
        upper[KingCrimsonShatterFarData.indexOf(-8, 4, -16, 2)] = packed(9, 6);
        long[] lower = new long[32768];
        lower[KingCrimsonShatterFarData.indexOf(-4, -4, -16, 2)] = packed(11, 2) | 1L << 24 | 3L << 20;
        source.put(2, -1, 0, -1, upper, 0L, false);
        source.put(2, -1, -1, -1, lower, 0L, false);
        RingRead read = scan(new RingSpec(0, 1, 2), -8, -3, 7, budget(3, 16), () -> false, source);
        helper.assertTrue(read.outcome() == Outcome.COMPLETE && read.cells().size() == 3
                        && read.stats().columns() == 16 && source.released == 2,
                "Scan only its negative-coordinate tile and clip to dimension build heights");
        helper.assertTrue(read.cells().contains(new Cell(-16, -16, 5.0F, 19, 321)),
                "An air-coded carrier must win over the lower solid and retain carried block, biome and fill");
        helper.assertTrue(read.cells().contains(new Cell(-12, -16, 1.5F, 3, 5))
                        && read.cells().contains(new Cell(-8, -16, 7.0F, 9, 6)),
                "Partial fill stays fractional and a top cut by maxY is clamped, even after source reuse");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_far")
    public static void farScanReleasesSectionsOnFailureAndCancellation(GameTestHelper helper) {
        FakeSections failed = new FakeSections();
        failed.put(0, 0, 0, 0, new long[1], 0L, true);
        RingRead failure = scan(ONE_TILE, 8, 0, 1, budget(2, 256), () -> false, failed);
        helper.assertTrue(failure.outcome() == Outcome.FAILED && failure.failure() != null && failure.cells().isEmpty()
                        && failed.acquired == 1 && failed.released == 1,
                "A section failing inside the scan must still be released exactly once");

        FakeSections cancelled = new FakeSections();
        cancelled.cancelOnAcquire = new AtomicBoolean();
        cancelled.put(0, 0, 0, 0, null, packed(9, 1), false);
        RingRead stopped = scan(ONE_TILE, 8, 0, 1, budget(2, 256), cancelled.cancelOnAcquire::get, cancelled);
        helper.assertTrue(stopped.outcome() == Outcome.CANCELLED && stopped.cells().isEmpty()
                        && cancelled.acquired == 1 && cancelled.released == 1 && cancelled.rawReads == 0,
                "A cancellation after a disk acquire must release the section without scanning it");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_far")
    public static void farRingsOwnWholeTilesAndDropIncompleteGeometry(GameTestHelper helper) {
        RingSpec fine = new RingSpec(0, 16, 0);
        RingSpec coarse = new RingSpec(16, 32, 4);
        for (int x = -32; x < 16; x++) {
            boolean inside = x >= -16 && x < 0;
            helper.assertTrue(fine.owns(-8, -8, x, -8) == inside && coarse.owns(-8, -8, x, -8) != inside,
                    "A tile exactly on the ring seam belongs wholly to one level: x=" + x);
        }
        helper.assertFalse(coarse.owns(-8, -8, 16, -8), "The outer boundary is exclusive");

        FakeSections source = new FakeSections();
        long[] partlySolid = new long[32768];
        partlySolid[KingCrimsonShatterFarData.indexOf(0, 63, 0, 0)] = packed(3, 1);
        source.put(0, 0, 1, 0, partlySolid, 0L, true);
        RingRead limited = scan(ONE_TILE, 8, 0, 64, budget(1, 256), () -> false, source);
        helper.assertTrue(limited.outcome() == Outcome.SECTION_LIMIT && limited.stats().asked() == 1
                        && limited.stats().nonAirCells() == 1 && limited.cells().isEmpty() && source.released == 1,
                "Stop after the last allowed request and keep stats, not incomplete geometry");

        FakeSections unused = new FakeSections();
        RingRead cellLimited = scan(ONE_TILE, 8, 0, 1, budget(2, 255), () -> false, unused);
        RingRead expired = scan(ONE_TILE, 8, 0, 1, new Budget(2, 256, System.nanoTime()), () -> false, unused);
        helper.assertTrue(cellLimited.outcome() == Outcome.CELL_LIMIT && cellLimited.stats().asked() == 0
                        && expired.outcome() == Outcome.DEADLINE && expired.stats().asked() == 0 && unused.diskReads == 0,
                "An inadmissible tile or expired read cannot start a section request");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_far")
    public static void farReaderRechecksEngineBeforeDiskFallback(GameTestHelper helper) {
        FakeSections source = new FakeSections();
        source.closeOnCacheMiss = true;
        RingRead read = scan(ONE_TILE, 8, 0, 1, budget(2, 256), () -> false, source);
        helper.assertTrue(read.outcome() == Outcome.ENGINE_STOPPED && read.stats().asked() == 1
                        && read.stats().missing() == 0 && read.cells().isEmpty() && source.diskReads == 0,
                "A cache miss is not a missing section when shutdown prevents its disk fallback");

        FakeSections closed = new FakeSections();
        closed.open = false;
        RingRead expired = scan(ONE_TILE, 8, 0, 1, new Budget(2, 256, System.nanoTime() - 1L), () -> false, closed);
        helper.assertTrue(expired.outcome() == Outcome.ENGINE_STOPPED && expired.cells().isEmpty() && closed.diskReads == 0,
                "Engine shutdown must outrank an expired deadline and discard completed rings");

        FakeSections delayed = new FakeSections();
        AtomicBoolean cancelled = new AtomicBoolean();
        delayed.beforeAcquire = () -> cancelled.set(true);
        RingRead afterCancel = scan(ONE_TILE, 8, 0, 1, budget(2, 256), cancelled::get, delayed);
        helper.assertTrue(afterCancel.outcome() == Outcome.CANCELLED && afterCancel.cells().isEmpty()
                        && afterCancel.stats().asked() == 0 && delayed.cacheReads == 0 && delayed.diskReads == 0,
                "Cancellation during the pre-acquire delay must prevent section acquisition");

        FakeSections stopped = new FakeSections();
        stopped.beforeAcquire = () -> stopped.open = false;
        RingRead afterStop = scan(ONE_TILE, 8, 0, 1, budget(2, 256), () -> false, stopped);
        helper.assertTrue(afterStop.outcome() == Outcome.ENGINE_STOPPED && afterStop.cells().isEmpty()
                        && afterStop.stats().asked() == 0 && stopped.cacheReads == 0 && stopped.diskReads == 0,
                "Engine shutdown during the pre-acquire delay must prevent section acquisition");
        helper.succeed();
    }

    private static Budget budget(int sections, int columns) {
        return new Budget(sections, columns, System.nanoTime() + TimeUnit.SECONDS.toNanos(5));
    }

    private static RingRead scan(RingSpec spec, int origin, int minY, int maxY, Budget budget,
            BooleanSupplier cancelled, FakeSections source) {
        return KingCrimsonShatterFarData.scan(spec, origin, origin, minY, maxY, budget, cancelled, source);
    }

    private static long packed(int block, int biome) {
        return (long) block << 27 | (long) biome << 47;
    }

    private record Address(int level, int x, int y, int z) {}

    private record FakeSection(long[] raw, long uniform, boolean cached) {}

    private static final class FakeSections implements KingCrimsonShatterFarData.Sections<FakeSection> {
        private final Map<Address, FakeSection> sections = new HashMap<>();
        private boolean open = true;
        private boolean closeOnCacheMiss;
        private boolean clearOnRelease;
        private AtomicBoolean cancelOnAcquire;
        private Runnable beforeAcquire = () -> {};
        private int acquired, released, rawReads, cacheReads, diskReads;

        private void put(int level, int x, int y, int z, long[] raw, long uniform, boolean cached) {
            sections.put(new Address(level, x, y, z), new FakeSection(raw, uniform, cached));
        }

        @Override
        public void beforeAcquire() {
            beforeAcquire.run();
        }

        @Override
        public boolean isOpen() {
            return open;
        }

        @Override
        public FakeSection acquireCached(int level, int x, int y, int z) {
            cacheReads++;
            FakeSection section = sections.get(new Address(level, x, y, z));
            if (section != null && section.cached()) return acquire(section);
            if (closeOnCacheMiss) open = false;
            return null;
        }

        @Override
        public FakeSection acquireIfExists(int level, int x, int y, int z) {
            diskReads++;
            return acquire(sections.get(new Address(level, x, y, z)));
        }

        private FakeSection acquire(FakeSection section) {
            if (section != null) {
                acquired++;
                if (cancelOnAcquire != null) cancelOnAcquire.set(true);
            }
            return section;
        }

        @Override
        public long[] rawOrNull(FakeSection section) {
            rawReads++;
            return section.raw();
        }

        @Override
        public long uniformValue(FakeSection section) {
            return section.uniform();
        }

        @Override
        public void release(FakeSection section) {
            released++;
            if (clearOnRelease && section.raw() != null) Arrays.fill(section.raw(), 0L);
        }
    }
}
