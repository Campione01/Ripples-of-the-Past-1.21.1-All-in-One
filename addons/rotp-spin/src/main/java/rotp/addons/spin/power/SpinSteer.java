package rotp.addons.spin.power;

import net.minecraft.world.phys.Vec3;

public final class SpinSteer {
    private static final double EPSILON_SQ = 1.0E-8;

    private SpinSteer() {
    }

    public static Vec3 steer(Vec3 velocity, Vec3 desiredDirection, double turnRate, double minSpeed) {
        if (desiredDirection.lengthSqr() < 1.0E-8) {
            return velocity;
        } else {
            Vec3 desired = desiredDirection.normalize();
            double speed = Math.max(velocity.length(), minSpeed);
            double rate = Math.max(0.0, Math.min(1.0, turnRate));
            Vec3 current = velocity.lengthSqr() < 1.0E-8 ? desired : velocity.normalize();
            Vec3 blended = current.scale(1.0 - rate).add(desired.scale(rate));
            if (blended.lengthSqr() < 1.0E-8) {
                blended = desired;
            }

            return blended.normalize().scale(speed);
        }
    }
}
