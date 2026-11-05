package rotp.addons.mandom.state;

import java.lang.reflect.Field;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.mandom.MandomAddon;
import rotp.core.api.time.LevelTimeRates;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.subsystems.timestop.TimeStopState;

/**
 * R772 MIH-FRAME and rules G1-G3: while the level's time runs m = 4 times faster the world goes back 6 / m seconds
 * and the Mandom user 6 seconds of his own time; a time stop begun after the world's point ends and the entity it
 * froze is put back and released; an older stop goes on and its frozen entity stays where it froze; the day time
 * stays where the acceleration has it. Entity positions encode the game time they were sampled at.
 */
@GameTestHolder(MandomAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MandomRewindRulesGameTests {
    private static final float WORLD_RATE = 4.0F;
    private static final double STEP = 0.02D;
    private static final int REWIND_AT = 140;
    private static volatile ServerLevel acceleratedLevel;
    private static boolean rateRegistered;
    private static int nextStopId = -7_720_000;

    private MandomRewindRulesGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_mandom",
            batch = "mandom_rewind_rules", timeoutTicks = 240)
    public static void duringAnAccelerationTheWorldGoesBackSixSecondsOverMTheUserSixOfHisOwnAndLaterStopsEnd(
            GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        if (level.getGameTime() % 20L != 0L) {
            helper.runAfterDelay(1, () ->
                    duringAnAccelerationTheWorldGoesBackSixSecondsOverMTheUserSixOfHisOwnAndLaterStopsEnd(helper));
            return;
        }
        registerRate();
        Scene scene = new Scene(helper, level.getGameTime());
        try {
            scene.setUp();
            scene.step();
        }
        catch (RuntimeException | Error exception) {
            scene.close();
            throw exception;
        }
    }

    private static synchronized void registerRate() {
        if (!rateRegistered) {
            LevelTimeRates.register(
                    MandomAddon.id("gametest_world_rate"),
                    (level, channel) -> channel == LevelTimeRates.Channel.TIME
                                    && level == acceleratedLevel
                            ? WORLD_RATE
                            : LevelTimeRates.NO_RATE);
            rateRegistered = true;
        }
    }

    private static final class Scene {
        private final GameTestHelper helper;
        private final ServerLevel level;
        private final long t0;
        private final TimeStopState state;
        private final int oldStop = nextStopId--;
        private final int newStop = nextStopId--;
        private final HeldChunks chunks;
        private FakePlayer user;
        private Cow world;
        private Cow heldByOld;
        private Cow frozenByNew;
        private Vec3 userBase;
        private Vec3 worldBase;
        private Vec3 oldBase;
        private Vec3 newBase;

        private Scene(GameTestHelper helper, long t0) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.t0 = t0;
            this.state = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
            this.chunks = new HeldChunks(level);
        }

        private void setUp() {
            MandomRewindService.clear(level.getServer());
            acceleratedLevel = level;
            userBase = helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D));
            user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "MandomRewindRules"));
            user.setNoGravity(true);
            user.setInvulnerable(true);
            user.setPos(userBase);
            level.addNewPlayer(user);
            PowerClass.STAND.attachGet(user).setStand(MandomAddon.MANDOM.get());
            helper.assertTrue(MandomAddon.isMandomUser(user), "setup: the user has no Mandom");
            worldBase = userBase.add(2.0D, 0.0D, 0.0D);
            oldBase = userBase.add(-32.0D, 0.0D, 0.0D);
            newBase = userBase.add(32.0D, 0.0D, 0.0D);
            world = cow(worldBase);
            heldByOld = cow(oldBase);
            frozenByNew = cow(newBase);
        }

        private Cow cow(Vec3 pos) {
            chunks.hold(pos);
            helper.assertTrue(level.hasChunkAt(BlockPos.containing(pos)),
                    "setup: the chunk at " + pos + " is not loaded");
            Cow cow = EntityType.COW.create(level);
            helper.assertTrue(cow != null, "setup: could not create a cow");
            cow.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            cow.setNoGravity(true);
            cow.setInvulnerable(true);
            cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0.0D);
            helper.assertTrue(level.addFreshEntity(cow), "setup: could not add a cow");
            return cow;
        }

        private void step() {
            long dt = level.getGameTime() - t0;
            user.setPos(userBase.add(0.0D, 0.0D, dt * STEP));
            world.setPos(worldBase.add(dt * STEP, 0.0D, 0.0D));
            if (dt == REWIND_AT - 20) {
                level.setDayTime(level.getDayTime() + 1000L);
            }
            if (dt >= REWIND_AT) {
                try {
                    rewindAndVerify(dt);
                }
                finally {
                    close();
                }
                return;
            }
            helper.runAfterDelay(1, () -> {
                try {
                    step();
                }
                catch (RuntimeException | Error exception) {
                    close();
                    throw exception;
                }
            });
        }

        private void rewindAndVerify(long dt) {
            helper.assertTrue(dt == REWIND_AT, "The scene missed its rewind tick: " + dt);
            // The world's point is 6 / 4 s = 30 ticks back. The old stop is a held one begun 60 ticks ago, before
            // the point, whose elapsed ticks are its opening's 35 short of that; the new one began 10 ticks ago.
            Vec3 heldAt = oldBase.add(0.0D, 0.0D, 2.0D);
            heldByOld.setPos(heldAt);
            frozenByNew.setPos(newBase.add(0.0D, 0.0D, 2.0D));
            putStop(oldStop, heldByOld, 60, 60 - TimeStopAbility.TIME_STOP_OPENING_SETTLE_TICKS, "mandom_rules_old");
            putStop(newStop, frozenByNew, 10, 10, "mandom_rules_new");
            helper.assertTrue(state.shouldFreeze(heldByOld) && state.shouldFreeze(frozenByNew)
                            && !state.shouldFreeze(user) && !state.shouldFreeze(world),
                    "setup: the stops do not freeze just their cows");
            long dayTime = level.getDayTime();

            MandomRewindService.Result result = MandomRewindService.rewind(level, user, null);
            helper.assertTrue(result == MandomRewindService.Result.SUCCESS, "The rewind failed: " + result);

            double userBack = dt - (user.getZ() - userBase.z) / STEP;
            helper.assertTrue(Math.abs(userBack - 120.0D) < 1.0E-3D,
                    "The user did not go back 6 s of his own time (120 ticks at rate 1): " + userBack);
            double worldBack = dt - (world.getX() - worldBase.x) / STEP;
            helper.assertTrue(worldBack >= 30.0D - 1.0E-3D && worldBack <= 35.0D + 1.0E-3D,
                    "The world did not go back 6 / m s (30 ticks, sampled every 5): " + worldBack);
            helper.assertTrue(!heldByOld.isRemoved() && heldByOld.position().distanceToSqr(heldAt) < 1.0E-8D
                            && state.shouldFreeze(heldByOld) && state.getInstance(oldStop).isPresent(),
                    "An older stop did not go on with its frozen cow where it froze: " + heldByOld.position()
                            + ", removed " + heldByOld.isRemoved() + ", stop kept "
                            + state.getInstance(oldStop).isPresent() + ", frozen " + state.shouldFreeze(heldByOld));
            helper.assertTrue(state.getInstance(newStop).isEmpty(),
                    "A stop begun after the rewind's point did not end");
            helper.assertTrue(frozenByNew.position().distanceToSqr(newBase) < 1.0E-8D
                            && !state.shouldFreeze(frozenByNew) && !frozenByNew.isNoAi(),
                    "The cow of the ended stop was not put back and released: " + frozenByNew.position()
                            + ", NoAI " + frozenByNew.isNoAi());
            helper.assertTrue(level.getDayTime() == dayTime,
                    "The rewind set the day time back during the acceleration: " + dayTime + " -> "
                            + level.getDayTime());
            helper.succeed();
        }

        // A stop starts with no elapsed ticks and a user-less one ends on its next tick, so each is put in place for
        // this tick's rewind with the start the core records at a commit and the elapsed ticks it would have by now.
        @SuppressWarnings("unchecked")
        private void putStop(int id, Cow cow, int begunTicksAgo, int ticksPassed, String route) {
            ChunkPos center = new ChunkPos(cow.blockPosition());
            helper.assertTrue(state.tryPutInstance(new TimeStopState.Instance(id, 400, 400, center, 1, -1, route)),
                    "setup: could not stop time");
            long started = level.getGameTime() - begunTicksAgo;
            try {
                Field instances = TimeStopState.class.getDeclaredField("instances");
                instances.setAccessible(true);
                ((Map<Integer, TimeStopState.Instance>) instances.get(state)).put(id, new TimeStopState.Instance(
                        id, 400, 400, center, 1, -1, route, Optional.empty(), Optional.empty(), -1, -1,
                        false, false, 0.0F, ticksPassed));
                Field startTimes = TimeStopState.class.getDeclaredField("timeStopStartTimes");
                startTimes.setAccessible(true);
                ((Map<Integer, Long>) startTimes.get(state)).put(id, started);
            }
            catch (ReflectiveOperationException exception) {
                throw new AssertionError("setup: could not set a stop's start and elapsed ticks", exception);
            }
            Optional<TimeStopState.Instance> put = state.getInstance(id);
            helper.assertTrue(put.map(TimeStopState.Instance::ticksPassed).orElse(-1) == ticksPassed
                            && put.map(state::getStartGameTime).orElse(Long.MIN_VALUE) == started,
                    "setup: the stop's start and elapsed ticks were not set");
        }

        private void close() {
            acceleratedLevel = null;
            state.removeInstance(oldStop);
            state.removeInstance(newStop);
            for (var entity : new net.minecraft.world.entity.Entity[] {user, world, heldByOld, frozenByNew}) {
                if (entity != null) {
                    entity.discard();
                }
            }
            MandomRewindService.clear(level.getServer());
            chunks.release();
        }
    }

    /**
     * The cows of the stops stand two chunks from the template's, which the framework does not hold and which may
     * still be loading when the test starts. The fixture holds each at the loaded level its own ticket gives, and the
     * game test server does not pace its ticks, so it waits in real time while the server thread runs chunk tasks.
     */
    private static final class HeldChunks {
        // Distance 0 is the full-chunk level 33: loaded, as the border of the template's chunk was, not ticking.
        private static final int TICKET_DISTANCE = 0;
        private static final TicketType<UUID> TICKET =
                TicketType.create("rotp_mandom_gametest", Comparator.<UUID>naturalOrder());
        private static final long WAIT_NANOS = 30_000_000_000L;
        private static final long PAUSE_NANOS = 1_000_000L;
        private final ServerLevel level;
        private final UUID id = UUID.randomUUID();
        private final Set<ChunkPos> held = new LinkedHashSet<>();

        private HeldChunks(ServerLevel level) {
            this.level = level;
        }

        private void hold(Vec3 position) {
            BlockPos block = BlockPos.containing(position);
            ChunkPos chunk = new ChunkPos(block);
            ServerChunkCache cache = level.getChunkSource();
            if (held.add(chunk)) {
                cache.addRegionTicket(TICKET, chunk, TICKET_DISTANCE, id);
                level.getChunk(chunk.x, chunk.z);
            }
            long start = System.nanoTime();
            while (!level.hasChunkAt(block)) {
                long waited = System.nanoTime() - start;
                if (waited > WAIT_NANOS) {
                    throw new GameTestAssertException("setup: the held chunk " + chunk + " is not loaded after "
                            + waited / 1_000_000L + " ms");
                }
                if (!cache.pollTask()) {
                    LockSupport.parkNanos(PAUSE_NANOS);
                }
            }
        }

        private void release() {
            for (ChunkPos chunk : held) {
                level.getChunkSource().removeRegionTicket(TICKET, chunk, TICKET_DISTANCE, id);
            }
            held.clear();
        }
    }
}
