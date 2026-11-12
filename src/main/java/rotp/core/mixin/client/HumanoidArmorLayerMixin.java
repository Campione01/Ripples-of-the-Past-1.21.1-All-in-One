package rotp.core.mixin.client;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.mechanics.clothes.ClothesArmorPriority;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;

/** Clothes shown over armor: the armor piece a worn clothes piece covers is not drawn. */
@Mixin(HumanoidArmorLayer.class)
public abstract class HumanoidArmorLayerMixin {

	@Inject(method = "renderArmorPiece("
			+ "Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/client/renderer/MultiBufferSource;"
			+ "Lnet/minecraft/world/entity/LivingEntity;"
			+ "Lnet/minecraft/world/entity/EquipmentSlot;"
			+ "I"
			+ "Lnet/minecraft/client/model/HumanoidModel;"
			+ "FFFFFF)V", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$clothesOverArmor(
			PoseStack poseStack,
			MultiBufferSource buffer,
			LivingEntity entity,
			EquipmentSlot slot,
			int packedLight,
			HumanoidModel<?> armorModel,
			float limbSwing,
			float limbSwingAmount,
			float partialTick,
			float ageInTicks,
			float netHeadYaw,
			float headPitch,
			CallbackInfo ci) {
		if (!ClothesArmorPriority.current().showsArmor(entity, slot)) {
			ci.cancel();
		}
	}
}
