package rotp.core.api.stand;

import java.util.ArrayDeque;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.powersystem.Moveset;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.HeldInput;
import rotp.core.powersystem.standpower.AbilityFatigueOverrides;
import rotp.core.powersystem.standpower.StandPower;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * The fatigue of the core's no-cooldown mode (the owner, 2026-11-11): a player whose "No Stand ability cooldowns" setting
 * is on waits for no cooldown, and repeating one ability before the cooldown it skipped would have run out costs extra
 * stamina instead. Creatures never use the mode (PlayerClientBroadcastedSettings#isNoStandAbilityCooldownEnabled is false
 * for them), so they keep their cooldowns and take no fatigue.
 * <p>
 * Each use of an ability adds the cooldown the normal mode would set for that use ({@code C}, ticks in the Stand's
 * cooldown share) to the ability's fatigue {@code F}, up to {@link #STACKED_COOLDOWNS_MAX} such cooldowns; a cooldown under
 * {@link #TRIVIAL_COOLDOWN_TICKS} adds none. {@code F} runs down by one tick per tick of its user's
 * EntityTimeRates.Channel#STAND_ACTIONS rate, as cooldowns do. A use while {@code F > 0} is repeat {@code n} (1 for the
 * first) and pays for the cooldown it skips, at most the last cooldown {@code C} added: {@link #repeatCost} =
 * {@link #STAMINA_PER_FATIGUE_TICK} x min(F, C, {@link #COSTED_FATIGUE_TICKS_MAX}) x min(1, 2^(n -
 * {@link #FULL_COST_REPEAT})), with {@link #FULL_COST_REPEAT_PRACTICAL} for a small practical skill
 * (Ability#practicalSkill). The count starts again once {@code F} is gone. Only the same ability counts: another
 * ability's use, or a cooldown one ability sets on another, never adds to it.
 * <p>
 * The core records a use by itself where the normal mode sets the ability's cooldown: Ability#setCooldownOnUse, and
 * StandPower#setAbilityCooldown when it raises the ability's own stored cooldown. While an ability's press or action runs
 * ({@link #enterUse}, opened by the core around them), a cooldown it sets on another ability, or on another user's, is a
 * lock and no use of that ability; a cooldown set outside any press or action (by an entity, an effect, a level tick)
 * counts for the ability it is set on. An ability whose cooldown comes when its effect ends charges the repeat at its
 * start ({@link #chargeRepeat}) and adds the fatigue at its end ({@link #addFatigue}), as the core's time stop does.
 * {@link #withoutFatigue} keeps any cooldown set inside it out of the fatigue.
 * <p>
 * The server's config (standAbilityFatigueOverrides) may hand-set the cooldown an ability's fatigue is derived from, or
 * make it a practical skill, by its Stand's id and its moveset name; it overrides what the ability's code says.
 * <p>
 * In the mode, stamina regeneration also goes on while the Stand acts and during a special state of its user (his time
 * stop), at {@link #SPECIAL_STATE_REGEN_FACTOR} of its rate instead of none. RotpAddonApi#FEATURE_STAND_ABILITY_FATIGUE_V1.
 */
public final class StandAbilityFatigue {
	/** A normal cooldown under this many ticks adds no fatigue (basic punches, guards and other trivial cooldowns). */
	public static final int TRIVIAL_COOLDOWN_TICKS = 20;
	/** Fatigue stacks up to this many normal cooldowns of the use that adds to it. */
	public static final int STACKED_COOLDOWNS_MAX = 4;
	/**
	 * The calibration point, the time stop's former no-cooldown price (x3 until the owner's decision of 2026-11-11): it
	 * paid 2 x (225 / 100 + 9) = 22.5 stamina more per tick of a 100-tick stop and skipped 3 cooldown ticks per tick of
	 * stop, 7.5 stamina per cooldown tick. A full repeat that skips a whole cooldown still pays that.
	 */
	public static final float STAMINA_PER_FATIGUE_TICK = 7.5F;
	/** The skipped cooldown a repeat pays for at most: the cooldown of a full 100-tick time stop. */
	public static final int COSTED_FATIGUE_TICKS_MAX = 300;
	/** The repeat that pays the full cost; each earlier one pays half of the next (1/16, 1/8, 1/4, 1/2, 1). */
	public static final int FULL_COST_REPEAT = 5;
	/**
	 * The same for a small practical skill and the time stop's double press: two repeats gentler (1/64, 1/32, 1/16, 1/8,
	 * 1/4, 1/2, 1), so its first two or three repeats stay small (the owner's emergency time stop).
	 */
	public static final int FULL_COST_REPEAT_PRACTICAL = 7;
	/** The share of stamina regeneration kept while the Stand acts and during its user's special state. */
	public static final float SPECIAL_STATE_REGEN_FACTOR = 0.2F;

	private static final ThreadLocal<int[]> WITHOUT_FATIGUE = ThreadLocal.withInitial(() -> new int[1]);
	private static final ThreadLocal<ArrayDeque<Use>> USES = ThreadLocal.withInitial(ArrayDeque::new);

	private record Use(LivingEntity user, String abilityName) {}

	private StandAbilityFatigue() {}

	/** Whether the power's uses take fatigue: on the server, for a player Stand user with the no-cooldown setting on. */
	public static boolean applies(@Nullable StandPower power) {
		LivingEntity user = power != null ? power.getUser() : null;
		return user != null && !user.level().isClientSide()
				&& PlayerClientBroadcastedSettings.isNoStandAbilityCooldownEnabled(power);
	}

	/**
	 * The share of its stamina regeneration a Stand user keeps while his Stand acts or during his special state (a time
	 * stop): {@link #SPECIAL_STATE_REGEN_FACTOR} in the no-cooldown mode, none otherwise (as 1.16). Both sides.
	 */
	public static float regenKeptDuringUse(@Nullable StandPower power) {
		return PlayerClientBroadcastedSettings.isNoStandAbilityCooldownEnabled(power) ? SPECIAL_STATE_REGEN_FACTOR : 0.0F;
	}

	/** min(1, 2^(repeat - full)): the share of the full cost a repeat pays; 0 for no repeat. */
	public static float repeatCostShare(int repeat, boolean practical) {
		if (repeat <= 0) {
			return 0.0F;
		}
		int full = practical ? FULL_COST_REPEAT_PRACTICAL : FULL_COST_REPEAT;
		return repeat >= full ? 1.0F : (float) Math.pow(2.0D, repeat - full);
	}

	/**
	 * The extra stamina of repeat {@code repeat} with {@code fatigueTicks} of fatigue left, whose last use added
	 * {@code cooldownTicks}: the skipped cooldown, at most that one and {@link #COSTED_FATIGUE_TICKS_MAX}, at
	 * {@link #STAMINA_PER_FATIGUE_TICK} a tick and the repeat's share.
	 */
	public static float repeatCost(float fatigueTicks, int cooldownTicks, int repeat, boolean practical) {
		float skipped = Math.min(Math.min(fatigueTicks, cooldownTicks), COSTED_FATIGUE_TICKS_MAX);
		if (!(skipped > 0.0F)) {
			return 0.0F;
		}
		return STAMINA_PER_FATIGUE_TICK * skipped * repeatCostShare(repeat, practical);
	}

	/** The fatigue after a use whose normal cooldown is {@code cooldownTicks}: F + C, never above 4C but never cut. */
	public static float stackedFatigue(float fatigueTicks, int cooldownTicks) {
		float left = Math.max(fatigueTicks, 0.0F);
		if (cooldownTicks < TRIVIAL_COOLDOWN_TICKS) {
			return left;
		}
		float cap = (float) cooldownTicks * STACKED_COOLDOWNS_MAX;
		return Math.min(left + cooldownTicks, Math.max(left, cap));
	}

	/**
	 * A use of the ability where the normal mode would set this cooldown: charges the repeat's extra stamina if its
	 * fatigue still runs and adds the cooldown to it. {@code cooldownTicks} as StandPower#setAbilityCooldown takes them
	 * (the Stand's cooldown share is applied here). One use per ability and game tick counts. Returns the extra cost asked.
	 */
	public static float recordUse(StandPower power, Ability ability, int cooldownTicks) {
		if (!applies(power) || ability == null) {
			return 0.0F;
		}
		return use(power, ability.name(), ability, power.getAbilityCooldownShare(cooldownTicks), false);
	}

	/** {@link #recordUse(StandPower, Ability, int)} for the ability of the user's moveset under this name. */
	public static float recordUse(StandPower power, String abilityName, int cooldownTicks) {
		if (!applies(power) || abilityName == null || abilityName.isEmpty()) {
			return 0.0F;
		}
		return use(power, abilityName, movesetAbility(power, abilityName), power.getAbilityCooldownShare(cooldownTicks), false);
	}

	/**
	 * The start of an ability whose cooldown comes when its effect ends (a time stop, an erase): a repeat while its
	 * fatigue runs pays its extra stamina now; {@link #addFatigue} at the end adds the cooldown. {@code practicalUse} marks
	 * a use that counts as a small practical skill whatever the ability (the core's emergency time stop, a double press).
	 */
	public static float chargeRepeat(StandPower power, Ability ability, boolean practicalUse) {
		if (!applies(power) || ability == null) {
			return 0.0F;
		}
		boolean practical = isPractical(power, ability.name(), ability, practicalUse);
		float extra = power.getAbilityFatigue().chargeRepeat(ability.name(), practical, gameTime(power));
		return pay(power, extra);
	}

	public static float chargeRepeat(StandPower power, Ability ability) {
		return chargeRepeat(power, ability, false);
	}

	/** The end of such an ability's effect: adds the cooldown the normal mode sets then (taken as setAbilityCooldown takes it). */
	public static void addFatigue(StandPower power, Ability ability, int cooldownTicks) {
		if (applies(power) && ability != null) {
			power.getAbilityFatigue().addFatigue(ability.name(),
					fatigueCooldown(power, ability.name(), ability, power.getAbilityCooldownShare(cooldownTicks)), gameTime(power));
		}
	}

	/** Runs code whose cooldown sets are no use of the abilities they set (one ability's cooldown shared with another). */
	public static void withoutFatigue(Runnable action) {
		int[] depth = WITHOUT_FATIGUE.get();
		depth[0]++;
		try {
			action.run();
		}
		finally {
			depth[0]--;
		}
	}

	public static boolean isWithoutFatigue() {
		return WITHOUT_FATIGUE.get()[0] > 0;
	}

	/**
	 * Marks the ability's press or action as running on this thread until the scope is closed: a cooldown set meanwhile on
	 * another ability, or on another user's, is no use of it. The core opens it around a key press, a buffered press's
	 * replay, a hold's release and each tick of an action; an add-on runs its own ability code elsewhere (an entity that
	 * carries a skill on) inside one when that code sets other abilities' cooldowns. Server side, players only.
	 */
	public static UseScope enterUse(@Nullable LivingEntity user, @Nullable Ability ability) {
		if (!(user instanceof Player) || user.level().isClientSide() || ability == null) {
			return UseScope.NONE;
		}
		USES.get().push(new Use(user, ability.name()));
		return new UseScope(true);
	}

	/** {@link #enterUse(LivingEntity, Ability)} for an action: its ability and its power's user. */
	public static UseScope enterUse(@Nullable EntityActionInstance action) {
		return action != null && action.ability instanceof Ability ability
				? enterUse(action.getPowerUser(), ability)
				: UseScope.NONE;
	}

	/** {@link #enterUse(LivingEntity, Ability)} for a held input that is an ability's action. */
	public static UseScope enterUse(@Nullable LivingEntity user, @Nullable HeldInput held) {
		return held instanceof EntityActionInstance action && action.ability instanceof Ability ability
				? enterUse(user, ability)
				: UseScope.NONE;
	}

	/** An open {@link #enterUse} scope; closing it ends the use. */
	public static final class UseScope implements AutoCloseable {
		private static final UseScope NONE = new UseScope(false);
		private final boolean pushed;
		private boolean closed;

		private UseScope(boolean pushed) {
			this.pushed = pushed;
		}

		@Override
		public void close() {
			if (pushed && !closed) {
				closed = true;
				USES.get().pop();
			}
		}
	}

	/** The fatigue ticks left on the power's ability of this name (server side). */
	public static float getFatigueTicks(StandPower power, String abilityName) {
		return power != null ? power.getAbilityFatigue().getFatigue(abilityName) : 0.0F;
	}

	/** The repeats counted on it while its fatigue has run without a break. */
	public static int getRepeats(StandPower power, String abilityName) {
		return power != null ? power.getAbilityFatigue().getRepeats(abilityName) : 0;
	}

	/** Ability#setCooldownOnUse in the no-cooldown mode: the cooldown the normal mode would set, recorded as a use. */
	@ApiStatus.Internal
	public static void onCooldownSkipped(StandPower power, Ability ability, int ticksHeld) {
		if (!applies(power) || isWithoutFatigue()) {
			return;
		}
		int normal = ability.getNormalCooldown(power, ticksHeld);
		use(power, ability.name(), ability, power.getAbilityCooldownShare(normal), false);
	}

	/** StandPower#setAbilityCooldown raising the ability's stored cooldown in the no-cooldown mode (already in the share). */
	@ApiStatus.Internal
	public static void onCooldownRaised(StandPower power, String abilityName, int sharedCooldownTicks) {
		if (!applies(power) || isWithoutFatigue()) {
			return;
		}
		Use current = USES.get().peek();
		if (current != null && (current.user() != power.getUser() || !current.abilityName().equals(abilityName))) {
			// set by another ability's press or action: a lock on this one, as Made in Heaven's reset sets the time system
			return;
		}
		use(power, abilityName, movesetAbility(power, abilityName), sharedCooldownTicks, false);
	}

	private static float use(StandPower power, String abilityName, @Nullable Ability ability, int sharedCooldownTicks,
			boolean practicalUse) {
		boolean practical = isPractical(power, abilityName, ability, practicalUse);
		int cooldown = fatigueCooldown(power, abilityName, ability, sharedCooldownTicks);
		float extra = power.getAbilityFatigue().use(abilityName, cooldown, practical, gameTime(power));
		return pay(power, extra);
	}

	private static float pay(StandPower power, float extra) {
		if (extra > 0.0F) {
			// a bar that cannot pay is emptied; the ability has already gone off
			power.consumeStamina(extra);
		}
		return extra;
	}

	// the server's override first, then what the ability's code says
	private static int fatigueCooldown(StandPower power, String abilityName, @Nullable Ability ability, int cooldownTicks) {
		AbilityFatigueOverrides.Entry override = AbilityFatigueOverrides.find(power, abilityName);
		if (override != null && override.ticks() >= 0) {
			return override.ticks();
		}
		return Math.max(ability != null ? ability.getFatigueCooldown(power, cooldownTicks) : cooldownTicks, 0);
	}

	private static boolean isPractical(StandPower power, String abilityName, @Nullable Ability ability, boolean practicalUse) {
		if (practicalUse || ability != null && ability.isPracticalSkill(power)) {
			return true;
		}
		AbilityFatigueOverrides.Entry override = AbilityFatigueOverrides.find(power, abilityName);
		return override != null && override.practical();
	}

	@Nullable
	private static Ability movesetAbility(StandPower power, String abilityName) {
		Moveset moveset = power.hasPower() ? power.getMoveset() : null;
		return moveset != null ? moveset.getAbility(abilityName) : null;
	}

	private static long gameTime(StandPower power) {
		LivingEntity user = power.getUser();
		return user != null ? user.level().getGameTime() : 0L;
	}
}
