package rotp.addons.ger.client;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** 1.16 DisablerClientState: the entities the server told this client are disabled. */
public final class GerDisablerClientState {
    private static final Set<Integer> DISABLED_ENTITIES =
            ConcurrentHashMap.newKeySet();
    private static final Set<Integer> HELD_BY_DISABLER =
            ConcurrentHashMap.newKeySet();

    private GerDisablerClientState() {}

    public static boolean isDisabled(int entityId) {
        return DISABLED_ENTITIES.contains(entityId);
    }

    /** Whether the Disabler holds it, which Return to Zero does not get past. */
    public static boolean isHeldByDisabler(int entityId) {
        return HELD_BY_DISABLER.contains(entityId);
    }

    public static void setDisabled(
            int entityId, boolean disabled, boolean byDisabler) {
        if (disabled) {
            DISABLED_ENTITIES.add(entityId);
        }
        else {
            DISABLED_ENTITIES.remove(entityId);
        }
        if (byDisabler) {
            HELD_BY_DISABLER.add(entityId);
        }
        else {
            HELD_BY_DISABLER.remove(entityId);
        }
    }

    public static void clear() {
        DISABLED_ENTITIES.clear();
        HELD_BY_DISABLER.clear();
    }
}
