package rotp.core.client.ui.utils;

/**
 * The rows of the Stand skills screen's list, in pixels from its top: the skills, then, when the Stand has any, a header
 * and its passive skills ({@code Ability#isPassive}). A row's first {@link #ROW_GAP} pixels belong to no row, as the
 * skills' always did. No client classes here, so the smoke test can load it.
 */
public final class SkillListLayout {
	public static final int ROW = 20;
	public static final int ROW_GAP = 4;
	public static final int PASSIVE_HEADER = 14;
	// the header's label, right of the list's scroll bar and left of the scissor's edge
	public static final int LABEL_ROOM = 56;
	public static final int MAX_SECONDS_SHOWN = 999;

	private SkillListLayout() {}

	public static int height(int skills, int passives) {
		return skills * ROW + (passives > 0 ? PASSIVE_HEADER + passives * ROW : 0) + 2;
	}

	public static int passiveHeaderTop(int skills) {
		return skills * ROW;
	}

	public static int passiveRowTop(int skills, int index) {
		return skills * ROW + PASSIVE_HEADER + index * ROW;
	}

	/** The skill at that height of the list, or -1. */
	public static int skillAt(int pos, int skills) {
		if (pos < 0) {
			return -1;
		}
		int index = pos / ROW;
		return index < skills && pos % ROW >= ROW_GAP ? index : -1;
	}

	/** The passive skill at that height of the list, or -1: none on the header. */
	public static int passiveAt(int pos, int skills, int passives) {
		int inPassives = pos - passiveRowTop(skills, 0);
		if (pos < 0 || inPassives < 0) {
			return -1;
		}
		int index = inPassives / ROW;
		return index < passives && inPassives % ROW >= ROW_GAP ? index : -1;
	}

	/** The whole seconds a cooldown of that many ticks has left, rounded up, as its row shows them. */
	public static int secondsShown(int ticks) {
		return ticks <= 0 ? 0 : Math.min((ticks + 19) / 20, MAX_SECONDS_SHOWN);
	}
}
