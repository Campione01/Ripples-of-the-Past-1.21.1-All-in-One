package rotp.addons.kingcrimsonvoxy;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL15C;
import org.lwjgl.opengl.GL30C;
import org.lwjgl.opengl.GL31C;
import org.lwjgl.opengl.GL43C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.Util;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterClient;
import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterIrisConstants;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarSurface;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPlan;

/**
 * Keeps Voxy alive during the local Time Erase and moves its quads with plates of the far plan. Outside an erase its whole cost
 * is three buffer binds per terrain pass. All poses come from {@link KingCrimsonShatterPlan#pose}, on the CPU, once per frame.
 */
public final class KcVoxyFar {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SCENE_BYTES = 144;
    private static final int POSE_FLOATS = 28;
    private static final int POSE_BYTES = POSE_FLOATS * 4;
    private static final int AFFINE = 12;
    // Beyond this distance a far plate's flight grows with its distance, so that it moves on screen as the plates at the join do.
    private static final float REACH_FROM = 192.0F;
    // its tick 85, but a plate seen edge-on at the horizon is a line that holes hardly thin, so it seems to stay the longest.
    static volatile float marginDegrees = 40.0F;
    private static final float PLANE_SLACK_BLOCKS = 64.0F;
    // A pack shows the far ground only if it leaves the void off LOD pixels: it says so with this entity id.
    private static final String PACK_MARKER_NAMESPACE = "rotp_kingcrimson";
    private static final String PACK_MARKER_NAME = "time_erase_far";

    /** 0: off (debug runs). 1: Voxy alive, nothing moves. 2: quads move, stock culling. 3: quads move, culling open. */
    static volatile int stage = KcVoxyClientEvents.COMMANDS ? Integer.getInteger("rotp.kcvoxy.stage", 3) : 3;
    static volatile float frozenTick = -1.0F;
    /** The parts of the look that run (KcVoxyShaderPatch.LOOK_*): all of them, but for before-and-after takes. */
    static volatile int look = KcVoxyShaderPatch.LOOK_ALL;
    /** The last LOD level whose quads are cut along the plates (KcVoxyShaderPatch.LOOK_SPLIT); coarser ones go whole. */
    static volatile int splitLevel = 15;
    /** Debug runs: blocks added to every loose piece's rise, to find them on screen. */
    static volatile float pieceBoost = 0.0F;
    private static int traceFlags;

    private static boolean live;
    private static Far far;
    private static int sceneBuffer;
    private static int poseBuffer;
    private static int wordBuffer;
    private static ByteBuffer sceneBytes;
    private static boolean sceneIdle;
    private static boolean packKnown;
    private static boolean packSupports;
    private static int packMarkerId;
    private static MethodHandle entityIds;
    private static Object markerKey;
    private static Object lastEntityIds;

    private KcVoxyFar() {}

    /** Whether Voxy, and the terrain pass that calls it, stay on in this frame. */
    public static boolean live() {
        return live;
    }

    /**
     * While Voxy runs for the erase the void shell draws under the pack's id for it, and only that id keeps off Voxy's pixels.
     * With any other id the void covers everything: Voxy's depth is stale whenever the guard stops it.
     */
    public static int shellEntityId(int id) {
        return live && id == KingCrimsonShatterIrisConstants.VOID_EMISSIVE_ID ? packMarkerId : id;
    }

