package rotp.core.gametest;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.hierophant.HierophantPuppetAbility;
import rotp.core.impl.stands.hierophant.HierophantPuppetEffect;
import rotp.core.init.ModDataAttachmentTypes;
import rotp.core.init.power.ModStandAbilities;
import rotp.core.powersystem.Power;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.condition.AvailableAbilities.AbilityConditionCheck;
import rotp.core.powersystem.ability.condition.ConditionCheck;
import rotp.core.powersystem.ability.controls.InputMethod;
import rotp.core.powersystem.ability.input.AbilityInput;
import rotp.core.powersystem.ability.input.ActionInputBuffer.BufferingState;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.entity_possessionv2.LivingComponentPossession;
import rotp.core.subsystems.entity_puppetcontrol.EntityComponentController;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * While Hierophant Green is inside its puppet (HierophantPuppetEffect: the Stand possesses the creature and its user
 * steers it), it cannot fight outside at the same time, as in the manga, where Hierophant Green works a body from inside
 * it (owner, 2026-11-13; 1.16 had no puppet, and the official 1.21.1 port left every ability usable during it). The
 * server refuses its user's other Stand abilities with their message, as the HUD greys them; the puppet's own controls
 * stay: the manual control key, which switches the puppet's control, and the puppet's key, which lets it go.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HierophantPuppetRefusalGameTests {
	private static final String REFUSAL = "jojo.message.action_condition.hierophant_puppet";
	private static final short KEY = 131;

	private HierophantPuppetRefusalGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 40)
	public static void hierophantGreenInsideAPuppetRefusesItsAttackAndOtherAbilitiesUntilItLeaves(GameTestHelper helper) {
		try (Scene s = new Scene(helper)) {
			Mob cow = s.cow();
			helper.assertTrue(s.resolve("string_attack").conditionCheck.isPositive()
					&& s.resolve("emerald_splash").conditionCheck.isPositive(),
					"setup: the string attack or Emerald Splash is unusable before the puppet");
			HierophantPuppetEffect puppet = s.puppet(cow);
			helper.assertTrue(LivingComponentPossession.getEntityPossessedBy(s.stand) == cow
					&& EntityComponentController.getControlTarget(s.user) == cow,
					"setup: Hierophant Green is not inside the cow under its user's control: possessed "
							+ LivingComponentPossession.getEntityPossessedBy(s.stand) + ", controlled "
							+ EntityComponentController.getControlTarget(s.user));

			// inside the puppet: the HUD's check of the slots, then the presses as the server takes them
			String attackSlot = answer(s.resolve("string_attack"));
			String splashSlot = answer(s.resolve("emerald_splash"));
			BufferingState attack = s.press("string_attack", InputMethod.CLICK, true);
			EntityActionInstance afterAttack = s.standAction.getAction();
			BufferingState splash = s.press("emerald_splash", InputMethod.HOLD, true);
			EntityActionInstance afterSplash = s.standAction.getAction();
			helper.assertTrue(REFUSAL.equals(attackSlot) && REFUSAL.equals(splashSlot)
					&& !attack.isActionSuccess && !attack.shouldBuffer && afterAttack == null
					&& !splash.isActionSuccess && !splash.shouldBuffer && afterSplash == null
					&& s.user.keys().equals(List.of(REFUSAL, REFUSAL)),
					"Hierophant Green inside its puppet fights from there or not at all: its string attack and Emerald Splash"
							+ " must be refused with " + REFUSAL + "; the slots answer " + attackSlot + " and " + splashSlot
							+ ", the attack started " + attack.isActionSuccess + " (queued " + attack.shouldBuffer + ", the Stand's"
							+ " action " + afterAttack + "), Emerald Splash started " + splash.isActionSuccess + " (queued "
							+ splash.shouldBuffer + ", the Stand's action " + afterSplash + "), messages " + s.user.keys());

			// the puppet's own controls: the manual control key gives its control back to the user and takes it again
			s.user.clearKeys();
			String manualSlot = answer(s.resolve("manual_control"));
			s.press("manual_control", InputMethod.CLICK, true);
			Entity freed = EntityComponentController.getControlTarget(s.user);
			s.press("manual_control", InputMethod.CLICK, true);
			Entity retaken = EntityComponentController.getControlTarget(s.user);
			String releaseSlot = answer(s.resolve("puppet"));
			helper.assertTrue("usable".equals(manualSlot) && freed != cow && retaken == cow && "usable".equals(releaseSlot)
					&& s.user.keys().isEmpty() && LivingComponentPossession.getEntityPossessedBy(s.stand) == cow,
					"The puppet's own controls stay usable: the manual control key answers " + manualSlot + " and switched the"
							+ " control to " + freed + " and back to " + retaken + ", the release answers " + releaseSlot
							+ ", messages " + s.user.keys());

			// its key lets the puppet go, and Hierophant Green leaves the cow
			s.press("puppet", InputMethod.CLICK, true);
			s.power.userStandEffects.tick();
			helper.assertTrue(puppet.isStopped() && !HierophantPuppetAbility.hasPuppetUnderControl(s.power)
					&& LivingComponentPossession.getEntityPossessedBy(s.stand) == null,
					"setup: the puppet's key did not let it go: stopped " + puppet.isStopped() + ", possessed "
							+ LivingComponentPossession.getEntityPossessedBy(s.stand));

			// out of the puppet, the same two work again
			s.user.clearKeys();
			BufferingState attackAgain = s.press("string_attack", InputMethod.CLICK, true);
			EntityActionInstance attackAction = s.standAction.getAction();
			s.standAction.setAction(null, s.user, SyncType.NO_SYNC);
			s.power.setStamina(s.power.getMaxStamina());
			BufferingState splashAgain = s.press("emerald_splash", InputMethod.HOLD, false);
			EntityActionInstance splashAction = s.standAction.getAction();
			AbilityInput.keyRelease(KEY, s.user);
			helper.assertTrue(attackAgain.isActionSuccess && attackAction != null
					&& attackAction.ability == s.power.getAbility("string_attack")
					&& splashAgain.isActionSuccess && splashAction != null
					&& splashAction.ability == s.power.getAbility("emerald_splash") && s.user.keys().isEmpty(),
					"Out of the puppet the string attack and Emerald Splash work again: the attack started "
							+ attackAgain.isActionSuccess + " (" + attackAction + "), Emerald Splash started " + splashAgain.isActionSuccess
							+ " (" + splashAction + "), messages " + s.user.keys());
			helper.succeed();
		}
	}

	private static String answer(AbilityConditionCheck container) {
		if (container == null) {
			return "not in the moveset";
		}
		ConditionCheck check = container.conditionCheck;
		if (check.isPositive()) {
			return "usable";
		}
		Component warning = check.getWarning();
		if (warning == null) {
			return "refused without a message";
		}
		return warning.getContents() instanceof TranslatableContents contents ? contents.getKey() : warning.getString();
	}

	private static final class MessagePlayer extends FakePlayer {
		private final List<Component> actionBar = new ArrayList<>();

		private MessagePlayer(ServerLevel level, String name) {
			super(level, new GameProfile(UUID.randomUUID(), name));
		}

		@Override
		public void displayClientMessage(Component message, boolean actionBar) {
			if (actionBar) {
				this.actionBar.add(message);
			}
		}

		private List<String> keys() {
			return actionBar.stream()
					.map(message -> message.getContents() instanceof TranslatableContents contents
							? contents.getKey() : message.getString())
					.filter(key -> key.startsWith("jojo.message.action_condition."))
					.toList();
		}

		private void clearKeys() {
			actionBar.clear();
		}
	}

	// a Hierophant Green user with the puppet and Emerald Splash learned, the Stand out, looking south; it does not tick
	private static final class Scene implements AutoCloseable {
		private final GameTestHelper helper;
		private final MessagePlayer user;
		private final Vec3 origin;
		private final List<Entity> spawned = new ArrayList<>();
		private StandType standType;
		private StandPower power;
		private StandEntity stand;
		private LivingComponentAction standAction;

		private Scene(GameTestHelper helper) {
			this.helper = helper;
			user = new MessagePlayer(helper.getLevel(), "PuppetRefusal");
			origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			try {
				standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("hierophant_green"));
				helper.assertTrue(standType != null, "Missing Stand type hierophant_green");
				user.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
				user.setNoGravity(true);
				user.getAbilities().instabuild = false;
				helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
				power = PowerClass.STAND.attachGet(user);
				helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
						== StandPowerTransitions.Status.APPLIED, "Could not grant Hierophant Green");
				power.setResolveLevel(4);
				unlock("puppet");
				unlock("emerald_splash");
				helper.assertTrue(standType.summon(user, power), "Could not summon Hierophant Green");
				stand = power.getSummonedStandEntity();
				helper.assertTrue(stand != null, "Summoned Hierophant Green is missing");
				standAction = LivingComponentAction.getComponent(stand);
				// the summon lock skips the action tick, and StandEntity.tick, which counts it down, does not run here
				stand.summonLockTicks = 0;
				stand.gradualSummonWeaknessTicks = 0;
				power.setStamina(power.getMaxStamina());
			}
			catch (RuntimeException | Error e) {
				close();
				throw e;
			}
		}

		// as a player who can press it has it; the scene does not run the user's tick
		private void unlock(String name) {
			power.getCurTypeData()._lockedAbilities.remove(name);
			helper.assertTrue(power.getAbility(name).isAbilityUnlocked(power), "setup: " + name + " is still locked");
		}

		private Mob cow() {
			Mob cow = EntityType.COW.create(helper.getLevel());
			helper.assertTrue(cow != null, "Could not create a cow");
			cow.setNoAi(true);
			cow.setNoGravity(true);
			cow.moveTo(origin.x, origin.y, origin.z + 3.0D, 180.0F, 0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(cow), "Could not add the cow");
			spawned.add(cow);
			return cow;
		}

		// a puppet of its user's, as the puppeting action leaves one (PuppetingAction.actionPerformEnd)
		private HierophantPuppetEffect puppet(Mob target) {
			HierophantPuppetEffect puppet = ModStandAbilities.EFFECT_HG_PUPPET.get().create(helper.getLevel());
			power.userStandEffects.addEffect(puppet.withTarget(target));
			helper.assertTrue(HierophantPuppetAbility.hasPuppetUnderControl(power), "setup: the user has no puppet");
			return puppet;
		}

		// as ClAbilityInputPacket takes a press: the key's resolved ability, its condition check, then the press
		private BufferingState press(String name, InputMethod inputMethod, boolean release) {
			BufferingState state = BufferingState.clickCanBuffer();
			Ability base = power.getAbility(name);
			helper.assertTrue(base != null, "Missing ability " + name);
			AbilityConditionCheck container = resolve(name);
			if (container != null && AbilityInput.withConditionCheck(container, user, inputMethod)) {
				AbilityInput.keyPress(KEY, container.ability, user, null, inputMethod, 0, state, base.getAbilityId());
			}
			if (release) {
				AbilityInput.keyRelease(KEY, user);
			}
			return state;
		}

		// the power resolves its keys once a tick (Power.updateAvailableMoves), and the test does not tick it
		private AbilityConditionCheck resolve(String name) {
			try {
				Field cached = Power.class.getDeclaredField("cachedMovesThisTick");
				cached.setAccessible(true);
				cached.setBoolean(power, false);
			}
			catch (ReflectiveOperationException e) {
				throw new IllegalStateException("Power.cachedMovesThisTick is not reachable", e);
			}
			return power.updateAvailableMoves().getContextVariationContainer(power.getAbility(name));
		}

		@Override
		public void close() {
			user.getData(ModDataAttachmentTypes.ENTITY_ABILITY_INPUT.get()).heldKeys.clear();
			LivingComponentAction.getComponent(user).entityAim.setTarget(ActionTarget.EMPTY);
			if (power != null) {
				power.userStandEffects.getEffectsOfType(ModStandAbilities.EFFECT_HG_PUPPET.get())
						.forEach(effect -> effect.remove());
				power.userStandEffects.tick();
				if (power.isSummoned() && standType != null) {
					standType.forceUnsummon(user, power);
				}
			}
			spawned.forEach(Entity::discard);
			user.discard();
		}
	}
}
