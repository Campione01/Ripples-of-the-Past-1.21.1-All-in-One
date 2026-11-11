package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.entityattachment.custom_effect.EntityCustomEffect;
import rotp.core.impl.stands.goldexperience.GECreatedLifeformEffect;
import rotp.core.impl.stands.goldexperience.GETransformationEntity;
import rotp.core.impl.stands.goldexperience.GoldExperienceCreateLifeformAbility;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformRoster;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformRoster.Answer;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformRoster.Place;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformRoster.Row;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeformState;
import rotp.core.impl.stands.goldexperience.GoldExperienceLifeforms;
import rotp.core.impl.stands.goldexperience.GoldExperienceRevertLifeformAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.network.s2c.TrGELifeformRosterPacket;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.effect.StandEffectInstance;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import io.netty.buffer.Unpooled;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.connection.ConnectionType;

/*
 * The owner's items of 2026-11-08 for Gold Experience's lifeforms: one that has lived 7 minutes no longer takes one of
 * the 16 places; the server keeps a record of each that follows the creature and ends when it no longer exists; the
 * menu Revert opens reverts, on the server's word, only the asker's own lifeform that is loaded in his dimension, once.
 * The coordinator's follow-up of 2026-11-10: 1.16's 128-block leash holds only while a lifeform still counts.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GoldExperienceLifeformRosterGameTests {
	private static final String BATCH = "ge_lifeform_roster";
	private static final short KEY = 25;

	private GoldExperienceLifeformRosterGameTests() {}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceLifeformStopsCountingTowardTheLimitAfterSevenMinutes(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			scene.open(true);
			Pig pig = scene.pig(0.0D, 4.0D);
			long now = scene.level.getGameTime();
			for (int i = 0; i < GoldExperienceCreateLifeformAbility.MAX_CREATED_LIFEFORMS - 1; i++) {
				scene.lifeform(pig);
			}
			GECreatedLifeformEffect old = scene.lifeformCreatedAt(pig, now - GECreatedLifeformEffect.COUNTED_TICKS - 1);
			helper.assertTrue(old.getCreatedGameTime() == now - GECreatedLifeformEffect.COUNTED_TICKS - 1,
					"GE-LIFE premise: the record made from saved data lost its creation time");
			helper.assertTrue(GoldExperienceCreateLifeformAbility.countedLifeforms(scene.power) == 15
					&& GoldExperienceCreateLifeformAbility.canCreateMoreLifeforms(scene.power),
					"GE-LIFE: a lifeform 7 minutes and a tick old still takes one of the 16 places ("
							+ GoldExperienceCreateLifeformAbility.countedLifeforms(scene.power) + " counted)");

			scene.power.userStandEffects.removeEffect(old);
			scene.lifeformCreatedAt(pig, now - GECreatedLifeformEffect.COUNTED_TICKS + 1);
			helper.assertTrue(GoldExperienceCreateLifeformAbility.countedLifeforms(scene.power) == 16
					&& !GoldExperienceCreateLifeformAbility.canCreateMoreLifeforms(scene.power),
					"GE-LIFE: a lifeform a tick short of 7 minutes must still count; 16 of them leave no place");

			GECreatedLifeformEffect fresh = scene.lifeform(pig);
			helper.assertTrue(fresh.getCreatedGameTime() == now,
					"GE-LIFE: a new record must take the game time it was made at, got " + fresh.getCreatedGameTime());
			CompoundTag saved = fresh.toNBT();
			EntityCustomEffect reloaded = EntityCustomEffect.fromNBT(saved, scene.level);
			helper.assertTrue(reloaded instanceof GECreatedLifeformEffect copy && copy.getCreatedGameTime() == now,
					"GE-LIFE: the creation time does not survive a save and a reload");
		}
		finally {
			scene.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceLifeformPastSevenMinutesIsFreeOfTheDistanceLeash(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerLevel level = helper.getLevel();
		ChunkPos far = null;
		try {
			scene.open(true);
			double away = GoldExperienceRevertLifeformAbility.MAX_LIFEFORM_DISTANCE + 72.0D;
			far = new ChunkPos(BlockPos.containing(scene.origin.add(0.0D, 0.0D, away)));
			level.setChunkForced(far.x, far.z, true);
			level.getChunk(far.x, far.z);
			Pig counted = scene.pig(0.0D, away);
			Pig free = scene.pig(2.0D, away);
			GECreatedLifeformEffect countedRecord = scene.lifeform(counted);
			GECreatedLifeformEffect freeRecord = scene.lifeformCreatedAt(free, level.getGameTime() - GECreatedLifeformEffect.COUNTED_TICKS - 1);
			// a chunk forced in this tick lists its creatures by UUID only after the next ticket update: the record is
			// handed its creature, as the creation press hands it
			freeRecord.withTarget(free);
			for (int tick = 0; tick < 5; tick++) {
				scene.tick();
			}
			helper.assertTrue(countedRecord.isStopped(),
					"GE-LIFE: a lifeform that still counts must turn back more than 128 blocks from its user, as in 1.16");
			helper.assertTrue(!freeRecord.isStopped() && free.isAlive(),
					"GE-LIFE: a lifeform past 7 minutes turned back by distance; it must go on existing wherever its user goes");
			Row row = scene.row(free.getUUID());
			helper.assertTrue(row.place() == Place.HERE && !row.counts() && row.entityId() == free.getId(),
					"GE-LIFE: a far lifeform past 7 minutes must be in the menu, revertible and not counted, got " + row);
			helper.assertTrue(GoldExperienceLifeformRoster.revert(scene.user, free.getUUID()) == Answer.REVERTED && freeRecord.isStopped(),
					"GE-LIFE: the menu must still revert a lifeform past 7 minutes, however far it is in its user's dimension");
		}
		finally {
			if (far != null) {
				// what the pigs turned back into lies out there
				Vec3 there = scene.origin.add(0.0D, 0.0D, GoldExperienceRevertLifeformAbility.MAX_LIFEFORM_DISTANCE + 72.0D);
				level.getEntitiesOfClass(Entity.class, new AABB(there, there).inflate(8.0D), entity -> !(entity instanceof FakePlayer))
						.forEach(Entity::discard);
				level.setChunkForced(far.x, far.z, false);
			}
			scene.close();
		}
		helper.succeed();
	}

	// the coordinator's last point of 2026-11-10: past 7 minutes a lifeform is free of its creator, his stamina included
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceLifeformPastSevenMinutesCostsItsCreatorNoStamina(GameTestHelper helper) {
		Scene none = new Scene(helper);
		Scene free = new Scene(helper);
		Scene counting = new Scene(helper);
		try {
			none.open(false);
			free.openAt(none.origin.add(6.0D, 0.0D, 0.0D), false);
			counting.openAt(none.origin.add(12.0D, 0.0D, 0.0D), false);
			Pig freePig = free.pig(0.0D, 4.0D);
			Pig countingPig = counting.pig(0.0D, 4.0D);
			GECreatedLifeformEffect freeRecord = free.lifeformCreatedAt(freePig,
					free.level.getGameTime() - GECreatedLifeformEffect.COUNTED_TICKS - 1);
			freeRecord.withTarget(freePig);
			GECreatedLifeformEffect countingRecord = counting.lifeform(countingPig);
			float start = none.power.getMaxStamina() / 2.0F;
			for (Scene scene : List.of(none, free, counting)) {
				scene.power.setStamina(start);
			}
			helper.assertTrue(none.power.usesStamina() && !none.power.isStaminaInfinite()
					&& free.power.getStamina() == start && counting.power.getStamina() == start,
					"GE-LIFE premise: the three users do not start with the same finite stamina");
			// three users alike but for their lifeform: none, one past 7 minutes, one that counts. What a user's stamina
			// is below the one without a lifeform is what his lifeform took; a tick's cost is paid on the next tick
			for (int tick = 0; tick < 3; tick++) {
				none.tick();
				free.tick();
				counting.tick();
			}
			float perTick = GoldExperienceCreateLifeformAbility.getStaminaCostTicking(counting.power, countingPig);
			float tookCounting = none.power.getStamina() - counting.power.getStamina();
			float tookFree = none.power.getStamina() - free.power.getStamina();
			helper.assertTrue(!countingRecord.isStopped() && Math.abs(tookCounting - 2.0F * perTick) < perTick * 0.25F,
					"GE-LIFE: a lifeform that still counts must cost its creator stamina every tick, as in 1.16 (took "
							+ tookCounting + ", " + perTick + " a tick)");
			helper.assertTrue(!freeRecord.isStopped() && Math.abs(tookFree) < perTick * 0.25F,
					"GE-LIFE: a lifeform past 7 minutes still drains its creator's stamina (took " + tookFree + ")");
		}
		finally {
			counting.close();
			free.close();
			none.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceLifeformPastSevenMinutesOutlastsItsCreatorsStamina(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			scene.open(false);
			Pig countingPig = scene.pig(0.0D, 4.0D);
			Pig freePig = scene.pig(2.0D, 4.0D);
			GECreatedLifeformEffect countingRecord = scene.lifeform(countingPig);
			GECreatedLifeformEffect freeRecord = scene.lifeformCreatedAt(freePig,
					scene.level.getGameTime() - GECreatedLifeformEffect.COUNTED_TICKS - 1);
			freeRecord.withTarget(freePig);
			scene.tick();
			helper.assertTrue(!countingRecord.isStopped() && !freeRecord.isStopped(),
					"GE-LIFE premise: the two lifeforms did not last a tick with stamina to spare");
			// his stamina runs out: only the lifeforms' own ticks, so no regeneration comes between
			scene.power.setStamina(0.0F);
			scene.power.userStandEffects.tick();
			helper.assertTrue(countingRecord.isStopped(),
					"GE-LIFE: a lifeform that still counts must turn back when its creator's stamina runs out, as in 1.16");
			helper.assertTrue(!freeRecord.isStopped() && freePig.isAlive(),
					"GE-LIFE: a lifeform past 7 minutes turned back when its creator's stamina ran out; it must go on existing");
		}
		finally {
			scene.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceLifeformRecordFollowsItsCreatureThroughAnUnload(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			scene.open(true);
			Pig pig = scene.pig(0.0D, 4.0D);
			pig.setCustomName(Component.literal("Bessie"));
			GECreatedLifeformEffect effect = scene.lifeform(pig);
			scene.tick();
			helper.assertTrue(pig.position().equals(effect.getLastPos()) && effect.getLastDimension() == scene.level.dimension()
					&& ResourceLocation.withDefaultNamespace("pig").equals(effect.getLifeformType())
					&& effect.getLifeformName() != null && "Bessie".equals(effect.getLifeformName().getString()),
					"GE-LIFE: the record did not take the creature's kind, name and place: " + effect.getLifeformType()
							+ " " + effect.getLifeformName() + " " + effect.getLastPos());
			helper.assertTrue(pig.getPersistentData().getLong("jojo_ripples:ge_lifeform_born") == effect.getCreatedGameTime(),
					"GE-LIFE: the creature does not carry its creation time");
			Row row = scene.row(pig.getUUID());
			helper.assertTrue(row.place() == Place.HERE && row.entityId() == pig.getId() && row.counts(),
					"GE-LIFE: a loaded lifeform near its user must be a revertible row, got " + row);

			Vec3 lastPlace = pig.position();
			UUID id = pig.getUUID();
			pig.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
			for (int tick = 0; tick < 25; tick++) {
				scene.tick();
			}
			helper.assertTrue(scene.power.userStandEffects.getById(effect.getId()) == effect && !effect.isStopped(),
					"GE-LIFE: a lifeform whose chunk unloaded must stay in the record");
			row = scene.row(id);
			helper.assertTrue(row.place() == Place.UNLOADED && lastPlace.equals(row.pos()) && row.entityId() == -1,
					"GE-LIFE: an unloaded lifeform must show grey at its last place, got " + row);
			helper.assertTrue(GoldExperienceLifeformRoster.revert(scene.user, id) == Answer.NOT_HERE,
					"GE-LIFE: an unloaded lifeform must not be revertible from the menu");

			Pig back = EntityType.PIG.create(scene.level);
			back.setUUID(id);
			back.setNoAi(true);
			back.setNoGravity(true);
			back.moveTo(lastPlace.x, lastPlace.y, lastPlace.z, 0.0F, 0.0F);
			helper.assertTrue(scene.level.addFreshEntity(back), "GE-LIFE premise: the pig could not come back");
			scene.entities.add(back);
			scene.tick();
			row = scene.row(id);
			helper.assertTrue(row.place() == Place.HERE && row.entityId() == back.getId(),
					"GE-LIFE: the record did not find its creature when its chunk loaded again, got " + row);
		}
		finally {
			scene.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceLifeformThatNoLongerExistsLeavesTheRecord(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			scene.open(true);
			Pig pig = scene.pig(0.0D, 4.0D);
			GECreatedLifeformEffect effect = scene.lifeform(pig);
			scene.tick();
			AABB area = pig.getBoundingBox().inflate(3.0D);
			pig.discard();
			scene.tick();
			helper.assertTrue(scene.power.userStandEffects.getById(effect.getId()) == null,
					"GE-LIFE: a lifeform that no longer exists still has a record (it would count and show forever)");
			helper.assertTrue(scene.level.getEntitiesOfClass(GETransformationEntity.class, area).isEmpty(),
					"GE-LIFE: a lifeform that no longer exists must not turn back into its source");
		}
		finally {
			scene.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH, timeoutTicks = 400)
	public static void goldExperienceLifeformInAnotherDimensionShowsThereAndIsNotRevertedFromHere(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		ChunkPos far = new ChunkPos(97, 97);
		Vec3 there = new Vec3(far.getMiddleBlockX() + 0.5D, 200.0D, far.getMiddleBlockZ() + 0.5D);
		Holder<Pig> moved = new Holder<>();
		Holder<GECreatedLifeformEffect> effect = new Holder<>();
		helper.startSequence()
				.thenExecute(() -> {
					helper.assertTrue(nether != null, "GE-LIFE premise: the test server has no Nether");
					nether.setChunkForced(far.x, far.z, true);
					// made here and now: the game test server's ticks outrun a chunk generated in the background
					nether.getChunk(far.x, far.z);
					scene.open(true);
					Pig pig = scene.pig(0.0D, 4.0D);
					pig.setInvulnerable(true);
					effect.value = scene.lifeform(pig);
					scene.tick();
					Entity arrived = pig.changeDimension(new DimensionTransition(nether, there, Vec3.ZERO, 0.0F, 0.0F,
							DimensionTransition.DO_NOTHING));
					helper.assertTrue(arrived instanceof Pig && pig.isRemoved(), "GE-LIFE premise: the pig did not go to the Nether");
					moved.value = (Pig) arrived;
				})
				.thenWaitUntil(() -> helper.assertTrue(nether.getEntity(moved.value.getUUID()) == moved.value,
						"GE-LIFE premise: the pig's Nether chunk has not loaded"))
				.thenExecute(() -> {
					// the server ticks the user's effects too: a record that lost the pig before its Nether chunk showed
					// it looks again within a second
					for (int tick = 0; tick <= 20; tick++) {
						scene.tick();
					}
					helper.assertTrue(!effect.value.isStopped() && moved.value.isAlive(),
							"GE-LIFE: a lifeform far away in another dimension must not turn back by distance");
					Row row = scene.row(moved.value.getUUID());
					helper.assertTrue(row.place() == Place.ELSEWHERE && Level.NETHER.location().equals(row.dimension())
							&& row.pos() != null && row.pos().distanceTo(there) < 0.01D && row.entityId() == -1,
							"GE-LIFE: a lifeform in the Nether must show there and grey, got " + row);
					helper.assertTrue(GoldExperienceLifeformRoster.revert(scene.user, moved.value.getUUID()) == Answer.NOT_HERE
							&& moved.value.isAlive() && !effect.value.isStopped(),
							"GE-LIFE: a lifeform in another dimension was reverted from the menu");
					for (int tick = 0; tick < 3; tick++) {
						scene.tick();
					}
					helper.assertTrue(!effect.value.isStopped() && moved.value.isAlive(),
							"GE-LIFE: a lifeform far away in another dimension must not turn back by distance");
				})
				.thenExecute(() -> {
					moved.value.discard();
					nether.setChunkForced(far.x, far.z, false);
					scene.close();
				})
				.thenSucceed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceLifeformSentIntoAnUnloadedChunkOfAnotherDimensionShowsWhereItWent(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		ServerLevel nether = helper.getLevel().getServer().getLevel(Level.NETHER);
		ChunkPos unloaded = new ChunkPos(-211, 173);
		Vec3 there = new Vec3(unloaded.getMiddleBlockX() + 0.5D, 200.0D, unloaded.getMiddleBlockZ() + 0.5D);
		Entity arrived = null;
		try {
			helper.assertTrue(nether != null && !nether.hasChunk(unloaded.x, unloaded.z),
					"GE-LIFE premise: the Nether chunk must not be loaded");
			scene.open(true);
			Pig pig = scene.pig(0.0D, 4.0D);
			GECreatedLifeformEffect effect = scene.lifeform(pig);
			scene.tick();
			arrived = pig.changeDimension(new DimensionTransition(nether, there, Vec3.ZERO, 0.0F, 0.0F,
					DimensionTransition.DO_NOTHING));
			helper.assertTrue(arrived != null && pig.isRemoved(), "GE-LIFE premise: the pig did not go to the Nether");
			for (int tick = 0; tick <= 20; tick++) {
				scene.tick();
			}
			Row row = scene.row(pig.getUUID());
			helper.assertTrue(!effect.isStopped() && row.place() == Place.UNLOADED
					&& Level.NETHER.location().equals(row.dimension()) && row.pos() != null && row.pos().distanceTo(there) < 0.01D,
					"GE-LIFE: a lifeform sent into an unloaded Nether chunk must show grey where it went, got " + row);
		}
		finally {
			if (arrived != null) {
				arrived.discard();
			}
			scene.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceMenuRevertsOnlyTheAskersOwnLifeformAndOnlyOnce(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		Scene other = new Scene(helper);
		FakePlayer standless = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "GeRosterNoStand"));
		try {
			scene.open(true);
			other.openAt(scene.origin.add(2.0D, 0.0D, 0.0D), true);
			Pig pig = scene.pig(0.0D, 4.0D);
			GECreatedLifeformEffect effect = scene.lifeform(pig);
			// the other player has a lifeform of his own, so his Revert is usable and only ownership can refuse him
			Pig othersPig = other.pig(0.0D, 4.0D);
			GECreatedLifeformEffect othersEffect = other.lifeform(othersPig);
			scene.tick();
			other.tick();
			AABB area = pig.getBoundingBox().inflate(3.0D);

			helper.assertTrue(GoldExperienceLifeformRoster.revert(other.user, pig.getUUID()) == Answer.GONE
					&& GoldExperienceLifeformRoster.revert(standless, pig.getUUID()) == Answer.REFUSED,
					"GE-LIFE: another player's request for the lifeform must be refused");
			helper.assertTrue(!effect.isStopped() && pig.isAlive() && !othersEffect.isStopped() && othersPig.isAlive(),
					"GE-LIFE: another player's request reverted a lifeform");
			helper.assertTrue(GoldExperienceLifeformRoster.rows(other.user).stream().map(Row::id).toList()
					.equals(List.of(othersPig.getUUID())), "GE-LIFE: another player's menu lists a lifeform that is not his");

			helper.assertTrue(GoldExperienceLifeformRoster.revert(scene.user, pig.getUUID()) == Answer.REVERTED && effect.isStopped(),
					"GE-LIFE: the owner's menu request did not revert his lifeform");
			Answer again = GoldExperienceLifeformRoster.revert(scene.user, pig.getUUID());
			helper.assertTrue(again == Answer.GONE, "GE-LIFE: a lifeform was reverted twice (second answer " + again + ")");
			scene.tick();
			List<GETransformationEntity> reverses = scene.level.getEntitiesOfClass(GETransformationEntity.class, area,
					transformation -> transformation.getTransformationTarget() == pig);
			helper.assertTrue(reverses.size() == 1 && reverses.get(0).isTurningBack() && pig.isRemoved()
					&& scene.power.userStandEffects.getById(effect.getId()) == null,
					"GE-LIFE: the reverted lifeform did not turn back once into its source; found " + reverses.size());
		}
		finally {
			standless.discard();
			other.close();
			scene.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceMenuRevertRefundsTheCreateCooldownAsAPressDoes(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			scene.open(false);
			scene.user.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.STICK));
			Ability create = scene.power.getAbility("create_lifeform");
			helper.assertTrue(create != null, "GE-LIFE premise: Gold Experience has no create_lifeform");
			AbilityInput.keyPress(KEY, create, scene.user, null, InputMethod.CLICK, 0.0F, BufferingState.clickOnly(),
					create.getAbilityId());
			AbilityInput.keyRelease(KEY, scene.user);
			List<GETransformationEntity> forming = scene.level.getEntitiesOfClass(GETransformationEntity.class, scene.space);
			helper.assertTrue(forming.size() == 1, "GE-LIFE premise: the press made " + forming.size() + " transformations");
			GETransformationEntity transformation = forming.get(0);
			scene.entities.add(transformation);
			int cooldown = scene.power.getAbilityCooldown("create_lifeform");
			helper.assertTrue(cooldown > 0 && transformation.actionCooldown == cooldown,
					"GE-LIFE premise: the press set no Create cooldown (" + cooldown + ", " + transformation.actionCooldown + ")");
			Row row = scene.row(transformation.getUUID());
			helper.assertTrue(row.place() == Place.FORMING, "GE-LIFE: a forming lifeform must be a forming row, got " + row);
			helper.assertTrue(GoldExperienceLifeformRoster.revert(scene.user, transformation.getUUID()) == Answer.REVERTED,
					"GE-LIFE: the menu could not revert a forming lifeform");
			helper.assertTrue(scene.power.getAbilityCooldown("create_lifeform") <= 0,
					"GE-LIFE: the menu's revert did not refund the Create cooldown as a press does ("
							+ scene.power.getAbilityCooldown("create_lifeform") + " left of " + cooldown + ")");
		}
		finally {
			scene.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceRevertPointsOnlyAtALifeformInSight(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			scene.open(true);
			Pig pig = scene.pig(0.0D, 6.0D);
			GECreatedLifeformEffect effect = scene.lifeform(pig);
			scene.tick();
			scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
			Optional<GECreatedLifeformEffect> aimed = GoldExperienceRevertLifeformAbility.findAimedLifeform(scene.power, scene.user);
			helper.assertTrue(aimed.isPresent() && aimed.get() == effect,
					"GE-LIFE: Revert aimed at the lifeform does not point at it");

			scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, scene.user.getEyePosition().add(6.0D, 0.0D, 0.0D));
			helper.assertTrue(GoldExperienceRevertLifeformAbility.findAimedLifeform(scene.power, scene.user).isEmpty(),
					"GE-LIFE: Revert aimed 90 degrees away still points at the lifeform (it must open the menu)");

			BlockPos wallBase = BlockPos.containing(scene.origin.x, scene.origin.y, scene.origin.z + 3.0D);
			for (int dx = -1; dx <= 1; dx++) {
				for (int dy = -1; dy <= 3; dy++) {
					BlockPos wall = wallBase.offset(dx, dy, 0);
					scene.level.setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
					scene.blocks.add(wall);
				}
			}
			scene.user.lookAt(EntityAnchorArgument.Anchor.EYES, pig.getBoundingBox().getCenter());
			helper.assertTrue(GoldExperienceRevertLifeformAbility.findAimedLifeform(scene.power, scene.user).isEmpty(),
					"GE-LIFE: Revert points at a lifeform behind a wall");
		}
		finally {
			scene.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceLifeformRowsCrossTheNetworkWhole(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		List<Row> rows = new ArrayList<>();
		rows.add(new Row(UUID.randomUUID(), ResourceLocation.withDefaultNamespace("cow"), Component.literal("Bessie"),
				Component.literal("Stick"), Level.OVERWORLD.location(), new Vec3(1.5D, 64.0D, -3.25D), 42, 1234L, true,
				Place.HERE));
		rows.add(new Row(UUID.randomUUID(), null, null, Component.empty(), null, null, -1, 9000L, false, Place.UNLOADED));
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess(), ConnectionType.NEOFORGE);
		try {
			TrGELifeformRosterPacket sent = new TrGELifeformRosterPacket(rows, Answer.REVERTED, Component.literal("Bessie"));
			TrGELifeformRosterPacket.Handler.STREAM_CODEC.encode(buf, sent);
			TrGELifeformRosterPacket read = TrGELifeformRosterPacket.Handler.STREAM_CODEC.decode(buf);
			helper.assertTrue(read.rows().equals(sent.rows()) && read.answer() == Answer.REVERTED
					&& read.subject() != null && "Bessie".equals(read.subject().getString()) && buf.readableBytes() == 0,
					"GE-LIFE: the menu's rows do not cross the network whole: " + read.rows());
		}
		finally {
			buf.release();
		}
		helper.succeed();
	}

	private static final class Holder<T> {
		private T value;
	}

	/*
	 * The server replays every ability press to the clients watching its user (AbilityInput.keyPress sends a
	 * TrAbilityUsePacket, whose client handler runs AbilityInput.keyPress again, and so onClick). On the presser's own
	 * client he is the local player; on a watcher's client the same press runs with him as a remote player. The vanilla
	 * mock player answers isLocalPlayer() like the first, a server-side player like the second.
	 */
	@GameTest(template = "empty", batch = BATCH)
	public static void goldExperienceLifeformWindowsOpenOnlyOnThePressersOwnClient(GameTestHelper helper) {
		Player presser = helper.makeMockPlayer(GameType.SURVIVAL);
		Player presserOnAWatchersClient = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		try {
			helper.assertTrue(presser.isLocalPlayer() && !presserOnAWatchersClient.isLocalPlayer(),
					"GE-LIFE premise: the two test players no longer answer isLocalPlayer() as a client's own and a remote player do");
			helper.assertTrue(GoldExperienceLifeforms.pressedOnThisClient(presser),
					"GE-LIFE: Choose Lifeform and Revert must open their window on the client of the player who pressed");
			helper.assertTrue(!GoldExperienceLifeforms.pressedOnThisClient(presserOnAWatchersClient),
					"GE-LIFE: a watching player's client opened a window for someone else's Choose Lifeform or Revert press");
		}
		finally {
			presser.discard();
			presserOnAWatchersClient.discard();
		}
		helper.succeed();
	}

	private static final class Scene {
		private final GameTestHelper helper;
		private final ServerLevel level;
		private final List<Entity> entities = new ArrayList<>();
		private final List<BlockPos> blocks = new ArrayList<>();
		private FakePlayer user;
		private StandType standType;
		private StandPower power;
		private Vec3 origin;
		private AABB space;

		private Scene(GameTestHelper helper) {
			this.helper = helper;
			this.level = helper.getLevel();
		}

		private void open(boolean infiniteStamina) {
			BlockPos template = helper.absolutePos(BlockPos.ZERO);
			ChunkPos chunk = new ChunkPos(template);
			openAt(new Vec3(chunk.getMinBlockX() + 8.5D, template.getY() + 40.0D, chunk.getMinBlockZ() + 4.5D), infiniteStamina);
		}

		private void openAt(Vec3 at, boolean infiniteStamina) {
			origin = at;
			space = new AABB(origin.x - 4, origin.y - 2, origin.z - 4, origin.x + 4, origin.y + 4, origin.z + 10);
			standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("gold_experience"));
			helper.assertTrue(standType != null, "GE-LIFE premise: missing Gold Experience");
			user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "GeRosterUser"));
			user.setGameMode(GameType.SURVIVAL);
			user.getAbilities().instabuild = infiniteStamina;
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(user), "GE-LIFE premise: could not add the user");
			power = PowerClass.STAND.attachGet(user);
			StandPowerTransitions.Result inserted = StandPowerTransitions.insert(power, new StandInstance(standType));
			helper.assertTrue(inserted.status() == StandPowerTransitions.Status.APPLIED && standType.summon(user, power),
					"GE-LIFE premise: could not give and summon Gold Experience: " + inserted.status());
			power.getSummonedStandEntity().moveTo(origin.x + 1.0D, origin.y, origin.z, 0.0F, 0.0F);
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			Cow seen = EntityType.COW.create(level);
			seen.moveTo(origin.x, origin.y, origin.z - 3.0D, 0.0F, 0.0F);
			GoldExperienceLifeformState.get(user).learnLifeformsForEntity(seen, level);
			seen.discard();
			power.setStamina(power.getMaxStamina());
		}

		private Pig pig(double side, double ahead) {
			Pig pig = EntityType.PIG.create(level);
			helper.assertTrue(pig != null, "GE-LIFE premise: could not create the pig");
			pig.setNoAi(true);
			pig.setNoGravity(true);
			pig.moveTo(origin.x + side, origin.y, origin.z + ahead, 180.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(pig), "GE-LIFE premise: could not add the pig");
			entities.add(pig);
			return pig;
		}

		private GECreatedLifeformEffect lifeform(Entity target) {
			GECreatedLifeformEffect effect = ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get().create(level);
			effect.setSourceItem(new ItemStack(Items.STICK));
			effect.withTarget(target);
			power.userStandEffects.addEffect(effect);
			return effect;
		}

		// a record as it comes back from a save, made at the given game time
		private GECreatedLifeformEffect lifeformCreatedAt(Entity target, long createdAt) {
			CompoundTag nbt = new CompoundTag();
			nbt.putString("Type", ModStandAbilities.EFFECT_GE_CREATED_LIFEFORM.get().registryKey.toString());
			nbt.putInt("TickCount", 0);
			nbt.putUUID("Target", target.getUUID());
			nbt.putLong("GECreatedTime", createdAt);
			GECreatedLifeformEffect effect = (GECreatedLifeformEffect) EntityCustomEffect.fromNBT(nbt, level);
			power.userStandEffects.addEffect(effect);
			return effect;
		}

		private void tick() {
			user.getData(ModDataAttachmentTypes.DATA_EVENT_HELPER.get()).onTick();
		}

		private Row row(UUID id) {
			Optional<Row> row = GoldExperienceLifeformRoster.rows(user).stream().filter(candidate -> candidate.id().equals(id))
					.findFirst();
			helper.assertTrue(row.isPresent(), "GE-LIFE: the menu has no row for " + id);
			return row.get();
		}

		private void close() {
			try {
				if (power != null) {
					for (StandEffectInstance effect : List.copyOf(power.userStandEffects.getEffects())) {
						power.userStandEffects.removeEffect(effect);
					}
					if (power.isSummoned() && standType != null) {
						standType.forceUnsummon(user, power);
					}
				}
			}
			finally {
				entities.forEach(Entity::discard);
				blocks.forEach(pos -> level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
				if (space != null) {
					level.getEntitiesOfClass(Entity.class, space.inflate(4.0D), entity -> !(entity instanceof FakePlayer))
							.forEach(Entity::discard);
				}
				if (user != null) {
					user.discard();
				}
			}
		}
	}
}
