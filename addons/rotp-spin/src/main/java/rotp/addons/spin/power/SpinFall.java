package rotp.addons.spin.power;

public final class SpinFall {
    static final float ROTP_MIN_DISTANCE = 3.0F;

    private SpinFall() {
    }

    public static float rotpReduction(float leapStrength) {
        return leapStrength > 0.0F ? (leapStrength + 5.0F) * 3.0F : 0.0F;
    }

    public static float correct(float original, float current, float spinLeap, float standLeap, float spinReduction) {
        float rotpLeap = Math.max(spinLeap, standLeap);
        if (!(original <= 3.0F) && !(rotpLeap <= 0.0F)) {
            float afterRotp = Math.max(original - rotpReduction(rotpLeap), 0.0F);
            float wanted = Math.max(original - Math.max(rotpReduction(standLeap), spinReduction), 0.0F);
            return Math.max(current + (wanted - afterRotp), 0.0F);
        } else {
            return current;
        }
    }
}
