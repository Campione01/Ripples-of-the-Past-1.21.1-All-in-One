package rotp.core.gametest;

import java.util.List;

import rotp.core.core.JojoMod;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The load-time check of the Stands' control policies (JojoMod.checkStandControlPolicies): on a game test server, as in a
 * development environment, a Stand that lacks a field stops the load naming it; in a player's game it is logged once,
 * with the defaults the Stand then uses, and the load goes on.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandControlPolicyLoadCheckGameTests {
	private StandControlPolicyLoadCheckGameTests() {}

	@GameTest(template = "empty")
	public static void aStandWithoutItsControlPolicyStopsATestServersLoadAndIsOnlyLoggedInAPlayersGame(GameTestHelper helper) {
		helper.assertTrue(JojoMod.standControlPolicyProblemsStopTheLoad(),
				"A game test server must stop its load for a Stand without its control policy");
		StandStats stats = new StandStats.Builder().power(1).speed(1).range(1, 2).durability(1).precision(1).build();
		EntityStandType complete = new EntityStandType(stats, new MovesetBuilder(), JojoMod.resLoc("gametest_complete_policy"),
				StandControlType.CLOSE_RANGE_DIRECT, 1, 2, true, true);
		EntityStandType broken = new EntityStandType(stats, new MovesetBuilder(),
				JojoMod.resLoc("gametest_policy_without_type_and_decay")).manualControl(true);
		String named = "Stand jojo_ripples:gametest_policy_without_type_and_decay (" + EntityStandType.class.getName()
				+ "): standControlType, distanceStrengthDecay are required";
		List<EntityStandType> stands = List.of(complete, broken);

		helper.assertTrue(JojoMod.checkStandControlPolicies(List.of(complete), true).isEmpty(),
				"A complete control policy was reported");
		String stopped = null;
		try {
			JojoMod.checkStandControlPolicies(stands, true);
		}
		catch (IllegalStateException e) {
			stopped = e.getMessage();
		}
		helper.assertTrue(stopped != null && stopped.startsWith("1 registered Stand(s) with an incomplete control policy")
				&& stopped.contains(named), "The strict check did not stop the load naming the Stand and its fields: " + stopped);

		List<String> logged = JojoMod.checkStandControlPolicies(stands, false);
		helper.assertTrue(logged.equals(List.of(named)), "The player's game check did not report exactly the faulty Stand: "
				+ logged);
		helper.assertTrue(!broken.reportStandControlPolicyProblem(),
				"The player's game check did not log the problem once (its policy getters would log it again)");
		helper.assertTrue(broken.getStandControlType() == StandControlType.CLOSE_RANGE_DIRECT
						&& broken.canBeManuallyControlled() && broken.usesDistanceStrengthDecay(),
				"The faulty Stand does not use the core's defaults (manually controllable with distance decay, as 1.16's"
						+ " StandEntity): " + broken.getStandControlType() + ", " + broken.canBeManuallyControlled() + ", "
						+ broken.usesDistanceStrengthDecay());
		helper.succeed();
	}
}
