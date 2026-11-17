package rotp.addons.kingcrimson.client;

import java.nio.ByteBuffer;

import org.joml.Matrix3f;
import org.joml.Vector3f;

import rotp.core.client.entityrender.stand.StandEntityRenderer;
import rotp.core.powersystem.standpower.entity.StandEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import rotp.addons.kingcrimson.entity.KingCrimsonAfterimageEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public final class KingCrimsonAfterimageRenderer
        extends EntityRenderer<KingCrimsonAfterimageEntity> {
    private static final ThreadLocal<Integer> RENDER_DEPTH =
            new ThreadLocal<>();

    public KingCrimsonAfterimageRenderer(
            EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(
            KingCrimsonAfterimageEntity entity) {
        return null;
    }

    @Override
    public void render(
            KingCrimsonAfterimageEntity afterimage,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        Minecraft minecraft = Minecraft.getInstance();
        Entity origin = afterimage.getOriginEntity();
        if (origin == null
                || minecraft.player != null
                        && afterimage.isInvisibleTo(minecraft.player)
                || origin == minecraft.getCameraEntity()
                        && minecraft.options.getCameraType()
                                .isFirstPerson()) {
            return;
        }

        if (afterimage.isMovingThing()
                && afterimage.getPosition(partialTick).distanceToSqr(origin.getPosition(partialTick)) < 1.0E-4D) {
            return;
        }

        Integer currentDepth = RENDER_DEPTH.get();
        RENDER_DEPTH.set(currentDepth == null ? 1 : currentDepth + 1);
        try {
            EntityRenderer<? super Entity> renderer =
                    entityRenderDispatcher.getRenderer(origin);
            // Every afterimage is drawn in the donor's dark red; the full-color, outlined "present" afterimage
            // went with the Time Erase rewind it marked (owner decision 2026-09-23).
            MultiBufferSource renderBuffer = type -> new RedVertexConsumer(buffer.getBuffer(type));
            // A shifted player's image is drawn where his entity is drawn this frame, not a tick behind it.
            Vec3 lead = afterimage.followsOrigin()
                    ? origin.getPosition(partialTick).subtract(afterimage.getPosition(partialTick))
                    : Vec3.ZERO;
            poseStack.pushPose();
            poseStack.translate(lead.x, lead.y, lead.z);
            if (origin instanceof StandEntity stand
                    && renderer
                            instanceof StandEntityRenderer<?, ?, ?>
                                    standRenderer) {
                renderStandAfterimage(
                        standRenderer,
                        stand,
                        yRotation,
                        partialTick,
                        poseStack,
                        renderBuffer,
                        packedLight);
            } else {
                renderer.render(
                        origin,
                        yRotation,
                        partialTick,
                        poseStack,
                        renderBuffer,
                        packedLight);
            }
            super.render(
                    afterimage,
                    yRotation,
                    partialTick,
                    poseStack,
                    buffer,
                    packedLight);
            poseStack.popPose();
        }
        finally {
            int depth = RENDER_DEPTH.get() - 1;
            if (depth <= 0) {
                RENDER_DEPTH.remove();
            } else {
                RENDER_DEPTH.set(depth);
            }
        }
    }

    static boolean isRenderingAfterimage() {
        Integer depth = RENDER_DEPTH.get();
        return depth != null && depth > 0;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void renderStandAfterimage(
            StandEntityRenderer renderer,
            StandEntity stand,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        renderer.renderAfterimage(
                stand,
                yRotation,
                partialTick,
                poseStack,
                buffer,
                packedLight);
    }

    private static final class RedVertexConsumer
            implements VertexConsumer {
        private final VertexConsumer delegate;

        private RedVertexConsumer(VertexConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public VertexConsumer addVertex(
                float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(
                int red, int green, int blue, int alpha) {
            delegate.setColor(red / 2, 0, 0, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(
                float red, float green, float blue, float alpha) {
            delegate.setColor(red * 0.5F, 0.0F, 0.0F, alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int color) {
            delegate.setColor(redOnly(color));
            return this;
        }

        @Override
        public VertexConsumer setWhiteAlpha(int alpha) {
            delegate.setColor(128, 0, 0, alpha);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setLight(int packedLight) {
            delegate.setLight(packedLight);
            return this;
        }

        @Override
        public VertexConsumer setOverlay(int packedOverlay) {
            delegate.setOverlay(packedOverlay);
            return this;
        }

        @Override
        public VertexConsumer setNormal(
                float normalX, float normalY, float normalZ) {
            delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }

        @Override
        public void addVertex(
                float x,
                float y,
                float z,
                int color,
                float u,
                float v,
                int packedOverlay,
                int packedLight,
                float normalX,
                float normalY,
                float normalZ) {
            delegate.addVertex(
                    x,
                    y,
                    z,
                    redOnly(color),
                    u,
                    v,
                    packedOverlay,
                    packedLight,
                    normalX,
                    normalY,
                    normalZ);
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose,
                BakedQuad quad,
                float red,
                float green,
                float blue,
                float alpha,
                int packedLight,
                int packedOverlay) {
            delegate.putBulkData(
                    pose,
                    quad,
                    red * 0.5F,
                    0.0F,
                    0.0F,
                    alpha,
                    packedLight,
                    packedOverlay);
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose,
                BakedQuad quad,
                float[] brightness,
                float red,
                float green,
                float blue,
                float alpha,
                int[] lightmap,
                int packedOverlay,
                boolean readAlpha) {
            delegate.putBulkData(
                    pose,
                    quad,
                    brightness,
                    red * 0.5F,
                    0.0F,
                    0.0F,
                    alpha,
                    lightmap,
                    packedOverlay,
                    readAlpha);
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose,
                BakedQuad quad,
                float red,
                float green,
                float blue,
                float alpha,
                int packedLight,
                int packedOverlay,
                boolean readExistingColor) {
            delegate.putBulkData(
                    pose,
                    quad,
                    red * 0.5F,
                    0.0F,
                    0.0F,
                    alpha,
                    packedLight,
                    packedOverlay,
                    readExistingColor);
        }

        @Override
        public VertexConsumer misc(
                VertexFormatElement element, int... rawData) {
            delegate.misc(element, rawData);
            return this;
        }

        @Override
        public int applyBakedLighting(
                int packedLight, ByteBuffer data) {
            return delegate.applyBakedLighting(packedLight, data);
        }

        @Override
        public void applyBakedNormals(
                Vector3f generated,
                ByteBuffer data,
                Matrix3f normalTransform) {
            delegate.applyBakedNormals(
                    generated, data, normalTransform);
        }

        private static int redOnly(int color) {
            int alpha = color & 0xFF000000;
            int red = color >>> 16 & 0xFF;
            return alpha | red / 2 << 16;
        }
    }
}
