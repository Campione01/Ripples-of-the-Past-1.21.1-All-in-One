package rotp.addons.kiss.ability;

import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.LivingEntity;

/**
 * 1.16 KissBarrageAction: the core melee barrage, its cry given by getSounds rather than a registered standSound.
 * StandEntityMeleeBarrage.playSoundAtStand repeated only the registered cry under Resolve, so Kiss's played one clip.
 */
public final class KissBarrageAbility extends StandEntityBarrageAbility {
    public KissBarrageAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, KissBarrage::new);
    }

    public static final class KissBarrage extends StandEntityBarrage {
        public KissBarrage(EntityActionType ability) {
            super(ability);
        }

        @Override
        protected boolean usesEndlessBarrageCry(StandEntity stand) {
            return false;
        }

        // 1.0.2a KissBarrageAction.aimBarrage: through the PERFORM the Stand takes its user's whole rotation every tick,
        // so it and the arms' head_x_rotation track point where the user looks. The action ticks after the Stand, so
        // this is the rotation each tick ends with; the hits are aimed before it by the core rules.
        @Override
        public void actionTick() {
            super.actionTick();
            if (getPhase() == ActionPhase.PERFORM
                    && getPerformer() instanceof StandEntity stand
                    && !stand.isManuallyControlled()
                    && stand.getUser() != null) {
                copyUserRotation(stand, stand.getUser());
            }
        }

        static void copyUserRotation(StandEntity stand, LivingEntity user) {
            stand.setYRot(user.getYRot());
            stand.yRotO = user.yRotO;
            stand.setXRot(user.getXRot());
            stand.xRotO = user.xRotO;
            stand.yHeadRot = user.yHeadRot;
            stand.yHeadRotO = user.yHeadRotO;
            stand.yBodyRot = user.yBodyRot;
            stand.yBodyRotO = user.yBodyRotO;
        }
    }
}
