package rotp.addons.ger.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.ability.GerLifeShotAbility;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Hurt;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.ObservedCow;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandMeleeReach;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandStats;

/**
 * The owner's panel of 2026-11-08: power 20, speed 17.5, range 7 / 10.5,
 * durability 20, precision 20. Every basic move is the core's formula on
 * those stats, read here from real presses on a real target.
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerBasicMovesGameTests {
    static final float PUNCH = 5.0F;
    static final float HEAVY = 20.0F;
    static final float BARRAGE_HIT = 0.315F;
    static final int BARRAGE_HITS_A_TICK = 6;
    static final int BARRAGE_LONGEST_TICKS = 120;
    static final int HEAVY_WINDUP_TICKS = 13;
    static final int HEAVY_RECOVERY_TICKS = 13;
    /** Today's core: power x 1.5. */
    static final float CHARGED_BY_POWER = 30.0F;
    /** The core's coming rule: the larger of it and 1.1 x the barrage's damage a second. */
    static final float CHARGED_BY_BARRAGE = 1.1F * BARRAGE_HIT * BARRAGE_HITS_A_TICK * 20;
    static final double REACH = 10.5D;
    static final double REACH_LIMIT = 13.125D;
    static final double REMOTE_LIMIT = 15.75D;
    private static final float EPS = 1.0E-3F;

    private GerBasicMovesGameTests() {}

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 600, batch = "ger_basic_moves")
    public static void basicMovesFollowTheAwakenedPanel(GameTestHelper helper) {
        new Sequence(helper, "basic/moves", 500) {
            private Actor actor;
            private ObservedCow cow;
            private EntityActionInstance action;
            private int seen;
            private int waited;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        actor = scene.actor();
                        cow = scene.cow(scene.origin.add(0, 0, 1.5));
                        actor.stand().setFinisherMeter(0);
                        stage = 1;
                    }
                    case 1 -> {
                        action = scene.pressAction(actor, cow, "ger_stand_punch", "ger_stand_punch",
                                InputMethod.CLICK, GerMeleeGameTestScene.LMB);
                        stage = 2;
                    }
                    case 2 -> {
                        if (cow.hurts.size() > seen) {
                            expect("the punch", cow.hurts.get(seen).amount(), PUNCH);
                            stage = 3;
                        }
                    }
                    case 3 -> {
                        if (idle()) {
                            actor.stand().setFinisherMeter(0);
                            action = scene.pressAction(actor, cow, "ger_stand_heavy_punch",
                                    "ger_stand_heavy_punch", InputMethod.CLICK, GerMeleeGameTestScene.RMB);
                            helper.assertTrue(action.phasesLength.getFloat(ActionPhase.WINDUP) == HEAVY_WINDUP_TICKS,
                                    scene.id + ": the heavy punch winds up for "
                                            + action.phasesLength.getFloat(ActionPhase.WINDUP) + " ticks");
                            stage = 4;
                        }
                    }
                    case 4 -> {
                        if (cow.hurts.size() > seen) {
                            expect("the heavy punch", cow.hurts.get(seen).amount(), HEAVY);
                            stage = 5;
                        }
                    }
                    case 5 -> {
                        if (action.getPhase() == ActionPhase.RECOVERY && waited++ == 0) {
                            helper.assertTrue(action.getCurPhaseLength() == HEAVY_RECOVERY_TICKS,
                                    scene.id + ": the heavy punch recovers for " + action.getCurPhaseLength()
                                            + " ticks");
                        }
                        if (idle()) {
                            actor.stand().setFinisherMeter(0);
                            action = scene.pressAction(actor, cow, "ger_stand_barrage", "ger_stand_barrage",
                                    InputMethod.HOLD, GerMeleeGameTestScene.LMB);
                            helper.assertTrue(
                                    StandEntityBarrageAbility.getBarrageHoldDurationMax(actor.stand())
                                            == BARRAGE_LONGEST_TICKS,
                                    scene.id + ": the longest barrage is "
                                            + StandEntityBarrageAbility.getBarrageHoldDurationMax(actor.stand())
                                            + " ticks");
                            waited = 0;
                            stage = 6;
                        }
                    }
                    case 6 -> {
                        // 20 ticks of hits: a second of the barrage.
                        if (cow.hurts.size() - seen >= 20) {
                            List<Hurt> second = cow.hurts.subList(seen, seen + 20);
                            float sum = 0;
                            for (Hurt hurt : second) {
                                expect("a tick of the barrage", hurt.amount(), BARRAGE_HIT * BARRAGE_HITS_A_TICK);
                                sum += hurt.amount();
                            }
                            scene.log("barrage a second=" + sum);
                            AbilityInput.keyRelease(GerMeleeGameTestScene.LMB, actor.user());
                            seen = cow.hurts.size();
                            stage = 7;
                        }
                    }
                    case 7 -> {
                        if (idle()) {
                            seen = cow.hurts.size();
                            actor.power().setStamina(actor.power().getMaxStamina());
                            scene.press(actor, cow, "heavy_charged", "heavy_charged",
                                    InputMethod.HOLD, GerMeleeGameTestScene.GUARD);
                            // let go at once: it charges on to the full and fires
                            AbilityInput.keyRelease(GerMeleeGameTestScene.GUARD, actor.user());
                            waited = 0;
                            stage = 8;
                        }
                    }
                    case 8 -> {
                        if (cow.hurts.size() > seen) {
                            float charged = cow.hurts.get(seen).amount();
                            scene.log("charged heavy=" + charged + ", power x 1.5=" + CHARGED_BY_POWER
                                    + ", 1.1 x the barrage's second=" + CHARGED_BY_BARRAGE);
                            expect("a full charged heavy, the larger of the two", charged,
                                    Math.max(CHARGED_BY_POWER, CHARGED_BY_BARRAGE));
                            return true;
                        }
                        helper.assertTrue(waited++ < 200, scene.id + ": the charged heavy never landed");
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }

            private boolean idle() {
                return actor.stand().getCurStandAction() == null;
            }

            private void expect(String what, float actual, float expected) {
                seen = cow.hurts.size();
                scene.log(what + "=" + actual);
                helper.assertTrue(Math.abs(actual - expected) < EPS,
                        scene.id + ": " + what + " deals " + actual + ", expected " + expected);
            }
        }.start();
    }

    /** Nothing of the Stand reads the old 10 / 15: its punches, its remote control and its weakening follow 7 / 10.5. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_basic_reach")
    public static void reachFollowsTheNewRange(GameTestHelper helper) {
        new Sequence(helper, "basic/reach", 100) {
            @Override
            boolean step() {
                Actor actor = scene.actor();
                StandStats stats = actor.type().getStandStats();
                helper.assertTrue(stats.power() == 20 && stats.speed() == 17.5 && stats.durability() == 20
                                && stats.precision() == 20 && stats.rangeEffective() == 7 && stats.rangeMax() == 10.5,
                        scene.id + ": the panel reads " + stats.power() + " " + stats.speed() + " "
                                + stats.rangeEffective() + "/" + stats.rangeMax() + " " + stats.durability() + " "
                                + stats.precision());
                helper.assertTrue(actor.stand().getEffectiveRange() == 7 && actor.stand().getMaxRange() == 10.5,
                        scene.id + ": the body's range is " + actor.stand().getEffectiveRange() + "/"
                                + actor.stand().getMaxRange());
                double reach = StandMeleeReach.baseReach(actor.stand(), actor.user());
                double limit = StandMeleeReach.maxReach(actor.stand(), actor.user(), false);
                double remote = StandMeleeReach.maxReach(actor.stand(), actor.user(), true);
                scene.log("reach=" + reach + " limit=" + limit + " remote=" + remote);
                helper.assertTrue(reach == REACH && limit == REACH_LIMIT && remote == REMOTE_LIMIT,
                        scene.id + ": a punch reaches " + reach + " at full strength and " + limit
                                + " at most, remote control " + remote);
                // The beam's reach is its own number, not the Stand's range.
                helper.assertTrue(GerLifeShotAbility.LEGACY_RANGE == 150.0D,
                        scene.id + ": Life Shot's reach moved to " + GerLifeShotAbility.LEGACY_RANGE);
                return true;
            }
        }.start();
    }
}
