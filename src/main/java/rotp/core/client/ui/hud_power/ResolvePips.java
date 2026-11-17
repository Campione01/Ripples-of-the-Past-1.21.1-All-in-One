package rotp.core.client.ui.hud_power;

import javax.annotation.Nullable;

/**
 * Which of the official version's Resolve pip sprites (textures/hud/stand_resolve) show a 1.16 Resolve state. The
 * official drew its own four Resolve stages with them; here the stages are the Resolve levels.
 */
public final class ResolvePips {
	public static final int PIPS = 4;

	/** Counts of pips, each 0 to 4: filled for the level, outlined for the highest level reached, or the Resolve mode's level. */
	public record Shown(int filled, int outlined, int modeLevel) {}

	private ResolvePips() {}

	/**
	 * Null when the Stand's level count is not four, which the pips cannot show: the strip shows it instead.
	 * {@code resolveEffectLevel} is the Resolve effect's amplifier, -1 without one.
	 */
	public static @Nullable Shown of(int level, int highestLevel, int maxLevel, int resolveEffectLevel) {
		if (maxLevel != PIPS) {
			return null;
		}
		if (resolveEffectLevel >= 0) {
			// the effect's amplifier is the level the mode started at, and the mode raises it by one
			return new Shown(0, 0, clamp(resolveEffectLevel + 1));
		}
		int filled = clamp(level);
		return new Shown(filled, Math.max(filled, clamp(highestLevel)), 0);
	}

	private static int clamp(int pips) {
		return Math.max(0, Math.min(PIPS, pips));
	}
}
