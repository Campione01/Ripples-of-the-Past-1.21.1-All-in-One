package rotp.addons.echoes.ability;

import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.EntityActionInstance;

import rotp.addons.echoes.init.EchoesSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/** The author's EchoesHeavyChargedAbility: ACT3 cries out as the charge starts, and the charge's own hit sound follows. */
public final class EchoesAct3HeavyChargedAbility extends StandEntityHeavyPunchChargedAbility {

    public EchoesAct3HeavyChargedAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
    }

    @Override
    public void initActionFromConfig(
            EntityActionInstance action, Level level, LivingEntity powerUser, LivingEntity performer) {
        super.initActionFromConfig(action, level, powerUser, performer);
        if (!level.isClientSide()) {
            level.playSound(null, performer.blockPosition(), EchoesSounds.HEAVY_ATTACK.get(), SoundSource.PLAYERS,
                    1.0F, 1.0F);
        }
    }
}
