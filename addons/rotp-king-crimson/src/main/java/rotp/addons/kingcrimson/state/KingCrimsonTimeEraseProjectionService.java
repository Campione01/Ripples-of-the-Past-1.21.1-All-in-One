package rotp.addons.kingcrimson.state;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import org.slf4j.Logger;

import rotp.core.powersystem.standpower.StandUtil;
import com.mojang.logging.LogUtils;

import rotp.addons.kingcrimson.KingCrimsonAddon;
import rotp.addons.kingcrimson.KingCrimsonPhase5Spec;
import rotp.addons.kingcrimson.entity.KingCrimsonAfterimageEntity;
import rotp.addons.kingcrimson.entity.KingCrimsonTimeEraseDecoyEntity;
import rotp.addons.kingcrimson.network.TimeEraseAfterimageRemovePayload;
import rotp.addons.kingcrimson.network.TimeEraseAfterimageSpawnPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

public final class KingCrimsonTimeEraseProjectionService {
    private static final Logger LOGGER = LogUtils.getLogger();

    private KingCrimsonTimeEraseProjectionService() {
    }

    public static void begin(ServerPlayer player) {
        KingCrimsonTemporalState state =
                KingCrimsonAddon.temporalState(player);
        Session stale = state.takeProjectionSession();
        if (stale != null) {
            stale.close(player);
        }

        Session session = new Session(player.serverLevel());
        state.setProjectionSession(session);
        try {
            spawnAfterimages(player, session);
            spawnDecoy(player, session);
        } catch (RuntimeException error) {
            state.takeProjectionSession();
            session.close(player);
            LOGGER.error(
                    "Failed to initialize King Crimson Time Erase "
                            + "projection for {}.",
                    player.getGameProfile().getName(),
                    error);
        }
    }

    public static void tick(ServerPlayer player) {
        Session session = KingCrimsonAddon.temporalState(player)
                .projectionSession();
        if (session != null
                && session.level != player.serverLevel()) {
            finish(player);
        }
    }

    /**
     * A creature that turns on this player while his erase runs is turned on his decoy instead, as those were that hunted
     * him when it began, and loses it with them when the erase ends. False when he has no decoy: nothing is changed then.
     */
    public static boolean retarget(ServerPlayer player, Mob mob) {
        Session session = player.hasData(KingCrimsonAddon.TEMPORAL_STATE)
                ? KingCrimsonAddon.temporalState(player).projectionSession() : null;
        if (session == null || session.closed || session.decoy == null || !session.decoy.isAlive() || mob.level() != session.level) {
            return false;
        }
        if (!session.retargetedMobs.contains(mob)) {
            session.retargetedMobs.add(mob);
        }
        mob.setTarget(session.decoy);
        return true;
    }

    /** The decoy that stands in the world for this player while he erases; null when he does not, or has none. */
    @Nullable
    public static KingCrimsonTimeEraseDecoyEntity decoyOf(ServerPlayer player) {
        Session session = player.hasData(KingCrimsonAddon.TEMPORAL_STATE)
                ? KingCrimsonAddon.temporalState(player).projectionSession() : null;
        return session != null && !session.closed && session.decoy != null && session.decoy.isAlive()
                ? session.decoy : null;
    }

    public static void finish(ServerPlayer player) {
        Session session = KingCrimsonAddon.temporalState(player)
                .takeProjectionSession();
        if (session != null) {
            session.close(player);
        }
    }

    // One dark-red afterimage trailing each entity by half a second, as in 1.16. The donor's second,
    // full-color afterimage stood still for three seconds to mark where erase end would rewind the entity,
    // and its glow gave the caster an outline of every tracked creature through walls. Owner decision
    // 2026-09-23 (no rewind, manga logic) drops that copy; by the owner's follow-up the same day its red
    // outline moves onto the dark-red afterimage, which the caster's client alone receives.
    private static void spawnAfterimages(
            ServerPlayer player, Session session) {
        List<LivingEntity> targets =
                session.level.getEntitiesOfClass(
                        LivingEntity.class,
                        player.getBoundingBox().inflate(
                                KingCrimsonPhase5Spec
                                        .TIME_ERASE_AFTERIMAGE_RADIUS),
                        target -> isAfterimageTarget(player, target));
        for (LivingEntity target : targets) {
            spawnAfterimage(
                    player,
                    session.afterimageIds,
                    target,
                    KingCrimsonPhase5Spec
                            .TIME_ERASE_MOVING_AFTERIMAGE_DELAY_TICKS);
        }
    }

