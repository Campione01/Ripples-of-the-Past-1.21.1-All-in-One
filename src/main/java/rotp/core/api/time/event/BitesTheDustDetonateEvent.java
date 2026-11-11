package rotp.core.api.time.event;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Posted on the game bus by Bites the Dust (Killer Queen, Killer Queen Lion) on the server just before it detonates on
 * an entity, so that other time abilities can answer it without depending on its code. It has three outcomes
 * ({@link Outcome}): not cancelled, it detonates; cancelled, it is held: nothing detonates, nothing is rewound and the
 * bomb stays (owner Q3); {@link #defuse() defused}, the bomb is gone for good, with no explosion, no damage and no
 * rewind, and the poster ends its state as if it had gone off (the owner, for a user with a summoned Gold Experience
 * Requiem: "應該是會在引爆時直接消失並不造成任何影響").
 */
public class BitesTheDustDetonateEvent extends Event implements ICancellableEvent {

	public enum Outcome {
		/** Bites the Dust detonates. */
		DETONATE,
		/** Nothing detonates and nothing is rewound; the bomb stays as it is. */
		HELD,
		/** Nothing detonates and nothing is rewound; the poster removes the bomb for good. */
		DEFUSED
	}

	private final LivingEntity owner;
	private final Entity target;
	@Nullable private final Entity trigger;
	private final ServerLevel level;
	private final long gameTime;
	private boolean defused;

	public BitesTheDustDetonateEvent(LivingEntity owner, Entity target, @Nullable Entity trigger, ServerLevel level, long gameTime) {
		this.owner = owner;
		this.target = target;
		this.trigger = trigger;
		this.level = level;
		this.gameTime = gameTime;
	}

	/** Posts the event for a detonation now; whether Bites the Dust may detonate (false when held or defused). */
	public static boolean post(LivingEntity owner, Entity target, @Nullable Entity trigger) {
		return postOutcome(owner, target, trigger) == Outcome.DETONATE;
	}

	/** Posts the event for a detonation now; what becomes of it. */
	public static Outcome postOutcome(LivingEntity owner, Entity target, @Nullable Entity trigger) {
		if (!(target.level() instanceof ServerLevel level)) {
			return Outcome.DETONATE;
		}
		return NeoForge.EVENT_BUS.post(new BitesTheDustDetonateEvent(owner, target, trigger, level, level.getGameTime()))
				.getOutcome();
	}

	/** Defuses the bomb: nothing detonates, and the poster removes it for good (cancels the event). */
	public void defuse() {
		defused = true;
		setCanceled(true);
	}

	/** Detonates, held while cancelled, defused while cancelled by {@link #defuse()}. */
	public Outcome getOutcome() {
		if (!isCanceled()) {
			return Outcome.DETONATE;
		}
		return defused ? Outcome.DEFUSED : Outcome.HELD;
	}

	/**
	 * Cancelling holds the bomb, unless a listener defused it; un-cancelling lets it detonate and forgets a defusal.
	 */
	@Override
	public void setCanceled(boolean canceled) {
		if (!canceled) {
			defused = false;
		}
		ICancellableEvent.super.setCanceled(canceled);
	}

	/** The user of the Killer Queen whose Bites the Dust this is. */
	public LivingEntity getOwner() {
		return owner;
	}

	/** The entity it is about to detonate on. */
	public Entity getTarget() {
		return target;
	}

	/** Who set it off (the one who found out, or hit the bearer), if anyone. */
	@Nullable
	public Entity getTrigger() {
		return trigger;
	}

	public ServerLevel getLevel() {
		return level;
	}

	public long getGameTime() {
		return gameTime;
	}
}
