package rotp.addons.echoes.ability;

import javax.annotation.Nullable;

import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

/**
 * ACT3 barrage: one voice at a time, the cry set in EchoesAct3Stand (one of the author's two barrage voices), and no
 * impact sound on hits. The voices last about a second and a half and the barrage up to 3.75 seconds, so it repeats
 * clip after clip until the barrage leaves PERFORM, as the author's EchoesClientSoundEvents loops his. A second, uncut
 * start voice used to play on top of the cry; in the donor that cry pointed at a missing file, so only one voice was
 * ever heard there.
 */
public final class EchoesAct3BarrageAbility extends StandEntityBarrageAbility {

    public EchoesAct3BarrageAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, Act3Barrage::new);
    }

    public static final class Act3Barrage extends StandEntityBarrage {

        public Act3Barrage(EntityActionType ability) {
            super(ability);
        }

        // 092 barrageHitSound(null): no impact sound on hits
        @Nullable
        @Override
        protected Holder<SoundEvent> getBarrageHitSound() {
            return null;
        }

        @Override
        protected boolean usesEndlessBarrageCry(StandEntity stand) {
            return true;
        }
    }
}
