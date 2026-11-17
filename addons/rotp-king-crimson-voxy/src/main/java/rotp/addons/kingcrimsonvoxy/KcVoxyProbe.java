package rotp.addons.kingcrimsonvoxy;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.FloatBuffer;
import java.util.Locale;

import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL45C;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import rotp.addons.kingcrimson.client.KingCrimsonTemporalClientState;

/**
 * Coverage from real frames, for one diagnostic run: LOD pixels and what the void left of them, against the last normal frame.
 * Reads depth synchronously, so it must be off when timing is measured. Enabled by -Drotp.kcvoxy.probe=true.
 */
final class KcVoxyProbe {
    static final boolean ENABLED = Boolean.getBoolean("rotp.kcvoxy.probe");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int NORMAL_EVERY = 30;
    private static final int ERASE_EVERY = 4;
    private static final float VOID_DEPTH = 0.9999990F;

    static volatile long renderOpaqueCalls;

    private static Access access;
    private static boolean failed;
    private static FloatBuffer lod;
    private static FloatBuffer main;
    private static int capacity;
    private static long[] baseline;
    private static long[] previousLod;
    private static long[] baselineSolid;
    private static int baselineCount;
    private static int baselineWidth;
    private static int baselineHeight;
    private static Vec3 baselineCamera;
    private static float baselineYaw;
    private static float baselinePitch;
    private static long frame;
    private static long callsAtLast;
    private static long frameAtLast;
    private static boolean erasing;
    private static double worstAny;
    private static double worstVisible;
    private static int eraseSamples;
    private static int movedSamples;

    private KcVoxyProbe() {}

    static void onRenderOpaque() {
        renderOpaqueCalls++;
    }

    static void afterEntities(Camera camera) {
        if (!ENABLED || failed) {
            return;
        }
        frame++;
        boolean erase = KingCrimsonTemporalClientState.localTimeErase();
        if (erasing != erase) {
            if (erasing) {
                LOGGER.info("KcVoxy probe erase end: samples={} movedSamples={} worst coverage any={} visible={}",
                        eraseSamples, movedSamples, percent(worstAny), percent(worstVisible));
            }
            erasing = erase;
            worstAny = worstVisible = 1.0D;
            eraseSamples = movedSamples = 0;
        }
        if (frame % (erase ? ERASE_EVERY : NORMAL_EVERY) != 0L) {
            return;
        }
        try {
            if (access == null) {
                access = Access.bind();
            }
            sample(camera, erase);
        } catch (Throwable failure) {
            if (failure instanceof Error error && !(failure instanceof LinkageError)) {
                throw error;
            }
            failed = true;
            LOGGER.warn("KcVoxy probe stopped.", failure);
        }
    }

