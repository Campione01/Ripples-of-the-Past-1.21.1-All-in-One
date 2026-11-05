package rotp.addons.nextalbum.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import rotp.addons.nextalbum.NextAlbumAddon;
import rotp.addons.nextalbum.client.ClientCigaretteState;
import rotp.addons.nextalbum.item.SpiritCigaretteItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Next Album 0.1.13 CigaretteMouthLayer: the lit cigarettes in a mouth. */
public final class CigaretteMouthLayer
        extends RenderLayer<AbstractClientPlayer,
                PlayerModel<AbstractClientPlayer>> {
    public CigaretteMouthLayer(
            RenderLayerParent<AbstractClientPlayer,
                    PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @Override
    public void render(
            PoseStack poseStack,
            MultiBufferSource buffer,
            int packedLight,
            AbstractClientPlayer player,
            float limbSwing,
            float limbSwingAmount,
            float partialTick,
            float ageInTicks,
            float netHeadYaw,
            float headPitch) {
        int count = ClientCigaretteState.getMouthCount(player.getId());
        if (count <= 0 || player.isInvisible()) {
            return;
        }
        ItemStack stack = new ItemStack(NextAlbumAddon.SPIRIT_CIGARETTE.get());
        SpiritCigaretteItem.setLit(stack, true);
        ItemRenderer itemRenderer = Minecraft.getInstance().getItemRenderer();
        BakedModel model = itemRenderer.getModel(
                stack, player.level(), player, player.getId());
        poseStack.pushPose();
        getParentModel().getHead().translateAndRotate(poseStack);
        poseStack.translate(0.0D, -0.1D, -0.2D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        for (int i = 0; i < count; i++) {
            poseStack.pushPose();
            float centered = count == 1 ? 0.0F : i - (count - 1) * 0.5F;
            poseStack.mulPose(Axis.YP.rotationDegrees(centered * 32.0F));
            poseStack.mulPose(Axis.XP.rotationDegrees(172.0F));
            poseStack.scale(0.55F, 0.55F, 0.55F);
            // 1.16 drew the raw quads after (-0.46875, -0.03125, -0.75);
            // render() moves by -0.5 on each axis itself.
            poseStack.translate(0.03125D, 0.46875D, -0.25D);
            itemRenderer.render(stack, ItemDisplayContext.NONE, false,
                    poseStack, buffer, packedLight,
                    OverlayTexture.NO_OVERLAY, model);
            poseStack.popPose();
        }
        poseStack.popPose();
    }
}
