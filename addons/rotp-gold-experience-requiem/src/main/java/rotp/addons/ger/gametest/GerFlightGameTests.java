package rotp.addons.ger.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.GoldExperienceRequiemGameEvents;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.addons.ger.state.GerPlayerState;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.init.ModItems;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;

/**
 * The flight of a Requiem user, which is kept as it is: Resolve gives a GER user mayfly (the state remembers it was given)
 * and its end takes it back, except from a creative or spectator player. The evolution that earns the Resolve holds the
 * user up with levitation and leaves him on slow falling, whether it completes or is cancelled. The players are ticked
 * with doTick, the call of their connection's tick, which is where the player tick event comes from.
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerFlightGameTests {
    private static final int SHORT_RESOLVE_TICKS = 6;
    private static final int LONG_RESOLVE_TICKS = 400;
    private static final int SETTLE_TICKS = 3;
    private static final int SLOW_FALLING_TICKS = 100;
    private static final int EVOLUTION_RESOLVE_AMPLIFIER = 5;
    private static final int TICKS_AFTER_EVOLUTION_START = 3;
    // a few ticks after a duration is set, which the tick it is set in has already counted down
    private static final int DURATION_MARGIN = 10;

    private GerFlightGameTests() {}

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100, batch = "ger_flight_grant")
    public static void aSurvivalRequiemUserWithResolveGetsMayfly(GameTestHelper helper) {
        new Sequence(helper, "flight/grant", 40) {
            private Actor actor;
            private int settled;

            @Override
            boolean step() {
                if (actor == null) {
                    actor = scene.actor();
                    ServerPlayer user = actor.user();
                    scene.premise(GoldExperienceRequiemAddon.isGerUser(user)
                                    && user.gameMode.getGameModeForPlayer() == GameType.SURVIVAL,
                            "a survival Requiem user");
                    user.doTick();
                    scene.premise(!user.getAbilities().mayfly && !state(user).flightGranted(),
                            "no Resolve, no flight");
                    resolve(user, LONG_RESOLVE_TICKS);
                    return false;
                }
                ServerPlayer user = actor.user();
                user.doTick();
                helper.assertTrue(user.hasEffect(ModStatusEffects.RESOLVE)
                                && user.getAbilities().mayfly && state(user).flightGranted(),
                        scene.id + ": a Requiem user with Resolve must be able to fly and the state must say it was"
                                + " granted; mayfly " + user.getAbilities().mayfly + ", flightGranted "
                                + state(user).flightGranted());
                return ++settled >= SETTLE_TICKS;
            }
        }.start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100, batch = "ger_flight_ends")
    public static void flyingAndMayflyEndWithResolve(GameTestHelper helper) {
        new ResolveEndsSequence(helper, "flight/ends", null).start();
    }

    /**
     * The same end, seen at the add-on's own tick event: the player tick's tail clears flying of a player who may not fly
     * (ServerPlayer.doTick) whatever the add-on does, so the event is posted by itself here.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100, batch = "ger_flight_ends_at_event")
    public static void theEndOfResolveClearsFlyingAndMayflyAtTheAddonsTickEvent(GameTestHelper helper) {
        new Sequence(helper, "flight/ends-at-event", 40) {
            private Actor actor;

            @Override
            boolean step() {
                if (actor == null) {
                    actor = scene.actor();
                    resolve(actor.user(), LONG_RESOLVE_TICKS);
                    return false;
                }
                ServerPlayer user = actor.user();
                user.doTick();
                scene.premise(user.getAbilities().mayfly && state(user).flightGranted(), "Resolve granted flight");
                user.getAbilities().flying = true;
                user.removeEffect(ModStatusEffects.RESOLVE);
                NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(user));
                helper.assertTrue(!user.getAbilities().mayfly && !user.getAbilities().flying
                                && !state(user).flightGranted(),
                        scene.id + ": without Resolve the add-on's tick must take flying and mayfly back and forget the"
                                + " grant; mayfly " + user.getAbilities().mayfly + ", flying "
                                + user.getAbilities().flying + ", flightGranted " + state(user).flightGranted());
                return true;
            }
        }.start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100, batch = "ger_flight_creative")
    public static void aCreativeRequiemUserKeepsMayflyWhenResolveEnds(GameTestHelper helper) {
        new ResolveEndsSequence(helper, "flight/creative", GameType.CREATIVE).start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100, batch = "ger_flight_spectator")
    public static void aSpectatorRequiemUserKeepsMayflyWhenResolveEnds(GameTestHelper helper) {
        new ResolveEndsSequence(helper, "flight/spectator", GameType.SPECTATOR).start();
    }

    /** A control Requiem user in the same scene shows that the ticks do grant flight, so no flight elsewhere is no accident. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100, batch = "ger_flight_other_stand")
    public static void resolveWithoutTheRequiemStandGivesNoFlight(GameTestHelper helper) {
        new Sequence(helper, "flight/other-stand", 40) {
            private Actor control;
            private Actor otherStand;
            private ServerPlayer noStand;
            private int settled;

            @Override
            boolean step() {
                if (control == null) {
                    control = scene.actor();
                    otherStand = scene.standUser(scene.origin.add(2.0D, 0.0D, 0.0D), 0, ModStands.THE_WORLD.get(), false);
                    noStand = scene.client(scene.origin.add(-2.0D, 0.0D, 0.0D)).player();
                    StandPower bare = StandPower.get(noStand);
                    scene.premise(!GoldExperienceRequiemAddon.isGerUser(otherStand.user())
                                    && otherStand.power().hasPower()
                                    && (bare == null || !bare.hasPower()),
                            "one user of another Stand and one player with no Stand");
                    for (ServerPlayer player : new ServerPlayer[] {control.user(), otherStand.user(), noStand}) {
                        resolve(player, LONG_RESOLVE_TICKS);
                    }
                    return false;
                }
                control.user().doTick();
                otherStand.user().doTick();
                for (ServerPlayer player : new ServerPlayer[] {otherStand.user(), noStand}) {
                    helper.assertTrue(player.hasEffect(ModStatusEffects.RESOLVE)
                                    && !player.getAbilities().mayfly && !state(player).flightGranted(),
                            scene.id + ": Resolve alone gives no flight to a player whose Stand is not Requiem; "
                                    + player.getGameProfile().getName() + " has mayfly " + player.getAbilities().mayfly
                                    + ", flightGranted " + state(player).flightGranted());
                }
                helper.assertTrue(control.user().getAbilities().mayfly && state(control.user()).flightGranted(),
                        scene.id + ": the control Requiem user with Resolve does not fly, so the ticks prove nothing");
                return ++settled >= SETTLE_TICKS;
            }
        }.start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100, batch = "ger_flight_evolution_start")
    public static void theBeetleArrowStartsTheEvolutionWithLevitation(GameTestHelper helper) {
        new Sequence(helper, "flight/evolution-start", 40) {
            private Actor actor;
            private int ticks;

            @Override
            boolean step() {
                if (actor == null) {
                    actor = evolver(scene);
                    useArrow(helper, scene, actor);
                    ServerPlayer user = actor.user();
                    MobEffectInstance levitation = user.getEffect(MobEffects.LEVITATION);
                    helper.assertTrue(levitation != null
                                    && levitation.getDuration() == GoldExperienceRequiemGameEvents.EVOLUTION_TICKS
                                    && state(user).isEvolving()
                                    && state(user).evolutionRemaining() == GoldExperienceRequiemGameEvents.EVOLUTION_TICKS
                                    && user.getMainHandItem().isEmpty(),
                            scene.id + ": the arrow must start the evolution and lift the user with levitation for "
                                    + GoldExperienceRequiemGameEvents.EVOLUTION_TICKS + " ticks; levitation " + levitation
                                    + ", evolving " + state(user).isEvolving() + ", arrow left "
                                    + user.getMainHandItem());
                    return false;
                }
                ServerPlayer user = actor.user();
                user.doTick();
                helper.assertTrue(state(user).isEvolving() && user.hasEffect(MobEffects.LEVITATION),
                        scene.id + ": the user must still be evolving and held up; evolving " + state(user).isEvolving()
                                + ", levitation " + user.hasEffect(MobEffects.LEVITATION));
                return ++ticks >= TICKS_AFTER_EVOLUTION_START;
            }
        }.start();
    }

    /**
     * The counter is shortened to its last ticks, so that levitation, which would run out by itself in the same tick the
     * full evolution completes, is still there for the completion to take away.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100, batch = "ger_flight_evolution_complete")
    public static void completingTheEvolutionRemovesLevitationAndGivesSlowFalling(GameTestHelper helper) {
        new Sequence(helper, "flight/evolution-complete", 40) {
            private Actor actor;
            private int ticks;

            @Override
            boolean step() {
                if (actor == null) {
                    actor = evolver(scene);
                    useArrow(helper, scene, actor);
                    ServerPlayer user = actor.user();
                    scene.premise(user.hasEffect(MobEffects.LEVITATION) && state(user).isEvolving(), "evolving");
                    state(user).beginEvolution(2);
                    return false;
                }
                ServerPlayer user = actor.user();
                user.doTick();
                if (++ticks < 2) {
                    helper.assertTrue(state(user).isEvolving() && user.hasEffect(MobEffects.LEVITATION),
                            scene.id + ": the evolution ended a tick early");
                    return false;
                }
                requireCompleted(helper, scene, actor);
                return true;
            }
        }.start();
    }

    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100, batch = "ger_flight_evolution_cancel")
    public static void cancellingTheEvolutionRemovesLevitationAndGivesSlowFalling(GameTestHelper helper) {
        new Sequence(helper, "flight/evolution-cancel", 40) {
            private Actor actor;

            @Override
            boolean step() {
                ServerPlayer user = actor != null ? actor.user() : null;
                if (actor == null) {
                    actor = evolver(scene);
                    useArrow(helper, scene, actor);
                    user = actor.user();
                    scene.premise(user.hasEffect(MobEffects.LEVITATION) && state(user).isEvolving(), "evolving");
                    // a user whose Stand is no longer Gold Experience has no evolution to finish
                    StandPower power = actor.power();
                    scene.premise(StandPowerTransitions.replace(power,
                                            power.getStandInstance().orElseThrow().getStandId(),
                                            new StandInstance(ModStands.THE_WORLD.get())).applied(),
                            "the Stand is replaced");
                    return false;
                }
                user.doTick();
                MobEffectInstance slowFalling = user.getEffect(MobEffects.SLOW_FALLING);
                helper.assertTrue(!state(user).isEvolving() && !user.hasEffect(MobEffects.LEVITATION)
                                && slowFalling != null && slowFalling.getDuration() > SLOW_FALLING_TICKS - DURATION_MARGIN
                                && slowFalling.getDuration() <= SLOW_FALLING_TICKS
                                && !user.hasEffect(ModStatusEffects.RESOLVE),
                        scene.id + ": a cancelled evolution must end with no levitation, slow falling for "
                                + SLOW_FALLING_TICKS + " ticks and no Resolve; evolving " + state(user).isEvolving()
                                + ", levitation " + user.hasEffect(MobEffects.LEVITATION) + ", slow falling "
                                + slowFalling + ", Resolve " + user.hasEffect(ModStatusEffects.RESOLVE));
                return true;
            }
        }.start();
    }

    /** The whole of it: the user is held up for every one of its ticks, and the Resolve it ends with gives him flight. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 500, batch = "ger_flight_evolution_full")
    public static void aFullEvolutionHoldsTheUserUpAndEndsInResolveThatLetsHimFly(GameTestHelper helper) {
        new Sequence(helper, "flight/evolution-full", GoldExperienceRequiemGameEvents.EVOLUTION_TICKS + 60) {
            private Actor actor;
            private int ticks;
            private boolean completed;

            @Override
            boolean step() {
                if (actor == null) {
                    actor = evolver(scene);
                    useArrow(helper, scene, actor);
                    return false;
                }
                ServerPlayer user = actor.user();
                user.doTick();
                if (completed) {
                    helper.assertTrue(user.getAbilities().mayfly && state(user).flightGranted(),
                            scene.id + ": the Requiem user the evolution made, with its Resolve, must be able to fly;"
                                    + " mayfly " + user.getAbilities().mayfly + ", flightGranted "
                                    + state(user).flightGranted());
                    return true;
                }
                ticks++;
                if (state(user).isEvolving()) {
                    helper.assertTrue(ticks < GoldExperienceRequiemGameEvents.EVOLUTION_TICKS
                                    && user.hasEffect(MobEffects.LEVITATION),
                            scene.id + ": the evolution must hold the user up until its last tick; tick " + ticks
                                    + ", levitation " + user.hasEffect(MobEffects.LEVITATION));
                    return false;
                }
                helper.assertTrue(ticks == GoldExperienceRequiemGameEvents.EVOLUTION_TICKS,
                        scene.id + ": the evolution ended after " + ticks + " ticks");
                requireCompleted(helper, scene, actor);
                completed = true;
                return false;
            }
        }.start();
    }

    private static GerPlayerState state(ServerPlayer player) {
        return GoldExperienceRequiemAddon.playerState(player);
    }

    private static void resolve(ServerPlayer player, int ticks) {
        player.addEffect(new MobEffectInstance(ModStatusEffects.RESOLVE, ticks, 0, false, false, true));
    }

    /** A Gold Experience user at max Resolve level with the beetle arrow in hand. */
    private static Actor evolver(GerMeleeGameTestScene scene) {
        Actor actor = scene.standUser(scene.origin, 0, ModStands.GOLD_EXPERIENCE.get(), false);
        StandPower power = actor.power();
        scene.premise(power.getPowerType() == ModStands.GOLD_EXPERIENCE.get()
                        && power.getResolveLevel() >= power.getMaxResolveLevel(),
                "a Gold Experience user at max Resolve level");
        // the evolution leaves the Requiem Stand out, which the scene does not know to put away
        scene.onClose(() -> {
            if (power.isSummoned()) {
                power.getPowerType().forceUnsummon(actor.user(), power);
            }
        });
        actor.user().setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.STAND_ARROW_BEETLE.get()));
        return actor;
    }

    private static void useArrow(GameTestHelper helper, GerMeleeGameTestScene scene, Actor actor) {
        PlayerInteractEvent.RightClickItem use =
                new PlayerInteractEvent.RightClickItem(actor.user(), InteractionHand.MAIN_HAND);
        NeoForge.EVENT_BUS.post(use);
        helper.assertTrue(use.isCanceled() && use.getCancellationResult() == InteractionResult.SUCCESS,
                scene.id + ": the beetle arrow of a ready Gold Experience user must be taken: canceled "
                        + use.isCanceled() + ", result " + use.getCancellationResult());
    }

    /** Right after the tick the evolution completed in. */
    private static void requireCompleted(GameTestHelper helper, GerMeleeGameTestScene scene, Actor actor) {
        ServerPlayer user = actor.user();
        MobEffectInstance slowFalling = user.getEffect(MobEffects.SLOW_FALLING);
        MobEffectInstance resolve = user.getEffect(ModStatusEffects.RESOLVE);
        helper.assertTrue(!state(user).isEvolving()
                        && actor.power().getPowerType() == GoldExperienceRequiemAddon.GER_STAND.get(),
                scene.id + ": the evolution must be over and the Stand Requiem; evolving " + state(user).isEvolving()
                        + ", Stand " + actor.power().getPowerType());
        helper.assertTrue(!user.hasEffect(MobEffects.LEVITATION)
                        && slowFalling != null && slowFalling.getDuration() > SLOW_FALLING_TICKS - DURATION_MARGIN
                        && slowFalling.getDuration() <= SLOW_FALLING_TICKS,
                scene.id + ": the completed evolution must take levitation away and give slow falling for "
                        + SLOW_FALLING_TICKS + " ticks; levitation " + user.hasEffect(MobEffects.LEVITATION)
                        + ", slow falling " + slowFalling);
        helper.assertTrue(resolve != null && resolve.getAmplifier() == EVOLUTION_RESOLVE_AMPLIFIER
                        && resolve.getDuration() > GoldExperienceRequiemGameEvents.EVOLUTION_RESOLVE_TICKS - DURATION_MARGIN
                        && resolve.getDuration() <= GoldExperienceRequiemGameEvents.EVOLUTION_RESOLVE_TICKS,
                scene.id + ": the completed evolution must give Resolve level " + EVOLUTION_RESOLVE_AMPLIFIER + " for "
                        + GoldExperienceRequiemGameEvents.EVOLUTION_RESOLVE_TICKS + " ticks; Resolve " + resolve);
    }

    /**
     * A survival Requiem user is given flight by a short Resolve, which then runs out. A survival user loses mayfly and
     * flying with it; one who has turned creative or spectator since keeps both, and the state forgets the grant in
     * every case.
     */
    private static final class ResolveEndsSequence extends Sequence {
        private final GameType later;
        private Actor actor;
        private boolean granted;

        ResolveEndsSequence(GameTestHelper helper, String id, GameType later) {
            super(helper, id, 40);
            this.later = later;
        }

        @Override
        boolean step() {
            if (actor == null) {
                actor = scene.actor();
                scene.premise(actor.user().gameMode.getGameModeForPlayer() == GameType.SURVIVAL,
                        "a survival Requiem user");
                resolve(actor.user(), SHORT_RESOLVE_TICKS);
                return false;
            }
            ServerPlayer user = actor.user();
            user.doTick();
            if (!granted) {
                granted = true;
                scene.premise(user.getAbilities().mayfly && state(user).flightGranted(), "Resolve granted flight");
                if (later != null) {
                    scene.premise(user.gameMode.changeGameModeForPlayer(later)
                                    && user.getAbilities().mayfly,
                            "the user is now " + later);
                }
                user.getAbilities().flying = true;
                user.onUpdateAbilities();
                scene.premise(state(user).flightGranted(), "the state still holds the grant");
                return false;
            }
            if (user.hasEffect(ModStatusEffects.RESOLVE)) {
                helper.assertTrue(user.getAbilities().mayfly && user.getAbilities().flying,
                        scene.id + ": flight must last as long as Resolve does");
                return false;
            }
            boolean keeps = later != null;
            helper.assertTrue(!state(user).flightGranted()
                            && user.getAbilities().mayfly == keeps && user.getAbilities().flying == keeps,
                    scene.id + ": Resolve has ended; the user (" + (keeps ? later : GameType.SURVIVAL) + ") must "
                            + (keeps ? "keep" : "lose") + " mayfly and flying and the grant must be forgotten; mayfly "
                            + user.getAbilities().mayfly + ", flying " + user.getAbilities().flying
                            + ", flightGranted " + state(user).flightGranted());
            return true;
        }
    }
}
