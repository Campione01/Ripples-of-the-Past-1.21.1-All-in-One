package rotp.core.api.simulation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.core.EventHandler;
import rotp.core.core.JojoMod;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.powersystem.standpower.entity.NoKnockbackOnBlocking;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Owner-keyed rules that an entity is a simulation: a copy an add-on keeps outside the world and ticks itself to see
 * what would happen (King Crimson's Epitaph). Such a copy is never added to a level and may share its int id with the
 * real entity it stands for, so the core's own state that is keyed by entity id or spans a level does not take it in:
 * <ul>
 * <li>stopped time (TimeStopState) neither freezes nor releases it, keeps no frozen place, queued resume work or dirty
 * data for it (its id would reach the real entity's), and skips it in the stop's effect refresh;</li>
 * <li>the core's time stop ability starts no stop for it, before anything is charged (a stop is keyed by its user's
 * id); an add-on that starts stops itself through TimeStopState asks {@link #isSimulated} first;</li>
 * <li>the level's records do not take it: a charged Hamon egg, a chicken hatched from one, Gold Experience's growth
 * sparkles, block destroy progress it deals.</li>
 * </ul>
 * Reads stay as they are: whether stopped time would freeze it, rates, perception. Any rule that answers true wins; a
 * rule that fails counts as false. Without a rule nothing is asked, so a rule is best cheap when no simulation runs (a
 * static flag first). Server side.
 *
 * <p>A copy never joins a level and is ticked by its add-on, so no join or tick event is posted for it: the add-on runs
 * the core's own work for it with {@link #runJoinWork}, {@link #runTickStartWork} and {@link #runTickEndWork}.</p>
 */
public final class EntitySimulations {
	private static final Map<ResourceLocation, Predicate<Entity>> RULES = new LinkedHashMap<>();
	private static volatile ResourceLocation[] owners = new ResourceLocation[0];
	private static volatile Predicate<?>[] rules = new Predicate<?>[0];

	private EntitySimulations() {}

	public static synchronized void register(ResourceLocation owner, Predicate<Entity> simulated) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(simulated, "simulated");
		if (RULES.putIfAbsent(owner, simulated) != null) {
			throw new IllegalStateException("Duplicate entity simulation rule: " + owner);
		}
		refresh();
	}

	/** @return whether a rule was registered for the owner */
	public static synchronized boolean unregister(ResourceLocation owner) {
		boolean removed = RULES.remove(Objects.requireNonNull(owner, "owner")) != null;
		if (removed) {
			refresh();
		}
		return removed;
	}

	private static void refresh() {
		owners = RULES.keySet().toArray(new ResourceLocation[0]);
		rules = RULES.values().toArray(new Predicate<?>[0]);
	}

	public static boolean hasRules() {
		return rules.length > 0;
	}

	@SuppressWarnings("unchecked")
	public static boolean isSimulated(@Nullable Entity entity) {
		Predicate<?>[] all = rules;
		if (all.length == 0 || entity == null) {
			return false;
		}
		ResourceLocation[] keys = owners;
		for (int i = 0; i < all.length && i < keys.length; i++) {
			try {
				if (((Predicate<Entity>) all[i]).test(entity)) {
					return true;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error("Entity simulation rule {} failed.", keys[i], error);
			}
		}
		return false;
	}

	/**
	 * What the core does as an entity joins a level (EntityJoinLevelEvent), for a copy that never joins one: a player's
	 * powers attached; for a mob the vampire goals and the AI a time stop saved on it; a new projectile's Hamon charge.
	 * Call it once the copy is loaded and in place; loadedFromDisk as for the real event (false for
	 * a copy that stands for an entity spawned in the simulation). The level's records leave a simulated copy out.
	 */
	public static void runJoinWork(Entity entity, boolean loadedFromDisk) {
		EventHandler.onEntityJoined(entity, entity.level(), loadedFromDisk);
	}

	/**
	 * What the core does as an entity's tick starts (EntityTickEvent.Pre), but holding it in stopped time, which a
	 * simulated copy is never held by: the end of a Resolve barrage's lift and of a guard's one-tick knockback
	 * resistance, the direction of its gravity. Call it before the copy's tick.
	 */
	public static void runTickStartWork(Entity entity) {
		StandEntityBarrageAbility.ResolveBarrageNoGravity.onTickStarting(entity);
		NoKnockbackOnBlocking.onTickStarting(entity);
		DirectionalGravityApi.reconcileEffectiveDirection(entity);
	}

	/**
	 * What the core does after an entity's tick (EntityTickEvent.Post): the tick of its data (powers and their stamina,
	 * cooldowns, actions, effects), a vampire's sunburn, Magician's Red's fire under a player. Call it after the copy's
	 * tick.
	 */
	public static void runTickEndWork(Entity entity) {
		EventHandler.onEntityTicked(entity);
	}
}
