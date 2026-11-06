package rotp.core.gametest;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.imageio.ImageIO;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;

import io.netty.buffer.Unpooled;
import rotp.core.JojoModConfig;
import rotp.core.client.entityanim.PreFrameEntityAnimCalc.LivingAnimState;
import rotp.core.client.input.AbilityInputState;
import rotp.core.client.input.controlscheme.ClientControlScheme;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.customobjects.entity_projectile.ThrownBlockEntity;
import rotp.core.gametest.StandTaskTargetOffsetGameTests.Scene;
import rotp.core.impl.stands._entitybase.StandBlockTossAbility;
import rotp.core.impl.stands._entitybase.StandEntityGrabAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModEntityTypes;
import rotp.core.init.ModGamerules;
import rotp.core.init.ModStatusEffects;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.MovesetBuilder;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.EntityActionAbility;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.ControlSchemeTemplate;
import rotp.core.powersystem.ability.controls.InputBindTemplate;
import rotp.core.powersystem.ability.controls.InputKey;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.StandUnlockableSkill;
import rotp.core.powersystem.standpower.entity.EntityStandType;
import rotp.core.powersystem.standpower.entity.StandControlType;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.powersystem.standpower.type.StandTypePersistentData;
import rotp.core.subsystems.entity_useitem.StandItemUse;

