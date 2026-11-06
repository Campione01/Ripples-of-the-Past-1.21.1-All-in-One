package rotp.core.mixin.directional_gravity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.api.gravity.DirectionalGravityTransforms;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

@Mixin(Projectile.class)
public abstract class ProjectileDirectionalGravityMixin {
	/** A bow, a snowball and the like shoot along the raw angles, which mean the shooter's own frame. */
	@WrapOperation(method = "shootFromRotation",
			at = @At(value = "INVOKE",
					target = "Lnet/minecraft/world/entity/projectile/Projectile;shoot(DDDFF)V"))
	private void jojo_ripples$directionalGravityShootFromRotation(Projectile projectile,
			double x, double y, double z, float velocity, float inaccuracy, Operation<Void> original,
			@Local(argsOnly = true) Entity shooter) {
		Direction direction = DirectionalGravityApi.getEffectiveDirection(shooter);
		if (direction == Direction.DOWN) {
			original.call(projectile, x, y, z, velocity, inaccuracy);
			return;
		}
		Vec3 world = DirectionalGravityTransforms.toWorld(direction, new Vec3(x, y, z));
		original.call(projectile, world.x, world.y, world.z, velocity, inaccuracy);
	}
}
