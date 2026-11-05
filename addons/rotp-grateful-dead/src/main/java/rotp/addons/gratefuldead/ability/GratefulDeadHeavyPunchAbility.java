package rotp.addons.gratefuldead.ability;

import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Next Album 0.1.13 THE_GRATEFUL_DEAD_HEAVY_PUNCH and THE_GRATEFUL_DEAD_FINISHER: the core heavy punch with
 * Prosciutto's shout from the user on the press, PROSCIUTTO_HEAVY_SHOUT on the heavy punch (also the one thrown at a
 * held entity, 0.1.13's same action) and PROSCIUTTO_AGING_PUNCH on the finisher. Both shouted while sneaking in 1.16
 * (a shift variation, and a finisher).
 */
public final class GratefulDeadHeavyPunchAbility
        extends StandEntityHeavyPunchAbility {

    public GratefulDeadHeavyPunchAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        setPlaysVoiceLineOnSneak();
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity standUser,
            LivingEntity standEntity) {
        super.initActionFromConfig(
                action, level, standUser, standEntity);
        if (!level.isClientSide() && standUser != null) {
            sayShout(standUser, isStandFinisherOf != null
                    ? GratefulDeadAddon.randomAgingPunchShout()
                    : GratefulDeadAddon.randomHeavyShout());
        }
    }
}
