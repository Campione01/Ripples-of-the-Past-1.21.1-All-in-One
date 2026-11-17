package rotp.core.powersystem.standpower;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * The core Stands' own numbers a server may change on the settings screen's Stands page, each read where the Stand's code
 * used its constant, so the default of each is that constant and nothing in play moves until a server changes it. A value
 * lives in the "special" object of the Stand's tuning file ({@link StandTuning}); an add-on's own numbers stay in its own
 * config file.
 */
public final class StandSpecials {
	public record Special(ResourceLocation standId, String key, boolean integer, double defaultValue, double min, double max) {
		/** This Stand's value; the default for any other Stand, or for none. */
		public double get(@Nullable StandType type) {
			StandTuning tuning = type != null && standId.equals(type.getId()) ? StandTuning.of(type) : null;
			Double set = tuning != null ? tuning.special(key) : null;
			if (set == null) {
				return defaultValue;
			}
			double value = Mth.clamp(set, min, max);
			return integer ? Math.round(value) : value;
		}

		public double get(@Nullable StandPower power) {
			return get(power != null ? power.getPowerType() : null);
		}

		/** The value of the Stand of this entity's user, the entity being the user or the Stand. */
		public double getFor(@Nullable Entity standOrUser) {
			LivingEntity user = standOrUser instanceof LivingEntity living ? StandUtil.getStandUser(living) : null;
			return get(user != null ? StandPower.get(user) : null);
		}

		public int getInt(@Nullable StandPower power) {
			return (int) Math.round(get(power));
		}

		/** A duration kept in seconds, in ticks: 5 seconds read 100 ticks, as the constant it replaces. */
		public int ticks(@Nullable StandPower power) {
			return (int) Math.round(get(power) * 20);
		}

		public int ticksFor(@Nullable Entity standOrUser) {
			return (int) Math.round(getFor(standOrUser) * 20);
		}
	}

	private static final Map<ResourceLocation, List<Special>> BY_STAND = new LinkedHashMap<>();

	private static final ResourceLocation STAR_PLATINUM = JojoMod.resLoc("star_platinum");
	private static final ResourceLocation THE_WORLD = JojoMod.resLoc("the_world");
	private static final ResourceLocation MAGICIANS_RED = JojoMod.resLoc("magicians_red");
	private static final ResourceLocation HIEROPHANT_GREEN = JojoMod.resLoc("hierophant_green");
	private static final ResourceLocation SILVER_CHARIOT = JojoMod.resLoc("silver_chariot");
	private static final ResourceLocation CRAZY_DIAMOND = JojoMod.resLoc("crazy_diamond");
	private static final ResourceLocation GOLD_EXPERIENCE = JojoMod.resLoc("gold_experience");

	// the time stop: TimeStopLearning and TimeStopCooldowns for a Stand without a TimeStopProgressionPolicy
	public static final String TIME_STOP_SECONDS = "timeStopSeconds";
	public static final String TIME_STOP_SECONDS_ENHANCED = "timeStopSecondsEnhanced";
	public static final String TIME_STOP_GROWTH = "timeStopGrowth";
	public static final String TIME_STOP_COOLDOWN = "timeStopCooldown";

	public static final Special SP_TIME_STOP_SECONDS = decimal(STAR_PLATINUM, TIME_STOP_SECONDS, 5, 0.25, 120);
	public static final Special SP_TIME_STOP_SECONDS_ENHANCED = decimal(STAR_PLATINUM, TIME_STOP_SECONDS_ENHANCED, 9, 0.25, 120);
	public static final Special SP_TIME_STOP_GROWTH = decimal(STAR_PLATINUM, TIME_STOP_GROWTH, 0.25, 0, 10);
	public static final Special SP_TIME_STOP_COOLDOWN = decimal(STAR_PLATINUM, TIME_STOP_COOLDOWN, 3, 0, 60);
	public static final Special SP_INHALE_REACH = decimal(STAR_PLATINUM, "inhaleReach", 12, 0, 64);

	public static final Special TW_TIME_STOP_SECONDS = decimal(THE_WORLD, TIME_STOP_SECONDS, 5, 0.25, 120);
	public static final Special TW_TIME_STOP_SECONDS_ENHANCED = decimal(THE_WORLD, TIME_STOP_SECONDS_ENHANCED, 9, 0.25, 120);
	public static final Special TW_TIME_STOP_GROWTH = decimal(THE_WORLD, TIME_STOP_GROWTH, 0.1, 0, 10);
	public static final Special TW_TIME_STOP_COOLDOWN = decimal(THE_WORLD, TIME_STOP_COOLDOWN, 3, 0, 60);
	public static final Special TW_KICK_SWEEP_SHARE = decimal(THE_WORLD, "kickSweepShare", 0.5, 0, 1);

