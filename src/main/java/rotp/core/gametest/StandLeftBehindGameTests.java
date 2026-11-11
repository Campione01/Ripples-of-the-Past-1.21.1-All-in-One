package rotp.core.gametest;

import java.util.Comparator;
import java.util.function.Consumer;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModEntityAttributes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * A user teleported far in his level (a command, an ender pearl, a portal, another mod) left his summoned Stand where he
 * was, in chunks that stay loaded for a while but no longer tick: it stayed his Stand and never acted on what a key
 * handed it, so every Stand key did nothing until he recalled it. The Stand now comes along. One that is meant to be
 * away from him, manually controlled within the range its movement is held to, stays. The scene summons the Stand in
 * far chunks it forces, teleports its user back into the test's own and keeps the far chunks loaded without ticking, as
 * a teleport leaves them. A Stand whose place is in no loaded chunk is hidden: the level takes it out (it leaves the
 * level) and keeps it in its storage, as it keeps an entity not saved with its chunk; it comes along too (King
 * Crimson's reproduction sets it into such a chunk).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandLeftBehindGameTests {
	private static final TicketType<ChunkPos> LOADED_NOT_TICKING = TicketType.create("jojo_ripples_test_left_behind",
			Comparator.comparingLong(ChunkPos::toLong));

	private StandLeftBehindGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 400, batch = "stand_left_behind_far_teleport")
	public static void aStandLeftBehindByAFarTeleportComesAlongAndItsKeyAnswers(GameTestHelper helper) {
		Scene scene = new Scene(helper, "star_platinum", new Vec3(0.0D, 0.0D, 1600.0D));
		StandEntity[] stand = new StandEntity[1];
		EntityActionInstance[] pressed = new EntityActionInstance[1];
		ActionPhase[] phase = new ActionPhase[1];
		float[] ticksLeft = new float[1];
		scene.whenFarTicks(() -> {
			stand[0] = scene.summonFarAndTeleportBack(null, true);
			scene.step(2, () -> checkComeAlongAndPress(helper, scene, stand, pressed, phase, ticksLeft, true));
			scene.step(12, () -> checkActed(helper, scene, stand, pressed, phase, ticksLeft));
		});
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = "stand_left_behind_set_unloaded")
	public static void aStandSetIntoAChunkNothingLoadsComesAlongAndItsKeyAnswers(GameTestHelper helper) {
		Scene scene = new Scene(helper, "star_platinum", new Vec3(0.0D, 0.0D, 598.0D), false);
		StandEntity[] stand = new StandEntity[1];
		EntityActionInstance[] pressed = new EntityActionInstance[1];
		ActionPhase[] phase = new ActionPhase[1];
		float[] ticksLeft = new float[1];
		scene.step(1, () -> {
			stand[0] = scene.summonHere();
			helper.assertTrue(!scene.level.hasChunk(scene.farChunk.x, scene.farChunk.z), "Fixture: the chunk 598 blocks off is loaded");
			stand[0].setPos(scene.far.x, scene.far.y, scene.far.z);
			helper.assertTrue(!stand[0].isAddedToLevel() && scene.leftLevel > 0 && !stand[0].isRemoved()
					&& scene.power.getSummonedStandEntity() == stand[0],
					"Fixture: the Stand set into a chunk nothing loads is not hidden: in the level " + stand[0].isAddedToLevel()
							+ ", left it " + scene.leftLevel + " times, removed " + stand[0].isRemoved());
		});
		scene.step(3, () -> checkComeAlongAndPress(helper, scene, stand, pressed, phase, ticksLeft, false));
		scene.step(13, () -> checkActed(helper, scene, stand, pressed, phase, ticksLeft));
	}

	private static void checkComeAlongAndPress(GameTestHelper helper, Scene scene, StandEntity[] stand,
			EntityActionInstance[] pressed, ActionPhase[] phase, float[] ticksLeft, boolean farStaysLoaded) {
		if (farStaysLoaded) {
			scene.assertFarDoesNotTick();
		}
		else {
			helper.assertTrue(scene.leftLevel > 0, "Fixture: the Stand never left the level, so it was never hidden");
			helper.assertTrue(scene.level.isPositionEntityTicking(scene.user.blockPosition()),
					"Fixture: the user's own place does not tick");
		}
		StandEntity now = scene.power.getSummonedStandEntity();
		helper.assertTrue(now == stand[0], "The Stand left behind is no longer its user's: " + now);
		double away = now.distanceTo(scene.user);
		helper.assertTrue(away < 4.0D && now.isAddedToLevel(), "A few ticks after it was left " + (int) scene.far.distanceTo(scene.origin)
				+ " blocks away Star Platinum is still " + away + " blocks from its user, at " + now.position()
				+ (now.isAddedToLevel() ? ", where nothing ticks it" : ", hidden: out of the level"));
		now.summonLockTicks = 0;
		scene.power.setStamina(scene.power.getMaxStamina());
		Ability punch = scene.power.getAbility("punch");
		helper.assertTrue(punch != null, "Missing Star Platinum's punch");
		punch.onKeyPress(helper.getLevel(), scene.user, null, InputMethod.CLICK, 0.0F, BufferingState.clickOnly());
		pressed[0] = now.getCurStandAction();
		helper.assertTrue(pressed[0] != null, "Fixture: the punch key handed nothing to the Stand");
		phase[0] = pressed[0].getPhase();
		ticksLeft[0] = pressed[0].getPhaseTicksLeft();
	}

	private static void checkActed(GameTestHelper helper, Scene scene, StandEntity[] stand,
			EntityActionInstance[] pressed, ActionPhase[] phase, float[] ticksLeft) {
		EntityActionInstance action = pressed[0];
		boolean acted = action.isOver() || stand[0].getCurStandAction() != action || action.getPhase() != phase[0]
				|| action.getPhaseTicksLeft() != ticksLeft[0];
		helper.assertTrue(acted, "Ten ticks after the punch key Star Platinum has not acted on it: still in "
				+ action.getPhase() + " with " + action.getPhaseTicksLeft() + " ticks left, at " + stand[0].position());
		scene.close();
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 400, batch = "stand_left_behind_manual_in_range")
	public static void aManuallyControlledStandWithinItsRangeStaysWhereItIs(GameTestHelper helper) {
		Scene scene = new Scene(helper, "hierophant_green", new Vec3(1600.0D, 0.0D, 0.0D));
		StandEntity[] stand = new StandEntity[1];
		Vec3[] place = new Vec3[1];
		scene.whenFarTicks(() -> {
			stand[0] = scene.summonFarAndTeleportBack(far -> {
				far.getAttribute(ModEntityAttributes.STAND_EFFECTIVE_RANGE).setBaseValue(2000.0D);
				far.getAttribute(ModEntityAttributes.STAND_MAX_RANGE).setBaseValue(2000.0D);
				far.setManuallyControlled(true);
				helper.assertTrue(far.getMaxRangeForMovement(scene.user) >= 2000.0D, "Fixture: Hierophant Green's movement is "
						+ "held to " + far.getMaxRangeForMovement(scene.user) + " blocks");
			}, true);
			place[0] = stand[0].position();
			scene.step(4, () -> {
				scene.assertFarDoesNotTick();
				StandEntity now = scene.power.getSummonedStandEntity();
				helper.assertTrue(now == stand[0] && now.isManuallyControlled(), "Fixture: the manually controlled Stand is no"
						+ " longer its user's: " + now);
				helper.assertTrue(now.position().distanceTo(place[0]) < 1.0E-6, "A manually controlled Hierophant Green "
						+ place[0].distanceTo(scene.user.position()) + " blocks from its user, within its range of 2000, was "
						+ "moved from " + place[0] + " to " + now.position());
				scene.close();
				helper.succeed();
			});
		});
	}

	private static final class Scene {
		final GameTestHelper helper;
		final ServerLevel level;
		final Vec3 origin;
		final Vec3 far;
		final ChunkPos farChunk;
		final Player user;
		final StandType type;
		StandPower power;
		boolean forced;
		boolean ticketed;
		int leftLevel;
		private Consumer<EntityLeaveLevelEvent> leaveListener;

		Scene(GameTestHelper helper, String standId, Vec3 offset) {
			this(helper, standId, offset, true);
		}

		Scene(GameTestHelper helper, String standId, Vec3 offset, boolean forceFar) {
			this.helper = helper;
			level = helper.getLevel();
			origin = helper.absoluteVec(new Vec3(1.5D, 3.0D, 1.5D));
			far = origin.add(offset);
			farChunk = new ChunkPos(BlockPos.containing(far));
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			if (forceFar) {
				forEachFarChunk((x, z) -> level.setChunkForced(x, z, true));
				forced = true;
			}
		}

		StandEntity summonHere() {
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(user), "Fixture: could not add the Stand user");
			return summon();
		}

		private StandEntity summon() {
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Fixture: could not grant " + type);
			helper.assertTrue(type.summon(user, power), "Fixture: could not summon " + type);
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null && stand.isAddedToLevel() && stand.distanceTo(user) < 4.0D,
					"Fixture: the Stand is not out beside its user");
			stand.setNoGravity(true);
			leaveListener = event -> {
				if (event.getEntity() == stand) {
					leftLevel++;
				}
			};
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, EntityLeaveLevelEvent.class, leaveListener);
			return stand;
		}

		/*
		 * keepLoaded: what a teleport leaves, the chunks loaded a while and nothing in them ticking; else they are let
		 * go at once, and the Stand in them is hidden
		 */
		StandEntity summonFarAndTeleportBack(Consumer<StandEntity> setUp, boolean keepLoaded) {
			helper.assertTrue(level.isPositionEntityTicking(BlockPos.containing(far)), "Fixture: the forced far chunks do not tick");
			user.setNoGravity(true);
			user.moveTo(far.x, far.y, far.z, 0.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(user), "Fixture: could not add the Stand user");
			StandEntity stand = summon();
			if (setUp != null) {
				setUp.accept(stand);
			}
			if (keepLoaded) {
				forEachFarChunk((x, z) -> level.getChunkSource().addRegionTicket(LOADED_NOT_TICKING, new ChunkPos(x, z), 0,
						new ChunkPos(x, z)));
				ticketed = true;
			}
			forEachFarChunk((x, z) -> level.setChunkForced(x, z, false));
			forced = false;
			user.teleportTo(origin.x, origin.y, origin.z);
			helper.assertTrue(user.position().distanceTo(origin) < 1.0E-6, "Fixture: the user was not teleported back");
			return stand;
		}

		void assertFarDoesNotTick() {
			helper.assertTrue(!level.isPositionEntityTicking(BlockPos.containing(far)) && level.hasChunk(farChunk.x, farChunk.z),
					"Fixture: the far chunks still tick or were unloaded");
			helper.assertTrue(level.isPositionEntityTicking(user.blockPosition()), "Fixture: the user's own place does not tick");
		}

		// far chunks forced a moment ago are entity-ticking only once those around them are loaded
		void whenFarTicks(Runnable then) {
			boolean[] started = new boolean[1];
			helper.onEachTick(() -> {
				if (!started[0] && level.isPositionEntityTicking(BlockPos.containing(far))) {
					started[0] = true;
					guarded(then);
				}
			});
		}

		void step(int delay, Runnable step) {
			helper.runAfterDelay(delay, () -> guarded(step));
		}

		private void guarded(Runnable step) {
			try {
				step.run();
			}
			catch (RuntimeException | Error e) {
				close();
				throw e;
			}
		}

		void close() {
			if (leaveListener != null) {
				NeoForge.EVENT_BUS.unregister(leaveListener);
				leaveListener = null;
			}
			StandEntity stand = power != null ? power.getSummonedStandEntity() : null;
			if (stand != null) {
				power.setSummonedStand(null);
				stand.discard();
			}
			user.discard();
			if (forced) {
				forEachFarChunk((x, z) -> level.setChunkForced(x, z, false));
				forced = false;
			}
			if (ticketed) {
				forEachFarChunk((x, z) -> level.getChunkSource().removeRegionTicket(LOADED_NOT_TICKING, new ChunkPos(x, z), 0,
						new ChunkPos(x, z)));
				ticketed = false;
			}
		}

		private void forEachFarChunk(ChunkAction action) {
			for (int dx = -1; dx <= 1; dx++) {
				for (int dz = -1; dz <= 1; dz++) {
					action.run(farChunk.x + dx, farChunk.z + dz);
				}
			}
		}
	}

	private interface ChunkAction {
		void run(int x, int z);
	}
}
