package rotp.addons.spin.entity;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.phys.Vec3;

public final class WreckingBall {
    public static final int SATELLITE_COUNT = 14;
    public static final double GOLDEN_ANGLE = Math.PI * (3.0 - Math.sqrt(5.0));

    private WreckingBall() {
    }

    public static List<Vec3> socketDirections(int count) {
        List<Vec3> directions = new ArrayList<>(Math.max(count, 0));

        for (int i = 0; i < count; i++) {
            double y = 1.0 - 2.0 * ((double)i + 0.5) / (double)count;
            double ring = Math.sqrt(1.0 - y * y);
            double angle = GOLDEN_ANGLE * (double)i;
            directions.add(new Vec3(ring * Math.cos(angle), y, ring * Math.sin(angle)));
        }

        return directions;
    }

    public static Vec3 satelliteVelocity(Vec3 aimDir, int index, double speed) {
        Vec3 dir = aimDir.normalize();
        double angle = GOLDEN_ANGLE * (double)index;
        double cos = Math.cos(angle);
        double sin = Math.sin(angle);
        double x = dir.x * cos + dir.z * sin;
        double z = -dir.x * sin + dir.z * cos;
        double y = index == 0 ? dir.y : dir.y * 0.5 + (index % 2 == 1 ? 0.25 : -0.25);
        return new Vec3(x, y, z).normalize().scale(speed);
    }

    public static List<Vec3> satelliteVelocities(Vec3 aimDir, int count, double speed) {
        List<Vec3> velocities = new ArrayList<>(Math.max(count, 0));

        for (int i = 0; i < count; i++) {
            velocities.add(satelliteVelocity(aimDir, i, speed));
        }

        return velocities;
    }

    public static boolean isOnLeftSide(Vec3 look, Vec3 toTarget) {
        return look.z * toTarget.x - look.x * toTarget.z > 0.0;
    }

    public static float neglectVeilOpacity(int remainingTicks, int fadeOutTicks, double maxOpacity) {
        if (remainingTicks > 0 && !(maxOpacity <= 0.0)) {
            double max = Math.min(maxOpacity, 1.0);
            return fadeOutTicks > 0 && remainingTicks < fadeOutTicks ? (float)(max * (double)remainingTicks / (double)fadeOutTicks) : (float)max;
        } else {
            return 0.0F;
        }
    }

    public static float veerRight(float yaw, double degreesPerTick) {
        return (float)((double)yaw + Math.max(degreesPerTick, 0.0));
    }
}
