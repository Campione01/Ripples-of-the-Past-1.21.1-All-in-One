package rotp.addons.kingcrimson.client.shatter;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntFunction;

import org.slf4j.Logger;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL32;
import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.logging.LogUtils;

import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPackRoute;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterProjection;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.neoforged.fml.ModList;

/** Client presentation owned by the local shader-pack erase session. */
public final class KingCrimsonShatterIrisPresentation {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int EMISSIVE_LIGHT = LightTexture.pack(0, 0);
    private static final int FALLBACK_LIGHT = LightTexture.pack(15, 0);
    private static final int NO_ENTITY_ID = 0;
    private static final int VOID_BUFFER_BYTES = 64 * 1024;
    // EntityCulling may evaluate its predicate on its own thread.
    private static volatile boolean cullOverridden;
    private static boolean previousSmartCull;
    private static boolean cullGraphDirty;
    private static boolean captureFailureLogged;
    private static String lastVoidProjection;

    private KingCrimsonShatterIrisPresentation() {}

    public static void syncCull(boolean active) {
        if (active == cullOverridden) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (active) {
            previousSmartCull = minecraft.smartCull;
            minecraft.smartCull = false;
        } else {
            minecraft.smartCull = previousSmartCull;
        }
        cullOverridden = active;
        cullGraphDirty = minecraft.level != null;
    }

