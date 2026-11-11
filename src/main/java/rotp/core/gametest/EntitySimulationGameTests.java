package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.UUID;

import rotp.core.api.simulation.EntitySimulations;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.ServerBlockDestroyTracker;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * EntitySimulations: a copy that an add-on keeps outside the world with its real entity's int id (King Crimson's
 * Epitaph) leaves the core's id-keyed and level-wide state alone. Stopped time keeps holding the real cow where it froze
 * whatever is asked of the copy, and runs no resume work queued on it; a simulated Stand user starts no time stop; block
 * destroy progress a copy deals is not recorded. Every stop is put and removed within the test's first tick, so it holds
 * no neighbour cell of its batch.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EntitySimulationGameTests {
	private static final ResourceLocation OWNER = JojoMod.resLoc("entity_simulation_test");
	// apart from the other time stop tests' ids
	private static int nextStopId = -7_360_000;

	private EntitySimulationGameTests() {}

	@GameTest(template = "empty", batch = GameTestBatches.TIME_STOP)
	public static void stoppedTimeTakesNothingFromACopyWithItsRealEntitysId(GameTestHelper helper) {
		TimeStopState state = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
		Cow real = cow(helper, 2);
		Cow copy = copyOf(helper, real);
		int stop = nextStopId--;
		int[] resumed = new int[1];
		EntitySimulations.register(OWNER, entity -> entity == copy);
		try {
			helper.assertTrue(state.tryPutInstance(new TimeStopState.Instance(stop, 200, 200,
					new ChunkPos(real.blockPosition()), 2, -1, "entity_simulation_test")), "Could not stop time");
			helper.assertTrue(state.interruptTickEarly(real) && real.isNoAi(), "Setup: the real cow did not freeze");
			Vec3 frozenAt = real.position();
			copy.setPos(frozenAt.add(0, 0, 1.5));
			helper.assertTrue(state.shouldFreeze(copy), "Setup: stopped time would not hold the copy where it stands");

			boolean repinned = state.repinOrRelease(copy);
			state.reconcileFrozenEntity(copy);
			state.freezeEntity(copy);
			boolean interrupted = state.interruptTickEarly(copy);
			state.queueOnTimeResume(copy, () -> resumed[0]++);
			state.unfreezeEntity(copy);
			helper.assertTrue(!repinned && !interrupted && !copy.isNoAi(), "Stopped time took the copy in: re-pinned "
					+ repinned + ", its tick interrupted " + interrupted + ", its AI off " + copy.isNoAi());

			helper.assertTrue(state.interruptTickEarly(real), "The real cow no longer froze after its copy was asked");
			helper.assertTrue(real.position().distanceToSqr(frozenAt) < 1.0E-12 && real.isNoAi(),
					"The freeze did not hold the real cow where it froze: it is at " + real.position() + ", it froze at "
							+ frozenAt + ", its AI off " + real.isNoAi());
			state.removeInstance(stop);
			helper.assertTrue(resumed[0] == 0 && !real.isNoAi(), "Time resuming for the real cow ran " + resumed[0]
					+ " resume work queued on its copy, its AI back " + !real.isNoAi());
		}
		finally {
			EntitySimulations.unregister(OWNER);
			state.removeInstance(stop);
			real.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = GameTestBatches.TIME_STOP)
	public static void aSimulatedStandUserStartsNoTimeStop(GameTestHelper helper) {
		TimeStopState state = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(zombie != null, "Setup: could not create the zombie");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "Setup: could not add the zombie");
		StandPower power = PowerClass.STAND.attachGet(zombie);
		EntitySimulations.register(OWNER, entity -> entity == zombie);
		try {
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Setup: could not give the zombie The World");
			power.setResolveLevel(4);
			power.getCurTypeData()._lockedAbilities.remove("time_stop");
			helper.assertTrue(type.summon(zombie, power), "Setup: could not summon the zombie's The World");
			helper.assertTrue(power.getAbility("time_stop") instanceof TimeStopAbility, "Setup: The World's time stop is "
					+ power.getAbility("time_stop"));
			TimeStopAbility timeStop = (TimeStopAbility) power.getAbility("time_stop");
			Method start = TimeStopAbility.class.getDeclaredMethod("startTimeStopAfterHold", LivingEntity.class, boolean.class);
			start.setAccessible(true);
			power.setStamina(power.getMaxStamina());
			float stamina = power.getStamina();
			boolean started = (boolean) start.invoke(timeStop, zombie, true);
			helper.assertTrue(!started && state.getInstance(zombie.getId()).isEmpty() && power.getStamina() == stamina
					&& !zombie.hasEffect(ModStatusEffects.TIME_STOP), "A simulated Stand user stopped time: started " + started
							+ ", its stop " + state.getInstance(zombie.getId()) + ", stamina " + stamina + " -> " + power.getStamina());

			EntitySimulations.unregister(OWNER);
			boolean control = (boolean) start.invoke(timeStop, zombie, true);
			helper.assertTrue(control && state.getInstance(zombie.getId()).isPresent(),
					"Setup: the same zombie, not simulated, did not stop time: started " + control);
		}
		catch (ReflectiveOperationException error) {
			throw new IllegalStateException("Setup: could not start The World's time stop", error);
		}
		finally {
			EntitySimulations.unregister(OWNER);
			state.removeInstance(zombie.getId());
			zombie.removeEffect(ModStatusEffects.TIME_STOP);
			if (power.isSummoned() && type != null) {
				type.forceUnsummon(zombie, power);
			}
			zombie.discard();
		}
		helper.succeed();
	}

	@GameTest(template = "empty")
	public static void blockDestroyProgressACopyDealsIsNotRecorded(GameTestHelper helper) {
		BlockPos pos = helper.absolutePos(new BlockPos(1, 1, 1));
		BlockPos control = helper.absolutePos(new BlockPos(3, 1, 1));
		helper.getLevel().setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
		helper.getLevel().setBlockAndUpdate(control, Blocks.STONE.defaultBlockState());
		Cow real = cow(helper, 2);
		Cow copy = copyOf(helper, real);
		EntitySimulations.register(OWNER, entity -> entity == copy);
		try {
			ServerBlockDestroyTracker tracker = helper.getLevel().getData(ModDataAttachmentTypes.BLOCK_DESTROY.get());
			float added = ServerBlockDestroyTracker.addBlockDestroyProgress(helper.getLevel(), copy, pos,
					helper.getLevel().getBlockState(pos), 0.2F, false).progressAdded;
			helper.assertTrue(added == 0 && !tracker.blockDestroy.containsKey(pos), "A copy's hit on stone was recorded: "
					+ added + " progress");
			float realAdded = ServerBlockDestroyTracker.addBlockDestroyProgress(helper.getLevel(), real, control,
					helper.getLevel().getBlockState(control), 0.2F, false).progressAdded;
			helper.assertTrue(realAdded > 0 && tracker.blockDestroy.containsKey(control),
					"Setup: the real cow's hit on stone was not recorded: " + realAdded);
			tracker.blockDestroy.remove(control);
		}
		finally {
			EntitySimulations.unregister(OWNER);
			real.discard();
		}
		helper.succeed();
	}

	private static Cow cow(GameTestHelper helper, int x) {
		Cow cow = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(cow != null, "Setup: could not create a cow");
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(x, 0, 2)));
		cow.moveTo(pos.x, pos.y, pos.z, 0, 0);
		cow.getAttribute(Attributes.MOVEMENT_SPEED).setBaseValue(0);
		helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Setup: could not add a cow");
		return cow;
	}

	// as Epitaph makes one: from the real entity's saved data, a UUID of its own, the real entity's int id, never added
	private static <T extends Entity> T copyOf(GameTestHelper helper, T real) {
		@SuppressWarnings("unchecked")
		T copy = (T) real.getType().create(helper.getLevel());
		helper.assertTrue(copy != null, "Setup: could not create a copy of " + real);
		copy.load(real.saveWithoutId(new CompoundTag()));
		copy.setUUID(UUID.randomUUID());
		copy.setId(real.getId());
		return copy;
	}
}
