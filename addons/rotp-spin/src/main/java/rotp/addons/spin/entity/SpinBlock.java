package rotp.addons.spin.entity;

public final class SpinBlock {
    private SpinBlock() {
    }

    public static boolean canSpin(boolean isAir, float destroySpeed, boolean fluidEmpty, boolean hasTile) {
        return !isAir && !hasTile && fluidEmpty ? destroySpeed >= 0.0F : false;
    }
}
