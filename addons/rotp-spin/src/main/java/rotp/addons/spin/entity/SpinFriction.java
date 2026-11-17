package rotp.addons.spin.entity;

public final class SpinFriction {
    private SpinFriction() {
    }

    public static boolean canStripBark(boolean enabled, boolean spinning, boolean returning, double speed, double minSpeed) {
        return enabled && spinning && !returning && speed >= minSpeed;
    }

    public static boolean canCutBullets(boolean enabled, boolean spinning, boolean returning, boolean alreadyCut, double speed, double minSpeed) {
        return !alreadyCut && canStripBark(enabled, spinning, returning, speed, minSpeed);
    }

    public static double blockConsumeChance(int bulletsPerCut, int nuggetsPerBlock) {
        return nuggetsPerBlock <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, (double)bulletsPerCut / (double)nuggetsPerBlock));
    }

    public static double speedAfterStrip(double speed, double retention) {
        return speed * Math.max(0.0, Math.min(1.0, retention));
    }
}
