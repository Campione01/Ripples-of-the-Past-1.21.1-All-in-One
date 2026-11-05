package rotp.addons.darkbluemoon.ability;

import javax.annotation.Nullable;

import rotp.addons.darkbluemoon.entity.MiniTornadoEntity;
import rotp.addons.darkbluemoon.util.DbmWater;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 DarkBlueMoonBarrage: the core barrage without hit sounds, its cry the light punch sound, the Stand turned the way
 * its user looks for the whole perform; in water it starts with a water sound and, once it has gone on 40 ticks, sends
 * one mini water tornado ahead (1.5 blocks out, moving 0.6 a tick along the look).
 */
public class DbmBarrageAbility extends StandEntityBarrageAbility {
    public static final int CHARGE_TIME = 40;

    public DbmBarrageAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, DbmBarrage::new);
    }

    @Override
    public void rotateStandTowardsTarget(StandEntity standEntity, EntityActionInstance action, ActionTarget target) {
        super.rotateStandTowardsTarget(standEntity, action, target);
        if (action.getPhase() == ActionPhase.PERFORM) {
            aimBarrage(standEntity);
        }
    }

    static void aimBarrage(StandEntity stand) {
        LivingEntity user = stand.getUser();
        if (user != null) {
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

    public static class DbmBarrage extends StandEntityBarrage {
        private boolean started;
        private boolean fired;

        public DbmBarrage(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionTick() {
            super.actionTick();
            Level level = level();
            if (level.isClientSide() || getPhase() != ActionPhase.PERFORM || !(getPerformer() instanceof StandEntity stand)) {
                return;
            }
            LivingEntity user = stand.getUser();
            if (user == null) {
                return;
            }
            boolean inWater = DbmWater.isInWater(user);
            if (!started) {
                started = true;
                if (inWater) {
                    stand.playSound(DbmWater.randomWaterUseSound(stand.getRandom()), 1.0F, 1.0F);
                }
            }
            if (!fired && getPhaseTick() >= CHARGE_TIME && inWater) {
                fired = true;
                Vec3 look = user.getLookAngle();
                MiniTornadoEntity tornado = new MiniTornadoEntity(level, user, stand.position().add(look.scale(1.5)));
                tornado.setDeltaMovement(look.scale(0.6));
                level.addFreshEntity(tornado);
            }
        }

        @Override
        public void applyStandUserRotation(StandEntity standEntity, LivingEntity user) {
            super.applyStandUserRotation(standEntity, user);
            if (getPhase() == ActionPhase.PERFORM) {
                aimBarrage(standEntity);
            }
        }

        @Override
        @Nullable
        protected Holder<SoundEvent> getBarrageHitSound() {
            return null;
        }
    }
}
