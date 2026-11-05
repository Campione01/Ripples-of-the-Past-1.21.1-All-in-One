package rotp.addons.beachboy.util;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.annotation.Nullable;

import rotp.core.powersystem.standpower.StandPower;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

/**
 * Next Album 0.1.13 StandStaminaPauseUtil (from StandPowerStaminaPauseMixin): a Beach Boy player's stamina stops
 * regenerating for 10 ticks after each stamina cost it pays. Here a core stamina tick-gain factor of 0 in that window,
 * kept per side.
 */
public final class BeachBoyStaminaPause {
    private static final int PAUSE_TICKS = 10;
    private static final Map<UUID, Long> SERVER_PAUSED_UNTIL = new ConcurrentHashMap<>();
    private static final Map<UUID, Long> CLIENT_PAUSED_UNTIL = new ConcurrentHashMap<>();

    private BeachBoyStaminaPause() {}

    public static void pause(@Nullable LivingEntity user) {
        if (user instanceof Player) {
            map(user).put(user.getUUID(), user.level().getGameTime() + PAUSE_TICKS);
        }
    }

    public static float tickGainFactor(StandPower power) {
        LivingEntity user = power.getUser();
        if (user == null || !BeachBoySettings.isBeachBoy(power)) {
            return 1.0F;
        }
        Map<UUID, Long> paused = map(user);
        Long until = paused.get(user.getUUID());
        if (until == null) {
            return 1.0F;
        }
        if (user.level().getGameTime() >= until) {
            paused.remove(user.getUUID());
            return 1.0F;
        }
        return 0.0F;
    }

    private static Map<UUID, Long> map(LivingEntity user) {
        return user.level().isClientSide() ? CLIENT_PAUSED_UNTIL : SERVER_PAUSED_UNTIL;
    }
}
