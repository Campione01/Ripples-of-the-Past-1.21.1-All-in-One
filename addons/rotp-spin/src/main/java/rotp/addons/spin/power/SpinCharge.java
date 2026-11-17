package rotp.addons.spin.power;

public final class SpinCharge {
    private static final double EPS = 1.0E-9;

    private SpinCharge() {
    }

    public static double fraction(int chargeTicks, int maxTicks) {
        return maxTicks <= 0 ? 1.0 : clamp01((double)chargeTicks / (double)maxTicks);
    }

    public static double limit(boolean chipped, double chippedMax) {
        return chipped ? clamp01(chippedMax) : 1.0;
    }

    public static double cap(double fraction, boolean chipped, double chippedMax) {
        return Math.min(clamp01(fraction), limit(chipped, chippedMax));
    }

    public static double multiplier(double fraction, double maxMultiplier) {
        return 1.0 + Math.max(0.0, maxMultiplier - 1.0) * clamp01(fraction);
    }

    public static boolean isFull(int chargeTicks, int maxTicks, boolean chipped, double chippedMax) {
        return fraction(chargeTicks, maxTicks) >= limit(chipped, chippedMax) - 1.0E-9;
    }

    public static int percent(double fraction) {
        return (int)Math.round(clamp01(fraction) * 100.0);
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }
}
