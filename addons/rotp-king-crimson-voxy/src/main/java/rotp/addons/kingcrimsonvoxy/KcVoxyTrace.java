package rotp.addons.kingcrimsonvoxy;

import java.lang.management.ManagementFactory;
import java.util.Locale;

import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL33C;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterClient;

/**
 * Timing runs only (-Drotp.kcvoxy.trace=true): per frame of an erase, CPU marks and GPU timestamps kept in memory and printed
 * once the erase is over, so that the measured frames carry no logging. Off, every entry point returns at its first test.
 */
final class KcVoxyTrace {
    static final boolean ENABLED = Boolean.getBoolean("rotp.kcvoxy.trace");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX = 2400;
    private static final int MARKS = 4;
    static final int FLAG_FOOTPRINT = 1;
    static final int FLAG_JOB = 2;
    static final int FLAG_PLANNED = 4;
    static final int FLAG_UPLOAD = 8;
    static final int FLAG_READY = 16;
    static final int FLAG_LIVE = 32;
    static final int FLAG_MOVING = 64;
    static final int FLAG_OPEN = 128;
    static final int FLAG_KC_BAKE = 256;

    private static final long NORMAL = Long.MIN_VALUE;
    /** Ordinary frames still to record, set by the debug command. */
    static volatile int normalFrames;
    private static boolean recording;
    private static long session;
    private static long createdAt;
    private static int count;
    private static int current = -1;
    private static final long[] kcStart = new long[MAX];
    private static final long[] preAt = new long[MAX];
    private static final long[] preEnd = new long[MAX];
    private static final long[] skyNanos = new long[MAX];
    private static final long[] voxyNanos = new long[MAX];
    private static final long[] postAt = new long[MAX];
    private static final long[] uptime = new long[MAX];
    private static final int[] flags = new int[MAX];
    private static final int[] ticks = new int[MAX];
    private static final long[][] marks = new long[4][MAX];
    private static int[] queries;
    private static long voxyStart;

    private KcVoxyTrace() {}

    /** Last in the frame's Pre event, after King Crimson's own frame start (its bake slice included). */
    static void framePre(KingCrimsonShatterClient.View erase, long now) {
        if (!ENABLED) {
            return;
        }
        current = -1;
        if (erase == null) {
            if (recording && session != NORMAL) {
                dump();
            }
            if (normalFrames <= 0) {
                if (recording) {
                    dump();
                }
                return;
            }
            // Ordinary frames on request, to compare Voxy's own cost outside an erase with its cost inside one.
            if (!recording) {
                begin(NORMAL, now);
            }
            if (count >= Math.min(normalFrames, MAX)) {
                normalFrames = 0;
                dump();
                return;
            }
            int i = count++;
            current = i;
            kcStart[i] = now;
            preAt[i] = now;
            preEnd[i] = now;
            for (long[] mark : marks) {
                mark[i] = 0L;
            }
            skyNanos[i] = 0L;
            voxyNanos[i] = 0L;
            postAt[i] = 0L;
            flags[i] = 0;
            uptime[i] = ManagementFactory.getRuntimeMXBean().getUptime();
            ticks[i] = -1;
            GL33C.glQueryCounter(queries[i * MARKS], GL33C.GL_TIMESTAMP);
            return;
        }
        if (recording && session == NORMAL) {
            dump();
        }
        long startedAt = erase.startedAt();
        if (!recording || session != startedAt) {
            if (recording) {
                dump();
            }
            begin(startedAt, erase.createdAt());
        }
        if (count >= MAX) {
            return;
        }
        int i = count++;
        current = i;
        long kc = erase.frameStartedAt();
        boolean bake = erase.bakedThisFrame();
        kcStart[i] = kc == 0L ? now : kc;
        preAt[i] = now;
        preEnd[i] = now;
        for (long[] mark : marks) {
            mark[i] = 0L;
        }
        skyNanos[i] = 0L;
        voxyNanos[i] = 0L;
        postAt[i] = 0L;
        flags[i] = bake ? FLAG_KC_BAKE : 0;
        uptime[i] = ManagementFactory.getRuntimeMXBean().getUptime();
        ticks[i] = erase.ticks();
        GL33C.glQueryCounter(queries[i * MARKS], GL33C.GL_TIMESTAMP);
    }

