package rotp.core.mixin.directional_gravity;

import java.util.Optional;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.api.gravity.DirectionalGravityTransforms;

import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.control.LookControl;
import net.minecraft.world.phys.Vec3;

/** A mob's head angles are read inside its gravity frame, so what it looks at is measured in that frame. */
@Mixin(LookControl.class)
public abstract class LookControlDirectionalGravityMixin {
	@Shadow
	@Final
	protected Mob mob;
	@Shadow
	protected double wantedX;
	@Shadow
	protected double wantedY;
	@Shadow
	protected double wantedZ;

	@Inject(method = "getXRotD", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$directionalGravityLookPitch(CallbackInfoReturnable<Optional<Float>> cir) {
		Direction direction = DirectionalGravityApi.getEffectiveDirection(mob);
		if (direction != Direction.DOWN) {
			Vec3 local = DirectionalGravityTransforms.toLocal(
					direction, new Vec3(wantedX, wantedY, wantedZ).subtract(mob.getEyePosition()));
			cir.setReturnValue(Optional.of((float) -(Mth.atan2(local.y, local.horizontalDistance()) * Mth.RAD_TO_DEG)));
		}
	}

	@Inject(method = "getYRotD", at = @At("HEAD"), cancellable = true)
	private void jojo_ripples$directionalGravityLookYaw(CallbackInfoReturnable<Optional<Float>> cir) {
		Direction direction = DirectionalGravityApi.getEffectiveDirection(mob);
		if (direction != Direction.DOWN) {
			Vec3 local = DirectionalGravityTransforms.toLocal(
					direction, new Vec3(wantedX, wantedY, wantedZ).subtract(mob.position()));
			if (local.horizontalDistanceSqr() > 1.0E-10) {
				cir.setReturnValue(Optional.of((float) (Mth.atan2(local.z, local.x) * Mth.RAD_TO_DEG) - 90.0F));
			}
			else {
				cir.setReturnValue(Optional.empty());
			}
		}
	}
}
