package rotp.addons.beachboy.util;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.beachboy.network.BeachBoySwimPosePayload;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * 1.16 BeachBoyPathSwim: a player dragged down along the path line, or slammed by the overhead slash, lies in the
 * swimming pose for a while, on every client that sees it.
 */
public final class BeachBoyPathSwim {
    private static final Map<UUID, Long> ACTIVE_UNTIL = new HashMap<>();
    private static final Map<UUID, Integer> LAST_SENT_REMAINING = new HashMap<>();

    private BeachBoyPathSwim() {}

    public static boolean isActive(@Nullable LivingEntity living) {
        return living != null && ACTIVE_UNTIL.containsKey(living.getUUID());
    }

    public static void set(@Nullable Entity entity, boolean swim) {
        if (entity instanceof LivingEntity living) {
            if (swim) {
                ACTIVE_UNTIL.put(living.getUUID(), living.level().getGameTime() + 2L);
                apply(living);
            }
            else {
                clear(living);
            }
        }
    }

    public static void slam(@Nullable LivingEntity living, int ticks) {
        if (living != null) {
            ACTIVE_UNTIL.put(living.getUUID(), living.level().getGameTime() + Math.max(2, ticks));
            apply(living);
        }
    }

    public static void keep(@Nullable LivingEntity living) {
        if (living == null) {
            return;
        }
        Long until = ACTIVE_UNTIL.get(living.getUUID());
        if (until != null) {
            if (living.level().getGameTime() > until) {
                clear(living);
            }
            else {
                applyPoseOnly(living);
            }
        }
    }

    public static void clear(@Nullable LivingEntity living) {
        if (living == null) {
            return;
        }
        boolean had = ACTIVE_UNTIL.remove(living.getUUID()) != null;
        LAST_SENT_REMAINING.remove(living.getUUID());
        if (had || living instanceof Player player && player.getForcedPose() == Pose.SWIMMING) {
            if (!living.level().isClientSide()) {
                sync(living, 0);
            }
            if (!BeachBoyCollapse.isCollapsed(living)) {
                clearPoseOnly(living);
            }
        }
    }

    public static void applyFromPacket(@Nullable Entity entity, int remainingTicks) {
        if (!(entity instanceof LivingEntity living)) {
            return;
        }
        if (remainingTicks <= 0) {
            ACTIVE_UNTIL.remove(living.getUUID());
            if (!BeachBoyCollapse.isCollapsed(living)) {
                clearPoseOnly(living);
            }
        }
        else {
            long until = remainingTicks >= 20 ? living.level().getGameTime() + remainingTicks : Long.MAX_VALUE / 4;
            ACTIVE_UNTIL.put(living.getUUID(), until);
            applyPoseOnly(living);
        }
    }

    public static void forget(UUID id) {
        ACTIVE_UNTIL.remove(id);
        LAST_SENT_REMAINING.remove(id);
    }

    private static void apply(LivingEntity living) {
        applyPoseOnly(living);
        if (!living.level().isClientSide()) {
            Long until = ACTIVE_UNTIL.get(living.getUUID());
            sync(living, until == null ? 0 : (int) Math.max(1L, until - living.level().getGameTime()));
        }
    }

    private static void sync(LivingEntity living, int remainingTicks) {
        Integer last = LAST_SENT_REMAINING.get(living.getUUID());
        if (last == null || remainingTicks <= 0 || last <= 0 || remainingTicks > last) {
            LAST_SENT_REMAINING.put(living.getUUID(), remainingTicks);
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(living,
                    new BeachBoySwimPosePayload(living.getId(), remainingTicks));
        }
    }

    private static void applyPoseOnly(LivingEntity living) {
        living.setSwimming(true);
        living.setPose(Pose.SWIMMING);
        if (living instanceof Player player) {
            player.setForcedPose(Pose.SWIMMING);
        }
        living.refreshDimensions();
    }

    private static void clearPoseOnly(LivingEntity living) {
        living.setSwimming(false);
        if (living instanceof Player player && player.getForcedPose() == Pose.SWIMMING) {
            player.setForcedPose(null);
        }
        if (living.getPose() == Pose.SWIMMING && !living.isInWater()) {
            living.setPose(Pose.STANDING);
        }
        living.refreshDimensions();
    }
}
