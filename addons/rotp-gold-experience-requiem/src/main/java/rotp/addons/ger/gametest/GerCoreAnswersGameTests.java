package rotp.addons.ger.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import rotp.addons.ger.GoldExperienceRequiemAddon;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Actor;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Client;
import rotp.addons.ger.gametest.GerMeleeGameTestScene.Sequence;
import rotp.addons.ger.network.GerAnswerPayloads;
import rotp.addons.ger.state.GerCoreSeams;
import rotp.core.api.time.TimeEraseWitnesses;
import rotp.core.api.time.event.BitesTheDustDetonateEvent;
import rotp.core.api.time.event.TimeEraseRunningEvent;
import rotp.core.api.time.event.TimeEraseStartEvent;

/**
 * What Requiem answers by itself through the core's events: a bomb that would
 * go off, an erase that starts, an erase that runs, and what its holder
 * witnesses of one. Each test has its own batch: a summoned Requiem answers
 * within chunks, across scenes.
 */
@GameTestHolder(GoldExperienceRequiemAddon.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GerCoreAnswersGameTests {
    private static final ResourceLocation RETURN_TO_ZERO = GoldExperienceRequiemAddon.id("return_to_zero");

    private GerCoreAnswersGameTests() {}

    private static Husk husk(GerMeleeGameTestScene scene, Vec3 position) {
        Husk husk = EntityType.HUSK.create(scene.level);
        husk.setNoAi(true);
        husk.setPersistenceRequired();
        return scene.add(husk, position);
    }

    /** A place whose chunk is this many chunks east of the user's. */
    private static Vec3 chunksAway(LivingEntity user, int chunks) {
        return user.position().add(16.0D * chunks, 0.0D, 0.0D);
    }

    private static boolean startAnswered(GerMeleeGameTestScene scene, LivingEntity eraser, LivingEntity user,
            int chunks, TimeEraseStartEvent.Kind kind) {
        return NeoForge.EVENT_BUS.post(new TimeEraseStartEvent(eraser, scene.level, chunksAway(user, chunks),
                scene.level.getGameTime(), 100, kind)).isCanceled();
    }

    /** The owner: the bomb is gone at the moment it would go off, and nothing happens. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100, batch = "ger_answers_bomb")
    public static void theBombOnASummonedRequiemsUserIsDefused(GameTestHelper helper) {
        new Sequence(helper, "answers/bomb", 100) {
            @Override
            boolean step() {
                Actor actor = scene.actor();
                Husk bomber = husk(scene, scene.origin.add(0, 0, 3));
                BitesTheDustDetonateEvent.Outcome summoned =
                        BitesTheDustDetonateEvent.postOutcome(bomber, actor.user(), null);
                scene.log("summoned: " + summoned);
                helper.assertTrue(summoned == BitesTheDustDetonateEvent.Outcome.DEFUSED,
                        scene.id + ": the bomb on a summoned Requiem's user is " + summoned + ", not defused");
                helper.assertTrue(!BitesTheDustDetonateEvent.post(bomber, actor.user(), null),
                        scene.id + ": the bomb went off on a summoned Requiem's user");
                actor.type().forceUnsummon(actor.user(), actor.power());
                BitesTheDustDetonateEvent.Outcome away =
                        BitesTheDustDetonateEvent.postOutcome(bomber, actor.user(), null);
                helper.assertTrue(away == BitesTheDustDetonateEvent.Outcome.DETONATE,
                        scene.id + ": with Requiem away the bomb is " + away);
                return true;
            }
        }.start();
    }

    /** A full Time Erase is returned to zero within the rewind's 8 chunks, King Crimson's step within 4. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100, batch = "ger_answers_erase_start")
    public static void aStartingEraseIsAnsweredWithinEightChunksAndAStepWithinFour(GameTestHelper helper) {
        new Sequence(helper, "answers/erase-start", 100) {
            @Override
            boolean step() {
                Actor actor = scene.actor();
                Husk eraser = husk(scene, scene.origin.add(0, 0, 3));
                scene.premise(GerCoreSeams.ERASE_ANSWER_CHUNKS == 8 && GerCoreSeams.ERASE_NEAR_CHUNKS == 4,
                        "8 chunks for an erase, 4 for a step");
                boolean erase8 = startAnswered(scene, eraser, actor.user(), 8, TimeEraseStartEvent.Kind.ERASE);
                boolean erase9 = startAnswered(scene, eraser, actor.user(), 9, TimeEraseStartEvent.Kind.ERASE);
                boolean step4 = startAnswered(scene, eraser, actor.user(), 4, TimeEraseStartEvent.Kind.STEP);
                boolean step5 = startAnswered(scene, eraser, actor.user(), 5, TimeEraseStartEvent.Kind.STEP);
                scene.log("erase at 8=" + erase8 + " at 9=" + erase9 + "; step at 4=" + step4 + " at 5=" + step5);
                helper.assertTrue(erase8 && !erase9,
                        scene.id + ": a full erase is answered at 8 chunks " + erase8 + ", at 9 " + erase9);
                helper.assertTrue(step4 && !step5,
                        scene.id + ": a step is answered at 4 chunks " + step4 + ", at 5 " + step5);
                actor.type().forceUnsummon(actor.user(), actor.power());
                helper.assertTrue(!startAnswered(scene, eraser, actor.user(), 0, TimeEraseStartEvent.Kind.ERASE)
                                && !startAnswered(scene, eraser, actor.user(), 0, TimeEraseStartEvent.Kind.STEP),
                        scene.id + ": an erase was answered with Requiem away");
                return true;
            }
        }.start();
    }

    /** The owner: "直接被強制退出時刪並顯示黃鎮的歸零特效", within 4 chunks of a summoned Requiem's user. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100, batch = "ger_answers_erase_running")
    public static void anEraserWhoComesNearInARunningEraseIsThrownOut(GameTestHelper helper) {
        new Sequence(helper, "answers/erase-running", 100) {
            @Override
            boolean step() {
                Actor actor = scene.actor();
                // not in the level: the event reads only where he is
                Husk eraser = EntityType.HUSK.create(scene.level);
                Vec3 far = chunksAway(actor.user(), 5);
                eraser.setPos(far.x, far.y, far.z);
                TimeEraseRunningEvent outside = TimeEraseRunningEvent.post(eraser, 20);
                helper.assertTrue(!outside.isCanceled() && outside.getAnsweredBy() == null,
                        scene.id + ": an eraser 5 chunks off was thrown out by " + outside.getAnsweredBy());

                Vec3 near = chunksAway(actor.user(), 4);
                eraser.setPos(near.x, near.y, near.z);
                TimeEraseRunningEvent inside = TimeEraseRunningEvent.post(eraser, 21);
                scene.log("at 4 chunks: cancelled=" + inside.isCanceled() + " by " + inside.getAnsweredBy()
                        + " answerer=" + inside.getAnswerer());
                helper.assertTrue(inside.isCanceled() && RETURN_TO_ZERO.equals(inside.getAnsweredBy())
                                && inside.getAnswerer() == actor.user(),
                        scene.id + ": an eraser 4 chunks off was not thrown out by the Requiem user: cancelled "
                                + inside.isCanceled() + ", by " + inside.getAnsweredBy());
                helper.assertTrue(actor.received().stream().anyMatch(payload ->
                                payload instanceof GerAnswerPayloads.FlashPayload flash && flash.full()
                                        && flash.userId() == eraser.getId()),
                        scene.id + ": Requiem's gold effect was not shown on the eraser");

                actor.type().forceUnsummon(actor.user(), actor.power());
                helper.assertTrue(!TimeEraseRunningEvent.post(eraser, 22).isCanceled(),
                        scene.id + ": the eraser was thrown out with Requiem away");
                return true;
            }
        }.start();
    }

    /** The owner: a Requiem holder sees the player in his erase and is not moved to his image at its end. */
    @GameTest(template = "empty",
            timeoutTicks = GerMeleeGameTestScene.WAIT_TEST_TICKS + 100, batch = "ger_answers_witness")
    public static void aRequiemHolderWitnessesAnErase(GameTestHelper helper) {
        new Sequence(helper, "answers/witness", 100) {
            @Override
            boolean step() {
                Actor holder = scene.actor();
                Client other = scene.client(scene.origin.add(0, 0, 3));
                Husk eraser = husk(scene, scene.origin.add(3, 0, 0));
                helper.assertTrue(TimeEraseWitnesses.seesEraser(holder.user(), eraser)
                                && TimeEraseWitnesses.keepsPlace(holder.user(), eraser),
                        scene.id + ": a Requiem holder sees the eraser "
                                + TimeEraseWitnesses.seesEraser(holder.user(), eraser) + ", keeps his place "
                                + TimeEraseWitnesses.keepsPlace(holder.user(), eraser));
                helper.assertTrue(!TimeEraseWitnesses.seesEraser(other.player(), eraser)
                                && !TimeEraseWitnesses.keepsPlace(other.player(), eraser),
                        scene.id + ": a player without Requiem witnesses the erase");
                holder.type().forceUnsummon(holder.user(), holder.power());
                helper.assertTrue(TimeEraseWitnesses.seesEraser(holder.user(), eraser)
                                && TimeEraseWitnesses.keepsPlace(holder.user(), eraser),
                        scene.id + ": with his Stand away a Requiem holder no longer witnesses the erase");
                return true;
            }
        }.start();
    }
}
