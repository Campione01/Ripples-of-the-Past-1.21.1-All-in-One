package rotp.addons.stickyfingers.state;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.LockSupport;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.stickyfingers.StickyFingersAddon;
import rotp.addons.stickyfingers.entity.StickyFingersExtendedPunchEntity;
import rotp.core.api.time.EntityTimeRates;

@GameTestHolder(StickyFingersAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StickyFingersExtendedPunchTimeGameTests {
    private static final String A_OWNER = "rotp_stfn.gametest.extended_punch_a_owner";
    private static final String B_OWNER = "rotp_stfn.gametest.extended_punch_b_owner";
    private static final String B_PUNCH = "rotp_stfn.gametest.extended_punch_b_entity";
    private static final AtomicBoolean RATE_REGISTERED = new AtomicBoolean();
    // the reach the punches get to, from their start
    private static final double REACH = 5.7D;
    private static final double BEFORE_BORDER = 3.0D;
    private static final double PATH_MARGIN = 3.0D;
    // a region ticket of radius 2 has level 31, the entity-ticking level of a forced chunk
    private static final int ENTITY_TICKING_RADIUS = 2;
    private static final long PATH_WAIT_NANOS = 30_000_000_000L;
    private static final TicketType<UUID> PATH_TICKET =
            TicketType.create("rotp_stfn_gametest_punch_path", Comparator.<UUID>naturalOrder());

    private StickyFingersExtendedPunchTimeGameTests() {}

    // The existing ability tests discard all nearby extended punches during cleanup.
    @GameTest(template = "empty", batch = "sticky_extended_punch_owner_time", timeoutTicks = 60)
    public static void extendedPunchUsesOwnerTimeWithoutDoubleEntityTicks(GameTestHelper helper) {
        if (RATE_REGISTERED.compareAndSet(false, true)) {
            EntityTimeRates.register(StickyFingersAddon.id("gametest_extended_punch_time"), (entity, channel) -> {
                if (channel == EntityTimeRates.Channel.STAND_ACTIONS && entity instanceof ArmorStand) {
                    if (entity.getTags().contains(A_OWNER)) {
                        return 0.5F;
                    }
                    if (entity.getTags().contains(B_OWNER)) {
                        return 2.0F;
                    }
                }
                if (channel == EntityTimeRates.Channel.ENTITY_TICK
                        && entity instanceof StickyFingersExtendedPunchEntity && entity.getTags().contains(B_PUNCH)) {
                    return 2.0F;
                }
                return EntityTimeRates.NO_RATE;
            });
        }

        Fixture fixture = new Fixture(helper);
        helper.testInfo.addListener(fixture);
        // The framework holds only the template's chunk at the entity-ticking level, and an entity stops ticking in a
        // chunk held below it: a punch put there, or reaching the next chunk on its way out, stood still and the counts
        // read short. The punches start BEFORE_BORDER blocks before the next chunk, so every run crosses a border, and
        // the chunks of their paths are held and awaited before they are added.
        int templateZ = helper.absolutePos(BlockPos.ZERO).getZ();
        double startZ = SectionPos.sectionToBlockCoord(SectionPos.blockToSectionCoord(templateZ) + 1) - BEFORE_BORDER;
        double x = helper.absoluteVec(new Vec3(1.5D, 0.0D, 0.0D)).x;
        fixture.holdPath(x, startZ);
        // Separate the three paths vertically so no punch can intercept another fixture.
        Punch normal = fixture.spawn(x, 8, startZ, null, null);
        Punch modeA = fixture.spawn(x, 12, startZ, A_OWNER, null);
        Punch modeB = fixture.spawn(x, 16, startZ, B_OWNER, B_PUNCH);

        helper.runAfterDelay(2, () -> {
            normal.check(helper, "rate 1", 0.6D, false);
            modeA.check(helper, "mode A", 0.3D, false);
            modeB.check(helper, "mode B", 1.2D, false);
            helper.assertTrue(modeB.entity().tickCount == 2 * normal.entity().tickCount,
                    "Mode B fixture did not receive extra entity ticks");
        });
        // The existing double-precision reach formula truncates to 19 outward steps, not 20.
        helper.runAfterDelay(10, () -> modeB.check(helper, "mode B full reach and hold", 5.7D, true));
        helper.runAfterDelay(11, () -> modeB.check(helper, "mode B retraction", 2.7D, true));
        helper.runAfterDelay(12, () -> helper.assertTrue(modeB.entity().isRemoved(), "Mode B punch outlived owner time"));
        helper.runAfterDelay(19, () -> normal.check(helper, "rate 1 full reach", 5.7D, false));
        helper.runAfterDelay(20, () -> normal.check(helper, "rate 1 hold before retraction", 5.7D, true));
        helper.runAfterDelay(21, () -> normal.check(helper, "rate 1 retraction", 4.2D, true));
        helper.runAfterDelay(24, () -> helper.assertTrue(normal.entity().isRemoved(), "Rate 1 punch outlived existing timing"));
        helper.runAfterDelay(38, () -> modeA.check(helper, "mode A full reach", 5.7D, false));
        helper.runAfterDelay(39, () -> modeA.check(helper, "mode A hold", 5.7D, false));
        helper.runAfterDelay(40, () -> modeA.check(helper, "mode A retraction begins after hold", 5.7D, true));
        helper.runAfterDelay(41, () -> modeA.check(helper, "mode A retraction step", 4.95D, true));
        helper.runAfterDelay(47, () -> modeA.check(helper, "mode A last retraction step", 0.45D, true));
        helper.runAfterDelay(48, () -> {
            helper.assertTrue(modeA.entity().isRemoved(), "Mode A punch outlived owner time");
            helper.succeed();
        });
    }

    private record Punch(StickyFingersExtendedPunchEntity entity, double originZ) {
        void check(GameTestHelper helper, String phase, double expectedDistance, boolean retracting) {
            helper.assertTrue(entity.isAlive(), phase + ": punch is gone");
            double distance = entity.getZ() - originZ;
            helper.assertTrue(Math.abs(distance - expectedDistance) < 1.0E-5D,
                    phase + ": expected extension " + expectedDistance + ", got " + distance);
            helper.assertTrue(entity.isRetracting() == retracting,
                    phase + ": expected retracting=" + retracting + ", got " + entity.isRetracting());
        }
    }

    private static final class Fixture implements GameTestListener {
        private final GameTestHelper helper;
        private final List<Entity> entities = new ArrayList<>();
        private final UUID ticketId = UUID.randomUUID();
        private final Set<ChunkPos> held = new LinkedHashSet<>();

        Fixture(GameTestHelper helper) {
            this.helper = helper;
        }

        // Every chunk within PATH_MARGIN of the punches' way out, at the entity-ticking level; the wait is real time on
        // the server thread, which runs the chunk tasks meanwhile, so no game tick passes before the punches are added.
        void holdPath(double x, double startZ) {
            ServerLevel level = helper.getLevel();
            for (int chunkX = SectionPos.blockToSectionCoord(Mth.floor(x - PATH_MARGIN));
                    chunkX <= SectionPos.blockToSectionCoord(Mth.floor(x + PATH_MARGIN)); chunkX++) {
                for (int chunkZ = SectionPos.blockToSectionCoord(Mth.floor(startZ - PATH_MARGIN));
                        chunkZ <= SectionPos.blockToSectionCoord(Mth.floor(startZ + REACH + PATH_MARGIN)); chunkZ++) {
                    ChunkPos chunk = new ChunkPos(chunkX, chunkZ);
                    if (held.add(chunk)) {
                        level.getChunkSource().addRegionTicket(PATH_TICKET, chunk, ENTITY_TICKING_RADIUS, ticketId);
                    }
                }
            }
            long start = System.nanoTime();
            List<ChunkPos> idle;
            while (!(idle = held.stream().filter(chunk -> !level.isPositionEntityTicking(chunk.getWorldPosition())).toList())
                    .isEmpty()) {
                if (System.nanoTime() - start > PATH_WAIT_NANOS) {
                    throw new GameTestAssertException("Fixture: the punches' chunks " + idle + " do not tick entities");
                }
                if (!level.getChunkSource().pollTask()) {
                    LockSupport.parkNanos(1_000_000L);
                }
            }
        }

        Punch spawn(double x, int y, double z, String ownerTag, String punchTag) {
            ServerLevel level = helper.getLevel();
            ArmorStand owner = EntityType.ARMOR_STAND.create(level);
            helper.assertTrue(owner != null, "Could not create the punch fixture owner");
            owner.setNoGravity(true);
            Vec3 pos = new Vec3(x, helper.absoluteVec(new Vec3(0.0D, y, 0.0D)).y, z);
            owner.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            if (ownerTag != null) {
                owner.addTag(ownerTag);
            }
            entities.add(owner);
            helper.assertTrue(level.addFreshEntity(owner), "Could not add the punch fixture owner");

            StickyFingersExtendedPunchEntity punch = new StickyFingersExtendedPunchEntity(owner, level);
            if (punchTag != null) {
                punch.addTag(punchTag);
            }
            entities.add(punch);
            helper.assertTrue(level.addFreshEntity(punch), "Could not add the extended punch");
            return new Punch(punch, punch.getZ());
        }

        private void close() {
            entities.forEach(Entity::discard);
            held.forEach(chunk -> helper.getLevel().getChunkSource()
                    .removeRegionTicket(PATH_TICKET, chunk, ENTITY_TICKING_RADIUS, ticketId));
            held.clear();
        }

        @Override
        public void testStructureLoaded(GameTestInfo test) {}

        @Override
        public void testPassed(GameTestInfo test, GameTestRunner runner) {
            close();
        }

        @Override
        public void testFailed(GameTestInfo test, GameTestRunner runner) {
            close();
        }

        @Override
        public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
            close();
        }
    }
}
