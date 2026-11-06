package rotp.core.mixin.directional_gravity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.api.gravity.DirectionalGravityTransforms;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

@Mixin(Mob.class)
public abstract class MobDirectionalGravityMixin {
	@Shadow
	protected abstract float rotlerp(float angle, float targetAngle, float maxIncrease);

	@Inject(method = "lookAt(Lnet/minecraft/world/entity/Entity;FF)V", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$directionalGravityLookAt(Entity entity, float maxYRotIncrease, float maxXRotIncrease,
			CallbackInfo ci) {
		Mob mob = (Mob) (Object) this;
		Direction direction = DirectionalGravityApi.getEffectiveDirection(mob);
		if (direction == Direction.DOWN) {
			return;
		}
		Vec3 target = entity instanceof LivingEntity ? entity.getEyePosition() : entity.getBoundingBox().getCenter();
		Vec3 local = DirectionalGravityTransforms.toLocal(direction, target.subtract(mob.getEyePosition()));
		mob.setXRot(rotlerp(mob.getXRot(),
				(float) -(Mth.atan2(local.y, local.horizontalDistance()) * Mth.RAD_TO_DEG), maxXRotIncrease));
		mob.setYRot(rotlerp(mob.getYRot(),
				(float) (Mth.atan2(local.z, local.x) * Mth.RAD_TO_DEG) - 90.0F, maxYRotIncrease));
		ci.cancel();
	}
}
