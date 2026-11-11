package rotp.core.api.damage;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import rotp.core.core.JojoMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;

/**
 * Hits the core's own defences do not stop, for Gold Experience Requiem ("黃鎮自身其他傷害以及持有者召喚黃鎮期間的近戰傷害也無視敵方格擋或
 * 無敵幀以及無敵機制和限傷等"). A hit pierces a {@link Defence} when its damage type is in that defence's tag, or when a
 * registered {@link AttackerRule} says its attacker's hit does (the holder's own melee is a vanilla player attack, whose
 * type cannot carry the tags). Vanilla's part is vanilla's tags (minecraft:bypasses_shield, bypasses_resistance...),
 * which an add-on puts on its own damage types as well; a core reader asks a vanilla tag only in that tag's own sense
 * (bypasses_armor for an armour-like cut, bypasses_resistance for Resistance), never for one of these.
 * The tags hold no type of an add-on by default and no rule is registered, so nothing changes for a hit until an add-on
 * asks.
 * <ul>
 * <li>{@link Defence#GUARD} {@code jojo_ripples:pierces_guard}: a Stand's guard (held or automatic) and the half block of
 * a busy Stand; a guarding Stand's cut of the hits on its user, explosions included; a barrage parry; the counters and
 * dodges that cancel a hit: Vampirism's Freeze, the Pillar Man's Blade Dash, Blade Barrage and evasions, Hamon's Sendo
 * Wave Kick, Rebuff Overdrive and Snake Muffler; Hamon's Protection (a Hamon Master's whole hit, a player's paid cut)
 * and the cut Rebuff Overdrive's charge gives.</li>
 * <li>{@link Defence#INVULNERABILITY} {@code jojo_ripples:pierces_invulnerability}: the hurt cooldown (invulnerability
 * frames, as minecraft:bypasses_cooldown); the window after a Stand's hit in which another Stand hit deals only its
 * excess; a sleeper in a closed coffin. A Stand body hurt only by Stands asks this tag on the hit's damage type, never an
 * attacker rule: only a Stand touches a Stand.</li>
 * <li>{@link Defence#DAMAGE_CAPS} {@code jojo_ripples:pierces_damage_caps}: limits on a hit: a Stand user's Resistance on
 * the hits his Stand takes. The core has no per-hit or per-second limit and no "cannot go below 1 health" rule of its
 * own; any it gets answers this one.</li>
 * <li>{@link Defence#MITIGATION} {@code jojo_ripples:pierces_mitigation}: what lessens a hit as armour does, which only
 * true damage passes: a Stand's resistance from its durability and power and the standResistanceMultiplier setting, on
 * a hit it does not block; a Stand user's Resolve; the timeStopDamageMultiplier setting below 1 on a target held in
 * stopped time. The tag the core ships holds vanilla's void and /kill (minecraft:out_of_world, generic_kill), which 1.16
 * let through Resolve.</li>
 * </ul>
 */
public final class DefencePiercing {

	public enum Defence {
		GUARD("pierces_guard"),
		INVULNERABILITY("pierces_invulnerability"),
		DAMAGE_CAPS("pierces_damage_caps"),
		MITIGATION("pierces_mitigation");

		private final TagKey<DamageType> tag;

		Defence(String tag) {
			this.tag = TagKey.create(Registries.DAMAGE_TYPE, JojoMod.resLoc(tag));
		}

		/** The damage type tag whose types pierce this defence. */
		public TagKey<DamageType> tag() {
			return tag;
		}
	}

	/**
	 * Whether a living attacker's hit pierces a defence now, asked on the server. A rule registered by
	 * {@link #registerAttackerRule(ResourceLocation, AttackerRule)} is asked only for the attacker's direct hits (its
	 * melee: the source's direct entity is the attacker itself), never for its projectiles or other indirect damage.
	 */
	@FunctionalInterface
	public interface AttackerRule {
		boolean pierces(LivingEntity attacker, DamageSource source, Defence defence);
	}

	public static final TagKey<DamageType> GUARD = Defence.GUARD.tag();
	public static final TagKey<DamageType> INVULNERABILITY = Defence.INVULNERABILITY.tag();
	public static final TagKey<DamageType> DAMAGE_CAPS = Defence.DAMAGE_CAPS.tag();
	public static final TagKey<DamageType> MITIGATION = Defence.MITIGATION.tag();

	private static final Map<ResourceLocation, Registered> RULES = new LinkedHashMap<>();
	private static volatile Registered[] rules = new Registered[0];

	private DefencePiercing() {}

	/** Registers a rule asked for the attacker's direct hits only (its melee). */
	public static void registerAttackerRule(ResourceLocation id, AttackerRule rule) {
		register(id, rule, false);
	}

	/** Registers a rule asked for every hit whose causing entity is a living attacker: its projectiles and the rest too. */
	public static void registerAttackerRuleForAnySource(ResourceLocation id, AttackerRule rule) {
		register(id, rule, true);
	}

	private static synchronized void register(ResourceLocation id, AttackerRule rule, boolean anySource) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(rule, "rule");
		if (RULES.putIfAbsent(id, new Registered(id, rule, anySource)) != null) {
			throw new IllegalStateException("Duplicate defence piercing rule: " + id);
		}
		rules = RULES.values().toArray(new Registered[0]);
	}

	/** Whether the hit pierces this defence: by its damage type's tag or by a rule on its attacker. */
	public static boolean pierces(DamageSource source, Defence defence) {
		return source.is(defence.tag()) || piercesByAttacker(source, defence);
	}

	/** Whether no guard, parry or counter of the core stops or cuts the hit. */
	public static boolean piercesGuard(DamageSource source) {
		return pierces(source, Defence.GUARD);
	}

	/** Whether no hurt cooldown, invulnerable state or invulnerability window of the core stops or cuts the hit. */
	public static boolean piercesInvulnerability(DamageSource source) {
		return pierces(source, Defence.INVULNERABILITY);
	}

	/** Whether no damage cap of the core limits the hit. */
	public static boolean piercesDamageCaps(DamageSource source) {
		return pierces(source, Defence.DAMAGE_CAPS);
	}

	/** Whether nothing of the core lessens the hit as armour does: true damage. */
	public static boolean piercesMitigation(DamageSource source) {
		return pierces(source, Defence.MITIGATION);
	}

	private static boolean piercesByAttacker(DamageSource source, Defence defence) {
		Registered[] all = rules;
		if (all.length == 0 || !(source.getEntity() instanceof LivingEntity attacker) || attacker.level().isClientSide()) {
			return false;
		}
		boolean direct = source.getDirectEntity() == attacker;
		for (Registered registered : all) {
			if (!direct && !registered.anySource()) {
				continue;
			}
			try {
				if (registered.rule().pierces(attacker, source, defence)) {
					return true;
				}
			}
			catch (RuntimeException error) {
				JojoMod.getLogger().error("Defence piercing rule {} failed.", registered.id(), error);
			}
		}
		return false;
	}

	private record Registered(ResourceLocation id, AttackerRule rule, boolean anySource) {}
}
