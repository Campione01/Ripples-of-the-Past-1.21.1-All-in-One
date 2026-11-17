package rotp.core.client.ui.tutorial_toast;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.NoSummonStandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;

/**
 * The official version's tutorial toasts: a tip in the top right corner until the player does what it teaches. Which
 * tip shows when, kept free of client classes; ModTutorialToasts drives it and TutorialToast draws it.
 */
public final class TutorialTips {
	public static final String SUMMON = "stand_summon";
	public static final String SKILLS = "stand_skills";
	public static final List<String> ALL = List.of(SUMMON, SKILLS);
	/** The official's numbers: a tip stays 8 seconds, the next waits 10, and none shows in a player's first 10 seconds. */
	public static final int SHOW_TICKS = 160;
	public static final int PAUSE_TICKS = 200;
	public static final int FIRST_TICKS = 200;

	private TutorialTips() {}

	/** The player did what the tip teaches: it is done for good. */
	public static boolean learned(String tip, boolean summoned, boolean skillsScreenOpen) {
		return switch (tip) {
			case SUMMON -> summoned;
			case SKILLS -> skillsScreenOpen;
			default -> false;
		};
	}

	/** The tip would help the player now. */
	public static boolean wanted(String tip, boolean canSummon, boolean summoned, boolean canLearnSkill) {
		return switch (tip) {
			case SUMMON -> canSummon && !summoned;
			case SKILLS -> canLearnSkill;
			default -> false;
		};
	}

	public static boolean canSummon(@Nullable StandPower power) {
		return power != null && power.hasPower() && !(power.getPowerType() instanceof NoSummonStandType);
	}

	/** A Stand skill the player has not learned can be learned in the skills screen now. */
	public static boolean canLearnStandSkill(@Nullable StandPower power) {
		StandTypePersistentData data = power != null && power.hasPower() ? power.getCurTypeData() : null;
		return data != null && data.getAllSkills().values().stream()
				.anyMatch(skill -> !data.isSkillUnlocked(skill.skillName) && skill.canUnlockFromMenu(power, data).isPositive());
	}

	/** One tip at a time, each at most once a game session, with a pause after each. */
	public static final class Schedule {
		private @Nullable String showing;
		private int showingTicks;
		private int pause;
		private final Set<String> shownThisSession = new HashSet<>();

		public @Nullable String showing() {
			return showing;
		}

		/** How far the shown tip's time has run, 0 to 1. */
		public float progress() {
			return showing != null ? Math.min(1.0F, (float) showingTicks / SHOW_TICKS) : 0.0F;
		}

		/** One unpaused game tick: the tip to start showing now, or null. A seen tip, or the tutorial turned off, ends the shown one. */
		public @Nullable String tick(boolean enabled, int playerTicks, Collection<String> seen, Predicate<String> wanted) {
			if (showing != null) {
				if (!enabled || seen.contains(showing) || ++showingTicks > SHOW_TICKS) {
					showing = null;
					pause = PAUSE_TICKS;
				}
				return null;
			}
			if (pause > 0) {
				pause--;
				return null;
			}
			if (!enabled || playerTicks < FIRST_TICKS) {
				return null;
			}
			for (String tip : ALL) {
				if (!seen.contains(tip) && !shownThisSession.contains(tip) && wanted.test(tip)) {
					showing = tip;
					showingTicks = 0;
					shownThisSession.add(tip);
					return tip;
				}
			}
			return null;
		}
	}
}