    /** At the start of a frame, after King Crimson's client has moved its own session on. */
    static void onFrameStart() {
        long now = System.nanoTime();
        KcVoxyWords.tick();
        if (!KcVoxyStatus.usable() || !KcVoxyShaderPatch.approved()) {
            live = false;
            return;
        }
        traceFlags = 0;
        try {
            // An older King Crimson without the view throws a LinkageError here, which turns the effect off below.
            KingCrimsonShatterClient.View session = KingCrimsonShatterClient.view();
            if (KcVoxyTrace.ENABLED) {
                KcVoxyTrace.framePre(session, now);
            }
            if (session == null) {
                packKnown = false;
            }
            KcVoxyTrace.mark(0);
            if (session == null || stage == 0 || !session.iris() || !packSupportsFar()) {
                live = false;
                closeFar();
                return;
            }
            KcVoxyTrace.mark(1);
            long startedAt = session.startedAt();
            if (far == null || far.startedAt != startedAt) {
                closeFar();
                far = new Far(startedAt, session.originX(), session.originY(), session.originZ(), session.createdAt());
            }
            KcVoxyTrace.mark(2);
            far.advance(session);
            KcVoxyTrace.mark(3);
            boolean ready = session.ready();
            live = !session.failed() && ready;
            if (KcVoxyTrace.ENABLED) {
                traceFlags |= (ready ? KcVoxyTrace.FLAG_READY : 0) | (live ? KcVoxyTrace.FLAG_LIVE : 0)
                        | (far.job != null ? KcVoxyTrace.FLAG_JOB : 0) | (far.planned != null ? KcVoxyTrace.FLAG_PLANNED : 0);
            }
        } catch (Throwable failure) {
            rethrowFatal(failure);
            KcVoxyStatus.fail("erase session read failed: " + failure);
            live = false;
            closeFar();
        } finally {
            if (KcVoxyTrace.ENABLED) {
                KcVoxyTrace.framePreEnd(traceFlags);
            }
        }
    }

    // Read again at each erase, but only the map's identity is looked at unless Iris has loaded a pack since: the press frame is busy.
    private static boolean packSupportsFar() {
        if (!packKnown) {
            packKnown = true;
            try {
                if (entityIds == null) {
                    ClassLoader loader = KcVoxyFar.class.getClassLoader();
                    Class<?> settings = Class.forName("net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings", true, loader);
                    Class<?> namespaced = Class.forName("net.irisshaders.iris.shaderpack.materialmap.NamespacedId", true, loader);
                    MethodHandle getter = MethodHandles.publicLookup().findVirtual(settings, "getEntityIds",
                            MethodType.methodType(settings.getMethod("getEntityIds").getReturnType()));
                    entityIds = getter.bindTo(settings.getField("INSTANCE").get(null)).asType(MethodType.methodType(Object.class));
                    markerKey = namespaced.getConstructor(String.class, String.class).newInstance(PACK_MARKER_NAMESPACE, PACK_MARKER_NAME);
                }
                Object ids = (Object) entityIds.invokeExact();
                if (ids != lastEntityIds || ids == null) {
                    lastEntityIds = ids;
                    int id = ids == null ? -1 : ((it.unimi.dsi.fastutil.objects.Object2IntFunction<?>) ids).getInt(markerKey);
                    packSupports = id > 0;
                    packMarkerId = id;
                    LOGGER.info("King Crimson Voxy far terrain: shader pack marker id {}, {}", id, packSupports ? "supported" : "not supported");
                }
            } catch (Throwable failure) {
                rethrowFatal(failure);
                packSupports = false;
                LOGGER.info("King Crimson Voxy far terrain: cannot read the shader pack's entity ids: {}", failure.toString());
            }
        }
        return packSupports;
    }

    /** At AFTER_SKY: before Voxy's call in the terrain pass, after the camera of this frame is known. */
    static void onAfterSky(RenderLevelStageEvent event) {
        if (sceneBuffer == 0) {
            return;
        }
        if (!KcVoxyTrace.ENABLED) {
            writeScene(event);
            return;
        }
        long start = System.nanoTime();
        int flags = writeScene(event);
        KcVoxyTrace.sky(System.nanoTime() - start, flags);
    }

