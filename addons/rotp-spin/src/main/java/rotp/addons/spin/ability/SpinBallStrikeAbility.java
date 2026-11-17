package rotp.addons.spin.ability;

import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import rotp.addons.spin.config.SpinConfig;
import rotp.addons.spin.item.SteelBallItem;
import rotp.addons.spin.power.SpinStrike;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

public final class SpinBallStrikeAbility extends SpinActionAbility {
    public SpinBallStrikeAbility(AbilityType<?> type, AbilityId id) {
        super(type, id, 1, StrikeAction::new);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive() || !(context.getUser() instanceof Player player) || player.isSpectator()) {
            return check.isPositive() ? ConditionCheck.NEGATIVE : check;
        }
        if (SteelBallItem.handWithBall(player, false) == null) {
            return ConditionCheck.createNegative("rotp_spin.no_steel_ball_in_hand");
        }
        if (!hasEnergy(player, SpinConfig.STRIKE_ENERGY_COST.get())) {
            return ConditionCheck.createNegative("no_energy_spin");
        }
        return bodyOf(player, LivingComponentAction.getAimTarget(player)) != null
                ? ConditionCheck.POSITIVE : ConditionCheck.NEGATIVE;
    }

    @Nullable
    private static LivingEntity bodyOf(Player player, ActionTarget target) {
        if (target == null || target.getType() != ActionTarget.TargetType.ENTITY
                || !HitResultUtil.isTargetWithinRange(target, player, player.level(), 0, SpinConfig.STRIKE_REACH.get())) {
            return null;
        }
        Entity selected = target.getMainEntity();
        LivingEntity body = selected instanceof StandEntity stand ? stand.getUser()
                : selected instanceof LivingEntity living ? living : null;
        return body != null && body.level() == player.level() && body.isAlive() && !body.is(player) && !body.isSpectator()
                && (!(body instanceof Player victim) || player.canHarmPlayer(victim))
                && (player.level().isClientSide() || player.hasLineOfSight(selected)) ? body : null;
    }

    public static final class StrikeAction extends EntityActionInstance {
        public StrikeAction(EntityActionType type) {
            super(type);
        }

        @Override
        public void onActionSet(EntityActionInstance previousAction) {
            if (getPowerUser() instanceof Player player) {
                captureActionTargetFromAim(player);
            }
        }

        @Override
        public void actionPerformStart() {
            if (!(level() instanceof ServerLevel server) || !(getPowerUser() instanceof Player player)
                    || !(ability instanceof SpinBallStrikeAbility strike)) {
                return;
            }
            LivingEntity body = bodyOf(player, getActionTargetSnapshot(server));
            InteractionHand hand = SteelBallItem.handWithBall(player, false);
            if (body == null || hand == null || !spend(player, SpinConfig.STRIKE_ENERGY_COST.get())) {
                return;
            }
            strike.beginCooldown(player, SpinConfig.STRIKE_COOLDOWN_TICKS.get());
            boolean chipped = SteelBallItem.isChipped(player.getItemInHand(hand));
            double penalty = SpinConfig.STRIKE_CHIPPED_MULTIPLIER.get();
            float damage = (float) SpinStrike.scaled(SpinConfig.STRIKE_DAMAGE.get(), chipped, penalty);
            if (damage > 0) {
                body.hurt(server.damageSources().playerAttack(player), damage);
            }
            double knockback = SpinStrike.scaled(SpinConfig.STRIKE_KNOCKBACK.get(), chipped, penalty);
            if (knockback > 0) {
                body.knockback(knockback, player.getX() - body.getX(), player.getZ() - body.getZ());
                double lift = SpinStrike.scaled(SpinConfig.STRIKE_LIFT.get(), chipped, penalty);
                Vec3 motion = body.getDeltaMovement();
                body.setDeltaMovement(motion.x, SpinStrike.liftedY(motion.y, lift), motion.z);
                body.hasImpulse = true;
                body.hurtMarked = true;
            }
            player.swing(hand, true);
            server.sendParticles(ParticleTypes.CRIT, body.getX(), body.getY(0.5), body.getZ(),
                    14, body.getBbWidth() * 0.4, body.getBbHeight() * 0.3, body.getBbWidth() * 0.4, 0.3);
            server.sendParticles(ParticleTypes.ENCHANTED_HIT, body.getX(), body.getY(0.5), body.getZ(),
                    8, body.getBbWidth() * 0.3, body.getBbHeight() * 0.3, body.getBbWidth() * 0.3, 0.1);
            server.playSound(null, body, SoundEvents.PLAYER_ATTACK_KNOCKBACK, SoundSource.PLAYERS, 1, chipped ? 0.8F : 1.1F);
        }
    }
}
