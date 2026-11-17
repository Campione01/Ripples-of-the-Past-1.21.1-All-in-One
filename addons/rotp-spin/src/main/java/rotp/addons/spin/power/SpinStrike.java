package rotp.addons.spin.power;

public final class SpinStrike {
    private SpinStrike() {
    }

    public static double efficiency(boolean chipped, double chippedMultiplier) {
        return !chipped ? 1.0 : Math.max(0.0, Math.min(1.0, chippedMultiplier));
    }

    public static double scaled(double base, boolean chipped, double chippedMultiplier) {
        return Math.max(0.0, base) * efficiency(chipped, chippedMultiplier);
    }

    public static double liftedY(double currentY, double lift) {
        return Math.max(currentY, lift);
    }
}
