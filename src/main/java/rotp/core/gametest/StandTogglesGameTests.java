package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.config.client.StandToggle;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityPunchAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModGamerules;
import rotp.core.init.power.ModPlayerPowers;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.playerpower.PlayerPower;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;
import rotp.core.util.functions.JojoModUtil;
import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The owner's "Stand toggles" (R772.1): on by default, which is the game as it plays without them, an off toggle reads on
 * the server for that player's Stand alone, and the game rule still wins over a toggle that is on.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandTogglesGameTests {
	private static final BlockPos GLASS_AT = new BlockPos(2, 3, 3);

	private StandTogglesGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void togglesStartOnTravelAndReadOnFromASaveWithoutThem(GameTestHelper helper) {
		HolderLookup.Provider registries = helper.getLevel().registryAccess();
		for (StandToggle toggle : StandToggle.values()) {
			PlayerClientBroadcastedSettings settings = new PlayerClientBroadcastedSettings();
			helper.assertTrue(toggle.isOn(settings), toggle + " does not start on: the game would not play as it did");
			toggle.flip(settings);
			helper.assertTrue(!toggle.isOn(settings), toggle + " did not flip");
			for (StandToggle other : StandToggle.values()) {
				helper.assertTrue(other == toggle || other.isOn(settings), "flipping " + toggle + " turned " + other + " off");
			}
			FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
			settings.toBuf(buf);
			PlayerClientBroadcastedSettings sent = new PlayerClientBroadcastedSettings();
			sent.fromBuf(buf);
			helper.assertTrue(!toggle.isOn(sent) && buf.readableBytes() == 0, toggle + " set off came back on through the buffer");
			helper.assertTrue(!toggle.isOn(settings.copy()), "a copy of the settings turned " + toggle + " back on");
			CompoundTag nbt = settings.serializeNBT(registries);
			PlayerClientBroadcastedSettings loaded = new PlayerClientBroadcastedSettings();
			loaded.deserializeNBT(registries, nbt);
			helper.assertTrue(!toggle.isOn(loaded), toggle + " set off came back on from NBT");
		}
		PlayerClientBroadcastedSettings oldSave = new PlayerClientBroadcastedSettings();
		CompoundTag nbt = oldSave.serializeNBT(registries);
		nbt.remove("StandBreaksBlocks");
		nbt.remove("StandPicksUpItems");
		nbt.remove("StandMovesBeyondEffectiveRange");
		oldSave.deserializeNBT(registries, nbt);
		for (StandToggle toggle : StandToggle.values()) {
			helper.assertTrue(toggle.isOn(oldSave), "a save from before the toggles read " + toggle + " as off");
		}
		Cow mob = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(mob != null && PlayerClientBroadcastedSettings.isStandToggleOn(StandToggle.BREAK_BLOCKS, mob),
				"a mob's Stand lost a toggle");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aPunchObeysTheToggleAndTheRuleStillWinsOverIt(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "star_platinum")) {
			BlockPos glass = s.glass(GLASS_AT);
			s.setRule(true);
			s.settings().standBreaksBlocks = false;
			s.punch(glass);
			helper.assertTrue(s.level().getBlockState(glass).is(Blocks.GLASS), "a punch broke glass with the player's toggle off");

			s.settings().standBreaksBlocks = true;
			s.setRule(false);
			s.punch(glass);
			helper.assertTrue(s.level().getBlockState(glass).is(Blocks.GLASS), "the toggle on broke glass with the game rule off");

			s.setRule(true);
			s.punch(glass);
			helper.assertTrue(s.level().getBlockState(glass).isAir(), "with the toggle on and the rule on a punch no longer breaks glass");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void theToggleCountsForTheStandAndItsUserButNotAMoveOfAnotherPower(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "star_platinum")) {
			BlockPos glass = s.glass(GLASS_AT);
			s.setRule(true);
			helper.assertTrue(s.canDestroy(glass, s.user) && s.canDestroy(glass, s.stand), "fixture: the toggle on refuses the break");
			s.settings().standBreaksBlocks = false;
			helper.assertTrue(!s.canDestroy(glass, s.stand), "the Stand may break a block with its user's toggle off");
			helper.assertTrue(!s.canDestroy(glass, s.user), "a Stand user's own break, as a projectile's owner, passed the toggle off");
			helper.assertTrue(!JojoModUtil.breakingBlocksEnabled(s.level(), s.stand), "the rule with a breaker ignored the toggle");
			helper.assertTrue(JojoModUtil.breakingBlocksEnabled(s.level()), "outside any move the rule alone answers");

			PlayerPower playerPower = PowerClass.PLAYER_POWER.attachGet(s.user);
			playerPower.setPowerType(ModPlayerPowers.HAMON.get());
			Ability hamonMove = playerPower.getAbility("sendo_overdrive");
			helper.assertTrue(hamonMove instanceof EntityActionType, "fixture: Hamon's Sendo Overdrive is missing: " + hamonMove);
			EntityActionInstance hamonAction = probe(hamonMove, null);
			LivingComponentAction.getComponent(s.user).setAction(hamonAction, s.user, SyncType.NO_SYNC);
			helper.assertTrue(s.canDestroy(glass, s.user), "the Stand toggle stopped a Hamon move from breaking a block");
			LivingComponentAction.getComponent(s.user).setAction(null, SyncType.NO_SYNC);

			Player noStand = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			try {
				noStand.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).standBreaksBlocks = false;
				helper.assertTrue(s.canDestroy(glass, noStand), "a toggle left off stopped a player with no Stand from breaking a block");
			}
			finally {
				noStand.discard();
			}
		}
		helper.succeed();
	}

	// the add-ons' gate that names no breaker: Stand abilities of The Hand and the like call it from their own action
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aStandMoveThatNamesNoBreakerObeysTheToggleInItsOwnTick(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "star_platinum")) {
			s.setRule(true);
			List<Boolean> asked = new ArrayList<>();
			Ability punch = s.power.getAbility("punch");
			helper.assertTrue(punch != null, "fixture: Star Platinum's punch is missing");
			EntityActionInstance action = probe(punch, () -> asked.add(JojoModUtil.breakingBlocksEnabled(s.level())));
			LivingComponentAction slot = LivingComponentAction.getComponent(s.stand);
			slot.setAction(action, s.user, SyncType.NO_SYNC);
			s.stand.summonLockTicks = 0;
			slot.tick();
			s.settings().standBreaksBlocks = false;
			slot.tick();
			helper.assertTrue(asked.equals(List.of(true, false)),
					"the rule asked in a Stand action's tick, toggle on then off, answered " + asked);
			helper.assertTrue(EntityActionInstance._runningOwnTick() == null, "the running action stayed set after its tick");
			slot.setAction(null, SyncType.NO_SYNC);
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aStandUnderManualControlPicksUpItemsOnlyWithTheToggleOn(GameTestHelper helper) {
		try (Scene s = new Scene(helper, "star_platinum")) {
			s.stand.setManuallyControlled(true);
			ItemEntity item = new ItemEntity(s.level(), s.stand.getX(), s.stand.getY(), s.stand.getZ(), new ItemStack(Items.APPLE));
			helper.assertTrue(s.level().addFreshEntity(item), "fixture: item not added");
			try {
				s.settings().standPicksUpItems = false;
				s.pickUp();
				helper.assertTrue(!item.isRemoved() && s.stand.getMainHandItem().isEmpty() && s.stand.getOffhandItem().isEmpty(),
						"the Stand picked up an item with the toggle off");
				s.settings().standPicksUpItems = true;
				s.pickUp();
				helper.assertTrue(item.isRemoved() && s.stand.getMainHandItem().is(Items.APPLE),
						"the Stand under manual control no longer picks up an item: main hand " + s.stand.getMainHandItem());
			}
			finally {
				item.discard();
				s.stand.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aStandStopsAtItsEffectiveRangeOnlyWithTheToggleOff(GameTestHelper helper) {
		// a close-range Stand keeps its full strength out to its base reach (StandMeleeReach), 5 for Crazy Diamond's user
		try (Scene s = new Scene(helper, "crazy_diamond")) {
			s.stand.setManuallyControlled(true);
			double fullRange = s.stand.getMaxRangeForMovement(s.user);
			helper.assertTrue(Math.abs(fullRange - 7.5D) < 1.0E-6, "the toggle on changed how far Crazy Diamond goes: " + fullRange);
			s.settings().standMovesBeyondEffectiveRange = false;
			helper.assertTrue(Math.abs(s.stand.getMaxRangeForMovement(s.user) - 5.0D) < 1.0E-6,
					"with the toggle off Crazy Diamond's range is " + s.stand.getMaxRangeForMovement(s.user) + ", not its base reach 5");
			s.moveFromUser(8.0D);
			helper.assertTrue(Math.abs(s.stand.distanceTo(s.user) - 5.0D) < 1.0E-6,
					"with the toggle off the Stand moved to " + s.stand.distanceTo(s.user) + " from its user, not 5");
			s.settings().standMovesBeyondEffectiveRange = true;
			s.moveFromUser(8.0D);
			helper.assertTrue(Math.abs(s.stand.distanceTo(s.user) - fullRange) < 1.0E-6,
					"with the toggle on the Stand stopped at " + s.stand.distanceTo(s.user) + ", not " + fullRange);
		}
		try (Scene s = new Scene(helper, "hierophant_green")) {
			s.stand.setManuallyControlled(true);
			double maxRange = s.stand.getMaxRange();
			double effective = s.stand.getEffectiveRange();
			helper.assertTrue(effective < maxRange && s.stand.getMaxRangeForMovement(s.user) == maxRange,
					"the toggle on changed how far Hierophant Green goes, or it has no margin: " + maxRange + " / " + effective);
			s.settings().standMovesBeyondEffectiveRange = false;
			helper.assertTrue(s.stand.getMaxRangeForMovement(s.user) == effective,
					"with the toggle off a long-range Stand's range is " + s.stand.getMaxRangeForMovement(s.user) + ", not its effective " + effective);
		}
		helper.succeed();
	}

	private static EntityActionInstance probe(Ability ability, Runnable onTick) {
		EntityActionInstance action = new EntityActionInstance((EntityActionType) ability) {
			@Override
			public void actionTick() {
				if (onTick != null) {
					onTick.run();
				}
			}
		};
		action.phasesLength.put(ActionPhase.PERFORM, 200F);
		action.setStartingPhase();
		return action;
	}

	private static final class Scene implements AutoCloseable {
		final GameTestHelper helper;
		final FakePlayer user;
		final StandType type;
		final StandPower power;
		final StandEntity stand;
		private final boolean ruleBefore;
		private BlockPos placed;

		Scene(GameTestHelper helper, String standId) {
			this.helper = helper;
			ServerLevel level = helper.getLevel();
			ruleBefore = level.getGameRules().getBoolean(ModGamerules.BREAK_BLOCKS);
			Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(BlockPos.ZERO)).add(0.0D, 25.0D, 0.0D);
			user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "StandToggles"));
			user.setGameMode(GameType.SURVIVAL);
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(user), "fixture: could not add the user");
			power = PowerClass.STAND.attachGet(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && type.summon(user, power), "fixture: could not give and summon " + standId);
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "fixture: the summoned Stand is missing");
			stand.setNoGravity(true);
		}

		ServerLevel level() {
			return helper.getLevel();
		}

		PlayerClientBroadcastedSettings settings() {
			return user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get());
		}

		void setRule(boolean on) {
			level().getGameRules().getRule(ModGamerules.BREAK_BLOCKS).set(on, level().getServer());
		}

		BlockPos glass(BlockPos relative) {
			placed = helper.absolutePos(relative);
			level().setBlock(placed, Blocks.GLASS.defaultBlockState(), Block.UPDATE_CLIENTS);
			return placed;
		}

		void punch(BlockPos pos) {
			StandEntityPunchAbility.StandEntityPunch.hitBlockTarget(new ActionTarget(pos, Direction.UP), level(), stand, false);
		}

		boolean canDestroy(BlockPos pos, net.minecraft.world.entity.LivingEntity breaker) {
			return JojoModUtil.canEntityDestroy(level(), pos, level().getBlockState(pos), breaker);
		}

		void moveFromUser(double blocks) {
			stand.moveTo(user.getX(), user.getY(), user.getZ(), 0.0F, 0.0F);
			stand.move(MoverType.SELF, new Vec3(blocks, 0.0D, 0.0D));
		}

		void pickUp() {
			try {
				Method pickUp = StandEntity.class.getDeclaredMethod("pickUpItemEntities");
				pickUp.setAccessible(true);
				pickUp.invoke(stand);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("fixture: pickUpItemEntities failed", e);
			}
		}

		@Override
		public void close() {
			if (placed != null) {
				level().setBlock(placed, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
			}
			setRule(ruleBefore);
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
