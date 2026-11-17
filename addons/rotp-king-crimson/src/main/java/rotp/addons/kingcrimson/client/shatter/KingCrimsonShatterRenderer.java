package rotp.addons.kingcrimson.client.shatter;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPlan;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterMotion;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Draws one erase's broken ground. The plates are baked once into vertex buffers on the render thread, one draw range
 * per plate, each drawn with its own model-view matrix through the entity programs on the block atlas. Cracks, the wind
 * and rising blocks are few and go through the buffer source. Everything is a function of the tick, so a frozen
 * erase stands still simply because its tick does not move.
 */
@OnlyIn(Dist.CLIENT)
final class KingCrimsonShatterRenderer {
    private static final org.slf4j.Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    private static long buffersCreated;
    private static long buffersClosed;
    private static long liveBuffers;
    private static final ResourceLocation WHITE = ResourceLocation.withDefaultNamespace("textures/misc/white.png");
    // The entity shaders with the terrain's mipmapped atlas: without mipmaps far grass sparkles as the plates move.
    private static final RenderType PLATE_SOLID = RenderType.create(
            "rotp_kingcrimson_plate_solid",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_SOLID_SHADER)
                    .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(false));
    private static final RenderType PLATE_CUTOUT = RenderType.create(
            "rotp_kingcrimson_plate_cutout",
            DefaultVertexFormat.NEW_ENTITY,
            VertexFormat.Mode.QUADS,
            1536,
            false,
            false,
            RenderType.CompositeState.builder()
                    .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                    .setTextureState(RenderStateShard.BLOCK_SHEET_MIPPED)
                    .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                    .setCullState(RenderStateShard.NO_CULL)
                    .setLightmapState(RenderStateShard.LIGHTMAP)
                    .setOverlayState(RenderStateShard.OVERLAY)
                    .createCompositeState(false));
    private static final RenderType FLAT = RenderType.entityCutoutNoCull(WHITE);

    private static final int FULL_BRIGHT = LightTexture.FULL_BRIGHT;
    private static final int NO_OVERLAY = OverlayTexture.NO_OVERLAY;
    // The void shows through a crack.
    private static final int CRACK_COLOR = 0xFF120516;
    // Once the plates beside it start to part the line widens with them, then narrows away inside the real gap.
    private static final float CRACK_PARTING_TICKS = 6.0F;
    // Close to the camera the gap can be seen wider than the line within a few ticks: the line must not hang over the trench.
    private static final float CRACK_PARTS_QUICKLY = 0.5F;
    private static final float CRACK_PARTS_QUICKLY_WITHIN_BLOCKS = 40.0F;
    private static final float CRACK_STEEPEST_STEP_BLOCKS = 1.5F;
    private static final float CRACK_WIDENS_BY = 2.5F;
    // Right under the camera the thin line is wide enough: the feet of a user in first person would get dark bars.
    private static final float CRACK_WIDENS_FULLY_AT_BLOCKS = 18.0F;
    private static final float CRACK_HALF_WIDTH = 0.018F;
    private static final float CRACK_HALF_WIDTH_PER_BLOCK = 0.001F;
    // A plate between the camera and the user gets out of the way in this many ticks.
    private static final float VIEW_GUARD_TICKS = 5.0F;
    // Until a plate has gone some way it is only the real ground, which the camera has already dealt with.
    private static final float VIEW_GUARD_FROM_AGE = 14.0F;
    private static final float VIEW_GUARD_MARGIN = 0.35F;
    private static final float[][] CUBE = {
        {0, 0, 1, 0, 0, 0, 1, 0, 0, 1, 0, 1},
        {0, 1, 0, 0, 1, 1, 1, 1, 1, 1, 1, 0},
        {1, 1, 0, 1, 0, 0, 0, 0, 0, 0, 1, 0},
        {0, 1, 1, 0, 0, 1, 1, 0, 1, 1, 1, 1},
        {0, 1, 0, 0, 0, 0, 0, 0, 1, 0, 1, 1},
        {1, 1, 1, 1, 0, 1, 1, 0, 0, 1, 1, 0},
    };
    // Indexed by Direction.ordinal(), then the cut wall. A diagonal cut's wall is a staircase of blocks, and the entity
    // lights put north and south at 0.74 and east and west at 0.5, which reads as stripes: all of a cut's wall takes one.
    private static final float[][] FACE_NORMALS = {
        {0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}, {0, 0, -1},
    };
    private static final float DEBRIS_COUNT_TICK = 36.0F;
    private static final int IRIS_SLICE_QUADS = 40 * 1024;
    // Finish the current plate when possible; the quad cap also bounds a single unusually large plate.
    private static final long IRIS_SLICE_TARGET_NANOS = 24_000_000L;
    private static final float SLICE_TUNED_FRAME_NANOS = 40_000_000.0F;
    private static final float MOST_SLICE_SCALE = 5.0F;
    // Reserve room for Iris's extra attributes and the AR padding without depending on their format classes.
    private static final int IRIS_STAGING_BYTES_PER_VERTEX = 64;

    private final KingCrimsonShatterPlan plan;
    private final KingCrimsonShatterMesh mesh;
    private final boolean iris;
    private final KingCrimsonShatterPlan.Pose[] poses;
    private final boolean[] visible;
    private final int[] keptBuckets;
    private final float[] viewGuardTick;
    private final BakedLayer solid;
    private final BakedLayer cutout;
    private final BakedLayer flat;
    private final BakedLayer[] layers;
    // Each plate's transform relative to the erase's origin, with the view up to that origin: for this frame and the
    // last, because a renderer that wants motion vectors needs M_now * c - M_prev * c at a plate's centre.
    private Matrix4f[] transforms;
    private Matrix4f[] previousTransforms;
    private final Matrix4f originView = new Matrix4f();
    private final Matrix4f previousOriginView = new Matrix4f();
    private final Matrix4f plateView = new Matrix4f();
    private final Matrix3f plateNormal = new Matrix3f();
    private final float[] normalValues = new float[9];
    private final Vector3f velocity = new Vector3f();
    private final Vector3f previousPivot = new Vector3f();
    private int nextBakeLayer;
    private float sliceScale = 1.0F;
    private int bakeSlices;
    private int bakeUploads;
    private long firstBakeAt;
    private long lastBakeAt;
    private long bakeNanos;
    private long vertexWriteNanos;
    private long uploadNanos;
    private long maxSliceNanos;
    private long maxSliceWriteNanos;
    private long maxSliceUploadNanos;
    private boolean hasPreviousFrame;
    private int drawnVertices;
    private int plateDrawCalls;
    private int velocityDrawCalls;
    private int debrisInView;

    /** Vanilla bakes immediately; Iris prepares the draw ranges and bakes at frame boundaries. */
    KingCrimsonShatterRenderer(KingCrimsonShatterPlan plan, KingCrimsonShatterMesh mesh, boolean iris) {
        this.plan = plan;
        this.mesh = mesh;
        this.iris = iris;
        int plates = mesh.plates.length;
        poses = new KingCrimsonShatterPlan.Pose[plates];
        transforms = new Matrix4f[plates];
        previousTransforms = new Matrix4f[plates];
        for (int i = 0; i < plates; i++) {
            poses[i] = new KingCrimsonShatterPlan.Pose();
            transforms[i] = new Matrix4f();
            previousTransforms[i] = new Matrix4f();
        }
        visible = new boolean[plates];
        keptBuckets = new int[plates];
        viewGuardTick = new float[plates];
        Arrays.fill(viewGuardTick, Float.NaN);
        long start = System.nanoTime();
        solid = new BakedLayer(mesh.plates, plate -> plate.solid);
        cutout = new BakedLayer(mesh.plates, plate -> plate.cutout);
        flat = new BakedLayer(mesh.plates, plate -> plate.flat);
        layers = new BakedLayer[] {solid, cutout, flat};
        if (iris) return;
        try {
            for (BakedLayer layer : layers) {
                if (!layer.ready()) bakeChunk(layer, layer.totalQuads(), start);
            }
        } catch (RuntimeException | LinkageError failure) {
            try {
                close();
            } catch (RuntimeException | LinkageError cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        recordBakeSlice(start, 0L, 0L);
        finishBake();
    }

    boolean ready() {
        return nextBakeLayer == layers.length;
    }

    /** Only the uploaded VBOs survive this call. Capture and native staging are scoped to each upload. */
    void bakeIrisSlice() {
        bakeIrisSlice(IRIS_SLICE_QUADS * 4);
    }

    /**
     * On a machine whose frames are long anyway a slice can be longer in step, or one slice a frame holds the terrain for seconds
     * (2 s at 7 fps in a forest). A frame in the rate the slices were tuned for is not touched.
     */
    void bakeIrisSliceFor(long frameNanos) {
        sliceScale = Math.max(1.0F, Math.min(MOST_SLICE_SCALE, (float) frameNanos / SLICE_TUNED_FRAME_NANOS));
        try {
            bakeIrisSlice((int) (IRIS_SLICE_QUADS * sliceScale) * 4);
        } finally {
            sliceScale = 1.0F;
        }
    }

    void bakeIrisSlice(int vertexBudget) {
        RenderSystem.assertOnRenderThread();
        long start = System.nanoTime();
        long previousWrite = vertexWriteNanos;
        long previousUpload = uploadNanos;
        int sliceQuads = Math.min((int) (IRIS_SLICE_QUADS * sliceScale), vertexBudget / 4);
        if (sliceQuads <= 0) return;
        int left = sliceQuads;
        try {
            while (!ready()) {
                BakedLayer layer = layers[nextBakeLayer];
                layer.skipEmptyPlates();
                if (layer.ready()) {
                    nextBakeLayer++;
                    continue;
                }
                if (left < sliceQuads && (layer.nextPlateQuads() > left
                        || System.nanoTime() - start >= IRIS_SLICE_TARGET_NANOS * sliceScale)) {
                    break;
                }
                left -= bakeChunk(layer, left, start);
                if (!layer.ready()) break;
                nextBakeLayer++;
            }
            if (ready()) finishBake();
        } finally {
            recordBakeSlice(start, previousWrite, previousUpload);
        }
    }

    private void finishBake() {
        nextBakeLayer = layers.length;
        for (KingCrimsonShatterMesh.Plate plate : mesh.plates) {
            plate.releaseGeometry();
        }
    }

    int bakedVertices() {
        return (solid.bakedQuads + cutout.bakedQuads + flat.bakedQuads) * 4;
    }

    long bakeMillis() {
        return bakeNanos / 1_000_000L;
    }

    BakeTiming bakeTiming() {
        return new BakeTiming(bakeSlices, bakeUploads, bakeNanos, vertexWriteNanos, uploadNanos,
                maxSliceNanos, maxSliceWriteNanos, maxSliceUploadNanos, lastBakeAt - firstBakeAt);
    }

    private void recordBakeSlice(long start, long previousWrite, long previousUpload) {
        if (bakeSlices++ == 0) firstBakeAt = start;
        lastBakeAt = System.nanoTime();
        long spent = lastBakeAt - start;
        bakeNanos += spent;
        maxSliceNanos = Math.max(maxSliceNanos, spent);
        maxSliceWriteNanos = Math.max(maxSliceWriteNanos, vertexWriteNanos - previousWrite);
        maxSliceUploadNanos = Math.max(maxSliceUploadNanos, uploadNanos - previousUpload);
    }

    /** How far a plate's pivot moved on the screen's axes since the last frame, camera included, for motion vectors. */
    Vector3f pivotMotion(int plate, Vector3f out) {
        return KingCrimsonShatterMotion.pivotDelta(
                hasPreviousFrame, originView, transforms[plate], previousOriginView, previousTransforms[plate],
                out, previousPivot);
    }

    int drawnVertices() {
        return drawnVertices;
    }

    int plateDrawCalls() {
        return plateDrawCalls;
    }

    int velocityDrawCalls() {
        return velocityDrawCalls;
    }

    int debrisInView() {
        return debrisInView;
    }

    void close() {
        Throwable failure = null;
        for (BakedLayer layer : layers) {
            for (BakedChunk chunk : layer.chunks) {
                try {
                    chunk.buffer.close();
                    ++buffersClosed;
                    --liveBuffers;
                } catch (RuntimeException | LinkageError cleanup) {
                    if (failure == null) failure = cleanup;
                    else if (failure != cleanup) failure.addSuppressed(cleanup);
                }
            }
            layer.chunks.clear();
        }
        if (iris) LOGGER.info("King Crimson shatter VBO ownership: created={} closed={} live={}",
                buffersCreated, buffersClosed, liveBuffers);
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof LinkageError error) throw error;
    }

    private int bakeChunk(BakedLayer layer, int budget, long frameStart) {
        int firstQuad = layer.bakedQuads;
        int firstPlate = layer.nextPlate;
        int maxQuads = Math.min(budget, layer.totalQuads() - firstQuad);
        int stride = DefaultVertexFormat.NEW_ENTITY.getVertexSize();
        if (iris) stride = Math.max(stride, IRIS_STAGING_BYTES_PER_VERTEX);
        long writeStart = System.nanoTime();
        try (ByteBufferBuilder bytes = new ByteBufferBuilder(maxQuads * 4 * stride)) {
            MeshData data;
            try {
                BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.NEW_ENTITY);
                if (iris) {
                    KingCrimsonShatterIrisPresentation.withEntityId(
                            KingCrimsonShatterIrisConstants.PLATE_EMISSIVE_ID, light -> {
                                writeChunk(layer, builder, maxQuads, frameStart, light);
                                return null;
                            });
                } else {
                    writeChunk(layer, builder, maxQuads, frameStart, -1);
                }
                data = builder.buildOrThrow();
            } finally {
                vertexWriteNanos += System.nanoTime() - writeStart;
            }
            long uploadStart = System.nanoTime();
            VertexBuffer buffer = null;
            try {
                try (data) {
                    buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    ++buffersCreated;
                    ++liveBuffers;
                    try {
                        buffer.bind();
                        buffer.upload(data);
                        if (iris && bakeUploads == 0) {
                            LOGGER.info("King Crimson Iris upload format: stride={} vertices={}",
                                    data.drawState().format().getVertexSize(), data.drawState().vertexCount());
                        }
                    } finally {
                        VertexBuffer.unbind();
                    }
                }
                int endPlate = layer.nextPlate + (layer.bakedQuads > layer.firstQuad[layer.nextPlate] ? 1 : 0);
                layer.chunks.add(new BakedChunk(buffer, firstPlate, endPlate, firstQuad, layer.bakedQuads));
                bakeUploads++;
            } catch (RuntimeException | LinkageError failure) {
                if (buffer != null) {
                    try {
                        buffer.close();
                        ++buffersClosed;
                        --liveBuffers;
                    } catch (RuntimeException | LinkageError cleanup) {
                        failure.addSuppressed(cleanup);
                    }
                }
                throw failure;
            } finally {
                uploadNanos += System.nanoTime() - uploadStart;
            }
        }
        return layer.bakedQuads - firstQuad;
    }

    private void writeChunk(BakedLayer layer, BufferBuilder builder, int maxQuads, long frameStart, int lightOverride) {
        int left = maxQuads;
        boolean ids = iris && KingCrimsonShatterIrisPresentation.readsEntityIds();
        boolean wide = ids && KingCrimsonShatterPackRouter.widePlateLight();
        while (left > 0) {
            layer.skipEmptyPlates();
            if (layer.ready()) break;
            int plateQuads = layer.nextPlateQuads();
            if (iris && left < maxQuads && (plateQuads > left
                    || System.nanoTime() - frameStart >= IRIS_SLICE_TARGET_NANOS * sliceScale)) {
                break;
            }
            int count = Math.min(plateQuads, left);
            int start = layer.bakedQuads - layer.firstQuad[layer.nextPlate];
            writeQuads(builder, layer.layerOf.apply(mesh.plates[layer.nextPlate]), start, count, lightOverride, ids, wide);
            layer.bakedQuads += count;
            left -= count;
        }
        layer.skipEmptyPlates();
    }

    private static void writeQuads(
            BufferBuilder builder, KingCrimsonShatterMesh.Layer layer, int start, int count, int lightOverride, boolean ids,
            boolean wide) {
        float[] vertices = layer.vertices;
        int[] colors = layer.colors;
        byte[] normals = layer.normals;
        int[] lights = layer.lights;
        byte[] levels = layer.levels;
        int vertex = start * 4;
        int at = vertex * KingCrimsonShatterMesh.Layer.STRIDE;
        int written = -1;
        for (int quad = start, end = start + count; quad < end; quad++) {
            float[] normal = FACE_NORMALS[normals[quad]];
            int light = lightOverride >= 0 ? lightOverride : lights[quad];
            if (ids && levels[quad] != written) {
                written = levels[quad];
                KingCrimsonShatterIrisPresentation.nextEntityId(KingCrimsonShatterIrisConstants.plateId(written, wide));
            }
            for (int corner = 0; corner < 4; corner++) {
                builder.addVertex(
                        vertices[at],
                        vertices[at + 1],
                        vertices[at + 2],
                        colors[vertex],
                        vertices[at + 3],
                        vertices[at + 4],
                        NO_OVERLAY,
                        light,
                        normal[0],
                        normal[1],
                        normal[2]);
                at += KingCrimsonShatterMesh.Layer.STRIDE;
                vertex++;
            }
        }
    }

    void render(Camera camera, Frustum frustum, MultiBufferSource.BufferSource source, Vec3 userAt, float tick) {
        Vec3 eye = camera.getPosition();
        double camX = eye.x;
        double camY = eye.y;
        double camZ = eye.z;
        Matrix4f[] reused = previousTransforms;
        previousTransforms = transforms;
        transforms = reused;
        previousOriginView.set(originView);
        originView.set(RenderSystem.getModelViewMatrix())
                .translate((float) (mesh.originX - camX), (float) (mesh.originY - camY), (float) (mesh.originZ - camZ));
        KingCrimsonShatterMesh.Plate[] plates = mesh.plates;
        for (int i = 0; i < plates.length; i++) {
            KingCrimsonShatterMesh.Plate plate = plates[i];
            KingCrimsonShatterPlan.Pose pose = poses[i];
            plan.pose(plate.id, tick, pose);
            if (pose.scale > 0.0F && tick > plan.startTick(plate.id) + VIEW_GUARD_FROM_AGE) {
                pose.scale *= viewGuardScale(i, plate, pose, eye, userAt, tick);
            }
            float scale = pose.scale;
            double x = plate.pivotX + pose.dx;
            double y = plate.pivotY + pose.dy;
            double z = plate.pivotZ + pose.dz;
            transforms[i].set(
                    pose.m00 * scale, pose.m10 * scale, pose.m20 * scale, 0.0F,
                    pose.m01 * scale, pose.m11 * scale, pose.m21 * scale, 0.0F,
                    pose.m02 * scale, pose.m12 * scale, pose.m22 * scale, 0.0F,
                    (float) (x - mesh.originX), (float) (y - mesh.originY), (float) (z - mesh.originZ), 1.0F);
            if (scale <= 0.0F) {
                visible[i] = false;
                continue;
            }
            keptBuckets[i] = (int) Math.ceil((1.0F - plan.tearShare(plate.id, tick)) * KingCrimsonShatterMesh.TEAR_BUCKETS);
            double r = plate.radius * scale;
            visible[i] = frustum.isVisible(new AABB(x - r, y - r, z - r, x + r, y + r, z + r));
        }

        drawnVertices = 0;
        plateDrawCalls = 0;
        velocityDrawCalls = 0;
        // The void has no blue haze: these keep their colours at any distance.
        float fogStart = RenderSystem.getShaderFogStart();
        if (!iris) RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        try {
            drawBaked(PLATE_SOLID, solid);
            drawBaked(PLATE_CUTOUT, cutout);
            drawBaked(FLAT, flat);

            float ox = (float) (mesh.originX - camX);
            float oy = (float) (mesh.originY - camY);
            float oz = (float) (mesh.originZ - camZ);
            if (iris && (mesh.cuts.length != 0 || mesh.wind != null || mesh.debris.length != 0)) {
                KingCrimsonShatterIrisPresentation.withEntityId(KingCrimsonShatterIrisConstants.DETAIL_EMISSIVE_ID, light -> {
                    drawDetails(source, ox, oy, oz, tick, light);
                    return null;
                });
            } else if (!iris && (mesh.cuts.length != 0 || mesh.wind != null || mesh.debris.length != 0)) {
                drawDetails(source, ox, oy, oz, tick, -1);
            }
            if (tick >= DEBRIS_COUNT_TICK && tick < DEBRIS_COUNT_TICK + 1.0F) {
                debrisInView = Math.max(debrisInView, countDebrisInView(frustum, tick));
            }
            hasPreviousFrame = true;
        } finally {
            if (!iris) RenderSystem.setShaderFogStart(fogStart);
        }
    }

    private void drawDetails(MultiBufferSource.BufferSource source, float ox, float oy, float oz, float tick, int light) {
        VertexConsumer blocks = source.getBuffer(PLATE_SOLID);
        boolean ids = iris && KingCrimsonShatterIrisPresentation.readsEntityIds();
        drawDebris(blocks, ox, oy, oz, tick, light, ids, ids && KingCrimsonShatterPackRouter.widePlateLight());
        source.endBatch(PLATE_SOLID);
        if (ids) KingCrimsonShatterIrisPresentation.nextEntityId(KingCrimsonShatterIrisConstants.DETAIL_EMISSIVE_ID);

        int detailLight = light >= 0 ? light : FULL_BRIGHT;
        VertexConsumer lines = source.getBuffer(FLAT);
        for (KingCrimsonShatterMesh.CutLine cut : mesh.cuts) {
            drawCrack(lines, cut, ox, oy, oz, tick, detailLight);
        }
        source.endBatch(FLAT);
        if (mesh.wind != null && tick < KingCrimsonShatterPlan.WIND_LAST_TICK) {
            VertexConsumer wind = source.getBuffer(KingCrimsonShatterWind.RENDER_TYPE);
            drawnVertices += mesh.wind.draw(wind, tick, ox, oy, oz, detailLight);
            source.endBatch(KingCrimsonShatterWind.RENDER_TYPE);
        }
    }

    // A plate that has come between the camera and the user, or up to the camera, shrinks away and does not come back.
    private float viewGuardScale(
            int index,
            KingCrimsonShatterMesh.Plate plate,
            KingCrimsonShatterPlan.Pose pose,
            Vec3 eye,
            Vec3 userAt,
            float tick) {
        if (Float.isNaN(viewGuardTick[index]) && blocksView(plate, pose, eye, userAt)) {
            viewGuardTick[index] = tick;
        }
        return Float.isNaN(viewGuardTick[index])
                ? 1.0F
                : 1.0F - KingCrimsonShatterPlan.smooth((tick - viewGuardTick[index]) / VIEW_GUARD_TICKS);
    }

    private boolean blocksView(KingCrimsonShatterMesh.Plate plate, KingCrimsonShatterPlan.Pose pose, Vec3 eye, Vec3 userAt) {
        double centerX = plate.pivotX + pose.dx;
        double centerY = plate.pivotY + pose.dy;
        double centerZ = plate.pivotZ + pose.dz;
        double reach = plate.radius + 4.0D;
        double reachSquared = reach * reach;
        if (squared(eye.x - centerX, eye.y - centerY, eye.z - centerZ) > reachSquared
                && squared(userAt.x - centerX, userAt.y - centerY, userAt.z - centerZ) > reachSquared) {
            return false;
        }
        // Both ends in the plate's own axes: the transpose turns back, the scale is still 1 this early or close to it.
        double ax = eye.x - centerX;
        double ay = eye.y - centerY;
        double az = eye.z - centerZ;
        double bx = userAt.x - centerX;
        double by = userAt.y - centerY;
        double bz = userAt.z - centerZ;
        double localAX = pose.m00 * ax + pose.m10 * ay + pose.m20 * az;
        double localAY = pose.m01 * ax + pose.m11 * ay + pose.m21 * az;
        double localAZ = pose.m02 * ax + pose.m12 * ay + pose.m22 * az;
        double localBX = pose.m00 * bx + pose.m10 * by + pose.m20 * bz;
        double localBY = pose.m01 * bx + pose.m11 * by + pose.m21 * bz;
        double localBZ = pose.m02 * bx + pose.m12 * by + pose.m22 * bz;
        double from = 0.0D;
        double to = 1.0D;
        double dy = localBY - localAY;
        double low = plate.bottom - VIEW_GUARD_MARGIN;
        double high = plate.groundTop + VIEW_GUARD_MARGIN;
        if (Math.abs(dy) < 1.0E-6D) {
            if (localAY < low || localAY > high) {
                return false;
            }
        } else {
            double t0 = (low - localAY) / dy;
            double t1 = (high - localAY) / dy;
            from = Math.max(from, Math.min(t0, t1));
            to = Math.min(to, Math.max(t0, t1));
            if (from > to) {
                return false;
            }
        }
        // The outline relative to the pivot; inside is on the left of each edge.
        double[] polygonX = plan.polygonX(plate.id);
        double[] polygonZ = plan.polygonZ(plate.id);
        // the plan measures from the centre of the origin's column
        double pivotDX = plate.pivotX - mesh.originX - 0.5D;
        double pivotDZ = plate.pivotZ - mesh.originZ - 0.5D;
        double dx = localBX - localAX;
        double dz = localBZ - localAZ;
        int corners = polygonX.length;
        for (int i = 0; i < corners; i++) {
            int next = (i + 1) % corners;
            double edgeX = polygonX[next] - polygonX[i];
            double edgeZ = polygonZ[next] - polygonZ[i];
            double length = Math.max(1.0E-6D, Math.sqrt(edgeX * edgeX + edgeZ * edgeZ));
            double normalX = -edgeZ / length;
            double normalZ = edgeX / length;
            double distance = normalX * (localAX - (polygonX[i] - pivotDX))
                    + normalZ * (localAZ - (polygonZ[i] - pivotDZ))
                    + VIEW_GUARD_MARGIN;
            double slope = normalX * dx + normalZ * dz;
            if (Math.abs(slope) < 1.0E-9D) {
                if (distance < 0.0D) {
                    return false;
                }
                continue;
            }
            double cross = -distance / slope;
            if (slope > 0.0D) {
                from = Math.max(from, cross);
            } else {
                to = Math.min(to, cross);
            }
            if (from > to) {
                return false;
            }
        }
        return true;
    }

    private static double squared(double x, double y, double z) {
        return x * x + y * y + z * z;
    }

    private void drawBaked(RenderType type, BakedLayer layer) {
        if (layer.chunks.isEmpty()) {
            return;
        }
        ShaderInstance previousShader = iris ? RenderSystem.getShader() : null;
        ShaderInstance shader = null;
        int velocityAttribute = -1;
        try {
            type.setupRenderState();
            shader = RenderSystem.getShader();
            if (shader == null || shader.MODEL_VIEW_MATRIX == null) {
                throw new IllegalStateException("Shatter entity shader has no model-view uniform");
            }
            Uniform normal = iris ? shader.getUniform("NormalMat") : null;
            // iterationRP can compute normals from derivatives and link this uniform out.
            shader.setDefaultUniforms(
                    VertexFormat.Mode.QUADS,
                    RenderSystem.getModelViewMatrix(),
                    RenderSystem.getProjectionMatrix(),
                    Minecraft.getInstance().getWindow());
            shader.apply();
            int linked = iris ? GL20.glGetAttribLocation(shader.getId(), "at_velocity") : -1;
            for (BakedChunk chunk : layer.chunks) {
                if (iris && !chunk.buffer.getFormat().equals(shader.getVertexFormat())) {
                    throw new IllegalStateException("Iris shatter vertex layout changed");
                }
                chunk.buffer.bind();
                // ar_velocity reserves 9; never overwrite an attribute fed by the bound format.
                int attribute = linked >= 9 && GL20.glGetVertexAttribi(linked, GL20.GL_VERTEX_ATTRIB_ARRAY_ENABLED) == 0
                        ? linked : -1;
                if (attribute >= 0) velocityAttribute = attribute;
                drawRanges(layer, chunk, shader, normal, attribute);
            }
        } finally {
            try {
                if (velocityAttribute >= 0) {
                    GL20.glVertexAttrib3f(velocityAttribute, 0.0F, 0.0F, 0.0F);
                }
                VertexBuffer.unbind();
                if (shader != null) shader.clear();
            } finally {
                try {
                    type.clearRenderState();
                } finally {
                    if (iris) RenderSystem.setShader(() -> previousShader);
                }
            }
        }
    }

    private void drawRanges(BakedLayer layer, BakedChunk chunk, ShaderInstance shader, Uniform normal, int velocityAttribute) {
        VertexFormat.IndexType indexType = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS).type();
        Uniform modelView = shader.MODEL_VIEW_MATRIX;
        Uniform light0 = iris ? null : shader.LIGHT0_DIRECTION;
        Uniform light1 = iris ? null : shader.LIGHT1_DIRECTION;
        FloatBuffer light0Now = light0 != null ? light0.getFloatBuffer() : null;
        FloatBuffer light1Now = light1 != null ? light1.getFloatBuffer() : null;
        float l0x = light0Now != null ? light0Now.get(0) : 0.0F;
        float l0y = light0Now != null ? light0Now.get(1) : 0.0F;
        float l0z = light0Now != null ? light0Now.get(2) : 0.0F;
        float l1x = light1Now != null ? light1Now.get(0) : 0.0F;
        float l1y = light1Now != null ? light1Now.get(1) : 0.0F;
        float l1z = light1Now != null ? light1Now.get(2) : 0.0F;
        for (int i = chunk.firstPlate; i < chunk.endPlate; i++) {
            if (!visible[i]) {
                continue;
            }
            // Intersect this VBO's span with the original tear prefix, including for a plate split across uploads.
            int firstQuad = Math.max(layer.firstQuad[i], chunk.firstQuad);
            int keptEnd = layer.firstQuad[i] + layer.keptQuads[i][keptBuckets[i]];
            int quads = Math.min(keptEnd, chunk.endQuad) - firstQuad;
            if (quads <= 0) {
                continue;
            }
            plateView.set(originView).mul(transforms[i]);
            modelView.set(plateView);
            modelView.upload();
            if (normal != null) {
                plateNormal.set(plateView).invert().transpose().get(normalValues);
                normal.set(normalValues);
                normal.upload();
            }
            if (velocityAttribute >= 0) {
                if (KingCrimsonShatterDiagnostics.VELOCITY_OFF) velocity.zero();
                else pivotMotion(i, velocity);
                GL20.glVertexAttrib3f(velocityAttribute, velocity.x, velocity.y, velocity.z);
                velocityDrawCalls++;
            }
            // The vanilla entity program needs its lights in the plate's own axes.
            KingCrimsonShatterPlan.Pose pose = poses[i];
            if (light0 != null) {
                light0.set(
                        pose.m00 * l0x + pose.m10 * l0y + pose.m20 * l0z,
                        pose.m01 * l0x + pose.m11 * l0y + pose.m21 * l0z,
                        pose.m02 * l0x + pose.m12 * l0y + pose.m22 * l0z);
                light0.upload();
            }
            if (light1 != null) {
                light1.set(
                        pose.m00 * l1x + pose.m10 * l1y + pose.m20 * l1z,
                        pose.m01 * l1x + pose.m11 * l1y + pose.m21 * l1z,
                        pose.m02 * l1x + pose.m12 * l1y + pose.m22 * l1z);
                light1.upload();
            }
            GlStateManager._drawElements(
                    GL11.GL_TRIANGLES,
                    quads * 6,
                    indexType.asGLType,
                    (long) (firstQuad - chunk.firstQuad) * 6L * indexType.bytes);
            drawnVertices += quads * 4;
            plateDrawCalls++;
        }
    }

    // A thin dark line that runs just ahead of its crest and stays when the crest has collapsed. As soon as it has run past
    // the plates beside it they start to part: it widens with them and then narrows away into the real gap.
    private void drawCrack(VertexConsumer out, KingCrimsonShatterMesh.CutLine cut, float ox, float oy, float oz, float tick, int light) {
        float age = tick - cut.startTick;
        if (age <= 0.0F || tick > cut.lastParting + CRACK_PARTING_TICKS * 2.0F) {
            return;
        }
        float head = cut.source.headDistance(age);
        float[] along = cut.along;
        float[] height = cut.height;
        float[] parting = cut.parting;
        float dirX = cut.dirX;
        float dirZ = cut.dirZ;
        for (int i = 0; i + 1 < along.length; i++) {
            float s0 = along[i];
            if (s0 >= head) {
                break;
            }
            float y0 = height[i];
            float y1 = height[i + 1];
            // Over a step in the ground the line would hang in the air like a wire.
            if (Float.isNaN(y0) || Float.isNaN(y1) || Math.abs(y1 - y0) > CRACK_STEEPEST_STEP_BLOCKS) {
                continue;
            }
            float s1 = Math.min(along[i + 1], head);
            float lift0 = 0.02F + s0 * 0.0003F;
            float lift1 = 0.02F + s1 * 0.0003F;
            float x0 = cut.startX + dirX * s0 + ox;
            float z0 = cut.startZ + dirZ * s0 + oz;
            float x1 = cut.startX + dirX * s1 + ox;
            float z1 = cut.startZ + dirZ * s1 + oz;
            float wy0 = y0 + lift0 + oy;
            float wy1 = y1 + lift1 + oy;
            float parts = CRACK_PARTING_TICKS * (CRACK_PARTS_QUICKLY + (1.0F - CRACK_PARTS_QUICKLY)
                    * KingCrimsonShatterPlan.smooth((float) Math.sqrt(x0 * x0 + wy0 * wy0 + z0 * z0) / CRACK_PARTS_QUICKLY_WITHIN_BLOCKS));
            float since = (tick - parting[i]) / parts;
            float widening = CRACK_WIDENS_BY * KingCrimsonShatterPlan.smooth(since);
            float left = 1.0F - KingCrimsonShatterPlan.smooth(since - 1.0F);
            if (left <= 0.0F) {
                continue;
            }
            float w0 = crackHalfWidth(x0, wy0, z0, widening) * left;
            float w1 = crackHalfWidth(x1, wy1, z1, widening) * left;
            putFlat(out, x0 - dirZ * w0, wy0, z0 + dirX * w0, CRACK_COLOR, light);
            putFlat(out, x0 + dirZ * w0, wy0, z0 - dirX * w0, CRACK_COLOR, light);
            putFlat(out, x1 + dirZ * w1, wy1, z1 - dirX * w1, CRACK_COLOR, light);
            putFlat(out, x1 - dirZ * w1, wy1, z1 + dirX * w1, CRACK_COLOR, light);
            drawnVertices += 4;
        }
    }

    // Thin, and never under about a pixel wide: a far line must not vanish or alias away.
    private float crackHalfWidth(float x, float y, float z, float widening) {
        float distance = (float) Math.sqrt(x * x + y * y + z * z);
        float perBlock = iris ? KingCrimsonShatterIrisConstants.DETAIL_WIDTH_PER_BLOCK : CRACK_HALF_WIDTH_PER_BLOCK;
        return Math.max(CRACK_HALF_WIDTH, perBlock * distance)
                * (1.0F + widening * KingCrimsonShatterPlan.smooth(distance / CRACK_WIDENS_FULLY_AT_BLOCKS));
    }

    private static void putFlat(VertexConsumer out, float x, float y, float z, int color, int light) {
        out.addVertex(x, y, z, color, 0.5F, 0.5F, NO_OVERLAY, light, 0.0F, 1.0F, 0.0F);
    }

    private void drawDebris(VertexConsumer out, float ox, float oy, float oz, float tick, int lightOverride, boolean ids, boolean wide) {
        float[] debris = mesh.debris;
        int[] colors = mesh.debrisColors;
        int[] lights = mesh.debrisLights;
        byte[] levels = mesh.debrisLevels;
        int index = 0;
        for (int at = 0; at < debris.length; at += KingCrimsonShatterMesh.DEBRIS_STRIDE, index++) {
            float age = tick - debris[at + 5];
            float size = debris[at + 3] * KingCrimsonShatterPlan.debrisScale(age, tick, debris[at + 12]);
            if (age < 0.0F || size <= 0.0F) {
                continue;
            }
            int light = lightOverride >= 0 ? lightOverride : lights[index];
            if (ids) KingCrimsonShatterIrisPresentation.nextEntityId(KingCrimsonShatterIrisConstants.plateId(levels[index], wide));
            float half = size * 0.5F;
            float drift = KingCrimsonShatterPlan.debrisDrift(age);
            float x = debris[at] + debris[at + 10] * drift + ox;
            float y = debris[at + 1] + oy + KingCrimsonShatterPlan.debrisRise(debris[at + 4], debris[at + KingCrimsonShatterMesh.DEBRIS_GROWTH], age);
            float z = debris[at + 2] + debris[at + 11] * drift + oz;
            float axisX = debris[at + 6];
            float axisY = debris[at + 7];
            float axisZ = debris[at + 8];
            float angle = debris[at + 9] * age;
            float c = (float) Math.cos(angle);
            float s = (float) Math.sin(angle);
            float t = 1.0F - c;
            float m00 = t * axisX * axisX + c;
            float m01 = t * axisX * axisY - s * axisZ;
            float m02 = t * axisX * axisZ + s * axisY;
            float m10 = t * axisX * axisY + s * axisZ;
            float m11 = t * axisY * axisY + c;
            float m12 = t * axisY * axisZ - s * axisX;
            float m20 = t * axisX * axisZ - s * axisY;
            float m21 = t * axisY * axisZ + s * axisX;
            float m22 = t * axisZ * axisZ + c;
            for (int face = 0; face < 6; face++) {
                float[] corners = CUBE[face];
                float[] normal = FACE_NORMALS[face];
                float nx = m00 * normal[0] + m01 * normal[1] + m02 * normal[2];
                float ny = m10 * normal[0] + m11 * normal[1] + m12 * normal[2];
                float nz = m20 * normal[0] + m21 * normal[1] + m22 * normal[2];
                int uvAt = at + 13 + face * 4;
                float u0 = debris[uvAt];
                float v0 = debris[uvAt + 1];
                float u1 = debris[uvAt + 2];
                float v1 = debris[uvAt + 3];
                int color = colors[index * 6 + face];
                for (int corner = 0; corner < 4; corner++) {
                    float lx = (corners[corner * 3] * 2.0F - 1.0F) * half;
                    float ly = (corners[corner * 3 + 1] * 2.0F - 1.0F) * half;
                    float lz = (corners[corner * 3 + 2] * 2.0F - 1.0F) * half;
                    out.addVertex(
                            m00 * lx + m01 * ly + m02 * lz + x,
                            m10 * lx + m11 * ly + m12 * lz + y,
                            m20 * lx + m21 * ly + m22 * lz + z,
                            color,
                            corner < 2 ? u0 : u1,
                            corner == 0 || corner == 3 ? v0 : v1,
                            NO_OVERLAY,
                            light,
                            nx,
                            ny,
                            nz);
                }
            }
            drawnVertices += 24;
        }
    }

    // Only for the numbers in the debug line.
    private int countDebrisInView(Frustum frustum, float tick) {
        float[] debris = mesh.debris;
        int seen = 0;
        for (int at = 0; at < debris.length; at += KingCrimsonShatterMesh.DEBRIS_STRIDE) {
            float age = tick - debris[at + 5];
            if (age < 0.0F) {
                continue;
            }
            float drift = KingCrimsonShatterPlan.debrisDrift(age);
            double x = mesh.originX + debris[at] + debris[at + 10] * drift;
            double y = mesh.originY + debris[at + 1] + KingCrimsonShatterPlan.debrisRise(debris[at + 4], debris[at + KingCrimsonShatterMesh.DEBRIS_GROWTH], age);
            double z = mesh.originZ + debris[at + 2] + debris[at + 11] * drift;
            if (frustum.isVisible(new AABB(x - 0.1D, y - 0.1D, z - 0.1D, x + 0.1D, y + 0.1D, z + 0.1D))) {
                seen++;
            }
        }
        return seen;
    }

    record BakeTiming(int slices, int uploads, long cpuNanos, long writeNanos, long uploadNanos,
            long maxSliceNanos, long maxSliceWriteNanos, long maxSliceUploadNanos, long elapsedNanos) {}

    private record BakedChunk(VertexBuffer buffer, int firstPlate, int endPlate, int firstQuad, int endQuad) {}

    /** The layer's original tear order, with that quad range covered by one or more uploaded chunks. */
    private static final class BakedLayer {
        private final Function<KingCrimsonShatterMesh.Plate, KingCrimsonShatterMesh.Layer> layerOf;
        private final int[] firstQuad;
        private final int[][] keptQuads;
        private final List<BakedChunk> chunks = new ArrayList<>();
        private int nextPlate;
        private int bakedQuads;

        private BakedLayer(KingCrimsonShatterMesh.Plate[] plates,
                Function<KingCrimsonShatterMesh.Plate, KingCrimsonShatterMesh.Layer> layerOf) {
            this.layerOf = layerOf;
            firstQuad = new int[plates.length + 1];
            keptQuads = new int[plates.length][];
            for (int i = 0; i < plates.length; i++) {
                KingCrimsonShatterMesh.Layer layer = layerOf.apply(plates[i]);
                firstQuad[i + 1] = firstQuad[i] + layer.normals.length;
                keptQuads[i] = layer.kept;
            }
        }

        private int totalQuads() {
            return firstQuad[keptQuads.length];
        }

        private boolean ready() {
            return bakedQuads == totalQuads();
        }

        private int nextPlateQuads() {
            return firstQuad[nextPlate + 1] - bakedQuads;
        }

        private void skipEmptyPlates() {
            while (nextPlate < keptQuads.length && nextPlateQuads() == 0) nextPlate++;
        }

    }
}
