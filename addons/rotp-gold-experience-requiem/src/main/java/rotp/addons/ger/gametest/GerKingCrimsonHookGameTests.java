package rotp.addons.ger.gametest;

import java.util.List;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.ability.GerReturnToZeroAbility;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Client;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.addons.ger.network.GerAnswerPayloads;
import rotp.addons.ger.state.GerRtzRewindService;
import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonConfig;
import rotp.addons.kingcrimson.epitaph.EpitaphEnd;
import rotp.addons.kingcrimson.epitaph.EpitaphSession;
import rotp.addons.kingcrimson.epitaph.EpitaphSessions;
import rotp.addons.kingcrimson.epitaph.api.EpitaphPredictions;
import rotp.addons.kingcrimson.state.KingCrimsonCreatureErase;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModStands;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.subsystems.timestop.TimeStopState;

/**
 * King Crimson's two hooks as Requiem fills them, with King Crimson loaded in the test server: the Epitaph exemption
 * and its break by the press of Return to Zero (EPITAPH-ARCH 3.7, ruling 36), and a creature's erase against a holder
 * returned to zero as it would begin (rule C6). Each test has its own batch: a mark and an erase reach other scenes.
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerKingCrimsonHookGameTests {
    private static final int MARK_CHUNK_RADIUS = 8;
    private static final String ERASE = "kingcrimson_timeerase";

    private GerKingCrimsonHookGameTests() {}

    private static void kingCrimsonLoaded(GerMeleeGameTestScene scene) {
        scene.premise(ModList.get().isLoaded("rotp_kingcrimson"), "King Crimson is loaded in the test server");
    }

    private static boolean flashedOn(List<Object> received, LivingEntity entity) {
        return received.stream().anyMatch(payload -> payload instanceof GerAnswerPayloads.FlashPayload flash
                && flash.userId() == entity.getId() && flash.attackerId() == -1 && flash.full());
    }

    /** A creature that carries King Crimson, as the creature add-on gives it: every skill, summoned, a full bar. */
    private static Husk bearer(GerMeleeGameTestScene scene, Vec3 position, LivingEntity target) {
        Husk husk = EntityType.HUSK.create(scene.level);
        husk.setNoAi(true);
        husk.setPersistenceRequired();
        scene.add(husk, position);
        StandPower power = PowerClass.STAND.attachGet(husk);
        power.setStand(KingCrimsonAddon.KING_CRIMSON.get());
        power.skipProgression();
        power.setResolveLevel(4);
        scene.premise(KingCrimsonAddon.KING_CRIMSON.get().summon(husk, power), "the husk's King Crimson came out");
        StandEntity stand = power.getSummonedStandEntity();
        stand.summonLockTicks = 0;
        scene.owned.add(stand);
        power.setStamina(power.getMaxStamina());
        husk.setTarget(target);
        scene.onClose(() -> KingCrimsonCreatureErase.end(husk));
        return husk;
    }

    private static final short RTZ_KEY = 409;
    private static final int STOP_ID = 77_301;

    /** Epitaph runs no simulation budget here and King Crimson's reflex stays out; every prediction ends with the scene. */
    private static void quietEpitaph(GerMeleeGameTestScene scene) {
        kingCrimsonLoaded(scene);
        int budget = KingCrimsonConfig.EPITAPH_SIM_BUDGET_MILLIS.get();
        boolean reflex = KingCrimsonConfig.REFLEX_ERASE.get();
        KingCrimsonConfig.EPITAPH_SIM_BUDGET_MILLIS.set(0);
        KingCrimsonConfig.REFLEX_ERASE.set(false);
        scene.onClose(() -> {
            for (EpitaphSession session : EpitaphSessions.all()) {
                session.end(EpitaphEnd.CANCELLED);
            }
            KingCrimsonConfig.EPITAPH_SIM_BUDGET_MILLIS.set(budget);
            KingCrimsonConfig.REFLEX_ERASE.set(reflex);
        });
    }

    // King Crimson's types stay out of the test class's own method signatures: the game test registry reads them
    // all, and without King Crimson each test then fails on its premise instead of the server failing to start.
    private static final class Predictions {
        private Predictions() {}

        /** The mark, then a prediction of the other aimed at the holder, which tells him and does not walk him. */
        static EpitaphSession markAndPredict(GerMeleeGameTestScene scene, Actor holder, Actor foreseer) {
            ServerPlayer user = holder.user();
            scene.premise(GerRtzRewindService.setMark(scene.level, user, MARK_CHUNK_RADIUS)
                            == GerRtzRewindService.Result.SUCCESS,
                    "the mark is set");
            EpitaphSession session = EpitaphSessions.predict(foreseer.user(), List.of(user), user);
            scene.helper.assertTrue(session != null && session.exempts(user)
                            && EpitaphPredictions.isPredictedPlayer(user)
                            && !EpitaphPredictions.isForcedWalking(user),
                    scene.id + ": a prediction aimed at the holder " + (session == null ? "did not begin"
                            : "exempts him " + session.exempts(user) + ", tells him "
                                    + EpitaphPredictions.isPredictedPlayer(user) + ", walks him "
                                    + EpitaphPredictions.isForcedWalking(user)));
            return session;
        }
    }

    // as the server takes a press: the slot's check, then the press
    private static void pressReturnToZero(GerMeleeGameTestScene scene, Actor actor) {
        Ability base = actor.power().getAbility(GoldExperienceRequiemAddon.GER_RETURN_TO_ZERO_NAME);
        AvailableAbilities moves = actor.power().updateAvailableMoves();
        moves.update(actor.power(), actor.power().getMoveset());
        AbilityConditionCheck key = moves.getContextVariationContainer(base);
        scene.premise(key != null && key.ability == base
                        && AbilityInput.withConditionCheck(key, actor.user(), InputMethod.CLICK),
                "the holder's key of Return to Zero takes the press");
        AbilityInput.keyPress(RTZ_KEY, key.ability, actor.user(), null, InputMethod.CLICK, 0,
                BufferingState.clickCanBuffer(), key.ability.getAbilityId());
    }

    private static boolean pressOver(Actor actor) {
        return LivingComponentAction.getCurEntityAction(actor.stand()) == null
                && LivingComponentAction.getCurEntityAction(actor.user()) == null;
    }

    /**
     * Ruling 36: a holder under a prediction aimed at him is told of it and not walked; his key press of Return to Zero
     * breaks it and does nothing else (no rewind, no stamina, no cooldown, the mark kept); the prediction of the other
     * goes on, and his next press rewinds as always.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 300, batch = "ger_kc_epitaph_break")
    public static void aHolderToldOfAPredictionBreaksItWithHisPressAndNothingElse(GameTestHelper helper) {
        new Sequence(helper, "kc/epitaph-break", 200) {
            private Actor holder;
            private Actor foreseer;
            private EpitaphSession session;
            private float stamina;
            private int pressedAt;
            private int stage;

            @Override
            boolean step() {
                ServerPlayer user = holder != null ? holder.user() : null;
                switch (stage) {
                    case 0 -> {
                        quietEpitaph(scene);
                        holder = scene.actor();
                        foreseer = scene.standUser(scene.origin.add(0, 0, -3), 0,
                                KingCrimsonAddon.KING_CRIMSON.get(), true);
                        scene.onClose(() -> AbilityInput.keyRelease(RTZ_KEY, holder.user()));
                        stage = 1;
                    }
                    case 1 -> {
                        session = Predictions.markAndPredict(scene, holder, foreseer);
                        stamina = holder.power().getStamina();
                        pressReturnToZero(scene, holder);
                        pressedAt = tick;
                        stage = 2;
                    }
                    case 2 -> {
                        if (tick - pressedAt >= 3 && pressOver(holder) || tick - pressedAt > 40) {
                            String rtz = GoldExperienceRequiemAddon.GER_RETURN_TO_ZERO_NAME;
                            helper.assertTrue(GerRtzRewindService.hasMark(user)
                                            && !GerRtzRewindService.hasActivePresentation(user)
                                            && holder.power().getStamina() >= stamina
                                            && holder.power().getAbilityCooldown(rtz) == 0,
                                    scene.id + ": the press that broke the prediction kept the mark "
                                            + GerRtzRewindService.hasMark(user) + ", rewound "
                                            + GerRtzRewindService.hasActivePresentation(user) + ", took "
                                            + (stamina - holder.power().getStamina())
                                            + " stamina and set a cooldown of " + holder.power().getAbilityCooldown(rtz));
                            helper.assertTrue(!EpitaphPredictions.isPredictedPlayer(user) && !session.ended()
                                            && EpitaphSessions.of(foreseer.user()) == session,
                                    scene.id + ": after the press the holder is told "
                                            + EpitaphPredictions.isPredictedPlayer(user)
                                            + "; the prediction of the other ended " + session.ended());
                            AbilityInput.keyRelease(RTZ_KEY, user);
                            stage = 3;
                        }
                    }
                    case 3 -> {
                        pressReturnToZero(scene, holder);
                        pressedAt = tick;
                        stage = 4;
                    }
                    case 4 -> {
                        if (!GerRtzRewindService.hasMark(user)) {
                            helper.assertTrue(GerRtzRewindService.hasActivePresentation(user),
                                    scene.id + ": the next press spent the mark without a rewind");
                            return true;
                        }
                        helper.assertTrue(tick - pressedAt < 40, scene.id + ": the next press did not rewind");
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }

    /**
     * The coordinator, R772.1: only a key press breaks a prediction. A stop of time that holds a holder under a
     * prediction is answered by Return to Zero by itself as always, and the prediction aimed at him is not broken.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_kc_epitaph_stop")
    public static void aStopOfTimeDuringAPredictionIsAnsweredAndThePredictionIsNotBroken(GameTestHelper helper) {
        new Sequence(helper, "kc/epitaph-stop", 150) {
            private Actor holder;
            private Actor foreseer;
            private Actor stopper;
            private EpitaphSession session;
            private int stage;

            private TimeStopState stops() {
                return scene.level.getData(ModDataAttachmentTypes.TIME_STOP.get());
            }

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        quietEpitaph(scene);
                        holder = scene.actor();
                        foreseer = scene.standUser(scene.origin.add(0, 0, -3), 0,
                                KingCrimsonAddon.KING_CRIMSON.get(), true);
                        // a stop ends once no user with a time stop holds it
                        stopper = scene.standUser(scene.origin.add(-2, 0, 0), 0, ModStands.THE_WORLD.get(), false);
                        scene.onClose(() -> stops().removeInstance(STOP_ID));
                        stage = 1;
                    }
                    case 1 -> {
                        session = Predictions.markAndPredict(scene, holder, foreseer);
                        stage = 2;
                    }
                    case 2 -> {
                        scene.premise(stops().tryPutInstance(new TimeStopState.Instance(STOP_ID, 300, 300,
                                        new ChunkPos(holder.user().blockPosition()), 8, stopper.user().getId(),
                                        "ger_kc_epitaph_stop")) && stops().shouldFreeze(holder.user()),
                                "a stop after the mark holds the holder");
                        stage = 3;
                    }
                    case 3 -> {
                        ServerPlayer user = holder.user();
                        // What the service runs a tick after such a stop (processAutomaticRtz), which finds a player
                        // through the server's list; a scene's players are not in it.
                        GerReturnToZeroAbility rtz = (GerReturnToZeroAbility) holder.power().getMoveset()
                                .getAbility(GoldExperienceRequiemAddon.GER_RETURN_TO_ZERO_NAME);
                        boolean answered = rtz.tryAutomaticTimeStopResponse(scene.level, user, holder.power());
                        helper.assertTrue(answered && !GerRtzRewindService.hasMark(user)
                                        && GerRtzRewindService.hasActivePresentation(user)
                                        && stops().getInstance(STOP_ID).isEmpty(),
                                scene.id + ": the answer ran " + answered + ", spent the mark, rewound "
                                        + GerRtzRewindService.hasActivePresentation(user) + " and ended the stop "
                                        + stops().getInstance(STOP_ID).isEmpty());
                        helper.assertTrue(EpitaphPredictions.isPredictedPlayer(user) && !session.ended(),
                                scene.id + ": after the answer the holder is told "
                                        + EpitaphPredictions.isPredictedPlayer(user)
                                        + "; the prediction ended " + session.ended());
                        return true;
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }

    /**
     * The owner: a creature's erase does not work on a holder of Requiem; it is cancelled at the moment it would begin,
     * with Requiem's Return to Zero effect, whatever the range and with his Stand away. Against him means the player it
     * fights, and the one whose hit would kill it, which its passive erase then does not escape.
     */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 200, batch = "ger_kc_creature_erase")
    public static void aCreaturesEraseAgainstARequiemHolderIsReturnedToZero(GameTestHelper helper) {
        new Sequence(helper, "kc/creature-erase", 150) {
            private Actor holder;
            private Client other;
            private Husk fightsHim;
            private Husk fightsOther;
            private int stage;

            @Override
            boolean step() {
                switch (stage) {
                    case 0 -> {
                        kingCrimsonLoaded(scene);
                        boolean reflex = KingCrimsonConfig.REFLEX_ERASE.get();
                        KingCrimsonConfig.REFLEX_ERASE.set(true);
                        scene.onClose(() -> KingCrimsonConfig.REFLEX_ERASE.set(reflex));
                        holder = scene.standUser(scene.origin, 0, GoldExperienceRequiemAddon.GER_STAND.get(), false);
                        other = scene.client(scene.origin.add(0, 0, 3));
                        fightsHim = bearer(scene, scene.origin.add(3, 0, 0), holder.user());
                        fightsOther = bearer(scene, scene.origin.add(-3, 0, 0), other.player());
                        stage = 1;
                    }
                    case 1 -> {
                        scene.premise(GoldExperienceRequiemAddon.isGerUser(holder.user())
                                        && GoldExperienceRequiemAddon.activeStand(holder.user()) == null,
                                "the holder holds Requiem with his Stand away");
                        StandPower power = StandPower.get(fightsHim);
                        float bar = power.getStamina();
                        boolean began = KingCrimsonCreatureErase.begin(fightsHim);
                        helper.assertTrue(!began && !KingCrimsonCreatureErase.isErasing(fightsHim)
                                        && power.getStamina() < bar && power.isAbilityOnCooldown(ERASE)
                                        && flashedOn(holder.received(), fightsHim),
                                scene.id + ": the erase of a creature that fights the holder began " + began
                                        + ", runs " + KingCrimsonCreatureErase.isErasing(fightsHim) + ", cost "
                                        + (bar - power.getStamina()) + ", its key waits "
                                        + power.getAbilityCooldown(ERASE) + " ticks, Requiem's effect was shown on it "
                                        + flashedOn(holder.received(), fightsHim));
                        boolean otherBegan = KingCrimsonCreatureErase.begin(fightsOther);
                        helper.assertTrue(otherBegan && KingCrimsonCreatureErase.isErasing(fightsOther),
                                scene.id + ": the erase of a creature that fights another player began " + otherBegan);
                        stage = 2;
                    }
                    case 2 -> {
                        Vec3 at = fightsOther.position();
                        fightsOther.hurt(fightsOther.damageSources().playerAttack(holder.user()), 1000.0F);
                        helper.assertTrue(fightsOther.isDeadOrDying() && fightsOther.position().distanceTo(at) < 1.0E-3
                                        && flashedOn(holder.received(), fightsOther),
                                scene.id + ": the holder's killing hit on a creature in its erase left it "
                                        + (fightsOther.isDeadOrDying() ? "dead" : "alive") + ", "
                                        + fightsOther.position().distanceTo(at) + " from its place; Requiem's effect"
                                        + " was shown on it " + flashedOn(holder.received(), fightsOther));
                        return true;
                    }
                    default -> throw new IllegalStateException();
                }
                return false;
            }
        }.start();
    }
}
