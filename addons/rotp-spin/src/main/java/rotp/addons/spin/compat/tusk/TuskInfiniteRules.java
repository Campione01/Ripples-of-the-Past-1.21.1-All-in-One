package rotp.addons.spin.compat.tusk;

public final class TuskInfiniteRules {
    public static final int ACT4_INDEX = 3;

    private TuskInfiniteRules() {
    }

    public static boolean grantsCharge(boolean enabled, boolean alreadyCharged, int act, int lesson, int minLesson, boolean superSpin) {
        return enabled && !alreadyCharged && act >= 3 && lesson >= minLesson && superSpin;
    }

    public static boolean counterRotates(
        boolean enabled, boolean spinning, boolean chipped, int lesson, int minLesson, boolean needsSuperSpin, boolean superSpin
    ) {
        return enabled && spinning && !chipped && lesson >= minLesson && (!needsSuperSpin || superSpin);
    }
}
