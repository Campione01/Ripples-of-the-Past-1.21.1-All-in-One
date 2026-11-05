package rotp.addons.gratefuldead.ability;

import rotp.core.impl.stands._entitybase.StandEntityGrabThrowAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import rotp.addons.gratefuldead.GratefulDeadAddon;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * The core grab throw with Next Album 0.1.13 THE_GRATEFUL_DEAD_THROW's shout, PROSCIUTTO_HEAVY_SHOUT, on the press
 * (a shift variation of the grab in 1.16, so it shouted while sneaking too). The throw itself stays the core's.
 */
public final class GratefulDeadGrabThrowAbility
        extends StandEntityGrabThrowAbility {

    public GratefulDeadGrabThrowAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        setPlaysVoiceLineOnSneak();
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action,
            Level level,
            LivingEntity powerUser,
            LivingEntity performer) {
        super.initActionFromConfig(
                action, level, powerUser, performer);
        if (!level.isClientSide() && powerUser != null) {
            sayShout(powerUser, GratefulDeadAddon.randomHeavyShout());
        }
    }
}
