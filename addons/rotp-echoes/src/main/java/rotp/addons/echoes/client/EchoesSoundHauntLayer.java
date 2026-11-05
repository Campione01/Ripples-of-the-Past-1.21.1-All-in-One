package rotp.addons.echoes.client;

import rotp.addons.echoes.RotpEchoes;
import rotp.addons.echoes.init.EchoesMobEffects;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * R772 F11: ACT1's sound drawn on the entity it is on (the master's SoundHauntLayer, added to every living renderer):
 * one of the author's three haunt overlays by the effect's level, for Stand users only.
 */
public final class EchoesSoundHauntLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
    private static final ResourceLocation[] TEXTURES = {
            RotpEchoes.id("textures/entity/biped/layer/sound_haunt/1.png"),
            RotpEchoes.id("textures/entity/biped/layer/sound_haunt/2.png"),
            RotpEchoes.id("textures/entity/biped/layer/sound_haunt/3.png")
    };

    public EchoesSoundHauntLayer(RenderLayerParent<T, M> renderer) {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity, float limbSwing,
            float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        MobEffectInstance haunt = entity.getEffect(EchoesMobEffects.SOUND_HAUNT);
        if (haunt == null || entity.isInvisible() || !EchoesOnomatopoeiaMarkRenderer.viewerHasStand()) {
            return;
        }
        ResourceLocation texture = TEXTURES[Mth.clamp(haunt.getAmplifier(), 0, TEXTURES.length - 1)];
        getParentModel().renderToBuffer(poseStack, buffer.getBuffer(RenderType.entityTranslucent(texture)),
                packedLight, LivingEntityRenderer.getOverlayCoords(entity, 0.0F), -1);
    }
}
