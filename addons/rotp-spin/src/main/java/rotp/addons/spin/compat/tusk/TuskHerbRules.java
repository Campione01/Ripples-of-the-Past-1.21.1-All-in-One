package rotp.addons.spin.compat.tusk;

public final class TuskHerbRules {
    public static final int MAX_NAILS = 10;

    private TuskHerbRules() {
    }

    public static boolean growsNailThisTick(int remainingTicks, int intervalTicks, int nails, int maxNails) {
        return remainingTicks > 0 && intervalTicks > 0 && nails < maxNails ? remainingTicks % intervalTicks == 0 : false;
    }

    public static int extendInfusion(int remainingTicks, int durationTicks, int maxTicks) {
        return Math.max(remainingTicks, Math.min(maxTicks, Math.max(0, remainingTicks) + durationTicks));
    }
}
