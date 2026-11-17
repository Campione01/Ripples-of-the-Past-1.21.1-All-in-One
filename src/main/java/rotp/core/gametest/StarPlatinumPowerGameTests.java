package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The owner (2026-11, numbers of 2026-11-13): Star Platinum's power is well above The World's, 20 at its base and 35 at
 * its top; the summoned Stand reads both ends, and its punches follow.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StarPlatinumPowerGameTests {
	private StarPlatinumPowerGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void starPlatinumPowerRunsFromTwentyToThirtyFive(GameTestHelper helper) {
		StandType starPlatinum = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandType theWorld = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
		helper.assertTrue(starPlatinum != null && theWorld != null, "Missing Star Platinum or The World");
		helper.assertTrue(starPlatinum.getStandStats().power(0) == 20 && starPlatinum.getStandStats().power(1) == 35,
				"Star Platinum's power runs " + starPlatinum.getStandStats().power(0) + " to " + starPlatinum.getStandStats().power(1));
		helper.assertTrue(starPlatinum.getStandStats().power(1) > theWorld.getStandStats().power(1),
				"Star Platinum's top power is not above The World's " + theWorld.getStandStats().power(1));

		double lowest = summonedPower(helper, starPlatinum, false);
		double highest = summonedPower(helper, starPlatinum, true);
		helper.assertTrue(lowest == 20 && highest == 35,
				"The summoned Star Platinum's power is " + lowest + " at Resolve 0 and " + highest + " at its highest");
		helper.assertTrue(StandStatFormulas.getLightAttackDamage(highest) == 8.75F
				&& StandStatFormulas.getHeavyAttackDamage(highest) == 35F,
				"Star Platinum's punches at its top: light " + StandStatFormulas.getLightAttackDamage(highest)
						+ ", heavy " + StandStatFormulas.getHeavyAttackDamage(highest));
		helper.succeed();
	}

	private static double summonedPower(GameTestHelper helper, StandType type, boolean top) {
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		StandPower power = null;
		try {
			user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add a player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			power.setResolveLevel(top ? power.getMaxResolveLevel() : 0);
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Missing the Star Platinum entity");
			StandEntity.updateStandStatAttributes(stand, user);
			power.setStamina(power.getMaxStamina());
			stand.updateStrengthMultipliers();
			return stand.getAttackDamage();
		}
		finally {
			if (power != null && power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
