package rotp.core.gametest;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.isInvulnerableTo let OUT_OF_WORLD through before anything else, and its kill() hurt with that type: the
 * kill command and the void reach any Stand. A Stand whose user is a removed entity was invulnerable to everything, so the
 * command reported success and it stayed; on a summoned Stand the hit reaches its user through the health link.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandKillCommandGameTests {
	private StandKillCommandGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void theKillCommandAndTheVoidReachAStandWhoseUserIsRemoved(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie user = addZombie(helper);
		StandEntity stand = summon(helper, user);
		// as a decoy's Stand, which outlives its user
		stand.healthLinkedWithUser = false;
		user.discard();
		helper.assertTrue(stand.isInvulnerableTo(level.damageSources().generic()),
				"setup: a Stand whose user is removed takes no ordinary damage");
		float health = stand.getHealth();
		helper.assertTrue(stand.hurt(level.damageSources().fellOutOfWorld(), 4.0F) && stand.getHealth() == health - 4.0F,
				"the void hurts a Stand whose user is removed: it has " + stand.getHealth() + " of " + health);
		stand.kill();
		helper.assertTrue(stand.isDeadOrDying(), "the kill command kills a Stand whose user is removed: it has " + stand.getHealth());
		helper.runAfterDelay(25, () -> {
			if (!stand.isRemoved()) {
				stand.discard();
				helper.fail("a Stand the kill command killed leaves the world after its death");
			}
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void theKillCommandAndTheVoidReachASummonedStandsUserThroughTheHealthLink(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Zombie user = addZombie(helper);
		StandEntity stand = summon(helper, user);
		try {
			float health = user.getHealth();
			helper.assertTrue(stand.hurt(level.damageSources().fellOutOfWorld(), 4.0F) && user.getHealth() == health - 4.0F,
					"the void hits the user of a summoned Stand through the health link: he has " + user.getHealth() + " of " + health);
			stand.kill();
			helper.assertTrue(!user.isAlive() && stand.isDeadOrDying(), "the kill command on a summoned Stand kills its user through"
					+ " the health link: he has " + user.getHealth() + ", the Stand " + stand.getHealth());
		}
		finally {
			end(user, stand);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aCreativeUsersStandSurvivesTheKillCommandAsItsUserDoes(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.CREATIVE);
		user.getAbilities().invulnerable = true;
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		user.setNoGravity(true);
		user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		helper.assertTrue(level.addFreshEntity(user), "setup: could not add the user");
		StandEntity stand = summon(helper, user);
		try {
			helper.assertTrue(stand.isInvulnerableTo(level.damageSources().generic()),
					"ordinary damage is still refused to a creative user's Stand");
			helper.assertTrue(!stand.isInvulnerableTo(level.damageSources().genericKill()),
					"the kill command is let through to a creative user's Stand");
			float health = user.getHealth();
			stand.kill();
			helper.assertTrue(user.isAlive() && user.getHealth() == health, "the health link does not hurt a creative user: he has "
					+ user.getHealth() + " of " + health);
			helper.assertTrue(stand.isAlive() && !stand.isDeadOrDying() && stand.getPose() != Pose.DYING,
					"a Stand whose user survives the hit does not die: it has " + stand.getHealth() + ", pose " + stand.getPose());
		}
		finally {
			end(user, stand);
		}
		helper.succeed();
	}

	private static Zombie addZombie(GameTestHelper helper) {
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(zombie != null, "setup: could not create the zombie");
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "setup: could not add the zombie");
		return zombie;
	}

	private static StandEntity summon(GameTestHelper helper, LivingEntity user) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = PowerClass.STAND.attachGet(user);
		helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED && type.summon(user, power),
				"setup: could not give the user Star Platinum and summon it");
		StandEntity stand = power.getSummonedStandEntity();
		helper.assertTrue(stand != null && stand.getUser() == user, "setup: the summoned Stand is missing");
		return stand;
	}

	private static void end(LivingEntity user, StandEntity stand) {
		stand.discard();
		user.discard();
	}
}
