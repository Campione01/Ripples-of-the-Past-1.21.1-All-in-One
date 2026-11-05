package rotp.addons.sexpistols.content.revolver;

import rotp.addons.sexpistols.config.SexPistolsConfig;
import rotp.addons.sexpistols.content.revolver.item.RevolverStats;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class RevolverRecoil {

    private static long serverApplications;
    private static float lastServerPitch;
    private static float lastServerYaw;
    private static boolean lastServerAiming;

    private RevolverRecoil() {}

    // Next Album 0.1.13 RevolverItem.getPitchRecoil/getYawRecoil: the gun's
    // own recoil, its ADS scale and the recoilMultiplier config.
    public static Recoil values(
            RevolverStats stats,
            boolean aiming,
            float randomValue,
            double multiplier) {
        return new Recoil(
                stats.pitchRecoil(aiming, multiplier),
                stats.yawRecoil(
                        aiming,
                        Mth.clamp(randomValue, 0.0F, 1.0F),
                        multiplier));
    }

    public static void applyServer(
            Player player,
            RevolverStats stats,
            boolean aiming,
            float randomValue) {
        Recoil recoil = values(
                stats,
                aiming,
                randomValue,
                SexPistolsConfig.recoilMultiplier());
        player.setXRot(Mth.clamp(
                player.getXRot() - recoil.pitch(),
                -90.0F,
                90.0F));
        player.setYRot(player.getYRot() + recoil.yaw());
        synchronized (RevolverRecoil.class) {
            serverApplications++;
            lastServerPitch = recoil.pitch();
            lastServerYaw = recoil.yaw();
            lastServerAiming = aiming;
        }
    }

    public static synchronized Snapshot snapshot() {
        return new Snapshot(
                serverApplications,
                lastServerPitch,
                lastServerYaw,
                lastServerAiming);
    }

    public static synchronized void clearReceipts() {
        serverApplications = 0L;
        lastServerPitch = 0.0F;
        lastServerYaw = 0.0F;
        lastServerAiming = false;
    }

    public record Recoil(float pitch, float yaw) {}

    public record Snapshot(
            long serverApplications,
            float lastServerPitch,
            float lastServerYaw,
            boolean lastServerAiming) {}
}
