package rotp.addons.spin.power;

public final class SpinLeap {
    static final double MIN_SPEED_RATIO = 0.05;

    private SpinLeap() {
    }

    public static float strength(float baseStrength, double speed, double baseSpeed, boolean ignoreSlowness) {
        if (ignoreSlowness && !(baseSpeed <= 0.0)) {
            double ratio = speed / baseSpeed;
            return ratio >= 1.0 ? baseStrength : (float)((double)baseStrength / Math.max(ratio, 0.05));
        } else {
            return baseStrength;
        }
    }
}
