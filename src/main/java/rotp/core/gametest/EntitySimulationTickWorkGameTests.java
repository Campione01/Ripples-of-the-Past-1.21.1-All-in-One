package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.api.simulation.EntitySimulations;
import rotp.core.core.JojoMod;
import rotp.core.init.ModStatusEffects;
import rotp.core.subsystems.movement_input_sync.PlayerMovementInputData;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A simulated copy gets no tick event, so what the core does in a player's tick events is in EntitySimulations' tick work
 * for a copy of a player: no sprinting while stunned as his tick starts, his movement keys' timers as it ends. An add-on
 * whose per-tick work matters to a copy subscribes to the copy's room tick (EntitySimulations#subscribeTick) and the room
 * runs the listeners for each copy (EntitySimulations#runTickListeners). The copy here is a player never added to a
 * level, as a copy is.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EntitySimulationTickWorkGameTests {
	private static final ResourceLocation FIRST = JojoMod.resLoc("simulation_tick_test_first");
	private static final ResourceLocation SECOND = JojoMod.resLoc("simulation_tick_test_second");

	private EntitySimulationTickWorkGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void aSimulatedPlayersTickWorkStopsHisSprintInStunAndTicksHisKeyTimers(GameTestHelper helper) {
		Player copy = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		helper.assertTrue(!copy.isAddedToLevel(), "Fixture: the copy is in a level");
		copy.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 100));
		copy.setSprinting(true);
		helper.assertTrue(ModStatusEffects.isStunned(copy) && copy.isSprinting(), "Fixture: the copy is not a stunned sprinter");
		EntitySimulations.runTickStartWork(copy);
		helper.assertTrue(!copy.isSprinting(), "A stunned player's copy still sprints after the tick's start work, as the"
				+ " player's tick event would not let him");

		PlayerMovementInputData input = PlayerMovementInputData.get(copy);
		helper.assertTrue(input != null, "Fixture: the copy has no movement keys");
		input.setValues(0.0F, 1.0F, false, false, true);
		input.leftTimer = 0;
		input.forwardTimer = 0;
		input.jumpingTimer = 0;
		input.shiftKeyDownTimer = 0;
		input.sprintTimer = 0;
		EntitySimulations.runTickEndWork(copy);
		helper.assertTrue(input.forwardTimer == 1 && input.leftTimer == 1 && input.jumpingTimer == 1
				&& input.shiftKeyDownTimer == 1 && input.sprintTimer == 1, "The copy's movement key timers after the tick's end"
				+ " work: forward " + input.forwardTimer + ", sprint " + input.sprintTimer + ", not one tick each");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void tickListenersRunForEachCopyInOrderPastAFailingOneAndStopWhenUnsubscribed(GameTestHelper helper) {
		Player copy = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		List<String> calls = new ArrayList<>();
		boolean before = EntitySimulations.hasTickListeners();
		try {
			EntitySimulations.subscribeTick(FIRST, entity -> {
				calls.add("first " + (entity == copy));
				throw new IllegalStateException("a failing listener (expected by the test)");
			});
			EntitySimulations.subscribeTick(SECOND, entity -> calls.add("second " + (entity == copy)));
			boolean duplicate = false;
			try {
				EntitySimulations.subscribeTick(SECOND, entity -> {});
			}
			catch (IllegalStateException expected) {
				duplicate = true;
			}
			helper.assertTrue(duplicate, "A second listener of one owner was taken");
			helper.assertTrue(EntitySimulations.hasTickListeners(), "Two subscribed listeners do not count");
			EntitySimulations.runTickListeners(copy);
			helper.assertTrue(calls.equals(List.of("first true", "second true")), "The room's tick of a copy ran " + calls
					+ ", not each listener with that copy, in order, past the one that failed");
		}
		finally {
			EntitySimulations.unsubscribeTick(FIRST);
			EntitySimulations.unsubscribeTick(SECOND);
		}
		calls.clear();
		EntitySimulations.runTickListeners(copy);
		helper.assertTrue(calls.isEmpty() && EntitySimulations.hasTickListeners() == before,
				"Unsubscribed listeners still ran: " + calls);
		helper.succeed();
	}
}
