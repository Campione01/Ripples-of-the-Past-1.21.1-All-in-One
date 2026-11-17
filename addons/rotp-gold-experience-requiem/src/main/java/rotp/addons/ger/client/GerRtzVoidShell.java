package rotp.addons.ger.client;

import static org.lwjgl.opengl.GL45C.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.client.GerRtzVfxSettings.Mode;
import rotp.addons.ger.state.GerRtzPackRoute;

/**
 * Return to Zero's cosmos under a shader pack, behind everything that stands in the world instead of mixed over the whole image. A black
 * camera-centred box goes through the pack's entity program at a depth of its own, after the entities, so that Stands, players and plates
 * stay in front of it. Before the composites the box's depth moves to a plain distance, because a pack takes what lies at the far plane for
 * its sky. A pack that declares the marker then draws the cosmos itself after its upscaler, with the envelope it reads from the box's sky
 * light; any other gets it painted over its final image here, where the depth says the box still shows.
 */
@OnlyIn(Dist.CLIENT)
public final class GerRtzVoidShell {
    private static final Logger LOGGER = LogUtils.getLogger();
    // One step nearer than King Crimson's own shell: where both are drawn this one stands in front, on every route and with no tie
    // between the two add-ons. The owner's rule: only this background outranks that one.
    private static final float MARKER = Math.nextDown(Math.nextDown(1.0F));
    private static final float HALF_EDGE = 512.0F;
    // The distance King Crimson's shell moves to as well: past what a pack's fog and shading notice near the camera.
    private static final float COMPOSITE_DISTANCE = 32.0F;
    private static final int ENVELOPE_LIGHT_ZERO = 8;
    private static final int ENVELOPE_LIGHT_RANGE = 216;
    private static final int FALLBACK_LIGHT = LightTexture.pack(15, 0);
    // Not quite black: some packs discard a fragment whose vertex colour is, and then the depth holds no shell.
    private static final int BLACK = 0xFF010101;
    private static final int SHELL_BYTES = 64 * 1024;
    private static final RenderType SHELL_TYPE =
            RenderType.entitySolid(ResourceLocation.withDefaultNamespace("textures/misc/white.png"));
    private static final float[][] CORNERS = corners();
    private static final String VERTEX = """
            #version 450 core
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    // A 24-bit depth reads back within a step of the marker; the next value up is King Crimson's and the plates stop further down.
    private static final String FRAGMENT = """
            #version 450 core
            layout(binding = 0) uniform sampler2D originalDepth;
            layout(location = 0) uniform ivec2 regionOrigin;
            layout(location = 1) uniform float replacementDepth;
            void main() {
                uint bits = floatBitsToUint(texelFetch(originalDepth, ivec2(gl_FragCoord.xy) - regionOrigin, 0).r);
                if (bits != 0x3f7ffffeu && bits != 0x3f7ffffdu) discard;
                gl_FragDepth = replacementDepth;
            }
            """;

    private static ShaderInstance paintProgram;
    private static int program;
    private static int vao;
    private static int framebuffer;
    private static int copy;
    private static int copyWidth;
    private static int copyHeight;
    private static int copyFormat;
    private static Matrix4f worldProjection;
    private static float movedTo;
    private static boolean drawn;
    private static boolean moved;
    private static boolean faulted;
    private static boolean resourcesHeld;

    private GerRtzVoidShell() {}

    public static void onRegisterShaders(RegisterShadersEvent event) {
        paintProgram = null;
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), GoldExperienceRequiemAddon.id("rtz_cosmos_paint"), DefaultVertexFormat.POSITION),
                    loaded -> paintProgram = loaded);
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Unable to load Return to Zero's cosmos painter; the old mix over the image stays on such packs.", failure);
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (GerRtzClientState.activeMode() != Mode.TIME_ERASE) {
            if (resourcesHeld) close();
            return;
        }
        RenderLevelStageEvent.Stage stage = event.getStage();
        if (stage != RenderLevelStageEvent.Stage.AFTER_ENTITIES
                && stage != RenderLevelStageEvent.Stage.AFTER_WEATHER
                && stage != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        GerRtzPackRoute route = GerRtzPackAccess.route();
        if (route == GerRtzPackRoute.NO_SHADERS || GerRtzPackAccess.inShadowPass()) return;
        resourcesHeld = true;
        if (faulted) return;
        try {
            if (stage == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
                drawn = false;
                moved = false;
                float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
                drawShell(route == GerRtzPackRoute.DECLARED, GerRtzClientState.lifecycleAlpha(partial));
                drawn = true;
            } else if (stage == RenderLevelStageEvent.Stage.AFTER_WEATHER) {
                if (drawn) moveShellDepth();
            } else if (route == GerRtzPackRoute.UNDECLARED && moved) {
                float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
                paint(event.getModelViewMatrix(), event.getProjectionMatrix(),
                        GerRtzClientState.elapsedTicks() + partial, GerRtzClientState.lifecycleAlpha(partial));
            }
        } catch (RuntimeException | LinkageError failure) {
            faulted = true;
            LOGGER.warn("Return to Zero's shell failed for this Return to Zero; the old mix over the image takes over.", failure);
        }
    }

    /** When the old whole-image mix must carry the cosmos: a pack that this shell cannot serve this time. */
    static boolean needsOldMix() {
        GerRtzPackRoute route = GerRtzPackAccess.route();
        if (route == GerRtzPackRoute.NO_SHADERS) return false;
        return faulted
                || !GL.getCapabilities().OpenGL45
                || route == GerRtzPackRoute.DECLARED && !GerRtzPackAccess.canCaptureEntityId()
                || route == GerRtzPackRoute.UNDECLARED && paintProgram == null;
    }

    private static void drawShell(boolean declared, float envelope) {
        worldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        // The envelope in the sky light: the pack reads it back as the low byte of the data it keeps for each pixel.
        int light = declared
                ? (ENVELOPE_LIGHT_ZERO + Math.round(Math.max(0.0F, Math.min(1.0F, envelope)) * ENVELOPE_LIGHT_RANGE)) << 16
                : FALLBACK_LIGHT;
        RenderType type = SHELL_TYPE;
        try (ByteBufferBuilder storage = new ByteBufferBuilder(SHELL_BYTES);
                MeshData mesh = GerRtzPackAccess.withEntityId(GerRtzPackRoute.MARKER_ID, declared, id -> {
                    BufferBuilder builder = new BufferBuilder(storage, type.mode(), type.format());
                    for (float[] c : CORNERS) {
                        builder.addVertex(c[0], c[1], c[2], BLACK, 0.5F, 0.5F, OverlayTexture.NO_OVERLAY, light, c[3], c[4], c[5]);
                    }
                    return builder.buildOrThrow();
                })) {
            // The pack's shared entity source defers its real flush; this depth must be written now.
            ShaderInstance previousShader = RenderSystem.getShader();
            int previousTexture = RenderSystem.getShaderTexture(0);
            try {
                type.setupRenderState();
                drawAtMarkerDepth(mesh);
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

    private static void drawAtMarkerDepth(MeshData mesh) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var range = stack.mallocDouble(2);
            glGetDoublev(GL_DEPTH_RANGE, range);
            boolean clamp = glIsEnabled(GL_DEPTH_CLAMP);
            try {
                glEnable(GL_DEPTH_CLAMP);
                glDepthRange(MARKER, MARKER);
                BufferUploader.drawWithShader(mesh);
            } finally {
                glDepthRange(range.get(0), range.get(1));
                if (!clamp) glDisable(GL_DEPTH_CLAMP);
            }
        }
    }

    private static float[][] corners() {
        float[][] corners = new float[24][];
        int at = 0;
        for (Direction face : Direction.values()) {
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
                float u = corner == 1 || corner == 2 ? HALF_EDGE : -HALF_EDGE;
                float v = corner >= 2 ? HALF_EDGE : -HALF_EDGE;
                corners[at++] = new float[] {
                    -nx * HALF_EDGE + ux * u + vx * v, -ny * HALF_EDGE + vy * v, -nz * HALF_EDGE + uz * u + vz * v, nx, ny, nz};
            }
        }
        return corners;
    }

    private static void moveShellDepth() {
        if (worldProjection == null || !GL.getCapabilities().OpenGL45) {
            throw new IllegalStateException("OpenGL 4.5 depth copy unavailable");
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer viewport = stack.mallocInt(4);
            glGetIntegerv(GL_VIEWPORT, viewport);
            int x = viewport.get(0);
            int y = viewport.get(1);
            int[] extent = GerRtzPackAccess.renderExtent(viewport.get(2), viewport.get(3));
            int width = extent[0];
            int height = extent[1];
            int depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureId();
            if (depth <= 0 || width <= 0 || height <= 0 || x < 0 || y < 0
                    || x + width > glGetTextureLevelParameteri(depth, 0, GL_TEXTURE_WIDTH)
                    || y + height > glGetTextureLevelParameteri(depth, 0, GL_TEXTURE_HEIGHT)) {
                throw new IllegalStateException("World viewport is outside the current depth texture");
            }
            float replacement = depthAtDistance(worldProjection, COMPOSITE_DISTANCE);
            if (!Float.isFinite(replacement) || replacement <= 0.0F || replacement >= MARKER) {
                throw new IllegalStateException("Invalid projected void depth " + replacement);
            }
            int format = sizedFormat(depth);
            ensureCopy(width, height, format);
            ensureDrawResources();
            // A rejected copy must classify no pixels, never reuse the preceding frame's marker.
            clearCopy(format);
            glCopyImageSubData(depth, GL_TEXTURE_2D, 0, x, y, 0, copy, GL_TEXTURE_2D, 0, 0, 0, 0, width, height, 1);
            glNamedFramebufferTexture(framebuffer, GL_DEPTH_ATTACHMENT, depth, 0);
            if (glCheckNamedFramebufferStatus(framebuffer, GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Void depth framebuffer incomplete");
            }
            rewrite(x, y, width, height, replacement, viewport, stack);
            movedTo = replacement;
            moved = true;
        }
    }

    static float depthAtDistance(Matrix4fc projection, float distance) {
        float clipZ = projection.m32() - distance * projection.m22();
        float clipW = projection.m33() - distance * projection.m23();
        return (clipZ / clipW + 1.0F) * 0.5F;
    }

    private static int sizedFormat(int depth) {
        int format = glGetTextureLevelParameteri(depth, 0, GL_TEXTURE_INTERNAL_FORMAT);
        if (format == GL_DEPTH_COMPONENT) {
            int bits = glGetTextureLevelParameteri(depth, 0, GL_TEXTURE_DEPTH_SIZE);
            int type = glGetTextureLevelParameteri(depth, 0, GL_TEXTURE_DEPTH_TYPE);
            if (bits == 24 && type == GL_UNSIGNED_NORMALIZED) format = GL_DEPTH_COMPONENT24;
            else if (bits == 32) format = type == GL_FLOAT ? GL_DEPTH_COMPONENT32F : GL_DEPTH_COMPONENT32;
        }
        if (format != GL_DEPTH_COMPONENT24 && format != GL_DEPTH_COMPONENT32 && format != GL_DEPTH_COMPONENT32F
                && format != GL_DEPTH24_STENCIL8 && format != GL_DEPTH32F_STENCIL8) {
            throw new IllegalStateException("Unsupported void depth format 0x" + Integer.toHexString(format));
        }
        return format;
    }

    private static void clearCopy(int format) {
        int clearFormat = format == GL_DEPTH24_STENCIL8 || format == GL_DEPTH32F_STENCIL8
                ? GL_DEPTH_STENCIL : GL_DEPTH_COMPONENT;
        int clearType = format == GL_DEPTH24_STENCIL8 ? GL_UNSIGNED_INT_24_8
                : format == GL_DEPTH32F_STENCIL8 ? GL_FLOAT_32_UNSIGNED_INT_24_8_REV : GL_FLOAT;
        glClearTexImage(copy, 0, clearFormat, clearType, (ByteBuffer) null);
    }

    private static void ensureCopy(int width, int height, int format) {
        if (copy != 0 && (width != copyWidth || height != copyHeight || format != copyFormat)) {
            glDeleteTextures(copy);
            copy = 0;
        }
        if (copy == 0) {
            copy = glCreateTextures(GL_TEXTURE_2D);
            glTextureStorage2D(copy, 1, format, width, height);
            glTextureParameteri(copy, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTextureParameteri(copy, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTextureParameteri(copy, GL_TEXTURE_COMPARE_MODE, GL_NONE);
            copyWidth = width;
            copyHeight = height;
            copyFormat = format;
        }
    }

    private static void ensureDrawResources() {
        if (framebuffer == 0) {
            framebuffer = glCreateFramebuffers();
            glNamedFramebufferDrawBuffer(framebuffer, GL_NONE);
            glNamedFramebufferReadBuffer(framebuffer, GL_NONE);
        }
        if (program != 0) return;
        int vertex = compile(GL_VERTEX_SHADER, VERTEX);
        int fragment = 0;
        int candidate = 0;
        try {
            fragment = compile(GL_FRAGMENT_SHADER, FRAGMENT);
            candidate = glCreateProgram();
            glAttachShader(candidate, vertex);
            glAttachShader(candidate, fragment);
            glLinkProgram(candidate);
            if (glGetProgrami(candidate, GL_LINK_STATUS) != GL_TRUE) throw new IllegalStateException(glGetProgramInfoLog(candidate));
            vao = glCreateVertexArrays();
            program = candidate;
        } finally {
            glDeleteShader(vertex);
            if (fragment != 0) glDeleteShader(fragment);
            if (candidate != program && candidate != 0) glDeleteProgram(candidate);
        }
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_TRUE) return shader;
        String message = glGetShaderInfoLog(shader);
        glDeleteShader(shader);
        throw new IllegalStateException(message);
    }

    private static void rewrite(int x, int y, int width, int height, float replacement, IntBuffer viewport, MemoryStack stack) {
        int oldDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int oldTextureUnit = glGetInteger(GL_ACTIVE_TEXTURE);
        int oldSampler = glGetIntegeri(GL_SAMPLER_BINDING, 0);
        int oldDepthFunc = glGetInteger(GL_DEPTH_FUNC);
        boolean oldDepthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        boolean oldDepthTest = glIsEnabled(GL_DEPTH_TEST);
        boolean oldCull = glIsEnabled(GL_CULL_FACE);
        boolean oldScissor = glIsEnabled(GL_SCISSOR_TEST);
        boolean oldStencil = glIsEnabled(GL_STENCIL_TEST);
        IntBuffer polygon = stack.callocInt(2);
        glGetIntegerv(GL_POLYGON_MODE, polygon);
        var depthRange = stack.mallocDouble(2);
        glGetDoublev(GL_DEPTH_RANGE, depthRange);
        RenderSystem.activeTexture(GL_TEXTURE0);
        int oldTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            // A depth-only target with no draw buffers leaves the pack's colour outputs alone.
            RenderSystem.viewport(x, y, width, height);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL_ALWAYS);
            RenderSystem.depthMask(true);
            RenderSystem.disableCull();
            RenderSystem.disableScissor();
            glDisable(GL_STENCIL_TEST);
            glDepthRange(0.0, 1.0);
            glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
            GlStateManager._bindTexture(copy);
            glBindSampler(0, 0);
            glBindVertexArray(vao);
            GlStateManager._glUseProgram(program);
            glProgramUniform2i(program, 0, x, y);
            glProgramUniform1f(program, 1, replacement);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        } finally {
            GlStateManager._glUseProgram(oldProgram);
            glBindVertexArray(oldVao);
            glBindSampler(0, oldSampler);
            GlStateManager._bindTexture(oldTexture);
            RenderSystem.activeTexture(oldTextureUnit);
            glPolygonMode(GL_FRONT_AND_BACK, polygon.get(0));
            glDepthRange(depthRange.get(0), depthRange.get(1));
            if (oldStencil) glEnable(GL_STENCIL_TEST);
            if (oldScissor) GlStateManager._enableScissorTest();
            if (oldCull) RenderSystem.enableCull();
            RenderSystem.depthMask(oldDepthMask);
            RenderSystem.depthFunc(oldDepthFunc);
            if (!oldDepthTest) RenderSystem.disableDepthTest();
            RenderSystem.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldDraw);
        }
    }

    // Over the pack's final image, which is at display size while the depth it read may be at the render's: the moved depth says where the
    // box still shows, whatever came in front of it later, a hand included.
    private static void paint(Matrix4fc view, Matrix4fc projection, float time, float envelope) {
        ShaderInstance shader = paintProgram;
        if (shader == null) return;
        var target = Minecraft.getInstance().getMainRenderTarget();
        int[] extent = GerRtzPackAccess.renderExtent(target.width, target.height);
        boolean depthTest = glIsEnabled(GL_DEPTH_TEST);
        boolean cull = glIsEnabled(GL_CULL_FACE);
        int previousTexture = RenderSystem.getShaderTexture(0);
        try {
            shader.safeGetUniform("InvViewProj").set(new Matrix4f(projection).mul(view).invert());
            shader.safeGetUniform("Time").set(time);
            shader.safeGetUniform("Alpha").set(envelope);
            shader.safeGetUniform("MaskDepth").set(movedTo);
            shader.safeGetUniform("MaskSize").set((float) extent[0], (float) extent[1]);
            shader.safeGetUniform("UseMask").set(1);
            // A draw fills every sampler from these slots, so setSampler would be overwritten.
            RenderSystem.setShaderTexture(0, target.getDepthTextureId());
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(() -> shader);
            BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
            builder.addVertex(-1.0F, -1.0F, 0.0F);
            builder.addVertex(3.0F, -1.0F, 0.0F);
            builder.addVertex(-1.0F, 3.0F, 0.0F);
            BufferUploader.drawWithShader(builder.buildOrThrow());
        } finally {
            RenderSystem.setShaderTexture(0, previousTexture);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            if (depthTest) RenderSystem.enableDepthTest();
            if (cull) RenderSystem.enableCull();
        }
    }

    public static void close() {
        RenderSystem.assertOnRenderThread();
        if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
        if (copy != 0) glDeleteTextures(copy);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (program != 0) glDeleteProgram(program);
        framebuffer = copy = vao = program = 0;
        worldProjection = null;
        drawn = false;
        moved = false;
        faulted = false;
        resourcesHeld = false;
    }
}
