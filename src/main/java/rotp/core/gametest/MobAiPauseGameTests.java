package rotp.core.gametest;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import rotp.core.api.entity.MobAiPause;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityTypeTags;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.enderdragon.phases.EnderDragonPhase;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * MobAiPause, for King Crimson's amnesia and Gold Experience Requiem's Return to Zero: a paused mob's AI does nothing
 * for exactly its ticks of pause and then resumes; it still falls and takes knockback; a skeleton does not shoot, a
 * creeper does not swell, a villager's brain holds, a Wither does not fly at its target, an Ender Dragon's phase and
 * flight hold, a mob's Stand action waits; nothing of it is saved; one setter never shortens a pause, the other
 * restarts it; the never_ai_paused tag keeps a type out. Own batch: the mobs walk and the bosses fly.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MobAiPauseGameTests {
	private static final String BATCH = "mob_ai_pause";

	private MobAiPauseGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80, batch = BATCH)
	public static void aChasingZombieStopsForExactlyItsPauseAndResumes(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper).floor();
		Pig pig = fixture.target(new Vec3(3.5, 1, 1.5));
		Zombie zombie = fixture.add(helper.spawn(EntityType.ZOMBIE, new Vec3(-0.5, 1, 1.5)));
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		AiStepProbe probe = new AiStepProbe();
		zombie.goalSelector.addGoal(0, probe);
		// its own target goals took the villager of a neighbour cell and dropped it again, and the pig with it
		zombie.targetSelector.removeAllGoals(goal -> true);
		zombie.setTarget(pig);
		int pause = 20;
		// a zombie's melee goal looks for a path once in 20 ticks and finds none before it has touched the ground
		int chasing = 30;
		double start = zombie.getX();
		int[] at = new int[2];
		double[] x = new double[2];
		helper.runAfterDelay(chasing, () -> {
			helper.assertTrue(zombie.getX() - start > 0.05, "Setup: the zombie did not chase the pig: x " + start + " -> " + zombie.getX());
			helper.assertTrue(MobAiPause.pause(zombie, pause) && MobAiPause.remainingTicks(zombie) == pause,
					"The zombie was not paused for " + pause + " ticks: " + MobAiPause.remainingTicks(zombie));
			at[0] = zombie.tickCount;
			at[1] = probe.ticks;
		});
		helper.runAfterDelay(chasing + 5, () -> x[0] = zombie.getX());
		helper.runAfterDelay(chasing + pause, () -> {
			x[1] = zombie.getX();
			helper.assertTrue(Math.abs(x[1] - x[0]) < 0.01 && probe.ticks == at[1], "A paused zombie went on: x " + x[0]
					+ " -> " + x[1] + " in its pause, its AI stepped " + (probe.ticks - at[1]) + " times");
		});
		helper.runAfterDelay(chasing + pause + 12, () -> {
			int ticks = zombie.tickCount - at[0];
			int steps = probe.ticks - at[1];
			helper.assertTrue(steps == ticks - pause, "In " + ticks + " ticks from a pause of " + pause + " the zombie's AI stepped "
					+ steps + " times, " + (ticks - pause) + " expected");
			helper.assertTrue(zombie.getX() - x[1] > 0.05 && !MobAiPause.isPaused(zombie),
					"The zombie did not chase again after its pause: x " + x[1] + " -> " + zombie.getX() + ", the pig at "
					+ pig.getX() + ", its target " + zombie.getTarget() + ", its path done " + zombie.getNavigation().isDone());
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void aPausedZombieFallsAndTakesKnockback(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper).floor();
		Zombie zombie = fixture.add(helper.spawn(EntityType.ZOMBIE, new Vec3(1.5, 3, 1.5)));
		zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		helper.assertTrue(MobAiPause.pause(zombie, 100), "The zombie was not paused");
		double top = zombie.getY();
		double[] x = new double[1];
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(zombie.onGround() && top - zombie.getY() > 1.5,
					"A paused zombie did not fall: y " + top + " -> " + zombie.getY() + ", on the ground " + zombie.onGround());
			x[0] = zombie.getX();
			zombie.knockback(0.6, -1.0, 0.0);
		});
		helper.runAfterDelay(30, () -> {
			helper.assertTrue(zombie.getX() - x[0] > 0.3 && MobAiPause.isPaused(zombie),
					"A paused zombie was not knocked back: x " + x[0] + " -> " + zombie.getX());
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 160, batch = BATCH)
	public static void aPausedSkeletonDoesNotShoot(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper).floor();
		Pig pig = fixture.target(new Vec3(3.5, 1, 1.5));
		Skeleton skeleton = fixture.add(helper.spawn(EntityType.SKELETON, new Vec3(-0.5, 1, 1.5)));
		skeleton.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
		skeleton.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.BOW));
		skeleton.setTarget(pig);
		int pause = 60;
		helper.assertTrue(MobAiPause.pause(skeleton, pause), "The skeleton was not paused");
		long begun = helper.getTick();
		AABB around = skeleton.getBoundingBox().inflate(24.0);
		helper.onEachTick(() -> {
			List<AbstractArrow> arrows = helper.getLevel().getEntitiesOfClass(AbstractArrow.class, around,
					arrow -> arrow.getOwner() == skeleton);
			if (arrows.isEmpty()) {
				return;
			}
			arrows.forEach(Entity::discard);
			long ticks = helper.getTick() - begun;
			helper.assertTrue(ticks > pause, "A paused skeleton shot " + ticks + " ticks into a pause of " + pause);
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 100, batch = BATCH)
	public static void aPausedCreeperDoesNotSwell(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper).floor();
		Pig pig = fixture.target(new Vec3(2.5, 1, 1.5));
		Creeper creeper = fixture.add(helper.spawn(EntityType.CREEPER, new Vec3(0.5, 1, 1.5)));
		creeper.setTarget(pig);
		float[] swell = { -1.0F };
		long[] pausedAt = { -1L };
		helper.onEachTick(() -> {
			float now = creeper.getSwelling(1.0F);
			if (pausedAt[0] < 0) {
				if (now > 0.0F) {
					helper.assertTrue(MobAiPause.pause(creeper, 40), "The creeper was not paused");
					pausedAt[0] = helper.getTick();
					swell[0] = now;
				}
				return;
			}
			helper.assertTrue(creeper.isAlive() && now <= swell[0], "A paused creeper swelled from " + swell[0] + " to " + now
					+ " (alive " + creeper.isAlive() + ") " + (helper.getTick() - pausedAt[0]) + " ticks into its pause");
			swell[0] = now;
			if (helper.getTick() - pausedAt[0] >= 38) {
				fixture.close();
				helper.succeed();
			}
		});
	}

	@GameTest(template = "empty", timeoutTicks = 60, batch = BATCH)
	public static void aVillagersBrainHoldsWhilePaused(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper).floor();
		Villager villager = fixture.add(helper.spawn(EntityType.VILLAGER, new Vec3(1.5, 1, 1.5)));
		villager.getBrain().setMemoryWithExpiry(MemoryModuleType.GOLEM_DETECTED_RECENTLY, true, 5L);
		helper.assertTrue(MobAiPause.pause(villager, 30), "The villager was not paused");
		helper.runAfterDelay(25, () -> helper.assertTrue(villager.getBrain().hasMemoryValue(MemoryModuleType.GOLEM_DETECTED_RECENTLY),
				"A paused villager's brain forgot a memory of 5 ticks in 25"));
		helper.runAfterDelay(45, () -> {
			helper.assertTrue(!villager.getBrain().hasMemoryValue(MemoryModuleType.GOLEM_DETECTED_RECENTLY),
					"The villager's brain did not run again after its pause: a memory of 5 ticks is still there");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH)
	public static void aPausedEnderDragonHoldsItsPhaseAndPlace(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		EnderDragon dragon = EntityType.ENDER_DRAGON.create(helper.getLevel());
		helper.assertTrue(dragon != null, "Could not create a dragon");
		Vec3 high = helper.absoluteVec(new Vec3(1.5, 40, 1.5));
		dragon.moveTo(high.x, high.y, high.z, 0.0F, 0.0F);
		dragon.getPhaseManager().setPhase(EnderDragonPhase.CHARGING_PLAYER);
		dragon.getPhaseManager().getPhase(EnderDragonPhase.CHARGING_PLAYER).setTarget(high.add(0, 0, 40));
		helper.assertTrue(MobAiPause.pause(dragon, 100), "The dragon was not paused");
		helper.assertTrue(helper.getLevel().addFreshEntity(dragon), "Could not add the dragon");
		fixture.add(dragon);
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(dragon.position().distanceTo(high) < 0.01
					&& dragon.getPhaseManager().getCurrentPhase().getPhase() == EnderDragonPhase.CHARGING_PLAYER,
					"A paused dragon moved " + dragon.position().distanceTo(high) + " blocks or left its charge for "
							+ dragon.getPhaseManager().getCurrentPhase().getPhase());
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH)
	public static void aPausedWitherDoesNotFlyAtItsTarget(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		Vec3 high = helper.absoluteVec(new Vec3(1.5, 30, 1.5));
		Pig pig = EntityType.PIG.create(helper.getLevel());
		WitherBoss wither = EntityType.WITHER.create(helper.getLevel());
		helper.assertTrue(pig != null && wither != null, "Could not create the pig and the wither");
		pig.setNoAi(true);
		pig.setNoGravity(true);
		pig.moveTo(high.x + 10, high.y + 6, high.z, 0.0F, 0.0F);
		wither.setNoGravity(true);
		wither.moveTo(high.x, high.y, high.z, 0.0F, 0.0F);
		helper.assertTrue(MobAiPause.pause(wither, 100), "The wither was not paused");
		helper.assertTrue(helper.getLevel().addFreshEntity(pig) && helper.getLevel().addFreshEntity(wither),
				"Could not add the pig and the wither");
		fixture.add(pig);
		fixture.add(wither);
		wither.setAlternativeTarget(0, pig.getId());
		helper.runAfterDelay(20, () -> {
			helper.assertTrue(wither.position().distanceTo(high) < 0.01, "A paused wither flew "
					+ wither.position().distanceTo(high) + " blocks towards its target");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = BATCH)
	public static void aPausedMobsStandActionWaits(GameTestHelper helper) {
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(zombie != null, "Could not create the zombie");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "Could not add the zombie");
		StandPower power = PowerClass.STAND.attachGet(zombie);
		Runnable close = () -> {
			zombie.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			if (power.isSummoned() && type != null) {
				type.forceUnsummon(zombie, power);
			}
			zombie.discard();
		};
		try {
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && type.summon(zombie, power), "Could not summon the zombie's Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "The zombie's Stand is missing");
			stand.summonLockTicks = 0;
			power.setStamina(power.getMaxStamina());
			AbilityInput.keyPressMob(power.getAbility("barrage"), zombie, null, InputMethod.HOLD);
			EntityActionInstance barrage = LivingComponentAction.getComponent(stand).getAction();
			helper.assertTrue(barrage != null && barrage.ability == power.getAbility("barrage"), "The zombie's Stand did not barrage: " + barrage);
			float[] ticks = new float[1];
			helper.runAfterDelay(2, () -> {
				helper.assertTrue(MobAiPause.pause(zombie, 10) && MobAiPause.isPaused(stand), "The zombie or its Stand is not paused");
				ticks[0] = barrage.getFullTicksPassed();
			});
			helper.runAfterDelay(8, () -> helper.assertTrue(barrage.getFullTicksPassed() == ticks[0], "A paused zombie's Stand barraged on: "
					+ ticks[0] + " -> " + barrage.getFullTicksPassed() + " ticks"));
			helper.runAfterDelay(18, () -> {
				try {
					helper.assertTrue(barrage.getFullTicksPassed() > ticks[0] + 3, "The zombie's Stand did not barrage on after the pause: "
							+ ticks[0] + " -> " + barrage.getFullTicksPassed() + " ticks");
				}
				finally {
					close.run();
				}
				helper.succeed();
			});
		}
		catch (RuntimeException | Error error) {
			close.run();
			throw error;
		}
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void nothingOfAPauseIsSaved(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		Zombie zombie = fixture.add(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(1.5, 2, 1.5)));
		zombie.setNoGravity(true);
		helper.assertTrue(MobAiPause.pause(zombie, 200), "The zombie was not paused");
		CompoundTag paused = zombie.saveWithoutId(new CompoundTag());
		MobAiPause.end(zombie);
		CompoundTag free = zombie.saveWithoutId(new CompoundTag());
		helper.assertTrue(paused.equals(free) && !paused.getBoolean("NoAI") && !zombie.isNoAi(),
				"A pause left something in the saved data or set NoAI: " + paused + " / " + free);
		MobAiPause.pause(zombie, 200);
		CompoundTag saved = new CompoundTag();
		helper.assertTrue(zombie.save(saved), "The zombie could not be saved");
		Entity copy = EntityType.create(saved, helper.getLevel()).orElse(null);
		helper.assertTrue(copy instanceof Zombie loaded && MobAiPause.remainingTicks(loaded) == 0,
				"A zombie loaded from a paused one's data is paused: " + copy);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = BATCH)
	public static void pauseNeverShortensRestartRestartsAndTheTagKeepsATypeOut(GameTestHelper helper) {
		Fixture fixture = new Fixture(helper);
		Zombie zombie = fixture.add(helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new Vec3(1.5, 2, 1.5)));
		zombie.setNoGravity(true);
		helper.assertTrue(MobAiPause.pause(zombie, 20) && MobAiPause.remainingTicks(zombie) == 20, "pause(20)");
		helper.assertTrue(MobAiPause.pause(zombie, 10) && MobAiPause.remainingTicks(zombie) == 20,
				"pause(10) shortened a pause of 20 to " + MobAiPause.remainingTicks(zombie));
		helper.assertTrue(MobAiPause.pause(zombie, 30) && MobAiPause.remainingTicks(zombie) == 30,
				"pause(30) did not lengthen a pause of 20: " + MobAiPause.remainingTicks(zombie));
		helper.assertTrue(MobAiPause.restart(zombie, 5) && MobAiPause.remainingTicks(zombie) == 5,
				"restart(5) did not restart a pause of 30: " + MobAiPause.remainingTicks(zombie));
		helper.assertTrue(!MobAiPause.restart(zombie, 0) && !MobAiPause.isPaused(zombie), "restart(0) did not end the pause");
		MobAiPause.pause(zombie, 15);
		MobAiPause.end(zombie);
		helper.assertTrue(MobAiPause.remainingTicks(zombie) == 0, "end did not end the pause");

		helper.assertTrue(BuiltInRegistries.ENTITY_TYPE.getTag(ModEntityTypeTags.NEVER_AI_PAUSED).isPresent(),
				"Data tag jojo_ripples:never_ai_paused is not loaded");
		Map<TagKey<EntityType<?>>, List<Holder<EntityType<?>>>> original = new IdentityHashMap<>();
		BuiltInRegistries.ENTITY_TYPE.getTags().forEach(pair -> original.put(pair.getFirst(), pair.getSecond().stream().toList()));
		Map<TagKey<EntityType<?>>, List<Holder<EntityType<?>>>> tagged = new IdentityHashMap<>(original);
		List<Holder<EntityType<?>>> never = new ArrayList<>(original.get(ModEntityTypeTags.NEVER_AI_PAUSED));
		never.add(EntityType.ZOMBIE.builtInRegistryHolder());
		tagged.put(ModEntityTypeTags.NEVER_AI_PAUSED, never);
		try {
			BuiltInRegistries.ENTITY_TYPE.bindTags(tagged);
			helper.assertTrue(!MobAiPause.pause(zombie, 20) && !MobAiPause.restart(zombie, 20) && !MobAiPause.isPaused(zombie),
					"A zombie was paused while its type is in jojo_ripples:never_ai_paused");
		}
		finally {
			BuiltInRegistries.ENTITY_TYPE.bindTags(original);
		}
		helper.assertTrue(MobAiPause.pause(zombie, 20), "With the tag restored a zombie could not be paused");
		helper.succeed();
	}

	/** Counts its mob's AI steps: a running goal that wants every tick ticks once in each. */
	private static final class AiStepProbe extends Goal {
		int ticks;

		@Override
		public boolean canUse() {
			return true;
		}

		@Override
		public boolean requiresUpdateEveryTick() {
			return true;
		}

		@Override
		public void tick() {
			ticks++;
		}
	}

	private static final class Fixture implements GameTestListener {
		private final GameTestHelper helper;
		private final List<Entity> entities = new ArrayList<>();
		private final List<BlockPos> blocks = new ArrayList<>();
		private boolean closed;

		Fixture(GameTestHelper helper) {
			this.helper = helper;
			helper.testInfo.addListener(this);
		}

		// a stone floor in place of the barrier walls and ceiling the test cell gets around its 1-block template
		Fixture floor() {
			for (int x = -1; x <= 3; x++) {
				for (int z = -1; z <= 3; z++) {
					for (int y = 1; y <= 2; y++) {
						helper.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState());
					}
					BlockPos floor = new BlockPos(x, 0, z);
					helper.setBlock(floor, Blocks.STONE.defaultBlockState());
					blocks.add(floor);
				}
			}
			return this;
		}

		<E extends Entity> E add(E entity) {
			entities.add(entity);
			return entity;
		}

		/** A pig with no goals to chase. */
		Pig target(Vec3 relative) {
			return add(helper.spawnWithNoFreeWill(EntityType.PIG, relative));
		}

		void close() {
			if (closed) {
				return;
			}
			closed = true;
			entities.forEach(Entity::discard);
			for (BlockPos pos : blocks) {
				helper.setBlock(pos, Blocks.AIR.defaultBlockState());
			}
		}

		@Override
		public void testStructureLoaded(GameTestInfo test) {}

		@Override
		public void testPassed(GameTestInfo test, GameTestRunner runner) {
			close();
		}

		@Override
		public void testFailed(GameTestInfo test, GameTestRunner runner) {
			close();
		}

		@Override
		public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
			close();
		}
	}
}
