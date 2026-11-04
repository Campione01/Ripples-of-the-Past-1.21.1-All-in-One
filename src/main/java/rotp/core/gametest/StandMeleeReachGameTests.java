package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.impl.stands._entitybase.StandMeleeReach;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/*
 * The owner's reach rule for close-range Stands (2026-10-30 and 2026-11-01, StandMeleeReach), through the real key path:
 * The World (max range 10) punches a cow its user aims at past the user's reach of 5, flying out to it: at 8 blocks with
 * full strength, at 11.5, in the margin of 2.5 past its base reach of 10, weaker in a line to a quarter at 12.5, and at 13.5
 * not at all; away from its user it cannot guard. Its barrage flies out to the cow and stops once the cow leaves the reach.
 * An own batch: the rays run 13 blocks along the scene.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandMeleeReachGameTests {
	private static final short KEY = 121;
	private static final float COW_HEALTH = 500.0F;

	private StandMeleeReachGameTests() {}

	private enum Row {
		WITHIN_BASE_REACH(8.0D, "punch"),
		IN_THE_MARGIN(11.5D, "punch"),
		PAST_THE_MARGIN(13.5D, "punch"),
		BARRAGE(8.0D, "barrage");

		final double distance;
		final String ability;

		Row(double distance, String ability) {
			this.distance = distance;
			this.ability = ability;
		}
	}

	@GameTest(template = "empty", timeoutTicks = 400, batch = "rotp_t2_melee_reach")
	public static void theWorldStrikesAMobItsUserAimsAtPastItsUsersReachWeakerInTheMargin(GameTestHelper helper) {
		Run run = new Run(helper);
		helper.onEachTick(run::tick);
	}

	private static final class Run implements GameTestListener {
		final GameTestHelper helper;
		final List<String> wrong = new ArrayList<>();
		Scene scene;
		int index;
		float fullLoss = Float.NaN;
		boolean closed;

		Run(GameTestHelper helper) {
			this.helper = helper;
			helper.testInfo.addListener(this);
		}

		void tick() {
			if (closed) {
				return;
			}
			try {
				if (scene != null && scene.done) {
					scene.close();
					scene = null;
				}
				if (scene == null) {
					if (index == Row.values().length) {
						close();
						helper.assertTrue(wrong.isEmpty(), "The World's reach out: " + String.join("; ", wrong));
						helper.succeed();
						return;
					}
					scene = new Scene(this, Row.values()[index++]);
					scene.start();
					return;
				}
				scene.tick();
			}
			catch (RuntimeException | Error error) {
				close();
				throw error;
			}
		}

		void close() {
			if (closed) {
				return;
			}
			closed = true;
			if (scene != null) {
				scene.close();
			}
		}

		@Override public void testStructureLoaded(GameTestInfo test) {}
		@Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
		@Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
		@Override public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) { close(); }
	}

	private static final class Scene implements AutoCloseable {
		final Run run;
		final Row row;
		Player user;
		StandType type;
		StandPower power;
		StandEntity stand;
		Cow cow;
		EntityActionInstance action;
		int ticks;
		boolean movedAway;
		int ticksAway;
		boolean done;
		boolean closed;

		Scene(Run run, Row row) {
			this.run = run;
			this.row = row;
		}

		void start() {
			GameTestHelper helper = run.helper;
			ChunkPos chunk = new ChunkPos(helper.absolutePos(BlockPos.ZERO));
			// at the chunk's north edge, 60 blocks up, so that the cow 14 blocks south stays in the chunk, in the air
			Vec3 origin = new Vec3(chunk.getMiddleBlockX() + 0.5D, helper.absolutePos(BlockPos.ZERO).getY() + 60,
					chunk.getMinBlockZ() + 0.5D);
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.noPhysics = true;
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			premise(helper.getLevel().addFreshEntity(user), "the user could not be added");
			power = PowerClass.STAND.attachGet(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
			premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && type.summon(user, power), "could not give and summon The World");
			stand = power.getSummonedStandEntity();
			premise(stand != null && stand.getUser() == user, "the summoned Stand is missing");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			power.setStamina(power.getMaxStamina());
			cow = EntityType.COW.create(helper.getLevel());
			premise(cow != null, "could not create the cow");
			cow.getAttribute(Attributes.MAX_HEALTH).setBaseValue(COW_HEALTH);
			cow.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1.0D);
			cow.setHealth(COW_HEALTH);
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.noPhysics = true;
			cow.moveTo(origin.x, origin.y, origin.z + row.distance, 0.0F, 0.0F);
			premise(helper.getLevel().addFreshEntity(cow), "the cow could not be added");
			user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
			user.setYHeadRot(user.getYRot());
			user.yRotO = user.getYRot();
			user.xRotO = user.getXRot();
			stand.updatePosition(user);
			// a client's aims stop at the user's reach, so they met nothing
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
			premise(StandMeleeReach.isCloseRange(stand) && Math.abs(StandMeleeReach.baseReach(stand, user) - 10.0D) < 1.0E-6
					&& StandMeleeReach.userDistance(user, cow) > 5.0D,
					"The World's base reach is " + StandMeleeReach.baseReach(stand, user) + ", the cow "
							+ StandMeleeReach.userDistance(user, cow) + " from the user's eyes");
			Ability base = power.getAbility(row.ability);
			var selected = power.updateAvailableMoves().getContextVariationContainer(base);
			premise(selected != null, "no " + row.ability + " to press");
			boolean barrage = row == Row.BARRAGE;
			AbilityInput.keyPress(KEY, selected.ability, user, null, barrage ? InputMethod.HOLD : InputMethod.CLICK, 0,
					BufferingState.clickOnly(), base.getAbilityId());
			action = stand.getCurStandAction();
			premise(action != null && (!barrage || action instanceof StandEntityBarrage), "the " + row.ability
					+ " did not start: " + action);
			premise(!barrage || StandEntityBarrageAbility.isDirectionalBarrage(stand, action), "the barrage is not the"
					+ " default one, which aims each hit along its user's look");
		}

		void tick() {
			ticks++;
			if (ticks > 40) {
				run.wrong.add(row + ": nothing happened in 40 ticks (phase " + action.getPhase() + ")");
				done = true;
				return;
			}
			if (row == Row.BARRAGE) {
				barrageTick();
			}
			else if (action.punchedTarget != null) {
				punchStruck();
			}
		}

		private void punchStruck() {
			float loss = COW_HEALTH - cow.getHealth();
			double away = stand.distanceTo(user);
			double fromEyes = StandMeleeReach.userDistance(user, cow);
			switch (row) {
				case WITHIN_BASE_REACH -> {
					check(loss > 0, "the punch did not hit the cow " + fromEyes + " from its user's eyes");
					check(away > StandMeleeReach.AWAY_DISTANCE && !stand.canStartBlocking(), "the Stand did not fly out to the"
							+ " cow or could guard: it is " + away + " from its user");
					run.fullLoss = loss;
				}
				case IN_THE_MARGIN -> {
					float expected = StandStatFormulas.meleeReachStrengthFactor(fromEyes, 10.0D, 2.5D);
					check(expected > 0.25F && expected < 1.0F, "the cow " + fromEyes + " from the eyes is not in the margin");
					check(away > StandMeleeReach.AWAY_DISTANCE, "the Stand did not fly out: " + away + " from its user");
					check(Math.abs(loss - run.fullLoss * expected) < 1.0E-3F, "the punch in the margin took " + loss
							+ " health; at full strength " + run.fullLoss + ", times " + expected);
				}
				case PAST_THE_MARGIN -> {
					check(loss == 0, "the punch hit the cow " + fromEyes + " from its user's eyes, past the 12.5 it reaches");
					check(away <= StandMeleeReach.AWAY_DISTANCE && stand.canStartBlocking(), "the Stand flew out or could not"
							+ " guard with no target: it is " + away + " from its user");
				}
				default -> {}
			}
			done = true;
		}

		private void barrageTick() {
			if (!movedAway) {
				if (cow.getHealth() < COW_HEALTH) {
					double away = stand.distanceTo(user);
					check(away > StandMeleeReach.AWAY_DISTANCE && !stand.canStartBlocking(), "the barrage's Stand did not fly"
							+ " out to the cow or could guard: it is " + away + " from its user, in tick " + ticks + ", its offset "
							+ stand.offsetFromUser.getRelativeOffset());
					check(action.getPhase() == ActionPhase.PERFORM, "the barrage hit and left its perform");
					cow.moveTo(cow.getX(), cow.getY(), user.getZ() + 14.0D, 0.0F, 0.0F);
					movedAway = true;
				}
				return;
			}
			if (action.getPhase() == ActionPhase.RECOVERY || action.isOver()) {
				done = true;
				return;
			}
			if (++ticksAway > 2) {
				check(false, "the barrage still performs " + ticksAway + " ticks after its cow left its reach");
				done = true;
			}
		}

		private void check(boolean passed, String detail) {
			if (!passed) {
				run.wrong.add(row + ": " + detail);
			}
		}

		private void premise(boolean passed, String detail) {
			run.helper.assertTrue(passed, "Fixture " + row + ": " + detail);
		}

		@Override
		public void close() {
			if (closed) {
				return;
			}
			closed = true;
			if (user != null) {
				AbilityInput.keyRelease(KEY, user);
			}
			if (power != null && power.isSummoned() && type != null) {
				type.forceUnsummon(user, power);
			}
			if (cow != null) {
				cow.discard();
			}
			if (user != null) {
				user.discard();
			}
		}
	}
}
