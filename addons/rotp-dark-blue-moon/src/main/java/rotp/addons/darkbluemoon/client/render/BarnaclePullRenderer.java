package rotp.addons.darkbluemoon.client.render;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.addons.darkbluemoon.client.DarkBlueMoonModels;
import rotp.addons.darkbluemoon.entity.BarnaclePullEntity;
import rotp.core.util.functions.MathUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 BarnaclePullRenderer (ExtendingEntityRenderer with BarnaclePullModel): the 4-long barnacle segment repeated from
 * the line's tip back to the Stand.
 */
public class BarnaclePullRenderer extends EntityRenderer<BarnaclePullEntity> {
    private static final ResourceLocation TEXTURE = DarkBlueMoonAddon.id("textures/entity/barnacle_pull.png");
    private static final float SEGMENT_LENGTH = 4.0F / 16.0F;
    private final ModelPart segment;

    public BarnaclePullRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.segment = context.bakeLayer(DarkBlueMoonModels.BARNACLE_PULL);
    }

    @Override
    public void render(BarnaclePullEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight) {
        if (!entity.isInvisible() || !entity.isInvisibleTo(Minecraft.getInstance().player)) {
            Vec3 extent = entity.getPosition(partialTicks).subtract(entity.getOriginPoint(partialTicks));
            float length = (float) extent.length();
            if (length > 1.0E-5F) {
                LivingEntity owner = entity.getOwner();
                if (owner != null) {
                    packedLight = entityRenderDispatcher.getPackedLightCoords(owner, partialTicks);
                }
                poseStack.pushPose();
                poseStack.scale(1.0F, -1.0F, -1.0F);
                poseStack.mulPose(Axis.YP.rotationDegrees(MathUtil.yRotDegFromVec(extent)));
                poseStack.mulPose(Axis.XP.rotationDegrees(MathUtil.xRotDegFromVec(extent)));
                VertexConsumer vertices = buffer.getBuffer(RenderType.entityCutout(TEXTURE));
                float left = length;
                while (left >= SEGMENT_LENGTH) {
                    segment.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);
                    left -= SEGMENT_LENGTH;
                    poseStack.translate(0.0F, 0.0F, SEGMENT_LENGTH);
                }
                segment.render(poseStack, vertices, packedLight, OverlayTexture.NO_OVERLAY);
                poseStack.popPose();
            }
        }
        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    public ResourceLocation getTextureLocation(BarnaclePullEntity entity) {
        return TEXTURE;
    }
}
