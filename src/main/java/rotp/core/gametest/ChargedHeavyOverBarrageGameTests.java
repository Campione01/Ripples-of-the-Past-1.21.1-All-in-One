package rotp.core.gametest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility.StandEntityChargedHeavy;
import rotp.core.init.ModDamageTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.ability.AbilityUsageGroup;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.StandStats;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The owner (2026-11): a fully charged heavy punch of every close-range humanoid Stand hits harder than one second of
 * its barrage. Every registered Stand whose moveset has a punch barrage and the charged heavy is taken at its lowest and
 * highest Resolve level; its real barrage lands one second of punches through its own hit, its real charged heavy is
 * charged, let go and lands, and each hit is read as it reaches the target.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ChargedHeavyOverBarrageGameTests {
	private static final Set<String> CORE_STANDS = Set.of("star_platinum", "the_world", "crazy_diamond", "gold_experience");

	private ChargedHeavyOverBarrageGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void chargedHeavyBeatsOneSecondOfBarrage(GameTestHelper helper) {
		List<ResourceLocation> listed = new ArrayList<>();
		for (var entry : JojoRegistries.DEFAULT_STANDS_REG.entrySet()) {
			if (hasPunchBarrageAndChargedHeavy(entry.getValue())) {
				listed.add(entry.getKey().location());
			}
		}
		Set<String> coreListed = new TreeSet<>();
		listed.stream().filter(id -> id.getNamespace().equals(JojoMod.MOD_ID)).forEach(id -> coreListed.add(id.getPath()));
		helper.assertTrue(coreListed.containsAll(CORE_STANDS), "A core Stand lost its barrage or charged heavy: " + coreListed);
		double multiplier = JojoModConfig.getCommonConfigInstance(false).standDamageMultiplier.get();
		helper.assertTrue(multiplier > 0, "Fixture: the Stand damage multiplier is " + multiplier);

		List<String> failures = new ArrayList<>();
		for (ResourceLocation id : listed) {
			for (boolean top : new boolean[] { false, true }) {
				String failure = verify(helper, id, top, (float) multiplier);
				if (failure != null) {
					failures.add(failure);
				}
			}
		}
		failures.forEach(failure -> JojoMod.getLogger().error("CHARGED-OVER-BARRAGE FAIL " + failure));
		// a game test's message is kept short (1024 characters at most); the log has every case
		helper.assertTrue(failures.isEmpty(), failures.size() + " of " + listed.size() * 2 + " cases failed, first: "
				+ (failures.isEmpty() ? "" : failures.get(0)));
		helper.succeed();
	}

	private static boolean hasPunchBarrageAndChargedHeavy(StandType type) {
		boolean barrage = false;
		boolean charged = false;
		for (Ability ability : type.getBaseMoveset().abilities.values()) {
			barrage |= isPunchBarrage(ability);
			charged |= ability instanceof StandEntityHeavyPunchChargedAbility;
		}
		return barrage && charged;
	}

	private static boolean isPunchBarrage(Ability ability) {
		return ability instanceof StandEntityBarrageAbility && ability.getAbilityUsageCategory() != AbilityUsageGroup.GRAB;
	}

	@Nullable
	private static String verify(GameTestHelper helper, ResourceLocation id, boolean top, float multiplier) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(id);
		Player user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
		Vec3 userPos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
		List<Mob> spawned = new ArrayList<>();
		List<Float> landed = new ArrayList<>();
		Mob[] watched = new Mob[1];
		Consumer<LivingIncomingDamageEvent> listener = event -> {
			if (event.getEntity() == watched[0] && event.getSource().is(ModDamageTypes.STAND_ATTACK)) {
				landed.add(event.getOriginalAmount());
			}
		};
		NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, LivingIncomingDamageEvent.class, listener);
		StandPower power = null;
		String label = id + (top ? " at its highest stats" : " at its lowest stats");
		try {
			user.moveTo(userPos.x, userPos.y, userPos.z, 0.0F, 0.0F);
			user.setYHeadRot(0.0F);
			user.setYBodyRot(0.0F);
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the player of " + label);
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant " + label);
			power.setResolveLevel(top ? power.getMaxResolveLevel() : 0);
			helper.assertTrue(type.summon(user, power), "Could not summon " + label);
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Missing Stand entity of " + label);
			stand.summonLockTicks = 0;
			StandEntity.updateStandStatAttributes(stand, user);
			power.setStamina(power.getMaxStamina());
			stand.updateStrengthMultipliers();

			StandStats stats = type.getStandStats();
			float development = top ? 1 : 0;
			double strength = stand.getAttackDamage();
			double speed = stand.getAttackSpeed();
			double precision = stand.getPrecision();
			String values = " power=" + strength + " speed=" + speed + " precision=" + precision;
			if (!near(strength, stats.power(development)) || !near(speed, stats.speed(development))
					|| !near(precision, stats.precision(development))) {
				return label + ": not at its " + (top ? "highest" : "lowest") + " stats," + values;
			}

			Mob target = spawnHusk(helper, spawned, userPos.add(0.0D, 0.0D, 1.2D));
			watched[0] = target;
			int hitsPerSecond = StandStatFormulas.getBarrageHitsPerSecond(speed);
			float barrageSecond = barrageSecond(helper, power, user, stand, target, hitsPerSecond, landed, label);

			target.setHealth(target.getMaxHealth());
			target.invulnerableTime = 0;
			target.hurtTime = 0;
			target.moveTo(userPos.x, userPos.y, userPos.z + 1.2D, 180.0F, 0.0F);
			target.setDeltaMovement(Vec3.ZERO);
			landed.clear();
			float charged = chargedHeavy(helper, power, user, stand, target, landed, label);

			float expectedBarrage = StandStatFormulas.getBarrageHitDamage(strength, precision) * hitsPerSecond * multiplier;
			float expectedCharged = Math.max(Math.max((float) strength, 1) * 1.5F, expectedBarrage / multiplier * 1.1F) * multiplier;
			JojoMod.getLogger().info("CHARGED-OVER-BARRAGE " + label + values + " hits/s=" + hitsPerSecond
					+ " barrageSecond=" + barrageSecond + " charged=" + charged + " M=" + multiplier);
			if (!nearRelative(barrageSecond, expectedBarrage)) {
				return label + ": a second of barrage landed " + barrageSecond + ", not " + expectedBarrage + values;
			}
			if (!(charged > barrageSecond)) {
				return label + ": the charged heavy landed " + charged + ", not more than a second of barrage "
						+ barrageSecond + values;
			}
			if (!nearRelative(charged, expectedCharged)) {
				return label + ": the charged heavy landed " + charged + ", not " + expectedCharged + values;
			}
			return null;
		}
		finally {
			NeoForge.EVENT_BUS.unregister(listener);
			if (power != null && power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			spawned.forEach(Mob::discard);
			user.discard();
		}
	}

	// one second of punches in one hit of the real barrage, through its own hitEntity (an override runs too)
	private static float barrageSecond(GameTestHelper helper, StandPower power, Player user, StandEntity stand, Mob target,
			int hitsPerSecond, List<Float> landed, String label) {
		Ability ability = power.getMoveset().abilities.values().stream()
				.filter(ChargedHeavyOverBarrageGameTests::isPunchBarrage).findFirst().orElse(null);
		helper.assertTrue(ability != null, "No punch barrage on " + label);
		LivingComponentAction component = LivingComponentAction.getComponent(stand);
		EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
		helper.assertTrue(action instanceof StandEntityBarrage, "The barrage of " + label + " started " + action);
		component.setAction(action, user, SyncType.NO_SYNC);
		StandEntityBarrage barrage = (StandEntityBarrage) action;
		barrage.hitsThisTick = hitsPerSecond;
		try {
			Method hitEntity = StandEntityBarrage.class.getDeclaredMethod("hitEntity", ActionTarget.class, Level.class, StandEntity.class);
			hitEntity.setAccessible(true);
			hitEntity.invoke(barrage, new ActionTarget(target), helper.getLevel(), stand);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not run the barrage hit of " + label, e);
		}
		finally {
			component.setAction(null, user, SyncType.NO_SYNC);
		}
		helper.assertTrue(landed.size() == 1, "The barrage of " + label + " landed " + landed);
		return landed.get(0);
	}

	// charged, let go at once (it charges on and fires), the Stand moved to its offset every tick as StandEntity.tick does
	private static float chargedHeavy(GameTestHelper helper, StandPower power, Player user, StandEntity stand, Mob target,
			List<Float> landed, String label) {
		LivingComponentAction component = LivingComponentAction.getComponent(stand);
		stand.updatePosition(user);
		power.setStamina(power.getMaxStamina());
		stand.updateStrengthMultipliers();
		Ability ability = power.getMoveset().abilities.values().stream()
				.filter(StandEntityHeavyPunchChargedAbility.class::isInstance).findFirst().orElse(null);
		helper.assertTrue(ability != null, "No charged heavy on " + label);
		EntityActionInstance action = ((EntityActionType) ability).initActionOnAbilityUse(helper.getLevel(), user, stand, null);
		helper.assertTrue(action instanceof StandEntityChargedHeavy, "The charged heavy of " + label + " started " + action);
		component.setAction(action, user, SyncType.NO_SYNC);
		action.onKeyRelease(user);
		for (int tick = 0; tick < 80 && component.getAction() == action && landed.isEmpty(); tick++) {
			component.tick();
			stand.updatePosition(user);
		}
		component.setAction(null, user, SyncType.NO_SYNC);
		helper.assertTrue(landed.size() == 1, "The charged heavy of " + label + " landed " + landed
				+ " (phase " + action.getPhase() + ", target " + target.position() + ", Stand " + stand.position() + ")");
		return landed.get(0);
	}

	private static Mob spawnHusk(GameTestHelper helper, List<Mob> spawned, Vec3 position) {
		// a husk does not burn in daylight
		Mob mob = EntityType.HUSK.create(helper.getLevel());
		helper.assertTrue(mob != null, "Could not create a husk");
		mob.moveTo(position.x, position.y, position.z, 180.0F, 0.0F);
		mob.setNoAi(true);
		mob.setNoGravity(true);
		mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000.0D);
		mob.setHealth(1000.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(mob), "Could not add a husk");
		spawned.add(mob);
		return mob;
	}

	private static boolean near(double a, double b) {
		return Math.abs(a - b) < 1.0E-4;
	}

	private static boolean nearRelative(float a, float b) {
		return Math.abs(a - b) <= Math.max(Math.abs(b) * 1.0E-4F, 1.0E-4F);
	}
}
