package rotp.addons.darkbluemoon.ability;

import javax.annotation.Nullable;

import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

final class DbmTargets {
    private DbmTargets() {}

    /** 1.16 StandEntity.aimWithThisOrUser: the entity the user (or a manually controlled Stand) aims at in the Stand's reach. */
    @Nullable
    static Entity aimedEntity(StandEntity stand, LivingEntity user) {
        ActionTarget aimed = StandEntityPunchAbility.aimWithThisOrUser(stand, user, 1.0F,
                entity -> entity != stand && StandEntityPunchAbility.canStandHit(stand, entity),
                StandEntityPunchAbility.CrosshairRule.ENTITY, false);
        return aimed.getType() == ActionTarget.TargetType.ENTITY ? aimed.getMainEntity() : null;
    }
}
