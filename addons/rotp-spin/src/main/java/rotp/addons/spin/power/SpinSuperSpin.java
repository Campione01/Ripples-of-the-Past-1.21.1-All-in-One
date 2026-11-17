package rotp.addons.spin.power;

public final class SpinSuperSpin {
    private static final int MAX_GALLOP_TICKS = 1000000;
    public static final double SPEED_SMOOTHING = 0.3;

    private SpinSuperSpin() {
    }

    public static double smoothSpeed(double smoothed, double raw) {
        return smoothed + (raw - smoothed) * 0.3;
    }

    public static boolean isCrash(boolean horizontalCollision, double rawSpeed, double smoothedSpeed, double stopFraction) {
        return horizontalCollision && rawSpeed < smoothedSpeed * stopFraction;
    }

    public static int nextSlowTicks(int slowTicks, boolean galloping) {
        return galloping ? 0 : Math.min(slowTicks + 1, 1000000);
    }

    public static int nextGallopTicks(int ticks, boolean galloping, int slowTicks, int graceTicks, boolean broken, boolean horseHealthy) {
        if (!broken && horseHealthy && slowTicks <= Math.max(0, graceTicks)) {
            return galloping ? Math.min(ticks + 1, 1000000) : ticks;
        } else {
            return 0;
        }
    }

    public static boolean isGallopReady(int ticks, int requiredTicks) {
        return ticks >= Math.max(1, requiredTicks);
    }

    public static boolean isHealthy(float health, float maxHealth, double minFraction) {
        return maxHealth > 0.0F && (double)(health / maxHealth) >= minFraction;
    }

    public static boolean canDetour(int lesson, boolean chipped, boolean ownHorse, boolean horseHealthy, double riderDistanceSq, double kickRange) {
        return lesson >= 5 && !chipped && ownHorse && horseHealthy && riderDistanceSq <= kickRange * kickRange;
    }

    public static long detourUntil(long now, int durationTicks) {
        return now + (long)Math.max(0, durationTicks);
    }

    public static double multiplier(boolean superSpin, double base, double superMultiplier) {
        return superSpin ? Math.max(base, superMultiplier) : base;
    }
}
