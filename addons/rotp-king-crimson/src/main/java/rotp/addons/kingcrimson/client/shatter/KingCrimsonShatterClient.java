package rotp.addons.kingcrimson.client.shatter;

import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.client.KingCrimsonTemporalClientState;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPlan;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPackRoute;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.core.api.client.render.ClientRenderCompatibility;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * The ground breaking up under the eraser's feet, on his client alone: no server or block change. A session follows
 * one erase: the mesh is built in the background while the real terrain stays, then the plates take over and the
 * timeline starts; the end of the erase drops everything at once, behind the existing end transition.
 */
@OnlyIn(Dist.CLIENT)
public final class KingCrimsonShatterClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    // The terrain comes off at last even if the background build got stuck.
    private static final int MAX_TERRAIN_HOLD_TICKS = 40;
    // The timeline starts as the activation flash fades, about here: the wind must not run under it.
    private static final int TICKS_UNDER_THE_FLASH = 4;
    // A late build takes this much of its wait out of the first ticks, in which hardly anything has started to move yet.
    private static final int MOST_CAUGHT_UP_TICKS = 2;
    // The first builds of a session take several times as long as the later ones, until the JVM has compiled them, and the
    // first line of that erase would start half a second late: a King Crimson user's client builds twice for nothing, unseen.
    private static final int WARM_UP_AFTER_TICKS = 100;

    private static Session session;
    private static int warmUpIn = WARM_UP_AFTER_TICKS;
    private static IrisWarmUp irisWarmUp;
    private static long previousFrameStart;

    private KingCrimsonShatterClient() {}

    /**
     * What another mod may read of the erase whose ground is breaking on this client, on the render thread and read only: the
     * far-terrain add-on follows the near plates with it. Its answers change from frame to frame.
     */
    public interface View {
        /** The erase's start on the server clock: one erase, one value. */
        long startedAt();

        /** {@link System#nanoTime} when this client began the erase's ground. */
        long createdAt();

        /** Whether the plates are drawn through a shader pack. */
        boolean iris();

        /** Whether the breaking ground gave up: the erase goes on without plates. */
        boolean failed();

        /** Whether the plates are built and uploaded and have taken over from the real ground. */
        boolean ready();

        boolean paused();

        /** Whether the timeline runs: {@link #ticks} counts from its start. */
        boolean running();

        int ticks();

        long seed();

        int originX();

        int originY();

        int originZ();

        /** The yaw the user faced at the press: the plan's star of cuts is laid out around it. */
        float pressYaw();

        /** The range of the near plan, which a far plan follows (see {@link KingCrimsonShatterPlan#createFar}). */
        int nearRange();

        /** The columns the near plates hold; null until the near mesh is built. */
        rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarSurface.NearFootprint footprint();

        /** {@link System#nanoTime} at the start of this render frame, 0 before the first. */
        long frameStartedAt();

        /** Whether this frame uploaded plates. */
        boolean bakedThisFrame();
    }

    /** The running erase's view, or null when none runs on this client. */
    public static View view() {
        return session;
    }

    private static int rangeOf(Minecraft minecraft) {
        return Math.min(KingCrimsonShatterPlan.DEFAULT_RANGE, minecraft.options.getEffectiveRenderDistance() * 16);
    }

    private static void warmUp() {
        if (warmUpIn <= 0) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null
                || player == null
                || !KingCrimsonAddon.isKingCrimsonUser(player)
                || --warmUpIn > 0) {
            return;
        }
        int range = rangeOf(minecraft);
        KingCrimsonShatterPlan plan =
                KingCrimsonShatterPlan.create(player.getBlockX(), player.getBlockZ(), 1L, range, player.getYRot());
        if (ClientRenderCompatibility.snapshot().irisShaderPackInUse()) {
            if (!KingCrimsonTemporalClientState.localTimeErase()) {
                try {
                    irisWarmUp = new IrisWarmUp(level, player, plan, range);
                } catch (RuntimeException | LinkageError failure) {
                    LOGGER.warn("Time Erase could not start its Iris warm-up.", failure);
                }
            }
            return;
        }
        KingCrimsonShatterBuilder first =
                new KingCrimsonShatterBuilder(level, plan, player.getBlockY(), range, 1L, () -> false);
        KingCrimsonShatterBuilder second =
                new KingCrimsonShatterBuilder(level, plan, player.getBlockY(), range, 2L, () -> false);
        CompletableFuture.supplyAsync(
                        () -> first.build().buildMillis + " and " + second.build().buildMillis, Util.backgroundExecutor())
                .whenComplete((built, failure) -> {
                    if (failure != null) {
                        LOGGER.warn("Time Erase could not warm up the breaking of the ground.", failure);
                    } else {
                        LOGGER.info("King Crimson shatter warmed up: built in {} ms", built);
                    }
                });
    }

    private static void advanceIrisWarmUp() {
        IrisWarmUp current = irisWarmUp;
        if (current == null) return;
        Minecraft minecraft = Minecraft.getInstance();
        try {
            if (minecraft.level == current.level && minecraft.player == current.player
                    && !KingCrimsonTemporalClientState.localTimeErase()
                    && ClientRenderCompatibility.snapshot().irisShaderPackInUse()
                    && current.bakeNextSlice()) {
                return;
            }
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("Time Erase could not warm up its Iris plate upload.", failure);
        }
        stopIrisWarmUp();
    }

    private static void stopIrisWarmUp() {
        IrisWarmUp current = irisWarmUp;
        if (current == null) return;
        irisWarmUp = null;
        try {
            current.close();
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("Time Erase could not close its Iris warm-up buffers.", failure);
        }
    }

    public static void tick() {
        warmUp();
        Session current = sync();
        if (current == null) {
            return;
        }
        current.heldTicks++;
        if (!current.iris) current.pollBuild();
        if (current.ready() && !current.paused() && current.heldTicks > TICKS_UNDER_THE_FLASH) {
            if (!current.running) {
                current.timelineStartedAt = current.heldTicks;
                current.timelineStartedNanos = System.nanoTime() - current.createdAt;
                // Owner 2026-11-07, the passive's erase: "不做主動進入時刪時的地裂與漂浮等，直接把周圍景物覆蓋". Its timeline
                // begins where the key's ends: every plate, crest and drifting block is gone and the void is all there is.
                current.ticks = KingCrimsonAddon.temporalState(current.player).eraseKind().isPassive()
                        ? (int) Math.ceil(KingCrimsonShatterPlan.END_TICK)
                        : Math.min(MOST_CAUGHT_UP_TICKS, current.heldTicks - TICKS_UNDER_THE_FLASH - 1);
            }
            current.ticks++;
            current.running = true;
        }
    }

    public static void onLogout() {
        stopIrisWarmUp();
        end();
        KingCrimsonShatterReturnFade.close();
    }

    /** Whether the real terrain is still drawn: the ground must not vanish before the plates can take its place. */
    public static boolean holdsTerrain() {
        Session current = sync();
        if (current == null) {
            return false;
        }
        if (!current.iris) current.pollBuild();
        return !current.ready() && !current.failed && current.heldTicks < MAX_TERRAIN_HOLD_TICKS;
    }

    public static void onRenderFrameStart() {
        long start = System.nanoTime();
        long previous = previousFrameStart;
        previousFrameStart = start;
        advanceIrisWarmUp();
        if (session == null || !session.iris) return;
        Session current = sync();
        if (current != null && current.iris) current.beginIrisFrame(start, previous);
    }

    public static void onRenderFrameEnd() {
        Session current = session;
        if (current != null && current.iris && current.frameStartedAt != 0L) {
            long spent = System.nanoTime() - current.frameStartedAt;
            current.renderFrames.record(spent);
            if (current.bakedThisFrame) current.bakeRenderFrames.record(spent);
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            // Iris has finished shadows; invalidate Sodium before the main visibility pass.
            KingCrimsonShatterIrisPresentation.refreshTerrainVisibility();
            return;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            paintUniverse(event);
            return;
        }
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        Session current = sync();
        if (current == null) {
            return;
        }
        if (current.iris) {
            if (current.irisFailed || ClientRenderCompatibility.isIrisShadowPass()) return;
            long stageStart = System.nanoTime();
            current.irisVoidDrawn = false;
            try {
                current.irisVoidDrawn = KingCrimsonShatterIrisPresentation.drawVoid();
                if (!current.irisVoidDrawn) {
                    throw new IllegalStateException("Iris void was not submitted");
                }
                renderPlates(event, current);
            } catch (RuntimeException | LinkageError exception) {
                current.failIris(exception);
            } finally {
                current.irisEntitiesStage.record(System.nanoTime() - stageStart);
            }
        } else {
            renderPlates(event, current);
        }
    }

    // A pack that does not declare the void has shaded the shell as a dark wall: the universe goes over its final image.
    private static void paintUniverse(RenderLevelStageEvent event) {
        Session current = session;
        if (current == null) {
            KingCrimsonShatterReturnFade.paint(event.getModelViewMatrix(), event.getProjectionMatrix());
            return;
        }
        if (current.route != KingCrimsonShatterPackRoute.UNDECLARED || !current.irisVoidDrawn || current.irisFailed) {
            return;
        }
        KingCrimsonShatterUniverse.paintOverFinal(event.getModelViewMatrix(), event.getProjectionMatrix(),
                KingCrimsonShatterVoidDepth.mask(), KingCrimsonShatterVoidDepth.maskWidth(), KingCrimsonShatterVoidDepth.maskHeight(),
                1.0F);
    }

    private static void renderPlates(RenderLevelStageEvent event, Session current) {
        if (!current.ready()) return;
        float partial = current.paused() || !current.running
                ? 0.0F
                : event.getPartialTick().getGameTimeDeltaPartialTick(false);
        long start = System.nanoTime();
        current.renderer.render(
                event.getCamera(),
                event.getFrustum(),
                Minecraft.getInstance().renderBuffers().bufferSource(),
                current.player.getPosition(partial).add(0.0D, 1.1D, 0.0D),
                current.ticks + partial);
        current.recordFrame(start, System.nanoTime());
    }

    /** The final-target layer is a fallback only; it must not cover successful world rendering. */
    public static boolean useIrisOverlay() {
        if (!KingCrimsonTemporalClientState.localTimeErase()
                || !ClientRenderCompatibility.snapshot().irisShaderPackInUse()) {
            return false;
        }
        Session current = session;
        return current == null || !current.iris || current.irisFailed || current.failed || !current.irisVoidDrawn;
    }

    public static boolean drewIrisVoid() {
        Session current = session;
        return current != null && current.iris && current.irisVoidDrawn && !current.irisFailed
                && KingCrimsonTemporalClientState.localTimeErase();
    }

    /** The shatter's timeline tick of this frame, the one its plates are drawn at; -1 while no breaking runs. */
    public static float shakeTick(float partial) {
        Session current = session;
        if (current == null || !current.running || current.paused() || !current.ready()
                || !KingCrimsonTemporalClientState.localTimeErase()
                || KingCrimsonAddon.temporalState(current.player).eraseKind().isPassive()) {
            return -1.0F;
        }
        return current.ticks + partial;
    }

    private static Session sync() {
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null
                || player == null
                || !KingCrimsonTemporalClientState.localTimeErase()) {
            Session ended = session;
            end(ended != null && level == ended.level && player == ended.player);
            return null;
        }
        stopIrisWarmUp();
        KingCrimsonShatterPackRoute route = KingCrimsonShatterPackRouter.current();
        long startedAt = KingCrimsonAddon.temporalState(player).startedAt();
        if (session == null || session.level != level || session.startedAt != startedAt || session.route != route) {
            end();
            KingCrimsonShatterReturnFade.close();
            session = new Session(minecraft, level, player, startedAt, route);
        }
        KingCrimsonShatterIrisPresentation.syncCull(session.iris && !session.irisFailed);
        return session;
    }

    private static void end() {
        end(false);
    }

    /** @param returning the erase ended in the world it ran in, so the world comes back now */
    private static void end(boolean returning) {
        Session ended = session;
        if (ended != null) {
            session = null;
            try {
                ended.close();
            } finally {
                try {
                    if (returning && ended.fadesTheReturn()) {
                        KingCrimsonShatterReturnFade.begin(KingCrimsonShatterVoidDepth.handOverMask(),
                                KingCrimsonShatterVoidDepth.maskWidth(), KingCrimsonShatterVoidDepth.maskHeight());
                    }
                } finally {
                    try {
                        KingCrimsonShatterVoidDepth.close();
                    } finally {
                        try {
                            KingCrimsonShatterVoidTexture.close();
                        } finally {
                            KingCrimsonShatterIrisPresentation.syncCull(false);
                        }
                    }
                }
            }
        }
    }

    private static final class IrisWarmUp {
        private final ClientLevel level;
        private final LocalPlayer player;
        private final KingCrimsonShatterPlan plan;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private CompletableFuture<KingCrimsonShatterMesh> build;
        private KingCrimsonShatterRenderer renderer;

        private IrisWarmUp(ClientLevel level, LocalPlayer player, KingCrimsonShatterPlan plan, int range) {
            this.level = level;
            this.player = player;
            this.plan = plan;
            int y = player.getBlockY();
            KingCrimsonShatterBuilder first = new KingCrimsonShatterBuilder(level, plan, y, range, 1L, cancelled::get);
            KingCrimsonShatterBuilder second = new KingCrimsonShatterBuilder(level, plan, y, range, 2L, cancelled::get);
            build = CompletableFuture.supplyAsync(() -> {
                KingCrimsonShatterMesh mesh = first.build();
                if (mesh == null || cancelled.get()) return null;
                long firstMillis = mesh.buildMillis;
                mesh = null;
                mesh = second.build();
                if (mesh != null && !cancelled.get()) {
                    LOGGER.info("King Crimson shatter Iris warm-up: built in {} and {} ms", firstMillis, mesh.buildMillis);
                }
                return mesh;
            }, Util.backgroundExecutor());
        }

        /** Bake with the erase's real Iris vertex and upload path, without ever drawing the temporary mesh. */
        private boolean bakeNextSlice() {
            if (renderer == null) {
                if (!build.isDone()) return true;
                KingCrimsonShatterMesh mesh = build.join();
                build = null;
                if (mesh == null) return false;
                long gpuStart = System.nanoTime();
                KingCrimsonShatterVoidTexture.fill(Util.getNanos() * 1.0E-9D);
                KingCrimsonShatterVoidDepth.warmUp();
                LOGGER.info("King Crimson shatter Iris warm-up: void texture/program setup {} ms", ms(System.nanoTime() - gpuStart));
                renderer = new KingCrimsonShatterRenderer(plan, mesh, true);
            }
            renderer.bakeIrisSlice();
            if (!renderer.ready()) return true;
            KingCrimsonShatterRenderer.BakeTiming bake = renderer.bakeTiming();
            LOGGER.info("King Crimson shatter Iris warm-up: {} slices, {} uploads, {} ms CPU, {} ms elapsed; "
                            + "vertex writes {} ms, upload {} ms, max slice {} ms",
                    bake.slices(), bake.uploads(), ms(bake.cpuNanos()), ms(bake.elapsedNanos()),
                    ms(bake.writeNanos()), ms(bake.uploadNanos()), ms(bake.maxSliceNanos()));
            return false;
        }

        private void close() {
            cancelled.set(true);
            build = null;
            if (renderer != null) renderer.close();
        }
    }

    private static final class Session implements View {
        private final ClientLevel level;
        private final LocalPlayer player;
        private final long startedAt;
        private final KingCrimsonShatterPackRoute route;
        private final boolean iris;
        private final KingCrimsonShatterPlan plan;
        private final AtomicBoolean cancelled = new AtomicBoolean();
        private final int fpsAtPress;
        private final long createdAt = System.nanoTime();
        private CompletableFuture<KingCrimsonShatterMesh> build;
        private KingCrimsonShatterMesh mesh;
        private final long seed;
        private final float pressYaw;
        private final int originX;
        private final int originY;
        private final int originZ;
        private final int nearRange;
        private KingCrimsonShatterRenderer renderer;
        private boolean failed;
        private boolean irisFailed;
        private boolean irisVoidDrawn;
        private int heldTicks;
        private int ticks;
        private boolean running;
        private int timelineStartedAt;
        private long timelineStartedNanos;
        private int readyHeldTick = -1;
        private long readyNanos;
        private long frameStartedAt;
        private long activationInterval;
        private boolean bakedThisFrame;
        private final FrameTiming renderFrames = new FrameTiming();
        private final FrameTiming bakeRenderFrames = new FrameTiming();
        private final FrameTiming engineFrames = new FrameTiming();
        private final FrameTiming bakeEngineFrames = new FrameTiming();
        private final FrameTiming frameIntervals = new FrameTiming();
        private final FrameTiming bakeFrameIntervals = new FrameTiming();
        private final FrameTiming irisEntitiesStage = new FrameTiming();
        private KingCrimsonShatterRenderer.BakeTiming failedBake;
        private long frames;
        private long drawNanos;
        private long maxDrawNanos;
        private long maxLaterDrawNanos;
        private long firstFrameAt;
        private long lastFrameAt;
        private long earlyFrames;
        private long earlyEndAt;
        private long earlyDrawNanos;
        private int peakVertices;
        private int peakPlateDrawCalls;
        private int peakVelocityDrawCalls;

        private Session(Minecraft minecraft, ClientLevel level, LocalPlayer player, long startedAt, KingCrimsonShatterPackRoute route) {
            this.level = level;
            this.player = player;
            this.startedAt = startedAt;
            this.route = route;
            this.iris = route != KingCrimsonShatterPackRoute.NO_SHADERS;
            this.fpsAtPress = minecraft.getFps();
            int x = player.getBlockX();
            int y = player.getBlockY();
            int z = player.getBlockZ();
            this.seed = startedAt * 0x2545F4914F6CDD1DL + (((long) x & 0xFFFFFFL) << 24 | (long) z & 0xFFFFFFL);
            this.originX = x;
            this.originY = y;
            this.originZ = z;
            this.pressYaw = player.getYRot();
            int range = rangeOf(minecraft);
            this.nearRange = range;
            this.plan = KingCrimsonShatterPlan.create(x, z, seed, range, player.getYRot());
            // Read now, under the flash: the large strip would otherwise load on the wind's first frame.
            KingCrimsonShatterWind.prepare(minecraft);
            KingCrimsonShatterDiagnostics.nextErase();
            KingCrimsonShatterBuilder builder =
                    new KingCrimsonShatterBuilder(level, plan, y, range, seed, cancelled::get);
            this.build = CompletableFuture.supplyAsync(builder::build, Util.backgroundExecutor());
        }

        /** Whether the universe fades off the world it ran in: its route asks for it and its last frame drew the void. */
        private boolean fadesTheReturn() {
            return route.fadesTheReturn() && iris && !irisFailed && irisVoidDrawn && KingCrimsonShatterDiagnostics.returnFadeOn();
        }

        @Override
        public boolean paused() {
            KingCrimsonTemporalState state = KingCrimsonAddon.temporalState(player);
            return state.isErasePaused();
        }

        @Override
        public boolean ready() {
            return renderer != null && renderer.ready();
        }

        @Override
        public long startedAt() {
            return startedAt;
        }

        @Override
        public long createdAt() {
            return createdAt;
        }

        @Override
        public boolean iris() {
            return iris;
        }

        @Override
        public boolean failed() {
            return failed || irisFailed;
        }

        @Override
        public boolean running() {
            return running;
        }

        @Override
        public int ticks() {
            return ticks;
        }

        @Override
        public long seed() {
            return seed;
        }

        @Override
        public int originX() {
            return originX;
        }

        @Override
        public int originY() {
            return originY;
        }

        @Override
        public int originZ() {
            return originZ;
        }

        @Override
        public float pressYaw() {
            return pressYaw;
        }

        @Override
        public int nearRange() {
            return nearRange;
        }

        @Override
        public rotp.addons.kingcrimson.shatter.KingCrimsonShatterFarSurface.NearFootprint footprint() {
            KingCrimsonShatterMesh built = mesh;
            return built == null ? null : built.footprint;
        }

        @Override
        public long frameStartedAt() {
            return frameStartedAt;
        }

        @Override
        public boolean bakedThisFrame() {
            return bakedThisFrame;
        }

        private void beginIrisFrame(long start, long previous) {
            if (frameStartedAt != 0L) {
                long interval = start - frameStartedAt;
                frameIntervals.record(interval);
                if (bakedThisFrame) bakeFrameIntervals.record(interval);
                // The prior frame's engine timer reaches the final blit; the Post event does not.
                long engine = Minecraft.getInstance().getFrameTimeNs();
                if (engine > 0L) {
                    engineFrames.record(engine);
                    if (bakedThisFrame) bakeEngineFrames.record(engine);
                }
            } else if (previous != 0L && createdAt - previous >= 0L) {
                activationInterval = start - previous;
            }
            frameStartedAt = start;
            bakedThisFrame = false;
            if (irisFailed) return;
            pollBuild();
            if (renderer == null || ready()) return;
            bakedThisFrame = true;
            try {
                // Publish only the complete bake, before this frame decides whether to draw the real terrain.
                renderer.bakeIrisSliceFor(previous != 0L ? start - previous : 0L);
                if (ready()) {
                    readyHeldTick = heldTicks;
                    readyNanos = System.nanoTime() - createdAt;
                }
            } catch (RuntimeException | LinkageError failure) {
                failIris(failure);
            }
        }

        private void pollBuild() {
            CompletableFuture<KingCrimsonShatterMesh> pending = build;
            if (pending == null || !pending.isDone()) {
                return;
            }
            build = null;
            try {
                mesh = pending.join();
                if (mesh != null) {
                    renderer = new KingCrimsonShatterRenderer(plan, mesh, iris);
                } else {
                    failed = true;
                }
            } catch (RuntimeException | LinkageError exception) {
                if (iris) {
                    failIris(exception);
                    return;
                }
                failed = true;
                LOGGER.warn("Time Erase could not break the ground up; it goes on without the plates.", exception);
            }
        }

        private void failIris(Throwable exception) {
            if (irisFailed) return;
            irisFailed = true;
            failed = true;
            irisVoidDrawn = false;
            cancelled.set(true);
            build = null;
            try {
                if (renderer != null) {
                    failedBake = renderer.bakeTiming();
                    try {
                        renderer.close();
                    } catch (RuntimeException | LinkageError cleanup) {
                        exception.addSuppressed(cleanup);
                    }
                }
            } finally {
                renderer = null;
                mesh = null;
                KingCrimsonShatterIrisPresentation.syncCull(false);
            }
            LOGGER.warn("King Crimson shatter could not draw through Iris; using the Time Erase overlay.", exception);
        }

        private void recordFrame(long start, long end) {
            long spent = end - start;
            frames++;
            drawNanos += spent;
            maxDrawNanos = Math.max(maxDrawNanos, spent);
            if (frames > 5L) {
                maxLaterDrawNanos = Math.max(maxLaterDrawNanos, spent);
            }
            if (ticks < KingCrimsonShatterPlan.END_TICK) {
                earlyFrames++;
                earlyEndAt = end;
                earlyDrawNanos += spent;
            }
            if (firstFrameAt == 0L) {
                firstFrameAt = start;
            }
            lastFrameAt = end;
            peakVertices = Math.max(peakVertices, renderer.drawnVertices());
            peakPlateDrawCalls = Math.max(peakPlateDrawCalls, renderer.plateDrawCalls());
            peakVelocityDrawCalls = Math.max(peakVelocityDrawCalls, renderer.velocityDrawCalls());
        }

        // How the plates of this erase ended: their last ticks are the plan's, so this reads what was drawn.
        private void logTheTears() {
            int plates = mesh.plates.length;
            float[] ends = new float[plates];
            for (int i = 0; i < plates; i++) {
                ends[i] = plan.retireTick(mesh.plates[i].id);
            }
            Arrays.sort(ends);
            StringBuilder left = new StringBuilder();
            for (int tick = 40; tick <= (int) KingCrimsonShatterPlan.END_TICK + 5; tick += 10) {
                int gone = 0;
                while (gone < plates && ends[gone] <= tick) {
                    gone++;
                }
                left.append(tick).append(':').append(plates - gone).append(' ');
            }
            int most = 0;
            for (int first = 0, last = 0; last < plates; last++) {
                while (ends[last] - ends[first] > 2.0F) {
                    first++;
                }
                most = Math.max(most, last - first + 1);
            }
            LOGGER.info(
                    "King Crimson shatter tears: range {}, first plate gone at tick {}, last at tick {}, plates left {}, most in 2 ticks {}",
                    plan.range(),
                    plates > 0 ? String.format("%.1f", ends[0]) : "-",
                    plates > 0 ? String.format("%.1f", ends[plates - 1]) : "-",
                    left.toString().trim(),
                    most);
        }

        private void logResult() {
            if (mesh != null && frames > 0L) {
                double seconds = Math.max(1.0E-3D, (lastFrameAt - firstFrameAt) / 1.0E9D);
                double earlySeconds = Math.max(1.0E-3D, (earlyEndAt - firstFrameAt) / 1.0E9D);
                LOGGER.info(
                        "King Crimson shatter: {} plates, {}+{}+{} vertices, {} cells went coarse, {} small blocks, {} in view at tick 36, built in {} ms, baked in {} ms, timeline from held tick {}; pressed at {} fps; until the ground is gone {} fps, draw {} ms; "
                                + "whole erase {} fps, draw {} ms, at most {} ms, at most {} ms after the first frames, at most {} vertices in a frame",
                        mesh.plates.length,
                        mesh.solidVertices,
                        mesh.cutoutVertices,
                        mesh.flatVertices,
                        mesh.coarseCells,
                        mesh.debrisLights.length,
                        renderer.debrisInView(),
                        mesh.buildMillis,
                        renderer.bakeMillis(),
                        timelineStartedAt,
                        fpsAtPress,
                        String.format("%.1f", earlyFrames / earlySeconds),
                        String.format("%.2f", earlyDrawNanos / (double) Math.max(1L, earlyFrames) / 1.0E6D),
                        String.format("%.1f", frames / seconds),
                        String.format("%.2f", drawNanos / (double) frames / 1.0E6D),
                        String.format("%.2f", maxDrawNanos / 1.0E6D),
                        String.format("%.2f", maxLaterDrawNanos / 1.0E6D),
                        peakVertices);
                logTheTears();
                if (iris) {
                    LOGGER.info("King Crimson shatter Iris: peak plate draws {}, peak draws with at_velocity {}",
                            peakPlateDrawCalls, peakVelocityDrawCalls);
                }
            }
            if (iris) logIrisTiming();
        }

        private void logIrisTiming() {
            KingCrimsonShatterRenderer.BakeTiming bake = renderer != null ? renderer.bakeTiming() : failedBake;
            if (bake != null) {
                LOGGER.info("King Crimson shatter Iris bake: {} slices, {} uploads, {} ms CPU, {} ms elapsed; "
                                + "vertex writes {} ms, upload {} ms; max slice {} ms (max write {} ms, max upload {} ms); "
                                + "ready held tick {} ({} ms from session start), timeline held tick {} ({} ms from session start)",
                        bake.slices(), bake.uploads(), ms(bake.cpuNanos()), ms(bake.elapsedNanos()),
                        ms(bake.writeNanos()), ms(bake.uploadNanos()), ms(bake.maxSliceNanos()),
                        ms(bake.maxSliceWriteNanos()), ms(bake.maxSliceUploadNanos()),
                        readyHeldTick >= 0 ? readyHeldTick : "-", readyHeldTick >= 0 ? ms(readyNanos) : "-",
                        running ? timelineStartedAt : "-", running ? ms(timelineStartedNanos) : "-");
            }
            LOGGER.info("King Crimson shatter Iris timing: AFTER_ENTITIES void/plates CPU {}; "
                            + "render frame Pre-to-Post {} (bake frames {}); Minecraft render-through-blit {} (bake frames {}); "
                            + "frame interval Pre-to-next-Pre {} (bake frames {}), activation interval {}",
                    irisEntitiesStage.summary(), renderFrames.summary(), bakeRenderFrames.summary(),
                    engineFrames.summary(), bakeEngineFrames.summary(), frameIntervals.summary(), bakeFrameIntervals.summary(),
                    activationInterval > 0L ? ms(activationInterval) + " ms" : "unmeasured");
        }

        private void close() {
            cancelled.set(true);
            try {
                logResult();
            } finally {
                try {
                    if (renderer != null) renderer.close();
                } finally {
                    mesh = null;
                    renderer = null;
                    build = null;
                }
            }
        }
    }

    private static String ms(long nanos) {
        return String.format(Locale.ROOT, "%.2f", nanos / 1.0E6D);
    }

    private static final class FrameTiming {
        private long maxNanos;
        private int samples;
        private int over60;

        private void record(long nanos) {
            samples++;
            if (nanos > 60_000_000L) over60++;
            maxNanos = Math.max(maxNanos, nanos);
        }

        private String summary() {
            return samples == 0 ? "unmeasured" : ms(maxNanos) + " ms max (" + over60 + "/" + samples + " over 60 ms)";
        }
    }
}
