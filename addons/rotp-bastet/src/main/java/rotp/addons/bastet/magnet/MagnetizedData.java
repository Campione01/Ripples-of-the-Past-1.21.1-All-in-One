package rotp.addons.bastet.magnet;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.annotation.Nullable;

import rotp.addons.bastet.BastetAddon;
import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/**
 * 1.16 MagnetizedData: who is magnetized and for how many more ticks, server side, by UUID. Not saved, as in 1.16
 * (its saveToNBT/loadFromNBT were never called).
 */
public final class MagnetizedData {
    public static final int MAX_DURATION = 2400;
    /** 1.16 isWithinBastetRange: a Bastet user within 20 blocks keeps a creature magnetized. */
    public static final double MAX_RANGE = 20.0D;

    private static final Map<UUID, Integer> MAGNETIZED = new HashMap<>();

    private MagnetizedData() {}

    public static boolean isMagnetized(LivingEntity entity) {
        return MAGNETIZED.containsKey(entity.getUUID());
    }

    public static int getDuration(LivingEntity entity) {
        Integer ticks = MAGNETIZED.get(entity.getUUID());
        return ticks == null ? 0 : ticks;
    }

    /** 0 as it is magnetized, rising to 1 as the magnetization runs out. */
    public static float getProgress(LivingEntity entity) {
        int ticks = getDuration(entity);
        return ticks <= 0 ? 0.0F : 1.0F - (float) ticks / MAX_DURATION;
    }

    public static void magnetize(LivingEntity entity) {
        MAGNETIZED.put(entity.getUUID(), MAX_DURATION);
    }

    public static void clearMagnetization(LivingEntity entity) {
        MAGNETIZED.remove(entity.getUUID());
    }

    /** 1.16 MagnetizedData.onLivingTick, server side. */
    public static void tick(LivingEntity entity) {
        UUID uuid = entity.getUUID();
        if (!entity.isAlive()) {
            MAGNETIZED.remove(uuid);
            return;
        }
        Integer ticks = MAGNETIZED.get(uuid);
        if (ticks == null) {
            return;
        }
        if (ticks <= 0 || !isWithinBastetRange(entity)) {
            MAGNETIZED.remove(uuid);
            return;
        }
        MAGNETIZED.put(uuid, ticks - 1);
        MagnetForceHandler.applyMagnetForce(entity);
    }

    /**
     * The Bastet user whose magnetism hurts this creature, for the Stand's damage share: the nearest other one in range
     * (1.16 kept no owner of a magnetization), or null.
     */
    @Nullable
    public static StandPower bastetPowerNear(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return null;
        }
        ServerPlayer nearest = null;
        for (ServerPlayer player : level.players()) {
            if (player != entity && player.distanceToSqr(entity) <= MAX_RANGE * MAX_RANGE && BastetAddon.isBastetUser(player)
                    && (nearest == null || player.distanceToSqr(entity) < nearest.distanceToSqr(entity))) {
                nearest = player;
            }
        }
        return nearest != null ? StandPower.get(nearest) : null;
    }

    private static boolean isWithinBastetRange(LivingEntity entity) {
        if (!(entity.level() instanceof ServerLevel level)) {
            return false;
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(entity) <= MAX_RANGE * MAX_RANGE && BastetAddon.isBastetUser(player)) {
                return true;
            }
        }
        return false;
    }
}
