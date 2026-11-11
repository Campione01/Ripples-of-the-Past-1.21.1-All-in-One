package rotp.core.api.entity;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.init.ModEntityTypeTags;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Creeper;

/**
 * Pauses a mob's thinking for a number of its own ticks, on the server, for King Crimson's amnesia at the end of a
 * Time Erase and Gold Experience Requiem's Return to Zero ("only their AI: goals, navigation, targeting and look do
 * nothing, and they stand where they are; gravity, knockback, damage and fire go on").
 * <p>
 * While paused, a mob's goals, brain (villagers, piglins, the Warden...), sensing, navigation and its move, look and jump
 * controls do not run and it gets no walk, swim or jump input, so it makes no attack or special attack: a creeper's fuse
 * goes back down (not one lit by flint and steel), a Wither neither flies at its targets nor shoots, and an Ender
 * Dragon's phases, flight, wings, head and block breaking hold where it hovers. The actions of the mob and of its
 * summoned Stand wait as they do in stopped time. Gravity, knockback, damage, fire, effects and picking up items go on.
 * <p>
 * Counted by the mob's own ticks, so whatever stops or slows the mob's ticks (stopped time, a time rate) stops or slows
 * the count. Never NoAI and never saved: a reload or a change of dimension ends it. Types in the entity type tag
 * {@code jojo_ripples:never_ai_paused} (empty by default) are never paused. The client knows nothing of it.
 */
public final class MobAiPause {

	/** Implemented by Mob. */
	@ApiStatus.Internal
	public interface Holder {
		int jojo_ripples$aiPauseTicks();

		void jojo_ripples$setAiPauseTicks(int ticks);
	}

	private MobAiPause() {}

	/**
	 * Pauses the mob for at least this many of its ticks: a running pause that has more left keeps it.
	 *
	 * @return whether the mob is paused now (false on a client, for a dead or removed mob or a type that is never paused)
	 */
	public static boolean pause(Mob mob, int ticks) {
		if (!canPause(mob)) {
			return false;
		}
		Holder holder = (Holder) mob;
		if (ticks > holder.jojo_ripples$aiPauseTicks()) {
			start(mob, ticks);
		}
		return holder.jojo_ripples$aiPauseTicks() > 0;
	}

	/**
	 * Pauses the mob for exactly this many of its ticks from now, whatever was left of a running pause; 0 ends it.
	 *
	 * @return whether the mob is paused now
	 */
	public static boolean restart(Mob mob, int ticks) {
		if (!canPause(mob)) {
			return false;
		}
		start(mob, Math.max(ticks, 0));
		return ticks > 0;
	}

	/** The mob's ticks of pause left, 0 when it thinks. */
	public static int remainingTicks(Mob mob) {
		return ((Holder) mob).jojo_ripples$aiPauseTicks();
	}

	/** Ends the mob's pause now: it thinks again on its next tick. */
	public static void end(Mob mob) {
		((Holder) mob).jojo_ripples$setAiPauseTicks(0);
	}

	/** Whether the entity is a paused mob, or a Stand whose user is one. */
	public static boolean isPaused(Entity entity) {
		Entity mob = entity instanceof StandEntity stand ? stand.getUser() : entity;
		return mob instanceof Holder holder && holder.jojo_ripples$aiPauseTicks() > 0;
	}

	/** Whether a pause can take the mob: on the server, alive and not of a type that is never paused. */
	public static boolean canPause(Mob mob) {
		return !mob.level().isClientSide() && mob.isAlive() && !mob.getType().is(ModEntityTypeTags.NEVER_AI_PAUSED);
	}

	private static void start(Mob mob, int ticks) {
		((Holder) mob).jojo_ripples$setAiPauseTicks(ticks);
		if (ticks > 0) {
			holdStill(mob);
		}
	}

	/** In place of a paused mob's AI step: no input of its own. */
	@ApiStatus.Internal
	public static void holdStill(Mob mob) {
		mob.setSpeed(0.0F);
		mob.setXxa(0.0F);
		mob.setYya(0.0F);
		mob.setZza(0.0F);
		mob.setJumping(false);
		holdFuse(mob);
	}

	/** A paused creeper's fuse goes back down, unless something lit it. */
	@ApiStatus.Internal
	public static void holdFuse(LivingEntity entity) {
		if (entity instanceof Creeper creeper && !creeper.isIgnited()) {
			creeper.setSwellDir(-1);
		}
	}
}
