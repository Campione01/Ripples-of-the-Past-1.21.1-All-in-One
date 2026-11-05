package rotp.addons.whitesnake.ability;

import java.util.Set;

import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance.StandPart;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.NoPoseStandEntityAbility;
import rotp.core.impl.stands._entitybase.StandAbilityStamina;
import rotp.core.util.functions.ItemUtil;

import rotp.addons.whitesnake.WhitesnakeAddon;
import rotp.addons.whitesnake.buffdisc.DiscItem;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * PATCH1 ExtractOwnBuff (staminaCost 30, resolveLevelToUnlock 1, arms): the user's first effect that is not a
 * negative one comes out as a Buff disc of that effect, level and remaining time; 40-tick cooldown.
 */
public final class WhitesnakeExtractOwnBuffAbility
        extends NoPoseStandEntityAbility {
    public static final float STAMINA_COST = 30.0F;
    public static final int COOLDOWN_TICKS = 40;
    private static final String ABILITY_NAME = "extract_own_buff";
    // PATCH1 isNegativeEffect, every harmful 1.16 effect; the 1.21 harmful category covers the later ones
    private static final Set<Holder<MobEffect>> NEGATIVE_EFFECTS = Set.of(
            MobEffects.BLINDNESS,
            MobEffects.CONFUSION,
            MobEffects.DIG_SLOWDOWN,
            MobEffects.HUNGER,
            MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.POISON,
            MobEffects.WEAKNESS,
            MobEffects.WITHER,
            MobEffects.UNLUCK,
            MobEffects.LEVITATION,
            MobEffects.BAD_OMEN,
            MobEffects.HARM);

    public WhitesnakeExtractOwnBuffAbility(
            AbilityType<?> abilityType, AbilityId abilityId) {
        super(abilityType, abilityId, ExtractOwnBuffAction::new);
        partsRequired(StandPart.ARMS);
        setDefaultPhaseLength(ActionPhase.PERFORM, 1);
        setDefaultPhaseLength(ActionPhase.RECOVERY, 0);
    }

    @Override
    public boolean shouldSetCooldownOnKeyPress(InputMethod inputMethod) {
        return false;
    }

    @Override
    public boolean canContinueAction(EntityActionInstance action) {
        return WhitesnakeAddon.isActiveWhitesnakeUser(
                action.getPowerUser(), ABILITY_NAME);
    }

    // PATCH1 checkStandConditions: refused silently while the user has no effect but negative ones
    @Override
    public ConditionCheck checkSpecificConditions(Power<?> context) {
        ConditionCheck check = super.checkSpecificConditions(context);
        if (!check.isPositive()) {
            return check;
        }
        LivingEntity user = context.getUser();
        return user != null && findBuff(user) != null
                ? ConditionCheck.POSITIVE
                : ConditionCheck.NEGATIVE;
    }

    static boolean isNegativeEffect(Holder<MobEffect> effect) {
        return NEGATIVE_EFFECTS.contains(effect)
                || effect.value().getCategory()
                        == MobEffectCategory.HARMFUL;
    }

    private static MobEffectInstance findBuff(LivingEntity user) {
        for (MobEffectInstance effect : user.getActiveEffects()) {
            if (!isNegativeEffect(effect.getEffect())) {
                return effect;
            }
        }
        return null;
    }

    public static final class ExtractOwnBuffAction
            extends EntityActionInstance {
        public ExtractOwnBuffAction(EntityActionType ability) {
            super(ability);
        }

        @Override
        public void actionPerformStart() {
            if (level().isClientSide()) {
                return;
            }
            LivingEntity user = getPowerUser();
            StandPower power = user != null ? StandPower.get(user) : null;
            if (user instanceof Player player && power != null) {
                extract(player, power);
            }
            // 1.16 StandEntityTask paid the cost after the perform
            if (power != null) {
                StandAbilityStamina.consume(
                        ability, power, STAMINA_COST, false);
            }
        }

        private void extract(Player player, StandPower power) {
            MobEffectInstance buff = findBuff(player);
            if (buff == null) {
                player.displayClientMessage(Component.translatable(
                                "message.rotp_whitesnake.no_buff_to_extract"),
                        true);
                return;
            }
            Holder<MobEffect> effect = buff.getEffect();
            int amplifier = buff.getAmplifier();
            int duration = buff.getDuration();
            player.removeEffect(effect);
            ItemUtil.giveItemTo(
                    player,
                    DiscItem.createEffect(effect, amplifier, duration),
                    true);
            player.displayClientMessage(Component.translatable(
                            "message.rotp_whitesnake.extracted_buff",
                            Component.translatable(
                                    effect.value().getDescriptionId())),
                    true);
            power.setAbilityCooldown(ABILITY_NAME, COOLDOWN_TICKS);
        }
    }
}
