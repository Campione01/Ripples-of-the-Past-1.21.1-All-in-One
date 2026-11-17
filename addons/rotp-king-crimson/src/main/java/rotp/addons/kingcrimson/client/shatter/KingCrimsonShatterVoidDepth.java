package rotp.addons.kingcrimson.client.shatter;

import static org.lwjgl.opengl.GL45C.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;
import net.neoforged.fml.ModList;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterProjection;

/** Rewrites only the shell marker after world and hand rendering, before Iris composites. */
public final class KingCrimsonShatterVoidDepth {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean SWEEP = Boolean.getBoolean("rotp.kingcrimson.shatter.depthSweep");
    private static final boolean DIAGNOSTICS = Boolean.getBoolean("rotp.kingcrimson.shatter.diagnostics");
    private static final float[] SWEEP_DISTANCES = {0.0F, 8.0F, 16.0F, 32.0F};
    private static final String VERTEX = """
            #version 450 core
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String FRAGMENT = """
            #version 450 core
            layout(binding = 0) uniform sampler2D originalDepth;
            layout(location = 0) uniform ivec2 regionOrigin;
            layout(location = 1) uniform float replacementDepth;
            void main() {
                ivec2 p = ivec2(gl_FragCoord.xy) - regionOrigin;
                if (floatBitsToUint(texelFetch(originalDepth, p, 0).r) != 0x3f7fffffu) discard;
                gl_FragDepth = replacementDepth;
            }
            """;
    private static final String READBACK = """
            #version 450 core
            layout(local_size_x = 8, local_size_y = 8) in;
            layout(binding = 0) uniform sampler2D originalDepth;
            layout(binding = 1) uniform sampler2D currentDepth;
            layout(location = 0) uniform ivec2 regionOrigin;
            layout(location = 1) uniform ivec2 regionSize;
            layout(location = 2) uniform float replacementDepth;
            layout(location = 3) uniform bool rewriting;
            layout(std430, binding = 0) buffer Counts {
                uint sampled, markers, markerChanged, rewritten, otherChanged;
            };
            void main() {
                ivec2 p = ivec2(gl_GlobalInvocationID.xy);
                if (any(greaterThanEqual(p, regionSize))) return;
                if (gl_LocalInvocationIndex == 0u) {
                    ivec2 covered = min(ivec2(gl_WorkGroupSize.xy), regionSize - p);
                    atomicAdd(sampled, uint(covered.x * covered.y));
                }
                uint beforeBits = floatBitsToUint(texelFetch(originalDepth, p, 0).r);
                float after = texelFetch(currentDepth, p + regionOrigin, 0).r;
                bool changed = beforeBits != floatBitsToUint(after);
                if (beforeBits == 0x3f7fffffu) {
                    atomicAdd(markers, 1u);
                    if (changed) {
                        atomicAdd(markerChanged, 1u);
                        if (rewriting && abs(after - replacementDepth) <= 1.2e-7) atomicAdd(rewritten, 1u);
                    }
                } else if (changed) {
                    atomicAdd(otherChanged, 1u);
                }
            }
            """;
    private static final int READBACK_COUNTERS = 5;
    private static int program;
    private static int vao;
    private static int framebuffer;
    private static int copy;
    private static int readbackProgram;
    private static int readbackBuffer;
    private static int copyWidth;
    private static int copyHeight;
    private static int copyFormat;
    private static boolean active;
    private static boolean faulted;
    private static boolean logged;
    private static int erase;
    private static int draws;
    private static float distance;
    private static float moved;
    private static Matrix4f worldProjection;

    private KingCrimsonShatterVoidDepth() {}

    public static void rememberWorldProjection() {
        worldProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
    }

    /** The depth of this frame before the shell was moved: its marker says where the void is. 0 when there is none to trust. */
    static int mask() {
        return active && !faulted ? copy : 0;
    }

    /**
     * Gives the last frame's mask to the return fade, which owns and deletes it from then on; 0 when there is none to trust. The next
     * erase allocates a copy of its own.
     */
    static int handOverMask() {
        int mask = mask();
        if (mask != 0) copy = 0;
        return mask;
    }

    static int maskWidth() {
        return copyWidth;
    }

    static int maskHeight() {
        return copyHeight;
    }

    /** The depth the shell was moved to this frame: what stands there is the void. 0 when there is none. */
    public static float movedDepth() {
        return active && !faulted ? moved : 0.0F;
    }

    /** The part of the world's depth texture the pack has filled: Super Resolution renders into a corner of it. */
    public static int[] renderExtent(int width, int height) {
        if (Scale.ACCESS != null && Scale.ACCESS.applies()) {
            return new int[] {Math.min(width, Scale.ACCESS.width()), Math.min(height, Scale.ACCESS.height())};
        }
        return new int[] {width, height};
    }

    static void warmUp() {
        RenderSystem.assertOnRenderThread();
        if (!GL.getCapabilities().OpenGL45) return;
        long start = System.nanoTime();
        int scratch = 0;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            int source = Minecraft.getInstance().getMainRenderTarget().getDepthTextureId();
            int width = glGetTextureLevelParameteri(source, 0, GL_TEXTURE_WIDTH);
            int height = glGetTextureLevelParameteri(source, 0, GL_TEXTURE_HEIGHT);
            if (Scale.ACCESS != null && Scale.ACCESS.applies()) {
                width = Math.min(width, Scale.ACCESS.width());
                height = Math.min(height, Scale.ACCESS.height());
            }
            int format = sizedFormat(source);
            ensureCopy(width, height, format);
            ensureDrawResources();
            clearCopy(format);
            scratch = glCreateTextures(GL_TEXTURE_2D);
            glTextureStorage2D(scratch, 1, format, 1, 1);
            glNamedFramebufferTexture(framebuffer, GL_DEPTH_ATTACHMENT, scratch, 0);
            if (glCheckNamedFramebufferStatus(framebuffer, GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("Warm-up depth framebuffer incomplete");
            }
            IntBuffer viewport = stack.mallocInt(4);
            glGetIntegerv(GL_VIEWPORT, viewport);
            // Exercise the linked depth program against owned scratch storage, never the world attachment.
            draw(0, 0, 1, 1, 0.5F, viewport, stack);
            LOGGER.info("King Crimson void GPU warm-up: {} ms, format=0x{}, scratch only",
                    (System.nanoTime() - start) / 1_000_000.0, Integer.toHexString(format));
        } catch (RuntimeException | LinkageError failure) {
            LOGGER.warn("King Crimson could not warm the void depth program; normal rendering will retry.", failure);
        } finally {
            if (framebuffer != 0) glNamedFramebufferTexture(framebuffer, GL_DEPTH_ATTACHMENT, 0, 0);
            if (scratch != 0) glDeleteTextures(scratch);
        }
    }

    public static void beforeComposites() {
        RenderSystem.assertOnRenderThread();
        if (!KingCrimsonShatterClient.drewIrisVoid()) {
            if (active) close();
            active = false;
            faulted = false;
            logged = false;
            draws = 0;
            return;
        }
        if (!active) {
            active = true;
            distance = SWEEP ? SWEEP_DISTANCES[erase++ % SWEEP_DISTANCES.length]
                    : KingCrimsonShatterIrisConstants.VOID_COMPOSITE_DISTANCE;
            logged = false;
        }
        if (faulted || worldProjection == null) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            if (!GL.getCapabilities().OpenGL45) throw new IllegalStateException("OpenGL 4.5 depth copy unavailable");
            IntBuffer viewport = stack.mallocInt(4);
            glGetIntegerv(GL_VIEWPORT, viewport);
            int x = viewport.get(0), y = viewport.get(1), width = viewport.get(2), height = viewport.get(3);
            if (Scale.ACCESS != null && Scale.ACCESS.applies()) {
                width = Math.min(width, Scale.ACCESS.width());
                height = Math.min(height, Scale.ACCESS.height());
            } else if (Scale.UNAVAILABLE) {
                throw new IllegalStateException("Super Resolution render extent is unavailable");
            }
            int depth = Minecraft.getInstance().getMainRenderTarget().getDepthTextureId();
            if (depth <= 0 || width <= 0 || height <= 0 || x < 0 || y < 0
                    || x + width > glGetTextureLevelParameteri(depth, 0, GL_TEXTURE_WIDTH)
                    || y + height > glGetTextureLevelParameteri(depth, 0, GL_TEXTURE_HEIGHT)) {
                throw new IllegalStateException("World viewport is outside the current depth texture");
            }
            float replacement = distance == 0.0F ? KingCrimsonShatterProjection.VOID_DEPTH
                    : KingCrimsonShatterProjection.depthAtDistance(worldProjection, distance);
            if (!logged) {
                logged = true;
                LOGGER.info("King Crimson void depth: erase={} distance={} depth={} viewport={},{},{}x{} source={} sweep={}",
                        erase, distance, replacement, x, y, width, height, depth, SWEEP);
            }
            boolean rewriting = distance != 0.0F;
            boolean sample = DIAGNOSTICS && (++draws == 1 || draws == 60);
            if (!rewriting && !sample) return;
            if (rewriting && (!Float.isFinite(replacement) || replacement <= 0.0F
                    || replacement >= KingCrimsonShatterProjection.VOID_DEPTH)) {
                throw new IllegalStateException("Invalid projected void depth " + replacement);
            }
            int format = sizedFormat(depth);
            ensureCopy(width, height, format);
            if (rewriting) ensureDrawResources();
            // A rejected copy must classify no pixels, never reuse the preceding frame's shell marker.
            clearCopy(format);
            glCopyImageSubData(depth, GL_TEXTURE_2D, 0, x, y, 0, copy, GL_TEXTURE_2D, 0, 0, 0, 0, width, height, 1);
            if (rewriting) {
                glNamedFramebufferTexture(framebuffer, GL_DEPTH_ATTACHMENT, depth, 0);
                if (glCheckNamedFramebufferStatus(framebuffer, GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("Void depth framebuffer incomplete");
                }
                draw(x, y, width, height, replacement, viewport, stack);
                moved = replacement;
            }
            if (sample) {
                try {
                    measureDepth(depth, x, y, width, height, replacement, rewriting, stack);
                } catch (RuntimeException | LinkageError failure) {
                    LOGGER.warn("King Crimson void depth GPU readback failed.", failure);
                }
            }
        } catch (RuntimeException | LinkageError failure) {
            faulted = true;
            LOGGER.warn("King Crimson void depth rewrite failed for this erase.", failure);
        }
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

    private static void draw(int x, int y, int width, int height, float replacement, IntBuffer viewport, MemoryStack stack) {
        int oldDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int oldTextureUnit = glGetInteger(GL_ACTIVE_TEXTURE);
        int oldSampler = glGetIntegeri(GL_SAMPLER_BINDING, 0);
        int oldDepthFunc = glGetInteger(GL_DEPTH_FUNC);
        boolean oldDepthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        boolean oldDepthTest = glIsEnabled(GL_DEPTH_TEST), oldCull = glIsEnabled(GL_CULL_FACE);
        boolean oldScissor = glIsEnabled(GL_SCISSOR_TEST), oldStencil = glIsEnabled(GL_STENCIL_TEST);
        IntBuffer polygon = stack.callocInt(2);
        glGetIntegerv(GL_POLYGON_MODE, polygon);
        var depthRange = stack.mallocDouble(2);
        glGetDoublev(GL_DEPTH_RANGE, depthRange);
        RenderSystem.activeTexture(GL_TEXTURE0);
        int oldTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
            // A depth-only FBO with GL_NONE draw buffers disables color writes without changing MRT masks.
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

    public static void close() {
        RenderSystem.assertOnRenderThread();
        if (copy != 0) glDeleteTextures(copy);
        if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (program != 0) glDeleteProgram(program);
        if (readbackProgram != 0) glDeleteProgram(readbackProgram);
        if (readbackBuffer != 0) glDeleteBuffers(readbackBuffer);
        copy = framebuffer = vao = program = readbackProgram = readbackBuffer = 0;
        worldProjection = null;
        active = false;
        faulted = false;
        logged = false;
        draws = 0;
    }

    private static void ensureReadbackResources() {
        if (readbackProgram != 0) return;
        int shader = compile(GL_COMPUTE_SHADER, READBACK);
        int candidate = 0, buffer = 0;
        try {
            candidate = glCreateProgram();
            glAttachShader(candidate, shader);
            glLinkProgram(candidate);
            if (glGetProgrami(candidate, GL_LINK_STATUS) != GL_TRUE) throw new IllegalStateException(glGetProgramInfoLog(candidate));
            buffer = glCreateBuffers();
            int bytes = READBACK_COUNTERS * Integer.BYTES;
            glNamedBufferData(buffer, bytes, GL_DYNAMIC_READ);
            if (glGetNamedBufferParameteri(buffer, GL_BUFFER_SIZE) != bytes) {
                throw new IllegalStateException("Void depth GPU readback counter storage unavailable");
            }
            readbackBuffer = buffer;
            readbackProgram = candidate;
        } finally {
            glDeleteShader(shader);
            if (candidate != readbackProgram && candidate != 0) glDeleteProgram(candidate);
            if (buffer != readbackBuffer && buffer != 0) glDeleteBuffers(buffer);
        }
    }

    private static void measureDepth(int depth, int x, int y, int width, int height, float replacement,
            boolean rewriting, MemoryStack stack) {
        ensureReadbackResources();
        IntBuffer counts = stack.callocInt(READBACK_COUNTERS);
        glNamedBufferSubData(readbackBuffer, 0, counts);

        int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int oldGenericBuffer = glGetInteger(GL_SHADER_STORAGE_BUFFER_BINDING);
        int oldIndexedBuffer = glGetIntegeri(GL_SHADER_STORAGE_BUFFER_BINDING, 0);
        long oldStart = glGetInteger64i(GL_SHADER_STORAGE_BUFFER_START, 0);
        long oldSize = glGetInteger64i(GL_SHADER_STORAGE_BUFFER_SIZE, 0);
        int oldTextureUnit = glGetInteger(GL_ACTIVE_TEXTURE);
        int oldSampler0 = glGetIntegeri(GL_SAMPLER_BINDING, 0);
        int oldSampler1 = glGetIntegeri(GL_SAMPLER_BINDING, 1);
        // Keep texture changes local to this dispatch, including when Iris caches a non-2D target on a unit.
        glActiveTexture(GL_TEXTURE0);
        int oldTexture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
        glActiveTexture(GL_TEXTURE1);
        int oldTexture1 = glGetInteger(GL_TEXTURE_BINDING_2D);
        try {
            glBindTexture(GL_TEXTURE_2D, depth);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, copy);
            glBindSampler(0, 0);
            glBindSampler(1, 0);
            glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, readbackBuffer);
            GlStateManager._glUseProgram(readbackProgram);
            glProgramUniform2i(readbackProgram, 0, x, y);
            glProgramUniform2i(readbackProgram, 1, width, height);
            glProgramUniform1f(readbackProgram, 2, replacement);
            glProgramUniform1i(readbackProgram, 3, rewriting ? 1 : 0);
            glDispatchCompute((width + 7) / 8, (height + 7) / 8, 1);
            glMemoryBarrier(GL_BUFFER_UPDATE_BARRIER_BIT);
            glGetNamedBufferSubData(readbackBuffer, 0, counts);
        } finally {
            GlStateManager._glUseProgram(oldProgram);
            if (oldSize > 0) glBindBufferRange(GL_SHADER_STORAGE_BUFFER, 0, oldIndexedBuffer, oldStart, oldSize);
            else glBindBufferBase(GL_SHADER_STORAGE_BUFFER, 0, oldIndexedBuffer);
            glBindBuffer(GL_SHADER_STORAGE_BUFFER, oldGenericBuffer);
            glBindSampler(0, oldSampler0);
            glBindSampler(1, oldSampler1);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, oldTexture0);
            glActiveTexture(GL_TEXTURE1);
            glBindTexture(GL_TEXTURE_2D, oldTexture1);
            glActiveTexture(oldTextureUnit);
        }
        if (counts.get(0) != width * height) {
            throw new IllegalStateException("Void depth GPU readback did not sample the full region: " + counts.get(0));
        }
        LOGGER.info("King Crimson void depth GPU readback: erase={} draw={} distance={} rewrite={} format=0x{} region={}x{} sampled={} markers={} markerChanged={} rewritten={} otherChanged={}",
                erase, draws, distance, rewriting, Integer.toHexString(copyFormat), width, height,
                counts.get(0), counts.get(1), counts.get(2), counts.get(3), counts.get(4));
    }

    private record Scale(MethodHandle enabled, MethodHandle renderWidth, MethodHandle renderHeight) {
        private static final boolean PRESENT = ModList.get().isLoaded("super_resolution");
        private static final Scale ACCESS = resolve();
        private static final boolean UNAVAILABLE = PRESENT && ACCESS == null;

        private static Scale resolve() {
            if (!PRESENT) return null;
            try {
                ClassLoader loader = KingCrimsonShatterVoidDepth.class.getClassLoader();
                Class<?> api = loader.loadClass("io.homo.superresolution.api.SuperResolutionAPI");
                Class<?> iris = loader.loadClass("io.homo.superresolution.shadercompat.IrisShaderCompatUtils");
                var lookup = MethodHandles.publicLookup();
                return new Scale(lookup.findStatic(iris, "shouldApplySuperResolutionChanges", MethodType.methodType(boolean.class)),
                        lookup.findStatic(api, "getRenderWidth", MethodType.methodType(int.class)),
                        lookup.findStatic(api, "getRenderHeight", MethodType.methodType(int.class)));
            } catch (ReflectiveOperationException | LinkageError failure) {
                LOGGER.warn("King Crimson cannot resolve Super Resolution's active render extent.", failure);
                return null;
            }
        }

        boolean applies() { return (boolean) call(enabled); }
        int width() { return (int) call(renderWidth); }
        int height() { return (int) call(renderHeight); }

        private static Object call(MethodHandle handle) {
            try {
                return handle.invoke();
            } catch (Throwable failure) {
                if (failure instanceof Error error && !(error instanceof LinkageError)) throw error;
                throw new IllegalStateException("Could not query the active Super Resolution extent", failure);
            }
        }
    }
}
