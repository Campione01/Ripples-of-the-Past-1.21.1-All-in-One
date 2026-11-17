package rotp.core.gametest;

import java.util.concurrent.atomic.AtomicBoolean;

import rotp.core.api.time.event.BitesTheDustDetonateEvent;
import rotp.core.api.time.event.BitesTheDustDetonateEvent.Outcome;
import rotp.core.core.JojoMod;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * BitesTheDustDetonateEvent's three outcomes: no answer detonates, a cancel holds the bomb, a defusal removes it; post
 * keeps its meaning (true only for a detonation) and a later un-cancel forgets a defusal. The test's listener only
 * answers for targets carrying its tags, so it changes nothing for the other tests once registered.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BitesTheDustOutcomeGameTests {
	private static final String HOLD = "jojo_ripples.gametest.btd_hold";
	private static final String DEFUSE = "jojo_ripples.gametest.btd_defuse";
	private static final String DEFUSE_THEN_LET_GO = "jojo_ripples.gametest.btd_defuse_let_go";
	private static final String DEFUSE_THEN_HOLD = "jojo_ripples.gametest.btd_defuse_hold";
	private static final AtomicBoolean REGISTERED = new AtomicBoolean();

	private BitesTheDustOutcomeGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void aDetonationDetonatesIsHeldOrIsDefused(GameTestHelper helper) {
		if (REGISTERED.compareAndSet(false, true)) {
			NeoForge.EVENT_BUS.addListener(BitesTheDustDetonateEvent.class, event -> {
				if (event.getTarget().getTags().contains(HOLD)) {
					event.setCanceled(true);
				}
				else if (event.getTarget().getTags().contains(DEFUSE) || event.getTarget().getTags().contains(DEFUSE_THEN_LET_GO)
						|| event.getTarget().getTags().contains(DEFUSE_THEN_HOLD)) {
					event.defuse();
				}
			});
			// a cancelled event reaches only the listeners that ask for it
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, BitesTheDustDetonateEvent.class, event -> {
				if (event.getTarget().getTags().contains(DEFUSE_THEN_LET_GO)) {
					event.setCanceled(false);
				}
				else if (event.getTarget().getTags().contains(DEFUSE_THEN_HOLD)) {
					event.setCanceled(false);
					event.setCanceled(true);
				}
			});
		}
		Pig owner = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 2, 1));
		Pig plain = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 2, 1));
		Pig held = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 2, 1));
		Pig defused = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 2, 1));
		Pig letGo = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 2, 1));
		Pig heldAfter = helper.spawnWithNoFreeWill(EntityType.PIG, new BlockPos(1, 2, 1));
		try {
			held.addTag(HOLD);
			defused.addTag(DEFUSE);
			letGo.addTag(DEFUSE_THEN_LET_GO);
			heldAfter.addTag(DEFUSE_THEN_HOLD);
			helper.assertTrue(BitesTheDustDetonateEvent.postOutcome(owner, heldAfter, null) == Outcome.HELD,
					"A defusal taken back and then a hold read as " + BitesTheDustDetonateEvent.postOutcome(owner, heldAfter, null));
			helper.assertTrue(BitesTheDustDetonateEvent.postOutcome(owner, plain, null) == Outcome.DETONATE
					&& BitesTheDustDetonateEvent.post(owner, plain, null), "A detonation no one answered did not detonate");
			helper.assertTrue(BitesTheDustDetonateEvent.postOutcome(owner, held, owner) == Outcome.HELD
					&& !BitesTheDustDetonateEvent.post(owner, held, owner), "A cancelled detonation was not held");
			helper.assertTrue(BitesTheDustDetonateEvent.postOutcome(owner, defused, owner) == Outcome.DEFUSED
					&& !BitesTheDustDetonateEvent.post(owner, defused, owner), "A defused detonation was not defused, or post let it go off");
			helper.assertTrue(BitesTheDustDetonateEvent.postOutcome(owner, letGo, null) == Outcome.DETONATE,
					"A defusal a later listener took back did not detonate");
		}
		finally {
			owner.discard();
			plain.discard();
			held.discard();
			defused.discard();
			letGo.discard();
			heldAfter.discard();
		}
		helper.succeed();
	}
}
