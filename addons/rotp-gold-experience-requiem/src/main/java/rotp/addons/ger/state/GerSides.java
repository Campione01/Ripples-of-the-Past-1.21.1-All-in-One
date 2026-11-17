package rotp.addons.ger.state;

import javax.annotation.Nullable;

import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.monster.Enemy;

/**
 * Who stands with a Requiem user and who against him, as the owner's rules of
 * 2026-11-08 count them: his own are his Stand, the animals he tamed and the
 * lifeforms he created; hostile is a monster, and any creature that is after
 * him or one of his own.
 */
public final class GerSides {
    private GerSides() {}

    public static boolean isOwn(
            @Nullable LivingEntity user, @Nullable Entity entity) {
        if (user == null || entity == null) {
            return false;
        }
        if (entity == user) {
            return true;
        }
        if (entity instanceof StandEntity stand) {
            return stand.getUser() == user;
        }
        if (entity instanceof OwnableEntity owned
                && user.getUUID().equals(owned.getOwnerUUID())) {
            return true;
        }
        if (entity instanceof LivingEntity living) {
            StandPower power = StandPower.get(user);
            return power != null
                    && power.hasPower()
                    && power.userStandEffects.getEffectTargeting(
                                    ModStandAbilities
                                            .EFFECT_GE_CREATED_LIFEFORM.get(),
                                    living)
                            .isPresent();
        }
        return false;
    }

    /** Never a player: other players are neither his side nor hostile creatures. */
    public static boolean isHostile(
            @Nullable LivingEntity user, @Nullable Entity entity) {
        if (!(entity instanceof Mob mob) || isOwn(user, mob)) {
            return false;
        }
        return mob instanceof Enemy
                || mob.getTarget() != null && isOwn(user, mob.getTarget());
    }
}
