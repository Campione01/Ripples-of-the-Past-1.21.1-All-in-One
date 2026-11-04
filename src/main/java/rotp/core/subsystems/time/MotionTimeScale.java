package rotp.core.subsystems.time;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The arithmetic of EntityTimeRates' MOTION rate r for a living entity whose provider slows its walking and its gravity
 * by attributes (Made in Heaven: speeds times r, gravity times r squared) while every per-tick drag stays vanilla's: the
 * paths no attribute reaches get speeds and pushes times r, a climb keeps r times its per-tick rise under the gravity
 * the entity now has, and Jump Boost keeps the height its bonus adds at the entity's base gravity, reached later.
 */
@ApiStatus.Internal
public final class MotionTimeScale {
	/** LivingEntity.travel's vertical air drag. */
	static final double AIR_DRAG = 0.98F;
	private static final int MAX_JUMP_TICKS = 100_000;

	private MotionTimeScale() {}

	/** The entity's gravity without its attribute modifiers, 0 without gravity. */
	public static double baseGravity(LivingEntity entity) {
		if (entity.isNoGravity()) {
			return 0.0;
		}
		AttributeInstance gravity = entity.getAttribute(Attributes.GRAVITY);
		return gravity != null ? gravity.getBaseValue() : entity.getGravity();
	}

	/**
	 * The climb speed travel sets on a ladder, vine or powder snow, so that the rise of each tick, (speed - gravity)
	 * times the drag, is r times vanilla's (speed - base gravity) times the drag.
	 */
	public static double climbSpeed(double vanillaSpeed, float rate, double gravity, double baseGravity) {
		return (vanillaSpeed - baseGravity) * rate + gravity;
	}

	/**
	 * The jump power that reaches, under the entity's gravity, the height its plain power plus Jump Boost's bonus
	 * reaches at its base gravity, taking the plain power for the height it reaches now (the provider keeps that).
	 */
	public static double jumpPowerKeepingHeight(double plainPower, double bonus, double gravity, double baseGravity) {
		if (!(gravity > 0.0) || !(baseGravity > 0.0) || !(plainPower >= 0.0) || !Double.isFinite(bonus)) {
			return plainPower + bonus;
		}
		double basePlainPower = powerForHeight(jumpHeight(plainPower, gravity), baseGravity);
		return powerForHeight(jumpHeight(basePlainPower + bonus, baseGravity), gravity);
	}

	/** LivingEntity.travel in the air: each tick the entity moves by its velocity, then it becomes (v - g) x 0.98. */
	public static double jumpHeight(double power, double gravity) {
		double height = 0.0;
		double velocity = power;
		for (int tick = 0; tick < MAX_JUMP_TICKS && velocity > 0.0; tick++) {
			height += velocity;
			velocity = (velocity - gravity) * AIR_DRAG;
		}
		return height;
	}

	/** The least power whose jump reaches the height under this gravity. */
	public static double powerForHeight(double height, double gravity) {
		if (!(height > 0.0)) {
			return 0.0;
		}
		double low = 0.0;
		double high = 1.0;
		for (int step = 0; step < 32 && jumpHeight(high, gravity) < height; step++) {
			high *= 2.0;
		}
		for (int step = 0; step < 50; step++) {
			double middle = (low + high) / 2.0;
			if (jumpHeight(middle, gravity) < height) {
				low = middle;
			}
			else {
				high = middle;
			}
		}
		return high;
	}
}
