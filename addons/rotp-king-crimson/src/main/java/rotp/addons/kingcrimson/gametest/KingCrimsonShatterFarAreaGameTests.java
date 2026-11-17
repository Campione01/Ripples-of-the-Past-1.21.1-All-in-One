package rotp.addons.kingcrimson.gametest;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.Area;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.Budget;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.Cell;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.Outcome;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.RingRead;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarData.RingSpec;

@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonShatterFarAreaGameTests {
    private KingCrimsonShatterFarAreaGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_far")
    public static void farAreaClipsWholeTilesWithoutChangingRingOwnership(GameTestHelper helper) {
        Area area = new Area(-32, -16, 16, 16);
        helper.assertTrue(area.contains(-32, -16) && area.contains(15, 15)
                        && !area.contains(-33, -16) && !area.contains(-32, -17)
                        && !area.contains(16, 15) && !area.contains(15, 16),
                "Area is half-open on each signed world axis");
        boolean rejected = false;
        try {
            new Area(-31, -16, 16, 16);
        } catch (IllegalArgumentException expected) {
            rejected = true;
        }
        helper.assertTrue(rejected, "An unaligned area could split an ownership tile");

        SolidSections fineSource = new SolidSections();
        RingRead fine = KingCrimsonShatterFarData.scan(new RingSpec(0, 16, 2), -8, -8, 0, 1,
                budget(1, 16), () -> false, fineSource, area);
        helper.assertTrue(fine.outcome() == Outcome.COMPLETE && fine.stats().columns() == 16
                        && fine.cells().size() == 16 && new HashSet<>(fine.cells()).size() == 16
                        && fine.cells().stream().allMatch(cell -> cell.x() >= -16 && cell.x() < 0
                                && cell.z() >= -16 && cell.z() < 0)
                        && fineSource.acquired == 1 && fineSource.released == 1,
                "The fine ring still owns all 16 columns in the central tile, once each");

        SolidSections coarseSource = new SolidSections();
        RingRead coarse = KingCrimsonShatterFarData.scan(new RingSpec(16, 64, 4), -8, -8, 0, 1,
                budget(4, 5), () -> false, coarseSource, area);
        Set<Cell> expected = Set.of(top(-32, -16), top(0, -16), top(-32, 0), top(-16, 0), top(0, 0));
        helper.assertTrue(coarse.outcome() == Outcome.COMPLETE && coarse.stats().columns() == 5
                        && coarse.cells().size() == 5 && new HashSet<>(coarse.cells()).equals(expected)
                        && coarseSource.acquired == 4 && coarseSource.released == 4,
                "The clipped coarse ring owns exactly the five remaining tiles across the section seams");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 40, batch = "kc_shatter_far")
    public static void farAreaClampsVisitedSectionsAtExclusiveBoundaries(GameTestHelper helper) {
        SolidSections reference = new SolidSections();
        RingRead oneTile = KingCrimsonShatterFarData.scan(new RingSpec(0, 1, 4), 488, -24, 0, 1,
                budget(1, 1), () -> false, reference);
        SolidSections clipped = new SolidSections();
        RingRead sector = KingCrimsonShatterFarData.scan(new RingSpec(0, 1024, 4), 504, -8, 0, 1,
                budget(1, 1), () -> false, clipped, new Area(496, -16, 512, 0));
        helper.assertTrue(oneTile.outcome() == Outcome.COMPLETE && sector.outcome() == Outcome.COMPLETE
                        && oneTile.cells().equals(List.of(top(480, -32)))
                        && sector.cells().equals(List.of(top(496, -16)))
                        && clipped.acquired == 1 && clipped.released == 1
                        && clipped.openChecks == reference.openChecks,
                "A one-tile sector visits only its section, even at both exclusive section boundaries");
        helper.succeed();
    }

    private static Budget budget(int sections, int columns) {
        return new Budget(sections, columns, System.nanoTime() + TimeUnit.SECONDS.toNanos(5));
    }

    private static Cell top(int x, int z) {
        return new Cell(x, z, 1.0F, 7, 9);
    }

    private static final class SolidSections implements KingCrimsonShatterFarData.Sections<Long> {
        private int openChecks, acquired, released;

        @Override
        public boolean isOpen() {
            openChecks++;
            return true;
        }

        @Override
        public Long acquireCached(int level, int x, int y, int z) {
            acquired++;
            return 7L << 27 | 9L << 47;
        }

        @Override
        public Long acquireIfExists(int level, int x, int y, int z) {
            throw new AssertionError("A cached section must not fall back to disk");
        }

        @Override
        public long[] rawOrNull(Long section) {
            return null;
        }

        @Override
        public long uniformValue(Long section) {
            return section;
        }

        @Override
        public void release(Long section) {
            released++;
        }
    }
}