    private static int writeScene(RenderLevelStageEvent event) {
        Far current = far;
        if (!live || current == null) {
            writeIdle();
            return 0;
        }
        int level = stage;
        boolean moving = level >= 2 && current.uploaded;
        boolean opened = level >= 3 && moving;
        try {
            if (moving) {
                current.writePoses(tickOf(event));
            }
        } catch (Throwable failure) {
            rethrowFatal(failure);
            KcVoxyStatus.fail("pose upload failed: " + failure);
            live = false;
            closeFar();
            writeIdle();
            return 0;
        }
        ByteBuffer scene = sceneBytes;
        scene.clear();
        scene.putInt(moving ? 1 : 0).putInt(opened ? 1 : 0).putInt(1).putInt(current.planned == null ? 0 : current.planned.plates);
        scene.putInt(current.originX).putInt(current.originY).putInt(current.originZ).putInt(look | splitLevel << 8);
        KcVoxyPlan plan = current.planned;
        scene.putInt(plan == null ? 0 : plan.footprintMinX).putInt(plan == null ? 0 : plan.footprintMinZ)
                .putInt(plan == null ? 0 : plan.footprintSide).putInt(plan == null ? 0 : plan.firstFootprintWord);
        scene.putInt(KcVoxyPlan.HALF_EXTENT).putInt(KcVoxyPlan.TEXELS).putInt(KcVoxyPlan.BLOCKS_PER_TEXEL_SHIFT).putInt(KcVoxyPlan.OUTER_SECTORS);
        writePlanes(scene, event);
        boolean pieces = plan != null && plan.pieces.count > 0;
        scene.putInt(pieces ? plan.firstPieceWord : 0).putInt(pieces ? KcVoxyPieces.SQUARES : 0)
                .putInt(pieces ? plan.firstPieceWord + KcVoxyPieces.SQUARES * KcVoxyPieces.SQUARES * KcVoxyPieces.SQUARE_WORDS : 0)
                .putInt(pieces ? plan.plates + plan.outerRimPlate.length : 0);
        scene.flip();
        GL45C.glNamedBufferSubData(sceneBuffer, 0L, scene);
        sceneIdle = false;
        return (moving ? KcVoxyTrace.FLAG_MOVING : 0) | (opened ? KcVoxyTrace.FLAG_OPEN : 0);
    }

    private static float tickOf(RenderLevelStageEvent event) throws Throwable {
        if (frozenTick >= 0.0F) {
            return frozenTick;
        }
        KingCrimsonShatterClient.View session = KingCrimsonShatterClient.view();
        if (session == null) {
            return 0.0F;
        }
        float partial = session.paused() || !session.running()
                ? 0.0F
                : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        return session.ticks() + partial;
    }

    // The view's own side planes, each turned outward: pieces outside the view at rest can fly into it.
    private static void writePlanes(ByteBuffer scene, RenderLevelStageEvent event) {
        Matrix4f m = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        Vector3f look = event.getCamera().getLookVector();
        float push = (float) Math.tan(Math.toRadians(marginDegrees));
        writePlane(scene, m.m03() + m.m00(), m.m13() + m.m10(), m.m23() + m.m20(), look, push);
        writePlane(scene, m.m03() - m.m00(), m.m13() - m.m10(), m.m23() - m.m20(), look, push);
        writePlane(scene, m.m03() + m.m01(), m.m13() + m.m11(), m.m23() + m.m21(), look, push);
        writePlane(scene, m.m03() - m.m01(), m.m13() - m.m11(), m.m23() - m.m21(), look, push);
    }

    private static void writePlane(ByteBuffer scene, float x, float y, float z, Vector3f look, float push) {
        float length = (float) Math.sqrt(x * x + y * y + z * z);
        x = x / length + look.x * push;
        y = y / length + look.y * push;
        z = z / length + look.z * push;
        length = (float) Math.sqrt(x * x + y * y + z * z);
        scene.putFloat(x / length).putFloat(y / length).putFloat(z / length).putFloat(PLANE_SLACK_BLOCKS);
    }

    private static void writeIdle() {
        if (!sceneIdle) {
            sceneIdle = true;
            ByteBuffer scene = sceneBytes;
            scene.clear();
            while (scene.hasRemaining()) {
                scene.put((byte) 0);
            }
            scene.flip();
            GL45C.glNamedBufferSubData(sceneBuffer, 0L, scene);
        }
    }

