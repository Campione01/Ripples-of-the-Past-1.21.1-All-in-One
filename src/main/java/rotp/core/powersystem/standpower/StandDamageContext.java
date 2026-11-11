package rotp.core.powersystem.standpower;

import java.util.ArrayDeque;
import java.util.Map;

import javax.annotation.Nullable;

import org.jetbrains.annotations.ApiStatus;

import com.google.common.collect.MapMaker;

import rotp.core.core.JojoMod;
import rotp.core.customobjects.DamageSourceModified;
import rotp.core.customobjects.entity_projectile.DamagingEntity;
import rotp.core.init.ModDamageTypes;
import rotp.core.powersystem.PowerClass;
import rotp.core.powersystem.ability.Ability;
import rotp.core.powersystem.entityaction.EntityActionInstance;
import rotp.core.powersystem.standpower.entity.StandEntity;
import rotp.core.powersystem.standpower.entity.StandLinkDamageSource;
import rotp.core.powersystem.standpower.type.StandType;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.TraceableEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;

/**
 * Which Stand a damage is the work of, whatever its type, for the Stand's damage shares (StandTuningDamage), so an
 * add-on's Stand gets them without code of its own. A damage is a Stand's when its source names the Stand's power, when
 * the Stand's body, a Stand projectile, or an entity the Stand made hits with it, or when it has no other attacker than
 * the Stand or its user while the Stand's code runs: an ability's press, release and action ticks, the tick of an entity
 * the Stand made, and the tick of an effect it gave. What a Stand does to its own user is never its hit.
 * <p>
 * The entities and effects a Stand made are remembered while they exist in this session, not saved with the world.
 * Code the core does not run as the Stand's (an add-on's own event handlers) can say so with {@link #enter}, or mark an
 * entity with {@link #claim}, or a source with {@code DamageSourceModified#jojo_ripples$setStandPower}.
 */
@EventBusSubscriber(modid = JojoMod.MOD_ID)
public final class StandDamageContext {
	private StandDamageContext() {}

	/** The Stand a damage is the work of: its type as it was then (whose shares apply), and its power (whose user it spares). */
	public record Owner(StandPower power, StandType type) {}

	private static final ThreadLocal<ArrayDeque<Owner>> RUNNING = ThreadLocal.withInitial(ArrayDeque::new);
	// identity keys: an entity is equal to another with its id, an effect to another with its numbers
	private static final Map<Entity, Owner> MADE = new MapMaker().weakKeys().makeMap();
	private static final Map<MobEffectInstance, Owner> GIVEN = new MapMaker().weakKeys().makeMap();
	private static final Scope NONE = new Scope(null);

	/** Code that runs as the Stand's until the scope closes: use it in try-with-resources. A null power opens nothing. */
	public static Scope enter(@Nullable StandPower power) {
		return enter(ownerOf(power));
	}

	private static Scope enter(@Nullable Owner owner) {
		if (owner == null) {
			return NONE;
		}
		RUNNING.get().push(owner);
		return new Scope(owner);
	}

	/** The Stand power of an ability's power class on its user's server side, or null for another power or a client. */
	@Nullable
	public static StandPower of(@Nullable PowerClass<?> powerClass, @Nullable LivingEntity user) {
		return powerClass == PowerClass.STAND && user != null && !user.level().isClientSide() ? StandPower.get(user) : null;
	}

	/** The Stand power an action is the ability of, on its performer's server side, or null. */
	@Nullable
	public static StandPower of(EntityActionInstance action) {
		return action.ability instanceof Ability ability ? of(ability.abilityId.powerClass(), action.getPowerUser()) : null;
	}

	/** Marks an entity as the Stand's work, for an add-on that makes one outside the Stand's code. */
	public static void claim(Entity entity, StandPower power) {
		Owner owner = ownerOf(power);
		if (owner != null && !entity.level().isClientSide()) {
			MADE.put(entity, owner);
		}
	}

	/** The Stand whose code runs now, or null. */
	@Nullable
	public static Owner running() {
		return RUNNING.get().peek();
	}

	/** The Stand that made this entity: its body, a Stand projectile, or one made while its code ran. */
	@Nullable
	public static Owner madeBy(@Nullable Entity entity) {
		if (entity == null) {
			return null;
		}
		if (entity instanceof StandEntity stand) {
			return ownerOf(stand.getUserPower());
		}
		if (entity instanceof DamagingEntity damaging && damaging.standDamage()) {
			LivingEntity owner = damaging.getOwner();
			return owner != null ? ownerOf(StandPower.get(StandUtil.getStandUser(owner))) : null;
		}
		return MADE.get(entity);
	}

