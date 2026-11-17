package rotp.addons.ger.gametest;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.GoldExperienceRequiemGameEvents;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.addons.ger.state.GerRtzRewindService;

/**
 * Return to Zero's history must not keep a stopped server alive, must refuse a stopped server, and must capture
 * block changes only inside a mark. The first three use a bare server and level (no constructor run), which nothing
 * but the history can reach.
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerRtzHistoryLifetimeGameTests {
    private static final int MARK_CHUNK_RADIUS = 8;

    private GerRtzHistoryLifetimeGameTests() {}

    @GameTest(template = "empty", timeoutTicks = 20, batch = "ger_rtz_history_stopped_collectable")
    public static void aStoppedServerAndItsLevelAreCollectableAfterTheStopEvenAfterALateWrite(
            GameTestHelper helper) {
        WeakReference<?>[] refs = stopWithLateWrite(helper);
        awaitCollected(refs[0], "The stopped server is still reachable");
        awaitCollected(refs[1], "The stopped server's level is still reachable");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, batch = "ger_rtz_history_never_holds_key")
    public static void aHistoryWithAMarkedBlockDoesNotKeepItsServerOrLevelAlive(GameTestHelper helper) {
        WeakReference<?>[] refs = markOneBlock(helper);
        awaitCollected(refs[0], "A server with a marked block is kept alive by the Return to Zero histories");
        awaitCollected(refs[1], "A level with a marked block is kept alive by the Return to Zero histories");
        helper.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 20, batch = "ger_rtz_history_stopped_refused")
    public static void theStoppedEventClearsAServerAndAStoppedServerGetsNoHistory(GameTestHelper helper) {
        Bare bare = Bare.of(helper);
        UUID owner = UUID.randomUUID();
        GerRtzRewindService.markForTests(bare.level(), owner, BlockPos.ZERO);
        GerRtzRewindService.recordBlockMutation(bare.level(), preimage());
        helper.assertTrue(GerRtzRewindService.hasHistoryForTests(bare.server())
                        && GerRtzRewindService.markedBlocksForTests(bare.server(), owner) == 1,
                "setup: the running server's marked block was not recorded");
        bare.stop();
        GoldExperienceRequiemGameEvents.onServerStopped(new ServerStoppedEvent(bare.server()));
        helper.assertTrue(!GerRtzRewindService.hasHistoryForTests(bare.server()),
                "ServerStoppedEvent did not clear the stopped server's history");
        GerRtzRewindService.markForTests(bare.level(), owner, BlockPos.ZERO);
        helper.assertTrue(!GerRtzRewindService.hasHistoryForTests(bare.server()),
                "A stopped server got a new history");
        GerRtzRewindService.recordBlockMutation(bare.level(), preimage());
        helper.assertTrue(GerRtzRewindService.markedBlocksForTests(bare.server(), owner) == -1,
                "A late block write was recorded for a stopped server");
        helper.succeed();
    }

    /**
     * No mark anywhere: neither a set block (its neighbour updates) nor a broken one is captured. After the mark, a
     * block broken inside it is, one outside its chunks is not, and Return to Zero puts the block back.
     */
    @GameTest(template = "empty", timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100,
            batch = "ger_rtz_history_mark_gate")
    public static void blocksAreCapturedOnlyInsideAMarkAndReturnToZeroPutsThemBack(GameTestHelper helper) {
        new Sequence(helper, "rtz/history-gate", 60) {
            private Actor owner;
            private BlockPos inside;
            private int stage;

            @Override
            boolean step() {
                ServerLevel level = scene.level;
                if (stage == 0) {
                    // The batch runs alone: earlier batches' marks would otherwise cover this place.
                    GerRtzRewindService.clear(level.getServer());
                    owner = scene.actor();
                    inside = BlockPos.containing(scene.origin.add(3, 1, 0));
                    long before = GerRtzRewindService.blockCapturesForTests();
                    level.setBlock(inside, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
                    NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(
                            level, inside, level.getBlockState(inside), owner.user()));
                    helper.assertTrue(GerRtzRewindService.blockCapturesForTests() == before
                                    && !GerRtzRewindService.journals(level, inside),
                            scene.id + ": block changes were captured with no mark anywhere");
                    scene.premise(GerRtzRewindService.setMark(level, owner.user(), MARK_CHUNK_RADIUS)
                                    == GerRtzRewindService.Result.SUCCESS,
                            "the mark is set");
                    BlockPos outside = inside.offset((MARK_CHUNK_RADIUS + 2) * 16, 0, 0);
                    helper.assertTrue(GerRtzRewindService.journals(level, inside)
                                    && !GerRtzRewindService.journals(level, outside),
                            scene.id + ": the journal does not follow the mark's chunks");
                    NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(
                            level, inside, level.getBlockState(inside), owner.user()));
                    level.setBlock(inside, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    helper.assertTrue(GerRtzRewindService.blockCapturesForTests() > before
                                    && GerRtzRewindService.markedBlocksForTests(
                                            level.getServer(), owner.user().getUUID()) > 0,
                            scene.id + ": a block broken inside the mark was not recorded");
                    stage = 1;
                    return false;
                }
                helper.assertTrue(GerRtzRewindService.rewind(level, owner.user(), owner.stand(), owner.power(), 0)
                                == GerRtzRewindService.Result.SUCCESS,
                        scene.id + ": Return to Zero did not run");
                helper.assertTrue(level.getBlockState(inside).is(Blocks.STONE),
                        scene.id + ": Return to Zero did not put back the block broken after the mark: "
                                + level.getBlockState(inside));
                level.setBlock(inside, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                return true;
            }
        }.start();
    }

    // The bare server and level live only in this frame, so after it returns only the histories could hold them.
    private static WeakReference<?>[] stopWithLateWrite(GameTestHelper helper) {
        Bare bare = Bare.of(helper);
        UUID owner = UUID.randomUUID();
        GerRtzRewindService.markForTests(bare.level(), owner, BlockPos.ZERO);
        GerRtzRewindService.recordBlockMutation(bare.level(), preimage());
        helper.assertTrue(GerRtzRewindService.hasHistoryForTests(bare.server()),
                "setup: no history for the running server");
        // What ServerStoppingEvent does, then the stop, then what can still come (a logout during stopServer).
        GerRtzRewindService.clear(bare.server());
        bare.stop();
        GerRtzRewindService.markForTests(bare.level(), owner, BlockPos.ZERO);
        GerRtzRewindService.recordBlockMutation(bare.level(), preimage());
        return new WeakReference<?>[] {new WeakReference<>(bare.server()), new WeakReference<>(bare.level())};
    }

    private static WeakReference<?>[] markOneBlock(GameTestHelper helper) {
        Bare bare = Bare.of(helper);
        UUID owner = UUID.randomUUID();
        GerRtzRewindService.markForTests(bare.level(), owner, BlockPos.ZERO);
        GerRtzRewindService.recordBlockMutation(bare.level(), preimage());
        helper.assertTrue(GerRtzRewindService.markedBlocksForTests(bare.server(), owner) == 1,
                "setup: the marked block was not recorded");
        return new WeakReference<?>[] {new WeakReference<>(bare.server()), new WeakReference<>(bare.level())};
    }

    private static void awaitCollected(WeakReference<?> ref, String message) {
        for (int i = 0; i < 40 && ref.get() != null; i++) {
            System.gc();
            try {
                Thread.sleep(25L);
            }
            catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (ref.get() != null) {
            throw new GameTestAssertException(message);
        }
    }

    private static GerRtzRewindService.BlockPreimage preimage() {
        return new GerRtzRewindService.BlockPreimage(100L, BlockPos.ZERO, new CompoundTag(), null, true);
    }

    /** A server and a level allocated without their constructors: the level answers getServer and dimension. */
    private record Bare(MinecraftServer server, ServerLevel level) {
        private static Bare of(GameTestHelper helper) {
            try {
                Field theUnsafe = Class.forName("sun.misc.Unsafe").getDeclaredField("theUnsafe");
                theUnsafe.setAccessible(true);
                Object unsafe = theUnsafe.get(null);
                var allocate = unsafe.getClass().getMethod("allocateInstance", Class.class);
                MinecraftServer server = (MinecraftServer) allocate.invoke(
                        unsafe, helper.getLevel().getServer().getClass());
                ServerLevel level = (ServerLevel) allocate.invoke(unsafe, ServerLevel.class);
                set(ServerLevel.class, "server", level, server);
                set(Level.class, "dimension", level, Level.OVERWORLD);
                return new Bare(server, level);
            }
            catch (ReflectiveOperationException exception) {
                throw new AssertionError("setup: could not allocate a bare server and level", exception);
            }
        }

        private void stop() {
            try {
                set(MinecraftServer.class, "stopped", server, true);
            }
            catch (ReflectiveOperationException exception) {
                throw new AssertionError("setup: could not mark the bare server stopped", exception);
            }
            if (!server.isStopped()) {
                throw new GameTestAssertException("setup: the bare server is not stopped");
            }
        }

        private static void set(Class<?> owner, String name, Object target, Object value)
                throws ReflectiveOperationException {
            Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        }
    }
}
