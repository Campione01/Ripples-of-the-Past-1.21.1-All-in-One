package rotp.addons.beachboy.ability;

import rotp.addons.beachboy.util.BeachBoyCombatUtil;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.subsystems.target.ActionTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 BeachBoyTrapAction: plants the hook as a trap in the looked-at block, item or creature within 50 blocks of the
 * look source (the remote hook's view under remote control); 50 stamina. The target is the nearer of the block or
 * creature hit and an item on the look line, as 1.16's overrideVanillaMouseTarget and targetBeforePerform chose it.
 */
public class BeachBoyTrapAbility extends BeachBoyAbility {
    public BeachBoyTrapAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, 50.0F);
    }

    @Override
    protected ConditionCheck checkBeachBoyConditions(LivingEntity user, StandPower power) {
        ConditionCheck rod = needsRod(user);
        if (!rod.isPositive()) {
            return rod;
        }
        return checkTarget(BeachBoyCombatUtil.resolveTrapTarget(user), user);
    }

    private static ConditionCheck checkTarget(ActionTarget target, LivingEntity user) {
        if (target == null || target.getType() == ActionTarget.TargetType.EMPTY) {
            return ConditionCheck.createNegative("beach_boy_need_trap_target");
        }
        if (target.getType() == ActionTarget.TargetType.ENTITY) {
            Entity entity = target.getEntity();
            boolean item = entity instanceof ItemEntity itemEntity && itemEntity.isAlive()
                    && !itemEntity.getItem().isEmpty();
            if (!item && (!(entity instanceof LivingEntity) || !entity.isAlive())) {
                return ConditionCheck.createNegative("beach_boy_need_trap_target");
            }
            return ConditionCheck.noMessage(BeachBoyCombatUtil.isWithinTrapRange(user, entity.position()));
        }
        if (target.getType() == ActionTarget.TargetType.BLOCK) {
            BlockPos pos = target.getBlockPos();
            if (pos == null || user.level().getBlockState(pos).isAir()) {
                return ConditionCheck.createNegative("beach_boy_need_trap_target");
            }
            return ConditionCheck.noMessage(BeachBoyCombatUtil.isWithinTrapRange(user, Vec3.atCenterOf(pos)));
        }
        return ConditionCheck.createNegative("beach_boy_need_trap_target");
    }

    @Override
    protected void perform(Level level, LivingEntity user, StandPower power) {
        BeachBoyCombatUtil.performTrap(level, user, BeachBoyCombatUtil.resolveTrapTarget(user));
    }
}
