package rotp.core.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandMeleeReach;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.functions.MathUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/*
 * 1.16 measured a Stand's distance from its user feet to feet (Entity.distanceToSqr, distanceTo): StandEntity.move held
 * the Stand's feet within the max range of its user's, updateStrengthMultipliers decayed the strength by that distance,
 * and updatePosition put a following Stand at its user's feet plus its offset, however long. Crazy Diamond keeps the
 * port's effective range 1 and max range 2 (owner decision D-workspace-03) and decays only under manual control
 * (D-core-03). Since the owner's reach rule of 2026-10-30 (StandMeleeReach) a close-range Stand goes as far as its base
 * reach plus the remote control margin and decays only through that margin, both measured feet to feet as in 1.16.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandRangeMeasure116GameTests {
	private StandRangeMeasure116GameTests() {}

	/*
	 * Pushed 9 blocks east of its user's feet, or 9 above them, as the O key's movement moves it: past its base reach (the
	 * user's reach of 5, longer than its max range of 2) by the remote control margin of half of it (StandMeleeReach, owner
	 * 2026-10-30), measured feet to feet as 1.16 held a Stand within its max range
	 */
	@GameTest(template = "empty")
	public static void aManuallyControlledStandStopsPastItsBaseReachByTheRemoteMarginFromItsUsersFeet(GameTestHelper helper) {
		for (String row : new String[] { "east", "up" }) {
			try (Scene s = new Scene(helper)) {
				boolean up = row.equals("up");
				s.stand.setManuallyControlled(true);
				Vec3 start = s.user.position().add(up ? 0.0D : 1.0D, up ? 1.0D : 0.0D, 0.0D);
				s.stand.moveTo(start.x, start.y, start.z, 0.0F, 0.0F);
				double base = StandMeleeReach.baseReach(s.stand, s.user);
				helper.assertTrue(s.stand.isManuallyControlled() && s.stand.getMaxRange() == 2.0D && Math.abs(base - 5.0D) < 1.0E-6,
						"STAND-RANGE premise: Crazy Diamond is not manually controlled with its max range of 2 and the base reach"
								+ " of its user's 5: " + s.stand.getMaxRange() + ", " + base);
				s.stand.move(MoverType.SELF, up ? new Vec3(0.0D, 8.0D, 0.0D) : new Vec3(8.0D, 0.0D, 0.0D));
				double distance = s.stand.distanceTo(s.user);
				double gap = MathUtil.getAABBDistance(s.stand.getBoundingBox(), s.user.getBoundingBox());
				helper.assertTrue(Math.abs(distance - 7.5D) < 1.0E-6 && Math.abs(s.stand.getMaxRangeForMovement(s.user) - 7.5D) < 1.0E-6,
						"Crazy Diamond moved under manual control 9 blocks " + row + " of its user's feet stopped " + distance
								+ " blocks from them (" + gap + " between the boxes); it goes as far as its base reach 5 and half of"
								+ " it, 7.5, from its user's feet");
			}
		}
		helper.succeed();
	}

	// an offset 3 blocks ahead of a user who looks south, longer than Crazy Diamond's max range of 2
	@GameTest(template = "empty")
	public static void aFollowingStandTakesAnOffsetLongerThanItsMaxRangeAsIn116(GameTestHelper helper) {
		try (Scene s = new Scene(helper)) {
			helper.assertTrue(s.stand.isFollowingUser() && s.stand.getMaxRange() < 3.0D,
					"STAND-RANGE premise: Crazy Diamond does not follow its user or reaches 3 blocks: " + s.stand.getMaxRange());
			s.stand.offsetFromUser.setOffset(new Vec3(0.0D, 0.0D, 3.0D), StandOffsetFromUser.Rotations.HEAD);
			s.stand.updatePosition(s.user);
			Vec3 expected = s.user.position().add(0.0D, 0.0D, 3.0D);
			helper.assertTrue(s.stand.position().distanceTo(expected) < 1.0E-6, "Crazy Diamond following its user with an offset"
					+ " 3 blocks ahead stood at " + s.stand.position().subtract(s.user.position()) + " from its user's feet; 1.16"
					+ " updatePosition put it at the user's feet plus the whole offset, (0, 0, 3)");
		}
		helper.succeed();
	}

	/*
	 * Under remote control a close-range Stand keeps its full strength out to its base reach (5 for Crazy Diamond, its
	 * user's reach) and past it falls in a line to a quarter at the end of the margin, 7.5 (StandMeleeReach, owner
	 * 2026-10-30), where 1.16 decayed it from the effective range of 1; measured feet to feet as 1.16 did, so 5.5 above the
	 * user's feet is past the base reach though the boxes are closer. Not manually controlled it stays whole (D-core-03).
	 */
	@GameTest(template = "empty")
	public static void aManuallyControlledStandWeakensOnlyPastItsBaseReachFromItsUsersFeet(GameTestHelper helper) {
		Object[][] rows = { { "beside", 1.5D, false, 1.0D }, { "at the base reach", 5.0D, false, 1.0D },
				{ "halfway through the margin", 6.25D, false, 0.625D }, { "at the end of the margin", 7.5D, false, 0.25D },
				{ "above, halfway through the margin", 6.25D, true, 0.625D },
				{ "halfway through the margin, not manually controlled", 6.25D, false, 1.0D } };
		for (Object[] row : rows) {
			try (Scene s = new Scene(helper)) {
				String name = (String) row[0];
				double at = (Double) row[1];
				boolean above = (Boolean) row[2];
				double expected = (Double) row[3];
				boolean manual = !name.endsWith("not manually controlled");
				s.stand.setManuallyControlled(manual);
				Vec3 pos = s.user.position().add(above ? 0.0D : at, above ? at : 0.0D, 0.0D);
				s.stand.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
				s.stand.updateStrengthMultipliers();
				double feet = s.stand.distanceTo(s.user);
				double gap = MathUtil.getAABBDistance(s.stand.getBoundingBox(), s.user.getBoundingBox());
				helper.assertTrue(s.stand.isManuallyControlled() == manual && Math.abs(feet - at) < 1.0E-6
						&& Math.abs(StandMeleeReach.baseReach(s.stand, s.user) - 5.0D) < 1.0E-6,
						"STAND-RANGE premise: Crazy Diamond " + feet + " blocks from its user's feet, " + gap + " between the boxes,"
								+ " base reach " + StandMeleeReach.baseReach(s.stand, s.user));
				helper.assertTrue(Math.abs(s.stand.rangeEfficiency - expected) < 1.0E-6, "Crazy Diamond " + name + " of its"
						+ " user, " + at + " blocks from the feet (" + gap + " between the boxes): strength factor "
						+ s.stand.rangeEfficiency + " instead of " + expected);
			}
		}
		helper.succeed();
	}

	/** A Survival player 25 blocks above the test, looking south with no gravity, Crazy Diamond summoned. */
	private static final class Scene implements AutoCloseable {
		private FakePlayer user;
		private StandType standType;
		private StandPower power;
		private StandEntity stand;

		private Scene(GameTestHelper helper) {
			try {
				ServerLevel level = helper.getLevel();
				Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(BlockPos.ZERO)).add(0.0D, 25.0D, 0.0D);
				AABB space = new AABB(origin.x - 3, origin.y - 1, origin.z - 3, origin.x + 5, origin.y + 6, origin.z + 5);
				helper.assertTrue(BlockPos.betweenClosedStream(space).allMatch(level::isEmptyBlock)
						&& level.getEntities((Entity) null, space).isEmpty(), "STAND-RANGE premise: the scene is not empty");
				standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond"));
				helper.assertTrue(standType != null, "STAND-RANGE premise: missing Stand type crazy_diamond");
				user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "StandRangeMeasure"));
				user.setGameMode(GameType.SURVIVAL);
				user.setNoGravity(true);
				user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
				helper.assertTrue(level.addFreshEntity(user), "STAND-RANGE premise: could not add the user");
				power = PowerClass.STAND.attachGet(user);
				StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
				helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED && standType.summon(user, power),
						"STAND-RANGE premise: could not give and summon crazy_diamond: " + inserted.status());
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null, "STAND-RANGE premise: the summoned Stand is missing");
			}
			catch (RuntimeException | Error e) {
				close();
				throw e;
			}
		}

		@Override
		public void close() {
			if (power != null && power.isSummoned() && standType != null) {
				standType.forceUnsummon(user, power);
			}
			if (user != null) {
				user.discard();
			}
		}
	}
}
