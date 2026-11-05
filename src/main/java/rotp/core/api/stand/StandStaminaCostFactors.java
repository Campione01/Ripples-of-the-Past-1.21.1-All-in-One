package rotp.core.api.stand;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.resources.ResourceLocation;

/**
 * Owner-keyed factors on the stamina a Stand ability costs its user (Next Album 0.1.13's aging: non-melee skills cost
 * more for an aged user), as 0.1.13's StandActionStaminaAgingMixin scaled StandAction.getStaminaCost and
 * getStaminaCostTicking. The core multiplies what a use pays (StandAbilityStamina.consume: its single cost and each
 * tick of a ticking one) by every factor given, and its stamina checks by the factors given without an ability (the
 * ability is null there, as a check is asked with an amount only). A factor of 1, NaN or a negative value counts as
 * none. Without a provider every cost is as before.
 */
public final class StandStaminaCostFactors {
	@FunctionalInterface
	public interface Provider {
		/**
		 * @param ability the ability that pays, null for a stamina check that knows only the amount
		 * @param ticking whether the cost is one tick of a ticking cost
		 */
		float factor(@Nullable Ability ability, StandPower power, float baseCost, boolean ticking);
	}

	private static final Map<ResourceLocation, Provider> PROVIDERS = new LinkedHashMap<>();
	private static volatile Provider[] providers = new Provider[0];
	private static volatile ResourceLocation[] owners = new ResourceLocation[0];

	private StandStaminaCostFactors() {}

	public static synchronized void register(ResourceLocation owner, Provider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException("Duplicate Stand stamina cost factor: " + owner);
		}
		owners = PROVIDERS.keySet().toArray(new ResourceLocation[0]);
		providers = PROVIDERS.values().toArray(new Provider[0]);
	}

	/** The cost after every factor. */
	public static float apply(@Nullable Ability ability, StandPower power, float baseCost, boolean ticking) {
		Provider[] all = providers;
		if (all.length == 0 || baseCost <= 0.0F) {
			return baseCost;
		}
		ResourceLocation[] keys = owners;
		float cost = baseCost;
		for (int i = 0; i < all.length && i < keys.length; i++) {
			try {
				float factor = all[i].factor(ability, power, baseCost, ticking);
				if (Float.isFinite(factor) && factor >= 0.0F) {
					cost *= factor;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error("Stand stamina cost factor {} failed.", keys[i], error);
			}
		}
		return cost;
	}
}
