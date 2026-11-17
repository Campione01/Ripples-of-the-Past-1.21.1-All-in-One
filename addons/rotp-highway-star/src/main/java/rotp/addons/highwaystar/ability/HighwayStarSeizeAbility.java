package rotp.addons.highwaystar.ability;

import java.util.List;

import javax.annotation.Nullable;

import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;

import rotp.addons.highwaystar.HighwayStarAddon;
import rotp.addons.highwaystar.entity.HighwayStarEntity;
import rotp.addons.highwaystar.entity.HighwayStarSoleEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/** 1.16 HighwayStarSeizeAction, a StandEntityHeavyAttack whose strike tears off a sole into the creature it reaches. */
public final class HighwayStarSeizeAbility extends StandEntityHeavyPunchAbility {
    public static final float STAMINA_COST = 20.0F;
    private static final double RANGE = 5.0;
    // Fitted to the Stand's own clip, whose lunge is at full stretch at 0.29 s of its 0.33. A heavy punch's lengths at this
    // Stand's speed, 17 and 12 ticks, left the clip's last frame standing for over a second (a player's report).
    public static final int WINDUP_TICKS = 5;
    public static final int RECOVERY_TICKS = 4;

    public HighwayStarSeizeAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        // 1.16 .cooldown(60, 0): technical only
        cooldown(60, 0);
    }

    @Override
    public void initActionFromConfig(EntityActionInstance action, Level level, LivingEntity powerUser, LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        action.phasesLength.put(ActionPhase.WINDUP, WINDUP_TICKS);
        action.phasesLength.put(ActionPhase.RECOVERY, RECOVERY_TICKS);
    }

    @Override
    public EntityActionInstance createActionObj() {
        return new SeizeAction(this);
    }

    public static final class SeizeAction extends StandEntityHeavyPunch {
        public SeizeAction(EntityActionType ability) {
            super(ability);
        }

        // The heavy punch sets its recovery again here, from the finisher it takes.
        @Override
        public void onActionSet(@Nullable EntityActionInstance prevAction) {
            super.onActionSet(prevAction);
            phasesLength.put(ActionPhase.RECOVERY, RECOVERY_TICKS);
        }

        @Override
        public void actionPerformStart() {
            Level level = level();
            if (level.isClientSide() || !(performer instanceof HighwayStarEntity hs)) {
                return;
            }
            LivingEntity user = getPowerUser();
            if (user == null) {
                return;
            }
            StandPower power = StandPower.get(user);
            playHeavyPunchPerformSound(hs);
            seize(level, hs, user);
            // 1.16 StandEntityTask paid staminaCost after standPerform, whatever it did
            StandAbilityStamina.consume(ability, power, STAMINA_COST, false);
        }

        private void seize(Level level, HighwayStarEntity stand, LivingEntity user) {
            ActionTarget aimed = getActionTargetSnapshot(level);
            if (aimed.isEmpty(level)) {
                aimed = LivingComponentAction.getAimTarget(stand.isManuallyControlled() ? stand : user);
            }
            if (!aimed.isEmpty(level)) {
                setTaskTarget(aimed);
            }
            Vec3 look = user.getLookAngle();
            Vec3 start = stand.position().add(0.0, stand.getBbHeight() * 0.5, 0.0);
            if (aimed.getType() == ActionTarget.TargetType.ENTITY
                    && aimed.getEntity() instanceof LivingEntity living
                    && living != user && living != stand && living.isAlive()
                    && !(living instanceof HighwayStarSoleEntity)) {
                Vec3 toTarget = living.position().subtract(start);
                if (toTarget.length() <= RANGE && toTarget.normalize().dot(look) >= 0.3) {
                    triggerSeize(level, stand, user, living);
                    return;
                }
            }

            List<LivingEntity> targets = level.getEntitiesOfClass(LivingEntity.class,
                    stand.getBoundingBox().inflate(RANGE),
                    e -> e != user && e != stand && e.isAlive() && !(e instanceof HighwayStarSoleEntity));
            LivingEntity best = null;
            double bestDist = Double.MAX_VALUE;
            for (LivingEntity target : targets) {
                Vec3 toTarget = target.position().subtract(start);
                if (!(toTarget.length() > RANGE) && !(toTarget.normalize().dot(look) < 0.3)) {
                    double d = toTarget.lengthSqr();
                    if (d < bestDist) {
                        bestDist = d;
                        best = target;
                    }
                }
            }
            if (best != null) {
                triggerSeize(level, stand, user, best);
                return;
            }

            BlockPos center = stand.blockPosition();
            BlockPos bestBlock = null;
            double bestBlockDist = Double.MAX_VALUE;
            for (int x = -2; x <= 2; x++) {
                for (int y = -1; y <= 2; y++) {
                    for (int z = -2; z <= 2; z++) {
                        BlockPos pos = center.offset(x, y, z);
                        BlockState state = level.getBlockState(pos);
                        if (!state.isAir() && !(state.getDestroySpeed(level, pos) < 0.0F)) {
                            Vec3 toBlock = new Vec3(
                                    pos.getX() + 0.5 - stand.getX(),
                                    pos.getY() + 0.5 - stand.getY(),
                                    pos.getZ() + 0.5 - stand.getZ());
                            if (!(toBlock.length() > RANGE) && !(toBlock.normalize().dot(look) < 0.3)) {
                                double d = toBlock.lengthSqr();
                                if (d < bestBlockDist) {
                                    bestBlockDist = d;
                                    bestBlock = pos;
                                }
                            }
                        }
                    }
                }
            }
            if (bestBlock != null) {
                Direction face = Direction.getNearest(look.x, look.y, look.z);
                // 1.16 called HeavyPunchBlockInstance.doHit directly, which played no impact sound
                hitBlockAsHeavyPunch(new ActionTarget(bestBlock, face), level, stand, !isUserCreative(), null);
            }
        }

        private static void triggerSeize(Level level, HighwayStarEntity hs, LivingEntity user, LivingEntity target) {
            target.hurt(level.damageSources().mobAttack(user), 2.0F);
            target.invulnerableTime = 0;
            hs.setArmMissing(true);
            if (!(level instanceof ServerLevel serverLevel)) {
                return;
            }
            if (hs.isSplit()) {
                int i = 0;
                for (HighwayStarSoleEntity sole : HighwayStarEntity.ownedSoles(level, user, 128.0)) {
                    sole.attachTo(target.getUUID(), i++);
                }
                hs.setTrackingTarget(target.getUUID());
                return;
            }
            HighwayStarSoleEntity sole = HighwayStarAddon.SOLE_ENTITY.get().create(serverLevel);
            if (sole != null) {
                sole.moveTo(target.getX(), target.getY(), target.getZ(), 0.0F, 0.0F);
                sole.setOwnerUUID(user.getUUID());
                sole.setRemoteControlled(true);
                serverLevel.addFreshEntity(sole);
                sole.attachTo(target.getUUID(), 0);
                hs.setRemoteSoleUuid(sole.getUUID());
            }
        }
    }
}
