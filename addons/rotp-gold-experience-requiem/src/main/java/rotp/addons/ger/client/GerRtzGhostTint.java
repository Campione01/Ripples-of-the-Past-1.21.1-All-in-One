package rotp.addons.ger.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormatElement;

import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.util.FastColor;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/** Draws whatever goes through it in the rewind's gold: every vertex colour is multiplied by it. */
@OnlyIn(Dist.CLIENT)
final class GerRtzGhostTint implements MultiBufferSource {
    private final MultiBufferSource delegate;
    private final float red;
    private final float green;
    private final float blue;

    GerRtzGhostTint(
            MultiBufferSource delegate,
            float red,
            float green,
            float blue) {
        this.delegate = delegate;
        this.red = red;
        this.green = green;
        this.blue = blue;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        return new Tinted(delegate.getBuffer(renderType));
    }

    private int tint(int argb) {
        return FastColor.ARGB32.color(
                FastColor.ARGB32.alpha(argb),
                Math.round(FastColor.ARGB32.red(argb) * red),
                Math.round(FastColor.ARGB32.green(argb) * green),
                Math.round(FastColor.ARGB32.blue(argb) * blue));
    }

    private final class Tinted implements VertexConsumer {
        private final VertexConsumer vertices;

        private Tinted(VertexConsumer vertices) {
            this.vertices = vertices;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            vertices.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(
                int r, int g, int b, int alpha) {
            vertices.setColor(
                    Math.round(r * red),
                    Math.round(g * green),
                    Math.round(b * blue),
                    alpha);
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            vertices.setColor(tint(argb));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            vertices.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            vertices.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            vertices.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            vertices.setNormal(x, y, z);
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
            vertices.addVertex(
                    x, y, z, tint(color), u, v,
                    packedOverlay, packedLight,
                    normalX, normalY, normalZ);
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose,
                BakedQuad quad,
                float r,
                float g,
                float b,
                float alpha,
                int packedLight,
                int packedOverlay) {
            vertices.putBulkData(
                    pose, quad, r * red, g * green, b * blue, alpha,
                    packedLight, packedOverlay);
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose,
                BakedQuad quad,
                float[] brightness,
                float r,
                float g,
                float b,
                float alpha,
                int[] lightmap,
                int packedOverlay,
                boolean readAlpha) {
            vertices.putBulkData(
                    pose, quad, brightness,
                    r * red, g * green, b * blue, alpha,
                    lightmap, packedOverlay, readAlpha);
        }

        @Override
        public void putBulkData(
                PoseStack.Pose pose,
                BakedQuad quad,
                float r,
                float g,
                float b,
                float alpha,
                int packedLight,
                int packedOverlay,
                boolean readExistingColor) {
            vertices.putBulkData(
                    pose, quad, r * red, g * green, b * blue, alpha,
                    packedLight, packedOverlay, readExistingColor);
        }

        @Override
        public VertexConsumer misc(
                VertexFormatElement element, int... rawData) {
            vertices.misc(element, rawData);
            return this;
        }
    }
}
