package rotp.core.mixin.motiontime;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import rotp.core.api.time.EntityTimeRates;
import rotp.core.subsystems.time.MotionTimeScale;

import net.minecraft.world.entity.LivingEntity;

/**
 * EntityTimeRates' MOTION rate r on the motion of a living entity that no attribute scales, the way the attributes
 * scale its walking (speeds and pushes times r, every per-tick drag kept): the climb on ladders, vines and powder snow
 * rises r times as far a tick under the gravity the entity now has, the climbing caps, the water climb and levitation's
 * speed times r, aiStep's rest threshold (0.003) times r so that a slowed velocity is not stopped as too small, and a
 * jump with Jump Boost reaches the height it reaches at the entity's base gravity. At r = 1 every value is vanilla.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMotionTimeMixin {
	private static final double CLIMB_CAP = (double) 0.15F;

	private float jojo_ripples$motionRate() {
		return EntityTimeRates.rate(EntityTimeRates.Channel.MOTION, (LivingEntity) (Object) this);
	}

	@ModifyConstant(method = "handleRelativeFrictionAndCalculateMovement", constant = @Constant(doubleValue = 0.2))
	private double jojo_ripples$climbSpeed(double speed) {
		float rate = jojo_ripples$motionRate();
		if (rate == 1.0F) {
			return speed;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		return MotionTimeScale.climbSpeed(speed, rate, self.getGravity(), MotionTimeScale.baseGravity(self));
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

	// levitation keeps vanilla's 0.2 share of the way a tick: with the drag per tick, r times the speed is r times the rise
	@ModifyConstant(method = "travel", constant = @Constant(doubleValue = 0.05))
	private double jojo_ripples$levitationSpeed(double speed) {
		return speed * jojo_ripples$motionRate();
	}

	// aiStep's three |v| < 0.003 checks; the rate is asked only for a component that vanilla would stop
	@ModifyExpressionValue(method = "aiStep", at = @At(value = "INVOKE", target = "Ljava/lang/Math;abs(D)D"), require = 3)
	private double jojo_ripples$restThreshold(double magnitude) {
		if (magnitude == 0.0 || !(magnitude < LivingEntity.MIN_MOVEMENT_DISTANCE)) {
			return magnitude;
		}
		float rate = jojo_ripples$motionRate();
		return rate < 1.0F ? magnitude / rate : magnitude;
	}

	@ModifyReturnValue(method = "getJumpPower(F)F", at = @At("RETURN"))
	private float jojo_ripples$jumpBoostHeight(float power) {
		LivingEntity self = (LivingEntity) (Object) this;
		float bonus = self.getJumpBoostPower();
		if (bonus == 0.0F) {
			return power;
		}
		float rate = jojo_ripples$motionRate();
		if (rate == 1.0F) {
			return power;
		}
		return (float) MotionTimeScale.jumpPowerKeepingHeight(power - bonus, bonus, self.getGravity(),
				MotionTimeScale.baseGravity(self));
	}
}
