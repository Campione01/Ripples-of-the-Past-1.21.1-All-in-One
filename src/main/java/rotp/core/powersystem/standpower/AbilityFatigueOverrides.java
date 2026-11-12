package rotp.core.powersystem.standpower;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import rotp.core.JojoModConfig;

import net.minecraft.resources.ResourceLocation;

/**
 * The server's hand-set fatigue tiers (JojoModConfig standAbilityFatigueOverrides), keyed by the Stand's id and the
 * ability's moveset name: {@code <stand id>/<ability>=<ticks>} sets the cooldown the ability's fatigue is derived from
 * (0: none), {@code <stand id>/<ability>=practical} makes it a small practical skill. Both may be given for one ability.
 * A line of another shape is left out.
 */
@ApiStatus.Internal
public final class AbilityFatigueOverrides {
	/** The defaults the coordinator set for R772.1: utility skills at 300 ticks, everyday and emergency skills practical. */
	public static final List<String> DEFAULTS = List.of(
			"rotp_wr:weather_report/change_weather=300",
			"rotp_wr:weather_report/cloud_shield=300",
			"rotp_wr:weather_report/give_cloud_shield=300",
			"highwaystar:highway_star/highway_star_room=300",
			"rotp_ttr:ticket_to_ride/emiya=300",
			"rotp_mandom:mandom/time_rewind=practical",
			"rotp_ab:achtung_baby/active_invisibility=practical",
			"rotp_zhp:hermito_purple/hp_doxx=practical",
			"rotp_zhp:hermito_purple/cringe=200");
	public static final String PRACTICAL = "practical";
	private static final Pattern LINE = Pattern.compile("([a-z0-9_.-]+:[a-z0-9_./-]+)/([^/=\\s]+)=(" + PRACTICAL + "|\\d{1,6})");

	/** ticks: the hand-set cooldown, or -1 for none given. */
	public record Entry(int ticks, boolean practical) {
		static final Entry NONE = new Entry(-1, false);
	}

	private static volatile List<? extends String> parsedFrom;
	private static volatile Map<String, Entry> table = Map.of();
	@Nullable private static volatile Map<String, Entry> testTable;

	private AbilityFatigueOverrides() {}

	public static boolean isValid(Object value) {
		return value instanceof String line && LINE.matcher(line.trim()).matches();
	}

	public static Map<String, Entry> parse(List<? extends String> lines) {
		Map<String, Entry> parsed = new HashMap<>();
		for (String raw : lines) {
			Matcher matcher = raw != null ? LINE.matcher(raw.trim()) : null;
			if (matcher == null || !matcher.matches() || ResourceLocation.tryParse(matcher.group(1)) == null) {
				continue;
			}
			String key = matcher.group(1) + "/" + matcher.group(2);
			Entry old = parsed.getOrDefault(key, Entry.NONE);
			parsed.put(key, PRACTICAL.equals(matcher.group(3))
					? new Entry(old.ticks(), true)
					: new Entry(Integer.parseInt(matcher.group(3)), old.practical()));
		}
		return Map.copyOf(parsed);
	}

	/** The server's override of this ability of the power's Stand, or null. */
	@Nullable
	public static Entry find(StandPower power, String abilityName) {
		if (power == null || abilityName == null || power.getPowerType() == null) {
			return null;
		}
		return current().get(power.getPowerType().getId() + "/" + abilityName);
	}

	private static Map<String, Entry> current() {
		Map<String, Entry> test = testTable;
		if (test != null) {
			return test;
		}
		List<? extends String> lines = JojoModConfig.getCommonConfigInstance(false).standAbilityFatigueOverrides.get();
		if (!Objects.equals(lines, parsedFrom)) {
			table = parse(lines);
			parsedFrom = List.copyOf(lines);
		}
		return table;
	}

	/** Game tests: runs the action with this table in place of the server's. */
	public static void runWithTable(List<String> lines, Runnable action) {
		Map<String, Entry> previous = testTable;
		testTable = parse(lines);
		try {
			action.run();
		}
		finally {
			testTable = previous;
		}
	}
}
