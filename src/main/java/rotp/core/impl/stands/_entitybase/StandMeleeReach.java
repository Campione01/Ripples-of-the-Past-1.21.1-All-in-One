package rotp.core.impl.stands._entitybase;

import javax.annotation.Nullable;

import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.HitResultUtil;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The reach of a close-range Stand's melee attacks (owner, 2026-10-30 and 2026-11-01). Its punches, heavy punches and
 * barrages strike an entity as far as its base reach, the larger of its user's reach (1.16 StandEntity.getAimDistance)
 * and its max range (The World 10, Star Platinum its user's 5), plus a margin past it, a quarter of the base reach and
 * at least a block (StandStatFormulas#meleeReachMargin): an entity the Stand cannot reach from where it stands, which is
 * on its user's look and in its user's sight ({@link #reachable}). The Stand flies out to it ({@link #flyOutOffset}) at
 * its own speed ({@link #flightSpeed}). Full strength up to the base reach; through the margin the damage and knockback
 * fall to a quarter ({@link #strikeFactor}). Under remote control the Stand goes as far as its base reach plus half of it
 * (two blocks at least) and weakens through that margin (StandEntity#updateStrengthMultipliers). A Stand away from its
 * user to strike cannot guard ({@link #isAwayToStrike}). What its Stand reaches from where it is stays as in 1.16.
 */
public final class StandMeleeReach {
	/** The longest max range of a close-range Stand; a longer-ranged one (Hierophant Green's 100) keeps 1.16's rules. */
	public static final double CLOSE_RANGE_MAX = 10;
	/** How far from its user a Stand counts as away, past its offsets of 1.16 (at most two blocks in front). */
	public static final double AWAY_DISTANCE = 3;

	private StandMeleeReach() {}

	/**
	 * A close-range Stand: a max range of {@link #CLOSE_RANGE_MAX} at most and a type that fights in its user's reach
	 * (StandControlType CLOSE_RANGE_DIRECT or HYBRID_FORM), not a long-distance, automatic, colony or phenomenon one.
	 */
	public static boolean isCloseRange(StandEntity stand) {
		return stand.getMaxRange() <= CLOSE_RANGE_MAX && hasCloseRangeControl(stand.getStandType());
	}

	private static boolean hasCloseRangeControl(@Nullable ResourceLocation standId) {
		StandType type = standId != null ? StandType.fromId(standId) : null;
		if (type == null) {
			return true;
		}
		StandControlType control = type.getStandControlType();
		return control == StandControlType.CLOSE_RANGE_DIRECT || control == StandControlType.HYBRID_FORM;
	}

	public static double baseReach(StandEntity stand, LivingEntity user) {
		return Math.max(StandEntityPunchAbility.userAimReach(user, stand), stand.getMaxRange());
	}

	public static double maxReach(StandEntity stand, LivingEntity user, boolean remoteControl) {
		double base = baseReach(stand, user);
		return base + StandStatFormulas.meleeReachMargin(base, remoteControl);
	}

	/** A punch, heavy punch or barrage, an add-on's that extends them too, but not a grab's. */
	public static boolean isReachingAttack(@Nullable EntityActionType ability) {
		return ability != null && ability.getAbilityUsageCategory() != AbilityUsageGroup.GRAB
				&& (ability instanceof StandEntityPunchAbility || ability instanceof StandEntityHeavyPunchAbility
						|| ability instanceof StandEntityBarrageAbility);
	}

	/**
	 * Whether the Stand's current action reaches out: a reaching attack ({@link #isReachingAttack}) of a close-range Stand
	 * whose user aims it and that follows its user whole, not under remote control nor in its arms-only mode.
	 */
	public static boolean reachesOut(StandEntity stand) {
		EntityActionInstance action = LivingComponentAction.getCurEntityAction(stand);
		return action != null && isReachingAttack(action.ability) && stand.getUser() != null && isCloseRange(stand)
				&& !stand.aimsWithoutUser() && stand.isFollowingUser() && !stand.isArmsOnlyMode();
	}

	/** From the user's eyes to the nearest point of the entity's box. */
	public static double userDistance(LivingEntity user, Entity target) {
		return Math.sqrt(target.getBoundingBox().distanceToSqr(user.getEyePosition()));
	}

	/** Whether a block's collision shape does not stand between the user's eyes and both the entity's eyes and centre. */
	public static boolean userSees(LivingEntity user, Entity target) {
		Vec3 eyes = user.getEyePosition();
		return !wallBetween(user, eyes, target.getEyePosition()) || !wallBetween(user, eyes, target.getBoundingBox().getCenter());
	}

	private static boolean wallBetween(LivingEntity user, Vec3 from, Vec3 to) {
		return HitResultUtil.clipBlocks(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, user),
				user.level()).getType() != HitResult.Type.MISS;
	}

	/**
	 * Whether the entity is ahead of the user and within the Stand's own reach of the user's look line, so that a Stand
	 * flying out along that look reaches it.
	 */
	public static boolean onUserLook(StandEntity stand, LivingEntity user, Entity target) {
		Vec3 eyes = user.getEyePosition();
		Vec3 look = user.getLookAngle();
		AABB box = target.getBoundingBox();
		double along = box.getCenter().subtract(eyes).dot(look);
		if (along <= 0) {
			return false;
		}
		double reach = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		return box.distanceToSqr(eyes.add(look.scale(along))) <= reach * reach;
	}

	/**
	 * Whether the Stand's current action reaches out to the entity: it reaches out ({@link #reachesOut}), the entity is
	 * within the max reach of the user, on the user's look ({@link #onUserLook}) and in the user's sight.
	 */
	public static boolean reachable(StandEntity stand, Entity entity) {
		LivingEntity user = stand.getUser();
		return user != null && reachesOut(stand) && userDistance(user, entity) <= maxReach(stand, user, false)
				&& onUserLook(stand, user, entity) && userSees(user, entity);
	}

	/** Whether a strike reaches the entity: the Stand reaches it from where it is (1.16), or reaches out to it. */
	public static boolean strikeReaches(StandEntity stand, Entity entity, boolean standReaches) {
		return standReaches || reachable(stand, entity);
	}

	/** Whether the action keeps the entity: within 1.16's range of the action, or as far as it reaches out to it. */
	public static boolean keepsEntity(StandEntity stand, Entity entity, boolean keptBy116Range) {
		return keptBy116Range || reachable(stand, entity);
	}

	/** Whether the entity a reaching attack aimed at has left its max reach or its user's sight, alive. */
	public static boolean leftReach(StandEntity stand, Entity struck) {
		LivingEntity user = stand.getUser();
		if (user == null || !struck.isAlive() || !reachesOut(stand)) {
			return false;
		}
		return userDistance(user, struck) > maxReach(stand, user, false) || !userSees(user, struck);
	}

	/** The share of its damage and knockback a strike of the Stand's current action deals to the entity. */
	public static float strikeFactor(StandEntity stand, Entity target) {
		LivingEntity user = stand.getUser();
		if (user == null || !reachesOut(stand)) {
			return 1F;
		}
		double base = baseReach(stand, user);
		return StandStatFormulas.meleeReachStrengthFactor(userDistance(user, target), base,
				StandStatFormulas.meleeReachMargin(base, false));
	}

	/**
	 * The forward offset of a reaching attack's Stand for its target entity: as 1.16's offsetToTarget measured, in front
	 * of the entity, when the action reaches out to it and from the attack's own offset the Stand would stop more than
	 * its reach less a block short of that place; the attack's own offset otherwise.
	 */
	public static double flyOutOffset(StandEntity stand, Entity target, double offsetToTarget, double frontOffset) {
		double reach = stand.getAttributeValue(Attributes.ENTITY_INTERACTION_RANGE);
		if (offsetToTarget - frontOffset <= reach - 1 || !reachable(stand, target)) {
			return frontOffset;
		}
		return Math.min(offsetToTarget, maxReach(stand, stand.getUser(), false));
	}

	/** Whether a reaching attack whose Stand would stand at the forward offset flies out to the entity instead. */
	public static boolean fliesOutTo(StandEntity stand, Entity target, double frontOffset) {
		LivingEntity user = stand.getUser();
		if (user == null) {
			return false;
		}
		double offsetToTarget = new Vec3(target.getX() - user.getX(), 0, target.getZ() - user.getZ()).length()
				- (1.0 + target.getBoundingBox().getXsize() / 2.0);
		return flyOutOffset(stand, target, offsetToTarget, frontOffset) > frontOffset;
	}

	/** The blocks a Stand flies in a tick to strike and back: ten times its movement speed, 1 + speed / 2 (The World 9). */
	public static double flightSpeed(StandEntity stand) {
		return Math.max(stand.getAttributeValue(Attributes.MOVEMENT_SPEED) * 10, 0.5);
	}

	/** How far from its user a close-range Stand counts as away: past its offsets of 1.16 and its idle place. */
	public static double awayDistance(StandEntity stand) {
		return Math.max(AWAY_DISTANCE, stand.offsetFromUser.idleOffset.length() + 1);
	}

	/** Whether a close-range Stand that follows its user is away from it to strike, or not back yet. */
	public static boolean isAwayToStrike(StandEntity stand) {
		LivingEntity user = stand.getUser();
		if (user == null || !isCloseRange(stand) || !stand.isFollowingUser() || stand.isArmsOnlyMode()) {
			return false;
		}
		double away = awayDistance(stand);
		return stand.distanceToSqr(user) > away * away;
	}

	/** The share of its strength a Stand under remote control has at its distance from its user. */
	public static float remoteStrengthFactor(StandEntity stand, LivingEntity user) {
		double base = baseReach(stand, user);
		return StandStatFormulas.meleeReachStrengthFactor(stand.distanceTo(user), base,
				StandStatFormulas.meleeReachMargin(base, true));
	}
}
