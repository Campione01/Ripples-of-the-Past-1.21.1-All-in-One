package rotp.core.gametest;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.JojoModConfig;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility;
import rotp.core.impl.stands._entitybase.StandEntityBarrageAbility.StandEntityBarrage;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchAbility.StandEntityHeavyPunch;
import rotp.core.impl.stands._entitybase.StandEntityHeavyPunchChargedAbility;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
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

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Only damage follows a Stand's power above 21, the top of the ordinary scale (StandStatFormulas.ORDINARY_POWER_CAP, the
 * owner's numbers of 2026-11-13): the leap, the blasts, the knockbacks, the swing pitch, the speed of what a Stand throws,
 * what its punch may break and the resistance read power at most 21. Star Platinum (35 at its top) and The World (23 at
 * its top) are the Stands above it; every other value of every Stand is the formula as it was before the cap.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StandPowerCapGameTests {
	// pinned here, not read from StandStatFormulas.ORDINARY_POWER_CAP: a change of the cap must move this test
	private static final float TOP = 21F;
	private static final float STAR_PLATINUM_TOP = 35F;
	private static final Set<ResourceLocation> ABOVE_THE_SCALE = Set.of(
			JojoMod.resLoc("star_platinum"), JojoMod.resLoc("the_world"));
	private static final float EPSILON = 1.0E-5F;
	private static final float[] FINISHER_METERS = { 0F, 0.5F, 1F };
	private static final float[] BLOCKED_RATIOS = { 0F, 0.5F };
	private static final int SAMPLES = 64;

	private StandPowerCapGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void everyStandsPowerReadsStayTheUncappedFormulasExceptStarPlatinumAndTheWorldAboveTwentyOne(GameTestHelper helper) {
		List<String> failures = new ArrayList<>();
		int standsAbove = 0;
		int checked = 0;
		boolean coreSeen = false;
		for (var entry : JojoRegistries.DEFAULT_STANDS_REG.entrySet()) {
			ResourceLocation id = entry.getKey().location();
			StandStats stats = entry.getValue().getStandStats();
			if (stats == null) {
				continue;
			}
			coreSeen |= id.equals(JojoMod.resLoc("star_platinum"));
			for (float ratio : new float[] { 0F, 1F }) {
				double power = stats.power(ratio);
				double durability = stats.durability(ratio);
				double speed = stats.speed(ratio);
				if (power > TOP) {
					standsAbove++;
					if (!ABOVE_THE_SCALE.contains(id)) {
						failures.add(id + " has power " + power + ", above " + TOP
								+ ": only Star Platinum and The World are meant to be");
					}
					continue;
				}
				checked++;
				compare(failures, id + " at " + (ratio == 0 ? "lowest" : "highest") + " power " + power, power, durability, speed);
			}
		}
		failures.forEach(failure -> JojoMod.getLogger().error("POWER-CAP FAIL " + failure));
		helper.assertTrue(coreSeen && checked >= 8 && standsAbove >= ABOVE_THE_SCALE.size(),
				"Fixture: " + checked + " stat sets checked, " + standsAbove + " above " + TOP + ", Star Platinum registered: " + coreSeen);
		helper.assertTrue(failures.isEmpty(), failures.size() + " of " + checked + " stat sets differ, first: "
				+ (failures.isEmpty() ? "" : failures.get(0)));
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 100)
	public static void starPlatinumAtItsTopPowerReadsTwentyOneEverywhereButDamage(GameTestHelper helper) {
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		helper.assertTrue(type != null, "Missing Star Platinum");
		ServerLevel level = helper.getLevel();
		Vec3 origin = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		Player user = FakePlayerFactory.get(level, new GameProfile(
				UUID.nameUUIDFromBytes("PowerCapSp".getBytes(StandardCharsets.US_ASCII)), "PowerCapSp"));
		StandPower power = null;
		try {
			user.moveTo(origin.x, origin.y, origin.z);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the user");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Star Platinum");
			power.setResolveLevel(power.getMaxResolveLevel());
			helper.assertTrue(type.summon(user, power), "Could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Missing the Star Platinum entity");
			StandEntity.updateStandStatAttributes(stand, user);
			power.setStamina(power.getMaxStamina());
			stand.updateStrengthMultipliers();
			double strength = stand.getAttackDamage();
			helper.assertTrue(strength == STAR_PLATINUM_TOP, "Fixture: the Stand's power is " + strength + ", not " + STAR_PLATINUM_TOP);

			near(helper, "leap", stand.getLeapStrength(), TOP / 5F);
			near(helper, "heavy punch blast radius", StandEntityHeavyPunchAbility.calcExplosionRadius(stand), Math.min(TOP * 0.175F, 10));
			near(helper, "charged heavy blast radius", StandEntityHeavyPunchChargedAbility.calcExplosionRadius(stand), Math.min(TOP * 0.25F, 10));
			helper.assertTrue(StandEntityHeavyPunchAbility.calcExplosionRadius(stand) != Math.min(STAR_PLATINUM_TOP * 0.175F, 10)
					&& StandEntityHeavyPunchChargedAbility.calcExplosionRadius(stand) != Math.min(STAR_PLATINUM_TOP * 0.25F, 10),
					"Fixture: the blast radii are those of power " + STAR_PLATINUM_TOP);

			Pig pig = helper.spawn(EntityType.PIG, new BlockPos(1, 2, 3));
			near(helper, "barrage knockback factor", StandEntityBarrageAbility.getBarrageKnockbackMultiplier(stand, pig), TOP * 0.0075F);

			Ability heavy = power.getAbility("heavy_punch");
			helper.assertTrue(heavy instanceof EntityActionType, "Star Platinum's heavy_punch: " + heavy);
			EntityActionInstance heavyAction = ((EntityActionType) heavy).initActionOnAbilityUse(level, user, stand, null);
			helper.assertTrue(heavyAction instanceof StandEntityHeavyPunch, "heavy_punch action: " + heavyAction);
			StandEntityHeavyPunch heavyPunch = (StandEntityHeavyPunch) heavyAction;
			for (float meter : FINISHER_METERS) {
				heavyPunch.finisherValue = meter;
				near(helper, "heavy knockback at finisher " + meter, heavyPunchKnockback(heavyPunch, stand),
						0.5F + TOP / (8 - meter * 4));
			}
			helper.assertTrue(StandStatFormulas.getHeavyAttackKnockback(strength, 1F) == 0.5F + TOP / 4F,
					"The knockback formula at finisher 1 reads " + StandStatFormulas.getHeavyAttackKnockback(strength, 1F));

			Ability barrage = power.getAbility("barrage");
			helper.assertTrue(barrage instanceof EntityActionType, "Star Platinum's barrage: " + barrage);
			EntityActionInstance barrageAction = ((EntityActionType) barrage).initActionOnAbilityUse(level, user, stand, null);
			LivingComponentAction.getComponent(stand).setAction(barrageAction, user, SyncType.NO_SYNC);
			helper.assertTrue(barrageAction instanceof StandEntityBarrage, "barrage action: " + barrageAction);
			float lowest = Float.MAX_VALUE;
			float highest = -Float.MAX_VALUE;
			for (int i = 0; i < SAMPLES; i++) {
				float pitch = swingPitch((StandEntityBarrage) barrageAction, stand);
				lowest = Math.min(lowest, pitch);
				highest = Math.max(highest, pitch);
			}
			float base = 1.8F - TOP * 0.05F;
			helper.assertTrue(lowest >= base - EPSILON && highest < base + 0.2F + EPSILON,
					"The barrage swing pitch runs " + lowest + " to " + highest + " at power " + STAR_PLATINUM_TOP + ", not " + base
							+ " up by 0.2");

			StandStats stats = type.getStandStats();
			float lightAtTop = StandStatFormulas.getLightAttackDamage(strength);
			float heavyAtTop = StandStatFormulas.getHeavyAttackDamage(strength);
			float barrageHitAtTop = StandStatFormulas.getBarrageHitDamage(strength, stand.getPrecision());
			float chargedAtTop = StandStatFormulas.getChargedHeavyAttackDamage(strength, stand.getAttackSpeed(), stand.getPrecision());
			helper.assertTrue(lightAtTop == STAR_PLATINUM_TOP * 0.25F && heavyAtTop == STAR_PLATINUM_TOP,
					"Star Platinum's light and heavy punch damage: " + lightAtTop + ", " + heavyAtTop);
			double precisionPart = stand.getPrecision() / 16;
			float barrageHitByTop = (0.04F + STAR_PLATINUM_TOP * 0.01F) * (float) (1 + precisionPart * 0.5 * Math.min(precisionPart, 0.5));
			near(helper, "barrage hit damage", barrageHitAtTop, barrageHitByTop);
			float barrageSecond = barrageHitByTop * Math.max((int) (stand.getAttackSpeed() * 8.0 - 20.0), 0);
			near(helper, "charged heavy damage", chargedAtTop, Math.max(STAR_PLATINUM_TOP * 1.5F, barrageSecond * 1.1F));
			helper.assertTrue(chargedAtTop > StandStatFormulas.getChargedHeavyAttackDamage(TOP, stand.getAttackSpeed(), stand.getPrecision())
					&& lightAtTop > StandStatFormulas.getLightAttackDamage(TOP),
					"Star Platinum's damage at power " + STAR_PLATINUM_TOP + " is not above its damage at " + TOP);

			near(helper, "projectile speed", StandStatFormulas.projectileVelocityScaling(strength, 1F), (TOP - 8) / 12F + 1);
			near(helper, "block mining efficiency", StandStatFormulas.getBlockMiningEfficiency(strength), (TOP - 16.5F) * 2);
			near(helper, "barrage block mining efficiency", StandStatFormulas.getBarrageBlockMiningEfficiency(strength, stand.getAttackSpeed()),
					(TOP - 16.5F) * 2 * StandStatFormulas.getBarrageHitsPerSecond(stand.getAttackSpeed()) / 8F);
			near(helper, "uppercut knockback", StandStatFormulas.getUppercutKnockback(strength, 1F), 0.5F + TOP / 16 * 1F);
			near(helper, "block shard velocity", StandStatFormulas.getBlockShardVelocity(strength), 0.5F + TOP * 0.05F);
			helper.assertTrue(Math.abs(StandStatFormulas.maxBreakableHardness(strength) - Math.exp(TOP / 4)) < 1.0E-9
					&& StandStatFormulas.getLegacyHarvestTier(strength) == (int) TOP / 4
					&& StandStatFormulas.getStandHarvestLevel(strength) == StandStatFormulas.getStandHarvestLevel(TOP),
					"What a punch of power " + STAR_PLATINUM_TOP + " may break is not what one of power " + TOP + " may");
			double durability = stand.getDurability();
			for (float blocked : BLOCKED_RATIOS) {
				helper.assertTrue(StandStatFormulas.getPhysicalResistance(durability, strength, blocked, 1F)
						== StandStatFormulas.getPhysicalResistance(durability, TOP, blocked, 1F),
						"The physical resistance reads power above " + TOP + " (blocked " + blocked + ")");
			}
			helper.assertTrue(stats.power(1F) == STAR_PLATINUM_TOP, "Star Platinum's top power is " + stats.power(1F));
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned()) {
				type.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	private static void compare(List<String> failures, String label, double power, double durability, double speed) {
		for (float meter : FINISHER_METERS) {
			expect(failures, label + " heavy knockback " + meter, StandStatFormulas.getHeavyAttackKnockback(power, meter),
					0.5F + (float) power / (8 - meter * 4));
			expect(failures, label + " uppercut knockback " + meter, StandStatFormulas.getUppercutKnockback(power, meter),
					0.5F + (float) power / 16 * meter);
		}
		expect(failures, label + " leap", StandStatFormulas.getLeapStrength(power), (float) Math.min(power, 40) / 5F);
		expect(failures, label + " heavy blast radius", StandStatFormulas.getHeavyPunchBlastRadius(power),
				Math.min((float) power * 0.175f, 10));
		expect(failures, label + " charged blast radius", StandStatFormulas.getChargedHeavyBlastRadius(power),
				Math.min((float) power * 0.25F, 10));
		expect(failures, label + " block shard velocity", StandStatFormulas.getBlockShardVelocity(power),
				0.5f + (float) power * 0.05f);
		expect(failures, label + " swing pitch", StandStatFormulas.getBarrageSwingPitch(power), 1.8F - (float) power * 0.05F);
		expect(failures, label + " barrage knockback factor", StandStatFormulas.getBarrageKnockbackFactor(power),
				Mth.clamp((float) power * 0.0075F, 0, 1));
		expect(failures, label + " tooth speed", (float) StandStatFormulas.getKnockedOutToothSpeed(power),
				(float) (Math.max(power, 2.0D) * 0.05D));
		for (float velocity : new float[] { 0.5F, 1F, 3F }) {
			float expected = velocity;
			if (power > 8) {
				expected *= ((float) power - 8) / 12f + 1;
			}
			expect(failures, label + " projectile speed " + velocity, StandStatFormulas.projectileVelocityScaling(power, velocity), expected);
		}
		expect(failures, label + " block mining efficiency", StandStatFormulas.getBlockMiningEfficiency(power),
				power > 17 ? (float) (power - 16.5) * 2 : 1);
		float barrageMining = StandStatFormulas.getBarrageHitsPerSecond(speed) / 8F
				* (power > 17 ? (float) (power - 16.5) * 2 : 1);
		expect(failures, label + " barrage block mining efficiency", StandStatFormulas.getBarrageBlockMiningEfficiency(power, speed), barrageMining);
		expect(failures, label + " breakable hardness", (float) StandStatFormulas.maxBreakableHardness(power), (float) Math.exp(power / 4));
		expect(failures, label + " legacy harvest tier", StandStatFormulas.getLegacyHarvestTier(power), (int) power / 4);
		int tier = Mth.clamp((int) (power - 2) / 3, 0, StandStatFormulas.miningTiers.length - 1);
		if (StandStatFormulas.getStandHarvestLevel(power) != StandStatFormulas.miningTiers[tier]) {
			failures.add(label + " harvest level differs");
		}
		double config = JojoModConfig.getCommonConfigInstance(false).standResistanceMultiplier.get();
		for (float blocked : BLOCKED_RATIOS) {
			for (float damage : new float[] { 1F, 40F }) {
				expect(failures, label + " resistance blocked " + blocked + " damage " + damage,
						StandStatFormulas.getPhysicalResistance(durability, power, blocked, damage),
						uncappedResistance(durability, power, blocked, damage, config));
			}
		}
	}

	private static float uncappedResistance(double durability, double strength, float blocked, float damageDealt, double config) {
		double x = (durability * 2 + strength * 1) / 3;
		double resistance = x / (x + 4);
		double dmgCoeff = 1;
		if (blocked > 0) {
			dmgCoeff -= 0.8 * blocked;
			double furtherReductionCap = durability / 2;
			if (damageDealt < furtherReductionCap) {
				dmgCoeff *= damageDealt / furtherReductionCap;
			}
		}
		if (config > 1) {
			dmgCoeff /= config;
		}
		resistance += (1 - resistance) * Mth.clamp(1 - dmgCoeff, 0, 1);
		return (float) resistance;
	}

	private static void expect(List<String> failures, String what, float actual, float expected) {
		if (!(Math.abs(actual - expected) <= EPSILON)) {
			failures.add(what + " is " + actual + ", the uncapped formula gives " + expected);
		}
	}

	private static void expect(List<String> failures, String what, int actual, int expected) {
		if (actual != expected) {
			failures.add(what + " is " + actual + ", the uncapped formula gives " + expected);
		}
	}

	private static void near(GameTestHelper helper, String what, float actual, float expected) {
		helper.assertTrue(Math.abs(actual - expected) <= EPSILON * Math.max(1F, Math.abs(expected)),
				"Star Platinum at power " + STAR_PLATINUM_TOP + ": " + what + " is " + actual + ", at power " + TOP + " it is " + expected);
	}

	private static float heavyPunchKnockback(StandEntityHeavyPunch action, StandEntity stand) {
		try {
			Method method = StandEntityHeavyPunch.class.getDeclaredMethod("getAdditionalHeavyPunchKnockback", StandEntity.class);
			method.setAccessible(true);
			return (Float) method.invoke(action, stand);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not read the heavy punch's knockback", e);
		}
	}

	private static float swingPitch(StandEntityBarrage barrage, StandEntity stand) {
		try {
			Method method = StandEntityBarrage.class.getDeclaredMethod("getBarrageSwingPitch", StandEntity.class);
			method.setAccessible(true);
			return (Float) method.invoke(barrage, stand);
		}
		catch (ReflectiveOperationException e) {
			throw new IllegalStateException("Could not read the barrage swing pitch", e);
		}
	}
}