	public static final Special MR_BURN_SECONDS = decimal(MAGICIANS_RED, "burnSeconds", 10, 0, 60);
	public static final Special MR_CROSSFIRE_SHOTS = integer(MAGICIANS_RED, "crossfireShots", 8, 1, 32);
	public static final Special MR_DETECTOR_RADIUS = decimal(MAGICIANS_RED, "detectorRadius", 15, 1, 64);
	public static final Special MR_DETECTOR_SECONDS = decimal(MAGICIANS_RED, "detectorSeconds", 30, 1, 600);

	public static final Special HG_SPLASH_SECONDS = decimal(HIEROPHANT_GREEN, "splashSeconds", 1.5, 0.25, 10);
	public static final Special HG_STRING_COUNT = integer(HIEROPHANT_GREEN, "stringCount", 7, 1, 20);
	public static final Special HG_BARRIERS = integer(HIEROPHANT_GREEN, "barriers", 15, 0, 1000);
	public static final Special HG_BARRIERS_RESOLVE = integer(HIEROPHANT_GREEN, "barriersAtResolve4", 100, 0, 1000);

	public static final Special SC_RAPIER_RICOCHETS = integer(SILVER_CHARIOT, "rapierRicochets", 100, 0, 1000);
	public static final Special SC_SWEEP_SHARE = decimal(SILVER_CHARIOT, "sweepShare", 0.5, 0, 1);
	public static final Special SC_ARMOR_OFF_AFTERIMAGES = integer(SILVER_CHARIOT, "armorOffAfterimages", 10, 0, 50);

	public static final Special CD_RESTORE_REACH = integer(CRAZY_DIAMOND, "restoreReach", 12, 1, 64);
	public static final Special CD_HEAL_SPEED = decimal(CRAZY_DIAMOND, "healSpeed", 1, 0, 10);
	public static final Special CD_MISSHAPEN_SECONDS = decimal(CRAZY_DIAMOND, "misshapenSeconds", 10, 0, 600);

	public static final Special GE_LIFESHOT_SECONDS = decimal(GOLD_EXPERIENCE, "lifeshotSeconds", 5, 0.25, 60);
	public static final Special GE_LIFESHOT_REACH = decimal(GOLD_EXPERIENCE, "lifeshotReach", 8, 1, 32);
	public static final Special GE_HEAL_REGEN_SECONDS = decimal(GOLD_EXPERIENCE, "healRegenSeconds", 300, 1, 3600);

	private StandSpecials() {}

	private static Special decimal(ResourceLocation standId, String key, double defaultValue, double min, double max) {
		return define(new Special(standId, key, false, defaultValue, min, max));
	}

	private static Special integer(ResourceLocation standId, String key, int defaultValue, int min, int max) {
		return define(new Special(standId, key, true, defaultValue, min, max));
	}

	private static Special define(Special special) {
		BY_STAND.computeIfAbsent(special.standId(), id -> new ArrayList<>()).add(special);
		return special;
	}

	/** This Stand's special values in the order the page lists them; none for a Stand without any. */
	public static List<Special> of(ResourceLocation standId) {
		return Collections.unmodifiableList(BY_STAND.getOrDefault(standId, List.of()));
	}

	@Nullable
	public static Special find(ResourceLocation standId, String key) {
		for (Special special : of(standId)) {
			if (special.key().equals(key)) {
				return special;
			}
		}
		return null;
	}

	@Nullable
	private static Special find(@Nullable StandPower power, String key) {
		StandType type = power != null ? power.getPowerType() : null;
		return type != null ? find(type.getId(), key) : null;
	}

	/**
	 * The time stop's numbers, which the core's two time stoppers share: the Stand's own for this key, or the constant for a
	 * Stand that has none (an add-on time stopper without a progression policy keeps the core's).
	 */
	public static int timeStopTicks(@Nullable StandPower power, String key, int fallbackTicks) {
		Special special = find(power, key);
		return special != null ? special.ticks(power) : fallbackTicks;
	}

	public static float timeStopFloat(@Nullable StandPower power, String key, float fallback) {
		Special special = find(power, key);
		return special != null ? (float) special.get(power) : fallback;
	}
}
