package rotp.core.api.client.time;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

import rotp.core.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Owner-keyed per-entity client clock rates, for beings and things an add-on
 * holds back or speeds up on the server (Made in Heaven's acceleration), so
 * that their client-tick-driven animations keep pace with them.
 *
 * <p>The clock is the age in ticks a living renderer hands its model
 * (LivingEntityRenderer#getBob: idle sways, bobbing, AnimationState clips);
 * other renderers can read it through {@link #ageInTicks}. When several
 * providers answer for one entity the slowest rate wins: slowdowns and
 * speed-ups from different sources do not multiply. A rate change keeps the
 * clock continuous. Without a provider, or with no answer, an entity keeps
 * the vanilla clock (tickCount + partialTick). Frozen entities are the time
 * stop's (ClientTimeStopHandler#getConstantEntityPartialTick) and need no
 * rate. Render thread only.</p>
 */
@OnlyIn(Dist.CLIENT)
public final class ClientEntityClockRates {
	public static final float NO_RATE = Float.NaN;

	private static final Map<ResourceLocation, ClientEntityClockRateProvider>
			PROVIDERS = new LinkedHashMap<>();
	private static volatile List<Registration> snapshot = List.of();
	private static final Map<Entity, Clock> CLOCKS = new WeakHashMap<>();

	private ClientEntityClockRates() {}

	public static synchronized void register(
			ResourceLocation owner,
			ClientEntityClockRateProvider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException(
					"Duplicate client entity clock rate provider: " + owner);
		}
		List<Registration> registrations =
				new ArrayList<>(PROVIDERS.size());
		PROVIDERS.forEach((key, value) ->
				registrations.add(new Registration(key, value)));
		snapshot = List.copyOf(registrations);
	}

	/** The slowest rate the providers answer for the entity, 1 when none does. */
	public static float rate(Entity entity) {
		return resolve(entity, snapshot);
	}

	/**
	 * The entity's client clock in ticks: tickCount + partialTick as long as
	 * its rate is and was 1.
	 */
	public static float ageInTicks(Entity entity, float partialTick) {
		return onClock(entity, entity.tickCount + partialTick);
	}

	/**
	 * The entity's client clock at the moment its vanilla clock (tickCount +
	 * partialTick) reads vanillaTicks.
	 */
	public static float onClock(Entity entity, float vanillaTicks) {
		List<Registration> registrations = snapshot;
		if (registrations.isEmpty()) {
			return vanillaTicks;
		}
		float rate = resolve(entity, registrations);
		Clock clock = CLOCKS.get(entity);
		if (clock == null || clock.entity.get() != entity) {
			if (rate == 1.0F) {
				return vanillaTicks;
			}
			clock = new Clock(entity, vanillaTicks);
			CLOCKS.put(entity, clock);
		}
		return clock.at(vanillaTicks, rate);
	}

	private static float resolve(Entity entity, List<Registration> registrations) {
		float slowest = Float.POSITIVE_INFINITY;
		for (Registration registration : registrations) {
			try {
				float rate = registration.provider().rate(entity);
				if (Float.isFinite(rate) && rate > 0.0F) {
					slowest = Math.min(slowest, rate);
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error(
						"Client entity clock rate provider {} failed.",
						registration.owner(),
						error);
			}
		}
		return slowest == Float.POSITIVE_INFINITY ? 1.0F : slowest;
	}

	private static final class Clock {
		private final WeakReference<Entity> entity;
		private double base;
		private double since;
		private float rate = 1.0F;

		private Clock(Entity entity, double vanillaTicks) {
			this.entity = new WeakReference<>(entity);
			this.base = vanillaTicks;
			this.since = vanillaTicks;
		}

		private float at(double vanillaTicks, float newRate) {
			if (newRate != rate) {
				base += (vanillaTicks - since) * rate;
				since = vanillaTicks;
				rate = newRate;
			}
			return (float) (base + (vanillaTicks - since) * rate);
		}
	}

	private record Registration(
			ResourceLocation owner,
			ClientEntityClockRateProvider provider) {}
}
