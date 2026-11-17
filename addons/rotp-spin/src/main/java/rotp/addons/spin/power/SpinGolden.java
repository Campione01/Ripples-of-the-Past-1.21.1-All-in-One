package rotp.addons.spin.power;

import java.util.Collection;

public final class SpinGolden {
    private SpinGolden() {
    }

    public static boolean isCalibrated(int lesson, String biomeCategory, boolean hasBuckle, Collection<? extends String> deadCategories) {
        return isCalibrated(lesson, biomeCategory, hasBuckle, false, deadCategories);
    }

    public static boolean isCalibrated(int lesson, String biomeCategory, boolean hasBuckle, boolean reference, Collection<? extends String> deadCategories) {
        if (lesson >= 5) {
            return true;
        } else {
            return lesson < 4 ? false : hasBuckle || reference || !deadCategories.contains(biomeCategory);
        }
    }

    public static boolean isHandFrameActive(long now, long until) {
        return now < until;
    }

    public static long handFrameUntil(long now, int durationTicks) {
        return now + (long)Math.max(0, durationTicks);
    }

    public static double multiplier(int lesson, boolean calibrated, double mult4, double mult5) {
        if (lesson >= 5) {
            return mult5;
        } else {
            return lesson == 4 && calibrated ? mult4 : 1.0;
        }
    }

    public static double applyChipped(double multiplier, double chippedRetention) {
        return 1.0 + (multiplier - 1.0) * chippedRetention;
    }

    public static boolean isGallopSuperSpin(int lesson, double horseSpeed, double minGallopSpeed) {
        return lesson >= 4 && horseSpeed >= minGallopSpeed;
    }
}
