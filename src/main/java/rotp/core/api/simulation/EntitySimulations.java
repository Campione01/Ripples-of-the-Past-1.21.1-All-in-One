package rotp.core.api.simulation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import rotp.core.api.gravity.DirectionalGravityApi;
import rotp.core.core.EventHandler;
import rotp.core.core.JojoMod;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.powersystem.standpower.entity.NoKnockbackOnBlocking;
import rotp.core.subsystems.movement_input_sync.PlayerMovementInputData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

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
 * the core's own work for it with {@link #runJoinWork}, {@link #runTickStartWork} and {@link #runTickEndWork}, and lets
 * the add-ons that asked for it do theirs with {@link #runTickListeners}.</p>
 */
public final class EntitySimulations {
	private static final Map<ResourceLocation, Predicate<Entity>> RULES = new LinkedHashMap<>();
	private static volatile ResourceLocation[] owners = new ResourceLocation[0];
	private static volatile Predicate<?>[] rules = new Predicate<?>[0];
	private static final Map<ResourceLocation, Consumer<Entity>> TICK_LISTENERS = new LinkedHashMap<>();
	private static volatile ResourceLocation[] tickListenerOwners = new ResourceLocation[0];
	private static volatile Consumer<?>[] tickListeners = new Consumer<?>[0];

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
	 * resistance, the direction of its gravity; and for a player what it does as his tick starts (PlayerTickEvent.Pre):
	 * no sprinting while stunned. Call it before the copy's tick.
	 */
	public static void runTickStartWork(Entity entity) {
		StandEntityBarrageAbility.ResolveBarrageNoGravity.onTickStarting(entity);
		NoKnockbackOnBlocking.onTickStarting(entity);
		DirectionalGravityApi.reconcileEffectiveDirection(entity);
		if (entity instanceof Player player) {
			EventHandler.stopSprintingInStun(player);
		}
	}

	/**
	 * What the core does after an entity's tick (EntityTickEvent.Post): the tick of its data (powers and their stamina,
	 * cooldowns, actions, effects), a vampire's sunburn, Magician's Red's fire under a player; and for a player what it
	 * does as his tick ends (PlayerTickEvent.Post) on his copy: the timers of his movement keys. The AFK particles and
	 * advancement and the temple map's message reach the world and are left out. Call it after the copy's tick.
	 */
	public static void runTickEndWork(Entity entity) {
		EventHandler.onEntityTicked(entity);
		if (entity instanceof Player player) {
			PlayerMovementInputData.tickTimers(player);
		}
	}

	/**
	 * Subscribes an add-on's per-tick work for simulated copies, which get no tick event: a copy's room calls
	 * {@link #runTickListeners} for each copy once a tick. The listener must touch the copy only (its own state, its
	 * data, its powers), never the world, the real entity it stands for, or anything keyed by the copy's id. One
	 * listener per owner. Server side.
	 */
	public static synchronized void subscribeTick(ResourceLocation owner, Consumer<Entity> listener) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(listener, "listener");
		if (TICK_LISTENERS.putIfAbsent(owner, listener) != null) {
			throw new IllegalStateException("Duplicate entity simulation tick listener: " + owner);
		}
		refreshTickListeners();
	}

	/** @return whether a tick listener was subscribed for the owner */
	public static synchronized boolean unsubscribeTick(ResourceLocation owner) {
		boolean removed = TICK_LISTENERS.remove(Objects.requireNonNull(owner, "owner")) != null;
		if (removed) {
			refreshTickListeners();
		}
		return removed;
	}

	private static void refreshTickListeners() {
		tickListenerOwners = TICK_LISTENERS.keySet().toArray(new ResourceLocation[0]);
		tickListeners = TICK_LISTENERS.values().toArray(new Consumer<?>[0]);
	}

	public static boolean hasTickListeners() {
		return tickListeners.length > 0;
	}

	/**
	 * For a copy's room, once a tick for each copy, after its tick and {@link #runTickEndWork}: runs the subscribed
	 * listeners in their order of subscription. A listener that fails is logged and the next one runs. Nothing is done
	 * and nothing allocated when nobody subscribed.
	 */
	@SuppressWarnings("unchecked")
	public static void runTickListeners(Entity copy) {
		Consumer<?>[] all = tickListeners;
		if (all.length == 0 || copy == null) {
			return;
		}
		ResourceLocation[] keys = tickListenerOwners;
		for (int i = 0; i < all.length && i < keys.length; i++) {
			try {
				((Consumer<Entity>) all[i]).accept(copy);
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error("Entity simulation tick listener {} failed.", keys[i], error);
			}
		}
	}
}
