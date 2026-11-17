package rotp.addons.spin.entity;

import net.minecraft.world.phys.Vec3;

public final class SpinRicochet {
    private SpinRicochet() {
    }

    public static Vec3 reflect(Vec3 velocity, Vec3 normal, double retention) {
        double dot = velocity.dot(normal);
        return velocity.subtract(normal.scale(2.0 * dot)).scale(retention);
    }

    public static boolean canBounce(int bouncesDone, int maxBounces, double speedAfter, double minSpeed) {
        return bouncesDone < maxBounces && speedAfter >= minSpeed;
    }
}
