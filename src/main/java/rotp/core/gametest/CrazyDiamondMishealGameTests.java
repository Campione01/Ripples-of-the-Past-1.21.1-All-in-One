package rotp.core.gametest;

import java.util.UUID;

import com.mojang.authlib.GameProfile;

import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.impl.stands.crazydiamond.CrazyDHealAbility;
import rotp.core.impl.stands.crazydiamond.MisshapenEffect;
import rotp.core.init.ModStatusEffects;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.type.EntityActionType;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;
import rotp.core.subsystems.target.ActionTarget;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/*
 * Crazy Diamond's misheal (the Disfiguring Punch's Misshapen Face, Arms and Legs) on mobs, and its proper heal (owner,
 * R772.1). A player's parts work in its controls on the client (MisshapenControlsSmokeTest).
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CrazyDiamondMishealGameTests {

	private CrazyDiamondMishealGameTests() {}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aMobsMisshapenFaceCannotHoldATarget(GameTestHelper helper) {
		Husk husk = husk(helper, new BlockPos(1, 2, 1));
		Cow cow = cow(helper, new BlockPos(3, 2, 1));
		husk.setTarget(cow);
		helper.assertTrue(husk.getTarget() == cow, "fixture: the husk did not take the cow as its target");
		husk.addEffect(new MobEffectInstance(ModStatusEffects.MISSHAPEN_FACE, 200));
		helper.runAfterDelay(2, () -> {
			helper.assertTrue(husk.getTarget() == null, "A misshapen face drops the mob's target: it still has " + husk.getTarget());
			husk.setTarget(cow);
			helper.assertTrue(husk.getTarget() == null, "A misshapen face takes no new target: it took " + husk.getTarget());
			husk.removeEffect(ModStatusEffects.MISSHAPEN_FACE);
			husk.setTarget(cow);
			helper.assertTrue(husk.getTarget() == cow, "With the face put right the mob takes a target again");
			helper.succeed();
		});
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aMobsMisshapenArmsDealNoMeleeDamage(GameTestHelper helper) {
		Husk husk = husk(helper, new BlockPos(1, 2, 1));
		Cow struck = cow(helper, new BlockPos(2, 2, 1));
		Cow control = cow(helper, new BlockPos(3, 2, 3));
		husk.addEffect(new MobEffectInstance(ModStatusEffects.MISSHAPEN_ARMS, 200));
		float before = struck.getHealth();
		husk.doHurtTarget(struck);
		helper.assertTrue(struck.getHealth() == before, "A mob with misshapen arms deals no melee damage: the cow went from "
				+ before + " to " + struck.getHealth());
		husk.removeEffect(ModStatusEffects.MISSHAPEN_ARMS);
		float controlBefore = control.getHealth();
		husk.doHurtTarget(control);
		helper.assertTrue(control.getHealth() < controlBefore, "fixture: the husk's blow does no damage even with its arms put right");
		helper.succeed();
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aMobsMisshapenLegsHalveItsSpeedAndStopItsJumpsUntilTheyEnd(GameTestHelper helper) {
		Husk husk = husk(helper, new BlockPos(1, 2, 1));
		double speed = husk.getAttributeValue(Attributes.MOVEMENT_SPEED);
		double jump = husk.getAttributeValue(Attributes.JUMP_STRENGTH);
		helper.assertTrue(speed > 0 && jump > 0, "fixture: the husk has no speed or jump strength");
		husk.addEffect(new MobEffectInstance(ModStatusEffects.MISSHAPEN_LEGS, 4));
		helper.runAfterDelay(1, () -> {
			helper.assertTrue(Math.abs(husk.getAttributeValue(Attributes.MOVEMENT_SPEED) - speed * 0.5) < 1.0E-6,
					"Misshapen legs halve a mob's speed: " + speed + " became " + husk.getAttributeValue(Attributes.MOVEMENT_SPEED));
			helper.assertTrue(husk.getAttributeValue(Attributes.JUMP_STRENGTH) == 0.0, "Misshapen legs stop a mob's jumps: jump strength "
					+ husk.getAttributeValue(Attributes.JUMP_STRENGTH));
		});
		helper.runAfterDelay(10, () -> {
			helper.assertTrue(!husk.hasEffect(ModStatusEffects.MISSHAPEN_LEGS), "fixture: the 4-tick effect did not end");
			helper.assertTrue(husk.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed
					&& husk.getAttributeValue(Attributes.JUMP_STRENGTH) == jump, "When misshapen legs end the mob walks and jumps"
							+ " as before: speed " + husk.getAttributeValue(Attributes.MOVEMENT_SPEED) + " (was " + speed + "), jump "
							+ husk.getAttributeValue(Attributes.JUMP_STRENGTH) + " (was " + jump + ")");
			helper.succeed();
		});
	}

	// 1.16: a player's misshapen legs only invert its controls (client side); its speed and jump stay
	@GameTest(template = "empty", timeoutTicks = 60)
	public static void aPlayersMisshapenLegsLeaveItsSpeedAndJumpAsIn116(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		Player player = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "MisshapenLegsPlayer"));
		Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
		helper.assertTrue(level.addFreshEntity(player), "Could not add the player");
		double speed = player.getAttributeValue(Attributes.MOVEMENT_SPEED);
		double jump = player.getAttributeValue(Attributes.JUMP_STRENGTH);
		try {
			player.addEffect(new MobEffectInstance(ModStatusEffects.MISSHAPEN_LEGS, 200));
			// a fake player does not tick: the effect's tick is run as a real player's would be
			for (int i = 0; i < 3; i++) {
				ModStatusEffects.MISSHAPEN_LEGS.value().applyEffectTick(player, 0);
			}
			helper.assertTrue(player.getAttributeValue(Attributes.MOVEMENT_SPEED) == speed
					&& player.getAttributeValue(Attributes.JUMP_STRENGTH) == jump, "1.16 left a misshapen player's speed and"
							+ " jump alone: speed " + player.getAttributeValue(Attributes.MOVEMENT_SPEED) + " (was " + speed
							+ "), jump " + player.getAttributeValue(Attributes.JUMP_STRENGTH) + " (was " + jump + ")");
			helper.succeed();
		}
		finally {
			player.discard();
		}
	}

	@GameTest(template = "empty", timeoutTicks = 60)
	public static void crazyDiamondsProperHealEndsTheMisshapenParts(GameTestHelper helper) {
		ServerLevel level = helper.getLevel();
		StandType standType = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("crazy_diamond"));
		helper.assertTrue(standType != null, "Missing Crazy Diamond Stand type");
		Player user = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "MishealHealer"));
		StandPower power = null;
		try {
			Vec3 pos = Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
			user.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
			helper.assertTrue(level.addFreshEntity(user), "Could not add the Crazy Diamond player");
			power = PowerClass.STAND.attachGet(user);
			helper.assertTrue(StandPowerTransitions.insert(power, new StandInstance(standType)).status()
					== StandPowerTransitions.Status.APPLIED, "Could not grant Crazy Diamond");
			power.setResolveLevel(4);
			power.getCurTypeData()._lockedAbilities.remove("heal");
			Ability heal = power.getAbility("heal");
			helper.assertTrue(heal instanceof EntityActionType, "fixture: Crazy Diamond's heal is missing: " + heal);
			helper.assertTrue(standType.summon(user, power), "Could not summon Crazy Diamond");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Summoned Crazy Diamond is missing");

			Husk husk = husk(helper, new BlockPos(1, 2, 2));
			helper.assertTrue(husk.getHealth() == husk.getMaxHealth(), "fixture: the husk is hurt");
			for (Holder<MobEffect> effect : MisshapenEffect.effects()) {
				husk.addEffect(new MobEffectInstance(effect, 200));
			}
			CrazyDHealAbility.HealingAction action = new CrazyDHealAbility.HealingAction((EntityActionType) heal);
			boolean healing = action.restoreTarget(new ActionTarget(husk), stand).synched.isHealing;
			for (Holder<MobEffect> effect : MisshapenEffect.effects()) {
				helper.assertTrue(!husk.hasEffect(effect), "Crazy Diamond's proper heal puts the misshapen part right: the husk still"
						+ " has " + effect.getRegisteredName());
			}
			helper.assertTrue(healing, "Putting a misshapen part right counts as healing (sound, particles), at full health too");
			boolean again = action.restoreTarget(new ActionTarget(husk), stand).synched.isHealing;
			helper.assertTrue(!again, "fixture: a healthy husk with nothing misshapen is still being healed");
			helper.succeed();
		}
		finally {
			if (power != null && power.isSummoned()) {
				standType.forceUnsummon(user, power);
			}
			user.discard();
		}
	}

	// the victim's pose is drawn by every client that sees it, so watching players get the effects
	@GameTest(template = "empty", timeoutTicks = 20)
	public static void misshapenPartsAreSentToWatchingPlayers(GameTestHelper helper) {
		for (Holder<MobEffect> effect : MisshapenEffect.effects()) {
			helper.assertTrue(ModStatusEffects.TRACKED_EFFECTS.contains(effect), effect.getRegisteredName()
					+ " must be sent to the players who watch its entity");
		}
		helper.succeed();
	}

	private static Husk husk(GameTestHelper helper, BlockPos pos) {
		Husk husk = EntityType.HUSK.create(helper.getLevel());
		helper.assertTrue(husk != null, "Could not create the husk");
		place(helper, husk, pos);
		return husk;
	}

	private static Cow cow(GameTestHelper helper, BlockPos pos) {
		Cow cow = EntityType.COW.create(helper.getLevel());
		helper.assertTrue(cow != null, "Could not create the cow");
		place(helper, cow, pos);
		return cow;
	}

	private static void place(GameTestHelper helper, LivingEntity entity, BlockPos pos) {
		Vec3 at = Vec3.atBottomCenterOf(helper.absolutePos(pos));
		entity.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
		if (entity instanceof Mob mob) {
			mob.setNoAi(true);
			mob.setPersistenceRequired();
		}
		helper.getLevel().addFreshEntity(entity);
	}
}
