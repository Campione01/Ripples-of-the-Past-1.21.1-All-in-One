package rotp.core.api.time.event;

import java.util.Objects;

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
 * Its {@link Kind} tells a full erase from a step, which the Requiem answers within 4 chunks instead of 8 (the owner:
 * "這個技能的話改成四個區塊").
 */
public class TimeEraseStartEvent extends Event implements ICancellableEvent {

	public enum Kind {
		/** A Time Erase that runs for a while: the key, the reflex, the last chance. */
		ERASE,
		/**
		 * A Time Erase step: an instant move from where the eraser stands to elsewhere through erased time, over in
		 * the tick it starts ("利用時刪從a移動到b，過程被刪除實現的瞬移"); its duration is the ticks the trip implies.
		 */
		STEP
	}

	private final LivingEntity eraser;
	private final ServerLevel level;
	private final Vec3 position;
	private final long gameTime;
	private final int durationTicks;
	private final Kind kind;

	/** A full erase ({@link Kind#ERASE}). */
	public TimeEraseStartEvent(LivingEntity eraser, ServerLevel level, Vec3 position, long gameTime, int durationTicks) {
		this(eraser, level, position, gameTime, durationTicks, Kind.ERASE);
	}

	public TimeEraseStartEvent(LivingEntity eraser, ServerLevel level, Vec3 position, long gameTime, int durationTicks,
			Kind kind) {
		this.eraser = eraser;
		this.level = level;
		this.position = position;
		this.gameTime = gameTime;
		this.durationTicks = durationTicks;
		this.kind = Objects.requireNonNull(kind, "kind");
	}

	/** Posts the event for a full erase starting now where the eraser stands; whether the erase may start. */
	public static boolean post(LivingEntity eraser, int durationTicks) {
		return post(eraser, durationTicks, Kind.ERASE);
	}

	/** Posts the event for an erase of this kind starting now where the eraser stands; whether it may start. */
	public static boolean post(LivingEntity eraser, int durationTicks, Kind kind) {
		if (!(eraser.level() instanceof ServerLevel level)) {
			return true;
		}
		return !NeoForge.EVENT_BUS.post(new TimeEraseStartEvent(eraser, level, eraser.position(),
				level.getGameTime(), durationTicks, kind)).isCanceled();
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

	/** How long the erase would last, in ticks; for a step, the ticks its trip implies. */
	public int getDurationTicks() {
		return durationTicks;
	}

	/** A full erase or a step. */
	public Kind getKind() {
		return kind;
	}
}
