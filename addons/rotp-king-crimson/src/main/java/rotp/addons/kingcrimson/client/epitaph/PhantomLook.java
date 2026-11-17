package rotp.addons.kingcrimson.client.epitaph;

import javax.annotation.Nullable;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * How a phantom looks. An entity is drawn by its own renderer through this: at full brightness, each texel pulled
 * most of the way to white and then tinted, a little short of opaque, and once more slightly larger and added on top
 * in the glow colour, for the rim. One class and one set of numbers, so that another look is another preset. The
 * numbers of the foreseen future are a first choice, to be set by looking.
 */
@OnlyIn(Dist.CLIENT)
public final class PhantomLook {
    /** Epitaph: a bright pink-white copy with a magenta rim, unlike the dark crimson afterimage at a glance. */
    public static final PhantomLook EPITAPH_FUTURE = new PhantomLook(1.0F, 0.80F, 0.93F, 0.88F, 0.8F, 1.0F, 0.22F, 0.75F, 0.5F, 1.06F);

    private final float red;
    private final float green;
    private final float blue;
    private final float alpha;
    private final int whiteOverlayU;
    private final float glowRed;
    private final float glowGreen;
    private final float glowBlue;
    private final float glowScale;

    private PhantomLook(float red, float green, float blue, float alpha, float whiten, float glowRed, float glowGreen,
            float glowBlue, float glowStrength, float glowScale) {
        this.red = red;
        this.green = green;
        this.blue = blue;
        this.alpha = alpha;
        // The overlay the game flashes a creeper white with: the further along its row, the whiter.
        this.whiteOverlayU = OverlayTexture.u(whiten);
        this.glowRed = glowRed * glowStrength;
        this.glowGreen = glowGreen * glowStrength;
        this.glowBlue = glowBlue * glowStrength;
        this.glowScale = glowScale;
    }

    /** Draws the entity at the origin of the pose, as its renderer draws it, in this look. */
    public void draw(EntityRenderer<? super Entity> renderer, Entity entity, float yaw, float partialTick, PoseStack pose,
            MultiBufferSource buffer) {
        ResourceLocation texture = textureOf(renderer, entity);
        RenderType translucent = texture != null ? RenderType.entityTranslucent(texture) : null;
        RenderType[] opaque = texture != null ? opaqueTypes(texture) : new RenderType[0];
        renderer.render(entity, yaw, partialTick, pose, type -> new Tinted(
                buffer.getBuffer(translucent != null && isOneOf(type, opaque) ? translucent : type), red, green, blue, alpha,
                whiteOverlayU, OverlayTexture.v(false)), LightTexture.FULL_BRIGHT);
        if (texture == null) {
            return;
        }
        // The rim: the body alone again, a little larger about its middle, added on in the glow colour.
        RenderType glow = RenderType.eyes(texture);
        float middle = entity.getBbHeight() * 0.5F;
        pose.pushPose();
        try {
            pose.translate(0.0F, middle, 0.0F);
            pose.scale(glowScale, glowScale, glowScale);
            pose.translate(0.0F, -middle, 0.0F);
            renderer.render(entity, yaw, partialTick, pose, type -> type == translucent || isOneOf(type, opaque)
                    ? new Tinted(buffer.getBuffer(glow), glowRed, glowGreen, glowBlue, 1.0F, 0, OverlayTexture.v(false))
                    : NoVertices.INSTANCE, LightTexture.FULL_BRIGHT);
        }
        finally {
            pose.popPose();
        }
    }

    /** A particle made by a phantom takes the colour of the look. */
    public void tint(Particle particle) {
        particle.setColor(red, green, blue);
    }

    @Nullable
    private static ResourceLocation textureOf(EntityRenderer<? super Entity> renderer, Entity entity) {
        try {
            return renderer.getTextureLocation(entity);
        }
        catch (RuntimeException noTexture) {
            return null;
        }
    }

    // The game keeps one type per kind and texture, so a type is known by identity.
    private static RenderType[] opaqueTypes(ResourceLocation texture) {
        return new RenderType[] { RenderType.entitySolid(texture), RenderType.entityCutout(texture),
                RenderType.entityCutoutNoCull(texture), RenderType.entityCutoutNoCull(texture, false),
                RenderType.entityCutoutNoCullZOffset(texture), RenderType.entityCutoutNoCullZOffset(texture, false),
                RenderType.entitySmoothCutout(texture) };
    }

    private static boolean isOneOf(RenderType type, RenderType[] types) {
        for (RenderType candidate : types) {
            if (type == candidate) {
                return true;
            }
        }
        return false;
    }

    // Every vertex with its colour multiplied, its overlay the white one and its light full.
    private static final class Tinted implements VertexConsumer {
        private final VertexConsumer delegate;
        private final float red;
        private final float green;
        private final float blue;
        private final float alpha;
        private final int overlayU;
        private final int overlayV;

        private Tinted(VertexConsumer delegate, float red, float green, float blue, float alpha, int overlayU, int overlayV) {
            this.delegate = delegate;
            this.red = red;
            this.green = green;
            this.blue = blue;
            this.alpha = alpha;
            this.overlayU = overlayU;
            this.overlayV = overlayV;
        }

        private int tinted(int color) {
            return FastColor.ARGB32.color((int) (FastColor.ARGB32.alpha(color) * alpha), (int) (FastColor.ARGB32.red(color) * red),
                    (int) (FastColor.ARGB32.green(color) * green), (int) (FastColor.ARGB32.blue(color) * blue));
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            delegate.addVertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setColor(int r, int g, int b, int a) {
            delegate.setColor((int) (r * red), (int) (g * green), (int) (b * blue), (int) (a * alpha));
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            delegate.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            delegate.setUv1(overlayU, overlayV);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            delegate.setUv2(LightTexture.FULL_BRIGHT & 0xFFFF, LightTexture.FULL_BRIGHT >> 16 & 0xFFFF);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            delegate.setNormal(normalX, normalY, normalZ);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int packedOverlay, int packedLight,
                float normalX, float normalY, float normalZ) {
            delegate.addVertex(x, y, z, tinted(color), u, v, OverlayTexture.pack(overlayU, overlayV), LightTexture.FULL_BRIGHT,
                    normalX, normalY, normalZ);
        }
    }

    // For the layers that are no part of the rim.
    private static final class NoVertices implements VertexConsumer {
        private static final NoVertices INSTANCE = new NoVertices();

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
        public VertexConsumer setNormal(float normalX, float normalY, float normalZ) {
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int packedOverlay, int packedLight,
                float normalX, float normalY, float normalZ) {
        }
    }
}
