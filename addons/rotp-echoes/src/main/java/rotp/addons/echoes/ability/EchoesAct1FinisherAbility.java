package rotp.addons.echoes.ability;

import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.entityaction.ActionAnimIdentifier;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.entity.StandEntity;

import rotp.addons.echoes.state.EchoesSoundService;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/**
 * R772 F11: ACT1's finisher, the author's "Sound Give Punch" (RotP-EchoesACT-master InitStands 95-109,
 * EchoesACT1FinisherAction). It is the heavy punch's finisher variation (ACT1's moveset wires it with
 * initIsFinisher), a plain heavy punch that also gives the user's chosen sound to the living target it strikes, as
 * sound_give does, with no stamina or cooldown of its own.
 */
public final class EchoesAct1FinisherAbility extends StandEntityHeavyPunchAbility {
    // 1.16 setIsFinisher turned the variation's HEAVY_ATTACK pose into HEAVY_ATTACK_FINISHER, the author's finisherPunch
    private static final ActionAnimIdentifier FINISHER_CLIP = ActionAnimIdentifier.getOrCreate("finisher", false);

    public EchoesAct1FinisherAbility(AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId);
        this.createActionObj = SoundGivePunch::new;
    }

    @Override
    public ActionAnimIdentifier getEntityAnim(EntityActionInstance action) {
        return FINISHER_CLIP;
    }

    public static final class SoundGivePunch extends StandEntityHeavyPunch {
        public SoundGivePunch(EntityActionType ability) {
            super(ability);
        }

        // master: whether the blow landed or not, a living target takes the sound when the user has one chosen
        @Override
        protected void afterHeavyPunchHit(StandEntity stand, LivingEntity targetLiving, DamageSource dmgSource,
                float dmgAmount, boolean hurt) {
            super.afterHeavyPunchHit(stand, targetLiving, dmgSource, dmgAmount, hurt);
            if (getPowerUser() instanceof ServerPlayer player) {
                EchoesSoundService.give(player, targetLiving);
            }
        }
    }
}
