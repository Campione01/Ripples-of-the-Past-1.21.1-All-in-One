package rotp.core.api.time;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Owner-keyed per-entity time rates, for add-ons that slow an entity's time down or speed it up (Made in Heaven's
 * acceleration, Neo Stand's bullet time), on both sides. A provider answers a rate above 0 for an entity and a
 * {@link Channel} (1 normal, below 1 slower, above 1 faster) or {@link #NO_RATE} (any value that is not a finite
 * positive number) when it has nothing to say. When several providers answer, the slowest rate counts, the earliest
 * registered on a tie: slowdowns and speed-ups from different sources do not multiply (owner rule G4). The core applies
 * the rate of {@link Channel#STAND_ACTIONS}, {@link Channel#MOTION} and {@link Channel#ENTITY_TICK} itself, up to
 * {@link #MAX_RATE}; {@link Channel#BEING} is for add-ons to settle among themselves which of their slowdowns applies
 * ({@link #rateExcluding}, {@link #governs}). Without a provider every rate is 1 and the core keeps no state.
 * A provider that applies a slowdown of its own by other means (skipped ticks, attributes) answers only for the
 * channels it does not already cover, or the entity is slowed twice.
 */
public final class EntityTimeRates {
	public static final float NO_RATE = Float.NaN;
	/** The fastest rate the core applies: a step count per tick above it is capped there. */
	public static final float MAX_RATE = 64.0F;
	/** The most extra entity ticks ({@link Channel#ENTITY_TICK}) a level runs in one server tick; the rest tick once. */
	public static final int MAX_EXTRA_ENTITY_TICKS = 2048;

	public enum Channel {
		/**
		 * The rate an add-on holds an entity's own time to, by whatever means it uses: the core does not apply it. Each
		 * add-on that slows or speeds up beings answers it, and asks {@link #governs} or {@link #rateExcluding} before it
		 * applies its own means, so that only the slowest source acts.
		 */
		BEING,
		/**
		 * A user's actions and power cooldowns: the actions its Stand performs and those it performs itself (for its
		 * Stand power and for its non-Stand power: Hamon, vampirism, the Pillar Man's...), its Stand power's ability and
		 * leap cooldowns, and its non-Stand power's leap cooldown and the ability cooldowns of a power data that counts
		 * them by PlayerPower#getCooldownSteps (the core's Hamon, vampirism and Pillar Man): they advance by this rate,
		 * several steps a tick above 1, with the fraction carried. Asked for the user; a Stand counts as its user.
		 */
		STAND_ACTIONS,
		/**
		 * Motion of an entity that no attribute scales: ladder and vine climbing, levitation, bubble columns, the
		 * firework rocket's elytra boost, Jump Boost's bonus to a jump and the threshold under which LivingEntity.aiStep
		 * stops a slow velocity. Scaled as a provider's attributes scale walking (speeds, pushes, caps and that threshold
		 * times r, every per-tick drag unchanged; the provider slows gravity itself, Made in Heaven by r squared): a climb
		 * rises r times as far a tick under the gravity the entity has, and a jump with Jump Boost reaches the height it
		 * reaches at the entity's base gravity.
		 */
		MOTION,
		/**
		 * The server tick of a non-living entity that carries no passenger (never a living one, so never a player or a
		 * Stand): above 1 it ticks again in the same server tick, within {@link #MAX_EXTRA_ENTITY_TICKS} a level and
		 * not while stopped time freezes it; below 1 it waits; the fraction carried. Asked for every such entity each
		 * tick while any provider is registered, so a provider answers it cheaply.
		 */
		ENTITY_TICK
	}

	@FunctionalInterface
	public interface Provider {
		float rate(Entity entity, Channel channel);
	}

	private static final Map<ResourceLocation, Provider> PROVIDERS = new LinkedHashMap<>();
	private static volatile ResourceLocation[] owners = new ResourceLocation[0];
	private static volatile Provider[] providers = new Provider[0];

	private EntityTimeRates() {}

	public static synchronized void register(ResourceLocation owner, Provider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException("Duplicate entity time rate provider: " + owner);
		}
		owners = PROVIDERS.keySet().toArray(new ResourceLocation[0]);
		providers = PROVIDERS.values().toArray(new Provider[0]);
	}

	/** Whether any provider is registered; every rate is 1 while none is. */
	public static boolean hasProviders() {
		return providers.length > 0;
	}

	/** The slowest rate the providers answer for the entity on this channel, 1 when none does. */
	public static float rate(Channel channel, Entity entity) {
		Provider[] all = providers;
		return all.length == 0 ? 1.0F : resolve(channel, entity, null, all, owners);
	}

	/**
	 * How slow (or fast) the other providers make the entity on this channel: the slowest rate any provider but this
	 * owner answers, 1 when none does.
	 */
	public static float rateExcluding(Channel channel, Entity entity, ResourceLocation owner) {
		Provider[] all = providers;
		return all.length == 0 ? 1.0F : resolve(channel, entity, owner, all, owners);
	}

	/** The owner whose rate counts for the entity on this channel (the slowest; the earliest on a tie), null if none answers. */
	@Nullable
	public static ResourceLocation governing(Channel channel, Entity entity) {
		Provider[] all = providers;
		if (all.length == 0) {
			return null;
		}
		ResourceLocation[] keys = owners;
		float slowest = Float.POSITIVE_INFINITY;
		ResourceLocation governing = null;
		for (int i = 0; i < all.length && i < keys.length; i++) {
			float rate = ask(all[i], keys[i], entity, channel);
			if (rate < slowest) {
				slowest = rate;
				governing = keys[i];
			}
		}
		return governing;
	}

	/** Whether this owner's answer is the one that counts for the entity on this channel. */
	public static boolean governs(Channel channel, Entity entity, ResourceLocation owner) {
		return owner.equals(governing(channel, entity));
	}

	private static float resolve(Channel channel, Entity entity, @Nullable ResourceLocation excluded,
			Provider[] all, ResourceLocation[] keys) {
		float slowest = Float.POSITIVE_INFINITY;
		for (int i = 0; i < all.length && i < keys.length; i++) {
			if (excluded != null && excluded.equals(keys[i])) {
				continue;
			}
			slowest = Math.min(slowest, ask(all[i], keys[i], entity, channel));
		}
		return slowest == Float.POSITIVE_INFINITY ? 1.0F : slowest;
	}

	// POSITIVE_INFINITY for no answer
	private static float ask(Provider provider, ResourceLocation owner, Entity entity, Channel channel) {
		try {
			float rate = provider.rate(entity, channel);
			return Float.isFinite(rate) && rate > 0.0F ? rate : Float.POSITIVE_INFINITY;
		}
		catch (RuntimeException error) {
			JojoMod.getLogger().error("Entity time rate provider {} failed.", owner, error);
			return Float.POSITIVE_INFINITY;
		}
	}
}
