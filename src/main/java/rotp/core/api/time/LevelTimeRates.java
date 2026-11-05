package rotp.core.api.time;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Owner-keyed per-level time rates, on both sides: how many times faster a level's time runs (Made in Heaven's
 * multiplier m while it accelerates). A provider answers a rate above 0 for a level and a {@link Channel}, or
 * {@link #NO_RATE} when it has nothing to say; when several answer, the slowest counts, the earliest registered on a
 * tie (owner rule G4). The core applies the tick channels on the server, up to {@link EntityTimeRates#MAX_RATE}; it
 * asks them once per level tick, and for scheduled ticks once per level tick and channel. Weather and the day time are
 * not among them. Without a provider every rate is 1 and nothing is asked.
 */
public final class LevelTimeRates {
	public static final float NO_RATE = Float.NaN;
	/** The most extra block entity ticks a level runs in one server tick above rate 1; the rest tick once. */
	public static final int MAX_EXTRA_BLOCK_ENTITY_TICKS = 1024;

	public enum Channel {
		/**
		 * How many times faster the level's own time runs, for add-ons that measure time in it (Mandom's rewind goes
		 * back 6 seconds of its user's own time but only 6 / rate seconds of the world's). The core does not apply it.
		 */
		TIME,
		/**
		 * Random ticks (crops, leaves, ice and snow, farmland, copper) and precipitation: the random tick speed each chunk
		 * gets this tick, the fraction carried.
		 */
		RANDOM_TICKS,
		/** Scheduled fluid ticks: the delay of each one scheduled is divided by the rate (at least one tick). */
		FLUID_TICKS,
		/**
		 * Scheduled block ticks (fire, redstone, falling and growing blocks that schedule their next step): the delay of
		 * each one scheduled is divided by the rate (at least one tick).
		 */
		BLOCK_TICKS,
		/**
		 * Ticking block entities (furnaces, hoppers, brewing stands, spawners): above 1 each ticks again in the same
		 * server tick, within {@link #MAX_EXTRA_BLOCK_ENTITY_TICKS}; below 1 it waits; the fraction carried.
		 */
		BLOCK_ENTITIES
	}

	@FunctionalInterface
	public interface Provider {
		float rate(Level level, Channel channel);
	}

	private static final Map<ResourceLocation, Provider> PROVIDERS = new LinkedHashMap<>();
	private static volatile ResourceLocation[] owners = new ResourceLocation[0];
	private static volatile Provider[] providers = new Provider[0];

	private LevelTimeRates() {}

	public static synchronized void register(ResourceLocation owner, Provider provider) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(provider, "provider");
		if (PROVIDERS.putIfAbsent(owner, provider) != null) {
			throw new IllegalStateException("Duplicate level time rate provider: " + owner);
		}
		owners = PROVIDERS.keySet().toArray(new ResourceLocation[0]);
		providers = PROVIDERS.values().toArray(new Provider[0]);
	}

	public static boolean hasProviders() {
		return providers.length > 0;
	}

	/** The slowest rate the providers answer for the level on this channel, 1 when none does. */
	public static float rate(Channel channel, Level level) {
		return resolve(channel, level, null);
	}

	/** The slowest rate any provider but this owner answers for the level on this channel, 1 when none does. */
	public static float rateExcluding(Channel channel, Level level, ResourceLocation owner) {
		return resolve(channel, level, owner);
	}

	/** The owner whose rate counts for the level on this channel (the slowest; the earliest on a tie), null if none answers. */
	@Nullable
	public static ResourceLocation governing(Channel channel, Level level) {
		Provider[] all = providers;
		ResourceLocation[] keys = owners;
		float slowest = Float.POSITIVE_INFINITY;
		ResourceLocation governing = null;
		for (int i = 0; i < all.length && i < keys.length; i++) {
			float rate = ask(all[i], keys[i], level, channel);
			if (rate < slowest) {
				slowest = rate;
				governing = keys[i];
			}
		}
		return governing;
	}

	private static float resolve(Channel channel, Level level, @Nullable ResourceLocation excluded) {
		Provider[] all = providers;
		if (all.length == 0) {
			return 1.0F;
		}
		ResourceLocation[] keys = owners;
		float slowest = Float.POSITIVE_INFINITY;
		for (int i = 0; i < all.length && i < keys.length; i++) {
			if (excluded == null || !excluded.equals(keys[i])) {
				slowest = Math.min(slowest, ask(all[i], keys[i], level, channel));
			}
		}
		return slowest == Float.POSITIVE_INFINITY ? 1.0F : slowest;
	}

	private static float ask(Provider provider, ResourceLocation owner, Level level, Channel channel) {
		try {
			float rate = provider.rate(level, channel);
			return Float.isFinite(rate) && rate > 0.0F ? rate : Float.POSITIVE_INFINITY;
		}
		catch (RuntimeException error) {
			JojoMod.getLogger().error("Level time rate provider {} failed.", owner, error);
			return Float.POSITIVE_INFINITY;
		}
	}
}
