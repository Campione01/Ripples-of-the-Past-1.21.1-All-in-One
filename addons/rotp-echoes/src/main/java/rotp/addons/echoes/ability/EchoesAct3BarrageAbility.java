package rotp.addons.echoes.ability;

import javax.annotation.Nullable;

import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.type.EntityActionType;

import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;

/**
 * ACT3 barrage: one voice per barrage, the cry set in EchoesAct3Stand (one of the author's two barrage voices, cut off
 * when the barrage leaves PERFORM), and no impact sound on hits. A second, uncut start voice used to play on top of
 * the cry; in the donor that cry pointed at a missing file, so only one voice was ever heard there.
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
    }
}
