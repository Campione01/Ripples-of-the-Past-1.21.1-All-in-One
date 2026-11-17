package rotp.addons.kingcrimson.epitaph.sandbox;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import rotp.addons.kingcrimson.KingCrimsonConfig;

/**
 * What keeps a prediction from harming the game: the types that are never predicted (the setting, and those whose
 * phantoms kept throwing in this server run), and the count of a phantom's faults.
 */
public final class SandboxGuards {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** This many faults of one type in a server run, and the type is not predicted again until the server restarts. */
    public static final int FAULTS_TO_FUSE = 3;
    // The faults of one server run: a new count begins when the server stops.
    private static Faults run = new Faults();

    private static final class Faults {
        private final Map<EntityType<?>, Integer> counts = new HashMap<>();
        private final Set<EntityType<?>> fused = new HashSet<>();
        private final Set<EntityType<?>> unshown = new HashSet<>();
    }

    private SandboxGuards() {}

    public static boolean denied(EntityType<?> type) {
        return run.fused.contains(type) || KingCrimsonConfig.epitaphDenies(type);
    }

    /** A phantom's tick threw: its simulation is over. Logged once per type with the stack, and counted. */
    public static void fault(Entity phantom, Throwable error) {
        EntityType<?> type = phantom.getType();
        int faults = run.counts.merge(type, 1, Integer::sum);
        // A warning: the game goes on, and the creature is simply not foreseen further.
        if (faults == 1) {
            LOGGER.warn("Epitaph[phantom-fault] the prediction of a {} ended because its tick threw", BuiltInRegistries.ENTITY_TYPE.getKey(type),
                    error);
        }
        if (faults == FAULTS_TO_FUSE && run.fused.add(type)) {
            LOGGER.error("Epitaph[fuse] {} threw {} times and is not predicted again until the server restarts",
                    BuiltInRegistries.ENTITY_TYPE.getKey(type), faults);
        }
    }

    /** A phantom could not be shown to the one who foresees; it is foreseen all the same. Logged once per type. */
    public static void unshown(Entity phantom, Throwable error) {
        if (run.unshown.add(phantom.getType())) {
            LOGGER.warn("Epitaph[pairing] a {} could not be shown to the one who foresees",
                    BuiltInRegistries.ENTITY_TYPE.getKey(phantom.getType()), error);
        }
    }

    public static int faults(EntityType<?> type) {
        return run.counts.getOrDefault(type, 0);
    }

    public static void reset() {
        run = new Faults();
    }
}
