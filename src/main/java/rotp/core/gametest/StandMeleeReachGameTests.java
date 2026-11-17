package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.impl.stands._entitybase.StandMeleeReach;
import rotp.core.impl.stands.magiciansred.MagiciansRedKickAbility.MagiciansRedKick;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.subsystems.target.HitResultUtil;

import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
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
	private static final ResourceLocation REACH_MODIFIER = JojoMod.resLoc("gametest_melee_reach");

	private StandMeleeReachGameTests() {}

	private enum Row {
		WITHIN_BASE_REACH(8.0D, "punch"),
		IN_THE_MARGIN(11.5D, "punch"),
		PAST_THE_MARGIN(13.5D, "punch"),
		PAST_THE_MARGIN_IN_THE_AIM_HITBOX(13.1D, "punch"),
		BARRAGE(8.0D, "barrage"),
		BLOCK_REACH_MOD(14.3D, "punch"),
		ENTITY_REACH_MOD(14.3D, "punch"),
		REMOVED_ENTITY_REACH_MOD(14.3D, "punch"),
		KISS_BASE(12.44D, "punch"),
		KISS_MARGIN(14.3D, "punch"),
		GREEN_DAY_NEAR(6.4D, "punch"),
		GREEN_DAY_FAR(9.0D, "punch"),
		GREEN_DAY_REACH_MOD(13.0D, "punch");

		final double distance;
		final String ability;

		Row(double distance, String ability) {
			this.distance = distance;
			this.ability = ability;
		}
	}

	@GameTest(template = "empty", timeoutTicks = 400, batch = "rotp_t2_melee_reach")
	public static void theWorldStrikesAMobItsUserAimsAtPastItsUsersReachWeakerInTheMargin(GameTestHelper helper) {
		Run run = new Run(helper, List.of(Row.WITHIN_BASE_REACH, Row.IN_THE_MARGIN, Row.PAST_THE_MARGIN,
				Row.PAST_THE_MARGIN_IN_THE_AIM_HITBOX, Row.BARRAGE));
		helper.onEachTick(run::tick);
	}

	@GameTest(template = "empty", timeoutTicks = 200, batch = "rotp_live_melee_reach")
	public static void realPunchFollowsLiveBlockAndEntityReachModifiers(GameTestHelper helper) {
		Run run = new Run(helper, List.of(Row.BLOCK_REACH_MOD, Row.ENTITY_REACH_MOD, Row.REMOVED_ENTITY_REACH_MOD));
		helper.onEachTick(run::tick);
	}

	@GameTest(template = "empty", timeoutTicks = 260, batch = "rotp_owner_melee_reach_groups")
	public static void realPunchUsesTheOwnersTwoExplicitMeleeGroups(GameTestHelper helper) {
		// Core bodies with the actual add-on IDs and declared ranges; no add-on assets or registries are installed here.
		Run run = new Run(helper, List.of(Row.KISS_BASE, Row.KISS_MARGIN, Row.GREEN_DAY_NEAR, Row.GREEN_DAY_FAR, Row.GREEN_DAY_REACH_MOD));
		helper.onEachTick(run::tick);
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = "rotp_creative_melee_reach")
	public static void vanillaCreativeBonusesDoNotMultiplyModdedReach(GameTestHelper helper) {
		try (Scene scene = new Scene(new Run(helper, List.of()), Row.WITHIN_BASE_REACH)) {
			scene.start();
			var blocks = scene.user.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
			var entities = scene.user.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
			blocks.addTransientModifier(new AttributeModifier(ResourceLocation.withDefaultNamespace("creative_mode_block_range"),
					0.5D, AttributeModifier.Operation.ADD_VALUE));
			entities.addTransientModifier(new AttributeModifier(ResourceLocation.withDefaultNamespace("creative_mode_entity_range"),
					2.0D, AttributeModifier.Operation.ADD_VALUE));
			helper.assertTrue(StandEntityPunchAbility.userAimReach(scene.user, scene.stand) == 5.0D,
					"Creative alone raised the holder's five-block melee aim");
			blocks.addTransientModifier(new AttributeModifier(REACH_MODIFIER, 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			helper.assertTrue(StandEntityPunchAbility.userAimReach(scene.user, scene.stand) == 9.5D,
					"A mod multiplying block reach must not also multiply Creative's bonus");
			blocks.removeModifier(REACH_MODIFIER);
			entities.addTransientModifier(new AttributeModifier(REACH_MODIFIER, 2.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			helper.assertTrue(StandEntityPunchAbility.userAimReach(scene.user, scene.stand) == 11.0D,
					"A mod multiplying entity reach must not also multiply Creative's bonus");
			blocks.addTransientModifier(new AttributeModifier(REACH_MODIFIER, 1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
			helper.assertTrue(StandEntityPunchAbility.userAimReach(scene.user, scene.stand) == 11.0D,
					"Two simultaneous mods must use the larger bonus, not the sum");
			entities.removeModifier(REACH_MODIFIER);
			helper.assertTrue(StandEntityPunchAbility.userAimReach(scene.user, scene.stand) == 9.5D,
					"Removing the larger bonus must reveal the remaining live block bonus");
		}
		helper.succeed();
	}

	/*
	 * Magician's Red (range 5 to 10, base reach 10, margin 2.5) kicks a cow 12.3 blocks ahead: the kick flies out in front
	 * of it and follows its user through the strike instead of moving by itself, so the strike reaches out at about 0.44
	 * of its strength; a kick at a cow 3 blocks ahead keeps 1.16's slide and moves by itself.
	 */
	@GameTest(template = "empty", timeoutTicks = 60, batch = "rotp_t2_melee_reach_kick")
	public static void magiciansRedsKickFliesOutAndStrikesWeakerPastItsBaseReach(GameTestHelper helper) {
		List<Runnable> cleanup = new ArrayList<>();
		Runnable close = () -> {
			for (int i = cleanup.size() - 1; i >= 0; i--) {
				cleanup.get(i).run();
			}
			cleanup.clear();
		};
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
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && type.summon(user, power), "Fixture: could not give and summon"
							+ " Magician's Red");
			cleanup.add(() -> {
				if (power.isSummoned()) {
					LivingComponentAction.getComponent(power.getSummonedStandEntity()).setAction(null, SyncType.NO_SYNC);
					type.forceUnsummon(user, power);
				}
			});
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.getUser() == user && stand.isFollowingUser(),
					"Fixture: no Magician's Red following its user");
			stand.summonLockTicks = 0;
			stand.gradualSummonWeaknessTicks = 0;
			stand.updatePosition(user);
			Cow cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Fixture: no cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.noPhysics = true;
			cow.moveTo(origin.x, origin.y, origin.z + 3.0D, 0.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Fixture: the cow could not be added");
			cleanup.add(cow::discard);
			helper.assertTrue(StandMeleeReach.isCloseRange(stand)
					&& Math.abs(StandMeleeReach.baseReach(stand, user) - 10.0D) < 1.0E-6, "Fixture: Magician's Red's base"
							+ " reach is " + StandMeleeReach.baseReach(stand, user));

			MagiciansRedKick near = kickAt(helper, power, user, stand, cow);
			near.setPhase(ActionPhase.WINDUP, 8);
			helper.assertTrue(!near.fliesOut() && !stand.isFollowingUser(),
					"A kick at a cow 3 blocks ahead, within the Stand's own reach, flew out (" + near.fliesOut()
							+ ") or kept following its user in its last windup ticks (" + stand.isFollowingUser()
							+ "); 1.16's slide moves it by itself");
			LivingComponentAction.getComponent(stand).setAction(null, SyncType.NO_SYNC);

			cow.moveTo(origin.x, origin.y, origin.z + 12.3D, 0.0F, 0.0F);
			stand.updatePosition(user);
			MagiciansRedKick far = kickAt(helper, power, user, stand, cow);
			double forward = stand.offsetFromUser.getRelativeOffset().z;
			helper.assertTrue(far.fliesOut() && forward > 10.0D && forward < 11.5D,
					"A kick at a cow 12.3 blocks ahead did not fly out in front of it (10.85 forward): flies out "
							+ far.fliesOut() + ", offset " + stand.offsetFromUser.getRelativeOffset());
			far.setPhase(ActionPhase.WINDUP, 8);
			helper.assertTrue(stand.isFollowingUser(), "A kick that flew out moved by itself in its last windup ticks");
			far.setPhase(ActionPhase.PERFORM, 0);
			float factor = StandMeleeReach.strikeFactor(stand, cow);
			helper.assertTrue(stand.isFollowingUser() && StandMeleeReach.reachable(stand, cow) && factor > 0.4F
							&& factor < 0.5F,
					"A kick that flew out to a cow " + StandMeleeReach.userDistance(user, cow) + " from its user's eyes"
							+ " did not strike it at about 0.44 of its strength (base reach 10, margin 2.5): following "
							+ stand.isFollowingUser() + ", reachable " + StandMeleeReach.reachable(stand, cow)
							+ ", factor " + factor);
		}
		finally {
			close.run();
		}
		helper.succeed();
	}

	// the kick as its press sets it: the Stand's aim met the cow, as its user's client sent it
	private static MagiciansRedKick kickAt(GameTestHelper helper, StandPower power, Player user, StandEntity stand,
			Cow cow) {
		LivingComponentAction.getComponent(stand).entityAim.setTarget(new ActionTarget(cow));
		helper.assertTrue(power.getAbility("kick") instanceof EntityActionType, "Fixture: Magician's Red has no kick");
		EntityActionType kickType = (EntityActionType) power.getAbility("kick");
		EntityActionInstance kick = kickType.createActionObj();
		kickType.initActionFromConfig(kick, helper.getLevel(), user, stand);
		kick.phasesLength.put(ActionPhase.BUTTON_CHARGE, 0.0F);
		kick.phasesLength.put(ActionPhase.WINDUP, 10.0F);
		kick.phasesLength.put(ActionPhase.PERFORM, 1.0F);
		kick.phasesLength.put(ActionPhase.RECOVERY, 10.0F);
		kick.setStartingPhase();
		LivingComponentAction.getComponent(stand).setAction(kick, user, SyncType.NO_SYNC);
		helper.assertTrue(stand.getCurStandAction() == kick && kick instanceof MagiciansRedKick,
				"Fixture: the kick was not set: " + stand.getCurStandAction());
		return (MagiciansRedKick) kick;
	}

	private static final class Run implements GameTestListener {
		final GameTestHelper helper;
		final List<Row> rows;
		final List<String> wrong = new ArrayList<>();
		Scene scene;
		int index;
		float fullLoss = Float.NaN;
		boolean closed;

		Run(GameTestHelper helper, List<Row> rows) {
			this.helper = helper;
			this.rows = rows;
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
					if (index == rows.size()) {
						close();
						helper.assertTrue(wrong.isEmpty(), "Melee reach: " + String.join("; ", wrong));
						helper.succeed();
						return;
					}
					scene = new Scene(this, rows.get(index++));
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
		Vec3 lastOffset;
		double longestStep;
		double farthest;
		boolean guardedWhileAway;
		int ticksSinceStrike;
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
			type = switch (row) {
				case KISS_BASE, KISS_MARGIN -> testMeleeStand("kiss:kiss", 4.0D, 12.0D, 17.0D, 17.0D);
				case GREEN_DAY_NEAR, GREEN_DAY_FAR, GREEN_DAY_REACH_MOD -> testMeleeStand("rotp_zgd:green_day", 2.0D, 128.0D, 15.7D, 10.0D);
				default -> JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
			};
			premise(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && type.summon(user, power), "could not give and summon the fixture Stand");
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
			cow.moveTo(origin.x, origin.y + (row == Row.KISS_BASE ? 0.35D : 0.0D),
					origin.z + row.distance, 0.0F, 0.0F);
			premise(helper.getLevel().addFreshEntity(cow), "the cow could not be added");
			user.lookAt(EntityAnchorArgument.Anchor.EYES, cow.getBoundingBox().getCenter());
			user.setYHeadRot(user.getYRot());
			user.yRotO = user.getYRot();
			user.xRotO = user.getXRot();
			stand.updatePosition(user);
			// the flight starts from the offset a Stand holds once it has ticked; this one was summoned just now
			stand.resetManualOffset();
			lastOffset = stand.position().subtract(user.position());
			// a client's aims stop at the user's reach, so they met nothing
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			LivingComponentAction.getComponent(stand).entityAim.setTarget(ActionTarget.EMPTY);
			premise(StandMeleeReach.userDistance(user, cow) > 5.0D, "Expected a cow beyond the holder's unaided five blocks");
			if (type.getId().equals(JojoMod.resLoc("the_world"))) {
				premise(StandMeleeReach.isCloseRange(stand) && StandMeleeReach.baseReach(stand, user) == 10.0D,
						"The World's base reach must remain 10");
			}
			if (row == Row.BLOCK_REACH_MOD || row == Row.ENTITY_REACH_MOD || row == Row.REMOVED_ENTITY_REACH_MOD
					|| row == Row.GREEN_DAY_REACH_MOD) {
				var reach = user.getAttribute(row == Row.BLOCK_REACH_MOD || row == Row.GREEN_DAY_REACH_MOD
						? Attributes.BLOCK_INTERACTION_RANGE : Attributes.ENTITY_INTERACTION_RANGE);
				reach.addTransientModifier(new AttributeModifier(REACH_MODIFIER, 7.0D, AttributeModifier.Operation.ADD_VALUE));
				if (row == Row.REMOVED_ENTITY_REACH_MOD) {
					check(StandMeleeReach.baseReach(stand, user) == 12.0D, "the added entity bonus was not visible before removal");
					reach.removeModifier(REACH_MODIFIER);
				}
			}
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
			if (row == Row.GREEN_DAY_REACH_MOD) {
				flightTick();
			}
			else if (row == Row.BARRAGE) {
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
			if (row.ordinal() > Row.BARRAGE.ordinal()) {
				JojoMod.getLogger().info("MELEE-REACH row={} distance={} base={} damage={}",
						row, fromEyes, StandMeleeReach.baseReach(stand, user), loss);
			}
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
				case PAST_THE_MARGIN_IN_THE_AIM_HITBOX -> {
					double max = StandMeleeReach.maxReach(stand, user, false);
					Vec3 eyes = user.getEyePosition();
					boolean inHitbox = HitResultUtil.standPrecisionTargetHitbox(cow.getBoundingBox().inflate(cow.getPickRadius()),
							stand.getPrecision()).clip(eyes, eyes.add(user.getLookAngle().scale(max))).isPresent();
					check(fromEyes > max && inHitbox, "fixture: the cow " + fromEyes + " from the eyes must be past the " + max
							+ " it reaches, with its grown hitbox on the ray: " + inHitbox);
					ActionTarget pick = StandEntityPunchAbility.strikeAim(stand, ActionTarget.EMPTY, StandEntityPunchAbility.CrosshairRule.NONE);
					check(pick.getType() != ActionTarget.TargetType.ENTITY, "the aim assist picked the cow past the reach through its grown hitbox");
					check(loss == 0, "the punch hit the cow past the reach: " + loss);
				}
				case BLOCK_REACH_MOD, ENTITY_REACH_MOD -> {
					check(fromEyes > 12.5D && fromEyes <= 15.0D && loss > 0
									&& StandMeleeReach.baseReach(stand, user) == 12.0D,
							"the live reach modifier did not extend the actual punch: distance " + fromEyes + ", damage " + loss);
				}
				case REMOVED_ENTITY_REACH_MOD -> {
					check(loss == 0 && StandMeleeReach.baseReach(stand, user) == 10.0D,
							"the punch still used removed reach: distance " + fromEyes + ", damage " + loss);
				}
				case KISS_BASE -> {
					check(fromEyes >= 11.9D && fromEyes <= 12.0D && loss > 0
									&& StandMeleeReach.baseReach(stand, user) == 12.0D
									&& StandMeleeReach.strikeFactor(stand, cow) == 1.0F,
							"Kiss did not land a full punch at its declared reach: " + fromEyes + ", damage " + loss);
					run.fullLoss = loss;
				}
				case KISS_MARGIN -> {
					float expected = StandStatFormulas.meleeReachStrengthFactor(fromEyes, 12.0D, 3.0D);
					check(expected > 0.25F && expected < 1.0F && loss > 0
									&& Math.abs(loss - run.fullLoss * expected) < 1.0E-3F,
							"Kiss did not weaken through its normal margin: " + fromEyes + ", damage " + loss);
				}
				case GREEN_DAY_NEAR, GREEN_DAY_FAR -> {
					boolean inside = row == Row.GREEN_DAY_NEAR;
					check(StandMeleeReach.baseReach(stand, user) == 5.0D && !StandMeleeReach.isCloseRange(stand)
									&& stand.getMaxRange() == 128.0D && stand.getEffectiveRange() == 2.0D
									&& (fromEyes <= 6.25D) == inside && (loss > 0) == inside,
							"Green Day must use the holder's margin, not its ability/control distance: "
									+ fromEyes + ", damage " + loss);
				}
				default -> {}
			}
			done = true;
		}

		/*
		 * A reach mod is what puts this group's strikes further than a tick of its flight. Green Day strikes on its first tick
		 * and flies out to the cow after it, a tick at most at its own speed; while away it cannot guard.
		 */
		private void flightTick() {
			Vec3 offset = stand.position().subtract(user.position());
			if (lastOffset != null) {
				longestStep = Math.max(longestStep, offset.distanceTo(lastOffset));
			}
			lastOffset = offset;
			farthest = Math.max(farthest, offset.length());
			guardedWhileAway |= offset.length() > StandMeleeReach.awayDistance(stand) && stand.canStartBlocking();
			if (action.punchedTarget == null || ++ticksSinceStrike < 4) {
				return;
			}
			float loss = COW_HEALTH - cow.getHealth();
			double speed = StandMeleeReach.flightSpeed(stand);
			JojoMod.getLogger().info("MELEE-REACH row={} distance={} base={} damage={} farthest={} longestStep={} speed={}",
					row, StandMeleeReach.userDistance(user, cow), StandMeleeReach.baseReach(stand, user), loss, farthest, longestStep, speed);
			check(StandMeleeReach.baseReach(stand, user) == 12.0D && !StandMeleeReach.isCloseRange(stand) && loss > 0,
					"the fixture's Green Day did not strike by the holder's modified reach: damage " + loss);
			check(farthest > speed && longestStep <= speed + 1.0E-6D, "Green Day must fly out at its own speed, " + speed
					+ " a tick: it reached " + farthest + " from its user, with a tick of " + longestStep);
			check(farthest > StandMeleeReach.awayDistance(stand) && !guardedWhileAway, "Green Day away to strike could guard");
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

		private static EntityStandType testMeleeStand(String id, double effective, double max, double power, double speed) {
			MovesetBuilder moveset = new MovesetBuilder().addHumanoidStandStuff()
					.addAbility("punch", ModStandAbilities.PUNCH)
					.addSkill(StandUnlockableSkill.startingAbility("punch"));
			return new EntityStandType(new StandStats.Builder().power(power).speed(speed)
					.range(effective, max).durability(12.0D).precision(8.0D).build(), moveset,
					ResourceLocation.parse(id), StandControlType.CLOSE_RANGE_DIRECT, effective, max, true, true);
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
