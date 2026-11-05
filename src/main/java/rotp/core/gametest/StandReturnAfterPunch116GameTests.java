package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModSpecialActions;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandOffsetFromUser;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * 1.16 StandEntity.clearTask and updatePosition: a task that ended with its Stand 2 or more blocks from its user
 * (isCloseToUser: distanceToSqr below 4) retracted it (standRetractsAfterTask), the Stand moving toward its idle spot at its
 * movement speed (StandStatFormulas.getMovementSpeed: 0.1 + 0.05 x speed) until it was within 2 blocks, then following its
 * idle offset again; a client glided a changed offset over 4 ticks from the old one (offsetLerpMaxTicks), whatever the
 * server did meanwhile.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandReturnAfterPunch116GameTests {

	private StandReturnAfterPunch116GameTests() {}

	@GameTest(template = "empty", timeoutTicks = 120)
	public static void aStandTwoBlocksOutAfterAPunchAtNothingIsPulledBackAtItsMovementSpeedAsIn116(GameTestHelper helper) {
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		user.setNoGravity(true);
		user.noPhysics = true;
		user.moveTo(origin.x, origin.y, origin.z, 0, 0);
		user.setYHeadRot(0);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: the Stand user was not added");
		StandPower power = PowerClass.STAND.attachGet(user);
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("magicians_red"));
		helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "setup: Magician's Red was not granted");
		helper.assertTrue(type.summon(user, power), "setup: Magician's Red was not summoned");
		StandEntity stand = power.getSummonedStandEntity();
		helper.assertTrue(stand != null, "setup: the summoned Stand is missing");
		stand.summonLockTicks = 0;
		stand.gradualSummonWeaknessTicks = 0;
		power.setStamina(power.getMaxStamina());
		LivingComponentAction.getComponent(user).entityAim.setTargetFromClient(ActionTarget.EMPTY, helper.getLevel());
		double speed = stand.getAttributeValue(Attributes.MOVEMENT_SPEED);
		helper.assertTrue(Math.abs(speed - StandStatFormulas.getMovementSpeed(stand.getAttackSpeed())) < 1.0E-6,
				"setup: the Stand's movement speed " + speed + " is not 0.1 + 0.05 x " + stand.getAttackSpeed());

		int[] ticks = { 0 };
		int[] ended = { -1 };
		EntityActionInstance[] punch = { null };
		Vec3[] last = { null };
		boolean[] wasRetracted = { false };
		List<String> wrong = new ArrayList<>();
		List<String> rows = new ArrayList<>();
		boolean[] done = { false };
		helper.onEachTick(() -> {
			if (done[0]) return;
			try {
				ticks[0]++;
				if (ticks[0] == 5) {
					punch[0] = ((EntityActionType) power.getAbility("punch")).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
					punch[0].setStartedByClick(true);
					LivingComponentAction.getComponent(stand).setAction(punch[0], user, SyncType.NO_SYNC);
					return;
				}
				if (punch[0] == null) return;
				Vec3 pos = stand.position();
				double dist = Math.sqrt(stand.distanceToSqr(user));
				if (ended[0] < 0) {
					if (stand.getCurStandAction() == punch[0]) {
						last[0] = pos;
						return;
					}
					ended[0] = ticks[0];
					if (last[0].distanceTo(user.position()) < 2 || !stand.isBeingRetracted()) {
						wrong.add("ended " + last[0].distanceTo(user.position()) + " blocks out, retracted " + stand.isBeingRetracted());
					}
				}
				int after = ticks[0] - ended[0];
				rows.add("+" + after + " " + pos.subtract(user.position()) + " retracted " + stand.isBeingRetracted()
						+ " lerp " + stand.offsetFromUser.getLerpAmount());
				if (last[0] != null && wasRetracted[0] && pos.distanceTo(last[0]) > speed + 1.0E-6) {
					wrong.add("+" + after + ": a retracted Stand moved " + pos.distanceTo(last[0]) + " in a tick");
				}
				wasRetracted[0] = stand.isBeingRetracted();
				// a client's offset glide, from the punch's offset to the idle one, a quarter at a time
				float lerp = stand.offsetFromUser.getLerpAmount();
				if (after <= StandOffsetFromUser.LERP_TIME && Math.abs(lerp - (float) after / StandOffsetFromUser.LERP_TIME) > 1.0E-6) {
					wrong.add("+" + after + ": the offset glide is at " + lerp);
				}
				last[0] = pos;
				if (after == 8) {
					done[0] = true;
					Vec3 idle = stand.offsetFromUser.getPosition(user);
					if (stand.isBeingRetracted() || pos.distanceTo(idle) > 1.0E-6 || dist >= 2) {
						wrong.add("+8: not back at its idle spot " + idle + " but at " + pos + ", retracted " + stand.isBeingRetracted());
					}
					if (rows.stream().filter(row -> row.contains("retracted true")).count() < 2) {
						wrong.add("the retraction did not last the ticks a pull from 2 blocks at " + speed + " takes");
					}
					type.forceUnsummon(user, power);
					user.discard();
					helper.assertTrue(wrong.isEmpty(), "1.16 pulled a Stand ended 2 blocks out back at its movement speed ("
							+ speed + ") until it was within 2 blocks, then put it on its idle offset, a client gliding the"
							+ " offset over 4 ticks: " + wrong + "; ticks " + rows);
					helper.succeed();
				}
			}
			catch (RuntimeException | Error e) {
				done[0] = true;
				if (power.isSummoned()) type.forceUnsummon(user, power);
				user.discard();
				throw e;
			}
		});
	}

	/**
	 * 1.16 clearTask and retractStand(true) asked the Stand's own isFollowingUser, which add-ons override (Aqua Necklace in
	 * its host, Diver Down in a target, C-Moon on its target, Purple Haze mad, RHCP in a wire): a Stand its override keeps
	 * from following is not called back after its action, the unsummon input calls it back, and a Stand its action moved
	 * by itself (Magician's Red's kick within its reach) is still called back after it.
	 */
	@GameTest(template = "empty")
	public static void aStandsOwnFollowingRuleDecidesItsRecallAfterAnActionAsIn116(GameTestHelper helper) {
		List<Runnable> cleanup = new ArrayList<>();
		try {
			ChunkPos chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
			Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5D, helper.absolutePos(BlockPos.ZERO).getY() + 60,
					chunk.getMinBlockZ() + 0.5D);
			Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.noPhysics = true;
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Fixture: the user could not be added");
			cleanup.add(user::discard);
			StandPower power = PowerClass.STAND.attachGet(user);
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("magicians_red"));
			helper.assertTrue(type instanceof EntityStandType && StandPowerTransitions.insert(power, new StandInstance(type))
					.status() == StandPowerTransitions.Status.APPLIED, "Fixture: Magician's Red was not granted");
			cleanup.add(() -> {
				if (power.isSummoned()) {
					LivingComponentAction.getComponent(power.getSummonedStandEntity()).setAction(null, SyncType.NO_SYNC);
					type.forceUnsummon(user, power);
				}
			});
			power.setStamina(power.getMaxStamina());
			LivingComponentAction.getComponent(user).entityAim.setTargetFromClient(ActionTarget.EMPTY, helper.getLevel());
			Vec3 threeOut = new Vec3(origin.x + 3.0D, origin.y, origin.z);

			helper.assertTrue(type.summon(user, power), "Fixture: Magician's Red was not summoned");
			StandEntity kicker = power.getSummonedStandEntity();
			helper.assertTrue(kicker != null && kicker.getUser() == user && kicker.isFollowingUser(),
					"Fixture: no Magician's Red following its user");
			kicker.summonLockTicks = 0;
			kicker.gradualSummonWeaknessTicks = 0;
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Fixture: no cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.noPhysics = true;
			cow.moveTo(origin.x, origin.y, origin.z + 3.0D, 0.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Fixture: the cow could not be added");
			cleanup.add(cow::discard);
			LivingComponentAction.getComponent(kicker).entityAim.setTarget(new ActionTarget(cow));
			helper.assertTrue(power.getAbility("kick") instanceof EntityActionType, "Fixture: Magician's Red has no kick");
			EntityActionType kickType = (EntityActionType) power.getAbility("kick");
			EntityActionInstance kick = kickType.createActionObj();
			kickType.initActionFromConfig(kick, helper.getLevel(), user, kicker);
			kick.phasesLength.put(ActionPhase.BUTTON_CHARGE, 0.0F);
			kick.phasesLength.put(ActionPhase.WINDUP, 10.0F);
			kick.phasesLength.put(ActionPhase.PERFORM, 1.0F);
			kick.phasesLength.put(ActionPhase.RECOVERY, 10.0F);
			kick.setStartingPhase();
			LivingComponentAction.getComponent(kicker).setAction(kick, user, SyncType.NO_SYNC);
			kick.setPhase(ActionPhase.WINDUP, 8);
			kicker.moveTo(threeOut.x, threeOut.y, threeOut.z, 0.0F, 0.0F);
			helper.assertTrue(kicker.getCurStandAction() == kick && !kicker.isFollowingUser() && !kicker.isBeingRetracted(),
					"Fixture: a kick at a cow within the Stand's reach did not move it by itself: action "
							+ kicker.getCurStandAction() + ", following " + kicker.isFollowingUser());
			LivingComponentAction.getComponent(kicker).setAction(null, SyncType.NO_SYNC);
			helper.assertTrue(kicker.isBeingRetracted(), "A Stand its kick moved by itself, 3 blocks from its user, was not"
					+ " called back when the kick ended; 1.16 clearTask's isFollowingUser had no term for the ended action");
			type.forceUnsummon(user, power);

			boolean[] heldByItsOwnRule = { true };
			@SuppressWarnings("unchecked")
			EntityType<? extends StandEntity> entityType = (EntityType<? extends StandEntity>) ((EntityStandType) type)
					.getEntityType();
			StandEntity held = new StandEntity(entityType, helper.getLevel()) {
				@Override
				public boolean isFollowingUser() {
					return !heldByItsOwnRule[0] && super.isFollowingUser();
				}
			}.withStandType(type);
			held.copyPosition(user);
			held.copyStandUserRotation(user);
			power.setSummonedStand(held);
			((EntityStandType) type).finalizeStandSummonFromAction(user, power, held, true);
			helper.assertTrue(power.getSummonedStandEntity() == held && held.getUser() == user && held.isAddedToLevel()
					&& !held.isFollowingUser(), "Fixture: the Stand with a following rule of its own was not summoned");
			held.summonLockTicks = 0;
			held.gradualSummonWeaknessTicks = 0;
			held.setNoGravity(true);
			held.moveTo(threeOut.x, threeOut.y, threeOut.z, 0.0F, 0.0F);
			EntityActionInstance punch = ((EntityActionType) power.getAbility("punch"))
					.initActionOnAbilityUse(helper.getLevel(), user, held, null);
			punch.setStartedByClick(true);
			LivingComponentAction.getComponent(held).setAction(punch, user, SyncType.NO_SYNC);
			helper.assertTrue(held.getCurStandAction() == punch, "Fixture: the punch was not set: " + held.getCurStandAction());
			LivingComponentAction.getComponent(held).setAction(null, SyncType.NO_SYNC);
			helper.assertTrue(!held.isBeingRetracted() && held.getCurStandAction() == null,
					"A Stand its own isFollowingUser keeps from following, 3 blocks from its user, was called back after its"
							+ " punch; 1.16 clearTask retracted only a Stand its isFollowingUser said followed its user");
			held.onUnsummonUserInput();
			EntityActionInstance unsummon = held.getCurStandAction();
			helper.assertTrue(held.isBeingRetracted() && unsummon != null
					&& unsummon.ability == ModSpecialActions.STAND_UNSUMMON.get(), "The unsummon input did not call back a"
							+ " Stand its own isFollowingUser keeps from following (1.16 retractStand(true)): retracted "
							+ held.isBeingRetracted() + ", action " + unsummon);
			heldByItsOwnRule[0] = false;
			helper.assertTrue(!held.isFollowingUser(), "A Stand being called back counted as following its user");
		}
		finally {
			for (int i = cleanup.size() - 1; i >= 0; i--) {
				cleanup.get(i).run();
			}
		}
		helper.succeed();
	}
}
