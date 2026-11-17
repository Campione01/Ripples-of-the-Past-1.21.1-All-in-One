package rotp.addons.spin.world;

import java.util.List;
import java.util.Locale;

public final class GyroSpawnRules {
    private GyroSpawnRules() {}

    public static boolean categoryListed(String category, List<? extends String> listed) {
        if (category == null || listed == null) return false;
        String wanted = category.trim().toUpperCase(Locale.ROOT);
        return listed.stream().anyMatch(entry -> entry != null && entry.trim().toUpperCase(Locale.ROOT).equals(wanted));
    }

    public static boolean isCheckTick(long time, int interval) {
        return interval > 0 && time % interval == 0;
    }

    public static boolean rolled(double roll, double chance) {
        return roll < chance;
    }

    public static int spreadOffset(double roll, int spread) {
        if (spread <= 0) return 0;
        return Math.max(-spread, Math.min(spread, (int) Math.floor(roll * (2 * spread + 1)) - spread));
    }

    public static double sameChunkOffset(double x, double offset) {
        int chunk = chunkOf(x);
        if (chunkOf(x + offset) == chunk) return offset;
        return chunkOf(x - offset) == chunk ? -offset : 0.0D;
    }

    private static int chunkOf(double x) {
        return (int) Math.floor(x) >> 4;
    }
}
