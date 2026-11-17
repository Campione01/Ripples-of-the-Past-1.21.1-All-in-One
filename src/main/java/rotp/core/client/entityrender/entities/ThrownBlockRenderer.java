package rotp.core.client.entityrender.entities;

import rotp.core.customobjects.entity_projectile.ThrownBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

public class ThrownBlockRenderer extends EntityRenderer<ThrownBlockEntity> {

	public ThrownBlockRenderer(EntityRendererProvider.Context context) {
		super(context);
		shadowRadius = 0.5F;
	}

	@Override
	public ResourceLocation getTextureLocation(ThrownBlockEntity entity) {
		return InventoryMenu.BLOCK_ATLAS;
	}

	@Override
	public void render(ThrownBlockEntity entity, float yRotation, float partialTick,
			PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
		poseStack.pushPose();
		poseStack.translate(-0.5D, 0.0D, -0.5D);
		Minecraft.getInstance().getBlockRenderer().renderSingleBlock(entity.getBlock(), poseStack, buffer, packedLight, OverlayTexture.NO_OVERLAY);
		poseStack.popPose();
		super.render(entity, yRotation, partialTick, poseStack, buffer, packedLight);
	}
}
