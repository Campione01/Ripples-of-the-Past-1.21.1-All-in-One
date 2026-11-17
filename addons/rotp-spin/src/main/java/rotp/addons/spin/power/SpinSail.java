package rotp.addons.spin.power;

import net.minecraft.world.phys.Vec3;

public final class SpinSail {
    private SpinSail() {
    }

    public static boolean shouldStart(boolean sailing, float fallDistance, double minFallDistance) {
        return sailing || (double)fallDistance >= minFallDistance;
    }

    public static boolean hasEnergy(boolean sailing, double energy, double costPerTick, double startEnergy) {
        return energy >= (sailing ? costPerTick : Math.max(costPerTick, startEnergy));
    }

    public static Vec3 sail(Vec3 velocity, Vec3 look, double maxFallSpeed, double push, double maxHorizontalSpeed) {
        double y = Math.max(velocity.y, -maxFallSpeed);
        double x = velocity.x;
        double z = velocity.z;
        double lookLength = Math.sqrt(look.x * look.x + look.z * look.z);
        if (lookLength > 1.0E-6) {
            x += look.x / lookLength * push;
            z += look.z / lookLength * push;
        }

        double horizontal = Math.sqrt(x * x + z * z);
        if (horizontal > maxHorizontalSpeed) {
            double scale = maxHorizontalSpeed / horizontal;
            x *= scale;
            z *= scale;
        }

        return new Vec3(x, y, z);
    }
}
