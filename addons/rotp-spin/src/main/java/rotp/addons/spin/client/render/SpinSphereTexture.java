package rotp.addons.spin.client.render;

import com.mojang.blaze3d.platform.NativeImage;

public final class SpinSphereTexture {
    public static final int WIDTH = 64;
    public static final int HEIGHT = 32;
    public static final int SMALL_WIDTH = 32;
    public static final int SMALL_HEIGHT = 16;
    static final int GROOVES = 6;

    private SpinSphereTexture() {
    }

    public static NativeImage wreckingBall() {
        NativeImage image = new NativeImage(64, 32, false);

        for (int y = 0; y < 32; y++) {
            double v = (double)y / 31.0;
            double shade = shade(v);

            for (int x = 0; x < 64; x++) {
                image.setPixelRGBA(x, y, copper((double)x / 64.0, v, shade));
            }
        }

        return image;
    }

    public static NativeImage satelliteBall() {
        NativeImage image = new NativeImage(32, 16, false);

        for (int y = 0; y < 16; y++) {
            double v = (double)y / 15.0;
            double shade = 0.55 + 0.45 * Math.sin(Math.PI * v);

            for (int x = 0; x < 32; x++) {
                double u = (double)x / 32.0;
                boolean highlight = u > 0.08 && u < 0.18 && v > 0.28 && v < 0.45;
                image.setPixelRGBA(
                    x, y, highlight ? rgba(255, 251, 233, 138) : rgba(255, clamp((int)(235.0 * shade)), clamp((int)(199.0 * shade)), clamp((int)(49.0 * shade)))
                );
            }
        }

        return image;
    }

    public static NativeImage emptySocket() {
        NativeImage image = new NativeImage(32, 16, false);

        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 32; x++) {
                image.setPixelRGBA(x, y, rgba(255, 40, 14, 4));
            }
        }

        return image;
    }

    private static double shade(double v) {
        return 0.45 + 0.55 * Math.sin(Math.PI * v);
    }

    private static int copper(double u, double v, double shade) {
        double lon = frac(u * 6.0 + 0.12 * Math.sin((Math.PI * 2) * v));
        if (lon < 0.05) {
            return rgba(255, clamp((int)(242.0 * shade)), clamp((int)(159.0 * shade)), clamp((int)(108.0 * shade)));
        } else if (lon < 0.14) {
            return rgba(255, clamp((int)(232.0 * shade)), clamp((int)(96.0 * shade)), clamp((int)(28.0 * shade)));
        } else {
            return lon < 0.22
                ? rgba(255, clamp((int)(75.0 * shade)), clamp((int)(20.0 * shade)), 0)
                : rgba(255, clamp((int)(137.0 * shade)), clamp((int)(63.0 * shade)), clamp((int)(16.0 * shade)));
        }
    }

    private static int rgba(int alpha, int red, int green, int blue) {
        return alpha << 24 | blue << 16 | green << 8 | red;
    }

    private static double frac(double value) {
        return value - Math.floor(value);
    }

    private static int clamp(int value) {
        return Math.max(0, Math.min(255, value));
    }
}