    private static boolean isAfterimageTarget(
            ServerPlayer player, LivingEntity target) {
        // A shifted player has no trailing afterimage: the eraser's client draws his image where the server names
        // it each tick (KingCrimsonShiftedImagesClient). The decoy of another eraser stands for its player here
        // too, and that eraser and his Stand are not there for anybody.
        boolean ownDecoy = target instanceof KingCrimsonTimeEraseDecoyEntity decoy && decoy.getOwner() == player;
        boolean standsForAPlayer = target instanceof Player
                || target instanceof KingCrimsonTimeEraseDecoyEntity;
        return target.isAlive()
                && target != player
                && !ownDecoy
                && !(standsForAPlayer
                        && KingCrimsonAddon.temporalState(player).shiftSession() != null)
                && !KingCrimsonEraseReach.isOutOfReach(target)
                && StandUtil.getStandUser(target) != player;
    }

    private static void spawnAfterimage(
            ServerPlayer observer,
            List<Integer> entityIds,
            LivingEntity target,
            int delayTicks) {
        KingCrimsonAfterimageEntity afterimage =
                KingCrimsonAddon.KC_AFTERIMAGE_ENTITY.get()
                        .create(target.level());
        if (afterimage == null) {
            return;
        }
        afterimage.initialize(
                target,
                delayTicks,
                KingCrimsonAddon.temporalState(observer).eraseLengthTicks());
        entityIds.add(afterimage.getId());
        PacketDistributor.sendToPlayer(
                observer,
                TimeEraseAfterimageSpawnPayload.from(afterimage));
    }

    private static void spawnDecoy(
            ServerPlayer player, Session session) {
        KingCrimsonTimeEraseDecoyEntity decoy =
                KingCrimsonAddon.TIME_ERASE_DECOY_ENTITY.get()
                        .create(session.level);
        if (decoy == null) {
            return;
        }
        // Owner 2026-11-07: riding at the press, he leaves the mount and the decoy takes his seat.
        Entity mount = player.getVehicle();
        decoy.setOwner(player);
        decoy.setLifespanTicks(KingCrimsonAddon.temporalState(player).eraseLengthTicks());
        if (mount != null) {
            player.stopRiding();
        }
        if (!session.level.addFreshEntity(decoy)) {
            decoy.discard();
            return;
        }
        if (mount != null && mount.isAlive()) {
            decoy.startRiding(mount, true);
        }
        session.decoy = decoy;
        session.retargetedMobs.addAll(
                session.level.getEntitiesOfClass(
                        Mob.class,
                        player.getBoundingBox().inflate(
                                KingCrimsonPhase5Spec
                                        .TIME_ERASE_DECOY_TARGET_RADIUS),
                        mob -> mob.getTarget() == player));
        session.retargetedMobs.forEach(
                mob -> mob.setTarget(decoy));
    }

    static final class Session {
        private final ServerLevel level;
        private final List<Integer> afterimageIds =
                new ArrayList<>();
        private final List<Mob> retargetedMobs =
                new ArrayList<>();
        @Nullable
        private KingCrimsonTimeEraseDecoyEntity decoy;
        private boolean closed;

        private Session(ServerLevel level) {
            this.level = level;
        }

        private void close(ServerPlayer observer) {
            if (closed) {
                return;
            }
            closed = true;
            sendRemoval(observer, afterimageIds);

            if (decoy != null) {
                for (Mob mob : retargetedMobs) {
                    if (mob.isAlive() && mob.getTarget() == decoy) {
                        mob.setTarget(null);
                    }
                }
                decoy.discard();
            }
            afterimageIds.clear();
            retargetedMobs.clear();
            decoy = null;
        }

        private static void sendRemoval(
                ServerPlayer observer, List<Integer> entityIds) {
            if (entityIds.isEmpty()
                    || observer.connection == null
                    || observer.hasDisconnected()) {
                return;
            }
            try {
                PacketDistributor.sendToPlayer(
                        observer,
                        new TimeEraseAfterimageRemovePayload(
                                entityIds.stream()
                                        .mapToInt(Integer::intValue)
                                        .toArray()));
            } catch (RuntimeException error) {
                LOGGER.debug(
                        "Time Erase observer disconnected before "
                                + "afterimage cleanup.",
                        error);
            }
        }
    }

}
