package rotp.addons.spin.power;

public final class SpinLessons {
    public static final int FIRST = 1;
    public static final int MAX = 5;
    public static final int LAST_ACTION_LESSON = 3;

    private SpinLessons() {
    }

    public static int clamp(int lesson) {
        return Math.max(1, Math.min(5, lesson));
    }

    public static int reached(
        int current, int ballHits, int hijacks, int goldenHits, int lesson2BallHits, int lesson3Hijacks, int lesson4GoldenHits, int lesson5GoldenHits
    ) {
        int lesson = clamp(current);
        if (lesson == 1 && ballHits >= lesson2BallHits) {
            lesson = 2;
        }

        if (lesson == 2 && hijacks >= lesson3Hijacks) {
            lesson = 3;
        }

        if (lesson == 3 && goldenHits >= lesson4GoldenHits) {
            lesson = 4;
        }

        if (lesson == 4 && goldenHits >= lesson5GoldenHits) {
            lesson = 5;
        }

        return lesson;
    }
}