    private static void begin(long startedAt, long sessionCreatedAt) {
        if (queries == null) {
            queries = new int[MAX * MARKS];
            GL15C.glGenQueries(queries);
        }
        recording = true;
        session = startedAt;
        createdAt = sessionCreatedAt;
        count = 0;
    }

    /** Points inside this add-on's frame start, to split its cost. */
    static void mark(int k) {
        if (!ENABLED || current < 0) {
            return;
        }
        marks[k][current] = System.nanoTime();
    }

    /** After this add-on's own frame start work. */
    static void framePreEnd(int stateFlags) {
        if (!ENABLED || current < 0) {
            return;
        }
        preEnd[current] = System.nanoTime();
        flags[current] |= stateFlags;
    }

    static void sky(long nanos, int stateFlags) {
        if (!ENABLED || current < 0) {
            return;
        }
        skyNanos[current] += nanos;
        flags[current] |= stateFlags;
    }

    static void voxyHead() {
        if (!ENABLED || current < 0) {
            return;
        }
        voxyStart = System.nanoTime();
        GL33C.glQueryCounter(queries[current * MARKS + 1], GL33C.GL_TIMESTAMP);
    }

    static void voxyReturn() {
        if (!ENABLED || current < 0 || voxyStart == 0L) {
            return;
        }
        voxyNanos[current] += System.nanoTime() - voxyStart;
        voxyStart = 0L;
        GL33C.glQueryCounter(queries[current * MARKS + 2], GL33C.GL_TIMESTAMP);
    }

    static void framePost() {
        if (!ENABLED || current < 0) {
            return;
        }
        postAt[current] = System.nanoTime();
        GL33C.glQueryCounter(queries[current * MARKS + 3], GL33C.GL_TIMESTAMP);
    }

    private static long query(int frame, int mark, boolean wanted) {
        if (!wanted) {
            return 0L;
        }
        int id = queries[frame * MARKS + mark];
        if (!GL15C.glIsQuery(id)) {
            return 0L;
        }
        return GL33C.glGetQueryObjecti64(id, GL15C.GL_QUERY_RESULT);
    }

    private static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1.0E6D);
    }

    private static void dump() {
        recording = false;
        current = -1;
        int frames = count;
        LOGGER.info("KcVoxy trace begin: erase {}, {} frames; columns: f t dt kcPre ours sky voxyCpu toPost gpuFrame gpuVoxy gpuToPost uptime ticks flags",
                session == NORMAL ? "normal" : String.valueOf(session), frames);
        long[] gpuStart = new long[frames];
        for (int i = 0; i < frames; i++) {
            gpuStart[i] = query(i, 0, true);
        }
        for (int i = 0; i < frames; i++) {
            long dt = i == 0 ? 0L : kcStart[i] - kcStart[i - 1];
            long gpuFrame = i + 1 < frames && gpuStart[i + 1] != 0L && gpuStart[i] != 0L ? gpuStart[i + 1] - gpuStart[i] : 0L;
            long vHead = query(i, 1, voxyNanos[i] > 0L);
            long vEnd = query(i, 2, voxyNanos[i] > 0L);
            long post = query(i, 3, postAt[i] != 0L);
            StringBuilder split = new StringBuilder();
            for (long[] mark : marks) {
                split.append(split.isEmpty() ? "" : "/").append(mark[i] == 0L ? "-" : ms(mark[i] - preAt[i]));
            }
            LOGGER.info("KcVoxy trace {} {} {} {} {} {} {} {} {} {} {} {} {} {} {}",
                    i, ms(kcStart[i] - createdAt), ms(dt), ms(preAt[i] - kcStart[i]), ms(preEnd[i] - preAt[i]), ms(skyNanos[i]),
                    ms(voxyNanos[i]), postAt[i] == 0L ? "-" : ms(postAt[i] - preAt[i]), ms(gpuFrame),
                    vHead != 0L && vEnd != 0L ? ms(vEnd - vHead) : "-", post != 0L && gpuStart[i] != 0L ? ms(post - gpuStart[i]) : "-",
                    uptime[i], ticks[i], Integer.toHexString(flags[i]), split);
        }
        LOGGER.info("KcVoxy trace end: erase {}", session);
        count = 0;
    }
}
