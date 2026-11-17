package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import com.mojang.authlib.GameProfile;

import rotp.core.api.time.TimeEraseWitnesses;
import rotp.core.api.time.event.TimeEraseRunningEvent;
import rotp.core.api.time.event.TimeEraseStartEvent;
import rotp.core.core.JojoMod;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The time erase's meeting points (King Crimson posts, Gold Experience Requiem answers): the start event's kind, a full
 * erase by default and a step when said; the running event, not cancelled unless answered, and its answer; the
 * witnesses' two questions answered apart. The test's listeners and rules only act on erasers and witnesses carrying
 * its tags, so they change nothing for the other tests once registered.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeEraseApiGameTests {
	private static final String ANSWERED = "jojo_ripples.gametest.erase_answered";
	private static final String SEES = "jojo_ripples.gametest.erase_witness_sees";
	private static final String KEEPS = "jojo_ripples.gametest.erase_witness_keeps";
	private static final String BROKEN = "jojo_ripples.gametest.erase_witness_broken";
	private static final ResourceLocation ANSWER = JojoMod.resLoc("gametest_erase_answer");
	private static final AtomicBoolean REGISTERED = new AtomicBoolean();
	// the kinds of the starts posted for the tagged eraser, on the server thread
	private static final List<TimeEraseStartEvent.Kind> STARTS = new ArrayList<>();

	private TimeEraseApiGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theEraseEventsTellAStepFromAnEraseAndCarryTheirAnswer(GameTestHelper helper) {
		register();
		FakePlayer eraser = player(helper, "EraseEventsEraser");
		try {
			TimeEraseStartEvent plain = new TimeEraseStartEvent(eraser, helper.getLevel(), eraser.position(), 0L, 200);
			helper.assertTrue(plain.getKind() == TimeEraseStartEvent.Kind.ERASE,
					"The start event's old constructor did not mean a full erase: " + plain.getKind());
			eraser.addTag(ANSWERED);
			STARTS.clear();
			helper.assertTrue(TimeEraseStartEvent.post(eraser, 200) && TimeEraseStartEvent.post(eraser, 12, TimeEraseStartEvent.Kind.STEP),
					"A start no one refused was refused");
			helper.assertTrue(STARTS.equals(List.of(TimeEraseStartEvent.Kind.ERASE, TimeEraseStartEvent.Kind.STEP)),
					"post(eraser, ticks) and post(eraser, ticks, STEP) gave the kinds " + STARTS);

			eraser.removeTag(ANSWERED);
			TimeEraseRunningEvent free = TimeEraseRunningEvent.post(eraser, 7);
			helper.assertTrue(!free.isCanceled() && free.getAnsweredBy() == null && free.getAnswerer() == null
					&& free.getTicksElapsed() == 7 && free.getPosition().equals(eraser.position()) && free.getLevel() == helper.getLevel(),
					"An erase no one answered was thrown out or carried an answer: " + free.getAnsweredBy());
			eraser.addTag(ANSWERED);
			TimeEraseRunningEvent answered = TimeEraseRunningEvent.post(eraser, 8);
			helper.assertTrue(answered.isCanceled() && ANSWER.equals(answered.getAnsweredBy()) && answered.getAnswerer() == eraser,
					"An answered erase did not come back cancelled with its answer: " + answered.isCanceled() + " "
							+ answered.getAnsweredBy() + " " + answered.getAnswerer());
		}
		finally {
			eraser.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theWitnessesAnswerTheirTwoQuestionsApart(GameTestHelper helper) {
		register();
		FakePlayer eraser = player(helper, "WitnessEraser");
		FakePlayer seer = player(helper, "WitnessSeer");
		FakePlayer keeper = player(helper, "WitnessKeeper");
		FakePlayer both = player(helper, "WitnessBoth");
		FakePlayer nobody = player(helper, "WitnessNobody");
		try {
			seer.addTag(SEES);
			keeper.addTag(KEEPS);
			both.addTag(SEES);
			both.addTag(KEEPS);
			both.addTag(BROKEN);
			nobody.addTag(BROKEN);
			helper.assertTrue(TimeEraseWitnesses.hasWitnesses(), "No witness rule is registered");
			helper.assertTrue(TimeEraseWitnesses.seesEraser(seer, eraser) && !TimeEraseWitnesses.keepsPlace(seer, eraser),
					"A witness that only sees the eraser answered " + TimeEraseWitnesses.seesEraser(seer, eraser) + ", "
							+ TimeEraseWitnesses.keepsPlace(seer, eraser));
			helper.assertTrue(!TimeEraseWitnesses.seesEraser(keeper, eraser) && TimeEraseWitnesses.keepsPlace(keeper, eraser),
					"A witness that only keeps its place answered " + TimeEraseWitnesses.seesEraser(keeper, eraser) + ", "
							+ TimeEraseWitnesses.keepsPlace(keeper, eraser));
			helper.assertTrue(TimeEraseWitnesses.seesEraser(both, eraser) && TimeEraseWitnesses.keepsPlace(both, eraser),
					"A rule that failed kept another rule's yes from counting");
			helper.assertTrue(!TimeEraseWitnesses.seesEraser(nobody, eraser) && !TimeEraseWitnesses.keepsPlace(nobody, eraser)
					&& !TimeEraseWitnesses.answers(nobody, eraser, TimeEraseWitnesses.Question.SEES_ERASER),
					"A failing rule counted as a yes, or a player no rule names was a witness");
		}
		finally {
			eraser.discard();
			seer.discard();
			keeper.discard();
			both.discard();
			nobody.discard();
		}
		helper.succeed();
	}

	private static void register() {
		if (!REGISTERED.compareAndSet(false, true)) {
			return;
		}
		NeoForge.EVENT_BUS.addListener(TimeEraseStartEvent.class, event -> {
			if (event.getEraser().getTags().contains(ANSWERED)) {
				STARTS.add(event.getKind());
			}
		});
		NeoForge.EVENT_BUS.addListener(TimeEraseRunningEvent.class, event -> {
			if (event.getEraser().getTags().contains(ANSWERED)) {
				event.answer(ANSWER, event.getEraser());
			}
		});
		TimeEraseWitnesses.register(JojoMod.resLoc("gametest_erase_seer"),
				(witness, eraser, question) -> question == TimeEraseWitnesses.Question.SEES_ERASER && witness.getTags().contains(SEES));
		TimeEraseWitnesses.register(JojoMod.resLoc("gametest_erase_broken"), (witness, eraser, question) -> {
			if (witness.getTags().contains(BROKEN)) {
				throw new IllegalStateException("gametest: a witness rule that fails");
			}
			return false;
		});
		TimeEraseWitnesses.register(JojoMod.resLoc("gametest_erase_keeper"),
				(witness, eraser, question) -> question == TimeEraseWitnesses.Question.KEEPS_PLACE && witness.getTags().contains(KEEPS));
	}

	private static FakePlayer player(GameTestHelper helper, String name) {
		FakePlayer player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), name));
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 1, 1)));
		player.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
		return player;
	}
}