    private static void sample(Camera camera, boolean erase) throws Throwable {
        Object system = (Object) access.system.invokeExact();
        if (system == null) {
            return;
        }
        Object viewport = (Object) access.viewport.invokeExact(system);
        int lodTexture = (int) access.depthTexture.invokeExact(system);
        if (viewport == null || lodTexture == 0) {
            LOGGER.info("KcVoxy probe: no viewport or no LOD depth (texture {})", lodTexture);
            return;
        }
        int width = (int) access.width.invokeExact(viewport);
        int height = (int) access.height.invokeExact(viewport);
        int frameId = (int) access.frameId.invokeExact(viewport);
        int build = (int) access.buildId.invokeExact(viewport);
        int mainTexture = Minecraft.getInstance().getMainRenderTarget().getDepthTextureId();
        if (width <= 0 || height <= 0) {
            return;
        }
        int pixels = width * height;
        if (pixels > capacity) {
            if (lod != null) {
                MemoryUtil.memFree(lod);
                MemoryUtil.memFree(main);
            }
            lod = MemoryUtil.memAllocFloat(pixels);
            main = MemoryUtil.memAllocFloat(pixels);
            capacity = pixels;
        }
        lod.clear().limit(pixels);
        main.clear().limit(pixels);
        GL45C.glGetTextureSubImage(lodTexture, 0, 0, 0, 0, width, height, 1, GL11C.GL_DEPTH_COMPONENT, GL11C.GL_FLOAT, lod);
        GL45C.glGetTextureSubImage(mainTexture, 0, 0, 0, 0, width, height, 1, GL11C.GL_DEPTH_COMPONENT, GL11C.GL_FLOAT, main);

        int lodPixels = 0;
        int visible = 0;
        int mainFar = 0;
        long[] mask = new long[(pixels + 63) >> 6];
        long[] lodMask = new long[mask.length];
        long[] solid = new long[mask.length];
        int holes = 0;
        int kept = 0;
        int keptVisible = 0;
        int interior = 0;
        int keptInterior = 0;
        boolean same = baseline != null && baselineWidth == width && baselineHeight == height;
        for (int i = 0; i < pixels; i++) {
            boolean hasLod = lod.get(i) < 1.0F;
            boolean far = main.get(i) == 1.0F;
            // Ground or LOD on the last normal frame, and now nothing but the void or the sky: a hole in the world.
            boolean something = hasLod || main.get(i) < VOID_DEPTH;
            if (something) {
                solid[i >> 6] |= 1L << i;
            } else if (same && insideMask(baselineSolid, i, width, pixels)) {
                holes++;
            }
            lodPixels += hasLod ? 1 : 0;
            if (hasLod) {
                lodMask[i >> 6] |= 1L << i;
            }
            mainFar += far ? 1 : 0;
            if (hasLod && far) {
                visible++;
                mask[i >> 6] |= 1L << i;
            }
            if (same && (baseline[i >> 6] & 1L << i) != 0L) {
                // Inside the baseline: all four neighbours were LOD too, so jitter at an edge cannot count as a loss.
                boolean inner = i % width != 0 && i % width != width - 1 && i >= width && i < pixels - width
                        && bit(baseline, i - 1) && bit(baseline, i + 1) && bit(baseline, i - width) && bit(baseline, i + width);
                interior += inner ? 1 : 0;
                keptInterior += inner && hasLod ? 1 : 0;
                if (hasLod) {
                    kept++;
                    keptVisible += far ? 1 : 0;
                }
            }
        }
        int flips = -1;
        if (previousLod != null && previousLod.length == lodMask.length) {
            flips = 0;
            for (int w = 0; w < lodMask.length; w++) {
                flips += Long.bitCount(previousLod[w] ^ lodMask[w]);
            }
        }
        previousLod = lodMask;
        Vec3 position = camera.getPosition();
        boolean moved = baselineCamera == null || position.distanceToSqr(baselineCamera) > 1.0E-4D
                || Math.abs(camera.getYRot() - baselineYaw) > 0.01F || Math.abs(camera.getXRot() - baselinePitch) > 0.01F;
        double any = baselineCount == 0 ? 0.0D : kept / (double) baselineCount;
        double seen = baselineCount == 0 ? 0.0D : keptVisible / (double) baselineCount;
        long calls = renderOpaqueCalls;
        long frames = frame - frameAtLast;
        double perFrame = frames == 0L ? 0.0D : (calls - callsAtLast) / (double) frames;
        callsAtLast = calls;
        frameAtLast = frame;
        if (erase) {
            eraseSamples++;
            if (moved) {
                movedSamples++;
            } else {
                worstAny = Math.min(worstAny, any);
                worstVisible = Math.min(worstVisible, seen);
            }
        }
        LOGGER.info(String.format(Locale.ROOT,
                "KcVoxy probe %s frame=%d size=%dx%d renderOpaque/frame=%.2f viewportFrame=%d build=%d lod=%d mainFar=%d visible=%d"
                        + " baseline=%d keptAny=%d keptVisible=%d coverageAny=%s coverageVisible=%s moved=%s live=%s interior=%d keptInterior=%d coverageInterior=%s lodFlips=%d holes=%d",
                erase ? "ERASE" : "normal", frame, width, height, perFrame, frameId, build, lodPixels, mainFar, visible,
                baselineCount, kept, keptVisible, percent(any), percent(seen), moved, KcVoxyFar.live(), interior, keptInterior,
                percent(interior == 0 ? 0.0D : keptInterior / (double) interior), flips, holes));
        if (!erase) {
            baseline = mask;
            baselineSolid = solid;
            baselineCount = visible;
            baselineWidth = width;
            baselineHeight = height;
            baselineCamera = position;
            baselineYaw = camera.getYRot();
            baselinePitch = camera.getXRot();
        }
    }

    private static boolean insideMask(long[] mask, int i, int width, int pixels) {
        return mask != null && bit(mask, i) && i % width != 0 && i % width != width - 1 && i >= width && i < pixels - width
                && bit(mask, i - 1) && bit(mask, i + 1) && bit(mask, i - width) && bit(mask, i + width);
    }

    private static boolean bit(long[] mask, int index) {
        return (mask[index >> 6] & 1L << index) != 0L;
    }

    private static String percent(double share) {
        return String.format(Locale.ROOT, "%.2f%%", share * 100.0D);
    }

    private record Access(MethodHandle system, MethodHandle viewport, MethodHandle depthTexture, MethodHandle width,
            MethodHandle height, MethodHandle frameId, MethodHandle buildId) {
        private static Access bind() throws ReflectiveOperationException {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            ClassLoader loader = KcVoxyProbe.class.getClassLoader();
            String prefix = "me.cortex.voxy.client.core.";
            Class<?> renderer = Class.forName(prefix + "VoxyRenderSystem", true, loader);
            Class<?> accessor = Class.forName(prefix + "IGetVoxyRenderSystem", true, loader);
            Class<?> viewport = Class.forName(prefix + "rendering.Viewport", true, loader);
            return new Access(
                    lookup.findStatic(accessor, "getNullable", MethodType.methodType(renderer))
                            .asType(MethodType.methodType(Object.class)),
                    lookup.findVirtual(renderer, "getViewport", MethodType.methodType(viewport))
                            .asType(MethodType.methodType(Object.class, Object.class)),
                    lookup.findVirtual(renderer, "getSableOcclusionDepthTexture", MethodType.methodType(int.class))
                            .asType(MethodType.methodType(int.class, Object.class)),
                    lookup.findGetter(viewport, "width", int.class).asType(MethodType.methodType(int.class, Object.class)),
                    lookup.findGetter(viewport, "height", int.class).asType(MethodType.methodType(int.class, Object.class)),
                    lookup.findGetter(viewport, "frameId", int.class).asType(MethodType.methodType(int.class, Object.class)),
                    lookup.findGetter(viewport, "prevBuildFrameId", int.class).asType(MethodType.methodType(int.class, Object.class)));
        }
    }
}
