package rotp.addons.jumpingjackflash.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;

import rotp.addons.jumpingjackflash.JumpingJackFlashAddon;
import rotp.addons.jumpingjackflash.entity.GravityDomainEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

// 1.16 DomainClientState/DomainRenderer: two white solid spheres drawn
// blended, without culling and without depth writes, so the dome also
// shows from inside, where the domain's owner always stands.
public final class GravityDomainRenderer
        extends EntityRenderer<GravityDomainEntity> {
    private static final int LATITUDE_SEGMENTS = 48;
    private static final int LONGITUDE_SEGMENTS = 72;
    private static final ResourceLocation TEXTURE =
            JumpingJackFlashAddon.id(
                    "textures/entity/stand/jumping_jack_flash.png");

    public GravityDomainRenderer(
            EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0.0F;
    }

    @Override
    public ResourceLocation getTextureLocation(
            GravityDomainEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(
            GravityDomainEntity entity,
            float yRotation,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight) {
        float radius = entity.currentRadius(partialTick);
        float alphaFactor = entity.currentAlphaFactor(partialTick);
        if (radius <= 0.001F || alphaFactor <= 0.001F) {
            return;
        }
        VertexConsumer vertices =
                buffer.getBuffer(DomainShell.DOMAIN_SHELL);
        drawSphere(
                poseStack.last(),
                vertices,
                radius - 0.1F,
                alpha(180.0F * alphaFactor));
        drawSphere(
                poseStack.last(),
                vertices,
                radius,
                alpha(220.0F * alphaFactor * 0.3F));
        super.render(
                entity,
                yRotation,
                partialTick,
                poseStack,
                buffer,
                packedLight);
    }

    private static int alpha(float value) {
        return Mth.clamp(Math.round(value), 0, 255);
    }

    private static void drawSphere(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            float radius,
            int alpha) {
        if (radius <= 0.001F || alpha <= 0) {
            return;
        }
        for (int latitude = 0;
                latitude < LATITUDE_SEGMENTS;
                latitude++) {
            float v0 = latitude / (float) LATITUDE_SEGMENTS;
            float v1 = (latitude + 1) / (float) LATITUDE_SEGMENTS;
            float phi0 = (v0 - 0.5F) * Mth.PI;
            float phi1 = (v1 - 0.5F) * Mth.PI;
            float y0 = Mth.sin(phi0) * radius;
            float y1 = Mth.sin(phi1) * radius;
            float ring0 = Mth.cos(phi0) * radius;
            float ring1 = Mth.cos(phi1) * radius;
            for (int longitude = 0;
                    longitude < LONGITUDE_SEGMENTS;
                    longitude++) {
                float angle0 = Mth.TWO_PI * longitude
                        / LONGITUDE_SEGMENTS;
                float angle1 = Mth.TWO_PI * (longitude + 1)
                        / LONGITUDE_SEGMENTS;
                vertex(pose, vertices,
                        Mth.cos(angle0) * ring0, y0,
                        Mth.sin(angle0) * ring0, alpha);
                vertex(pose, vertices,
                        Mth.cos(angle0) * ring1, y1,
                        Mth.sin(angle0) * ring1, alpha);
                vertex(pose, vertices,
                        Mth.cos(angle1) * ring1, y1,
                        Mth.sin(angle1) * ring1, alpha);
                vertex(pose, vertices,
                        Mth.cos(angle1) * ring0, y0,
                        Mth.sin(angle1) * ring0, alpha);
            }
        }
    }

    private static void vertex(
            PoseStack.Pose pose,
            VertexConsumer vertices,
            float x,
            float y,
            float z,
            int alpha) {
        vertices.addVertex(pose.pose(), x, y, z)
                .setColor(255, 255, 255, alpha);
    }

    private static final class DomainShell extends RenderType {
        static final RenderType DOMAIN_SHELL = create(
                JumpingJackFlashAddon.MOD_ID + ":gravity_domain_shell",
                DefaultVertexFormat.POSITION_COLOR,
                VertexFormat.Mode.QUADS,
                1536,
                false,
                true,
                CompositeState.builder()
                        .setShaderState(POSITION_COLOR_SHADER)
                        .setTransparencyState(TRANSLUCENT_TRANSPARENCY)
                        .setCullState(NO_CULL)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));

        private DomainShell(
                String name,
                VertexFormat format,
                VertexFormat.Mode mode,
                int bufferSize,
                boolean affectsCrumbling,
                boolean sortOnUpload,
                Runnable setupState,
                Runnable clearState) {
            super(name, format, mode, bufferSize, affectsCrumbling,
                    sortOnUpload, setupState, clearState);
        }
    }
}
