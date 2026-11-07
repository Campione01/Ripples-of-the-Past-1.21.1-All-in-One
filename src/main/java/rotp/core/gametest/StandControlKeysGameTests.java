package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.UUID;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityUnsummonAction;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInputState;
import rotp.core.powersystem.entityaction.EntityActionInputState.HeldInputEntry;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.entity_externalcontainer._stand.input.StandItemInput;
import rotp.core.subsystems.entity_externalcontainer._stand.input.StandItemInput.Action;
import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** The keys a player binds for a Stand's moves and held items (R772.1, none has a default): what a press does, on the server. */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandControlKeysGameTests {
	private static final short KEY = 172;

	private StandControlKeysGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void theCancelKeyEndsAWindupAndLeavesTheStandOut(GameTestHelper helper) {
		try (Scene s = new Scene(helper)) {
			for (ActionPhase phase : new ActionPhase[] { ActionPhase.BUTTON_CHARGE, ActionPhase.WINDUP }) {
				EntityActionInstance action = s.startProbe(phase);
				s.press();
				helper.assertTrue(s.stand.getCurStandAction() == null, "the cancel key left the action in its " + phase);
				helper.assertTrue(action.isOver(), "the cancelled action is not over");
				helper.assertTrue(s.power.isSummoned() && !s.stand.isRemoved(), "cancelling a " + phase + " took the Stand away");
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void theCancelKeyLeavesAMoveThatHasAlreadyGoneOff(GameTestHelper helper) {
		try (Scene s = new Scene(helper)) {
			for (ActionPhase phase : new ActionPhase[] { ActionPhase.PERFORM, ActionPhase.RECOVERY }) {
				EntityActionInstance action = s.startProbe(phase);
				s.press();
				helper.assertTrue(s.stand.getCurStandAction() == action && !action.isOver(),
						"the cancel key ended an action in its " + phase);
				helper.assertTrue(s.power.isSummoned(), "the cancel key recalled the Stand during a " + phase);
				LivingComponentAction.getComponent(s.stand).setAction(null, SyncType.NO_SYNC);
			}
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void withNoMoveTheCancelKeyRecallsAndASecondPressEndsTheRecallAtOnce(GameTestHelper helper) {
		try (Scene s = new Scene(helper)) {
			s.press();
			helper.assertTrue(s.stand.getCurStandAction() instanceof StandEntityUnsummonAction.StandUnsummonInstance,
					"the first press started no recall: " + s.stand.getCurStandAction());
			helper.assertTrue(!s.stand.isRemoved() && s.power.isSummoned(), "the first press took the Stand away at once");
			s.press();
			helper.assertTrue(s.stand.isRemoved() && !s.power.isSummoned(), "the second press did not end the recall");
			s.press();
			helper.assertTrue(!s.power.isSummoned(), "the cancel key summoned the Stand");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aCancelledWindupLeavesNoHeldKeyAndNoWaitingPress(GameTestHelper helper) {
		try (Scene s = new Scene(helper)) {
			EntityActionInstance action = s.startProbe(ActionPhase.WINDUP);
			EntityActionInputState input = s.user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT);
			input.heldKeys.put(KEY, new HeldInputEntry(KEY, PowerClass.STAND, action));
			input.inputBuffer(PowerClass.STAND).bufferClickInput(s.power.getAbility("punch").getAbilityId());
			helper.assertTrue(AbilityInput.hasHeldInput(s.user, PowerClass.STAND) && buffered(input) != null, "fixture: nothing held or waiting");
			s.press();
			helper.assertTrue(!AbilityInput.hasHeldInput(s.user, PowerClass.STAND), "the key that held the cancelled action still holds it");
			helper.assertTrue(buffered(input) == null, "a press waiting behind the cancelled action would start at once");
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 20)
	public static void theItemKeysAskForEmptyHandsOutsideManualControl(GameTestHelper helper) {
		helper.assertTrue(StandItemInput.ownKeyActs(Action.DROP, false, false, true, true, false),
				"toss with the main hand empty did nothing");
		helper.assertTrue(!StandItemInput.ownKeyActs(Action.DROP, false, false, true, false, true),
				"toss acted with an item in the main hand: bound to the vanilla key it would throw both");
		helper.assertTrue(StandItemInput.ownKeyActs(Action.SWAP_HANDS, false, false, true, true, true),
				"swap with both hands empty did nothing");
		helper.assertTrue(!StandItemInput.ownKeyActs(Action.SWAP_HANDS, false, false, true, true, false),
				"swap acted with an item in the off hand");
		helper.assertTrue(!StandItemInput.ownKeyActs(Action.DROP, false, false, false, true, true),
				"toss acted with the Stand holding nothing");
		helper.assertTrue(StandItemInput.ownKeyActs(Action.DROP, true, false, true, false, false),
				"toss did nothing under manual control with full hands");
		helper.assertTrue(!StandItemInput.ownKeyActs(Action.DROP, true, true, true, true, true),
				"under manual control the vanilla key already tosses: a key bound the same would toss twice");
		helper.succeed();
	}

	private static Object buffered(EntityActionInputState input) {
		try {
			Field field = ActionInputBuffer.class.getDeclaredField("buffered");
			field.setAccessible(true);
			return field.get(input.inputBuffer(PowerClass.STAND));
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("fixture: the input buffer's entry is out of reach", e);
		}
	}

	private static final class Scene implements AutoCloseable {
		final FakePlayer user;
		final StandType type;
		final StandPower power;
		final StandEntity stand;

		Scene(GameTestHelper helper) {
			ServerLevel level = helper.getLevel();
			Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user = new FakePlayer(level, new GameProfile(UUID.randomUUID(), "StandControlKeys"));
			user.setGameMode(GameType.SURVIVAL);
			user.setNoGravity(true);
			user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(user), "fixture: could not add the user");
			power = PowerClass.STAND.attachGet(user);
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && type.summon(user, power), "fixture: could not give and summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "fixture: the summoned Stand is missing");
			stand.summonLockTicks = 0;
		}

		void press() {
			power.getPowerType().onUserCancelCommand(user, power);
		}

		EntityActionInstance startProbe(ActionPhase phase) {
			Ability punch = power.getAbility("punch");
			EntityActionInstance action = new EntityActionInstance((EntityActionType) punch) {};
			for (ActionPhase each : ActionPhase.values()) {
				action.phasesLength.put(each, 100F);
			}
			action.setStartingPhase();
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			action.setPhaseStart(phase);
			return action;
		}

		@Override
		public void close() {
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
