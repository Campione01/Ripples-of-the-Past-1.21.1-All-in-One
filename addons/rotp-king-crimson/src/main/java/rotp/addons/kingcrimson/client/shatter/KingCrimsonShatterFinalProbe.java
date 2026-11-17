package rotp.addons.kingcrimson.client.shatter;

import static org.lwjgl.opengl.GL45C.*;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Minecraft;

/** Diagnostic only: marks conservative world-depth matches in the final image, not proven background pixels. */
public final class KingCrimsonShatterFinalProbe {
    private static final String PROPERTY = "rotp.kingcrimson.shatter.finalProbe";
    public static final boolean ENABLED = Boolean.getBoolean(PROPERTY);
    private static final boolean READBACK = ENABLED && Boolean.getBoolean(PROPERTY + ".readback");
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int MAX_READBACK_PIXELS = 1920 * 1080;
    private static final int[] DISABLED_DURING_DRAW = {
            GL_DEPTH_TEST, GL_CULL_FACE, GL_STENCIL_TEST, GL_COLOR_LOGIC_OP, GL_DITHER, GL_FRAMEBUFFER_SRGB
    };
    private static final String VERTEX = """
            #version 450 core
            void main() {
                vec2 corner = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(corner * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String FRAGMENT = """
            #version 450 core
            layout(binding = 0) uniform sampler2D worldDepth;
            layout(location = 0) uniform ivec2 outputExtent;
            layout(location = 0) out vec4 outputColor;
            void main() {
                ivec2 extent = textureSize(worldDepth, 0);
                ivec2 p = ivec2(gl_FragCoord.xy * vec2(extent) / vec2(outputExtent));
                if (any(lessThan(p, ivec2(1))) || any(greaterThanEqual(p, extent - 1))) discard;
                for (int y = -1; y <= 1; ++y) {
                    for (int x = -1; x <= 1; ++x) {
                        // Math.nextDown(1.0f) in D24/F32; never accept clear depth or tolerate near matches.
                        if (floatBitsToUint(texelFetch(worldDepth, p + ivec2(x, y), 0).r) != 0x3f7fffffu) discard;
                    }
                }
                outputColor = vec4(1.0, 0.0, 1.0, 1.0);
            }
            """;

    private static long frameSerial;
    private static boolean frameOpen;
    private static Capture pending;
    private static DepthCopy copy;
    private static int program;
    private static int vertexArray;
    private static int draws;
    private static boolean captureSkipLogged;
    private static boolean applySkipLogged;
    private static boolean faulted;

    private KingCrimsonShatterFinalProbe() {}

    /** RenderFrameEvent.Pre, including frames that will not reach the world or GUI hooks. */
    public static void beginFrame() {
        if (!ENABLED || !RenderSystem.isOnRenderThread()) return;
        ++frameSerial;
        pending = null;
        frameOpen = true;
    }

    /** AFTER_LEVEL, before the hand-depth clear, with the known bottom-left world render extent. */
    public static void capture(int renderWidth, int renderHeight) {
        if (!ENABLED || !RenderSystem.isOnRenderThread()) return;
        pending = null;
        if (!frameOpen || faulted) return;
        frameOpen = false;
        try {
            if (!GL.getCapabilities().OpenGL45) {
                skipCapture("OpenGL 4.5 DSA is unavailable");
                return;
            }
            Minecraft minecraft = Minecraft.getInstance();
            RenderTarget target = minecraft.getMainRenderTarget();
            int width = minecraft.getWindow().getWidth();
            int height = minecraft.getWindow().getHeight();
            int framebuffer = target.frameBufferId;
            int depth = target.getDepthTextureId();
            if (renderWidth < 3 || renderHeight < 3 || renderWidth > width || renderHeight > height
                    || framebuffer <= 0 || !glIsFramebuffer(framebuffer) || !texture2d(depth)
                    || attachment(framebuffer, GL_DEPTH_ATTACHMENT) != depth
                    || level(depth, GL_TEXTURE_WIDTH) < renderWidth || level(depth, GL_TEXTURE_HEIGHT) < renderHeight
                    || glGetInteger(GL_CLIP_ORIGIN) != GL_LOWER_LEFT || glGetDouble(GL_DEPTH_CLEAR_VALUE) != 1.0) {
                skipCapture("world extent or main depth attachment was not recognized");
                return;
            }
            int reportedFormat = level(depth, GL_TEXTURE_INTERNAL_FORMAT);
            int format = sizedDepthFormat(depth, reportedFormat);
            if (format != GL_DEPTH_COMPONENT24 && format != GL_DEPTH24_STENCIL8
                    && format != GL_DEPTH_COMPONENT32 && format != GL_DEPTH_COMPONENT32F
                    && format != GL_DEPTH32F_STENCIL8) {
                skipCapture("unsupported world depth format 0x" + Integer.toHexString(reportedFormat)
                        + ", depth bits=" + level(depth, GL_TEXTURE_DEPTH_SIZE)
                        + ", depth type=0x" + Integer.toHexString(level(depth, GL_TEXTURE_DEPTH_TYPE)));
                return;
            }
            int minFilter = glGetTextureParameteri(depth, GL_TEXTURE_MIN_FILTER);
            if (glGetTextureParameteri(depth, GL_TEXTURE_BASE_LEVEL) != 0 ||
                    (minFilter != GL_NEAREST && minFilter != GL_LINEAR
                            && glGetTextureParameteri(depth, GL_TEXTURE_MAX_LEVEL) != 0)) {
                skipCapture("world depth level zero is not known to be complete");
                return;
            }
            DepthCopy snapshot = ensureCopy(renderWidth, renderHeight, format);
            // Never retain last frame's marker if the driver rejects a copy despite the checks above.
            int clearFormat = format == GL_DEPTH24_STENCIL8 || format == GL_DEPTH32F_STENCIL8
                    ? GL_DEPTH_STENCIL : GL_DEPTH_COMPONENT;
            int clearType = format == GL_DEPTH24_STENCIL8 ? GL_UNSIGNED_INT_24_8
                    : format == GL_DEPTH32F_STENCIL8 ? GL_FLOAT_32_UNSIGNED_INT_24_8_REV : GL_FLOAT;
            glClearTexImage(snapshot.id, 0, clearFormat, clearType, (ByteBuffer) null);
            glCopyImageSubData(depth, GL_TEXTURE_2D, 0, 0, 0, 0,
                    snapshot.id, GL_TEXTURE_2D, 0, 0, 0, 0, renderWidth, renderHeight, 1);
            pending = new Capture(frameSerial, target, framebuffer, width, height, snapshot);
        } catch (RuntimeException | LinkageError failure) {
            fault("world-depth capture", failure);
        }
    }

    /** RenderGuiEvent.Pre after pack final/SR. The frame is consumed even if the output is rejected. */
    public static void apply() {
        if (!ENABLED || !RenderSystem.isOnRenderThread()) return;
        Capture frame = pending;
        pending = null;
        frameOpen = false;
        if (faulted || frame == null || frame.serial != frameSerial) return;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            Minecraft minecraft = Minecraft.getInstance();
            RenderTarget target = minecraft.getMainRenderTarget();
            int framebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            IntBuffer viewport = stack.mallocInt(4);
            glGetIntegerv(GL_VIEWPORT, viewport);
            if (target != frame.target || target.frameBufferId != frame.framebuffer || framebuffer != frame.framebuffer
                    || minecraft.getWindow().getWidth() != frame.width || minecraft.getWindow().getHeight() != frame.height
                    || viewport.get(0) != 0 || viewport.get(1) != 0
                    || viewport.get(2) != frame.width || viewport.get(3) != frame.height
                    || glGetInteger(GL_CLIP_ORIGIN) != GL_LOWER_LEFT || glGetInteger(GL_DRAW_BUFFER0) != GL_COLOR_ATTACHMENT0
                    || glCheckNamedFramebufferStatus(framebuffer, GL_DRAW_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
                if (!applySkipLogged) {
                    skipApply("expected framebuffer " + frame.framebuffer + " and origin-zero " + frame.width + "x"
                            + frame.height + "; draw framebuffer=" + framebuffer + " main=" + target.frameBufferId
                            + " viewport=" + viewport.get(0) + "," + viewport.get(1) + " " + viewport.get(2) + "x"
                            + viewport.get(3) + " window=" + minecraft.getWindow().getWidth() + "x"
                            + minecraft.getWindow().getHeight());
                }
                return;
            }
            int color = attachment(framebuffer, GL_COLOR_ATTACHMENT0);
            if (color == frame.depth.id || color != target.getColorTextureId() || !texture2d(color)) {
                skipApply("final color identity: attached=" + color + ", expected=" + target.getColorTextureId());
                return;
            }
            if (level(color, GL_TEXTURE_WIDTH) != frame.width || level(color, GL_TEXTURE_HEIGHT) != frame.height) {
                skipApply("final color storage differs from the output size");
                return;
            }
            String blockedState = blockedDrawState(stack);
            if (blockedState != null) {
                skipApply(blockedState);
                return;
            }
            int component = glGetNamedFramebufferAttachmentParameteri(framebuffer, GL_COLOR_ATTACHMENT0,
                    GL_FRAMEBUFFER_ATTACHMENT_COMPONENT_TYPE);
            if (component != GL_UNSIGNED_NORMALIZED && component != GL_FLOAT) {
                skipApply("final color attachment is not normalized or floating point");
                return;
            }
            ensureProgram();
            int draw = ++draws;
            if (draw == 1) {
                LOGGER.info("King Crimson final probe DIAGNOSTIC ONLY: frame={} draw={} world={}x{} output={}x{} depth=0x{}. "
                                + "Only pre-hand world depth is classified; color-only and late geometry may be marked.",
                        frame.serial, draw, frame.depth.width, frame.depth.height, frame.width, frame.height,
                        Integer.toHexString(frame.depth.format));
            }
            drawWithOptionalReadback(frame, color, component, draw, stack);
        } catch (RuntimeException | LinkageError failure) {
            fault("final-output composite", failure);
        }
    }

    /** Release only probe-owned objects. Session/logout may call this more than once. */
    public static void close() {
        if (!ENABLED) return;
        if (!RenderSystem.isOnRenderThread()) {
            RenderSystem.recordRenderCall(KingCrimsonShatterFinalProbe::close);
            return;
        }
        pending = null;
        frameOpen = false;
        faulted = captureSkipLogged = applySkipLogged = false;
        draws = 0;
        if (copy != null) glDeleteTextures(copy.id);
        if (program != 0) glDeleteProgram(program);
        if (vertexArray != 0) glDeleteVertexArrays(vertexArray);
        copy = null;
        program = vertexArray = 0;
    }

    private static boolean texture2d(int texture) {
        return texture > 0 && glIsTexture(texture) && glGetTextureParameteri(texture, GL_TEXTURE_TARGET) == GL_TEXTURE_2D;
    }

    private static int level(int texture, int parameter) {
        return glGetTextureLevelParameteri(texture, 0, parameter);
    }

    private static int sizedDepthFormat(int depth, int reportedFormat) {
        if (reportedFormat != GL_DEPTH_COMPONENT) return reportedFormat;
        int bits = level(depth, GL_TEXTURE_DEPTH_SIZE);
        int type = level(depth, GL_TEXTURE_DEPTH_TYPE);
        if (type == GL_FLOAT && bits == 32) return GL_DEPTH_COMPONENT32F;
        if (type == GL_UNSIGNED_NORMALIZED && bits == 24) return GL_DEPTH_COMPONENT24;
        if (type == GL_UNSIGNED_NORMALIZED && bits == 32) return GL_DEPTH_COMPONENT32;
        return 0;
    }

    private static int attachment(int framebuffer, int point) {
        if (glGetNamedFramebufferAttachmentParameteri(framebuffer, point, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_TYPE) != GL_TEXTURE
                || glGetNamedFramebufferAttachmentParameteri(framebuffer, point, GL_FRAMEBUFFER_ATTACHMENT_TEXTURE_LEVEL) != 0) {
            return 0;
        }
        return glGetNamedFramebufferAttachmentParameteri(framebuffer, point, GL_FRAMEBUFFER_ATTACHMENT_OBJECT_NAME);
    }

    private static String blockedDrawState(MemoryStack stack) {
        IntBuffer polygonMode = stack.callocInt(2);
        glGetIntegerv(GL_POLYGON_MODE, polygonMode);
        int currentProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int front = polygonMode.get(0);
        int back = polygonMode.get(1);
        if ((front != GL_FILL && front != GL_LINE && front != GL_POINT)
                || (back != 0 && front != back)) {
            return "final polygon state cannot be restored: front=" + front + ", back=" + back;
        }
        if (glIsEnabled(GL_RASTERIZER_DISCARD)) return "final raster discard is active";
        if (glGetBoolean(GL_TRANSFORM_FEEDBACK_ACTIVE)) return "final transform feedback is active";
        if (currentProgram != 0 && (glGetProgrami(currentProgram, GL_DELETE_STATUS) == GL_TRUE
                || glGetProgrami(currentProgram, GL_LINK_STATUS) != GL_TRUE)) return "final program cannot be restored";
        for (int i = 1, count = glGetInteger(GL_MAX_DRAW_BUFFERS); i < count; ++i) {
            if (glGetInteger(GL_DRAW_BUFFER0 + i) != GL_NONE) return "unexpected active final draw buffer " + i;
        }
        for (int i = 0, count = glGetInteger(GL_MAX_CLIP_DISTANCES); i < count; ++i) {
            if (glIsEnabled(GL_CLIP_DISTANCE0 + i)) return "unexpected final clip distance " + i;
        }
        return null;
    }

    private static DepthCopy ensureCopy(int width, int height, int format) {
        if (copy != null) {
            if (copy.width == width && copy.height == height && copy.format == format) return copy;
            glDeleteTextures(copy.id);
            copy = null;
        }
        int texture = glCreateTextures(GL_TEXTURE_2D);
        if (texture == 0) throw new IllegalStateException("Could not allocate probe depth texture");
        try {
            glTextureStorage2D(texture, 1, format, width, height);
            if (level(texture, GL_TEXTURE_WIDTH) != width || level(texture, GL_TEXTURE_HEIGHT) != height
                    || level(texture, GL_TEXTURE_INTERNAL_FORMAT) != format) {
                throw new IllegalStateException("Probe depth texture storage was not allocated");
            }
            glTextureParameteri(texture, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
            glTextureParameteri(texture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
            glTextureParameteri(texture, GL_TEXTURE_COMPARE_MODE, GL_NONE);
            copy = new DepthCopy(texture, width, height, format);
            return copy;
        } finally {
            if (copy == null) glDeleteTextures(texture);
        }
    }

    private static void ensureProgram() {
        if (program != 0) return;
        int vertex = 0, fragment = 0, candidate = 0;
        try {
            vertex = compile(GL_VERTEX_SHADER, VERTEX);
            fragment = compile(GL_FRAGMENT_SHADER, FRAGMENT);
            candidate = glCreateProgram();
            if (candidate == 0) throw new IllegalStateException("Could not allocate probe program");
            glAttachShader(candidate, vertex);
            glAttachShader(candidate, fragment);
            glLinkProgram(candidate);
            if (glGetProgrami(candidate, GL_LINK_STATUS) != GL_TRUE) {
                throw new IllegalStateException(glGetProgramInfoLog(candidate));
            }
            glDetachShader(candidate, vertex);
            glDetachShader(candidate, fragment);
            vertexArray = glCreateVertexArrays();
            if (vertexArray == 0) throw new IllegalStateException("Could not allocate probe VAO");
            program = candidate;
        } finally {
            if (candidate != program && candidate != 0) glDeleteProgram(candidate);
            if (fragment != 0) glDeleteShader(fragment);
            if (vertex != 0) glDeleteShader(vertex);
        }
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        if (shader == 0) throw new IllegalStateException("Could not allocate probe shader");
        try {
            glShaderSource(shader, source);
            glCompileShader(shader);
            if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_TRUE) return shader;
            throw new IllegalStateException(glGetShaderInfoLog(shader));
        } catch (RuntimeException | LinkageError failure) {
            glDeleteShader(shader);
            throw failure;
        }
    }

    private static void draw(Capture frame, MemoryStack stack) {
        IntBuffer polygonMode = stack.callocInt(2);
        glGetIntegerv(GL_POLYGON_MODE, polygonMode);
        int previousPolygonMode = polygonMode.get(0);
        int previousVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        int previousProgram = glGetInteger(GL_CURRENT_PROGRAM);
        int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        int sampler = glGetIntegeri(GL_SAMPLER_BINDING, 0);
        boolean blend = glIsEnabledi(GL_BLEND, 0);
        boolean scissor = glIsEnabledi(GL_SCISSOR_TEST, 0);
        boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        ByteBuffer colorMask = stack.malloc(4);
        glGetBooleani_v(GL_COLOR_WRITEMASK, 0, colorMask);
        boolean[] wasEnabled = new boolean[DISABLED_DURING_DRAW.length];
        for (int i = 0; i < wasEnabled.length; ++i) wasEnabled[i] = glIsEnabled(DISABLED_DURING_DRAW[i]);

        // Raw GL only until restoration: do not perturb Blaze3D's cached state or other texture targets.
        glActiveTexture(GL_TEXTURE0);
        try {
            int texture = glGetInteger(GL_TEXTURE_BINDING_2D);
            try {
                for (int flag : DISABLED_DURING_DRAW) glDisable(flag);
                glDisablei(GL_BLEND, 0);
                glDisablei(GL_SCISSOR_TEST, 0);
                glDepthMask(false);
                glColorMaski(0, true, true, true, false);
                glBindTexture(GL_TEXTURE_2D, frame.depth.id);
                glBindSampler(0, 0);
                glBindVertexArray(vertexArray);
                glUseProgram(program);
                glProgramUniform2i(program, 0, frame.width, frame.height);
                if (previousPolygonMode != GL_FILL) glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
                glDrawArrays(GL_TRIANGLES, 0, 3);
            } finally {
                if (previousPolygonMode != GL_FILL) glPolygonMode(GL_FRONT_AND_BACK, previousPolygonMode);
                glUseProgram(previousProgram);
                glBindVertexArray(previousVao);
                glBindSampler(0, sampler);
                glBindTexture(GL_TEXTURE_2D, texture);
                glColorMaski(0, colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
                glDepthMask(depthMask);
                if (scissor) glEnablei(GL_SCISSOR_TEST, 0);
                if (blend) glEnablei(GL_BLEND, 0);
                for (int i = 0; i < wasEnabled.length; ++i) {
                    if (wasEnabled[i]) glEnable(DISABLED_DURING_DRAW[i]);
                }
            }
        } finally {
            glActiveTexture(activeTexture);
        }
    }

    private static void drawWithOptionalReadback(Capture frame, int color, int component, int draw, MemoryStack stack) {
        if (!READBACK || (draw > 2 && draw != 12 && draw != 60)) {
            draw(frame, stack);
            return;
        }
        if ((long) frame.width * frame.height > MAX_READBACK_PIXELS || component != GL_UNSIGNED_NORMALIZED
                || level(color, GL_TEXTURE_RED_SIZE) != 8 || level(color, GL_TEXTURE_GREEN_SIZE) != 8
                || level(color, GL_TEXTURE_BLUE_SIZE) != 8 || glGetInteger(GL_PIXEL_PACK_BUFFER_BINDING) != 0
                || glGetInteger(GL_PACK_ROW_LENGTH) != 0 || glGetInteger(GL_PACK_SKIP_PIXELS) != 0
                || glGetInteger(GL_PACK_SKIP_ROWS) != 0 || glGetInteger(GL_PACK_SKIP_IMAGES) != 0
                || glGetInteger(GL_PACK_IMAGE_HEIGHT) != 0) {
            if (draw == 1) LOGGER.info("King Crimson final probe RGB8 skipped: output format, size, or pack state unsupported.");
            draw(frame, stack);
            return;
        }
        int alignment = glGetInteger(GL_PACK_ALIGNMENT);
        int stride = (frame.width * 3 + alignment - 1) & -alignment;
        ByteBuffer before = null, after = null;
        try {
            before = MemoryUtil.memCalloc(stride * frame.height);
            after = MemoryUtil.memCalloc(stride * frame.height);
            glGetTextureSubImage(color, 0, 0, 0, 0, frame.width, frame.height, 1, GL_RGB, GL_UNSIGNED_BYTE, before);
            draw(frame, stack);
            glGetTextureSubImage(color, 0, 0, 0, 0, frame.width, frame.height, 1, GL_RGB, GL_UNSIGNED_BYTE, after);
            int beforeMagenta = 0, afterMagenta = 0, newlyMagenta = 0, otherChanged = 0;
            for (int y = 0; y < frame.height; ++y) {
                for (int x = 0, at = y * stride; x < frame.width; ++x, at += 3) {
                    boolean was = magenta(before, at), now = magenta(after, at);
                    if (was) ++beforeMagenta;
                    if (now) ++afterMagenta;
                    if (now && !was) ++newlyMagenta;
                    if (!now && (before.get(at) != after.get(at) || before.get(at + 1) != after.get(at + 1)
                            || before.get(at + 2) != after.get(at + 2))) ++otherChanged;
                }
            }
            LOGGER.info("King Crimson final probe RGB8 DIAGNOSTIC ONLY: frame={} draw={} pixels={} beforeMagenta={} "
                            + "afterMagenta={} newlyMagenta={} otherChanged={}", frame.serial, draw,
                    frame.width * frame.height, beforeMagenta, afterMagenta, newlyMagenta, otherChanged);
        } finally {
            MemoryUtil.memFree(after);
            MemoryUtil.memFree(before);
        }
    }

    private static boolean magenta(ByteBuffer rgb, int at) {
        return rgb.get(at) == (byte) 255 && rgb.get(at + 1) == 0 && rgb.get(at + 2) == (byte) 255;
    }

    private static void skipCapture(String reason) {
        if (!captureSkipLogged) {
            captureSkipLogged = true;
            LOGGER.info("King Crimson final probe capture skipped (DIAGNOSTIC ONLY): {}.", reason);
        }
    }

    private static void skipApply(String reason) {
        if (!applySkipLogged) {
            applySkipLogged = true;
            LOGGER.info("King Crimson final probe composite skipped (DIAGNOSTIC ONLY): {}.", reason);
        }
    }

    private static void fault(String stage, Throwable failure) {
        pending = null;
        faulted = true;
        LOGGER.warn("King Crimson final probe disabled for this session after {} failed (DIAGNOSTIC ONLY).", stage, failure);
    }

    private record DepthCopy(int id, int width, int height, int format) {}

    private record Capture(long serial, RenderTarget target, int framebuffer, int width, int height, DepthCopy depth) {}
}
