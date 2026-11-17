package rotp.addons.d4c.travel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Which dimensions the selector offers, in what order, and under what
 * name. Ids are "namespace:path" strings. Pure Java so the contract check
 * can call it.
 */
public final class D4CDimensionRules {
    public static final String OVERWORLD = "minecraft:overworld";
    public static final String NETHER = "minecraft:the_nether";
    public static final String END = "minecraft:the_end";
    public static final String PARALLEL_WORLD = "rotp_d4c:parallel_world";
    /** The four that always lead the list, in this order. */
    public static final List<String> LEADING =
            List.of(OVERWORLD, NETHER, END, PARALLEL_WORLD);

    private D4CDimensionRules() {}

    /**
     * A level may be offered at all: it has a stem in the server's
     * dimension registry (so it is no runtime, per-player or instanced
     * level) and is not in the module's exclusion tag.
     */
    public static boolean isCandidate(boolean registered, boolean excluded) {
        return registered && !excluded;
    }

    /**
     * Owner 2026-11-10: a real dimension is offered only once this player
     * has been there, or in creative. Parallels are offered by their pair
     * alone and need no unlock.
     */
    public static boolean isOffered(
            boolean registered,
            boolean excluded,
            boolean visited,
            boolean creative) {
        return isCandidate(registered, excluded) && (creative || visited);
    }

    /**
     * The pair's entries: standing in a dimension with a parallel, that
     * parallel; standing in a parallel, the parallel itself (choosing it
     * leads back) and its source. Null arguments are absent.
     */
    public static List<String> pairTargets(
            String current, String pairedParallel, String sourceOfCurrent) {
        List<String> pair = new ArrayList<>();
        if (sourceOfCurrent != null) {
            pair.add(current);
            pair.add(sourceOfCurrent);
        }
        else if (pairedParallel != null) {
            pair.add(pairedParallel);
        }
        return pair;
    }

    /** What a visit writes into the player's record. */
    public static boolean isRecorded(
            String id, boolean registered, boolean excluded) {
        return isCandidate(registered, excluded)
                && !PARALLEL_WORLD.equals(id);
    }

    /**
     * The first record of a player who had none: where he stands, the
     * Overworld, and the Nether and the End when he holds vanilla's
     * advancements for having entered them.
     */
    public static Set<String> firstRecord(
            String current,
            boolean enteredNether,
            boolean enteredEnd) {
        Set<String> record = new LinkedHashSet<>();
        record.add(OVERWORLD);
        if (enteredNether) {
            record.add(NETHER);
        }
        if (enteredEnd) {
            record.add(END);
        }
        if (current != null && !PARALLEL_WORLD.equals(current)) {
            record.add(current);
        }
        return record;
    }

    /**
     * The leading four first, then parallels other than the Overworld's,
     * then the rest by id.
     */
    public static List<String> order(Collection<String> ids) {
        List<String> ordered = new ArrayList<>();
        for (String leading : LEADING) {
            if (ids.contains(leading)) {
                ordered.add(leading);
            }
        }
        List<String> parallels = new ArrayList<>();
        List<String> rest = new ArrayList<>();
        for (String id : ids) {
            if (LEADING.contains(id)
                    || rest.contains(id)
                    || parallels.contains(id)) {
                continue;
            }
            (id.startsWith("rotp_d4c:parallel/") ? parallels : rest).add(id);
        }
        parallels.sort(null);
        ordered.addAll(parallels);
        rest.sort(null);
        ordered.addAll(rest);
        return ordered;
    }

    /** The key a dimension's own mod is expected to name it by. */
    public static String translationKey(String namespace, String path) {
        return "dimension." + namespace + "." + path.replace('/', '.');
    }

    /** A readable name made from the id: "deep_darky" -> "Deep Darky". */
    public static String fallbackName(String path) {
        String last = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder name = new StringBuilder();
        for (String word : last.split("[_\\-.]+")) {
            if (word.isEmpty()) {
                continue;
            }
            if (name.length() > 0) {
                name.append(' ');
            }
            name.append(word.substring(0, 1).toUpperCase(Locale.ROOT))
                    .append(word.substring(1));
        }
        return name.length() > 0 ? name.toString() : path;
    }
}
