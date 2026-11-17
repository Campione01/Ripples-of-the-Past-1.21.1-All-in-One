package rotp.addons.mandom.state;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.mandom.MandomAddon;
import rotp.addons.mandom.MandomEvents;
import rotp.core.powersystem.PowerClass;

/**
 * The rewind history must not keep a stopped server alive, must refuse a stopped server, and must journal blocks
 * only in a level where a Mandom user is. The first three use a bare server and level (no constructor run), which
 * nothing but the history can reach.
 */
@GameTestHolder(MandomAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MandomHistoryLifetimeGameTests {
    private MandomHistoryLifetimeGameTests() {}

    @GameTest(template = "empty", templateNamespace = "rotp_mandom",
            batch = "mandom_history_stopped_collectable", timeoutTicks = 20)
    public static void aStoppedServerAndItsLevelAreCollectableAfterTheStopEvenAfterALateWrite(
            GameTestHelper helper) {
        WeakReference<?>[] refs = stopWithLateWrite(helper);
        awaitCollected(refs[0], "The stopped server is still reachable");
        awaitCollected(refs[1], "The stopped server's level is still reachable");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "rotp_mandom",
            batch = "mandom_history_never_holds_key", timeoutTicks = 20)
    public static void aHistoryWithBlockRecordsDoesNotKeepItsServerOrLevelAlive(GameTestHelper helper) {
        WeakReference<?>[] refs = journalOneBlock(helper);
        awaitCollected(refs[0], "A server with a block record is kept alive by Mandom's histories");
        awaitCollected(refs[1], "A level with a block record is kept alive by Mandom's histories");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "rotp_mandom",
            batch = "mandom_history_stopped_refused", timeoutTicks = 20)
    public static void theStoppedEventClearsAServerAndAStoppedServerGetsNoHistory(GameTestHelper helper) {
        Bare bare = Bare.of(helper);
        helper.assertTrue(MandomRewindService.openJournal(bare.level()), "setup: a running server got no history");
        MandomRewindService.recordBlockMutation(bare.level(), preimage());
        helper.assertTrue(MandomRewindService.hasHistory(bare.server())
                        && MandomRewindService.blockRecordCount(bare.level()) == 1,
                "setup: the running server's block was not journalled");
        bare.stop();
        MandomEvents.onServerStopped(new ServerStoppedEvent(bare.server()));
        helper.assertTrue(!MandomRewindService.hasHistory(bare.server()),
                "ServerStoppedEvent did not clear the stopped server's history");
        helper.assertTrue(!MandomRewindService.openJournal(bare.level())
                        && !MandomRewindService.hasHistory(bare.server()),
                "A stopped server got a new history");
        MandomRewindService.recordBlockMutation(bare.level(), preimage());
        helper.assertTrue(MandomRewindService.blockRecordCount(bare.level()) == -1
                        && !MandomRewindService.hasHistory(bare.server()),
                "A late block write was journalled for a stopped server");
        helper.succeed();
    }

    @GameTest(template = "empty", templateNamespace = "rotp_mandom",
            batch = "mandom_history_user_gate", timeoutTicks = 320)
    public static void blocksAreJournalledOnlyWhileAMandomUserIsInTheLevelAndRewindThere(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(1, 3, 1));
        for (Entity entity : level.getAllEntities()) {
            helper.assertTrue(!(entity instanceof LivingEntity living && MandomAddon.isMandomUser(living)),
                    "setup: a Mandom user is already in the level: " + entity);
        }
        afterBaseSample(helper, () -> {
            assertNoJournal(helper, level, pos, "with no Mandom user in the level");
            FakePlayer user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "MandomGate"));
            Runnable close = () -> {
                user.discard();
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            };
            guard(close, () -> {
                user.setNoGravity(true);
                user.setInvulnerable(true);
                user.setPos(helper.absoluteVec(new Vec3(1.5D, 2.0D, 1.5D)));
                level.addNewPlayer(user);
                PowerClass.STAND.attachGet(user).setStand(MandomAddon.MANDOM.get());
            });
            afterBaseSample(helper, () -> guard(close, () -> {
                helper.assertTrue(MandomRewindService.blockRecordCount(level) >= 0,
                        "No journal was opened at the base sample after a Mandom user came");
                helper.runAfterDelay(60, () -> guard(close, () -> {
                    int before = MandomRewindService.blockRecordCount(level);
                    level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
                    helper.assertTrue(MandomRewindService.blockRecordCount(level) > before,
                            "A block change next to the Mandom user was not journalled");
                    helper.runAfterDelay(60, () -> guard(close, () -> {
                        MandomRewindService.Result result = MandomRewindService.rewind(level, user, null);
                        helper.assertTrue(result == MandomRewindService.Result.SUCCESS,
                                "The rewind 120 ticks after the journal opened failed: " + result);
                        helper.assertTrue(level.getBlockState(pos).isAir(),
                                "The rewind did not take back the block set 60 ticks ago");
                        user.discard();
                        afterBaseSample(helper, () -> guard(close, () -> {
                            assertNoJournal(helper, level, pos, "after the Mandom user left");
                            close.run();
                            helper.succeed();
                        }));
                    }));
                }));
            }));
        });
    }

    private static void assertNoJournal(GameTestHelper helper, ServerLevel level, BlockPos pos, String when) {
        helper.assertTrue(MandomRewindService.blockRecordCount(level) == -1,
                "The level has an open block journal " + when);
        level.setBlock(pos, Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        helper.assertTrue(MandomRewindService.blockRecordCount(level) == -1,
                "A block change was journalled " + when);
    }

    /** Runs the action in the tick after the next base sample (game time 1 modulo 20; test code runs before Post). */
    private static void afterBaseSample(GameTestHelper helper, Runnable action) {
        helper.runAfterDelay(1, () -> {
            if (helper.getLevel().getGameTime() % 20L == 1L) {
                action.run();
            }
            else {
                afterBaseSample(helper, action);
            }
        });
    }

    private static void guard(Runnable close, Runnable action) {
        try {
            action.run();
        }
        catch (RuntimeException | Error exception) {
            close.run();
            throw exception;
        }
    }

    // The bare server and level live only in this frame, so after it returns only Mandom's maps could hold them.
    private static WeakReference<?>[] stopWithLateWrite(GameTestHelper helper) {
        Bare bare = Bare.of(helper);
        helper.assertTrue(MandomRewindService.openJournal(bare.level()), "setup: a running server got no history");
        MandomRewindService.recordBlockMutation(bare.level(), preimage());
        helper.assertTrue(MandomRewindService.hasHistory(bare.server()), "setup: no history for the running server");
        // What ServerStoppingEvent does, then the stop, then the writes that can still come.
        MandomRewindService.clear(bare.server());
        bare.stop();
        MandomRewindService.openJournal(bare.level());
        MandomRewindService.recordBlockMutation(bare.level(), preimage());
        return new WeakReference<?>[] {new WeakReference<>(bare.server()), new WeakReference<>(bare.level())};
    }

    private static WeakReference<?>[] journalOneBlock(GameTestHelper helper) {
        Bare bare = Bare.of(helper);
        helper.assertTrue(MandomRewindService.openJournal(bare.level()), "setup: a running server got no history");
        MandomRewindService.recordBlockMutation(bare.level(), preimage());
        helper.assertTrue(MandomRewindService.hasHistory(bare.server())
                        && MandomRewindService.blockRecordCount(bare.level()) == 1,
                "setup: the block was not journalled");
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

    private static MandomRewindService.BlockPreimage preimage() {
        return new MandomRewindService.BlockPreimage(100L, BlockPos.ZERO, new CompoundTag(), null, true);
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
