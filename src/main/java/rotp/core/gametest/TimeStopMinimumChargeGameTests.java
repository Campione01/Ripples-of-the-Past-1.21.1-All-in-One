package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import com.mojang.authlib.GameProfile;

import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;

import rotp.core.JojoModConfig;
import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.impl.stands.theworld.TimeStopBlinkAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModSoundEvents;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityId;
import rotp.core.powersystem.ability.AbilityType;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopCooldowns;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundStopSoundPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.PlayLevelSoundEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The owner's rule of 2026-11-05: "時間停止這裏在達到最低蓄力前鬆開不應該成功釋放，想要緊急情況下釋放應該是瞬間連按兩次". A release before half of
 * the full charge starts nothing, at or past it the stop keeps its share of the charge (D-core-91), a full charge fires by
 * itself, and a second press within 7 ticks of the first fires what a release at once used to fire. A tap sounds like
 * nothing, a charge says the line 1.16's said, and the double press plays the sound of 1.16's instant stop.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TimeStopMinimumChargeGameTests {
	private static final short KEY = 143;
	private static final String TIME_STOP = TimeStopLearning.TIME_STOP;
	// short of the 100-tick cap, so that a stop still trains
	private static final int TRAINED = 90;
	private static final int OPENING = TimeStopAbility.TIME_STOP_OPENING_SETTLE_TICKS;
	private static final PowerSkillUnlocks.Owner TEST_UNLOCKS =
			PowerSkillUnlocks.register(JojoMod.resLoc("time_stop_minimum_charge_gametest"));

	private TimeStopMinimumChargeGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 50, batch = "jojo_ripples_timestop_default_dimension")
	public static void aRealTimeStopCoversDistantChunksByDefault(GameTestHelper helper) {
		helper.assertTrue(JojoModConfig.getCommonConfigInstance(false).timeStopChunkRange.get() == 0,
				"The loaded default must stop the entire current dimension");
		assertRangeFromRealPress(helper, 0);
	}

	@GameTest(template = "empty", timeoutTicks = 50, batch = "jojo_ripples_timestop_explicit_range")
	public static void aRealTimeStopHonorsAnExplicitChunkLimit(GameTestHelper helper) {
		ModConfigSpec.IntValue range = JojoModConfig.COMMON_SPEC.getValues()
				.get(List.of("Stand settings", "Time Stop", "timeStopRangeChunks"));
		int original = range.get();
		helper.testInfo.addListener(new CloseOnEnd(() -> range.set(original)));
		range.set(12);
		assertRangeFromRealPress(helper, 12);
	}

	private static void assertRangeFromRealPress(GameTestHelper helper, int expectedRange) {
		Scene s = new Scene(helper, "star_platinum", 4, false);
		s.guarded(() -> helper.assertTrue(s.press() != null, "The real Time Stop charge was not admitted"));
		helper.runAfterDelay(15, () -> s.guarded(() -> {
			s.assertChargedFor(15);
			s.release();
			TimeStopState.Instance instance = s.instance();
			helper.assertTrue(instance != null && instance.chunkRange() == expectedRange,
					"The real charge did not use the loaded range " + expectedRange + ": " + instance);
			ChunkPos center = instance.centerPos();
			ChunkPos near = new ChunkPos(center.x + 11, center.z);
			ChunkPos edge = new ChunkPos(center.x + 12, center.z);
			ChunkPos distant = new ChunkPos(center.x + 64, center.z + 64);
			boolean wholeDimension = expectedRange == 0;
			helper.assertTrue(s.state.isTimeStopped(near)
							&& s.state.isTimeStopped(edge) == wholeDimension
							&& s.state.isTimeStopped(distant) == wholeDimension,
					"Time Stop coverage did not match the configured dimension/finite boundary");
			s.close();
			helper.succeed();
		}));
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.TIME_STOP)
	public static void aTapOfStarPlatinumsTimeStopStartsNothingAndPaysNothing(GameTestHelper helper) {
		aTapStartsNothing(helper, "star_platinum", 4);
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.TIME_STOP)
	public static void aTapOfTheWorldsTimeStopStartsNothingAndPaysNothing(GameTestHelper helper) {
		aTapStartsNothing(helper, "the_world", 3);
	}

	private static void aTapStartsNothing(GameTestHelper helper, String stand, int resolve) {
		Scene s = new Scene(helper, stand, resolve);
		s.guarded(() -> s.press());
		helper.runAfterDelay(3, () -> s.guarded(() -> {
			s.release();
			s.assertNothingStarted("a tap");
			s.assertNoTimeStopSound("a tap");
		}));
		helper.runAfterDelay(10, () -> s.guarded(() -> {
			s.assertNothingStarted("the time after a tap");
			s.assertNoTimeStopSound("the time after a tap");
			helper.succeed();
		}));
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = GameTestBatches.TIME_STOP)
	public static void starPlatinumsTimeStopReleasesFromFifteenOfThirtyTicks(GameTestHelper helper) {
		aReleaseStartsItsShareFromHalfTheCharge(helper, "star_platinum", 4, 30, 15);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = GameTestBatches.TIME_STOP)
	public static void theWorldsTimeStopReleasesFromFifteenOfThirtyTicks(GameTestHelper helper) {
		aReleaseStartsItsShareFromHalfTheCharge(helper, "the_world", 3, 30, 15);
	}

	@GameTest(template = "empty", timeoutTicks = 80, batch = GameTestBatches.TIME_STOP)
	public static void theWorldsTimeStopReleasesFromTenOfTwentyTicksFromResolveFour(GameTestHelper helper) {
		aReleaseStartsItsShareFromHalfTheCharge(helper, "the_world", 4, 20, 10);
	}

	private static void aReleaseStartsItsShareFromHalfTheCharge(GameTestHelper helper, String stand, int resolve, int charge,
			int minimum) {
		helper.assertTrue(TimeStopAbility.getMinReleaseChargeTicks(charge) == minimum,
				"half of a " + charge + "-tick charge: " + TimeStopAbility.getMinReleaseChargeTicks(charge));
		Scene s = new Scene(helper, stand, resolve);
		s.guarded(() -> {
			EntityActionInstance held = s.press();
			helper.assertTrue(held != null && held.getCurPhaseLength() == charge, stand + " does not charge for " + charge
					+ " ticks: " + held);
		});
		helper.runAfterDelay(minimum - 1, () -> s.guarded(() -> {
			s.assertChargedFor(minimum - 1);
			s.release();
			s.assertNothingStarted("a release a tick before half the charge");
		}));
		helper.runAfterDelay(minimum, () -> s.guarded(() -> s.press()));
		helper.runAfterDelay(minimum * 2, () -> s.guarded(() -> {
			EntityActionInstance held = s.chargeInSlot();
			s.assertChargedFor(minimum);
			s.release();
			TimeStopState.Instance started = s.instance();
			helper.assertTrue(started != null && started.totalTicks() == TRAINED / 2, "a release at half the charge starts half"
					+ " of its " + TRAINED + " ticks: " + started);
			float cost = TimeStopLearning.getTimeStopStaminaCost(s.power, TIME_STOP, TRAINED / 2);
			helper.assertTrue(near(s.power.getMaxStamina() - s.power.getStamina(), cost), "it pays for those ticks, " + cost
					+ ": paid " + (s.power.getMaxStamina() - s.power.getStamina()));
			StandEntity standEntity = s.power.getSummonedStandEntity();
			helper.assertTrue(standEntity == null || standEntity.getPerformPose() == held,
					"its Stand keeps the perform pose: " + (standEntity != null ? standEntity.getPerformPose() : null));
			helper.succeed();
		}));
	}

	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.TIME_STOP)
	public static void aDoublePressOfStarPlatinumsTimeStopFiresWhatAReleaseAtOnceFired(GameTestHelper helper) {
		aDoublePressFires(helper, "star_platinum", 4, 30, ModSoundEvents.STAR_PLATINUM_TIME_STOP_BLINK);
	}

	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.TIME_STOP)
	public static void aDoublePressOfTheWorldsTimeStopFiresWhatAReleaseAtOnceFired(GameTestHelper helper) {
		aDoublePressFires(helper, "the_world", 3, 30, ModSoundEvents.THE_WORLD_TIME_STOP_BLINK);
	}

	/*
	 * Exactly at the window's end, and for Star Platinum with its Stand away: its first press summons it and the double
	 * press still works. Before this rule a press released at once started a stop of the trained ticks' share of one tick
	 * of charge, paid for those ticks, set the cooldown and trained for them as it ended, and left the perform pose.
	 */
	private static void aDoublePressFires(GameTestHelper helper, String stand, int resolve, int charge,
			Holder<SoundEvent> blinkSound) {
		Scene s = new Scene(helper, stand, resolve);
		int ticks = TimeStopLearning.getReleasedTimeStopTicks(TRAINED, 1, charge);
		float[] pointsBefore = new float[1];
		s.guarded(() -> {
			helper.assertTrue(s.power.getSummonedStandEntity() == null, "setup: " + stand + " is out already");
			pointsBefore[0] = s.points();
			s.press();
			helper.assertTrue(stand.equals("the_world") == (s.power.getSummonedStandEntity() == null),
					"setup: Star Platinum's press summons it, The World from Resolve 3 stays away: "
							+ s.power.getSummonedStandEntity());
		});
		helper.runAfterDelay(2, () -> s.guarded(() -> {
			s.release();
			s.assertNothingStarted("the first press let go");
			s.assertNoTimeStopSound("the first press let go");
		}));
		helper.runAfterDelay(TimeStopAbility.DOUBLE_PRESS_WINDOW_TICKS, () -> s.guarded(() -> {
			BufferingState pressed = BufferingState.clickCanBuffer();
			HeldInputEntry held = s.press(pressed);
			TimeStopState.Instance started = s.instance();
			helper.assertTrue(pressed.isActionSuccess && started != null && started.totalTicks() == ticks,
					"the second press, " + TimeStopAbility.DOUBLE_PRESS_WINDOW_TICKS + " ticks after the first, fires the "
							+ ticks + "-tick stop at once: " + started);
			helper.assertTrue(held == null && s.slot().getAction() == null && s.user.hasEffect(ModStatusEffects.TIME_STOP),
					"nothing is left to hold or to let go: " + held + ", " + s.slot().getAction());
			float cost = TimeStopLearning.getTimeStopStaminaCost(s.power, TIME_STOP, ticks);
			helper.assertTrue(near(s.power.getMaxStamina() - s.power.getStamina(), cost), "it pays for its " + ticks
					+ " ticks, " + cost + ": paid " + (s.power.getMaxStamina() - s.power.getStamina()));
			StandEntity standEntity = s.power.getSummonedStandEntity();
			helper.assertTrue(standEntity == null || standEntity.getPerformPose() != null,
					"its Stand takes the perform pose");
			helper.assertTrue(s.heardOnce(blinkSound, SoundSource.AMBIENT), "it plays the blink sound " + blinkSound.value()
					.getLocation() + ": heard " + s.timeStopSounds());
			helper.assertTrue(s.timeStopSounds().size() == 1, "and says no line and plays no stop sound: " + s.timeStopSounds());
		}));
		helper.runAfterDelay(TimeStopAbility.DOUBLE_PRESS_WINDOW_TICKS + OPENING + ticks + 3, () -> s.guarded(() -> {
			helper.assertTrue(s.removed != null && s.removed.ticksPassed() == ticks, "the stop runs out: " + s.removed);
			int cooldown = s.power.getAbilityCooldown(TIME_STOP);
			int expected = TimeStopCooldowns.timeStopCooldown(s.power, ticks);
			helper.assertTrue(expected > 0 && cooldown > 0 && cooldown <= expected, "its cooldown is that of a " + ticks
					+ "-tick stop, " + expected + ": " + cooldown);
			float trained = TimeStopLearning.learningPerTick(s.power) * ticks;
			helper.assertTrue(near(s.points() - pointsBefore[0], trained), "it trains for its " + ticks + " ticks, " + trained
					+ " points: " + (s.points() - pointsBefore[0]));
			helper.succeed();
		}));
	}

	@GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.TIME_STOP)
	public static void aSlowSecondPressIsAChargeOfItsOwn(GameTestHelper helper) {
		helper.assertTrue(TimeStopAbility.DOUBLE_PRESS_WINDOW_TICKS == 7, "the double press's window is vanilla's for a"
				+ " double-tap sprint: " + TimeStopAbility.DOUBLE_PRESS_WINDOW_TICKS);
		Scene s = new Scene(helper, "the_world", 3);
		int slow = TimeStopAbility.DOUBLE_PRESS_WINDOW_TICKS + 1;
		s.guarded(() -> s.press());
		helper.runAfterDelay(2, () -> s.guarded(s::release));
		helper.runAfterDelay(slow, () -> s.guarded(() -> {
			EntityActionInstance held = s.press();
			helper.assertTrue(s.instance() == null && held != null && held.getPhase() == ActionPhase.BUTTON_CHARGE,
					"a second press " + slow + " ticks after the first starts no stop, only a charge: " + s.instance() + ", " + held);
		}));
		helper.runAfterDelay(slow + 2, () -> s.guarded(() -> {
			s.release();
			s.assertNothingStarted("the slow press let go");
		}));
		// it began a pair of its own
		helper.runAfterDelay(slow + 4, () -> s.guarded(() -> {
			HeldInputEntry held = s.press(BufferingState.clickCanBuffer());
			helper.assertTrue(held == null && s.instance() != null, "a press 4 ticks after the slow one is its double press: "
					+ s.instance());
			helper.succeed();
		}));
	}

	/*
	 * A press in the user's own stop resumes it (its stop here has at most 11 ticks left, so at once) and a press that close
	 * to the resume is no first press: mashing the key to resume a stop starts no new one, however fast it goes on.
	 */
	@GameTest(template = "empty", timeoutTicks = 60, batch = GameTestBatches.TIME_STOP)
	public static void aResumeFollowedByQuickPressesStartsNoStop(GameTestHelper helper) {
		Scene s = new Scene(helper, "the_world", 3);
		s.guarded(() -> s.press());
		helper.runAfterDelay(1, () -> s.guarded(s::release));
		helper.runAfterDelay(2, () -> s.guarded(() -> {
			s.press();
			helper.assertTrue(s.instance() != null, "setup: the double press started no stop");
		}));
		helper.runAfterDelay(10, () -> s.guarded(() -> {
			BufferingState resume = BufferingState.clickCanBuffer();
			HeldInputEntry held = s.press(resume);
			TimeStopState.Instance stopping = s.instance();
			helper.assertTrue(resume.isActionSuccess && held == null && stopping != null && stopping.ticksLeft() == 0,
					"the press did not resume the stop: " + stopping);
		}));
		helper.runAfterDelay(12, () -> s.guarded(() -> {
			helper.assertTrue(s.instance() == null, "the resumed stop did not end: " + s.instance());
			// what this asks of the input must not hide behind the short stop's own cooldown
			s.power.setAbilityCooldown(TIME_STOP, 0);
			float stamina = s.power.getStamina();
			EntityActionInstance held = s.press();
			helper.assertTrue(held != null && held.getPhase() == ActionPhase.BUTTON_CHARGE && s.instance() == null,
					"the first press after the resume is a charge at most: " + held + ", " + s.instance());
			helper.assertTrue(near(s.power.getStamina(), stamina), "it pays nothing");
		}));
		helper.runAfterDelay(13, () -> s.guarded(s::release));
		helper.runAfterDelay(14, () -> s.guarded(() -> {
			EntityActionInstance held = s.press();
			helper.assertTrue(s.instance() == null && held != null, "the next quick press is not a double press with the one"
					+ " before it, " + (s.level.getGameTime() - s.input().timeStopResumePressTime) + " ticks after the resume: "
					+ s.instance());
		}));
		helper.runAfterDelay(15, () -> s.guarded(s::release));
		// out of the resume's window the keys are as ever
		helper.runAfterDelay(19, () -> s.guarded(() -> s.press()));
		helper.runAfterDelay(20, () -> s.guarded(s::release));
		helper.runAfterDelay(21, () -> s.guarded(() -> {
			s.press();
			helper.assertTrue(s.instance() != null, "a double press later on still fires");
			helper.succeed();
		}));
	}

	// a mob holds the charge until it fires by itself; a release too early starts nothing for a mob either
	@GameTest(template = "empty", timeoutTicks = 100, batch = GameTestBatches.TIME_STOP)
	public static void aMobsTimeStopFiresAtItsFullChargeAndNotOnAnEarlyRelease(GameTestHelper helper) {
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(zombie != null, "setup: could not create the zombie");
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "setup: could not add the zombie");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		StandPower power = PowerClass.STAND.attachGet(zombie);
		helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "setup: could not give the zombie Star Platinum");
		power.setResolveLevel(4);
		power.getCurTypeData()._lockedAbilities.remove(TIME_STOP);
		power.setStamina(power.getMaxStamina());
		TimeStopState state = helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get());
		Consumer<TimeStopLifecycleEvent.PreStart> isolate = event -> {
			if (event.getInstance().userId() == zombie.getId()) {
				event.setInstance(event.getInstance().withArea(new ChunkPos(51300, 51300), 1));
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, TimeStopLifecycleEvent.PreStart.class, isolate);
		helper.testInfo.addListener(new CloseOnEnd(() -> {
			NeoForge.EVENT_BUS.unregister(isolate);
			state.removeInstance(zombie.getId());
			if (power.isSummoned()) {
				type.forceUnsummon(zombie, power);
			}
			zombie.discard();
		}));
		HeldInputEntry first = AbilityInput.keyPressMob(power.getAbility(TIME_STOP), zombie, null, InputMethod.HOLD);
		helper.assertTrue(first != null && first.action instanceof TimeStopAbility.TimeStopAction,
				"the zombie's press did not begin a charge: " + first);
		helper.runAfterDelay(5, () -> {
			AbilityInput.keyRelease(first.keyId, zombie);
			helper.assertTrue(state.getInstance(zombie.getId()).isEmpty()
					&& LivingComponentAction.getCurEntityAction(zombie, PowerClass.STAND) == null,
					"the zombie's release after 5 of 30 ticks started a stop or left its charge");
		});
		helper.runAfterDelay(TimeStopAbility.DOUBLE_PRESS_WINDOW_TICKS + 3, () -> {
			HeldInputEntry second = AbilityInput.keyPressMob(power.getAbility(TIME_STOP), zombie, null, InputMethod.HOLD);
			helper.assertTrue(second != null && second.action instanceof TimeStopAbility.TimeStopAction
					&& state.getInstance(zombie.getId()).isEmpty(), "the zombie's next press did not begin a charge: " + second);
			helper.runAfterDelay(31, () -> {
				helper.assertTrue(state.getInstance(zombie.getId()).isPresent(),
						"the zombie's charge did not fire by itself at its full 30 ticks");
				helper.succeed();
			});
		});
	}

	// 1.16 TimeStop.getHoldDurationToFire: 0 in stopped time, so a press there goes off at once, as no double press's first
	@GameTest(template = "empty", timeoutTicks = 20, batch = GameTestBatches.TIME_STOP)
	public static void inSomeonesStoppedTimeAPressHasNoChargeAndIsNoFirstPress(GameTestHelper helper) {
		Scene s = new Scene(helper, "the_world", 3);
		int foreign = 1_000_143;
		s.guarded(() -> {
			try {
				helper.assertTrue(s.state.tryPutInstance(new TimeStopState.Instance(foreign, 200, 200,
						new ChunkPos(s.user.blockPosition()), 1, foreign, "time_stop_minimum_charge_gametest", foreign, 0)),
						"setup: could not stop time over the user");
				EntityActionInstance held = s.press();
				helper.assertTrue(held != null && held.phasesLength.getFloat(ActionPhase.BUTTON_CHARGE) == 0.0F
						&& held.getPhase() == ActionPhase.PERFORM, "a press in stopped time has no charge: " + held);
				helper.assertTrue(s.input().timeStopFirstPressTime == EntityActionInputState.NO_PRESS,
						"it counts as a double press's first");
				s.slot().tick();
				helper.assertTrue(s.instance() != null && s.instance().totalTicks() == TRAINED, "it goes off at once for its"
						+ " whole length: " + s.instance());
				helper.assertTrue(s.heard.stream().noneMatch(sound -> sound.endsWith("@voice")),
						"1.16 TimeStop#getShout said no line inside stopped time: " + s.heard);
			}
			finally {
				// removed before the level ticks, so no neighbouring test is frozen
				s.state.removeInstance(foreign);
				s.state.removeInstance(s.user.getId());
			}
		});
		s.close();
		helper.succeed();
	}

	// 1.16 Diego's THEWORLDTimeStop.getHoldDurationToFire 0: a charge of its own of 0 ticks is no charge to let go of
	@GameTest(template = "empty", timeoutTicks = 20, batch = GameTestBatches.TIME_STOP)
	public static void aTimeStopWithNoChargeFiresOnItsPressAndIsNoFirstPress(GameTestHelper helper) {
		Scene s = new Scene(helper, "the_world", 3);
		s.guarded(() -> {
			TimeStopAbility noCharge = new AbilityType<TimeStopAbility>(JojoMod.resLoc("time_stop"), NoChargeTimeStop::new)
					.createInstance(new AbilityId(PowerClass.STAND, JojoMod.resLoc("the_world"), TIME_STOP));
			BufferingState pressed = BufferingState.clickOnly();
			noCharge.onKeyPress(s.level, s.user, null, InputMethod.HOLD, 0.0F, pressed);
			EntityActionInstance held = s.slot().getAction();
			helper.assertTrue(pressed.isActionSuccess && held != null && held.getPhase() == ActionPhase.PERFORM,
					"the press has no charge to wait for: " + held);
			helper.assertTrue(s.input().timeStopFirstPressTime == EntityActionInputState.NO_PRESS,
					"it counts as a double press's first");
			s.slot().tick();
			helper.assertTrue(s.instance() != null && s.instance().totalTicks() == TRAINED, "it stops time for its whole length: "
					+ s.instance());
		});
		s.close();
		helper.succeed();
	}

	private static final class NoChargeTimeStop extends TimeStopAbility {
		private NoChargeTimeStop(AbilityType<?> type, AbilityId id) {
			super(type, id);
		}

		@Override
		public void initActionFromConfig(EntityActionInstance action, Level level, LivingEntity powerUser, LivingEntity performer) {
			super.initActionFromConfig(action, level, powerUser, performer);
			action.phasesLength.put(ActionPhase.BUTTON_CHARGE, 0);
		}
	}

	/*
	 * In the charge's own ticks, within one game tick: nothing in a tap's ticks, the line 1.16's press said after them, cut
	 * off for its hearers by a release before the minimum or a dropped charge, and not by a release at the minimum or a full
	 * charge, whose start sound still asks for a stop of 40 ticks.
	 */
	@GameTest(template = "empty", timeoutTicks = 20, batch = GameTestBatches.TIME_STOP)
	public static void aChargeSaysItsLineAfterATapsTicksAndLosesItWithTheCharge(GameTestHelper helper) {
		Holder<SoundEvent> line = ModSoundEvents.JOTARO_STAR_PLATINUM_THE_WORLD;
		Holder<SoundEvent> stopSound = ModSoundEvents.STAR_PLATINUM_TIME_STOP;
		String lineId = line.value().getLocation().toString();
		try (VoiceScene s = new VoiceScene(helper, "CutByRelease", "star_platinum", TRAINED)) {
			s.press();
			s.tick(3);
			helper.assertTrue(s.heard.isEmpty(), "a tap's 3 ticks said " + s.heard);
			s.tick(1);
			helper.assertTrue(s.heard.equals(List.of(lineId + "@voice")), "the charge's 4th tick says its line: " + s.heard);
			s.tick(2);
			s.release();
			helper.assertTrue(s.user.stopped.equals(List.of(lineId + "@voice")) && s.stopTicks() == 0,
					"a release before the minimum cuts the line off and starts nothing: " + s.user.stopped + ", " + s.stopTicks());
		}
		try (VoiceScene s = new VoiceScene(helper, "CutByTap", "star_platinum", TRAINED)) {
			s.press();
			s.tick(3);
			s.release();
			helper.assertTrue(s.heard.isEmpty() && s.user.stopped.isEmpty(), "a tap has no line to cut off: " + s.heard + ", "
					+ s.user.stopped);
			s.press();
			helper.assertTrue(s.stopTicks() > 0 && s.heard.equals(List.of(ModSoundEvents.STAR_PLATINUM_TIME_STOP_BLINK.value()
					.getLocation() + "@ambient")), "a double press says no line, only the blink sound: " + s.heard);
		}
		try (VoiceScene s = new VoiceScene(helper, "CutByDrop", "star_platinum", TRAINED)) {
			EntityActionInstance charge = s.press();
			s.tick(5);
			charge.forceStop();
			s.tick(1);
			helper.assertTrue(s.user.stopped.equals(List.of(lineId + "@voice")),
					"a charge dropped by anything else cuts its line off too: " + s.user.stopped);
		}
		try (VoiceScene s = new VoiceScene(helper, "AtMinimum", "star_platinum", TRAINED)) {
			s.press();
			s.tick(15);
			s.release();
			helper.assertTrue(s.stopTicks() == TRAINED / 2 && s.user.stopped.isEmpty() && s.heard.equals(List.of(lineId + "@voice",
					stopSound.value().getLocation() + "@ambient")), "a release at the minimum keeps its line and, 40 ticks or"
							+ " more, plays the stop sound: " + s.stopTicks() + ", " + s.user.stopped + ", " + s.heard);
		}
		try (VoiceScene s = new VoiceScene(helper, "ShortAtMinimum", "star_platinum", 50)) {
			s.press();
			s.tick(15);
			s.release();
			helper.assertTrue(s.stopTicks() == 25 && s.heard.equals(List.of(lineId + "@voice")),
					"a stop under 40 ticks plays no stop sound, as 1.16's: " + s.stopTicks() + ", " + s.heard);
		}
		try (VoiceScene s = new VoiceScene(helper, "FullCharge", "star_platinum", TRAINED)) {
			s.press();
			s.tick(30);
			helper.assertTrue(s.stopTicks() == TRAINED && s.user.stopped.isEmpty() && s.heard.equals(List.of(lineId + "@voice",
					stopSound.value().getLocation() + "@ambient")), "a full charge fires by itself with its line and stop sound: "
							+ s.stopTicks() + ", " + s.user.stopped + ", " + s.heard);
		}
		// the line of a charge let go early does not keep the next charge's back, as 1.16's voiceLineDelay 0
		try (VoiceScene s = new VoiceScene(helper, "LineAgain", "star_platinum", TRAINED)) {
			s.press();
			s.tick(5);
			s.release();
			s.user.input().timeStopFirstPressTime = EntityActionInputState.NO_PRESS;
			s.press();
			s.tick(5);
			helper.assertTrue(s.heard.equals(List.of(lineId + "@voice", lineId + "@voice")),
					"the second charge said no line of its own: " + s.heard);
		}
		helper.succeed();
	}

	// the same line's other Stand, and the stop sound for a Stand without a blink, whatever the stop's length
	@GameTest(template = "empty", timeoutTicks = 20, batch = GameTestBatches.TIME_STOP)
	public static void aDoublePressPlaysTheStopSoundWhereTheStandHasNoBlink(GameTestHelper helper) {
		try (VoiceScene s = new VoiceScene(helper, "NoBlink", "the_world", TRAINED)) {
			Ability ability = s.power.getMoveset().getAbility(TimeStopCooldowns.TIME_STOP_BLINK);
			helper.assertTrue(ability instanceof TimeStopBlinkAbility, "setup: The World has no blink");
			TimeStopBlinkAbility blink = (TimeStopBlinkAbility) ability;
			String boundTo = blink.getTimeStopAbilityName();
			blink.setTimeStopAbilityName("absent_test_ability");
			try {
				s.press();
				s.tick(2);
				s.release();
				s.press();
			}
			finally {
				blink.setTimeStopAbilityName(boundTo);
			}
			helper.assertTrue(s.stopTicks() > 0 && s.stopTicks() < 40 && s.heard.equals(List.of(ModSoundEvents.THE_WORLD_TIME_STOP
					.value().getLocation() + "@ambient")), "the double press played " + s.heard + " for a stop of " + s.stopTicks()
							+ " ticks");
		}
		helper.succeed();
	}

	// the owner, 2026-11-06: "都固定1.5秒，並且可以由玩家在設定裡更改，最短不能低於0.5秒"
	@GameTest(template = "empty", timeoutTicks = 20, batch = GameTestBatches.TIME_STOP)
	public static void bothStandsChargeForTheConfigsOneAndAHalfSecondsByDefault(GameTestHelper helper) {
		helper.assertTrue(chargeConfig().getDefault() == 1.5D && chargeConfig().get() == 1.5D,
				"the config's charge is not 1.5 s: " + chargeConfig().getDefault() + ", " + chargeConfig().get());
		float starPlatinum = chargeAt(helper, "DefaultSp", "star_platinum", 4, 1.5D);
		float theWorld = chargeAt(helper, "DefaultTw", "the_world", 3, 1.5D);
		helper.assertTrue(starPlatinum == 30.0F && theWorld == 30.0F, "the default charge is not 30 ticks for both: Star Platinum "
				+ starPlatinum + ", The World " + theWorld);
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20, batch = GameTestBatches.TIME_STOP)
	public static void theChargeConfigRefusesWhatIsOutsideItsRange(GameTestHelper helper) {
		ModConfigSpec.DoubleValue value = chargeConfig();
		helper.assertTrue(value.getSpec().test(0.5D) && !value.getSpec().test(0.49D), "the file's minimum is not 0.5 s");
		helper.assertTrue(value.getSpec().test(5.0D) && !value.getSpec().test(5.01D), "the file's maximum is not 5 s");
		helper.assertTrue(Double.valueOf(0.5D).equals(value.getSpec().correct(0.1D)), "a file's 0.1 s is not corrected to 0.5 s: "
				+ value.getSpec().correct(0.1D));
		JojoModConfig.Common client = JojoModConfig.getCommonConfigInstance(true);
		JojoModConfig.Common server = JojoModConfig.getCommonConfigInstance(false);
		double before = value.get();
		RegistryFriendlyByteBuf buf = new RegistryFriendlyByteBuf(Unpooled.buffer(), helper.getLevel().registryAccess());
		try {
			// a packet's number is not trusted either
			client.timeStopChargeSeconds.set(0.1D);
			helper.assertTrue(client.timeStopChargeTicks() == 10, "0.1 s from a server charges for " + client.timeStopChargeTicks()
					+ " ticks, under the 10 of the minimum");
			client.timeStopChargeSeconds.set(Double.NaN);
			helper.assertTrue(client.timeStopChargeTicks() == 30, "NaN from a server charges for " + client.timeStopChargeTicks()
					+ " ticks, not the default's 30");
			// the server's value wins on the client, as the other synced time stop values do
			value.set(0.75D);
			new JojoModConfig.Common.SyncedValues(server).writeToBuf(buf);
			JojoModConfig.applySyncedConfig(new JojoModConfig.Common.SyncedValues(buf));
			helper.assertTrue(client.timeStopChargeSeconds.get() == 0.75D && client.timeStopChargeTicks() == 15,
					"the server's 0.75 s did not reach the client: " + client.timeStopChargeSeconds.get());
		}
		finally {
			value.set(before);
			JojoModConfig.resetSyncedConfig();
			buf.release();
		}
		helper.succeed();
	}

	/** The owner's rule moves with the charge: under half nothing, from half its share, full by itself, the line along. */
	@GameTest(template = "empty", timeoutTicks = 20, batch = GameTestBatches.TIME_STOP)
	public static void aReleaseKeepsItsShareOfAChangedCharge(GameTestHelper helper) {
		String line = ModSoundEvents.JOTARO_STAR_PLATINUM_THE_WORLD.value().getLocation() + "@voice";
		String stopSound = ModSoundEvents.STAR_PLATINUM_TIME_STOP.value().getLocation() + "@ambient";
		for (int[] row : new int[][] { { 10, 5, TRAINED / 2 }, { 60, 30, TRAINED / 2 }, { 60, 45, 68 } }) {
			double seconds = row[0] / 20.0D;
			try (VoiceScene s = new VoiceScene(helper, "Share" + row[0] + "x" + row[1], "star_platinum", TRAINED)) {
				s.pressAt(seconds);
				s.tick(row[1] - (row[1] == 45 ? 0 : 1));
				if (row[1] != 45) {
					s.release();
					helper.assertTrue(s.stopTicks() == 0, "a release a tick under half of " + row[0] + " ticks started a stop of "
							+ s.stopTicks());
					s.user.input().timeStopFirstPressTime = EntityActionInputState.NO_PRESS;
					s.pressAt(seconds);
					s.tick(row[1]);
				}
				s.release();
				helper.assertTrue(s.stopTicks() == row[2], "a release after " + row[1] + " of " + row[0] + " ticks does not start "
						+ row[2] + " ticks: " + s.stopTicks());
			}
		}
		// the shortest charge the config allows: its line starts within the charge and is not cut by the stop it fires
		try (VoiceScene s = new VoiceScene(helper, "ShortestFull", "star_platinum", TRAINED)) {
			s.pressAt(0.5D);
			s.tick(10);
			helper.assertTrue(s.stopTicks() == TRAINED && s.user.stopped.isEmpty() && s.heard.equals(List.of(line, stopSound)),
					"a full charge of 10 ticks did not fire with its line and stop sound: " + s.stopTicks() + ", " + s.user.stopped
							+ ", " + s.heard);
		}
		helper.succeed();
	}

	/** "留下來": The World from Resolve 4 charges for two thirds, never under the minimum. Star Platinum has no such rule. */
	@GameTest(template = "empty", timeoutTicks = 20, batch = GameTestBatches.TIME_STOP)
	public static void theWorldsChargeIsTwoThirdsFromResolveFourAndStarPlatinumsIsNot(GameTestHelper helper) {
		Object[][] rows = {
				{ "the_world", 3, 1.5D, 30.0F }, { "the_world", 4, 1.5D, 20.0F }, { "the_world", 4, 3.0D, 40.0F },
				{ "the_world", 3, 3.0D, 60.0F }, { "the_world", 4, 0.5D, 10.0F }, { "the_world", 4, 0.6D, 10.0F },
				{ "the_world", 4, 1.2D, 16.0F }, { "star_platinum", 4, 1.5D, 30.0F }, { "star_platinum", 4, 3.0D, 60.0F },
				{ "star_platinum", 4, 0.5D, 10.0F } };
		int index = 0;
		for (Object[] row : rows) {
			float charge = chargeAt(helper, "Resolve" + index++, (String) row[0], (int) row[1], (double) row[2]);
			helper.assertTrue(charge == (float) row[3], row[0] + " at Resolve " + row[1] + " and " + row[2] + " s charges for " + charge
					+ " ticks, not " + row[3]);
		}
		helper.succeed();
	}

	private static float chargeAt(GameTestHelper helper, String name, String stand, int resolve, double seconds) {
		try (VoiceScene s = new VoiceScene(helper, name, stand, TRAINED)) {
			s.power.setResolveLevel(resolve);
			EntityActionInstance held = s.pressAt(seconds);
			helper.assertTrue(held != null, name + ": no charge began");
			return held.getCurPhaseLength();
		}
	}

	private static ModConfigSpec.DoubleValue chargeConfig() {
		return JojoModConfig.COMMON_SPEC.getValues().get(List.of("Stand settings", "Time Stop", "timeStopChargeSeconds"));
	}

	private static boolean near(float actual, float expected) {
		return Math.abs(actual - expected) < 0.01F;
	}

	// the power resolves its keys once a tick (Power.updateAvailableMoves)
	private static AbilityConditionCheck resolve(Power<?> power, Ability ability) {
		try {
			Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
			cached.setAccessible(true);
			cached.setBoolean(power, false);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
		}
		return power.updateAvailableMoves().getContextVariationContainer(ability);
	}

	private record CloseOnEnd(Runnable close) implements GameTestListener {
		@Override
		public void testStructureLoaded(GameTestInfo test) {}

		@Override
		public void testPassed(GameTestInfo test, GameTestRunner runner) {
			close.run();
		}

		@Override
		public void testFailed(GameTestInfo test, GameTestRunner runner) {
			close.run();
		}

		@Override
		public void testAddedForRerun(GameTestInfo oldTest, GameTestInfo newTest, GameTestRunner runner) {
			close.run();
		}
	}

	/** A trained user in real game ticks, its stops moved far from every test cell. */
	private static final class Scene {
		private final GameTestHelper helper;
		private final ServerLevel level;
		private final Player user;
		private final StandPower power;
		private final TimeStopState state;
		private final List<Object> listeners = new ArrayList<>();
		private final List<String> heard = new ArrayList<>();
		private final float pointsAtStart;
		@Nullable private TimeStopState.Instance removed;
		private boolean closed;

		private Scene(GameTestHelper helper, String standId, int resolve) {
			this(helper, standId, resolve, true);
		}

		private Scene(GameTestHelper helper, String standId, int resolve, boolean isolate) {
			this.helper = helper;
			level = helper.getLevel();
			state = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			user.setNoGravity(true);
			user.noPhysics = true;
			BlockPos cell = helper.absolutePos(new BlockPos(0, 2, 0));
			user.moveTo(cell.getX() + 0.5, cell.getY(), cell.getZ() + 0.5, 0, 0);
			helper.assertTrue(level.addFreshEntity(user), "setup: could not add the user");
			StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "setup: could not grant " + standId);
			power.setResolveLevel(resolve);
			PowerSkillUnlocks.Result unlocked = TEST_UNLOCKS.forceUnlock(power, TIME_STOP);
			helper.assertTrue(unlocked == PowerSkillUnlocks.Result.UNLOCKED || unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED,
					"setup: the time stop is locked");
			power.getCurTypeData().setAbilityLearningProgressPoints(TIME_STOP, TRAINED - TimeStopLearning.MIN_TIME_STOP_TICKS,
					TimeStopLearning.getMaxTrainingPoints(power), power);
			power.setStamina(power.getMaxStamina());
			pointsAtStart = points();
			helper.assertTrue(TimeStopLearning.getTimeStopTicks(power, TIME_STOP) == TRAINED,
					"setup: not a " + TRAINED + "-tick stop: " + TimeStopLearning.getTimeStopTicks(power, TIME_STOP));
			ChunkPos farAway = new ChunkPos(51100 + user.getId() * 2, 51100);
			if (isolate) {
				Consumer<TimeStopLifecycleEvent.PreStart> isolateStop = event -> {
					if (owns(event)) {
						event.setInstance(event.getInstance().withArea(farAway, 1));
					}
				};
				listeners.add(isolateStop);
				NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, TimeStopLifecycleEvent.PreStart.class, isolateStop);
			}
			Consumer<TimeStopLifecycleEvent.Removed> ended = event -> {
				if (owns(event)) {
					removed = event.getInstance();
				}
			};
			Consumer<PlayLevelSoundEvent.AtPosition> sound = event -> {
				if (!closed && event.getSound() != null && event.getPosition().distanceToSqr(user.position()) < 1.0D) {
					heard.add(event.getSound().value().getLocation().getPath() + "@" + event.getSource().getName());
				}
			};
			listeners.add(ended);
			listeners.add(sound);
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, TimeStopLifecycleEvent.Removed.class, ended);
			NeoForge.EVENT_BUS.addListener(sound);
			helper.testInfo.addListener(new CloseOnEnd(this::close));
		}

		private boolean owns(TimeStopLifecycleEvent event) {
			return !closed && event.getLevel() == level && event.getInstance().userId() == user.getId();
		}

		// a failure in a later tick must not leave the scene's listeners and stop to the next test
		private void guarded(Runnable step) {
			try {
				step.run();
			}
			catch (RuntimeException | Error failure) {
				close();
				throw failure;
			}
		}

		// as ClAbilityInputPacket handles a press
		@Nullable
		private HeldInputEntry press(BufferingState state) {
			Ability base = power.getAbility(TIME_STOP);
			AbilityConditionCheck container = resolve(power, base);
			helper.assertTrue(container != null && AbilityInput.withConditionCheck(container, user, InputMethod.HOLD),
					"the press was refused: " + (container != null ? container.conditionCheck.getWarning() : null)
							+ ", cooldown " + power.getAbilityCooldown(TIME_STOP) + ", at " + level.getGameTime());
			return AbilityInput.keyPress(KEY, container.ability, user, null, InputMethod.HOLD, 0, state, base.getAbilityId());
		}

		@Nullable
		private EntityActionInstance press() {
			HeldInputEntry held = press(BufferingState.clickCanBuffer());
			return held != null && held.action instanceof EntityActionInstance action ? action : null;
		}

		private void release() {
			AbilityInput.keyRelease(KEY, user);
		}

		private LivingComponentAction slot() {
			return LivingComponentAction.getComponent(user, PowerClass.STAND);
		}

		private EntityActionInstance chargeInSlot() {
			EntityActionInstance charge = slot().getAction();
			helper.assertTrue(charge != null && charge.getPhase() == ActionPhase.BUTTON_CHARGE, "no charge in the slot: " + charge);
			return charge;
		}

		private void assertChargedFor(int ticks) {
			EntityActionInstance charge = chargeInSlot();
			helper.assertTrue(charge.getPhaseTick() == ticks, "setup: the charge is at " + charge.getPhaseTick() + " ticks, not "
					+ ticks);
		}

		private EntityActionInputState input() {
			return user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		}

		@Nullable
		private TimeStopState.Instance instance() {
			return state.getInstance(user.getId()).orElse(null);
		}

		private float points() {
			return power.getCurTypeData().getAbilityLearningProgressPoints(TIME_STOP);
		}

		// no summon's sounds: a Stand's own and its user's shout on summoning have no time stop in their names
		private List<String> timeStopSounds() {
			return heard.stream().filter(sound -> sound.contains("time_stop") || sound.contains("the_world")
					|| sound.contains("tomare")).toList();
		}

		private boolean heardOnce(Holder<SoundEvent> sound, SoundSource source) {
			String wanted = sound.value().getLocation().getPath() + "@" + source.getName();
			return heard.stream().filter(wanted::equals).count() == 1;
		}

		private void assertNothingStarted(String what) {
			helper.assertTrue(instance() == null && !user.hasEffect(ModStatusEffects.TIME_STOP) && slot().getAction() == null,
					what + " started a stop or kept its charge: " + instance() + ", " + slot().getAction());
			helper.assertTrue(near(power.getStamina(), power.getMaxStamina()), what + " paid stamina: " + power.getStamina()
					+ " of " + power.getMaxStamina());
			helper.assertTrue(!power.isAbilityOnCooldown(TIME_STOP) && !power.isAbilityOnCooldown(TimeStopCooldowns.TIME_STOP_BLINK),
					what + " set a cooldown");
			helper.assertTrue(near(points(), pointsAtStart), what + " trained the time stop: " + points());
		}

		private void assertNoTimeStopSound(String what) {
			helper.assertTrue(timeStopSounds().isEmpty(), what + " sounded like a stop: " + timeStopSounds());
		}

		private void close() {
			if (closed) {
				return;
			}
			closed = true;
			for (Object listener : listeners) {
				NeoForge.EVENT_BUS.unregister(listener);
			}
			listeners.clear();
			slot().setAction(null, SyncType.NO_SYNC);
			release();
			state.removeInstance(user.getId());
			user.removeEffect(ModStatusEffects.TIME_STOP);
			StandEntity summoned = power.getSummonedStandEntity();
			if (summoned != null) {
				summoned.discard();
			}
			user.discard();
		}
	}

	/** A server player who hears the packets, its Stand out and every action tick by hand, inside one game tick. */
	private static final class VoiceScene implements AutoCloseable {
		private final GameTestHelper helper;
		private final Listener user;
		private final StandPower power;
		private final StandType type;
		private final StandEntity stand;
		private final List<String> heard = new ArrayList<>();
		private final List<Object> listeners = new ArrayList<>();

		private VoiceScene(GameTestHelper helper, String name, String standId, int trained) {
			this.helper = helper;
			user = new Listener(helper.getLevel(), name);
			Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			user.setNoGravity(true);
			user.getAbilities().instabuild = false;
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the user");
			try {
				type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc(standId));
				power = PowerClass.STAND.attachGet(user);
				helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
						== StandPowerTransitions.Status.APPLIED, "setup: could not grant " + standId);
				power.setResolveLevel(4);
				TEST_UNLOCKS.forceUnlock(power, TIME_STOP);
				power.getCurTypeData().setAbilityLearningProgressPoints(TIME_STOP, trained - TimeStopLearning.MIN_TIME_STOP_TICKS,
						TimeStopLearning.getMaxTrainingPoints(power), power);
				power.setStamina(power.getMaxStamina());
				helper.assertTrue(type.summon(user, power), "setup: could not summon " + standId);
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null, "setup: " + standId + " is not out");
				stand.summonLockTicks = 0;
				stand.gradualSummonWeaknessTicks = 0;
			}
			catch (RuntimeException | Error setupFailure) {
				GameTestPlayers.discardAfterFailedSetup(user);
				throw setupFailure;
			}
			ChunkPos farAway = new ChunkPos(51500 + user.getId() * 2, 51500);
			Consumer<TimeStopLifecycleEvent.PreStart> isolate = event -> {
				if (event.getInstance().userId() == user.getId()) {
					event.setInstance(event.getInstance().withArea(farAway, 1));
				}
			};
			Consumer<PlayLevelSoundEvent.AtPosition> sound = event -> {
				if (event.getSound() != null && event.getPosition().distanceToSqr(user.position()) < 1.0D) {
					heard.add(event.getSound().value().getLocation() + "@" + event.getSource().getName());
				}
			};
			listeners.add(isolate);
			listeners.add(sound);
			NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, false, TimeStopLifecycleEvent.PreStart.class, isolate);
			NeoForge.EVENT_BUS.addListener(sound);
		}

		private LivingComponentAction slot() {
			return LivingComponentAction.getComponent(user, PowerClass.STAND);
		}

		private void tick(int ticks) {
			for (int tick = 0; tick < ticks; tick++) {
				stand.tickCount++;
				slot().tick();
			}
		}

		@Nullable
		private EntityActionInstance press() {
			power.setStamina(power.getMaxStamina());
			Ability base = power.getAbility(TIME_STOP);
			AbilityConditionCheck container = resolve(power, base);
			helper.assertTrue(container != null && AbilityInput.withConditionCheck(container, user, InputMethod.HOLD),
					"the press was refused: " + (container != null ? container.conditionCheck : null));
			HeldInputEntry held = AbilityInput.keyPress(KEY, container.ability, user, null, InputMethod.HOLD, 0,
					BufferingState.clickCanBuffer(), base.getAbilityId());
			return held != null && held.action instanceof EntityActionInstance action ? action : null;
		}

		// a press reads the config once: the value is back before anything else can read it
		@Nullable
		private EntityActionInstance pressAt(double seconds) {
			ModConfigSpec.DoubleValue value = chargeConfig();
			double before = value.get();
			value.set(seconds);
			try {
				return press();
			}
			finally {
				value.set(before);
			}
		}

		private void release() {
			AbilityInput.keyRelease(KEY, user);
		}

		private int stopTicks() {
			return helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get()).getInstance(user.getId())
					.map(TimeStopState.Instance::totalTicks).orElse(0);
		}

		@Override
		public void close() {
			for (Object listener : listeners) {
				NeoForge.EVENT_BUS.unregister(listener);
			}
			release();
			slot().setAction(null, SyncType.NO_SYNC);
			helper.getLevel().getData(ModDataAttachmentTypes.TIME_STOP.get()).removeInstance(user.getId());
			user.removeAllEffects();
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	// a player whose client would stop the sounds the server tells it to
	private static final class Listener extends FakePlayer {
		private final List<String> stopped = new ArrayList<>();

		private Listener(ServerLevel level, String name) {
			super(level, new GameProfile(UUID.randomUUID(), name));
			connection = new ListeningConnection(level, this);
		}

		private EntityActionInputState input() {
			return getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get());
		}
	}

	private static final class ListeningConnection extends ServerGamePacketListenerImpl {
		@Nullable private final Listener listener;

		private ListeningConnection(ServerLevel level, Listener listener) {
			super(level.getServer(), openConnection(), listener, CommonListenerCookie.createInitial(listener.getGameProfile(), false));
			this.listener = listener;
		}

		// as GameTestHelper.makeMockServerPlayerInLevel: a live channel, so channel lookups do not fail
		private static Connection openConnection() {
			Connection connection = new Connection(PacketFlow.SERVERBOUND);
			new EmbeddedChannel(connection);
			return connection;
		}

		@Override
		public void send(Packet<?> packet, @Nullable PacketSendListener sendListener) {
			if (listener == null) {
				return;
			}
			if (packet instanceof ClientboundBundlePacket bundle) {
				bundle.subPackets().forEach(sub -> send(sub, null));
			}
			else if (packet instanceof ClientboundStopSoundPacket stop) {
				listener.stopped.add(stop.getName() + "@" + (stop.getSource() != null ? stop.getSource().getName() : null));
			}
		}
	}
}
