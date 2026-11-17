package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import rotp.core.api.power.PowerSkillUnlocks;
import rotp.core.api.stand.StandAbilityFatigue;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.api.timestop.TimeStopLifecycleEvent;
import rotp.core.config.client.PlayerClientBroadcastedSettings;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.theworld.TimeStopAbility;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.timestop.TimeStopLearning;
import rotp.core.subsystems.timestop.TimeStopState;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The no-cooldown mode's fatigue on the core's time stop (StandAbilityFatigue): its user keeps his reduced regeneration
 * through his own stop, where the normal mode's per-tick charge takes it back as 1.16 did; the stop's fatigue comes at
 * its end, the cooldown the normal mode sets then, and a stop begun while it runs pays its repeat at its start, half for
 * the owner's emergency stop (a double press). Every stop is moved far from the test cells.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandAbilityFatigueTimeStopGameTests {
	private static final ChunkPos FAR_AWAY = new ChunkPos(50600, 50600);
	private static final float COST_TICK = 9.0F;
	private static final float HALF_BAR = 500.0F;
	private static final float EPS = 0.01F;
	private static final PowerSkillUnlocks.Owner TEST_UNLOCKS =
			PowerSkillUnlocks.register(JojoMod.resLoc("stand_ability_fatigue_gametest"));

	private StandAbilityFatigueTimeStopGameTests() {}

	/*
	 * F6: the stopper's tick charge takes his regeneration back only in the normal mode. Over the one tick after the stop
	 * is put, the bar moves by the regeneration and the stop's charge, whichever of the power's tick and the stop's
	 * lifecycle comes first: what the bar gained plus what is still booked for its next tick.
	 */
	@GameTest(template = "empty", timeoutTicks = 40, batch = GameTestBatches.TIME_STOP)
	public static void theStopperKeepsHisRegenerationInTheNoCooldownMode(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		TimeStopState state = level.getData(ModDataAttachmentTypes.TIME_STOP.get());
		List<FakePlayer> users = new ArrayList<>();
		Runnable cleanup = () -> {
			for (FakePlayer user : users) {
				state.removeInstance(user.getId());
				user.discard();
			}
		};
		StandPower noCooldown;
		StandPower normal;
		try {
			noCooldown = theWorldUser(helper, users, true);
			normal = theWorldUser(helper, users, false);
			for (StandPower power : List.of(noCooldown, normal)) {
				LivingEntity user = power.getUser();
				TimeStopState.Instance instance = new TimeStopState.Instance(user.getId(), 20, 20,
						new ChunkPos(user.blockPosition()), 1, user.getId(), "the_world_time_stop", COST_TICK)
						.withArea(FAR_AWAY, 1);
				helper.assertTrue(state.tryPutInstance(instance), "setup: the stop of " + user.getScoreboardName() + " was refused");
				power.setStamina(HALF_BAR);
				setBooked(power, 0.0F);
			}
		}
		catch (RuntimeException | Error error) {
			cleanup.run();
			throw error;
		}
		helper.runAfterDelay(1, () -> {
			try {
				for (StandPower power : List.of(noCooldown, normal)) {
					helper.assertTrue(state.getInstance(power.getUser().getId()).filter(TimeStopState.Instance::isActive).isPresent(),
							"setup: the stop of " + power.getUser().getScoreboardName() + " ended on its first tick");
				}
				float gain = noCooldown.getStaminaTickGain();
				float moved = noCooldown.getStamina() + booked(noCooldown) - HALF_BAR;
				float multiplier = PlayerClientBroadcastedSettings.getTimeStopStaminaCostMultiplier(noCooldown);
				helper.assertTrue(gain > 0.0F && near(moved, gain - COST_TICK * multiplier), String.format(Locale.ROOT,
						"a tick of the stop moved the bar by %.3f in the no-cooldown mode, not the regeneration %.3f less the"
								+ " charge %.3f: the regeneration must stay", moved, gain, COST_TICK * multiplier));
				float normalMoved = normal.getStamina() + booked(normal) - HALF_BAR;
				helper.assertTrue(near(normalMoved, -COST_TICK), String.format(Locale.ROOT,
						"a tick of the stop moved the bar by %.3f in the normal mode, not -%.3f: the charge takes the"
								+ " regeneration back", normalMoved, COST_TICK));
			}
			finally {
				cleanup.run();
			}
			helper.succeed();
		});
	}

	/*
	 * The time stop in the no-cooldown mode since the owner's decision of 2026-11-11: a stop costs its normal price (no
	 * x3); its end adds the normal cooldown, 3 x the ticks it ran, to its fatigue; and the owner's emergency stop, the
	 * double press, stays gentle when it repeats inside that fatigue: its first three repeats pay 1/64, 1/32 and 1/16 of
	 * the skipped cooldown at 7.5 a tick (a practical use), and add no fatigue of their own.
	 */
	@GameTest(template = "empty", timeoutTicks = 200, batch = GameTestBatches.TIME_STOP)
	public static void aStopCostsItsNormalPriceAndItsEmergencyRepeatsStayGentle(GameTestHelper helper) {
		Scene scene = new Scene(helper);
		try {
			scene.setUp();
			float before = scene.power.getStamina();
			scene.holdToFire();
			TimeStopState.Instance started = scene.instance();
			helper.assertTrue(started != null && started.totalTicks() == 100,
					"setup: a full charge did not start the trained 100-tick stop: " + started);
			float paid = before - scene.power.getStamina();
			float normalStart = scene.power.getStaminaCostShare(
					TimeStopLearning.getTimeStopStaminaCost(scene.power, TimeStopLearning.TIME_STOP, 100));
			helper.assertTrue(near(paid, normalStart), "a full stop's start in the no-cooldown mode cost " + paid
					+ ", not its normal " + normalStart + " (the owner dropped the x3 on 2026-11-11)");
			helper.assertTrue(StandAbilityFatigue.getFatigueTicks(scene.power, TimeStopLearning.TIME_STOP) == 0.0F,
					"a stop's start added fatigue: " + StandAbilityFatigue.getFatigueTicks(scene.power, TimeStopLearning.TIME_STOP));
			helper.runAfterDelay(TimeStopAbility.TIME_STOP_OPENING_SETTLE_TICKS + 12, () -> {
				try {
					scene.resumeAtOnce();
					emergencyRepeat(helper, scene, 1, new ArrayList<>());
				}
				catch (RuntimeException | Error error) {
					scene.close();
					throw error;
				}
			});
		}
		catch (RuntimeException | Error error) {
			scene.close();
			throw error;
		}
	}

	// one double press past the window in which a press after a resume is no double press, its stop resumed at once
	private static void emergencyRepeat(GameTestHelper helper, Scene scene, int repeat, List<Float> extras) {
		helper.runAfterDelay(TimeStopAbility.DOUBLE_PRESS_WINDOW_TICKS + 2, () -> {
			try {
				helper.assertTrue(scene.removed.size() == repeat, "setup: the stops did not end: " + scene.removed);
				int ran = Math.max(scene.removed.get(0).ticksPassed(), 0);
				int cooldown = 3 * ran;
				if (repeat == 1) {
					helper.assertTrue(ran > 0 && near(scene.fatigueAtRemoval, cooldown) && scene.cooldownAtRemoval == cooldown,
							"a stop that ran " + ran + " ticks left fatigue " + scene.fatigueAtRemoval + " (cooldown "
									+ scene.cooldownAtRemoval + "), not the normal cooldown 3 x " + ran);
				}
				float left = StandAbilityFatigue.getFatigueTicks(scene.power, TimeStopLearning.TIME_STOP);
				float startCost = PlayerClientBroadcastedSettings.getTimeStopStaminaCostMultiplier(scene.power)
						* TimeStopLearning.getTimeStopStaminaCost(scene.power, TimeStopLearning.TIME_STOP, 4);
				scene.power.setStamina(scene.power.getMaxStamina());
				scene.doublePress();
				TimeStopState.Instance emergency = scene.instance();
				helper.assertTrue(left > 0.0F && emergency != null && emergency.totalTicks() == 4,
						"setup: no 4-tick emergency stop while the fatigue " + left + " ran: " + emergency);
				float extra = scene.power.getMaxStamina() - scene.power.getStamina() - scene.power.getStaminaCostShare(startCost);
				float share = (float) Math.pow(2.0D, repeat - StandAbilityFatigue.FULL_COST_REPEAT_PRACTICAL);
				float full = StandAbilityFatigue.STAMINA_PER_FATIGUE_TICK * Math.min(left, cooldown);
				float expected = scene.power.getStaminaCostShare(full * share);
				helper.assertTrue(near(extra, expected) && StandAbilityFatigue.getRepeats(scene.power, TimeStopLearning.TIME_STOP) == repeat
						&& near(StandAbilityFatigue.getFatigueTicks(scene.power, TimeStopLearning.TIME_STOP), left),
						"emergency repeat " + repeat + " paid " + extra + " over its start, not " + share + " of 7.5 x "
								+ Math.min(left, cooldown) + " = " + expected + ", or changed the fatigue " + left);
				extras.add(extra);
				scene.resumeAtOnce();
				if (repeat < 3) {
					emergencyRepeat(helper, scene, repeat + 1, extras);
					return;
				}
				JojoMod.getLogger().info("Fatigue emergency stops: extra over the start {} after a stop that ran {} ticks",
						extras, ran);
			}
			catch (RuntimeException | Error error) {
				scene.close();
				throw error;
			}
			scene.close();
			helper.succeed();
		});
	}

	private static StandPower theWorldUser(GameTestHelper helper, List<FakePlayer> users, boolean noCooldown) {
		FakePlayer user = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "FatigueStopper"));
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(user), "setup: could not add the user");
		users.add(user);
		user.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).noStandAbilityCooldown = noCooldown;
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
		StandPower power = PowerClass.STAND.attachGet(user);
		helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED, "setup: could not give The World");
		PowerSkillUnlocks.Result unlocked = TEST_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
		// The World's time stop asks Resolve level 2
		power.setResolveLevel(3);
		Ability timeStop = power.getMoveset().getAbility(TimeStopLearning.TIME_STOP);
		helper.assertTrue((unlocked == PowerSkillUnlocks.Result.UNLOCKED || unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED)
				&& timeStop != null && timeStop.isAbilityUnlocked(power), "setup: the time stop is not unlocked");
		power.setStamina(power.getMaxStamina());
		helper.assertTrue(!power.isStaminaInfinite() && StandAbilityFatigue.applies(power) == noCooldown,
				"setup: not a survival The World user with the no-cooldown setting " + noCooldown);
		return power;
	}

	private static boolean near(float actual, float expected) {
		return Math.abs(actual - expected) < EPS;
	}

	private static float booked(StandPower power) {
		try {
			return bookedField().getFloat(power);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static void setBooked(StandPower power, float value) {
		try {
			bookedField().setFloat(power, value);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException(e);
		}
	}

	private static Field bookedField() throws NoSuchFieldException {
		Field field = StandPower.class.getDeclaredField("staminaAddNextTick");
		field.setAccessible(true);
		return field;
	}

	// TimeStopEarlyEndRefundGameTests' user of The World, in the no-cooldown mode, its stops moved far away
	private static final class Scene {
		private final GameTestHelper helper;
		private final ServerLevel level;
		private final List<TimeStopState.Instance> removed = new ArrayList<>();
		private final List<Object> listeners = new ArrayList<>();
		private float fatigueAtRemoval = Float.NaN;
		private int cooldownAtRemoval = -1;
		private Player player;
		private StandPower power;
		private TimeStopAbility timeStop;
		private LivingComponentAction component;
		private boolean closed;

		private Scene(GameTestHelper helper) {
			this.helper = helper;
			this.level = helper.getLevel();
		}

		private void setUp() {
			Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			player = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			player.setNoGravity(true);
			player.moveTo(pos.x, pos.y, pos.z, 0, 0);
			helper.assertTrue(level.addFreshEntity(player), "setup: could not add the user");
			player.getData(ModDataAttachmentTypes.PLAYER_BROADCASTED_SETTINGS.get()).noStandAbilityCooldown = true;
			StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("the_world"));
			power = PowerClass.STAND.attachGet(player);
			helper.assertTrue(standType != null && StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "setup: could not grant The World");
			PowerSkillUnlocks.Result unlocked = TEST_UNLOCKS.forceUnlock(power, TimeStopLearning.TIME_STOP);
			helper.assertTrue(unlocked == PowerSkillUnlocks.Result.UNLOCKED
					|| unlocked == PowerSkillUnlocks.Result.ALREADY_UNLOCKED, "setup: time stop not unlocked");
			// Resolve level 3 starts The World's time stop without the Stand body; 4 would shorten the charge to 20
			power.setResolveLevel(3);
			power.getCurTypeData().setAbilityLearningProgressPoints(TimeStopLearning.TIME_STOP, 95,
					TimeStopLearning.getMaxTrainingPoints(power), power);
			power.setStamina(power.getMaxStamina());
			Ability ability = power.getMoveset().getAbility(TimeStopLearning.TIME_STOP);
			helper.assertTrue(ability instanceof TimeStopAbility && !power.isStaminaInfinite()
					&& StandAbilityFatigue.applies(power) && TimeStopLearning.getTimeStopTicks(power) == 100,
					"setup: not a survival The World user in the no-cooldown mode with a 100-tick stop");
			timeStop = (TimeStopAbility) ability;
			component = LivingComponentAction.getComponent(player, PowerClass.STAND);

			Consumer<TimeStopLifecycleEvent.PreStart> isolate = event -> {
				if (!closed && event.getInstance().userId() == player.getId()) {
					event.setInstance(event.getInstance().withArea(FAR_AWAY, 1));
				}
			};
			Consumer<TimeStopLifecycleEvent.Removed> onRemoved = event -> {
				if (!closed && event.getLevel() == level && event.getInstance().userId() == player.getId()) {
					removed.add(event.getInstance());
					fatigueAtRemoval = StandAbilityFatigue.getFatigueTicks(power, TimeStopLearning.TIME_STOP);
					cooldownAtRemoval = power.getAbilityFatigue().getCooldown(TimeStopLearning.TIME_STOP);
				}
			};
			listeners.add(isolate);
			NeoForge.EVENT_BUS.addListener(TimeStopLifecycleEvent.PreStart.class, isolate);
			listeners.add(onRemoved);
			NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, false, TimeStopLifecycleEvent.Removed.class, onRemoved);
		}

		/** The held charge as the action component runs it: BUTTON_CHARGE to its end, then PERFORM. */
		private void holdToFire() {
			EntityActionInstance action = timeStop.initActionOnAbilityUse(level, player, player, null);
			component.setAction(action, player, SyncType.NO_SYNC);
			helper.assertTrue(component.getAction() == action && action.getPhase() == ActionPhase.BUTTON_CHARGE,
					"setup: The World's time stop charge did not start: " + action.getPhase());
			int ticks = 0;
			while (ticks < 60 && component.getAction() == action && !action.isOver()) {
				component.tick();
				ticks++;
			}
		}

		// TimeStopAbility.onKeyPress: a press let go, then a second one at once
		private void doublePress() {
			BufferingState first = BufferingState.clickOnly();
			timeStop.onKeyPress(level, player, null, InputMethod.HOLD, 0.0F, first);
			EntityActionInstance charge = component.getAction();
			helper.assertTrue(first.isActionSuccess && charge != null && charge.getPhase() == ActionPhase.BUTTON_CHARGE,
					"setup: the first press did not begin a charge: " + charge);
			charge.onButtonStopHold();
			BufferingState second = BufferingState.clickOnly();
			timeStop.onKeyPress(level, player, null, InputMethod.HOLD, 0.0F, second);
			helper.assertTrue(second.isActionSuccess, "setup: the second press was refused");
		}

		private void resumeAtOnce() {
			for (int press = 0; press < 2 && instance() != null && instance().ticksLeft() > 0; press++) {
				BufferingState resume = BufferingState.clickOnly();
				timeStop.onKeyPress(level, player, null, InputMethod.HOLD, 0.0F, resume);
				helper.assertTrue(resume.isActionSuccess, "setup: the time stop key did not resume the stop");
			}
			TimeStopState.Instance stopping = instance();
			helper.assertTrue(stopping != null && stopping.ticksLeft() == 0, "setup: two presses did not resume at once: " + stopping);
		}

		private TimeStopState.Instance instance() {
			return level.getData(ModDataAttachmentTypes.TIME_STOP.get()).getInstance(player.getId()).orElse(null);
		}

		private void close() {
			if (closed) return;
			try {
				if (player != null) {
					level.getData(ModDataAttachmentTypes.TIME_STOP.get()).removeInstance(player.getId());
				}
			}
			finally {
				closed = true;
				for (Object listener : listeners) NeoForge.EVENT_BUS.unregister(listener);
				listeners.clear();
				if (component != null) component.setAction(null, SyncType.NO_SYNC);
				if (player != null) {
					player.removeEffect(ModStatusEffects.TIME_STOP);
					player.discard();
				}
			}
		}
	}
}
