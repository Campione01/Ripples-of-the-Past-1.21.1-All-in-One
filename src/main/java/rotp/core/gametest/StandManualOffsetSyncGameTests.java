package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModEntityTypes;
import rotp.core.network.s2c.TrDirectEntityPosPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// R148-C23: a server position sync (TrDirectEntityPosPacket) must stick on
// the client that controls the Stand manually.
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandManualOffsetSyncGameTests {
	private StandManualOffsetSyncGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void directPosSyncReanchorsManualOffset(GameTestHelper helper) {
		FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "ManualOffsetSync"));
		StandPower power = null;
		OffsetStand stand = null;
		try {
			Vec3 userPos = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(userPos.x, userPos.y, userPos.z);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant the Stand");
			stand = new OffsetStand(helper.getLevel());
			stand.withStandType(type);
			power.setSummonedStand(stand);
			helper.assertTrue(stand.getUser() == user, "Stand user did not resolve");

			// Old spot beside the user, anchored the way manual control keeps it.
			Vec3 oldPos = userPos.add(1.0D, 0.0D, 0.0D);
			stand.moveTo(oldPos.x, oldPos.y, oldPos.z);
			stand.anchor();
			Vec3 newPos = userPos.add(0.0D, 0.0D, 1.0D);
			TrDirectEntityPosPacket.applyPos(stand, newPos);
			helper.assertTrue(stand.position().distanceTo(newPos) < 1.0E-6D,
					"Position sync did not place the Stand at " + newPos);
			Vec3 offset = stand.offset();
			helper.assertTrue(offset != null && offset.distanceTo(newPos.subtract(userPos)) < 1.0E-6D,
					"Position sync kept the old manual offset " + offset);

			// A manual-control step with no input keeps the synced spot.
			stand.manualControlInput(Vec3.ZERO);
			stand.manualStep();
			helper.assertTrue(stand.position().distanceTo(newPos) < 1.0E-6D,
					"Manual control pulled the Stand back to " + stand.position());
		}
		finally {
			if (power != null) power.setSummonedStand(null);
			if (stand != null) stand.discard();
			user.discard();
		}
		helper.succeed();
	}

	/**
	 * 1.16 StandEntity.moveStandManually: each tick a key was held the Stand moved once by its motion, times 0.98
	 * (LivingEntity.aiStep's scaling on the controlling client), and it stopped once the key was let go. The port left each
	 * step as the Stand's velocity, which travel moved it by again on the next tick: two steps a tick, and one after the key.
	 */
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aHeldManualControlKeyMovesTheStandOnceATickAsIn116(GameTestHelper helper) {
		ChunkPos chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
		Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5D, helper.absolutePos(BlockPos.ZERO).getY() + 60,
				chunk.getMinBlockZ() + 0.5D);
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		user.setNoGravity(true);
		user.noPhysics = true;
		user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "Fixture: the Stand user could not be added");
		StandPower power = PowerClass.STAND.attachGet(user);
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("magicians_red"));
		helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "Fixture: Magician's Red was not granted");
		OffsetStand stand = new OffsetStand(helper.getLevel());
		stand.withStandType(type);
		stand.moveTo(origin.x + 2.0D, origin.y, origin.z, 0.0F, 0.0F);
		power.setSummonedStand(stand);
		helper.assertTrue(helper.getLevel().addFreshEntity(stand) && stand.getUser() == user,
				"Fixture: the Stand could not be added");
		stand.summonLockTicks = 0;
		stand.gradualSummonWeaknessTicks = 0;
		stand.setManuallyControlled(true);
		Vec3 motion = new Vec3(0.0D, 0.0D, 0.25D);
		int heldTicks = 8;
		List<Vec3> positions = new ArrayList<>();
		int[] calls = { 0 };
		boolean[] done = { false };
		Runnable close = () -> {
			done[0] = true;
			power.setSummonedStand(null);
			stand.discard();
			user.discard();
		};
		helper.onEachTick(() -> {
			if (done[0]) return;
			try {
				calls[0]++;
				helper.assertTrue(stand.isManuallyControlled() && !stand.isFollowingUser() && stand.isAlive(),
						"Fixture: the Stand left manual control at call " + calls[0]);
				positions.add(stand.position());
				if (calls[0] <= heldTicks) {
					stand.manualControlInput(motion);
					return;
				}
				if (calls[0] < heldTicks + 5) return;
				close.run();
				double step = motion.length() * StandEntity.MANUAL_STEP_FACTOR;
				int steps = 0;
				List<String> wrong = new ArrayList<>();
				for (int i = 1; i < positions.size(); i++) {
					Vec3 moved = positions.get(i).subtract(positions.get(i - 1));
					if (moved.length() < 1.0E-9D) continue;
					steps++;
					if (Math.abs(moved.z - step) > 1.0E-6D || Math.abs(moved.x) > 1.0E-9D || Math.abs(moved.y) > 1.0E-9D) {
						wrong.add("tick " + i + " moved " + moved);
					}
				}
				helper.assertTrue(wrong.isEmpty() && steps == heldTicks, "A held key moved the Stand other than once a tick by"
						+ " its motion 0.25 times 0.98 (" + step + "), " + heldTicks + " times, and stopped after it: " + steps
						+ " moving ticks, " + wrong + "; positions " + positions);
				helper.succeed();
			}
			catch (RuntimeException | Error e) {
				if (!done[0]) close.run();
				throw e;
			}
		});
	}

	private static final class OffsetStand extends StandEntity {
		OffsetStand(Level level) {
			super(ModEntityTypes.HUMANOID_STAND.get(), level);
		}

		// Plays the controlling client's side of manual control.
		@Override
		public boolean isControlledByLocalInstance() {
			return true;
		}

		void anchor() {
			updateUserOffset(getUser());
		}

		Vec3 offset() {
			return _offsetFromUserVec;
		}

		void manualStep() {
			moveStandManualControl();
		}
	}
}
