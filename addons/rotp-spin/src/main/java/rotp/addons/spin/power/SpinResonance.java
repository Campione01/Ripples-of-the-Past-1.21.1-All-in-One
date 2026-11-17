package rotp.addons.spin.power;

public final class SpinResonance {
    private SpinResonance() {
    }

    public static int effectiveSources(int sources, int maxSources) {
        return Math.max(0, Math.min(sources, Math.max(0, maxSources)));
    }

    public static double damageMultiplier(int sources, int maxSources, double perSource, double maxMultiplier) {
        int n = effectiveSources(sources, maxSources);
        double mult = 1.0 + (double)n * Math.max(0.0, perSource);
        return Math.max(1.0, Math.min(mult, maxMultiplier));
    }

    public static int extraFlightTicks(int sources, int maxSources, int ticksPerSource) {
        return effectiveSources(sources, maxSources) * Math.max(0, ticksPerSource);
    }
}
