package rotp.core.api.time.event;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Posted on the game bus by a time erase (King Crimson) on the server just before it starts, so that other time
 * abilities can answer it without depending on its code. Cancelled, the erase does not start; by owner Q4 an erase
 * that Gold Experience Requiem returns to zero still spends its stamina and cooldown, which the eraser's add-on does.
 */
public class TimeEraseStartEvent extends Event implements ICancellableEvent {
	private final LivingEntity eraser;
	private final ServerLevel level;
	private final Vec3 position;
	private final long gameTime;
	private final int durationTicks;

	public TimeEraseStartEvent(LivingEntity eraser, ServerLevel level, Vec3 position, long gameTime, int durationTicks) {
		this.eraser = eraser;
		this.level = level;
		this.position = position;
		this.gameTime = gameTime;
		this.durationTicks = durationTicks;
	}

	/** Posts the event for an erase starting now where the eraser stands; whether the erase may start. */
	public static boolean post(LivingEntity eraser, int durationTicks) {
		if (!(eraser.level() instanceof ServerLevel level)) {
			return true;
		}
		return !NeoForge.EVENT_BUS.post(new TimeEraseStartEvent(eraser, level, eraser.position(),
				level.getGameTime(), durationTicks)).isCanceled();
	}

	/** Who erases time. */
	public LivingEntity getEraser() {
		return eraser;
	}

	public ServerLevel getLevel() {
		return level;
	}

	/** Where the erase starts: where its eraser stands, and where a return to zero puts him back. */
	public Vec3 getPosition() {
		return position;
	}

	/** The level's game time the erase starts at. */
	public long getGameTime() {
		return gameTime;
	}

	/** How long the erase would last, in ticks. */
	public int getDurationTicks() {
		return durationTicks;
	}
}
