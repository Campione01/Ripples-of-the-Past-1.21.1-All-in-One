package rotp.addons.kingcrimson.client;

import java.io.IOException;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.mojang.logging.LogUtils;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.entity.KingCrimsonAfterimageEntity;

/**
 * The crimson halo of Time Erase's afterimages under shader packs. The vanilla outline goes through the pack's world and cannot be
 * trusted to come out red there, so these afterimages alone are drawn again after the pack's final image: their silhouettes, with
 * no depth test as the outline had, blurred and laid over in crimson. Other glowing entities keep the vanilla path. Only the erasing
 * user's client holds these entities, so only that user sees it. With no shaders the same halo (owner, 2026-11-11); the vanilla red outline
 * comes back only if the halo cannot draw.
 */
@OnlyIn(Dist.CLIENT)
public final class KingCrimsonAfterimageHalo {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SILHOUETTE_BYTES = 256 * 1024;
    // One step of the blur at 1080p, in the silhouette's half-size pixels.
    private static final float BLUR_STEP_AT_1080 = 3.0F;

    private static ShaderInstance program;
    private static RenderTarget silhouette;
    private static RenderTarget blurredOneWay;
    private static RenderTarget blurred;
    private static ByteBufferBuilder storage;
    private static boolean failed;

    private KingCrimsonAfterimageHalo() {}

    public static void onRegisterShaders(RegisterShadersEvent event) {
        program = null;
        try {
            event.registerShader(
                    new ShaderInstance(event.getResourceProvider(), KingCrimsonAddon.id("time_erase_halo"), DefaultVertexFormat.POSITION),
                    loaded -> program = loaded);
        } catch (IOException | RuntimeException failure) {
            LOGGER.error("Unable to load the afterimage halo; afterimages keep the vanilla outline.", failure);
        }
    }

    public static void afterLevel(RenderLevelStageEvent event) {
        if (!KingCrimsonTemporalClientState.localTimeErase()) {
            close();
            return;
        }
        // With no shaders too (owner, 2026-11-11: as under shader packs): the red outline is only the fallback now.
        boolean applies = program != null && !failed;
        KingCrimsonAfterimageEntity.setHaloReplacesOutline(applies);
        if (!applies) return;
        Minecraft minecraft = Minecraft.getInstance();
        List<KingCrimsonAfterimageEntity> afterimages = outlined(minecraft.level);
        if (afterimages.isEmpty()) return;
        try {
            draw(minecraft, event, afterimages);
        } catch (RuntimeException | LinkageError failure) {
            failed = true;
            KingCrimsonAfterimageEntity.setHaloReplacesOutline(false);
            LOGGER.warn("The afterimage halo could not draw; afterimages go back to the vanilla outline.", failure);
        }
    }

    private static List<KingCrimsonAfterimageEntity> outlined(ClientLevel level) {
        List<KingCrimsonAfterimageEntity> found = new ArrayList<>();
        if (level == null) return found;
        for (Entity entity : level.entitiesForRendering()) {
            if (entity instanceof KingCrimsonAfterimageEntity afterimage && afterimage.showsTimeEraseOutline()) {
                found.add(afterimage);
            }
        }
        return found;
    }

