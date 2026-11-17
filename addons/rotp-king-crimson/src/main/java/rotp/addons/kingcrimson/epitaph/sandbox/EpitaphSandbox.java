package rotp.addons.kingcrimson.epitaph.sandbox;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.entity.LevelEntityGetter;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;

/**
 * The scope prediction code runs in. While a room is entered, the phantoms of one prediction are ticked inside the
 * real level, and every hook of the barrier turns what they do to the world into a recorded op or into nothing.
 * {@link #depth} is the one flag everybody else reads: 0 whenever no prediction code is running. The state is the
 * server thread's; a hook that finds the flag set on another thread lets its call through.
 */
public final class EpitaphSandbox {
    private static final Logger LOGGER = LogUtils.getLogger();

    public enum Mode {
        NONE,
        /** The room: nothing leaves it. */
        SEALED,
        /** An action of the record ticking on its real performer: it runs, what it writes is absorbed. */
        MUTED
    }

    /** What a muted tick is given in place of the level: whom it may find, and what the record holds of its hits. */
    public interface Mirror {
        /** The bodies on their tracks: lookups made in the level answer these alone. */
        LevelEntityGetter<Entity> bodies();

        /** The players among them. */
        List<ServerPlayer> players();

        /** Whether the record has this hit at the tick being lived: the action is told that it landed, and the op deals it. */
        boolean lands(LivingEntity victim, DamageSource source);
    }

    /** 0 = no prediction code is running. Every hook starts by reading it and returns at once when it is 0. */
    public static int depth;
    private static Mode mode = Mode.NONE;
    @Nullable
    private static SandboxRoom room;
    @Nullable
    private static Entity actor;
    @Nullable
    private static ServerPlayer viewer;
    @Nullable
    private static Thread thread;
    private static boolean replaying;
    @Nullable
    private static Mirror mirror;
    @Nullable
    private static RandomState realRandom;
    private static long passes;
    private static int leaks;
    @Nullable
    private static String firstLeak;
    @Nullable
    private static Throwable firstLeakTrace;
    private static boolean leakLogged;

    private EpitaphSandbox() {}

    public static void enter(SandboxRoom entered, Mode as) {
        if (depth != 0) {
            throw new IllegalStateException("A room is already entered");
        }
        thread = Thread.currentThread();
        room = entered;
        mode = as;
        actor = null;
        depth = 1;
        entered.onEnter();
    }

    /**
     * Enters the muted mode for the tick of an action on its real performer. What it draws from the random source of the
     * level is given back at the end: the world draws after it what it would have drawn without it.
     */
    public static void enterMuted(SandboxRoom of, Mirror with) {
        if (depth != 0) {
            throw new IllegalStateException("A room is already entered");
        }
        thread = Thread.currentThread();
        room = of;
        mode = Mode.MUTED;
        mirror = with;
        actor = null;
        realRandom = RandomState.of(of.level().random);
        depth = 1;
    }

    public static void exit() {
        SandboxRoom left = room;
        try {
            if (mode == Mode.MUTED) {
                if (left != null && realRandom != null) {
                    realRandom.writeTo(left.level().random);
                }
            } else if (left != null) {
                left.onExit();
            }
        }
        finally {
            depth = 0;
            mode = Mode.NONE;
            room = null;
            actor = null;
            viewer = null;
            mirror = null;
            realRandom = null;
        }
    }

    /**
     * Whether the caller is room code: asked by a hook once the flag was not 0. It counts the call, so a test can tell
     * that no hook went past its flag while no prediction ran.
     */
    public static boolean sealed() {
        passes++;
        return mode == Mode.SEALED && Thread.currentThread() == thread;
    }

    /** Whether a muted tick runs: asked by a hook after it asked whether the caller is room code, which counted the call. */
    public static boolean mirroring() {
        return mode == Mode.MUTED && Thread.currentThread() == thread;
    }

    /** What the muted tick that runs is given in place of the level. */
    public static Mirror mirror() {
        return mirror;
    }

    public static SandboxRoom room() {
        return room;
    }

    @Nullable
    public static Entity actor() {
        return actor;
    }

    public static ActorKey actorKey() {
        return ActorKey.of(actor);
    }

    public static void setActor(@Nullable Entity phantom) {
        actor = phantom;
    }

    @Nullable
    public static ServerPlayer viewer() {
        return viewer;
    }

    public static void setViewer(@Nullable ServerPlayer user) {
        viewer = user;
    }

    /** Records what the acting phantom did at the tick the room is working on. */
    public static void record(RecordedOp op) {
        if (room != null) {
            room.record(op);
        }
    }

    /** An op of the record is being carried out for real: hooks let it through, and the fate rules know it is no outsider. */
    public static boolean replaying() {
        return replaying;
    }

    public static void setReplaying(boolean carryingOut) {
        replaying = carryingOut;
    }

    /**
     * Something reached past the barrier, or would have: a belt hook was hit, or room code was handed a real entity.
     * Counted; the first one is kept with its stack and logged once a server run.
     */
    public static void leak(String what) {
        leaks++;
        if (firstLeak == null) {
            firstLeak = what;
            firstLeakTrace = new Throwable(what);
        }
        if (!leakLogged) {
            leakLogged = true;
            LOGGER.warn("Epitaph[leak] something reached past the room: {}", what, firstLeakTrace);
        }
    }

    public static int leaks() {
        return leaks;
    }

    @Nullable
    public static String firstLeak() {
        return firstLeak;
    }

    public static void resetLeaks() {
        leaks = 0;
        firstLeak = null;
        firstLeakTrace = null;
    }

    /** A hook of the replay went past its flag. */
    public static void countPass() {
        passes++;
    }

    /** How often a hook went past its flag check since the last reset. */
    public static long passes() {
        return passes;
    }

    public static void resetPasses() {
        passes = 0L;
    }
}
