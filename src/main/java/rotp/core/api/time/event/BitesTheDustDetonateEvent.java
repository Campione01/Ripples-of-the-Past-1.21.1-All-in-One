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
 * an entity, so that other time abilities can answer it without depending on its code. Cancelled, nothing detonates
 * and nothing is rewound; by owner Q3 a summoned Gold Experience Requiem cancels it for its user, mark or no mark.
 */
public class BitesTheDustDetonateEvent extends Event implements ICancellableEvent {
	private final LivingEntity owner;
	private final Entity target;
	@Nullable private final Entity trigger;
	private final ServerLevel level;
	private final long gameTime;

	public BitesTheDustDetonateEvent(LivingEntity owner, Entity target, @Nullable Entity trigger, ServerLevel level, long gameTime) {
		this.owner = owner;
		this.target = target;
		this.trigger = trigger;
		this.level = level;
		this.gameTime = gameTime;
	}

	/** Posts the event for a detonation now; whether Bites the Dust may detonate. */
	public static boolean post(LivingEntity owner, Entity target, @Nullable Entity trigger) {
		if (!(target.level() instanceof ServerLevel level)) {
			return true;
		}
		return !NeoForge.EVENT_BUS.post(new BitesTheDustDetonateEvent(owner, target, trigger, level, level.getGameTime()))
				.isCanceled();
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
