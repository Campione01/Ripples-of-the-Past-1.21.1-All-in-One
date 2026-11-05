package rotp.addons.darkbluemoon.ability;

import javax.annotation.Nullable;

import rotp.addons.darkbluemoon.DarkBlueMoonAddon;
import rotp.addons.darkbluemoon.DarkBlueMoonConfig;
import rotp.addons.darkbluemoon.util.BarnacleBinds;
import rotp.addons.darkbluemoon.util.BarnacleItemHelper;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * 1.16 DarkBlueMoonBarnacleAction ("whirlpool" pose, 20 ticks, 200 stamina, cooldown 40, pressed at an entity): on land
 * it takes 15-20 barnacles (a third with the necklace) and needs one held; it binds the first living entity in front of
 * the Stand within 3 blocks (Barnacle Bind, 200 ticks), which its Shift variation can cancel.
 */
public class DbmBarnacleAbility extends DbmStandAction {
    public DbmBarnacleAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, "whirlpool", 20, 200.0F, 40, TargetRequirement.ENTITY);
    }

    @Override
    protected ConditionCheck checkDbmConditions(LivingEntity user, @Nullable StandPower power) {
        return ConditionCheck.noMessage(BarnacleItemHelper.canUseBarnacleSkill(user));
    }

    @Override
    protected void standPerform(DbmAction action, StandEntity stand, LivingEntity user) {
        if (!BarnacleItemHelper.payLandCost(user)) {
            return;
        }
        Vec3 look = user.getLookAngle();
        Vec3 start = stand.position().add(0.0, stand.getBbHeight() * 0.5, 0.0);
        int duration = DarkBlueMoonConfig.getInt(DarkBlueMoonConfig.BARNACLE_BIND_DURATION);
        double radius = DarkBlueMoonConfig.getDouble(DarkBlueMoonConfig.BARNACLE_BIND_RADIUS);
        for (LivingEntity target : stand.level().getEntitiesOfClass(LivingEntity.class,
                stand.getBoundingBox().inflate(radius), e -> e != user && e != stand && e.isAlive())) {
            Vec3 toTarget = target.position().subtract(start);
            if (toTarget.length() <= radius && toTarget.normalize().dot(look) >= 0.5) {
                target.addEffect(new MobEffectInstance(DarkBlueMoonAddon.BARNACLE_BIND, duration, 0, false, false, false));
                BarnacleBinds.bind(user, target);
                break;
            }
        }
    }
}
