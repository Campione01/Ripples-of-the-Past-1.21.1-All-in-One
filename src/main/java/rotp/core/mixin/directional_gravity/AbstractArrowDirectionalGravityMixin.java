package rotp.core.mixin.directional_gravity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.subsystems.directional_gravity.DirectionalGravityRuntime;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

@Mixin(AbstractArrow.class)
public abstract class AbstractArrowDirectionalGravityMixin {
	@Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/LivingEntity;"
			+ "Lnet/minecraft/world/level/Level;Lnet/minecraft/world/item/ItemStack;"
			+ "Lnet/minecraft/world/item/ItemStack;)V", at = @At("TAIL"))
	private void jojo_ripples$directionalGravityArrowStart(EntityType<? extends AbstractArrow> type,
			LivingEntity owner, Level level, ItemStack pickup, ItemStack weapon, CallbackInfo ci) {
		DirectionalGravityRuntime.startProjectileAtEye((Entity) (Object) this, owner);
	}
}
