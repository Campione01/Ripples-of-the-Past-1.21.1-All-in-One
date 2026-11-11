package rotp.core.api.stand;

import java.util.List;

import javax.annotation.Nullable;

import rotp.core.api.perception.PerceptionBlockers;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.ActionTargetAim;
import rotp.core.subsystems.target.ActionTargetRange;
import rotp.core.subsystems.target.StandPrecisionRay;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * The rules by which The World's and Star Platinum's time-stop blink (TimeStopBlinkAbility) picks where it goes and
 * lands, for another instant move that lands the same way (King Crimson's Time Erase step): the reach a user walks in a
 * number of ticks, the target, the landing position, the landing a blink refuses and the ticks a trip implies. The blink
 * itself uses these. Server side; the cost, cooldown, teleport and effects are the caller's.
 */
public final class BlinkLanding {
	/** The farthest a blink goes, whatever the ticks. */
	public static final double MAX_DISTANCE = 192;

	private BlinkLanding() {}

	/** The blocks the user walks in a tick: its movement speed times 2.1585 (1.16 TimeStopInstant). */
	public static double distancePerTick(LivingEntity user) {
		return user.getAttributeValue(Attributes.MOVEMENT_SPEED) * 2.1585;
	}

	/** How far the user reaches in this many ticks of walking, at most maxDistance. */
	public static double reach(LivingEntity user, int ticks, double maxDistance) {
		return Math.min(distancePerTick(user) * ticks, maxDistance);
	}

	/**
	 * What a blink goes to: the entity or block the user aims at within its Stand's range (with the Stand out and aim
	 * assist on, the Stand's precision ray when the aim is empty), else the nearest living entity or block on the user's
	 * look ray within maxDistance, else nothing (ActionTarget.EMPTY: the blink goes the whole way). Entities the user
	 * cannot perceive in an instant stop (PerceptionBlockers) and the user's own Stand are left out.
	 *
	 * @param power the user's Stand power, for the Stand's precision ray; null for none
	 */
	public static ActionTarget target(LivingEntity user, @Nullable StandPower power, double maxDistance,
			boolean wallsHideEntities) {
		ActionTarget target = aimedTarget(user, power, wallsHideEntities);
		return target.isEmpty(user.level()) ? lookTarget(user, maxDistance, wallsHideEntities) : target;
	}

	/**
	 * Where a blink at the target lands. An entity: in front of it on the user's line, or where the user stands if it
	 * is already that close; behind it when behindEntity is true. A block: on top of it when the block above is empty,
	 * else against the face aimed at. Nothing: maxDistance along the look from the eyes, dropped to the ground.
	 */
	public static Vec3 landingPos(ServerLevel level, LivingEntity user, ActionTarget target, double maxDistance,
			boolean behindEntity) {
		if (target.isEmpty(level)) {
			return missPos(level, user, maxDistance);
		}
		return switch (target.getType()) {
			case ENTITY -> entityPos(user, target.getEntity(), behindEntity);
			case BLOCK -> blockPos(level, target);
			default -> missPos(level, user, maxDistance);
		};
	}

	/**
	 * Whether a blink refuses this landing: on or next to an entity the user cannot perceive in an instant stop (half a
	 * block around the user's box there), as it may not reach what its user does not know is there.
	 */
	public static boolean refusesLanding(Level level, Entity user, Vec3 landingPos) {
		return PerceptionBlockers.hasBlockers() && PerceptionBlockers.anyBlockedIn(level, user,
				user.getDimensions(user.getPose()).makeBoundingBox(landingPos).inflate(0.5D),
				PerceptionBlockers.Sight.INSTANT_TIME_STOP);
	}

	/** The ticks the user would walk to the landing position, rounded up, from 0 to maxTicks. */
	public static int impliedTicks(LivingEntity user, Vec3 landingPos, int maxTicks) {
		double speed = distancePerTick(user);
		if (speed <= 0) {
			return 0;
		}
		return Mth.clamp(Mth.ceil(landingPos.subtract(user.position()).length() / speed), 0, maxTicks);
	}

	/** The entity a blink would land by: the user aims at it, or it is behind it. */
	public static Vec3 entityPos(LivingEntity user, Entity targetEntity, boolean behindEntity) {
		if (behindEntity) {
			return targetEntity.position().subtract(Vec3.directionFromRotation(0, targetEntity.getYRot())
					.scale(targetEntity.getBbWidth() + user.getBbWidth()));
		}
		double distance = targetEntity.getBbWidth() + user.getBbWidth();
		return user.distanceToSqr(targetEntity) > distance * distance
				? targetEntity.position().subtract(user.getLookAngle().scale(distance))
				: user.position();
	}

	private static Vec3 blockPos(ServerLevel level, ActionTarget target) {
		BlockPos blockPos = target.getBlockPos();
		BlockPos standPos = level.isEmptyBlock(blockPos.above()) ? blockPos.above() : blockPos.relative(target.getFace());
		return Vec3.atBottomCenterOf(standPos);
	}

	private static Vec3 missPos(ServerLevel level, LivingEntity user, double maxDistance) {
		Vec3 pos = user.getEyePosition().add(user.getLookAngle().scale(maxDistance));
		BlockPos blockPos = BlockPos.containing(pos);
		while (blockPos.getY() > level.getMinBuildHeight() && level.isEmptyBlock(blockPos.below())) {
			blockPos = blockPos.below();
		}
		double y = blockPos.getY() > level.getMinBuildHeight() ? blockPos.getY() : user.position().y;
		return new Vec3(pos.x, y, pos.z);
	}

	private static ActionTarget aimedTarget(LivingEntity user, @Nullable StandPower power,
			boolean wallsHideEntities) {
		ActionTargetAim aim = LivingComponentAction.getComponent(user).entityAim;
		// 1.16 PowerBaseImpl.checkTarget: a press on an entity the server no longer has is a press on nothing
		if (!user.level().isClientSide() && aim.isClientEntityMissing()) {
			return ActionTarget.EMPTY;
		}
		ActionTarget target = aim.getTarget().copy().resolveEntityId(user.level());
		// 1.16 EntityStandType.clientHitResult: with the Stand out, a vanilla miss became the Stand's precision ray
		StandEntity summoned = power != null ? power.getSummonedStandEntity() : null;
		if (target.getType() == ActionTarget.TargetType.EMPTY && summoned != null && StandEntityPunchAbility.aimAssist(user)) {
			target = StandPrecisionRay.trace(user, summoned, StandPrecisionRay.reach(user, summoned), 0.0D, true,
					wallsHideEntities);
		}
		if (target.getType() == ActionTarget.TargetType.ENTITY) {
			Entity entity = target.getEntity();
			if (entity == null || entity == user || entity.level() != user.level() || entity.isRemoved()
					|| entity.isSpectator() || !entity.isPickable()
					|| entity instanceof StandEntity stand && stand.getUser() == user
					|| PerceptionBlockers.isBlocked(user, entity, PerceptionBlockers.Sight.INSTANT_TIME_STOP)) {
				return ActionTarget.EMPTY;
			}
		}
		else if (target.getType() == ActionTarget.TargetType.BLOCK && !user.level().hasChunkAt(target.getBlockPos())) {
			return ActionTarget.EMPTY;
		}
		return ActionTargetRange.isTargetWithinRange(ActionTargetRange.standPerformer(user), target, user.level())
				? target : ActionTarget.EMPTY;
	}

	// 1.16 JojoModUtil.rayTrace: the nearest pickable living entity on the look ray, else the block by its outline
	private static ActionTarget lookTarget(LivingEntity user, double maxDistance, boolean wallsHideEntities) {
		List<ActionTarget> targets = StandPrecisionRay.clipTargets(user, maxDistance,
				entity -> canTarget(user, entity), 0.0D, 0.0D, wallsHideEntities);
		return targets.isEmpty() ? ActionTarget.EMPTY : targets.get(0);
	}

	private static boolean canTarget(LivingEntity user, Entity entity) {
		if (!(entity instanceof LivingEntity)) {
			return false;
		}
		if (entity instanceof StandEntity stand && stand.getUser() == user) {
			return false;
		}
		return !PerceptionBlockers.isBlocked(user, entity, PerceptionBlockers.Sight.INSTANT_TIME_STOP);
	}
}
