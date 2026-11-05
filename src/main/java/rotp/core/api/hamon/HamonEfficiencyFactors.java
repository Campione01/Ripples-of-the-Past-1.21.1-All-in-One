package rotp.core.api.hamon;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import rotp.core.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;

/**
 * Owner-keyed factors on a Hamon user's bloodstream efficiency (Next Album 0.1.13's aging: an aged user breathes and
 * channels Hamon less well), as 0.1.13's HamonDataAgingMixin scaled HamonData.getBloodstreamEfficiency. The core
 * multiplies HamonData#getBloodstreamEfficiency(user), which its breathing (energy regeneration) and its Hamon
 * abilities use, by every factor given: at 0 the user neither breathes Hamon nor uses it, as a frozen bloodstream.
 * A NaN or negative factor counts as none. Without a provider the efficiency is as before.
 */
public final class HamonEfficiencyFactors {
	@FunctionalInterface
	public interface Provider {
		float factor(LivingEntity user);
	}

	private static final Map<ResourceLocation, Provider> PROVIDERS = new LinkedHashMap<>();
	private static volatile Provider[] providers = new Provider[0];
	private static volatile ResourceLocation[] owners = new ResourceLocation[0];

	private HamonEfficiencyFactors() {}

	public static synchronized void register(ResourceLocation owner, Provider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException("Duplicate Hamon efficiency factor: " + owner);
		}
		owners = PROVIDERS.keySet().toArray(new ResourceLocation[0]);
		providers = PROVIDERS.values().toArray(new Provider[0]);
	}

	/** The efficiency after every factor. */
	public static float apply(LivingEntity user, float efficiency) {
		Provider[] all = providers;
		if (all.length == 0) {
			return efficiency;
		}
		ResourceLocation[] keys = owners;
		for (int i = 0; i < all.length && i < keys.length; i++) {
			try {
				float factor = all[i].factor(user);
				if (Float.isFinite(factor) && factor >= 0.0F) {
					efficiency *= factor;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error("Hamon efficiency factor {} failed.", keys[i], error);
			}
		}
		return efficiency;
	}
}
