package rotp.addons.aquanecklace.ability;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;
import rotp.core.util.functions.DamageUtil;

import javax.annotation.Nullable;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;

final class AquaNecklaceTargeting {
    private AquaNecklaceTargeting() {}

    // 1.16 took the user's crosshair target (the Stand's under manual
    // control); the Stand's aim is that target. The server has it from the
    // user's client, which reads the aim it sends.
    static ActionTarget standAim(StandEntity stand, Level level) {
        if (level.isClientSide()) {
            return stand.getUser() != null
                            && stand.getUser() == ClientProxy.getClientPlayer()
                    ? ClientsideAim.standAim.getTarget()
                            .resolveEntityId(level)
                    : ActionTarget.EMPTY;
        }
        return aimOf(stand, level);
    }

    // with the power inactive, EntityStandType.clientHitResult sent the
    // vanilla pick alone
    static ActionTarget userPick(LivingEntity user, Level level) {
        if (level.isClientSide()) {
            return user == ClientProxy.getClientPlayer()
                    ? ClientsideAim.playerAim.getTarget()
                            .resolveEntityId(level)
                    : ActionTarget.EMPTY;
        }
        return aimOf(user, level);
    }

    // Extra Stands 1.1.0d getHoldDurationToFire: JojoModUtil.rayTrace from the
    // user's eyes, or the Stand's under manual control, as far as the user's
    // reach; an entity on it counts through blocks, as 1.16's entity rays did
    @Nullable
    static Entity reachRayEntity(LivingEntity user, @Nullable StandEntity stand) {
        LivingEntity aiming = stand != null && stand.isManuallyControlled()
                ? stand
                : user;
        double reach = user.getAttributes()
                        .hasAttribute(Attributes.BLOCK_INTERACTION_RANGE)
                ? user.getAttributeValue(Attributes.BLOCK_INTERACTION_RANGE)
                : Attributes.BLOCK_INTERACTION_RANGE.value().getDefaultValue();
        ActionTarget hit = HitResultUtil.clip(
                aiming.getEyePosition(),
                aiming.getLookAngle(),
                reach,
                reach,
                aiming.level(),
                entity -> !entity.isSpectator()
                        && entity.isPickable()
                        && !entity.is(user),
                aiming,
                0.0D,
                false,
                0.0D,
                ClipContext.Block.OUTLINE);
        return hit.getType() == ActionTarget.TargetType.ENTITY
                ? hit.getMainEntity()
                : null;
    }

    // StandEntity.canAttack read of the user, as a Stand summoned for the
    // press read it: no friendly fire, not an animal sharing the user's
    // vehicle, not the user's own tamed animal
    static boolean userMayAttack(LivingEntity user, LivingEntity target) {
        if (!DamageUtil.isNotFriendlyFire(
                user, StandUtil.getStandUser(target))) {
            return false;
        }
        return !(target instanceof Animal)
                || !target.isPassengerOfSameVehicle(user)
                        && !(target instanceof TamableAnimal tamed
                                && tamed.getOwner() == user);
    }

    private static ActionTarget aimOf(LivingEntity aiming, Level level) {
        var aim = LivingComponentAction.getAim(aiming);
        ActionTarget target = aim != null ? aim.getTarget() : null;
        return target != null
                ? target.resolveEntityId(level)
                : ActionTarget.EMPTY;
    }
}
