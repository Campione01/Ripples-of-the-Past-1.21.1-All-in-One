package rotp.addons.highwaystar.client;

import static org.lwjgl.opengl.GL30C.*;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;
import java.util.LinkedHashMap;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.entity.Entity;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import rotp.addons.highwaystar.room.HighwayStarRoomLure;

/** A viewer's perspective image of one doorway, captured after the world pass. */
final class HighwayStarRoomPreview implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int WIDTH = 256;
    private static final int HEIGHT = 512;
    private static final float OPENING_ASPECT = 0.75F / 1.8125F;

    private RenderTarget target;
    private RenderType doorway;
    private Entity actor;
    private boolean ready;
    private boolean failed;

    @Nullable
    RenderType opening() {
        return ready ? doorway : null;
    }

    void refresh(HighwayStarRoomLure lure, float partialTick) {
        if (failed) return;
        RenderSystem.assertOnRenderThread();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            CaptureState state = new CaptureState(stack);
            try {
                RenderSystem.activeTexture(GL_TEXTURE0);
                RenderSystem.disableScissor();
                RenderSystem.depthMask(true);
                RenderSystem.colorMask(true, true, true, true);
                if (target == null) initialize(lure);
                target.bindWrite(true);
                RenderSystem.clearColor(0.094F, 0.137F, 0.176F, 1.0F);
                GlStateManager._clearDepth(1.0D);
                RenderSystem.clear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL_LEQUAL);
                RenderSystem.setShaderColor(1, 1, 1, 1);
                RenderSystem.setShaderFogStart(64);
                RenderSystem.setShaderFogEnd(128);
                RenderSystem.getModelViewStack().identity();
                RenderSystem.applyModelViewMatrix();
                RenderSystem.setProjectionMatrix(new Matrix4f().perspective((float) Math.toRadians(52),
                        OPENING_ASPECT, 0.05F, 32.0F), VertexSorting.DISTANCE_TO_ORIGIN);
                drawRoom(lure, partialTick);
                ready = true;
            } finally {
                state.restore();
            }
        } catch (RuntimeException | LinkageError failure) {
            failed = true;
            try {
                close();
            } catch (RuntimeException | LinkageError cleanup) {
                failure.addSuppressed(cleanup);
            }
            LOGGER.warn("Could not render a Highway Star doorway preview", failure);
        }
    }

    private void initialize(HighwayStarRoomLure lure) {
        RenderTarget created = new RenderTarget(true) {};
        target = created;
        created.resize(WIDTH, HEIGHT, Minecraft.ON_OSX);
        created.setFilterMode(GL_LINEAR);
        actor = HighwayStarRoomScene.preview(lure);
        doorway = RenderType.create("highway_star_room_preview", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 1536, RenderType.CompositeState.builder()
                        .setShaderState(RenderStateShard.RENDERTYPE_ENTITY_CUTOUT_NO_CULL_SHADER)
                        .setTextureState(new RenderStateShard.EmptyTextureStateShard(
                                () -> RenderSystem.setShaderTexture(0, created.getColorTextureId()), () -> {}))
                        .setCullState(RenderStateShard.NO_CULL)
                        .setLightmapState(RenderStateShard.LIGHTMAP)
                        .setOverlayState(RenderStateShard.OVERLAY)
                        .createCompositeState(false));
    }

    private void drawRoom(HighwayStarRoomLure lure, float partialTick) {
        try (ByteBufferBuilder shared = new ByteBufferBuilder(8192);
                ByteBufferBuilder foil = new ByteBufferBuilder(1536)) {
            // The chest's base consumer and foil consumer must remain writable at the same time.
            var fixed = new LinkedHashMap<RenderType, ByteBufferBuilder>();
            fixed.put(RenderType.entityGlintDirect(), foil);
            MultiBufferSource.BufferSource buffers = MultiBufferSource.immediateWithBuffers(fixed, shared);
            PoseStack pose = new PoseStack();
            pose.mulPose(Axis.XP.rotationDegrees(12));
            pose.translate(0, -0.15, -2.45);
            try {
                HighwayStarRoomScene.enclosure(pose, buffers, 0);
                HighwayStarRoomScene.furniture(pose, buffers, lure, actor, partialTick);
            } finally {
                buffers.endBatch();
            }
        }
    }

    @Override
    public void close() {
        RenderTarget owned = target;
        target = null;
        doorway = null;
        actor = null;
        ready = false;
        if (owned == null) return;
        if (RenderSystem.isOnRenderThread()) destroy(owned);
        else RenderSystem.recordRenderCall(() -> destroy(owned));
    }

    private static void destroy(RenderTarget target) {
        int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        int draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int texture = glGetInteger(GL_TEXTURE_BINDING_2D);
        int frame = target.frameBufferId;
        int color = target.getColorTextureId(), depth = target.getDepthTextureId();
        try {
            target.destroyBuffers();
        } finally {
            bindFramebuffers(read == frame ? 0 : read, draw == frame ? 0 : draw);
            GlStateManager._bindTexture(texture == color || texture == depth ? 0 : texture);
        }
    }

    private static void bindFramebuffers(int read, int draw) {
        GlStateManager._glBindFramebuffer(GL_READ_FRAMEBUFFER, read);
        GlStateManager._glBindFramebuffer(GL_DRAW_FRAMEBUFFER, draw);
    }

    private static final class CaptureState {
        private final int read = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        private final int draw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        private final int program = glGetInteger(GL_CURRENT_PROGRAM);
        private final int activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        private final int depthFunc = glGetInteger(GL_DEPTH_FUNC);
        private final int srcRgb = glGetInteger(GL_BLEND_SRC_RGB), dstRgb = glGetInteger(GL_BLEND_DST_RGB);
        private final int srcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA), dstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        private final boolean depth = glIsEnabled(GL_DEPTH_TEST), blend = glIsEnabled(GL_BLEND);
        private final boolean cull = glIsEnabled(GL_CULL_FACE), scissor = glIsEnabled(GL_SCISSOR_TEST);
        private final boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        private final double clearDepth = glGetDouble(GL_DEPTH_CLEAR_VALUE);
        private final Matrix4f projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        private final Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewStack());
        private final Matrix4f textureMatrix = new Matrix4f(RenderSystem.getTextureMatrix());
        private final VertexSorting sorting = RenderSystem.getVertexSorting();
        private final ShaderInstance shader = RenderSystem.getShader();
        private final float fogStart = RenderSystem.getShaderFogStart(), fogEnd = RenderSystem.getShaderFogEnd();
        private final float[] shaderColor = RenderSystem.getShaderColor().clone();
        private final int[] textures = new int[3], shaderTextures = new int[3];
        private final IntBuffer viewport;
        private final FloatBuffer clearColor;
        private final ByteBuffer colorMask;

        CaptureState(MemoryStack stack) {
            viewport = stack.mallocInt(4);
            clearColor = stack.mallocFloat(4);
            colorMask = stack.malloc(4);
            glGetIntegerv(GL_VIEWPORT, viewport);
            glGetFloatv(GL_COLOR_CLEAR_VALUE, clearColor);
            glGetBooleanv(GL_COLOR_WRITEMASK, colorMask);
            try {
                for (int i = 0; i < textures.length; i++) {
                    RenderSystem.activeTexture(GL_TEXTURE0 + i);
                    textures[i] = glGetInteger(GL_TEXTURE_BINDING_2D);
                    shaderTextures[i] = RenderSystem.getShaderTexture(i);
                }
            } finally {
                RenderSystem.activeTexture(activeTexture);
            }
        }

        void restore() {
            bindFramebuffers(read, draw);
            RenderSystem.viewport(viewport.get(0), viewport.get(1), viewport.get(2), viewport.get(3));
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.getModelViewStack().set(modelView);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setTextureMatrix(textureMatrix);
            RenderSystem.setShader(() -> shader);
            GlStateManager._glUseProgram(program);
            for (int i = 0; i < textures.length; i++) {
                RenderSystem.activeTexture(GL_TEXTURE0 + i);
                GlStateManager._bindTexture(textures[i]);
                RenderSystem.setShaderTexture(i, shaderTextures[i]);
            }
            RenderSystem.activeTexture(activeTexture);
            RenderSystem.setShaderColor(shaderColor[0], shaderColor[1], shaderColor[2], shaderColor[3]);
            RenderSystem.setShaderFogStart(fogStart);
            RenderSystem.setShaderFogEnd(fogEnd);
            RenderSystem.clearColor(clearColor.get(0), clearColor.get(1), clearColor.get(2), clearColor.get(3));
            GlStateManager._clearDepth(clearDepth);
            RenderSystem.colorMask(colorMask.get(0) != 0, colorMask.get(1) != 0, colorMask.get(2) != 0, colorMask.get(3) != 0);
            RenderSystem.depthMask(depthMask);
            RenderSystem.depthFunc(depthFunc);
            RenderSystem.blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (depth) RenderSystem.enableDepthTest(); else RenderSystem.disableDepthTest();
            if (blend) RenderSystem.enableBlend(); else RenderSystem.disableBlend();
            if (cull) RenderSystem.enableCull(); else RenderSystem.disableCull();
            if (scissor) GlStateManager._enableScissorTest(); else RenderSystem.disableScissor();
        }
    }
}
