package rotp.addons.echoes.ability;

import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

import rotp.addons.echoes.state.EchoesGravityService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * R772 F11: the ACT2 and ACT3 finishers of the author's latest Echoes (RotP-EchoesACT-master InitStands 173-187 and
 * 274-291, EchoesAct2FinisherAction, FinisherAction), the finisher variations of their heavy punches (each moveset wires
 * its finisher with initIsFinisher). At the strike the living target in the user's aim, within 8 blocks of the Stand,
 * takes six hits of 3. ACT2's finisher strikes as its heavy first; ACT3's strikes nothing itself and then freezes the
 * target, as the freeze does.
 */
public final class EchoesFinisherAbility extends StandEntityHeavyPunchAbility {
    public enum Mode {
        ACT2,
        ACT3
    }

    private static final double RANGE = 8.0D;
    private static final int HITS = 6;
    private static final float DAMAGE_PER_HIT = 3.0F;
    // 1.16 HEAVY_ATTACK_FINISHER, the pose the author's finisherPunch clips play
    private static final ActionAnimIdentifier FINISHER_CLIP = ActionAnimIdentifier.getOrCreate("finisher", false);

    public EchoesFinisherAbility(AbilityType<?> type, AbilityId id, Mode mode) {
        super(type, id);
        this.createActionObj = action -> new FinisherPunch(action, mode);
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return FINISHER_CLIP;
    }

    public static final class FinisherPunch extends StandEntityHeavyPunch {
        private final Mode mode;

        public FinisherPunch(EntityActionType ability, Mode mode) {
            super(ability);
            this.mode = mode;
        }

        // master FinisherAction.standPerform does not call the heavy's own strike
        @Override
        protected ActionTarget getPunchTarget(StandEntity stand) {
            return mode == Mode.ACT3 ? ActionTarget.EMPTY : super.getPunchTarget(stand);
        }

        @Override
        public void actionPerformStart() {
            super.actionPerformStart();
            if (level().isClientSide() || !(performer instanceof StandEntity stand)
                    || !(getPowerUser() instanceof ServerPlayer player)) {
                return;
            }
            ActionTarget aimed = HitResultUtil.clip(
                    player.getEyePosition(),
                    player.getLookAngle(),
                    RANGE,
                    RANGE,
                    player.level(),
                    entity -> entity instanceof LivingEntity
                            && entity.isAlive()
                            && entity != player
                            && entity != stand,
                    player,
                    0.0D);
            if (!(aimed.getMainEntity() instanceof LivingEntity target) || stand.distanceTo(target) > RANGE) {
                return;
            }
            // master FinisherAction: switchSkill(FREEZE) ends self-freeze or gravity infuse
            if (mode == Mode.ACT3) {
                EchoesGravityService.onAct3FinisherHit(player);
            }
            for (int hit = 0; hit < HITS && target.isAlive(); hit++) {
                target.hurt(player.damageSources().playerAttack(player), DAMAGE_PER_HIT);
            }
            // master GravityFreezeAction.startFreeze
            if (mode == Mode.ACT3) {
                EchoesGravityService.freezeFinisherTarget(player, target);
            }
        }
    }
}
