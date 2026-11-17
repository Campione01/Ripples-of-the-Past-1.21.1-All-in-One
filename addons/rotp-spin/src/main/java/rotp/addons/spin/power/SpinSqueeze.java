package rotp.addons.spin.power;

public final class SpinSqueeze {
    private SpinSqueeze() {
    }

    public static double relativeHeight(double hitY, double targetBottomY, double targetHeight) {
        if (!(targetHeight > 0.0)) {
            return 0.5;
        } else {
            double rel = (hitY - targetBottomY) / targetHeight;
            return Math.max(0.0, Math.min(1.0, rel));
        }
    }

    public static SpinSqueeze.Zone zone(double relativeHeight, double legHeight, double headHeight) {
        if (relativeHeight < legHeight) {
            return SpinSqueeze.Zone.LEGS;
        } else {
            return relativeHeight < Math.max(legHeight, headHeight) ? SpinSqueeze.Zone.BODY : SpinSqueeze.Zone.HEAD;
        }
    }

    public static int duration(int baseTicks, boolean chipped, double damagedMultiplier) {
        double ticks = (double)Math.max(0, baseTicks) * (chipped ? Math.max(0.0, damagedMultiplier) : 1.0);
        return (int)Math.round(ticks);
    }

    public static boolean applies(boolean enabled, int lesson, int minLesson) {
        return enabled && lesson >= minLesson;
    }

    public static enum Zone {
        LEGS,
        BODY,
        HEAD;
    }
}
