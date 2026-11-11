package rotp.core.powersystem.standpower;

import javax.annotation.Nullable;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.init.ModDamageTypes;
import rotp.core.powersystem.standpower.entity.StandEntity;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * A Stand's damage to players and to mobs (StandTuning) on every hit the core counts as a Stand's: a source that names the
 * Stand power it came from, or one of the damage types a Stand can be hurt by (#jojo_ripples:can_hurt_stands), whose
 * attacker is a Stand or a Stand's user. An add-on that hits with these types, or names its power on its source, gets the
 * share without code of its own; the Stand body's link to its user passes the hit on unscaled, as its type is not one of them.
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
		StandTuning tuning = StandTuning.of(attackerPower(source));
		if (tuning == null) {
			return;
		}
		// a hit on a Stand's body is a hit on its user, who is the player or the mob
		LivingEntity victim = target instanceof StandEntity stand && stand.getUser() != null ? stand.getUser() : target;
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

	@Nullable
	public static StandPower attackerPower(DamageSource source) {
		if (source instanceof DamageSourceModified modified && modified.jojo_ripples$standPower() != null) {
			return modified.jojo_ripples$standPower();
		}
		if (!source.is(ModDamageTypes.CAN_HURT_STANDS)) {
			return null;
		}
		if (source.getDirectEntity() instanceof StandEntity stand) {
			return stand.getUserPower();
		}
		Entity attacker = source.getEntity();
		return attacker instanceof LivingEntity living ? StandPower.get(StandUtil.getStandUser(living)) : null;
	}
}
