package rotp.addons.spin.entity;

import net.minecraft.world.phys.Vec3;

public final class SpinRope {
    private SpinRope() {
    }

    public static boolean canAnchor(double distanceToOwner, double maxLength) {
        return distanceToOwner <= maxLength;
    }

    public static Vec3 pull(Vec3 velocity, Vec3 toAnchor, double strength, double maxSpeed) {
        double length = toAnchor.length();
        if (length < 1.0E-6) {
            return velocity;
        } else {
            Vec3 result = velocity.add(toAnchor.scale(strength / length));
            double speed = result.length();
            return speed > maxSpeed ? result.scale(maxSpeed / speed) : result;
        }
    }

    public static boolean shouldRelease(double distanceToAnchor, int ropeTicks, double releaseDistance, int maxTicks) {
        return distanceToAnchor <= releaseDistance || ropeTicks > maxTicks;
    }
}
