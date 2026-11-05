package rotp.core.mixin.motiontime;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Slice;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import rotp.core.api.time.EntityTimeRates;
import rotp.core.subsystems.time.MotionTimeScale;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * EntityTimeRates' MOTION rate r on the vertical motion of a living entity, as in a film slowed 1/r times (the provider
 * scales its speeds by r and its gravity by r squared). In the air: the vertical drag so that a fall tends to r times
 * vanilla's terminal speed, Levitation's speed so that it rises r times as fast, the climb on ladders, vines and powder
 * snow so that each tick rises r times as far, the climbing caps times r, Slow Falling's gravity cap times r squared, and
 * a jump the power that reaches its full height under that gravity and drag. In water and lava: vanilla's sinking rule
 * on the velocity divided by r and the gravity divided by r squared, lava's own sinking likewise, and the climb out of
 * water and the boost out of a fluid times r. Everywhere aiStep's rest threshold (0.003) times r, so that a slowed
 * velocity is not stopped as too small. At r = 1 every value is vanilla.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMotionTimeMixin {
	private static final double CLIMB_CAP = (double) 0.15F;
	private static final double FLUID_EXIT_BOOST = (double) 0.3F;

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

	// the lift out of water or lava onto a ledge
	@ModifyConstant(method = "travel", constant = @Constant(doubleValue = FLUID_EXIT_BOOST))
	private double jojo_ripples$fluidExitBoost(double speed) {
		return speed * jojo_ripples$motionRate();
	}

	@ModifyConstant(method = "travel", constant = @Constant(doubleValue = 0.01))
	private double jojo_ripples$slowFallingGravityCap(double cap) {
		float rate = jojo_ripples$motionRate();
		return rate == 1.0F ? cap : MotionTimeScale.slowFallingCap(cap, rate);
	}

	// levitation keeps vanilla's 0.2 share of the way a tick; its speed gives r times vanilla's steady rise at the air drag
	@ModifyConstant(method = "travel", constant = @Constant(doubleValue = 0.05))
	private double jojo_ripples$levitationSpeed(double speed) {
		float rate = jojo_ripples$motionRate();
		return rate == 1.0F ? speed : speed * MotionTimeScale.levitationSpeedFactor(rate);
	}

	// the vertical drag at the end of the air branch, after Levitation (the elytra's 0.98 comes before it)
	@ModifyConstant(method = "travel", constant = @Constant(doubleValue = MotionTimeScale.AIR_DRAG),
			slice = @Slice(from = @At(value = "FIELD",
					target = "Lnet/minecraft/world/effect/MobEffects;LEVITATION:Lnet/minecraft/core/Holder;")))
	private double jojo_ripples$airDrag(double drag) {
		float rate = jojo_ripples$motionRate();
		return rate == 1.0F ? drag : MotionTimeScale.airDrag(rate);
	}

	// water's and shallow lava's sinking rule (gravity / 16, the 0.003 floor) on the velocity in vanilla's units
	@WrapOperation(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;"
			+ "getFluidFallingAdjustedMovement(DZLnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/Vec3;"))
	private Vec3 jojo_ripples$fluidFallingAtRate(LivingEntity self, double gravity, boolean isFalling, Vec3 motion,
			Operation<Vec3> original) {
		float rate = jojo_ripples$motionRate();
		if (rate == 1.0F || !(rate > 0.0F)) {
			return original.call(self, gravity, isFalling, motion);
		}
		Vec3 vanillaUnits = new Vec3(motion.x, motion.y / rate, motion.z);
		Vec3 adjusted = original.call(self, gravity / ((double) rate * rate), isFalling, vanillaUnits);
		return adjusted == vanillaUnits ? motion : new Vec3(adjusted.x, adjusted.y * rate, adjusted.z);
	}

	// lava's own sinking, -gravity / 4 a tick, in vanilla's units: -gravity / (4 r)
	@ModifyConstant(method = "travel", constant = @Constant(doubleValue = 4.0))
	private double jojo_ripples$lavaSinkDivisor(double divisor) {
		return divisor * jojo_ripples$motionRate();
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
	private float jojo_ripples$jumpHeight(float power) {
		float rate = jojo_ripples$motionRate();
		if (rate == 1.0F || !(rate > 0.0F)) {
			return power;
		}
		LivingEntity self = (LivingEntity) (Object) this;
		float bonus = self.getJumpBoostPower();
		return (float) MotionTimeScale.jumpPower(power - bonus, bonus, self.getGravity(), MotionTimeScale.baseGravity(self),
				rate);
	}
}
