package rotp.addons.ger.state;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import javax.annotation.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

/**
 * Where those who are after a Requiem user stood in the last two seconds, so
 * that an attack returned to zero can be put back to where it began. Only who
 * showed it is followed: a creature that took him as its target, and whoever
 * hit him. With nobody after him nothing is kept and nothing runs.
 */
public final class GerAttackOrigins {
    /** How far back an attack can have begun. */
    public static final int HISTORY_TICKS = 40;
    /** How long someone is followed after he last showed himself. */
    public static final int WATCH_TICKS = 200;

    private static final Map<LivingEntity, Map<UUID, Track>> WATCHED =
            new WeakHashMap<>();

    private GerAttackOrigins() {}

    private static final class Track {
        private final Vec3[] positions = new Vec3[HISTORY_TICKS + 1];
        private int next;
        private int filled;
        private long watchedUntil;

        private void push(Vec3 position) {
            positions[next] = position;
            next = (next + 1) % positions.length;
            filled = Math.min(filled + 1, positions.length);
        }

        @Nullable
        private Vec3 ago(int ticks) {
            if (filled == 0) {
                return null;
            }
            int back = Math.min(Math.max(ticks, 0), filled - 1);
            return positions[Math.floorMod(
                    next - 1 - back, positions.length)];
        }
    }

    public static void watch(LivingEntity user, Entity attacker) {
        if (attacker == user || user.level().isClientSide()) {
            return;
        }
        Track track = WATCHED
                .computeIfAbsent(user, ignored -> new HashMap<>())
                .computeIfAbsent(
                        attacker.getUUID(), ignored -> new Track());
        if (track.filled == 0) {
            track.push(attacker.position());
        }
        track.watchedUntil =
                user.level().getGameTime() + WATCH_TICKS;
    }

    /** Where the attacker stood that many ticks ago, as far back as it was followed; null if it was not. */
    @Nullable
    public static Vec3 positionAgo(
            LivingEntity user, Entity attacker, int ticks) {
        Map<UUID, Track> tracks = WATCHED.get(user);
        Track track = tracks != null
                ? tracks.get(attacker.getUUID())
                : null;
        return track != null ? track.ago(ticks) : null;
    }

    /** Once a server tick, after the entities moved. */
    public static void tick() {
        if (WATCHED.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<LivingEntity, Map<UUID, Track>>> users =
                WATCHED.entrySet().iterator();
        while (users.hasNext()) {
            Map.Entry<LivingEntity, Map<UUID, Track>> entry =
                    users.next();
            LivingEntity user = entry.getKey();
            if (user == null
                    || user.isRemoved()
                    || !(user.level() instanceof ServerLevel level)) {
                users.remove();
                continue;
            }
            long now = level.getGameTime();
            Iterator<Map.Entry<UUID, Track>> tracks =
                    entry.getValue().entrySet().iterator();
            while (tracks.hasNext()) {
                Map.Entry<UUID, Track> tracked = tracks.next();
                Entity attacker = level.getEntity(tracked.getKey());
                if (attacker == null
                        || !attacker.isAlive()
                        || now > tracked.getValue().watchedUntil) {
                    tracks.remove();
                }
                else {
                    tracked.getValue().push(attacker.position());
                }
            }
            if (entry.getValue().isEmpty()) {
                users.remove();
            }
        }
    }

    public static void clear() {
        WATCHED.clear();
    }
}
