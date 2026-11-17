package rotp.addons.kingcrimson.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.api.EpitaphPredictions;
import rotp.addons.kingcrimson.epitaph.record.ActorKey;
import rotp.addons.kingcrimson.epitaph.record.EntityTrack;
import rotp.addons.kingcrimson.epitaph.record.RecordedOp;
import rotp.addons.kingcrimson.epitaph.replay.PuppetBinder.Binding;
import rotp.addons.kingcrimson.epitaph.sandbox.EpitaphSandbox;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalState;
import rotp.addons.kingcrimson.state.KingCrimsonTemporalStateService;
import rotp.core.api.entity.MobAiPause;
import rotp.core.api.time.event.RewindAppliedEvent;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopState;

/**
 * Where a prediction of Epitaph meets what else bends time (EPITAPH-ARCH section 4 and the rulings on a rewind and on a
 * stop of time): the amnesia after an erase, the decoy of an erase, a rewind by another player, a stop of time.
 */
@GameTestHolder(KingCrimsonAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class KingCrimsonEpitaphMeetingGameTests {
    private static final int PRESS_AT = 5;

    private KingCrimsonEpitaphMeetingGameTests() {}

    // The passive erase would answer a foreseen hit by itself: the erases of these tests are begun by the test.
    private static void withoutReflex(KingCrimsonEpitaphFixture fixture) {
        boolean reflex = KingCrimsonConfig.REFLEX_ERASE.get();
        fixture.base.onClose(() -> KingCrimsonConfig.REFLEX_ERASE.set(reflex));
        KingCrimsonConfig.REFLEX_ERASE.set(false);
    }

    // F4
    @GameTest(template = "empty", timeoutTicks = 400, batch = "kc_epitaph_meet_amnesia")
    public static void amnesiaWaitsForTheTrack(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-amn", 4, 24, 10);
        fixture.budget(0);
        withoutReflex(fixture);
        // The longest amnesia the setting allows, so that the short erase of this test leaves a pause to count.
        double seconds = KingCrimsonConfig.TIME_ERASE_AMNESIA_SECONDS.get();
        fixture.base.onClose(() -> KingCrimsonConfig.TIME_ERASE_AMNESIA_SECONDS.set(seconds));
        KingCrimsonConfig.TIME_ERASE_AMNESIA_SECONDS.set(10.0D);
        ServerPlayer user = fixture.user;
        Zombie zombie = fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 12.5D, 2.5D));
        // A creature no prediction holds: what the end of an erase does to any creature around.
        Pig pig = fixture.mob(EntityType.PIG, 12.5D, 7.5D);
        EpitaphSession[] running = new EpitaphSession[1];
        // 0 before the first erase, then: it runs, it has ended, the second one runs.
        int[] step = new int[1];
        int[] since = new int[1];
        int[] struck = new int[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            EpitaphSession session = fixture.press(null);
            session.foreseeAll();
            helper.assertTrue(EpitaphSessions.isOnTrack(zombie) && !EpitaphSessions.isOnTrack(pig), "Setup: the zombie that hunts the"
                    + " user is on no track, or the pig is on one");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            Binding held = session.binder().of(zombie);
            if (step[0] == 0 && session.passed() >= 6) {
                helper.assertTrue(KingCrimsonTemporalStateService.beginTimeErase(user, KingCrimsonTemporalState.EraseKind.REFLEX),
                        "Setup: no erase began");
                step[0] = 1;
                since[0] = 0;
            } else if (step[0] == 1 && ++since[0] == 10) {
                KingCrimsonTemporalStateService.terminate(user);
                struck[0] = MobAiPause.remainingTicks(pig);
                helper.assertTrue(struck[0] >= 4 && held != null, "Setup: the end of an erase of ten ticks left a pig a pause of "
                        + struck[0] + " ticks, or the zombie is off its track");
                step[0] = 2;
                since[0] = 0;
            } else if (step[0] == 2 && ++since[0] == 2) {
                // T5: its thinking is not paused while its track holds it; what struck it is kept for the end of the track.
                fixture.rule(held != null && !MobAiPause.isPaused(zombie) && held.pauseOwed() == struck[0], "two ticks after the end of"
                        + " the erase the zombie on its track is " + (MobAiPause.isPaused(zombie) ? "paused for "
                                + MobAiPause.remainingTicks(zombie) + " ticks" : "not paused") + ", and its track keeps "
                        + (held == null ? "nothing: it is off its track" : held.pauseOwed() + " ticks for its end")
                        + "; the pig beside it was struck with " + struck[0]);
            } else if (step[0] == 2 && session.passed() >= session.clock().foresight() - 8 && !session.ended()) {
                helper.assertTrue(KingCrimsonTemporalStateService.beginTimeErase(user, KingCrimsonTemporalState.EraseKind.REFLEX),
                        "Setup: no second erase began");
                // The first erase took its target from it when it ended: nothing turns it on the decoy as this one begins.
                helper.assertTrue(zombie.getTarget() == null, "Setup: as the second erase began the zombie on its track is after "
                        + zombie.getTarget());
                step[0] = 3;
            }
            if (!session.ended()) {
                return;
            }
            running[0] = null;
            helper.assertTrue(step[0] == 3 && KingCrimsonEraseFixture.erasing(user) && session.endedAs() == EpitaphEnd.COMPLETE,
                    "Setup: the prediction ended as " + session.endedAs() + " at step " + step[0] + ", or the second erase is over");
            List<KingCrimsonTimeEraseDecoyEntity> decoys = fixture.base.decoys(user);
            // T7
            fixture.rule(decoys.size() == 1 && zombie.getTarget() == decoys.get(0), "let go at the end of its track while the user"
                    + " erases, the zombie whose phantom hunted him to the end is after " + zombie.getTarget() + "; the erase has "
                    + decoys.size() + " decoys");
            // T5
            int left = MobAiPause.remainingTicks(zombie);
            fixture.rule(left >= struck[0] - 1 && left <= struck[0], "let go at the end of its track, the zombie stops thinking for "
                    + left + " ticks; the amnesia that struck it on its track was " + struck[0]);
            KingCrimsonTemporalStateService.terminate(user);
            fixture.finish("The amnesia waits for the track, and a creature let go during an erase is after the decoy");
        }));
    }

    // T5, the other side: a creature that is paused at the press
    @GameTest(template = "empty", timeoutTicks = 200, batch = "kc_epitaph_meet_paused")
    public static void aPauseAtThePressIsLivedByThePhantom(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-paused", 2, 24, 8);
        fixture.budget(0);
        Zombie zombie = fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 12.5D, 2.5D));
        EpitaphSession[] running = new EpitaphSession[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            helper.assertTrue(MobAiPause.pause(zombie, 12), "Setup: the zombie could not be paused");
            // It stands at the press: the speed it had would carry its copy on a little, and that is no thinking.
            zombie.setDeltaMovement(Vec3.ZERO);
            EpitaphSession session = fixture.press(null);
            session.foreseeAll();
            EntityTrack track = session.record().track(zombie.getId());
            helper.assertTrue(track != null && EpitaphSessions.isOnTrack(zombie) && track.lastTick() >= 26, "Setup: the zombie is on no"
                    + " track, or its track is shorter than 26 ticks");
            Vec3 start = KingCrimsonEpitaphFixture.on(track, 0);
            double stood = KingCrimsonEpitaphFixture.on(track, 10).distanceTo(start);
            double went = KingCrimsonEpitaphFixture.on(track, 26).distanceTo(KingCrimsonEpitaphFixture.on(track, 14));
            fixture.rule(stood < 0.05D && went > 0.5D, "paused for 12 ticks at the press, the phantom of the zombie went " + stood
                    + " blocks in its first ten ticks and " + went + " in the twelve after its pause");
            fixture.rule(!MobAiPause.isPaused(zombie), "on its track the zombie itself still carries the pause its phantom lives: "
                    + MobAiPause.remainingTicks(zombie) + " ticks");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null || !session.ended()) {
                return;
            }
            running[0] = null;
            fixture.rule(session.endedAs() == EpitaphEnd.COMPLETE && MobAiPause.remainingTicks(zombie) == 0, "the prediction ended as "
                    + session.endedAs() + ", and let go at the end of its track the zombie stops thinking for "
                    + MobAiPause.remainingTicks(zombie) + " ticks: the pause it had at the press was lived by its phantom");
            fixture.finish("A pause at the press is lived by the phantom");
        }));
    }

    // The ruling on question 7 (the D-left of the lane): a rewind ends the prediction for what it rewound, and the rest stands
    @GameTest(template = "empty", timeoutTicks = 400, batch = "kc_epitaph_meet_rewind")
    public static void aRewindEndsThePredictionForWhatItRewound(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-rewind", 4, 16, 8);
        fixture.phantomsUseStands();
        fixture.budget(0);
        withoutReflex(fixture);
        fixture.steadyHealth();
        ServerPlayer user = fixture.user;
        Zombie zombie = fixture.sturdy(fixture.hunter(EntityType.ZOMBIE, 8.5D, 2.5D));
        zombie.getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(1.0D);
        EpitaphSession[] running = new EpitaphSession[1];
        RecordedOp.StandSummon[] foreseen = new RecordedOp.StandSummon[1];
        int[] begins = {-1};
        int[] firstHit = {-1};
        boolean[] looked = new boolean[1];
        // 0 before the first rewind, 1 after it, 2 after the second.
        int[] step = new int[1];
        int[] landed = new int[1];
        float[] health = new float[2];
        fixture.listen(LivingIncomingDamageEvent.class, event -> {
            // A hit of the record, as it is carried out, on one of the two after the first rewind.
            if (step[0] >= 1 && EpitaphSandbox.replaying() && (event.getEntity() == zombie || event.getEntity() == user)) {
                landed[0]++;
            }
        });
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            // His Stand is in: his phantom will call its own out, and the record does the same for him.
            KingCrimsonAddon.KING_CRIMSON.get().forceUnsummon(user, fixture.power);
            EpitaphSession session = fixture.press(null);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            session.foreseeAll();
            ActorKey own = ActorKey.of(user);
            ActorKey hunter = ActorKey.of(zombie);
            foreseen[0] = KingCrimsonEpitaphFixture.ops(session, RecordedOp.StandSummon.class).stream()
                    .filter(op -> op.user().equals(own)).findFirst().orElse(null);
            helper.assertTrue(foreseen[0] != null, "Setup: the phantom of the user summoned no Stand in the room");
            for (RecordedOp.Action op : KingCrimsonEpitaphFixture.ops(session, RecordedOp.Action.class)) {
                if (begins[0] < 0 && op.actor().equals(foreseen[0].stand()) && op.state().hasAction()) {
                    begins[0] = KingCrimsonEpitaphFixture.tickOf(session, op);
                }
            }
            firstHit[0] = KingCrimsonEpitaphFixture.ops(session, RecordedOp.Damage.class).stream()
                    .filter(hit -> KingCrimsonEpitaphFixture.tickOf(session, hit) > 4 && (hit.victim().equals(hunter)
                            || hit.victim().equals(own))).mapToInt(hit -> KingCrimsonEpitaphFixture.tickOf(session, hit)).min().orElse(-1);
            helper.assertTrue(begins[0] > 6 && firstHit[0] > 4, "Setup: the Stand of the phantom of the user began its first action at"
                    + " tick " + begins[0] + ", and the first hit between the two after tick 4 is at tick " + firstHit[0]);
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            helper.assertTrue(!session.ended(), "Setup: the prediction ended as " + session.endedAs() + " before both rewinds were made");
            if (step[0] == 0 && session.passed() >= 3) {
                helper.assertTrue(EpitaphSessions.isOnTrack(zombie) && EpitaphPredictions.isForcedWalking(user)
                        && zombie.distanceTo(user) > 3.0F, "Setup: at tick 3 the zombie is off its track, the user is not walked, or"
                                + " they stand " + zombie.distanceTo(user) + " blocks apart");
                // What a rewind posts when it has put things back: here it went over the zombie alone.
                RewindAppliedEvent.post(null, fixture.level, fixture.level.getGameTime() - 20L, zombie.getBoundingBox().inflate(1.0D),
                        KingCrimsonAddon.id("a_rewind_of_a_test"));
                step[0] = 1;
                health[0] = zombie.getHealth();
                health[1] = user.getHealth();
                fixture.rule(!EpitaphSessions.isOnTrack(zombie) && zombie.isAlive(), "a rewind went over the zombie of the prediction,"
                        + " and it is " + (EpitaphSessions.isOnTrack(zombie) ? "still on its track" : "off its track"));
                fixture.rule(!session.userVoid() && EpitaphPredictions.isForcedWalking(user), "a rewind over the zombie alone let the"
                        + " user of the prediction go: the rest stands");
                if (session.userVoid()) {
                    // The rule above says it; without him on his track nothing of what follows can be looked at.
                    running[0] = null;
                    session.end(EpitaphEnd.CANCELLED);
                    fixture.finish("A rewind ends the prediction for what it rewound, and the rest stands");
                    return;
                }
                // Let go, it would come at him by itself: that is not what is looked at here.
                zombie.setNoAi(true);
                return;
            }
            if (step[0] != 1) {
                return;
            }
            if (!looked[0] && session.passed() > firstHit[0]) {
                looked[0] = true;
                // Nothing foreseen of the zombie or for it happened since the rewind went over it.
                fixture.rule(landed[0] == 0 && zombie.getHealth() == health[0] && user.getHealth() == health[1], "past tick " + firstHit[0]
                        + ", where the record has a hit between the two, " + landed[0] + " hits of the record landed on them since the"
                        + " rewind; the zombie has " + zombie.getHealth() + " of " + health[0] + " health, the user " + user.getHealth()
                        + " of " + health[1]);
                fixture.rule(session.record().skipped() > 0, "no op of the record was left out after the rewind over the zombie");
            }
            StandEntity stand = fixture.power.getSummonedStandEntity();
            LivingComponentAction slot = stand != null ? LivingComponentAction.getComponent(stand) : null;
            boolean acting = slot != null && slot.getAction() != null && !slot.getAction().isOver() && session.binder().of(stand) != null;
            helper.assertTrue(session.passed() < session.clock().foresight() - 2, "Setup: to the end of the prediction the Stand the"
                    + " record summoned for the user was never found in the middle of an action after tick " + firstHit[0]);
            if (!looked[0] || session.passed() <= begins[0] || !acting) {
                return;
            }
            helper.assertTrue(stand.getId() == foreseen[0].stand().id(), "Setup: the Stand of the user is not the one the record summoned");
            // The second rewind: over the user and the Stand the record gave him, in the middle of an action of the record.
            RewindAppliedEvent.post(null, fixture.level, fixture.level.getGameTime() - 20L, user.getBoundingBox().inflate(4.0D),
                    KingCrimsonAddon.id("a_rewind_of_a_test"));
            step[0] = 2;
            running[0] = null;
            fixture.rule(session.userVoid() && !EpitaphPredictions.isForcedWalking(user), "a rewind went over the user, and he is "
                    + (EpitaphPredictions.isForcedWalking(user) ? "still walked" : "let go") + ", his part "
                    + (session.userVoid() ? "void" : "not void"));
            boolean idle = slot.getAction() == null || slot.getAction().isOver();
            fixture.rule(fixture.power.getSummonedStandEntity() == stand && !stand.isRemoved() && session.binder().of(stand) == null && idle,
                    "after the rewind over the user the Stand the record summoned for him is " + (stand.isRemoved() ? "gone" : "out")
                            + ", " + (session.binder().of(stand) == null ? "off its track" : "still on its track") + ", and "
                            + (idle ? "performs nothing" : "still performs an action of the record"));
            if (!session.ended()) {
                session.end(EpitaphEnd.CANCELLED);
            }
            fixture.finish("A rewind ends the prediction for what it rewound, and the rest stands");
        }));
    }

    // A19 with a Stand in the middle of an action of the record: a stop of time holds the action with its prediction
    @GameTest(template = "empty", timeoutTicks = 400, batch = "kc_epitaph_meet_stop")
    public static void aStopOfTimeHoldsAMirroredActionWithItsPrediction(GameTestHelper helper) {
        KingCrimsonEpitaphFixture fixture = new KingCrimsonEpitaphFixture(helper, "kc-ep-stopact", 4, 16, 8);
        fixture.phantomsUseStands();
        fixture.budget(0);
        withoutReflex(fixture);
        fixture.steadyHealth();
        ServerPlayer user = fixture.user;
        // A zombie that stands and does nothing, in reach of the Stand of the user, with health to spare.
        Zombie dummy = fixture.sturdy(fixture.mob(EntityType.ZOMBIE, 4.5D, 2.5D));
        dummy.setNoAi(true);
        EpitaphSession[] running = new EpitaphSession[1];
        StandEntity[] out = new StandEntity[1];
        Runnable[] resume = new Runnable[1];
        EntityActionInstance[] held = new EntityActionInstance[1];
        ActionPhase[] phase = new ActionPhase[1];
        float[] phaseTick = new float[1];
        int[] begins = {-1};
        // 0 before the stop; then the server ticks since it began.
        int[] stopped = new int[1];
        int[] passedThen = new int[1];
        helper.runAfterDelay(PRESS_AT, () -> fixture.guarded(() -> {
            out[0] = fixture.power.getSummonedStandEntity();
            user.getRandom().setSeed(77_900L);
            EpitaphSession session = fixture.press(dummy);
            KingCrimsonEpitaphFixture.acceptTeleport(user);
            session.foreseeAll();
            ActorKey key = ActorKey.of(out[0]);
            for (RecordedOp.Action op : KingCrimsonEpitaphFixture.ops(session, RecordedOp.Action.class)) {
                if (begins[0] < 0 && op.actor().equals(key) && op.state().hasAction()) {
                    begins[0] = KingCrimsonEpitaphFixture.tickOf(session, op);
                }
            }
            helper.assertTrue(begins[0] > 0, "Setup: the Stand of the phantom of the user began no action in the room");
            running[0] = session;
        }));
        helper.onEachTick(() -> fixture.guarded(() -> {
            EpitaphSession session = running[0];
            if (session == null) {
                return;
            }
            EntityActionInstance now = LivingComponentAction.getComponent(out[0]).getAction();
            if (stopped[0] == 0) {
                helper.assertTrue(!session.ended(), "Setup: the prediction ended as " + session.endedAs() + " before the Stand of the"
                        + " user was found in the middle of an action of the record");
                if (session.passed() <= begins[0] || now == null || now.isOver()) {
                    return;
                }
                resume[0] = fixture.stopTime(new ChunkPos(user.blockPosition()));
                helper.assertTrue(TimeStopState.shouldFreezeOnServer(out[0]) || TimeStopState.shouldFreezeOnServer(user),
                        "Setup: the stopped time holds neither the user nor his Stand");
                held[0] = now;
                phase[0] = now.getPhase();
                phaseTick[0] = now.getPhaseTick();
                passedThen[0] = session.passed();
                stopped[0] = 1;
                return;
            }
            stopped[0]++;
            if (stopped[0] <= 12) {
                fixture.rule(now == held[0] && !now.isOver() && now.getPhase() == phase[0] && now.getPhaseTick() == phaseTick[0]
                        && session.passed() == passedThen[0], "in stopped time the action the record put on the Stand of the user went"
                                + " from " + phase[0] + " " + phaseTick[0] + " to " + (now == null ? "nothing" : now.getPhase() + " "
                                        + now.getPhaseTick() + (now == held[0] ? "" : ", another action") + (now.isOver() ? ", over" : ""))
                                + ", and the bodies from tick " + passedThen[0] + " to " + session.passed());
                if (stopped[0] == 12) {
                    resume[0].run();
                }
                return;
            }
            if (stopped[0] < 16 && !session.ended()) {
                return;
            }
            running[0] = null;
            helper.assertTrue(!session.ended() && session.passed() > passedThen[0], "Setup: four ticks after time went on the prediction"
                    + " ended as " + session.endedAs() + ", or its bodies are still at tick " + session.passed());
            EntityTrack track = session.record().track(out[0].getId());
            EntityTrack.ActionMark mark = track != null ? track.mark(session.passed(), LivingComponentAction.MAIN_SLOT) : null;
            boolean idle = now == null || now.isOver();
            fixture.rule(mark == null ? idle : !idle && now.getPhase() == mark.phase() && (int) now.getPhaseTick() == mark.phaseTick(),
                    "after the stop of time, at tick " + session.passed() + " the record has the Stand of the user at "
                            + (mark == null ? "nothing" : mark.phase() + " " + mark.phaseTick()) + ", and the real Stand performs "
                            + (idle ? "nothing" : now.getPhase() + " " + now.getPhaseTick()));
            session.end(EpitaphEnd.CANCELLED);
            fixture.finish("A stop of time holds a mirrored action with its prediction");
        }));
    }
}
