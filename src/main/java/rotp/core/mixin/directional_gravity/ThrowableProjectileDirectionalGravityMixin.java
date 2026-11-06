package rotp.core.mixin.directional_gravity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.subsystems.directional_gravity.DirectionalGravityRuntime;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;

@Mixin(ThrowableProjectile.class)
public abstract class ThrowableProjectileDirectionalGravityMixin {
	@Inject(method = "<init>(Lnet/minecraft/world/entity/EntityType;Lnet/minecraft/world/entity/LivingEntity;"
			+ "Lnet/minecraft/world/level/Level;)V", at = @At("TAIL"))
	private void jojo_ripples$directionalGravityThrowableStart(EntityType<? extends ThrowableProjectile> type,
			LivingEntity shooter, Level level, CallbackInfo ci) {
		DirectionalGravityRuntime.startProjectileAtEye((Entity) (Object) this, shooter);
	}
}
