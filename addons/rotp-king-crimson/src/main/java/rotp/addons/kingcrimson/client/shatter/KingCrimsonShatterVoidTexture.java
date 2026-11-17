package rotp.addons.kingcrimson.client.shatter;

import static org.lwjgl.opengl.GL45C.*;

import java.nio.FloatBuffer;
import java.util.Random;

import org.lwjgl.opengl.GL;
import org.lwjgl.system.MemoryStack;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;

/** Produces the camera-independent color atlas sampled by the Iris void shell. */
public final class KingCrimsonShatterVoidTexture {
    static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            KingCrimsonAddon.MOD_ID, "shatter/void_atlas");
    private static final int COLUMNS = 3;
    private static final int ROWS = 2;
    private static final int FACE_PIXELS = 1024;
    private static final int WIDTH = COLUMNS * FACE_PIXELS;
    private static final int HEIGHT = ROWS * FACE_PIXELS;
    private static final int STAR_COUNT = COLUMNS * ROWS * KingCrimsonShatterIrisConstants.VOID_STARS_PER_FACE;
    private static final float STAR_DEPTH_INSET = 1.0F;
    private static final String VERTEX = """
            #version 450 core
            layout(location = 0) in vec4 bounds;
            void main() {
                vec2 corner = vec2(gl_VertexID & 1, (gl_VertexID >> 1) & 1);
                gl_Position = vec4(mix(bounds.xy, bounds.zw, corner) * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String FRAGMENT = """
            #version 450 core
            layout(location = 0) uniform vec4 starColor;
            layout(location = 0) out vec4 color;
            void main() { color = starColor; }
            """;

    private static AtlasTexture atlas;
    private static int framebuffer;
    private static int program;
    private static int vao;
    private static int stars;

    private KingCrimsonShatterVoidTexture() {}

    static float u(int face, float local) {
        return (face % COLUMNS * FACE_PIXELS + 0.5F + local * (FACE_PIXELS - 1)) / WIDTH;
    }

    static float v(int face, float local) {
        return (face / COLUMNS * FACE_PIXELS + 0.5F + local * (FACE_PIXELS - 1)) / HEIGHT;
    }

    /** Fill the atlas once before the shell draw. The static placeholder does not animate with time. */
    static boolean fill(double seconds) {
        RenderSystem.assertOnRenderThread();
        var window = Minecraft.getInstance().getWindow();
        if (window.getWidth() <= 0 || window.getHeight() <= 0) return false;
        try {
            if (atlas == null) create();
            drawPlaceholder();
            return true;
        } catch (RuntimeException | LinkageError failure) {
            try {
                close();
            } catch (RuntimeException | LinkageError cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    private static void create() {
        if (!GL.getCapabilities().OpenGL45) throw new IllegalStateException("OpenGL 4.5 void atlas unavailable");
        atlas = new AtlasTexture();
        int texture = atlas.getId();
        glTextureStorage2D(texture, 1, GL_RGBA8, WIDTH, HEIGHT);
        glTextureParameteri(texture, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTextureParameteri(texture, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        glTextureParameteri(texture, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTextureParameteri(texture, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        framebuffer = glCreateFramebuffers();
        glNamedFramebufferTexture(framebuffer, GL_COLOR_ATTACHMENT0, texture, 0);
        glNamedFramebufferDrawBuffer(framebuffer, GL_COLOR_ATTACHMENT0);
        if (glCheckNamedFramebufferStatus(framebuffer, GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("King Crimson void atlas framebuffer incomplete");
        }
        program = createProgram();
        int color = KingCrimsonShatterIrisConstants.VOID_STAR_COLOR;
        glProgramUniform4f(program, 0, channel(color, 16), channel(color, 8), channel(color, 0), channel(color, 24));
        vao = glCreateVertexArrays();
        stars = glCreateBuffers();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glNamedBufferStorage(stars, starBounds(stack.mallocFloat(STAR_COUNT * 4)), 0);
        }
        glVertexArrayVertexBuffer(vao, 0, stars, 0, 4 * Float.BYTES);
        glVertexArrayAttribFormat(vao, 0, 4, GL_FLOAT, false, 0);
        glVertexArrayAttribBinding(vao, 0, 0);
        glEnableVertexArrayAttrib(vao, 0);
        glVertexArrayBindingDivisor(vao, 0, 1);
        Minecraft.getInstance().getTextureManager().register(TEXTURE, atlas);
    }

    private static FloatBuffer starBounds(FloatBuffer bounds) {
        var random = new Random(KingCrimsonPhase5Spec.TIME_ERASE_SKY_RANDOM_SEED);
        float distance = KingCrimsonShatterIrisConstants.VOID_HALF_EDGE - STAR_DEPTH_INSET;
        float half = KingCrimsonShatterIrisConstants.VOID_STAR_HALF_WIDTH;
        float range = distance - half;
        for (int face = 0; face < COLUMNS * ROWS; face++) {
            for (int star = 0; star < KingCrimsonShatterIrisConstants.VOID_STARS_PER_FACE; star++) {
                float x = (random.nextFloat() * 2.0F - 1.0F) * range;
                float y = (random.nextFloat() * 2.0F - 1.0F) * range;
                // Normalize by the old inset star plane to keep the same rays on the shell face.
                bounds.put(u(face, 0.5F + (x - half) / (2.0F * distance)));
                bounds.put(v(face, 0.5F + (y - half) / (2.0F * distance)));
                bounds.put(u(face, 0.5F + (x + half) / (2.0F * distance)));
                bounds.put(v(face, 0.5F + (y + half) / (2.0F * distance)));
            }
        }
        return bounds.flip();
    }

    private static void drawPlaceholder() {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var viewport = stack.mallocInt(4);
            glGetIntegerv(GL_VIEWPORT, viewport);
            var polygon = stack.mallocInt(2);
            glGetIntegerv(GL_POLYGON_MODE, polygon);
            var mask = stack.malloc(4);
            glGetBooleani_v(GL_COLOR_WRITEMASK, 0, mask);
            int oldDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
            int oldProgram = glGetInteger(GL_CURRENT_PROGRAM);
            int oldVao = glGetInteger(GL_VERTEX_ARRAY_BINDING);
            boolean blend = glIsEnabledi(GL_BLEND, 0), scissor = glIsEnabledi(GL_SCISSOR_TEST, 0);
            boolean cull = glIsEnabled(GL_CULL_FACE), logic = glIsEnabled(GL_COLOR_LOGIC_OP);
            boolean discard = glIsEnabled(GL_RASTERIZER_DISCARD);
            try {
                GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
                RenderSystem.viewport(0, 0, WIDTH, HEIGHT);
                // Scope indexed overrides to the atlas output; never change the other MRT masks or blend states.
                if (blend) glDisablei(GL_BLEND, 0);
                if (scissor) glDisablei(GL_SCISSOR_TEST, 0);
                if (cull) RenderSystem.disableCull();
                if (logic) GlStateManager._disableColorLogicOp();
                if (discard) glDisable(GL_RASTERIZER_DISCARD);
                glColorMaski(0, true, true, true, true);
                glPolygonMode(GL_FRONT_AND_BACK, GL_FILL);
                int color = KingCrimsonShatterIrisConstants.VOID_COLOR;
                glClearNamedFramebufferfv(framebuffer, GL_COLOR, 0, stack.floats(
                        channel(color, 16), channel(color, 8), channel(color, 0), channel(color, 24)));
                GlStateManager._glUseProgram(program);
                glBindVertexArray(vao);
                glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, STAR_COUNT);
            } finally {
                glBindVertexArray(oldVao);
                GlStateManager._glUseProgram(oldProgram);
                glPolygonMode(GL_FRONT_AND_BACK, polygon.get(0));
                glColorMaski(0, mask.get(0) != 0, mask.get(1) != 0, mask.get(2) != 0, mask.get(3) != 0);
                if (discard) glEnable(GL_RASTERIZER_DISCARD);
                if (logic) GlStateManager._enableColorLogicOp();
                if (cull) RenderSystem.enableCull();
                if (scissor) glEnablei(GL_SCISSOR_TEST, 0);
                if (blend) glEnablei(GL_BLEND, 0);
                RenderSystem.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
                GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldDraw);
            }
        }
    }

    private static float channel(int argb, int shift) {
        return (argb >>> shift & 0xFF) / 255.0F;
    }

    private static int createProgram() {
        int vertex = compile(GL_VERTEX_SHADER, VERTEX);
        int fragment = 0;
        int result = 0;
        boolean linked = false;
        try {
            fragment = compile(GL_FRAGMENT_SHADER, FRAGMENT);
            result = glCreateProgram();
            glAttachShader(result, vertex);
            glAttachShader(result, fragment);
            glLinkProgram(result);
            linked = glGetProgrami(result, GL_LINK_STATUS) == GL_TRUE;
            if (!linked) throw new IllegalStateException(glGetProgramInfoLog(result));
            return result;
        } finally {
            glDeleteShader(vertex);
            if (fragment != 0) glDeleteShader(fragment);
            if (!linked && result != 0) glDeleteProgram(result);
        }
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_TRUE) return shader;
        String log = glGetShaderInfoLog(shader);
        glDeleteShader(shader);
        throw new IllegalStateException(log);
    }

    public static void close() {
        if (atlas == null) return;
        RenderSystem.assertOnRenderThread();
        if (framebuffer != 0) glDeleteFramebuffers(framebuffer);
        if (vao != 0) glDeleteVertexArrays(vao);
        if (stars != 0) glDeleteBuffers(stars);
        if (program != 0) glDeleteProgram(program);
        var textures = Minecraft.getInstance().getTextureManager();
        if (textures.getTexture(TEXTURE, null) == atlas) textures.release(TEXTURE);
        else atlas.releaseId();
        framebuffer = program = vao = stars = 0;
        atlas = null;
    }

    private static final class AtlasTexture extends AbstractTexture {
        private AtlasTexture() {
            id = glCreateTextures(GL_TEXTURE_2D);
        }

        @Override
        public void setFilter(boolean blur, boolean mipmap) {
            RenderSystem.assertOnRenderThreadOrInit();
            this.blur = blur;
            this.mipmap = mipmap;
            // RenderType setup must not bind this atlas on an unrelated active Iris sampler unit.
            int min = blur ? (mipmap ? GL_LINEAR_MIPMAP_LINEAR : GL_LINEAR)
                    : (mipmap ? GL_NEAREST_MIPMAP_LINEAR : GL_NEAREST);
            glTextureParameteri(getId(), GL_TEXTURE_MIN_FILTER, min);
            glTextureParameteri(getId(), GL_TEXTURE_MAG_FILTER, blur ? GL_LINEAR : GL_NEAREST);
        }

        @Override
        public void load(ResourceManager resources) {
            // The atlas is filled by its owner, never by a resource-pack image.
        }
    }
}
