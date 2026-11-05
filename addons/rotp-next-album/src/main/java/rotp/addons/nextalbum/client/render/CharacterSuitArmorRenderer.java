package rotp.addons.nextalbum.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import rotp.addons.nextalbum.NextAlbumIds;
import rotp.addons.nextalbum.item.CharacterSuitArmorItem;
import rotp.addons.nextalbum.state.ProsciuttoShadow;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.resources.PlayerSkin;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * Next Album 0.1.13 Pesci/ProsciuttoSuitArmorRenderer: every bone of the
 * piece's general_suit model is drawn (the pants carry a belt on the body
 * bone), and nothing is drawn on a Prosciutto shadow player.
 */
public final class CharacterSuitArmorRenderer
        extends GeoArmorRenderer<CharacterSuitArmorItem> {
    public CharacterSuitArmorRenderer() {
        super(new Model());
    }

    @Override
    public void prepForRender(
            Entity entity,
            ItemStack stack,
            EquipmentSlot slot,
            HumanoidModel<?> baseModel,
            MultiBufferSource bufferSource,
            float partialTick,
            float limbSwing,
            float limbSwingAmount,
            float netHeadYaw,
            float headPitch) {
        ((CharacterSuitArmorItem) stack.getItem()).setRenderingState(
                entity instanceof AbstractClientPlayer player
                        && player.getSkin().model() == PlayerSkin.Model.SLIM,
                stack);
        super.prepForRender(entity, stack, slot, baseModel, bufferSource,
                partialTick, limbSwing, limbSwingAmount, netHeadYaw,
                headPitch);
    }

    @Override
    protected void applyBoneVisibilityBySlot(EquipmentSlot currentSlot) {
        setAllVisible(true);
    }

    @Override
    public void renderToBuffer(
            PoseStack poseStack,
            VertexConsumer buffer,
            int packedLight,
            int packedOverlay,
            int colour) {
        if (getCurrentEntity() instanceof LivingEntity living
                && ProsciuttoShadow.isShadow(living)) {
            return;
        }
        super.renderToBuffer(poseStack, buffer, packedLight, packedOverlay,
                colour);
    }

    private static final class Model
            extends GeoModel<CharacterSuitArmorItem> {
        @Override
        public ResourceLocation getModelResource(
                CharacterSuitArmorItem animatable) {
            return NextAlbumIds.id(animatable.modelPath());
        }

        @Override
        public ResourceLocation getTextureResource(
                CharacterSuitArmorItem animatable) {
            return NextAlbumIds.id(animatable.texturePath());
        }

        @Override
        public ResourceLocation getAnimationResource(
                CharacterSuitArmorItem animatable) {
            return NextAlbumIds.id(
                    "animations/mista_suit_armor.animation.json");
        }
    }
}
