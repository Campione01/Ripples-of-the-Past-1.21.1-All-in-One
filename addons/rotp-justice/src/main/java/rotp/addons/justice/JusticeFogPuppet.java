package rotp.addons.justice;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;

/** Justice fog and puppets, owned by one server and used only on its main thread. */
public final class JusticeFogPuppet {
	public static final double RADIUS = 150.0;
	private static final double RADIUS_SQR = RADIUS * RADIUS;
	private static final int TICK_INTERVAL = 5;
	private static final int CLOUDS_PER_BURST = 500;
	private static final double LAUNCH_SPEED = 2.5;
	private static final float ALLY_HEALTH_THRESHOLD = 0.05F;
	private static final int RETALIATION_TICKS = 100;

	private final Set<UUID> enabled = new HashSet<>();
	private final Map<UUID, Set<Mob>> allies = new HashMap<>();
	private final Set<LivingEntity> injured = identitySet();

	public boolean enable(ServerPlayer caster) {
		return enabled.add(caster.getUUID());
	}

	/** Returns the new enabled state. Callers own the other Fog-dependent effects and sync. */
	public boolean toggle(ServerPlayer caster) {
		if (enable(caster)) return true;
		stop(caster.getUUID());
		return false;
	}

	public boolean isEnabled(UUID casterId) {
		return enabled.contains(casterId);
	}

	/** Dismisses this caster's exact retained puppets on Fog off, death or logout. Not on recall. */
	public boolean stop(UUID casterId) {
		boolean wasEnabled = enabled.remove(casterId);
		Set<Mob> retained = allies.remove(casterId);
		if (retained != null) {
			for (Mob ally : retained) {
				if (ally.isAlive()) ally.kill();
			}
		}
		return wasEnabled;
	}

	/** Drops server-local references after lifecycle stop callbacks have been handled. */
	public void clear() {
		enabled.clear();
		allies.clear();
		injured.clear();
	}

	/** Puppet's click; a wound need not have been marked by a Fog hurt event. */
	public int trigger(ServerPlayer caster) {
		UUID owner = caster.getUUID();
		if (!isEnabled(owner)) return 0;
		ServerLevel level = caster.serverLevel();
		Vec3 launch = caster.getLookAngle().scale(LAUNCH_SPEED);
		int launched = 0;
		for (LivingEntity target : level.getEntitiesOfClass(LivingEntity.class,
				caster.getBoundingBox().inflate(RADIUS), candidate -> candidate != caster && candidate.isAlive()
						&& candidate.getHealth() < candidate.getMaxHealth() && withinFog(caster, candidate))) {
			target.setDeltaMovement(launch);
			target.hasImpulse = true;
			target.hurtMarked = true;
			if (target instanceof Mob mob && isLowHealth(mob)) retain(owner, mob);
			launched++;
		}
		return launched;
	}

	public void tick(ServerPlayer caster) {
		ServerLevel level = caster.serverLevel();
		if (!isEnabled(caster.getUUID()) || level.getGameTime() % TICK_INTERVAL != 0) return;
		tickPuppets(caster, level);
		emitFog(caster, level);
	}

	/** Registers the donor's visual wound only; no Puppet motion or enlistment happens here. */
	public void onHurt(LivingEntity victim, float amount) {
		if (!(amount > 0.0F) || !(victim.level() instanceof ServerLevel level) || enabled.isEmpty()) return;
		for (ServerPlayer caster : level.players()) {
			if (isEnabled(caster.getUUID()) && withinFog(caster, victim)) {
				injured.add(victim);
				return;
			}
		}
	}

	public void tickInjured(LivingEntity victim) {
		if (!(victim.level() instanceof ServerLevel level) || !injured.contains(victim)) return;
		if (!victim.isAlive() || victim.getHealth() >= victim.getMaxHealth()) {
			forgetInjured(victim);
			return;
		}
		if (level.getGameTime() % TICK_INTERVAL == 0) {
			level.sendParticles(ParticleTypes.SMOKE, victim.getX(), victim.getY() + victim.getBbHeight() / 2.0,
					victim.getZ(), 8, 0.25, 0.35, 0.25, 0.01);
		}
	}

	public void forgetInjured(LivingEntity victim) {
		injured.remove(victim);
	}

	private void tickPuppets(ServerPlayer caster, ServerLevel level) {
		UUID owner = caster.getUUID();
		var area = caster.getBoundingBox().inflate(RADIUS);
		for (Mob mob : level.getEntitiesOfClass(Mob.class, area,
				candidate -> candidate.isAlive() && isLowHealth(candidate) && withinFog(caster, candidate))) {
			retain(owner, mob);
		}
		Set<Mob> retained = allies.get(owner);
		if (retained == null) return;

		Set<LivingEntity> attackers = new HashSet<>(level.getEntitiesOfClass(Mob.class, area,
				candidate -> candidate.isAlive() && candidate.getTarget() == caster));
		LivingEntity lastAttacker = caster.getLastHurtByMob();
		if (lastAttacker != null && lastAttacker.isAlive()
				&& caster.tickCount - caster.getLastHurtByMobTimestamp() <= RETALIATION_TICKS
				&& withinFog(caster, lastAttacker)) {
			attackers.add(lastAttacker);
		}

		// Once enlisted, donor puppets are retained even if they heal or leave the radius.
		var it = retained.iterator();
		while (it.hasNext()) {
			Mob ally = it.next();
			if (!ally.isAlive()) {
				it.remove();
				continue;
			}
			for (LivingEntity attacker : attackers) {
				if (attacker == ally || retained.contains(attacker)) continue;
				ally.setTarget(attacker);
				break;
			}
		}
		if (retained.isEmpty()) allies.remove(owner);
	}

	private static void emitFog(ServerPlayer caster, ServerLevel level) {
		double y = caster.getY() + caster.getBbHeight() / 2.0;
		ThreadLocalRandom random = ThreadLocalRandom.current();
		for (int i = 0; i < CLOUDS_PER_BURST; i++) {
			double vertical = random.nextDouble(-1.0, 1.0);
			double angle = random.nextDouble(0.0, Math.PI * 2.0);
			double distance = RADIUS * Math.cbrt(random.nextDouble());
			double horizontal = distance * Math.sqrt(1.0 - vertical * vertical);
			level.sendParticles(ParticleTypes.CLOUD, caster.getX() + horizontal * Math.cos(angle),
					y + distance * vertical, caster.getZ() + horizontal * Math.sin(angle), 1, 0, 0, 0, 0);
		}
	}

	private void retain(UUID caster, Mob mob) {
		allies.computeIfAbsent(caster, ignored -> identitySet()).add(mob);
	}

	private static boolean withinFog(ServerPlayer caster, LivingEntity target) {
		return caster.distanceToSqr(target) <= RADIUS_SQR;
	}

	private static boolean isLowHealth(Mob mob) {
		return mob.getMaxHealth() > 0.0F && mob.getHealth() / mob.getMaxHealth() < ALLY_HEALTH_THRESHOLD;
	}

	private static <T> Set<T> identitySet() {
		return Collections.newSetFromMap(new IdentityHashMap<>());
	}
}
