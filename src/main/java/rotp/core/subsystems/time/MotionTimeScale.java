package rotp.core.subsystems.time;

import org.jetbrains.annotations.ApiStatus;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;

/**
 * The arithmetic of EntityTimeRates' MOTION rate r for a living entity that moves as in a film slowed 1/r times. The
 * provider scales its walking and swimming speeds by r and its gravity by r squared (Made in Heaven). In the air the core
 * keeps the vertical motion on that film: velocities times r, accelerations times r squared, and the per-tick vertical
 * drag weakened ({@link #airDrag}) so that a fall's terminal speed is r times vanilla's; a jump reaches its full height
 * about 1/r times later. In water and lava, whose per-tick drag brings a being to its steady speed within a few ticks,
 * vanilla's per-tick rules run on the velocity divided by r and the gravity divided by r squared, so that sinking, a
 * bubble column and swimming up keep r times vanilla's speeds. At r = 1 every value is vanilla.
 */
@ApiStatus.Internal
public final class MotionTimeScale {
	/** LivingEntity.travel's vertical air drag. */
	public static final double AIR_DRAG = 0.98F;
	/** The share of its velocity Levitation keeps each tick (travel: v + (speed - v) x 0.2). */
	private static final double LEVITATION_KEEP = 1.0 - 0.2;
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
	 * The per-tick vertical drag in the air at rate r: under gravity times r squared a fall then tends to r times vanilla's
	 * terminal speed, g d / (1 - d) (3.92 blocks a tick), and it gets there about 1/r times later (about d^r a tick).
	 */
	public static double airDrag(float rate) {
		if (rate == 1.0F || !(rate > 0.0F)) {
			return AIR_DRAG;
		}
		return AIR_DRAG / (AIR_DRAG + rate * (1.0 - AIR_DRAG));
	}

	/** The factor on Levitation's speed that gives r times vanilla's steady rise, (0.2 speed d) / (1 - 0.8 d), at the air drag of r. */
	public static double levitationSpeedFactor(float rate) {
		if (rate == 1.0F || !(rate > 0.0F)) {
			return 1.0;
		}
		double drag = airDrag(rate);
		return rate * AIR_DRAG * (1.0 - LEVITATION_KEEP * drag) / (drag * (1.0 - LEVITATION_KEEP * AIR_DRAG));
	}

	/**
	 * The climb speed travel sets on a ladder, vine or powder snow, so that the rise of each tick, (speed - gravity)
	 * times the air drag of r, is r times vanilla's (speed - base gravity) times its drag.
	 */
	public static double climbSpeed(double vanillaSpeed, float rate, double gravity, double baseGravity) {
		return (vanillaSpeed - baseGravity) * rate * AIR_DRAG / airDrag(rate) + gravity;
	}

	/** Slow Falling's cap on the gravity, an acceleration: r squared times vanilla's. */
	public static double slowFallingCap(double vanillaCap, float rate) {
		return vanillaCap * rate * rate;
	}

	/**
	 * The jump power that reaches, under the entity's gravity and the air drag of r, the height its plain power plus Jump
	 * Boost's bonus reaches at its base gravity and vanilla's drag. The provider keeps the plain jump's height under
	 * vanilla's drag (Made in Heaven's jump strength does), so the plain power is taken for that height.
	 */
	public static double jumpPower(double plainPower, double bonus, double gravity, double baseGravity, float rate) {
		if (!(gravity > 0.0) || !(plainPower >= 0.0) || !Double.isFinite(bonus)) {
			return plainPower + bonus;
		}
		double height = jumpHeight(plainPower, gravity, AIR_DRAG);
		if (bonus != 0.0) {
			if (!(baseGravity > 0.0)) {
				return plainPower + bonus;
			}
			double basePlainPower = powerForHeight(height, baseGravity, AIR_DRAG);
			height = jumpHeight(basePlainPower + bonus, baseGravity, AIR_DRAG);
		}
		return powerForHeight(height, gravity, airDrag(rate));
	}

	/** LivingEntity.travel in the air at vanilla's drag: each tick the entity moves by its velocity, then it becomes (v - g) x 0.98. */
	public static double jumpHeight(double power, double gravity) {
		return jumpHeight(power, gravity, AIR_DRAG);
	}

	/** The least power whose jump reaches the height under this gravity and vanilla's drag. */
	public static double powerForHeight(double height, double gravity) {
		return powerForHeight(height, gravity, AIR_DRAG);
	}

	/** Each tick the entity moves by its velocity, then it becomes (v - g) x drag. */
	public static double jumpHeight(double power, double gravity, double drag) {
		double height = 0.0;
		double velocity = power;
		for (int tick = 0; tick < MAX_JUMP_TICKS && velocity > 0.0; tick++) {
			height += velocity;
			velocity = (velocity - gravity) * drag;
		}
		return height;
	}

	/** The least power whose jump reaches the height under this gravity and drag. */
	public static double powerForHeight(double height, double gravity, double drag) {
		if (!(height > 0.0)) {
			return 0.0;
		}
		// without drag a jump of power p rises about p^2 / 2g + p / 2 (within g / 8), and a drag only lowers it
		double low = gravity > 0.0
				? 0.9 * Math.max(0.0, Math.sqrt(2.0 * gravity * height + gravity * gravity / 4.0) - gravity / 2.0) : 0.0;
		double high = Math.max(2.0 * low, 1.0E-3);
		for (int step = 0; step < 64 && jumpHeight(high, gravity, drag) < height; step++) {
			low = high;
			high *= 2.0;
		}
		for (int step = 0; step < 50; step++) {
			double middle = (low + high) / 2.0;
			if (jumpHeight(middle, gravity, drag) < height) {
				low = middle;
			}
			else {
				high = middle;
			}
		}
		return high;
	}
}
