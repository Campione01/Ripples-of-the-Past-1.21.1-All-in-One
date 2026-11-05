package rotp.addons.heavensdoor;

import java.util.Comparator;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Marker;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;

/**
 * A test's helper whose one-block template stands AWAY blocks west of its grid place (LANE more per lane), where no
 * chunk of the framework or of another test reaches, with its corner on the last block of a chunk in x and z. It
 * holds the corner's chunk at the level that makes it accessible but leaves its neighbours inaccessible: the state of
 * a grid place whose neighbouring chunks the framework has not promoted yet when the test starts. Whatever a fixture
 * puts a block or more east or south of the corner joins a hidden entity section there, unless the fixture holds
 * that chunk itself. Lanes 0-12 are the hd_chunk_border reproductions'.
 */
public final class HeavensDoorAwayHelper extends GameTestHelper implements GameTestListener {
    static final int AWAY = 512;
    static final int LANE = 256;
    private static final TicketType<UUID> TICKET =
            TicketType.create("rotp_hd_gametest_away", Comparator.<UUID>naturalOrder());
    private static final long WAIT_NANOS = 30_000_000_000L;
    private final BlockPos shift;
    private final ChunkPos corner;
    private final UUID id = UUID.randomUUID();
    private boolean held;

    public HeavensDoorAwayHelper(GameTestHelper helper, int lane) {
        super(helper.testInfo);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int x = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getX() - AWAY - LANE * lane)) + 15;
        int z = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(origin.getZ())) + 15;
        shift = new BlockPos(x - origin.getX(), 0, z - origin.getZ());
        corner = new ChunkPos(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        helper.testInfo.addListener(this);
        // Level 33: the corner's chunk is accessible, the chunks around it (34) are not.
        getLevel().getChunkSource().addRegionTicket(TICKET, corner, 0, id);
        held = true;
        getLevel().getChunk(corner.x, corner.z);
        assertTrue(visibleWithin(absoluteVec(new Vec3(0.5D, 2.0D, 0.5D)), WAIT_NANOS),
                "fixture: the template's chunk " + corner + " does not show its entities");
        assertTrue(!visibleWithin(absoluteVec(new Vec3(1.5D, 2.0D, 0.5D)), 0L)
                        && !visibleWithin(absoluteVec(new Vec3(0.5D, 2.0D, 1.5D)), 0L),
                "fixture: a chunk next to the template's chunk " + corner + " shows its entities");
    }

    private boolean visibleWithin(Vec3 position, long budgetNanos) {
        ServerLevel level = getLevel();
        Marker marker = EntityType.MARKER.create(level);
        marker.moveTo(position);
        level.addFreshEntity(marker);
        try {
            long start = System.nanoTime();
            while (level.getEntity(marker.getUUID()) == null) {
                if (System.nanoTime() - start > budgetNanos) {
                    return false;
                }
                if (!level.getChunkSource().pollTask()) {
                    LockSupport.parkNanos(1_000_000L);
                }
            }
            return true;
        }
        finally {
            marker.discard();
        }
    }

    @Override
    public BlockPos absolutePos(BlockPos pos) {
        return super.absolutePos(pos).offset(shift);
    }

    @Override
    public Vec3 absoluteVec(Vec3 pos) {
        return super.absoluteVec(pos).add(shift.getX(), shift.getY(), shift.getZ());
    }

    @Override
    public BlockPos relativePos(BlockPos pos) {
        return super.relativePos(pos.subtract(shift));
    }

    @Override
    public Vec3 relativeVec(Vec3 pos) {
        return super.relativeVec(pos.subtract(shift.getX(), shift.getY(), shift.getZ()));
    }

    private void release() {
        if (held) {
            held = false;
            getLevel().getChunkSource().removeRegionTicket(TICKET, corner, 0, id);
        }
    }

    @Override
    public void testStructureLoaded(GameTestInfo test) {}

    @Override
    public void testPassed(GameTestInfo test, GameTestRunner runner) {
        release();
    }

    @Override
    public void testFailed(GameTestInfo test, GameTestRunner runner) {
        release();
    }

    @Override
    public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
        release();
    }
}
