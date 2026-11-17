package rotp.addons.spin.power;

public final class BallBreakerAura {
    private BallBreakerAura() {
    }

    public static int sparkCount(int sparksPerTick, float alpha) {
        return sparksPerTick > 0 && !(alpha <= 0.0F) ? Math.round((float)sparksPerTick * Math.min(alpha, 1.0F)) : 0;
    }

    public static double verticalRadius(double bodyHeight, double radius) {
        return Math.max(bodyHeight, 0.0) * 0.5 + radius * 0.5;
    }

    public static double[] shellPoint(double u, double v, double radius, double height) {
        double phi = u * 2.0 * Math.PI;
        double cosTheta = 1.0 - 2.0 * clamp01(v);
        double sinTheta = Math.sqrt(Math.max(0.0, 1.0 - cosTheta * cosTheta));
        return new double[]{radius * sinTheta * Math.cos(phi), height * cosTheta, radius * sinTheta * Math.sin(phi)};
    }

    public static double[][] arc(double[] from, double[] to, int segments, double jitter, double[] randoms) {
        int n = Math.max(1, segments);
        double[][] points = new double[n + 1][];

        for (int i = 0; i <= n; i++) {
            double t = (double)i / (double)n;
            double[] p = new double[3];

            for (int axis = 0; axis < 3; axis++) {
                p[axis] = from[axis] + (to[axis] - from[axis]) * t;
                if (i > 0 && i < n) {
                    int r = (i - 1) * 3 + axis;
                    double rnd = randoms != null && r < randoms.length ? clamp01(randoms[r]) : 0.5;
                    p[axis] += (rnd - 0.5) * 2.0 * jitter;
                }
            }

            points[i] = p;
        }

        return points;
    }

    private static double clamp01(double x) {
        return x < 0.0 ? 0.0 : (x > 1.0 ? 1.0 : x);
    }
}
