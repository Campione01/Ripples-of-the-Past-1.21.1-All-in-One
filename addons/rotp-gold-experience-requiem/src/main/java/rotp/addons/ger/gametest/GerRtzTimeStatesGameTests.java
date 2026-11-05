package rotp.addons.ger.gametest;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.ability.GerReturnToZeroAbility;
import rotp.addons.ger.client.GerDisablerClientState;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.ObservedCow;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.ScenePlayer;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.addons.ger.network.GerDisablerPayloads;
import rotp.addons.ger.state.GerLivingState;
import rotp.addons.ger.state.GerRtzRewindService;
import rotp.core.api.time.EntityTimeRates;
import rotp.core.api.time.LevelTimeRates;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopState;

/**
 * Owner Q9: the holder of Gold Experience Requiem may press the manual Return to Zero in someone else's time stop, in
 * another Requiem's lockdown and in an acceleration, and it really runs. Its mark, stamina, cooldown and the Disabler's
 * refusal stay; what the press ends stays under rules G1, G2 and G3.
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerRtzTimeStatesGameTests {
    private static final short RTZ_KEY = 408;
    private static final int MARK_CHUNK_RADIUS = 2;
    private static final int STOP_CHUNK_RANGE = 8;
    private static final int OLDER_STOP = 77_101;
    private static final int LATER_STOP = 77_102;
    private static final int FAR_STOP = 77_103;
    private static final String ON_DISABLE = "jojo.message.action_condition.on_disable";
    private static final String USABLE = "usable";
    private static final String RTZ = GoldExperienceRequiemAddon.GER_RETURN_TO_ZERO_NAME;
    private static final String MARK = GoldExperienceRequiemAddon.GER_RETURN_TO_ZERO_MARK_NAME;
    private static final double SAME_PLACE = 1.0E-3D;
    // A lockdown lasts the ticks since the mark less the presentation's fades, so this leaves it some to watch.
    private static final int TICKS_MARK_TO_REWIND = 40;

    private static final ResourceLocation RATES = GoldExperienceRequiemAddon.id("gametest_time_states");
    private static final Set<UUID> HELD_BACK = ConcurrentHashMap.newKeySet();
    private static final float ACCELERATION = 4.0F;
    private static volatile boolean accelerating;
    private static boolean ratesRegistered;

    private GerRtzTimeStatesGameTests() {}

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_rtz_frozen_stand_out")
    public static void aFrozenHolderPressesReturnToZeroWithHisStandOut(GameTestHelper helper) {
        new FrozenPressSequence(helper, "rtz-states/frozen-stand-out", true).start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_rtz_frozen_stand_away")
    public static void aFrozenHolderPressesReturnToZeroWithHisStandAway(GameTestHelper helper) {
        new FrozenPressSequence(helper, "rtz-states/frozen-stand-away", false).start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_rtz_in_lockdown")
    public static void anotherRequiemsLockdownLetsReturnToZeroThroughAndNothingElse(GameTestHelper helper) {
        new LockdownPressSequence(helper).start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100, batch = "ger_rtz_under_disabler")
    public static void theDisablerStillRefusesReturnToZero(GameTestHelper helper) {
        new DisablerSequence(helper).start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_rtz_in_acceleration")
    public static void aHeldBackHolderPressesReturnToZeroInAnAcceleration(GameTestHelper helper) {
        new AccelerationSequence(helper).start();
    }

    private static String answer(ConditionCheck check) {
        if (check.isPositive()) {
            return USABLE;
        }
        Component warning = check.getWarning();
        if (warning == null) {
            return "refused without a message";
        }
        return warning.getContents() instanceof TranslatableContents text ? text.getKey() : warning.getString();
    }

    private static AbilityConditionCheck freshKey(GerMeleeGameTestScene scene, Power<?> power, String name) {
        Ability base = power.getAbility(name);
        scene.premise(base != null, "the moveset has " + name);
        AvailableAbilities moves = power.updateAvailableMoves();
        moves.update(power, power.getMoveset());
        AbilityConditionCheck key = moves.getContextVariationContainer(base);
        scene.premise(key != null && key.ability == base, name + " resolves to itself");
        return key;
    }

    private static String keyAnswer(GerMeleeGameTestScene scene, Actor actor, String name) {
        return answer(freshKey(scene, actor.power(), name).conditionCheck);
    }

    // as the server takes a press: the slot's check, then the press
    private static boolean pressReturnToZero(GerMeleeGameTestScene scene, Actor actor) {
        AbilityConditionCheck key = freshKey(scene, actor.power(), RTZ);
        boolean taken = AbilityInput.withConditionCheck(key, actor.user(), InputMethod.CLICK);
        if (taken) {
            AbilityInput.keyPress(RTZ_KEY, key.ability, actor.user(), null, InputMethod.CLICK, 0,
                    BufferingState.clickCanBuffer(), key.ability.getAbilityId());
        }
        return taken;
    }

    private static void asOwnClient(Actor actor, boolean ownClient) {
        ((ScenePlayer) actor.user()).ownClient = ownClient;
    }

    private static GerDisablerPayloads.StatePayload lastTold(Actor actor) {
        GerDisablerPayloads.StatePayload last = null;
        List<Object> received = actor.received();
        for (Object packet : received) {
            if (packet instanceof GerDisablerPayloads.StatePayload state && state.entityId() == actor.user().getId()) {
                last = state;
            }
        }
        return last;
    }

    private static void markFor(GerMeleeGameTestScene scene, Actor actor) {
        scene.premise(GerRtzRewindService.setMark(scene.level, actor.user(), MARK_CHUNK_RADIUS)
                        == GerRtzRewindService.Result.SUCCESS,
                "the mark is set");
    }

    private static boolean locked(LivingEntity entity) {
        return GoldExperienceRequiemAddon.livingState(entity).returnToZeroDisabled();
    }

    private static boolean samePlace(Vec3 a, Vec3 b) {
        return a.distanceTo(b) < SAME_PLACE;
    }

    /**
     * Someone else's stop, older than the mark, holds the holder; a later one comes, and the press comes one tick before
     * the automatic response would have. Both end, because they hold him (owner Q9), and he stands where he stood (a rewind
     * leaves its owner's place alone); a stop elsewhere, older than the mark, runs on (G1) and the mob stays where the
     * rewind put it; the press took its stamina and its cooldown; nobody stays locked. With his Stand away the press
     * summons it, as out of stopped time.
     */
    private static final class FrozenPressSequence extends Sequence {
        private final boolean standOut;
        private Actor actor;
        private ObservedCow mob;
        private Actor stopper;
        private Vec3 markedMob;
        private Vec3 standsAt;
        private boolean frozenAndMarked;
        private boolean pressed;
        private int pressedAt;

        FrozenPressSequence(GameTestHelper helper, String id, boolean standOut) {
            super(helper, id, 120);
            this.standOut = standOut;
        }

        private TimeStopState stops() {
            return scene.level.getData(ModDataAttachmentTypes.TIME_STOP.get());
        }

        private void stop(int id) {
            stop(id, new ChunkPos(actor.user().blockPosition()));
        }

        private void stop(int id, ChunkPos center) {
            scene.premise(stops().tryPutInstance(new TimeStopState.Instance(id, 300, 300,
                            center, STOP_CHUNK_RANGE, stopper.user().getId(),
                            "ger_rtz_time_states")),
                    "the stop " + id + " starts");
        }

        @Override
        boolean step() {
            if (actor == null) {
                actor = scene.actor();
                mob = scene.cow(scene.origin.add(2.0D, 0.0D, 0.0D));
                // a stop ends once no user with a time stop holds it
                stopper = scene.standUser(scene.origin.add(-2.0D, 0.0D, 0.0D), 0, ModStands.THE_WORLD.get(), false);
                scene.premise(stopper.power().getAbility("time_stop").isAbilityUnlocked(stopper.power()),
                        "The World's user has its time stop");
                markedMob = mob.position();
                standsAt = actor.user().position();
                scene.onClose(() -> {
                    stops().removeInstance(OLDER_STOP);
                    stops().removeInstance(LATER_STOP);
                    stops().removeInstance(FAR_STOP);
                    AbilityInput.keyRelease(RTZ_KEY, actor.user());
                });
                if (!standOut) {
                    actor.type().forceUnsummon(actor.user(), actor.power());
                }
                return false;
            }
            if (!frozenAndMarked) {
                frozenAndMarked = true;
                stop(OLDER_STOP);
                ChunkPos holderChunk = new ChunkPos(actor.user().blockPosition());
                stop(FAR_STOP, new ChunkPos(holderChunk.x + 3 * STOP_CHUNK_RANGE, holderChunk.z));
                scene.premise(stops().shouldFreeze(actor.user()), "the older stop freezes the holder");
                markFor(scene, actor);
                return false;
            }
            if (!pressed) {
                pressed = true;
                pressedAt = tick;
                press();
                return false;
            }
            helper.assertTrue(stops().getInstance(FAR_STOP).isPresent() && !stops().shouldFreeze(actor.user()),
                    scene.id + ": rule G1 leaves a stop elsewhere, older than the mark, running; running "
                            + stops().getInstance(FAR_STOP).isPresent() + ", the holder frozen "
                            + stops().shouldFreeze(actor.user()));
            helper.assertTrue(samePlace(actor.user().position(), standsAt) && samePlace(mob.position(), markedMob),
                    scene.id + ": the lockdown keeps them where the rewind leaves them; the holder is at "
                            + actor.user().position() + ", he stood at " + standsAt + ", the mob is at "
                            + mob.position() + ", marked at " + markedMob);
            if (GerRtzRewindService.hasActivePresentation(actor.user())) {
                return false;
            }
            helper.assertTrue(!locked(mob) && !locked(actor.power().getSummonedStandEntity()),
                    scene.id + ": the presentation is over, " + (tick - pressedAt) + " ticks after the press, and"
                            + " left the mob locked " + locked(mob) + ", the Stand locked "
                            + locked(actor.power().getSummonedStandEntity()));
            return true;
        }

        private void press() {
            mob.moveTo(markedMob.x + 1.0D, markedMob.y, markedMob.z);
            TimeStopState.repinOrReleaseOnServer(mob);
            stop(LATER_STOP);
            String key = keyAnswer(scene, actor, RTZ);
            float stamina = actor.power().getStamina();
            boolean taken = pressReturnToZero(scene, actor);
            helper.assertTrue(taken && USABLE.equals(key),
                    scene.id + ": owner Q9: a frozen holder may press Return to Zero; its slot answers " + key);
            StandEntity stand = actor.power().getSummonedStandEntity();
            helper.assertTrue(!GerRtzRewindService.hasMark(actor.user())
                            && GerRtzRewindService.hasActivePresentation(actor.user()),
                    scene.id + ": the press of a frozen holder rewinds at once; marked still "
                            + GerRtzRewindService.hasMark(actor.user()) + ", presentation "
                            + GerRtzRewindService.hasActivePresentation(actor.user()));
            helper.assertTrue(stops().getInstance(LATER_STOP).isEmpty() && stops().getInstance(OLDER_STOP).isEmpty()
                            && stops().getInstance(FAR_STOP).isPresent() && !stops().shouldFreeze(actor.user()),
                    scene.id + ": owner Q9: the press ends the stops that hold him and no other; later running "
                            + stops().getInstance(LATER_STOP).isPresent() + ", older running "
                            + stops().getInstance(OLDER_STOP).isPresent() + ", far running "
                            + stops().getInstance(FAR_STOP).isPresent());
            helper.assertTrue(samePlace(mob.position(), markedMob),
                    scene.id + ": the rewind puts the frozen mob back, at " + mob.position());
            helper.assertTrue(actor.power().getStamina() == stamina - GerReturnToZeroAbility.STAMINA_COST
                            && actor.power().getAbilityCooldown(RTZ) == GerReturnToZeroAbility.COOLDOWN_TICKS,
                    scene.id + ": Q4 keeps its price; stamina " + stamina + " became " + actor.power().getStamina()
                            + ", cooldown " + actor.power().getAbilityCooldown(RTZ));
            helper.assertTrue(stand != null && stand.isAddedToLevel()
                            && LivingComponentAction.getCurEntityAction(stand) == null,
                    scene.id + ": the press leaves the Stand out and idle: " + stand);
        }
    }

    /**
     * Holder A's Return to Zero locks holder B and his Stand down. B's slot and press pass while every other action stays
     * refused, on the server and on B's own client as told. B's press acts by B's own mark while A's presentation runs:
     * it locks A in turn. Both presentations end and leave nobody locked, and B's client is told.
     */
    private static final class LockdownPressSequence extends Sequence {
        private Actor holderA;
        private Actor holderB;
        private ObservedCow mob;
        private Vec3 markedMob;
        private int markedAt;
        private boolean rewound;
        private boolean pressed;
        private int clearAt = -1;

        LockdownPressSequence(GameTestHelper helper) {
            super(helper, "rtz-states/lockdown", 200);
        }

        @Override
        boolean step() {
            if (holderA == null) {
                setUp();
                return false;
            }
            if (!rewound) {
                if (tick - markedAt < TICKS_MARK_TO_REWIND) {
                    return false;
                }
                rewound = true;
                mob.moveTo(markedMob.x + 1.0D, markedMob.y, markedMob.z);
                scene.premise(GerRtzRewindService.rewind(scene.level, holderA.user(), holderA.user(), holderA.power(), 0)
                                == GerRtzRewindService.Result.SUCCESS,
                        "holder A's Return to Zero ran");
                scene.premise(locked(holderB.user()) && locked(holderB.stand()) && locked(mob) && !locked(holderA.user()),
                        "A's lockdown holds B, his Stand and the mob, not A");
                return false;
            }
            if (!pressed) {
                pressed = true;
                requireOnlyReturnToZeroPasses();
                requireOwnClientFollows();
                requirePressRuns();
                return false;
            }
            return requireEveryoneFree();
        }

        private void setUp() {
            holderA = scene.actor();
            holderB = scene.actor(scene.origin.add(3.0D, 0.0D, 0.0D), 0, null);
            mob = scene.cow(scene.origin.add(0.0D, 0.0D, 3.0D));
            markedMob = mob.position();
            scene.onClose(() -> {
                asOwnClient(holderB, false);
                GerDisablerClientState.setDisabled(holderB.user().getId(), false, false);
                AbilityInput.keyRelease(RTZ_KEY, holderB.user());
            });
            requireANewerLockdownMovesTheOlderOnesPin();
            markFor(scene, holderA);
            markFor(scene, holderB);
            markedAt = tick;
        }

        // A lockdown that outlasts a later rewind would pull the entity back to where it first held it.
        private void requireANewerLockdownMovesTheOlderOnesPin() {
            GerLivingState state = GoldExperienceRequiemAddon.livingState(mob);
            UUID older = UUID.randomUUID();
            UUID newer = UUID.randomUUID();
            Vec3 movedTo = markedMob.add(1.0D, 0.0D, 0.0D);
            state.startReturnToZeroDisable(older, mob, 100);
            mob.moveTo(movedTo.x, movedTo.y, movedTo.z);
            state.startReturnToZeroDisable(newer, mob, 5);
            state.clearReturnToZeroDisable(newer);
            state.enforceReturnToZeroDisable(mob);
            Vec3 heldAt = mob.position();
            state.clearReturnToZeroDisable(older);
            mob.moveTo(markedMob.x, markedMob.y, markedMob.z);
            helper.assertTrue(samePlace(heldAt, movedTo),
                    scene.id + ": the older lockdown pulled the entity back to " + heldAt + " from " + movedTo);
        }

        private void requireOnlyReturnToZeroPasses() {
            scene.premise(locked(holderB.user()), "A's lockdown still holds B a tick later");
            String rtz = keyAnswer(scene, holderB, RTZ);
            String mark = keyAnswer(scene, holderB, MARK);
            helper.assertTrue(USABLE.equals(rtz) && ON_DISABLE.equals(mark),
                    scene.id + ": owner Q9 lets only Return to Zero through another's lockdown; it answers " + rtz
                            + ", the mark " + mark);
        }

        private void requireOwnClientFollows() {
            GerDisablerPayloads.StatePayload told = lastTold(holderB);
            helper.assertTrue(told != null && told.disabled() && !told.byDisabler(),
                    scene.id + ": B's client is told of a lockdown that is not the Disabler: " + told);
            GerDisablerClientState.setDisabled(told.entityId(), told.disabled(), told.byDisabler());
            asOwnClient(holderB, true);
            try {
                String rtz = keyAnswer(scene, holderB, RTZ);
                String mark = keyAnswer(scene, holderB, MARK);
                helper.assertTrue(USABLE.equals(rtz) && ON_DISABLE.equals(mark),
                        scene.id + ": B's own client lets only Return to Zero through; it answers " + rtz
                                + ", the mark " + mark);
            }
            finally {
                asOwnClient(holderB, false);
            }
        }

        private void requirePressRuns() {
            helper.assertTrue(pressReturnToZero(scene, holderB),
                    scene.id + ": the lockdown refused B's Return to Zero press");
            helper.assertTrue(!GerRtzRewindService.hasMark(holderB.user())
                            && GerRtzRewindService.hasActivePresentation(holderB.user())
                            && GerRtzRewindService.hasActivePresentation(holderA.user()),
                    scene.id + ": B's press rewinds at once by his own mark beside A's presentation; B marked "
                            + GerRtzRewindService.hasMark(holderB.user()) + ", B's presentation "
                            + GerRtzRewindService.hasActivePresentation(holderB.user()) + ", A's "
                            + GerRtzRewindService.hasActivePresentation(holderA.user()));
            helper.assertTrue(locked(holderA.user()) && locked(mob) && samePlace(mob.position(), markedMob),
                    scene.id + ": B's rewind locks A in turn and keeps the mob where B marked it; A locked "
                            + locked(holderA.user()) + ", the mob locked " + locked(mob) + " at " + mob.position());
            String mark = keyAnswer(scene, holderB, MARK);
            helper.assertTrue(ON_DISABLE.equals(mark),
                    scene.id + ": A's lockdown still refuses B's other actions; the mark answers " + mark);
        }

        private boolean requireEveryoneFree() {
            boolean running = GerRtzRewindService.hasActivePresentation(holderA.user())
                    || GerRtzRewindService.hasActivePresentation(holderB.user());
            if (running) {
                return false;
            }
            helper.assertTrue(!locked(holderA.user()) && !locked(holderB.user()) && !locked(holderA.stand())
                            && !locked(holderB.stand()) && !locked(mob),
                    scene.id + ": both presentations are over and someone stays locked: A " + locked(holderA.user())
                            + ", B " + locked(holderB.user()) + ", A's Stand " + locked(holderA.stand())
                            + ", B's Stand " + locked(holderB.stand()) + ", the mob " + locked(mob));
            if (clearAt < 0) {
                clearAt = tick;
                return false;
            }
            if (tick - clearAt < 2) {
                return false;
            }
            GerDisablerPayloads.StatePayload told = lastTold(holderB);
            helper.assertTrue(told != null && !told.disabled() && !told.byDisabler(),
                    scene.id + ": B's client is told the lockdown is over: " + told);
            return true;
        }
    }

    /**
     * The Disabler is no time state (Q4): alone or on top of a lockdown it refuses Return to Zero with its message, and a
     * client told it refuses too, while a client told of a lockdown alone does not.
     */
    private static final class DisablerSequence extends Sequence {
        private Actor actor;
        private boolean told;

        DisablerSequence(GameTestHelper helper) {
            super(helper, "rtz-states/disabler", 60);
        }

        @Override
        boolean step() {
            if (actor == null) {
                actor = scene.actor();
                scene.onClose(() -> {
                    asOwnClient(actor, false);
                    GerDisablerClientState.setDisabled(actor.user().getId(), false, false);
                });
                markFor(scene, actor);
                GoldExperienceRequiemAddon.livingState(actor.user()).applyDisabler(UUID.randomUUID(), 40);
                return false;
            }
            if (!told) {
                told = true;
                requireRefused("the Disabler");
                GerDisablerPayloads.StatePayload last = lastTold(actor);
                helper.assertTrue(last != null && last.disabled() && last.byDisabler(),
                        scene.id + ": the client is told the Disabler holds it: " + last);
                requireOwnClient(last.disabled(), last.byDisabler(), ON_DISABLE);
                requireOwnClient(true, false, USABLE);
                GoldExperienceRequiemAddon.livingState(actor.user())
                        .startReturnToZeroDisable(UUID.randomUUID(), actor.user(), 2);
                requireRefused("the Disabler on top of a lockdown");
                return false;
            }
            return true;
        }

        private void requireRefused(String by) {
            String rtz = keyAnswer(scene, actor, RTZ);
            boolean taken = pressReturnToZero(scene, actor);
            helper.assertTrue(ON_DISABLE.equals(rtz) && !taken && GerRtzRewindService.hasMark(actor.user())
                            && !GerRtzRewindService.hasActivePresentation(actor.user()),
                    scene.id + ": " + by + " refuses Return to Zero; it answers " + rtz + ", the press taken " + taken
                            + ", marked still " + GerRtzRewindService.hasMark(actor.user()));
        }

        private void requireOwnClient(boolean disabled, boolean byDisabler, String expected) {
            GerDisablerClientState.setDisabled(actor.user().getId(), disabled, byDisabler);
            asOwnClient(actor, true);
            try {
                String rtz = keyAnswer(scene, actor, RTZ);
                helper.assertTrue(expected.equals(rtz),
                        scene.id + ": a client told disabled " + disabled + ", by the Disabler " + byDisabler
                                + " answers " + rtz + " for Return to Zero, expected " + expected);
            }
            finally {
                asOwnClient(actor, false);
            }
        }
    }

    /**
     * Made in Heaven's mode A in the core's terms: the level's time runs faster, a being held back has its Stand actions
     * slowed. Nothing refuses the press and the slowed action still reaches its perform within a few ticks. The day time
     * stays the acceleration's (G3).
     */
    private static final class AccelerationSequence extends Sequence {
        private static final long DAY_AT_MARK = 1_000L;
        private static final long DAY_AT_PRESS = 13_000L;
        // One action step takes 1 / rate ticks, and the float carry may need one more.
        private static final int MOST_TICKS_TO_PERFORM = (int) ACCELERATION + 2;
        private Actor actor;
        private boolean pressed;
        private int pressedAt;

        AccelerationSequence(GameTestHelper helper) {
            super(helper, "rtz-states/acceleration", 80);
        }

        private static synchronized void registerRates() {
            if (ratesRegistered) {
                return;
            }
            ratesRegistered = true;
            EntityTimeRates.register(RATES, (entity, channel) ->
                    channel == EntityTimeRates.Channel.STAND_ACTIONS && HELD_BACK.contains(entity.getUUID())
                            ? 1.0F / ACCELERATION
                            : EntityTimeRates.NO_RATE);
            LevelTimeRates.register(RATES, (level, channel) ->
                    channel == LevelTimeRates.Channel.TIME && accelerating
                            ? ACCELERATION
                            : LevelTimeRates.NO_RATE);
        }

        @Override
        boolean step() {
            if (actor == null) {
                registerRates();
                actor = scene.actor();
                long dayBefore = scene.level.getDayTime();
                scene.level.setDayTime(DAY_AT_MARK);
                markFor(scene, actor);
                HELD_BACK.add(actor.user().getUUID());
                accelerating = true;
                scene.onClose(() -> {
                    accelerating = false;
                    HELD_BACK.remove(actor.user().getUUID());
                    scene.level.setDayTime(dayBefore);
                    AbilityInput.keyRelease(RTZ_KEY, actor.user());
                });
                return false;
            }
            if (!pressed) {
                pressed = true;
                pressedAt = tick;
                scene.level.setDayTime(DAY_AT_PRESS);
                String key = keyAnswer(scene, actor, RTZ);
                helper.assertTrue(USABLE.equals(key) && pressReturnToZero(scene, actor),
                        scene.id + ": an acceleration does not refuse Return to Zero; it answers " + key);
                return false;
            }
            if (GerRtzRewindService.hasMark(actor.user())) {
                helper.assertTrue(tick - pressedAt <= MOST_TICKS_TO_PERFORM,
                        scene.id + ": Return to Zero has not run " + (tick - pressedAt)
                                + " ticks after its press at a Stand action rate of 1/" + ACCELERATION);
                return false;
            }
            helper.assertTrue(GerRtzRewindService.hasActivePresentation(actor.user()),
                    scene.id + ": the mark is spent but no presentation began");
            helper.assertTrue(scene.level.getDayTime() >= DAY_AT_PRESS,
                    scene.id + ": rule G3 leaves the day time to the acceleration, and it went back to "
                            + scene.level.getDayTime());
            return true;
        }
    }
}
