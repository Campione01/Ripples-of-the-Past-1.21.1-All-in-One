package rotp.core.api.damage;

import rotp.core.core.JojoMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;

/**
 * Damage type tags for hits the core's own defences do not stop, for Gold Experience Requiem ("黃鎮自身其他傷害以及持有者召喚黃鎮期間的
 * 近戰傷害也無視敵方格擋或無敵幀以及無敵機制和限傷等"). Vanilla's part is vanilla's tags (minecraft:bypasses_shield,
 * bypasses_cooldown, bypasses_invulnerability, bypasses_resistance, bypasses_effects...), which an add-on puts on its
 * own damage types as well; these three are the core's part, empty by default, so nothing changes for a type in none.
 * <ul>
 * <li>{@link #GUARD} {@code jojo_ripples:pierces_guard}: a Stand's guard (held or automatic) and the half block of a
 * busy Stand; a guarding Stand's cut of the hits on its user, explosions included; a barrage parry; the counters and
 * dodges that cancel a hit: Vampirism's Freeze, the Pillar Man's Blade Dash, Blade Barrage and evasions, Hamon's Sendo
 * Wave Kick, Rebuff Overdrive and Snake Muffler.</li>
 * <li>{@link #INVULNERABILITY} {@code jojo_ripples:pierces_invulnerability}: a Stand body hurt only by Stands; the window
 * after a Stand's hit in which another Stand hit deals only its excess; a sleeper in a closed coffin.</li>
 * <li>{@link #DAMAGE_CAPS} {@code jojo_ripples:pierces_damage_caps}: a Stand's resistance from its durability and power
 * and the standResistanceMultiplier setting, on a hit it does not block; its user's Resistance on the hits it takes; a
 * Stand user's Resolve; the timeStopDamageMultiplier setting below 1 on a target held in stopped time; Hamon's
 * Protection and Rebuff Overdrive's cut.</li>
 * </ul>
 */
public final class DefencePiercing {
	public static final TagKey<DamageType> GUARD = create("pierces_guard");
	public static final TagKey<DamageType> INVULNERABILITY = create("pierces_invulnerability");
	public static final TagKey<DamageType> DAMAGE_CAPS = create("pierces_damage_caps");

	private DefencePiercing() {}

	/** Whether no guard, parry or counter of the core stops or cuts the hit. */
	public static boolean piercesGuard(DamageSource source) {
		return source.is(GUARD);
	}

	/** Whether no invulnerable state or invulnerability window of the core stops or cuts the hit. */
	public static boolean piercesInvulnerability(DamageSource source) {
		return source.is(INVULNERABILITY);
	}

	/** Whether no resistance or damage cap of the core cuts the hit. */
	public static boolean piercesDamageCaps(DamageSource source) {
		return source.is(DAMAGE_CAPS);
	}

	private static TagKey<DamageType> create(String path) {
		return TagKey.create(Registries.DAMAGE_TYPE, JojoMod.resLoc(path));
	}
}
