package rotp.addons.nutkingcall.ability;

import rotp.core.client.ClientProxy;
import rotp.core.client.input.ClientsideAim;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandUtil;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * The target a press of a Nut King Call action is aimed at: 1.16 took the user's crosshair target (the Stand's under
 * manual control), which is the summoned Stand's aim; with the Stand away the user's own pick.
 */
final class NutKingCallTargeting {
    private NutKingCallTargeting() {}

    static ActionTarget pressedTarget(LivingEntity user) {
        StandEntity stand = StandUtil.getSummonedStand(user);
        Level level = user.level();
        if (level.isClientSide()) {
            if (user != ClientProxy.getClientPlayer()) {
                return ActionTarget.EMPTY;
            }
            ActionTarget target = stand != null
                    ? ClientsideAim.standAim.getTarget()
                    : ClientsideAim.playerAim.getTarget();
            return target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
        }
        LivingEntity aiming = stand != null ? stand : user;
        var aim = LivingComponentAction.getAim(aiming);
        ActionTarget target = aim != null ? aim.getTarget() : null;
        return target != null ? target.resolveEntityId(level) : ActionTarget.EMPTY;
    }
}
