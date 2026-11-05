package rotp.core.mixin.motiontime;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import rotp.core.api.time.EntityTimeRates;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.phys.Vec3;

/**
 * EntityTimeRates' MOTION rate r of a gliding entity on a firework rocket's boost, as if its time ran r times as fast:
 * vanilla takes half the way to 1.7 times the look a tick (v + 0.1 look + (1.5 look - v) / 2); at rate r the speed it
 * tends to is r times as large and the share of the way 1 - 0.5^r. At r = 1 the vanilla motion is kept.
 */
@Mixin(FireworkRocketEntity.class)
public abstract class FireworkRocketBoostTimeMixin {
	@WrapOperation(method = "tick", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(Lnet/minecraft/world/phys/Vec3;)V"))
	private void jojo_ripples$boostOnTheGlidersTime(LivingEntity glider, Vec3 boosted, Operation<Void> original) {
		float rate = EntityTimeRates.rate(EntityTimeRates.Channel.MOTION, glider);
		if (rate == 1.0F) {
			original.call(glider, boosted);
			return;
		}
		Vec3 motion = glider.getDeltaMovement();
		Vec3 look = glider.getLookAngle();
		double share = 1.0 - Math.pow(0.5, rate);
		Vec3 aim = look.scale(1.5 * rate);
		Vec3 push = look.scale(0.2 * rate * share);
		original.call(glider, motion.add(push).add(aim.subtract(motion).scale(share)));
	}
}
