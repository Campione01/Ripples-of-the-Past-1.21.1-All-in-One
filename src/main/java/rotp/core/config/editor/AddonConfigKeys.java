package rotp.core.config.editor;

import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;

/** The names of add-on settings: {@code <modid>/<dotted path>}. A core key has no slash, so the two can never meet. */
public final class AddonConfigKeys {
	// Switches that do nothing would lie to the player, and the files keep them: nothing in these add-ons reads them.
	// King Crimson's three are what 1.16 stored and never applied (KingCrimsonConfig.timeEraseCooldownTicks).
	private static final Set<String> HIDDEN = Set.of(
			"rotp_kingcrimson/King Crimson Time Erase Config.timeEraseDynamicCooldown",
			"rotp_kingcrimson/King Crimson Time Erase Config.timeEraseDynamicCooldownMultiplier",
			"rotp_kingcrimson/King Crimson Time Erase Config.timeEraseDynamicMinimumCooldown",
			"darkbluemoon/darkbluemoon.barnacleTrapDuration",
			"rotp_yellowtemperance/client.enableAppleSkinIntegration",
			"rotp_yellowtemperance/client.standPortraitNamespaces");

	private AddonConfigKeys() {}

	public static String key(String modId, List<String> path) {
		return modId + '/' + String.join(".", path);
	}

	public static boolean isAddonKey(@Nullable String key) {
		return key != null && key.indexOf('/') > 0;
	}

	public static String modId(String key) {
		return key.substring(0, key.indexOf('/'));
	}

	public static boolean isHidden(String key) {
		return HIDDEN.contains(key);
	}

	public static Set<String> hiddenKeys() {
		return HIDDEN;
	}

	/** Everything before the leaf: {@code a.b} for {@code a.b.c}, {@code mod/a} for {@code mod/a.c}, {@code mod/} for {@code mod/c}. */
	public static String group(String key) {
		int slash = key.indexOf('/');
		int dot = key.lastIndexOf('.');
		if (dot > slash) {
			return key.substring(0, dot);
		}
		return slash < 0 ? "" : key.substring(0, slash + 1);
	}

	/** The group's path within its add-on, empty for the values at the top of the file. */
	public static String groupPath(String key) {
		String group = group(key);
		return group.substring(group.indexOf('/') + 1);
	}
}