    /** Before anything of Voxy's can run in this terrain pass. */
    public static void bindBuffers() {
        if (!KcVoxyShaderPatch.patchedAny()) {
            return;
        }
        if (sceneBuffer == 0) {
            sceneBytes = MemoryUtil.memAlloc(SCENE_BYTES).order(java.nio.ByteOrder.nativeOrder());
            sceneBuffer = GL45C.glCreateBuffers();
            GL45C.glNamedBufferData(sceneBuffer, SCENE_BYTES, GL15C.GL_DYNAMIC_DRAW);
            poseBuffer = GL45C.glCreateBuffers();
            GL45C.glNamedBufferData(poseBuffer, POSE_BYTES, GL15C.GL_STREAM_DRAW);
            wordBuffer = GL45C.glCreateBuffers();
            GL45C.glNamedBufferData(wordBuffer, 16L, GL15C.GL_STATIC_DRAW);
            sceneIdle = false;
            writeIdle();
        }
        GL30C.glBindBufferBase(GL31C.GL_UNIFORM_BUFFER, KcVoxyShaderPatch.UBO_BINDING, sceneBuffer);
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, KcVoxyShaderPatch.POSE_BINDING, poseBuffer);
        GL30C.glBindBufferBase(GL43C.GL_SHADER_STORAGE_BUFFER, KcVoxyShaderPatch.WORD_BINDING, KcVoxyWords.buffer(wordBuffer));
    }

    static void onLogout() {
        live = false;
        closeFar();
        KcVoxyWords.close();
    }

    private static void closeFar() {
        Far old = far;
        if (old == null) {
            return;
        }
        far = null;
        old.close();
    }

    static String describe() {
        Far current = far;
        return "live=" + live + " stage=" + stage + " frozen=" + frozenTick
                + " status=" + (KcVoxyStatus.failed() ? KcVoxyStatus.reason() : "ok")
                + " approved=" + KcVoxyShaderPatch.approved()
                + " packSupports=" + packSupports
                + " far=" + (current == null ? "none" : current.describe());
    }

    private static void rethrowFatal(Throwable failure) {
        if (failure instanceof Error error && !(failure instanceof LinkageError)) {
            throw error;
        }
    }

    private static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1.0E6D);
    }

    private static final class Far {
        private final long startedAt;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final long sessionCreatedAt;
        private CompletableFuture<KcVoxyPlan> job;
        private KcVoxyPlan planned;
        private boolean planFailed;
        private boolean uploaded;
        private ByteBuffer poses;
        private final KingCrimsonShatterPlan.Pose pose = new KingCrimsonShatterPlan.Pose();
        private final float[] rows = new float[AFFINE];
        private float[] previous;
        private boolean previousValid;
        private long uploadNanos;
        private long poseNanos;
        private long poseFrames;
        private long poseMax;
        private boolean closed;

        private Far(long startedAt, int originX, int originY, int originZ, long sessionCreatedAt) {
            this.startedAt = startedAt;
            this.originX = originX;
            this.originY = originY;
            this.originZ = originZ;
            this.sessionCreatedAt = sessionCreatedAt;
        }

        private void advance(KingCrimsonShatterClient.View session) {
            if (planned == null && job == null && !planFailed) {
                KingCrimsonShatterFarSurface.NearFootprint footprint = session.footprint();
                ByteBuffer out = footprint == null ? null : KcVoxyWords.acquire();
                if (out != null) {
                    long seed = session.seed();
                    float yaw = session.pressYaw();
                    int nearRange = session.nearRange();
                    int x = originX;
                    int z = originZ;
                    job = CompletableFuture.supplyAsync(() -> {
                        try {
                            return new KcVoxyPlan(x, z, seed, yaw, nearRange, footprint, out);
                        } finally {
                            KcVoxyWords.release();
                        }
                    }, Util.backgroundExecutor());
                }
            }
            if (job != null && job.isDone()) {
                CompletableFuture<KcVoxyPlan> done = job;
                job = null;
                try {
                    planned = done.join();
                } catch (CompletionException failure) {
                    planFailed = true;
                    LOGGER.warn("King Crimson Voxy far plan of erase {} failed; this erase goes on without far terrain.", startedAt,
                            failure.getCause());
                    return;
                }
                upload();
            }
        }

        private void upload() {
            long start = System.nanoTime();
            KcVoxyWords.publish(planned.words);
            int total = planned.plates + KcVoxyPlan.OUTER_RINGS * KcVoxyPlan.OUTER_SECTORS + planned.pieces.count;
            poses = MemoryUtil.memAlloc(total * POSE_BYTES).order(java.nio.ByteOrder.nativeOrder());
            uploaded = true;
            uploadNanos = System.nanoTime() - start;
            traceFlags |= KcVoxyTrace.FLAG_UPLOAD;
            LOGGER.info("King Crimson Voxy far plan: erase {}, {} plates, {} loose pieces, plan {} ms, map {} ms, upload {} ms ({} KiB), "
                            + "{} ms after the press", startedAt, planned.plates, planned.pieces.count, ms(planned.planNanos),
                    ms(planned.mapNanos), ms(uploadNanos), planned.words / 256, ms(System.nanoTime() - sessionCreatedAt));
        }

        private void writePoses(float tick) {
            long start = System.nanoTime();
            KcVoxyPlan far = planned;
            KingCrimsonShatterPlan plan = far.plan;
            ByteBuffer out = poses;
            out.clear();
            KcVoxyPieces pieces = far.pieces;
            int total = far.plates + far.outerRimPlate.length + pieces.count;
            if (previous == null) {
                previous = new float[total * AFFINE];
            }
            boolean reach = (look & KcVoxyShaderPatch.LOOK_REACH) != 0;
            for (int plate = 0; plate < far.plates; plate++) {
                float distance = plan.plateDistance(plate);
                float clock = KcVoxyTail.tick(tick, distance, far.nearRange);
                plan.pose(plate, clock, pose);
                writePose(out, plate, plan.pivotX(plate) - originX, plan.pivotZ(plate) - originZ, plan.tearShare(plate, clock),
                        grainOf(distance), reach ? reachOf(distance) : 1.0F);
            }
            for (int outer = 0; outer < far.outerRimPlate.length; outer++) {
                int rim = far.outerRimPlate[outer];
                float x = far.outerPivotX[outer];
                float z = far.outerPivotZ[outer];
                float rimDistance = (float) Math.sqrt(x * x + z * z);
                float clock = KcVoxyTail.tick(tick, rimDistance, far.nearRange);
                plan.pose(rim, clock, pose);
                writePose(out, far.plates + outer, x, z, plan.tearShare(rim, clock), 4,
                        reach ? reachOf(rimDistance) : 1.0F);
            }
            boolean loose = (look & KcVoxyShaderPatch.LOOK_PIECES) != 0;
            int firstPiece = far.plates + far.outerRimPlate.length;
            for (int piece = 0; piece < pieces.count; piece++) {
                int plate = pieces.parent[piece];
                float distance = plan.plateDistance(plate);
                float clock = KcVoxyTail.tick(tick, distance, far.nearRange);
                plan.pose(plate, clock, pose);
                float plateReach = reach ? reachOf(distance) : 1.0F;
                rowsOf(plan.pivotX(plate) - originX, plan.pivotZ(plate) - originZ, plateReach);
                if (loose) {
                    float pieceReach = reach ? Math.max(1.0F, pieces.distance[piece] / KcVoxyPieces.REACH_BLOCKS) : 1.0F;
                    float half = pieces.side[piece] * 0.5F;
                    loosen(pieces.cellX[piece] + half, pieces.cellZ[piece] + half, pieces.turn(piece, clock),
                            pieces.rise(piece, clock) * pieceReach + pieceBoost);
                }
                emit(out, firstPiece + piece, loose ? pieces.tear(piece, clock) : plan.tearShare(plate, clock), grainOf(distance));
            }
            previousValid = true;
            out.flip();
            GL45C.glNamedBufferData(poseBuffer, out, GL15C.GL_STREAM_DRAW);
            long spent = System.nanoTime() - start;
            poseNanos += spent;
            poseFrames++;
            poseMax = Math.max(poseMax, spent);
        }

        private static float reachOf(float distance) {
            return Math.max(1.0F, distance / REACH_FROM);
        }

        private static int grainOf(float distance) {
            return distance < 384.0F ? 2 : distance < 1024.0F ? 3 : 4;
        }

        // q' = A (q - pivot) + pivot + d, all relative to the erase's origin; the pivot's y is the origin's. Then the tear, then
        // these rows minus the last frame's, which the pack turns into motion vectors.
        private void writePose(ByteBuffer out, int index, float pivotX, float pivotZ, float tear, int grainShift, float reach) {
            rowsOf(pivotX, pivotZ, reach);
            emit(out, index, tear, grainShift);
        }

        // A loose piece: its plate's pose after a turn about the vertical line through the cell's centre (k), then a rise
        // straight up. Rows [A | t] become [A R | t + A (k - R k) + rise].
        private void loosen(float centreX, float centreZ, float turn, float rise) {
            float[] r = rows;
            float c = (float) Math.cos(turn);
            float s = (float) Math.sin(turn);
            float ex = centreX - (c * centreX + s * centreZ);
            float ez = centreZ - (-s * centreX + c * centreZ);
            for (int row = 0; row < AFFINE; row += 4) {
                float a0 = r[row];
                float a2 = r[row + 2];
                r[row + 3] += a0 * ex + a2 * ez;
                r[row] = c * a0 - s * a2;
                r[row + 2] = s * a0 + c * a2;
            }
            r[7] += rise;
        }

        private void rowsOf(float pivotX, float pivotZ, float reach) {
            KingCrimsonShatterPlan.Pose p = pose;
            float s = p.scale;
            float[] r = rows;
            r[0] = p.m00 * s;
            r[1] = p.m01 * s;
            r[2] = p.m02 * s;
            r[4] = p.m10 * s;
            r[5] = p.m11 * s;
            r[6] = p.m12 * s;
            r[8] = p.m20 * s;
            r[9] = p.m21 * s;
            r[10] = p.m22 * s;
            r[3] = pivotX + p.dx * reach - (r[0] * pivotX + r[2] * pivotZ);
            r[7] = p.dy * reach - (r[4] * pivotX + r[6] * pivotZ);
            r[11] = pivotZ + p.dz * reach - (r[8] * pivotX + r[10] * pivotZ);
        }

        private void emit(ByteBuffer out, int index, float tear, int grainShift) {
            float[] r = rows;
            for (float value : r) {
                out.putFloat(value);
            }
            out.putFloat(tear).putFloat(grainShift).putFloat(0.0F).putFloat(0.0F);
            int base = index * AFFINE;
            for (int i = 0; i < AFFINE; i++) {
                out.putFloat(previousValid ? r[i] - previous[base + i] : 0.0F);
                previous[base + i] = r[i];
            }
        }

        private String describe() {
            return "erase=" + startedAt + " plates=" + (planned == null ? -1 : planned.plates) + " uploaded=" + uploaded;
        }

        private void close() {
            if (closed) {
                return;
            }
            closed = true;
            if (job != null) {
                job.cancel(false);
            }
            if (poseFrames > 0L) {
                LOGGER.info("King Crimson Voxy far poses: erase {}, {} frames, {} ms mean, {} ms at most",
                        startedAt, poseFrames, ms(poseNanos / poseFrames), ms(poseMax));
            }
            if (poses != null) {
                MemoryUtil.memFree(poses);
                poses = null;
            }
        }
    }

    /** Opens the shader reader on Voxy's own loader: the first thing in a world, before Voxy needs it. */
    public static void bindShaderParser() {
        try {
            Class<?> loader = Class.forName("me.cortex.voxy.client.core.gl.shader.ShaderLoader", true, KcVoxyFar.class.getClassLoader());
            MethodHandle parse = MethodHandles.publicLookup().findStatic(loader, "parse", MethodType.methodType(String.class, String.class));
            KcVoxyShaderPatch.setParser(path -> {
                try {
                    return (String) parse.invokeExact(path);
                } catch (RuntimeException | Error failure) {
                    throw failure;
                } catch (Throwable failure) {
                    throw new IllegalStateException(failure);
                }
            });
        } catch (ReflectiveOperationException | LinkageError failure) {
            KcVoxyStatus.fail("Voxy's shader loader is absent: " + failure);
        }
    }

}