import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Block Toss: the Stand throws a block it holds on the held key of the punches. The press goes through the path a client's press
 * takes on the server, the block through its own flight. What is drawn and heard, and the client's choice of the move on the key
 * beyond its priority, are left to the in-game pass.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BlockTossGameTests {
	private static final short KEY = 21;
	private static final int CHARGE_TICKS = 16;
	private static final int PERFORM_TICKS = 6;
	private static final int RECOVERY_TICKS = 12;
	private static final float STAMINA_COST = 20.0F;
	private static final float BASE_DAMAGE = 2.5F;
	private static final String TOSS = "block_toss";
	private static final List<String> WITH_THE_SKILL = List.of("star_platinum", "crazy_diamond", "the_world");
	private static final List<String> WITHOUT_THE_SKILL = List.of("gold_experience", "magicians_red", "silver_chariot",
			"hierophant_green", "boy_ii_man", "mr_president");
	private static final List<String> LANGUAGES = List.of("en_us", "en_pt", "es_es", "es_ve", "it_it", "ja_jp", "lzh", "pt_br", "ru_ru",
			"uk_ua", "zh_cn", "zh_tw");

	private BlockTossGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aHeldKeyThrowsTheBlockInTheStandsHand(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			scene.stand().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.DIRT, 3));
			EntityActionInstance toss = press(scene);
			for (int tick = 0; tick < CHARGE_TICKS; tick++) {
				helper.assertTrue(toss.getPhase() == ActionPhase.BUTTON_CHARGE, "The charge ended after " + tick + " ticks: " + toss.getPhase());
				tick(scene);
			}
			helper.assertTrue(toss.getPhase() == ActionPhase.WINDUP, "After the charge the key does not hold it ready: " + toss.getPhase());
			tick(scene, 60);
			helper.assertTrue(toss.getPhase() == ActionPhase.WINDUP && thrown(scene).isEmpty(),
					"A held key did not wait for its release: " + toss.getPhase() + ", thrown " + thrown(scene).size());

			AbilityInput.keyRelease(KEY, scene.user());
			helper.assertTrue(toss.getPhase() == ActionPhase.PERFORM, "The release did not throw: " + toss.getPhase());
			tick(scene);
			helper.assertTrue(scene.stand().offsetFromUser.getRelativeOffset().z >= 2.0D,
					"The Stand does not step forward to throw: " + scene.stand().offsetFromUser.getRelativeOffset());
			tick(scene, PERFORM_TICKS - 2);
			helper.assertTrue(thrown(scene).isEmpty(), "The block left before the end of the perform");
			tick(scene);
			List<ThrownBlockEntity> thrown = thrown(scene);
			helper.assertTrue(thrown.size() == 1, "The throw made " + thrown.size() + " blocks");
			ThrownBlockEntity block = thrown.get(0);
			helper.assertTrue(block.getBlock().is(Blocks.DIRT), "The block thrown is not the one held: " + block.getBlock());
			helper.assertTrue(block.getOwner() == scene.stand(), "The thrown block is not the Stand's: " + block.getOwner());
			Vec3 motion = block.getDeltaMovement();
			float speed = StandStatFormulas.projectileVelocityScaling(scene.stand().getAttackDamage(), 1.0F);
			helper.assertTrue(Math.abs(motion.length() - speed) < 0.01D && motion.z > 0.99D * speed,
					"The block does not fly along the look at speed " + speed + ": " + motion);
			helper.assertTrue(toss.getPhase() == ActionPhase.RECOVERY, "The throw did not go on to its recovery: " + toss.getPhase());
			tick(scene, RECOVERY_TICKS);
			helper.assertTrue(toss.isOver(), "The recovery is not " + RECOVERY_TICKS + " ticks: " + toss.getPhase());
		});
		helper.succeed();
	}

	// the key let go during the charge: it charges on and throws, with no held pose between
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void lettingGoDuringTheChargeThrowsWhenItEnds(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			scene.stand().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE, 3));
			EntityActionInstance toss = press(scene);
			tick(scene, 4);
			AbilityInput.keyRelease(KEY, scene.user());
			helper.assertTrue(toss.getPhase() == ActionPhase.BUTTON_CHARGE, "Letting go cut the charge short: " + toss.getPhase());
			helper.assertTrue(!((EntityActionAbility) toss.ability).isHeldSincePress(toss), "The action let go still counts as held");
			tick(scene, CHARGE_TICKS - 5);
			helper.assertTrue(toss.getPhase() == ActionPhase.BUTTON_CHARGE, "The charge ended early: " + toss.getPhase());
			tick(scene);
			helper.assertTrue(toss.getPhase() == ActionPhase.PERFORM, "The charge let go does not throw as it ends: " + toss.getPhase());
			tick(scene, PERFORM_TICKS);
			helper.assertTrue(thrown(scene).size() == 1, "The throw made " + thrown(scene).size() + " blocks");
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void theThrowCostsOneBlockAndItsStamina(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			scene.stand().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE, 3));
			StandPower power = scene.power();
			power.setStamina(power.getMaxStamina());
			float stamina = power.getStamina();
			EntityActionInstance toss = press(scene);
			tick(scene, CHARGE_TICKS + 3);
			helper.assertTrue(power.getStamina() == stamina && scene.stand().getMainHandItem().getCount() == 3,
					"The hold is not free: stamina " + stamina + " -> " + power.getStamina() + ", " + scene.stand().getMainHandItem());
			AbilityInput.keyRelease(KEY, scene.user());
			tick(scene, PERFORM_TICKS);
			helper.assertTrue(toss.getPhase() == ActionPhase.RECOVERY && thrown(scene).size() == 1, "Fixture: no throw");
			helper.assertTrue(scene.stand().getMainHandItem().is(Items.COBBLESTONE) && scene.stand().getMainHandItem().getCount() == 2,
					"The throw did not use up one block: " + scene.stand().getMainHandItem());
			helper.assertTrue(Math.abs(stamina - power.getStamina() - STAMINA_COST) < 0.01F,
					"The throw did not cost " + STAMINA_COST + " stamina: " + stamina + " -> " + power.getStamina());
			helper.assertTrue(power.getAbility(TOSS).getStaminaCost(power) == STAMINA_COST,
					"The ability reports a cost of " + power.getAbility(TOSS).getStaminaCost(power));
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aCreativeUserKeepsTheBlock(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			scene.user().getAbilities().instabuild = true;
			scene.stand().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE, 3));
			press(scene);
			tick(scene, CHARGE_TICKS);
			AbilityInput.keyRelease(KEY, scene.user());
			tick(scene, PERFORM_TICKS);
			helper.assertTrue(thrown(scene).size() == 1, "Fixture: no throw");
			helper.assertTrue(scene.stand().getMainHandItem().getCount() == 3, "A Creative user's block was used up: "
					+ scene.stand().getMainHandItem());
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void withoutABlockTheAbilityIsNotThereAndTheBarrageHasTheKey(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			StandEntity stand = scene.stand();
			helper.assertTrue(resolve(scene, TOSS) == null, "Empty hands have the toss");
			helper.assertTrue(resolve(scene, "barrage") != null, "Empty hands lost the barrage");
			helper.assertTrue(!AbilityInput.withConditionCheck(resolve(scene, TOSS), scene.user(), InputMethod.HOLD),
					"A press with nothing to throw passes");
			stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			stand.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.SNOWBALL, 4));
			helper.assertTrue(resolve(scene, TOSS) == null, "A sword and a snowball can be tossed as blocks");
			stand.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.COBBLESTONE));
			helper.assertTrue(resolve(scene, TOSS) != null, "A block in the other hand does not count");

			stand.setItemSlot(EquipmentSlot.OFFHAND, ItemStack.EMPTY);
			ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
			helper.assertTrue(resolve(scene, TOSS) == null, "Fixture: a sword alone has the toss");
			stand.setItemSlot(EquipmentSlot.OFFHAND, shulker);
			helper.assertTrue(resolve(scene, TOSS) != null, "An empty shulker box is a block like any other");
			NonNullList<ItemStack> contents = NonNullList.withSize(1, new ItemStack(Items.DIAMOND, 64));
			shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));
			helper.assertTrue(resolve(scene, TOSS) == null, "A full shulker box can be thrown away, with what is in it");

			stand.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.COBBLESTONE));
			Cow cow = scene.cow(new Vec3(0.0D, 0.0D, 3.0D));
			var grab = stand.getData(ModDataAttachmentTypes.LIVING_GRAB.get());
			try {
				grab.setGrabTarget(cow);
				helper.assertTrue(resolve(scene, TOSS) == null, "A Stand that holds an entity can toss a block as well");
			}
			finally {
				grab.setGrabTarget(null);
			}
			helper.assertTrue(resolve(scene, TOSS) != null, "Fixture: letting go did not give the toss back");
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aBlockGoneDuringTheHoldThrowsNothing(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			StandPower power = scene.power();
			power.setStamina(power.getMaxStamina());
			float stamina = power.getStamina();
			StandEntity stand = scene.stand();

			// in the charge, the key held
			stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE));
			EntityActionInstance toss = press(scene);
			tick(scene, 3);
			stand.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			tick(scene);
			helper.assertTrue(toss.isOver(), "The charge went on with an empty hand: " + toss.getPhase());
			AbilityInput.keyRelease(KEY, scene.user());

			// held ready
			stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE));
			toss = press(scene);
			tick(scene, CHARGE_TICKS + 2);
			helper.assertTrue(toss.getPhase() == ActionPhase.WINDUP, "Fixture: not held ready: " + toss.getPhase());
			stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			tick(scene);
			helper.assertTrue(toss.isOver(), "The hold went on with a sword in the hand: " + toss.getPhase());
			AbilityInput.keyRelease(KEY, scene.user());

			// let go in the charge: the key is up, so only the charge's end can tell
			stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE));
			toss = press(scene);
			tick(scene, 2);
			AbilityInput.keyRelease(KEY, scene.user());
			stand.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			tick(scene, CHARGE_TICKS - 2);
			helper.assertTrue(toss.isOver(), "A charge let go went on to throw with an empty hand: " + toss.getPhase());

			// already thrown off by the release, the hand emptied in the perform
			stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE));
			toss = press(scene);
			tick(scene, CHARGE_TICKS);
			AbilityInput.keyRelease(KEY, scene.user());
			helper.assertTrue(toss.getPhase() == ActionPhase.PERFORM, "Fixture: not in the perform: " + toss.getPhase());
			stand.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
			tick(scene, PERFORM_TICKS);

			helper.assertTrue(thrown(scene).isEmpty(), "A block flew out of an empty hand");
			helper.assertTrue(power.getStamina() == stamina, "A throw that never left cost stamina: " + stamina + " -> " + power.getStamina());
		});
		helper.succeed();
	}

	// 1.16 stopHeldAction(true) checks once more before a released hold fires
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void aStunnedStandThrowsNothingOnTheRelease(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			StandEntity stand = scene.stand();
			stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE, 2));
			EntityActionInstance toss = press(scene);
			tick(scene, CHARGE_TICKS);
			helper.assertTrue(toss.getPhase() == ActionPhase.WINDUP, "Fixture: not held ready: " + toss.getPhase());
			stand.addEffect(new MobEffectInstance(ModStatusEffects.STUN, 40));
			AbilityInput.keyRelease(KEY, scene.user());
			helper.assertTrue(toss.isOver(), "A stunned Stand's release went on: " + toss.getPhase());
			helper.assertTrue(thrown(scene).isEmpty() && stand.getMainHandItem().getCount() == 2, "A stunned Stand threw its block");
			stand.removeEffect(ModStatusEffects.STUN);
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void onlyThePressBeforeThePerformCutsItShort(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			scene.stand().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE, 2));
			EntityActionAbility heavy = (EntityActionAbility) scene.power().getAbility("heavy_punch");
			EntityActionInstance toss = press(scene);
			helper.assertTrue(toss.canBeCancelledInto(heavy), "Another move cannot cut the charge short");
			tick(scene, CHARGE_TICKS);
			helper.assertTrue(toss.canBeCancelledInto(heavy), "Another move cannot cut the held throw short");
			AbilityInput.keyRelease(KEY, scene.user());
			helper.assertTrue(!toss.canBeCancelledInto(heavy), "Another move can cut the throw short in its perform");
			tick(scene, PERFORM_TICKS);
			helper.assertTrue(!toss.canBeCancelledInto(heavy), "Another move can cut the recovery short");
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void theBlockShattersOnAMobAndTheHitIsItsUsers(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			ThrownBlockEntity block = throwOne(scene);
			Cow cow = scene.cow(new Vec3(0.0D, 0.0D, 6.0D));
			float health = cow.getHealth();
			block.setPos(cow.getBoundingBox().getCenter().add(0.0D, 0.0D, -3.0D));
			for (int tick = 0; tick < 10 && !block.isRemoved(); tick++) {
				block.tick();
			}
			helper.assertTrue(block.isRemoved(), "The block flew on after hitting the cow: at " + block.position());
			float damage = BASE_DAMAGE * JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get().floatValue();
			helper.assertTrue(Math.abs(health - cow.getHealth() - damage) < 0.01F, "The hit took " + (health - cow.getHealth()) + ", not " + damage);
			helper.assertTrue(cow.getLastHurtByMob() == scene.user(), "The hit is not its user's: " + cow.getLastHurtByMob());
			helper.assertTrue(itemsAround(scene).isEmpty(), "The block came back as an item: " + itemsAround(scene));
		});
		helper.succeed();
	}

	/*
	 * The griefing rule on the block it hits. It breaks only what breaks at a touch, and then only under the rule; a stone wall stays
	 * and the thrown block still ends. Reloaded in flight it has no owner to ask the rule of, and the rule still holds.
	 */
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void theBlockShattersOnABlockAndBreaksOnlyWhatTheRulesAllow(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			ServerLevel level = helper.getLevel();
			boolean rule = level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
			BlockPos wall = BlockPos.containing(scene.user().position().add(0.0D, 1.0D, 5.0D));
			try {
				setRule(scene, true);
				hitWall(scene, throwOne(scene), wall, Blocks.STONE.defaultBlockState());
				helper.assertTrue(level.getBlockState(wall).is(Blocks.STONE), "The thrown block broke stone");

				hitWall(scene, throwOne(scene), wall, Blocks.SLIME_BLOCK.defaultBlockState());
				helper.assertTrue(level.getBlockState(wall).isAir(), "With the rule on, what breaks at a touch stayed: " + level.getBlockState(wall));

				setRule(scene, false);
				hitWall(scene, throwOne(scene), wall, Blocks.SLIME_BLOCK.defaultBlockState());
				helper.assertTrue(level.getBlockState(wall).is(Blocks.SLIME_BLOCK), "The rule off, the thrown block still broke a block");

				ThrownBlockEntity reloaded = ModEntityTypes.THROWN_BLOCK.get().create(level);
				helper.assertTrue(reloaded != null && level.addFreshEntity(reloaded), "Fixture: no block without an owner");
				scene.spawned().add(reloaded);
				reloaded.setDeltaMovement(0.0D, 0.0D, 1.0D);
				hitWall(scene, reloaded, wall, Blocks.SLIME_BLOCK.defaultBlockState());
				helper.assertTrue(level.getBlockState(wall).is(Blocks.SLIME_BLOCK), "The rule off, a block reloaded in flight still broke a block");
			}
			finally {
				level.setBlock(wall, Blocks.AIR.defaultBlockState(), 3);
				setRule(scene, rule);
				itemsAround(scene).forEach(ItemEntity::discard);
			}
		});
		helper.succeed();
	}

	// the recall rule: what a Stand has sent out goes on, and its hit stays its user's
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aThrownBlockFliesOnAfterARecallAndStillHits(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			ThrownBlockEntity block = throwOne(scene);
			Cow cow = scene.cow(new Vec3(0.0D, 0.0D, 8.0D));
			float health = cow.getHealth();
			block.setPos(cow.getBoundingBox().getCenter().add(0.0D, 0.0D, -5.0D));
			scene.power().getPowerType().forceUnsummon(scene.user(), scene.power());
			helper.assertTrue(scene.stand().isRecalled(), "Fixture: the Stand was not recalled");
			block.tick();
			helper.assertTrue(!block.isRemoved(), "The thrown block ended with its Stand's recall");
			for (int tick = 0; tick < 10 && !block.isRemoved(); tick++) {
				block.tick();
			}
			helper.assertTrue(block.isRemoved() && cow.getHealth() < health, "The block did not reach the cow after the recall: health "
					+ cow.getHealth() + ", block " + (block.isRemoved() ? "gone" : "flying"));
			helper.assertTrue(cow.getLastHurtByMob() == scene.user(), "After the recall the hit is not its user's: " + cow.getLastHurtByMob());
			itemsAround(scene).forEach(ItemEntity::discard);
		});
		helper.succeed();
	}

	// the throw is a move of the Stand's own body: recalled before the block leaves, nothing flies and nothing is paid
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aRecallBeforeTheBlockLeavesStopsTheThrow(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			StandPower power = scene.power();
			power.setStamina(power.getMaxStamina());
			float stamina = power.getStamina();
			scene.stand().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE, 2));
			EntityActionInstance toss = press(scene);
			tick(scene, CHARGE_TICKS);
			AbilityInput.keyRelease(KEY, scene.user());
			tick(scene, 2);
			power.getPowerType().forceUnsummon(scene.user(), power);
			helper.assertTrue(toss.isOver(), "The throw outlived the Stand: " + toss.getPhase());
			helper.assertTrue(power.getStamina() == stamina, "A throw that never left cost stamina: " + stamina + " -> " + power.getStamina());
			AABB around = new AABB(scene.user().blockPosition()).inflate(16.0D);
			helper.assertTrue(helper.getLevel().getEntitiesOfClass(ThrownBlockEntity.class, around).isEmpty(), "A block flew from a recalled Stand");
			itemsAround(scene).forEach(ItemEntity::discard);
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aFlyingBlockEndsByItsLifespanAndKeepsItsBlockThroughASave(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			ThrownBlockEntity block = throwOne(scene, Items.DIRT);
			ServerLevel level = helper.getLevel();
			helper.assertTrue(block.getBlock().is(Blocks.DIRT), "Fixture: the block thrown is " + block.getBlock());
			CompoundTag saved = block.saveWithoutId(new CompoundTag());
			ThrownBlockEntity restored = ModEntityTypes.THROWN_BLOCK.get().create(level);
			helper.assertTrue(restored != null, "The thrown block's type cannot be made");
			restored.load(saved);
			helper.assertTrue(restored.getBlock() == block.getBlock(), "A save lost the block: " + restored.getBlock());

			RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
			try {
				block.writeSpawnData(buf);
				ThrownBlockEntity onAClient = ModEntityTypes.THROWN_BLOCK.get().create(level);
				helper.assertTrue(onAClient != null, "The thrown block's type cannot be made");
				onAClient.readSpawnData(buf);
				helper.assertTrue(onAClient.getBlock() == block.getBlock() && !buf.isReadable(), "A client is not told the block: " + onAClient.getBlock());
			}
			finally {
				buf.release();
			}
			helper.assertTrue(block.getBbWidth() == 1.0F && block.getBbHeight() == 1.0F, "The block is not a block's size: " + block.getBbWidth());
			helper.assertTrue(block.ticksLifespan() == 100, "Its lifespan is " + block.ticksLifespan());
			block.tickCount = block.ticksLifespan() + 1;
			block.tick();
			helper.assertTrue(block.isRemoved(), "A block that hit nothing outlived its lifespan");
		});
		helper.succeed();
	}

	// the clip is mirrored: the arm that holds the block plays it, on the user's client too; a block the grab took comes in the off hand
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void theArmThatHoldsTheBlockThrowsIt(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			StandEntity stand = scene.stand();
			stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE, 2));
			EntityActionInstance right = press(scene);
			helper.assertTrue("block_toss_right".equals(right.getEntityAnim().name()), "The main hand of a right-handed user plays "
					+ right.getEntityAnim());
			LivingAnimState pose = new LivingAnimState();
			right.extractAnim(pose, stand, 0.5F);
			helper.assertTrue(pose.actionPhase == ActionPhase.BUTTON_CHARGE && pose.phaseCompletion < 1.0F,
					"The clip's charge section is passed over: drawn in " + pose.actionPhase + " at " + pose.phaseCompletion);
			right.forceStop();
			AbilityInput.keyRelease(KEY, scene.user());
			tick(scene);

			ItemStack taken = new ItemStack(Items.COBBLESTONE);
			StandEntityGrabAbility.addOriginalPos(taken, stand.blockPosition(), helper.getLevel());
			stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_SWORD));
			stand.setItemSlot(EquipmentSlot.OFFHAND, taken);
			EntityActionInstance left = press(scene);
			helper.assertTrue("block_toss_left".equals(left.getEntityAnim().name()), "The off hand of a right-handed user plays "
					+ left.getEntityAnim());
			helper.assertTrue("block_toss_left".equals(clientCopy(scene, left).getEntityAnim().name()),
					"A client's copy plays " + clientCopy(scene, left).getEntityAnim());
			tick(scene, CHARGE_TICKS);
			AbilityInput.keyRelease(KEY, scene.user());
			tick(scene, PERFORM_TICKS);
			helper.assertTrue(thrown(scene).size() == 1 && stand.getOffhandItem().isEmpty() && stand.getMainHandItem().is(Items.IRON_SWORD),
					"The throw did not take the block the grab took: " + thrown(scene).size() + " thrown, hands "
							+ stand.getMainHandItem() + " " + stand.getOffhandItem());
		});
		helper.succeed();
	}

	/*
	 * The barrage gives way on the client's choice of the move on the key (ClientControlScheme.setPrioritizedAbility), which goes by
	 * this order. The flags are set through the one method the ability's client state calls: its source is read for that.
	 */
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void theTossTakesTheHeldKeyFromTheBarrage(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "star_platinum", scene -> {
			scene.stand().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE));
			AbilityConditionCheck toss = resolve(scene, TOSS);
			AbilityConditionCheck barrage = resolve(scene, "barrage");
			helper.assertTrue(toss != null && barrage != null, "Fixture: with a block in hand the two moves are " + toss + " and " + barrage);
			toss.clientInputState = StandBlockTossAbility.takesTheHeldKey(AbilityInputState.init())._value;
			barrage.clientInputState = AbilityInputState.init()._value;
			try {
				Method priority = ClientControlScheme.class.getDeclaredMethod("abilityPriority", AbilityConditionCheck.class, Power.class);
				priority.setAccessible(true);
				int ofToss = (int) priority.invoke(null, toss, scene.power());
				int ofBarrage = (int) priority.invoke(null, barrage, scene.power());
				helper.assertTrue(ofToss < ofBarrage, "The barrage keeps the key: toss " + ofToss + ", barrage " + ofBarrage);
			}
			catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
				throw new IllegalStateException("Fixture: cannot ask the order of the moves", e);
			}
			helper.assertTrue(StandItemUse.inputStateBesideTheItem(toss.clientInputState) == toss.clientInputState,
					"The toss steps aside for the Stand's item, which is the very block it throws");
		});
		helper.assertTrue(source("impl/stands/_entitybase/StandBlockTossAbility.java")
				.contains("return takesTheHeldKey(super.cl_abilityInputState(context));"), "The client's state does not set the flags");
		helper.succeed();
	}

	// the one rule of the core: whichever Stand has the grab has the toss, as a starting skill, on the held key of every control scheme, beside its barrage
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void everyCoreStandWithAGrabStartsWithItOnTheHeldKey(GameTestHelper helper) {
		List<String> grabbing = new ArrayList<>();
		for (StandType type : JojoRegistries.DEFAULT_STANDS_REG) {
			String id = type.getId().getPath();
			boolean hasGrab = type.getDefaultMoveset().abilities.containsKey("grab");
			if (hasGrab) {
				grabbing.add(id);
			}
			helper.assertTrue(type.getUnlockableSkills().containsKey(TOSS) == hasGrab, id + " has the grab " + hasGrab + " but the skill "
					+ type.getUnlockableSkills().containsKey(TOSS));
		}
		helper.assertTrue(grabbing.containsAll(WITH_THE_SKILL) && WITH_THE_SKILL.containsAll(grabbing), "The core Stands with a grab are " + grabbing);
		for (String id : WITH_THE_SKILL) {
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(id));
			helper.assertTrue(type != null, "Missing Stand type " + id);
			var skill = type.getUnlockableSkills().get(TOSS);
			helper.assertTrue(skill != null && skill.isStarting && skill.unlocksAbilities.contains(TOSS), id + " does not start with Block Toss");
			helper.assertTrue(type.getBaseMoveset().getAbility(TOSS) instanceof StandBlockTossAbility, id + " has no Block Toss in its moveset");
			MovesetBuilder built = type.getDefaultMoveset().deepCopy();
			built.addWhatItsMovesBring();
			built.build(type.getPowerClass(), type.getId());
			for (String schemeName : new String[] { "hotbar", "keybinds" }) {
				ControlSchemeTemplate scheme = built.controlSchemes.get(schemeName);
				helper.assertTrue(scheme != null, id + " lost its " + schemeName + " scheme");
				assertOnTheHeldKey(helper, id + " " + schemeName, scheme);
			}
			assertOnTheHeldKey(helper, id + "'s client template", type.makeDefaultControlSchemeTemplate());
		}
		helper.succeed();
	}

	private static void assertOnTheHeldKey(GameTestHelper helper, String where, ControlSchemeTemplate scheme) {
		for (String move : new String[] { TOSS, "barrage" }) {
			Pair<InputMethod, InputBindTemplate> bind = scheme.defaultGroup.separateBinds.get(move);
			helper.assertTrue(bind != null && bind.getFirst() == InputMethod.HOLD && bind.getSecond() == InputKey.LMB,
					where + ": " + move + " is not on the held left button: " + bind);
		}
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aMovesetWithoutAGrabDoesNotHaveIt(GameTestHelper helper) {
		for (String id : WITHOUT_THE_SKILL) {
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(id));
			helper.assertTrue(type != null, "Missing Stand type " + id);
			helper.assertTrue(!type.getDefaultMoveset().abilities.containsKey("grab"), "Fixture: " + id + " has a grab");
			helper.assertTrue(!type.getUnlockableSkills().containsKey(TOSS) && type.getBaseMoveset().getAbility(TOSS) == null
					&& !type.makeDefaultControlSchemeTemplate().defaultGroup.separateBinds.containsKey(TOSS), id + " has Block Toss with no grab");
		}
		EntityStandType noGrab = standOf(new MovesetBuilder().addHumanoidStandStuff().addAbility("barrage", ModStandAbilities.BARRAGE), "gametest_no_grab");
		helper.assertTrue(!noGrab.getUnlockableSkills().containsKey(TOSS) && noGrab.getBaseMoveset().getAbility(TOSS) == null,
				"The humanoid call alone gave a moveset Block Toss");
		helper.succeed();
	}

	// the rule fills a gap and takes nothing: another ability under the name, its own skill and key, or no skill at all, stay as declared
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aMovesetThatDeclaresItsOwnKeepsIt(GameTestHelper helper) {
		EntityStandType own = standOf(new MovesetBuilder()
				.addAbility("grab", ModStandAbilities.GRAB)
				.addAbility("barrage", ModStandAbilities.BARRAGE)
				.addAbility(TOSS, ModStandAbilities.HEAVY_PUNCH)
				.makeControlScheme("hotbar")
					.bind(TOSS, InputMethod.CLICK, InputKey.RMB)
					.bind("barrage", InputMethod.HOLD, InputKey.LMB)
				.finalizeControlScheme()
				.makeControlScheme("keybinds")
					.bind("barrage", InputMethod.HOLD, InputKey.LMB)
				.finalizeControlScheme()
				.addSkill(StandUnlockableSkill.unlockableAbility(TOSS, 100)), "gametest_own_toss");
		helper.assertTrue(own.getBaseMoveset().getAbility(TOSS).abilityType == ModStandAbilities.HEAVY_PUNCH.get(),
				"A moveset's own ability under the name was replaced: " + own.getBaseMoveset().getAbility(TOSS).abilityType);
		StandUnlockableSkill ownSkill = own.getUnlockableSkills().get(TOSS);
		helper.assertTrue(!ownSkill.isStarting && ownSkill.expToUnlock == 100, "A moveset's own skill was replaced: starting " + ownSkill.isStarting
				+ ", " + ownSkill.expToUnlock + " to unlock");
		Pair<InputMethod, InputBindTemplate> ownBind = own.makeDefaultControlSchemeTemplate().defaultGroup.separateBinds.get(TOSS);
		helper.assertTrue(ownBind != null && ownBind.getFirst() == InputMethod.CLICK && ownBind.getSecond() == InputKey.RMB,
				"A moveset's own key was replaced: " + ownBind);

		EntityStandType noSkill = standOf(new MovesetBuilder().addAbility("grab", ModStandAbilities.GRAB).addAbility(TOSS, ModStandAbilities.HEAVY_PUNCH),
				"gametest_own_toss_no_skill");
		helper.assertTrue(noSkill.getBaseMoveset().getAbility(TOSS).abilityType == ModStandAbilities.HEAVY_PUNCH.get()
				&& !noSkill.getUnlockableSkills().containsKey(TOSS), "A moveset's own ability with no skill got the core's");
		helper.succeed();
	}

	// a config's "disable" takes the ability out of the built moveset, and the rule, which runs on every copy, must not put it back
	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aConfigThatDisablesItKeepsItDisabled(GameTestHelper helper) {
		EntityStandType type = standOf(new MovesetBuilder().addAbility("grab", ModStandAbilities.GRAB), "gametest_toss_disabled");
		helper.assertTrue(type.getBaseMoveset().getAbility(TOSS) != null, "Fixture: the moveset has no Block Toss");
		JsonObject moveset = new JsonObject();
		JsonArray disable = new JsonArray();
		disable.add(TOSS);
		moveset.add("disable", disable);
		JsonObject config = new JsonObject();
		config.add("moveset", moveset);
		type.applyConfig(config);
		helper.assertTrue(type.getBaseMoveset().getAbility(TOSS) == null, "A config could not disable Block Toss");
		type.restoreDefaults();
		helper.assertTrue(type.getBaseMoveset().getAbility(TOSS) != null, "Block Toss did not come back with the defaults");
		helper.succeed();
	}

	// an add-on's Stand finds the core's icon and words: its ability's sprite and text keys are the core's, not its own namespace's, and a skin's own icon still comes first
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void anAddOnsStandFindsTheCoresIconAndWords(GameTestHelper helper) {
		EntityStandType type = new EntityStandType(new StandStats.Builder().power(1).speed(1).range(1, 2).durability(1).precision(1).build(),
				new MovesetBuilder().addHumanoidStandStuff().addAbility("grab", ModStandAbilities.GRAB),
				ResourceLocation.fromNamespaceAndPath("rotp_gametest", "grabbing_stand"), StandControlType.CLOSE_RANGE_DIRECT, 1, 2, true, true);
		Ability toss = type.getBaseMoveset().getAbility(TOSS);
		helper.assertTrue(toss != null, "An add-on's Stand with a grab has no Block Toss");
		helper.assertTrue(toss.getSpriteId(null).equals(JojoMod.resLoc(TOSS)), "Its icon is looked for at " + toss.getSpriteId(null));
		helper.assertTrue("jojo_ripples.ability.block_toss".equals(toss.getTranslationKey()), "Its name is looked for at " + toss.getTranslationKey());
		String icons = source("client/standskin/sprites/AbilityIconSprites.java");
		helper.assertTrue(icons.contains("spritePath = skinSpritePath(curStandSkin.skinId, abilitySpriteId.getPath());"),
				"A skin's own icon of the ability no longer comes first");
		helper.assertTrue(icons.contains("return icons.apply(JojoMod.resLoc(skill.skillName));"), "A skill with no ability of its name, Use Item, no longer finds the core's icon");
		String words = source("client/ui/text/StandSkillText.java");
		helper.assertTrue(words.contains("return \"jojo_ripples.skill.\" + skill.skillName + suffix;") && words.contains("return Component.translatable(baseKey, args);"),
				"A skill's text is no longer found under the core's key on another mod's Stand");
		helper.succeed();
	}

	// the same key on a Stand other than the first two: with empty hands the hold is the barrage, with a block the throw
	@GameTest(template = "empty", timeoutTicks = 100)
	public static void onAnotherStandTheBarrageKeepsTheKeyWithEmptyHands(GameTestHelper helper) {
		StandTaskTargetOffsetGameTests.withStand(helper, "the_world", scene -> {
			StandEntity stand = scene.stand();
			helper.assertTrue(resolve(scene, TOSS) == null, "The World's empty hands have the toss");
			AbilityConditionCheck barrage = resolve(scene, "barrage");
			helper.assertTrue(barrage != null, "The World's empty hands lost the barrage");
			stand.summonLockTicks = 0;
			BufferingState state = BufferingState.clickCanBuffer();
			AbilityInput.keyPress(KEY, barrage.ability, scene.user(), null, InputMethod.HOLD, 0, state, barrage.ability.getAbilityId());
			EntityActionInstance running = stand.getCurStandAction();
			helper.assertTrue(state.isActionSuccess && running != null && running.ability == barrage.ability,
					"The held key with empty hands did not start the barrage: " + running);
			AbilityInput.keyRelease(KEY, scene.user());
			running.forceStop();
			tick(scene);

			stand.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE, 3));
			EntityActionInstance toss = press(scene);
			helper.assertTrue("block_toss_right".equals(toss.getEntityAnim().name()), "The World's main hand plays " + toss.getEntityAnim());
			tick(scene, CHARGE_TICKS);
			AbilityInput.keyRelease(KEY, scene.user());
			tick(scene, PERFORM_TICKS);
			helper.assertTrue(thrown(scene).size() == 1 && stand.getMainHandItem().getCount() == 2, "The World threw " + thrown(scene).size() + " blocks, holds "
					+ stand.getMainHandItem());
			thrown(scene).forEach(ThrownBlockEntity::discard);
		});
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void anOldSaveOfAnyStandWithAGrabGetsIt(GameTestHelper helper) {
		for (String id : WITH_THE_SKILL) {
			StandTaskTargetOffsetGameTests.withStand(helper, id, scene -> {
				StandTypePersistentData data = scene.power().getCurTypeData();
				scene.stand().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.COBBLESTONE));
				helper.assertTrue(data.isSkillUnlocked(TOSS) && resolve(scene, TOSS) != null, "A new " + id + " has no Block Toss");
				CompoundTag saved = data.serializeNBT(helper.getLevel().registryAccess());
				ListTag old = new ListTag();
				for (Tag skill : saved.getList("skills", Tag.TAG_STRING)) {
					if (!TOSS.equals(skill.getAsString())) {
						old.add(StringTag.valueOf(skill.getAsString()));
					}
				}
				saved.put("skills", old);
				data._setSkillUnlocked(TOSS, false, false);
				helper.assertTrue(resolve(scene, TOSS) == null, "A locked skill still has the ability");
				data.deserializeNBT(helper.getLevel().registryAccess(), saved);
				helper.assertTrue(data.isSkillUnlocked(TOSS), "A save of " + id + " from before the skill does not get it");
			});
		}
		helper.succeed();
	}

	private static EntityStandType standOf(MovesetBuilder moveset, String id) {
		return new EntityStandType(new StandStats.Builder().power(1).speed(1).range(1, 2).durability(1).precision(1).build(), moveset, JojoMod.resLoc(id),
				StandControlType.CLOSE_RANGE_DIRECT, 1, 2, true, true);
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theSkillHasItsWordsItsIconItsClipsAndItsRenderer(GameTestHelper helper) {
		String lmb = Character.toString(0xEAFD);
		for (String language : LANGUAGES) {
			JsonObject words = StandClipPoses116GameTests.readJson(helper, "lang/" + language + ".json");
			for (String key : List.of("jojo_ripples.skill.block_toss", "jojo_ripples.skill.block_toss.desc",
					"jojo_ripples.skill.block_toss.controls", "jojo_ripples.ability.block_toss", "entity.jojo_ripples.thrown_block")) {
				helper.assertTrue(words.has(key) && !words.get(key).getAsString().isBlank(), language + " lacks " + key);
			}
			helper.assertTrue(words.get("jojo_ripples.skill.block_toss.controls").getAsString().contains(lmb),
					language + " does not show the left button in the controls");
		}
		try (InputStream in = BlockTossGameTests.class.getResourceAsStream("/assets/jojo_ripples/textures/ability/block_toss.png")) {
			helper.assertTrue(in != null, "The skill has no icon");
			BufferedImage icon = ImageIO.read(in);
			helper.assertTrue(icon.getWidth() == 16 && icon.getHeight() == 16, "The icon is " + icon.getWidth() + "x" + icon.getHeight());
		}
		catch (IOException e) {
			throw new IllegalStateException(e);
		}
		for (String stand : WITH_THE_SKILL) {
			JsonObject clip = StandClipPoses116GameTests.readJson(helper,
					"stand_skins/" + stand + "/assets/jojo_ripples/animations/" + stand + ".animation.json")
					.getAsJsonObject("animations").getAsJsonObject(TOSS);
			assertATossClip(helper, stand, clip);
		}
		JsonObject defaults = StandClipPoses116GameTests.readJson(helper, "animations/stand_default.animation.json").getAsJsonObject("animations");
		assertATossClip(helper, "stand_default", defaults.getAsJsonObject(TOSS));
		helper.assertTrue(defaults.has(TOSS + "_right") && defaults.has("bow_shoot_right"), "stand_default does not write the other hand of a clip its mirror cannot hold");
		for (String stand : List.of("star_platinum", "crazy_diamond")) {
			JsonObject theirs = StandClipPoses116GameTests.readJson(helper, "stand_skins/" + stand + "/assets/jojo_ripples/animations/" + stand + ".animation.json")
					.getAsJsonObject("animations").getAsJsonObject(TOSS).getAsJsonObject("bones");
			for (Map.Entry<String, JsonElement> bone : defaults.getAsJsonObject(TOSS).getAsJsonObject("bones").entrySet()) {
				helper.assertTrue(!bone.getKey().endsWith("arm") || !bone.getValue().equals(theirs.get(bone.getKey())),
						"stand_default's " + bone.getKey() + " is a copy of " + stand + "'s");
			}
		}
		helper.assertTrue(source("client/ModEntityTypeRenderers.java")
				.contains("event.registerEntityRenderer(ModEntityTypes.THROWN_BLOCK.get(), ThrownBlockRenderer::new);"),
				"The thrown block has no renderer, and a client that meets one crashes");
		helper.succeed();
	}

	private static void assertATossClip(GameTestHelper helper, String stand, JsonObject clip) {
		helper.assertTrue(clip != null && clip.has("bones"), stand + " has no clip for the throw");
		String timeline = clip.getAsJsonObject("timeline").toString();
		for (String mark : new String[] { "phase = BUTTON_CHARGE", "phase = WINDUP", "phase = PERFORM", "phase = RECOVERY", "mirror.start" }) {
			helper.assertTrue(timeline.contains(mark), stand + "'s clip lost " + mark);
		}
	}

	private static String source(String file) {
		for (Path at = Path.of("").toAbsolutePath(); at != null; at = at.getParent()) {
			Path path = at.resolve("src/main/java/rotp/core").resolve(file);
			if (Files.isRegularFile(path)) {
				try {
					return Files.readString(path).replace("\r\n", "\n");
				}
				catch (IOException e) {
					throw new IllegalStateException("Could not read " + path, e);
				}
			}
		}
		throw new IllegalStateException("No source of " + file + " above " + Path.of("").toAbsolutePath());
	}

	// the press as a client's goes on the server: the move resolved for the key, its check, then the key press
	private static EntityActionInstance press(Scene scene) {
		GameTestHelper helper = scene.helper();
		AbilityConditionCheck toss = resolve(scene, TOSS);
		helper.assertTrue(toss != null && AbilityInput.withConditionCheck(toss, scene.user(), InputMethod.HOLD),
				"The press was refused with " + scene.stand().getMainHandItem() + " and " + scene.stand().getOffhandItem() + " in hand");
		scene.stand().summonLockTicks = 0;
		BufferingState state = BufferingState.clickCanBuffer();
		AbilityInput.keyPress(KEY, toss.ability, scene.user(), null, InputMethod.HOLD, 0, state, toss.ability.getAbilityId());
		EntityActionInstance action = scene.stand().getCurStandAction();
		helper.assertTrue(state.isActionSuccess && action != null && action.getPhase() == ActionPhase.BUTTON_CHARGE,
				"The press did not start the charge: started " + state.isActionSuccess + ", action " + action);
		return action;
	}

	private static ThrownBlockEntity throwOne(Scene scene) {
		return throwOne(scene, Items.COBBLESTONE);
	}

	private static ThrownBlockEntity throwOne(Scene scene, Item blockItem) {
		scene.stand().setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(blockItem, 16));
		int before = thrown(scene).size();
		EntityActionInstance toss = press(scene);
		tick(scene, CHARGE_TICKS);
		AbilityInput.keyRelease(KEY, scene.user());
		tick(scene, PERFORM_TICKS);
		List<ThrownBlockEntity> thrown = thrown(scene);
		scene.helper().assertTrue(thrown.size() == before + 1, "Fixture: the throw made " + (thrown.size() - before) + " blocks");
		// a move of the Stand's next throw must not wait behind this one's recovery
		toss.forceStop();
		tick(scene);
		ThrownBlockEntity newest = thrown.get(thrown.size() - 1);
		scene.spawned().add(newest);
		return newest;
	}

	private static void hitWall(Scene scene, ThrownBlockEntity block, BlockPos wall, BlockState state) {
		ServerLevel level = scene.helper().getLevel();
		level.setBlock(wall, state, 3);
		block.setPos(Vec3.atCenterOf(wall).add(0.0D, 0.0D, -3.0D));
		for (int tick = 0; tick < 10 && !block.isRemoved(); tick++) {
			block.tick();
		}
		scene.helper().assertTrue(block.isRemoved(), "The block flew on through " + state + ": at " + block.position());
	}

	private static void setRule(Scene scene, boolean value) {
		ServerLevel level = scene.helper().getLevel();
		level.getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(value, level.getServer());
	}

	private static void tick(Scene scene) {
		LivingComponentAction.getComponent(scene.stand()).tick();
	}

	private static void tick(Scene scene, int times) {
		for (int i = 0; i < times; i++) {
			tick(scene);
		}
	}

	private static List<ThrownBlockEntity> thrown(Scene scene) {
		AABB around = new AABB(scene.user().blockPosition()).inflate(16.0D);
		return new ArrayList<>(scene.helper().getLevel().getEntitiesOfClass(ThrownBlockEntity.class, around,
				block -> block.getOwner() == scene.stand()));
	}

	// the blocks these tests use, as items; other tests run beside these, with items of their own
	private static List<ItemEntity> itemsAround(Scene scene) {
		AABB around = new AABB(scene.user().blockPosition()).inflate(8.0D);
		return scene.helper().getLevel().getEntitiesOfClass(ItemEntity.class, around,
				item -> item.getItem().is(Items.COBBLESTONE) || item.getItem().is(Items.SLIME_BLOCK));
	}

	// the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
	private static AbilityConditionCheck resolve(Scene scene, String move) {
		try {
			Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
			cached.setAccessible(true);
			cached.setBoolean(scene.power(), false);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
		}
		return scene.power().updateAvailableMoves().getContextVariationContainer(move);
	}

	// the action as a client decodes it, given its Stand as the client's slot does
	private static EntityActionInstance clientCopy(Scene scene, EntityActionInstance action) {
		GameTestHelper helper = scene.helper();
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			EntityActionInstance.encode(buf, action);
			EntityActionInstance copy = EntityActionInstance.decode(helper.getLevel(), buf);
			helper.assertTrue(copy != null && copy.getClass() == action.getClass() && !buf.isReadable(), "Fixture: the action decodes as " + copy);
			Field performer = EntityActionInstance.class.getDeclaredField("performer");
			performer.setAccessible(true);
			performer.set(copy, scene.stand());
			return copy;
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Fixture: cannot give the client's copy its Stand", e);
		}
		finally {
			buf.release();
		}
	}
}
