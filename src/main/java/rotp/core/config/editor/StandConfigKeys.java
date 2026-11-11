package rotp.core.config.editor;

import javax.annotation.Nullable;

import net.minecraft.resources.ResourceLocation;

/**
 * The names of a Stand's settings on the Stands page: {@code @<namespace>:<stand>/<field>}. A mod id cannot start with '@',
 * so these never meet a core key (no slash) or an add-on's ({@code <modid>/<path>}).
 */
public final class StandConfigKeys {
	public static final char MARK = '@';
	public static final String SPECIAL = "special.";

	private StandConfigKeys() {}

	public static String key(ResourceLocation standId, String field) {
		return MARK + standId.toString() + '/' + field;
	}

	public static String group(ResourceLocation standId) {
		return MARK + standId.toString();
	}

	public static boolean isStandKey(@Nullable String key) {
		return key != null && !key.isEmpty() && key.charAt(0) == MARK;
	}

	/** The Stand a key or a group names, or null for any other name. */
	@Nullable
	public static ResourceLocation standId(@Nullable String keyOrGroup) {
		if (!isStandKey(keyOrGroup)) {
			return null;
		}
		int slash = keyOrGroup.indexOf('/');
		return ResourceLocation.tryParse(slash < 0 ? keyOrGroup.substring(1) : keyOrGroup.substring(1, slash));
	}

	/** {@code powerMax}, {@code damageVsPlayers}, {@code special.timeStopSeconds}; empty for a group. */
	public static String field(String key) {
		int slash = key.indexOf('/');
		return slash < 0 ? "" : key.substring(slash + 1);
	}
}
