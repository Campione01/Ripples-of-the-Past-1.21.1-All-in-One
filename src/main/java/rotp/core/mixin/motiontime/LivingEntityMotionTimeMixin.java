package rotp.core.mixin.motiontime;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import rotp.core.api.time.EntityTimeRates;

import net.minecraft.world.entity.LivingEntity;

/**
 * EntityTimeRates' MOTION rate r on the motion of a living entity that no attribute scales, as if its time ran r times
 * as fast: speeds and caps times r (climbing on ladders, vines and powder snow, the descent cap, levitation's speed,
 * Jump Boost's bonus to a jump), levitation's per-tick approach to its speed 1 - 0.8^r. At r = 1 every value is vanilla.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMotionTimeMixin {
	private static final double CLIMB_CAP = (double) 0.15F;

	private float jojo_ripples$motionRate() {
		return EntityTimeRates.rate(EntityTimeRates.Channel.MOTION, (LivingEntity) (Object) this);
	}

	@ModifyConstant(method = "handleRelativeFrictionAndCalculateMovement", constant = @Constant(doubleValue = 0.2))
	private double jojo_ripples$climbSpeed(double speed) {
		return speed * jojo_ripples$motionRate();
	}

	@ModifyConstant(method = "handleOnClimbable", constant = {
			@Constant(doubleValue = CLIMB_CAP), @Constant(doubleValue = -CLIMB_CAP) })
	private double jojo_ripples$climbCap(double cap) {
		return cap * jojo_ripples$motionRate();
	}

	// travel's first 0.2: climbing out of water against a climbable block
	@ModifyConstant(method = "travel", constant = @Constant(doubleValue = 0.2, ordinal = 0))
	private double jojo_ripples$waterClimbSpeed(double speed) {
		return speed * jojo_ripples$motionRate();
	}

	@ModifyConstant(method = "travel", constant = @Constant(doubleValue = 0.05))
	private double jojo_ripples$levitationSpeed(double speed) {
		return speed * jojo_ripples$motionRate();
	}

	// travel's second 0.2: the share of the way to levitation's speed taken each tick
	@ModifyConstant(method = "travel", constant = @Constant(doubleValue = 0.2, ordinal = 1))
	private double jojo_ripples$levitationApproach(double approach) {
		float rate = jojo_ripples$motionRate();
		return rate == 1.0F ? approach : 1.0 - Math.pow(1.0 - approach, rate);
	}

	@ModifyReturnValue(method = "getJumpBoostPower", at = @At("RETURN"))
	private float jojo_ripples$jumpBoost(float bonus) {
		return bonus == 0.0F ? bonus : bonus * jojo_ripples$motionRate();
	}
}
