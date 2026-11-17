package rotp.core.api.time.event;

import java.util.Objects;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Posted on the game bus by a time erase (King Crimson) on the server in each server tick of a running erase (the key,
 * the reflex, the last chance), so that other time abilities can answer it without depending on its code. Cancelled,
 * the eraser is thrown out of the erase at once where he stands, and the rest of the end runs as at any end but for
 * the blow and the Blood Throw that only his own exit gives; by the owner, Gold Experience Requiem cancels it within
 * 4 chunks of its summoned holder ("直接被強制退出時刪並顯示黃鎮的歸零特效"), and shows its own Return to Zero on him.
 * The canceller says who answered with {@link #answer}, so that the eraser's side can leave out its own exit effects.
 */
public class TimeEraseRunningEvent extends Event implements ICancellableEvent {
	private final LivingEntity eraser;
	private final ServerLevel level;
	private final Vec3 position;
	private final int ticksElapsed;
	@Nullable private ResourceLocation answeredBy;
	@Nullable private LivingEntity answerer;

	public TimeEraseRunningEvent(LivingEntity eraser, ServerLevel level, Vec3 position, int ticksElapsed) {
		this.eraser = eraser;
		this.level = level;
		this.position = position;
		this.ticksElapsed = ticksElapsed;
	}

	/**
	 * Posts the event for this tick of the eraser's running erase, where he stands now. The erase ends at once if the
	 * event comes back cancelled ({@link #isCanceled()}); {@link #getAnsweredBy()} then says who ended it, if anyone did.
	 *
	 * @throws IllegalArgumentException for an eraser that is not in a server level
	 */
	public static TimeEraseRunningEvent post(LivingEntity eraser, int ticksElapsed) {
		if (!(eraser.level() instanceof ServerLevel level)) {
			throw new IllegalArgumentException("A time erase runs on the server: " + eraser);
		}
		return NeoForge.EVENT_BUS.post(new TimeEraseRunningEvent(eraser, level, eraser.position(), ticksElapsed));
	}

	/**
	 * Throws the eraser out of his erase (cancels the event) and says who did: the ability, e.g.
	 * geraddon:return_to_zero, and the entity that answered (the Requiem's holder), if any.
	 */
	public void answer(ResourceLocation by, @Nullable LivingEntity answerer) {
		this.answeredBy = Objects.requireNonNull(by, "by");
		this.answerer = answerer;
		setCanceled(true);
	}

	/** Who erases time. */
	public LivingEntity getEraser() {
		return eraser;
	}

	public ServerLevel getLevel() {
		return level;
	}

	/** Where the eraser stands in this tick. */
	public Vec3 getPosition() {
		return position;
	}

	/** The erase's ticks so far, as the eraser's side counts them. */
	public int getTicksElapsed() {
		return ticksElapsed;
	}

	/** The ability that threw the eraser out, if the canceller said; null otherwise. */
	@Nullable
	public ResourceLocation getAnsweredBy() {
		return answeredBy;
	}

	/** The entity that answered, if the canceller named one. */
	@Nullable
	public LivingEntity getAnswerer() {
		return answerer;
	}
}
