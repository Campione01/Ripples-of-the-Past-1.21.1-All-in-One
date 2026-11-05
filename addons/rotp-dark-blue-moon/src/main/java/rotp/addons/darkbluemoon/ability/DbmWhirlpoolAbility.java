package rotp.addons.darkbluemoon.ability;

import javax.annotation.Nullable;

import rotp.addons.darkbluemoon.DarkBlueMoonConfig;
import rotp.addons.darkbluemoon.entity.DarkBlueMoonEntity;
import rotp.addons.darkbluemoon.entity.WhirlpoolEntity;
import rotp.addons.darkbluemoon.util.DbmWater;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.16 DarkBlueMoonWhirlpoolAction ("Stir" pose, 200 ticks, 500 stamina, cooldown 600): only in water and with at least
 * 200 water blocks within 10 ("Not enough space around" otherwise), a whirlpool forms at the user and the Stand stays
 * where it is for 200 ticks.
 */
public class DbmWhirlpoolAbility extends DbmStandAction {
    public DbmWhirlpoolAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, "Stir", 200, 500.0F, 600, TargetRequirement.NONE);
    }

    @Override
    protected ConditionCheck checkDbmConditions(LivingEntity user, @Nullable StandPower power) {
        if (!DbmWater.isInWater(user)) {
            return ConditionCheck.NEGATIVE;
        }
        int radius = DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.WHIRLPOOL_WATER_CHECK_RADIUS);
        int required = DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.WHIRLPOOL_REQUIRED_WATER);
        if (DbmWater.countWater(user.level(), user.blockPosition(), radius, required) < required) {
            return ConditionCheck.createNegative(
                    Component.translatable("message.darkbluemoon.not_enough_space").withStyle(ChatFormatting.GRAY));
        }
        return ConditionCheck.POSITIVE;
    }

    @Override
    protected void standPerform(DbmAction action, StandEntity stand, LivingEntity user) {
        stand.level().addFreshEntity(new WhirlpoolEntity(stand.level(), user, user.position()));
        if (stand instanceof DarkBlueMoonEntity dbm) {
            dbm.startStay();
        }
    }
}
