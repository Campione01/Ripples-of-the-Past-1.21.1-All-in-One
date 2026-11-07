package rotp.core.mixin.motiontime;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;

import rotp.core.api.time.EntityTimeRates;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

@Mixin(Player.class)
public abstract class PlayerSwimMotionTimeMixin {
	@WrapOperation(method = "travel", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/phys/Vec3;add(DDD)Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 jojo_ripples$swimTowardSlowedPitchSpeed(Vec3 motion, double x, double y, double z,
			Operation<Vec3> original, @Local(ordinal = 0) double lookY, @Local(ordinal = 1) double gain) {
		float rate = EntityTimeRates.rate(EntityTimeRates.Channel.MOTION, (Player) (Object) this);
		// Keep the approach and water drag intact; scale the target speed, not the correction toward it.
		return original.call(motion, x, rate < 1.0F ? (rate * lookY - motion.y) * gain : y, z);
	}
}