	/** The Stand a damage is the work of, or null: see the class description. */
	@Nullable
	public static Owner attackerOf(DamageSource source) {
		// the body's link passes a hit on to its user as it was
		if (source instanceof StandLinkDamageSource) {
			return null;
		}
		if (source instanceof DamageSourceModified modified && modified.jojo_ripples$standPower() != null) {
			return ownerOf(modified.jojo_ripples$standPower());
		}
		Entity direct = source.getDirectEntity();
		Entity attacker = source.getEntity();
		Owner owner = madeBy(direct);
		if (owner == null) {
			owner = madeBy(attacker);
		}
		if (owner == null && source.is(ModDamageTypes.CAN_HURT_STANDS) && attacker instanceof LivingEntity living) {
			owner = ownerOf(StandPower.get(StandUtil.getStandUser(living)));
		}
		if (owner == null) {
			Owner running = running();
			if (running != null && isOwn(direct, running) && isOwn(attacker, running)) {
				owner = running;
			}
		}
		return owner;
	}

	/** Whether the entity is the Stand's user or the Stand's own: a hit on them is never the Stand's. */
	public static boolean isOwnUser(Owner owner, @Nullable Entity entity) {
		return entity != null && (entity == owner.power().getUser() || entity instanceof StandEntity stand
				&& stand.getUser() == owner.power().getUser());
	}

	private static boolean isOwn(@Nullable Entity entity, Owner owner) {
		return entity == null || isOwnUser(owner, entity) || owner.equals(MADE.get(entity));
	}

	@Nullable
	private static Owner ownerOf(@Nullable StandPower power) {
		if (power == null || !power.hasPower() || power.getUser() == null || power.getUser().level().isClientSide()) {
			return null;
		}
		StandType type = power.getPowerType();
		return type != null ? new Owner(power, type) : null;
	}

	/** The core's server entity tick (ServerLevelStandDamageScopeMixin): a Stand's body or an entity it made ticks as its. */
	@ApiStatus.Internal
	public static Scope entityTicks(Entity entity) {
		if (entity instanceof StandEntity stand) {
			return enter(stand.getUserPower());
		}
		return MADE.isEmpty() ? NONE : enter(MADE.get(entity));
	}

	/** An effect's tick (MobEffectInstanceStandDamageScopeMixin): one a Stand gave ticks as its. */
	@ApiStatus.Internal
	public static Scope effectTicks(MobEffectInstance effect) {
		return GIVEN.isEmpty() ? NONE : enter(GIVEN.get(effect));
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onEntityJoin(EntityJoinLevelEvent event) {
		if (event.loadedFromDisk() || event.getLevel().isClientSide()) {
			return;
		}
		Owner owner = running();
		Entity entity = event.getEntity();
		if (owner != null && isMadeThing(entity, owner)) {
			MADE.putIfAbsent(entity, owner);
		}
	}

	// a creature that only happens to be born meanwhile (a slime split by the Stand's blow) is not the Stand's
	private static boolean isMadeThing(Entity entity, Owner owner) {
		if (entity instanceof Player || entity instanceof StandEntity || isOwnUser(owner, entity)) {
			return false;
		}
		if (!(entity instanceof LivingEntity)) {
			return true;
		}
		Entity master = entity instanceof OwnableEntity ownable ? ownable.getOwner()
				: entity instanceof TraceableEntity traceable ? traceable.getOwner() : null;
		return isOwnUser(owner, master);
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public static void onEffectAdded(MobEffectEvent.Added event) {
		LivingEntity holder = event.getEntity();
		if (holder.level().isClientSide()) {
			return;
		}
		Owner owner = running();
		if (owner == null) {
			owner = madeBy(event.getEffectSource());
		}
		if (owner == null || isOwnUser(owner, holder)) {
			return;
		}
		// an effect the holder already has takes the new one into itself, and that one ticks on
		MobEffectInstance ticking = event.getOldEffectInstance() != null ? event.getOldEffectInstance() : event.getEffectInstance();
		GIVEN.put(ticking, owner);
	}

	/** A Stand's code running: closing it ends that, in the order scopes were opened. */
	public static final class Scope implements AutoCloseable {
		@Nullable private final Owner owner;

		private Scope(@Nullable Owner owner) {
			this.owner = owner;
		}

		@Override
		public void close() {
			if (owner != null) {
				ArrayDeque<Owner> running = RUNNING.get();
				if (running.peek() == owner) {
					running.pop();
				}
				else {
					running.removeFirstOccurrence(owner);
				}
			}
		}
	}
}
