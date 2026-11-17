package rotp.core.powersystem.standpower;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * A Stand's damage to players and to mobs (StandTuning) on every hit that is the Stand's work, whatever its damage type
 * (StandDamageContext): its body's and projectiles' hits, what the entities and effects it made do, and what its abilities
 * do with no other attacker than it or its user. The Stand body's link to its user passes a hit on unscaled, and what a
 * Stand does to its own user is not scaled.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class StandTuningDamage {
	private StandTuningDamage() {}

	@SubscribeEvent
	public static void onIncomingDamage(LivingIncomingDamageEvent event) {
		LivingEntity target = event.getEntity();
		if (target.level().isClientSide()) {
			return;
		}
		DamageSource source = event.getSource();
		StandDamageContext.Owner owner = StandDamageContext.attackerOf(source);
		StandTuning tuning = owner != null ? StandTuning.of(owner.type()) : null;
		if (tuning == null) {
			return;
		}
		// a hit on a Stand's body is a hit on its user, who is the player or the mob
		LivingEntity victim = target instanceof StandEntity stand && stand.getUser() != null ? stand.getUser() : target;
		if (StandDamageContext.isOwnUser(owner, victim)) {
			return;
		}
		float amount = event.getAmount();
		float tuned = tuning.damage(amount, victim);
		if (tuned == amount) {
			return;
		}
		if (source instanceof DamageSourceModified modified && modified.jojo_ripples$isNonLethal()) {
			tuned = Math.min(tuned, Math.max(target.getHealth() - 0.0001F, 0.0F));
		}
		event.setAmount(tuned);
	}

	/** The Stand power a damage is the work of (StandDamageContext#attackerOf), or null. */
	@Nullable
	public static StandPower attackerPower(DamageSource source) {
		StandDamageContext.Owner owner = StandDamageContext.attackerOf(source);
		return owner != null ? owner.power() : null;
	}
}
