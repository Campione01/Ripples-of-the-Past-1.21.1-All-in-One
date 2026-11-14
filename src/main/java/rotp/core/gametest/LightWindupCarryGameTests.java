package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.entity.StandStatFormulas.LightWindup;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The light windup's rounding (owner, 2026-11-14): a first punch whose mean windup is above 0 takes at least a tick, and
 * a sequence of punches keeps the formula's mean windup, the first punch's extra coming off the next punches
 * (StandStatFormulas#getLightAttackWindup with its carry).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LightWindupCarryGameTests {
	private LightWindupCarryGameTests() {}

	private static final int FIRST_PUNCHES = 200;
	private static final int TWO_PUNCH_SEQUENCES = 500;
	private static final int CHAINED_PUNCHES = 2000;
	private static final int FORMULA_PUNCHES = 20000;
	// a rounded windup varies by at most a quarter of a tick squared, so N of them by at most half a tick times the root of
	// N; six times that fails an unbiased rounding about twice in a billion runs
	private static final double SIGMAS = 6;

	/*
	 * Star Platinum at its base speed 18, whose first punch's mean windup is 0.75: 200 first punches of its registered punch
	 * each start in a windup of a tick (rounded at random, a quarter of them had none). And the formula at every quarter
	 * speed from 14 to 24, with meter and guard counter: a first punch's windup is 0 only where its mean is 0, a tick where
	 * its mean is below one, its carry the rest, else its mean rounded; a later punch whose mean is a tick or more never
	 * takes less than a tick, whatever carry it pays.
	 */
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aFirstPunchNeverLosesItsWindupWhileItsMeanIsAboveZero(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (int quarter = 56; quarter <= 96; quarter++) {
			double speed = quarter / 4.0D;
			for (float meter : new float[] { 0, 0.5F, 1 }) {
				for (float guard : new float[] { 0, 0.3F, 0.9F, 1 }) {
					double mean = StandStatFormulas.getLightAttackWindupMean(speed, meter, guard, true);
					LightWindup first = StandStatFormulas.getLightAttackWindup(speed, meter, guard, true, 0.7D);
					boolean asRuled = mean <= 0 ? first.ticks() == 0 && first.carry() == 0
							: mean < 1 ? first.ticks() == 1 && Math.abs(first.carry() - (1 - mean)) < 1.0E-9
							: (first.ticks() == Mth.floor(mean) || first.ticks() == Mth.ceil(mean)) && first.carry() == 0;
					double later = StandStatFormulas.getLightAttackWindupMean(speed, meter, guard, false);
					for (double carry : new double[] { 0, 0.5D, 0.99D }) {
						LightWindup next = StandStatFormulas.getLightAttackWindup(speed, meter, guard, false, carry);
						double paid = carry - next.carry();
						asRuled &= paid >= -1.0E-12 && paid <= carry + 1.0E-12
								&& later - paid >= (later >= 1 ? 1 : 0) - 1.0E-12 && (later < 1 || next.ticks() >= 1);
					}
					if (!asRuled) {
						wrong.add("speed " + speed + ", meter " + meter + ", guard counter " + guard + ": a first punch of mean "
								+ mean + " took " + first);
					}
				}
			}
		}
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			StandEntity stand = scene.stand();
			helper.assertTrue(stand.getAttackSpeed() == 18.0D && stand.getFinisherMeter() == 0 && stand.guardCounter() == 0,
					"Fixture: Star Platinum's speed " + stand.getAttackSpeed() + ", meter " + stand.getFinisherMeter()
							+ ", guard counter " + stand.guardCounter() + ", not 18, 0 and 0");
			EntityActionAbility punch = (EntityActionAbility) scene.power().getAbility("punch");
			int none = 0;
			for (int i = 0; i < FIRST_PUNCHES; i++) {
				helper.assertTrue(stand.getCurStandAction() == null, "Fixture: Star Platinum's punch is not a first punch");
				EntityActionInstance action = punch.initActionOnAbilityUse(helper.getLevel(), scene.user(), stand, null);
				if (action.getPhase() != ActionPhase.WINDUP || action.getCurPhaseLength() != 1) {
					none++;
				}
			}
			if (none > 0) {
				wrong.add(none + " of " + FIRST_PUNCHES + " first punches of Star Platinum at speed 18 (mean windup "
						+ StandStatFormulas.getLightAttackWindupMean(18, 0, 0, true) + ") did not start in a one-tick windup");
			}
		});
		helper.assertTrue(wrong.isEmpty(), "A first punch whose mean windup is above 0 takes at least a tick: "
				+ String.join("; ", wrong));
		helper.succeed();
	}

	/*
	 * Star Platinum's registered punch at speed 18 in 500 sequences of two punches, each sequence ended before the next (a
	 * first punch of mean 0.75 and a second of 1.5 that pays the first's carry of 0.25), and at speed 23 in one sequence of
	 * 2000 punches as they chain (0.125, then 0.25 each); and the formula alone over 20000 punches at every other quarter
	 * speed from 16.25 to 23.75: the windups add up to the formula's means within six standard deviations of their
	 * rounding, and a single sequence within the tick more its carry may leave unpaid as it ends.
	 */
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aPunchSequenceKeepsTheFormulasMeanWindup(GameTestHelper helper) {
		List<String> wrong = new ArrayList<>();
		for (int quarter = 65; quarter <= 95; quarter += 2) {
			double speed = quarter / 4.0D;
			double carry = 0;
			double means = 0;
			long ticks = 0;
			for (int i = 0; i < FORMULA_PUNCHES; i++) {
				boolean first = i == 0;
				means += StandStatFormulas.getLightAttackWindupMean(speed, 0, 0, first);
				LightWindup windup = StandStatFormulas.getLightAttackWindup(speed, 0, 0, first, carry);
				ticks += windup.ticks();
				carry = windup.carry();
			}
			check(wrong, "the formula at speed " + speed, ticks, means, 1, FORMULA_PUNCHES);
		}
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			StandEntity stand = scene.stand();
			LivingComponentAction actions = LivingComponentAction.getComponent(stand);
			EntityActionAbility punch = (EntityActionAbility) scene.power().getAbility("punch");
			helper.assertTrue(stand.getAttackSpeed() == 18.0D, "Fixture: Star Platinum's speed is " + stand.getAttackSpeed());
			double means = 0;
			long ticks = 0;
			for (int sequence = 0; sequence < TWO_PUNCH_SEQUENCES; sequence++) {
				for (int i = 0; i < 2; i++) {
					EntityActionInstance action = punch.initActionOnAbilityUse(helper.getLevel(), scene.user(), stand, null);
					actions.setAction(action, scene.user(), SyncType.NO_SYNC);
					ticks += (long) action.phasesLength.getFloat(ActionPhase.WINDUP);
					means += StandStatFormulas.getLightAttackWindupMean(18, 0, 0, i == 0);
				}
				actions.setAction(null, scene.user(), SyncType.NO_SYNC);
			}
			// the second punch's windup of 1.5 has room for the first's carry of 0.25: nothing is left unpaid
			check(wrong, "Star Platinum's punch at speed 18 in sequences of two", ticks, means, 0, 2 * TWO_PUNCH_SEQUENCES);

			scene.user().getAttribute(ModEntityAttributes.STAND_SPEED).setBaseValue(23.0D);
			StandEntity.updateStandStatAttributes(stand, scene.user());
			helper.assertTrue(stand.getAttackSpeed() == 23.0D, "Fixture: Star Platinum's speed is " + stand.getAttackSpeed()
					+ ", not 23");
			means = 0;
			ticks = 0;
			for (int i = 0; i < CHAINED_PUNCHES; i++) {
				EntityActionInstance action = punch.initActionOnAbilityUse(helper.getLevel(), scene.user(), stand, null);
				actions.setAction(action, scene.user(), SyncType.NO_SYNC);
				ticks += (long) action.phasesLength.getFloat(ActionPhase.WINDUP);
				means += StandStatFormulas.getLightAttackWindupMean(23, 0, 0, i == 0);
			}
			actions.setAction(null, scene.user(), SyncType.NO_SYNC);
			check(wrong, "Star Platinum's punch at speed 23 in one sequence", ticks, means, 1, CHAINED_PUNCHES);
		});
		helper.assertTrue(wrong.isEmpty(), "A sequence of punches keeps the formula's mean windup: " + String.join("; ", wrong));
		helper.succeed();
	}

	private static void check(List<String> wrong, String what, long ticks, double means, double unpaid, int punches) {
		double tolerance = SIGMAS * 0.5D * Math.sqrt(punches) + unpaid;
		if (Math.abs(ticks - means) > tolerance) {
			wrong.add(what + ": " + punches + " windups took " + ticks + " ticks, their means " + means + " (tolerance "
					+ tolerance + ")");
		}
	}
}
