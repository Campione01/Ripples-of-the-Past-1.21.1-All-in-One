package rotp.addons.kingcrimson.client;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;

import org.joml.Matrix4f;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL31;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterPackRouter;
import rotp.addons.kingcrimson.client.shatter.KingCrimsonShatterVoidDepth;
import rotp.addons.kingcrimson.shatter.KingCrimsonShatterPackRoute;
import rotp.addons.kingcrimson.shatter.KingCrimsonTerrainOutlineField;
import rotp.addons.kingcrimson.shatter.KingCrimsonTerrainOutlineScan;

/**
 * The owner's outlines for Time Erase's movement: the world is gone but its blocks still stop the eraser, so what blocks the way around him
 * shows as a faint dark red line, and what lies under his feet as a thinner, fainter one. Drawn after everything the pack does, because a
 * thin line in the gbuffer dies under an upscaler at ratio 3: here it is crisp at display resolution. The depth of the scene still hides it
 * behind a Stand or a creature. The player has to judge depth from them, so they are no see-through wireframe: the faces those edges bound
 * go into a depth of their own first, and a line behind a nearer solid is not drawn. One buffer holds all the edges in reach and one all the
 * faces; colour, width and the fade with distance are the shader's, so the buffers change only when sections come into reach. Nothing at
 * all runs outside an erase.
 */
@OnlyIn(Dist.CLIENT)
public final class KingCrimsonTimeEraseTerrainOutline {
    private static final Logger LOGGER = LogUtils.getLogger();
    // The plates still stand where the blocks are at first: the lines come up as those go.
    private static final float FADE_IN_FROM_TICK = 12.0F;
    private static final float FADE_IN_TICKS = 28.0F;
    private static final long READ_BUDGET_NANOS = 2_000_000L;
    // While the sections of a new place come in, the buffer follows every so many frames, not with each.
    private static final int UPLOAD_EVERY_FRAMES = 6;
    private static final int SEGMENT_BYTES = KingCrimsonTerrainOutlineScan.STRIDE * Float.BYTES;

    private static ShaderInstance program;
    private static final KingCrimsonTerrainOutlineField FIELD = new KingCrimsonTerrainOutlineField();
    private static ClientLevel fieldLevel;
    private static long startedAt = Long.MIN_VALUE;
    private static long revalidatedAtTick;
    private static boolean failed;
    private static boolean active;
    private static int framesSinceUpload;
    // Pushed back this far in the faces' depth: what lies on a face passes the test, on a slope as well.
    private static final float FACE_PUSH_SLOPE = 3.0F;
    private static final float FACE_PUSH_UNITS = 8.0F;
    private static final int MODE_LINES = 0;
    private static final int MODE_DEPTH = 1;
    private static final int MODE_FILL = 2;
    private static int edgeArray;
    private static int edgeBuffer;
    private static int faceArray;
    private static int faceBuffer;
    private static FloatBuffer edgeVertices;
    private static FloatBuffer faceVertices;
    private static int segments;
    private static int faces;
    private static int faceDepth;
    private static int faceTarget;
    private static int faceDepthWidth;
    private static int faceDepthHeight;
    private static int uploads;
    private static long worstUploadNanos;

    private KingCrimsonTimeEraseTerrainOutline() {}

