package rotp.addons.darkbluemoon.ability;

import javax.annotation.Nullable;

import rotp.addons.darkbluemoon.entity.DarkBlueMoonEntity;
import rotp.addons.darkbluemoon.util.BarnacleItemHelper;
import rotp.addons.darkbluemoon.util.DbmWater;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.target.ActionTarget;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * 1.16 DarkBlueMoonBarnacleTrapAction ("whirlpool" pose, 10 ticks, 150 stamina, cooldown 40, pressed at a block): with a
 * trap already set it clears it; otherwise, on land for 15-20 held barnacles, it lays the trap on the block if the user
 * or the block is in water or water lies within 5 blocks of it (the barnacles are taken either way).
 */
public class DbmBarnacleTrapAbility extends DbmStandAction {
    public DbmBarnacleTrapAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, "whirlpool", 10, 150.0F, 40, TargetRequirement.BLOCK);
    }

    @Override
    protected ConditionCheck checkDbmConditions(LivingEntity user, @Nullable StandPower power) {
        return ConditionCheck.noMessage(BarnacleItemHelper.canUseBarnacleSkill(user));
    }

    @Override
    protected void standPerform(DbmAction action, StandEntity stand, LivingEntity user) {
        if (!(stand instanceof DarkBlueMoonEntity dbm)) {
            return;
        }
        if (dbm.hasBarnacleTrap()) {
            dbm.clearBarnacleTrap();
            return;
        }
        if (!BarnacleItemHelper.payLandCost(user)) {
            return;
        }
        ActionTarget target = action.getPressedTarget();
        if (target.getType() != ActionTarget.TargetType.BLOCK || target.getBlockPos() == null) {
            return;
        }
        Level level = stand.level();
        BlockPos blockPos = target.getBlockPos();
        boolean trapInWater = DbmWater.isInWater(user) || DbmWater.isWater(level.getBlockState(blockPos));
        if (trapInWater || DbmWater.hasWaterNearby(level, blockPos, 5)) {
            dbm.setBarnacleTrapPos(blockPos);
        }
    }
}
