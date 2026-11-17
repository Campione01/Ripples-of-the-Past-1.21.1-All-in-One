package rotp.addons.yellowtemperance.client;

import rotp.core.client.firstperson.FirstPersonModelLayer;
import com.mojang.blaze3d.vertex.PoseStack;

import rotp.addons.yellowtemperance.YellowTemperanceAddon;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.16 ErosionLayer: the target's own posed model again, in the donor's patches of yellow, more of them with the
 * erosion's level. A layer runs inside the entity's transform, so the patches stay on the limbs. As the core's
 * HamonBurnLayer, one texture size for every model: a 1.21 model no longer tells its own.
 */
public final class YellowTemperanceErosionLayer<
        T extends LivingEntity,
        M extends EntityModel<T>> extends RenderLayer<T, M>
        implements FirstPersonModelLayer {
    private static final ResourceLocation[] TEXTURES = {
            texture(1), texture(2), texture(3), texture(4), texture(5)
    };

    public YellowTemperanceErosionLayer(
            RenderLayerParent<T, M> parent) {
        super(parent);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            T entity,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        ResourceLocation texture = textureFor(entity);
        if (texture == null || entity.isInvisible()) {
            return;
        }
        getParentModel().renderToBuffer(
                poseStack,
                buffer.getBuffer(RenderType.entityTranslucent(texture)),
                packedLight,
                LivingEntityRenderer.getOverlayCoords(entity, 0.0F));
    }

    @Override
    public void renderHandFirstPerson(
            HumanoidArm side,
            PoseStack poseStack,
            MultiBufferSource buffer,
            int light,
            LivingEntity entity,
            LivingEntityRenderer<?, ?> entityRenderer,
            float partialTick) {
        if (entityRenderer.getModel() instanceof HumanoidModel<?> model) {
            FirstPersonModelLayer.defaultRender(
                    side,
                    poseStack,
                    buffer,
                    light,
                    entity,
                    entityRenderer,
                    model,
                    textureFor(entity),
                    partialTick);
        }
    }

    private static ResourceLocation textureFor(LivingEntity entity) {
        MobEffectInstance erosion =
                entity.getEffect(YellowTemperanceAddon.EROSION);
        return erosion != null
                ? TEXTURES[Math.min(erosion.getAmplifier() / 2, 4)]
                : null;
    }

    private static ResourceLocation texture(int level) {
        return YellowTemperanceAddon.id(
                "textures/entity/layer/erosion/t64x64/" + level + ".png");
    }
}