    public static void onRegisterShaders(RegisterShadersEvent event) {
        program = null;
        try {
            // Position and Color name the two attributes of each edge and face: one end or corner, then the other and its height or kind.
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), KingCrimsonAddon.id("time_erase_outline"), DefaultVertexFormat.POSITION_COLOR),
                    loaded -> program = loaded);
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Unable to load the terrain outline's program; the erase goes on without the outlines.", failure);
        }
    }

    public static void afterLevel(RenderLevelStageEvent event) {
        boolean erasing = KingCrimsonTemporalClientState.localTimeErase();
        if (!erasing) {
            close();
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (program == null || failed || level == null || player == null) return;
        try {
            long started = KingCrimsonAddon.temporalState(player).startedAt();
            if (started != startedAt || level != fieldLevel) {
                release();
                startedAt = started;
                fieldLevel = level;
            }
            float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
            float fadeIn = fadeIn(level.getGameTime() - startedAt + partial);
            if (fadeIn <= 0.0F) return;
            active = true;
            BlockPos feet = player.blockPosition();
            FIELD.update(level, feet, true, READ_BUDGET_NANOS);
            if (level.getGameTime() != revalidatedAtTick) {
                revalidatedAtTick = level.getGameTime();
                FIELD.revalidateOne(level);
            }
            framesSinceUpload++;
            if (FIELD.changed() && (FIELD.complete() || framesSinceUpload >= UPLOAD_EVERY_FRAMES)) {
                upload();
            }
            if (segments > 0 || faces > 0) {
                draw(event, player.getPosition(partial), feet, fadeIn);
            }
        } catch (RuntimeException | LinkageError failure) {
            failed = true;
            LOGGER.warn("The terrain outlines could not draw; the erase goes on without them.", failure);
        }
    }

    private static float fadeIn(float ticks) {
        float t = (ticks - FADE_IN_FROM_TICK) / FADE_IN_TICKS;
        t = Math.max(0.0F, Math.min(1.0F, t));
        return t * t * (3.0F - 2.0F * t);
    }

    private static void upload() {
        long started = System.nanoTime();
        edgeVertices = room(edgeVertices, FIELD.edgeFloatsToWrite());
        faceVertices = room(faceVertices, FIELD.faceFloatsToWrite());
        segments = FIELD.write(edgeVertices, faceVertices);
        faces = FIELD.facesWritten();
        edgeVertices.flip();
        faceVertices.flip();
        int previousBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        if (edgeBuffer == 0) createBuffers();
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, edgeBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, edgeVertices, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, faceBuffer);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, faceVertices, GL15.GL_DYNAMIC_DRAW);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, previousBuffer);
        framesSinceUpload = 0;
        uploads++;
        worstUploadNanos = Math.max(worstUploadNanos, System.nanoTime() - started);
    }

    private static FloatBuffer room(FloatBuffer held, int floats) {
        if (held == null || held.capacity() < floats) {
            if (held != null) MemoryUtil.memFree(held);
            held = MemoryUtil.memAllocFloat(Math.max(floats + floats / 2, KingCrimsonTerrainOutlineScan.STRIDE * 1024));
        }
        held.clear();
        return held;
    }

    private static void createBuffers() {
        if (!GL.getCapabilities().OpenGL33) throw new IllegalStateException("Instanced drawing needs OpenGL 3.3");
        BufferUploader.reset();
        edgeBuffer = GL15.glGenBuffers();
        faceBuffer = GL15.glGenBuffers();
        edgeArray = arrayOver(edgeBuffer);
        faceArray = arrayOver(faceBuffer);
        GlStateManager._glBindVertexArray(0);
    }

    private static int arrayOver(int buffer) {
        int array = GL30.glGenVertexArrays();
        GlStateManager._glBindVertexArray(array);
        GlStateManager._glBindBuffer(GL15.GL_ARRAY_BUFFER, buffer);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 3, GL11.GL_FLOAT, false, SEGMENT_BYTES, 0L);
        GL33.glVertexAttribDivisor(0, 1);
        GL20.glEnableVertexAttribArray(1);
        GL20.glVertexAttribPointer(1, 4, GL11.GL_FLOAT, false, SEGMENT_BYTES, 3L * Float.BYTES);
        GL33.glVertexAttribDivisor(1, 1);
        return array;
    }

    private static void ensureFaceDepth(int width, int height) {
        if (faceDepth != 0 && faceDepthWidth == width && faceDepthHeight == height) return;
        deleteFaceDepth();
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        faceDepth = GL11.glGenTextures();
        GlStateManager._bindTexture(faceDepth);
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_DEPTH_COMPONENT32F, width, height, 0, GL11.GL_DEPTH_COMPONENT, GL11.GL_FLOAT, (ByteBuffer) null);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GlStateManager._bindTexture(previousTexture);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousRead = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        faceTarget = GL30.glGenFramebuffers();
        GlStateManager._glBindFramebuffer(GL30.GL_FRAMEBUFFER, faceTarget);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_DEPTH_ATTACHMENT, GL11.GL_TEXTURE_2D, faceDepth, 0);
        GL11.glDrawBuffer(GL11.GL_NONE);
        GL11.glReadBuffer(GL11.GL_NONE);
        int complete = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, previousRead);
        if (complete != GL30.GL_FRAMEBUFFER_COMPLETE) throw new IllegalStateException("The faces' depth target is incomplete: " + complete);
        faceDepthWidth = width;
        faceDepthHeight = height;
    }

    private static void deleteFaceDepth() {
        if (faceTarget != 0) GL30.glDeleteFramebuffers(faceTarget);
        if (faceDepth != 0) GL11.glDeleteTextures(faceDepth);
        faceTarget = 0;
        faceDepth = 0;
    }

    private static void draw(RenderLevelStageEvent event, Vec3 at, BlockPos feet, float fadeIn) {
        ShaderInstance shader = program;
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        BlockPos origin = FIELD.origin();
        int viewportX;
        int viewportY;
        int viewportWidth;
        int viewportHeight;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var viewport = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            viewportX = viewport.get(0);
            viewportY = viewport.get(1);
            viewportWidth = Math.max(1, viewport.get(2));
            viewportHeight = Math.max(1, viewport.get(3));
        }
        ensureFaceDepth(viewportWidth, viewportHeight);
        Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
        Matrix4f modelView = new Matrix4f(event.getModelViewMatrix());
        var window = Minecraft.getInstance().getWindow();
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean cull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        int previousDraw = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int previousTexture0 = RenderSystem.getShaderTexture(0);
        int previousTexture1 = RenderSystem.getShaderTexture(1);
        try {
            boolean sampled = setUpDepth(shader, projection);
            shader.safeGetUniform("ViewSize").set((float) viewportWidth, (float) viewportHeight);
            shader.safeGetUniform("ViewScale").set(viewportHeight / 1080.0F);
            shader.safeGetUniform("Origin").set((float) (origin.getX() - eye.x), (float) (origin.getY() - eye.y), (float) (origin.getZ() - eye.z));
            shader.safeGetUniform("Feet").set((float) (at.x - origin.getX()), (float) (at.y - origin.getY()), (float) (at.z - origin.getZ()));
            shader.safeGetUniform("FeetLevel").set((float) (feet.getY() - origin.getY()));
            shader.safeGetUniform("Reach").set((float) KingCrimsonTerrainOutlineField.RADIUS,
                    (float) KingCrimsonTerrainOutlineField.REACH_UP, (float) KingCrimsonTerrainOutlineField.REACH_DOWN);
            shader.safeGetUniform("Fade").set(fadeIn);
            BufferUploader.reset();
            RenderSystem.setShader(() -> shader);
            RenderSystem.disableCull();

            // The faces' depth. A sampler must not hold the texture being drawn into.
            RenderSystem.setShaderTexture(1, 0);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, faceTarget);
            GlStateManager._viewport(0, 0, viewportWidth, viewportHeight);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
            GL11.glClearDepth(1.0D);
            GL11.glClear(GL11.GL_DEPTH_BUFFER_BIT);
            RenderSystem.colorMask(false, false, false, false);
            RenderSystem.enablePolygonOffset();
            RenderSystem.polygonOffset(FACE_PUSH_SLOPE, FACE_PUSH_UNITS);
            RenderSystem.disableBlend();
            drawInstances(shader, MODE_DEPTH, faceArray, faces, modelView, projection, window);
            RenderSystem.polygonOffset(0.0F, 0.0F);
            RenderSystem.disablePolygonOffset();
            RenderSystem.colorMask(true, true, true, true);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            GlStateManager._viewport(viewportX, viewportY, viewportWidth, viewportHeight);

            RenderSystem.setShaderTexture(1, faceDepth);
            RenderSystem.depthMask(false);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            if (sampled) {
                RenderSystem.disableDepthTest();
            } else {
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_LEQUAL);
            }
            drawInstances(shader, MODE_FILL, faceArray, faces, modelView, projection, window);
            drawInstances(shader, MODE_LINES, edgeArray, segments, modelView, projection, window);
            GlStateManager._glBindVertexArray(0);
            shader.clear();
        } finally {
            RenderSystem.colorMask(true, true, true, true);
            RenderSystem.disablePolygonOffset();
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, previousDraw);
            GlStateManager._viewport(viewportX, viewportY, viewportWidth, viewportHeight);
            RenderSystem.setShaderTexture(0, previousTexture0);
            RenderSystem.setShaderTexture(1, previousTexture1);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            if (depthTest) RenderSystem.enableDepthTest();
            else RenderSystem.disableDepthTest();
            if (cull) RenderSystem.enableCull();
        }
    }

    private static void drawInstances(ShaderInstance shader, int mode, int array, int count, Matrix4f modelView, Matrix4f projection, com.mojang.blaze3d.platform.Window window) {
        if (count <= 0) return;
        shader.safeGetUniform("Mode").set(mode);
        shader.setDefaultUniforms(VertexFormat.Mode.TRIANGLE_STRIP, modelView, projection, window);
        shader.apply();
        GlStateManager._glBindVertexArray(array);
        GL31.glDrawArraysInstanced(GL11.GL_TRIANGLE_STRIP, 0, 4, count);
    }

    // A pack's image is at display resolution and its depth at the render's: the shader compares against that depth itself. With no
    // shaders the depth test does it.
    private static boolean setUpDepth(ShaderInstance shader, Matrix4f projection) {
        boolean pack = KingCrimsonShatterPackRouter.current() != KingCrimsonShatterPackRoute.NO_SHADERS;
        shader.safeGetUniform("UseDepth").set(pack ? 1 : 0);
        if (!pack) {
            return false;
        }
        var target = Minecraft.getInstance().getMainRenderTarget();
        int[] extent = KingCrimsonShatterVoidDepth.renderExtent(target.width, target.height);
        // A draw fills every sampler from these slots, so setSampler would be overwritten.
        RenderSystem.setShaderTexture(0, target.getDepthTextureId());
        shader.safeGetUniform("DepthExtent").set((float) extent[0], (float) extent[1]);
        shader.safeGetUniform("ProjZ").set(projection.m22(), projection.m32());
        shader.safeGetUniform("ShellDepth").set(KingCrimsonShatterVoidDepth.movedDepth());
        return true;
    }

    private static void release() {
        if (active) {
            LOGGER.info("Terrain outlines of this erase: {}; buffer uploads {}, worst {} us", FIELD.summary(), uploads, worstUploadNanos / 1000L);
        }
        FIELD.reset();
        active = false;
        fieldLevel = null;
        segments = 0;
        faces = 0;
        uploads = 0;
        worstUploadNanos = 0L;
        framesSinceUpload = 0;
    }

    public static void close() {
        if (startedAt == Long.MIN_VALUE && edgeBuffer == 0 && edgeVertices == null && !failed) return;
        release();
        startedAt = Long.MIN_VALUE;
        failed = false;
        if (edgeBuffer == 0 && edgeVertices == null) return;
        RenderSystem.assertOnRenderThread();
        if (edgeBuffer != 0) {
            GL15.glDeleteBuffers(edgeBuffer);
            GL15.glDeleteBuffers(faceBuffer);
            GL30.glDeleteVertexArrays(edgeArray);
            GL30.glDeleteVertexArrays(faceArray);
            edgeBuffer = faceBuffer = edgeArray = faceArray = 0;
        }
        deleteFaceDepth();
        if (edgeVertices != null) {
            MemoryUtil.memFree(edgeVertices);
            MemoryUtil.memFree(faceVertices);
            edgeVertices = null;
            faceVertices = null;
        }
    }
}
