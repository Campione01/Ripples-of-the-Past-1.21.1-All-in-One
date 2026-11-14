package rotp.core.impl.stands.hierophant;

import java.util.List;

import javax.annotation.Nullable;

import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.powersystem.standpower.entity.StandEntityAbility;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

public class HierophantPuppetAbility extends StandEntityAbility {

	public HierophantPuppetAbility(AbilityType<?> abilityType, AbilityId abilityId) {
		super(abilityType, abilityId, PuppetingAction::new);
	}

	@Override
	public HeldInput onKeyPress(Level level, LivingEntity user, FriendlyByteBuf extraClientInput,
			InputMethod inputMethod, float clickHoldResolveTime, BufferingState bufferingState) {
		if (!level.isClientSide()) {
			StandPower power = PowerClass.STAND.get(user);
			if (power != null) {
				List<HierophantPuppetEffect> effects = power.userStandEffects
						.getEffectsOfType(ModStandAbilities.EFFECT_HG_PUPPET.get())
						.toList();
				if (!effects.isEmpty()) {
					effects.forEach(StandEffectInstance::remove);
					return null;
				}
			}
		}

		return super.onKeyPress(level, user, extraClientInput, inputMethod, clickHoldResolveTime, bufferingState);
	}

	/*
	 * While its user has a puppet, a press lets it go at once (onKeyPress) and never waits in the input buffer behind the
	 * Stand's task, so the slot is drawn by its requirements, as 1.16 drew a press its Stand's task did not queue.
	 */
	@Override
	public boolean isQueuedBehindStandTask(LivingEntity user) {
		return !hasPuppetUnderControl(StandPower.get(user)) && super.isQueuedBehindStandTask(user);
	}

	public static class PuppetingAction extends EntityActionInstance {
		public LivingEntity targetEntity;

		public PuppetingAction(EntityActionType ability) {
			super(ability);
		}

		@Override
		public void onActionSet(@Nullable EntityActionInstance prevAction) {
			keepStandAimedAtTarget();
			if (standRotationTarget != null && standRotationTarget.getEntity() instanceof LivingEntity targetLiving) {
				this.targetEntity = targetLiving;
			}
		}

		@Override
		public void actionPerformEnd() {
			Level level = level();
			if (!level.isClientSide()) {
				LivingEntity standUser = getPowerUser();
				StandPower power = StandPower.get(standUser);
				if (power != null) {
					power.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_HG_PUPPET.get())
							.forEach(StandEffectInstance::remove);

					if (targetEntity != null) {
						HierophantPuppetEffect newEffect = ModStandAbilities.EFFECT_HG_PUPPET.get().create(level);
						power.userStandEffects.addEffect(newEffect.withTarget(targetEntity));
					}
				}
			}
		}
	}

	public static boolean hasPuppetUnderControl(StandPower userPower) {
		return userPower != null && userPower.userStandEffects
				.getEffectOfType(ModStandAbilities.EFFECT_HG_PUPPET.get())
				.isPresent();
	}

	private static final ConditionCheck INSIDE_A_PUPPET = ConditionCheck.createNegative("hierophant_puppet");

	/*
	 * Inside its puppet Hierophant Green cannot fight outside, as in the manga, where it works a body from inside it (owner,
	 * 2026-11-13; 1.16 had no puppet, the official 1.21.1 port left every ability usable). Registered as a refusal so that
	 * every place the core checks conditions asks it: the HUD, the press on both sides, a queued press, a held tick. Only
	 * the puppet's own controls stay: this ability, which lets the puppet go, and the manual control key, which switches
	 * the puppet's control (HierophantPuppetEffect.onManualControlToggle) and which refusals never ask.
	 */
	@Nullable
	static ConditionCheck refuseInsideAPuppet(Ability ability, Power<?> context, LivingEntity user) {
		if (ability.abilityId.powerClass() != PowerClass.STAND || ability instanceof HierophantPuppetAbility) {
			return null;
		}
		return isInsideAPuppet(PowerClass.STAND.cast(context)) ? INSIDE_A_PUPPET : null;
	}

	// a puppet that was let go stays listed until its user's next effect tick
	private static boolean isInsideAPuppet(@Nullable StandPower userPower) {
		return userPower != null && userPower.userStandEffects
				.getEffectsOfType(ModStandAbilities.EFFECT_HG_PUPPET.get())
				.anyMatch(puppet -> !puppet.isStopped());
	}

	protected String releaseSpriteName;
	@Override
	protected void initVariationAssets() {
		this.releaseSpriteName = this.spriteName + "_release";
	}

	@Override
	public String getSpriteName(Power<?> context) {
		if (hasPuppetUnderControl(PowerClass.STAND.cast(context))) {
			return releaseSpriteName;
		}
		return super.getSpriteName(context);
	}

	@Override
	public Component getName(Power<?> context) {
		if (hasPuppetUnderControl(PowerClass.STAND.cast(context))) {
			return abilityName(context, ".release");
		}
		return super.getName(context);
	}
}
