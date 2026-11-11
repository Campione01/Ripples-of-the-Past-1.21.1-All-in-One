package rotp.core.gametest;

import java.util.ArrayList;
import java.util.List;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.entityaction.ActionReplayState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * LivingComponentAction's replay state, for King Crimson's Epitaph: a Stand barrage in progress, taken from one zombie's
 * Star Platinum and put on another's, goes on there from the phase and phase tick it had, for the other zombie, under a
 * generation of its own and with nothing paid; no state clears the slot.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ActionReplayStateGameTests {

	private ActionReplayStateGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void aBarrageInProgressGoesOnWhereItWasOnAnotherStand(GameTestHelper helper) {
		List<Runnable> close = new ArrayList<>();
		try {
			StandPower from = standUser(helper, 1, close);
			StandPower to = standUser(helper, 3, close);
			StandEntity fromStand = from.getSummonedStandEntity();
			StandEntity toStand = to.getSummonedStandEntity();
			AbilityInput.keyPressMob(from.getAbility("barrage"), from.getUser(), null, InputMethod.HOLD);
			EntityActionInstance barrage = LivingComponentAction.getComponent(fromStand).getAction();
			helper.assertTrue(barrage != null && barrage.ability == from.getAbility("barrage"),
					"Setup: the first zombie's Stand did not barrage: " + barrage);
			helper.runAfterDelay(6, () -> {
				try {
					LivingComponentAction fromSlot = LivingComponentAction.getComponent(fromStand);
					LivingComponentAction toSlot = LivingComponentAction.getComponent(toStand);
					ActionReplayState state = fromSlot.captureReplayState();
					float phaseTick = barrage.getPhaseTick();
					helper.assertTrue(state.hasAction() && !barrage.isOver() && phaseTick > 0,
							"Setup: no barrage in progress to take: " + barrage.getPhase() + " at " + phaseTick);
					to.setStamina(to.getMaxStamina() / 2);
					float stamina = to.getStamina();
					EntityActionInstance replayed = toSlot.applyReplayState(state, to.getUser(), SyncType.TRACKING_AND_SELF);
					helper.assertTrue(replayed != null && toSlot.getAction() == replayed && replayed != barrage
							&& replayed.isOfAbility(to.getAbility("barrage")),
							"The other Stand did not take the barrage: " + toSlot.getAction());
					helper.assertTrue(replayed.getPhase() == barrage.getPhase() && replayed.getPhaseTick() == phaseTick,
							"The taken barrage did not go on where it was: " + replayed.getPhase() + " at "
									+ replayed.getPhaseTick() + ", " + barrage.getPhase() + " at " + phaseTick + " expected");
					helper.assertTrue(replayed.getPowerUser() == to.getUser() && replayed.getPerformer() == toStand,
							"The taken barrage is not the other zombie's: user " + replayed.getPowerUser() + ", performer "
									+ replayed.getPerformer());
					helper.assertTrue(toSlot.actionGeneration() > 0 && toSlot.actionGeneration() != fromSlot.actionGeneration()
							&& to.getStamina() == stamina, "The taken barrage has no generation of its own ("
									+ toSlot.actionGeneration() + ", " + fromSlot.actionGeneration() + ") or was paid: "
									+ stamina + " -> " + to.getStamina());
				}
				catch (RuntimeException | Error error) {
					close.forEach(Runnable::run);
					throw error;
				}
			});
			helper.runAfterDelay(9, () -> {
				try {
					EntityActionInstance replayed = LivingComponentAction.getComponent(toStand).getAction();
					helper.assertTrue(replayed != null && !replayed.isOver() && replayed.isOfAbility(to.getAbility("barrage")),
							"The taken barrage did not go on on its own Stand: " + replayed);
					LivingComponentAction toSlot = LivingComponentAction.getComponent(toStand);
					helper.assertTrue(toSlot.applyReplayState(ActionReplayState.NONE, to.getUser(), SyncType.NO_SYNC) == null
							&& toSlot.getAction() == null, "No replay state did not clear the slot: " + toSlot.getAction());
				}
				finally {
					close.forEach(Runnable::run);
				}
				helper.succeed();
			});
		}
		catch (RuntimeException | Error error) {
			close.forEach(Runnable::run);
			throw error;
		}
	}

	private static StandPower standUser(GameTestHelper helper, int x, List<Runnable> close) {
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(zombie != null, "Setup: could not create a zombie");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(x, 2, 1)));
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "Setup: could not add a zombie");
		StandPower power = PowerClass.STAND.attachGet(zombie);
		close.add(() -> {
			zombie.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			if (power.isSummoned() && type != null) {
				type.forceUnsummon(zombie, power);
			}
			zombie.discard();
		});
		helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
				== StandPowerTransitions.Status.APPLIED && type.summon(zombie, power), "Setup: could not summon Star Platinum");
		StandEntity stand = power.getSummonedStandEntity();
		helper.assertTrue(stand != null, "Setup: a zombie's Stand is missing");
		stand.summonLockTicks = 0;
		power.setStamina(power.getMaxStamina());
		return power;
	}
}
