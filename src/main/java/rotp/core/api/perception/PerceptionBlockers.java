package rotp.core.api.perception;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Owner-keyed rules that an entity cannot be perceived, and so not targeted, by another one (a King Crimson user in
 * erased time, whom a time stopper does not see). Any rule that blocks wins. The core asks them, on both sides, wherever
 * it takes an entity as a target: HitResultUtil's rays (so the Stands' aims and the precision ray), the aim a client sends
 * to the server, the Stands' strikes, and the time-stop blink and TS punch, which also do not land on or next to an entity
 * their user cannot perceive. An add-on that picks targets of its own asks {@link #isBlocked}. A rule that fails counts as
 * not blocking. Without a rule nothing is asked.
 */
public final class PerceptionBlockers {

	/** How the viewer looks at the target. */
	public enum Sight {
		/** An aim, a ray or a strike in the viewer's own time, in stopped time too. */
		AIM,
		/**
		 * The viewer moves or strikes within an instant time stop only it lives through, which no time stop state covers:
		 * the time-stop blink (its target and where it lands) and the TS punch's blink. Asked besides {@link #AIM}.
		 */
		INSTANT_TIME_STOP
	}

	@FunctionalInterface
	public interface Blocker {
		/**
		 * @param viewer the entity that looks: an aiming user, a Stand (StandUtil.getStandUser gives its user), an aiming
		 *        projectile, or null for a ray that no entity casts
		 * @return true if the viewer cannot perceive the target
		 */
		boolean blocks(@Nullable Entity viewer, Entity target, Sight sight);
	}

	private static final Map<ResourceLocation, Blocker> BLOCKERS = new LinkedHashMap<>();
	private static volatile ResourceLocation[] owners = new ResourceLocation[0];
	private static volatile Blocker[] blockers = new Blocker[0];

	private PerceptionBlockers() {}

	public static synchronized void register(ResourceLocation owner, Blocker blocker) {
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(blocker, "blocker");
		if (BLOCKERS.putIfAbsent(owner, blocker) != null) {
			throw new IllegalStateException("Duplicate perception blocker: " + owner);
		}
		owners = BLOCKERS.keySet().toArray(new ResourceLocation[0]);
		blockers = BLOCKERS.values().toArray(new Blocker[0]);
	}

	public static boolean hasBlockers() {
		return blockers.length > 0;
	}

	/** Whether the viewer cannot perceive the target by an aim ({@link Sight#AIM}). */
	public static boolean isBlocked(@Nullable Entity viewer, Entity target) {
		return isBlocked(viewer, target, Sight.AIM);
	}

	public static boolean isBlocked(@Nullable Entity viewer, Entity target, Sight sight) {
		Blocker[] all = blockers;
		if (all.length == 0 || target == null) {
			return false;
		}
		ResourceLocation[] keys = owners;
		for (int i = 0; i < all.length && i < keys.length; i++) {
			try {
				if (all[i].blocks(viewer, target, sight)) {
					return true;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error("Perception blocker {} failed.", keys[i], error);
			}
		}
		return false;
	}

	/**
	 * The entity filter of a ray the viewer casts, without the entities it cannot perceive; the filter itself (null
	 * too) while no rule is registered. A null filter takes every entity the viewer perceives.
	 */
	@Nullable
	public static Predicate<Entity> perceivedBy(@Nullable Entity viewer, @Nullable Predicate<Entity> filter, Sight sight) {
		if (blockers.length == 0) {
			return filter;
		}
		return filter == null
				? entity -> !isBlocked(viewer, entity, sight)
				: entity -> filter.test(entity) && !isBlocked(viewer, entity, sight);
	}

	/** Whether an entity the viewer cannot perceive meets the box (the viewer itself left out). */
	public static boolean anyBlockedIn(Level level, @Nullable Entity viewer, AABB box, Sight sight) {
		if (blockers.length == 0) {
			return false;
		}
		List<Entity> blocked = level.getEntities(viewer, box, entity -> isBlocked(viewer, entity, sight));
		return !blocked.isEmpty();
	}
}