    private static void draw(Minecraft minecraft, RenderLevelStageEvent event, List<KingCrimsonAfterimageEntity> afterimages) {
        int drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int viewportX, viewportY, viewportWidth, viewportHeight;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var viewport = stack.mallocInt(4);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            viewportX = viewport.get(0);
            viewportY = viewport.get(1);
            viewportWidth = viewport.get(2);
            viewportHeight = viewport.get(3);
        }
        int width = Math.max(1, viewportWidth / 2);
        int height = Math.max(1, viewportHeight / 2);
        ensureTargets(width, height);
        int previousTexture0 = RenderSystem.getShaderTexture(0);
        int previousTexture1 = RenderSystem.getShaderTexture(1);
        try {
            drawSilhouettes(minecraft, event, afterimages);
            float step = BLUR_STEP_AT_1080 * viewportHeight / 1080.0F;
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();
            // Added to a cleared target this is a plain write.
            RenderSystem.enableBlend();
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
            pass(blurredOneWay, silhouette.getColorTextureId(), step / width, 0.0F);
            pass(blurred, blurredOneWay.getColorTextureId(), 0.0F, step / height);
            // Wide steps leave a pattern of their own spacing on a small body: a narrow blur closes it.
            pass(blurredOneWay, blurred.getColorTextureId(), 1.0F / width, 0.0F);
            pass(blurred, blurredOneWay.getColorTextureId(), 0.0F, 1.0F / height);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._viewport(viewportX, viewportY, viewportWidth, viewportHeight);
            fullscreen(blurred.getColorTextureId(), silhouette.getColorTextureId(), 1, 0.0F, 0.0F);
        } finally {
            RenderSystem.setShaderTexture(0, previousTexture0);
            RenderSystem.setShaderTexture(1, previousTexture1);
            GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GlStateManager._viewport(viewportX, viewportY, viewportWidth, viewportHeight);
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.enableDepthTest();
            RenderSystem.enableCull();
        }
    }

    private static void ensureTargets(int width, int height) {
        if (silhouette == null || silhouette.width != width || silhouette.height != height) {
            destroyTargets();
            silhouette = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            blurredOneWay = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            blurred = new TextureTarget(width, height, false, Minecraft.ON_OSX);
            for (RenderTarget target : new RenderTarget[] {silhouette, blurredOneWay, blurred}) {
                target.setClearColor(0.0F, 0.0F, 0.0F, 0.0F);
                target.setFilterMode(GL11.GL_LINEAR);
            }
        }
    }

    private static void destroyTargets() {
        if (silhouette != null) silhouette.destroyBuffers();
        if (blurredOneWay != null) blurredOneWay.destroyBuffers();
        if (blurred != null) blurred.destroyBuffers();
        silhouette = blurredOneWay = blurred = null;
    }

    private static void drawSilhouettes(Minecraft minecraft, RenderLevelStageEvent event, List<KingCrimsonAfterimageEntity> afterimages) {
        silhouette.clear(Minecraft.ON_OSX);
        if (storage == null) storage = new ByteBufferBuilder(SILHOUETTE_BYTES);
        MultiBufferSource.BufferSource buffers = MultiBufferSource.immediate(storage);
        // Vanilla keeps one outline type per texture; so does this frame.
        Map<RenderType, RenderType> haloTypes = new IdentityHashMap<>();
        MultiBufferSource source = type -> {
            Optional<RenderType> outline = type.outline();
            return outline.isPresent()
                    ? new Silhouette(buffers.getBuffer(haloTypes.computeIfAbsent(outline.get(), HaloType::new)))
                    : NO_VERTICES;
        };
        Camera camera = event.getCamera();
        Vec3 eye = camera.getPosition();
        float partial = event.getPartialTick().getGameTimeDeltaPartialTick(false);
        Matrix4f previousProjection = new Matrix4f(RenderSystem.getProjectionMatrix());
        VertexSorting previousSorting = RenderSystem.getVertexSorting();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity().mul(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(event.getProjectionMatrix()), VertexSorting.DISTANCE_TO_ORIGIN);
        try {
            PoseStack pose = new PoseStack();
            for (KingCrimsonAfterimageEntity afterimage : afterimages) {
                EntityRenderer<? super KingCrimsonAfterimageEntity> renderer = minecraft.getEntityRenderDispatcher().getRenderer(afterimage);
                double x = Mth.lerp(partial, afterimage.xOld, afterimage.getX()) - eye.x;
                double y = Mth.lerp(partial, afterimage.yOld, afterimage.getY()) - eye.y;
                double z = Mth.lerp(partial, afterimage.zOld, afterimage.getZ()) - eye.z;
                pose.pushPose();
                pose.translate(x, y, z);
                renderer.render(afterimage, Mth.lerp(partial, afterimage.yRotO, afterimage.getYRot()), partial, pose, source, LightTexture.FULL_BRIGHT);
                pose.popPose();
            }
            buffers.endBatch();
        } finally {
            modelView.popMatrix();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(previousProjection, previousSorting);
        }
    }

    private static void pass(RenderTarget into, int texture, float stepX, float stepY) {
        into.clear(Minecraft.ON_OSX);
        into.bindWrite(true);
        fullscreen(texture, 0, 0, stepX, stepY);
    }

    private static void fullscreen(int first, int second, int mode, float stepX, float stepY) {
        ShaderInstance shader = program;
        // A draw fills every sampler from these slots, so setSampler would be overwritten.
        RenderSystem.setShaderTexture(0, first);
        RenderSystem.setShaderTexture(1, second);
        shader.safeGetUniform("Step").set(stepX, stepY);
        shader.safeGetUniform("Mode").set(mode);
        RenderSystem.setShader(() -> shader);
        BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.TRIANGLES, DefaultVertexFormat.POSITION);
        builder.addVertex(-1.0F, -1.0F, 0.0F);
        builder.addVertex(3.0F, -1.0F, 0.0F);
        builder.addVertex(-1.0F, 3.0F, 0.0F);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    public static void close() {
        KingCrimsonAfterimageEntity.setHaloReplacesOutline(false);
        failed = false;
        if (silhouette == null && storage == null) return;
        RenderSystem.assertOnRenderThread();
        destroyTargets();
        if (storage != null) {
            storage.close();
            storage = null;
        }
    }

    /** The vanilla outline type with its target swapped for ours. */
    private static final class HaloType extends RenderType {
        private HaloType(RenderType outline) {
            super(
                    "rotp_kingcrimson:afterimage_halo",
                    outline.format(),
                    outline.mode(),
                    outline.bufferSize(),
                    false,
                    false,
                    () -> {
                        outline.setupRenderState();
                        silhouette.bindWrite(true);
                    },
                    outline::clearRenderState);
        }
    }

    /** Position and texture only, as the vanilla outline takes them: the colour is the composite's. */
    private record Silhouette(VertexConsumer delegate) implements VertexConsumer {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z).setColor(255, 255, 255, 255);
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    }

    private static final VertexConsumer NO_VERTICES = new VertexConsumer() {
        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            return this;
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            return this;
        }
    };
}