    static void refreshTerrainVisibility() {
        if (!cullGraphDirty) return;
        cullGraphDirty = false;
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) minecraft.levelRenderer.needsUpdate();
    }

    public static void registerEntityCullingWhitelist() {
        if (ModList.get().isLoaded("entityculling")) {
            EntityCulling.register();
        }
    }

    /** Called in the main pass at AFTER_ENTITIES, before the immediate plate draws. */
    public static boolean drawVoid() {
        if (!KingCrimsonShatterVoidTexture.fill(Util.getNanos() * 1.0E-9D)) return false;
        KingCrimsonShatterVoidDepth.rememberWorldProjection();
        RenderType type = VoidShell.RENDER_TYPE;
        try (ByteBufferBuilder storage = new ByteBufferBuilder(VOID_BUFFER_BYTES);
                MeshData mesh = withEntityId(
                        KingCrimsonShatterIrisConstants.VOID_EMISSIVE_ID, light -> {
                            BufferBuilder builder = new BufferBuilder(storage, type.mode(), type.format());
                            for (ShellVertex vertex : VoidShell.VERTICES) {
                                builder.addVertex(vertex.x, vertex.y, vertex.z, 0xFFFFFFFF,
                                        vertex.u, vertex.v, OverlayTexture.NO_OVERLAY, light,
                                        vertex.nx, vertex.ny, vertex.nz);
                            }
                            return builder.buildOrThrow();
                        })) {
            // Iris's shared entity source defers its real flush; this depth must be written now.
            ShaderInstance previousShader = RenderSystem.getShader();
            int previousTexture = RenderSystem.getShaderTexture(0);
            try {
                type.setupRenderState();
                ShaderInstance shader = RenderSystem.getShader();
                if (shader == null) {
                    return false;
                }
                try {
                    drawVoidAtFarDepth(mesh);
                } catch (RuntimeException | LinkageError failure) {
                    try {
                        shader.clear();
                    } catch (RuntimeException | LinkageError cleanup) {
                        failure.addSuppressed(cleanup);
                    }
                    throw failure;
                }
                return true;
            } finally {
                try {
                    BufferUploader.reset();
                } finally {
                    try {
                        type.clearRenderState();
                    } finally {
                        RenderSystem.setShaderTexture(0, previousTexture);
                        RenderSystem.setShader(() -> previousShader);
                    }
                }
            }
        }
    }

    private static void drawVoidAtFarDepth(MeshData mesh) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var range = stack.mallocDouble(2);
            GL11.glGetDoublev(GL11.GL_DEPTH_RANGE, range);
            boolean clamp = GL11.glIsEnabled(GL32.GL_DEPTH_CLAMP);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level != null) {
                float projectionFar = KingCrimsonShatterProjection.farPlane(RenderSystem.getProjectionMatrix());
                int renderDistance = minecraft.options.getEffectiveRenderDistance();
                String signature = minecraft.level.dimension().location() + ":" + renderDistance
                        + ":" + Math.round(projectionFar);
                if (!signature.equals(lastVoidProjection)) {
                    lastVoidProjection = signature;
                    LOGGER.info("King Crimson shatter projection: {} RD{}, game far={}, projection far={}",
                            minecraft.level.dimension().location(), renderDistance, minecraft.gameRenderer.getDepthFar(),
                            projectionFar);
                }
            }
            try {
                // Keep the cube's rays, put its depth behind the world but below the pack's sky marker.
                GL11.glEnable(GL32.GL_DEPTH_CLAMP);
                GL11.glDepthRange(KingCrimsonShatterProjection.VOID_DEPTH, KingCrimsonShatterProjection.VOID_DEPTH);
                BufferUploader.drawWithShader(mesh);
            } finally {
                GL11.glDepthRange(range.get(0), range.get(1));
                if (!clamp) GL11.glDisable(GL32.GL_DEPTH_CLAMP);
            }
        }
    }

    /**
     * Captures an ID in the written vertices, without changing the next entity's ID. Only a pack that declares the void reads these ids
     * as we mean them: any other could mean anything by them, so it gets no id and lights the geometry itself from block light.
     */
    public static <T> T withEntityId(int entityId, IntFunction<T> write) {
        IrisEntityCapture capture = IrisEntityCapture.ACCESS;
        if (capture == null) {
            return write.apply(FALLBACK_LIGHT);
        }
        boolean declared = readsEntityIds();
        int previousId;
        try {
            previousId = (int) capture.getter.invokeExact();
        } catch (Throwable error) {
            rethrowFatal(error);
            logCaptureFallback(error);
            return write.apply(FALLBACK_LIGHT);
        }
        try {
            setEntityId(capture, declared ? entityId : NO_ENTITY_ID);
            return write.apply(declared ? EMISSIVE_LIGHT : FALLBACK_LIGHT);
        } finally {
            setEntityId(capture, previousId);
        }
    }

    static boolean readsEntityIds() {
        return IrisEntityCapture.ACCESS != null
                && KingCrimsonShatterPackRouter.current() == KingCrimsonShatterPackRoute.DECLARED;
    }

    /** Inside {@link #withEntityId}, on a pack that reads them: the id the next vertices take. */
    static void nextEntityId(int entityId) {
        setEntityId(IrisEntityCapture.ACCESS, entityId);
    }

    private static void setEntityId(IrisEntityCapture capture, int entityId) {
        try {
            capture.setter.invokeExact(entityId);
        } catch (Throwable error) {
            rethrowFatal(error);
            throw new IllegalStateException("Could not set the Iris entity ID for Time Erase geometry", error);
        }
    }

    private static void rethrowFatal(Throwable error) {
        if (error instanceof Error fatal && !(fatal instanceof LinkageError)) {
            throw fatal;
        }
    }

    private static void logCaptureFallback(Throwable error) {
        if (!captureFailureLogged) {
            captureFailureLogged = true;
            LOGGER.warn("King Crimson cannot capture Iris entity IDs; using block-light emissiveness.", error);
        }
    }

    private record IrisEntityCapture(MethodHandle getter, MethodHandle setter) {
        private static final IrisEntityCapture ACCESS = resolve();

        private static IrisEntityCapture resolve() {
            try {
                Class<?> type = optionalClass("net.irisshaders.iris.uniforms.CapturedRenderingState");
                var lookup = MethodHandles.publicLookup();
                Object instance = lookup.findStaticGetter(type, "INSTANCE", type).invoke();
                return new IrisEntityCapture(
                        lookup.findVirtual(type, "getCurrentRenderedEntity", MethodType.methodType(int.class))
                                .bindTo(instance),
                        lookup.findVirtual(type, "setCurrentEntity", MethodType.methodType(void.class, int.class))
                                .bindTo(instance));
            } catch (Throwable error) {
                rethrowFatal(error);
                logCaptureFallback(error);
                return null;
            }
        }
    }

    private static Class<?> optionalClass(String name) throws ClassNotFoundException {
        return KingCrimsonShatterIrisPresentation.class.getClassLoader().loadClass(name);
    }

    private static final class EntityCulling {
        private static void register() {
            try {
                Class<?> type = optionalClass("dev.tr7zw.entityculling.EntityCullingModBase");
                Class<?> cullable = optionalClass("dev.tr7zw.entityculling.access.Cullable");
                var lookup = MethodHandles.publicLookup();
                Object instance = lookup.findStaticGetter(type, "instance", type).invoke();
                MethodHandle isCulled = lookup.findVirtual(cullable, "isCulled", MethodType.methodType(boolean.class))
                        .asType(MethodType.methodType(boolean.class, Entity.class));
                MethodHandle setCulled = lookup.findVirtual(cullable, "setCulled",
                        MethodType.methodType(void.class, boolean.class))
                        .asType(MethodType.methodType(void.class, Entity.class, boolean.class));
                Function<Entity, Boolean> whitelist = entity -> {
                    if (!cullOverridden || !cullable.isInstance(entity)) {
                        return false;
                    }
                    try {
                        // The whitelist skips a new occlusion test but does not clear an existing result.
                        if ((boolean) isCulled.invokeExact(entity)) {
                            setCulled.invokeExact(entity, false);
                        }
                        return true;
                    } catch (Throwable error) {
                        rethrowFatal(error);
                        return false;
                    }
                };
                lookup.findVirtual(type, "addDynamicEntityWhitelist", MethodType.methodType(void.class, Function.class))
                        .bindTo(instance).invokeExact(whitelist);
            } catch (Throwable error) {
                rethrowFatal(error);
                LOGGER.warn("King Crimson could not register the EntityCulling Time Erase whitelist.", error);
            }
        }
    }

    private static final class VoidShell {
        private static final RenderType RENDER_TYPE = RenderType.entitySolid(KingCrimsonShatterVoidTexture.TEXTURE);
        private static final List<ShellVertex> VERTICES = buildVertices();

        private static List<ShellVertex> buildVertices() {
            var vertices = new ArrayList<ShellVertex>(24);
            float halfEdge = KingCrimsonShatterIrisConstants.VOID_HALF_EDGE;
            for (Direction face : Direction.values()) {
                addQuad(vertices, face, halfEdge);
            }
            return List.copyOf(vertices);
        }

        private static void addQuad(List<ShellVertex> vertices, Direction face, float distance) {
            int nx = -face.getStepX();
            int ny = -face.getStepY();
            int nz = -face.getStepZ();
            int ux = nx == 0 ? 1 : 0;
            int uz = 1 - ux;
            // V = inward normal cross U; the four corners then face the camera from inside.
            int vx = ny * uz;
            int vy = nz * ux - nx * uz;
            int vz = -ny * ux;
            for (int corner = 0; corner < 4; corner++) {
                int texU = corner == 1 || corner == 2 ? 1 : 0;
                int texV = corner >= 2 ? 1 : 0;
                float u = texU == 0 ? -distance : distance;
                float v = texV == 0 ? -distance : distance;
                vertices.add(new ShellVertex(-nx * distance + ux * u + vx * v, -ny * distance + vy * v,
                        -nz * distance + uz * u + vz * v,
                        KingCrimsonShatterVoidTexture.u(face.ordinal(), texU),
                        KingCrimsonShatterVoidTexture.v(face.ordinal(), texV), nx, ny, nz));
            }
        }
    }

    private record ShellVertex(float x, float y, float z, float u, float v, int nx, int ny, int nz) {}
}
