package rotp.core.gametest;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import rotp.core.api.damage.DefencePiercing;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.init.ModDamageTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.ActionPhase;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.entityaction.LivingComponentAction;
import rotp.core.powersystem.entityaction.netcode.SyncType;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandStatFormulas;
import rotp.core.powersystem.standpower.entity.StandUserGuard;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.util.functions.DamageUtil;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * DefencePiercing's damage type tags, one test per kind of the core's defence: a guarding Stand and its user take the
 * whole hit from a type in jojo_ripples:pierces_guard and the usual cut one otherwise; a Stand body is hurt by a type in
 * pierces_invulnerability that is not a Stand's; a Stand's durability resistance and its user's Resistance leave a type
 * in pierces_damage_caps whole. The test types are put in the tags and taken out again inside one call, so no other
 * test sees them. The user stands facing south (+Z), its Stand half a block in front.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DefencePiercingGameTests {
	private static final float EPS = 1.0E-3F;

	private DefencePiercingGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void aGuardingStandAndItsUserTakeTheWholeHitOfATypeThatPiercesGuard(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			float durability = (float) f.stand.getDurability();
			float raw = durability / 2 + 3;
			DamageSource zombieHit = helper.getLevel().damageSources().mobAttack(f.zombieAt(0, 3));
			float amount = f.scaled(zombieHit, raw);
			float unguarded = f.hitUser(zombieHit, raw);
			f.resetUser();
			f.holdGuard();
			float cut = f.hitUser(zombieHit, raw);
			f.resetUser();
			float whole = f.tagged(() -> f.hitUser(zombieHit, raw), DamageTypes.MOB_ATTACK, DefencePiercing.GUARD);
			helper.assertTrue(close(cut, StandUserGuard.userHitAfterGuard(amount, durability)) && close(whole, unguarded)
					&& unguarded > cut + 1, "A zombie's hit on a guarded user took " + cut + " ("
							+ StandUserGuard.userHitAfterGuard(amount, durability) + " expected) untagged and " + whole + " ("
							+ unguarded + ", the unguarded hit, expected) piercing the guard");

			f.holdGuard();
			float standCut = f.hitStandFrom(0, 2, 6);
			f.holdGuard();
			float standUnguarded = f.tagged(() -> f.hitStandFrom(0, 2, 6), ModDamageTypes.STAND_ATTACK, DefencePiercing.GUARD);
			f.holdGuard();
			float standWhole = f.tagged(() -> f.hitStandFrom(0, 2, 6), ModDamageTypes.STAND_ATTACK, DefencePiercing.GUARD,
					DefencePiercing.DAMAGE_CAPS);
			helper.assertTrue(close(standCut, f.standBlocked(6, 1)) && close(standUnguarded, f.standBlocked(6, 0))
					&& close(standWhole, 6), "A Stand's hit on the guarding Stand passed " + standCut + " (" + f.standBlocked(6, 1)
							+ " expected) untagged, " + standUnguarded + " (" + f.standBlocked(6, 0) + ") piercing the guard, "
							+ standWhole + " (6) piercing the guard and the caps");
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void aStandBodyIsHurtByATypeThatPiercesInvulnerability(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			DamageSource zombieHit = helper.getLevel().damageSources().mobAttack(f.zombieAt(0, 3));
			boolean shielded = f.stand.isInvulnerableTo(zombieHit);
			boolean pierced = f.tagged(() -> !f.stand.isInvulnerableTo(zombieHit) && f.hitStand(zombieHit, 4) > 0,
					DamageTypes.MOB_ATTACK, DefencePiercing.INVULNERABILITY);
			helper.assertTrue(shielded && pierced, "A Stand body was " + (shielded ? "" : "not ") + "invulnerable to a zombie's hit"
					+ " and " + (pierced ? "" : "not ") + "hurt by one piercing invulnerability");
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 80)
	public static void aStandsResistanceAndItsUsersLeaveATypeThatPiercesDamageCapsWhole(GameTestHelper helper) {
		Fixture f = new Fixture(helper);
		try {
			float resisted = f.hitStandFrom(0, -2, 6);
			float whole = f.tagged(() -> f.hitStandFrom(0, -2, 6), ModDamageTypes.STAND_ATTACK, DefencePiercing.DAMAGE_CAPS);
			helper.assertTrue(close(resisted, f.standBlocked(6, 0)) && resisted < 6 - EPS && close(whole, 6),
					"An idle Stand passed " + resisted + " (" + f.standBlocked(6, 0) + " expected) untagged and " + whole
							+ " (6 expected) piercing the caps");

			f.user.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 1));
			float userResisted = f.hitStandFrom(0, -2, 6);
			float userWhole = f.tagged(() -> f.hitStandFrom(0, -2, 6), ModDamageTypes.STAND_ATTACK, DefencePiercing.DAMAGE_CAPS);
			helper.assertTrue(userResisted < f.standBlocked(6, 0) - EPS && close(userWhole, 6),
					"With its user under Resistance II an idle Stand passed " + userResisted + " untagged and " + userWhole
							+ " (6 expected) piercing the caps");
			f.user.removeEffect(MobEffects.DAMAGE_RESISTANCE);

			DamageSource zombieHit = helper.getLevel().damageSources().mobAttack(f.zombieAt(0, -3));
			float amount = f.scaled(zombieHit, 4);
			f.power.resolveCounter.setResolveValue(f.power, Float.MAX_VALUE);
			float resolveCut = f.hitUser(zombieHit, 4);
			f.resetUser();
			float resolveWhole = f.tagged(() -> {
				f.power.resolveCounter.setResolveValue(f.power, Float.MAX_VALUE);
				return f.hitUser(zombieHit, 4);
			}, DamageTypes.MOB_ATTACK, DefencePiercing.DAMAGE_CAPS);
			helper.assertTrue(resolveCut < amount - 1 && close(resolveWhole, amount), "With full Resolve its user took "
					+ resolveCut + " of a zombie's " + amount + " untagged and " + resolveWhole + " piercing the caps");
		}
		finally {
			f.close();
		}
		helper.succeed();
	}

	private static boolean close(float a, float b) {
		return Math.abs(a - b) < EPS;
	}

	private static final class Fixture {
		final GameTestHelper helper;
		final Player user;
		final StandType type;
		final StandPower power;
		final StandEntity stand;
		final Vec3 pos;
		final List<Zombie> zombies = new ArrayList<>();

		Fixture(GameTestHelper helper) {
			this.helper = helper;
			// the factory FakePlayer is invulnerable, and so would its Stand be
			user = GameTestPlayers.makeServerMockPlayer(helper, GameType.SURVIVAL);
			pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(2, 2, 2)));
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			user.setYHeadRot(0);
			user.getAbilities().invulnerable = false;
			helper.assertTrue(helper.getLevel().addFreshEntity(user), "Could not add the Stand user");
			type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
			helper.assertTrue(type != null, "Missing registered Star Platinum");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "The summoned Stand is missing");
			power.setStamina(power.getMaxStamina());
			LivingComponentAction.getComponent(stand).setAction(null, user, SyncType.NO_SYNC);
			face();
			helper.assertTrue(stand.getCurStandAction() == null && !stand.isManuallyControlled()
					&& stand.isFollowingUser() && stand.getArmorValue() == 0, "The Stand is not idle by its user");
			resetUser();
		}

		// half a block in front of the user, facing south as the user does
		void face() {
			stand.moveTo(pos.x, pos.y, pos.z + 0.5, 0, 0);
			stand.setYHeadRot(0);
			stand.yRotO = 0;
			stand.yHeadRotO = 0;
		}

		// the Stand's guard, held as a user holds it
		void holdGuard() {
			Ability ability = power.getAbility("guard");
			helper.assertTrue(ability instanceof EntityActionType, "Star Platinum's guard is missing");
			EntityActionType actionType = (EntityActionType) ability;
			EntityActionInstance action = actionType.createActionObj();
			actionType.initActionFromConfig(action, helper.getLevel(), user, stand);
			action.phasesLength.put(ActionPhase.BUTTON_CHARGE, 0F);
			action.phasesLength.put(ActionPhase.WINDUP, 0F);
			action.phasesLength.put(ActionPhase.PERFORM, 200F);
			action.setStartingPhase();
			LivingComponentAction.getComponent(stand).setAction(action, user, SyncType.NO_SYNC);
			power.setStamina(power.getMaxStamina());
			helper.assertTrue(stand.isStandBlocking() && stand.isFollowingUser(), "The held guard is not blocking");
			face();
		}

		Zombie zombieAt(double dx, double dz) {
			Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
			helper.assertTrue(zombie != null, "Could not create the attacker");
			zombie.moveTo(pos.x + dx, pos.y, pos.z + dz, 0, 0);
			zombies.add(zombie);
			return zombie;
		}

		// what Player.hurt passes on after difficulty scaling
		float scaled(DamageSource source, float amount) {
			return source.type().scaling().getScalingFunction().scaleDamage(
					source, user, amount, helper.getLevel().getDifficulty());
		}

		float hitUser(DamageSource source, float amount) {
			float before = user.getHealth();
			user.hurt(source, amount);
			return before - user.getHealth();
		}

		float hitStand(DamageSource source, float amount) {
			resetUser();
			stand.invulnerableTime = 0;
			float before = user.getHealth();
			stand.hurt(source, amount);
			return before - user.getHealth();
		}

		float hitStandFrom(double dx, double dz, float amount) {
			return hitStand(DamageUtil.make(helper.getLevel(), ModDamageTypes.STAND_ATTACK,
					zombieAt(stand.getX() - pos.x + dx, stand.getZ() - pos.z + dz)), amount);
		}

		// StandEntity.standDamageResistance for an unarmored user
		float standBlocked(float amount, float blockedRatio) {
			return amount * (1 - StandStatFormulas.getPhysicalResistance(
					stand.getDurability(), stand.getAttackDamage(), blockedRatio, amount));
		}

		@SafeVarargs
		final <T> T tagged(Supplier<T> hit, ResourceKey<DamageType> damageType, TagKey<DamageType>... tags) {
			Registry<DamageType> types = helper.getLevel().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
			Map<TagKey<DamageType>, List<Holder<DamageType>>> original = new IdentityHashMap<>();
			types.getTags().forEach(pair -> original.put(pair.getFirst(), pair.getSecond().stream().toList()));
			Map<TagKey<DamageType>, List<Holder<DamageType>>> bound = new IdentityHashMap<>(original);
			for (TagKey<DamageType> tag : tags) {
				List<Holder<DamageType>> members = new ArrayList<>(original.getOrDefault(tag, List.of()));
				members.add(types.getHolderOrThrow(damageType));
				bound.put(tag, members);
			}
			try {
				types.bindTags(bound);
				return hit.get();
			}
			finally {
				types.bindTags(original);
				resetUser();
			}
		}

		// the user's Resolve, which grows with each hit, cuts the hits on him
		void resetUser() {
			power.resolveCounter.resetResolveValue(power);
			user.setHealth(user.getMaxHealth());
			user.invulnerableTime = 0;
			user.lastHurt = 0;
			user.setDeltaMovement(Vec3.ZERO);
			user.moveTo(pos.x, pos.y, pos.z, 0, 0);
			user.setYHeadRot(0);
		}

		void close() {
			zombies.forEach(Zombie::discard);
			if (power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}
}
