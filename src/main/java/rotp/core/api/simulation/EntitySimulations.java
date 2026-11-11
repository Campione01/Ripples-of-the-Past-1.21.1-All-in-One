package rotp.core.api.simulation;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

/**
 * Owner-keyed rules that an entity is a simulation: a copy an add-on keeps outside the world and ticks itself to see
 * what would happen (King Crimson's Epitaph). Such a copy is never added to a level and may share its int id with the
 * real entity it stands for, so the core's own state that is keyed by entity id or spans a level does not take it in:
 * <ul>
 * <li>stopped time (TimeStopState) neither freezes nor releases it, keeps no frozen place, queued resume work or dirty
 * data for it (its id would reach the real entity's), and skips it in the stop's effect refresh;</li>
 * <li>it cannot start a time stop (TimeStopAbility refuses the press before anything is charged);</li>
 * <li>the level's records do not take it: a charged Hamon egg, a chicken hatched from one, Gold Experience's growth
 * sparkles, block destroy progress it deals.</li>
 * </ul>
 * Reads stay as they are: whether stopped time would freeze it, rates, perception. Any rule that answers true wins; a
 * rule that fails counts as false. Without a rule nothing is asked, so a rule is best cheap when no simulation runs (a
 * static flag first). Server side.
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
}
