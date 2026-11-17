package rotp.core.gametest;

import java.util.UUID;

import rotp.core.api.entity.EntityStateRestore;
import rotp.core.api.stand.StandPowerTransitions;
import rotp.core.core.JojoMod;
import rotp.core.core.JojoRegistries;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.standpower.StandInstance;
import rotp.core.powersystem.standpower.StandPower;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * EntityStateRestore, the snapshot and restore a rewind or a pull-back puts on a live entity: a Stand user's health,
 * place and effects go back to the snapshot's, the effects it gained since are gone, its UUID stays, and its summoned
 * Stand is still out and its own after the load.
 */
@GameTestHolder(JojoMod.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EntityStateRestoreGameTests {

	private EntityStateRestoreGameTests() {}

	@GameTest(template = "empty")
	public static void aStandUserIsPutBackAsItWasWithItsStandStillOut(GameTestHelper helper) {
		Zombie zombie = EntityType.ZOMBIE.create(helper.getLevel());
		helper.assertTrue(zombie != null, "Setup: could not create the zombie");
		StandType type = JojoRegistries.DEFAULT_STANDS_REG.get(JojoMod.resLoc("star_platinum"));
		Vec3 origin = Vec3.atCenterOf(helper.absolutePos(new BlockPos(1, 2, 1)));
		zombie.setNoAi(true);
		zombie.setNoGravity(true);
		zombie.moveTo(origin.x, origin.y, origin.z, 0.0F, 0.0F);
		helper.assertTrue(helper.getLevel().addFreshEntity(zombie), "Setup: could not add the zombie");
		StandPower power = PowerClass.STAND.attachGet(zombie);
		try {
			helper.assertTrue(type != null && StandPowerTransitions.insert(power, new StandInstance(type)).status()
					== StandPowerTransitions.Status.APPLIED && type.summon(zombie, power), "Setup: could not summon Star Platinum");
			StandEntity stand = power.getSummonedStandEntity();
			helper.assertTrue(stand != null, "Setup: the zombie's Stand is missing");
			zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 400, 1));
			UUID uuid = zombie.getUUID();
			float health = zombie.getHealth();
			Vec3 at = zombie.position();
			EntityStateRestore.Snapshot snapshot = EntityStateRestore.capture(zombie);

			zombie.setHealth(health / 4);
			zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 2));
			zombie.removeEffect(MobEffects.DAMAGE_RESISTANCE);
			zombie.teleportTo(at.x + 2, at.y, at.z);
			EntityStateRestore.restore(zombie, snapshot);

			helper.assertTrue(zombie.getHealth() == health && zombie.position().distanceToSqr(at) < 1.0E-6
					&& zombie.getUUID().equals(uuid), "The zombie was not put back: health " + zombie.getHealth() + " ("
							+ health + "), at " + zombie.position() + " (" + at + "), UUID kept " + zombie.getUUID().equals(uuid));
			helper.assertTrue(zombie.hasEffect(MobEffects.DAMAGE_RESISTANCE) && !zombie.hasEffect(MobEffects.MOVEMENT_SPEED),
					"The zombie's effects are not the snapshot's: " + zombie.getActiveEffectsMap().keySet());
			StandPower restored = PowerClass.STAND.get(zombie);
			helper.assertTrue(restored != null && restored.isSummoned() && restored.getSummonedStandEntity() == stand
					&& !stand.isRemoved() && stand.getUser() == zombie, "The zombie's Stand is no longer out and its own: power "
							+ restored + ", summoned " + (restored != null ? restored.getSummonedStandEntity() : null));
		}
		finally {
			StandPower now = PowerClass.STAND.get(zombie);
			if (now != null && now.isSummoned() && type != null) {
				type.forceUnsummon(zombie, now);
			}
			zombie.discard();
		}
		helper.succeed();
	}
}
